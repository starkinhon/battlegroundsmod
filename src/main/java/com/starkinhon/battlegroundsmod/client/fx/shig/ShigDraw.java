package com.starkinhon.battlegroundsmod.client.fx.shig;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;

// Small immediate meshes for Shigaraki's effects, drawn with their own shaders (the preview's ShaderMaterials).
// Vertices are camera-relative, in NEW_ENTITY's layout: the shaders read UV0, UV1, UV2 and Color as the extra numbers
// each effect needs (age, seed, where along a fibre...), see each shader.
public final class ShigDraw {
    public static final int OPAQUE = 0, ALPHA = 1, ADD = 2;

    private ShigDraw() {
    }

    public static BufferBuilder begin() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
    }

    public static BufferBuilder beginPos() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
    }

    // One vertex: position, colour (0..1), uv0 (floats), uv1 and uv2 (whole numbers), normal.
    public static void v(BufferBuilder b, float x, float y, float z, float r, float g, float bl, float a, float u, float vv, int u1, int v1, int u2,
            int v2, float nx, float ny, float nz) {
        b.addVertex(x, y, z).setColor(r, g, bl, a).setUv(u, vv).setUv1(u1, v1).setUv2(u2, v2).setNormal(nx, ny, nz);
    }

    // Draws what was built with the shader. mode: OPAQUE writes depth; ALPHA and ADD don't. cull: back faces hidden;
    // front: only back faces (the outlines).
    public static void draw(BufferBuilder b, ShaderInstance sh, int mode, boolean cull, boolean front) {
        MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        if (sh == null) {
            mesh.close();
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        if (mode == OPAQUE) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        } else {
            RenderSystem.enableBlend();
            if (mode == ADD) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            } else {
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }
            RenderSystem.depthMask(false);
        }
        if (cull) {
            RenderSystem.enableCull();
            if (front) {
                org.lwjgl.opengl.GL11.glCullFace(org.lwjgl.opengl.GL11.GL_FRONT);
            }
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.setShader(() -> sh);
        BufferUploader.drawWithShader(mesh);
        if (front) {
            org.lwjgl.opengl.GL11.glCullFace(org.lwjgl.opengl.GL11.GL_BACK);
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ---------- shapes ----------

    public interface Attr {
        // the extra numbers of one vertex of a tube or sphere: u along (0..1), v around (0..1)
        void put(BufferBuilder b, float x, float y, float z, float nx, float ny, float nz, float u, float v);
    }

    // A tube through pts (a centripetal Catmull-Rom like three.js' default curve), with parallel-transported frames.
    // radius(u) along it.
    public static void tube(BufferBuilder b, float[][] pts, int tubular, int radial, java.util.function.DoubleUnaryOperator radius, Attr attr) {
        float[][] c = new float[tubular + 1][3];
        for (int i = 0; i <= tubular; i++) {
            catmull(pts, i / (float) tubular, c[i]);
        }
        float[][] N = new float[tubular + 1][], B = new float[tubular + 1][];
        float[] T = new float[3];
        for (int i = 0; i <= tubular; i++) {
            float[] a = c[Math.min(i + 1, tubular)], bb = c[Math.max(i - 1, 0)];
            T[0] = a[0] - bb[0];
            T[1] = a[1] - bb[1];
            T[2] = a[2] - bb[2];
            norm(T);
            float[] n;
            if (i == 0) {
                float[] ax = Math.abs(T[1]) < 0.9f ? new float[] {0, 1, 0} : new float[] {1, 0, 0};
                n = cross(ax, T);
            } else {
                float[] p = N[i - 1];
                float d = p[0] * T[0] + p[1] * T[1] + p[2] * T[2];
                n = new float[] {p[0] - T[0] * d, p[1] - T[1] * d, p[2] - T[2] * d};
            }
            norm(n);
            N[i] = n;
            float[] bn = cross(T, n);
            norm(bn);
            B[i] = bn;
        }
        float[][][] ring = new float[tubular + 1][radial + 1][];
        for (int i = 0; i <= tubular; i++) {
            float r = (float) radius.applyAsDouble(i / (double) tubular);
            for (int j = 0; j <= radial; j++) {
                double an = j / (double) radial * Math.PI * 2;
                float cs = (float) Math.cos(an), sn = (float) -Math.sin(an);
                float nx = cs * N[i][0] + sn * B[i][0], ny = cs * N[i][1] + sn * B[i][1], nz = cs * N[i][2] + sn * B[i][2];
                ring[i][j] = new float[] {c[i][0] + nx * r, c[i][1] + ny * r, c[i][2] + nz * r, nx, ny, nz};
            }
        }
        for (int i = 0; i < tubular; i++) {
            for (int j = 0; j < radial; j++) {
                quad(b, attr, ring[i][j], ring[i + 1][j], ring[i + 1][j + 1], ring[i][j + 1], i / (float) tubular, (i + 1) / (float) tubular,
                        j / (float) radial, (j + 1) / (float) radial);
            }
        }
    }

    private static void quad(BufferBuilder b, Attr attr, float[] p00, float[] p10, float[] p11, float[] p01, float u0, float u1, float v0, float v1) {
        put(b, attr, p00, u0, v0);
        put(b, attr, p10, u1, v0);
        put(b, attr, p01, u0, v1);
        put(b, attr, p10, u1, v0);
        put(b, attr, p11, u1, v1);
        put(b, attr, p01, u0, v1);
    }

    private static void put(BufferBuilder b, Attr attr, float[] p, float u, float v) {
        attr.put(b, p[0], p[1], p[2], p[3], p[4], p[5], u, v);
    }

    // A UV sphere of radius 1 scaled by (sx, sy, sz) at (cx, cy, cz). u around (0..1), v from the top (0..1).
    public static void sphere(BufferBuilder b, float cx, float cy, float cz, float sx, float sy, float sz, int ws, int hs, Attr attr) {
        float[][][] g = new float[hs + 1][ws + 1][];
        for (int i = 0; i <= hs; i++) {
            double th = i / (double) hs * Math.PI;
            for (int j = 0; j <= ws; j++) {
                double ph = j / (double) ws * Math.PI * 2;
                float nx = (float) (-Math.cos(ph) * Math.sin(th)), ny = (float) Math.cos(th), nz = (float) (Math.sin(ph) * Math.sin(th));
                float[] n = {nx / sx, ny / sy, nz / sz};
                norm(n);
                g[i][j] = new float[] {cx + nx * sx, cy + ny * sy, cz + nz * sz, n[0], n[1], n[2]};
            }
        }
        for (int i = 0; i < hs; i++) {
            for (int j = 0; j < ws; j++) {
                quad(b, attr, g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1], j / (float) ws, j / (float) ws, i / (float) hs, (i + 1) / (float) hs);
            }
        }
    }

    // three.js CatmullRomCurve3 'centripetal' at t (0..1), not closed.
    public static void catmull(float[][] pts, float t, float[] out) {
        int l = pts.length;
        if (l == 1) {
            System.arraycopy(pts[0], 0, out, 0, 3);
            return;
        }
        float p = (l - 1) * t;
        int ip = (int) Math.floor(p);
        float w = p - ip;
        if (ip >= l - 1) {
            ip = l - 2;
            w = 1;
        }
        float[] p0 = ip > 0 ? pts[ip - 1] : ext(pts[0], pts[1]);
        float[] p1 = pts[ip], p2 = pts[ip + 1];
        float[] p3 = ip + 2 < l ? pts[ip + 2] : ext(pts[l - 1], pts[l - 2]);
        float dt0 = (float) Math.pow(d2(p0, p1), 0.25), dt1 = (float) Math.pow(d2(p1, p2), 0.25), dt2 = (float) Math.pow(d2(p2, p3), 0.25);
        if (dt1 < 1e-4f) {
            dt1 = 1;
        }
        if (dt0 < 1e-4f) {
            dt0 = dt1;
        }
        if (dt2 < 1e-4f) {
            dt2 = dt1;
        }
        for (int k = 0; k < 3; k++) {
            float x0 = p0[k], x1 = p1[k], x2 = p2[k], x3 = p3[k];
            float t1 = ((x1 - x0) / dt0 - (x2 - x0) / (dt0 + dt1) + (x2 - x1) / dt1) * dt1;
            float t2 = ((x2 - x1) / dt1 - (x3 - x1) / (dt1 + dt2) + (x3 - x2) / dt2) * dt1;
            out[k] = x1 + t1 * w + (-3 * x1 + 3 * x2 - 2 * t1 - t2) * w * w + (2 * x1 - 2 * x2 + t1 + t2) * w * w * w;
        }
    }

    private static float[] ext(float[] a, float[] b) {
        return new float[] {2 * a[0] - b[0], 2 * a[1] - b[1], 2 * a[2] - b[2]};
    }

    private static float d2(float[] a, float[] b) {
        float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return x * x + y * y + z * z;
    }

    static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static void norm(float[] a) {
        float n = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (n > 1e-8f) {
            a[0] /= n;
            a[1] /= n;
            a[2] /= n;
        }
    }

    public static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    public static float easeOut3(float x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    public static float clamp(float x, float a, float b) {
        return Math.max(a, Math.min(b, x));
    }

    // The camera's position folded into 0..512, so the shaders' world noise stays put without losing precision far out.
    public static float[] origin(net.minecraft.world.phys.Vec3 camera) {
        return new float[] {(float) (camera.x - Math.floor(camera.x / 512) * 512), (float) (camera.y - Math.floor(camera.y / 512) * 512),
                (float) (camera.z - Math.floor(camera.z / 512) * 512)};
    }
}
