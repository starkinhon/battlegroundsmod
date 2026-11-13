package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Terras do Jogo (Stark 2026-11-12; port released 2026-11-13), done like the other Jujutsu biomes: once per chunk
// of the biome, the preview's fields and volcano step by step (JogoGen is its line-by-line port, checked block for block
// against the preview in 8 pieces), in the preview's order:
// 1. the volcanoes: one spot per cell of 720 (35% of them) with the biome all around it and its meteor crater; each of
//    the chunk's columns of the cone built on its own (the cone's columns do not depend on each other);
// 2. the ground on the real terrain (basalt, ash, magma cracks), the lava rivers sunk 2 into their valleys with obsidian
//    banks; 3. the basalt columns, fumaroles, charred trees and hot springs on the grid of 18;
// 4. the meteor crater beside each volcano, with the molten rock in the middle.
// Writes only inside its own chunk; the ground only where the biome is.
public final class JogoFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0x10605A;
    private static final int CELL = 720;
    private static final Cache<Long, int[]> SITES = CacheBuilder.newBuilder().maximumSize(4096).build();
    private static final int[] NONE = {};

    public JogoFeature(Codec<NoneFeatureConfiguration> codec) {
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
                if (level.getBiome(pos.set(x, h + 1, z)).is(ModWorldgen.TERRAS_DO_JOGO)) {
                    inBiome[((x - x0) << 4) | (z - z0)] = true;
                    any = true;
                }
            }
        }
        if (!any) {
            return false;
        }

        // {cx, cz, base, volcanoSeed, meteorX, meteorZ, meteorSeed}
        List<int[]> volcanoes = new ArrayList<>();
        int reach = JogoGen.VOLCANO_REACH + JogoGen.OBJECT_REACH;
        for (int ci = Math.floorDiv(x0 - reach, CELL); ci <= Math.floorDiv(x1 + reach, CELL); ci++) {
            for (int cj = Math.floorDiv(z0 - reach, CELL); cj <= Math.floorDiv(z1 + reach, CELL); cj++) {
                int[] s = site(ci, cj, worldKey, seed, sea, generator, random, ground, maxY);
                if (s != NONE) {
                    volcanoes.add(s);
                }
            }
        }
        int keep = JogoGen.VOLCANO_R + 6;

        int box = JogoGen.OBJECT_REACH;
        TemploGen.Vox w = new TemploGen.Vox(x0 - box, z0 - box, x1 + box, z1 + box);

        // 1. The volcanoes' cones, column by column.
        for (int[] v : volcanoes) {
            double[] flows = JogoGen.flows(v[3]);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    JogoGen.volcanoColumn(w, x, z, v[0], v[1], v[2], v[3], flows, ground);
                }
            }
        }

        // 2. The ground and the lava rivers (the valley's top over the bed carved).
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int h = ground.applyAsInt(x, z);
                if (!inBiome[((x - x0) << 4) | (z - z0)] || h < sea || near(x, z, volcanoes, keep)) {
                    continue;
                }
                int top = JogoGen.ground0(w, x, z, h, seed, JogoGen.riverAt(x, z, seed));
                for (int y = top + 1; y <= h; y++) {
                    w.force(x, y, z, JogoGen.AIR);
                }
            }
        }

        // 3. The things on the ground.
        for (int gx = Math.floorDiv(x0 - box - 18, 18) * 18; gx <= x1 + box; gx += 18) {
            for (int gz = Math.floorDiv(z0 - box - 18, 18) * 18; gz <= z1 + box; gz += 18) {
                int[] o = JogoGen.objectSpot(gx, gz, seed);
                if (o[2] < 0 || near(o[0], o[1], volcanoes, keep) || JogoGen.riverAt(o[0], o[1], seed) < 0.08) {
                    continue;
                }
                int h = ground.applyAsInt(o[0], o[1]);
                if (h < sea + 1 || !biomeAt(generator, random, o[0], h, o[1])) {
                    continue;
                }
                JogoGen.objectAt(w, o, JogoGen.topAt(h, JogoGen.riverAt(o[0], o[1], seed)), seed);
            }
        }

        // 4. The meteor craters.
        for (int[] v : volcanoes) {
            if (Math.abs(v[4] - (x0 + 8)) > 40 || Math.abs(v[5] - (z0 + 8)) > 40) {
                continue;
            }
            JogoGen.meteorCrater(w, v[4], v[5], ground, v[6]);
        }

        // Into the world.
        w.each((x, y, z, t) -> {
            if (y < minY || y > maxY || x < x0 || x > x1 || z < z0 || z > z1) {
                return;
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
            level.setBlock(pos, block(t), 2);
        });
        return true;
    }

    private static boolean near(int x, int z, List<int[]> sites, double r) {
        for (int[] s : sites) {
            if (JjkGen.hypot(x - s[0], z - s[1]) < r) {
                return true;
            }
        }
        return false;
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.TERRAS_DO_JOGO);
    }

    private static long key(int ci, int cj, int worldKey) {
        return ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
    }

    // A cell's volcano, well inside the cell: good only on dry land with room for its 62 over the ground and the biome
    // all around the cone and around its meteor crater.
    private static int[] site(int ci, int cj, int worldKey, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator ground, int maxY) {
        try {
            return SITES.get(key(ci, cj, worldKey), () -> {
                if (JjkGen.hash3(ci, 101, cj, seed) > 0.35) {
                    return NONE;
                }
                int cx = ci * CELL + 160 + JjkGen.floor(JjkGen.hash3(ci, 102, cj, seed) * (CELL - 320));
                int cz = cj * CELL + 160 + JjkGen.floor(JjkGen.hash3(ci, 103, cj, seed) * (CELL - 320));
                int base = ground.applyAsInt(cx, cz);
                if (base < sea + 1 || base + 70 > maxY || !biomeAt(generator, random, cx, base, cz)) {
                    return NONE;
                }
                int vs = JjkGen.hashInt(ci, cj, 101, seed);
                for (int a = 0; a < 8; a++) {
                    int x = cx + JjkGen.round(Math.cos(a * Math.PI / 4) * (JogoGen.VOLCANO_R + 6));
                    int z = cz + JjkGen.round(Math.sin(a * Math.PI / 4) * (JogoGen.VOLCANO_R + 6));
                    if (!biomeAt(generator, random, x, base, z)) {
                        return NONE;
                    }
                }
                double[] flows = JogoGen.flows(vs);
                double ma = flows[0] + Math.PI + 0.6;
                int mx = JjkGen.round(cx + JjkGen.cos(ma) * 98), mz = JjkGen.round(cz + JjkGen.sin(ma) * 98);
                int my = ground.applyAsInt(mx, mz);
                if (my < sea + 2 || !biomeAt(generator, random, mx, my, mz)) {
                    return NONE;
                }
                return new int[] {cx, cz, base, vs, mx, mz, JjkGen.hashInt(ci, cj, 102, seed)};
            });
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    static BlockState block(int t) {
        return switch (t) {
            case JogoGen.STONE -> Blocks.DEEPSLATE.defaultBlockState();
            case JogoGen.BASALT -> JujutsuBlocks.JOGO_BASALT.get().defaultBlockState();
            case JogoGen.BASALT_DARK -> JujutsuBlocks.JOGO_BASALT_DARK.get().defaultBlockState();
            case JogoGen.ASH -> JujutsuBlocks.VOLCANIC_ASH.get().defaultBlockState();
            case JogoGen.MAGMA_CRACK -> JujutsuBlocks.MAGMA_CRACKED_BASALT.get().defaultBlockState();
            case JogoGen.LAVA -> Blocks.LAVA.defaultBlockState();
            case JogoGen.OBSIDIAN -> Blocks.OBSIDIAN.defaultBlockState();
            case JogoGen.COLUMN -> JujutsuBlocks.BASALT_COLUMN.get().defaultBlockState();
            case JogoGen.COLUMN_TOP -> JujutsuBlocks.COLUMN_TOP.get().defaultBlockState();
            case JogoGen.SULFUR -> JujutsuBlocks.SULFUR.get().defaultBlockState();
            case JogoGen.CHAR -> JujutsuBlocks.CHARRED_WOOD.get().defaultBlockState();
            case JogoGen.MAGMA_ROCK -> JujutsuBlocks.MOLTEN_ROCK.get().defaultBlockState();
            case JogoGen.CINDER -> JujutsuBlocks.CINDER.get().defaultBlockState();
            case JogoGen.SPRING -> Blocks.WATER.defaultBlockState();
            case JogoGen.SPRING_STONE -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case JogoGen.IRON -> JujutsuBlocks.IRON_BAND.get().defaultBlockState();
            case JogoGen.BLACKSTONE -> Blocks.BLACKSTONE.defaultBlockState();
            default -> Blocks.DEEPSLATE.defaultBlockState();
        };
    }
}
