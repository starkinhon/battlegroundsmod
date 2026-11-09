package com.starkinhon.battlegroundsmod.combat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;

// Holds entities completely still for a while (used during cutscenes): no walking, turning, attacking or skills.
// Mobs get their AI switched off; players are put back in place every tick. Attacks and item use are blocked in
// PowerEvents, skills in PowerLogic.
public final class Freeze {
    // One hold on an entity: whose move made it (Tasks.owner()), where it keeps them, and how long it has run.
    private static final class Grip {
        final Entity owner;
        final IntFunction<Vec3> anchor;
        final boolean lookFree;
        // A player moved by their own client along the same path (holdDriven): only put back when this far off.
        final double slack;
        final float startYaw;
        final float startPitch;
        // holdLoose: paralyzed, not nailed. Hits still throw them, but only within this (the expansion's barrier); null
        // for every other hold.
        UnaryOperator<Vec3> bound;
        // The throw of the last hit, carried out a tick at a time and dying off (mobs; players' own clients fly them).
        Vec3 push;
        // Set by place: where this hold keeps them, over its own anchor.
        Vec3 at;
        int age;
        boolean done;

        Grip(Entity owner, IntFunction<Vec3> anchor, boolean lookFree, double slack, float startYaw, float startPitch) {
            this.owner = owner;
            this.anchor = anchor;
            this.lookFree = lookFree;
            this.slack = slack;
            this.startYaw = startYaw;
            this.startPitch = startPitch;
        }
    }

    // Every hold an entity is under. Only the newest one moves them (two moves holding the same target used to pull it
    // to two places every tick: it shook between them, and the longer one pulled it back at the end, Stark 2026-11-08).
    private static final class Hold {
        final List<Grip> grips = new ArrayList<>();
        boolean hadNoAi;
        // Set by face: the way they are kept looking, over the one each hold started with.
        float faceYaw = Float.NaN;
        float facePitch;

        Grip top() {
            return grips.isEmpty() ? null : grips.get(grips.size() - 1);
        }

        // Kept in place but free to look around (holdLookFree) only while every hold on them is that kind.
        boolean lookFree() {
            for (Grip grip : grips) {
                if (!grip.lookFree) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final Map<Entity, Hold> HELD = new HashMap<>();

    private Freeze() {
    }

    public static boolean isFrozen(Entity entity) {
        return HELD.containsKey(entity);
    }

    // Each hold returns the action that ends it (only it: the entity's other holds go on).
    public static Runnable hold(LivingEntity entity, int ticks) {
        Vec3 pos = entity.position();
        return hold(entity, ticks, age -> pos);
    }

    // Same, but the entity is kept at anchor(age) each tick instead of where it stood (used to carry someone around).
    public static Runnable hold(LivingEntity entity, int ticks, IntFunction<Vec3> anchor) {
        return hold(entity, ticks, anchor, false);
    }

    // Paralyzed inside an expansion (Gojo's Vazio Infinito, Sukuna's Santuário): they can't walk, attack or use skills,
    // but a hit still throws them like anyone, never past the barrier (bound puts a spot back inside it). Stark,
    // 2026-11-09: "o mob pode ser movido por hits e dano, pra n ficar totalmente paralisado no msm lugar, só que sem
    // conseguir sair da barreira da expansão".
    public static Runnable holdLoose(LivingEntity entity, int ticks, UnaryOperator<Vec3> bound) {
        Vec3 pos = entity.position();
        Hold before = HELD.get(entity);
        // The expansions renew their hold every few ticks: a throw still going on carries over to the new one.
        Vec3 push = before != null && before.top() != null ? before.top().push : null;
        Runnable release = hold(entity, ticks, age -> pos, false, 0);
        Hold hold = HELD.get(entity);
        if (hold != null && hold.top() != null) {
            hold.top().bound = bound;
            hold.top().push = push;
        }
        return release;
    }

    // Same as hold, but the camera stays free: they can't walk, jump, attack or use skills, yet can turn and look around
    // (like Madara's Katon). Turning changes nothing for the move, which aims by positions, not by where they look.
    public static Runnable holdLookFree(LivingEntity entity, int ticks) {
        Vec3 pos = entity.position();
        return hold(entity, ticks, age -> pos, true, 0);
    }

    // Carried along anchor(age) with the camera free.
    public static Runnable holdLookFree(LivingEntity entity, int ticks, IntFunction<Vec3> anchor) {
        return hold(entity, ticks, anchor, true, 0);
    }

    // Same, for a move whose path the player's own client also follows tick by tick (so their view glides instead of
    // jumping with every teleport, Sukuna's B): a player is only put back when more than slack blocks off the anchor
    // (lag puts the two a tick or so apart). Others are carried exactly.
    public static Runnable holdDriven(LivingEntity entity, int ticks, IntFunction<Vec3> anchor, double slack) {
        return hold(entity, ticks, anchor, true, entity instanceof ServerPlayer ? slack : 0);
    }

    private static Runnable hold(LivingEntity entity, int ticks, IntFunction<Vec3> anchor, boolean lookFree) {
        return hold(entity, ticks, anchor, lookFree, 0);
    }

    private static Runnable hold(LivingEntity entity, int ticks, IntFunction<Vec3> anchor, boolean lookFree, double slack) {
        if (ticks <= 0) {
            return () -> {
            };
        }
        Hold hold = HELD.computeIfAbsent(entity, e -> new Hold());
        if (hold.grips.isEmpty() && entity instanceof Mob mob) {
            hold.hadNoAi = mob.isNoAi();
            mob.setNoAi(true);
        }
        Grip grip = new Grip(Tasks.owner(), anchor, lookFree, slack, entity.getYRot(), entity.getXRot());
        hold.grips.add(grip);
        Tasks.addSystem(age -> {
            if (grip.done) {
                // Let go early (free, or its own release).
                return true;
            }
            grip.age = age;
            if (age >= ticks || !entity.isAlive() || entity.isRemoved()) {
                release(entity, grip);
                return true;
            }
            if (HELD.get(entity) != hold || hold.top() != grip) {
                // A newer hold has them: it is the one that moves them.
                return false;
            }
            Vec3 pos;
            try {
                pos = grip.at != null ? grip.at : anchor.apply(age);
            } catch (RuntimeException e) {
                // a move's anchor that fails must not keep them held for good (every skill is blocked while held)
                com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Freeze anchor failed", e);
                release(entity, grip);
                return true;
            }
            if (grip.slack > 0) {
                // their client flies them; the server only steps in when they drift too far
                if (entity.position().distanceToSqr(pos) > grip.slack * grip.slack && entity instanceof ServerPlayer player) {
                    lookFreeTeleport(player, pos);
                }
                // The path goes through whatever stands in it (it was planned on flat ground): without this the server
                // checked each step his client sent against the blocks, found him "moved wrongly" and threw him back,
                // so he teleported about (Stark 2026-11-09, the B in a real fight). Player.tick sets it back to
                // isSpectator() at the start of every tick; the client's steps arrive between ticks, after this.
                entity.noPhysics = true;
                entity.resetFallDistance();
                return false;
            }
            if (grip.bound != null) {
                Vec3 thrown = loose(entity, grip, pos);
                if (thrown == null) {
                    // A player just hit: their own client flies them (the knockback it was sent); kept inside only.
                    return false;
                }
                pos = thrown;
            }
            entity.setDeltaMovement(Vec3.ZERO);
            if (hold.lookFree()) {
                // Only the position is put back; the rotation is sent as relative zero so the camera is never yanked.
                if (entity.position().distanceToSqr(pos) > 1.0E-4) {
                    if (entity instanceof ServerPlayer player) {
                        lookFreeTeleport(player, pos);
                    } else {
                        entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
                    }
                }
                entity.hurtMarked = true;
                return false;
            }
            boolean faced = !Float.isNaN(hold.faceYaw);
            float yRot = faced ? hold.faceYaw : grip.startYaw;
            float xRot = faced ? hold.facePitch : grip.startPitch;
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
        return () -> release(entity, grip);
    }

    // A loose hold's tick: where they are kept now that a hit may have thrown them. null for a player in the middle of a
    // hit (their client moves them; the server only keeps them inside the barrier).
    private static Vec3 loose(LivingEntity entity, Grip grip, Vec3 pos) {
        if (entity instanceof ServerPlayer player) {
            Vec3 here = player.position();
            if (player.hurtTime <= 0) {
                return pos;
            }
            Vec3 inside = grip.bound.apply(here);
            if (inside.distanceToSqr(here) > 1.0E-4) {
                lookFreeTeleport(player, new Vec3(inside.x, here.y, inside.z));
            }
            grip.at = new Vec3(inside.x, pos.y, inside.z);
            return null;
        }
        Vec3 v = entity.getDeltaMovement();
        if (v.horizontalDistanceSqr() > 0.0025 && (grip.push == null || v.horizontalDistanceSqr() > grip.push.horizontalDistanceSqr())) {
            // A hit's knockback: thrown from where they are kept.
            grip.push = new Vec3(v.x, 0, v.z);
        }
        if (grip.push == null) {
            return pos;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setPos(pos.x, pos.y, pos.z);
        entity.move(MoverType.SELF, grip.push);
        Vec3 inside = grip.bound.apply(entity.position());
        Vec3 next = new Vec3(inside.x, pos.y, inside.z);
        grip.at = next;
        grip.push = grip.push.scale(0.7);
        if (grip.push.horizontalDistanceSqr() < 0.0025) {
            grip.push = null;
        }
        return next;
    }

    // Moves a player without touching the camera. Vanilla's relative teleport sends (yaw - current yaw) and also sets the
    // server rotation to the yaw given, so passing 0, 0 snapped the view (and the body others see) to yaw 0 / pitch 0.
    // Passing the current rotation makes the sent turn exactly zero and keeps the server rotation as it is.
    private static void lookFreeTeleport(ServerPlayer player, Vec3 pos) {
        player.connection.teleport(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot(),
                EnumSet.of(RelativeMovement.Y_ROT, RelativeMovement.X_ROT));
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

    // Puts someone held still at pos looking this way, and keeps them there while the hold lasts: the holds of whoever is
    // moving them (Tasks.owner()), or the newest one if none is theirs.
    public static void place(LivingEntity entity, Vec3 pos, float yaw, float pitch) {
        Hold hold = HELD.get(entity);
        if (hold != null) {
            Entity owner = Tasks.owner();
            boolean mine = false;
            for (Grip grip : hold.grips) {
                if (owner != null && grip.owner == owner) {
                    grip.at = pos;
                    mine = true;
                }
            }
            if (!mine && hold.top() != null) {
                hold.top().at = pos;
            }
        }
        if (hold != null && hold.lookFree() && entity instanceof ServerPlayer player) {
            // Free look: move them, but leave the camera where they are pointing it.
            lookFreeTeleport(player, pos);
            return;
        }
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch);
        } else {
            entity.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        }
        face(entity, yaw, pitch);
    }

    // Lets go of the holds the move running now made on entity (Tasks.owner(); holds of no known owner go too), right
    // away. Holds someone else's move has on them go on: a skill ending used to drop the target out of another one's
    // grab in mid-air (Stark 2026-11-08). With no known owner running, every hold goes.
    public static void free(Entity entity) {
        Hold hold = HELD.get(entity);
        if (hold == null) {
            return;
        }
        Entity owner = Tasks.owner();
        for (Grip grip : new ArrayList<>(hold.grips)) {
            if (owner == null || grip.owner == null || grip.owner == owner) {
                release(entity, grip);
            }
        }
    }

    // Whose move holds entity now (the newest hold, the one that moves them), or null when no hold has them.
    public static Entity holder(Entity entity) {
        Hold hold = HELD.get(entity);
        Grip top = hold == null ? null : hold.top();
        return top == null ? null : top.owner;
    }

    // Lets go of the holds entity's own moves put on it (a cast that kept them still), when someone else's grab takes
    // them: those would otherwise outlast or overtake the grab.
    public static void freeOwn(Entity entity) {
        Hold hold = HELD.get(entity);
        if (hold == null) {
            return;
        }
        for (Grip grip : new ArrayList<>(hold.grips)) {
            if (grip.owner == entity) {
                release(entity, grip);
            }
        }
    }

    // Lets go of every hold on entity, whoever made it (a boss's new phase, a cutscene that takes over).
    public static void freeAll(Entity entity) {
        Hold hold = HELD.get(entity);
        if (hold == null) {
            return;
        }
        for (Grip grip : new ArrayList<>(hold.grips)) {
            release(entity, grip);
        }
    }

    private static void release(Entity entity, Grip grip) {
        grip.done = true;
        if (grip.slack > 0 && entity instanceof net.minecraft.world.entity.player.Player player) {
            player.noPhysics = player.isSpectator();
        }
        Hold hold = HELD.get(entity);
        if (hold == null || !hold.grips.remove(grip) || !hold.grips.isEmpty()) {
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
