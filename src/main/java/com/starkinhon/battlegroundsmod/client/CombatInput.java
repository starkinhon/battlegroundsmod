package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// The guard key, held in combat mode. Tells the server when it goes down and up (Defense). F is also vanilla's swap
// hands key, so in combat mode that swap is skipped while both share the key.
public final class CombatInput {
    private static boolean guarding = false;

    private CombatInput() {
    }

    // Before the vanilla keys are handled this tick.
    public static void tickPre() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            guarding = false;
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        boolean combat = data.hasPower() && data.combat;
        if (combat && Keybinds.GUARD.same(minecraft.options.keySwapOffhand)) {
            while (minecraft.options.keySwapOffhand.consumeClick()) {
                // Eaten: the key is the guard here.
            }
        }
        boolean want = combat && minecraft.screen == null && Keybinds.GUARD.isDown();
        if (want != guarding) {
            guarding = want;
            PacketDistributor.sendToServer(new ModPayloads.Guard(want));
        }
    }
}
