package com.starkinhon.battlegroundsmod.intro;

import com.starkinhon.battlegroundsmod.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

// The playable introduction (Stark 2026-11-19, roteiro in the "Introdução jogável" doc): the first time a player enters
// the mod they play Madara against Hashirama at the Vale do Fim, Goku against Vegeta in the desert and Itadori following
// Gojo through Shibuya down to Sukuna, alone in their own copy. Only the hook is here so far (the flag and the event other
// systems listen to); the scenes come after the preview is approved.
public final class Intro {
    private Intro() {
    }

    // Whether this player already went through the introduction (or skipped it).
    public static boolean done(ServerPlayer player) {
        return player.getData(ModAttachments.INTRO_DONE);
    }

    // Marks the introduction as done and tells everyone listening (IntroDoneEvent). Called once, after the logo or the
    // skip; calling it again for a player who is already marked does nothing.
    public static void finish(ServerPlayer player, boolean skipped) {
        if (done(player)) {
            return;
        }
        player.setData(ModAttachments.INTRO_DONE, true);
        NeoForge.EVENT_BUS.post(new IntroDoneEvent(player, skipped));
    }
}
