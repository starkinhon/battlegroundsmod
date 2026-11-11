package com.starkinhon.battlegroundsmod.client;

import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.fx.MakimaFx;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

// The "BANG" over Makima's finger (forja/makima-bang): white Bangers letters with a thick black outline and a hard drop
// shadow, tilted 9 degrees, born big and settling, shaking on the shot, a bit bigger on the finisher. On everyone's
// screen, at the finger's spot in the world.
public final class MakimaHud {
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "bangers");
    private static final float LIFE = 14;

    private MakimaHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui || CutsceneHud.hidden()) {
            return;
        }
        List<double[]> bangs = MakimaFx.bangs();
        if (bangs.isEmpty()) {
            return;
        }
        double now = ScreenFx.now() + delta.getGameTimeDeltaPartialTick(false);
        // Only the newest shot shows (the preview keeps one word on screen and re-pops it each shot).
        double[] b = null;
        for (double[] x : bangs) {
            if (b == null || x[0] > b[0]) {
                b = x;
            }
        }
        float d = (float) (now - b[0]);
        if (d < 0 || d > LIFE) {
            return;
        }
        float alpha = smooth(d / 1.2f + 0.2f) * (1 - smooth((d - 9f) / 5f));
        if (alpha < 0.02f) {
            return;
        }
        float[] screen = FalaRender.toScreen(graphics, new Vec3(b[1], b[2] + 0.55, b[3]));
        if (screen == null) {
            return;
        }
        Font font = mc.font;
        Component word = Component.literal("BANG").withStyle(Style.EMPTY.withFont(FONT));
        float em = graphics.guiHeight() * 0.085f;
        float sc = (b[4] > 1 ? 1.5f : 1f) * (1 + 0.6f * (float) Math.exp(-d / 20f * 18f)) * em / 11f;
        float shake = d > 2.4f && d < 6f ? (float) (Math.sin(d * 91.7) * 0.5) * 6 : 0;
        int a = (int) (alpha * 255) << 24;
        int w = font.width(word);
        graphics.pose().pushPose();
        graphics.pose().translate(screen[0] + shake, screen[1], 200);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-9));
        graphics.pose().scale(sc, sc, 1);
        // translate(-50%, -100%), around the bottom middle.
        graphics.pose().translate(-w / 2f, -font.lineHeight, 0);
        // The preview's 5 px stroke and 4,5 px shadow on 64 px letters, in font pixels (11 to the em).
        float o = 0.86f;
        // The hard shadow (of the outlined word), then the thick outline all around, then the white letters.
        for (int pass = 0; pass < 2; pass++) {
            float sx = pass == 0 ? 0.69f : 0, sy = pass == 0 ? 0.86f : 0;
            for (int i = 0; i < 12; i++) {
                double ang = i * Math.PI * 2 / 12;
                ink(graphics, font, word, sx + (float) Math.cos(ang) * o, sy + (float) Math.sin(ang) * o, a);
            }
        }
        ink(graphics, font, word, 0, 0, a | 0xFFFFFF);
        graphics.pose().popPose();
    }

    private static void ink(GuiGraphics graphics, Font font, Component text, float dx, float dy, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(dx, dy, 0);
        graphics.drawString(font, text, 0, 0, argb, false);
        graphics.pose().popPose();
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
