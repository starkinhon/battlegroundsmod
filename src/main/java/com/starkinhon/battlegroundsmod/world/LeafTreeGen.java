package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

// The trees of the Floresta da Folha (Stark 2026-11-11: "quero árvores bem bonitas", after Tan's Huge Trees; he approved
// the preview "Floresta da Folha" built from /mnt/project-files/forja/naruto-floresta/gen.js). This is that generator
// line by line: the same steps in the same order with the same random (xorshift32 and the position hash), so a seed
// grows here the tree it grows in the preview. Change one, change the other.
// A tree grows in levels like in Tan's: trunk, thick boughs, branches, twigs; every piece walks block by block,
// thinning and bending a little each step; bark outside and stripped wood inside; leaves only in flat clusters at the
// tips (light gets through); roots arching out of the foot. The output is a set of voxels (Vox), placed by LeafForest.
public final class LeafTreeGen {
    public static final byte AIR = 0, BARK = 1, CORE = 2, LEAF = 3, LEAF_TIP = 4, MOSS = 5, LITTER = 8, FERN = 9, FLOWER = 11,
            LEAF_DARK = 12;
    private static final int[] PRIO = {0, 3, 4, 1, 1, 2, 0, 0, 0, 0, 0, 0, 1};

    private LeafTreeGen() {
    }

    // ---- random -------------------------------------------------------------------------------------------------

    static final class Rng {
        private int s;

        Rng(int seed) {
            s = seed != 0 ? seed : 0x9E3779B9;
        }

        double next() {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            return (s & 0xFFFFFFFFL) / 4294967296.0;
        }

        double range(double a, double b) {
            return a + (b - a) * next();
        }

        int nextInt(int a, int b) {
            return a + (int) Math.floor(next() * (b - a + 1));
        }
    }

    // A random number by position, so it doesn't depend on the order the blocks are set.
    public static double hash3(int x, int y, int z, int seed) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (seed * (int) 2654435761L);
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    public static double valueNoise(int x, int z, int seed, double scale) {
        double fx = x / scale, fz = z / scale;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = smooth(fx - ix), tz = smooth(fz - iz);
        double a = hash3(ix, 0, iz, seed) + (hash3(ix + 1, 0, iz, seed) - hash3(ix, 0, iz, seed)) * tx;
        double b = hash3(ix, 0, iz + 1, seed) + (hash3(ix + 1, 0, iz + 1, seed) - hash3(ix, 0, iz + 1, seed)) * tx;
        return a + (b - a) * tz;
    }

    private static double smooth(double a) {
        return a * a * (3 - 2 * a);
    }

    // ---- voxels -------------------------------------------------------------------------------------------------

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
            long k = key(x, y, z);
            Byte o = m.get(k);
            if (o == null || o == 0 || PRIO[t] >= PRIO[o]) {
                m.put(k, t);
            }
        }

        void force(int x, int y, int z, byte t) {
            if (t == 0) {
                m.remove(key(x, y, z));
            } else {
                m.put(key(x, y, z), t);
            }
        }

        // Packed as x, y, z, type per voxel.
        public int[] pack() {
            int[] out = new int[m.size() * 4];
            int i = 0;
            for (Map.Entry<Long, Byte> e : m.entrySet()) {
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

    private record V(double x, double y, double z) {
    }

    private record Step(V p, V d, double r, double t, int i) {
    }

    private interface OnStep {
        void at(int k, double t, V p, V d, double r);
    }

    private interface Grav {
        double at(double t);
    }

    private static V norm(V d) {
        double l = Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
        if (l == 0) {
            l = 1;
        }
        return new V(d.x / l, d.y / l, d.z / l);
    }

    private static V fromAngles(double yaw, double pitch) {
        return new V(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
    }

    // A thick disc (a ball) of wood: bark at the edge, stripped wood inside.
    private static void wood(Vox w, V c, double r) {
        int n = (int) Math.ceil(r + 0.5);
        double rr = (r + 0.35) * (r + 0.35), inner = (r - 0.9) * (r - 0.9);
        int cx = (int) Math.round(c.x), cy = (int) Math.round(c.y), cz = (int) Math.round(c.z);
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    double d2 = (x - c.x) * (x - c.x) + (y - c.y) * (y - c.y) + (z - c.z) * (z - c.z);
                    if (d2 > rr) {
                        continue;
                    }
                    w.set(x, y, z, r > 1.2 && d2 < inner ? CORE : BARK);
                }
            }
        }
    }

    // One piece of the tree (trunk, bough, branch, twig, root): walks block by block, thins from r0 to r1, bends a little
    // each step and feels "gravity" (positive rises, negative falls). onStep lets the next level grow from it. floorY (the
    // ground's top block per column) keeps roots from diving deep: they run along under the surface.
    private static List<Step> limb(Vox w, Rng rng, V start, V dir, int len, double r0, double r1, double curve, Grav grav,
            OnStep onStep, IntBinaryOperator floorY) {
        V p = new V(start.x, start.y, start.z), d = norm(dir);
        List<Step> path = new ArrayList<>();
        for (int i = 0; i <= len; i++) {
            double t = (double) i / Math.max(1, len), r = r0 + (r1 - r0) * t;
            wood(w, p, r);
            path.add(new Step(p, d, r, t, i));
            if (onStep != null) {
                onStep.at(i, t, p, d, r);
            }
            double nx = d.x + rng.range(-curve, curve);
            double ny = d.y + rng.range(-curve, curve) + grav.at(t);
            double nz = d.z + rng.range(-curve, curve);
            d = norm(new V(nx, ny, nz));
            p = new V(p.x + d.x, p.y + d.y, p.z + d.z);
            if (floorY != null) {
                int fy = floorY.applyAsInt((int) Math.round(p.x), (int) Math.round(p.z));
                if (p.y < fy - r * 0.6) {
                    p = new V(p.x, fy - r * 0.6, p.z);
                    d = norm(new V(d.x, Math.max(d.y, -0.05), d.z));
                }
            }
        }
        return path;
    }

    // A cluster of leaves: a flattened lens, dense in the middle and thin at the edge, with holes (light gets through).
    // The top lighter, the bottom darker.
    private static void clump(Vox w, V c, double rx, double ry, int seed) {
        int nx = (int) Math.ceil(rx), ny = (int) Math.ceil(ry);
        int cx = (int) Math.round(c.x), cy = (int) Math.round(c.y), cz = (int) Math.round(c.z);
        for (int dx = -nx; dx <= nx; dx++) {
            for (int dy = -ny; dy <= ny; dy++) {
                for (int dz = -nx; dz <= nx; dz++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    double ax = (x - c.x) / rx, ay = (y - c.y) / ry, az = (z - c.z) / rx;
                    double q = Math.sqrt(ax * ax + ay * ay + az * az);
                    if (q > 1) {
                        continue;
                    }
                    double keep = q < 0.6 ? 0.985 : 0.985 - (q - 0.6) * 2.1;
                    double h = hash3(x, y, z, seed);
                    if (h > keep) {
                        continue;
                    }
                    double top = (y - c.y) / ry;
                    w.set(x, y, z, top > 0.35 && h < 0.5 ? LEAF_TIP : (top < -0.45 ? LEAF_DARK : LEAF));
                }
            }
        }
    }

    // The GIANT tree: a straight thick trunk flaring at the foot with arching roots, thick boughs almost lying down (3
    // blocks wide, you can run on them), the crown in layers of clusters, an open crown on top. 46-58 blocks.
    public static void giantTree(Vox w, int ox, int oy, int oz, int seed, IntBinaryOperator floorY) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(46, 58);
        double R0 = rng.range(3.4, 4.2), R1 = 2.0;
        double lean = rng.range(0, Math.PI * 2);
        List<Step> trunk = limb(w, rng, new V(ox, oy - 2, oz), new V(Math.cos(lean) * 0.05, 1, Math.sin(lean) * 0.05), H, R0, R1,
                0.035, t -> 0.03, null, null);
        for (Step s : trunk) {
            wood(w, s.p, R0 + (R1 - R0) * Math.pow(s.t, 1.3));
        }
        // The foot flares out near the ground.
        for (int y = 0; y < 7; y++) {
            wood(w, new V(trunk.get(y + 2).p.x, oy + y, trunk.get(y + 2).p.z), R0 + (7 - y) * (7 - y) * 0.06);
        }
        // Roots arching out of the trunk and diving into the ground.
        int nr = rng.nextInt(5, 7);
        for (int i = 0; i < nr; i++) {
            double a = ((double) i / nr) * Math.PI * 2 + rng.range(-0.3, 0.3);
            double sy = oy + rng.range(2.5, 5.5);
            V s = new V(ox + Math.cos(a) * R0 * 0.8, sy, oz + Math.sin(a) * R0 * 0.8);
            V dir = new V(Math.cos(a), rng.range(0.3, 0.5), Math.sin(a));
            limb(w, rng, s, dir, rng.nextInt(11, 17), R0 * 0.58, 0.7, 0.06, t -> -0.09 - t * 0.04, null, floorY);
        }
        // The thick boughs (the paths up there).
        int nb = rng.nextInt(6, 8);
        double golden = 2.39996;
        double yaw = rng.range(0, Math.PI * 2);
        for (int i = 0; i < nb; i++) {
            double hf = 0.4 + ((double) i / (nb - 1)) * 0.46 + rng.range(-0.03, 0.03);
            Step at = trunk.get(Math.min(trunk.size() - 1, (int) Math.floor(hf * H) + 2));
            yaw += golden + rng.range(-0.25, 0.25);
            double r0 = Math.max(1.45, (R0 + (R1 - R0) * Math.pow(hf, 1.3)) * 0.55);
            int len = (int) Math.round(H * rng.range(0.42, 0.55) * (1.2 - 0.55 * hf));
            double pitch = rng.range(0.08, 0.22) + (hf - 0.4) * 0.6;
            Rng sub = new Rng(seed * 31 + i * 7 + 1);
            int[] side = {sub.next() < 0.5 ? 1 : -1};
            int[] nextBranch = {(int) Math.floor(len * sub.range(0.22, 0.3))};
            final int bi = i, blen = len;
            List<Step> bough = limb(w, rng, at.p, fromAngles(yaw, pitch), len, r0, 0.7, 0.05, t -> t < 0.5 ? 0.012 : -0.02,
                    (k, t, p, d, r) -> {
                        if (t > 0.5 && k % 4 == 1) {
                            clump(w, new V(p.x, p.y + r + 1.2, p.z), sub.range(2.8, 3.8), sub.range(1.6, 2.1), seed + k * 5 + bi * 211);
                        }
                        if (k != nextBranch[0]) {
                            return;
                        }
                        nextBranch[0] += sub.nextInt(3, 4);
                        side[0] = -side[0];
                        branch(w, sub, p, d, side[0], (int) Math.round((blen - k) * sub.range(0.45, 0.6)) + 5, Math.max(0.55, r * 0.6),
                                seed + k * 13 + bi);
                    }, null);
            Step end = bough.get(bough.size() - 1);
            clump(w, end.p, sub.range(4.2, 5.5), sub.range(2.3, 3.0), seed + i * 101);
        }
        // The top: 2 or 3 leaders opening upward, with the top crown.
        Step top = trunk.get(trunk.size() - 1);
        int nl = rng.nextInt(2, 3);
        for (int i = 0; i < nl; i++) {
            double a = yaw + i * (Math.PI * 2 / nl) + rng.range(-0.4, 0.4);
            Rng sub = new Rng(seed * 17 + i * 3 + 5);
            final int li = i;
            V dir = fromAngles(a, rng.range(0.85, 1.15));
            List<Step> lp = limb(w, rng, trunk.get(trunk.size() - 6).p, dir, rng.nextInt(8, 12), 1.2, 0.6, 0.07, t -> 0.02,
                    (k, t, p, d, r) -> {
                        if (k > 3 && k % 3 == 0) {
                            branch(w, sub, p, d, k % 2 != 0 ? 1 : -1, sub.nextInt(5, 8), 0.55, seed + k * 7 + li * 3);
                        }
                    }, null);
            V tip = lp.get(lp.size() - 1).p;
            double rx = rng.range(5, 6.5);
            clump(w, tip, rx, rng.range(2.8, 3.6), seed + 900 + i);
        }
        double rx = rng.range(6, 7);
        clump(w, new V(top.p.x, top.p.y + 2, top.p.z), rx, 3.6, seed + 999);
    }

    // A branch: goes out to the side, rises a little, puts out twigs and ends in clusters.
    private static void branch(Vox w, Rng rng, V at, V parentDir, int side, int len, double r0, int seed) {
        double yaw = Math.atan2(parentDir.z, parentDir.x) + side * rng.range(0.6, 1.1);
        double pitch = rng.range(0.2, 0.55);
        int[] n = {0};
        List<Step> path = limb(w, rng, at, fromAngles(yaw, pitch), len, r0, 0.5, 0.09, t -> t > 0.6 ? -0.03 : 0.0,
                (k, t, p, d, r) -> {
                    if (t > 0.45 && k % 3 == 0 && n[0] < 3) {
                        n[0]++;
                        V dir = fromAngles(yaw + (n[0] % 2 != 0 ? 1 : -1) * rng.range(0.7, 1.2), rng.range(0.1, 0.5));
                        List<Step> tw = limb(w, rng, p, dir, rng.nextInt(3, 5), 0.5, 0.5, 0.15, tt -> 0, null, null);
                        V tip = tw.get(tw.size() - 1).p;
                        double rx = rng.range(2.6, 3.6);
                        clump(w, tip, rx, rng.range(1.6, 2.2), seed + n[0] * 37);
                    }
                }, null);
        V tip = path.get(path.size() - 1).p;
        double rx = rng.range(3.2, 4.4);
        clump(w, tip, rx, rng.range(1.9, 2.6), seed + 77);
    }

    // The MEDIUM tree: the common oak of the forest, between the giants. A curved trunk, a wide low crown. 15-22 blocks.
    public static void mediumTree(Vox w, int ox, int oy, int oz, int seed, IntBinaryOperator floorY) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(15, 22);
        double R0 = rng.range(1.2, 1.7);
        double lean = rng.range(0, Math.PI * 2);
        List<Step> trunk = limb(w, rng, new V(ox, oy - 1, oz), new V(Math.cos(lean) * 0.2, 1, Math.sin(lean) * 0.2), H, R0, 0.6,
                0.08, t -> 0.02, null, null);
        for (int y = 0; y < 3; y++) {
            wood(w, new V(ox, oy + y, oz), R0 + (3 - y) * 0.35);
        }
        int nr = rng.nextInt(3, 4);
        for (int i = 0; i < nr; i++) {
            double a = ((double) i / nr) * Math.PI * 2 + rng.range(-0.4, 0.4);
            V dir = new V(Math.cos(a), 0.1, Math.sin(a));
            limb(w, rng, new V(ox, oy + 1.5, oz), dir, rng.nextInt(3, 6), R0 * 0.55, 0.5, 0.1, t -> -0.12, null, floorY);
        }
        int nb = rng.nextInt(4, 6);
        double yaw = rng.range(0, 6.28);
        for (int i = 0; i < nb; i++) {
            double hf = 0.45 + ((double) i / Math.max(1, nb - 1)) * 0.4;
            Step at = trunk.get(Math.min(trunk.size() - 1, (int) Math.floor(hf * H) + 1));
            yaw += 2.39996 + rng.range(-0.3, 0.3);
            Rng sub = new Rng(seed * 13 + i);
            int len = (int) Math.round(H * rng.range(0.35, 0.5));
            branch(w, sub, at.p, fromAngles(yaw, 0), 0, len, Math.max(0.55, R0 * 0.55), seed + i * 19);
        }
        V top = trunk.get(trunk.size() - 1).p;
        double rx = rng.range(4.5, 5.5);
        clump(w, new V(top.x, top.y + 1, top.z), rx, rng.range(2.6, 3.2), seed + 555);
    }

    // A fallen trunk, covered in moss.
    public static void fallenLog(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, 6.28);
        int len = rng.nextInt(12, 18);
        double r0 = rng.range(1.3, 1.7);
        limb(w, rng, new V(ox, oy + 1, oz), new V(Math.cos(a), 0, Math.sin(a)), len, r0, 1.0, 0.03, t -> 0, null, null);
    }

    public static void bush(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        double rx = rng.range(1.6, 2.6);
        clump(w, new V(x, y + 0.6, z), rx, rng.range(1.1, 1.6), seed);
    }

    // Moss on top of the thick boughs and the roots (only wood with air above it and wood under it).
    public static void moss(Vox w, int seed) {
        List<int[]> add = new ArrayList<>();
        for (Map.Entry<Long, Byte> e : w.m.entrySet()) {
            if (e.getValue() != BARK) {
                continue;
            }
            long k = e.getKey();
            int z = (int) (k % 4096) - 2048;
            long r = (k - (z + 2048)) / 4096;
            int y = (int) (r % 4096) - 256;
            int x = (int) ((r - (y + 256)) / 4096) - 2048;
            if (w.get(x, y + 1, z) != 0) {
                continue;
            }
            int below = w.get(x, y - 1, z);
            if (below != BARK && below != CORE) {
                continue;
            }
            if (hash3(x, y, z, seed + 3) < 0.55) {
                add.add(new int[] {x, y, z});
            }
        }
        for (int[] p : add) {
            w.force(p[0], p[1], p[2], MOSS);
        }
    }

    // ---- where things grow --------------------------------------------------------------------------------------

    // The world is split into cells; each cell draws (by the world seed) whether it holds a tree and where. Every piece
    // of the map works out on its own which trees touch it, even ones whose foot is in another piece.
    public enum Kind {
        GIANT(56, 0.8, 10, 41, 101), LOG(40, 0.25, 6, 20, 303), MEDIUM(20, 0.6, 3, 20, 202), BUSH(10, 0.35, 2, 4, 404);

        final int size, pad, reach, salt;
        final double chance;

        Kind(int size, double chance, int pad, int reach, int salt) {
            this.size = size;
            this.chance = chance;
            this.pad = pad;
            this.reach = reach;
            this.salt = salt;
        }

        public int reach() {
            return reach;
        }
    }

    public record Obj(Kind kind, int x, int z, int seed) {
    }

    static Obj cellObject(Kind c, int i, int j, int seed) {
        if (hash3(i, c.salt, j, seed) >= c.chance) {
            return null;
        }
        int span = c.size - 2 * c.pad;
        int x = i * c.size + c.pad + (int) Math.floor(hash3(i, c.salt + 1, j, seed) * span);
        int z = j * c.size + c.pad + (int) Math.floor(hash3(i, c.salt + 2, j, seed) * span);
        return new Obj(c, x, z, (int) Math.floor(hash3(i, c.salt + 3, j, seed) * 1e9) + 1);
    }

    // The others don't grow glued to a giant's trunk (9 blocks for a bush, 13 for the rest); each one checks the giants
    // around itself, so the answer is the same in every piece of the map.
    static boolean nearGiant(Obj o, int seed, Predicate<Obj> accept) {
        Kind c = Kind.GIANT;
        int d = o.kind == Kind.BUSH ? 9 : 13;
        for (int i = Math.floorDiv(o.x - d - c.size, c.size); i <= Math.floorDiv(o.x + d, c.size); i++) {
            for (int j = Math.floorDiv(o.z - d - c.size, c.size); j <= Math.floorDiv(o.z + d, c.size); j++) {
                Obj g = cellObject(c, i, j, seed);
                if (g != null && Math.hypot(g.x - o.x, g.z - o.z) < d && accept.test(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    // The trees of every kind that touch the box [x0,x1]x[z0,z1], in cell order (the same in every piece).
    public static List<Obj> objectsNear(int x0, int z0, int x1, int z1, int seed, Predicate<Obj> accept) {
        List<Obj> out = new ArrayList<>();
        for (Kind c : Kind.values()) {
            for (int i = Math.floorDiv(x0 - c.reach, c.size); i <= Math.floorDiv(x1 + c.reach, c.size); i++) {
                for (int j = Math.floorDiv(z0 - c.reach, c.size); j <= Math.floorDiv(z1 + c.reach, c.size); j++) {
                    Obj o = cellObject(c, i, j, seed);
                    if (o == null || o.x + c.reach < x0 || o.x - c.reach > x1 || o.z + c.reach < z0 || o.z - c.reach > z1) {
                        continue;
                    }
                    if (!accept.test(o) || (c != Kind.GIANT && nearGiant(o, seed, accept))) {
                        continue;
                    }
                    out.add(o);
                }
            }
        }
        return out;
    }

    // One tree's voxels, its foot at height y (the first air above the ground).
    public static Vox build(Obj o, int y, IntBinaryOperator floorY) {
        Vox w = new Vox();
        switch (o.kind) {
            case GIANT -> giantTree(w, o.x, y, o.z, o.seed, floorY);
            case MEDIUM -> mediumTree(w, o.x, y, o.z, o.seed, floorY);
            case LOG -> fallenLog(w, o.x, y - 1, o.z, o.seed);
            default -> bush(w, o.x, y, o.z, o.seed);
        }
        moss(w, o.seed);
        return w;
    }

    // The trail: a winding line where a wide noise crosses its middle (like a contour line); its width is measured in
    // blocks (distance to the line = offset / the noise's slope), so it doesn't swell where the noise is flat.
    public static boolean pathAt(int x, int z, int seed) {
        double n = valueNoise(x, z, seed + 7, 56) - 0.5;
        if (Math.abs(n) > 0.05) {
            return false;
        }
        double gx = valueNoise(x + 1, z, seed + 7, 56) - valueNoise(x - 1, z, seed + 7, 56);
        double gz = valueNoise(x, z + 1, seed + 7, 56) - valueNoise(x, z - 1, seed + 7, 56);
        double g = Math.hypot(gx, gz) / 2;
        return g > 1e-6 && Math.abs(n) / g < 1.3;
    }

    // What grows on the ground of a column: fallen leaves only under the crowns (in patches), ferns, a yellow flower in
    // the sun.
    public static byte groundDeco(int x, int z, boolean shaded, int seed) {
        double h = hash3(x, 7, z, seed);
        if (shaded && valueNoise(x, z, seed + 21, 6) > 0.6 && h < 0.75) {
            return LITTER;
        }
        if (h > 0.955) {
            return FERN;
        }
        if (h > 0.885 && h <= 0.9 && !shaded) {
            return FLOWER;
        }
        return AIR;
    }
}
