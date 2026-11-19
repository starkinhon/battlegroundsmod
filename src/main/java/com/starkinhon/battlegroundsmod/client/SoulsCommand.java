package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// /souls: the player's own options, client only (saved in the client config file).
// /souls kanji desativar | ativar: the kanji of Kokushibo's forms beside him (FormKanji). Stark, 2026-11-08.
// /souls dungeon | dungeon sair: the roguelike mode (challenge/Dungeon), asked of the server.
public final class SoulsCommand {
    private SoulsCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("souls")
                .then(Commands.literal("kanji")
                        .then(Commands.literal("desativar").executes(c -> {
                            FxConfig.setFormKanji(false);
                            FormKanji.clear();
                            c.getSource().sendSystemMessage(Component.literal("Kanji das formas desativado."));
                            return 1;
                        }))
                        .then(Commands.literal("ativar").executes(c -> {
                            FxConfig.setFormKanji(true);
                            c.getSource().sendSystemMessage(Component.literal("Kanji das formas ativado."));
                            return 1;
                        })))
                // /souls dungeon: the roguelike (challenge/Dungeon). /souls dungeon sair gives it up. Both go to the server.
                .then(Commands.literal("dungeon")
                        .executes(c -> {
                            PacketDistributor.sendToServer(new ModPayloads.DungeonStart());
                            return 1;
                        })
                        .then(Commands.literal("sair").executes(c -> {
                            PacketDistributor.sendToServer(new ModPayloads.DungeonQuit());
                            return 1;
                        }))));
    }
}
