package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.List;

import org.joml.Vector3f;

// Hanging cloth as chains of points (verlet), the same solver as the Guarda-roupa viewer: each column hangs from an
// anchor on the body, neighbours keep their spacing, the legs and the body push it out. Rigid strips (tabard, armor
// plates) keep their width and never twist. Works in rig pixels (y up, facing +z, feet at 0), relative to the player.
final class Cloth {
    // How a cloth is made; one per outfit piece, shared by every player wearing it.
    record Spec(float[][] anchors, int rows, float seg, boolean wrap, float margin, int iters, float flare, float hStiff,
            float[][] outDirs, float[] rigidRight, float rigidHalfWidth) {
    }

    // A body part the cloth bumps into.
    record ColliderSpec(Rig.Bone bone, boolean radial, float cx, float cy, float cz, float hx, float hy, float hz, float len,
            Geo.RadiusFn radius) {
        static ColliderSpec box(Rig.Bone bone, float cx, float cy, float cz, float hx, float hy, float hz) {
            return new ColliderSpec(bone, false, cx, cy, cz, hx, hy, hz, 0, null);
        }

        static ColliderSpec radial(Rig.Bone bone, float len, Geo.RadiusFn radius) {
            return new ColliderSpec(bone, true, 0, 0, 0, 0, 0, 0, len, radius);
        }
    }

    final Spec spec;
    final int n;
    final Vector3f[][] p;
    final Vector3f[][] o;
    final Vector3f[] anchor;
    final float[][] hRest;

    Cloth(Spec spec, Rig rig) {
        this.spec = spec;
        this.n = spec.anchors().length;
        p = new Vector3f[n][spec.rows()];
        o = new Vector3f[n][spec.rows()];
        anchor = new Vector3f[n];
        for (int c = 0; c < n; c++) {
            anchor[c] = new Vector3f();
            for (int r = 0; r < spec.rows(); r++) {
                p[c][r] = new Vector3f();
                o[c][r] = new Vector3f();
            }
        }
        hRest = new float[spec.rows()][n];
        pin(rig);
        for (int r = 0; r < spec.rows(); r++) {
            for (int c = 0; c < n; c++) {
                hRest[r][c] = p[c][r].distance(p[(c + 1) % n][r]);
            }
        }
    }

    private void anchors(Rig rig) {
        for (int c = 0; c < n; c++) {
            float[] a = spec.anchors()[c];
            rig.body(a[0], a[1], a[2], anchor[c]);
        }
    }

    // Back to hanging straight down from the anchors.
    void pin(Rig rig) {
        anchors(rig);
        for (int c = 0; c < n; c++) {
            for (int r = 0; r < spec.rows(); r++) {
                Vector3f q = p[c][r].set(anchor[c]).add(0, -spec.seg() * r, 0);
                if (spec.outDirs() != null) {
                    float[] d = spec.outDirs()[c];
                    q.add(d[0] * spec.flare() * r, 0, d[2] * spec.flare() * r);
                }
                o[c][r].set(q);
            }
        }
    }

    void step(float dt, Vector3f force, Rig rig, List<ColliderSpec> colliders) {
        anchors(rig);
        float d2 = dt * dt;
        int rows = spec.rows();
        for (int c = 0; c < n; c++) {
            for (int r = 1; r < rows; r++) {
                Vector3f q = p[c][r], old = o[c][r];
                float vx = (q.x - old.x) * 0.985f, vy = (q.y - old.y) * 0.985f, vz = (q.z - old.z) * 0.985f;
                old.set(q);
                q.add(vx + force.x * d2, vy + force.y * d2, vz + force.z * d2);
            }
        }
        Vector3f tmp = new Vector3f();
        for (int it = 0; it < spec.iters(); it++) {
            for (int c = 0; c < n; c++) {
                p[c][0].set(anchor[c]);
            }
            for (int c = 0; c < n; c++) {
                for (int r = 0; r < rows - 1; r++) {
                    link(p[c][r], p[c][r + 1], spec.seg(), r == 0 ? 0 : 0.5f, 1);
                }
            }
            int last = spec.wrap() ? n : n - 1;
            for (int c = 0; c < last; c++) {
                int c2 = (c + 1) % n;
                for (int r = 1; r < rows; r++) {
                    link(p[c][r], p[c2][r], hRest[r][c], 0.5f, spec.hStiff());
                }
            }
            for (int c = 0; c < n; c++) {
                for (int r = 1; r < rows; r++) {
                    for (ColliderSpec k : colliders) {
                        push(rig, k, p[c][r], spec.margin(), tmp);
                    }
                }
            }
            if (spec.rigidRight() != null) {
                keepWidth(rig);
            }
        }
        for (int c = 0; c < n; c++) {
            p[c][0].set(anchor[c]);
        }
    }

    private void keepWidth(Rig rig) {
        float[] rr = spec.rigidRight();
        Vector3f right = rig.bodyDir(rr[0], rr[1], rr[2], new Vector3f());
        Vector3f mid = new Vector3f();
        for (int r = 1; r < spec.rows(); r++) {
            mid.zero();
            for (int c = 0; c < n; c++) {
                mid.add(p[c][r]);
            }
            mid.mul(1f / n);
            for (int c = 0; c < n; c++) {
                float f = n == 1 ? 0 : (c / (float) (n - 1)) * 2 - 1;
                p[c][r].set(mid).add(right.x * f * spec.rigidHalfWidth(), right.y * f * spec.rigidHalfWidth(), right.z * f * spec.rigidHalfWidth());
            }
        }
    }

    private static void link(Vector3f a, Vector3f b, float len, float wa, float k) {
        float dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-6f) {
            d = 1e-6f;
        }
        float f = (d - len) / d * k;
        float wb = 1 - wa;
        a.add(dx * f * wa, dy * f * wa, dz * f * wa);
        b.sub(dx * f * wb, dy * f * wb, dz * f * wb);
    }

    private static void push(Rig rig, ColliderSpec k, Vector3f point, float margin, Vector3f q) {
        rig.toLocal(k.bone(), point, q);
        boolean hit = false;
        if (!k.radial()) {
            float dy = q.y - k.cy();
            if (Math.abs(dy) > k.hy()) {
                return;
            }
            float qx = q.x - k.cx(), qz = q.z - k.cz();
            float dx = k.hx() + margin - Math.abs(qx), dz = k.hz() + margin - Math.abs(qz);
            if (dx > 0 && dz > 0) {
                hit = true;
                if (dx < dz) {
                    q.x = k.cx() + (qx >= 0 ? 1 : -1) * (k.hx() + margin);
                } else {
                    q.z = k.cz() + (qz >= 0 ? 1 : -1) * (k.hz() + margin);
                }
            }
        } else {
            float s = -q.y / k.len();
            if (s < -0.05f || s > 1.05f) {
                return;
            }
            float radius = k.radius().at(Math.min(1, Math.max(0, s)), 0) * 0.93f + margin;
            if (Math.hypot(q.x, q.z) < radius && Math.abs(q.x) < radius) {
                hit = true;
                q.z = (q.z < 0 ? -1 : 1) * (float) Math.sqrt(radius * radius - q.x * q.x);
            }
        }
        if (hit) {
            rig.toWorld(k.bone(), q, point);
        }
    }
}
