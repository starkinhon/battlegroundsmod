// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.mixin;

import com.starkinhon.battlegroundsmod.universe.EnergyControl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Controle de Energia (universe/EnergyControl): a player who learned it climbs any block side like a ladder.
@Mixin(LivingEntity.class)
public abstract class ClimbMixin {
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void souls$climbWalls(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && EnergyControl.climbs(player)) {
            cir.setReturnValue(true);
        }
    }
}
