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
 * Custom 3D renderer for Nanami's cleaver blade using the baked GLB mesh and exact preview hand transforms.
 */
public class NanamiBladeRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BattlegroundsMod.MODID, "textures/models/nanami_blade.png");

    private static MeshModel mesh;
    private static boolean broken;

    public NanamiBladeRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static boolean ready() {
        if (broken) {
            return false;
        }
        if (mesh == null) {
            try {
                mesh = MeshModel.load("nanami_blade");
            } catch (RuntimeException e) {
                broken = true;
                BattlegroundsMod.LOGGER.error("Failed to load nanami_blade mesh", e);
                return false;
            }
        }
        return true;
    }

    public static void toThirdPerson(PoseStack poseStack) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        poseStack.translate(0f, -0.01f, -0.03f);
        poseStack.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(91f),
                (float) Math.toRadians(2f),
                (float) Math.toRadians(-3f)));
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
            // nanami_blade.json applies vanilla firstperson_righthand; apply exact previewer 1p transform.
            poseStack.translate(0.45f, 0.05f, 0.20f);
            poseStack.mulPose(new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(166f),
                    (float) Math.toRadians(-1f),
                    (float) Math.toRadians(92f)));
            poseStack.scale(1.05f, 1.05f, 1.05f);
        } else {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(45f)));
            poseStack.scale(1.28f, 1.28f, 1.28f);
            poseStack.translate(-0.133f, 0.051f, 0f);
        }
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }
}
