package com.starkinhon.battlegroundsmod.power.powers;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
