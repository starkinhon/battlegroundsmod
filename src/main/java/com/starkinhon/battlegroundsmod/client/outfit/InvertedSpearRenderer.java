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
 * Custom 3D renderer for Toji's Inverted Spear of Heaven (Lança Invertida do Céu) using the baked mesh and calibrated hand transforms.
 */
public class InvertedSpearRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BattlegroundsMod.MODID, "textures/models/lanca_invertida.png");

    private static MeshModel mesh;
    private static boolean broken;

    public InvertedSpearRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static boolean ready() {
        if (broken) {
            return false;
        }
        if (mesh == null) {
            try {
                mesh = MeshModel.load("lanca_invertida");
            } catch (RuntimeException e) {
                broken = true;
                BattlegroundsMod.LOGGER.error("Failed to load lanca_invertida mesh", e);
                return false;
            }
        }
        return true;
    }

    public static void toThirdPerson(PoseStack poseStack) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        poseStack.translate(0f, 0f, -0.03f);
        poseStack.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(91f),
                (float) Math.toRadians(2f),
                (float) Math.toRadians(-98f)));
        poseStack.scale(1.5f, 1.5f, 1.5f);
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
                    (float) Math.toRadians(4f)));
            poseStack.scale(1.26f, 1.26f, 1.26f);
        } else {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-45f)));
            poseStack.scale(1.55f, 1.55f, 1.55f);
            poseStack.translate(0.009f, -0.121f, 0f);
        }
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }
}
