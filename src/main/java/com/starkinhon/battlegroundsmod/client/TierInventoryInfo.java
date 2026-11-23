// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ScreenEvent;

// The player's tier in the inventory (Stark's test 2026-11-10: "tem que ter algum jeito de mostrar o meu tier... no E").
// Since 2026-11-23 (preview "Tier no inventário", replacing the dark box with the gold line): a small plate with cut
// corners pinned on top of the inventory, overlapping its top edge by 3 px. Tier name on the left, the Poder in numbers
// on the right and the bar to the next tier under them; only the name with the progression off or at the top tier. On
// the soul inventory it is the same plate in the soul's shades, with the light band and the little stars; on the vanilla
// inventory (Minecraft style, or no soul) it is vanilla's gray panel.
public final class TierInventoryInfo {
    private static final int TAG_W = 120;
    private static final int OVERLAP = 3;
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/soul_tier_tag");

    private static String builtFor;
    private static int[] lit = new int[0];
    private static int[] spots = new int[0];

    private TierInventoryInfo() {
    }

    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
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
        int w = Math.max(TAG_W, font.width(name) + font.width(nums) + 24);
        int h = bar ? 25 : 17;
        int x = screen.getGuiLeft() + (176 - w) / 2;
        int y = screen.getGuiTop() - h + OVERLAP;
        GuiGraphics g = event.getGuiGraphics();
        RenderSystem.enableBlend();
        if (CrystalInventory.active() && CrystalInventory.currentRamp() != null) {
            soulTag(g, font, x, y, w, h, bar, name, nums, frac);
        } else {
            vanillaTag(g, font, x, y, w, h, bar, name, nums, frac);
        }
        RenderSystem.disableBlend();
    }

    private static void soulTag(GuiGraphics g, Font font, int x, int y, int w, int h, boolean bar, String name, String nums, float frac) {
        int[] R = CrystalInventory.currentRamp();
        boolean light = CrystalInventory.currentLight();
        String key = java.util.Arrays.toString(R) + "|" + light + "|" + w + "x" + h;
        if (!key.equals(builtFor)) {
            int[] buf = new int[w * h];
            List<Integer> litList = new ArrayList<>();
            List<Integer> spotList = new ArrayList<>();
            CrystalInventory.plate(buf, w, 0, 0, w, h, 3, R, CrystalInventory.currentHue(), light, false, litList, spotList);
            lit = CrystalInventory.toRelative(litList, 0, 0);
            spots = CrystalInventory.toRelative(spotList, 0, 0);
            // register() closes the texture it replaces.
            Minecraft.getInstance().getTextureManager().register(TEX, new DynamicTexture(CrystalInventory.image(buf, w, h)));
            builtFor = key;
        }
        float t = (Util.getMillis() % 3_600_000L) / 1000f;
        g.blit(TEX, x, y, 0, 0, w, h, w, h);
        CrystalInventory.sweep(g, lit, x, y, t, w, h, light ? 0xFFFFFFFF : R[6]);
        int nameC = 0xFF000000 | (light ? R[1] : R[6]);
        int numC = 0xFF000000 | (light ? R[3] : R[4]);
        if (bar) {
            g.drawString(font, name, x + 6, y + 4, nameC, !light);
            g.drawString(font, nums, x + w - 6 - font.width(nums), y + 4, numC, !light);
            // Poder bar: a glass groove, filled in the soul's color with a lit top and a glint running inside.
            int bx = x + 6, by = y + 15, bw = w - 12, f = Math.round((bw - 2) * frac);
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
        } else {
            g.drawString(font, name, x + (w - font.width(name)) / 2, y + 5, nameC, !light);
        }
        CrystalInventory.stars(g, spots, x, y, t, new Random(key.hashCode() * 31L + 5), R, light, 3);
    }

    private static void vanillaTag(GuiGraphics g, Font font, int x, int y, int w, int h, boolean bar, String name, String nums, float frac) {
        // Vanilla's gray panel: black outline with rounded corners, white top/left, dark bottom/right.
        g.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
        g.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFC6C6C6);
        if (bar) {
            g.drawString(font, name, x + 6, y + 4, 0xFF404040, false);
            g.drawString(font, nums, x + w - 6 - font.width(nums), y + 4, 0xFF707070, false);
            int bx = x + 6, by = y + 15, bw = w - 12, f = Math.round((bw - 2) * frac);
            g.fill(bx, by, bx + bw, by + 5, 0xFF373737);
            g.fill(bx + 1, by + 4, bx + bw, by + 5, 0xFFFFFFFF);
            g.fill(bx + bw - 1, by + 1, bx + bw, by + 5, 0xFFFFFFFF);
            g.fill(bx + 1, by + 1, bx + bw - 1, by + 4, 0xFF8B8B8B);
            if (f > 0) {
                g.fill(bx + 1, by + 1, bx + 1 + f, by + 4, 0xFF55C83A);
                g.fill(bx + 1, by + 1, bx + 1 + f, by + 2, 0xFF9CF07A);
            }
        } else {
            g.drawString(font, name, x + (w - font.width(name)) / 2, y + 5, 0xFF404040, false);
        }
    }
}
