// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

// Freeza's Telecinese viva and Death Psycho Bomb (the previews Stark approved 2026-11-27, forja/freeza-kit/telec-viva.js
// and freeza-novas.js): no wire and no dome round the one held, only a purple outline that trembles over the body. The
// body is drawn once more a little bigger in a swirling energy shell (the charged creeper's), tinted purple; strength
// above 1 (the Psycho Bomb swelling) brightens it and turns it to pink-white. DbFx.telekinesis touches it every frame
// it draws the hold; it fades out on its own a few frames after the last touch.
public final class TeleRim<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation SWIRL = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    // entity id -> {strength, game time (ticks + partial) of the last touch}
    private static final Map<Integer, double[]> HELD = new HashMap<>();
    private static final float FADE = 4;

    public TeleRim(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void touch(int entityId, float strength, double time) {
        HELD.put(entityId, new double[] {strength, time});
    }

    public static void clear() {
        HELD.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        double[] h = HELD.get(entity.getId());
        if (h == null || entity.isInvisible()) {
            return;
        }
        double now = entity.level().getGameTime() + partialTick;
        float fade = 1 - (float) Math.max(0, now - h[1]) / FADE;
        if (fade <= 0 || now < h[1] - 40) {
            HELD.remove(entity.getId());
            return;
        }
        float s = (float) h[0] * fade;
        // it trembles: the brightness flickers a little every frame
        float flick = 0.75f + 0.25f * Mth.sin(ageInTicks * 2.7f + entity.getId()) * Mth.sin(ageInTicks * 1.9f);
        float pink = Mth.clamp(s - 1, 0, 1);
        float k = Math.min(1, s) * flick;
        int r = (int) (255 * Mth.clamp((0.77f + 0.23f * pink) * k, 0, 1));
        int g = (int) (255 * Mth.clamp((0.36f + 0.46f * pink) * k, 0, 1));
        int b = (int) (255 * Mth.clamp((1f - 0.05f * pink) * k, 0, 1));
        float scroll = ageInTicks * 0.02f;
        VertexConsumer out = buffer.getBuffer(RenderType.energySwirl(SWIRL, scroll % 1, scroll * 0.7f % 1));
        poseStack.pushPose();
        // a bit bigger than the body, round its middle (the model's y grows downward, the waist at 12 px)
        float grow = 1.08f + 0.04f * pink;
        poseStack.translate(0, 0.75, 0);
        poseStack.scale(grow, grow, grow);
        poseStack.translate(0, -0.75, 0);
        getParentModel().renderToBuffer(poseStack, out, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFF000000 | r << 16 | g << 8 | b);
        poseStack.popPose();
    }
}
