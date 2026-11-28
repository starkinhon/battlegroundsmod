// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.compat.IrisCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Itadori's Escama Vermelha (forja/itadori-kit shinjuku.js, Shift+Z): once, for a little under a second, the veins light up
// under his skin, spreading out of the heart, beating, and the skin goes a little red. The body drawn once more 1.006x
// with souls_it_veins (the page's VEIN_F on each part, scale 1.006). Jj2Fx touches it every frame it draws the Escama;
// it goes off on its own a few frames after the last touch.
public final class ItadoriVeins<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    // entity id -> {rev, beat, amount, heart x, y, z (world), scale, game time of the last touch}
    private static final Map<Integer, double[]> ON = new HashMap<>();

    public ItadoriVeins(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    static void touch(int entityId, float rev, float beat, float amount, Vec3 heart, float scale, double time) {
        ON.put(entityId, new double[] {rev, beat, amount, heart.x, heart.y, heart.z, scale, time});
    }

    public static void clear() {
        ON.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        double[] h = ON.get(entity.getId());
        ShaderInstance sh = FxShaders.itVeins;
        if (h == null || entity.isInvisible() || sh == null || IrisCompat.inShadowPass()) {
            return;
        }
        double now = entity.level().getGameTime() + partialTick;
        if (now - h[7] > 3 || now < h[7] - 40) {
            ON.remove(entity.getId());
            return;
        }
        if (h[2] <= 0.003) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // drawn and flushed right away, so the uniforms are this body's
        MultiBufferSource.BufferSource own = Minecraft.getInstance().renderBuffers().bufferSource();
        own.endBatch(FxRenderTypes.IT_VEINS);
        sh.safeGetUniform("Heart").set((float) (h[3] - cam.x), (float) (h[4] - cam.y), (float) (h[5] - cam.z));
        sh.safeGetUniform("Rev").set((float) h[0]);
        sh.safeGetUniform("Beat").set((float) h[1]);
        sh.safeGetUniform("Amt").set((float) h[2]);
        sh.safeGetUniform("Scale").set((float) h[6]);
        sh.safeGetUniform("Seed").set(3.7f + (entity.getId() % 7) * 2.3f);
        poseStack.pushPose();
        // 1.006x round the body's middle (the model's y grows downward, the waist at 12 px)
        poseStack.translate(0, 0.75, 0);
        poseStack.scale(1.006f, 1.006f, 1.006f);
        poseStack.translate(0, -0.75, 0);
        getParentModel().renderToBuffer(poseStack, own.getBuffer(FxRenderTypes.IT_VEINS), 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        own.endBatch(FxRenderTypes.IT_VEINS);
    }
}
