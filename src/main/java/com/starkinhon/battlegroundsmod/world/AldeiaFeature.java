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
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Aldeia Amaldiçoada (Stark 2026-11-12, "pântanos"; port released 2026-11-13), done like the Floresta Negra: once
// per chunk of the biome, the preview's swamp and village step by step (AldeiaGen is its line-by-line port, checked
// block for block against the preview in 8 pieces):
// 1. the swamp's ground on the real terrain (mud, soaked grass, water up to the sea, reeds and water lilies), in a box
//    a little wider than the chunk so the cypresses see the water around their feet as the preview's do;
// 2. the cypresses on the grid of 9, in the preview's order, every one whose blocks can reach the chunk (none within
//    72 of a village, as in the preview);
// 3. the villages: one spot per cell of 260 (half of them), on low wet land with the biome all around. Built once and
//    kept, centered on its spot exactly as the preview builds it around (0, 0): the ground rises into the preview's
//    island under it (fading out from 84 to 100 into the swamp around) and the village is laid on it.
// Writes only inside its own chunk.
public final class AldeiaFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0xA1DE1A;
    private static final int CELL = 260;
    private static final int ISLE_FADE = 100;
    private static final int TREE_KEEP = 72;
    private static final int BOX = AldeiaGen.TREE_REACH + 6;
    private static final Cache<Long, Built> VILLAGES = CacheBuilder.newBuilder().maximumSize(16).build();
    private static final Cache<Long, int[]> SITES = CacheBuilder.newBuilder().maximumSize(4096).build();
    private static final int[] NONE = {};

    private record Built(int[] vox, int cx, int cz) {
    }

    public AldeiaFeature(Codec<NoneFeatureConfiguration> codec) {
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
                int h = real.applyAsInt(x, z);
                if (level.getBiome(pos.set(x, h + 1, z)).is(ModWorldgen.ALDEIA_AMALDICOADA)) {
                    inBiome[((x - x0) << 4) | (z - z0)] = true;
                    any = true;
                }
            }
        }
        if (!any) {
            return false;
        }

        // The villages near enough to raise the ground or reach the chunk.
        List<int[]> sites = new ArrayList<>();
        int reach = Math.max(ISLE_FADE, AldeiaGen.VILLAGE_REACH) + BOX;
        for (int ci = Math.floorDiv(x0 - reach, CELL); ci <= Math.floorDiv(x1 + reach, CELL); ci++) {
            for (int cj = Math.floorDiv(z0 - reach, CELL); cj <= Math.floorDiv(z1 + reach, CELL); cj++) {
                int[] s = site(ci, cj, worldKey, seed, sea, generator, random, real);
                if (s != NONE) {
                    sites.add(s);
                }
            }
        }
        IntBinaryOperator ground = (x, z) -> {
            int g = real.applyAsInt(x, z);
            for (int[] s : sites) {
                g = Math.max(g, isle(x, z, s[0], s[1], seed, sea));
            }
            return g;
        };

        // 1. The swamp's ground, in the box around the chunk.
        TemploGen.Vox w = new TemploGen.Vox(x0 - BOX, z0 - BOX, x1 + BOX, z1 + BOX);
        for (int x = x0 - BOX; x <= x1 + BOX; x++) {
            for (int z = z0 - BOX; z <= z1 + BOX; z++) {
                int g = ground.applyAsInt(x, z);
                AldeiaGen.ground0(w, x, z, g, seed, sea);
                // where the island lifted the ground, earth under it down to the real one
                for (int y = real.applyAsInt(x, z) + 1; y <= g - 4; y++) {
                    w.force(x, y, z, AldeiaGen.DIRT);
                }
            }
        }

        // 2. The cypresses.
        int tr = AldeiaGen.TREE_REACH;
        for (int gx = Math.floorDiv(x0 - tr - 9, 9) * 9; gx <= x1 + tr; gx += 9) {
            for (int gz = Math.floorDiv(z0 - tr - 9, 9) * 9; gz <= z1 + tr; gz += 9) {
                int[] s = AldeiaGen.treeSpot(gx, gz, seed);
                if (s == null || near(s[0], s[1], sites, TREE_KEEP)) {
                    continue;
                }
                int g = ground.applyAsInt(s[0], s[1]);
                if (g < sea - 3 || g + 40 > maxY || !biomeAt(generator, random, s[0], g, s[1])) {
                    continue;
                }
                AldeiaGen.tree(w, s[0], g, s[1], seed, sea);
            }
        }

        // 3. The villages over it, moved from the preview's (0, 0) to their spots.
        for (int[] s : sites) {
            if (JjkGen.hypot(Math.max(0, Math.max(x0 - s[0], s[0] - x1)), Math.max(0, Math.max(z0 - s[1], s[1] - z1)))
                    > AldeiaGen.VILLAGE_REACH + 2) {
                continue;
            }
            Built b = village(s, worldKey, seed, sea, real);
            if (b == null) {
                continue;
            }
            int[] v = b.vox();
            for (int i = 0; i < v.length; i += 4) {
                w.force(v[i] + b.cx(), v[i + 1], v[i + 2] + b.cz(), (byte) v[i + 3]);
            }
        }

        // Into the world: only the chunk's own columns of the biome (a village is written whole, its site has the biome
        // all around).
        w.each((x, y, z, t) -> {
            if (y < minY || y > maxY || x < x0 || x > x1 || z < z0 || z > z1) {
                return;
            }
            if (!inBiome[((x - x0) << 4) | (z - z0)] && !near(x, z, sites, AldeiaGen.VILLAGE_REACH)) {
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
            boolean open = there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty();
            if (soft(t) && !open) {
                return; // leaves and plants only grow into the air, as the preview's set
            }
            level.setBlock(pos, block(t), 2);
        });
        return true;
    }

    // The preview's island under a village (centered at cx, cz), fading into the swamp from 84 to 100.
    private static int isle(int x, int z, int cx, int cz, int seed, int sea) {
        double d = JjkGen.hypot(x - cx, z - cz);
        if (d >= ISLE_FADE) {
            return Integer.MIN_VALUE;
        }
        double isle = Math.max(0, 1 - d / 62);
        double fade = Math.max(0, d - AldeiaGen.VILLAGE_REACH) * 3.0 / (ISLE_FADE - AldeiaGen.VILLAGE_REACH);
        return JjkGen.floor(sea - 1 + isle * 5 - fade + (JjkGen.valueNoise(x, z, seed + 9, 12) - 0.5) * 2);
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
                random.sampler()).is(ModWorldgen.ALDEIA_AMALDICOADA);
    }

    private static long key(int ci, int cj, int worldKey) {
        return ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
    }

    // A cell's village spot, well inside the cell: good only on low wet land (the swamp's level) with the biome all
    // around as far as the paddies and the cage shed reach. {cx, cz, villageSeed} or NONE.
    private static int[] site(int ci, int cj, int worldKey, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator real) {
        try {
            return SITES.get(key(ci, cj, worldKey), () -> {
                if (JjkGen.hash3(ci, 71, cj, seed) > 0.5) {
                    return NONE;
                }
                int cx = ci * CELL + 100 + JjkGen.floor(JjkGen.hash3(ci, 72, cj, seed) * (CELL - 200));
                int cz = cj * CELL + 100 + JjkGen.floor(JjkGen.hash3(ci, 73, cj, seed) * (CELL - 200));
                int gy = real.applyAsInt(cx, cz);
                if (gy < sea - 3 || gy > sea + 5 || !biomeAt(generator, random, cx, gy, cz)) {
                    return NONE;
                }
                for (int a = 0; a < 8; a++) {
                    int x = cx + JjkGen.round(Math.cos(a * Math.PI / 4) * AldeiaGen.VILLAGE_REACH);
                    int z = cz + JjkGen.round(Math.sin(a * Math.PI / 4) * AldeiaGen.VILLAGE_REACH);
                    int y = real.applyAsInt(x, z);
                    if (y > sea + 12 || !biomeAt(generator, random, x, y, z)) {
                        return NONE;
                    }
                }
                return new int[] {cx, cz, JjkGen.hashInt(ci, cj, 71, seed)};
            });
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    // The village built as the preview builds it around (0, 0), on its island and the swamp's ground there (in the
    // world's columns, so it joins the swamp around); kept with the spot's offset taken out.
    private static Built village(int[] s, int worldKey, int seed, int sea, IntBinaryOperator real) {
        int cx = s[0], cz = s[1];
        try {
            return VILLAGES.get(((long) cx << 32) ^ (cz & 0xFFFFFFFFL) ^ ((long) worldKey * 0x9E3779B97F4A7C15L), () -> {
                IntBinaryOperator ground = (x, z) -> Math.max(real.applyAsInt(x + cx, z + cz), isle(x + cx, z + cz, cx, cz, seed, sea));
                TemploGen.Vox v = new TemploGen.Vox();
                int r = AldeiaGen.VILLAGE_REACH;
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (JjkGen.hypot(x, z) > r) {
                            continue;
                        }
                        TemploGen.Vox col = new TemploGen.Vox();
                        AldeiaGen.ground0(col, x + cx, z + cz, ground.applyAsInt(x, z), seed, sea);
                        col.each((wx, y, wz, t) -> v.force(wx - cx, y, wz - cz, t));
                    }
                }
                AldeiaGen.village(v, s[2], ground, sea);
                return new Built(v.pack(), cx, cz);
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static boolean soft(int t) {
        return t == AldeiaGen.LEAF || t == AldeiaGen.LEAF2 || t == AldeiaGen.MOSS_HANG || t == AldeiaGen.REED
                || t == AldeiaGen.LILY;
    }

    private static BlockState leaves(BlockState b) {
        return b.setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState block(int t) {
        return switch (t) {
            case AldeiaGen.STONE -> Blocks.STONE.defaultBlockState();
            case AldeiaGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case AldeiaGen.MUD -> Blocks.MUD.defaultBlockState();
            case AldeiaGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case AldeiaGen.GRASS_WET -> JujutsuBlocks.SOAKED_GRASS.get().defaultBlockState();
            case AldeiaGen.WATER -> Blocks.WATER.defaultBlockState();
            case AldeiaGen.PATH -> Blocks.DIRT_PATH.defaultBlockState();
            case AldeiaGen.BOARD -> JujutsuBlocks.SWAMP_BOARDS.get().defaultBlockState();
            case AldeiaGen.POST -> JujutsuBlocks.SWAMP_POST.get().defaultBlockState();
            case AldeiaGen.WOOD -> JujutsuBlocks.FARM_WOOD.get().defaultBlockState();
            case AldeiaGen.WOOD_DARK -> JujutsuBlocks.FARM_WOOD_DARK.get().defaultBlockState();
            case AldeiaGen.PLASTER -> ModBlocks.PLASTER.get().defaultBlockState();
            case AldeiaGen.SHOJI -> ModBlocks.SHOJI.get().defaultBlockState();
            case AldeiaGen.SHOJI_TORN -> JujutsuBlocks.TORN_SHOJI.get().defaultBlockState();
            case AldeiaGen.THATCH -> JujutsuBlocks.THATCH.get().defaultBlockState();
            case AldeiaGen.THATCH_DARK -> JujutsuBlocks.THATCH_ROTTEN.get().defaultBlockState();
            case AldeiaGen.RIDGE -> JujutsuBlocks.ROOF_RIDGE.get().defaultBlockState();
            case AldeiaGen.CHAR -> JujutsuBlocks.CHARRED_WOOD.get().defaultBlockState();
            case AldeiaGen.LANTERN -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            case AldeiaGen.STRAW -> Blocks.HAY_BLOCK.defaultBlockState();
            case AldeiaGen.RICE -> JujutsuBlocks.RICE.get().defaultBlockState();
            case AldeiaGen.REED -> JujutsuBlocks.REED.get().defaultBlockState();
            case AldeiaGen.LILY -> Blocks.LILY_PAD.defaultBlockState();
            case AldeiaGen.BARK -> JujutsuBlocks.CYPRESS_BARK.get().defaultBlockState();
            case AldeiaGen.LEAF -> leaves(JujutsuBlocks.CYPRESS_LEAVES.get().defaultBlockState());
            case AldeiaGen.LEAF2 -> leaves(JujutsuBlocks.CYPRESS_LEAVES_LIGHT.get().defaultBlockState());
            case AldeiaGen.MOSS_HANG -> JujutsuBlocks.OLD_MANS_BEARD.get().defaultBlockState();
            case AldeiaGen.ROOT -> JujutsuBlocks.CYPRESS_KNEE.get().defaultBlockState();
            case AldeiaGen.TORII -> JujutsuBlocks.FADED_TORII.get().defaultBlockState();
            case AldeiaGen.STATUE -> Blocks.ANDESITE.defaultBlockState();
            case AldeiaGen.RED_BIB -> Blocks.RED_WOOL.defaultBlockState();
            case AldeiaGen.BARS -> Blocks.DARK_OAK_FENCE.defaultBlockState();
            case AldeiaGen.TALISMAN -> ModBlocks.SHIDE.get().defaultBlockState();
            case AldeiaGen.DIKE -> JujutsuBlocks.CLAY_DIKE.get().defaultBlockState();
            case AldeiaGen.BAMBOO -> Blocks.BAMBOO_BLOCK.defaultBlockState();
            case AldeiaGen.COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
