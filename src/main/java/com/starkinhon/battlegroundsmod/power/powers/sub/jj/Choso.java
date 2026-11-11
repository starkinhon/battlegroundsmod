package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Choso (SubAlma of Jujutsu, tier 6), kit remade with Stark (2026-11-11, preview forja/choso-kit v2): Blood Manipulation,
// real dark liquid blood, no neon.
// Passive Sangue do Utero Amaldicoado: every blow of his blood that lands leaves a pool on the ground for 10 s; the
//   pools are traps for the Supernova and the Meteorite.
// Z Sangue Perfurante: Convergence between the palms; hold to compress (up to 2 s) and let go: a thin line too fast to
//   see, 25 blocks. The longer the hold, the harder and the longer it lasts: while it pours out he sweeps it with his aim.
// X Supernova: 5 orbs of blood circle him; each one takes a hit or a shot for him and bursts. Press again (or 8 s later)
//   and the ones left burst into needles, and every pool of his near by bursts into needles too.
// C Crimson Binding: a rope of blood flies 12 blocks and wraps whoever it meets for 2.5 s. Press again and he pulls them
//   to his feet and slams them down.
// V Perfurante Maximo: the mark on his face lights up (the Red Scale as the charge), his blood leaves the body and spirals
//   into his palms for 2 s, and a giant Piercing Blood goes 40 blocks, cutting the ground, swept in a fan; at the end the
//   whole trail bursts into blood spikes.
// B Meteorito de Sangue: a ball of hardened blood thrown in an arc that crushes where it falls; on a pool, the pool bursts
//   into needles.
public final class Choso {
    private static final int FULL = 40;
    private static final int POOL_TICKS = 200;
    private static final int ORB_TICKS = 160;
    // Supernova: the orbs left round each Choso (bits) and the tick they came up.
    private static final Map<UUID, long[]> ORBS = new HashMap<>();
    // Crimson Binding: who each Choso holds on the rope.
    private static final Map<UUID, LivingEntity> BOUND = new HashMap<>();
    // His pools of blood on the ground: where, until when, the effect's id.
    private static final Map<UUID, List<Pool>> POOLS = new HashMap<>();

    private record Pool(Vec3 pos, long until, int id) {
    }

    private Choso() {
    }

    public static Power create() {
        return new Power("choso", 0x7A1E2C, 1.0f, List.of(
                new Ability(200, 0, Choso::piercing),
                new Ability(360, 0, Choso::supernova),
                new Ability(260, 0, Choso::binding),
                new Ability(700, 0, Choso::maximo),
                new Ability(300, 0, Choso::meteor)));
    }

    // ---------- Passive: pools of blood ----------

    // A pool of his blood under where a blow of it landed.
    static void pool(LivingEntity caster, ServerLevel level, Vec3 at) {
        Vec3 g = SubCommon.ground(level, at);
        long now = level.getGameTime();
        List<Pool> list = POOLS.computeIfAbsent(caster.getUUID(), k -> new java.util.ArrayList<>());
        list.removeIf(p -> p.until() <= now);
        for (Pool p : list) {
            if (p.pos().distanceTo(g) < 1.2) {
                return;
            }
        }
        if (list.size() >= 8) {
            Pool old = list.remove(0);
            SubNet.send(level, SubNet.BLOOD_POOL, old.id(), old.pos(), Vec3.ZERO, 0, 0, 0);
        }
        Pool p = new Pool(g, now + POOL_TICKS, SubNet.newId());
        list.add(p);
        SubNet.send(level, SubNet.BLOOD_POOL, p.id(), g, Vec3.ZERO, POOL_TICKS, 0.8f + 0.5f * SubCommon.RANDOM.nextFloat(), 0);
    }

    // A pool bursts into needles: everyone standing on or near it is hurt and thrown up.
    private static void poolBurst(LivingEntity caster, ServerLevel level, Pool p, float damage) {
        SubNet.send(level, SubNet.BLOOD_POOL, p.id(), p.pos(), Vec3.ZERO, 0, 0, 0);
        SubNet.send(level, SubNet.GROUND_SPIKES, SubNet.newId(), p.pos(), Vec3.ZERO, 1.6f, 9, 1.4f);
        for (LivingEntity t : Combat.inRadius(level, p.pos().add(0, 0.6, 0), 2.6, caster)) {
            Combat.hurtMagic(caster, t, damage);
            Stun.apply(t, 20);
            t.setDeltaMovement(t.getDeltaMovement().add(0, 0.7, 0));
            t.hurtMarked = true;
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), new Vec3(0, 1, 0), 0.9f, 0, 0);
        }
        SubCommon.sound(level, p.pos(), SoundEvents.HONEY_BLOCK_BREAK, 1.4f, 0.6f);
        SubCommon.sound(level, p.pos(), SoundEvents.TRIDENT_HIT, 1f, 0.8f);
    }

    // His live pools within range of a point (removed from the list: they are used).
    private static List<Pool> takePools(LivingEntity caster, ServerLevel level, Vec3 at, double range) {
        List<Pool> out = new java.util.ArrayList<>();
        List<Pool> list = POOLS.get(caster.getUUID());
        if (list == null) {
            return out;
        }
        long now = level.getGameTime();
        list.removeIf(p -> p.until() <= now);
        for (java.util.Iterator<Pool> it = list.iterator(); it.hasNext();) {
            Pool p = it.next();
            if (p.pos().distanceTo(at) <= range) {
                out.add(p);
                it.remove();
            }
        }
        return out;
    }

    // ---------- Z: Sangue Perfurante ----------

    private static boolean piercing(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int bossHold = 14 + SubCommon.RANDOM.nextInt(24);
        int min = 8;
        int id = SubNet.newId();
        SubCommon.pose(caster, "jj.blood_charge");
        SubCommon.busy(caster, FULL + 30);
        SubCommon.sound(level, caster.position(), SoundEvents.HONEY_BLOCK_SLIDE, 1f, 0.6f);
        float[] k = {0};
        int[] fired = {-1};
        int[] pour = {0};
        Set<Integer> hit = new HashSet<>();
        Tasks.add(age -> {
            Vec3 hands = DbCommon.hands(caster);
            if (guard.broken()) {
                SubNet.send(level, SubNet.BLOOD_BALL, id, hands, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.BLOOD_SWEEP, id, hands, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (fired[0] < 0) {
                k[0] = Math.min(1, age / (float) FULL);
                boolean held = SubCommon.held(caster, age, bossHold);
                if (held && age < FULL) {
                    Charge.report(caster, k[0]);
                }
                if (age < min || (held && age < FULL)) {
                    caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                    SubNet.send(level, SubNet.BLOOD_BALL, id, hands, caster.getLookAngle(), 0.32f - 0.18f * k[0], k[0], 0);
                    if (age % 8 == 0) {
                        SubCommon.sound(level, hands, SoundEvents.HONEY_BLOCK_SLIDE, 0.6f, 0.6f + k[0]);
                    }
                    return false;
                }
                fired[0] = age;
                pour[0] = 3 + Math.round(11 * k[0]);
                SubNet.send(level, SubNet.BLOOD_BALL, id, hands, Vec3.ZERO, 0, 0, 1);
                SubCommon.pose(caster, "jj.blood_shoot");
                SubCommon.busy(caster, pour[0] + 4);
                DbCommon.screen(level, hands, 29);
                SubCommon.sound(level, hands, SoundEvents.TRIDENT_THROW.value(), 1.4f, 1.6f);
            }
            int t = age - fired[0];
            // While it pours, the line follows his aim (the sweep).
            Vec3 dir = caster.getLookAngle();
            LivingEntity aimed = caster instanceof Player ? null : JjCommon.target(caster, 25);
            if (aimed != null) {
                dir = Combat.center(aimed).subtract(hands).normalize();
            }
            Vec3 end = DbCommon.beamEnd(caster, level, hands, dir, 25, 0.6, true);
            boolean last = t >= pour[0];
            SubNet.send(level, SubNet.BLOOD_SWEEP, id, hands, end.subtract(hands), 0.1f + 0.08f * k[0], 0, last ? 1 : 0);
            float dmg = 7 + 13 * k[0];
            for (LivingEntity e : Combat.alongLine(level, hands, end, 0.7, caster)) {
                if (!hit.add(e.getId())) {
                    continue;
                }
                Combat.hurtMagic(caster, e, dmg);
                Stun.apply(e, 10 + (int) (16 * k[0]));
                Combat.push(e, dir, 0.4 + 0.5 * k[0], 0.1);
                SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(e), dir, 0.8f + 0.5f * k[0], 0, 0);
                pool(caster, level, e.position());
            }
            if (t % 3 == 0) {
                SubCommon.sound(level, end, SoundEvents.HONEY_BLOCK_BREAK, 0.9f, 0.7f);
            }
            return last;
        });
        return true;
    }

    // ---------- X: Supernova ----------

    private static boolean supernova(LivingEntity caster, ServerLevel level) {
        long[] orbs = ORBS.get(caster.getUUID());
        if (orbs != null && orbs[0] != 0) {
            burst(caster, level);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        long start = level.getGameTime();
        ORBS.put(caster.getUUID(), new long[] {31, start});
        SubCommon.pose(caster, "jj.supernova");
        SubCommon.busy(caster, 8);
        DbCommon.cooldown(caster, 1, 12);
        SubCommon.sound(level, caster.position(), SoundEvents.HONEY_BLOCK_PLACE, 1.4f, 0.6f);
        Tasks.add(age -> {
            long[] o = ORBS.get(caster.getUUID());
            boolean mine = o != null && o[1] == start;
            if (guard.broken() || !mine || o[0] == 0) {
                SubNet.send(level, SubNet.BLOOD_ORBS, caster.getId(), Combat.center(caster), Vec3.ZERO, 0, 0, 1);
                if (mine && o[0] == 0) {
                    ORBS.remove(caster.getUUID());
                    DbCommon.cooldown(caster, 1, 300);
                }
                return true;
            }
            SubNet.send(level, SubNet.BLOOD_ORBS, caster.getId(), Combat.center(caster), Vec3.ZERO, o[0], angle(age), 0);
            boolean near = !(caster instanceof Player) && age > 30 && !Combat.inRadius(level, Combat.center(caster), 3.5, caster).isEmpty();
            if (age >= ORB_TICKS || near) {
                burst(caster, level);
                return true;
            }
            return false;
        });
        return false;
    }

    private static float angle(long age) {
        return age * 0.09f;
    }

    private static Vec3 orbAt(LivingEntity caster, int i, long age) {
        double a = angle(age) + i / 5.0 * Math.PI * 2;
        return Combat.center(caster).add(Math.cos(a) * 1.3, 0, Math.sin(a) * 1.3);
    }

    private static void burst(LivingEntity caster, ServerLevel level) {
        long[] o = ORBS.remove(caster.getUUID());
        if (o == null || o[0] == 0) {
            return;
        }
        long age = level.getGameTime() - o[1];
        int mask = (int) o[0];
        int count = Integer.bitCount(mask);
        Vec3 c = Combat.center(caster);
        SubNet.send(level, SubNet.BLOOD_ORBS, caster.getId(), c, Vec3.ZERO, 0, 0, 1);
        SubNet.send(level, SubNet.SUPERNOVA_BURST, 0, c, Vec3.ZERO, mask, angle(age), 0);
        DbCommon.screen(level, c, 30);
        SubCommon.pose(caster, "jj.supernova_burst");
        for (LivingEntity t : Combat.inRadius(level, c, 4.5, caster)) {
            Combat.hurtMagic(caster, t, 3.6f * count);
            Stun.apply(t, 10 + 3 * count);
            Combat.pushFrom(t, c, 0.6 + 0.1 * count, 0.3);
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), Combat.center(t).subtract(c), 0.9f, 0, 0);
            pool(caster, level, t.position());
        }
        SubCommon.sound(level, c, SoundEvents.HONEY_BLOCK_BREAK, 1.6f, 0.5f);
        SubCommon.sound(level, c, SoundEvents.TRIDENT_RIPTIDE_1.value(), 1f, 1.4f);
        DbCommon.cooldown(caster, 1, 360);
        // The pools near by are orbs too: they burst into needles.
        for (Pool p : takePools(caster, level, c, 14)) {
            poolBurst(caster, level, p, 2.4f * Math.max(2, count));
        }
    }

    // Called on every hit: an orb up takes it.
    static boolean orbTakes(LivingEntity target, ServerLevel level, Entity from) {
        long[] o = ORBS.get(target.getUUID());
        if (o == null || o[0] == 0 || from == null) {
            return false;
        }
        int i = Long.numberOfTrailingZeros(o[0]);
        o[0] &= ~(1L << i);
        Vec3 at = orbAt(target, i, level.getGameTime() - o[1]);
        SubNet.send(level, SubNet.ORB_POP, 0, at, Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, at, SoundEvents.HONEY_BLOCK_BREAK, 1.2f, 1.2f);
        return true;
    }

    // ---------- C: Crimson Binding ----------

    private static boolean binding(LivingEntity caster, ServerLevel level) {
        LivingEntity held = BOUND.get(caster.getUUID());
        if (held != null && held.isAlive() && SubCommon.hasState(held, JjCommon.BOUND)) {
            pull(caster, level, held);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        double range = 12;
        double speed = 1.6;
        LivingEntity target = JjCommon.target(caster, range);
        Vec3 aim = target != null ? null : SubCommon.aimPoint(caster, range);
        SubCommon.pose(caster, "jj.rope_throw");
        SubCommon.busy(caster, 10);
        DbCommon.cooldown(caster, 2, 12);
        SubCommon.sound(level, caster.position(), SoundEvents.FISHING_BOBBER_THROW, 1.2f, 0.7f);
        double[] out = {0};
        int[] bound = {-1};
        int hold = target != null ? JjCommon.control(caster, target, 50) : 0;
        Tasks.add(age -> {
            Vec3 hand = DbCommon.hand(caster);
            if (guard.broken() || (target != null && !target.isAlive())) {
                SubNet.send(level, SubNet.ROPE, id, hand, Vec3.ZERO, 0, 0, 1);
                if (target != null) {
                    SubCommon.clearState(target, JjCommon.BOUND);
                    Freeze.free(target);
                }
                BOUND.remove(caster.getUUID());
                DbCommon.cooldown(caster, 2, 200);
                return true;
            }
            if (bound[0] < 0) {
                Vec3 goal = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : aim;
                out[0] += speed;
                double dist = goal.distanceTo(hand);
                Vec3 end = hand.add(goal.subtract(hand).normalize().scale(Math.min(out[0], dist)));
                SubNet.send(level, SubNet.ROPE, id, hand, end.subtract(hand), 0, 0, 0);
                if (out[0] < dist) {
                    return false;
                }
                if (target == null) {
                    SubNet.send(level, SubNet.ROPE, id, hand, end.subtract(hand), 0, 0, 1);
                    SubCommon.warn(caster, "choso.rope_miss");
                    DbCommon.cooldown(caster, 2, 80);
                    return true;
                }
                bound[0] = age;
                SubCommon.setState(target, JjCommon.BOUND, hold);
                Freeze.hold(target, hold);
                PowerLogic.seal(target, hold);
                Stun.apply(target, Math.min(hold, 20));
                BOUND.put(caster.getUUID(), target);
                SubCommon.sound(level, target.position(), SoundEvents.HONEY_BLOCK_STEP, 1.4f, 0.6f);
                SubCommon.warn(caster, "choso.rope_bound");
                return false;
            }
            boolean still = SubCommon.hasState(target, JjCommon.BOUND) && BOUND.get(caster.getUUID()) == target;
            if (!still) {
                SubNet.send(level, SubNet.ROPE, id, hand, target.position().subtract(hand), 1, target.getBbHeight(), 1);
                if (BOUND.get(caster.getUUID()) == target) {
                    BOUND.remove(caster.getUUID());
                    DbCommon.cooldown(caster, 2, 260);
                }
                return true;
            }
            SubNet.send(level, SubNet.ROPE, id, hand, target.position().subtract(hand), 1, target.getBbHeight(), 0);
            // Mobs pull on their own.
            if (!(caster instanceof Player) && age - bound[0] == 24) {
                pull(caster, level, target);
            }
            return false;
        });
        return false;
    }

    private static void pull(LivingEntity caster, ServerLevel level, LivingEntity target) {
        BOUND.remove(caster.getUUID());
        SubCommon.clearState(target, JjCommon.BOUND);
        SubCommon.pose(caster, "jj.rope_pull");
        SubCommon.busy(caster, 12);
        DbCommon.cooldown(caster, 2, 260);
        int id = SubNet.newId();
        Vec3 from = target.position();
        Vec3 feet = caster.position().add(SubCommon.flat(caster.getLookAngle()).scale(1.3));
        Vec3[] at = {from};
        Freeze.free(target);
        JjCommon.carry(target, 7, at);
        SubCommon.sound(level, from, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.4f, 0.6f);
        Tasks.add(age -> {
            Vec3 hand = DbCommon.hand(caster);
            if (!target.isAlive() || !caster.isAlive()) {
                SubNet.send(level, SubNet.ROPE, id, hand, Vec3.ZERO, 0, 0, 1);
                Freeze.free(target);
                return true;
            }
            float k = SubCommon.easeOutCubic(age / 6f);
            at[0] = from.lerp(feet, k).add(0, Math.sin(k * Math.PI) * 1.2, 0);
            SubNet.send(level, SubNet.ROPE, id, hand, at[0].subtract(hand), 1, target.getBbHeight(), age >= 6 ? 1 : 0);
            if (age < 6) {
                return false;
            }
            Freeze.free(target);
            Vec3 c = Combat.center(target);
            Combat.hurt(caster, target, 10);
            Stun.apply(target, 25);
            target.setDeltaMovement(0, -0.6, 0);
            target.hurtMarked = true;
            DbCommon.screen(level, c, 31);
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, c, new Vec3(0, -1, 0), 1.2f, 0, 0);
            SubNet.send(level, SubNet.CRACKS, 0, SubCommon.ground(level, feet), Vec3.ZERO, 1.6f, 5, 1);
            pool(caster, level, target.position());
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.6f);
            return true;
        });
    }

    // ---------- V: Perfurante Maximo ----------

    private static boolean maximo(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int charge = 40;
        int sweep = 32;
        int after = 8;
        double len = 40;
        int id = SubNet.newId();
        SubCommon.pose(caster, "jj.blood_charge");
        SubCommon.busy(caster, charge + sweep + 6);
        Freeze.holdLookFree(caster, charge + sweep);
        // Not crushed while he charges: his blood hardens round him.
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, charge + sweep, 2, false, false));
        SubNet.send(level, SubNet.SCALE, caster.getId(), caster.position(), Vec3.ZERO, charge + sweep + 20, 0, 0);
        DbCommon.screen(level, Combat.center(caster), 29);
        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 2f, 1.2f);
        float[] aim = {0, 0};
        Set<Integer> hit = new HashSet<>();
        List<Vec3> trail = new ArrayList<>();
        Tasks.add(age -> {
            Vec3 hands = DbCommon.hands(caster);
            if (guard.broken()) {
                SubNet.send(level, SubNet.MAX_CHARGE, caster.getId(), hands, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.BLOOD_BALL, id, hands, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.BLOOD_SWEEP, id, hands, Vec3.ZERO, 0, 0, 1);
                Freeze.free(caster);
                return true;
            }
            if (age < charge) {
                float k = age / (float) charge;
                SubNet.send(level, SubNet.MAX_CHARGE, caster.getId(), hands, Vec3.ZERO, k, 0, 0);
                SubNet.send(level, SubNet.BLOOD_BALL, id, hands, caster.getLookAngle(), 0.55f - 0.25f * k, k, 0);
                Charge.report(caster, k);
                if (age % 10 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6f, 1.2f + 0.4f * k);
                    SubCommon.sound(level, hands, SoundEvents.HONEY_BLOCK_SLIDE, 1f, 0.5f + k);
                }
                return false;
            }
            int t = age - charge;
            if (t == 0) {
                SubNet.send(level, SubNet.MAX_CHARGE, caster.getId(), hands, Vec3.ZERO, 1, 0, 1);
                SubNet.send(level, SubNet.BLOOD_BALL, id, hands, Vec3.ZERO, 0, 0, 1);
                SubCommon.pose(caster, "jj.blood_shoot");
                DbCommon.screen(level, hands, 30);
                aim[0] = caster.getYRot();
                aim[1] = Mth.clamp(caster.getXRot(), -30, 20);
                LivingEntity target = caster instanceof Player ? null : JjCommon.target(caster, len);
                if (target != null) {
                    Vec3 to = Combat.center(target).subtract(hands);
                    aim[0] = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
                    aim[1] = 0;
                }
                SubCommon.sound(level, hands, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
                SubCommon.sound(level, hands, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.6f, 0.7f);
            }
            if (t <= sweep) {
                // The fan: from one side of his aim to the other.
                float u = SubCommon.smooth(t / (float) sweep);
                Vec3 dir = Vec3.directionFromRotation(aim[1], aim[0] + 28 - 56 * u);
                Vec3 end = hands.add(dir.scale(len));
                SubNet.send(level, SubNet.BLOOD_SWEEP, id, hands, end.subtract(hands), 0.75f, 0, t == sweep ? 1 : 0);
                if (t % 2 == 0) {
                    cut(caster, level, hands, dir, len, trail, t % 4 == 0);
                }
                for (LivingEntity e : Combat.alongLine(level, hands, end, 1.6, caster)) {
                    if (!hit.add(e.getId())) {
                        continue;
                    }
                    Defense.breakGuard(e, caster);
                    Combat.hurtMagic(caster, e, 34);
                    Stun.apply(e, JjCommon.isBoss(e) ? 52 : 30);
                    Combat.push(e, dir, 1.4, 0.4);
                    SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(e), dir, 1.5f, 0, 0);
                    pool(caster, level, e.position());
                }
                if (t % 4 == 0) {
                    SubCommon.sound(level, end, SoundEvents.HONEY_BLOCK_BREAK, 1.4f, 0.5f);
                    SubCommon.sound(level, hands, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.8f, 0.6f);
                }
                return false;
            }
            if (t == sweep + after) {
                spikes(caster, level, trail);
                return true;
            }
            return false;
        });
        return true;
    }

    // The giant line cuts what it crosses (walls, pillars) and digs a trench where it runs over the ground; it remembers
    // the ground it ran over for the spikes.
    private static void cut(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 dir, double len, List<Vec3> trail, boolean mark) {
        for (double s = 2.5; s <= len; s += 1.6) {
            Vec3 p = from.add(dir.scale(s));
            if (SubCommon.solid(level, p) || SubCommon.solid(level, p.add(0, 0.9, 0)) || SubCommon.solid(level, p.add(0, -0.9, 0))) {
                JjCommon.carve(level, p, 1.3, from);
            }
            if (mark && ((int) s) % 3 == 0 && trail.size() < 70) {
                Vec3 g = SubCommon.ground(level, p);
                if (p.y - g.y < 3) {
                    JjCommon.carve(level, g, 0.9, from);
                    trail.add(g);
                }
            }
        }
    }

    // At the end, the whole trail bursts into blood spikes.
    private static void spikes(LivingEntity caster, ServerLevel level, List<Vec3> trail) {
        Set<Integer> hit = new HashSet<>();
        List<Vec3> done = new ArrayList<>();
        for (Vec3 g : trail) {
            boolean near = false;
            for (Vec3 d : done) {
                if (d.distanceTo(g) < 2.2) {
                    near = true;
                    break;
                }
            }
            if (near || done.size() >= 40) {
                continue;
            }
            done.add(g);
            SubNet.send(level, SubNet.GROUND_SPIKES, SubNet.newId(), g, Vec3.ZERO, 1.5f, 6, 2.4f);
            for (LivingEntity e : Combat.inRadius(level, g.add(0, 0.8, 0), 2.2, caster)) {
                if (!hit.add(e.getId())) {
                    continue;
                }
                Combat.hurtMagic(caster, e, 10);
                Stun.apply(e, 25);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                e.hurtMarked = true;
            }
        }
        if (!done.isEmpty()) {
            Vec3 mid = done.get(done.size() / 2);
            DbCommon.screen(level, mid, 30);
            SubCommon.sound(level, mid, SoundEvents.TRIDENT_HIT, 2f, 0.6f);
            SubCommon.sound(level, mid, SoundEvents.HONEY_BLOCK_BREAK, 2f, 0.5f);
        }
    }

    // ---------- B: Meteorito de Sangue ----------

    private static boolean meteor(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity t = JjCommon.target(caster, 22);
        Vec3[] goal = {SubCommon.ground(level, t != null ? t.position() : SubCommon.aimPoint(caster, 22))};
        int id = SubNet.newId();
        int windup = 7;
        SubCommon.pose(caster, "jj.block_throw");
        SubCommon.busy(caster, windup + 4);
        SubCommon.sound(level, caster.position(), SoundEvents.HONEY_BLOCK_PLACE, 1.4f, 0.5f);
        Vec3[] from = {null};
        int[] flight = {0};
        double[] peak = {0};
        Tasks.add(age -> {
            if (age < windup) {
                if (guard.broken()) {
                    SubNet.send(level, SubNet.METEOR, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                    return true;
                }
                SubNet.send(level, SubNet.METEOR, id, DbCommon.hand(caster).add(0, 0.7, 0), Vec3.ZERO, 0.25f + 0.45f * age / windup, 0, 0);
                return false;
            }
            if (from[0] == null) {
                from[0] = DbCommon.hand(caster).add(0, 0.7, 0);
                double dist = from[0].distanceTo(goal[0]);
                flight[0] = 10 + (int) (dist * 0.55);
                peak[0] = 2.5 + dist * 0.18;
                SubCommon.sound(level, from[0], SoundEvents.TRIDENT_THROW.value(), 1.2f, 0.5f);
            }
            int f = age - windup;
            double u = f / (double) flight[0];
            if (t != null && t.isAlive() && u < 0.5) {
                // It follows the target a little at first.
                goal[0] = goal[0].lerp(SubCommon.ground(level, t.position()), 0.25);
            }
            Vec3 p = from[0].lerp(goal[0], Math.min(1, u)).add(0, 4 * peak[0] * u * (1 - u), 0);
            SubNet.send(level, SubNet.METEOR, id, p, Vec3.ZERO, 0.7f, 0, u >= 1 ? 1 : 0);
            if (u < 1) {
                return false;
            }
            crash(caster, level, goal[0]);
            return true;
        });
        return true;
    }

    private static void crash(LivingEntity caster, ServerLevel level, Vec3 at) {
        JjCommon.crater(level, at, 2.4, 1.3, 14);
        DbCommon.screen(level, at, 30);
        SubNet.send(level, SubNet.CRACKS, 0, at, Vec3.ZERO, 2.6f, 8, 1);
        SubNet.send(level, SubNet.BLOOD_SPLASH, 0, at.add(0, 0.4, 0), new Vec3(0, 1, 0), 1.6f, 0, 0);
        for (LivingEntity e : Combat.inRadius(level, at.add(0, 0.8, 0), 3, caster)) {
            Combat.hurtMagic(caster, e, 16);
            Stun.apply(e, 25);
            Combat.pushFrom(e, at, 0.9, 0.5);
        }
        SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.7f);
        SubCommon.sound(level, at, SoundEvents.HONEY_BLOCK_BREAK, 1.6f, 0.4f);
        // On a pool: the pool bursts into needles.
        for (Pool p : takePools(caster, level, at, 3)) {
            poolBurst(caster, level, p, 10);
        }
    }

    static void clear() {
        ORBS.clear();
        BOUND.clear();
        POOLS.clear();
    }
}
