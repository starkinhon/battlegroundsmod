package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
// in the soul's colors like a game logo (Stark's reference): a gradient from the soul's dark shade on the left to its
// bright shade on the right, a hard dark outline around every letter with a darker shadow under it, and a soft glow of
// the soul's color outside the outline (no bar behind it), no name on top. It stays 5 s, then fades out rising a little.
// It is drawn as a screen layer over the world, at the head's spot on screen and sized by the distance like a name
// tag, so no effect (glow, smoke, distortion, bloom) can ever cover it. A wall between the camera and the head hides it.
public final class FalaRender {
    // How long it stays whole, then how long it takes to fade out (ticks).
    private static final float HOLD = 100;
    private static final float FADE = 10;
    // How much it rises while fading, in blocks.
    private static final float RISE = 0.3f;
    // Over the head, well clear of it (the name tag sits at +0.5; Stark: 0.5 too close, 1.0 too far, a third of a block higher).
    private static final float ABOVE = 0.83f;
    // Text height in blocks per font pixel (a name tag is 0.025), and the screen size limits (GUI scale).
    private static final float BLOCKS_PER_PIXEL = 0.03f;
    private static final float MIN_SCALE = 0.7f;
    private static final float MAX_SCALE = 2.5f;
    // The widest a line gets, as a share of the screen width, before it wraps.
    private static final float MAX_WIDTH = 0.7f;
    // The glow: copies of the line in the soul's bright color outside the outline, one ring per entry {radius in font
    // pixels, alpha}.
    private static final float[][] GLOW = {{2.0f, 0.30f}, {3.0f, 0.12f}};
    // The outline: the soul's dark shade darkened this much (0 black, 1 the dark shade itself).
    private static final float OUTLINE_SHADE = 0.32f;
    // Farther than this, it is not drawn (the server only sends it to players within 32 blocks).
    private static final double MAX_DISTANCE = 48;

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean captured;

    private record Line(String text, int color1, int color2, long start, float hold) {
        float total() {
            return hold + FADE;
        }
    }

    private static final Map<Integer, Line> LINES = new HashMap<>();

    private FalaRender() {
    }

    public static void add(int entityId, String text, int color1, int color2) {
        add(entityId, text, color1, color2, HOLD);
    }

    // Same, staying whole for hold ticks (Saitama's short words: "ok.", "Ha...", "ATCHIM!"). An empty text takes the line off.
    public static void add(int entityId, String text, int color1, int color2, float hold) {
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
        if (text.isEmpty()) {
            LINES.remove(entityId);
            return;
        }
        LINES.put(entityId, new Line(shown, color1, color2, now, hold));
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

    // A world point on the GUI screen of the last frame: {x, y, GUI pixels per block there}, or null when it is behind the
    // camera or the frame isn't known yet (FormKanji uses it too).
    static float[] toScreen(GuiGraphics graphics, Vec3 at) {
        if (!captured) {
            return null;
        }
        Vector4f view = new Vector4f((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z), 1);
        VIEW.transform(view);
        float depth = -view.z;
        if (depth <= 0.1f) {
            return null;
        }
        Vector4f clip = new Vector4f(view);
        PROJ.transform(clip);
        if (clip.w <= 0.05f) {
            return null;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        return new float[] {(clip.x / clip.w * 0.5f + 0.5f) * w, (1 - (clip.y / clip.w * 0.5f + 0.5f)) * h, PROJ.m11() * h / 2f / depth};
    }

    static Vec3 camera() {
        return camera;
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
        float out = age > line.hold() ? (age - line.hold()) / FADE : 0;
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

        // A long line wraps (whole words) to fit the screen at this scale, up close too; the gradient runs over the whole
        // phrase, line after line, and the last line sits on the head's spot.
        Font font = mc.font;
        String text = line.text();
        List<String> rows = wrap(font, text, Math.max(80, (int) (w * MAX_WIDTH / scale)));
        int total = text.length();
        // Up close (someone standing right in front of the boss) the head's spot is near the top of the screen: the
        // lines come down as far as needed to stay whole on screen (the outline and glow need a few pixels).
        float top = sy + (-font.lineHeight - (rows.size() - 1) * (font.lineHeight + 2) - 4) * scale;
        if (top < 4) {
            sy += 4 - top;
        }
        int index = 0;
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r);
            int start = text.indexOf(row, index);
            if (start < 0) {
                start = index;
            }
            index = start + row.length();
            float rowY = -font.lineHeight - (rows.size() - 1 - r) * (font.lineHeight + 2);
            drawRow(graphics, font, row, start, total, line, alpha, a, sx, sy, scale, rowY);
        }
    }

    // Splits text into lines no wider than maxWidth font pixels, breaking only between words (a single word too long
    // for a line stays whole).
    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> rows = new ArrayList<>();
        if (font.width(text) <= maxWidth) {
            rows.add(text);
            return rows;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = current.length() == 0 ? word : current + " " + word;
            if (current.length() > 0 && font.width(next) > maxWidth) {
                rows.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(next);
            }
        }
        if (current.length() > 0) {
            rows.add(current.toString());
        }
        return rows;
    }

    // One line of the phrase: glow, shadow, outline and the letters. first: where it starts in the whole phrase (for the
    // gradient).
    private static void drawRow(GuiGraphics graphics, Font font, String text, int first, int total, Line line, float alpha, int a, float sx,
            float sy, float scale, float rowY) {
        MutableComponent colored = Component.empty();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float k = total <= 1 ? 0 : (float) (first + i) / (total - 1);
            colored.append(Component.literal(String.valueOf(text.charAt(i))).withColor(lerpColor(line.color1(), line.color2(), k)));
        }
        Component plain = Component.literal(text);
        int width = font.width(text);

        graphics.pose().pushPose();
        graphics.pose().translate(sx, sy, 0);
        graphics.pose().scale(scale, scale, 1);
        // Centered, sitting on the head's spot.
        float x = -width / 2f, y = rowY;
        graphics.pose().translate(x, y, 0);
        // No bar behind it (Stark): the outline and the glow make it read on any sky.
        // The glow: the line in the soul's bright color, a few times around the letters, fainter the farther out.
        for (float[] ring : GLOW) {
            int ga = Math.round(ring[1] * alpha * 255);
            if (ga < 5) {
                continue;
            }
            for (int d = 0; d < 8; d++) {
                double ang = d * Math.PI / 4;
                graphics.pose().pushPose();
                graphics.pose().translate((float) Math.cos(ang) * ring[0], (float) Math.sin(ang) * ring[0], 0);
                graphics.drawString(font, plain, 0, 0, ga << 24 | line.color2(), false);
                graphics.pose().popPose();
            }
        }
        // The shadow (one pixel lower, two thick) and the outline (one pixel all around), in the dark shade.
        int dark = a << 24 | shade(line.color1(), OUTLINE_SHADE);
        int shadow = a << 24 | shade(line.color1(), OUTLINE_SHADE * 0.5f);
        graphics.drawString(font, plain, 0, 2, shadow, false);
        graphics.drawString(font, plain, 1, 2, shadow, false);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    graphics.drawString(font, plain, dx, dy, dark, false);
                }
            }
        }
        // The letters on top.
        graphics.drawString(font, colored, 0, 0, a << 24 | 0xFFFFFF, false);
        graphics.pose().popPose();
    }

    private static int shade(int c, float k) {
        return Math.round((c >> 16 & 255) * k) << 16 | Math.round((c >> 8 & 255) * k) << 8 | Math.round((c & 255) * k);
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
