package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// Skill column on the right side of the screen: the power's name and tier on top, then one framed icon per skill with
// its key in the corner, top to bottom in key order, and the ult at the bottom under a small divider. A skill on
// cooldown darkens, with the shade going away from the bottom up and the seconds left on it; it flashes when it comes
// back. The ult fills with gold as the ult charge grows and glows when full. While Shift is held the column shows the
// Shift variants (marked with a small diamond). The skill names show next to the icons for a moment when the power
// changes or Shift is pressed. The base attacks have their own selector above the hotbar (BaseAttacks).
public final class PowerHud {
    private static final int SLOT = 22;
    private static final int ICON = 18;
    private static final int GAP = 4;
    private static final int ULT_GAP = 9;
    private static final int PAD = 5;
    private static final int MARGIN = 5;
    private static final int ULT_COLOR = 0xF2C94C;
    private static final long FLASH_MS = 420;
    private static final long NAMES_MS = 2600;

    // Longest cooldown seen since each slot last came back, so the shade knows how full it started.
    private static final int[] FULL = new int[PowerData.SLOT_COUNT];
    private static final int[] LAST = new int[PowerData.SLOT_COUNT];
    private static final long[] FLASH = new long[PowerData.SLOT_COUNT];
    private static String lastPower = "";
    private static boolean lastShift;
    private static long namesUntil;

    private PowerHud() {
    }

    private static boolean cutscene() {
        return GrabCutscene.active() || BopCutscene.active() || HakaiCutscene.active() || EsferaCutscene.active()
                || BossCutscene.active() || ShoulderCutscene.active();
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || cutscene()) {
            return;
        }
        PowerData data = minecraft.player.getData(ModAttachments.POWER);
        Power power = Powers.get(data.power);
        if (power == null) {
            lastPower = "";
            return;
        }
        long now = Util.getMillis();
        Font font = minecraft.font;
        boolean shift = minecraft.options.keyShift.isDown() && !power.shiftSlots().isEmpty();
        if (!power.id().equals(lastPower) || shift != lastShift) {
            namesUntil = now + NAMES_MS;
            lastPower = power.id();
            lastShift = shift;
        }

        // The slots shown, in key order with the ult last: each key's Shift variant while Shift is held.
        List<Integer> slots = new ArrayList<>();
        for (int slot : power.slots()) {
            slots.add(shift ? power.shiftSlot(power.slotKey(slot)) : slot);
        }
        for (int slot = 0; slot < PowerData.SLOT_COUNT; slot++) {
            int cooldown = data.cooldowns[slot];
            if (cooldown <= 0) {
                if (LAST[slot] > 0) {
                    FLASH[slot] = now;
                }
                FULL[slot] = 0;
            } else if (cooldown > FULL[slot] || LAST[slot] <= 0) {
                FULL[slot] = cooldown;
            }
            LAST[slot] = cooldown;
        }

        int count = slots.size();
        boolean hasUlt = slots.stream().anyMatch(PowerData::isUlt);
        int columnHeight = count * SLOT + (count - 1) * GAP + (hasUlt ? ULT_GAP - GAP : 0) + PAD * 2;
        int right = graphics.guiWidth() - MARGIN;
        int left = right - SLOT - PAD * 2;
        int headerHeight = 24;
        int top = Math.max(headerHeight + 4, (graphics.guiHeight() - columnHeight) / 2 + headerHeight / 2);
        int color = power.color();
        int bright = GuiDraw.mix(color, 0xFFFFFF, 0.35f);

        header(graphics, font, power, data, shift, left, right, top);

        // Column backing.
        graphics.fillGradient(left, top, right, top + columnHeight, 0xB0080510, 0xC8050308);
        GuiDraw.ornateFrame(graphics, left, top, right, top + columnHeight, GuiDraw.argb(150, GuiDraw.mix(color, 0x000000, 0.25f)),
                GuiDraw.argb(255, bright));

        float namesAlpha = Mth.clamp((namesUntil - now) / 500f, 0, 1);
        boolean ultReady = data.ult >= PowerData.MAX_ULT;
        int x = left + PAD;
        int y = top + PAD;
        for (int slot : slots) {
            boolean ult = PowerData.isUlt(slot);
            if (ult && hasUlt) {
                divider(graphics, left, right, y - GAP);
                y += ULT_GAP - GAP;
            }
            boolean variant = shift && slot >= PowerData.SLOT_SHIFT;
            slot(graphics, font, power, data, slot, x, y, ult, ultReady, variant, now);
            if (namesAlpha > 0.03f) {
                name(graphics, font, power, slot, x - 6, y + SLOT / 2 - 4, ult, namesAlpha);
            }
            y += SLOT + GAP;
        }
    }

    private static void header(GuiGraphics graphics, Font font, Power power, PowerData data, boolean shift, int left, int right, int top) {
        Component name = power.name();
        Component second = shift
                ? Component.literal("Shift")
                : Component.literal(Tiers.name(data.tier));
        int width = Math.max(font.width(name), font.width(second)) + 10;
        int x1 = Math.min(left, right - width);
        int y1 = top - 25;
        int color = power.color();
        graphics.fillGradient(x1, y1, right, top - 3, 0x00050308, 0xC0050308);
        graphics.fill(x1 + 4, top - 4, right, top - 3, GuiDraw.argb(200, color));
        graphics.fill(right - 1, y1 + 2, right, top - 3, GuiDraw.argb(200, color));
        graphics.drawString(font, name, right - 4 - font.width(name), y1 + 3, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.2f), true);
        graphics.drawString(font, second, right - 4 - font.width(second), y1 + 12, shift ? 0xFF000000 | color : 0xFF9A93A6, true);
    }

    private static void divider(GuiGraphics graphics, int left, int right, int y) {
        int mid = (left + right) / 2;
        int line = GuiDraw.argb(150, ULT_COLOR);
        int cy = y + (ULT_GAP) / 2;
        graphics.fill(left + 3, cy, mid - 3, cy + 1, line);
        graphics.fill(mid + 4, cy, right - 3, cy + 1, line);
        GuiDraw.diamond(graphics, mid + 0.5f, cy + 0.5f, 2.5f, GuiDraw.argb(255, ULT_COLOR));
        graphics.flush();
    }

    private static void slot(GuiGraphics graphics, Font font, Power power, PowerData data, int slot, int x, int y, boolean ult,
                             boolean ultReady, boolean variant, long now) {
        int cooldown = data.cooldowns[slot];
        boolean spent = cooldown >= PowerData.SPENT / 2;
        boolean cooling = cooldown > 0;
        boolean waitingUlt = ult && !ultReady;
        int color = power.color();
        float pulse = 0.5f + 0.5f * Mth.sin(now / 260f);

        int ix = x + (SLOT - ICON) / 2;
        int iy = y + (SLOT - ICON) / 2;
        if (ult && ultReady && !cooling) {
            GuiDraw.glow(graphics, x + SLOT / 2f, y + SLOT / 2f, SLOT * 1.15f, ULT_COLOR, 0.35f + 0.3f * pulse);
        }
        graphics.fill(x, y, x + SLOT, y + SLOT, 0xF0100A14);
        SkillIcons.draw(graphics, font, power, slot, ix, iy, ICON, cooling || waitingUlt ? 0.42f : 1f);

        if (cooling) {
            float fraction = spent ? 1 : Mth.clamp(cooldown / (float) Math.max(1, FULL[slot]), 0, 1);
            int shade = Math.round(ICON * fraction);
            graphics.fill(ix, iy, ix + ICON, iy + shade, 0x90000000);
            graphics.fill(ix, iy + shade, ix + ICON, Math.min(iy + ICON, iy + shade + 1), GuiDraw.argb(160, color));
            Component left = Component.literal(spent ? "✕" : seconds(cooldown));
            graphics.drawString(font, left, x + SLOT / 2 - font.width(left) / 2 + 1, y + SLOT / 2 - 3, 0xFFFFFFFF, true);
        } else if (waitingUlt) {
            // The ult charge rises from the bottom.
            float charge = Mth.clamp(data.ult / PowerData.MAX_ULT, 0, 1);
            int fill = Math.round(ICON * charge);
            graphics.fill(ix, iy + ICON - fill, ix + ICON, iy + ICON, GuiDraw.argb(70, ULT_COLOR));
            if (fill > 0) {
                graphics.fill(ix, iy + ICON - fill, ix + ICON, iy + ICON - fill + 1, GuiDraw.argb(220, ULT_COLOR));
            }
        }

        int frame;
        if (cooling) {
            frame = 0xFF3A3242;
        } else if (ult) {
            frame = ultReady ? 0xFF000000 | GuiDraw.mix(ULT_COLOR, 0xFFFFFF, 0.3f * pulse) : 0xFF6B5524;
        } else {
            frame = 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.2f);
        }
        GuiDraw.frame(graphics, x, y, x + SLOT, y + SLOT, frame);
        GuiDraw.frame(graphics, x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, GuiDraw.alpha(frame, 0.35f));

        // The moment it comes back: a white flash and a short glow.
        float flash = 1 - (now - FLASH[slot]) / (float) FLASH_MS;
        if (flash > 0 && FLASH[slot] > 0) {
            graphics.fill(ix, iy, ix + ICON, iy + ICON, GuiDraw.argb(Math.round(170 * flash), 0xFFFFFF));
            GuiDraw.glow(graphics, x + SLOT / 2f, y + SLOT / 2f, SLOT * (1.4f - 0.4f * flash), ult ? ULT_COLOR : color, 0.6f * flash);
        }

        // Key badge on the top left corner.
        String key = shortKey(Keybinds.forSlot(power, slot).getTranslatedKeyMessage().getString());
        int keyWidth = font.width(key) + 4;
        int kx = x - 3;
        int ky = y - 3;
        graphics.fill(kx, ky, kx + keyWidth, ky + 10, 0xF0050308);
        GuiDraw.frame(graphics, kx, ky, kx + keyWidth, ky + 10, frame);
        graphics.drawString(font, key, kx + 2, ky + 1, cooling ? 0xFFAAAAAA : 0xFFFFFFFF, false);

        if (variant) {
            GuiDraw.diamond(graphics, x + SLOT - 2, y + 2, 3.5f, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.5f));
            GuiDraw.diamond(graphics, x + SLOT - 2, y + 2, 1.5f, 0xFFFFFFFF);
            graphics.flush();
        }
    }

    private static void name(GuiGraphics graphics, Font font, Power power, int slot, int right, int y, boolean ult, float alpha) {
        Component name = power.abilityName(slot);
        int width = font.width(name);
        int a = Math.round(alpha * 255);
        graphics.fillGradient(right - width - 8, y - 2, right + 2, y + 10, GuiDraw.argb(Math.round(alpha * 150), 0x050308),
                GuiDraw.argb(Math.round(alpha * 150), 0x050308));
        graphics.drawString(font, name, right - width - 2, y, GuiDraw.argb(Math.max(a, 8), ult ? ULT_COLOR : 0xFFFFFF), true);
    }

    private static String seconds(int ticks) {
        float seconds = ticks / 20f;
        return seconds >= 10 ? String.valueOf(Mth.ceil(seconds)) : String.format("%.1f", seconds);
    }

    // Key names that fit the badge ("Left Alt" -> "LAl").
    static String shortKey(String key) {
        if (key.length() <= 2) {
            return key.toUpperCase();
        }
        return key.substring(0, 3);
    }
}
