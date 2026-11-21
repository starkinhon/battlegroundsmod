// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// The fighting guard (Defesa), remade 2026-11-21 from the approved page forja/guarda/guarda.html (v4). Same numbers.
// Fist first (#480): each pose says where the fist goes and the arm angles, and the shoulder pivot is worked out from
// them, so the top of the arm stands in for a bent elbow. Left foot forward, right shoulder back, chin down.
// Raising, lowering and the block's recovery run through a critically damped spring (no jolt even when one state cuts
// another); the block's impact, the perfect defense and the guard break are dry, without it.
public final class GuardAnim {
    // Parts, each x, y, z (pixels) and xRot, yRot, zRot (degrees), absolute ModelPart values.
    private static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    private static final int N = 36;
    private static final float D = (float) (Math.PI / 180);

    public static final int BLOCK = 0, PARRY = 1, BREAK = 2;

    // Spring strength per tick: 0.55 settles in about 8 ticks.
    private static final double OMEGA = 0.55;
    private static final int RAISE = 18, LOWER = 16, BLOCK_LEN = 24, BLOCK_DRY = 4, PARRY_LEN = 12, BREAK_LEN = 20;
    // The walk keeps the vanilla leg swing under the guard, at this share.
    private static final float WALK_LEGS = 0.6f;

    private static final float[] GUARD, GUARD_OVER, GUARD_BREATH, BLOCK_POSE, PARRY_POSE, BROKEN;

    private record Stance(float lean, float twist, float drop, float head, float[] rFist, float[] rPiv, float[] rAng,
            float[] lFist, float[] lPiv, float[] lAng, float[] rLeg, float[] lLeg, float rLegZ, float lLegZ) {
        Stance with(float lean, float drop, float head, float twist) {
            return new Stance(lean, twist, drop, head, rFist, rPiv, rAng, lFist, lPiv, lAng, rLeg, lLeg, rLegZ, lLegZ);
        }

        Stance fists(float[] rFist, float[] rAng, float[] lFist, float[] lAng) {
            return new Stance(lean, twist, drop, head, rFist, null, rAng, lFist, null, lAng, rLeg, lLeg, rLegZ, lLegZ);
        }

        Stance legs(float[] rLeg, float[] lLeg) {
            return new Stance(lean, twist, drop, head, rFist, rPiv, rAng, lFist, lPiv, lAng, rLeg, lLeg, rLegZ, lLegZ);
        }
    }

    static {
        Stance guard = new Stance(8, 18, 0.8f, 12, v(-3.3f, -0.6f, -5.8f), null, v(-168, 0, 17), v(2.5f, -2.2f, -9.0f), null,
                v(-156, 0, -14), v(17, 4), v(-21, -5), 0.6f, -0.6f);
        GUARD = stance(guard);
        GUARD_OVER = stance(guard.with(11, 1.1f, 12, 18).fists(v(-2.2f, -3.9f, -6.9f), guard.rAng(), v(1.2f, -5.6f, -10.8f), guard.lAng()));
        GUARD_BREATH = stance(guard.with(9, 1.0f, 12, 18).fists(v(-2.2f, -2.8f, -6.5f), guard.rAng(), v(1.3f, -4.4f, -9.8f), guard.lAng()));
        BLOCK_POSE = stance(guard.with(3, 1.2f, 20, 18).fists(v(-3.0f, -1.2f, -4.4f), v(-172, 0, 19), v(2.0f, -2.6f, -6.8f), v(-164, 0, -16))
                .legs(v(24, 4), v(-18, -5)));
        PARRY_POSE = stance(guard.with(5, 0.8f, 10, 30).fists(v(-3.0f, -0.8f, -7.6f), v(-160, 0, 14), v(5.0f, -4.6f, -9.4f), v(-166, -10, -30))
                .legs(v(19, 4), v(-24, -6)));
        BROKEN = stance(new Stance(-13, -6, 0.3f, -18, null, v(-5, 2, 0.8f), v(-128, 8, 34), null, v(5, 2, 0.8f), v(-120, -6, -40),
                v(26, 6), v(-12, -6), 0, 0));
    }

    private static final class State {
        boolean on;
        double onAt = -1.0E9;
        double offAt = -1.0E9;
        double hitAt = -1.0E9;
        int hit = -1;
        float[] x;
        float[] v;
        double last = Double.NaN;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private GuardAnim() {
    }

    // GuardPose from the server: the guard went up or down.
    public static void guard(int entityId, boolean on) {
        State s = STATES.computeIfAbsent(entityId, id -> new State());
        if (s.on == on) {
            return;
        }
        s.on = on;
        double now = now(entityId);
        if (on) {
            s.onAt = now;
            s.hit = -1;
        } else {
            s.offAt = now;
        }
    }

    // GuardHit from the server: the guard blocked, parried or broke.
    public static void hit(int entityId, int kind) {
        State s = STATES.computeIfAbsent(entityId, id -> new State());
        s.hit = kind;
        s.hitAt = now(entityId);
        if (kind == BREAK) {
            s.on = false;
            s.offAt = s.hitAt;
        }
    }

    public static void forget(int entityId) {
        STATES.remove(entityId);
    }

    public static void clear() {
        STATES.clear();
    }

    // Whether the guard (up, being lowered or breaking) poses this entity at this time.
    public static boolean active(Entity entity, float ageInTicks) {
        State s = STATES.get(entity.getId());
        if (s == null) {
            return false;
        }
        if (s.on) {
            return true;
        }
        // (raw far below 0: an older clock of this id, a body that died and respawned)
        double since = ageInTicks - (s.hit == BREAK ? s.hitAt : s.offAt);
        boolean live = since >= -1 && since < (s.hit == BREAK ? BREAK_LEN : LOWER + 8);
        if (!live) {
            STATES.remove(entity.getId());
        }
        return live;
    }

    // Poses the model after vanilla set it up. The head keeps following the aim.
    public static void apply(LivingEntity entity, HumanoidModel<?> model, float ageInTicks) {
        State s = STATES.get(entity.getId());
        float lookX = model.head.xRot;
        float lookY = model.head.yRot;
        float legR = model.rightLeg.xRot;
        float legL = model.leftLeg.xRot;
        float[] rest = read(model);
        // the aim is added at the end, for every pose alike
        rest[HEAD * 6 + 3] = 0;
        rest[HEAD * 6 + 4] = 0;
        rest[HEAD * 6 + 5] = 0;
        // and the walk's leg swing too, at a share that depends on how far up the guard is
        rest[RIGHT_LEG * 6 + 3] = 0;
        rest[LEFT_LEG * 6 + 3] = 0;

        float[] target;
        double omega = OMEGA;
        float guardWeight = 1;
        if (s.hit == BREAK && !s.on) {
            double t = ageInTicks - s.hitAt;
            target = track(t, rest, k(0, GUARD, 0), k(2, BROKEN, OUT), k(8, BROKEN, IO), k(BREAK_LEN, rest, IO));
            omega = 0;
            guardWeight = (float) Math.max(0, 1 - t / BREAK_LEN);
        } else if (s.on) {
            double t = ageInTicks - s.onAt;
            target = t < RAISE ? track(t, rest, k(0, rest, 0), k(10, GUARD_OVER, OUT), k(RAISE, GUARD, IO))
                    : track((t - RAISE) % 24, rest, k(0, GUARD, 0), k(11, GUARD_BREATH, IO), k(24, GUARD, IO));
            if (t < RAISE) {
                guardWeight = (float) (t / RAISE);
            }
            double h = ageInTicks - s.hitAt;
            if (s.hit == BLOCK && h >= 0 && h < BLOCK_LEN) {
                target = track(h, rest, k(0, GUARD, 0), k(1, BLOCK_POSE, CUT), k(BLOCK_DRY, BLOCK_POSE, IO), k(BLOCK_LEN, GUARD, IO));
                omega = h < BLOCK_DRY ? 0 : OMEGA;
            } else if (s.hit == PARRY && h >= 0 && h < PARRY_LEN) {
                target = track(h, rest, k(0, GUARD, 0), k(2, PARRY_POSE, OUT), k(5, PARRY_POSE, IO), k(PARRY_LEN, GUARD, IO));
                omega = 0;
            }
        } else {
            double t = ageInTicks - s.offAt;
            target = track(t, rest, k(0, GUARD, 0), k(LOWER, rest, IO));
            guardWeight = (float) Math.max(0, 1 - t / LOWER);
        }

        float[] pose = follow(s, target, ageInTicks, omega);
        write(model, pose);
        model.head.xRot += lookX;
        model.head.yRot += lookY;
        // walking: the vanilla leg swing goes on under the guard, smaller
        float share = 1 - (1 - WALK_LEGS) * Math.min(1, Math.max(0, guardWeight));
        model.rightLeg.xRot += legR * share;
        model.leftLeg.xRot += legL * share;
        model.hat.copyFrom(model.head);
    }

    // ---- spring ----

    // Critically damped: the pose follows the target without overshooting; omega 0 follows it exactly.
    private static float[] follow(State s, float[] target, float age, double omega) {
        double dt = Double.isNaN(s.last) ? 0 : age - s.last;
        s.last = age;
        if (s.x == null || omega <= 0 || dt <= 0 || dt > 5) {
            // (dt <= 0: the same frame drawn again, or an older clock; dt > 5: not drawn for a while)
            if (s.x == null || omega <= 0 || dt > 5 || dt < -1) {
                s.x = target.clone();
                s.v = new float[N];
            }
            return s.x;
        }
        double e = Math.exp(-omega * dt);
        for (int i = 0; i < N; i++) {
            double y = s.x[i] - target[i];
            double tmp = (s.v[i] + omega * y) * dt;
            s.v[i] = (float) ((s.v[i] - omega * tmp) * e);
            s.x[i] = (float) (target[i] + (y + tmp) * e);
        }
        return s.x;
    }

    // ---- keyframes ----

    private static final int CUT = 0, OUT = 1, IO = 2;

    private record Key(double tick, float[] pose, int ease) {
    }

    private static Key k(double tick, float[] pose, int ease) {
        return new Key(tick, pose, ease);
    }

    private static float[] track(double t, float[] rest, Key... keys) {
        if (t <= keys[0].tick()) {
            return keys[0].pose().clone();
        }
        for (int i = 1; i < keys.length; i++) {
            Key a = keys[i - 1];
            Key b = keys[i];
            if (t <= b.tick()) {
                double u = (t - a.tick()) / Math.max(1.0E-6, b.tick() - a.tick());
                return mix(a.pose(), b.pose(), (float) ease(b.ease(), u));
            }
        }
        return keys[keys.length - 1].pose().clone();
    }

    private static double ease(int kind, double t) {
        return switch (kind) {
            case OUT -> 1 - (1 - t) * (1 - t) * (1 - t);
            case IO -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            default -> t;
        };
    }

    private static float[] mix(float[] a, float[] b, float t) {
        float[] o = new float[N];
        for (int i = 0; i < N; i++) {
            o[i] = a[i] + (b[i] - a[i]) * t;
        }
        return o;
    }

    // ---- model ----

    private static ModelPart[] parts(HumanoidModel<?> model) {
        return new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }

    private static float[] read(HumanoidModel<?> model) {
        float[] o = new float[N];
        ModelPart[] parts = parts(model);
        for (int i = 0; i < 6; i++) {
            ModelPart p = parts[i];
            o[i * 6] = p.x;
            o[i * 6 + 1] = p.y;
            o[i * 6 + 2] = p.z;
            o[i * 6 + 3] = p.xRot / D;
            o[i * 6 + 4] = p.yRot / D;
            o[i * 6 + 5] = p.zRot / D;
        }
        return o;
    }

    private static void write(HumanoidModel<?> model, float[] pose) {
        ModelPart[] parts = parts(model);
        for (int i = 0; i < 6; i++) {
            ModelPart p = parts[i];
            p.setPos(pose[i * 6], pose[i * 6 + 1], pose[i * 6 + 2]);
            p.setRotation(pose[i * 6 + 3] * D, pose[i * 6 + 4] * D, pose[i * 6 + 5] * D);
        }
    }

    // ---- pose math (same as the page) ----

    // ModelPart turns by Z * Y * X.
    private static float[] rotZYX(float x, float y, float z, float rx, float ry, float rz) {
        double cx = Math.cos(rx * D), sx = Math.sin(rx * D), cy = Math.cos(ry * D), sy = Math.sin(ry * D);
        double cz = Math.cos(rz * D), sz = Math.sin(rz * D);
        double y1 = y * cx - z * sx, z1 = y * sx + z * cx;
        double x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
        double x3 = x2 * cz - y1 * sz, y3 = x2 * sz + y1 * cz;
        return new float[] {(float) x3, (float) y3, (float) z2};
    }

    // Fist first: where the fist goes plus the arm angles give the shoulder pivot. side: 1 right arm, -1 left.
    private static void armAt(float[] out, int part, int side, float fx, float fy, float fz, float[] ang) {
        float[] hand = rotZYX(-side, 9, 0, ang[0], ang[1], ang[2]);
        set(out, part, fx - hand[0], fy - hand[1], fz - hand[2], ang[0], ang[1], ang[2]);
    }

    // Leans at the hips (neck and shoulders go with it), turns the chest, lowers the body by drop.
    private static float[] stance(Stance s) {
        float[] o = new float[N];
        double lean = s.lean() * D;
        float dy = (float) (12 - 12 * Math.cos(lean)) + s.drop();
        float dz = (float) (-12 * Math.sin(lean));
        set(o, BODY, 0, dy, dz, s.lean(), s.twist(), 0);
        set(o, HEAD, 0, dy, dz, s.head(), 0, 0);
        if (s.rFist() != null) {
            armAt(o, RIGHT_ARM, 1, s.rFist()[0], s.rFist()[1] + dy, s.rFist()[2] + dz, s.rAng());
        } else {
            set(o, RIGHT_ARM, s.rPiv()[0], s.rPiv()[1] + dy, s.rPiv()[2] + dz, s.rAng()[0], s.rAng()[1], s.rAng()[2]);
        }
        if (s.lFist() != null) {
            armAt(o, LEFT_ARM, -1, s.lFist()[0], s.lFist()[1] + dy, s.lFist()[2] + dz, s.lAng());
        } else {
            set(o, LEFT_ARM, s.lPiv()[0], s.lPiv()[1] + dy, s.lPiv()[2] + dz, s.lAng()[0], s.lAng()[1], s.lAng()[2]);
        }
        set(o, RIGHT_LEG, -1.9f, 12 + s.drop(), s.rLegZ(), s.rLeg()[0], 0, s.rLeg()[1]);
        set(o, LEFT_LEG, 1.9f, 12 + s.drop(), s.lLegZ(), s.lLeg()[0], 0, s.lLeg()[1]);
        return o;
    }

    private static void set(float[] o, int part, float x, float y, float z, float rx, float ry, float rz) {
        int i = part * 6;
        o[i] = x;
        o[i + 1] = y;
        o[i + 2] = z;
        o[i + 3] = rx;
        o[i + 4] = ry;
        o[i + 5] = rz;
    }

    private static float[] v(float... values) {
        return values;
    }

    // The entity's clock now (the same one ageInTicks counts at render).
    private static double now(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        return entity == null ? -1.0E9 : entity.tickCount + mc.getTimer().getGameTimeDeltaPartialTick(false);
    }
}
