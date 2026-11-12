package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

// The Naruto structures (Stark 2026-11-12: "estruturas raras com itens do universo, cada uma com a sua atividade"), a
// line by line port of the preview's generator (forja/naruto-estruturas/gen.js, the inWorld path): the same xorshift32,
// the same hash, the same order of every random draw, StrictMath for sin/cos/atan2, so a seed gives the same blocks as
// the preview. Pure Java (no Minecraft): NarutoSites turns the cells into blocks. The world's ground comes in through
// World (the natural top block, the floor of the place and the sea level); only the flattened footprint of the
// terrain is written (the top and two blocks under it), the rest of the ground stays the biome's own.
// A cell set to AIR is "carve": it puts air in the world (tunnels, the nest, the hall); a cell never set leaves the world
// as it is.
public final class NarutoStructGen {
    public static final int AIR = 0, STONE = 1, COBBLE = 2, MOSSY = 3, BRICK = 4, SMOOTH = 5, DARK = 6, DARK_CARVE = 7,
            GRASS = 8, DARK_GRASS = 9, DIRT = 10, COARSE = 11, PODZOL = 12, GRAVEL = 13, SAND = 14, SANDSTONE = 15,
            ROCK_R = 16, ROCK_L = 17, ROCK_D = 18, WATER = 19, OIL = 20, OILFALL = 21, LOG = 22, LOG_CUT = 23, PLANK = 24,
            DPLANK = 25, DLOG = 26, LEAVES = 27, LEAVES_D = 28, PINE_LOG = 29, PINE = 30, FENCE = 31, BARS = 32, ROPE = 33,
            TARGET = 34, BELL = 35, PAPER = 36, METAL = 37, METAL_D = 38, RUST = 39, WIN = 40, WIN_LIT = 41, PIPE = 42,
            RED = 43, ROOF = 44, BONE = 45, SKIN = 46, BLADE = 47, WRAP = 48, CALCITE = 49, STATUE = 50, STATUE_M = 51,
            LEAF_G = 52, STEM = 53, PETAL = 54, SHELF = 55, CLOTH = 56, PUPPET = 57, GOLD = 58, LANTERN = 59, CHEST = 60,
            FLOWER = 61, VINE = 62, THREAD = 63, PEAK = 64, PEAK_M = 65, CARVE = 66, WHITE = 67, ROOT_W = 68, MUD = 69,
            BARREL = 70, SLAB = 71, ROOF_SLAB = 72, PLANK_SLAB = 73, GRATE = 74, SPIKE = 75, MOSS = 76, CORE = 77, BLACK = 78;

    public static final String FOLHA = "folha", MORTE = "morte", COVIL = "covil", DESERTO = "deserto", NEVOA = "nevoa",
            CHUVA = "chuva", MYOBOKU = "myoboku", PICO = "pico";

    private NarutoStructGen() {
    }

    // The world under the place: ground(x, z) = the natural top block (local coordinates), floor = the place's floor,
    // sea = the sea's surface.
    public interface World {
        int ground(int x, int z);

        int floor();

        int sea();
    }

    // ---------------------------------------------------------------- the preview's random and hash

    public static final class Rng {
        private int s;

        public Rng(int seed) {
            s = seed != 0 ? seed : 0x9E3779B9;
        }

        public double next() {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            return (s & 0xFFFFFFFFL) / 4294967296.0;
        }

        public double range(double a, double b) {
            return a + (b - a) * next();
        }

        public int nextInt(int a, int b) {
            return a + (int) Math.floor(next() * (b - a + 1));
        }

        public int pick(int[] arr) {
            return arr[(int) Math.floor(next() * arr.length)];
        }
    }

    public static double hash3(int x, int y, int z, int seed) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (seed * (int) 2654435761L);
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    // The preview's cells: a map (x, y, z) -> type. force writes always (AIR is a carve); set writes only where there is
    // nothing (or a carve) yet.
    public static final class Vox {
        public final HashMap<Long, Integer> m = new HashMap<>(1 << 16);

        public static long key(int x, int y, int z) {
            return ((long) (x + 524288) * 1048576L + (z + 524288)) * 4096L + (y + 2048);
        }

        public static int kx(long k) {
            int y = (int) (k % 4096) - 2048;
            long r = (k - (y + 2048)) / 4096;
            int z = (int) (r % 1048576) - 524288;
            return (int) ((r - (z + 524288)) / 1048576) - 524288;
        }

        public static int ky(long k) {
            return (int) (k % 4096) - 2048;
        }

        public static int kz(long k) {
            long r = (k - (ky(k) + 2048)) / 4096;
            return (int) (r % 1048576) - 524288;
        }

        public int get(int x, int y, int z) {
            Integer v = m.get(key(x, y, z));
            return v == null ? 0 : v;
        }

        public void set(int x, int y, int z, int t) {
            long k = key(x, y, z);
            Integer v = m.get(k);
            if (v == null || v == 0) {
                m.put(k, t);
            }
        }

        public void force(int x, int y, int z, int t) {
            m.put(key(x, y, z), t);
        }
    }

    // What a structure gives: its cells, the columns of the flattened ground (cleared up to the sky before building),
    // its radius and the spots the activities need (local coordinates).
    public static final class Result {
        public final Vox w;
        public final List<int[]> columns = new ArrayList<>();
        public final Map<String, List<int[]>> marks = new LinkedHashMap<>();
        public int radius;

        Result(Vox w) {
            this.w = w;
        }

        void mark(String name, int x, int y, int z) {
            marks.computeIfAbsent(name, k -> new ArrayList<>()).add(new int[] {x, y, z});
        }

        public int[] first(String name) {
            List<int[]> list = marks.get(name);
            return list == null || list.isEmpty() ? null : list.get(0);
        }
    }

    public static Result generate(String kind, int seed, World world) {
        return switch (kind) {
            case FOLHA -> folha(seed, world);
            case MORTE -> morte(seed, world);
            case COVIL -> covil(seed, world);
            case DESERTO -> deserto(seed, world);
            case NEVOA -> nevoa(seed, world);
            case CHUVA -> chuva(seed, world);
            case MYOBOKU -> myoboku(seed, world);
            case PICO -> pico(seed, world);
            default -> throw new IllegalArgumentException(kind);
        };
    }

    // ---------------------------------------------------------------- math as in the preview

    private static double smooth(double a) {
        return a * a * (3 - 2 * a);
    }

    private static double sq(double a) {
        return a * a;
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hyp(double a, double b) {
        return Math.sqrt(a * a + b * b + 0);
    }

    private static double hyp(double a, double b, double c) {
        return Math.sqrt(a * a + b * b + (c != 0 ? c * c : 0));
    }

    private static double sin(double a) {
        return StrictMath.sin(a);
    }

    private static double cos(double a) {
        return StrictMath.cos(a);
    }

    private static int round(double a) {
        return (int) Math.round(a);
    }

    private static int floor(double a) {
        return (int) Math.floor(a);
    }

    private static int ceil(double a) {
        return (int) Math.ceil(a);
    }

    public static double valueNoise(double x, double z, int seed, double scale) {
        double fx = x / scale, fz = z / scale;
        int ix = floor(fx), iz = floor(fz);
        double tx = fx - ix, tz = fz - iz;
        double h00 = hash3(ix, 0, iz, seed), h10 = hash3(ix + 1, 0, iz, seed);
        double a = h00 + (h10 - h00) * smooth(tx);
        double h01 = hash3(ix, 0, iz + 1, seed), h11 = hash3(ix + 1, 0, iz + 1, seed);
        double b = h01 + (h11 - h01) * smooth(tx);
        return a + (b - a) * smooth(tz);
    }

    public static double valueNoise3(double x, double y, double z, int seed, double scale) {
        double fx = x / scale, fy = y / scale, fz = z / scale;
        int ix = floor(fx), iy = floor(fy), iz = floor(fz);
        double tx = smooth(fx - ix), ty = smooth(fy - iy), tz = smooth(fz - iz);
        double x00 = lerp(hash3(ix, iy, iz, seed), hash3(ix + 1, iy, iz, seed), tx);
        double x10 = lerp(hash3(ix, iy + 1, iz, seed), hash3(ix + 1, iy + 1, iz, seed), tx);
        double x01 = lerp(hash3(ix, iy, iz + 1, seed), hash3(ix + 1, iy, iz + 1, seed), tx);
        double x11 = lerp(hash3(ix, iy + 1, iz + 1, seed), hash3(ix + 1, iy + 1, iz + 1, seed), tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    // ---------------------------------------------------------------- building tools

    private static void box(Vox w, int x0, int y0, int z0, int x1, int y1, int z1, int t) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    w.force(x, y, z, t);
                }
            }
        }
    }

    private static void beam(Vox w, double ax, double ay, double az, double bx, double by, double bz, double r, int t, boolean keep) {
        beam(w, ax, ay, az, bx, by, bz, null, r, t, keep);
    }

    private static void beam(Vox w, double ax, double ay, double az, double bx, double by, double bz, DoubleUnaryOperator rf, int t,
            boolean keep) {
        beam(w, ax, ay, az, bx, by, bz, rf, 0, t, keep);
    }

    // A thick line between two points (branch, root, beam, pipe); the radius can change along it.
    private static void beam(Vox w, double ax, double ay, double az, double bx, double by, double bz, DoubleUnaryOperator rf, double r0,
            int t, boolean keep) {
        double d = hyp(bx - ax, by - ay, bz - az);
        int n = Math.max(1, ceil(d * 2));
        int R = ceil(rf != null ? Math.max(rf.applyAsDouble(0), rf.applyAsDouble(1)) : r0);
        for (int i = 0; i <= n; i++) {
            double k = (double) i / n, cx = ax + (bx - ax) * k, cy = ay + (by - ay) * k, cz = az + (bz - az) * k;
            double rr = rf != null ? rf.applyAsDouble(k) : r0;
            int x0 = floor(cx - R - 1), x1 = ceil(cx + R), y0 = floor(cy - R - 1), y1 = ceil(cy + R), z0 = floor(cz - R - 1), z1 = ceil(cz + R);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        if (sq(x + 0.5 - cx) + sq(y + 0.5 - cy) + sq(z + 0.5 - cz) > rr * rr + 0.05) {
                            continue;
                        }
                        if (keep) {
                            w.set(x, y, z, t);
                        } else {
                            w.force(x, y, z, t);
                        }
                    }
                }
            }
        }
    }

    private static void ball(Vox w, double cx, double cy, double cz, double rx, double ry, double rz, int t, boolean keep) {
        int x0 = floor(cx - rx), x1 = ceil(cx + rx), y0 = floor(cy - ry), y1 = ceil(cy + ry), z0 = floor(cz - rz), z1 = ceil(cz + rz);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (sq((x + 0.5 - cx) / rx) + sq((y + 0.5 - cy) / ry) + sq((z + 0.5 - cz) / rz) > 1) {
                        continue;
                    }
                    if (keep) {
                        w.set(x, y, z, t);
                    } else {
                        w.force(x, y, z, t);
                    }
                }
            }
        }
    }

    private static boolean inR(int x, int z, double cx, double cz, double r) {
        return sq(x + 0.5 - cx) + sq(z + 0.5 - cz) <= r * r;
    }

    interface Top {
        int at(int x, int z, int h);
    }

    interface Sub {
        int at(int x, int z);
    }

    interface Ground {
        int at(int x, int z);
    }

    // The ground around the structure: the world's, flattened to the floor inside the footprint and blended at the edge
    // (14 blocks); only the flattened circle (flat + 14) is written: its top block and two under it.
    private static Ground terrain(Result res, World W, double flat, int fy, Top top, Sub sub) {
        Vox w = res.w;
        HashMap<Long, Integer> hf = new HashMap<>();
        Ground g = (x, z) -> {
            long k = (long) x * 4096 + z;
            Integer c = hf.get(k);
            if (c != null) {
                return c;
            }
            int h = W.ground(x, z);
            if (flat > 0) {
                h = round(lerp(fy, h, smooth(clamp((hyp(x, z) - flat) / 14, 0, 1))));
            }
            hf.put(k, h);
            return h;
        };
        if (flat > 0) {
            int F = (int) flat + 14;
            for (int x = -F; x <= F; x++) {
                for (int z = -F; z <= F; z++) {
                    if (x * x + z * z > F * F) {
                        continue;
                    }
                    int h = g.at(x, z);
                    w.set(x, h, z, top.at(x, z, h));
                    for (int y = h - 2; y < h; y++) {
                        w.set(x, y, z, sub.at(x, z));
                    }
                    res.columns.add(new int[] {x, h, z});
                }
            }
        }
        return g;
    }

    // The trees the structures bring (the biome has its own around them).
    private static void leafTree(Vox w, int x, int y, int z, int seed, boolean big) {
        Rng r = new Rng(seed);
        int h = big ? r.nextInt(15, 21) : r.nextInt(8, 12), thick = big ? 2 : 1;
        for (int i = 0; i < h; i++) {
            for (int a = 0; a < thick; a++) {
                for (int b = 0; b < thick; b++) {
                    w.force(x + a, y + i, z + b, LOG);
                }
            }
        }
        if (big) {
            int[] dxs = {1, -1, 0, 0}, dzs = {0, 0, 1, -1};
            for (int k = 0; k < 4; k++) {
                int dx = dxs[k], dz = dzs[k];
                beam(w, x + 1 + dx * 1.5, y + 2, z + 1 + dz * 1.5, x + 1 + dx * 3.2, y - 0.5, z + 1 + dz * 3.2, 0.8, LOG, true);
            }
        }
        double cx = x + thick / 2.0, cz = z + thick / 2.0;
        int top = y + h;
        int n = big ? 6 : 3;
        for (int i = 0; i < n; i++) {
            double a = r.range(0, Math.PI * 2), d = r.range(0, big ? 4.5 : 2), yy = top - r.range(0, big ? 6 : 3);
            double rx = big ? r.range(3.6, 5.4) : r.range(2.6, 3.6);
            ball(w, cx + cos(a) * d, yy, cz + sin(a) * d, rx, rx * 0.62, rx, hash3(i, x, z, seed) < 0.5 ? LEAVES : LEAVES_D, true);
            if (big && d > 1.5) {
                beam(w, cx, yy - 3, cz, cx + cos(a) * d, yy - 1, cz + sin(a) * d, 0.7, LOG, true);
            }
        }
    }

    // A tree of the Forest of Death: tall dark trunk with buttress roots and the crown far up.
    private static void darkTree(Vox w, int x, int y, int z, int seed) {
        Rng r = new Rng(seed);
        int h = r.nextInt(22, 30);
        for (int i = 0; i < h; i++) {
            double rr = 1.6 - (double) i / h * 0.6;
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    if (a * a + b * b <= rr * rr + 0.3) {
                        w.force(x + a, y + i, z + b, DLOG);
                    }
                }
            }
        }
        for (int k = 0; k < 5; k++) {
            double a = k / 5.0 * Math.PI * 2 + r.range(0, 1);
            beam(w, x + 0.5, y + 4.5, z + 0.5, x + 0.5 + cos(a) * 4.5, y - 0.5, z + 0.5 + sin(a) * 4.5, k2 -> 1.1 - k2 * 0.6, DLOG, true);
        }
        for (int i = 0; i < 5; i++) {
            double a = r.range(0, Math.PI * 2), d = r.range(2, 6), yy = y + h - r.range(0, 6);
            beam(w, x + 0.5, yy - 4, z + 0.5, x + 0.5 + cos(a) * d, yy, z + 0.5 + sin(a) * d, 0.6, DLOG, true);
            ball(w, x + 0.5 + cos(a) * d, yy + 1, z + 0.5 + sin(a) * d, r.range(4, 6), r.range(2, 3), r.range(4, 6), LEAVES_D, true);
        }
    }

    private static void pine(Vox w, int x, int y, int z, int seed) {
        Rng r = new Rng(seed);
        int h = r.nextInt(9, 14);
        for (int i = 0; i < h; i++) {
            w.force(x, y + i, z, PINE_LOG);
        }
        for (int i = 3; i <= h + 1; i++) {
            double rr = (double) (h + 1 - i) / (h - 2) * 3.2 + 0.4;
            if ((i & 1) != 0 && i < h) {
                continue;
            }
            for (int a = -4; a <= 4; a++) {
                for (int b = -4; b <= 4; b++) {
                    if (a * a + b * b <= rr * rr && (a != 0 || b != 0 || i > h - 1)) {
                        w.set(x + a, y + i, z + b, PINE);
                    }
                }
            }
        }
    }

    // ================================================================ 1. FOLHA: Training Ground 3 (the bell test)
    static Result folha(int seed, World W) {
        Vox w = new Vox();
        Result res = new Result(w);
        Rng r = new Rng(seed * 31 + 7);
        int R = 60, Y = W.floor();
        res.radius = R;
        Ground ground = terrain(res, W, 26, Y, (x, z, h) -> {
            double n = valueNoise(x, z, seed + 5, 6);
            return hyp(x, z) < 24 && n > 0.66 ? COARSE : GRASS;
        }, (x, z) -> DIRT);
        // the beaten path from the south to the stumps
        for (int z = 6; z <= R; z++) {
            double cx = sin(z / 9.0 + seed) * 3;
            for (int x = floor(cx - 1.5); x <= ceil(cx + 1.5); x++) {
                if (hash3(x, 1, z, seed) < 0.86) {
                    w.force(x, ground.at(x, z), z, COARSE);
                }
            }
        }
        for (int x = -9; x <= 9; x++) {
            for (int z = -10; z <= 3; z++) {
                if (sq(x / 9.0) + sq((z + 3) / 7.0) < 1 && hash3(x, 2, z, seed) < 0.7) {
                    w.force(x, Y, z, COARSE);
                }
            }
        }
        // the stream along the east side, with stepping stones
        DoubleUnaryOperator sx = z -> 19 + sin(z / 13 + seed * 0.7) * 5;
        for (int z = -R; z <= R; z++) {
            double c = sx.applyAsDouble(z);
            for (int x = floor(c - 4); x <= ceil(c + 4); x++) {
                double d = Math.abs(x + 0.5 - c);
                int g = ground.at(x, z);
                if (d < 2.2) {
                    w.force(x, g, z, WATER);
                    w.force(x, g - 1, z, WATER);
                    w.force(x, g - 2, z, GRAVEL);
                } else if (d < 3.4) {
                    w.force(x, g, z, hash3(x, 3, z, seed) < 0.5 ? GRAVEL : MOSSY);
                }
            }
        }
        for (int k = -1; k <= 1; k++) {
            int z = 2 + k * 2, x = round(sx.applyAsDouble(z) + k * 1.2);
            w.force(x, ground.at(x, z), z, MOSSY);
            w.force(x, ground.at(x, z) + 1, z, MOSSY);
        }
        // the three stumps; the middle one with the rope and the bells
        for (int px : new int[] {-5, 0, 5}) {
            int h = px == 0 ? 3 : 2 + (hash3(px, 0, 0, seed) < 0.5 ? 1 : 0);
            for (int y = 1; y <= h; y++) {
                w.force(px, Y + y, -6, LOG);
            }
            w.force(px, Y + h, -6, LOG_CUT);
            w.force(px, Y + 2, -6, px == 0 ? ROPE : LOG);
        }
        w.force(0, Y + 2, -6, LOG_CUT);
        w.force(-1, Y + 3, -6, BELL);
        w.force(1, Y + 3, -6, BELL);
        res.mark("bell", -1, Y + 3, -6);
        res.mark("bell", 1, Y + 3, -6);
        // the memorial stone
        int mx = -11, mz = -15;
        box(w, mx - 3, Y + 1, mz - 2, mx + 3, Y + 1, mz + 2, BRICK);
        box(w, mx - 2, Y + 2, mz - 1, mx + 2, Y + 2, mz + 1, SMOOTH);
        for (int y = 3; y <= 9; y++) {
            int hw = y <= 6 ? 1 : y == 7 ? 1 : 0;
            for (int x = -hw; x <= hw; x++) {
                w.force(mx + x, Y + y, mz, y >= 4 && y <= 6 && x == 0 ? DARK_CARVE : DARK);
            }
        }
        w.force(mx, Y + 10, mz, DARK);
        for (int fx : new int[] {-1, 1}) {
            w.force(mx + fx, Y + 3, mz + 1, FLOWER);
        }
        // training dummies and a log pile
        for (int[] bb : new int[][] {{-15, 2}, {-13, 8}}) {
            int bx = bb[0], bz = bb[1];
            for (int y = 1; y <= 3; y++) {
                w.force(bx, Y + y, bz, LOG);
            }
            w.force(bx - 1, Y + 3, bz, FENCE);
            w.force(bx + 1, Y + 3, bz, FENCE);
            w.force(bx, Y + 4, bz, TARGET);
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < (i < 2 ? 2 : 1); j++) {
                for (int x = 0; x < 4; x++) {
                    w.force(-20 + x, Y + 1 + j, 11 + i, LOG);
                }
            }
        }
        // the little log gate at the path's entrance
        int gz = 27;
        for (int gx : new int[] {-3, 3}) {
            int g = ground.at(gx, gz);
            for (int y = 1; y <= 5; y++) {
                w.force(gx, g + y, gz, LOG);
            }
        }
        int gt = Math.max(ground.at(-3, gz), ground.at(3, gz)) + 6;
        for (int x = -4; x <= 4; x++) {
            w.force(x, gt, gz, LOG);
        }
        for (int x = -1; x <= 1; x++) {
            w.force(x, gt - 1, gz, PLANK);
            w.force(x, gt - 2, gz, PLANK);
        }
        w.force(0, gt - 1, gz + 1, PAPER);
        // the targets on the ring of trees and on loose posts
        List<double[]> trees = new ArrayList<>();
        for (int k = 0; k < 18; k++) {
            double a = k / 18.0 * Math.PI * 2 + r.range(-0.1, 0.1), d = r.range(31, 38);
            int x = round(cos(a) * d), z = round(sin(a) * d);
            if (Math.abs(x + 0.5 - sx.applyAsDouble(z)) < 5) {
                continue;
            }
            if (z > 24 && Math.abs(x) < 9) {
                continue;
            }
            trees.add(new double[] {x, z, a});
            leafTree(w, x, ground.at(x, z) + 1, z, seed * 97 + k, true);
        }
        int targets = 0;
        for (double[] t : trees) {
            if (targets >= 8 || r.next() < 0.35) {
                continue;
            }
            int tx0 = (int) t[0], tz0 = (int) t[1];
            double fx = -cos(t[2]), fz = -sin(t[2]);
            double ax = Math.abs(fx) > Math.abs(fz) ? Math.signum(fx) : 0, az = ax != 0 ? 0 : Math.signum(fz);
            int tx = tx0 + (ax > 0 ? 2 : ax < 0 ? -1 : 0), tz = tz0 + (az > 0 ? 2 : az < 0 ? -1 : 0);
            int ty = ground.at(tx0, tz0) + r.nextInt(2, 5);
            w.force(tx, ty, tz, TARGET);
            res.mark("target", tx, ty, tz);
            targets++;
        }
        for (int k = 0; k < 4; k++) {
            double a = r.range(0, Math.PI * 2), d = r.range(12, 20);
            int x = round(cos(a) * d), z = round(sin(a) * d);
            if (Math.abs(x + 0.5 - sx.applyAsDouble(z)) < 4 || inR(x, z, mx, mz, 4) || (z > -2 && Math.abs(x) < 7)) {
                continue;
            }
            w.force(x, Y + 1, z, LOG);
            w.force(x, Y + 2, z, TARGET);
            res.mark("target", x, Y + 2, z);
            targets++;
        }
        // the loot (StructureLoot): behind the middle stump, the lunch Kakashi left
        w.force(0, Y + 1, -7, CHEST);
        res.mark("loot", 0, Y + 1, -7);
        res.mark("center", 0, Y + 1, -2);
        return res;
    }

    // ================================================================ 2. MORTE: the Chunin Exam tower
    static Result morte(int seed, World W) {
        Vox w = new Vox();
        Result res = new Result(w);
        Rng r = new Rng(seed * 17 + 3);
        int R = 66, Y = W.floor();
        res.radius = R;
        Ground ground = terrain(res, W, 30, Y, (x, z, h) -> {
            double n = valueNoise(x, z, seed + 9, 7);
            return n > 0.62 ? PODZOL : n < 0.25 ? COARSE : DARK_GRASS;
        }, (x, z) -> DIRT);
        interface Wall {
            int at(int x, int y, int z, double d, int yy);
        }
        Wall wallAt = (x, y, z, rr, yy) -> {
            if ((yy % 7) == 0) {
                return BRICK;
            }
            double n = valueNoise3(x, y, z, seed + 4, 4);
            return n > 0.72 ? MOSSY : n > 0.62 ? STONE : CALCITE;
        };
        interface Cyl {
            void at(double cx, double cz, int y0, int y1, double rad, double shell, Wall f);
        }
        Cyl cyl = (cx, cz, y0, y1, rad, shell, f) -> {
            for (int x = floor(cx - rad - 1); x <= ceil(cx + rad); x++) {
                for (int z = floor(cz - rad - 1); z <= ceil(cz + rad); z++) {
                    double d = hyp(x + 0.5 - cx, z + 0.5 - cz);
                    if (d > rad) {
                        continue;
                    }
                    for (int y = y0; y <= y1; y++) {
                        if (shell != 0 && d < rad - shell && y > y0) {
                            w.force(x, y, z, AIR);
                            continue;
                        }
                        w.force(x, y, z, f.at(x, y, z, d, y - y0));
                    }
                }
            }
        };
        int DR = 17, DH = 13;
        cyl.at(0.5, 0.5, Y, Y + DH, DR, 1.4, (x, y, z, d, yy) -> yy == 0 ? (((x + z) & 1) != 0 ? SMOOTH : BRICK) : wallAt.at(x, y, z, d, yy));
        cyl.at(0.5, 0.5, Y + DH + 1, Y + DH + 1, DR + 1, 0, (x, y, z, d, yy) -> BRICK);
        cyl.at(0.5, 0.5, Y + DH + 2, Y + DH + 2, DR + 1, 0.9, (x, y, z, d, yy) -> ROOF_SLAB);
        for (int k = 0; k < 28; k++) {
            double a = k / 28.0 * Math.PI * 2;
            int x = floor(0.5 + cos(a) * (DR - 0.6)), z = floor(0.5 + sin(a) * (DR - 0.6));
            for (int yy : new int[] {4, 5, 9, 10}) {
                w.force(x, Y + yy, z, WIN);
            }
        }
        for (int x = -2; x <= 3; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z = DR - 3; z <= DR + 1; z++) {
                    if (!(y == 6 && (x == -2 || x == 3))) {
                        w.force(x, Y + y, z, AIR);
                    }
                }
            }
        }
        for (int x = -3; x <= 4; x++) {
            w.force(x, Y + 7, DR - 1, RED);
        }
        double TR = 8.5;
        int TH = 42;
        cyl.at(0.5, 0.5, Y + DH + 1, Y + TH, TR, 1.3, (x, y, z, d, yy) -> wallAt.at(x, y, z, d, yy + DH));
        for (int k = 0; k < 16; k++) {
            double a = k / 16.0 * Math.PI * 2 + 0.1;
            int x = floor(0.5 + cos(a) * (TR - 0.5)), z = floor(0.5 + sin(a) * (TR - 0.5));
            for (int yy = DH + 5; yy < TH - 2; yy += 6) {
                w.force(x, Y + yy, z, WIN);
                w.force(x, Y + yy + 1, z, WIN);
            }
        }
        cyl.at(0.5, 0.5, Y + TH + 1, Y + TH + 2, TR + 1.5, 0, (x, y, z, d, yy) -> yy == 0 ? BRICK : RED);
        for (int i = 0; i <= 10; i++) {
            double rad = (TR + 2.2) * (1 - i / 10.5);
            cyl.at(0.5, 0.5, Y + TH + 3 + i, Y + TH + 3 + i, rad, rad > 2.2 ? 1.6 : 0, (x, y, z, d, yy) -> ROOF);
        }
        for (int y = Y + TH + 13; y <= Y + TH + 17; y++) {
            w.force(0, y, 0, y == Y + TH + 17 ? GOLD : METAL_D);
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2, cx = 0.5 + cos(a) * (DR - 1), cz = 0.5 + sin(a) * (DR - 1);
            cyl.at(cx, cz, Y, Y + 22, 3.7, 1, (x, y, z, d, yy) -> wallAt.at(x, y, z, d, yy));
            cyl.at(cx, cz, Y + 23, Y + 23, 4.6, 0, (x, y, z, d, yy) -> RED);
            for (int i = 0; i <= 5; i++) {
                cyl.at(cx, cz, Y + 24 + i, Y + 24 + i, 4.6 * (1 - i / 5.6), 0, (x, y, z, d, yy) -> ROOF);
            }
            for (int yy : new int[] {8, 14, 19}) {
                int x = floor(cx + cos(a) * 3.2), z = floor(cz + sin(a) * 3.2);
                w.force(x, Y + yy, z, WIN);
            }
        }
        List<int[]> vines = new ArrayList<>();
        for (int k = 0; k < 60; k++) {
            double a = r.range(0, Math.PI * 2);
            int x = floor(0.5 + cos(a) * (DR + 0.4)), z = floor(0.5 + sin(a) * (DR + 0.4));
            if (w.get(x, Y + 1, z) != 0 || z > DR - 4) {
                continue;
            }
            int len = r.nextInt(3, 11);
            for (int y = Y + 1; y < Y + 1 + len; y++) {
                vines.add(new int[] {x, y, z});
            }
        }
        for (int[] v : vines) {
            w.set(v[0], v[1], v[2], VINE);
        }
        // the hall: the wall of Heaven and Earth, the altar of the two scrolls, the sealed chamber behind
        for (int x = -5; x <= 6; x++) {
            for (int y = 1; y <= 8; y++) {
                w.force(x, Y + y, -DR + 3, RED);
                res.mark("wall", x, Y + y, -DR + 3);
            }
        }
        String[] glyph = {"..#..#..", ".######.", "...##...", "..#..#..", ".#....#."};
        for (int gy = 0; gy < glyph.length; gy++) {
            for (int gx = 0; gx < glyph[gy].length(); gx++) {
                if (glyph[gy].charAt(gx) == '#') {
                    w.force(-3 + gx, Y + 7 - gy, -DR + 4, GOLD);
                    res.mark("glyph", -3 + gx, Y + 7 - gy, -DR + 4);
                }
            }
        }
        box(w, -3, Y + 1, -DR + 1, 4, Y + 4, -DR + 2, AIR);
        w.force(0, Y + 1, -DR + 2, CHEST);
        w.force(1, Y + 1, -DR + 2, CHEST);
        res.mark("chest", 0, Y + 1, -DR + 2);
        res.mark("chest", 1, Y + 1, -DR + 2);
        for (int ax : new int[] {-2, 3}) {
            w.force(ax, Y + 1, -2, DARK);
            w.force(ax, Y + 2, -2, PAPER);
            res.mark("altar", ax, Y + 2, -2);
        }
        box(w, -3, Y, -4, 4, Y, 0, DARK);
        for (int k = 0; k < 8; k++) {
            double a = k / 8.0 * Math.PI * 2 + 0.2;
            int x = floor(0.5 + cos(a) * 11), z = floor(0.5 + sin(a) * 11);
            if (z < -DR + 6) {
                continue;
            }
            for (int y = 1; y <= DH; y++) {
                w.force(x, Y + y, z, BRICK);
            }
        }
        // the fence of Area 44
        int FR = 34;
        double[] broken = {r.range(0, 6.28), r.range(0, 6.28), r.range(0, 6.28)};
        int N = round(FR * Math.PI * 2);
        for (int i = 0; i < N; i++) {
            double a = (double) i / N * Math.PI * 2;
            int x = round(cos(a) * FR), z = round(sin(a) * FR);
            if (Math.abs(a - Math.PI / 2) < 0.13) {
                continue;
            }
            int g = ground.at(x, z);
            boolean post = i % 7 == 0;
            int h = post ? 5 : 4;
            int lean = 0;
            for (double b : broken) {
                double da = Math.abs(((a - b + Math.PI * 3) % (Math.PI * 2)) - Math.PI);
                if (da < 0.06) {
                    h = 0;
                } else if (da < 0.1) {
                    lean = 1;
                }
            }
            for (int y = 1; y <= h; y++) {
                w.force(x + (lean != 0 && y > 2 ? round(cos(a)) : 0), g + y, z + (lean != 0 && y > 2 ? round(sin(a)) : 0), post ? STONE : BARS);
            }
        }
        for (int gx : new int[] {-4, 5}) {
            int g = ground.at(gx, FR);
            for (int y = 1; y <= 8; y++) {
                for (int dz = 0; dz <= 1; dz++) {
                    w.force(gx, g + y, FR - dz, BRICK);
                }
            }
            w.force(gx, g + 9, FR, RED);
            w.force(gx, g + 9, FR - 1, RED);
        }
        for (int x = -5; x <= 6; x++) {
            w.force(x, ground.at(0, FR) + 8, FR, RED);
        }
        for (int x = -1; x <= 2; x++) {
            w.force(x, ground.at(0, FR) + 7, FR, PLANK);
            w.force(x, ground.at(0, FR) + 6, FR, PLANK);
        }
        w.force(0, ground.at(0, FR) + 7, FR + 1, PAPER);
        w.force(1, ground.at(0, FR) + 7, FR + 1, PAPER);
        for (int z = DR; z <= FR + 6; z++) {
            for (int x = -1; x <= 2; x++) {
                if (hash3(x, 4, z, seed) < 0.85) {
                    w.force(x, ground.at(x, z), z, GRAVEL);
                }
            }
        }
        // the giant trees outside the fence are the biome's; their draws stay so the rest matches the preview
        for (int k = 0; k < 22; k++) {
            r.range(-0.1, 0.1);
            r.range(42, 60);
        }
        for (int k = 0; k < 6; k++) {
            double a = r.range(0, Math.PI * 2), d = r.range(23, 29);
            int x = round(cos(a) * d), z = round(sin(a) * d);
            if (z > 18 && Math.abs(x) < 7) {
                continue;
            }
            darkTree(w, x, ground.at(x, z) + 1, z, seed * 43 + k);
        }
        res.mark("center", 0, Y + 1, -1);
        return res;
    }

    // ================================================================ 2b. MORTE: the Snake's Den (cobra_gigante)
    static Result covil(int seed, World W) {
        Vox w = new Vox();
        Result res = new Result(w);
        Rng r = new Rng(seed * 23 + 11);
        int R = 54, Y = W.floor();
        res.radius = R;
        Ground ground = terrain(res, W, 18, Y, (x, z, h) -> valueNoise(x, z, seed + 9, 6) > 0.55 ? PODZOL : DARK_GRASS, (x, z) -> DIRT);
        int L = 30;
        double RO = 7.5, RI = 5.4;
        int cy = Y + 4;
        double yawA = r.range(-0.35, 0.35), ca = cos(yawA), sa = sin(yawA);
        double nx = -L * ca - 2 * ca, nz = -L * sa - 2 * sa;
        for (int x = floor(nx - 11); x <= nx + 11; x++) {
            for (int z = floor(nz - 11); z <= nz + 11; z++) {
                // in the world the nest is under the ground even where the ground is low (a river bank, PC test of
                // 2026-11-12): the rock reaches the floor at least
                for (int y = Y - 14; y < Math.max(ground.at(x, z) - 2, Y - 1); y++) {
                    w.set(x, y, z, STONE);
                }
            }
        }
        ball(w, nx, Y - 6, nz, 9, 6, 9, AIR, false);
        int nestFloor = Y - 11;
        for (int x = floor(nx - 10); x <= nx + 10; x++) {
            for (int z = floor(nz - 10); z <= nz + 10; z++) {
                Integer fl = null;
                for (int y = Y - 12; y <= Y; y++) {
                    if (w.get(x, y, z) == 0 && w.get(x, y - 1, z) != 0) {
                        fl = y;
                        break;
                    }
                }
                if (fl != null && inR(x, z, nx, nz, 8)) {
                    w.force(x, fl, z, hash3(x, 5, z, seed) < 0.45 ? SKIN : PODZOL);
                    if (hash3(x, 6, z, seed) < 0.06) {
                        w.force(x, fl + 1, z, BONE);
                    }
                    if (x == floor(nx) && z == floor(nz)) {
                        nestFloor = fl;
                    }
                }
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
        for (int x = floor(nx - 10); x <= nx + 10; x++) {
            for (int z = floor(nz - 10); z <= nz + 10; z++) {
                for (int y = Y - 13; y <= Y; y++) {
                    if (w.get(x, y, z) == 0 && y < Y - 1) {
                        for (int[] dd : dirs) {
                            int t = w.get(x + dd[0], y + dd[1], z + dd[2]);
                            if (t == DIRT || t == STONE) {
                                w.force(x + dd[0], y + dd[1], z + dd[2], hash3(x + dd[0], y, z + dd[2], seed) < 0.5 ? MUD : STONE);
                            }
                        }
                    }
                }
            }
        }
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double u = x * ca + z * sa, v = -x * sa + z * ca;
                if (u < -L - 1 || u > L + 2) {
                    continue;
                }
                double endU = L - valueNoise(v * 3, 0, seed + 7, 2) * 4;
                if (u > endU) {
                    continue;
                }
                for (int y = Y - 4; y <= Y + 11; y++) {
                    double dv = v, dy = y + 0.5 - cy, d = hyp(dv, dy);
                    double bump = (valueNoise3(u, y, v, seed + 13, 3) - 0.5) * 1.2;
                    if (d > RO + bump) {
                        continue;
                    }
                    if (d < RI + bump * 0.5 && u > -L + 3) {
                        if (y >= Y) {
                            w.force(x, y, z, AIR);
                        }
                        continue;
                    }
                    boolean top = dy > RO - 2.2;
                    w.force(x, y, z, top && valueNoise(u, v, seed + 21, 4) > 0.45 ? MOSS : d > RO - 1.2 ? DLOG : DPLANK);
                }
                if (u > -L + 3 && u < endU - 1 && Math.abs(v) < RI - 1) {
                    int fy = Y;
                    w.force(x, fy, z, PODZOL);
                    boolean coil = Math.abs(v - sin(u / 3.2) * 2.2) < 0.9;
                    if (coil && u > -8) {
                        w.force(x, fy + 1, z, SKIN);
                    } else if (hash3(x, 7, z, seed) < 0.04) {
                        w.force(x, fy + 1, z, BONE);
                    }
                }
            }
        }
        // the trunk's open end with a clear way in, even where it lies against a hill (PC test of 2026-11-12: the mouth
        // was full of earth)
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double u = x * ca + z * sa, v = -x * sa + z * ca;
                if (u < L - 6 || u > L + 12 || Math.abs(v) > RI - 0.5) {
                    continue;
                }
                double endU = L - valueNoise(v * 3, 0, seed + 7, 2) * 4;
                if (u < endU - 0.5) {
                    continue;
                }
                double roof = cy + Math.sqrt(Math.max(0, RI * RI - v * v)) - (u - endU) * 0.4;
                for (int y = Y + 1; y <= Math.max(roof, Y + 3); y++) {
                    w.force(x, y, z, AIR);
                }
                w.force(x, Y, z, PODZOL);
            }
        }
        beam(w, (-L + 5) * ca, Y + 1, (-L + 5) * sa, nx, Y - 6, nz, 2.4, AIR, false);
        for (int k = 0; k < 11; k++) {
            double a = k / 11.0 * Math.PI * 2, ry = sin(a) * 6, rz = cos(a) * 6;
            double bx = -L * ca, bz = -L * sa;
            double ex = bx - ca * r.range(3, 7) - sa * rz * 1.4, ez = bz - sa * r.range(3, 7) + ca * rz * 1.4, ey = cy + ry * 1.6 + r.range(-1, 2);
            beam(w, bx - sa * rz * 0.6, cy + ry * 0.6, bz + ca * rz * 0.6, ex, ey, ez, q -> 1.5 - q * 1.0, DLOG, true);
            if (ey > Y + 3) {
                beam(w, ex, ey, ez, ex - ca * 2, ey + r.range(1, 3), ez - sa * 2, 0.6, DLOG, true);
            }
        }
        double ex = (L + 3) * ca, ez = (L + 3) * sa;
        for (int k = 0; k < 7; k++) {
            int x = round(ex + r.range(-4, 6) * ca - r.range(-5, 5) * sa), z = round(ez + r.range(-4, 6) * sa + r.range(-5, 5) * ca);
            w.force(x, ground.at(x, z) + 1, z, BONE);
        }
        for (int k = 0; k < 12; k++) {
            r.range(-0.2, 0.2);
            r.range(32, 50);
        }
        int tx = round(cos(1.1) * 34), tz = round(sin(1.1) * 34);
        darkTree(w, tx, ground.at(tx, tz) + 1, tz, seed * 61);
        for (int i = 0; i < 70; i++) {
            double a = i * 0.35, y = ground.at(tx, tz) + 2 + i * 0.28;
            w.force(round(tx + cos(a) * 2.6), round(y), round(tz + sin(a) * 2.6), SKIN);
        }
        res.mark("nest", floor(nx), nestFloor, floor(nz));
        res.mark("mouth", round(ex), Y + 1, round(ez));
        res.mark("center", 0, Y + 1, 0);
        return res;
    }

    // ================================================================ 3. DESERTO: the Sealed Hideout / the Puppeteer's Workshop
    static Result deserto(int seed, World W) {
        Vox w = new Vox();
        Result res = new Result(w);
        Rng r = new Rng(seed * 29 + 5);
        int R = 70, Y = W.floor();
        res.radius = R;
        Ground ground = terrain(res, W, 0, Y, (x, z, h) -> SAND, (x, z) -> SAND);
        int MZ = -24, MRX = 32, MRZ = 24, MH = 26;
        int faceZ = MZ + round(MRZ * 0.62);
        for (int x = -MRX - 4; x <= MRX + 4; x++) {
            for (int z = MZ - MRZ - 4; z <= faceZ + 3; z++) {
                double e = sq((x + 0.5) / MRX) + sq((z + 0.5 - MZ) / MRZ);
                double n = valueNoise(x, z, seed + 31, 9);
                if (e > 1 + (n - 0.5) * 0.4) {
                    continue;
                }
                double cliff = faceZ - (valueNoise(x, 0, seed + 37, 5) - 0.5) * 3;
                if (z + 0.5 > cliff) {
                    continue;
                }
                int g = ground.at(x, z);
                double fall = e < 0.62 ? 1 : smooth(clamp((1 - e) / 0.38, 0, 1));
                double top = Y + MH * (Math.floor(fall * 4) / 4 * 0.5 + fall * 0.5) - n * 4 + (valueNoise(x, z, seed + 33, 4) - 0.5) * 2;
                for (int y = g - 2; y <= top; y++) {
                    int band = floor((y + valueNoise(x, z, seed + 35, 16) * 3) / 2.6) % 4;
                    w.force(x, y, z, band == 0 ? ROCK_L : band == 2 ? ROCK_D : band == 3 ? SANDSTONE : ROCK_R);
                }
                if (top > g + 2 && hash3(x, 8, z, seed) < 0.3) {
                    w.force(x, floor(top) + 1, z, SAND);
                }
            }
        }
        int RCZ = faceZ - 16, RF = Y + 1;
        for (int x = -13; x <= 13; x++) {
            for (int z = RCZ - 9; z <= RCZ + 9; z++) {
                for (int y = RF; y <= RF + 8; y++) {
                    double e = sq(x / 12.5) + sq((z - RCZ) / 8.5) + sq((y - RF) / 8.0) * (y > RF + 3 ? 1 : 0);
                    if (e < 1 + (valueNoise3(x, y, z, seed + 3, 3) - 0.5) * 0.3) {
                        w.force(x, y, z, AIR);
                    }
                }
            }
        }
        for (int x = -12; x <= 12; x++) {
            for (int z = RCZ - 8; z <= RCZ + 8; z++) {
                if (w.get(x, RF, z) == 0 && w.get(x, RF - 1, z) != 0) {
                    w.force(x, RF - 1, z, PLANK);
                }
            }
        }
        for (int z = RCZ + 6; z <= faceZ + 4; z++) {
            for (int x = -2; x <= 2; x++) {
                for (int y = RF; y <= RF + 4; y++) {
                    if (!(y == RF + 4 && Math.abs(x) == 2)) {
                        w.force(x, y, z, AIR);
                    }
                }
            }
        }
        for (int z = RCZ + 6; z <= faceZ + 2; z++) {
            for (int x = -2; x <= 2; x++) {
                w.force(x, RF - 1, z, SANDSTONE);
            }
        }
        for (int z = faceZ - 4; z <= faceZ + 4; z++) {
            for (int x = -2; x <= 2; x++) {
                if (hash3(x, 9, z, seed) < 0.6) {
                    w.set(x, RF, z, SAND);
                }
            }
        }
        // the boulder over the mouth, with the "forbidden" seal
        double bz = faceZ + 3.5;
        ball(w, 0.5, RF + 3.4, bz, 4.8, 4.6, 3.4, ROCK_D, false);
        for (int x = -4; x <= 5; x++) {
            for (int y = RF - 1; y <= RF + 8; y++) {
                for (int z = floor(bz - 4); z <= bz + 4; z++) {
                    if (w.get(x, y, z) == ROCK_D && valueNoise3(x, y, z, seed + 41, 2.5) > 0.55) {
                        w.force(x, y, z, STONE);
                    }
                }
            }
        }
        for (int x = -6; x <= 7; x++) {
            for (int y = RF - 2; y <= RF + 9; y++) {
                for (int z = floor(bz - 5); z <= bz + 5; z++) {
                    int t = w.get(x, y, z);
                    if ((t == ROCK_D || t == STONE) && sq((x + 0.5 - 0.5) / 4.8) + sq((y + 0.5 - RF - 3.4) / 4.6) + sq((z + 0.5 - bz) / 3.4) <= 1) {
                        res.mark("boulder", x, y, z);
                    }
                }
            }
        }
        for (int y = RF + 2; y <= RF + 5; y++) {
            int z2 = ceil(bz);
            while (w.get(0, y, z2) != 0) {
                z2++;
            }
            w.force(0, y, z2, PAPER);
            res.mark("boulder_seal", 0, y, z2);
        }
        // the five seals, each on a rock around
        for (int k = 0; k < 5; k++) {
            r.range(-0.2, 0.2);
            double ang = -Math.PI * 0.05 + k / 4.0 * Math.PI * 1.1;
            double d = r.range(30, 46);
            int x = round(sin(ang - Math.PI / 2 + Math.PI / 2) * 0 + cos(ang) * d), z = round(faceZ + 6 + sin(ang) * d);
            int g = ground.at(x, z), h = r.nextInt(4, 7);
            double rr = r.range(1.6, 2.6);
            for (int y = g - 1; y <= g + h; y++) {
                double q = rr * (1 - (double) (y - g) / (h + 2) * 0.55);
                for (int a2 = -3; a2 <= 3; a2++) {
                    for (int b2 = -3; b2 <= 3; b2++) {
                        if (a2 * a2 + b2 * b2 <= q * q + 0.3) {
                            w.force(x + a2, y, z + b2, (((y - g) >> 1) & 1) != 0 ? ROCK_R : ROCK_L);
                        }
                    }
                }
            }
            double dx = -cos(ang), dz = -sin(ang);
            boolean alongX = Math.abs(dx) > Math.abs(dz);
            int sxp = alongX ? x + (int) Math.signum(dx) * ceil(rr) : x, szp = alongX ? z : z + (int) Math.signum(dz) * ceil(rr);
            int px = sxp, pz = szp;
            while (w.get(px, g + 2, pz) != 0) {
                px += alongX ? (int) Math.signum(dx) : 0;
                pz += alongX ? 0 : (int) Math.signum(dz);
            }
            w.force(px, g + 2, pz, PAPER);
            res.mark("seal", px, g + 2, pz);
        }
        // the workshop
        Ground ceilAt = (x, z) -> {
            for (int y = RF + 1; y < RF + 14; y++) {
                if (w.get(x, y, z) != 0) {
                    return y;
                }
            }
            return RF + 9;
        };
        int[][] hung = {{-8, RCZ - 3}, {-5, RCZ - 6}, {-1, RCZ - 6}, {4, RCZ - 6}, {8, RCZ - 3}, {-9, RCZ + 2}, {9, RCZ + 2}, {6, RCZ + 4}};
        for (int[] hh : hung) {
            int x = hh[0], z = hh[1];
            int c = ceilAt.at(x, z), feet = RF + 1 + r.nextInt(0, 2);
            for (int y = feet + 4; y < c; y++) {
                w.force(x, y, z, THREAD);
            }
            w.force(x, feet, z, PUPPET);
            w.force(x, feet + 1, z, CLOTH);
            w.force(x, feet + 2, z, CLOTH);
            w.force(x, feet + 3, z, PUPPET);
            int side = (x + z) & 1;
            w.force(x + (side != 0 ? 1 : 0), feet + 2, z + (side != 0 ? 0 : 1), PUPPET);
            w.force(x - (side != 0 ? 1 : 0), feet + 2, z - (side != 0 ? 0 : 1), PUPPET);
            res.mark("puppet", x, feet, z);
        }
        for (int x = -6; x <= -3; x++) {
            w.force(x, RF, RCZ + 1, PLANK_SLAB);
            w.force(x, RF, RCZ + 2, PLANK_SLAB);
        }
        for (int x = 3; x <= 5; x++) {
            w.force(x, RF, RCZ - 1, PLANK_SLAB);
        }
        w.force(-5, RF + 1, RCZ + 1, PUPPET);
        w.force(4, RF + 1, RCZ - 1, BLADE);
        for (int x = -6; x <= 6; x++) {
            for (int y = RF; y <= RF + 2; y++) {
                int z = RCZ - 7;
                while (w.get(x, y, z) == 0 && z > RCZ - 12) {
                    z--;
                }
                if (w.get(x, y, z + 1) == 0) {
                    w.force(x, y, z + 1, SHELF);
                }
            }
        }
        ball(w, 0.5, RF + 1.5, RCZ + 0.5, 2.2, 1.8, 2.6, CLOTH, false);
        w.force(0, RF + 3, RCZ + 2, PUPPET);
        for (int i = 0; i < 9; i++) {
            double a = i / 8.0 * Math.PI;
            w.force(round(0.5 + sin(a) * 0.5), round(RF + 2 + sin(a) * 3.5), round(RCZ - 2 - i * 0.5 - (1 - cos(a)) * 0.8), i == 8 ? BLADE : METAL_D);
        }
        for (int[] ll : new int[][] {{-10, RCZ}, {10, RCZ}, {0, RCZ - 7}}) {
            int c = ceilAt.at(ll[0], ll[1]);
            w.force(ll[0], c - 1, ll[1], LANTERN);
        }
        w.force(-3, RF, RCZ - 5, CHEST);
        res.mark("chest", -3, RF, RCZ - 5);
        for (int k = 0; k < 14; k++) {
            int x = r.nextInt(-R + 4, R - 4), z = r.nextInt(faceZ + 8, R - 4);
            if (w.get(x, ground.at(x, z) + 1, z) != 0) {
                continue;
            }
            w.force(x, ground.at(x, z) + 1, z, k % 3 != 0 ? FLOWER : BONE);
        }
        res.mark("hall", 0, RF, RCZ);
        res.mark("center", 0, ground.at(0, faceZ + 12) + 1, faceZ + 12);
        return res;
    }

    // ================================================================ 4. NEVOA: the Unfinished Bridge and the Swordsman's Grave
    static Result nevoa(int seed, World W) {
        Vox w = new Vox();
        Result res = new Result(w);
        int R = 88, SL = W.sea();
        res.radius = R;
        int shoreX = -44;
        interface Isle {
            double at(int x, int z);
        }
        Isle isleAt = (x, z) -> Math.min(1, Math.max(0, 1 - hyp(x - 54, z - 24) / 15) * 2.2) * 14 + Math.max(0, 1 - hyp(x - 54, z - 24) / 6) * 2;
        Ground gw = terrain(res, W, 0, SL, (x, z, h) -> h >= SL + 2 ? GRASS : h >= SL - 1 ? SAND : GRAVEL, (x, z) -> SAND);
        Ground ground = (x, z) -> hyp(x - 54, z - 24) < 15 ? Math.max(gw.at(x, z), round(SL - 7 + isleAt.at(x, z))) : gw.at(x, z);
        for (int x = 36; x <= 72; x++) {
            for (int z = 6; z <= 42; z++) {
                int g = ground.at(x, z);
                double d = hyp(x - 54, z - 24);
                if (d > 15) {
                    continue;
                }
                for (int y = Math.min(SL - 6, gw.at(x, z)); y <= g; y++) {
                    double n = valueNoise3(x, y, z, seed + 7, 3.5);
                    if (y < g - 1 || d > 9) {
                        w.force(x, y, z, n > 0.6 ? MOSSY : n < 0.3 ? COBBLE : STONE);
                    } else {
                        w.force(x, y, z, y == g ? (g >= SL + 2 ? GRASS : SAND) : DIRT);
                    }
                }
                if (d < 11 && g > SL + 2 && hash3(x, 1, z, seed) < 0.06) {
                    w.force(x, g + 1, z, FLOWER);
                }
            }
        }
        pine(w, 49, ground.at(49, 19) + 1, 19, seed + 1);
        pine(w, 59, ground.at(59, 30) + 1, 30, seed + 2);
        pine(w, 61, ground.at(61, 18) + 1, 18, seed + 3);
        int DY = SL + 6, X0 = shoreX - 12, XEND = 30;
        for (int x = X0; x <= XEND; x++) {
            boolean unfinished = x > XEND - 14;
            for (int z = -3; z <= 3; z++) {
                boolean hole = unfinished && hash3(x, 2, z, seed) < (x - (XEND - 14)) / 16.0;
                if (!hole) {
                    w.force(x, DY, z, unfinished ? PLANK : (z == 0 || Math.abs(z) == 3) ? BRICK : SMOOTH);
                }
                if (!unfinished) {
                    w.force(x, DY - 1, z, BRICK);
                }
            }
            if (!unfinished) {
                for (int z : new int[] {-3, 3}) {
                    w.force(x, DY + 1, z, x % 6 == 0 ? BRICK : FENCE);
                    if (x % 6 == 0) {
                        w.force(x, DY + 2, z, LANTERN);
                    }
                }
            }
            int g = ground.at(x, 0);
            if (g >= SL) {
                for (int z = -3; z <= 3; z++) {
                    for (int y = g + 1; y < DY; y++) {
                        w.force(x, y, z, BRICK);
                    }
                }
            }
        }
        if (ground.at(X0, 0) < SL) {
            for (int x = X0 - 8; x < X0; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (int y = Math.min(ground.at(x, z), SL - 6); y <= DY; y++) {
                        w.force(x, y, z, y == DY ? SMOOTH : (valueNoise3(x, y, z, seed, 3) > 0.62 ? MOSSY : BRICK));
                    }
                }
            }
        }
        for (int px = X0 + 14; px <= XEND; px += 12) {
            boolean last = px > XEND - 14;
            for (int x = px - 1; x <= px + 1; x++) {
                for (int z = -5; z <= 5; z++) {
                    if (Math.abs(z) == 5 && x != px) {
                        continue;
                    }
                    for (int y = ground.at(x, z) - 1; y < DY - (last ? 0 : 1); y++) {
                        w.force(x, y, z, last ? (Math.abs(z) % 2 != 0 ? LOG : FENCE) : (valueNoise3(x, y, z, seed, 3) > 0.62 ? MOSSY : BRICK));
                    }
                }
            }
        }
        for (int x = XEND - 12; x <= XEND + 2; x += 3) {
            for (int z : new int[] {-4, 4}) {
                for (int y = DY + 1; y <= DY + 6; y++) {
                    w.force(x, y, z, y == DY + 6 ? PLANK : LOG);
                }
            }
        }
        for (int x = XEND - 12; x <= XEND + 2; x++) {
            for (int z : new int[] {-4, 4}) {
                w.force(x, DY + 6, z, PLANK);
            }
        }
        for (int x = XEND - 12; x <= XEND + 2; x++) {
            for (int z : new int[] {-4, 4}) {
                if (x % 3 != 0) {
                    w.force(x, DY + 3, z, FENCE);
                }
            }
        }
        for (int x = XEND + 1; x <= XEND + 4; x++) {
            w.force(x, DY - 1, 0, LOG);
        }
        int gx = XEND - 6;
        for (int y = DY + 1; y <= DY + 15; y++) {
            w.force(gx, y, -2, LOG);
        }
        for (int x = gx - 3; x <= gx + 11; x++) {
            w.force(x, DY + 15, -2, PLANK);
        }
        for (int i = 1; i <= 5; i++) {
            w.force(gx + i, DY + 15 - i, -2, FENCE);
        }
        for (int y = DY + 5; y < DY + 15; y++) {
            w.force(gx + 10, y, -2, ROPE);
        }
        box(w, gx + 9, DY + 3, -3, gx + 11, DY + 4, -1, BRICK);
        box(w, X0 + 30, DY + 1, 1, X0 + 32, DY + 2, 2, BRICK);
        for (int x = X0 + 36; x <= X0 + 40; x++) {
            w.force(x, DY + 1, -2, LOG);
            w.force(x, DY + 1, -1, LOG);
            w.force(x, DY + 2, -2, LOG);
        }
        w.force(X0 + 44, DY + 1, 2, BARREL);
        w.force(X0 + 45, DY + 1, 2, BARREL);
        w.force(X0 + 44, DY + 2, 2, BARREL);
        // the swordsman's grave
        int tx = 54, tz = 24, tg = ground.at(tx, tz);
        for (int x = tx - 2; x <= tx + 2; x++) {
            for (int z = tz - 1; z <= tz + 3; z++) {
                if (Math.abs(x - tx) + Math.abs(z - tz - 1) <= 3) {
                    w.force(x, tg + 1, z, COARSE);
                }
            }
        }
        w.force(tx, tg + 2, tz + 1, COARSE);
        for (int y = 0; y < 8; y++) {
            for (int x = -1; x <= 2; x++) {
                if (y == 6 && x == 0) {
                    continue;
                }
                if (y == 7 && x == -1) {
                    continue;
                }
                w.force(tx + x, tg + 1 + y, tz - 1, x == -1 ? METAL : BLADE);
                res.mark(x == -1 ? "spine" : "blade", tx + x, tg + 1 + y, tz - 1);
            }
        }
        for (int y = 8; y <= 10; y++) {
            w.force(tx + 2, tg + 1 + y, tz - 1, WRAP);
            res.mark("wrap", tx + 2, tg + 1 + y, tz - 1);
        }
        w.force(tx + 4, tg + 1, tz + 1, COARSE);
        w.force(tx + 4, tg + 1, tz + 2, COARSE);
        w.force(tx + 4, tg + 2, tz, COBBLE);
        w.force(tx + 4, tg + 3, tz, COBBLE);
        w.force(tx + 3, tg + 2, tz + 2, FLOWER);
        res.mark("grave", tx, tg + 2, tz + 2);
        res.mark("hunter", tx + 4, tg + 2, tz + 2);
        res.mark("bridge_end", XEND - 2, DY + 1, 0);
        res.mark("center", tx, tg + 2, tz + 2);
        return res;
    }

    // ================================================================ 5. CHUVA: the Angel's Tower and the Giant Pipes (salamandra_venenosa)
    static Result chuva(int seed, World W) {
        Vox w = new Vox();
        Result res = new Result(w);
        Rng r = new Rng(seed * 43 + 9);
        int R = 60, Y = W.floor();
        res.radius = R;
        Ground ground = terrain(res, W, 22, Y, (x, z, h) -> {
            double n = valueNoise(x, z, seed + 3, 5);
            return n > 0.72 ? WATER : n > 0.5 ? GRAVEL : MUD;
        }, (x, z) -> MUD);
        interface Metal {
            int at(int x, int y, int z);
        }
        Metal metal = (x, y, z) -> {
            double n = valueNoise3(x, y * 0.4, z, seed + 5, 3), streak = valueNoise(x * 3 + z * 3, y * 0.15, seed + 6, 2);
            return streak > 0.72 ? RUST : n > 0.66 ? METAL_D : METAL;
        };
        double TR = 5.5;
        int TH = 62;
        for (int y = Y; y <= Y + TH; y++) {
            double rad = TR + Math.max(0, (Y + 8 - y) / 8.0) * 2.2;
            for (int x = -9; x <= 9; x++) {
                for (int z = -9; z <= 9; z++) {
                    double d = hyp(x + 0.5, z + 0.5);
                    boolean ring = (y - Y) % 5 == 0;
                    if (d > rad + (ring ? 0.9 : 0)) {
                        continue;
                    }
                    if (d < rad - 1.3 && y > Y) {
                        w.force(x, y, z, AIR);
                        continue;
                    }
                    int t = ring && d > rad - 0.2 ? METAL_D : metal.at(x, y, z);
                    if (!ring && d > rad - 1 && hash3(x, y, z, seed) < 0.05) {
                        t = WIN_LIT;
                    }
                    w.force(x, y, z, t);
                }
            }
        }
        for (int y = Y + 1; y <= Y + TH; y++) {
            double a = y * 0.42;
            w.force(floor(0.5 + cos(a) * 3), y, floor(0.5 + sin(a) * 3), METAL_D);
            w.force(floor(0.5 + cos(a + 0.25) * 3.6), y, floor(0.5 + sin(a + 0.25) * 3.6), METAL_D);
        }
        for (double a : new double[] {0.6, 2.3, 3.9, 5.2}) {
            int px = floor(0.5 + cos(a) * (TR + 1.2)), pz = floor(0.5 + sin(a) * (TR + 1.2));
            for (int y = Y + 8; y <= Y + TH - 4; y++) {
                w.force(px, y, pz, PIPE);
            }
        }
        int HY = Y + TH + 8, HZ = 1;
        for (int x = -10; x <= 10; x++) {
            for (int y = -10; y <= 11; y++) {
                for (int z = -10; z <= 11; z++) {
                    double px = x + 0.5, py = y + 0.5, pz = z + 0.5 - HZ;
                    double d = sq(px / 8.2) + sq(py / 8.6) + sq(pz / 7.6);
                    double jaw = py < -2 && py > -9 && pz > -3 ? (sq(px / 6.8) + sq((py + 4) / 5) + sq((pz - 1.5) / 7.2)) : 9;
                    if (Math.min(d, jaw) > 1) {
                        continue;
                    }
                    boolean inner = sq(px / 6.6) + sq(py / 7) + sq(pz / 6) < 1;
                    if (inner && y > -9) {
                        w.force(x, HY + y, z, AIR);
                        continue;
                    }
                    w.force(x, HY + y, z, metal.at(x, HY + y, z));
                }
            }
        }
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= -1; y++) {
                for (int z = 3; z <= 10; z++) {
                    if (sq(x / 5.6) + sq((y + 3) / 2.6) < 1) {
                        w.force(x, HY + y, z, AIR);
                    }
                }
            }
        }
        for (int x = -4; x <= 4; x += 2) {
            w.force(x, HY - 1, 7, SPIKE);
            w.force(x + 1, HY - 5, 7, SPIKE);
        }
        for (int s : new int[] {-1, 1}) {
            for (int i = 0; i < 4; i++) {
                int x = s * (2 + i), y = 2 + floor(i * 0.6);
                int z = 10;
                while (w.get(x, HY + y, z) == 0 && z > 0) {
                    z--;
                }
                w.force(x, HY + y, z, WIN_LIT);
                w.force(x, HY + y + 1, z, i < 2 ? WIN_LIT : w.get(x, HY + y + 1, z));
            }
        }
        for (int i = 0; i <= 12; i++) {
            int z = 5 + i, y = HY - 5 - floor(sq(i / 12.0) * 5), hw = i < 10 ? 2 : 1;
            for (int x = -hw; x <= hw; x++) {
                w.force(x, y, z, RED);
            }
            if (i == 12) {
                for (int x = -1; x <= 1; x++) {
                    w.force(x, y + 1, z, RED);
                }
            }
        }
        int ly = HY - 5 - floor(sq(10 / 12.0) * 5);
        w.force(0, ly + 1, 15, PAPER);
        w.force(-1, ly + 1, 15, FLOWER);
        w.force(1, ly + 1, 15, FLOWER);
        w.force(0, ly + 1, 14, FLOWER);
        res.mark("flower", 0, ly + 1, 15);
        for (int s : new int[] {-1, 1}) {
            beam(w, s * 5, HY + 6, HZ, s * 9, HY + 15, HZ - 3, k -> 1.6 - k * 1.2, METAL_D, false);
        }
        for (int[] an : new int[][] {{0, -2, 12}, {-3, 2, 8}, {3, 2, 9}}) {
            for (int y = HY + 8; y < HY + 8 + an[2]; y++) {
                w.force(an[0], y, an[1], SPIKE);
            }
        }
        int PX = 22, PZ = -14, PR = 10;
        for (int x = -PR - 2; x <= PR + 2; x++) {
            for (int z = -PR - 2; z <= PR + 2; z++) {
                double d = hyp(x + 0.5, z + 0.5);
                if (d > PR + 1.5) {
                    continue;
                }
                for (int y = Y - 14; y <= Y + 1; y++) {
                    if (d < PR) {
                        w.force(PX + x, y, PZ + z, y <= Y - 6 ? WATER : AIR);
                    } else {
                        w.force(PX + x, y, PZ + z, y == Y + 1 ? BARS : STONE);
                    }
                }
                if (d < PR) {
                    w.force(PX + x, Y - 15, PZ + z, MUD);
                }
            }
        }
        for (int k = 0; k < 3; k++) {
            double a = -0.9 + k * 0.45, sx = cos(a) * (TR + 2.5), sz = sin(a) * (TR + 2.5);
            double mx = PX + cos(a + 2.4) * 6, mz = PZ + sin(a + 2.4) * 6;
            double[][] pts = {{sx, Y + 3 + k * 3, sz}, {sx + (mx - sx) * 0.55, Y + 3 + k * 3, sz + (mz - sz) * 0.55}, {mx, Y + 2 + k, mz},
                    {mx + (PX - mx) * 0.3, Y - 8, mz + (PZ - mz) * 0.3}};
            for (int i = 0; i < pts.length - 1; i++) {
                beam(w, pts[i][0], pts[i][1], pts[i][2], pts[i + 1][0], pts[i + 1][1], pts[i + 1][2], 2.6, RUST, false);
            }
            for (int i = 0; i < pts.length - 1; i++) {
                beam(w, pts[i][0], pts[i][1], pts[i][2], pts[i + 1][0], pts[i + 1][1], pts[i + 1][2], 1.5, AIR, false);
            }
            for (int i = 1; i < pts.length - 1; i++) {
                beam(w, pts[i][0], pts[i][1], pts[i][2], pts[i][0], pts[i][1] + 0.01, pts[i][2], 3.2, METAL_D, true);
            }
        }
        for (int k = 0; k < 64; k++) {
            double a = k / 64.0 * Math.PI * 2;
            int x = round(PX + cos(a) * (PR + 1)), z = round(PZ + sin(a) * (PR + 1));
            w.force(x, Y + 1, z, BARS);
            w.force(x, Y + 2, z, k % 8 != 0 ? BARS : METAL_D);
        }
        for (int k = 0; k < 6; k++) {
            double a = k / 6.0 * Math.PI * 2 + 0.5 + r.range(-0.2, 0.2), d = r.range(32, 46);
            int cx = round(cos(a) * d), cz = round(sin(a) * d);
            if (hyp(cx - PX, cz - PZ) < 18) {
                continue;
            }
            int hw = r.nextInt(3, 6), hd = r.nextInt(3, 6), h = r.nextInt(12, 30);
            for (int x = -hw; x <= hw; x++) {
                for (int z = -hd; z <= hd; z++) {
                    for (int y = 1; y <= h; y++) {
                        boolean edge = Math.abs(x) == hw || Math.abs(z) == hd;
                        if (!edge && y < h) {
                            continue;
                        }
                        w.force(cx + x, ground.at(cx, cz) + y, cz + z,
                                edge && y % 4 == 2 && hash3(x, y, z, seed + k) < 0.4 ? WIN_LIT : metal.at(cx + x, y, cz + z));
                    }
                }
            }
        }
        int bx0 = 6, bz0 = 40, bh = 54;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 1; y <= bh; y++) {
                    boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
                    if (!edge && y < bh) {
                        continue;
                    }
                    w.force(bx0 + x, Y + y, bz0 + z, edge && y % 4 == 2 && hash3(x, y, z, seed + 77) < 0.4 ? WIN_LIT : metal.at(bx0 + x, y, bz0 + z));
                }
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z : new int[] {-4, 4}) {
                w.force(bx0 + x, Y + bh + 1, bz0 + z, BARS);
            }
        }
        for (int z = -4; z <= 4; z++) {
            for (int x : new int[] {-4, 4}) {
                w.force(bx0 + x, Y + bh + 1, bz0 + z, BARS);
            }
        }
        res.mark("pit", PX, Y - 5, PZ);
        // the loot (StructureLoot): on the tower's ground floor, against the west wall, clear of the stair
        w.force(-6, Y + 1, 0, CHEST);
        res.mark("loot", -6, Y + 1, 0);
        res.mark("tower", 0, Y + 1, 0);
        res.mark("center", 0, Y + 1, 12);
        return res;
    }

    // ================================================================ 7. MYOBOKU: the Sages' Spring (sage training)
    static Result myoboku(int seed, World W) {
        Vox w = new Vox();
        Result res = new Result(w);
        Rng r = new Rng(seed * 53 + 13);
        int R = 58, Y = W.floor();
        res.radius = R;
        Ground ground = terrain(res, W, 24, Y, (x, z, h) -> valueNoise(x, z, seed + 4, 6) > 0.7 ? MOSS : GRASS, (x, z) -> DIRT);
        for (int x = -38; x <= 38; x++) {
            for (int z = -R; z <= -16; z++) {
                double edge = -16 - (valueNoise(x, 0, seed + 8, 7) - 0.5) * 6;
                if (z > edge) {
                    continue;
                }
                double top = Y + 26 + (valueNoise(x, z, seed + 9, 12) - 0.5) * 10 - Math.max(0, Math.abs(x) - 22) * 1.8 - Math.max(0, -z - 40) * 1.4;
                if (top < ground.at(x, z)) {
                    continue;
                }
                for (int y = ground.at(x, z); y <= top; y++) {
                    int band = floor(y / 3.0 + valueNoise(x, z, seed + 10, 9) * 2) % 3;
                    w.force(x, y, z, band == 1 ? PEAK_M : PEAK);
                }
                w.force(x, floor(top) + 1, z, MOSS);
            }
        }
        double hx = 0.5;
        int hy = Y + 9, hz = -15;
        for (int x = -13; x <= 13; x++) {
            for (int y = -8; y <= 11; y++) {
                for (int z = -8; z <= 8; z++) {
                    double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
                    double head = sq(px / 12) + sq(py / 7) + sq(pz / 7);
                    double eyeL = sq((px + 5.5) / 2.8) + sq((py - 5) / 2.6) + sq((pz - 1) / 2.8), eyeR = sq((px - 5.5) / 2.8) + sq((py - 5) / 2.6) + sq((pz - 1) / 2.8);
                    if (Math.min(head, Math.min(eyeL, eyeR)) > 1) {
                        continue;
                    }
                    int t = valueNoise3(x, y, z, seed + 12, 3) > 0.62 ? STATUE_M : STATUE;
                    if (eyeL < 1 || eyeR < 1) {
                        double ex = px < 0 ? -5.5 : 5.5;
                        if (pz > 2.4 && Math.abs(py - 5) < 0.9 && Math.abs(px - ex) < 1.6) {
                            t = BLACK;
                        }
                    }
                    w.force(floor(hx + x), hy + y, hz + z, t);
                }
            }
        }
        for (int x = -8; x <= 8; x++) {
            for (int y = -2; y <= 0; y++) {
                for (int z = 2; z <= 8; z++) {
                    if (Math.abs(x) < 8 - (y + 2) * 0.5) {
                        w.force(floor(hx + x), hy + y, hz + z, AIR);
                    }
                }
            }
        }
        int pz0 = -3;
        for (int x = -16; x <= 16; x++) {
            for (int z = -14; z <= 10; z++) {
                double e = sq(x / 14.5) + sq((z - pz0) / 10.0);
                if (e > 1.18) {
                    continue;
                }
                if (e > 1) {
                    w.force(x, Y, z, hash3(x, 0, z, seed) < 0.5 ? STONE : MOSSY);
                    continue;
                }
                w.force(x, Y, z, OIL);
                w.force(x, Y - 1, z, OIL);
                w.force(x, Y - 2, z, e < 0.5 ? OIL : DIRT);
                w.force(x, Y - 3, z, DIRT);
            }
        }
        for (int y = hy - 2; y > Y; y--) {
            for (int x = -5; x <= 5; x++) {
                w.force(x, y, hz + 7 + floor((hy - y) * 0.12), OILFALL);
            }
        }
        int[][] stones = {{-6, 1}, {0, 4}, {6, 0}, {-1, -4}};
        for (int[] st : stones) {
            int sx = st[0], sz = st[1];
            box(w, sx, Y - 2, sz, sx + 1, Y + 1, sz + 1, PEAK);
            box(w, sx, Y + 1, sz, sx + 1, Y + 1, sz + 1, SLAB);
            res.mark("stone", sx, Y + 2, sz);
        }
        interface Frog {
            void at(int cx, int cz, double s, double yaw);
        }
        Frog frog = (cx, cz, s, yaw) -> {
            int gy = ground.at(cx, cz) + 1;
            double c = cos(yaw), si = sin(yaw);
            for (int x = -4; x <= 4; x++) {
                for (int y = 0; y <= 5; y++) {
                    for (int z = -4; z <= 4; z++) {
                        double lx = (x + 0.5) * c + (z + 0.5) * si, lz = -(x + 0.5) * si + (z + 0.5) * c, lyy = y + 0.5;
                        double body = sq(lx / (1.5 * s)) + sq((lyy - 1.1 * s) / (1.15 * s)) + sq((lz + 0.2 * s) / (1.4 * s));
                        double head = sq(lx / (1.2 * s)) + sq((lyy - 2.1 * s) / (0.8 * s)) + sq((lz - 0.8 * s) / (0.9 * s));
                        double eye = Math.min(sq((lx - 0.6 * s) / (0.4 * s)), sq((lx + 0.6 * s) / (0.4 * s))) + sq((lyy - 2.8 * s) / (0.4 * s))
                                + sq((lz - 0.9 * s) / (0.4 * s));
                        if (Math.min(body, Math.min(head, eye)) > 1) {
                            continue;
                        }
                        w.force(cx + x, gy + y, cz + z, valueNoise3(cx + x, y, cz + z, seed + 15, 2) > 0.6 ? STATUE_M : STATUE);
                    }
                }
            }
        };
        for (int k = 0; k < 7; k++) {
            double a = Math.PI * 0.15 + k / 6.0 * Math.PI * 0.7 + r.range(-0.1, 0.1), d = r.range(17, 22);
            double xs = cos(a) * d * (k % 2 != 0 ? 1 : -1);
            int x = round(xs), z = round(pz0 + sin(a) * d * 0.7 + 3);
            double s = r.range(0.8, 1.7);
            // the preview's -x keeps the sign of a rounded zero (Math.round(-0.3) is -0 in JS)
            double negX = x != 0 ? -x : (xs < 0 ? 0.0 : -0.0);
            frog.at(x, z, s, StrictMath.atan2(negX, pz0 - z) + r.range(-0.6, 0.6));
        }
        interface Leaf {
            void at(int x, int z, int h, double rr, double a);
        }
        Leaf leaf = (x, z, h, rr, a) -> {
            int g = ground.at(x, z) + 1;
            double ex = x + cos(a) * h * 0.35, ez = z + sin(a) * h * 0.35;
            beam(w, x + 0.5, g, z + 0.5, ex, g + h, ez, 0.6, STEM, true);
            for (int i = -ceil(rr); i <= rr; i++) {
                for (int j = -ceil(rr); j <= rr; j++) {
                    double d = hyp(i, j);
                    if (d > rr) {
                        continue;
                    }
                    w.set(round(ex + i), round(g + h - sq(d / rr) * 2), round(ez + j), d > rr - 1 ? LEAVES_D : LEAF_G);
                }
            }
        };
        for (int k = 0; k < 9; k++) {
            double a = r.range(0, Math.PI * 2), d = r.range(26, 46);
            int x = round(cos(a) * d), z = round(sin(a) * d);
            if (z < -14) {
                continue;
            }
            int h = r.nextInt(7, 15);
            leaf.at(x, z, h, r.range(3, 6), a);
        }
        int cx = 22, cz = 8, cg = ground.at(cx, cz);
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double d = hyp(x, z);
                if (d > 5.6) {
                    continue;
                }
                w.force(cx + x, cg, cz + z, PLANK);
                if (d > 4.4) {
                    for (int y = 1; y <= 4; y++) {
                        w.force(cx + x, cg + y, cz + z, (x == -5 || x == -4) && Math.abs(z) < 2 && y < 4 ? AIR : (y == 2 && Math.abs(x) < 2 ? WIN_LIT : STEM));
                    }
                }
            }
        }
        ball(w, cx + 0.5, cg + 5, cz + 0.5, 7.5, 3.6, 7.5, LEAF_G, true);
        for (int x = -7; x <= 8; x++) {
            for (int z = -7; z <= 8; z++) {
                for (int y = cg + 1; y <= cg + 4; y++) {
                    if (w.get(cx + x, y, cz + z) == LEAF_G) {
                        w.force(cx + x, y, cz + z, AIR);
                    }
                }
            }
        }
        w.force(cx - 6, cg + 1, cz - 2, LANTERN);
        w.force(cx - 6, cg + 1, cz + 2, LANTERN);
        // the loot (StructureLoot): the toads' pantry, against the back wall of the house
        w.force(cx + 4, cg + 1, cz, CHEST);
        res.mark("loot", cx + 4, cg + 1, cz);
        res.mark("center", 0, Y + 1, 6);
        return res;
    }

    // The giant toad's peak: a very tall stone needle with the stair cut around it and the summoning circle on top.
    static Result pico(int seed, World W) {
        Vox w = new Vox();
        Result res = new Result(w);
        int R = 46, Y = W.floor();
        res.radius = R;
        terrain(res, W, 14, Y, (x, z, h) -> GRASS, (x, z) -> DIRT);
        int H = 46;
        for (int y = Y - 2; y <= Y + H; y++) {
            double t = (double) (y - Y) / H;
            double rad = lerp(9, 4.6, smooth(clamp(t * 1.3, 0, 1)));
            if (t > 0.9) {
                rad = lerp(4.6, 9, smooth((t - 0.9) / 0.1));
            }
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    double a = StrictMath.atan2(z, x), d = hyp(x + 0.5, z + 0.5);
                    double rr = rad + (valueNoise3(cos(a) * 6, y * 0.6, sin(a) * 6, seed + 3, 2) - 0.5) * 1.8;
                    if (d > rr) {
                        continue;
                    }
                    int band = floor(y / 3.0 + valueNoise(x, z, seed + 10, 9) * 2) % 3;
                    w.force(x, y, z, band == 1 ? PEAK_M : PEAK);
                }
            }
        }
        for (int y = Y + 1; y <= Y + H - 1; y++) {
            double t = (double) (y - Y) / H, rad = lerp(9, 4.6, smooth(clamp(t * 1.3, 0, 1))), a = y * 0.24;
            for (int k = 0; k < 3; k++) {
                int x = floor(0.5 + cos(a + k * 0.12) * (rad - 0.2)), z = floor(0.5 + sin(a + k * 0.12) * (rad - 0.2));
                w.force(x, y - 1, z, SLAB);
                for (int yy = 0; yy < 3; yy++) {
                    w.force(x, y + yy, z, AIR);
                }
            }
        }
        int TY = Y + H;
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double d = hyp(x + 0.5, z + 0.5);
                if (d > 8.6) {
                    continue;
                }
                w.force(x, TY, z, PEAK);
                double a = StrictMath.atan2(z + 0.5, x + 0.5);
                boolean ring = Math.abs(d - 6.5) < 0.6 || Math.abs(d - 3) < 0.5;
                boolean ray = d > 3 && d < 6.5 && Math.abs(((a / (Math.PI * 2) * 8) % 1 + 1) % 1 - 0.5) < 0.12;
                if (ring || ray || d < 1) {
                    w.force(x, TY, z, CARVE);
                }
                if (d > 7.8 && valueNoise(x, z, seed, 3) > 0.5) {
                    w.force(x, TY + 1, z, MOSS);
                }
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = k / 8.0 * Math.PI * 2;
            int x = round(cos(a) * 7.8), z = round(sin(a) * 7.8);
            w.force(x, TY + 1, z, LANTERN);
        }
        // the loot (StructureLoot): the prize of the climb, on the edge of the summoning circle
        w.force(6, TY + 1, 3, CHEST);
        res.mark("loot", 6, TY + 1, 3);
        res.mark("top", 0, TY + 1, 0);
        res.mark("foot", round(cos(Y * 0.24) * 9), Y + 1, round(sin(Y * 0.24) * 9));
        res.mark("center", 0, TY + 1, 0);
        return res;
    }
}
