// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

// Whether the player is in a universe biome's ambience (its fog, wind, leaves...). A biome mixed with vanilla ones (the
// Norte Gelado next to the vanilla snow) flipped its ambience on and off every few steps along the border (Stark
// 2026-11-12, "tira um pouco da imersão"). So a biome reaches a little around itself: getting close is enough to start
// it (ENTER blocks), and it only lets go once there is none of it within EXIT blocks. The effects ease in and out on top.
public final class BiomeAura {
    private static final int ENTER = 16;
    private static final int EXIT = 36;
    private static final Set<String> IN = new HashSet<>();
    private static Level lastLevel;

    private BiomeAura() {
    }

    // id: a name for this check (one state per biome); is: the biome test.
    public static boolean in(String id, Predicate<Holder<Biome>> is) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            IN.clear();
            return false;
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            IN.clear();
        }
        boolean was = IN.contains(id);
        BlockPos at = mc.player.blockPosition();
        // Flying fast the player can get ahead of the chunks the server has sent. The client reads an unloaded chunk as
        // plains, so the ambience started fading out there and came back once the chunk arrived: a stop and a jump back
        // in the middle of the blend (Stark 2026-11-19, "para do nada e volta, como se tivesse lagado"). Over a chunk
        // not here yet the ambience keeps going as it was.
        if (!loaded(mc.level, at)) {
            return was;
        }
        boolean now = near(mc.level, at, is, was ? EXIT : ENTER);
        if (now) {
            IN.add(id);
        } else {
            IN.remove(id);
        }
        return now;
    }

    // The spot itself, then two rings of eight around it at half and full radius (at the player's height).
    private static boolean near(Level level, BlockPos pos, Predicate<Holder<Biome>> is, int radius) {
        if (is.test(level.getBiome(pos))) {
            return true;
        }
        for (int ring = 1; ring <= 2; ring++) {
            int r = radius * ring / 2;
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                BlockPos at = pos.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r));
                // (a spot in a chunk not loaded yet says nothing: it would read as plains)
                if (loaded(level, at) && is.test(level.getBiome(at))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean loaded(Level level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    // The flat colored wash the biomes lay over the whole screen (the "grade": orange in the Dragon Ball lands, green on
    // Namek, grey in the mist and the rain...). A veil of even a few percent over the entire image lowers its contrast
    // and makes it look softly blurred, like a photo edited with a filter, in the day as at night (Stark 2026-11-14). The
    // fog, the sky, the wind and the vignette at the edges keep each biome's atmosphere, so the wash is off (0). 1 = as it
    // was before, 0.3 = a faint trace of it. Every biome's wash goes through wash() below.
    public static final float WASH_STRENGTH = 0f;

    // A flat wash of rgb over the whole screen, alpha 0..1 before WASH_STRENGTH.
    public static void wash(net.minecraft.client.gui.GuiGraphics graphics, int rgb, float alpha) {
        int a = net.minecraft.util.Mth.clamp((int) (alpha * WASH_STRENGTH * 255), 0, 255);
        if (a > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (a << 24) | (rgb & 0xFFFFFF));
        }
    }

    // How much of a light screen grade to keep: all of it by day, a fifth at night. A pale tint over the dark night
    // screen read as a faded picture lying on top of it (Stark 2026-11-13, in every biome with its own ambience).
    public static float nightDim(float partial) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return 1;
        }
        float sunY = net.minecraft.util.Mth.cos(mc.level.getSunAngle(partial));
        return 0.2f + 0.8f * net.minecraft.util.Mth.clamp(sunY * 3 + 0.3f, 0, 1);
    }
}