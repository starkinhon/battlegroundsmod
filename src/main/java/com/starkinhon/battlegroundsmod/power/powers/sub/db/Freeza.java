package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Freeza (SubAlma of Dragon Ball, tier 6), the Visualizador's Freeza tab.
// Z Death Beam: a thin pink ray, instant, that pierces the target; held, a burst of 8 (some miss and hole the ground),
//   the last one pushes.
// X Telekinesis: the hand comes up and the target rises, stuck in the air; X again, a gesture slams it into the ground.
// C Death Saucer: a pink saw disc cuts the target and goes on, turns and comes back... and stops for no one: it cuts
//   Freeza too. C again blows it up before it comes back.
// V Death Ball: a little red ball on the raised finger grows into a red sun the size of a house; a flick and it comes
//   down slowly on the target: three blasts in a chain and a crater.
public final class Freeza {
    public static final String TELE = "db_freeza_tele";
    public static final String SAUCER = "db_freeza_saucer";
    // 1.5x of the first port (15 and 30 blocks).
    private static final double TELE_RANGE = 22.5;
    private static final double SAUCER_RANGE = 45;

    private Freeza() {
    }

    public static Power create() {
        return new Power("freeza", 0xC050FF, 1.0f, List.of(
                new Ability(70, 0, Freeza::deathBeam),
                new Ability(220, 0, Freeza::telekinesis),
                new Ability(260, 0, Freeza::saucer),
                new Ability(1100, 0, Freeza::deathBall)));
    }

    // ---------- Z: Death Beam ----------

    private static boolean deathBeam(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int bossShots = 1 + SubCommon.RANDOM.nextInt(8);
        SubCommon.pose(caster, "db.apontar");
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age < 2) {
                return false;
            }
            // Held: a burst of 8, one every 3 ticks; the last one pushes.
            int shot = (age - 2) / 3;
            boolean held = caster instanceof Player ? SubCommon.held(caster, age, 0) : shot < bossShots;
            if ((age - 2) % 3 == 0) {
                if (shot > 0 && !held) {
                    return true;
                }
                boolean last = shot >= 7 || (shot > 0 && !(caster instanceof Player) && shot + 1 >= bossShots);
                fire(caster, level, shot, last);
                if (last) {
                    return true;
                }
            }
            return false;
        });
        return true;
    }

    private static void fire(LivingEntity caster, ServerLevel level, int shot, boolean last) {
        Vec3 tip = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.2));
        Vec3 dir = caster.getLookAngle();
        // In the burst some miss: they go low and punch a hole in the ground behind.
        boolean miss = shot > 0 && !last && SubCommon.RANDOM.nextFloat() < 0.3f;
        if (shot > 0) {
            // Each one at a slightly new spot.
            double spread = miss ? 0.2 : 0.06;
            dir = dir.add((SubCommon.RANDOM.nextDouble() - 0.5) * spread, (SubCommon.RANDOM.nextDouble() - 0.5) * spread - (miss ? 0.15 : 0),
                    (SubCommon.RANDOM.nextDouble() - 0.5) * spread).normalize();
        }
        Vec3 end = DbCommon.beamEnd(caster, level, tip, dir, 40, 0.4, true);
        DbScar.along(caster, level, tip, end, 0.4);
        SubNet.send(level, SubNet.DEATH_BEAM, 0, tip, end.subtract(tip), 0, 0, 0);
        SubCommon.sound(level, tip, SoundEvents.TRIDENT_THROW.value(), 1.2f, 2f);
        SubCommon.sound(level, tip, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 2f);
        boolean hitSomeone = false;
        for (LivingEntity t : Combat.alongLine(level, tip, end, 0.45, caster)) {
            hitSomeone = true;
            Combat.hurtMagic(caster, t, 5.5f);
            DbCommon.hit(level, Combat.center(t), 0.5f, Db.PINK);
            if (last) {
                t.setDeltaMovement(t.getDeltaMovement().add(dir.x * 1.1, 0.35, dir.z * 1.1));
                t.hurtMarked = true;
            }
        }
        if (last && hitSomeone) {
            DbCommon.screen(level, end, 12);
        }
        if (!hitSomeone && end.distanceTo(tip) < 39.5 && SubCommon.solid(level, end.add(dir.scale(0.3)))) {
            JjCommon.crater(level, end, 0.6, 1.5, 3);
        }
    }

    // ---------- X: Telekinesis ----------

    private static boolean telekinesis(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, TELE)) {
            SubCommon.clearState(caster, TELE);
            return true;
        }
        LivingEntity target = SubCommon.aimed(caster, TELE_RANGE);
        if (target == null) {
            SubCommon.warn(caster, "freeza.tele_no_target", TELE_RANGE);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int most = 60;
        int bossHold = 20 + SubCommon.RANDOM.nextInt(30);
        int id = SubNet.newId();
        Vec3 start = target.position();
        SubCommon.setState(caster, TELE, most + 2);
        SubCommon.pose(caster, "db.mao_alto");
        SubCommon.sound(level, start, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.2f, 0.7f);
        // Up 3 blocks and stuck there: no walking, no skills.
        Freeze.hold(target, most + 12, a -> start.add(Math.sin(a * 0.05) * 0.1, 3 * SubCommon.smooth(a / 14f) + Math.sin(a * 0.1) * 0.08, 0));
        PowerLogic.seal(target, most + 12);
        int[] thrown = {-1};
        Vec3[] goal = {null};
        Tasks.add(age -> {
            if (thrown[0] < 0) {
                boolean let = !SubCommon.hasState(caster, TELE) || (!(caster instanceof Player) && age >= bossHold);
                if (guard.broken() || !target.isAlive() || age >= most) {
                    // Let go without the slam.
                    SubCommon.clearState(caster, TELE);
                    Freeze.free(target);
                    target.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).sealedUntil = 0;
                    SubNet.send(level, SubNet.TELEKINESIS, id, target.position(), DbCommon.hand(caster), 0, 0, 1);
                    DbCommon.cooldown(caster, 1, 220);
                    return true;
                }
                SubNet.send(level, SubNet.TELEKINESIS, target.getId(), Combat.center(target), DbCommon.hand(caster).add(0, 0.3, 0), 1, 0, 0);
                if (age % 10 == 0) {
                    SubCommon.sound(level, target.position(), SoundEvents.BEACON_AMBIENT, 1f, 1.6f);
                }
                if (!let) {
                    return false;
                }
                // X again: a gesture and it is thrown at the ground where he looks.
                thrown[0] = age;
                SubCommon.clearState(caster, TELE);
                SubCommon.pose(caster, "db.gesto_baixo");
                Vec3 to = target.position().add(SubCommon.flat(caster.getLookAngle()).scale(4));
                goal[0] = new Vec3(to.x, DbCommon.groundY(level, to.add(0, 2, 0)), to.z);
                Vec3 from = target.position();
                Freeze.free(target);
                Freeze.hold(target, 12, a -> {
                    double k = Math.min(1, a / 10.0);
                    Vec3 flat = from.lerp(goal[0], k);
                    return new Vec3(flat.x, goal[0].y + (from.y - goal[0].y) * (1 - k * k), flat.z);
                });
                SubCommon.sound(level, from, SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f, 0.6f);
                SubNet.send(level, SubNet.TELEKINESIS, target.getId(), Combat.center(target), DbCommon.hand(caster), 1, 0, 0);
                return false;
            }
            int t = age - thrown[0];
            if (t < 10) {
                SubNet.send(level, SubNet.TELEKINESIS, target.getId(), Combat.center(target), DbCommon.hand(caster), 1, 0, 0);
                return false;
            }
            SubNet.send(level, SubNet.TELEKINESIS, target.getId(), Combat.center(target), DbCommon.hand(caster), 0, 0, 1);
            target.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).sealedUntil = 0;
            if (target.isAlive()) {
                Combat.hurt(caster, target, 13);
                Stun.apply(target, 24);
            }
            DbCommon.hit(level, goal[0].add(0, 0.5, 0), 1.2f, Db.WHITE);
            DbCommon.screen(level, goal[0], 10);
            // The slam (style 1000+ in the client: the burst and the dust ring, no dome).
            SubNet.send(level, SubNet.KI_BOOM, 0, goal[0].add(0, 0.5, 0), new Vec3(goal[0].y, 0, 0), 1.4f, Db.PURPLE, 1000);
            SubCommon.sound(level, goal[0], SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
            SubCommon.sound(level, goal[0], SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.2f);
            JjCommon.crater(level, goal[0], 2, 1.4, 14);
            DbCommon.cooldown(caster, 1, 220);
            return true;
        });
        // The key stays free for the throw.
        DbCommon.cooldown(caster, 1, 6);
        return false;
    }

    // ---------- C: Death Saucer ----------

    private static boolean saucer(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, SAUCER)) {
            SubCommon.clearState(caster, SAUCER);
            return true;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int form = 24;
        int id = SubNet.newId();
        LivingEntity target = SubCommon.aimed(caster, SAUCER_RANGE);
        SubCommon.setState(caster, SAUCER, 200);
        SubCommon.pose(caster, "db.dedo_ceu");
        SubCommon.busy(caster, form);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
        Set<Integer> cutOut = DbCommon.once();
        Set<Integer> cutBack = DbCommon.once();
        List<Vec3> path = new ArrayList<>();
        Vec3[] p = {null};
        Vec3[] goal = {null};
        int[] phase = {0};
        int[] turnAt = {0};
        Vec3[] turnFrom = {null};
        Vec3[] turnDir = {null};
        Tasks.add(age -> {
            boolean popped = !SubCommon.hasState(caster, SAUCER) && age > form;
            if (age < form) {
                if (guard.broken()) {
                    SubCommon.clearState(caster, SAUCER);
                    SubNet.send(level, SubNet.SAW, id, DbCommon.raised(caster), new Vec3(0, 1, 0), 0, Db.PINK, 1);
                    DbCommon.cooldown(caster, 2, 130);
                    return true;
                }
                float r = 0.15f + 0.45f * SubCommon.smooth((age - 2) / 20f);
                SubNet.send(level, SubNet.SAW, id, DbCommon.raised(caster).add(0, 0.45, 0), new Vec3(0, 1, 0), r, Db.PINK, 0);
                if (age % 4 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, 0.8f, 1.6f);
                }
                return false;
            }
            if (age == form) {
                SubCommon.pose(caster, "db.arremesso");
                p[0] = DbCommon.raised(caster).add(0, 0.45, 0);
                goal[0] = target != null ? Combat.center(target) : SubCommon.aimPoint(caster, SAUCER_RANGE).add(0, 1, 0);
                SubCommon.sound(level, p[0], SoundEvents.TRIDENT_THROW.value(), 1.2f, 1.2f);
            }
            if (popped) {
                // C again: it bursts before coming back (no second cut).
                SubNet.send(level, SubNet.SAW, id, p[0], new Vec3(0, 1, 0), 0.6f, Db.PINK, 1);
                DbCommon.boom(level, p[0], 1.2f, Db.PINK, 21);
                DbCommon.hurtRadius(caster, level, p[0], 1.6, 6, 0.6, 0.2);
                DbCommon.cooldown(caster, 2, 260);
                return true;
            }
            int t = age - form;
            double speed = 0.55;
            Vec3 next;
            if (phase[0] == 0) {
                // Out: it follows the target and cuts it, then goes on past.
                if (target != null && target.isAlive() && !cutOut.contains(target.getId())) {
                    goal[0] = Combat.center(target);
                }
                Vec3 to = goal[0].subtract(p[0]);
                if (to.length() < speed && (target == null || cutOut.contains(target.getId()) || !target.isAlive())) {
                    phase[0] = 1;
                    turnAt[0] = t;
                    turnFrom[0] = p[0];
                    turnDir[0] = SubCommon.flat(p[0].subtract(path.isEmpty() ? caster.position() : path.get(0)));
                    next = p[0];
                } else {
                    Vec3 step = to.normalize().scale(speed);
                    next = p[0].add(step);
                    if (target != null && cutOut.contains(target.getId()) && t > 4) {
                        // Past the target: keep going a few blocks before turning.
                        goal[0] = goal[0].add(step.scale(0.6));
                        if (path.size() > 6 && next.distanceTo(Combat.center(target)) > 3) {
                            phase[0] = 1;
                            turnAt[0] = t;
                            turnFrom[0] = next;
                            turnDir[0] = SubCommon.flat(step);
                        }
                    }
                }
            } else if (phase[0] == 1) {
                // The turn: half a circle (radius 2.2), then back the way it came.
                float k = (t - turnAt[0]) / 18f;
                Vec3 side = turnDir[0].cross(new Vec3(0, 1, 0));
                double a = Math.min(1, k) * Math.PI;
                next = turnFrom[0].add(side.scale(1.1 - 1.1 * Math.cos(a))).add(turnDir[0].scale(1.1 * Math.sin(a)));
                if (k >= 1) {
                    phase[0] = 2;
                    turnDir[0] = turnDir[0].scale(-1);
                    SubCommon.sound(level, next, SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2f, 1.2f);
                }
            } else {
                // Back toward Freeza (wherever he is now), and on past him once it has cut him.
                Vec3 to = Combat.center(caster).subtract(p[0]);
                if (!cutBack.contains(caster.getId()) && to.lengthSqr() > 1.0E-4) {
                    turnDir[0] = to.normalize();
                }
                next = p[0].add(turnDir[0].scale(speed));
            }
            Vec3 prev = p[0];
            p[0] = next;
            path.add(next);
            if (path.size() > 40) {
                path.remove(0);
            }
            SubNet.send(level, SubNet.SAW, id, next, new Vec3(0, 1, 0), 0.6f, Db.PINK, 0);
            if (t % 5 == 0) {
                SubCommon.sound(level, next, SoundEvents.UI_STONECUTTER_TAKE_RESULT, 0.9f, 1.4f);
            }
            // Cuts whoever it passes: once each way. On the way back it cuts Freeza too.
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(prev, next).inflate(0.9),
                    e -> e.isAlive() && !e.isSpectator())) {
                boolean self = e == caster;
                if (!self && !Combat.isTarget(e, caster)) {
                    continue;
                }
                Set<Integer> done = phase[0] == 2 ? cutBack : cutOut;
                if (self && phase[0] != 2) {
                    continue;
                }
                if (!done.add(e.getId())) {
                    continue;
                }
                Vec3 c = Combat.center(e);
                if (self) {
                    e.invulnerableTime = 0;
                    e.hurt(level.damageSources().magic(), 14 * com.starkinhon.battlegroundsmod.power.Tiers.damageMultiplier(caster) * com.starkinhon.battlegroundsmod.combat.Combat.subScale(caster));
                    Stun.apply(e, 26);
                    DbCommon.screen(level, c, 13);
                } else {
                    Combat.hurtMagic(caster, e, 15);
                    Stun.apply(e, 14);
                    DbCommon.screen(level, c, 12);
                }
                SubNet.send(level, SubNet.SLASH, 0, c, next.subtract(prev).normalize().add(0, 0.25, 0), 0.75f, 0.12f, SubNet.STYLE_PINK);
                DbCommon.hit(level, c, 0.8f, Db.PINK);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.2f);
            }
            boolean gone = t > 240 || (phase[0] == 2 && cutBack.contains(caster.getId()) && next.distanceTo(Combat.center(caster)) > 6)
                    || SubCommon.solid(level, next);
            if (gone) {
                SubNet.send(level, SubNet.SAW, id, next, new Vec3(0, 1, 0), 0.6f, Db.PINK, 1);
                SubCommon.clearState(caster, SAUCER);
                DbCommon.cooldown(caster, 2, 260);
                return true;
            }
            return false;
        });
        DbCommon.cooldown(caster, 2, form + 4);
        return false;
    }

    // ---------- V: Death Ball ----------

    private static boolean deathBall(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int grow = 110;
        int fall = 56;
        SubCommon.pose(caster, "db.dedo_ceu_longo");
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
        Vec3[] base = {null};
        Vec3[] goal = {null};
        Tasks.add(age -> {
            if (age < grow) {
                if (guard.broken()) {
                    SubNet.send(level, SubNet.DEATH_BALL, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                    return true;
                }
                Freeze.holdLookFree(caster, 2);
                SubCommon.busy(caster, 3);
                float g = SubCommon.smooth((age - 4) / 100f);
                float r = 0.08f + 3.4f * g;
                SubNet.send(level, SubNet.DEATH_BALL, id, DbCommon.raised(caster).add(0, 0.15 + r, 0), Vec3.ZERO, r, 0, 0);
                if (age % 20 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.FIRE_AMBIENT, 2f, 0.4f + g * 0.3f);
                    SubCommon.sound(level, caster.position(), SoundEvents.BEACON_AMBIENT, 2f, 0.4f);
                }
                return false;
            }
            if (age == grow) {
                base[0] = DbCommon.raised(caster).add(0, 3.55, 0);
                goal[0] = SubCommon.aimPoint(caster, 50).add(0, 1.5, 0);
                SubCommon.pose(caster, "db.gesto");
                SubCommon.sound(level, base[0], SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.5f);
            }
            float k = Math.min(1, (age - grow) / (float) fall);
            Vec3 at = base[0].lerp(goal[0], SubCommon.smooth(k));
            boolean touch = k > 0.3f && !Combat.inRadius(level, at, 2.8, caster).isEmpty();
            if (k < 1 && !touch) {
                SubNet.send(level, SubNet.DEATH_BALL, id, at, Vec3.ZERO, 3.48f, 0, 0);
                if ((age - grow) % 10 == 0) {
                    SubCommon.sound(level, at, SoundEvents.FIRE_AMBIENT, 2.5f, 0.4f);
                }
                return false;
            }
            SubNet.send(level, SubNet.DEATH_BALL, id, at, Vec3.ZERO, 3.48f, 0, 1);
            double ground = DbCommon.groundY(level, at);
            Vec3 c = new Vec3(at.x, Math.min(at.y, ground + 1.5), at.z);
            SubNet.send(level, SubNet.SUPERNOVA, 0, c, new Vec3(ground, 0, 0), 1, 0, 0);
            DbCommon.screen(level, c, 11);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.4f);
            Tasks.later(5, () -> SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.6f));
            Tasks.later(9, () -> SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f));
            DbCommon.hurtRadius(caster, level, c, 9, 38, 1.8, 0.7);
            JjCommon.crater(level, new Vec3(c.x, ground, c.z), 6.5, 3.2, 60);
            return true;
        });
        return true;
    }
}
