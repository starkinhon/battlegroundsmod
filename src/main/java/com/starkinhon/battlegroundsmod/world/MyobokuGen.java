package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

import com.starkinhon.battlegroundsmod.world.DesertGen.Vox;
import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;

// The Monte Myoboku (Stark 2026-11-12, "literalmente perfeito"): the line-by-line port of the approved preview's
// generator (forja/naruto-myoboku/gen.js), the same draws in the same order, so the game makes what the preview showed.
// The land of the toads: a forest of thin, very tall stone spires in layers, with moss on the ledges, hanging vines and
// giant plants on the crown; between them giant leaves on curved stalks, flowers the size of trees, ponds of the sages'
// oil (the biome's water has the oil's colour) with lily pads, shrubs and the stone toads, the ones who couldn't take the
// sage training and turned to stone, each in its own size and pose.
public final class MyobokuGen {
    public static final byte AIR = 0, STONE = 1, STONE_DARK = 2, STONE_LIGHT = 3, MOSS = 4, GRASS = 5, DIRT = 6, WATER = 7,
            STEM = 8, LEAF = 9, LEAF_YELLOW = 10, LEAF_DARK = 11, PETAL = 12, PETAL_TIP = 13, CORE = 14, FROG = 15,
            FROG_MOSS = 16, FROG_DARK = 17, VINE = 18, LILY = 19, CARVE = 20, CLAY = 21;

    private MyobokuGen() {
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    static double hash3(int x, int y, int z, int seed) {
        return LeafTreeGen.hash3(x, y, z, seed);
    }

    static double valueNoise(double x, double z, int seed, double scale) {
        return DesertGen.valueNoise(x, z, seed, scale);
    }

    static double valueNoise3(double x, double y, double z, int seed, double scale) {
        return DesertGen.valueNoise3(x, y, z, seed, scale);
    }

    private static double smooth(double a) {
        return a * a * (3 - 2 * a);
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double sq(double a) {
        return a * a;
    }

    private static double frac(double a) {
        return a - Math.floor(a);
    }

    private static int floor(double a) {
        return (int) Math.floor(a);
    }

    private static double cos(double a) {
        return StrictMath.cos(a);
    }

    private static double sin(double a) {
        return StrictMath.sin(a);
    }

    private static final int[][] D = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // ---- the spires -----------------------------------------------------------------------------------------------

    public static final class Pillar {
        public final int x, z, H, k, layer, seed;
        public final double r0, lx, lz, ph;
        public int gy;

        Pillar(int x, int z, int H, double r0, double lx, double lz, int k, double ph, int layer, int seed) {
            this.x = x;
            this.z = z;
            this.H = H;
            this.r0 = r0;
            this.lx = lx;
            this.lz = lz;
            this.k = k;
            this.ph = ph;
            this.layer = layer;
            this.seed = seed;
        }

        int top() {
            return gy + H;
        }

        double[] center(double y) {
            double t = clamp((y - gy) / H, 0, 1);
            return new double[] {x + lx * H * t * t, z + lz * H * t * t};
        }

        int band(int y) {
            return floor((y - gy + 6 * valueNoise(x, y, seed + 3, 9)) / layer);
        }

        double radius(int y) {
            int h = y - gy;
            double t = clamp((double) h / H, 0, 1);
            double r = r0 * (1 - 0.48 * StrictMath.pow(t, 0.9)) * (1 + 0.55 * StrictMath.exp(-Math.max(h, 0) / 3.5));
            int b = band(y);
            double f = frac((y - gy + 6 * valueNoise(x, y, seed + 3, 9)) / layer);
            r += (hash3(b, 0, 0, seed + 4) - 0.5) * 1.1 + smooth(clamp((f - 0.72) / 0.28, 0, 1)) * 0.9;
            int top = top();
            if (y > top - 2) {
                r *= Math.sqrt(Math.max(0, 1 - sq((y - (top - 2)) / 2.6)));
            }
            return r;
        }

        boolean inside(int px, int y, int pz) {
            if (y > top()) {
                return false;
            }
            double[] c = center(y);
            double dx = px + 0.5 - c[0], dz = pz + 0.5 - c[1], d = Math.sqrt(dx * dx + dz * dz);
            double th = StrictMath.atan2(dz, dx);
            double groove = 0.09 * sin(k * th + ph + 2.2 * valueNoise(0, y, seed + 5, 11));
            double rr = radius(y) * (1 + groove) + 0.9 * (valueNoise3(px, y, pz, seed + 6, 3.2) - 0.5);
            return d < rr;
        }
    }

    static byte pillarStone(Pillar p, int x, int y, int z) {
        int b = p.band(y);
        double h = hash3(b, 1, 0, p.seed + 7), j = hash3(x >> 1, y, z >> 1, p.seed + 8);
        if (h < 0.5) {
            return j < 0.1 ? STONE_DARK : STONE;
        }
        if (h < 0.8) {
            return j < 0.12 ? STONE : STONE_LIGHT;
        }
        return STONE_DARK;
    }

    public static List<Pillar> spirePillars(int ox, int oz, int seed) {
        Rng rng = new Rng(seed);
        int n = 1 + (rng.next() < 0.55 ? 1 : 0) + (rng.next() < 0.3 ? 1 : 0);
        List<Pillar> out = new ArrayList<>();
        int H0 = rng.nextInt(40, 78);
        double r0 = rng.range(6, 9.5);
        double a0 = rng.range(0, Math.PI * 2);
        for (int i = 0; i < n; i++) {
            boolean main = i == 0;
            double a = a0 + i * 2.3 + rng.range(-0.4, 0.4), d = main ? 0 : rng.range(10, 14);
            double la = rng.range(0, Math.PI * 2), lk = rng.range(0.02, 0.08);
            int x = ox + (int) Math.round(cos(a) * d), z = oz + (int) Math.round(sin(a) * d);
            int H = main ? H0 : (int) Math.round(H0 * rng.range(0.4, 0.75));
            double r = main ? r0 : r0 * rng.range(0.55, 0.75);
            double lx = cos(la) * lk, lz = sin(la) * lk;
            int k = rng.nextInt(5, 9);
            double ph = rng.range(0, 6.283);
            int layer = rng.nextInt(4, 7);
            out.add(new Pillar(x, z, H, r, lx, lz, k, ph, layer, seed + 97 * (i + 1)));
        }
        return out;
    }

    // The spires of a piece, with grass on top, moss on the ledges, the hanging vines and the crown's plants.
    public static List<Pillar> spire(Vox w, int ox, int oz, int seed, IntBinaryOperator ground) {
        List<Pillar> pillars = spirePillars(ox, oz, seed);
        for (Pillar p : pillars) {
            p.gy = ground.applyAsInt(p.x, p.z);
            int top = p.top();
            int R = (int) Math.ceil(p.r0 * 1.7 + 2), L = (int) Math.ceil(Math.sqrt(p.lx * p.lx + p.lz * p.lz) * p.H + 1);
            for (int x = p.x - R - L; x <= p.x + R + L; x++) {
                for (int z = p.z - R - L; z <= p.z + R + L; z++) {
                    int g = ground.applyAsInt(x, z);
                    for (int y = g - 2; y <= top; y++) {
                        if (!p.inside(x, y, z)) {
                            continue;
                        }
                        boolean open = !p.inside(x, y + 1, z);
                        byte t = pillarStone(p, x, y, z);
                        if (open) {
                            t = y >= top - 1 ? GRASS : valueNoise3(x, y, z, p.seed + 9, 4) > 0.3 ? MOSS : t;
                        } else if (y >= top - 3 && !p.inside(x, y + 2, z)) {
                            t = DIRT;
                        }
                        w.force(x, y, z, t);
                        if (open && t == MOSS && y < top - 3) {
                            for (int[] dd : D) {
                                int dx = dd[0], dz = dd[1];
                                if (p.inside(x + dx, y, z + dz) || hash3(x + dx, y, z + dz, p.seed + 10) > 0.2) {
                                    continue;
                                }
                                int len = 3 + floor(hash3(x + dx, y + 1, z + dz, p.seed + 10) * 11);
                                for (int k = 0; k < len; k++) {
                                    int yy = y - k;
                                    if (yy <= ground.applyAsInt(x + dx, z + dz) || p.inside(x + dx, yy, z + dz)) {
                                        break;
                                    }
                                    w.set(x + dx, yy, z + dz, VINE);
                                }
                            }
                        }
                    }
                }
            }
            double[] c = p.center(top);
            Rng rng = new Rng(p.seed + 11);
            int m = rng.nextInt(1, 2);
            for (int i = 0; i < m; i++) {
                double a = rng.range(0, 6.283), d = i == 0 ? rng.range(0, 1.5) : rng.range(1.5, Math.max(1.6, p.r0 * 0.35));
                int x = floor(c[0] + cos(a) * d), z = floor(c[1] + sin(a) * d);
                leafPlant(w, x, top + 1, z, p.seed + 13 * (i + 1), 0.7);
            }
        }
        return pillars;
    }

    // ---- the giant plants -----------------------------------------------------------------------------------------

    static void line(Vox w, double ax, double ay, double az, double bx, double by, double bz, double r, byte t, boolean force) {
        int n = (int) Math.ceil(Math.max(Math.abs(bx - ax), Math.max(Math.abs(by - ay), Math.abs(bz - az))) * 2) + 1;
        int R = (int) Math.ceil(r);
        for (int i = 0; i <= n; i++) {
            double s = (double) i / n, x = ax + (bx - ax) * s, y = ay + (by - ay) * s, z = az + (bz - az) * s;
            for (int dx = -R; dx <= R; dx++) {
                for (int dy = -R; dy <= R; dy++) {
                    for (int dz = -R; dz <= R; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r * r + 0.01) {
                            continue;
                        }
                        int vx = floor(x + dx), vy = floor(y + dy), vz = floor(z + dz);
                        if (force) {
                            w.force(vx, vy, vz, t);
                        } else {
                            w.set(vx, vy, vz, t);
                        }
                    }
                }
            }
        }
    }

    static void leafBlade(Vox w, double cx, double cy, double cz, double dx, double dz, double R, double tilt, byte color, int seed) {
        double L = R * 1.3;
        double px = -dz, pz = dx;
        for (double a = -0.25; a <= 1.0001; a += 0.35 / L) {
            double half = R * Math.sqrt(Math.max(0, 1 - sq((a - 0.4) / 0.62))) * (a < 0 ? 0.6 : 1);
            for (double b = -half; b <= half + 0.0001; b += 0.35) {
                double along = a * L, e = Math.abs(b) / Math.max(half, 0.5);
                double y = cy + along * tilt - sq(Math.max(0, a - 0.55)) * L * 0.9 - e * e * R * 0.35 + (1 - e * e) * 0.3;
                int x = floor(cx + dx * along + px * b), z = floor(cz + dz * along + pz * b), yy = floor(y);
                boolean vein = Math.abs(b) < 0.55 && a > 0.02 && a < 0.9;
                boolean rib = Math.abs(frac((a * L + Math.abs(b) * 0.8) / 2.6) - 0.5) < 0.09 && e < 0.85 && e > 0.15;
                byte t = vein ? STEM : e > 0.86 || rib ? LEAF_DARK : color;
                if (color == LEAF_YELLOW && e > 0.86) {
                    t = LEAF;
                }
                if (vein) {
                    w.force(x, yy, z, t);
                } else {
                    w.set(x, yy, z, t);
                }
            }
        }
    }

    public static void leafPlant(Vox w, int ox, int oy, int oz, int seed, double scale) {
        Rng rng = new Rng(seed);
        int n = rng.nextInt(1, 3);
        byte color = rng.next() < 0.28 ? LEAF_YELLOW : LEAF;
        double a0 = rng.range(0, 6.283);
        for (int i = 0; i < n; i++) {
            double a = a0 + i * (6.283 / n) + rng.range(-0.5, 0.5), dx = cos(a), dz = sin(a);
            double h = rng.range(5, 12) * scale, bend = rng.range(1.5, 4) * scale, R = rng.range(3, 6.5) * scale;
            double px = ox + 0.5, py = oy, pz = oz + 0.5;
            int steps = 6;
            for (int k = 1; k <= steps; k++) {
                double t = (double) k / steps;
                double nx = ox + 0.5 + dx * bend * t * t, ny = oy + h * sin(t * Math.PI / 2), nz = oz + 0.5 + dz * bend * t * t;
                line(w, px, py, pz, nx, ny, nz, scale > 0.9 ? 0.75 : 0.45, STEM, true);
                px = nx;
                py = ny;
                pz = nz;
            }
            leafBlade(w, px, py, pz, dx, dz, R, rng.range(-0.15, 0.3), color, seed + i * 31);
        }
    }

    public static void flower(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double h = rng.range(8, 16), a = rng.range(0, 6.283), bend = rng.range(1, 3);
        double dx = cos(a), dz = sin(a);
        double px = ox + 0.5, py = oy, pz = oz + 0.5;
        for (int k = 1; k <= 8; k++) {
            double t = k / 8.0;
            double nx = ox + 0.5 + dx * bend * t * t, ny = oy + h * t, nz = oz + 0.5 + dz * bend * t * t;
            line(w, px, py, pz, nx, ny, nz, 0.8, STEM, true);
            px = nx;
            py = ny;
            pz = nz;
        }
        for (int i = 0; i < 2; i++) {
            double la = a + Math.PI * (i + 0.3) + rng.range(-0.4, 0.4);
            double ly = oy + h * rng.range(0.25, 0.55);
            leafBlade(w, ox + 0.5 + dx * bend * 0.1, ly, oz + 0.5 + dz * bend * 0.1, cos(la), sin(la), rng.range(2.2, 3.4), 0.25, LEAF,
                    seed + 7 * i);
        }
        int n = rng.nextInt(5, 6);
        double Rb = rng.range(3.5, 5.5), Hb = rng.range(4, 6), ph = rng.range(0, 6.283);
        for (int i = 0; i < n; i++) {
            double f = ph + i * 6.283 / n, fx = cos(f), fz = sin(f), qx = -fz, qz = fx;
            for (double s = 0; s <= 1.0001; s += 0.06) {
                double width = sin(Math.PI * Math.min(1, s * 1.1)) * Rb * 0.42 + 0.3;
                for (double b = -width; b <= width + 0.0001; b += 0.4) {
                    double rad = Rb * s, y = py + StrictMath.pow(s, 1.6) * Hb - StrictMath.pow(Math.max(0, s - 0.8), 2) * 6 + Math.abs(b) * 0.25;
                    int x = floor(px + fx * rad + qx * b), z = floor(pz + fz * rad + qz * b);
                    w.set(x, floor(y), z, s > 0.78 ? PETAL_TIP : PETAL);
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (x * x + y * y * 1.4 + z * z <= 3.2) {
                        w.force(floor(px + x), floor(py + y), floor(pz + z), CORE);
                    }
                }
            }
        }
    }

    public static void shrub(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double r = rng.range(1.4, 2.6), hgt = rng.range(0.8, 1.4);
        int R = (int) Math.ceil(r);
        for (int x = -R; x <= R; x++) {
            for (int y = 0; y <= R; y++) {
                for (int z = -R; z <= R; z++) {
                    double d = Math.sqrt(x * x + sq(y / hgt) + z * z);
                    if (d < r - 0.7 * hash3(ox + x, oy + y, oz + z, seed)) {
                        w.set(ox + x, oy + y, oz + z, hash3(ox + x, oy + y, oz + z, seed + 1) < 0.35 ? LEAF_DARK : LEAF);
                    }
                }
            }
        }
    }

    // ---- the stone toads ------------------------------------------------------------------------------------------

    static double sdEll(double px, double py, double pz, double cx, double cy, double cz, double rx, double ry, double rz) {
        double x = (px - cx) / rx, y = (py - cy) / ry, z = (pz - cz) / rz;
        double k = Math.sqrt(x * x + y * y + z * z);
        return (k - 1) * Math.min(rx, Math.min(ry, rz));
    }

    static double sdCapsule(double px, double py, double pz, double ax, double ay, double az, double bx, double by, double bz, double r) {
        double pax = px - ax, pay = py - ay, paz = pz - az, bax = bx - ax, bay = by - ay, baz = bz - az;
        double h = clamp((pax * bax + pay * bay + paz * baz) / (bax * bax + bay * bay + baz * baz), 0, 1);
        return Math.sqrt(sq(pax - bax * h) + sq(pay - bay * h) + sq(paz - baz * h)) - r;
    }

    static double smin(double a, double b, double k) {
        double h = clamp(0.5 + 0.5 * (b - a) / k, 0, 1);
        return b + (a - b) * h - k * h * (1 - h);
    }

    record Frog(double body, double fat, double headY, double reach) {
    }

    // A toad sitting in its own coordinates (front = +z, ground at y = 0); returns the distance, and whether the point
    // is a pupil or the mouth (dark).
    static double frogShape(Frog F, double x, double y, double z, boolean[] dark) {
        double f = F.fat, hy = F.headY;
        double d = sdEll(x, y, z, 0, 0.66 * F.body, -0.35, 1.05 * f, 0.62 * F.body, 1.0);
        d = smin(d, sdEll(x, y, z, 0, 0.95 * F.body, 0.22, 0.85 * f, 0.55 * F.body, 0.62), 0.2);
        d = smin(d, sdEll(x, y, z, 0, hy, 0.75, 0.95 * f, 0.42, 0.7), 0.16);
        boolean dk = false;
        for (int s = -1; s <= 1; s += 2) {
            d = smin(d, sdEll(x, y, z, s * 0.55 * f, hy + 0.43, 0.72, 0.3, 0.3, 0.3), 0.08);
            d = smin(d, sdCapsule(x, y, z, s * 0.6 * f, hy - 0.35, 0.62, s * 0.76 * f, 0.1, 0.98 + F.reach, 0.17), 0.1);
            d = smin(d, sdEll(x, y, z, s * 0.82 * f, 0.05, 1.12 + F.reach, 0.3, 0.08, 0.24), 0.06);
            d = smin(d, sdEll(x, y, z, s * 0.95 * f, 0.45 * F.body, -0.55, 0.42, 0.45 * F.body, 0.75), 0.14);
            d = smin(d, sdEll(x, y, z, s * 1.05 * f, 0.06, 0.15, 0.28, 0.08, 0.5), 0.08);
            if (sq(x - s * 0.62 * f) + sq(y - hy - 0.5) + sq(z - 0.97) < 0.03) {
                dk = true;
            }
        }
        if (Math.abs(y - (hy - 0.12)) < 0.065 && z > 0.6 && Math.abs(x) < 0.9 * f && d > -0.22) {
            dk = true;
        }
        if (dark != null) {
            dark[0] = dk;
        }
        return d;
    }

    public static void frog(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double S = rng.range(5.5, 9);
        double body = rng.range(0.9, 1.2), fat = rng.range(0.9, 1.15), reach = rng.range(-0.1, 0.2);
        double headY = 0.95 * body + 0.4 + rng.range(-0.05, 0.08);
        Frog F = new Frog(body, fat, headY, reach);
        double yaw = rng.range(0, 6.283), lookUp = rng.range(0, 0.35), c = cos(yaw), s = sin(yaw);
        double ck = cos(lookUp), sk = sin(lookUp), pivotY = 0.95 * F.body, pivotZ = 0.2;
        int R = (int) Math.ceil(S * 1.7), Hh = (int) Math.ceil(S * (F.headY + 1.0));
        boolean[] dark = new boolean[1];
        double[] l = new double[3];
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                for (int y = -1; y <= Hh; y++) {
                    local(x, y, z, S, c, s, ck, sk, pivotY, pivotZ, l);
                    double d = frogShape(F, l[0], l[1], l[2], dark);
                    if (d >= 0) {
                        continue;
                    }
                    boolean dk = dark[0];
                    local(x, y + 1, z, S, c, s, ck, sk, pivotY, pivotZ, l);
                    boolean top = frogShape(F, l[0], l[1], l[2], null) >= 0;
                    double n = valueNoise3(ox + x, oy + y, oz + z, seed + 3, 2.5);
                    byte t = dk ? FROG_DARK : FROG;
                    if (!dk && ((top && n > 0.58) || (y < 1 && n > 0.5))) {
                        t = FROG_MOSS;
                    }
                    w.force(ox + x, oy + y, oz + z, t);
                }
            }
        }
    }

    // To the toad's coordinates: turn around y; above the neck, tilt the head up.
    private static void local(int x, int y, int z, double S, double c, double s, double ck, double sk, double pivotY, double pivotZ,
            double[] out) {
        double wx = (x + 0.5) / S, wz = (z + 0.5) / S, wy = (y + 0.5) / S + 0.08;
        double lx = wx * c + wz * s, lz = -wx * s + wz * c, ly = wy;
        if (ly > pivotY && lz > -0.2) {
            double k = smooth(clamp((ly - pivotY) / 0.4, 0, 1));
            double ry = ly - pivotY, rz = lz - pivotZ, ckk = 1 - (1 - ck) * k, skk = sk * k;
            ly = pivotY + ry * ckk + rz * skk;
            lz = pivotZ - ry * skk + rz * ckk;
        }
        out[0] = lx;
        out[1] = ly;
        out[2] = lz;
    }

    // ---- the oil ponds --------------------------------------------------------------------------------------------

    public static void pool(Vox w, int ox, int oz, int seed, IntBinaryOperator ground) {
        Rng rng = new Rng(seed);
        double a = rng.range(5, 10), b = a * rng.range(0.6, 1), ang = rng.range(0, 6.283), depth = rng.range(2, 4.5);
        double c = cos(ang), s = sin(ang);
        int R = (int) Math.ceil(a * 1.35) + 1;
        int wl = Integer.MAX_VALUE;
        for (int x = ox - R; x <= ox + R; x++) {
            for (int z = oz - R; z <= oz + R; z++) {
                double e = ell(x, z, ox, oz, a, b, c, s, seed);
                if (e >= 1 && e < 1.3) {
                    wl = Math.min(wl, ground.applyAsInt(x, z));
                }
            }
        }
        wl -= 1;
        for (int x = ox - R; x <= ox + R; x++) {
            for (int z = oz - R; z <= oz + R; z++) {
                double e = ell(x, z, ox, oz, a, b, c, s, seed);
                int g = ground.applyAsInt(x, z);
                if (e < 1) {
                    int bot = Math.min(wl - 1, wl - floor(depth * (1 - e * e)) - 1 + (hash3(x, 0, z, seed + 3) < 0.3 ? 1 : 0));
                    for (int y = wl + 1; y <= g + 1; y++) {
                        w.force(x, y, z, CARVE);
                    }
                    for (int y = bot + 1; y <= wl; y++) {
                        w.force(x, y, z, WATER);
                    }
                    w.force(x, bot, z, hash3(x, 1, z, seed + 4) < 0.6 ? CLAY : DIRT);
                    if (e < 0.85 && hash3(x, 2, z, seed + 5) < 0.07) {
                        w.force(x, wl + 1, z, LILY);
                    }
                } else if (e < 1.3) {
                    for (int y = wl + 1; y <= g; y++) {
                        w.force(x, y, z, CARVE);
                    }
                    int m = Math.min(g, wl);
                    for (int y = m - 1; y <= m; y++) {
                        w.force(x, y, z, y == m && hash3(x, 3, z, seed + 6) < 0.75 ? MOSS : DIRT);
                    }
                }
            }
        }
    }

    private static double ell(int x, int z, int ox, int oz, double a, double b, double c, double s, int seed) {
        double dx = x + 0.5 - ox, dz = z + 0.5 - oz;
        double u = (dx * c + dz * s) / a, v = (-dx * s + dz * c) / b;
        return Math.sqrt(u * u + v * v) * (1 + 0.18 * (valueNoise(x, z, seed + 2, 5) - 0.5));
    }

    // ---- the ground and the cells ---------------------------------------------------------------------------------

    // The top of the ground: grass with patches of moss.
    public static byte groundTop(int x, int z, int seed) {
        return valueNoise(x, z, seed + 50, 9) * 0.7 + valueNoise(x, z, seed + 51, 3) * 0.3 > 0.62 ? MOSS : GRASS;
    }

    public enum Kind {
        SPIRE(60, 0.78, 10, 32, 1701, 0, false), POOL(60, 0.75, 16, 15, 1703, 1, false), FROG(44, 0.55, 12, 18, 1707, 2, false),
        FLOWER(24, 0.45, 5, 7, 1709, 3, true), LEAF(13, 0.5, 3, 8, 1711, 4, true), SHRUB(7, 0.4, 1, 3, 1713, 5, true);

        public final int size, pad, reach, salt, order;
        public final double chance;
        public final boolean plant;

        Kind(int size, double chance, int pad, int reach, int salt, int order, boolean plant) {
            this.size = size;
            this.chance = chance;
            this.pad = pad;
            this.reach = reach;
            this.salt = salt;
            this.order = order;
            this.plant = plant;
        }
    }

    public record Obj(Kind kind, int x, int z, int seed, int reach) {
    }

    static Obj cellObject(Kind c, int i, int j, int seed) {
        double h = hash3(i, j, c.salt, seed);
        if (h > c.chance) {
            return null;
        }
        int x = i * c.size + c.pad + floor(hash3(i, j, c.salt + 1, seed) * (c.size - 2 * c.pad));
        int z = j * c.size + c.pad + floor(hash3(i, j, c.salt + 2, seed) * (c.size - 2 * c.pad));
        return new Obj(c, x, z, floor(hash3(i, j, c.salt + 3, seed) * 2147483647), c.reach);
    }

    public static List<Obj> objectsNear(int x0, int z0, int x1, int z1, int seed) {
        List<Obj> out = new ArrayList<>();
        for (Kind c : Kind.values()) {
            int i0 = Math.floorDiv(x0 - c.reach, c.size), i1 = Math.floorDiv(x1 + c.reach, c.size);
            int j0 = Math.floorDiv(z0 - c.reach, c.size), j1 = Math.floorDiv(z1 + c.reach, c.size);
            for (int i = i0; i <= i1; i++) {
                for (int j = j0; j <= j1; j++) {
                    Obj o = cellObject(c, i, j, seed);
                    if (o != null) {
                        out.add(o);
                    }
                }
            }
        }
        return out;
    }

    public static boolean before(Obj p, Obj o) {
        return p.kind.order < o.kind.order || (p.kind.order == o.kind.order && (p.x < o.x || (p.x == o.x && p.z < o.z)));
    }

    // Who gives way (whatever chunk asks): a plant gives way to any piece that isn't a plant within its reach, and to a
    // plant of higher priority close by; the other pieces give way to one of higher priority close by.
    public static boolean yields(Obj o, List<Obj> cands) {
        boolean plant = o.kind.plant;
        for (Obj p : cands) {
            if (p == o) {
                continue;
            }
            double d2 = sq(p.x - o.x) + sq(p.z - o.z);
            if (plant && !p.kind.plant) {
                if (d2 < sq((p.kind == Kind.SPIRE ? 20 : p.reach) + (o.kind == Kind.SHRUB ? 1 : 3))) {
                    return true;
                }
                continue;
            }
            if (before(p, o) && d2 < sq((p.reach + o.reach) * (plant ? 0.45 : 0.6))) {
                return true;
            }
        }
        return false;
    }

    public static Vox build(Obj o, int gy, IntBinaryOperator ground) {
        Vox p = new Vox();
        switch (o.kind) {
            case SPIRE -> spire(p, o.x, o.z, o.seed, ground);
            case POOL -> pool(p, o.x, o.z, o.seed, ground);
            case FROG -> frog(p, o.x, gy + 1, o.z, o.seed);
            case FLOWER -> flower(p, o.x, gy + 1, o.z, o.seed);
            case LEAF -> leafPlant(p, o.x, gy + 1, o.z, o.seed, 1);
            case SHRUB -> shrub(p, o.x, gy + 1, o.z, o.seed);
        }
        return p;
    }
}
