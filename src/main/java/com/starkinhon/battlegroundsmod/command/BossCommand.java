package com.starkinhon.battlegroundsmod.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBossAI;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// /soul bossbar: turns the custom boss bars on or off for yourself (saved on the player, kept on death).
// /soul boss debug: shows what every boss is thinking on the action bar (ops only).
public final class BossCommand {
    private BossCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> bossbar() {
        return Commands.literal("bossbar").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean off = !player.getData(ModAttachments.BOSS_BAR_OFF);
            player.setData(ModAttachments.BOSS_BAR_OFF, off);
            syncBossBar(player);
            context.getSource().sendSuccess(() -> Component.translatable(off
                    ? "message.battlegroundsmod.bossbar_off" : "message.battlegroundsmod.bossbar_on"), false);
            return 1;
        });
    }

    public static LiteralArgumentBuilder<CommandSourceStack> boss() {
        return Commands.literal("boss")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug").executes(context -> {
                    SoulBossAI.debug = !SoulBossAI.debug;
                    context.getSource().sendSuccess(() -> Component.translatable(SoulBossAI.debug
                            ? "message.battlegroundsmod.boss_debug_on" : "message.battlegroundsmod.boss_debug_off"), false);
                    return 1;
                }));
    }

    // Tells the client whether to draw custom boss bars. Sent on login and when toggled.
    public static void syncBossBar(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.BossBarToggle(!player.getData(ModAttachments.BOSS_BAR_OFF)));
    }
}
