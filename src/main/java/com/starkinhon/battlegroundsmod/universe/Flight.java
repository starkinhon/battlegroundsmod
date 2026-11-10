package com.starkinhon.battlegroundsmod.universe;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Voo (Bukujutsu), the Dragon Ball technique (Stark 2026-11-10: universe techniques are the player's, they work in every
// universe). Learned from Gohan (npc/Npcs). Once learned: jump twice to take off, like the creative flight; flying
// burns hunger (Goku eats for a reason) and lands the player when the hunger bar runs low. Faster with each tier.
public final class Flight {
    public static final String TECHNIQUE = "voo";
    private static final int MIN_FOOD = 6;

    private Flight() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Flight::onTick);
    }

    public static boolean knows(ServerPlayer player) {
        return player.getData(ModAttachments.TECHNIQUES).contains(TECHNIQUE);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator() || !knows(player)) {
            return;
        }
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        if (!abilities.mayfly) {
            abilities.mayfly = true;
            changed = true;
        }
        float speed = 0.04f + 0.007f * (Tiers.HUMANO - Tiers.of(player));
        if (Math.abs(abilities.getFlyingSpeed() - speed) > 1e-4) {
            abilities.setFlyingSpeed(speed);
            changed = true;
        }
        if (abilities.flying) {
            player.causeFoodExhaustion(player.isSprinting() ? 0.05f : 0.025f);
            if (player.getFoodData().getFoodLevel() <= MIN_FOOD) {
                abilities.flying = false;
                changed = true;
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.flight_tired"), true);
            } else if (player.tickCount % 3 == 0) {
                Fx.burst(player.serverLevel(), ParticleTypes.CLOUD, player.position(), 1, 0.15, 0.01);
            }
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }
}
