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

// Saitama's serious moves, drawn the way the approved page draws them (forja/saitama-soco-serio): one unit mesh per piece
// bent in souls_opm exactly as the page's three.js shaders bend it, see-through and premultiplied, behind blocks. OpmFx
// queues what to draw; FxWorld draws the queue into the frame and into the glow layer, like KiMesh. Each piece is drawn
// back faces first, then front faces (DoubleSide), in the page's renderOrder, far before near.
public final class OpmMesh {
    public enum Mesh {
        // THREE: tubeGeo(72, 420), tubeGeo(48, 260, half), tubeGeo(72, 260), ConeGeometry(1, 1, 64, 6, true),
        // PlaneGeometry(2, 2), SphereGeometry(1, 32, 24), BoxGeometry(1, 1, 1)
        TUBE, HALF, SNEEZE, CONE, QUAD, SPHERE, BOX;

        VertexBuffer vbo;
    }

    public static final int TUBE = 0, MACH = 1, CONE = 2, DUST = 3, RING = 4, SNEEZE = 5, CLOUD = 6, MASK = 7, FLASH = 8, GHOST = 9, STAR = 10;
    public static final int FRONT = 1, BACK = 2, BOTH = 3;

    public static final class P {
        public int kind;
        public float len = 1, t, tp, seed, scale = 1, halo, core, alpha = 1, ink, life, fwd, ringR, ringMax = 1, ringW = 1, hole;
        public float[] hc = {0, 0}, colA = {1, 1, 1}, colB = {1, 1, 1};
        // false: never drawn into the glow layer
        public boolean glow = true;

        P copy() {
            P p = new P();
            p.kind = kind; p.len = len; p.t = t; p.tp = tp; p.seed = seed; p.scale = scale; p.halo = halo; p.core = core; p.alpha = alpha; p.ink = ink;
            p.life = life; p.fwd = fwd; p.ringR = ringR; p.ringMax = ringMax; p.ringW = ringW; p.hole = hole; p.hc = hc.clone(); p.colA = colA.clone();
            p.colB = colB.clone(); p.glow = glow;
            return p;
        }
    }

    private record Cmd(Mesh mesh, Matrix4f model, P p, int sides, float dist, int order) {
    }

    private static final List<Cmd> QUEUE = new ArrayList<>();
    private static boolean broken;

    private OpmMesh() {
    }

    public static P p(int kind) {
        P p = new P();
        p.kind = kind;
        return p;
    }

    public static boolean ready() {
        return FxShaders.opm != null && !broken;
    }

    public static void queue(Mesh mesh, Matrix4f model, P p, int sides, int order) {
        if (!ready() || p.alpha <= 0.002f) {
            return;
        }
        Vector3f c = model.getTranslation(new Vector3f());
        QUEUE.add(new Cmd(mesh, new Matrix4f(model), p.copy(), sides, c.length(), order));
    }

    public static boolean pending() {
        return !QUEUE.isEmpty();
    }

    // glow false: into the frame; true: into the glow layer (between FxPost.beginGlow and endGlow).
    public static void flush(boolean glow) {
        if (QUEUE.isEmpty() || !ready()) {
            return;
        }
        ShaderInstance shader = FxShaders.opm;
        try {
            build();
            QUEUE.sort((x, y) -> x.order() != y.order() ? Integer.compare(x.order(), y.order()) : Float.compare(y.dist(), x.dist()));
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
                draw(shader, c, glow, view, proj);
            }
        } catch (RuntimeException e) {
            broken = true;
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Saitama's effects failed; they are off until restart", e);
        } finally {
            GL11.glCullFace(GL11.GL_BACK);
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    // The hole in the clouds: a flat piece that writes only depth where the hole is, drawn before the game draws its
    // clouds, so they stay hidden behind it (FxWorld calls this after the particles).
    public static void mask(Matrix4f model, P p, Matrix4f view, Matrix4f proj) {
        if (!ready()) {
            return;
        }
        ShaderInstance shader = FxShaders.opm;
        try {
            build();
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            set(shader, model, p, false);
            Mesh.QUAD.vbo.bind();
            Mesh.QUAD.vbo.drawWithShader(view, proj, shader);
            VertexBuffer.unbind();
        } catch (RuntimeException e) {
            broken = true;
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Saitama's cloud hole failed; effects are off until restart", e);
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableCull();
        }
    }

    private static void draw(ShaderInstance shader, Cmd c, boolean glow, Matrix4f view, Matrix4f proj) {
        set(shader, c.model(), c.p(), glow);
        VertexBuffer vbo = c.mesh().vbo;
        vbo.bind();
        if ((c.sides() & BACK) != 0) {
            GL11.glCullFace(GL11.GL_FRONT);
            vbo.drawWithShader(view, proj, shader);
        }
        if ((c.sides() & FRONT) != 0) {
            GL11.glCullFace(GL11.GL_BACK);
            vbo.drawWithShader(view, proj, shader);
        }
        VertexBuffer.unbind();
    }

    public static void endFrame() {
        QUEUE.clear();
    }

    private static void set(ShaderInstance s, Matrix4f model, P p, boolean glow) {
        s.safeGetUniform("Model").set(model);
        s.safeGetUniform("Kind").set((float) p.kind);
        s.safeGetUniform("Len").set(p.len);
        s.safeGetUniform("T").set(p.t);
        s.safeGetUniform("TP").set(p.tp);
        s.safeGetUniform("Seed").set(p.seed);
        s.safeGetUniform("Scale").set(p.scale);
        s.safeGetUniform("Halo").set(p.halo);
        s.safeGetUniform("Core").set(p.core);
        s.safeGetUniform("A").set(p.alpha);
        s.safeGetUniform("Ink").set(p.ink);
        s.safeGetUniform("Life").set(p.life);
        s.safeGetUniform("Fwd").set(p.fwd);
        s.safeGetUniform("RingR").set(p.ringR);
        s.safeGetUniform("RingMax").set(p.ringMax);
        s.safeGetUniform("RingW").set(p.ringW);
        s.safeGetUniform("Hole").set(p.hole);
        s.safeGetUniform("HC").set(p.hc[0], p.hc[1]);
        s.safeGetUniform("ColA").set(p.colA[0], p.colA[1], p.colA[2]);
        s.safeGetUniform("ColB").set(p.colB[0], p.colB[1], p.colB[2]);
        s.safeGetUniform("Light").set(0.4f, 0.8f, -0.45f);
        s.safeGetUniform("GlowPass").set(glow ? 1f : 0f);
    }

    // ---------- the unit meshes, built once ----------

    private static void build() {
        for (Mesh m : Mesh.values()) {
            if (m.vbo != null) {
                continue;
            }
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            switch (m) {
                case TUBE -> tube(b, 72, 420, false);
                case HALF -> tube(b, 48, 260, true);
                case SNEEZE -> tube(b, 72, 260, false);
                case CONE -> cone(b, 64, 6);
                case QUAD -> quad(b);
                case SPHERE -> sphere(b, 32, 24);
                case BOX -> box(b);
            }
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(b.buildOrThrow());
            VertexBuffer.unbind();
            m.vbo = vbo;
        }
    }

    // The page's tubeGeo: (cos a, sin a, u), a round the whole circle (or half of it), u from 0 to 1.
    private static void tube(BufferBuilder b, int radial, int along, boolean half) {
        double span = half ? Math.PI : Math.PI * 2;
        for (int j = 0; j < along; j++) {
            float u0 = j / (float) along, u1 = (j + 1) / (float) along;
            for (int i = 0; i < radial; i++) {
                double a0 = span * i / radial, a1 = span * (i + 1) / radial;
                float[] p = {(float) Math.cos(a0), (float) Math.sin(a0), u0}, q = {(float) Math.cos(a1), (float) Math.sin(a1), u0};
                float[] r = {(float) Math.cos(a1), (float) Math.sin(a1), u1}, s = {(float) Math.cos(a0), (float) Math.sin(a0), u1};
                tri(b, p, s, q);
                tri(b, s, r, q);
            }
        }
    }

    // THREE.ConeGeometry(1, 1, radial, rows, true): apex up at y = 0.5 (kept a hair open so every ring has its angle).
    private static void cone(BufferBuilder b, int radial, int rows) {
        for (int j = 0; j < rows; j++) {
            float v0 = j / (float) rows, v1 = (j + 1) / (float) rows;
            float r0 = Math.max(1.0E-4f, v0), r1 = v1;
            float y0 = 0.5f - v0, y1 = 0.5f - v1;
            for (int i = 0; i < radial; i++) {
                double t0 = Math.PI * 2 * i / radial, t1 = Math.PI * 2 * (i + 1) / radial;
                float[] p = {(float) Math.sin(t0) * r0, y0, (float) Math.cos(t0) * r0}, q = {(float) Math.sin(t1) * r0, y0, (float) Math.cos(t1) * r0};
                float[] r = {(float) Math.sin(t1) * r1, y1, (float) Math.cos(t1) * r1}, s = {(float) Math.sin(t0) * r1, y1, (float) Math.cos(t0) * r1};
                tri(b, p, s, q);
                tri(b, s, r, q);
            }
        }
    }

    // A flat square from -1 to 1 in x and y (THREE.PlaneGeometry(2, 2), facing +z).
    private static void quad(BufferBuilder b) {
        float[] a = {-1, -1, 0}, c = {1, -1, 0}, d = {1, 1, 0}, e = {-1, 1, 0};
        tri(b, a, c, d);
        tri(b, a, d, e);
    }

    private static void sphere(BufferBuilder b, int w, int h) {
        for (int j = 0; j < h; j++) {
            double t0 = Math.PI * j / h, t1 = Math.PI * (j + 1) / h;
            for (int i = 0; i < w; i++) {
                double p0 = Math.PI * 2 * i / w, p1 = Math.PI * 2 * (i + 1) / w;
                float[] a = sp(t0, p0), c = sp(t1, p0), d = sp(t1, p1), e = sp(t0, p1);
                tri(b, a, e, c);
                tri(b, e, d, c);
            }
        }
    }

    private static float[] sp(double theta, double phi) {
        return new float[] {(float) (-Math.cos(phi) * Math.sin(theta)), (float) Math.cos(theta), (float) (Math.sin(phi) * Math.sin(theta))};
    }

    // Unit cube centered on the origin, faces wound outward.
    private static void box(BufferBuilder b) {
        float h = 0.5f;
        float[][] v = {{-h, -h, -h}, {h, -h, -h}, {h, h, -h}, {-h, h, -h}, {-h, -h, h}, {h, -h, h}, {h, h, h}, {-h, h, h}};
        int[][] f = {{4, 5, 6, 7}, {1, 0, 3, 2}, {5, 1, 2, 6}, {0, 4, 7, 3}, {7, 6, 2, 3}, {0, 1, 5, 4}};
        for (int[] q : f) {
            tri(b, v[q[0]], v[q[1]], v[q[2]]);
            tri(b, v[q[0]], v[q[2]], v[q[3]]);
        }
    }

    private static void tri(BufferBuilder b, float[] p, float[] q, float[] r) {
        b.addVertex(p[0], p[1], p[2]);
        b.addVertex(q[0], q[1], q[2]);
        b.addVertex(r[0], r[1], r[2]);
    }
}
