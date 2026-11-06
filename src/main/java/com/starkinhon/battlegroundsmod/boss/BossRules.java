package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.TierSpec;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

// World rules for bosses.
// /gamerule allbossestier0 true: every boss becomes Tier 0 with the same health, armor, knockback resistance and
// damage (TierSpec.bossTier0), so a fight between bosses is won by their skills only. Speed, sight and AI stay each
// boss's own. Switching it back restores each boss's file.
// /gamerule soulbossfight true: the full boss fight (entrance and phase titles, the boss bar, phases that unlock moves
// over time, the final ult before dying, the grab line in the chat, the Roxo Irrestrito title). false (default, while
// there are no boss fights yet): bosses are plain mobs that use every move they have from the start, with no titles,
// bar, entrance, phase changes or final ult.
public final class BossRules {
    public static final GameRules.Key<GameRules.BooleanValue> ALL_BOSSES_TIER0 =
            GameRules.register("allbossestier0", GameRules.Category.MOBS, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.BooleanValue> BOSSFIGHT =
            GameRules.register("soulbossfight", GameRules.Category.MOBS, GameRules.BooleanValue.create(false));

    private BossRules() {
    }

    // Loads the class so the rule is registered at startup.
    public static void init() {
    }

    public static boolean allTier0(Level level) {
        return level.getGameRules().getBoolean(ALL_BOSSES_TIER0);
    }

    public static boolean bossfight(Level level) {
        return level.getGameRules().getBoolean(BOSSFIGHT);
    }

    public static int tier(Level level, BossSpec spec) {
        if (allTier0(level)) {
            return Tiers.DEUS;
        }
        return spec.tier() != null ? Tiers.clamp(spec.tier()) : Tiers.HUMANO;
    }

    public static BossSpec.Atributos atributos(Level level, BossSpec spec) {
        BossSpec.Atributos own = spec.atributos();
        TierSpec.BossTier0 equal = TierSpec.get().bossTier0();
        if (!allTier0(level) || equal == null) {
            return own;
        }
        return new BossSpec.Atributos(equal.vida(), equal.armadura(), own.velocidade(), equal.resistenciaEmpurrao(), own.alcanceVisao(),
                equal.multDano());
    }
}
