package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Choso (SubAlma of Jujutsu, tier 6), the Visualizador's Choso tab: Blood Manipulation.
// Z Piercing Blood: hold to compress the blood between his palms (up to 3 s), let go and it leaves as a line too fast to
//   see, 25 blocks through everyone in it. The longer the hold, the harder. Instant under Red Scale.
// X Supernova: 5 orbs of blood circle him; each one takes a hit or a shot for him and bursts. Press again (or 8 s later)
//   and the ones left burst into needles all round.
// C Crimson Binding: a rope of blood flies 12 blocks and wraps whoever it meets for 2.5 s (can't move, no skills).
//   Press again and he pulls them to his feet and slams them down.
// V Red Scale: his blood runs faster: 8 s of +50% punches and speed, Piercing Blood without charging, and the moment it
//   starts, three punches and an uppercut on whoever is in front.
public final class Choso {
    private static final int FULL = 62;
    private static final int ORB_TICKS = 160;
    // Supernova: the orbs left round each Choso (bits) and the tick they came up.
    private static final Map<UUID, long[]> ORBS = new HashMap<>();
    // Crimson Binding: who each Choso holds on the rope.
    private static final Map<UUID, LivingEntity> BOUND = new HashMap<>();

    private Choso() {
    }

    public static Power create() {
        return new Power("choso", 0x7A1E2C, 1.0f, List.of(
                new Ability(200, 0, Choso::piercing),
                new Ability(360, 0, Choso::supernova),
                new Ability(260, 0, Choso::binding),
                new Ability(700, 0, Choso::redScale)));
    }

    // ---------- Z: Piercing Blood ----------

    private static boolean piercing(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean fast = SubCommon.hasState(caster, JjCommon.SCALE);
        int bossHold = 24 + SubCommon.RANDOM.nextInt(30);
        int min = fast ? 6 : 10;
        int id = SubNet.newId();
        SubCommon.pose(caster, "jj.blood_charge");
        SubCommon.busy(caster, FULL + 10);
        SubCommon.sound(level, caster.position(), SoundEvents.HONEY_BLOCK_SLIDE, 1f, 0.6f);
        float[] k = {0};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.BLOOD_BALL, id, DbCommon.hands(caster), Vec3.ZERO, 0, 0, 1);
                Freeze.free(caster);
                return true;
            }
            Vec3 hands = DbCommon.hands(caster);
            k[0] = fast ? 1 : Math.min(1, age / (float) FULL);
            boolean held = !fast && SubCommon.held(caster, age, bossHold);
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
            SubNet.send(level, SubNet.BLOOD_BALL, id, hands, Vec3.ZERO, 0, 0, 1);
            SubCommon.pose(caster, "jj.blood_shoot");
            Vec3 dir = caster.getLookAngle();
            LivingEntity aimed = JjCommon.target(caster, 25);
            if (!(caster instanceof Player) && aimed != null) {
                dir = Combat.center(aimed).subtract(hands).normalize();
            }
            Vec3 end = DbCommon.beamEnd(caster, level, hands, dir, 25, 0.6, true);
            SubNet.send(level, SubNet.BLOOD_BEAM, 0, hands, end.subtract(hands), 0, 0, 0);
            DbCommon.screen(level, hands, 29);
            float dmg = 7 + 13 * k[0];
            for (LivingEntity t : Combat.alongLine(level, hands, end, 0.7, caster)) {
                Combat.hurtMagic(caster, t, dmg);
                Stun.apply(t, 10 + (int) (14 * k[0]));
                Combat.push(t, dir, 0.4 + 0.5 * k[0], 0.1);
                SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), dir, 0.8f + 0.5f * k[0], 0, 0);
            }
            SubCommon.sound(level, hands, SoundEvents.TRIDENT_THROW.value(), 1.4f, 1.6f);
            SubCommon.sound(level, end, SoundEvents.HONEY_BLOCK_BREAK, 1.2f, 0.7f);
            SubCommon.busy(caster, 0);
            caster.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).busyTicks = 6;
            return true;
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
        }
        SubCommon.sound(level, c, SoundEvents.HONEY_BLOCK_BREAK, 1.6f, 0.5f);
        SubCommon.sound(level, c, SoundEvents.TRIDENT_RIPTIDE_1.value(), 1f, 1.4f);
        DbCommon.cooldown(caster, 1, 360);
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
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.6f);
            return true;
        });
    }

    // ---------- V: Red Scale ----------

    private static boolean redScale(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int ticks = 160;
        SubCommon.setState(caster, JjCommon.SCALE, ticks);
        SubNet.send(level, SubNet.SCALE, caster.getId(), caster.position(), Vec3.ZERO, ticks, 0, 0);
        SubCommon.pose(caster, "jj.scale_on");
        SubCommon.busy(caster, 6);
        DbCommon.screen(level, Combat.center(caster), 29);
        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 2f, 1.4f);
        Tasks.add(age -> {
            if (guard.broken() || !SubCommon.hasState(caster, JjCommon.SCALE)) {
                SubCommon.clearState(caster, JjCommon.SCALE);
                SubNet.send(level, SubNet.SCALE, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
                return true;
            }
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6, 1, false, false));
            if (age % 20 == 0) {
                SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 1.6f);
            }
            return false;
        });
        LivingEntity t = JjCommon.target(caster, 4);
        if (t != null && t.distanceTo(caster) <= 4.5) {
            rush(caster, level, t);
        }
        return true;
    }

    // Three punches and an uppercut, too fast to block.
    private static void rush(LivingEntity caster, ServerLevel level, LivingEntity t) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.busy(caster, 24);
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive() || t.distanceTo(caster) > 5.5) {
                return true;
            }
            if (age == 2 || age == 7 || age == 12) {
                SubCommon.pose(caster, age == 7 ? "jj.scale_punch_l" : "jj.scale_punch");
                Combat.hurt(caster, t, 4);
                Stun.apply(t, 10);
                Combat.push(t, t.position().subtract(caster.position()), 0.15, 0);
                DbCommon.hit(level, JjCommon.contact(caster, t), 0.7f, Db.RED);
                SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.2f + age * 0.02f);
            }
            if (age == 19) {
                SubCommon.pose(caster, "jj.uppercut");
                Vec3 c = Combat.center(t);
                Combat.hurt(caster, t, 10);
                Stun.apply(t, 25);
                t.setDeltaMovement(t.position().subtract(caster.position()).normalize().scale(0.3).add(0, 1.1, 0));
                t.hurtMarked = true;
                DbCommon.screen(level, c, 31);
                SubNet.send(level, SubNet.BLOOD_SPLASH, 0, c, new Vec3(0, 1, 0), 1f, 0, 0);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.8f);
                return true;
            }
            return false;
        });
    }

    static void clear() {
        ORBS.clear();
        BOUND.clear();
    }
}
