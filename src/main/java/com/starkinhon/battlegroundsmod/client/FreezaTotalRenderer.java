package com.starkinhon.battlegroundsmod.client;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.FreezaTotalEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// Freeza Poder Total: the final form's skin, the 100% skin (muscle lines and veins) with the body swelling as the
// muscles come (FreezaTotalEntity.bulk), and his tail: the preview's chain of 12 segments, each turned on the one
// before (the page's tailPose: alive, swaying; the whip sweeps it round for 0.32 s), white with a purple tip.
public class FreezaTotalRenderer extends SoulBossRenderer<FreezaTotalEntity> {
    private static final ResourceLocation FULL = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/freeza_total_100.png");

    public FreezaTotalRenderer(EntityRendererProvider.Context context) {
        super(context, "freeza_total", false);
        addLayer(new Tail(this));
    }

    @Override
    public ResourceLocation getTextureLocation(FreezaTotalEntity entity) {
        return entity.bulk() > 0.5f ? FULL : super.getTextureLocation(entity);
    }

    @Override
    protected void scale(FreezaTotalEntity entity, PoseStack pose, float partial) {
        super.scale(entity, pose, partial);
        // the page's freezaM is 1.08 tall; at 100% the chest and the arms get wider
        float k = entity.bulk();
        pose.scale(1.04f + 0.12f * k, 1.04f + 0.04f * k, 1.04f + 0.1f * k);
    }

    static final class Tail extends RenderLayer<FreezaTotalEntity, PlayerModel<FreezaTotalEntity>> {
        private final LivingEntityRenderer<FreezaTotalEntity, PlayerModel<FreezaTotalEntity>> owner;

        Tail(LivingEntityRenderer<FreezaTotalEntity, PlayerModel<FreezaTotalEntity>> parent) {
            super(parent);
            this.owner = parent;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, FreezaTotalEntity e, float limbSwing, float limbSwingAmount, float partial,
                float age, float headYaw, float headPitch) {
            if (e.isInvisible()) {
                return;
            }
            VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(owner.getTextureLocation(e)));
            float t = age / 20f;
            // the whip: 0..1 over 0.32 s
            float since = (e.level().getGameTime() - e.whipAt() + partial) / 20f;
            float whip = since >= 0 && since < 0.32f ? since / 0.32f : 0;
            pose.pushPose();
            // the body's own turn (the tail hangs from the hips)
            getParentModel().body.translateAndRotate(pose);
            PoseStack.Pose last = pose.last();
            // In the page's frame: blocks, y up, the back is +z; the model's is pixels with y down.
            Vector3f p = new Vector3f(0, 0.86f, 0.13f);
            Matrix3f frame = new Matrix3f();
            if (whip > 0) {
                frame.rotateX(-0.5f * Mth.sin(whip * Mth.PI));
            }
            for (int i = 0; i < 12; i++) {
                float u = i / 11f;
                float idle = Mth.sin(t * 2.2f - i * 0.5f) * 0.12f;
                float w = whip > 0 ? Mth.sin(Mth.clamp(whip * 1.3f - u * 0.3f, 0, 1) * Mth.PI) * 0.55f : 0;
                float rx = i == 0 ? 0.55f : (i < 5 ? 0.14f : -0.22f) + Mth.sin(t * 1.6f - i * 0.4f) * 0.05f;
                // THREE's Euler XYZ on the segment: this one's turn after the one before
                frame.rotateX(rx).rotateY(idle + w);
                float s = 0.2f - i * 0.011f, L = 0.2f;
                Vector3f z = frame.transform(new Vector3f(0, 0, 1));
                Vector3f x = frame.transform(new Vector3f(1, 0, 0));
                Vector3f y = frame.transform(new Vector3f(0, 1, 0));
                box(out, last, p, x, y, z, s, L + 0.02f, light, i > 9);
                p.add(new Vector3f(z).mul(L));
            }
            pose.popPose();
        }

        // A box from p along z (length len), s wide; u/v from the skin's tail corner.
        private static void box(VertexConsumer out, PoseStack.Pose pose, Vector3f p, Vector3f x, Vector3f y, Vector3f z, float s, float len, int light, boolean tip) {
            float h = s / 2;
            Vector3f[] c = new Vector3f[8];
            for (int k = 0; k < 8; k++) {
                float sx = (k & 1) == 0 ? -h : h, sy = (k & 2) == 0 ? -h : h, sz = (k & 4) == 0 ? 0 : len;
                c[k] = new Vector3f(p).add(new Vector3f(x).mul(sx)).add(new Vector3f(y).mul(sy)).add(new Vector3f(z).mul(sz));
            }
            float v0 = tip ? 24.5f / 64 : 16.5f / 64, v1 = tip ? 31.5f / 64 : 23.5f / 64, u0 = 56.5f / 64, u1 = 63.5f / 64;
            // the six faces (outward normals)
            quad(out, pose, c[0], c[2], c[3], c[1], new Vector3f(z).negate(), u0, v0, u1, v1, light);
            quad(out, pose, c[4], c[5], c[7], c[6], z, u0, v0, u1, v1, light);
            quad(out, pose, c[0], c[1], c[5], c[4], new Vector3f(y).negate(), u0, v0, u1, v1, light);
            quad(out, pose, c[2], c[6], c[7], c[3], y, u0, v0, u1, v1, light);
            quad(out, pose, c[0], c[4], c[6], c[2], new Vector3f(x).negate(), u0, v0, u1, v1, light);
            quad(out, pose, c[1], c[3], c[7], c[5], x, u0, v0, u1, v1, light);
        }

        private static void quad(VertexConsumer out, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f n, float u0, float v0, float u1,
                float v1, int light) {
            vertex(out, pose, a, n, u0, v0, light);
            vertex(out, pose, b, n, u1, v0, light);
            vertex(out, pose, c, n, u1, v1, light);
            vertex(out, pose, d, n, u0, v1, light);
        }

        // page frame (blocks, y up, back +z) to the body part's frame (pixels/16, y down, back +z)
        private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vector3f p, Vector3f n, float u, float v, int light) {
            float mx = p.x, my = (1.5f - p.y), mz = p.z;
            out.addVertex(pose, mx, my, mz).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(pose, n.x, -n.y, n.z);
        }
    }
}
