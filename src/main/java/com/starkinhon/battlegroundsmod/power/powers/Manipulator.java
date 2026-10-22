package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// Como Manipulador: kido spells, total hypnosis and a transcendent form.
public final class Manipulator {
    private static final int PURPLE = 0x8E44FF;
    private static final int PALE = 0xE8DAFF;

    private Manipulator() {
    }

    public static Power create() {
        return new Power("manipulator", 0x8E44FF, 1.0f, List.of(
                Ability.player(50, 15, Manipulator::whiteLightning),
                Ability.player(200, 30, Manipulator::blackCoffin),
                Ability.player(260, 25, Manipulator::hypnosis),
                Ability.player(100, 0, Manipulator::transcendence)));
    }

    // Z: a piercing beam of white lightning.
    private static boolean whiteLightning(ServerPlayer player, ServerLevel level) {
        Vec3 start = Combat.handPos(player);
        Vec3 end = Combat.raycastPoint(player, 32);
        Fx.line(level, ParticleTypes.END_ROD, start, end, 0.3);
        Fx.line(level, ParticleTypes.ELECTRIC_SPARK, start, end, 0.5);
        Fx.line(level, Fx.dust(0xFFFFFF, 1.5f), start, end, 0.25);
        Fx.burst(level, ParticleTypes.FLASH, end, 1, 0, 0);
        Fx.sound(level, start, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5f, 1.5f);
        for (LivingEntity target : Combat.alongLine(level, start, end, 0.6, player)) {
            Combat.hurt(player, target, 9f);
            Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, Combat.center(target), 20, 0.4, 0.3);
        }
        return true;
    }

    // X: traps the target in a black coffin, holds them still, then crushes them.
    private static boolean blackCoffin(ServerPlayer player, ServerLevel level) {
        LivingEntity target = Combat.raycastTarget(player, 30);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            return false;
        }
        int duration = 40;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration + 5, 9, false, false));
        Fx.sound(level, target.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1f, 0.6f);
        Tasks.add(age -> {
            if (!target.isAlive()) {
                return true;
            }
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
            target.hurtMarked = true;
            double grow = Math.min(1, (age + 1) / 15.0);
            var box = target.getBoundingBox().inflate(0.6);
            box = box.setMaxY(box.minY + (box.maxY - box.minY) * grow);
            if (age % 2 == 0) {
                Fx.box(level, Fx.dust(0x111111, 2f), box, 0.3);
                Fx.box(level, Fx.dust(PURPLE, 1f), box.inflate(0.1), 0.6);
            }
            if (age < duration) {
                return false;
            }
            Vec3 pos = Combat.center(target);
            Combat.hurtMagic(player, target, 18f);
            Fx.burst(level, ParticleTypes.SQUID_INK, pos, 80, 0.8, 0.2);
            Fx.burst(level, Fx.dust(PURPLE, 2f), pos, 60, 1, 0);
            Fx.sound(level, pos, SoundEvents.WITHER_BREAK_BLOCK, 1f, 0.6f);
            return true;
        });
        return true;
    }

    // C: everyone nearby is blinded and confused, mobs lose track of the player,
    // and the player reappears behind the closest enemy.
    private static boolean hypnosis(ServerPlayer player, ServerLevel level) {
        Vec3 origin = player.position();
        List<LivingEntity> targets = Combat.inRadius(level, Combat.center(player), 14, player);
        var glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
        Fx.burst(level, glass, origin.add(0, 1, 0), 80, 1.5, 0.2);
        Fx.sphere(level, Fx.dust(PALE, 1.5f), origin.add(0, 1, 0), 3, 60);
        Fx.sound(level, origin, SoundEvents.ILLUSIONER_MIRROR_MOVE, 2f, 0.8f);
        Fx.sound(level, origin, SoundEvents.GLASS_BREAK, 1.5f, 0.6f);
        LivingEntity closest = null;
        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            if (target instanceof Mob mob && mob.getTarget() == player) {
                mob.setTarget(null);
            }
            Fx.burst(level, glass, Combat.center(target), 30, 0.5, 0.15);
            if (closest == null || target.distanceToSqr(player) < closest.distanceToSqr(player)) {
                closest = target;
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, false));
        if (closest != null) {
            Vec3 behind = closest.position().subtract(Combat.flatLook(closest).scale(1.5));
            if (Combat.canStandAt(player, behind)) {
                Vec3 to = closest.position().subtract(behind);
                float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                player.connection.teleport(behind.x, behind.y, behind.z, yaw, player.getXRot());
            }
        }
        return true;
    }

    // V (ult): transcendent form for 12 seconds. Initial shockwave, then spiritual pressure crushes enemies nearby.
    private static boolean transcendence(ServerPlayer player, ServerLevel level) {
        int duration = 240;
        Vec3 origin = Combat.center(player);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 1));
        Fx.sphere(level, Fx.dust(PALE, 3f), origin, 4, 120);
        Fx.sphere(level, ParticleTypes.END_ROD, origin, 6, 80);
        Fx.burst(level, ParticleTypes.FLASH, origin, 1, 0, 0);
        Fx.sound(level, origin, SoundEvents.TOTEM_USE, 2f, 0.6f);
        Fx.sound(level, origin, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.7f);
        for (LivingEntity target : Combat.inRadius(level, origin, 10, player)) {
            Combat.hurt(player, target, 8f);
            Combat.pushFrom(target, origin, 2.0, 0.8);
        }
        Tasks.add(age -> {
            if (!player.isAlive()) {
                return true;
            }
            Vec3 base = player.position();
            double angle = age * 0.4;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                Fx.burst(level, Fx.dust(PURPLE, 1.5f), base.add(Math.cos(a) * 0.9, (age % 20) * 0.1, Math.sin(a) * 0.9), 1, 0, 0);
            }
            Fx.burst(level, ParticleTypes.WITCH, base.add(0, 1, 0), 1, 0.5, 0);
            if (age % 20 == 0) {
                Fx.ring(level, Fx.dust(PALE, 1.5f), base.add(0, 0.1, 0), 8, 40, 0);
                for (LivingEntity target : Combat.inRadius(level, Combat.center(player), 8, player)) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                    Combat.hurtMagic(player, target, 2f);
                    Fx.burst(level, Fx.dust(PURPLE, 1f), Combat.center(target), 10, 0.4, 0);
                }
            }
            return age >= duration;
        });
        return true;
    }
}
