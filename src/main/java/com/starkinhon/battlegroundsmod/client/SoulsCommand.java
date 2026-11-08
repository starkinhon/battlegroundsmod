package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

// /souls: the player's own options, client only (saved in the client config file).
// /souls kanji desativar | ativar: the kanji of Kokushibo's forms beside him (FormKanji). Stark, 2026-11-08.
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
                        }))));
    }
}
