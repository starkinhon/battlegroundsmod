// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.item.NinjaThrown;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Custom renderer for thrown ninja projectiles (Kunai, Shuriken, Paper Bomb):
 * - Kunai flies with its tip pointed directly along its flight velocity vector.
 * - Shuriken flies flat along its flight trajectory while spinning rapidly.
 * - Paper Bomb keeps the standard thrown-item billboard look.
 */
public class NinjaThrownRenderer extends EntityRenderer<NinjaThrown> {
    private final ItemRenderer itemRenderer;

    public NinjaThrownRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(NinjaThrown entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.tickCount < 2 && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) {
            return;
        }
        ItemStack stack = entity.getItem();
        poseStack.pushPose();

        if (stack.is(ModItems.KUNAI.get()) || stack.is(ModItems.HIRAISHIN_KUNAI.get())) {
            Vec3 v = entity.getDeltaMovement();
            double horiz = Math.hypot(v.x, v.z);
            float yawDeg = v.lengthSqr() > 1.0e-6 ? (float) Math.toDegrees(Math.atan2(v.x, v.z)) : entityYaw;
            float pitchDeg = v.lengthSqr() > 1.0e-6 ? (float) Math.toDegrees(Math.atan2(v.y, horiz)) : entity.getXRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(90f - pitchDeg));
            poseStack.scale(0.85f, 0.85f, 0.85f);
            this.itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, entity.level(), entity.getId());
        } else if (stack.is(ModItems.SHURIKEN.get())) {
            Vec3 v = entity.getDeltaMovement();
            double horiz = Math.hypot(v.x, v.z);
            float yawDeg = v.lengthSqr() > 1.0e-6 ? (float) Math.toDegrees(Math.atan2(v.x, v.z)) : entityYaw;
            float pitchDeg = v.lengthSqr() > 1.0e-6 ? (float) Math.toDegrees(Math.atan2(v.y, horiz)) : entity.getXRot();
            float spin = (entity.tickCount + partialTick) * 55f;
            poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitchDeg));
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            poseStack.scale(0.85f, 0.85f, 0.85f);
            this.itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, entity.level(), entity.getId());
        } else {
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY,
                    poseStack, buffer, entity.level(), entity.getId());
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(NinjaThrown entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
