package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

// Mesh builder for the 3D outfits. Coordinates are in "rig pixels": 1 unit = 1 pixel of the 64x64 player, y up, the
// player facing +z (the same space as the Guarda-roupa viewer). Each grid gets its winding turned so its normals face
// the side the caller says is outside, then the normals are smoothed inside the grid only, so edges between grids
// stay sharp. bake() gives triangles of 11 floats per vertex: x y z u v r g b nx ny nz.
final class Geo {
    interface GridFn {
        // u, v in 0..1 -> x, y, z, texture u, texture v
        float[] at(float u, float v);
    }

    interface Hint {
        // Which way is "outside" around this point.
        Vector3f at(float x, float y, float z);
    }

    private final List<float[]> verts = new ArrayList<>();
    private final List<int[]> tris = new ArrayList<>();
    private final List<Integer> groupOf = new ArrayList<>();
    private int groups = 0;
    private float r = 1, g = 1, b = 1;

    Geo color(int rgb) {
        return color(rgb, 1);
    }

    Geo color(int rgb, float shade) {
        r = ((rgb >> 16) & 255) / 255f * shade;
        g = ((rgb >> 8) & 255) / 255f * shade;
        b = (rgb & 255) / 255f * shade;
        return this;
    }

    int vertex(float x, float y, float z, float u, float v, float shade) {
        verts.add(new float[] {x, y, z, u, v, r * shade, g * shade, b * shade});
        return verts.size() - 1;
    }

    void tri(int a, int b, int c) {
        tris.add(new int[] {a, b, c});
        groupOf.add(groups);
    }

    // A grid of (nu + 1) x (nv + 1) vertices. shade may be null.
    int grid(int nu, int nv, GridFn fn, ShadeFn shade, Hint hint) {
        int base = verts.size();
        for (int i = 0; i <= nv; i++) {
            for (int j = 0; j <= nu; j++) {
                float u = j / (float) nu, v = i / (float) nv;
                float[] q = fn.at(u, v);
                vertex(q[0], q[1], q[2], q[3], q[4], shade == null ? 1 : shade.at(u, v));
            }
        }
        int w = nu + 1;
        int first = tris.size();
        for (int i = 0; i < nv; i++) {
            for (int j = 0; j < nu; j++) {
                int a = base + i * w + j, bb = a + 1, c = a + w, d = c + 1;
                tri(a, c, bb);
                tri(bb, c, d);
            }
        }
        orient(first, hint);
        groups++;
        return base;
    }

    interface ShadeFn {
        float at(float u, float v);
    }

    // A fan closing a ring of vertices (first..first+count-1) around their middle.
    void cap(int first, int count, Hint hint) {
        float cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < count; i++) {
            float[] q = verts.get(first + i);
            cx += q[0];
            cy += q[1];
            cz += q[2];
        }
        int c = vertex(cx / count, cy / count, cz / count, 0.5f, 0.5f, 1);
        int start = tris.size();
        for (int i = 0; i < count - 1; i++) {
            tri(c, first + i, first + i + 1);
        }
        orient(start, hint);
        groups++;
    }

    // Turns every triangle from index first on so that, on the whole, they face where the hint says.
    private void orient(int first, Hint hint) {
        if (hint == null) {
            return;
        }
        float sum = 0;
        Vector3f n = new Vector3f();
        for (int t = first; t < tris.size(); t++) {
            int[] tr = tris.get(t);
            face(tr, n);
            float[] a = verts.get(tr[0]);
            sum += n.dot(hint.at(a[0], a[1], a[2]));
        }
        if (sum < 0) {
            for (int t = first; t < tris.size(); t++) {
                int[] tr = tris.get(t);
                int k = tr[1];
                tr[1] = tr[2];
                tr[2] = k;
            }
        }
    }

    private void face(int[] tr, Vector3f out) {
        float[] a = verts.get(tr[0]), b2 = verts.get(tr[1]), c = verts.get(tr[2]);
        float ux = b2[0] - a[0], uy = b2[1] - a[1], uz = b2[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        out.set(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx);
    }

    // Moves every vertex added from index first on.
    void transform(int first, Matrix4f m) {
        Vector3f q = new Vector3f();
        for (int i = first; i < verts.size(); i++) {
            float[] v = verts.get(i);
            m.transformPosition(q.set(v[0], v[1], v[2]));
            v[0] = q.x;
            v[1] = q.y;
            v[2] = q.z;
        }
    }

    interface Warp {
        float[] at(float x, float y, float z);
    }

    // Moves every vertex added from index first on through a function.
    void warp(int first, Warp fn) {
        for (int i = first; i < verts.size(); i++) {
            float[] v = verts.get(i);
            float[] q = fn.at(v[0], v[1], v[2]);
            v[0] = q[0];
            v[1] = q[1];
            v[2] = q[2];
        }
    }

    int size() {
        return verts.size();
    }

    float[] bake() {
        float[] normals = new float[verts.size() * 3];
        Vector3f n = new Vector3f();
        for (int[] tr : tris) {
            face(tr, n);
            for (int k : tr) {
                normals[k * 3] += n.x;
                normals[k * 3 + 1] += n.y;
                normals[k * 3 + 2] += n.z;
            }
        }
        float[] out = new float[tris.size() * 3 * 11];
        int o = 0;
        for (int[] tr : tris) {
            for (int k : tr) {
                float[] v = verts.get(k);
                float nx = normals[k * 3], ny = normals[k * 3 + 1], nz = normals[k * 3 + 2];
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-6f) {
                    len = 1;
                    ny = 1;
                }
                System.arraycopy(v, 0, out, o, 8);
                out[o + 8] = nx / len;
                out[o + 9] = ny / len;
                out[o + 10] = nz / len;
                o += 11;
            }
        }
        return out;
    }

    // ---- Shapes ----------------------------------------------------------------------------------------------------

    static float[] supe(float c, float s, float a, float b, float p) {
        float e = 2 / p;
        return new float[] {a * Math.signum(c) * (float) Math.pow(Math.abs(c), e), b * Math.signum(s) * (float) Math.pow(Math.abs(s), e)};
    }

    static float smooth(float a, float b, float x) {
        float t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    interface RadiusFn {
        float at(float s, float phi);
    }

    interface OffsetFn {
        float[] at(float s, float phi, float x, float z);
    }

    // A tube with a superellipse cross section (a, b, power p), from height y0 (s = 0) down to y1 (s = 1).
    static final class Tube {
        float a, b, p = 2, y0, y1, phi0 = 0, phi1 = (float) (Math.PI * 2);
        int rings = 12, segs = 48;
        boolean capTop, capBot;
        RadiusFn rf;
        OffsetFn off;
        ShadeFn shade;

        Tube(float a, float b, float p, float y0, float y1) {
            this.a = a;
            this.b = b;
            this.p = p;
            this.y0 = y0;
            this.y1 = y1;
        }

        Tube rings(int rings, int segs) {
            this.rings = rings;
            this.segs = segs;
            return this;
        }

        Tube rf(RadiusFn rf) {
            this.rf = rf;
            return this;
        }

        Tube off(OffsetFn off) {
            this.off = off;
            return this;
        }

        Tube shade(ShadeFn shade) {
            this.shade = shade;
            return this;
        }

        Tube caps(boolean top, boolean bottom) {
            this.capTop = top;
            this.capBot = bottom;
            return this;
        }

        Tube arc(float phi0, float phi1) {
            this.phi0 = phi0;
            this.phi1 = phi1;
            return this;
        }

        void build(Geo geo) {
            int base = geo.grid(segs, rings, (u, v) -> {
                float ph = phi0 + (phi1 - phi0) * u;
                float k = rf == null ? 1 : rf.at(v, ph);
                float[] xz = supe((float) Math.cos(ph), (float) Math.sin(ph), a * k, b * k, p);
                float x = xz[0], z = xz[1], y = y0 + (y1 - y0) * v;
                if (off != null) {
                    float[] d = off.at(v, ph, x, z);
                    x += d[0];
                    y += d[1];
                    z += d[2];
                }
                return new float[] {x, y, z, u, v};
            }, shade == null ? null : (u, v) -> shade.at(v, phi0 + (phi1 - phi0) * u), (x, y, z) -> new Vector3f(x, 0, z));
            if (capTop) {
                geo.cap(base, segs + 1, (x, y, z) -> new Vector3f(0, 1, 0));
            }
            if (capBot) {
                geo.cap(base + rings * (segs + 1), segs + 1, (x, y, z) -> new Vector3f(0, -1, 0));
            }
        }
    }

    // A thin plate w x h x d, curved around the y axis with radius R (edges bend back, the front faces +z).
    static void bentPlate(Geo geo, float w, float h, float d, float radius, Matrix4f place) {
        int first = geo.size();
        int seg = 16;
        float hw = w / 2, hh = h / 2, hd = d / 2;
        // front and back
        for (int side = -1; side <= 1; side += 2) {
            float z = side * hd;
            int s = side;
            geo.grid(seg, 1, (u, v) -> new float[] {-hw + w * u, hh - h * v, z, u, v}, null, (x, y, zz) -> new Vector3f(0, 0, s));
        }
        // top and bottom edges
        for (int side = -1; side <= 1; side += 2) {
            float y = side * hh;
            int s = side;
            geo.grid(seg, 1, (u, v) -> new float[] {-hw + w * u, y, -hd + d * v, u, v}, null, (x, yy, z) -> new Vector3f(0, s, 0));
        }
        // left and right edges
        for (int side = -1; side <= 1; side += 2) {
            float x = side * hw;
            int s = side;
            geo.grid(1, 1, (u, v) -> new float[] {x, hh - h * v, -hd + d * u, u, v}, null, (xx, y, z) -> new Vector3f(s, 0, 0));
        }
        bend(geo, first, radius);
        geo.transform(first, place);
    }

    // Bends vertices around the y axis: x becomes an arc of the given radius.
    static void bend(Geo geo, int first, float radius) {
        if (radius > 1e4f) {
            return;
        }
        for (int i = first; i < geo.verts.size(); i++) {
            float[] v = geo.verts.get(i);
            float th = v[0] / radius;
            float rr = radius + v[2];
            v[0] = rr * (float) Math.sin(th);
            v[2] = rr * (float) Math.cos(th) - radius;
        }
    }

    // A small box, as six grids.
    static void box(Geo geo, float w, float h, float d, Matrix4f place) {
        bentPlate(geo, w, h, d, 1e6f, place);
    }

    // A round cord along a Catmull-Rom curve through the points.
    static void cord(Geo geo, float[][] pts, float radius, int samples) {
        Vector3f[] path = new Vector3f[samples + 1];
        int n = pts.length;
        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples * (n - 1);
            int k = Math.min(n - 2, (int) t);
            float f = t - k;
            float[] p0 = pts[Math.max(0, k - 1)], p1 = pts[k], p2 = pts[k + 1], p3 = pts[Math.min(n - 1, k + 2)];
            float[] q = new float[3];
            for (int c = 0; c < 3; c++) {
                q[c] = 0.5f * (2 * p1[c] + (-p0[c] + p2[c]) * f + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * f * f
                        + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * f * f * f);
            }
            path[i] = new Vector3f(q[0], q[1], q[2]);
        }
        int sides = 6;
        Vector3f[] center = path;
        geo.grid(sides, samples, (u, v) -> {
            int i = Math.round(v * samples);
            Vector3f t = new Vector3f(center[Math.min(samples, i + 1)]).sub(center[Math.max(0, i - 1)]).normalize();
            Vector3f ref = Math.abs(t.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            Vector3f nn = new Vector3f(t).cross(ref).normalize();
            Vector3f bb = new Vector3f(t).cross(nn).normalize();
            float a = u * (float) Math.PI * 2;
            Vector3f p = new Vector3f(center[i]).add(nn.mul(radius * (float) Math.cos(a))).add(bb.mul(radius * (float) Math.sin(a)));
            return new float[] {p.x, p.y, p.z, u, v};
        }, null, null);
        // Cords are thin and round: their own winding already faces out evenly enough.
    }

    static void sphere(Geo geo, float cx, float cy, float cz, float radius) {
        geo.grid(10, 6, (u, v) -> {
            float th = u * (float) Math.PI * 2, ph = v * (float) Math.PI;
            float x = (float) (Math.sin(ph) * Math.cos(th)), y = (float) Math.cos(ph), z = (float) (Math.sin(ph) * Math.sin(th));
            return new float[] {cx + x * radius, cy + y * radius, cz + z * radius, u, v};
        }, null, (x, y, z) -> new Vector3f(x - cx, y - cy, z - cz));
    }
}
