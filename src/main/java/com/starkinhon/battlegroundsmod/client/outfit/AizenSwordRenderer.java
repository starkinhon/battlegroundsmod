package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// Draws Aizen's sword item with the 3D model from the Armario Souls viewer (OutfitDefs.sword()). The item model
// (models/item/aizen_sword.json) uses the vanilla handheld transforms, so here the sword is laid out like a vanilla
// sword sprite: grip near the bottom left corner of the 0..1 square, tip toward the top right, edge toward the top left
// (forward and down in the hand). In third person it is tilted up about 36 degrees more, so it points forward and up
// like in the viewer. In the inventory, on the ground and in frames it is shrunk to fit, centred.
public class AizenSwordRenderer extends BlockEntityWithoutLevelRenderer {
    private static List<OutfitDefs.Piece> pieces;
    private static boolean broken;

    public AizenSwordRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (broken) {
            return;
        }
        try {
            if (pieces == null) {
                pieces = OutfitDefs.sword();
            }
            draw(context, poseStack, buffer, light, overlay);
        } catch (RuntimeException e) {
            broken = true;
            BattlegroundsMod.LOGGER.error("Drawing Aizen's sword failed; it is invisible until the game restarts", e);
        }
    }

    private static void draw(ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        boolean firstPerson = context.firstPerson();
        boolean thirdPerson = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        poseStack.pushPose();
        float scale, anchor;
        if (firstPerson || thirdPerson) {
            // the fist sits where a vanilla sword's handle is
            poseStack.translate(3 / 16f, 3 / 16f, 0.5f);
            if (thirdPerson) {
                poseStack.mulPose(Axis.ZP.rotation(-0.62f));
            }
            scale = thirdPerson ? 1.15f : 1.0f;
            anchor = OutfitDefs.SWORD_GRIP;
        } else {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            scale = 0.74f;
            anchor = OutfitDefs.SWORD_MIDDLE;
        }
        // sword space (+y tip, +x edge, z thickness) to the sprite's diagonal
        poseStack.mulPose(Axis.ZP.rotation((float) -Math.PI / 4));
        poseStack.mulPose(Axis.YP.rotation((float) Math.PI));
        poseStack.scale(scale / 16, scale / 16, scale / 16);
        poseStack.translate(0, -anchor, 0);
        PoseStack.Pose pose = poseStack.last();
        // one buffer at a time: asking for another render type closes the one before
        for (OutfitDefs.Piece piece : pieces) {
            VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(piece.texture()));
            float[] t = piece.tris();
            for (int i = 0; i < t.length; i += 33) {
                for (int v = 0; v < 4; v++) {
                    int o = i + Math.min(v, 2) * 11;
                    out.addVertex(pose, t[o], t[o + 1], t[o + 2]).setColor(t[o + 5], t[o + 6], t[o + 7], 1f).setUv(t[o + 3], t[o + 4])
                            .setOverlay(overlay).setLight(light).setNormal(pose, t[o + 8], t[o + 9], t[o + 10]);
                }
            }
        }
        poseStack.popPose();
    }
}
