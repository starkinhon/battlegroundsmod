// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
// false and vanilla draws its own background. A row of buttons in the screen's top right corner picks dark slots, light slots or Minecraft's
// default look. require = 0: if the methods ever change, the vanilla inventory stays.
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {
    private InventoryScreenMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Unique
    private List<CrystalInventory.StyleButton> battlegrounds$styleButtons;

    // The "Escuro / Claro / Minecraft" buttons, top left corner of the screen.
    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void battlegrounds$addStyleButtons(CallbackInfo ci) {
        this.battlegrounds$styleButtons = CrystalInventory.makeButtons();
        CrystalInventory.placeButtons(this.battlegrounds$styleButtons, this.width);
        for (CrystalInventory.StyleButton button : this.battlegrounds$styleButtons) {
            this.addRenderableWidget(button);
        }
    }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true, require = 0)
    private void battlegrounds$crystalBackground(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.battlegrounds$styleButtons != null) {
            CrystalInventory.placeButtons(this.battlegrounds$styleButtons, this.width);
        }
        if (CrystalInventory.renderBg(graphics, this.leftPos, this.topPos, mouseX, mouseY, this.menu)) {
            ci.cancel();
        }
    }

    // The "Crafting" title in the soul style color: light on the dark plate, dark on the light one.
    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true, require = 0)
    private void battlegrounds$lightTitle(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (CrystalInventory.active()) {
            graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, CrystalInventory.labelColor(), CrystalInventory.labelShadow());
            ci.cancel();
        }
    }
}
