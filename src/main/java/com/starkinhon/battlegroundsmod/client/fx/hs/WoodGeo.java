package com.starkinhon.battlegroundsmod.client.fx.hs;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3d;

// The approved Hashirama page's geometry library (forja/hashirama-boss/hashirama.js), line by line so the same seeds give
// the same wood: its random numbers (the LCG), its CPU value noise, the bent paths every branch follows, and the tube
// with its own ring per point (parallel transport) that every trunk, beam, stake, finger and spike is made of.
// All in page units (the player is 2 tall; HsWood scales by 0.9 into blocks).
public final class WoodGeo {
    private WoodGeo() {
    }

    // ---------- random numbers and noise, exactly like the page ----------

    // rnd(): rng = (rng * 1664525 + 1013904223) >>> 0
    public static final class Rng {
        long s;

        public Rng(double seed) {
            s = toUint32(seed);
        }

        public double next() {
            s = (s * 1664525L + 1013904223L) & 0xFFFFFFFFL;
            return s / 4294967296.0;
        }

        public void seed(double seed) {
            s = toUint32(seed);
        }
    }

    static long toUint32(double v) {
        return ((long) v) & 0xFFFFFFFFL;
    }

    private static int toInt32(double v) {
        return (int) (long) v;
    }

    static double hsh(int x, int y, int z) {
        int h = toInt32((double) x * 374761393.0 + (double) y * 668265263.0 + (double) z * 1442695041.0);
        h = toInt32((double) (h ^ (h >>> 13)) * 1274126177.0);
        return ((h ^ (h >>> 16)) & 0xffff) / 65535.0;
    }

    public static double vnoise(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = x - xi, fy = y - yi, fz = z - zi;
        double u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy), w = fz * fz * (3 - 2 * fz);
        return l(l(l(hsh(xi, yi, zi), hsh(xi + 1, yi, zi), u), l(hsh(xi, yi + 1, zi), hsh(xi + 1, yi + 1, zi), u), v),
                l(l(hsh(xi, yi, zi + 1), hsh(xi + 1, yi, zi + 1), u), l(hsh(xi, yi + 1, zi + 1), hsh(xi + 1, yi + 1, zi + 1), u), v), w) * 2 - 1;
    }

    private static double l(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    public static double smooth(double x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public static double easeOut3(double x) {
        x = clamp(x, 0, 1);
        return 1 - Math.pow(1 - x, 3);
    }

    public static Vector3d v(double x, double y, double z) {
        return new Vector3d(x, y, z);
    }

    // ---------- one mesh: the page's BufferGeometry ----------

    public static final class Geo {
        public int nv;
        public float[] p, n, c, b, d;
        public float[] u, ao;
        // vertex colors (flat lit pieces)
        public float[] k;
        // a tube drawn with the carved-surface wood (the Buddha's attack arms): uploaded as a surface
        public boolean surf;
        public int[] idx;
        // tube rebuild info
        int rings, seg;
        double circ, fl = 1;
        Vector3d[] side;
        boolean dirty = true;
        Object gpu;

        Geo(int nv, int ni, boolean tube) {
            this.nv = nv;
            p = new float[nv * 3];
            n = new float[nv * 3];
            if (tube) {
                c = new float[nv * 3];
                b = new float[nv * 2];
                u = new float[nv];
            }
            d = new float[nv * 3];
            idx = new int[ni];
        }

        public Geo copy() {
            Geo g = new Geo(nv, idx.length, c != null);
            System.arraycopy(p, 0, g.p, 0, p.length);
            System.arraycopy(n, 0, g.n, 0, n.length);
            System.arraycopy(d, 0, g.d, 0, d.length);
            System.arraycopy(idx, 0, g.idx, 0, idx.length);
            if (c != null) {
                System.arraycopy(c, 0, g.c, 0, c.length);
                System.arraycopy(b, 0, g.b, 0, b.length);
                System.arraycopy(u, 0, g.u, 0, u.length);
            }
            if (ao != null) {
                g.ao = ao.clone();
            }
            g.rings = rings;
            g.seg = seg;
            g.circ = circ;
            g.fl = fl;
            g.side = side;
            g.surf = surf;
            return g;
        }

        public void changed() {
            dirty = true;
        }

        // three's Mesh.scale baked into the vertices (and the centers), normals fixed for the squash.
        public Geo scale(double sx, double sy, double sz) {
            for (int i = 0; i < nv; i++) {
                p[i * 3] *= sx;
                p[i * 3 + 1] *= sy;
                p[i * 3 + 2] *= sz;
                if (c != null) {
                    c[i * 3] *= sx;
                    c[i * 3 + 1] *= sy;
                    c[i * 3 + 2] *= sz;
                }
                double nx = n[i * 3] / sx, ny = n[i * 3 + 1] / sy, nz = n[i * 3 + 2] / sz, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l > 1e-9) {
                    n[i * 3] = (float) (nx / l);
                    n[i * 3 + 1] = (float) (ny / l);
                    n[i * 3 + 2] = (float) (nz / l);
                }
            }
            dirty = true;
            return this;
        }

        // A group's transform (rotation q as a 3x3 row-major matrix m, then translation t, with uniform scale s) baked in.
        public Geo transform(double[] m, double s, double tx, double ty, double tz) {
            for (int i = 0; i < nv; i++) {
                xf(p, i, m, s, tx, ty, tz);
                if (c != null) {
                    xf(c, i, m, s, tx, ty, tz);
                }
                xf(n, i, m, 1, 0, 0, 0);
                xf(d, i, m, 1, 0, 0, 0);
            }
            dirty = true;
            return this;
        }

        private static void xf(float[] a, int i, double[] m, double s, double tx, double ty, double tz) {
            double x = a[i * 3], y = a[i * 3 + 1], z = a[i * 3 + 2];
            a[i * 3] = (float) ((m[0] * x + m[1] * y + m[2] * z) * s + tx);
            a[i * 3 + 1] = (float) ((m[3] * x + m[4] * y + m[5] * z) * s + ty);
            a[i * 3 + 2] = (float) ((m[6] * x + m[7] * y + m[8] * z) * s + tz);
        }
    }

    // ---------- the tube (anel por ponto, transporte paralelo) ----------

    static Vector3d[][] tubeFrames(Vector3d[] pts) {
        int n = pts.length;
        Vector3d[] T = new Vector3d[n], N = new Vector3d[n], B = new Vector3d[n];
        for (int i = 0; i < n; i++) {
            Vector3d t = new Vector3d(pts[Math.min(n - 1, i + 1)]).sub(pts[Math.max(0, i - 1)]);
            T[i] = t.lengthSquared() > 1e-10 ? t.normalize() : (i > 0 ? new Vector3d(T[i - 1]) : v(0, 1, 0));
        }
        Vector3d up = Math.abs(T[0].y) > 0.9 ? v(1, 0, 0) : v(0, 1, 0);
        N[0] = new Vector3d(up).cross(T[0]).normalize();
        B[0] = new Vector3d(T[0]).cross(N[0]);
        for (int i = 1; i < n; i++) {
            N[i] = new Vector3d(N[i - 1]);
            Vector3d ax = new Vector3d(T[i - 1]).cross(T[i]);
            double l = ax.length();
            if (l > 1e-6) {
                ax.div(l);
                N[i].rotateAxis(Math.acos(clamp(T[i - 1].dot(T[i]), -1, 1)), ax.x, ax.y, ax.z);
            }
            B[i] = new Vector3d(T[i]).cross(N[i]);
        }
        return new Vector3d[][] {N, B, T};
    }

    public static Geo tubeGeo(Vector3d[] pts, double[] radii, int seg, double circ) {
        return tubeGeo(pts, radii, seg, circ, null, 1);
    }

    // side: one vector for the whole tube or one per point (null = transported frames); fl squashes the ring along B.
    public static Geo tubeGeo(Vector3d[] pts, double[] radii, int seg, double circ, Vector3d[] side, double fl) {
        int n = pts.length, V = n * (seg + 1);
        Geo g = new Geo(V, (n - 1) * seg * 6, true);
        int k = 0;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < seg; j++) {
                int a = i * (seg + 1) + j, b = a + seg + 1;
                g.idx[k++] = a;
                g.idx[k++] = b;
                g.idx[k++] = a + 1;
                g.idx[k++] = b;
                g.idx[k++] = b + 1;
                g.idx[k++] = a + 1;
            }
        }
        g.rings = n;
        g.seg = seg;
        g.circ = circ;
        g.side = side;
        g.fl = fl;
        updateTube(g, pts, radii);
        return g;
    }

    public static void updateTube(Geo g, Vector3d[] pts, double[] radii) {
        int n = g.rings, seg = g.seg;
        double circ = g.circ, K = g.fl;
        Vector3d[][] F = tubeFrames(pts);
        Vector3d[] FN = F[0], FB = F[1];
        Vector3d[] Ts = new Vector3d[n];
        for (int i = 0; i < n; i++) {
            Vector3d t = new Vector3d(pts[Math.min(n - 1, i + 1)]).sub(pts[Math.max(0, i - 1)]);
            Ts[i] = t.lengthSquared() > 0 ? t.normalize() : v(0, 0, 0);
        }
        if (g.side != null) {
            for (int i = 0; i < n; i++) {
                Vector3d sd = g.side.length == 1 ? g.side[0] : g.side[i];
                Vector3d T = Ts[i];
                FN[i] = new Vector3d(sd).fma(-sd.dot(T), T).normalize();
                FB[i] = new Vector3d(T).cross(FN[i]);
            }
        }
        double[] s = new double[n];
        for (int i = 1; i < n; i++) {
            s[i] = s[i - 1] + pts[i].distance(pts[i - 1]);
        }
        double S = s[n - 1] != 0 ? s[n - 1] : 1;
        float[] P = g.p, Nn = g.n, C = g.c, U = g.u, Bb = g.b, D = g.d;
        for (int i = 0; i < n; i++) {
            Vector3d T = Ts[i];
            for (int j = 0; j <= seg; j++) {
                int kk = (i * (seg + 1) + j) * 3;
                D[kk] = (float) T.x;
                D[kk + 1] = (float) T.y;
                D[kk + 2] = (float) T.z;
            }
        }
        for (int i = 0; i < n; i++) {
            Vector3d Ni = FN[i], Bi = FB[i];
            for (int j = 0; j <= seg; j++) {
                double a = (double) j / seg * 6.2831853, cs = Math.cos(a), sn = Math.sin(a);
                int k = i * (seg + 1) + j;
                double r = radii[i];
                double ox = Ni.x * cs + Bi.x * sn * K, oy = Ni.y * cs + Bi.y * sn * K, oz = Ni.z * cs + Bi.z * sn * K;
                P[k * 3] = (float) (pts[i].x + ox * r);
                P[k * 3 + 1] = (float) (pts[i].y + oy * r);
                P[k * 3 + 2] = (float) (pts[i].z + oz * r);
                if (K != 1) {
                    double nx = Ni.x * cs * K + Bi.x * sn, ny = Ni.y * cs * K + Bi.y * sn, nz = Ni.z * cs * K + Bi.z * sn, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (nl == 0) {
                        nl = 1;
                    }
                    Nn[k * 3] = (float) (nx / nl);
                    Nn[k * 3 + 1] = (float) (ny / nl);
                    Nn[k * 3 + 2] = (float) (nz / nl);
                } else {
                    Nn[k * 3] = (float) (Ni.x * cs + Bi.x * sn);
                    Nn[k * 3 + 1] = (float) (Ni.y * cs + Bi.y * sn);
                    Nn[k * 3 + 2] = (float) (Ni.z * cs + Bi.z * sn);
                }
                C[k * 3] = (float) pts[i].x;
                C[k * 3 + 1] = (float) pts[i].y;
                C[k * 3 + 2] = (float) pts[i].z;
                U[k] = (float) (s[i] / S);
                Bb[k * 2] = (float) ((double) j / seg * circ);
                Bb[k * 2 + 1] = (float) s[i];
            }
        }
        g.dirty = true;
    }

    // ---------- paths ----------

    // curva torta: de A seguindo dir, curvando pra "bend", com tremor de ruído próprio
    public static Vector3d[] bentPath(Vector3d A, Vector3d dir, double len, Vector3d bend, int n, double seed, double wob) {
        Vector3d[] pts = new Vector3d[n];
        Vector3d p = new Vector3d(A), d = new Vector3d(dir).normalize();
        double step = len / (n - 1);
        for (int i = 0; i < n; i++) {
            pts[i] = new Vector3d(p);
            double u = (double) i / (n - 1);
            d.fma(step / len * 1.4, bend);
            d.add(vnoise(u * 3, seed, 1.7) * wob * step, vnoise(u * 3, seed, 5.1) * 0.5 * wob * step, vnoise(u * 3, seed, 9.3) * wob * step).normalize();
            p.fma(step, d);
        }
        return pts;
    }

    public static double[] taper(int n, double r0, double r1, double pw) {
        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            r[i] = i == n - 1 ? 0 : r0 + (r1 - r0) * Math.pow(u, pw);
        }
        return r;
    }

    public static double[] taper(int n, double r0, double r1) {
        return taper(n, r0, r1, 1);
    }

    // ---------- three's CatmullRomCurve3 ('catmullrom', tension 0.5), arc-length spaced points ----------

    public static final class Catmull {
        final Vector3d[] pts;
        final double tension;
        double[] lengths;

        public Catmull(Vector3d[] pts, double tension) {
            this.pts = pts;
            this.tension = tension;
        }

        public Vector3d point(double t) {
            int l = pts.length;
            double p = (l - 1) * t;
            int ip = (int) Math.floor(p);
            double w = p - ip;
            if (w == 0 && ip == l - 1) {
                ip = l - 2;
                w = 1;
            }
            Vector3d p0 = ip > 0 ? pts[(ip - 1) % l] : new Vector3d(pts[0]).sub(pts[1]).add(pts[0]);
            Vector3d p1 = pts[ip % l], p2 = pts[(ip + 1) % l];
            Vector3d p3 = ip + 2 < l ? pts[(ip + 2) % l] : new Vector3d(pts[l - 1]).sub(pts[l - 2]).add(pts[l - 1]);
            return v(cr(p0.x, p1.x, p2.x, p3.x, w), cr(p0.y, p1.y, p2.y, p3.y, w), cr(p0.z, p1.z, p2.z, p3.z, w));
        }

        private double cr(double x0, double x1, double x2, double x3, double t) {
            double t0 = tension * (x2 - x0), t1 = tension * (x3 - x1);
            double c0 = x1, c1 = t0, c2 = -3 * x1 + 3 * x2 - 2 * t0 - t1, c3 = 2 * x1 - 2 * x2 + t0 + t1;
            double t2 = t * t;
            return c0 + c1 * t + c2 * t2 + c3 * t2 * t;
        }

        private void lengths() {
            int div = 200;
            lengths = new double[div + 1];
            Vector3d last = point(0);
            for (int p = 1; p <= div; p++) {
                Vector3d cur = point((double) p / div);
                lengths[p] = lengths[p - 1] + cur.distance(last);
                last = cur;
            }
        }

        // three's getUtoTmapping
        double uToT(double u) {
            if (lengths == null) {
                lengths();
            }
            int il = lengths.length;
            double target = u * lengths[il - 1];
            int low = 0, high = il - 1;
            while (low <= high) {
                int i = (int) Math.floor(low + (high - low) / 2.0);
                double cmp = lengths[i] - target;
                if (cmp < 0) {
                    low = i + 1;
                } else if (cmp > 0) {
                    high = i - 1;
                } else {
                    high = i;
                    break;
                }
            }
            int i = high;
            if (lengths[i] == target) {
                return (double) i / (il - 1);
            }
            double lb = lengths[i], la = lengths[i + 1], seg = la - lb;
            double frac = (target - lb) / seg;
            return (i + frac) / (il - 1);
        }

        public Vector3d[] spaced(int divisions) {
            Vector3d[] out = new Vector3d[divisions + 1];
            for (int d = 0; d <= divisions; d++) {
                out[d] = point(uToT((double) d / divisions));
            }
            return out;
        }
    }

    // smoothTube: curve through pts sampled in n points, radii interpolated, little bumps (page smoothTube)
    public static Object[] smoothTube(Vector3d[] pts, double[] radii, int n, double bump, double bumpF) {
        Vector3d[] P = new Catmull(pts, 0.5).spaced(n - 1);
        double[] R = new double[n];
        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1) * (radii.length - 1);
            int k = Math.min(radii.length - 2, (int) Math.floor(f));
            double w = f - k, r = radii[k] + (radii[k + 1] - radii[k]) * w;
            R[i] = i == n - 1 ? 0 : r * (1 + bump * Math.pow(Math.max(0, Math.sin((double) i / (n - 1) * bumpF * 6.283 + vnoise(i * 0.3, 2, 7) * 1.2)), 2));
        }
        return new Object[] {P, R};
    }

    // ---------- merging (fan.js mergeGeos) ----------

    public static Geo merge(List<Geo> list) {
        int V = 0, I = 0;
        boolean tube = true;
        for (Geo g : list) {
            V += g.nv;
            I += g.idx.length;
            tube &= g.c != null;
        }
        Geo out = new Geo(V, I, tube);
        boolean anyAo = list.stream().anyMatch(g -> g.ao != null);
        if (anyAo) {
            out.ao = new float[V];
        }
        int v0 = 0, i0 = 0;
        for (Geo g : list) {
            System.arraycopy(g.p, 0, out.p, v0 * 3, g.nv * 3);
            System.arraycopy(g.n, 0, out.n, v0 * 3, g.nv * 3);
            System.arraycopy(g.d, 0, out.d, v0 * 3, g.nv * 3);
            if (tube) {
                System.arraycopy(g.c, 0, out.c, v0 * 3, g.nv * 3);
                System.arraycopy(g.b, 0, out.b, v0 * 2, g.nv * 2);
                System.arraycopy(g.u, 0, out.u, v0, g.nv);
            }
            if (anyAo) {
                for (int k = 0; k < g.nv; k++) {
                    out.ao[v0 + k] = g.ao != null ? g.ao[k] : 1;
                }
            }
            for (int k = 0; k < g.idx.length; k++) {
                out.idx[i0 + k] = g.idx[k] + v0;
            }
            v0 += g.nv;
            i0 += g.idx.length;
        }
        return out;
    }

    public static Geo merge(Geo... list) {
        List<Geo> l = new ArrayList<>(List.of(list));
        return merge(l);
    }

    // ---------- colors (THREE.Color hex, offsetHSL) ----------

    public static float[] hex(String h) {
        int c = Integer.parseInt(h.substring(1), 16);
        return new float[] {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    public static float[] offsetHSL(float[] rgb, double dh, double ds, double dl) {
        double r = rgb[0], g = rgb[1], b = rgb[2], max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double h = 0, s, l = (min + max) / 2;
        if (min == max) {
            s = 0;
        } else {
            double delta = max - min;
            s = l <= 0.5 ? delta / (max + min) : delta / (2 - max - min);
            if (max == r) {
                h = (g - b) / delta + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / delta + 2;
            } else {
                h = (r - g) / delta + 4;
            }
            h /= 6;
        }
        h += dh;
        s += ds;
        l += dl;
        h = ((h % 1) + 1) % 1;
        s = clamp(s, 0, 1);
        l = clamp(l, 0, 1);
        if (s == 0) {
            return new float[] {(float) l, (float) l, (float) l};
        }
        double p = l <= 0.5 ? l * (1 + s) : l + s - l * s, q = 2 * l - p;
        return new float[] {(float) hue(q, p, h + 1 / 3.0), (float) hue(q, p, h), (float) hue(q, p, h - 1 / 3.0)};
    }

    private static double hue(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1 / 6.0) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1 / 2.0) {
            return q;
        }
        if (t < 2 / 3.0) {
            return p + (q - p) * 6 * (2 / 3.0 - t);
        }
        return p;
    }
}
