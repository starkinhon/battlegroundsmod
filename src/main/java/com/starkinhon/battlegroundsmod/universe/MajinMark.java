package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Babidi's mark (Stark 2026-11-12: "você pode conseguir a marca Majin, que você vira um cara mau, mas fica mais
// poderoso"). Earned the first time Babidi's guardians fall in his ship (DbActivities.waves). With it on: the M on the
// forehead (client/fx/MajinMarkLayer), a dark wisp now and then in combat, +20% to all the damage you deal, and the
// price: in combat the life no longer comes back by itself (the small natural heals; Senzu, potions and the like still
// work). Crouching 3 s inside Babidi's ship lets it go, and the same takes it back (StructureMissions).
public final class MajinMark {
    public static final String TECHNIQUE = "marca_majin";
    private static final String OFF = "souls_majin_solta";
    private static final float DAMAGE = 1.2f;

    private MajinMark() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(MajinMark::onIncoming);
        NeoForge.EVENT_BUS.addListener(MajinMark::onHeal);
        NeoForge.EVENT_BUS.addListener(MajinMark::onTick);
        NeoForge.EVENT_BUS.addListener(MajinMark::onStartTracking);
        NeoForge.EVENT_BUS.addListener(MajinMark::onLogin);
        NeoForge.EVENT_BUS.addListener(MajinMark::onRespawn);
        NeoForge.EVENT_BUS.addListener(MajinMark::onDimension);
    }

    public static boolean knows(Player player) {
        return player.getData(ModAttachments.TECHNIQUES).contains(TECHNIQUE);
    }

    public static boolean active(Player player) {
        return knows(player) && !player.getPersistentData().getBoolean(OFF);
    }

    // The first time the guardians fall: Babidi marks you.
    static void grant(ServerPlayer player) {
        if (knows(player)) {
            return;
        }
        List<String> techniques = new ArrayList<>(player.getData(ModAttachments.TECHNIQUES));
        techniques.add(TECHNIQUE);
        player.setData(ModAttachments.TECHNIQUES, List.copyOf(techniques));
        player.getPersistentData().putBoolean(OFF, false);
        DbActivities.title(player, Component.translatable("message.battlegroundsmod.majin_titulo"),
                Component.translatable("message.battlegroundsmod.majin_sub"), 0xB04AD0);
        player.sendSystemMessage(Component.translatable("message.battlegroundsmod.majin_explica").withStyle(s -> s.withColor(0xC78BE0)));
        burst(player);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.WITHER_SPAWN, 0.5f, 1.4f);
        sync(player);
    }

    // Crouched 3 s in Babidi's ship: let the mark go, or take it back.
    static void toggle(ServerPlayer player) {
        boolean on = !active(player);
        player.getPersistentData().putBoolean(OFF, !on);
        DbActivities.title(player, Component.translatable("message.battlegroundsmod.majin_titulo"),
                Component.translatable(on ? "message.battlegroundsmod.majin_volta" : "message.battlegroundsmod.majin_solta"), 0xB04AD0);
        if (on) {
            burst(player);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.WITHER_AMBIENT, 0.6f, 1.2f);
        } else {
            Fx.burst(player.serverLevel(), ParticleTypes.CLOUD, player.position().add(0, 1.6, 0), 20, 0.3, 0.03);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.2f);
        }
        sync(player);
        DbQuests.sync(player);
    }

    private static void burst(ServerPlayer player) {
        Fx.burst(player.serverLevel(), Fx.dust(0x2A0A33, 2f), player.position().add(0, 1, 0), 80, 0.7, 0.08);
        Fx.burst(player.serverLevel(), Fx.dust(0xB04AD0, 1.4f), player.position().add(0, 1.7, 0), 40, 0.35, 0.04);
    }

    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker != event.getEntity() && active(attacker)) {
            event.setAmount(event.getAmount() * DAMAGE);
        }
    }

    // The price: in combat, no small natural heals (regeneration from food comes 1 point at a time).
    private static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() <= 1.0f && active(player) && CombatMode.in(player)) {
            event.setCanceled(true);
        }
    }

    // A dark wisp off the head now and then while fighting.
    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 12 == 0 && active(player) && CombatMode.in(player)) {
            Fx.burst(player.serverLevel(), Fx.dust(0x2A0A33, 1.1f), player.position().add(0, 1.9, 0), 3, 0.25, 0.01);
        }
    }

    static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ModPayloads.MajinState(player.getId(), active(player)));
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer && active(target)) {
            PacketDistributor.sendToPlayer(viewer, new ModPayloads.MajinState(target.getId(), true));
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)) {
            sync(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)) {
            sync(player);
        }
    }

    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)) {
            sync(player);
        }
    }
}
