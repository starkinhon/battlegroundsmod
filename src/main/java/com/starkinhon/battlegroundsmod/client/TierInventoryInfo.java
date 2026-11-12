package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ScreenEvent;

// The player's tier in the inventory (Stark's test 2026-11-10: "tem que ter algum jeito de mostrar o meu tier... no E"):
// a small card above the inventory with the tier's name and, with the progression on, the Poder bar to the next tier.
public final class TierInventoryInfo {
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
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        Component name = Tiers.name(data.tier);
        // The Poder in numbers too (Stark 2026-11-10), "Humano · Poder 40 / 120".
        if (Progression.clientOn && Progression.cost(data.tier) > 0) {
            name = name.copy().append(Component.literal("  ·  Poder " + Math.round(data.poder) + " / " + Math.round(Progression.cost(data.tier)))
                    .withStyle(s -> s.withColor(0xC8B88A)));
        }
        int width = Math.max(110, font.width(name) + 16);
        int x1 = screen.getGuiLeft() + (176 - width) / 2;
        int y1 = screen.getGuiTop() - 26;
        int x2 = x1 + width;
        int y2 = y1 + 22;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fillGradient(x1, y1, x2, y2, 0xE0120C1C, 0xE00A0710);
        graphics.fill(x1, y1, x2, y1 + 1, 0xFFE0B04A);
        graphics.fill(x1, y2 - 1, x2, y2, 0xFF5A4520);
        graphics.drawCenteredString(font, name, (x1 + x2) / 2, y1 + 4, 0xFFF2D27A);
        if (Progression.clientOn) {
            double need = Progression.cost(data.tier);
            float fraction = need <= 0 ? 1 : Mth.clamp((float) (data.poder / need), 0, 1);
            int bx1 = x1 + 6;
            int bx2 = x2 - 6;
            int by = y1 + 15;
            graphics.fill(bx1, by, bx2, by + 3, 0xC0000000);
            int filled = bx1 + Math.round((bx2 - bx1) * fraction);
            if (filled > bx1) {
                graphics.fill(bx1, by, filled, by + 3, 0xFFE8C25A);
            }
        }
        graphics.pose().popPose();
    }
}
