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
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Mar de Dagon (Stark 2026-11-12; port released 2026-11-13), done like the other Jujutsu biomes: once per chunk of
// the biome, the preview's coast and Nest step by step (DagonGen is its line-by-line port, checked block for block
// against the preview in 8 pieces), in the preview's order:
// 1. the ground on the real terrain (white sand, wet sand at the waterline, gravel deep down, grass on the high islets,
//    water up to the sea, loose rocks);
// 2. the life in the shallows (glowing coral, seagrass) and on the sand (shells, starfish);
// 3. the coconut palms on the grid of 7; 4. the giant fish skeletons on the grid of 50;
// 5. the Nests: one spot per cell of 480 (40% of them) out in the water with the biome all around; built once and kept,
//    on the preview's ring of sandbank (the ground rising around it at 66 as in the preview).
// Writes only inside its own chunk; the coast's ground only where the biome is.
public final class DagonFeature extends Feature<NoneFeatureConfiguration> {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int SALT = 0xDA60E5;
    private static final int CELL = 480;
    private static final int RING = 84;
    private static final Cache<Long, Built> NESTS = CacheBuilder.newBuilder().maximumSize(16).build();
    private static final Cache<Long, int[]> SITES = CacheBuilder.newBuilder().maximumSize(4096).build();
    private static final int[] NONE = {};

    private record Built(int[] vox) {
    }

    public DagonFeature(Codec<NoneFeatureConfiguration> codec) {
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
        IntBinaryOperator real = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;

        boolean any = false;
        boolean[] inBiome = new boolean[256];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int h = Math.max(real.applyAsInt(x, z), sea);
                if (level.getBiome(pos.set(x, h + 1, z)).is(ModWorldgen.MAR_DE_DAGON)) {
                    inBiome[((x - x0) << 4) | (z - z0)] = true;
                    any = true;
                }
            }
        }
        if (!any) {
            return false;
        }

        // The Nests near enough to raise the ring of sand or reach the chunk.
        List<int[]> nests = new ArrayList<>();
        int reach = Math.max(RING, DagonGen.NEST_REACH) + DagonGen.FISH_REACH;
        for (int ci = Math.floorDiv(x0 - reach, CELL); ci <= Math.floorDiv(x1 + reach, CELL); ci++) {
            for (int cj = Math.floorDiv(z0 - reach, CELL); cj <= Math.floorDiv(z1 + reach, CELL); cj++) {
                int[] s = site(ci, cj, worldKey, seed, sea, generator, random, real);
                if (s != NONE) {
                    nests.add(s);
                }
            }
        }
        // the preview's ring of islets around a Nest (+4 at 66 from its center)
        IntBinaryOperator ground = (x, z) -> {
            int g = real.applyAsInt(x, z);
            for (int[] s : nests) {
                double d = JjkGen.hypot(x - s[0], z - s[1]);
                if (d < RING) {
                    g += JjkGen.round(Math.max(0, 1 - Math.abs(d - 66) / 18) * 4);
                }
            }
            return g;
        };
        IntBinaryOperator kept = (x, z) -> near(x, z, nests, DagonGen.NEST_KEEP) ? 1 : 0;

        // 1. The ground, in a box a little wider than the chunk (a rock spreads 1, a coral 3, and they look at the water).
        int box = DagonGen.CORAL_REACH + 1;
        TemploGen.Vox w = new TemploGen.Vox(x0 - box, z0 - box, x1 + box, z1 + box);
        for (int x = x0 - box; x <= x1 + box; x++) {
            for (int z = z0 - box; z <= z1 + box; z++) {
                if (kept.applyAsInt(x, z) == 0) {
                    DagonGen.ground0(w, x, z, ground.applyAsInt(x, z), seed, sea);
                }
            }
        }
        // 2. The life.
        int cr = DagonGen.CORAL_REACH;
        for (int x = x0 - cr; x <= x1 + cr; x++) {
            for (int z = z0 - cr; z <= z1 + cr; z++) {
                if (kept.applyAsInt(x, z) == 0) {
                    DagonGen.life(w, x, z, ground.applyAsInt(x, z), seed, sea);
                }
            }
        }
        // 3. The palms.
        int pr = DagonGen.PALM_REACH;
        for (int gx = Math.floorDiv(x0 - pr - 7, 7) * 7; gx <= x1 + pr; gx += 7) {
            for (int gz = Math.floorDiv(z0 - pr - 7, 7) * 7; gz <= z1 + pr; gz += 7) {
                int[] s = DagonGen.palmSpot(gx, gz, seed);
                if (kept.applyAsInt(s[0], s[1]) != 0) {
                    continue;
                }
                int h = ground.applyAsInt(s[0], s[1]);
                if (!DagonGen.palmHere(s[0], s[1], h, seed, sea) || h + 20 > maxY || !biomeAt(generator, random, s[0], h, s[1])) {
                    continue;
                }
                DagonGen.palmAt(w, s[0], s[1], h, seed);
            }
        }
        // 4. The fish stranded on the sand at the water's edge.
        int fr = DagonGen.FISH_REACH;
        for (int gx = Math.floorDiv(x0 - fr - 40, 50) * 50; gx <= x1 + fr; gx += 50) {
            for (int gz = Math.floorDiv(z0 - fr - 40, 50) * 50; gz <= z1 + fr; gz += 50) {
                int[] s = DagonGen.fishSpot(gx, gz, seed);
                if (s == null || kept.applyAsInt(s[0], s[1]) != 0) {
                    continue;
                }
                int h = ground.applyAsInt(s[0], s[1]);
                if (h < sea || h > sea + 1 || !biomeAt(generator, random, s[0], h, s[1])) {
                    continue;
                }
                DagonGen.fishAt(w, s[0], s[1], h, seed);
            }
        }
        // 5. The Nests over it all.
        for (int[] s : nests) {
            if (JjkGen.hypot(Math.max(0, Math.max(x0 - s[0], s[0] - x1)), Math.max(0, Math.max(z0 - s[1], s[1] - z1)))
                    > DagonGen.NEST_REACH) {
                continue;
            }
            Built b = nest(s, worldKey, sea, ground);
            if (b == null) {
                continue;
            }
            int[] v = b.vox();
            for (int i = 0; i < v.length; i += 4) {
                w.force(v[i], v[i + 1], v[i + 2], (byte) v[i + 3]);
            }
        }

        // Into the world.
        w.each((x, y, z, t) -> {
            if (y < minY || y > maxY || x < x0 || x > x1 || z < z0 || z > z1) {
                return;
            }
            if (coastal(t) && !inBiome[((x - x0) << 4) | (z - z0)] && !near(x, z, nests, DagonGen.NEST_REACH)) {
                return; // the coast's ground and reef stop at the biome's edge; a palm or a skeleton over it does not get cut
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
                return; // fronds and things lying on the sand only go into the air, as the preview's set
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
                random.sampler()).is(ModWorldgen.MAR_DE_DAGON);
    }

    private static long key(int ci, int cj, int worldKey) {
        return ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
    }

    // A cell's Nest spot, well inside the cell: good only out in the water (the bottom at least 2 under the sea) with the
    // biome around as far as the tentacles curl (7 of 8 points). {cx, cz, nestSeed} or NONE.
    private static int[] site(int ci, int cj, int worldKey, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator real) {
        try {
            return SITES.get(key(ci, cj, worldKey), () -> {
                if (JjkGen.hash3(ci, 81, cj, seed) > 0.4) {
                    return NONE;
                }
                int cx = ci * CELL + 120 + JjkGen.floor(JjkGen.hash3(ci, 82, cj, seed) * (CELL - 240));
                int cz = cj * CELL + 120 + JjkGen.floor(JjkGen.hash3(ci, 83, cj, seed) * (CELL - 240));
                int gy = real.applyAsInt(cx, cz);
                if (gy > sea - 2 || gy < sea - 40 || !biomeAt(generator, random, cx, sea, cz)) {
                    return NONE;
                }
                int miss = 0;
                for (int a = 0; a < 8; a++) {
                    int x = cx + JjkGen.round(Math.cos(a * Math.PI / 4) * DagonGen.NEST_REACH);
                    int z = cz + JjkGen.round(Math.sin(a * Math.PI / 4) * DagonGen.NEST_REACH);
                    if (!biomeAt(generator, random, x, sea, z) && ++miss > 1) {
                        return NONE;
                    }
                }
                LOGGER.info("[jjk] Ninho de Dagon em {} {}", cx, cz);
                return new int[] {cx, cz, JjkGen.hashInt(ci, cj, 81, seed)};
            });
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    // A Nest built in the world's coordinates, as the preview builds it.
    private static Built nest(int[] s, int worldKey, int sea, IntBinaryOperator ground) {
        try {
            return NESTS.get(((long) s[0] << 32) ^ (s[1] & 0xFFFFFFFFL) ^ ((long) worldKey * 0x9E3779B97F4A7C15L), () -> {
                TemploGen.Vox v = new TemploGen.Vox();
                DagonGen.nest(v, s[0], s[1], s[2], ground, sea);
                return new Built(v.pack());
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static boolean coastal(int t) {
        return t != DagonGen.PALM && t != DagonGen.FROND && t != DagonGen.FROND2 && t != DagonGen.COCONUT && t != DagonGen.BONE
                && t != TemploGen.CARVE;
    }

    private static boolean soft(int t) {
        return t == DagonGen.FROND || t == DagonGen.FROND2 || t == DagonGen.COCONUT || t == DagonGen.SHELL || t == DagonGen.STAR;
    }

    private static BlockState leaves(BlockState b) {
        return b.setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState block(int t) {
        return switch (t) {
            case DagonGen.STONE -> Blocks.STONE.defaultBlockState();
            case DagonGen.SAND -> JujutsuBlocks.DAGON_SAND.get().defaultBlockState();
            case DagonGen.SAND_WET -> JujutsuBlocks.WET_SAND.get().defaultBlockState();
            case DagonGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case DagonGen.WATER -> Blocks.WATER.defaultBlockState();
            case DagonGen.PALM -> JujutsuBlocks.PALM_TRUNK.get().defaultBlockState();
            case DagonGen.FROND -> leaves(JujutsuBlocks.PALM_FROND.get().defaultBlockState());
            case DagonGen.FROND2 -> leaves(JujutsuBlocks.PALM_FROND_LIGHT.get().defaultBlockState());
            case DagonGen.COCONUT -> JujutsuBlocks.COCONUT.get().defaultBlockState();
            case DagonGen.CORAL_PINK -> JujutsuBlocks.CORAL_PINK.get().defaultBlockState();
            case DagonGen.CORAL_ORANGE -> JujutsuBlocks.CORAL_ORANGE.get().defaultBlockState();
            case DagonGen.CORAL_PURPLE -> JujutsuBlocks.CORAL_PURPLE.get().defaultBlockState();
            case DagonGen.CORAL_YELLOW -> JujutsuBlocks.CORAL_YELLOW.get().defaultBlockState();
            case DagonGen.SEAGRASS -> Blocks.SEAGRASS.defaultBlockState();
            case DagonGen.SHELL -> JujutsuBlocks.SEASHELL.get().defaultBlockState();
            case DagonGen.STAR -> JujutsuBlocks.STARFISH.get().defaultBlockState();
            case DagonGen.BONE -> Blocks.BONE_BLOCK.defaultBlockState();
            case DagonGen.TENTACLE -> JujutsuBlocks.DAGON_TENTACLE.get().defaultBlockState();
            case DagonGen.TENTACLE_DARK -> JujutsuBlocks.DAGON_TENTACLE_DARK.get().defaultBlockState();
            case DagonGen.SUCKER -> JujutsuBlocks.SUCKER.get().defaultBlockState();
            case DagonGen.ABYSS -> JujutsuBlocks.ABYSS.get().defaultBlockState();
            case DagonGen.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case DagonGen.ROCK -> Blocks.ANDESITE.defaultBlockState();
            case DagonGen.PRISMARINE -> Blocks.PRISMARINE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
