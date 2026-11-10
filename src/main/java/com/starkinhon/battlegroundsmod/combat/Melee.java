package com.starkinhon.battlegroundsmod.combat;

import java.util.Comparator;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;
import com.starkinhon.battlegroundsmod.skill.StaminaSpec;

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
// With a sword in hand the combo, the Forte, the Barragem and the Dash Attack are sword moves instead (SwordArts).
public final class Melee {
    public static final Ability STRONG = new Ability(60, 0, Melee::strong);
    public static final Ability BARRAGE = new Ability(140, 10, Melee::barrageMove);
    // Holding the base attack key: the Forte charges this much longer at most, for up to this much more damage...
    private static final int STRONG_CHARGE_TICKS = 30;
    private static final float STRONG_CHARGE_BONUS = 1.5f;
    // The bare-fist poses of the Forte and the Barragem (tools/poses, client/SkillAnimations FIST_PREFIX).
    private static final String STRONG_CHARGE = "soco.forte_carga";
    private static final String STRONG_PUNCH = "soco.forte_golpe";
    private static final int STRONG_HIT_DELAY = 3;
    private static final String BARRAGE_POSE = "soco.barragem";
    private static final String BARRAGE_END_POSE = "soco.barragem_final";
    // ...and the Barragem goes on for up to this long.
    private static final int BARRAGE_HELD_TICKS = 60;
    // How long after taking a later-thrown punch an earlier one still comes out (covers that punch's hitstop).
    private static final int PRIORITY_WINDOW_TICKS = 5;

    private Melee() {
    }

    static float multiplier(LivingEntity player) {
        Power power = Powers.get(player.getData(ModAttachments.POWER).power);
        float buff = com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon.meleeMultiplier(player)
                * com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.meleeMultiplier(player)
                * com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon.meleeMultiplier(player);
        return power == null ? buff : power.meleeMultiplier() * buff;
    }

    // How far a punch reaches: the combo's reach, plus the Sage Mode's 2 blocks (Kawazu Kumite).
    static double reach(LivingEntity attacker, ComboSpec spec) {
        return spec.alcance() + com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.extraReach(attacker);
    }

    static int fistColor(LivingEntity player) {
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
            target = closestInFront(attacker, reach(attacker, spec), 0.55);
        }
        ComboSpec.Passo passo = spec.passo();
        if (target == null) {
            target = closestInFront(attacker, passo.distanciaMax(), passo.cone());
            if (target != null) {
                chase(attacker, target, spec);
                return true;
            }
        } else if (attacker.distanceTo(target) > reach(attacker, spec) + 0.5) {
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
            if (!target.isAlive() || flat.length() <= reach(attacker, spec) - 0.6 || age >= passo.ticksMax()) {
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
        // Under the Kaioken the punches come faster.
        data.punchDelay = Math.max(1, Math.round((golpe.windUp() + golpe.recuperacaoTicks())
                / com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon.punchSpeed(attacker)));
        data.punchQueued = false;
        CombatMode.enter(attacker);
        Stamina.gain(attacker, StaminaSpec.get().ganhoGolpePct());
        boolean sword = SwordArts.held(attacker);
        int windUp = golpe.windUp();
        if (sword) {
            int extra = SwordArts.comboSwing(attacker, step);
            windUp += extra;
            data.punchDelay += extra;
        } else if (onHit == null && com.starkinhon.battlegroundsmod.power.powers.sukuna.SukunaMelee.active(attacker)) {
            // Sukuna: an open hand slashes the air and throws a Dismantle first, then the punch (not on the Dash
            // Attack's hit, which has its own cut at the start of the dash).
            int extra = com.starkinhon.battlegroundsmod.power.powers.sukuna.SukunaMelee.comboSwing(attacker, target, step,
                    (float) golpe.dano() * multiplier(attacker));
            windUp += extra;
            data.punchDelay += extra;
        } else {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new ModPayloads.ComboAnimation(attacker.getId(), step));
        }
        if (golpe.avanco() > 0) {
            Vec3 look = Combat.flatLook(attacker).scale(golpe.avanco());
            Vec3 now = attacker.getDeltaMovement();
            attacker.setDeltaMovement(now.x + look.x, now.y, now.z + look.z);
            attacker.hurtMarked = true;
        }
        int interrupts = data.interrupts;
        data.swingStart = attacker.level().getGameTime();
        Tasks.later(windUp, () -> {
            if (data.interrupts != interrupts) {
                return;
            }
            // Swapped the sword away mid-swing: it lands as the punch.
            boolean landed = sword && SwordArts.held(attacker) ? SwordArts.comboCut(attacker, target, step, golpe, spec)
                    : land(attacker, target, golpe, spec);
            if (landed && onHit != null) {
                onHit.run();
            }
        });
    }

    // Returns true when the hit connected (blocked or not).
    private static boolean land(LivingEntity attacker, LivingEntity target, ComboSpec.Golpe golpe, ComboSpec spec) {
        // Hit out of the wind-up: the punch never comes out (unless it started first, see struckFirst).
        if (!attacker.isAlive() || (!Stun.canAttack(attacker) || Freeze.isFrozen(attacker)) && !struckFirst(attacker)) {
            return false;
        }
        ServerLevel level = Combat.level(attacker);
        if (target == null || !Combat.isTarget(target, attacker) || attacker.distanceTo(target) > reach(attacker, spec) + 1.2) {
            target = closestInFront(attacker, reach(attacker, spec), 0.55);
        }
        if (target == null || !Combat.hurtBasic(attacker, target, (float) golpe.dano() * multiplier(attacker))) {
            SkillSounds.play(level, spec.sons(), "erro", 0, 0, onde -> Combat.handPos(attacker), List.of());
            return false;
        }
        Stamina.gain(attacker, StaminaSpec.get().ganhoAcertoPct());
        LivingEntity hit = target;
        markHit(attacker, hit);
        com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.onPunchLanded(attacker, hit);
        // Guarded: the defense already played its effects; no stun, no hitstop.
        if (Defense.blockedThisTick(hit)) {
            return true;
        }
        Vec3 hitPos = hitPos(attacker, hit);
        Vec3 look = attacker.getLookAngle();
        Stun.apply(hit, golpe.stunTicks());
        SkillSounds.play(level, spec.sons(), golpe.momento(), 0, 0, onde -> "alvo".equals(onde) ? hitPos : Combat.handPos(attacker), List.of());
        hitFx(attacker, hit, golpe.momento());
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

    // Punch priority: two punches thrown at each other, the one that started first wins. A punch already winding up is
    // not cut off by a punch that was thrown after it (that one still lands: they trade). A real stun still cuts it.
    static boolean struckFirst(LivingEntity attacker) {
        PowerData data = attacker.getData(ModAttachments.POWER);
        long now = attacker.level().getGameTime();
        return data.hitBySwing > data.swingStart && now - data.hitAt <= PRIORITY_WINDOW_TICKS && !Stun.real(attacker);
    }

    // Remembers on whoever a combo hit landed on when that swing had started (for struckFirst).
    static void markHit(LivingEntity attacker, LivingEntity hit) {
        PowerData data = hit.getData(ModAttachments.POWER);
        data.hitBySwing = attacker.getData(ModAttachments.POWER).swingStart;
        data.hitAt = hit.level().getGameTime();
    }

    // On the side of the body facing the puncher.
    private static Vec3 hitPos(LivingEntity attacker, LivingEntity hit) {
        Vec3 toAttacker = attacker.getEyePosition().subtract(Combat.center(hit));
        return Combat.center(hit).add(toAttacker.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toAttacker.normalize().scale(hit.getBbWidth() * 0.5));
    }

    // The combo's hit look, shared by every punch: the air shock tinted with the soul (combat/PunchFx) for the momento
    // ("golpe" a jab, "final" the last hit, "dash" the Dash Attack), plus padrao.combo.json's vfx of that momento if any.
    static void hitFx(LivingEntity attacker, LivingEntity hit, String momento) {
        int kind = momento == null ? PunchFx.JAB : switch (momento) {
            case "final" -> PunchFx.FINAL;
            case "dash" -> PunchFx.DASH;
            default -> PunchFx.JAB;
        };
        hitFx(attacker, hit, momento, kind, 0);
    }

    static void hitFx(LivingEntity attacker, LivingEntity hit, String momento, int kind, float charge) {
        ServerLevel level = Combat.level(attacker);
        Vec3 at = hitPos(attacker, hit);
        Fx.effects(level, ComboSpec.get().skill(), false, momento, at, attacker.getLookAngle());
        PunchFx.hit(level, kind, at, attacker.getLookAngle(), fistColor(attacker), charge, hit.getY());
    }

    static void shake(LivingEntity entity, float strength, int ticks) {
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
        if (SwordArts.held(player)) {
            return SwordArts.strong(player, level);
        }
        PowerData data = player.getData(ModAttachments.POWER);
        float mult = multiplier(player);
        int color = fistColor(player);
        int interrupts = data.interrupts;
        int windup = 8;
        data.busyTicks = windup + 2;
        anim(player, STRONG_CHARGE);
        Fx.sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        float[] released = {-1};
        int[] releasedAt = {-1};
        Tasks.add(age -> {
            // Super armor: plain punches hurt but don't cut the Forte off; only a real stun (Stun.INTERRUPT_TICKS+) does.
            if (!player.isAlive() || data.interrupts != interrupts || Stun.real(player)) {
                if (released[0] < 0) {
                    anim(player, "");
                }
                return true;
            }
            boolean charging = released[0] < 0 && (age < windup || data.attackHeld && age < windup + STRONG_CHARGE_TICKS);
            float charge = released[0] >= 0 ? released[0] : Mth.clamp((age - windup) / (float) STRONG_CHARGE_TICKS, 0, 1);
            Vec3 hand = Combat.handPos(player);
            if (charging) {
                data.busyTicks = 2;
                if (data.attackHeld) {
                    Charge.report(player, charge);
                }
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                // Vapor rings tinted with the soul closing on the fist, stronger the longer it charges.
                PunchFx.follow(level, PunchFx.CHARGE, player, player.getLookAngle(), color, charge);
                if (age > windup && (age - windup) % 6 == 0) {
                    Fx.sound(level, hand, SoundEvents.BREEZE_CHARGE, 0.5f, 0.8f + charge * 0.8f);
                }
                if (age == windup + STRONG_CHARGE_TICKS - 1) {
                    Fx.sound(level, hand, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
                }
                return false;
            }
            if (released[0] < 0) {
                // Let go (or fully charged): the punch swings and lands STRONG_HIT_DELAY ticks later, when the fist
                // reaches the front in soco.forte_golpe.
                released[0] = charge;
                releasedAt[0] = age;
                data.busyTicks = STRONG_HIT_DELAY + 4;
                if (com.starkinhon.battlegroundsmod.power.powers.sukuna.SukunaMelee.active(player)) {
                    // Sukuna: the fist stays cocked while the left hand throws a big Dismantle, then the fist lands.
                    anim(player, com.starkinhon.battlegroundsmod.power.powers.sukuna.SukunaMelee.FORTE_POSE);
                    com.starkinhon.battlegroundsmod.power.powers.sukuna.SukunaMelee.forteCut(player, Combat.raycastTarget(player, 6),
                            25f * mult * (1 + STRONG_CHARGE_BONUS * charge));
                } else {
                    anim(player, STRONG_PUNCH);
                }
                return false;
            }
            if (age - releasedAt[0] < STRONG_HIT_DELAY) {
                return false;
            }
            float power = 1 + STRONG_CHARGE_BONUS * charge;
            Vec3 look = player.getLookAngle();
            Vec3 front = player.getEyePosition().add(look.scale(2.5));
            LivingEntity target = Combat.raycastTarget(player, 4.5);
            if (target == null) {
                PunchFx.swing(level, front, look, color, 1 + 0.5f * charge);
            }
            Fx.sound(level, front, SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.6f - charge * 0.15f);
            if (target != null) {
                Vec3 hitPos = Combat.center(target);
                Defense.breakGuard(target, player);
                if (Combat.hurtBasic(player, target, 25f * mult * power)) {
                    hitFx(player, target, "final", PunchFx.FORTE, charge);
                    Stamina.gain(player, StaminaSpec.get().ganhoAcertoPct());
                    shake(target, 0.5f + charge * 0.5f, 8);
                }
                Combat.push(target, look, 2.6 * (1 + 0.8 * charge), 0.6 + 0.3 * charge);
                Fx.sound(level, hitPos, SoundEvents.GENERIC_EXPLODE.value(), 0.6f + charge * 0.6f, 1.6f - charge * 0.6f);
            }
            return true;
        });
        return true;
    }

    private static boolean barrageMove(LivingEntity player, ServerLevel level) {
        if (SwordArts.held(player)) {
            return SwordArts.barrage(player, level);
        }
        barrage(player, 20, BARRAGE_HELD_TICKS, 4.0, 3.75f * multiplier(player), 15f * multiplier(player), fistColor(player), true);
        return true;
    }

    public static void barrage(LivingEntity player, int ticks, double range, float hitDamage, float finisherDamage, int fistColor) {
        barrage(player, ticks, ticks, range, hitDamage, finisherDamage, fistColor);
    }

    // Rapid punches in a cone in front of the player, ending with a finisher that launches whoever is in front.
    // Lasts ticks, or up to maxTicks while the base attack key is held. Also used by powers whose own moves are bigger
    // barrages. Drawn as wind and speed lines along the punches.
    public static void barrage(LivingEntity player, int ticks, int maxTicks, double range, float hitDamage, float finisherDamage, int fistColor) {
        barrage(player, ticks, maxTicks, range, hitDamage, finisherDamage, fistColor, false);
    }

    // poses: plays the base move's own punches (soco.barragem, then soco.barragem_final); the powers' barrages keep
    // their own poses.
    private static void barrage(LivingEntity player, int ticks, int maxTicks, double range, float hitDamage, float finisherDamage, int fistColor, boolean poses) {
        ServerLevel level = Combat.level(player);
        PowerData data = player.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        data.busyTicks = ticks + 4;
        if (poses) {
            anim(player, BARRAGE_POSE);
        }
        Tasks.add(age -> {
            // Super armor, like the Forte: only a real stun cuts the Barragem off.
            if (!player.isAlive() || data.interrupts != interrupts || Stun.real(player)) {
                if (poses) {
                    anim(player, "");
                }
                return true;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            if (age < ticks || data.attackHeld && age < maxTicks) {
                data.busyTicks = 3;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 1, false, false));
                if (age % 2 == 0) {
                    // Each punch pushes a small tinted vapor ring into the air in front.
                    Vec3 offset = new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5).scale(0.9);
                    PunchFx.swing(level, eye.add(look.scale(range * 0.4)).add(offset), look, fistColor, 0.4f);
                    for (LivingEntity target : Combat.inCone(player, range, 0.55)) {
                        if (Combat.hurtBasic(player, target, hitDamage)) {
                            hitFx(player, target, "golpe", PunchFx.BARRAGEM, 0);
                            Stamina.gain(player, StaminaSpec.get().ganhoAcertoBarragemPct());
                            shake(target, 0.15f, 4);
                        }
                        // Keeps the target in front of the player for the whole barrage.
                        Combat.push(target, look, 0.2, 0.05);
                    }
                    Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.8f + level.random.nextFloat() * 0.6f);
                }
                return false;
            }
            boolean sukuna = poses && com.starkinhon.battlegroundsmod.power.powers.sukuna.SukunaMelee.active(player);
            if (poses) {
                anim(player, sukuna ? com.starkinhon.battlegroundsmod.power.powers.sukuna.SukunaMelee.BARRAGE_END_POSE : BARRAGE_END_POSE);
            }
            Vec3 front = eye.add(look.scale(2));
            PunchFx.swing(level, front, look, fistColor, 1.1f);
            Fx.sound(level, front, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
            List<LivingEntity> finished = Combat.inCone(player, range, 0.55);
            if (sukuna) {
                // Sukuna: with the right fist still out, the left hand throws one more Dismantle at whoever flies away.
                com.starkinhon.battlegroundsmod.power.powers.sukuna.SukunaMelee.barrageCut(player,
                        finished.stream().min(Comparator.comparingDouble(e -> e.distanceTo(player))).orElse(null), finisherDamage);
            }
            for (LivingEntity target : finished) {
                if (Combat.hurtBasic(player, target, finisherDamage)) {
                    hitFx(player, target, "final");
                    Stamina.gain(player, StaminaSpec.get().ganhoAcertoPct());
                    shake(target, 0.6f, 8);
                }
                Combat.push(target, look, 2.2, 0.6);
            }
            data.busyTicks = 4;
            return true;
        });
    }

    // The fist color pulled towards white, so wind and rings read as light even for dark powers.
    static int lighter(int color) {
        int r = (color >> 16 & 0xFF) + (255 - (color >> 16 & 0xFF)) * 3 / 5;
        int g = (color >> 8 & 0xFF) + (255 - (color >> 8 & 0xFF)) * 3 / 5;
        int b = (color & 0xFF) + (255 - (color & 0xFF)) * 3 / 5;
        return r << 16 | g << 8 | b;
    }

    private static void anim(LivingEntity entity, String move) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.PlaySkillAnimation(entity.getId(), move, false));
    }
}
