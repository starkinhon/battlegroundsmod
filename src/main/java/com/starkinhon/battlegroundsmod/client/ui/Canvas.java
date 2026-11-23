// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.ui;

import com.mojang.blaze3d.platform.NativeImage;

// A small picture painted pixel by pixel in memory (the web preview's R and P, with alpha blending), turned into a
// texture once and then drawn with one blit (client/ui/Art). Used for the paper, wood, plastic and folders of the
// universe themed screens, which would cost thousands of fills a frame if painted live.
public final class Canvas {
    public final int w, h;
    final int[] px;

    public Canvas(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    // Paints a box of rgb with alpha a over what is there.
    public void R(float x, float y, float w, float h, int rgb, float a) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        for (int j = Math.max(0, y0); j < Math.min(this.h, y1); j++) {
            for (int i = Math.max(0, x0); i < Math.min(this.w, x1); i++) {
                blend(i, j, rgb, a);
            }
        }
    }

    public void R(float x, float y, float w, float h, int rgb) {
        R(x, y, w, h, rgb, 1);
    }

    public void P(float x, float y, int rgb, float a) {
        int i = Math.round(x), j = Math.round(y);
        if (i >= 0 && j >= 0 && i < w && j < h) {
            blend(i, j, rgb, a);
        }
    }

    public void P(float x, float y, int rgb) {
        P(x, y, rgb, 1);
    }

    private void blend(int i, int j, int rgb, float a) {
        if (a <= 0) {
            return;
        }
        a = Math.min(1, a);
        int k = j * w + i;
        int d = px[k];
        float da = (d >>> 24) / 255f;
        float oa = a + da * (1 - a);
        if (oa <= 0) {
            return;
        }
        int r = Math.round((((rgb >> 16) & 0xFF) * a + ((d >> 16) & 0xFF) * da * (1 - a)) / oa);
        int g = Math.round((((rgb >> 8) & 0xFF) * a + ((d >> 8) & 0xFF) * da * (1 - a)) / oa);
        int b = Math.round(((rgb & 0xFF) * a + (d & 0xFF) * da * (1 - a)) / oa);
        px[k] = Math.round(oa * 255) << 24 | r << 16 | g << 8 | b;
    }

    // A box with rounded corners of radius r (the preview's roundRect).
    public void roundRect(float x, float y, int w, int h, float r, int rgb, float a) {
        for (int j = 0; j < h; j++) {
            float dy = j < r ? r - j - 0.5f : j >= h - r ? j - (h - r) + 0.5f : 0;
            int cut = dy > 0 ? Math.round(r - (float) Math.sqrt(Math.max(0, r * r - dy * dy))) : 0;
            R(x + cut, y + j, w - 2 * cut, 1, rgb, a);
        }
    }

    public NativeImage image() {
        NativeImage image = new NativeImage(w, h, true);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = px[j * w + i];
                // NativeImage wants ABGR
                image.setPixelRGBA(i, j, (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16);
            }
        }
        return image;
    }

    // ---------------------------------------------------------------- noise (same family as the preview)

    public static float hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    public static float vnoise(float x, float y, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
        float a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    private static final int[] BAY = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    // 4x4 ordered dither threshold in 0..1.
    public static float bay(int x, int y) {
        return (BAY[(y & 3) * 4 + (x & 3)] + 0.5f) / 16f;
    }
}
