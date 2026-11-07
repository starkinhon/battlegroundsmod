package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.LimpBody;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

// A body out of its own control (client/LimpBody): its limbs go loose after any humanoid model (players, bosses,
// zombies, skeletons...) was fully set up, including the mob's own pose code and the skill poses.
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void battlegroundsmod$limpReset(LivingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            CallbackInfo ci) {
        if (((LivingEntityRenderer<?, ?>) (Object) this).getModel() instanceof HumanoidModel<?> model) {
            LimpBody.beforeSetup(model);
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void battlegroundsmod$limpPose(LivingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            CallbackInfo ci) {
        if (((LivingEntityRenderer<?, ?>) (Object) this).getModel() instanceof HumanoidModel<?> model) {
            LimpBody.pose(entity, model, partialTick);
        }
    }
}
