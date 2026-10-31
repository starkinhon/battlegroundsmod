package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Bills' Destruction Energy (power/powers/bills/DestructionEnergy), drawn with shader shapes only (no textures), the
// same as the Skill Visualizer's "Esfera de anéis": a white ball of light with a lilac edge, rings of white-lilac energy
// turning around it, each on its own tilted plane that slowly turns, with the light running along it and a tail that
// fades; behind it a soft purple glow, two starbursts turning opposite ways and short rays flickering. While it forms the
// rings open one after another, spinning fast, and settle. Flying, it leaves a lilac streak behind.
// The many small balls of the Shift + Z are drawn lighter (fewer rings and rays, shorter rings).
public final class EnergyRenderer {
    // track: the positions the server sent (one per tick), smoothed (SmoothTrack).
    private record Ball(SmoothTrack track, float prevGrow, float grow, boolean small, long updated, long born) {
    }

    private static final Map<Integer, Ball> BALLS = new HashMap<>();
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] DEEP = FxDraw.rgb(0x2A0A6E);
    private static final float[] PURPLE = FxDraw.rgb(0x6A22E0);
    private static final float[] VIOLET = FxDraw.rgb(0x9A5CFF);
    private static final float[] LILAC = FxDraw.rgb(0xCDB0FF);
    private static final float[] PALE = FxDraw.rgb(0xF1E6FF);
    // Past this many blocks a big ball is drawn like a small one.
    private static final double FAR = 48;

    private EnergyRenderer() {
    }

    public static void ball(int id, int mode, Vec3 pos, float radius, float grow, boolean small) {
        if (mode == 2) {
            BALLS.remove(id);
            return;
        }
        long now = ScreenFx.now();
        Ball old = BALLS.get(id);
        if (old == null) {
            BALLS.put(id, new Ball(new SmoothTrack(pos, radius), grow, grow, small, now, now));
            return;
        }
        old.track().push(pos, radius);
        BALLS.put(id, new Ball(old.track(), old.grow(), grow, small, now, old.born()));
    }

    public static boolean active() {
        return !BALLS.isEmpty();
    }

    public static void clear() {
        BALLS.clear();
    }

    static void render(Vec3 camera, double now, float partial) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        // A ball the server stopped talking about (out of range, say) goes away on its own.
        BALLS.values().removeIf(b -> now - b.updated() > 6);
        Camera view = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(view.getLeftVector());
        Vector3f up = new Vector3f(view.getUpVector());
        Vector3f look = new Vector3f(view.getLookVector());
        for (Map.Entry<Integer, Ball> entry : BALLS.entrySet()) {
            Ball b = entry.getValue();
            float age = (float) (now - b.born());
            b.track().sample(now);
            float r = b.track().radius();
            float grow = Mth.lerp(partial, b.prevGrow(), b.grow());
            Vec3 world = b.track().pos();
            Vector3f at = world.subtract(camera).toVector3f();
            int seed = entry.getKey() * 2027 + 7;
            boolean light = b.small() || world.distanceTo(camera) > FAR;
            Vec3 step = b.track().velocity();
            if (step.lengthSqr() > 0.04 && grow >= 1) {
                trail(at, step, r, b.small());
            }
            draw(at, r, age, seed, grow, light, left, up, look);
        }
    }

    // The streak it leaves flying: a wide lilac glow and a thin white core, longer the faster it goes.
    private static void trail(Vector3f at, Vec3 step, float r, boolean small) {
        Vector3f dir = step.normalize().toVector3f();
        float length = (float) Math.min(step.length() * (small ? 2.5 : 3.5), Math.max(1.5f, r * 14));
        Vector3f back = new Vector3f(dir).mul(-length).add(at);
        FxDraw.ribbon(back, at, r * 1.4f, FxDraw.STREAK, 0.2f, VIOLET, 0.35f, true);
        FxDraw.ribbon(new Vector3f(dir).mul(-length * 0.6f).add(at), at, r * 0.45f, FxDraw.STREAK, 0.8f, PALE, 0.6f, true);
    }

    // grow: 0 to 1 while it forms. light: fewer rings, rays and ring segments (the small balls, or a big one far away).
    private static void draw(Vector3f at, float r, float age, int seed, float grow, boolean light, Vector3f left, Vector3f up, Vector3f look) {
        float g = smooth(grow);
        float pulse = 1 + 0.04f * Mth.sin(age * 0.25f) + 0.02f * Mth.sin(age * 0.71f);
        // The purple glow and halo behind it, kept small and soft so it doesn't wash the screen out in game.
        FxDraw.billboard(at, r * (light ? 4 : 6) * g, 0, FxDraw.SOFT, 0, DEEP, 0.22f * g, true);
        FxDraw.billboard(at, r * (light ? 3 : 4) * g * pulse, 0, FxDraw.SOFT, 0, PURPLE, 0.22f * g, true);
        FxDraw.billboard(at, r * 2.6f * pulse, 0, FxDraw.SOFT, 0, VIOLET, 0.25f * g, true);
        // Rays of light: two starbursts turning opposite ways, and short rays flickering.
        FxDraw.billboard(at, r * (light ? 3.5f : 5) * g, age * 0.004f, FxDraw.FLARE, 0.5f, VIOLET, 0.16f * g, true);
        if (!light) {
            FxDraw.billboard(at, r * 4 * g, -age * 0.006f + 0.3f, FxDraw.FLARE, 0.75f, LILAC, 0.1f * g, true);
        }
        int rays = light ? 6 : 16;
        for (int k = 0; k < rays; k++) {
            float a = FxWorld.hash(seed, k, 1) * Mth.TWO_PI + age * 0.002f * (FxWorld.hash(seed, k, 2) - 0.5f);
            float flick = 0.55f + 0.45f * Mth.sin(age * (0.08f + 0.12f * FxWorld.hash(seed, k, 3)) + k * 1.7f);
            float length = r * (light ? 1.6f + 1.2f * FxWorld.hash(seed, k, 4) : 2 + 2 * FxWorld.hash(seed, k, 4)) * (0.7f + 0.3f * flick) * g;
            Vector3f d = new Vector3f(left).mul(Mth.cos(a)).add(new Vector3f(up).mul(Mth.sin(a)));
            FxDraw.ribbon(new Vector3f(d).mul(r * 0.9f).add(at), new Vector3f(d).mul(length).add(at), r * (0.08f + 0.1f * FxWorld.hash(seed, k, 5)),
                    FxDraw.STREAK, 0.5f, FxWorld.hash(seed, k, 6) < 0.4f ? PALE : LILAC, 0.25f * flick * g, true);
        }
        rings(at, r, age, seed, grow, light, look);
        // The ball of light: a blown out white core with a lilac edge.
        FxDraw.billboard(at, r * 1.8f * pulse, 0, FxDraw.SOFT, 0.3f, LILAC, 0.45f * g, true);
        FxDraw.billboard(at, r * 1.2f * pulse, 0, FxDraw.SOFT, 0.7f, PALE, 0.75f * g, true);
        FxDraw.billboard(at, r * 0.7f, 0, FxDraw.SOFT, 1, WHITE, 0.9f * g, true);
        FxDraw.billboard(at, r * 2.2f * g, age * 0.02f, FxDraw.FLARE, 6 / 32f, PALE, 0.22f * g, true);
    }

    // Each ring on its own tilted plane that slowly turns; the light runs along it, strong at the head and fading to the
    // tail. They open one after another while the ball forms, spinning three times faster until they are open. The part
    // passing behind the ball is dimmed.
    private static void rings(Vector3f at, float r, float age, int seed, float grow, boolean light, Vector3f look) {
        int count = light ? 4 : 6;
        int segments = light ? 24 : 72;
        for (int k = 0; k < count; k++) {
            float open = smooth((grow * count - k * 0.6f) / 1.5f);
            if (open <= 0) {
                continue;
            }
            float tilt = 0.35f + 1.1f * FxWorld.hash(seed, k, 10);
            float yaw = FxWorld.hash(seed, k, 11) * Mth.TWO_PI + age * (0.004f + 0.01f * FxWorld.hash(seed, k, 12)) * (k % 2 == 1 ? 1 : -1);
            Vector3f n = new Vector3f(Mth.sin(tilt) * Mth.cos(yaw), Mth.cos(tilt), Mth.sin(tilt) * Mth.sin(yaw));
            Vector3f u = new Vector3f(n).cross(Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f v = new Vector3f(n).cross(u);
            float ring = r * (1.25f + 0.45f * FxWorld.hash(seed, k, 13)) * (0.3f + 0.7f * open);
            float speed = (0.06f + 0.07f * FxWorld.hash(seed, k, 14)) * (k % 3 != 0 ? 1 : -1) * (1 + 2 * (1 - open));
            float head = FxWorld.hash(seed, k, 15) * Mth.TWO_PI + age * speed;
            float span = Mth.TWO_PI * (light ? 0.45f + 0.3f * FxWorld.hash(seed, k, 16) : 0.55f + 0.45f * FxWorld.hash(seed, k, 16)) * open;
            float width = Math.max(0.018f, r * (0.06f + 0.03f * FxWorld.hash(seed, k, 17)));
            float sign = Math.signum(speed);
            Vector3f last = null;
            for (int j = 0; j <= segments; j++) {
                float s = j / (float) segments;
                float a = head - sign * span * s;
                Vector3f off = new Vector3f(u).mul(Mth.cos(a) * ring).add(new Vector3f(v).mul(Mth.sin(a) * ring));
                Vector3f point = new Vector3f(off).add(at);
                if (last != null) {
                    float bright = (float) Math.pow(1 - s, 1.6) * 0.85f + 0.15f * (1 - s);
                    float depth = off.dot(look);
                    if (depth > 0 && new Vector3f(off).sub(new Vector3f(look).mul(depth)).length() < r * 1.1f) {
                        bright *= 0.35f;
                    }
                    FxDraw.ribbon(last, point, width * 4, FxDraw.BEAM, 0, VIOLET, 0.25f * bright * open, true);
                    if (!light) {
                        FxDraw.ribbon(last, point, width * 2.2f, FxDraw.BEAM, 0.2f, LILAC, 0.45f * bright * open, true);
                    }
                    FxDraw.ribbon(last, point, width, FxDraw.BEAM, 0.7f, WHITE, bright * open, true);
                }
                last = point;
            }
            // The bright head of the ring.
            Vector3f tip = new Vector3f(u).mul(Mth.cos(head) * ring).add(new Vector3f(v).mul(Mth.sin(head) * ring)).add(at);
            FxDraw.billboard(tip, r * 0.5f, 0, FxDraw.SOFT, 0.6f, PALE, 0.5f * open, true);
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
