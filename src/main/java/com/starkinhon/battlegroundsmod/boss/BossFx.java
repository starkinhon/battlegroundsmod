package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.fx.ModParticles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Looks and sounds of the boss itself (warnings, entrance, phase changes, dashes). The skills bring their own.
final class BossFx {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final double TITLE_RANGE = 48;

    private BossFx() {
    }

    // Warning before a move: a ring closing in at the feet and sparks gathering where the move comes from.
    // progress goes 0 -> 1 over the warning.
    static void tell(ServerLevel level, LivingEntity boss, String tipo, int color, double progress) {
        Vec3 feet = boss.position().add(0, 0.1, 0);
        Fx.ring(level, ModParticles.dust(color, 1.1f, 6, 0.9f), feet, 2.2 * (1 - progress) + 0.4, 14, progress * 3);
        Vec3 focus = switch (tipo == null ? "" : tipo) {
            case "golpe", "barragem" -> Combat.handPos(boss);
            case "ult" -> boss.position().add(0, boss.getBbHeight() + 1.2, 0);
            default -> boss.getEyePosition().add(boss.getLookAngle().scale(0.3));
        };
        for (int i = 0; i < 3; i++) {
            Vec3 from = focus.add((RANDOM.nextDouble() - 0.5) * 2.4, (RANDOM.nextDouble() - 0.5) * 2.4, (RANDOM.nextDouble() - 0.5) * 2.4);
            Fx.moving(level, ModParticles.dust(color, 0.9f, 6, 1f), from, focus.subtract(from).scale(0.18));
        }
    }

    static void tellStart(ServerLevel level, LivingEntity boss, String sound) {
        SoundEvent event = sound(sound);
        if (event != null) {
            Fx.sound(level, boss.position(), event, 1.4f, 0.8f);
        }
    }

    // Entrance and phase changes: a dark column around the boss while it stands still.
    static void aura(ServerLevel level, LivingEntity boss, int color, int age) {
        Vec3 base = boss.position();
        for (int i = 0; i < 6; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = 0.8 + RANDOM.nextDouble() * 0.7;
            Vec3 pos = base.add(Math.cos(a) * r, RANDOM.nextDouble() * 0.4, Math.sin(a) * r);
            Fx.moving(level, ModParticles.dust(color, 1.4f, 20, 0.95f), pos, new Vec3(-Math.sin(a) * 0.1, 0.12 + RANDOM.nextDouble() * 0.1, Math.cos(a) * 0.1));
        }
        if (age % 4 == 0) {
            Fx.ring(level, ModParticles.dust(color, 1.6f, 10, 0.9f), base.add(0, 0.1, 0), 1.5 + (age % 20) * 0.15, 20, age * 0.1);
        }
        if (age % 10 == 0) {
            Fx.sound(level, base, SoundEvents.WITHER_AMBIENT, 0.8f, 0.5f);
        }
    }

    // The burst that ends the entrance or a phase change.
    static void shockwave(ServerLevel level, LivingEntity boss, int color, double radius) {
        Vec3 base = boss.position().add(0, 0.2, 0);
        for (int ring = 1; ring <= 4; ring++) {
            Fx.ring(level, ModParticles.dust(color, 2f, 14, 0.9f), base, radius * ring / 4, 12 + ring * 8, ring);
        }
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, base, 1, 0, 0);
        Fx.burst(level, ModParticles.smoke(), base.add(0, 1, 0), 30, 1.2, 0.08);
        Fx.sound(level, base, SoundEvents.WITHER_SPAWN, 1.2f, 0.7f);
        Fx.sound(level, base, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
    }

    // Afterimage along a dash.
    static void dash(ServerLevel level, LivingEntity boss, int color) {
        Vec3 center = Combat.center(boss);
        Fx.burst(level, ModParticles.dust(color, 1.3f, 10, 0.8f), center, 14, 0.35, 0.02);
        Fx.burst(level, ModParticles.smoke(), boss.position().add(0, 0.2, 0), 6, 0.3, 0.02);
        Fx.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.4f);
    }

    static void teleport(ServerLevel level, Vec3 from, Vec3 to) {
        for (Vec3 pos : new Vec3[] {from, to}) {
            Fx.burst(level, ModParticles.smoke(), pos.add(0, 1, 0), 25, 0.5, 0.05);
            Fx.burst(level, ParticleTypes.LARGE_SMOKE, pos.add(0, 1, 0), 10, 0.4, 0.02);
            Fx.sound(level, pos, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.6f);
        }
    }

    static void title(ServerLevel level, LivingEntity boss, String title, String subtitle, int color) {
        if (title == null || title.isEmpty()) {
            return;
        }
        Component big = Component.literal(title).withStyle(s -> s.withColor(color).withBold(true));
        Component small = Component.literal(subtitle == null ? "" : subtitle).withStyle(s -> s.withColor(0xAAAAAA));
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(boss) <= TITLE_RANGE) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
                player.connection.send(new ClientboundSetTitleTextPacket(big));
                player.connection.send(new ClientboundSetSubtitleTextPacket(small));
            }
        }
    }

    // "#ff7a10" -> 0xFF7A10. Falls back to red.
    static int color(String hex) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (RuntimeException e) {
            return 0xD12F2F;
        }
    }

    static SoundEvent sound(String id) {
        ResourceLocation location = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return location == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(location).orElse(null);
    }
}
