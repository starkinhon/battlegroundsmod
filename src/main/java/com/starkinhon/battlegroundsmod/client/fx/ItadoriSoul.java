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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

// Itadori's soul (forja/itadori-kit shinjuku.js, B and the domain: the preview's ghost, a Minecraft body of pale light):
// the body's own model once more, in its own pose, a little bigger, with souls_it_soul (GHOST_F), moved by an offset in
// the world (the tremble, a blow pushing it out, the rip). Jj2Fx touches it every frame it draws the soul; it goes off on
// its own a few frames after the last touch. The rip outranks the plain marked soul in the same frame.
public final class ItadoriSoul<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    // entity id -> {off x, y, z (world, blocks), scale, alpha, game time of the touch, rank}
    private static final Map<Integer, double[]> ON = new HashMap<>();

    public ItadoriSoul(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    static void touch(int entityId, float ox, float oy, float oz, float scale, float alpha, double time, int rank) {
        double[] h = ON.get(entityId);
        if (h != null && Math.abs(h[5] - time) < 0.01 && h[6] > rank) {
            return;
        }
        ON.put(entityId, new double[] {ox, oy, oz, scale, alpha, time, rank});
    }

    public static void clear() {
        ON.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        double[] h = ON.get(entity.getId());
        if (h == null || FxShaders.itSoul == null || IrisCompat.inShadowPass()) {
            return;
        }
        double now = entity.level().getGameTime() + partialTick;
        if (now - h[5] > 3 || now < h[5] - 40) {
            ON.remove(entity.getId());
            return;
        }
        float a = (float) Math.min(1, h[4]);
        if (a <= 0.003f) {
            return;
        }
        // the world offset into the model's space (the renderer turned it by 180 - body yaw, then flipped x and y)
        float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float th = -(180 - yaw) * Mth.DEG_TO_RAD;
        double cs = Math.cos(th);
        double sn = Math.sin(th);
        double mx = h[0] * cs + h[2] * sn;
        double mz = -h[0] * sn + h[2] * cs;
        float k = 1 / Math.max(0.1f, entity.getScale());
        float sc = (float) h[3];
        poseStack.pushPose();
        poseStack.translate(-mx * k, -h[1] * k, mz * k);
        // sc round the body's middle (the model's y grows downward, the waist at 12 px)
        poseStack.translate(0, 0.75, 0);
        poseStack.scale(sc, sc, sc);
        poseStack.translate(0, -0.75, 0);
        int alpha = Math.round(a * 255);
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(FxRenderTypes.IT_SOUL), 0xF000F0, OverlayTexture.NO_OVERLAY, alpha << 24 | 0xFFFFFF);
        poseStack.popPose();
    }
}
