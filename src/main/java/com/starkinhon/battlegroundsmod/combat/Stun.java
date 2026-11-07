package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

// The stun a hit leaves: the ATORDOADO effect. While it lasts the fighter is slow and can't punch, use skills or dodge
// (checked in Melee, PowerLogic, Movement and PowerEvents). A longer stun replaces a shorter one, never the other way.
// Every hit also leaves the shorter COOLDOWN effect: no punches or skills, but moving, guarding and dodging still work.
// canAttack checks both.
public final class Stun {
    // From this long on a stun cuts off the move the target is making (a parry, a guard break, the skills' stuns). The
    // combo's punches stun for less (10 to 20), like the threshold Bills' moves already used (cancelarStunTicks 25).
    public static final int INTERRUPT_TICKS = 25;

    private Stun() {
    }

    public static void apply(LivingEntity target, int ticks) {
        if (target instanceof SoulBoss boss && boss.isBoss()) {
            ticks = (int) Math.round(ticks * ComboSpec.get().atordoado().multiplicadorBoss());
        }
        boolean wasStunned = is(target);
        if (ticks > 0) {
            target.addEffect(new MobEffectInstance(ModEffects.ATORDOADO, ticks, 0, false, false, true));
        }
        // Only a real stun opens the stun-only skills (Stamina.stunnable): the punches' short one is just hitstun.
        if (ticks >= INTERRUPT_TICKS) {
            PowerData data = target.getData(ModAttachments.POWER);
            data.realStunUntil = Math.max(data.realStunUntil, target.level().getGameTime() + ticks);
        }
        // A real stun (not the short one every punch leaves) knocks them out of the move they are making (busy with it, or
        // holding its key), with its sounds and effects. Counters keep working through the punches' short stuns, and
        // someone already stunned (held in a trap, falling with the Esfera) is not cut off again.
        if (ticks >= INTERRUPT_TICKS && !wasStunned && target.hasData(ModAttachments.POWER)
                && (target.getData(ModAttachments.POWER).busyTicks > 0 || Stamina.holding(target))) {
            PowerLogic.interrupt(target);
        }
        // Any other mob (not a fighter of the mod): a real stun also stops its own attack, not only its swing (that is
        // blocked in PowerEvents). While stunned a skeleton lets go of the bow, a creeper stops swelling.
        if (ticks >= INTERRUPT_TICKS && !wasStunned && target instanceof Mob mob && !(target instanceof SoulBoss)) {
            Tasks.addSystem(age -> {
                if (!mob.isAlive() || mob.isRemoved() || !is(mob)) {
                    return true;
                }
                mob.stopUsingItem();
                if (mob instanceof Creeper creeper) {
                    creeper.setSwellDir(-1);
                }
                return false;
            });
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

    // Stunned by a real stun (not the punches' short one), still going.
    public static boolean real(LivingEntity entity) {
        if (entity.hasEffect(ModEffects.BRAIN_DAMAGE)) {
            return true;
        }
        return entity.hasEffect(ModEffects.ATORDOADO)
                && entity.getData(ModAttachments.POWER).realStunUntil > entity.level().getGameTime();
    }

    public static boolean is(Entity entity) {
        return entity instanceof LivingEntity living && (living.hasEffect(ModEffects.ATORDOADO) || living.hasEffect(ModEffects.BRAIN_DAMAGE));
    }
}
