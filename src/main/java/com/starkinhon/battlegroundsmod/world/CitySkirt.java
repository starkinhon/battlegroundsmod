package com.starkinhon.battlegroundsmod.world;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

// The land under a city leveled at one height (Shibuya, the Colônia) and the skirt that blends it back into the hills
// around. Stark 2026-11-19: the old skirt had a fixed width (8, 16), so next to hills 30 high it was a wall of steps,
// and caves and lava showed under its shelves and under the asphalt.
// - The skirt is as wide as the hills are tall (1.5 blocks out for each block up or down, at least the city's old
//   width, at most MAX) and goes past the biome's edge into the plain biomes around (vanilla ones only: the mod's own
//   biomes put their trees and buildings on the noise's ground, which the skirt would leave them floating over).
// - A chunk without the city's biome never runs the city's feature, so it keeps its land: the skirt fades to nothing
//   before it (and before another mod biome). Every number here is a function of the column alone (the city mask, the
//   biome quarts, the noise's ground), so two chunks always agree along their edge.
// - Outside the biome it only moves the land: water below the sea stays (no hole in the ocean), trees on lowered
//   ground go and the leaves they held go with them (no leaves left hanging), plants on raised ground that can no
//   longer stand there go, and a chunk with a village or another building on the surface is left as it is.
// - Under the skirt's new top, 20 blocks of caves and fluids are filled; under the city, from 3 below the street down to
//   24 below.
final class CitySkirt {
    static final int MAX = 40;
    // the skirt is nothing this close to a chunk that keeps its land (or another mod biome)
    private static final int BAND = 4;
    private static final int GRID = 48;
    private static final int CAVES = 20;
    private static final int UNDER = 24;
    private static final int CLEAR = 24;
    private static final Cache<Long, Boolean> VANILLA = CacheBuilder.newBuilder().maximumSize(65536).build();

    interface Plan {
        boolean city(int x, int z);

        // the city's biome on this quart
        boolean own(int qx, int qz);

        // the skirt's top block inside the biome (null keeps what is there)
        BlockState ground(int x, int z, int top, int h, double d);

        // after the column's top is set (inside the biome)
        default void after(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z, double d) {
        }
    }

    // Levels this chunk's columns. 'inner' is the city's old skirt width (inside the biome everything above the new top
    // is cut there, as before), 'fillTop' the highest block filled with stone under the city. False when the city is
    // nowhere near.
    static boolean level(WorldGenLevel level, ChunkGenerator generator, RandomState random, int worldKey, ChunkPos chunk,
            IntBinaryOperator ground, int cityY, int inner, int fillTop, Plan plan) {
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        int n = 16 + 2 * GRID, gx0 = x0 - GRID, gz0 = z0 - GRID;
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1, sea = cityY - 1;

        // the distance to the city
        int[] city = new int[n * n];
        boolean any = false;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                boolean c = plan.city(gx0 + a, gz0 + b);
                city[a * n + b] = c ? 0 : Integer.MAX_VALUE / 2;
                any |= c;
            }
        }
        if (!any) {
            return false;
        }
        chamfer(city, n);

        // the distance to the land that stays: chunks without the biome, quarts of another mod biome
        int qn = n / 4, qy = QuartPos.fromBlock(cityY), cq0x = gx0 >> 2, cq0z = gz0 >> 2;
        boolean[] own = new boolean[qn * qn];
        for (int a = 0; a < qn; a++) {
            for (int b = 0; b < qn; b++) {
                own[a * qn + b] = plan.own(cq0x + a, cq0z + b);
            }
        }
        int cn = qn / 4;
        boolean[] runs = new boolean[cn * cn];
        for (int ca = 0; ca < cn; ca++) {
            for (int cb = 0; cb < cn; cb++) {
                boolean r = false;
                for (int a = 0; a < 4 && !r; a++) {
                    for (int b = 0; b < 4 && !r; b++) {
                        r = own[(ca * 4 + a) * qn + cb * 4 + b];
                    }
                }
                runs[ca * cn + cb] = r;
            }
        }
        int[] edge = new int[n * n];
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                int qa = a >> 2, qb = b >> 2;
                boolean stays = !runs[(qa >> 2) * cn + (qb >> 2)]
                        || !own[qa * qn + qb] && !vanilla(generator, random, worldKey, cq0x + qa, qy, cq0z + qb);
                edge[a * n + b] = stays ? 0 : Integer.MAX_VALUE / 2;
            }
        }
        chamfer(edge, n);
        boolean built = surfaceStructure(level, chunk);

        // the plan of each column: its new top, and whether the land above it is cut
        int[] tops = new int[256], hs = new int[256];
        double[] ds = new double[256];
        byte[] mode = new byte[256]; // 0 untouched, 1 city, 2 inner (cut all), 3 lowered, 4 raised
        boolean cut = false;
        int cutLo = Integer.MAX_VALUE;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz, i = lx * 16 + lz, g = (lx + GRID) * n + lz + GRID;
                int h = ground.applyAsInt(x, z);
                hs[i] = h;
                if (city[g] == 0) {
                    mode[i] = 1;
                    tops[i] = cityY;
                    continue;
                }
                double d = city[g] / 3.0, e = Math.min(GRID, edge[g] / 3.0);
                boolean in = plan.own(x >> 2, z >> 2);
                int w = Math.max(inner, Math.min(MAX, (int) Math.ceil(Math.abs(h - cityY) * 1.5)));
                if (d > w || !in && built) {
                    continue;
                }
                double k = 1 - d / (w + 1);
                k = k * k * (3 - 2 * k);
                double ee = Math.max(0, e - BAND);
                k *= ee / (ee + d);
                int top = JjkGen.round(h + (cityY - h) * k);
                ds[i] = d;
                tops[i] = top;
                if (in && d <= inner) {
                    mode[i] = 2;
                } else if (top < h) {
                    mode[i] = 3;
                } else if (top > h) {
                    mode[i] = 4;
                } else {
                    continue;
                }
                if (mode[i] <= 3) {
                    cut = true;
                    cutLo = Math.min(cutLo, top);
                }
            }
        }

        // the leaves held by what is about to be cut
        Leaves leaves = cut ? new Leaves(level, x0, z0, Math.max(minY, cutLo - 7), maxY) : null;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int i = lx * 16 + lz, x = x0 + lx, z = z0 + lz, h = hs[i], top = tops[i];
                if (mode[i] == 0) {
                    continue;
                }
                if (mode[i] == 1) {
                    clear(level, pos, x, z, cityY + 1, Math.max(Math.max(h, cityY) + CLEAR, surface(level, x, z)), maxY, Integer.MIN_VALUE);
                    for (int y = Math.max(minY, h + 1); y <= fillTop; y++) {
                        level.setBlock(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                    }
                    fill(level, pos, x, z, Math.max(minY, cityY - UNDER), fillTop, y -> Blocks.STONE.defaultBlockState());
                    continue;
                }
                boolean in = plan.own(x >> 2, z >> 2);
                double d = ds[i];
                // the land's own top and what lies under it (sand stays sand, snow stays snow)
                BlockState surf = solid(level, pos.set(x, h, z)) ? level.getBlockState(pos) : Blocks.GRASS_BLOCK.defaultBlockState();
                BlockState sub = solid(level, pos.set(x, h - 1, z)) ? level.getBlockState(pos) : Blocks.DIRT.defaultBlockState();
                if (in) {
                    sub = Blocks.DIRT.defaultBlockState();
                }
                if (mode[i] == 2) {
                    clear(level, pos, x, z, top + 1, Math.max(Math.max(h, top) + CLEAR, surface(level, x, z)), maxY, Integer.MIN_VALUE);
                } else if (mode[i] == 3) {
                    clear(level, pos, x, z, top + 1, Math.max(h + CLEAR, surface(level, x, z)), maxY, sea);
                }
                final BlockState under = sub;
                for (int y = Math.max(minY, h + 1); y < top; y++) {
                    level.setBlock(pos.set(x, y, z), (top - y > 3 ? Blocks.STONE.defaultBlockState() : under), 2);
                }
                fill(level, pos, x, z, Math.max(minY, top - CAVES), Math.min(h, top - 1),
                        y -> top - y > 3 ? Blocks.STONE.defaultBlockState() : under);
                if (top < minY || top > maxY) {
                    continue;
                }
                BlockState t = in ? plan.ground(x, z, top, h, d) : top != h ? surf : null;
                if (t != null) {
                    level.setBlock(pos.set(x, top, z), t, 2);
                }
                if (mode[i] == 4) {
                    // what stood on the old ground and now sits in the air or in the new one
                    for (int y = top + 1; y <= Math.min(maxY, top + 2); y++) {
                        BlockState b = level.getBlockState(pos.set(x, y, z));
                        if (!b.isAir() && b.getFluidState().createLegacyBlock() != b && !b.canSurvive(level, pos)) {
                            level.setBlock(pos, b.getFluidState().createLegacyBlock(), 2);
                        }
                    }
                }
                if (in) {
                    plan.after(level, pos, x, top, z, d);
                }
            }
        }

        if (leaves != null) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int i = lx * 16 + lz;
                    if (mode[i] == 2 || mode[i] == 3) {
                        leaves.cut(x0 + lx, z0 + lz, tops[i]);
                    }
                }
            }
            leaves.drop(level, pos);
        }
        return true;
    }

    // ---------------------------------------------------------------- the pieces

    // Distances (3 a step, 4 a diagonal: about 3 per block) to the nearest 0, in two sweeps.
    private static void chamfer(int[] f, int n) {
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                int v = f[a * n + b];
                if (a > 0) {
                    v = Math.min(v, f[(a - 1) * n + b] + 3);
                    if (b > 0) {
                        v = Math.min(v, f[(a - 1) * n + b - 1] + 4);
                    }
                    if (b < n - 1) {
                        v = Math.min(v, f[(a - 1) * n + b + 1] + 4);
                    }
                }
                if (b > 0) {
                    v = Math.min(v, f[a * n + b - 1] + 3);
                }
                f[a * n + b] = v;
            }
        }
        for (int a = n - 1; a >= 0; a--) {
            for (int b = n - 1; b >= 0; b--) {
                int v = f[a * n + b];
                if (a < n - 1) {
                    v = Math.min(v, f[(a + 1) * n + b] + 3);
                    if (b > 0) {
                        v = Math.min(v, f[(a + 1) * n + b - 1] + 4);
                    }
                    if (b < n - 1) {
                        v = Math.min(v, f[(a + 1) * n + b + 1] + 4);
                    }
                }
                if (b < n - 1) {
                    v = Math.min(v, f[a * n + b + 1] + 3);
                }
                f[a * n + b] = v;
            }
        }
    }

    private static boolean vanilla(ChunkGenerator generator, RandomState random, int worldKey, int qx, int qy, int qz) {
        long k = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
        try {
            return VANILLA.get(k, () -> generator.getBiomeSource().getNoiseBiome(qx, qy, qz, random.sampler()).unwrapKey()
                    .map(key -> key.location().getNamespace().equals("minecraft")).orElse(false));
        } catch (ExecutionException e) {
            return false;
        }
    }

    // A village, a temple, a ruined portal... reaching this chunk (the ones under the ground don't count).
    private static boolean surfaceStructure(WorldGenLevel level, ChunkPos chunk) {
        for (Map.Entry<Structure, LongSet> r : level.getChunk(chunk.x, chunk.z).getAllReferences().entrySet()) {
            GenerationStep.Decoration step = r.getKey().step();
            if (!r.getValue().isEmpty() && step != GenerationStep.Decoration.UNDERGROUND_STRUCTURES
                    && step != GenerationStep.Decoration.STRONGHOLDS) {
                return true;
            }
        }
        return false;
    }

    private static boolean solid(WorldGenLevel level, BlockPos pos) {
        BlockState b = level.getBlockState(pos);
        return !b.isAir() && b.getFluidState().isEmpty() && b.isCollisionShapeFullBlock(level, pos)
                && !b.is(BlockTags.LOGS) && !b.is(BlockTags.LEAVES);
    }

    private static int surface(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    // Caves and fluids from y0 to y1 filled.
    private static void fill(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y0, int y1,
            java.util.function.IntFunction<BlockState> with) {
        for (int y = y0; y <= y1; y++) {
            BlockState b = level.getBlockState(pos.set(x, y, z));
            if (b.isAir() || !b.getFluidState().isEmpty() && !b.is(Blocks.BEDROCK)) {
                level.setBlock(pos, with.apply(y), 2);
            }
        }
    }

    // Cuts the land away from y0 to y1, then on up while it still finds something (until 8 blocks of air in a row: what
    // a neighbour chunk's feature put above the heightmap). Fluids below 'sea' stay.
    private static void clear(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y0, int y1, int maxY, int sea) {
        for (int y = y0; y <= Math.min(y1, maxY); y++) {
            cut(level, pos.set(x, y, z), y, sea);
        }
        int air = 0;
        for (int y = Math.max(y0, y1 + 1); y <= maxY && air < 8; y++) {
            if (level.getBlockState(pos.set(x, y, z)).isAir()) {
                air++;
            } else {
                air = 0;
                cut(level, pos, y, sea);
            }
        }
    }

    private static void cut(WorldGenLevel level, BlockPos pos, int y, int sea) {
        BlockState b = level.getBlockState(pos);
        if (!b.isAir() && !b.is(Blocks.BEDROCK) && !(y < sea && !b.getFluidState().isEmpty())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // The leaves around the chunk (8 out; the logs that hold them read 14 out, all inside the neighbour chunks): those a
    // log held before the cut and none holds after it are dropped, so no crown is left in the air; leaves no log held
    // before (a big tree of the mod's, persistent ones) are left alone.
    private static final class Leaves {
        private static final int R = 14, W = 8;
        private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        final int ox, oz, oy, n, ny;
        final byte[] kind; // 0 other, 1 leaf, 2 log
        final boolean[] held;

        Leaves(WorldGenLevel level, int x0, int z0, int yLo, int maxY) {
            ox = x0 - R;
            oz = z0 - R;
            n = 16 + 2 * R;
            int hi = yLo;
            for (int a = 0; a < n; a++) {
                for (int b = 0; b < n; b++) {
                    hi = Math.max(hi, surface(level, ox + a, oz + b));
                }
            }
            oy = yLo;
            ny = Math.min(maxY, hi + 1) - yLo + 1;
            kind = new byte[n * n * ny];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int a = 0; a < n; a++) {
                for (int b = 0; b < n; b++) {
                    int top = Math.min(oy + ny - 1, surface(level, ox + a, oz + b));
                    for (int y = oy; y <= top; y++) {
                        BlockState s = level.getBlockState(pos.set(ox + a, y, oz + b));
                        kind[idx(a, y - oy, b)] = s.is(BlockTags.LOGS) ? (byte) 2
                                : s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.DISTANCE) ? (byte) 1 : 0;
                    }
                }
            }
            held = reach();
        }

        int idx(int a, int y, int b) {
            return (a * n + b) * ny + y;
        }

        void cut(int x, int z, int top) {
            int a = x - ox, b = z - oz;
            for (int y = Math.max(0, top + 1 - oy); y < ny; y++) {
                kind[idx(a, y, b)] = 0;
            }
        }

        // The leaves within 6 steps (through leaves) of a log, as the game counts them.
        boolean[] reach() {
            int size = kind.length;
            byte[] dist = new byte[size];
            int[] queue = new int[size];
            int head = 0, tail = 0;
            for (int i = 0; i < size; i++) {
                dist[i] = 7;
                if (kind[i] == 2) {
                    dist[i] = 0;
                    queue[tail++] = i;
                }
            }
            while (head < tail) {
                int i = queue[head++];
                int y = i % ny, ab = i / ny, a = ab / n, b = ab % n;
                if (dist[i] >= 6) {
                    continue;
                }
                for (int[] s : STEPS) {
                    int na = a + s[0], ny2 = y + s[1], nb = b + s[2];
                    if (na < 0 || nb < 0 || na >= n || nb >= n || ny2 < 0 || ny2 >= ny) {
                        continue;
                    }
                    int j = idx(na, ny2, nb);
                    if (kind[j] == 1 && dist[j] > dist[i] + 1) {
                        dist[j] = (byte) (dist[i] + 1);
                        queue[tail++] = j;
                    }
                }
            }
            boolean[] out = new boolean[size];
            for (int i = 0; i < size; i++) {
                out[i] = kind[i] == 1 && dist[i] <= 6;
            }
            return out;
        }

        void drop(WorldGenLevel level, BlockPos.MutableBlockPos pos) {
            boolean[] now = reach();
            for (int a = R - W; a < n - (R - W); a++) {
                for (int b = R - W; b < n - (R - W); b++) {
                    for (int y = 0; y < ny; y++) {
                        int i = idx(a, y, b);
                        if (!held[i] || now[i]) {
                            continue;
                        }
                        BlockState s = level.getBlockState(pos.set(ox + a, oy + y, oz + b));
                        if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
                            level.setBlock(pos, s.getFluidState().createLegacyBlock(), 2);
                        }
                    }
                }
            }
        }
    }
}
