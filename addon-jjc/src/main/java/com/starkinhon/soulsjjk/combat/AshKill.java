package com.starkinhon.soulsjjk.combat;

import com.starkinhon.soulsjjk.network.ModPayloads;

import net.minecraft.world.entity.LivingEntity;
import com.starkinhon.soulsjjk.compat.PacketDistributor;

// Fire hits that turn their victim to ash: if this hit kills, the clients draw the body charring and crumbling away
// on the wind (client/fx/AshDeath) instead of the normal death.
public final class AshKill {
    private AshKill() {
    }

    public static boolean hurt(LivingEntity attacker, LivingEntity target, float damage) {
        boolean wasAlive = target.isAlive() && !target.isDeadOrDying();
        boolean hit = Combat.hurt(attacker, target, damage);
        if (wasAlive && target.isDeadOrDying()) {
            CustomDeath.mark(target);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.AshDeath(target.getId()));
        }
        return hit;
    }
}
