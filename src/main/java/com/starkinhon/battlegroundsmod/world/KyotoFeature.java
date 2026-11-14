package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
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

// The Outono de Kyoto (Stark 2026-11-12; port released 2026-11-13), done like the other Jujutsu biomes: once per chunk
// of the biome, the preview's hills and pond garden step by step (KyotoGen is its line-by-line port, checked block for
// block against the preview in 8 pieces), in the preview's order:
// 1. the ground's top on the real terrain (grass with patches of moss);
// 2. the maples, ginkgos and pines on the grid of 8 (each carpeting the ground around it with its fallen leaves);
// 3. the bamboo groves with their gravel trail; 4. the streams with water and mossy stones;
// 5. the pond gardens: one spot per cell of 400 (40% of them) with the biome all around; built once and kept (the pond
//    with its islands, the Golden Pavilion, the bridge, the tea house, the lanterns and maples).
// Writes only inside its own chunk; the ground only where the biome is.
public final class KyotoFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0x6A0700;
    private static final int CELL = 400;
    private static final Cache<Long, Built> GARDENS = CacheBuilder.newBuilder().maximumSize(16).build();
    private static final Cache<Long, int[]> SITES = CacheBuilder.newBuilder().maximumSize(4096).build();
    private static final int[] NONE = {};

    private record Built(int[] vox) {
    }

    public KyotoFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        int seed = worldKey ^ SALT;
        int sea = generator.getSeaLevel() - 1;
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator ground = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;

        boolean any = false;
        boolean[] inBiome = new boolean[256];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int h = ground.applyAsInt(x, z);
                if (level.getBiome(pos.set(x, h + 1, z)).is(ModWorldgen.OUTONO_DE_KYOTO)) {
                    inBiome[((x - x0) << 4) | (z - z0)] = true;
                    any = true;
                }
            }
        }
        if (!any) {
            return false;
        }

        List<int[]> gardens = new ArrayList<>();
        int reach = KyotoGen.GARDEN_REACH + KyotoGen.TREE_REACH;
        for (int ci = Math.floorDiv(x0 - reach, CELL); ci <= Math.floorDiv(x1 + reach, CELL); ci++) {
            for (int cj = Math.floorDiv(z0 - reach, CELL); cj <= Math.floorDiv(z1 + reach, CELL); cj++) {
                int[] s = site(ci, cj, worldKey, seed, sea, generator, random, ground, maxY);
                if (s != NONE) {
                    gardens.add(s);
                }
            }
        }

        TemploGen.Vox w = new TemploGen.Vox(x0, z0, x1, z1);

        // 1. The ground's top.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int h = ground.applyAsInt(x, z);
                if (inBiome[((x - x0) << 4) | (z - z0)] && h >= sea && !kept(x, z, gardens)) {
                    KyotoGen.ground0(w, x, z, h, seed);
                }
            }
        }

        // 2. The trees.
        int tr = KyotoGen.TREE_REACH;
        for (int gx = Math.floorDiv(x0 - tr - 8, 8) * 8; gx <= x1 + tr; gx += 8) {
            for (int gz = Math.floorDiv(z0 - tr - 8, 8) * 8; gz <= z1 + tr; gz += 8) {
                int[] s = KyotoGen.treeSpot(gx, gz, seed);
                if (s == null || kept(s[0], s[1], gardens)) {
                    continue;
                }
                int h = ground.applyAsInt(s[0], s[1]);
                if (h < sea + 1 || h + 30 > maxY || !biomeAt(generator, random, s[0], h, s[1])) {
                    continue;
                }
                KyotoGen.treeAt(w, s, h, seed, ground);
            }
        }

        // 3. The bamboo groves (a culm's leaves spread 1).
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (!KyotoGen.grove(x, z, seed) || kept(x, z, gardens)) {
                    continue;
                }
                int h = ground.applyAsInt(x, z);
                if (h < sea + 1 || !(w.in(x, z) ? inBiome[((x - x0) << 4) | (z - z0)] : biomeAt(generator, random, x, h, z))) {
                    continue;
                }
                KyotoGen.groveAt(w, x, z, h, seed);
            }
        }

        // 4. The streams.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!KyotoGen.stream(x, z, seed) || kept(x, z, gardens) || !inBiome[((x - x0) << 4) | (z - z0)]) {
                    continue;
                }
                int h = ground.applyAsInt(x, z);
                if (h >= sea) {
                    KyotoGen.streamAt(w, x, z, h, seed);
                }
            }
        }

        // 5. The pond gardens over it all.
        for (int[] s : gardens) {
            if (JjkGen.hypot(Math.max(0, Math.max(x0 - s[0], s[0] - x1)), Math.max(0, Math.max(z0 - s[1], s[1] - z1)))
                    > KyotoGen.GARDEN_REACH + 4) {
                continue;
            }
            Built b = garden(s, worldKey, ground);
            if (b == null) {
                continue;
            }
            int[] v = b.vox();
            for (int i = 0; i < v.length; i += 4) {
                w.force(v[i], v[i + 1], v[i + 2], (byte) v[i + 3]);
            }
        }

        // Into the world.
        FluidEdges edges = new FluidEdges(); // water and lava spill over their steps (FluidEdges)
        w.each((x, y, z, t) -> {
            if (y < minY || y > maxY) {
                return;
            }
            if (groundLike(t) && !inBiome[((x - x0) << 4) | (z - z0)] && !kept(x, z, gardens)) {
                return; // the ground stops at the biome's edge; a tree over it does not get cut
            }
            pos.set(x, y, z);
            BlockState there = level.getBlockState(pos);
            if (there.is(Blocks.BEDROCK)) {
                return;
            }
            if (t == TemploGen.CARVE) {
                if (!there.isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                return;
            }
            boolean open = there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty();
            if (soft(t) && !open) {
                return; // leaves and plants only grow into the air, as the preview's set
            }
            edges.set(level, pos, block(t), 2);
        });
        edges.apply(level, 2);
        return true;
    }

    private static boolean kept(int x, int z, List<int[]> gardens) {
        for (int[] s : gardens) {
            if (KyotoGen.gardenKeep(x, z, s[0], s[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.OUTONO_DE_KYOTO);
    }

    private static long key(int ci, int cj, int worldKey) {
        return ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
    }

    // A cell's pond garden, well inside the cell: good only on dry land with room for the pavilion and the biome all
    // around as far as the tea house and the maples. {cx, cz, gardenSeed} or NONE.
    private static int[] site(int ci, int cj, int worldKey, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator ground, int maxY) {
        try {
            return SITES.get(key(ci, cj, worldKey), () -> {
                if (JjkGen.hash3(ci, 111, cj, seed) > 0.4) {
                    return NONE;
                }
                int cx = ci * CELL + 100 + JjkGen.floor(JjkGen.hash3(ci, 112, cj, seed) * (CELL - 200));
                int cz = cj * CELL + 100 + JjkGen.floor(JjkGen.hash3(ci, 113, cj, seed) * (CELL - 200));
                int gy = ground.applyAsInt(cx, cz);
                if (gy < sea + 3 || gy + 40 > maxY || !biomeAt(generator, random, cx, gy, cz)) {
                    return NONE;
                }
                for (int a = 0; a < 8; a++) {
                    int x = cx + JjkGen.round(Math.cos(a * Math.PI / 4) * KyotoGen.GARDEN_REACH);
                    int z = cz + JjkGen.round(Math.sin(a * Math.PI / 4) * KyotoGen.GARDEN_REACH * 0.8);
                    if (!biomeAt(generator, random, x, gy, z)) {
                        return NONE;
                    }
                }
                return new int[] {cx, cz, JjkGen.hashInt(ci, cj, 111, seed)};
            });
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    private static Built garden(int[] s, int worldKey, IntBinaryOperator ground) {
        try {
            return GARDENS.get(((long) s[0] << 32) ^ (s[1] & 0xFFFFFFFFL) ^ ((long) worldKey * 0x9E3779B97F4A7C15L), () -> {
                TemploGen.Vox v = new TemploGen.Vox();
                KyotoGen.garden(v, s[0], s[1], ground, s[2]);
                return new Built(v.pack());
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static boolean groundLike(int t) {
        return t == KyotoGen.STONE || t == KyotoGen.DIRT || t == KyotoGen.GRASS || t == KyotoGen.MOSS || t == KyotoGen.LITTER_R
                || t == KyotoGen.LITTER_O || t == KyotoGen.LITTER_Y || t == KyotoGen.PATH || t == KyotoGen.WATER || t == KyotoGen.GRAVEL
                || t == KyotoGen.FERN || t == KyotoGen.ROCK_MOSS || t == TemploGen.CARVE;
    }

    private static boolean soft(int t) {
        return t == KyotoGen.MAPLE_R || t == KyotoGen.MAPLE_R2 || t == KyotoGen.MAPLE_O || t == KyotoGen.MAPLE_Y || t == KyotoGen.GINKGO
                || t == KyotoGen.PINE || t == KyotoGen.BAMBOO_LEAF || t == KyotoGen.FERN;
    }

    private static BlockState leaves(BlockState b) {
        return b.setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState block(int t) {
        return switch (t) {
            case KyotoGen.STONE -> Blocks.STONE.defaultBlockState();
            case KyotoGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case KyotoGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case KyotoGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case KyotoGen.LITTER_R -> JujutsuBlocks.LITTER_RED.get().defaultBlockState();
            case KyotoGen.LITTER_O -> JujutsuBlocks.LITTER_ORANGE.get().defaultBlockState();
            case KyotoGen.LITTER_Y -> JujutsuBlocks.LITTER_GOLD.get().defaultBlockState();
            case KyotoGen.BARK -> JujutsuBlocks.MAPLE_TRUNK.get().defaultBlockState();
            case KyotoGen.BARK_DARK -> JujutsuBlocks.KYOTO_DARK_BARK.get().defaultBlockState();
            case KyotoGen.MAPLE_R -> leaves(JujutsuBlocks.MAPLE_RED.get().defaultBlockState());
            case KyotoGen.MAPLE_R2 -> leaves(JujutsuBlocks.MAPLE_CRIMSON.get().defaultBlockState());
            case KyotoGen.MAPLE_O -> leaves(JujutsuBlocks.MAPLE_ORANGE.get().defaultBlockState());
            case KyotoGen.MAPLE_Y -> leaves(JujutsuBlocks.MAPLE_GOLD.get().defaultBlockState());
            case KyotoGen.GINKGO -> leaves(JujutsuBlocks.GINKGO_LEAVES.get().defaultBlockState());
            case KyotoGen.PINE -> leaves(JujutsuBlocks.JAPANESE_PINE_NEEDLES.get().defaultBlockState());
            case KyotoGen.BAMBOO -> Blocks.BAMBOO_BLOCK.defaultBlockState();
            case KyotoGen.BAMBOO_LEAF -> leaves(JujutsuBlocks.BAMBOO_LEAVES.get().defaultBlockState());
            case KyotoGen.WATER -> Blocks.WATER.defaultBlockState();
            case KyotoGen.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case KyotoGen.PATH -> ModBlocks.WHITE_GRAVEL.get().defaultBlockState();
            case KyotoGen.LANTERN -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case KyotoGen.LANTERN_LIT -> ModBlocks.STONE_LANTERN_LIGHT.get().defaultBlockState();
            case KyotoGen.WOOD -> JujutsuBlocks.KYOTO_LIGHT_WOOD.get().defaultBlockState();
            case KyotoGen.WOOD_DARK -> JujutsuBlocks.FARM_WOOD_DARK.get().defaultBlockState();
            case KyotoGen.SHOJI -> ModBlocks.SHOJI.get().defaultBlockState();
            case KyotoGen.GOLD -> JujutsuBlocks.GOLD_LEAF.get().defaultBlockState();
            case KyotoGen.GOLD_DARK -> JujutsuBlocks.DARK_GOLD.get().defaultBlockState();
            case KyotoGen.ROOF -> JujutsuBlocks.BARK_ROOF.get().defaultBlockState();
            case KyotoGen.ROOF_EDGE -> JujutsuBlocks.BARK_EAVE.get().defaultBlockState();
            case KyotoGen.PLASTER -> ModBlocks.PLASTER.get().defaultBlockState();
            case KyotoGen.BRIDGE -> JujutsuBlocks.BRIDGE_WOOD.get().defaultBlockState();
            case KyotoGen.ROCK -> Blocks.ANDESITE.defaultBlockState();
            case KyotoGen.ROCK_MOSS -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case KyotoGen.TATAMI -> JujutsuBlocks.TATAMI.get().defaultBlockState();
            case KyotoGen.FERN -> Blocks.FERN.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
