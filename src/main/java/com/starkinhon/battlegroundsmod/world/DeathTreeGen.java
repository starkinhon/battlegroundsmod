package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;

// What grows in the Floresta da Morte (Stark 2026-11-11, "que coisa linda", the approved preview built from
// /mnt/project-files/forja/naruto-morte/gen.js). That generator line by line, like LeafTreeGen for the Floresta da Folha:
// the same steps in the same order with the same random, so a seed grows here what it grows in the preview. Change one,
// change the other.
// The giants are bigger than the Folha's and twisted: a trunk of 3 or 4 strands winding round each other, knots, roots
// leaving it 8 to 13 blocks up and coming down in arches far out (the tunnels), crooked boughs that droop, a dark closed
// crown whose clusters sag at the edge, thick lianas falling to the ground and curtains of vines. Between them crooked
// half-dead trees, giant mushrooms, carnivorous flowers with teeth, fallen trunks, bushes.
public final class DeathTreeGen {
    public static final byte AIR = 0, BARK = 1, CORE = 2, LEAF = 3, LEAF_TIP = 4, MOSS = 5, GRASS = 6, DIRT = 7, LITTER = 8,
            FERN = 9, MUD = 10, WATER = 11, LEAF_DARK = 12, LIANA = 13, VINE = 14, STEM = 15, CAP = 16, SPOT = 17, GILL = 18,
            PETAL = 19, PETAL_SPOT = 20, MAW = 21, TOOTH = 22, GLOWSHROOM = 23, ROOTS = 24;
    private static final int[] PRIO = {0, 3, 4, 1, 1, 2, 0, 0, 0, 0, 0, 0, 1, 2, 0, 3, 3, 3, 3, 3, 3, 3, 3, 0, 0};

    private DeathTreeGen() {
    }

    private static double hash3(int x, int y, int z, int seed) {
        return LeafTreeGen.hash3(x, y, z, seed);
    }

    public static double valueNoise(int x, int z, int seed, double scale) {
        return LeafTreeGen.valueNoise(x, z, seed, scale);
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

    private static double sq(double a) {
        return a * a;
    }

    private static void wood(Vox w, V c, double r) {
        int n = (int) Math.ceil(r + 0.5);
        double rr = (r + 0.35) * (r + 0.35), inner = (r - 0.9) * (r - 0.9);
        int cx = (int) Math.round(c.x), cy = (int) Math.round(c.y), cz = (int) Math.round(c.z);
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    double d2 = sq(x - c.x) + sq(y - c.y) + sq(z - c.z);
                    if (d2 > rr) {
                        continue;
                    }
                    w.set(x, y, z, r > 1.2 && d2 < inner ? CORE : BARK);
                }
            }
        }
    }

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

    // A cluster of dark leaves that sags at the edge (a wilted umbrella), and vines hanging from its underside in tufts.
    private static void clump(Vox w, V c, double rx, double ry, int seed, double vines) {
        int nx = (int) Math.ceil(rx), ny = (int) Math.ceil(ry);
        int cx = (int) Math.round(c.x), cy = (int) Math.round(c.y), cz = (int) Math.round(c.z);
        for (int dx = -nx; dx <= nx; dx++) {
            for (int dy = -ny - (int) Math.ceil(ry); dy <= ny; dy++) {
                for (int dz = -nx; dz <= nx; dz++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    double flat = sq((x - c.x) / rx) + sq((z - c.z) / rx);
                    double yy = (y - c.y) + flat * ry * 0.9;
                    double q = Math.sqrt(flat + sq(yy / ry));
                    if (q > 1) {
                        continue;
                    }
                    double keep = q < 0.55 ? 0.97 : 0.97 - (q - 0.55) * 2.0;
                    double h = hash3(x, y, z, seed);
                    if (h > keep) {
                        continue;
                    }
                    double top = yy / ry;
                    w.set(x, y, z, top > 0.4 && h < 0.35 ? LEAF_TIP : (top < -0.3 ? LEAF_DARK : LEAF));
                    if (vines != 0 && top < -0.25 && hash3(x, 5, z, seed + 1) < vines && hash3(x >> 2, 8, z >> 2, seed + 2) < 0.45) {
                        int len = 2 + (int) Math.floor(Math.pow(hash3(x, 6, z, seed + 1), 3) * 16);
                        for (int k = 1; k <= len; k++) {
                            w.set(x, y - k, z, VINE);
                        }
                    }
                }
            }
        }
    }

    // A thick liana: from a bough down to the ground in a sagging curve, swinging a little to one side.
    private static void liana(Vox w, V a, V b, double sag, int seed) {
        int n = (int) Math.ceil(Math.sqrt(sq(b.x - a.x) + sq(b.y - a.y) + sq(b.z - a.z)) * 2.2);
        double sway = (hash3(seed, 1, 2, 9) - 0.5) * 3;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double x = a.x + (b.x - a.x) * t + Math.sin(t * Math.PI) * sway;
            double y = a.y + (b.y - a.y) * t - sag * 4 * t * (1 - t);
            double z = a.z + (b.z - a.z) * t + Math.sin(t * Math.PI) * sway * 0.6;
            w.set((int) Math.round(x), (int) Math.round(y), (int) Math.round(z), LIANA);
            if (hash3(i, 3, seed, 4) < 0.18) {
                w.set((int) Math.round(x) + 1, (int) Math.round(y), (int) Math.round(z), LIANA);
            }
        }
    }

    // The GIANT: 60 to 74 blocks.
    public static void giantTree(Vox w, int ox, int oy, int oz, int seed, IntBinaryOperator floorY) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(60, 74);
        double R0 = rng.range(4.6, 5.6), R1 = 2.2;
        double lean = rng.range(0, Math.PI * 2);
        // the middle line: crooked, but rising
        List<Step> mid = limb(new Vox(), rng, new V(ox, oy - 2, oz), new V(Math.cos(lean) * 0.08, 1, Math.sin(lean) * 0.08), H, 1, 1,
                0.07, t -> 0.035, null, null);
        int strands = rng.nextInt(3, 4);
        double twist = rng.range(1.2, 2.0) * (rng.next() < 0.5 ? 1 : -1);
        double phase = rng.range(0, Math.PI * 2);
        for (Step s : mid) {
            double R = trunkR(R0, R1, s.t);
            wood(w, s.p, R * 0.4);
            for (int k = 0; k < strands; k++) {
                double a = phase + k * Math.PI * 2 / strands + twist * s.t * Math.PI * 2;
                double off = R * 0.7 * (1 - 0.3 * s.t);
                wood(w, new V(s.p.x + Math.cos(a) * off, s.p.y, s.p.z + Math.sin(a) * off), R * 0.46);
            }
        }
        // knots
        int nk = rng.nextInt(4, 7);
        for (int i = 0; i < nk; i++) {
            Step s = mid.get((int) Math.floor(rng.range(0.08, 0.8) * H));
            double a = rng.range(0, Math.PI * 2), R = trunkR(R0, R1, s.t);
            double kx = s.p.x + Math.cos(a) * R * 0.8, ky = s.p.y + rng.range(-1, 1), kz = s.p.z + Math.sin(a) * R * 0.8;
            wood(w, new V(kx, ky, kz), rng.range(1.4, 2.4));
        }
        // the foot flares out
        for (int y = 0; y < 8; y++) {
            wood(w, new V(mid.get(y + 2).p.x, oy + y, mid.get(y + 2).p.z), R0 + (8 - y) * (8 - y) * 0.05);
        }
        // the tunnel roots
        int nr = rng.nextInt(4, 6);
        for (int i = 0; i < nr; i++) {
            double a = ((double) i / nr) * Math.PI * 2 + rng.range(-0.35, 0.35);
            double sy = oy + rng.range(8, 13);
            V s = new V(ox + Math.cos(a) * R0 * 0.7, sy, oz + Math.sin(a) * R0 * 0.7);
            int len = rng.nextInt(20, 28);
            V dir = new V(Math.cos(a), rng.range(0.05, 0.2), Math.sin(a));
            double r0 = rng.range(2.0, 2.6);
            List<Step> path = limb(w, rng, s, dir, len, r0, 0.9, 0.05, t -> -0.03 - t * 0.07, null, floorY);
            // a branch of the root comes down earlier, opening another arch
            Step at = path.get((int) Math.floor(len * rng.range(0.35, 0.5)));
            double ax = Math.cos(a + rng.range(-0.9, 0.9));
            double az = Math.sin(a + rng.range(-0.9, 0.9));
            limb(w, rng, at.p, new V(ax, -0.3, az), rng.nextInt(8, 12), at.r * 0.7, 0.6, 0.08, t -> -0.12, null, floorY);
        }
        // smaller roots along the ground
        int ns = rng.nextInt(5, 8);
        for (int i = 0; i < ns; i++) {
            double a = rng.range(0, Math.PI * 2);
            limb(w, rng, new V(ox + Math.cos(a) * R0, oy + 1, oz + Math.sin(a) * R0), new V(Math.cos(a), -0.05, Math.sin(a)),
                    rng.nextInt(7, 12), 1.3, 0.5, 0.12, t -> -0.08, null, floorY);
        }
        // the crooked boughs
        int nb = rng.nextInt(5, 7);
        double yaw = rng.range(0, Math.PI * 2);
        List<V> lianaFrom = new ArrayList<>();
        for (int i = 0; i < nb; i++) {
            double hf = 0.52 + ((double) i / (nb - 1)) * 0.36 + rng.range(-0.03, 0.03);
            Step at = mid.get(Math.min(mid.size() - 1, (int) Math.floor(hf * H) + 2));
            yaw += 2.39996 + rng.range(-0.3, 0.3);
            double r0 = Math.max(1.5, trunkR(R0, R1, hf) * 0.5);
            int len = (int) Math.round(H * rng.range(0.36, 0.48) * (1.2 - 0.5 * hf));
            Rng sub = new Rng(seed * 31 + i * 7 + 1);
            int[] side = {sub.next() < 0.5 ? 1 : -1};
            int[] nextBranch = {(int) Math.floor(len * sub.range(0.25, 0.35))};
            final int bi = i, blen = len;
            V dir = fromAngles(yaw, rng.range(0.15, 0.35));
            List<Step> bough = limb(w, rng, at.p, dir, len, r0, 0.7, 0.13, t -> t < 0.35 ? 0.0 : -0.045,
                    (k, t, p, d, r) -> {
                        if (t > 0.4 && k % 3 == 1) {
                            double rx = sub.range(3.2, 4.4);
                            clump(w, new V(p.x, p.y + r + 0.8, p.z), rx, sub.range(1.5, 2.0), seed + k * 5 + bi * 211, 0.07);
                        }
                        if (t > 0.3 && k % 5 == 2) {
                            lianaFrom.add(new V(p.x, p.y - r, p.z));
                        }
                        if (k != nextBranch[0]) {
                            return;
                        }
                        nextBranch[0] += sub.nextInt(4, 5);
                        side[0] = -side[0];
                        branch(w, sub, p, d, side[0], (int) Math.round((blen - k) * sub.range(0.45, 0.6)) + 5, Math.max(0.55, r * 0.6),
                                seed + k * 13 + bi);
                    }, null);
            Step end = bough.get(bough.size() - 1);
            double rx = sub.range(4.6, 6.0);
            clump(w, end.p, rx, sub.range(2.2, 2.8), seed + i * 101, 0.1);
        }
        // the top: a wide low crown closing the sky
        Step top = mid.get(mid.size() - 1);
        int nl = rng.nextInt(3, 4);
        for (int i = 0; i < nl; i++) {
            double a = yaw + i * (Math.PI * 2 / nl) + rng.range(-0.4, 0.4);
            Rng sub = new Rng(seed * 17 + i * 3 + 5);
            final int li = i;
            V dir = fromAngles(a, rng.range(0.4, 0.75));
            List<Step> lp = limb(w, rng, mid.get(mid.size() - 8).p, dir, rng.nextInt(10, 15), 1.5, 0.6, 0.1, t -> -0.015,
                    (k, t, p, d, r) -> {
                        if (k > 3 && k % 3 == 0) {
                            branch(w, sub, p, d, k % 2 != 0 ? 1 : -1, sub.nextInt(5, 8), 0.55, seed + k * 7 + li * 3);
                        }
                    }, null);
            double rx = rng.range(6, 7.5);
            clump(w, lp.get(lp.size() - 1).p, rx, rng.range(2.6, 3.2), seed + 900 + i, 0.08);
        }
        double rx = rng.range(7, 8);
        clump(w, new V(top.p.x, top.p.y + 1, top.p.z), rx, 3.2, seed + 999, 0.05);
        // the thick lianas: from the boughs to the ground, away from the trunk
        for (int i = 0; i < lianaFrom.size(); i++) {
            if (hash3(i, 11, seed, 3) > 0.55) {
                continue;
            }
            V a = lianaFrom.get(i);
            double da = hash3(i, 12, seed, 3) * Math.PI * 2, dd = 3 + hash3(i, 13, seed, 3) * 8;
            int bx = (int) Math.round(a.x + Math.cos(da) * dd), bz = (int) Math.round(a.z + Math.sin(da) * dd);
            liana(w, a, new V(bx, floorY.applyAsInt(bx, bz), bz), 2 + hash3(i, 14, seed, 3) * 4, seed + i * 7);
        }
    }

    private static double trunkR(double r0, double r1, double t) {
        return r0 + (r1 - r0) * Math.pow(t, 1.1);
    }

    // A crooked branch: out to the side, rising little, drooping at the tip.
    private static void branch(Vox w, Rng rng, V at, V parentDir, int side, int len, double r0, int seed) {
        double yaw = Math.atan2(parentDir.z, parentDir.x) + side * rng.range(0.6, 1.2);
        double pitch = rng.range(0.1, 0.4);
        int[] n = {0};
        List<Step> path = limb(w, rng, at, fromAngles(yaw, pitch), len, r0, 0.5, 0.16, t -> t > 0.5 ? -0.05 : 0.0,
                (k, t, p, d, r) -> {
                    if (t > 0.45 && k % 3 == 0 && n[0] < 3) {
                        n[0]++;
                        V dir = fromAngles(yaw + (n[0] % 2 != 0 ? 1 : -1) * rng.range(0.7, 1.2), rng.range(-0.1, 0.4));
                        List<Step> tw = limb(w, rng, p, dir, rng.nextInt(3, 5), 0.5, 0.5, 0.2, tt -> -0.02, null, null);
                        double rx = rng.range(2.8, 3.8);
                        clump(w, tw.get(tw.size() - 1).p, rx, rng.range(1.4, 1.9), seed + n[0] * 37, 0.08);
                    }
                }, null);
        double rx = rng.range(3.4, 4.6);
        clump(w, path.get(path.size() - 1).p, rx, rng.range(1.7, 2.3), seed + 77, 0.1);
    }

    // A medium tree: crooked, half dead, a thin crown. 16 to 26 blocks.
    public static void mediumTree(Vox w, int ox, int oy, int oz, int seed, IntBinaryOperator floorY) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(16, 26);
        double R0 = rng.range(1.3, 1.9);
        double lean = rng.range(0, Math.PI * 2);
        List<Step> trunk = limb(w, rng, new V(ox, oy - 1, oz), new V(Math.cos(lean) * 0.3, 1, Math.sin(lean) * 0.3), H, R0, 0.6,
                0.13, t -> 0.03, null, null);
        for (int y = 0; y < 3; y++) {
            wood(w, new V(ox, oy + y, oz), R0 + (3 - y) * 0.4);
        }
        int nr = rng.nextInt(3, 5);
        for (int i = 0; i < nr; i++) {
            double a = ((double) i / nr) * Math.PI * 2 + rng.range(-0.4, 0.4);
            limb(w, rng, new V(ox, oy + 2.5, oz), new V(Math.cos(a), 0.15, Math.sin(a)), rng.nextInt(4, 8), R0 * 0.6, 0.5, 0.12,
                    t -> -0.14, null, floorY);
        }
        int nb = rng.nextInt(3, 5);
        double yaw = rng.range(0, 6.28);
        for (int i = 0; i < nb; i++) {
            double hf = 0.4 + ((double) i / Math.max(1, nb - 1)) * 0.45;
            Step at = trunk.get(Math.min(trunk.size() - 1, (int) Math.floor(hf * H) + 1));
            yaw += 2.39996 + rng.range(-0.3, 0.3);
            Rng sub = new Rng(seed * 13 + i);
            int len = (int) Math.round(H * rng.range(0.3, 0.45));
            branch(w, sub, at.p, fromAngles(yaw, 0), 0, len, Math.max(0.55, R0 * 0.5), seed + i * 19);
        }
        V top = trunk.get(trunk.size() - 1).p;
        double rx = rng.range(3.6, 4.6);
        clump(w, new V(top.x, top.y + 1, top.z), rx, rng.range(2.0, 2.6), seed + 555, 0.1);
    }

    // A giant mushroom: a curved pale stem, a rust-red dome cap with pale spots and gills underneath.
    public static void mushroom(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(5, 11);
        double rs = rng.range(0.8, 1.3);
        double lean = rng.range(0, 6.28);
        V p = new V(ox, oy, oz), d = new V(Math.cos(lean) * 0.25, 1, Math.sin(lean) * 0.25);
        for (int i = 0; i <= H; i++) {
            double r = rs * (1.25 - 0.25 * i / H);
            int n = (int) Math.ceil(r + 0.5);
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    if (dx * dx + dz * dz <= sq(r + 0.35)) {
                        w.set((int) Math.round(p.x) + dx, (int) Math.round(p.y), (int) Math.round(p.z) + dz, STEM);
                    }
                }
            }
            double nx = d.x + rng.range(-0.08, 0.08);
            double nz = d.z + rng.range(-0.08, 0.08);
            d = norm(new V(nx, d.y, nz));
            p = new V(p.x + d.x, p.y + d.y, p.z + d.z);
        }
        double rc = rng.range(3.2, 5.4), hc = rc * rng.range(0.5, 0.7);
        int cx = (int) Math.round(p.x), cy = (int) Math.round(p.y), cz = (int) Math.round(p.z);
        int n = (int) Math.ceil(rc + 1);
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                for (int dy = -1; dy <= Math.ceil(hc) + 1; dy++) {
                    double q = Math.sqrt((dx * dx + dz * dz) / (rc * rc) + (dy * dy) / (hc * hc));
                    if (dy >= 0 && q <= 1 && q > 1 - 1.6 / rc) {
                        int x = cx + dx, y = cy + dy, z = cz + dz;
                        w.set(x, y, z, hash3(x, y, z, seed) < 0.13 && q > 0.85 ? SPOT : CAP);
                    } else if (dy == -1 && dx * dx + dz * dz <= sq(rc - 0.4) && dx * dx + dz * dz > sq(rs + 0.8)) {
                        w.set(cx + dx, cy, cz + dz, GILL);
                    } else if (dy == 0 && q > 0.92 && q <= 1.08) {
                        w.set(cx + dx, cy - 1, cz + dz, CAP);
                    }
                }
            }
        }
    }

    // A big carnivorous flower: five or six thick petals lying on the ground (dark red, pale spots) curling up at the
    // tips, and in the middle the mouth, a deep cup with pale teeth on its rim.
    public static void flower(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double s = rng.range(1.2, 1.7);
        int np = rng.nextInt(5, 6);
        double rot = rng.range(0, 6.28);
        for (int i = 0; i < np; i++) {
            double a = rot + i * Math.PI * 2 / np;
            double px = ox + Math.cos(a) * 2.6 * s, pz = oz + Math.sin(a) * 2.6 * s;
            double lr = 2.0 * s, wr = 1.35 * s;
            int n = (int) Math.ceil(lr + 1);
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    int x = (int) Math.round(px) + dx, z = (int) Math.round(pz) + dz;
                    double u = (x - px) * Math.cos(a) + (z - pz) * Math.sin(a), v = -(x - px) * Math.sin(a) + (z - pz) * Math.cos(a);
                    double q = sq(u / lr) + sq(v / wr);
                    if (q > 1) {
                        continue;
                    }
                    int y = oy + (u > lr * 0.55 ? 1 : 0);
                    w.set(x, y, z, hash3(x, y, z, seed) < 0.16 ? PETAL_SPOT : PETAL);
                    if (u > lr * 0.8 && q > 0.5) {
                        w.set(x, y + 1, z, PETAL);
                    }
                }
            }
        }
        double rm = 1.8 * s;
        int hm = (int) Math.round(2 * s) + 1;
        int n = (int) Math.ceil(rm + 1);
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rm + 0.4) {
                    continue;
                }
                int x = ox + dx, z = oz + dz;
                for (int y = 0; y < hm; y++) {
                    w.set(x, oy + y, z, d < rm - 0.9 ? (y == 0 ? MAW : AIR) : PETAL);
                }
                if (d < rm - 0.9) {
                    for (int y = 0; y < hm; y++) {
                        w.force(x, oy + y, z, AIR);
                    }
                    w.force(x, oy - 1, z, MAW);
                } else if (hash3(x, 9, z, seed) < 0.5) {
                    w.set(x, oy + hm, z, TOOTH);
                }
            }
        }
    }

    public static void fallenLog(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, 6.28);
        int len = rng.nextInt(14, 22);
        double r0 = rng.range(1.5, 2.1);
        limb(w, rng, new V(ox, oy + 1, oz), new V(Math.cos(a), 0, Math.sin(a)), len, r0, 1.1, 0.06, t -> 0, null, null);
    }

    public static void bush(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        double rx = rng.range(1.6, 2.6);
        clump(w, new V(x, y + 0.6, z), rx, rng.range(1.0, 1.5), seed, 0);
    }

    // Moss on the backs of the boughs and roots, more than in the Folha (it's damp).
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
            if (hash3(x, y, z, seed + 3) < 0.5) {
                add.add(new int[] {x, y, z});
            }
        }
        for (int[] p : add) {
            w.force(p[0], p[1], p[2], MOSS);
        }
    }

    // ---- where things grow --------------------------------------------------------------------------------------

    public enum Kind {
        GIANT(50, 0.85, 9, 46, 111), LOG(36, 0.3, 6, 24, 313), MEDIUM(18, 0.5, 3, 20, 212), MUSHROOM(22, 0.4, 4, 7, 515),
        FLOWER(34, 0.35, 6, 6, 616), BUSH(9, 0.4, 2, 4, 414);

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

    // Nothing grows glued to a giant's trunk (inside the tunnel roots it would be stuck): 8 blocks for a bush, 16 for
    // the rest. Each one checks the giants around it itself, so the answer is the same in any piece of the map.
    static boolean nearGiant(Obj o, int seed, Predicate<Obj> accept) {
        Kind c = Kind.GIANT;
        int d = o.kind == Kind.BUSH ? 8 : 16;
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

    // One object's voxels, its foot at height y (the first air above the ground).
    public static Vox build(Obj o, int y, IntBinaryOperator floorY) {
        Vox w = new Vox();
        switch (o.kind) {
            case GIANT -> giantTree(w, o.x, y, o.z, o.seed, floorY);
            case MEDIUM -> mediumTree(w, o.x, y, o.z, o.seed, floorY);
            case LOG -> fallenLog(w, o.x, y - 1, o.z, o.seed);
            case MUSHROOM -> mushroom(w, o.x, y, o.z, o.seed);
            case FLOWER -> flower(w, o.x, y, o.z, o.seed);
            default -> bush(w, o.x, y, o.z, o.seed);
        }
        if (o.kind != Kind.MUSHROOM && o.kind != Kind.FLOWER) {
            moss(w, o.seed);
        }
        return w;
    }

    // Still water: patches where a wide noise runs low. The water sits a block below the ground around it, mud at the
    // bottom and on the shore.
    public static double poolAt(int x, int z, int seed) {
        return valueNoise(x, z, seed + 5, 22) + (valueNoise(x, z, seed + 6, 7) - 0.5) * 0.12;
    }

    public static final double POOL = 0.22, SHORE = 0.26;

    // What grows on the ground: rotten leaves in patches, ferns, small mushrooms glowing faintly, roots showing.
    public static byte groundDeco(int x, int z, boolean shaded, int seed) {
        double h = hash3(x, 7, z, seed);
        if (valueNoise(x, z, seed + 21, 5) > 0.55 && h < 0.7) {
            return LITTER;
        }
        if (h > 0.93) {
            return FERN;
        }
        if (h > 0.905 && shaded) {
            return GLOWSHROOM;
        }
        if (h > 0.87 && h <= 0.88) {
            return ROOTS;
        }
        return 0;
    }
}
