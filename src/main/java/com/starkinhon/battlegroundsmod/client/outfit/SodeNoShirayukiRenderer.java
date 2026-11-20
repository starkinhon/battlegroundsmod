// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.outfit;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Custom 3D renderer for Sode no Shirayuki using the baked GLB mesh and calibrated hand transforms.
 */
public class SodeNoShirayukiRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BattlegroundsMod.MODID, "textures/models/sode_no_shirayuki.png");

    private static MeshModel mesh;
    private static boolean broken;

    public SodeNoShirayukiRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static boolean ready() {
        if (broken) {
            return false;
        }
        if (mesh == null) {
            try {
                mesh = MeshModel.load("sode_no_shirayuki");
            } catch (RuntimeException e) {
                broken = true;
                BattlegroundsMod.LOGGER.error("Failed to load sode_no_shirayuki mesh", e);
                return false;
            }
        }
        return true;
    }

    public static void toThirdPerson(PoseStack poseStack) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        poseStack.translate(-0.01f, 0.01f, -0.02f);
        poseStack.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(91f),
                (float) Math.toRadians(2f),
                (float) Math.toRadians(-8f)));
        poseStack.scale(2f, 2f, 2f);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (!ready()) {
            return;
        }
        poseStack.pushPose();
        if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            toThirdPerson(poseStack);
        } else if (context.firstPerson()) {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.translate(0.43f, -0.06f, 0.25f);
            poseStack.mulPose(new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(167f),
                    (float) Math.toRadians(-1f),
                    (float) Math.toRadians(94f)));
            poseStack.scale(1.26f, 1.26f, 1.26f);
        } else {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(45f)));
            poseStack.scale(1.22f, 1.22f, 1.22f);
            poseStack.translate(-0.232f, -0.006f, 0f);
        }
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }
}
