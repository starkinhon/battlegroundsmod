package com.starkinhon.battlegroundsmod.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /soul: the one command for everything in the mod. /soul ajuda lists what the player can use; /poder still works as
// a shortcut to the same tree.
//
// Other parts of the mod add their own subcommands with addSubcommand, from the mod constructor (before commands
// are registered), e.g. SoulCommand.addSubcommand(BossCommand::build) for /soul bossbar.
public final class SoulCommand {
    private static final List<Supplier<LiteralArgumentBuilder<CommandSourceStack>>> EXTRA = new ArrayList<>();

    private SoulCommand() {
    }

    // build makes a fresh subcommand each time (commands are rebuilt on /reload). Give it its own .requires for
    // permissions: /soul itself is open to everyone.
    public static void addSubcommand(Supplier<LiteralArgumentBuilder<CommandSourceStack>> build) {
        EXTRA.add(build);
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("soul");
        root.then(Commands.literal("ajuda").executes(context -> help(dispatcher, context.getSource())));
        for (LiteralArgumentBuilder<CommandSourceStack> sub : PowerCommand.subcommands()) {
            root.then(sub.requires(source -> source.hasPermission(2)));
        }
        for (Supplier<LiteralArgumentBuilder<CommandSourceStack>> build : EXTRA) {
            root.then(build.get());
        }
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root.executes(context -> help(dispatcher, context.getSource())));
        dispatcher.register(Commands.literal("poder").redirect(node));
    }

    private static int help(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) {
        CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild("soul");
        source.sendSuccess(() -> Component.translatable("message.battlegroundsmod.soul_help"), false);
        Map<CommandNode<CommandSourceStack>, String> usage = dispatcher.getSmartUsage(node, source);
        for (String line : usage.values()) {
            source.sendSuccess(() -> Component.literal("  /soul " + line), false);
        }
        return usage.size();
    }
}
