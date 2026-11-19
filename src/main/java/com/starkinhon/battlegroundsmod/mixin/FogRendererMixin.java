package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starkinhon.battlegroundsmod.client.fx.BiomeColorEase;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;

// The biome fog color eased in time as well as in space (BiomeColorEase): no cut in the horizon's color crossing a border
// flying fast. Only the open-air fog: under water and in lava the game keeps its own.
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"),
            require = 0)
    private static Vec3 battlegroundsmod$easeFog(Vec3 at, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original) {
        return BiomeColorEase.FOG.ease(at, original.call(at, fetcher));
    }
}
