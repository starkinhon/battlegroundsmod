// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// The base attack selector, every power has it: Dash Attack (see combat/DashAttack), Ataque Forte (charges while G is
// held) and Ataque de barragem (lasts longer while G is held). R picks the next one (Shift + R the one before), G uses;
// using one starts combat mode. Drawn above the
// hearts and food (and the held item's name), over the hotbar: the picked attack's name with a dot for each choice. The GUI scale setting scales it like the rest of the HUD.
public final class BaseAttacks {
    private static final String[] NAMES = {
            "hud.battlegroundsmod.dash_attack",
            "hud.battlegroundsmod.strong_attack",
            "hud.battlegroundsmod.barrage"
    };
    // The ability slot each one uses.
    private static final int[] SLOTS = {PowerData.SLOT_DASH_ATTACK, PowerData.SLOT_STRONG, PowerData.SLOT_BARRAGE};
    private static final int SWITCH_FLASH_TICKS = 8;
    // Souls whose name shows next to "Ultimate" over the ult bar.
    private static final String ULT_TITLE_WITH_SOUL = "uchiha_ghost";

    private static int selected = 0;
    private static int switchedAt = -100;
    private static boolean heldSent = false;
    // The top of the selector's backing, the highest thing of the bottom stack, from the last frame (MIN_VALUE when it
    // was not drawn). StatusBars lifts vanilla's action bar message above it.
    static int stackTop = Integer.MIN_VALUE;

    private BaseAttacks() {
    }

    public static void cycle(boolean back) {
        selected = Math.floorMod(selected + (back ? -1 : 1), NAMES.length);
        var player = Minecraft.getInstance().player;
        switchedAt = player == null ? 0 : player.tickCount;
    }

    public static void use() {
        PacketDistributor.sendToServer(new ModPayloads.UseAbility(SLOTS[selected]));
    }

    // Tells the server when the use key goes down or up, so a held Forte charges and a held Barragem lasts longer.
    public static void holdKey(boolean down) {
        if (down != heldSent && Minecraft.getInstance().getConnection() != null) {
            heldSent = down;
            PacketDistributor.sendToServer(new ModPayloads.AttackHold(down));
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        stackTop = Integer.MIN_VALUE;
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (minecraft.options.hideGui || player == null || player.isSpectator()) {
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        if (!com.starkinhon.battlegroundsmod.combat.CombatMode.fighter(player)) {
            return;
        }
        Font font = minecraft.font;
        int center = graphics.guiWidth() / 2;
        // Right above whichever side of the status bars stands taller (hearts and armor on the left, food and air on the
        // right). The held item's name, which vanilla shows there, goes above this stack instead (Stark 2026-11-22).
        // leftHeight/rightHeight point at the next free row, 10 px above the last drawn one: +8 sits the stack 2 px over
        // that last row (Stark 2026-11-22: "Ultimate" glued to the hearts/armor).
        int top = graphics.guiHeight() - Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight) + 8;
        if (data.combat && data.hasPower()) {
            top = ultTitle(graphics, font, data, player.tickCount, center, top);
        }
        // Sukuna's Modelo bar gets its own row between the ult title and the selector, so nothing hides behind it.
        if (ModelBarHud.showing(data)) {
            top = ModelBarHud.placeAbove(top);
        }
        int slot = SLOTS[selected];
        int cooldown = data.cooldowns[slot];
        Component name = Component.translatable(NAMES[selected]);
        int width = font.width(name);
        float flash = Mth.clamp(1 - (player.tickCount - switchedAt) / (float) SWITCH_FLASH_TICKS, 0, 1);
        int color = cooldown > 0 ? 0xFF8A8A8A : lerp(0xFFFFFFFF, 0xFFFFD84A, flash);
        int textY = top - font.lineHeight;
        stackTop = textY - 2;
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
        itemName(graphics, font, minecraft, center, stackTop);
    }

    // True while this stack sits over the status bars: vanilla's held item name is cancelled (StatusBars) and drawn
    // above the stack by itemName, as vanilla draws it (rarity color, italic when renamed, fading out).
    static boolean movesItemName() {
        return stackTop != Integer.MIN_VALUE;
    }

    private static void itemName(GuiGraphics graphics, Font font, Minecraft minecraft, int center, int above) {
        net.minecraft.client.gui.Gui gui = minecraft.gui;
        net.minecraft.world.item.ItemStack stack = gui.lastToolHighlight;
        if (gui.toolHighlightTimer <= 0 || stack.isEmpty()) {
            return;
        }
        net.minecraft.network.chat.MutableComponent text = Component.empty().append(stack.getHoverName()).withStyle(stack.getRarity().color());
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            text.withStyle(net.minecraft.ChatFormatting.ITALIC);
        }
        int alpha = Math.min(255, (int) (gui.toolHighlightTimer * 256f / 10f));
        if (alpha <= 0) {
            return;
        }
        int w = font.width(text);
        int x = center - w / 2;
        int y = above - font.lineHeight - 4;
        graphics.fill(x - 2, y - 2, x + w + 2, y + font.lineHeight + 2, minecraft.options.getBackgroundColor(0));
        graphics.drawString(font, text, x, y, 0xFFFFFF | alpha << 24, true);
    }

    // In combat mode, the ult bar's title, just over the hearts and food (the gap right above the bar is too
    // narrow for it): "Ultimate", plus the soul's name for the Uchiha ghost. Blinks when the ult is ready.
    // Returns where the selector goes, above it.
    private static int ultTitle(GuiGraphics graphics, Font font, PowerData data, int tick, int center, int top) {
        Power power = Powers.get(data.power);
        Component title = Component.translatable("hud.battlegroundsmod.ultimate");
        if (power != null && ULT_TITLE_WITH_SOUL.equals(power.id())) {
            title = title.copy().append(" | ").append(power.name());
        }
        boolean ready = data.ult >= PowerData.MAX_ULT;
        // In the soul's color, like the bar (Stark 2026-11-19); blinks to the lightest shade when ready.
        int[] shades = StatusBars.soulRamp(data.power);
        int color = ready && (tick / 5) % 2 == 0 ? shades[6] : shades[5];
        int y = top - font.lineHeight;
        graphics.drawString(font, title, center - font.width(title) / 2, y, color, true);
        return y - 3;
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
