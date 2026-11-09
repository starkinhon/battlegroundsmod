package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// Goku (SubAlma of Dragon Ball, tier 6), the Visualizador's Goku tab.
// Z Kamehameha: held, the blue ball grows between the cupped hands; let go, the beam comes out, and held again it
//   grows thicker (0.6 to 1.8), pushes the target and blows up at the end. With the Kaioken on it comes out red and 2x.
// X Instant Transmission: instant, he vanishes and appears behind the aimed target with a heavy punch that stuns.
// C Kaioken: a buff (faster, harder punches, the life drains); C again: a red blur of four hits and a kick that launches.
// V Genki Dama: hands to the sky, the light of everything gathers in a glass ball over him (held up to 60 s; full = it
//   can kill in one hit); thrown, it is guided onto the target (or the crosshair) and blows up into a crater from about
//   7 blocks wide (2 s) to 52 (the full minute), the damage growing with it.
public final class Goku {
    private static final int TELE_RANGE = 30;
    private static final int KAIOKEN_TICKS = 200;
    private static final int GENKI_FULL = 1200;
    private static final int GENKI_RANGE = 80;

    private Goku() {
    }

    public static Power create() {
        return new Power("goku", 0x5AB8FF, 1.1f, List.of(
                new Ability(160, 0, Goku::kamehameha),
                new Ability(140, 0, Goku::teleport),
                new Ability(400, 0, Goku::kaioken),
                new Ability(1200, 0, Goku::genki)));
    }

    // ---------- Z: Kamehameha ----------

    private static boolean kamehameha(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean kaioken = SubCommon.hasState(caster, DbCommon.KAIOKEN);
        int color = kaioken ? Db.RED : Db.KAME;
        float big = kaioken ? 2 : 1;
        int ball = SubNet.newId();
        int beam = SubNet.newId();
        int most = 60;
        int bossCharge = 20 + SubCommon.RANDOM.nextInt(40);
        int bossBeam = 20 + SubCommon.RANDOM.nextInt(40);
        SubCommon.pose(caster, "db.concha");
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.2, 0, 0), most + 10, color, 1);
        int[] fired = {-1};
        float[] charge = {0};
        Map<Integer, Integer> hitAt = new HashMap<>();
        Vec3[] lastEnd = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.KI_BALL, ball, DbCommon.hands(caster), Vec3.ZERO, 0, color, 1);
                SubNet.send(level, SubNet.KI_BEAM, beam, DbCommon.hands(caster), Vec3.ZERO, 0, color, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, color, 1);
                return true;
            }
            SubCommon.busy(caster, 3);
            Vec3 hm = DbCommon.hands(caster);
            if (fired[0] < 0) {
                // Ka... me... ha... me...
                float k = SubCommon.smooth(age / 50f);
                charge[0] = k;
                boolean held = SubCommon.held(caster, age, bossCharge);
                if (held) {
                    Charge.report(caster, age / (double) most);
                }
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 3, false, false));
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, (0.06f + 0.16f * k) * big, color + 16, 0);
                if (age % 12 == 0) {
                    SubCommon.sound(level, hm, SoundEvents.BEACON_AMBIENT, 1f, 0.8f + k);
                }
                if ((held || age < 10) && age < most) {
                    return false;
                }
                fired[0] = age;
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0, color, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, color, 1);
                SubCommon.pose(caster, "db.empurra");
                DbCommon.screen(level, hm, 45);
                SubCommon.sound(level, hm, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.3f);
                SubCommon.sound(level, hm, SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.7f);
            }
            int t = age - fired[0];
            boolean held = SubCommon.held(caster, age, fired[0] + bossBeam);
            // Held after letting go: the beam grows thicker (0.6 to 1.8).
            float thick = (0.6f + 1.2f * SubCommon.smooth((t - 4) / 54f)) * big;
            boolean end = t >= 64 || (t >= 16 && !held);
            Vec3 dir = caster.getLookAngle();
            double width = thick * 0.6 + 0.4;
            Vec3 reachEnd = DbCommon.beamEnd(caster, level, hm, dir, 40, width, false);
            Vec3 to = hm.add(reachEnd.subtract(hm).scale(Math.min(1, t / 5.0)));
            lastEnd[0] = to;
            SubNet.send(level, SubNet.KI_BEAM, beam, hm, to.subtract(hm), thick, color, end ? 1 : 0);
            float damage = (0.8f + 0.9f * charge[0]) * (float) Math.sqrt(thick / 0.6f) * (kaioken ? 1.5f : 1);
            DbCommon.hurtAlong(caster, level, hm, to, width, damage, age, 4, hitAt, dir.scale(0.35));
            if (t % 8 == 0) {
                SubCommon.sound(level, to, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.6f);
            }
            if (end) {
                // The blast at the end (Stark, 2026-11-09: a bit bigger), growing with how thick the beam got.
                float blast = (3.6f + 0.9f * thick) * (kaioken ? 1.4f : 1);
                DbCommon.boom(level, to, blast, color, 4);
                // The ground under the end is torn up: a bowl of blocks, some thrown out.
                double ground = DbCommon.groundY(level, to);
                if (to.y - ground < 4) {
                    JjCommon.crater(level, new Vec3(to.x, ground, to.z), (3.2 + 1.4 * thick) * (kaioken ? 1.3 : 1), 1.5 + thick * 0.7, 18);
                }
                DbCommon.hurtRadius(caster, level, to, blast, (6 + 8 * charge[0]) * (kaioken ? 1.5f : 1), 1.3, 0.45);
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- X: Instant Transmission ----------

    private static boolean teleport(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, TELE_RANGE);
        if (target == null) {
            SubCommon.warn(caster, "goku.tele_no_target", TELE_RANGE);
            return false;
        }
        // Instant (Stark, 2026-11-09): no wait with the fingers on the forehead. A flick of the fingers, he is gone the
        // same tick and appears behind the target, and the punch lands two ticks later: heavy, a real stun.
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "db.testa");
        SubCommon.busy(caster, 10);
        SubNet.send(level, SubNet.SPARKS, SubNet.newId(), DbCommon.forehead(caster), Vec3.ZERO, 0.25f, Db.GOLD_W, 1);
        Vec3 from = caster.position();
        Vec3 behind = target.position().subtract(SubCommon.flat(target.getLookAngle()).scale(1.2));
        if (SubCommon.solid(level, behind.add(0, 0.5, 0)) || SubCommon.solid(level, behind.add(0, 1.5, 0))) {
            behind = target.position().add(SubCommon.flat(target.position().subtract(from)).scale(1.2));
        }
        SubNet.send(level, SubNet.VANISH, 0, from.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.8f);
        Vec3 face = target.position().subtract(behind);
        float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
        if (caster instanceof ServerPlayer player) {
            player.teleportTo(level, behind.x, behind.y, behind.z, yaw, player.getXRot());
        } else {
            caster.teleportTo(behind.x, behind.y, behind.z);
            caster.setYRot(yaw);
        }
        SubNet.send(level, SubNet.VANISH, 0, behind.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
        SubCommon.pose(caster, "soco.forte_golpe");
        Tasks.later(2, () -> {
            if (guard.broken() || !target.isAlive() || !caster.isAlive()) {
                return;
            }
            Combat.hurt(caster, target, 14);
            // A real stun (over Stun.INTERRUPT_TICKS): it cuts off whatever the target was doing. Only this one, no hold
            // on top (one prison at a time).
            Stun.apply(target, 40);
            Combat.push(target, target.position().subtract(caster.position()), 1.5, 0.3);
            Vec3 c = Combat.center(target);
            DbCommon.hit(level, c, 1.6f, Db.KAME_W);
            DbCommon.boom(level, c, 1.6f, Db.KAME_W, 4);
            DbCommon.screen(level, c, 56);
            SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.8f, 0.6f);
            SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.7f);
        });
        return true;
    }

    // ---------- C: Kaioken ----------

    private static boolean kaioken(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, DbCommon.KAIOKEN)) {
            return rush(caster, level);
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.setState(caster, DbCommon.KAIOKEN, KAIOKEN_TICKS);
        SubCommon.pose(caster, "db.grito");
        SubCommon.busy(caster, 10);
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(1, 0, 0), KAIOKEN_TICKS, Db.RED, 1);
        DbCommon.screen(level, Combat.center(caster), 2);
        SubCommon.sound(level, caster.position(), SoundEvents.BLAZE_SHOOT, 1.4f, 0.6f);
        SubCommon.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 1.4f, 0.5f);
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, DbCommon.KAIOKEN);
            if (guard.broken() || !on) {
                SubCommon.clearState(caster, DbCommon.KAIOKEN);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.RED, 1);
                DbCommon.cooldown(caster, 2, 400);
                return true;
            }
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6, 1, false, false));
            // One heart a second; it never kills: at two hearts it switches off on its own.
            if (age > 0 && age % 20 == 0) {
                if (caster.getHealth() <= 4.5f) {
                    SubCommon.clearState(caster, DbCommon.KAIOKEN);
                    SubCommon.warn(caster, "goku.kaioken_limit");
                } else {
                    caster.setHealth(Math.max(4, caster.getHealth() - 2));
                }
            }
            if (age == 40 || age % 60 == 0) {
                SubCommon.sound(level, caster.position(), SoundEvents.FIRE_AMBIENT, 1f, 0.6f);
            }
            // Mobs and bosses use the rush too, once someone is close in front of them.
            if (!(caster instanceof net.minecraft.world.entity.player.Player) && age >= 50 && age % 10 == 0 && SubCommon.aimed(caster, 7) != null) {
                rush(caster, level);
            }
            return false;
        });
        // The key stays free for the rush: the cooldown starts when the Kaioken ends.
        DbCommon.cooldown(caster, 2, 10);
        return false;
    }

    // C again: four hits from different sides in a red blur, then a kick that launches; the buff ends.
    private static boolean rush(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 8);
        if (target == null) {
            for (LivingEntity t : Combat.inCone(caster, 5, 0.4)) {
                target = t;
                break;
            }
        }
        if (target == null) {
            SubCommon.warn(caster, "goku.rush_no_target");
            return false;
        }
        LivingEntity tgt = target;
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.busy(caster, 36);
        Stun.apply(tgt, 40);
        Vec3 c0 = tgt.position();
        Vec3 fwd = SubCommon.flat(c0.subtract(caster.position()));
        Vec3 side = fwd.cross(new Vec3(0, 1, 0));
        // The spots round the target, as on the page: left, right, behind, front-left, then in front for the kick.
        Vec3[] spots = {c0.add(side.scale(-0.9)).add(fwd.scale(0.3)), c0.add(side.scale(0.9)).add(fwd.scale(0.5)), c0.add(fwd.scale(1.1)),
                c0.add(side.scale(-0.7)).add(fwd.scale(-0.8)), c0.add(fwd.scale(-0.95))};
        int[] hits = {4, 10, 16, 22};
        Vec3[] prev = {caster.position().add(0, 1, 0)};
        for (int i = 0; i < 5; i++) {
            int k = i;
            int at = i < 4 ? hits[i] - 3 : 27;
            Tasks.later(at, () -> {
                if (guard.broken() || !tgt.isAlive()) {
                    return;
                }
                Vec3 spot = spots[k];
                if (SubCommon.solid(level, spot.add(0, 0.5, 0))) {
                    spot = caster.position();
                }
                Vec3 face = tgt.position().subtract(spot);
                float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
                if (caster instanceof ServerPlayer player) {
                    player.teleportTo(level, spot.x, spot.y, spot.z, yaw, player.getXRot());
                } else {
                    caster.teleportTo(spot.x, spot.y, spot.z);
                    caster.setYRot(yaw);
                }
                SubNet.send(level, SubNet.STREAK, 0, prev[0], spot.add(0, 1, 0).subtract(prev[0]), 0.9f, Db.RED, 4);
                prev[0] = spot.add(0, 1, 0);
                SubCommon.pose(caster, k % 2 == 0 ? "soco.forte_golpe" : "soco.barragem_final");
                SubCommon.sound(level, spot, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.6f);
            });
            Tasks.later(at + 3, () -> {
                if (!tgt.isAlive() || !caster.isAlive()) {
                    return;
                }
                if (k < 4) {
                    Combat.hurt(caster, tgt, 4);
                    Stun.apply(tgt, 12);
                    DbCommon.hit(level, Combat.center(tgt), 0.7f, Db.RED);
                    DbCommon.screen(level, Combat.center(tgt), 4);
                    SubCommon.sound(level, tgt.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 1.1f);
                } else {
                    Combat.hurt(caster, tgt, 8);
                    Combat.push(tgt, tgt.position().subtract(caster.position()), 1.6, 0.7);
                    DbCommon.hit(level, Combat.center(tgt), 1.2f, Db.RED);
                    DbCommon.screen(level, Combat.center(tgt), 3);
                    SubCommon.sound(level, tgt.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.6f);
                    SubCommon.clearState(caster, DbCommon.KAIOKEN);
                }
            });
        }
        return true;
    }

    // ---------- V: Genki Dama ----------

    private static boolean genki(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int least = 40;
        int bossHold = 140 + SubCommon.RANDOM.nextInt(60);
        Vec3 feet = caster.position();
        SubCommon.pose(caster, "db.ceu");
        SubCommon.sound(level, feet, SoundEvents.BEACON_ACTIVATE, 2f, 0.7f);
        int[] thrown = {-1};
        Vec3[] at = {null};
        Vec3[] vel = {Vec3.ZERO};
        LivingEntity[] locked = {null};
        float[] size = {0};
        Tasks.add(age -> {
            if (thrown[0] < 0) {
                if (guard.broken()) {
                    // Hit while gathering: it is lost (and half the wait comes back).
                    SubNet.send(level, SubNet.GENKI, id, caster.position(), feet, size[0], 0, 1);
                    SubCommon.sound(level, caster.position(), SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.8f);
                    return true;
                }
                Freeze.holdLookFree(caster, 2);
                SubCommon.busy(caster, 3);
                if (age > 0 && age % 110 == 0) {
                    // Held for up to a minute: the arms stay up.
                    SubCommon.pose(caster, "db.ceu");
                }
                float grow = SubCommon.smooth((age - 10) / 130f);
                // After the first seconds it keeps swelling slowly the whole minute (the blast grows far more).
                float more = (float) Math.pow(Math.min(1, age / (float) GENKI_FULL), 0.8);
                float r = 0.2f + 3.8f * grow + 3f * more;
                size[0] = r;
                Vec3 top = caster.position().add(0, 3.2 + 4 * grow + 3 * more, 0);
                boolean held = SubCommon.held(caster, age, bossHold);
                if (held) {
                    Charge.report(caster, age / (double) GENKI_FULL);
                }
                if (age % 20 == 0) {
                    SubCommon.sound(level, top, SoundEvents.BEACON_AMBIENT, 2f, 0.6f + grow * 0.6f);
                }
                if (age == GENKI_FULL) {
                    SubCommon.sound(level, top, SoundEvents.TOTEM_USE, 1.4f, 1.4f);
                }
                if ((held || age < least) && age < GENKI_FULL + 200) {
                    SubNet.send(level, SubNet.GENKI, id, top, caster.position(), r, 1, 0);
                    return false;
                }
                thrown[0] = age;
                at[0] = top;
                // Locked on whoever is aimed at (or the nearest one in front; a boss takes its own target). Nobody: it
                // follows the crosshair the whole way.
                LivingEntity target = SubCommon.aimed(caster, GENKI_RANGE);
                if (target == null) {
                    double best = Double.MAX_VALUE;
                    for (LivingEntity e : Combat.inCone(caster, GENKI_RANGE, 0.93)) {
                        double d = e.distanceToSqr(caster);
                        if (d < best) {
                            best = d;
                            target = e;
                        }
                    }
                }
                if (target == null && caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
                    target = mob.getTarget();
                }
                locked[0] = target;
                vel[0] = genkiGoal(caster, locked[0]).subtract(top).normalize().scale(0.3);
                SubCommon.pose(caster, "db.empurra");
                SubCommon.sound(level, top, SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.8f);
            }
            int t = age - thrown[0];
            // Guided (Stark, 2026-11-09): it turns towards the target every tick, starting slow and speeding up.
            if (locked[0] != null && !locked[0].isAlive()) {
                locked[0] = null;
            }
            Vec3 goal = genkiGoal(caster, locked[0]);
            double speed = 0.3 + 1.0 * SubCommon.smooth(t / 30f);
            Vec3 want = goal.subtract(at[0]);
            if (want.lengthSqr() > 1.0E-4) {
                vel[0] = vel[0].lerp(want.normalize().scale(speed), 0.22);
            }
            Vec3 next = at[0].add(vel[0]);
            float r = size[0];
            // It lands on whoever it touches, on the ground or a wall in its way, or after 12 s in the air.
            boolean touch = !Combat.inRadius(level, next, r * 0.8 + 0.6, caster).isEmpty();
            Vec3 ahead = next.add(vel[0].lengthSqr() > 1.0E-4 ? vel[0].normalize().scale(r * 0.7) : Vec3.ZERO);
            boolean wall = SubCommon.solid(level, next) || SubCommon.solid(level, ahead);
            boolean arrived = locked[0] == null && caster instanceof Mob && next.distanceTo(goal) < r * 0.6 + 0.5;
            at[0] = next;
            if (!touch && !wall && !arrived && t < 240) {
                SubNet.send(level, SubNet.GENKI, id, next, caster.position(), r, 0, 0);
                if (t % 10 == 0) {
                    SubCommon.sound(level, next, SoundEvents.BEACON_AMBIENT, 2f, 0.5f);
                }
                return false;
            }
            SubNet.send(level, SubNet.GENKI, id, next, caster.position(), r, 0, 1);
            genkiBlast(caster, level, next, Math.min(1, thrown[0] / (float) GENKI_FULL), thrown[0] >= GENKI_FULL);
            return true;
        });
        return true;
    }

    // Where the guided ball heads: the locked target's body, or (nobody locked) the caster's crosshair.
    private static Vec3 genkiGoal(LivingEntity caster, LivingEntity target) {
        if (target != null) {
            return Combat.center(target);
        }
        return Combat.raycastPoint(caster, GENKI_RANGE);
    }

    // The landing (Stark, 2026-11-09: "the more it charges, the more blocks, the more damage"): the blast radius goes from
    // about 7 blocks (2 s) to 52 (the full minute), the damage with it, and the full one can kill in one hit. The dome
    // of the approved explosion is sized to the real radius, so what is seen is what is hit and destroyed.
    private static void genkiBlast(LivingEntity caster, ServerLevel level, Vec3 at, float charge, boolean full) {
        double radius = 3 + 49 * Math.pow(charge, 0.7);
        double ground = DbCommon.groundY(level, at);
        boolean low = at.y - ground < 6;
        Vec3 c = low ? new Vec3(at.x, Math.min(at.y, ground + 1.5), at.z) : at;
        SubNet.send(level, SubNet.GENKI_BOOM, 0, c, new Vec3(low ? ground : c.y - 1, 0, 0), (float) (radius / 7), 0, 0);
        DbCommon.screen(level, c, charge >= 0.25f ? 57 : 47);
        SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 4f + (float) radius * 0.1f, 0.5f);
        SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 3f + (float) radius * 0.08f, 0.6f);
        if (charge >= 0.25f) {
            SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f + (float) radius * 0.1f, 0.6f);
        }
        float dmg0 = (float) (15 + 85 * Math.pow(charge, 0.8));
        for (LivingEntity e : Combat.inRadius(level, c, radius, caster)) {
            double kk = 1 - 0.6 * Math.min(1, Combat.center(e).distanceTo(c) / radius);
            float dmg = (float) (dmg0 * kk);
            if (full) {
                // Full: one hit can kill, even someone stronger.
                dmg = Math.max(dmg, e.getMaxHealth() * 4);
            }
            Combat.hurtMagic(caster, e, dmg);
            Combat.pushFrom(e, c, (1.0 + 1.6 * charge) * kk, 0.6);
        }
        // The crater only when it lands on (or near) the ground; blown up high in the air it leaves the ground alone.
        if (low || at.y - ground < radius * 0.5) {
            DbCommon.bigCrater(level, new Vec3(c.x, ground, c.z), radius * 0.85, Math.max(2, radius * 0.35), (int) Math.max(4, radius / 2));
        }
    }
}
