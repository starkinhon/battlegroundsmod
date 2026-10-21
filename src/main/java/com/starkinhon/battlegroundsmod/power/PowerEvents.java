package com.starkinhon.battlegroundsmod.power;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.combat.Tasks;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Game events that drive the power system.
public final class PowerEvents {

    private PowerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PowerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onAttack);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onFall);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Tasks.tick();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Tasks.clear();
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PowerLogic.tick(player);
        }
    }

    // First time a player joins, they get the power book.
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PowerData data = player.getData(ModAttachments.POWER);
            if (!data.receivedBook) {
                data.receivedBook = true;
                player.getInventory().add(new ItemStack(ModItems.POWER_BOOK.get()));
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.welcome"), false);
            }
            PowerLogic.sync(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getData(ModAttachments.POWER).resetCombat();
            PowerLogic.sync(player);
        }
    }

    // Left click with an empty hand runs the power's punch combo instead of the vanilla hit.
    private static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getData(ModAttachments.POWER).hasPower()) {
            return;
        }
        event.setCanceled(true);
        Melee.normalHit(player, target);
    }

    // Dealing damage charges the ultimate.
    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != event.getEntity()) {
            PowerLogic.addUltCharge(player, event.getNewDamage());
        }
    }

    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getData(ModAttachments.POWER).noFallTicks > 0) {
            event.setCanceled(true);
        }
    }
}
