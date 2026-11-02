package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// The stun a hit leaves: the ATORDOADO effect. While it lasts the fighter is slow and can't punch, use skills or dodge
// (checked in Melee, PowerLogic, Movement and PowerEvents). A longer stun replaces a shorter one, never the other way.
// Every hit also leaves the shorter COOLDOWN effect: no punches or skills, but moving, guarding and dodging still work.
// canAttack checks both.
public final class Stun {
    private Stun() {
    }

    public static void apply(LivingEntity target, int ticks) {
        if (target instanceof SoulBoss) {
            ticks = (int) Math.round(ticks * ComboSpec.get().atordoado().multiplicadorBoss());
        }
        if (ticks > 0) {
            target.addEffect(new MobEffectInstance(ModEffects.ATORDOADO, ticks, 0, false, false, true));
        }
    }

    public static void cooldown(LivingEntity target, int ticks) {
        if (ticks > 0) {
            target.addEffect(new MobEffectInstance(ModEffects.COOLDOWN, ticks, 0, false, false, true));
        }
    }

    public static boolean canAttack(Entity entity) {
        return !is(entity) && !(entity instanceof LivingEntity living && living.hasEffect(ModEffects.COOLDOWN));
    }

    public static boolean is(Entity entity) {
        return entity instanceof LivingEntity living && (living.hasEffect(ModEffects.ATORDOADO) || living.hasEffect(ModEffects.BRAIN_DAMAGE));
    }
}
