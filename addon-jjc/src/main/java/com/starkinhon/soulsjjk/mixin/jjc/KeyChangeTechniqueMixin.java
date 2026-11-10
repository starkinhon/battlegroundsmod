package com.starkinhon.soulsjjk.mixin.jjc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.soulsjjk.jjc.JjcTechniques;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

// JujutsuCraft's technique selector: with Gojo Souls or Sukuna Souls it cycles through our entries (JjcTechniques).
@Mixin(targets = "net.mcreator.jujutsucraft.procedures.KeyChangeTechniqueOnKeyPressedProcedure", remap = false)
public abstract class KeyChangeTechniqueMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void soulsjjk$change(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (entity != null && JjcTechniques.change(entity)) {
            ci.cancel();
        }
    }
}
