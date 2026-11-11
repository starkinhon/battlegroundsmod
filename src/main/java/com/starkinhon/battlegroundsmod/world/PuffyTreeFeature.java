package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The trees of the Terra Sagrada de Karin (Stark 2026-11-11, with two frames of Korin's tower over the forest): a dense
// temperate forest, not a jungle, a sea of round puffy crowns in light yellow-green. Each tree is a short trunk that
// forks into a few branches, each ending in a round blob of leaves; the blobs overlap into one cloud-like crown. Trunk
// height, how many blobs, where and how big each one is all come from the feature's random, so every tree is its own.
// The leaves are oak leaves, so the biome's foliage color paints them. It stays within 7 blocks of its foot.
public final class PuffyTreeFeature extends Feature<NoneFeatureConfiguration> {
    private static final int REACH = 7;

    public PuffyTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos foot = context.origin();
        BlockState ground = level.getBlockState(foot.below());
        if (!ground.is(BlockTags.DIRT) || !level.getBlockState(foot).canBeReplaced() || !level.getFluidState(foot).isEmpty()) {
            return false;
        }
        boolean big = random.nextInt(5) == 0;
        int trunk = big ? 6 + random.nextInt(4) : 3 + random.nextInt(3);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        for (int i = 0; i < trunk; i++) {
            set(level, foot.above(i), log);
        }
        level.setBlock(foot.below(), Blocks.DIRT.defaultBlockState(), 2);
        BlockPos top = foot.above(trunk);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        // The middle blob sits on the trunk, the others around and a little above it on their branches.
        int blobs = (big ? 4 : 2) + random.nextInt(3);
        double mainR = big ? 3.6 + random.nextDouble() * 1.0 : 2.5 + random.nextDouble() * 0.9;
        blob(level, top.getX() + 0.5, top.getY() + mainR * 0.35, top.getZ() + 0.5, mainR, 0.82 + random.nextDouble() * 0.15, foot, leaves);
        double spin = random.nextDouble() * Math.PI * 2;
        for (int b = 0; b < blobs; b++) {
            double angle = spin + b * Math.PI * 2 / blobs + (random.nextDouble() - 0.5) * 0.8;
            double out = mainR * (0.55 + random.nextDouble() * 0.45);
            double r = mainR * (0.55 + random.nextDouble() * 0.35);
            double cx = top.getX() + 0.5 + Math.cos(angle) * out;
            double cz = top.getZ() + 0.5 + Math.sin(angle) * out;
            double cy = top.getY() - 0.5 - random.nextDouble() * 1.5 + random.nextDouble() * (big ? 2.5 : 1.5);
            branch(level, top.below(1 + random.nextInt(Math.max(1, trunk / 2))), cx, cy, cz, foot);
            blob(level, cx, cy, cz, r, 0.75 + random.nextDouble() * 0.2, foot, leaves);
        }
        return true;
    }

    // A round blob, a little flattened (squash < 1), the edge nibbled so it doesn't look turned on a lathe.
    private static void blob(WorldGenLevel level, double cx, double cy, double cz, double r, double squash, BlockPos foot,
            BlockState leaves) {
        int ri = (int) Math.ceil(r);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    int x = (int) Math.floor(cx) + dx, y = (int) Math.floor(cy) + dy, z = (int) Math.floor(cz) + dz;
                    double ox = x + 0.5 - cx, oy = (y + 0.5 - cy) / squash, oz = z + 0.5 - cz;
                    double d = ox * ox + oy * oy + oz * oz;
                    double edge = r * r * (0.86 + 0.14 * hash(x, y, z));
                    if (d > edge) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (inReach(pos, foot) && level.getBlockState(pos).canBeReplaced()) {
                        level.setBlock(pos, leaves, 2);
                    }
                }
            }
        }
    }

    // A branch of logs from the trunk out to a blob's center.
    private static void branch(WorldGenLevel level, BlockPos from, double tx, double ty, double tz, BlockPos foot) {
        double fx = from.getX() + 0.5, fy = from.getY() + 0.5, fz = from.getZ() + 0.5;
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz));
        Direction.Axis axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) ? Direction.Axis.Y
                : Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            BlockPos p = BlockPos.containing(fx + dx * t, fy + dy * t, fz + dz * t);
            if (inReach(p, foot)) {
                set(level, p, log);
            }
        }
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        BlockState there = level.getBlockState(pos);
        if (there.canBeReplaced() || there.is(BlockTags.LEAVES)) {
            level.setBlock(pos, state, 2);
        }
    }

    private static boolean inReach(BlockPos pos, BlockPos foot) {
        return Math.abs(pos.getX() - foot.getX()) <= REACH && Math.abs(pos.getZ() - foot.getZ()) <= REACH;
    }

    private static double hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7368787L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65535.0;
    }
}
