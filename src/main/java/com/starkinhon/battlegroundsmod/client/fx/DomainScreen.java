package com.starkinhon.battlegroundsmod.client.fx;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;

// What Gojo's domain puts on the screen itself (DomainScene.renderGui): the white swallowing the view, the black ink
// tearing it open into the tunnel, and, for those caught only, the infinite information: threads of light and letters
// rushing into the mind, and flashes of nature and senses (a spirograph mandala, bubbles, mirrored branches) coming
// faster and faster. All drawn from the time, no state.
final class DomainScreen {
    private static final String GLYPHS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#%&$@?!+=<>/*";

    private DomainScreen() {
    }

    // The white light: the whole view goes white, soft clouds of it closing in from the edges first.
    static void whiteout(GuiGraphics graphics, float a, double t) {
        if (a <= 0.002f) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        graphics.fill(0, 0, w, h, argb(a, 0xFFFFFF));
        if (a < 1) {
            graphics.flush();
            BufferBuilder b = begin();
            Matrix4f m = graphics.pose().last().pose();
            for (int k = 0; k < 14; k++) {
                float ang = FxWorld.hash(1101, k, 1) * Mth.TWO_PI;
                float cx = w / 2f + Mth.cos(ang) * w * 0.6f;
                float cy = h / 2f + Mth.sin(ang) * h * 0.6f;
                float r = Math.max(w, h) * (0.25f + 0.6f * a) * (0.7f + 0.5f * FxWorld.hash(1101, k, 2));
                blob(b, m, cx, cy, r, 0.18f, 1101 + k, (float) t * 0.02f, argb(Math.min(1, a * 1.6f), 0xFFFFFF), argb(0, 0xFFFFFF));
            }
            end(b);
        }
    }

    // From white into the tunnel: a ragged blot of black ink spreads out of the middle over the white while streaks of
    // violet, magenta and red shoot out of it; then the black thins away and the tunnel is there.
    static void inkOpening(GuiGraphics graphics, float k, double t) {
        k = Mth.clamp(k, 0, 1);
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        float diag = (float) Math.hypot(w, h);
        graphics.flush();
        BufferBuilder b = begin();
        Matrix4f m = graphics.pose().last().pose();
        if (k < 0.5f) {
            quad(b, m, 0, 0, w, h, argb(1, 0xFFFFFF));
            float r = diag * 0.62f * (float) Math.pow(k / 0.5f, 1.3);
            blob(b, m, w / 2f, h / 2f, r, 0.3f, 1201, (float) t * 0.05f, argb(1, 0x050008), argb(1, 0x050008));
            for (int n = 0; n < 9; n++) {
                float ang = FxWorld.hash(1202, n, 1) * Mth.TWO_PI;
                float d = r * (0.7f + 0.5f * FxWorld.hash(1202, n, 2));
                blob(b, m, w / 2f + Mth.cos(ang) * d, h / 2f + Mth.sin(ang) * d, r * (0.12f + 0.15f * FxWorld.hash(1202, n, 3)), 0.35f, 1210 + n, 0,
                        argb(1, 0x050008), argb(1, 0x050008));
            }
        } else {
            quad(b, m, 0, 0, w, h, argb(1 - (k - 0.5f) / 0.5f, 0x050008));
        }
        int[] colors = {0xA040FF, 0xFF3CDC, 0xFF2058, 0x4C5CFF, 0xFFD6FF};
        for (int n = 0; n < 70; n++) {
            float ang = FxWorld.hash(1203, n, 1) * Mth.TWO_PI;
            float from = diag * (0.02f + 0.1f * FxWorld.hash(1203, n, 2));
            float to = from + diag * k * (0.4f + 0.8f * FxWorld.hash(1203, n, 3));
            float alpha = (1 - k) * 0.95f;
            int c = colors[n % colors.length];
            line(b, m, w / 2f + Mth.cos(ang) * from, h / 2f + Mth.sin(ang) * from, w / 2f + Mth.cos(ang) * to, h / 2f + Mth.sin(ang) * to,
                    1 + 2.5f * FxWorld.hash(1203, n, 4), argb(alpha * 0.2f, c), argb(alpha, c));
        }
        end(b);
    }

    // The white tearing open into the tunnel (k from 0 to 1): dark holes with streaks of light open in it, from the
    // middle out, and it breaks into round puffs of white that shrink and fly off the screen, showing the tunnel behind.
    static void whiteBurst(GuiGraphics graphics, float k, double t) {
        k = Mth.clamp(k, 0, 1);
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float diag = (float) Math.hypot(w, h);
        graphics.flush();
        BufferBuilder b = begin();
        Matrix4f m = graphics.pose().last().pose();
        int cols = 9;
        int rows = 6;
        float cell = Math.max(w / (float) cols, h / (float) rows);
        for (int i = -1; i <= cols; i++) {
            for (int j = -1; j <= rows; j++) {
                int id = (i + 2) * 31 + j + 2;
                float x = (i + 0.5f) * w / cols + (FxWorld.hash(1301, id, 1) - 0.5f) * cell * 0.6f;
                float y = (j + 0.5f) * h / rows + (FxWorld.hash(1301, id, 2) - 0.5f) * cell * 0.6f;
                float from = (float) Math.hypot(x - cx, y - cy) / (diag * 0.5f);
                // The middle tears first; each puff then shrinks and is flung outwards.
                float start = 0.05f + 0.4f * from + 0.12f * FxWorld.hash(1301, id, 3);
                float u = Mth.clamp((k - start) / 0.45f, 0, 1);
                if (u >= 1) {
                    continue;
                }
                float ease = u * u;
                float dx = x - cx;
                float dy = y - cy;
                float len = Math.max(1, (float) Math.hypot(dx, dy));
                float fling = diag * 0.35f * ease;
                float r = cell * (0.85f + 0.3f * FxWorld.hash(1301, id, 4)) * (1 - ease);
                blob(b, m, x + dx / len * fling, y + dy / len * fling, r, 0.18f, 1310 + id, (float) t * 0.2f, argb(1, 0xFFFFFF), argb(1, 0xFFFFFF));
            }
        }
        // Torn edges: small drops of white thrown off.
        for (int n = 0; n < 60; n++) {
            float ang = FxWorld.hash(1302, n, 1) * Mth.TWO_PI;
            float start = 0.1f + 0.5f * FxWorld.hash(1302, n, 2);
            float u = Mth.clamp((k - start) / 0.4f, 0, 1);
            if (u <= 0 || u >= 1) {
                continue;
            }
            float d = diag * (0.1f + 0.6f * u) * (0.5f + 0.5f * FxWorld.hash(1302, n, 3));
            float r = diag * 0.012f * (1 - u) * (0.5f + FxWorld.hash(1302, n, 4));
            blob(b, m, cx + Mth.cos(ang) * d, cy + Mth.sin(ang) * d, r, 0.1f, 1400 + n, 0, argb(1, 0xFFFFFF), argb(1, 0xFFFFFF));
        }
        end(b);
    }

    // Those caught: the mind flooded. u is how far through it they are (0 to 1); everything comes faster with it.
    static void information(GuiGraphics graphics, double t, float u, float strength, boolean inVoid, int seed) {
        if (strength <= 0) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float reach = Math.max(w, h) * 0.75f;
        float s = Math.min(1, strength);

        scenes(graphics, t, u, s, seed);

        graphics.flush();
        BufferBuilder b = begin();
        Matrix4f m = graphics.pose().last().pose();
        // Threads of light rushing in from every side into the middle of the view.
        int threads = 30 + (int) (40 * u);
        for (int k = 0; k < threads; k++) {
            float life = 5 + 5 * FxWorld.hash(1301, k, 1);
            double cycle = (t + FxWorld.hash(1301, k, 2) * life) / life;
            int round = (int) Math.floor(cycle);
            float p = (float) (cycle - round);
            float ang = FxWorld.hash(1301 + round, k, 3) * Mth.TWO_PI;
            float r0 = reach * (1 - p);
            float r1 = Math.max(0, r0 - reach * 0.25f);
            float a = s * 0.6f * Mth.sin(p * Mth.PI);
            int c = k % 3 == 0 ? 0x9FE8FF : 0xFFFFFF;
            line(b, m, cx + Mth.cos(ang) * r0, cy + Mth.sin(ang) * r0, cx + Mth.cos(ang) * r1, cy + Mth.sin(ang) * r1, 1.2f, argb(0, c), argb(a, c));
        }
        // Lines of data flickering across, new ones every tick.
        int tick = (int) t;
        for (int k = 0; k < 30; k++) {
            float y = FxWorld.hash(tick, k, 1) * h;
            float x = FxWorld.hash(tick, k, 2) * w;
            float len = (0.05f + 0.3f * FxWorld.hash(tick, k, 3)) * w;
            quad(b, m, x, y, Math.min(w, x + len), y + 1, argb(s * (0.06f + 0.18f * FxWorld.hash(tick, k, 4)), k % 3 == 0 ? 0x9FE8FF : 0xFFFFFF));
        }
        end(b);

        // Letters and numbers in Minecraft's font flying into the mind, smaller as they sink in.
        Font font = Minecraft.getInstance().font;
        int count = 40 + (int) (70 * u);
        for (int k = 0; k < count; k++) {
            float life = 6 + 6 * FxWorld.hash(1401, k, 1);
            double cycle = (t + FxWorld.hash(1401, k, 2) * life) / life;
            int round = (int) Math.floor(cycle);
            float p = (float) (cycle - round);
            float ang = FxWorld.hash(1401 + round, k, 3) * Mth.TWO_PI;
            float r = reach * (1 - p) * (0.4f + 0.6f * FxWorld.hash(1401 + round, k, 4));
            float scale = 0.6f + 2.2f * (1 - p);
            int alpha = (int) (255 * s * Math.min(1, p * 5) * (1 - p * 0.6f));
            if (alpha < 10) {
                continue;
            }
            String text = String.valueOf(GLYPHS.charAt((int) (FxWorld.hash(1401 + round, k, 5) * GLYPHS.length()) % GLYPHS.length()));
            graphics.pose().pushPose();
            graphics.pose().translate(cx + Mth.cos(ang) * r, cy + Mth.sin(ang) * r, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, -font.width(text) / 2, -4, alpha << 24 | (k % 4 == 0 ? 0x9FE8FF : 0xFFFFFF), false);
            graphics.pose().popPose();
        }
        // A slow white pulse, the mind overloading.
        float pulse = (float) Math.pow(Math.max(0, Mth.sin((float) t * 0.12f)), 8);
        if (inVoid && pulse > 0.01f) {
            graphics.fill(0, 0, w, h, argb(s * 0.18f * pulse, 0xFFFFFF));
        }
    }

    // Flashes of nature and senses, each for a moment, coming faster and faster.
    private static void scenes(GuiGraphics graphics, double t, float u, float s, int seed) {
        double period = Mth.lerp(u, 46, 7);
        double phase = (t + 13) / period;
        int index = (int) Math.floor(phase);
        double into = (phase - index) * period;
        double show = Math.min(4, period * 0.55);
        if (into > show || t < 30) {
            return;
        }
        float a = s * 0.9f * (float) Math.min(1, Math.min(into / 0.6, (show - into) / 0.8));
        if (a <= 0.01f) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        graphics.flush();
        BufferBuilder b = begin();
        Matrix4f m = graphics.pose().last().pose();
        int kind = (int) (FxWorld.hash(seed + index, 1, 1) * 3);
        int id = seed * 7 + index;
        if (kind == 0) {
            mandala(b, m, w, h, t, a, id);
        } else if (kind == 1) {
            bubbles(b, m, w, h, t, a, id);
        } else {
            branches(b, m, w, h, a, id);
        }
        end(b);
    }

    // A cyan spirograph on black with a black heart (ref4).
    private static void mandala(BufferBuilder b, Matrix4f m, int w, int h, double t, float a, int id) {
        quad(b, m, 0, 0, w, h, argb(a * 0.9f, 0x020406));
        float cx = w / 2f;
        float cy = h / 2f;
        float size = h * 0.44f;
        for (int curve = 0; curve < 2; curve++) {
            float rr = 0.18f + 0.3f * FxWorld.hash(id, curve, 1);
            float dd = 0.35f + 0.6f * FxWorld.hash(id, curve, 2);
            float spin = (float) t * 0.04f + curve * 0.4f;
            int c = curve == 0 ? 0x7FE8FF : 0xE8FFFF;
            float px = 0, py = 0;
            int steps = 1100;
            for (int i = 0; i <= steps; i++) {
                float th = i / (float) steps * Mth.TWO_PI * 12;
                float x = (1 - rr) * Mth.cos(th) + dd * rr * Mth.cos((1 - rr) / rr * th);
                float y = (1 - rr) * Mth.sin(th) - dd * rr * Mth.sin((1 - rr) / rr * th);
                float sx = cx + (x * Mth.cos(spin) - y * Mth.sin(spin)) * size;
                float sy = cy + (x * Mth.sin(spin) + y * Mth.cos(spin)) * size;
                if (i > 0) {
                    line(b, m, px, py, sx, sy, 1.1f, argb(a * (curve == 0 ? 0.8f : 0.35f), c), argb(a * (curve == 0 ? 0.8f : 0.35f), c));
                }
                px = sx;
                py = sy;
            }
        }
        blob(b, m, cx, cy, h * 0.07f, 0, id, 0, argb(a, 0x000000), argb(a, 0x000000));
        ring(b, m, cx, cy, h * 0.075f, 1.5f, argb(a * 0.8f, 0x9FF0FF));
    }

    // Bubbles rising in dark water.
    private static void bubbles(BufferBuilder b, Matrix4f m, int w, int h, double t, float a, int id) {
        quad(b, m, 0, 0, w, h, argb(a * 0.88f, 0x050909));
        for (int k = 0; k < 70; k++) {
            float r = h * (0.01f + 0.09f * (float) Math.pow(FxWorld.hash(id, k, 1), 2.2));
            float x = FxWorld.hash(id, k, 2) * w;
            float y = (float) ((FxWorld.hash(id, k, 3) * h - t * (0.5 + 4 * r / h)) % h);
            if (y < 0) {
                y += h;
            }
            blob(b, m, x, y, r, 0, id + k, 0, argb(a * 0.15f, 0x9FB8B8), argb(a * 0.05f, 0x9FB8B8));
            ring(b, m, x, y, r, Math.max(1, r * 0.12f), argb(a * 0.7f, 0xC8DADA));
            blob(b, m, x - r * 0.35f, y - r * 0.35f, r * 0.18f, 0, id + k, 0, argb(a * 0.8f, 0xFFFFFF), argb(0, 0xFFFFFF));
        }
    }

    // Branches of a tree, mirrored four ways, like the senses seen through glass.
    private static void branches(BufferBuilder b, Matrix4f m, int w, int h, float a, int id) {
        quad(b, m, 0, 0, w, h, argb(a * 0.85f, 0xE6E2DA));
        float cx = w / 2f;
        float cy = h / 2f;
        for (int mirror = 0; mirror < 4; mirror++) {
            float sx = mirror % 2 == 0 ? 1 : -1;
            float sy = mirror < 2 ? 1 : -1;
            branch(b, m, cx, cy, sx, sy, 0, 0, -Mth.HALF_PI + 0.5f, h * 0.2f, 7, Math.max(1.5f, h * 0.012f), a, id);
        }
    }

    private static void branch(BufferBuilder b, Matrix4f m, float cx, float cy, float sx, float sy, float x, float y, float ang, float len, int depth,
            float width, float a, int id) {
        if (depth <= 0 || len < 2) {
            return;
        }
        float x2 = x + Mth.cos(ang) * len;
        float y2 = y + Mth.sin(ang) * len;
        line(b, m, cx + x * sx, cy + y * sy, cx + x2 * sx, cy + y2 * sy, width, argb(a, 0x101010), argb(a, 0x101010));
        int salt = depth * 31 + (int) (ang * 100);
        float spread = 0.35f + 0.35f * FxWorld.hash(id, salt, 1);
        branch(b, m, cx, cy, sx, sy, x2, y2, ang - spread, len * (0.62f + 0.15f * FxWorld.hash(id, salt, 2)), depth - 1, width * 0.7f, a, id);
        branch(b, m, cx, cy, sx, sy, x2, y2, ang + spread, len * (0.62f + 0.15f * FxWorld.hash(id, salt, 3)), depth - 1, width * 0.7f, a, id);
    }

    // ---- drawing helpers: colored triangles in GUI space -------------------------------------------------------------

    private static BufferBuilder begin() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void end(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, int color) {
        b.addVertex(m, x0, y0, 0).setColor(color);
        b.addVertex(m, x0, y1, 0).setColor(color);
        b.addVertex(m, x1, y1, 0).setColor(color);
        b.addVertex(m, x0, y0, 0).setColor(color);
        b.addVertex(m, x1, y1, 0).setColor(color);
        b.addVertex(m, x1, y0, 0).setColor(color);
    }

    private static void line(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float width, int c0, int c1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-3f) {
            return;
        }
        float nx = -dy / len * width / 2;
        float ny = dx / len * width / 2;
        b.addVertex(m, x0 - nx, y0 - ny, 0).setColor(c0);
        b.addVertex(m, x0 + nx, y0 + ny, 0).setColor(c0);
        b.addVertex(m, x1 + nx, y1 + ny, 0).setColor(c1);
        b.addVertex(m, x0 - nx, y0 - ny, 0).setColor(c0);
        b.addVertex(m, x1 + nx, y1 + ny, 0).setColor(c1);
        b.addVertex(m, x1 - nx, y1 - ny, 0).setColor(c1);
    }

    // A filled circle (ragged by rough, 0 = round), inner color in the middle and outer at the edge.
    private static void blob(BufferBuilder b, Matrix4f m, float cx, float cy, float r, float rough, int seed, float turn, int inner, int outer) {
        int seg = 48;
        float px = 0, py = 0;
        for (int i = 0; i <= seg; i++) {
            float ang = i / (float) seg * Mth.TWO_PI;
            int j = i % seg;
            float wob = 1 + rough * (FxWorld.hash(seed, j, 7) - 0.5f) * 2 * (0.6f + 0.4f * Mth.sin(turn + j));
            float x = cx + Mth.cos(ang) * r * wob;
            float y = cy + Mth.sin(ang) * r * wob;
            if (i > 0) {
                b.addVertex(m, cx, cy, 0).setColor(inner);
                b.addVertex(m, px, py, 0).setColor(outer);
                b.addVertex(m, x, y, 0).setColor(outer);
            }
            px = x;
            py = y;
        }
    }

    private static void ring(BufferBuilder b, Matrix4f m, float cx, float cy, float r, float width, int color) {
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            float a0 = i / (float) seg * Mth.TWO_PI;
            float a1 = (i + 1) / (float) seg * Mth.TWO_PI;
            float r0 = r - width / 2;
            float r1 = r + width / 2;
            b.addVertex(m, cx + Mth.cos(a0) * r0, cy + Mth.sin(a0) * r0, 0).setColor(color);
            b.addVertex(m, cx + Mth.cos(a0) * r1, cy + Mth.sin(a0) * r1, 0).setColor(color);
            b.addVertex(m, cx + Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, 0).setColor(color);
            b.addVertex(m, cx + Mth.cos(a0) * r0, cy + Mth.sin(a0) * r0, 0).setColor(color);
            b.addVertex(m, cx + Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, 0).setColor(color);
            b.addVertex(m, cx + Mth.cos(a1) * r0, cy + Mth.sin(a1) * r0, 0).setColor(color);
        }
    }

    private static int argb(float a, int rgb) {
        return (int) (Mth.clamp(a, 0, 1) * 255) << 24 | (rgb & 0xFFFFFF);
    }
}
