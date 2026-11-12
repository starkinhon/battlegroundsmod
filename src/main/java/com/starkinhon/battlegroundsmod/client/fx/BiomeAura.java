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
        boolean now = near(mc.level, mc.player.blockPosition(), is, was ? EXIT : ENTER);
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
                if (is.test(level.getBiome(pos.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r))))) {
                    return true;
                }
            }
        }
        return false;
    }
}
