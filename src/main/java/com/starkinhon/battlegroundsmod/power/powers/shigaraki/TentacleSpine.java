package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

// The V's five tentacles as the preview draws them (forja/shigaraki-tentaculos: DEFS, keyAt, sampleCurve, updateSpine,
// growAt), shared by the server (what they touch) and the clients (client/fx/shig/TentacleFx: the hands along them).
// Everything in the preview's frame: Shigaraki at the origin facing +z, +x to the side the preview calls "direito",
// y up. world = feet + x * (cos yaw, 0, sin yaw) + y * up + z * (-sin yaw, 0, cos yaw) (Minecraft's yaw).
public final class TentacleSpine {
    public static final int KK = 40;
    public static final float CAST_LEN = 9.2f;
    public static final float THICK = 1.35f;
    private static final int DENSE = 140;

    public record Key(float t, float[] apex, float[] mid, float[] tgt) {
    }

    // One tentacle: seed (its hands), root on his back, the way it leaves, its keys, delay, radii (root, middle, tip
    // factor), hand scale, length L (rounded arc length at t = 0, as the preview).
    public static final class Def {
        public final String name;
        public final int seed;
        public final float[] root, dir;
        public final Key[] keys;
        // THICK: thicker than the preview's (Stark, 2026-11-13: "Aumentar os Tentaculos"), the hands with it; the hitbox
        // (Tentaculos) reads the same radius, so it grows too.
        public final float delay, Rr = 0.45f * THICK, Rm, tipK = 0.5f, handK = 1.25f;
        public float L;

        Def(String name, int seed, float[] root, float[] dir, Key[] keys, float delay, float Rm) {
            this.name = name;
            this.seed = seed;
            this.root = root;
            float n = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            this.dir = new float[] {dir[0] / n, dir[1] / n, dir[2] / n};
            this.keys = keys;
            this.delay = delay;
            this.Rm = Rm * THICK;
        }

        // The tentacle's radius d blocks along it (RadF).
        public float radius(float d) {
            float r = Rr + (Rm - Rr) * smooth((d - 1.5f) / 9);
            return r * (1 + (tipK - 1) * (float) Math.pow(clamp(d / L, 0, 1), 1.3));
        }

        // How much of it is out at t seconds (growAt): grows over 1.45 s after its delay, goes back from 7.45 s.
        public float grow(float t) {
            if (t < 0 || t > CAST_LEN) {
                return 0;
            }
            float up = easeOut3((t - delay) / 1.45f);
            float ret = 1 - (float) Math.pow(clamp((t - 7.45f - delay * 0.2f) / 1.3f, 0, 1), 2);
            return clamp(up * ret, 0, 1);
        }
    }

    public static final Def[] DEFS;

    static {
        Key[] kT0 = {
                key(0, 9, 12, 3, 10, 12, 12, 6, 0.4f, 26), key(2.4f, 9, 12, 3, 10, 12, 12, 6, 0.4f, 26),
                key(4.2f, 10, 11, 3.5f, 14, 9, 12, 13, 0.4f, 21), key(5.0f, 8, 17, 3, 6, 19, 9, 4, 13, 21),
                key(5.55f, 8, 13, 3, 6, 12, 11, 1, 0.4f, 21), key(7.4f, 8, 13, 3, 6, 12, 11, 1, 0.4f, 21)};
        Key[] kT3 = {
                key(0, 5, 3, 2.5f, 12, 2, 8, 16, 0.4f, 19), key(2.5f, 5, 3, 2.5f, 12, 2, 8, 16, 0.4f, 19),
                key(4.5f, 5, 3, 3, 7, 2.5f, 12, -8, 0.4f, 22), key(5.8f, 4, 3, 3, 2, 2, 11, -15, 0.4f, 13),
                key(7.4f, 4, 3, 3, 2, 2, 11, -15, 0.4f, 13)};
        Key[] central = {
                key(0, -5, 15, 3, -2, 17, 12, 0, 11, 31), key(2.4f, -5, 15, 3, -2, 17, 12, 0, 11, 31),
                key(4.4f, -5, 15, 3, -1, 12, 13, 2, 1, 30), key(7.4f, -5, 15, 3, -1, 12, 13, 2, 1, 30)};
        DEFS = new Def[] {
                new Def("alto-direito", 101, v(0.27f, 1.55f, -0.16f), v(1, 0.9f, 0.25f), kT0, 0.55f, 2.0f),
                new Def("alto-esquerdo", 202, v(-0.27f, 1.55f, -0.16f), v(-1, 0.9f, 0.25f), mirror(kT0, 0.12f), 0.62f, 2.0f),
                new Def("central", 303, v(0, 1.45f, -0.2f), v(-0.55f, 1, 0.25f), central, 0.45f, 2.4f),
                new Def("baixo-direito", 404, v(0.33f, 1.25f, -0.05f), v(1, 0.3f, 0.2f), kT3, 0.72f, 1.7f),
                new Def("baixo-esquerdo", 505, v(-0.33f, 1.2f, -0.05f), v(-1, 0.3f, 0.2f), mirror(kT3, 0.25f), 0.8f, 1.7f)};
        float[][] dense = new float[DENSE + 1][3];
        float[] cum = new float[DENSE + 1];
        for (Def d : DEFS) {
            d.L = Math.round(sample(d, 0, false, null, dense, cum));
        }
    }

    private TentacleSpine() {
    }

    private static float[] v(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    private static Key key(float t, float ax, float ay, float az, float mx, float my, float mz, float tx, float ty, float tz) {
        return new Key(t, v(ax, ay, az), v(mx, my, mz), v(tx, ty, tz));
    }

    private static Key[] mirror(Key[] ks, float dt) {
        Key[] out = new Key[ks.length];
        for (int i = 0; i < ks.length; i++) {
            Key k = ks[i];
            out[i] = new Key(k.t() + dt, v(-k.apex()[0], k.apex()[1], k.apex()[2]), v(-k.mid()[0], k.mid()[1], k.mid()[2]),
                    v(-k.tgt()[0], k.tgt()[1], k.tgt()[2]));
        }
        return out;
    }

    // ---------- the spine at a time ----------

    // The KK points of the spine at t (preview frame), the ground flattening included, into out[KK][3].
    public static void spine(Def d, float t, float[][] out) {
        spine(d, t, null, out);
    }

    // The same, homing on aim (the target in the preview frame, or null): Stark 2026-11-13, "os tentáculos teleguiados
    // no alvo que ele tá lutando". From the strike on, every tentacle's target, middle and apex move with the target
    // (each keeps its own spread around it), so the hands bend after them instead of hitting the preview's fixed spot.
    public static void spine(Def d, float t, float[] aim, float[][] out) {
        float[][] dense = new float[DENSE + 1][3];
        float[] cum = new float[DENSE + 1];
        sample(d, t, true, aim, dense, cum);
        // homing stretches or shrinks the curve: the drawn length follows it, so the hands still end on the target
        float stretch = 1;
        if (aim != null) {
            float[][] base = new float[DENSE + 1][3];
            float[] baseCum = new float[DENSE + 1];
            float bl = sample(d, t, true, null, base, baseCum);
            stretch = bl > 1e-3f ? clamp(cum[DENSE] / bl, 0.5f, 2f) : 1;
        }
        int j = 0;
        for (int k = 0; k < KK; k++) {
            float along = k / (float) (KK - 1) * d.L, dd = along * stretch;
            while (j < DENSE - 1 && cum[j + 1] < dd) {
                j++;
            }
            if (dd <= cum[DENSE]) {
                float f = clamp((dd - cum[j]) / Math.max(cum[j + 1] - cum[j], 1e-5f), 0, 1);
                for (int c = 0; c < 3; c++) {
                    out[k][c] = dense[j][c] + (dense[j + 1][c] - dense[j][c]) * f;
                }
            } else {
                float[] tn = sub(dense[DENSE], dense[DENSE - 1]);
                norm(tn);
                for (int c = 0; c < 3; c++) {
                    out[k][c] = dense[DENSE][c] + tn[c] * (dd - cum[DENSE]);
                }
            }
            out[k][1] = Math.max(out[k][1], d.radius(along) * 0.5f); // flattens on the ground instead of going through
        }
    }

    // Parallel transport along the spine: N[k], B[k] (the hands don't twist by themselves along it).
    public static void frames(float[][] S, float[][] N, float[][] B) {
        float[] T = new float[3];
        for (int k = 0; k < KK; k++) {
            float[] a = S[Math.min(k + 1, KK - 1)], b = S[Math.max(k - 1, 0)];
            T[0] = a[0] - b[0];
            T[1] = a[1] - b[1];
            T[2] = a[2] - b[2];
            norm(T);
            if (k == 0) {
                float[] ax = Math.abs(T[1]) < 0.9f ? v(0, 1, 0) : v(1, 0, 0);
                float[] n = cross(ax, T);
                norm(n);
                N[0] = n;
            } else {
                float[] p = N[k - 1];
                float dt = p[0] * T[0] + p[1] * T[1] + p[2] * T[2];
                float[] n = v(p[0] - T[0] * dt, p[1] - T[1] * dt, p[2] - T[2] * dt);
                norm(n);
                N[k] = n;
            }
            float[] bb = cross(T, N[k]);
            norm(bb);
            B[k] = bb;
        }
    }

    private static Key[] at(Def d, float t, float[][] o) {
        Key[] K = d.keys;
        int i = 0;
        while (i < K.length - 2 && t > K[i + 1].t()) {
            i++;
        }
        Key a = K[i], b = K[i + 1];
        float w = smooth((t - a.t()) / Math.max(b.t() - a.t(), 1e-3f));
        for (int c = 0; c < 3; c++) {
            o[0][c] = a.apex()[c] + (b.apex()[c] - a.apex()[c]) * w;
            o[1][c] = a.mid()[c] + (b.mid()[c] - a.mid()[c]) * w;
            o[2][c] = a.tgt()[c] + (b.tgt()[c] - a.tgt()[c]) * w;
        }
        return K;
    }

    // sampleCurve: a centripetal Catmull-Rom through root, root + 2.5 dir, apex, mid, target, sampled at DENSE + 1
    // points (with the slow writhing wave when wave), and the arc length up to each.
    private static float sample(Def d, float t, boolean wave, float[] aim, float[][] dense, float[] cum) {
        float[][] k = new float[3][3];
        at(d, t, k);
        if (aim != null) {
            home(t, aim, k);
        }
        float[][] P = {d.root.clone(), v(d.root[0] + d.dir[0] * 2.5f, d.root[1] + d.dir[1] * 2.5f, d.root[2] + d.dir[2] * 2.5f), k[0], k[1], k[2]};
        for (int i = 0; i <= DENSE; i++) {
            float p = i / (float) DENSE;
            catmull(P, p, dense[i]);
            if (wave) {
                float amp = 0.9f * smooth((p - 0.04f) / 0.3f) * (1 - 0.4f * p), ph = d.seed * 0.37f;
                dense[i][0] += (float) Math.sin(p * 9 - t * 3.1f + ph) * amp;
                dense[i][1] += (float) Math.sin(p * 7.3f - t * 2.6f + ph * 1.7f) * amp * 0.8f;
                dense[i][2] += (float) Math.sin(p * 6.1f - t * 2.2f + ph * 2.3f) * amp * 0.6f;
            }
            cum[i] = i == 0 ? 0 : cum[i - 1] + dist(dense[i], dense[i - 1]);
        }
        return cum[DENSE];
    }

    // Where the preview's tentacles all strike at (the dummy, about 20 blocks ahead on the ground).
    private static final float[] REF = {0, 0.4f, 20};

    // Moves the keys (apex, middle, target) after the aim: nothing while they rise (before 2.2 s), all of it from 3.4 s.
    // The aim is kept in front and within the tentacles' reach.
    private static void home(float t, float[] aim, float[][] k) {
        float w = smooth((t - 2.2f) / 1.2f);
        if (w <= 0) {
            return;
        }
        float ax = clamp(aim[0], -16, 16), ay = clamp(aim[1], 0, 10), az = clamp(aim[2], 6, 28);
        float dx = (ax - REF[0]) * w, dy = (ay - REF[1]) * w, dz = (az - REF[2]) * w;
        float[] share = {0.25f, 0.55f, 1};
        for (int i = 0; i < 3; i++) {
            k[i][0] += dx * share[i];
            k[i][1] += dy * share[i];
            k[i][2] += dz * share[i];
        }
    }

    // A world point into the preview frame of feet + yaw (toWorld's inverse).
    public static float[] toFrame(double x, double y, double z, double fx, double fy, double fz, float yawDeg) {
        double yr = Math.toRadians(yawDeg), c = Math.cos(yr), s = Math.sin(yr);
        double dx = x - fx, dz = z - fz;
        return new float[] {(float) (dx * c + dz * s), (float) (y - fy), (float) (-dx * s + dz * c)};
    }

    // three.js CatmullRomCurve3.getPoint, curveType 'centripetal', not closed.
    private static void catmull(float[][] pts, float t, float[] out) {
        int l = pts.length;
        float p = (l - 1) * t;
        int ip = (int) Math.floor(p);
        float w = p - ip;
        if (w == 0 && ip == l - 1) {
            ip = l - 2;
            w = 1;
        }
        float[] p0 = ip > 0 ? pts[ip - 1] : v(2 * pts[0][0] - pts[1][0], 2 * pts[0][1] - pts[1][1], 2 * pts[0][2] - pts[1][2]);
        float[] p1 = pts[ip], p2 = pts[ip + 1];
        float[] p3 = ip + 2 < l ? pts[ip + 2] : v(2 * pts[l - 1][0] - pts[l - 2][0], 2 * pts[l - 1][1] - pts[l - 2][1], 2 * pts[l - 1][2] - pts[l - 2][2]);
        float dt0 = (float) Math.pow(dist2(p0, p1), 0.25), dt1 = (float) Math.pow(dist2(p1, p2), 0.25), dt2 = (float) Math.pow(dist2(p2, p3), 0.25);
        if (dt1 < 1e-4f) {
            dt1 = 1;
        }
        if (dt0 < 1e-4f) {
            dt0 = dt1;
        }
        if (dt2 < 1e-4f) {
            dt2 = dt1;
        }
        for (int c = 0; c < 3; c++) {
            float x0 = p0[c], x1 = p1[c], x2 = p2[c], x3 = p3[c];
            float t1 = (x1 - x0) / dt0 - (x2 - x0) / (dt0 + dt1) + (x2 - x1) / dt1;
            float t2 = (x2 - x1) / dt1 - (x3 - x1) / (dt1 + dt2) + (x3 - x2) / dt2;
            t1 *= dt1;
            t2 *= dt1;
            float c0 = x1, c1 = t1, c2 = -3 * x1 + 3 * x2 - 2 * t1 - t2, c3 = 2 * x1 - 2 * x2 + t1 + t2;
            out[c] = c0 + c1 * w + c2 * w * w + c3 * w * w * w;
        }
    }

    // ---------- small math ----------

    public static float smooth(float x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public static float easeOut3(float x) {
        x = clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    public static float clamp(float x, float a, float b) {
        return Math.max(a, Math.min(b, x));
    }

    private static float dist2(float[] a, float[] b) {
        float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return x * x + y * y + z * z;
    }

    private static float dist(float[] a, float[] b) {
        return (float) Math.sqrt(dist2(a, b));
    }

    private static float[] sub(float[] a, float[] b) {
        return v(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
    }

    private static float[] cross(float[] a, float[] b) {
        return v(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
    }

    private static void norm(float[] a) {
        float n = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (n > 1e-8f) {
            a[0] /= n;
            a[1] /= n;
            a[2] /= n;
        }
    }

    // Preview frame to the world: feet + yaw (degrees, Minecraft's).
    public static double[] toWorld(float[] p, double fx, double fy, double fz, float yawDeg) {
        double y = Math.toRadians(yawDeg), c = Math.cos(y), s = Math.sin(y);
        return new double[] {fx + p[0] * c - p[2] * s, fy + p[1], fz + p[0] * s + p[2] * c};
    }
}
