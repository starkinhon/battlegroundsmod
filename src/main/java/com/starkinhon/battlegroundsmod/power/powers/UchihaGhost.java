package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Projectile;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Katon;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Mangekyo;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Fantasma dos Uchiha (Madara). Being remade move by move: Z is the new Katon, X the Mangekyo, C/V are still the first version.
public final class UchihaGhost {
    private static final int SPECTRAL = 0x3A6BFF;

    private UchihaGhost() {
    }

    public static Power create() {
        return new Power("uchiha_ghost", 0xD12F2F, 1.1f, List.of(
                new Ability(300, 30, Katon::use),
                new Ability(400, 35, Mangekyo::use),
                new Ability(300, 30, UchihaGhost::spectralArmor),
                new Ability(100, 0, UchihaGhost::meteor)));
    }

    // C: spectral ribcage armor. Big damage reduction and extra hearts for 8 seconds.
    private static boolean spectralArmor(ServerPlayer player, ServerLevel level) {
        int duration = 160;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 2));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 3));
        Fx.sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
        Fx.sound(level, player.position(), SoundEvents.WITHER_SHOOT, 0.8f, 0.4f);
        Tasks.add(age -> {
            if (!player.isAlive()) {
                return true;
            }
            if (age % 2 == 0) {
                Vec3 base = player.position();
                for (int ring = 0; ring < 4; ring++) {
                    double radius = 1.7 - Math.abs(ring - 1.5) * 0.25;
                    Fx.ring(level, Fx.dust(SPECTRAL, 1.8f), base.add(0, 0.3 + ring * 0.7, 0), radius, 14, age * 0.05 + ring);
                }
                Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, base.add(0, 1.2, 0), 3, 0.9, 0.02);
            }
            return age >= duration;
        });
        return true;
    }

    // V (ult): a meteor falls on where the player is looking. A red circle warns everyone first.
    private static boolean meteor(ServerPlayer player, ServerLevel level) {
        Vec3 target = Combat.raycastPoint(player, 60);
        Vec3 start = target.add(Combat.flatLook(player).scale(-18)).add(0, 40, 0);
        int travel = 60;
        Vec3 velocity = target.subtract(start).scale(1.0 / travel);
        Fx.sound(level, target, SoundEvents.WITHER_SPAWN, 2f, 0.5f);
        Tasks.repeat(travel, age -> {
            if (age % 3 == 0) {
                Fx.ring(level, Fx.dust(0xFF2200, 2f), target.add(0, 0.2, 0), 8, 48, 0);
                Fx.ring(level, Fx.dust(0xFF8800, 1.5f), target.add(0, 0.2, 0), 8.0 * age / travel, 32, 0);
            }
        });
        new Projectile(player, start, velocity, 3.0, travel + 10)
                .trail(pos -> {
                    Fx.burst(level, ParticleTypes.FLAME, pos, 30, 1.8, 0.05);
                    Fx.burst(level, ParticleTypes.LAVA, pos, 3, 1.5, 0);
                    Fx.burst(level, ParticleTypes.LARGE_SMOKE, pos, 8, 2.2, 0.02);
                    Fx.burst(level, Fx.dust(0x5A2A1A, 4f), pos, 15, 2.0, 0);
                })
                .onImpact(pos -> meteorImpact(player, level, pos))
                .impactOnExpire()
                .launch();
        return true;
    }

    private static void meteorImpact(ServerPlayer player, ServerLevel level, Vec3 pos) {
        Combat.explode(player, pos, 7f);
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, pos, 4, 2, 0);
        Fx.burst(level, ParticleTypes.FLAME, pos, 300, 4, 0.4);
        Fx.burst(level, ParticleTypes.LARGE_SMOKE, pos, 120, 4, 0.1);
        Fx.sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.6f);
        Fx.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 0.8f);
        for (LivingEntity target : Combat.inRadius(level, pos, 9, player)) {
            double falloff = 1 - Combat.center(target).distanceTo(pos) / 12;
            Combat.hurt(player, target, (float) (34 * falloff));
            target.igniteForSeconds(8);
            Combat.pushFrom(target, pos, 2.0, 1.0);
        }
    }
}
