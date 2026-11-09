package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;

// Freeza's Supernova landing (approved preview forja/freeza-supernova, "Realista", Stark 2026-11-09): the fire dome
// of radius 36 raymarched in a box (souls_nova_vol). DbFx.supernova says each frame which domes to draw (camera-relative
// impact point on the ground and age in ticks); FxWorld draws them at the end of the level, like the Fuga's fire.
public final class NovaRenderer {
    // The dome's radius at full size (the server's hitbox is the same shape: Freeza.NOVA_R).
    public static final float RMAX = 36;

    private record Want(Vector3f e, float age, float seed) {
    }

    private static final List<Want> WANTS = new ArrayList<>();

    private NovaRenderer() {
    }

    // The dome's radius and height at an age in ticks (the preview's seconds times 20).
    public static float radius(float age) {
        float t = age / 20f;
        float u = 1 - Math.min(1, Math.max(0, t / 1.5f));
        return 1 + (RMAX - 2.5f) * (1 - u * u * u * u) + 1.5f * Math.min(1, Math.max(0, (t - 1.5f) / 6));
    }

    static void want(Vector3f e, float age, float seed) {
        if (WANTS.size() > 16) {
            WANTS.clear();
        }
        if (age >= 0 && age < 190) {
            WANTS.add(new Want(new Vector3f(e), age, seed));
        }
    }

    static void render() {
        if (WANTS.isEmpty()) {
            return;
        }
        ShaderInstance sh = FxShaders.novaVol;
        if (sh == null) {
            WANTS.clear();
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        for (Want w : WANTS) {
            float t = w.age() / 20f;
            float R = radius(w.age()), H = R * 0.5f;
            float life = Math.min(1, t / 9);
            // The flow's phase: the integral of a speed that starts explosive and calms down (never clock x speed).
            float phase = 0.25f * t + 2.6f / 1.1f * (1 - (float) Math.exp(-1.1f * t));
            float fade = 1 - smooth((t - 8) / 1.5f);
            if (fade <= 0) {
                continue;
            }
            Vector3f e = w.e();
            float pad = R * 1.3f;
            float[] mn = {e.x - pad, e.y - 0.5f, e.z - pad};
            float[] mx = {e.x + pad, e.y + H * 1.45f + life * H * 0.6f + 2, e.z + pad};
            sh.safeGetUniform("E").set(e.x, e.y, e.z);
            sh.safeGetUniform("R").set(R);
            sh.safeGetUniform("H").set(H);
            sh.safeGetUniform("Life").set(life);
            sh.safeGetUniform("Phase").set(phase);
            sh.safeGetUniform("Seed").set(w.seed());
            sh.safeGetUniform("Fade").set(fade);
            sh.safeGetUniform("Bmin").set(mn[0], mn[1], mn[2]);
            sh.safeGetUniform("Bmax").set(mx[0], mx[1], mx[2]);
            box(sh, mn, mx);
        }
        WANTS.clear();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    // The faces of the box (camera-relative) that face away from the camera: the ray through each of their pixels
    // crosses the whole box in front of it. With the camera inside, that is every face.
    private static void box(ShaderInstance sh, float[] mn, float[] mx) {
        Vector3f[] v = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            v[i] = new Vector3f((i & 1) == 0 ? mn[0] : mx[0], (i & 2) == 0 ? mn[1] : mx[1], (i & 4) == 0 ? mn[2] : mx[2]);
        }
        Vector3f centre = new Vector3f((mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2);
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int count = 0;
        for (int[] face : faces) {
            Vector3f fc = new Vector3f(v[face[0]]).add(v[face[1]]).add(v[face[2]]).add(v[face[3]]).div(4);
            Vector3f out = new Vector3f(fc).sub(centre);
            if (out.dot(fc) <= 0) {
                continue;
            }
            for (int k : face) {
                b.addVertex(v[k].x, v[k].y, v[k].z);
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

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
