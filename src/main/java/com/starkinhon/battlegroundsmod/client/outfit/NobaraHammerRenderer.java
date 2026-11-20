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

    // The mesh is in blocks, standing: handle down to y -0.255, head on top, striking face +x, claw -x. In the hands it
    // is moved to the convention of the standing weapons (armas-display: handle bottom at y 0, centred on x 8 z 8, tip
    // +y, edge +x), so the display in nobara_hammer.json (Samehada base, Stark's clava/maca) puts the handle in the fist.
    // Before (print of 2026-11-20) the hand used its own rotation and the hammer came out upside down, handle up the arm.
    static final float SCALE = 1.4f;
    private static final float HANDLE_BOTTOM = -0.255f;

    public static void toThirdPerson(PoseStack poseStack) {
        poseStack.translate(0.5f, -HANDLE_BOTTOM * SCALE, 0.5f);
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (!ready()) {
            return;
        }
        poseStack.pushPose();
        if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context.firstPerson()) {
            toThirdPerson(poseStack);
        } else {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            if (context == ItemDisplayContext.GROUND) {
                // Dropped on the ground: half size, like a vanilla item.
                poseStack.scale(0.5f, 0.5f, 0.5f);
            }
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-45f)));
            poseStack.scale(2.1f, 2.1f, 2.1f);
            poseStack.translate(0.022f, 0.081f, 0f);
        }
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }
}
