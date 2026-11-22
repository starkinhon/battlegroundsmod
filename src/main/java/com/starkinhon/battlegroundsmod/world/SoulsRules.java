// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.world;

import java.util.List;

import com.starkinhon.battlegroundsmod.boss.BossRules;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.clash.Clash;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.OniBody;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;

// The server side of the Experiência do jogador menu (client/SoulsSettingsScreen, Stark 2026-11-22):
// - the Souls game rules the "Mundo" tab shows, which only the world's owner (single player, or the host of a LAN
//   world) or an operator can change; the server checks it on every change, whatever the client says;
// - the player's own choice of the missions' auto tracking, kept in their data (the client tells it on login and on
//   every change).
public final class SoulsRules {
    // set when the player turned the auto tracking off (absent = on, as before the menu); kept on respawn
    // (StructureMissions.KEEP)
    public static final String AUTO_TRACK_OFF = "souls_sem_rastreio_auto";

    private SoulsRules() {
    }

    // The rules of the "Mundo" tab, in the order of the bits the client gets (and of the tab's rows).
    public static List<GameRules.Key<GameRules.BooleanValue>> rules() {
        return List.of(BlockRules.SOULS_BLOCK_DESTRUCTION, Clash.RULE, BossRules.CHAOS, OniBody.PASSIVE);
    }

    public static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    // The rules' values and whether this player can change them.
    public static void send(ServerPlayer player) {
        GameRules gameRules = player.serverLevel().getGameRules();
        List<GameRules.Key<GameRules.BooleanValue>> rules = rules();
        int bits = 0;
        for (int i = 0; i < rules.size(); i++) {
            if (gameRules.getBoolean(rules.get(i))) {
                bits |= 1 << i;
            }
        }
        PacketDistributor.sendToPlayer(player, new ModPayloads.SoulsRulesSync(bits, canEdit(player)));
    }

    // From the menu: a rule changed (index < 0 only asks for the values).
    public static void set(ServerPlayer player, int index, boolean value) {
        List<GameRules.Key<GameRules.BooleanValue>> rules = rules();
        if (index >= 0 && index < rules.size() && canEdit(player)) {
            player.serverLevel().getGameRules().getRule(rules.get(index)).set(value, player.server);
        }
        send(player);
    }

    public static boolean autoTrack(ServerPlayer player) {
        return !player.getPersistentData().getBoolean(AUTO_TRACK_OFF);
    }

    public static void setAutoTrack(ServerPlayer player, boolean on) {
        if (on) {
            player.getPersistentData().remove(AUTO_TRACK_OFF);
        } else {
            player.getPersistentData().putBoolean(AUTO_TRACK_OFF, true);
        }
    }
}
