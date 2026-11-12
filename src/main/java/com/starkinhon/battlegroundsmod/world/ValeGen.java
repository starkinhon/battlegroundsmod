package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import com.starkinhon.battlegroundsmod.world.DesertGen.Vox;
import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Keys;
import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;

// The Vale do Fim (Stark 2026-11-12, "a melhor construção do mod", the approved preview built from
// /mnt/project-files/forja/naruto-vale/gen.js). That generator line by line, like RainGen: the same steps in the same
// order with the same random, so a seed makes here what it makes in the preview. Change one, change the other.
// Shuumatsu no Tani: a massif of layered rock, covered in woods, cut by a river. Up top the river runs wide and shallow
// to the edge and drops 30 blocks into a deep lake walled by cliffs. On both sides of the fall, standing on stone
// promontories, the giant statues of Hashirama and Madara face each other over the water, each with a hand raised in the
// seal of confrontation. From the lake the river goes on through a gorge whose walls come down until it ends on a gravel
// beach. Around it, over the rest of the biome, the marks of the fight: craters, cuts in the ground, rocks of columns,
// boulders and the woods.
// The massif is built on top of the game's relief and blends into it at the edge; all the water sits in closed basins.
// The sines and cosines are StrictMath's, which is what the preview's engine computes, so the parity holds bit for bit.
public final class ValeGen {
    public static final byte AIR = 0, STONE = 1, ANDESITE = 2, TUFF = 3, COBBLE = 4, MOSSY = 5, MOSS = 6, GRASS = 7, DIRT = 8,
            GRAVEL = 9, SAND = 10, WATER = 11, FALL = 12, LOG = 13, LEAVES = 14, PINE_LOG = 15, PINE = 16, STATUE = 17,
            STATUE_ALT = 18, STATUE_MOSS = 19, STATUE_DARK = 20, COARSE = 21, CARVE = 22, LEAVES_DARK = 23, VINE = 24;
    public static final int NONE = -1_000_000_000;

    private ValeGen() {
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

    // ---- shapes (signed distance) ---------------------------------------------------------------------------------

    static double sdCapsule(double px, double py, double pz, double ax, double ay, double az, double bx, double by, double bz,
            double ra, double rb) {
        double pax = px - ax, pay = py - ay, paz = pz - az, bax = bx - ax, bay = by - ay, baz = bz - az;
        double h = clamp((pax * bax + pay * bay + paz * baz) / (bax * bax + bay * bay + baz * baz), 0, 1);
        return Math.sqrt(sq(pax - bax * h) + sq(pay - bay * h) + sq(paz - baz * h)) - (ra + (rb - ra) * h);
    }

    static double sdEll(double px, double py, double pz, double cx, double cy, double cz, double rx, double ry, double rz) {
        double k = Math.sqrt(sq((px - cx) / rx) + sq((py - cy) / ry) + sq((pz - cz) / rz));
        return (k - 1) * Math.min(rx, Math.min(ry, rz));
    }

    static double sdBox(double px, double py, double pz, double cx, double cy, double cz, double bx, double by, double bz,
            double r) {
        double qx = Math.abs(px - cx) - bx + r, qy = Math.abs(py - cy) - by + r, qz = Math.abs(pz - cz) - bz + r;
        return Math.sqrt(sq(Math.max(qx, 0)) + sq(Math.max(qy, 0)) + sq(Math.max(qz, 0))) + Math.min(Math.max(qx, Math.max(qy, qz)), 0)
                - r;
    }

    static double smin(double a, double b, double k) {
        double h = clamp(0.5 + 0.5 * (b - a) / k, 0, 1);
        return b + (a - b) * h - k * h * (1 - h);
    }

    // ---- the statues ----------------------------------------------------------------------------------------------

    // A statue 52 blocks tall, in blocks: y = 0 at the feet, facing +z, the raised arm on the +x side. The armour (cuirass,
    // skirt of plates, shoulder plates, greaves, bracers, collar) is one stone; the bare body and the hair the other.
    // who 0 = Hashirama: long straight hair, parted, falling down the back and two locks in front.
    // who 1 = Madara: a huge mane of spikes down the back, spiky fringe and a long lock over the eye.
    public static final int STATUE_H = 52;
    private static final double[][] MANE;

    static {
        // the spikes of Madara's mane: always the same
        Rng rng = new Rng(0x4D414441);
        List<double[]> out = new ArrayList<>();
        for (int k = 0; k < 26; k++) {
            double t = rng.range(-0.4, Math.PI + 0.4), up = sin(t), side = cos(t);
            double bx = side * rng.range(3.5, 6.0), by = 39 + up * rng.range(4.5, 8.5), bz = rng.range(-5.6, -4.0);
            double len = rng.range(3.2, 6.5);
            double dx = side * 0.75, dy = up * 0.35 - 0.62, dz = -0.45;
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            out.add(new double[] {bx, by, bz, bx + dx / dl * len, by + dy / dl * len, bz + dz / dl * len, rng.range(1.2, 1.9)});
        }
        for (int k = 0; k < 9; k++) {
            // the bottom points, on the back
            double x = -6.5 + k * 1.6 + rng.range(-0.4, 0.4), y = rng.range(26.5, 29.5);
            double y1 = y - rng.range(1.5, 4);
            double z1 = -4.9 - rng.range(0, 0.8);
            out.add(new double[] {x, y + 4, -4.6, x * 1.1, y1, z1, rng.range(1.0, 1.5)});
        }
        MANE = out.toArray(new double[0][]);
    }

    // Distance and part (0 armour, 1 body/hair, 2 dark: the belt, the eyes and the mouth) of the local point.
    private static final class Sdf {
        double d = 1e9;
        int part;

        void arm(double v) {
            if (v < d) {
                d = v;
                part = 0;
            }
        }

        void body(double v) {
            if (v < d) {
                d = v;
                part = 1;
            }
        }
    }

    static Sdf statueSdf(double x, double y, double z, int who) {
        Sdf r = new Sdf();
        double ax = Math.abs(x);
        // the stone base
        r.arm(sdBox(x, y, z, 0, -1.2, 0.3, 8.6, 1.3, 6.4, 1.0));
        // feet and greaves
        r.arm(sdBox(ax, y, z, 4.6, 1.1, 1.3, 2.1, 1.1, 3.3, 0.7));
        r.arm(sdCapsule(ax, y, z, 4.6, 2.2, 0.0, 4.3, 12.6, 0.2, 2.4, 3.0));
        r.arm(sdEll(ax, y, z, 4.3, 13.4, 1.1, 2.7, 2.0, 2.4));
        // thighs and hips
        r.body(sdCapsule(ax, y, z, 4.2, 14.0, 0.0, 3.4, 24.0, 0.0, 3.2, 3.3));
        r.body(sdBox(x, y, z, 0, 25.2, 0, 5.4, 2.0, 3.4, 1.5));
        // the skirt of plates (kusazuri): an elliptic cone in overlapping lames, split in front, behind and at the sides
        if (y > 11.0 && y < 28.0) {
            double t = (y - 11.4) / 16.6;
            double lame = 0.85 * frac((28.0 - y) / 2.4);
            double rx = 9.0 - t * 3.2 + lame, rz = rx * 0.66;
            double k = Math.sqrt(sq(x / rx) + sq(z / rz));
            double ds = (k - 1) * rz;
            ds = Math.max(ds, 11.4 - y);
            if (ds < 0 && ds > -1.1 && (Math.abs(x) < 0.55 || Math.abs(z) < 0.5)) {
                ds = 0.3;
            }
            r.arm(ds);
        }
        // the cuirass: chest and the lames of the belly
        {
            double dt = sdBox(x, y, z, 0, 34.0, 0.3, 5.8, 6.4, 3.7, 2.2);
            dt = smin(dt, sdEll(x, y, z, 0, 35.6, 1.1, 5.5, 4.2, 3.5), 1.2);
            if (y < 31.6 && z > 0) {
                dt -= 0.55 * frac((31.6 - y) / 1.8);
            }
            r.arm(dt);
        }
        // the belt (obi), darker
        {
            double db = sdBox(x, y, z, 0, 28.3, 0.3, 6.0, 0.95, 3.95, 0.8);
            if (db < r.d) {
                r.d = db;
                r.part = 2;
            }
        }
        // shoulders and shoulder plates (sode), hanging outward, in lames
        r.body(sdEll(ax, y, z, 5.8, 39.2, 0.0, 2.8, 2.2, 2.6));
        {
            double c = 0.966, s = 0.259; // 15° outward
            double qx = ax - 8.3, qy = y - 36.2;
            double lx = qx * c - qy * s, ly = qx * s + qy * c;
            double lame = 0.45 * frac((4.4 - ly) / 2.2);
            r.arm(sdBox(lx, ly, z, 0, 0, 0.2, 1.0 + lame, 4.3, 3.5, 0.6));
        }
        // neck, collar and head
        r.body(sdCapsule(x, y, z, 0, 40.0, 0.0, 0, 43.4, 0.3, 1.8, 1.7));
        r.arm(sdEll(x, y, z, 0, 40.8, 0.2, 3.5, 1.2, 2.9));
        {
            double dh = sdEll(x, y, z, 0, 46.8, 0.4, 3.3, 3.9, 3.4);
            dh = smin(dh, sdEll(x, y, z, 0, 44.6, 1.0, 2.3, 1.9, 2.4), 0.8);
            dh = Math.min(dh, sdBox(x, y, z, 0, 46.3, 3.7, 0.5, 1.1, 0.6, 0.2));
            dh = Math.min(dh, sdBox(x, y, z, 0, 47.9, 3.3, 2.5, 0.45, 0.6, 0.2));
            dh = Math.min(dh, sdEll(ax, y, z, 3.0, 46.5, 0.4, 0.55, 1.1, 0.75));
            dh = Math.max(dh, -sdEll(ax, y, z, 1.3, 47.0, 3.7, 0.8, 0.55, 0.7));
            // the deep eyes and the mouth: the shadow that makes the face
            if (dh < 0 && dh > -1.2 && ((ax > 0.7 && ax < 2.0 && y > 46.4 && y < 47.6 && z > 2.4) || (ax < 1.1 && y > 43.9 && y < 44.6 && z > 2.6))) {
                if (dh < r.d) {
                    r.d = dh;
                    r.part = 2;
                }
                dh = 1e9;
            }
            r.body(dh);
        }
        if (x < 0) {
            // the arm that hangs (side -x) and the closed fist
            r.body(sdCapsule(x, y, z, -6.5, 38.5, 0.0, -8.0, 30.5, 0.2, 2.1, 2.1));
            r.arm(sdCapsule(x, y, z, -8.0, 30.5, 0.2, -8.6, 22.8, 0.9, 1.9, 2.2));
            r.body(sdEll(x, y, z, -8.7, 21.2, 1.0, 1.5, 2.0, 1.6));
        } else {
            // the raised arm: the forearm comes up in front of the chest and the hand stands, two fingers up
            r.body(sdCapsule(x, y, z, 6.4, 38.5, 0.0, 8.0, 31.6, 3.2, 2.1, 2.1));
            r.arm(sdCapsule(x, y, z, 8.0, 31.6, 3.2, 3.0, 39.0, 7.0, 2.0, 1.75));
            r.body(sdBox(x, y, z, 2.1, 41.0, 7.3, 0.75, 1.6, 1.45, 0.4));
            r.body(sdCapsule(x, y, z, 1.9, 42.4, 7.9, 1.9, 45.4, 8.0, 0.68, 0.6));
            r.body(sdCapsule(x, y, z, 1.9, 42.4, 6.8, 1.9, 45.0, 6.7, 0.68, 0.6));
            r.body(sdEll(x, y, z, 2.7, 41.7, 7.4, 1.0, 1.15, 1.35));
        }
        // the hair
        if (who == 0) {
            double dc = sdEll(x, y, z, 0, 47.4, 0.0, 3.75, 4.15, 3.75);
            if (z > 0.6 && y < 48.4 && ax < 2.8) {
                dc = Math.max(dc, 0.4);
            }
            if (ax < 0.45 && y > 49.8 && z > -1.5) {
                dc = Math.max(dc, 0.3);
            }
            r.body(dc);
            // the curtain down the back, widening downward, with uneven ends
            if (y < 47) {
                double hx = 3.0 + Math.max(0, 47 - y) * 0.085, zc = -3.8 - Math.max(0, 44 - y) * 0.03;
                double bottom = 26.2 + 2.6 * hash3(floor(x), 0, 0, 77);
                double dk = sdBox(x, y, z, 0, 36.8, zc, hx, 10.4, 0.95, 0.7);
                if (y < bottom) {
                    dk = Math.max(dk, 0.3);
                }
                r.body(dk);
            }
            r.body(sdCapsule(ax, y, z, 2.9, 46.8, 1.6, 3.6, 38.6, 2.9, 1.0, 0.8));
        } else {
            double dc = sdEll(x, y, z, 0, 47.5, -0.1, 3.95, 4.4, 3.95);
            if (z > 0.9 && y < 48.0 && ax < 2.8) {
                dc = Math.max(dc, 0.4);
            }
            r.body(dc);
            r.body(sdEll(x, y, z, 0, 40.5, -4.4, 6.4, 8.6, 3.1));
            r.body(sdBox(x, y, z, 0, 31.5, -4.3, 6.2 + Math.max(0, 34 - y) * 0.08, 5.5, 1.6, 1.0));
            for (double[] m : MANE) {
                r.body(sdCapsule(x, y, z, m[0], m[1], m[2], m[3], m[4], m[5], m[6], 0.25));
            }
            // the mane at the sides, over the ears down to the shoulders
            r.body(sdCapsule(ax, y, z, 2.9, 47.2, -0.2, 4.7, 40.2, -0.8, 1.7, 0.6));
            // the spiky fringe and the long lock over the right eye
            for (int k = 0; k < 5; k++) {
                double bx = -2.0 + k;
                r.body(sdCapsule(x, y, z, bx, 50.0, 1.4, bx * 1.25, 48.0, 3.5, 0.85, 0.25));
            }
            r.body(sdCapsule(x, y, z, -1.1, 49.2, 2.8, -1.9, 44.8, 3.8, 0.95, 0.45));
        }
        return r;
    }

    // A statue in the world: origin (ox, oy, oz) between the feet; (sx, sz) = where the local +x points; (fx, fz) = front.
    public static void statue(Vox w, double ox, int oy, double oz, double sx, double sz, double fx, double fz, int who, int seed) {
        int R = 15;
        int minX = floor(ox - R), maxX = (int) Math.ceil(ox + R), minZ = floor(oz - R), maxZ = (int) Math.ceil(oz + R);
        Map<Long, Integer> parts = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5 - ox, dz = z + 0.5 - oz;
                double lx = dx * sx + dz * sz, lz = dx * fx + dz * fz;
                if (Math.abs(lx) > 12 || lz < -9.5 || lz > 9) {
                    continue;
                }
                for (int y = oy - 3; y <= oy + STATUE_H + 2; y++) {
                    Sdf r = statueSdf(lx, y - oy + 0.5, lz, who);
                    if (r.d < 0) {
                        parts.put(Keys.key(x, y, z), r.part);
                    }
                }
            }
        }
        // the old stone: moss over what lies open to the sky, and at the foot
        for (Map.Entry<Long, Integer> e : parts.entrySet()) {
            long k = e.getKey();
            int x = Keys.x(k), y = Keys.y(k), z = Keys.z(k);
            boolean exposed = !parts.containsKey(Keys.key(x, y + 1, z));
            double n = valueNoise3(x, y, z, seed + 31, 3.5);
            int part = e.getValue();
            byte t = part == 0 ? STATUE : part == 2 ? STATUE_DARK : STATUE_ALT;
            if (exposed && n > 0.6) {
                t = STATUE_MOSS;
            } else if (y < oy + 1 && n > 0.55) {
                t = STATUE_MOSS;
            }
            w.set(x, y, z, t);
        }
    }

    // ---- the valley -----------------------------------------------------------------------------------------------

    // Everything in blocks, heights relative to the water of the lake below (base). u runs downstream, v across.
    public static final int HF = 30, LAKE_U = -52, UMIN = -80, UMAX = 192, LD = 172, MARGIN = 22, POOL_U = 55, POOL_RU = 58,
            POOL_RV = 40, STATUE_U = 7;

    public record Statue(double u, double v, double ox, double oz, int who, double sx, double sz, double fx, double fz) {
    }

    public record Rock(double u, double v, double r) {
    }

    public static final class Frame {
        public final double cx, cz, c, s, p1, p2, hw0;
        public final int base, seed;
        public final List<Statue> statues = new ArrayList<>();
        public final List<Rock> rocks = new ArrayList<>();

        Frame(double cx, double cz, double c, double s, double p1, double p2, int base, int seed, double hw0) {
            this.cx = cx;
            this.cz = cz;
            this.c = c;
            this.s = s;
            this.p1 = p1;
            this.p2 = p2;
            this.base = base;
            this.seed = seed;
            this.hw0 = hw0;
        }

        // world position of the local point
        public int worldX(double u, double v) {
            return floor(cx + c * u - s * v);
        }

        public int worldZ(double u, double v) {
            return floor(cz + s * u + c * v);
        }
    }

    public static Frame valleyFrame(double cx, double cz, int seed, IntBinaryOperator ground) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, Math.PI * 2);
        double c = cos(a), s = sin(a);
        double p1 = rng.range(0, 6.283), p2 = rng.range(0, 6.283);
        int sum = 0;
        for (int k = 0; k < 9; k++) {
            int u = -70 + k * 32;
            sum += ground.applyAsInt(floor(cx + c * u), floor(cz + s * u));
        }
        int base = floor(sum / 9.0) - 2;
        double hw0 = 14 + 6 * valueNoise(0, 0, seed + 3, 22);
        int vs = floor(hw0 + 12);
        Frame F = new Frame(cx, cz, c, s, p1, p2, base, seed, hw0);
        // Hashirama on one side, Madara on the other, facing each other over the fall, turned a little downstream (so
        // whoever looks from the lake sees both three-quarters, with the raised hand)
        double tc = cos(0.42), ts = sin(0.42);
        for (int side = -1; side <= 1; side += 2) {
            double u = STATUE_U, v = side * vs;
            double ox = floor(cx + c * u - s * v) + 0.5, oz = floor(cz + s * u + c * v) + 0.5;
            double f0x = s * side, f0z = -c * side;
            F.statues.add(new Statue(u, v, ox, oz, side < 0 ? 0 : 1, c * tc - f0x * ts, s * tc - f0z * ts, f0x * tc + c * ts,
                    f0z * tc + s * ts));
        }
        // the boulders of the lake and the gorge (one of them good to stand on looking at the fall)
        F.rocks.add(new Rock(88, 4, 3.4));
        for (int k = 0; k < 9; k++) {
            double u = rng.range(45, 160), v = rng.range(-14, 14), r = rng.range(1.6, 3.0);
            F.rocks.add(new Rock(u, v, r));
        }
        return F;
    }

    public static double[] toLocal(Frame F, int x, int z) {
        double u0 = x + 0.5 - F.cx, v0 = z + 0.5 - F.cz;
        return new double[] {u0 * F.c + v0 * F.s, -u0 * F.s + v0 * F.c};
    }

    static double riverOffset(Frame F, double u) {
        if (u < -8) {
            return 6 * sin((u + 8) / 34 + F.p1) * smooth(clamp((-u - 8) / 24, 0, 1));
        }
        if (u > 104) {
            return 8 * sin((u - 104) / 40 + F.p2) * smooth(clamp((u - 104) / 26, 0, 1));
        }
        return 0;
    }

    // How far the point is outside the valley (negative = inside).
    public static double valleyOut(Frame F, double u, double v) {
        return Math.max(Math.abs(v) - (56 + 10 * valueNoise(u, 0, F.seed + 13, 30)), Math.max(UMIN - u, u - UMAX));
    }

    static boolean onPromontory(Frame F, double u, double v) {
        for (Statue st : F.statues) {
            if (Math.sqrt(sq(u - st.u) + sq(v - st.v)) < 8.5 + 2 * valueNoise(u, v, F.seed + 15, 4)) {
                return true;
            }
        }
        return false;
    }

    record Water(double edge, int w, double depth, boolean up) {
    }

    // The water of the point: edge > 0 inside it (the more, the further from the bank), w = top of the water.
    static Water valleyWater(Frame F, double u, double v) {
        int sd = F.seed;
        double vv = v - riverOffset(F, u);
        double lip = (valueNoise(0, v, sd + 6, 5) - 0.5) * 3;
        if (u < lip) {
            double hw = 14 + 6 * valueNoise(u, 0, sd + 3, 22);
            double edge = u >= LAKE_U ? hw - Math.abs(vv) : -1e9;
            double lr = 20 + 8 * (valueNoise(u, v, sd + 7, 12) - 0.5);
            edge = Math.max(edge, lr - Math.sqrt(sq(u - LAKE_U) + sq(vv)));
            double depth = 1 + Math.min(Math.max(edge, 0), 6) * 0.6 + 1.2 * valueNoise(u, v, sd + 8, 7);
            return new Water(edge, HF, depth, true);
        }
        if (onPromontory(F, u, v)) {
            return new Water(-1, 0, 0, false);
        }
        // the lake under the fall: long downstream, narrow at the fall and wide in the middle, to see the statues from afar
        double pk = 1 + 0.2 * (valueNoise(u, v, sd + 5, 14) - 0.5);
        double pool = (pk - Math.sqrt(sq((u - POOL_U) / POOL_RU) + sq(v / POOL_RV))) * POOL_RV;
        if (u < lip + 10) {
            pool = Math.max(pool, Math.min(F.hw0 + 2 - Math.abs(v), 4));
        }
        double hwg = 12 + 7 * valueNoise(u, 0, sd + 4, 26);
        if (u > LD - 30) {
            hwg *= Math.sqrt(Math.max(0, 1 - sq((u - (LD - 30)) / 30)));
        }
        double gorge = u > 90 ? hwg - Math.abs(vv) : -1e9;
        double edge = Math.max(pool, gorge);
        double depth = pool >= gorge
                ? 3 + 6.5 * clamp(pool / 16, 0, 1) * (u < 40 ? 1 : 0.7) + 1.5 * valueNoise(u, v, sd + 8, 7)
                : 2 + Math.min(Math.max(gorge, 0), 6) * 0.5 + 1.2 * valueNoise(u, v, sd + 8, 7);
        return new Water(edge, 0, depth, false);
    }

    public enum K {
        WATER, LAND, BEACH, LEDGE, STATUE, EDGE
    }

    record Land(double top, K kind) {
    }

    // The height (relative to the base) of the land at the point, knowing the distance to the water; and what it is.
    static Land valleyLand(Frame F, double u, double v, double dw) {
        int sd = F.seed;
        if (onPromontory(F, u, v)) {
            return new Land(HF + 3, K.STATUE);
        }
        double n1 = valueNoise(u, v, sd + 10, 28), n2 = valueNoise(u, v, sd + 11, 9);
        double up = HF + 2 + Math.min(dw, 14) * 0.35 + 5 * n1 + 2 * n2;
        double falls = HF + 3 - 17 * smooth(clamp((u - 10) / 60, 0, 1)) + 2 * n2 + 4 * n1 * smooth(clamp(u / 30, 0, 1));
        double gorge = HF - 14 + (10 - HF + 14) * smooth(clamp((u - 100) / (LD - 100), 0, 1)) + 5 * n1 + 2 * n2;
        double wUp = smooth(clamp((-u - 4) / 16, 0, 1)), wG = smooth(clamp((u - 96) / 20, 0, 1));
        double top = falls * (1 - wUp - wG) + up * wUp + gorge * wG;
        if (u > -4) {
            // the stone ledges just above the water under the cliffs; and the gravel beach where the gorge ends
            if (u > LD - 40 && dw < 2 + 8 * smooth(clamp((u - (LD - 40)) / 22, 0, 1))) {
                return new Land(1, K.BEACH);
            }
            if (u > 10 && dw < 1.2 + 3.2 * valueNoise(u, v, sd + 12, 6) - 1.4) {
                return new Land(1 + floor(2 * valueNoise(u, v, sd + 16, 3)), K.LEDGE);
            }
        }
        return new Land(top, K.LAND);
    }

    public record Height(int s, int w, boolean wet, boolean up, double u, double v, K kind) {
    }

    // The solid top (world) and the water of the column; null outside the valley.
    public static Height valleyHeight(Frame F, int x, int z, IntBinaryOperator ground) {
        double[] uv = toLocal(F, x, z);
        double u = uv[0], v = uv[1];
        double e = valleyOut(F, u, v);
        if (e >= MARGIN) {
            return null;
        }
        if (e <= 0) {
            Water wt = valleyWater(F, u, v);
            if (wt.edge > 0) {
                return new Height(F.base + wt.w - floor(wt.depth), F.base + wt.w, true, wt.up, u, v, K.WATER);
            }
            Land ld = valleyLand(F, u, v, -wt.edge);
            return new Height(F.base + floor(ld.top), NONE, false, false, u, v, ld.kind);
        }
        int g = ground.applyAsInt(x, z);
        double m = 1 - smooth(clamp((e + 6 * (valueNoise(u, v, F.seed + 14, 10) - 0.5)) / MARGIN, 0, 1));
        if (m <= 0.02) {
            return null;
        }
        Land ld = valleyLand(F, u, v, 40);
        return new Height(floor(g + (F.base + ld.top - g) * m), NONE, false, false, u, v, K.EDGE);
    }

    public static byte rockAt(int seed, int x, int y, int z) {
        int band = floor((y + 5 * valueNoise(x, z, seed + 20, 18)) / 3);
        double h = hash3(0, band, 0, seed + 21), j = hash3(x >> 1, band, z >> 1, seed + 22);
        if (h < 0.5) {
            return j < 0.12 ? ANDESITE : STONE;
        }
        if (h < 0.78) {
            return j < 0.15 ? STONE : ANDESITE;
        }
        return TUFF;
    }

    // The whole column. From low to s it is solid (at says the block); from s+1 to w it is water; the fall is the column
    // of water that drops from the edge (fall = its top); vine = {bottom, top, dx, dz} of a vine against the wall at dx, dz.
    public static final class Column {
        public final int low, s, w, fall;
        public final K kind;
        public final int top, under;
        public final int[] vine;
        final boolean spray;
        final Frame F;
        final int x, z;

        Column(Frame F, int x, int z, int low, int s, int w, int fall, K kind, int top, int under, int[] vine, boolean spray) {
            this.F = F;
            this.x = x;
            this.z = z;
            this.low = low;
            this.s = s;
            this.w = w;
            this.fall = fall;
            this.kind = kind;
            this.top = top;
            this.under = under;
            this.vine = vine;
            this.spray = spray;
        }

        public byte at(int y) {
            if (y == s && top >= 0) {
                return (byte) top;
            }
            if (under >= 0 && y >= s - 3) {
                return (byte) under;
            }
            if (spray && y < F.base + 14 && hash3(x, y, z, F.seed + 27) < 0.3) {
                return MOSSY;
            }
            return rockAt(F.seed, x, y, z);
        }
    }

    private static final int[][] D = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static Column valleyColumn(Frame F, int x, int z, IntBinaryOperator ground) {
        Height h = valleyHeight(F, x, z, ground);
        if (h == null) {
            return null;
        }
        int low = h.s, nmin = Integer.MAX_VALUE, fall = NONE;
        int[] vine = null;
        int self = Math.max(h.s, h.w);
        for (int[] d : D) {
            int dx = d[0], dz = d[1];
            Height n = valleyHeight(F, x + dx, z + dz, ground);
            int s = n != null ? n.s : ground.applyAsInt(x + dx, z + dz);
            // vines down the cliffs, from the edge of the higher neighbour
            if (n != null && s >= self + 5 && n.kind != K.WATER && hash3(x + dx, 7, z + dz, F.seed + 28) < 0.22) {
                int len = 3 + floor(hash3(x + dx, 8, z + dz, F.seed + 28) * 14);
                int bot = Math.max(self + 1, s - len);
                if (vine == null || s > vine[1]) {
                    vine = new int[] {bot, s, dx, dz};
                }
            }
            low = Math.min(low, s);
            nmin = Math.min(nmin, n != null ? Math.max(n.s, n.w) : s);
            if (n != null && n.up && n.wet && h.wet && !h.up) {
                fall = Math.max(fall, n.w);
            }
        }
        low -= 3;
        int drop = h.s - nmin;
        int sd = F.seed;
        double u = h.u, v = h.v;
        boolean spray = Math.sqrt(sq(u - 4) + sq(v)) < 30;
        int top, under = -1;
        if (h.kind == K.WATER) {
            double n = valueNoise(x, z, sd + 23, 6);
            if (h.up) {
                top = n < 0.6 ? GRAVEL : n < 0.75 ? SAND : STONE;
            } else if (spray) {
                top = n < 0.5 ? MOSSY : STONE;
            } else {
                top = n < 0.55 ? GRAVEL : n < 0.68 ? SAND : STONE;
            }
        } else if (h.kind == K.BEACH) {
            top = valueNoise(x, z, sd + 24, 5) < 0.7 ? GRAVEL : SAND;
            under = GRAVEL;
        } else if (h.kind == K.LEDGE) {
            double k = hash3(x, 0, z, sd + 25);
            top = k < 0.45 ? MOSSY : k < 0.8 ? STONE : GRAVEL;
        } else if (drop >= 3 || h.kind == K.STATUE) {
            top = spray && hash3(x, 1, z, sd + 26) < 0.55 ? MOSS : -1;
        } else {
            top = GRASS;
            under = DIRT;
        }
        return new Column(F, x, z, low, h.s, h.w, fall, h.kind, top, under, vine, spray);
    }

    // ---- trees, boulders and the marks of the fight ------------------------------------------------------------------

    private static void leaf(Vox w, int cx, int cy, int cz, double r, byte t, int seed) {
        int R = (int) Math.ceil(r);
        for (int x = -R; x <= R; x++) {
            for (int y = -R; y <= R; y++) {
                for (int z = -R; z <= R; z++) {
                    double d = Math.sqrt(x * x + y * y * 1.6 + z * z);
                    if (d < r - 0.9 * hash3(cx + x, cy + y, cz + z, seed)) {
                        w.set(cx + x, cy + y, cz + z, t);
                    }
                }
            }
        }
    }

    // A tree of the Land of Fire: broadleaf with a wide crown in three or four lumps, or a tall pine.
    public static void tree(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        if (rng.next() < 0.32) {
            int H = rng.nextInt(11, 17);
            for (int y = 0; y < H; y++) {
                w.force(ox, oy + y, oz, PINE_LOG);
            }
            for (int y = 3; y <= H; y++) {
                double r = (H - y) * 0.36 + 0.8 + (y % 3 == 0 ? 0.7 : 0);
                int R = (int) Math.ceil(r);
                for (int x = -R; x <= R; x++) {
                    for (int z = -R; z <= R; z++) {
                        if (x * x + z * z <= r * r && hash3(ox + x, oy + y, oz + z, seed) < 0.9) {
                            w.set(ox + x, oy + y, oz + z, PINE);
                        }
                    }
                }
            }
            w.set(ox, oy + H, oz, PINE);
            w.set(ox, oy + H + 1, oz, PINE);
            return;
        }
        int H = rng.nextInt(5, 8);
        for (int y = 0; y < H; y++) {
            w.force(ox, oy + y, oz, LOG);
        }
        int n = rng.nextInt(3, 4);
        byte dark = rng.next() < 0.4 ? LEAVES_DARK : LEAVES;
        leaf(w, ox, oy + H + 1, oz, rng.range(3.2, 4.2), dark, seed);
        for (int k = 0; k < n; k++) {
            double a = rng.range(0, Math.PI * 2), r = rng.range(2.2, 3.6);
            int up = rng.nextInt(-1, 2);
            int ex = ox + (int) Math.round(cos(a) * r), ez = oz + (int) Math.round(sin(a) * r), ey = oy + H + up;
            // the branch to the lump
            int steps = (int) Math.ceil(r) + 1;
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                w.force(ox + (int) Math.round((ex - ox) * t), oy + H - 2 + (int) Math.round((ey - oy - H + 2) * t),
                        oz + (int) Math.round((ez - oz) * t), LOG);
            }
            leaf(w, ex, ey + 1, ez, rng.range(2.4, 3.4), dark, seed);
        }
    }

    public static void boulder(Vox w, int ox, int oy, int oz, double r, int seed) {
        int R = (int) Math.ceil(r) + 1;
        for (int x = -R; x <= R; x++) {
            for (int y = -R; y <= R; y++) {
                for (int z = -R; z <= R; z++) {
                    double d = Math.sqrt(x * x + y * y * 1.4 + z * z) + 1.4 * (valueNoise3(ox + x, oy + y, oz + z, seed, 2.5) - 0.5);
                    if (d < r) {
                        boolean top = Math.sqrt(x * x + sq(y + 1) * 1.4 + z * z)
                                + 1.4 * (valueNoise3(ox + x, oy + y + 1, oz + z, seed, 2.5) - 0.5) >= r;
                        double k = hash3(ox + x, oy + y, oz + z, seed);
                        w.set(ox + x, oy + y, oz + z, top && k < 0.6 ? MOSS : k < 0.25 ? MOSSY : k < 0.45 ? ANDESITE : STONE);
                    }
                }
            }
        }
    }

    // A rock of columns: a bundle of stone columns of different heights, with moss and grass on top.
    public static void outcrop(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int n = rng.nextInt(4, 8);
        double[][] cols = new double[n][];
        for (int k = 0; k < n; k++) {
            double cx = rng.range(-4, 4), cz = rng.range(-4, 4), r = rng.range(1.6, 2.8);
            int h = rng.nextInt(5, 15);
            cols[k] = new double[] {cx, cz, r, h};
        }
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                int H = -1;
                for (double[] c : cols) {
                    if (sq(x - c[0]) + sq(z - c[1]) < c[2] * c[2]) {
                        H = Math.max(H, (int) c[3]);
                    }
                }
                if (H < 0) {
                    continue;
                }
                for (int y = -3; y <= H; y++) {
                    double band = hash3(0, floor((oy + y) / 3.0), 0, seed + 1);
                    w.force(ox + x, oy + y, oz + z,
                            y == H ? (hash3(ox + x, 0, oz + z, seed) < 0.55 ? MOSS : GRASS) : band < 0.6 ? STONE : ANDESITE);
                }
            }
        }
    }

    // A crater of the fight: a deep bowl with a raised rim of broken stone and turned earth, and pieces thrown far.
    public static void crater(Vox w, int ox, int oy, int oz, int seed, IntBinaryOperator ground) {
        Rng rng = new Rng(seed);
        double R = rng.range(7, 14), D = R * rng.range(0.45, 0.65), rim = R * 0.22;
        int E = (int) Math.ceil(R * 1.5);
        for (int x = -E; x <= E; x++) {
            for (int z = -E; z <= E; z++) {
                int g = ground.applyAsInt(ox + x, oz + z);
                double r = Math.sqrt(x * x + z * z) * (1 + 0.18 * (valueNoise(ox + x, oz + z, seed, 5) - 0.5));
                if (r < R) {
                    int bot = floor(oy - D * (1 - sq(r / R)) + rim * smooth(clamp((r - R * 0.7) / (R * 0.3), 0, 1)));
                    for (int y = bot + 1; y <= g + 1; y++) {
                        w.force(ox + x, y, oz + z, CARVE);
                    }
                    for (int y = bot - 2; y <= bot; y++) {
                        double k = hash3(ox + x, y, oz + z, seed);
                        w.force(ox + x, y, oz + z, y == bot ? (k < 0.4 ? COBBLE : k < 0.65 ? GRAVEL : k < 0.85 ? STONE : COARSE) : STONE);
                    }
                } else if (r < R * 1.5) {
                    int hgt = floor(rim * (1 - (r - R) / (R * 0.5)) * (0.6 + 0.8 * valueNoise(ox + x, oz + z, seed + 1, 3)) + 0.4);
                    for (int y = g + 1; y <= g + hgt; y++) {
                        double k = hash3(ox + x, y, oz + z, seed + 2);
                        w.set(ox + x, y, oz + z, k < 0.35 ? COARSE : k < 0.6 ? COBBLE : k < 0.8 ? DIRT : STONE);
                    }
                    if (hgt > 0) {
                        w.force(ox + x, g, oz + z, COARSE);
                    }
                }
            }
        }
        int n = rng.nextInt(3, 7);
        for (int k = 0; k < n; k++) {
            double a = rng.range(0, Math.PI * 2), d = R * rng.range(1.5, 2.6);
            int bx = ox + (int) Math.round(cos(a) * d), bz = oz + (int) Math.round(sin(a) * d);
            double br = rng.range(1.0, 2.2);
            boulder(w, bx, ground.applyAsInt(bx, bz) + 1, bz, br, seed + 10 + k);
        }
    }

    // A cut: a straight deep slash in the ground, like from a blade of chakra, with chips of stone at the edges.
    public static void cut(Vox w, int ox, int oy, int oz, int seed, IntBinaryOperator ground) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, Math.PI), L = rng.range(18, 44), W = rng.range(1.2, 2.4), D = rng.range(6, 12);
        double c = cos(a), s = sin(a);
        int E = (int) Math.ceil(L / 2 + 4);
        for (int x = -E; x <= E; x++) {
            for (int z = -E; z <= E; z++) {
                double along = x * c + z * s, across = -x * s + z * c;
                double t = Math.abs(along) / (L / 2);
                if (t >= 1) {
                    continue;
                }
                double half = W * Math.sqrt(1 - t * t);
                int g = ground.applyAsInt(ox + x, oz + z);
                if (Math.abs(across) < half) {
                    int bot = floor(g - D * Math.sqrt(1 - t * t) * (1 - sq(across / Math.max(half, 0.01))) * 0.9);
                    for (int y = bot + 1; y <= g + 1; y++) {
                        w.force(ox + x, y, oz + z, CARVE);
                    }
                    w.force(ox + x, bot, oz + z, GRAVEL);
                } else if (Math.abs(across) < half + 1.6 && hash3(ox + x, 0, oz + z, seed) < 0.5 * (1 - t)) {
                    w.set(ox + x, g + 1, oz + z, hash3(ox + x, 1, oz + z, seed) < 0.5 ? COBBLE : STONE);
                    w.force(ox + x, g, oz + z, COARSE);
                }
            }
        }
    }

    // ---- cells ----------------------------------------------------------------------------------------------------

    public enum Kind {
        VALE(900, 0.85, 180, 200, 1601, -1), CRATER(70, 0.5, 16, 22, 1607, 0), CUT(80, 0.4, 26, 28, 1609, 1),
        OUTCROP(46, 0.45, 8, 9, 1613, 2), BOULDER(16, 0.35, 4, 5, 1619, 3), TREE(9, 0.45, 2, 6, 1621, 4);

        public final int size, pad, reach, salt, order;
        public final double chance;

        Kind(int size, double chance, int pad, int reach, int salt, int order) {
            this.size = size;
            this.chance = chance;
            this.pad = pad;
            this.reach = reach;
            this.salt = salt;
            this.order = order;
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

    public static List<Obj> objectsNear(int x0, int z0, int x1, int z1, int seed, Kind... kinds) {
        List<Obj> out = new ArrayList<>();
        for (Kind c : kinds) {
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

    // Whether p goes before o when two pieces are too close (the order of kinds, then the position).
    public static boolean before(Obj p, Obj o) {
        return p.kind.order < o.kind.order || (p.kind.order == o.kind.order && (p.x < o.x || (p.x == o.x && p.z < o.z)));
    }

    // The piece itself, at the ground height gy of its spot.
    public static Vox build(Obj o, int gy, IntBinaryOperator ground) {
        Vox p = new Vox();
        switch (o.kind) {
            case TREE -> tree(p, o.x, gy + 1, o.z, o.seed);
            case BOULDER -> boulder(p, o.x, gy + 1, o.z, new Rng(o.seed).range(1.4, 3.0), o.seed);
            case OUTCROP -> outcrop(p, o.x, gy, o.z, o.seed);
            case CRATER -> crater(p, o.x, gy, o.z, o.seed, ground);
            case CUT -> cut(p, o.x, gy, o.z, o.seed, ground);
            default -> {
            }
        }
        return p;
    }
}
