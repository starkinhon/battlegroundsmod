package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.world.NamekWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;

// On Namek the Mine's sun is Earth's (Stark: "o intruso é o sol normal do mine"): its texture is swapped for a blank one
// there, so only Namek's own suns (DragonWind) show.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    private static final ResourceLocation BATTLEGROUNDSMOD$NO_SUN = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID,
            "textures/environment/no_sun.png");

    @ModifyArg(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
            ordinal = 0), index = 1, require = 0)
    private ResourceLocation battlegroundsmod$noSunOnNamek(ResourceLocation texture) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == NamekWorld.LEVEL ? BATTLEGROUNDSMOD$NO_SUN : texture;
    }

    // Under Shenlong's storm the Mine's flat clouds would hang in front of the storm sky (it is drawn at the far plane).
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void battlegroundsmod$noCloudsInStorm(CallbackInfo ci) {
        if (com.starkinhon.battlegroundsmod.client.fx.ShenlongScene.stormNow() > 0.15) {
            ci.cancel();
        }
    }
}
