package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.Arrays;

// The hands of Shigaraki's preview (forja/shigaraki-tentaculos: mkRng, sweep, buildHand), number for number: each hand
// its own, from its own random numbers (palm, bony or thick fingers, long fingers, open to almost closed, spread,
// a forearm that goes into the tentacle, left or right). Hand space: X to the side, Y the back of the hand, Z forward.
public final class HandGen {
    private HandGen() {
    }

    // mkRng: the preview's LCG, so the same seed gives the same hands as there.
    public static final class Rng {
        private long s;

        public Rng(long seed) {
            s = seed & 0xFFFFFFFFL;
        }

        public float next() {
            s = (s * 1664525L + 1013904223L) & 0xFFFFFFFFL;
            return (float) (s / 4294967296.0);
        }
    }

    // A growing triangle mesh: positions, normals (3 floats each) and indices.
    public static final class Mesh {
        public float[] pos = new float[1024], nrm = new float[1024];
        public int[] idx = new int[2048];
        public int nv, ni;

        public void vert(float x, float y, float z, float nx, float ny, float nz) {
            if (nv * 3 + 3 > pos.length) {
                pos = Arrays.copyOf(pos, pos.length * 2);
                nrm = Arrays.copyOf(nrm, nrm.length * 2);
            }
            pos[nv * 3] = x;
            pos[nv * 3 + 1] = y;
            pos[nv * 3 + 2] = z;
            nrm[nv * 3] = nx;
            nrm[nv * 3 + 1] = ny;
            nrm[nv * 3 + 2] = nz;
            nv++;
        }

        public void tri(int a, int b, int c) {
            if (ni + 3 > idx.length) {
                idx = Arrays.copyOf(idx, idx.length * 2);
            }
            idx[ni++] = a;
            idx[ni++] = b;
            idx[ni++] = c;
        }
    }

    private static float clamp(float x, float a, float b) {
        return Math.max(a, Math.min(b, x));
    }

    private static void norm(float[] v) {
        float n = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (n > 1e-8f) {
            v[0] /= n;
            v[1] /= n;
            v[2] /= n;
        }
    }

    // A sweep of elliptic section along a polyline, each ring a little irregular, with rounded caps.
    private static void sweep(Mesh out, float[][] pts, float[] rx, float[] ry, float[] sideRef, int nRad, Rng r) {
        int base = out.nv, n = pts.length;
        float[] t = new float[3], side = new float[3], up = new float[3];
        for (int i = 0; i < n; i++) {
            float[] a = pts[Math.min(i + 1, n - 1)], b = pts[Math.max(i - 1, 0)];
            t[0] = a[0] - b[0];
            t[1] = a[1] - b[1];
            t[2] = a[2] - b[2];
            norm(t);
            float d = sideRef[0] * t[0] + sideRef[1] * t[1] + sideRef[2] * t[2];
            side[0] = sideRef[0] - t[0] * d;
            side[1] = sideRef[1] - t[1] * d;
            side[2] = sideRef[2] - t[2] * d;
            norm(side);
            up[0] = t[1] * side[2] - t[2] * side[1];
            up[1] = t[2] * side[0] - t[0] * side[2];
            up[2] = t[0] * side[1] - t[1] * side[0];
            for (int k = 0; k < nRad; k++) {
                float an = (float) (k / (double) nRad * Math.PI * 2 + (r.next() - 0.5f) * 0.25f), c = (float) Math.cos(an), s = (float) Math.sin(an);
                float j = 1 + (r.next() - 0.5f) * 0.12f;
                float px = pts[i][0] + side[0] * c * rx[i] * j + up[0] * s * ry[i] * j;
                float py = pts[i][1] + side[1] * c * rx[i] * j + up[1] * s * ry[i] * j;
                float pz = pts[i][2] + side[2] * c * rx[i] * j + up[2] * s * ry[i] * j;
                float[] nn = {side[0] * c * ry[i] + up[0] * s * rx[i], side[1] * c * ry[i] + up[1] * s * rx[i], side[2] * c * ry[i] + up[2] * s * rx[i]};
                norm(nn);
                out.vert(px, py, pz, nn[0], nn[1], nn[2]);
            }
        }
        for (int i = 0; i < n - 1; i++) {
            for (int k = 0; k < nRad; k++) {
                int k1 = (k + 1) % nRad, p0 = base + i * nRad + k, p1 = base + i * nRad + k1, p2 = base + (i + 1) * nRad + k, p3 = base + (i + 1) * nRad + k1;
                out.tri(p0, p1, p2);
                out.tri(p1, p3, p2);
            }
        }
        cap(out, pts, rx, ry, nRad, base, n - 1, 1);
        cap(out, pts, rx, ry, nRad, base, 0, -1);
    }

    private static void cap(Mesh out, float[][] pts, float[] rx, float[] ry, int nRad, int base, int i, int sign) {
        int n = pts.length;
        float[] a = pts[Math.min(i + 1, n - 1)], b = pts[Math.max(i - 1, 0)];
        float[] t = {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
        norm(t);
        t[0] *= sign;
        t[1] *= sign;
        t[2] *= sign;
        int ci = out.nv;
        float rr = Math.min(rx[i], ry[i]);
        out.vert(pts[i][0] + t[0] * rr * 0.85f, pts[i][1] + t[1] * rr * 0.85f, pts[i][2] + t[2] * rr * 0.85f, t[0], t[1], t[2]);
        for (int k = 0; k < nRad; k++) {
            int k1 = (k + 1) % nRad, q = base + i * nRad;
            if (sign > 0) {
                out.tri(q + k1, ci, q + k);
            } else {
                out.tri(q + k, ci, q + k1);
            }
        }
    }

    private static float[] v(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    // buildHand(r, fMul): fMul makes the forearm longer (the trap's hands come out of the ground).
    public static Mesh hand(Rng r, float fMul) {
        Mesh out = new Mesh();
        float palmL = 0.40f + r.next() * 0.08f, palmW = palmL * (0.86f + r.next() * 0.2f), palmT = palmL * (0.27f + r.next() * 0.08f);
        float bony = 0.78f + r.next() * 0.4f;
        float longF = 0.95f + r.next() * 0.45f;
        float curlBase = (float) Math.pow(r.next(), 1.4) * 1.5f;
        float spread = 0.25f + r.next() * 1.1f;
        float F = palmL * (1.0f + r.next() * 2.4f) * fMul;
        float bendX = (r.next() - 0.5f) * F * 0.35f, bendY = (r.next() - 0.5f) * F * 0.3f;
        {
            float[] zs = {-F, -F * 0.62f, -F * 0.28f, -0.02f, palmL * 0.32f, palmL * 0.72f, palmL * 0.96f};
            float aW = palmW * (0.3f + r.next() * 0.1f), aT = aW * (0.75f + r.next() * 0.15f), wW = palmW * (0.3f + r.next() * 0.05f);
            float[][] pts = new float[zs.length][];
            for (int i = 0; i < zs.length; i++) {
                float z = zs[i];
                float k = (float) Math.sin(clamp((z + F) / F, 0, 1) * Math.PI);
                pts[i] = v(bendX * k * (z < 0 ? 1 : 0), bendY * k * (z < 0 ? 1 : 0) + (i > 3 ? 0.01f : 0), z);
            }
            float[] rx = {aW * 0.9f, aW * (1 + r.next() * 0.15f), aW, wW, palmW * 0.47f, palmW * 0.52f, palmW * 0.47f};
            float[] ry = {aT * 0.9f, aT, aT * 0.95f, wW * 0.72f, palmT * 0.5f, palmT * 0.5f, palmT * 0.4f};
            sweep(out, pts, rx, ry, v(1, 0, 0), 7, r);
        }
        float[] rel = {0.93f + (r.next() - 0.5f) * 0.1f, 1.0f, 0.94f + (r.next() - 0.5f) * 0.1f, 0.72f + (r.next() - 0.5f) * 0.12f};
        for (int f = 0; f < 4; f++) {
            float fx = palmW * (0.36f - f * 0.24f) * (0.95f + r.next() * 0.1f);
            float[] base = v(fx, 0.01f, palmL * 0.95f - Math.abs(fx) * 0.22f);
            float len = rel[f] * palmL * 1.02f * longF * (0.93f + r.next() * 0.14f);
            float yaw = (fx / palmW) * 0.45f * spread + (r.next() - 0.5f) * 0.18f;
            float c = clamp(curlBase + (r.next() - 0.5f) * 0.6f, -0.15f, 1.7f);
            float[] segs = {0.45f, 0.31f, 0.24f};
            float[] ang = {c * (0.35f + r.next() * 0.25f), c * (0.6f + r.next() * 0.3f), c * (0.45f + r.next() * 0.3f)};
            float rad = palmT * 0.4f * (1 - f * 0.07f) * bony;
            float[][] pts = new float[7][];
            float[] rx = new float[7], ry = new float[7];
            pts[0] = base.clone();
            rx[0] = rad * 1.1f;
            ry[0] = rad * 0.95f;
            float[] p = base.clone();
            float pitch = (r.next() - 0.6f) * 0.25f;
            for (int s = 0; s < 3; s++) {
                pitch += ang[s];
                float[] d = v((float) (Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch), (float) (Math.cos(yaw) * Math.cos(pitch)));
                float L = segs[s] * len;
                float[] mid = v(p[0] + d[0] * L * 0.5f, p[1] + d[1] * L * 0.5f, p[2] + d[2] * L * 0.5f);
                p = v(p[0] + d[0] * L, p[1] + d[1] * L, p[2] + d[2] * L);
                float tap = 1 - s * 0.12f;
                pts[1 + s * 2] = mid;
                pts[2 + s * 2] = p.clone();
                rx[1 + s * 2] = rad * 0.9f * tap;
                rx[2 + s * 2] = rad * (s < 2 ? 1.0f : 0.82f) * tap;
                ry[1 + s * 2] = rad * 0.8f * tap;
                ry[2 + s * 2] = rad * (s < 2 ? 0.88f : 0.72f) * tap;
            }
            sweep(out, pts, rx, ry, v(1, 0, 0), 5, r);
        }
        {
            float cT = clamp(curlBase * 0.8f + (r.next() - 0.5f) * 0.5f, 0, 1.4f);
            float[] p = v(palmW * 0.44f, -0.02f, palmL * 0.2f);
            float yaw0 = 0.75f + r.next() * 0.5f, pit = 0.25f + r.next() * 0.2f;
            float rad = palmT * 0.45f * bony, L = palmL * (0.95f + r.next() * 0.25f) * longF;
            float[] segs = {0.4f, 0.33f, 0.27f};
            float[][] pts = new float[4][];
            float[] rx = new float[4], ry = new float[4];
            pts[0] = p.clone();
            rx[0] = rad * 1.15f;
            ry[0] = rad;
            for (int s = 0; s < 3; s++) {
                float yaw = yaw0 - cT * 0.55f * (s + 1), pitch = pit + cT * 0.32f * (s + 1);
                float[] d = v((float) (Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch), (float) (Math.cos(yaw) * Math.cos(pitch)));
                p = v(p[0] + d[0] * segs[s] * L, p[1] + d[1] * segs[s] * L, p[2] + d[2] * segs[s] * L);
                pts[s + 1] = p.clone();
                rx[s + 1] = rad * (1 - s * 0.12f);
                ry[s + 1] = rad * (0.9f - s * 0.12f);
            }
            sweep(out, pts, rx, ry, v(0, 1, 0), 5, r);
        }
        if (r.next() < 0.5f) {
            for (int i = 0; i < out.nv; i++) {
                out.pos[i * 3] *= -1;
                out.nrm[i * 3] *= -1;
            }
            for (int i = 0; i < out.ni; i += 3) {
                int a = out.idx[i + 1];
                out.idx[i + 1] = out.idx[i + 2];
                out.idx[i + 2] = a;
            }
        }
        return out;
    }
}
