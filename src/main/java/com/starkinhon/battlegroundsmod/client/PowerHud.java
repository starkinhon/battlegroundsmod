package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// Top-left HUD: power name and the power's skills with their key and cooldown. While Shift is held it lists the Shift
// variants instead (the keys stay the same). The ult charge is the red bar in place
// of the XP bar during combat (UltBar), and the base attacks have their own selector above the hotbar (BaseAttacks).
public final class PowerHud {
    private static final int X = 6;
    private static final int Y = 6;
    private static final int WIDTH = 190;
    private static final int ROW_HEIGHT = 11;
    private static final int ULT_COLOR = 0xFFF2C94C;

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
        boolean shift = minecraft.options.keyShift.isDown() && !power.shiftSlots().isEmpty();
        var slots = shift ? power.shiftSlots() : power.slots();
        int rows = slots.size();
        graphics.fill(X - 3, Y - 3, X + WIDTH, Y + 14 + rows * ROW_HEIGHT, 0x88000000);
        Component tier = Component.literal(" [" + Tiers.name(data.tier) + "]");
        graphics.drawString(font, power.name(), X, Y, 0xFF000000 | power.color(), true);
        graphics.drawString(font, tier, X + font.width(power.name()), Y, 0xFFAAAAAA, true);
        if (shift) {
            graphics.drawString(font, "Shift", X + WIDTH - 6 - font.width("Shift"), Y, 0xFF000000 | power.color(), true);
        }

        boolean ultReady = data.ult >= PowerData.MAX_ULT;
        int y = Y + 13;
        for (int slot : slots) {
            Component key = Keybinds.forSlot(power, slot).getTranslatedKeyMessage();
            int cooldown = data.cooldowns[slot];
            boolean ult = PowerData.isUlt(slot);
            boolean blocked = cooldown > 0 || (ult && !ultReady);
            int color = blocked ? 0xFF777777 : ult ? ULT_COLOR : 0xFFFFFFFF;
            Component line = Component.literal("[").append(key).append("] ").append(power.abilityName(slot));
            graphics.drawString(font, line, X, y, color, true);
            if (cooldown >= PowerData.SPENT / 2) {
                graphics.drawString(font, Component.translatable("hud.battlegroundsmod.spent"), X + font.width(line) + 4, y, 0xFFFF6B6B, true);
            } else if (cooldown > 0) {
                graphics.drawString(font, String.format(" %.1fs", cooldown / 20f), X + font.width(line), y, 0xFFFF6B6B, true);
            }
            y += ROW_HEIGHT;
        }
    }
}
