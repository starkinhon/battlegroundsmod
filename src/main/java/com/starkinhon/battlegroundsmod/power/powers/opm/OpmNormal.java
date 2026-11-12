package com.starkinhon.battlegroundsmod.power.powers.opm;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Saitama's normal mode (the approved page's second row): weak on purpose next to the serious moves, a bored face, no
// silence, no destruction, but his normal blows hit harder than most (Stark's port rules, 2026-11-11).
// Z Socos Consecutivos: a short step and 14 punches in 1.1 s; the ghosts of the arms the eye missed fill the air; the last
// one throws the target 8 blocks. X Gancho: low, then the fist straight up: the target goes about 20 blocks up, and the
// hook CUTS whatever the target was charging. C Chute: a step and the leg straight out: the target slides 12+ blocks
// back, guard broken, stunned. B Counter: he waits; the blow that comes is caught with one hand ("ok."), and a flick on
// the forehead sends them spinning away.
public final class OpmNormal {
    private static final double REACH = 3.4;

    private OpmNormal() {
    }

    // The one in front: the aimed target, or the nearest in a cone ahead.
    static LivingEntity front(LivingEntity caster, double range) {
        LivingEntity aimed = SubCommon.aimed(caster, range + 1);
        if (aimed != null) {
            return aimed;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        for (LivingEntity e : Combat.inRadius((ServerLevel) caster.level(), eye, range + 1, caster)) {
            Vec3 to = Combat.center(e).subtract(eye);
            double d = to.length();
            if (d < bestD && to.normalize().dot(look) > 0.45) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    private static Vec3 flatFrom(LivingEntity caster, LivingEntity target) {
        Vec3 d = target.position().subtract(caster.position());
        d = new Vec3(d.x, 0, d.z);
        return d.lengthSqr() < 1.0E-4 ? SubCommon.flat(caster.getLookAngle()) : d.normalize();
    }

    static void launch(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        // (again next tick: the first can be eaten by the hit's own knockback)
        Tasks.later(1, () -> {
            if (target.isAlive()) {
                target.setDeltaMovement(velocity);
                target.hurtMarked = true;
            }
        });
        SubNet.send(level, SubNet.OPM_LAUNCH, target.getId(), caster.position(), velocity, 0, 0, 0);
    }

    // ---------- Z: Socos Consecutivos ----------

    static final double NS0 = 0.25, NSD = 0.075;
    static final int NSN = 14;

    static boolean socos(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity first = front(caster, REACH + 1);
        SubCommon.pose(caster, "opm.socos");
        SubCommon.busy(caster, 28);
        SubNet.send(level, SubNet.OPM_SOCOS, caster.getId(), caster.position(), caster.getLookAngle(), first == null ? -1 : first.getId(), 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
            double t0 = (age - 1) / 20.0, t1 = age / 20.0;
            for (int i = 0; i < NSN; i++) {
                double hitT = NS0 + i * NSD + NSD * 0.4;
                if (hitT <= t0 || hitT > t1) {
                    continue;
                }
                boolean last = i == NSN - 1;
                for (LivingEntity target : Combat.inCone(caster, REACH, 0.5)) {
                    Combat.hurt(caster, target, last ? 8f : 3f);
                    if (last) {
                        launch(level, caster, target, flatFrom(caster, target).scale(0.9).add(0, 0.25, 0));
                        SubNet.send(level, SubNet.OPM_FLASH, target.getId(), Combat.center(target), Vec3.ZERO, 0.95f, 1, 0);
                        SubNet.send(level, SubNet.OPM_RING, 0, target.position(), Vec3.ZERO, 4, 0.8f, 0);
                        Stun.apply(target, 12);
                    } else {
                        // held in front of the fists (no knockback: they don't fall out of the barrage)
                        target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
                        target.hurtMarked = true;
                        Stun.apply(target, 8);
                    }
                }
                SubCommon.sound(level, caster.getEyePosition().add(caster.getLookAngle().scale(2)), last ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                        last ? 1.2f : 0.6f, last ? 0.8f : 1.2f + 0.05f * i);
            }
            return t1 > NS0 + NSN * NSD + 0.1;
        });
        return true;
    }

    // ---------- X: Gancho ----------

    static boolean gancho(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "opm.gancho");
        SubCommon.busy(caster, 10);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < 7) {
                return false;
            }
            LivingEntity target = front(caster, REACH);
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.9f);
            if (target == null) {
                return true;
            }
            // The hook cuts whatever the target was charging (Stark's rule).
            PowerData data = target.hasData(ModAttachments.POWER) ? target.getData(ModAttachments.POWER) : null;
            if (data != null && (data.busyTicks > 0 || Stamina.holding(target))) {
                PowerLogic.interrupt(target);
            }
            Combat.hurt(caster, target, 9f);
            Stun.apply(target, 30);
            launch(level, caster, target, flatFrom(caster, target).scale(0.03).add(0, 1.05, 0));
            Vec3 c = target.position().add(0, 1.6, 0).subtract(flatFrom(caster, target).scale(0.35));
            SubNet.send(level, SubNet.OPM_FLASH, target.getId(), c, Vec3.ZERO, 0.85f, 1, 0);
            SubNet.send(level, SubNet.OPM_RING, 0, target.position(), Vec3.ZERO, 3.5f, 0.8f, 0);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.6f);
            return true;
        });
        return true;
    }

    // ---------- C: Chute ----------

    static boolean chute(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "opm.chute");
        SubCommon.busy(caster, 9);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < 6) {
                return false;
            }
            LivingEntity target = front(caster, REACH + 0.6);
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
            if (target == null) {
                return true;
            }
            Defense.breakGuard(target, caster);
            Combat.hurt(caster, target, 8f);
            // (bosses take half: 25 left, still a real stun)
            Stun.apply(target, 50);
            Vec3 dir = flatFrom(caster, target);
            launch(level, caster, target, dir.scale(1.3).add(0, 0.22, 0));
            Vec3 c = target.position().add(0, 1.0, 0).subtract(dir.scale(0.45));
            SubNet.send(level, SubNet.OPM_FLASH, target.getId(), c, dir, 0.85f, 1, 0);
            SubNet.send(level, SubNet.OPM_RING, 0, caster.position().add(dir), Vec3.ZERO, 3f, 0.7f, 0);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.5f);
            return true;
        });
        return true;
    }

    // ---------- B: Counter ----------

    static final int COUNTER_WINDOW = 12;

    static boolean counter(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.setState(caster, Opm.COUNTER, COUNTER_WINDOW);
        SubCommon.pose(caster, "opm.counter_espera");
        SubCommon.busy(caster, COUNTER_WINDOW);
        return true;
    }

    // The blow came: he holds the fist ("ok."), then flicks the forehead.
    static void caught(LivingEntity caster, ServerLevel level, LivingEntity enemy) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "opm.counter");
        SubCommon.busy(caster, 22);
        Freeze.hold(enemy, 20);
        Vec3 dir = flatFrom(caster, enemy);
        Vec3 fist = caster.position().add(0, 1.45, 0).add(dir.scale(0.95)).add(new Vec3(dir.z, 0, -dir.x).scale(-0.32));
        SubNet.send(level, SubNet.OPM_FLASH, caster.getId(), fist, Vec3.ZERO, 0.4f, 0, 0);
        SubNet.send(level, SubNet.OPM_SAY, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, fist, SoundEvents.SHIELD_BLOCK, 1f, 1.4f);
        PowerData data = caster.getData(ModAttachments.POWER);
        data.ult = Math.min(PowerData.MAX_ULT, data.ult + 15);
        Tasks.add(age -> {
            if (guard.broken() || !enemy.isAlive()) {
                return true;
            }
            if (age < 19) {
                return false;
            }
            Vec3 head = enemy.position().add(0, enemy.getBbHeight() * 0.92, 0).subtract(dir.scale(0.3));
            Combat.hurt(caster, enemy, 12f);
            boolean boss = enemy instanceof SoulBoss b && b.isBoss();
            if (boss) {
                // Against a boss the flick only pushes (and a real stun).
                launch(level, caster, enemy, dir.scale(0.6).add(0, 0.2, 0));
            } else {
                launch(level, caster, enemy, dir.scale(1.7).add(0, 0.45, 0));
            }
            Stun.apply(enemy, 50);
            SubNet.send(level, SubNet.OPM_FLASH, enemy.getId(), head, dir, 0.8f, 2, 0);
            SubCommon.sound(level, head, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 1.6f);
            SubCommon.sound(level, head, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.7f);
            return true;
        });
    }
}
