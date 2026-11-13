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
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Floresta de Hanami (Stark 2026-11-12; port released 2026-11-13), done like the other Jujutsu biomes: once per
// chunk of the biome, the preview's woods and Heart step by step (HanamiGen is its line-by-line port, checked block for
// block against the preview in 8 pieces), in the preview's order:
// 1. the ground's top on the real terrain (living grass with patches of moss);
// 2. the ancient trees on the grid of 10, every one whose blocks can reach the box around the chunk;
// 3. the ground objects on the grid of 24 (giant mushrooms and flowers, mossy logs, fans of stakes), each on the top it
//    finds after the trees; 4. the flowers, ferns and tall grass on every column;
// 5. the Hearts: one spot per cell of 420 (45% of them) with the biome all around; the canopy over the meadow trimmed,
//    then the clearing and the colossal tree (built once and kept), then the dense flowers of the meadow.
// Writes only inside its own chunk; the ground and the plants only where the biome is.
public final class HanamiFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0x4A7A31;
    private static final int CELL = 420;
    private static final Cache<Long, Built> HEARTS = CacheBuilder.newBuilder().maximumSize(16).build();
    private static final Cache<Long, int[]> SITES = CacheBuilder.newBuilder().maximumSize(4096).build();
    private static final int[] NONE = {};

    private record Built(int[] vox) {
    }

    public HanamiFeature(Codec<NoneFeatureConfiguration> codec) {
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
                if (level.getBiome(pos.set(x, h + 1, z)).is(ModWorldgen.FLORESTA_DE_HANAMI)) {
                    inBiome[((x - x0) << 4) | (z - z0)] = true;
                    any = true;
                }
            }
        }
        if (!any) {
            return false;
        }

        List<int[]> hearts = new ArrayList<>();
        int reach = HanamiGen.HEART_REACH + HanamiGen.TREE_REACH;
        for (int ci = Math.floorDiv(x0 - reach, CELL); ci <= Math.floorDiv(x1 + reach, CELL); ci++) {
            for (int cj = Math.floorDiv(z0 - reach, CELL); cj <= Math.floorDiv(z1 + reach, CELL); cj++) {
                int[] s = site(ci, cj, worldKey, seed, sea, generator, random, ground, maxY);
                if (s != NONE) {
                    hearts.add(s);
                }
            }
        }

        // 1. The ground's top, in the box the ground objects can stand in (they look for the top after the trees).
        int box = HanamiGen.OBJECT_REACH;
        TemploGen.Vox w = new TemploGen.Vox(x0 - box, z0 - box, x1 + box, z1 + box);
        for (int x = x0 - box; x <= x1 + box; x++) {
            for (int z = z0 - box; z <= z1 + box; z++) {
                int h = ground.applyAsInt(x, z);
                if (h >= sea) {
                    HanamiGen.ground0(w, x, z, h, seed);
                }
            }
        }

        // 2. The ancient trees.
        int tr = HanamiGen.TREE_REACH;
        for (int gx = Math.floorDiv(x0 - box - tr - 10, 10) * 10; gx <= x1 + box + tr; gx += 10) {
            for (int gz = Math.floorDiv(z0 - box - tr - 10, 10) * 10; gz <= z1 + box + tr; gz += 10) {
                int[] s = HanamiGen.treeSpot(gx, gz, seed);
                if (s == null || near(s[0], s[1], hearts, HanamiGen.HEART_KEEP)) {
                    continue;
                }
                int h = ground.applyAsInt(s[0], s[1]);
                if (h < sea + 1 || h + 70 > maxY || !biomeAt(generator, random, s[0], h, s[1])) {
                    continue;
                }
                HanamiGen.treeAt(w, s[0], s[1], h, seed);
            }
        }

        // 3. The ground objects, each on the top it finds.
        for (int gx = Math.floorDiv(x0 - box - 24, 24) * 24; gx <= x1 + box; gx += 24) {
            for (int gz = Math.floorDiv(z0 - box - 24, 24) * 24; gz <= z1 + box; gz += 24) {
                int[] o = HanamiGen.objectSpot(gx, gz, seed);
                if (o[2] < 0 || !w.in(o[0], o[1]) || near(o[0], o[1], hearts, HanamiGen.HEART_KEEP)) {
                    continue;
                }
                int h = HanamiGen.topOf(w, o[0], o[1], ground);
                if (h < sea + 1 || w.get(o[0], h + 1, o[1]) != 0 || !biomeAt(generator, random, o[0], h, o[1])) {
                    continue;
                }
                HanamiGen.objectAt(w, o, h, seed);
            }
        }

        // 4. The flowers, ferns and tall grass.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!inBiome[((x - x0) << 4) | (z - z0)] || ground.applyAsInt(x, z) < sea) {
                    continue;
                }
                HanamiGen.plantAt(w, x, HanamiGen.topOf(w, x, z, ground), z, seed, false);
            }
        }

        // 5. The Hearts over it all.
        for (int[] s : hearts) {
            if (JjkGen.hypot(Math.max(0, Math.max(x0 - s[0], s[0] - x1)), Math.max(0, Math.max(z0 - s[1], s[1] - z1)))
                    > HanamiGen.HEART_REACH) {
                continue;
            }
            HanamiGen.heartTrim(w, s[0], s[1], ground, s[2], x0 - box, z0 - box, x1 + box, z1 + box);
            Built b = heart(s, worldKey, ground);
            if (b == null) {
                continue;
            }
            int[] v = b.vox();
            for (int i = 0; i < v.length; i += 4) {
                w.force(v[i], v[i + 1], v[i + 2], (byte) v[i + 3]);
            }
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (JjkGen.hypot(x - s[0], z - s[1]) < HanamiGen.MEADOW) {
                        HanamiGen.plantAt(w, x, HanamiGen.topOf(w, x, z, ground), z, seed, true);
                    }
                }
            }
        }

        // Into the world.
        w.each((x, y, z, t) -> {
            if (y < minY || y > maxY || x < x0 || x > x1 || z < z0 || z > z1) {
                return;
            }
            if (groundLike(t) && !inBiome[((x - x0) << 4) | (z - z0)] && !near(x, z, hearts, HanamiGen.HEART_R + 4)) {
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
            level.setBlock(pos, block(t, w.get(x, y - 1, z) == HanamiGen.FERN, w.get(x, y + 1, z) == HanamiGen.FERN), 2);
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
                random.sampler()).is(ModWorldgen.FLORESTA_DE_HANAMI);
    }

    private static long key(int ci, int cj, int worldKey) {
        return ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
    }

    // A cell's Heart spot, well inside the cell: good only on dry land with room for the colossal tree and the biome all
    // around as far as its crown reaches. {cx, cz, heartSeed} or NONE.
    private static int[] site(int ci, int cj, int worldKey, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator ground, int maxY) {
        try {
            return SITES.get(key(ci, cj, worldKey), () -> {
                if (JjkGen.hash3(ci, 91, cj, seed) > 0.45) {
                    return NONE;
                }
                int cx = ci * CELL + 110 + JjkGen.floor(JjkGen.hash3(ci, 92, cj, seed) * (CELL - 220));
                int cz = cj * CELL + 110 + JjkGen.floor(JjkGen.hash3(ci, 93, cj, seed) * (CELL - 220));
                int gy = ground.applyAsInt(cx, cz);
                if (gy < sea + 2 || gy + 90 > maxY || !biomeAt(generator, random, cx, gy, cz)) {
                    return NONE;
                }
                for (int a = 0; a < 8; a++) {
                    int x = cx + JjkGen.round(Math.cos(a * Math.PI / 4) * HanamiGen.HEART_REACH);
                    int z = cz + JjkGen.round(Math.sin(a * Math.PI / 4) * HanamiGen.HEART_REACH);
                    if (!biomeAt(generator, random, x, gy, z)) {
                        return NONE;
                    }
                }
                return new int[] {cx, cz, JjkGen.hashInt(ci, cj, 91, seed)};
            });
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    // The clearing and the colossal tree, in the world's coordinates as the preview builds them (the canopy's trim is
    // done on each chunk's own trees).
    private static Built heart(int[] s, int worldKey, IntBinaryOperator ground) {
        try {
            return HEARTS.get(((long) s[0] << 32) ^ (s[1] & 0xFFFFFFFFL) ^ ((long) worldKey * 0x9E3779B97F4A7C15L), () -> {
                TemploGen.Vox v = new TemploGen.Vox();
                HanamiGen.heartClearing(v, s[0], s[1], ground, s[2]);
                HanamiGen.heartTree(v, s[0], s[1], ground, s[2]);
                return new Built(v.pack());
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static boolean groundLike(int t) {
        return t == HanamiGen.STONE || t == HanamiGen.DIRT || t == HanamiGen.GRASS || t == HanamiGen.MOSS || t == HanamiGen.FERN
                || t == HanamiGen.TALLGRASS || t >= HanamiGen.FLOWER_W && t <= HanamiGen.FLOWER_B;
    }

    private static boolean soft(int t) {
        return t == HanamiGen.LEAF || t == HanamiGen.LEAF2 || t == HanamiGen.LEAF3 || t == HanamiGen.BLOSSOM || t == HanamiGen.VINE
                || t == HanamiGen.FERN || t == HanamiGen.TALLGRASS || t >= HanamiGen.FLOWER_W && t <= HanamiGen.FLOWER_B;
    }

    private static BlockState leaves(BlockState b) {
        return b.setValue(LeavesBlock.PERSISTENT, true);
    }

    // A fern with another over or under it is the two halves of a large fern.
    static BlockState block(int t, boolean fernBelow, boolean fernAbove) {
        return switch (t) {
            case HanamiGen.STONE -> Blocks.STONE.defaultBlockState();
            case HanamiGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case HanamiGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case HanamiGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case HanamiGen.BARK -> JujutsuBlocks.ANCIENT_BARK.get().defaultBlockState();
            case HanamiGen.BARK_MOSS -> JujutsuBlocks.ANCIENT_BARK_MOSS.get().defaultBlockState();
            case HanamiGen.WOOD_IN -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            case HanamiGen.LEAF -> leaves(JujutsuBlocks.HANAMI_LEAVES.get().defaultBlockState());
            case HanamiGen.LEAF2 -> leaves(JujutsuBlocks.HANAMI_LEAVES_LUSH.get().defaultBlockState());
            case HanamiGen.LEAF3 -> leaves(JujutsuBlocks.HANAMI_LEAVES_LIGHT.get().defaultBlockState());
            case HanamiGen.BLOSSOM -> leaves(JujutsuBlocks.BLOOMING_CANOPY.get().defaultBlockState());
            case HanamiGen.FERN -> fernBelow
                    ? Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)
                    : fernAbove ? Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER)
                            : Blocks.FERN.defaultBlockState();
            case HanamiGen.TALLGRASS -> Blocks.SHORT_GRASS.defaultBlockState();
            case HanamiGen.FLOWER_W -> Blocks.OXEYE_DAISY.defaultBlockState();
            case HanamiGen.FLOWER_Y -> Blocks.DANDELION.defaultBlockState();
            case HanamiGen.FLOWER_P -> Blocks.PINK_TULIP.defaultBlockState();
            case HanamiGen.FLOWER_R -> Blocks.POPPY.defaultBlockState();
            case HanamiGen.FLOWER_B -> Blocks.CORNFLOWER.defaultBlockState();
            case HanamiGen.PETAL_W -> JujutsuBlocks.PETAL_WHITE.get().defaultBlockState();
            case HanamiGen.PETAL_P -> JujutsuBlocks.PETAL_PINK.get().defaultBlockState();
            case HanamiGen.PETAL_Y -> JujutsuBlocks.PETAL_YELLOW.get().defaultBlockState();
            case HanamiGen.PETAL_R -> JujutsuBlocks.PETAL_RED.get().defaultBlockState();
            case HanamiGen.STEM -> JujutsuBlocks.GIANT_FLOWER_STEM.get().defaultBlockState();
            case HanamiGen.POLLEN -> JujutsuBlocks.GOLDEN_POLLEN.get().defaultBlockState();
            case HanamiGen.CAP -> Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
            case HanamiGen.CAP_DOT, HanamiGen.STALK -> Blocks.MUSHROOM_STEM.defaultBlockState();
            case HanamiGen.SPIKE -> JujutsuBlocks.WOODEN_STAKE.get().defaultBlockState();
            case HanamiGen.BUD -> JujutsuBlocks.CURSED_BUD.get().defaultBlockState();
            case HanamiGen.BUD_TIP -> JujutsuBlocks.BUD_TIP.get().defaultBlockState();
            case HanamiGen.HOLLOW -> JujutsuBlocks.HANAMI_HOLLOW.get().defaultBlockState();
            case HanamiGen.VINE -> JujutsuBlocks.HANAMI_VINE.get().defaultBlockState();
            case HanamiGen.WATER -> Blocks.WATER.defaultBlockState();
            case HanamiGen.ROCK -> Blocks.ANDESITE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
