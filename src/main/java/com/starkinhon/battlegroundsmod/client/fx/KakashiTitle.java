package com.starkinhon.battlegroundsmod.client.fx;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Sennen Goroshi's title on the screen (font/sennen: Shippori Mincho ExtraBold cut to these four characters, OFL), as the approved page shows it (forja/kakashi-kit, the sennen tab): 千年殺し in big
// black calligraphy with a white outline, MIL ANOS DE MORTE under it, popping in a little big and growing slowly while
// the target flies, then fading.
public final class KakashiTitle {
    private static final ResourceLocation KANJI = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "sennen");
    private static final double LIFE = 40;
    private static double shownAt = -1000;

    private KakashiTitle() {
    }

    static void show(double now) {
        shownAt = now;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        double t = ScreenFx.now() + delta.getGameTimeDeltaPartialTick(false) - shownAt;
        if (t < 0 || t > LIFE) {
            return;
        }
        float alpha = (float) (t < 32 ? 1 : 1 - (t - 32) / 8);
        if (alpha <= 0.02f) {
            return;
        }
        float pop = (float) (t < 2 ? 1.4 - 0.4 * (t / 2) : 1 + 0.002 * (t - 2));
        Font font = Minecraft.getInstance().font;
        int a = Math.max(4, (int) (alpha * 255)) << 24;
        Component title = Component.literal("千年殺し").withStyle(st -> st.withFont(KANJI));
        Component sub = Component.translatable("hud.battlegroundsmod.kakashi.sennen");
        float big = Math.max(3f, graphics.guiHeight() / 52f) * pop;
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2f, graphics.guiHeight() * 0.12f, 0);
        graphics.pose().scale(big, big, 1);
        int w = font.width(title);
        // the white outline first, then the black ink over it
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    graphics.drawString(font, title, -w / 2 + dx, dy, a | 0xFFFFFF, false);
                }
            }
        }
        graphics.drawString(font, title, -w / 2, 0, a | 0x111111, false);
        graphics.pose().popPose();
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2f, graphics.guiHeight() * 0.12f + font.lineHeight * big + 6, 0);
        float small = big * 0.32f;
        graphics.pose().scale(small, small, 1);
        graphics.drawString(font, sub, -font.width(sub) / 2, 0, a | 0xFFFFFF, true);
        graphics.pose().popPose();
    }
}
