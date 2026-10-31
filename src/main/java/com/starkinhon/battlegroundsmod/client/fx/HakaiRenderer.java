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
import net.minecraft.world.phys.Vec3;

// Bills' hakai, drawn with shader shapes only (no textures). The hakai ball (power/powers/bills/HakaiBola), like the
// reference: a violet sphere, black-purple at the edge and lighter towards the middle, cloudy swirls inside, a white
// starburst in the center, thin black arcs of aura going around it and a soft lilac glow; whoever is trapped inside
// glows magenta. And the
// hakaizinho (Hakaizinho): a tiny pink four-pointed sparkle with a thin streak behind it.
// The dark rim and the black arcs are drawn in overlay(), after the glow layer, so the light doesn't wash them out.
public final class HakaiRenderer {
    // track: the positions the server sent (one per tick), smoothed (SmoothTrack).
    private record Ball(int mode, SmoothTrack track, int target, long updated, long born) {
    }

    private record Shot(Vec3 start, Vec3 velocity, int ticks, long born) {
    }

    private static final Map<Integer, Ball> BALLS = new HashMap<>();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BLACK = {0, 0, 0};
    private static final float[] EDGE = FxDraw.rgb(0x16021F);
    private static final float[] PURPLE_DARK = FxDraw.rgb(0x4A0A7A);
    private static final float[] PURPLE = FxDraw.rgb(0x8A18CC);
    private static final float[] PURPLE_LIGHT = FxDraw.rgb(0xB840F2);
    private static final float[] PALE = FxDraw.rgb(0xE6A8FF);
    private static final float[] VIOLET = FxDraw.rgb(0xA62BEA);
    private static final float[] LILAC = FxDraw.rgb(0xE28CF5);
    private static final float[] MAGENTA = FxDraw.rgb(0xD81ECF);
    private static final float[] HOT = FxDraw.rgb(0xFF4FE6);
    private static final float[] PINK = FxDraw.rgb(0xFF7BEA);
    private static final float[] SOFT_PINK = FxDraw.rgb(0xFFC4F6);

    private HakaiRenderer() {
    }

    public static void ball(int id, int mode, Vec3 pos, int target, float radius, boolean awakened) {
        if (mode == 2) {
            BALLS.remove(id);
            return;
        }
        long now = ScreenFx.now();
        Ball old = BALLS.get(id);
        if (old == null) {
            BALLS.put(id, new Ball(mode, new SmoothTrack(pos, radius), target, now, now));
            return;
        }
        old.track().push(pos, radius);
        BALLS.put(id, new Ball(mode, old.track(), target, now, old.born()));
    }

    public static void shot(Vec3 start, Vec3 velocity, int ticks, boolean awakened) {
        SHOTS.add(new Shot(start, velocity, Math.max(1, ticks), ScreenFx.now()));
    }

    // An attack in flight was pulverized by a hakaizinho: whatever drew it near pos stops.
    public static void erase(String skill, Vec3 pos, float radius) {
        MeteorRenderer.erase(pos, radius);
        EsferaRenderer.erase(pos, radius);
        FxWorld.erase(skill);
    }

    public static boolean active() {
        return !BALLS.isEmpty() || !SHOTS.isEmpty();
    }

    public static void clear() {
        BALLS.clear();
        SHOTS.clear();
    }

    // With the other world effects: the ball's body and light, the trapped target's glow, the hakaizinho.
    static void render(Vec3 camera, double now, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }
        // A ball the server stopped talking about (out of range, say) goes away on its own.
        BALLS.values().removeIf(b -> now - b.updated() > 6);
        SHOTS.removeIf(s -> now - s.born() > s.ticks() + 1);
        Camera view = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(view.getLeftVector());
        Vector3f up = new Vector3f(view.getUpVector());
        Vector3f look = new Vector3f(view.getLookVector());
        for (Map.Entry<Integer, Ball> entry : BALLS.entrySet()) {
            Ball b = entry.getValue();
            float age = (float) (now - b.born());
            b.track().sample(now);
            float r = b.track().radius();
            Vector3f at = b.track().pos().subtract(camera).toVector3f();
            int seed = entry.getKey() * 977 + 13;
            boolean trapped = b.mode() == 1;
            drawBall(at, r, age, seed, left, up, look, trapped);
            if (trapped && level.getEntity(b.target()) instanceof Entity target) {
                glowTrapped(target.getPosition(partial).subtract(camera).toVector3f(), target.getBbHeight(), target.getBbWidth(), age, seed);
            }
        }
        for (Shot s : SHOTS) {
            double age = now - s.born();
            if (age < 0 || age > s.ticks()) {
                continue;
            }
            drawShot(s, age, camera);
        }
    }

    // After the glow layer: the dark rim and the black aura lines going around the balls.
    static void overlay(Vec3 camera, double now, float partial) {
        if (BALLS.isEmpty()) {
            return;
        }
        Vector3f look = new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
        for (Map.Entry<Integer, Ball> entry : BALLS.entrySet()) {
            Ball b = entry.getValue();
            float age = (float) (now - b.born());
            b.track().sample(now);
            float r = b.track().radius();
            Vector3f at = b.track().pos().subtract(camera).toVector3f();
            int seed = entry.getKey() * 977 + 13;
            // A wide band darkening the inside of the edge, then the thin black outline.
            FxDraw.billboard(layer(at, look, r, 0), r * 2.0f, 0, FxDraw.RING, 0.22f, EDGE, 0.8f, false);
            FxDraw.billboard(layer(at, look, r, 1), r * 2.04f, 0, FxDraw.RING, 0.06f, BLACK, 1, false);
            orbits(at, r, age, seed, look);
        }
    }

    // Built like the reference: a violet sphere, black-purple at the edge and lighter towards the middle, cloudy and
    // swirling inside, a white starburst in the center and a soft lilac glow around it. Most of it is on the alpha
    // layer so the bloom doesn't wash it out to white.
    private static void drawBall(Vector3f at, float r, float age, int seed, Vector3f left, Vector3f up, Vector3f look, boolean trapped) {
        float pulse = 1 + 0.025f * Mth.sin(age * 0.3f);
        float body = trapped ? 0.6f : 1;
        // Glow around it, kept soft.
        FxDraw.billboard(at, r * 3.5f * pulse, 0, FxDraw.SOFT, 0, LILAC, 0.3f, true);
        FxDraw.billboard(at, r * 2.4f * pulse, 0, FxDraw.SOFT, 0, VIOLET, 0.3f, true);
        // The body: discs from the dark edge to the light middle, each a hair closer to the camera so they stack in order.
        FxDraw.billboard(layer(at, look, r, 0), r * 2.0f, 0, FxDraw.DISC, 0.06f, EDGE, trapped ? 0.75f : 1, false);
        FxDraw.billboard(layer(at, look, r, 1), r * 1.92f, 0, FxDraw.DISC, 0.35f, PURPLE_DARK, 0.95f * body, false);
        FxDraw.billboard(layer(at, look, r, 2), r * 1.68f, 0, FxDraw.DISC, 0.55f, PURPLE, 0.9f * body, false);
        // Clouds of lighter and darker purple drifting inside.
        for (int k = 0; k < 14; k++) {
            float a = age * (0.012f + 0.02f * FxWorld.hash(seed, k, 2)) + FxWorld.hash(seed, k, 3) * Mth.TWO_PI;
            float d = r * 0.7f * FxWorld.hash(seed, k, 4);
            Vector3f point = new Vector3f(left).mul(Mth.cos(a) * d).add(new Vector3f(up).mul(Mth.sin(a) * d)).add(layer(at, look, r, 3));
            float[] color = FxDraw.mix(PURPLE_DARK, PURPLE_LIGHT, FxWorld.hash(seed, k, 5));
            FxDraw.billboard(point, r * (0.5f + 0.4f * FxWorld.hash(seed, k, 6)), age * 0.01f + k, FxDraw.SMOKE, 0.25f, color, 0.4f * body, false);
        }
        FxDraw.billboard(layer(at, look, r, 4), r * 1.25f, 0, FxDraw.DISC, 0.75f, PURPLE_LIGHT, 0.75f * body, false);
        // Light swirls winding out from the middle.
        for (int k = 0; k < 4; k++) {
            float base = age * 0.05f + k * Mth.TWO_PI / 4 + FxWorld.hash(seed, k, 1);
            Vector3f last = null;
            for (int j = 0; j <= 10; j++) {
                float s = j / 10f;
                float a = base + s * 2.2f;
                float d = r * (0.1f + 0.75f * s);
                Vector3f point = new Vector3f(left).mul(Mth.cos(a) * d).add(new Vector3f(up).mul(Mth.sin(a) * d)).add(layer(at, look, r, 5));
                if (last != null) {
                    FxDraw.ribbon(last, point, r * (0.22f - 0.14f * s), FxDraw.STREAK, 0, PALE, 0.45f * (1 - s) * body, false);
                }
                last = point;
            }
        }
        FxDraw.billboard(layer(at, look, r, 6), r * 0.75f, 0, FxDraw.DISC, 0.9f, PALE, 0.6f * body, false);
        // The white starburst in the middle, into the bloom.
        float twinkle = 0.85f + 0.15f * Mth.sin(age * 0.5f);
        FxDraw.billboard(at, r * 0.9f, 0, FxDraw.SOFT, 0.3f, PALE, 0.35f, true);
        FxDraw.billboard(at, r * 1.3f * twinkle, age * 0.015f, FxDraw.FLARE, 6 / 32f, WHITE, 0.85f, true);
        FxDraw.billboard(at, r * 0.22f, 0, FxDraw.SOFT, 0.95f, WHITE, 1, true);
    }

    // at nudged k steps towards the camera, so layers drawn at the same spot keep their order.
    private static Vector3f layer(Vector3f at, Vector3f look, float r, int k) {
        return new Vector3f(look).mul(-0.004f * r * k).add(at);
    }

    // Thin black arcs of aura close around the sphere at a slant, turning and fading at their ends; the part behind the
    // sphere is hidden by it.
    private static void orbits(Vector3f at, float r, float age, int seed, Vector3f look) {
        int segments = 48;
        for (int k = 0; k < 3; k++) {
            Vector3f axis = FxWorld.sphere(seed, k + 5);
            axis.rotateY(age * (0.01f + 0.005f * k));
            Vector3f x = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f y = new Vector3f(axis).cross(x);
            float ring = r * (1.06f + 0.06f * k);
            float spin = age * (0.025f + 0.012f * k) * (k % 2 == 0 ? 1 : -1);
            float length = Mth.TWO_PI * (0.55f + 0.2f * FxWorld.hash(seed, k, 9));
            Vector3f last = null;
            boolean lastHidden = false;
            for (int j = 0; j <= segments; j++) {
                float s = j / (float) segments;
                float a = spin + s * length;
                Vector3f off = new Vector3f(x).mul(Mth.cos(a) * ring).add(new Vector3f(y).mul(Mth.sin(a) * ring));
                float depth = off.dot(look);
                boolean hidden = depth > 0 && new Vector3f(off).sub(new Vector3f(look).mul(depth)).length() < r * 1.02f;
                Vector3f point = off.add(at);
                if (last != null && !hidden && !lastHidden) {
                    float fade = Math.min(1, Math.min(s, 1 - s) * 6);
                    FxDraw.ribbon(last, point, r * 0.028f * (0.4f + 0.6f * fade), FxDraw.BEAM, 0, BLACK, 0.95f * fade, false);
                }
                last = point;
                lastHidden = hidden;
            }
        }
    }

    // The body inside the ball shining magenta, flickering.
    private static void glowTrapped(Vector3f feet, float height, float width, float age, int seed) {
        float flicker = 0.8f + 0.2f * Mth.sin(age * 0.9f);
        Vector3f middle = new Vector3f(feet).add(0, height / 2, 0);
        FxDraw.billboard(middle, height * 1.3f, 0, FxDraw.SOFT, 0.1f, MAGENTA, 0.35f * flicker, true);
        for (int k = 0; k < 5; k++) {
            float h = height * (0.1f + 0.8f * k / 4f);
            Vector3f point = new Vector3f(feet).add((FxWorld.hash(seed, k, 7) - 0.5f) * width, h, (FxWorld.hash(seed, k, 8) - 0.5f) * width);
            FxDraw.billboard(point, Math.max(0.6f, width * 1.4f), age * 0.05f + k, FxDraw.SOFT, 0.2f, k % 2 == 0 ? VIOLET : MAGENTA, 0.3f * flicker,
                    true);
        }
    }

    private static void drawShot(Shot s, double age, Vec3 camera) {
        Vec3 dir = s.velocity().normalize();
        double speed = s.velocity().length();
        Vector3f at = s.start().add(s.velocity().scale(age)).subtract(camera).toVector3f();
        float tail = (float) Math.min(age * speed, 7);
        Vector3f back = new Vector3f(dir.toVector3f()).mul(-tail).add(at);
        float a = (float) age;
        FxDraw.ribbon(back, at, 0.16f, FxDraw.STREAK, 0.6f, HOT, 0.75f, true);
        FxDraw.ribbon(new Vector3f(dir.toVector3f()).mul(-tail * 0.5f).add(at), at, 0.05f, FxDraw.BEAM, 0.8f, WHITE, 0.9f, true);
        FxDraw.billboard(at, 0.9f, 0, FxDraw.SOFT, 0.2f, PINK, 0.8f, true);
        FxDraw.billboard(at, 1.5f, a * 0.25f, FxDraw.FLARE, 4 / 32f, SOFT_PINK, 1, true);
        FxDraw.billboard(at, 0.26f, 0, FxDraw.SOFT, 0.95f, WHITE, 1, true);
    }
}
