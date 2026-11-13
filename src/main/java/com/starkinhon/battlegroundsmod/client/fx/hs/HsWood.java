package com.starkinhon.battlegroundsmod.client.fx.hs;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.fx.FxShaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

// Draws Hashirama's wood (souls_wood) the way the approved page does: every piece is its own mesh (WoodGeo) with its own
// material numbers (seed, colors, grain, rings, carved lines, growth), uploaded once to the GPU and drawn every frame with
// the page's uniforms. HsFx queues pieces while it walks the effects; FxWorld flushes them once a frame after KiMesh,
// never into the glow. Meshes not drawn for a while give their GPU memory back.
public final class HsWood {
    public static final float PU = 0.9f;
    // the page's light (WL.uLight), in the world
    private static final float LX, LY, LZ;

    static {
        double x = -0.45, y = 0.8, z = 0.4, l = Math.sqrt(x * x + y * y + z * z);
        LX = (float) (x / l);
        LY = (float) (y / l);
        LZ = (float) (z / l);
    }

    // A material: the page's woodMat / woodSurfMat options.
    public static final class Mat {
        public int mode; // 0 tube, 1 surface, 2 flat color, 3 dragon eye
        public float[] wood = WoodGeo.hex("#b48a5a"), dark = WoodGeo.hex("#5a3a20"), tint = {1, 1, 1};
        public float seed, tip = 0.08f, fresh = 1, gr = 1, fine, ring, cav, grain = 7;

        public static Mat tube(double seed, String wood, String dark) {
            Mat m = new Mat();
            m.seed = (float) seed;
            m.wood = WoodGeo.hex(wood);
            m.dark = WoodGeo.hex(dark);
            return m;
        }

        public static Mat surf(double seed, String wood, String dark, double grain) {
            Mat m = tube(seed, wood, dark);
            m.mode = 1;
            m.grain = (float) grain;
            return m;
        }

        public Mat tip(double v) {
            tip = (float) v;
            return this;
        }

        public Mat gr(double v) {
            gr = (float) v;
            return this;
        }

        public Mat fine(double v) {
            fine = (float) v;
            return this;
        }

        public Mat ring(double v) {
            ring = (float) v;
            return this;
        }

        public Mat cav(double v) {
            cav = (float) v;
            return this;
        }
    }

    private record Cmd(WoodGeo.Geo g, Mat m, Matrix4f model, float grow, float dis, float bright, Rail rail, float bodyLen) {
    }

    // The dragon's trail on the GPU: a RailN x 3 float texture (points, normals, binormals) the skin is laid on.
    public static final class Rail {
        int tex = -1, n;
        java.nio.FloatBuffer buf;

        public void set(org.joml.Vector3d[] pts) {
            RenderSystem.assertOnRenderThread();
            n = pts.length;
            org.joml.Vector3d[][] F = WoodGeo.tubeFrames(pts);
            if (buf == null || buf.capacity() < n * 12) {
                buf = org.lwjgl.BufferUtils.createFloatBuffer(n * 12);
            }
            buf.clear();
            org.joml.Vector3d[][] rows = {pts, F[0], F[1]};
            for (org.joml.Vector3d[] row : rows) {
                for (org.joml.Vector3d p : row) {
                    buf.put((float) p.x).put((float) p.y).put((float) p.z).put(1f);
                }
            }
            buf.flip();
            boolean fresh = tex < 0;
            if (fresh) {
                tex = com.mojang.blaze3d.platform.GlStateManager._genTexture();
            }
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(tex);
            if (fresh) {
                com.mojang.blaze3d.platform.GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                com.mojang.blaze3d.platform.GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            }
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            com.mojang.blaze3d.platform.GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_RGBA32F, n, 3, 0, GL11.GL_RGBA, GL11.GL_FLOAT, buf);
        }

        public void close() {
            if (tex >= 0) {
                int t = tex;
                tex = -1;
                RenderSystem.recordRenderCall(() -> com.mojang.blaze3d.platform.GlStateManager._deleteTexture(t));
            }
        }
    }

    // Geometry built this frame (splinters, ground warnings, motes): triangles, 12 floats a vertex
    // (x, y, z, u, v, r, g, b, a, nx, ny, nz), drawn once.
    public static final class Tris {
        float[] v = new float[12 * 64];
        int n;

        public void vert(float x, float y, float z, float u, float w, float r, float g, float b, float a, float nx, float ny, float nz) {
            if ((n + 1) * 12 > v.length) {
                v = java.util.Arrays.copyOf(v, v.length * 2);
            }
            int k = n * 12;
            v[k] = x;
            v[k + 1] = y;
            v[k + 2] = z;
            v[k + 3] = u;
            v[k + 4] = w;
            v[k + 5] = r;
            v[k + 6] = g;
            v[k + 7] = b;
            v[k + 8] = a;
            v[k + 9] = nx;
            v[k + 10] = ny;
            v[k + 11] = nz;
            n++;
        }

        // a flat quad on the page's ground (y), from (x0, z0) to (x1, z1), uv -1..1
        public void ground(float x0, float z0, float x1, float z1, float y) {
            vert(x0, y, z0, -1, -1, 1, 1, 1, 1, 0, 1, 0);
            vert(x1, y, z0, 1, -1, 1, 1, 1, 1, 0, 1, 0);
            vert(x1, y, z1, 1, 1, 1, 1, 1, 1, 0, 1, 0);
            vert(x0, y, z0, -1, -1, 1, 1, 1, 1, 0, 1, 0);
            vert(x1, y, z1, 1, 1, 1, 1, 1, 1, 0, 1, 0);
            vert(x0, y, z1, -1, 1, 1, 1, 1, 1, 0, 1, 0);
        }

        // a quad facing the camera at (x, y, z), half size s, with the camera's right (rx..) and up (ux..) in this space
        public void sprite(float x, float y, float z, float s, float rx, float ry, float rz, float ux, float uy, float uz, float r, float g, float b, float a) {
            float ax = (-rx - ux) * s, ay = (-ry - uy) * s, az = (-rz - uz) * s, bx = (rx - ux) * s, by = (ry - uy) * s, bz = (rz - uz) * s;
            vert(x + ax, y + ay, z + az, -1, -1, r, g, b, a, 0, 1, 0);
            vert(x + bx, y + by, z + bz, 1, -1, r, g, b, a, 0, 1, 0);
            vert(x - ax, y - ay, z - az, 1, 1, r, g, b, a, 0, 1, 0);
            vert(x + ax, y + ay, z + az, -1, -1, r, g, b, a, 0, 1, 0);
            vert(x - ax, y - ay, z - az, 1, 1, r, g, b, a, 0, 1, 0);
            vert(x - bx, y - by, z - bz, -1, 1, r, g, b, a, 0, 1, 0);
        }

        // a little lit cube (a splinter or a clod): center, half sizes, rotation (3x3 row-major), color
        public void cube(float x, float y, float z, float sx, float sy, float sz, float[] m, float r, float g, float b) {
            float[][] N = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (float[] nn : N) {
                float[] a1 = nn[0] != 0 ? new float[] {0, 1, 0} : new float[] {1, 0, 0};
                float[] a2 = {nn[1] * a1[2] - nn[2] * a1[1], nn[2] * a1[0] - nn[0] * a1[2], nn[0] * a1[1] - nn[1] * a1[0]};
                float[][] c = new float[4][];
                int[][] sg = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int q = 0; q < 4; q++) {
                    float lx = (nn[0] + a1[0] * sg[q][0] + a2[0] * sg[q][1]) * sx, ly = (nn[1] + a1[1] * sg[q][0] + a2[1] * sg[q][1]) * sy,
                            lz = (nn[2] + a1[2] * sg[q][0] + a2[2] * sg[q][1]) * sz;
                    c[q] = new float[] {x + m[0] * lx + m[1] * ly + m[2] * lz, y + m[3] * lx + m[4] * ly + m[5] * lz, z + m[6] * lx + m[7] * ly + m[8] * lz};
                }
                float nx = m[0] * nn[0] + m[1] * nn[1] + m[2] * nn[2], ny = m[3] * nn[0] + m[4] * nn[1] + m[5] * nn[2], nz = m[6] * nn[0] + m[7] * nn[1] + m[8] * nn[2];
                int[] o = {0, 1, 2, 0, 2, 3};
                for (int q : o) {
                    vert(c[q][0], c[q][1], c[q][2], 0, 0, r, g, b, 1, nx, ny, nz);
                }
            }
        }
    }

    private record Imm(int mode, Matrix4f model, Tris t, java.util.function.Consumer<ShaderInstance> uniforms, boolean opaque) {
    }

    private static final List<Imm> IMM = new ArrayList<>();

    // Queue geometry built this frame; uniforms sets this draw's own numbers (Crack, Grow, Seed, Tint, Alpha...).
    public static void imm(int mode, Matrix4f model, Tris t, boolean opaque, java.util.function.Consumer<ShaderInstance> uniforms) {
        if (!broken && t != null && t.n > 0) {
            IMM.add(new Imm(mode, model, t, uniforms, opaque));
        }
    }

    private static final List<Cmd> QUEUE = new ArrayList<>();
    private static final Map<WoodGeo.Geo, int[]> LIVE = new IdentityHashMap<>();
    private static boolean broken;
    private static int frame;

    private HsWood() {
    }

    // The page's growTo(m, g): uGrow = g * (1 + tip).
    public static float grow(Mat m, double g) {
        return (float) (g * (1 + m.tip));
    }

    // Queue a piece: model maps its page-unit geometry to camera-relative blocks; grow = the page's uGrow (2 = all of it).
    public static void draw(WoodGeo.Geo g, Mat m, Matrix4f model, float grow, float dis, float bright) {
        if (!broken && g != null && dis < 1 && grow > 0) {
            QUEUE.add(new Cmd(g, m, model, grow, dis, bright, null, 0));
        }
    }

    // Queue a piece in the dragon's body space, laid on rail (body length bodyLen page units).
    public static void drawSkin(WoodGeo.Geo g, Mat m, Matrix4f model, float grow, float dis, float bright, Rail rail, float bodyLen) {
        if (!broken && g != null && dis < 1 && grow > 0 && rail != null && rail.tex >= 0) {
            QUEUE.add(new Cmd(g, m, model, grow, dis, bright, rail, bodyLen));
        }
    }

    // How lit the wood standing at pos is (0.3 in the dark, 1 in daylight or by a torch).
    public static float bright(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 1;
        }
        int packed = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos));
        float sky = ((packed >> 20) & 15) / 15f * Math.min(1, mc.level.getSkyDarken(1) * 1.1f), block = ((packed >> 4) & 15) / 15f;
        return 0.3f + 0.7f * Math.max(sky, block * 0.9f);
    }

    // A frame in page units: origin camera-relative, turned so the page's -z points along yaw (radians, the page's yawTo),
    // scaled 0.9.
    public static Matrix4f frame(double ox, double oy, double oz, double yaw) {
        return new Matrix4f().translation((float) ox, (float) oy, (float) oz).rotateY((float) yaw).scale(PU);
    }

    public static boolean active() {
        return !QUEUE.isEmpty() || !IMM.isEmpty();
    }

    public static void clear() {
        QUEUE.clear();
        IMM.clear();
    }

    private static void drawImm(ShaderInstance sh, Imm im) {
        sh.safeGetUniform("Model").set(im.model());
        sh.safeGetUniform("Mode").set((float) im.mode());
        sh.safeGetUniform("Alpha").set(1f);
        sh.safeGetUniform("Tint").set(1f, 1f, 1f);
        sh.safeGetUniform("Bright").set(1f);
        sh.safeGetUniform("Skin").set(0f);
        if (im.uniforms() != null) {
            im.uniforms().accept(sh);
        }
        Tris t = im.t();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
        for (int i = 0; i < t.n; i++) {
            int k = i * 12;
            b.addVertex(t.v[k], t.v[k + 1], t.v[k + 2]).setColor(t.v[k + 5], t.v[k + 6], t.v[k + 7], t.v[k + 8]).setUv(t.v[k + 3], t.v[k + 4]).setUv1(0, 0).setUv2(0, 0)
                    .setNormal(t.v[k + 9], t.v[k + 10], t.v[k + 11]);
        }
        MeshData mesh = b.build();
        if (mesh != null) {
            RenderSystem.setShader(() -> sh);
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }
    }

    public static void flush(boolean glow) {
        if (glow) {
            return;
        }
        frame++;
        ShaderInstance sh = FxShaders.wood;
        if (sh == null || broken || QUEUE.isEmpty() && IMM.isEmpty()) {
            QUEUE.clear();
            IMM.clear();
            collect();
            return;
        }
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            Matrix4f view = RenderSystem.getModelViewMatrix(), proj = RenderSystem.getProjectionMatrix();
            sh.safeGetUniform("Light").set(LX, LY, LZ);
            sh.safeGetUniform("Amb").set(0.58f);
            for (Cmd c : QUEUE) {
                VertexBuffer vb = upload(c.g());
                if (vb == null) {
                    continue;
                }
                Mat m = c.m();
                sh.safeGetUniform("Model").set(c.model());
                sh.safeGetUniform("Mode").set((float) m.mode);
                sh.safeGetUniform("Grow").set(c.grow());
                sh.safeGetUniform("Tip").set(m.tip);
                sh.safeGetUniform("Bright").set(c.bright());
                sh.safeGetUniform("Seed").set(m.seed);
                sh.safeGetUniform("Fresh").set(m.fresh);
                sh.safeGetUniform("Dis").set(c.dis());
                sh.safeGetUniform("Gr").set(m.gr);
                sh.safeGetUniform("Fine").set(m.fine);
                sh.safeGetUniform("Ring").set(m.ring);
                sh.safeGetUniform("Cav").set(m.cav);
                sh.safeGetUniform("Grain").set(m.grain);
                sh.safeGetUniform("Wood").set(m.wood[0], m.wood[1], m.wood[2]);
                sh.safeGetUniform("Dark").set(m.dark[0], m.dark[1], m.dark[2]);
                sh.safeGetUniform("Tint").set(m.tint[0], m.tint[1], m.tint[2]);
                Rail r = c.rail();
                sh.safeGetUniform("Skin").set(r != null ? 1f : 0f);
                if (r != null) {
                    sh.safeGetUniform("RailN").set((float) r.n);
                    sh.safeGetUniform("BodyLen").set(c.bodyLen());
                    RenderSystem.setShaderTexture(0, r.tex);
                }
                vb.bind();
                vb.drawWithShader(view, proj, sh);
                VertexBuffer.unbind();
            }
            for (Imm im : IMM) {
                if (im.opaque()) {
                    drawImm(sh, im);
                }
            }
            // the see-through things last (ground warnings, motes): premultiplied, no depth writes
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(false);
            for (Imm im : IMM) {
                if (!im.opaque()) {
                    drawImm(sh, im);
                }
            }
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        } catch (RuntimeException e) {
            broken = true;
            BattlegroundsMod.LOGGER.error("Hashirama's wood failed to draw; it is off until restart", e);
        } finally {
            QUEUE.clear();
            IMM.clear();
            RenderSystem.enableCull();
            GL11.glCullFace(GL11.GL_BACK);
            collect();
        }
    }

    // Meshes not drawn for 3 s give their buffers back.
    private static void collect() {
        Iterator<Map.Entry<WoodGeo.Geo, int[]>> it = LIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<WoodGeo.Geo, int[]> e = it.next();
            if (frame - e.getValue()[0] > 180) {
                if (e.getKey().gpu instanceof VertexBuffer vb) {
                    vb.close();
                }
                e.getKey().gpu = null;
                e.getKey().dirty = true;
                it.remove();
            }
        }
    }

    private static VertexBuffer upload(WoodGeo.Geo g) {
        LIVE.computeIfAbsent(g, k -> new int[1])[0] = frame;
        if (g.gpu instanceof VertexBuffer vb && !g.dirty) {
            return vb;
        }
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
        boolean tube = g.c != null && !g.surf;
        for (int t = 0; t < g.idx.length; t++) {
            int i = g.idx[t];
            float x = g.p[i * 3], y = g.p[i * 3 + 1], z = g.p[i * 3 + 2];
            b.addVertex(x, y, z);
            if (tube) {
                int u = Math.max(0, Math.min(65535, Math.round(g.u[i] * 0.5f * 65535)));
                b.setColor((u >> 8) & 255, u & 255, 0, 255);
                b.setUv(g.b[i * 2], g.b[i * 2 + 1]);
                b.setUv1(sh(x - g.c[i * 3]), sh(y - g.c[i * 3 + 1]));
                b.setUv2(sh(z - g.c[i * 3 + 2]), 0);
            } else if (g.k != null) {
                b.setColor(c8(g.k[i * 3]), c8(g.k[i * 3 + 1]), c8(g.k[i * 3 + 2]), 255);
                b.setUv(0, 0);
                b.setUv1(0, 0);
                b.setUv2(0, 0);
            } else {
                float ao = g.ao != null ? g.ao[i] : 1;
                b.setColor(c8(g.d[i * 3] * 0.5f + 0.5f), c8(g.d[i * 3 + 1] * 0.5f + 0.5f), c8(g.d[i * 3 + 2] * 0.5f + 0.5f), c8(ao));
                b.setUv(0, 0);
                b.setUv1(0, 0);
                b.setUv2(0, 0);
            }
            b.setNormal(g.n[i * 3], g.n[i * 3 + 1], g.n[i * 3 + 2]);
        }
        MeshData mesh = b.build();
        if (mesh == null) {
            return null;
        }
        VertexBuffer vb = g.gpu instanceof VertexBuffer old ? old : new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        g.gpu = vb;
        g.dirty = false;
        return vb;
    }

    private static int sh(float v) {
        return Math.max(-32767, Math.min(32767, Math.round(v * 1024)));
    }

    private static int c8(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }
}
