package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

// A boss's line floating over its head (boss/BossFalas sends it), the style Stark picked in the Visualizador
// ("Falas na cabeça", style 2): the letters type out one by one in the soul's colors (a gradient from the first color to the
// second), on a dark see-through bar so it reads on any sky, no name on top. It lasts a moment longer the longer the
// line is, then fades out rising a little. Held in the world like a name tag, so it gets smaller farther away.
public final class FalaRender {
    // Ticks per letter while typing.
    private static final float TICKS_PER_LETTER = 0.6f;
    // How long it stays after it appears: base + per letter. Then it fades out in FADE ticks.
    private static final float HOLD = 30;
    private static final float HOLD_PER_LETTER = 1.2f;
    private static final float FADE = 8;
    // How much it rises while fading, in blocks.
    private static final float RISE = 0.3f;
    // Over the head (the name tag sits at +0.5); a bit higher when the name tag shows.
    private static final float ABOVE = 0.5f;
    private static final float ABOVE_NAME = 0.3f;
    // Text size: 0.03 of a block per font pixel (a name tag is 0.025).
    private static final float SCALE = 0.03f;
    // The bar's darkness (0..255): 62%, the same as the Visualizador.
    private static final int BAR_ALPHA = 158;

    private record Line(String text, int color1, int color2, long start) {
        float total() {
            return HOLD + text.length() * HOLD_PER_LETTER + FADE;
        }
    }

    private static final Map<Integer, Line> LINES = new HashMap<>();

    private FalaRender() {
    }

    public static void add(int entityId, String text, int color1, int color2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String shown = I18n.exists(text) ? I18n.get(text) : text;
        long now = mc.level.getGameTime();
        // Drop the ones that are over (a boss that died or left keeps nothing around).
        for (Iterator<Map.Entry<Integer, Line>> it = LINES.entrySet().iterator(); it.hasNext(); ) {
            Line line = it.next().getValue();
            if (now - line.start() > line.total() + 20 || now < line.start()) {
                it.remove();
            }
        }
        LINES.put(entityId, new Line(shown, color1, color2, now));
    }

    // Called by the boss renderer after the body is drawn.
    public static void render(LivingEntity entity, boolean nameShown, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        Line line = LINES.get(entity.getId());
        Minecraft mc = Minecraft.getInstance();
        if (line == null || mc.level == null) {
            return;
        }
        float age = (mc.level.getGameTime() - line.start()) + partialTick;
        float total = line.total();
        if (age < 0 || age > total) {
            if (age > total) {
                LINES.remove(entity.getId());
            }
            return;
        }
        float out = age > total - FADE ? (age - (total - FADE)) / FADE : 0;
        float smoothOut = out * out * (3 - 2 * out);
        float alpha = 1 - smoothOut;
        int a = Math.round(alpha * 255);
        // The font turns an alpha under 4 into fully opaque, so a line that is almost gone is just not drawn.
        if (a < 5) {
            return;
        }
        String text = line.text();
        int shown = Math.min(text.length(), (int) Math.floor(age / TICKS_PER_LETTER) + 1);
        // A small pop when it appears.
        float pop = 1 + 0.15f * (1 - Mth.clamp(age / 4, 0, 1));

        Font font = mc.font;
        MutableComponent typed = Component.empty();
        int len = text.length();
        for (int i = 0; i < shown; i++) {
            float k = len <= 1 ? 0 : (float) i / (len - 1);
            typed.append(Component.literal(String.valueOf(text.charAt(i))).withColor(lerpColor(line.color1(), line.color2(), k)));
        }

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() + ABOVE + (nameShown ? ABOVE_NAME : 0) + RISE * smoothOut, 0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = SCALE * pop;
        poseStack.scale(s, -s, s);
        Matrix4f pose = poseStack.last().pose();
        // Centered on the whole line, so the bar grows to the right as the letters come in.
        float x = -font.width(text) / 2f;
        int bar = Math.round(BAR_ALPHA * alpha) << 24;
        font.drawInBatch(typed, x, 0, (a << 24) | 0xFFFFFF, false, pose, buffer, Font.DisplayMode.NORMAL, bar, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static int lerpColor(int c1, int c2, float k) {
        int r = Math.round(Mth.lerp(k, c1 >> 16 & 255, c2 >> 16 & 255));
        int g = Math.round(Mth.lerp(k, c1 >> 8 & 255, c2 >> 8 & 255));
        int b = Math.round(Mth.lerp(k, c1 & 255, c2 & 255));
        return r << 16 | g << 8 | b;
    }

    public static void clear() {
        LINES.clear();
    }
}
