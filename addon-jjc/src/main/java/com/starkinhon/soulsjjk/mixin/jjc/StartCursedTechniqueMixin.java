package com.starkinhon.soulsjjk.mixin.jjc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.soulsjjk.jjc.JjcTechniques;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

// JujutsuCraft's start key: our entries run the Souls move (JjcTechniques.start); its own entries go on as usual.
@Mixin(targets = "net.mcreator.jujutsucraft.procedures.StartCursedTechniqueProcedure", remap = false)
public abstract class StartCursedTechniqueMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void soulsjjk$start(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (entity != null && JjcTechniques.start(entity)) {
            ci.cancel();
        }
    }
}
