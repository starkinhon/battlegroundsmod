// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// The rare loot of the two end-game bosses (Stark 2026-11-19): only when a player beats them out in the open world, the
// fight he went looking for (the Senju crest at the Valley of the End, Freeza's ship). The spawn egg, a challenge arena,
// the Dungeon and a puppet of Makima give nothing, so the loot can't be farmed.
public final class EndBossLoot {
    private EndBossLoot() {
    }

    public static void drop(SoulBoss boss, ServerLevel level, DamageSource source, int xp, ItemStack... loot) {
        if (!(source.getEntity() instanceof Player player) || player.isSpectator() || boss.puppetOwner() != null
                || boss.dungeonFight() || ChallengeArenas.isArena(level.dimension())
                || !boss.getPersistentData().getBoolean(WORLD_TAG)) {
            return;
        }
        for (ItemStack stack : loot) {
            boss.spawnAtLocation(stack, 1f);
        }
        ExperienceOrb.award(level, boss.position(), xp);
    }

    // Set by the world spawn (NarutoActivities.valeHashirama, FreezaShip): only these bosses carry the loot.
    public static final String WORLD_TAG = "souls_chefe_mundo";
}
