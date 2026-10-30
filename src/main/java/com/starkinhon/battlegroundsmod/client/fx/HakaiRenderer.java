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
// references: a dark magenta sphere with a black rim, magenta swirls turning inside, a white sparkle in the middle,
// thin black lines orbiting across it and a pink glow all around; whoever is trapped inside glows magenta. And the
// hakaizinho (Hakaizinho): a tiny pink four-pointed sparkle with a thin streak behind it.
// The black parts are drawn in overlay(), after the glow layer, so the light doesn't wash them out.
public final class HakaiRenderer {
    // prev/pos: the last two positions the server sent (one per tick), to slide between them.
    private record Ball(int mode, Vec3 prev, Vec3 pos, int target, float prevRadius, float radius, long updated, long born) {
    }

    private record Shot(Vec3 start, Vec3 velocity, int ticks, long born) {
    }

    private static final Map<Integer, Ball> BALLS = new HashMap<>();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BLACK = {0, 0, 0};
    private static final float[] DARK = FxDraw.rgb(0x2A0536);
    private static final float[] DEEP = FxDraw.rgb(0x5E0A6E);
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
        BALLS.put(id, old == null
                ? new Ball(mode, pos, pos, target, radius, radius, now, now)
                : new Ball(mode, old.pos(), pos, target, old.radius(), radius, now, old.born()));
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
        for (Map.Entry<Integer, Ball> entry : BALLS.entrySet()) {
            Ball b = entry.getValue();
            float age = (float) (now - b.born());
            float r = Mth.lerp(partial, b.prevRadius(), b.radius());
            Vector3f at = b.prev().lerp(b.pos(), partial).subtract(camera).toVector3f();
            int seed = entry.getKey() * 977 + 13;
            boolean trapped = b.mode() == 1;
            drawBall(at, r, age, seed, left, up, trapped);
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

    // After the glow layer: the black rim and the black lines going around the balls.
    static void overlay(Vec3 camera, double now, float partial) {
        if (BALLS.isEmpty()) {
            return;
        }
        Vector3f look = new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
        for (Map.Entry<Integer, Ball> entry : BALLS.entrySet()) {
            Ball b = entry.getValue();
            float age = (float) (now - b.born());
            float r = Mth.lerp(partial, b.prevRadius(), b.radius());
            Vector3f at = b.prev().lerp(b.pos(), partial).subtract(camera).toVector3f();
            int seed = entry.getKey() * 977 + 13;
            FxDraw.billboard(at, r * 2.02f, 0, FxDraw.RING, 0.07f, BLACK, 0.95f, false);
            orbits(at, r, age, seed, look);
        }
    }

    private static void drawBall(Vector3f at, float r, float age, int seed, Vector3f left, Vector3f up, boolean trapped) {
        float pulse = 1 + 0.03f * Mth.sin(age * 0.35f);
        // Pink light all around it, then the dark body.
        FxDraw.billboard(at, r * 3.6f * pulse, 0, FxDraw.SOFT, 0.05f, PINK, 0.42f, true);
        FxDraw.billboard(at, r * 2.6f * pulse, 0, FxDraw.SOFT, 0.1f, MAGENTA, 0.5f, true);
        FxDraw.billboard(at, r * 2, 0, FxDraw.DISC, 0.12f, DARK, trapped ? 0.55f : 0.95f, false);
        FxDraw.billboard(at, r * 1.7f, 0, FxDraw.SOFT, 0, DEEP, trapped ? 0.3f : 0.6f, false);
        // Magenta light inside, brighter towards the middle.
        FxDraw.billboard(at, r * 1.75f, 0, FxDraw.SOFT, 0.05f, MAGENTA, 0.55f, true);
        // Swirls turning inside, arms winding out from the middle.
        int arms = 6;
        int steps = 10;
        for (int k = 0; k < arms; k++) {
            float base = age * 0.07f + k * Mth.TWO_PI / arms + FxWorld.hash(seed, k, 1);
            float wobble = 0.85f + 0.15f * Mth.sin(age * 0.11f + k);
            Vector3f last = null;
            for (int j = 0; j <= steps; j++) {
                float s = j / (float) steps;
                float a = base + s * 2.6f;
                float d = r * 0.88f * s * wobble;
                Vector3f point = new Vector3f(left).mul(Mth.cos(a) * d).add(new Vector3f(up).mul(Mth.sin(a) * d)).add(at);
                if (last != null) {
                    FxDraw.ribbon(last, point, r * (0.16f - 0.1f * s), FxDraw.STREAK, 0.25f, s < 0.5f ? HOT : MAGENTA, 0.75f * (1 - s * 0.6f), true);
                }
                last = point;
            }
        }
        // Flecks of light drifting around inside.
        for (int k = 0; k < 18; k++) {
            float a = age * (0.03f + 0.04f * FxWorld.hash(seed, k, 2)) + FxWorld.hash(seed, k, 3) * Mth.TWO_PI;
            float d = r * (0.2f + 0.65f * FxWorld.hash(seed, k, 4));
            Vector3f point = new Vector3f(left).mul(Mth.cos(a) * d).add(new Vector3f(up).mul(Mth.sin(a) * d)).add(at);
            float twinkle = 0.5f + 0.5f * Mth.sin(age * 0.4f + k * 1.7f);
            FxDraw.billboard(point, r * 0.12f, 0, FxDraw.SOFT, 0.6f, SOFT_PINK, 0.7f * twinkle, true);
        }
        // Pink edge just outside the black rim, and the white sparkle in the middle.
        FxDraw.billboard(at, r * 2.16f, 0, FxDraw.RING, 0.06f, HOT, 0.85f, true);
        float twinkle = 0.85f + 0.15f * Mth.sin(age * 0.5f);
        FxDraw.billboard(at, r * 1.1f * twinkle, age * 0.02f, FxDraw.FLARE, 4 / 32f, SOFT_PINK, 0.95f, true);
        FxDraw.billboard(at, r * 0.32f, 0, FxDraw.SOFT, 0.9f, WHITE, 1, true);
    }

    // Three thin black rings going around the sphere at a slant, turning; the half behind the sphere is hidden by it.
    private static void orbits(Vector3f at, float r, float age, int seed, Vector3f look) {
        int segments = 56;
        for (int k = 0; k < 3; k++) {
            Vector3f axis = FxWorld.sphere(seed, k + 5);
            axis.rotateY(age * (0.012f + 0.006f * k));
            Vector3f x = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f y = new Vector3f(axis).cross(x);
            float ring = r * (1.18f + 0.14f * k);
            float spin = age * (0.03f + 0.015f * k) * (k % 2 == 0 ? 1 : -1);
            Vector3f last = null;
            boolean lastHidden = false;
            for (int j = 0; j <= segments; j++) {
                float a = spin + j * Mth.TWO_PI / segments;
                Vector3f off = new Vector3f(x).mul(Mth.cos(a) * ring).add(new Vector3f(y).mul(Mth.sin(a) * ring));
                float depth = off.dot(look);
                boolean hidden = depth > 0 && new Vector3f(off).sub(new Vector3f(look).mul(depth)).length() < r;
                Vector3f point = off.add(at);
                if (last != null && !hidden && !lastHidden) {
                    FxDraw.ribbon(last, point, r * 0.035f, FxDraw.BEAM, 0.5f, BLACK, 0.9f, false);
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
        FxDraw.billboard(middle, height * 1.5f, 0, FxDraw.SOFT, 0.2f, HOT, 0.6f * flicker, true);
        for (int k = 0; k < 6; k++) {
            float h = height * (0.1f + 0.8f * k / 5f);
            Vector3f point = new Vector3f(feet).add((FxWorld.hash(seed, k, 7) - 0.5f) * width, h, (FxWorld.hash(seed, k, 8) - 0.5f) * width);
            FxDraw.billboard(point, Math.max(0.6f, width * 1.6f), age * 0.05f + k, FxDraw.SOFT, 0.5f, k % 2 == 0 ? PINK : MAGENTA, 0.55f * flicker,
                    true);
        }
        FxDraw.billboard(middle, height * 0.5f, 0, FxDraw.SOFT, 0.8f, SOFT_PINK, 0.5f * flicker, true);
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
