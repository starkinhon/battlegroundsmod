package com.starkinhon.soulsjjk.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.soulsjjk.network.ModPayloads;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import com.starkinhon.soulsjjk.compat.PacketDistributor;

// Gojo's Blue (power/powers/gojo/Azul), drawn the same way as the Skill Visualizer's drawAzul (aba "Gojo · Azul"):
// a deep blue glow behind it, lines of light sucked in spiraling from far away, arcs of wind turning around it (small
// vortices), the orb itself (souls_vfx shape 18; the look comes from gojo.azul.json: now 2, the realism volume; 3, the mix
// glass ball, is kept for later) and the space bent around it: a lens on the orb and, charged, a swirl around it.
// The server sends where it is every tick (smoothed with SmoothTrack); everything else is worked out here.
public final class AzulRenderer {
    private static final float[] DEEP = FxDraw.rgb(0x0A1F9E);
    private static final float[] BLUE = FxDraw.rgb(0x1E6BFF);
    private static final float[] CYAN = FxDraw.rgb(0x60DAFF);
    private static final float[] PALE = FxDraw.rgb(0xDFF6FF);
    private static final float[] WHITE = {1, 1, 1};
    // How long it takes to appear, and to fade at the end (a tap's orb, and the held one that implodes).
    private static final float SPAWN = 3;
    private static final float FADE = 6;
    private static final float IMPLODE = 4;

    private static final class Orb {
        final SmoothTrack track;
        final long born;
        float prevCharge, charge;
        int style;
        long updated;
        long endAt = -1;
        float endLength = FADE;

        Orb(Vec3 pos, float radius, float charge, int style, long now) {
            this.track = new SmoothTrack(pos, radius);
            this.prevCharge = this.charge = charge;
            this.style = style;
            this.born = now;
            this.updated = now;
        }
    }

    private static final Map<Integer, Orb> ORBS = new HashMap<>();

    private AzulRenderer() {
    }

    public static void orb(int id, int mode, Vec3 pos, float radius, float charge, int style) {
        long now = ScreenFx.now();
        Orb o = ORBS.get(id);
        if (o == null) {
            if (mode != 0) {
                return;
            }
            o = new Orb(pos, radius, charge, style, now);
            ORBS.put(id, o);
            return;
        }
        o.updated = now;
        if (mode != 0) {
            if (o.endAt < 0) {
                o.endAt = now;
                o.endLength = mode == 1 ? IMPLODE : FADE;
            }
            return;
        }
        o.track.push(pos, radius);
        o.prevCharge = o.charge;
        o.charge = charge;
        o.style = style;
    }

    // When the server last said our own Blue is under our control (it says so every tick).
    private static long controlAt = -100;

    public static void control(boolean on) {
        controlAt = on ? ScreenFx.now() : -100;
    }

    private static boolean controlling() {
        return Minecraft.getInstance().player != null && ScreenFx.now() - controlAt <= 5;
    }

    // While our Blue follows the aim the wheel moves it (up away, down closer) and the hotbar stays put.
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!controlling() || Minecraft.getInstance().screen != null) {
            return;
        }
        event.setCanceled(true);
        double d = event.getScrollDelta();
        if (d != 0) {
            PacketDistributor.sendToServer(new ModPayloads.AzulScroll(d > 0 ? 1 : -1));
        }
    }

    public static boolean active() {
        return !ORBS.isEmpty();
    }

    public static void clear() {
        ORBS.clear();
        controlAt = -100;
    }

    // The shader's param byte: style * 64 + charge * 63 (the same as the Visualizer's azParam).
    static float param(int style, float charge) {
        return (Mth.clamp(style, 0, 3) * 64 + Math.round(Mth.clamp(charge, 0, 1) * 63)) / 255f;
    }

    static void render(Vec3 camera, double now, float partial) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        // Gone after its fade, or when the server stopped talking about it (out of range, say).
        ORBS.values().removeIf(o -> o.endAt >= 0 ? now - o.endAt > o.endLength : now - o.updated > 6);
        Camera view = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(view.getLeftVector());
        Vector3f up = new Vector3f(view.getUpVector());
        for (Map.Entry<Integer, Orb> entry : ORBS.entrySet()) {
            Orb o = entry.getValue();
            float age = (float) (now - o.born);
            if (o.endAt < 0) {
                o.track.sample(now);
            }
            float charge = Mth.lerp(partial, o.prevCharge, o.charge);
            float spawn = smooth(age / SPAWN);
            if (o.endAt >= 0) {
                spawn *= 1 - smooth((float) ((now - o.endAt) / o.endLength));
            }
            Vec3 world = o.track.pos();
            Vector3f at = world.subtract(camera).toVector3f();
            int seed = entry.getKey() * 2027 + 501;
            Vec3 step = o.endAt < 0 ? o.track.velocity() : Vec3.ZERO;
            if (step.lengthSqr() > 0.06) {
                wind(at, step, o.track.radius() * spawn, age, seed);
            }
            draw(world, at, o.track.radius(), age, seed, o.style, charge, spawn, left, up);
        }
    }

    // Moving (following, circling, shot): a few thin white lines of wind streaming past it and off its back, longer the
    // faster it goes; each one is redrawn every few ticks somewhere else around it. Kept minimal on purpose.
    static void wind(Vector3f at, Vec3 step, float r, float age, int seed) {
        float speed = (float) step.length();
        Vector3f dir = step.normalize().toVector3f();
        Vector3f side = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f other = new Vector3f(dir).cross(side).normalize();
        float k = Mth.clamp((speed - 0.25f) / 1.2f, 0, 1);
        int count = 3 + Math.round(3 * k);
        for (int i = 0; i < count; i++) {
            float life = 5 + 3 * FxWorld.hash(seed, i, 61);
            float ph = (age + FxWorld.hash(seed, i, 62) * life) / life;
            int cyc = (int) Math.floor(ph);
            float u = ph - cyc;
            float a = FxWorld.hash(seed + cyc * 7, i, 63) * Mth.TWO_PI;
            float off = r * (1.15f + 0.6f * FxWorld.hash(seed + cyc * 7, i, 64));
            Vector3f base = new Vector3f(side).mul(Mth.cos(a) * off).add(new Vector3f(other).mul(Mth.sin(a) * off)).add(at);
            float length = Math.min(8, Math.max(1.2f, speed * (3 + 2 * FxWorld.hash(seed, i, 65)))) * (0.4f + 0.6f * k);
            // Slides back along the orb as it lives, and fades in and out.
            Vector3f head = new Vector3f(dir).mul(r * (0.8f - 1.6f * u)).add(base);
            Vector3f tail = new Vector3f(dir).mul(-length).add(head);
            float o = Mth.sin(u * Mth.PI) * (0.35f + 0.35f * k);
            FxDraw.ribbon(tail, head, Math.max(0.025f, r * 0.05f), FxDraw.STREAK, 0.9f, WHITE, o, true);
        }
    }

    // r: the orb's radius; charge 0 to 1; spawn 0 to 1 (appearing, fading).
    private static void draw(Vec3 world, Vector3f at, float r, float age, int seed, int style, float charge, float spawn, Vector3f left, Vector3f up) {
        float R = r * spawn;
        if (R < 0.01f) {
            return;
        }
        Vector3f look = at.lengthSquared() > 1.0E-6f ? new Vector3f(at).normalize() : new Vector3f(0, 0, 1);
        boolean cartoon = style == 1;
        // Cartoon and glass go in the alpha layer (solid color, they don't blow out white).
        boolean light = style == 0 || style == 2;
        // The glow (it goes to the bloom); a little only in the cartoon, its edge is drawn.
        FxDraw.billboard(at, R * 7, 0, FxDraw.SOFT, 0, DEEP, (cartoon ? 0.18f : 0.32f) * spawn, true);
        FxDraw.billboard(at, R * 3.4f, 0, FxDraw.SOFT, 0.2f, BLUE, (cartoon ? 0.15f : 0.32f) * spawn, true);
        // Lines sucked in: born far away, they come in turning.
        int lines = (int) Math.floor(10 + 22 * charge);
        for (int k = 0; k < lines; k++) {
            float life = Math.max(6, 12 + 8 * FxWorld.hash(seed, k, 1) - 5 * charge);
            float ph = (age + FxWorld.hash(seed, k, 2) * life) / life;
            float u = ph - (float) Math.floor(ph);
            int cyc = (int) Math.floor(ph);
            int s0 = seed + cyc * 13;
            float a0 = FxWorld.hash(s0, k, 3) * Mth.TWO_PI;
            Vector3f d0 = new Vector3f(left).mul(Mth.cos(a0)).add(new Vector3f(up).mul(Mth.sin(a0)));
            d0.add(new Vector3f(look).mul((FxWorld.hash(s0, k, 4) - 0.5f) * 0.8f)).normalize();
            float reach = 1.3f + (2.4f + 2.2f * charge) * (0.6f + 0.4f * FxWorld.hash(seed + cyc, k, 5));
            float o = Mth.sin(u * Mth.PI) * spawn;
            float w = Math.max(0.02f, R * (0.05f + 0.04f * FxWorld.hash(seed, k, 6)));
            Vector3f from = linePoint(d0, look, at, R, reach, charge, Math.max(0, u - (cartoon ? 0.2f : 0.22f)));
            Vector3f to = linePoint(d0, look, at, R, reach, charge, u);
            if (cartoon) {
                FxDraw.ribbon(from, to, w * 1.3f, FxDraw.STREAK, 0.9f, WHITE, o, false);
            } else {
                FxDraw.ribbon(from, to, w, FxDraw.STREAK, 0.8f, k % 3 != 0 ? PALE : CYAN, 0.8f * o, true);
            }
        }
        // Arcs of wind turning around it (small vortices).
        int arcs = 3 + Math.round(4 * charge);
        for (int k = 0; k < arcs; k++) {
            float rad = R * (1.2f + 0.9f * FxWorld.hash(seed, k, 11));
            float spin = age * (0.18f + 0.14f * FxWorld.hash(seed, k, 12)) * (1 + charge);
            float span = 1.0f + 1.4f * FxWorld.hash(seed, k, 13);
            float a0 = FxWorld.hash(seed, k, 14) * Mth.TWO_PI + spin;
            float tilt = (FxWorld.hash(seed, k, 15) - 0.5f) * 0.9f;
            Vector3f last = null;
            for (int j = 0; j <= 14; j++) {
                float s = j / 14f;
                float a = a0 + s * span;
                float rr = rad * (1 - 0.18f * s);
                Vector3f p = new Vector3f(left).mul(Mth.cos(a) * rr).add(new Vector3f(up).mul(Mth.sin(a) * rr));
                p.rotateAxis(tilt, left.x, left.y, left.z).add(at);
                if (last != null) {
                    float f = Mth.sin(s * Mth.PI);
                    if (cartoon) {
                        FxDraw.ribbon(last, p, Math.max(0.025f, R * 0.07f) * f, FxDraw.BEAM, 0.9f, WHITE, 0.95f * spawn, false);
                    } else {
                        FxDraw.ribbon(last, p, Math.max(0.03f, R * 0.12f) * f, FxDraw.BEAM, 0.1f, BLUE, 0.35f * spawn, true);
                        FxDraw.ribbon(last, p, Math.max(0.012f, R * 0.035f) * f, FxDraw.BEAM, 0.8f, PALE, 0.7f * f * spawn, true);
                    }
                }
                last = p;
            }
        }
        // The orb (shape 18). The color only carries its seed (the shader reads it from red and blue).
        float[] seedColor = {0.2f + 0.6f * FxWorld.hash(seed, 0, 31), 1, 0.2f + 0.6f * FxWorld.hash(seed, 1, 31)};
        FxDraw.billboard(at, R * 4, 0, FxDraw.AZUL, param(style, charge), seedColor, spawn, light);
        // The space bending: the orb itself (lens) and, charged, the space around it (swirl).
        ScreenFx.holdWarp(world, R * 2.2f, (0.35f + 0.3f * charge) * spawn, 2, new Vec3(0, 1, 0), age / 20);
        if (charge > 0.3f) {
            ScreenFx.holdWarp(world, R * 3.6f, 0.35f * charge * spawn, 1, new Vec3(look.x, look.y, look.z), age / 20);
        }
    }

    // A point of a sucked in line: s 0 far away, 1 at the orb's edge, turning around the look axis on the way in.
    private static Vector3f linePoint(Vector3f d0, Vector3f look, Vector3f at, float R, float reach, float charge, float s) {
        Vector3f p = new Vector3f(d0).rotateAxis(s * (1.1f + 0.8f * charge), look.x, look.y, look.z);
        return p.mul(R * Mth.lerp(s, reach, 1.05f)).add(at);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
