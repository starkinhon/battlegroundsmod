// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
import com.starkinhon.battlegroundsmod.network.ModPayloads;
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
import net.neoforged.neoforge.network.PacketDistributor;

// Kakashi (SubAlma of Naruto, tier 7), the approved preview forja/kakashi-kit v13 (kakashi.js, kakashi2.js).
// Z Raikiri: he crouches, the left hand holds the right wrist and the Raikiri grows in steps; then a run of up to 16 blocks
//   with the hand low ripping a furrow, the hit throws the target. HOLD Z: Raiju Tsuiga: he drives the hand into the ground
//   and the lightning becomes a hound of bolts that runs along the ground after the target, turning if it dodges (24
//   blocks), and bites: 2 s paralysis.
// X Kamui: the Mangekyo, the spiral opens on the target (or the aimed spot) and sucks everyone round it into the middle,
//   stunned; then they vanish for a moment and are spat back out, hurt. Shift+X Kamui on himself: a counter, a blow from
//   close goes through him and anything thrown from afar comes back out at the thrower (2026-11-26).
// C Sharingan Copy: the eye memorizes the last skill used within 24 blocks (kept 15 s, shown on the C slot: SharinganMemory);
//   C uses it once, 20% stronger.
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
                // the 1st update's cut: a zigzag bolt splitting the target across, and one impact frame (no flicker)
                SubNet.send(level, SubNet.KK_CUT, t.getId(), t.position(), d, t.getBbHeight(), 0, 0);
                DbCommon.screen(level, c, 38);
                DbCommon.screen(level, c, 26);
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
                // The hound runs at you: a guard from the front stops the bite and Kakashi is stunned (rebalance
                // 2026-11-20, the divines' rule).
                if (com.starkinhon.battlegroundsmod.combat.Defense.blockGrab(t, caster)) {
                    bite[0] = age;
                    break;
                }
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

    // Remade 2026-11-26 (Stark: "bem inútil"; "uma skill que suga os inimigos literalmente, estunando"): the spiral opens on
    // the target (or where he aims) and SUCKS IN everyone round it, stunned while dragged to the middle; then all of them
    // vanish into the other dimension for a moment and are spat back out, hurt.
    private static final double KAMUI_PULL = 6;
    private static final float KAMUI_LOOK = 1.9f;

    private static boolean kamui(LivingEntity caster, ServerLevel level) {
        LivingEntity aimed = NrCommon.target(caster, 34);
        Vec3 c;
        if (aimed != null) {
            c = Combat.center(aimed);
        } else {
            Vec3 at = SubCommon.aimPoint(caster, 34);
            if (at.distanceTo(caster.getEyePosition()) > 33) {
                SubCommon.warn(caster, "kakashi.no_target");
                return false;
            }
            c = at.add(0, 1, 0);
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int open = 8;
        int vanish = 38;
        int id = SubNet.newId();
        // who it caught, and whether each was invisible before
        Map<LivingEntity, Boolean> caught = new HashMap<>();
        if (aimed != null && Combat.isTarget(aimed, caster)) {
            caught.put(aimed, aimed.isInvisible());
        }
        int[] gone = {24};
        SubCommon.pose(caster, "nr.bandana");
        SubCommon.busy(caster, open + 4);
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, vanish + 20, 3, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 0.7f);
        Tasks.add(age -> {
            Vec3 axis = caster.getEyePosition().subtract(c).normalize();
            if (age < vanish && guard.broken()) {
                SubNet.send(level, SubNet.KAMUI, id, c, Vec3.ZERO, 0, 0, 1);
                caught.keySet().forEach(Freeze::free);
                return true;
            }
            if (age >= open && age < vanish) {
                int t = age - open;
                float k = SubCommon.smooth(t / 8f) * (1 - SubCommon.smooth((t - 25) / 5f));
                SubNet.send(level, SubNet.KAMUI, id, c, axis, k * KAMUI_LOOK, Math.min(1, t / 26f), 0);
                if (t == 0) {
                    SubCommon.sound(level, c, SoundEvents.PORTAL_TRAVEL, 0.5f, 1.8f);
                }
                // everyone in reach is caught (once) and dragged to the middle, faster as it closes
                for (LivingEntity e : Combat.inRadius(level, c, KAMUI_PULL, caster)) {
                    if (e != caster && !caught.containsKey(e)) {
                        caught.put(e, e.isInvisible());
                        // limp, each limb twisting its own way toward the spiral (client LimpBody)
                        limp(e, ModPayloads.Limp.PULL, 0, 0);
                    }
                }
                // blocks per tick toward the middle: slow at first, fast as it closes (Stun holds them, so they are moved
                // by hand, the way the Raikiri's run moves Kakashi)
                double speed = 0.18 + 0.42 * Math.min(1, t / 26.0);
                for (LivingEntity e : caught.keySet()) {
                    if (!e.isAlive()) {
                        continue;
                    }
                    Stun.apply(e, vanish - age + 2);
                    Vec3 mid = Combat.center(e);
                    Vec3 to = c.subtract(mid);
                    double d = to.length();
                    if (d > 0.25) {
                        Vec3 next = e.position().add(to.scale(Math.min(speed, d) / d));
                        if (!SubCommon.solid(level, next.add(0, e.getBbHeight() * 0.5, 0))) {
                            if (e instanceof net.minecraft.server.level.ServerPlayer player) {
                                player.teleportTo(level, next.x, next.y, next.z, player.getYRot(), player.getXRot());
                            } else {
                                e.teleportTo(next.x, next.y, next.z);
                            }
                            e.setDeltaMovement(Vec3.ZERO);
                            e.fallDistance = 0;
                        }
                    }
                    float yaw = e.getYRot() + 18 + t * 1.5f;
                    e.setYRot(yaw);
                    e.setYHeadRot(yaw);
                    e.setYBodyRot(yaw);
                    if (t % 10 == 5) {
                        Combat.hurtMagic(caster, e, 1.5f);
                    }
                    if (t % 6 == 3) {
                        limp(e, ModPayloads.Limp.JOLT, 0, 0.35f + 0.5f * Math.min(1, t / 26f));
                    }
                }
            }
            if (age == vanish) {
                SubNet.send(level, SubNet.KAMUI, id, c, axis, 0, 1, 1);
                caught.keySet().removeIf(e -> !e.isAlive() || e.isRemoved());
                // ~1.2 s in the other dimension (rebalance 2026-11-20, it was ~7 s); a boss only 0.8 s
                for (LivingEntity e : caught.keySet()) {
                    limp(e, ModPayloads.Limp.OFF, 0, 0);
                    banish(caster, level, e, JjCommon.isBoss(e) ? 16 : 24);
                }
                if (!caught.isEmpty()) {
                    SubNet.send(level, SubNet.SELF_SCREEN, caster.getId(), caster.position(), Vec3.ZERO, 23, 0, 0);
                }
                SubCommon.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.5f);
                return caught.isEmpty();
            }
            int back = vanish + gone[0];
            if (age > vanish && age < back + 12) {
                if (age >= back - 3) {
                    int t = age - back + 3;
                    float k = SubCommon.smooth(t / 5f) * (1 - SubCommon.smooth((t - 10) / 6f));
                    SubNet.send(level, SubNet.KAMUI, id + 1, c, axis, k * KAMUI_LOOK, -Math.max(0.01f, 1 - t / 10f), 0);
                }
                for (Map.Entry<LivingEntity, Boolean> e : caught.entrySet()) {
                    LivingEntity who = e.getKey();
                    int out = JjCommon.isBoss(who) ? vanish + 16 : back;
                    if (age < out && who.isAlive()) {
                        who.setInvisible(true);
                    }
                    if (age == out) {
                        Vec3 away = SubCommon.flat(who.position().subtract(c));
                        if (away.lengthSqr() < 1.0E-4) {
                            away = SubCommon.flat(c.subtract(caster.position()));
                        }
                        spitOut(caster, level, who, c, e.getValue(), false, 12, away);
                    }
                }
                return false;
            }
            if (age < vanish) {
                return false;
            }
            SubNet.send(level, SubNet.KAMUI, id + 1, c, axis, 0, 0, 1);
            caught.forEach((who, wasInvisible) -> {
                if (!wasInvisible && who.isAlive()) {
                    who.setInvisible(false);
                }
            });
            return true;
        });
        return true;
    }

    // Out of the fight in the other dimension: can't hit, can't be hit, can't move or use anything.
    private static void banish(LivingEntity caster, ServerLevel level, LivingEntity target, int gone) {
        target.setInvisible(true);
        // the effect too: any other effect changing (the stun running out) makes vanilla recompute the invisible flag
        // from the effects, and he showed up standing ~8 ticks in
        target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, gone + 2, 0, false, false));
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
            target.removeEffect(MobEffects.INVISIBILITY);
            target.setInvisible(false);
        }
        Freeze.free(target);
        if (!lost && target.isAlive()) {
            Combat.hurtMagic(caster, target, damage);
            Stun.apply(target, JjCommon.control(caster, target, 10));
            Combat.push(target, away, 0.8, 0.45);
            // the 1st update: thrown out limp with a burst of dark shards and one impact frame
            limp(target, ModPayloads.Limp.THROW, 16, 0.8f);
            SubNet.send(level, SubNet.KK_SHARDS, SubNet.newId(), Combat.center(target), Vec3.ZERO, 0, 0, 0);
            DbCommon.screen(level, at, 38);
            DbCommon.screen(level, at, 4);
        }
        SubCommon.sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.7f);
    }

    private static void limp(LivingEntity target, int phase, int ticks, float strength) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.Limp(target.getId(), phase, ticks, strength));
    }

    // ---------- Shift+X: Kamui on himself ----------

    // Remade 2026-11-26 (Stark: a counter, "jogou prego? Kamui em mim, devolvo pra você"): for ~1.1 s a blow from close goes
    // through him (as before) and anything thrown from afar, a shot or a technique, goes into the Kamui and comes back out
    // at whoever threw it. No stun or push gets him meanwhile (Stun.apply, Combat.push).
    private static final int KAMUI_SELF_TICKS = 22;
    // The most one returned blow takes (a Divine ult would kill in one go otherwise), and its stun.
    private static final float KAMUI_RETURN_MAX = 16;
    private static final Map<UUID, Set<UUID>> RETURNED = new HashMap<>();

    private static boolean kamuiSelf(LivingEntity caster, ServerLevel level) {
        int id = SubNet.newId();
        int len = KAMUI_SELF_TICKS;
        RETURNED.put(caster.getUUID(), new HashSet<>());
        SubCommon.setState(caster, NrCommon.UNTOUCHABLE, len);
        SubCommon.setState(caster, NrCommon.KAMUI_COUNTER, len);
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, len + 6, 3, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.6f);
        Tasks.add(age -> {
            float k = age < 3 ? SubCommon.smooth(age / 3f) : 1 - SubCommon.smooth((age - len + 4) / 4f);
            Vec3 c = Combat.center(caster);
            SubNet.send(level, SubNet.KK_VORTEX, id, c, caster.getLookAngle(), (float) (1.9 * PU) * k, 0.25f, age >= len ? 1 : 0);
            if (age >= len || !caster.isAlive()) {
                RETURNED.remove(caster.getUUID());
                return true;
            }
            return false;
        });
        return true;
    }

    // A blow from afar swallowed by the Shift+X: it comes out of a Kamui at the one who threw it (once per enemy per use,
    // so a beam that hits every tick is returned once).
    public static void kamuiReturn(LivingEntity kakashi, ServerLevel level, LivingEntity enemy, float amount) {
        Set<UUID> done = RETURNED.get(kakashi.getUUID());
        if (done == null || !done.add(enemy.getUUID())) {
            return;
        }
        Vec3 at = Combat.center(enemy);
        Vec3 axis = kakashi.getEyePosition().subtract(at).normalize();
        int id = SubNet.newId();
        Tasks.add(age -> {
            float k = SubCommon.smooth(age / 4f) * (1 - SubCommon.smooth((age - 8) / 6f));
            SubNet.send(level, SubNet.KAMUI, id, at, axis, k, -Math.max(0.01f, 1 - age / 10f), age >= 14 ? 1 : 0);
            return age >= 14;
        });
        Tasks.later(3, () -> {
            if (!enemy.isAlive() || !kakashi.isAlive()) {
                return;
            }
            Combat.hurtMagic(kakashi, enemy, Math.min(KAMUI_RETURN_MAX, Math.max(4, amount)));
            Stun.apply(enemy, JjCommon.control(kakashi, enemy, 15));
            Combat.push(enemy, enemy.position().subtract(kakashi.position()), 0.6, 0.3);
            DbCommon.screen(level, at, 4);
        });
        SubCommon.sound(level, kakashi.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.4f);
        SubCommon.sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.7f);
    }

    // A real shot (an arrow, a kunai) at Kakashi in his Kamui: it flies back at whoever threw it. Called from the projectile
    // impact event; true when it was sent back (the impact is cancelled).
    public static boolean reflectShot(net.minecraft.world.entity.projectile.Projectile shot, net.minecraft.world.entity.Entity hitEntity) {
        if (!(hitEntity instanceof LivingEntity kakashi) || !(kakashi.level() instanceof ServerLevel level)
                || !SubCommon.hasState(kakashi, NrCommon.KAMUI_COUNTER)) {
            return false;
        }
        Vec3 v = shot.getDeltaMovement();
        net.minecraft.world.entity.Entity owner = shot.getOwner();
        double speed = Math.max(1.2, v.length() * 1.3);
        Vec3 back = owner != null && owner.isAlive() && owner != kakashi
                ? Combat.center(owner).subtract(shot.position()).normalize()
                : v.normalize().scale(-1);
        shot.setDeltaMovement(back.scale(speed));
        shot.setOwner(kakashi);
        shot.hurtMarked = true;
        if (shot instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
            arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        }
        SubCommon.sound(level, shot.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
        return true;
    }

    // ---------- C: Sharingan Copy ----------

    private static boolean copy(LivingEntity caster, ServerLevel level) {
        // Exactly the skill the C slot shows (SharinganMemory): the last one the eye saw, kept 15 s.
        NrCommon.Cast cast = SharinganMemory.current(caster);
        if (cast == null) {
            SubCommon.warn(caster, "kakashi.nothing_to_copy");
            return false;
        }
        var power = Powers.get(cast.power());
        Component name = power != null ? power.abilityName(cast.slot()) : Component.literal("?");
        SharinganMemory.forget(caster);
        SubCommon.pose(caster, "nr.selo");
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 40, 3, 0);
        // the Sharingan in close-up on his own screen, the tomoe spinning fast
        SubNet.send(level, SubNet.KK_EYE, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 1, 0, 0);
        SubCommon.warn(caster, "kakashi.copied", name);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.6f);
        SubCommon.setState(caster, NrCommon.COPY, 200);
        Tasks.later(6, () -> {
            if (!caster.isAlive()) {
                return;
            }
            if (!cast.ability().action().use(caster, level)) {
                // It could not go off here (no target, no ground): the copy is not lost and C comes back at once.
                SubCommon.warn(caster, "kakashi.copy_failed", name);
                SubCommon.clearState(caster, NrCommon.COPY);
                SharinganMemory.keep(caster, cast);
                int key = SharinganMemory.copyKey(caster);
                if (key >= 0) {
                    caster.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).cooldowns[key] = 0;
                    if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
                        PowerLogic.sync(player);
                    }
                }
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
                int hold = JjCommon.control(caster, t, 45);
                Freeze.hold(t, hold + 15);
                PowerLogic.seal(t, hold + 15);
                Stun.apply(t, hold + 15);
                Combat.hurtMagic(caster, t, 6);
                DbCommon.screen(level, Combat.center(t), 38);
                DbCommon.screen(level, Combat.center(t), 4);
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
            int hold = JjCommon.control(caster, t, 45);
            int end = 15 + hold;
            boolean gone = b >= end || !t.isAlive();
            SubNet.send(level, SubNet.KK_SEAL, id, at, Vec3.ZERO, 1 - 0.7f * SubCommon.smooth(b / (float) end), gone ? 0 : 1, gone ? 1 : 0);
            // b = the target's height: Pakkun climbs onto its head
            SubNet.send(level, SubNet.KK_DOGS, t.getId(), t.position(), at, b, t.getBbHeight(), gone ? 1 : 0);
            // each dog comes out of the ground in its own poof (the same delays and places as NrFx.dogs)
            for (int i = 0; i < 8 && !gone; i++) {
                if (b == Math.round(20 * (0.04f * i + 0.02f * ((i * 5) % 3)))) {
                    double a = i / 8.0 * Math.PI * 2 + 0.3;
                    SubNet.send(level, SubNet.POOF, 0, at.add(Math.cos(a) * 2.6 * PU, 0.5, Math.sin(a) * 2.6 * PU), Vec3.ZERO, i == 0 ? 0.4f : 0.55f, 0, 0);
                }
            }
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
        // Stark 2026-11-26: any blow cut it while it grew. Up in it, no stun gets him (the shield already takes the damage).
        SubCommon.setState(caster, NrCommon.SUSANOO_UP, 400);
        LivingEntity[] lock = {null};
        // The Raikiri de Kamui at the end (1st update, Stark approved 2026-11-27, forja/kakashi-kit/kakashi3.js): when
        // it starts, who it goes for, the spot behind them and the ids of its lightning and its two spirals.
        int[] rkAt = {-1};
        LivingEntity[] victim = {null};
        Vec3[] behind = {null};
        Runnable[] rkHold = {null};
        int rkBall = SubNet.newId();
        int rkSpiral = SubNet.newId();
        Tasks.add(age -> {
            seatAge[0] = age;
            if (rkAt[0] >= 0) {
                return raikiriKamui(caster, level, guard, age - rkAt[0], victim[0], behind, rkHold, rkBall, rkSpiral, feet, yaw[0], unseat,
                        unshield);
            }
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
                    // Stark 2026-11-26: "não tem acerto": the shurikens go after the one he aims at (or the nearest
                    // in front), and only fall on the aimed spot when there is nobody
                    lock[0] = NrCommon.target(caster, 64);
                    Vec3 goal = lock[0] != null ? lock[0].position() : SubCommon.aimPoint(caster, 64);
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
                if (lock[0] != null && lock[0].isAlive()) {
                    // homing: the space opens where the target is now
                    center[0] = Combat.center(lock[0]);
                }
                for (int i = 0; i < 2; i++) {
                    if (lock[0] != null && lock[0].isAlive()) {
                        vel[i] = center[0].subtract(fly[i]).normalize().scale(2.2);
                    }
                    Vec3 next = fly[i].add(vel[i]);
                    if (next.distanceTo(center[0]) < 2.2 || SubCommon.solid(level, next) || age - thrown[0] > flyMax) {
                        landed = true;
                    }
                    // anyone in its way stops it there (the hit)
                    if (!landed && !Combat.alongLine(level, fly[i], next, 1.4, caster).isEmpty()) {
                        landed = true;
                        center[0] = next;
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
                    // the hit itself: the ones the shurikens strike take it before the pull
                    for (LivingEntity e : Combat.inRadius(level, center[0], 3, caster)) {
                        Combat.hurtMagic(caster, e, 10);
                        Stun.apply(e, JjCommon.control(caster, e, 20));
                    }
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
                            Combat.hurtMagic(caster, e, 4);
                            Stun.apply(e, JjCommon.control(caster, e, 12));
                        }
                        float spin = e.getYRot() + 25;
                        e.setYRot(spin);
                        e.setYBodyRot(spin);
                        if (d < 2.2 && t > 12) {
                            // into the other dimension; comes back thrown when the space closes
                            banished.add(e.getId());
                            boolean was = e.isInvisible();
                            // ~1.2 s away: with the vortex and the stun after, 4 s at most (rebalance 2026-11-20)
                            int gone = JjCommon.isBoss(e) ? 16 : 24;
                            banish(caster, level, e, gone);
                            Tasks.later(gone, () -> spitOut(caster, level, e, c, was, !e.isAlive(), 30, Combat.center(e).subtract(feet)));
                        }
                    }
                }
                if (t == 40) {
                    SubCommon.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 2f, 0.3f);
                }
                if (t == 50 && fadeAt[0] < 0) {
                    // Spat out dizzy: now the Raikiri goes for the one he locked (or the nearest one it caught). Nobody
                    // left: the Susanoo just fades as before.
                    LivingEntity v = lock[0] != null && lock[0].isAlive() && !lock[0].isRemoved() ? lock[0] : null;
                    if (v == null) {
                        double best = Double.MAX_VALUE;
                        for (LivingEntity e : inside) {
                            if (e.isAlive() && !e.isRemoved() && e.distanceToSqr(caster) < best) {
                                best = e.distanceToSqr(caster);
                                v = e;
                            }
                        }
                    }
                    if (v != null && v.distanceTo(caster) < 72) {
                        victim[0] = v;
                        rkAt[0] = age;
                    } else {
                        fadeAt[0] = age;
                    }
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
                SubCommon.clearState(caster, NrCommon.SUSANOO_UP);
                caster.fallDistance = 0;
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- V's end: Raikiri de Kamui ----------

    // Ticks from the start of the end: the Raikiri grows in his hand up in the Susanoo (which turns see-through), he twists
    // into a Kamui and is gone (the Susanoo with him), comes out of another spiral right behind the target and goes through
    // it: one impact frame, the lightning bursting out of its front, the shock over its body, and it flies.
    private static final int RK_CHARGE = 22;
    private static final int RK_IN = 33;
    private static final int RK_OUT = 34;
    private static final int RK_HIT = 40;
    private static final int RK_END = 52;

    private static boolean raikiriKamui(LivingEntity caster, ServerLevel level, SubCommon.Guard guard, int r, LivingEntity target, Vec3[] behind,
            Runnable[] hold, int ball, int spiral, Vec3 feet, float yaw, Runnable unseat, Runnable unshield) {
        caster.fallDistance = 0;
        boolean seated = r < RK_IN;
        if (guard.broken() || !caster.isAlive() || (r < RK_HIT && (!target.isAlive() || target.isRemoved()))) {
            SubNet.send(level, SubNet.CHIDORI, ball, caster.position(), Vec3.ZERO, 0, 1, 1);
            SubNet.send(level, SubNet.KAMUI, spiral, caster.position(), Vec3.ZERO, 0, 0, 1);
            SubNet.send(level, SubNet.KAMUI, spiral + 1, caster.position(), Vec3.ZERO, 0, 0, 1);
            SubNet.send(level, SubNet.KK_SUSANOO, caster.getId(), feet, new Vec3(yaw, 0, 0), 1.15f, 0, 1);
            caster.removeEffect(MobEffects.INVISIBILITY);
            unseat.run();
            unshield.run();
            if (hold[0] != null) {
                hold[0].run();
            }
            SubCommon.clearState(caster, NrCommon.SUSANOO_UP);
            return true;
        }
        SubCommon.busy(caster, 3);
        if (seated) {
            // The Susanoo stays round him, see-through, then goes as he twists away.
            float alpha = r < RK_CHARGE ? 1 - 0.7f * SubCommon.smooth(r / 8f) : 0.3f * (1 - SubCommon.smooth((r - RK_CHARGE) / 10f));
            SubNet.send(level, SubNet.KK_SUSANOO, caster.getId(), feet, new Vec3(yaw, 0, 0), 1.15f, alpha, 0);
        }
        if (r == 0) {
            SubCommon.pose(caster, "nr.raikiri");
            SubCommon.sound(level, caster.position(), SoundEvents.BEEHIVE_WORK, 1f, 2f);
        }
        if (r < RK_IN || (r >= RK_OUT && r < RK_HIT + 6)) {
            // The Raikiri in his hand (it grows in four steps, like Z's).
            float size = r < RK_CHARGE ? Math.max(0.25f, (float) Math.ceil(Math.min(1, r / 14f) * 4) / 4f) : r >= RK_HIT ? 1 - (r - RK_HIT) / 6f : 1;
            SubNet.send(level, SubNet.CHIDORI, ball, DbCommon.hand(caster), Vec3.ZERO, 0.3f * size, 1, 0);
            if (r % 3 == 0) {
                SubCommon.sound(level, DbCommon.hand(caster), SoundEvents.BEEHIVE_WORK, 0.8f, 2f);
            }
        }
        if (r >= RK_CHARGE && r < RK_IN) {
            // He twists into the spiral.
            int t = r - RK_CHARGE;
            Vec3 c = Combat.center(caster);
            float k = SubCommon.smooth(t / 6f);
            SubNet.send(level, SubNet.KAMUI, spiral, c, new Vec3(0, 1, 0), k * 1.2f, Math.min(1, t / 10f), 0);
            if (t == 0) {
                SubCommon.sound(level, c, SoundEvents.PORTAL_TRAVEL, 0.5f, 1.8f);
                SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 30, 3, 0);
            }
        }
        if (r == RK_IN) {
            SubNet.send(level, SubNet.KAMUI, spiral, Combat.center(caster), new Vec3(0, 1, 0), 0, 1, 1);
            SubNet.send(level, SubNet.CHIDORI, ball, caster.position(), Vec3.ZERO, 0, 1, 0);
            SubNet.send(level, SubNet.KK_SUSANOO, caster.getId(), feet, new Vec3(yaw, 0, 0), 1.15f, 0, 1);
            caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 3, 0, false, false));
            SubCommon.sound(level, caster.position(), SoundEvents.ENDERMAN_TELEPORT, 1f, 0.5f);
            unseat.run();
            unshield.run();
            SubCommon.clearState(caster, NrCommon.SUSANOO_UP);
        }
        if (r == RK_OUT) {
            // Right behind the target, on its far side from him (the vortex spun it, so not by where it looks), or on the
            // near side when that is a wall.
            Vec3 away = SubCommon.flat(target.position().subtract(caster.position()));
            if (away.lengthSqr() < 1.0E-4) {
                away = SubCommon.flat(caster.getLookAngle());
            }
            Vec3 spot = target.position().add(away.normalize().scale(1.7));
            if (SubCommon.solid(level, spot.add(0, 0.5, 0)) || SubCommon.solid(level, spot.add(0, 1.5, 0))) {
                spot = target.position().add(SubCommon.flat(caster.position().subtract(target.position())).scale(1.7));
            }
            behind[0] = spot;
            Vec3 face = target.position().subtract(spot);
            float y = (float) (Math.atan2(-face.x, face.z) * 180 / Math.PI);
            if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
                player.teleportTo(level, spot.x, spot.y, spot.z, y, 0);
            } else {
                caster.teleportTo(spot.x, spot.y, spot.z);
                caster.setYRot(y);
            }
            caster.removeEffect(MobEffects.INVISIBILITY);
            hold[0] = Freeze.holdLookFree(caster, RK_END - RK_OUT, a -> behind[0]);
            SubCommon.pose(caster, "nr.investida");
            SubCommon.sound(level, spot, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.9f);
            // the target stays dizzy until the blow
            Stun.apply(target, RK_HIT - RK_OUT + 4);
        }
        if (r >= RK_OUT && r < RK_HIT) {
            // He comes out of another spiral, already running through.
            int t = r - RK_OUT;
            float k = SubCommon.smooth(t / 3f) * (1 - SubCommon.smooth((t - 3) / 3f));
            Vec3 axis = SubCommon.flat(target.position().subtract(behind[0]));
            SubNet.send(level, SubNet.KAMUI, spiral + 1, behind[0].add(0, 1.1, 0), axis, k * 1.2f, -Math.max(0.01f, 1 - t / 6f), 0);
        }
        if (r == RK_HIT) {
            SubNet.send(level, SubNet.KAMUI, spiral + 1, behind[0].add(0, 1.1, 0), Vec3.ZERO, 0, 0, 1);
            Vec3 fwd = SubCommon.flat(target.position().subtract(behind[0]));
            if (fwd.lengthSqr() < 1.0E-4) {
                fwd = SubCommon.flat(caster.getLookAngle());
            }
            Vec3 c = Combat.center(target);
            behind[0] = behind[0].add(fwd.normalize().scale(0.75));
            Combat.hurtMagic(caster, target, 26);
            Stun.apply(target, JjCommon.control(caster, target, 30));
            // one impact frame (no flashing), the lightning out of its front and the shock over its body
            DbCommon.screen(level, c, 38);
            DbCommon.screen(level, c, 26);
            SubNet.send(level, SubNet.PIERCE, 0, c, fwd, 1, 0, 0);
            SubNet.send(level, SubNet.PARALYZE, target.getId(), target.position(), Vec3.ZERO, 32, 0, 0);
            // it flies (set three ticks running so the stun and the ground don't eat it)
            Vec3 v = fwd.normalize().scale(1.7).add(0, 0.65, 0);
            limp(target, ModPayloads.Limp.THROW, 18, 0.9f);
            target.setDeltaMovement(v);
            target.hurtMarked = true;
            for (int k = 1; k <= 2; k++) {
                Tasks.later(k, () -> {
                    if (target.isAlive()) {
                        target.setDeltaMovement(v);
                        target.hurtMarked = true;
                    }
                });
            }
            SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6f, 1.3f);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.7f);
        }
        if (r >= RK_END) {
            SubNet.send(level, SubNet.CHIDORI, ball, caster.position(), Vec3.ZERO, 0, 1, 1);
            if (hold[0] != null) {
                hold[0].run();
            }
            SubCommon.pose(caster, "");
            return true;
        }
        return false;
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
                // 2.3 s held before the poke + 0.7 s after: 3 s, the ceiling of a SubAlma's control (rebalance 2026-11-20, it
                // was ~5.8 s).
                Stun.apply(target, JjCommon.control(caster, target, 14));
                PowerLogic.seal(target, JjCommon.control(caster, target, 14));
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
