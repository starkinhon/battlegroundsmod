package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// A boss's line floating over its head (boss/BossFalas sends it). The whole line shows at once (no letter by letter),
// in the soul's colors (a gradient from the first color to the second) with a glow of the soul's color around the
// letters, on a dark see-through bar so it reads on any sky, no name on top. It stays 5 s, then fades out rising a little.
// It is drawn as a screen layer over the world, at the head's spot on screen and sized by the distance like a name
// tag, so no effect (glow, smoke, distortion, bloom) can ever cover it. A wall between the camera and the head hides it.
public final class FalaRender {
    // How long it stays whole, then how long it takes to fade out (ticks).
    private static final float HOLD = 100;
    private static final float FADE = 10;
    // How much it rises while fading, in blocks.
    private static final float RISE = 0.3f;
    // Over the head (the name tag sits at +0.5).
    private static final float ABOVE = 0.5f;
    // Text height in blocks per font pixel (a name tag is 0.025), and the screen size limits (GUI scale).
    private static final float BLOCKS_PER_PIXEL = 0.03f;
    private static final float MIN_SCALE = 0.7f;
    private static final float MAX_SCALE = 2.5f;
    // The bar's darkness (0..255): 62%, the same as the Visualizador.
    private static final int BAR_ALPHA = 158;
    // The glow: copies of the line in the soul's color around the letters, one ring per entry {radius in font pixels, alpha}.
    private static final float[][] GLOW = {{1.0f, 0.38f}, {2.0f, 0.16f}};
    // Farther than this, it is not drawn (the server only sends it to players within 32 blocks).
    private static final double MAX_DISTANCE = 48;

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean captured;

    private record Line(String text, int color1, int color2, long start) {
        float total() {
            return HOLD + FADE;
        }
    }

    private static final Map<Integer, Line> LINES = new HashMap<>();

    private FalaRender() {
    }

    public static void add(int entityId, String text, int color1, int color2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String shown = I18n.exists(text) ? I18n.get(text) : text;
        long now = mc.level.getGameTime();
        // Drop the ones that are over (a boss that died or left keeps nothing around).
        for (Iterator<Map.Entry<Integer, Line>> it = LINES.entrySet().iterator(); it.hasNext(); ) {
            Line line = it.next().getValue();
            if (now - line.start() > line.total() + 20 || now < line.start()) {
                it.remove();
            }
        }
        LINES.put(entityId, new Line(shown, color1, color2, now));
    }

    // The camera of the frame, to place the lines on screen.
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        VIEW.set(event.getModelViewMatrix());
        PROJ.set(event.getProjectionMatrix());
        camera = event.getCamera().getPosition();
        captured = true;
    }

    // The screen layer (under the HUD, over the world and every effect).
    public static void renderGui(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (LINES.isEmpty() || mc.level == null || mc.player == null || !captured || mc.options.hideGui) {
            return;
        }
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        for (Iterator<Map.Entry<Integer, Line>> it = LINES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Line> entry = it.next();
            Line line = entry.getValue();
            float age = (now - line.start()) + partial;
            if (age > line.total()) {
                it.remove();
                continue;
            }
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null || age < 0) {
                continue;
            }
            draw(graphics, mc, entity, line, age, partial);
        }
    }

    private static void draw(GuiGraphics graphics, Minecraft mc, Entity entity, Line line, float age, float partial) {
        float out = age > HOLD ? (age - HOLD) / FADE : 0;
        float smoothOut = out * out * (3 - 2 * out);
        float alpha = 1 - smoothOut;
        int a = Math.round(alpha * 255);
        // The font turns an alpha under 4 into fully opaque, so a line that is almost gone is just not drawn.
        if (a < 5) {
            return;
        }
        Vec3 head = entity.getPosition(partial).add(0, entity.getBbHeight() + ABOVE + RISE * smoothOut, 0);
        if (head.distanceTo(camera) > MAX_DISTANCE) {
            return;
        }
        // Behind a wall: hidden, like a name tag.
        if (mc.level.clip(new ClipContext(camera, head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType()
                != HitResult.Type.MISS) {
            return;
        }
        Vector4f view = new Vector4f((float) (head.x - camera.x), (float) (head.y - camera.y), (float) (head.z - camera.z), 1);
        VIEW.transform(view);
        float depth = -view.z;
        if (depth <= 0.1f) {
            return;
        }
        Vector4f clip = new Vector4f(view);
        PROJ.transform(clip);
        if (clip.w <= 0.05f) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        float sx = (clip.x / clip.w * 0.5f + 0.5f) * w;
        float sy = (1 - (clip.y / clip.w * 0.5f + 0.5f)) * h;
        // GUI pixels per block at that distance, and the font scale that makes one font pixel BLOCKS_PER_PIXEL tall.
        float pxPerBlock = PROJ.m11() * h / 2f / depth;
        float scale = Mth.clamp(BLOCKS_PER_PIXEL * pxPerBlock, MIN_SCALE, MAX_SCALE);

        String text = line.text();
        Font font = mc.font;
        MutableComponent colored = Component.empty();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float k = len <= 1 ? 0 : (float) i / (len - 1);
            colored.append(Component.literal(String.valueOf(text.charAt(i))).withColor(lerpColor(line.color1(), line.color2(), k)));
        }
        Component plain = Component.literal(text);
        int width = font.width(text);

        graphics.pose().pushPose();
        graphics.pose().translate(sx, sy, 0);
        graphics.pose().scale(scale, scale, 1);
        // Centered, sitting on the head's spot.
        float x = -width / 2f, y = -font.lineHeight;
        graphics.pose().translate(x, y, 0);
        // The bar.
        graphics.fill(-3, -2, width + 2, font.lineHeight + 1, Math.round(BAR_ALPHA * alpha) << 24 | 0x08060C);
        // The glow: the line in the soul's color, a few times around the letters, fainter the farther out.
        for (float[] ring : GLOW) {
            int ga = Math.round(ring[1] * alpha * 255);
            if (ga < 5) {
                continue;
            }
            for (int d = 0; d < 8; d++) {
                double ang = d * Math.PI / 4;
                graphics.pose().pushPose();
                graphics.pose().translate((float) Math.cos(ang) * ring[0], (float) Math.sin(ang) * ring[0], 0);
                graphics.drawString(font, plain, 0, 0, ga << 24 | line.color1(), false);
                graphics.pose().popPose();
            }
        }
        // The letters on top.
        graphics.drawString(font, colored, 0, 0, a << 24 | 0xFFFFFF, false);
        graphics.pose().popPose();
    }

    private static int lerpColor(int c1, int c2, float k) {
        int r = Math.round(Mth.lerp(k, c1 >> 16 & 255, c2 >> 16 & 255));
        int g = Math.round(Mth.lerp(k, c1 >> 8 & 255, c2 >> 8 & 255));
        int b = Math.round(Mth.lerp(k, c1 & 255, c2 & 255));
        return r << 16 | g << 8 | b;
    }

    public static void clear() {
        LINES.clear();
    }
}
