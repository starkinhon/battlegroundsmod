package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.battlegroundsmod.client.CrystalInventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

// The survival inventory drawn as the crystal cluster in the soul's color (CrystalInventory). If that fails it returns
// false and vanilla draws its own background. require = 0: if the methods ever change, the vanilla inventory stays.
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {
    private InventoryScreenMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true, require = 0)
    private void battlegrounds$crystalBackground(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (CrystalInventory.renderBg(graphics, this.leftPos, this.topPos, mouseX, mouseY, this.menu)) {
            ci.cancel();
        }
    }

    // The "Crafting" title in a light color: vanilla's dark gray vanishes on the crystals.
    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true, require = 0)
    private void battlegrounds$lightTitle(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (CrystalInventory.active()) {
            graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, CrystalInventory.labelColor(), true);
            ci.cancel();
        }
    }
}
