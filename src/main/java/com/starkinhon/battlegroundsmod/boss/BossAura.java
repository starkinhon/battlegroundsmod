// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Tasks;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

// The two end-game bosses keep whoever fights them on the ground (Stark, 2026-11-20): flying over Freeza or Hashirama
// skipped every move made for the ground. Near a living one (64 blocks around, 96 up) the Voo and Gaara's sand platform
// don't take off, and whoever was in the air comes down slowly, with "A aura de Freeza não te deixa voar".
// Creative and spectator are left alone. The look around is cached per player for half a second.
public final class BossAura {
    private static final double RANGE = 64;
    private static final double UP = 96;
    private static final int RECHECK = 10;
    private static final Map<UUID, Long> CHECKED_AT = new HashMap<>();
    private static final Map<UUID, String> NEAR = new HashMap<>();
    private static final Map<UUID, Long> SAID_AT = new HashMap<>();

    private BossAura() {
    }

    // The id of the boss whose aura holds this entity down ("freeza_total", "hashirama"), or null.
    public static String holder(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
            return null;
        }
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        Long at = CHECKED_AT.get(id);
        if (at == null || now - at >= RECHECK || now < at) {
            CHECKED_AT.put(id, now);
            String found = look(player);
            if (found == null) {
                NEAR.remove(id);
            } else {
                NEAR.put(id, found);
            }
        }
        return NEAR.get(id);
    }

    public static boolean grounded(LivingEntity entity) {
        return holder(entity) != null;
    }

    private static String look(ServerPlayer player) {
        AABB box = new AABB(player.getX() - RANGE, player.getY() - UP, player.getZ() - RANGE,
                player.getX() + RANGE, player.getY() + 16, player.getZ() + RANGE);
        List<SoulBoss> bosses = player.level().getEntitiesOfClass(SoulBoss.class, box,
                b -> b.isAlive() && b.puppetOwner() == null && (b instanceof FreezaTotalEntity || b instanceof HashiramaEntity));
        return bosses.isEmpty() ? null : bosses.get(0).bossId();
    }

    // Brings a flier down: the line on the action bar (at most every 3 s) and a slow fall so the drop doesn't kill.
    public static void bringDown(ServerPlayer player, String boss) {
        say(player, boss);
        player.fallDistance = 0;
        if (player.onGround()) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, false));
        // no fall damage on this drop, until they touch the ground (at most 30 s)
        Tasks.add(a -> {
            player.fallDistance = 0;
            if (a > 2 && (player.onGround() || player.isInWater()) || a > 600 || !player.isAlive() || player.hasDisconnected()) {
                player.removeEffect(MobEffects.SLOW_FALLING);
                return true;
            }
            return false;
        });
    }

    public static void say(ServerPlayer player, String boss) {
        long now = player.level().getGameTime();
        Long at = SAID_AT.get(player.getUUID());
        if (at != null && now - at < 60 && now >= at) {
            return;
        }
        SAID_AT.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.aura_sem_voo." + boss), true);
    }

    public static void forget(UUID id) {
        CHECKED_AT.remove(id);
        NEAR.remove(id);
        SAID_AT.remove(id);
    }

    public static void resetStatics() {
        CHECKED_AT.clear();
        NEAR.clear();
        SAID_AT.clear();
    }
}
