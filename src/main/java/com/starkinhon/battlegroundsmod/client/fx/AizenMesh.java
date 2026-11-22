// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;

// Aizen's 3D effects (forja/aizen-kit, approved v2.3), drawn the way the page draws them: its geometry built the same
// way (three.js spheres, cylinders, boxes, its CPU tubes with parallel-transported frames), its shaders' text in
// souls_aizen, premultiplied, behind blocks, then the bright parts again into the glow layer (the page's bloom).
// AizenKit builds and queues the pieces while it runs the skills' timelines; FxWorld flushes the queue twice a frame.
public final class AizenMesh {
    public static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL;

    // souls_aizen's kinds
    public static final int EN = 0, DRAG = 1, DHEAD = 2, SOKA = 3, WALL = 4, CRES = 5, COC = 6, ORB = 7, OHALO = 8, ORBE = 9, EHALO = 10, SPIKE = 11,
            CORE = 12, CRACK = 13, RA = 14, PDOME = 15, WING = 16, LIT = 17;
    // three.js sides: FRONT, BACK; BOTH = back faces first then front faces (two draws); DOUBLE = DoubleSide (one draw, no culling)
    public static final int FRONT = 1, BACK = 2, BOTH = 3, DOUBLE = 4;

    // One piece's uniforms. queue() copies it, so a Piece can be reused.
    public static final class Piece {
        public int kind;
        public float alpha = 1, seed;
        public final float[] a = new float[4], b = new float[4];
        public float[] tintA = {1, 1, 1}, tintB = {1, 1, 1}, tintC = {1, 1, 1};
        public int sides = FRONT, order = 10;
        // false: never in the glow layer; solid: opaque, drawn first, writes depth (the page's plain ShaderMaterial)
        public boolean glow = true, solid;

        public Piece(int kind) {
            this.kind = kind;
        }

        public Piece a(float x, float y, float z, float w) {
            a[0] = x; a[1] = y; a[2] = z; a[3] = w;
            return this;
        }

        public Piece b(float x, float y, float z, float w) {
            b[0] = x; b[1] = y; b[2] = z; b[3] = w;
            return this;
        }

        public Piece sides(int s) {
            sides = s;
            return this;
        }

        public Piece order(int o) {
            order = o;
            return this;
        }

        public Piece solid() {
            solid = true;
            order = Math.min(order, 0);
            return this;
        }

        Piece copy() {
            Piece p = new Piece(kind);
            p.alpha = alpha; p.seed = seed;
            System.arraycopy(a, 0, p.a, 0, 4);
            System.arraycopy(b, 0, p.b, 0, 4);
            p.tintA = tintA.clone(); p.tintB = tintB.clone(); p.tintC = tintC.clone();
            p.sides = sides; p.order = order; p.glow = glow; p.solid = solid;
            return p;
        }
    }

    // A mesh: loose triangles of (x, y, z, u, v, nx, ny, nz). A static one is uploaded once; a dynamic one (a tube that
    // moves) is filled again every frame (reset + add) and uploaded once that frame for both passes.
    public static final class Geo {
        float[] d = new float[8 * 3 * 64];
        int n;
        VertexBuffer vbo;
        boolean dirty = true;
        final boolean dynamic;

        public Geo(boolean dynamic) {
            this.dynamic = dynamic;
        }

        public void reset() {
            n = 0;
            dirty = true;
        }

        public void v(float x, float y, float z, float u, float w, float nx, float ny, float nz) {
            if ((n + 1) * 8 > d.length) {
                float[] g = new float[d.length * 2];
                System.arraycopy(d, 0, g, 0, n * 8);
                d = g;
            }
            int k = n * 8;
            d[k] = x; d[k + 1] = y; d[k + 2] = z; d[k + 3] = u; d[k + 4] = w; d[k + 5] = nx; d[k + 6] = ny; d[k + 7] = nz;
            n++;
            dirty = true;
        }

        // vertex i of an indexed list (pos[i*3], uv[i*2], nor[i*3])
        void vi(float[] pos, float[] uv, float[] nor, int i) {
            v(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], uv == null ? 0 : uv[i * 2], uv == null ? 0 : uv[i * 2 + 1], nor[i * 3], nor[i * 3 + 1], nor[i * 3 + 2]);
        }

        public void indexed(float[] pos, float[] uv, float[] nor, int[] idx) {
            for (int i : idx) {
                vi(pos, uv, nor, i);
            }
        }

        public boolean empty() {
            return n == 0;
        }

        public void close() {
            if (vbo != null) {
                vbo.close();
                vbo = null;
            }
            dirty = true;
        }

        boolean upload() {
            if (n == 0) {
                return false;
            }
            if (vbo != null && !dirty) {
                return true;
            }
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, FORMAT);
            for (int i = 0; i < n; i++) {
                int k = i * 8;
                b.addVertex(d[k], d[k + 1], d[k + 2]).setUv(d[k + 3], d[k + 4]).setColor(255, 255, 255, 255).setNormal(d[k + 5], d[k + 6], d[k + 7]);
            }
            MeshData mesh = b.build();
            if (mesh == null) {
                return false;
            }
            if (vbo == null) {
                vbo = new VertexBuffer(dynamic ? VertexBuffer.Usage.DYNAMIC : VertexBuffer.Usage.STATIC);
            }
            vbo.bind();
            vbo.upload(mesh);
            VertexBuffer.unbind();
            dirty = false;
            return true;
        }
    }

    private record Cmd(Geo geo, Matrix4f model, Piece p, int cull, float key, float dist) {
    }

    private static final List<Cmd> QUEUE = new ArrayList<>();
    private static boolean broken;
    private static float time;

    private AizenMesh() {
    }

    public static boolean ready() {
        return FxShaders.aizen != null && !broken && !FxShaders.flat();
    }

    public static void time(float seconds) {
        time = seconds;
    }

    // Queues a piece on a mesh; model places it relative to the camera.
    public static void queue(Geo geo, Matrix4f model, Piece p) {
        if (!ready() || geo == null || geo.empty() || p.alpha <= 0.002f) {
            return;
        }
        Vector3f c = model.getTranslation(new Vector3f());
        float dist = c.length();
        Piece q = p.copy();
        Matrix4f m = new Matrix4f(model);
        // A mirrored model (a wing on the left) turns its triangles round: cull the other side.
        boolean flip = m.determinant() < 0;
        int back = flip ? GL11.GL_BACK : GL11.GL_FRONT, front = flip ? GL11.GL_FRONT : GL11.GL_BACK;
        switch (q.sides) {
            case BACK -> QUEUE.add(new Cmd(geo, m, q, back, q.order, dist));
            case BOTH -> {
                QUEUE.add(new Cmd(geo, m, q, back, q.order, dist));
                QUEUE.add(new Cmd(geo, m, q, front, q.order + 0.5f, dist));
            }
            case DOUBLE -> QUEUE.add(new Cmd(geo, m, q, 0, q.order, dist));
            default -> QUEUE.add(new Cmd(geo, m, q, front, q.order + 0.5f, dist));
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
        ShaderInstance shader = FxShaders.aizen;
        try {
            // the page: opaque first (near to far), then the see-through ones by renderOrder, far before near
            QUEUE.sort((x, y) -> {
                if (x.p().solid != y.p().solid) {
                    return x.p().solid ? -1 : 1;
                }
                if (x.p().solid) {
                    return Float.compare(x.dist(), y.dist());
                }
                return x.key() != y.key() ? Float.compare(x.key(), y.key()) : Float.compare(y.dist(), x.dist());
            });
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            Matrix4f view = RenderSystem.getModelViewMatrix(), proj = RenderSystem.getProjectionMatrix();
            for (Cmd c : QUEUE) {
                if (glow && !c.p().glow) {
                    continue;
                }
                if (!c.geo().upload()) {
                    continue;
                }
                set(shader, c, glow);
                RenderSystem.depthMask(c.p().solid && !glow);
                if (c.cull() == 0) {
                    RenderSystem.disableCull();
                } else {
                    RenderSystem.enableCull();
                    GL11.glCullFace(c.cull());
                }
                c.geo().vbo.bind();
                c.geo().vbo.drawWithShader(view, proj, shader);
                VertexBuffer.unbind();
            }
        } catch (RuntimeException e) {
            broken = true;
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Aizen's 3D effects failed; they are off until restart", e);
        } finally {
            RenderSystem.enableCull();
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
        s.safeGetUniform("Time").set(time);
        s.safeGetUniform("Seed").set(p.seed);
        s.safeGetUniform("Alpha").set(p.alpha);
        s.safeGetUniform("GlowPass").set(glow ? 1f : 0f);
        s.safeGetUniform("A").set(p.a[0], p.a[1], p.a[2], p.a[3]);
        s.safeGetUniform("B").set(p.b[0], p.b[1], p.b[2], p.b[3]);
        s.safeGetUniform("TintA").set(p.tintA[0], p.tintA[1], p.tintA[2]);
        s.safeGetUniform("TintB").set(p.tintB[0], p.tintB[1], p.tintB[2]);
        s.safeGetUniform("TintC").set(p.tintC[0], p.tintC[1], p.tintC[2]);
    }

    // ---------- three.js geometry, built the same way (same vertex order, same winding) ----------

    // SphereGeometry(r, w, h, phiStart, phiLength, thetaStart, thetaLength)
    public static Geo sphere(float r, int w, int h, float th0, float thLen) {
        Geo g = new Geo(false);
        int nv = (w + 1) * (h + 1);
        float[] pos = new float[nv * 3], uv = new float[nv * 2], nor = new float[nv * 3];
        int k = 0;
        for (int iy = 0; iy <= h; iy++) {
            float v = (float) iy / h;
            for (int ix = 0; ix <= w; ix++) {
                float u = (float) ix / w;
                double phi = u * Math.PI * 2, th = th0 + v * thLen;
                float x = (float) (-Math.cos(phi) * Math.sin(th)), y = (float) Math.cos(th), z = (float) (Math.sin(phi) * Math.sin(th));
                pos[k * 3] = x * r; pos[k * 3 + 1] = y * r; pos[k * 3 + 2] = z * r;
                nor[k * 3] = x; nor[k * 3 + 1] = y; nor[k * 3 + 2] = z;
                uv[k * 2] = u; uv[k * 2 + 1] = 1 - v;
                k++;
            }
        }
        List<Integer> idx = new ArrayList<>();
        for (int iy = 0; iy < h; iy++) {
            for (int ix = 0; ix < w; ix++) {
                int a = iy * (w + 1) + ix + 1, b = iy * (w + 1) + ix, c = (iy + 1) * (w + 1) + ix, d = (iy + 1) * (w + 1) + ix + 1;
                if (iy != 0 || th0 > 0) {
                    idx.add(a); idx.add(b); idx.add(d);
                }
                if (iy != h - 1 || th0 + thLen < Math.PI - 1.0E-4) {
                    idx.add(b); idx.add(c); idx.add(d);
                }
            }
        }
        g.indexed(pos, uv, nor, idx.stream().mapToInt(Integer::intValue).toArray());
        return g;
    }

    public static Geo sphere(float r, int w, int h) {
        return sphere(r, w, h, 0, (float) Math.PI);
    }

    // CylinderGeometry(1, 1, 1, radial, hseg, open) moved up by 0.5 (y from 0 to 1): the Sokatsui's flame
    public static Geo openCylinder(int radial, int hseg) {
        Geo g = new Geo(false);
        int nv = (radial + 1) * (hseg + 1);
        float[] pos = new float[nv * 3], uv = new float[nv * 2], nor = new float[nv * 3];
        int k = 0;
        for (int y = 0; y <= hseg; y++) {
            float v = (float) y / hseg;
            for (int x = 0; x <= radial; x++) {
                float u = (float) x / radial;
                double th = u * Math.PI * 2;
                float s = (float) Math.sin(th), c = (float) Math.cos(th);
                pos[k * 3] = s; pos[k * 3 + 1] = -v + 0.5f + 0.5f; pos[k * 3 + 2] = c;
                nor[k * 3] = s; nor[k * 3 + 2] = c;
                uv[k * 2] = u; uv[k * 2 + 1] = 1 - v;
                k++;
            }
        }
        List<Integer> idx = new ArrayList<>();
        for (int x = 0; x < radial; x++) {
            for (int y = 0; y < hseg; y++) {
                int a = y * (radial + 1) + x, b = (y + 1) * (radial + 1) + x, c = (y + 1) * (radial + 1) + x + 1, d = y * (radial + 1) + x + 1;
                idx.add(a); idx.add(b); idx.add(d);
                idx.add(b); idx.add(c); idx.add(d);
            }
        }
        g.indexed(pos, uv, nor, idx.stream().mapToInt(Integer::intValue).toArray());
        return g;
    }

    // BoxGeometry(w, h, d) centered (the Danku pane): positions are the box's own (the shader reads them)
    public static Geo box(float w, float h, float d) {
        Geo g = new Geo(false);
        float x = w / 2, y = h / 2, z = d / 2;
        float[][] faces = {
                {x, -y, z, x, -y, -z, x, y, -z, x, y, z, 1, 0, 0}, {-x, -y, -z, -x, -y, z, -x, y, z, -x, y, -z, -1, 0, 0},
                {-x, y, z, x, y, z, x, y, -z, -x, y, -z, 0, 1, 0}, {-x, -y, -z, x, -y, -z, x, -y, z, -x, -y, z, 0, -1, 0},
                {-x, -y, z, x, -y, z, x, y, z, -x, y, z, 0, 0, 1}, {x, -y, -z, -x, -y, -z, -x, y, -z, x, y, -z, 0, 0, -1}};
        for (float[] f : faces) {
            int[] o = {0, 1, 2, 0, 2, 3};
            for (int i : o) {
                g.v(f[i * 3], f[i * 3 + 1], f[i * 3 + 2], 0, 0, f[12], f[13], f[14]);
            }
        }
        return g;
    }

    // a flat square on y = 0 from -1 to 1, uv 0..1 (the crack decal, scaled by its model)
    public static Geo plane(int n) {
        Geo g = new Geo(false);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float u0 = (float) i / n, u1 = (float) (i + 1) / n, w0 = (float) j / n, w1 = (float) (j + 1) / n;
                float x0 = u0 * 2 - 1, x1 = u1 * 2 - 1, z0 = w0 * 2 - 1, z1 = w1 * 2 - 1;
                g.v(x0, 0, z0, u0, w0, 0, 1, 0); g.v(x0, 0, z1, u0, w1, 0, 1, 0); g.v(x1, 0, z1, u1, w1, 0, 1, 0);
                g.v(x0, 0, z0, u0, w0, 0, 1, 0); g.v(x1, 0, z1, u1, w1, 0, 1, 0); g.v(x1, 0, z0, u1, w0, 0, 1, 0);
            }
        }
        return g;
    }

    // a flat-shaded convex solid (tetrahedron, icosahedron) of radius 1, every face turned outward
    public static Geo poly(float[][] verts, int[] faces) {
        Geo g = new Geo(false);
        for (int i = 0; i < faces.length; i += 3) {
            Vector3f a = new Vector3f(verts[faces[i]][0], verts[faces[i]][1], verts[faces[i]][2]).normalize();
            Vector3f b = new Vector3f(verts[faces[i + 1]][0], verts[faces[i + 1]][1], verts[faces[i + 1]][2]).normalize();
            Vector3f c = new Vector3f(verts[faces[i + 2]][0], verts[faces[i + 2]][1], verts[faces[i + 2]][2]).normalize();
            Vector3f nrm = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
            Vector3f mid = new Vector3f(a).add(b).add(c);
            if (nrm.dot(mid) < 0) {
                Vector3f t = b;
                b = c;
                c = t;
                nrm.negate();
            }
            for (Vector3f p : new Vector3f[] {a, b, c}) {
                g.v(p.x, p.y, p.z, 0, 0, nrm.x, nrm.y, nrm.z);
            }
        }
        return g;
    }

    public static Geo tetra() {
        return poly(new float[][] {{1, 1, 1}, {-1, -1, 1}, {-1, 1, -1}, {1, -1, -1}}, new int[] {2, 1, 0, 0, 3, 2, 1, 3, 0, 2, 3, 1});
    }

    public static Geo icosa() {
        float t = (1 + (float) Math.sqrt(5)) / 2;
        float[][] v = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        int[] f = {0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8, 3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
                4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        return poly(v, f);
    }
}
