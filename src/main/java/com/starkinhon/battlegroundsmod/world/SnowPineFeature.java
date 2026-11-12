package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The pines of the Norte Gelado: tall dark spruces in tiers of drooping boughs, every tier loaded with snow. How tall,
// how wide, how many tiers and how far each one droops come from the random; some lean a little, some lost the top.
// Within 6 blocks of the foot.
public final class SnowPineFeature extends Feature<NoneFeatureConfiguration> {
    private static final int REACH = 6;

    public SnowPineFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos foot = context.origin();
        BlockState ground = level.getBlockState(foot.below());
        if (!(ground.is(BlockTags.DIRT) || ground.is(Blocks.SNOW_BLOCK) || ground.is(BlockTags.BASE_STONE_OVERWORLD))
                || !level.getBlockState(foot).canBeReplaced() || !level.getFluidState(foot).isEmpty()) {
            return false;
        }
        int height = 9 + random.nextInt(9);
        boolean broken = random.nextInt(9) == 0;
        double width = 2.4 + random.nextDouble() * 1.8 + height * 0.06;
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = broken ? height - 3 - random.nextInt(3) : height;
        for (int y = 0; y < top; y++) {
            set(level, pos.setWithOffset(foot, 0, y, 0), log, true);
        }
        // Tiers from a few blocks up to the top, wide at the bottom, every one drooping at its rim.
        int start = 2 + random.nextInt(2);
        int y = start;
        while (y <= height) {
            double k = (double) (y - start) / Math.max(1, height - start);
            double r = width * Math.pow(1 - k, 0.85) + 0.6;
            double droop = 0.25 + random.nextDouble() * 0.35;
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    // a ragged rim, so the tier is not a perfect disc
                    double edge = r * (0.82 + 0.18 * hash(foot.getX() + dx, foot.getY() + y, foot.getZ() + dz));
                    if (d > edge || y > top + 1) {
                        continue;
                    }
                    int sag = (int) Math.floor(d * droop * d / Math.max(1, r));
                    set(level, pos.setWithOffset(foot, dx, y - sag, dz), leaves, false);
                    if (sag > 0) {
                        set(level, pos.setWithOffset(foot, dx, y - sag + 1, dz), leaves, false);
                    }
                    // the snow lying on the bough
                    pos.setWithOffset(foot, dx, y - sag + (sag > 0 ? 2 : 1), dz);
                    if (level.getBlockState(pos).isAir() && random.nextInt(5) != 0) {
                        level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(2)), 2);
                    }
                }
            }
            y += 2 + (random.nextInt(3) == 0 ? 1 : 0);
        }
        if (!broken) {
            set(level, pos.setWithOffset(foot, 0, height + 1, 0), leaves, false);
            pos.setWithOffset(foot, 0, height + 2, 0);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState(), 2);
            }
        }
        return true;
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state, boolean log) {
        BlockState there = level.getBlockState(pos);
        if (there.isAir() || there.is(Blocks.SNOW) || (there.canBeReplaced() && there.getFluidState().isEmpty())
                || (log && there.is(BlockTags.LEAVES))) {
            level.setBlock(pos, state, 2);
        }
    }

    private static double hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7368787L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65535.0;
    }
}
