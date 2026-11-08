package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.KokushiboEntity;
import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// The name of the Moon form Kokushibo is using, written in kanji in a vertical column beside his body (Stark's idea,
// 2026-11-08, preview forja/kokushibo-boss/kanji-das-formas.html): brushed in from top to bottom as the form starts, held a
// moment, then it rises a little and fades. The visualizer's font (Shippori Mincho ExtraBold, font/kanji.json) with the
// falas' look: a gradient from a dark shade on top to a bright one below and a hard dark outline, no glow (Stark: "fica
// zoado"). Gold on the normal Moon Breathing, purple on the Awakened Moon. From left to right: the form and its name in
// romaji (small, turned on its side), the name, the form's number and the breathing. Only the boss shows it for now.
// /souls kanji desativar turns it off (FxConfig.FORM_KANJI).
public final class FormKanji {
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "kanji");
    // The font's em in font pixels (font/kanji.json "size").
    private static final float EM = 11;
    // Ticks: brushed in, total on screen, fading at the end.
    private static final float REVEAL = 11, TOTAL = 72, FADE = 12;
    // Where it hangs: over his shoulders, a bit to his right on screen (blocks).
    private static final double UP = 1.95, SIDE = 0.55;
    // The em in blocks at that spot, and its limits as a share of the screen height (the preview's 7 to 16 px, 1.4x
    // smaller than the first one).
    private static final float EM_BLOCKS = 0.27f, MIN_EM = 0.010f, MAX_EM = 0.023f;
    private static final double MAX_DISTANCE = 64;

    private record Form(int n, String num, String name, String pt, String rom) {
        boolean awakened() {
            return n >= 7;
        }
    }

    private static final Form[] FORMS = {
        new Form(1, "壱ノ型", "闇月・宵の宮", "Primeira Forma", "Yoru no Miya"),
        new Form(2, "弐ノ型", "珠華ノ弄月", "Segunda Forma", "Shuka no Rōgetsu"),
        new Form(3, "参ノ型", "厭忌月・銷り", "Terceira Forma", "Enkizuki Tsugari"),
        new Form(5, "伍ノ型", "月魄災渦", "Quinta Forma", "Geppaku Saika"),
        new Form(6, "陸ノ型", "常夜孤月・無間", "Sexta Forma", "Tokoyo Kogetsu Muken"),
        new Form(7, "漆ノ型", "厄鏡・月映え", "Sétima Forma", "Yakkyō Zukibae"),
        new Form(8, "捌ノ型", "月龍輪尾", "Oitava Forma", "Getsuryū Rinbi"),
        new Form(9, "玖ノ型", "降り月・連面", "Nona Forma", "Kudaritsuki Renmen"),
        new Form(10, "拾ノ型", "穿面斬・蘿月", "Décima Forma", "Senmenzan Ragetsu"),
        new Form(14, "拾肆ノ型", "兇変・天満繊月", "Décima Quarta Forma", "Kyōhen Tenman Sengetsu"),
        new Form(16, "拾陸ノ型", "月虹・片割れ月", "Décima Sexta Forma", "Gekkō Katawarezuki"),
    };

    // {dark top, bright bottom, outline}: the falas' gold and purple.
    private static final int[] GOLD = {0xA86A00, 0xFFE27A, 0x2E1A00};
    private static final int[] PURPLE = {0x6A2AC0, 0xE2B4FF, 0x1E0638};

    private record Shown(Form form, long start) {
    }

    private static final Map<Integer, Shown> SHOWN = new HashMap<>();

    private FormKanji() {
    }

    // A Moon form started (client/fx/SwordFx, from the form's own effect packet).
    public static void cast(int entityId, int n) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof KokushiboEntity)) {
            return;
        }
        for (Form form : FORMS) {
            if (form.n() == n) {
                SHOWN.put(entityId, new Shown(form, mc.level.getGameTime()));
                return;
            }
        }
    }

    public static void clear() {
        SHOWN.clear();
    }

    public static void renderGui(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (SHOWN.isEmpty() || mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        if (!FxConfig.formKanji() || CutsceneHud.hidden()) {
            SHOWN.clear();
            return;
        }
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        for (Iterator<Map.Entry<Integer, Shown>> it = SHOWN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Shown> entry = it.next();
            float age = now - entry.getValue().start() + partial;
            Entity entity = mc.level.getEntity(entry.getKey());
            if (age > TOTAL || age < 0 || entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            draw(graphics, mc, entity, entry.getValue().form(), age, partial);
        }
    }

    private static void draw(GuiGraphics graphics, Minecraft mc, Entity entity, Form form, float age, float partial) {
        Vector3f left = mc.gameRenderer.getMainCamera().getLeftVector();
        Vec3 at = entity.getPosition(partial).add(-left.x() * SIDE, UP, -left.z() * SIDE);
        Vec3 camera = FalaRender.camera();
        if (at.distanceTo(camera) > MAX_DISTANCE) {
            return;
        }
        if (mc.level.clip(new ClipContext(camera, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType()
                != HitResult.Type.MISS) {
            return;
        }
        float[] screen = FalaRender.toScreen(graphics, at);
        if (screen == null) {
            return;
        }
        int h = graphics.guiHeight();
        float em = Mth.clamp(EM_BLOCKS * screen[2], MIN_EM * h, MAX_EM * h);
        // The name column stays whole on screen.
        em = Math.min(em, Math.max(MIN_EM * h * 0.6f, (h - screen[1] - 8) / (form.name().length() * 1.15f * 1.08f + 0.6f)));
        float reveal = Mth.clamp(age / REVEAL, 0, 1);
        float fade = Mth.clamp((age - (TOTAL - FADE)) / FADE, 0, 1);
        float alpha = 1 - fade * fade * (3 - 2 * fade);
        if (alpha * 255 < 5) {
            return;
        }
        int[] ink = form.awakened() ? PURPLE : GOLD;
        Font font = mc.font;

        graphics.pose().pushPose();
        graphics.pose().translate(screen[0], screen[1] - fade * em * 0.8f, 0);
        float grow = 1 + fade * 0.12f;
        graphics.pose().scale(grow, grow, 1);
        // The columns, left to right, in ems from the anchor.
        float x = 0;
        float gap = 0.18f;
        // the form and its romaji, small and on its side
        float subSize = 0.3f;
        drawSideways(graphics, font, form.pt() + " · " + form.rom(), x + subSize * 0.6f, 0.4f, subSize, em, alpha, reveal);
        x += subSize * 1.2f + gap;
        float nameSize = 1.15f;
        drawColumn(graphics, font, form.name(), x + nameSize * 1.05f / 2, 0, nameSize, 0.08f, em, ink, alpha, reveal);
        x += nameSize * 1.05f + gap;
        float numSize = 0.75f;
        drawColumn(graphics, font, form.num(), x + numSize * 1.05f / 2, 0.15f, numSize, 0.08f, em, ink, alpha, reveal);
        x += numSize * 1.05f + gap;
        float breathSize = 0.34f;
        drawColumn(graphics, font, form.awakened() ? "月の呼吸・覚醒" : "月の呼吸", x + breathSize * 1.05f / 2, 0.2f, breathSize, 0.3f, em, ink,
                alpha * 0.8f, reveal);
        graphics.pose().popPose();
    }

    // One vertical column of kanji centered on cx, starting top ems down: each character its own glyph, the gradient
    // running down the column, brushed in from the top as reveal goes from 0 to 1.
    private static void drawColumn(GuiGraphics graphics, Font font, String text, float cx, float top, float size, float spacing, float em,
            int[] ink, float alpha, float reveal) {
        int count = text.codePointCount(0, text.length());
        float step = size * (1 + spacing);
        float scale = size * em / EM;
        float outline = Math.max(0.5f, 0.9f / scale);
        int i = 0;
        for (int offset = 0; offset < text.length(); i++) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            float pos = count <= 1 ? 0 : (float) i / (count - 1);
            float shown = Mth.clamp((reveal * 1.15f - pos * 0.9f) / 0.25f, 0, 1) * alpha;
            int a = Math.round(shown * 255);
            if (a < 5) {
                continue;
            }
            Component glyph = Component.literal(new String(Character.toChars(cp))).withStyle(Style.EMPTY.withFont(FONT));
            int color = lerp(ink[0], ink[1], pos);
            graphics.pose().pushPose();
            graphics.pose().translate(cx * em, (top + i * step) * em, 0);
            graphics.pose().scale(scale, scale, 1);
            float gx = -font.width(glyph) / 2f;
            graphics.pose().translate(gx, 0, 0);
            // a darker shadow under it, the outline all around, the letter on top
            ink(graphics, font, glyph, 0, 2 * outline, a << 24 | shade(ink[2], 0.6f));
            for (int d = 0; d < 8; d++) {
                double ang = d * Math.PI / 4;
                ink(graphics, font, glyph, (float) Math.cos(ang) * outline, (float) Math.sin(ang) * outline, a << 24 | ink[2]);
            }
            ink(graphics, font, glyph, 0, 0, a << 24 | color);
            graphics.pose().popPose();
        }
    }

    // The small romaji line, turned a quarter clockwise (read top to bottom, as vertical Japanese writing does with Latin).
    private static void drawSideways(GuiGraphics graphics, Font font, String text, float cx, float top, float size, float em, float alpha,
            float reveal) {
        int a = Math.round(alpha * 0.85f * Mth.clamp(reveal * 1.5f, 0, 1) * 255);
        if (a < 5) {
            return;
        }
        Component line = Component.literal(text).withStyle(Style.EMPTY.withFont(FONT));
        float scale = size * em / EM;
        graphics.pose().pushPose();
        graphics.pose().translate(cx * em, top * em, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(0, -EM / 2f, 0);
        float outline = Math.max(0.5f, 0.9f / scale);
        for (int d = 0; d < 8; d++) {
            double ang = d * Math.PI / 4;
            ink(graphics, font, line, (float) Math.cos(ang) * outline, (float) Math.sin(ang) * outline, a << 24 | 0x0B0A10);
        }
        ink(graphics, font, line, 0, 0, a << 24 | 0xEDE6F2);
        graphics.pose().popPose();
    }

    private static void ink(GuiGraphics graphics, Font font, Component text, float dx, float dy, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(dx, dy, 0);
        graphics.drawString(font, text, 0, 0, argb, false);
        graphics.pose().popPose();
    }

    private static int shade(int c, float k) {
        return Math.round((c >> 16 & 255) * k) << 16 | Math.round((c >> 8 & 255) * k) << 8 | Math.round((c & 255) * k);
    }

    private static int lerp(int c1, int c2, float k) {
        int r = Math.round(Mth.lerp(k, c1 >> 16 & 255, c2 >> 16 & 255));
        int g = Math.round(Mth.lerp(k, c1 >> 8 & 255, c2 >> 8 & 255));
        int b = Math.round(Mth.lerp(k, c1 & 255, c2 & 255));
        return r << 16 | g << 8 | b;
    }
}
