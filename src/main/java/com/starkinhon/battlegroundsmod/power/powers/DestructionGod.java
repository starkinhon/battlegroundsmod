package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Projectile;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Deus da Destruição: destruction orbs, the destruction touch, divine steps and a sun of destruction.
public final class DestructionGod {
    private static final int PURPLE = 0x9B30FF;
    private static final int ORANGE = 0xFF8C1A;

    private DestructionGod() {
    }

    public static Power create() {
        return new Power("destruction_god", 0x9B30FF, 1.3f, List.of(
                Ability.player(40, 12, DestructionGod::orb),
                Ability.player(240, 35, DestructionGod::destructionTouch),
                Ability.player(80, 15, DestructionGod::divineStep),
                Ability.player(100, 0, DestructionGod::destructionSun)));
    }

    // Z: a fast destruction orb that withers whoever it hits.
    private static boolean orb(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        Vec3 start = Combat.handPos(player).add(look.scale(0.5));
        Fx.sound(level, start, SoundEvents.EVOKER_CAST_SPELL, 1.2f, 1.4f);
        new Projectile(player, start, look.scale(1.5), 0.8, 40)
                .trail(pos -> {
                    Fx.burst(level, Fx.dust(PURPLE, 1.6f), pos, 4, 0.2, 0);
                    Fx.burst(level, ParticleTypes.PORTAL, pos, 4, 0.2, 0.3);
                    Fx.burst(level, ParticleTypes.WITCH, pos, 1, 0.1, 0);
                })
                .onHit(target -> {
                    Combat.hurt(player, target, 8f);
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
                })
                .onImpact(pos -> {
                    Fx.burst(level, ParticleTypes.REVERSE_PORTAL, pos, 40, 0.4, 0.3);
                    Fx.burst(level, Fx.dust(PURPLE, 2f), pos, 20, 0.5, 0);
                    Fx.sound(level, pos, SoundEvents.AMETHYST_BLOCK_BREAK, 1.5f, 0.6f);
                })
                .launch();
        return true;
    }

    // X: marks a close target, holds them for a moment, then erases them. Ignores armor.
    private static boolean destructionTouch(ServerPlayer player, ServerLevel level) {
        LivingEntity target = Combat.raycastTarget(player, 7);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            return false;
        }
        int delay = 15;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, delay + 5, 9, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, delay + 5, 0, false, false));
        Fx.sound(level, target.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1f, 1.2f);
        Tasks.add(age -> {
            if (!target.isAlive()) {
                return true;
            }
            Vec3 base = target.position();
            double height = target.getBbHeight() * age / delay;
            for (int i = 0; i < 3; i++) {
                double a = age * 0.6 + i * Math.PI * 2 / 3;
                Fx.burst(level, Fx.dust(PURPLE, 1.4f), base.add(Math.cos(a) * 0.8, height, Math.sin(a) * 0.8), 1, 0, 0);
            }
            if (age < delay) {
                return false;
            }
            Vec3 pos = Combat.center(target);
            Combat.hurtMagic(player, target, 16f);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            Fx.burst(level, ParticleTypes.ASH, pos, 120, 0.6, 0.05);
            Fx.burst(level, Fx.dust(PURPLE, 2f), pos, 50, 0.6, 0.05);
            Fx.burst(level, ParticleTypes.REVERSE_PORTAL, pos, 60, 0.4, 0.4);
            Fx.sound(level, pos, SoundEvents.WITHER_SHOOT, 1f, 0.5f);
            return true;
        });
        return true;
    }

    // C: teleports behind the target being looked at, or 12 blocks forward.
    private static boolean divineStep(ServerPlayer player, ServerLevel level) {
        Vec3 from = player.position();
        LivingEntity target = Combat.raycastTarget(player, 24);
        Vec3 dest;
        float yaw = player.getYRot();
        if (target != null) {
            dest = target.position().subtract(Combat.flatLook(target).scale(1.5));
            Vec3 to = target.position().subtract(dest);
            yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        } else {
            Vec3 point = Combat.raycastPoint(player, 12);
            dest = point.subtract(player.getLookAngle().scale(0.8)).subtract(0, player.getEyeHeight(), 0);
        }
        if (!Combat.canStandAt(player, dest)) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.blocked"), true);
            return false;
        }
        Fx.burst(level, ParticleTypes.PORTAL, from.add(0, 1, 0), 60, 0.4, 0.5);
        Fx.burst(level, Fx.dust(PURPLE, 1.5f), from.add(0, 1, 0), 25, 0.4, 0);
        player.connection.teleport(dest.x, dest.y, dest.z, yaw, player.getXRot());
        player.resetFallDistance();
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, dest.add(0, 1, 0), 60, 0.4, 0.3);
        Fx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.8f);
        Fx.sound(level, dest, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.2f);
        return true;
    }

    // V (ult): charges a sun of destruction above the head, then throws it. Huge explosion.
    private static boolean destructionSun(ServerPlayer player, ServerLevel level) {
        int charge = 40;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, charge, 2, false, false));
        Fx.sound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 2f, 0.5f);
        Tasks.add(age -> {
            if (!player.isAlive()) {
                return true;
            }
            Vec3 center = player.position().add(0, player.getBbHeight() + 2.5, 0);
            double radius = 0.4 + 2.6 * age / charge;
            if (age < charge) {
                Fx.sphere(level, Fx.dust(ORANGE, 2.5f), center, radius, 40);
                Fx.sphere(level, Fx.dust(PURPLE, 2f), center, radius * 0.6, 20);
                Fx.burst(level, ParticleTypes.FLAME, center, 4, radius * 0.5, 0.02);
                if (age % 10 == 0) {
                    Fx.sound(level, center, SoundEvents.FIRE_AMBIENT, 2f, 0.5f);
                }
                return false;
            }
            Vec3 look = player.getLookAngle();
            Fx.sound(level, center, SoundEvents.BLAZE_SHOOT, 2f, 0.3f);
            new Projectile(player, center, look.scale(0.9), 3.0, 80)
                    .trail(pos -> {
                        Fx.sphere(level, Fx.dust(ORANGE, 3f), pos, 3, 40);
                        Fx.burst(level, ParticleTypes.FLAME, pos, 10, 2, 0.02);
                    })
                    .onImpact(pos -> sunImpact(player, level, pos))
                    .impactOnExpire()
                    .launch();
            return true;
        });
        return true;
    }

    private static void sunImpact(ServerPlayer player, ServerLevel level, Vec3 pos) {
        Combat.explode(player, pos, 8f);
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, pos, 5, 2.5, 0);
        Fx.burst(level, Fx.dust(ORANGE, 4f), pos, 200, 4, 0);
        Fx.burst(level, Fx.dust(PURPLE, 3f), pos, 120, 3, 0);
        Fx.burst(level, ParticleTypes.FLAME, pos, 200, 4, 0.3);
        Fx.sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
        for (LivingEntity target : Combat.inRadius(level, pos, 9, player)) {
            double falloff = 1 - Combat.center(target).distanceTo(pos) / 12;
            Combat.hurt(player, target, (float) (32 * falloff));
            Combat.pushFrom(target, pos, 2.2, 1.0);
        }
    }
}
