package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

// Boss bars drawn with the boss's own frames instead of the vanilla bar: frame 0 is full life, the last one empty.
// The bar shakes and flashes when the boss takes a hit. Players switch it off with /soul bossbar.
public final class BossBars {
    // glow: color of the light circling the bar, -1 for none.
    private record Style(String texture, int frames, int width, int height, int glow) {
    }

    private static final class Shown {
        float lastProgress = -1;
        int hitTicks;
    }

    private static final int HIT_TICKS = 8;
    private static final int GAP = 4;

    public static boolean enabled = true;
    private static final Map<UUID, Style> STYLES = new HashMap<>();
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private BossBars() {
    }

    static void style(UUID barId, String texture, int frames, int width, int height, int glow) {
        STYLES.put(barId, new Style(texture, Math.max(1, frames), width, height, glow));
    }

    public static void tick() {
        for (Shown shown : SHOWN.values()) {
            if (shown.hitTicks > 0) {
                shown.hitTicks--;
            }
        }
    }

    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bar = event.getBossEvent();
        Style style = STYLES.get(bar.getId());
        if (!enabled || style == null) {
            return;
        }
        event.setCanceled(true);
        float progress = Mth.clamp(bar.getProgress(), 0, 1);
        Shown shown = SHOWN.computeIfAbsent(bar.getId(), id -> new Shown());
        if (shown.lastProgress >= 0 && progress < shown.lastProgress - 0.002f) {
            shown.hitTicks = HIT_TICKS;
        }
        shown.lastProgress = progress;

        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 - style.width() / 2;
        int y = event.getY() - 2;
        if (shown.hitTicks > 0) {
            float shake = shown.hitTicks / (float) HIT_TICKS * 2;
            x += Math.round((float) (Math.random() * 2 - 1) * shake);
            y += Math.round((float) (Math.random() * 2 - 1) * shake);
        }
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID,
                "textures/gui/bossbar/" + style.texture() + "/" + frame(progress, style.frames()) + ".png");
        if (style.glow() >= 0) {
            glow(graphics, x, y, style, false);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // UVs over the whole image, whatever its size.
        graphics.blit(texture, x, y, style.width(), style.height(), 0, 0, 1, 1, 1, 1);
        if (style.glow() >= 0) {
            glow(graphics, x, y, style, true);
            RenderSystem.enableBlend();
        }
        if (shown.hitTicks > 0) {
            // White flash over the frame.
            RenderSystem.setShaderColor(1, 1, 1, shown.hitTicks / (float) HIT_TICKS * 0.5f);
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            graphics.blit(texture, x, y, style.width(), style.height(), 0, 0, 1, 1, 1, 1);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableBlend();
        event.setIncrement(style.height() + GAP);
    }

    // Light bursting from behind the bar: purple and violet beams fanning out from the middle ornament, each slowly
    // breathing, the fan turning very slowly, with a bright core. Drawn behind the frame, then once more, faint, over it
    // so the light spills onto the art.
    private static final int RAYS = 14;

    private static void glow(GuiGraphics graphics, int x, int y, Style style, boolean over) {
        float t = Util.getMillis() / 1000f;
        int color = style.glow();
        int hot = GuiDraw.mix(color, 0xFFFFFF, 0.55f);
        int cool = GuiDraw.mix(color, 0x4A5CFF, 0.45f);
        float cx = x + style.width() / 2f;
        float cy = y + style.height() * 0.5f;
        float k = over ? 0.22f : 1f;
        float reach = style.width() * 0.62f;

        // Two passes over the same beams: a plain see-through purple layer first, so they keep their color over a
        // bright day sky (light alone turns white there), then the light itself.
        for (int pass = over ? 1 : 0; pass < 2; pass++) {
            BufferBuilder buffer = pass == 1 ? GuiDraw.beginAdditive(graphics) : null;
            java.util.Random random = new java.util.Random(4242);
            for (int i = 0; i < RAYS; i++) {
                float base = i / (float) RAYS * Mth.TWO_PI + (random.nextFloat() - 0.5f) * 0.25f;
                float spread = 0.035f + random.nextFloat() * 0.09f;
                float phase = random.nextFloat() * Mth.TWO_PI;
                float speed = 0.6f + random.nextFloat() * 1.2f;
                float length = reach * (0.45f + random.nextFloat() * 0.55f) * (0.8f + 0.2f * Mth.sin(t * speed + phase));
                float bright = (0.22f + 0.18f * random.nextFloat()) * (0.65f + 0.35f * Mth.sin(t * speed * 1.3f + phase * 2));
                float angle = base + t * 0.04f + 0.03f * Mth.sin(t * 0.5f + phase);
                int tint = random.nextFloat() < 0.35f ? cool : color;
                float dx = Mth.cos(angle);
                float dy = Mth.sin(angle);
                float nx = -dy;
                float ny = dx;
                float start = 1.5f;
                float end = length * spread * 3;
                float ex = cx + dx * length;
                float ey = cy + dy * length;
                int outer = tint & 0xFFFFFF;
                if (pass == 0) {
                    int paint = GuiDraw.argb(Math.round(110 * bright / 0.4f), tint);
                    GuiDraw.quad(graphics, cx + nx * start, cy + ny * start, ex + nx * end, ey + ny * end,
                            ex - nx * end, ey - ny * end, cx - nx * start, cy - ny * start, paint, outer, outer, paint);
                    continue;
                }
                int inner = GuiDraw.argb(Math.round(255 * bright * k), GuiDraw.mix(tint, 0xFFFFFF, 0.3f));
                GuiDraw.additiveQuad(graphics, buffer, cx + nx * start, cy + ny * start, ex + nx * end, ey + ny * end,
                        ex - nx * end, ey - ny * end, cx - nx * start, cy - ny * start, inner, outer, outer, inner);
                // A thin bright core down the middle of the wider beams.
                if (spread > 0.07f) {
                    float core = end * 0.25f;
                    int coreColor = GuiDraw.argb(Math.round(200 * bright * k), hot);
                    GuiDraw.additiveQuad(graphics, buffer, cx + nx * 0.6f, cy + ny * 0.6f, ex + nx * core, ey + ny * core,
                            ex - nx * core, ey - ny * core, cx - nx * 0.6f, cy - ny * 0.6f, coreColor, hot & 0xFFFFFF, hot & 0xFFFFFF, coreColor);
                }
            }
            if (buffer != null) {
                GuiDraw.endAdditive(buffer);
            }
        }
        float breathe = 0.5f + 0.5f * Mth.sin(t * 1.6f);
        GuiDraw.glow(graphics, cx, cy, style.height() * 1.1f, color, (0.28f + 0.1f * breathe) * k);
        GuiDraw.glow(graphics, cx, cy, style.height() * 0.45f, hot, (0.3f + 0.12f * breathe) * k);
    }

    // Full life shows frame 0; any damage moves to frame 1, and the last frame only shows at zero.
    private static int frame(float progress, int frames) {
        if (progress >= 0.999f) {
            return 0;
        }
        if (progress <= 0) {
            return frames;
        }
        return Mth.clamp(frames - (int) Math.ceil(progress * (frames - 1)), 1, frames - 1);
    }
}
