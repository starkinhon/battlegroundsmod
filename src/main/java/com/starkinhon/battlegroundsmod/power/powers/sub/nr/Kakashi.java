package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbScar;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// Kakashi (SubAlma of Naruto, tier 7), the approved preview forja/kakashi-kit v13 (kakashi.js, kakashi2.js).
// Z Raikiri: he crouches, the left hand holds the right wrist and the Raikiri grows in steps; then a run of up to 16 blocks
//   with the hand low ripping a furrow, the hit throws the target. HOLD Z: Raiju Tsuiga: he drives the hand into the ground
//   and the lightning becomes a hound of bolts that runs along the ground after the target, turning if it dodges (24
//   blocks), and bites: 2 s paralysis.
// X Kamui: the Mangekyo, the space round the target turns into a spiral and swallows it: out of the fight for 4 s, then
//   spat back out, hurt. Shift+X Kamui on himself: intangible for a second, the blow goes through him.
// C Sharingan Copy: the last skill anyone used near him in the last 10 s, once, 20% stronger.
// B Ninken: a seal of blood on the ground where he aims (16 blocks), armed 40 s; whoever steps on it is held 2.5 s by the
//   eight ninken coming out of the ground (the tier rule cuts it on stronger ones and bosses).
// V Perfect Susanoo: it rises from below with him at its head, each hand holding a Kamui shuriken; it throws both where he
//   aims and the space there becomes a spiral: all inside is pulled in and hurt, what reaches the middle vanishes for a
//   while and comes back thrown, the ground is swallowed.
// Shift+V Sennen Goroshi (secret): a poof, he is crouched behind the target, the tiger seal, a second of silence... and the
//   target flies up holding its backside until it is a little star. Low damage, it stuns, and it comes back down.
public final class Kakashi {
    // One page unit in blocks (the page's Kakashi is 2 units tall).
    private static final double PU = KakashiRig.BLOCK;
    private static final Map<UUID, int[]> SEALS = new HashMap<>();

    private Kakashi() {
    }

    public static Power create() {
        return new Power("kakashi", 0x56653A, 1.0f, List.of(
                new Ability(200, 0, Kakashi::raikiri),
                new Ability(400, 0, Kakashi::kamui),
                new Ability(300, 0, Kakashi::copy),
                new Ability(1400, 0, Kakashi::susanoo),
                new Ability(500, 0, Kakashi::ninken)),
                Arrays.asList(null, new Ability(240, 0, Kakashi::kamuiSelf), null, new Ability(1800, 0, Kakashi::sennen), null));
    }

    private static Vec3 lowHand(LivingEntity caster) {
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        return caster.position().add(0, caster.getBbHeight() * 0.4, 0).add(right.scale(0.45)).add(look.scale(0.1));
    }

    // ---------- Z: Raikiri / hold: Raiju Tsuiga ----------

    private static boolean raikiri(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity target = NrCommon.target(caster, 32);
        int ball = SubNet.newId();
        int form = 15;
        // Bots pick the hound when the target is far (it follows) and the run when it is close.
        boolean botHound = target != null && caster.distanceTo(target) > 12;
        SubCommon.pose(caster, "nr.raikiri");
        SubCommon.busy(caster, form + 2);
        SubCommon.sound(level, caster.position(), SoundEvents.BEEHIVE_WORK, 1f, 2f);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.CHIDORI, ball, caster.position(), Vec3.ZERO, 0, 1, 1);
                return true;
            }
            Vec3 hand = DbCommon.hand(caster);
            if (age < form) {
                // It grows in four steps (the page: 25, 50, 75, 100%).
                float size = Math.max(0.25f, (float) Math.ceil(Math.max(0, Math.min(1, (age / 20f - 0.1f) / 0.5f)) * 4) / 4f);
                SubNet.send(level, SubNet.CHIDORI, ball, hand, Vec3.ZERO, 0.3f * size, 1, 0);
                if (age % 3 == 0) {
                    SubCommon.sound(level, hand, SoundEvents.BEEHIVE_WORK, 0.8f, 2f);
                }
                return false;
            }
            if (SubCommon.held(caster, age, botHound ? 99 : 0)) {
                raiju(caster, level, guard, ball, target);
            } else {
                dash(caster, level, guard, ball, target);
            }
            return true;
        });
        return true;
    }

    private static void dash(LivingEntity caster, ServerLevel level, SubCommon.Guard guard, int ball, LivingEntity aimed) {
        Vec3 dir = SubCommon.flat(caster.getLookAngle());
        double dist = 16;
        LivingEntity goal = aimed != null && aimed.distanceTo(caster) < 18 ? aimed : NrCommon.target(caster, 18);
        if (goal != null) {
            dir = SubCommon.flat(goal.position().subtract(caster.position()));
            dist = Math.min(16, goal.distanceTo(caster) + 1.5);
        }
        int ticks = Math.max(5, (int) Math.ceil(dist / 1.3));
        Vec3 d = dir;
        double speed = dist / ticks;
        Vec3[] last = {Combat.center(caster)};
        Vec3 start = caster.position();
        Set<Integer> hit = new HashSet<>();
        SubCommon.setState(caster, NrCommon.UNTOUCHABLE, ticks + 2);
        SubCommon.busy(caster, ticks + 6);
        SubCommon.pose(caster, "nr.investida");
        SubCommon.sound(level, caster.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
        Tasks.add(age -> {
            if (guard.broken() || age > ticks) {
                caster.setDeltaMovement(caster.getDeltaMovement().scale(0.2));
                caster.hurtMarked = true;
                SubNet.send(level, SubNet.CHIDORI, ball, lowHand(caster), Vec3.ZERO, 0, 0, 1);
                Vec3 from = start.add(0, 0.9, 0);
                SubNet.send(level, SubNet.BOLT_TRAIL, 0, from, caster.position().add(0, 0.9, 0).subtract(from), 0.87f, 0, 0);
                return true;
            }
            caster.setDeltaMovement(d.x * speed, Math.min(caster.getDeltaMovement().y, 0.02), d.z * speed);
            caster.hurtMarked = true;
            caster.fallDistance = 0;
            Vec3 now = Combat.center(caster);
            SubNet.send(level, SubNet.CHIDORI, ball, lowHand(caster).add(d.scale(0.4)), Vec3.ZERO, 0.4f, 0, 0);
            // The hand low by the ground opens a lit furrow (soulsBlockDestruction; never under his feet).
            DbScar.along(caster, level, last[0].add(0, -0.6, 0), now.add(0, -0.6, 0), 0.45, 0);
            for (LivingEntity t : Combat.alongLine(level, last[0], now.add(d.scale(0.9)), 1.0, caster)) {
                if (!hit.add(t.getId())) {
                    continue;
                }
                Combat.hurtMagic(caster, t, 17);
                Stun.apply(t, JjCommon.control(caster, t, 32));
                // Thrown up and back like on the page (velocity set, again next tick so nothing eats it).
                Vec3 v = d.scale(1.3).add(0, 0.55, 0);
                t.setDeltaMovement(v);
                t.hurtMarked = true;
                Tasks.later(1, () -> {
                    t.setDeltaMovement(v);
                    t.hurtMarked = true;
                });
                Vec3 c = Combat.center(t);
                SubNet.send(level, SubNet.PIERCE, 0, c, d, 1, 0, 0);
                DbCommon.screen(level, c, 0);
                JjCommon.crater(level, t.position().add(d.scale(0.6)), 1.6, 0.8, 6);
                SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 1.4f);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            }
            last[0] = now;
            return false;
        });
    }

    private static void raiju(LivingEntity caster, ServerLevel level, SubCommon.Guard guard, int ball, LivingEntity target) {
        int dog = SubNet.newId();
        int slam = 6;
        double speed = 0.75;
        double maxRun = 24;
        SubCommon.pose(caster, "nr.mao_chao");
        SubCommon.busy(caster, slam + 4);
        Vec3[] pos = {null};
        Vec3[] dir = {SubCommon.flat(caster.getLookAngle())};
        double[] ran = {0};
        float[] gait = {0};
        int[] bite = {-1};
        Tasks.add(age -> {
            if (guard.broken() && pos[0] == null) {
                SubNet.send(level, SubNet.CHIDORI, ball, caster.position(), Vec3.ZERO, 0, 1, 1);
                return true;
            }
            if (age < slam) {
                Vec3 low = caster.position().add(dir[0].scale(0.7)).add(0, 0.3, 0);
                SubNet.send(level, SubNet.CHIDORI, ball, low, Vec3.ZERO, 0.3f, 1, 0);
                return false;
            }
            if (pos[0] == null) {
                SubNet.send(level, SubNet.CHIDORI, ball, caster.position(), Vec3.ZERO, 0, 1, 1);
                pos[0] = SubCommon.ground(level, caster.position().add(dir[0].scale(1.2)));
                SubCommon.sound(level, pos[0], SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
                SubCommon.sound(level, pos[0], SoundEvents.WOLF_GROWL, 1.4f, 1.6f);
                JjCommon.crater(level, pos[0], 1.2, 0.5, 4);
                DbCommon.hit(level, pos[0].add(0, 0.3, 0), 1, Db.BLUE);
            }
            if (bite[0] >= 0) {
                // The bite: the jaw opens, snaps, the hound fades in steps.
                int b = age - bite[0];
                float jaw = b < 3 ? SubCommon.smooth(b / 2.4f) : 1 - SubCommon.smooth((b - 2.4f) / 1.2f);
                float k = b < 6 ? 1 : Math.max(0, 1 - (b - 6) / 7f);
                pos[0] = pos[0].add(dir[0].scale(b < 3 ? 0.3 : 0));
                SubNet.send(level, SubNet.KK_HOUND, dog, pos[0], dir[0], gait[0], jaw, b >= 13 ? 1 : 0);
                return b >= 13;
            }
            // Runs low along the ground and turns after the target (5 rad/s like on the page).
            if (target != null && target.isAlive()) {
                Vec3 want = SubCommon.flat(target.position().subtract(pos[0]));
                double ang = Math.atan2(want.x, want.z) - Math.atan2(dir[0].x, dir[0].z);
                ang = Math.atan2(Math.sin(ang), Math.cos(ang));
                double turn = Math.max(-0.25, Math.min(0.25, ang));
                double a = Math.atan2(dir[0].x, dir[0].z) + turn;
                dir[0] = new Vec3(Math.sin(a), 0, Math.cos(a));
            }
            Vec3 next = pos[0].add(dir[0].scale(speed));
            next = SubCommon.ground(level, next);
            ran[0] += speed;
            pos[0] = next;
            gait[0] += 0.65f;
            boolean end = ran[0] >= maxRun || SubCommon.solid(level, next.add(0, 0.8, 0));
            if (age % 2 == 0) {
                DbScar.along(caster, level, next.subtract(dir[0].scale(speed * 2)).add(0, 0.1, 0), next.add(0, 0.1, 0), 0.3, 0);
            }
            for (LivingEntity t : Combat.inRadius(level, next.add(dir[0].scale(1.3)).add(0, 0.8, 0), 1.6, caster)) {
                Combat.hurtMagic(caster, t, 13);
                NrCommon.paralyze(level, t, JjCommon.control(caster, t, 40));
                DbCommon.hit(level, Combat.center(t), 1, Db.BLUE);
                DbCommon.screen(level, Combat.center(t), 0);
                SubCommon.sound(level, t.position(), SoundEvents.WOLF_GROWL, 1.2f, 1.2f);
                SubCommon.sound(level, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, 1.6f);
                bite[0] = age;
                break;
            }
            if (end && bite[0] < 0) {
                bite[0] = age;
            }
            SubNet.send(level, SubNet.KK_HOUND, dog, next, dir[0], gait[0], 0, 0);
            if (age % 4 == 0) {
                SubCommon.sound(level, next, SoundEvents.BEEHIVE_WORK, 0.8f, 1.8f);
            }
            return false;
        });
    }

    // ---------- X: Kamui ----------

    private static boolean kamui(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 34);
        if (target == null) {
            SubCommon.warn(caster, "kakashi.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean boss = JjCommon.isBoss(target);
        int open = 12;
        int vanish = 43;
        int gone = boss ? 40 : 80;
        int back = vanish + gone;
        int out = back + 12;
        int id = SubNet.newId();
        boolean wasInvisible = target.isInvisible();
        Vec3 c = Combat.center(target);
        SubCommon.pose(caster, "nr.bandana");
        SubCommon.busy(caster, open + 4);
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, vanish + 20, 3, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 0.7f);
        Tasks.add(age -> {
            boolean lost = !target.isAlive() || target.isRemoved();
            if (age < vanish && (guard.broken() || lost)) {
                SubNet.send(level, SubNet.KAMUI, id, c, Vec3.ZERO, 0, 0, 1);
                Freeze.free(target);
                return true;
            }
            Vec3 axis = caster.getEyePosition().subtract(c).normalize();
            if (age == open) {
                Freeze.hold(target, vanish - open + 2);
                Stun.apply(target, vanish - open);
                SubCommon.sound(level, c, SoundEvents.PORTAL_TRAVEL, 0.4f, 1.8f);
            }
            if (age >= open && age < vanish) {
                // the page: grows in 0.4 s, closes over the last 0.25 s; it turns faster and faster as it pulls
                int t = age - open;
                float k = SubCommon.smooth(t / 8f) * (1 - SubCommon.smooth((t - 26) / 5f));
                SubNet.send(level, SubNet.KAMUI, id, c, axis, k, Math.min(1, t / 26f), 0);
                float yaw = target.getYRot() + t * t * 0.35f;
                target.setYRot(yaw);
                target.setYHeadRot(yaw);
                target.setYBodyRot(yaw);
            }
            if (age == vanish) {
                SubNet.send(level, SubNet.KAMUI, id, c, axis, 0, 1, 1);
                banish(caster, level, target, gone);
                SubNet.send(level, SubNet.SELF_SCREEN, caster.getId(), caster.position(), Vec3.ZERO, 23, 0, 0);
                SubCommon.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.5f);
                return false;
            }
            if (age > vanish && age < out) {
                if (age >= back - 3) {
                    int t = age - back + 3;
                    float k = SubCommon.smooth(t / 5f) * (1 - SubCommon.smooth((t - 10) / 6f));
                    SubNet.send(level, SubNet.KAMUI, id + 1, c, axis, k, -Math.max(0.01f, 1 - t / 10f), 0);
                }
                if (age == back) {
                    spitOut(caster, level, target, c, wasInvisible, lost, 12, target.position().subtract(caster.position()));
                }
                return false;
            }
            SubNet.send(level, SubNet.KAMUI, id + 1, c, axis, 0, 0, 1);
            if (!wasInvisible && target.isAlive()) {
                target.setInvisible(false);
            }
            return true;
        });
        return true;
    }

    // Out of the fight in the other dimension: can't hit, can't be hit, can't move or use anything.
    private static void banish(LivingEntity caster, ServerLevel level, LivingEntity target, int gone) {
        target.setInvisible(true);
        Intangible.hold(target, gone);
        Freeze.hold(target, gone);
        PowerLogic.seal(target, gone);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, gone, 0, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, gone, 0, false, false));
        }
    }

    private static void spitOut(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 at, boolean wasInvisible, boolean lost, float damage, Vec3 away) {
        if (!wasInvisible) {
            target.setInvisible(false);
        }
        Freeze.free(target);
        if (!lost && target.isAlive()) {
            Combat.hurtMagic(caster, target, damage);
            Stun.apply(target, JjCommon.control(caster, target, 30));
            Combat.push(target, away, 0.8, 0.45);
            DbCommon.screen(level, at, 4);
        }
        SubCommon.sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.7f);
    }

    // ---------- Shift+X: Kamui on himself ----------

    private static boolean kamuiSelf(LivingEntity caster, ServerLevel level) {
        int id = SubNet.newId();
        int len = 22;
        Intangible.hold(caster, len);
        SubCommon.setState(caster, NrCommon.UNTOUCHABLE, len);
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, len + 6, 3, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.6f);
        Tasks.add(age -> {
            float k = age < 3 ? SubCommon.smooth(age / 3f) : 1 - SubCommon.smooth((age - len + 4) / 4f);
            Vec3 c = Combat.center(caster);
            SubNet.send(level, SubNet.KK_VORTEX, id, c, caster.getLookAngle(), (float) (1.9 * PU) * k, 0.25f, age >= len ? 1 : 0);
            return age >= len || !caster.isAlive();
        });
        return true;
    }

    // ---------- C: Sharingan Copy ----------

    private static boolean copy(LivingEntity caster, ServerLevel level) {
        NrCommon.Cast cast = NrCommon.lastCast(caster, 16);
        if (cast == null) {
            SubCommon.warn(caster, "kakashi.nothing_to_copy");
            return false;
        }
        var power = Powers.get(cast.power());
        Component name = power != null ? power.abilityName(cast.slot()) : Component.literal("?");
        SubCommon.pose(caster, "nr.selo");
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 40, 3, 0);
        SubCommon.warn(caster, "kakashi.copied", name);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.6f);
        SubCommon.setState(caster, NrCommon.COPY, 200);
        Tasks.later(6, () -> {
            if (!caster.isAlive()) {
                return;
            }
            if (!cast.ability().action().use(caster, level)) {
                SubCommon.warn(caster, "kakashi.copy_failed", name);
                SubCommon.clearState(caster, NrCommon.COPY);
            }
        });
        return true;
    }

    // ---------- B: Ninken (Tsuiga no Jutsu, a trap) ----------

    private static boolean ninken(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 at = SubCommon.ground(level, SubCommon.aimPoint(caster, 16));
        int id = SubNet.newId();
        // One seal at a time: a new one takes the place of the old.
        int[] mine = {id};
        SEALS.put(caster.getUUID(), mine);
        int bite = 7;
        int slam = 10;
        int armed = 800;
        SubCommon.pose(caster, "nr.mao_chao");
        SubCommon.busy(caster, slam + 8);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_WEAK, 1f, 1.4f);
        int[] lit = {-1};
        LivingEntity[] caught = {null};
        Tasks.add(age -> {
            boolean replaced = SEALS.get(caster.getUUID()) != mine;
            if (lit[0] < 0 && (age < bite + slam && guard.broken() || replaced || !caster.isAlive() || age > armed)) {
                SubNet.send(level, SubNet.KK_SEAL, id, at, Vec3.ZERO, 0, 0, 1);
                if (!replaced) {
                    SEALS.remove(caster.getUUID());
                }
                return true;
            }
            if (age == bite + slam) {
                // The scroll hits the ground and the seal runs to where he aimed.
                SubCommon.sound(level, caster.position(), SoundEvents.BOOK_PUT, 1.2f, 0.7f);
                SubCommon.sound(level, at, SoundEvents.SCULK_BLOCK_SPREAD, 1.2f, 0.6f);
            }
            if (age < bite + slam) {
                return false;
            }
            if (lit[0] < 0) {
                float k = SubCommon.smooth((age - bite - slam) / 10f);
                if (age % 2 == 0) {
                    SubNet.send(level, SubNet.KK_SEAL, id, at, Vec3.ZERO, k, 0, 0);
                }
                if (k < 1) {
                    return false;
                }
                for (LivingEntity t : Combat.inRadius(level, at.add(0, 0.5, 0), 1.3, caster)) {
                    if (Math.abs(t.getY() - at.y) < 1.6) {
                        caught[0] = t;
                        lit[0] = age;
                        break;
                    }
                }
                if (lit[0] < 0) {
                    return false;
                }
                LivingEntity t = caught[0];
                int hold = JjCommon.control(caster, t, 50);
                Freeze.hold(t, hold + 15);
                PowerLogic.seal(t, hold + 15);
                Stun.apply(t, hold + 15);
                Combat.hurtMagic(caster, t, 6);
                DbCommon.screen(level, Combat.center(t), 0);
                SubCommon.sound(level, at, SoundEvents.WOLF_GROWL, 1.6f, 0.9f);
                SubCommon.sound(level, at, SoundEvents.ROOTED_DIRT_BREAK, 1.6f, 0.8f);
                for (int i = 0; i < 8; i++) {
                    double a = i / 8.0 * Math.PI * 2 + 0.3;
                    JjCommon.crater(level, at.add(Math.cos(a) * 2.6 * PU, 0, Math.sin(a) * 2.6 * PU), 0.6, 0.4, 2);
                }
                return false;
            }
            int b = age - lit[0];
            LivingEntity t = caught[0];
            int hold = JjCommon.control(caster, t, 50);
            int end = 15 + hold;
            boolean gone = b >= end || !t.isAlive();
            SubNet.send(level, SubNet.KK_SEAL, id, at, Vec3.ZERO, 1 - 0.7f * SubCommon.smooth(b / (float) end), gone ? 0 : 1, gone ? 1 : 0);
            SubNet.send(level, SubNet.KK_DOGS, t.getId(), t.position(), at, b, 0, gone ? 1 : 0);
            if (b > 0 && b % 10 == 0 && !gone) {
                Combat.hurtMagic(caster, t, 2);
                SubCommon.sound(level, t.position(), SoundEvents.WOLF_GROWL, 1f, 0.8f + 0.1f * (b / 10));
            }
            if (gone) {
                // the dogs vanish in their poofs
                for (int i = 0; i < 8; i++) {
                    double a = i / 8.0 * Math.PI * 2 + 0.3;
                    SubNet.send(level, SubNet.POOF, 0, t.position().add(Math.cos(a) * 1.25 * PU, 0.5, Math.sin(a) * 1.25 * PU), Vec3.ZERO, 0.6f, 0, 0);
                }
                SubCommon.sound(level, t.position(), SoundEvents.CHICKEN_EGG, 1.2f, 0.5f);
                if (SEALS.get(caster.getUUID()) == mine) {
                    SEALS.remove(caster.getUUID());
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- V: Perfect Susanoo + Kamui shurikens ----------

    private static boolean susanoo(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int eye = 11;
        int born = eye + 36;
        int aim = born + 10;
        int flyMax = 40;
        Vec3 feet = SubCommon.ground(level, caster.position());
        double seat = KakashiRig.SEAT * PU;
        int sus = SubNet.newId();
        int[] sh = {SubNet.newId(), SubNet.newId()};
        int vort = SubNet.newId();
        float[] yaw = {(float) Math.toRadians(caster.getYRot())};
        Vec3[] fly = new Vec3[2];
        Vec3[] vel = new Vec3[2];
        Vec3[] center = {null};
        int[] thrown = {-1};
        int[] hitAt = {-1};
        int[] fadeAt = {-1};
        Set<Integer> banished = new HashSet<>();
        List<LivingEntity> inside = new ArrayList<>();
        SubCommon.pose(caster, "nr.bandana");
        SubCommon.busy(caster, aim + 4);
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, aim + 60, 3, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 0.6f);
        // He stands at its head (the page: y 13.5), looking free; the Susanoo is the shield: nothing gets to him.
        int[] seatAge = {0};
        Runnable unseat = Freeze.holdLookFree(caster, 400, a -> feet.add(0, seat * riseSeat(seatAge[0], eye, fadeAt[0]), 0));
        Runnable unshield = Intangible.shield(caster, 400);
        Tasks.add(age -> {
            seatAge[0] = age;
            caster.fallDistance = 0;
            boolean stop = guard.broken() && fadeAt[0] < 0;
            if (stop) {
                fadeAt[0] = age;
            }
            // ---- the Susanoo itself ----
            float rise = age < eye ? 0 : 1.15f * SubCommon.smooth((age - eye) / 32f);
            float alpha = fadeAt[0] < 0 ? 1 : 1 - SubCommon.smooth((age - fadeAt[0]) / 16f);
            float armK = SubCommon.smooth((age - eye - 16) / 10f);
            float throwK = 0;
            if (age >= born && thrown[0] < 0) {
                throwK = SubCommon.smooth((age - born) / 9f);
            } else if (thrown[0] >= 0) {
                float r = SubCommon.smooth((age - thrown[0]) / 8f);
                armK = 1 - r;
                throwK = 1 - r;
            }
            if (thrown[0] < 0) {
                yaw[0] = (float) Math.toRadians(caster.getYRot());
            }
            if (age >= eye) {
                SubNet.send(level, SubNet.KK_SUSANOO, caster.getId(), feet, new Vec3(yaw[0], armK, throwK), rise, alpha, alpha <= 0 ? 1 : 0);
            }
            if (age == eye) {
                SubCommon.sound(level, feet, SoundEvents.WARDEN_EMERGE, 2f, 0.7f);
                SubCommon.sound(level, feet, SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
            }
            // ---- the shurikens: in its hands, then thrown where he aims ----
            if (fadeAt[0] < 0 && age >= eye + 20 && thrown[0] < 0) {
                float a = SubCommon.smooth((age - eye - 20) / 6f);
                for (int i = 0; i < 2; i++) {
                    Vec3 p = KakashiRig.shurikenInHand(feet, yaw[0], i, armK, throwK);
                    SubNet.send(level, SubNet.KK_SHURIKEN, sh[i], p, Vec3.ZERO, (float) (1.1 * PU), a, 0);
                }
                if (age >= aim) {
                    thrown[0] = age;
                    Vec3 goal = SubCommon.aimPoint(caster, 64);
                    Vec3 right = new Vec3(Math.cos(yaw[0]), 0, Math.sin(yaw[0]));
                    center[0] = goal.add(0, 1.5 * PU, 0);
                    for (int i = 0; i < 2; i++) {
                        fly[i] = KakashiRig.shurikenInHand(feet, yaw[0], i, armK, throwK);
                        Vec3 to = goal.add(right.scale(i == 0 ? PU : -PU)).add(0, PU, 0);
                        vel[i] = to.subtract(fly[i]).normalize().scale(2.0);
                    }
                    SubCommon.sound(level, caster.position(), SoundEvents.TRIDENT_THROW.value(), 2f, 0.5f);
                    SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 2f, 0.5f);
                }
            }
            if (thrown[0] >= 0 && hitAt[0] < 0) {
                boolean landed = false;
                for (int i = 0; i < 2; i++) {
                    Vec3 next = fly[i].add(vel[i]);
                    if (next.distanceTo(center[0]) < 2.2 || SubCommon.solid(level, next) || age - thrown[0] > flyMax) {
                        landed = true;
                    }
                    fly[i] = next;
                    SubNet.send(level, SubNet.KK_SHURIKEN, sh[i], next, vel[i], (float) (1.5 * PU), 1, 0);
                }
                if (landed) {
                    hitAt[0] = age;
                    if (fly[0].distanceTo(center[0]) > 3) {
                        center[0] = fly[0].add(fly[1]).scale(0.5);
                    }
                    for (int i = 0; i < 2; i++) {
                        SubNet.send(level, SubNet.KK_SHURIKEN, sh[i], fly[i], vel[i], (float) (1.5 * PU), 0, 1);
                    }
                    DbCommon.screen(level, center[0], 0);
                    SubCommon.sound(level, center[0], SoundEvents.ENDERMAN_TELEPORT, 2.5f, 0.4f);
                    SubCommon.sound(level, center[0], SoundEvents.PORTAL_TRIGGER, 1.5f, 1.4f);
                }
            }
            // ---- the Kamui where they hit: 2.6 s, grows in 0.5 s, closes at 1.9 s ----
            if (hitAt[0] >= 0) {
                int t = age - hitAt[0];
                Vec3 c = center[0];
                float grow = 1 - (float) Math.pow(1 - Math.min(1, t / 10f), 3);
                float collapse = SubCommon.smooth((t - 38) / 10f);
                float radius = (float) (5.2 * grow * (1 - collapse));
                Vec3 axis = c.subtract(feet.add(0, seat, 0)).normalize();
                SubNet.send(level, SubNet.KK_VORTEX, vort, c, axis, radius, Math.min(1, t / 38f), t >= 50 ? 1 : 0);
                if (t <= 24 && t % 4 == 0) {
                    // the ground and everything in it swallowed into a smooth hole
                    JjCommon.carve(level, c, 1.5 + 3.4 * grow, c);
                }
                if (t < 38) {
                    for (LivingEntity e : Combat.inRadius(level, c, 9, caster)) {
                        if (banished.contains(e.getId())) {
                            continue;
                        }
                        if (!inside.contains(e)) {
                            inside.add(e);
                        }
                        Vec3 to = c.subtract(Combat.center(e));
                        double d = to.length();
                        Vec3 n = to.normalize();
                        Vec3 swirl = n.cross(axis).normalize();
                        double pull = 0.35 + 0.9 * (1 - Math.min(1, d / 9));
                        e.setDeltaMovement(n.scale(pull).add(swirl.scale(0.35)));
                        e.hurtMarked = true;
                        e.fallDistance = 0;
                        if (t % 5 == 0) {
                            Combat.hurtMagic(caster, e, 3);
                            Stun.apply(e, JjCommon.control(caster, e, 12));
                        }
                        float spin = e.getYRot() + 25;
                        e.setYRot(spin);
                        e.setYBodyRot(spin);
                        if (d < 2.2 && t > 12) {
                            // into the other dimension; comes back thrown when the space closes
                            banished.add(e.getId());
                            boolean was = e.isInvisible();
                            int gone = JjCommon.isBoss(e) ? 30 : 60;
                            banish(caster, level, e, gone);
                            Tasks.later(gone, () -> spitOut(caster, level, e, c, was, !e.isAlive(), 24, Combat.center(e).subtract(feet)));
                        }
                    }
                }
                if (t == 40) {
                    SubCommon.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 2f, 0.3f);
                }
                if (t == 50 && fadeAt[0] < 0) {
                    fadeAt[0] = age;
                }
            } else if (thrown[0] < 0 && stop) {
                for (int i = 0; i < 2; i++) {
                    SubNet.send(level, SubNet.KK_SHURIKEN, sh[i], feet, Vec3.ZERO, 0, 0, 1);
                }
            }
            if (fadeAt[0] >= 0 && age - fadeAt[0] >= 16) {
                SubNet.send(level, SubNet.KK_SUSANOO, caster.getId(), feet, new Vec3(yaw[0], 0, 0), rise, 0, 1);
                unseat.run();
                unshield.run();
                caster.fallDistance = 0;
                return true;
            }
            return false;
        });
        return true;
    }

    // How high he stands (0..1 of the seat): up with the Susanoo in 1 s, back down as it fades.
    private static double riseSeat(int age, int eye, int fadeAt) {
        double up = SubCommon.smooth((age - eye) / 20f);
        if (fadeAt >= 0) {
            up *= 1 - SubCommon.smooth((age - fadeAt) / 16f);
        }
        return up;
    }

    // ---------- Shift+V: Sennen Goroshi (secret) ----------

    private static boolean sennen(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 20);
        if (target == null) {
            SubCommon.warn(caster, "kakashi.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int vanish = 5;
        int appear = vanish + 7;
        int poke = appear + 34;
        int star = poke + 9 + 30;
        boolean wasInvisible = caster.isInvisible();
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, poke + 10);
        // the target doesn't notice: held where it is from the poof to the poke
        Freeze.hold(target, poke + 1);
        Stun.apply(target, poke + 1);
        PowerLogic.seal(target, poke + 1);
        Tasks.add(age -> {
            if (age <= poke && (guard.broken() || !target.isAlive())) {
                caster.setInvisible(wasInvisible);
                Freeze.free(target);
                return true;
            }
            if (age == vanish) {
                SubNet.send(level, SubNet.POOF, 0, caster.position().add(0, 0.9, 0), Vec3.ZERO, 1.1f, 0, 0);
                SubCommon.sound(level, caster.position(), SoundEvents.CHICKEN_EGG, 1f, 0.7f);
                caster.setInvisible(true);
            }
            if (age == appear) {
                Vec3 back = SubCommon.flat(target.getLookAngle()).scale(-1.25 * PU);
                Vec3 at = target.position().add(back);
                if (SubCommon.solid(level, at.add(0, 0.5, 0))) {
                    at = target.position().subtract(SubCommon.flat(target.position().subtract(caster.position())).scale(1.1));
                }
                NrCommon.teleport(caster, at, NrCommon.yawTo(at, target.position()));
                caster.setInvisible(wasInvisible);
                SubNet.send(level, SubNet.POOF, 0, at.add(0, 0.6, 0), Vec3.ZERO, 0.8f, 0, 0);
                SubCommon.pose(caster, "nr.selo");
                SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 40, 3, 0);
                Freeze.hold(caster, poke - appear + 4);
            }
            if (age == poke) {
                SubCommon.pose(caster, "nr.investida");
                Combat.hurtMagic(caster, target, 5);
                Freeze.free(target);
                Stun.apply(target, JjCommon.control(caster, target, 70));
                PowerLogic.seal(target, JjCommon.control(caster, target, 60));
                Vec3 c = Combat.center(target);
                DbCommon.screen(level, c, 0);
                SubNet.send(level, SubNet.KK_SENNEN, target.getId(), c, Vec3.ZERO, 0, 0, 0);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 1.6f);
                SubCommon.sound(level, c, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.6f, 0.8f);
                Vec3 up = new Vec3(0, 2.3, 0).add(SubCommon.flat(target.getLookAngle()).scale(0.15));
                target.setDeltaMovement(up);
                target.hurtMarked = true;
                Tasks.later(1, () -> {
                    target.setDeltaMovement(up);
                    target.hurtMarked = true;
                });
            }
            if (age > poke) {
                // it comes back down (3 s), no fall damage: the joke doesn't kill
                target.fallDistance = 0;
                float yaw = target.getYRot() + 35;
                target.setYRot(yaw);
                target.setYBodyRot(yaw);
            }
            if (age == star) {
                SubNet.send(level, SubNet.KK_SENNEN, target.getId(), Combat.center(target), Vec3.ZERO, 1, 0, 0);
                SubCommon.sound(level, target.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 2f);
            }
            return age > poke + 80 || (age > star && target.onGround());
        });
        return true;
    }

    // The world closed (ServerReset): nothing of it is kept for the next one (old entities, old game times).
    public static void resetStatics() {
        SEALS.clear();
    }
}
