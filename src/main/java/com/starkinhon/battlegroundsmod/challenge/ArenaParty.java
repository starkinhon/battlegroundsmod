package com.starkinhon.battlegroundsmod.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

// Groups for the challenge arenas (Stark 2026-11-13: "eu e um amigo fazer uma arena junto", with an invite button the
// friend accepts if he wants). The book's Convidar lists the players of the world; the invited one gets the invite in
// the chat and in his own book (Aceitar / Recusar). Once in a group, whoever opens an arena takes the others along
// (ChallengeArenas.start), and the challenger has life times the players. Lives on the server, so it works on LAN and on
// a dedicated server alike; a group ends when its players leave it or log out.
public final class ArenaParty {
    public static final int MAX = 4;
    private static final int INVITE_TICKS = 20 * 120;

    private record Invite(UUID from, long until) {
    }

    // Each member maps to the group's shared list.
    private static final Map<UUID, List<UUID>> GROUPS = new HashMap<>();
    // Invited player -> the invite (one at a time, the newest wins).
    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    // The client's view (ModPayloads.SyncArenaParty): the names of the others in its group, and who invited it.
    public static volatile List<String> clientParty = List.of();
    public static volatile String clientInvite = "";

    private ArenaParty() {
    }

    // The others of the player's group who are online.
    public static List<ServerPlayer> others(ServerPlayer player) {
        List<ServerPlayer> list = new ArrayList<>();
        List<UUID> group = GROUPS.get(player.getUUID());
        if (group == null) {
            return list;
        }
        for (UUID id : group) {
            ServerPlayer mate = player.server.getPlayerList().getPlayer(id);
            if (mate != null && mate != player) {
                list.add(mate);
            }
        }
        return list;
    }

    public static void invite(ServerPlayer from, ServerPlayer to) {
        if (to == null || to == from) {
            return;
        }
        List<UUID> group = GROUPS.get(from.getUUID());
        if (group != null && group.contains(to.getUUID())) {
            from.displayClientMessage(Component.translatable("message.battlegroundsmod.party_already", to.getDisplayName()), true);
            return;
        }
        if (group != null && group.size() >= MAX) {
            from.displayClientMessage(Component.translatable("message.battlegroundsmod.party_full", MAX), true);
            return;
        }
        INVITES.put(to.getUUID(), new Invite(from.getUUID(), from.server.getTickCount() + INVITE_TICKS));
        String name = from.getGameProfile().getName();
        Component accept = Component.translatable("message.battlegroundsmod.party_accept_button").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/soul desafios aceitar " + name))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.battlegroundsmod.party_accept_hover"))));
        Component decline = Component.translatable("message.battlegroundsmod.party_decline_button").withStyle(s -> s.withColor(ChatFormatting.RED)
                .withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/soul desafios recusar " + name)));
        to.sendSystemMessage(Component.translatable("message.battlegroundsmod.party_invited", from.getDisplayName())
                .withStyle(ChatFormatting.GOLD).append(" ").append(accept).append(" ").append(decline));
        to.displayClientMessage(Component.translatable("message.battlegroundsmod.party_invited_bar", from.getDisplayName()), true);
        to.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.2f);
        from.displayClientMessage(Component.translatable("message.battlegroundsmod.party_sent", to.getDisplayName()), true);
        sync(to);
    }

    public static void accept(ServerPlayer to, ServerPlayer from) {
        Invite invite = INVITES.get(to.getUUID());
        if (from == null || invite == null || !invite.from().equals(from.getUUID())) {
            to.displayClientMessage(Component.translatable("message.battlegroundsmod.party_no_invite"), true);
            return;
        }
        INVITES.remove(to.getUUID());
        List<UUID> group = GROUPS.get(from.getUUID());
        if (group != null && group.size() >= MAX) {
            to.displayClientMessage(Component.translatable("message.battlegroundsmod.party_full", MAX), true);
            return;
        }
        leaveGroup(to, false);
        if (group == null) {
            group = new ArrayList<>();
            group.add(from.getUUID());
            GROUPS.put(from.getUUID(), group);
        }
        group.add(to.getUUID());
        GROUPS.put(to.getUUID(), group);
        for (ServerPlayer member : members(to.server, group)) {
            member.sendSystemMessage(Component.translatable("message.battlegroundsmod.party_joined", to.getDisplayName())
                    .withStyle(ChatFormatting.GREEN));
            member.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
            sync(member);
        }
    }

    public static void decline(ServerPlayer to, ServerPlayer from) {
        Invite invite = INVITES.remove(to.getUUID());
        sync(to);
        if (invite != null && from != null && invite.from().equals(from.getUUID())) {
            from.displayClientMessage(Component.translatable("message.battlegroundsmod.party_declined", to.getDisplayName()), false);
        }
    }

    // Out of the group (the book's button, or logging out); a group of one is no group.
    public static void leaveGroup(ServerPlayer player, boolean tell) {
        INVITES.remove(player.getUUID());
        List<UUID> group = GROUPS.remove(player.getUUID());
        if (group == null) {
            return;
        }
        group.remove(player.getUUID());
        if (group.size() == 1) {
            GROUPS.remove(group.get(0));
        }
        for (ServerPlayer member : members(player.server, group)) {
            member.sendSystemMessage(Component.translatable("message.battlegroundsmod.party_left", player.getDisplayName()).withStyle(ChatFormatting.GRAY));
            sync(member);
        }
        if (group.size() == 1) {
            group.clear();
        }
        if (tell) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.party_you_left"), true);
        }
        sync(player);
    }

    private static List<ServerPlayer> members(MinecraftServer server, List<UUID> group) {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID id : new ArrayList<>(group)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    // Invites run out after two minutes.
    static void tick(MinecraftServer server) {
        if (INVITES.isEmpty() || server.getTickCount() % 20 != 0) {
            return;
        }
        long now = server.getTickCount();
        INVITES.entrySet().removeIf(entry -> {
            if (entry.getValue().until() > now) {
                return false;
            }
            ServerPlayer to = server.getPlayerList().getPlayer(entry.getKey());
            if (to != null) {
                PacketDistributor.sendToPlayer(to, new ModPayloads.SyncArenaParty(names(to), ""));
            }
            return true;
        });
    }

    private static List<String> names(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer mate : others(player)) {
            names.add(mate.getGameProfile().getName());
        }
        return names;
    }

    public static void sync(ServerPlayer player) {
        Invite invite = INVITES.get(player.getUUID());
        ServerPlayer from = invite == null ? null : player.server.getPlayerList().getPlayer(invite.from());
        PacketDistributor.sendToPlayer(player, new ModPayloads.SyncArenaParty(names(player), from == null ? "" : from.getGameProfile().getName()));
    }

    // The book's buttons (ModPayloads.ArenaPartyAction).
    public static void action(ServerPlayer player, int action, String name) {
        ServerPlayer other = name.isEmpty() ? null : player.server.getPlayerList().getPlayerByName(name);
        switch (action) {
            case 0 -> invite(player, other);
            case 1 -> accept(player, other);
            case 2 -> decline(player, other);
            case 3 -> leaveGroup(player, true);
            default -> {
            }
        }
    }
}
