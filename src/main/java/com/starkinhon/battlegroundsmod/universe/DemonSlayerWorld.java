package com.starkinhon.battlegroundsmod.universe;

import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// What fills the Demon Slayer universe besides its characters (Stark 2026-11-10):
// - its common demons burn in sunlight (UniverseSpawns only brings them at night) and drop its items now and then:
//   Oni -> Flor de Glicínia, Lua Inferior -> Remédio da Tamayo, Oni Espadachim -> Espada Nichirin (rare);
// - Marca do Caçador, the Demon Slayer technique taught by Urokodaki: below 40% health the mark wakes up by itself for
//   20 s (strength II, speed II, resistance), 3 min before it can wake again. Works in every universe.
public final class DemonSlayerWorld {
    public static final String UNIVERSE = "demonslayer";
    public static final String MARK = "marca_cacador";
    private static final Set<String> COMMON_ONI = Set.of("oni_comum", "oni_lua_inferior", "oni_espadachim");
    private static final String MARK_READY = "souls_marca_pronta";
    private static final int MARK_DURATION = 400;
    private static final int MARK_RECHARGE = 3600;

    private DemonSlayerWorld() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DemonSlayerWorld::onDeath);
        NeoForge.EVENT_BUS.addListener(DemonSlayerWorld::onEntityTick);
        NeoForge.EVENT_BUS.addListener(DemonSlayerWorld::onPlayerTick);
    }

    private static String id(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    // A demon: the common ones and Kokushibo (the Nichirin and the wisteria work on him too).
    public static boolean isOni(LivingEntity entity) {
        String id = id(entity);
        return COMMON_ONI.contains(id) || id.equals("kokushibo");
    }

    public static boolean knowsMark(ServerPlayer player) {
        return player.getData(ModAttachments.TECHNIQUES).contains(MARK);
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !UNIVERSE.equals(Universes.universeOf(victim.level().dimension()))
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (id(victim)) {
            case "oni_comum" -> BleachWorld.chance(level, victim, new ItemStack(ModItems.WISTERIA.get(), 2), 0.07);
            case "oni_lua_inferior" -> BleachWorld.chance(level, victim, new ItemStack(ModItems.TAMAYO_MEDICINE.get()), 0.05);
            case "oni_espadachim" -> BleachWorld.chance(level, victim, new ItemStack(ModItems.NICHIRIN_SWORD.get()), 0.02);
            default -> {
            }
        }
    }

    // The sun burns the common demons.
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity oni) || oni.tickCount % 20 != 0 || !(oni.level() instanceof ServerLevel level)
                || !COMMON_ONI.contains(id(oni))) {
            return;
        }
        if (level.isDay() && !level.isRaining() && level.canSeeSky(oni.blockPosition().above())) {
            oni.igniteForSeconds(4);
            oni.hurt(level.damageSources().onFire(), 4f);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0 || !player.isAlive()
                || player.getHealth() > player.getMaxHealth() * 0.4f || !knowsMark(player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now < player.getPersistentData().getLong(MARK_READY)) {
            return;
        }
        player.getPersistentData().putLong(MARK_READY, now + MARK_RECHARGE);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MARK_DURATION, 1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MARK_DURATION, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MARK_DURATION, 0));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 40, 12));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("technique.battlegroundsmod.marca_cacador.title")
                .withStyle(s -> s.withColor(0xFF4A3A).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        Fx.burst(player.serverLevel(), ParticleTypes.FLAME, player.position().add(0, 1.6, 0), 40, 0.3, 0.02);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.7f);
    }
}
