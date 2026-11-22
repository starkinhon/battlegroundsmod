// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.combat;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// Whether the mod's skills (players and bosses) may break or change blocks: craters, explosions, fire, the black
// hole pulling blocks, cuts through terrain. Both rules must be on:
// - vanilla /gamerule mobGriefing
// - /gamerule soulsBlockDestruction (default true), to turn off only this mod's block damage and keep mobGriefing
//   for creepers and endermen.
// When off, the visual effects, sounds and damage stay the same; only the blocks are left alone.
public final class BlockRules {
    public static final GameRules.Key<GameRules.BooleanValue> SOULS_BLOCK_DESTRUCTION =
            GameRules.register("soulsBlockDestruction", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    private BlockRules() {
    }

    // Loads the class so the rule is registered at startup.
    public static void init() {
    }

    public static boolean canBreak(Level level) {
        // the arenas never lose a block, in a Dungeon run or not (Stark 2026-11-22: a fight in an arena left dirt floating
        // on its barrier roof; rule "arenas: boss não destrói blocos")
        if (com.starkinhon.battlegroundsmod.challenge.Dungeon.protectsBlocks(level)
                || com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.isArena(level.dimension())) {
            return false;
        }
        // nor the arenas of an introduction (intro/Intro.protectsBlocks)
        if (com.starkinhon.battlegroundsmod.intro.Intro.protectsBlocks(level)) {
            return false;
        }
        // nor the Sala do Tempo (Stark 2026-11-20: the training there broke the room; StructureRegen stays as a backup)
        if (level.dimension() == com.starkinhon.battlegroundsmod.world.TimeChamber.LEVEL) {
            return false;
        }
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && level.getGameRules().getBoolean(SOULS_BLOCK_DESTRUCTION);
    }

    // A block the skills and the mobs leave standing because it holds a block entity (a chest, a sign, a spawner: its
    // contents would be lost). The mod's own scenery with a block entity breaks like any other block (Stark 2026-11-19:
    // in Shibuya the base of the buildings stayed whole "como se fosse indestrutível": the big screens (Telão, up to
    // 22 x 12 on the base's front) and the power lines were skipped by every technique).
    public static boolean keeps(BlockState state) {
        return state.hasBlockEntity() && !(state.getBlock() instanceof com.starkinhon.battlegroundsmod.TelaoBlock)
                && !(state.getBlock() instanceof com.starkinhon.battlegroundsmod.PowerLineBlock);
    }
}
