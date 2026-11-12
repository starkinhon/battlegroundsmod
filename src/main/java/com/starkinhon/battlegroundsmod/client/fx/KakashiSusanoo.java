package com.starkinhon.battlegroundsmod.client.fx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.KakashiRig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

// Kakashi's Perfect Susanoo, drawn the way the approved page draws it (forja/kakashi-kit/kakashi.js v13): the page's model
// baked piece by piece (models/mesh/kakashi_susanoo.bin, made by forja/kakashi-kit/bake-susanoo.js from the page itself),
// each piece in its own buffer so the see-through pieces can be sorted far to near like three.js does, and the page's
// shaders (souls_kk_sus, souls_kk_mist). The draw order is the page's renderOrder: the flat black/red bits, the wing
// feathers (11), the ink outlines (12), the body (13), the chakra flames (15), the mist (16). The Kamui shurikens use the
// same shader with their own colors.
public final class KakashiSusanoo {
    private static final class Piece {
        int mat, bone, flags;
        float outW;
        Matrix4f inv;
        Vector3f center;
        VertexBuffer vbo;
    }

    // What the server last said about one Susanoo (SubNet.KK_SUSANOO).
    private static final class State {
        Vec3 feet;
        float yaw, armK, throwK, rise, alpha;
        double born, updated, endAt = -1;
    }

    private static final class Shuriken {
        Vec3 pos, prev, dir;
        float size, alpha;
        double born, updated, endAt = -1;
    }

    private static final Map<Integer, State> SUS = new HashMap<>();
    // The camera pull-back of each caster: {born, ended (-1 not yet), last update}; outlives SUS by the ease out.
    private static final Map<Integer, double[]> CAM = new HashMap<>();
    private static final Map<Integer, Shuriken> SHURIKENS = new HashMap<>();
    private static final List<Piece> PIECES = new ArrayList<>();
    private static Piece shurikenMesh;
    private static VertexBuffer cube;
    private static boolean broken, loaded;

    // The page's colors (makeSusanoo U and the per-material overrides).
    private static final float[] BASE = rgb(0x46c6ea), SHADE = rgb(0x2a9fd0), DEEP = rgb(0x1a72a8), INK = rgb(0x0a2638), GLOW = rgb(0xc8f4ff);
    private static final float[] HAIR_B = rgb(0x38b2dc), HAIR_S = rgb(0x2389bd), HAIR_D = rgb(0x165c92), HAIR_G = rgb(0x8fdcf6);
    private static final float[] GEM_B = rgb(0x7fd6f6), GEM_S = rgb(0x5cc0e8), GEM_D = rgb(0x44a8d8);
    private static final float[] DARK = rgb(0x081c2a), EYE = rgb(0xff1a14), FIG = rgb(0x16305e);
    private static final float[] SH_CORE = rgb(0xe6fbff), SH_BODY = rgb(0x4fc4ff), SH_EDGE = rgb(0x1446c8);

    private KakashiSusanoo() {
    }

    private static float[] rgb(int c) {
        return new float[] {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    // ---------- from the server ----------

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, double now) {
        if (kind == com.starkinhon.battlegroundsmod.power.powers.sub.SubNet.KK_SUSANOO) {
            State s = SUS.get(id);
            if (s == null || s.endAt >= 0) {
                s = new State();
                s.born = now;
                SUS.put(id, s);
            }
            double[] cam = CAM.get(id);
            if (cam == null || cam[1] >= 0 && s.born == now) {
                cam = new double[] {now, -1, now};
                CAM.put(id, cam);
            }
            cam[2] = now;
            if (c >= 1) {
                cam[1] = now;
            }
            s.feet = pos;
            s.yaw = (float) dir.x;
            s.armK = (float) dir.y;
            s.throwK = (float) dir.z;
            s.rise = a;
            s.alpha = b;
            s.updated = now;
            if (c >= 1) {
                s.endAt = now;
            }
        } else {
            Shuriken s = SHURIKENS.get(id);
            if (s == null || s.endAt >= 0) {
                s = new Shuriken();
                s.born = now;
                s.prev = pos;
                SHURIKENS.put(id, s);
            } else {
                s.prev = s.pos;
            }
            s.pos = pos;
            s.dir = dir;
            s.size = a;
            s.alpha = b;
            s.updated = now;
            if (c >= 1) {
                s.endAt = now;
            }
        }
    }

    // Third person inside his own Susanoo: the camera is pulled back (12 blocks more, eased in and out) so the giant is
    // seen from outside instead of filling the screen from within. Null when the viewer has no Susanoo.
    public static Vec3 pullBack(int viewerId, Vec3 camPos, Vector3f look, float partial) {
        double[] c = CAM.get(viewerId);
        if (c == null) {
            return null;
        }
        double now = ScreenFx.now() + partial;
        State s = SUS.get(viewerId);
        double endAt = s != null && s.endAt < 0 && now - s.updated <= 6 ? -1 : c[1] >= 0 ? c[1] : c[2];
        float k = smooth((float) (now - c[0]) / 18f);
        if (endAt >= 0) {
            k *= 1 - smooth((float) (now - endAt) / 18f);
            if (now - endAt > 18) {
                CAM.remove(viewerId);
                return null;
            }
        }
        if (k <= 0.001f) {
            return null;
        }
        double d = 12 * k;
        return camPos.add(-look.x() * d, -look.y() * d + 2.5 * k, -look.z() * d);
    }

    static boolean active() {
        return !SUS.isEmpty() || !SHURIKENS.isEmpty();
    }

    static void clear() {
        SUS.clear();
        CAM.clear();
        SHURIKENS.clear();
    }

    private static void prune(double now) {
        Iterator<State> it = SUS.values().iterator();
        while (it.hasNext()) {
            State s = it.next();
            if (s.endAt < 0 && now - s.updated > 6) {
                s.endAt = s.updated;
            }
            if (s.endAt >= 0 && now - s.endAt > 4) {
                it.remove();
            }
        }
        Iterator<Shuriken> is = SHURIKENS.values().iterator();
        while (is.hasNext()) {
            Shuriken s = is.next();
            if (s.endAt < 0 && now - s.updated > 4) {
                s.endAt = s.updated;
            }
            if (s.endAt >= 0 && now - s.endAt > 2) {
                is.remove();
            }
        }
    }

    // ---------- the model ----------

    private static boolean ready() {
        if (broken || FxShaders.kkSus == null) {
            return false;
        }
        if (!loaded) {
            loaded = true;
            try {
                load();
            } catch (RuntimeException | IOException e) {
                broken = true;
                BattlegroundsMod.LOGGER.error("Kakashi's Susanoo model failed to load", e);
                return false;
            }
        }
        return !PIECES.isEmpty();
    }

    // models/mesh/kakashi_susanoo.bin (big-endian, see bake-susanoo.js): int pieces, 12 floats of pivots (KakashiRig has
    // them); per piece int mat, bone, flags, float outline width, 16 floats inverse of its own matrix, 3 floats center,
    // int vertices, each 3 shorts position (1/1000), 2 shorts uv (1/25000), 3 bytes normal (1/127), then int indices as
    // shorts (ints past 65535 vertices).
    private static void load() throws IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "models/mesh/kakashi_susanoo.bin");
        ByteBuffer buf;
        try (InputStream raw = Minecraft.getInstance().getResourceManager().open(id)) {
            buf = ByteBuffer.wrap(raw.readAllBytes()).order(ByteOrder.BIG_ENDIAN);
        }
        int n = buf.getInt();
        for (int i = 0; i < 12; i++) {
            buf.getFloat();
        }
        for (int p = 0; p < n; p++) {
            Piece piece = new Piece();
            piece.mat = buf.getInt();
            piece.bone = buf.getInt();
            piece.flags = buf.getInt();
            piece.outW = buf.getFloat();
            float[] m = new float[16];
            for (int i = 0; i < 16; i++) {
                m[i] = buf.getFloat();
            }
            piece.inv = new Matrix4f().set(m);
            piece.center = new Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat());
            int nv = buf.getInt();
            float[] v = new float[nv * 8];
            for (int i = 0; i < nv; i++) {
                v[i * 8] = buf.getShort() / 1000f;
                v[i * 8 + 1] = buf.getShort() / 1000f;
                v[i * 8 + 2] = buf.getShort() / 1000f;
                v[i * 8 + 3] = buf.getShort() / 25000f;
                v[i * 8 + 4] = buf.getShort() / 25000f;
                v[i * 8 + 5] = buf.get() / 127f;
                v[i * 8 + 6] = buf.get() / 127f;
                v[i * 8 + 7] = buf.get() / 127f;
            }
            int ni = buf.getInt();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            for (int i = 0; i < ni; i++) {
                int k = nv > 65535 ? buf.getInt() : buf.getShort() & 0xFFFF;
                int o = k * 8;
                b.addVertex(v[o], v[o + 1], v[o + 2]).setUv(v[o + 3], v[o + 4]).setColor(255, 255, 255, 255).setNormal(v[o + 5], v[o + 6], v[o + 7]);
            }
            piece.vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            piece.vbo.bind();
            piece.vbo.upload(b.buildOrThrow());
            VertexBuffer.unbind();
            if (piece.mat == 9) {
                shurikenMesh = piece;
            } else {
                PIECES.add(piece);
            }
        }
        // A cube from -1 to 1 for the mist's box.
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        float[][] c = {{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}};
        int[][] f = {{0, 2, 1}, {0, 3, 2}, {4, 5, 6}, {4, 6, 7}, {0, 1, 5}, {0, 5, 4}, {3, 6, 2}, {3, 7, 6}, {0, 4, 7}, {0, 7, 3}, {1, 2, 6}, {1, 6, 5}};
        for (int[] t : f) {
            for (int k : t) {
                b.addVertex(c[k][0], c[k][1], c[k][2]);
            }
        }
        cube = new VertexBuffer(VertexBuffer.Usage.STATIC);
        cube.bind();
        cube.upload(b.buildOrThrow());
        VertexBuffer.unbind();
    }

    // ---------- drawing (FxWorld, after KiMesh: once into the frame, once into the glow layer) ----------

    public static void flush(Vec3 camera, double now, boolean glow) {
        prune(now);
        if (!active() || !ready()) {
            return;
        }
        ShaderInstance sh = FxShaders.kkSus;
        Matrix4f view = RenderSystem.getModelViewMatrix(), proj = RenderSystem.getProjectionMatrix();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            set(sh, "GlowPass", glow ? 1 : 0);
            for (State s : SUS.values()) {
                float out = s.endAt < 0 ? 1 : 1 - smooth((float) (now - s.endAt) / 4f);
                if (out <= 0) {
                    continue;
                }
                drawSusanoo(sh, s, camera, now, glow, out, view, proj);
            }
            for (Shuriken s : SHURIKENS.values()) {
                float out = s.endAt < 0 ? 1 : 1 - smooth((float) (now - s.endAt) / 2f);
                if (out > 0 && shurikenMesh != null) {
                    drawShuriken(sh, s, camera, now, glow, out, view, proj);
                }
            }
        } catch (RuntimeException e) {
            broken = true;
            BattlegroundsMod.LOGGER.error("Kakashi's Susanoo failed to draw; it is off until restart", e);
        } finally {
            GL11.glCullFace(GL11.GL_BACK);
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void drawSusanoo(ShaderInstance sh, State s, Vec3 camera, double now, boolean glow, float out, Matrix4f view, Matrix4f proj) {
        Vector3f rel = s.feet.subtract(camera).toVector3f();
        Matrix4f model = new Matrix4f().translation(rel).rotateY((float) Math.PI - s.yaw).scale(KakashiRig.BLOCK);
        Matrix4f inv = new Matrix4f(model).invert();
        Vector3f camPage = inv.transformPosition(new Vector3f());
        Matrix4f[] left = KakashiRig.bones(0, s.armK, s.throwK), right = KakashiRig.bones(1, s.armK, s.throwK);
        Matrix4f id = new Matrix4f();
        float time = (float) ((now - s.born) / 20.0);
        float th = (float) Math.PI - s.yaw;
        Vector3f light = new Vector3f(-0.4f, 0.7f, -0.65f).normalize().rotateY(th);
        set(sh, "Model", model);
        set(sh, "Time", time);
        set(sh, "Rise", s.rise);
        set(sh, "H", 28);
        set(sh, "A", s.alpha * out);
        set(sh, "FadeY", 1.4f);
        set(sh, "FadeW", 7.5f);
        set(sh, "ObjScale", KakashiRig.SUS_SCALE);
        set3(sh, "LightDir", light.x, light.y, light.z);
        set3(sh, "Ink", INK);
        // far pieces first (three.js sorts its see-through objects by distance)
        List<Piece> order = new ArrayList<>(PIECES);
        Map<Piece, Float> dist = new HashMap<>();
        for (Piece p : order) {
            Matrix4f bone = bone(p.bone, left, right, id);
            Vector3f c = bone.transformPosition(new Vector3f(p.center)).mul(KakashiRig.SUS_SCALE);
            dist.put(p, c.distanceSquared(camPage));
        }
        order.sort((x, y) -> Float.compare(dist.get(y), dist.get(x)));
        // 0: the flat black/red bits, solid
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        GL11.glCullFace(GL11.GL_BACK);
        if (!glow) {
            for (Piece p : order) {
                if (p.mat >= 6 && p.mat <= 8) {
                    material(sh, p, 0, left, right, id);
                    draw(sh, p.vbo, view, proj);
                }
            }
        }
        RenderSystem.enableBlend();
        premul();
        // 1: feathers, 2: outlines (back faces pushed out), 3: body. The page writes depth for all three.
        RenderSystem.depthMask(!glow);
        for (int pass = 1; pass <= 3; pass++) {
            GL11.glCullFace(pass == 2 ? GL11.GL_FRONT : GL11.GL_BACK);
            if (pass == 2 && glow) {
                continue;
            }
            for (Piece p : order) {
                boolean take = switch (pass) {
                    case 1 -> p.mat == 2;
                    case 2 -> (p.flags & 1) != 0;
                    default -> p.mat != 2 && (p.mat < 6 || p.mat > 8);
                };
                if (take) {
                    material(sh, p, pass, left, right, id);
                    draw(sh, p.vbo, view, proj);
                }
            }
        }
        // 4: the flames, added, no depth
        RenderSystem.depthMask(false);
        GL11.glCullFace(GL11.GL_BACK);
        for (Piece p : order) {
            if ((p.flags & 2) != 0) {
                material(sh, p, 4, left, right, id);
                boolean feather = (p.flags & 4) != 0;
                set(sh, "Gain", feather ? 0.45f : 1f);
                set(sh, "Push", feather ? 0.06f : 0.12f);
                draw(sh, p.vbo, view, proj);
            }
        }
        // 5: the mist on the ground (the page's box 26 x 10 x 26 round the feet)
        if (!glow && FxShaders.kkMist != null) {
            ShaderInstance m = FxShaders.kkMist;
            set(m, "Model", model);
            set3(m, "BoxMin", -13, 0, -12);
            set3(m, "BoxMax", 13, 10, 14);
            set3(m, "CamObj", camPage.x, camPage.y, camPage.z);
            set3(m, "MistCol", rgb(0x1f6fa8));
            set3(m, "MistGlow", rgb(0x8fe0ff));
            set(m, "Time", time);
            set(m, "A", s.alpha * out);
            set(m, "Rise", s.rise);
            set(m, "GlowPass", 0);
            GL11.glCullFace(GL11.GL_FRONT);
            draw(m, cube, view, proj);
            GL11.glCullFace(GL11.GL_BACK);
        }
        RenderSystem.depthMask(true);
    }

    private static Matrix4f bone(int b, Matrix4f[] left, Matrix4f[] right, Matrix4f id) {
        return switch (b) {
            case 1 -> left[0];
            case 2 -> left[1];
            case 3 -> right[0];
            case 4 -> right[1];
            default -> id;
        };
    }

    private static void material(ShaderInstance sh, Piece p, int pass, Matrix4f[] left, Matrix4f[] right, Matrix4f id) {
        set(sh, "Pass", pass);
        set(sh, "Mat", p.mat);
        set(sh, "Bone", bone(p.bone, left, right, id));
        set(sh, "MeshInv", p.inv);
        set(sh, "OutW", p.outW);
        set(sh, "Stripe", p.mat == 1 ? 2.6f : p.mat == 2 ? -1 : 0);
        set(sh, "Flame", p.mat == 4 ? 0.22f : p.mat == 3 ? 1 : 0);
        set(sh, "Opq", p.mat == 4 ? 1 : 0);
        set(sh, "Gain", 1);
        set(sh, "Push", 0.12f);
        float[] base = BASE, shade = SHADE, deep = DEEP, glw = GLOW;
        switch (p.mat) {
            case 4 -> {
                base = HAIR_B;
                shade = HAIR_S;
                deep = HAIR_D;
                glw = HAIR_G;
            }
            case 5 -> {
                base = GEM_B;
                shade = GEM_S;
                deep = GEM_D;
            }
            case 6 -> base = DARK;
            case 7 -> base = EYE;
            case 8 -> base = FIG;
            default -> {
            }
        }
        set3(sh, "Base", base);
        set3(sh, "Shade", shade);
        set3(sh, "Deep", deep);
        set3(sh, "Glow", glw);
    }

    private static void drawShuriken(ShaderInstance sh, Shuriken s, Vec3 camera, double now, boolean glow, float out, Matrix4f view, Matrix4f proj) {
        float age = (float) (now - s.born);
        boolean flying = s.dir.lengthSqr() > 1.0E-4;
        // turning like on the page: in the hand it spins slowly, thrown it spins fast and tilts
        float spin = age / 20f * 14f * (flying ? 2.2f : 0.6f) * (s.dir.y >= 0 ? 1 : -1);
        Vector3f rel = s.pos.subtract(camera).toVector3f();
        Matrix4f model = new Matrix4f().translation(rel).rotateX(0.3f).rotateY(spin).rotateZ(0.25f).scale(s.size);
        set(sh, "Model", model);
        set(sh, "Bone", new Matrix4f());
        set(sh, "MeshInv", new Matrix4f());
        set(sh, "ObjScale", 1);
        set(sh, "Pass", 3);
        set(sh, "Mat", 9);
        set(sh, "Time", age / 20f);
        set(sh, "Spin", age / 20f * 14f);
        set(sh, "A", s.alpha * out);
        set(sh, "Flame", 0);
        set3(sh, "Base", SH_CORE);
        set3(sh, "Shade", SH_BODY);
        set3(sh, "Deep", SH_EDGE);
        RenderSystem.enableBlend();
        premul();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        draw(sh, shurikenMesh.vbo, view, proj);
        RenderSystem.enableCull();
    }

    private static void premul() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private static void draw(ShaderInstance sh, VertexBuffer vbo, Matrix4f view, Matrix4f proj) {
        vbo.bind();
        vbo.drawWithShader(view, proj, sh);
        VertexBuffer.unbind();
    }

    private static void set(ShaderInstance s, String name, float v) {
        s.safeGetUniform(name).set(v);
    }

    private static void set(ShaderInstance s, String name, Matrix4f m) {
        s.safeGetUniform(name).set(m);
    }

    private static void set3(ShaderInstance s, String name, float x, float y, float z) {
        s.safeGetUniform(name).set(x, y, z);
    }

    private static void set3(ShaderInstance s, String name, float[] c) {
        s.safeGetUniform(name).set(c[0], c[1], c[2]);
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
