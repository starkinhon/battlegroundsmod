package com.starkinhon.battlegroundsmod.power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

// One move of a power. Cooldown is in ticks (20 = 1 second).
// The caster is any living entity, so bosses use the very same moves as players.
public record Ability(int cooldown, float energyCost, Action action) {

    @FunctionalInterface
    public interface Action {
        // Returns false when the move could not happen (no target, etc.), so nothing is spent.
        boolean use(LivingEntity caster, ServerLevel level);
    }

    @FunctionalInterface
    public interface PlayerAction {
        boolean use(ServerPlayer player, ServerLevel level);
    }

    // For moves that only work for players for now (the older powers). Other casters just fail.
    public static Ability player(int cooldown, float energyCost, PlayerAction action) {
        return new Ability(cooldown, energyCost, (caster, level) -> caster instanceof ServerPlayer player && action.use(player, level));
    }
}
