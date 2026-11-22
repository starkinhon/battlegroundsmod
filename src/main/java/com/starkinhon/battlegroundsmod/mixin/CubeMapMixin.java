// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.battlegroundsmod.client.SoulsPanorama;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.ResourceLocation;

// The menu panorama: when the mod carries panoramas of its biomes (SoulsPanorama), the vanilla cube is drawn
// as one of them instead, the same spin. Only vanilla's own cube (textures/gui/title/background/panorama);
// other mods' cubes and ours pass through untouched.
@Mixin(CubeMap.class)
public abstract class CubeMapMixin {
    @Shadow
    @Final
    private ResourceLocation[] images;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void battlegroundsmod$soulsPanorama(Minecraft mc, float pitch, float yaw, float alpha, CallbackInfo ci) {
        if (SoulsPanorama.isVanilla(images[0])) {
            CubeMap ours = SoulsPanorama.chosen();
            if (ours != null) {
                ours.render(mc, pitch, yaw, alpha);
                ci.cancel();
            }
        }
    }
}
