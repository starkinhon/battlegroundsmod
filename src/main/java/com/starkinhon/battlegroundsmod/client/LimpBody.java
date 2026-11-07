package com.starkinhon.battlegroundsmod.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// A body out of its own control (Stark, 2026-11-07: the target of Gojo's Shift+Z came in "straight and stiff").
// Every limb is a pendulum on a spring that looks for the way the body "feels" pulled: gravity, the wind against the
// motion and the jerk of the acceleration. So whoever is dragged in face first trails arms, legs and head behind, and
// whoever is thrown backwards folds into a C with everything flung forward. Each limb has its own weight (heavy slow
// legs, light arms, a head that bobs more) and a loose sway on top, stronger the faster it goes. The server says the
// phase (ModPayloads.Limp): the pull, slow motion (everything at 0.2x), stuck on the fist (folded around the punch,
// jolted by each wave), the throw (tumbling loose in the air), lying on its back while stunned, and getting back up.
// The very same code runs in the Visualizador (forja/gojo-shift-ragdoll/limp.js): same constants, same order.
// Humanoid models (players, bosses, zombies...) get the limbs; every mob gets the whole-body tilt and tumble.
public final class LimpBody {
    // The phases the server sends (ModPayloads.Limp) plus LIE, which only the client knows (it landed).
    public static final int PULL = ModPayloads.Limp.PULL, SLOW = ModPayloads.Limp.SLOW, FIT = ModPayloads.Limp.FIT, THROW = ModPayloads.Limp.THROW,
            LIE = 4, OFF = ModPayloads.Limp.OFF, JOLT = ModPayloads.Limp.JOLT;
    private static final float G = 0.08f, KD = 0.9f, KA = 1.6f, SLOW_RATE = 0.2f;
    private static final int SUB = 4;
    // Angles (degrees): head, chest, right arm (forward, out), left arm, right leg, left leg, the whole body (tilt
    // forward, roll to the side), head to the side.
    private static final int N = 13;
    private static final float[] K = {0.30f, 0.22f, 0.32f, 0.32f, 0.30f, 0.30f, 0.18f, 0.18f, 0.17f, 0.17f, 0.10f, 0.10f, 0.24f};
    private static final float[] C = {0.16f, 0.36f, 0.28f, 0.30f, 0.27f, 0.30f, 0.38f, 0.40f, 0.36f, 0.40f, 0.32f, 0.32f, 0.18f};
    // How much of the loose sway each one takes.
    private static final float[] WOB = {0.9f, 0.35f, 1.0f, 0.7f, 1.0f, 0.7f, 0.55f, 0.3f, 0.55f, 0.3f, 0, 0, 0.8f};
    // Folded around the fist.
    private static final float[] FOLD = {38, 52, 72, 22, 58, 30, 42, 8, 26, 10, 10, 0, 0};
    private static final float DEG = Mth.DEG_TO_RAD;

    private static final class State {
        int phase = OFF, age, seed, air, lie = 24;
        float w, prevW, t, pw, prevPw;
        final float[] ang = new float[N], vel = new float[N], prev = new float[N];
        float[] tgt;
        double[] vPrev;
        Vec3 last;
    }

    private static final Map<Integer, State> BODIES = new HashMap<>();
    private static final Set<Integer> TILTED = new java.util.HashSet<>();
    // Models we moved parts of (shared by everyone drawn with them): put back before the next one is set up.
    private static final Set<HumanoidModel<?>> SHIFTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static Object lastLevel;

    private LimpBody() {
    }

    // The server says what happens to this body now.
    public static void event(int entityId, int phase, int ticks, float strength) {
        State s = BODIES.get(entityId);
        if (s == null) {
            if (phase == OFF || phase == JOLT) {
                return;
            }
            s = new State();
            s.seed = entityId;
            BODIES.put(entityId, s);
        }
        event(s, phase, ticks, strength);
    }

    private static void event(State s, int phase, int ticks, float strength) {
        if (phase == JOLT) {
            float k = strength;
            s.vel[1] += 7 * k;
            s.vel[0] += 10 * k;
            for (int i = 2; i <= 9; i++) {
                s.vel[i] += (hash(s.seed, s.age, i) - 0.5f) * (i < 6 ? 26 : 14) * k;
            }
            s.vel[10] -= 3 * k;
            return;
        }
        if (phase == THROW) {
            float k = 0.6f + 0.4f * strength;
            s.vel[10] -= 8 * k;
            s.vel[0] -= 22 * k;
            s.vel[1] += 12 * k;
            s.vel[2] += 18 * k;
            s.vel[4] += 18 * k;
            s.vel[6] += 14 * k;
            s.vel[8] += 14 * k;
            s.lie = ticks > 0 ? ticks : 24;
        }
        if (phase == LIE) {
            // After tumbling in the air: the same angle brought near zero, so it lies down the short way.
            float turns = Math.round(s.ang[10] / 360) * 360f;
            s.ang[10] -= turns;
            s.prev[10] -= turns;
        }
        if (s.phase == OFF && phase != OFF) {
            s.vPrev = null;
        }
        s.phase = phase;
        s.age = 0;
        s.air = 0;
    }

    public static void clear() {
        BODIES.clear();
        TILTED.clear();
    }

    public static boolean active(Entity entity) {
        return BODIES.containsKey(entity.getId());
    }

    // Every client tick (not while paused): each body moves on with how its entity moved.
    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            BODIES.clear();
            lastLevel = level;
        }
        if (level == null || BODIES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, State>> it = BODIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, State> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            State s = e.getValue();
            if (!(entity instanceof LivingEntity living) || entity.isRemoved()) {
                it.remove();
                continue;
            }
            if (!living.isAlive() && s.phase != OFF) {
                event(s, OFF, 0, 0);
            }
            Vec3 pos = entity.position();
            Vec3 d = s.last == null ? Vec3.ZERO : pos.subtract(s.last);
            s.last = pos;
            // In its own frame: right, up, forward (the way it faces).
            float yaw = entity.getYRot() * DEG;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
            double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
            double[] v = {d.x * rx + d.z * rz, d.y, d.x * fx + d.z * fz};
            if (!step(s, v, entity.onGround())) {
                it.remove();
            }
        }
    }

    // One tick. v = blocks per tick in its own frame: right, up, forward. False when it is over.
    private static boolean step(State s, double[] v, boolean onGround) {
        System.arraycopy(s.ang, 0, s.prev, 0, N);
        s.prevPw = s.pw;
        s.prevW = s.w;
        if (s.vPrev == null) {
            s.vPrev = v.clone();
        }
        double[] a = {v[0] - s.vPrev[0], v[1] - s.vPrev[1], v[2] - s.vPrev[2]};
        s.vPrev = v.clone();
        int p = s.phase;
        s.w = p == OFF ? Math.max(0, s.w - 0.125f) : Math.min(1, s.w + 0.34f);
        if (p == OFF && s.w <= 0) {
            return false;
        }
        float rate = p == SLOW ? SLOW_RATE : 1;
        s.t += rate;
        s.age++;
        double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        // What the body feels, in the frame of where it faces...
        double[] fw = {-v[0] * KD - a[0] * KA, -G - v[1] * KD - a[1] * KA, -v[2] * KD - a[2] * KA};
        // ...and in the frame of the tilted body (lying down, gravity goes to the back).
        double th = s.ang[10] * DEG, ct = Math.cos(th), st = Math.sin(th);
        double[] fb = {fw[0], fw[1] * ct + fw[2] * st, -fw[1] * st + fw[2] * ct};
        double nb = len(fb), nw = len(fw);
        double[] d = {fb[0] / nb, fb[1] / nb, fb[2] / nb};
        double[] dw = {fw[0] / nw, fw[1] / nw, fw[2] / nw};
        // Where the limbs fall (+ forward) and to the side (+ its right).
        float swing = (float) Math.toDegrees(Math.atan2(d[2], -d[1]));
        float side = (float) Math.toDegrees(Math.asin(Mth.clamp(d[0], -1, 1)));
        float[] tgt;
        if (p == SLOW && s.tgt != null) {
            tgt = s.tgt.clone();
        } else if (p == FIT) {
            tgt = FOLD.clone();
        } else {
            tgt = new float[N];
            tgt[0] = Mth.clamp(0.6f * swing, -60, 55);
            tgt[1] = Mth.clamp(0.3f * swing, -30, 45);
            tgt[2] = Mth.clamp(swing, -110, 175);
            tgt[3] = Mth.clamp(12 + side, -15, 85);
            tgt[4] = Mth.clamp(swing, -110, 175);
            tgt[5] = Mth.clamp(12 - side, -15, 85);
            tgt[6] = Mth.clamp(0.75f * swing, -70, 95);
            tgt[7] = Mth.clamp(6 + 0.4f * side, -10, 35);
            tgt[8] = Mth.clamp(0.75f * swing, -70, 95);
            tgt[9] = Mth.clamp(6 - 0.4f * side, -10, 35);
            // The whole body tips over towards where it is pulled from (the legs stay behind).
            tgt[10] = Mth.clamp(-0.45f * (float) Math.toDegrees(Math.atan2(dw[2], -dw[1])), -60, 60);
            tgt[11] = Mth.clamp(-30 * (float) dw[0], -25, 25);
            tgt[12] = 0;
            if (p == LIE) {
                tgt[10] = s.ang[10] >= 0 ? 90 : -90;
                tgt[11] = 0;
                // Lying down: the arms open and drop to the ground, the head falls to the side.
                tgt[3] = 55;
                tgt[5] = 48;
                tgt[2] = Mth.clamp(tgt[2], -20, 30);
                tgt[4] = Mth.clamp(tgt[4], -20, 30);
                tgt[12] = 25;
            }
            if (p == PULL) {
                s.tgt = tgt.clone();
            }
        }
        // The loose sway: stronger the faster it goes (in slow motion it floats slowly).
        float amp = p == FIT ? 5 : p == LIE ? 1.5f : p == SLOW ? 16 : 10 + 30 * Mth.clamp((float) speed / 1.5f, 0, 1);
        for (int i = 0; i < N; i++) {
            if (WOB[i] == 0) {
                continue;
            }
            float w1 = 0.35f + 0.5f * hash(s.seed, i, 1), w2 = 0.8f + 0.7f * hash(s.seed, i, 2);
            float p1 = 6.283f * hash(s.seed, i, 3), p2 = 6.283f * hash(s.seed, i, 4);
            tgt[i] += amp * WOB[i] * (0.6f * (float) Math.sin(s.t * w1 + p1) + 0.4f * (float) Math.sin(s.t * w2 + p2));
        }
        // In the air after the throw it tumbles loose; when it hits the ground, it lies down.
        boolean free = p == THROW;
        if (free) {
            s.air++;
            if (onGround && s.air > 3) {
                event(s, LIE, s.lie, 0);
            } else if (s.air > 80) {
                event(s, OFF, 0, 0);
            }
        } else if (p == LIE && s.age > s.lie) {
            event(s, OFF, 0, 0);
        } else if ((p == PULL || p == SLOW || p == FIT) && s.age > 120) {
            event(s, OFF, 0, 0);
        }
        // Lying on the ground (0 to 1): the body comes down until it touches.
        s.pw = Mth.clamp(s.pw + (p == LIE ? 0.25f : p == OFF ? -0.125f : -0.25f), 0, 1);
        float h = rate / SUB;
        for (int n = 0; n < SUB; n++) {
            for (int i = 0; i < N; i++) {
                float k = K[i], c = C[i];
                if (free && i == 10) {
                    k = 0;
                    c = 0.02f;
                }
                if (p == LIE && i == 10) {
                    k = 0.3f;
                    c = 0.5f;
                }
                s.vel[i] += (k * (tgt[i] - s.ang[i]) - c * s.vel[i]) * h;
                s.ang[i] += s.vel[i] * h;
            }
        }
        return true;
    }

    private static double len(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1.0E-9 ? 1 : l;
    }

    // Same as the Visualizador's limpHash (and FxWorld.hash).
    private static float hash(int a, int b, int c) {
        int h = a * 374761393 + b * 668265263 + c * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float weight(State s, float pt) {
        return Mth.clamp(s.prevW + (s.w - s.prevW) * pt, 0, 1);
    }

    private static float angle(State s, int i, float pt) {
        return s.prev[i] + (s.ang[i] - s.prev[i]) * pt;
    }

    // Before a humanoid model is set up for someone: whatever we moved for the last one drawn goes back.
    public static void beforeSetup(HumanoidModel<?> model) {
        if (!SHIFTED.remove(model)) {
            return;
        }
        model.body.setPos(0, 0, 0);
        model.head.setPos(0, 0, 0);
        model.rightArm.setPos(-5, 2, 0);
        model.leftArm.setPos(5, 2, 0);
        model.rightLeg.setPos(-1.9f, 12, 0);
        model.leftLeg.setPos(1.9f, 12, 0);
    }

    // After the model was set up (vanilla, the skill poses, everything): the limbs go loose, blended in and out.
    public static void pose(LivingEntity entity, HumanoidModel<?> model, float pt) {
        State s = BODIES.get(entity.getId());
        if (s == null || s.w <= 0) {
            return;
        }
        float w = weight(s, pt);
        float lean = angle(s, 1, pt) * DEG;
        float cos = Mth.cos(lean), sin = Mth.sin(lean);
        set(model.body, w, lean, 0, 0, 0, 12 - 12 * cos, -12 * sin);
        set(model.head, w, lean + angle(s, 0, pt) * DEG, angle(s, 12, pt) * DEG, 0, 0, 12 - 12 * cos, -12 * sin);
        set(model.rightArm, w, lean - angle(s, 2, pt) * DEG, 0, angle(s, 3, pt) * DEG, -5, 2 + 10 - 10 * cos, -10 * sin);
        set(model.leftArm, w, lean - angle(s, 4, pt) * DEG, 0, -angle(s, 5, pt) * DEG, 5, 2 + 10 - 10 * cos, -10 * sin);
        set(model.rightLeg, w, -angle(s, 6, pt) * DEG, 0, angle(s, 7, pt) * DEG, -1.9f, 12, 0);
        set(model.leftLeg, w, -angle(s, 8, pt) * DEG, 0, -angle(s, 9, pt) * DEG, 1.9f, 12, 0);
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> player) {
            player.leftSleeve.copyFrom(player.leftArm);
            player.rightSleeve.copyFrom(player.rightArm);
            player.jacket.copyFrom(player.body);
            player.leftPants.copyFrom(player.leftLeg);
            player.rightPants.copyFrom(player.rightLeg);
        }
        SHIFTED.add(model);
    }

    // From what the model had (weight 0) to the loose pose (weight 1).
    private static void set(ModelPart part, float w, float xRot, float yRot, float zRot, float x, float y, float z) {
        part.xRot = Mth.lerp(w, part.xRot, xRot);
        part.yRot = Mth.lerp(w, part.yRot, yRot);
        part.zRot = Mth.lerp(w, part.zRot, zRot);
        part.x = Mth.lerp(w, part.x, x);
        part.y = Mth.lerp(w, part.y, y);
        part.z = Mth.lerp(w, part.z, z);
        part.xScale = part.yScale = part.zScale = 1;
    }

    // The whole body tips over, rolls and tumbles about its middle (any mob), and lying down it comes to the ground.
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled() || BODIES.isEmpty()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        State s = BODIES.get(entity.getId());
        if (s == null || s.w <= 0) {
            return;
        }
        float pt = event.getPartialTick();
        float w = weight(s, pt);
        float pitch = angle(s, 10, pt) * w * DEG;
        float roll = angle(s, 11, pt) * w * DEG;
        float ground = s.prevPw + (s.pw - s.prevPw) * pt;
        float half = entity.getBbHeight() / 2;
        float thick = Math.min(half, entity.getBbWidth() / 2);
        float drop = (half - thick) * Math.abs(Mth.sin(pitch)) * ground;
        float yaw = Mth.rotLerp(pt, entity.yBodyRotO, entity.yBodyRot) * DEG;
        float sy = Mth.sin(yaw), cy = Mth.cos(yaw);
        // Tilting forward turns about its right side (the top goes the way it faces); rolling, about where it faces.
        Quaternionf q = new Quaternionf().rotationAxis(pitch, cy, 0, sy).rotateAxis(roll, -sy, 0, cy);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, half - drop, 0);
        pose.mulPose(q);
        pose.translate(0, -half, 0);
        TILTED.add(entity.getId());
    }

    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (TILTED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }
}
