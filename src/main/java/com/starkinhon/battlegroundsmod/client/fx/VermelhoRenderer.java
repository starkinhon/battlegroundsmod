package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Gojo's Red (power/powers/gojo/Vermelho), drawn the same way as the Skill Visualizer's drawVermelho (aba
// "Gojo · Vermelho", the version Stark approved: bright core, radius 0.093). The orb (souls_vfx shape 20) sits at his
// fingertip; its tails (shape 19, strips with u along them) come out of it in a spiral, pulled back by the spin, and
// never leave it: the energy runs along them into the middle. They form one by one, thin and faint with a lit tip,
// until they are done. Around it a red haze and flakes spin along, and the space bends a little on the orb.
// The server only says it is alive every tick; where it is comes from the owner's hand here, so it never lags.
public final class VermelhoRenderer {
    private static final float[] DEEP = FxDraw.rgb(0x52000A);
    private static final float[] RED = FxDraw.rgb(0xFF1020);
    private static final float[] HOT = FxDraw.rgb(0xFF9AA0);
    private static final float[] PALE = FxDraw.rgb(0xFFD6D8);

    // The Visualizer's VERMELHO.
    private static final float RADIUS = 0.093f;
    private static final int TAILS = 7;
    private static final int THREADS = 5;
    private static final float SPIN = 0.24f;
    private static final float FORM = 50;
    private static final int SEGMENTS = 24;
    private static final float LENGTH_MIN = 5;
    private static final float LENGTH_MAX = 12;
    private static final float CURL_MIN = 1.0f;
    private static final float CURL_MAX = 2.3f;
    private static final float TAIL_WIDTH = 2.6f;
    private static final float THREAD_WIDTH = 0.8f;
    private static final int FLAKES = 22;
    // Appearing, and fading at the end (the tails pull in first, then the orb goes out).
    private static final float SPAWN = 4;
    private static final float FADE = 24;

    private static final class Orb {
        final int owner;
        final long born;
        long updated;
        long endAt = -1;
        // Worked out in render, used again by the overlay.
        Vector3f at;
        float drawR;
        float spawn;
        int seed;

        Orb(int owner, long now) {
            this.owner = owner;
            this.born = now;
            this.updated = now;
        }
    }

    private static final Map<Integer, Orb> ORBS = new HashMap<>();
    private static final List<Orb> DRAWN = new ArrayList<>();

    private VermelhoRenderer() {
    }

    public static void orb(int id, int owner, int mode) {
        long now = ScreenFx.now();
        Orb o = ORBS.get(id);
        if (o == null) {
            if (mode == 0) {
                ORBS.put(id, new Orb(owner, now));
            }
            return;
        }
        o.updated = now;
        if (mode != 0 && o.endAt < 0) {
            o.endAt = now;
        }
    }

    public static boolean active() {
        return !ORBS.isEmpty();
    }

    public static void clear() {
        ORBS.clear();
        DRAWN.clear();
    }

    static void render(Vec3 camera, double now, float partial) {
        DRAWN.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        // The server stopped talking about it (out of range, say): it fades like at the end.
        for (Orb o : ORBS.values()) {
            if (o.endAt < 0 && now - o.updated > 6) {
                o.endAt = o.updated;
            }
        }
        ORBS.values().removeIf(o -> o.endAt >= 0 && now - o.endAt > FADE);
        Camera view = mc.gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(view.getLeftVector());
        Vector3f up = new Vector3f(view.getUpVector());
        for (Map.Entry<Integer, Orb> entry : ORBS.entrySet()) {
            Orb o = entry.getValue();
            Entity owner = mc.level.getEntity(o.owner);
            if (owner == null) {
                continue;
            }
            float age = (float) (now - o.born);
            float spawn = smooth(age / SPAWN);
            float grow = smooth((age - 2) / FORM);
            if (o.endAt >= 0) {
                float e = (float) (now - o.endAt);
                grow *= 1 - smooth(e / (FADE - 4));
                spawn *= 1 - smooth((e - 6) / (FADE - 6));
            }
            Vec3 world = fingertip(owner, view, camera, partial);
            o.at = world.subtract(camera).toVector3f();
            o.seed = entry.getKey() * 2027 + 4049;
            o.spawn = spawn;
            o.drawR = RADIUS * spawn;
            if (o.drawR < 0.01f) {
                continue;
            }
            draw(o.at, o.drawR, age, o.seed, grow, spawn, left, up);
            ScreenFx.holdWarp(world, o.drawR * 2.4f, 0.25f * spawn, 2, new Vec3(0, 1, 0), age / 20);
            DRAWN.add(o);
        }
    }

    // Drawn after the glow: the orb over the roots of its tails, and the light on top of it.
    static void overlay() {
        for (Orb o : DRAWN) {
            float R = o.drawR;
            float[] seedColor = {0.2f + 0.6f * FxWorld.hash(o.seed, 0, 33), 1, 0.2f + 0.6f * FxWorld.hash(o.seed, 1, 33)};
            // param 0.75: the bright core (the dark one, param 0.25, is kept in the shader).
            FxDraw.billboard(o.at, R * 4, 0, FxDraw.VORBE, 0.75f, seedColor, o.spawn, false);
            FxDraw.billboard(o.at, R * 1.3f, 0, FxDraw.SOFT, 0.5f, PALE, 0.35f * o.spawn, true);
        }
        DRAWN.clear();
    }

    // His raised fingertip: in front of the right side of his chest (the Visualizer's "na ponta do dedo"); in first
    // person, low on the right of the view.
    private static Vec3 fingertip(Entity owner, Camera view, Vec3 camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (owner == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vector3f look = view.getLookVector();
            Vector3f left = view.getLeftVector();
            Vector3f up = view.getUpVector();
            return camera.add(look.x * 0.85 - left.x * 0.3 - up.x * 0.16, look.y * 0.85 - left.y * 0.3 - up.y * 0.16,
                    look.z * 0.85 - left.z * 0.3 - up.z * 0.16);
        }
        float yaw = (owner instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : owner.getYRot())
                * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        // His right, facing forward.
        double rx = -fz, rz = fx;
        double scale = owner.getBbHeight() / 1.8;
        return owner.getPosition(partial).add((fx * 0.55 + rx * 0.36) * scale, 1.5 * scale, (fz * 0.55 + rz * 0.36) * scale);
    }

    // Everything but the orb itself. R = the orb's radius; grow 0 to 1 = the tails forming; spawn 0 to 1.
    private static void draw(Vector3f at, float R, float age, int seed, float grow, float spawn, Vector3f X, Vector3f Y) {
        Vector3f look = at.lengthSquared() > 1.0E-6f ? new Vector3f(at).normalize() : new Vector3f(0, 0, 1);
        // The red haze around it (goes to the bloom).
        FxDraw.billboard(at, R * 18 * (0.4f + 0.6f * grow), 0, FxDraw.SOFT, 0, DEEP, 0.3f * spawn, false);
        FxDraw.billboard(at, R * 10 * (0.4f + 0.6f * grow), 0, FxDraw.SOFT, 0, DEEP, 0.25f * spawn, true);
        FxDraw.billboard(at, R * 6, 0, FxDraw.SOFT, 0.1f, RED, 0.35f * spawn, true);
        // Tails: the body in the alpha layer (strong red, it doesn't blow out white) and a glow copy added on top.
        List<Tail> tails = new ArrayList<>();
        for (int k = 0; k < TAILS + THREADS; k++) {
            float f = smooth(Mth.clamp(grow * 1.7f - 0.7f * FxWorld.hash(seed, k, 7), 0, 1));
            if (f <= 0.01f) {
                continue;
            }
            Tail t = tail(at, R, age, seed, k, f, X, Y, look);
            t.o = Math.min(1, f * 2.5f) * spawn;
            tails.add(t);
            // The tip lit while it is still growing.
            if (f < 0.999f) {
                FxDraw.billboard(t.pts[SEGMENTS], R * (t.thin ? 0.6f : 1.1f), 0, FxDraw.SOFT, 0.5f, HOT, 2.4f * f * (1 - f) * spawn, true);
            }
        }
        // A wide faint smear behind each tail (the references' "diffuse").
        for (Tail t : tails) {
            if (!t.thin) {
                float[] c = {0.3f + 0.5f * FxWorld.hash(seed, t.k, 34), 1, 0.3f};
                FxDraw.strip(t.pts, scaled(t.ws, 2.4f), t.us, FxDraw.VRABO, 0.4f, c, 0.3f * t.o, false);
            }
        }
        for (Tail t : tails) {
            FxDraw.strip(t.pts, t.ws, t.us, FxDraw.VRABO, 0.25f, t.color(seed), 0.95f * t.o, false);
        }
        for (Tail t : tails) {
            FxDraw.strip(t.pts, scaled(t.ws, 0.8f), t.us, FxDraw.VRABO, 0.75f, t.color(seed), 0.3f * t.o, true);
        }
        // Red flakes turning along, slowly.
        for (int k = 0; k < FLAKES; k++) {
            float life = 30 + 30 * FxWorld.hash(seed, k, 41);
            float ph = (age + FxWorld.hash(seed, k, 42) * life) / life;
            int cyc = (int) Math.floor(ph);
            float u = ph - cyc;
            float a = FxWorld.hash(seed + cyc * 5, k, 43) * Mth.TWO_PI + age * SPIN * 0.6f;
            float rad = R * (2 + 7 * FxWorld.hash(seed + cyc * 5, k, 44)) * (1 - 0.5f * u) * grow;
            Vector3f p = new Vector3f(X).mul(Mth.cos(a) * rad).add(new Vector3f(Y).mul(Mth.sin(a) * rad))
                    .add(new Vector3f(look).mul((FxWorld.hash(seed, k, 45) - 0.5f) * R * 2)).add(at);
            float o = Mth.sin(u * Mth.PI) * spawn;
            float s = R * (0.18f + 0.3f * FxWorld.hash(seed, k, 46));
            FxDraw.billboard(p, s, 0, FxDraw.SOFT, 0.2f, RED, 0.9f * o, false);
            FxDraw.billboard(p, s * 2.2f, 0, FxDraw.SOFT, 0.4f, RED, 0.35f * o, true);
        }
        // The orb's glow under it (the orb is drawn over the glow, in overlay).
        FxDraw.billboard(at, R * 2.6f, 0, FxDraw.SOFT, 0.3f, PALE, 0.45f * spawn, true);
    }

    private static final class Tail {
        final Vector3f[] pts = new Vector3f[SEGMENTS + 1];
        final float[] ws = new float[SEGMENTS + 1];
        final float[] us = new float[SEGMENTS + 1];
        int k;
        boolean thin;
        float o;

        float[] color(int seed) {
            return new float[] {0.2f + 0.6f * FxWorld.hash(seed, k, 31), 1, 0.2f + 0.6f * FxWorld.hash(seed, k, 32)};
        }
    }

    // One tail: a spiral out of the orb, pulled back by the spin, with a little depth (Z = towards the camera's look).
    private static Tail tail(Vector3f at, float r, float age, int seed, int k, float grow, Vector3f X, Vector3f Y, Vector3f Z) {
        Tail t = new Tail();
        t.k = k;
        t.thin = k >= TAILS;
        float len = r * Mth.lerp(FxWorld.hash(seed, k, 1), LENGTH_MIN, LENGTH_MAX) * (t.thin ? 1.25f : 1) * grow;
        float spin = SPIN * (0.85f + 0.3f * FxWorld.hash(seed, k, 2));
        float a0 = FxWorld.hash(seed, k, 3) * Mth.TWO_PI + age * spin;
        float curl = Mth.lerp(FxWorld.hash(seed, k, 4), CURL_MIN, CURL_MAX);
        float tilt = (FxWorld.hash(seed, k, 5) - 0.5f) * 1.1f;
        float w0 = r * (t.thin ? THREAD_WIDTH : TAIL_WIDTH) * (0.8f + 0.5f * FxWorld.hash(seed, k, 6));
        for (int j = 0; j <= SEGMENTS; j++) {
            float s = j / (float) SEGMENTS;
            float rad = r * 0.7f + len * s;
            float ang = a0 - curl * (float) Math.pow(s, 1.2) + 0.12f * Mth.sin(age * 0.09f + s * 4 + k * 1.7f);
            float lift = Mth.sin(tilt) * len * s * 0.6f + r * 0.4f * Mth.sin(age * 0.07f + k + s * 3) * s;
            t.pts[j] = new Vector3f(X).mul(Mth.cos(ang) * rad).add(new Vector3f(Y).mul(Mth.sin(ang) * rad)).add(new Vector3f(Z).mul(lift)).add(at);
            t.ws[j] = w0 * (0.3f + 0.7f * (float) Math.pow(1 - s, 0.8)) * (0.25f + 0.75f * grow);
            t.us[j] = s;
        }
        return t;
    }

    private static float[] scaled(float[] ws, float k) {
        float[] out = new float[ws.length];
        for (int i = 0; i < ws.length; i++) {
            out[i] = ws[i] * k;
        }
        return out;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
