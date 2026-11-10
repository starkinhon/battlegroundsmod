package com.starkinhon.battlegroundsmod.universe;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

// Modo Sábio (Senjutsu), the Naruto technique (Stark 2026-11-10), learned from Jiraiya. Crouch still on the ground for
// 3 s to gather natural energy (green motes come in, the bar fills in the action bar); then 30 s of Sage power:
// strength, speed, resistance, regeneration and higher jumps. 2 min before gathering again. Moving or standing up
// breaks the gathering. Works in every universe.
public final class SageMode {
    public static final String TECHNIQUE = "modo_sabio";
    private static final int GATHER = 60;
    private static final int DURATION = 600;
    private static final int RECHARGE = 2400;
    private static final String READY_TAG = "souls_sabio_pronto";
    private static final String GATHER_TAG = "souls_sabio_juntando";
    private static final DustParticleOptions NATURE = new DustParticleOptions(new Vector3f(0.45f, 0.95f, 0.4f), 1.1f);
    private static final DustParticleOptions SAGE = new DustParticleOptions(new Vector3f(1f, 0.6f, 0.15f), 1.2f);

    private SageMode() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SageMode::onTick);
    }

    public static boolean knows(ServerPlayer player) {
        return player.getData(ModAttachments.TECHNIQUES).contains(TECHNIQUE);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !knows(player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        var data = player.getPersistentData();
        if (player.hasEffect(MobEffects.JUMP) && data.getLong(READY_TAG) - RECHARGE + DURATION > now && now % 4 == 0) {
            // In Sage Mode: orange around the eyes.
            Vec3 eye = player.getEyePosition();
            player.serverLevel().sendParticles(SAGE, eye.x, eye.y, eye.z, 2, 0.25, 0.1, 0.25, 0);
        }
        boolean still = player.isShiftKeyDown() && player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 0.0004;
        if (!still || now < data.getLong(READY_TAG)) {
            if (data.getInt(GATHER_TAG) > 0) {
                data.putInt(GATHER_TAG, 0);
            }
            return;
        }
        int gathered = data.getInt(GATHER_TAG) + 1;
        data.putInt(GATHER_TAG, gathered);
        Vec3 at = player.position().add(0, 1, 0);
        double a = gathered * 0.7;
        player.serverLevel().sendParticles(NATURE, at.x + Math.cos(a) * 1.4, at.y - 0.6 + (gathered % 20) / 20.0, at.z + Math.sin(a) * 1.4, 1, 0, 0, 0, 0);
        if (gathered % 10 == 0) {
            int bars = gathered * 10 / GATHER;
            player.displayClientMessage(Component.literal("§a" + "▮".repeat(bars) + "§8" + "▮".repeat(10 - bars)), true);
        }
        if (gathered >= GATHER) {
            data.putInt(GATHER_TAG, 0);
            data.putLong(READY_TAG, now + RECHARGE);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, DURATION, 1));
            player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 40, 12));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("technique.battlegroundsmod.modo_sabio.title")
                    .withStyle(s -> s.withColor(0xFF9A2E).withBold(true))));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            Fx.burst(player.serverLevel(), SAGE, at, 80, 0.8, 0.05);
            Fx.sound(player.serverLevel(), at, SoundEvents.BEACON_POWER_SELECT, 1f, 1.3f);
        }
    }
}
