package com.starkinhon.battlegroundsmod.combat;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

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
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && level.getGameRules().getBoolean(SOULS_BLOCK_DESTRUCTION);
    }
}
