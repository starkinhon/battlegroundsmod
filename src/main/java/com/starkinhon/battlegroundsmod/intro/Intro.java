package com.starkinhon.battlegroundsmod.intro;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.challenge.Dungeon;
import com.starkinhon.battlegroundsmod.command.SoulCommand;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The playable introduction (Stark 2026-11-19, roteiro in the "Introdução jogável" doc, approved preview "Introdução"):
// the first time a player enters the mod they play Madara against Hashirama at the Vale do Fim, Goku against Vegeta in the
// desert and Itadori following Gojo through Shibuya down to Sukuna, alone in their own copy of the arenas (a row of the
// arena dimensions of their own, ChallengeArenas.INTRO_Z), even with friends on the server.
// Everything is scripted (IntroScript): the enemy only does what the script casts, the life bars move step by step and a
// step only moves on when its mission is met. The real character (soul, Sincronia, inventory, place, game mode) is saved
// first and put back at the end, or at the next login after a crash or a disconnect (which also restarts the intro).
// /soul intro [jogador] plays it again, /soul intro pular [jogador] ends it.
public final class Intro {
    private static final Map<UUID, IntroScript> RUNS = new HashMap<>();

    private Intro() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, Intro::onLogin);
        NeoForge.EVENT_BUS.addListener(Intro::onLogout);
        NeoForge.EVENT_BUS.addListener(Intro::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Intro::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(Intro::onDamage);
        SoulCommand.addSubcommand(Intro::command);
    }

    // Whether this player already went through the introduction (or skipped it).
    public static boolean done(ServerPlayer player) {
        return player.getData(ModAttachments.INTRO_DONE);
    }

    // In the introduction right now (its soul's skills are gated even without progression: SoulSync).
    public static boolean active(ServerPlayer player) {
        return RUNS.containsKey(player.getUUID());
    }

    static IntroScript run(ServerPlayer player) {
        return RUNS.get(player.getUUID());
    }

    // Marks the introduction as done and tells everyone listening (IntroDoneEvent). Calling it again for a player who is
    // already marked does nothing.
    public static void finish(ServerPlayer player, boolean skipped) {
        if (done(player)) {
            return;
        }
        player.setData(ModAttachments.INTRO_DONE, true);
        NeoForge.EVENT_BUS.post(new IntroDoneEvent(player, skipped));
    }

    // ---- start and end

    public static boolean start(ServerPlayer player) {
        if (active(player) || Dungeon.inRun(player) || ChallengeArenas.isArena(player.level().dimension()) || !player.isAlive()) {
            return false;
        }
        int row = freeRow();
        if (row < 0) {
            return false;
        }
        // the real character, before anything is lent
        player.setData(ModAttachments.INTRO_BACKUP, Dungeon.capture(player));
        player.setData(ModAttachments.INTRO_GAMEMODE, player.gameMode.getGameModeForPlayer().getId());
        IntroScript run = new IntroScript(player, row);
        RUNS.put(player.getUUID(), run);
        BattlegroundsMod.LOGGER.info("[intro] {} começou (fileira {})", player.getGameProfile().getName(), row);
        return true;
    }

    // The scenes are over (or skipped): the character back, the row free.
    static void end(ServerPlayer player, boolean markDone, boolean skipped) {
        IntroScript run = RUNS.remove(player.getUUID());
        if (run != null) {
            run.dispose();
        }
        restore(player);
        ui(player, "clear", "", 0, 0);
        if (markDone) {
            finish(player, skipped);
        }
    }

    private static void restore(ServerPlayer player) {
        Dungeon.Backup b = player.getData(ModAttachments.INTRO_BACKUP);
        int mode = player.getData(ModAttachments.INTRO_GAMEMODE);
        if (!b.active()) {
            return;
        }
        player.setData(ModAttachments.INTRO_BACKUP, Dungeon.Backup.EMPTY);
        player.setData(ModAttachments.INTRO_GAMEMODE, -1);
        Dungeon.putBack(player, b);
        if (mode >= 0) {
            player.setGameMode(GameType.byId(mode));
        }
    }

    private static int freeRow() {
        boolean[] used = new boolean[ChallengeArenas.INTRO_ROWS];
        for (IntroScript run : RUNS.values()) {
            used[run.row] = true;
        }
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return -1;
    }

    // ---- the client

    static void ui(ServerPlayer player, String kind, String id, int a, int b) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.IntroUi(kind, id, a, b));
    }

    // The teaching card was closed (ModPayloads.IntroAck).
    public static void ack(ServerPlayer player, String kind) {
        IntroScript run = RUNS.get(player.getUUID());
        if (run != null && "card".equals(kind)) {
            run.cardClosed();
        }
    }

    // ---- events

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // a crash or a closed game in the middle: the character back first
        restore(player);
        // only in a world to live in: the Souls world type or the normal one (Stark 2026-11-19). Never in a superflat
        // world (the test/fight mode); /soul intro still plays it anywhere.
        if (!done(player) && !com.starkinhon.battlegroundsmod.power.Progression.flatWorld(player.server)) {
            // a moment for the world to show up before the screen goes dark
            pendingStart.put(player.getUUID(), player.server.getTickCount() + 60);
        }
    }

    private static final Map<UUID, Integer> pendingStart = new HashMap<>();

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            pendingStart.remove(player.getUUID());
            if (active(player)) {
                // not done: it plays again from the start next time
                end(player, false, false);
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!pendingStart.isEmpty()) {
            int now = server.getTickCount();
            pendingStart.entrySet().removeIf(e -> {
                if (now < e.getValue()) {
                    return false;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                if (player != null && !done(player) && !active(player) && !start(player)) {
                    return false;       // busy (a Dungeon, an arena): tries again
                }
                return true;
            });
        }
        if (RUNS.isEmpty()) {
            return;
        }
        for (IntroScript run : RUNS.values().toArray(IntroScript[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(run.playerId);
            if (player == null) {
                RUNS.remove(run.playerId);
                run.dispose();
                continue;
            }
            try {
                run.tick(player);
            } catch (RuntimeException e) {
                // never leave someone stuck in the void with a borrowed soul
                BattlegroundsMod.LOGGER.error("[intro] erro no roteiro de {}", player.getGameProfile().getName(), e);
                end(player, true, true);
            }
        }
    }

    // Life under control: in the intro nobody dies, and the hits on the player are capped (the script decides how it goes).
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (RUNS.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IntroScript run = RUNS.get(player.getUUID());
        if (run == null) {
            return;
        }
        if (!run.vulnerable()) {
            event.setCanceled(true);
            return;
        }
        float max = player.getMaxHealth();
        float amount = Math.min(event.getAmount(), max * 0.12f);
        amount = Math.min(amount, Math.max(0, player.getHealth() - max * 0.3f));
        if (amount <= 0) {
            event.setCanceled(true);
            return;
        }
        event.setAmount(amount);
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (RUNS.isEmpty() || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IntroScript run = RUNS.get(player.getUUID());
        LivingEntity hurt = event.getEntity();
        if (run != null && hurt != player) {
            run.hit(hurt, event.getNewDamage());
        }
    }

    // ---- /soul intro [jogador], /soul intro pular [jogador]

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("intro").requires(s -> s.hasPermission(2))
                .executes(c -> replay(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(c -> replay(c.getSource(), EntityArgument.getPlayer(c, "jogador"))))
                .then(Commands.literal("pular")
                        .executes(c -> skip(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(c -> skip(c.getSource(), EntityArgument.getPlayer(c, "jogador")))));
    }

    private static int replay(CommandSourceStack source, ServerPlayer player) {
        if (active(player)) {
            end(player, false, false);
        }
        if (!start(player)) {
            source.sendFailure(Component.translatable("intro.battlegroundsmod.cmd.busy"));
            return 0;
        }
        return 1;
    }

    private static int skip(CommandSourceStack source, ServerPlayer player) {
        pendingStart.remove(player.getUUID());
        if (active(player)) {
            end(player, true, true);
        } else {
            finish(player, true);
        }
        source.sendSuccess(() -> Component.translatable("intro.battlegroundsmod.cmd.skipped", player.getDisplayName()), true);
        return 1;
    }
}
