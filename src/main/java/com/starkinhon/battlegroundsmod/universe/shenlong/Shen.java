package com.starkinhon.battlegroundsmod.universe.shenlong;

// The Shenlong cutscene's math, line by line the same as the approved preview (forja/shenlong/scene.js): the
// dragon's path, the body's radius and frame, the sway, the spikes, the balls' flight and the timeline curves.
// Everything is in the preview's scene space: the summoner at the origin, looking +Z, the balls 5 blocks ahead.
// Used by the client to draw the scene (client/fx/ShenlongScene) and by the server for where the balls go.
public final class Shen {
    public static final double[] BALLS = {0, 0, 5};
    public static final double[] EYE = {0, 1.62, 0};
    public static final double T_MENU = 22, T_PICKED = 22.31, T_END = 34;
    public static final double HEAD_S = 1.35;
    public static final int RINGS = 900, SIDES = 30;

    private Shen() {
    }

    // ---------- small math (the preview's V) ----------
    public static double sm(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    public static double cl(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    public static double easeOut(double x) {
        return 1 - Math.pow(1 - cl(x, 0, 1), 3);
    }

    public static double easeInOut(double x) {
        x = cl(x, 0, 1);
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    public static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    public static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static double[] mul(double[] a, double k) {
        return new double[] {a[0] * k, a[1] * k, a[2] * k};
    }

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static double len(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    public static double[] norm(double[] a) {
        double l = len(a);
        return l < 1e-12 ? new double[] {Double.NaN, Double.NaN, Double.NaN} : mul(a, 1 / l);
    }

    public static double[] lerp(double[] a, double[] b, double t) {
        return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    // ---------- the dragon's path: out of the balls, up in a spiral round the light, the head down to the player ----------
    private static final double[][] DENSE;
    private static final double[] ARC;
    public static final double L;
    public static final double HELIX_END;
    public static final double BODY_L;

    static {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        pts.add(new double[] {BALLS[0], 0.2, BALLS[2]});
        pts.add(new double[] {BALLS[0], 2.5, BALLS[2] + 0.6});
        double turns = 2.1, r0 = 10, top = 50;
        int n = 90;
        for (int i = 1; i <= n; i++) {
            double u = (double) i / n, a = -Math.PI / 2 + u * turns * Math.PI * 2;
            double r = r0 * sm(0, 0.18, u) + 1.2;
            pts.add(new double[] {BALLS[0] + Math.cos(a) * r, 3 + u * (top - 3), BALLS[2] + Math.sin(a) * r});
        }
        double[] last = pts.get(pts.size() - 1);
        pts.add(new double[] {last[0] * 0.6, top + 5, last[2] + 5});
        pts.add(new double[] {0, top, BALLS[2] + 15});
        pts.add(new double[] {0, 34, BALLS[2] + 15});
        pts.add(new double[] {0, 24, BALLS[2] + 10});
        pts.add(new double[] {0, 17.5, BALLS[2] + 6.5});
        java.util.List<double[]> dense = new java.util.ArrayList<>();
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] p0 = pts.get(Math.max(0, i - 1)), p1 = pts.get(i), p2 = pts.get(i + 1), p3 = pts.get(Math.min(pts.size() - 1, i + 2));
            for (int s = 0; s < 12; s++) {
                dense.add(cr(p0, p1, p2, p3, s / 12.0));
            }
        }
        dense.add(pts.get(pts.size() - 1));
        DENSE = dense.toArray(new double[0][]);
        ARC = new double[DENSE.length];
        for (int i = 1; i < DENSE.length; i++) {
            ARC[i] = ARC[i - 1] + len(sub(DENSE[i], DENSE[i - 1]));
        }
        L = ARC[ARC.length - 1];
        HELIX_END = ARC[(n + 1) * 12];
        BODY_L = L;
    }

    private static double[] cr(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        double[] o = new double[3];
        for (int k = 0; k < 3; k++) {
            o[k] = 0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
        }
        return o;
    }

    public static double[] at(double a) {
        a = cl(a, 0, L);
        int lo = 0, hi = ARC.length - 1;
        while (hi - lo > 1) {
            int m = (lo + hi) >> 1;
            if (ARC[m] <= a) {
                lo = m;
            } else {
                hi = m;
            }
        }
        double t = (a - ARC[lo]) / Math.max(1e-6, ARC[hi] - ARC[lo]);
        return lerp(DENSE[lo], DENSE[hi], t);
    }

    public static double radius(double d) {
        double x = d / BODY_L;
        double r = 1.45 + 0.45 * sm(0, 0.06, x);
        r *= 1 - 0.92 * Math.pow(sm(0.45, 1.0, x), 1.25);
        r += 0.12 * Math.exp(-Math.pow((d - 14) / 3, 2)) + 0.14 * Math.exp(-Math.pow((d - 64) / 4, 2));
        return Math.max(0.12, r);
    }

    // the body's frame at a point of the path: T forward (to the head), N = the back, B = the side
    public record Frame(double[] p, double[] t, double[] n, double[] b) {
    }

    public static Frame frame(double a) {
        double e = 0.6;
        double[] p = at(a), p2 = at(a + e), p1 = at(a - e);
        double[] t = norm(sub(p2, p1));
        double[] out = norm(new double[] {p[0] - BALLS[0], 0, p[2] - BALLS[2]});
        double k = sm(HELIX_END - 12, HELIX_END + 6, a);
        double[] d = norm(lerp(out, new double[] {0, 1, 0}, k));
        double[] n = sub(d, mul(t, dot(d, t)));
        if (!(len(n) >= 1e-3)) {
            n = new double[] {0, 1, 0};
        }
        n = norm(n);
        return new Frame(p, t, n, cross(t, n));
    }

    // a living wave running down the body (gone near the head and near the tail held in the light)
    public static double[] sway(double a, double d, double time, double settle) {
        double w = sm(0, 10, a) * sm(0, 12, d) * settle;
        return new double[] {Math.sin(a * 0.11 - time * 1.3) * 0.7 * w, Math.sin(a * 0.07 - time * 0.9 + 1.3) * 0.5 * w};
    }

    // the spikes of the back: each its own width, height and lean
    public record Spike(double d, double w, double h, double lean) {
    }

    public static final java.util.List<Spike> SPIKES;

    static {
        java.util.List<Spike> list = new java.util.ArrayList<>();
        double d = 2.2;
        for (int k = 0; d < BODY_L - 4; k++) {
            double h0 = hash(k * 3.17 + 0.5), h1 = hash(k * 7.91 + 2.1), h2 = hash(k * 1.37 + 9.4);
            double r = radius(d);
            list.add(new Spike(d, r * (0.3 + 0.16 * h0), r * (0.2 + 0.24 * h1) * (k % 5 == 0 ? 1.2 : 1), 0.45 + 0.45 * h2));
            d += r * (0.36 + 0.22 * h1) + 0.16;
        }
        SPIKES = java.util.List.copyOf(list);
    }

    // where the legs hold on: arc length from the head and the side
    public static final double[][] LEG_AT = {{13, 1}, {14.2, -1}, {63, 1}, {64.5, -1}};

    // ---------- the balls ----------
    public static double[] home(int i) {
        return new double[] {BALLS[0] + Math.cos(i / 7.0 * Math.PI * 2) * 0.95, 0.36, BALLS[2] + Math.sin(i / 7.0 * Math.PI * 2) * 0.95};
    }

    public static double[] ballAt(int i, double t) {
        double[] p = home(i);
        if (t > 27) {
            double up = easeOut((t - 27) / 1.3), ang = i / 7.0 * Math.PI * 2 + (t - 27) * (2 + (t - 27) * 3), rr = 0.95 * (1 - up) + 1.4 * up;
            p = new double[] {BALLS[0] + Math.cos(ang) * rr, 0.36 + up * 9, BALLS[2] + Math.sin(ang) * rr};
        }
        if (t > 29.2) {
            double x = t - 29.2;
            double[] dir = scatterDir(i);
            double h0 = hash(i * 4.1 + 1), h1 = hash(i * 9.7 + 3);
            double dist = (18 + 14 * h1) * x * (1 + x * 0.9);
            p = add(p, add(mul(dir, dist), new double[] {0, 22 * Math.sin(Math.min(x, 1.4) / 1.4 * Math.PI * 0.5) + 18 * h0 * x, 0}));
        }
        return p;
    }

    // the way each ball flies off (the server sends it that way into the world too)
    public static double[] scatterDir(int i) {
        double h0 = hash(i * 4.1 + 1);
        return new double[] {Math.cos(i / 7.0 * Math.PI * 2 + h0), 0, Math.sin(i / 7.0 * Math.PI * 2 + h0)};
    }

    // ---------- the timeline ----------
    public static double storm(double t) {
        return sm(2, 5, t) * (1 - sm(29.5, 33.5, t));
    }

    public static double pulse(double t) {
        double beatRate = 0.9 + 3.2 * sm(0, 5, t);
        double phase = t < 5 ? (t * beatRate) % 1 : 0;
        double pulse = t < 5 ? Math.pow(1 - phase, 6) * (0.35 + 0.65 * sm(0, 5, t)) : 0;
        pulse = Math.max(pulse, sm(4.9, 5.3, t) * (1 - sm(6.5, 9, t)) * 2.2);
        pulse = Math.max(pulse, sm(9, 11, t) * 0.45 * (1 - sm(25.5, 27, t)));
        pulse = Math.max(pulse, sm(26.6, 27.2, t) * (1 - sm(29.4, 30.5, t)) * 1.6);
        return pulse;
    }

    public static double ballPow(double t) {
        return pulse(t) * 1.4 + sm(5.5, 7, t) * (1 - sm(13.5, 15.5, t)) * 1.2;
    }

    public static double headArc(double t) {
        return 0.5 + easeInOut((t - 5.6) / 7.4) * (L - 0.5);
    }

    public static boolean dragonVisible(double t) {
        return t > 5.6 && t < 27.5;
    }

    public static double settle(double t) {
        return sm(11.5, 14, t);
    }

    public static double look(double t) {
        return sm(11.4, 13.6, t);
    }

    public static double emerge(double t) {
        return 1 - sm(12.5, 15, t) * 0.75;
    }

    public static double dissolve(double t) {
        return sm(25.6, 27.4, t);
    }

    public static double eye(double t) {
        return sm(12.8, 13.4, t) * (1 - sm(25.6, 26.4, t)) + 0.15;
    }

    public static double pillar(double t) {
        return sm(4.95, 5.4, t) * (1 - sm(13.0, 15.0, t));
    }

    public static double pillarGrow(double t) {
        return easeOut((t - 4.95) / 0.7);
    }

    public static double stone(double t) {
        return sm(31.5, 33.5, t);
    }

    public static double letterbox(double t) {
        return sm(4.6, 5.2, t) * (1 - sm(32, 33.5, t));
    }

    // the speech lines: the two fixed ones, then the wish's answer and the goodbye
    public static final double[][] LINES = {{15.0, 17.6}, {17.7, 21.8}};

    public static boolean talking(double t) {
        for (double[] l : LINES) {
            if (t >= l[0] && t < l[1] - 0.3) {
                return true;
            }
        }
        return (t >= 22.3 && t < 24.7) || (t >= 25.0 && t < 25.8);
    }

    public static double jaw(double t) {
        double jaw = 0.06;
        if (t > 13.0 && t < 14.6) {
            jaw = 0.62 * Math.sin(cl((t - 13.0) / 1.6, 0, 1) * Math.PI);
        }
        if (talking(t)) {
            jaw = 0.1 + 0.22 * Math.abs(Math.sin(t * 8.3 + Math.sin(t * 2.9) * 2.0)) * (0.6 + 0.4 * Math.sin(t * 1.7));
        }
        return jaw;
    }

    // the lightning: when, its seed and where it strikes
    public static final double[][] BOLTS = {{3.3, 11, -26, 0, 38}, {4.15, 23, 31, 0, 24}, {4.85, 37, -12, 0, 55}, {13.25, 51, 18, 0, 44},
            {13.45, 64, -30, 0, 20}};

    public static double boltLife(double t, double t0) {
        double x = t - t0;
        if (x < 0 || x > 0.55) {
            return 0;
        }
        return x < 0.08 ? 1 : x < 0.14 ? 0.15 : x < 0.22 ? 0.8 : x < 0.3 ? 0.1 : x < 0.38 ? 0.5 : (0.55 - x) / 0.17 * 0.3;
    }

    // segments {ax, ay, az, bx, by, bz, width} of one bolt (the preview's boltPath, same random sequence)
    public static java.util.List<double[]> boltPath(int seed, double[] to) {
        long[] s = {seed};
        java.util.function.DoubleSupplier rnd = () -> {
            s[0] = (s[0] * 16807) % 2147483647L;
            return s[0] / 2147483647.0;
        };
        double[] from = {to[0] * 0.4 + (rnd.getAsDouble() - 0.5) * 30, 120, to[2] * 0.6 + 30};
        java.util.List<double[]> segs = new java.util.ArrayList<>();
        split(from, to, 7, 1, rnd, segs);
        return segs;
    }

    private static void split(double[] a, double[] b, int depth, double w, java.util.function.DoubleSupplier rnd, java.util.List<double[]> segs) {
        if (depth == 0) {
            segs.add(new double[] {a[0], a[1], a[2], b[0], b[1], b[2], w});
            return;
        }
        double[] m = lerp(a, b, 0.5);
        double l = len(sub(b, a));
        double r1 = rnd.getAsDouble(), r2 = rnd.getAsDouble(), r3 = rnd.getAsDouble();
        double[] mm = add(m, new double[] {(r1 - 0.5) * l * 0.32, (r2 - 0.5) * l * 0.12, (r3 - 0.5) * l * 0.32});
        split(a, mm, depth - 1, w, rnd, segs);
        split(mm, b, depth - 1, w, rnd, segs);
        if (depth > 2 && rnd.getAsDouble() < 0.42 && w > 0.2) {
            double e1 = rnd.getAsDouble(), e2 = rnd.getAsDouble(), e3 = rnd.getAsDouble();
            double[] end = add(mm, new double[] {(e1 - 0.5) * l * 0.9, -l * (0.3 + e2 * 0.4), (e3 - 0.5) * l * 0.9});
            split(mm, end, depth - 2, w * 0.45, rnd, segs);
        }
    }
}
