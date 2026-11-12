package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Z Bang (forja/makima-bang, approved 2026-11-11). Index and thumb up, "Bang": the air ripples along the line, a shock
// shell on the body, red mist, the body thrown back folding over itself, a short manga impact frame and the BANG in
// big letters by her hand. Tap: the one she points at, 0.55 s later. Hold: her eyes lock one target at a time (rings
// round each), up to 3; letting go shoots them one after the other. Someone at 15% life or less is blown apart.
public final class Bang {
    private static final double RANGE = 40;
    private static final int TAP_SHOT = 11;
    private static final int LOCK_EVERY = 8;
    private static final int GAP = 4;
    private static final int MAX_LOCKS = 3;
    private static final float DAMAGE = 45;
    private static final float DAMAGE_LOCKED = 40;
    private static final float FINISH_LIFE = 0.15f;

    private Bang() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        LivingEntity first = Corrente.aimed(caster, level, RANGE);
        SubCommon.pose(caster, "makima.bang");
        Makima.eyes(caster, 40);
        SubCommon.Guard guard = SubCommon.guard(caster);
        List<LivingEntity> locked = new ArrayList<>();
        int[] lastLock = {-100};
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            boolean held = SubCommon.held(caster, age, 0);
            if (age < 6) {
                SubCommon.busy(caster, 3);
                if (!held) {
                    // A tap: the one she points at.
                    shootAll(caster, level, first == null ? List.of() : List.of(first), TAP_SHOT - age, guard);
                    return true;
                }
                return false;
            }
            // Held: lock one more every few ticks, the one in her aim (or the nearest in front of her) not locked yet.
            SubCommon.busy(caster, 3);
            if (locked.size() < MAX_LOCKS && age - lastLock[0] >= LOCK_EVERY) {
                LivingEntity next = nextLock(caster, level, locked);
                if (next != null) {
                    locked.add(next);
                    lastLock[0] = age;
                    MkNet.send(level, MkNet.MARK, next.getId(), next.position(), 200, 0, 0);
                    SubCommon.sound(level, caster.position(), SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.8f);
                    Makima.eyes(caster, 40);
                }
            }
            if (!held || age > 120) {
                if (locked.isEmpty() && first != null) {
                    locked.add(first);
                }
                shootAll(caster, level, locked, 4, guard);
                return true;
            }
            return false;
        });
        return true;
    }

    private static LivingEntity nextLock(LivingEntity caster, ServerLevel level, List<LivingEntity> locked) {
        LivingEntity aimed = Corrente.aimed(caster, level, RANGE);
        if (aimed != null && !locked.contains(aimed)) {
            return aimed;
        }
        // The nearest one in front (inside a 25 degree cone) that she can see.
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : Combat.inRadius(level, caster.position(), RANGE, caster)) {
            if (locked.contains(e) || Makima.ours(caster, e) || !e.isAlive()) {
                continue;
            }
            Vec3 to = Combat.center(e).subtract(eye);
            double d = to.length();
            double dot = to.scale(1 / Math.max(1e-4, d)).dot(look);
            if (dot < 0.9 || !Corrente.seen(level, eye, e)) {
                continue;
            }
            double score = (1 - dot) * 60 + d * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    // The shots, one after the other, the first one after delay ticks.
    private static void shootAll(LivingEntity caster, ServerLevel level, List<LivingEntity> targets, int delay, SubCommon.Guard guard) {
        if (targets.isEmpty()) {
            // Nobody in her aim: the finger still says it.
            Tasks.later(Math.max(0, delay), () -> {
                if (!guard.broken()) {
                    fire(caster, level, null, 0, 1);
                }
            });
            DbCommon.cooldown(caster, 0, 30);
            SubCommon.warn(caster, "makima.z_miss");
            return;
        }
        int n = targets.size();
        SubCommon.busy(caster, delay + (n - 1) * GAP + 6);
        Makima.eyes(caster, delay + (n - 1) * GAP + 30);
        for (int i = 0; i < n; i++) {
            LivingEntity t = targets.get(i);
            int index = i;
            Tasks.later(Math.max(0, delay) + i * GAP, () -> {
                if (!guard.broken()) {
                    fire(caster, level, t, index, n);
                }
            });
        }
        DbCommon.cooldown(caster, 0, n > 1 ? 140 : 60);
    }

    private static void fire(LivingEntity caster, ServerLevel level, LivingEntity t, int index, int total) {
        Vec3 tip = Makima.hand(caster);
        SubCommon.pose(caster, "makima.bang_tiro");
        boolean alive = t != null && t.isAlive() && !t.isRemoved() && t.distanceTo(caster) <= RANGE + 8;
        Vec3 hit = alive ? Combat.center(t).add(0, t.getBbHeight() * 0.12, 0) : Combat.raycastPoint(caster, RANGE);
        boolean finish = alive && t.getHealth() <= t.getMaxHealth() * FINISH_LIFE;
        MkNet.send(level, MkNet.BANG, caster.getId(), tip, hit.subtract(tip), finish ? 2 : 1, index, alive ? t.getId() : -1);
        SubCommon.sound(level, tip, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.9f);
        SubCommon.sound(level, tip, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 0.6f);
        if (!alive) {
            return;
        }
        Vec3 dir = SubCommon.flat(hit.subtract(caster.position()));
        MkNet.send(level, MkNet.SHELL, t.getId(), hit, finish ? 2.6f : 1.6f, 0, t.getId() * 1.37f);
        MkNet.send(level, MkNet.DUST, t.getId(), SubCommon.ground(level, t.position()), 7, 0, 0);
        MkNet.send(level, MkNet.IMPACT, caster.getId(), hit, finish ? 1 : 0, index == total - 1 ? 1 : 0, 0);
        if (finish) {
            // Blown apart: the whole body bursts (every piece its own size, color and flight).
            MkNet.send(level, MkNet.BITS, t.getId(), hit, dir.scale(4), 38, 1, 1);
            MkNet.send(level, MkNet.MIST, t.getId(), hit, dir, 150, 0, 0);
            MkNet.send(level, MkNet.MIST, t.getId(), hit, dir.scale(-1), 60, 0, 0);
            SubCommon.sound(level, hit, SoundEvents.SLIME_SQUISH, 1.6f, 0.5f);
            SubCommon.sound(level, hit, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.2f);
            if (Makima.boss(t)) {
                Combat.hurt(caster, t, 120);
                Stun.apply(t, 40);
            } else {
                Corrente.kill(caster, t, 400);
            }
            return;
        }
        MkNet.send(level, MkNet.MIST, t.getId(), hit.add(dir.scale(0.15)), dir, 70, 0, 0);
        Combat.hurt(caster, t, total > 1 ? DAMAGE_LOCKED : DAMAGE);
        Stun.apply(t, 20);
        Combat.push(t, dir, 1.6, 0.55);
        Tasks.later(1, () -> Combat.push(t, dir, 1.4, 0.45));
    }
}
