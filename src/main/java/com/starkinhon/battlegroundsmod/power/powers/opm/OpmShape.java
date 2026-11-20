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
    // The bigger Saltos (Stark 2026-11-20, forja/saitama-epico): the ground sinks in a ring round the target (radius 6 to 20,
    // up to 2.4 deep as the hops go on), the final punch opens a crater 25 across (7 deep) and a dome of air runs 62 out in
    // 0.9 s, felling the trees it reaches.
    public static final double SINK_R0 = 6, SINK_R1 = 20, SINK_D = 2.4, CRATER_R = 12.5, CRATER_D = 7, DOME_R = 62, DOME_T = 0.9;
    // The serious mode turning on: the ground cracks this far round his feet.
    public static final double WAKE_R = 8;

    // ---------- Virada de Mesa Seria: the slab (x from -36 to 36, z from 2 to 68, 12 deep) ----------
    public static final double MG = 1.0, MF = 1.5, MV = 2.3, MC = 3.3;
    public static final int SLAB_X = 36, SLAB_Z0 = 2, SLAB_Z1 = 68, SLAB_DEEP = 12, SLAB_UP = 8;

    // ---------- Espirro Serio ----------
    // (300 blocks in 1.6 s since 2026-11-20: up to 20 deep; the shader's sneeze uses the same two numbers)
    public static final double SN_L = 300, SN_T = 1.6;
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

    // ---------- Saltos, the bigger ground ----------

    // How big a landing's dent is: hop i of 15 (5 to 6 across).
    public static double dentW(int i) {
        return 1.2 + 0.55 * (i / (double) SLN);
    }

    // The ring sunk round the target at the point k (0..1) of the hops: {radius, depth}.
    public static double[] sink(double k) {
        double s = smooth(k);
        return new double[] {SINK_R0 + (SINK_R1 - SINK_R0) * s, SINK_D * s};
    }

    // How deep the ring is sunk at distance r from the target (radius R, depth D), with the radial cracks (13 of them, one
    // block wide wherever they are) a block deeper.
    public static double sinkDepth(double dx, double dz, double R, double D) {
        double r = Math.hypot(dx, dz), u = r / R;
        if (u >= 1 || D <= 0) {
            return 0;
        }
        double d = D * (1 - u * u * u * u);
        double a = Math.atan2(dz, dx), n = 13, f = a / (Math.PI * 2) * n + (hash((int) Math.floor(r * 0.5), 7, 11) - 0.5) * 0.35;
        double cr = Math.abs(f - Math.floor(f) - 0.5);
        if (r > 3 && cr < 0.55 / Math.max(Math.PI * 2 * r / n, 1)) {
            d += 1.2;
        }
        return d;
    }

    // The dome's radius b seconds after the punch.
    public static double domeAt(double b) {
        return DOME_R * (1 - Math.pow(1 - clamp(b / DOME_T, 0, 1), 2.2));
    }

    // The cracks of the serious mode turning on, at (dx, dz) from his feet: 9 jagged lines, one block wide, out to WAKE_R.
    public static boolean wakeCrack(int x, int z, double dx, double dz) {
        double r = Math.hypot(dx, dz);
        if (r < 0.9 || r > WAKE_R * (0.75 + 0.25 * hash(x, z, 5))) {
            return false;
        }
        double a = Math.atan2(dz, dx), n = 9, f = a / (Math.PI * 2) * n + (hash((int) Math.floor(r * 0.7), 3, 13) - 0.5) * 0.3;
        return Math.abs(f - Math.floor(f) - 0.5) < 0.5 / Math.max(Math.PI * 2 * r / n, 1);
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
    // {x, z, size, rise speed, deep (1/0), orbit speed, bob frequency, bob phase, plate (1/0: a flat slab of ground)}. More and bigger as he goes on. The server
    // takes those blocks out of the ground; the client lifts them and turns them round the target.
    public static java.util.List<double[]> levSpawns(int seed, int age, double kf) {
        java.util.Random r = new java.util.Random(seed * 31L + age * 7919L);
        int n = (int) Math.floor(3 * (10 + 90 * kf) / 20.0 + r.nextDouble());
        java.util.List<double[]> out = new java.util.ArrayList<>(n);
        for (int q = 0; q < n; q++) {
            double g = r.nextDouble() * Math.PI * 2, rr = 3.4 + Math.pow(r.nextDouble(), 0.7) * (6 + 14 * kf);
            boolean plate = r.nextDouble() < 0.2;
            double size = (0.35 + Math.pow(r.nextDouble(), 1.8) * (0.8 + 1.8 * kf)) * (plate ? 2.4 : 1);
            double vy = (1.6 + r.nextDouble() * (3 + 5 * kf)) * (plate ? 1.4 : 1);
            double deep = r.nextDouble() < 0.3 ? 1 : 0;
            double w = (0.25 + r.nextDouble() * 0.5) / Math.sqrt(rr / 4);
            out.add(new double[] {Math.cos(g) * rr, Math.sin(g) * rr, size, vy, deep, w, 1 + r.nextDouble() * 2, r.nextDouble() * 6, plate ? 1 : 0});
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
        return Math.min(20, 0.5 + r * 0.085) * (1 - smooth((u - 0.85) / 0.15)) * open * smooth((r - 2) / 6);
    }

    // Fixed random number in [0, 1) for a block column (so the edges of the cuts are ragged the same way everywhere).
    public static double hash(int x, int z, int k) {
        long h = x * 374761393L + z * 668265263L + k * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }
}
