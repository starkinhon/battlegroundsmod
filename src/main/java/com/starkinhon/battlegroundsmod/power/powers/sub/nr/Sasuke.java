package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbScar;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Sasuke (SubAlma of Naruto, tier 6), the Visualizador's Sasuke tab.
// Z Chidori: held, the hand becomes a bundle of chirping lightning scratching the ground; the longer, the farther he goes
//   (8 to 22 blocks: Stark, 2026-11-09, "he has to run to the enemy"). Let go: a dash that goes through the target, turning
//   onto whoever is in front within that reach, and the hand rips a furrow in the ground behind him. A target paralyzed by
//   the Senbon can't dodge it: it is a critical hit.
// X Sharingan: a reading stance of 1 s. Hit in that window (punch or shot), he reads it: no damage, an afterimage stays
//   and he appears behind the attacker with a spinning kick. Nobody attacked: "nothing to read" and a short cooldown.
// C Chidori Senbon: a fan of 9 lightning needles (60°, 14 blocks); who takes 2 or more is paralyzed 3.5 s with sparks on
//   the body, the whole of it on a boss too (a real stun: it opens the stun combos); one needle still stuns 1.5 s.
// V Kirin, one of the strongest moves of Naruto: storm clouds gather over the target (64 blocks); he raises his hand and a
//   dragon of lightning comes down on it: it can't be dodged (it follows the target). The blast is a storm of lightning
//   8 blocks round, bolts running along the ground, a deep crater. Open sky only.
public final class Sasuke {
    private Sasuke() {
    }

    public static Power create() {
        return new Power("sasuke", 0x5A3A8A, 1.05f, List.of(
                new Ability(150, 0, Sasuke::chidori),
                new Ability(240, 0, Sasuke::read),
                new Ability(200, 0, Sasuke::senbon),
                new Ability(1100, 0, Sasuke::kirin)));
    }

    static Vec3 lowHand(LivingEntity caster) {
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        return caster.position().add(0, caster.getBbHeight() * 0.4, 0).add(right.scale(0.45)).add(look.scale(0.1));
    }

    // ---------- Z: Chidori ----------

    private static boolean chidori(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int full = 50;
        int bossHold = 30 + SubCommon.RANDOM.nextInt(21);
        SubCommon.pose(caster, "nr.chidori");
        SubCommon.busy(caster, full + 12);
        int[] release = {-1};
        Vec3[] start = {null};
        Vec3[] dir = {null};
        double[] dist = {0};
        Map<Integer, Boolean> hit = new HashMap<>();
        Vec3[] last = {null};
        int[] dashTicks = {8};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.CHIDORI, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (release[0] < 0) {
                float k = SubCommon.smooth(age / 20f);
                Vec3 hand = lowHand(caster);
                Vec3 scratch = SubCommon.ground(level, hand.add(SubCommon.flat(caster.getLookAngle()).scale(0.6)));
                SubNet.send(level, SubNet.CHIDORI, id, hand, scratch.subtract(hand), (0.28f + 0.1f * SubCommon.smooth(age / 40f)) * Math.max(0.3f, k), 0, 0);
                Charge.report(caster, Math.min(1, (age + 1) / (double) full));
                if (age % 3 == 0) {
                    SubCommon.sound(level, hand, SoundEvents.BEEHIVE_WORK, 0.9f, 2f);
                    SubCommon.sound(level, hand, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.3f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.4f);
                }
                if (age >= 8 && (age >= full || !SubCommon.held(caster, age, bossHold))) {
                    release[0] = age;
                    // Long reach (Stark, 2026-11-09): 8 blocks tapped, 22 held in full.
                    dist[0] = 8 + 14 * Math.min(1, age / (double) full);
                    start[0] = caster.position();
                    Vec3 look = SubCommon.flat(caster.getLookAngle());
                    // He runs to the enemy in front: one paralyzed by the Senbon first (it can't dodge), else the closest in
                    // the aim, and goes 2 blocks through them.
                    LivingEntity goal = null;
                    for (LivingEntity t : Combat.inCone(caster, dist[0] + 2, 0.6)) {
                        if (SubCommon.hasState(t, NrCommon.PARALYZED) || SubCommon.hasState(t, NrCommon.MARKED)) {
                            goal = t;
                            break;
                        }
                    }
                    if (goal == null) {
                        goal = NrCommon.target(caster, dist[0] + 2);
                    }
                    if (goal != null && SubCommon.flat(goal.position().subtract(caster.position())).lengthSqr() > 0.01) {
                        look = SubCommon.flat(goal.position().subtract(caster.position()));
                        dist[0] = Math.min(dist[0] + 2, goal.distanceTo(caster) + 2);
                    }
                    dashTicks[0] = Math.max(6, (int) Math.ceil(dist[0] / 1.8));
                    // Nothing touches him while he runs (Stark, 2026-11-09: the bosses beat him up while he uses a
                    // skill): he goes through the blow.
                    SubCommon.setState(caster, NrCommon.UNTOUCHABLE, dashTicks[0] + 2);
                    SubCommon.busy(caster, dashTicks[0] + 4);
                    dir[0] = look;
                    last[0] = Combat.center(caster);
                    SubCommon.pose(caster, "nr.investida");
                    SubCommon.sound(level, caster.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
                }
                return false;
            }
            int a = age - release[0];
            if (a <= dashTicks[0]) {
                double speed = dist[0] / dashTicks[0];
                caster.setDeltaMovement(dir[0].x * speed, Math.min(caster.getDeltaMovement().y, 0.02), dir[0].z * speed);
                caster.hurtMarked = true;
                caster.fallDistance = 0;
                Vec3 now = Combat.center(caster);
                SubNet.send(level, SubNet.CHIDORI, id, lowHand(caster).add(dir[0].scale(0.4)), Vec3.ZERO, 0.38f, 0, 0);
                // The hand low by the ground rips a furrow along the run (soulsBlockDestruction; never under his feet).
                DbScar.along(caster, level, last[0].add(0, -0.6, 0), now.add(0, -0.6, 0), 0.45, 0);
                for (LivingEntity t : Combat.alongLine(level, last[0], now.add(dir[0].scale(0.8)), 0.9, caster)) {
                    if (hit.put(t.getId(), true) != null) {
                        continue;
                    }
                    boolean crit = SubCommon.hasState(t, NrCommon.PARALYZED) || SubCommon.hasState(t, NrCommon.MARKED);
                    SubCommon.clearState(t, NrCommon.MARKED);
                    if (SubCommon.hasState(t, NrCommon.PARALYZED)) {
                        Freeze.free(t);
                        SubCommon.clearState(t, NrCommon.PARALYZED);
                    }
                    Combat.hurtMagic(caster, t, crit ? 24 : 15);
                    Stun.apply(t, 30);
                    Combat.push(t, dir[0], 0.6, 0.2);
                    Vec3 c = Combat.center(t);
                    SubNet.send(level, SubNet.PIERCE, 0, c, dir[0], crit ? 1 : 0, 0, 0);
                    DbCommon.screen(level, c, 0);
                    SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 1.4f);
                    SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
                }
                last[0] = now;
                return false;
            }
            caster.setDeltaMovement(caster.getDeltaMovement().scale(0.2));
            caster.hurtMarked = true;
            SubNet.send(level, SubNet.CHIDORI, id, lowHand(caster), Vec3.ZERO, 0, 0, 1);
            Vec3 from = start[0].add(0, 0.9, 0);
            SubNet.send(level, SubNet.BOLT_TRAIL, 0, from, caster.position().add(0, 0.9, 0).subtract(from), 0.87f, 0, 0);
            return true;
        });
        return true;
    }

    // ---------- Sharingan: the reading that dodges ----------

    // How long the eyes stay on, and how many blows they read before the counter.
    private static final int READ_TICKS = 120;
    private static final int READ_DODGES = 2;
    // A blow this strong (a Divine Soul's ult) is too much to read: it lands.
    private static final float READ_MAX = 45;
    private static final Map<Integer, Integer> dodges = new HashMap<>();
    // The Sharingan's key slot (its cooldown).
    private static final int READ_SLOT = 1;
    private static final int READ_COOLDOWN = 240;

    private static boolean read(LivingEntity caster, ServerLevel level) {
        dodges.put(caster.getId(), 0);
        SubCommon.setState(caster, NrCommon.READ, READ_TICKS);
        SubCommon.pose(caster, "nr.leitura");
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, READ_TICKS, 1, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 1.4f);
        int id = caster.getId();
        cooldown(caster, READ_TICKS + 10);
        Tasks.later(READ_TICKS, () -> {
            Integer n = dodges.get(id);
            // Still the same reading (not ended by the counter): nobody attacked, "nothing to read" and a short cooldown.
            if (n != null) {
                dodges.remove(id);
                SubCommon.clearState(caster, NrCommon.READ);
                if (n == 0) {
                    SubCommon.warn(caster, "sasuke.nothing_to_read");
                    cooldown(caster, 40);
                } else {
                    cooldown(caster, READ_COOLDOWN);
                }
            }
        });
        return false;
    }

    // The Sharingan's own cooldown (not when Kakashi copied it: the slot is another move of his).
    private static void cooldown(LivingEntity caster, int ticks) {
        if ("sasuke".equals(caster.getData(ModAttachments.POWER).power)) {
            DbCommon.cooldown(caster, READ_SLOT, ticks);
        }
    }

    // A blow (punch, shot or skill) was read: no damage, he slides to the free side and the blow goes through an
    // afterimage. Whoever he read is marked (the Chidori goes straight at them and is critical, one Senbon needle
    // paralyzes). The second blow read is answered: he appears behind them with a spinning kick and the eyes close.
    // Returns false when the blow is too strong to read.
    static boolean dodge(LivingEntity sasuke, ServerLevel level, LivingEntity enemy, float amount) {
        if (amount >= READ_MAX) {
            return false;
        }
        int n = dodges.merge(sasuke.getId(), 1, Integer::sum);
        SubCommon.setState(enemy, NrCommon.MARKED, 80);
        if (n >= READ_DODGES) {
            SubCommon.clearState(sasuke, NrCommon.READ);
            dodges.remove(sasuke.getId());
            counter(sasuke, level, enemy);
            return true;
        }
        SubCommon.setState(sasuke, NrCommon.UNTOUCHABLE, 8);
        Vec3 away = SubCommon.flat(sasuke.position().subtract(enemy.position()));
        if (away.lengthSqr() < 1.0E-4) {
            away = SubCommon.flat(sasuke.getLookAngle()).scale(-1);
        }
        Vec3 side = away.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 to = null;
        // The free side: left or right (the side away from the last dodge first), else straight back.
        int first = n % 2 == 0 ? 1 : -1;
        for (Vec3 d : new Vec3[] {side.scale(first), side.scale(-first), away}) {
            Vec3 p = sasuke.position().add(d.scale(2.6)).add(away.scale(0.6));
            if (!SubCommon.solid(level, p.add(0, 0.5, 0)) && !SubCommon.solid(level, p.add(0, 1.5, 0))) {
                to = p;
                break;
            }
        }
        if (to == null) {
            to = sasuke.position();
        }
        Vec3 left = sasuke.position();
        SubNet.send(level, SubNet.AFTERIMAGE, sasuke.getId(), left, to.subtract(left), sasuke.getBbHeight(), 0, 0);
        SubCommon.sound(level, left, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.9f);
        SubCommon.sound(level, left, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        NrCommon.teleport(sasuke, to, NrCommon.yawTo(to, enemy.position()));
        sasuke.setDeltaMovement(Vec3.ZERO);
        sasuke.hurtMarked = true;
        return true;
    }

    // The second blow read: an afterimage stays, he appears behind the attacker and kicks them away.
    static void counter(LivingEntity sasuke, ServerLevel level, LivingEntity enemy) {
        cooldown(sasuke, READ_COOLDOWN);
        SubCommon.setState(sasuke, NrCommon.UNTOUCHABLE, 12);
        Vec3 dir = SubCommon.flat(enemy.position().subtract(sasuke.position()));
        Vec3 behind = enemy.position().add(dir.scale(1.6));
        if (SubCommon.solid(level, behind.add(0, 0.5, 0)) || SubCommon.solid(level, behind.add(0, 1.5, 0))) {
            behind = enemy.position().add(dir.scale(0.8));
        }
        Vec3 left = sasuke.position();
        SubNet.send(level, SubNet.AFTERIMAGE, sasuke.getId(), left, behind.subtract(left), sasuke.getBbHeight(), 0, 0);
        SubCommon.sound(level, left, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
        NrCommon.teleport(sasuke, behind, NrCommon.yawTo(behind, enemy.position()));
        SubNet.send(level, SubNet.EYES, sasuke.getId(), sasuke.getEyePosition(), Vec3.ZERO, 30, 1, 0);
        Tasks.later(4, () -> SubCommon.pose(sasuke, "nr.chute_giro"));
        Tasks.later(8, () -> {
            if (!sasuke.isAlive() || !enemy.isAlive()) {
                return;
            }
            Combat.hurt(sasuke, enemy, 9);
            Stun.apply(enemy, 30, true);
            Combat.push(enemy, dir.scale(-1), 1.6, 0.45);
            Vec3 c = Combat.center(enemy);
            DbCommon.hit(level, c, 0.8f, Db.RED);
            DbCommon.screen(level, c, 3);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.8f);
        });
    }

    // ---------- C: Chidori Senbon ----------

    private static boolean senbon(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        // Half the first port's wind-up: it has to land before a boss closes in.
        int out = 10;
        SubCommon.pose(caster, "nr.senbon");
        SubCommon.busy(caster, out + 4);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.CHIDORI, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            Vec3 hand = DbCommon.hand(caster);
            if (age < out) {
                SubNet.send(level, SubNet.CHIDORI, id, hand, Vec3.ZERO, 0.18f * SubCommon.smooth((age - 1) / 7f), 0, 0);
                if (age % 4 == 0) {
                    SubCommon.sound(level, hand, SoundEvents.BEEHIVE_WORK, 0.6f, 2f);
                }
                return false;
            }
            SubNet.send(level, SubNet.CHIDORI, id, hand, Vec3.ZERO, 0, 0, 1);
            SubCommon.sound(level, hand, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 2f);
            SubCommon.sound(level, hand, SoundEvents.ARROW_SHOOT, 1f, 1.8f);
            Vec3 look = caster.getLookAngle();
            Map<LivingEntity, Integer> count = new HashMap<>();
            for (int i = 0; i < 9; i++) {
                double ang = (i - 4) / 4.0 * 0.52;
                Vec3 d = look.yRot((float) -ang).add(0, (Db.hash(i, 4, 1) - 0.5) * 0.08, 0).normalize();
                Vec3 far = hand.add(d.scale(14));
                var clip = level.clip(new ClipContext(hand, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                Vec3 end = clip.getType() == HitResult.Type.MISS ? far : clip.getLocation();
                SubNet.send(level, SubNet.SENBON, 0, hand, end.subtract(hand), 0, 0, 0);
                for (LivingEntity t : Combat.alongLine(level, hand, end, 0.35, caster)) {
                    count.merge(t, 1, Integer::sum);
                }
            }
            Tasks.later(4, () -> {
                for (var e : count.entrySet()) {
                    LivingEntity t = e.getKey();
                    if (!t.isAlive()) {
                        continue;
                    }
                    Combat.hurtMagic(caster, t, 3 * e.getValue());
                    DbCommon.hit(level, Combat.center(t), 0.6f, Db.BLUE);
                    // A strong stun (Stark, 2026-11-09: "the Senbon has to stun a lot"), whole on a boss too.
                    // Two needles, or one on someone the Sharingan read.
                    if (e.getValue() >= 2 || SubCommon.hasState(t, NrCommon.MARKED)) {
                        SubCommon.clearState(t, NrCommon.MARKED);
                        NrCommon.paralyze(level, t, 70);
                    } else {
                        Stun.apply(t, 30, true);
                    }
                }
                if (count.isEmpty()) {
                    SubCommon.warn(caster, "sasuke.senbon_miss");
                }
            });
            return true;
        });
        return true;
    }

    // ---------- V: Kirin ----------

    // The blast's reach (it was 3.2).
    private static final double KIRIN_R = 8;

    private static boolean kirin(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 64);
        Vec3 point = target != null ? target.position() : SubCommon.ground(level, SubCommon.aimPoint(caster, 64));
        if (!level.canSeeSky(BlockPos.containing(point).above())) {
            SubCommon.warn(caster, "sasuke.kirin_sky");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        // Only the seal holds him (under 1 s, and only a real stun cuts it): after it the storm is called and he is free
        // to walk, dodge and fight while it gathers (Stark, 2026-11-09: the bosses beat him up while he uses a skill).
        // The dragon comes down on the target whatever he does then.
        int seal = 16;
        int strike = 100;
        Vec3[] at = {point};
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, seal + 2);
        Freeze.holdLookFree(caster, seal);
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, seal + 30, 1, 0);
        Tasks.add(age -> {
            if (age <= seal && guard.broken()) {
                Freeze.free(caster);
                return true;
            }
            if (!caster.isAlive()) {
                return true;
            }
            if (target != null && target.isAlive()) {
                at[0] = target.position();
            }
            if (age == seal) {
                SubCommon.pose(caster, "nr.mao_alto");
                SubNet.send(level, SubNet.STORM, 0, point.add(0, 22, 0), Vec3.ZERO, strike - seal, 12, 0);
                SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
                SubCommon.sound(level, point, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 0.6f);
            }
            if (age > seal + 10 && age < strike && SubCommon.RANDOM.nextFloat() < 0.08f) {
                SubCommon.sound(level, point.add(0, 20, 0), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4f, 0.8f + SubCommon.RANDOM.nextFloat() * 0.4f);
            }
            if (age < strike - 6) {
                return false;
            }
            if (age == strike - 6) {
                // The dragon comes down where the target is now: it can't be dodged, only blocked.
                SubNet.send(level, SubNet.KIRIN, 0, point.add(0, 22, 0), at[0].add(0, 0.9, 0).subtract(point.add(0, 22, 0)), (float) KIRIN_R, 0, 0);
                return false;
            }
            if (age < strike) {
                return false;
            }
            Vec3 c = at[0];
            // One of the strongest moves of Naruto (Stark, 2026-11-09): a heavy hit on the target and a storm of
            // lightning 8 blocks round, everyone in it hurt and stunned (the target in full, boss or not).
            if (target != null && target.isAlive()) {
                Combat.hurtMagic(caster, target, 60);
                Stun.apply(target, 60, true);
            }
            for (LivingEntity t : Combat.inRadius(level, c.add(0, 0.9, 0), KIRIN_R, caster)) {
                if (t != target) {
                    double k = 1 - 0.5 * Math.min(1, t.position().distanceTo(c) / KIRIN_R);
                    Combat.hurtMagic(caster, t, (float) (34 * k));
                    Stun.apply(t, 40);
                }
            }
            double ground = DbCommon.groundY(level, c);
            if (c.y - ground < 4) {
                JjCommon.crater(level, new Vec3(c.x, ground, c.z), 6.5, 3.2, 40);
            }
            // Bolts running out along the ground, each leaving a burnt line.
            for (int i = 0; i < 9; i++) {
                double ang = i / 9.0 * Math.PI * 2 + Db.hash(i, 7, 3) * 0.5;
                double len = KIRIN_R * (0.85 + 0.6 * Db.hash(i, 7, 4));
                Vec3 from = new Vec3(c.x, ground + 0.15, c.z);
                SubNet.send(level, SubNet.BOLT_TRAIL, 0, from, new Vec3(Math.cos(ang) * len, 0, Math.sin(ang) * len), 0.15f, 0, 0);
            }
            SubNet.send(level, SubNet.DUST, 0, c, Vec3.ZERO, 3.6f, 0, 0);
            DbCommon.screen(level, c, 34);
            SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 0.7f);
            SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.5f, 0.8f);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 1.2f);
            return true;
        });
        return true;
    }
}
