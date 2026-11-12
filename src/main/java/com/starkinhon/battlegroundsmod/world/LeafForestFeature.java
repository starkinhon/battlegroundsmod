package com.starkinhon.battlegroundsmod.world;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Floresta da Folha's woods (Stark 2026-11-11, from the approved preview): runs once per chunk of the biome and puts
// in that chunk the part of every tree that reaches it (LeafTreeGen's cells), the giants' crowns 40 blocks out from
// their foot included; a tree is the same seed in every chunk, so the pieces meet. Then the ground of the chunk: the
// trail, fallen leaves under the crowns, ferns and yellow flowers. Writes only inside its own chunk.
// A tree only grows where the place is good for all of it: its foot on dry land in the biome, and for the big ones the
// biome all around (so a giant never comes out cut at the edge of the forest) and not too steep a slope.
public final class LeafForestFeature extends Feature<NoneFeatureConfiguration> {
    // The voxels of the last trees grown: one giant is asked for by some 30 chunks.
    private static final Cache<Long, int[]> TREES = CacheBuilder.newBuilder().maximumSize(400).build();

    public LeafForestFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int seed = (int) (worldSeed ^ (worldSeed >>> 32));
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator height = (x, z) -> height(generator, level, random, seed, x, z);
        IntBinaryOperator floorY = (x, z) -> height.applyAsInt(x, z) - 1;

        List<LeafTreeGen.Obj> trees = LeafTreeGen.objectsNear(x0, z0, x1, z1, seed, o -> good(o, generator, random, height, seed));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (LeafTreeGen.Obj o : trees) {
            int foot = height.applyAsInt(o.x(), o.z());
            int[] vox = voxels(o, foot, floorY);
            for (int i = 0; i < vox.length; i += 4) {
                int x = vox[i], y = vox[i + 1], z = vox[i + 2];
                if (x < x0 || x > x1 || z < z0 || z > z1 || y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                    continue;
                }
                pos.set(x, y, z);
                put(level, pos, vox[i + 3]);
            }
        }
        // The ground.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!inForest(level.getBiome(pos.set(x, height.applyAsInt(x, z), z)))) {
                    continue;
                }
                int y = height.applyAsInt(x, z);
                pos.set(x, y - 1, z);
                BlockState ground = level.getBlockState(pos);
                if (!ground.is(Blocks.GRASS_BLOCK)) {
                    continue;
                }
                pos.set(x, y, z);
                if (!level.getBlockState(pos).isAir()) {
                    continue;
                }
                if (LeafTreeGen.pathAt(x, z, seed)) {
                    level.setBlock(pos.set(x, y - 1, z), Blocks.DIRT_PATH.defaultBlockState(), 2);
                    continue;
                }
                boolean shaded = false;
                for (int k = 4; k < 60 && !shaded; k++) {
                    shaded = isLeaf(level.getBlockState(pos.set(x, y + k, z)));
                }
                byte deco = LeafTreeGen.groundDeco(x, z, shaded, seed);
                pos.set(x, y, z);
                if (deco == LeafTreeGen.LITTER) {
                    level.setBlock(pos, ModBlocks.FALLEN_LEAVES.get().defaultBlockState(), 2);
                } else if (deco == LeafTreeGen.FERN) {
                    level.setBlock(pos, Blocks.FERN.defaultBlockState(), 2);
                } else if (deco == LeafTreeGen.FLOWER) {
                    level.setBlock(pos, Blocks.DANDELION.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    private static int[] voxels(LeafTreeGen.Obj o, int foot, IntBinaryOperator floorY) {
        long key = ((long) o.seed() << 20) ^ ((long) o.x() * 341873128712L) ^ ((long) o.z() * 132897987541L) ^ ((long) foot << 8) ^ o.kind().ordinal();
        try {
            return TREES.get(key, () -> LeafTreeGen.build(o, foot, floorY).pack());
        } catch (ExecutionException e) {
            return new int[0];
        }
    }

    // The ground's first air block at a column, as the terrain is shaped before anything is built on it.
    // Keyed by the world's seed too: another world opened in the same game has other heights.
    static int height(ChunkGenerator generator, WorldGenLevel level, RandomState random, int seed, int x, int z) {
        return TerrainHeight.at(generator, level, random, seed, x, z);
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return inForest(generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()));
    }

    private static boolean inForest(Holder<Biome> biome) {
        return biome.is(ModWorldgen.FLORESTA_DA_FOLHA);
    }

    // Is the spot good for this tree: dry land, not on the trail, in the biome (for the big ones, the biome all around
    // as far as they reach) and, for a giant, not on a steep slope.
    private static boolean good(LeafTreeGen.Obj o, ChunkGenerator generator, RandomState random, IntBinaryOperator height, int seed) {
        int y = height.applyAsInt(o.x(), o.z());
        if (y <= generator.getSeaLevel() || LeafTreeGen.pathAt(o.x(), o.z(), seed) || !biomeAt(generator, random, o.x(), y, o.z())) {
            return false;
        }
        int reach = o.kind() == LeafTreeGen.Kind.BUSH ? 0 : o.kind().reach();
        if (reach > 0) {
            for (int a = 0; a < 8; a++) {
                int x = o.x() + (int) Math.round(Math.cos(a * Math.PI / 4) * reach);
                int z = o.z() + (int) Math.round(Math.sin(a * Math.PI / 4) * reach);
                if (!biomeAt(generator, random, x, y, z)) {
                    return false;
                }
            }
        }
        if (o.kind() == LeafTreeGen.Kind.GIANT || o.kind() == LeafTreeGen.Kind.LOG) {
            int lo = y, hi = y;
            for (int a = 0; a < 4; a++) {
                int h = height.applyAsInt(o.x() + (a % 2 == 0 ? 5 : -5), o.z() + (a < 2 ? 5 : -5));
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            if (hi - lo > (o.kind() == LeafTreeGen.Kind.GIANT ? 6 : 2) || lo <= generator.getSeaLevel()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLeaf(BlockState state) {
        return state.is(ModBlocks.LEAF_LEAVES.get()) || state.is(ModBlocks.LEAF_LEAVES_LIGHT.get()) || state.is(ModBlocks.LEAF_LEAVES_DARK.get());
    }

    // A voxel into the world. Wood (and moss) goes through leaves, plants and the ground's dirt (the roots run into it);
    // leaves only into air and plants. Nothing else is touched.
    private static void put(WorldGenLevel level, BlockPos pos, int type) {
        BlockState there = level.getBlockState(pos);
        boolean wood = type == LeafTreeGen.BARK || type == LeafTreeGen.CORE || type == LeafTreeGen.MOSS;
        if (wood) {
            if (!(there.canBeReplaced() || there.is(BlockTags.LEAVES) || there.is(BlockTags.DIRT) || there.is(Blocks.DIRT_PATH))) {
                return;
            }
        } else if (!there.canBeReplaced() || !there.getFluidState().isEmpty()) {
            return;
        }
        BlockState state = switch (type) {
            case LeafTreeGen.BARK -> Blocks.OAK_WOOD.defaultBlockState();
            case LeafTreeGen.CORE -> Blocks.STRIPPED_OAK_WOOD.defaultBlockState();
            case LeafTreeGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case LeafTreeGen.LEAF_TIP -> leaves(ModBlocks.LEAF_LEAVES_LIGHT.get().defaultBlockState());
            case LeafTreeGen.LEAF_DARK -> leaves(ModBlocks.LEAF_LEAVES_DARK.get().defaultBlockState());
            default -> leaves(ModBlocks.LEAF_LEAVES.get().defaultBlockState());
        };
        level.setBlock(pos, state, 2);
    }

    private static BlockState leaves(BlockState state) {
        return state.setValue(LeavesBlock.PERSISTENT, true);
    }
}
