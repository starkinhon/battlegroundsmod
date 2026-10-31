package com.starkinhon.battlegroundsmod.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

// The Madara constellation's backdrop, all drawn (no images), in red and black: a giant blood moon with the Infinite
// Tsukuyomi eye on it (a pupil and three dotted rings, each with three tomoe, turning slowly), dark smoke across it,
// ridges of black mountains in front and a sharp central peak whose tip holds the constellation's core, with red mist
// glowing at their feet.
public final class TsukuyomiSky {
    private static final int MOON_BRIGHT = 0xB52A1C;
    private static final int MOON_DARK = 0x3A0806;
    private static final int EYE = 0x1A0303;
    private static final int MIST = 0xC0180C;

    private TsukuyomiSky() {
    }

    public static int top() {
        return 0xFF050102;
    }

    public static int bottom() {
        return 0xFF1E0404;
    }

    // moonX, moonY, radius: the moon. coreX, coreY: the peak's tip.
    public static void draw(GuiGraphics graphics, int width, int height, float moonX, float moonY, float radius,
                            float coreX, float coreY, float t, float fade) {
        moon(graphics, moonX, moonY, radius, t, fade);
        smoke(graphics, moonX, moonY, radius, t, fade);
        mountains(graphics, width, height, radius, coreX, coreY, t, fade);
    }

    private static void moon(GuiGraphics graphics, float cx, float cy, float r, float t, float fade) {
        GuiDraw.glow(graphics, cx, cy, r * 1.9f, MIST, 0.32f * fade);
        GuiDraw.glow(graphics, cx, cy, r * 1.25f, 0xE03020, 0.30f * fade);
        int alpha = Math.round(255 * fade);

        // The disc, row by row: darker at the rim, brighter towards the middle.
        for (float dy = -r; dy < r; dy += 1) {
            float mid = dy + 0.5f;
            float half = Mth.sqrt(Math.max(0, r * r - mid * mid));
            float vertical = Mth.sqrt(Math.max(0, 1 - (mid / r) * (mid / r)));
            int center = GuiDraw.argb(alpha, GuiDraw.mix(MOON_DARK, MOON_BRIGHT, 0.45f + 0.55f * vertical - Math.max(0, mid / r) * 0.15f));
            int edge = GuiDraw.argb(alpha, MOON_DARK);
            GuiDraw.quad(graphics, cx - half, cy + dy, cx - half, cy + dy + 1, cx, cy + dy + 1, cx, cy + dy, edge, edge, center, center);
            GuiDraw.quad(graphics, cx, cy + dy, cx, cy + dy + 1, cx + half, cy + dy + 1, cx + half, cy + dy, center, center, edge, edge);
        }

        // Seas and craters: darker and lighter blotches.
        Random random = new Random(77);
        for (int i = 0; i < 26; i++) {
            float a = random.nextFloat() * Mth.TWO_PI;
            float d = (float) Math.sqrt(random.nextFloat()) * r * 0.9f;
            float size = r * (0.03f + random.nextFloat() * 0.12f);
            if (d + size > r * 0.97f) {
                continue;
            }
            boolean dark = random.nextFloat() < 0.75f;
            int color = dark ? GuiDraw.argb(Math.round((18 + random.nextInt(23)) * fade), 0x000000)
                    : GuiDraw.argb(Math.round((10 + random.nextInt(13)) * fade), 0xF06040);
            GuiDraw.disc(graphics, cx + Mth.cos(a) * d, cy + Mth.sin(a) * d, size, color);
        }
        graphics.flush();

        // The eye: pupil, three dotted rings and three tomoe on each, turning slowly.
        float turn = t * 0.12f;
        int eye = GuiDraw.argb(Math.round(225 * fade), EYE);
        int dots = GuiDraw.argb(Math.round(170 * fade), EYE);
        float[] rings = {0.24f, 0.49f, 0.76f};
        for (int ring = 0; ring < rings.length; ring++) {
            float rr = r * rings[ring];
            int count = Math.max(24, Math.round(Mth.TWO_PI * rr / 2.6f));
            for (int i = 0; i < count; i++) {
                float a = i / (float) count * Mth.TWO_PI;
                float px = cx + Mth.cos(a) * rr;
                float py = cy + Mth.sin(a) * rr;
                GuiDraw.rect(graphics, px - 0.5f, py - 0.5f, px + 0.6f, py + 0.6f, dots);
            }
            float offset = ring * Mth.PI / 3 + turn;
            for (int k = 0; k < 3; k++) {
                float a = offset + k * Mth.TWO_PI / 3;
                tomoe(graphics, cx + Mth.cos(a) * rr, cy + Mth.sin(a) * rr, a, Math.max(2.6f, r * 0.07f), eye, fade);
            }
        }
        GuiDraw.disc(graphics, cx, cy, Math.max(2.5f, r * 0.075f), eye);
        graphics.flush();
    }

    // A tomoe at x, y on a ring, at angle a around the moon: a round head and a tail curling back along the ring.
    private static void tomoe(GuiGraphics graphics, float x, float y, float a, float size, int color, float fade) {
        GuiDraw.disc(graphics, x, y, size + 0.8f, GuiDraw.argb(Math.round(90 * fade), 0x6A140A));
        GuiDraw.disc(graphics, x, y, size, color);
        float back = a - Mth.HALF_PI;
        int steps = 9;
        float prevX = x;
        float prevY = y;
        float prevW = size;
        float prevDir = back;
        for (int i = 1; i <= steps; i++) {
            float s = i / (float) steps;
            float dir = back + s * 1.9f;
            float d = size * (0.1f + 2.3f * s);
            float qx = x + Mth.cos(dir) * d;
            float qy = y + Mth.sin(dir) * d;
            float w = size * (float) Math.pow(1 - s, 0.8);
            float nx1 = -Mth.sin(prevDir);
            float ny1 = Mth.cos(prevDir);
            float nx2 = -Mth.sin(dir);
            float ny2 = Mth.cos(dir);
            GuiDraw.quad(graphics, prevX + nx1 * prevW, prevY + ny1 * prevW, qx + nx2 * w, qy + ny2 * w,
                    qx - nx2 * w, qy - ny2 * w, prevX - nx1 * prevW, prevY - ny1 * prevW, color, color, color, color);
            prevX = qx;
            prevY = qy;
            prevW = w;
            prevDir = dir;
        }
    }

    private static void smoke(GuiGraphics graphics, float cx, float cy, float r, float t, float fade) {
        GuiDraw.shade(graphics, cx - r * 0.75f + Mth.sin(t * 0.08f) * r * 0.1f, cy - r * 0.8f, r * 0.75f, 0x050102, 0.55f * fade);
        GuiDraw.shade(graphics, cx + r * 0.65f + Mth.cos(t * 0.06f) * r * 0.1f, cy - r * 0.55f, r * 0.6f, 0x050102, 0.45f * fade);
        GuiDraw.shade(graphics, cx + r * 0.1f + Mth.sin(t * 0.05f + 2) * r * 0.15f, cy - r * 1.05f, r * 0.7f, 0x050102, 0.5f * fade);
        GuiDraw.shade(graphics, cx - r * 1.2f, cy + r * 0.2f, r * 0.6f, 0x050102, 0.35f * fade);
    }

    // Sharp peaks: 1 on the ridge line, falling straight to 0 between ridges.
    private static float ridge(float u) {
        return 1 - Math.abs(Mth.sin(u));
    }

    private static void mountains(GuiGraphics graphics, int width, int height, float r, float coreX, float coreY, float t, float fade) {
        // Far ridge: wide, dark red-black, covering the moon's lower sides.
        float farBase = coreY + 6;
        int far = 0xFF000000 | 0x1C0506;
        int farRim = GuiDraw.argb(Math.round(150 * fade), 0x7A1408);
        for (int x = 0; x < width; x += 2) {
            float h = r * (0.15f + 0.40f * ridge(x * 0.009f + 1.3f) + 0.20f * ridge(x * 0.023f + 0.4f) + 0.07f * ridge(x * 0.07f + 2));
            int top = Math.round(farBase - h);
            graphics.fill(x, top, x + 2, height, far);
            graphics.fill(x, top, x + 2, top + 1, farRim);
        }
        // Red mist between the ridges.
        for (int i = -3; i <= 3; i++) {
            float mx = coreX + i * width * 0.16f;
            GuiDraw.glow(graphics, mx + Mth.sin(t * 0.3f + i) * 12, farBase - r * 0.05f, r * 0.75f, MIST, (0.22f + 0.06f * Mth.sin(t * 0.7f + i * 1.7f)) * fade);
        }
        GuiDraw.glow(graphics, coreX, coreY + r * 0.25f, r * 0.9f, MIST, 0.3f * fade);

        // Near ridge: a sharp black peak under the core, jagged slopes running down to the sides.
        int near = 0xFF050102;
        int nearRim = GuiDraw.argb(Math.round(200 * fade), 0xA51E10);
        float tip = coreY + 3;
        for (int x = 0; x < width; x += 2) {
            float dx = Math.abs(x + 1 - coreX);
            float jag = 2.5f * Mth.sin(x * 0.19f) + 1.5f * Mth.sin(x * 0.53f + 1) + dx * 0.06f * Mth.sin(x * 0.045f);
            float y = tip + (height - tip) * (float) Math.pow(dx / (width * 0.42f), 0.65) + (dx < 6 ? 0 : jag);
            int top = Math.round(Math.max(tip, y));
            if (top >= height) {
                continue;
            }
            graphics.fill(x, top, x + 2, height, near);
            float lit = Mth.clamp(1 - dx / (width * 0.35f), 0, 1);
            graphics.fill(x, top, x + 2, top + 1, GuiDraw.alpha(nearRim, 0.25f + 0.75f * lit));
        }
        // Low red fog over the near slopes.
        GuiDraw.glow(graphics, coreX - width * 0.25f, height - 20, r * 0.8f, MIST, 0.18f * fade);
        GuiDraw.glow(graphics, coreX + width * 0.25f, height - 20, r * 0.8f, MIST, 0.18f * fade);
    }
}
