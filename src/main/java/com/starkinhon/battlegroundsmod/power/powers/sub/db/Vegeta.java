package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Vegeta (SubAlma of Dragon Ball, tier 6), the Visualizador's Vegeta tab.
// Z Renzoku Energy Dan: held, he switches hands without stopping and fires dozens of yellow ki shots; the target
//   disappears into a cloud of blasts and smoke (it can't see).
// X Big Bang Attack: arm out, open palm: a big blue ball made of Zanka tongues of ki, with waves running over it; it
//   flies heavy and bursts into tongues that open and die torn.
// V Final Flash: arms open, the yellow ki gathers in both palms with bolts round the body; hands together, a huge yellow
//   beam goes through everything and tears the ground.
public final class Vegeta {
    private Vegeta() {
    }

    public static Power create() {
        return new Power("vegeta", 0xFFD84A, 1.1f, List.of(
                new Ability(140, 0, Vegeta::rajada),
                new Ability(200, 0, Vegeta::bigBang),
                new Ability(220, 0, Vegeta::galickGunMissing),
                new Ability(1000, 0, Vegeta::finalFlash)));
    }

    // ---------- Z: Renzoku Energy Dan ----------

    private static boolean rajada(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int least = 20;
        int most = 82;
        int bossHold = 30 + SubCommon.RANDOM.nextInt(40);
        SubCommon.pose(caster, "soco.barragem");
        Map<Integer, Integer> hitAt = new HashMap<>();
        Map<Integer, Float> cover = new HashMap<>();
        Map<Integer, LivingEntity> covered = new HashMap<>();
        int[] stop = {-1};
        Tasks.add(age -> {
            boolean broken = guard.broken();
            if (stop[0] < 0) {
                boolean held = SubCommon.held(caster, age, bossHold);
                if (broken || age >= most || (age >= least && !held)) {
                    stop[0] = age;
                } else {
                    if (held) {
                        Charge.report(caster, age / (double) most);
                    }
                    SubCommon.busy(caster, 3);
                    if (age % 8 == 0) {
                        SubCommon.pose(caster, "soco.barragem");
                    }
                    caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false));
                    LivingEntity target = SubCommon.aimed(caster, 32);
                    Vec3 aim = target != null ? Combat.center(target) : Combat.raycastPoint(caster, 32);
                    for (int j = 0; j < 2; j++) {
                        Vec3 from = (age + j) % 2 == 0 ? DbCommon.hand(caster) : DbCommon.handLeft(caster);
                        Vec3 to = aim.add((SubCommon.RANDOM.nextDouble() - 0.5) * 1.6, (SubCommon.RANDOM.nextDouble() - 0.5) * 1.4,
                                (SubCommon.RANDOM.nextDouble() - 0.5) * 1.6);
                        int flight = Math.max(2, (int) Math.round(from.distanceTo(to) / 1.8));
                        SubNet.send(level, SubNet.SHOT, SubCommon.RANDOM.nextInt(100), from, to.subtract(from), flight, Db.GOLD, 0);
                        Tasks.later(flight, () -> {
                            for (LivingEntity t : Combat.inRadius(level, to, 1.3, caster)) {
                                Integer last = hitAt.get(t.getId());
                                if (last == null || age - last >= 2) {
                                    hitAt.put(t.getId(), age);
                                    Combat.hurtMagic(caster, t, 0.45f);
                                    cover.merge(t.getId(), 0.04f, Float::sum);
                                    covered.put(t.getId(), t);
                                }
                            }
                        });
                    }
                    if (age % 2 == 0) {
                        SubCommon.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 0.6f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.3f);
                    }
                    if (age % 10 == 0) {
                        DbCommon.screen(level, caster.position(), 4);
                    }
                }
            }
            // The smoke piles up round whoever is hit (blinding players) and slowly opens after it stops.
            boolean any = false;
            for (Map.Entry<Integer, LivingEntity> e : covered.entrySet()) {
                LivingEntity t = e.getValue();
                float k = Math.min(1, cover.getOrDefault(e.getKey(), 0f));
                if (stop[0] >= 0) {
                    k = Math.max(0, k - 1 / 40f);
                    cover.put(e.getKey(), k);
                }
                if (k > 0 && t.isAlive()) {
                    any = true;
                    if (k > 0.5f) {
                        t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false));
                    }
                }
                SubNet.send(level, SubNet.SMOKE_COVER, t.getId(), t.position(), Vec3.ZERO, k, 0, k <= 0 || !t.isAlive() ? 1 : 0);
            }
            return stop[0] >= 0 && (!any || age - stop[0] > 60);
        });
        return true;
    }

    // ---------- X: Big Bang Attack ----------

    private static boolean bigBang(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int form = 32;
        int flight = 18;
        SubCommon.pose(caster, "db.palma_dir");
        SubCommon.busy(caster, form + 6);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, form, 3, false, false));
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.6, 0, 0), form + 4, Db.GOLD, 1);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.4f, 1.2f);
        Vec3[] from = {null};
        Vec3[] dir = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.BIG_BANG, id, DbCommon.hand(caster), Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            if (age < form) {
                float k = SubCommon.smooth(age / (float) form);
                Vec3 p = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.6 * k + 0.2));
                SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.1f + 0.6f * k, SubCommon.smooth(age / 4f), 0);
                if (age % 6 == 0) {
                    SubCommon.sound(level, p, SoundEvents.FIRE_AMBIENT, 1.4f, 0.7f + k);
                }
                return false;
            }
            if (age == form) {
                from[0] = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.8));
                Vec3 goal = SubCommon.aimPoint(caster, 40);
                LivingEntity target = SubCommon.aimed(caster, 40);
                if (target != null) {
                    goal = Combat.center(target);
                }
                dir[0] = goal.subtract(from[0]);
                SubCommon.sound(level, from[0], SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.4f);
            }
            float k = (age - form) / (float) flight;
            Vec3 p = from[0].add(dir[0].scale(Math.min(1, k)));
            boolean touch = !Combat.inRadius(level, p, 1.1, caster).isEmpty() || SubCommon.solid(level, p);
            if (k < 1 && !touch) {
                SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.7f, 1, 0);
                Vec3 back = from[0].add(dir[0].scale(Math.max(0, k - 0.3)));
                SubNet.send(level, SubNet.STREAK, 0, back, p.subtract(back), 1.1f, Db.BLUE, 3);
                return false;
            }
            SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.7f, 1, 1);
            double ground = DbCommon.groundY(level, p);
            SubNet.send(level, SubNet.BIG_BANG_BOOM, 0, p, new Vec3(ground, 0, 0), 3.2f, 0, 0);
            DbCommon.screen(level, p, 6);
            SubCommon.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.7f);
            SubCommon.sound(level, p, SoundEvents.BLAZE_SHOOT, 2f, 0.5f);
            DbCommon.hurtRadius(caster, level, p, 4.2, 22, 1.4, 0.5);
            return true;
        });
        return true;
    }

    // ---------- C: not made yet in the Visualizador ----------

    private static boolean galickGunMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }

    // ---------- V: Final Flash ----------

    private static boolean finalFlash(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int join = 80;
        int fire = 90;
        int end = 150;
        int right = SubNet.newId();
        int left = SubNet.newId();
        int bolts = SubNet.newId();
        int beam = SubNet.newId();
        SubCommon.pose(caster, "db.bracos_abertos");
        DbCommon.screen(level, Combat.center(caster), 8);
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.45, 0, 0), fire, Db.GOLD, 1.1f);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 2f, 0.9f);
        Map<Integer, Integer> hitAt = new HashMap<>();
        Vec3[] start = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.KI_BALL, right, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.KI_BALL, left, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.SPARKS, bolts, caster.position(), caster.position(), 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.KI_BEAM, beam, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age < fire) {
                // Charged with the arms open: still and open to hits.
                Freeze.holdLookFree(caster, 2);
                float k = SubCommon.smooth((age - 6) / 60f);
                Vec3 look = caster.getLookAngle();
                Vec3 side = SubCommon.flat(look).cross(new Vec3(0, 1, 0));
                Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.72, 0);
                if (age < join + 4) {
                    SubNet.send(level, SubNet.KI_BALL, right, chest.add(side.scale(0.95)), Vec3.ZERO, 0.1f + 0.18f * k, Db.GOLD, 0);
                    SubNet.send(level, SubNet.KI_BALL, left, chest.add(side.scale(-0.95)), Vec3.ZERO, 0.1f + 0.18f * k, Db.GOLD, 0);
                } else {
                    SubNet.send(level, SubNet.KI_BALL, left, chest, Vec3.ZERO, 0, Db.GOLD, 1);
                    SubNet.send(level, SubNet.KI_BALL, right, DbCommon.hands(caster).add(look.scale(0.3)), Vec3.ZERO, 0.6f, Db.GOLD, 0);
                }
                SubNet.send(level, SubNet.SPARKS, bolts, caster.position(), caster.position(), k, Db.GOLD, 0);
                if (age == join) {
                    SubCommon.pose(caster, "db.empurra");
                }
                if (age % 15 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.BEACON_AMBIENT, 2f, 0.6f + k);
                }
                if (age % 7 == 0) {
                    DbCommon.screen(level, caster.position(), 4);
                }
                return false;
            }
            if (age == fire) {
                SubNet.send(level, SubNet.KI_BALL, right, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.SPARKS, bolts, caster.position(), caster.position(), 0, Db.GOLD, 1);
                DbCommon.screen(level, Combat.center(caster), 7);
                SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.6f);
                SubCommon.sound(level, caster.position(), SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.6f);
                start[0] = caster.position();
            }
            int t = age - fire;
            Vec3 from = DbCommon.hands(caster).add(caster.getLookAngle().scale(0.3));
            Vec3 dir = caster.getLookAngle();
            Vec3 far = DbCommon.beamEnd(caster, level, from, dir, 40, 1.6, true);
            Vec3 to = from.add(far.subtract(from).scale(Math.min(1, t / 6.0)));
            boolean done = age >= end;
            SubNet.send(level, SubNet.KI_BEAM, beam, from, to.subtract(from), 2.6f + 0.2f * (float) Math.sin(age), Db.GOLD, done ? 1 : 0);
            // Goes through everything: everyone in the way, 3 blocks wide.
            DbCommon.hurtAlong(caster, level, from, to, 1.6, 2.2f, age, 4, hitAt, dir.scale(0.3));
            if (t == 2) {
                Vec3 flat = SubCommon.flat(dir);
                double ground = DbCommon.groundY(level, start[0].add(flat.scale(1)));
                SubNet.send(level, SubNet.SCAR, 0, new Vec3(start[0].x, ground, start[0].z).add(flat), flat.scale(Math.min(40, far.distanceTo(from))), 3, 140, 0);
            }
            if (t % 6 == 0) {
                SubCommon.sound(level, to, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 0.5f);
            }
            if (done) {
                DbCommon.boom(level, to, 3, Db.GOLD, 8);
            }
            return done;
        });
        return true;
    }
}
