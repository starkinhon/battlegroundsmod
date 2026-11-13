package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.AirFight;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.universe.Flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The combat flight on the client (combat/AirFight; forja db-voo-mobs/combate.html, approved by Stark 2026-11-13):
// attack mode (K) on while flying, with someone to fight within reach: locked on them. The camera (the player's own
// turn) stays on them, behind the shoulder; FlightClient steers around them (W closes in, S backs off, A/D circle,
// Space/Shift up and down, Ctrl dodges) and FlightPose stands the body up in the fighting stance.
// K off lets go: the normal flight with the boost, to flee or cross the map.
public final class AirFightClient {
    private static final double REACH = 48;
    private static final double KEEP = 72;
    private static int targetId = -1;
    private static int sent = -2;
    // the ki dash the server asked for (AirFight.MOVE_DASH): at whom, ticks left
    private static int dashTarget = -1;
    private static int dashTicks;
    private static long lastFrame;

    private AirFightClient() {
    }

    // Every client tick from FlightClient, before the flight moves the player: keeps the lock right.
    static void tick(LocalPlayer player, boolean flying) {
        boolean wants = flying && player.getData(ModAttachments.POWER).combat && !player.isSpectator();
        LivingEntity current = entity(targetId);
        if (!wants) {
            targetId = -1;
        } else if (current == null || !valid(player, current, KEEP)) {
            LivingEntity best = pick(player);
            targetId = best == null ? -1 : best.getId();
        }
        if (dashTicks > 0) {
            dashTicks--;
        }
        exchangeTick();
        if (sent != targetId) {
            sent = targetId;
            PacketDistributor.sendToServer(new ModPayloads.AirLock(targetId));
        }
    }

    public static void clear() {
        targetId = -1;
        sent = -2;
        dashTicks = 0;
        exTicks = 0;
        camPos = null;
    }

    private static LivingEntity entity(int id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (id < 0 || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    private static boolean valid(LocalPlayer player, LivingEntity e, double reach) {
        return e.isAlive() && !e.isRemoved() && e != player && !e.isSpectator() && e.distanceToSqr(player) < reach * reach
                && !(e instanceof Player p && p.isCreative());
    }

    // Who to fight: a flying soul first, then whoever is hostile (a player flying too counts), the closest one in front.
    private static LivingEntity pick(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : minecraft.level.getEntities(player, player.getBoundingBox().inflate(REACH))) {
            if (!(e instanceof LivingEntity living) || !valid(player, living, REACH)) {
                continue;
            }
            boolean flier = FlightClient.flying(living);
            boolean hostile = living instanceof SoulBoss || living instanceof Enemy || living instanceof Player && flier;
            if (!hostile) {
                continue;
            }
            Vec3 to = living.position().add(0, living.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            double front = d < 1.0E-3 ? 1 : to.scale(1 / d).dot(look);
            double score = d - front * 8 - (flier ? 24 : 0) - (living instanceof SoulBoss ? 6 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    // The one locked on, or null (no combat flight now).
    public static LivingEntity target() {
        return entity(targetId);
    }

    public static boolean active() {
        return target() != null && FlightClient.velocity() != null;
    }

    // ------------------------------------------------------------------ the moves the server asks for

    static void move(int kind, int target, int ticks) {
        if (kind == AirFight.MOVE_DASH) {
            dashTarget = target;
            dashTicks = ticks;
            FlightClient.dashStart();
        } else if (kind == AirFight.MOVE_EXCHANGE) {
            exOther = target;
            exTicks = ticks;
            if (dashTicks > 0) {
                FlightClient.dashEnd();
            }
            dashTicks = 0;
        } else if (kind == AirFight.MOVE_STOP) {
            if (dashTicks > 0) {
                FlightClient.dashEnd();
            }
            dashTicks = 0;
        }
    }

    // The ki dash's target while it flies, or null.
    static LivingEntity dashing() {
        return dashTicks > 0 ? entity(dashTarget) : null;
    }

    // ------------------------------------------------------------------ the camera: the approved page's, line by line

    // Stark's 3rd play (2026-11-13): "a prévia tá tão mais fluida... recria a prévia dentro do jogo". The page never
    // turned the player's aim: its locked camera is its own, behind the player on the player-target line, over the
    // shoulder, opening to the side when close so the player never covers the target, looking between the two
    // (placeCam, camKey 'jogador' + combatOn). The player itself just faces the target (look.yaw = the line).
    private static Vec3 camPos, camLook;
    // the punch exchange (AirFight.MOVE_EXCHANGE): seen from below and from the side, like the page's EX camera
    private static int exOther = -1;
    private static int exTicks;
    private static Vec3 exDir;

    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : (float) Mth.clamp((now - lastFrame) / 1.0E9, 0, 0.1);
        lastFrame = now;
        if (player == null || minecraft.isPaused() || !active()) {
            camPos = null;
            return;
        }
        LivingEntity t = target();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 head = player.getPosition(partial).add(0, 1.6, 0);
        Vec3 mc = t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
        Vec3 dv = mc.subtract(head);
        double dl = dv.length(), hz = Math.sqrt(dv.x * dv.x + dv.z * dv.z);
        // the player faces the target (the aim of every skill goes there too)
        if (dl > 0.2) {
            float yaw = (float) (Mth.atan2(-dv.x, dv.z) * Mth.RAD_TO_DEG);
            float pitch = Mth.clamp((float) (-Mth.atan2(dv.y, hz) * Mth.RAD_TO_DEG), -80, 80);
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYHeadRot(yaw);
            player.yHeadRotO = yaw;
        }
        Vec3 want, at;
        LivingEntity other = exTicks > 0 ? entity(exOther) : null;
        if (other != null) {
            Vec3 c = head.add(other.getPosition(partial).add(0, 1, 0)).scale(0.5);
            if (exDir == null) {
                Vec3 line = other.position().subtract(player.position());
                Vec3 side = new Vec3(-line.z, 0, line.x);
                exDir = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
                if (camPos != null && camPos.subtract(c).dot(exDir) < 0) {
                    exDir = exDir.scale(-1);
                }
            }
            want = c.add(exDir.scale(24));
            want = new Vec3(want.x, c.y - 8, want.z);
            at = c;
        } else {
            exDir = null;
            Vec3 flat = hz > 0.8 ? new Vec3(dv.x / hz, 0, dv.z / hz) : Vec3.directionFromRotation(0, player.getYRot());
            Vec3 right = new Vec3(-flat.z, 0, flat.x);
            // Stark's 4th play: "a câmera tá meio desordenada". The page's tilt along the line (up to 0.5 rad) put the
            // camera under the player looking at the sky whenever the target flew higher; here it stays near level,
            // a bit above the shoulder, and looks closer to the player so both stay in the frame.
            double el = Mth.clamp(Math.atan2(dv.y, Math.max(hz, 0.01)), -0.25, 0.25);
            Vec3 back = flat.scale(Math.cos(el)).add(0, Math.sin(el), 0);
            double side = 1.1 + 1.2 * Mth.clamp((7 - dl) / 5, 0, 1);
            want = head.add(back.scale(-4.6)).add(0, 1.0, 0).add(right.scale(-side));
            at = head.lerp(mc, Mth.clamp(1 - 2.5 / Math.max(dl, 0.1), 0.3, 0.6));
        }
        if (camPos == null) {
            camPos = want;
            camLook = at;
        }
        camPos = camPos.lerp(want, 1 - Math.exp(-(other != null ? 4 : 12) * dt));
        camLook = camLook.lerp(at, 1 - Math.exp(-14 * dt));
        // never inside a block: pulled in towards the player's head
        if (minecraft.level != null) {
            var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(head, camPos,
                    net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                camPos = hit.getLocation().add(head.subtract(hit.getLocation()).normalize().scale(0.3));
            }
        }
    }

    // The locked camera for CameraMixin, or null (vanilla camera).
    public static FaceCutscene.Shot shot() {
        if (camPos == null || camLook == null || !active()) {
            return null;
        }
        Vec3 d = camLook.subtract(camPos);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        if (d.lengthSqr() < 1.0E-4) {
            return null;
        }
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
        return new FaceCutscene.Shot(camPos, yaw, pitch);
    }

    // Client tick: the exchange camera runs down.
    static void exchangeTick() {
        if (exTicks > 0) {
            exTicks--;
        }
    }

    // Others: whether that player flies in the combat stance (Flight.COMBAT from the server).
    public static boolean combat(LivingEntity entity) {
        if (entity == Minecraft.getInstance().player) {
            return active();
        }
        return (FlightClient.state(entity) & Flight.COMBAT) != 0;
    }
}
