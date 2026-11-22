// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.ui;

import static com.starkinhon.battlegroundsmod.client.ui.Canvas.bay;
import static com.starkinhon.battlegroundsmod.client.ui.Canvas.hash;
import static com.starkinhon.battlegroundsmod.client.ui.Canvas.vnoise;
import static com.starkinhon.battlegroundsmod.client.ui.Px.mix;
import static com.starkinhon.battlegroundsmod.client.ui.Px.rgb;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

// The materials of the universe themed screens (Stark 2026-11-22, approved in the web preview "Diário Sem Cara de IA"):
// no dark veil, gold hairlines or diamonds, each universe has its own stuff. Naruto is a scroll (burnt paper, wooden rods,
// brush ink, red seal), Dragon Ball a Capsule Corp gadget (plastic shell with screws, green radar screen, rubber
// buttons) and Jujutsu a school dossier (manila folder, typed sheet, photo with a clip, rubber stamp, blue pen). The
// universes with no theme of their own yet (Bleach, Demon Slayer) use the dossier (Stark 2026-11-22: "os mais
// neutros"). The big static parts are painted once into textures (Canvas); the small ink marks are painted live.
public final class Art {
    public enum Theme {
        SCROLL, DEVICE, DOSSIER
    }

    public static Theme theme(String uni) {
        if ("naruto".equals(uni)) {
            return Theme.SCROLL;
        }
        if ("dragonball".equals(uni) || "db".equals(uni)) {
            return Theme.DEVICE;
        }
        return Theme.DOSSIER;
    }

    // Naruto
    public static final int N_PAPER = rgb(234, 220, 186), N_PAPER2 = rgb(214, 196, 156), N_BURN = rgb(150, 108, 62), N_INK = rgb(38, 28, 22),
            N_RED = rgb(160, 40, 30), N_GREY = rgb(104, 90, 74), N_W0 = rgb(62, 38, 20), N_W1 = rgb(122, 78, 42), N_W2 = rgb(178, 124, 72),
            N_W3 = rgb(218, 172, 112), N_LAC = rgb(32, 20, 16);
    // Dragon Ball
    public static final int D_SHELL = rgb(236, 234, 226), D_SH2 = rgb(198, 195, 184), D_OUT = rgb(54, 56, 64), D_DEP = rgb(160, 157, 146),
            D_SCR = rgb(10, 40, 31), D_SCR2 = rgb(18, 58, 44), D_GRID = rgb(22, 68, 52), D_TXT = rgb(200, 255, 222), D_DIM = rgb(112, 196, 152),
            D_AMB = rgb(255, 206, 96), D_BLUE = rgb(34, 92, 176), D_BTN = rgb(238, 106, 44), D_BTND = rgb(146, 50, 18);
    // Jujutsu (and the universes with no theme yet)
    public static final int J_FOLDER = rgb(212, 180, 118), J_F2 = rgb(176, 142, 86), J_PAPER = rgb(241, 238, 229), J_P2 = rgb(222, 218, 206),
            J_TYPE = rgb(40, 40, 44), J_GREY = rgb(118, 116, 112), J_PEN = rgb(40, 70, 172), J_STAMP = rgb(190, 36, 40), J_CLIP = rgb(172, 178, 188);

    // The mission states (nova, em andamento, feita hoje, concluída, oculta) as ink on paper and as light on the radar.
    public static final int[] INK_STATE = {rgb(214, 150, 20), rgb(40, 110, 200), rgb(40, 140, 60), rgb(130, 80, 200), rgb(110, 100, 120)};
    public static final int[] TAG_STATE = {rgb(240, 200, 80), rgb(120, 190, 250), rgb(120, 210, 120), rgb(196, 160, 250), rgb(120, 110, 130)};
    public static final int[] RADAR_STATE = {rgb(255, 214, 80), rgb(120, 200, 255), rgb(130, 230, 130), rgb(200, 170, 255), rgb(130, 120, 140)};

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static int next;

    private Art() {
    }

    // Draws the picture painted by make at (x, y), painting it the first time.
    public static void blit(String key, float x, float y, Supplier<Canvas> make) {
        ResourceLocation tex = CACHE.get(key);
        int[] size = SIZES.get(key);
        if (tex == null) {
            Canvas c = make.get();
            tex = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/art_" + next++);
            Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(c.image()));
            CACHE.put(key, tex);
            size = new int[] {c.w, c.h};
            SIZES.put(key, size);
        }
        RenderSystem.enableBlend();
        Px.g.blit(tex, Math.round(x), Math.round(y), 0, 0, size[0], size[1], size[0], size[1]);
    }

    private static final Map<String, int[]> SIZES = new HashMap<>();

    // ---------------------------------------------------------------- Naruto

    // Burnt paper with fibers; torn = a ragged right edge.
    public static void paper(Canvas g, int x0, int y0, int w, int h, int seed, boolean torn) {
        for (int y = 0; y < h; y++) {
            int jl = Math.round(vnoise(0, y * 0.15f, seed) * 1.4f);
            int jr = torn ? Math.round(vnoise(9, y * 0.5f, seed) * 4 + hash(1, y, seed) * 1.5f) : Math.round(vnoise(7, y * 0.15f, seed) * 1.4f);
            for (int x = jl; x < w - jr; x++) {
                int ex = Math.min(Math.min(x - jl, w - jr - 1 - x), Math.min(y, h - 1 - y));
                float n = hash(x, y, seed) * 0.06f - 0.03f + (vnoise(x * 0.07f, y * 0.07f, seed + 1) - 0.5f) * 0.10f;
                int c = mix(N_PAPER, N_PAPER2, Math.max(0, Math.min(1, 0.5f + n * 5 - 0.3f)));
                if (hash(x / 5, y, seed + 2) < 0.05f && hash(x, y, seed + 3) < 0.8f) {
                    c = mix(c, N_PAPER2, 0.55f);
                }
                float burn = Math.max(0, 1 - ex / 4.5f);
                if (burn > 0 && bay(x, y) < burn * 0.85f) {
                    c = mix(c, N_BURN, 0.35f + burn * 0.35f);
                }
                g.P(x0 + x, y0 + y, c);
            }
        }
    }

    // A wooden rod with lacquer caps sticking 5 pixels out on each side.
    public static void rod(Canvas g, int x, int y, int w) {
        int[] rows = {N_W0, N_W1, N_W2, N_W3, N_W2, N_W1, N_W0};
        for (int i = 0; i < rows.length; i++) {
            g.R(x, y + i, w, 1, rows[i]);
        }
        for (int i = 0; i < w; i++) {
            if (hash(i, 0, 9) < 0.12f) {
                g.P(x + i, y + 2, N_W1, 0.6f);
            }
            if (hash(i, 1, 9) < 0.1f) {
                g.P(x + i, y + 4, N_W1, 0.5f);
            }
        }
        for (int ex : new int[] {x - 5, x + w}) {
            g.R(ex, y - 1, 5, 9, N_LAC);
            g.R(ex, y, 5, 1, rgb(80, 60, 52));
            g.R(ex + 1, y + 1, 1, 6, rgb(70, 50, 44), 0.8f);
            g.R(ex, y + 7, 5, 1, rgb(16, 10, 8));
        }
    }

    // The mission scroll: paper between two rods. Drawn at (x - 9, y - 1); the paper starts at (x, y + 3).
    public static void scroll(float x, float y, int w, int h) {
        blit("scroll|" + w + "|" + h, x - 9, y - 1, () -> {
            Canvas g = new Canvas(w + 22, h + 8);
            int ox = 9, oy = 1;
            g.R(ox + 4, oy + 6, w, h - 6, 0, 0.35f);
            paper(g, ox, oy + 3, w, h - 6, 21, false);
            rod(g, ox - 4, oy, w + 8);
            rod(g, ox - 4, oy + h - 6, w + 8);
            return g;
        });
    }

    public static void paperSlip(float x, float y, int w, int h, int seed) {
        blit("slip|" + w + "|" + h + "|" + seed, x, y, () -> {
            Canvas g = new Canvas(w, h);
            paper(g, 0, 0, w, h, seed, false);
            return g;
        });
    }

    // An ink wash behind a face.
    public static void blot(float cx, float cy, int r, int rgb, int seed, float a) {
        blit("blot|" + r + "|" + rgb + "|" + seed + "|" + a, cx - r - 3, cy - r - 3, () -> {
            Canvas g = new Canvas(r * 2 + 7, r * 2 + 7);
            int c0 = r + 3;
            for (int y = -r - 2; y <= r + 2; y++) {
                for (int x = -r - 2; x <= r + 2; x++) {
                    double an = Math.atan2(y, x);
                    float d = (float) Math.hypot(x, y);
                    float edge = r * (0.82f + 0.3f * vnoise((float) Math.cos(an) * 2 + 5, (float) Math.sin(an) * 2 + 5, seed));
                    if (d > edge) {
                        continue;
                    }
                    float rim = d > edge - 1.6f ? 1.7f : 1;
                    float k = (1 - d / edge * 0.35f) * rim;
                    if (bay(x + 50, y + 50) < k * 0.95f) {
                        g.P(c0 + x, c0 + y, rgb, a * Math.min(1, k));
                    }
                }
            }
            return g;
        });
    }

    // A wooden plaque button with a cord and a tassel on the left (the cord is drawn live, it swings).
    public static void woodTag(float x, float y, int w, int h, boolean hot) {
        blit("wood|" + w + "|" + h + "|" + hot, x, y, () -> {
            Canvas g = new Canvas(w, h + 2);
            g.R(1, h, w - 2, 2, N_W0);
            for (int j = 0; j < h; j++) {
                int ch = (j == 0 || j == h - 1) ? 1 : 0;
                for (int i = ch; i < w - ch; i++) {
                    double grain = Math.sin(i * 0.18 + vnoise(i * 0.05f, j * 0.4f, 3) * 5 + j * 0.9);
                    int c = mix(N_W2, N_W1, (float) (0.35 + grain * 0.25 + (hash(i, j, 4) - 0.5) * 0.1));
                    if (hot) {
                        c = mix(c, N_W3, 0.18f);
                    }
                    g.P(i, j, c);
                }
            }
            g.R(1, 0, w - 2, 1, mix(N_W3, rgb(255, 240, 210), 0.2f));
            g.R(0, 1, 1, h - 2, N_W0);
            g.R(w - 1, 1, 1, h - 2, N_W0);
            g.R(1, h - 1, w - 2, 1, N_W0);
            g.P(3, h / 2 - 1, N_W0);
            g.P(3, h / 2, N_W0);
            return g;
        });
    }

    // ---------------------------------------------------------------- Dragon Ball

    // The plastic shell with four screws. With screen: the green radar screen at (8, 18) inside it.
    public static void shell(Canvas g, int x, int y, int w, int h) {
        g.roundRect(x + 3, y + 4, w, h, 6, 0, 0.35f);
        g.roundRect(x - 1, y - 1, w + 2, h + 4, 7, D_OUT, 1);
        g.roundRect(x, y + 3, w, h, 6, D_DEP, 1);
        g.roundRect(x, y, w, h, 6, D_SHELL, 1);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                float k = (j / (float) h) * 0.6f + (i / (float) w) * 0.2f;
                if (bay(i, j) < k * 0.35f && inside(i, j, w, h, 6)) {
                    g.P(x + i, y + j, D_SH2, 0.5f);
                }
            }
        }
        g.R(x + 4, y + 1, w - 8, 1, 0xFFFFFF, 0.8f);
        for (int[] s : new int[][] {{x + 5, y + 5}, {x + w - 8, y + 5}, {x + 5, y + h - 8}, {x + w - 8, y + h - 8}}) {
            g.R(s[0], s[1], 3, 3, rgb(160, 160, 166));
            g.P(s[0] + 1, s[1], rgb(200, 200, 206));
            g.R(s[0], s[1] + 1, 3, 1, rgb(96, 96, 104));
        }
    }

    private static boolean inside(int i, int j, int w, int h, float r) {
        float dy = j < r ? r - j - 0.5f : j >= h - r ? j - (h - r) + 0.5f : 0;
        int cut = dy > 0 ? Math.round(r - (float) Math.sqrt(Math.max(0, r * r - dy * dy))) : 0;
        return i >= cut && i < w - cut;
    }

    // The radar screen: dark green with a soft center, a 12 pixel grid, scanlines and dark corners.
    public static void screen(Canvas g, int x, int y, int w, int h) {
        g.R(x - 2, y - 2, w + 4, h + 4, rgb(36, 38, 44));
        g.R(x - 1, y - 1, w + 2, 1, rgb(20, 22, 26));
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                float d = (float) Math.hypot((i - w / 2f) / (w / 2f), (j - h / 2f) / (h / 2f));
                int c = D_SCR;
                if (bay(i, j) < (1 - d) * 0.9f) {
                    c = D_SCR2;
                }
                if (i % 12 == 0 || j % 12 == 0) {
                    c = mix(c, D_GRID, 0.8f);
                }
                if (j % 2 == 1) {
                    c = mix(c, 0, 0.12f);
                }
                if (bay(i, j) < (d - 0.85f) * 2) {
                    c = mix(c, 0, 0.5f);
                }
                g.P(x + i, y + j, c);
            }
        }
    }

    // The mission gadget: shell with the screen at (x + 8, y + 18), w - 16 by h - 52. Drawn at (x - 1, y - 1).
    public static void device(float x, float y, int w, int h) {
        blit("device|" + w + "|" + h, x - 1, y - 1, () -> {
            Canvas g = new Canvas(w + 6, h + 8);
            shell(g, 1, 1, w, h);
            screen(g, 9, 19, w - 16, h - 52);
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 5; i++) {
                    g.P(1 + w - 40 + i * 2, 1 + 6 + j * 2, rgb(120, 118, 110));
                }
            }
            return g;
        });
    }

    // A small shell with a screen on its right part (the corner tracker).
    public static void miniDevice(float x, float y, int w, int h, int screenX) {
        blit("minidev|" + w + "|" + h + "|" + screenX, x - 1, y - 1, () -> {
            Canvas g = new Canvas(w + 6, h + 8);
            shell(g, 1, 1, w, h);
            screen(g, 1 + screenX, 1 + 5, w - screenX - 8, h - 11);
            return g;
        });
    }

    // The scanline running down a screen, painted live.
    public static void scan(float x, float y, int w, int h, float t) {
        float sy = (t * 30) % (h + 20) - 10;
        for (int k = 0; k < 3; k++) {
            if (sy + k >= 0 && sy + k < h) {
                Px.R(x, y + sy + k, w, 1, D_TXT, 0.05f * (3 - k));
            }
        }
    }

    // Segments: n of them, f lit.
    public static void segBar(float x, float y, int n, int f, int w) {
        for (int i = 0; i < n; i++) {
            boolean on = i < f;
            Px.R(x + i * (w + 1), y, w, 4, on ? D_TXT : D_GRID, 1);
            if (on) {
                Px.R(x + i * (w + 1), y, w, 1, rgb(240, 255, 246), 1);
            }
        }
    }

    // A capsule outline (the rewards sit in one).
    public static void capsule(float x, float y, int w, int h, int c) {
        float r = h / 2f;
        for (int j = 0; j < h; j++) {
            float dy = Math.abs(j - r + 0.5f);
            int cut = Math.round(r - (float) Math.sqrt(Math.max(0, r * r - dy * dy)));
            Px.P(x + cut, y + j, c, 1);
            Px.P(x + w - 1 - cut, y + j, c, 1);
        }
        Px.R(x + r, y, w - 2 * r, 1, c, 1);
        Px.R(x + r, y + h - 1, w - 2 * r, 1, c, 1);
        Px.R(x + Math.round(w * 0.42f), y + 1, 1, h - 2, c, 0.6f);
    }

    // A rubber button: lighter under the mouse, pushed in while pressed.
    public static void rubber(float x, float y, int w, int h, String label, int c, int cd, boolean hot, boolean pressed) {
        int dep = pressed ? 1 : 3;
        float yy = y + (pressed ? 2 : 0);
        roundRect(x - 1, y - 1, w + 2, h + 4, 4, D_OUT, 1);
        roundRect(x, yy + dep - 1, w, h, 4, cd, 1);
        roundRect(x, yy, w, h - 1, 4, hot ? mix(c, 0xFFFFFF, 0.12f) : c, 1);
        Px.R(x + 2, yy + 1, w - 4, 1, mix(c, 0xFFFFFF, 0.45f), 1);
        Px.R(x + 2, yy + h - 4, w - 4, 2, cd, 0.25f);
        Px.T(label, x + w / 2f + 2, yy + h / 2f - 3, cd, label.length() > 2 ? 8 : 10, true, 0, false, 1);
        Px.T(label, x + w / 2f + 1, yy + h / 2f - 4, rgb(255, 246, 236), label.length() > 2 ? 8 : 10, true, 0, false, 1);
    }

    // Rounded box painted live (few rows).
    public static void roundRect(float x, float y, int w, int h, float r, int rgb, float a) {
        for (int j = 0; j < h; j++) {
            float dy = j < r ? r - j - 0.5f : j >= h - r ? j - (h - r) + 0.5f : 0;
            int cut = dy > 0 ? Math.round(r - (float) Math.sqrt(Math.max(0, r * r - dy * dy))) : 0;
            Px.R(x + cut, y + j, w - 2 * cut, 1, rgb, a);
        }
    }

    // A blip on the radar with a ring going out; sel = a dashed ring around it.
    public static void radarBlip(float x, float y, int state, boolean sel, float t) {
        int col = RADAR_STATE[Math.max(0, Math.min(4, state))];
        float ph = (t * 1.4f + x * 0.01f) % 1;
        float r = 2 + ph * 7;
        for (int a = 0; a < 40; a++) {
            double an = a / 40.0 * Math.PI * 2;
            Px.P(Math.round(x + Math.cos(an) * r), Math.round(y + Math.sin(an) * r), col, 0.55f * (1 - ph));
        }
        Px.R(x - 2, y - 1, 5, 3, col, 1);
        Px.R(x - 1, y - 2, 3, 5, col, 1);
        Px.P(x, y, 0xFFFFFF, 1);
        if (sel) {
            for (int a = 0; a < 48; a++) {
                if (a % 4 < 2) {
                    double an = a / 48.0 * Math.PI * 2;
                    Px.P(Math.round(x + Math.cos(an) * 9), Math.round(y + Math.sin(an) * 9), col, 1);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Jujutsu

    // The dossier: folder back with a tab on top, the folder and the typed sheet at (x + 6, y + 14). Drawn at (x, y).
    public static void dossier(float x, float y, int w, int h) {
        blit("dossier|" + w + "|" + h, x, y, () -> {
            Canvas g = new Canvas(w + 6, h + 8);
            g.R(4, 6, w, h, 0, 0.4f);
            g.R(0, 8, w, h - 8, J_F2);
            g.R(8, 0, 70, 10, J_F2);
            g.R(9, 1, 68, 9, J_FOLDER);
            g.R(1, 9, w - 2, h - 10, J_FOLDER);
            for (int j = 0; j < h - 10; j++) {
                for (int i = 0; i < w - 2; i++) {
                    if (hash(i, j, 70) < 0.08f) {
                        g.P(1 + i, 9 + j, J_F2, 0.5f);
                    }
                }
            }
            sheet(g, 6, 14, w - 12, h - 22);
            return g;
        });
    }

    public static void sheet(Canvas g, int px, int py, int pw, int ph) {
        g.R(px + 1, py + 1, pw, ph, 0, 0.25f);
        for (int j = 0; j < ph; j++) {
            for (int i = 0; i < pw; i++) {
                g.P(px + i, py + j, hash(i, j, 71) < 0.06f ? J_P2 : J_PAPER);
            }
        }
    }

    // A loose sheet (the corner tracker, the talk's talisman base).
    public static void note(float x, float y, int w, int h) {
        blit("note|" + w + "|" + h, x, y, () -> {
            Canvas g = new Canvas(w + 2, h + 2);
            sheet(g, 0, 0, w, h);
            return g;
        });
    }

    // A paper clip over the top edge.
    public static void clip(float x, float y, int len) {
        for (int i = 0; i < len; i++) {
            Px.P(x, y + i, J_CLIP, 1);
            Px.P(x + 4, y + 2 + i, J_CLIP, 1);
        }
        Px.R(x, y - 1, 5, 1, J_CLIP, 1);
        Px.R(x + 1, y + len - 5, 3, 1, J_CLIP, 1);
        Px.P(x + 1, y, rgb(220, 224, 232), 1);
    }

    // ---------------------------------------------------------------- ink marks painted live

    // A brush stroke: swells in the middle, wobbles, goes dry at the end.
    public static void brush(float x, float y, int len, float th, int c, int seed, float a, float dry) {
        for (int i = 0; i < len; i++) {
            float t = i / (float) len;
            float w = th * (float) Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.15f + 0.04f)), 0.35) * (0.75f + 0.5f * vnoise(i * 0.3f, 0, seed));
            int wob = Math.round((vnoise(i * 0.12f, 3, seed) - 0.5f) * 1.6f);
            int hh = Math.max(1, Math.round(w));
            for (int k = 0; k < hh; k++) {
                if (t > dry && hash(i, k, seed) < (t - dry) * 2.4f) {
                    continue;
                }
                Px.P(x + i, y + wob + k - hh / 2, c, a);
            }
        }
    }

    // An ink ring that does not quite close (a checkbox drawn with a brush).
    public static void inkRing(float cx, float cy, float r, int c, int seed, float a) {
        int n = Math.round((float) Math.PI * 2 * r * 2.2f);
        for (int i = 0; i < n * 0.93f; i++) {
            double an = -2.2 + i / (double) n * Math.PI * 2;
            float rr = r + (vnoise(i * 0.2f, 1, seed) - 0.5f) * 0.9f;
            float px = cx + (float) Math.cos(an) * rr, py = cy + (float) Math.sin(an) * rr;
            Px.P(Math.round(px), Math.round(py), c, a);
            if (i / (float) n > 0.15f && i / (float) n < 0.55f) {
                Px.P(Math.round(px + 0.6f), Math.round(py + 0.4f), c, a * 0.8f);
            }
        }
    }

    // A check mark in two strokes.
    public static void check(float x, float y, int c, float a) {
        for (int k = 0; k < 4; k++) {
            Px.P(x + k, y + k, c, a);
        }
        for (int k = 0; k < 8; k++) {
            Px.P(x + 3 + k, y + 3 - k, c, a);
            Px.P(x + 4 + k, y + 3 - k, c, a * 0.6f);
        }
    }

    // A small paper slip with an ink mark in the state's color (an explosive tag), for Naruto's pins and legend.
    public static void paperTag(float x, float y, int state, int h) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < 5; i++) {
                if (j == h - 1 && (i == 0 || i == 4)) {
                    continue;
                }
                Px.P(x + i, y + j, mix(N_PAPER, N_PAPER2, hash(i, j, (int) x) * 0.6f), 1);
            }
        }
        int c = INK_STATE[Math.max(0, Math.min(4, state))];
        for (int j = 1; j < h - 1; j++) {
            Px.P(x + 2, y + j, c, 1);
            if (j > 1 && j < h - 2) {
                Px.P(x + (j % 3 == 0 ? 1 : 3), y + j, c, 0.75f);
            }
        }
        Px.R(x + 1, y + 1, 3, 1, c, 1);
    }

    // A kunai stuck in the map at (x, y) with a paper tag tied to its ring (the map pins of Naruto), drawn 2x.
    public static void kunaiPin(float x0, float y0, int state, boolean sel) {
        int blade = rgb(70, 76, 88), bl = rgb(150, 158, 172), grip = rgb(40, 30, 28);
        float s = 2;
        // in the 24x24 picture of the preview the tip is at (10, 20); drawn at 2x with that point on (x0, y0 + 0)
        float ox = x0 - 20, oy = y0 - 40;
        java.util.function.BiConsumer<float[], Integer> r = (b, col) -> Px.R(ox + b[0] * s, oy + b[1] * s, b[2] * s, b[3] * s, col, b.length > 4 ? b[4] : 1);
        int x = 10, y = 20;
        r.accept(new float[] {x - 1, y + 4, 3, 1, 0.35f}, 0);
        for (int i = 0; i < 6; i++) {
            int half = (5 - i) / 3;
            r.accept(new float[] {x - half, y - i, 1 + 2 * half, 1}, i < 2 ? bl : blade);
        }
        r.accept(new float[] {x, y - 1, 1, 1}, bl);
        r.accept(new float[] {x, y - 12, 1, 6}, grip);
        r.accept(new float[] {x - 1, y - 6, 3, 1}, rgb(60, 46, 40));
        for (int i = 0; i < 6; i += 2) {
            r.accept(new float[] {x, y - 11 + i, 1, 1}, rgb(90, 70, 60));
        }
        r.accept(new float[] {x - 1, y - 15, 3, 1}, blade);
        r.accept(new float[] {x - 1, y - 14, 1, 1}, blade);
        r.accept(new float[] {x + 1, y - 14, 1, 1}, blade);
        r.accept(new float[] {x - 1, y - 13, 3, 1}, blade);
        r.accept(new float[] {x + 2, y - 14, 1, 1}, N_RED);
        r.accept(new float[] {x + 3, y - 13, 1, 1}, N_RED);
        // the tag, 2x as well
        Px.g.pose().pushPose();
        Px.g.pose().translate(ox + (x + 3) * s, oy + (y - 13) * s, 0);
        Px.g.pose().scale(s, s, 1);
        paperTag(0, 0, state, sel ? 9 : 7);
        Px.g.pose().popPose();
    }

    // The state stamp (EM ANDAMENTO, NOVA...): a box of ink with the text, a bit crooked and worn.
    public static void stamp(String text, float x, float y, int col, boolean doubleLine, float a) {
        int w = Px.tw(text, 8, true) + 8, h = 13;
        Px.g.pose().pushPose();
        Px.g.pose().translate(x, y + 2, 0);
        Px.g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-4));
        Px.R(0, 0, w, 1, col, a);
        Px.R(0, h - 1, w, 1, col, a);
        Px.R(0, 0, 1, h, col, a);
        Px.R(w - 1, 0, 1, h, col, a);
        if (doubleLine) {
            Px.R(2, 2, w - 4, 1, col, a);
            Px.R(2, h - 3, w - 4, 1, col, a);
        }
        Px.T(text, 4, 3, col, 8, true, -1, false, a);
        Px.g.pose().popPose();
    }

    // A small red hanko seal.
    public static void hanko(float x, float y) {
        String[] pat = {".........", ".###.###.", ".#.#...#.", ".###.###.", ".....#...", ".#####.#.", "...#..##.", ".#.#.#.#.", "........."};
        Px.R(x, y, 9, 9, N_RED, 0.9f);
        for (int j = 0; j < 9; j++) {
            for (int i = 0; i < 9; i++) {
                if (pat[j].charAt(i) == '#' || hash(i, j, 4) < 0.12f) {
                    Px.P(x + i, y + j, N_PAPER, 1);
                }
            }
        }
    }
}
