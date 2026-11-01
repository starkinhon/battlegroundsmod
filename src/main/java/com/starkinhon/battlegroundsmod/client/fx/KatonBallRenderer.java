package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// The giant Katon's fireball (Shift + Z), drawn only with shader fire like the rest of souls_vfx: a swirling shell of
// flame tongues turning around a white-hot core, inside a wide orange glow. Nothing is simulated: every flame is a fixed
// hash of its index, moved by the ball's age, so it never jitters. The server says when it grows, flies and blows up.
public final class KatonBallRenderer {
    // mode 0: growing over the caster's head (offset = pos); 1: flying from pos at velocity per tick.
    private record Ball(int caster, int mode, Vec3 pos, Vec3 velocity, float radius, float maxRadius, int ticks, long start, AttackClock clock) {
    }

    private static final Map<Integer, Ball> BALLS = new HashMap<>();
    private static final float[] CORE = FxDraw.rgb(0xFFF6D0);
    private static final float[] HOT = FxDraw.rgb(0xFFD040);
    private static final float[] BODY = FxDraw.rgb(0xFF7A10);
    private static final float[] EMBER = FxDraw.rgb(0xD8300A);

    private KatonBallRenderer() {
    }

    public static void update(int id, int caster, int mode, Vec3 pos, Vec3 velocity, float radius, float maxRadius, int ticks) {
        if (mode == 2) {
            BALLS.remove(id);
            return;
        }
        BALLS.put(id, new Ball(caster, mode, pos, velocity, radius, maxRadius, Math.max(1, ticks), ScreenFx.now(), new AttackClock()));
    }

    public static boolean active() {
        return !BALLS.isEmpty();
    }

    public static void clear() {
        BALLS.clear();
    }

    // Called from FxWorld while its buffers are open.
    static void render(Vec3 camera, double now, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            BALLS.clear();
            return;
        }
        BALLS.values().removeIf(b -> b.mode() == 1 && b.clock().peek(now - b.start()) > b.ticks() + 2 || now - b.start() > 20 * 60);
        for (var entry : BALLS.entrySet()) {
            Ball b = entry.getValue();
            float age = (float) (now - b.start());
            Vec3 center;
            float radius;
            Vec3 dir = null;
            if (b.mode() == 0) {
                Entity caster = level.getEntity(b.caster());
                if (caster == null) {
                    continue;
                }
                float k = Mth.clamp(age / b.ticks(), 0, 1);
                radius = Mth.lerp(k * k * (3 - 2 * k), b.radius(), b.maxRadius());
                center = caster.getEyePosition(partial).add(b.pos()).add(0, radius, 0);
            } else {
                // Stopped in the air by Gojo's shockwave, like on the server.
                age = (float) b.clock().age(age, a -> b.pos().add(b.velocity().scale(Math.min(a, b.ticks()))));
                radius = b.radius();
                center = b.pos().add(b.velocity().scale(Math.min(age, b.ticks())));
                dir = b.velocity().normalize();
            }
            draw(center.subtract(camera).toVector3f(), dir == null ? null : dir.toVector3f(), radius, age, entry.getKey() * 7919);
        }
    }

    private static void draw(Vector3f at, Vector3f dir, float r, float age, int seed) {
        // Wide glow first, so it blooms around the ball.
        FxDraw.billboard(at, r * 3.4f, 0, FxDraw.SOFT, 0.1f, BODY, 0.45f, true);
        FxDraw.billboard(at, r * 2.2f, 0, FxDraw.SOFT, 0.3f, HOT, 0.5f, true);
        // Flame tongues on the shell, turning around a tilted axis like the swirls in the anime ball. Inner ones run
        // faster and are hotter; outer ones are slower, bigger and redder.
        int count = Mth.clamp((int) (36 + r * 9), 36, 96);
        Vector3f axis = new Vector3f(0.25f, 1, 0.15f).normalize();
        for (int k = 0; k < count; k++) {
            float layer = FxWorld.hash(seed, k, 1);
            Vector3f d = FxWorld.sphere(seed, k);
            float spin = age * (0.09f - 0.05f * layer) + FxWorld.hash(seed, k, 2) * 6;
            d.rotateAxis(spin, axis.x, axis.y, axis.z);
            float boil = 1 + 0.14f * Mth.sin(age * 0.35f + k * 1.7f);
            float shell = r * (0.35f + 0.5f * layer);
            Vector3f pos = new Vector3f(d).mul(shell).add(at);
            float size = r * (0.55f + 0.45f * layer) * boil;
            float[] color = layer < 0.4f ? FxDraw.mix(HOT, BODY, layer / 0.4f) : FxDraw.mix(BODY, EMBER, (layer - 0.4f) / 0.6f * 0.7f);
            FxDraw.billboard(pos, size, FxWorld.hash(seed, k, 3) * 6 + age * 0.04f, FxDraw.FIRE, 0.1f + 0.4f * layer, color, 0.95f, true);
        }
        // Spiral tongues wrapped around the ball (the "rolling" look of the reference).
        for (int k = 0; k < 7; k++) {
            Vector3f n = FxWorld.sphere(seed + 31, k);
            Vector3f x = new Vector3f(n).cross(Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f y = new Vector3f(n).cross(x);
            float a0 = age * 0.16f + k * 2.1f;
            Vector3f last = null;
            for (int j = 0; j <= 6; j++) {
                float a = a0 + j * 0.28f;
                float rr = r * (0.92f + 0.06f * Mth.sin(age * 0.4f + j));
                Vector3f p = new Vector3f(x).mul(Mth.cos(a) * rr).add(new Vector3f(y).mul(Mth.sin(a) * rr)).add(at);
                if (last != null) {
                    FxDraw.ribbon(last, p, r * 0.22f * (1 - j / 7f), FxDraw.BEAM, 0.5f, FxDraw.mix(HOT, BODY, j / 6f), 0.7f, true);
                }
                last = p;
            }
        }
        FxDraw.billboard(at, r * 1.5f, 0, FxDraw.SOFT, 0.9f, CORE, 0.85f, true);
        if (dir != null) {
            // Flying: fire left behind it, smaller and redder further back.
            for (int j = 1; j <= 10; j++) {
                float f = j / 10f;
                Vector3f pos = new Vector3f(dir).mul(-r * (0.4f + f * 2.4f)).add(at)
                        .add((FxWorld.hash(seed, j, 5) - 0.5f) * r * 0.5f, (FxWorld.hash(seed, j, 6) - 0.5f) * r * 0.5f + f * r * 0.3f, 0);
                FxDraw.billboard(pos, r * (1.1f - f * 0.6f), age * 0.1f + j, FxDraw.FIRE, 0.3f + f * 0.6f, FxDraw.mix(BODY, EMBER, f), 1 - f * 0.8f,
                        true);
            }
        }
    }
}
