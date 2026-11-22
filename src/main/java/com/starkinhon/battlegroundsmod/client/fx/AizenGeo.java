// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// The page's geometry helpers for Aizen's kit (forja/aizen-kit/aizen.js), carried over: the CPU tube with parallel
// transported frames (Tube), the jagged lightning line (jag), three's centripetal CatmullRomCurve3 (Curve), the wings'
// fan (wingGeo), the sculpted dragon head (makeDragonHeadE) and the star's spikes. AizenKit drives them.
final class AizenGeo {
    private AizenGeo() {
    }

    // The page's hsh(x, y, z), bit for bit.
    static float hsh(int x, int y, int z) {
        return BleachFx.hsh(x, y, z);
    }

    static Vector3f[] pts(int n) {
        Vector3f[] p = new Vector3f[n + 1];
        for (int i = 0; i <= n; i++) {
            p[i] = new Vector3f();
        }
        return p;
    }

    // ---------- Tube: NS+1 points, NR around; R(i) the radius; mod(i, ang, o) may change o[0] = r, o[1] = n, o[2] = b ----------

    interface Mod {
        void apply(int i, double ang, float[] o);
    }

    static final class Tube {
        final int ns;
        final int nr;
        final Vector3f[] tg, nn, bb;
        final AizenMesh.Geo geo;
        private final float[] pos, nor, uv;
        private final int[] idx;

        Tube(int ns, int nr, boolean dynamic) {
            this.ns = ns;
            this.nr = nr;
            tg = pts(ns);
            nn = pts(ns);
            bb = pts(ns);
            geo = new AizenMesh.Geo(dynamic);
            int n = (ns + 1) * (nr + 1);
            pos = new float[n * 3];
            nor = new float[n * 3];
            uv = new float[n * 2];
            idx = new int[ns * nr * 6];
            int q = 0;
            for (int i = 0; i <= ns; i++) {
                for (int j = 0; j <= nr; j++) {
                    int k = i * (nr + 1) + j;
                    uv[k * 2] = (float) i / ns;
                    uv[k * 2 + 1] = (float) j / nr;
                    if (i < ns && j < nr) {
                        int a = k, b = k + nr + 1;
                        idx[q++] = a; idx[q++] = a + 1; idx[q++] = b;
                        idx[q++] = b; idx[q++] = a + 1; idx[q++] = b + 1;
                    }
                }
            }
        }

        void update(Vector3f[] p, IntToDoubleFunction r, Mod mod) {
            for (int i = 0; i <= ns; i++) {
                Vector3f a = p[Math.max(0, i - 1)], b = p[Math.min(ns, i + 1)];
                tg[i].set(b).sub(a);
                if (tg[i].lengthSquared() < 1.0E-12f) {
                    tg[i].set(i > 0 ? tg[i - 1] : new Vector3f(0, 0, 1));
                }
                tg[i].normalize();
            }
            Vector3f up = Math.abs(tg[0].y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            tg[0].cross(up, nn[0]).normalize();
            tg[0].cross(nn[0], bb[0]).normalize();
            for (int i = 1; i <= ns; i++) {
                Vector3f t = tg[i], np = nn[i - 1];
                nn[i].set(np).fma(-np.dot(t), t);
                if (nn[i].lengthSquared() < 1.0E-12f) {
                    nn[i].set(np);
                }
                nn[i].normalize();
                t.cross(nn[i], bb[i]).normalize();
            }
            float[] o = new float[3];
            for (int i = 0; i <= ns; i++) {
                Vector3f c = p[i], n = nn[i], b = bb[i];
                float r0 = (float) r.applyAsDouble(i);
                for (int j = 0; j <= nr; j++) {
                    double ang = (double) j / nr * Math.PI * 2;
                    float cx = (float) Math.cos(ang), cy = (float) Math.sin(ang);
                    int k = (i * (nr + 1) + j) * 3;
                    o[0] = r0;
                    o[1] = 0;
                    o[2] = 0;
                    if (mod != null) {
                        mod.apply(i, ang, o);
                    }
                    float nx = n.x * cx + b.x * cy, ny = n.y * cx + b.y * cy, nz = n.z * cx + b.z * cy;
                    pos[k] = c.x + nx * o[0] + n.x * o[1] + b.x * o[2];
                    pos[k + 1] = c.y + ny * o[0] + n.y * o[1] + b.y * o[2];
                    pos[k + 2] = c.z + nz * o[0] + n.z * o[1] + b.z * o[2];
                    nor[k] = nx;
                    nor[k + 1] = ny;
                    nor[k + 2] = nz;
                }
            }
            geo.reset();
            geo.indexed(pos, uv, nor, idx);
        }

        // Bakes the tube as it is now into another mesh (a static part of a head or a wing).
        void bakeInto(AizenMesh.Geo into, Matrix4f m) {
            Matrix3f nm = new Matrix3f(m).invert().transpose();
            for (int i : idx) {
                Vector3f p = m.transformPosition(new Vector3f(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]));
                Vector3f n = nm.transform(new Vector3f(nor[i * 3], nor[i * 3 + 1], nor[i * 3 + 2])).normalize();
                into.v(p.x, p.y, p.z, uv[i * 2], uv[i * 2 + 1], n.x, n.y, n.z);
            }
        }
    }

    // ---------- jag: a broken line from A to B (big bends every K points + fine shake; two seeds mixed) ----------

    static Vector3f[] jag(Vector3f a, Vector3f b, int n, int seedA, int seedB, float mixK, float amp, int kk, Vector3f[] out, boolean pinEnd) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-5f) {
            d.set(0, 0, -1);
        } else {
            d.div(len);
        }
        Vector3f p1 = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f p2 = new Vector3f(d).cross(p1);
        for (int i = 0; i <= n; i++) {
            float u = (float) i / n;
            float env = pinEnd ? (float) Math.pow(Math.sin(Math.PI * u), 0.55) : (float) Math.pow(Math.min(1, u * 3), 0.7);
            float ox = lerp(off(seedA, i, 1, kk), off(seedB, i, 1, kk), mixK) * amp * env;
            float oy = lerp(off(seedA, i, 2, kk), off(seedB, i, 2, kk), mixK) * amp * env;
            out[i].set(a).fma(len * u, d).fma(ox, p1).fma(oy, p2);
        }
        return out;
    }

    private static float off(int s, int i, int ax, int kk) {
        int k0 = Math.floorDiv(i, kk);
        float f = (float) i / kk - k0;
        float h0 = hsh(s, k0, ax) * 2 - 1, h1 = hsh(s, k0 + 1, ax) * 2 - 1;
        return lerp(h0, h1, f) + 0.22f * (hsh(s, i, ax + 7) * 2 - 1);
    }

    static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    // ---------- three's CatmullRomCurve3 (centripetal), with getPointAt / getTangentAt by arc length ----------

    static final class Curve {
        final Vector3f[] p;
        final boolean closed;
        private final float[] lengths;
        final float length;
        private static final int DIV = 400;

        Curve(Vector3f[] points, boolean closed) {
            this.p = points;
            this.closed = closed;
            lengths = new float[DIV + 1];
            Vector3f last = point(0, new Vector3f()), cur = new Vector3f();
            float sum = 0;
            for (int i = 1; i <= DIV; i++) {
                point((float) i / DIV, cur);
                sum += cur.distance(last);
                lengths[i] = sum;
                last.set(cur);
            }
            length = sum;
        }

        Vector3f point(float t, Vector3f out) {
            int l = p.length;
            float pp = (l - (closed ? 0 : 1)) * t;
            int ip = (int) Math.floor(pp);
            float w = pp - ip;
            if (closed) {
                ip += ip > 0 ? 0 : ((int) Math.floor(Math.abs(ip) / (float) l) + 1) * l;
            } else if (w == 0 && ip == l - 1) {
                ip = l - 2;
                w = 1;
            }
            Vector3f p0, p3;
            if (closed || ip > 0) {
                p0 = p[(ip - 1 + l) % l];
            } else {
                p0 = new Vector3f(p[0]).sub(p[1]).add(p[0]);
            }
            Vector3f p1 = p[ip % l], p2 = p[(ip + 1) % l];
            if (closed || ip + 2 < l) {
                p3 = p[(ip + 2) % l];
            } else {
                p3 = new Vector3f(p[l - 1]).sub(p[l - 2]).add(p[l - 1]);
            }
            float dt0 = (float) Math.pow(p0.distanceSquared(p1), 0.25), dt1 = (float) Math.pow(p1.distanceSquared(p2), 0.25),
                    dt2 = (float) Math.pow(p2.distanceSquared(p3), 0.25);
            if (dt1 < 1.0E-4f) {
                dt1 = 1;
            }
            if (dt0 < 1.0E-4f) {
                dt0 = dt1;
            }
            if (dt2 < 1.0E-4f) {
                dt2 = dt1;
            }
            out.x = cr(p0.x, p1.x, p2.x, p3.x, dt0, dt1, dt2, w);
            out.y = cr(p0.y, p1.y, p2.y, p3.y, dt0, dt1, dt2, w);
            out.z = cr(p0.z, p1.z, p2.z, p3.z, dt0, dt1, dt2, w);
            return out;
        }

        private static float cr(float x0, float x1, float x2, float x3, float dt0, float dt1, float dt2, float t) {
            float t1 = (x1 - x0) / dt0 - (x2 - x0) / (dt0 + dt1) + (x2 - x1) / dt1;
            float t2 = (x2 - x1) / dt1 - (x3 - x1) / (dt1 + dt2) + (x3 - x2) / dt2;
            t1 *= dt1;
            t2 *= dt1;
            float c0 = x1, c1 = t1, c2 = -3 * x1 + 3 * x2 - 2 * t1 - t2, c3 = 2 * x1 - 2 * x2 + t1 + t2;
            return c0 + c1 * t + c2 * t * t + c3 * t * t * t;
        }

        private float uToT(float u) {
            float target = Math.max(0, Math.min(1, u)) * length;
            int lo = 0, hi = DIV;
            while (lo < hi - 1) {
                int mid = (lo + hi) >>> 1;
                if (lengths[mid] < target) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            float seg = lengths[hi] - lengths[lo];
            float f = seg < 1.0E-6f ? 0 : (target - lengths[lo]) / seg;
            return (lo + f) / DIV;
        }

        Vector3f pointAt(float u, Vector3f out) {
            return point(uToT(u), out);
        }

        Vector3f tangentAt(float u, Vector3f out) {
            float t = uToT(u), d = 1.0E-4f;
            float t1 = Math.max(0, t - d), t2 = Math.min(1, t + d);
            Vector3f a = point(t1, new Vector3f()), b = point(t2, new Vector3f());
            return out.set(b).sub(a).normalize();
        }
    }

    // ---------- the Hogyoku's wings: a fan from the root, the outer edge torn ----------

    private static final float[][][] WING_BASE = {
            {{0, 0}, {0.4f, 1.6f}, {1.5f, 3.0f}, {2.6f, 3.1f}, {3.2f, 2.4f}, {2.6f, 1.1f}, {0, 0}},
            {{0, 0}, {1.0f, 0.6f}, {2.4f, 0.7f}, {2.9f, 0.0f}, {2.4f, -0.9f}, {1.2f, -0.9f}, {0, 0}},
            {{0, 0}, {0.6f, -0.5f}, {1.3f, -1.7f}, {1.5f, -2.9f}, {1.0f, -3.3f}, {0.6f, -2.4f}, {0, 0}}};

    // UV0.x = aR (0 at the root, 1 at the edge); positions in the wing's own plane (the shader reads xy)
    static AizenMesh.Geo wing(int kind, int seed) {
        Vector3f[] base = new Vector3f[WING_BASE[kind].length];
        for (int i = 0; i < base.length; i++) {
            base[i] = new Vector3f(WING_BASE[kind][i][0], WING_BASE[kind][i][1], 0);
        }
        Curve curve = new Curve(base, true);
        int m = 120;
        Vector3f[] out = new Vector3f[m];
        for (int i = 0; i < m; i++) {
            float u = (float) i / m;
            Vector3f p = curve.pointAt(u, new Vector3f()), t = curve.tangentAt(u, new Vector3f());
            Vector3f nrm = new Vector3f(t.y, -t.x, 0);
            float far = BleachFx.smooth(BleachFx.clamp((p.length() - 1.0f) / 1.6f, 0, 1));
            float saw = (float) Math.pow((u * (14 + kind * 3) + hsh(seed, (int) Math.floor(u * 40), 3) * 0.6f) % 1, 2.5);
            float notch = (hsh(seed, (int) Math.floor(u * 9), 5) > 0.72f ? 0.35f : 0) * (float) Math.sin(((u * 9) % 1) * Math.PI);
            p.fma(-(saw * 0.22f + notch) * far, nrm);
            out[i] = p;
        }
        int rings = 6;
        List<float[]> verts = new ArrayList<>();
        verts.add(new float[] {0, 0, 0, 0});
        for (int r = 1; r <= rings; r++) {
            float k = (float) r / rings;
            for (int i = 0; i < m; i++) {
                verts.add(new float[] {out[i].x * k, out[i].y * k, (float) Math.sin(k * Math.PI) * 0.12f * (kind == 2 ? -1 : 1), k});
            }
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            idx.add(0); idx.add(1 + i); idx.add(1 + (i + 1) % m);
        }
        for (int r = 1; r < rings; r++) {
            for (int i = 0; i < m; i++) {
                int a = 1 + (r - 1) * m + i, b = 1 + (r - 1) * m + (i + 1) % m, c = a + m, d = b + m;
                idx.add(a); idx.add(c); idx.add(b);
                idx.add(b); idx.add(c); idx.add(d);
            }
        }
        AizenMesh.Geo g = new AizenMesh.Geo(false);
        for (int i : idx) {
            float[] v = verts.get(i);
            g.v(v[0], v[1], v[2], v[3], 0, 0, 0, 1);
        }
        return g;
    }

    // the long tendril under the lower wing (a Lambert tube), in the wing's own space
    static AizenMesh.Geo tendril() {
        Tube tube = new Tube(40, 6, false);
        Vector3f[] p = pts(40);
        for (int i = 0; i <= 40; i++) {
            float u = i / 40f;
            p[i].set(1.1f + 0.5f * u + (float) Math.sin(u * 5) * 0.15f, -3.0f - u * 2.2f, 0.15f * (float) Math.sin(u * 7));
        }
        tube.update(p, i -> 0.06 * Math.pow(1 - i / 40.0, 0.8) + 0.008, null);
        return tube.geo;
    }

    // ---------- cones (three's ConeGeometry, moved and turned by m); UV0.y = the cone's own y + 0.5 (the spikes' shader) ----------

    static void cone(AizenMesh.Geo g, Matrix4f m, float r, float h, int radial, float shiftY) {
        Matrix3f nm = new Matrix3f(m).invert().transpose();
        float slope = r / h;
        for (int x = 0; x < radial; x++) {
            double a0 = 2 * Math.PI * x / radial, a1 = 2 * Math.PI * (x + 1) / radial;
            float[] top = {0, h / 2 + shiftY, 0};
            float[] b0 = {(float) Math.sin(a0) * r, -h / 2 + shiftY, (float) Math.cos(a0) * r};
            float[] b1 = {(float) Math.sin(a1) * r, -h / 2 + shiftY, (float) Math.cos(a1) * r};
            Vector3f n0 = new Vector3f((float) Math.sin(a0), slope, (float) Math.cos(a0)).normalize();
            Vector3f n1 = new Vector3f((float) Math.sin(a1), slope, (float) Math.cos(a1)).normalize();
            Vector3f nt = new Vector3f(n0).add(n1).normalize();
            cv(g, m, nm, top, nt);
            cv(g, m, nm, b0, n0);
            cv(g, m, nm, b1, n1);
            Vector3f down = new Vector3f(0, -1, 0);
            cv(g, m, nm, new float[] {0, -h / 2 + shiftY, 0}, down);
            cv(g, m, nm, b1, down);
            cv(g, m, nm, b0, down);
        }
    }

    private static void cv(AizenMesh.Geo g, Matrix4f m, Matrix3f nm, float[] p, Vector3f n) {
        Vector3f q = m.transformPosition(new Vector3f(p[0], p[1], p[2]));
        Vector3f nn = nm.transform(new Vector3f(n)).normalize();
        g.v(q.x, q.y, q.z, 0, p[1] + 0.5f, nn.x, nn.y, nn.z);
    }

    // ---------- a sculpted sphere (the page's sculptGeo on a SphereGeometry, then computeVertexNormals) ----------

    interface Sculpt {
        Vector3f apply(Vector3f p);
    }

    static void sculpted(AizenMesh.Geo g, Matrix4f m, int w, int h, Sculpt f) {
        int nv = (w + 1) * (h + 1);
        float[] pos = new float[nv * 3];
        int k = 0;
        for (int iy = 0; iy <= h; iy++) {
            double th = Math.PI * iy / h;
            for (int ix = 0; ix <= w; ix++) {
                double phi = 2 * Math.PI * ix / w;
                Vector3f p = new Vector3f((float) (-Math.cos(phi) * Math.sin(th)), (float) Math.cos(th), (float) (Math.sin(phi) * Math.sin(th)));
                Vector3f r = m.transformPosition(f.apply(p));
                pos[k * 3] = r.x; pos[k * 3 + 1] = r.y; pos[k * 3 + 2] = r.z;
                k++;
            }
        }
        List<Integer> il = new ArrayList<>();
        for (int iy = 0; iy < h; iy++) {
            for (int ix = 0; ix < w; ix++) {
                int a = iy * (w + 1) + ix + 1, b = iy * (w + 1) + ix, c = (iy + 1) * (w + 1) + ix, d = (iy + 1) * (w + 1) + ix + 1;
                if (iy != 0) {
                    il.add(a); il.add(b); il.add(d);
                }
                if (iy != h - 1) {
                    il.add(b); il.add(c); il.add(d);
                }
            }
        }
        int[] idx = il.stream().mapToInt(Integer::intValue).toArray();
        float[] nor = new float[nv * 3];
        for (int i = 0; i < idx.length; i += 3) {
            int a = idx[i], b = idx[i + 1], c = idx[i + 2];
            Vector3f pa = new Vector3f(pos[a * 3], pos[a * 3 + 1], pos[a * 3 + 2]), pb = new Vector3f(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2]),
                    pc = new Vector3f(pos[c * 3], pos[c * 3 + 1], pos[c * 3 + 2]);
            Vector3f n = new Vector3f(pc).sub(pb).cross(new Vector3f(pa).sub(pb));
            for (int v : new int[] {a, b, c}) {
                nor[v * 3] += n.x; nor[v * 3 + 1] += n.y; nor[v * 3 + 2] += n.z;
            }
        }
        for (int i = 0; i < nv; i++) {
            Vector3f n = new Vector3f(nor[i * 3], nor[i * 3 + 1], nor[i * 3 + 2]);
            if (n.lengthSquared() > 1.0E-12f) {
                n.normalize();
            }
            nor[i * 3] = n.x; nor[i * 3 + 1] = n.y; nor[i * 3 + 2] = n.z;
        }
        g.indexed(pos, null, nor, idx);
    }

    // ---------- the dragon's sculpted head (front = -z): skull and snout, open jaw, fangs, horns, blade mane, lit eyes ----------

    static final class Head {
        final AizenMesh.Geo skin = new AizenMesh.Geo(false), teeth = new AizenMesh.Geo(false), throat = new AizenMesh.Geo(false), eyes = new AizenMesh.Geo(false);
        final AizenMesh.Geo jawSkin = new AizenMesh.Geo(false), jawTeeth = new AizenMesh.Geo(false);
        final Tube[] whiskers = {new Tube(30, 5, true), new Tube(30, 5, true)};
        final float[] whiskerLen = new float[2];

        void close() {
            for (AizenMesh.Geo g : new AizenMesh.Geo[] {skin, teeth, throat, eyes, jawSkin, jawTeeth, whiskers[0].geo, whiskers[1].geo}) {
                g.close();
            }
        }
    }

    static Head head(int seed) {
        Head g = new Head();
        java.util.function.IntFunction<Float> r = k -> hsh(seed, k, 77);
        Matrix4f id = new Matrix4f();
        // skull + snout: a sphere stretched forward, flattened, the snout thinning and rising at the tip, brows on the forehead
        sculpted(g.skin, id, 56, 40, p -> {
            float zf = -p.z;
            float len = zf > 0 ? 1 + 1.6f * zf : 1;
            float x = p.x * (zf > 0 ? 0.95f - 0.5f * (float) Math.pow(zf, 1.6) : 0.95f);
            float y = p.y * (zf > 0 ? 0.7f - 0.32f * (float) Math.pow(zf, 1.4) : 0.72f);
            float yy = y + (p.y > 0 ? 0.06f : -0.02f) + 0.12f * (float) Math.pow(Math.max(0, zf), 3) * (p.y > 0 ? 1 : 0.3f);
            float brow = (float) Math.exp(-Math.pow((zf - 0.25) / 0.18, 2)) * Math.max(0, p.y) * (float) Math.pow(Math.abs(p.x), 0.5) * 0.35f;
            yy += brow;
            return new Vector3f(x * (1 + 0.08f * Math.max(0, -zf)), yy + 0.12f, p.z * len);
        });
        // lower jaw, hinged behind (in the jaw's own space; AizenKit turns it)
        sculpted(g.jawSkin, id, 40, 28, p -> {
            float zf = -p.z;
            return new Vector3f(p.x * (0.78f - 0.42f * Math.max(0, zf)), p.y * 0.26f - 0.18f - 0.05f * Math.max(0, zf), (p.z - 0.4f) * 1.45f - 0.3f);
        });
        // the dark throat inside
        sculpted(g.throat, id, 28, 20, p -> new Vector3f(p.x * 0.55f, p.y * 0.3f - 0.12f, p.z * 1.5f - 0.6f));
        // fangs, up and down, each with its own size and curve
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 6; k++) {
                float z = -0.5f - k * 0.32f;
                boolean big = k == 4;
                float len = big ? 0.55f : 0.18f + 0.14f * r.apply(k + side * 10), w = big ? 0.09f : 0.045f + 0.02f * r.apply(k + 40);
                Matrix4f up = new Matrix4f().translate(side * (0.48f - k * 0.055f), -0.02f, z).rotateXYZ(0.15f, 0, -side * 0.12f);
                cone(g.teeth, up, w, len, 5, -len / 2);
                // lower fangs live in the jaw (the jaw's origin is at (0, -0.05, 0.4) in the head)
                Matrix4f dn = new Matrix4f().translate(side * (0.42f - k * 0.05f), -0.12f, z + 0.1f - 0.4f).rotateXYZ(-0.15f, 0, side * 0.12f);
                cone(g.jawTeeth, dn, w * 0.9f, len * 0.8f, 5, len * 0.4f);
            }
        }
        // horns: two long ones sweeping back and up
        for (int i = 0; i < 2; i++) {
            int sx = i == 0 ? -1 : 1;
            Tube tb = new Tube(24, 8, false);
            Vector3f[] p = pts(24);
            float len = 2.4f + 0.9f * r.apply(50 + i);
            for (int k = 0; k <= 24; k++) {
                float u = k / 24f;
                p[k].set(sx * (0.38f + 0.55f * u + 0.1f * (float) Math.sin(u * 3)), 0.55f + 0.9f * u - 0.35f * u * u, 0.2f + len * u);
            }
            tb.update(p, k -> 0.17 * Math.pow(1 - k / 24.0, 0.8) + 0.01, null);
            tb.bakeInto(g.skin, id);
        }
        // mane: wide blades from the nape going back, each with its own length and angle
        for (int k = 0; k < 9; k++) {
            float sx = (k % 2 == 1 ? 1 : -1) * (0.15f + 0.6f * r.apply(60 + k));
            Tube tb = new Tube(14, 6, false);
            Vector3f[] p = pts(14);
            float len = 1.2f + 1.6f * r.apply(70 + k), y0 = 0.35f + 0.35f * r.apply(80 + k);
            for (int j = 0; j <= 14; j++) {
                float u = j / 14f;
                p[j].set(sx * (1 + 0.8f * u), y0 + 0.5f * u - 0.6f * u * u, 0.35f + k * 0.12f + len * u);
            }
            float w0 = 0.16f + 0.08f * r.apply(90 + k);
            tb.update(p, j -> w0 * Math.pow(1 - j / 14.0, 1.1) + 0.005, (j, ang, o) -> o[0] *= 1 + 0.9f * (float) Math.abs(Math.cos(ang)));
            tb.bakeInto(g.skin, id);
        }
        g.whiskerLen[0] = 3 + r.apply(95);
        g.whiskerLen[1] = 3 + r.apply(96);
        // lit eyes
        for (int sx = -1; sx <= 1; sx += 2) {
            sculpted(g.eyes, new Matrix4f().translate(sx * 0.6f, 0.45f, -0.35f).scale(0.12f * 1.3f, 0.12f * 0.6f, 0.12f), 12, 8, p -> p);
        }
        return g;
    }

    // whiskers waving (in the head's own space)
    static void whiskers(Head h, float time) {
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? -1 : 1;
            Vector3f[] p = pts(30);
            for (int k = 0; k <= 30; k++) {
                float u = k / 30f;
                p[k].set(sx * (0.35f + 0.9f * u), -0.05f - 0.4f * u + 0.25f * (float) Math.sin(u * 6 - time * 4 + sx), -1.6f + 1.2f * u + h.whiskerLen[i] * u * 0.9f);
            }
            h.whiskers[i].update(p, k -> 0.05 * Math.pow(1 - k / 30.0, 0.7) + 0.005, null);
        }
    }
}
