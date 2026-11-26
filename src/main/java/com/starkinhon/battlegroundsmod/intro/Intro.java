// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
        NeoForge.EVENT_BUS.addListener(Intro::onTeleportCommand);
        NeoForge.EVENT_BUS.addListener(Intro::onSpectatorTeleport);
        // nothing of one world's scenes carries to the next one opened in the same game (issue #379)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            RUNS.clear();
            pendingStart.clear();
            pendingHidden.clear();
            asking.clear();
        });
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

    // Nobody teleports to someone in the introduction, and nobody in it teleports to someone outside it (Stark 2026-11-25:
    // "não deve dar pra dar tp no player que tá no tutorial"). For the mod's own teleports to a target (Ctrl 2x).
    public static boolean teleportBlocked(net.minecraft.world.entity.Entity who, net.minecraft.world.entity.Entity target) {
        if (RUNS.isEmpty() || who == target) {
            return false;
        }
        boolean whoIn = who instanceof ServerPlayer a && active(a);
        boolean targetIn = target instanceof ServerPlayer b && active(b);
        return targetIn || whoIn && target instanceof ServerPlayer;
    }

    // How close to someone in the introduction a /tp may not land (their scene is theirs alone).
    private static final double TELEPORT_KEEP_OUT = 96;

    // Whether a teleport of this entity to this spot goes into someone's scene, or takes someone out of theirs.
    private static boolean intrudes(net.minecraft.world.entity.Entity entity, double x, double y, double z) {
        if (RUNS.isEmpty()) {
            return false;
        }
        if (entity instanceof ServerPlayer p && active(p)) {
            return true;
        }
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return false;
        }
        for (IntroScript run : RUNS.values()) {
            ServerPlayer p = server.getPlayerList().getPlayer(run.playerId);
            if (p != null && p != entity && p.distanceToSqr(x, y, z) < TELEPORT_KEEP_OUT * TELEPORT_KEEP_OUT) {
                return true;
            }
        }
        return false;
    }

    // /tp and /teleport: not to someone in the introduction, not of someone in it.
    private static void onTeleportCommand(net.neoforged.neoforge.event.entity.EntityTeleportEvent.TeleportCommand event) {
        if (intrudes(event.getEntity(), event.getTargetX(), event.getTargetY(), event.getTargetZ())) {
            event.setCanceled(true);
        }
    }

    // A spectator's click on a player in the spectator menu: same rule.
    private static void onSpectatorTeleport(net.neoforged.neoforge.event.entity.EntityTeleportEvent.SpectatorTeleport event) {
        if (intrudes(event.getEntity(), event.getTargetX(), event.getTargetY(), event.getTargetZ())) {
            event.setCanceled(true);
        }
    }

    // The arenas keep their blocks while a scene is on in them (combat/BlockRules): no hole to fall through.
    // And the real Shibuya while a scene is played there (its last act): the duel's domains leave the city standing.
    public static boolean protectsBlocks(net.minecraft.world.level.Level level) {
        if (RUNS.isEmpty()) {
            return false;
        }
        if (ChallengeArenas.isArena(level.dimension())) {
            return true;
        }
        for (IntroScript run : RUNS.values()) {
            if (run.protects(level)) {
                return true;
            }
        }
        return false;
    }

    // Every scene playing now (IntroScript: two never share a Shibuya station).
    static java.util.Collection<IntroScript> runs() {
        return RUNS.values();
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
        // one Inseto de Alma at the end (Stark 2026-11-19): eat it for a hunger bar always full, or keep it. Once per player
        // (INTRO_DONE is set just above), not for one who already ate one.
        if (!player.getData(ModAttachments.SOUL_FED)) {
            net.minecraft.world.item.ItemStack insect = new net.minecraft.world.item.ItemStack(
                    com.starkinhon.battlegroundsmod.ModItems.SOUL_INSECT.get());
            if (!player.getInventory().add(insect)) {
                player.drop(insect, false);
            }
        }
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
        ui(player, "run", "", 1, 0);
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
        // the lent Controle de Energia off the client too (it moves the player on the water itself)
        com.starkinhon.battlegroundsmod.universe.EnergyControl.sync(player);
        // and the desert's lent flight off, unless the Gohan had already taught it (the backup's techniques)
        com.starkinhon.battlegroundsmod.universe.Flight.revoke(player);
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
        } else if ("sim".equals(kind) || "nao".equals(kind)) {
            answer(player, "sim".equals(kind));
        }
    }

    // ---- events

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // a crash or a closed game in the middle: the character back first
        restore(player);
        // a save from before the fix (Stark 2026-11-19): the intro is done but the desert's flight stayed with the
        // player without the technique
        if (done(player)) {
            com.starkinhon.battlegroundsmod.universe.Flight.revoke(player);
        }
        // only in a world to live in: the Souls world type or the normal one (Stark 2026-11-19). Never in a superflat
        // world (the test/fight mode); /soul intro still plays it anywhere.
        if (!done(player) && !com.starkinhon.battlegroundsmod.power.Progression.flatWorld(player.server)) {
            // asked first (Stark 2026-11-20): "Deseja fazer o Tutorial de Souls?" Sim | Não (pular). No answer yet (the
            // game closed on the question) asks again next time.
            asking.add(player.getUUID());
            ui(player, "ask", "", 1, 0);
        }
    }

    private static final Map<UUID, Integer> pendingStart = new HashMap<>();
    // players whose pending start failed once (busy) and already had their fade cleared, so we do not resend every tick
    private static final java.util.Set<UUID> pendingHidden = new java.util.HashSet<>();
    // players looking at the question right now: only their answer counts (a stray packet starts or skips nothing)
    private static final java.util.Set<UUID> asking = new java.util.HashSet<>();

    // The answer to the question (ModPayloads.IntroAck "sim"/"nao").
    private static void answer(ServerPlayer player, boolean yes) {
        if (!asking.remove(player.getUUID()) || done(player) || active(player)) {
            return;
        }
        if (yes) {
            // dark right away, a moment for the world behind it, then the scenes
            pendingHidden.remove(player.getUUID());
            pendingStart.put(player.getUUID(), player.server.getTickCount() + 20);
            ui(player, "black", "", 1, 1);
            ui(player, "run", "", 1, 0);
        } else {
            // nothing was lent yet: only marked done (and the Inseto de Alma, like the end of the scenes)
            finish(player, true);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            pendingStart.remove(player.getUUID());
            pendingHidden.remove(player.getUUID());
            asking.remove(player.getUUID());
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
                    if (pendingHidden.add(e.getKey())) {
                        ui(player, "black", "", 0, 0);
                        ui(player, "run", "", 0, 0);
                    }
                    return false;       // busy (a Dungeon, an arena): tries again
                }
                pendingHidden.remove(e.getKey());
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
        // anyone may skip their own intro (a LAN guest without cheats too); replaying it, or acting on someone else, is
        // for operators (issue #379)
        return Commands.literal("intro")
                .executes(c -> c.getSource().hasPermission(2) ? replay(c.getSource(), c.getSource().getPlayerOrException()) : 0)
                .then(Commands.argument("jogador", EntityArgument.player()).requires(s -> s.hasPermission(2))
                        .executes(c -> replay(c.getSource(), EntityArgument.getPlayer(c, "jogador"))))
                .then(Commands.literal("pular")
                        .executes(c -> skip(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("jogador", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(c -> skip(c.getSource(), EntityArgument.getPlayer(c, "jogador")))));
    }

    private static int replay(CommandSourceStack source, ServerPlayer player) {
        if (asking.remove(player.getUUID())) {
            ui(player, "clear", "", 0, 0);
        }
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
        if (asking.remove(player.getUUID())) {
            ui(player, "clear", "", 0, 0);
        }
        if (active(player)) {
            end(player, true, true);
        } else {
            finish(player, true);
        }
        source.sendSuccess(() -> Component.translatable("intro.battlegroundsmod.cmd.skipped", player.getDisplayName()), true);
        return 1;
    }
}
