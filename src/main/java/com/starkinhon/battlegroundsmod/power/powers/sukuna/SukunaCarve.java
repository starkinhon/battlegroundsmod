package com.starkinhon.battlegroundsmod.power.powers.sukuna;

import com.starkinhon.battlegroundsmod.combat.BlockRules;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Sukuna's thrown cuts (the Z and its chant, the Dismantles of his base moves) cut the world they fly through as well
// (Stark 2026-11-09: "destruírem como cortes mesmo... um estrago equivalente ao tamanho deles"): the blade's own plane, as
// wide as the blade and a block thin, is taken out of the blocks between where its front was and where it is now. A
// standing cut low enough leaves a groove in the ground; a lying one, a slit across whatever wall it meets. Nothing is
// thrown about: the blocks in the cut just go (gamerule soulsBlockDestruction, BlockRules).
public final class SukunaCarve {
    // Sampling step along and across the blade (blocks), and the most blocks one cut takes out in one tick.
    private static final double STEP = 0.45;
    private static final int PER_TICK = 72;

    private SukunaCarve() {
    }

    // One blade's sweep this tick. origin: where it left the hand; way: its flight (unit); side: across its span (unit);
    // span: half its width; bow: how far its tips trail behind its middle (the crescent); from/upTo: its front's travel
    // along way this tick. done: the blocks this cut already took (one set per cut).
    public static void sweep(ServerLevel level, LivingEntity caster, Vec3 origin, Vec3 way, Vec3 side, double span, double bow, double from,
            double upTo, LongOpenHashSet done) {
        sweep(level, caster, origin, way, side, span, bow, from, upTo, 1, done);
    }

    // Same, the cut layers blocks thick across its face (the chant's stages cut deeper: 1, 2, 3).
    public static void sweep(ServerLevel level, LivingEntity caster, Vec3 origin, Vec3 way, Vec3 side, double span, double bow, double from,
            double upTo, int layers, LongOpenHashSet done) {
        if (!BlockRules.canBreak(level) || upTo <= from) {
            return;
        }
        int budget = PER_TICK * Math.max(1, layers);
        Vec3 face = side.cross(way);
        face = face.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : face.normalize();
        // not under his own feet, nor in front of his face: the cut leaves the hand
        double start = Math.max(from, 0.6);
        for (double s = start; s <= upTo && budget > 0; s += STEP) {
            for (double c = -span; c <= span && budget > 0; c += STEP) {
                double u = c / Math.max(span, 1.0E-3);
                Vec3 p = origin.add(way.scale(s - bow * u * u)).add(side.scale(c));
                for (int k = 0; k < Math.max(1, layers) && budget > 0; k++) {
                    Vec3 q = p.add(face.scale((k - (layers - 1) / 2.0) * 0.9));
                    if (caster.getBoundingBox().inflate(0.3).contains(q)) {
                        continue;
                    }
                    if (carve(level, caster, q, done)) {
                        budget--;
                    }
                }
            }
        }
    }

    // A straight stroke (Shift+Z's cuts born on the body): a block-thin line of this length through its middle.
    public static void line(ServerLevel level, LivingEntity caster, Vec3 center, Vec3 dir, double length, LongOpenHashSet done) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int budget = PER_TICK;
        for (double s = -length / 2; s <= length / 2 && budget > 0; s += STEP) {
            Vec3 p = center.add(dir.scale(s));
            if (caster.getBoundingBox().inflate(0.3).contains(p)) {
                continue;
            }
            if (carve(level, caster, p, done)) {
                budget--;
            }
        }
    }

    // The ground he stands on: blocks under his feet (any depth), up to FLOOR blocks around him, are never cut, so his own
    // Desmantelares don't take the floor from under him (Stark, 2026-11-09). Read where he is when the cut gets there.
    public static final double FLOOR = 1.5;

    public static boolean underFeet(LivingEntity caster, BlockPos pos) {
        if (caster == null || pos.getY() + 1 > caster.getY() + 0.01) {
            return false;
        }
        AABB box = caster.getBoundingBox().inflate(FLOOR, 0, FLOOR);
        return pos.getX() + 1 > box.minX && pos.getX() < box.maxX && pos.getZ() + 1 > box.minZ && pos.getZ() < box.maxZ;
    }

    private static boolean carve(ServerLevel level, LivingEntity caster, Vec3 p, LongOpenHashSet done) {
        BlockPos pos = BlockPos.containing(p);
        if (underFeet(caster, pos)) {
            return false;
        }
        if (!done.add(pos.asLong()) || !level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, pos, state, Block.UPDATE_ALL);
        return true;
    }
}
