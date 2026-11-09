package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Freeza's Supernova landing (approved preview forja/freeza-supernova, "Realista", Stark 2026-11-09): the fire dome
// of radius 36 raymarched in a box (souls_nova_vol). DbFx.supernova says each frame which domes to draw (camera-relative
// impact point on the ground and age in ticks); FxWorld draws them at the end of the level, like the Fuga's fire.
public final class NovaRenderer {
    // The dome's radius at full size (the server's hitbox is the same shape: Freeza.NOVA_R).
    public static final float RMAX = 36;

    private record Want(Vector3f e, Vec3 world, float age, float seed) {
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

    static void want(Vector3f e, Vec3 world, float age, float seed) {
        if (WANTS.size() > 16) {
            WANTS.clear();
        }
        if (age >= 0 && age < 190) {
            WANTS.add(new Want(new Vector3f(e), world, age, seed));
        }
    }

    static void render(Vec3 camera) {
        if (WANTS.isEmpty()) {
            return;
        }
        tremors(camera);
        ShaderInstance sh = FxShaders.novaVol;
        if (sh == null) {
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
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        WANTS.clear();
    }

    // The three tremors of the preview: each wave's front lifts the ground's top blocks (each its own height and tilt)
    // and lets them down again. Drawn as copies of the real top blocks rising out of their columns, so nothing on the
    // server moves and no hole is left behind (the falling blocks of the first port left holes and lagged).
    private static void tremors(Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        PoseStack pose = new PoseStack();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int drawn = 0;
        for (Want w : WANTS) {
            float t = w.age() / 20f;
            Map<Long, float[]> lift = new HashMap<>();
            for (int k = 0; k < 3; k++) {
                float tw = t - 0.1f - k * 0.8f;
                if (tw <= 0) {
                    continue;
                }
                float pr = tw * (27 - k * 5), width = 2.3f + k * 0.7f, amp = 1.3f - k * 0.32f;
                float r0 = Math.max(9, pr - width * 2.2f), r1 = Math.min(50, pr + width * 2.2f);
                for (float rr = r0; rr <= r1; rr += 0.7f) {
                    float b = (float) Math.exp(-Math.pow((rr - pr) / width, 2)) * amp * (1 - smooth((rr - 34) / 16));
                    if (b < 0.05f) {
                        continue;
                    }
                    float step = 0.7f / rr;
                    for (float a = 0; a < Mth.TWO_PI; a += step) {
                        int x = Mth.floor(w.world().x + Mth.cos(a) * rr), z = Mth.floor(w.world().z + Mth.sin(a) * rr);
                        float[] v = lift.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), q -> new float[3]);
                        float rx = x + 0.5f - (float) w.world().x, rz = z + 0.5f - (float) w.world().z;
                        float bb = (float) Math.exp(-Math.pow((Mth.sqrt(rx * rx + rz * rz) - pr) / width, 2)) * amp * (1 - smooth((Mth.sqrt(rx * rx + rz * rz) - 34) / 16));
                        if (bb > v[2]) {
                            v[2] = bb;
                        }
                    }
                }
            }
            for (Map.Entry<Long, float[]> en : lift.entrySet()) {
                if (drawn > 3500) {
                    break;
                }
                int x = (int) (en.getKey() >> 32), z = (int) (long) en.getKey();
                float b = en.getValue()[2];
                float s = hash(x, z, 1), s2 = hash(x, z, 2), s3 = hash(x, z, 3);
                float dy = b * (0.35f + 1.4f * s3);
                if (dy < 0.08f) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                at.set(x, y, z);
                BlockState st = level.getBlockState(at);
                if (!st.isAir() && st.canBeReplaced()) {
                    at.move(0, -1, 0);
                    st = level.getBlockState(at);
                }
                if (Math.abs(at.getY() - w.world().y) > 6 || st.isAir() || st.getRenderShape() != RenderShape.MODEL || !st.getFluidState().isEmpty()) {
                    continue;
                }
                float ang = b * 0.7f * (s3 - 0.5f) * 1.1f;
                Vector3f axis = new Vector3f(s - 0.5f, 0, s2 - 0.5f);
                if (axis.lengthSquared() < 1.0E-4f) {
                    axis.set(1, 0, 0);
                }
                axis.normalize();
                pose.pushPose();
                pose.translate(x + 0.5 - camera.x, at.getY() + 1 + dy - camera.y, z + 0.5 - camera.z);
                pose.mulPose(new Quaternionf().rotationAxis(ang, axis));
                pose.translate(-0.5, -1, -0.5);
                int light = LevelRenderer.getLightColor(level, at.above());
                blocks.renderSingleBlock(st, pose, source, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
                drawn++;
            }
        }
        source.endBatch();
    }

    private static float hash(int x, int z, int k) {
        long h = x * 374761393L + z * 668265263L + k * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
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
