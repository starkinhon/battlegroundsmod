package com.starkinhon.battlegroundsmod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// Small drawing helpers shared by the skill HUD and the constellation screen: free quads and lines (GuiGraphics only
// has axis aligned boxes), soft glows, framed boxes and scaled text. Colors are ARGB.
public final class GuiDraw {
    private static final ResourceLocation SOFT = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/soft.png");

    private GuiDraw() {
    }

    public static int argb(int alpha, int rgb) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int alpha(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        return argb(Math.round(a * Mth.clamp(factor, 0, 1)), color);
    }

    // Mixes two RGB colors (t = 0 gives a, 1 gives b).
    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    // A quad with one color per corner, in any winding (GUI quads must be in the same order as GuiGraphics.fill).
    public static void quad(GuiGraphics graphics, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                            int c1, int c2, int c3, int c4) {
        float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        if (area <= 0) {
            buffer.addVertex(pose, x1, y1, 0).setColor(c1);
            buffer.addVertex(pose, x2, y2, 0).setColor(c2);
            buffer.addVertex(pose, x3, y3, 0).setColor(c3);
            buffer.addVertex(pose, x4, y4, 0).setColor(c4);
        } else {
            buffer.addVertex(pose, x4, y4, 0).setColor(c4);
            buffer.addVertex(pose, x3, y3, 0).setColor(c3);
            buffer.addVertex(pose, x2, y2, 0).setColor(c2);
            buffer.addVertex(pose, x1, y1, 0).setColor(c1);
        }
    }

    // A line of the given width with soft edges: a solid core and a halo that fades out on both sides.
    public static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2, float width, float halo, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 0.01f) {
            return;
        }
        float nx = -dy / length;
        float ny = dx / length;
        float w = width / 2;
        quad(graphics, x1 + nx * w, y1 + ny * w, x2 + nx * w, y2 + ny * w, x2 - nx * w, y2 - ny * w, x1 - nx * w, y1 - ny * w,
                color, color, color, color);
        if (halo > 0) {
            int clear = color & 0xFFFFFF;
            int soft = alpha(color, 0.35f);
            float h = w + halo;
            quad(graphics, x1 + nx * w, y1 + ny * w, x2 + nx * w, y2 + ny * w, x2 + nx * h, y2 + ny * h, x1 + nx * h, y1 + ny * h,
                    soft, soft, clear, clear);
            quad(graphics, x1 - nx * w, y1 - ny * w, x2 - nx * w, y2 - ny * w, x2 - nx * h, y2 - ny * h, x1 - nx * h, y1 - ny * h,
                    soft, soft, clear, clear);
        }
    }

    // A four pointed star (two thin diamonds crossed), turned by angle.
    public static void star(GuiGraphics graphics, float x, float y, float size, float thickness, float angle, int color) {
        for (int arm = 0; arm < 2; arm++) {
            float a = angle + arm * Mth.HALF_PI;
            float cx = Mth.cos(a);
            float sy = Mth.sin(a);
            float px = -sy;
            float py = cx;
            int clear = color & 0xFFFFFF;
            quad(graphics, x + cx * size, y + sy * size, x + px * thickness, y + py * thickness,
                    x - cx * size, y - sy * size, x - px * thickness, y - py * thickness, clear, color, clear, color);
        }
    }

    // A box with float corners, batched (it shows on the next flush, unlike GuiGraphics.fill).
    public static void rect(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color) {
        quad(graphics, x1, y1, x1, y2, x2, y2, x2, y1, color, color, color, color);
    }

    public static void diamond(GuiGraphics graphics, float x, float y, float radius, int color) {
        quad(graphics, x, y - radius, x + radius, y, x, y + radius, x - radius, y, color, color, color, color);
    }

    // Additive colored quads (light that adds up instead of covering): begin, add quads with additiveQuad, end.
    public static BufferBuilder beginAdditive(GuiGraphics graphics) {
        graphics.flush();
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void additiveQuad(GuiGraphics graphics, BufferBuilder buffer, float x1, float y1, float x2, float y2, float x3, float y3,
                                    float x4, float y4, int c1, int c2, int c3, int c4) {
        Matrix4f pose = graphics.pose().last().pose();
        buffer.addVertex(pose, x1, y1, 0).setColor(c1);
        buffer.addVertex(pose, x2, y2, 0).setColor(c2);
        buffer.addVertex(pose, x3, y3, 0).setColor(c3);
        buffer.addVertex(pose, x4, y4, 0).setColor(c4);
    }

    public static void endAdditive(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    // A soft round glow (additive), centered on x, y.
    public static void glow(GuiGraphics graphics, float x, float y, float radius, int rgb, float strength) {
        if (strength <= 0.003f) {
            return;
        }
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, Mth.clamp(strength, 0, 1));
        graphics.pose().pushPose();
        graphics.pose().translate(x - radius, y - radius, 0);
        float scale = radius * 2 / 64f;
        graphics.pose().scale(scale, scale, 1);
        graphics.blit(SOFT, 0, 0, 0, 0, 64, 64, 64, 64);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
    }

    // A soft round shadow (normal blending): darkens what is under it, for smoke and clouds.
    public static void shade(GuiGraphics graphics, float x, float y, float radius, int rgb, float strength) {
        if (strength <= 0.003f) {
            return;
        }
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, Mth.clamp(strength, 0, 1));
        graphics.pose().pushPose();
        graphics.pose().translate(x - radius, y - radius, 0);
        float scale = radius * 2 / 64f;
        graphics.pose().scale(scale, scale, 1);
        graphics.blit(SOFT, 0, 0, 0, 0, 64, 64, 64, 64);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    // A filled circle made of thin rows (batched like rect).
    public static void disc(GuiGraphics graphics, float x, float y, float radius, int color) {
        float step = radius > 12 ? 1 : 0.5f;
        for (float dy = -radius; dy < radius; dy += step) {
            float mid = dy + step / 2;
            float half = Mth.sqrt(Math.max(0, radius * radius - mid * mid));
            rect(graphics, x - half, y + dy, x + half, y + dy + step, color);
        }
    }

    // A 1 pixel frame around a box.
    public static void frame(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        graphics.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    // A frame with small bright corners, the look shared by every box of the skill UI.
    public static void ornateFrame(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int corner) {
        frame(graphics, x1, y1, x2, y2, color);
        int c = 3;
        graphics.fill(x1 - 1, y1 - 1, x1 + c, y1 + 1, corner);
        graphics.fill(x1 - 1, y1 - 1, x1 + 1, y1 + c, corner);
        graphics.fill(x2 - c, y1 - 1, x2 + 1, y1 + 1, corner);
        graphics.fill(x2 - 1, y1 - 1, x2 + 1, y1 + c, corner);
        graphics.fill(x1 - 1, y2 - 1, x1 + c, y2 + 1, corner);
        graphics.fill(x1 - 1, y2 - c, x1 + 1, y2 + 1, corner);
        graphics.fill(x2 - c, y2 - 1, x2 + 1, y2 + 1, corner);
        graphics.fill(x2 - 1, y2 - c, x2 + 1, y2 + 1, corner);
    }

    public static void text(GuiGraphics graphics, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    public static void centeredText(GuiGraphics graphics, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
        text(graphics, font, text, x - font.width(text) * scale / 2, y, scale, color, shadow);
    }

    public static void rightText(GuiGraphics graphics, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
        text(graphics, font, text, x - font.width(text) * scale, y, scale, color, shadow);
    }
}
