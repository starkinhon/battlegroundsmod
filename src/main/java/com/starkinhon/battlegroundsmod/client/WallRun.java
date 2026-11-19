package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.universe.EnergyControl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The Controle de Energia's tree run (universe/EnergyControl; Stark 2026-11-19: "não é pra escalar a árvore, é pra correr
// na árvore, virar horizontalmente e correr por ela"). Walking into a tree trunk or a wall puts the feet on it: the body
// turns flat, sticking out of the bark, and runs up it, the arms trailing behind. Forward runs up, back slides down, the
// sides run along it, crouch holds still, jump kicks off backwards, and at the top it hops over the edge onto it.
// The local player moves itself here (after its tick, so the next move uses this speed); the others' state comes from
// the server (ModPayloads.WallRun) and only changes how they are drawn.
public final class WallRun {
    private static final float RUN = 0.3f;
    private static final float DOWN = 0.22f;
    private static final float SIDE = 0.15f;

    static final class S {
        int dir = -1;
        int last = 0;
        float w, wO;
        float speed;
        double y = Double.NaN;
    }

    private static final Map<Integer, S> STATES = new HashMap<>();
    private static Object lastLevel;
    private static Direction wall;
    private static int kick;
    private static boolean jumpWas;
    private static boolean pushed;

    private WallRun() {
    }

    private static S state(int id) {
        return STATES.computeIfAbsent(id, k -> new S());
    }

    // ModPayloads.WallRun from the server: another player started or stopped.
    public static void remote(int entity, int dir) {
        S s = state(entity);
        s.dir = dir;
        if (dir >= 0) {
            s.last = dir;
        }
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lastLevel) {
            STATES.clear();
            wall = null;
            lastLevel = minecraft.level;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.isPaused()) {
            local(player);
        }
        Iterator<Map.Entry<Integer, S>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, S> en = it.next();
            S s = en.getValue();
            Entity e = minecraft.level.getEntity(en.getKey());
            s.wO = s.w;
            s.w += ((s.dir >= 0 && e != null ? 1 : 0) - s.w) * 0.45f;
            if (e != null) {
                if (!Double.isNaN(s.y)) {
                    s.speed += ((float) Math.abs(e.getY() - s.y) + (float) e.position().subtract(e.xo, e.getY(), e.zo).horizontalDistance() - s.speed) * 0.5f;
                }
                s.y = e.getY();
            }
            if (e == null || s.dir < 0 && s.w < 0.01f) {
                it.remove();
            }
        }
    }

    private static void local(LocalPlayer player) {
        boolean can = EnergyControl.knows(player) && player.isAlive() && !player.isSpectator() && !player.getAbilities().flying
                && !player.isPassenger() && !player.isInWater() && !player.isFallFlying() && !player.level().getBlockState(player.blockPosition()).is(net.minecraft.tags.BlockTags.CLIMBABLE);
        boolean fwd = player.zza > 0.1f;
        boolean back = player.zza < -0.1f;
        boolean jump = player.input.jumping;
        if (kick > 0) {
            kick--;
        }
        Direction d = can ? touching(player, wall) : null;
        if (d != null && wall == null) {
            // starts only when running into it, facing it
            Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
            double dot = look.x * d.getStepX() + look.z * d.getStepZ();
            if (!fwd || dot < 0.55 || kick > 0) {
                d = null;
            }
        }
        if (d == null) {
            if (wall != null) {
                if (can && fwd && !player.isShiftKeyDown() && player.getDeltaMovement().y > 0) {
                    // ran out of wall going up: over the edge onto the top
                    player.setDeltaMovement(wall.getStepX() * 0.32, 0.45, wall.getStepZ() * 0.32);
                }
                set(player, null);
            }
            jumpWas = jump;
            return;
        }
        if (wall != d) {
            set(player, d);
        }
        Vec3 in = new Vec3(d.getStepX(), 0, d.getStepZ());
        Vec3 left = new Vec3(in.z, 0, -in.x);
        if (jump && !jumpWas) {
            // kicks off the wall, backwards and up
            player.setDeltaMovement(in.scale(-0.65).add(0, 0.5, 0));
            kick = 10;
            set(player, null);
            jumpWas = true;
            return;
        }
        jumpWas = jump;
        double up = player.isShiftKeyDown() ? 0 : fwd ? RUN : back ? -DOWN : -0.06;
        Vec3 along = left.scale(player.xxa * SIDE);
        player.setDeltaMovement(in.x * 0.08 + along.x, up, in.z * 0.08 + along.z);
        player.resetFallDistance();
    }

    // The wall side the player's body touches (a sliver past it, floor and ceiling left out), the one it already runs on
    // first, else the one most in front.
    private static Direction touching(Player player, Direction prefer) {
        AABB box = player.getBoundingBox().deflate(0, 0.1, 0);
        if (prefer != null && hits(player, box, prefer)) {
            return prefer;
        }
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        Direction best = null;
        double bestDot = -2;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            double dot = look.x * d.getStepX() + look.z * d.getStepZ();
            if (dot > bestDot && hits(player, box, d)) {
                best = d;
                bestDot = dot;
            }
        }
        return best;
    }

    private static boolean hits(Player player, AABB box, Direction d) {
        return !player.level().noCollision(player, box.move(d.getStepX() * 0.07, 0, d.getStepZ() * 0.07));
    }

    private static void set(LocalPlayer player, Direction d) {
        wall = d;
        int dir = d == null ? -1 : d.get2DDataValue();
        EnergyControl.setRunning(player, d != null);
        remote(player.getId(), dir);
        PacketDistributor.sendToServer(new ModPayloads.WallRun(player.getId(), dir));
    }

    // ---------- the body, flat on the wall ----------

    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        pushed = false;
        Player player = event.getEntity();
        S s = STATES.get(player.getId());
        if (s == null) {
            return;
        }
        float partial = event.getPartialTick();
        float w = Mth.lerp(partial, s.wO, s.w);
        if (w < 0.01f) {
            return;
        }
        Direction d = Direction.from2DDataValue(s.last);
        // Minecraft turns the body by its yaw after this: take it off and face the wall instead, then lay the body down
        // with the feet on the wall and the front up (the way it runs)
        float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Quaternionf vanilla = new Quaternionf().rotateY((float) Math.toRadians(180f - body));
        Quaternionf facing = new Quaternionf().rotateY((float) Math.toRadians(180f - d.toYRot()));
        Quaternionf turn = new Quaternionf().slerp(facing.mul(vanilla.invert()), w);
        Vector3f axis = new Vector3f(0, 1, 0).cross(d.getStepX(), 0, d.getStepZ());
        Quaternionf tilt = new Quaternionf().rotateAxis(-Mth.HALF_PI * w, axis);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float half = player.getBbWidth() / 2;
        pose.translate(d.getStepX() * half * w, 0.9f * w, d.getStepZ() * half * w);
        pose.mulPose(tilt.mul(turn));
        pushed = true;
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (pushed) {
            event.getPoseStack().popPose();
            pushed = false;
        }
    }

    // The run's limbs (PlayerModelMixin, after Minecraft's pose): arms trailing straight back, legs pumping.
    public static void apply(LivingEntity entity, HumanoidModel<?> model, float age) {
        S s = STATES.get(entity.getId());
        if (s == null || SkillAnimations.posing(entity)) {
            return;
        }
        float w = Mth.clamp(s.w, 0, 1);
        if (w < 0.01f) {
            return;
        }
        float move = Mth.clamp(s.speed / 0.2f, 0, 1);
        float phase = age * 0.9f;
        float legs = Mth.sin(phase) * 1.1f * move;
        set(model.rightArm, 1.35f + Mth.sin(phase * 0.5f) * 0.05f, 0, 0.18f, w);
        set(model.leftArm, 1.35f - Mth.sin(phase * 0.5f) * 0.05f, 0, -0.18f, w);
        set(model.rightLeg, legs, 0, 0.02f, w);
        set(model.leftLeg, -legs, 0, -0.02f, w);
        set(model.body, 0, 0, 0, w);
        set(model.head, -0.25f, 0, 0, w);
        model.hat.copyFrom(model.head);
    }

    private static void set(ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }
}
