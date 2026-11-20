// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starkinhon.battlegroundsmod.client.fx.BiomeColorEase;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;

// The biome sky color eased in time as well as in space (BiomeColorEase): no cut in the sky's color crossing a border
// flying fast.
@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyMixin {
    @WrapOperation(method = "getSkyColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"),
            require = 0)
    private Vec3 battlegroundsmod$easeSky(Vec3 at, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original) {
        return BiomeColorEase.SKY.ease(at, original.call(at, fetcher));
    }
}
