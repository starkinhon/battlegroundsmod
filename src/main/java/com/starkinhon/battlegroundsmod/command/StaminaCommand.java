package com.starkinhon.battlegroundsmod.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.power.PowerLogic;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// /soul estamina cheia | zerar | <valor>: for testing the attack stamina bar.
public final class StaminaCommand {
    private StaminaCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("estamina").requires(source -> source.hasPermission(2))
                .then(Commands.literal("cheia").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Stamina.refill(player);
                    return done(context.getSource(), player);
                }))
                .then(Commands.literal("zerar").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Stamina.exhaust(player);
                    return done(context.getSource(), player);
                }))
                .then(Commands.argument("valor", FloatArgumentType.floatArg(0)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Stamina.refill(player);
                    float value = (float) Math.min(Stamina.max(player), FloatArgumentType.getFloat(context, "valor"));
                    if (value <= 0) {
                        Stamina.exhaust(player);
                    } else {
                        player.getData(ModAttachments.POWER).stamina = value;
                    }
                    return done(context.getSource(), player);
                }));
    }

    private static int done(CommandSourceStack source, ServerPlayer player) {
        PowerLogic.sync(player);
        source.sendSuccess(() -> Component.translatable("message.battlegroundsmod.stamina_set",
                Math.round(Stamina.get(player)), Math.round(Stamina.max(player))), false);
        return 1;
    }
}
