package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

// The charge bar (client/ChargeBar): how far the attack the player is holding has charged. Every held attack (the
// Ataque Forte, the giant Katon, the Blue, the Red, the Purple, the Black Box...) calls report each tick it is held
// with its progress, 0 to 1 (1 = fully charged). When no attack reports for a couple of ticks the charge is over.
// Server side, players only (bosses don't see bars); sent to the player only when the value changes.
public final class Charge {
    private Charge() {
    }

    public static void report(LivingEntity entity, double progress) {
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        PowerData data = entity.getData(ModAttachments.POWER);
        data.chargeProgress = (float) Mth.clamp(progress, 0, 1);
        data.chargeReportedAt = entity.level().getGameTime();
    }

    // Every tick on the server, after the moves ran.
    public static void tick(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        boolean charging = data.chargeReportedAt >= player.level().getGameTime() - 1;
        float value = charging ? data.chargeProgress : -1;
        float sent = data.chargeSent;
        boolean changed = (value < 0) != (sent < 0) || Math.abs(value - sent) >= 0.01f || value >= 1 && sent < 1;
        if (changed) {
            data.chargeSent = value;
            PacketDistributor.sendToPlayer(player, new ModPayloads.SyncCharge(value));
        }
    }
}
