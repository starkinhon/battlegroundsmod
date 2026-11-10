package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// The ground torn along the body of a ki beam (Stark, 2026-11-09: "only the blast broke the ground, the ray itself left
// nothing"). Wherever the beam runs close over the ground, a furrow is ripped under it: wider and deeper the bigger the
// beam and the closer it runs, a few clods thrown up and forward. It digs one layer a tick, so a beam held over the same
// spot keeps eating into it until the ground is out of its reach (radius + 0.9 under the beam's axis), and it never
// touches the ground under whoever fires it. Only with soulsBlockDestruction (and mobGriefing).
public final class DbScar {
    // How far below the beam's axis (past its own radius) the ground is still torn.
    private static final double REACH = 0.9;
    private static final double STEP = 0.6;

    private DbScar() {
    }

    // The beam from `from` to `to`, `radius` thick (its hitbox), fired by caster (its feet are spared).
    public static void along(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 to, double radius) {
        along(caster, level, from, to, radius, 1.2);
    }

    // The same from `skip` blocks along it on (a piece of a beam that runs out over several ticks starts at 0).
    public static void along(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 to, double radius, double skip) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-3) {
            return;
        }
        Vec3 dir = d.scale(1 / len);
        double below = radius + REACH;
        Vec3 feet = caster.position();
        int[] flown = {0};
        int flyMax = 1 + (int) (radius * 2);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Columns already dug this tick (two samples close together share their columns).
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (double s = skip; s <= len; s += STEP) {
            Vec3 p = from.add(dir.scale(s));
            double ground = SubCommon.ground(level, p).y;
            double gap = p.y - ground;
            if (gap < -0.5 || gap > below) {
                continue;
            }
            float close = (float) Mth.clamp(1 - gap / below, 0, 1);
            double half = 0.3 + radius * (0.4 + 0.6 * close);
            for (int x = Mth.floor(p.x - half); x <= Mth.floor(p.x + half); x++) {
                for (int z = Mth.floor(p.z - half); z <= Mth.floor(p.z + half); z++) {
                    double dx = x + 0.5 - p.x;
                    double dz = z + 0.5 - p.z;
                    // A ragged edge: each column a bit in or out.
                    double edge = half * (0.8 + 0.35 * Db.hash(x, z, 31));
                    if (dx * dx + dz * dz > edge * edge) {
                        continue;
                    }
                    // Never under the one firing it.
                    double fx = x + 0.5 - feet.x;
                    double fz = z + 0.5 - feet.z;
                    if (fx * fx + fz * fz < 2.25) {
                        continue;
                    }
                    if (!done.add(BlockPos.asLong(x, 0, z))) {
                        continue;
                    }
                    int top = Mth.floor(ground - 0.01);
                    // The grass and flowers on top go with it.
                    pos.set(x, top + 1, z);
                    BlockState over = level.getBlockState(pos);
                    if (!over.isAir() && over.canBeReplaced() && over.getFluidState().isEmpty()) {
                        com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, pos, Block.UPDATE_CLIENTS);
                    }
                    pos.set(x, top, z);
                    if (!level.isLoaded(pos) || pos.getY() > p.y) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                        continue;
                    }
                    if (flown[0] < flyMax && SubCommon.RANDOM.nextInt(7) == 0) {
                        // A clod thrown up and on along the beam, a little to the side it was on.
                        flown[0]++;
                        FallingBlockEntity clod = FallingBlockEntity.fall(level, pos.immutable(), state);
                        clod.dropItem = false;
                        Vec3 side = new Vec3(dx, 0, dz);
                        side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize();
                        clod.setDeltaMovement(dir.multiply(1, 0, 1).scale(0.25 + 0.3 * SubCommon.RANDOM.nextDouble())
                                .add(side.scale(0.15 + 0.2 * SubCommon.RANDOM.nextDouble()))
                                .add(0, 0.35 + 0.35 * close * SubCommon.RANDOM.nextDouble(), 0));
                        clod.hurtMarked = true;
                    } else {
                        com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, pos, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
