package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.skill.MedoSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// What the local player feels under the MEDO effect: a light constant screen shake and a racing heartbeat.
public final class Fear {
    private static int beat;

    private Fear() {
    }

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.hasEffect(ModEffects.MEDO)) {
            beat = 0;
            return;
        }
        MedoSpec spec = MedoSpec.get();
        ScreenShake.start(spec.tremor(), 3);
        if (beat-- <= 0) {
            beat = Math.max(4, spec.batimentoTicks());
            player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                    spec.volumeBatimento(), spec.pitchBatimento(), false);
        }
    }
}
