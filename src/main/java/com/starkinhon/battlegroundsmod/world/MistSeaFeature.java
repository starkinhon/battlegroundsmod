package com.starkinhon.battlegroundsmod.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

// The Mar da Névoa (Stark 2026-11-11, "um dos mais bonitos até agora", from the approved preview), done like the other
// Naruto features: once per chunk of the biome, the part of every pillar, islet, needle group, old pier and rope bridge
// (SeaGen's cells, the same seed in every chunk so the pieces meet) that reaches the chunk. The stone first, standing on
// the sea's own bottom and taking the place of the water, the sand and the kelp; then the wood, the leaves, the vines
// and the rope, only into air or water. The sea's surface is the game's sea level (the preview's 0 is the top water
// block). Writes only inside its own chunk.
public final class MistSeaFeature extends Feature<NoneFeatureConfiguration> {
    private static final Cache<Long, int[]> PIECES = CacheBuilder.newBuilder().maximumSize(300).build();

    public MistSeaFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        int seed = worldKey ^ 0x5EA0F06;
        int sea = generator.getSeaLevel() - 1;
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator floor = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        List<SeaGen.Obj> objs = SeaGen.objectsNear(x0, z0, x1, z1, seed, o -> good(o, generator, random, floor, sea));
        // the stone first, then the pier and the bridges (a bridge after the pillars it joins)
        objs.sort((a, b) -> Integer.compare(order(a), order(b)));
        for (SeaGen.Obj o : objs) {
            int foot = o.kind() == SeaGen.Kind.BRIDGE ? 0 : floor.applyAsInt(o.x(), o.z());
            int[] vox = voxels(o, foot, sea);
            Map<Long, Integer> shape = null;
            for (int i = 0; i < vox.length; i += 4) {
                int x = vox[i], y = vox[i + 1], z = vox[i + 2], t = vox[i + 3];
                if (x < x0 || x > x1 || z < z0 || z > z1 || y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                    continue;
                }
                BlockState state;
                if (t == SeaGen.VINE) {
                    if (shape == null) {
                        shape = shape(vox);
                    }
                    state = vine(shape, x, y, z);
                    if (state == null) {
                        continue;
                    }
                } else if (t == SeaGen.ROPE || t == SeaGen.ROPE_Z || t == SeaGen.ROPE_Y) {
                    state = ModBlocks.ROPE.get().defaultBlockState().setValue(ChainBlock.AXIS,
                            t == SeaGen.ROPE ? Direction.Axis.X : t == SeaGen.ROPE_Z ? Direction.Axis.Z : Direction.Axis.Y);
                } else {
                    state = block(t);
                }
                put(level, pos.set(x, y, z), state, rock(t));
            }
        }
        return true;
    }

    private static int order(SeaGen.Obj o) {
        return o.kind() == SeaGen.Kind.BRIDGE ? 2 : o.kind() == SeaGen.Kind.PIER ? 1 : 0;
    }

    private static boolean rock(int t) {
        return t == SeaGen.STONE || t == SeaGen.ANDESITE || t == SeaGen.TUFF || t == SeaGen.MOSSY || t == SeaGen.GRASS
                || t == SeaGen.DIRT || t == SeaGen.MOSS || t == SeaGen.GRAVEL;
    }

    // Stone takes the place of water, the sea's bottom, plants and kelp; wood, leaves, vines and rope go only into air
    // and water (waterlogged where that's a thing). Never over another piece's stone.
    private static void put(WorldGenLevel level, BlockPos pos, BlockState state, boolean rock) {
        BlockState there = level.getBlockState(pos);
        boolean water = there.getFluidState().is(Fluids.WATER) && there.getFluidState().isSource();
        if (rock) {
            if (!(water || there.canBeReplaced() || there.is(BlockTags.SAND) || there.is(BlockTags.DIRT) || there.is(Blocks.GRAVEL)
                    || there.is(Blocks.CLAY) || there.is(BlockTags.BASE_STONE_OVERWORLD) || there.is(Blocks.KELP)
                    || there.is(Blocks.KELP_PLANT) || there.is(Blocks.SEAGRASS) || there.is(Blocks.TALL_SEAGRASS))) {
                return;
            }
        } else {
            if (!(there.isAir() || water || (there.canBeReplaced() && there.getFluidState().isEmpty()))) {
                return;
            }
            if (state.hasProperty(ChainBlock.WATERLOGGED)) {
                state = state.setValue(ChainBlock.WATERLOGGED, water);
            } else if (water && state.is(BlockTags.LEAVES)) {
                state = state.setValue(LeavesBlock.WATERLOGGED, true);
            } else if (water && state.getBlock() instanceof VineBlock) {
                return;
            }
        }
        level.setBlock(pos, state, 2);
    }

    // The piece's stone, to hang a vine on the right side.
    private static Map<Long, Integer> shape(int[] vox) {
        Map<Long, Integer> m = new HashMap<>();
        for (int i = 0; i < vox.length; i += 4) {
            m.put(LeafTreeGen.Keys.key(vox[i], vox[i + 1], vox[i + 2]), vox[i + 3]);
        }
        return m;
    }

    private static BlockState vine(Map<Long, Integer> shape, int x, int y, int z) {
        BlockState s = Blocks.VINE.defaultBlockState();
        boolean any = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Integer t = shape.get(LeafTreeGen.Keys.key(x + d.getStepX(), y, z + d.getStepZ()));
            if (t != null && rock(t)) {
                s = s.setValue(VineBlock.getPropertyForFace(d), true);
                any = true;
            }
        }
        return any ? s : null;
    }

    private static BlockState block(int t) {
        return switch (t) {
            case SeaGen.STONE -> Blocks.STONE.defaultBlockState();
            case SeaGen.ANDESITE -> Blocks.ANDESITE.defaultBlockState();
            case SeaGen.TUFF -> Blocks.TUFF.defaultBlockState();
            case SeaGen.MOSSY -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case SeaGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case SeaGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case SeaGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case SeaGen.LOG -> Blocks.SPRUCE_LOG.defaultBlockState();
            case SeaGen.PINE -> ModBlocks.PINE_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            case SeaGen.PINE_DARK -> ModBlocks.PINE_LEAVES_DARK.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            case SeaGen.PLANK -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case SeaGen.POST -> Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
            case SeaGen.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case SeaGen.SAND -> Blocks.SAND.defaultBlockState();
            case SeaGen.CLAY -> Blocks.CLAY.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    private static int[] voxels(SeaGen.Obj o, int foot, int sea) {
        long key = ((long) o.seed() << 20) ^ ((long) o.x() * 341873128712L) ^ ((long) o.z() * 132897987541L) ^ ((long) foot << 8)
                ^ ((long) sea << 40) ^ o.kind().ordinal();
        try {
            return PIECES.get(key, () -> SeaGen.build(o, foot, sea).pack());
        } catch (ExecutionException e) {
            return new int[0];
        }
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.MAR_DA_NEVOA);
    }

    // Is the spot good: open sea (the bottom at least 3 blocks under the water, not deeper than 40), the biome all around
    // as far as the thing reaches. A bridge is good when both its pillars are (SeaGen asks for each).
    private static boolean good(SeaGen.Obj o, ChunkGenerator generator, RandomState random, IntBinaryOperator floor, int sea) {
        int y = floor.applyAsInt(o.x(), o.z());
        if (y > sea - 2 || y < sea - 40 || !biomeAt(generator, random, o.x(), sea, o.z())) {
            return false;
        }
        int reach = o.reach();
        for (int a = 0; a < 8; a++) {
            int x = o.x() + (int) Math.round(Math.cos(a * Math.PI / 4) * reach);
            int z = o.z() + (int) Math.round(Math.sin(a * Math.PI / 4) * reach);
            if (!biomeAt(generator, random, x, sea, z)) {
                return false;
            }
        }
        return true;
    }
}
