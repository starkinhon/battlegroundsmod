package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.client.fx.FxRenderTypes;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

// Kokushibo's sword putting itself away (Stark, 2026-11-07: "Só no guardar"): it is living flesh. It wakes up in the
// hand (veins throb, the gold eyes open), the grip melts out of the palm in strings of slime, it flies by itself along
// a curve over his left side, writhing and shaking off drops, and slides into the sheath, which swallows it; there it
// hardens back into a blade. The character stands still; only the sword moves. A port of the approved visualizer page
// forja/kokushibo-espada/espada-carne-viva.html (v3, scene "Guardar na bainha"): same path, timings and shapes.
// Every space here is the player model's (the pose KokushiboSheathLayer gets: 1 = one block, +y down, -z the front).
// The page works in its own world (the player at the origin facing +z, +y up, 0.9375 blocks per unit): its numbers
// come through visualPoint / visualDir.
final class FleshSheathe {
    // The page's timeline is 64 ticks long, played SLOW times slower.
    static final float SLOW = 1.8f;
    static final float END = 64 * SLOW;
    // When the hand has let go (the new item may show from then on).
    static final float LET_GO = 22 * SLOW;

    private static final float U = 1 / 0.9375f;
    private static final float TIP = KokushiboSwordRenderer.TIP;
    private static final int N = 900;
    // The middle of the flight, in the page's world: rears up out of the hand, curls over to his left, high, down his
    // left side, swings round in front.
    private static final float[][] MIDDLE = { { -0.2f, 1.9f, 2.95f }, { 0.9f, 2.55f, 2.6f }, { 1.85f, 2.0f, 1.6f }, { 1.45f, 1.35f, 2.4f } };
    private static final float[] GRIP_Y = { -1.6f, -2.8f, -4.0f, -5.2f, -6.4f, -7.6f };
    private static final float[] STRING_Y = { 6, 10, 14, 18, 22, 26, 30 };

    private static final Random RANDOM = new Random();

    // One put-away of one player.
    static final class Run {
        private Path path;
        private float lastTick;
        private final boolean[] dropped = new boolean[GRIP_Y.length];
        private final List<List<float[]>> strings = new ArrayList<>();
        private final List<Drop> drops = new ArrayList<>();

        Run() {
            for (int i = 0; i < STRING_Y.length; i++) {
                strings.add(new ArrayList<>());
            }
        }
    }

    private static final class Drop {
        final Vector3f p;
        final Vector3f v;
        final float size;
        float life;

        Drop(Vector3f p, Vector3f v, float size) {
            this.p = p;
            this.v = v;
            this.size = size;
        }
    }

    // The flight path from the sword in the hand to the inside of the sheath, as a table of N + 1 points with a frame
    // carried along it (parallel transport), started on the held sword's edge and twisted to end on the sheath's.
    private static final class Path {
        final Vector3f[] p = new Vector3f[N + 1];
        final Vector3f[] t = new Vector3f[N + 1];
        final Vector3f[] n = new Vector3f[N + 1];
        final float[] d = new float[N + 1];
        float length, dMouth, dTip0, dTip1, endTwist, k;
        Vector3f mouth;

        Path(Matrix4f held, Matrix4f sheath) {
            k = held.getScale(new Vector3f()).x;
            Vector3f pButt = at(held, KokushiboSwordRenderer.BUTT), pTip = at(held, TIP);
            mouth = at(sheath, 0);
            Vector3f in = at(sheath, 10).sub(mouth).normalize();
            List<Vector3f> points = new ArrayList<>();
            points.add(pButt);
            points.add(at(held, 0));
            points.add(pTip);
            for (float[] m : MIDDLE) {
                points.add(visualPoint(m[0], m[1], m[2]));
            }
            points.add(new Vector3f(mouth).fma(-1.6f * U, in));
            points.add(new Vector3f(mouth).fma(-0.6f * U, in));
            points.add(new Vector3f(mouth));
            points.add(at(sheath, TIP + 4));
            for (int i = 0; i <= N; i++) {
                p[i] = catmullRom(points, i / (float) N);
            }
            d[0] = 0;
            for (int i = 1; i <= N; i++) {
                d[i] = d[i - 1] + p[i].distance(p[i - 1]);
            }
            for (int i = 0; i <= N; i++) {
                t[i] = new Vector3f(p[Math.min(N, i + 1)]).sub(p[Math.max(0, i - 1)]).normalize();
            }
            Vector3f heldX = held.transformDirection(new Vector3f(1, 0, 0)).normalize();
            Vector3f sheathX = sheath.transformDirection(new Vector3f(1, 0, 0)).normalize();
            Vector3f carry = new Vector3f(heldX).fma(-heldX.dot(t[0]), t[0]).normalize();
            for (int i = 0; i <= N; i++) {
                carry.fma(-carry.dot(t[i]), t[i]).normalize();
                n[i] = new Vector3f(carry);
            }
            length = d[N];
            dMouth = closest(mouth);
            Vector3f want = new Vector3f(sheathX).fma(-sheathX.dot(t[N]), t[N]).normalize();
            endTwist = (float) Math.atan2(new Vector3f(n[N]).cross(want).dot(t[N]), n[N].dot(want));
            dTip0 = closest(pTip);
            dTip1 = dMouth + TIP * k;
        }

        private float closest(Vector3f q) {
            float best = Float.MAX_VALUE, at = 0;
            for (int i = 0; i <= N; i++) {
                float e = p[i].distance(q);
                if (e < best) {
                    best = e;
                    at = d[i];
                }
            }
            return at;
        }

        private int index(float dist) {
            int lo = 0, hi = N;
            while (hi - lo > 1) {
                int mid = (lo + hi) >> 1;
                if (d[mid] < dist) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }

        // The point dist along the path and its frame: t along it, n the blade's edge, b = n x t its flat side.
        void frame(float dist, Frame f) {
            dist = Math.max(0, Math.min(length, dist));
            int i = index(dist), j = Math.min(N, i + 1);
            float u = (dist - d[i]) / Math.max(1e-9f, d[j] - d[i]);
            f.p.set(p[i]).lerp(p[j], u);
            f.t.set(t[i]).lerp(t[j], u).normalize();
            f.n.set(n[i]).lerp(n[j], u);
            float twist = endTwist * clamp01((dist - dTip0) / (length - dTip0));
            rotate(f.n, f.t, twist);
            f.n.fma(-f.n.dot(f.t), f.t).normalize();
            f.b.set(f.n).cross(f.t);
        }
    }

    private static final class Frame {
        final Vector3f p = new Vector3f(), t = new Vector3f(), n = new Vector3f(), b = new Vector3f();
    }

    // held: the sword's space as held in the right hand; sheath: the sheath's space on the hip; palm: the middle of
    // the right palm. All relative to pose (the player model's space). t: ticks since it started putting it away.
    static void render(Run run, PoseStack.Pose pose, Matrix4f held, Matrix4f sheath, Vector3f palm, float t, float ageInTicks,
            MultiBufferSource buffer, int light) {
        if (run.path == null) {
            run.path = new Path(held, sheath);
        }
        Path path = run.path;
        float tick = t / SLOW;
        float clock = ageInTicks / 20 / SLOW;
        if (tick < run.lastTick) {
            run.drops.clear();
            run.strings.forEach(List::clear);
        }
        boolean shader = FxRenderTypes.fleshReady();
        RenderType type = shader ? FxRenderTypes.flesh(KokushiboSwordRenderer.TEXTURE) : RenderType.entityCutoutNoCull(KokushiboSwordRenderer.TEXTURE);
        Out out = new Out(buffer.getBuffer(type), pose, light, shader);
        float k = path.k;

        // how alive it is
        float flesh = tick < 12 ? 0.75f * easeOut(tick / 12) : tick < 50 ? 0.75f + 0.25f * clamp01((tick - 12) / 8) : 1 - easeInOut(clamp01((tick - 52) / 12));
        // the sheath swallows
        float gulp = (float) Math.exp(-Math.pow((tick - 50) / 3, 2));
        float sheathFlesh = 0.15f * gulp + 0.3f * (float) Math.exp(-Math.pow((tick - 46) / 6, 2));
        Matrix4f swallow = new Matrix4f(sheath).scale(1 + 0.12f * gulp, 1, 1 + 0.12f * gulp);
        out.mesh(KokushiboSwordRenderer.saya().data(), swallow, sheathFlesh, 0);

        // where the tip is along the path
        float loosen = tick < 12 ? 0 : tick < 22 ? 0.06f * easeIn((tick - 12) / 10) : 1;
        float travel = tick < 22 ? 0 : easeInOut(clamp01((tick - 22) / 30));
        float dTip = tick < 22 ? path.dTip0 + loosen * 0.25f * U : path.dTip0 + 0.015f * U + (path.dTip1 - path.dTip0 - 0.015f * U) * travel;
        // the body writhes while it flies, not in the hand nor in the sheath
        float writhe = tick < 22 ? 0 : (float) Math.sin(Math.PI * clamp01((tick - 22) / 30));
        float swell = flesh * (0.25f + 0.35f * easeInOut(clamp01((tick - 14) / 14))) * (tick > 52 ? 1 - clamp01((tick - 52) / 8) : 1);
        // hand -> path: a smooth hand-over, never a pop
        float hand = 1 - easeInOut(clamp01((tick - 9) / 13));

        float[] src = KokushiboSwordRenderer.katana().data();
        int count = src.length / 8;
        float[] pos = new float[count * 3], nor = new float[count * 3];
        Frame f = new Frame();
        Vector3f q = new Vector3f(), qn = new Vector3f(), h = new Vector3f();
        for (int i = 0; i < count; i++) {
            int o = i * 8;
            float x = src[o], y = src[o + 1], z = src[o + 2];
            float dist = dTip - (TIP - y) * k;
            path.frame(dist, f);
            float inSheath = clamp01((dist - path.dMouth) / (0.1f * U));
            float dv = dist / U;
            float wave = U * writhe * (1 - inSheath) * ((float) Math.sin(dv * 6.0 - clock * 10.0) * 0.03f + (float) Math.sin(dv * 21.0 - clock * 26.0) * 0.006f);
            // the flesh swells: rounder (thicker) than the flat blade, with lumps running down it
            float lump = Math.max(0, (float) Math.sin(y * 1.6 - clock * 9.0));
            float swollen = swell * (1 - inSheath * 0.85f) * (y > KokushiboSwordRenderer.BLADE_BASE ? 1 : 0.12f);
            float wide = 1 + swollen * (0.08f + 0.18f * lump), thick = 1 + swollen * (0.7f + 0.9f * lump);
            q.set(f.p).fma(x * k * wide + wave, f.n).fma(z * k * thick + wave * 0.5f, f.b);
            // in the hand (before it lets go) it stays exactly as held
            if (hand > 0) {
                q.lerp(held.transformPosition(h.set(x * wide, y, z * thick)), hand);
            }
            float nx = src[o + 5], ny = src[o + 6], nz = src[o + 7];
            qn.set(f.n).mul(nx).fma(ny, f.t).fma(nz, f.b).normalize();
            if (hand > 0) {
                qn.lerp(held.transformDirection(h.set(nx, ny, nz)).normalize(), hand).normalize();
            }
            pos[i * 3] = q.x;
            pos[i * 3 + 1] = q.y;
            pos[i * 3 + 2] = q.z;
            nor[i * 3] = qn.x;
            nor[i * 3 + 1] = qn.y;
            nor[i * 3 + 2] = qn.z;
        }
        out.bent(src, pos, nor, flesh, flesh);

        // slime from the palm to the grip: long sagging strings that stretch and snap one by one
        if (tick >= 12 && tick < 34) {
            for (int i = 0; i < GRIP_Y.length; i++) {
                float y = GRIP_Y[i];
                path.frame(dTip - (TIP - y) * k, f);
                Vector3f b = new Vector3f(f.p).lerp(at(held, y), hand);
                Vector3f a = new Vector3f(palm).add(visualDir(((i % 3) - 1) * 0.015f, ((i >> 1) - 1) * 0.012f, 0));
                float len = a.distance(b) / U;
                float snapAt = 23 + i * 1.6f, snap = clamp01((tick - snapAt) / 3.5f);
                if (snap > 0 && snap < 0.15f && !run.dropped[i]) {
                    run.dropped[i] = true;
                    run.drops.add(new Drop(new Vector3f(a).lerp(b, 0.5f), visualDir(0, -0.2f, 0), 0.016f));
                }
                if (tick < snapAt) {
                    run.dropped[i] = false;
                }
                float sag = 0.05f + len * 0.35f;
                float r = (0.0065f / (1 + len * 4)) * (1 - snap * 0.7f) + 0.0015f;
                if (snap <= 0) {
                    Vector3f mid = new Vector3f(a).lerp(b, 0.5f).add(visualDir(0, -sag, 0));
                    out.tube(a, mid, b, 16, r * U);
                } else {
                    // the two halves fall and pull back to each end
                    Vector3f from = i % 2 == 1 ? a : b, to = i % 2 == 1 ? b : a;
                    Vector3f end = new Vector3f(from).lerp(to, 0.5f * (1 - snap)).add(visualDir(0, -sag * (0.6f + snap), 0));
                    Vector3f mid = new Vector3f(from).lerp(end, 0.5f).add(visualDir(0, -sag * 0.3f, 0));
                    out.tube(from, mid, end, 16, r * U);
                }
            }
        }

        // strings trailing behind the blade in flight: each hangs from a point on it to where that point was a moment
        // ago, thins as it stretches, snaps into drops and grows again
        boolean flying = tick >= 23 && tick < 49;
        for (int i = 0; i < STRING_Y.length; i++) {
            List<float[]> hist = run.strings.get(i);
            float dist = dTip - (TIP - STRING_Y[i]) * k;
            if (!flying || dist > path.dMouth) {
                hist.clear();
                continue;
            }
            path.frame(dist, f);
            Vector3f a = new Vector3f(f.p);
            hist.add(new float[] { tick, a.x, a.y, a.z });
            while (hist.size() > 2 && hist.get(1)[0] < tick - 3.5f) {
                hist.remove(0);
            }
            float[] first = hist.get(0);
            Vector3f b = new Vector3f(first[1], first[2], first[3]);
            float len = a.distance(b) / U;
            float max = 0.28f + 0.12f * ((i * 7) % 3);
            if (len > max) {
                run.drops.add(new Drop(b, visualDir(0, -0.1f, 0), 0.014f + RANDOM.nextFloat() * 0.01f));
                run.drops.add(new Drop(new Vector3f(b).lerp(a, 0.4f), new Vector3f(), 0.01f));
                hist.clear();
                hist.add(new float[] { tick, a.x, a.y, a.z });
                continue;
            }
            if (len < 0.02f) {
                continue;
            }
            Vector3f mid = new Vector3f(a).lerp(b, 0.5f).add(visualDir(0, -0.04f - len * 0.4f, 0));
            out.tube(a, mid, b, 14, (0.006f * (1 - len / max) + 0.0012f) * U);
        }

        // drops shaken off in flight
        if (tick >= 24 && tick < 46 && Math.floor(tick * 3) != Math.floor(run.lastTick * 3)) {
            path.frame(dTip - (TIP - 8 - RANDOM.nextFloat() * 22) * k, f);
            run.drops.add(new Drop(new Vector3f(f.p), visualDir((RANDOM.nextFloat() - 0.5f) * 0.3f, 0.05f, (RANDOM.nextFloat() - 0.5f) * 0.3f),
                    0.014f + RANDOM.nextFloat() * 0.01f));
        }
        // squeezed out round the mouth as it slides in
        if (tick >= 46 && tick < 53 && Math.floor(tick * 2) != Math.floor(run.lastTick * 2)) {
            Vector3f side = new Vector3f(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() * 0.5f, RANDOM.nextFloat() - 0.5f).normalize();
            run.drops.add(new Drop(new Vector3f(path.mouth).add(visualDir(side.x * 0.04f, side.y * 0.04f, side.z * 0.04f)),
                    visualDir(side.x * 0.25f, side.y * 0.25f + 0.1f, side.z * 0.25f), 0.012f));
        }
        float dt = Math.max(0, Math.min(0.1f, (tick - run.lastTick) / 20));
        for (int i = run.drops.size() - 1; i >= 0; i--) {
            Drop drop = run.drops.get(i);
            drop.life += dt;
            // the page's gravity, -9.8 * 0.45 up its y: down here is +y
            drop.v.y += 9.8f * dt * 0.45f * U;
            drop.p.fma(dt, drop.v);
            if (drop.life > 0.75f) {
                run.drops.remove(i);
                continue;
            }
            // a drop stretches along its fall, like slime
            float speed = drop.v.length() / U;
            float r = drop.size * Math.max(0.05f, 1 - drop.life * 1.3f) * U;
            out.drop(drop.p, drop.v, r, r * (1 + speed * 2.2f));
        }
        run.lastTick = tick;
    }

    // Writes the vertices: through souls_flesh (flesh and wet in the color, the unbent height for the veins in the
    // overlay slot), or tinted on a vanilla entity type when that shader can't run.
    private static final class Out {
        final VertexConsumer out;
        final PoseStack.Pose pose;
        final int light;
        final boolean shader;
        final float slimeU, slimeV;

        Out(VertexConsumer out, PoseStack.Pose pose, int light, boolean shader) {
            this.out = out;
            this.pose = pose;
            this.light = light;
            this.shader = shader;
            float[] blade = KokushiboSwordRenderer.katana().data();
            slimeU = blade[3];
            slimeV = blade[4];
        }

        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, float flesh, float wet, boolean slime, float sy, float vein) {
            out.addVertex(pose, x, y, z);
            if (shader) {
                out.setColor(flesh, wet, 0f, slime ? 0f : 1f);
                out.setUv(u, v);
                out.setUv1(Math.round(sy * 100), Math.round(vein * 100));
            } else {
                float m = slime ? 1 : flesh * 0.8f;
                out.setColor(1 - m * 0.45f, 1 - m * 0.92f, 1 - m * 0.86f, 1f);
                out.setUv(slime ? slimeU : u, slime ? slimeV : v);
                out.setOverlay(OverlayTexture.NO_OVERLAY);
            }
            out.setLight(light);
            out.setNormal(pose, nx, ny, nz);
        }

        // A mesh through a matrix (relative to pose), each triangle as a quad with its last corner repeated.
        void mesh(float[] d, Matrix4f m, float flesh, float wet) {
            Matrix3f normal = new Matrix3f(m).normal();
            Vector3f p = new Vector3f(), n = new Vector3f();
            for (int i = 0; i < d.length; i += 24) {
                for (int c = 0; c < 4; c++) {
                    int o = i + Math.min(c, 2) * 8;
                    m.transformPosition(p.set(d[o], d[o + 1], d[o + 2]));
                    normal.transform(n.set(d[o + 5], d[o + 6], d[o + 7])).normalize();
                    vertex(p.x, p.y, p.z, d[o + 3], d[o + 4], n.x, n.y, n.z, flesh, wet, false, d[o + 1], d[o] * 5 + d[o + 2] * 7);
                }
            }
        }

        // The bent sword: positions and normals already worked out per vertex.
        void bent(float[] d, float[] pos, float[] nor, float flesh, float wet) {
            for (int i = 0; i < d.length / 8; i += 3) {
                for (int c = 0; c < 4; c++) {
                    int v = i + Math.min(c, 2), o = v * 8;
                    vertex(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2], d[o + 3], d[o + 4], nor[v * 3], nor[v * 3 + 1], nor[v * 3 + 2], flesh, wet, false,
                            d[o + 1], d[o] * 5 + d[o + 2] * 7);
                }
            }
        }

        // A tube of slime along a quadratic curve a -> m -> b.
        void tube(Vector3f a, Vector3f m, Vector3f b, int segments, float radius) {
            final int sides = 6;
            Vector3f[] c = new Vector3f[segments + 1];
            for (int i = 0; i <= segments; i++) {
                float u = i / (float) segments, w = 1 - u;
                c[i] = new Vector3f(a).mul(w * w).fma(2 * w * u, m).fma(u * u, b);
            }
            Vector3f[][] ring = new Vector3f[segments + 1][sides];
            Vector3f[][] rn = new Vector3f[segments + 1][sides];
            Vector3f side = null;
            for (int i = 0; i <= segments; i++) {
                Vector3f t = new Vector3f(c[Math.min(segments, i + 1)]).sub(c[Math.max(0, i - 1)]);
                if (t.lengthSquared() < 1e-12f) {
                    t.set(0, 1, 0);
                }
                t.normalize();
                if (side == null) {
                    side = Math.abs(t.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
                }
                side.fma(-side.dot(t), t).normalize();
                Vector3f other = new Vector3f(t).cross(side);
                for (int s = 0; s < sides; s++) {
                    double ang = s * Math.PI * 2 / sides;
                    Vector3f dir = new Vector3f(side).mul((float) Math.cos(ang)).fma((float) Math.sin(ang), other);
                    rn[i][s] = dir;
                    ring[i][s] = new Vector3f(c[i]).fma(radius, dir);
                }
            }
            for (int i = 0; i < segments; i++) {
                for (int s = 0; s < sides; s++) {
                    int s2 = (s + 1) % sides;
                    quad(ring[i][s], rn[i][s], ring[i + 1][s], rn[i + 1][s], ring[i + 1][s2], rn[i + 1][s2], ring[i][s2], rn[i][s2]);
                }
            }
        }

        // A drop: a small diamond stretched along its fall.
        void drop(Vector3f p, Vector3f v, float r, float length) {
            Vector3f z = v.lengthSquared() > 1e-8f ? new Vector3f(v).normalize() : new Vector3f(0, 1, 0);
            Vector3f x = Math.abs(z.y) < 0.9f ? new Vector3f(0, 1, 0).cross(z).normalize() : new Vector3f(1, 0, 0).cross(z).normalize();
            Vector3f y = new Vector3f(z).cross(x);
            Vector3f[] axes = { x, y, new Vector3f(x).negate(), new Vector3f(y).negate() };
            Vector3f top = new Vector3f(p).fma(length, z), bottom = new Vector3f(p).fma(-length, z);
            for (int i = 0; i < 4; i++) {
                Vector3f e1 = new Vector3f(p).fma(r, axes[i]), e2 = new Vector3f(p).fma(r, axes[(i + 1) % 4]);
                Vector3f n1 = new Vector3f(axes[i]).add(axes[(i + 1) % 4]).add(z).normalize();
                Vector3f n2 = new Vector3f(axes[i]).add(axes[(i + 1) % 4]).sub(z).normalize();
                quad(top, n1, e1, n1, e2, n1, e2, n1);
                quad(bottom, n2, e2, n2, e1, n2, e1, n2);
            }
        }

        private void quad(Vector3f a, Vector3f na, Vector3f b, Vector3f nb, Vector3f c, Vector3f nc, Vector3f d, Vector3f nd) {
            vertex(a.x, a.y, a.z, 0, 0, na.x, na.y, na.z, 1, 1, true, 0, 0);
            vertex(b.x, b.y, b.z, 0, 0, nb.x, nb.y, nb.z, 1, 1, true, 0, 0);
            vertex(c.x, c.y, c.z, 0, 0, nc.x, nc.y, nc.z, 1, 1, true, 0, 0);
            vertex(d.x, d.y, d.z, 0, 0, nd.x, nd.y, nd.z, 1, 1, true, 0, 0);
        }
    }

    // A point of the page's world (relative to the player's feet) in the player model's space.
    private static Vector3f visualPoint(float x, float y, float z) {
        return new Vector3f(x * U, -y * U + 1.501f, -z * U);
    }

    // A direction or offset of the page's world in the player model's space.
    private static Vector3f visualDir(float x, float y, float z) {
        return new Vector3f(x * U, -y * U, -z * U);
    }

    private static Vector3f at(Matrix4f m, float y) {
        return m.transformPosition(new Vector3f(0, y, 0));
    }

    // Rodrigues: v turned by angle about the unit axis.
    private static void rotate(Vector3f v, Vector3f axis, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        Vector3f cross = new Vector3f(axis).cross(v);
        float dot = axis.dot(v);
        v.mul(c).fma(s, cross).fma(dot * (1 - c), axis);
    }

    // three.js CatmullRomCurve3, open, centripetal (the page's curve), at u in 0..1.
    private static Vector3f catmullRom(List<Vector3f> pts, float u) {
        int l = pts.size();
        float p = (l - 1) * u;
        int i = (int) Math.floor(p);
        float w = p - i;
        if (i >= l - 1) {
            i = l - 2;
            w = 1;
        }
        Vector3f p0 = i > 0 ? pts.get(i - 1) : new Vector3f(pts.get(0)).sub(pts.get(1)).add(pts.get(0));
        Vector3f p1 = pts.get(i), p2 = pts.get(i + 1);
        Vector3f p3 = i + 2 < l ? pts.get(i + 2) : new Vector3f(pts.get(l - 1)).sub(pts.get(l - 2)).add(pts.get(l - 1));
        float dt0 = (float) Math.pow(p0.distanceSquared(p1), 0.25);
        float dt1 = (float) Math.pow(p1.distanceSquared(p2), 0.25);
        float dt2 = (float) Math.pow(p2.distanceSquared(p3), 0.25);
        if (dt1 < 1e-4f) {
            dt1 = 1;
        }
        if (dt0 < 1e-4f) {
            dt0 = dt1;
        }
        if (dt2 < 1e-4f) {
            dt2 = dt1;
        }
        return new Vector3f(segment(p0.x, p1.x, p2.x, p3.x, dt0, dt1, dt2, w), segment(p0.y, p1.y, p2.y, p3.y, dt0, dt1, dt2, w),
                segment(p0.z, p1.z, p2.z, p3.z, dt0, dt1, dt2, w));
    }

    private static float segment(float x0, float x1, float x2, float x3, float dt0, float dt1, float dt2, float w) {
        float t1 = (x1 - x0) / dt0 - (x2 - x0) / (dt0 + dt1) + (x2 - x1) / dt1;
        float t2 = (x2 - x1) / dt1 - (x3 - x1) / (dt1 + dt2) + (x3 - x2) / dt2;
        t1 *= dt1;
        t2 *= dt1;
        float c2 = -3 * x1 + 3 * x2 - 2 * t1 - t2, c3 = 2 * x1 - 2 * x2 + t1 + t2;
        return x1 + t1 * w + c2 * w * w + c3 * w * w * w;
    }

    private static float clamp01(float x) {
        return Math.max(0, Math.min(1, x));
    }

    private static float easeInOut(float u) {
        return u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2;
    }

    private static float easeOut(float u) {
        return 1 - (float) Math.pow(1 - u, 3);
    }

    private static float easeIn(float u) {
        return u * u * u;
    }

    private FleshSheathe() {
    }
}
