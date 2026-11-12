package com.starkinhon.battlegroundsmod.client;

import net.minecraft.client.gui.GuiGraphics;

// Pixel pieces of the Livro das Almas (PowerSelectScreen), drawn rect by rect in GUI pixels like the web preview
// (forja/menus/template.html): the ornate frame of Stark's references (gradient fill, double line, horn-and-leaf
// corners, a gem on top), the small frame of tabs and slots, and the padlock of a skill not awakened yet.
public final class SoulBookDraw {
    private SoulBookDraw() {
    }

    static int mix(int a, int b, float t) {
        return GuiDraw.mix(a, b, Math.max(0, Math.min(1, t)));
    }

    static int a(int rgb, float alpha) {
        return GuiDraw.argb(Math.round(255 * Math.max(0, Math.min(1, alpha))), rgb);
    }

    static void r(GuiGraphics g, int x, int y, int w, int h, int argb) {
        if (w > 0 && h > 0) {
            g.fill(x, y, x + w, y + h, argb);
        }
    }

    static void p(GuiGraphics g, int x, int y, int argb) {
        g.fill(x, y, x + 1, y + 1, argb);
    }

    // Corner pattern (top left; mirrored on the others) and its lit pixels.
    private static final int[][] CORNER = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 1}, {-1, -1}, {-2, -1},
            {-1, -2}, {-2, -3}, {-3, -2}, {5, -1}, {6, -2}, {-1, 5}, {-2, 6}, {2, 2}, {3, 1}, {1, 3}};
    private static final int[][] CORNER_HI = {{-2, -3}, {-3, -2}, {6, -2}, {-2, 6}};

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int c, boolean gem, boolean corners, float alpha) {
        int dark = mix(c, 0x06040E, 0.86f), mid = mix(c, 0x000000, 0.25f), hi = mix(c, 0xFFFFFF, 0.45f);
        int deep = mix(c, 0x0A0614, 0.6f);
        for (int i = 0; i < h - 4; i += 2) {
            r(g, x + 2, y + 2 + i, w - 4, Math.min(2, h - 4 - i), a(mix(dark, deep, i / (float) h), alpha));
        }
        r(g, x + 1, y, w - 2, 1, a(mid, 1));
        r(g, x + 1, y + h - 1, w - 2, 1, a(mid, 1));
        r(g, x, y + 1, 1, h - 2, a(mid, 1));
        r(g, x + w - 1, y + 1, 1, h - 2, a(mid, 1));
        r(g, x + 2, y + 2, w - 4, 1, a(c, 0.35f));
        r(g, x + 2, y + h - 3, w - 4, 1, a(c, 0.25f));
        if (corners) {
            int[][] origins = {{1, 1, x, y}, {-1, 1, x + w - 1, y}, {1, -1, x, y + h - 1}, {-1, -1, x + w - 1, y + h - 1}};
            for (int[] o : origins) {
                for (int[] q : CORNER) {
                    p(g, o[2] + q[0] * o[0], o[3] + q[1] * o[1], a(hi, 1));
                }
                for (int[] q : CORNER_HI) {
                    p(g, o[2] + q[0] * o[0], o[3] + q[1] * o[1], 0xFFFFFFFF);
                }
            }
        }
        if (gem) {
            int cx = x + w / 2;
            r(g, cx - 5, y - 1, 11, 1, a(mid, 1));
            for (int i = 0; i < 4; i++) {
                r(g, cx - 3 + i, y - 4 + i, 7 - 2 * i, 1, a(i == 0 ? hi : c, 1));
                r(g, cx - 3 + i, y - 4 - i, 7 - 2 * i, 1, a(i == 0 ? hi : mix(c, 0xFFFFFF, 0.2f), 1));
            }
            p(g, cx, y - 3, 0xFFFFFFFF);
        }
    }

    public static void smallFrame(GuiGraphics g, int x, int y, int w, int h, int c, boolean lit) {
        int mid = mix(c, 0x000000, lit ? 0 : 0.35f), dk = mix(c, 0x06040E, 0.85f);
        r(g, x + 1, y + 1, w - 2, h - 2, a(dk, 1));
        r(g, x + 1, y, w - 2, 1, a(mid, 1));
        r(g, x + 1, y + h - 1, w - 2, 1, a(mid, 1));
        r(g, x, y + 1, 1, h - 2, a(mid, 1));
        r(g, x + w - 1, y + 1, 1, h - 2, a(mid, 1));
        int hi = a(mix(c, 0xFFFFFF, lit ? 0.7f : 0.3f), 1);
        p(g, x, y, hi);
        p(g, x + w - 1, y, hi);
        p(g, x, y + h - 1, hi);
        p(g, x + w - 1, y + h - 1, hi);
    }

    // 8 x 9 padlock (gold body, grey shackle).
    public static void padlock(GuiGraphics g, int x, int y) {
        r(g, x + 2, y, 4, 1, 0xFFDDDDDD);
        r(g, x + 1, y + 1, 1, 3, 0xFFDDDDDD);
        r(g, x + 6, y + 1, 1, 3, 0xFFDDDDDD);
        r(g, x, y + 4, 8, 5, 0xFFE8C34A);
        r(g, x, y + 4, 8, 1, 0xFFFFF2A8);
        r(g, x + 3, y + 6, 2, 2, 0xFF5A3C00);
    }

    // Pixel disc (rows of rects), for glows behind the crystals.
    public static void disc(GuiGraphics g, int cx, int cy, int radius, int argb) {
        for (int yy = -radius; yy <= radius; yy++) {
            int xx = (int) Math.round(Math.sqrt(radius * radius - yy * yy));
            r(g, cx - xx, cy + yy, xx * 2, 1, argb);
        }
    }
}
