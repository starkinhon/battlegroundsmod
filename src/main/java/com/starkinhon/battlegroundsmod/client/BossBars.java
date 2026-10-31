package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
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

    // Light leaking around the bar: a soft breathing halo along it and two bright spots slowly circling it, each
    // with a fading tail. Drawn behind the frame, then once more, faint, over it so the light spills onto the art.
    private static void glow(GuiGraphics graphics, int x, int y, Style style, boolean over) {
        float t = Util.getMillis() / 1000f;
        int color = style.glow();
        int hot = GuiDraw.mix(color, 0xFFFFFF, 0.35f);
        float cx = x + style.width() / 2f;
        float cy = y + style.height() * 0.5f;
        float rx = style.width() * 0.47f;
        float ry = style.height() * 0.42f;
        float breathe = 0.5f + 0.5f * Mth.sin(t * 1.6f);
        float k = over ? 0.3f : 1f;
        if (!over) {
            for (int i = 0; i < 5; i++) {
                float px = cx + (i - 2) * style.width() * 0.2f;
                GuiDraw.glow(graphics, px, cy, style.height() * 0.85f, color, (0.16f + 0.08f * breathe) * k);
            }
        }
        for (int spot = 0; spot < 2; spot++) {
            float base = t * 0.55f + spot * Mth.PI;
            for (int tail = 0; tail < 7; tail++) {
                float a = base - tail * 0.09f;
                float fade = 1 - tail / 7f;
                float px = cx + Mth.cos(a) * rx;
                float py = cy + Mth.sin(a) * ry;
                GuiDraw.glow(graphics, px, py, style.height() * (0.55f + 0.25f * fade), tail == 0 ? hot : color, 0.42f * fade * fade * k);
            }
        }
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
