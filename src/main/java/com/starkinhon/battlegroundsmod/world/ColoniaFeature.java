package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;

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

// The Colônia do Jogo do Abate (Stark 2026-11-12; port released 2026-11-13; whole-biome city 2026-11-14): the biome is
// one big destroyed Tokyo. Stark on the first version (pieces of 128 to 240 standing in the biome): "parece que foi um
// chunk colocado, um quadrado colocado, aí acaba, vem terra"; he wants the biome to be the city, big where the biome is
// big. So the preview's city (ColoniaGen: its streets, its ruined buildings, craters, cars, barricades, fires, the big
// fight crater with the toppled building) is laid on a world grid of blocks of 34 over the whole biome:
// - a block is city when the biome is under it and 12 blocks around it (16 spots), so the city ends at a street and the
//   game runs this feature in every chunk the city and its skirt touch (it runs only near the biome);
// - one level for the whole city (just above the sea, as Shibuya): the land cut above, filled below;
// - around the city a skirt of 16 blends the land back, its ground a waste of rubble, gravel and coarse dirt;
// - blocks at the city's edge are lower and more broken, so the skyline falls off instead of stopping;
// - each cell of 360 may hold the big fight crater (the preview's colony middle) where the city is 5 blocks wide.
// Blocks and craters are built once and kept, split by chunk. Writes only inside its own chunk.
public final class ColoniaFeature extends Feature<NoneFeatureConfiguration> {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int SALT = 0x6A0800;
    private static final int B = ColoniaGen.BLOCK, ROAD = ColoniaGen.ROAD;
    private static final int CELL = 360;
    private static final int SKIRT = 16;
    private static final int CLEAR = 24; // how high above the old ground the cut goes (trees, hills' tops)
    private static final int CR = 20; // the big crater's radius, as the preview's
    private static final Cache<Long, Boolean> QUARTS = CacheBuilder.newBuilder().maximumSize(65536).build();
    private static final Cache<Long, Boolean> BLOCKS_IN = CacheBuilder.newBuilder().maximumSize(16384).build();
    private static final Cache<Long, Map<Long, int[]>> BLOCKS = CacheBuilder.newBuilder().maximumSize(512).build();
    private static final Cache<Long, int[]> DISTRICTS = CacheBuilder.newBuilder().maximumSize(1024).build();
    private static final Cache<Long, Map<Long, int[]>> CRATERS = CacheBuilder.newBuilder().maximumSize(16).build();
    private static final int[] NONE = {};

    public ColoniaFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    // The city's level: the street's top, just above the sea (Shibuya's too).
    static int cityY(ChunkGenerator generator) {
        return generator.getSeaLevel() + 1;
    }

    // Where the city is, for one world.
    private static final class Mask {
        final ChunkGenerator generator;
        final RandomState random;
        final int worldKey, seed, qy;
        final Map<Long, Boolean> blocks = new HashMap<>();

        Mask(ChunkGenerator generator, RandomState random, int worldKey) {
            this.generator = generator;
            this.random = random;
            this.worldKey = worldKey;
            this.seed = worldKey ^ SALT;
            this.qy = QuartPos.fromBlock(cityY(generator));
        }

        boolean biome(int x, int z) {
            int qx = x >> 2, qz = z >> 2;
            long k = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
            try {
                return QUARTS.get(k, () -> generator.getBiomeSource().getNoiseBiome(qx, qy, qz, random.sampler())
                        .is(ModWorldgen.COLONIA_DO_JOGO_DO_ABATE));
            } catch (ExecutionException e) {
                return false;
            }
        }

        // A block (with the street on its low sides) is city when the biome is at 16 spots from 12 outside it to 12
        // outside its far street.
        boolean block(int bx, int bz) {
            long k = ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
            Boolean known = blocks.get(k);
            if (known != null) {
                return known;
            }
            long gk = k ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
            Boolean in = BLOCKS_IN.getIfPresent(gk);
            if (in == null) {
                in = true;
                int[] off = {-12, 10, 32, B + ROAD + 12};
                for (int a = 0; a < 4 && in; a++) {
                    for (int b = 0; b < 4 && in; b++) {
                        in = biome(bx * B + off[a], bz * B + off[b]);
                    }
                }
                BLOCKS_IN.put(gk, in);
            }
            blocks.put(k, in);
            return in;
        }

        // A column is city when its block is, or it is a street (or crossing) along a block that is.
        boolean at(int x, int z) {
            int bx = Math.floorDiv(x, B), bz = Math.floorDiv(z, B);
            if (block(bx, bz)) {
                return true;
            }
            boolean onX = Math.floorMod(x, B) < ROAD, onZ = Math.floorMod(z, B) < ROAD;
            return onX && block(bx - 1, bz) || onZ && block(bx, bz - 1) || onX && onZ && block(bx - 1, bz - 1);
        }

        // Is the block at the city's edge (a block next to it is not city)?
        boolean edge(int bx, int bz) {
            return !block(bx - 1, bz) || !block(bx + 1, bz) || !block(bx, bz - 1) || !block(bx, bz + 1);
        }

        // The cell's big fight crater, or NONE: {cx, cz, toppled-building seed}, on a crossing with 5 by 5 city blocks.
        int[] district(int ci, int cj) {
            long k = ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
            try {
                return DISTRICTS.get(k, () -> {
                    if (JjkGen.hash3(ci, 111, cj, seed) >= 0.6) {
                        return NONE;
                    }
                    int per = CELL / B;
                    int bx = Math.floorDiv(ci * CELL, B) + 3 + JjkGen.floor(JjkGen.hash3(ci, 112, cj, seed) * (per - 5));
                    int bz = Math.floorDiv(cj * CELL, B) + 3 + JjkGen.floor(JjkGen.hash3(ci, 113, cj, seed) * (per - 5));
                    for (int a = -2; a <= 2; a++) {
                        for (int b = -2; b <= 2; b++) {
                            if (!block(bx + a, bz + b)) {
                                return NONE;
                            }
                        }
                    }
                    int cx = bx * B + ROAD / 2, cz = bz * B + ROAD / 2;
                    LOGGER.info("[jjk] Colônia: cratera da grande luta em {} {}", cx, cz);
                    return new int[] {cx, cz, JjkGen.hashInt(ci, cj, 117, seed)};
                });
            } catch (ExecutionException e) {
                return NONE;
            }
        }

        // The crater near a spot (within reach), or NONE.
        int[] craterNear(int x, int z, int reach) {
            for (int ci = Math.floorDiv(x - reach, CELL); ci <= Math.floorDiv(x + reach, CELL); ci++) {
                for (int cj = Math.floorDiv(z - reach, CELL); cj <= Math.floorDiv(z + reach, CELL); cj++) {
                    int[] d = district(ci, cj);
                    if (d != NONE && Math.abs(d[0] - x) <= reach && Math.abs(d[1] - z) <= reach) {
                        return d;
                    }
                }
            }
            return NONE;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        Mask mask = new Mask(generator, random, worldKey);
        int seed = mask.seed;
        int cityY = cityY(generator);
        int dy = cityY - ColoniaGen.GROUND;
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator ground = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;

        // The city around the chunk, for the skirt's distances.
        int w0x = x0 - SKIRT, w0z = z0 - SKIRT, n = 16 + 2 * SKIRT;
        boolean[] city = new boolean[n * n];
        boolean any = false;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                boolean c = mask.at(w0x + a, w0z + b);
                city[a * n + b] = c;
                any |= c;
            }
        }
        if (!any) {
            return false;
        }

        // 1. The land: cut above the city's level and filled below it, blended around it.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int a = x - w0x, b = z - w0z;
                int h = ground.applyAsInt(x, z);
                if (city[a * n + b]) {
                    clear(level, pos, x, z, cityY + 1, Math.max(Math.max(h, cityY) + CLEAR, surface(level, x, z)), maxY);
                    for (int y = Math.max(minY, h + 1); y <= cityY - 4; y++) {
                        level.setBlock(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                    }
                    continue;
                }
                int d = SKIRT + 1;
                for (int da = -SKIRT; da <= SKIRT; da++) {
                    for (int db = -SKIRT; db <= SKIRT; db++) {
                        int m = Math.max(Math.abs(da), Math.abs(db));
                        if (m < d && city[(a + da) * n + b + db]) {
                            d = m;
                        }
                    }
                }
                if (d > SKIRT) {
                    continue;
                }
                double k = 1 - d / (double) (SKIRT + 1);
                k = k * k * (3 - 2 * k);
                int top = JjkGen.round(h + (cityY - h) * k);
                clear(level, pos, x, z, top + 1, Math.max(Math.max(h, top) + CLEAR, surface(level, x, z)), maxY);
                for (int y = Math.max(minY, h + 1); y < top; y++) {
                    level.setBlock(pos.set(x, y, z), (top - y > 3 ? Blocks.STONE : Blocks.DIRT).defaultBlockState(), 2);
                }
                if (top >= minY && top <= maxY && level.getFluidState(pos.set(x, top + 1, z)).isEmpty()) {
                    level.setBlock(pos.set(x, top, z), waste(x, z, d, seed), 2);
                    // rubble spilled out of the city, thinner further out
                    double r = JjkGen.hash3(x, 21, z, seed);
                    if (r < 0.18 * (1 - d / (double) (SKIRT + 1)) && top + 1 <= maxY) {
                        level.setBlock(pos.set(x, top + 1, z), JujutsuBlocks.RUBBLE.get().defaultBlockState(), 2);
                    }
                }
            }
        }

        // 2. The streets of this chunk, then the blocks and the craters reaching it.
        TemploGen.Vox w = new TemploGen.Vox(x0, z0, x1, z1);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (city[(x - w0x) * n + z - w0z]) {
                    ColoniaGen.street(w, x, z, seed);
                }
            }
        }
        long ck = ChunkPos.asLong(chunk.x, chunk.z);
        for (int bx = Math.floorDiv(x0 - 8, B); bx <= Math.floorDiv(x1 + 8, B); bx++) {
            for (int bz = Math.floorDiv(z0 - 8, B); bz <= Math.floorDiv(z1 + 8, B); bz++) {
                if (!mask.block(bx, bz)) {
                    continue;
                }
                Map<Long, int[]> built = block(mask, bx, bz);
                int[] v = built == null ? null : built.get(ck);
                if (v != null) {
                    for (int i = 0; i < v.length; i += 4) {
                        w.force(v[i], v[i + 1], v[i + 2], (byte) v[i + 3]);
                    }
                }
            }
        }
        int[] dist = mask.craterNear((x0 + x1) / 2, (z0 + z1) / 2, CR + 40);
        if (dist != NONE) {
            Map<Long, int[]> built = crater(dist);
            int[] v = built == null ? null : built.get(ck);
            if (v != null) {
                for (int i = 0; i < v.length; i += 4) {
                    w.force(v[i], v[i + 1], v[i + 2], (byte) v[i + 3]);
                }
            }
        }

        // Into the world.
        w.each((x, y0, z, t) -> {
            int y = y0 + dy;
            if (y < minY || y > maxY) {
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
            if (t == ColoniaGen.FIRE) {
                // A column of fire: embers where it starts and a real fire over them (it never burns out on embers).
                if (w.get(x, y0 - 1, z) == ColoniaGen.FIRE) {
                    return;
                }
                level.setBlock(pos, JujutsuBlocks.EMBERS.get().defaultBlockState(), 2);
                if (y + 1 <= maxY && (w.get(x, y0 + 1, z) == 0 || w.get(x, y0 + 1, z) == ColoniaGen.FIRE)) {
                    level.setBlock(pos.set(x, y + 1, z), Blocks.FIRE.defaultBlockState(), 2);
                }
                return;
            }
            boolean open = there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty();
            if (t == ColoniaGen.WEED && !open) {
                return; // the weeds only grow into the air, as the preview's set
            }
            level.setBlock(pos, block(t), 2);
        });
        return true;
    }

    // The skirt's ground: a waste of rubble, gravel and coarse dirt near the city, grass coming back further out.
    private static BlockState waste(int x, int z, int d, int seed) {
        double r = JjkGen.hash3(x, 20, z, seed) + (JjkGen.valueNoise(x, z, seed + 3, 9) - 0.5) * 0.5;
        double far = d / (double) (SKIRT + 1);
        if (r < 0.25 - 0.15 * far) {
            return JujutsuBlocks.RUBBLE.get().defaultBlockState();
        }
        if (r < 0.5 - 0.2 * far) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (r < 0.85 - 0.35 * far) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    // A city block: one or two ruined buildings on its lot (none near a big crater), and in its streets a crater, a car,
    // a barricade or a fire now and then. At the city's edge the buildings are lower and more broken. Split by chunk.
    private static Map<Long, int[]> block(Mask mask, int bx, int bz) {
        int seed = mask.seed;
        long k = ((long) bx << 32) ^ (bz & 0xFFFFFFFFL) ^ ((long) mask.worldKey * 0x9E3779B97F4A7C15L);
        boolean edge = mask.edge(bx, bz);
        int[] dist = mask.craterNear(bx * B + B / 2, bz * B + B / 2, CR + 14 + B);
        try {
            return BLOCKS.get(k, () -> {
                TemploGen.Vox w = new TemploGen.Vox();
                int lx = bx * B + ROAD + 2, lz = bz * B + ROAD + 2, L = B - ROAD - 4;
                boolean nearCrater = dist != NONE && JjkGen.hypot(lx + L / 2.0 - dist[0], lz + L / 2.0 - dist[1]) < CR + 14;
                Rng r = new Rng(JjkGen.hashInt(bx, 0, bz, seed + 10));
                List<int[]> blds = new ArrayList<>();
                if (!nearCrater) {
                    boolean split = r.next() < 0.45;
                    int[][] parts = split ? new int[][] {{lx, lz, L / 2 - 1, L}, {lx + L / 2 + 1, lz, L - L / 2 - 1, L}}
                            : new int[][] {{lx + 1, lz + 1, L - 2, L - 2}};
                    for (int[] p : parts) {
                        boolean tall = r.next() < (edge ? 0.08 : 0.3);
                        int floors = tall ? r.nextInt(9, 16) : r.nextInt(3, 8);
                        boolean stump = r.next() < (edge ? 0.45 : 0.18);
                        int fl = stump ? r.nextInt(1, 3) : edge ? Math.min(floors, 5) : floors;
                        int cuts = stump ? 1 : r.nextInt(1, 3);
                        ColoniaGen.building(w, p[0], p[1], p[2], p[3], fl, JjkGen.hashInt(p[0], 1, p[1], seed + 11), cuts);
                        blds.add(p);
                    }
                }
                // things in the block's own streets (its low sides)
                for (int t = 0; t < 2; t++) {
                    double q = JjkGen.hash3(bx, 30 + t, bz, seed);
                    if (q > 0.75) {
                        continue;
                    }
                    boolean alongX = JjkGen.hash3(bx, 40 + t, bz, seed) < 0.5;
                    int along = 6 + JjkGen.floor(JjkGen.hash3(bx, 50 + t, bz, seed) * (B - 12));
                    int across = 1 + JjkGen.floor(JjkGen.hash3(bx, 60 + t, bz, seed) * (ROAD - 4));
                    int x = alongX ? bx * B + along : bx * B + across, z = alongX ? bz * B + across : bz * B + along;
                    double c = JjkGen.hash3(bx, 70 + t, bz, seed);
                    if (c < 0.3) {
                        ColoniaGen.crater(w, x, z, 3 + JjkGen.floor(JjkGen.hash3(bx, 80 + t, bz, seed) * 3), JjkGen.hashInt(x, 0, z, seed + 12));
                    } else if (c < 0.7) {
                        ColoniaGen.car(w, x, z, alongX ? 'x' : 'z', JjkGen.hashInt(x, 1, z, seed + 13));
                    } else if (c < 0.82) {
                        ColoniaGen.barricade(w, x, z, alongX ? 'z' : 'x', JjkGen.hashInt(x, 2, z, seed + 14));
                    } else {
                        ColoniaGen.fire(w, x, ColoniaGen.GROUND + 1, z, seed + 15);
                    }
                }
                for (int[] b : blds) {
                    if (JjkGen.hash3(b[0], 3, b[1], seed) < 0.3) {
                        ColoniaGen.fire(w, b[0] - 2, ColoniaGen.GROUND + 1, b[1] + b[3] / 2, seed + 16);
                    }
                }
                return byChunk(w.pack());
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    // The big fight's crater with the toppled building across it, as the preview's colony middle. Split by chunk.
    private static Map<Long, int[]> crater(int[] d) {
        long k = ((long) d[0] << 32) ^ (d[1] & 0xFFFFFFFFL) ^ d[2];
        try {
            return CRATERS.get(k, () -> {
                int seed = d[2];
                Rng rng = new Rng(seed);
                int ox = d[0], oz = d[1];
                // the preview's crater is drawn around (0, 0): draw it there and move it
                TemploGen.Vox c = new TemploGen.Vox();
                ColoniaGen.crater(c, 0, 0, CR, seed + 17);
                for (int x = -CR - 4; x <= CR + 4; x++) {
                    for (int z = -CR - 4; z <= CR + 4; z++) {
                        double dd = JjkGen.hypot(x, z);
                        if (dd < CR + 4 && dd > CR - 1 && JjkGen.hash3(x, 4, z, seed) < 0.35) {
                            for (int kk = 1; kk <= (JjkGen.hash3(x, 5, z, seed) < 0.25 ? 2 : 1); kk++) {
                                c.force(x, ColoniaGen.GROUND + kk, z, ColoniaGen.RUBBLE);
                            }
                        }
                    }
                }
                double a = rng.range(0, Math.PI), ca = JjkGen.cos(a), sa = JjkGen.sin(a);
                final int Lb = 46;
                final double Wb = 9;
                for (int s = -Lb / 2; s <= Lb / 2; s++) {
                    for (double t = -Wb / 2; t <= Wb / 2; t++) {
                        int x = JjkGen.round(ca * s - sa * t), z = JjkGen.round(sa * s + ca * t);
                        int base = ColoniaGen.GROUND - 6 + JjkGen.round((s + Lb / 2.0) / Lb * 10);
                        for (int kk = 0; kk < 9; kk++) {
                            boolean edgeB = Math.abs(t) >= Wb / 2 - 0.5 || kk == 0 || kk == 8;
                            int sl = JjkGen.round(s + Lb / 2.0);
                            boolean fy = sl % 4 == 0;
                            c.force(x, base + kk, z, edgeB ? (fy || kk == 0 || kk == 8 ? ColoniaGen.CONCRETE_DARK
                                    : (kk % 3 != 0 && sl % 4 != 1) ? (JjkGen.hash3(x, base + kk, z, seed) < 0.12 ? ColoniaGen.AIR
                                            : ColoniaGen.WINDOW) : ColoniaGen.CONCRETE_DARK)
                                    : fy ? ColoniaGen.FLOOR : ColoniaGen.AIR);
                        }
                    }
                }
                for (int i = 0; i < 7; i++) {
                    int fx = rng.nextInt(-CR + 4, CR - 4), fz = rng.nextInt(-CR + 4, CR - 4);
                    int fy = ColoniaGen.GROUND + 12;
                    while (fy > ColoniaGen.GROUND - 14 && c.get(fx, fy - 1, fz) == 0) {
                        fy--;
                    }
                    ColoniaGen.fire(c, fx, fy, fz, seed + 18 + i);
                }
                int[] p = c.pack();
                for (int i = 0; i < p.length; i += 4) {
                    p[i] += ox;
                    p[i + 2] += oz;
                }
                return byChunk(p);
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static Map<Long, int[]> byChunk(int[] p) {
        Map<Long, int[]> count = new HashMap<>();
        for (int i = 0; i < p.length; i += 4) {
            count.computeIfAbsent(ChunkPos.asLong(p[i] >> 4, p[i + 2] >> 4), q -> new int[1])[0]++;
        }
        Map<Long, int[]> by = new HashMap<>();
        Map<Long, int[]> at = new HashMap<>();
        for (Map.Entry<Long, int[]> e : count.entrySet()) {
            by.put(e.getKey(), new int[e.getValue()[0] * 4]);
            at.put(e.getKey(), new int[1]);
        }
        for (int i = 0; i < p.length; i += 4) {
            long c = ChunkPos.asLong(p[i] >> 4, p[i + 2] >> 4);
            int[] j = at.get(c);
            System.arraycopy(p, i, by.get(c), j[0], 4);
            j[0] += 4;
        }
        return by;
    }

    // The real top of a column (overhangs, arches and floating bits above the noise's ground included), so the cut takes
    // everything over the city and nothing is left hanging in the air.
    private static int surface(WorldGenLevel level, int x, int z) {
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    // Cuts the land away from y0 to y1 (whatever is there: hills, trees, water).
    private static void clear(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y0, int y1, int maxY) {
        for (int y = y0; y <= Math.min(y1, maxY); y++) {
            BlockState b = level.getBlockState(pos.set(x, y, z));
            if (!b.isAir() && !b.is(Blocks.BEDROCK)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // The heightmap can miss what a neighbour chunk's feature put here (a Dragon World pillar left floating over
        // the city, 2026-11-13), so the cut goes on up while it still finds something, until 8 blocks of air in a row.
        int air = 0;
        for (int y = Math.max(y0, y1 + 1); y <= maxY && air < 8; y++) {
            BlockState b = level.getBlockState(pos.set(x, y, z));
            if (b.isAir()) {
                air++;
            } else if (!b.is(Blocks.BEDROCK)) {
                air = 0;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    // Whether a column is on the city or its skirt, give or take a margin. Other features ask this so they never put
    // anything on the city (a Dragon World pillar there was cut by the city and left floating, Stark 2026-11-13).
    public static boolean covers(WorldGenLevel level, ChunkGenerator generator, int x, int z, int margin) {
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        Mask mask = new Mask(generator, random, (int) (worldSeed ^ (worldSeed >>> 32)));
        int reach = SKIRT + margin;
        for (int dx = -reach; dx <= reach; dx += 4) {
            for (int dz = -reach; dz <= reach; dz += 4) {
                if (mask.at(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    // The big fight craters near a spot (for the client's dome and the ambience): {cx, cz, cityY} of each crater whose
    // cell is within one of the spot's cell. Only cells already looked at by the generator are known.
    public static List<int[]> knownColonies(int x, int z, long worldSeed) {
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        List<int[]> out = new ArrayList<>();
        int ci0 = Math.floorDiv(x, CELL), cj0 = Math.floorDiv(z, CELL);
        for (int ci = ci0 - 1; ci <= ci0 + 1; ci++) {
            for (int cj = cj0 - 1; cj <= cj0 + 1; cj++) {
                long k = ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
                int[] d = DISTRICTS.getIfPresent(k);
                if (d != null && d != NONE) {
                    out.add(new int[] {d[0], d[1], 0});
                }
            }
        }
        return out;
    }

    static BlockState block(int t) {
        return switch (t) {
            case ColoniaGen.STONE -> Blocks.STONE.defaultBlockState();
            case ColoniaGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case ColoniaGen.ASPHALT -> JujutsuBlocks.ASPHALT.get().defaultBlockState();
            case ColoniaGen.ASPHALT_CRACK -> JujutsuBlocks.ASPHALT_CRACKED.get().defaultBlockState();
            case ColoniaGen.LINE -> JujutsuBlocks.ROAD_LINE.get().defaultBlockState();
            case ColoniaGen.SIDEWALK -> JujutsuBlocks.SIDEWALK.get().defaultBlockState();
            case ColoniaGen.CONCRETE -> JujutsuBlocks.COLONY_CONCRETE.get().defaultBlockState();
            case ColoniaGen.CONCRETE_DARK -> JujutsuBlocks.COLONY_CONCRETE_DARK.get().defaultBlockState();
            case ColoniaGen.CONCRETE_LIGHT -> JujutsuBlocks.COLONY_CONCRETE_LIGHT.get().defaultBlockState();
            case ColoniaGen.WINDOW -> JujutsuBlocks.COLONY_WINDOW.get().defaultBlockState();
            case ColoniaGen.WINDOW_LIT -> JujutsuBlocks.COLONY_WINDOW_LIT.get().defaultBlockState();
            case ColoniaGen.FLOOR -> JujutsuBlocks.FLOOR_SLAB.get().defaultBlockState();
            case ColoniaGen.REBAR -> JujutsuBlocks.REBAR.get().defaultBlockState();
            case ColoniaGen.RUBBLE -> JujutsuBlocks.RUBBLE.get().defaultBlockState();
            case ColoniaGen.SCORCH -> JujutsuBlocks.JOGO_BASALT_DARK.get().defaultBlockState();
            case ColoniaGen.FIRE -> JujutsuBlocks.EMBERS.get().defaultBlockState();
            case ColoniaGen.CAR_RED -> Blocks.RED_CONCRETE.defaultBlockState();
            case ColoniaGen.CAR_WHITE -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case ColoniaGen.CAR_BLUE -> Blocks.BLUE_CONCRETE.defaultBlockState();
            case ColoniaGen.CAR_BLACK -> Blocks.BLACK_CONCRETE.defaultBlockState();
            case ColoniaGen.TIRE -> JujutsuBlocks.TIRE.get().defaultBlockState();
            case ColoniaGen.BARRIER_CONE -> JujutsuBlocks.TRAFFIC_CONE.get().defaultBlockState();
            case ColoniaGen.FENCE -> JujutsuBlocks.WORK_FENCE.get().defaultBlockState();
            case ColoniaGen.SIGN -> JujutsuBlocks.WARNING_SIGN.get().defaultBlockState();
            case ColoniaGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case ColoniaGen.WEED -> JujutsuBlocks.STREET_WEEDS.get().defaultBlockState();
            case ColoniaGen.GLASS_BITS -> Blocks.GLASS_PANE.defaultBlockState();
            case ColoniaGen.TILE -> JujutsuBlocks.LOT_TILES.get().defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
