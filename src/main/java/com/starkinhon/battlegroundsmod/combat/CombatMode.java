package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

// Combat mode: out of it a player is plain Minecraft (mines, punches like vanilla). Using a base attack or a skill
// starts it; from then on left click is the punch combo, the dodges and the defense work, and the XP bar shows the ult.
// Hitting, getting hit or using moves keeps it going; after LINGER_TICKS of none of that it ends.
// Server side; the client reads PowerData.combat, synced with the power.
public final class CombatMode {
    public static final int LINGER_TICKS = 200;

    private CombatMode() {
    }

    public static boolean in(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        if (entity.level().isClientSide()) {
            return data.combat;
        }
        return data.combatUntil > entity.level().getGameTime();
    }

    // Starts combat mode, or keeps it going.
    public static void enter(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        if (!data.hasPower()) {
            return;
        }
        boolean was = in(entity);
        data.combatUntil = entity.level().getGameTime() + LINGER_TICKS;
        data.combat = true;
        if (!was && entity instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
    }

    // Keeps it going only if it is already on (hits and damage don't start it, only moves do).
    public static void refresh(LivingEntity entity) {
        if (!entity.level().isClientSide() && in(entity)) {
            enter(entity);
        }
    }

    // Every tick on the server: ends it when it ran out.
    public static void tick(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        if (data.combat && !in(entity)) {
            data.combat = false;
            if (entity instanceof ServerPlayer player) {
                PowerLogic.sync(player);
            }
        }
    }

    public static void leave(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        data.combatUntil = 0;
        data.combat = false;
    }
}
