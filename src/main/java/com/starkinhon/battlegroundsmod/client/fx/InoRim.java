// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.compat.IrisCompat;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

// Ino's mind aura, the lilac outline on the body (forja/ino-kit/ino-aura.js: each part copied 1.1x bigger with a rim
// shader, uA = 0.8 * k). Her own body in the Teatro Mental and the body she pilots. InoFx touches it every frame it
// draws the aura; it fades out on its own a few frames after the last touch. One more draw of the model, nothing else.
public final class InoRim<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    // entity id -> {k, game time (ticks + partial) of the last touch}
    private static final Map<Integer, double[]> ON = new HashMap<>();
    private static final float FADE = 3;

    public InoRim(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    static void touch(int entityId, float k, double time) {
        ON.put(entityId, new double[] {k, time});
    }

    public static void clear() {
        ON.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        double[] h = ON.get(entity.getId());
        if (h == null || entity.isInvisible() || FxShaders.inoRim == null || IrisCompat.inShadowPass()) {
            return;
        }
        double now = entity.level().getGameTime() + partialTick;
        float fade = 1 - (float) Math.max(0, now - h[1]) / FADE;
        if (fade <= 0 || now < h[1] - 40) {
            ON.remove(entity.getId());
            return;
        }
        float a = 0.8f * (float) h[0] * fade;
        if (a <= 0.003f) {
            return;
        }
        poseStack.pushPose();
        // 1.1x round the body's middle (the model's y grows downward, the waist at 12 px)
        poseStack.translate(0, 0.75, 0);
        poseStack.scale(1.1f, 1.1f, 1.1f);
        poseStack.translate(0, -0.75, 0);
        int alpha = Math.round(Math.min(1, a) * 255);
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(FxRenderTypes.INO_RIM), 0xF000F0, OverlayTexture.NO_OVERLAY, alpha << 24 | 0xFFFFFF);
        poseStack.popPose();
    }
}
