package com.starkinhon.battlegroundsmod.universe;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.challenge.Universes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// What fills the Naruto universe besides its characters (Stark 2026-11-10):
// - its common enemies drop its items now and then: Ninja Renegado -> Papel-Bomba, Ninja do Som -> Pílula do Soldado,
//   ANBU da Raiz -> Kunai or the Kunai do Hiraishin (rare);
// - the Bandana Ninja gives ninja speed and jumps while worn (any universe).
public final class NarutoWorld {
    public static final String UNIVERSE = "naruto";

    private NarutoWorld() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NarutoWorld::onDeath);
        NeoForge.EVENT_BUS.addListener(NarutoWorld::onTick);
        NarutoActivities.register();
        NarutoGear.registerGame();
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !UNIVERSE.equals(Universes.universeOf(victim.level().dimension()))
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getPath()) {
            case "anbu_raiz" -> {
                chance(level, victim, new ItemStack(ModItems.KUNAI.get(), 2), 0.2);
                chance(level, victim, new ItemStack(ModItems.HIRAISHIN_KUNAI.get()), 0.03);
            }
            default -> {
            }
        }
    }

    private static void chance(ServerLevel level, LivingEntity victim, ItemStack stack, double chance) {
        if (level.getRandom().nextDouble() < chance) {
            ItemEntity entity = new ItemEntity(level, victim.getX(), victim.getY() + 0.5, victim.getZ(), stack);
            entity.setDefaultPickUpDelay();
            entity.setGlowingTag(true);
            level.addFreshEntity(entity);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        Item headband = ModItems.NINJA_HEADBAND.get();
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(headband)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 50, 0, true, false));
        }
    }
}
