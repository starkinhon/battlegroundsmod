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
 * Custom 3D renderer for Nobara's hammer using the baked GLB mesh and calibrated hand transforms.
 */
public class NobaraHammerRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BattlegroundsMod.MODID, "textures/models/nobara_hammer.png");

    private static MeshModel mesh;
    private static boolean broken;

    public NobaraHammerRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static boolean ready() {
        if (broken) {
            return false;
        }
        if (mesh == null) {
            try {
                mesh = MeshModel.load("nobara_hammer");
            } catch (RuntimeException e) {
                broken = true;
                BattlegroundsMod.LOGGER.error("Failed to load nobara_hammer mesh", e);
                return false;
            }
        }
        return true;
    }

    public static void toThirdPerson(PoseStack poseStack) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        poseStack.translate(0.02f, 0f, -0.03f);
        poseStack.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(83f),
                (float) Math.toRadians(0f),
                (float) Math.toRadians(-93f)));
        poseStack.scale(1.4f, 1.4f, 1.4f);
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
            poseStack.translate(0.35f, 0.02f, 0.12f);
            poseStack.mulPose(new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(166f),
                    (float) Math.toRadians(8f),
                    (float) Math.toRadians(4f)));
            poseStack.scale(1.4f, 1.4f, 1.4f);
        } else {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-45f)));
            poseStack.scale(2.1f, 2.1f, 2.1f);
            poseStack.translate(0.022f, 0.081f, 0f);
        }
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }
}
