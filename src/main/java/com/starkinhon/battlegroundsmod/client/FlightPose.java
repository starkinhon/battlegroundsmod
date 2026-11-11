package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.universe.Flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

// How a player on the Dragon Ball flight looks (forja voo/voo.html, approved by Stark 2026-11-10): standing up while
// floating, lying down into the cruise (the body following the path up and down), the right fist forward on the boost,
// the body turning round into the curves, and the poses taking turns by themselves (6 floating, 5 cruising, Stark: "varia
// mais as animações"), with the limbs swinging out in the turns, reaching forward in a dive and fluttering in the wind.
// The whole body is turned in RenderPlayerEvent.Pre (around the hips), the limbs after Minecraft's pose (PlayerModelMixin).
// The page's rig has knees, the player model does not: the shins are left out.
public final class FlightPose {
    private static final float PIVOT = 0.95f;

    static final class S {
        float weight, lean, bank, fist, pitch, yaw, yawRate, lagT, climb, shellA;
        double lastHead = Double.NaN;
        double time = -1;
        double px, py, pz;
        Vec3 vel = Vec3.ZERO;
        final Mixer hover = new Mixer(HOVER, 3), cruise = new Mixer(CRUISE, 4);
        float[] limbs;
        Quaternionf world = new Quaternionf();
    }

    private static final Map<Integer, S> STATES = new HashMap<>();
    private static Object lastLevel;
    private static boolean pushed;

    private FlightPose() {
    }

    private static float damp(float k, float dt) {
        return 1 - (float) Math.exp(-k * dt);
    }

    private static S get(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lastLevel) {
            STATES.clear();
            lastLevel = minecraft.level;
        }
        boolean flying = entity instanceof Player && FlightClient.flying(entity);
        S s = STATES.get(entity.getId());
        if (s == null) {
            if (!flying) {
                return null;
            }
            s = new S();
            s.yaw = (float) Math.toRadians(-entity.getYRot());
            s.px = entity.getX();
            s.py = entity.getY();
            s.pz = entity.getZ();
            STATES.put(entity.getId(), s);
        }
        return s;
    }

    // Walks the pose on (once per frame per player). Returns null when the player does not fly (anymore).
    static S update(Player player, float partial) {
        S s = get(player);
        if (s == null) {
            return null;
        }
        double now = (ScreenFx.now() + partial) / 20.0;
        if (s.time == now) {
            return s;
        }
        float dt = s.time < 0 ? 0.016f : (float) Mth.clamp(now - s.time, 0, 0.1);
        s.time = now;
        boolean flying = FlightClient.flying(player);
        int state = FlightClient.state(player);
        s.weight += ((flying ? 1 : 0) - s.weight) * damp(flying ? 10 : 7, dt);
        if (!flying && s.weight < 0.01f) {
            STATES.remove(player.getId());
            return null;
        }
        // speed: the local player's own, the others' from how they moved
        Vec3 local = player == Minecraft.getInstance().player ? FlightClient.velocity() : null;
        if (local != null) {
            s.vel = local;
        } else {
            double x = Mth.lerp(partial, player.xo, player.getX()), y = Mth.lerp(partial, player.yo, player.getY()), z = Mth.lerp(partial, player.zo, player.getZ());
            if (dt > 1.0E-4) {
                Vec3 seen = new Vec3((x - s.px) / dt, (y - s.py) / dt, (z - s.pz) / dt);
                if (seen.lengthSqr() < 120 * 120) {
                    s.vel = s.vel.lerp(seen, damp(8, dt));
                }
            }
            s.px = x;
            s.py = y;
            s.pz = z;
        }
        float boostTarget = local != null ? FlightClient.boostAmount() : (state & Flight.BOOST) != 0 ? 1 : 0;
        float strafe = local != null ? FlightClient.strafe() : 0;
        double v = s.vel.length();
        float c = FlightClient.cruise(player);
        float moving = flying ? Mth.clamp((float) (v - 2) / (c * 0.7f), 0, 1) : 0;
        s.lean += (moving - s.lean) * damp(4, dt);
        s.fist += (boostTarget - s.fist) * damp(6, dt);
        s.shellA += ((s.fist > 0.15f ? s.fist : 0) - s.shellA) * damp(8, dt);
        // the body points along the speed when moving, along the look when floating
        Vec3 dir = v > 1.5 ? s.vel.scale(1 / v) : horizontal(player.getViewVector(partial));
        float yawT = (float) Math.atan2(dir.x, dir.z);
        float dy = Mth.wrapDegrees((float) Math.toDegrees(yawT - s.yaw)) * Mth.DEG_TO_RAD;
        s.yaw += dy * damp(flying ? 5 : 10, dt);
        // leaning by the real turn: how far the flight's heading turned this frame
        double head = Math.atan2(s.vel.x, s.vel.z);
        double dh = Double.isNaN(s.lastHead) ? 0 : Mth.wrapDegrees(Math.toDegrees(head - s.lastHead)) * Mth.DEG_TO_RAD;
        s.lastHead = head;
        float yawRate = dt > 0 && v > 3 ? (float) (dh / dt) : dy * 3;
        s.yawRate += (Mth.clamp(yawRate, -4, 4) - s.yawRate) * damp(5, dt);
        float bankT = Mth.clamp(-s.yawRate * 0.55f * (0.2f + s.lean) - strafe * 0.5f * s.lean, -1.35f, 1.35f);
        s.bank += (bankT - s.bank) * damp(4, dt);
        s.climb = (float) Math.asin(Mth.clamp(dir.y, -1, 1));
        float pitchT = -s.lean * ((float) Math.PI / 2 - 0.12f) + s.lean * s.climb * 0.9f
                - (1 - s.lean) * Mth.clamp((float) s.vel.y * 0.02f, -0.2f, 0.2f);
        s.pitch += (pitchT - s.pitch) * damp(5, dt);
        if (!Float.isFinite(s.yaw + s.pitch + s.bank + s.yawRate)) {
            s.yaw = s.pitch = s.bank = s.yawRate = 0;
        }
        limbs(s, dt, (float) now, (float) v, c, flying);
        // the page's body turn (Euler YXZ: yaw, -pitch, bank) in Minecraft's frame (the model faces -z there)
        float twist = s.limbs[12];
        Quaternionf local3 = new Quaternionf().rotateY(twist).rotateX(s.pitch).rotateZ(-s.bank);
        s.world = new Quaternionf().rotateY(s.yaw + (float) Math.PI).mul(local3);
        return s;
    }

    private static Vec3 horizontal(Vec3 look) {
        Vec3 h = new Vec3(look.x, 0, look.z);
        return h.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : h.normalize();
    }

    // The feet -> head line of the flying body (for the shell and the trail), or null.
    public static Vec3 bodyUp(Player player, float partial) {
        S s = update(player, partial);
        if (s == null) {
            return null;
        }
        Vector3f up = s.world.transform(new Vector3f(0, 1, 0));
        return new Vec3(up.x, up.y, up.z);
    }

    // The same as the last frame drew it, without walking the pose on (for the trail's samples every tick).
    public static Vec3 lastUp(Player player) {
        S s = STATES.get(player.getId());
        if (s == null || s.weight < 0.01f) {
            return null;
        }
        Vector3f up = s.world.transform(new Vector3f(0, 1, 0));
        return new Vec3(up.x, up.y, up.z);
    }

    public static float shell(Player player, float partial) {
        S s = update(player, partial);
        return s == null ? 0 : s.shellA * s.weight;
    }

    public static float weight(Player player, float partial) {
        S s = update(player, partial);
        return s == null ? 0 : s.weight;
    }

    public static Vec3 velocity(Player player, float partial) {
        S s = update(player, partial);
        return s == null ? Vec3.ZERO : s.vel;
    }

    public static float boost(Player player, float partial) {
        S s = update(player, partial);
        return s == null ? 0 : s.fist;
    }

    public static float pivot() {
        return PIVOT;
    }

    // ---------- the whole body ----------

    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        pushed = false;
        Player player = event.getEntity();
        float partial = event.getPartialTick();
        S s = update(player, partial);
        if (s == null || s.weight < 0.001f) {
            return;
        }
        // Minecraft turns the body by its body yaw after this; take that off and put the flight's turn instead.
        float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Quaternionf vanilla = new Quaternionf().rotateY((float) Math.toRadians(180f - body));
        Quaternionf full = new Quaternionf(s.world).mul(new Quaternionf(vanilla).invert());
        Quaternionf q = new Quaternionf().slerp(full, s.weight);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, PIVOT, 0);
        pose.mulPose(q);
        pose.translate(0, -PIVOT, 0);
        pushed = true;
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (pushed) {
            event.getPoseStack().popPose();
            pushed = false;
        }
    }

    // ---------- the limbs (PlayerModelMixin, after Minecraft's pose) ----------

    public static void apply(LivingEntity entity, HumanoidModel<?> model) {
        S s = STATES.get(entity.getId());
        if (s == null || s.limbs == null || s.weight < 0.001f || !(entity instanceof Player) || SkillAnimations.busy(entity)) {
            return;
        }
        float w = s.weight;
        float[] q = s.limbs;
        // the page's rig faces +z, the model -z: sideways (z) turns and the head's yaw flip
        set(model.rightArm, q[0], 0, -q[1], w);
        set(model.leftArm, q[2], 0, -q[3], w);
        set(model.rightLeg, q[4], 0, -q[5], w);
        set(model.leftLeg, q[7], 0, -q[8], w);
        set(model.head, q[10], -q[11], 0, w);
        set(model.body, 0, 0, 0, w);
        model.hat.copyFrom(model.head);
    }

    private static void set(ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    // ---------- the poses (the page's library) ----------
    // [right arm x, z, left arm x, z, right leg x, z, right shin, left leg x, z, left shin, head x, head y, twist]

    private static final float[] BOOST = {-2.95f, -0.05f, 0.42f, 0.1f, 0.05f, 0.04f, 0.08f, 0.07f, -0.04f, 0.15f, -0.94f, 0, 0};

    private static float sin(double a) {
        return (float) Math.sin(a);
    }

    @SuppressWarnings("unchecked")
    private static final Function<Float, float[]>[] HOVER = new Function[] {
        // loose, arms open, legs hanging
        (Function<Float, float[]>) t -> new float[] {0.15f + sin(t * 2) * 0.06f, -0.32f, 0.12f + sin(t * 2 + 1) * 0.06f, 0.32f, 0.12f + sin(t * 1.7) * 0.08f, 0.04f,
            0.35f, -0.08f + sin(t * 1.7 + 2) * 0.08f, -0.04f, 0.2f, 0, 0, 0},
        // arms crossed, serious (Vegeta / Piccolo)
        (Function<Float, float[]>) t -> new float[] {-1.25f, 0.62f, -1.15f, -0.6f, 0.05f + sin(t * 1.3) * 0.04f, 0.06f, 0.18f, -0.02f, -0.06f, 0.32f, -0.08f,
            sin(t * 0.4) * 0.12f, 0},
        // fighting guard, fists up, one leg bent
        (Function<Float, float[]>) t -> new float[] {-1.0f + sin(t * 3) * 0.05f, 0.18f, -1.35f + sin(t * 3 + 1.5) * 0.05f, -0.12f, -0.45f, 0.05f, 0.9f, 0.18f, -0.05f,
            0.25f, -0.12f, 0.22f, 0.25f},
        // looking around, fists at the sides
        (Function<Float, float[]>) t -> new float[] {0.05f, -0.12f, 0.05f, 0.12f, 0.08f + sin(t * 1.5) * 0.05f, 0.03f, 0.25f, -0.05f + sin(t * 1.5 + 2) * 0.05f, -0.03f,
            0.4f, sin(t * 0.8) * 0.15f, sin(t * 0.55) * 0.8f, sin(t * 0.55) * 0.15f},
        // one leg drawn up, hand on the hip, at ease
        (Function<Float, float[]>) t -> new float[] {0.3f, -0.75f, 0.1f + sin(t * 1.8) * 0.08f, 0.2f, -0.95f, 0.12f, 1.5f, 0.1f + sin(t * 1.4) * 0.06f, -0.05f, 0.15f,
            0.05f, -0.2f, -0.12f},
    };

    @SuppressWarnings("unchecked")
    private static final Function<Float, float[]>[] CRUISE = new Function[] {
        // the classic: arms back along the body, one leg bent
        (Function<Float, float[]>) t -> new float[] {0.25f, -0.12f, 0.35f, 0.15f, 0.05f, 0.04f, 0.08f, 0.32f, -0.04f, 0.75f, -1.1f, 0, 0},
        // Superman: both fists forward
        (Function<Float, float[]>) t -> new float[] {-2.9f, 0.12f, -2.85f, -0.12f, 0.04f, 0.05f, 0.05f, 0.08f, -0.05f, 0.12f, -0.9f, 0, 0},
        // one fist forward, the other back, legs together
        (Function<Float, float[]>) t -> new float[] {-2.85f, -0.08f, 0.5f, 0.18f, 0.02f, 0.02f, 0.05f, 0.06f, -0.02f, 0.1f, -0.95f, 0, 0.12f},
        // gliding with the arms open
        (Function<Float, float[]>) t -> new float[] {-0.15f, -1.25f + sin(t * 1.2) * 0.06f, -0.15f, 1.25f + sin(t * 1.2 + 1) * 0.06f, 0.05f, 0.16f, 0.12f, 0.05f, -0.16f,
            0.12f, -1.05f, 0, 0},
        // arms crossed, taking it easy (legs apart: crossed they turned into one leg seen from behind)
        (Function<Float, float[]>) t -> new float[] {-1.15f, 0.6f, -1.05f, -0.58f, 0.04f, 0.07f, 0.08f, 0.3f, -0.07f, 0.7f, -1.15f, sin(t * 0.6) * 0.1f, 0},
    };

    // One pose after the other: each held 3.5 to 7.5 s, then 0.9 s of blending into another one (never the same).
    static final class Mixer {
        final Function<Float, float[]>[] lib;
        int cur, prev;
        float k = 1, hold;

        Mixer(Function<Float, float[]>[] lib, float hold) {
            this.lib = lib;
            this.hold = hold;
        }

        float[] at(float t, float dt, float rate) {
            hold -= dt * rate;
            if (hold <= 0 && k >= 1) {
                int n = cur;
                while (lib.length > 1 && n == cur) {
                    n = (int) (Math.random() * lib.length);
                }
                prev = cur;
                cur = n;
                k = 0;
                hold = 3.5f + (float) Math.random() * 4;
            }
            k = Math.min(1, k + dt / 0.9f);
            float e = k * k * (3 - 2 * k);
            float[] a = lib[prev].apply(t), b = lib[cur].apply(t);
            float[] out = new float[a.length];
            for (int i = 0; i < a.length; i++) {
                out[i] = Mth.lerp(e, a[i], b[i]);
            }
            return out;
        }
    }

    private static void limbs(S s, float dt, float t, float v, float c, boolean flying) {
        float lf = s.lean, fi = s.fist;
        float[] hv = s.hover.at(t, dt, flying ? 1 : 0.5f);
        float[] cr = s.cruise.at(t, dt, 1);
        float[] q = new float[13];
        for (int i = 0; i < 13; i++) {
            q[i] = Mth.lerp(fi, Mth.lerp(lf, hv[i], cr[i]), BOOST[i]);
        }
        // reacting to the flight
        float turn = Mth.clamp(s.yawRate / 3, -1, 1) * lf;
        float dive = Mth.clamp(-s.climb / 1.1f, -1, 1) * lf;
        s.lagT += (turn - s.lagT) * damp(3, dt);
        float flut = sin(t * 13) * 0.05f * Mth.clamp(v / (c * 1.6f), 0, 1) * lf;
        float breath = sin(t * 1.9) * 0.05f * (1 - lf);
        q[1] += -Math.max(s.lagT, 0) * 0.6f * (1 - fi);
        q[3] += Math.max(-s.lagT, 0) * 0.6f * (1 - fi);
        q[5] += -s.lagT * 0.25f;
        q[8] += -s.lagT * 0.25f;
        q[12] += -s.lagT * 0.35f;
        q[0] += -dive * 0.7f * (1 - fi);
        q[2] += -dive * 0.55f * (1 - fi);
        q[10] += dive * 0.25f;
        q[0] += breath;
        q[2] += breath * 0.8f;
        q[4] += flut * 0.5f;
        q[7] -= flut * 0.5f;
        s.limbs = q;
    }
}
