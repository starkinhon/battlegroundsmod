package com.starkinhon.battlegroundsmod.combat;

import java.util.Comparator;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Hand-to-hand moves every power shares: the punch combo on left click (values in souls_skills/padrao.combo.json),
// a strong punch that charges while held and a barrage that lasts while held. The Dash Attack is in DashAttack.
public final class Melee {
    public static final Ability STRONG = new Ability(60, 0, Melee::strong);
    public static final Ability BARRAGE = new Ability(140, 10, Melee::barrageMove);
    // Holding the base attack key: the Forte charges this much longer at most, for up to this much more damage...
    private static final int STRONG_CHARGE_TICKS = 30;
    private static final float STRONG_CHARGE_BONUS = 1.5f;
    // ...and the Barragem goes on for up to this long.
    private static final int BARRAGE_HELD_TICKS = 60;

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

    // A boss's punch at the target it chose.
    public static boolean normalHit(LivingEntity attacker, LivingEntity target) {
        return punch(attacker, target);
    }

    // Next hit of the combo. Players click (target null: whoever is in front, or nobody, and the punch whiffs); a click
    // during the end of the previous hit is kept for later. Someone a bit too far in front gets chased first.
    public static boolean punch(LivingEntity attacker, LivingEntity target) {
        PowerData data = attacker.getData(ModAttachments.POWER);
        ComboSpec spec = ComboSpec.get();
        if (!attacker.isAlive() || data.busyTicks > 0 || Freeze.isFrozen(attacker) || !Stun.canAttack(attacker) || Defense.isGuarding(attacker)) {
            return false;
        }
        if (data.punchDelay > 0) {
            data.punchQueued = target == null && data.punchDelay <= spec.bufferTicks();
            return false;
        }
        data.punchQueued = false;
        if (target == null) {
            target = closestInFront(attacker, spec.alcance(), 0.55);
        }
        ComboSpec.Passo passo = spec.passo();
        if (target == null) {
            target = closestInFront(attacker, passo.distanciaMax(), passo.cone());
            if (target != null) {
                chase(attacker, target, spec);
                return true;
            }
        } else if (attacker.distanceTo(target) > spec.alcance() + 0.5) {
            chase(attacker, target, spec);
            return true;
        }
        swing(attacker, target, spec);
        return true;
    }

    // Runs at the target for up to ticksMax ticks, then throws the punch.
    private static void chase(LivingEntity attacker, LivingEntity target, ComboSpec spec) {
        PowerData data = attacker.getData(ModAttachments.POWER);
        ComboSpec.Passo passo = spec.passo();
        data.punchDelay = passo.ticksMax() + 1;
        SkillSounds.play(Combat.level(attacker), spec.sons(), "passo", 0, 0, onde -> attacker.position(), List.of());
        Tasks.add(age -> {
            if (!attacker.isAlive() || Stun.is(attacker) || Freeze.isFrozen(attacker)) {
                data.punchDelay = 0;
                return true;
            }
            Vec3 to = target.position().subtract(attacker.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (!target.isAlive() || flat.length() <= spec.alcance() - 0.6 || age >= passo.ticksMax()) {
                data.punchDelay = 0;
                swing(attacker, target.isAlive() ? target : null, spec);
                return true;
            }
            Vec3 step = flat.normalize().scale(passo.impulso());
            attacker.setDeltaMovement(step.x, Math.min(attacker.getDeltaMovement().y, 0.1), step.z);
            attacker.hurtMarked = true;
            return false;
        });
    }

    // Plays the next hit's animation and lands it after its wind-up.
    private static void swing(LivingEntity attacker, LivingEntity target, ComboSpec spec) {
        PowerData data = attacker.getData(ModAttachments.POWER);
        int step = Math.floorMod(data.comboStep, spec.golpes().size());
        play(attacker, target, step, spec.golpe(step), spec, null);
    }

    // Lands combo hit step (with golpe's values) on target, with no chase: the Dash Attack's hit. The combo goes on from
    // the next step, and onHit runs when it connects.
    public static void strike(LivingEntity attacker, LivingEntity target, int step, ComboSpec.Golpe golpe, Runnable onHit) {
        play(attacker, target, step, golpe, ComboSpec.get(), onHit);
    }

    private static void play(LivingEntity attacker, LivingEntity target, int step, ComboSpec.Golpe golpe, ComboSpec spec, Runnable onHit) {
        PowerData data = attacker.getData(ModAttachments.POWER);
        data.comboStep = step + 1 >= spec.golpes().size() ? 0 : step + 1;
        data.comboResetTicks = spec.resetTicks();
        data.punchDelay = golpe.windUp() + golpe.recuperacaoTicks();
        data.punchQueued = false;
        CombatMode.enter(attacker);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new ModPayloads.ComboAnimation(attacker.getId(), step));
        if (golpe.avanco() > 0) {
            Vec3 look = Combat.flatLook(attacker).scale(golpe.avanco());
            Vec3 now = attacker.getDeltaMovement();
            attacker.setDeltaMovement(now.x + look.x, now.y, now.z + look.z);
            attacker.hurtMarked = true;
        }
        int interrupts = data.interrupts;
        Tasks.later(golpe.windUp(), () -> {
            if (data.interrupts == interrupts && land(attacker, target, golpe, spec) && onHit != null) {
                onHit.run();
            }
        });
    }

    // Returns true when the hit connected (blocked or not).
    private static boolean land(LivingEntity attacker, LivingEntity target, ComboSpec.Golpe golpe, ComboSpec spec) {
        // Hit out of the wind-up: the punch never comes out.
        if (!attacker.isAlive() || !Stun.canAttack(attacker) || Freeze.isFrozen(attacker)) {
            return false;
        }
        ServerLevel level = Combat.level(attacker);
        if (target == null || !Combat.isTarget(target, attacker) || attacker.distanceTo(target) > spec.alcance() + 1.2) {
            target = closestInFront(attacker, spec.alcance(), 0.55);
        }
        if (target == null || !Combat.hurt(attacker, target, (float) golpe.dano() * multiplier(attacker))) {
            SkillSounds.play(level, spec.sons(), "erro", 0, 0, onde -> Combat.handPos(attacker), List.of());
            return false;
        }
        LivingEntity hit = target;
        // Guarded: the defense already played its effects; no stun, no hitstop.
        if (Defense.blockedThisTick(hit)) {
            return true;
        }
        // On the side of the body facing the puncher.
        Vec3 toAttacker = attacker.getEyePosition().subtract(Combat.center(hit));
        Vec3 hitPos = Combat.center(hit).add(toAttacker.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toAttacker.normalize().scale(hit.getBbWidth() * 0.5));
        Vec3 look = attacker.getLookAngle();
        Stun.apply(hit, golpe.stunTicks());
        SkillSounds.play(level, spec.sons(), golpe.momento(), 0, 0, onde -> "alvo".equals(onde) ? hitPos : Combat.handPos(attacker), List.of());
        Fx.effects(level, spec.skill(), false, golpe.momento(), hitPos, look);
        Fx.burst(level, Fx.dust(fistColor(attacker), 1.1f), hitPos, 5, 0.15, 0);
        int stop = golpe.hitstopTicks();
        if (stop > 0) {
            Freeze.hold(hit, stop);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new ModPayloads.Hitstop(attacker.getId(), stop));
        }
        shake(attacker, golpe.tremor(), stop + 4);
        shake(hit, golpe.tremor() * 1.3f, stop + 4);
        Tasks.later(stop, () -> {
            if (hit.isAlive()) {
                Combat.push(hit, look, golpe.empurrao(), golpe.subida());
            }
        });
        return true;
    }

    private static void shake(LivingEntity entity, float strength, int ticks) {
        if (strength > 0 && entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.ScreenShake(strength, ticks));
        }
    }

    // The target in front that the aim points at most, within range.
    private static LivingEntity closestInFront(LivingEntity attacker, double range, double minDot) {
        LivingEntity aimed = Combat.raycastTarget(attacker, range);
        if (aimed != null) {
            return aimed;
        }
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getLookAngle();
        return Combat.inCone(attacker, range, minDot).stream()
                .filter(attacker::hasLineOfSight)
                .max(Comparator.comparingDouble(e -> Combat.center(e).subtract(eye).normalize().dot(look) - e.distanceTo(attacker) * 0.05))
                .orElse(null);
    }

    // Short wind-up, then a heavy punch that sends the target flying. Holding the base attack key charges it: up to
    // STRONG_CHARGE_TICKS more, for up to STRONG_CHARGE_BONUS more damage and a harder throw. Bosses never hold it.
    private static boolean strong(LivingEntity player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        float mult = multiplier(player);
        int color = fistColor(player);
        int interrupts = data.interrupts;
        int windup = 8;
        data.busyTicks = windup + 2;
        Fx.sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        Tasks.add(age -> {
            if (!player.isAlive() || data.interrupts != interrupts || !Stun.canAttack(player)) {
                return true;
            }
            boolean charging = age < windup || data.attackHeld && age < windup + STRONG_CHARGE_TICKS;
            float charge = Mth.clamp((age - windup) / (float) STRONG_CHARGE_TICKS, 0, 1);
            Vec3 hand = Combat.handPos(player);
            if (charging) {
                data.busyTicks = 2;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                // Energy gathering on the fist, more and faster the longer it charges.
                int bits = 3 + (int) (charge * 5);
                for (int i = 0; i < bits; i++) {
                    Vec3 from = hand.add((level.random.nextDouble() - 0.5) * 2, (level.random.nextDouble() - 0.5) * 2, (level.random.nextDouble() - 0.5) * 2);
                    Fx.moving(level, Fx.dust(color, 1f + charge), from, hand.subtract(from).scale(0.25 + charge * 0.15));
                }
                Fx.burst(level, ParticleTypes.CRIT, hand, 2, 0.1, 0.05);
                if (age > windup && (age - windup) % 6 == 0) {
                    Fx.burst(level, ModParticles.ring(lighter(color), 0.6f + charge, 5), hand, 1, 0, 0);
                    Fx.sound(level, hand, SoundEvents.BREEZE_CHARGE, 0.5f, 0.8f + charge * 0.8f);
                }
                if (age == windup + STRONG_CHARGE_TICKS - 1) {
                    Fx.sound(level, hand, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
                    Fx.burst(level, ModParticles.ring(0xFFFFFF, 1.8f, 7), hand, 1, 0, 0);
                }
                return false;
            }
            float power = 1 + STRONG_CHARGE_BONUS * charge;
            Vec3 look = player.getLookAngle();
            Vec3 front = player.getEyePosition().add(look.scale(2.5));
            LivingEntity target = Combat.raycastTarget(player, 4.5);
            Fx.burst(level, ParticleTypes.SWEEP_ATTACK, front, 1, 0, 0);
            Fx.line(level, ParticleTypes.CLOUD, hand, front.add(look.scale(2)), 0.4);
            Fx.burst(level, ModParticles.fist(lighter(color), 0.6f + charge * 0.5f, 5), front, 1, 0, 0);
            Fx.sound(level, front, SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.6f - charge * 0.15f);
            if (target != null) {
                Vec3 hitPos = Combat.center(target);
                Defense.breakGuard(target, player);
                Combat.hurt(player, target, 10f * mult * power);
                Combat.push(target, look, 2.6 * (1 + 0.8 * charge), 0.6 + 0.3 * charge);
                Fx.burst(level, ParticleTypes.EXPLOSION, hitPos, 2 + (int) (charge * 3), 0.3, 0);
                Fx.burst(level, Fx.dust(color, 2f), hitPos, 25, 0.5, 0);
                Fx.burst(level, ModParticles.ring(0xFFFFFF, 1.5f + charge * 2, 8), hitPos, 1, 0, 0);
                Fx.sound(level, hitPos, SoundEvents.GENERIC_EXPLODE.value(), 0.6f + charge * 0.6f, 1.6f - charge * 0.6f);
            }
            return true;
        });
        return true;
    }

    private static boolean barrageMove(LivingEntity player, ServerLevel level) {
        barrage(player, 20, BARRAGE_HELD_TICKS, 4.0, 1.5f * multiplier(player), 6f * multiplier(player), fistColor(player));
        return true;
    }

    public static void barrage(LivingEntity player, int ticks, double range, float hitDamage, float finisherDamage, int fistColor) {
        barrage(player, ticks, ticks, range, hitDamage, finisherDamage, fistColor);
    }

    // Rapid punches in a cone in front of the player, ending with a finisher that launches whoever is in front.
    // Lasts ticks, or up to maxTicks while the base attack key is held. Also used by powers whose own moves are bigger
    // barrages. Drawn as fists flying out and wind lines along the punches.
    public static void barrage(LivingEntity player, int ticks, int maxTicks, double range, float hitDamage, float finisherDamage, int fistColor) {
        ServerLevel level = Combat.level(player);
        PowerData data = player.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        int fist = lighter(fistColor);
        data.busyTicks = ticks + 4;
        Tasks.add(age -> {
            if (!player.isAlive() || data.interrupts != interrupts || !Stun.canAttack(player)) {
                return true;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            if (age < ticks || data.attackHeld && age < maxTicks) {
                data.busyTicks = 3;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 1, false, false));
                Vec3 hand = Combat.handPos(player);
                for (int i = 0; i < 2; i++) {
                    Vec3 offset = new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(1.3);
                    Vec3 at = eye.add(look.scale(range * 0.35 + level.random.nextDouble() * range * 0.4)).add(offset);
                    Fx.moving(level, ModParticles.fist(fist, 0.28f + level.random.nextFloat() * 0.18f, 4), at, look.scale(0.18));
                    Vec3 from = hand.add(offset.scale(0.5));
                    Fx.moving(level, ModParticles.streak(0xFFFFFF, 0.55f, 3), from.add(look.scale(0.6)), look.scale(0.9));
                }
                if (age % 2 == 0) {
                    if (age % 4 == 0) {
                        Fx.burst(level, ModParticles.ring(0xFFFFFF, 0.9f, 5), eye.add(look.scale(range * 0.6)), 1, 0, 0);
                    }
                    for (LivingEntity target : Combat.inCone(player, range, 0.55)) {
                        if (Combat.hurt(player, target, hitDamage)) {
                            Fx.burst(level, ModParticles.fist(fist, 0.4f, 3), Combat.center(target).subtract(look.scale(target.getBbWidth() * 0.5)), 1, 0.15, 0);
                        }
                        // Keeps the target in front of the player for the whole barrage.
                        Combat.push(target, look, 0.2, 0.05);
                    }
                    Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.8f + level.random.nextFloat() * 0.6f);
                }
                return false;
            }
            Vec3 front = eye.add(look.scale(2));
            Fx.burst(level, ParticleTypes.EXPLOSION, front, 1, 0, 0);
            Fx.burst(level, ParticleTypes.GUST, front, 1, 0, 0);
            Fx.burst(level, ModParticles.fist(fist, 0.9f, 6), front, 1, 0, 0);
            Fx.burst(level, ModParticles.ring(0xFFFFFF, 2.2f, 8), front, 1, 0, 0);
            for (int i = 0; i < 8; i++) {
                Vec3 offset = new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(1.6);
                Fx.moving(level, ModParticles.streak(0xFFFFFF, 0.8f, 5), front.add(offset), look.scale(1.1));
            }
            Fx.sound(level, front, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
            for (LivingEntity target : Combat.inCone(player, range, 0.55)) {
                Combat.hurt(player, target, finisherDamage);
                Combat.push(target, look, 2.2, 0.6);
            }
            data.busyTicks = 4;
            return true;
        });
    }

    // The fist color pulled towards white, so fists and rings read as light even for dark powers.
    private static int lighter(int color) {
        int r = (color >> 16 & 0xFF) + (255 - (color >> 16 & 0xFF)) * 3 / 5;
        int g = (color >> 8 & 0xFF) + (255 - (color >> 8 & 0xFF)) * 3 / 5;
        int b = (color & 0xFF) + (255 - (color & 0xFF)) * 3 / 5;
        return r << 16 | g << 8 | b;
    }
}
