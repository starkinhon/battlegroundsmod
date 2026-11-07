package com.starkinhon.battlegroundsmod.power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

// One move of a power. Cooldown is in ticks (20 = 1 second).
// The caster is any living entity, so bosses use the very same moves as players.
// counter: a move made to get out of a beating, so it still works while stunned or with the Cooldown effect.
// beatenCounter: an instant answer to being beaten up (punches, combos: their short hitstun and the Cooldown effect), but not
// to a real stun from a technique (Stun.real), which still blocks it.
public record Ability(int cooldown, float energyCost, Action action, boolean counter, boolean beatenCounter) {

    public Ability(int cooldown, float energyCost, Action action) {
        this(cooldown, energyCost, action, false, false);
    }

    public Ability(int cooldown, float energyCost, Action action, boolean counter) {
        this(cooldown, energyCost, action, counter, false);
    }

    public Ability asCounter() {
        return new Ability(cooldown, energyCost, action, true, false);
    }

    public Ability asBeatenCounter() {
        return new Ability(cooldown, energyCost, action, false, true);
    }

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
