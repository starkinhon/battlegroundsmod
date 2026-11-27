// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.SharinganMemory;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

// Kakashi's C, the approved page (forja/kakashi-kit, kakashi4.js, tab "C Cópia"): the Sharingan in close-up in the top
// right corner of his own screen, white of the eye, red iris, black rims and the three tomoe turning. It shows for a
// moment when the eye sees a skill (slow turn) and when he copies it (the tomoe spin fast, then slow down). The eye is
// drawn once into two textures (the eye, and the tomoe on their own so they can turn).
public final class KakashiEye {
    private static final int S = 128;
    private static final ResourceLocation EYE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/kakashi_eye");
    private static final ResourceLocation TOMOE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/kakashi_tomoe");
    private static final long SEEN_MS = 1800, COPY_MS = 2000;
    private static boolean built;
    private static long copyAt = -100000;
    private static long last;
    private static float spin, alpha;

    private KakashiEye() {
    }

    // He copied: the eye opens on his screen with the tomoe spinning fast.
    static void copied() {
        copyAt = Util.getMillis();
    }

    public static void clear() {
        copyAt = -100000;
        alpha = 0;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        float dt = Math.min(0.1f, (now - last) / 1000f);
        last = now;
        long sinceCopy = now - copyAt;
        long sinceSeen = now - SharinganMemory.clientSeenAt;
        boolean copying = sinceCopy >= 0 && sinceCopy < COPY_MS;
        boolean seeing = SharinganMemory.clientSeenAt > 0 && sinceSeen >= 0 && sinceSeen < SEEN_MS;
        // how open the eye should be: in quick, out over the last part of its time
        float want = 0;
        if (copying) {
            want = Math.max(want, Math.min(1, sinceCopy / 120f) * Math.min(1, (COPY_MS - sinceCopy) / 500f));
        }
        if (seeing) {
            want = Math.max(want, Math.min(1, sinceSeen / 200f) * Math.min(1, (SEEN_MS - sinceSeen) / 400f));
        }
        alpha += (want - alpha) * Math.min(1, dt * 12);
        // the tomoe: 14 rad/s while copying (the first 0.7 s), easing back to 2.2
        float speed = copying ? 2.2f + 11.8f * Math.max(0, 1 - Math.max(0, sinceCopy - 700) / 600f) : 2.2f;
        spin += dt * speed;
        if (alpha < 0.01f || mc.options.hideGui) {
            return;
        }
        if (!built) {
            build(mc);
        }
        float size = Math.max(48, graphics.guiHeight() * 0.19f);
        float cx = graphics.guiWidth() - 8 - size / 2;
        float cy = 8 + size / 2 + (mc.player != null && !mc.player.getActiveEffects().isEmpty() ? 52 : 0);
        float sc = size / S;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, Math.min(1, alpha));
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(sc, sc, 1);
        graphics.blit(EYE, -S / 2, -S / 2, 0, 0, S, S, S, S);
        graphics.pose().mulPose(Axis.ZP.rotation(spin));
        graphics.blit(TOMOE, -S / 2, -S / 2, 0, 0, S, S, S, S);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // ---------- the two pictures (the page's canvas: 220 px, eye radius 110, iris 82, ring 50, pupil 17, tomoe 11) ----------

    private static final float R = 57;          // the white of the eye, in texture pixels (radius)
    private static final float K = R / 110f;    // page pixels to texture pixels

    private static void build(Minecraft mc) {
        NativeImage eye = new NativeImage(S, S, true);
        NativeImage tomoe = new NativeImage(S, S, true);
        float[][] tail = tailShape();
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float[] e = new float[4];
                float[] t = new float[4];
                // 3x3 samples a pixel, so the edges are smooth
                for (int sy = 0; sy < 3; sy++) {
                    for (int sx = 0; sx < 3; sx++) {
                        float px = x + (sx + 0.5f) / 3 - S / 2f, py = y + (sy + 0.5f) / 3 - S / 2f;
                        add(e, eyeAt(px, py));
                        add(t, tomoeAt(px, py, tail));
                    }
                }
                eye.setPixelRGBA(x, y, pack(e, 9));
                tomoe.setPixelRGBA(x, y, pack(t, 9));
            }
        }
        mc.getTextureManager().register(EYE, new DynamicTexture(eye));
        mc.getTextureManager().register(TOMOE, new DynamicTexture(tomoe));
        built = true;
    }

    // premultiplied rgba of one sample of the eye
    private static float[] eyeAt(float x, float y) {
        float d = (float) Math.sqrt(x * x + y * y);
        float rim = R + 3.2f;
        if (d > rim) {
            // the soft shadow round the frame
            float a = Math.max(0, 1 - (d - rim) / 4.5f) * 0.45f;
            return new float[] {0, 0, 0, a};
        }
        if (d > R) {
            return rgba(0x0B0B0F, 1);
        }
        float p = d / K;   // page pixels from the middle
        if (p > 85) {
            return rgba(0xF1E6DF, 1);
        }
        if (p > 79) {
            return rgba(0x120708, 1);   // the iris' black rim (stroke 6)
        }
        if (Math.abs(p - 50) < 1.5f) {
            return mix(iris(p), rgba(0x120708, 1), 0.85f);   // the inner ring
        }
        if (p < 17) {
            return rgba(0x0D0607, 1);
        }
        return iris(p);
    }

    private static float[] iris(float p) {
        if (p <= 10) {
            return rgba(0xFF3B3B, 1);
        }
        float u = (p - 10) / 72f;
        return u < 0.75f ? mix(rgba(0xFF3B3B, 1), rgba(0xC3121A, 1), u / 0.75f) : mix(rgba(0xC3121A, 1), rgba(0x5A0408, 1), (u - 0.75f) / 0.25f);
    }

    private static float[] tomoeAt(float x, float y, float[][] tail) {
        float px = x / K, py = y / K;
        for (int i = 0; i < 3; i++) {
            double an = i * 2.0944;
            // into the tomoe's own frame: rotated by an, moved out 50
            float lx = (float) (px * Math.cos(an) + py * Math.sin(an)) - 50;
            float ly = (float) (-px * Math.sin(an) + py * Math.cos(an));
            if (lx * lx + ly * ly <= 121 || inside(tail, lx, ly)) {
                return rgba(0x0D0607, 1);
            }
        }
        return new float[4];
    }

    // The tail: (0,-11) curving by (22,-14) to (20,10), back by (14,-2) to (0,11), as on the page.
    private static float[][] tailShape() {
        int n = 16;
        float[][] pts = new float[n * 2][];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            pts[i] = quad(0, -11, 22, -14, 20, 10, t);
            pts[n + i] = quad(20, 10, 14, -2, 0, 11, t);
        }
        return pts;
    }

    private static float[] quad(float ax, float ay, float bx, float by, float cx, float cy, float t) {
        float u = 1 - t;
        return new float[] {u * u * ax + 2 * u * t * bx + t * t * cx, u * u * ay + 2 * u * t * by + t * t * cy};
    }

    private static boolean inside(float[][] poly, float x, float y) {
        boolean in = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            float xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    private static float[] rgba(int rgb, float a) {
        return new float[] {((rgb >> 16) & 255) / 255f * a, ((rgb >> 8) & 255) / 255f * a, (rgb & 255) / 255f * a, a};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, a[3] + (b[3] - a[3]) * t};
    }

    private static void add(float[] to, float[] c) {
        for (int i = 0; i < 4; i++) {
            to[i] += c[i];
        }
    }

    // averaged premultiplied samples to the image's ABGR (straight alpha)
    private static int pack(float[] c, int n) {
        float a = c[3] / n;
        if (a <= 0.002f) {
            return 0;
        }
        int r = Math.round(Math.min(1, c[0] / n / a) * 255), g = Math.round(Math.min(1, c[1] / n / a) * 255), b = Math.round(Math.min(1, c[2] / n / a) * 255);
        int al = Math.round(Math.min(1, a) * 255);
        return al << 24 | b << 16 | g << 8 | r;
    }
}
