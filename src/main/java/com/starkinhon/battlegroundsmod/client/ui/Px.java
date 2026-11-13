package com.starkinhon.battlegroundsmod.client.ui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Window;
import com.starkinhon.battlegroundsmod.client.GuiDraw;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

// The pixel grid of the approved menu previews (Stark 2026-11-13, "Pode portar": the Livro dos Desafios, the mission
// journal on the map, the talk scene and the corner tracker). The preview draws on a grid of big square pixels: the screen
// is cut in S x S real pixels so that it is at least 460 x 262 of them, and every box, line, diamond and letter lands on
// that grid. Here the same: begin() scales the GUI so one unit is one of those pixels, and the helpers below are the
// preview's R, P, T, diamond, frame, smallFrame, hudVeil and button, so the screens are written with the same numbers as
// the preview (JOGO = PRÉVIA). Colors are 0xRRGGBB plus a separate alpha, like the preview's css(color, alpha).
public final class Px {
    public static final int WHITE = 0xFFFFFF, BLACK = 0x000000, GOLD = 0xF2C94C, GOLD_D = 0x966E28, RED = 0xD8352A,
            MUTED = 0x9C93B4, TEXT = 0xE8E2F4, VEIL = 0x080810;

    public static GuiGraphics g;
    public static Font font;
    // real pixels per grid pixel, and the screen size in grid pixels
    public static int S = 2;
    public static int GW = 460, GH = 262;
    // mouse in grid pixels
    public static float mx = -1, my = -1;
    // seconds, for the small animations (bobbing pins, pulses)
    public static float time;
    private static float k = 1;
    private static int depth;

    private Px() {
    }

    // Starts drawing on the grid (call end() after). Mouse in GUI coordinates (or -1 for the HUD).
    public static void begin(GuiGraphics graphics, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        int ww = window.getWidth(), wh = window.getHeight();
        S = Math.max(1, (int) Math.floor(Math.min(ww / 460f, wh / 262f)));
        GW = ww / S;
        GH = wh / S;
        double scale = window.getGuiScale();
        k = (float) (S / scale);
        g = graphics;
        font = minecraft.font;
        time = (Util.getMillis() % 10_000_000L) / 1000f;
        mx = mouseX < 0 ? -1 : (float) (mouseX * scale / S);
        my = mouseY < 0 ? -1 : (float) (mouseY * scale / S);
        graphics.pose().pushPose();
        graphics.pose().scale(k, k, 1);
        depth++;
    }

    public static void end() {
        if (depth > 0) {
            g.pose().popPose();
            depth--;
        }
    }

    // A GUI coordinate (mouse events) to grid pixels.
    public static float toGrid(double gui) {
        return (float) (gui * Minecraft.getInstance().getWindow().getGuiScale() / S);
    }

    // ---------------------------------------------------------------- colors

    public static int rgb(int r, int g, int b) {
        return (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
    }

    public static int mix(int a, int b, float t) {
        return GuiDraw.mix(a, b, t);
    }

    // ARGB from a color and an alpha 0..1.
    public static int c(int rgb, float a) {
        return (Math.round(Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    // A color readable on the dark veil (the preview's ink): dark colors are lifted towards white.
    public static int ink(int rgb) {
        int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float l = (0.3f * r + 0.59f * gg + 0.11f * b) / 255f;
        return l < 0.55f ? mix(rgb, WHITE, (0.55f - l) * 1.2f) : rgb;
    }

    public static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float easeOut(float t) {
        t = clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    public static float easeIn(float t) {
        return t * t * t;
    }

    public static float easeBack(float t) {
        t = clamp(t, 0, 1);
        float c = 1.9f;
        return 1 + (c + 1) * (t - 1) * (t - 1) * (t - 1) + c * (t - 1) * (t - 1);
    }

    public static float smoother(float x) {
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    // ---------------------------------------------------------------- boxes

    // A filled box on the grid (rounded like the preview: both edges to the nearest pixel).
    public static void R(float x, float y, float w, float h, int argb) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        if (x1 <= x0 || y1 <= y0 || (argb >>> 24) == 0) {
            return;
        }
        g.fill(x0, y0, x1, y1, argb);
    }

    public static void R(float x, float y, float w, float h, int rgb, float a) {
        R(x, y, w, h, c(rgb, a));
    }

    public static void P(float x, float y, int rgb, float a) {
        R(x, y, 1, 1, c(rgb, a));
    }

    public static boolean over(float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // A pixel diamond (filled or just its outline).
    public static void diamond(float cx, float cy, int r, int rgb, float a, boolean fill) {
        int x = Math.round(cx), y = Math.round(cy);
        for (int k = -r; k <= r; k++) {
            int ww = r - Math.abs(k);
            if (fill) {
                R(x - ww, y + k, ww * 2 + 1, 1, rgb, a);
            } else {
                P(x - ww, y + k, rgb, a);
                if (ww > 0) {
                    P(x + ww, y + k, rgb, a);
                }
            }
        }
    }

    public static void diamond(float cx, float cy, int r, int rgb, float a) {
        diamond(cx, cy, r, rgb, a, true);
    }

    // A soft round light (normal blending), radius in grid pixels.
    public static void glow(float cx, float cy, float r, int rgb, float a) {
        GuiDraw.shade(g, cx, cy, r, rgb, a);
    }

    // A vertical run of 2 pixel rows from color a to color b (the preview's for(i<h;i+=2) gradients).
    public static void vgrad(float x, float y, float w, float h, int top, float ta, int bottom, float ba) {
        for (int i = 0; i < h; i += 2) {
            float t = i / Math.max(1f, h);
            R(x, y + i, w, Math.min(2, h - i), mix(top, bottom, t), lerp(ta, ba, t));
        }
    }

    // The frame of the new style (Stark 2026-11-13, "bem melhor", for every panel): no colored box, a dark veil with a
    // touch of the color on top, gold hairlines fading at the ends and a diamond in the middle. gem = a bigger diamond on
    // top and a small one under; title = a label sitting on the top line.
    public static void frame(float x, float y, float w, float h, int rgb, float alpha, boolean gem, boolean corners, String title) {
        int top = mix(VEIL, rgb, 0.16f);
        for (int i = 0; i < h; i += 2) {
            float t = i / h;
            R(x, y + i, w, Math.min(2, h - i), mix(top, VEIL, Math.min(1, t * 1.6f)), alpha);
        }
        float la = Math.min(1, alpha + 0.1f);
        for (int i = 0; i < w; i++) {
            float t = 1 - Math.abs(i - (w - 1) / 2f) / (w / 2f), kk = (float) Math.pow(Math.max(0, t), 0.55);
            P(x + i, y, GOLD, 0.9f * kk * la);
            P(x + i, y + h - 1, GOLD, 0.5f * kk * la);
        }
        for (int i = 0; i < h; i++) {
            float t = 1 - Math.abs(i - (h - 1) / 2f) / (h / 2f);
            float kk = 0.18f * (float) Math.pow(Math.max(0, t), 0.8);
            P(x, y + i, GOLD, kk);
            P(x + w - 1, y + i, GOLD, kk);
        }
        int cx = Math.round(x + w / 2);
        if (gem || corners) {
            diamond(cx, y, gem ? 3 : 2, GOLD, 1);
            diamond(cx, y, 1, WHITE, 0.9f);
        }
        if (gem) {
            diamond(cx, y + h - 1, 2, GOLD, 0.7f);
        }
        if (title != null) {
            int w2 = tw(title, 8, true) + 16, tx = Math.round(cx - w2 / 2f);
            R(tx, y - 5, w2, 11, VEIL, 0.95f);
            diamond(tx, y, 2, GOLD, 1);
            diamond(tx + w2, y, 2, GOLD, 1);
            T(title, cx, y - 4, rgb(255, 236, 180), 8, true, 0, true, 1);
        }
    }

    // A small box (tabs, chips, ladder cells): dark, a little of the color when lit, a bright bottom line and a diamond.
    public static void smallFrame(float x, float y, float w, float h, int rgb, boolean lit, float a) {
        R(x, y, w, h, lit ? mix(VEIL, rgb, 0.28f) : VEIL, (lit ? 0.85f : 0.62f) * a);
        if (lit) {
            R(x, y + h - 1, w, 1, mix(rgb, WHITE, 0.35f), a);
            R(x, y, w, 1, rgb, 0.3f * a);
            if (w >= 16) {
                diamond(Math.round(x + w / 2), y + h - 1, 1, mix(rgb, WHITE, 0.6f), a);
            }
        } else {
            R(x + 1, y + h - 1, w - 2, 1, rgb, 0.4f * a);
        }
    }

    // The corner tracker's veil: born at the screen edge, fading to the right, gold line on top.
    public static void hudVeil(float x, float y, float w, float h, int rgb, float a, boolean small) {
        int v = mix(VEIL, rgb, 0.12f);
        for (int i = 0; i < w; i += 2) {
            float t = i / w, u = Math.min(1, Math.max(0, (t - 0.3f) / 0.7f)), kk = 1 - u * u * (3 - 2 * u);
            R(x + i, y, 2, h, v, a * kk);
        }
        for (int i = 0; i < w; i++) {
            float kk = Math.max(0, 1 - i / (w * 0.85f));
            P(x + i, y, GOLD, (small ? 0.35f : 0.85f) * kk);
            if (!small) {
                P(x + i, y + h - 1, GOLD, 0.3f * kk);
            }
        }
        if (!small) {
            diamond(x + 2, y, 2, GOLD, 1);
            diamond(x + 2, y, 1, WHITE, 0.9f);
        }
    }

    // The ornate gold button (LUTAR, Rastrear): darker downwards, bright edge, a diamond on each side.
    public static void button(float bx, float by, float bw, float bh, int base, boolean hot) {
        for (int i = 0; i < bh; i++) {
            R(bx, by + i, bw, 1, mix(base, BLACK, (hot ? 0.42f : 0.58f) + i / bh * 0.25f), 1);
        }
        int bc = mix(base, WHITE, hot ? 0.4f : 0.15f);
        R(bx + 1, by, bw - 2, 1, bc, 1);
        R(bx + 1, by + bh - 1, bw - 2, 1, bc, 1);
        R(bx, by + 1, 1, bh - 2, bc, 1);
        R(bx + bw - 1, by + 1, 1, bh - 2, bc, 1);
        diamond(bx, by + bh / 2, 2, bc, 1);
        diamond(bx + bw - 1, by + bh / 2, 2, bc, 1);
        R(bx + 2, by + 1, bw - 4, 1, WHITE, 0.25f);
    }

    // A gold hairline that fades out to the right, from x to x + w.
    public static void fadeLine(float x, float y, float w, int rgb, float a) {
        for (int i = 0; i < w; i++) {
            P(x + i, y, rgb, a * (1 - i / w));
        }
    }

    // A hairline bright in the middle and gone at both ends.
    public static void centerLine(float x, float y, float w, int rgb, float a, float edge) {
        for (int i = 0; i < w; i++) {
            float e = Math.min(i, w - i) / (w * edge);
            P(x + i, y, rgb, a * clamp(e, 0, 1));
        }
    }

    // ---------------------------------------------------------------- text

    // The Minecraft font at the size of the preview's pixel font (px): its letters are kept on whole real pixels.
    public static float fontScale(float px) {
        int real = Math.max(1, Math.round(px / 10f * S));
        return real / (float) S;
    }

    private static Component comp(String text, boolean bold) {
        return bold ? Component.literal(text).withStyle(Style.EMPTY.withBold(true)) : Component.literal(text);
    }

    public static int tw(String text, float px, boolean bold) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(font.width(comp(text, bold)) * fontScale(px));
    }

    public static int tw(String text, float px) {
        return tw(text, px, false);
    }

    // Text: align -1 left, 0 center, 1 right; y is the top of the capitals. Returns the width.
    public static int T(String text, float x, float y, int rgb, float px, boolean bold, int align, boolean shadow, float a) {
        if (text == null || text.isEmpty() || a < 0.02f) {
            return 0;
        }
        float s = fontScale(px);
        Component c = comp(text, bold);
        int w = (int) Math.ceil(font.width(c) * s);
        float xx = Math.round(align == 0 ? x - w / 2f : align > 0 ? x - w : x);
        float yy = Math.round(y + px * 0.12f);
        if (shadow) {
            draw(c, xx + 1, yy + 1, mix(rgb, BLACK, 0.75f), a, s);
        }
        draw(c, xx, yy, rgb, a, s);
        return w;
    }

    public static int T(String text, float x, float y, int rgb, float px) {
        return T(text, x, y, rgb, px, false, -1, true, 1);
    }

    public static int T(String text, float x, float y, int rgb, float px, boolean bold) {
        return T(text, x, y, rgb, px, bold, -1, true, 1);
    }

    public static int Tc(String text, float x, float y, int rgb, float px, boolean bold) {
        return T(text, x, y, rgb, px, bold, 0, true, 1);
    }

    public static int Tr(String text, float x, float y, int rgb, float px, boolean bold) {
        return T(text, x, y, rgb, px, bold, 1, true, 1);
    }

    private static void draw(Component c, float x, float y, int rgb, float a, float s) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, c, 0, 0, c(rgb, Math.max(a, 0.02f)), false);
        g.pose().popPose();
    }

    // Splits a text into lines no wider than w (by words).
    public static List<String> wrap(String text, float w, float px, boolean bold) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String line = "";
        for (String word : text.split(" ")) {
            String t = line.isEmpty() ? word : line + " " + word;
            if (tw(t, px, bold) > w && !line.isEmpty()) {
                out.add(line);
                line = word;
            } else {
                line = t;
            }
        }
        if (!line.isEmpty()) {
            out.add(line);
        }
        return out;
    }

    public static List<String> wrap(String text, float w, float px) {
        return wrap(text, w, px, false);
    }

    // Cuts a text with "…" until it fits.
    public static String fit(String text, float w, float px) {
        String t = text;
        while (tw(t, px) > w && t.length() > 3) {
            t = t.substring(0, t.length() - 2) + "…";
        }
        return t;
    }

    // The vanilla purple tooltip, on the grid (preview vtip): lines of [text, color].
    public static void tooltip(List<String> lines, List<Integer> colors, float x0, float y0) {
        if (lines.isEmpty()) {
            return;
        }
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, tw(l, 8));
        }
        w += 8;
        int h = lines.size() * 10 + 4;
        float x = x0 + 10, y = y0 - 12;
        if (x + w > GW - 2) {
            x = x0 - w - 10;
        }
        if (y + h > GH - 2) {
            y = GH - h - 2;
        }
        if (y < 2) {
            y = 2;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        R(x + 1, y, w - 2, h, 0x100010, 0.94f);
        R(x, y + 1, w, h - 2, 0x100010, 0.94f);
        for (int i = 1; i < h - 1; i++) {
            int cc = mix(rgb(80, 0, 255), rgb(40, 0, 127), i / (float) h);
            P(x + 1, y + i, cc, 0.6f);
            P(x + w - 2, y + i, cc, 0.6f);
        }
        R(x + 2, y + 1, w - 4, 1, rgb(80, 0, 255), 0.6f);
        R(x + 2, y + h - 2, w - 4, 1, rgb(40, 0, 127), 0.6f);
        float yy = y + 3;
        for (int i = 0; i < lines.size(); i++) {
            T(lines.get(i), x + 4, yy, colors.get(Math.min(i, colors.size() - 1)), 8);
            yy += 10;
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- click areas (the preview's hits)

    public record Hit(float x, float y, float w, float h, Runnable click, List<String> tip, List<Integer> tipColors) {
        boolean in(float px, float py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public static final List<Hit> hits = new ArrayList<>();

    public static void hit(float x, float y, float w, float h, Runnable click) {
        hits.add(new Hit(x, y, w, h, click, null, null));
    }

    public static void hit(float x, float y, float w, float h, Runnable click, List<String> tip, List<Integer> tipColors) {
        hits.add(new Hit(x, y, w, h, click, tip, tipColors));
    }

    // The top most area under a point (the last one added), or null.
    public static Hit hitAt(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).in(x, y)) {
                return hits.get(i);
            }
        }
        return null;
    }

    // Clicks the area under the point; true when something took it.
    public static boolean click(float x, float y) {
        Hit h = hitAt(x, y);
        if (h != null && h.click() != null) {
            h.click().run();
            return true;
        }
        return h != null;
    }

    // The tooltip of the area under the mouse, if it has one.
    public static void drawTip() {
        Hit h = hitAt(mx, my);
        if (h != null && h.tip() != null && !h.tip().isEmpty()) {
            tooltip(h.tip(), h.tipColors(), mx, my);
        }
    }

    // ---------------------------------------------------------------- small pixel icons

    // The direction arrow (0 = ahead, then clockwise in eighths), 9 x 9.
    private static final String[] ARROW_UP = {
            "....#....",
            "...###...",
            "..#####..",
            ".#######.",
            "...###...",
            "...###...",
            "...###...",
            "...###...",
            "...###..."};
    private static final String[] ARROW_DIAG = {
            "...######",
            "....#####",
            ".....####",
            "....#####",
            "...###.##",
            "..###...#",
            ".###.....",
            "###......",
            ".#......."};

    public static void arrow(float x, float y, int dir, int rgb, float a) {
        dir = Math.floorMod(dir, 8);
        String[] pat = dir % 2 == 0 ? ARROW_UP : ARROW_DIAG;
        int turns = dir / 2;
        int bx = Math.round(x), by = Math.round(y);
        for (int j = 0; j < 9; j++) {
            for (int i = 0; i < 9; i++) {
                if (pat[j].charAt(i) != '#') {
                    continue;
                }
                int u = i, v = j;
                for (int t = 0; t < turns; t++) {
                    int nu = 8 - v;
                    v = u;
                    u = nu;
                }
                P(bx + u, by + v, rgb, a);
            }
        }
    }

    // The "tracking" mark (a small sight), 7 x 7.
    public static void sight(float x, float y, int rgb, float a) {
        int cx = Math.round(x) + 3, cy = Math.round(y) + 3;
        R(cx - 2, cy - 3, 5, 1, rgb, a);
        R(cx - 2, cy + 3, 5, 1, rgb, a);
        R(cx - 3, cy - 2, 1, 5, rgb, a);
        R(cx + 3, cy - 2, 1, 5, rgb, a);
        P(cx, cy, rgb, a);
        P(cx, cy - 4, rgb, a);
        P(cx, cy + 4, rgb, a);
        P(cx - 4, cy, rgb, a);
        P(cx + 4, cy, rgb, a);
    }

    // A check mark, 7 x 6.
    public static void check(float x, float y, int rgb, float a) {
        int bx = Math.round(x), by = Math.round(y);
        int[][] pts = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {1, 3}, {2, 4}, {3, 3}, {4, 2}, {5, 1}, {6, 0}};
        for (int[] p : pts) {
            P(bx + p[0], by + p[1], rgb, a);
        }
    }

    // A small star, 7 x 7.
    public static void star(float x, float y, int rgb, float a) {
        int bx = Math.round(x), by = Math.round(y);
        R(bx + 3, by, 1, 2, rgb, a);
        R(bx, by + 2, 7, 1, rgb, a);
        R(bx + 1, by + 3, 5, 1, rgb, a);
        R(bx + 2, by + 4, 3, 1, rgb, a);
        R(bx + 1, by + 5, 2, 1, rgb, a);
        R(bx + 4, by + 5, 2, 1, rgb, a);
        P(bx, by + 6, rgb, a);
        P(bx + 6, by + 6, rgb, a);
    }

    // A half filled circle (in progress), 7 x 7.
    public static void half(float x, float y, int rgb, float a) {
        int bx = Math.round(x), by = Math.round(y);
        for (int j = 0; j < 7; j++) {
            for (int i = 0; i < 7; i++) {
                float d = (float) Math.hypot(i - 3, j - 3);
                if (d < 3.6f && (d > 2.4f || i <= 3)) {
                    P(bx + i, by + j, rgb, a);
                }
            }
        }
    }

    // The close cross, 7 x 7.
    public static void cross(float x, float y, int rgb, float a) {
        int bx = Math.round(x), by = Math.round(y);
        for (int i = 0; i < 7; i++) {
            P(bx + i, by + i, rgb, a);
            P(bx + 6 - i, by + i, rgb, a);
        }
    }

    // The state glyph of a mission (NOVA !, EM ANDAMENTO half, FEITA HOJE check, CONCLUÍDA star, NÃO DESCOBERTA ?),
    // centered on cx, cy.
    public static void stateGlyph(String state, float cx, float cy, int rgb, float a, float px) {
        switch (state) {
            case "andamento" -> half(cx - 3, cy - 3, rgb, a);
            case "feita" -> check(cx - 3, cy - 3, rgb, a);
            case "concluida" -> star(cx - 3, cy - 3, rgb, a);
            case "oculta" -> T("?", cx + 0.5f, cy - px * 0.45f, rgb, px, true, 0, false, a);
            default -> T("!", cx + 0.5f, cy - px * 0.45f, rgb, px, true, 0, false, a);
        }
    }
}
