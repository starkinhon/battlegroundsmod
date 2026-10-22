package com.starkinhon.battlegroundsmod.command;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Cone;
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

// The power subcommands of /soul (see SoulCommand): escolher <poder> | limpar | carregar | semcooldown | despertar | hitbox | livro
public final class PowerCommand {

    private PowerCommand() {
    }

    public static List<LiteralArgumentBuilder<CommandSourceStack>> subcommands() {
        return List.of(
                Commands.literal("escolher")
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
                                })),
                Commands.literal("limpar").executes(context -> {
                    PowerLogic.clear(context.getSource().getPlayerOrException());
                    return 1;
                }),
                // Fills energy and ult and clears cooldowns. Handy for recording.
                Commands.literal("carregar").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PowerData data = player.getData(ModAttachments.POWER);
                    data.resetCombat();
                    data.ult = PowerData.MAX_ULT;
                    PowerLogic.sync(player);
                    return 1;
                }),
                // Toggles cooldowns and costs off for this player, to test and record moves back to back.
                Commands.literal("semcooldown").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PowerData data = player.getData(ModAttachments.POWER);
                    data.noCooldown = !data.noCooldown;
                    data.cooldowns = new int[PowerData.SLOT_COUNT];
                    PowerLogic.sync(player);
                    context.getSource().sendSuccess(() -> Component.translatable(data.noCooldown
                            ? "message.battlegroundsmod.no_cooldown_on" : "message.battlegroundsmod.no_cooldown_off"), false);
                    return 1;
                }),
                // Awakened mode: skills switch to their stronger "despertado" values.
                Commands.literal("despertar").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PowerData data = player.getData(ModAttachments.POWER);
                    data.awakened = !data.awakened;
                    context.getSource().sendSuccess(() -> Component.translatable(data.awakened
                            ? "message.battlegroundsmod.awakened_on" : "message.battlegroundsmod.awakened_off"), false);
                    return 1;
                }),
                // Shows the hitboxes of cone moves as white particles.
                Commands.literal("hitbox").executes(context -> {
                    Cone.debug = !Cone.debug;
                    context.getSource().sendSuccess(() -> Component.translatable(Cone.debug
                            ? "message.battlegroundsmod.hitbox_on" : "message.battlegroundsmod.hitbox_off"), false);
                    return 1;
                }),
                Commands.literal("livro").executes(context -> {
                    context.getSource().getPlayerOrException().getInventory().add(new ItemStack(ModItems.POWER_BOOK.get()));
                    return 1;
                }));
    }
}
