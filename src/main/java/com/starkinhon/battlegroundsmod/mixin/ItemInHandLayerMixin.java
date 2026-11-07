package com.starkinhon.battlegroundsmod.mixin;

import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.SkillAnimations;
import com.starkinhon.battlegroundsmod.client.fx.SwordFx;
import com.starkinhon.battlegroundsmod.client.outfit.KokushiboSheathLayer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// The sword moves turn the held sword about the wrist (the "Item" bone of their keyframe animations), which Minecraft's
// arms can't do on their own; and the blade's trail (client/fx/SwordFx) is taken from right here, where it is drawn.
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    // Kokushibo's sword in the right hand is drawn by KokushiboSheathLayer while it rests in the sheath (out of combat
    // mode) or moves between sheath and hand; and whatever was just switched to stays out of the hand until the sword
    // is home.
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void battlegroundsmod$puttingAway(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (arm == HumanoidArm.RIGHT && KokushiboSheathLayer.hidesHand(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void battlegroundsmod$wrist(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (arm != HumanoidArm.RIGHT || stack != entity.getMainHandItem()) {
            return;
        }
        float[] wrist = SkillAnimations.wrist(entity);
        if (wrist != null) {
            pose.mulPose(new Quaternionf().rotationZYX(wrist[2], wrist[1], wrist[0]));
            SwordFx.atWrist(entity, stack, pose);
        }
    }
}
