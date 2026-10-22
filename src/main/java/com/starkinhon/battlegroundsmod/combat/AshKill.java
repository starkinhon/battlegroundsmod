package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

// Fire hits that turn their victim to ash: if this hit kills, the clients draw the body charring and crumbling away
// on the wind (client/fx/AshDeath) instead of the normal death.
public final class AshKill {
    private AshKill() {
    }

    public static boolean hurt(LivingEntity attacker, LivingEntity target, float damage) {
        boolean wasAlive = target.isAlive() && !target.isDeadOrDying();
        boolean hit = Combat.hurt(attacker, target, damage);
        if (wasAlive && target.isDeadOrDying()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.AshDeath(target.getId()));
        }
        return hit;
    }
}
