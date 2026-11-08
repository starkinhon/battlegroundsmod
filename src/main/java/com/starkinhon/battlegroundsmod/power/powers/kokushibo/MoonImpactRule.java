package com.starkinhon.battlegroundsmod.power.powers.kokushibo;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

// The Awakened Moon's new impact (Stark, 2026-11-08): the screen script of every awakened form (client/fx/MoonScreen:
// impact frames, shakes, FOV, colour split, letterbox, the cut passing by) and its freezes and slow motion (MoonTime).
// /soul effectsimpactkokushibo off brings back how they were before: real time, and only the flash, blur and shake the
// forms opened with. Saved in the world as /gamerule soulsKokushiboImpact (default on). The server tells the clients
// with each form it casts, so everyone sees the same and the hits stay on the cuts.
public final class MoonImpactRule {
    public static final GameRules.Key<GameRules.BooleanValue> RULE =
            GameRules.register("soulsKokushiboImpact", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    private MoonImpactRule() {
    }

    // Loads the class so the rule is registered at startup.
    public static void init() {
    }

    public static boolean on(Level level) {
        return level.getGameRules().getBoolean(RULE);
    }

    // /soul effectsimpactkokushibo on | off
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("effectsimpactkokushibo").requires(source -> source.hasPermission(2))
                .then(Commands.literal("on").executes(c -> set(c.getSource(), true)))
                .then(Commands.literal("off").executes(c -> set(c.getSource(), false)))
                .executes(c -> {
                    boolean on = on(c.getSource().getLevel());
                    c.getSource().sendSuccess(() -> Component.literal("Efeitos novos da Lua Despertada: " + (on ? "ligados" : "desligados")), false);
                    return on ? 1 : 0;
                });
    }

    private static int set(CommandSourceStack source, boolean on) {
        source.getServer().getGameRules().getRule(RULE).set(on, source.getServer());
        source.sendSuccess(() -> Component.literal(on ? "Efeitos novos da Lua Despertada ligados."
                : "Efeitos novos da Lua Despertada desligados: as formas despertas voltaram a ser como antes."), true);
        return 1;
    }
}
