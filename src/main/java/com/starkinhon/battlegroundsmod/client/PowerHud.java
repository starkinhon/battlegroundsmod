// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Tiers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// Skill column on the right side of the screen, in the same look as the hotbar (Stark 2026-11-22, approved in the web
// preview "Diário Sem Cara de IA", aba Coluna): a plate with cut corners in the soul's shades, a glow breathing behind,
// a band of light sweeping the frame and little stars. One glass slot per skill in key order, the key on a tiny plate at
// the left edge, and the ult a bit bigger at the bottom under a notch. The power's name and tier sit above, with the
// Poder bar on the plate's top edge. A skill on cooldown darkens from the top with a lit edge and the seconds on it; a
// band of light runs over the icon when it comes back. The ult fills with a wavy line of light and gets a bright rim
// when full. While Shift is held the column shows the Shift variants (small up arrow). The skill names show next to the
// keys for a moment when the power changes or Shift is pressed. The base attacks have their own selector above the
// hotbar (BaseAttacks).
public final class PowerHud {
    private static final int SLOT = 22;
    private static final int GAP = 5;
    private static final int ULT_GAP = 11;
    private static final int W = 34;
    private static final int MARGIN = 6;
    private static final int GLOW_PAD = 14;
    private static final long READY_MS = 700;
    private static final long NAMES_MS = 2600;

    // The plate texture, rebuilt when the soul, the style or the column height changes.
    private static boolean broken;
    private static String builtFor;
    private static ResourceLocation plateTex;
    private static ResourceLocation glowTex;
    private static int[] lit;
    private static int[] spots;

    // Longest cooldown seen since each slot last came back, so the shade knows how full it started.
    private static final int[] FULL = new int[PowerData.SLOT_COUNT];
    private static final int[] LAST = new int[PowerData.SLOT_COUNT];
    private static final long[] FLASH = new long[PowerData.SLOT_COUNT];
    private static String lastPower = "";
    private static int lastSwaps;
    private static boolean lastShift;
    private static long namesUntil;

    private PowerHud() {
    }

    private static boolean cutscene() {
        return com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene.hidesHud() || GrabCutscene.active() || BopCutscene.active() || HakaiCutscene.active() || EsferaCutscene.active()
                || BossCutscene.active() || ShoulderCutscene.active();
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || cutscene() || com.starkinhon.battlegroundsmod.client.fx.DuelScene.cutscene()) {
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
        boolean shiftHeld = minecraft.options.keyShift.isDown();
        boolean shift = shiftHeld && !power.shiftSlots().isEmpty();
        if (!power.id().equals(lastPower) || shiftHeld != lastShift || lastSwaps != com.starkinhon.battlegroundsmod.power.SkillSwap.clientVersion) {
            lastSwaps = com.starkinhon.battlegroundsmod.power.SkillSwap.clientVersion;
            namesUntil = now + NAMES_MS;
            lastPower = power.id();
            lastShift = shiftHeld;
        }

        // The slots shown, in key order with the ult last: each key's Shift variant while Shift is held.
        List<Integer> slots = new ArrayList<>();
        List<Integer> keys = new ArrayList<>(power.slots());
        if (!keys.contains(PowerData.SLOT_EXTRA) && keys.contains(PowerData.SLOT_ULT)
                && com.starkinhon.battlegroundsmod.power.SkillSwap.ref(minecraft.player, PowerData.SLOT_EXTRA) != null) {
            // a fifth skill from the Dungeon's Montar Kit on a soul with no B: its key shows up before the ult
            keys.add(keys.indexOf(PowerData.SLOT_ULT), PowerData.SLOT_EXTRA);
        }
        for (int slot : keys) {
            int kitShift = com.starkinhon.battlegroundsmod.power.SkillSwap.shiftOf(slot);
            if (shiftHeld && kitShift >= 0 && com.starkinhon.battlegroundsmod.power.SkillSwap.ref(minecraft.player, kitShift) != null) {
                // a Shift variant built in Montar Kit
                slots.add(kitShift);
                continue;
            }
            // a key holding another soul's skill (the Dungeon's Modo Caos, SkillSwap) shows that skill with Shift too
            boolean swapped = com.starkinhon.battlegroundsmod.power.SkillSwap.ref(minecraft.player, slot) != null;
            slots.add(shift && !swapped ? power.shiftSlot(power.slotKey(slot)) : slot);
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

        int[] R = CrystalInventory.rampFor(power.id());
        float hue = CrystalInventory.hueFor(power.id());
        boolean light = com.starkinhon.battlegroundsmod.client.fx.FxConfig.inventoryStyle() == 1;
        float t = (now % 3_600_000L) / 1000f;

        // Layout: one glass slot per key, the ult a bit bigger at the bottom under a notch.
        int count = slots.size();
        int[] ys = new int[count];
        int cursor = 6;
        for (int i = 0; i < count; i++) {
            boolean ult = PowerData.isUlt(slots.get(i));
            if (ult && i > 0) {
                cursor += ULT_GAP - GAP;
            }
            ys[i] = cursor;
            cursor += size(ult) + GAP;
        }
        int columnHeight = cursor - GAP + 6;
        int right = graphics.guiWidth() - MARGIN;
        int left = right - W;
        int headerHeight = 24;
        int top = Math.max(headerHeight + 4, (graphics.guiHeight() - columnHeight) / 2 + headerHeight / 2);

        build(power.id(), light, columnHeight, R, hue);
        RenderSystem.enableBlend();
        // The glow breathing behind, the plate, the light band and the stars (same as the hotbar).
        if (glowTex != null) {
            graphics.setColor(1, 1, 1, 0.7f + 0.3f * Mth.sin(t * 1.3f));
            graphics.blit(glowTex, left - GLOW_PAD, top - GLOW_PAD, 0, 0, W + GLOW_PAD * 2, columnHeight + GLOW_PAD * 2,
                    W + GLOW_PAD * 2, columnHeight + GLOW_PAD * 2);
            graphics.setColor(1, 1, 1, 1);
        }
        if (plateTex != null) {
            graphics.blit(plateTex, left, top, 0, 0, W, columnHeight, W, columnHeight);
            CrystalInventory.sweep(graphics, lit, left, top, t, W, columnHeight, light ? 0xFFFFFFFF : R[6]);
        } else {
            graphics.fill(left, top, right, top + columnHeight, 0xC8050308);
        }

        header(graphics, font, power, data, shift, left, right, top, R, light);

        float namesAlpha = Mth.clamp((namesUntil - now) / 500f, 0, 1);
        boolean ultReady = data.ult >= PowerData.MAX_ULT;
        for (int i = 0; i < count; i++) {
            int slot = slots.get(i);
            boolean ult = PowerData.isUlt(slot);
            int s = size(ult);
            int x = left + (W - s) / 2;
            int y = top + ys[i];
            if (ult && i > 0) {
                notch(graphics, left, y - (ULT_GAP - GAP) / 2 - GAP / 2 - 1, R, light);
            }
            boolean variant = shiftHeld && slot >= PowerData.SLOT_SHIFT;
            slot(graphics, font, power, data, slot, x, y, s, ult, ultReady, variant, now, t, R, hue, light);
            int keyLeft = keyChip(graphics, font, power, slot, left + 3, y + s / 2 - 5, data.cooldowns[slot] > 0, R, light);
            if (namesAlpha > 0.03f) {
                name(graphics, font, power, slot, keyLeft - 3, y + s / 2 - 4, ult, namesAlpha, R);
            }
        }
        if (spots != null) {
            CrystalInventory.stars(graphics, spots, left, top, t, new java.util.Random(builtFor.hashCode() * 13L + 1), R, light, 4);
        }
        RenderSystem.disableBlend();
    }

    private static int size(boolean ult) {
        return ult ? SLOT + 4 : SLOT;
    }

    // The plate (and its glow) built into a texture once per soul, style and column height.
    private static void build(String soul, boolean light, int h, int[] R, float hue) {
        String key = soul + "|" + light + "|" + h;
        if (key.equals(builtFor) || broken) {
            return;
        }
        try {
            int[] buf = new int[W * h];
            List<Integer> litList = new ArrayList<>();
            List<Integer> spotList = new ArrayList<>();
            CrystalInventory.plate(buf, W, 0, 0, W, h, 3, R, hue, light, false, litList, spotList);
            lit = CrystalInventory.toRelative(litList, 0, 0);
            spots = CrystalInventory.toRelative(spotList, 0, 0);
            Minecraft mc = Minecraft.getInstance();
            ResourceLocation plateLoc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/soul_column");
            ResourceLocation glowLoc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/soul_column_glow");
            // register() closes the texture it replaces.
            mc.getTextureManager().register(plateLoc, new DynamicTexture(CrystalInventory.image(buf, W, h)));
            mc.getTextureManager().register(glowLoc, new DynamicTexture(CrystalInventory.glow(W + GLOW_PAD * 2, h + GLOW_PAD * 2,
                    light ? R[5] : R[4], light ? 0.3f : 0.4f)));
            plateTex = plateLoc;
            glowTex = glowLoc;
            builtFor = key;
        } catch (Exception e) {
            broken = true;
            plateTex = null;
            glowTex = null;
            lit = null;
            spots = null;
            BattlegroundsMod.LOGGER.error("Skill column plate failed, drawing a plain backing", e);
        }
    }

    private static void header(GuiGraphics graphics, Font font, Power power, PowerData data, boolean shift, int left, int right, int top,
                               int[] R, boolean light) {
        Component name = power.name();
        Component second = shift
                ? Component.literal("Shift")
                : Tiers.name(data.tier);
        double cost = com.starkinhon.battlegroundsmod.power.Progression.cost(data.tier);
        if (!shift && com.starkinhon.battlegroundsmod.power.Progression.clientOn && cost > 0) {
            // the Poder in numbers next to the tier (Stark 2026-11-10)
            second = second.copy().append(Component.literal("  " + Math.round(data.poder) + "/" + Math.round(cost))
                    .withStyle(s -> s.withColor(0x9A93A6)));
        }
        int nameColor = 0xFF000000 | CrystalInventory.mix(R[5], 0xFFFFFF, 0.2f);
        int secondColor = shift ? 0xFF000000 | R[6] : 0xFF000000 | CrystalInventory.mix(R[4], 0xC8C8C8, 0.5f);
        graphics.drawString(font, name, right - 3 - font.width(name), top - 23, nameColor, true);
        graphics.drawString(font, second, right - 3 - font.width(second), top - 13, secondColor, true);
        // The tier's Poder bar (power/Progression) on the plate's top edge: full = next tier.
        if (!com.starkinhon.battlegroundsmod.power.Progression.clientOn) {
            return;
        }
        float fraction = cost <= 0 ? 1 : Mth.clamp((float) (data.poder / cost), 0, 1);
        int barLeft = left + 4;
        int barRight = right - 4;
        int filled = barLeft + Math.round((barRight - barLeft) * fraction);
        graphics.fill(barLeft, top - 4, barRight, top - 2, 0xFF000000 | R[0]);
        if (filled > barLeft) {
            graphics.fill(barLeft, top - 4, filled, top - 2, 0xFF000000 | R[5]);
        }
    }

    // The notch before the ult: a dark groove with a lit line under it and two bright pixels in the middle.
    private static void notch(GuiGraphics graphics, int left, int y, int[] R, boolean light) {
        graphics.fill(left + 5, y, left + W - 5, y + 1, 0xFF000000 | (light ? R[2] : R[0]));
        graphics.fill(left + 5, y + 1, left + W - 5, y + 2, 0xFF000000 | (light ? 0xFFFFFF : R[2]));
        graphics.fill(left + W / 2 - 1, y, left + W / 2 + 1, y + 1, 0xFF000000 | (light ? R[3] : R[6]));
    }

    // The key on a tiny plate sticking out of the column's left edge (its right edge at rightEdge): outline, lit
    // top/left, shaded bottom/right, cut corners. Returns its left edge.
    private static int keyChip(GuiGraphics graphics, Font font, Power power, int slot, int rightEdge, int y, boolean cooling, int[] R,
                               boolean light) {
        String key = shortKey(Keybinds.forSlot(power, slot).getTranslatedKeyMessage().getString());
        int w = Math.max(11, font.width(key) + 4);
        int x = rightEdge - w;
        int h = 11;
        int outline = 0xFF000000 | (light ? R[2] : R[0]);
        int hi = 0xFF000000 | (light ? 0xFFFFFF : R[4]);
        int lo = 0xFF000000 | (light ? R[4] : R[1]);
        int body = light ? CrystalInventory.mix(R[6], R[5], 0.5f) : CrystalInventory.mix(R[1], R[0], 0.4f);
        graphics.fill(x + 1, y, x + w - 1, y + h, outline);
        graphics.fill(x, y + 1, x + w, y + h - 1, outline);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, lo);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 2, hi);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, body);
        int text = cooling ? 0xFF000000 | R[3] : 0xFF000000 | (light ? R[1] : R[6]);
        graphics.drawString(font, key, x + w / 2 - font.width(key) / 2 + 1, y + 2, text, false);
        return x;
    }

    private static void slot(GuiGraphics graphics, Font font, Power power, PowerData data, int slot, int x, int y, int s, boolean ult,
                             boolean ultReady, boolean variant, long now, float t, int[] R, float hue, boolean light) {
        int cooldown = data.cooldowns[slot];
        boolean spent = cooldown >= PowerData.SPENT / 2;
        boolean cooling = cooldown > 0;
        boolean waitingUlt = ult && !ultReady;
        // another soul's skill on this key (SkillSwap): its own icon, name and color
        com.starkinhon.battlegroundsmod.power.SkillSwap.Ref swap = com.starkinhon.battlegroundsmod.power.SkillSwap.ref(
                net.minecraft.client.Minecraft.getInstance().player, slot);
        float pulse = 0.5f + 0.5f * Mth.sin(now / 260f);

        int ix = x + 1;
        int iy = y + 1;
        int icon = s - 2;
        if (ult && ultReady && !cooling) {
            GuiDraw.glow(graphics, x + s / 2f, y + s / 2f, s * 1.1f, R[5] & 0xFFFFFF, 0.3f + 0.25f * pulse);
            RenderSystem.enableBlend();
        }
        CrystalInventory.slotFrame(graphics, x, y, s, R, hue, light);
        boolean locked = swap == null && !com.starkinhon.battlegroundsmod.power.SoulSync.awake(net.minecraft.client.Minecraft.getInstance().player, power, slot);
        SkillIcons.draw(graphics, font, swap != null ? swap.power() : power, swap != null ? swap.slot() : slot, ix, iy, icon,
                locked ? 0.22f : cooling ? 0.42f : 1f);
        RenderSystem.enableBlend();
        if (locked) {
            // Not awakened yet (Sincronia, Livro das Almas): a small padlock instead of the cooldown.
            SoulBookDraw.padlock(graphics, x + s / 2 - 4, y + s / 2 - 5);
            return;
        }

        if (cooling) {
            // The shade goes away from the top down, with a lit edge, and the seconds on it.
            float fraction = spent ? 1 : Mth.clamp(cooldown / (float) Math.max(1, FULL[slot]), 0, 1);
            int shade = Math.round(icon * fraction);
            graphics.fill(ix, iy, ix + icon, iy + shade, 0x99000000);
            if (shade < icon) {
                graphics.fill(ix, iy + shade, ix + icon, iy + shade + 1, CrystalInventory.argb(R[6], 0.9f));
            }
            // "Recarga em números" off (Experiência do jogador): only the shade; the ✕ of a spent skill stays
            if (spent || com.starkinhon.battlegroundsmod.client.fx.FxConfig.cooldownNumbers()) {
                Component left = Component.literal(spent ? "✕" : seconds(cooldown));
                graphics.drawString(font, left, x + s / 2 - font.width(left) / 2 + 1, y + s / 2 - 3, 0xFFFFFFFF, true);
            }
        } else if (waitingUlt) {
            // The ult charge rises from the bottom as a wavy line of light.
            float charge = Mth.clamp(data.ult / PowerData.MAX_ULT, 0, 1);
            for (int q = 0; q < icon; q++) {
                int surface = Mth.clamp(Math.round(icon * (1 - charge) + Mth.sin(q * 0.6f + t * 3) * 1.2f), 0, icon);
                if (surface > 0) {
                    graphics.fill(ix + q, iy, ix + q + 1, iy + surface, 0x73000000);
                }
                if (surface < icon) {
                    graphics.fill(ix + q, iy + surface, ix + q + 1, iy + icon, CrystalInventory.argb(R[4], 0.22f));
                    graphics.fill(ix + q, iy + surface, ix + q + 1, iy + surface + 1, CrystalInventory.argb(R[6], 0.95f));
                }
            }
        } else if (ult) {
            // Full: a bright rim, like the hotbar's selected slot, breathing.
            int rim = CrystalInventory.argb(light ? R[1] : R[6], 0.6f + 0.4f * pulse);
            graphics.fill(x - 3, y - 3, x + s + 3, y - 2, rim);
            graphics.fill(x - 3, y + s + 2, x + s + 3, y + s + 3, rim);
            graphics.fill(x - 3, y - 2, x - 2, y + s + 2, rim);
            graphics.fill(x + s + 2, y - 2, x + s + 3, y + s + 2, rim);
        }

        // The moment it comes back: a diagonal band of light runs over the icon once.
        long since = now - FLASH[slot];
        if (FLASH[slot] > 0 && since >= 0 && since < READY_MS && !cooling) {
            float p = since / (float) READY_MS * (icon * 2 + 6) - 6;
            for (int k = 0; k < 4; k++) {
                int c = CrystalInventory.argb(R[6], k == 1 || k == 2 ? 0.55f : 0.25f);
                for (int q = 0; q < icon; q++) {
                    int d = Math.round(p + k - q);
                    if (d >= 0 && d < icon) {
                        graphics.fill(ix + d, iy + q, ix + d + 1, iy + q + 1, c);
                    }
                }
            }
        }

        if (swap != null) {
            // stolen: a small square in the owner's color in the bottom right corner
            int owner = SoulColors.of(swap.power());
            graphics.fill(x + s - 5, y + s - 5, x + s, y + s, 0xFF000000 | R[0]);
            graphics.fill(x + s - 4, y + s - 4, x + s - 1, y + s - 1, 0xFF000000 | owner);
        }
        if (variant) {
            // Shift variant: a small up arrow in the top right corner
            int c = 0xFF000000 | (light ? R[1] : R[6]);
            int ax = x + s - 5;
            int ay = y + 2;
            graphics.fill(ax + 1, ay, ax + 2, ay + 1, c);
            graphics.fill(ax, ay + 1, ax + 3, ay + 2, c);
            graphics.fill(ax + 1, ay + 2, ax + 2, ay + 4, c);
        }
    }

    private static void name(GuiGraphics graphics, Font font, Power power, int slot, int right, int y, boolean ult, float alpha, int[] R) {
        net.minecraft.world.entity.player.Player who = net.minecraft.client.Minecraft.getInstance().player;
        Component name = com.starkinhon.battlegroundsmod.power.SkillSwap.ref(who, slot) != null
                ? com.starkinhon.battlegroundsmod.power.SkillSwap.name(who, power, slot)
                : com.starkinhon.battlegroundsmod.power.powers.MoonDemon.abilityName(power, who, slot);
        int width = font.width(name);
        int a = Math.max(8, Math.round(alpha * 255));
        graphics.drawString(font, name, right - width, y, GuiDraw.argb(a, ult ? R[6] & 0xFFFFFF : 0xFFFFFF), true);
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
