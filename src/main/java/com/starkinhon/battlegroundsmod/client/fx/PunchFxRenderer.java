package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.combat.PunchFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// The hand-to-hand moves' look (combat/PunchFx), scene "E · Alma + Choque de ar" of the Visualizador, drawn with
// shader shapes only (no vanilla particles, no smoke puffs):
// - The air shock gives the weight: the space bends at the contact, vapor rings go through the target along the punch
//   and wind lines come out of its back. Dust on the ground only for the Forte and the Dash Attack.
// - The soul gives the color: the rings and the wind are tinted with the puncher's power color, a short flash in that
//   color marks the contact and a few soul flames rise from it.
// - The Forte charging: tinted vapor rings closing on the fist, a glow and flames on it. The Forte landing: three rings,
//   a big bend, an impact frame in the soul color and a radial blur.
// - The Dash Attack sliding: a tinted vapor cone in front of the body, soul flames behind it and dust at the feet.
public final class PunchFxRenderer {
    private record Hit(int kind, Vec3 pos, Vector3f dir, float[] color, float[] light, float charge, double ground, double born, int seed, float size) {
    }

    private record Follow(int kind, int entityId, Vector3f dir, float[] color, float[] light, float a, double seen) {
    }

    // Size of each kind of hit (the Visualizador's GB_S).
    private static final float[] SCALE = {0.55f, 1f, 1.6f, 0.42f, 1.25f, 0, 0, 1f};
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] AIR = FxDraw.rgb(0xEEF5FF);
    private static final float[] DUST = FxDraw.rgb(0xA8987E);
    private static final float[] DUST_DARK = FxDraw.rgb(0x7A6C56);
    // Hits are drawn this long at most (the soul flames are the last to go).
    private static final float LIFE = 32;
    // A charge or a slide that stops being sent is gone after this many ticks.
    private static final float FOLLOW_TICKS = 2.5f;
    // Only cameras this close get the impact frame and the blur of a Forte.
    private static final double SCREEN_REACH = 16;

    private static final List<Hit> HITS = new ArrayList<>();
    private static final Map<Integer, Follow> CHARGES = new HashMap<>();
    private static final Map<Integer, Follow> TRAILS = new HashMap<>();
    private static int seeds;

    private PunchFxRenderer() {
    }

    public static void receive(int packed, int entityId, Vec3 pos, Vec3 dir, float a, float b) {
        int kind = packed & 0xF;
        int rgb = packed >>> 4 & 0xFFFFFF;
        // the puncher's scale (combat/PunchFx sizeBits): a giant's hit is drawn as big as his fist
        float size = (packed >>> 28) + 1;
        float[] color = FxDraw.rgb(rgb);
        float[] light = FxDraw.mix(color, WHITE, 0.65f);
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        double now = now();
        switch (kind) {
            case PunchFx.CHARGE -> CHARGES.put(entityId, new Follow(kind, entityId, d.toVector3f(), color, light, a, now));
            case PunchFx.TRAIL -> TRAILS.put(entityId, new Follow(kind, entityId, d.toVector3f(), color, light, a, now));
            default -> {
                if (kind >= SCALE.length) {
                    return;
                }
                CHARGES.remove(entityId);
                HITS.add(new Hit(kind, pos, d.toVector3f(), color, light, a, b, now, 7919 * ++seeds, size));
                screen(kind, pos, d, rgb, a, size);
            }
        }
    }

    public static boolean active() {
        return !HITS.isEmpty() || !CHARGES.isEmpty() || !TRAILS.isEmpty();
    }

    public static void clear() {
        HITS.clear();
        CHARGES.clear();
        TRAILS.clear();
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // What the hit does to the screen: the bend for everyone who sees it, the impact frame and the blur only up close.
    private static void screen(int kind, Vec3 pos, Vec3 dir, int rgb, float charge, float size) {
        if (kind == PunchFx.SWING) {
            return;
        }
        float s = SCALE[kind] * size;
        boolean big = kind == PunchFx.FINAL || kind == PunchFx.FORTE || kind == PunchFx.DASH;
        ScreenFx.warp(pos, 1.6f * s, (big ? 0.45f : 0.25f) * (kind == PunchFx.FORTE ? 1 + 0.5f * charge : 1), 10, 0, dir, rgb, 0.04f,
                big ? 14 : 10, -1, null, 0);
        LocalPlayer player = Minecraft.getInstance().player;
        double distance = player == null ? 0 : player.getEyePosition().distanceTo(pos);
        float near = (float) Math.max(0, 1 - distance / (SCREEN_REACH * size));
        if (near <= 0) {
            return;
        }
        if (kind == PunchFx.FORTE) {
            ScreenFx.impactFrame(new int[] {1, 2}, 1, rgb, pos, 0);
            ScreenFx.radialBlur(pos, 0.6f * near, 8);
        } else if (kind == PunchFx.DASH) {
            ScreenFx.radialBlur(pos, 0.4f * near, 6);
        }
    }

    static void render(Vec3 camera, double now, float partial) {
        HITS.removeIf(h -> now - h.born > LIFE);
        CHARGES.values().removeIf(f -> now - f.seen > FOLLOW_TICKS);
        TRAILS.values().removeIf(f -> now - f.seen > FOLLOW_TICKS);
        for (Hit h : HITS) {
            hit(h, h.pos.subtract(camera).toVector3f(), (float) (now - h.born), (float) (h.ground - camera.y));
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Follow f : CHARGES.values()) {
            Entity e = level.getEntity(f.entityId);
            if (e != null) {
                float sc = scale(e);
                // Where the server puts the fist (combat/Combat.handPos), at this frame.
                Vec3 hand = e.getEyePosition(partial).add(e.getViewVector(partial).scale(0.6 * sc)).add(0, -0.35 * sc, 0);
                charge(f, hand.subtract(camera).toVector3f(), (float) now, sc);
            }
        }
        for (Follow f : TRAILS.values()) {
            Entity e = level.getEntity(f.entityId);
            if (e != null) {
                Vec3 feet = e.getPosition(partial);
                trail(f, feet.subtract(camera).toVector3f(), e.getBbHeight(), (float) now, scale(e));
            }
        }
    }

    private static float scale(Entity e) {
        return e instanceof net.minecraft.world.entity.LivingEntity living ? Math.max(1, living.getScale()) : 1;
    }

    // One hit, age ticks after it landed.
    private static void hit(Hit h, Vector3f at, float age, float groundY) {
        int k = h.kind;
        float s = SCALE[k] * h.size;
        boolean big = k == PunchFx.FINAL || k == PunchFx.FORTE || k == PunchFx.DASH;
        boolean swing = k == PunchFx.SWING;
        if (swing) {
            s *= Math.max(0.2f, h.charge);
        }
        float life = big ? 14 : 9;
        Vector3f dir = h.dir;
        Vector3f u = perpendicular(dir);
        Vector3f v = new Vector3f(dir).cross(u).normalize();
        Vector3f toCamera = new Vector3f(at).negate();
        // The flash of the contact, in the soul color with a white core.
        float flashLife = big ? 6 : 4;
        if (!swing && age < flashLife) {
            float kk = age / flashLife;
            FxDraw.billboard(at, (big ? 1.8f : 1.1f) * s * (1 - kk * 0.4f), 0.2f + h.seed, FxDraw.FLARE, 4 / 32f, h.color, 1 - smooth(kk), true);
            FxDraw.billboard(at, 0.9f * s, 0, FxDraw.SOFT, 0.4f, h.color, 0.6f * (1 - kk), false);
            FxDraw.billboard(at, 0.4f * s, 0, FxDraw.SOFT, 0.95f, WHITE, 0.9f * (1 - kk), true);
        }
        // The vapor rings going through, one after the other.
        int rings = k == PunchFx.FORTE ? 3 : big ? 2 : 1;
        for (int j = 0; j < rings; j++) {
            float a = age - j * 2;
            if (a < 0 || a >= life) {
                continue;
            }
            float kk = a / life;
            Vector3f c = new Vector3f(dir).mul((0.2f + a * 0.22f + j * 0.3f) * s).add(at);
            vapor(c, dir, (0.5f + 1.6f * easeOut(kk)) * s * (1 - j * 0.15f), 1 - smooth(kk), h.color, h.light);
        }
        if (big && age < 9) {
            FxDraw.disk(at, toCamera, (0.4f + 2.2f * easeOut(age / 9)) * s, FxDraw.RING, 0.08f, h.color, 0.8f * (1 - age / 9), true);
        }
        // Wind out of the target's back.
        int lines = switch (k) {
            case PunchFx.JAB -> 6;
            case PunchFx.FINAL -> 10;
            case PunchFx.FORTE -> 16;
            case PunchFx.BARRAGEM -> 3;
            case PunchFx.DASH -> 12;
            default -> 6;
        };
        if (age < life) {
            float op = 0.75f * (1 - smooth(age / life));
            for (int i = 0; i < lines; i++) {
                float ang = FxWorld.hash(h.seed, i, 1) * Mth.TWO_PI;
                float r = (0.2f + 0.45f * FxWorld.hash(h.seed, i, 2)) * s;
                float speed = (0.35f + 0.3f * FxWorld.hash(h.seed, i, 3)) * s;
                Vector3f head = new Vector3f(u).mul(Mth.cos(ang) * r).add(new Vector3f(v).mul(Mth.sin(ang) * r)).add(at)
                        .add(new Vector3f(dir).mul(0.3f + age * speed));
                Vector3f tail = new Vector3f(dir).mul(-(0.7f + 0.6f * FxWorld.hash(h.seed, i, 4)) * s).add(head);
                FxDraw.ribbon(tail, head, 0.03f * s + 0.01f, FxDraw.STREAK, 0.6f, i % 2 == 1 ? h.light : AIR, op, false);
                if (i % 2 == 1) {
                    FxDraw.ribbon(new Vector3f(dir).mul(-0.5f * s).add(head), head, 0.05f * s, FxDraw.STREAK, 0.9f, h.color, op * 0.6f, true);
                }
            }
        }
        // A few soul flames thrown out of the contact, rising.
        int flames = switch (k) {
            case PunchFx.JAB -> 2;
            case PunchFx.FINAL -> 5;
            case PunchFx.FORTE -> 8;
            case PunchFx.BARRAGEM -> 1;
            case PunchFx.DASH -> 6;
            default -> 0;
        };
        float flameLife = big ? 20 : 12;
        if (age < flameLife) {
            for (int i = 0; i < flames; i++) {
                Vector3f d = FxWorld.sphere(h.seed, i).add(new Vector3f(dir).mul(0.7f)).normalize();
                float e = easeOut(age / 8);
                Vector3f p = d.mul((0.25f + 1.1f * e * (0.6f + 0.6f * FxWorld.hash(h.seed, i, 3))) * s).add(at).add(0, age * 0.03f * s, 0);
                wisp(p, (0.4f * (1 - age / flameLife) + 0.08f) * s, h.color, h.light, 0.85f * (1 - smooth(age / flameLife)), i, age);
            }
        }
        if (k == PunchFx.FORTE || k == PunchFx.DASH) {
            dust(at, groundY, age - 1, (k == PunchFx.FORTE ? 1.3f : 1) * h.size, h.seed);
        }
    }

    // The Forte charging: tinted rings closing on the fist, a glow and flames on it.
    private static void charge(Follow f, Vector3f fist, float now, float sc) {
        float k = Mth.clamp(f.a, 0, 1);
        for (int i = 0; i < 3; i++) {
            float ph = frac(now * 0.08f + i / 3f);
            vapor(new Vector3f(f.dir).mul(0.1f * sc).add(fist), f.dir, ((1 - ph) * 1.4f + 0.15f) * sc, (0.25f + 0.75f * k) * ph * 0.8f, f.color, f.light);
        }
        FxDraw.billboard(fist, (0.5f + 0.7f * k) * sc, 0, FxDraw.SOFT, 0.5f, f.color, 0.2f + 0.3f * k, true);
        for (int i = 0; i < 4; i++) {
            float ph = frac(now * 0.07f + i / 4f);
            Vector3f p = new Vector3f((FxWorld.hash(i, 1, 3) - 0.5f) * 0.3f, ph * 0.6f, (FxWorld.hash(i, 2, 3) - 0.5f) * 0.3f).mul(sc).add(fist);
            wisp(p, 0.3f * (1 - ph) * (0.5f + k) * sc, f.color, f.light, (0.3f + 0.7f * k) * (1 - ph), i, now);
        }
    }

    // The Dash Attack sliding.
    private static void trail(Follow f, Vector3f feet, float height, float now, float sc) {
        Vector3f dir = f.dir;
        Vector3f center = new Vector3f(feet).add(0, height * 0.55f, 0);
        Vector3f cone = new Vector3f(dir).mul(0.9f * sc).add(center);
        for (int j = 0; j < 2; j++) {
            vapor(new Vector3f(dir).mul(-j * 0.5f * sc).add(cone), dir, (1.5f - j * 0.3f) * sc, 0.6f - j * 0.2f, f.color, f.light);
        }
        Vector3f side = new Vector3f(-dir.z, 0, dir.x);
        if (side.lengthSquared() < 1.0E-4f) {
            side.set(1, 0, 0);
        }
        side.normalize();
        int tick = Mth.floor(now);
        for (int i = 0; i < 3; i++) {
            float a = frac(now * 0.5f + i);
            Vector3f p = new Vector3f(side).mul((FxWorld.hash(i, tick, 1) - 0.5f) * 0.8f * sc).add(feet).add(0, (0.25f + a * 0.3f) * sc, 0)
                    .add(new Vector3f(dir).mul(-a * 1.4f * sc));
            FxDraw.billboard(p, (0.5f + a * 0.6f) * sc, i + now * 0.1f, FxDraw.SMOKE, 0.3f + a * 0.3f, DUST, 0.5f * (1 - a), false);
        }
        for (int i = 0; i < 6; i++) {
            float a = frac(now * 0.25f + i / 6f);
            Vector3f p = new Vector3f(side).mul((FxWorld.hash(i, 4, 1) - 0.5f) * 0.6f * sc).add(center)
                    .add(0, (FxWorld.hash(i, 5, 1) - 0.5f) * height * 0.75f + a * 0.3f * sc, 0).add(new Vector3f(dir).mul(-a * 2.2f * sc));
            wisp(p, (0.4f * (1 - a) + 0.08f) * sc, f.color, f.light, 0.7f * (1 - a), i, now);
        }
    }

    // One vapor ring around normal: white-ish vapor tinted with the soul, its edge lit in the soul color.
    private static void vapor(Vector3f c, Vector3f normal, float size, float op, float[] color, float[] light) {
        FxDraw.disk(c, normal, size, FxDraw.RING, 0.18f, FxDraw.mix(AIR, light, 0.6f), op * 0.6f, false);
        FxDraw.disk(c, normal, size * 0.97f, FxDraw.RING, 0.07f, color, op * 0.8f, true);
    }

    // A soul flame: a solid layer (reads in daylight) under a glowing one.
    private static void wisp(Vector3f p, float size, float[] color, float[] light, float op, int i, float t) {
        FxDraw.billboard(p, size * 1.15f, i + t * 0.05f, FxDraw.FIRE, 0.35f, color, op * 0.55f, false);
        FxDraw.billboard(p, size, i + t * 0.05f, FxDraw.FIRE, 0.3f, i % 3 != 0 ? color : light, op, true);
    }

    // Dust kicked up in a ring on the ground under the hit.
    private static void dust(Vector3f at, float groundY, float age, float s, int seed) {
        if (age < 0 || age > 30) {
            return;
        }
        float e = easeOut(age / 14);
        for (int i = 0; i < 14; i++) {
            float a = i / 14f * Mth.TWO_PI + FxWorld.hash(seed, i, 1) * 0.4f;
            float r = (0.3f + 2.2f * e * (0.7f + 0.5f * FxWorld.hash(seed, i, 2))) * s;
            Vector3f p = new Vector3f(at.x + Mth.cos(a) * r, groundY + 0.25f + 0.3f * e * FxWorld.hash(seed, i, 3), at.z + Mth.sin(a) * r);
            FxDraw.billboard(p, (0.5f + 0.9f * e) * s, i, FxDraw.SMOKE, 0.25f + 0.4f * age / 30, i % 2 == 1 ? DUST : DUST_DARK,
                    0.65f * (1 - age / 30), false);
        }
    }

    private static Vector3f perpendicular(Vector3f d) {
        Vector3f helper = Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        return new Vector3f(d).cross(helper).normalize();
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
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
