// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.client.event.ScreenEvent;

// A little speech bubble "Experiência definitiva" over the World Type button of the create world screen
// (Stark 2026-11-22). It pops up for about 2 seconds when the World tab shows Souls (or the button cycles
// back to Souls) and fades away. Only drawing: no widget, no click, nothing saved.
public final class SoulsWorldTypeBadge {
    public static final ResourceKey<WorldPreset> SOULS = ResourceKey.create(Registries.WORLD_PRESET,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "souls"));
    private static final Component TEXT = Component.translatable("createWorld.battlegroundsmod.souls_badge");

    private static final long POP_MS = 160;
    private static final long HOLD_MS = 1600;
    private static final long FADE_MS = 500;

    private static Screen screen;
    private static boolean shown;
    private static long start = -1;

    private SoulsWorldTypeBadge() {
    }

    public static boolean isSouls(WorldCreationUiState.WorldTypeEntry entry) {
        return entry != null && entry.preset() != null && entry.preset().is(SOULS);
    }

    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen create)) {
            screen = null;
            return;
        }
        if (screen != create) {
            screen = create;
            shown = false;
            start = -1;
        }
        CycleButton<?> button = null;
        for (GuiEventListener child : create.children()) {
            if (child instanceof CycleButton<?> cycle && cycle.visible && cycle.getValue() instanceof WorldCreationUiState.WorldTypeEntry) {
                button = cycle;
                break;
            }
        }
        boolean souls = button != null && isSouls((WorldCreationUiState.WorldTypeEntry) button.getValue());
        long now = Util.getMillis();
        if (souls && !shown) {
            start = now;
        }
        shown = souls;
        if (!souls || start < 0) {
            return;
        }
        long t = now - start;
        if (t > POP_MS + HOLD_MS + FADE_MS) {
            return;
        }
        float alpha = t < POP_MS ? t / (float) POP_MS
                : t < POP_MS + HOLD_MS ? 1f
                : 1f - (t - POP_MS - HOLD_MS) / (float) FADE_MS;
        alpha = Mth.clamp(alpha, 0f, 1f);
        if (alpha < 0.05f) {
            return;
        }
        // Small "pop": grows from 85% to 100% while it fades in.
        float pop = t < POP_MS ? 0.85f + 0.15f * (t / (float) POP_MS) : 1f;
        draw(event.getGuiGraphics(), button, alpha, pop);
    }

    private static void draw(GuiGraphics graphics, CycleButton<?> button, float alpha, float scale) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(TEXT);
        int w = textWidth + 10;
        int h = 14;
        // Tail tip touches the top of the button, near its right side (over the type name, away from the label).
        float tipX = button.getX() + button.getWidth() * 0.72f;
        float tipY = button.getY() + 1;
        graphics.pose().pushPose();
        graphics.pose().translate(tipX, tipY, 400);
        graphics.pose().scale(scale, scale, 1);
        int x1 = -w / 2;
        int y2 = -4;
        int y1 = y2 - h;
        int x2 = x1 + w;
        int bg = argb(alpha * 0.94f, 0x100010);
        int borderTop = argb(alpha, 0xE0B04A);
        int borderBottom = argb(alpha, 0x8A6A2A);
        // Body (vanilla tooltip shape: corners cut by one pixel).
        graphics.fill(x1 + 1, y1, x2 - 1, y2, bg);
        graphics.fill(x1, y1 + 1, x1 + 1, y2 - 1, bg);
        graphics.fill(x2 - 1, y1 + 1, x2, y2 - 1, bg);
        // Gold border.
        graphics.fillGradient(x1 + 1, y1 + 1, x1 + 2, y2 - 1, borderTop, borderBottom);
        graphics.fillGradient(x2 - 2, y1 + 1, x2 - 1, y2 - 1, borderTop, borderBottom);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, borderTop);
        graphics.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, borderBottom);
        // Tail: a 3-step triangle pointing down at the button.
        graphics.fill(-3, y2 - 1, 3, y2, bg);
        graphics.fill(-2, y2, 2, y2 + 1, bg);
        graphics.fill(-1, y2 + 1, 1, y2 + 2, bg);
        graphics.fill(-3, y2 - 2, -2, y2, borderBottom);
        graphics.fill(2, y2 - 2, 3, y2, borderBottom);
        graphics.fill(-2, y2, -1, y2 + 1, borderBottom);
        graphics.fill(1, y2, 2, y2 + 1, borderBottom);
        graphics.fill(-1, y2 + 1, 1, y2 + 3, borderBottom);
        graphics.drawString(font, TEXT, x1 + 5, y1 + 3, argb(alpha, 0xFFD966), true);
        graphics.pose().popPose();
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        return a << 24 | (rgb & 0xFFFFFF);
    }
}
