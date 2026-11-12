package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Namekian trees (Stark's refs, 2026-11-10): a long thin trunk, a little bent, with a round ball of leaves on top
// (the biome colors the leaves blue); tall ones and short ones, some with a second smaller ball lower down. Within 5
// blocks of the foot.
public final class NamekTreeFeature extends Feature<NoneFeatureConfiguration> {
    public NamekTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return grow(context.level(), context.origin(), context.random());
    }

    public static boolean grow(WorldGenLevel level, BlockPos foot, RandomSource random) {
        if (!level.getBlockState(foot.below()).is(Blocks.GRASS_BLOCK) || !level.getBlockState(foot).canBeReplaced()
                || !level.getFluidState(foot).isEmpty()) {
            return false;
        }
        boolean tall = random.nextInt(3) == 0;
        int height = tall ? 13 + random.nextInt(10) : 5 + random.nextInt(6);
        double r = tall ? 2.5 + random.nextDouble() * 0.9 : 1.6 + random.nextDouble() * 0.7;
        double bend = random.nextDouble() * 1.4, angle = random.nextDouble() * Math.PI * 2;
        BlockState log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = 0, z = 0;
        for (int y = 0; y < height; y++) {
            double t = (double) y / height;
            int nx = (int) Math.round(Math.cos(angle) * bend * t * t), nz = (int) Math.round(Math.sin(angle) * bend * t * t);
            if (nx != x || nz != z) {
                set(level, pos.setWithOffset(foot, nx, y - 1, nz), log);
            }
            x = nx;
            z = nz;
            set(level, pos.setWithOffset(foot, x, y, z), log);
        }
        ball(level, foot.offset(x, height + (int) Math.round(r * 0.5), z), r, leaves, pos);
        if (tall && random.nextBoolean()) {
            ball(level, foot.offset(x, height - 4 - random.nextInt(3), z), r * 0.55, leaves, pos);
        }
        return true;
    }

    private static void ball(WorldGenLevel level, BlockPos c, double r, BlockState leaves, BlockPos.MutableBlockPos pos) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.25 + dz * dz);
                    if (d > r + 0.25 * hash(c.getX() + dx, c.getY() + dy, c.getZ() + dz)) {
                        continue;
                    }
                    pos.setWithOffset(c, dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, leaves, 2);
                    }
                }
            }
        }
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        BlockState there = level.getBlockState(pos);
        if (there.isAir() || there.canBeReplaced() || there.is(Blocks.OAK_LEAVES)) {
            level.setBlock(pos, state, 2);
        }
    }

    private static double hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7368787L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65535.0;
    }
}
