package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

// Saitama's serious face (Stark 2026-11-20, approved on the preview): in the Modo Serio his eyes glow (a hot core on each
// eye and a soft halo round it) and the top of the face falls into the manga's shadow. Each serious move makes them
// flare (OpmFx.EYES). Drawn on the head as it is really posed, players and the boss alike.
public class OpmEyesLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/outfit/white.png");

    public OpmEyesLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, T entity, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !getParentModel().head.visible) {
            return;
        }
        double now = ScreenFx.now();
        Double flashAt = OpmFx.EYES.get(entity.getId());
        float flare = flashAt == null ? 0 : (float) Math.max(0, 1 - (now - flashAt) / 30.0);
        boolean serious = OpmFx.isSerious(entity);
        if (!serious && flare <= 0.01f) {
            return;
        }
        float on = serious ? 1 : flare;
        // a slow breath in the glow, and the flare on top
        float glow = on * (0.8f + 0.2f * Mth.sin((float) now * 0.25f)) + flare * 0.8f;
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        PoseStack.Pose last = pose.last();
        // the shadow over the brow and the eyes' sockets (drawn under the glow)
        VertexConsumer shade = buffer.getBuffer(RenderType.entityTranslucent(WHITE));
        quad(shade, last, -4.02f, -8.02f, 4.02f, -4.6f, -4.03f, 0.05f, 0.04f, 0.06f, 0.42f * on, light);
        quad(shade, last, -4.02f, -4.6f, 4.02f, -3.9f, -4.03f, 0.05f, 0.04f, 0.06f, 0.2f * on, light);
        // the eyes: one row, the two columns of each eye (Steve's face: x -3..-1 and 1..3, row 4 of 8)
        VertexConsumer eyes = buffer.getBuffer(RenderType.eyes(WHITE));
        float r = 1f, g = 0.95f, b = 0.84f;
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side < 0 ? -3 : 1, x1 = x0 + 2;
            float h = 0.9f + 1.4f * flare;
            // halo first (dim, wide), then the core (bright, the eye's own size)
            quad(eyes, last, x0 - h, -4 - h * 0.6f, x1 + h, -3 + h * 0.6f, -4.05f, r * 0.35f * glow, g * 0.32f * glow, b * 0.25f * glow, 1, LightTexture.FULL_BRIGHT);
            quad(eyes, last, x0, -4, x1, -3, -4.06f, r * glow, g * glow, b * glow, 1, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    // A quad facing out of the face (-z), in head pixels.
    private static void quad(VertexConsumer out, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z, float r, float g, float b, float a,
            int light) {
        float s = 1 / 16f;
        r = Mth.clamp(r, 0, 1);
        g = Mth.clamp(g, 0, 1);
        b = Mth.clamp(b, 0, 1);
        out.addVertex(pose, x0 * s, y1 * s, z * s).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        out.addVertex(pose, x1 * s, y1 * s, z * s).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        out.addVertex(pose, x1 * s, y0 * s, z * s).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        out.addVertex(pose, x0 * s, y0 * s, z * s).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        if (light == LightTexture.FULL_BRIGHT) {
            // the glow is drawn with culling on: the other winding too, whichever way the face is turned
            out.addVertex(pose, x0 * s, y0 * s, z * s).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
            out.addVertex(pose, x1 * s, y0 * s, z * s).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
            out.addVertex(pose, x1 * s, y1 * s, z * s).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
            out.addVertex(pose, x0 * s, y1 * s, z * s).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        }
    }
}
