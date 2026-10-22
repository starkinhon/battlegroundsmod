package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Hand-to-hand moves every power shares: a 4-hit combo on left click, a charged strong punch and a barrage.
public final class Melee {
    private static final int COMBO_LENGTH = 4;
    private static final int COMBO_RESET_TICKS = 25;
    private static final int PUNCH_DELAY_TICKS = 5;

    public static final Ability STRONG = new Ability(60, 0, Melee::strong);
    public static final Ability BARRAGE = new Ability(140, 10, Melee::barrageMove);

    private Melee() {
    }

    private static float multiplier(LivingEntity player) {
        Power power = Powers.get(player.getData(ModAttachments.POWER).power);
        return power == null ? 1f : power.meleeMultiplier();
    }

    private static int fistColor(LivingEntity player) {
        Power power = Powers.get(player.getData(ModAttachments.POWER).power);
        return power == null ? 0xFFFFFF : power.color();
    }

    // Left click on an entity (or a boss's punch). The last hit of the combo launches the target.
    public static void normalHit(LivingEntity player, LivingEntity target) {
        PowerData data = player.getData(ModAttachments.POWER);
        if (data.punchDelay > 0 || data.busyTicks > 0) {
            return;
        }
        ServerLevel level = Combat.level(player);
        float mult = multiplier(player);
        data.comboStep = data.comboStep % COMBO_LENGTH + 1;
        data.comboResetTicks = COMBO_RESET_TICKS;
        data.punchDelay = PUNCH_DELAY_TICKS;
        Vec3 hitPos = Combat.center(target);
        Vec3 look = player.getLookAngle();

        if (data.comboStep < COMBO_LENGTH) {
            Combat.hurt(player, target, 4f * mult);
            Combat.push(target, look, 0.35, 0.1);
            Fx.burst(level, ParticleTypes.CRIT, hitPos, 8, 0.2, 0.3);
            Fx.burst(level, Fx.dust(fistColor(player), 1.2f), hitPos, 5, 0.2, 0);
            Fx.sound(level, hitPos, SoundEvents.PLAYER_ATTACK_WEAK, 1f, 0.9f + data.comboStep * 0.1f);
        } else {
            Combat.hurt(player, target, 7f * mult);
            Combat.push(target, look, 1.8, 0.5);
            Fx.burst(level, ParticleTypes.EXPLOSION, hitPos, 1, 0, 0);
            Fx.burst(level, ParticleTypes.SWEEP_ATTACK, hitPos, 2, 0.3, 0);
            Fx.burst(level, ParticleTypes.CLOUD, hitPos, 15, 0.3, 0.15);
            Fx.sound(level, hitPos, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.8f);
            data.comboStep = 0;
        }
    }

    // Short wind-up, then a heavy punch that sends the target flying.
    private static boolean strong(LivingEntity player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        float mult = multiplier(player);
        int color = fistColor(player);
        int windup = 8;
        data.busyTicks = windup + 2;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, windup, 2, false, false));
        Fx.sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        Tasks.add(age -> {
            if (!player.isAlive()) {
                return true;
            }
            Vec3 hand = Combat.handPos(player);
            if (age < windup) {
                // Energy gathering on the fist.
                for (int i = 0; i < 3; i++) {
                    Vec3 from = hand.add((level.random.nextDouble() - 0.5) * 2, (level.random.nextDouble() - 0.5) * 2, (level.random.nextDouble() - 0.5) * 2);
                    Fx.moving(level, Fx.dust(color, 1f), from, hand.subtract(from).scale(0.25));
                }
                Fx.burst(level, ParticleTypes.CRIT, hand, 2, 0.1, 0.05);
                return false;
            }
            Vec3 look = player.getLookAngle();
            Vec3 front = player.getEyePosition().add(look.scale(2.5));
            LivingEntity target = Combat.raycastTarget(player, 4.5);
            Fx.burst(level, ParticleTypes.SWEEP_ATTACK, front, 1, 0, 0);
            Fx.line(level, ParticleTypes.CLOUD, hand, front.add(look.scale(2)), 0.4);
            Fx.sound(level, front, SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
            if (target != null) {
                Vec3 hitPos = Combat.center(target);
                Combat.hurt(player, target, 10f * mult);
                Combat.push(target, look, 2.6, 0.6);
                Fx.burst(level, ParticleTypes.EXPLOSION, hitPos, 2, 0.3, 0);
                Fx.burst(level, Fx.dust(color, 2f), hitPos, 25, 0.5, 0);
                Fx.sound(level, hitPos, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
            }
            return true;
        });
        return true;
    }

    private static boolean barrageMove(LivingEntity player, ServerLevel level) {
        barrage(player, 20, 4.0, 1.5f * multiplier(player), 6f * multiplier(player), fistColor(player));
        return true;
    }

    // Rapid punches in a cone in front of the player, ending with a finisher that launches whoever is in front.
    // Also used by powers whose own moves are bigger barrages.
    public static void barrage(LivingEntity player, int ticks, double range, float hitDamage, float finisherDamage, int fistColor) {
        ServerLevel level = Combat.level(player);
        PowerData data = player.getData(ModAttachments.POWER);
        data.busyTicks = ticks + 4;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, false));
        Tasks.add(age -> {
            if (!player.isAlive()) {
                return true;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            if (age < ticks) {
                if (age % 2 == 0) {
                    Vec3 hand = Combat.handPos(player);
                    for (int i = 0; i < 3; i++) {
                        Vec3 offset = new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(1.4);
                        Vec3 fist = eye.add(look.scale(range * 0.5 + level.random.nextDouble())).add(offset);
                        Fx.line(level, Fx.dust(fistColor, 1f), hand, fist, 0.35);
                        Fx.burst(level, ParticleTypes.CRIT, fist, 2, 0.1, 0.1);
                    }
                    for (LivingEntity target : Combat.inCone(player, range, 0.55)) {
                        Combat.hurt(player, target, hitDamage);
                        // Keeps the target in front of the player for the whole barrage.
                        Combat.push(target, look, 0.2, 0.05);
                    }
                    Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.8f + level.random.nextFloat() * 0.6f);
                }
                return false;
            }
            Vec3 front = eye.add(look.scale(2));
            Fx.burst(level, ParticleTypes.EXPLOSION, front, 1, 0, 0);
            Fx.burst(level, ParticleTypes.CLOUD, front, 20, 0.4, 0.2);
            Fx.sound(level, front, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
            for (LivingEntity target : Combat.inCone(player, range, 0.55)) {
                Combat.hurt(player, target, finisherDamage);
                Combat.push(target, look, 2.2, 0.6);
            }
            return true;
        });
    }
}
