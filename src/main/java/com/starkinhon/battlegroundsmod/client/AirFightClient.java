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
        if (sent != targetId) {
            sent = targetId;
            PacketDistributor.sendToServer(new ModPayloads.AirLock(targetId));
        }
    }

    public static void clear() {
        targetId = -1;
        sent = -2;
        dashTicks = 0;
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

    // ------------------------------------------------------------------ the camera: turned to the target

    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : (float) Mth.clamp((now - lastFrame) / 1.0E9, 0, 0.1);
        lastFrame = now;
        if (player == null || minecraft.isPaused() || !active()) {
            return;
        }
        LivingEntity t = target();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partial);
        Vec3 at = t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
        Vec3 to = at.subtract(eye);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        if (to.lengthSqr() < 0.04) {
            return;
        }
        float yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
        // the elevation held in a band (72°: at 50° a target well below fell out of the shot in the 4th scripted test)
        float pitch = Mth.clamp((float) (-Mth.atan2(to.y, flat) * Mth.RAD_TO_DEG), -72, 72);
        float k = 1 - (float) Math.exp(-14 * dt);
        float y = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * k;
        float x = player.getXRot() + (pitch - player.getXRot()) * k;
        player.setYRot(y);
        player.setXRot(x);
        player.yRotO = y;
        player.xRotO = x;
        player.setYHeadRot(y);
        player.yHeadRotO = y;
    }

    // Others: whether that player flies in the combat stance (Flight.COMBAT from the server).
    public static boolean combat(LivingEntity entity) {
        if (entity == Minecraft.getInstance().player) {
            return active();
        }
        return (FlightClient.state(entity) & Flight.COMBAT) != 0;
    }
}
