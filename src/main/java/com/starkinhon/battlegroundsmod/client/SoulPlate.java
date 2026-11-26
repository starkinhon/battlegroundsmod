// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.client.ui.Art;
import com.starkinhon.battlegroundsmod.client.ui.Canvas;
import com.starkinhon.battlegroundsmod.client.ui.Px;

// The new hotbar's plate and glass (PR #503) on the pixel grid of the screens (client/ui/Px), for the Livro dos
// Desafios: a plate with cut corners in a soul's seven shades (painted once into a texture), the glass slot, the light
// running over the lit edge and the little stars. Same look as the hotbar and the skill column.
public final class SoulPlate {
    private static final Map<String, int[]> LIT = new HashMap<>();

    private SoulPlate() {
    }

    // The seven shades of a soul (power id); a hue for the ones with no soul (universes, locked bosses).
    public static int[] ramp(String power) {
        return strip(CrystalInventory.rampFor(power == null || power.isEmpty() ? "?" : power));
    }

    public static int[] ramp(float hue, float sat) {
        float k = sat / 0.55f;
        float toward = (hue > 200 && hue < 300) ? 1 : -1;
        return strip(new int[] {CrystalInventory.hsl(hue + 14 * toward, Math.min(1, 0.45f * k), 0.07f),
                CrystalInventory.hsl(hue + 10 * toward, Math.min(1, 0.5f * k), 0.16f), CrystalInventory.hsl(hue + 6 * toward, Math.min(1, 0.5f * k), 0.27f),
                CrystalInventory.hsl(hue, Math.min(1, 0.5f * k), 0.40f), CrystalInventory.hsl(hue - 6 * toward, Math.min(1, 0.55f * k), 0.56f),
                CrystalInventory.hsl(hue - 12 * toward, Math.min(1, 0.65f * k), 0.73f), CrystalInventory.hsl(hue - 18 * toward, Math.min(1, 0.7f * k), 0.89f)});
    }

    // The ramp of an RGB color (a universe's color): its hue, a calm saturation.
    public static int[] rampOf(int rgb, float sat) {
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), d = mx - mn;
        float h = 0;
        if (d > 0) {
            h = mx == r ? ((g - b) / d) % 6 : mx == g ? (b - r) / d + 2 : (r - g) / d + 4;
            h = (h * 60 + 360) % 360;
        }
        return ramp(h, d < 0.08f ? 0.05f : sat);
    }

    // Gray shades (locked boss: nothing of its color shows).
    public static int[] gray() {
        return ramp(230, 0.04f);
    }

    private static int[] strip(int[] r) {
        int[] o = new int[r.length];
        for (int i = 0; i < r.length; i++) {
            o[i] = r[i] & 0xFFFFFF;
        }
        return o;
    }

    // The ramp a bit lit (under the mouse, the selected one).
    public static int[] lit(int[] r, float k) {
        int[] o = r.clone();
        for (int i = 0; i < 4; i++) {
            o[i] = Px.mix(r[i], r[Math.min(6, i + 2)], k);
        }
        return o;
    }

    // The plate at (x, y); returns its lit edge pixels on the grid, for the light and the stars.
    public static List<int[]> plate(float x, float y, int w, int h, int cut, int[] r) {
        String key = "plate|" + w + "|" + h + "|" + cut + "|" + java.util.Arrays.toString(r);
        Art.blit(key, x, y, () -> {
            int[] buf = new int[w * h];
            List<Integer> lit = new ArrayList<>(), spots = new ArrayList<>();
            CrystalInventory.plate(buf, w, 0, 0, w, h, cut, r, 0, false, false, lit, spots);
            Canvas c = new Canvas(w, h);
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int p = buf[j * w + i];
                    if ((p >>> 24) != 0) {
                        c.P(i, j, p & 0xFFFFFF);
                    }
                }
            }
            int[] packed = new int[lit.size()];
            for (int i = 0; i < packed.length; i++) {
                packed[i] = lit.get(i);
            }
            LIT.put(key, packed);
            return c;
        });
        int[] packed = LIT.getOrDefault(key, new int[0]);
        List<int[]> out = new ArrayList<>(packed.length);
        for (int p : packed) {
            out.add(new int[] {Math.round(x) + (short) (p & 0xFFFF), Math.round(y) + (p >> 16)});
        }
        return out;
    }

    // The glass slot of inner size s: halo, dark edge, glass with a light top/left and a shaded bottom/right.
    public static void glass(float x, float y, float s, int[] r) {
        glass(x, y, s, s, r);
    }

    public static void glass(float x, float y, float w, float h, int[] r) {
        Px.R(x - 2, y - 2, w + 4, h + 4, r[4], 0.55f);
        Px.R(x - 1, y - 1, w + 2, h + 2, r[0], 0.95f);
        Px.R(x, y, w, h, Px.mix(r[1], r[0], 0.45f), 0.94f);
        Px.R(x, y, w, 1, r[4], 0.5f);
        Px.R(x, y, 1, h, r[4], 0.35f);
        Px.R(x, y + h - 1, w, 1, 0x000000, 0.25f);
        Px.R(x + w - 1, y, 1, h, 0x000000, 0.25f);
    }

    // A thin bright outline around a box (the selected card).
    public static void outline(float x, float y, float w, float h, int rgb) {
        Px.R(x, y, w, 1, rgb, 1);
        Px.R(x, y + h - 1, w, 1, rgb, 1);
        Px.R(x, y, 1, h, rgb, 1);
        Px.R(x + w - 1, y, 1, h, rgb, 1);
    }

    // Three diagonal stripes of light running over the lit edge.
    public static void sweep(List<int[]> lit, float t, int rgb, float a) {
        float pos = ((t * 70) % 600) - 100;
        for (int k = 0; k < 3; k++) {
            float al = (k == 1 ? 0.55f : 0.25f) * a, lo = pos + k * 5, hi = lo + 5;
            for (int[] p : lit) {
                float d = p[0] * 0.5f + p[1];
                if (d >= lo && d < hi) {
                    Px.P(p[0], p[1], rgb, al);
                }
            }
        }
    }

    // Little stars blinking on the lit edge.
    public static void twinkle(List<int[]> lit, float t, int seed, int[] r, int n) {
        if (lit.isEmpty()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float r1 = Canvas.hash(i, 1, seed), r2 = Canvas.hash(i, 2, seed), r3 = Canvas.hash(i, 3, seed);
            int[] p = lit.get(Math.min(lit.size() - 1, (int) (r1 * lit.size())));
            float s = (float) Math.sin(t * (0.6f + r2 * 1.4f) * 2 + r3 * 6.28f);
            if (s < 0.2f) {
                continue;
            }
            float a = (s - 0.2f) / 0.8f;
            Px.P(p[0], p[1], 0xFFFFFF, a);
            Px.P(p[0] + 1, p[1], r[6], a * 0.9f);
            Px.P(p[0] - 1, p[1], r[6], a * 0.9f);
            Px.P(p[0], p[1] + 1, r[6], a * 0.9f);
            Px.P(p[0], p[1] - 1, r[6], a * 0.9f);
        }
    }

    // A soft breathing glow around a box (dithered, fades over pad pixels). Painted once into a texture per size and
    // color, and drawn with the alpha as a shader color: a fill per pixel made thousands of draw calls a frame (each
    // GuiGraphics.fill outside drawManaged is its own draw), and the Livro das Almas fell to 5 FPS (Stark 2026-11-26).
    public static void glow(float x, float y, int w, int h, int pad, int rgb, float a) {
        if (a <= 0.004f || pad <= 0) {
            return;
        }
        int cw = w + pad * 2, ch = h + pad * 2;
        String key = "glow|" + w + "|" + h + "|" + pad + "|" + rgb;
        float[] was = com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
        Px.g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(was[0], was[1], was[2], was[3] * Math.min(1, a));
        Art.blit(key, x - pad, y - pad, () -> {
            Canvas c = new Canvas(cw, ch);
            for (int j = -pad; j < h + pad; j++) {
                for (int i = -pad; i < w + pad; i++) {
                    int dx = Math.max(0, Math.max(-i, i - w + 1)), dy = Math.max(0, Math.max(-j, j - h + 1));
                    float d = (float) Math.hypot(dx, dy);
                    if (d > 0 && d < pad && Canvas.bay(i, j) < 0.75f) {
                        c.P(i + pad, j + pad, rgb, (float) Math.pow(1 - d / pad, 1.5));
                    }
                }
            }
            return c;
        });
        Px.g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(was[0], was[1], was[2], was[3]);
    }
}
