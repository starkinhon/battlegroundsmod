// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

// Minecraft's Ctrl + B turns the narrator on or off. In the mod the run is on Ctrl and a skill is on B, so the narrator
// kept turning on in the middle of fights (Stark 2026-11-26). The first time a world is joined, the shortcut is turned
// off in Minecraft's own option (Acessibilidade > Atalho do narrador) and a narrator the shortcut left on is turned off
// too. A flag in our client config keeps it from ever happening again, so a player who turns either back on keeps it.
public final class NarratorSetup {
    private NarratorSetup() {
    }

    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (FxConfig.get(FxConfig.NARRATOR_SETUP)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.narratorHotkey().set(false);
        if (minecraft.options.narrator().get() != NarratorStatus.OFF) {
            minecraft.options.narrator().set(NarratorStatus.OFF);
        }
        minecraft.options.save();
        FxConfig.set(FxConfig.NARRATOR_SETUP, true);
    }
}
