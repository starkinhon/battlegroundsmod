package com.starkinhon.battlegroundsmod.client.outfit;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.item.KokushiboSwordItem;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

// Kokushibo's sheath on the left hip, samurai style: mouth just in front of the hip, edge up, the scabbard running
// back and down past the leg. With the sword in hand the sheath is empty; with it only in the inventory the sword
// rests inside (the two meshes share one space, so it fits the blade exactly).
public class KokushiboSheathLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    // Model space (1 = one pixel, +y down, -z the front, +x the player's left), relative to the body's pivot.
    private static final Vector3f GUARD_AT = new Vector3f(4.9f, 10.6f, -3.2f);
    private static final Quaternionf TURN;

    static {
        Vector3f tip = new Vector3f(0.12f, 0.3f, 0.95f).normalize();
        Vector3f edge = new Vector3f(0, -1, 0);
        edge.sub(new Vector3f(tip).mul(edge.dot(tip))).normalize();
        Vector3f side = new Vector3f(edge).cross(tip);
        TURN = new Matrix3f(edge, tip, side).getNormalizedRotation(new Quaternionf());
    }

    public KokushiboSheathLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator() || !getParentModel().body.visible) {
            return;
        }
        int style = 0;
        if (player.getMainHandItem().getItem() instanceof KokushiboSwordItem sword) {
            style = sword.style();
        } else if (player.getOffhandItem().getItem() instanceof KokushiboSwordItem sword) {
            style = sword.style();
        }
        boolean inHand = style > 0;
        if (!inHand) {
            style = Sheaths.style(player.getId());
        }
        int look = style - 1;
        if (look < 0 || !KokushiboSwordRenderer.ready(look)) {
            return;
        }
        poseStack.pushPose();
        try {
            getParentModel().body.translateAndRotate(poseStack);
            poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);
            poseStack.translate(GUARD_AT.x, GUARD_AT.y, GUARD_AT.z);
            poseStack.mulPose(TURN);
            KokushiboSwordRenderer.emit(look, poseStack.last(), buffer, light, LivingEntityRenderer.getOverlayCoords(player, 0), !inHand, true);
        } catch (RuntimeException e) {
            KokushiboSwordRenderer.fail(e);
        } finally {
            poseStack.popPose();
        }
    }
}
