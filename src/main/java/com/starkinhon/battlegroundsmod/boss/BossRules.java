// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.TierSpec;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

// World rules for bosses.
// /gamerule allbossestier0 true (also in the Experiência do jogador menu, "Mundo" tab): every boss THE PLAYER placed
// (spawn egg, /summon, a dispenser with an egg: SoulBoss.playerSpawned) becomes Tier 0 with the same health, armor,
// knockback resistance and damage (TierSpec.bossTier0), so a fight between bosses is won by their skills only. Speed,
// sight and AI stay each boss's own. Switching it back restores each boss's file. Since 2026-11-23 (Stark) it never
// touches the bosses the mod brings: Livro dos Desafios, arenas, Dungeon, natural spawns and structures keep their tier.
// (There was a /gamerule soulbossfight with entrance and phase titles, a boss bar, phases and a final ult; Stark took it
// out on 2026-11-22. Bosses always fight as they did with it off: every move from the start, no titles, bar, entrance,
// phase changes or final ult.)
// /gamerule soulbotscaoticos true: every bot and boss forgets its strategy and fights like a lunatic (SoulBossAI.chaos):
// decisions several times faster, no waiting between moves, the move picked almost at random among everything it may
// use (the tactics only still say what is FORBIDDEN, like an ult outside its moment), short warnings, always running,
// random dodges, air jumps, dashes and blinks in between. Every cooldown, stun, busy lock and reaction delay stays the
// same, so it is wilder, not unfair. false (default): the strategy as it was.
public final class BossRules {
    public static final GameRules.Key<GameRules.BooleanValue> ALL_BOSSES_TIER0 =
            GameRules.register("allbossestier0", GameRules.Category.MOBS, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.BooleanValue> CHAOS =
            GameRules.register("soulbotscaoticos", GameRules.Category.MOBS, GameRules.BooleanValue.create(false));

    private BossRules() {
    }

    // Loads the class so the rule is registered at startup.
    public static void init() {
    }

    public static boolean allTier0(Level level) {
        return level.getGameRules().getBoolean(ALL_BOSSES_TIER0);
    }

    public static boolean chaos(Level level) {
        return level.getGameRules().getBoolean(CHAOS);
    }

    // tier0: the rule applies to this boss (SoulBoss.allTier0).
    public static int tier(boolean tier0, BossSpec spec) {
        if (tier0) {
            return Tiers.DEUS;
        }
        return spec.tier() != null ? Tiers.clamp(spec.tier()) : Tiers.HUMANO;
    }

    public static BossSpec.Atributos atributos(boolean tier0, BossSpec spec) {
        BossSpec.Atributos own = spec.atributos();
        TierSpec.BossTier0 equal = TierSpec.get().bossTier0();
        if (!tier0 || equal == null) {
            return own;
        }
        return new BossSpec.Atributos(equal.vida(), equal.armadura(), own.velocidade(), equal.resistenciaEmpurrao(), own.alcanceVisao(),
                equal.multDano());
    }
}
