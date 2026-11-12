package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

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

import net.minecraft.client.renderer.ShaderInstance;

// Rukia's and Byakuya's 3D effects, drawn exactly the way the approved pages draw them (forja/bleach: rukia-remake,
// byakuya-remake). The shader is the pages' own text (souls_bleach, made by forja/bleach/mod-shaders.sh), the unit meshes
// here are built the same way as lib.js builds them (loose triangles, same counts, same order), and each piece is drawn
// like the pages' piece(): back faces at its order, front faces at order + 0.5, premultiplied, behind blocks, then again
// into the glow layer (the bloom) unless it is marked glow = false. BleachFx queues the pieces while it runs the skills'
// timelines (the pages' update functions, line by line).
public final class BleachMesh {
    // The pages' GEO table.
    public enum Mesh {
        SWARM, BLADES, SPH, SPH_LO, HALO, TUBE, TUBE_HI, CRY, GRID, GRID_LO;

        VertexBuffer vbo;
    }

    // The pages' K table (bleach_common.glsl).
    public static final int FROST = 0, CRYSTAL = 1, PILLAR = 2, LIGHT = 3, MIST = 4, SHARD = 5, BLIZ = 6, KIDO = 7, FIRE = 8, PETAL = 9, HALO = 10,
            ICE = 11, FLASH = 12, STAKE = 13, BURST = 14, SWARM = 15, BLADES = 16, SLASH = 17, BOLT = 18, WALL = 19, RIPPLE = 20, WING = 21,
            TINT = 22;
    public static final int FRONT = 1, BACK = 2, BOTH = 3;

    // One piece: what the pages keep in its uniforms. queue() copies it, so a Piece can be reused.
    public static final class Piece {
        public int kind;
        public float life, seed, alpha = 1;
        public final float[] a = new float[4], b = new float[4];
        public float[] color = {1, 1, 1}, color2 = {0.533f, 0.6f, 0.667f};
        public final float[] anchor = new float[3];
        public int sides = BOTH, order = 10;
        public boolean glow = true, solid;

        public Piece a(float x, float y, float z, float w) {
            a[0] = x; a[1] = y; a[2] = z; a[3] = w;
            return this;
        }

        public Piece b(float x, float y, float z, float w) {
            b[0] = x; b[1] = y; b[2] = z; b[3] = w;
            return this;
        }

        Piece copy() {
            Piece p = new Piece();
            p.kind = kind; p.life = life; p.seed = seed; p.alpha = alpha;
            System.arraycopy(a, 0, p.a, 0, 4);
            System.arraycopy(b, 0, p.b, 0, 4);
            System.arraycopy(anchor, 0, p.anchor, 0, 3);
            p.color = color.clone(); p.color2 = color2.clone(); p.sides = sides; p.order = order; p.glow = glow; p.solid = solid;
            return p;
        }
    }

    private record Cmd(Mesh mesh, VertexBuffer own, Matrix4f model, Piece p, boolean back, float key, float dist) {
    }

    private static final List<Cmd> QUEUE = new ArrayList<>();
    private static boolean broken;
    private static float time;

    private BleachMesh() {
    }

    public static boolean ready() {
        return FxShaders.bleach != null && !broken && !FxShaders.flat();
    }

    // The pages' NOWT.t: seconds, the same clock for every piece of the frame.
    public static void time(float seconds) {
        time = seconds;
    }

    public static Piece piece(int kind) {
        Piece p = new Piece();
        p.kind = kind;
        return p;
    }

    // Queues a piece on a unit mesh; model places it relative to the camera (the pages' g.matrixWorld).
    public static void queue(Mesh mesh, Matrix4f model, Piece p) {
        add(mesh, null, model, p);
    }

    // Queues a piece on a mesh of its own (a body's skin for the ice shell), already relative to the camera.
    public static void queue(VertexBuffer own, Piece p) {
        add(null, own, new Matrix4f(), p);
    }

    private static void add(Mesh mesh, VertexBuffer own, Matrix4f model, Piece p) {
        if (!ready() || p.alpha <= 0.002f) {
            return;
        }
        Vector3f c = model.getTranslation(new Vector3f());
        float dist = c.length();
        Piece q = p.copy();
        // A round piece wrapping the camera (a mist, a halo, a flash over a third-person camera): the pages never put
        // the camera inside one, here it would paint the whole screen, so it fades out as the camera gets in.
        if ((mesh == Mesh.SPH || mesh == Mesh.HALO) && (q.kind == MIST || q.kind == HALO || q.kind == FLASH || q.kind == BURST)) {
            // How far the camera is in the piece's own unit space (its scale may be flat, like the Bankai's dome): only
            // when it is really inside does the piece fade, never a dome the camera sees from outside.
            Vector3f local = new Matrix4f(model).invert().transformPosition(new Vector3f());
            float edge = q.kind == MIST || q.kind == BURST ? 1.25f : 1.05f;
            float k = Math.max(0, Math.min(1, (local.length() / edge - 0.8f) / 0.4f));
            q.alpha *= k * k * (3 - 2 * k);
            if (q.alpha <= 0.002f) {
                return;
            }
        }
        Matrix4f m = new Matrix4f(model);
        if ((q.sides & BACK) != 0) {
            QUEUE.add(new Cmd(mesh, own, m, q, true, q.order, dist));
        }
        if ((q.sides & FRONT) != 0) {
            QUEUE.add(new Cmd(mesh, own, m, q, false, q.order + 0.5f, dist));
        }
    }

    public static boolean pending() {
        return !QUEUE.isEmpty();
    }

    // glow false: into the frame; true: into the glow layer (between FxPost.beginGlow and endGlow).
    public static void flush(boolean glow) {
        if (QUEUE.isEmpty() || !ready()) {
            return;
        }
        ShaderInstance shader = FxShaders.bleach;
        try {
            build();
            // three.js: renderOrder first, then far before near.
            QUEUE.sort((x, y) -> x.key() != y.key() ? Float.compare(x.key(), y.key()) : Float.compare(y.dist(), x.dist()));
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.enableCull();
            Matrix4f view = RenderSystem.getModelViewMatrix(), proj = RenderSystem.getProjectionMatrix();
            for (Cmd c : QUEUE) {
                if (glow && !c.p().glow) {
                    continue;
                }
                set(shader, c, glow);
                VertexBuffer vbo = c.own() != null ? c.own() : c.mesh().vbo;
                RenderSystem.depthMask(c.p().solid && !glow);
                GL11.glCullFace(c.back() ? GL11.GL_FRONT : GL11.GL_BACK);
                vbo.bind();
                vbo.drawWithShader(view, proj, shader);
                VertexBuffer.unbind();
            }
        } catch (RuntimeException e) {
            broken = true;
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Bleach 3D effects failed; they are off until restart", e);
        } finally {
            GL11.glCullFace(GL11.GL_BACK);
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    public static void endFrame() {
        QUEUE.clear();
    }

    private static void set(ShaderInstance s, Cmd c, boolean glow) {
        Piece p = c.p();
        s.safeGetUniform("Model").set(c.model());
        s.safeGetUniform("Kind").set((float) p.kind);
        s.safeGetUniform("Life").set(p.life);
        s.safeGetUniform("Seed").set(p.seed);
        s.safeGetUniform("Time").set(time);
        s.safeGetUniform("Alpha").set(p.alpha);
        s.safeGetUniform("GlowPass").set(glow ? 1f : 0f);
        s.safeGetUniform("A").set(p.a[0], p.a[1], p.a[2], p.a[3]);
        s.safeGetUniform("B").set(p.b[0], p.b[1], p.b[2], p.b[3]);
        s.safeGetUniform("Color").set(p.color[0], p.color[1], p.color[2]);
        s.safeGetUniform("Color2").set(p.color2[0], p.color2[1], p.color2[2]);
        s.safeGetUniform("Anchor").set(p.anchor[0], p.anchor[1], p.anchor[2]);
    }

    // ---------- the unit meshes (lib.js: geoSphere, geoTube, geoGrid, geoSwarm, geoBlades), built once ----------

    private static void build() {
        for (Mesh m : Mesh.values()) {
            if (m.vbo != null) {
                continue;
            }
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            switch (m) {
                case SWARM -> swarm(b, 1500);
                case BLADES -> blades(b, 132, 12);
                case SPH -> sphere(b, 64, 48);
                case SPH_LO -> sphere(b, 6, 4);
                case HALO -> sphere(b, 32, 24);
                case TUBE -> tube(b, 40, 48);
                case TUBE_HI -> tube(b, 48, 120);
                case CRY -> tube(b, 6, 5);
                case GRID -> grid(b, 64);
                case GRID_LO -> grid(b, 16);
            }
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(b.buildOrThrow());
            VertexBuffer.unbind();
            m.vbo = vbo;
        }
    }

    private static void sphere(BufferBuilder b, int w, int h) {
        for (int j = 0; j < h; j++) {
            double t0 = Math.PI * j / h, t1 = Math.PI * (j + 1) / h;
            for (int i = 0; i < w; i++) {
                double p0 = 2 * Math.PI * i / w, p1 = 2 * Math.PI * (i + 1) / w;
                double[] a = sp(t0, p0), c = sp(t1, p0), d = sp(t1, p1), e = sp(t0, p1);
                v(b, a); v(b, e); v(b, c);
                v(b, e); v(b, d); v(b, c);
            }
        }
    }

    private static double[] sp(double t, double p) {
        return new double[] {-Math.cos(p) * Math.sin(t), Math.cos(t), Math.sin(p) * Math.sin(t)};
    }

    private static void tube(BufferBuilder b, int w, int h) {
        for (int j = 0; j < h; j++) {
            double y0 = (double) j / h, y1 = (double) (j + 1) / h;
            for (int i = 0; i < w; i++) {
                double a0 = 2 * Math.PI * i / w, a1 = 2 * Math.PI * (i + 1) / w;
                double[] p = {Math.sin(a0), y0, Math.cos(a0)}, q = {Math.sin(a1), y0, Math.cos(a1)};
                double[] r = {Math.sin(a1), y1, Math.cos(a1)}, s = {Math.sin(a0), y1, Math.cos(a0)};
                v(b, p); v(b, q); v(b, r);
                v(b, p); v(b, r); v(b, s);
            }
        }
    }

    private static void grid(BufferBuilder b, int n) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double x0 = -1 + 2.0 * i / n, x1 = -1 + 2.0 * (i + 1) / n, z0 = -1 + 2.0 * j / n, z1 = -1 + 2.0 * (j + 1) / n;
                v(b, x0, 0, z0); v(b, x1, 0, z1); v(b, x1, 0, z0);
                v(b, x0, 0, z0); v(b, x0, 0, z1); v(b, x1, 0, z1);
            }
        }
    }

    // Position = (petal id, corner 0..3, 0).
    private static void swarm(BufferBuilder b, int n) {
        int[] c = {0, 1, 2, 0, 2, 3};
        for (int i = 0; i < n; i++) {
            for (int k : c) {
                v(b, i, k, 0);
            }
        }
    }

    // Position = (blade id, u -1..1 across, v 0..1 along).
    private static void blades(BufferBuilder b, int n, int vs) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < vs; j++) {
                double v0 = (double) j / vs, v1 = (double) (j + 1) / vs;
                for (int a = -1; a < 1; a++) {
                    double u0 = a, u1 = a + 1;
                    v(b, i, u0, v0); v(b, i, u1, v0); v(b, i, u1, v1);
                    v(b, i, u0, v0); v(b, i, u1, v1); v(b, i, u0, v1);
                }
            }
        }
    }

    private static void v(BufferBuilder b, double[] p) {
        b.addVertex((float) p[0], (float) p[1], (float) p[2]);
    }

    private static void v(BufferBuilder b, double x, double y, double z) {
        b.addVertex((float) x, (float) y, (float) z);
    }
}
