package com.starkinhon.battlegroundsmod.universe;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.challenge.Universes;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// What fills the Bleach universe (Hueco Mundo) besides its characters (Stark 2026-11-10):
// - its common enemies drop its items now and then: Hollow -> Pílula de Alma, Arrancar -> Máscara Hollow,
//   Menos Grande -> Zanpakutō (rare);
// - the Máscara Hollow, worn, gives strength and speed but drains hunger (any universe);
// - Shunpō, the Bleach technique taught by Yoruichi: sprinting becomes the flash step (speed III and a trail of
//   reiatsu), in every universe.
public final class BleachWorld {
    public static final String UNIVERSE = "bleach";
    public static final String SHUNPO = "shunpo";
    private static final DustParticleOptions REIATSU = new DustParticleOptions(new Vector3f(0.6f, 0.85f, 1f), 0.8f);

    private BleachWorld() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BleachWorld::onDeath);
        NeoForge.EVENT_BUS.addListener(BleachWorld::onTick);
    }

    public static boolean knowsShunpo(ServerPlayer player) {
        return player.getData(ModAttachments.TECHNIQUES).contains(SHUNPO);
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !UNIVERSE.equals(Universes.universeOf(victim.level().dimension()))
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getPath()) {
            case "hollow" -> chance(level, victim, new ItemStack(ModItems.SOUL_CANDY.get()), 0.06);
            case "arrancar" -> chance(level, victim, new ItemStack(ModItems.HOLLOW_MASK.get()), 0.03);
            case "menos_grande" -> chance(level, victim, new ItemStack(ModItems.ZANPAKUTO.get()), 0.02);
            default -> {
            }
        }
    }

    static void chance(ServerLevel level, LivingEntity victim, ItemStack stack, double chance) {
        if (level.getRandom().nextDouble() < chance) {
            ItemEntity entity = new ItemEntity(level, victim.getX(), victim.getY() + 0.5, victim.getZ(), stack);
            entity.setDefaultPickUpDelay();
            entity.setGlowingTag(true);
            level.addFreshEntity(entity);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (player.isSprinting() && knowsShunpo(player)) {
            if (player.tickCount % 10 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 15, 2, true, false));
            }
            if (player.tickCount % 2 == 0) {
                player.serverLevel().sendParticles(REIATSU, player.getX(), player.getY() + 0.9, player.getZ(), 2, 0.25, 0.5, 0.25, 0);
            }
        }
        if (player.tickCount % 20 == 0 && player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.HOLLOW_MASK.get())) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 50, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
            player.causeFoodExhaustion(0.8f);
        }
    }
}
