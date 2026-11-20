// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

// Lets the arrows (CreativeSubTabs) refill the open tab after picking another page.
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenAccessor {
    @Invoker("selectTab")
    void battlegrounds$selectTab(CreativeModeTab tab);
}
