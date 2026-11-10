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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// What fills the Jujutsu universe besides its characters (Stark 2026-11-10):
// - its common enemies drop its items now and then: Maldição Menor -> Boneco de Palha, Maldição de Grau 2 -> Boneco
//   Amaldiçoado, Feiticeiro Renegado -> Dedo do Sukuna (rare);
// - the Venda do Gojo gives the Six Eyes while worn (any universe): night vision, and everything alive within 24 blocks
//   glows, through walls.
public final class JujutsuWorld {
    public static final String UNIVERSE = "jujutsu";
    private static final double SIX_EYES_RANGE = 24;

    private JujutsuWorld() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(JujutsuWorld::onDeath);
        NeoForge.EVENT_BUS.addListener(JujutsuWorld::onTick);
        NeoForge.EVENT_BUS.addListener(JujutsuWorld::onHurt);
    }

    // Whoever a player hits becomes their Boneco de Palha's target (Nobara's Ressonância).
    private static void onHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity() != player) {
            com.starkinhon.battlegroundsmod.item.StrawDollItem.remember(player, event.getEntity().getUUID());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !UNIVERSE.equals(Universes.universeOf(victim.level().dimension()))
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getPath()) {
            case "maldicao_menor" -> chance(level, victim, new ItemStack(ModItems.STRAW_DOLL.get()), 0.07);
            case "maldicao_grau2" -> chance(level, victim, new ItemStack(ModItems.CURSED_DOLL.get()), 0.05);
            case "feiticeiro_renegado" -> chance(level, victim, new ItemStack(ModItems.SUKUNA_FINGER.get()), 0.03);
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
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || !player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GOJO_BLINDFOLD.get())) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false));
        AABB around = player.getBoundingBox().inflate(SIX_EYES_RANGE);
        for (LivingEntity seen : player.serverLevel().getEntitiesOfClass(LivingEntity.class, around, e -> e != player && e.isAlive())) {
            seen.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false));
        }
    }
}
