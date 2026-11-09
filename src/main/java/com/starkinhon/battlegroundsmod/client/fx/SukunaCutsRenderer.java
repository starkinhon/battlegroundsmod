package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
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

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

// Sukuna's technique cuts (power/powers/sukuna): the shapes of the 3D previews Stark approved, drawn with
// shaders/core/souls_sukuna_fx. Everything is a function of the time since the server said it started, as in the previews.
//   - Slash: a thrown Dismantle (forja/sukuna-desmantelar, the Z): the crescent, white-hot in the hand and black with a
//     white outline in flight, its animated trail, dashes torn off its edge, a four-point star at each tip when it snaps to
//     full size; past its reach it breaks into pieces of itself that drift apart and burn away, with chips. The standing
//     one leaves a black groove on the ground. WORLD: the World Cutting Slash (forja/sukuna-corte-mundo): the same cut,
//     enormous and thin, growing out of the hand, with two afterimages.
//   - Bar: one line of the Shift+Z born on the opponent: drawn across in a flash, held, then broken into pieces that burn
//     away, with chips, dashes and sometimes a star at its tip.
//   - Waffle: the crouched World Cutting Slash: a grid of Shift+Z lines flying out, each dragging the plane it sweeps.
//   - Star: a four-point star (where a cut goes into someone, a word of the chant in the hand).
public final class SukunaCutsRenderer {
    public static final int GROOVE = 1, WORLD = 2;

    // A thrown crescent. S: the crescent's unit in blocks (its tips are 1.077 units from the middle); reach: how far its
    // front flies before it breaks; ground: the height of the ground under it, for the groove.
    private record Slash(double start, Vec3 origin, Vec3 dir, Vec3 across, Vec3 n, double S, double speed, double reach, float seed,
            int flags, double ground) {
        boolean world() {
            return (flags & WORLD) != 0;
        }

        double pass() {
            return reach / speed;
        }

        double end() {
            return pass() + (world() ? 16 : 46);
        }
    }

    private record Bar(double start, Vec3 center, Vec3 dir, double len, double thick, double shift, float skew, float rough, float s0, float s1,
            float seed, boolean star, int k) {
    }

    private record Waffle(double start, Vec3 origin, Vec3 dir, Vec3 right, Vec3 up, double size, double above, double speed, double reach,
            float seed) {
        double flight() {
            return reach / speed;
        }
    }

    private record Star(double start, Vec3 pos, double size, float spin) {
    }

    private static final List<Slash> SLASHES = new ArrayList<>();
    private static final List<Bar> BARS = new ArrayList<>();
    private static final List<Waffle> WAFFLES = new ArrayList<>();
    private static final List<Star> STARS = new ArrayList<>();

    // The Z's timings (ticks): the crescent snaps to full size, the pieces burn away, the groove stays.
    private static final double SNAP = 0.75, BITS_LIFE = 13, CHIPS_LIFE = 9, DASH_LIFE = 5, STAR_LIFE = 5;
    private static final int NB = 6, NB_WORLD = 7, NCHIP = 18, NDASH = 12, NDASH_WORLD = 44;
    // The Shift+Z line: ticks to draw across, ticks it holds before breaking, pieces, chips, dashes.
    private static final double BAR_FORM = 1.0, BAR_BREAK = 5;
    private static final int BAR_NP = 4, BAR_NC = 4, BAR_ND = 3;
    // The waffle: lines a side, how far past the grid the lines run (in grid units of 110), grow distance.
    private static final int WN = 9;

    private SukunaCutsRenderer() {
    }

    public static void slash(Vec3 origin, Vec3 dir, Vec3 across, float unit, float speed, float reach, int seed, int flags, float ground) {
        Vec3 d = dir.normalize();
        Vec3 a = across.subtract(d.scale(across.dot(d)));
        a = a.lengthSqr() < 1.0E-6 ? new Vec3(-d.z, 0, d.x).normalize() : a.normalize();
        SLASHES.add(new Slash(now(), origin, d, a, a.cross(d).normalize(), Math.max(0.05, unit), Math.max(0.05, speed), reach,
                (seed & 0xFFFF) / 6553.6f, flags, ground));
    }

    public static void bar(Vec3 center, Vec3 dir, float len, float thick, float shift, float skew, float rough, float s0, float s1, int seed,
            boolean star) {
        BARS.add(new Bar(now(), center, dir.normalize(), len, thick, shift, skew, rough, s0, s1, (seed & 0xFFFF) / 6553.6f, star, seed & 0xFFFF));
    }

    public static void waffle(Vec3 origin, Vec3 dir, Vec3 up, float size, float above, float speed, float reach, int seed) {
        Vec3 d = dir.normalize();
        Vec3 u = up.subtract(d.scale(up.dot(d))).normalize();
        WAFFLES.add(new Waffle(now(), origin, d, d.cross(u).normalize(), u, size, above, Math.max(0.05, speed), reach,
                (seed & 0xFFFF) / 6553.6f));
    }

    public static void star(Vec3 pos, float size, int seed) {
        STARS.add(new Star(now(), pos, size, (float) ((hash(seed) - 0.5) * 0.6)));
    }

    public static boolean active() {
        return !SLASHES.isEmpty() || !BARS.isEmpty() || !WAFFLES.isEmpty() || !STARS.isEmpty();
    }

    public static void clear() {
        SLASHES.clear();
        BARS.clear();
        WAFFLES.clear();
        STARS.clear();
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ---------- small helpers, as in the previews ----------

    private static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    private static double out(double u) {
        return 1 - Math.pow(1 - u, 3);
    }

    private static double snap(double u) {
        return 1 - Math.pow(1 - u, 5);
    }

    private static double io(double u) {
        return u < .5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
    }

    private static double cl(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    // A point on the crescent's arc (crescent units): sv along it (-1..1 tip to tip), rad from its centre (0, -0.72).
    private static double[] arcPt(double sv, double rad) {
        return new double[] {Math.sin(sv) * rad, -0.72 + Math.cos(sv) * rad};
    }

    // ---------- drawing ----------

    // The uniforms of one quad: everything is set for every quad, so nothing leaks from the one before.
    private static final class U {
        float mode, a = 1, heat, tear, t, seed, s0 = -9, s1 = 9, tearSeed, len = 1, n = 3, asp = 1, rot, kind, grow = 1, close, flash, skew = 1,
                rough = 0.5f, o = 1, w = 0.5f, blood, thin = 1, px, fine, halo = 0.5f;

        static U of(float mode) {
            U u = new U();
            u.mode = mode;
            return u;
        }

        void apply(ShaderInstance sh) {
            sh.safeGetUniform("Mode").set(mode);
            sh.safeGetUniform("A").set(a);
            sh.safeGetUniform("Heat").set(heat);
            sh.safeGetUniform("Tear").set(tear);
            sh.safeGetUniform("T").set(t);
            sh.safeGetUniform("Seed").set(seed);
            sh.safeGetUniform("S0").set(s0);
            sh.safeGetUniform("S1").set(s1);
            sh.safeGetUniform("TearSeed").set(tearSeed);
            sh.safeGetUniform("Len").set(len);
            sh.safeGetUniform("N").set(n);
            sh.safeGetUniform("Asp").set(asp);
            sh.safeGetUniform("Rot").set(rot);
            sh.safeGetUniform("Kind").set(kind);
            sh.safeGetUniform("Grow").set(grow);
            sh.safeGetUniform("Close").set(close);
            sh.safeGetUniform("Flash").set(flash);
            sh.safeGetUniform("Skew").set(skew);
            sh.safeGetUniform("Rough").set(rough);
            sh.safeGetUniform("O").set(o);
            sh.safeGetUniform("W").set(w);
            sh.safeGetUniform("Blood").set(blood);
            sh.safeGetUniform("Thin").set(thin);
            sh.safeGetUniform("Px").set(px);
            sh.safeGetUniform("Fine").set(fine);
            sh.safeGetUniform("Halo").set(halo);
        }
    }

    private static ShaderInstance shader;
    private static Vec3 cam = Vec3.ZERO;
    private static Vec3 camRight = new Vec3(1, 0, 0), camUp = new Vec3(0, 1, 0), camFwd = new Vec3(0, 0, 1);

    // A quad: centre c, half-extents along r (u 0..1) and f (v 0..1), in world space.
    private static void quad(U u, Vec3 c, Vec3 r, Vec3 f) {
        u.apply(shader);
        Vec3 m = c.subtract(cam);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        put(b, m.subtract(r).subtract(f), 0, 0);
        put(b, m.add(r).subtract(f), 1, 0);
        put(b, m.add(r).add(f), 1, 1);
        put(b, m.subtract(r).add(f), 0, 1);
        MeshData mesh = b.build();
        if (mesh != null) {
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(mesh);
        }
    }

    // A quad with four corners given (world space), in the order (0,0), (1,0), (1,1), (0,1).
    private static void quad4(U u, Vec3 p00, Vec3 p10, Vec3 p11, Vec3 p01) {
        u.apply(shader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        put(b, p00.subtract(cam), 0, 0);
        put(b, p10.subtract(cam), 1, 0);
        put(b, p11.subtract(cam), 1, 1);
        put(b, p01.subtract(cam), 0, 1);
        MeshData mesh = b.build();
        if (mesh != null) {
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void put(BufferBuilder b, Vec3 p, float u, float v) {
        b.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v);
    }

    // Facing the camera, turned by spin, half-size s.
    private static void billboard(U u, Vec3 c, double s, double spin) {
        double cs = Math.cos(spin), sn = Math.sin(spin);
        Vec3 r = camRight.scale(cs).add(camUp.scale(sn)).scale(s);
        Vec3 f = camUp.scale(cs).subtract(camRight.scale(sn)).scale(s);
        quad(u, c, r, f);
    }

    // A ribbon fixed along a world direction d that turns its face to the camera around it: length len, width wid.
    private static void ribbon(U u, Vec3 c, Vec3 d, double len, double wid, Vec3 fallback) {
        Vec3 view = cam.subtract(c).normalize();
        Vec3 wv = d.cross(view);
        wv = wv.lengthSqr() < 1.0E-6 ? fallback : wv.normalize();
        quad(u, c, d.scale(len / 2), wv.scale(wid / 2));
    }

    // FxWorld calls this at the end of the level, with the model view set to the camera's turn.
    static void render(Vec3 camera) {
        shader = FxShaders.sukunaFx;
        if (!active() || shader == null) {
            return;
        }
        double now = now();
        SLASHES.removeIf(s -> now - s.start() > s.end() || now < s.start() - 40);
        BARS.removeIf(b -> now - b.start() > BAR_BREAK + 12 || now < b.start() - 40);
        WAFFLES.removeIf(w -> now - w.start() > w.flight() + 14 || now < w.start() - 40);
        STARS.removeIf(s -> now - s.start() > STAR_LIFE || now < s.start() - 40);
        if (!active()) {
            return;
        }
        cam = camera;
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = c.getLeftVector(), up = c.getUpVector(), look = c.getLookVector();
        camRight = new Vec3(-left.x(), -left.y(), -left.z());
        camUp = new Vec3(up.x(), up.y(), up.z());
        camFwd = new Vec3(look.x(), look.y(), look.z());
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        for (Slash s : SLASHES) {
            drawSlash(s, Math.max(0, now - s.start()));
        }
        for (Waffle w : WAFFLES) {
            drawWaffle(w, Math.max(0, now - w.start()));
        }
        for (Bar b : BARS) {
            drawBar(b, Math.max(0, now - b.start()));
        }
        for (Star s : STARS) {
            double st = Math.max(0, now - s.start());
            U u = U.of(4);
            u.halo = 0.5f;
            billboard(u, s.pos(), starSize(s.size(), st) / 2, s.spin() + st * 0.05);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    // The preview's star: pops to 1.25 in 0.6 ticks, settles, shrinks away by STAR_LIFE. size: its full width.
    private static double starSize(double s0, double st) {
        return s0 * (st < 0.6 ? out(st / 0.6) * 1.25 : 1.25 - 0.25 * cl((st - 0.6) / 1, 0, 1)) * (1 - Math.pow(st / STAR_LIFE, 2));
    }

    // ---------- the thrown crescent ----------

    // Its growth (0.55 -> 1 in SNAP ticks for the Z; growing with the distance out of the hand for the World Cutting Slash).
    private static double grow(Slash s, double lt) {
        if (!s.world()) {
            return 0.55 + 0.45 * snap(cl(lt / SNAP, 0, 1));
        }
        double ff = Math.min(s.reach(), s.speed() * lt);
        return cl(Math.pow(Math.max(ff, 0.05) / (0.306 * s.S()), 0.75), 0.012, 1);
    }

    // The middle of the crescent's quad at time lt, and its growth.
    private static Vec3 middle(Slash s, double lt) {
        double g = grow(s, lt);
        if (!s.world()) {
            return s.origin().add(s.dir().scale(s.speed() * lt - 0.35 * s.S()));
        }
        double ff = Math.min(s.reach(), s.speed() * Math.max(0, lt));
        return s.origin().add(s.dir().scale(ff * g - 0.56 * s.S() * g));
    }

    // A point on the crescent (crescent units cx, cy from the quad's middle) at time lt.
    private static Vec3 onCrescent(Slash s, double lt, double cx, double cy) {
        double g = grow(s, lt) * s.S();
        return middle(s, lt).add(s.across().scale(cx * g)).add(s.dir().scale(cy * g));
    }

    private static U crescentU(Slash s) {
        U u = U.of(0);
        u.seed = s.seed();
        if (s.world()) {
            u.thin = 0.075f;
            u.px = 1;
        }
        return u;
    }

    private static void drawSlash(Slash s, double lt) {
        boolean world = s.world();
        double S = s.S(), pass = s.pass();
        double FS = world ? S / 1.75 : 1;
        double heat = 1 - io(cl((lt - 0.6) / 1.1, 0, 1));
        // the groove on the ground, first (under everything)
        if ((s.flags() & GROOVE) != 0 && lt > 0.5) {
            double life = s.end() - 2;
            double from = 0.8, to = Math.min(s.reach(), s.speed() * lt);
            double L = Math.max(0.01, to - from);
            if (lt < life && L > 0.05) {
                Vec3 flat = new Vec3(s.dir().x, 0, s.dir().z);
                if (flat.lengthSqr() > 1.0E-4) {
                    flat = flat.normalize();
                    Vec3 side = new Vec3(-flat.z, 0, flat.x);
                    Vec3 base = new Vec3(s.origin().x, s.ground() + 0.02, s.origin().z);
                    U u = U.of(6);
                    u.o = 1;
                    u.w = 0.55f;
                    u.blood = 0;
                    u.a = (float) (lt < life - 10 ? 1 : (life - lt) / 10);
                    quad(u, base.add(flat.scale(from + L / 2)), flat.scale(L / 2), side.scale((world ? 1.2 : 0.7) / 2));
                }
            }
        }
        boolean flying = lt < pass;
        if (flying) {
            double g = grow(s, lt);
            Vec3 mid = middle(s, lt);
            // the trail: as long as the way it has come (the Z: at most 6.75 blocks; the world cut: 1.3 crescent units)
            double sc = S * g;
            double gone = world ? Math.min(s.reach(), s.speed() * lt) : s.speed() * lt + 0.4;
            double Lb = Math.min(world ? 1.3 * S : 6.75 * S / 1.75, Math.max(0.01, gone));
            Vec3 front = mid.add(s.dir().scale(0.62 * sc));
            U tu = U.of(1);
            tu.len = (float) (Lb / sc);
            tu.t = (float) lt;
            tu.seed = s.seed() + 2;
            if (world) {
                tu.thin = 0.075f;
                tu.fine = 1;
            }
            quad(tu, front.subtract(s.dir().scale(Lb / 2)), s.across().scale(sc), s.dir().scale(Lb / 2));
            // the world cut's two afterimages, thinner each
            if (world) {
                for (int k = 0; k < 2; k++) {
                    double gt = lt - (k + 1) * 0.55;
                    if (gt < 0.3) {
                        continue;
                    }
                    double gg = grow(s, gt) * (1 - 0.04 * (k + 1));
                    U gu = crescentU(s);
                    gu.seed = 2 + k;
                    gu.heat = (float) heat;
                    gu.tear = 0.35f + 0.2f * k;
                    gu.t = (float) (lt / 20 + k);
                    gu.a = 0.35f - 0.12f * k;
                    quad(gu, middle(s, gt), s.across().scale(S * gg), s.dir().scale(S * gg));
                }
            }
            U u = crescentU(s);
            u.heat = (float) heat;
            u.t = (float) (lt / 20);
            quad(u, mid, s.across().scale(S * g), s.dir().scale(S * g));
        }
        // dashes torn off its outer edge in flight, left behind along the path, stretching then shrinking away
        int nd = world ? NDASH_WORLD : NDASH;
        for (int k = 0; k < nd; k++) {
            double h1 = hash(k * 3.7 + 1), h2 = hash(k * 8.3 + 2), h3 = hash(k * 1.9 + 3);
            double born = world ? 0.3 + pass * ((double) k / nd) + h1 * 0.4 : 0.4 + (pass - 0.2) * ((double) k / nd) + h1 * 0.2;
            double life = world ? 6 : DASH_LIFE;
            double st = lt - born;
            if (st < 0 || st >= life || born > pass) {
                continue;
            }
            boolean black = hash(k * 7.7 + s.seed()) >= 0.55;
            double sv = (h2 * 2 - 1) * (world ? 0.9 : 0.85);
            double[] o = arcPt(sv, 1.2 + 0.15 * h3);
            double[] ap = arcPt(sv, 1);
            double ox = ap[0], oy = ap[1] + 0.72, ol = Math.sqrt(ox * ox + oy * oy);
            ox /= ol;
            Vec3 p = onCrescent(s, born, o[0], o[1])
                    .add(s.across().scale(ox * (0.15 + 0.3 * h1) * FS * (1 - Math.exp(-st / 1.2))))
                    .add(s.dir().scale(s.speed() * 0.12 * (1 - Math.exp(-st / 1.5))));
            double sz = world ? FS * 0.8 : 1;
            double L = (black ? 0.7 + 0.8 * h3 : 0.5 + 0.8 * h3) * sz * (0.6 + 0.6 * out(cl(st / 1.2, 0, 1))) * (1 - Math.pow(st / life, 2));
            double W = (black ? 0.14 + 0.08 * h1 : 0.08 + 0.06 * h1) * (world ? FS * 0.35 : 1) * (1 - 0.5 * st / life);
            Vec3 d = s.dir().add(s.across().scale(ox * 0.3)).normalize();
            U u = U.of(3);
            u.kind = black ? 1 : 0;
            u.seed = (float) hash(k * 1.3);
            u.a = (float) (1 - Math.pow(st / life, 3));
            ribbon(u, p, d, L, W, s.n());
        }
        // stars at the tips the moment it snaps to full size
        double full = world ? 0.306 * S / s.speed() : SNAP;
        for (int k = 0; k < 2; k++) {
            double st = lt - full;
            if (st < 0 || st >= STAR_LIFE || full > pass) {
                continue;
            }
            Vec3 p = onCrescent(s, full, Math.sin(k == 1 ? 0.97 : -0.97) * 1.2, -0.72 + Math.cos(0.97) * 1.2);
            U u = U.of(4);
            u.halo = world ? 0.95f : 0.5f;
            billboard(u, p, starSize(world ? 1.1 * FS * 0.45 : 1.1, st) / 2, (k - 1) * 0.25 + st * 0.05);
        }
        // past its reach: pieces of the crescent itself drift on, part, turn and burn away
        double bt = lt - pass;
        if (bt >= 0) {
            int nb = world ? NB_WORLD : NB;
            Vec3 at = middle(s, pass);
            double[] edges = new double[nb + 1];
            edges[0] = -1.2;
            edges[nb] = 1.2;
            for (int k = 1; k < nb; k++) {
                edges[k] = -1 + 2.0 * k / nb + (hash(k * 4.3 + (world ? 0 : s.seed())) - 0.5) * 0.18;
            }
            for (int k = 0; k < nb && bt < BITS_LIFE + (world ? 1 : 0); k++) {
                double h1 = hash(k * 1.7 + 5), h2 = hash(k * 2.9 + 1), h3 = hash(k * 5.3 + 2);
                double ps0 = Math.max(-1, edges[k]), ps1 = Math.min(1, edges[k + 1]);
                double sm = (ps0 + ps1) / 2;
                double[] pc = arcPt(sm, 1.12);
                double ox = pc[0], oy = pc[1] + 0.72, ol = Math.sqrt(ox * ox + oy * oy);
                ox /= ol;
                oy /= ol;
                double fw = (world ? S : s.speed() * 0.8) * (1 - Math.exp(-bt / 2.5)) * (0.75 + 0.5 * h1);
                double spread = (0.05 + 0.06 * h2) * bt;
                double drop = 0.0035 * bt * bt * FS * ((s.flags() & GROOVE) != 0 ? 1 : 1.6);
                Vec3 off = s.dir().scale(fw + oy * spread * S).add(s.across().scale(ox * spread * S)).add(s.n().scale((h3 - 0.5) * 0.09 * bt * FS))
                        .subtract(0, drop, 0);
                double spin = (h1 - 0.5) * 2.2 * out(cl(bt / 14, 0, 1)), tumble = (h2 - 0.5) * 1.6 * out(cl(bt / 12, 0, 1));
                Vec3 base = at.add(off);
                Vec3[] corner = new Vec3[4];
                double[][] cs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int i = 0; i < 4; i++) {
                    // about the piece's own middle: tumble about the span, then spin in the plane (the preview's order)
                    double x = cs[i][0] - pc[0], y = cs[i][1] - pc[1], z = 0;
                    double y1 = y * Math.cos(tumble) - z * Math.sin(tumble), z1 = y * Math.sin(tumble) + z * Math.cos(tumble);
                    double x2 = x * Math.cos(spin) - y1 * Math.sin(spin), y2 = x * Math.sin(spin) + y1 * Math.cos(spin);
                    x2 += pc[0];
                    y2 += pc[1];
                    corner[i] = base.add(s.across().scale(x2 * S)).add(s.dir().scale(y2 * S)).add(s.n().scale(z1 * S));
                }
                U u = crescentU(s);
                u.s0 = (float) edges[k];
                u.s1 = (float) edges[k + 1];
                u.heat = 0;
                u.tearSeed = k * 3.1f;
                u.tear = (float) cl((bt - 1 - h3 * 3) / 8, 0, 1);
                u.t = (float) ((pass + bt) / 20);
                quad4(u, corner[0], corner[1], corner[2], corner[3]);
            }
            // chips: little jagged bits flung off the edge as it breaks
            if (!world) {
                for (int k = 0; k < NCHIP; k++) {
                    double st = bt - (k % 5) * 0.3;
                    if (st < 0 || st >= CHIPS_LIFE) {
                        continue;
                    }
                    double h1 = hash(k * 3.1 + 2), h2 = hash(k * 1.3 + 7), h3 = hash(k * 8.9);
                    double[] o = arcPt(h1 * 2 - 1, 1.28 - 0.3 * h3);
                    double ox = o[0], oy = o[1] + 0.72, ol = Math.sqrt(ox * ox + oy * oy);
                    ox /= ol;
                    oy /= ol;
                    Vec3 v = s.across().scale(ox * (0.12 + 0.2 * h2)).add(s.dir().scale(0.25 + oy * 0.15)).add(s.n().scale((h3 - 0.5) * 0.25))
                            .add(0, 0.06, 0);
                    Vec3 p = at.add(s.across().scale(o[0] * S)).add(s.dir().scale(o[1] * S)).add(v.scale(st)).subtract(0, 0.01 * st * st, 0);
                    double sz = (0.1 + 0.18 * h2) * (1 - Math.pow(st / CHIPS_LIFE, 2));
                    U u = U.of(2);
                    u.n = 3 + (float) Math.floor(hash(k * 2.2) * 4);
                    u.asp = 0.6f + (float) hash(k * 6.1) * 1.4f;
                    u.rot = (float) (h1 * 6.28 + st * (h3 - 0.5) * 0.8);
                    billboard(u, p, sz / 2, 0);
                }
            }
        }
    }

    // ---------- the Shift+Z line ----------

    private static void drawBar(Bar b, double lt) {
        int k = b.k();
        Vec3 dir = b.dir();
        Vec3 nrm = dir.cross(new Vec3(0, 1, 0));
        nrm = nrm.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : nrm.normalize();
        Vec3 C = b.center();
        double wid = b.thick() * 2;
        if (lt < BAR_BREAK) {
            U u = barU(b);
            u.flash = (float) Math.exp(-lt / 1.5);
            u.grow = (float) snap(cl(lt / BAR_FORM, 0, 1));
            ribbon(u, C, dir, b.len(), wid, nrm);
        }
        double st = lt - BAR_BREAK;
        if (st >= 0 && st < 10) {
            for (int i = 0; i < BAR_NP; i++) {
                double f = (i + 0.5) / BAR_NP - 0.5, h1 = hash(k * 6.3 + i * 2.9), h2 = hash(k * 1.1 + i * 4.4);
                int side = (i + k) % 2 == 1 ? 1 : -1;
                Vec3 p = C.add(dir.scale(b.len() * f * (1 + 0.012 * st))).add(nrm.scale(side * (0.02 + 0.03 * h1) * st))
                        .add(camFwd.scale((h2 - 0.5) * 0.03 * st)).subtract(0, 0.003 * st * st, 0);
                U u = barU(b);
                u.s0 = b.s0() + (b.s1() - b.s0()) * i / BAR_NP;
                u.s1 = b.s0() + (b.s1() - b.s0()) * (i + 1) / BAR_NP;
                u.seed = b.seed() + i * 1.3f;
                u.grow = 1;
                u.tear = (float) (cl((st - 0.8 - h1 * 1.5) / 7, 0, 1) * 1.05);
                ribbon(u, p, dir, b.len() / BAR_NP * 0.97, wid, nrm);
            }
        }
        // chips where it struck
        for (int i = 0; i < BAR_NC; i++) {
            double cst = lt - 0.3 - i * 0.12;
            if (cst < 0 || cst >= 8) {
                continue;
            }
            double h1 = hash(k * 7 + i * 3.1), h2 = hash(k * 5 + i * 1.7), h3 = hash(k * 2 + i * 8.9);
            int side = i % 2 == 1 ? 1 : -1;
            Vec3 v = nrm.scale(side * (0.1 + 0.16 * h2)).add(dir.scale((h1 - 0.5) * 0.22)).add(camFwd.scale((h3 - 0.5) * 0.1)).add(0, 0.05, 0);
            Vec3 p = C.add(dir.scale(-b.shift() + (h1 - 0.5) * 0.9)).add(v.scale(cst)).subtract(0, 0.008 * cst * cst, 0);
            double sz = (0.12 + 0.15 * h2) * (1 - Math.pow(cst / 8, 2));
            U u = U.of(2);
            u.n = 3 + (float) Math.floor(hash(k * 5 + i * 2.2) * 4);
            u.asp = 0.6f + (float) hash(k * 3 + i * 6.1) * 1.4f;
            u.rot = (float) (h1 * 6.28 + cst * (h3 - 0.5) * 0.8);
            billboard(u, p, sz / 2, 0);
        }
        // dashes flung along the line, fast then slowing, stretching then shrinking away
        for (int i = 0; i < BAR_ND; i++) {
            double h1 = hash(k * 3.7 + i * 5.1), h2 = hash(k * 8.3 + i * 2.7), h3 = hash(k * 1.3 + i * 9.9);
            double dst = lt - 0.1 - h1 * 0.5;
            if (dst < 0 || dst >= 5) {
                continue;
            }
            boolean black = hash(k * 7.7 + i * 3.3) >= 0.55;
            int sgn = h2 < 0.5 ? -1 : 1;
            double off = (h3 - 0.5) * 2;
            Vec3 p = C.add(dir.scale(b.len() * (0.15 + 0.35 * h2) * sgn + sgn * (0.6 + 0.8 * h1) * (1 - Math.exp(-dst / 1.2))))
                    .add(nrm.scale(off * (0.12 + 0.25 * h1)));
            double L = (black ? 0.6 + 0.7 * h3 : 0.4 + 0.7 * h3) * (0.6 + 0.6 * out(cl(dst / 1.2, 0, 1))) * (1 - Math.pow(dst / 5, 2));
            double W = (black ? 0.13 + 0.08 * h1 : 0.07 + 0.06 * h1) * (1 - 0.5 * dst / 5);
            U u = U.of(3);
            u.kind = black ? 1 : 0;
            u.seed = (float) hash(k + i);
            u.a = (float) (1 - Math.pow(dst / 5, 3));
            ribbon(u, p, dir.add(nrm.scale((h1 - 0.5) * 0.25)).normalize(), L, W, nrm);
        }
        // a four-point star where the stroke finishes drawing
        if (b.star()) {
            double sst = lt - BAR_FORM + 0.05;
            if (sst >= 0 && sst < STAR_LIFE) {
                double sign = b.s0() > 0 ? 1 : hash(k * 23.3) < 0.5 ? 1 : -1;
                Vec3 tip = C.add(dir.scale(b.len() * 0.47 * sign));
                U u = U.of(4);
                billboard(u, tip, starSize(0.9 + 0.8 * hash(k * 25.7), sst) / 2, hash(k * 24.1) * 0.6 - 0.3 + sst * 0.05);
            }
        }
    }

    private static U barU(Bar b) {
        U u = U.of(5);
        u.seed = b.seed();
        u.s0 = b.s0();
        u.s1 = b.s1();
        u.skew = b.skew();
        u.rough = b.rough();
        u.t = 0;
        return u;
    }

    // ---------- the waffle ----------

    // One line of the grid (the preview's WL, in grid units of 110 x 110 with the bottom line just over the ground).
    private record Line(boolean vertical, double at, double slant, double width, double rough, float seed, double skew, double delay) {
    }

    private static Line line(Waffle w, boolean vertical, int k) {
        double u = (k + 0.5) / WN - 0.5;
        double s = w.seed();
        if (vertical) {
            return new Line(true, u * 110 + (hash(k * 3.1 + s) - 0.5) * 3.2, (hash(k * 5.7 + s) - 0.5) * 0.05, 0.4 + 0.35 * hash(k * 7.3 + s),
                    hash(k * 9.1 + s), (float) (k * 2.7 + 1 + s), 0.7 + 0.6 * hash(k * 3.9 + s), hash(k * 7.1) * 0.6);
        }
        double y = 56 + u * 110 + (hash(k * 4.3 + 1 + s) - 0.5) * 3.2;
        if (k == 0) {
            y = Math.max(y, 1.2);
        }
        return new Line(false, y, (hash(k * 6.1 + 2 + s) - 0.5) * 0.05, 0.4 + 0.35 * hash(k * 8.9 + 3 + s), hash(k * 10.7 + 4 + s),
                (float) (k * 3.1 + 40 + s), 0.7 + 0.6 * hash(k * 5.3 + 2 + s), hash(k * 7.1 + 3) * 0.6);
    }

    private static void drawWaffle(Waffle w, double tf) {
        double sc = w.size() / 110;
        double FL = w.flight();
        double fadeT = Math.max(0, tf - FL);
        double tear = cl(fadeT / 10, 0, 1);
        double f = Math.min(w.reach(), w.speed() * tf);
        double grow = cl(Math.pow(Math.max(f, 0.05) / (34 * sc), 0.75), 0.012, 1);
        double LEXT = 12;
        // the grid's middle: from the hand out, then flying straight; (x, y) in grid units around it
        Vec3 gc = w.origin().add(w.dir().scale(f)).add(w.up().scale(56 * sc - w.above()));
        gc = gc.add(w.origin().subtract(gc).scale(1 - grow));
        for (int v = 0; v < 2; v++) {
            for (int k = 0; k < WN; k++) {
                Line L = line(w, v == 0, k);
                Vec3 dir, cen;
                double len;
                if (L.vertical()) {
                    dir = w.up().add(w.right().scale(L.slant())).normalize();
                    cen = w.right().scale(L.at() * sc);
                    len = (110 + 2 * LEXT) * sc;
                } else {
                    dir = w.right().add(w.up().scale(L.slant())).normalize();
                    cen = w.up().scale((L.at() - 56) * sc);
                    len = (110 + 2 * LEXT) * sc;
                }
                Vec3 perp = w.dir().cross(dir).normalize();
                Vec3 c = gc.add(cen.scale(grow));
                double hq = L.width() / 0.07 * sc;
                // the plane it sweeps, behind it (as far as it has flown, 60 grid units at most)
                if (fadeT < 10 && grow >= 1) {
                    double back = Math.min(60 * sc, f);
                    U su = U.of(7);
                    su.len = (float) back;
                    su.seed = L.seed() * 1.2f;
                    su.a = (float) (1 - tear);
                    quad(su, c.subtract(w.dir().scale(back / 2)), dir.scale(len * grow / 2), w.dir().scale(back / 2));
                }
                if (fadeT >= 10) {
                    continue;
                }
                U u = U.of(5);
                u.seed = L.seed();
                u.skew = (float) L.skew();
                u.rough = (float) (0.3 + 0.6 * L.rough());
                u.grow = (float) cl((tf - L.delay()) / 1.0, 0, 1);
                u.flash = (float) (1 - cl((tf - L.delay()) / 3, 0, 1));
                u.tear = (float) tear;
                u.px = 1;
                quad(u, c, dir.scale(len * grow / 2), perp.scale(hq * grow / 2));
            }
        }
        // dashes torn off the lines in flight, left behind along the path
        for (int k = 0; k < NDASH_WORLD; k++) {
            double h1 = hash(k * 3.7 + 1), h2 = hash(k * 8.3 + 2), h3 = hash(k * 1.9 + 3);
            double born = 0.3 + FL * ((double) k / NDASH_WORLD) + h1 * 0.4, st = tf - born;
            if (st < 0 || st >= 6) {
                continue;
            }
            boolean black = hash(k * 7.7) >= 0.55;
            Line L = line(w, k % 2 == 1, k % WN);
            double ff = Math.min(w.reach(), w.speed() * born);
            Vec3 g0 = w.origin().add(w.dir().scale(ff)).add(w.up().scale(56 * sc - w.above()));
            double along = (h2 - 0.5) * 110 * 0.95;
            double sgn = h3 < 0.5 ? -1 : 1, push = sgn * (0.4 + 1.2 * h1) * (1 - Math.exp(-st / 1.2));
            Vec3 o = L.vertical()
                    ? g0.add(w.right().scale((L.at() + L.slant() * along) * sc + push)).add(w.up().scale(along * sc))
                    : g0.add(w.right().scale(along * sc)).add(w.up().scale((L.at() - 56 + L.slant() * along) * sc + push));
            Vec3 p = o.add(w.dir().scale(w.speed() * 0.12 * (1 - Math.exp(-st / 1.5))));
            double sz = 110 * sc / 1.75 * 0.45 * 0.12;
            double Ld = (black ? 0.7 + 0.8 * h3 : 0.5 + 0.8 * h3) * sz * 0.8 * (0.6 + 0.6 * out(cl(st / 1.2, 0, 1))) * (1 - Math.pow(st / 6, 2));
            double Wd = (black ? 0.14 + 0.08 * h1 : 0.08 + 0.06 * h1) * sz * 0.35 * (1 - 0.5 * st / 6);
            U u = U.of(3);
            u.kind = black ? 1 : 0;
            u.seed = (float) hash(k * 1.3);
            u.a = (float) (1 - Math.pow(st / 6, 3));
            ribbon(u, p, w.dir(), Ld, Wd, w.up());
        }
    }
}
