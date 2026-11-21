// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.freezaboss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Freeza Poder Total's moves at the scale of the strongest boss of the mod (approved page forja/escala-fim-de-jogo,
// https://claude.ai/artifact/3W7E3frk5jNpm6AVxXzcyP, Stark 2026-11-19; before it his Death Ball left a crater of 5).
// Blocks and seconds of the page (20 ticks).
//   Shift+X  Death Ball (the one that blew up Namek): he rises, raises a finger and gathers a ball 18 blocks across, the
//            wind lifting dust under him; it comes down slowly (32 blocks/s), sinks into the ground for almost a second
//            (the ground glowing and cracking round it) and blows up from inside: a fire dome of 46, a crater of 36 and
//            15 deep with lava at the bottom, lava cracks running to 95, three tremors, a shock ring in the sky, a column
//            of smoke at the end. The answer: run from the moment the finger goes up (the damage falls off to the rim).
//   Shift+C  Corte do Planeta: he points and the Death Beam sweeps the ground from side to side in 0.9 s, opening a
//            canyon 84 long, 5 wide and 7 deep; half a second later it blows up in a chain from end to end (11 fire
//            blasts widening it to ~16 and 9 to 12 deep). The answer: the beam's sweep is the warning; out of the line.
//   Shift+B  Gesto dos Dois Dedos: two fingers up; the ground in a radius of 25 round the target cracks red-hot and shakes
//            for 1.5 s, then blows up: a column of fire 60 high that turns into a mushroom, blocks of 2-3 m flying 40
//            up, a crater of 25 and 14 deep. The cracks mark the exact area; the middle hurts the most.
// The Ilha Colossal (X) is FreezaTotal.island at this scale.
public final class FzEpic {
    private static final int PINK_TINT = 0xFFD0F4;

    private FzEpic() {
    }

    static void quake(ServerLevel level, Vec3 at, double s, boolean impact) {
        RuinNet.quake(level, at, s, impact, PINK_TINT);
    }

    // Holds him still in the air at p this tick (the Chuva de Ki's way).
    private static void hold(LivingEntity caster, Vec3 p) {
        caster.setNoGravity(true);
        caster.setDeltaMovement(Vec3.ZERO);
        caster.setPos(p.x, p.y, p.z);
        SubCommon.busy(caster, 3);
    }

    private static double ease(double x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - Math.pow(1 - x, 3);
    }

    // ================= Shift+X: Death Ball =================
    static final double RB = 9;

    static boolean deathBall(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 80);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 start = caster.position();
        Vec3 hover = start.add(0, 20, 0);
        int ball = SubNet.newId();
        FreezaTotal.armor(caster, 120);
        SubCommon.sound(level, start, SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
        Vec3[] bp = {null};
        Vec3[] from = {null};
        Vec3[] to = {null};
        Destructible.Attack[] clash = {null};
        Tasks.add(age -> {
            if (clash[0] != null && clash[0].gone()) {
                // Taken by a clash de poderes (it made the blast): nothing more here.
                caster.setNoGravity(false);
                return true;
            }
            if (guard.broken() || !caster.isAlive()) {
                caster.setNoGravity(false);
                SubNet.send(level, SubNet.DEATH_BALL, ball, bp[0] != null ? bp[0] : caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            // up into the sky
            if (age <= 16) {
                hold(caster, start.lerp(hover, ease(age / 16.0)));
                return false;
            }
            int t = age - 16;
            if (target.isAlive()) {
                caster.lookAt(EntityAnchorArgument.Anchor.EYES, Combat.center(target));
            }
            if (t < 48) {
                hold(caster, hover);
                if (t == 0) {
                    SubCommon.pose(caster, "db.dedo_ceu");
                    SubCommon.sound(level, hover, SoundEvents.WARDEN_SONIC_CHARGE, 3f, 0.4f);
                    Vec3 g = SubCommon.ground(level, start);
                    RuinNet.ring(level, g, 40, 3, false, 44, 0xB8A890);
                    RuinNet.dust(level, g, 16, 6, 80, 20, 0x9A8670);
                }
                // 2 s gathering: the ball grows over his finger
                double k = ease(t / 40.0), r = 0.3 + RB * k;
                bp[0] = DbCommon.raised(caster).add(0, r + 0.4, 0);
                SubNet.send(level, SubNet.DEATH_BALL, ball, bp[0], Vec3.ZERO, (float) r, 0, 0);
                if (t % 8 == 0) {
                    quake(level, bp[0], 0.25 * k + 0.1, false);
                    SubCommon.sound(level, bp[0], SoundEvents.FIRE_AMBIENT, 3f, 0.4f);
                }
                return false;
            }
            if (t == 48) {
                SubCommon.pose(caster, "db.gesto");
                from[0] = bp[0];
                to[0] = SubCommon.ground(level, target.isAlive() ? target.position() : start).add(0, RB * 0.6, 0);
                // Clash de poderes (Stark 2026-11-21): on its way down a beam or a ball can stop it, weight 2.2.
                clash[0] = Destructible.trackClash(level, caster, bp[0], RB,
                        () -> SubNet.send(level, SubNet.DEATH_BALL, ball, clash[0].pos(), Vec3.ZERO, (float) RB, 0, 1)).clash(2.2f, 0xFF4A30, 30f)
                        .clashDraw(p -> SubNet.send(level, SubNet.DEATH_BALL, ball, p, Vec3.ZERO, (float) RB, 0, 0));
                SubCommon.sound(level, bp[0], SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.5f);
            }
            hold(caster, hover);
            double L = Math.max(1, from[0].distanceTo(to[0]));
            int fly = Math.max(6, (int) Math.round(L / 32 * 20));
            int tt = t - 48;
            Vec3 g = new Vec3(to[0].x, to[0].y - RB * 0.6, to[0].z);
            if (tt <= fly) {
                bp[0] = from[0].lerp(to[0], tt / (double) fly);
                clash[0].move(bp[0]);
                if (tt == fly) {
                    clash[0].end();
                }
                SubNet.send(level, SubNet.DEATH_BALL, ball, bp[0], Vec3.ZERO, (float) RB, 0, 0);
                if (tt % 4 == 0) {
                    quake(level, bp[0], 0.3, false);
                }
                // whoever it runs into on the way down
                for (LivingEntity e : FreezaTotal.near(level, bp[0].add(0, -RB, 0), RB * 0.8, 0, RB * 2, caster)) {
                    if (e.distanceToSqr(bp[0]) < RB * RB) {
                        FreezaTotal.hurt(caster, e, 6);
                    }
                }
                if (tt == fly) {
                    // it touches down: the ground glows and cracks, it starts to sink
                    Ruin.bowl(level, g.x, g.z, 12, 9, 12, 0, Ruin.opts().scorch(1.2));
                    RuinNet.tremor(level, g, 0.8, 30, 40);
                    quake(level, g, 1.2, true);
                    FreezaTotal.mark(level, g, 30, 18, 6);
                }
                return false;
            }
            int sink = tt - fly;
            if (sink < 18) {
                bp[0] = new Vec3(to[0].x, to[0].y - RB * 1.6 * SubCommon.smooth(sink / 18f), to[0].z);
                SubNet.send(level, SubNet.DEATH_BALL, ball, bp[0], Vec3.ZERO, (float) RB, 0, 0);
                quake(level, g, 0.8 + 1.2 * sink / 18.0, false);
                return false;
            }
            SubNet.send(level, SubNet.DEATH_BALL, ball, bp[0], Vec3.ZERO, (float) RB, 0, 1);
            caster.setNoGravity(false);
            explode(caster, level, g);
            return true;
        }, () -> caster.setNoGravity(false));
        return true;
    }

    private static void explode(LivingEntity caster, ServerLevel level, Vec3 g) {
        RuinNet.boom(level, g, 46, 30, 120, 18, RuinNet.MODE_FIRE, RuinNet.SHAPE_DOME, 1.1, 0);
        Tasks.later(32, () -> RuinNet.boom(level, g.add(0, 4, 0), 22, 75, 160, 60, RuinNet.MODE_SMOKE, RuinNet.SHAPE_MUSHROOM, 0.7, 0x4A3F3A));
        Ruin.bowl(level, g.x, g.z, 36, 15, 18, 0, Ruin.opts().scorch(1.45).rim(0.3).lava(0.4).base(g.y));
        Ruin.fissures(level, g.x, g.z, 30, 95, 2.75, 5, 5, Ruin.opts().lava(1).width(0.09).seed(7));
        RuinNet.debris(level, g, 36, 40, 1, 260, 1.6);
        RuinNet.ring(level, g, 130, 7, false, 52, 0xB8A890);
        RuinNet.ring(level, g.add(0, 34, 0), 110, 4, true, 36, 0xFFE8D0);
        RuinNet.tremor(level, g, 2.4, 40, 110);
        Tasks.later(8, () -> RuinNet.tremor(level, g, 1.6, 30, 90));
        Tasks.later(16, () -> RuinNet.tremor(level, g, 1.1, 22, 70));
        quake(level, g, 3.2, true);
        SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.3f);
        SubCommon.sound(level, g, SoundEvents.WARDEN_SONIC_BOOM, 5f, 0.4f);
        DbCommon.boom(level, g.add(0, 0.5, 0), 8f, Db.ORANGE, FreezaTotal.RNG.nextInt(50));
        for (LivingEntity e : FreezaTotal.near(level, g, 46, 4, 40, caster)) {
            double d = Math.hypot(e.getX() - g.x, e.getZ() - g.z);
            float dmg = (float) (d < 12 ? 120 : Math.max(25, 120 - (d - 12) * 2.8));
            FreezaTotal.hurt(caster, e, dmg);
            Combat.pushFrom(e, g, 1.6, 1.4);
            FreezaTotal.stun(caster, e, 30);
            DbCommon.hit(level, Combat.center(e), 1.4f, Db.ORANGE);
        }
    }

    // ================= Shift+C: Corte do Planeta =================
    static boolean corte(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 60);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 f = FreezaTotal.aimDir(caster, target), side = new Vec3(-f.z, 0, f.x);
        Vec3 T = SubCommon.ground(level, target.position());
        // a line across the target, a little slanted (the page's A -> B)
        Vec3 A = SubCommon.ground(level, T.add(side.scale(-42)).add(f.scale(9))), B = SubCommon.ground(level, T.add(side.scale(42)).add(f.scale(-9)));
        Vec3 hover = caster.position().add(0, 8, 0);
        int beam = SubNet.newId();
        FreezaTotal.armor(caster, 40);
        SubCommon.pose(caster, "db.apontar");
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 2f, 1.6f);
        Set<Integer> hit = new HashSet<>();
        List<double[]> chain = new ArrayList<>();
        Tasks.add(age -> {
            if (guard.broken() || !caster.isAlive()) {
                caster.setNoGravity(false);
                SubNet.send(level, SubNet.KI_BEAM, beam, caster.position(), Vec3.ZERO, 0, Db.PINK, 1);
                return true;
            }
            if (age <= 9) {
                hold(caster, caster.position().lerp(hover, 0.3));
                caster.lookAt(EntityAnchorArgument.Anchor.EYES, A);
                return false;
            }
            int t = age - 9;
            hold(caster, caster.position());
            if (t == 0) {
                Ruin.trench(level, A, B, 2.6, 7, 18, 0, Ruin.opts().lip(2).seed(5).scorch(1));
                RuinNet.tremor(level, A, 0.5, 60, 90);
                SubCommon.sound(level, A, SoundEvents.WARDEN_SONIC_BOOM, 3f, 1.2f);
            }
            if (t <= 18) {
                Vec3 P = SubCommon.ground(level, A.lerp(B, t / 18.0));
                Vec3 tip = DbCommon.hand(caster);
                caster.lookAt(EntityAnchorArgument.Anchor.EYES, P);
                SubNet.send(level, SubNet.KI_BEAM, beam, tip, P.subtract(tip), 1.1f, Db.PINK, t == 18 ? 1 : 0);
                RuinNet.debris(level, P, 2.5, 18, 0.9, 6, 0.9);
                if (t % 3 == 0) {
                    quake(level, P, 0.6, false);
                    SubCommon.sound(level, P, SoundEvents.GENERIC_EXPLODE.value(), 2f, 1.4f);
                }
                for (LivingEntity e : Combat.alongLine(level, tip, P, 1.4, caster)) {
                    if (hit.add(e.getId())) {
                        FreezaTotal.hurt(caster, e, 45);
                        Combat.push(e, side, 0.8, 0.6);
                        FreezaTotal.stun(caster, e, 25);
                        quake(level, Combat.center(e), 1.2, true);
                    }
                }
                for (LivingEntity e : FreezaTotal.near(level, P, 2.8, 2, 4, caster)) {
                    if (hit.add(e.getId())) {
                        FreezaTotal.hurt(caster, e, 45);
                        FreezaTotal.stun(caster, e, 25);
                    }
                }
                if (t == 18) {
                    SubCommon.pose(caster, "db.gesto");
                    for (int i = 0; i < 11; i++) {
                        double u = i / 10.0;
                        chain.add(new double[] {t + 9 + u * 15 + (FreezaTotal.RNG.nextDouble() - 0.5), u, i == 10 ? 1 : 0});
                    }
                }
                return false;
            }
            hold(caster, caster.position());
            // the chain: from end to end, each a fire blast widening the canyon
            for (int i = 0; i < chain.size(); i++) {
                double[] c = chain.get(i);
                if (c[0] > t) {
                    continue;
                }
                chain.remove(i--);
                boolean big = c[2] > 0;
                Vec3 p = SubCommon.ground(level, A.lerp(B, c[1]));
                double R = big ? 14 : 8 + FreezaTotal.RNG.nextDouble() * 2;
                RuinNet.boom(level, p, R, R * 0.75, 64, 7, RuinNet.MODE_FIRE, RuinNet.SHAPE_DOME, 1, 0);
                Ruin.bowl(level, p.x, p.z, R * 0.85, big ? 12 : 9, 5, 0, Ruin.opts().scorch(1.3));
                RuinNet.debris(level, p, R * 0.85, 28, 0.9, 30, 1.4);
                if (i % 3 == 0 || big) {
                    RuinNet.ring(level, p, R * 3.2, 2.5, false, 24, 0xB8A890);
                }
                quake(level, p, big ? 2.2 : 1.1, big);
                SubCommon.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), big ? 5f : 3f, big ? 0.4f : 0.6f);
                for (LivingEntity e : FreezaTotal.near(level, p, R + 2, 3, R, caster)) {
                    FreezaTotal.hurt(caster, e, big ? 70 : 45);
                    Combat.pushFrom(e, p, 1.0, 1.0);
                    FreezaTotal.stun(caster, e, 25);
                }
            }
            if (chain.isEmpty()) {
                caster.setNoGravity(false);
                return true;
            }
            return false;
        }, () -> caster.setNoGravity(false));
        return true;
    }

    // ================= Shift+B: Gesto dos Dois Dedos =================
    static final double DR = 25;

    static boolean dedos(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 60);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 C = SubCommon.ground(level, target.position());
        FreezaTotal.armor(caster, 44);
        SubCommon.pose(caster, "db.dedo_ceu");
        SubCommon.busy(caster, 12);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 2f, 0.6f);
        Tasks.add(age -> {
            if (guard.broken() || !caster.isAlive()) {
                return true;
            }
            if (age == 6) {
                // the area cracks red-hot: the exact reach of the blast
                Ruin.fissures(level, C.x, C.z, 0, DR, 3, 0, 1, Ruin.opts().lava(1).width(0.2).seed(11));
                FreezaTotal.mark(level, C, DR, 30, 6);
                RuinNet.tremor(level, C, 0.6, 25, DR + 6);
                SubCommon.sound(level, C, SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.4f);
            }
            if (age > 6 && age < 36) {
                double k = (age - 6) / 30.0;
                if (age % 3 == 0) {
                    quake(level, C, 0.4 + 0.6 * k, false);
                    RuinNet.debris(level, C, DR, 5, 0.3, 4, 0.6);
                }
                return false;
            }
            if (age < 36) {
                return false;
            }
            RuinNet.boom(level, C, 18, 64, 120, 22, RuinNet.MODE_FIRE, RuinNet.SHAPE_MUSHROOM, 1.1, 0);
            RuinNet.dust(level, C, 32, 14, 100, 10, 0x8A7564);
            Ruin.bowl(level, C.x, C.z, DR, 14, 6, 0, Ruin.opts().scorch(1.3).rim(0.35));
            RuinNet.debris(level, C, DR * 0.9, 40, 0.6, 160, 2.6);
            RuinNet.ring(level, C, 90, 5, false, 40, 0xB8A890);
            RuinNet.ring(level, C.add(0, 40, 0), 60, 3, true, 28, 0xFFE8D0);
            RuinNet.tremor(level, C, 2, 32, 70);
            quake(level, C, 2.8, true);
            SubCommon.sound(level, C, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.35f);
            SubCommon.pose(caster, "db.gesto");
            for (LivingEntity e : FreezaTotal.near(level, C, DR, 3, 30, caster)) {
                double d = Math.hypot(e.getX() - C.x, e.getZ() - C.z);
                FreezaTotal.hurt(caster, e, d < 10 ? 90 : 35);
                Combat.push(e, SubCommon.flat(e.position().subtract(C)).lengthSqr() > 1.0E-4 ? SubCommon.flat(e.position().subtract(C)).normalize() : Vec3.ZERO,
                        0.5, d < 10 ? 2.0 : 1.2);
                FreezaTotal.stun(caster, e, d < 10 ? 35 : 25);
                DbCommon.hit(level, Combat.center(e), 1.2f, Db.ORANGE);
            }
            return true;
        });
        return true;
    }
}
