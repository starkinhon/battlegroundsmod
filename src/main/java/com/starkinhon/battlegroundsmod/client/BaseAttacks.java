package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// The base attack selector, every power has it: Ataque rápido (next punch of the combo), Ataque Forte and Ataque de
// barragem. R picks, G uses; using one starts combat mode. Drawn just above the hearts and food, over the hotbar: the
// picked attack's name with a dot for each choice. The GUI scale setting scales it like the rest of the HUD.
public final class BaseAttacks {
    private static final String[] NAMES = {
            "hud.battlegroundsmod.quick_attack",
            "hud.battlegroundsmod.strong_attack",
            "hud.battlegroundsmod.barrage"
    };
    // The ability slot each one uses; -1 is the punch.
    private static final int[] SLOTS = {-1, PowerData.SLOT_STRONG, PowerData.SLOT_BARRAGE};
    private static final int SWITCH_FLASH_TICKS = 8;

    private static int selected = 0;
    private static int switchedAt = -100;

    private BaseAttacks() {
    }

    public static void cycle() {
        selected = (selected + 1) % NAMES.length;
        var player = Minecraft.getInstance().player;
        switchedAt = player == null ? 0 : player.tickCount;
    }

    public static void use() {
        int slot = SLOTS[selected];
        PacketDistributor.sendToServer(slot < 0 ? new ModPayloads.Punch(true) : new ModPayloads.UseAbility(slot));
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (minecraft.options.hideGui || player == null || player.isSpectator()) {
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        if (!data.hasPower()) {
            return;
        }
        Font font = minecraft.font;
        int center = graphics.guiWidth() / 2;
        // Above whichever side of the status bars stands taller (hearts and armor on the left, food and air on the right).
        int top = graphics.guiHeight() - Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight) - 2;
        int slot = SLOTS[selected];
        int cooldown = slot < 0 ? 0 : data.cooldowns[slot];
        Component name = Component.translatable(NAMES[selected]);
        int width = font.width(name);
        float flash = Mth.clamp(1 - (player.tickCount - switchedAt) / (float) SWITCH_FLASH_TICKS, 0, 1);
        int color = cooldown > 0 ? 0xFF8A8A8A : lerp(0xFFFFFFFF, 0xFFFFD84A, flash);
        int textY = top - font.lineHeight;
        graphics.fill(center - width / 2 - 4, textY - 2, center + width / 2 + 4, textY + font.lineHeight + 4, 0x66000000);
        graphics.drawString(font, name, center - width / 2, textY, color, true);
        // One dot per attack, the picked one lit.
        int dotsY = textY + font.lineHeight + 1;
        for (int i = 0; i < NAMES.length; i++) {
            int x = center - (NAMES.length * 4) / 2 + i * 4;
            graphics.fill(x, dotsY, x + 2, dotsY + 2, i == selected ? 0xFFFFFFFF : 0xFF555555);
        }
        if (cooldown > 0) {
            String seconds = String.format(" %.1fs", cooldown / 20f);
            graphics.drawString(font, seconds, center + width / 2 + 5, textY, 0xFFFF6B6B, true);
        }
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
