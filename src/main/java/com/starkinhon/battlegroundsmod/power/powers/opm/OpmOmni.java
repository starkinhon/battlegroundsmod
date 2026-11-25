// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.opm;

import java.util.function.IntFunction;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// X in the Modo Serio: Socos Omnidirecionais Serios (Stark 2026-11-25, from the manga and the videos; forja/saitama-jogavel,
// "agora sim isso é uma alma divina"). 1) He punches the ground with everything: a crater of 18 and huge rocks fly up. Only
// whoever that slam catches (9 blocks round it) goes up with them: caught, the rest of the move is theirs; nobody caught,
// it was only the slam (Stark's rule). 2) The rocks stop in the air round the target, up to 62 blocks high. 3) He hops from
// rock to rock 72 times in 2.7 s: only beams of light cross the target, each one a blow. 4) So fast there are 50 of him, from
// every side, all punching at once: a sphere of pressure 70 across, the ground 62 below sinks in a ring and the trees fall.
// 5) The flash, the rocks burst out, the target over the horizon, and he comes back down on the same beam of light (no
// teleport). The client draws all of it from the messages (OpmFx); the server holds, hurts and digs on the same clock.
public final class OpmOmni {
    // Seconds: the slam, the first hop, one hop every OHD, the 50 punch, the flash.
    public static final double OS = 1.0, OH0 = 2.2, OHD = 0.038, OR = 5.75, OF = 6.25;
    public static final int OHN = 72;
    public static final double OHF = OH0 + OHN * OHD;
    // The return: on the beam from the target down to where he started, from OF + BACK0 to OF + BACK1.
    public static final double BACK0 = 0.45, BACK1 = 0.6;
    // Blocks: how high the target goes, how far round the slam it is caught, the crater, the push of the slam.
    public static final double HIGH = 62, CATCH_R = 9, CRATER_R = 18, CRATER_D = 6, SLAM_R = 18;
    // Damage before the Serio's 1.5x: the hops' share (in 12 lumps: the Escudo de tier takes a flat part off each blow) and
    // the 50's punch. 300 in all, the Soco Serio's.
    private static final float HOPS = 180, FINAL = 120;
    private static final int LUMPS = 12;

    private OpmOmni() {
    }

    private static int at(double s) {
        return OpmSerious.at(s);
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        LivingEntity aimed = SubCommon.aimed(caster, 32);
        if (aimed == null) {
            aimed = OpmNormal.front(caster, 18);
        }
        if (aimed == null) {
            SubCommon.warn(caster, "opm.sem_alvo");
            return false;
        }
        LivingEntity want = aimed;
        OpmSerious.Firm guard = new OpmSerious.Firm(caster);
        OpmSerious.begin(caster, level, "opm.omni", at(OF) + 16, at(OS) + 2);
        int seed = OpmSerious.seed();
        Vec3 home = caster.position();
        Vec3 dir = SubCommon.flat(want.position().subtract(home));
        Freeze.face(caster, (float) (Math.atan2(-dir.x, dir.z) * 180 / Math.PI), caster.getXRot());
        // the slam lands a few blocks ahead of him, between him and the target
        double ahead = Math.min(3.3, Math.max(1.5, SubCommon.flat(want.position().subtract(home)).length() * 0.5));
        Vec3 slam = SubCommon.ground(level, home.add(dir.scale(ahead)));
        boolean breaks = BlockRules.canBreak(level);
        SubNet.send(level, SubNet.OPM_OMNI, caster.getId(), slam, dir, seed, -1, 0);
        Runnable[] holdSelf = {Freeze.hold(caster, at(OS) + 2)};
        Runnable[] holdTarget = {null};
        LivingEntity[] caught = {null};
        Vec3[] oc = {null}, hide = {null};
        OpmSerious.Scan trees = breaks ? new OpmSerious.Scan(level, OpmSerious.disc(slam, OpmShape.DOME_R)) : null;
        int[] lumps = {0};
        Tasks.add(age -> {
            LivingEntity t = caught[0];
            boolean cut = guard.broken() || t != null && !t.isAlive() && age < at(OF);
            if (cut) {
                if (holdTarget[0] != null) {
                    holdTarget[0].run();
                }
                holdSelf[0].run();
                caster.removeEffect(MobEffects.INVISIBILITY);
                if (caster.isAlive() && oc[0] != null) {
                    caster.teleportTo(home.x, home.y, home.z);
                }
                SubNet.send(level, SubNet.OPM_OMNI, caster.getId(), slam, dir, seed, t == null ? -1 : t.getId(), 2);
                return true;
            }
            if (age < at(OS)) {
                return false;
            }
            if (age == at(OS)) {
                return slam(level, caster, want, slam, dir, seed, breaks, holdSelf, holdTarget, caught, oc, hide);
            }
            // the hops: every beam through the target is a blow, the damage in lumps
            if (age >= at(OH0) && age <= at(OHF)) {
                int due = (int) Math.min(LUMPS, Math.floor((age - at(OH0)) / (double) (at(OHF) - at(OH0)) * LUMPS) + 1);
                while (lumps[0] < due) {
                    lumps[0]++;
                    OpmSerious.hit(caster, t, HOPS / LUMPS, 20, Vec3.ZERO, level);
                }
                if (age % 2 == 0) {
                    SubCommon.sound(level, oc[0], SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.4f + SubCommon.RANDOM.nextFloat() * 0.5f);
                }
                if (age % 3 == 0) {
                    SubCommon.sound(level, oc[0], SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.9f + SubCommon.RANDOM.nextFloat() * 0.4f);
                }
            }
            if (age == at(OHF) + 1) {
                SubCommon.sound(level, oc[0], SoundEvents.BEACON_POWER_SELECT, 2f, 0.5f);
            }
            if (age == at(OR)) {
                // the 50 punch at once: the sphere of pressure; at the ground below the ring sinks and the trees fall
                SubCommon.sound(level, oc[0], SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
                SubCommon.sound(level, oc[0], SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.8f);
                for (LivingEntity e : Combat.inRadius(level, oc[0], 30, caster)) {
                    if (e != t) {
                        Vec3 out = e.position().subtract(oc[0]).normalize();
                        OpmSerious.hit(caster, e, 40, 30, out.scale(1.6).add(0, 0.4, 0), level);
                    }
                }
            }
            if (age == at(OR) + 7) {
                SubCommon.sound(level, slam, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.4f);
                if (breaks) {
                    OpmSerious.sink(level, slam, new double[] {34, 2.4});
                }
                OpmSerious.dome(level, caster, slam, trees, t);
            }
            if (age == at(OF)) {
                holdTarget[0].run();
                SubCommon.sound(level, oc[0], SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.6f);
                SubCommon.sound(level, oc[0], SoundEvents.WARDEN_SONIC_BOOM, 3f, 1.2f);
                boolean boss = t instanceof SoulBoss b && b.isBoss();
                OpmSerious.hit(caster, t, FINAL, 40, dir.scale(boss ? 2.5 : 6).add(0, 0.5, 0), level);
                safeLanding(t, 240);
                // he comes back down on the beam of light, to where he stood
                holdSelf[0].run();
                Vec3 from = oc[0];
                IntFunction<Vec3> path = a -> {
                    double k = OpmShape.clamp((a / 20.0 - BACK0) / (BACK1 - BACK0), 0, 1);
                    return k <= 0 ? hide[0] : from.lerp(home, k * k);
                };
                holdSelf[0] = Freeze.hold(caster, at(BACK1) + 2, path);
                caster.getData(ModAttachments.POWER).noFallTicks = Math.max(caster.getData(ModAttachments.POWER).noFallTicks, 60);
            }
            if (age == at(OF + BACK1)) {
                caster.removeEffect(MobEffects.INVISIBILITY);
                SubCommon.sound(level, home, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.3f);
            }
            return age >= at(OF + BACK1) + 2;
        });
        return true;
    }

    // The slam: the crater, the push, and who is caught. Returns true when the move is over (nobody caught).
    private static boolean slam(ServerLevel level, LivingEntity caster, LivingEntity want, Vec3 slam, Vec3 dir, int seed, boolean breaks,
            Runnable[] holdSelf, Runnable[] holdTarget, LivingEntity[] caught, Vec3[] oc, Vec3[] hide) {
        SubCommon.sound(level, slam, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
        SubCommon.sound(level, slam, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.6f);
        // the one aimed at if the slam reaches it, else the nearest it reaches
        LivingEntity t = null;
        if (want.isAlive() && want.position().distanceTo(slam) <= CATCH_R + want.getBbWidth()) {
            t = want;
        } else {
            double best = CATCH_R;
            for (LivingEntity e : Combat.inRadius(level, slam, CATCH_R, caster)) {
                double d = e.position().distanceTo(slam);
                if (d < best) {
                    best = d;
                    t = e;
                }
            }
        }
        if (breaks) {
            OpmSerious.bowl(level, slam, CRATER_R, CRATER_D);
        }
        // whoever else the slam reaches is thrown out of it
        for (LivingEntity e : Combat.inRadius(level, slam, SLAM_R, caster)) {
            if (e != t) {
                Vec3 out = SubCommon.flat(e.position().subtract(slam));
                OpmSerious.hit(caster, e, 30, 30, out.scale(1.3).add(0, 0.9, 0), level);
            }
        }
        if (t == null) {
            // nobody went up: it was only the slam
            holdSelf[0].run();
            SubNet.send(level, SubNet.OPM_OMNI, caster.getId(), slam, dir, seed, -1, 1);
            return true;
        }
        caught[0] = t;
        // as high as the sky lets it (a roof, a cave: lower)
        Vec3 up0 = slam.add(0, 1.5, 0);
        HitResult hit = level.clip(new ClipContext(up0, up0.add(0, HIGH, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        double h = hit.getType() == HitResult.Type.MISS ? HIGH : Math.max(4, hit.getLocation().y - slam.y - 4);
        h = Math.min(h, level.getMaxBuildHeight() - 8 - slam.y);
        oc[0] = slam.add(0, h, 0);
        SubNet.send(level, SubNet.OPM_OMNI, caster.getId(), slam, new Vec3(dir.x, h, dir.z), seed, t.getId(), 1);
        Vec3 start = t.position(), top = oc[0].add(0, -1, 0);
        int len = at(OF) - at(OS) + 1;
        holdTarget[0] = Freeze.hold(t, len, a -> {
            double k = 1 - Math.pow(1 - Math.min(1, a / 30.0), 4);
            return start.lerp(top, k);
        });
        Stun.apply(t, len, true);
        // he is gone in the hops (only his afterimages and the beams show), kept beside the target out of the way
        hide[0] = oc[0].add(dir.scale(-14)).add(0, 3, 0);
        holdSelf[0].run();
        holdSelf[0] = Freeze.hold(caster, len + 2, a -> a < 4 ? slam.subtract(dir.scale(0.8)) : hide[0]);
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, len + at(BACK1) + 4, 0, false, false));
        return false;
    }

    // Thrown from up there: no fall damage on the way down (the blow is the damage).
    private static void safeLanding(LivingEntity t, int ticks) {
        if (t.hasData(ModAttachments.POWER)) {
            t.getData(ModAttachments.POWER).noFallTicks = Math.max(t.getData(ModAttachments.POWER).noFallTicks, ticks);
        }
        Tasks.add(age -> {
            if (!t.isAlive() || t.isRemoved() || age > ticks) {
                return true;
            }
            t.fallDistance = 0;
            return age > 10 && t.onGround();
        });
    }
}
