// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

// "Trocar de alma" (Stark 2026-11-21): once the player has taken a second soul, the next time the Livro das Almas opens a
// card tells them the old one is still theirs and they can swap between the souls they own whenever they want. Once per
// player (ModAttachments.SOUL_SWAP_HINT). Only in the real game: progression on, the normal world (Souls world or
// Overworld), outside the introduction, a Dungeon run and the challenge arenas. Elsewhere the book opens as always and the
// card waits for the next time in the normal world.
public final class SoulSwapHint {
    private SoulSwapHint() {
    }

    // The client opened the book (ModPayloads.SoulSwapHint): answer with the card when it is time.
    public static void bookOpened(ServerPlayer player) {
        if (player.getData(ModAttachments.SOUL_SWAP_HINT) || !eligible(player)) {
            return;
        }
        player.setData(ModAttachments.SOUL_SWAP_HINT, true);
        PacketDistributor.sendToPlayer(player, new ModPayloads.SoulSwapHint());
    }

    private static boolean eligible(ServerPlayer player) {
        return Progression.on(player)
                && !player.isCreative() && !player.isSpectator()
                && player.level().dimension() == Level.OVERWORLD
                && !com.starkinhon.battlegroundsmod.intro.Intro.active(player)
                && !com.starkinhon.battlegroundsmod.challenge.Dungeon.inRun(player)
                && !com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.isArena(player.level().dimension())
                && souls(player) >= 2;
    }

    // Souls the book lets this player put back on: Divine Souls and SubAlmas worn or absorbed at least once. The player's
    // own kit and the techniques a master taught are in the same map but are not souls.
    private static int souls(ServerPlayer player) {
        int n = 0;
        for (String id : player.getData(ModAttachments.SOUL_SYNC).keySet()) {
            Power p = Powers.get(id);
            if (p != null && (Powers.divine().contains(p) || Powers.sub().contains(p))) {
                n++;
            }
        }
        return n;
    }
}
