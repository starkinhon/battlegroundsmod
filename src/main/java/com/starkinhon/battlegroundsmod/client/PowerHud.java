package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// Top-left HUD: power name, energy and ult bars, and every move with its key and cooldown.
public final class PowerHud {
    private static final int X = 6;
    private static final int Y = 6;
    private static final int BAR_WIDTH = 120;
    private static final int ROW_HEIGHT = 11;
    private static final int ENERGY_COLOR = 0xFF3FA9F5;
    private static final int ULT_COLOR = 0xFFF2C94C;

    private static final String[] MELEE_KEYS = {
            "hud.battlegroundsmod.strong_attack",
            "hud.battlegroundsmod.barrage"
    };

    private PowerHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        PowerData data = minecraft.player.getData(ModAttachments.POWER);
        Power power = Powers.get(data.power);
        if (power == null) {
            return;
        }
        Font font = minecraft.font;
        int rows = PowerData.SLOT_COUNT;
        graphics.fill(X - 3, Y - 3, X + BAR_WIDTH + 90, Y + 32 + rows * ROW_HEIGHT, 0x88000000);

        graphics.drawString(font, power.name(), X, Y, 0xFF000000 | power.color(), true);

        bar(graphics, Y + 12, data.energy / PowerData.MAX_ENERGY, ENERGY_COLOR);
        boolean ultReady = data.ult >= PowerData.MAX_ULT;
        // Blinks when the ult is ready.
        int ultColor = ultReady && (minecraft.player.tickCount / 5) % 2 == 0 ? 0xFFFFFFFF : ULT_COLOR;
        bar(graphics, Y + 20, data.ult / PowerData.MAX_ULT, ultColor);
        graphics.drawString(font, Component.translatable("hud.battlegroundsmod.energy"), X + BAR_WIDTH + 4, Y + 11, ENERGY_COLOR, true);
        graphics.drawString(font, Component.translatable(ultReady ? "hud.battlegroundsmod.ult_ready" : "hud.battlegroundsmod.ult"), X + BAR_WIDTH + 4, Y + 19, ULT_COLOR, true);

        int y = Y + 31;
        for (int slot = 0; slot < rows; slot++) {
            Component name = slot <= PowerData.SLOT_ULT ? power.abilityName(slot) : Component.translatable(MELEE_KEYS[slot - PowerData.SLOT_ULT - 1]);
            Component key = Keybinds.ALL.get(slot).getTranslatedKeyMessage();
            int cooldown = data.cooldowns[slot];
            boolean blocked = cooldown > 0 || (slot == PowerData.SLOT_ULT && !ultReady)
                    || (slot != PowerData.SLOT_ULT && data.energy < PowerLogic.ability(power, slot).energyCost());
            int color = blocked ? 0xFF777777 : slot == PowerData.SLOT_ULT ? ULT_COLOR : 0xFFFFFFFF;
            Component line = Component.literal("[").append(key).append("] ").append(name);
            graphics.drawString(font, line, X, y, color, true);
            if (cooldown > 0) {
                graphics.drawString(font, String.format(" %.1fs", cooldown / 20f), X + font.width(line), y, 0xFFFF6B6B, true);
            }
            y += ROW_HEIGHT;
        }
    }

    private static void bar(GuiGraphics graphics, int y, float fraction, int color) {
        graphics.fill(X, y, X + BAR_WIDTH, y + 5, 0xFF222222);
        graphics.fill(X, y, X + (int) (BAR_WIDTH * Math.max(0, Math.min(1, fraction))), y + 5, color);
    }
}
