package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.renderer.ShaderInstance;

// Luffy's moves, drawn the way the approved page draws them (forja/luffy-gears): one unit mesh per piece bent in
// souls_luffy exactly as the page's three.js shaders bend it. LuffyFx queues what to draw; FxWorld draws the queue into
// the frame and into the glow layer (with Saitama's). Solid pieces (the rubber arm, the fists, the balloon, the bolt)
// are drawn first and write depth; the see-through ones after them, far before near, in the page's renderOrder.
public final class LuffyMesh {
    public enum Mesh {
        // squareTubeGeo(96), squareTubeGeo(48), squareTubeGeo(220), PlaneGeometry(2, 2), SphereGeometry(1, 64, 48),
        // SphereGeometry(1, 112, 84) (lighter here), TorusGeometry(0.4, 0.15, 24, 72), BoxGeometry(1, 1, 1)
        ARM, ARM48, BOLT, QUAD, SPHERE, BLAST, TORUS, BOX, VEST_L, VEST_R;

        VertexBuffer vbo;
    }

    // position and normal (the game has no such format of its own)
    public static final VertexFormat FORMAT = VertexFormat.builder().add("Position", VertexFormatElement.POSITION).add("Normal", VertexFormatElement.NORMAL).build();

    public static final int ARM = 0, BURST = 1, STEAM = 2, COLLAR = 3, FIRE = 4, BLAST = 5, BOLT = 6, SOLID = 7;
    public static final int FRONT = 1, BACK = 2, BOTH = 3;

    public static final class P {
        public int kind;
        public float t, alpha = 1, seed;
        public float[] col = {1, 1, 1}, col2 = {1, 1, 1}, p0 = new float[4], p1 = new float[4], p2 = new float[4], p3 = new float[4];
        public boolean glow = true, solid;

        P copy() {
            P p = new P();
            p.kind = kind; p.t = t; p.alpha = alpha; p.seed = seed; p.col = col.clone(); p.col2 = col2.clone(); p.p0 = p0.clone(); p.p1 = p1.clone();
            p.p2 = p2.clone(); p.p3 = p3.clone(); p.glow = glow; p.solid = solid;
            return p;
        }
    }

    private record Cmd(Mesh mesh, Matrix4f model, P p, int sides, float dist, int order) {
    }

    private static final List<Cmd> QUEUE = new ArrayList<>();
    private static boolean broken;

    private LuffyMesh() {
    }

    public static P p(int kind) {
        P p = new P();
        p.kind = kind;
        p.solid = kind == ARM || kind == SOLID || kind == BOLT;
        return p;
    }

    public static boolean ready() {
        return FxShaders.luffy != null && !broken;
    }

    public static void queue(Mesh mesh, Matrix4f model, P p, int sides, int order) {
        if (!ready() || p.alpha <= 0.002f) {
            return;
        }
        Vector3f c = model.getTranslation(new Vector3f());
        QUEUE.add(new Cmd(mesh, new Matrix4f(model), p.copy(), sides, c.length(), order));
    }

    public static void flush(boolean glow) {
        if (QUEUE.isEmpty() || !ready()) {
            return;
        }
        ShaderInstance shader = FxShaders.luffy;
        try {
            build();
            QUEUE.sort((x, y) -> x.p().solid != y.p().solid ? (x.p().solid ? -1 : 1)
                    : x.order() != y.order() ? Integer.compare(x.order(), y.order()) : Float.compare(y.dist(), x.dist()));
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            Matrix4f view = RenderSystem.getModelViewMatrix(), proj = RenderSystem.getProjectionMatrix();
            for (Cmd c : QUEUE) {
                if (glow && !c.p().glow) {
                    continue;
                }
                // in the glow layer nothing writes depth (it only adds light)
                RenderSystem.depthMask(c.p().solid && !glow);
                draw(shader, c, glow, view, proj);
            }
        } catch (RuntimeException e) {
            broken = true;
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Luffy's effects failed; they are off until restart", e);
        } finally {
            GL11.glCullFace(GL11.GL_BACK);
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void draw(ShaderInstance shader, Cmd c, boolean glow, Matrix4f view, Matrix4f proj) {
        set(shader, c.model(), c.p(), glow);
        VertexBuffer vbo = c.mesh().vbo;
        vbo.bind();
        if (c.sides() == BOTH && c.p().solid) {
            RenderSystem.disableCull();
            vbo.drawWithShader(view, proj, shader);
            RenderSystem.enableCull();
        } else {
            if ((c.sides() & BACK) != 0) {
                GL11.glCullFace(GL11.GL_FRONT);
                vbo.drawWithShader(view, proj, shader);
            }
            if ((c.sides() & FRONT) != 0) {
                GL11.glCullFace(GL11.GL_BACK);
                vbo.drawWithShader(view, proj, shader);
            }
        }
        VertexBuffer.unbind();
    }

    public static void endFrame() {
        QUEUE.clear();
    }

    private static void set(ShaderInstance s, Matrix4f model, P p, boolean glow) {
        s.safeGetUniform("Model").set(model);
        s.safeGetUniform("Kind").set((float) p.kind);
        s.safeGetUniform("T").set(p.t);
        s.safeGetUniform("A").set(p.alpha);
        s.safeGetUniform("Seed").set(p.seed);
        s.safeGetUniform("GlowPass").set(glow ? 1f : 0f);
        s.safeGetUniform("Col").set(p.col[0], p.col[1], p.col[2]);
        s.safeGetUniform("Col2").set(p.col2[0], p.col2[1], p.col2[2]);
        s.safeGetUniform("P0").set(p.p0[0], p.p0[1], p.p0[2], p.p0[3]);
        s.safeGetUniform("P1").set(p.p1[0], p.p1[1], p.p1[2], p.p1[3]);
        s.safeGetUniform("P2").set(p.p2[0], p.p2[1], p.p2[2], p.p2[3]);
        s.safeGetUniform("P3").set(p.p3[0], p.p3[1], p.p3[2], p.p3[3]);
    }

    // ---------- the unit meshes, built once ----------

    private static void build() {
        for (Mesh m : Mesh.values()) {
            if (m.vbo != null) {
                continue;
            }
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, FORMAT);
            switch (m) {
                case ARM -> squareTube(b, 96);
                case ARM48 -> squareTube(b, 48);
                case BOLT -> squareTube(b, 220);
                case QUAD -> quad(b);
                case SPHERE -> sphere(b, 64, 48);
                case BLAST -> sphere(b, 96, 72);
                case TORUS -> torus(b, 0.4f, 0.15f, 24, 72);
                case BOX -> box(b);
                // the vest's two halves over the Fusen's balloon (three's SphereGeometry(1.012, 40, 28, phi0, dphi, 0.25, 1.15))
                case VEST_L -> sphere(b, 40, 28, 1.012, Math.PI * 0.62, Math.PI * 0.42, 0.25, 1.15);
                case VEST_R -> sphere(b, 40, 28, 1.012, Math.PI * 1.96, Math.PI * 0.42, 0.25, 1.15);
            }
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(b.buildOrThrow());
            VertexBuffer.unbind();
            m.vbo = vbo;
        }
    }

    // The page's squareTubeGeo: 4 flat faces, each with its own normal (n), from -1 to 1 across (t), z from 0 to 1.
    private static void squareTube(BufferBuilder b, int along) {
        for (int f = 0; f < 4; f++) {
            float nx = Math.round(Math.cos(f * Math.PI / 2)), ny = Math.round(Math.sin(f * Math.PI / 2)), tx = -ny, ty = nx;
            for (int j = 0; j < along; j++) {
                float u0 = j / (float) along, u1 = (j + 1) / (float) along;
                float[] a = {nx - tx, ny - ty, u0}, c = {nx + tx, ny + ty, u0}, d = {nx - tx, ny - ty, u1}, e = {nx + tx, ny + ty, u1};
                float[] n = {nx, ny, 0};
                tri(b, a, c, d, n, n, n);
                tri(b, c, e, d, n, n, n);
            }
        }
    }

    private static void quad(BufferBuilder b) {
        float[] a = {-1, -1, 0}, c = {1, -1, 0}, d = {1, 1, 0}, e = {-1, 1, 0}, n = {0, 0, 1};
        tri(b, a, c, d, n, n, n);
        tri(b, a, d, e, n, n, n);
    }

    private static void sphere(BufferBuilder b, int w, int h) {
        sphere(b, w, h, 1, 0, Math.PI * 2, 0, Math.PI);
    }

    private static void sphere(BufferBuilder b, int w, int h, double r, double phi0, double dphi, double th0, double dth) {
        float rr = (float) r;
        for (int j = 0; j < h; j++) {
            double t0 = th0 + dth * j / h, t1 = th0 + dth * (j + 1) / h;
            for (int i = 0; i < w; i++) {
                double p0 = phi0 + dphi * i / w, p1 = phi0 + dphi * (i + 1) / w;
                float[] a = sp(t0, p0), c = sp(t1, p0), d = sp(t1, p1), e = sp(t0, p1);
                // wound outward (counter-clockwise seen from outside), as three's SphereGeometry: FRONT is the outer face
                tri(b, mul(a, rr), mul(c, rr), mul(e, rr), a, c, e);
                tri(b, mul(e, rr), mul(c, rr), mul(d, rr), e, c, d);
            }
        }
    }

    private static float[] mul(float[] v, float k) {
        return new float[] {v[0] * k, v[1] * k, v[2] * k};
    }

    private static float[] sp(double theta, double phi) {
        return new float[] {(float) (-Math.cos(phi) * Math.sin(theta)), (float) Math.cos(theta), (float) (Math.sin(phi) * Math.sin(theta))};
    }

    // THREE.TorusGeometry in the xy plane (the caller lays it flat).
    private static void torus(BufferBuilder b, float R, float r, int rad, int tub) {
        for (int j = 0; j < rad; j++) {
            for (int i = 0; i < tub; i++) {
                float[][] v = new float[4][], n = new float[4][];
                int[][] ij = {{j, i}, {j + 1, i}, {j + 1, i + 1}, {j, i + 1}};
                for (int q = 0; q < 4; q++) {
                    double v1 = ij[q][0] / (double) rad * Math.PI * 2, u1 = ij[q][1] / (double) tub * Math.PI * 2;
                    float x = (float) ((R + r * Math.cos(v1)) * Math.cos(u1)), y = (float) ((R + r * Math.cos(v1)) * Math.sin(u1)), z = (float) (r * Math.sin(v1));
                    float cx = (float) (R * Math.cos(u1)), cy = (float) (R * Math.sin(u1));
                    v[q] = new float[] {x, y, z};
                    float nx = x - cx, ny = y - cy, nz = z, l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    n[q] = new float[] {nx / l, ny / l, nz / l};
                }
                // wound outward, as three's TorusGeometry
                tri(b, v[0], v[3], v[1], n[0], n[3], n[1]);
                tri(b, v[1], v[3], v[2], n[1], n[3], n[2]);
            }
        }
    }

    private static void box(BufferBuilder b) {
        float h = 0.5f;
        float[][] v = {{-h, -h, -h}, {h, -h, -h}, {h, h, -h}, {-h, h, -h}, {-h, -h, h}, {h, -h, h}, {h, h, h}, {-h, h, h}};
        int[][] f = {{4, 5, 6, 7}, {1, 0, 3, 2}, {5, 1, 2, 6}, {0, 4, 7, 3}, {7, 6, 2, 3}, {0, 1, 5, 4}};
        float[][] nn = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        for (int i = 0; i < 6; i++) {
            int[] q = f[i];
            tri(b, v[q[0]], v[q[1]], v[q[2]], nn[i], nn[i], nn[i]);
            tri(b, v[q[0]], v[q[2]], v[q[3]], nn[i], nn[i], nn[i]);
        }
    }

    private static void tri(BufferBuilder b, float[] p, float[] q, float[] r, float[] np, float[] nq, float[] nr) {
        b.addVertex(p[0], p[1], p[2]).setNormal(np[0], np[1], np[2]);
        b.addVertex(q[0], q[1], q[2]).setNormal(nq[0], nq[1], nq[2]);
        b.addVertex(r[0], r[1], r[2]).setNormal(nr[0], nr[1], nr[2]);
    }
}
