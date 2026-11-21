// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.combat.clash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Clash de poderes (forja/clash-poderes, approved by Stark 2026-11-21): two LAUNCHABLE powers (beams and balls, a closed
// list: only the moves that report here take part, the rest of the mod never knows this exists) meeting head on lock
// into one point of light. Both owners are held in their pose; each one presses their skill keys as fast as they can
// (players: any ability key, caught in ModPayloads before PowerLogic; bots press on their own) and the faster one,
// times the power's weight, pushes the point towards the other. Shift spends all the energy on a 0.7 s shove (it comes
// back in 9 s). Who the point reaches takes one blast made of both powers; when the 14 s run out it blows up in the middle
// and throws both away.
//
// A beam reports every tick it lives (beam()) and draws/hurts up to the point it gets back; while locked() it must not end
// by itself, and once done() it stops at once without its own blast (the clash made it). A ball is a Destructible.Attack
// marked with Attack.clash(...): while locked the clash holds it in the air (its task is paused), and at the end it is
// erased (its task sees gone() and stops quietly).
public final class Clash {
    // SubNet kinds 540-559 (the next free block after the end-game bosses' 520-539).
    public static final int FIRST = 540;
    // The clash (stream): id = the clash, pos = the point, dir = axis from side A to side B (unit), a = radius,
    // b = how far it leans (-1 B winning .. +1 A winning), c = 0 pushing, 1 running to the loser.
    public static final int POINT = 540;
    // Who clashes (sent at the start and every second): id, pos = A's origin, dir = B's origin, a = A's color (0xRRGGBB),
    // b = B's color, c = radius of a ball the clients fly on their own (0 none: see holdBall). The entity ids go in IDS.
    public static final int INFO = 541;
    // The two owners: id, a = A's entity id, b = B's entity id.
    public static final int IDS = 542;
    // A press (a pulse in the beam and the ball): id, a = side (1 A, -1 B), b = 1 when it is the Shift shove.
    public static final int PRESS = 543;
    // The end: id, pos = where it blows up, dir.x = the ground's height, a = size, b = color (0xRRGGBB), c = winner side (0 draw).
    public static final int BOOM = 544;
    // Only to the two players: id, pos = (lean, seconds left, own energy), dir = (other's energy, own side, 0), a = 1 shown, 0 gone.
    public static final int HUD = 545;
    public static final int LAST = 559;

    // /gamerule soulsclash false: no clash at all, the launchables pass through each other as before (Stark 2026-11-21).
    // true (default): the clash as approved.
    public static final net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.BooleanValue> RULE =
            net.minecraft.world.level.GameRules.register("soulsclash", net.minecraft.world.level.GameRules.Category.PLAYER,
                    net.minecraft.world.level.GameRules.BooleanValue.create(true));

    private static final int TIME = 14 * 20;
    private static final double DT = 0.05;
    // How many blocks the point moves per unit of lean speed per second (the page's SPAN).
    private static final double SPAN = 7.2;
    private static final float SURGE_PUSH = 9;
    private static final int SURGE_TICKS = 14;
    private static final double MIN_GAP = 1.6;

    // One launchable as it was this tick.
    private static final class Shot {
        final int id;
        LivingEntity owner;
        ServerLevel level;
        Vec3 from;
        Vec3 to;
        double width;
        float weight;
        int rgb;
        float power;
        long seen;
        Duel duel;
        boolean done;

        Shot(int id) {
            this.id = id;
        }
    }

    private static final class Side {
        final LivingEntity owner;
        final Shot beam;
        final Destructible.Attack ball;
        final float weight;
        // How hard this side pushes per press: the power's weight, the owner's tier and how charged it was.
        final float strength;
        final int rgb;
        final float power;
        Vec3 origin;
        double rate;
        int presses;
        float energy = 1;
        int surge;
        boolean shiftWas;
        double aiNext;
        float aiRate;
        Runnable release;

        Side(LivingEntity owner, Shot beam, Destructible.Attack ball, float weight, int rgb, float power, Vec3 origin) {
            this.owner = owner;
            this.beam = beam;
            this.ball = ball;
            this.weight = weight;
            this.strength = strength(owner, weight);
            this.rgb = rgb;
            this.power = power;
            this.origin = origin;
            // Bots press like a calm human; bosses like a fast one (Stark 2026-11-21: at 6.8/8.2 a Kaioken Kamehameha
            // nearly lost to a bot's Big Bang).
            this.aiRate = owner instanceof SoulBoss ? 6.0f : 4.6f;
        }

        boolean alive(long now) {
            if (!owner.isAlive() || owner.isRemoved()) {
                return false;
            }
            if (beam != null) {
                return now - beam.seen <= 3;
            }
            return ball != null && !ball.gone();
        }
    }

    // Stark 2026-11-21: the push mixes three things. The power's weight (squared, so 1.25 against 1.1 is a clear edge),
    // the tier (each tier above the other is 8% more) and the charge (a beam let go at once pushes 60%, full 100%).
    // A move that doesn't charge counts as full; a bot as three quarters charged (it holds its own random time).
    private static float strength(LivingEntity owner, float weight) {
        int tier = com.starkinhon.battlegroundsmod.power.Tiers.of(owner);
        float tierK = (float) Math.pow(1.08, com.starkinhon.battlegroundsmod.power.Tiers.HUMANO - tier);
        float charge = 0.75f;
        if (owner instanceof ServerPlayer) {
            var data = owner.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER);
            // charged in the last 5 s: this move's charge; otherwise a move without one (full)
            boolean recent = owner.level().getGameTime() - data.chargeReportedAt <= 100;
            charge = recent ? data.chargeProgress : 1;
        }
        return weight * weight * tierK * (0.6f + 0.4f * charge);
    }

    private static final class Duel {
        final int id = SubNet.newId();
        final ServerLevel level;
        final Side a;
        final Side b;
        double d;
        double len;
        double vel;
        int age;
        int timer = TIME;
        int winner;
        int state;
        float radius = 1.4f;

        Duel(ServerLevel level, Side a, Side b, Vec3 point) {
            this.level = level;
            this.a = a;
            this.b = b;
            this.len = Math.max(2 * MIN_GAP + 0.5, a.origin.distanceTo(b.origin));
            Vec3 axis = axis();
            this.d = Mth.clamp(point.subtract(a.origin).dot(axis), MIN_GAP, len - MIN_GAP);
        }

        Vec3 axis() {
            Vec3 ab = b.origin.subtract(a.origin);
            return ab.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : ab.normalize();
        }

        Vec3 point() {
            return a.origin.add(axis().scale(d));
        }

        double lean() {
            return Mth.clamp((d / len) * 2 - 1, -1, 1);
        }
    }

    private static final Map<Integer, Shot> SHOTS = new HashMap<>();
    private static final List<Duel> DUELS = new ArrayList<>();
    // Each clashable ball's place last tick (ball x ball is checked along the way both made, not only where they are).
    private static final Map<Destructible.Attack, Vec3> PREV = new java.util.WeakHashMap<>();

    private Clash() {
    }

    // ---------- what the powers call ----------

    // A beam, every tick it lives: from its hands to where it reaches now. Returns where it must end this tick (the
    // clash point while it is in a clash, else `to`). weight: how hard it pushes; rgb: its color; power: what it adds to
    // the final blast's damage.
    public static Vec3 beam(LivingEntity owner, ServerLevel level, int id, Vec3 from, Vec3 to, double width, float weight, int rgb, float power) {
        Shot s = SHOTS.computeIfAbsent(id, k -> new Shot(id));
        s.owner = owner;
        s.level = level;
        s.from = from;
        s.to = to;
        s.width = width;
        s.weight = weight;
        s.rgb = rgb;
        s.power = power;
        s.seen = level.getGameTime();
        if (s.duel == null && !s.done) {
            tryBeam(s);
        }
        if (s.duel != null) {
            return s.duel.point();
        }
        return to;
    }

    // Held in a clash: the move must not end on its own (its timer, the key let go).
    public static boolean locked(int id) {
        Shot s = SHOTS.get(id);
        return s != null && s.duel != null;
    }

    // The clash is over and made the blast: the move stops now, quietly. Forgets the beam.
    public static boolean done(int id) {
        Shot s = SHOTS.get(id);
        if (s != null && s.done) {
            SHOTS.remove(id);
            return true;
        }
        return false;
    }

    // The beam ended by itself (not in a clash): forget it.
    public static void forget(int id) {
        Shot s = SHOTS.get(id);
        if (s != null && s.duel == null) {
            SHOTS.remove(id);
        }
    }

    // A player pressed an ability key: inside a clash it is a press (and nothing else happens). True when it was taken.
    public static boolean mash(ServerPlayer player) {
        Duel duel = duelOf(player);
        if (duel == null) {
            return false;
        }
        if (duel.state == 0) {
            (duel.a.owner == player ? duel.a : duel.b).presses++;
        }
        return true;
    }

    // In a clash right now (held in the pose).
    public static boolean inClash(LivingEntity entity) {
        return duelOf(entity) != null;
    }

    private static Duel duelOf(LivingEntity entity) {
        for (Duel duel : DUELS) {
            if (duel.a.owner == entity || duel.b.owner == entity) {
                return duel;
            }
        }
        return null;
    }

    // ---------- finding a clash ----------

    private static boolean free(LivingEntity a, LivingEntity b) {
        return a != b && a.isAlive() && b.isAlive() && duelOf(a) == null && duelOf(b) == null && !a.isAlliedTo(b)
                && Combat.isTarget(b, a) && Combat.isTarget(a, b);
    }

    private static void tryBeam(Shot s) {
        long now = s.level.getGameTime();
        Vec3 dirA = s.to.subtract(s.from);
        double lenA = dirA.length();
        if (lenA < 1.0E-3) {
            return;
        }
        dirA = dirA.scale(1 / lenA);
        // beam x beam: coming at each other, the other's tip already past ours, close enough side to side
        for (Shot o : SHOTS.values()) {
            if (o == s || o.duel != null || o.done || o.level != s.level || now - o.seen > 1 || !free(s.owner, o.owner)) {
                continue;
            }
            Vec3 dirB = o.to.subtract(o.from);
            double lenB = dirB.length();
            if (lenB < 1.0E-3 || dirB.scale(1 / lenB).dot(dirA) > -0.55) {
                continue;
            }
            double tipB = o.to.subtract(s.from).dot(dirA);
            double fromB = o.from.subtract(s.from).dot(dirA);
            if (tipB > lenA + 0.3 || fromB < lenA - 0.3 || fromB < 2 * MIN_GAP) {
                continue;
            }
            // the stretch where the two bodies overlap, in A's terms; the point is its middle
            double at = Mth.clamp((Math.max(0, tipB) + Math.min(lenA, fromB)) / 2, MIN_GAP, fromB - MIN_GAP);
            Vec3 pa = s.from.add(dirA.scale(at));
            Vec3 pb = closest(pa, o.from, o.to);
            if (pa.distanceTo(pb) > (s.width + o.width) / 2 + 0.8) {
                continue;
            }
            start(s.level, new Side(s.owner, s, null, s.weight, s.rgb, s.power, s.from),
                    new Side(o.owner, o, null, o.weight, o.rgb, o.power, o.from), pa.add(pb).scale(0.5));
            return;
        }
        // beam x ball: the ball touches the beam's body
        for (Destructible.Attack ball : Destructible.clashables(s.level)) {
            if (!(ball.owner() instanceof LivingEntity bo) || !free(s.owner, bo)) {
                continue;
            }
            Vec3 c = ball.pos();
            if (c.distanceTo(closest(c, s.from, s.to)) > ball.radius() + s.width / 2 + 0.3) {
                continue;
            }
            double along = c.subtract(s.from).dot(dirA);
            if (along < MIN_GAP) {
                continue;
            }
            start(s.level, new Side(s.owner, s, null, s.weight, s.rgb, s.power, s.from),
                    new Side(bo, null, ball, ball.clashWeight(), ball.clashColor(), ball.clashPower(), origin(ball, s.from)), c);
            return;
        }
    }

    // ball x ball: two clashable balls touching
    private static void tryBalls(ServerLevel level) {
        List<Destructible.Attack> balls = Destructible.clashables(level);
        try {
            tryBalls(level, balls);
        } finally {
            for (Destructible.Attack ball : balls) {
                PREV.put(ball, ball.pos());
            }
        }
    }

    private static void tryBalls(ServerLevel level, List<Destructible.Attack> balls) {
        for (int i = 0; i < balls.size(); i++) {
            for (int j = i + 1; j < balls.size(); j++) {
                Destructible.Attack p = balls.get(i);
                Destructible.Attack q = balls.get(j);
                if (!(p.owner() instanceof LivingEntity po) || !(q.owner() instanceof LivingEntity qo) || !free(po, qo)) {
                    continue;
                }
                // where they came from last tick: two fast balls can cross each other between two ticks
                Vec3 p0 = PREV.getOrDefault(p, p.pos());
                Vec3 q0 = PREV.getOrDefault(q, q.pos());
                Vec3 rel0 = p0.subtract(q0);
                Vec3 relMove = p.pos().subtract(p0).subtract(q.pos().subtract(q0));
                double mm = relMove.lengthSqr();
                double t = mm < 1.0E-8 ? 1 : Mth.clamp(-rel0.dot(relMove) / mm, 0, 1);
                Vec3 pt = p0.lerp(p.pos(), t);
                Vec3 qt = q0.lerp(q.pos(), t);
                if (pt.distanceTo(qt) > p.radius() + q.radius()) {
                    continue;
                }
                double k = p.radius() / Math.max(0.01, p.radius() + q.radius());
                Vec3 at = pt.lerp(qt, k);
                Vec3 po0 = Combat.center(po);
                Vec3 qo0 = Combat.center(qo);
                start(level, new Side(po, null, p, p.clashWeight(), p.clashColor(), p.clashPower(), p.falls() ? origin(p, qo0) : po0),
                        new Side(qo, null, q, q.clashWeight(), q.clashColor(), q.clashPower(), q.falls() ? origin(q, po0) : qo0), at);
                return;
            }
        }
    }

    // Where a ball's side of the clash starts: its owner, or for a ball falling from the sky (the meteor) a point further
    // up its way, so beating it sends it back up and it blows up high.
    private static Vec3 origin(Destructible.Attack ball, Vec3 otherFrom) {
        if (!ball.falls()) {
            return Combat.center(ball.owner());
        }
        Vec3 away = ball.pos().subtract(otherFrom);
        away = away.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : away.normalize();
        return ball.pos().add(away.scale(14));
    }

    private static Vec3 closest(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l = ab.lengthSqr();
        double t = l < 1.0E-8 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / l, 0, 1);
        return a.add(ab.scale(t));
    }

    // Loads the class so the rule is registered at startup.
    public static void init() {
    }

    private static void start(ServerLevel level, Side a, Side b, Vec3 point) {
        if (!level.getGameRules().getBoolean(RULE)) {
            return;
        }
        if (a.origin.distanceTo(b.origin) < 2 * MIN_GAP + 0.5) {
            return;
        }
        Duel duel = new Duel(level, a, b, point);
        if (a.beam != null) {
            a.beam.duel = duel;
        }
        if (b.beam != null) {
            b.beam.duel = duel;
        }
        DUELS.add(duel);
        for (Side s : new Side[] {a, b}) {
            s.release = Freeze.holdLookFree(s.owner, TIME + 60);
            holdBall(duel, s);
            s.shiftWas = s.owner.isShiftKeyDown();
        }
        Vec3 at = duel.point();
        info(duel);
        SubNet.send(level, POINT, duel.id, at, duel.axis(), duel.radius, (float) duel.lean(), 0);
        DbCommon.screen(level, at, 45);
        SubCommon.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.6f);
        SubCommon.sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6f, 1.4f);
        double ground = DbCommon.groundY(level, at);
        if (at.y - ground < 4) {
            JjCommon.crater(level, new Vec3(at.x, ground, at.z), 2.2, 1, 14);
        }
    }

    // A ball in the clash: held in the air (its own task paused). A streamed one sits on its side of the point, its
    // front touching it, and is sent from here; one the clients fly stays where it locked.
    private static void holdBall(Duel duel, Side s) {
        if (s.ball == null || s.ball.gone()) {
            return;
        }
        s.ball.hold(3);
        if (s.ball.clashStreamed()) {
            Vec3 toOwner = duel.axis().scale(s == duel.a ? -1 : 1);
            Vec3 at = duel.point().add(toOwner.scale(s.ball.radius() * 0.8));
            s.ball.move(at);
            s.ball.clashDrawAt(at);
        }
    }

    // The radius of the biggest ball in the clash the clients fly on their own (0 for none): they freeze what is near
    // the point by this and move it with the point.
    private static float flown(Duel duel) {
        float r = 0;
        for (Side s : new Side[] {duel.a, duel.b}) {
            if (s.ball != null && !s.ball.clashStreamed()) {
                r = Math.max(r, (float) s.ball.radius());
            }
        }
        return r;
    }

    private static void info(Duel duel) {
        SubNet.send(duel.level, IDS, duel.id, duel.point(), Vec3.ZERO, duel.a.owner.getId(), duel.b.owner.getId(), 0);
        SubNet.send(duel.level, INFO, duel.id, duel.a.origin, duel.b.origin, duel.a.rgb, duel.b.rgb, flown(duel));
    }

    // ---------- every server tick ----------

    public static void tick() {
        // forget beams that stopped reporting
        Iterator<Shot> it = SHOTS.values().iterator();
        while (it.hasNext()) {
            Shot s = it.next();
            if (s.duel == null && (s.level == null || s.level.getGameTime() - s.seen > 40)) {
                it.remove();
            }
        }
        List<ServerLevel> levels = new ArrayList<>();
        for (Destructible.Attack ball : Destructible.clashablesAll()) {
            if (ball.level() instanceof ServerLevel l && !levels.contains(l)) {
                levels.add(l);
            }
        }
        for (ServerLevel l : levels) {
            tryBalls(l);
        }
        Iterator<Duel> dit = DUELS.iterator();
        while (dit.hasNext()) {
            Duel duel = dit.next();
            if (step(duel)) {
                finish(duel);
                dit.remove();
            }
        }
    }

    // One tick of a clash; true when it is over.
    private static boolean step(Duel duel) {
        ServerLevel level = duel.level;
        long now = level.getGameTime();
        duel.age++;
        boolean aOk = duel.a.alive(now);
        boolean bOk = duel.b.alive(now);
        if (duel.state == 0 && (!aOk || !bOk)) {
            // one side fell out (died, was knocked out of the move): the other wins
            if (!aOk && !bOk) {
                return true;
            }
            win(duel, aOk ? 1 : -1);
        }
        for (Side s : new Side[] {duel.a, duel.b}) {
            holdBall(duel, s);
            if (s.owner.isAlive()) {
                SubCommon.busy(s.owner, 3);
            }
        }
        Vec3 at = duel.point();
        if (duel.state == 0) {
            duel.timer--;
            press(duel, duel.a, 1, now);
            press(duel, duel.b, -1, now);
            // The weight counts squared: the stronger power wins clearly when both press alike (1.25 vs 1.1 was too close).
            double fa = duel.a.rate * duel.a.strength + (duel.a.surge > 0 ? SURGE_PUSH : 0);
            double fb = duel.b.rate * duel.b.strength + (duel.b.surge > 0 ? SURGE_PUSH : 0);
            duel.vel += ((fa - fb) * 0.075 - duel.vel) * (1 - Math.exp(-DT / 0.25));
            duel.d = Mth.clamp(duel.d + duel.vel * SPAN * DT, 0, duel.len);
            float k = 1 - duel.timer / (float) TIME;
            duel.radius = 1.4f + 0.7f * k * k * (3 - 2 * k);
            if (duel.d >= duel.len - MIN_GAP) {
                win(duel, 1);
            } else if (duel.d <= MIN_GAP) {
                win(duel, -1);
            } else if (duel.timer <= 0) {
                duel.winner = 0;
                return true;
            }
            // the ground under the point: a trench that follows it
            if (duel.age % 3 == 0) {
                double ground = DbCommon.groundY(level, at);
                if (at.y - ground < 4) {
                    JjCommon.crater(level, new Vec3(at.x, ground, at.z), 1.1 + 0.4 * duel.radius, 0.6 + 0.3 * duel.radius, 2);
                }
            }
            if (duel.age % 10 == 0) {
                SubCommon.sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.6f, 0.5f + 0.1f * SubCommon.RANDOM.nextFloat());
            }
            if (duel.age % 20 == 0) {
                info(duel);
            }
            hud(duel, true);
        } else {
            // running to the loser, swallowed by the winning side
            Vec3 loser = duel.winner > 0 ? duel.b.origin : duel.a.origin;
            double target = duel.winner > 0 ? duel.len - 0.6 : 0.6;
            duel.d += Mth.clamp(target - duel.d, -2.2, 2.2);
            duel.radius = Math.max(0.7f, duel.radius - 0.08f);
            if (Math.abs(duel.d - target) < 0.3 || duel.age > duel.state + 14) {
                return true;
            }
            at = duel.point();
            if (at.distanceTo(loser) < 0.8) {
                return true;
            }
        }
        SubNet.send(level, POINT, duel.id, duel.point(), duel.axis(), duel.radius, (float) duel.lean(), duel.state == 0 ? 0 : 1);
        return false;
    }

    private static void win(Duel duel, int side) {
        duel.winner = side;
        duel.state = Math.max(1, duel.age);
        hud(duel, false);
    }

    // Presses, Shift and the bots for one side.
    private static void press(Duel duel, Side s, int sign, long now) {
        int presses = s.presses;
        s.presses = 0;
        boolean shove = false;
        if (s.owner instanceof ServerPlayer player) {
            boolean shift = player.isShiftKeyDown();
            shove = shift && !s.shiftWas;
            s.shiftWas = shift;
        } else {
            s.aiNext -= DT;
            double noise = 1 + 0.3 * Math.sin(duel.age * 0.11 + s.owner.getId());
            if (s.aiNext <= 0) {
                presses++;
                s.aiNext = (0.7 + 0.6 * SubCommon.RANDOM.nextDouble()) / (s.aiRate * noise);
            }
            // a bot losing badly throws its all-or-nothing
            double lean = duel.lean() * sign;
            // (only when nearly lost, and not at once: it kept turning every clash around)
            shove = lean < -0.65 && SubCommon.RANDOM.nextFloat() < 0.03f;
        }
        s.rate = s.rate * Math.exp(-DT / 0.6) + presses / 0.6;
        if (presses > 0) {
            SubNet.send(duel.level, PRESS, duel.id, duel.point(), Vec3.ZERO, sign, 0, 0);
        }
        if (shove && s.energy >= 0.5f && s.surge <= 0) {
            s.surge = SURGE_TICKS;
            s.energy = 0;
            SubNet.send(duel.level, PRESS, duel.id, duel.point(), Vec3.ZERO, sign, 1, 0);
            SubCommon.sound(duel.level, s.origin, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.2f);
        }
        s.surge = Math.max(0, s.surge - 1);
        s.energy = Math.min(1, s.energy + (float) (DT / 9));
    }

    private static void hud(Duel duel, boolean shown) {
        for (Side s : new Side[] {duel.a, duel.b}) {
            if (s.owner instanceof ServerPlayer player) {
                int sign = s == duel.a ? 1 : -1;
                Side o = s == duel.a ? duel.b : duel.a;
                SubNet.sendTo(player, HUD, duel.id, new Vec3(duel.lean(), duel.timer / 20.0, s.energy), new Vec3(o.energy, sign, 0),
                        shown ? 1 : 0, 0, 0);
            }
        }
    }

    // The blast: on the loser with both powers summed, or in the middle (a draw) throwing both away.
    private static void finish(Duel duel) {
        ServerLevel level = duel.level;
        Vec3 at = duel.winner > 0 ? duel.b.origin : duel.winner < 0 ? duel.a.origin : duel.point();
        Side win = duel.winner > 0 ? duel.a : duel.winner < 0 ? duel.b : null;
        float sum = duel.a.power + duel.b.power;
        float size = Mth.clamp(4.5f + sum * 0.06f, 4.5f, 9);
        double ground = DbCommon.groundY(level, at);
        int color = win != null ? win.rgb : 0xE6DAFF;
        SubNet.send(level, BOOM, duel.id, at, new Vec3(ground, 0, 0), size, color, duel.winner);
        // the blast itself: the Dragon Ball kits' approved ki blast, in the winner's color (white for a draw)
        DbCommon.boom(level, at, size, win != null ? palette(win.rgb) : 10, 7);
        SubNet.send(level, POINT, duel.id, at, duel.axis(), 0, (float) duel.lean(), 2);
        hud(duel, false);
        SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.6f);
        SubCommon.sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 2f, 0.7f);
        DbCommon.screen(level, at, 45);
        for (Side s : new Side[] {duel.a, duel.b}) {
            if (s.release != null) {
                s.release.run();
            }
            if (s.beam != null) {
                s.beam.done = true;
                s.beam.duel = null;
            }
            if (s.ball != null) {
                s.ball.erase();
            }
        }
        if (at.y - ground < 5) {
            DbCommon.bigCrater(level, new Vec3(at.x, ground, at.z), size * 0.9, size * 0.45, 8);
        }
        if (win != null) {
            DbCommon.hurtRadius(win.owner, level, at, size, sum, 1.6, 0.6);
        } else {
            // a draw: each side's half hits the other (and anyone near), both thrown away from the middle
            DbCommon.hurtRadius(duel.a.owner, level, at, size + 2, sum * 0.3f, 1.8, 0.6);
            DbCommon.hurtRadius(duel.b.owner, level, at, size + 2, sum * 0.3f, 1.8, 0.6);
            for (Side s : new Side[] {duel.a, duel.b}) {
                if (s.owner.isAlive()) {
                    Combat.pushFrom(s.owner, at, 1.8, 0.7);
                }
            }
        }
    }

    // DbFx's ki colors (SubNet.KI_BOOM b): the one nearest to rgb.
    private static final int[] PALETTE = {0x5AB8FF, 0xE8F8FF, 0xFFD84A, 0xFFF6C8, 0xFF3A2A, 0xFFD0C8, 0xC050FF, 0xFF60D0, 0xFFE0F6, 0x70FF80,
            0xFFFFFF, 0xFF8A2A, 0x60B0FF, 0x2F7DFF};

    private static int palette(int rgb) {
        int best = 10;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            int p = PALETTE[i];
            long dr = ((p >> 16) & 255) - ((rgb >> 16) & 255);
            long dg = ((p >> 8) & 255) - ((rgb >> 8) & 255);
            long db = (p & 255) - (rgb & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public static void clear() {
        for (Duel duel : DUELS) {
            for (Side s : new Side[] {duel.a, duel.b}) {
                if (s.release != null) {
                    s.release.run();
                }
            }
        }
        DUELS.clear();
        SHOTS.clear();
        PREV.clear();
    }
}
