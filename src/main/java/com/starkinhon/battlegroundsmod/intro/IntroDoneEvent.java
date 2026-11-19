package com.starkinhon.battlegroundsmod.intro;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

// Posted on NeoForge.EVENT_BUS (server side) when a player finishes the playable introduction, right after the
// SOULS BATTLEGROUNDS logo, or skips it. By then the player is back in their world with their own things, so the first
// guided mission can start from here (thread "Guiar o jogador depois da introdução", 2026-11-19).
public class IntroDoneEvent extends Event {
    private final ServerPlayer player;
    private final boolean skipped;

    public IntroDoneEvent(ServerPlayer player, boolean skipped) {
        this.player = player;
        this.skipped = skipped;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    // true when the player skipped it (pause menu button) instead of playing to the end
    public boolean isSkipped() {
        return skipped;
    }
}
