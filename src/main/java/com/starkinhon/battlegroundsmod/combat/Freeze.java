package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// Holds entities completely still for a while (used during cutscenes): no walking, turning, attacking or skills.
// Mobs get their AI switched off; players are put back in place every tick. Attacks and item use are blocked in
// PowerEvents, skills in PowerLogic.
public final class Freeze {
    private static final class Hold {
        int count;
        boolean hadNoAi;
        // Set by face: the way they are kept looking, over the one each hold started with.
        float faceYaw = Float.NaN;
        float facePitch;
        // Set by place: where they are kept, over the anchor each hold started with.
        Vec3 at;
    }

    private static final Map<Entity, Hold> HELD = new HashMap<>();

    private Freeze() {
    }

    public static boolean isFrozen(Entity entity) {
        return HELD.containsKey(entity);
    }

    public static void hold(LivingEntity entity, int ticks) {
        Vec3 pos = entity.position();
        hold(entity, ticks, age -> pos);
    }

    // Same, but the entity is kept at anchor(age) each tick instead of where it stood (used to carry someone around).
    public static void hold(LivingEntity entity, int ticks, IntFunction<Vec3> anchor) {
        if (ticks <= 0) {
            return;
        }
        Hold hold = HELD.computeIfAbsent(entity, e -> new Hold());
        if (hold.count++ == 0 && entity instanceof Mob mob) {
            hold.hadNoAi = mob.isNoAi();
            mob.setNoAi(true);
        }
        float startYaw = entity.getYRot();
        float startPitch = entity.getXRot();
        Tasks.addSystem(age -> {
            if (HELD.get(entity) != hold) {
                // Let go early (free).
                return true;
            }
            if (age >= ticks || !entity.isAlive() || entity.isRemoved()) {
                release(entity);
                return true;
            }
            entity.setDeltaMovement(Vec3.ZERO);
            boolean faced = !Float.isNaN(hold.faceYaw);
            float yRot = faced ? hold.faceYaw : startYaw;
            float xRot = faced ? hold.facePitch : startPitch;
            Vec3 pos = hold.at != null ? hold.at : anchor.apply(age);
            boolean moved = entity.position().distanceToSqr(pos) > 1.0E-4 || entity.getYRot() != yRot || entity.getXRot() != xRot;
            if (moved) {
                if (entity instanceof ServerPlayer player) {
                    player.connection.teleport(pos.x, pos.y, pos.z, yRot, xRot);
                } else {
                    entity.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
                }
            }
            entity.hurtMarked = true;
            return false;
        });
    }

    // Turns someone held still to look this way, and keeps them so while the hold lasts.
    public static void face(LivingEntity entity, float yaw, float pitch) {
        Hold hold = HELD.get(entity);
        if (hold != null) {
            hold.faceYaw = yaw;
            hold.facePitch = pitch;
        }
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(entity.getX(), entity.getY(), entity.getZ(), yaw, pitch);
        }
    }

    // Puts someone held still at pos looking this way, and keeps them there while the hold lasts.
    public static void place(LivingEntity entity, Vec3 pos, float yaw, float pitch) {
        Hold hold = HELD.get(entity);
        if (hold != null) {
            hold.at = pos;
        }
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch);
        } else {
            entity.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        }
        face(entity, yaw, pitch);
    }

    // Lets someone go right away, whatever holds they still have.
    public static void free(Entity entity) {
        Hold hold = HELD.remove(entity);
        if (hold != null && entity instanceof Mob mob) {
            mob.setNoAi(hold.hadNoAi);
        }
    }

    private static void release(Entity entity) {
        Hold hold = HELD.get(entity);
        if (hold == null || --hold.count > 0) {
            return;
        }
        HELD.remove(entity);
        if (entity instanceof Mob mob) {
            mob.setNoAi(hold.hadNoAi);
        }
    }

    public static void clear() {
        HELD.clear();
    }
}
