package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.AirFightClient;
import com.starkinhon.battlegroundsmod.client.FlightPose;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Dragon Ball aura remake (forja/auras-db-v2, approved by Stark 2026-11-14: "caralho, ficou mt bom"). The same
// approved shell (souls_ki kind 5) in style 1:
// - born as an explosion: it bursts out at almost twice its size and shrinks, without wobbling, to its size (1.2x the old
//   one) in about a second, with a bent shockwave (kind 71) and a ring blowing over the ground; never rising from the feet;
// - aggressive crests running along the flames, 3 hard anime bands, the dense color, an outline on the silhouette only;
// - the light leaking over the ground (kind 70): fast streaks in jets, a ring on every pulse;
// - charging (an aura below full strength): the flames near the hands bend into them, a pulse every syllable
//   (Ka, me, ha, me...), and when it is let go the recoil blows the aura back;
// - Kaioken (red at full strength): tight fast flames with a dark crimson outline, beating like a heart, the body
//   going red (tint(), through LivingEntityRendererMixin);
// - flying: the flames run against the real velocity (the sky when still), a comet behind in the cruise, upright in the
//   combat flight (only leaning while dodging) and a strong comet on the ki dash, bursting again then.
// The flight's own aura (no skill aura on) replaces the old boost cone and the trail of strands (FlightFx).
public final class AuraFx {
    private static final class St {
        double lit = -1e9, seen = -1e9, releaseAt = -1e9, ringAt = -1e9, last = -1;
        int color = -1;
        float strength;
        boolean charge, kaioken;
        Vector3f back = new Vector3f(0, 0, 1);
        float phase, time;
        Vector3f tail = new Vector3f(0, 1, 0);
        float comet;
        int pulseN = -1, beatN = -1;
        // the flight's own aura
        float show;
        boolean flyOn, boostOn, dashOn;
        double flySeen = -1e9;
    }

    private static final Map<Integer, St> STATES = new HashMap<>();

    private AuraFx() {
    }

    private static St st(int id) {
        if (STATES.size() > 256) {
            STATES.clear();
        }
        return STATES.computeIfAbsent(id, k -> new St());
    }

    // ---------- from the server's KI_AURA ----------

    // An aura lit (or kept on). A new one, another color or one that was off for half a second bursts again.
    static void lit(int id, int color, float strength, double now) {
        St s = st(id);
        if (now - s.seen > 10 || s.color != color || now - s.releaseAt < 14) {
            s.lit = now;
            s.ringAt = now;
            s.pulseN = -1;
            s.beatN = -1;
            s.releaseAt = -1e9;
        }
        s.seen = now;
        s.color = color;
        s.strength = strength;
        // the charges send a weak aura (Kamehameha 0.2, Galick Gun 0.45, Makankosappo 0.55); forms send 0.6 or more
        s.charge = strength < 0.58f;
        s.kaioken = color == com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.RED && strength >= 0.95f;
    }

    // An aura switched off by the skill. A charge let go blows back from where the body looks.
    static void off(int id, double now) {
        St s = STATES.get(id);
        if (s == null) {
            return;
        }
        if (s.charge && now - s.lit > 10) {
            s.releaseAt = now;
            Entity e = FxAnchor.entity(id);
            if (e != null) {
                Vec3 look = e.getLookAngle();
                s.back.set((float) -look.x, (float) -look.y * 0.3f, (float) -look.z).normalize();
            }
        }
        s.kaioken = false;
    }

    static boolean kiOn(int id, double now) {
        St s = STATES.get(id);
        return s != null && now - s.seen < 6 && s.color >= 0;
    }

    // ---------- drawing ----------

    // A skill's aura (DbFx.aura). out: the fade at the end (1 on), a: its strength, sc: the body's scale.
    static void ki(Entity e, int id, float[] col, float a, float sc, double now, float partial, boolean self, float out, Params base) {
        St s = st(id);
        s.seen = Math.max(s.seen, now - 1);
        Params p = base;
        float sec = (float) (now - s.lit) / 20f;
        p.pulse = 0;
        p.speed = 1;
        if (s.kaioken && out > 0.99f) {
            p.kaioken();
            float beat = heart(sec, 1.4f);
            int n = (int) Math.floor(sec * 1.4f);
            if (n != s.beatN) {
                s.beatN = n;
                s.ringAt = now;
            }
            p.pulse = 0.45f * beat + 0.9f * (float) Math.exp(-sec * 3.2f);
            p.speed = 1.6f;
        } else if (s.charge && now >= s.releaseAt && s.releaseAt < s.lit) {
            // Ka... me... ha... me...: a pulse every 0.55 s from the first second
            if (sec >= 1) {
                int n = (int) Math.floor((sec - 1) / 0.55f);
                if (n != s.pulseN) {
                    s.pulseN = n;
                    s.ringAt = now;
                }
                float d = (sec - 1) - n * 0.55f;
                p.pulse = (float) Math.exp(-d * 7) * Math.min(0.85f, 0.55f + 0.15f * n);
            }
            p.pullAmt = 0.25f + 0.55f * Mth.clamp((sec - 0.6f) / 2, 0, 1);
            if (e instanceof LivingEntity le) {
                Vec3 look = le.getLookAngle();
                p.pullWorld = le.getPosition(partial).add(0, le.getBbHeight() * 0.58, 0).add(look.scale(0.55));
            }
            p.speed = 1 + 0.5f * Mth.clamp((sec - 0.8f) / 2.4f, 0, 1);
        }
        if (s.releaseAt > s.lit) {
            float d = (float) (now - s.releaseAt) / 20f;
            float bl = (float) Math.exp(-d * 2.2f) * (d < 0.06f ? d / 0.06f : 1);
            p.blowWorld = new Vector3f(s.back).mul(0.55f * bl).add(0, 0.12f * bl, 0);
            p.pulse = Math.max(p.pulse, 0.8f * (float) Math.exp(-d * 4));
            p.speed = 1.8f;
        }
        draw(e, s, p.color != null ? p.color : col, a, sc, now, partial, self, out, p, true);
    }

    // The flight's own aura, for everyone flying without a skill aura on (FlightFx).
    static void flight(LivingEntity e, double now, float partial, boolean self, float[] col) {
        St s = st(e.getId());
        if (kiOn(e.getId(), now)) {
            s.show = 0;
            s.flyOn = false;
            return;
        }
        float weight = FlightPose.weight(e, partial);
        Vec3 v = FlightPose.velocity(e, partial);
        float sp = (float) v.length();
        boolean combat = AirFightClient.combat(e);
        float boost = FlightPose.shell(e, partial);
        float want = weight * Math.max(boost, Math.max(Mth.clamp((sp - 4) / 8, 0, 1), combat ? 1 : 0));
        if (now != s.flySeen) {
            s.show += (want - s.show) * (want > s.show ? 0.5f : 0.08f);
            s.flySeen = now;
        }
        boolean on = s.show > 0.3f;
        if (on && !s.flyOn) {
            ignite(s, now);
        }
        s.flyOn = on;
        boolean boostOn = boost > 0.5f;
        if (boostOn && !s.boostOn && on) {
            ignite(s, now);
        }
        s.boostOn = boostOn;
        if (s.show < 0.01f) {
            return;
        }
        Params p = new Params();
        p.thresh = 0.59f;
        p.tongue = combat ? 0.38f : 0.3f;
        p.spike = combat ? 0.16f : 0.1f;
        p.spikeFreq = 5;
        p.spikeSharp = 2.2f;
        p.crown = combat ? 0.6f : 0;
        p.up = combat ? 0.55f : 0.2f;
        p.speed = 1.5f + 0.06f * Math.min(sp, 40);
        draw(e, s, col, s.show, 1, now, partial, self, 1, p, false);
    }

    private static void ignite(St s, double now) {
        s.lit = now;
        s.ringAt = now;
    }

    // How each aura looks (the per-color numbers DbFx had for the shell stay the base).
    static final class Params {
        float tongue = 0.36f, spike = 0.1f, spikeFreq = 4.5f, spikeSharp = 2.2f, crown = 0.55f, up = 0.5f, thresh = 0.62f;
        float outline = 2.2f, flicker = 0, wide = 1, pulse, pullAmt, speed = 1;
        float[] edge;
        float[] color;
        Vec3 pullWorld;
        Vector3f blowWorld;

        void kaioken() {
            tongue = 0.4f;
            spike = 0.12f;
            spikeFreq = 5.5f;
            spikeSharp = 2.2f;
            crown = 0.45f;
            up = 0.7f;
            thresh = 0.6f;
            outline = 4;
            flicker = 0.45f;
            wide = 1.12f;
            edge = new float[] {0.48f, 0f, 0.03f};
        }
    }

    private static void draw(Entity e, St s, float[] col, float a, float sc, double now, float partial, boolean self, float out, Params p, boolean ground) {
        if (!KiMesh.ready() || a <= 0) {
            return;
        }
        float dt = s.last < 0 ? 0 : (float) Mth.clamp((now - s.last) / 20.0, 0, 0.1);
        s.last = now;
        s.phase += dt * p.speed * 0.9f * 1.7f;
        s.time += dt * p.speed;
        float sec = (float) (now - s.lit) / 20f;
        float burstScale = sec < 0.05f ? 0.4f + 1.5f * (sec / 0.05f) : 1 + 0.9f * (float) Math.exp(-3.5f * (sec - 0.05f));
        float burst = (float) Math.exp(-sec * 3.5f);

        // where the flames run (world): the sky, bent back against the velocity; the comet in flight
        LivingEntity living = e instanceof LivingEntity l ? l : null;
        float weight = living == null ? 0 : FlightPose.weight(living, partial);
        Quaternionf turn = living == null ? null : FlightPose.bodyTurn(living, partial);
        Vec3 v;
        if (weight > 0.01f) {
            v = FlightPose.velocity(living, partial);
        } else {
            v = new Vec3(e.getX() - e.xo, 0, e.getZ() - e.zo).scale(20);
        }
        float sp = (float) v.length();
        Vector3f tailW = new Vector3f(0, 1, 0);
        float cometWant = 0;
        boolean rush = false;
        if (sp > 1.0E-3f) {
            boolean combat = weight > 0.01f && AirFightClient.combat(living);
            rush = weight > 0.01f && (living == Minecraft.getInstance().player ? AirFightClient.dashing() != null : combat && sp > 30);
            if (rush && !s.dashOn) {
                s.lit = now;
                s.ringAt = now;
            }
            float k = smoothstep(1.5f, 9, sp);
            if (weight < 0.01f) {
                k *= 0.5f;
            } else if (combat && !rush) {
                k *= 0.45f;
            }
            tailW.mul(1 - k).add((float) (-v.x / sp) * k, (float) (-v.y / sp) * k, (float) (-v.z / sp) * k).normalize();
            if (weight > 0.01f) {
                float boost = FlightPose.shell(living, partial);
                cometWant = rush ? 1.5f : combat ? 0.3f * Mth.clamp((sp - 3) / 11, 0, 1) : Math.max(1.05f * Mth.clamp((sp - 3) / 11, 0, 1), 1.6f * boost);
                cometWant *= weight;
            }
        }
        s.dashOn = rush;
        s.tail.lerp(tailW, rush ? 0.35f : 0.15f).normalize();
        s.comet += (cometWant - s.comet) * (rush ? 0.4f : 0.12f);
        if (turn == null) {
            float yaw = living != null ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : e.getYRot();
            turn = new Quaternionf().rotationY((float) Math.toRadians(180f - yaw));
        }

        Vector3f feet = DbFx.rel(e.getPosition(partial));
        float K = 1.2f * burstScale * p.wide;
        Matrix4f m = new Matrix4f().translation(feet.x, feet.y, feet.z);
        float pivot = FlightPose.pivot() * sc;
        m.translate(0, pivot, 0).rotate(turn).translate(0, -pivot, 0);
        m.translate(0, (1.0f + (1.12f * K - 1.12f) * 0.9f) * sc, 0).scale(sc);
        Matrix4f inv = new Matrix4f(m).invert();

        KiMesh.P k = KiMesh.p(KiMesh.SHELL);
        k.style = 1;
        k.phase = s.phase;
        k.time = s.time;
        k.growth = Math.min(1, out);
        k.flashAmt = 0;
        k.burst = burst;
        k.pulse = p.pulse;
        k.alpha = Mth.clamp(0.45f + 0.55f * a, 0, 1);
        k.color = col;
        k.color2 = p.edge != null ? p.edge : edge(col);
        k.seed = 0.37f * (e.getId() % 7 + 1);
        k.tongue = p.tongue;
        k.spike = p.spike;
        k.spikeFreq = p.spikeFreq;
        k.spikeSharp = p.spikeSharp;
        k.crown = p.crown;
        k.up = p.up;
        k.thresh = p.thresh;
        k.outline = p.outline;
        k.flicker = p.flicker;
        k.size = new float[] {0.62f * K, 1.12f * K, 0.5f * K};
        Vector3f td = inv.transformDirection(new Vector3f(s.tail)).normalize();
        k.tailDir = new float[] {td.x, td.y, td.z};
        k.comet = s.comet;
        if (p.pullWorld != null && p.pullAmt > 0) {
            Vector3f hp = inv.transformPosition(DbFx.rel(p.pullWorld));
            k.pullPos = new float[] {hp.x, hp.y, hp.z};
            k.pullAmt = p.pullAmt;
        }
        if (p.blowWorld != null) {
            Vector3f b = inv.transformDirection(new Vector3f(p.blowWorld));
            k.blow = new float[] {b.x, b.y, b.z};
        }
        if (!self) {
            KiMesh.queue(KiMesh.Mesh.AURA, m, k, KiMesh.BOTH, 10);
            // the birth's shockwave, 0.35 s
            float sk = sec / 0.35f;
            if (sk >= 0 && sk < 1) {
                Vector3f c = m.transformPosition(new Vector3f());
                KiMesh.P w = KiMesh.p(KiMesh.AURA_SHOCK);
                w.color = col;
                w.seed = k.seed;
                w.time = sec;
                w.alpha = (1 - sk) * (1 - sk) * Math.min(1, a);
                float r = (0.5f + 3.2f * (1 - (1 - sk) * (1 - sk) * (1 - sk))) * sc;
                KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(c, r), w, KiMesh.BOTH, 13);
            }
        }
        // the light leaking over the ground (standing on it only; in first person too, it is under you)
        if (ground && weight < 0.01f && e.onGround()) {
            KiMesh.P g = KiMesh.p(KiMesh.AURA_GROUND);
            g.color = col;
            g.growth = Math.min(1, out);
            g.alpha = Math.min(1, a);
            g.phase = s.phase;
            g.time = s.time;
            g.ringR = Mth.clamp((float) (now - s.ringAt) / 10f, 0, 1);
            KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(new Vector3f(feet.x, feet.y + 0.03f, feet.z), 4 * sc), g, KiMesh.BOTH, 5);
        }
    }

    // The Kaioken's red body: the color the model is drawn with (-1 = untouched).
    public static int tint(LivingEntity e) {
        St s = STATES.get(e.getId());
        if (s == null || !s.kaioken) {
            return -1;
        }
        long now = ScreenFx.now();
        if (now - s.seen > 40) {
            return -1;
        }
        float sec = (float) (now - s.lit) / 20f;
        float amt = Mth.clamp(0.35f + 0.25f * heart(sec, 1.4f) + 0.4f * (float) Math.exp(-sec * 3.2f), 0, 1);
        int gb = (int) (255 * (1 - 0.45f * amt));
        return 0xFF000000 | 0xFF0000 | gb << 8 | gb;
    }

    // "tum-tum": two beats per cycle, the second softer
    private static float heart(float t, float bpm) {
        float c = (t * bpm) % 1;
        if (c < 0) {
            c += 1;
        }
        return (float) (Math.exp(-Math.pow((c - 0.05) / 0.045, 2)) + 0.6 * Math.exp(-Math.pow((c - 0.24) / 0.05, 2)));
    }

    // The outline's color: the same hue, deeper and more saturated.
    private static float[] edge(float[] c) {
        float r = (float) Math.pow(c[0], 2.2), g = (float) Math.pow(c[1], 2.2), b = (float) Math.pow(c[2], 2.2);
        float mx = Math.max(r, Math.max(g, b));
        float k = mx > 1.0E-4f ? 0.95f / mx : 0;
        return new float[] {r * k, g * k, b * k};
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void clear() {
        STATES.clear();
    }
}
