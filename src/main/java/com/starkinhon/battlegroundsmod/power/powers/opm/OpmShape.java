package com.starkinhon.battlegroundsmod.power.powers.opm;

import net.minecraft.world.phys.Vec3;

// The shapes of Saitama's serious moves, the same numbers as the approved page (forja/saitama-soco-serio/scene.js) and
// its shader (souls_opm.vsh), so what hits and what breaks is exactly what is drawn. Each move has its own frame: the
// feet at the origin, z forward (flat), y up, x to his left (up x forward); distances in blocks, times in seconds.
public final class OpmShape {
    // ---------- Soco Serio: the tube of air ----------
    public static final double LMAX = 200, TFRONT = 1.4;
    // Seconds: the face turns serious, the wind-up, the punch, the hitstop before the tube leaves.
    public static final double T_FACE = 0.4, T_WIND = 1.0, T_P = 2.1, HSTOP = 0.24;

    // ---------- Saltos Laterais Serios ----------
    public static final double SL0 = 1.15, SLH = 0.13, SLF = SL0 + 15 * SLH + 0.08;
    public static final int SLN = 15;
    public static final double SL_STOP = 0.34;

    // ---------- Virada de Mesa Seria: the slab (x from -36 to 36, z from 2 to 68, 12 deep) ----------
    public static final double MG = 1.0, MF = 1.5, MV = 2.3, MC = 3.3;
    public static final int SLAB_X = 36, SLAB_Z0 = 2, SLAB_Z1 = 68, SLAB_DEEP = 12, SLAB_UP = 8;

    // ---------- Espirro Serio ----------
    public static final double SN_L = 150, SN_T = 1.1;
    public static final double E1 = 1.0, E2 = 1.45, EX = 2.0;
    public static final double FAN = 0.62;

    private OpmShape() {
    }

    public static double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    public static double smooth(double x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public static double yc(double z) {
        return 1.4 + 0.05 * z;
    }

    public static double rad(double z) {
        return Math.max(0.4, Math.min(1.0 + 0.13 * z, 20));
    }

    public static double frontAt(double tp) {
        return LMAX * (1 - Math.pow(1 - clamp(tp / TFRONT, 0, 1), 2.5));
    }

    public static double groundW(double z) {
        double r = rad(z), y = yc(z);
        return Math.max(0.75 * r, Math.sqrt(Math.max(r * r - y * y, 0))) * 1.2;
    }

    public static double digD(double z) {
        return Math.max(0, rad(z) - yc(z)) + 1.2 * smooth((z - 3) / 13);
    }

    // Whether a point (in the punch's frame) is in the tube, or on the ground of the trench under it.
    public static boolean inTube(double x, double y, double z) {
        if (z < 0.6) {
            return false;
        }
        double r = rad(z) * 1.05, dy = y - yc(z);
        return x * x + dy * dy < r * r || Math.abs(x) < groundW(z) && y < 2.2;
    }

    public static double snFront(double tp) {
        return SN_L * (1 - Math.pow(1 - clamp(tp / SN_T, 0, 1), 2));
    }

    // ---------- the frame ----------

    public record Frame(Vec3 origin, Vec3 forward, Vec3 left) {
        public static Frame of(Vec3 feet, Vec3 look) {
            Vec3 f = new Vec3(look.x, 0, look.z);
            f = f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
            return new Frame(feet, f, new Vec3(f.z, 0, -f.x));
        }

        public Vec3 world(double x, double y, double z) {
            return origin.add(left.scale(x)).add(0, y, 0).add(forward.scale(z));
        }

        public double[] local(Vec3 p) {
            Vec3 d = p.subtract(origin);
            return new double[] {d.dot(left), d.y, d.dot(forward)};
        }

        public Vec3 dir(double x, double y, double z) {
            return left.scale(x).add(0, y, 0).add(forward.scale(z));
        }
    }

    // The forward direction snapped to the nearest of the four sides (the slab lies on the block grid).
    public static Vec3 cardinal(Vec3 look) {
        if (Math.abs(look.x) > Math.abs(look.z)) {
            return new Vec3(Math.signum(look.x), 0, 0);
        }
        return new Vec3(0, 0, look.z < 0 ? -1 : 1);
    }

    // ---------- Saltos: the hops ----------

    // The 15 landing spots round the target and the last one (behind it, from where he started), in the frame of the move
    // (its origin at the target's feet), from the seed both sides know: a new side each hop (~137 degrees on), never the same
    // twice. Index 0 is where he stood.
    public static double[][] hops(double startX, double startZ, long seed) {
        java.util.Random r = new java.util.Random(seed);
        double[][] out = new double[SLN + 2][];
        out[0] = new double[] {startX, startZ};
        for (int i = 0; i < SLN; i++) {
            double a = Math.PI + i * 2.4 + (r.nextDouble() - 0.5) * 0.4, rr = 2.6 + r.nextDouble() * 1.1;
            out[i + 1] = new double[] {Math.sin(a) * rr, Math.cos(a) * rr};
        }
        out[SLN + 1] = new double[] {0, -1.6};
        return out;
    }

    // Where he is at t (seconds into the move), in the hop frame: {x, z, lift, landed (1/0), hop index}.
    public static double[] hopAt(double[][] hops, double t) {
        if (t < SL0) {
            return new double[] {hops[0][0], hops[0][1], 0, 1, -1};
        }
        if (t >= SLF) {
            double[] e = hops[hops.length - 1];
            return new double[] {e[0], e[1], 0, 1, SLN};
        }
        double fi = (t - SL0) / SLH;
        int i = Math.min(SLN, (int) Math.floor(fi));
        double k = fi - i;
        double kk = 1 - Math.pow(1 - clamp(k / 0.72, 0, 1), 2);
        double[] a = hops[i], b = hops[Math.min(i + 1, hops.length - 1)];
        return new double[] {a[0] + (b[0] - a[0]) * kk, a[1] + (b[1] - a[1]) * kk, Math.sin(Math.PI * kk) * 0.5, kk >= 1 ? 1 : 0, i};
    }

    // The ground the hops tear up at tick age of the move (kf: how far along the hops, 0..1), in the frame round the target:
    // {x, z, size, rise speed, deep (1/0), orbit speed, bob frequency, bob phase}. More and bigger as he goes on. The server
    // takes those blocks out of the ground; the client lifts them and turns them round the target.
    public static java.util.List<double[]> levSpawns(int seed, int age, double kf) {
        java.util.Random r = new java.util.Random(seed * 31L + age * 7919L);
        int n = (int) Math.floor(3 * (8 + 70 * kf) / 20.0 + r.nextDouble());
        java.util.List<double[]> out = new java.util.ArrayList<>(n);
        for (int q = 0; q < n; q++) {
            double g = r.nextDouble() * Math.PI * 2, rr = 3.4 + Math.pow(r.nextDouble(), 0.7) * (5 + 9 * kf);
            double size = 0.35 + Math.pow(r.nextDouble(), 1.8) * (0.8 + 1.8 * kf);
            double vy = 1.6 + r.nextDouble() * (3 + 4 * kf);
            double deep = r.nextDouble() < 0.3 ? 1 : 0;
            double w = (0.25 + r.nextDouble() * 0.5) / Math.sqrt(rr / 4);
            out.add(new double[] {Math.cos(g) * rr, Math.sin(g) * rr, size, vy, deep, w, 1 + r.nextDouble() * 2, r.nextDouble() * 6});
        }
        return out;
    }

    // ---------- Mesa: the slab on the block grid ----------

    // Cell (i across, j up, k forward) of the slab's frame: o is the block he stands in, (fx, fz) the side he faces.
    public static net.minecraft.core.BlockPos cell(net.minecraft.core.BlockPos o, int fx, int fz, int i, int j, int k) {
        return o.offset(i * fz + k * fx, j, -i * fx + k * fz);
    }

    // The hinge (far edge, at the top of the ground) the slab turns about: cell centers are at whole k, so the slab
    // (k 2..67) ends at 67.5 and lands on 68..133 (k' = 135 - k, j' = -1 - j).
    public static final double HINGE = SLAB_Z1 - 0.5;

    public static double slabAngle(double t) {
        if (t < MF) {
            return 0;
        }
        if (t < MV) {
            double k = (t - MF) / (MV - MF);
            return 1.75 * (1 - Math.pow(1 - k, 2.2));
        }
        double k = clamp((t - MV) / (MC - MV), 0, 1);
        return 1.75 + (Math.PI - 1.75) * k * k;
    }

    // ---------- Espirro: the fan dug in the ground ----------

    // How deep the fan digs at (x, z) of the move's frame once the front F has passed (0 outside it).
    public static double fanDepth(double x, double z, double front) {
        if (z <= 0) {
            return 0;
        }
        double r = Math.hypot(x, z - 0.5), u = Math.abs(Math.atan2(x, z)) / FAN;
        double open = smooth((front - r) / 6);
        return Math.min(3.4, 0.4 + r * 0.035) * (1 - smooth((u - 0.85) / 0.15)) * open * smooth((r - 2) / 6);
    }

    // Fixed random number in [0, 1) for a block column (so the edges of the cuts are ragged the same way everywhere).
    public static double hash(int x, int z, int k) {
        long h = x * 374761393L + z * 668265263L + k * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }
}
