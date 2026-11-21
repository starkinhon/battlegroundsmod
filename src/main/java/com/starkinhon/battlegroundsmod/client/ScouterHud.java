// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.item.ScouterItem;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

// The Scouter's lens (item/ScouterItem), remade 2026-11-21 from the preview Stark approved
// (https://claude.ai/artifact/2ec5F7ZjqGmNFUeJCxk2f7): a green glass over the left eye, the side the device sits on.
// Whoever is under the crosshair (64 blocks) gets locked: thick corners close in around the body, alien glyphs run for a
// moment, then the power level counts up (the server's real reading, ScouterItem.level). It changes live (Kaioken,
// Max Power, a boss's next phase) with "SUBINDO!". Far too strong (40x the wearer) and the number runs wild, ALERTA
// blinks and the lens cracks: it is out for 60 s. The scan key (Y) sends a ring over the lens and, for a few seconds,
// arrows on its edge point to the strong ones out of sight, with their power and distance.
public final class ScouterHud {
    private static final double RANGE = 64;
    // How much stronger than the wearer a target must be to blow the lens.
    private static final double OVERLOAD = 40;
    private static final double BROKEN_SECONDS = 60;
    private static final double SCAN_SECONDS = 4.5;
    // Readings older than this are stale (the Scouter was just put on, or the server stopped sending).
    private static final long STALE_MS = 1500;

    private static int lastTarget = -1;
    private static double lockStart;
    private static long lastShown;
    private static double jumpAt = -9;
    private static double scanAt = -99;
    private static double brokenUntil;
    private static double brokenAt;
    private static double lastTick;
    // The lock beep (glyphs done, the number starts) played for this target.
    private static boolean lockBeeped;
    private static List<float[][]> cracks = List.of();
    // Sounds waiting to play: {time, pitch, volume, kind (0 beep, 1 glass, 2 blast)}.
    private static final List<double[]> SOUNDS = new ArrayList<>();

    private ScouterHud() {
    }

    // The lens on the GUI: width, top and bottom of its body (the bottom edge slopes down to y1 + 10 on the left).
    private record Lens(float lw, float y0, float y1) {
        float bottom() {
            return y1 + 10;
        }

        // The right end of the glass on row y (0 = nothing).
        float right(float y) {
            if (y < y0 || y > bottom()) {
                return 0;
            }
            float top = (y - y0) / 3f * lw;
            float r;
            if (y < y0 + 13) {
                r = Mth.lerp((y - y0) / 13f, lw, lw * 0.97f);
            } else if (y < y1 - 10) {
                r = Mth.lerp((y - y0 - 13) / Math.max(1, y1 - 10 - y0 - 13), lw * 0.97f, lw * 0.86f);
            } else if (y <= y1) {
                r = Mth.lerp((y - (y1 - 10)) / 10f, lw * 0.86f, lw * 0.76f);
            } else {
                r = lw * 0.76f * (1 - (y - y1) / 10f);
            }
            return Math.max(0, Math.min(top, r));
        }

        boolean inside(float x, float y) {
            return x >= 0 && x < right(y);
        }

        float cx() {
            return lw * 0.45f;
        }

        float cy() {
            return (y0 + y1) / 2;
        }
    }

    private static Lens lens(int width, int height) {
        return new Lens(Math.min(width * 0.4f, 230), height * 0.06f, height * 0.56f);
    }

    private static double now() {
        return Util.getMillis() / 1000.0;
    }

    // The scan key. Nothing while the lens is broken or the Scouter is off.
    public static void scan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ScouterItem.worn(mc.player) || now() < brokenUntil) {
            return;
        }
        double t = now();
        scanAt = t;
        beep(t, 1.0f, 0.3f);
        beep(t + 0.09, 1.4f, 0.3f);
        beep(t + 0.18, 2.0f, 0.3f);
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !ScouterItem.worn(player)) {
            lastTarget = -1;
            return;
        }
        double t = now();
        playSounds(mc, t);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Lens lens = lens(width, height);
        float px = Math.min(width, height) / 150f;
        if (t < brokenUntil) {
            drawBroken(graphics, mc.font, lens, px, width, height, t);
            return;
        }
        if (brokenUntil > 0) {
            // back on
            brokenUntil = 0;
            lastTarget = -1;
            beep(t, 1.0f, 0.3f);
            beep(t + 0.12, 1.5f, 0.3f);
        }
        drawGlass(graphics, lens);
        boolean fresh = Util.getMillis() - ScouterItem.clientAt < STALE_MS;
        long me = fresh ? Math.max(1, ScouterItem.clientMe) : 0;
        Map<Integer, Long> levels = fresh ? ScouterItem.clientLevels : Map.of();
        Font font = mc.font;
        if (me > 0) {
            Component you = Component.translatable("hud.battlegroundsmod.scouter.you", fmt(mc, me));
            float s = 0.5f * px;
            text(graphics, font, you, lens.lw() * 0.9f - font.width(you) * s, lens.y0() + px * 9, s, 0xBFA0F096);
        }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        if (me > 0 && t - scanAt < SCAN_SECONDS) {
            drawScan(graphics, mc, player, lens, px, width, height, t, me, levels, partial);
        }
        float tx = px * 3.2f;
        float ty = lens.y0() + px * 4 + 7 * 0.6f * px;
        LivingEntity target = target(mc, player);
        int id = target == null ? -1 : target.getId();
        if (id != lastTarget) {
            lastTarget = id;
            lockStart = t;
            lastShown = 0;
            jumpAt = -9;
            lastTick = t;
            lockBeeped = false;
            if (target != null) {
                beep(t, 1.5f, 0.3f);
            }
        }
        if (target == null) {
            String dots = ".".repeat(1 + (int) (t * 2) % 3);
            text(graphics, font, Component.translatable("hud.battlegroundsmod.scouter.idle").append(dots), tx, ty, 0.6f * px, 0xF2AAFFA0);
            for (int i = 0; i < 6; i++) {
                glyph(graphics, tx + i * px * 5, ty + px * 4, px, (int) (t * 3) + i, 0x8C96F08C);
            }
            return;
        }
        double lockT = t - lockStart;
        Long read = levels.get(id);
        long v = read == null ? 0 : read;
        boolean known = read != null && me > 0;
        int color = known ? colorFor(v, me) : 0xF2AAFFA0;
        drawLock(graphics, target, px, partial, lockT, color);
        text(graphics, font, target.getDisplayName(), tx, ty, 0.6f * px, 0xF2AAFFA0);
        int dist = (int) Math.round(player.distanceTo(target));
        text(graphics, font, Component.literal(dist + " m"), tx, ty + px * 8, 0.5f * px, 0xCCA0F096);
        float yy = ty + px * 20;
        if (lockT < 0.6 || !known) {
            // glyphs, with the scouter's chatter
            for (int i = 0; i < 7; i++) {
                glyph(graphics, tx + i * px * 5, yy - px * 6, px * 1.4f, (int) (t * 14) + i * 7, 0xF2AAFFA0);
            }
            if (t - lastTick > 0.1) {
                lastTick = t;
                beep(t, 1.8f + (float) Math.random() * 0.2f, 0.12f);
            }
            return;
        }
        if (!lockBeeped) {
            lockBeeped = true;
            beep(t, 2.0f, 0.3f);
        }
        boolean over = v > me * OVERLOAD;
        long shown = over ? Math.round(Math.pow(10, 2 + (lockT - 0.6) * 2.6))
                : Math.round(v * Math.min(1, (lockT - 0.6) / 0.9));
        if (!over && lockT > 1.5) {
            if (lastShown > 0 && v > lastShown + 1) {
                jumpAt = t;
            }
            lastShown = v;
        }
        float jitter = over ? (float) (Math.random() - 0.5) * px * 2 : 0;
        float big = 1.2f * px;
        text(graphics, font, Component.literal(fmt(mc, shown)), tx + jitter, yy + px * 6 + jitter, big, color);
        if (t - jumpAt < 1.2 && (int) (t * 8) % 2 == 1) {
            text(graphics, font, Component.translatable("hud.battlegroundsmod.scouter.rising"), tx, yy + px * 13, 0.5f * px, 0xF2FF6E50);
        }
        if (over && (int) (t * 6) % 2 == 1) {
            text(graphics, font, Component.translatable("hud.battlegroundsmod.scouter.alert"), tx, yy + px * 14, 0.6f * px, 0xF2FF4632);
        }
        if (over && (shown > v || lockT > 3.4)) {
            breakLens(lens, px, t);
        }
    }

    private static int colorFor(long v, long me) {
        return v > me * 1.15 ? 0xFAFF5F4B : v > me * 0.87 ? 0xFAFFD75A : 0xFA96FF8C;
    }

    private static String fmt(Minecraft mc, long value) {
        Locale locale = mc.options.languageCode.startsWith("pt") ? Locale.forLanguageTag("pt-BR") : Locale.US;
        return String.format(locale, "%,d", value);
    }

    private static LivingEntity target(Minecraft mc, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1),
                e -> e instanceof LivingEntity && e != player && !e.isSpectator(), RANGE * RANGE);
        Entity entity = hit == null ? null : hit.getEntity();
        return entity instanceof LivingEntity living && player.hasLineOfSight(living) ? living : null;
    }

    // ---------- drawing ----------

    private static void drawGlass(GuiGraphics g, Lens lens) {
        int top = (int) Math.floor(lens.y0());
        int bottom = (int) Math.ceil(lens.bottom());
        float span = Math.max(1, bottom - top);
        for (int y = top; y < bottom; y++) {
            int right = Math.round(lens.right(y + 0.5f));
            if (right <= 0) {
                continue;
            }
            float f = (y - top) / span;
            // rgba(110,255,120,.20) -> rgba(70,230,100,.14) at 60% -> rgba(40,200,90,.22)
            int color = f < 0.6f ? mix(0x33_6EFF78, 0x24_46E664, f / 0.6f) : mix(0x24_46E664, 0x38_28C85A, (f - 0.6f) / 0.4f);
            g.fill(0, y, right, y + 1, color);
            // a wide, soft reflection on the top left corner (not a line)
            if (y - top < 46) {
                float k = 1 - (y - top) / 46f;
                int w = Math.min(right, Math.round(lens.lw() * 0.5f));
                for (int s = 0; s < 4; s++) {
                    int a = Math.round(0.14f * 255 * k * (1 - s / 4f));
                    g.fill(w * s / 4, y, w * (s + 1) / 4, y + 1, a << 24 | 0xFFFFFF);
                }
            }
        }
        // the frame: dark and thick
        int frame = 0xBF1E2822;
        for (int y = top; y < bottom; y++) {
            int right = Math.round(lens.right(y + 0.5f));
            if (right > 0) {
                g.fill(Math.max(0, right - 2), y, right, y + 1, frame);
            }
        }
        for (int x = 0; x < Math.round(lens.lw()); x++) {
            int y = Math.round(lens.y0() + 3f * x / lens.lw());
            g.fill(x, y, x + 1, y + 2, frame);
        }
        float bw = lens.lw() * 0.76f;
        for (int x = 0; x < Math.round(bw); x++) {
            int y = Math.round(lens.y1() + 10 * (1 - x / bw));
            g.fill(x, y - 2, x + 1, y, frame);
        }
    }

    // Thick corners around the target's body that close in from outside, and the Scouter's triangle over it.
    private static void drawLock(GuiGraphics g, LivingEntity target, float px, float partial, double lockT, int color) {
        Vec3 pos = target.getPosition(partial);
        float[] top = FalaRender.toScreen(g, pos.add(0, target.getBbHeight() + 0.1, 0));
        float[] bot = FalaRender.toScreen(g, pos);
        if (top == null || bot == null) {
            return;
        }
        float hgt = Math.max(10, bot[1] - top[1]);
        float wid = hgt * 0.55f;
        float cx = (top[0] + bot[0]) / 2;
        float cy = (top[1] + bot[1]) / 2;
        float k = (float) Math.max(0, 1 - lockT * 3);
        float hw = wid / 2 + px * 2 + k * px * 7;
        float hh = hgt / 2 + px * 1.4f + k * px * 7;
        int s = Math.max(1, Math.round(px * 0.9f));
        int len = Math.round(px * 3.5f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = Math.round(cx + sx * hw);
                int y = Math.round(cy + sy * hh);
                int hx0 = sx < 0 ? x : x - len;
                int vy0 = sy < 0 ? y : y - len;
                int hy0 = sy < 0 ? y : y - s;
                int vx0 = sx < 0 ? x : x - s;
                g.fill(hx0, hy0, hx0 + len, hy0 + s, color);
                g.fill(vx0, vy0, vx0 + s, vy0 + len, color);
            }
        }
        // triangle pointing down at the target
        float apex = cy - hh - px * 1.2f;
        float base = apex - px * 3.5f;
        float half = px * 2;
        for (int y = Math.round(base); y < Math.round(apex); y++) {
            float w = half * (apex - y) / (apex - base);
            g.fill(Math.round(cx - w), y, Math.round(cx + w), y + 1, color);
        }
    }

    // The ring over the lens, then arrows on its edge to the strong ones out of sight.
    private static void drawScan(GuiGraphics g, Minecraft mc, Player player, Lens lens, float px, int width, int height,
            double t, long me, Map<Integer, Long> levels, float partial) {
        double age = t - scanAt;
        float ph = (float) Math.min(1, age / 0.8);
        if (ph < 1) {
            float radius = ph * lens.lw();
            int alpha = Math.round(0.5f * (1 - ph) * 255);
            int thick = Math.max(1, Math.round((10 * (1 - ph) + 2) / 3f));
            int color = alpha << 24 | 0xBEFFB4;
            int steps = Math.max(60, (int) (radius * Mth.TWO_PI / 1.5f));
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2 / steps;
                float x = lens.cx() + (float) Math.cos(a) * radius;
                float y = lens.cy() + (float) Math.sin(a) * radius;
                if (lens.inside(x, y)) {
                    g.fill(Math.round(x), Math.round(y), Math.round(x) + thick, Math.round(y) + thick, color);
                }
            }
        }
        if (age < 0.5 || mc.level == null) {
            return;
        }
        float rr = Math.min(lens.lw() * 0.4f, (lens.y1() - lens.y0()) * 0.42f);
        for (Map.Entry<Integer, Long> e : levels.entrySet()) {
            long v = e.getValue();
            if (v <= me * 0.5) {
                continue;
            }
            Entity entity = mc.level.getEntity(e.getKey());
            if (entity == null) {
                continue;
            }
            Vec3 at = entity.getPosition(partial);
            float[] p = FalaRender.toScreen(g, at.add(0, entity.getBbHeight() * 0.6, 0));
            if (p != null && p[0] > 0 && p[0] < width && p[1] > 0 && p[1] < height) {
                continue;
            }
            double dx = at.x - player.getX();
            double dz = at.z - player.getZ();
            float yawTo = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            float rel = Mth.wrapDegrees(yawTo - player.getYRot()) * Mth.DEG_TO_RAD;
            float ax = lens.cx() + Mth.sin(rel) * rr;
            float ay = lens.cy() - Mth.cos(rel) * rr;
            int color = (int) (t * 4) % 2 == 1 ? 0xF2FF5A46 : 0xF2FFC85A;
            g.pose().pushPose();
            g.pose().translate(ax, ay, 0);
            g.pose().mulPose(Axis.ZP.rotation(rel));
            float tip = -px * 5;
            float base = px * 2;
            for (float y = tip; y < base; y += 0.5f) {
                float w = px * 4 * (y - tip) / (base - tip);
                g.fill(Math.round(-w), Math.round(y), Math.round(w), Math.round(y) + 1, color);
            }
            g.pose().popPose();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            Component label = Component.literal(fmt(mc, v) + " · " + dist + " m");
            text(g, mc.font, label, Math.max(2, ax - px * 14), ay + px * 9, 0.5f * px, 0xF2FFD78C);
        }
    }

    private static void breakLens(Lens lens, float px, double t) {
        brokenAt = t;
        brokenUntil = t + BROKEN_SECONDS;
        List<float[][]> out = new ArrayList<>();
        float ox = lens.lw() * 0.5f;
        float oy = (lens.y0() + lens.y1()) * 0.45f;
        float step = px * 4.7f;
        for (int i = 0; i < 9; i++) {
            double a = i / 9.0 * Math.PI * 2 + Math.random() * 0.4;
            float x = ox;
            float y = oy;
            float[][] c = new float[8][];
            c[0] = new float[] {x, y};
            for (int j = 1; j < 8; j++) {
                a += (Math.random() - 0.5) * 0.9;
                x += (float) Math.cos(a) * step;
                y += (float) Math.sin(a) * step;
                c[j] = new float[] {x, y};
            }
            out.add(c);
        }
        cracks = out;
        SOUNDS.add(new double[] {t, 1.6, 0.6, 1});
        SOUNDS.add(new double[] {t + 0.18, 1.8, 0.25, 2});
    }

    private static void drawBroken(GuiGraphics g, Font font, Lens lens, float px, int width, int height, double t) {
        double left = brokenUntil - t;
        float a = (float) Math.min(1, left / 1.2);
        int top = (int) Math.floor(lens.y0());
        int bottom = (int) Math.ceil(lens.bottom());
        int tint = Math.round(0.08f * a * 255) << 24 | 0x96FF8C;
        for (int y = top; y < bottom; y++) {
            int right = Math.round(lens.right(y + 0.5f));
            if (right > 0) {
                g.fill(0, y, right, y + 1, tint);
            }
        }
        int crack = Math.round(0.85f * a * 255) << 24 | 0xEBFFEB;
        for (float[][] c : cracks) {
            for (int j = 1; j < c.length; j++) {
                float x0 = c[j - 1][0];
                float y0 = c[j - 1][1];
                float x1 = c[j][0];
                float y1 = c[j][1];
                int n = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 1.5f));
                for (int i = 0; i <= n; i++) {
                    float x = Mth.lerp(i / (float) n, x0, x1);
                    float y = Mth.lerp(i / (float) n, y0, y1);
                    if (lens.inside(x, y)) {
                        g.fill(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y) + 1, crack);
                    }
                }
            }
        }
        double since = t - brokenAt;
        if (since < 0.6) {
            int flash = Math.round((float) ((0.6 - since) / 0.6 * 0.8) * 255) << 24 | 0xFFFFE6;
            g.fill(0, 0, width, height, flash);
        }
        int alpha = Math.round(a * 255);
        text(g, font, Component.translatable("hud.battlegroundsmod.scouter.broken"), px * 3.3f, lens.y1() - px * 10, 0.7f * px,
                alpha << 24 | 0xFF785A);
        text(g, font, Component.translatable("hud.battlegroundsmod.scouter.back", (int) Math.ceil(left)), px * 3.3f, lens.y1() - px * 2,
                0.5f * px, Math.round(a * 0.8f * 255) << 24 | 0xFFE6D2);
    }

    // An alien glyph, 3 x 5 cells.
    private static void glyph(GuiGraphics g, float x, float y, float size, int seed, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size, size, 1);
        long h = seed * 9301L + 49297;
        for (int i = 0; i < 15; i++) {
            h = (h * 9301 + 49297) % 233280;
            if (h / 233280.0 < 0.48) {
                int cx = i % 3;
                int cy = i / 3;
                g.fill(cx, cy, cx + 1, cy + 1, color);
            }
        }
        g.pose().popPose();
    }

    // Text at a baseline, scaled.
    private static void text(GuiGraphics g, Font font, Component text, float x, float baseline, float scale, int color) {
        if ((color >>> 24) < 5) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, baseline - 7 * scale, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static int mix(int a, int b, float f) {
        f = Mth.clamp(f, 0, 1);
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 0xFF;
            int cb = b >>> shift & 0xFF;
            out |= Math.round(ca + (cb - ca) * f) << shift;
        }
        return out;
    }

    // ---------- sounds (on the head, so UI sounds) ----------

    private static void beep(double at, float pitch, float volume) {
        SOUNDS.add(new double[] {at, pitch, volume, 0});
    }

    private static void playSounds(Minecraft mc, double t) {
        for (Iterator<double[]> it = SOUNDS.iterator(); it.hasNext(); ) {
            double[] s = it.next();
            if (s[0] > t) {
                continue;
            }
            it.remove();
            if (t - s[0] > 1) {
                continue;
            }
            float pitch = (float) s[1];
            float volume = (float) s[2];
            switch ((int) s[3]) {
                case 1 -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GLASS_BREAK, pitch, volume));
                case 2 -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EXPLODE.value(), pitch, volume));
                default -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), pitch, volume));
            }
        }
    }
}
