package com.starkinhon.battlegroundsmod.world;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Floresta da Morte (Stark 2026-11-11, from the approved preview), done like the Floresta da Folha's woods
// (LeafForestFeature): once per chunk of the biome, the part of every giant, crooked tree, giant mushroom, carnivorous
// flower, fallen trunk and bush that reaches the chunk (DeathTreeGen's cells, the same seed in every chunk so the pieces
// meet); then the ground: still dark water where the pool noise runs low, mud on its shore, and on the rest rotten
// leaves, ferns, bare roots and small glowing mushrooms in the shade. Writes only inside its own chunk.
// A pool only fills where the water can't run off: every neighbor's ground as high as its own or higher (a higher
// neighbor can't be water itself, since this one is lower), so nothing flows when the world updates it later.
public final class DeathForestFeature extends Feature<NoneFeatureConfiguration> {
    private static final Cache<Long, int[]> TREES = CacheBuilder.newBuilder().maximumSize(400).build();

    public DeathForestFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        // its own layout, not lined up with the Folha's cells
        int seed = worldKey ^ 0x5EED0D1E;
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator height = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z);
        IntBinaryOperator floorY = (x, z) -> height.applyAsInt(x, z) - 1;

        List<DeathTreeGen.Obj> objs = DeathTreeGen.objectsNear(x0, z0, x1, z1, seed, o -> good(o, generator, random, height, seed));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (DeathTreeGen.Obj o : objs) {
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
                int y = height.applyAsInt(x, z);
                if (!level.getBiome(pos.set(x, y, z)).is(ModWorldgen.FLORESTA_DA_MORTE)) {
                    continue;
                }
                pos.set(x, y - 1, z);
                if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                    continue;
                }
                double p = DeathTreeGen.poolAt(x, z, seed);
                if (p < DeathTreeGen.SHORE) {
                    boolean water = p < DeathTreeGen.POOL && level.getBlockState(pos.set(x, y, z)).isAir() && contained(height, x, z, y);
                    if (water) {
                        boolean deep = p < DeathTreeGen.POOL - 0.06;
                        level.setBlock(pos.set(x, y - 1, z), Blocks.WATER.defaultBlockState(), 2);
                        if (deep) {
                            level.setBlock(pos.set(x, y - 2, z), Blocks.WATER.defaultBlockState(), 2);
                        }
                        level.setBlock(pos.set(x, y - (deep ? 3 : 2), z), Blocks.MUD.defaultBlockState(), 2);
                    } else {
                        level.setBlock(pos.set(x, y - 1, z), Blocks.MUD.defaultBlockState(), 2);
                    }
                    continue;
                }
                pos.set(x, y, z);
                if (!level.getBlockState(pos).isAir()) {
                    continue;
                }
                boolean shaded = false;
                for (int k = 4; k < 70 && !shaded; k++) {
                    shaded = level.getBlockState(pos.set(x, y + k, z)).is(BlockTags.LEAVES);
                }
                byte deco = DeathTreeGen.groundDeco(x, z, shaded, seed);
                pos.set(x, y, z);
                if (deco == DeathTreeGen.LITTER) {
                    level.setBlock(pos, ModBlocks.ROTTEN_LEAVES.get().defaultBlockState(), 2);
                } else if (deco == DeathTreeGen.FERN) {
                    level.setBlock(pos, Blocks.FERN.defaultBlockState(), 2);
                } else if (deco == DeathTreeGen.GLOWSHROOM) {
                    level.setBlock(pos, ModBlocks.GLOW_MUSHROOM.get().defaultBlockState(), 2);
                } else if (deco == DeathTreeGen.ROOTS) {
                    level.setBlock(pos, ModBlocks.BARE_ROOTS.get().defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    // Can a pool hold water here: all four neighbors' ground at least as high.
    private static boolean contained(IntBinaryOperator height, int x, int z, int y) {
        return height.applyAsInt(x + 1, z) >= y && height.applyAsInt(x - 1, z) >= y && height.applyAsInt(x, z + 1) >= y
                && height.applyAsInt(x, z - 1) >= y;
    }

    private static int[] voxels(DeathTreeGen.Obj o, int foot, IntBinaryOperator floorY) {
        long key = ((long) o.seed() << 20) ^ ((long) o.x() * 341873128712L) ^ ((long) o.z() * 132897987541L) ^ ((long) foot << 8)
                ^ o.kind().ordinal();
        try {
            return TREES.get(key, () -> DeathTreeGen.build(o, foot, floorY).pack());
        } catch (ExecutionException e) {
            return new int[0];
        }
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.FLORESTA_DA_MORTE);
    }

    // Is the spot good: dry land off the pools, in the biome (the biome all around as far as the thing reaches) and, for a
    // giant or a fallen trunk, not too steep.
    private static boolean good(DeathTreeGen.Obj o, ChunkGenerator generator, RandomState random, IntBinaryOperator height, int seed) {
        int y = height.applyAsInt(o.x(), o.z());
        double margin = o.kind() == DeathTreeGen.Kind.GIANT ? 0.02 : 0;
        if (y <= generator.getSeaLevel() || DeathTreeGen.poolAt(o.x(), o.z(), seed) <= DeathTreeGen.SHORE + margin
                || !biomeAt(generator, random, o.x(), y, o.z())) {
            return false;
        }
        int reach = o.kind().reach();
        for (int a = 0; a < 8; a++) {
            int x = o.x() + (int) Math.round(Math.cos(a * Math.PI / 4) * reach);
            int z = o.z() + (int) Math.round(Math.sin(a * Math.PI / 4) * reach);
            if (!biomeAt(generator, random, x, y, z)) {
                return false;
            }
        }
        if (o.kind() == DeathTreeGen.Kind.GIANT || o.kind() == DeathTreeGen.Kind.LOG) {
            int lo = y, hi = y;
            for (int a = 0; a < 4; a++) {
                int h = height.applyAsInt(o.x() + (a % 2 == 0 ? 5 : -5), o.z() + (a < 2 ? 5 : -5));
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            if (hi - lo > (o.kind() == DeathTreeGen.Kind.GIANT ? 6 : 2) || lo <= generator.getSeaLevel()) {
                return false;
            }
        }
        return true;
    }

    // A voxel into the world. Wood, mushroom and flower go through leaves, plants and the ground's dirt (roots and the
    // flower's mouth go into it); lianas through leaves and plants; leaves and vines only into air and plants.
    private static void put(WorldGenLevel level, BlockPos pos, int type) {
        BlockState there = level.getBlockState(pos);
        boolean solid = type == DeathTreeGen.BARK || type == DeathTreeGen.CORE || type == DeathTreeGen.MOSS
                || type == DeathTreeGen.STEM || type == DeathTreeGen.CAP || type == DeathTreeGen.SPOT || type == DeathTreeGen.GILL
                || type == DeathTreeGen.PETAL || type == DeathTreeGen.PETAL_SPOT || type == DeathTreeGen.MAW
                || type == DeathTreeGen.TOOTH;
        if (solid) {
            if (!(there.canBeReplaced() || there.is(BlockTags.LEAVES) || there.is(BlockTags.DIRT) || there.is(Blocks.DIRT_PATH)
                    || there.is(ModBlocks.HANGING_VINE.get()))) {
                return;
            }
        } else if (type == DeathTreeGen.LIANA) {
            if (!(there.canBeReplaced() || there.is(BlockTags.LEAVES) || there.is(ModBlocks.HANGING_VINE.get()))
                    || !there.getFluidState().isEmpty()) {
                return;
            }
        } else if (!there.canBeReplaced() || !there.getFluidState().isEmpty()) {
            return;
        }
        BlockState state = switch (type) {
            case DeathTreeGen.BARK -> Blocks.DARK_OAK_WOOD.defaultBlockState();
            case DeathTreeGen.CORE -> Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();
            case DeathTreeGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case DeathTreeGen.LIANA -> ModBlocks.LIANA.get().defaultBlockState();
            case DeathTreeGen.VINE -> ModBlocks.HANGING_VINE.get().defaultBlockState();
            case DeathTreeGen.STEM, DeathTreeGen.SPOT -> Blocks.MUSHROOM_STEM.defaultBlockState();
            case DeathTreeGen.CAP -> ModBlocks.MUSHROOM_CAP.get().defaultBlockState();
            case DeathTreeGen.GILL -> Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState();
            case DeathTreeGen.PETAL -> ModBlocks.PETAL.get().defaultBlockState();
            case DeathTreeGen.PETAL_SPOT -> ModBlocks.PETAL_SPOTTED.get().defaultBlockState();
            case DeathTreeGen.MAW -> ModBlocks.MAW.get().defaultBlockState();
            case DeathTreeGen.TOOTH -> Blocks.BONE_BLOCK.defaultBlockState();
            case DeathTreeGen.LEAF_TIP -> leaves(ModBlocks.DEATH_LEAVES_LIGHT.get().defaultBlockState());
            case DeathTreeGen.LEAF_DARK -> leaves(ModBlocks.DEATH_LEAVES_DARK.get().defaultBlockState());
            case DeathTreeGen.LEAF -> leaves(ModBlocks.DEATH_LEAVES.get().defaultBlockState());
            default -> null;
        };
        if (state != null) {
            level.setBlock(pos, state, 2);
        }
    }

    private static BlockState leaves(BlockState state) {
        return state.setValue(LeavesBlock.PERSISTENT, true);
    }
}
