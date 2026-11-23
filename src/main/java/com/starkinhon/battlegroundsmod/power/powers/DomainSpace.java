// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// The space of an expansion up above the world (Gojo's Vazio Infinito, Sukuna's Santuário, DominioSpec.arena): a floor
// with no blocks under it. Mobs find their way by paths over blocks, so up there every one of them stood still
// (Stark, 2026-11-09: "o Gojo pode andar na expansão livremente, pq ele sempre fica parado?", and then "arruma isso em
// todas as expansões, pra todos os mobs"). Here: where that floor is, whether a blink lands inside, and the straight
// walk every mob takes there instead of a path.
public final class DomainSpace {
    // Farther than this from whoever it hunts, a plain mob doesn't bother (the same as a mob's usual follow range).
    private static final double STEER_RANGE = 48;
    // Close enough to hit: the mob's own attack takes over from here.
    private static final double STEER_STOP = 1.6;

    private DomainSpace() {
    }

    // The height of the floor while entity is up in the space of an expansion, NaN anywhere else.
    public static double floorY(LivingEntity entity) {
        double y = InfiniteVoid.floorY(entity);
        return Double.isNaN(y) ? ShrineDomain.floorY(entity) : y;
    }

    public static boolean inside(LivingEntity entity) {
        return !Double.isNaN(floorY(entity));
    }

    // Out of every expansion entity is caught in or up in, for good (the arenas move it on, or its fight is over): the
    // expansion's end no longer puts it back where it was taken in. putBack: down where it was taken in from, right now.
    // An expansion entity opened closes. See InfiniteVoid.release / ShrineDomain.release. True when it was in one.
    public static boolean release(LivingEntity entity, boolean putBack) {
        boolean gojo = InfiniteVoid.release(entity, putBack);
        return ShrineDomain.release(entity, putBack) | gojo;
    }

    // spot is on the floor of the expansion entity is up in (a blink lands there as on the ground).
    public static boolean onFloor(LivingEntity entity, Vec3 spot) {
        double y = floorY(entity);
        return !Double.isNaN(y) && Math.abs(spot.y - y) < 0.6;
    }

    // A blink or a step of entity's to spot stays in the expansion it is up in (always true outside one). Past the edge
    // Gojo's own domain shatters and the Santuário's barrier throws them back.
    public static boolean keepsInside(LivingEntity entity, Vec3 spot) {
        return InfiniteVoid.keepsInside(entity, spot) && ShrineDomain.keepsInside(entity, spot);
    }

    // How far out of the barrier the wall puts whoever walks into it.
    private static final double WALL_MARGIN = 0.6;

    // The dome standing in the world is solid from outside (Stark 2026-11-23: he walked into it from outside and was
    // taken in): anyone out in the world inside its raio (a sphere around center, the black ball) is put back just past
    // it, and what was carrying them in is taken off their speed. Spectators pass. Call it every tick while the dome
    // stands (everyone it caught is up in its own space, DominioSpec.arena, so nobody of the domain is down here).
    public static void wall(ServerLevel level, Vec3 center, double radius, LivingEntity owner) {
        double out = radius + WALL_MARGIN;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2 + 2, radius * 2 + 2, radius * 2 + 2),
                e -> e != owner && e.isAlive() && !e.isSpectator())) {
            Vec3 mid = com.starkinhon.battlegroundsmod.combat.Combat.center(e);
            Vec3 d = mid.subtract(center);
            double dist = d.length();
            if (dist > radius) {
                continue;
            }
            double dy = d.y;
            double half = e.getBbWidth() / 2;
            if (Math.abs(dy) >= out) {
                continue;
            }
            // Straight out sideways, at the same height (the ball's surface there).
            Vec3 flat = new Vec3(d.x, 0, d.z);
            if (flat.lengthSqr() < 1.0E-6) {
                flat = new Vec3(1, 0, 0);
            }
            flat = flat.normalize();
            double h = Math.sqrt(out * out - dy * dy) + half;
            Vec3 to = new Vec3(center.x + flat.x * h, e.getY(), center.z + flat.z * h);
            if (e instanceof ServerPlayer player) {
                player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot());
            } else {
                e.teleportTo(to.x, to.y, to.z);
            }
            Vec3 v = e.getDeltaMovement();
            double in = v.x * flat.x + v.z * flat.z;
            if (in < 0) {
                v = v.subtract(flat.x * in, 0, flat.z * in);
            }
            e.setDeltaMovement(v.add(flat.x * 0.08, 0, flat.z * 0.08));
            e.hurtMarked = true;
        }
    }

    // Called every tick for each mob up on the floor (the expansions' floor()): a mob hunting someone walks straight at
    // them over the floor, its MoveControl told where to go, since its path finder sees only air. The bosses and the
    // soul mobs (SoulBossAI) walk it themselves, and a mob already in reach is left to its own attack.
    public static void steer(LivingEntity entity, double floor) {
        if (!(entity instanceof Mob mob) || entity instanceof SoulBoss || mob.isNoAi()) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || target.level() != mob.level() || Double.isNaN(floorY(target))) {
            return;
        }
        double d = mob.distanceTo(target);
        if (d > STEER_RANGE || d <= STEER_STOP + mob.getBbWidth() / 2) {
            return;
        }
        Vec3 spot = new Vec3(target.getX(), floor, target.getZ());
        if (!keepsInside(mob, spot)) {
            return;
        }
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(spot.x, floor, spot.z, 1.0);
        mob.getLookControl().setLookAt(target, 30, 30);
    }
}
