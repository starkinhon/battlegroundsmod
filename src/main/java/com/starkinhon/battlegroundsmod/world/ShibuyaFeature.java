package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

// Shibuya (Stark 2026-11-12; port released 2026-11-13): the city covers the biome on the preview's world grid
// (ShibuyaGen is its line-by-line port, checked block for block against the preview in 4 pieces), at one level for the
// whole city (just above the sea), the land cut above it and filled below it; at the biome's edge a skirt of 8 blends
// the land back. Per chunk, in the preview's order, on a box of the chunk and 26 around it (so the wires between poles
// and the trees across the chunk's edge come out the same from both sides):
// 1. the ground of the streets and lots (only where the city is: the biome all around for 8);
// 2. the metro stations: one under each big scramble crossing (60% of them, and always at the grid's middle) with the
//    city all along it, built once and kept (concourse, platform, train, tunnels of 110 each way);
// 3. the blocks: alleys, buildings, skyscrapers, shrines and parking lots, each block built once and kept, only when
//    the city covers it whole;
// 4. the street furniture (poles, wires, lamps, avenue trees, guard rails, traffic lights).
// Writes only inside its own chunk.
public final class ShibuyaFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0x6A0900;
    private static final int M = 26;
    private static final int SKIRT = 8;
    private static final int CLEAR = 24;
    private static final int STATION_HALF = 110;
    // Built blocks and stations, and apart from them the keys found empty (cheap to keep, many): a chunk asks for ~16 blocks
    // and ~28 stations, from several generator threads at once, so a small cache kept throwing them away and building
    // the same metro stations again and again (the Souls world test of 2026-11-13 fell many seconds behind).
    private static final Cache<Long, int[]> PLANS = CacheBuilder.newBuilder().maximumSize(256).build();
    private static final Cache<Long, int[]> STATIONS = CacheBuilder.newBuilder().maximumSize(48).build();
    private static final Cache<Long, Boolean> EMPTY = CacheBuilder.newBuilder().maximumSize(16384).build();
    private static final Cache<Long, Boolean> QUARTS = CacheBuilder.newBuilder().maximumSize(65536).build();
    private static final Cache<Long, int[]> FOOTPRINTS = CacheBuilder.newBuilder().maximumSize(1024).build();
    private static final int[] NONE = {};
    // The feature's own types for the overgrown blocks at the city's edge (ShibuyaGen's types end at 111).
    private static final int WILD_GRASS = 200, WILD_TALL = 201, WILD_FLOWER = 202, WILD_BUSH = 203;

    public ShibuyaFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    // The city's level: the street's top, just above the sea.
    public static int cityY(ChunkGenerator generator) {
        return generator.getSeaLevel() + 1;
    }

    // The generator's seed for a world (the same the streets were drawn with): the pedestrians walk on these streets.
    public static int seed(long worldSeed) {
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        return worldKey ^ SALT;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        int seed = worldKey ^ SALT;
        int cityY = cityY(generator);
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator ground = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;
        City city = new City(generator, random, worldKey, cityY);

        int bx0 = x0 - M, bz0 = z0 - M, bx1 = x1 + M, bz1 = z1 + M;
        boolean any = false;
        for (int x = x0; x <= x1 && !any; x += 4) {
            for (int z = z0; z <= z1 && !any; z += 4) {
                any = city.biome(x, z);
            }
        }
        if (!any) {
            return false;
        }

        // The blocks reaching the box: a block at the biome's edge is built whole when the biome is under its middle,
        // its corners and the middles of its sides, and the city then takes its whole footprint (Stark 2026-11-14: the
        // city must fill the biome, not stop at a line of empty lots).
        int li = Math.floorDiv(bz0 - 20, ShibuyaGen.CELL) - 1, hi = Math.floorDiv(bz1 + 20, ShibuyaGen.CELL) + 1;
        int lj = Math.floorDiv(bx0 - 20, ShibuyaGen.CELL) - 1, hj = Math.floorDiv(bx1 + 20, ShibuyaGen.CELL) + 1;
        int[][] plans = new int[(hi - li + 1) * (hj - lj + 1)][];
        for (int i = li; i <= hi; i++) {
            for (int j = lj; j <= hj; j++) {
                int[] p = plan(i, j, seed, worldKey, city);
                plans[(i - li) * (hj - lj + 1) + j - lj] = p;
                if (p != NONE) {
                    int[] f = footprint(i, j, seed, worldKey);
                    city.extra.add(new int[] {f[0] - 4, f[1] + 4, f[2] - 4, f[3] + 4});
                }
            }
        }

        // 0. The land under the city and the skirt around it (this chunk only).
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int h = ground.applyAsInt(x, z);
                if (city.at(x, z)) {
                    clear(level, pos, x, z, cityY + 1, Math.max(Math.max(h, cityY) + CLEAR, surface(level, x, z)), maxY);
                    for (int y = Math.max(minY, h + 1); y <= cityY - 3; y++) {
                        level.setBlock(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                    }
                    continue;
                }
                if (!city.biome(x, z)) {
                    continue;
                }
                int d = city.distance(x, z, SKIRT);
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
                if (top != h && top >= minY && top <= maxY) {
                    level.setBlock(pos.set(x, top, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
        }

        ShibuyaGen.Vox w = new ShibuyaGen.Vox(bx0, bz0, bx1, bz1);

        // 1. The ground.
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                if (!city.at(x, z)) {
                    continue;
                }
                int g = ShibuyaGen.groundAt(x, z, seed);
                w.force(x, 0, z, g < 0 ? ShibuyaGen.LOT : g);
                w.force(x, -1, z, ShibuyaGen.SOIL);
                w.force(x, -2, z, ShibuyaGen.STONE);
            }
        }

        // 2. The metro stations reaching the box.
        int sj0 = Math.floorDiv(bx0 - STATION_HALF, ShibuyaGen.CELL) - 1, sj1 = Math.floorDiv(bx1 + STATION_HALF, ShibuyaGen.CELL) + 1;
        for (int i = li; i <= hi; i++) {
            for (int j = sj0; j <= sj1; j++) {
                int[] st = station(i, j, seed, worldKey, city);
                if (st != NONE) {
                    apply(w, st);
                }
            }
        }

        // 3. The blocks.
        for (int i = li; i <= hi; i++) {
            for (int j = lj; j <= hj; j++) {
                int[] p = plans[(i - li) * (hj - lj + 1) + j - lj];
                if (p != NONE) {
                    apply(w, p);
                } else {
                    overgrown(w, i, j, seed, worldKey, city);
                }
            }
        }

        // 4. The street furniture.
        ShibuyaGen.furniture(w, seed, li, hi, lj, hj, bx0, bx1, bz0, bz1, Integer.MAX_VALUE);

        // Into the world (this chunk only).
        w.each((x, y, z, t) -> {
            if (x < x0 || x > x1 || z < z0 || z > z1) {
                return;
            }
            int wy = y + cityY;
            if (wy < minY || wy > maxY) {
                return;
            }
            pos.set(x, wy, z);
            BlockState there = level.getBlockState(pos);
            if (there.is(Blocks.BEDROCK)) {
                return;
            }
            if (t == ShibuyaGen.CARVE) {
                if (!there.isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                return;
            }
            int b = ShibuyaGen.base(t);
            if ((b == ShibuyaGen.LEAVES || b == ShibuyaGen.LEAVES2) && !(there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty())) {
                return;
            }
            level.setBlock(pos, block(t), 2);
        });
        return true;
    }

    // A block the city doesn't cover whole (at the biome's edge) is not built: instead of a bare lot it is an overgrown
    // empty block, grass with tall grass, flowers and bushes, so the city's edge reads as a quiet outskirt, not a car park.
    private static void overgrown(ShibuyaGen.Vox w, int i, int j, int seed, int worldKey, City city) {
        int[] f = footprint(i, j, seed, worldKey);
        int x0 = Math.max(f[0], w.lx), x1 = Math.min(f[1], w.hx), z0 = Math.max(f[2], w.lz), z1 = Math.min(f[3], w.hz);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (w.get(x, 0, z) != ShibuyaGen.LOT || !city.at(x, z)) {
                    continue;
                }
                w.force(x, 0, z, WILD_GRASS);
                double r = JjkGen.hash3(x, 7, z, seed);
                if (r < 0.35) {
                    w.force(x, 1, z, WILD_TALL);
                } else if (r < 0.38) {
                    w.force(x, 1, z, WILD_FLOWER);
                } else if (r < 0.395) {
                    w.force(x, 1, z, WILD_BUSH);
                }
            }
        }
    }

    // A block's ground footprint {x0, x1, z0, z1}, kept.
    private static int[] footprint(int i, int j, int seed, int worldKey) {
        long k = ((long) i << 36) ^ ((long) (j & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L) ^ 0xF0;
        try {
            return FOOTPRINTS.get(k, () -> {
                ShibuyaGen.Plan plan = ShibuyaGen.blockPlan(i, j, seed);
                return new int[] {plan.x0, plan.x1, plan.z0, plan.z1};
            });
        } catch (ExecutionException e) {
            return new int[] {0, -1, 0, -1};
        }
    }

    private static void apply(ShibuyaGen.Vox w, int[] v) {
        for (int i = 0; i < v.length; i += 4) {
            w.force(v[i], v[i + 1], v[i + 2], v[i + 3] == ShibuyaGen.CARVE ? ShibuyaGen.AIR : v[i + 3]);
        }
    }

    // The real top of a column (overhangs, arches and floating bits above the noise's ground included), so the cut takes
    // everything over the city and nothing is left hanging in the air.
    private static int surface(WorldGenLevel level, int x, int z) {
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

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

    // Where the city is: a column is city when the biome covers its quart and every quart within 2 (8 blocks).
    private static final class City {
        final ChunkGenerator generator;
        final RandomState random;
        final int worldKey, qy;

        City(ChunkGenerator generator, RandomState random, int worldKey, int cityY) {
            this.generator = generator;
            this.random = random;
            this.worldKey = worldKey;
            this.qy = QuartPos.fromBlock(cityY);
        }

        boolean quart(int qx, int qz) {
            long k = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
            try {
                return QUARTS.get(k, () -> generator.getBiomeSource().getNoiseBiome(qx, qy, qz, random.sampler())
                        .is(ModWorldgen.SHIBUYA));
            } catch (ExecutionException e) {
                return false;
            }
        }

        boolean biome(int x, int z) {
            return quart(x >> 2, z >> 2);
        }

        // The answer is the same for the 16 columns of a quart: kept per quart for this chunk's work (a chunk asks it
        // for every column of its box and of the skirt around, tens of thousands of times).
        private final java.util.HashMap<Long, Boolean> cityQuarts = new java.util.HashMap<>();

        // The footprints of the blocks built at the biome's edge (this chunk's work), city too.
        final List<int[]> extra = new ArrayList<>();

        boolean at(int x, int z) {
            if (core(x, z)) {
                return true;
            }
            for (int[] r : extra) {
                if (x >= r[0] && x <= r[1] && z >= r[2] && z <= r[3]) {
                    return true;
                }
            }
            return false;
        }

        boolean core(int x, int z) {
            int qx = x >> 2, qz = z >> 2;
            long k = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL);
            Boolean known = cityQuarts.get(k);
            if (known != null) {
                return known;
            }
            boolean all = true;
            for (int a = -2; a <= 2 && all; a++) {
                for (int b = -2; b <= 2 && all; b++) {
                    all = quart(qx + a, qz + b);
                }
            }
            cityQuarts.put(k, all);
            return all;
        }

        // The distance (in blocks, square) to the nearest city column, up to max (more is max + 1).
        int distance(int x, int z, int max) {
            for (int d = 1; d <= max; d++) {
                for (int a = -d; a <= d; a++) {
                    if (at(x + a, z - d) || at(x + a, z + d) || at(x - d, z + a) || at(x + d, z + a)) {
                        return d;
                    }
                }
            }
            return max + 1;
        }
    }

    // A metro station under the scramble crossing of 'x' i and 'z' j: 60% of them (always the grid's middle), only with
    // the city all along it. Built once, packed (local y), or NONE.
    private static int[] station(int i, int j, int seed, int worldKey, City city) {
        if (!ShibuyaGen.scramble(i, j, seed) || !(i == 0 && j == 0) && JjkGen.hash3(i, j, 61, seed) >= 0.6) {
            return NONE;
        }
        long k = ((long) i << 36) ^ ((long) (j & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L) ^ 0x5A;
        if (EMPTY.getIfPresent(k) != null) {
            return NONE;
        }
        try {
            int[] out = STATIONS.get(k, () -> {
                int c = ShibuyaGen.street('x', i, seed).c(), ox = ShibuyaGen.street('z', j, seed).c();
                for (int a = -STATION_HALF; a <= STATION_HALF; a += 22) {
                    if (!city.core(ox + a, c - 12) || !city.core(ox + a, c + 12)) {
                        return NONE;
                    }
                }
                ShibuyaGen.Vox v = new ShibuyaGen.Vox();
                ShibuyaGen.metro(v, seed, STATION_HALF, i, ox);
                return v.pack();
            });
            if (out == NONE) {
                EMPTY.put(k, Boolean.TRUE);
                STATIONS.invalidate(k);
            }
            return out;
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    // The holes the station entrances open in the sidewalk (y = 0) near a block, as column keys.
    private static Set<Long> holes(int i, int j, int seed, int worldKey, City city) {
        Set<Long> out = new HashSet<>();
        for (int si : new int[] {i, i + 1}) {
            for (int sj = j - 3; sj <= j + 4; sj++) {
                if (station(si, sj, seed, worldKey, city) == NONE) {
                    continue;
                }
                int c = ShibuyaGen.street('x', si, seed).c(), ox = ShibuyaGen.street('z', sj, seed).c();
                int[][] en = ShibuyaGen.entrances(c);
                for (int q = 0; q < 2; q++) {
                    int sgn = q == 0 ? 1 : -1;
                    for (int kk = 0; kk < 8; kk++) {
                        for (int z : en[q]) {
                            out.add(((long) (ox + sgn * (29 - kk)) << 32) ^ (z & 0xFFFFFFFFL));
                        }
                    }
                }
            }
        }
        return out;
    }

    // A block, built once (packed, local y): its alleys and everything on its lots, only when the city covers it
    // whole. The street's ground around it is there while building (the drink machines look at it) but is not kept.
    private static int[] plan(int i, int j, int seed, int worldKey, City city) {
        long k = ((long) i << 36) ^ ((long) (j & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
        if (EMPTY.getIfPresent(k) != null) {
            return NONE;
        }
        try {
            int[] built = PLANS.get(k, () -> {
                ShibuyaGen.Plan plan = ShibuyaGen.blockPlan(i, j, seed);
                int cx = (plan.x0 + plan.x1) >> 1, cz = (plan.z0 + plan.z1) >> 1;
                if (!city.core(cx, cz)) {
                    return NONE;
                }
                int[] xs = {plan.x0 - 4, cx, plan.x1 + 4}, zs = {plan.z0 - 4, cz, plan.z1 + 4};
                for (int px : xs) {
                    for (int pz : zs) {
                        if (!city.biome(px, pz)) {
                            return NONE;
                        }
                    }
                }
                Set<Long> holes = holes(i, j, seed, worldKey, city);
                ShibuyaGen.Vox v = new ShibuyaGen.Vox();
                Map<Long, Integer> pre = new HashMap<>();
                for (int x = plan.x0 - 4; x <= plan.x1 + 4; x++) {
                    for (int z = plan.z0 - 4; z <= plan.z1 + 4; z++) {
                        if (holes.contains(((long) x << 32) ^ (z & 0xFFFFFFFFL))) {
                            continue;
                        }
                        int g = ShibuyaGen.groundAt(x, z, seed);
                        g = g < 0 ? ShibuyaGen.LOT : g;
                        v.force(x, 0, z, g);
                        pre.put(LeafTreeGen.Keys.key(x, 0, z), g);
                    }
                }
                for (int[] a : plan.alleys) {
                    for (int x = a[0]; x <= a[1]; x++) {
                        for (int z = a[2]; z <= a[3]; z++) {
                            v.force(x, 0, z, ShibuyaGen.ALLEY);
                        }
                    }
                }
                if (plan.towerAv != null) {
                    ShibuyaGen.skyscraper(v, plan, seed);
                }
                for (ShibuyaGen.Lot lot : plan.lots) {
                    if (lot.kind.equals("shrine")) {
                        ShibuyaGen.shrine(v, lot, seed);
                    } else if (lot.kind.equals("parking")) {
                        ShibuyaGen.parking(v, lot);
                    } else {
                        ShibuyaGen.building(v, lot, seed);
                    }
                }
                int[] all = v.pack();
                List<int[]> kept = new ArrayList<>();
                for (int q = 0; q < all.length; q += 4) {
                    Integer g = all[q + 1] == 0 ? pre.get(LeafTreeGen.Keys.key(all[q], 0, all[q + 2])) : null;
                    if (g != null && g == all[q + 3]) {
                        continue;
                    }
                    kept.add(new int[] {all[q], all[q + 1], all[q + 2], all[q + 3]});
                }
                int[] out = new int[kept.size() * 4];
                for (int q = 0; q < kept.size(); q++) {
                    System.arraycopy(kept.get(q), 0, out, q * 4, 4);
                }
                return out;
            });
            if (built == NONE) {
                EMPTY.put(k, Boolean.TRUE);
                PLANS.invalidate(k);
            }
            return built;
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    private static BlockState leaves(BlockState b) {
        return b.setValue(LeavesBlock.PERSISTENT, true);
    }

    public static BlockState block(int t0) {
        if ((t0 & ShibuyaGen.LIT) != 0) {
            return ((t0 & ShibuyaGen.COOL) != 0 ? JujutsuBlocks.WINDOW_COOL : JujutsuBlocks.WINDOW_WARM).get().defaultBlockState();
        }
        int t = ShibuyaGen.base(t0);
        if (t >= ShibuyaGen.ARM && t < ShibuyaGen.ARM + 16) {
            int a = t - ShibuyaGen.ARM;
            net.minecraft.core.Direction f = net.minecraft.core.Direction.from2DDataValue((a & 3) + 2 & 3);
            return JujutsuBlocks.POWER_LINE.get().defaultBlockState()
                    .setValue(com.starkinhon.battlegroundsmod.PowerLineBlock.FACING, f)
                    .setValue(com.starkinhon.battlegroundsmod.PowerLineBlock.CABLE, (a & 4) != 0)
                    .setValue(com.starkinhon.battlegroundsmod.PowerLineBlock.REACH, (a & 8) != 0);
        }
        if (t >= ShibuyaGen.WALL0 && t <= ShibuyaGen.WALL7) {
            return JujutsuBlocks.FACADES.get(t - ShibuyaGen.WALL0).get().defaultBlockState();
        }
        if (t >= ShibuyaGen.GLASS0 && t <= ShibuyaGen.GLASS3) {
            return JujutsuBlocks.TOWER_GLASSES.get(t - ShibuyaGen.GLASS0).get().defaultBlockState();
        }
        if (t >= ShibuyaGen.AWNING0 && t <= ShibuyaGen.AWNING3) {
            return JujutsuBlocks.AWNINGS.get(t - ShibuyaGen.AWNING0).get().defaultBlockState();
        }
        if (t >= ShibuyaGen.SIGN0 && t <= ShibuyaGen.SIGN7) {
            return JujutsuBlocks.SIGNS.get(t - ShibuyaGen.SIGN0).get().defaultBlockState();
        }
        if (t >= ShibuyaGen.VEND0 && t <= ShibuyaGen.VEND3) {
            return JujutsuBlocks.VENDINGS.get(t - ShibuyaGen.VEND0).get().defaultBlockState();
        }
        return switch (t) {
            case ShibuyaGen.STONE -> Blocks.STONE.defaultBlockState();
            case WILD_GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case WILD_TALL -> Blocks.SHORT_GRASS.defaultBlockState();
            case WILD_FLOWER -> Blocks.CORNFLOWER.defaultBlockState();
            case WILD_BUSH -> Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            case ShibuyaGen.SOIL -> Blocks.DIRT.defaultBlockState();
            case ShibuyaGen.ASPHALT -> JujutsuBlocks.SHIBUYA_ASPHALT.get().defaultBlockState();
            case ShibuyaGen.LANE, ShibuyaGen.ZEBRA, ShibuyaGen.PARK_LINE -> JujutsuBlocks.WHITE_LINE.get().defaultBlockState();
            case ShibuyaGen.YELLOW -> JujutsuBlocks.YELLOW_LINE.get().defaultBlockState();
            case ShibuyaGen.SIDEWALK -> JujutsuBlocks.SHIBUYA_SIDEWALK.get().defaultBlockState();
            case ShibuyaGen.SIDEWALK2 -> JujutsuBlocks.SHIBUYA_SIDEWALK_DARK.get().defaultBlockState();
            case ShibuyaGen.TACTILE -> JujutsuBlocks.TACTILE_PAVING.get().defaultBlockState();
            case ShibuyaGen.CURB -> JujutsuBlocks.CURB.get().defaultBlockState();
            case ShibuyaGen.LOT -> JujutsuBlocks.LOT_GROUND.get().defaultBlockState();
            case ShibuyaGen.ALLEY -> JujutsuBlocks.ALLEY_GROUND.get().defaultBlockState();
            case ShibuyaGen.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case ShibuyaGen.WIN -> JujutsuBlocks.BUILDING_WINDOW.get().defaultBlockState();
            case ShibuyaGen.MULLION -> JujutsuBlocks.MULLION.get().defaultBlockState();
            case ShibuyaGen.SLAB -> JujutsuBlocks.BUILDING_SLAB.get().defaultBlockState();
            case ShibuyaGen.CORNER -> JujutsuBlocks.DARK_CORNER.get().defaultBlockState();
            case ShibuyaGen.ROOF -> JujutsuBlocks.BUILDING_ROOF.get().defaultBlockState();
            case ShibuyaGen.PARAPET -> JujutsuBlocks.PARAPET.get().defaultBlockState();
            case ShibuyaGen.SHOP -> JujutsuBlocks.SHOP_WINDOW.get().defaultBlockState();
            case ShibuyaGen.DOOR -> JujutsuBlocks.GLASS_DOOR.get().defaultBlockState();
            case ShibuyaGen.GLYPH -> JujutsuBlocks.SIGN_GLYPH.get().defaultBlockState();
            case ShibuyaGen.SIGN_FRAME -> JujutsuBlocks.SIGN_FRAME.get().defaultBlockState();
            case ShibuyaGen.SCREEN -> JujutsuBlocks.BIG_SCREEN.get().defaultBlockState();
            case ShibuyaGen.METAL -> JujutsuBlocks.LIGHT_METAL.get().defaultBlockState();
            case ShibuyaGen.METAL_DARK -> JujutsuBlocks.DARK_METAL.get().defaultBlockState();
            case ShibuyaGen.TANK -> JujutsuBlocks.WATER_TANK.get().defaultBlockState();
            case ShibuyaGen.AVIATION -> JujutsuBlocks.AVIATION_LIGHT.get().defaultBlockState();
            case ShibuyaGen.POLE -> JujutsuBlocks.STREET_POLE.get().defaultBlockState();
            case ShibuyaGen.CABLE -> JujutsuBlocks.WIRE.get().defaultBlockState();
            case ShibuyaGen.LAMP -> JujutsuBlocks.STREET_LAMP.get().defaultBlockState();
            case ShibuyaGen.TRAFFIC_BOX -> JujutsuBlocks.TRAFFIC_BOX.get().defaultBlockState();
            case ShibuyaGen.TRAFFIC_R -> JujutsuBlocks.TRAFFIC_RED.get().defaultBlockState();
            case ShibuyaGen.TRAFFIC_G -> JujutsuBlocks.TRAFFIC_GREEN.get().defaultBlockState();
            case ShibuyaGen.RAILING -> JujutsuBlocks.GUARD_RAIL.get().defaultBlockState();
            case ShibuyaGen.PLANTER -> JujutsuBlocks.PLANTER.get().defaultBlockState();
            case ShibuyaGen.TRUNK -> JujutsuBlocks.STREET_TRUNK.get().defaultBlockState();
            case ShibuyaGen.LEAVES -> leaves(JujutsuBlocks.STREET_LEAVES.get().defaultBlockState());
            case ShibuyaGen.LEAVES2 -> leaves(JujutsuBlocks.STREET_LEAVES_DARK.get().defaultBlockState());
            case ShibuyaGen.VENDING -> JujutsuBlocks.VENDING_TOP.get().defaultBlockState();
            case ShibuyaGen.TORII -> ModBlocks.VERMILION_WOOD.get().defaultBlockState();
            case ShibuyaGen.TORII_TOP -> ModBlocks.BLACK_LACQUER.get().defaultBlockState();
            case ShibuyaGen.SHRINE_WOOD -> JujutsuBlocks.FARM_WOOD.get().defaultBlockState();
            case ShibuyaGen.SHRINE_ROOF -> JujutsuBlocks.BARK_ROOF.get().defaultBlockState();
            case ShibuyaGen.LANTERN -> ModBlocks.STONE_LANTERN_LIGHT.get().defaultBlockState();
            case ShibuyaGen.CURSE_STAIN -> JujutsuBlocks.CURSE_STAIN.get().defaultBlockState();
            case ShibuyaGen.CURSE_VEIN -> JujutsuBlocks.CURSED_VEIN.get().defaultBlockState();
            case ShibuyaGen.CURSE_GLOW -> JujutsuBlocks.CURSE_WINDOW.get().defaultBlockState();
            case ShibuyaGen.CURSE_FLOOR -> JujutsuBlocks.ABANDONED_FLOOR.get().defaultBlockState();
            case ShibuyaGen.BOARD -> JujutsuBlocks.NAILED_BOARDS.get().defaultBlockState();
            case ShibuyaGen.TAPE -> JujutsuBlocks.CAUTION_TAPE.get().defaultBlockState();
            case ShibuyaGen.TALISMAN -> JujutsuBlocks.STUCK_TALISMAN.get().defaultBlockState();
            case ShibuyaGen.DEAD_SIGN -> JujutsuBlocks.DEAD_SIGN.get().defaultBlockState();
            case ShibuyaGen.TILE_WALL -> JujutsuBlocks.METRO_TILE.get().defaultBlockState();
            case ShibuyaGen.TILE_LINE, ShibuyaGen.TRAIN_STRIPE -> JujutsuBlocks.ORANGE_STRIPE.get().defaultBlockState();
            case ShibuyaGen.TILE_FLOOR -> JujutsuBlocks.METRO_FLOOR.get().defaultBlockState();
            case ShibuyaGen.CEIL -> JujutsuBlocks.METRO_CEILING.get().defaultBlockState();
            case ShibuyaGen.CEIL_LIGHT -> JujutsuBlocks.METRO_LIGHT.get().defaultBlockState();
            case ShibuyaGen.PILLAR -> JujutsuBlocks.METRO_PILLAR.get().defaultBlockState();
            case ShibuyaGen.GATE -> JujutsuBlocks.TURNSTILE.get().defaultBlockState();
            case ShibuyaGen.EDGE -> JujutsuBlocks.PLATFORM_EDGE.get().defaultBlockState();
            case ShibuyaGen.STAIR -> JujutsuBlocks.METRO_STEP.get().defaultBlockState();
            case ShibuyaGen.BENCH -> JujutsuBlocks.METRO_BENCH.get().defaultBlockState();
            case ShibuyaGen.METRO_SIGN -> JujutsuBlocks.METRO_SIGN.get().defaultBlockState();
            case ShibuyaGen.SIGN_DARK -> JujutsuBlocks.METRO_SIGN_LETTER.get().defaultBlockState();
            case ShibuyaGen.BALLAST -> JujutsuBlocks.BALLAST.get().defaultBlockState();
            case ShibuyaGen.RAIL -> JujutsuBlocks.RAIL.get().defaultBlockState();
            case ShibuyaGen.TUNNEL -> JujutsuBlocks.TUNNEL_CONCRETE.get().defaultBlockState();
            case ShibuyaGen.TUNNEL_LIGHT -> JujutsuBlocks.TUNNEL_LIGHT.get().defaultBlockState();
            case ShibuyaGen.CROWN_LED -> JujutsuBlocks.LED_CROWN.get().defaultBlockState();
            case ShibuyaGen.FIN -> JujutsuBlocks.WHITE_FIN.get().defaultBlockState();
            case ShibuyaGen.DECK_GLASS -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case ShibuyaGen.LOUVER -> JujutsuBlocks.LOUVER.get().defaultBlockState();
            case ShibuyaGen.TRAIN -> JujutsuBlocks.TRAIN_BODY.get().defaultBlockState();
            case ShibuyaGen.TRAIN_WIN -> JujutsuBlocks.TRAIN_WINDOW.get().defaultBlockState();
            case ShibuyaGen.TRAIN_DOOR -> JujutsuBlocks.TRAIN_DOOR.get().defaultBlockState();
            case ShibuyaGen.TRAIN_ROOF -> JujutsuBlocks.TRAIN_ROOF.get().defaultBlockState();
            case ShibuyaGen.TRAIN_END -> JujutsuBlocks.TRAIN_END.get().defaultBlockState();
            case ShibuyaGen.TRAIN_UNDER -> JujutsuBlocks.TRAIN_UNDER.get().defaultBlockState();
            case ShibuyaGen.TRAIN_FRONT -> JujutsuBlocks.TRAIN_WINDSHIELD.get().defaultBlockState();
            case ShibuyaGen.HEADLIGHT -> JujutsuBlocks.TRAIN_HEADLIGHT.get().defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
