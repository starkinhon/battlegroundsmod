package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModCreativeTabs;
import com.starkinhon.battlegroundsmod.item.UniverseItems;
import com.starkinhon.battlegroundsmod.mixin.CreativeScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

// Pages inside the mod's universe tabs in creative (Stark, 2026-11-10, like the arrows of the Jujutsu Anomalies tabs):
// two arrows over the items flip between Tudo, Almas, Armas, Itens and Ovos, skipping the empty ones. Each tab keeps
// its own page while the game runs. The filtering itself runs right after vanilla fills the tab (CreativeScreenMixin).
public final class CreativeSubTabs {
    private static final UniverseItems.Kind[] KINDS = UniverseItems.Kind.values();
    private static final Map<CreativeModeTab, Integer> PAGE = new HashMap<>();
    private static CreativeModeTab current;
    private static Button left;
    private static Button right;

    private CreativeSubTabs() {
    }

    private static boolean ours(CreativeModeTab tab) {
        if (tab == null) {
            return false;
        }
        for (var holder : ModCreativeTabs.UNIVERSES.values()) {
            if (holder.get() == tab) {
                return true;
            }
        }
        return false;
    }

    private static UniverseItems.Kind kind(CreativeModeTab tab) {
        return KINDS[PAGE.getOrDefault(tab, 0)];
    }

    // Called by the mixin after vanilla filled the tab: drops what is not on the picked page. True if it changed it.
    public static boolean afterSelect(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        current = tab;
        if (!ours(tab) || kind(tab) == UniverseItems.Kind.ALL) {
            return false;
        }
        UniverseItems.Kind wanted = kind(tab);
        screen.getMenu().items.removeIf(stack -> UniverseItems.kind(stack.getItem()) != wanted);
        screen.getMenu().scrollTo(0);
        return true;
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) {
            return;
        }
        int x = screen.getGuiLeft();
        int y = screen.getGuiTop();
        left = Button.builder(Component.literal("<"), b -> flip(screen, -1)).bounds(x + 104, y + 4, 10, 10).build();
        right = Button.builder(Component.literal(">"), b -> flip(screen, 1)).bounds(x + 172, y + 4, 10, 10).build();
        event.addListener(left);
        event.addListener(right);
        update();
    }

    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen) {
            update();
        }
    }

    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || !ours(current)) {
            return;
        }
        Component name = Component.translatable("creative.battlegroundsmod.page." + kind(current).name().toLowerCase());
        var font = Minecraft.getInstance().font;
        int center = screen.getGuiLeft() + 143;
        event.getGuiGraphics().drawString(font, name, center - font.width(name) / 2, screen.getGuiTop() + 6, 0x404040, false);
    }

    private static void update() {
        boolean show = ours(current);
        if (left != null) {
            left.visible = show;
        }
        if (right != null) {
            right.visible = show;
        }
    }

    // Next or previous page with something on it, then refills the tab.
    private static void flip(CreativeModeInventoryScreen screen, int step) {
        if (!ours(current)) {
            return;
        }
        int page = PAGE.getOrDefault(current, 0);
        for (int i = 0; i < KINDS.length; i++) {
            page = Math.floorMod(page + step, KINDS.length);
            if (KINDS[page] == UniverseItems.Kind.ALL || has(current, KINDS[page])) {
                break;
            }
        }
        PAGE.put(current, page);
        ((CreativeScreenAccessor) screen).battlegrounds$selectTab(current);
    }

    private static boolean has(CreativeModeTab tab, UniverseItems.Kind kind) {
        for (ItemStack stack : tab.getDisplayItems()) {
            if (UniverseItems.kind(stack.getItem()) == kind) {
                return true;
            }
        }
        return false;
    }
}
