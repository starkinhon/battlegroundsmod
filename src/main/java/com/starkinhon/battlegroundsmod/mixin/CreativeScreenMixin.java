package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.battlegroundsmod.client.CreativeSubTabs;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

// After the creative screen fills a tab, keeps only the page the arrows picked (CreativeSubTabs) in the mod's
// universe tabs. require = 0: if the method ever changes name the tabs just show everything, the game still opens.
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
    @Shadow
    private float scrollOffs;

    @Inject(method = "selectTab", at = @At("TAIL"), require = 0)
    private void battlegrounds$filterPage(CreativeModeTab tab, CallbackInfo ci) {
        if (CreativeSubTabs.afterSelect((CreativeModeInventoryScreen) (Object) this, tab)) {
            this.scrollOffs = 0;
        }
    }
}
