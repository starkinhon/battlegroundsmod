// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.starkinhon.battlegroundsmod.world.SoulsWorld;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// A downloaded map (Stark 2026-11-26: the city map he played with four friends showed "Fale com o Mestre Kame" and
// "Fale com o Gohan", and none of the mod's places are in it). Such a world plays like the flat world: every skill free,
// no missions in the corner, no progression (Progression.on). The Souls world type is never free.
//
// How a world is told apart, decided once on its first load with the mod and saved in the overworld's data:
// - a brand new world (game clock at 0) is the mod's own world;
// - a save already played with the mod (it has the mod's data or players), opened only by NeoForge, is too, so the
//   old test saves keep their progression;
// - anything else (a map made in vanilla, Fabric, Forge... or an old save with nothing of the mod) is free.
// /soul mundo shows it; /soul mundo souls | livre (ops) changes it for that world.
public final class WorldOrigin {
    private static final String FILE = "battlegroundsmod_origem";
    private static final String NEOFORGE = "neoforge";

    // The server the guess and the cache belong to (a single player game opens several worlds in a row).
    private static MinecraftServer owner;
    // What the first look at the save said, taken before anything of the mod writes its data.
    private static boolean guessFree;
    // The saved answer, once read.
    private static Boolean cached;

    private WorldOrigin() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldOrigin::onAboutToStart);
        NeoForge.EVENT_BUS.addListener(WorldOrigin::onStopped);
        NeoForge.EVENT_BUS.addListener(WorldOrigin::onLogin);
        com.starkinhon.battlegroundsmod.command.SoulCommand.addSubcommand(WorldOrigin::command);
    }

    // Whether this world is a free one (a downloaded map): no missions, no progression, every skill open.
    public static boolean free(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return server == owner && guessFree;
        }
        if (SoulsWorld.is(overworld.getChunkSource().getGenerator())) {
            return false;
        }
        if (server == owner && cached != null) {
            return cached;
        }
        Data data = data(overworld);
        if (data.mode == null) {
            data.mode = server == owner && guessFree ? Data.FREE : Data.SOULS;
            data.setDirty();
            LogUtils.getLogger().info("[souls] mundo marcado como {}", data.mode);
        }
        owner = server;
        cached = Data.FREE.equals(data.mode);
        return cached;
    }

    private static Data data(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), FILE);
    }

    public static void set(MinecraftServer server, boolean free) {
        Data data = data(server.overworld());
        data.mode = free ? Data.FREE : Data.SOULS;
        data.setDirty();
        owner = server;
        cached = free;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    // Everything the client shows that depends on it: the bar and the books, and the corner mission.
    private static void refresh(ServerPlayer player) {
        PowerLogic.sync(player);
        com.starkinhon.battlegroundsmod.universe.GuidedPath.sync(player);
        com.starkinhon.battlegroundsmod.universe.DbQuests.sync(player);
    }

    private static void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        owner = server;
        cached = null;
        guessFree = guess(server);
    }

    private static void onStopped(ServerStoppedEvent event) {
        if (event.getServer() == owner) {
            owner = null;
            cached = null;
            guessFree = false;
        }
    }

    // A free world wipes a corner mission the client may still hold from the world opened before.
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && free(player.server)) {
            com.starkinhon.battlegroundsmod.universe.GuidedPath.sync(player);
            com.starkinhon.battlegroundsmod.universe.DbQuests.sync(player);
        }
    }

    // Free unless the save is brand new, or was already played with the mod and only ever opened by NeoForge.
    private static boolean guess(MinecraftServer server) {
        WorldData world = server.getWorldData();
        if (world.overworldData().getGameTime() <= 0) {
            return false;
        }
        Set<String> brands = world.getKnownServerBrands();
        boolean onlyNeoForge = !brands.isEmpty() && brands.stream().allMatch(NEOFORGE::equalsIgnoreCase);
        boolean free = !(onlyNeoForge && playedWithMod(server));
        LogUtils.getLogger().info("[souls] mundo existente, marcas {} -> {}", brands, free ? "livre" : "souls");
        return free;
    }

    // The mod's saved data in the world, or a player who carries the mod's data.
    private static boolean playedWithMod(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        try (Stream<Path> files = Files.list(root.resolve("data"))) {
            if (files.anyMatch(p -> p.getFileName().toString().startsWith("battlegroundsmod_")
                    && !p.getFileName().toString().startsWith(FILE))) {
                return true;
            }
        } catch (IOException ignored) {
            // no data folder
        }
        try (Stream<Path> files = Files.list(server.getWorldPath(LevelResource.PLAYER_DATA_DIR))) {
            return files.filter(p -> p.getFileName().toString().endsWith(".dat")).anyMatch(WorldOrigin::carriesMod);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean carriesMod(Path file) {
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            CompoundTag attachments = tag.getCompound("neoforge:attachments");
            for (String key : attachments.getAllKeys()) {
                if (key.startsWith("battlegroundsmod:")) {
                    return true;
                }
            }
            for (String data : new String[] {"NeoForgeData", "ForgeData"}) {
                if (tag.getCompound(data).getAllKeys().stream().anyMatch(k -> k.startsWith("souls_"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    // /soul mundo: says which kind of world this is. /soul mundo souls | livre (ops): changes it.
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("mundo")
                .executes(context -> {
                    boolean free = free(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable(free
                            ? "message.battlegroundsmod.mundo_livre" : "message.battlegroundsmod.mundo_souls"), false);
                    return 1;
                })
                .then(Commands.literal("souls").requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            set(context.getSource().getServer(), false);
                            context.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.mundo_souls"), true);
                            return 1;
                        }))
                .then(Commands.literal("livre").requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            set(context.getSource().getServer(), true);
                            context.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.mundo_livre"), true);
                            return 1;
                        }));
    }

    public static final class Data extends SavedData {
        static final String SOULS = "souls";
        static final String FREE = "livre";
        String mode;

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            if (tag.contains("modo")) {
                data.mode = tag.getString("modo");
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            if (mode != null) {
                tag.putString("modo", mode);
            }
            return tag;
        }
    }
}
