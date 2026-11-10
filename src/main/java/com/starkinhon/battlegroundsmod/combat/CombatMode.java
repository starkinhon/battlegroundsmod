package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

// Combat mode: out of it a player is plain Minecraft (mines, punches like vanilla). Using a base attack or a skill
// starts it; from then on left click is the punch combo, the dodges and the defense work, and the XP bar shows the ult.
// Hitting, getting hit or using moves keeps it going; after LINGER_TICKS of none of that it ends.
// Server side; the client reads PowerData.combat, synced with the power.
public final class CombatMode {
    // 30 s: it used to end after 10 s, too fast in the middle of a fight.
    public static final int LINGER_TICKS = 600;

    private CombatMode() {
    }

    // The punch combo runs with an empty main hand; with any sword in hand it's the sword combo (SwordArts). Anything
    // else in hand is the vanilla hit. Without this a plain sword's left click fell to the vanilla attack, with its
    // sweep, crit and damage indicator particles.
    public static boolean freeHand(LivingEntity entity) {
        ItemStack held = entity.getMainHandItem();
        return held.isEmpty() || held.getItem() instanceof SwordItem;
    }

    public static boolean in(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        if (entity.level().isClientSide()) {
            return data.combat;
        }
        return data.combatLocked || data.combatUntil > entity.level().getGameTime();
    }

    // Who fights with the base moves (combat mode, punches, Forte, Barragem, Dash Attack, guard, stamina): anyone with a
    // soul, and in a world with universes also the soulless human every player starts as (Stark, 2026-11-10: "vou nascer
    // com esses ataques básicos só").
    public static boolean fighter(LivingEntity entity) {
        if (entity.getData(ModAttachments.POWER).hasPower()) {
            return true;
        }
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
            return false;
        }
        if (entity.level().isClientSide()) {
            return com.starkinhon.battlegroundsmod.power.Progression.clientOn;
        }
        return entity instanceof ServerPlayer player && com.starkinhon.battlegroundsmod.power.Progression.on(player);
    }

    // Starts combat mode, or keeps it going.
    public static void enter(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        if (!fighter(entity)) {
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
        data.combatLocked = false;
        data.combat = false;
    }

    // The combat mode key (Stark, 2026-11-07): turns it on and keeps it on until pressed again, or turns it off right
    // away. Skills, base attacks and hits still turn it on by themselves for 30 s after it was turned off.
    public static void toggle(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        if (!fighter(player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        boolean on = !in(player);
        if (on) {
            enter(player);
            data.combatLocked = true;
        } else {
            leave(player);
        }
        PowerLogic.sync(player);
        PowerLogic.syncSheath(player);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                on ? "message.battlegroundsmod.combat_on" : "message.battlegroundsmod.combat_off"), true);
    }
}
