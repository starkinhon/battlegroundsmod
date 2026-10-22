package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

// Boss bars drawn with the boss's own frames instead of the vanilla bar: frame 0 is full life, the last one empty.
// The bar shakes and flashes when the boss takes a hit. Players switch it off with /soul bossbar.
public final class BossBars {
    private record Style(String texture, int frames, int width, int height) {
    }

    private static final class Shown {
        float lastProgress = -1;
        int hitTicks;
    }

    private static final int HIT_TICKS = 8;
    private static final int GAP = 4;
    // Size of the frame images (they are all the same size).
    private static final int TEXTURE_WIDTH = 374;
    private static final int TEXTURE_HEIGHT = 65;

    public static boolean enabled = true;
    private static final Map<UUID, Style> STYLES = new HashMap<>();
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private BossBars() {
    }

    static void style(UUID barId, String texture, int frames, int width, int height) {
        STYLES.put(barId, new Style(texture, Math.max(1, frames), width, height));
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, style.width(), style.height(), 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        if (shown.hitTicks > 0) {
            // White flash over the frame.
            RenderSystem.setShaderColor(1, 1, 1, shown.hitTicks / (float) HIT_TICKS * 0.5f);
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            graphics.blit(texture, x, y, style.width(), style.height(), 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableBlend();
        event.setIncrement(style.height() + GAP);
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
