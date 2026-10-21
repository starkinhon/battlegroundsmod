package com.starkinhon.battlegroundsmod.power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// One move of a power. Cooldown is in ticks (20 = 1 second).
public record Ability(int cooldown, float energyCost, Action action) {

    @FunctionalInterface
    public interface Action {
        // Returns false when the move could not happen (no target, etc.), so nothing is spent.
        boolean use(ServerPlayer player, ServerLevel level);
    }
}
