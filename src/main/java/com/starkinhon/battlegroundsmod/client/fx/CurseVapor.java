package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

// The vapour off a curse being exorcised (approved preview tools/mobs/jjmobs, JJFX.vapor, Stark 2026-11-14: "like the
// Katon's death, without the generic particles and without fire"): dark grey-indigo wisps rising from the edge the body
// evaporates along (souls_ash style 4), raymarched in a box round the lying body (souls_jj_vapor). Drawn by AshDeath
// right after the body, with the same progress, so the edge and the vapour always agree.
final class CurseVapor {
    private CurseVapor() {
    }

    // The height of the body as it lies when it evaporates (every curse is on the ground by then): the shader's edge
    // runs over it from the top down.
    static float lyingHeight(float bbHeight) {
        return Math.max(0.35f, Math.min(1.3f, bbHeight * 0.45f));
    }

    // feet: the body's feet, camera-relative; width/height: its box; progress: the exorcism 0..1 (souls_ash style 4).
    static void draw(Vec3 feet, float width, float height, float progress, double seconds) {
        ShaderInstance sh = FxShaders.jjVapor;
        float fr = Math.max(0, Math.min(1, (progress - 0.22f) / 0.78f));
        if (sh == null || fr <= 0) {
            return;
        }
        float k = Math.min(1, fr / 0.06f) * (1 - Math.max(0, Math.min(1, (fr - 0.88f) / 0.12f)));
        if (k <= 0) {
            return;
        }
        float lying = lyingHeight(height);
        // lying down, the body is about as long as it stood tall, in a direction we don't know: a round footprint
        float half = Math.max(width, height) * 0.5f + 0.05f;
        float front = lying * Math.max(0, Math.min(1, 1.08f - fr * 1.4f));
        float pad = half + 0.5f;
        float[] mn = {(float) feet.x - pad, (float) feet.y - 0.1f, (float) feet.z - pad};
        float[] mx = {(float) feet.x + pad, (float) feet.y + lying + 1.0f, (float) feet.z + pad};
        sh.safeGetUniform("Lo").set(mn[0], mn[1], mn[2]);
        sh.safeGetUniform("Hi").set(mx[0], mx[1], mx[2]);
        sh.safeGetUniform("C").set((float) feet.x, (float) feet.y, (float) feet.z);
        sh.safeGetUniform("Half").set(half, half);
        sh.safeGetUniform("Front").set(front);
        sh.safeGetUniform("K").set(k);
        sh.safeGetUniform("Time").set((float) (seconds % 1000.0));
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        box(sh, mn, mx);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    // The faces of the box that look away from the camera (at 0): the ray goes from the eye through them.
    private static void box(ShaderInstance sh, float[] mn, float[] mx) {
        float[][] v = new float[8][];
        for (int i = 0; i < 8; i++) {
            v[i] = new float[] {(i & 1) == 0 ? mn[0] : mx[0], (i & 2) == 0 ? mn[1] : mx[1], (i & 4) == 0 ? mn[2] : mx[2]};
        }
        float cx = (mn[0] + mx[0]) / 2, cy = (mn[1] + mx[1]) / 2, cz = (mn[2] + mx[2]) / 2;
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int count = 0;
        for (int[] face : faces) {
            float fx = 0, fy = 0, fz = 0;
            for (int k : face) {
                fx += v[k][0] / 4;
                fy += v[k][1] / 4;
                fz += v[k][2] / 4;
            }
            if ((fx - cx) * fx + (fy - cy) * fy + (fz - cz) * fz <= 0) {
                continue;
            }
            for (int k : face) {
                b.addVertex(v[k][0], v[k][1], v[k][2]);
            }
            count++;
        }
        MeshData mesh = b.build();
        if (count == 0 || mesh == null) {
            if (mesh != null) {
                mesh.close();
            }
            return;
        }
        RenderSystem.setShader(() -> sh);
        BufferUploader.drawWithShader(mesh);
    }
}
