// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;

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
// The limbs were remade on 2026-11-21 (forja/voo-poses, approved by Stark: "perfeito, pode tar portando"): whole poses
// in absolute numbers (FlightPoseData), fist first and foot first, three floating poses with little moves now and then
// (stretching, a spin in the air, scratching the head, two punches), a brake when it stops from a cruise and the
// approved boxing guard in the combat flight.
public final class FlightPose {
    private static final float PIVOT = 0.95f;

    static final class S {
        float weight, lean, bank, fist, pitch, yaw, yawRate, lagT, climb, shellA, wave;
        // the brake (weight and time left), the whole body's own turn (the spin, leaning into the turns)
        float brake, brakeT, twist;
        // the move while floating: which one (-1 none), from which floating pose, when it started, if it already played in this hold
        int clip = -1, clipBase, lastClip = -1;
        float clipStart;
        boolean clipDone;
        // the combat flight: how much in it, the speed ahead / to the right / up seen from the facing, the guard
        float cw, vf, vs, vu, guard;
        double lastHead = Double.NaN;
        double time = -1;
        double px, py, pz;
        Vec3 vel = Vec3.ZERO;
        final Mixer hover = new Mixer(FlightPoseData.HOVER, HOVER_EVERY), cruise = new Mixer(FlightPoseData.CRUISE, 40);
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
        boolean flying = FlightClient.flying(entity);
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

    // Walks the pose on (once per frame per flier: players and the flying souls of boss/AirCombat). Returns null when it
    // does not fly (anymore).
    static S update(LivingEntity player, float partial) {
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
        float boostTarget = local != null ? Math.max(FlightClient.boostAmount(), AirFightClient.dashing() != null ? 1 : 0)
                : (state & Flight.BOOST) != 0 ? 1 : 0;
        boolean combat = flying && AirFightClient.combat(player);
        s.cw += ((combat ? 1 : 0) - s.cw) * damp(6, dt);
        s.guard += ((combat && SkillAnimations.guarding(player) ? 1 : 0) - s.guard) * damp(14, dt);
        float strafe = local != null ? FlightClient.strafe() : 0;
        double v = s.vel.length();
        float c = FlightClient.cruise(player);
        float moving = flying ? Mth.clamp((float) (v - 2) / (c * 0.7f), 0, 1) : 0;
        // Freeza floating by stands up the whole way; a soul throwing a blow stands up for it (the punch reads right).
        if ((state & Flight.UPRIGHT) != 0 || !(player instanceof Player) && SkillAnimations.busy(player)) {
            moving = 0;
        }
        // Combat: standing in the stance; on the dash leaning into it a little (the page's 0.3).
        if (s.cw > 0.5) {
            moving = boostTarget > 0.5 ? 0.3f : 0;
        }
        boolean crossed = (state & Flight.CROSSED) != 0;
        // the floating guard (2) is left out of the floating poses: the guard is for the combat mode, which has its
        // own stance (Stark 2026-11-21: "a de guarda fica só no modo de combate ativo")
        s.hover.skip = 2;
        s.hover.forced = crossed ? 1 : -1;
        s.cruise.forced = crossed ? 4 : -1;
        s.wave += (((state & Flight.WAVE) != 0 ? 1 : 0) - s.wave) * damp(8, dt);
        // the brake: stopping from a cruise stands the body up for a moment (about a second), then it floats
        if (flying && s.cw < 0.5f && s.lean > 0.6f && moving < 0.1f && boostTarget < 0.5f) {
            s.brakeT = 1.2f;
        }
        s.brakeT = Math.max(0, s.brakeT - dt);
        float brakeTarget = s.brakeT > 0 ? 1 : 0;
        s.brake += (brakeTarget - s.brake) * damp(brakeTarget > 0 ? 9 : 3, dt);
        s.lean += (moving - s.lean) * damp(s.brakeT > 0 ? 7 : 4, dt);
        s.fist += (boostTarget - s.fist) * damp(6, dt);
        s.shellA += ((s.fist > 0.15f ? s.fist : 0) - s.shellA) * damp(8, dt);
        // the body points along the speed when moving, along the look when floating
        Vec3 dir = v > 1.5 && s.cw < 0.5 ? s.vel.scale(1 / v) : horizontal(player.getViewVector(partial));
        {
            // the speed seen from the facing, for the stance's lean and the limbs (combat)
            Vec3 face = horizontal(player.getViewVector(partial));
            Vec3 side = new Vec3(-face.z, 0, face.x);
            s.vf += ((float) s.vel.dot(face) - s.vf) * damp(6, dt);
            s.vs += ((float) s.vel.dot(side) - s.vs) * damp(6, dt);
            s.vu += ((float) s.vel.y - s.vu) * damp(6, dt);
        }
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
        // braking: the body stands up and leans back a little
        pitchT = pitchT * (1 - s.brake) + s.brake * 0.32f * (1 - s.lean);
        if (s.cw > 0.001f) {
            // the page's combat tilt: a little into where it goes (0.22 rad at 20 b/s), never lying down
            float tF = Mth.clamp(s.vf / 20, -1, 1) * 0.22f;
            float tS = Mth.clamp(s.vs / 20, -1, 1) * 0.22f;
            pitchT = Mth.lerp(s.cw, pitchT, -s.lean * ((float) Math.PI / 2 - 0.12f) - tF);
            s.bank += (Mth.lerp(s.cw, bankT, -tS) - bankT) * damp(4, dt);
        }
        s.pitch += (pitchT - s.pitch) * damp(5, dt);
        if (!Float.isFinite(s.yaw + s.pitch + s.bank + s.yawRate)) {
            s.yaw = s.pitch = s.bank = s.yawRate = 0;
        }
        limbs(s, dt, (float) now, (float) v, c, flying);
        // the page's body turn (Euler YXZ: yaw, -pitch, bank) in Minecraft's frame (the model faces -z there)
        float twist = s.twist;
        Quaternionf local3 = new Quaternionf().rotateY(twist).rotateX(s.pitch).rotateZ(-s.bank);
        s.world = new Quaternionf().rotateY(s.yaw + (float) Math.PI).mul(local3);
        return s;
    }

    private static Vec3 horizontal(Vec3 look) {
        Vec3 h = new Vec3(look.x, 0, look.z);
        return h.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : h.normalize();
    }

    // The feet -> head line of the flying body (for the shell and the trail), or null.
    public static Vec3 bodyUp(LivingEntity player, float partial) {
        S s = update(player, partial);
        if (s == null) {
            return null;
        }
        Vector3f up = s.world.transform(new Vector3f(0, 1, 0));
        return new Vec3(up.x, up.y, up.z);
    }

    // The same as the last frame drew it, without walking the pose on (for the trail's samples every tick).
    public static Vec3 lastUp(LivingEntity player) {
        S s = STATES.get(player.getId());
        if (s == null || s.weight < 0.01f) {
            return null;
        }
        Vector3f up = s.world.transform(new Vector3f(0, 1, 0));
        return new Vec3(up.x, up.y, up.z);
    }

    public static float shell(LivingEntity player, float partial) {
        S s = update(player, partial);
        return s == null ? 0 : s.shellA * s.weight;
    }

    public static float weight(LivingEntity player, float partial) {
        S s = update(player, partial);
        return s == null ? 0 : s.weight;
    }

    public static Vec3 velocity(LivingEntity player, float partial) {
        S s = update(player, partial);
        return s == null ? Vec3.ZERO : s.vel;
    }

    public static float boost(LivingEntity player, float partial) {
        S s = update(player, partial);
        return s == null ? 0 : s.fist;
    }

    public static java.util.Set<Integer> ids() {
        return STATES.keySet();
    }

    public static float pivot() {
        return PIVOT;
    }

    // ---------- the whole body ----------

    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        pushed = push(event.getEntity(), event.getPoseStack(), event.getPartialTick());
    }

    // Turns the whole body for the flight (around the hips), before Minecraft draws it. True when it pushed a pose
    // (pop it after drawing). Players through RenderPlayerEvent, the flying souls from SoulBossRenderer.
    public static boolean push(LivingEntity player, PoseStack pose, float partial) {
        S s = update(player, partial);
        if (s == null || s.weight < 0.001f) {
            return false;
        }
        // Minecraft turns the body by its body yaw after this; take that off and put the flight's turn instead.
        float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Quaternionf vanilla = new Quaternionf().rotateY((float) Math.toRadians(180f - body));
        Quaternionf full = new Quaternionf(s.world).mul(new Quaternionf(vanilla).invert());
        Quaternionf q = new Quaternionf().slerp(full, s.weight);
        pose.pushPose();
        pose.translate(0, PIVOT, 0);
        pose.mulPose(q);
        pose.translate(0, -PIVOT, 0);
        return true;
    }

    // The whole body's turn in the world as push draws it (vanilla's body yaw plus the flight's), or null when not flying.
    public static Quaternionf bodyTurn(LivingEntity entity, float partial) {
        S s = update(entity, partial);
        if (s == null || s.weight < 0.001f) {
            return null;
        }
        float body = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        Quaternionf vanilla = new Quaternionf().rotateY((float) Math.toRadians(180f - body));
        Quaternionf full = new Quaternionf(s.world).mul(new Quaternionf(vanilla).invert());
        return new Quaternionf().slerp(full, s.weight).mul(vanilla);
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
        if (s == null || s.limbs == null || s.weight < 0.001f || SkillAnimations.posing(entity)
                || SkillAnimations.guarding(entity) && s.cw < 0.5f) {
            return;
        }
        float w = s.weight;
        float[] q = s.limbs;
        set(model.body, q, 0, w);
        set(model.head, q, 6, w);
        set(model.rightArm, q, 12, w);
        set(model.leftArm, q, 18, w);
        set(model.rightLeg, q, 24, w);
        set(model.leftLeg, q, 30, w);
        model.hat.copyFrom(model.head);
    }

    // a part to its pose (pivot in pixels, turns in degrees), weighed against Minecraft's own
    private static void set(ModelPart part, float[] q, int i, float w) {
        part.x = Mth.lerp(w, part.x, q[i]);
        part.y = Mth.lerp(w, part.y, q[i + 1]);
        part.z = Mth.lerp(w, part.z, q[i + 2]);
        part.xRot = Mth.lerp(w, part.xRot, q[i + 3] * Mth.DEG_TO_RAD);
        part.yRot = Mth.lerp(w, part.yRot, q[i + 4] * Mth.DEG_TO_RAD);
        part.zRot = Mth.lerp(w, part.zRot, q[i + 5] * Mth.DEG_TO_RAD);
    }

    // ---------- mixing the poses ----------

    private static final int N = 37, YAW = 36;

    private static float sin(double a) {
        return (float) Math.sin(a);
    }

    private static float smoother(float x) {
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        float[] out = new float[N];
        for (int i = 0; i < N; i++) {
            out[i] = Mth.lerp(t, a[i], b[i]);
        }
        return out;
    }

    // One pose after the other: each held for `every` seconds, then 0.9 s of blending into another one (never the same).
    static final class Mixer {
        final float[][] lib;
        final float every;
        int cur, prev;
        float k = 1, hold;
        // a pose it must hold (a soul's crossed arms), or -1
        int forced = -1;
        // a pose it never picks by itself, or -1
        int skip = -1;

        Mixer(float[][] lib, float every) {
            this.lib = lib;
            this.every = every;
            this.hold = every;
        }

        float[] at(float dt, float rate) {
            hold -= dt * rate;
            if (forced >= 0 && forced < lib.length) {
                if (cur != forced && k >= 1) {
                    prev = cur;
                    cur = forced;
                    k = 0;
                }
                hold = 1;
            } else if (hold <= 0 && k >= 1) {
                int n = cur;
                while (lib.length > 2 && (n == cur || n == skip) || lib.length == 2 && n == cur) {
                    n = (int) (Math.random() * lib.length);
                }
                prev = cur;
                cur = n;
                k = 0;
                hold = every;
            }
            k = Math.min(1, k + dt / 0.9f);
            return mix(lib[prev], lib[cur], smoother(k));
        }
    }

    // Which of the moves in FlightPoseData play while floating: Espreguiça (0), Coça a cabeça (2), Soquinhos (3).
    // The spin in the air (1) is left out (Stark 2026-11-21: "tira a animação de giro do voo").
    private static final int[] IDLE_CLIPS = {0, 2, 3};
    // Only one change every 40 s while floating (Stark 2026-11-21: "diminui bastante, deixa 1 por 40 segundos"): the
    // floating pose changes every 80 s and one little move plays halfway, 40 s after each change. Cruising: 40 s.
    private static final float HOVER_EVERY = 80;

    // Floating: the pose of the moment, and now and then one of the little moves on top of it (only while it floats
    // calmly: not cruising, braking, boosting, fighting, waving or holding a forced pose).
    private static float[] floating(S s, float t, float dt, boolean flying) {
        float[] base = s.hover.at(dt, s.clip >= 0 ? 0 : flying ? 1 : 0.5f);
        if (s.clip >= 0) {
            float u = (t - s.clipStart) / FlightPoseData.CLIP_LEN[s.clip];
            if (u >= 1) {
                s.lastClip = s.clip;
                s.clip = -1;
                return base;
            }
            float[] ts = FlightPoseData.CLIP_T[s.clip];
            float[][] ks = FlightPoseData.CLIP_K[s.clip][s.clipBase];
            int i = 0;
            while (i < ts.length - 2 && u > ts[i + 1]) {
                i++;
            }
            float e = smoother(Mth.clamp((u - ts[i]) / (ts[i + 1] - ts[i]), 0, 1));
            return mix(ks[i], ks[i + 1], e);
        }
        boolean calm = flying && s.lean < 0.05f && s.cw < 0.05f && s.brake < 0.1f && s.fist < 0.05f && s.wave < 0.05f
                && s.hover.forced < 0 && s.hover.k >= 1;
        if (s.hover.k < 1) {
            s.clipDone = false;
        }
        // halfway through the hold, with a few seconds left so the move ends before the next change
        if (calm && !s.clipDone && s.hover.hold <= HOVER_EVERY / 2 && s.hover.hold > 5) {
            s.clipDone = true;
            int k = (int) (Math.random() * IDLE_CLIPS.length);
            if (IDLE_CLIPS[k] == s.lastClip) {
                k = (k + 1) % IDLE_CLIPS.length;
            }
            s.clip = IDLE_CLIPS[k];
            s.clipBase = s.hover.cur;
            s.clipStart = t;
        }
        return base;
    }

    // ---------- the combat stance (one pose for each way it moves, Stark 2026-11-13: "cada coisinha tem uma animação") ----------

    private static float[] combatLimbs(S s, float t) {
        float top = 16;
        float f = Mth.clamp(s.vf / top, -1, 1), sd = Mth.clamp(s.vs / top, -1, 1), u = Mth.clamp(s.vu / top, -1, 1);
        float wf = Math.max(f, 0), wb = Math.max(-f, 0), wr = Math.max(sd, 0), wl = Math.max(-sd, 0), wu = Math.max(u, 0), wd = Math.max(-u, 0);
        float sum = wf + wb + wr + wl + wu + wd;
        float norm = sum > 1 ? 1 / sum : 1;
        float[] g = FlightPoseData.C_GUARD;
        float[] out = new float[N];
        for (int i = 0; i < N; i++) {
            out[i] = g[i] + norm * (wf * (FlightPoseData.C_FWD[i] - g[i]) + wb * (FlightPoseData.C_BACK[i] - g[i])
                    + wr * (FlightPoseData.C_RIGHT[i] - g[i]) + wl * (FlightPoseData.C_LEFT[i] - g[i])
                    + wu * (FlightPoseData.C_UP[i] - g[i]) + wd * (FlightPoseData.C_DOWN[i] - g[i]));
            out[i] = Mth.lerp(s.guard, out[i], FlightPoseData.C_BLOCK[i]);
        }
        // the fists bob a hair out of step with each other while floating (alive, not swinging)
        float still = 1 - Math.min(1, sum);
        out[15] += sin(t * 2.3) * 2.3f * still;
        out[21] += sin(t * 2.3 + 2.1) * 2.3f * still;
        return out;
    }

    private static void limbs(S s, float dt, float t, float v, float c, boolean flying) {
        float lf = s.lean, fi = s.fist;
        float[] hv = floating(s, t, dt, flying);
        float[] cr = s.cruise.at(dt, 1);
        float[] q = mix(mix(mix(hv, cr, lf), FlightPoseData.BOOST, fi), FlightPoseData.BRAKE, s.brake);
        if (s.cw > 0.001f) {
            q = mix(q, mix(combatLimbs(s, t), FlightPoseData.BOOST, fi), s.cw);
        }
        // reacting to the flight (degrees)
        float turn = Mth.clamp(s.yawRate / 3, -1, 1) * lf;
        float dive = Mth.clamp(-s.climb / 1.1f, -1, 1) * lf;
        s.lagT += (turn - s.lagT) * damp(3, dt);
        // breathing while floating: the shoulders
        float breath = sin(t * 1.7) * (1 - lf) * (1 - s.cw);
        q[17] += 0.9f * breath;
        q[23] -= 0.9f * breath;
        // the wind in the legs while cruising fast
        float flut = sin(t * 13) * 2.6f * Mth.clamp(v / (c * 1.6f), 0, 1) * lf;
        q[27] += flut;
        q[33] -= flut;
        // the outer arm opens and the legs swing out in the turns
        float lt = s.lagT * (1 - fi);
        q[17] += Math.max(lt, 0) * 22;
        q[23] -= Math.max(-lt, 0) * 22;
        q[29] += s.lagT * 6;
        q[35] += s.lagT * 6;
        // diving: the arms reach forward, the head looks down the dive
        q[15] -= dive * 40 * (1 - fi);
        q[21] -= dive * 31 * (1 - fi);
        q[9] += dive * 14;
        // waving at you (Goku and Kuririn passing by): the right hand up, swinging side to side
        if (s.wave > 0.01f) {
            q[12] = Mth.lerp(s.wave, q[12], -5);
            q[13] = Mth.lerp(s.wave, q[13], 2);
            q[14] = Mth.lerp(s.wave, q[14], 0);
            q[15] = Mth.lerp(s.wave, q[15], -157.6f);
            q[16] = Mth.lerp(s.wave, q[16], 0);
            q[17] = Mth.lerp(s.wave, q[17], (0.35f - sin(t * 9) * 0.4f) * Mth.RAD_TO_DEG);
        }
        s.twist = q[YAW] - s.lagT * 0.35f;
        s.limbs = q;
    }
}
