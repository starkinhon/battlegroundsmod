package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
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

// The Dragon Ball kits' 3D effects, drawn the way the approved pages draw them (three.js): one unit mesh per effect
// (sphere, open tube, flat square) bent in souls_ki exactly as there, see-through and premultiplied, behind blocks.
// DbFx queues what to draw while it walks its effects; FxWorld draws the queue twice: into the frame after the
// see-through layer, and into the glow layer (the bloom). Each piece is drawn back faces first, then front faces, far
// pieces before near ones, like the pages' renderOrder.
public final class KiMesh {
    public enum Mesh {
        BLAST(112, 84), AURA(128, 96), BALL(96, 72), HALO(64, 48), FLASH(32, 24), TUBE(72, 200), SPIRAL(48, 600), WIND(64, 60), QUAD(1, 1);

        final int a;
        final int b;
        VertexBuffer vbo;

        Mesh(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    public static final int BLAST = 0, GENKI = 1, HALO = 2, BEAM = 3, RING = 4, SHELL = 5, FLASH = 6, SUN = 7, SUN_HALO = 8, SAW = 9, KIEN = 10,
            SPIRAL = 11, WIND = 12;
    public static final int FRONT = 1, BACK = 2, BOTH = 3;

    // Everything souls_ki reads; queue() keeps a copy, so one P can be reused for the next piece.
    public static final class P {
        public int kind;
        public float life, seed, time, flat, len = 1, w = 0.5f, flow, alpha = 1, tail = -1, pulse, smoke, skirt = 1;
        public float[] color = {0.55f, 0.83f, 1f}, color2 = {1f, 0.37f, 0.68f}, dust = {0.8f, 0.74f, 0.62f};
        public float phase, growth, flashAmt, tongue = 0.36f, spike = 0.1f, spikeFreq = 4.5f, spikeSharp = 2.2f, crown = 0.55f, up = 0.5f, thresh = 0.62f;
        public float[] size = {0.62f, 1.12f, 0.5f};
        public float ringR, ringMax = 1, rot, teeth = 26, pitch = 1.05f, twist;
        public float r0 = 0.3f, r1 = 1, flare = 1, bend;
        // false: never drawn into the glow layer (smoke, dust)
        public boolean glow = true;

        P copy() {
            P p = new P();
            p.kind = kind; p.life = life; p.seed = seed; p.time = time; p.flat = flat; p.len = len; p.w = w; p.flow = flow; p.alpha = alpha; p.tail = tail;
            p.pulse = pulse; p.smoke = smoke; p.skirt = skirt; p.color = color.clone(); p.color2 = color2.clone(); p.dust = dust.clone();
            p.phase = phase; p.growth = growth; p.flashAmt = flashAmt; p.tongue = tongue; p.spike = spike; p.spikeFreq = spikeFreq; p.spikeSharp = spikeSharp;
            p.crown = crown; p.up = up; p.thresh = thresh; p.size = size.clone(); p.ringR = ringR; p.ringMax = ringMax; p.rot = rot; p.teeth = teeth;
            p.pitch = pitch; p.twist = twist; p.r0 = r0; p.r1 = r1; p.flare = flare; p.bend = bend; p.glow = glow;
            return p;
        }
    }

    private record Cmd(Mesh mesh, Matrix4f model, P p, int sides, float dist, int order) {
    }

    private static final List<Cmd> QUEUE = new ArrayList<>();
    private static boolean broken;

    private KiMesh() {
    }

    // A fresh set of parameters for one kind of piece.
    public static P p(int kind) {
        P p = new P();
        p.kind = kind;
        return p;
    }

    public static boolean ready() {
        return FxShaders.ki != null && !broken;
    }

    // Queues a piece. order: ties broken by it (bigger = drawn later, on top), like the pages' renderOrder.
    public static void queue(Mesh mesh, Matrix4f model, P p, int sides, int order) {
        if (!ready()) {
            return;
        }
        Vector3f c = model.getTranslation(new Vector3f());
        float dist = c.length();
        P q = p.copy();
        float reach = reach(mesh, model);
        if (reach > 0) {
            // The camera inside a ball, dome or aura: on the pages the camera never goes in, but here a fingertip flash,
            // a Genki halo or a dome round the target can wrap it, and the inside then paints the whole screen (the
            // pink screen of the Makankosappo, the red one of the Supernova). It fades out as the camera gets in.
            float k = Math.max(0, Math.min(1, (dist / reach - 0.85f) / 0.4f));
            q.alpha *= k * k * (3 - 2 * k);
            if (q.alpha <= 0.002f) {
                return;
            }
        }
        QUEUE.add(new Cmd(mesh, new Matrix4f(model), q, sides, dist, order));
    }

    // How far a round piece reaches from its center (0: not round; tubes and squares are left alone).
    private static float reach(Mesh mesh, Matrix4f model) {
        float bound = switch (mesh) {
            case BLAST -> 1.3f;
            case AURA -> 1.75f;
            case BALL, HALO, FLASH -> 1.06f;
            default -> 0;
        };
        if (bound == 0) {
            return 0;
        }
        Vector3f s = model.getScale(new Vector3f());
        return Math.max(s.x, Math.max(s.y, s.z)) * bound;
    }

    // ---------- where things go (camera-relative) ----------

    public static Matrix4f sphere(Vector3f center, float r) {
        return new Matrix4f().translation(center).scale(r);
    }

    // A tube from `from` pointing along `dir` (its length is P.len, its width P.w, both in the shader).
    public static Matrix4f along(Vector3f from, Vector3f dir) {
        Vector3f d = new Vector3f(dir);
        if (d.lengthSquared() < 1.0E-8f) {
            d.set(0, 0, 1);
        }
        d.normalize();
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d);
        return new Matrix4f().translation(from).rotate(q);
    }

    // A flat square lying on the ground (half size `half`), or turned by q.
    public static Matrix4f flat(Vector3f center, float half) {
        return new Matrix4f().translation(center).scale(half, 1, half);
    }

    public static Matrix4f disc(Vector3f center, float half, Quaternionf q) {
        return new Matrix4f().translation(center).rotate(q).scale(half, 1, half);
    }

    // ---------- drawing (FxWorld) ----------

    public static boolean pending() {
        return !QUEUE.isEmpty();
    }

    // glow false: into the frame; true: into the glow layer (between FxPost.beginGlow and endGlow).
    public static void flush(boolean glow) {
        if (QUEUE.isEmpty() || !ready()) {
            return;
        }
        ShaderInstance shader = FxShaders.ki;
        try {
            build();
            // Like three.js on the pages: renderOrder first (smoke under the dome, halo over the ball), then far before near.
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
                set(shader, c, glow);
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
        } catch (RuntimeException e) {
            broken = true;
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Dragon Ball 3D effects failed; they are off until restart", e);
        } finally {
            GL11.glCullFace(GL11.GL_BACK);
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    // End of the frame: the next one queues again.
    public static void endFrame() {
        QUEUE.clear();
    }

    private static void set(ShaderInstance s, Cmd c, boolean glow) {
        P p = c.p();
        s.safeGetUniform("Model").set(c.model());
        s.safeGetUniform("Kind").set((float) p.kind);
        s.safeGetUniform("Life").set(p.life);
        s.safeGetUniform("Seed").set(p.seed);
        s.safeGetUniform("Time").set(p.time);
        s.safeGetUniform("Flat").set(p.flat);
        s.safeGetUniform("Len").set(p.len);
        s.safeGetUniform("W").set(p.w);
        s.safeGetUniform("Flow").set(p.flow);
        s.safeGetUniform("Alpha").set(p.alpha);
        s.safeGetUniform("Tail").set(p.tail);
        s.safeGetUniform("Pulse").set(p.pulse);
        s.safeGetUniform("Smoke").set(p.smoke);
        s.safeGetUniform("Skirt").set(p.skirt);
        s.safeGetUniform("Color").set(p.color[0], p.color[1], p.color[2]);
        s.safeGetUniform("Color2").set(p.color2[0], p.color2[1], p.color2[2]);
        s.safeGetUniform("Dust").set(p.dust[0], p.dust[1], p.dust[2]);
        s.safeGetUniform("LightDir").set(-0.4f, 0.8f, 0.45f);
        s.safeGetUniform("Phase").set(p.phase);
        s.safeGetUniform("Growth").set(p.growth);
        s.safeGetUniform("FlashAmt").set(p.flashAmt);
        s.safeGetUniform("Tongue").set(p.tongue);
        s.safeGetUniform("Spike").set(p.spike);
        s.safeGetUniform("SpikeFreq").set(p.spikeFreq);
        s.safeGetUniform("SpikeSharp").set(p.spikeSharp);
        s.safeGetUniform("Crown").set(p.crown);
        s.safeGetUniform("Up").set(p.up);
        s.safeGetUniform("Thresh").set(p.thresh);
        s.safeGetUniform("Size").set(p.size[0], p.size[1], p.size[2]);
        s.safeGetUniform("RingR").set(p.ringR);
        s.safeGetUniform("RingMax").set(p.ringMax);
        s.safeGetUniform("Rot").set(p.rot);
        s.safeGetUniform("Teeth").set(p.teeth);
        s.safeGetUniform("Pitch").set(p.pitch);
        s.safeGetUniform("Twist").set(p.twist);
        s.safeGetUniform("R0").set(p.r0);
        s.safeGetUniform("R1").set(p.r1);
        s.safeGetUniform("Flare").set(p.flare);
        s.safeGetUniform("Bend").set(p.bend);
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
                case TUBE, SPIRAL, WIND -> tube(b, m.a, m.b);
                case QUAD -> quad(b);
                default -> sphere(b, m.a, m.b);
            }
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(b.buildOrThrow());
            VertexBuffer.unbind();
            m.vbo = vbo;
        }
    }

    // Unit sphere, like THREE.SphereGeometry(1, w, h).
    private static void sphere(BufferBuilder b, int w, int h) {
        for (int j = 0; j < h; j++) {
            float t0 = (float) Math.PI * j / h, t1 = (float) Math.PI * (j + 1) / h;
            for (int i = 0; i < w; i++) {
                float p0 = (float) (Math.PI * 2) * i / w, p1 = (float) (Math.PI * 2) * (i + 1) / w;
                float[] a = sp(t0, p0), c = sp(t1, p0), d = sp(t1, p1), e = sp(t0, p1);
                tri(b, a, e, c);
                tri(b, e, d, c);
            }
        }
    }

    private static float[] sp(float theta, float phi) {
        return new float[] {(float) (-Math.cos(phi) * Math.sin(theta)), (float) Math.cos(theta), (float) (Math.sin(phi) * Math.sin(theta))};
    }

    // Open unit tube: radius 1, y from 0 to 1 (like CylinderGeometry(1, 1, 1, w, h, true) moved up by half).
    private static void tube(BufferBuilder b, int w, int h) {
        for (int j = 0; j < h; j++) {
            float y0 = j / (float) h, y1 = (j + 1) / (float) h;
            for (int i = 0; i < w; i++) {
                float a0 = (float) (Math.PI * 2) * i / w, a1 = (float) (Math.PI * 2) * (i + 1) / w;
                float[] p = {(float) Math.sin(a0), y0, (float) Math.cos(a0)}, q = {(float) Math.sin(a1), y0, (float) Math.cos(a1)};
                float[] r = {(float) Math.sin(a1), y1, (float) Math.cos(a1)}, s = {(float) Math.sin(a0), y1, (float) Math.cos(a0)};
                tri(b, p, q, r);
                tri(b, p, r, s);
            }
        }
    }

    // Flat square from -1 to 1 in x and z.
    private static void quad(BufferBuilder b) {
        float[] a = {-1, 0, -1}, c = {1, 0, -1}, d = {1, 0, 1}, e = {-1, 0, 1};
        tri(b, a, d, c);
        tri(b, a, e, d);
    }

    private static void tri(BufferBuilder b, float[] p, float[] q, float[] r) {
        b.addVertex(p[0], p[1], p[2]);
        b.addVertex(q[0], q[1], q[2]);
        b.addVertex(r[0], r[1], r[2]);
    }
}
