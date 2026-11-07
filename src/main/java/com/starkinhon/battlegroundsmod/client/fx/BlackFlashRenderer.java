package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// The Black Flash (power/powers/gojo/AzulFlash), drawn with shader shapes only: where the fist meets the target, black
// lightning with a red glow bursts out, most of it along the punch and the rest all around, crackling (re-rolled every
// two ticks) and branching; while the world is stopped on the hit (stop ticks) the lightning is frozen too, then it
// keeps crackling as it fades. Under it a red-hot flare and a white core that only lasts the first instant.
// The red glow goes in render() (to the bloom); the black cores of the bolts go in overlay(), after the glow, so the
// light doesn't wash them out (the same as the hakai's dark arcs).
public final class BlackFlashRenderer {
    private record Flash(Vec3 pos, Vector3f dir, float charge, int stop, long born, int seed) {
    }

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final float[] BLACK = {0, 0, 0};
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] DEEP = FxDraw.rgb(0x6A000C);
    private static final float[] RED = FxDraw.rgb(0xFF0A1E);
    private static final float[] HOT = FxDraw.rgb(0xFF5A68);
    private static int seeds;

    private BlackFlashRenderer() {
    }

    public static void flash(Vec3 pos, Vec3 dir, float charge, int stop) {
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        FLASHES.add(new Flash(pos, d.toVector3f(), Mth.clamp(charge, 0, 1), Math.max(1, stop), ScreenFx.now(), 7919 * ++seeds));
    }

    public static boolean active() {
        return !FLASHES.isEmpty();
    }

    public static void clear() {
        FLASHES.clear();
    }

    private static float life(Flash f) {
        return f.stop + 22 + 10 * f.charge;
    }

    static void render(Vec3 camera, double now, float partial) {
        FLASHES.removeIf(f -> now - f.born > life(f));
        for (Flash f : FLASHES) {
            float age = (float) (now - f.born);
            Vector3f at = f.pos.subtract(camera).toVector3f();
            float fade = fade(f, age);
            float c = f.charge;
            // The white-hot instant of the contact.
            float core = 1 - smooth(age / (f.stop + 2f));
            FxDraw.billboard(at, (1.6f + 1.2f * c) * (0.6f + 0.4f * core), age * 0.4f, FxDraw.SOFT, 0.9f, WHITE, core, true);
            // The red-hot flare, its rays turning slowly.
            float burst = easeOut(age / 3f);
            FxDraw.billboard(at, (5 + 4 * c) * burst, f.seed + age * 0.05f, FxDraw.FLARE, 0.35f, RED, 0.9f * fade, true);
            FxDraw.billboard(at, (9 + 6 * c) * burst, 0, FxDraw.SOFT, 0.2f, DEEP, 0.7f * fade, true);
            // The glow of every bolt: wide dark red, then a thinner hot red.
            bolts(f, at, age, (from, to, w, o) -> {
                FxDraw.ribbon(from, to, w * 4.5f, FxDraw.BEAM, 0.15f, DEEP, o * 0.75f, true);
                FxDraw.ribbon(from, to, w * 2.2f, FxDraw.BEAM, 0.45f, RED, o, true);
                FxDraw.ribbon(from, to, w * 1.3f, FxDraw.BEAM, 0.8f, HOT, o * 0.6f, true);
            });
        }
    }

    // The black cores of the bolts, over the glow.
    static void overlay(Vec3 camera, double now, float partial) {
        for (Flash f : FLASHES) {
            float age = (float) (now - f.born);
            Vector3f at = f.pos.subtract(camera).toVector3f();
            bolts(f, at, age, (from, to, w, o) -> FxDraw.ribbon(from, to, w, FxDraw.BEAM, 0, BLACK, Math.min(1, o * 1.15f), false));
            // A few black shards of ink thrown out of the contact in the first instant.
            float t = age / (f.stop + 8f);
            if (t < 1) {
                int n = 8 + Math.round(6 * f.charge);
                for (int k = 0; k < n; k++) {
                    Vector3f d = direction(f, k + 100);
                    float reach = (1.2f + 2.2f * f.charge) * easeOut(t) * (0.6f + 0.6f * FxWorld.hash(f.seed, k, 31));
                    Vector3f a = new Vector3f(d).mul(reach * 0.35f).add(at);
                    Vector3f b = new Vector3f(d).mul(reach).add(at);
                    FxDraw.ribbon(a, b, 0.12f * (1 - t), FxDraw.STREAK, 0, BLACK, 0.9f * (1 - t), false);
                }
            }
        }
    }

    private interface Segment {
        void draw(Vector3f from, Vector3f to, float width, float opacity);
    }

    // Every bolt of the flash right now: jagged lines out of the contact, each with a branch, re-rolled every two
    // ticks (frozen while the world is stopped). Fewer and fewer as it fades.
    private static void bolts(Flash f, Vector3f at, float age, Segment out) {
        float fade = fade(f, age);
        if (fade <= 0.01f) {
            return;
        }
        float c = f.charge;
        int count = 12 + Math.round(10 * c);
        float length = (3.2f + 3.4f * c) * easeOut(age / 2.5f);
        int epoch = age < f.stop ? 0 : 1 + (int) ((age - f.stop) / 2);
        float alive = age < f.stop + 4 ? 1 : fade;
        for (int k = 0; k < count; k++) {
            int seed = f.seed + epoch * 131 + k * 17;
            if (FxWorld.hash(seed, k, 11) > 0.35f + 0.65f * alive) {
                continue;
            }
            Vector3f d = direction(f, k + epoch * 3);
            float len = length * (0.55f + 0.6f * FxWorld.hash(seed, k, 12));
            float width = (0.07f + 0.05f * c) * (0.7f + 0.6f * FxWorld.hash(seed, k, 13));
            Vector3f end = jagged(at, d, len, width, seed, 8, fade, out);
            // A branch from about the middle, off to one side.
            if (FxWorld.hash(seed, k, 14) < 0.75f) {
                Vector3f side = perpendicular(d, seed);
                Vector3f bd = new Vector3f(d).add(side.mul(0.9f)).normalize();
                Vector3f mid = new Vector3f(at).lerp(end, 0.35f + 0.3f * FxWorld.hash(seed, k, 15));
                jagged(mid, bd, len * 0.45f, width * 0.65f, seed + 977, 5, fade * 0.85f, out);
            }
        }
    }

    // One jagged line from 'from' along d; returns where it ends.
    private static Vector3f jagged(Vector3f from, Vector3f d, float len, float width, int seed, int segments, float opacity, Segment out) {
        Vector3f side = perpendicular(d, seed);
        Vector3f up = new Vector3f(d).cross(side).normalize();
        Vector3f last = new Vector3f(from);
        for (int s = 1; s <= segments; s++) {
            float f = s / (float) segments;
            float jag = len * 0.13f * (s == segments ? 0.4f : 1);
            Vector3f next = new Vector3f(d).mul(len * f).add(from)
                    .add(new Vector3f(side).mul((FxWorld.hash(seed, s, 1) * 2 - 1) * jag))
                    .add(new Vector3f(up).mul((FxWorld.hash(seed, s, 2) * 2 - 1) * jag));
            // Thick at the root, a thread at the tip.
            out.draw(last, next, width * (1.15f - 0.85f * f), opacity * (1 - f * 0.45f));
            last = next;
        }
        return last;
    }

    // Bolt k's direction: two in three along the punch (a cone of about 55 degrees), the rest anywhere around.
    private static Vector3f direction(Flash f, int k) {
        Vector3f any = FxWorld.sphere(f.seed, k);
        if (FxWorld.hash(f.seed, k, 3) < 0.66f) {
            return new Vector3f(f.dir).mul(1.6f).add(any).normalize();
        }
        return any;
    }

    private static Vector3f perpendicular(Vector3f d, int seed) {
        Vector3f helper = Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f side = new Vector3f(d).cross(helper).normalize();
        float turn = FxWorld.hash(seed, 0, 9) * Mth.TWO_PI;
        return side.rotateAxis(turn, d.x, d.y, d.z).normalize();
    }

    // Full while the world is stopped and a moment after, then out.
    private static float fade(Flash f, float age) {
        float hold = f.stop + 4;
        return age < hold ? 1 : 1 - smooth((age - hold) / (life(f) - hold));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float easeOut(float t) {
        float u = 1 - Mth.clamp(t, 0, 1);
        return 1 - u * u * u;
    }
}
