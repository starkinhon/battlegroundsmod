// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

// The player's tier in the inventory (Stark's test 2026-11-10: "tem que ter algum jeito de mostrar o meu tier... no E").
// Since 2026-11-23 (preview "Tier no inventário", replacing the dark box with the gold line over the inventory): inside the
// inventory, in the empty spot under the crafting grid, with no box of its own (Stark: "bota ele aqui, ao invés de
// adicionar uma caixinha nova"). Tier name, the Poder in numbers under it and the bar to the next tier; only the name with
// the progression off or at the top tier. Soul shades on the soul inventory, vanilla gray and green on Minecraft's. The
// recipe book button, that sat in that spot, moves next to the "Crafting" title (InventoryScreenMixin).
public final class TierInventoryInfo {
    // The spot under the crafting grid, relative to the inventory box.
    private static final int AREA_X = 97;
    private static final int AREA_Y = 56;
    private static final int AREA_W = 73;
    // Where the recipe book button goes, relative to the inventory box.
    public static final int RECIPE_X = 151;
    public static final int RECIPE_Y = 5;

    private TierInventoryInfo() {
    }

    // Called from InventoryScreenMixin after the inventory background (soul or vanilla).
    public static void draw(GuiGraphics g, int left, int top) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        PowerData data = minecraft.player.getData(ModAttachments.POWER);
        Font font = minecraft.font;
        String name = Tiers.name(data.tier).getString();
        double need = Progression.clientOn ? Progression.cost(data.tier) : 0;
        boolean bar = need > 0;
        String nums = bar ? Math.round(data.poder) + " / " + Math.round(need) : "";
        float frac = bar ? Mth.clamp((float) (data.poder / need), 0, 1) : 1;
        int x = left + AREA_X;
        int y = top + AREA_Y;
        RenderSystem.enableBlend();
        if (CrystalInventory.active() && CrystalInventory.currentRamp() != null) {
            soul(g, font, x, y, bar, name, nums, frac);
        } else {
            vanilla(g, font, x, y, bar, name, nums, frac);
        }
        RenderSystem.disableBlend();
    }

    private static void centered(GuiGraphics g, Font font, String s, int x, int y, int color, boolean shadow) {
        g.drawString(font, s, x + (AREA_W - font.width(s)) / 2, y, color, shadow);
    }

    private static void soul(GuiGraphics g, Font font, int x, int y, boolean bar, String name, String nums, float frac) {
        int[] R = CrystalInventory.currentRamp();
        boolean light = CrystalInventory.currentLight();
        float t = (Util.getMillis() % 3_600_000L) / 1000f;
        centered(g, font, name, x, bar ? y : y + 8, 0xFF000000 | (light ? R[1] : R[6]), !light);
        if (!bar) {
            return;
        }
        centered(g, font, nums, x, y + 9, 0xFF000000 | (light ? R[3] : R[4]), !light);
        // Poder bar: a glass groove, filled in the soul's color with a lit top and a glint running inside.
        int bx = x, by = y + 19, bw = AREA_W, f = Math.round((bw - 2) * frac);
        g.fill(bx, by, bx + bw, by + 5, CrystalInventory.argb(light ? R[3] : R[0], 0.95f));
        g.fill(bx + 1, by + 1, bx + bw - 1, by + 4, 0xFF000000 | (light ? R[5] : CrystalInventory.mix(R[1], R[0], 0.45f)));
        g.fill(bx + 1, by + 4, bx + bw - 1, by + 5, CrystalInventory.argb(light ? 0xFFFFFF : R[2], 0.6f));
        if (f > 0) {
            g.fill(bx + 1, by + 1, bx + 1 + f, by + 4, 0xFF000000 | (light ? R[3] : R[4]));
            g.fill(bx + 1, by + 1, bx + 1 + f, by + 2, 0xFF000000 | (light ? R[5] : R[6]));
            int p = (int) Math.floor((t * 40) % (f + 30)) - 15;
            for (int k = 0; k < 6; k++) {
                int xx = p + k;
                if (xx >= 0 && xx < f) {
                    g.fill(bx + 1 + xx, by + 1, bx + 2 + xx, by + 4, CrystalInventory.argb(0xFFFFFF, 0.35f - Math.abs(k - 2.5f) * 0.1f));
                }
            }
        }
        for (int i = 1; i < 10; i++) {
            int tx = bx + 1 + i * (bw - 2) / 10;
            g.fill(tx, by + 1, tx + 1, by + 4, CrystalInventory.argb(light ? R[4] : R[0], 0.45f));
        }
    }

    private static void vanilla(GuiGraphics g, Font font, int x, int y, boolean bar, String name, String nums, float frac) {
        centered(g, font, name, x, bar ? y : y + 8, 0xFF404040, false);
        if (!bar) {
            return;
        }
        centered(g, font, nums, x, y + 9, 0xFF707070, false);
        int bx = x, by = y + 19, bw = AREA_W, f = Math.round((bw - 2) * frac);
        g.fill(bx, by, bx + bw, by + 5, 0xFF373737);
        g.fill(bx + 1, by + 4, bx + bw, by + 5, 0xFFFFFFFF);
        g.fill(bx + bw - 1, by + 1, bx + bw, by + 5, 0xFFFFFFFF);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + 4, 0xFF8B8B8B);
        if (f > 0) {
            g.fill(bx + 1, by + 1, bx + 1 + f, by + 4, 0xFF55C83A);
            g.fill(bx + 1, by + 1, bx + 1 + f, by + 2, 0xFF9CF07A);
        }
    }
}
