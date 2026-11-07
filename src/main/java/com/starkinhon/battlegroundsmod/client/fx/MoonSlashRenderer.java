package com.starkinhon.battlegroundsmod.client.fx;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.YoruNoMiya;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

// Kokushibo's 1st Form, Yoru no Miya (power/powers/kokushibo/YoruNoMiya), drawn exactly as the Visualizer page Stark
// approved (forja/kokushibo-formas/yoru-no-miya.html): the page's whole shader runs on one big quad standing in front
// of him (souls_moon), and this class does what the page's script did every frame: it lays out the chains of moons from
// the slash's seed (the same random numbers as the page) and works out where each moon, sparkle and the slash's point
// and tail are at this moment. Those go to the shader in a small float texture (3 rows per slash, see souls_moon.fsh).
// One pass: premultiplied see-through color, with the light drawn as a halo under the body (no bloom), so it looks the
// same on a bright sky, at night or in a cave.
public final class MoonSlashRenderer {
    private static final int NC = 40, NS = 28, MAX = 8, TEX_W = 40, TEX_H = MAX * 3;
    // The page's timeline (seconds).
    private static final double T_START = 0.10, T_SWEEP = 0.30, END = 2.35;
    private static final double A0 = Math.toRadians(200), SPAN = Math.toRadians(-265);
    private static final double CX = YoruNoMiya.CX, CY = YoruNoMiya.CY, R = YoruNoMiya.R;
    // The quad covers the page's p = (CX, CY) +- HALF (as souls_moon.fsh).
    private static final double HALF = 1.2;

    private record Moon(double u, double x, double y, double r, double rot, double spin, double thin, double cut, double out, double delay,
            double die) {
    }

    private record Spark(double u, double off, double z, double life, double delay) {
    }

    private record Slash(Vec3 centre, Vec3 right, Vec3 up, double born, List<Moon> moons, List<Spark> sparks) {
    }

    private static final List<Slash> SLASHES = new ArrayList<>();
    private static int texture = -1;
    private static FloatBuffer data;

    private MoonSlashRenderer() {
    }

    public static void receive(Vec3 eye, Vec3 look, int seed) {
        Vec3[] fr = YoruNoMiya.frame(eye, look);
        List<Moon> moons = new ArrayList<>();
        List<Spark> sparks = new ArrayList<>();
        build(seed, moons, sparks);
        SLASHES.add(new Slash(fr[0], fr[2], fr[3], ScreenFx.now(), moons, sparks));
        opening(fr[0]);
        while (SLASHES.size() > MAX) {
            SLASHES.remove(0);
        }
    }

    // Impact (Stark: "tá muito seco"): the cut opens with an impact frame in the moon's gold, a shake and a short radial
    // blur, for everyone close enough to see it well.
    private static final int GOLD = 0xFCB040;
    private static final double IMPACT_VIEW = 40;

    private static boolean near(Vec3 pos) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && mc.player.position().distanceTo(pos) < IMPACT_VIEW;
    }

    private static void opening(Vec3 centre) {
        if (!near(centre)) {
            return;
        }
        ScreenFx.impactFrame(new int[] {1, 4, 2}, 1, GOLD, centre, 3);
        ScreenFx.radialBlur(centre, 0.35f, 8);
        com.starkinhon.battlegroundsmod.client.ScreenShake.start(0.7f, 10);
    }

    // The blow landing on someone 0.5 s after the cut (Black Flash style): a second impact frame there and a harder shake.
    public static void hit(Vec3 pos) {
        if (!near(pos)) {
            return;
        }
        ScreenFx.impactFrame(new int[] {2, 4}, 1, GOLD, pos, 0);
        ScreenFx.flash(0xFFE2A0, 0.25f, 5);
        com.starkinhon.battlegroundsmod.client.ScreenShake.start(1.0f, 12);
    }

    public static boolean active() {
        return !SLASHES.isEmpty();
    }

    public static void clear() {
        SLASHES.clear();
    }

    // ---------- the page's script ----------

    private static final class Rng {
        private int seed;

        Rng(int seed) {
            this.seed = seed;
        }

        // mulberry32, as on the page
        double next() {
            seed += 0x6D2B79F5;
            int t = (seed ^ seed >>> 15) * (1 | seed);
            t = t + (t ^ t >>> 7) * (61 | t) ^ t;
            return ((t ^ t >>> 14) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    private static double angleAt(double u) {
        return A0 + SPAN * u;
    }

    private static double radiusAt(double u) {
        return R * (1 + 0.07 * u);
    }

    private static double[] onArc(double u, double off) {
        double a = angleAt(u), rr = radiusAt(u) + off;
        return new double[] {CX + Math.cos(a) * rr, CY + Math.sin(a) * rr};
    }

    private static void build(int seed, List<Moon> moons, List<Spark> sparks) {
        Rng rnd = new Rng(seed);
        int chains = 13;
        for (int c = 0; c < chains && moons.size() < NC; c++) {
            double u = (c + 0.25 + rnd.next() * 0.5) / chains * 0.96 + 0.02;
            double sign = rnd.next() < 0.62 ? 1 : -1;
            double off = sign * (0.035 + rnd.next() * 0.10);
            double size = 0.06 + Math.pow(rnd.next(), 1.3) * 0.11;
            int n = 2 + (int) Math.floor(rnd.next() * 2.4);
            double du = 0;
            for (int k = 0; k < n && moons.size() < NC; k++) {
                double uu = Math.min(0.99, u + du);
                double[] xy = onArc(uu, off);
                double tang = angleAt(uu) - Math.PI / 2;
                double rot = tang + (rnd.next() * 2 - 1) * 2.4;
                double spin = (rnd.next() * 2 - 1) * 0.6;
                double thin = 0.42 + rnd.next() * 0.22;
                double cut = 0.97 + rnd.next() * 0.06;
                double out = (rnd.next() * 0.5 + 0.5) * Math.signum(off);
                double delay = rnd.next() * 0.04;
                double die = 1.05 + uu * 0.5 + rnd.next() * 0.18;
                moons.add(new Moon(uu, xy[0], xy[1], size, rot, spin, thin, cut, out, delay, die));
                du += size * 1.25 / R * 0.5 + rnd.next() * 0.02;
                off += (rnd.next() * 2 - 1) * size * 0.9;
                size *= 0.62 + rnd.next() * 0.18;
            }
        }
        for (int i = 0; i < NS; i++) {
            double u = rnd.next() * 0.98 + 0.01;
            double off = (rnd.next() * 2 - 1) * 0.16;
            double z = 0.006 + rnd.next() * 0.012;
            double life = 0.25 + rnd.next() * 0.45;
            double delay = rnd.next() * 0.12;
            sparks.add(new Spark(u, off, z, life, delay));
        }
    }

    private static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }

    private static double outCubic(double x) {
        return 1 - Math.pow(1 - x, 3);
    }

    private static double inOut(double x) {
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    private static double outBack(double x) {
        double c1 = 2.2, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    private static double head(double t) {
        return outCubic(clamp01((t - T_START) / T_SWEEP));
    }

    private static double headInv(double u) {
        return T_START + T_SWEEP * (1 - Math.cbrt(1 - Math.min(u, 0.9999)));
    }

    private static double tail(double t) {
        return inOut(clamp01((t - 0.95) / 0.85));
    }

    // Rows 3k..3k+2 of the data texture for slash k at time t (seconds).
    private static void fill(int k, Slash s, double t) {
        int row0 = k * 3 * TEX_W * 4;
        int row1 = row0 + TEX_W * 4;
        int row2 = row1 + TEX_W * 4;
        for (int i = 0; i < NC; i++) {
            int a = row0 + i * 4, b = row1 + i * 4;
            Moon m = i < s.moons().size() ? s.moons().get(i) : null;
            double life = m == null ? -1 : t - (headInv(m.u()) + m.delay());
            if (m == null || life <= 0) {
                put(a, 0, 0, 0, 0);
                put(b, 0, 0, 0, 0);
                continue;
            }
            double pop = outBack(clamp01(life / 0.16));
            double diss = clamp01((t - m.die()) / 0.45);
            double ang = angleAt(m.u());
            double drift = outCubic(clamp01(life / 1.3)) * 0.045 * m.out();
            double side = 0.012 * outCubic(clamp01(life));
            double x = m.x() + Math.cos(ang) * drift + Math.cos(ang - Math.PI / 2) * side;
            double y = m.y() + Math.sin(ang) * drift + Math.sin(ang - Math.PI / 2) * side;
            put(a, x, y, m.r() * pop * (1 + diss * 0.12), m.rot() + m.spin() * outCubic(clamp01(life / 1.6)));
            put(b, m.thin(), 1, diss, m.cut());
        }
        for (int i = 0; i < NS; i++) {
            Spark sp = s.sparks().get(i);
            double q = (t - (headInv(sp.u()) + sp.delay())) / sp.life();
            double[] xy = onArc(sp.u(), sp.off() + 0.05 * clamp01(q));
            put(row2 + i * 4, xy[0], xy[1], sp.z(), q > 0 && q < 1 ? Math.sin(Math.PI * q) * (1 - q * 0.4) : 0);
        }
        double bandA = 1 - clamp01((t - 1.55) / 0.35);
        double glint = clamp01(t / 0.05) * (1 - clamp01((t - T_START - 0.03) / 0.08));
        put(row2 + 28 * 4, head(t), tail(t), bandA, glint);
    }

    private static void put(int at, double x, double y, double z, double w) {
        data.put(at, (float) x).put(at + 1, (float) y).put(at + 2, (float) z).put(at + 3, (float) w);
    }

    private static void upload() {
        if (texture < 0) {
            texture = TextureUtil.generateTextureId();
            data = MemoryUtil.memAllocFloat(TEX_W * TEX_H * 4);
            GlStateManager._bindTexture(texture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, TEX_W, TEX_H, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
        }
        GlStateManager._bindTexture(texture);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        data.position(0);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, TEX_W, TEX_H, GL11.GL_RGBA, GL11.GL_FLOAT, data);
    }

    // ---------- drawing ----------

    public static void render(MultiBufferSource source, Vec3 camera, double now, float partial) {
        SLASHES.removeIf(s -> (now - s.born()) / 20.0 > END);
        if (SLASHES.isEmpty() || FxShaders.moon == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (texture < 0) {
            upload();
        }
        for (int k = 0; k < SLASHES.size(); k++) {
            fill(k, SLASHES.get(k), Math.max(0, (now - SLASHES.get(k).born()) / 20.0));
        }
        upload();
        // One pass only: the light is a see-through halo inside the body (souls_moon.fsh), not bloom, so the color stays
        // the page's on a bright sky too.
        for (int k = 0; k < SLASHES.size(); k++) {
            Slash s = SLASHES.get(k);
            int ms = (int) Math.min(30000, Math.max(0, (now - s.born()) / 20.0 * 1000));
            quad(source.getBuffer(Types.BODY), s, camera, ms, k, 1);
        }
    }

    private static void quad(VertexConsumer out, Slash s, Vec3 camera, int ms, int k, float pass) {
        double h = HALF * YoruNoMiya.SCALE;
        Vec3 c = s.centre().subtract(camera);
        Vec3 r = s.right().scale(h), u = s.up().scale(h);
        vertex(out, c.subtract(r).subtract(u), 0, 0, ms, k, pass);
        vertex(out, c.add(r).subtract(u), 1, 0, ms, k, pass);
        vertex(out, c.add(r).add(u), 1, 1, ms, k, pass);
        vertex(out, c.subtract(r).add(u), 0, 1, ms, k, pass);
    }

    private static void vertex(VertexConsumer out, Vec3 p, float uu, float vv, int ms, int k, float pass) {
        out.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(uu, vv).setColor(pass, 1f, 1f, 1f).setUv2(ms, k);
    }

    // The two passes' render types: souls_moon reading the data texture. The body is premultiplied (its color already
    // carries its own coverage), the glow is added.
    static final class Types extends RenderType {
        private static final ShaderStateShard SHADER = new ShaderStateShard(() -> FxShaders.moon);
        private static final EmptyTextureStateShard DATA = new EmptyTextureStateShard(() -> {
            if (texture >= 0) {
                RenderSystem.setShaderTexture(0, texture);
            }
        }, () -> {
        });
        private static final TransparencyStateShard PREMULTIPLIED = new TransparencyStateShard("battlegroundsmod_premultiplied", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });
        static final RenderType BODY = create("battlegroundsmod_moon_body", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(DATA)
                        .setTransparencyState(PREMULTIPLIED)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType GLOW = create("battlegroundsmod_moon_glow", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(DATA)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
