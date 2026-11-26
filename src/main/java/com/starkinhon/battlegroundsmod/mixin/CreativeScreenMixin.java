// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import com.starkinhon.battlegroundsmod.client.CreativeCharacterSearch;
import com.starkinhon.battlegroundsmod.client.CreativeSubTabs;

import net.minecraft.client.gui.components.EditBox;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

// After the creative screen fills a tab, keeps only the page the arrows picked (CreativeSubTabs) in the mod's
// universe tabs. require = 0: if the method ever changes name the tabs just show everything, the game still opens.
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
    @Shadow
    private float scrollOffs;

    @Shadow
    private EditBox searchBox;

    @Shadow
    private static CreativeModeTab selectedTab;

    @Inject(method = "selectTab", at = @At("TAIL"), require = 0)
    private void battlegrounds$filterPage(CreativeModeTab tab, CallbackInfo ci) {
        if (CreativeSubTabs.afterSelect((CreativeModeInventoryScreen) (Object) this, tab)) {
            this.scrollOffs = 0;
        }
    }

    // The search finds characters by the real name too (CreativeCharacterSearch): their items go first, without
    // repeating the ones the game's own search already found. require = 0 like above: worst case, plain search.
    @Inject(method = "refreshSearchResults", at = @At("TAIL"), require = 0)
    private void battlegrounds$characterSearch(CallbackInfo ci) {
        if (this.searchBox == null || selectedTab == null || !selectedTab.hasSearchBar()) {
            return;
        }
        List<ItemStack> hits = CreativeCharacterSearch.find(this.searchBox.getValue(),
                selectedTab.getSearchTabDisplayItems());
        if (hits.isEmpty()) {
            return;
        }
        List<ItemStack> items = ((CreativeModeInventoryScreen) (Object) this).getMenu().items;
        items.removeIf(stack -> hits.stream().anyMatch(hit -> ItemStack.isSameItemSameComponents(hit, stack)));
        items.addAll(0, hits);
        this.scrollOffs = 0;
        ((CreativeModeInventoryScreen) (Object) this).getMenu().scrollTo(0);
    }
}
