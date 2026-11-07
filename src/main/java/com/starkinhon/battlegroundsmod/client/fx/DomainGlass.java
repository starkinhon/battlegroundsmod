package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.util.Mth;

// The end of Gojo's Expansão de Domínio: the black ball breaking like real glass (the Visualizador's "V · Expansão: a
// quebra" runs the same code). It starts from ONE point of impact: crooked radial cracks run out of it in four jolts
// (one per glass sound), split on the way, and ring cracks join them (a glass web). Near the impact the pieces are
// small, far from it they are huge and crooked (some rings are missing, so neighbours come off as one piece). Then the
// pieces come loose in a wave from the impact, fly out, spin (the small ones faster), fall with gravity, bounce on the
// ground, slide and fade, with glowing edges, a glint when one turns towards the light, and glitter of glass.
// Everything is a function of time; the only state is the pattern, made once per domain and viewer.
final class DomainGlass {
    // Rings of cracks around the impact, from the impact (0) to the far side (RINGS).
    private static final int RINGS = 11;
    // Radial cracks out of the impact (plus 0 to 2 more).
    private static final int LINES = 11;
    // How long the wave of pieces coming loose takes, from the impact to the far side, in ticks.
    static final float LOOSE_TICKS = 12;
    private static final float GRAVITY = 0.05f;
    // The crack front (radians from the impact) at each jolt, at these fractions of the cracking time.
    private static final float[][] JOLTS = {{0, 0.5f}, {0.25f, 1.25f}, {0.5f, 2.2f}, {0.75f, 3.6f}};
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] PALE = FxDraw.rgb(0xDFF6FF);
    private static final float[] DARK = {0.02f, 0.012f, 0.035f};
    private static final float[] FROST = {0.36f, 0.4f, 0.48f};
    private static final Vector3f SUN = new Vector3f(0.3f, 1, 0.2f).normalize();

    private record Crack(boolean radial, Vector3f a, Vector3f m, Vector3f b, float t0, float t1, float speed, int ring) {
    }

    private static final class Cell {
        final List<Vector3f> poly = new ArrayList<>();
        final List<Vector3f[]> tris = new ArrayList<>();
        int ring;
        int[] top;
        Vector3f c;
        int parent;
    }

    private static final class Shard {
        final List<Cell> cells = new ArrayList<>();
        final List<Vector3f[]> edges = new ArrayList<>();
        Vector3f c = new Vector3f();
        Vector3f axis;
        float size;
        float theta;
        float[] h;
        float ground = Float.NaN;
    }

    private final Vector3f impact;
    private final List<Crack> cracks = new ArrayList<>();
    private final List<Shard> shards = new ArrayList<>();
    private final int seed;
    // The ground under each piece was looked up for this middle (it changes when the people inside come back).
    private Vector3f groundFor;

    // impact: the direction from the middle of the ball to where it breaks first.
    DomainGlass(int seed, Vector3f impact) {
        this.seed = seed;
        this.impact = new Vector3f(impact).normalize();
        build();
    }

    // ---- the pattern ---------------------------------------------------------------------------------------------------

    private record Line(float phi, int start, float speed) {
    }

    private void build() {
        int K = RINGS;
        float[] theta = new float[K + 1];
        for (int k = 0; k <= K; k++) {
            theta[k] = Mth.PI * (float) Math.pow(k / (float) K, 1.55);
        }
        List<Line> lines = new ArrayList<>();
        int M = LINES + (int) (h(0, 1) * 3);
        for (int l = 0; l < M; l++) {
            lines.add(new Line(Mth.TWO_PI * (l + 0.55f * (h(l, 2) - 0.5f)) / M, 0, 0.85f + 0.3f * h(l, 3)));
        }
        for (int l = 0; l < M; l++) {
            float a = lines.get(l).phi(), b = l + 1 < M ? lines.get(l + 1).phi() : lines.get(0).phi() + Mth.TWO_PI, w = b - a;
            if (h(l, 4) < 0.8f) {
                int ks = 2 + (int) (h(l, 5) * 4);
                float f = 0.3f + 0.4f * h(l, 6);
                lines.add(new Line(a + w * f, ks, 0.8f + 0.3f * h(l, 7)));
                int ks2 = ks + 2 + (int) (h(l, 8) * 3);
                if (ks2 < K - 1 && h(l, 9) < 0.6f) {
                    float big = f > 0.5f ? a + w * f * (0.35f + 0.3f * h(l, 10)) : a + w * (f + (1 - f) * (0.35f + 0.3f * h(l, 10)));
                    lines.add(new Line(big, ks2, 0.75f + 0.3f * h(l, 11)));
                }
            }
        }
        List<Line> sorted = new ArrayList<>();
        for (Line line : lines) {
            sorted.add(new Line(((line.phi() % Mth.TWO_PI) + Mth.TWO_PI) % Mth.TWO_PI, line.start(), line.speed()));
        }
        sorted.sort((p, q) -> Float.compare(p.phi(), q.phi()));
        int N = sorted.size();
        // The corners in (theta, phi) around the impact, crooked, turned so the impact is this.impact.
        Vector3f ex = new Vector3f(Math.abs(impact.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).cross(impact).normalize();
        Vector3f ez = new Vector3f(ex).cross(impact).normalize();
        float[][] th = new float[K + 1][N];
        Vector3f[][] P = new Vector3f[K + 1][N];
        for (int k = 0; k <= K; k++) {
            for (int i = 0; i < N; i++) {
                float t, p;
                if (k == 0) {
                    t = 0;
                    p = 0;
                } else if (k == K) {
                    t = Mth.PI;
                    p = 0;
                } else {
                    float dt = (theta[k + 1] - theta[k - 1]) * 0.5f;
                    t = theta[k] + (h(k * 64 + i, 12) - 0.5f) * 0.5f * dt;
                    p = sorted.get(i).phi() + (h(k * 64 + i, 13) - 0.5f) * 0.16f;
                }
                th[k][i] = t;
                float s = Mth.sin(t);
                P[k][i] = new Vector3f(ex).mul(s * Mth.cos(p)).add(new Vector3f(impact).mul(Mth.cos(t))).add(new Vector3f(ez).mul(s * Mth.sin(p)));
            }
        }
        // Cells: ring band k, from line i to the next line that is already there.
        Map<Integer, Integer> cellAt = new HashMap<>();
        List<Cell> cells = new ArrayList<>();
        for (int k = 0; k < K; k++) {
            for (int i = 0; i < N; i++) {
                if (sorted.get(i).start() > k) {
                    continue;
                }
                int j = next(sorted, k, i);
                Cell cell = new Cell();
                cell.ring = k;
                if (k > 0) {
                    edge(cell.poly, P[k][i], P[k][j], ringKey(k, i));
                }
                edge(cell.poly, P[k][j], P[k + 1][j], radialKey(k, j));
                List<Integer> top = new ArrayList<>();
                top.add(i);
                for (int x = next(sorted, k + 1, i); x != j && top.size() <= N; x = next(sorted, k + 1, x)) {
                    top.add(x);
                }
                top.add(j);
                for (int t = top.size() - 1; t > 0; t--) {
                    if (k + 1 < K) {
                        edge(cell.poly, P[k + 1][top.get(t)], P[k + 1][top.get(t - 1)], ringKey(k + 1, top.get(t - 1)));
                    } else {
                        add(cell.poly, P[K][0]);
                    }
                }
                edge(cell.poly, P[k + 1][i], P[k][i], radialKey(k, i));
                if (cell.poly.size() > 1 && cell.poly.get(0).distanceSquared(cell.poly.get(cell.poly.size() - 1)) < 1.0E-10f) {
                    cell.poly.remove(cell.poly.size() - 1);
                }
                if (cell.poly.size() < 3) {
                    continue;
                }
                cell.top = top.stream().mapToInt(Integer::intValue).toArray();
                Vector3f c = new Vector3f();
                for (Vector3f p : cell.poly) {
                    c.add(p);
                }
                cell.c = c.normalize();
                cell.parent = cells.size();
                cellAt.put(k * 64 + i, cells.size());
                cells.add(cell);
            }
        }
        // Cells with no ring crack between them come off as one piece.
        for (int n = 0; n < cells.size(); n++) {
            Cell cell = cells.get(n);
            if (cell.ring + 1 >= K) {
                continue;
            }
            for (int t = 0; t + 1 < cell.top.length; t++) {
                int x = cell.top[t];
                if (ring(cell.ring + 1, x, K)) {
                    continue;
                }
                Integer up = cellAt.get((cell.ring + 1) * 64 + x);
                if (up != null) {
                    int a = find(cells, n), b = find(cells, up);
                    if (a != b) {
                        cells.get(b).parent = a;
                    }
                }
            }
        }
        // The triangles of each cell, split to follow the curve.
        for (Cell cell : cells) {
            for (int n = 0; n < cell.poly.size(); n++) {
                split(cell.c, cell.poly.get(n), cell.poly.get((n + 1) % cell.poly.size()), cell.tris, 0);
            }
        }
        Map<Integer, Shard> byRoot = new HashMap<>();
        for (int n = 0; n < cells.size(); n++) {
            int root = find(cells, n);
            Shard shard = byRoot.computeIfAbsent(root, r -> new Shard());
            Cell cell = cells.get(n);
            shard.cells.add(cell);
            shard.c.add(cell.c);
        }
        byRoot.forEach((root, shard) -> {
            shard.c.normalize();
            float size = 0;
            for (Cell cell : shard.cells) {
                for (int n = 0; n < cell.poly.size(); n++) {
                    size = Math.max(size, angle(shard.c, cell.poly.get(n)));
                    shard.edges.add(new Vector3f[] {cell.poly.get(n), cell.poly.get((n + 1) % cell.poly.size())});
                }
            }
            shard.size = size;
            shard.theta = angle(shard.c, impact);
            shard.h = new float[] {h(root, 20), h(root, 21), h(root, 22), h(root, 23), h(root, 24)};
            shard.axis = FxWorld.sphere(seed + 7, root);
            shards.add(shard);
        });
        // The cracks: radial ones grow out from the impact, ring ones show up after, from one end to the other.
        for (int i = 0; i < N; i++) {
            for (int k = sorted.get(i).start(); k < K; k++) {
                Vector3f a = P[k][i], b = P[k + 1][i];
                cracks.add(new Crack(true, a, mid(a, b, radialKey(k, i)), b, th[k][i], k + 1 < K ? th[k + 1][i] : Mth.PI, sorted.get(i).speed(), k));
            }
        }
        for (int k = 1; k < K; k++) {
            for (int i = 0; i < N; i++) {
                if (sorted.get(i).start() > k || !ring(k, i, K)) {
                    continue;
                }
                int j = next(sorted, k, i);
                Vector3f a = P[k][i], b = P[k][j], m = mid(a, b, ringKey(k, i));
                boolean flip = h(ringKey(k, i), 16) < 0.5f;
                float t0 = Math.max(th[k][i], th[k][j]) + 0.2f + 0.3f * h(ringKey(k, i), 17);
                cracks.add(flip ? new Crack(false, b, m, a, t0, 0, 1, k) : new Crack(false, a, m, b, t0, 0, 1, k));
            }
        }
    }

    private float h(int k, int salt) {
        return FxWorld.hash(seed, k, salt);
    }

    private static int next(List<Line> lines, int k, int i) {
        int n = lines.size();
        for (int s = 1; s <= n; s++) {
            int j = (i + s) % n;
            if (lines.get(j).start() <= k) {
                return j;
            }
        }
        return i;
    }

    // Near the impact every ring crack is there; far from it many are missing (big crooked pieces).
    private boolean ring(int k, int i, int K) {
        if (k <= 0 || k >= K) {
            return true;
        }
        return h(k * 64 + i, 14) < (k <= 3 ? 1 : k <= 6 ? 0.8f : 0.55f);
    }

    private static int radialKey(int k, int i) {
        return (64 + k) * 64 + i;
    }

    private static int ringKey(int k, int i) {
        return (128 + k) * 64 + i;
    }

    private static int find(List<Cell> cells, int x) {
        while (cells.get(x).parent != x) {
            cells.get(x).parent = cells.get(cells.get(x).parent).parent;
            x = cells.get(x).parent;
        }
        return x;
    }

    private static void add(List<Vector3f> poly, Vector3f p) {
        if (poly.isEmpty() || poly.get(poly.size() - 1).distanceSquared(p) > 1.0E-10f) {
            poly.add(p);
        }
    }

    private void edge(List<Vector3f> poly, Vector3f a, Vector3f b, int key) {
        add(poly, a);
        if (a.distanceSquared(b) > 1.0E-10f) {
            Vector3f m = mid(a, b, key);
            if (m != null) {
                add(poly, m);
            }
        }
    }

    // The crooked middle of a crack (the same for the two pieces on either side).
    private Vector3f mid(Vector3f a, Vector3f b, int key) {
        Vector3f n = new Vector3f(a).add(b);
        if (n.lengthSquared() < 1.0E-10f) {
            return null;
        }
        n.normalize();
        float d = a.distance(b);
        Vector3f side = new Vector3f(n).cross(new Vector3f(b).sub(a));
        if (side.lengthSquared() < 1.0E-12f) {
            return n;
        }
        side.normalize((h(key, 15) - 0.5f) * 0.35f * d);
        return n.add(side).normalize();
    }

    private static float angle(Vector3f a, Vector3f b) {
        return (float) Math.acos(Mth.clamp(a.dot(b), -1, 1));
    }

    private static void split(Vector3f a, Vector3f b, Vector3f c, List<Vector3f[]> out, int depth) {
        if (depth > 3 || Math.max(angle(a, b), Math.max(angle(b, c), angle(c, a))) < 0.2f) {
            out.add(new Vector3f[] {a, b, c});
            return;
        }
        Vector3f ab = new Vector3f(a).add(b).normalize(), bc = new Vector3f(b).add(c).normalize(), ca = new Vector3f(c).add(a).normalize();
        split(a, ab, ca, out, depth + 1);
        split(ab, b, bc, out, depth + 1);
        split(ca, bc, c, out, depth + 1);
        split(ab, bc, ca, out, depth + 1);
    }

    // ---- the cracks ----------------------------------------------------------------------------------------------------

    // The crack front (radians from the impact), tc ticks into cracking that lasts span ticks.
    private static float front(float tc, float span) {
        float f = 0;
        for (int n = 0; n < JOLTS.length; n++) {
            float at = JOLTS[n][0] * span, from = n > 0 ? JOLTS[n - 1][1] : 0;
            if (tc >= at) {
                f = Mth.lerp(FxWorld.easeOut((tc - at) / 2.5f), from, JOLTS[n][1]);
            }
        }
        return f;
    }

    // How bright the last jolt still is.
    private static float pulse(float tc, float span) {
        float p = 0;
        for (float[] jolt : JOLTS) {
            float at = jolt[0] * span;
            if (tc >= at) {
                p = Math.max(p, (float) Math.exp(-(tc - at) / 2));
            }
        }
        return p;
    }

    private static Vector3f along(Crack c, float u) {
        return u < 0.5f ? new Vector3f(c.a()).lerp(c.m(), u * 2).normalize() : new Vector3f(c.m()).lerp(c.b(), (u - 0.5f) * 2).normalize();
    }

    // The cracks running over the ball (center relative to the camera). Inside, all of it shows; from outside only the
    // side facing the camera.
    void cracks(Vector3f center, float r, float tc, float span, boolean inside) {
        float f = front(tc, span), p = pulse(tc, span);
        float width = 0.1f + r * 0.004f;
        for (Crack c : cracks) {
            float F = f * (c.radial() ? c.speed() : 1);
            float u = c.radial() ? (F - c.t0()) / Math.max(1.0E-3f, c.t1() - c.t0()) : (F - c.t0()) / 0.35f;
            if (u <= 0) {
                continue;
            }
            u = Math.min(1, u);
            float w = width * (c.radial() ? 1 - 0.45f * c.ring() / RINGS : 0.6f) * (c.ring() <= 1 ? 1.5f : 1);
            int last = Math.max(1, Mth.ceil(u * 4));
            Vector3f prev = along(c, 0).mul(r * 1.004f).add(center);
            for (int s = 1; s <= last; s++) {
                Vector3f q = along(c, Math.min(u, s / 4f)).mul(r * 1.004f).add(center);
                if (inside || shows(q, center)) {
                    FxDraw.ribbon(prev, q, w * 4, FxDraw.BEAM, 0.2f, PALE, 0.25f + 0.3f * p, true);
                    FxDraw.ribbon(prev, q, w, FxDraw.BEAM, 0.9f, WHITE, Math.min(1, 0.85f + 0.5f * p), true);
                }
                prev = q;
            }
            // The growing tip: a spark.
            if (u < 1 && (inside || shows(prev, center))) {
                FxDraw.billboard(prev, w * 9, 0, FxDraw.FLARE, 6 / 32f, WHITE, 0.9f, true);
            }
        }
        // The point of impact: a star of light that flares at every jolt.
        Vector3f hit = new Vector3f(impact).mul(r * 1.01f).add(center);
        if (inside || shows(hit, center)) {
            FxDraw.billboard(hit, r * (0.12f + 0.2f * p), tc * 0.05f, FxDraw.FLARE, 6 / 32f, WHITE, 0.6f + 0.4f * p, true);
            FxDraw.billboard(hit, r * (0.3f + 0.25f * p), 0, FxDraw.SOFT, 0.2f, PALE, 0.15f + 0.25f * p, true);
        }
    }

    // A point on the ball faces the camera (at the origin).
    private static boolean shows(Vector3f p, Vector3f center) {
        return new Vector3f(p).sub(center).dot(new Vector3f(p).negate()) > 0;
    }

    // ---- the pieces ----------------------------------------------------------------------------------------------------

    // Where the ground is under each piece (world y), looked up again when the middle moves. ground(x, z) gives it.
    interface Ground {
        float at(double x, double z);
    }

    // The pieces: in place (the shell) until they come loose, then flying. ts: ticks since it shattered; life: ticks
    // until it is all gone; center relative to the camera, world the same middle in the world (for the ground).
    void shards(Vector3f center, net.minecraft.world.phys.Vec3 world, float r, float ts, float life, boolean inside, boolean white, Ground ground) {
        float fade = 1 - smooth((ts - (life - 14)) / 14);
        if (fade <= 0) {
            return;
        }
        Vector3f here = world.toVector3f();
        if (groundFor == null || groundFor.distanceSquared(here) > 1) {
            groundFor = here;
            for (Shard shard : shards) {
                Vector3f home = new Vector3f(shard.c).mul(r).add(here);
                shard.ground = ground.at(home.x, home.z);
            }
        }
        Quaternionf turn = new Quaternionf();
        for (Shard shard : shards) {
            float detach = LOOSE_TICKS * (float) Math.pow(shard.theta / Mth.PI, 0.9) + 1.5f * shard.h[3];
            float tau = ts - detach;
            boolean loose = tau > 0;
            Vector3f home = new Vector3f(shard.c).mul(r).add(center);
            Vector3f pos = new Vector3f(home);
            float spin = 0;
            if (loose) {
                float near = 1 - shard.theta / Mth.PI;
                Vector3f away = new Vector3f(shard.c).sub(new Vector3f(impact).mul(shard.c.dot(impact)));
                if (away.lengthSquared() > 1.0E-6f) {
                    away.normalize();
                }
                Vector3f v = new Vector3f(shard.c).mul((0.1f + 0.22f * shard.h[0]) * (1 + 1.6f * near * near)).add(away.mul(0.18f * near)).add(0, 0.06f * shard.h[1], 0);
                float rate = (0.05f + 0.16f * shard.h[2]) / Mth.sqrt(Math.max(0.6f, shard.size * r));
                float y0 = home.y + here.y - center.y - shard.ground;
                float hitT = y0 > 0 ? (v.y + Mth.sqrt(v.y * v.y + 2 * GRAVITY * y0)) / GRAVITY : Float.MAX_VALUE;
                if (tau < hitT) {
                    pos.add(new Vector3f(v).mul(tau)).add(0, -0.5f * GRAVITY * tau * tau, 0);
                    spin = rate * tau;
                } else {
                    // Lands, bounces a little and slides to a stop.
                    float tp = tau - hitT, vb = -(v.y - GRAVITY * hitT) * 0.28f, slide = 1 - (float) Math.exp(-tp / 6);
                    pos.add(new Vector3f(v).mul(hitT)).add(0, -0.5f * GRAVITY * hitT * hitT, 0);
                    pos.add(v.x * 2.1f * slide, 0, v.z * 2.1f * slide);
                    pos.y = shard.ground - (here.y - center.y) + 0.05f + Math.max(0, vb * tp - 0.5f * GRAVITY * tp * tp);
                    spin = rate * hitT + rate * 2 * (1 - (float) Math.exp(-tp / 4));
                }
            }
            turn.identity().rotateAxis(spin, shard.axis.x, shard.axis.y, shard.axis.z);
            float shrink = loose ? 1 - 0.15f * smooth(tau / 30) : 1;
            Vector3f normal = new Vector3f(shard.c).rotate(turn);
            Vector3f toCam = new Vector3f(pos).negate().normalize();
            // From outside, while in place, only the front half (the back is hidden behind the ball anyway).
            if (!loose && !inside && normal.dot(toCam) < -0.05f) {
                continue;
            }
            boolean milky = white || shard.h[4] < 0.15f;
            float[] base = white ? PALE : DARK;
            float[] tint = loose ? mix(base, milky ? (white ? PALE : FROST) : DARK, smooth(tau / 4)) : base;
            float opacity = (loose ? (milky && !white ? 0.8f : 0.95f) : 1) * fade;
            Vector3f refl = new Vector3f(toCam).negate().reflect(normal);
            float glint = loose ? (float) Math.pow(Math.max(0, refl.dot(SUN)), 30) * 0.6f * fade : 0;
            for (Cell cell : shard.cells) {
                for (Vector3f[] t : cell.tris) {
                    Vector3f a = place(t[0], shard, r, home, pos, turn, shrink, center);
                    Vector3f b = place(t[1], shard, r, home, pos, turn, shrink, center);
                    Vector3f c = place(t[2], shard, r, home, pos, turn, shrink, center);
                    FxDraw.quad(a, b, c, c, FxDraw.SOLID, 0, tint, opacity, false);
                    if (glint > 0.03f) {
                        FxDraw.quad(a, b, c, c, FxDraw.SOLID, 0, PALE, glint, true);
                    }
                }
            }
            // Edges of light: bright just before and just after it comes loose, then a thin line.
            float hot = loose ? 0.45f + 0.55f * (float) Math.exp(-tau / 6) : 0.55f + 0.45f * smooth((ts + 3 - detach) / 3 + 1);
            float w = (0.08f + r * 0.003f) * (loose ? 0.8f : 1);
            for (Vector3f[] e : shard.edges) {
                Vector3f a = place(e[0], shard, r, home, pos, turn, shrink, center);
                Vector3f b = place(e[1], shard, r, home, pos, turn, shrink, center);
                FxDraw.ribbon(a, b, w * 3, FxDraw.BEAM, 0.2f, PALE, 0.12f * hot * fade, true);
                FxDraw.ribbon(a, b, w, FxDraw.BEAM, 0.9f, WHITE, hot * fade, true);
            }
            // Glass dust off the pieces near the impact.
            if (loose && shard.theta < 0.9f && tau < 16) {
                FxDraw.billboard(pos, (2 + 4 * shard.h[0]) * (0.5f + tau / 16), shard.h[1] * 6, FxDraw.SMOKE, 0.4f + 0.03f * tau, PALE,
                        0.18f * (1 - tau / 16) * fade, false);
            }
        }
        // Glitter: bits of glass thrown out of the impact, twinkling.
        Vector3f hit = new Vector3f(impact).mul(r).add(center);
        float floor = Float.NaN;
        for (Shard shard : shards) {
            if (shard.theta < 0.3f) {
                floor = shard.ground - (here.y - center.y);
                break;
            }
        }
        for (int k = 0; k < 70; k++) {
            float tt = ts - FxWorld.hash(991, k, 4);
            if (tt <= 0) {
                continue;
            }
            Vector3f dir = FxWorld.sphere(991, k).mul(0.7f).add(impact).normalize();
            float speed = 0.4f + 0.9f * FxWorld.hash(991, k, 5);
            Vector3f p = new Vector3f(dir).mul(speed * tt).add(hit).add(0, -0.5f * GRAVITY * tt * tt, 0);
            if (!Float.isNaN(floor) && p.y < floor) {
                continue;
            }
            float twinkle = 0.5f + 0.5f * Mth.sin(tt * (0.8f + FxWorld.hash(991, k, 6)) + k);
            FxDraw.billboard(p, 0.5f + 0.6f * FxWorld.hash(991, k, 7), tt * 0.2f, FxDraw.FLARE, 6 / 32f, WHITE, twinkle * fade * (1 - smooth(tt / 50)), true);
        }
    }

    // A corner of a piece where the piece is now: turned about its own middle, a little smaller once loose.
    private static Vector3f place(Vector3f unit, Shard shard, float r, Vector3f home, Vector3f pos, Quaternionf turn, float shrink, Vector3f center) {
        return new Vector3f(unit).mul(r).add(center).sub(home).mul(shrink).rotate(turn).add(pos);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }
}
