package com.starkinhon.battlegroundsmod.power.powers.hashirama;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Hashirama Senju, the God of Shinobi: an end-game boss of the Vale do Fim (bosses-fim-de-jogo, Stark 2026-11-13). His
// power is boss only and never becomes a soul. Ported from the approved page (forja/hashirama-boss,
// https://claude.ai/artifact/SokNyJiRRCpd6c47QM3BKP): the server runs the page's timeline and hits, the client draws the
// page's wood (client/fx/hs) from HsNet events. Page units are 0.9 blocks; page seconds are 20 ticks.
//   Z  Nascimento da Floresta: a wave of trees bursts out of the ground towards the target and throws whoever it meets.
//   X  Estacas Perseguidoras: a front of stakes runs under the ground after the target (12 u/s, turning 1.7 rad/s); the
//      ground swells 0.32 s before each fan of stakes; at the end a flower of 11 thick stakes.
//   C  Prisão dos Quatro Pilares: 0.9 s of a square crack, then four pillars and beams close round the target (3 s or 3 hits).
//   V  Dragão de Madeira: the ground cracks 0.75 s, the dragon bursts out, spirals up, roars, and hunts (turning slowly:
//      a last-second dash makes it stick its head in the ground); caught, it coils, drinks stamina and slams.
//   B  Lança Ramificada: the arm turns to wood (0.5 s), the lance opens into 6 branches that close where the target stood.
//   Shift+Z  Mundo das Árvores Floridas: nine giant buds round the target; open, their pollen fills a sleep bar; asleep,
//            a stake throws you. Running out while they are closed is the answer.
//   Shift+X  Cúpula de Madeira: the dome rises when someone closes in; hit, it creaks 0.4 s and throws 9 stakes at that
//            side; after 2.6 s it opens (the window).
//   Shift+V  Buda das Mil Mãos (HsBuddha): Sage Mode, the Buddha rises behind him, he stands on its head; the fan's arms
//            slap the ground (0.75 s shadows) and the Top Strike drops one giant palm (1.3 s, 6 u).
public final class Hashirama {
    public static final String ID = "hashirama";
    public static final int COLOR = 0x8A6440;
    static final double PU = HsNet.PU;
    static final RandomSource RNG = RandomSource.createThreadSafe();

    private Hashirama() {
    }

    public static Power create() {
        return new Power(ID, COLOR, 1.4f, List.of(
                new Ability(240, 0, Hashirama::forest),
                new Ability(150, 0, Hashirama::stakes),
                new Ability(300, 0, Hashirama::prison),
                new Ability(700, 0, Hashirama::dragon),
                new Ability(200, 0, Hashirama::lance)),
                Arrays.asList(new Ability(520, 0, Hashirama::flowers), new Ability(320, 0, Hashirama::dome), null, new Ability(1300, 0, HsBuddha::summon), null));
    }

    // ---------- the page's frame ----------

    static double yawOf(Vec3 d) {
        return Math.atan2(-d.x, -d.z);
    }

    // a page point (x, y, z) of the frame at o turned by yaw, in the world
    static Vec3 toWorld(Vec3 o, double yaw, double x, double y, double z) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        return o.add((x * c + z * s) * PU, y * PU, (-x * s + z * c) * PU);
    }

    static Vec3 aimDir(LivingEntity caster, LivingEntity target) {
        Vec3 d = SubCommon.flat(target != null ? target.position().subtract(caster.position()) : caster.getLookAngle());
        if (d.lengthSqr() < 1.0E-6) {
            d = SubCommon.flat(caster.getLookAngle());
        }
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    // Who stands in an upright cylinder at base (radius r and height h in blocks), the caster and the already hit left out.
    static List<LivingEntity> near(ServerLevel level, Vec3 base, double r, double below, double h, LivingEntity caster) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(base.x - r - 1, base.y - below, base.z - r - 1, base.x + r + 1, base.y + h, base.z + r + 1),
                e -> Combat.isTarget(e, caster))) {
            double dx = e.getX() - base.x, dz = e.getZ() - base.z, hw = e.getBbWidth() * 0.5;
            if (Math.sqrt(dx * dx + dz * dz) - hw <= r) {
                out.add(e);
            }
        }
        return out;
    }

    static int stun(LivingEntity caster, LivingEntity t, int ticks) {
        int n = JjCommon.control(caster, t, ticks);
        // bosses cut stuns in half: never under 25 there (PORT JÁ JOGÁVEL)
        n = Math.max(n, JjCommon.isBoss(t) ? 50 : 25);
        Stun.apply(t, n);
        return n;
    }

    // Thrown: page velocity (blocks per page second x 0.9 / 20 a tick).
    static void throwPage(LivingEntity t, Vec3 dir, double horiz, double up) {
        Combat.push(t, dir, horiz * PU / 20, up * PU / 20);
    }

    static void splinters(ServerLevel level, Vec3 at, double pow, int n) {
        SubNet.send(level, HsNet.SPLINTERS, 0, at, new Vec3(0, pow * PU, 0), n, 0, 0);
    }

    static void dust(ServerLevel level, Vec3 at, float size) {
        SubNet.send(level, SubNet.DUST, 0, at, Vec3.ZERO, size, 0, 0);
    }

    static boolean held(LivingEntity e) {
        return Capture.trapped(e) || Freeze.isFrozen(e);
    }

    // ================= Z: Nascimento da Floresta =================
    static boolean forest(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 34);
        Vec3 dir = aimDir(caster, target);
        Vec3 o = SubCommon.ground(level, caster.position());
        double yaw = yawOf(dir);
        SubCommon.pose(caster, "nr.mao_chao");
        SubCommon.busy(caster, 14);
        SubNet.send(level, HsNet.FOREST, caster.getId(), o, dir, 0, 0, 0);
        SubCommon.sound(level, o, SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
        DbCommon.screen(level, o, 4);
        List<HsMeta.Tree> trees = HsMeta.trees();
        boolean[] grown = new boolean[trees.size()];
        Set<Integer> hit = new HashSet<>();
        Tasks.add(age -> {
            double t = age / 20.0;
            boolean left = false;
            for (int i = 0; i < trees.size(); i++) {
                HsMeta.Tree tr = trees.get(i);
                if (grown[i]) {
                    continue;
                }
                if (t < tr.t0()) {
                    left = true;
                    continue;
                }
                grown[i] = true;
                Vec3 base = SubCommon.ground(level, toWorld(o, yaw, tr.bx(), 0, tr.bz()));
                if (i % 2 == 0) {
                    JjCommon.crater(level, base, (0.6 + tr.r() * 0.6) * PU, 0.6, 2);
                }
                if (i % 3 == 0) {
                    dust(level, base, (float) (1.2 + tr.r()));
                    SubCommon.sound(level, base, SoundEvents.WOOD_BREAK, 1.6f, 0.5f + RNG.nextFloat() * 0.2f);
                }
                for (LivingEntity e : near(level, base, (tr.r() + 1.1) * PU, 1.5, tr.h() * PU, caster)) {
                    if (!hit.add(e.getId())) {
                        continue;
                    }
                    Combat.hurt(caster, e, 22);
                    throwPage(e, dir, 4, 15);
                    stun(caster, e, 25);
                    DbCommon.screen(level, Combat.center(e), 8);
                    splinters(level, Combat.center(e), 5, 8);
                }
            }
            return !left;
        });
        return true;
    }

    // ================= X: Estacas Perseguidoras =================
    static boolean stakes(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 32);
        Vec3 dir0 = aimDir(caster, target);
        SubCommon.pose(caster, "nr.mao_chao");
        SubCommon.busy(caster, 12);
        SubCommon.sound(level, caster.position(), SoundEvents.ROOTED_DIRT_PLACE, 1.6f, 0.5f);
        // 0.32 s seal + 0.14 s slap, then the front runs
        int start = 9;
        Vec3[] F = {null};
        Vec3[] D = {dir0};
        double[] trav = {0, 0};
        boolean[] ended = {false};
        Set<Integer> hit = new HashSet<>();
        List<double[]> queue = new ArrayList<>();
        List<Vec3[]> qpos = new ArrayList<>();
        Tasks.add(age -> {
            if (age < start) {
                return false;
            }
            if (age == start) {
                F[0] = SubCommon.ground(level, caster.position().add(dir0.scale(1.4 * PU)));
                dust(level, F[0], 2.5f);
                DbCommon.screen(level, F[0], 4);
            }
            double dt = 0.05;
            if (!ended[0]) {
                Vec3 to = target != null && target.isAlive() ? SubCommon.flat(target.position().subtract(F[0])) : Vec3.ZERO;
                if (to.lengthSqr() > 1.0E-4) {
                    double ang = Math.atan2(to.x, to.z) - Math.atan2(D[0].x, D[0].z), da = Math.atan2(Math.sin(ang), Math.cos(ang));
                    double turn = Mth.clamp(da, -1.7 * dt, 1.7 * dt);
                    D[0] = D[0].yRot((float) turn);
                }
                F[0] = SubCommon.ground(level, F[0].add(D[0].scale(12 * dt * PU)));
                trav[0] += 12 * dt;
                if (trav[0] >= trav[1]) {
                    trav[1] += 0.75;
                    JjCommon.crater(level, F[0], 0.5 * PU, 0.4, 1);
                    queue.add(new double[] {age + 6, 0.8 + trav[0] / 30, 0});
                    qpos.add(new Vec3[] {F[0], D[0]});
                }
                double ahead = target != null ? SubCommon.flat(target.position().subtract(F[0])).dot(D[0]) : -9;
                if (trav[0] > 24 || ahead < -2.5 * PU || !hit.isEmpty()) {
                    ended[0] = true;
                    queue.add(new double[] {age + 6, 1, 1});
                    qpos.add(new Vec3[] {F[0], D[0]});
                }
            }
            for (int i = 0; i < queue.size(); i++) {
                double[] q = queue.get(i);
                if (q[0] > age) {
                    continue;
                }
                Vec3 p = qpos.get(i)[0], d = qpos.get(i)[1];
                queue.remove(i);
                qpos.remove(i);
                i--;
                if (q[2] == 1) {
                    SubNet.send(level, HsNet.STAKES, 0, p, d, 1, 0, 1);
                    JjCommon.crater(level, p, 1.8 * PU, 1.2, 9);
                    dust(level, p, 5.5f);
                    DbCommon.screen(level, p, 10);
                    SubCommon.sound(level, p, SoundEvents.WOOD_BREAK, 2.4f, 0.4f);
                    for (LivingEntity e : near(level, p, 2.6 * PU, 1.5, 5, caster)) {
                        if (hit.add(e.getId())) {
                            Combat.hurt(caster, e, 26);
                            throwPage(e, d, 4, 15);
                            stun(caster, e, 30);
                        }
                    }
                } else {
                    float big = (float) q[1];
                    SubNet.send(level, HsNet.STAKES, 0, p, d, big, RNG.nextInt(100000), 0);
                    if (RNG.nextBoolean()) {
                        dust(level, p, 1.6f + big);
                    }
                    SubCommon.sound(level, p, SoundEvents.WOOD_PLACE, 1.4f, 0.6f + RNG.nextFloat() * 0.3f);
                    for (LivingEntity e : near(level, p, 1.4 * PU * Math.max(1, big), 1.5, 4, caster)) {
                        if (hit.add(e.getId())) {
                            Combat.hurt(caster, e, 18);
                            throwPage(e, d, 5, 14);
                            stun(caster, e, 25);
                            DbCommon.screen(level, Combat.center(e), 8);
                        }
                    }
                }
            }
            return ended[0] && queue.isEmpty();
        });
        return true;
    }

    // ================= C: Prisão dos Quatro Pilares =================
    static boolean prison(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 26);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        // clean combat: never a second prison on someone already held
        if (held(target)) {
            SubCommon.warn(caster, "hashirama.alvo_preso");
            return false;
        }
        Vec3 c = SubCommon.ground(level, target.position());
        int id = SubNet.newId();
        SubNet.send(level, HsNet.PRISON, id, c, aimDir(caster, target), 0, 0, 0);
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, 18);
        SubCommon.sound(level, c, SoundEvents.ROOTED_DIRT_BREAK, 1.6f, 0.6f);
        double half = 1.9 * PU + 0.35;
        int close = 18, last = close + 60;
        Runnable[] release = {null};
        Runnable[] breaker = {null};
        float[] hp = {0};
        int[] hits = {0};
        Tasks.add(age -> {
            if (age < close) {
                if (age % 6 == 0) {
                    DbCommon.screen(level, c, 4);
                }
                return false;
            }
            if (age == close) {
                JjCommon.crater(level, c, 1.2, 0.5, 4);
                SubCommon.sound(level, c, SoundEvents.WOOD_PLACE, 2f, 0.5f);
                dust(level, c, 3f);
                boolean in = target.isAlive() && Math.abs(target.getX() - c.x) < half && Math.abs(target.getZ() - c.z) < half && Math.abs(target.getY() - c.y) < 3.5
                        && !held(target);
                if (!in) {
                    SubNet.send(level, HsNet.PRISON, id, c, Vec3.ZERO, 2, 0, 0);
                    return true;
                }
                Vec3 at = new Vec3(c.x, Math.max(c.y, target.getY()), c.z);
                release[0] = Freeze.hold(target, 60, a -> at);
                breaker[0] = () -> {
                    if (release[0] != null) {
                        release[0].run();
                    }
                };
                Capture.trap(target, breaker[0]);
                Combat.hurt(caster, target, 8);
                hp[0] = target.getHealth();
                return false;
            }
            boolean done = !target.isAlive() || age >= last || !Capture.trapped(target);
            if (target.getHealth() < hp[0] - 0.01f) {
                hits[0]++;
            }
            hp[0] = target.getHealth();
            if (hits[0] >= 3) {
                done = true;
            }
            if (done) {
                Capture.untrap(target, breaker[0]);
                if (release[0] != null) {
                    release[0].run();
                }
                SubNet.send(level, HsNet.PRISON, id, c, Vec3.ZERO, hits[0] >= 3 ? 1 : 2, 0, 0);
                if (hits[0] >= 3) {
                    SubCommon.sound(level, c, SoundEvents.WOOD_BREAK, 2f, 0.6f);
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // ================= B: Lança Ramificada =================
    static boolean lance(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 26);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubNet.send(level, HsNet.LANCE, caster.getId(), Combat.handPos(caster), aimDir(caster, target), 0, 0, 0);
        SubCommon.pose(caster, "db.apontar");
        SubCommon.busy(caster, 20);
        SubCommon.sound(level, caster.position(), SoundEvents.WOOD_HIT, 1.4f, 0.5f);
        int fire = 11, stab = fire + 8;
        Vec3[] T0 = {null};
        Tasks.add(age -> {
            if (age < fire) {
                if (target.isAlive()) {
                    caster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Combat.center(target));
                }
                return false;
            }
            if (age == fire) {
                Vec3 H = Combat.handPos(caster);
                T0[0] = target.position().add(0, 1.1 * PU, 0);
                SubNet.send(level, HsNet.LANCE, caster.getId(), H, T0[0], 1, 0, 0);
                SubCommon.sound(level, H, SoundEvents.TRIDENT_THROW.value(), 1.6f, 0.5f);
                DbCommon.screen(level, H, 4);
                return false;
            }
            if (age < stab) {
                return false;
            }
            Vec3 g = SubCommon.ground(level, T0[0].subtract(0, 1.1 * PU, 0));
            List<LivingEntity> caught = near(level, g, 1.4 * PU, 1.5, 3.5, caster);
            if (!caught.isEmpty()) {
                for (LivingEntity e : caught) {
                    Combat.hurt(caster, e, 28);
                    if (!held(e)) {
                        Freeze.hold(e, JjCommon.control(caster, e, 24));
                    }
                    stun(caster, e, 25);
                    DbCommon.screen(level, Combat.center(e), 17);
                }
                SubCommon.sound(level, g, SoundEvents.TRIDENT_HIT, 2f, 0.6f);
            } else {
                JjCommon.crater(level, g, 1.5 * PU, 0.9, 7);
                dust(level, g, 4f);
                splinters(level, T0[0], 5, 8);
                DbCommon.screen(level, g, 4);
            }
            return true;
        });
        return true;
    }

    // ================= Shift+Z: Mundo das Árvores Floridas =================
    static boolean flowers(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 30);
        Vec3 dir = aimDir(caster, target);
        Vec3 C = SubCommon.ground(level, target != null ? target.position() : caster.position().add(dir.scale(12)));
        double RF = 7.5;
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, 10);
        int seal = 8;
        Map<UUID, float[]> sleep = new HashMap<>();
        Map<UUID, LivingEntity> who = new HashMap<>();
        Set<UUID> struck = new HashSet<>();
        Tasks.add(age -> {
            if (age < seal) {
                return false;
            }
            int a = age - seal;
            if (a == 0) {
                SubNet.send(level, HsNet.FLOWERS, caster.getId(), C, dir, 0, 0, 0);
                DbCommon.screen(level, C, 4);
                dust(level, C, 7);
                SubCommon.sound(level, C, SoundEvents.AZALEA_LEAVES_PLACE, 2f, 0.5f);
                Vec3 o = C;
                for (int i = 0; i < 9; i++) {
                    double an = i / 9.0 * 6.283, r = i == 0 ? 0 : 4.5;
                    JjCommon.crater(level, SubCommon.ground(level, o.add(Math.cos(an) * r * PU, 0, Math.sin(an) * r * PU)), 0.8 * PU, 0.6, 3);
                }
            }
            if (a == 26) {
                SubCommon.sound(level, C, SoundEvents.SPORE_BLOSSOM_PLACE, 2f, 0.6f);
            }
            double tp = a / 20.0 - 2.3;
            if (tp >= 0 && tp <= 6.5) {
                for (LivingEntity e : near(level, C, (RF + 4) * PU, 4, 10, caster)) {
                    who.put(e.getUUID(), e);
                    sleep.computeIfAbsent(e.getUUID(), k -> new float[] {0, -1});
                }
            }
            boolean any = false;
            for (Map.Entry<UUID, float[]> en : sleep.entrySet()) {
                LivingEntity e = who.get(en.getKey());
                float[] s = en.getValue();
                if (e == null || !e.isAlive()) {
                    continue;
                }
                double dist = Math.hypot(e.getX() - C.x, e.getZ() - C.z);
                boolean inside = dist < (RF + 0.5) * PU && Math.abs(e.getY() - C.y) < 8;
                float before = s[0];
                if (s[1] < 0) {
                    if (tp >= 0 && tp <= 6.5 && inside) {
                        s[0] = Math.min(1, s[0] + (float) (0.05 * 0.42 * SubCommon.smooth((float) (tp / 1.2))));
                    } else {
                        s[0] = Math.max(0, s[0] - 0.05f * 0.5f);
                    }
                    if (s[0] >= 1) {
                        // asleep: never on someone another trap holds already (they stay at the edge of sleep)
                        if (held(e)) {
                            s[0] = 0.99f;
                        } else {
                            s[1] = 0;
                            Freeze.hold(e, 24);
                            Stun.apply(e, Math.max(24, JjCommon.control(caster, e, 24)));
                        }
                    }
                } else {
                    s[1] += 0.05f;
                    if (s[1] >= 0.9f && struck.add(en.getKey())) {
                        Vec3 p = SubCommon.ground(level, e.position());
                        SubNet.send(level, HsNet.STAKES, 0, p, Vec3.ZERO, 1, 0, 2);
                        Combat.hurt(caster, e, 30);
                        throwPage(e, new Vec3(1, 0, 1), 1.4, 15);
                        stun(caster, e, 25);
                        JjCommon.crater(level, p, 1.4 * PU, 1, 8);
                        DbCommon.screen(level, p, 10);
                        SubCommon.sound(level, p, SoundEvents.WOOD_BREAK, 2f, 0.5f);
                    }
                    if (s[1] > 1.2f) {
                        s[0] = Math.max(0, s[0] - 0.05f);
                    }
                }
                if (e instanceof ServerPlayer sp && Math.abs(before - s[0]) > 1.0E-4) {
                    SubNet.sendTo(sp, HsNet.SLEEP, 0, e.position(), Vec3.ZERO, s[0], 0, 0);
                }
                any |= s[0] > 0;
            }
            boolean over = a / 20.0 > 2.3 + 6.5;
            if (over && !any || a > 20 * 14) {
                for (Map.Entry<UUID, LivingEntity> en : who.entrySet()) {
                    if (en.getValue() instanceof ServerPlayer sp) {
                        SubNet.sendTo(sp, HsNet.SLEEP, 0, sp.position(), Vec3.ZERO, 0, 0, 0);
                    }
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // ================= Shift+X: Cúpula de Madeira =================
    static final class Dome {
        final LivingEntity owner;
        final ServerLevel level;
        final Vec3 at;
        int creakAt = -1, counterAt = -1, age;
        Vec3 side = Vec3.ZERO;
        Runnable release;

        Dome(LivingEntity owner, ServerLevel level, Vec3 at) {
            this.owner = owner;
            this.level = level;
            this.at = at;
        }
    }

    static final Map<Integer, Dome> DOMES = new HashMap<>();

    public static boolean inDome(LivingEntity e) {
        return DOMES.containsKey(e.getId());
    }

    // A blow on the dome (HashiramaEntity.hurt): absorbed; the first one arms the counter on that side.
    public static boolean domeHit(LivingEntity owner, DamageSource source) {
        Dome d = DOMES.get(owner.getId());
        if (d == null) {
            return false;
        }
        if (d.creakAt < 0 && d.age >= 4) {
            Entity src = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
            Vec3 side = src != null ? SubCommon.flat(src.position().subtract(owner.position())) : Combat.flatLook(owner);
            d.side = side.lengthSqr() < 1.0E-4 ? Combat.flatLook(owner) : side.normalize();
            d.creakAt = d.age;
            SubNet.send(d.level, HsNet.DOME, owner.getId(), d.at, d.side, 1, 0, 0);
            splinters(d.level, owner.position().add(d.side.scale(1.9 * PU)).add(0, 1.2, 0), 3, 6);
            SubCommon.sound(d.level, d.at, SoundEvents.WOOD_HIT, 2f, 0.4f);
        }
        return true;
    }

    static boolean dome(LivingEntity caster, ServerLevel level) {
        if (DOMES.containsKey(caster.getId())) {
            return false;
        }
        Vec3 at = SubCommon.ground(level, caster.position());
        Dome d = new Dome(caster, level, at);
        DOMES.put(caster.getId(), d);
        SubNet.send(level, HsNet.DOME, caster.getId(), at, Combat.flatLook(caster), 0, 0, 0);
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, 52);
        d.release = Freeze.hold(caster, 60, a -> at);
        JjCommon.crater(level, at, 2.6 * PU, 0.5, 6);
        dust(level, at, 3.6f);
        DbCommon.screen(level, at, 4);
        SubCommon.sound(level, at, SoundEvents.WOOD_PLACE, 2f, 0.5f);
        Tasks.add(age -> {
            d.age = age;
            boolean gone = !caster.isAlive();
            if (d.creakAt >= 0 && d.counterAt < 0 && age >= d.creakAt + 8) {
                d.counterAt = age;
                SubNet.send(level, HsNet.DOME, caster.getId(), at, d.side, 2, 0, 0);
                DbCommon.screen(level, at, 8);
                dust(level, at.add(d.side.scale(1.9 * PU)), 3);
                SubCommon.sound(level, at, SoundEvents.WOOD_BREAK, 2.4f, 0.5f);
                for (LivingEntity e : near(level, at, 5.5, 2, 5, caster)) {
                    Vec3 to = SubCommon.flat(e.position().subtract(at));
                    if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(d.side) < 0.1) {
                        continue;
                    }
                    Combat.hurt(caster, e, 24);
                    throwPage(e, d.side, 9, 11);
                    stun(caster, e, 25);
                    DbCommon.screen(level, Combat.center(e), 17);
                }
            }
            boolean open = d.counterAt >= 0 ? age >= d.counterAt + 38 : d.creakAt < 0 && age >= 52;
            if (open || gone || age > 120) {
                SubNet.send(level, HsNet.DOME, caster.getId(), at, Vec3.ZERO, 3, 0, 0);
                DOMES.remove(caster.getId());
                if (d.release != null) {
                    d.release.run();
                }
                return true;
            }
            return false;
        }, () -> {
            DOMES.remove(caster.getId());
            if (d.release != null) {
                d.release.run();
            }
        });
        return true;
    }

    // ================= V: Dragão de Madeira =================
    static boolean dragon(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 44);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        Vec3 dir = aimDir(caster, target);
        double yaw = yawOf(dir);
        Vec3 hole = SubCommon.ground(level, toWorld(caster.position(), yaw, 6.2, 0, 1.0));
        Vec3 dirT = SubCommon.flat(target.position().subtract(hole));
        dirT = dirT.lengthSqr() < 1.0E-4 ? dir : dirT.normalize();
        SubNet.send(level, HsNet.DRAGON, caster.getId(), hole, dirT, 0, 0, 0);
        SubCommon.pose(caster, "nr.selo_cruz");
        SubCommon.busy(caster, 22);
        SubCommon.sound(level, hole, SoundEvents.ROOTED_DIRT_BREAK, 2.4f, 0.4f);
        // the page's rise, on the server too: where the head ends at the top (page, yaw 0 at the hole)
        double sx = Math.cos(6.0) * 1.9, sz = Math.sin(6.0) * 1.9;
        Vec3 top = hole.add((sx + dirT.x * 4) * PU, (-3.2 + 15 + 0.6) * PU, (sz + dirT.z * 4) * PU);
        int burst = 21, hunt = burst + 33, maxHunt = hunt + 120;
        Vec3[] hp = {top};
        Vec3[] vel = {new Vec3(0, -1, 0)};
        int[] phase = {0};
        int[] at = {0};
        LivingEntity[] caught = {null};
        Runnable[] release = {null};
        Runnable[] breaker = {null};
        Vec3[] cpos = {null};
        Tasks.add(age -> {
            if (age < burst) {
                if (age % 5 == 1) {
                    DbCommon.screen(level, hole, 4);
                    dust(level, hole, 2f);
                }
                return false;
            }
            if (age == burst) {
                JjCommon.crater(level, hole, 3.3 * PU, 3.2 * PU, 40);
                dust(level, hole, 8);
                DbCommon.screen(level, hole, 18);
                SubCommon.sound(level, hole, SoundEvents.GENERIC_EXPLODE.value(), 2.4f, 0.5f);
                return false;
            }
            if (age == burst + 18) {
                SubCommon.sound(level, top, SoundEvents.RAVAGER_ROAR, 3f, 0.5f);
                SubCommon.sound(level, top, SoundEvents.ENDER_DRAGON_GROWL, 1.6f, 0.6f);
                DbCommon.screen(level, top, 18);
            }
            if (age < hunt) {
                return false;
            }
            double dt = 0.05;
            if (phase[0] == 0) {
                if (!target.isAlive() || age > maxHunt) {
                    SubNet.send(level, HsNet.DRAGON, caster.getId(), hp[0], vel[0], 7, 0, 0);
                    return true;
                }
                Vec3 aim = target.position().add(0, -0.8 * PU, 0);
                Vec3 want = aim.subtract(hp[0]);
                Vec3 cur = vel[0].normalize(), wd = want.lengthSqr() < 1.0E-6 ? cur : want.normalize();
                double ang = Math.acos(Mth.clamp(cur.dot(wd), -1, 1)), maxA = 2.2 * dt;
                if (ang > 1.0E-4) {
                    Vec3 ax = cur.cross(wd);
                    if (ax.lengthSqr() > 1.0E-10) {
                        cur = rotate(cur, ax.normalize(), Math.min(ang, maxA));
                    }
                }
                vel[0] = cur;
                hp[0] = hp[0].add(cur.scale(17 * PU * dt));
                SubNet.send(level, HsNet.DRAGON, caster.getId(), hp[0], cur, 2, 0, 0);
                if (age % 6 == 0) {
                    SubCommon.sound(level, hp[0], SoundEvents.WOOD_STEP, 2f, 0.4f);
                }
                // caught: under the head (the page's 2.9 u across, 3.8 u high)
                for (LivingEntity e : near(level, new Vec3(hp[0].x, hp[0].y - 3.8 * PU, hp[0].z), 2.9 * PU, 0.5, 3.8 * PU + 1.5, caster)) {
                    if (held(e) && e != target) {
                        continue;
                    }
                    caught[0] = e;
                    break;
                }
                if (caught[0] != null) {
                    LivingEntity e = caught[0];
                    phase[0] = 1;
                    at[0] = age;
                    cpos[0] = e.position();
                    SubNet.send(level, HsNet.DRAGON, caster.getId(), cpos[0], Vec3.ZERO, 4, 0, 0);
                    Combat.hurt(caster, e, 8);
                    DbCommon.screen(level, Combat.center(e), 17);
                    if (!held(e)) {
                        release[0] = Freeze.hold(e, 50, a -> {
                            int t = age + a - at[0];
                            double up = t > 36 ? Math.sin(Math.min(1, (t - 36) / 10.0) * Math.PI) * 3.5 * PU : 0;
                            return cpos[0].add(0, up, 0);
                        });
                        breaker[0] = () -> {
                            if (release[0] != null) {
                                release[0].run();
                            }
                        };
                        Capture.trap(e, breaker[0]);
                    }
                    return false;
                }
                double gy = SubCommon.ground(level, hp[0]).y;
                if (hp[0].y < gy + 0.5 * PU && cur.y < 0) {
                    phase[0] = 3;
                    at[0] = age;
                    SubNet.send(level, HsNet.DRAGON, caster.getId(), hp[0], cur, 3, 0, 0);
                    JjCommon.crater(level, hp[0], 3.0 * PU, 2.3 * PU, 40);
                    dust(level, hp[0], 6);
                    DbCommon.screen(level, hp[0], 10);
                    SubCommon.sound(level, hp[0], SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.6f);
                    for (LivingEntity e : near(level, hp[0], 3 * PU, 2, 4, caster)) {
                        Combat.hurt(caster, e, 20);
                        Combat.pushFrom(e, hp[0], 0.8, 0.5);
                    }
                }
                return false;
            }
            if (phase[0] == 1) {
                LivingEntity e = caught[0];
                int t = age - at[0];
                if (t > 10 && t <= 36 && e.isAlive()) {
                    Stamina.drain(e, 1.2);
                }
                if (t == 36) {
                    SubNet.send(level, HsNet.DRAGON, caster.getId(), cpos[0], Vec3.ZERO, 5, 0, 0);
                }
                if (t >= 46 || !e.isAlive()) {
                    Capture.untrap(e, breaker[0]);
                    if (release[0] != null) {
                        release[0].run();
                    }
                    if (e.isAlive()) {
                        Vec3 p = SubCommon.ground(level, cpos[0]);
                        Combat.hurt(caster, e, 45);
                        stun(caster, e, 30);
                        JjCommon.crater(level, p, 2.2 * PU, 1.5 * PU, 30);
                        dust(level, p, 5);
                        DbCommon.screen(level, p, 10);
                        SubCommon.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.7f);
                    }
                    return true;
                }
                return false;
            }
            // stuck in the ground: it goes away on its own
            return age - at[0] > 60;
        });
        return true;
    }

    static Vec3 rotate(Vec3 v, Vec3 ax, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        Vec3 cr = ax.cross(v);
        double d = ax.dot(v);
        return v.scale(c).add(cr.scale(s)).add(ax.scale(d * (1 - c)));
    }

    public static void resetStatics() {
        DOMES.clear();
        HsBuddha.resetStatics();
    }
}
