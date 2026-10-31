package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Bills' sphere of destruction, a sun drawn only with shader light (no textures): a boiling star (shape SUN, white-hot
// middle, orange cells, yellow streaks, glowing rim, flames past it), a red-orange corona, loops of fire rising off
// the rim and white lightning crackling around it. The same look in space
// (SpaceScene) and when it falls on the target, following its head on the path the server lands it with.
public final class EsferaRenderer {
    // ride: the sun sits on the target and comes down with it (the Esfera da Destruição Planetária) instead of
    // falling onto its head from start.
    private record Fall(int target, Vec3 start, Vec3 lastHead, int ticks, float radius, long born, boolean ride) {
    }

    private static final List<Fall> FALLS = new ArrayList<>();
    private static final float[] CORE = FxDraw.rgb(0xFFFBEA);
    private static final float[] HOT = FxDraw.rgb(0xFFE65A);
    private static final float[] BODY = FxDraw.rgb(0xFF9A1E);
    private static final float[] EMBER = FxDraw.rgb(0xD8420E);
    private static final float[] WHITE = {1, 1, 1};
    // How far the flames reach past the rim, as a share of the radius.
    private static final float FLAMES = 0.35f;

    private EsferaRenderer() {
    }

    // A sun falling from start onto a target's head over ticks (the old Shift + C fall; kept for anything that sends one).
    public static void fall(int targetId, Vec3 start, Vec3 lastHead, int ticks, float radius) {
        FALLS.add(new Fall(targetId, start, lastHead, Math.max(1, ticks), radius, ScreenFx.now(), false));
    }

    // A sun on top of the target, pushing it down for ticks (gone when it lands).
    public static void ride(int targetId, Vec3 lastHead, int ticks, float radius) {
        FALLS.add(new Fall(targetId, lastHead, lastHead, Math.max(1, ticks), radius, ScreenFx.now(), true));
    }

    private static Vec3 center(Fall f, Vec3 head, double k) {
        return f.ride() ? head.add(0, f.radius() * 0.85, 0) : fallAt(f.start(), head, k);
    }

    // From start towards the head it follows, slow and heavy, a little faster at the end.
    private static Vec3 fallAt(Vec3 start, Vec3 head, double k) {
        k = Math.max(0, Math.min(1, k));
        return start.lerp(head, k * 0.6 + k * k * 0.4);
    }

    public static boolean active() {
        return !FALLS.isEmpty();
    }

    // Pulverized on the way down (a hakaizinho): the sphere near pos is gone.
    public static void erase(Vec3 pos, double radius) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = ScreenFx.now();
        FALLS.removeIf(f -> {
            Entity target = level.getEntity(f.target());
            Vec3 head = target != null && target.isAlive() ? target.position().add(0, target.getBbHeight(), 0) : f.lastHead();
            return center(f, head, (now - f.born()) / (double) f.ticks()).distanceTo(pos) < radius + f.radius() + 3;
        });
    }

    static void render(Vec3 camera, double now, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            FALLS.clear();
            return;
        }
        FALLS.removeIf(f -> now - f.born() > f.ticks() + 1);
        for (Fall f : FALLS) {
            Entity target = level.getEntity(f.target());
            Vec3 head = target != null && target.isAlive() ? target.getPosition(partial).add(0, target.getBbHeight(), 0) : f.lastHead();
            double k = (now - f.born()) / f.ticks();
            Vec3 center = center(f, head, k);
            Vec3 dir = f.ride() ? new Vec3(0, -1, 0) : fallAt(f.start(), head, Math.min(1, k + 0.02)).subtract(center);
            drawSun(center.subtract(camera).toVector3f(), dir.lengthSqr() < 1.0E-6 ? new Vector3f(0, -1, 0) : dir.normalize().toVector3f(), f.radius(),
                    (float) (now - f.born()), f.target() * 131 + 7);
        }
    }

    // dir: where it flies (fire and light left behind it), or null when it stays still.
    static void drawSun(Vector3f at, Vector3f dir, float r, float age, int seed) {
        Camera view = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(view.getLeftVector());
        Vector3f up = new Vector3f(view.getUpVector());
        // Corona into the bloom: wide red-orange, then hot yellow hugging the rim.
        FxDraw.billboard(at, r * 5.0f, 0, FxDraw.SOFT, 0, EMBER, 0.3f, true);
        FxDraw.billboard(at, r * 3.0f, 0, FxDraw.SOFT, 0.1f, BODY, 0.45f, true);
        // The star itself, on the alpha layer so the bloom doesn't wash its surface out (shape SUN in souls_vfx).
        FxDraw.billboard(at, r * 2 * (1 + FLAMES), age * 0.002f, FxDraw.SUN, FLAMES, WHITE, 1, false);
        // A touch of light over the middle and the rim.
        FxDraw.billboard(at, r * 1.1f, 0, FxDraw.SOFT, 0.7f, CORE, 0.3f, true);
        FxDraw.billboard(at, r * 2.06f, 0, FxDraw.RING, 0.04f, HOT, 0.55f, true);
        // Loops of fire rising off the rim and falling back, each on its own cycle.
        for (int k = 0; k < 9; k++) {
            float life = 50 + 40 * FxWorld.hash(seed, k, 1);
            float u = ((age + FxWorld.hash(seed, k, 2) * life) % life) / life;
            float base = FxWorld.hash(seed, k, 3) * Mth.TWO_PI + age * 0.003f;
            float span = 0.18f + 0.22f * FxWorld.hash(seed, k, 4);
            float height = r * (0.15f + 0.35f * FxWorld.hash(seed, k, 5)) * Mth.sin(u * Mth.PI);
            Vector3f last = null;
            for (int j = 0; j <= 10; j++) {
                float s = j / 10f;
                float a = base - span / 2 + span * s;
                float out = r * 0.98f + height * Mth.sin(s * Mth.PI);
                Vector3f point = new Vector3f(left).mul(Mth.cos(a) * out).add(new Vector3f(up).mul(Mth.sin(a) * out)).add(at);
                if (last != null) {
                    FxDraw.ribbon(last, point, r * 0.07f, FxDraw.STREAK, 0.4f, s > 0.3f && s < 0.7f ? HOT : BODY, 0.8f * Mth.sin(u * Mth.PI), true);
                }
                last = point;
            }
        }
        // White lightning crackling around the edge, a new shape every couple of ticks.
        int flick = (int) (age / 2);
        for (int k = 0; k < 7; k++) {
            if (FxWorld.hash(seed + flick, k, 6) < 0.45f) {
                continue;
            }
            float a = FxWorld.hash(seed + flick, k, 7) * Mth.TWO_PI;
            Vector3f last = new Vector3f(left).mul(Mth.cos(a) * r).add(new Vector3f(up).mul(Mth.sin(a) * r)).add(at);
            for (int j = 1; j <= 6; j++) {
                float aj = a + (FxWorld.hash(seed + flick, k * 8 + j, 8) - 0.5f) * 0.25f + j * 0.04f;
                float out = r * (1 + j * 0.05f + 0.04f * FxWorld.hash(seed + flick, k * 8 + j, 9));
                Vector3f point = new Vector3f(left).mul(Mth.cos(aj) * out).add(new Vector3f(up).mul(Mth.sin(aj) * out)).add(at);
                FxDraw.ribbon(last, point, r * 0.025f, FxDraw.STREAK, 0.9f, CORE, 0.9f, true);
                last = point;
            }
        }
        if (dir != null) {
            // Fire and light trailing behind it.
            for (int j = 1; j <= 12; j++) {
                float f = j / 12f;
                Vector3f pos = new Vector3f(dir).mul(-r * (0.5f + f * 3.2f)).add(at)
                        .add((FxWorld.hash(seed, j, 5) - 0.5f) * r * 0.6f, (FxWorld.hash(seed, j, 6) - 0.5f) * r * 0.6f, (FxWorld.hash(seed, j, 7) - 0.5f) * r * 0.6f);
                FxDraw.billboard(pos, r * (1.3f - f * 0.7f), age * 0.1f + j, FxDraw.FIRE, 0.25f + f * 0.6f, FxDraw.mix(BODY, EMBER, f), 0.8f - f * 0.7f,
                        true);
            }
        }
    }
}
