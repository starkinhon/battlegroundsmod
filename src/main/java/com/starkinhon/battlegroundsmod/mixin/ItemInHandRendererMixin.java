package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.outfit.SwordStretch;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// Every held item, first or third person, is drawn through here: the item renderer is told whose hand it is in, so
// Kokushibo's sword can stretch in his hand (client/outfit/SwordStretch).
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void battlegroundsmod$holder(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        SwordStretch.holder(entity);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void battlegroundsmod$noHolder(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        SwordStretch.holder(null);
    }
}
