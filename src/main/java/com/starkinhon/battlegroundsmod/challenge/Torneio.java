// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.challenge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The Tournament of Power (Stark 2026-11-26: "a arena do Torneio do Poder... bem grande, no vácuo do espaço e bem
// destrutível"; a mode for himself, to fight his friends and try the powers). Its own dimension, empty space with a
// nebula sky (client/TorneioSky), and the giant top of tools/arenas/arena_torneio.py in the middle (the same blocks the
// web preview shows; the solid rock inside the top comes with the chunks, TorneioGenerator). /soul torneio takes you
// there, /soul torneio sair back where you were, /soul torneio refazer puts the arena back as new.
//
// No wall: whoever falls off the stage drops into the void and comes back on the stage (the anime's ring-out, without
// dying). Stark 2026-11-26: no drifting debris (too heavy for the PC), the arena breaks like the others.
public final class Torneio {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "torneio"));
    public static final String ARENA = "torneio";
    // the stage's floor: the arena goes from about 440 under it (the point of the top) to 340 over it (the big moon);
    // the dimension is 1024 high (dimension_type/torneio.json)
    public static final int FLOOR_Y = 560;
    // under the point of the top: fell off
    private static final int FALL_Y = FLOOR_Y - 320;

    private static final String BACK_LEVEL = "souls_torneio_volta_mundo";
    private static final String BACK_POS = "souls_torneio_volta";

    private static ChallengeArenas.Placer build;
    private static boolean ready;
    // who is waiting for the arena to be ready to go in
    private static final Set<UUID> WAITING = new LinkedHashSet<>();

    private Torneio() {
    }

    public static BlockPos center() {
        return new BlockPos(0, FLOOR_Y, 0);
    }

    public static boolean isHere(Level level) {
        return level != null && level.dimension() == LEVEL;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Torneio::onServerTick);
        TorneioMatch.register();
        com.starkinhon.battlegroundsmod.command.SoulCommand.addSubcommand(Torneio::command);
    }

    public static void resetStatics() {
        if (build != null) {
            build.cancel();
            build = null;
        }
        ready = false;
        WAITING.clear();
        TorneioMatch.resetStatics();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("torneio")
                .executes(c -> {
                    go(c.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("menu").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (p.level().dimension() == LEVEL) {
                        openMenu(p);
                    }
                    return 1;
                }))
                .then(Commands.literal("parar").executes(c -> {
                    TorneioMatch.stop(c.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("sair").executes(c -> {
                    leave(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("refazer").requires(s -> s.hasPermission(2)).executes(c -> {
                    rebuild(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.torneio.refazendo"), false);
                    return 1;
                }));
    }

    // ---------- going in and out ----------

    public static void go(ServerPlayer player) {
        ServerLevel level = player.server.getLevel(LEVEL);
        if (level == null) {
            return;
        }
        if (player.level() == level) {
            if (!TorneioMatch.running()) {
                toStage(player, level);
                openMenu(player);
            }
            return;
        }
        var tag = player.getPersistentData();
        tag.putString(BACK_LEVEL, player.level().dimension().location().toString());
        tag.putLong(BACK_POS, player.blockPosition().asLong());
        if (!ready && built(level)) {
            ready = true;
        }
        if (ready) {
            arrive(player, level);
            return;
        }
        WAITING.add(player.getUUID());
        startBuild(player.server);
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.torneio.montando", percent()), true);
    }

    public static void leave(ServerPlayer player) {
        WAITING.remove(player.getUUID());
        var tag = player.getPersistentData();
        ServerLevel to = null;
        BlockPos at = null;
        if (tag.contains(BACK_POS)) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString(BACK_LEVEL));
            to = dim == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            at = BlockPos.of(tag.getLong(BACK_POS));
        }
        if (to == null || to.dimension() == LEVEL) {
            to = player.server.overworld();
            at = to.getSharedSpawnPos();
        }
        if (player.level().dimension() != LEVEL) {
            return;
        }
        TorneioMatch.restore(player);
        player.teleportTo(to, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        tag.remove(BACK_POS);
        tag.remove(BACK_LEVEL);
    }

    private static void arrive(ServerPlayer player, ServerLevel level) {
        toStage(player, level);
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.torneio.titulo")
                .withStyle(s -> s.withColor(0xE6B4FF).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.torneio.sub")));
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 0.7f);
        if (!TorneioMatch.running()) {
            openMenu(player);
        }
    }

    // The menu where the tournament is set up (client/TorneioScreen).
    static void openMenu(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.starkinhon.battlegroundsmod.network.ModPayloads.TorneioOpen());
    }

    // The gods' balcony (the stands, Stark 2026-11-26: "a arquibancada tem que ser indestrutível"): the box round it, as
    // built by arena_torneio.py (radius RA + 4K, 9K wide, floor 34K over the stage, from 200 to 330 degrees).
    public static boolean balcony(BlockPos pos) {
        int y = pos.getY() - FLOOR_Y;
        int yb = 34 * TorneioGenerator.K;
        if (y < yb - 10 || y > yb + 14) {
            return false;
        }
        double r = Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ());
        double rb = TorneioGenerator.RA + 4 * TorneioGenerator.K;
        if (Math.abs(r - rb) > 4.5 * TorneioGenerator.K + 1.5) {
            return false;
        }
        double ang = Math.toDegrees(Math.atan2(pos.getZ(), pos.getX()));
        ang = ((ang % 360) + 360) % 360;
        return ang >= 198 && ang <= 332;
    }

    // A spot on the stage with ground under it: on the ring where the players start, else anywhere on the stage, else
    // on top of the pillar.
    static void toStage(ServerPlayer player, ServerLevel level) {
        ArenaTemplate.Meta meta = ArenaTemplate.meta(ARENA);
        int ring = meta == null ? 60 : Math.abs(meta.jogador()[2]);
        int R = meta == null ? 400 : meta.raio() - 12;
        var rng = player.getRandom();
        BlockPos spot = null;
        for (int i = 0; i < 40 && spot == null; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = i < 20 ? ring : 30 + rng.nextDouble() * (R - 30);
            spot = ground(level, (int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r));
        }
        if (spot == null) {
            spot = ground(level, 0, 0);
        }
        if (spot == null) {
            spot = center().above(2);
        }
        // facing the middle of the stage
        float yaw = (float) (Math.toDegrees(Math.atan2(-(0 - spot.getX()), 0 - spot.getZ())));
        player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 5f);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }

    // The first free spot over solid ground in this column of the stage (from the pillar's top down to the stage's rock).
    static BlockPos ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, FLOOR_Y + 150, z);
        for (; p.getY() > FLOOR_Y - 12; p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.isSolidRender(level, p)) {
                BlockPos up = p.above();
                if (level.getBlockState(up).isAir() && level.getBlockState(up.above()).isAir()) {
                    return up;
                }
                return null;
            }
        }
        return null;
    }

    // ---------- putting the arena up ----------

    // The arena was already put up in this world (its painted floor is there): no need to place it again. A broken arena
    // stays broken until /soul torneio refazer.
    private static boolean built(ServerLevel level) {
        BlockState s = level.getBlockState(center().offset(80, 0, 0));
        return !s.isAir() && !s.is(com.starkinhon.battlegroundsmod.ModBlocks.TORNEIO_ROCK.get());
    }

    private static void startBuild(MinecraftServer server) {
        if (build != null) {
            return;
        }
        ServerLevel level = server.getLevel(LEVEL);
        if (level == null) {
            return;
        }
        build = ChallengeArenas.place(ARENA, level, center());
        if (build == null) {
            BattlegroundsMod.LOGGER.error("[torneio] sem a arena torneio.bin");
        }
    }

    private static void rebuild(MinecraftServer server) {
        if (build != null) {
            build.cancel();
            build = null;
        }
        ready = false;
        startBuild(server);
    }

    private static int percent() {
        return ready ? 100 : build == null ? 0 : build.percent();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(LEVEL);
        if (level == null) {
            return;
        }
        if (build != null) {
            if (build.step()) {
                build = null;
                ready = true;
                // everyone already there (after /soul torneio refazer) back on the new stage
                for (ServerPlayer p : new ArrayList<>(level.players())) {
                    toStage(p, level);
                }
                for (UUID id : WAITING) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) {
                        arrive(p, level);
                    }
                }
                WAITING.clear();
            } else if (server.getTickCount() % 10 == 0) {
                for (UUID id : WAITING) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) {
                        p.displayClientMessage(Component.translatable("message.battlegroundsmod.torneio.montando", percent()), true);
                    }
                }
                for (ServerPlayer p : level.players()) {
                    p.displayClientMessage(Component.translatable("message.battlegroundsmod.torneio.montando", percent()), true);
                }
            }
        }
        // fell off the stage: back on it (the ring-out, without dying; in a match TorneioMatch takes them out first)
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (p.getY() < FALL_Y && !p.isSpectator()) {
                toStage(p, level);
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.torneio.fora")
                        .withStyle(s -> s.withColor(0xFF6A5A).withBold(true))));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            }
        }
    }
}
