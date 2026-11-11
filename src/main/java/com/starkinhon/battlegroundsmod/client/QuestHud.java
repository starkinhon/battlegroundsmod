package com.starkinhon.battlegroundsmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// The Dragon Ball mission on screen (universe/DbQuests, Stark 2026-11-10: Bulma's missions with an arrow on the HUD):
// top left, a small panel with "Missão", what to do (with the count when there is one) and, when it points to a place,
// an arrow turning with the camera and the distance in blocks.
public final class QuestHud {
    private static String id = "";
    private static int count, goal, x, z;
    private static boolean way;

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private QuestHud() {
    }

    static void set(String id, int count, int goal, int x, int z, boolean way) {
        QuestHud.id = id;
        QuestHud.count = count;
        QuestHud.goal = goal;
        QuestHud.x = x;
        QuestHud.z = z;
        QuestHud.way = way;
    }

    public static void clear() {
        id = "";
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (id.isEmpty() || minecraft.player == null || minecraft.options.hideGui || CutsceneHud.hidden() || minecraft.screen != null) {
            return;
        }
        Font font = minecraft.font;
        Component head = Component.translatable("message.battlegroundsmod.missao");
        Component text = Component.translatable("quest.battlegroundsmod." + id, count, goal);
        Component far = null;
        if (way) {
            double dx = x + 0.5 - minecraft.player.getX();
            double dz = z + 0.5 - minecraft.player.getZ();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float rel = Mth.wrapDegrees(target - minecraft.player.getYRot());
            int index = Math.floorMod(Math.round(rel / 45f), 8);
            far = Component.literal(ARROWS[index] + "  " + dist + " m");
        }
        int width = Math.max(font.width(head), font.width(text)) + 12;
        if (far != null) {
            width = Math.max(width, font.width(far) + 12);
        }
        int x1 = 4, y1 = 4;
        int height = far != null ? 36 : 26;
        graphics.fillGradient(x1, y1, x1 + width, y1 + height, 0xC0100A14, 0xA0080510);
        graphics.fill(x1, y1, x1 + 2, y1 + height, 0xFFF2C94C);
        graphics.drawString(font, head, x1 + 7, y1 + 4, 0xFFF2C94C, true);
        graphics.drawString(font, text, x1 + 7, y1 + 15, 0xFFE6E0EE, true);
        if (far != null) {
            graphics.drawString(font, far, x1 + 7, y1 + 26, 0xFF8FD3FF, true);
        }
    }
}
