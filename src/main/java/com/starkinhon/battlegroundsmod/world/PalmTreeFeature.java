package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The palms on the sand of the Mar do Sul (PalmTree has the shape). Only on dry sand or grass, never in water.
public final class PalmTreeFeature extends Feature<NoneFeatureConfiguration> {
    public PalmTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return grow(context.level(), context.origin(), context.random());
    }

    public static boolean grow(WorldGenLevel level, BlockPos foot, RandomSource random) {
        BlockState ground = level.getBlockState(foot.below());
        if (!(ground.is(BlockTags.SAND) || ground.is(BlockTags.DIRT)) || !level.getBlockState(foot).canBeReplaced()
                || !level.getFluidState(foot).isEmpty()) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        PalmTree.grow(random, (dx, dy, dz, state, log) -> {
            // a palm stays within a few blocks of its foot (inside the chunks a feature may touch)
            if (Math.abs(dx) > 8 || Math.abs(dz) > 8) {
                return;
            }
            pos.setWithOffset(foot, dx, dy, dz);
            BlockState there = level.getBlockState(pos);
            if (there.isAir() || (there.canBeReplaced() && there.getFluidState().isEmpty()) || (log && there.is(BlockTags.LEAVES))) {
                level.setBlock(pos, state, 2);
            }
        });
        return true;
    }
}
