package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.LimpBody;
import com.starkinhon.battlegroundsmod.client.fx.AuraFx;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

// A body out of its own control (client/LimpBody): its limbs go loose after any humanoid model (players, bosses,
// zombies, skeletons...) was fully set up, including the mob's own pose code and the skill poses.
// The Kaioken's red body (client/fx/AuraFx.tint): the color the model is drawn with, beating with the aura. Optional
// (require = 0): if the call is not found the body only stays its own color.
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

    @Unique
    private LivingEntity battlegroundsmod$drawn;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), require = 0)
    private void battlegroundsmod$tintStart(LivingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            CallbackInfo ci) {
        battlegroundsmod$drawn = entity;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4, require = 0)
    private int battlegroundsmod$kaiokenTint(int color) {
        LivingEntity entity = battlegroundsmod$drawn;
        if (entity == null) {
            return color;
        }
        int tint = AuraFx.tint(entity);
        if (tint == -1) {
            return color;
        }
        int a = color >>> 24, r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        r = r * (tint >> 16 & 255) / 255;
        g = g * (tint >> 8 & 255) / 255;
        b = b * (tint & 255) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }
}
