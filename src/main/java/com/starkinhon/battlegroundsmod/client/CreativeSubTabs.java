package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

// Universe filter arrows inside the mod's utility creative tabs:
// two arrows (< >) over the items flip between Tudo, each universe present in that tab, and Outros,
// automatically skipping empty universes and hiding the arrows on tabs with only one group.
// Each tab keeps its own selected page while the game runs. Filtering runs right after vanilla fills the tab (CreativeScreenMixin).
public final class CreativeSubTabs {
    private static final String PAGE_ALL = "all";
    private static final String PAGE_OTHER = "other";
    private static final List<String> PAGES = buildPages();

    private static final Map<CreativeModeTab, Integer> PAGE = new HashMap<>();
    // Whether a tab has items of more than one group. Worked out once per screen (ours() runs every frame).
    private static final Map<CreativeModeTab, Boolean> MULTI = new HashMap<>();
    private static CreativeModeTab current;
    private static Button left;
    private static Button right;

    private CreativeSubTabs() {
    }

    private static List<String> buildPages() {
        List<String> pages = new ArrayList<>();
        pages.add(PAGE_ALL);
        pages.addAll(UniverseItems.UNIVERSES);
        pages.add(PAGE_OTHER);
        return List.copyOf(pages);
    }

    private static boolean ours(CreativeModeTab tab) {
        if (tab == null) {
            return false;
        }
        boolean isModTab = false;
        for (var holder : ModCreativeTabs.ALL) {
            if (holder.get() == tab) {
                isModTab = true;
                break;
            }
        }
        if (!isModTab) {
            return false;
        }
        Boolean multi = MULTI.get(tab);
        if (multi == null) {
            multi = hasMultipleGroups(tab);
            // Only remembered once the tab is filled: an empty tab right after the screen opens is not the answer.
            if (!tab.getDisplayItems().isEmpty()) {
                MULTI.put(tab, multi);
            }
        }
        return multi;
    }

    private static boolean hasMultipleGroups(CreativeModeTab tab) {
        int groups = 0;
        for (String page : PAGES) {
            if (!PAGE_ALL.equals(page) && has(tab, page)) {
                groups++;
                if (groups > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String page(CreativeModeTab tab) {
        return PAGES.get(PAGE.getOrDefault(tab, 0));
    }

    private static boolean matches(ItemStack stack, String page) {
        if (PAGE_ALL.equals(page)) {
            return true;
        }
        String universe = UniverseItems.universe(stack.getItem());
        if (PAGE_OTHER.equals(page)) {
            return universe == null;
        }
        return page.equals(universe);
    }

    // Called by the mixin after vanilla filled the tab: drops what is not on the picked universe page. True if it changed it.
    public static boolean afterSelect(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        current = tab;
        if (!ours(tab)) {
            return false;
        }
        String wanted = page(tab);
        if (PAGE_ALL.equals(wanted)) {
            return false;
        }
        screen.getMenu().items.removeIf(stack -> !matches(stack, wanted));
        screen.getMenu().scrollTo(0);
        return true;
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) {
            return;
        }
        MULTI.clear();
        int x = screen.getGuiLeft();
        int y = screen.getGuiTop();
        left = Button.builder(Component.literal("<"), b -> flip(screen, -1)).bounds(x + 112, y + 4, 10, 10).build();
        right = Button.builder(Component.literal(">"), b -> flip(screen, 1)).bounds(x + 174, y + 4, 10, 10).build();
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
        Component name = Component.translatable("creative.battlegroundsmod.page." + page(current));
        var font = Minecraft.getInstance().font;
        int center = screen.getGuiLeft() + 148;
        int width = font.width(name);
        int maxW = 50;
        if (width <= maxW) {
            event.getGuiGraphics().drawString(font, name, center - width / 2, screen.getGuiTop() + 6, 0x404040, false);
        } else {
            float scale = (float) maxW / width;
            var pose = event.getGuiGraphics().pose();
            pose.pushPose();
            pose.translate(center, screen.getGuiTop() + 9.5f, 0);
            pose.scale(scale, scale, 1.0f);
            event.getGuiGraphics().drawString(font, name, -width / 2, -font.lineHeight / 2, 0x404040, false);
            pose.popPose();
        }
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

    // Next or previous universe page with something on it, then refills the tab.
    private static void flip(CreativeModeInventoryScreen screen, int step) {
        if (!ours(current)) {
            return;
        }
        int index = PAGE.getOrDefault(current, 0);
        for (int i = 0; i < PAGES.size(); i++) {
            index = Math.floorMod(index + step, PAGES.size());
            String candidate = PAGES.get(index);
            if (PAGE_ALL.equals(candidate) || has(current, candidate)) {
                break;
            }
        }
        PAGE.put(current, index);
        ((CreativeScreenAccessor) screen).battlegrounds$selectTab(current);
    }

    private static boolean has(CreativeModeTab tab, String page) {
        for (ItemStack stack : tab.getDisplayItems()) {
            if (matches(stack, page)) {
                return true;
            }
        }
        return false;
    }
}
