package com.starkinhon.battlegroundsmod.power.powers.luffy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmSerious;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// The 20 moves (4 keys x 5 Gears), on the page's clocks (forja/luffy-gears: FIRE, runGatling, runBazooka, ULT). The
// client draws them from the one LUFFY_FIRE message (LuffyFx); here only what happens: when the blow lands, how hard,
// where the target goes, which ground breaks (with soulsBlockDestruction). The stretched arm reaches far (Stark's port
// rules: generous range), a strong blow stuns for real (25+ ticks after a boss halves it).
public final class LuffyMoves {
    // Z, X, C, V: how far each Gear reaches (the page's target distances, a bit more).
    private static final double[][] REACH = {{12, 16, 14, 18, 22}, {9, 10, 12, 12, 15}, {11, 13, 13, 14, 17}, {12, 14, 4, 18, 22}};

    private LuffyMoves() {
    }

    // ---------- the keys ----------

    public static boolean z(LivingEntity caster, ServerLevel level) {
        return Luffy.press(caster, level, 0);
    }

    public static boolean x(LivingEntity caster, ServerLevel level) {
        return Luffy.press(caster, level, 1);
    }

    public static boolean c(LivingEntity caster, ServerLevel level) {
        return Luffy.press(caster, level, 2);
    }

    public static boolean v(LivingEntity caster, ServerLevel level) {
        return Luffy.press(caster, level, 3);
    }

    public static boolean b(LivingEntity caster, ServerLevel level) {
        return Luffy.gear5(caster, level);
    }

    // ---------- shared ----------

    private static int at(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    // The one in front: the aimed target, or the nearest in a cone ahead.
    static LivingEntity front(LivingEntity caster, double range) {
        LivingEntity aimed = SubCommon.aimed(caster, range + 1.5);
        if (aimed != null) {
            return aimed;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        // The Giant reaches 10x as far, so a wide cone picked whatever stood anywhere ahead (Stark: "the moves went
        // where I wasn't aiming"). He takes the one closest to his crosshair, in a narrow cone.
        boolean giant = k(caster) > 1.5;
        for (LivingEntity e : Combat.inRadius((ServerLevel) caster.level(), eye, range + 1, caster)) {
            Vec3 to = Combat.center(e).subtract(eye);
            double d = to.length();
            double dot = to.normalize().dot(look);
            double score = giant ? 1 - dot : d;
            if (score < bestD && dot > (giant ? 0.97 : 0.8)) {
                best = e;
                bestD = score;
            }
        }
        return best;
    }

    private static Vec3 flatTo(LivingEntity caster, Vec3 p) {
        Vec3 d = p.subtract(caster.position());
        d = new Vec3(d.x, 0, d.z);
        return d.lengthSqr() < 1.0E-4 ? SubCommon.flat(caster.getLookAngle()) : d.normalize();
    }

    // The Giant (10x) reaches and breaks with his size, hits 3x as hard and throws twice as far.
    private static double k(LivingEntity caster) {
        return Luffy.size(caster);
    }

    static void launch(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 velocity) {
        boolean boss = target instanceof SoulBoss b && b.isBoss();
        Vec3 base = boss ? velocity.multiply(0.35, 0.5, 0.35) : velocity;
        Vec3 v = k(caster) > 1.5 ? base.scale(2) : base;
        target.setDeltaMovement(v);
        target.hurtMarked = true;
        Tasks.later(1, () -> {
            if (target.isAlive()) {
                target.setDeltaMovement(v);
                target.hurtMarked = true;
            }
        });
        SubNet.send(level, SubNet.OPM_LAUNCH, target.getId(), caster.position(), v, 0, 0, 0);
    }

    // The Conqueror's haki in his blows: 1 = G4 (red), 2 = G5 (red and yellow), set by the move that went off last.
    private static final java.util.Map<LivingEntity, Integer> HAKI = new java.util.WeakHashMap<>();
    // when each target last showed the haki (a gatling lands many times a second: one burst every 2 ticks)
    private static final java.util.Map<LivingEntity, Long> HAKI_SHOWN = new java.util.WeakHashMap<>();

    // Every punch of the kit (Stark 2026-11-12: in the same tier the Madara out-damaged him ~1.6x in 6 recorded fights, DPS
    // 2.8-3.1 against 4.1-5.3, with the movement already equal).
    private static final float DAMAGE_MULT = 1.4f;

    private static void hit(LivingEntity caster, LivingEntity target, float damage, int stun) {
        Combat.hurt(caster, target, (k(caster) > 1.5 ? damage * 3 : damage) * DAMAGE_MULT);
        if (stun > 0) {
            Stun.apply(target, stun);
        }
        int haki = HAKI.getOrDefault(caster, 0);
        if (haki > 0 && caster.level() instanceof ServerLevel level) {
            long t = level.getGameTime();
            Long last = HAKI_SHOWN.get(target);
            if (last == null || t - last >= 2) {
                HAKI_SHOWN.put(target, t);
                Vec3 c = Combat.center(target);
                Vec3 d = c.subtract(caster.getEyePosition());
                SubNet.send(level, SubNet.LUFFY_HAKI, target.getId(), c, d.lengthSqr() < 1e-6 ? caster.getLookAngle() : d.normalize(), haki, (float) k(caster), 0);
            }
        }
    }

    private static void dent(LivingEntity caster, ServerLevel level, Vec3 at, double r, double deep, int pieces, int rubber) {
        // the giant's holes are his size, up to 4x (more would stall the server breaking blocks)
        double g = Math.min(4, k(caster));
        r *= g;
        deep *= Math.min(3, g);
        pieces = (int) Math.min(900, pieces * Math.min(2.5, g));
        SubNet.send(level, SubNet.LUFFY_DENT, 0, at, Vec3.ZERO, (float) r, pieces, rubber);
        if (rubber == 0 && BlockRules.canBreak(level)) {
            OpmSerious.dent(level, SubCommon.ground(level, at), r, deep);
        }
    }

    private static void rubber(LivingEntity caster, ServerLevel level, Vec3 at, double strength) {
        // b = how much bigger the ring runs (the giant's)
        SubNet.send(level, SubNet.LUFFY_RUBBER, 0, SubCommon.ground(level, at), Vec3.ZERO, (float) strength, (float) Math.min(3, k(caster)), 0);
    }

    // Everyone around p (but the caster) bounces up as the rubber wave passes them (the page's dummies).
    private static void reverb(ServerLevel level, LivingEntity caster, Vec3 p, double radius, float damage, Set<LivingEntity> skip) {
        radius *= k(caster);
        for (LivingEntity e : Combat.inRadius(level, p, radius, caster)) {
            if (skip.contains(e)) {
                continue;
            }
            double r = Math.hypot(e.getX() - p.x, e.getZ() - p.z);
            Tasks.later(Math.max(0, (int) Math.round(r / 26 * 20)), () -> {
                if (!e.isAlive()) {
                    return;
                }
                Combat.hurt(caster, e, damage);
                Stun.apply(e, 20);
                launch(level, caster, e, new Vec3(0, Math.max(0.5, 0.9 - r * 0.01), 0));
            });
        }
    }

    private static void sound(ServerLevel level, Vec3 p, SoundEvent s, float v, float pitch) {
        SubCommon.sound(level, p, s, v, pitch);
    }

    // ---------- a move goes off ----------

    static boolean fire(LivingEntity caster, ServerLevel level, int key, int gear) {
        double reach = REACH[key][gear] * k(caster);
        LivingEntity target = key == 3 && gear == 2 ? null : front(caster, reach);
        Vec3 aim = target != null ? Combat.center(target) : caster.getEyePosition().add(caster.getLookAngle().scale(reach));
        Vec3 dir = flatTo(caster, aim);
        SubNet.send(level, SubNet.LUFFY_FIRE, caster.getId(), caster.position(), caster.getLookAngle(), key, gear, target == null ? -1 : target.getId());
        // only G4 (red) and G5 (red and yellow) carry the Conqueror's haki
        HAKI.put(caster, Luffy.g5(caster) || gear == 4 ? 2 : gear == 3 ? 1 : 0);
        String[] poses = {"luffy.pistol", "luffy.gatling", "luffy.bazooka", "luffy.v"};
        SubCommon.pose(caster, poses[key] + (gear + 1));
        SubCommon.Guard guard = SubCommon.guard(caster);
        switch (key) {
            case 0 -> pistol(caster, level, gear, target, aim, dir, guard);
            case 1 -> gatling(caster, level, gear, target, aim, dir, guard);
            case 2 -> bazooka(caster, level, gear, target, aim, dir, guard);
            default -> ult(caster, level, gear, target, aim, dir, guard);
        }
        if (key != 3) {
            // (the Gigant Pistol shrinks him when the page does: after the arm has gone down)
            int done = key == 1 ? at(new double[] {1.0, 0.9, 1.3, 1.1, 1.2}[gear]) + 4 : key == 0 && gear == 2 ? at(1.42) : 14;
            Tasks.later(done, () -> Luffy.after(caster, level, gear));
        } else if (gear == 3) {
            Tasks.later(14, () -> Luffy.after(caster, level, 3));
        }
        return true;
    }

    // Whoever is where the fist lands: the target, or anyone along the line up to the aim point.
    private static List<LivingEntity> struck(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 aim, double radius) {
        radius *= k(caster);
        if (target != null && target.isAlive()) {
            Vec3 c = Combat.center(target);
            List<LivingEntity> near = Combat.inRadius(level, c, radius, caster);
            if (!near.contains(target)) {
                near = new java.util.ArrayList<>(near);
                near.add(target);
            }
            return near;
        }
        return Combat.alongLine(level, caster.getEyePosition(), aim, Math.max(0.8, radius * 0.5), caster);
    }

    // ---------- Z: Pistol ----------

    private static void pistol(LivingEntity caster, ServerLevel level, int gear, LivingEntity target, Vec3 aim, Vec3 dir, SubCommon.Guard guard) {
        int[] hitAt = {at(0.12), 0, at(0.42), at(0.07), at(0.3)};
        SubCommon.busy(caster, hitAt[gear] + 6);
        Freeze.holdLookFree(caster, hitAt[gear] + 3);
        sound(level, caster.position(), gear == 1 ? SoundEvents.FIREWORK_ROCKET_LAUNCH : SoundEvents.FISHING_BOBBER_THROW, 1.2f, gear >= 2 ? 0.5f : 0.8f);
        Tasks.later(hitAt[gear], () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 p = target != null && target.isAlive() ? Combat.center(target) : aim;
            switch (gear) {
                case 0 -> {
                    for (LivingEntity e : struck(caster, level, target, aim, 0.9)) {
                        hit(caster, e, 12f, 12);
                        launch(level, caster, e, dir.scale(0.9).add(0, 0.3, 0));
                    }
                    sound(level, p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.9f);
                }
                case 1 -> {
                    for (LivingEntity e : struck(caster, level, target, aim, 1.2)) {
                        hit(caster, e, 22f, 22);
                        launch(level, caster, e, dir.scale(2.6).add(0, 0.5, 0));
                    }
                    sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
                }
                case 2 -> {
                    for (LivingEntity e : struck(caster, level, target, aim, 3.4)) {
                        hit(caster, e, e == target ? 40f : 24f, 46);
                        Freeze.hold(e, 18);
                    }
                    dent(caster, level, new Vec3(p.x, p.y, p.z), 3.4, 2.2, 380, 0);
                    sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
                }
                case 3 -> {
                    for (LivingEntity e : struck(caster, level, target, aim, 1.8)) {
                        hit(caster, e, 58f, 48);
                        launch(level, caster, e, dir.scale(5.5).add(0, 0.9, 0));
                    }
                    caster.setDeltaMovement(dir.scale(-0.55).add(0, 0.05, 0));
                    caster.hurtMarked = true;
                    Vec3 o = caster.position();
                    for (int i = 0; i < 6; i++) {
                        dent(caster, level, o.add(dir.scale(2 + i * 2.2)), 0.6 + i * 0.12 + 0.6, 1, i == 0 ? 260 : 0, 0);
                    }
                    sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.7f);
                }
                default -> {
                    Set<LivingEntity> hitSet = new HashSet<>();
                    for (LivingEntity e : struck(caster, level, target, aim, 2.4)) {
                        hit(caster, e, 72f, 52);
                        Freeze.hold(e, 18);
                        hitSet.add(e);
                        Tasks.later(18, () -> {
                            if (e.isAlive()) {
                                launch(level, caster, e, dir.scale(1.2).add(0, 1.3, 0));
                            }
                        });
                    }
                    rubber(caster, level, p, 1);
                    dent(caster, level, p, 6, 0, 300, 1);
                    reverb(level, caster, p, 14, 8f, hitSet);
                    sound(level, p, SoundEvents.SLIME_BLOCK_FALL, 2f, 0.5f);
                    sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.9f);
                }
            }
        });
    }

    // ---------- X: Gatling ----------

    private static void gatling(LivingEntity caster, ServerLevel level, int gear, LivingEntity target, Vec3 aim, Vec3 dir, SubCommon.Guard guard) {
        double[] dur = {1.0, 0.9, 1.3, 1.1, 1.2};
        int[] every = {2, 1, 2, 1, 1};
        float[] per = {1.4f, 1.1f, 2.6f, 1.6f, 1.8f}, last = {7f, 14f, 30f, 40f, 40f};
        int end = at(dur[gear]);
        SubCommon.busy(caster, end + 6);
        Freeze.holdLookFree(caster, end + 4);
        double reach = REACH[1][gear] * k(caster);
        // The small punches' damage lands in blows every 4 ticks (the Escudo de tier takes a flat part off every blow, so
        // a dozen 1-2 damage punches did nothing against a higher tier; armor also eats small blows more).
        java.util.Map<LivingEntity, Float> owed = new java.util.HashMap<>();
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            LivingEntity t = target != null && target.isAlive() ? target : null;
            if (age < end && age % 4 == 3 || age >= end) {
                owed.forEach((e, amount) -> {
                    if (e.isAlive()) {
                        hit(caster, e, amount, 0);
                    }
                });
                owed.clear();
            }
            if (age < end) {
                if (age % every[gear] == 0) {
                    List<LivingEntity> in = t != null ? struck(caster, level, t, aim, gear == 2 ? 2.2 : 1.4) : Combat.inCone(caster, reach, 0.85);
                    for (LivingEntity e : in) {
                        owed.merge(e, per[gear], Float::sum);
                        Stun.apply(e, 8);
                        // held in front of the fists (no knockback: they don't fall out of the barrage); G4 holds them up
                        double up = gear == 3 ? Math.min(0.12, 2.5 - (e.getY() - caster.getY()) * 0.05) : gear == 1 ? 0.04 : Math.min(0, e.getDeltaMovement().y);
                        e.setDeltaMovement(dir.scale(gear == 0 ? 0.04 : 0.02).add(0, up, 0));
                        e.hurtMarked = true;
                    }
                    if (gear == 2 && age % 4 == 0 && t != null) {
                        Vec3 c = t.position();
                        dent(caster, level, c.add(SubCommon.RANDOM.nextDouble() * 2 - 1, 0, SubCommon.RANDOM.nextDouble() * 2 - 1), 1.2 + SubCommon.RANDOM.nextDouble() * 0.8, 1,
                                26, 0);
                    }
                    if (gear == 4 && age % 3 == 0 && t != null) {
                        rubber(caster, level, t.position(), 0.55 + SubCommon.RANDOM.nextDouble() * 0.25);
                        dent(caster, level, t.position(), 4, 0, 14, 1);
                    }
                    sound(level, caster.getEyePosition().add(dir.scale(2)), gear == 2 ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.PLAYER_ATTACK_WEAK,
                            gear == 2 ? 0.6f : 0.5f, 1.1f + 0.3f * SubCommon.RANDOM.nextFloat());
                }
                return false;
            }
            // the last one
            List<LivingEntity> in = t != null ? struck(caster, level, t, aim, gear == 2 ? 3.6 : 1.6) : Combat.inCone(caster, reach, 0.8);
            Vec3 p = t != null ? t.position() : aim;
            Set<LivingEntity> hitSet = new HashSet<>(in);
            for (LivingEntity e : in) {
                hit(caster, e, last[gear], new int[] {14, 26, 46, 50, 52}[gear]);
                switch (gear) {
                    case 0 -> launch(level, caster, e, dir.scale(1.1).add(0, 0.3, 0));
                    case 1 -> launch(level, caster, e, dir.scale(2.4).add(0, 0.55, 0));
                    case 2 -> Freeze.hold(e, 16);
                    case 3 -> launch(level, caster, e, dir.scale(5.2).add(0, 1.0, 0));
                    default -> launch(level, caster, e, dir.scale(1.4).add(0, 1.25, 0));
                }
            }
            if (gear == 2) {
                dent(caster, level, p, 3.6, 2.2, 300, 0);
            }
            if (gear == 4) {
                rubber(caster, level, p, 1);
                reverb(level, caster, p, 12, 6f, hitSet);
            }
            sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1f + 0.2f * gear, 1.4f - 0.15f * gear);
            return true;
        });
    }

    // ---------- C: Bazooka ----------

    private static void bazooka(LivingEntity caster, ServerLevel level, int gear, LivingEntity target, Vec3 aim, Vec3 dir, SubCommon.Guard guard) {
        int[] hitAt = {at(0.32), at(0.14), at(0.67), at(0.5), at(0.46)};
        SubCommon.busy(caster, hitAt[gear] + 8);
        Freeze.holdLookFree(caster, hitAt[gear] + 4);
        sound(level, caster.position(), SoundEvents.FISHING_BOBBER_THROW, 1.2f, 0.6f);
        Tasks.later(hitAt[gear], () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 p = target != null && target.isAlive() ? Combat.center(target) : aim;
            Set<LivingEntity> hitSet = new HashSet<>();
            for (LivingEntity e : struck(caster, level, target, aim, gear >= 2 ? 2.2 : 1.2)) {
                hitSet.add(e);
                Defense.breakGuard(e, caster);
                switch (gear) {
                    case 0 -> {
                        hit(caster, e, 16f, 24);
                        launch(level, caster, e, dir.scale(1.5).add(0, 0.35, 0));
                    }
                    case 1 -> {
                        hit(caster, e, 26f, 30);
                        launch(level, caster, e, dir.scale(3.2).add(0, 0.5, 0));
                    }
                    case 2 -> {
                        hit(caster, e, 46f, 50);
                        launch(level, caster, e, dir.scale(2.0).add(0, 0.18, 0));
                    }
                    case 3 -> {
                        hit(caster, e, 66f, 54);
                        launch(level, caster, e, dir.scale(5.5).add(0, 0.9, 0));
                    }
                    default -> {
                        hit(caster, e, 76f, 56);
                        Freeze.hold(e, 10);
                        Tasks.later(10, () -> {
                            if (e.isAlive()) {
                                launch(level, caster, e, dir.scale(0.7).add(0, 1.05, 0));
                            }
                        });
                    }
                }
            }
            switch (gear) {
                case 2 -> {
                    Vec3 g = target != null ? target.position() : aim;
                    for (int i = 0; i < 6; i++) {
                        dent(caster, level, g.add(dir.scale(2 + i * 3.2)), 1 + 0.2 * i + 0.4, 1.4, i == 0 ? 220 : 0, 0);
                    }
                }
                case 3 -> {
                    caster.setDeltaMovement(dir.scale(-0.45).add(0, 0.05, 0));
                    caster.hurtMarked = true;
                    dent(caster, level, caster.position().add(dir.scale(4)), 1.6, 1, 240, 0);
                }
                case 4 -> {
                    Vec3 g = target != null ? target.position() : aim;
                    rubber(caster, level, g, 1.7);
                    dent(caster, level, g, 16, 0, 420, 1);
                    reverb(level, caster, g, 18, 10f, hitSet);
                }
                default -> {
                }
            }
            sound(level, p, gear == 4 ? SoundEvents.SLIME_BLOCK_FALL : SoundEvents.GENERIC_EXPLODE.value(), 1.2f + 0.2f * gear, gear == 4 ? 0.6f : 1.4f - 0.15f * gear);
        });
    }

    // ---------- V: each Gear's own big move ----------

    private static void ult(LivingEntity caster, ServerLevel level, int gear, LivingEntity target, Vec3 aim, Vec3 dir, SubCommon.Guard guard) {
        switch (gear) {
            case 0 -> rifle(caster, level, target, aim, dir, guard);
            case 1 -> mergulho(caster, level, target, aim, dir, guard);
            case 2 -> fusen(caster, level, guard);
            case 3 -> kingKong(caster, level, target, aim, dir, guard);
            default -> kaminari(caster, level, target, aim, dir, guard);
        }
    }

    // Rifle: the twisted arm unwinds like a drill.
    private static void rifle(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 aim, Vec3 dir, SubCommon.Guard guard) {
        SubCommon.busy(caster, 10);
        Freeze.holdLookFree(caster, 6);
        Tasks.later(at(0.13), () -> {
            if (guard.broken()) {
                return;
            }
            for (LivingEntity e : struck(caster, level, target, aim, 1.2)) {
                Defense.breakGuard(e, caster);
                hit(caster, e, 30f, 30);
                launch(level, caster, e, dir.scale(1.6).add(0, 0.4, 0));
            }
            sound(level, aim, SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.4f, 1.2f);
            sound(level, aim, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.8f);
        });
    }

    // Jet Mergulho: gone in a burst of air, upside down over the target, and a punch straight down into the ground.
    private static void mergulho(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 aim, Vec3 dir, SubCommon.Guard guard) {
        Vec3 ground = target != null ? target.position() : SubCommon.ground(level, aim);
        SubCommon.busy(caster, at(1.3));
        sound(level, caster.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 1.6f);
        if (target != null) {
            Freeze.hold(target, at(0.35));
        }
        int land = at(0.3), endT = at(1.25);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            Vec3 c = target != null && target.isAlive() ? target.position() : ground;
            if (age >= 1 && age < land) {
                double k = age < at(0.24) ? 0 : Math.pow((age - at(0.24)) / (double) (land - at(0.24)), 1.6);
                Freeze.place(caster, new Vec3(c.x, c.y + Mth.lerp(k, 8.2, 4.15) - 2, c.z - 0.2), caster.getYRot(), caster.getXRot());
                caster.setDeltaMovement(Vec3.ZERO);
                caster.fallDistance = 0;
            }
            if (age == land) {
                for (LivingEntity e : struck(caster, level, target, c.add(0, 0.5, 0), 2.6)) {
                    hit(caster, e, 55f, 56);
                    Freeze.hold(e, 20);
                }
                dent(caster, level, c, 3.4, 2.4, 340, 0);
                sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.8f);
            }
            if (age > land && age < endT) {
                // a flip back, onto his feet beside the crater
                double k = SubCommon.smooth((float) ((age - land - at(0.45)) / (double) at(0.5)));
                Vec3 to = c.subtract(dir.scale(4 * k));
                Freeze.place(caster, new Vec3(to.x, c.y + (1 - k) * 1.2 + Math.sin(k * Math.PI) * 3, to.z), caster.getYRot(), caster.getXRot());
                caster.setDeltaMovement(Vec3.ZERO);
                caster.fallDistance = 0;
            }
            return age >= endT;
        });
    }

    // Fusen: a giant balloon; whatever is thrown at him sinks into the rubber and goes back to its owner.
    static final int FUSEN_TICKS = 28;

    private static void fusen(LivingEntity caster, ServerLevel level, SubCommon.Guard guard) {
        SubCommon.busy(caster, FUSEN_TICKS);
        SubCommon.setState(caster, Luffy.FUSEN, FUSEN_TICKS);
        sound(level, caster.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 2f, 0.5f);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubCommon.clearState(caster, Luffy.FUSEN);
                return true;
            }
            caster.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 3, 3, false, false));
            Vec3 c = caster.position().add(0, 1.2, 0);
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(3.2))) {
                if (p.getOwner() == caster || p.getPersistentData().getBoolean("luffy_fusen")) {
                    continue;
                }
                p.getPersistentData().putBoolean("luffy_fusen", true);
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.scale(-2));
                p.hurtMarked = true;
                p.setOwner(caster);
                sound(level, p.position(), SoundEvents.SLIME_JUMP, 1.6f, 0.6f);
                SubNet.send(level, SubNet.LUFFY_DENT, 0, p.position(), v.normalize().scale(-1), 0, 0, 2);
            }
            if (age >= FUSEN_TICKS) {
                SubCommon.clearState(caster, Luffy.FUSEN);
                return true;
            }
            return false;
        });
    }

    // Blows taken in the balloon come back (LivingIncomingDamageEvent, before the defenses).
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!Luffy.is(target) || !SubCommon.hasState(target, Luffy.FUSEN) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        event.setCanceled(true);
        if (event.getSource().getEntity() instanceof LivingEntity enemy && enemy != target && enemy.isAlive()) {
            Vec3 d = enemy.position().subtract(target.position());
            Vec3 dir = new Vec3(d.x, 0, d.z).lengthSqr() < 1.0E-4 ? SubCommon.flat(target.getLookAngle()) : new Vec3(d.x, 0, d.z).normalize();
            Combat.hurt(target, enemy, Math.max(20f, event.getAmount() * 2));
            Stun.apply(enemy, 30);
            launch(level, target, enemy, dir.scale(2.4).add(0, 0.5, 0));
            SubNet.send(level, SubNet.LUFFY_DENT, 0, Combat.center(enemy), dir, 0, 0, 2);
            SubCommon.sound(level, target.position(), SoundEvents.SLIME_JUMP, 2f, 0.5f);
        }
    }

    // King Kong Gun: the fist almost twice the Kong Gun's, black with red lines; the target over the horizon.
    private static void kingKong(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 aim, Vec3 dir, SubCommon.Guard guard) {
        SubCommon.busy(caster, 12);
        Freeze.holdLookFree(caster, 6);
        Tasks.later(at(0.07), () -> {
            if (guard.broken()) {
                return;
            }
            for (LivingEntity e : struck(caster, level, target, aim, 3)) {
                Defense.breakGuard(e, caster);
                hit(caster, e, 120f, 60);
                launch(level, caster, e, dir.scale(7.5).add(0, 1.2, 0));
            }
            caster.setDeltaMovement(dir.scale(-0.7).add(0, 0.05, 0));
            caster.hurtMarked = true;
            Vec3 o = caster.position();
            for (int i = 0; i < 10; i++) {
                dent(caster, level, o.add(dir.scale(3 + i * 3)), 1 + i * 0.15 + 0.5, 1.6, i == 0 ? 420 : 0, 0);
            }
            sound(level, aim, SoundEvents.GENERIC_EXPLODE.value(), 2.4f, 0.5f);
            sound(level, aim, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
        });
    }

    // Kaminari: the arm up to the clouds, the lightning caught and turned to rubber, thrown like a spear.
    private static void kaminari(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 aim, Vec3 dir, SubCommon.Guard guard) {
        int hitT = at(1.13);
        SubCommon.busy(caster, hitT + 6);
        Freeze.holdLookFree(caster, hitT + 2);
        sound(level, caster.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 1f);
        Tasks.later(at(0.4), () -> sound(level, caster.position(), SoundEvents.SLIME_JUMP, 1.6f, 0.5f));
        Tasks.later(hitT, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 p = target != null && target.isAlive() ? target.position() : SubCommon.ground(level, aim);
            Set<LivingEntity> hitSet = new HashSet<>();
            for (LivingEntity e : struck(caster, level, target, aim, 2.4)) {
                hitSet.add(e);
                Defense.breakGuard(e, caster);
                hit(caster, e, 140f, 70);
                Freeze.hold(e, 16);
                Tasks.later(16, () -> {
                    if (e.isAlive()) {
                        launch(level, caster, e, dir.scale(1.1).add(0, 1.2, 0));
                    }
                });
            }
            // the branches of the bolt running over the ground
            for (LivingEntity e : Combat.inRadius(level, p, 8 * k(caster), caster)) {
                if (!hitSet.contains(e)) {
                    hit(caster, e, 30f, 30);
                }
            }
            rubber(caster, level, p, 1.3);
            dent(caster, level, p, 7, 0, 260, 1);
            sound(level, p, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.4f, 0.8f);
            sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 0.7f);
        });
    }
}
