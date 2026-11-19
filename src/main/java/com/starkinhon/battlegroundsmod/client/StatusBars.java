package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// Vanilla status bars changed by the mod:
// - In combat mode the XP bar becomes the ult bar: the same bar tinted red (its title is drawn by BaseAttacks).
// - Vanilla's action bar message ("Combat mode on!", warnings) is lifted above the base attack selector, so it never
//   lands on the ult title or the selector.
// - Above Tier 10 health goes past 20, so the hearts row stays one row of 10 hearts, each one a tenth of the max,
//   with the real numbers next to it (vanilla would stack dozens of rows).
public final class StatusBars {
    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final int BAR_WIDTH = 182;
    // Vanilla draws the action bar message's text from guiHeight - 72 down 9 pixels.
    private static final int OVERLAY_MESSAGE_TOP = 72;
    private static boolean overlayLifted;

    private StatusBars() {
    }

    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        boolean combat = data.hasPower() && data.combat;
        ResourceLocation name = event.getName();
        if (name.equals(VanillaGuiLayers.OVERLAY_MESSAGE) && BaseAttacks.stackTop != Integer.MIN_VALUE) {
            GuiGraphics graphics = event.getGuiGraphics();
            int textTop = graphics.guiHeight() - OVERLAY_MESSAGE_TOP;
            int lift = textTop + minecraft.font.lineHeight + 3 - BaseAttacks.stackTop;
            if (lift > 0) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, -lift, 0);
                overlayLifted = true;
            }
            return;
        }
        if (combat && name.equals(VanillaGuiLayers.EXPERIENCE_BAR)) {
            event.setCanceled(true);
            ultBar(event.getGuiGraphics(), data);
        } else if (combat && name.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            // The ult's name goes above the hearts instead (BaseAttacks), the XP level is hidden.
            event.setCanceled(true);
        } else if (name.equals(VanillaGuiLayers.PLAYER_HEALTH) && player.getMaxHealth() > 20.5f && minecraft.gameMode != null
                && minecraft.gameMode.canHurtPlayer()) {
            event.setCanceled(true);
            hearts(event.getGuiGraphics(), minecraft, player);
        }
    }

    public static void onRenderLayerPost(RenderGuiLayerEvent.Post event) {
        if (overlayLifted && event.getName().equals(VanillaGuiLayers.OVERLAY_MESSAGE)) {
            overlayLifted = false;
            event.getGuiGraphics().pose().popPose();
        }
    }

    private static void ultBar(GuiGraphics graphics, PowerData data) {
        int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        int y = graphics.guiHeight() - 32 + 3;
        int filled = (int) (Mth.clamp(data.ult / PowerData.MAX_ULT, 0, 1) * (BAR_WIDTH + 1));
        // In the soul's color (Stark 2026-11-19), same shades as the crystal inventory and hotbar.
        int[] R = soulRamp(data.power);
        RenderSystem.enableBlend();
        graphics.fill(x, y, x + BAR_WIDTH, y + 5, CrystalInventory.argb(R[0], 0.9f));
        graphics.fill(x + 1, y + 1, x + BAR_WIDTH - 1, y + 4, R[1]);
        int end = Math.min(x + filled, x + BAR_WIDTH - 1);
        if (end > x + 1) {
            graphics.fill(x + 1, y + 1, end, y + 4, R[4]);
            graphics.fill(x + 1, y + 1, end, y + 2, R[6]);
            graphics.fill(x + 1, y + 3, end, y + 4, R[3]);
        }
        // Little notches, like vanilla's bar.
        for (int i = 1; i < 10; i++) {
            int nx = x + i * BAR_WIDTH / 10;
            graphics.fill(nx, y + 1, nx + 1, y + 4, CrystalInventory.argb(R[0], 0.55f));
        }
    }

    private static String ultSoul;
    private static int[] ultRamp;

    // The soul's seven shades (dark to light), cached for the current soul. Used by the ult bar and its title.
    static int[] soulRamp(String power) {
        if (!power.equals(ultSoul)) {
            ultSoul = power;
            ultRamp = CrystalInventory.ramp(CrystalInventory.hueFor(power));
        }
        return ultRamp;
    }

    private static void hearts(GuiGraphics graphics, Minecraft minecraft, Player player) {
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - minecraft.gui.leftHeight;
        float max = player.getMaxHealth();
        // Half hearts of 20, rounded up so a scratch never shows an empty row.
        int halves = Mth.ceil(Mth.clamp(player.getHealth() / max, 0, 1) * 20);
        RenderSystem.enableBlend();
        for (int i = 0; i < 10; i++) {
            int hx = x + i * 8;
            graphics.blitSprite(HEART_CONTAINER, hx, y, 9, 9);
            if (halves >= i * 2 + 2) {
                graphics.blitSprite(HEART_FULL, hx, y, 9, 9);
            } else if (halves == i * 2 + 1) {
                graphics.blitSprite(HEART_HALF, hx, y, 9, 9);
            }
        }
        Font font = minecraft.font;
        String text = Mth.ceil(player.getHealth()) + "/" + Mth.ceil(max);
        graphics.drawString(font, text, x - font.width(text) - 3, y + 1, 0xFFFF5555, true);
        minecraft.gui.leftHeight += 10;
    }
}
