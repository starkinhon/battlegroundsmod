// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// /souls: the player's own options, client only (saved in the client config file).
// /souls kanji desativar | ativar: the kanji of Kokushibo's forms beside him (FormKanji). Stark, 2026-11-08.
// /souls panorama [tamanho]: the six faces of a menu panorama, saved to screenshots/souls_panorama (PanoramaCapture). Stark, 2026-11-22.
// /souls dungeon | dungeon sair: the roguelike mode (challenge/Dungeon), asked of the server. /dungeons | /dungeons sair: the same.
public final class SoulsCommand {
    private SoulsCommand() {
    }

    private static int panorama(CommandContext<CommandSourceStack> c, int size) {
        Minecraft mc = Minecraft.getInstance();
        if (PanoramaCapture.busy()) {
            c.getSource().sendSystemMessage(Component.translatable("message.battlegroundsmod.panorama.ocupado"));
            return 0;
        }
        if (mc.player == null || mc.getCameraEntity() != mc.player) {
            c.getSource().sendSystemMessage(Component.translatable("message.battlegroundsmod.panorama.camera"));
            return 0;
        }
        PanoramaCapture.request(size);
        c.getSource().sendSystemMessage(Component.translatable("message.battlegroundsmod.panorama.tirando"));
        return 1;
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("souls")
                .then(Commands.literal("kanji")
                        .then(Commands.literal("desativar").executes(c -> {
                            FxConfig.setFormKanji(false);
                            FormKanji.clear();
                            c.getSource().sendSystemMessage(Component.translatable("message.battlegroundsmod.kanji_off"));
                            return 1;
                        }))
                        .then(Commands.literal("ativar").executes(c -> {
                            FxConfig.setFormKanji(true);
                            c.getSource().sendSystemMessage(Component.translatable("message.battlegroundsmod.kanji_on"));
                            return 1;
                        })))
                // /souls panorama: 1024 per face by default (the size of vanilla's own), 512 to 2048 by hand
                .then(Commands.literal("panorama")
                        .executes(c -> panorama(c, 1024))
                        .then(Commands.argument("tamanho", IntegerArgumentType.integer(512, 2048))
                                .executes(c -> panorama(c, IntegerArgumentType.getInteger(c, "tamanho")))))
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
        // /dungeons: the short way to the same run (Stark 2026-11-19 calls it that)
        event.getDispatcher().register(Commands.literal("dungeons")
                .executes(c -> {
                    PacketDistributor.sendToServer(new ModPayloads.DungeonStart());
                    return 1;
                })
                .then(Commands.literal("sair").executes(c -> {
                    PacketDistributor.sendToServer(new ModPayloads.DungeonQuit());
                    return 1;
                })));
    }
}
