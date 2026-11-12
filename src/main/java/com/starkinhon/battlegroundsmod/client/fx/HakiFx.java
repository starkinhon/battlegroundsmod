package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// Luffy's Conqueror's Haki (forja/luffy-haki, approved 2026-11-12: "tá ótimo"), the page's haki.js carried over.
// Only two places: red bolts on the Gear 4 punches, and in Gear 5 red and yellow bolts leaking out of him all the time,
// the pressure wave, the weak ones near him on their knees and every blow imbued.
// Each bolt is one tube (the BOLT's zigzag in the shader) with its own seed, size, life, path and branches: a black core,
// a thin edge in its color and a soft glow over it (the color does not depend on the sky). It grows from the root to the
// tip and is eaten from the root, flickering like anime lightning.
public final class HakiFx {
    private static final float[] RED_EDGE = {1f, 0.1f, 0.14f}, RED_GLOW = {0.95f, 0.08f, 0.16f};
    private static final float[] YEL_EDGE = {1f, 0.84f, 0.22f}, YEL_GLOW = {1f, 0.7f, 0.12f};
    // the page's "forte": bolts a second on the body, to the ground, up into the air, through the air; the pulse (s)
    private static final double CRAWL = 26, ARC = 3.2, SKY = 1.2, AIR = 1.4, PULSE = 1.8;
    private static final int MAX_BOLTS = 96;

    private static final class Bolt {
        Supplier<Vec3> from, to;
        double r, zig, k, wob, bend, seed, ph, spd, born, life, grow, keep, flick;
        float[] edge, glow;
        java.util.function.Consumer<Vec3> onHit;
        boolean hit;
        // its frame this frame (for branches that leave from it)
        Vec3 o, x, y, z;
        double L;
    }

    private record Mark(Vec3 at, double born, double size, float seed, float[] edge) {
    }

    private static final class Pebble {
        Vec3 p, v;
        double born, life, floor;
        float sx, sy, sz, rx, ry, rz, wx, wy, wz;
        int col;
    }

    private record Shell(Vec3 at, double born, double life, double r, double flat, float seed, float[] edge) {
    }

    private record Ring(Vec3 at, double born, float seed, float[] edge) {
    }

    // one Luffy in G5: when the next of each comes
    private static final class Leak {
        double crawl, arc, sky, air, nextPulse = 0.6, last;
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final List<Pebble> PEBBLES = new ArrayList<>();
    private static final List<Shell> SHELLS = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final Map<Integer, Leak> LEAKS = new HashMap<>();
    // whoever is weakened by his haki: until when (ticks)
    private static final Map<Integer, Double> WEAK = new HashMap<>();
    private static double now, wrapAcc;
    private static Vec3 cam = Vec3.ZERO;

    private HakiFx() {
    }

    public static boolean active() {
        return !BOLTS.isEmpty() || !MARKS.isEmpty() || !PEBBLES.isEmpty() || !SHELLS.isEmpty() || !RINGS.isEmpty() || !WEAK.isEmpty();
    }

    public static void clear() {
        BOLTS.clear();
        MARKS.clear();
        PEBBLES.clear();
        SHELLS.clear();
        RINGS.clear();
        LEAKS.clear();
        WEAK.clear();
    }

    private static double rnd() {
        return OpmBlocks.rnd();
    }

    private static float[][] col(boolean mix) {
        return mix && rnd() < 0.5 ? new float[][] {YEL_EDGE, YEL_GLOW} : new float[][] {RED_EDGE, RED_GLOW};
    }

    private static Bolt bolt(Supplier<Vec3> from, Supplier<Vec3> to, double life, double r, double zig, double k, double bend, double wob, double spd, double grow,
            double keep, double flick, float[][] c) {
        if (BOLTS.size() >= MAX_BOLTS) {
            return null;
        }
        Bolt b = new Bolt();
        b.from = from;
        b.to = to;
        b.life = life;
        b.r = r;
        b.zig = zig;
        b.k = k;
        b.bend = bend;
        b.wob = wob;
        b.spd = spd;
        b.grow = grow;
        b.keep = keep;
        b.flick = flick;
        b.seed = rnd() * 10;
        b.ph = rnd() * 6;
        b.born = now;
        b.edge = c[0];
        b.glow = c[1];
        frame(b);
        BOLTS.add(b);
        return b;
    }

    private static Supplier<Vec3> at(Vec3 p) {
        return () -> p;
    }

    // the bolt's frame: z from its root to its tip, x and y across (as three's lookAt)
    private static void frame(Bolt b) {
        Vec3 f = b.from.get(), t = b.to.get(), d = t.subtract(f);
        b.L = Math.max(0.05, d.length());
        Vec3 z = d.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 x = new Vec3(0, 1, 0).cross(z);
        if (x.lengthSqr() < 1e-8) {
            x = new Vec3(1, 0, 0);
        }
        x = x.normalize();
        b.o = f;
        b.z = z;
        b.x = x;
        b.y = z.cross(x);
    }

    // the page's boltPoint: the shader's path in the world, so a branch leaves from on top of its parent
    private static Vec3 point(Bolt b, double u) {
        double f = u * b.k, q = f - Math.floor(f), env = Math.sin(u * Math.PI);
        int i = (int) Math.floor(f);
        double zx = hz(b, i) + (hz(b, i + 1) - hz(b, i)) * q, zy = hz(b, i + 31) + (hz(b, i + 32) - hz(b, i + 31)) * q;
        double ox = zx * b.zig * (0.3 + 0.7 * env) + Math.sin(u * 6.2832 - b.ph) * b.wob * env;
        double oy = zy * b.zig * (0.3 + 0.7 * env) + 0.7 * Math.cos(u * 4.7 - b.ph * 1.3) * b.wob * env + b.bend * env;
        return b.o.add(b.x.scale(ox)).add(b.y.scale(oy)).add(b.z.scale(u * b.L));
    }

    private static double hz(Bolt b, double i) {
        double v = Math.sin(i * 91.7 + b.seed * 13.1) * 43758.5453;
        return (v - Math.floor(v)) * 2 - 1;
    }

    // ---------- where he is: points on his body, turned with him (the page's anchors) ----------

    private static final double[][] ANCHORS = {{0.33, 1.4, 0}, {0.38, 1.1, 0.05}, {0.38, 0.75, 0.05}, {0.15, 0.75, 0}, {0.12, 0.4, 0.05}, {0.18, 1.2, 0.14},
            {0.18, 1.2, -0.14}, {0.27, 1.6, 0}, {0.1, 0.1, 0.06}, {0.25, 1.3, -0.1}};

    private static Supplier<Vec3> anchor(Entity e, boolean self) {
        double[] a = ANCHORS[(int) (rnd() * ANCHORS.length)];
        // in first person the ones at his head would be in his face
        if (self && a[1] > 1.3) {
            a = ANCHORS[2];
        }
        double side = rnd() < 0.5 ? -1 : 1, jx = (rnd() - 0.5) * 0.08, jy = (rnd() - 0.5) * 0.1;
        double[] A = a;
        return () -> {
            float s = e instanceof LivingEntity l ? l.getScale() : 1;
            float yaw = e instanceof LivingEntity l ? Mth.lerp(partial, l.yBodyRotO, l.yBodyRot) : e.getYRot();
            double r = Math.toRadians(yaw), c = Math.cos(r), sn = Math.sin(r);
            double lx = (A[0] * side + jx) * s, ly = (A[1] + jy) * s, lz = A[2] * s;
            Vec3 p = e.getPosition(partial);
            // the body's right is -x turned by its yaw, its front +z turned
            return p.add(lx * -c - lz * sn, ly, -lx * sn + lz * c);
        };
    }

    private static float partial;

    private static double ground(Vec3 p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return p.y;
        }
        // (the client keeps MOTION_BLOCKING, never NO_LEAVES)
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
        return Math.abs(h - p.y) < 6 ? h : p.y;
    }

    // ---------- Gear 5: it leaks out of him ----------

    // every frame for a Luffy in G5 (LuffyFx); self = his own in first person
    static void leak(Entity e, double nowT, double dt, boolean self) {
        Leak lk = LEAKS.computeIfAbsent(e.getId(), i -> new Leak());
        lk.last = nowT;
        if (dt <= 0) {
            return;
        }
        double s = e instanceof LivingEntity l ? l.getScale() : 1;
        lk.crawl += dt * CRAWL;
        lk.arc += dt * ARC;
        lk.sky += dt * SKY;
        lk.air += dt * AIR;
        while (lk.crawl >= 1) {
            lk.crawl -= 1 + (rnd() - 0.5) * 0.6;
            if (!self) {
                bodyBolt(e, s);
            }
        }
        while (lk.arc >= 1) {
            lk.arc -= 1 + (rnd() - 0.5) * 0.8;
            groundArc(e, s, false, self);
        }
        while (lk.sky >= 1) {
            lk.sky -= 1 + (rnd() - 0.5) * 0.8;
            skyArc(e, s, self);
        }
        while (lk.air >= 1) {
            lk.air -= 1 + (rnd() - 0.5) * 0.8;
            airStreak(e, s, self);
        }
        lk.nextPulse -= dt;
        if (lk.nextPulse <= 0) {
            lk.nextPulse = PULSE * (0.8 + 0.4 * rnd());
            pulse(e, s, self);
        }
    }

    private static void bodyBolt(Entity e, double s) {
        Supplier<Vec3> a = anchor(e, false), b = null;
        Vec3 pa = a.get();
        for (int i = 0; i < 6 && b == null; i++) {
            Supplier<Vec3> c = anchor(e, false);
            double d = c.get().distanceTo(pa);
            if (d > 0.18 * s && d < 0.95 * s) {
                b = c;
            }
        }
        if (b != null) {
            bolt(a, b, 0.14 + 0.22 * rnd(), (0.016 + 0.018 * rnd()) * s, (0.06 + 0.08 * rnd()) * s, 3 + 4 * rnd(), (rnd() - 0.3) * 0.18 * s, 0.02 * rnd() * s, 20, 0.3, 0.4,
                    0.3, col(true));
        }
    }

    private static void groundArc(Entity e, double s, boolean burst, boolean self) {
        Supplier<Vec3> a = anchor(e, self);
        Vec3 p = e.position();
        double an = rnd() * Math.PI * 2, R = (1.8 + 4 * rnd()) * (burst ? 1.3 : 1) * s;
        Vec3 end0 = new Vec3(p.x + Math.cos(an) * R, 0, p.z + Math.sin(an) * R);
        Vec3 end = new Vec3(end0.x, ground(new Vec3(end0.x, p.y, end0.z)) + 0.01, end0.z);
        double th = burst ? 1.3 : 1;
        Bolt b = bolt(a, at(end), 0.3 + 0.35 * rnd(), (0.032 + 0.034 * rnd()) * th * s, (0.22 + 0.3 * rnd()) * s, 4 + 4 * rnd(), (0.3 + 0.7 * rnd()) * s, 0.03 * s, 10, 0.18,
                0.45, 0.2, col(true));
        if (b == null) {
            return;
        }
        b.onHit = q -> strike(q, burst ? 0.9 : 0.5, b.edge);
        // branches: from on top of the parent, each with a path of its own
        int nb = rnd() < 0.7 ? 1 + (int) (rnd() * (burst ? 3 : 2)) : 0;
        for (int i = 0; i < nb; i++) {
            double u = 0.3 + 0.45 * rnd();
            Vec3 side = new Vec3(rnd() - 0.5, -0.3 - 0.5 * rnd(), rnd() - 0.5).normalize().scale((0.8 + 1.4 * rnd()) * s);
            Vec3 base = point(b, u), to = base.add(side);
            Vec3 to2 = new Vec3(to.x, Math.max(ground(to) + 0.01, to.y), to.z);
            bolt(() -> point(b, u), at(to2), b.life * (0.6 + 0.3 * rnd()), b.r * (0.45 + 0.2 * rnd()), (0.1 + 0.15 * rnd()) * s, 3 + 4 * rnd(), 0.05 * rnd() * s, 0, 12, 0.25,
                    0.5, 0.3, new float[][] {b.edge, b.glow});
        }
    }

    private static void skyArc(Entity e, double s, boolean self) {
        Supplier<Vec3> a = anchor(e, self);
        Vec3 end = a.get().add(new Vec3((rnd() - 0.5) * 3.2, 1.8 + 3.2 * rnd(), (rnd() - 0.5) * 3.2).scale(s));
        bolt(a, at(end), 0.22 + 0.25 * rnd(), (0.026 + 0.024 * rnd()) * s, (0.2 + 0.3 * rnd()) * s, 4 + 4 * rnd(), (rnd() - 0.5) * 0.3 * s, 0, 14, 0.2, 0.6, 0.25, col(true));
    }

    // long bolts cutting the air all round him, 4-8 blocks (the Luffy x Doflamingo ref)
    private static void airStreak(Entity e, double s, boolean self) {
        Supplier<Vec3> a = anchor(e, self);
        double az = rnd() * Math.PI * 2, el = (rnd() - 0.35) * 0.9, R = (4 + 4 * rnd()) * s;
        Vec3 p = a.get();
        Vec3 end = p.add(Math.cos(az) * Math.cos(el) * R, Math.sin(el) * R, Math.sin(az) * Math.cos(el) * R);
        double g = ground(end);
        Vec3 end2 = new Vec3(end.x, Math.max(g + 0.05, end.y), end.z);
        Bolt b = bolt(a, at(end2), 0.3 + 0.3 * rnd(), (0.036 + 0.034 * rnd()) * s, (0.3 + 0.4 * rnd()) * s, 4 + 4 * rnd(), (rnd() - 0.5) * 1.2 * s, 0, 6, 0.14, 0.5, 0.12,
                col(true));
        if (b != null && end2.y < g + 0.1) {
            b.onHit = q -> strike(q, 0.8, b.edge);
        }
    }

    // the pressure wave: the ring over the ground with its white dust, a dark shell, a burst of bolts
    private static void pulse(Entity e, double s, boolean self) {
        Vec3 p = e.position();
        float[][] c = col(true);
        RINGS.add(new Ring(new Vec3(p.x, ground(p) + 0.04, p.z), now, (float) (rnd() * 10), c[0]));
        SHELLS.add(new Shell(p.add(0, 0.9 * s, 0), now, 0.6, 4.8 * s, 0.5, (float) (rnd() * 10), col(true)[0]));
        for (int i = 0; i < 4; i++) {
            groundArc(e, s, true, self);
            if (i % 2 == 0) {
                airStreak(e, s, self);
            }
        }
    }

    // ---------- every blow imbued (G4: red; G5: red and yellow) ----------

    // the fist of a rubber arm this frame (LuffyFx.drawArm): the haki winds round it
    static void wrap(Vec3 fist, Vec3 shoulder, boolean mix, double s) {
        if (wrapAcc < 0.035 * 20) {
            return;
        }
        wrapAcc = 0;
        Vec3 d = shoulder.subtract(fist);
        if (d.lengthSqr() < 1e-6) {
            return;
        }
        d = d.normalize();
        double back = (rnd() * 0.5 + 0.2) * s, sp = rnd() * Math.PI * 2;
        Vec3 side = new Vec3(Math.cos(sp), Math.sin(sp), 0).cross(d);
        Vec3 to = fist.add(d.scale(back)).add(side.lengthSqr() < 1e-6 ? Vec3.ZERO : side.normalize().scale(0.12 * s));
        bolt(at(fist), at(to), 0.1 + 0.08 * rnd(), (0.03 + 0.02 * rnd()) * s, 0.06 + 0.06 * rnd(), 6 + 5 * rnd(), 0, 0.05, 30, 0.25, 0.4, 0.2, col(mix));
    }

    // the blow lands: it hits without touching. A thick bolt closes the gap, the haki bursts on him in bolts, two shells,
    // the ground burnt under him (a = 1 G4's red, 2 G5's red and yellow)
    static void hit(Vec3 c, Vec3 dir, boolean mix, double s) {
        Vec3 d = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 hitP = c.subtract(d.scale(0.28 * s)), from = hitP.subtract(d.scale(1.2 * s));
        bolt(at(from), at(hitP), 0.26, 0.06 * s, 0.06 * s, 5, 0, 0, 8, 0.06, 0.5, 0.05, col(mix));
        int n = (mix ? 6 : 5) + (int) (rnd() * 4);
        for (int i = 0; i < n; i++) {
            double a = rnd() * Math.PI * 2, el = (rnd() - 0.3) * 1.4;
            Vec3 out = new Vec3(Math.cos(a) * Math.cos(el), Math.sin(el), Math.sin(a) * Math.cos(el)).add(d.scale(0.9)).normalize();
            Vec3 to = hitP.add(out.scale((1.4 + 3.2 * rnd()) * s));
            double g = ground(to);
            Vec3 to2 = new Vec3(to.x, Math.max(g + 0.01, to.y), to.z);
            Bolt b = bolt(at(hitP), at(to2), 0.24 + 0.24 * rnd(), (0.034 + 0.03 * rnd()) * s, (0.2 + 0.3 * rnd()) * s, 4 + 4 * rnd(), (rnd() - 0.5) * 0.3 * s, 0, 10, 0.15, 0.5,
                    0.2, col(mix));
            if (b != null && to2.y < g + 0.05) {
                b.onHit = q -> strike(q, 0.6, b.edge);
            }
        }
        float[] e = col(mix)[0];
        SHELLS.add(new Shell(hitP, now, 0.28, 0.9 * s, 1, (float) (rnd() * 10), e));
        SHELLS.add(new Shell(hitP, now, 0.4, 1.6 * s, 0.35, (float) (rnd() * 10), e));
        Vec3 under = new Vec3(c.x, ground(c) + 0.02, c.z);
        if (c.y - under.y < 3) {
            strike(under, 1, e);
        }
    }

    // ---------- weakened: on his knees, bent over, shaking ----------

    static void weak(int id, double until) {
        WEAK.put(id, until);
    }

    private static final java.util.Set<Integer> BENT = new java.util.HashSet<>();

    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (WEAK.isEmpty() || event.isCanceled()) {
            return;
        }
        LivingEntity e = event.getEntity();
        Double until = WEAK.get(e.getId());
        double t = ScreenFx.now();
        if (until == null || until < t) {
            return;
        }
        // eases in over half a second and out over the last
        double k = Math.min(1, Math.min((t - (until - 15)) / 10 + 1, (until - t) / 10));
        k = Math.max(0, Math.min(1, k));
        float h = e.getBbHeight();
        float yaw = Mth.lerp(event.getPartialTick(), e.yBodyRotO, e.yBodyRot);
        double j = 0.012 * k, ph = e.getId() * 1.7;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        BENT.add(e.getId());
        pose.translate(Math.sin(t * 3.55 + ph) * j, -0.18 * h * k, Math.cos(t * 2.95 + ph) * j);
        // bent forward about his feet, toward where he faces
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotation((float) (0.42 * k + 0.03 * Math.sin(t * 0.6 + ph) * k)));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (BENT.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    // ---------- burnt marks and stones ----------

    private static void strike(Vec3 p, double pow, float[] edge) {
        MARKS.add(new Mark(p, now, 0.1 + 0.2 * pow + 0.08 * rnd(), (float) (rnd() * 10), edge));
        if (MARKS.size() > 24) {
            MARKS.remove(0);
        }
        int n = (int) Math.round(2 + 4 * pow * (0.6 + 0.8 * rnd()));
        for (int i = 0; i < n && PEBBLES.size() < 64; i++) {
            Pebble q = new Pebble();
            double a = rnd() * Math.PI * 2, sp = 0.6 + 1.6 * rnd() * pow, sz = (0.03 + 0.06 * rnd()) * (0.7 + pow * 0.6);
            q.p = p.add(0, 0.02, 0);
            q.floor = p.y;
            q.v = new Vec3(Math.cos(a) * sp, 1.5 + 2.4 * rnd() * pow, Math.sin(a) * sp);
            q.born = now;
            q.life = 0.8 + 0.7 * rnd();
            q.sx = (float) (sz * (0.7 + 0.6 * rnd()));
            q.sy = (float) (sz * (0.6 + 0.5 * rnd()));
            q.sz = (float) (sz * (0.7 + 0.6 * rnd()));
            q.wx = (float) (rnd() * 12 - 6);
            q.wy = (float) (rnd() * 12 - 6);
            q.wz = (float) (rnd() * 12 - 6);
            q.col = rnd() > 0.5 ? 0x5F6B4A : 0x6D5A3E;
            PEBBLES.add(q);
        }
    }

    // ---------- every frame (from LuffyFx.render, after the bodies) ----------

    static void render(Vec3 camera, double nowTicks, float pt, double dt) {
        cam = camera;
        now = nowTicks;
        partial = pt;
        wrapAcc += dt * 20;
        double sec = dt;
        // the leaks of the ones no longer in G5
        LEAKS.values().removeIf(l -> nowTicks - l.last > 10);
        WEAK.values().removeIf(u -> u < nowTicks);
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            double age = (now - b.born) / 20;
            if (age >= b.life) {
                it.remove();
                continue;
            }
            b.ph += sec * b.spd;
            frame(b);
            double T = Math.min(1, age / b.life);
            double grow = Math.min(1, T / b.grow), tail = T < 1 - b.keep ? 0 : (T - (1 - b.keep)) / b.keep;
            if (!b.hit && grow >= 1) {
                b.hit = true;
                if (b.onHit != null) {
                    b.onHit.accept(b.to.get());
                }
            }
            // flicker: off for a moment in steps of 1/24 s, never at the start
            double st = Math.floor(age * 24 + b.seed * 7), v = Math.sin(st * 12.9898 + b.seed) * 43758.5453;
            float vis = T < 0.15 || v - Math.floor(v) > b.flick ? 1 : 0.15f;
            Matrix4f M = new Matrix4f((float) b.x.x, (float) b.x.y, (float) b.x.z, 0, (float) b.y.x, (float) b.y.y, (float) b.y.z, 0, (float) b.z.x, (float) b.z.y, (float) b.z.z,
                    0, (float) (b.o.x - cam.x), (float) (b.o.y - cam.y), (float) (b.o.z - cam.z), 1);
            for (int halo = 0; halo < 2; halo++) {
                LuffyMesh.P p = LuffyMesh.p(LuffyMesh.HAO);
                p.glow = false;
                p.seed = (float) b.seed;
                p.alpha = vis;
                p.p0 = new float[] {(float) b.L, (float) b.r, (float) b.zig, (float) b.ph};
                p.p1 = new float[] {(float) b.bend, (float) b.wob, (float) b.k, halo == 1 ? 3.6f : 1};
                p.p2 = new float[] {(float) grow, (float) tail, halo, 0};
                p.col = b.edge;
                p.col2 = b.glow;
                LuffyMesh.queue(halo == 1 ? LuffyMesh.Mesh.HALO : LuffyMesh.Mesh.HAO, M, p, LuffyMesh.BOTH, halo == 1 ? 18 : 19);
            }
        }
        MARKS.removeIf(m -> (now - m.born()) / 20 > 2.6);
        for (Mark m : MARKS) {
            double half = m.size() * 2.8;
            LuffyMesh.P p = LuffyMesh.p(LuffyMesh.SCORCH);
            p.glow = false;
            p.seed = m.seed();
            p.p0 = new float[] {(float) ((now - m.born()) / 20), (float) half, (float) m.size(), 0};
            p.col = m.edge();
            Matrix4f M = new Matrix4f().translate((float) (m.at().x - cam.x), (float) (m.at().y + 0.02 - cam.y), (float) (m.at().z - cam.z)).rotateX((float) (-Math.PI / 2))
                    .scale((float) half);
            LuffyMesh.queue(LuffyMesh.Mesh.QUAD, M, p, LuffyMesh.BOTH, 6);
        }
        RINGS.removeIf(r -> (now - r.born()) / 20 > 1.4);
        for (Ring r : RINGS) {
            float half = 22;
            LuffyMesh.P p = LuffyMesh.p(LuffyMesh.RING);
            p.glow = false;
            p.seed = r.seed();
            p.p0 = new float[] {(float) ((now - r.born()) / 20), half, 0, 0};
            p.col = r.edge();
            Matrix4f M = new Matrix4f().translate((float) (r.at().x - cam.x), (float) (r.at().y - cam.y), (float) (r.at().z - cam.z)).rotateX((float) (-Math.PI / 2)).scale(half);
            LuffyMesh.queue(LuffyMesh.Mesh.QUAD, M, p, LuffyMesh.BOTH, 7);
        }
        SHELLS.removeIf(s -> (now - s.born()) / 20 > s.life());
        for (Shell s : SHELLS) {
            double T = (now - s.born()) / 20 / s.life(), r = s.r() * (1 - Math.pow(1 - T, 3));
            LuffyMesh.P p = LuffyMesh.p(LuffyMesh.SHELL);
            p.glow = false;
            p.t = (float) (now / 20);
            p.seed = s.seed();
            p.p0 = new float[] {(float) (1 - T), 0, 0, 0};
            p.col = s.edge();
            Matrix4f M = new Matrix4f().translate((float) (s.at().x - cam.x), (float) (s.at().y - cam.y), (float) (s.at().z - cam.z)).scale((float) r, (float) (r * s.flat()),
                    (float) r);
            LuffyMesh.queue(LuffyMesh.Mesh.SPHERE, M, p, LuffyMesh.BOTH, 17);
        }
        for (Iterator<Pebble> it = PEBBLES.iterator(); it.hasNext(); ) {
            Pebble q = it.next();
            if ((now - q.born) / 20 > q.life) {
                it.remove();
                continue;
            }
            q.v = q.v.add(0, -9.8 * sec, 0);
            q.p = q.p.add(q.v.scale(sec));
            if (q.p.y < q.floor + 0.02) {
                q.p = new Vec3(q.p.x, q.floor + 0.02, q.p.z);
                q.v = new Vec3(q.v.x * 0.6, -q.v.y * 0.35, q.v.z * 0.6);
                q.wx *= 0.6f;
                q.wy *= 0.6f;
                q.wz *= 0.6f;
            }
            q.rx += q.wx * sec;
            q.ry += q.wy * sec;
            q.rz += q.wz * sec;
            double left = q.life - (now - q.born) / 20, k = Math.min(1, left / 0.25);
            LuffyMesh.P p = LuffyMesh.p(LuffyMesh.SOLID);
            p.glow = false;
            p.col = new float[] {(q.col >> 16 & 255) / 255f, (q.col >> 8 & 255) / 255f, (q.col & 255) / 255f};
            Matrix4f M = new Matrix4f().translate((float) (q.p.x - cam.x), (float) (q.p.y - cam.y), (float) (q.p.z - cam.z)).rotateXYZ(q.rx, q.ry, q.rz)
                    .scale((float) (q.sx * k), (float) (q.sy * k), (float) (q.sz * k));
            LuffyMesh.queue(LuffyMesh.Mesh.BOX, M, p, LuffyMesh.FRONT, 8);
        }
    }

    // a fist's world point from a camera-relative matrix (LuffyFx: the Giant's pieces are drawn small about the camera)
    static Vec3 world(Matrix4f m, float x, float y, float z, Vec3 camera, double scale) {
        Vector3f v = m.transformPosition(x, y, z, new Vector3f());
        return camera.add(v.x * scale, v.y * scale, v.z * scale);
    }
}
