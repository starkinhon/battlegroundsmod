package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;

// The Deserto do Vento (Stark 2026-11-11, "que perfeição", the approved preview built from
// /mnt/project-files/forja/naruto-deserto/gen.js). That generator line by line, like LeafTreeGen and DeathTreeGen: the
// same steps in the same order with the same random, so a seed makes here what it makes in the preview. Change one,
// change the other.
// A desert the wind made. The wind always blows the same way (WIND) and everything shows it: crescent dunes with the
// gentle side to the wind and the steep side away, their horns pointing where the wind goes, long sand ridges across the
// wind between them, the sand smooth in eighth-of-a-block layers; hard ground between the dune seas (cracked sandstone,
// red sand patches); layered sand-coloured rock carved by the wind (mesas with vertical grooves and ledges where the
// layer is hard, arches, hoodoos with caps, mushroom rocks with an eaten foot); the bones of giant beasts lying face
// down, their ribs a tunnel; bleached dead trees.
public final class DesertGen {
    public static final byte AIR = 0, SAND = 1, SANDSTONE = 2, SMOOTH = 3, RED_SAND = 4, T_ORANGE = 5, T_YELLOW = 6, T_WHITE = 7,
            T_BROWN = 8, RED_SS = 9, BONE = 10, DEADWOOD = 11, DEAD_BUSH = 12, DRY_GRASS = 13, CRACKED = 14;
    // 21..27: sand in layers (1 to 7 eighths of a block)
    public static final byte LAYER = 20;

    // The wind: blows toward (cos, sin) of this angle, the same in the whole world.
    public static final double WIND = 0.35;
    public static final double CW = Math.cos(WIND), SW = Math.sin(WIND);

    private DesertGen() {
    }

    public static boolean isLayer(int t) {
        return t > LAYER && t < LAYER + 8;
    }

    static double hash3(int x, int y, int z, int seed) {
        return LeafTreeGen.hash3(x, y, z, seed);
    }

    private static double smooth(double a) {
        return a * a * (3 - 2 * a);
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double sstep(double a, double b, double v) {
        return smooth(clamp((v - a) / (b - a), 0, 1));
    }

    private static double sq(double a) {
        return a * a;
    }

    private static int round(double a) {
        return (int) Math.round(a);
    }

    private static int floor(double a) {
        return (int) Math.floor(a);
    }

    // The preview's valueNoise, for coordinates that aren't whole blocks too.
    public static double valueNoise(double x, double z, int seed, double scale) {
        double fx = x / scale, fz = z / scale;
        int ix = floor(fx), iz = floor(fz);
        double tx = smooth(fx - ix), tz = smooth(fz - iz);
        double a = hash3(ix, 0, iz, seed) + (hash3(ix + 1, 0, iz, seed) - hash3(ix, 0, iz, seed)) * tx;
        double b = hash3(ix, 0, iz + 1, seed) + (hash3(ix + 1, 0, iz + 1, seed) - hash3(ix, 0, iz + 1, seed)) * tx;
        return a + (b - a) * tz;
    }

    static double valueNoise3(double x, double y, double z, int seed, double scale) {
        double fx = x / scale, fy = y / scale, fz = z / scale;
        int ix = floor(fx), iy = floor(fy), iz = floor(fz);
        double tx = smooth(fx - ix), ty = smooth(fy - iy), tz = smooth(fz - iz);
        double x00 = lerp(hash3(ix, iy, iz, seed), hash3(ix + 1, iy, iz, seed), tx);
        double x10 = lerp(hash3(ix, iy + 1, iz, seed), hash3(ix + 1, iy + 1, iz, seed), tx);
        double x01 = lerp(hash3(ix, iy, iz + 1, seed), hash3(ix + 1, iy, iz + 1, seed), tx);
        double x11 = lerp(hash3(ix, iy + 1, iz + 1, seed), hash3(ix + 1, iy + 1, iz + 1, seed), tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ---- voxels -------------------------------------------------------------------------------------------------

    // The first piece to claim a block keeps it (set); rock takes it over (force).
    public static final class Vox {
        final Map<Long, Byte> m = new HashMap<>();

        static long key(int x, int y, int z) {
            return ((x + 2048L) * 4096 + (y + 256)) * 4096 + (z + 2048);
        }

        int get(int x, int y, int z) {
            Byte t = m.get(key(x, y, z));
            return t == null ? 0 : t;
        }

        void set(int x, int y, int z, byte t) {
            m.putIfAbsent(key(x, y, z), t);
        }

        void force(int x, int y, int z, byte t) {
            if (t == 0) {
                m.remove(key(x, y, z));
            } else {
                m.put(key(x, y, z), t);
            }
        }

        // Packed as x, y, z, type per voxel (air left out).
        public int[] pack() {
            int n = 0;
            for (Byte t : m.values()) {
                if (t != 0) {
                    n++;
                }
            }
            int[] out = new int[n * 4];
            int i = 0;
            for (Map.Entry<Long, Byte> e : m.entrySet()) {
                if (e.getValue() == 0) {
                    continue;
                }
                long k = e.getKey();
                int z = (int) (k % 4096) - 2048;
                long r = (k - (z + 2048)) / 4096;
                int y = (int) (r % 4096) - 256;
                int x = (int) ((r - (y + 256)) / 4096) - 2048;
                out[i++] = x;
                out[i++] = y;
                out[i++] = z;
                out[i++] = e.getValue();
            }
            return out;
        }
    }

    // ---- rock layers ----------------------------------------------------------------------------------------------

    // By height in the world (the same layer runs through every rock at that height), waving a little. Suna's rock: sand
    // coloured, with few colour lines. The hard ones (sandstone, smooth, red sandstone) stand out of the cliffs.
    private static final byte[][] STRATA = {
        {SANDSTONE, 4}, {SMOOTH, 2}, {SANDSTONE, 3}, {T_YELLOW, 1}, {SANDSTONE, 3}, {SMOOTH, 1}, {T_WHITE, 1}, {SANDSTONE, 2},
        {T_ORANGE, 1},
    };
    private static final int STRATA_H;

    static {
        int h = 0;
        for (byte[] s : STRATA) {
            h += s[1];
        }
        STRATA_H = h;
    }

    private static boolean hard(int t) {
        return t == SANDSTONE || t == RED_SS || t == SMOOTH;
    }

    static byte stratum(int x, int y, int z, int seed) {
        double wob = (valueNoise(x, z, seed + 50, 40) - 0.5) * 4 + (valueNoise(x, z, seed + 51, 9) - 0.5) * 1.2;
        int k = floor(y + wob) % STRATA_H;
        if (k < 0) {
            k += STRATA_H;
        }
        for (byte[] s : STRATA) {
            if (k < s[1]) {
                return s[0];
            }
            k -= s[1];
        }
        return SANDSTONE;
    }

    // ---- dunes ----------------------------------------------------------------------------------------------------

    // Where the dune sea is (1) and where the ground is hard (0).
    public static double duneField(int x, int z, int seed) {
        return sstep(0.4, 0.56, valueNoise(x, z, seed + 30, 70));
    }

    // Long ridges across the wind: saw-tooth, rising slowly on the wind side and falling steep on the other.
    static double ridges(int x, int z, int seed) {
        double u = x * CW + z * SW, v = -x * SW + z * CW;
        double ph = (u + (valueNoise(x, z, seed + 40, 26) - 0.5) * 34) / 24;
        double f = ph - Math.floor(ph);
        double p = f < 0.78 ? f / 0.78 : 1 - (f - 0.78) / 0.22;
        double amp = 1.5 + 4.5 * valueNoise(v, Math.floor(ph), seed + 41, 18);
        return Math.pow(p, 1.4) * amp;
    }

    private static final int DUNE_SIZE = 64, DUNE_PAD = 12, DUNE_SALT = 707;
    private static final double DUNE_CHANCE = 0.6;

    private record Dune(int x, int z, double h, double w) {
    }

    private static Dune duneAt(int i, int j, int seed) {
        if (hash3(i, DUNE_SALT, j, seed) >= DUNE_CHANCE) {
            return null;
        }
        int span = DUNE_SIZE - 2 * DUNE_PAD;
        int x = i * DUNE_SIZE + DUNE_PAD + floor(hash3(i, DUNE_SALT + 1, j, seed) * span);
        int z = j * DUNE_SIZE + DUNE_PAD + floor(hash3(i, DUNE_SALT + 2, j, seed) * span);
        double h = 7 + hash3(i, DUNE_SALT + 3, j, seed) * 8;
        double w = h * (1.7 + hash3(i, DUNE_SALT + 4, j, seed) * 0.6);
        return new Dune(x, z, h, w);
    }

    // Barchan: a half moon. The crest bends with its horns forward (where the wind goes); behind it the sand rises
    // slowly; in front it falls at the sand's angle of repose (~32°).
    private static double barchan(Dune d, int x, int z) {
        double dx = x - d.x, dz = z - d.z;
        double u = dx * CW + dz * SW, v = -dx * SW + dz * CW;
        double q = v / d.w;
        if (Math.abs(q) >= 1) {
            return 0;
        }
        double crest = d.h * Math.pow(1 - q * q, 0.75);
        double uc = d.h * 1.5 * q * q;
        if (u <= uc) {
            double t = (uc - u) / (d.h * 2.8 * (0.35 + 0.65 * crest / d.h));
            return t >= 1 ? 0 : crest * (1 - t * t) * (1 - 0.15 * t);
        }
        return Math.max(0, crest - (u - uc) * 0.62);
    }

    // How much sand rises over the ground (in blocks, with a fraction).
    public static double duneHeight(int x, int z, int seed) {
        double f = duneField(x, z, seed);
        if (f <= 0) {
            return 0;
        }
        double h = ridges(x, z, seed) * f;
        for (int i = floor((x - 60) / (double) DUNE_SIZE); i <= floor((x + 60) / (double) DUNE_SIZE); i++) {
            for (int j = floor((z - 60) / (double) DUNE_SIZE); j <= floor((z + 60) / (double) DUNE_SIZE); j++) {
                Dune d = duneAt(i, j, seed);
                if (d == null) {
                    continue;
                }
                double b = barchan(d, x, z) * duneField(d.x, d.z, seed);
                if (b > h) {
                    h = b;
                }
            }
        }
        return h;
    }

    // ---- rock -----------------------------------------------------------------------------------------------------

    // A column of rock: at each height the radius comes from the profile, the hard layers out and the soft in, noise
    // eating the edge; the wind grinds the foot most (up to 3 blocks over the ground) and the side it blows from.
    private static void rockColumn(Vox w, int ox, int oy, int oz, int h, DoubleUnaryOperator rOf, int seed, byte cap) {
        double maxBase = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= h; i++) {
            maxBase = Math.max(maxBase, rOf.applyAsDouble((double) i / h));
        }
        int maxR = (int) Math.ceil(maxBase + 2.5);
        for (int y = 0; y <= h; y++) {
            double t = (double) y / h, base = rOf.applyAsDouble(t);
            for (int dx = -maxR; dx <= maxR; dx++) {
                for (int dz = -maxR; dz <= maxR; dz++) {
                    int x = ox + dx, z = oz + dz, wy = oy + y;
                    byte mat = cap != 0 && y >= h - 1 ? cap : stratum(x, wy, z, seed);
                    double r = base * (0.9 + 0.2 * valueNoise3(x, wy * 0.3, z, seed + 60, 4)) + (hard(mat) ? 0.3 : -0.2);
                    if (y < 3) {
                        r *= 0.84 + y * 0.05;
                    }
                    double d = Math.hypot(dx, dz);
                    if (base > 4) {
                        double arc = Math.atan2(dz, dx) * base;
                        r *= (0.68 + 0.5 * valueNoise(arc, 0, seed + 63, base * 0.9))
                                * (1 - 0.16 * sstep(0.5, 0.85, valueNoise(arc, 0, seed + 62, 2.4)));
                    }
                    double side = d > 0 ? (dx * CW + dz * SW) / d : 0;
                    r *= 1 - 0.08 * Math.max(0, -side);
                    if (d > r) {
                        continue;
                    }
                    if (y == h && hash3(x, wy, z, seed + 61) < 0.25 * (d / Math.max(1, r))) {
                        continue;
                    }
                    w.force(x, wy, z, mat);
                }
            }
        }
    }

    // Mesa: a flat-topped hill of two or three lobes, its cliffs in steps. At the foot a heap of fallen sand and stone.
    private static int mesa(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double big = rng.range(10, 17);
        int top = rng.nextInt(24, 38);
        double lobe = rng.range(0, 6.28), elong = rng.range(1.0, 1.45);
        int parts = rng.nextInt(2, 3);
        for (int p = 0; p < parts; p++) {
            double a = lobe + p * 2.1, off = p == 0 ? 0 : big * rng.range(0.5, 0.8);
            double r = p == 0 ? big : big * rng.range(0.55, 0.8);
            int h = p == 0 ? top : Math.max(8, round(top * rng.range(0.6, 0.95)));
            rockColumn(w, round(ox + Math.cos(a) * off * elong), oy - 1, round(oz + Math.sin(a) * off), h,
                    t -> r * (t < 0.1 ? 1.12 - t * 1.2 : 1) * (t > 1 - 2.5 / h ? 1.06 : 1), seed + p * 7, SANDSTONE);
        }
        talus(w, ox, oy, oz, big * 1.7, seed, top * 0.22);
        return top;
    }

    // Hoodoos: thin columns with a cap of hard rock, in a group.
    private static void hoodoos(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int n = rng.nextInt(3, 7);
        for (int i = 0; i < n; i++) {
            double a = rng.range(0, 6.28), d = i == 0 ? 0 : rng.range(3, 8);
            int x = round(ox + Math.cos(a) * d), z = round(oz + Math.sin(a) * d);
            int h = rng.nextInt(6, 16);
            double r0 = rng.range(1.3, 2.4), ph = rng.range(0, 6.28);
            rockColumn(w, x, oy - 1, z, h, t -> {
                double neck = t > 0.75 && t < 0.92 ? 0.7 : 1;
                double capW = t >= 0.92 ? 1.55 : 1;
                return r0 * (0.85 + 0.2 * Math.sin(t * h * 0.8 + ph)) * neck * capW * (t < 0.1 ? 1.25 : 1);
            }, seed + i * 13, i % 3 == 0 ? RED_SS : SMOOTH);
        }
        talus(w, ox, oy, oz, 9, seed + 3, 2.6);
    }

    // Mushroom rock: sand on the wind grinds the foot down to a thin neck under a wide head.
    private static void mushroomRock(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int h = rng.nextInt(5, 9);
        double big = rng.range(2.6, 4.2);
        rockColumn(w, ox, oy - 1, oz, h,
                t -> t < 0.55 ? big * (0.42 + 0.25 * t) : big * (0.85 + 0.3 * Math.sin((t - 0.55) / 0.45 * Math.PI)), seed, (byte) 0);
    }

    // Arch: two thick legs and a curved span, eaten by the wind, across the wind.
    private static void arch(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double span = rng.range(14, 22), ha = rng.range(12, 18), a = WIND + Math.PI / 2 + rng.range(-0.5, 0.5);
        double ax = ox - Math.cos(a) * span / 2, az = oz - Math.sin(a) * span / 2;
        double bx = ox + Math.cos(a) * span / 2, bz = oz + Math.sin(a) * span / 2;
        double thick = rng.range(1.8, 2.6);
        int steps = (int) Math.ceil(span * 3 + ha * 3);
        for (int i = 0; i <= steps; i++) {
            double th = (double) i / steps * Math.PI;
            double s = 0.5 - 0.5 * Math.cos(th);
            double cx = ax + (bx - ax) * s, cz = az + (bz - az) * s, cy = oy - 1 + Math.sin(th) * ha;
            double r = thick * (1 + 0.45 * Math.pow(Math.abs(Math.cos(th)), 4));
            int n = (int) Math.ceil(r + 1.5);
            for (int dx = -n; dx <= n; dx++) {
                for (int dy = -n; dy <= n; dy++) {
                    for (int dz = -n; dz <= n; dz++) {
                        int x = round(cx) + dx, y = round(cy) + dy, z = round(cz) + dz;
                        if (y < oy - 1) {
                            continue;
                        }
                        byte mat = stratum(x, y, z, seed);
                        double rr = r * (0.82 + 0.3 * valueNoise3(x, y, z, seed + 60, 3.5)) + (hard(mat) ? 0.4 : -0.3);
                        if (sq(x - cx) + sq(y - cy) + sq(z - cz) > rr * rr) {
                            continue;
                        }
                        w.force(x, y, z, mat);
                    }
                }
            }
        }
        talus(w, ax, oy, az, 6, seed + 1, 2.6);
        talus(w, bx, oy, bz, 6, seed + 2, 2.6);
    }

    // A heap of sand and fallen stone at a rock's foot, higher on the side away from the wind (the sand stops there).
    private static void talus(Vox w, double ox, int oy, double oz, double big, int seed, double top) {
        int n = (int) Math.ceil(big + 2);
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                int x = round(ox) + dx, z = round(oz) + dz;
                double d = Math.hypot(x - ox, z - oz);
                double lee = 1 + 0.5 * Math.max(0, (dx * CW + dz * SW) / Math.max(1, d));
                double h = top * (1 - d / big) * (1 - d / big) * lee + (valueNoise(x, z, seed + 70, 4) - 0.5) * 1.2;
                if (h <= 0) {
                    continue;
                }
                int full = floor(h);
                for (int y = 0; y < full; y++) {
                    w.set(x, oy + y, z, hash3(x, y, z, seed + 71) < 0.12 ? SANDSTONE : SAND);
                }
                int l = round((h - full) * 8);
                if (l > 0 && l < 8) {
                    w.set(x, oy + full, z, (byte) (LAYER + l));
                } else if (l == 8) {
                    w.set(x, oy + full, z, SAND);
                }
            }
        }
    }

    // ---- bones, dead tree, boulder --------------------------------------------------------------------------------

    private record Seg(double x, double y, double z, double dx, double dz, double t, int i) {
    }

    private static void ball(Vox w, double cx, double cy, double cz, double r) {
        int n = (int) Math.ceil(r);
        for (int ex = -n; ex <= n; ex++) {
            for (int ey = -n; ey <= n; ey++) {
                for (int ez = -n; ez <= n; ez++) {
                    if (ex * ex + ey * ey + ez * ez <= r * r + 0.3) {
                        w.set(round(cx) + ex, round(cy) + ey, round(cz) + ez, BONE);
                    }
                }
            }
        }
    }

    // A giant beast fallen face down: the spine up high and the ribs coming down in arches on both sides into the sand, a
    // tunnel to walk through. Neck and tail sink into the sand; the skull lies on the ground in front, with curved horns.
    // The sand covers whatever is under it (the bones only go into the air).
    private static void skeleton(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int len = rng.nextInt(34, 48);
        double a = rng.range(0, 6.28);
        double hs = rng.range(10, 14), ribW = rng.range(7, 10);
        double bend = rng.range(-0.01, 0.01);
        double px = ox - Math.cos(a) * len / 2, pz = oz - Math.sin(a) * len / 2, dx = Math.cos(a), dz = Math.sin(a);
        List<Seg> spine = new ArrayList<>();
        for (int i = 0; i <= len; i++) {
            double t = (double) i / len;
            double y = oy - 1 + hs * Math.pow(Math.sin(t * Math.PI), 0.55);
            spine.add(new Seg(px, y, pz, dx, dz, t, i));
            ball(w, px, y, pz, t > 0.15 && t < 0.85 ? 1.0 : 0.6);
            if (i % 2 == 0 && t > 0.15 && t < 0.85) {
                w.set(round(px), round(y) + 2, round(pz), BONE);
                if (t > 0.3 && t < 0.7) {
                    w.set(round(px), round(y) + 3, round(pz), BONE);
                }
            }
            double ca = Math.cos(bend), sa = Math.sin(bend);
            double ndx = dx * ca - dz * sa, ndz = dx * sa + dz * ca;
            dx = ndx;
            dz = ndz;
            px += dx;
            pz += dz;
        }
        for (Seg s : spine) {
            if (s.t < 0.22 || s.t > 0.78 || s.i % 3 != 0) {
                continue;
            }
            double k = Math.pow(Math.sin((s.t - 0.22) / 0.56 * Math.PI), 0.5);
            double nx = -s.dz, nz = s.dx, drop = s.y - oy + 2;
            for (int sg = -1; sg <= 1; sg += 2) {
                double hb = hash3(s.i, sg, 7, seed);
                double end = hb < 0.12 ? 0.35 + hb * 3 : 1;
                for (int q = 0; q <= 48; q++) {
                    double th = q / 48.0 * end * Math.PI / 2;
                    double out = Math.sin(th) * ribW * (0.55 + 0.45 * k), down = (1 - Math.cos(th)) * drop;
                    int rx = round(s.x + nx * sg * out), ry = round(s.y - down), rz = round(s.z + nz * sg * out);
                    w.set(rx, ry, rz, BONE);
                    if (q < 10) {
                        w.set(rx, ry - 1, rz, BONE);
                    }
                }
            }
        }
        Seg h = spine.get(spine.size() - 1);
        double fx = h.dx, fz = h.dz, sx = -fz, sz = fx;
        double cx = h.x + fx * 4, cz = h.z + fz * 4;
        int cy = oy;
        for (int u = -3; u <= 6; u++) {
            for (int v = -3; v <= 3; v++) {
                for (int y = -1; y <= 3; y++) {
                    double tap = u > 1 ? (u - 1) * 0.45 : 0;
                    if (Math.abs(v) > 3.2 - tap || y > 3.2 - tap * 1.2 || (u < -2 && Math.abs(v) > 2)) {
                        continue;
                    }
                    if (u >= -1 && u <= 0 && Math.abs(v) >= 2 && y >= 1 && y <= 2) {
                        continue;
                    }
                    if (u >= 2 && y == 0 && Math.abs(v) < 2) {
                        continue;
                    }
                    w.set(round(cx + fx * u + sx * v), cy + y, round(cz + fz * u + sz * v), BONE);
                }
            }
        }
        for (int sg = -1; sg <= 1; sg += 2) {
            for (int q = 0; q <= 22; q++) {
                double t = q / 22.0;
                double x = cx - fx * (1 + t * 5) + sx * sg * (2.5 + t * 3), z = cz - fz * (1 + t * 5) + sz * sg * (2.5 + t * 3);
                double y = cy + 3 + t * 7 - t * t * 3;
                w.set(round(x), round(y), round(z), BONE);
                if (q < 8) {
                    w.set(round(x), round(y) + 1, round(z), BONE);
                }
            }
        }
    }

    private static void stamp(Vox w, double qx, double qy, double qz, double r) {
        int n = (int) Math.ceil(r);
        for (int ex = -n; ex <= n; ex++) {
            for (int ey = -n; ey <= n; ey++) {
                for (int ez = -n; ez <= n; ez++) {
                    if (ex * ex + ey * ey + ez * ez <= r * r + 0.25) {
                        w.set(round(qx) + ex, round(qy) + ey, round(qz) + ez, DEADWOOD);
                    }
                }
            }
        }
    }

    private static double[] norm(double x, double y, double z) {
        double l = Math.hypot(Math.hypot(x, y), z);
        if (l == 0) {
            l = 1;
        }
        return new double[] {x / l, y / l, z / l};
    }

    // Dead tree: a crooked trunk bleached by the sun, thin twisted branches, not a leaf.
    private static void deadTree(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int h = rng.nextInt(6, 11);
        double[] p = {ox, oy, oz};
        double[] d = {rng.range(-0.25, 0.25), 1, rng.range(-0.25, 0.25)};
        int nb = rng.nextInt(3, 5);
        int[] forks = new int[nb];
        for (int i = 0; i < nb; i++) {
            forks[i] = rng.nextInt(floor(h * 0.45), h);
        }
        for (int i = 0; i <= h; i++) {
            stamp(w, p[0], p[1], p[2], i < 2 ? 1.1 : 0.6);
            for (int f : forks) {
                if (f != i) {
                    continue;
                }
                double[] q = {p[0], p[1], p[2]};
                double[] e = norm(rng.range(-1, 1), rng.range(0.2, 0.9), rng.range(-1, 1));
                int len = rng.nextInt(3, 7);
                for (int k = 0; k < len; k++) {
                    stamp(w, q[0], q[1], q[2], 0);
                    e = norm(e[0] + rng.range(-0.5, 0.5), e[1] + rng.range(-0.3, 0.4), e[2] + rng.range(-0.5, 0.5));
                    q = new double[] {q[0] + e[0], q[1] + e[1], q[2] + e[2]};
                }
            }
            d = norm(d[0] + rng.range(-0.3, 0.3), d[1], d[2] + rng.range(-0.3, 0.3));
            p = new double[] {p[0] + d[0] * 0.8, p[1] + d[1], p[2] + d[2] * 0.8};
        }
    }

    // A loose stone ground smooth by the wind.
    private static void boulder(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double r = rng.range(1.2, 2.3);
        int n = (int) Math.ceil(r + 1);
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -1; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    int x = ox + dx, y = oy + dy, z = oz + dz;
                    double rr = r * (0.8 + 0.35 * valueNoise3(x, y, z, seed, 2.5));
                    if (dx * dx + sq(dy * 1.4) + dz * dz > rr * rr) {
                        continue;
                    }
                    w.force(x, y, z, hash3(x, y, z, seed) < 0.5 ? SANDSTONE : SMOOTH);
                }
            }
        }
    }

    // ---- cells ----------------------------------------------------------------------------------------------------

    public enum Kind {
        MESA(120, 0.5, 22, 26, 811, true), ARCH(96, 0.35, 16, 18, 823, true), SKELETON(112, 0.45, 24, 26, 839, false),
        HOODOO(44, 0.4, 10, 12, 853, true), MUSHROOM(34, 0.3, 5, 6, 859, true), TREE(22, 0.35, 3, 8, 863, false),
        BOULDER(15, 0.3, 2, 3, 877, true);

        final int size, pad, reach, salt;
        final double chance;
        final boolean rock;

        Kind(int size, double chance, int pad, int reach, int salt, boolean rock) {
            this.size = size;
            this.chance = chance;
            this.pad = pad;
            this.reach = reach;
            this.salt = salt;
            this.rock = rock;
        }

        public int reach() {
            return reach;
        }

        public boolean rock() {
            return rock;
        }
    }

    public record Obj(Kind kind, int x, int z, int seed) {
    }

    private static Obj cellObject(Kind c, int i, int j, int seed) {
        if (hash3(i, c.salt, j, seed) >= c.chance) {
            return null;
        }
        int span = c.size - 2 * c.pad;
        int x = i * c.size + c.pad + floor(hash3(i, c.salt + 1, j, seed) * span);
        int z = j * c.size + c.pad + floor(hash3(i, c.salt + 2, j, seed) * span);
        return new Obj(c, x, z, floor(hash3(i, c.salt + 3, j, seed) * 1e9) + 1);
    }

    // Rock and trees only on the hard ground (off the dunes); the bones anywhere, the sand buries what it buries.
    private static boolean wantsGround(Obj o, int seed) {
        if (o.kind == Kind.SKELETON) {
            return true;
        }
        double lim = o.kind == Kind.TREE || o.kind == Kind.BOULDER ? 0.6 : 1.2;
        return duneHeight(o.x, o.z, seed) < lim && duneField(o.x, o.z, seed) < (o.kind.rock ? 0.5 : 0.7);
    }

    // Nothing grows hard against a big rock (a mesa or an arch).
    private static boolean nearBig(Obj o, int seed) {
        if (o.kind == Kind.MESA || o.kind == Kind.ARCH) {
            return false;
        }
        for (Kind c : new Kind[] {Kind.MESA, Kind.ARCH}) {
            int d = c.reach + 4;
            for (int i = floor((o.x - d - c.size) / (double) c.size); i <= floor((o.x + d) / (double) c.size); i++) {
                for (int j = floor((o.z - d - c.size) / (double) c.size); j <= floor((o.z + d) / (double) c.size); j++) {
                    Obj g = cellObject(c, i, j, seed);
                    if (g != null && Math.hypot(g.x - o.x, g.z - o.z) < d && wantsGround(g, seed)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static List<Obj> objectsNear(int x0, int z0, int x1, int z1, int seed, Predicate<Obj> accept) {
        List<Obj> out = new ArrayList<>();
        for (Kind c : Kind.values()) {
            for (int i = floor((x0 - c.reach) / (double) c.size); i <= floor((x1 + c.reach) / (double) c.size); i++) {
                for (int j = floor((z0 - c.reach) / (double) c.size); j <= floor((z1 + c.reach) / (double) c.size); j++) {
                    Obj o = cellObject(c, i, j, seed);
                    if (o == null) {
                        continue;
                    }
                    if (o.x + c.reach < x0 || o.x - c.reach > x1 || o.z + c.reach < z0 || o.z - c.reach > z1) {
                        continue;
                    }
                    if (!accept.test(o) || !wantsGround(o, seed) || nearBig(o, seed)) {
                        continue;
                    }
                    out.add(o);
                }
            }
        }
        return out;
    }

    public static Vox build(Obj o, int y) {
        Vox w = new Vox();
        switch (o.kind) {
            case MESA -> mesa(w, o.x, y, o.z, o.seed);
            case ARCH -> arch(w, o.x, y, o.z, o.seed);
            case SKELETON -> skeleton(w, o.x, y, o.z, o.seed);
            case HOODOO -> hoodoos(w, o.x, y, o.z, o.seed);
            case MUSHROOM -> mushroomRock(w, o.x, y, o.z, o.seed);
            case TREE -> deadTree(w, o.x, y, o.z, o.seed);
            default -> boulder(w, o.x, y, o.z, o.seed);
        }
        return w;
    }

    // ---- ground ---------------------------------------------------------------------------------------------------

    // The top of the hard ground (no dune): sand, cracked sandstone in plates, red sand patches.
    public static byte groundTop(int x, int z, int seed) {
        double f = duneField(x, z, seed);
        if (f < 0.35 && valueNoise(x, z, seed + 31, 7) > 0.6) {
            return CRACKED;
        }
        if (valueNoise(x, z, seed + 32, 13) > 0.78) {
            return RED_SAND;
        }
        return SAND;
    }

    // A plant on the ground: only on the hard ground, sparse.
    public static byte groundDeco(int x, int z, int seed) {
        if (duneField(x, z, seed) > 0.3) {
            return 0;
        }
        double h = hash3(x, 9, z, seed);
        if (h > 0.985) {
            return DEAD_BUSH;
        }
        if (h > 0.955 && valueNoise(x, z, seed + 33, 6) > 0.5) {
            return DRY_GRASS;
        }
        return 0;
    }
}
