package com.starkinhon.battlegroundsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /poder escolher <poder> | /poder limpar | /poder carregar | /poder livro
public final class PowerCommand {

    private PowerCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("poder")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("escolher")
                        .then(Commands.argument("poder", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Powers.all().stream().map(Power::id), builder))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Power power = Powers.get(StringArgumentType.getString(context, "poder"));
                                    if (power == null) {
                                        context.getSource().sendFailure(Component.translatable("message.battlegroundsmod.unknown_power"));
                                        return 0;
                                    }
                                    PowerLogic.select(player, power);
                                    return 1;
                                })))
                .then(Commands.literal("limpar").executes(context -> {
                    PowerLogic.clear(context.getSource().getPlayerOrException());
                    return 1;
                }))
                // Fills energy and ult and clears cooldowns. Handy for recording.
                .then(Commands.literal("carregar").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PowerData data = player.getData(ModAttachments.POWER);
                    data.resetCombat();
                    data.ult = PowerData.MAX_ULT;
                    PowerLogic.sync(player);
                    return 1;
                }))
                .then(Commands.literal("livro").executes(context -> {
                    context.getSource().getPlayerOrException().getInventory().add(new ItemStack(ModItems.POWER_BOOK.get()));
                    return 1;
                })));
    }
}
