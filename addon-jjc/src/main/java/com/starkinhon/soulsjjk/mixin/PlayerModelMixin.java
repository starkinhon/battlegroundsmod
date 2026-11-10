package com.starkinhon.soulsjjk.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.soulsjjk.client.SkillAnimations;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

// Hooks skill body poses in after the vanilla player pose, then refreshes the outer skin layers to match.
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void soulsjjk$skillPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        SkillAnimations.apply(entity, model, ageInTicks);
        com.starkinhon.soulsjjk.client.outfit.KokushiboSheathLayer.holdArm(entity, model, ageInTicks);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.jacket.copyFrom(model.body);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        // the smear frames of the anime-style poses stretch a part for a tick: the outer layer stretches with it
        soulsjjk$scale(model.leftSleeve, model.leftArm);
        soulsjjk$scale(model.rightSleeve, model.rightArm);
        soulsjjk$scale(model.jacket, model.body);
        soulsjjk$scale(model.leftPants, model.leftLeg);
        soulsjjk$scale(model.rightPants, model.rightLeg);
    }

    private static void soulsjjk$scale(ModelPart layer, ModelPart part) {
        layer.xScale = part.xScale;
        layer.yScale = part.yScale;
        layer.zScale = part.zScale;
    }
}
