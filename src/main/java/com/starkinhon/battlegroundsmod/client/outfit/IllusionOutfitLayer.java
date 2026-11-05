package com.starkinhon.battlegroundsmod.client.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.power.powers.aizen.AizenIllusion;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.Player;

// Kyoka Suigetsu's clone wears what its owner wears: the outfit's fixed pieces (Aizen's sword in the right hand). The
// hanging cloth is left out, since it needs the owner's own simulation; Aizen's outfit has none.
public class IllusionOutfitLayer extends RenderLayer<AizenIllusion, PlayerModel<AizenIllusion>> {
    private static boolean broken;

    public IllusionOutfitLayer(RenderLayerParent<AizenIllusion, PlayerModel<AizenIllusion>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AizenIllusion clone, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (broken || clone.isInvisible()) {
            return;
        }
        try {
            Player owner = clone.owner().map(clone.level()::getPlayerByUUID).orElse(null);
            String power = owner == null ? "" : Outfits.worn(owner.getId());
            OutfitDefs.Def def = power.isEmpty() ? null : OutfitDefs.get(power);
            if (def == null) {
                return;
            }
            PlayerModel<AizenIllusion> model = getParentModel();
            int overlay = LivingEntityRenderer.getOverlayCoords(clone, 0);
            for (OutfitDefs.Piece piece : def.pieces()) {
                ModelPart part = OutfitLayer.part(model, piece.bone());
                if (!part.visible || piece.bone() == Rig.Bone.HAND_R && !clone.getMainHandItem().isEmpty()) {
                    continue;
                }
                poseStack.pushPose();
                part.translateAndRotate(poseStack);
                OutfitLayer.emitStatic(buffer.getBuffer(RenderType.entityCutoutNoCull(piece.texture())), poseStack.last(), piece.bone(), piece.tris(),
                        light, overlay);
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            broken = true;
            BattlegroundsMod.LOGGER.error("Drawing the clone's outfit failed; it is off until the game restarts", e);
        }
    }
}
