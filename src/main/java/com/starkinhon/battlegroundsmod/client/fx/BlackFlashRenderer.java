// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Black Flash, one effect for every soul that has it (Stark 2026-11-25, approved page forja/black-flash-v3, "insano").
// Gojo's Shift+Z (power/powers/gojo/AzulFlash), Itadori's X and Todo's Turbilhao and dash (power/powers/sub/jj) all send
// ModPayloads.BlackFlash; kind = event + 10 * style (event 0 slow motion, 1 the punch, 2 the throw; style 0 Gojo,
// 1 Itadori, 2 Todo). Everything is drawn by souls_bf, the page's own GLSL, and is a function of (tick - birth):
//  - bolts: thick crooked shapes (4 levels of displaced midpoints, 1 to 3 branches each), black inside with a vivid red
//    rim, that grow from the root in 2 ticks, hold, and die eaten from the tip; tiny ones fly off as sparks;
//  - the core on the fist: the space implodes for a tick and a half and bursts (the lens warp pulls, then pushes);
//  - cracks in the ground, black with a red glow, only on the punch and the throw;
//  - one impact frame that holds still for 3 ticks and fades back (never alternating colors: safe for epilepsy), at
//    most one every 2 s.
// The bolts' red glow goes to the bloom (drawGlow, FxWorld's glow layer); the bodies go over it afterwards (overlay),
// so the light never washes the black out.
public final class BlackFlashRenderer {
    // Each soul's look: scale, main bolts, sparks, crack radius, ticks a bolt holds, pull forward, up, sideways, warp.
    private record Style(float scale, int n, int sparks, float ground, float hold, float fwd, float up, float side, float warp, boolean echo,
            int[] waves) {
        float wavePow(int i) {
            return this == STYLES[0] ? 0.45f + 0.08f * i : this == STYLES[1] ? 0.55f : 0.6f;
        }
    }

    private static final Style[] STYLES = {
            new Style(1.45f, 11, 22, 4.6f, 5, 0.45f, 0.1f, 0, 1.0f, false, new int[] {8, 15, 21, 27, 33}),
            new Style(1.0f, 7, 14, 3.2f, 4, 0.4f, 0.05f, 0, 0.8f, true, new int[] {6, 12}),
            new Style(1.2f, 8, 16, 4.2f, 4, 0.3f, 0.35f, 0.6f, 0.9f, false, new int[] {5, 10}),
    };

    private record Path(float[] pts, float[] ws, float[] ds, float root, float total) {
    }

    private record Bolt(List<Path> paths, float len, float seed, float delay, float hold, float fade) {
    }

    private record Spark(Vector3f v, float[] local, float[] ws, float[] ds, float delay, float life, float seed) {
    }

    private record Burst(double t0, Vec3 pos, Vec3 dir, float power, float sc, List<Bolt> bolts, List<Spark> sparks, double groundY,
            float coreSeed, float groundSeed, boolean core, float radius, float warp) {
    }

    private record Slow(int entity, Vec3 contact, long born, int ticks) {
    }

    private record Grade(Vec3 pos, long born, int fit) {
    }

    private static final double GRADE_RANGE = 28;
    private static final int RED_HEX = 0xFF0018;
    private static final List<Slow> SLOWS = new ArrayList<>();
    private static final List<Grade> GRADES = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    // impact frames: when each holds (a frame never comes sooner than 40 ticks after the last)
    private static final List<double[]> FRAMES = new ArrayList<>();
    private static double lastFrame = -1.0E9;
    private static int seeds;

    // This frame's geometry, camera-relative: per vertex x y z u v and the color (seed, life, tip or grow, opacity).
    private static final Layer BOLT = new Layer(), GLOW = new Layer(), CORE = new Layer(), CORE_GLOW = new Layer(), GROUND = new Layer();
    private static float time;
    private static Vec3 cam = Vec3.ZERO;

    private BlackFlashRenderer() {
    }

    // ---------- events ----------

    public static void event(int kind, int entity, Vec3 pos, Vec3 dir, float charge, int ticks) {
        long now = ScreenFx.now();
        int ev = Math.floorMod(kind, 10);
        Style st = STYLES[Mth.clamp(kind / 10, 0, STYLES.length - 1)];
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        double ground = groundUnder(pos);
        int seed = entity * 7919 + (int) now * 31 + 977 * ++seeds;
        switch (ev) {
            case 0 -> SLOWS.add(new Slow(entity, pos, now, Math.max(1, ticks)));
            case 1 -> {
                SLOWS.removeIf(s -> s.entity() == entity);
                GRADES.add(new Grade(pos, now, Math.max(1, ticks)));
                BURSTS.add(burst(now, pos, d, st, 1.0f, seed, ground, true));
                if (st.echo()) {
                    BURSTS.add(burst(now + 3, pos.add(d.scale(0.5)), d, st, 0.55f, seed + 1, ground, true));
                }
                for (int i = 0; i < st.waves().length; i++) {
                    BURSTS.add(burst(now + st.waves()[i], pos, d, st, st.wavePow(i), (seed + 2 + i) * 7, ground, false));
                }
                frame(now);
            }
            default -> {
                BURSTS.add(burst(now, pos.add(d.scale(0.3)), d, st, 1.25f, seed * 13, ground, true));
                frame(now);
            }
        }
    }

    private static void frame(double at) {
        if (at - lastFrame >= 40) {
            lastFrame = at;
            FRAMES.add(new double[] {at});
        }
    }

    public static boolean active() {
        return !SLOWS.isEmpty() || !GRADES.isEmpty() || !BURSTS.isEmpty() || !FRAMES.isEmpty() || BOLT.n > 0 || GLOW.n > 0;
    }

    public static void clear() {
        SLOWS.clear();
        GRADES.clear();
        BURSTS.clear();
        FRAMES.clear();
        lastFrame = -1.0E9;
        for (Layer l : new Layer[] {BOLT, GLOW, CORE, CORE_GLOW, GROUND}) {
            l.n = 0;
        }
    }

    // ---------- the generator (the page's, the same calls of the RNG in the same order) ----------

    // mulberry32, as on the page.
    private static final class Rng {
        private int a;

        Rng(int seed) {
            a = seed;
        }

        float next() {
            a += 0x6D2B79F5;
            int t = (a ^ (a >>> 15)) * (1 | a);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return (float) (((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0);
        }
    }

    private static Vector3f[] basis(Vector3f d) {
        Vector3f n = new Vector3f(d).normalize();
        Vector3f a = Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f s1 = new Vector3f(n).cross(a).normalize();
        Vector3f s2 = new Vector3f(n).cross(s1);
        return new Vector3f[] {s1, s2};
    }

    // The broken path A -> B: `levels` rounds of displaced midpoints, each with half the swing.
    private static List<Vector3f> fractal(Rng rng, Vector3f a, Vector3f b, int levels, float rough) {
        List<Vector3f> pts = new ArrayList<>(List.of(new Vector3f(a), new Vector3f(b)));
        Vector3f[] s = basis(new Vector3f(b).sub(a));
        float amp = a.distance(b) * rough;
        for (int l = 0; l < levels; l++) {
            List<Vector3f> np = new ArrayList<>();
            np.add(pts.get(0));
            for (int i = 0; i < pts.size() - 1; i++) {
                Vector3f m = new Vector3f(pts.get(i)).lerp(pts.get(i + 1), 0.5f);
                m.add(new Vector3f(s[0]).mul((rng.next() * 2 - 1) * amp)).add(new Vector3f(s[1]).mul((rng.next() * 2 - 1) * amp));
                np.add(m);
                np.add(pts.get(i + 1));
            }
            pts = np;
            amp *= 0.5f;
        }
        return pts;
    }

    private static float[] widths(Rng rng, int n, float w0) {
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            w[i] = i == n - 1 ? 0 : w0 * (float) Math.pow(1 - u, 0.6) * (0.65f + 0.7f * rng.next());
        }
        return w;
    }

    private static float[] flat(List<Vector3f> pts) {
        float[] out = new float[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) {
            out[i * 3] = pts.get(i).x;
            out[i * 3 + 1] = pts.get(i).y;
            out[i * 3 + 2] = pts.get(i).z;
        }
        return out;
    }

    private static float[] dists(List<Vector3f> pts) {
        float[] d = new float[pts.size()];
        for (int i = 1; i < pts.size(); i++) {
            d[i] = d[i - 1] + pts.get(i).distance(pts.get(i - 1));
        }
        return d;
    }

    private static Vector3f sphere(Rng rng) {
        float z = rng.next() * 2 - 1;
        float a = rng.next() * Mth.TWO_PI;
        float s = (float) Math.sqrt(1 - z * z);
        return new Vector3f(Mth.cos(a) * s, z, Mth.sin(a) * s);
    }

    // One main bolt (points relative to the burst's center) with 1 to 3 branches.
    private static Bolt bolt(Rng rng, Vector3f from, Vector3f dir, float len, float w0, float delay, float hold, float fade) {
        Vector3f end = new Vector3f(dir).mul(len).add(from);
        List<Vector3f> pts = fractal(rng, from, end, 4, 0.26f);
        float[] ws = widths(rng, pts.size(), w0);
        float[] ds = dists(pts);
        List<Path> paths = new ArrayList<>();
        paths.add(new Path(flat(pts), ws, ds, 0, ds[ds.length - 1]));
        int nb = 1 + (int) Math.floor(rng.next() * 3);
        for (int b = 0; b < nb; b++) {
            int i = 3 + (int) Math.floor(rng.next() * (pts.size() - 7));
            Vector3f seg = new Vector3f(pts.get(i + 1)).sub(pts.get(i)).normalize();
            Vector3f[] s = basis(seg);
            Vector3f bd = new Vector3f(seg).add(new Vector3f(s[0]).mul((rng.next() * 2 - 1) * 0.9f)).add(new Vector3f(s[1]).mul((rng.next() * 2 - 1) * 0.9f)).normalize();
            float bl = len * (0.3f + 0.25f * rng.next());
            List<Vector3f> bp = fractal(rng, pts.get(i), new Vector3f(bd).mul(bl).add(pts.get(i)), 3, 0.25f);
            float[] bw = widths(rng, bp.size(), ws[i] * 0.6f);
            float[] bds = dists(bp);
            paths.add(new Path(flat(bp), bw, bds, ds[i], ds[i] + bds[bds.length - 1]));
        }
        return new Bolt(paths, ds[ds.length - 1], rng.next(), delay, hold, fade);
    }

    // One burst of bolts (the punch, a wave or the throw); power 1 = the punch.
    private static Burst burst(double t0, Vec3 pos, Vec3 dir, Style st, float power, int seed, double groundY, boolean core) {
        Rng rng = new Rng(seed);
        float sc = st.scale();
        Vector3f dv = dir.toVector3f();
        Vector3f fwd = new Vector3f(dv.x, 0, dv.z);
        if (fwd.lengthSquared() < 1.0E-4f) {
            fwd.set(0, 0, 1);
        }
        fwd.normalize();
        Vector3f right = new Vector3f(-fwd.z, 0, fwd.x);
        int n = Math.max(2, Math.round(st.n() * (0.45f + 0.55f * power)));
        List<Bolt> bolts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Vector3f d = sphere(rng);
            d.y *= 0.55f;
            d.add(new Vector3f(dv).mul(st.fwd())).add(0, st.up(), 0).add(new Vector3f(right).mul((rng.next() * 2 - 1) * st.side())).normalize();
            if (d.y < -0.25f) {
                d.y = -0.25f;
                d.normalize();
            }
            float len = (1.6f + 2.6f * rng.next()) * sc * (float) Math.sqrt(power);
            float w0 = (0.16f + 0.12f * rng.next()) * sc;
            Vector3f from = new Vector3f(d).mul(0.15f * sc);
            // the page draws these three after the bolt's own calls
            Bolt shape = bolt(rng, from, d, len, w0, 0, 0, 0);
            float delay = rng.next() * 1.2f;
            float hold = st.hold() + rng.next() * 2;
            float fade = 7 + rng.next() * 3;
            bolts.add(new Bolt(shape.paths(), shape.len(), shape.seed(), delay, hold, fade));
        }
        List<Spark> sparks = new ArrayList<>();
        int ns = Math.round(st.sparks() * (0.4f + 0.6f * power));
        for (int i = 0; i < ns; i++) {
            Vector3f v = sphere(rng).add(new Vector3f(dv).mul(0.5f));
            v.normalize().mul((0.25f + 0.35f * rng.next()) * sc);
            float len = (0.35f + 0.45f * rng.next()) * sc;
            List<Vector3f> local = fractal(rng, new Vector3f(), new Vector3f(v).normalize().mul(len), 2, 0.3f);
            float[] ws = widths(rng, local.size(), (0.04f + 0.03f * rng.next()) * sc);
            sparks.add(new Spark(v, flat(local), ws, dists(local), 0.5f + rng.next() * 1.5f, 8 + rng.next() * 8, rng.next()));
        }
        return new Burst(t0, pos, dir, power, sc, bolts, sparks, groundY, rng.next(), rng.next(), core, st.ground() * power, st.warp());
    }

    // ---------- every frame ----------

    // FxWorld, in the world pass: builds this frame's geometry and drives the screen (warp, grade, impact frame).
    static void render(Vec3 camera, double now, float partial) {
        for (Layer l : new Layer[] {BOLT, GLOW, CORE, CORE_GLOW, GROUND}) {
            l.n = 0;
        }
        time = (float) (now / 20);
        cam = camera;
        SLOWS.removeIf(s -> now - s.born() > s.ticks() + 2);
        GRADES.removeIf(g -> now - g.born() > g.fit() + 44);
        BURSTS.removeIf(b -> now - b.t0() > 64);
        FRAMES.removeIf(f -> now - f[0] > 8);
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player == null ? camera : mc.player.getEyePosition(partial);
        for (Slow s : SLOWS) {
            float age = (float) (now - s.born());
            float k = smooth(age / 18);
            Vec3 fist = fist(s, partial);
            if (fist != null) {
                charge(fist, k, now);
                ScreenFx.holdWarp(fist, 0.9f, 0.35f * k * 0.5f, 2, new Vec3(0, 1, 0), (float) (now / 20));
            }
            ScreenFx.sustainGrade(10, smooth(age / 6f) * near(eye, s.contact()), 0x1A3A8A, s.contact());
        }
        for (Grade g : GRADES) {
            float age = (float) (now - g.born());
            if (age < 7) {
                continue;
            }
            float k = age < g.fit() ? 1 : 1 - smooth((age - g.fit()) / 40f);
            ScreenFx.sustainGrade(11, k * near(eye, g.pos()), RED_HEX, g.pos());
        }
        // the impact frame: holds 3 ticks, fades in 4 (drawn over the grade while it lasts)
        for (double[] f : FRAMES) {
            float a = (float) (now - f[0]);
            float hold = a < 3 ? 1 : 1 - smooth((a - 3) / 4);
            Burst b = BURSTS.isEmpty() ? null : BURSTS.get(BURSTS.size() - 1);
            Vec3 at = b == null ? camera : b.pos();
            for (Burst c : BURSTS) {
                if (Math.abs(c.t0() - f[0]) < 0.5) {
                    at = c.pos();
                }
            }
            if (hold > 0.004f && FxConfig.on(FxConfig.IMPACT_FRAMES)) {
                ScreenFx.sustainGrade(15, hold * near(eye, at), RED_HEX, at);
            }
        }
        for (Burst b : BURSTS) {
            float age = (float) (now - b.t0());
            if (age < 0) {
                continue;
            }
            draw(b, age, camera);
            if (b.core() && age < 12) {
                float r = (1.8f + b.power()) * b.sc();
                float s = age < 1.5f ? 1.2f * smooth(age / 1.5f) : -0.8f * (1 - smooth((age - 1.5f) / 10));
                ScreenFx.holdWarp(b.pos(), r, s * b.warp() * 0.5f, 2, new Vec3(0, 1, 0), age / 20);
            }
        }
    }

    // Black sparks crackling round a fist (Gojo's slow motion, Itadori's timing ring): k 0 to 1.
    public static void charge(Vec3 fist, float k, double now) {
        if (k <= 0.01f) {
            return;
        }
        Vector3f f = fist.subtract(cam).toVector3f();
        int ep = (int) Math.floor(now / 2);
        float a = (float) (now / 2 - ep);
        int n = 2 + Math.round(5 * k);
        for (int i = 0; i < n; i++) {
            Rng rng = new Rng(ep * 97 + i * 13 + 5);
            Vector3f d = sphere(rng);
            float len = (0.25f + 0.45f * k * rng.next()) * 1.2f;
            List<Vector3f> pts = fractal(rng, new Vector3f(d).mul(0.08f).add(f), new Vector3f(d).mul(len).add(f), 2, 0.3f);
            float[] ws = widths(rng, pts.size(), 0.05f + 0.04f * k);
            float[] ds = dists(pts);
            float seed = rng.next();
            float life = 0.25f + 0.6f * a;
            float[] p = flat(pts);
            strip(BOLT, p, pts.size(), ws, ds, 0, ds[ds.length - 1], seed, life, k, 1);
            strip(GLOW, p, pts.size(), ws, ds, 0, ds[ds.length - 1], seed, life, k, 3.2f);
        }
    }

    private static final float[] CUT_P = new float[3 * 64];
    private static final float[] CUT_W = new float[64];
    private static final float[] CUT_D = new float[64];

    private static void draw(Burst b, float age, Vec3 camera) {
        Vector3f at = b.pos().subtract(camera).toVector3f();
        float sc = b.sc();
        // the core: implodes for a tick and a half, bursts and comes apart in ~12 ticks
        if (b.core() && age < 14) {
            boolean imp = age < 1.5f;
            float size = imp ? (0.9f - 0.45f * smooth(age / 1.5f)) * sc : (0.75f + 0.75f * easeOut((age - 1.5f) / 4)) * sc * (0.8f + 0.3f * b.power());
            float life = imp ? 0 : Mth.clamp((age - 3) / 11, 0, 1);
            quad(CORE, at, size, b.coreSeed(), life, imp ? 0 : 1);
            if (!imp) {
                quad(CORE_GLOW, at, size * 2.6f, b.coreSeed(), life, 1);
            }
        }
        float start = b.core() ? 1.5f : 0;
        for (Bolt bolt : b.bolts()) {
            float a = age - start - bolt.delay();
            if (a < 0) {
                continue;
            }
            float life = Mth.clamp((a - bolt.hold()) / bolt.fade(), 0, 1);
            if (life >= 1) {
                continue;
            }
            float reach = bolt.len() * 1.15f * easeOut(a / 2);
            int step = (int) Math.floor(a * 0.6f);
            for (int pi = 0; pi < bolt.paths().size(); pi++) {
                Path p = bolt.paths().get(pi);
                float upTo = reach - p.root();
                if (upTo <= 0.02f) {
                    continue;
                }
                int n = cut(p.pts(), p.ws(), p.ds(), Math.min(upTo, p.ds()[p.ds().length - 1]));
                // the drawing trembles a little at 12 fps, more toward the tip
                for (int i = 1; i < n; i++) {
                    float u = Math.min(1, (CUT_D[i] + p.root()) / p.total());
                    float j = 0.025f * bolt.len() * Mth.sin(Mth.PI * u);
                    CUT_P[i * 3] += (FxWorld.hash(i, pi, step) - 0.5f) * j;
                    CUT_P[i * 3 + 1] += (FxWorld.hash(i + 7, pi, step) - 0.5f) * j;
                    CUT_P[i * 3 + 2] += (FxWorld.hash(i + 13, pi, step) - 0.5f) * j;
                }
                for (int i = 0; i < n; i++) {
                    CUT_P[i * 3] += at.x;
                    CUT_P[i * 3 + 1] += at.y;
                    CUT_P[i * 3 + 2] += at.z;
                }
                strip(BOLT, CUT_P, n, CUT_W, CUT_D, p.root(), p.total(), bolt.seed(), life, -1, 1);
                strip(GLOW, CUT_P, n, CUT_W, CUT_D, p.root(), p.total(), bolt.seed(), life, -1, 3.2f);
            }
        }
        // sparks: little bolts flying out with drag and gravity
        for (Spark s : b.sparks()) {
            float a = age - s.delay();
            if (a < 0 || a > s.life()) {
                continue;
            }
            float life = a / s.life();
            float k = (float) ((1 - Math.pow(0.86, a)) / (1 - 0.86));
            Vector3f o = new Vector3f(s.v()).mul(k).add(at);
            o.y -= 0.012f * a * a;
            float total = s.ds()[s.ds().length - 1];
            int n = cut(s.local(), s.ws(), s.ds(), total * easeOut(a / 1.2f));
            for (int i = 0; i < n; i++) {
                CUT_P[i * 3] += o.x;
                CUT_P[i * 3 + 1] += o.y;
                CUT_P[i * 3 + 2] += o.z;
            }
            strip(BOLT, CUT_P, n, CUT_W, CUT_D, 0, total, s.seed(), Math.max(life, 0.15f), -1, 1);
            strip(GLOW, CUT_P, n, CUT_W, CUT_D, 0, total, s.seed(), Math.max(life, 0.15f), -1, 3.2f);
        }
        // the cracks in the ground: only the punch and the throw (stacked decals fought each other)
        if (b.core() && b.radius() > 0.5f && age < 60) {
            float gy = (float) (b.groundY() - camera.y) + (b.power() > 1.1f ? 0.06f : 0.04f);
            floorQuad(GROUND, at.x, gy, at.z, b.radius(), b.groundSeed(), Mth.clamp((age - 18) / 42, 0, 1), easeOut(age / 5) * 0.98f);
        }
    }

    // The path cut at `upTo` along it, into CUT_*; returns the points kept.
    private static int cut(float[] pts, float[] ws, float[] ds, float upTo) {
        int n = 0;
        for (int i = 0; i < ds.length && n < CUT_W.length; i++) {
            if (ds[i] <= upTo) {
                CUT_P[n * 3] = pts[i * 3];
                CUT_P[n * 3 + 1] = pts[i * 3 + 1];
                CUT_P[n * 3 + 2] = pts[i * 3 + 2];
                CUT_W[n] = ws[i];
                CUT_D[n] = ds[i];
                n++;
                continue;
            }
            if (i > 0) {
                float f = (upTo - ds[i - 1]) / Math.max(1.0E-5f, ds[i] - ds[i - 1]);
                CUT_P[n * 3] = Mth.lerp(f, pts[i * 3 - 3], pts[i * 3]);
                CUT_P[n * 3 + 1] = Mth.lerp(f, pts[i * 3 - 2], pts[i * 3 + 1]);
                CUT_P[n * 3 + 2] = Mth.lerp(f, pts[i * 3 - 1], pts[i * 3 + 2]);
                CUT_W[n] = Mth.lerp(f, ws[i - 1], ws[i]);
                CUT_D[n] = upTo;
                n++;
            }
            break;
        }
        return n;
    }

    // A ribbon through the points facing the camera (at the origin): uv = (distance along, -1..1 across), color = (seed,
    // life, tip 0..1, opacity). opacity < 0: 1.
    private static void strip(Layer L, float[] p, int n, float[] ws, float[] ds, float root, float total, float seed, float life, float opacity, float wMul) {
        if (n < 2) {
            return;
        }
        float op = opacity < 0 ? 1 : opacity;
        float[] lx = new float[n * 3];
        float[] rx = new float[n * 3];
        Vector3f t = new Vector3f();
        Vector3f s = new Vector3f();
        for (int i = 0; i < n; i++) {
            int a = i == 0 ? 0 : i - 1;
            int b = i == n - 1 ? n - 1 : i + 1;
            if (i == 0) {
                b = 1;
            }
            if (i == n - 1) {
                a = n - 2;
            }
            t.set(p[b * 3] - p[a * 3], p[b * 3 + 1] - p[a * 3 + 1], p[b * 3 + 2] - p[a * 3 + 2]);
            // to the camera = -point
            s.set(t).cross(-p[i * 3], -p[i * 3 + 1], -p[i * 3 + 2]);
            if (s.lengthSquared() < 1.0E-12f) {
                s.set(0, 1, 0);
            }
            s.normalize(ws[i] * wMul);
            lx[i * 3] = p[i * 3] + s.x;
            lx[i * 3 + 1] = p[i * 3 + 1] + s.y;
            lx[i * 3 + 2] = p[i * 3 + 2] + s.z;
            rx[i * 3] = p[i * 3] - s.x;
            rx[i * 3 + 1] = p[i * 3 + 1] - s.y;
            rx[i * 3 + 2] = p[i * 3 + 2] - s.z;
        }
        for (int i = 0; i < n - 1; i++) {
            float u0 = ds[i] + root, u1 = ds[i + 1] + root;
            float t0 = Math.min(1, u0 / total), t1 = Math.min(1, u1 / total);
            L.v(lx, i, u0, 1, seed, life, t0, op);
            L.v(rx, i, u0, -1, seed, life, t0, op);
            L.v(lx, i + 1, u1, 1, seed, life, t1, op);
            L.v(rx, i, u0, -1, seed, life, t0, op);
            L.v(rx, i + 1, u1, -1, seed, life, t1, op);
            L.v(lx, i + 1, u1, 1, seed, life, t1, op);
        }
    }

    // A square facing the camera.
    private static void quad(Layer L, Vector3f c, float size, float seed, float life, float grow) {
        Vector3f v = new Vector3f(c).negate().normalize();
        Vector3f up = Math.abs(v.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f a = new Vector3f(up).cross(v).normalize(size);
        Vector3f b = new Vector3f(v).cross(a).normalize(size);
        corners(L, c.x, c.y, c.z, a, b, seed, life, grow);
    }

    // A square lying on the ground.
    private static void floorQuad(Layer L, float x, float y, float z, float r, float seed, float life, float grow) {
        corners(L, x, y, z, new Vector3f(r, 0, 0), new Vector3f(0, 0, r), seed, life, grow);
    }

    private static void corners(Layer L, float x, float y, float z, Vector3f a, Vector3f b, float seed, float life, float grow) {
        float[] q = {
                x - a.x - b.x, y - a.y - b.y, z - a.z - b.z,
                x + a.x - b.x, y + a.y - b.y, z + a.z - b.z,
                x + a.x + b.x, y + a.y + b.y, z + a.z + b.z,
                x - a.x + b.x, y - a.y + b.y, z - a.z + b.z};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int k : new int[] {0, 1, 2, 0, 2, 3}) {
            L.v(q, k, uv[k][0], uv[k][1], seed, life, grow, 1);
        }
    }

    // ---------- drawing (FxWorld; matrices are the camera's, positions camera-relative) ----------

    // Into the glow layer (the bloom): the bolts' and the core's red light.
    static void drawGlow() {
        if (FxShaders.bf == null) {
            return;
        }
        begin();
        additive();
        flush(GLOW, 1);
        flush(CORE_GLOW, 3);
        end();
    }

    // After the glow: the cracks, then the black bodies over the light. Without the bloom (a shaderpack, the flat mode),
    // the light is added straight here, under them.
    static void overlay(boolean flat) {
        if (FxShaders.bf == null) {
            return;
        }
        begin();
        if (flat) {
            additive();
            flush(GLOW, 1);
            flush(CORE_GLOW, 3);
        }
        premul();
        flush(GROUND, 4);
        flush(BOLT, 0);
        flush(CORE, 2);
        end();
    }

    private static void begin() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
    }

    private static void end() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void additive() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE);
    }

    private static void premul() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private static void flush(Layer L, int layer) {
        if (L.n == 0) {
            return;
        }
        ShaderInstance sh = FxShaders.bf;
        sh.safeGetUniform("Layer").set((float) layer);
        sh.safeGetUniform("BfTime").set(time);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[] d = L.data;
        for (int i = 0; i < L.n; i++) {
            int o = i * 9;
            b.addVertex(d[o], d[o + 1], d[o + 2]).setUv(d[o + 3], d[o + 4]).setColor(d[o + 5], d[o + 6], d[o + 7], d[o + 8]);
        }
        MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(() -> sh);
        BufferUploader.drawWithShader(mesh);
    }

    // A growing list of vertices: x y z u v and the four color numbers.
    private static final class Layer {
        // past this, the rest of the frame's pieces are left out (never more than ~60k triangles)
        private static final int MAX = 180000;
        float[] data = new float[9 * 4096];
        int n;

        void v(float[] p, int i, float u, float w, float c0, float c1, float c2, float c3) {
            if (n >= MAX) {
                return;
            }
            if ((n + 1) * 9 > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            int o = n++ * 9;
            data[o] = p[i * 3];
            data[o + 1] = p[i * 3 + 1];
            data[o + 2] = p[i * 3 + 2];
            data[o + 3] = u;
            data[o + 4] = w;
            data[o + 5] = Mth.clamp(c0, 0, 1);
            data[o + 6] = Mth.clamp(c1, 0, 1);
            data[o + 7] = Mth.clamp(c2, 0, 1);
            data[o + 8] = Mth.clamp(c3, 0, 1);
        }
    }

    // ---------- helpers ----------

    // How much of the screen grade someone sees: all of it close by, none past GRADE_RANGE.
    private static float near(Vec3 eye, Vec3 at) {
        return 1 - smooth((float) ((eye.distanceTo(at) - GRADE_RANGE * 0.6) / (GRADE_RANGE * 0.4)));
    }

    // Gojo's right fist, cocked back during the slow motion.
    private static Vec3 fist(Slow s, float partial) {
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(s.entity());
        if (!(e instanceof LivingEntity living)) {
            return null;
        }
        float yaw = Mth.lerp(partial, living.yBodyRotO, living.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double scale = living.getBbHeight() / 1.8;
        return living.getPosition(partial).add(forward.scale(-0.12 * scale)).add(right.scale(0.4 * scale)).add(0, 1.2 * scale, 0);
    }

    // The top of the ground under the point (up to 6 blocks down), or a little under the point in the air.
    private static double groundUnder(Vec3 at) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return at.y - 1.3;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + 1;
            }
            pos.move(0, -1, 0);
        }
        return at.y - 1.3;
    }

    private static float easeOut(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
