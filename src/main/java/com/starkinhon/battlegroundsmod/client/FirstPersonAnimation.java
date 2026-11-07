package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.client.fx.SwordFx;


import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.neoforge.client.event.RenderHandEvent;

// While a hand-made keyframe animation plays (SkillAnimations), the first person hands show the same arms as the
// third person body instead of the vanilla hand: each arm is drawn where it really is, seen from the eyes. So an arm
// the animation raises in front of the face shows up there, and one hanging at the side stays out of view.
public final class FirstPersonAnimation {
    // PlayerRenderer scales the model by 0.9375; its neck (model y 0) then sits 1.501 * 0.9375 blocks up, under the
    // eyes at 1.62.
    private static final float SCALE = 0.9375f;
    private static final float NECK_BELOW_EYES = 1.62f - 1.501f * SCALE;

    private FirstPersonAnimation() {
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof AbstractClientPlayer player)) {
            return;
        }
        float partial = event.getPartialTick();
        float age = player.tickCount + partial;
        // Kokushibo's sword resting in its sheath though selected (out of combat mode): nothing in the hand.
        boolean hidden = event.getHand() == InteractionHand.MAIN_HAND
                && com.starkinhon.battlegroundsmod.client.outfit.KokushiboSheathLayer.hidesHand(player);
        if (hidden && !SkillAnimations.keyframed(player, age)) {
            event.setCanceled(true);
            return;
        }
        if (!SkillAnimations.keyframed(player, age)
                || !(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }
        event.setCanceled(true);
        HumanoidArm side = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        model.attackTime = 0;
        model.crouching = false;
        model.swimAmount = 0;
        model.riding = false;
        // A real age makes PlayerModelMixin run the keyframes (age 0 is how vanilla poses its own first person hand).
        model.setupAnim(player, 0, 0, age, 0, 0);
        boolean right = side == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // From the camera to the body: the body does not pitch with the view, and may be turned from where the head looks.
        pose.mulPose(Axis.XP.rotationDegrees(player.getViewXRot(partial)));
        float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(headYaw - bodyYaw)));
        pose.translate(0, -NECK_BELOW_EYES, 0);
        // Model space has y down and the player's left on +x; the camera has y up and right on +x. Both face -z.
        pose.scale(-SCALE, -SCALE, SCALE);
        ResourceLocation skin = player.getSkin().texture();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        arm.visible = true;
        arm.render(pose, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
            sleeve.visible = true;
            sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        }
        // What the hand holds (the sword moves), the way the third person body holds it, turned about the wrist.
        ItemStack held = player.getMainHandItem();
        if (right && event.getHand() == InteractionHand.MAIN_HAND && !held.isEmpty() && !hidden) {
            pose.pushPose();
            arm.translateAndRotate(pose);
            toHand(pose, player);
            mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
                    pose, buffers, light);
            pose.popPose();
            if (SkillAnimations.swordMove(player, age)) {
                trail(player, arm, held, partial, bodyYaw);
            }
        }
        pose.popPose();
    }

    // ItemInHandLayer's steps from the arm to where the item is drawn, plus the sword move's wrist turn.
    private static void toHand(PoseStack pose, AbstractClientPlayer player) {
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(1 / 16f, 0.125f, -0.625f);
        float[] wrist = SkillAnimations.wrist(player);
        if (wrist != null) {
            com.starkinhon.battlegroundsmod.client.outfit.KokushiboSwordRenderer.turnWrist(pose, wrist, player.getMainHandItem());
        }
    }

    // In first person the body isn't drawn, so the blade's trail is found here: the same steps the body would be drawn
    // with (LivingEntityRenderer, PlayerRenderer), from the feet.
    private static void trail(AbstractClientPlayer player, ModelPart arm, ItemStack held, float partial, float bodyYaw) {
        PoseStack world = new PoseStack();
        float scale = player.getScale();
        world.scale(scale, scale, scale);
        world.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        world.scale(-1, -1, 1);
        world.scale(SCALE, SCALE, SCALE);
        world.translate(0, -1.501f, 0);
        arm.translateAndRotate(world);
        toHand(world, player);
        SwordFx.atWristFirstPerson(player, held, world, player.getPosition(partial));
    }
}
