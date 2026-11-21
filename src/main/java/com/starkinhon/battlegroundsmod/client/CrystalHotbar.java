// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import net.minecraft.Util;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// The hotbar in the same look as the inventory (CrystalInventory, Stark 2026-11-21): a plate with cut corners in the
// soul's color, no crystals, a glow breathing behind, a band of light sweeping the frame, glass slots and the selected
// slot lit. Follows the inventory's style buttons: "Escuro" and "Claro" are the dark and light plates, "Minecraft" keeps
// vanilla's hotbar. Also left to vanilla in spectator mode and when the attack indicator is set to show on the hotbar.
// Anything that fails turns it off and vanilla's hotbar comes back.
public final class CrystalHotbar {
    // Glow picture around the 182x22 hotbar, and the plate under it.
    private static final int W = 260;
    private static final int H = 60;
    private static final int PLATE_W = 188;
    private static final int PLATE_H = 25;

    private static boolean broken;
    private static String builtFor;
    private static int[] ramp;
    private static float hue;
    private static boolean light;
    private static ResourceLocation plateTex;
    private static ResourceLocation glowTex;
    private static int[] lit;
    private static int[] spots;

    private CrystalHotbar() {
    }

    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        int style = FxConfig.inventoryStyle();
        if (broken || !event.getName().equals(VanillaGuiLayers.HOTBAR) || style == 2) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
                || mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            return;
        }
        try {
            String soul = CrystalInventory.soulOf(player);
            if (soul.isEmpty()) {
                // No soul (Humano): Minecraft's own hotbar.
                return;
            }
            String key = soul + "|" + style;
            if (!key.equals(builtFor)) {
                build(soul, style == 1, key);
            }
            draw(event.getGuiGraphics(), player, event.getPartialTick().getGameTimeDeltaPartialTick(true));
            event.setCanceled(true);
        } catch (Exception e) {
            broken = true;
            builtFor = null;
            BattlegroundsMod.LOGGER.error("Soul hotbar failed, going back to the vanilla one", e);
        }
    }

    private static void draw(GuiGraphics g, Player player, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int[] R = ramp;
        float t = (Util.getMillis() % 3_600_000L) / 1000f;
        int hx = g.guiWidth() / 2 - 91;
        int hy = g.guiHeight() - 22;
        int ox = hx + 91 - W / 2;
        int oy = hy + 11 - H / 2;
        int px = hx - 3;
        int py = hy - 2;

        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, 0.7f + 0.3f * (float) Math.sin(t * 1.3));
        g.blit(glowTex, ox, oy, 0, 0, W, H, W, H);
        g.setColor(1, 1, 1, 1);
        g.blit(plateTex, px, py, 0, 0, PLATE_W, PLATE_H, PLATE_W, PLATE_H);
        CrystalInventory.sweep(g, lit, px, py, t, PLATE_W, PLATE_H, light ? 0xFFFFFFFF : R[6]);

        int selected = player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            int x = hx + 3 + i * 20;
            int y = hy + 3;
            CrystalInventory.slotFrame(g, x, y, 16, R, hue, light);
            if (i == selected) {
                // The selected slot: a bright rim (dark on the light plate) and a soft glow inside.
                int rim = light ? R[1] : R[6];
                g.fill(x - 3, y - 3, x + 19, y - 2, rim);
                g.fill(x - 3, y + 18, x + 19, y + 19, rim);
                g.fill(x - 3, y - 2, x - 2, y + 18, rim);
                g.fill(x + 18, y - 2, x + 19, y + 18, rim);
                g.fill(x, y, x + 16, y + 16, CrystalInventory.argb(R[4], 0.28f + 0.08f * (float) Math.sin(t * 4)));
            }
        }
        ItemStack offhand = player.getOffhandItem();
        int offX = 0;
        if (!offhand.isEmpty()) {
            offX = player.getMainArm().getOpposite() == HumanoidArm.LEFT ? hx - 26 : hx + 192;
            CrystalInventory.slotFrame(g, offX, hy + 3, 16, R, hue, light);
        }
        CrystalInventory.stars(g, spots, px, py, t, new Random(builtFor.hashCode() * 13L + 1), R, light, 5);

        // The items (with vanilla's pop when one is picked up).
        for (int i = 0; i < 10; i++) {
            ItemStack stack = i < 9 ? player.getInventory().items.get(i) : offhand;
            if (stack.isEmpty()) {
                continue;
            }
            int x = i < 9 ? hx + 3 + i * 20 : offX;
            int y = hy + 3;
            item(g, mc, player, stack, x, y, i + 1, partialTick);
        }
    }

    private static void item(GuiGraphics g, Minecraft mc, Player player, ItemStack stack, int x, int y, int seed, float partialTick) {
        float pop = stack.getPopTime() - partialTick;
        PoseStack pose = g.pose();
        if (pop > 0) {
            float f = 1 + pop / 5;
            pose.pushPose();
            pose.translate(x + 8, y + 12, 0);
            pose.scale(1 / f, (f + 1) / 2, 1);
            pose.translate(-(x + 8), -(y + 12), 0);
        }
        g.renderItem(player, stack, x, y, seed);
        if (pop > 0) {
            pose.popPose();
        }
        g.renderItemDecorations(mc.font, stack, x, y);
    }

    // The plate and the glow, built once per soul and style.
    private static void build(String soul, boolean lightStyle, String key) {
        hue = CrystalInventory.hueFor(soul);
        ramp = CrystalInventory.rampFor(soul);
        light = lightStyle;
        int[] R = ramp;
        int[] buf = new int[PLATE_W * PLATE_H];
        List<Integer> litList = new ArrayList<>();
        List<Integer> spotList = new ArrayList<>();
        CrystalInventory.plate(buf, PLATE_W, 0, 0, PLATE_W, PLATE_H, 3, R, hue, light, false, litList, spotList);
        lit = CrystalInventory.toRelative(litList, 0, 0);
        spots = CrystalInventory.toRelative(spotList, 0, 0);

        Minecraft mc = Minecraft.getInstance();
        ResourceLocation plateLoc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/soul_hotbar");
        ResourceLocation glowLoc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/soul_hotbar_glow");
        // register() closes the texture it replaces.
        mc.getTextureManager().register(plateLoc, new DynamicTexture(CrystalInventory.image(buf, PLATE_W, PLATE_H)));
        mc.getTextureManager().register(glowLoc, new DynamicTexture(CrystalInventory.glow(W, H, light ? R[5] : R[4], light ? 0.3f : 0.45f)));
        plateTex = plateLoc;
        glowTex = glowLoc;
        builtFor = key;
    }
}
