package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.at;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.bb;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.dsk;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.easeOut;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rib;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.smooth;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.sphere;
import static com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.hash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.animal.dbmob.DmNet;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// The Dragon Ball creatures' own effects (animal/dbmob/DmNet, SubNet kinds 400-419), drawn as the preview draws them
// (tools/mobs/dbmobs): the Saibaman's acid ball and its bubbling puddle, the pirate's bomb with its fuse, the Red Ribbon
// rocket and its smoke, Babidi's magic circle and the Majin M, Yamu's needle drinking the light, the dragon's fire, Tao
// Pai Pai's pillar, Yakon eating the light and his darkness, and the sand thrown in the eyes. Every grain, drop and
// spark has its own path from the hash (nothing copied). Drawn in DbFx's two passes (solid things in the plain one,
// light in the glow one). Age in ticks.
final class DmFx {
    private static final float[] ACID = FxDraw.rgb(0x6BE62E);
    private static final float[] ACID_PALE = FxDraw.rgb(0xC8FF8A);
    private static final float[] ACID_DEEP = FxDraw.rgb(0x1A5208);
    private static final float[] IRON = FxDraw.rgb(0x2A2A30);
    private static final float[] FUSE = FxDraw.rgb(0xFFB040);
    private static final float[] SPARK = FxDraw.rgb(0xFFF0B0);
    private static final float[] ROCKET_RED = FxDraw.rgb(0xB82020);
    private static final float[] ROCKET_GREY = FxDraw.rgb(0x8A8C90);
    private static final float[] FLAME = FxDraw.rgb(0xFF8A20);
    private static final float[] FLAME_CORE = FxDraw.rgb(0xFFE6A0);
    private static final float[] SMOKE = FxDraw.rgb(0x9A9CA0);
    private static final float[] SMOKE_DARK = FxDraw.rgb(0x3A3836);
    private static final float[] MAGIC = FxDraw.rgb(0xC040FF);
    private static final float[] MAGIC_PALE = FxDraw.rgb(0xF0C8FF);
    private static final float[] MAJIN_RED = FxDraw.rgb(0xE01828);
    private static final float[] DRAIN_LIGHT = FxDraw.rgb(0xFFE89A);
    private static final float[] GLASS = FxDraw.rgb(0xBFE8FF);
    private static final float[] FIRE_DEEP = FxDraw.rgb(0xD83A10);
    private static final float[] STONE = FxDraw.rgb(0xB8AE9C);
    private static final float[] STONE_DARK = FxDraw.rgb(0x7E7466);
    private static final float[] LIGHT = FxDraw.rgb(0xFFF6D0);
    private static final float[] DARK = {0.015f, 0.01f, 0.03f};
    private static final float[] DARK_EDGE = FxDraw.rgb(0x2A1240);
    private static final float[] SAND = FxDraw.rgb(0xD8C08A);
    private static final float[] SAND_DARK = FxDraw.rgb(0xA8905C);
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    private static final class Fx {
        final int kind;
        final int id;
        Vec3 pos;
        Vec3 dir;
        float a;
        float b;
        float c;
        final double born;
        double updated;
        double endAt = -1;
        boolean burst;

        Fx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, double now) {
            this.kind = kind;
            this.id = id;
            this.pos = pos;
            this.dir = dir;
            this.a = a;
            this.b = b;
            this.c = c;
            this.born = now;
            this.updated = now;
        }
    }

    private static final List<Fx> SHOTS = new ArrayList<>();
    private static final Map<Long, Fx> TRACKED = new HashMap<>();

    private DmFx() {
    }

    static boolean handles(int kind) {
        return kind >= DmNet.FIRST && kind <= DmNet.LAST;
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    private static boolean tracked(int kind) {
        return kind == DmNet.CIRCLE || kind == DmNet.MAJIN || kind == DmNet.DRAIN || kind == DmNet.PILLAR;
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (!tracked(kind)) {
            SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
            return;
        }
        Fx f = TRACKED.get(key(kind, id));
        boolean end = c >= 1 || kind == DmNet.MAJIN && a <= 0;
        if (end) {
            if (f != null && f.endAt < 0) {
                f.endAt = now;
                f.burst = kind == DmNet.CIRCLE;
            }
            return;
        }
        if (f == null || f.endAt >= 0) {
            f = new Fx(kind, id, pos, dir, a, b, c, now);
            TRACKED.put(key(kind, id), f);
        }
        f.pos = pos;
        f.dir = dir;
        f.a = a;
        f.b = b;
        f.updated = now;
    }

    static boolean active() {
        return !SHOTS.isEmpty() || !TRACKED.isEmpty();
    }

    static void clear() {
        SHOTS.clear();
        TRACKED.clear();
    }

    private static float life(Fx f) {
        return switch (f.kind) {
            case DmNet.ACID -> f.c + f.b + 12;
            case DmNet.BOMB -> f.a + 2;
            case DmNet.ROCKET -> f.a + 24;
            case DmNet.BREATH -> f.a + 14;
            case DmNet.SUCK -> f.b + 6;
            case DmNet.SAND -> f.b >= 1 ? 30 : 22;
            case DmNet.DARK -> f.b + 20;
            default -> 20;
        };
    }

    // how long a tracked effect stands without news before it fades, and how long the fade takes
    private static float hold(Fx f) {
        return switch (f.kind) {
            case DmNet.CIRCLE -> f.b + 10;
            case DmNet.MAJIN -> f.a;
            case DmNet.DRAIN -> f.b >= 1 ? 4 : 8;
            default -> 6;
        };
    }

    private static float fade(Fx f) {
        return switch (f.kind) {
            case DmNet.CIRCLE -> f.burst ? 14 : 8;
            case DmNet.PILLAR -> 26;
            case DmNet.MAJIN -> 10;
            default -> 6;
        };
    }

    private static void prune(double now) {
        SHOTS.removeIf(f -> now - f.born > life(f));
        Iterator<Fx> it = TRACKED.values().iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            if (f.endAt < 0 && now - f.updated > hold(f)) {
                f.endAt = f.updated + hold(f);
            }
            if (f.endAt >= 0 && now - f.endAt > fade(f)) {
                it.remove();
            }
        }
    }

    static void draw(double now, float partial) {
        if (DbFx.pass) {
            prune(now);
        }
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born);
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case DmNet.ACID -> acid(f, age);
                case DmNet.BOMB -> bomb(f, age);
                case DmNet.ROCKET -> rocket(f, age);
                case DmNet.BREATH -> breath(f, age, partial);
                case DmNet.SUCK -> suck(f, age);
                case DmNet.SAND -> sand(f, age);
                case DmNet.DARK -> dark(f, age);
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born);
            float gone = f.endAt < 0 ? 0 : (float) (now - f.endAt);
            float out = f.endAt < 0 ? 1 : 1 - smooth(gone / fade(f));
            switch (f.kind) {
                case DmNet.CIRCLE -> circle(f, age, gone, out);
                case DmNet.MAJIN -> majin(f, age, partial, out);
                case DmNet.DRAIN -> drain(f, age, partial, out);
                case DmNet.PILLAR -> pillar(f, age, gone, out);
                default -> {
                }
            }
        }
    }

    // ---------- helpers ----------

    private static Vector3f add(Vector3f p, float x, float y, float z) {
        return new Vector3f(p).add(x, y, z);
    }

    private static Vector3f vec(Vec3 d) {
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }

    // three unit vectors: d and two square to it
    private static Vector3f[] frame(Vector3f d) {
        Vector3f n = new Vector3f(d);
        if (n.lengthSquared() < 1.0E-6f) {
            n.set(0, 0, 1);
        }
        n.normalize();
        Vector3f u = new Vector3f(n).cross(Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : UP).normalize();
        Vector3f w = new Vector3f(n).cross(u).normalize();
        return new Vector3f[] {n, u, w};
    }

    // a throw from p along d: k of the way, arcing up by lift at the middle
    private static Vector3f arc(Vector3f p, Vector3f d, float k, float lift) {
        Vector3f q = at(p, d, k);
        q.y += 4 * k * (1 - k) * lift;
        return q;
    }

    // ---------- the Saibaman's acid ----------

    // A trembling green ball arcing from the mouth (c ticks), drops behind it, then a puddle of radius a that spreads,
    // bubbles (each bubble its own time) and steams for b ticks before it sinks into the ground.
    private static void acid(Fx f, float age) {
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float flight = Math.max(1, f.c);
        float lift = Math.min(1.6f, d.length() * 0.18f);
        if (age < flight) {
            float k = age / flight;
            Vector3f p = arc(from, d, k, lift);
            float wob = 0.22f + 0.03f * Mth.sin(age * 2.3f);
            bb(p, wob * 2.2f, age * 0.6f, FxDraw.SOFT, 0.85f, ACID, 0.95f, false);
            bb(p, wob * 1.1f, -age, FxDraw.SOFT, 0.9f, ACID_PALE, 0.8f, false);
            bb(p, wob * 4.2f, 0, FxDraw.SOFT, 0.3f, ACID, 0.5f, true);
            for (int i = 0; i < 6; i++) {
                float lag = 0.04f + i * 0.035f;
                if (k < lag) {
                    continue;
                }
                Vector3f q = arc(from, d, k - lag, lift);
                q.y -= 0.02f * i * i;
                bb(q, 0.12f - i * 0.013f, 0, FxDraw.SOFT, 0.85f, i % 2 == 0 ? ACID : ACID_PALE, 0.9f, false);
            }
            return;
        }
        float t = age - flight;
        Vector3f c = at(from, d, 1);
        float R = f.a;
        float grow = easeOut(t / 6);
        float out = 1 - smooth((t - f.b) / 12);
        if (out <= 0) {
            return;
        }
        // the splat: drops thrown out that fall back into the puddle
        if (t < 12) {
            for (int i = 0; i < 14; i++) {
                Vector3f s = sphere(f.id + 31, i);
                s.y = Math.abs(s.y) + 0.4f;
                float sp = 0.07f + 0.05f * hash(f.id, i, 4);
                Vector3f q = add(c, s.x * sp * t * R, s.y * sp * t * 1.4f - 0.02f * t * t, s.z * sp * t * R);
                if (q.y > c.y) {
                    bb(q, 0.1f + 0.06f * hash(f.id, i, 5), 0, FxDraw.SOFT, 0.85f, i % 3 == 0 ? ACID_PALE : ACID, 1 - t / 12, false);
                }
            }
        }
        // the puddle: a dark rim, a lit middle, the edge wavy from the hash
        Vector3f floor = add(c, 0, 0.04f, 0);
        dsk(floor, UP, R * 2.1f * grow, FxDraw.SOFT, 0.75f, ACID_DEEP, 0.85f * out, false);
        dsk(add(floor, 0, 0.005f, 0), UP, R * 1.6f * grow, FxDraw.SOFT, 0.7f, ACID, 0.75f * out, false);
        for (int i = 0; i < 7; i++) {
            float ang = hash(f.id, i, 7) * Mth.TWO_PI;
            float rr = R * (0.55f + 0.3f * hash(f.id, i, 8)) * grow;
            dsk(add(floor, Mth.cos(ang) * rr, 0.003f, Mth.sin(ang) * rr), UP, R * (0.6f + 0.4f * hash(f.id, i, 9)) * grow, FxDraw.SOFT, 0.7f,
                    ACID_DEEP, 0.6f * out, false);
        }
        dsk(add(floor, 0, 0.01f, 0), UP, R * 1.2f * grow, FxDraw.SOFT, 0.5f, ACID, 0.35f * out, true);
        // bubbles: each one swells and pops on its own clock
        for (int i = 0; i < 16; i++) {
            float speed = 0.04f + 0.05f * hash(f.id, i, 11);
            float ph = (t * speed + hash(f.id, i, 12)) % 1f;
            float ang = hash(f.id, i, 13) * Mth.TWO_PI;
            float rr = R * 0.8f * (float) Math.sqrt(hash(f.id, i, 14)) * grow;
            Vector3f q = add(floor, Mth.cos(ang) * rr, 0.03f + ph * 0.08f, Mth.sin(ang) * rr);
            float size = (0.06f + 0.16f * ph) * (0.6f + 0.6f * hash(f.id, i, 15));
            if (ph < 0.9f) {
                bb(q, size, 0, FxDraw.RING, 0.35f, ACID_PALE, 0.8f * out, false);
            } else {
                bb(add(q, 0, 0.06f, 0), size * 1.6f, i, FxDraw.SOFT, 0.6f, ACID_PALE, 0.6f * out * (1 - (ph - 0.9f) * 10), true);
            }
        }
        // the hiss: pale green steam rising, each wisp on its own path
        for (int i = 0; i < 8; i++) {
            float ph = (t * 0.03f + hash(f.id, i, 21)) % 1f;
            float ang = hash(f.id, i, 22) * Mth.TWO_PI;
            float rr = R * 0.7f * hash(f.id, i, 23) * grow;
            Vector3f q = add(floor, Mth.cos(ang) * rr + ph * 0.2f, 0.2f + ph * 1.4f, Mth.sin(ang) * rr);
            bb(q, 0.5f + ph * 0.9f, i + t * 0.02f, FxDraw.SMOKE, 0.3f + ph * 0.3f, ACID_PALE, 0.28f * out * (1 - ph), false);
        }
    }

    // ---------- the pirate's bomb ----------

    // A black iron ball arcing from the hand (c ticks), bouncing once, the fuse spitting sparks until a ticks.
    private static void bomb(Fx f, float age) {
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float flight = Math.max(1, f.c);
        Vector3f p;
        if (age < flight) {
            p = arc(from, d, age / flight, Math.min(2.2f, d.length() * 0.3f));
        } else {
            // one small bounce, then it rolls a little and lies there
            float t = age - flight;
            Vector3f land = at(from, d, 1);
            Vector3f h = new Vector3f(d.x, 0, d.z);
            if (h.lengthSquared() > 1.0E-4f) {
                h.normalize();
            }
            float roll = 0.5f * easeOut(t / 10);
            p = at(land, h, roll);
            p.y += t < 6 ? 0.35f * 4 * (t / 6) * (1 - t / 6) : 0;
        }
        p.y += 0.2f;
        bb(p, 0.42f, 0, FxDraw.SOFT, 0.95f, IRON, 1, false);
        bb(add(p, -0.05f, 0.06f, 0), 0.14f, 0, FxDraw.SOFT, 0.8f, ROCKET_GREY, 0.6f, false);
        // the fuse and its sparks; it burns shorter, and blinks faster near the end
        float burnt = Mth.clamp(age / Math.max(1, f.a), 0, 1);
        Vector3f tip = add(p, 0.06f, 0.24f - 0.08f * burnt, 0.03f);
        rib(add(p, 0, 0.18f, 0), tip, 0.04f, FxDraw.STREAK, 0.9f, SMOKE_DARK, 1, false);
        float blink = 0.6f + 0.4f * Mth.sin(age * (0.6f + burnt * 2.2f));
        bb(tip, 0.3f * blink, age, FxDraw.FLARE, 6 / 32f, FUSE, 0.9f, true);
        for (int i = 0; i < 8; i++) {
            float ph = (age * (0.2f + 0.15f * hash(f.id, i, 1)) + hash(f.id, i, 2)) % 1f;
            Vector3f s = sphere(f.id + i * 7 + (int) age / 3, i);
            s.y = Math.abs(s.y) + 0.3f;
            Vector3f q = add(tip, s.x * ph * 0.5f, s.y * ph * 0.45f - ph * ph * 0.3f, s.z * ph * 0.5f);
            rib(add(q, -s.x * 0.06f, -s.y * 0.06f, -s.z * 0.06f), q, 0.025f, FxDraw.STREAK, 0.9f, i % 2 == 0 ? SPARK : FUSE, 1 - ph, true);
        }
        if (DbFx.pass) {
            return;
        }
        float ph = (age * 0.05f) % 1f;
        bb(add(tip, 0.1f * ph, 0.2f + ph * 0.9f, 0), 0.2f + ph * 0.5f, age * 0.03f, FxDraw.SMOKE, 0.4f, SMOKE, 0.35f * (1 - ph), false);
    }

    // ---------- the Red Ribbon rocket ----------

    // A red-nosed rocket flying from pos to pos + dir in a ticks, a flame at its tail and a smoke trail that stays and
    // spreads after it (each puff born where the rocket was at its own tick).
    private static void rocket(Fx f, float age) {
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float flight = Math.max(1, f.a);
        float k = Math.min(1, age / flight);
        Vector3f[] fr = frame(d);
        if (age < flight) {
            // a slight wobble of the steering
            Vector3f p = at(at(at(from, d, k), fr[1], 0.08f * Mth.sin(age * 1.7f)), fr[2], 0.06f * Mth.cos(age * 2.1f));
            Vector3f nose = at(p, fr[0], 0.45f);
            Vector3f tail = at(p, fr[0], -0.45f);
            if (!DbFx.pass) {
                DbFx.box(tail, at(p, fr[0], 0.25f), 0.2f, ROCKET_GREY, 1);
                DbFx.box(at(p, fr[0], 0.25f), nose, 0.16f, ROCKET_RED, 1);
                for (int i = 0; i < 4; i++) {
                    Vector3f fin = i % 2 == 0 ? fr[1] : fr[2];
                    float s = i < 2 ? 1 : -1;
                    DbFx.box(tail, at(at(tail, fin, 0.2f * s), fr[0], 0.15f), 0.05f, ROCKET_RED, 1);
                }
            }
            float fl = 0.8f + 0.2f * Mth.sin(age * 3.1f);
            bb(at(tail, fr[0], -0.15f), 0.5f * fl, age, FxDraw.FIRE, 0.3f, FLAME, 0.95f, true);
            bb(at(tail, fr[0], -0.08f), 0.25f, 0, FxDraw.SOFT, 0.9f, FLAME_CORE, 1, true);
            rib(at(tail, fr[0], -0.9f * fl), tail, 0.18f, FxDraw.STREAK, 0.8f, FLAME, 0.8f, true);
        }
        if (DbFx.pass) {
            return;
        }
        int puffs = (int) Math.min(age, flight);
        for (int i = 0; i < puffs; i++) {
            float bornAt = i;
            float t = age - bornAt;
            float out = 1 - t / 24;
            if (out <= 0) {
                continue;
            }
            Vector3f q = at(from, d, i / flight);
            q = add(q, (hash(f.id, i, 1) - 0.5f) * t * 0.04f, t * 0.02f, (hash(f.id, i, 2) - 0.5f) * t * 0.04f);
            bb(q, 0.35f + t * 0.06f, i + t * 0.02f, FxDraw.SMOKE, 0.3f + t * 0.015f, FxDraw.mix(SMOKE, SMOKE_DARK, hash(f.id, i, 3) * 0.5f),
                    0.55f * out, false);
        }
    }

    // ---------- Babidi ----------

    // The magic circle on the ground under the one he points at: two rings turning opposite ways, a star of six points,
    // runes round the edge lighting one by one as it fills (b ticks); bursting in a pillar of light when it goes off.
    private static void circle(Fx f, float age, float gone, float out) {
        Vector3f c = add(rel(f.pos), 0, 0.06f, 0);
        float R = f.a;
        float fill = Mth.clamp(age / Math.max(1, f.b), 0, 1);
        float in = easeOut(age / 6);
        float o = out * in;
        float spin = age * 0.05f;
        dsk(c, UP, R * 2.05f, FxDraw.RING, 0.04f, MAGIC, 0.9f * o, true);
        dsk(add(c, 0, 0.005f, 0), UP, R * 1.7f, FxDraw.RING, 0.03f, MAGIC_PALE, 0.7f * o, true);
        dsk(add(c, 0, 0.002f, 0), UP, R * 2.0f, FxDraw.SOFT, 0.4f, MAGIC, 0.25f * o * (0.6f + 0.4f * fill), true);
        // the six-pointed star: two triangles turning against each other
        for (int tri = 0; tri < 2; tri++) {
            float base = tri == 0 ? spin : -spin + Mth.PI / 3;
            for (int i = 0; i < 3; i++) {
                float a0 = base + i * Mth.TWO_PI / 3;
                float a1 = base + (i + 1) * Mth.TWO_PI / 3;
                Vector3f p0 = add(c, Mth.cos(a0) * R * 0.82f, 0.01f, Mth.sin(a0) * R * 0.82f);
                Vector3f p1 = add(c, Mth.cos(a1) * R * 0.82f, 0.01f, Mth.sin(a1) * R * 0.82f);
                flat(p0, p1, 0.05f, MAGIC_PALE, 0.85f * o);
            }
        }
        // runes on the band: each lights when the fill reaches it
        for (int i = 0; i < 12; i++) {
            float ang = -spin * 0.6f + i * Mth.TWO_PI / 12;
            Vector3f p = add(c, Mth.cos(ang) * R * 0.93f, 0.015f, Mth.sin(ang) * R * 0.93f);
            float lit = smooth((fill * 12 - i) * 1.5f);
            float s = 0.12f + 0.05f * hash(f.id, i, 3);
            Vector3f t = new Vector3f(-Mth.sin(ang), 0, Mth.cos(ang));
            // a rune: two or three short strokes from the hash
            int strokes = 2 + (int) (hash(f.id, i, 4) * 2);
            for (int j = 0; j < strokes; j++) {
                float ra = hash(i, j, 5) * Mth.PI;
                Vector3f dj = new Vector3f(t).mul(Mth.cos(ra)).add(Mth.cos(ang) * Mth.sin(ra), 0, Mth.sin(ang) * Mth.sin(ra));
                Vector3f off = new Vector3f(t).mul((j - strokes / 2f) * s * 0.5f);
                flat(at(add(p, off.x, 0, off.z), dj, -s), at(add(p, off.x, 0, off.z), dj, s), 0.03f, lit > 0.5f ? MAGIC_PALE : MAGIC,
                        (0.3f + 0.7f * lit) * o);
            }
        }
        // motes rising faster as it fills
        for (int i = 0; i < 10; i++) {
            float ph = (age * (0.03f + 0.04f * fill) + hash(f.id, i, 6)) % 1f;
            float ang = hash(f.id, i, 7) * Mth.TWO_PI;
            float rr = R * (0.3f + 0.65f * hash(f.id, i, 8));
            bb(add(c, Mth.cos(ang) * rr, ph * 1.8f, Mth.sin(ang) * rr), 0.08f, 0, FxDraw.SOFT, 0.8f, MAGIC_PALE, 0.8f * o * (1 - ph), true);
        }
        if (f.burst && gone < 14) {
            float k = gone / 14;
            float h = 5 * easeOut(gone / 4);
            rib(c, add(c, 0, h, 0), R * 1.6f * (1 - k), FxDraw.BEAM, 0.5f, MAGIC, 0.9f * (1 - k), true);
            rib(c, add(c, 0, h * 0.9f, 0), R * 0.7f * (1 - k), FxDraw.BEAM, 0.5f, MAGIC_PALE, 1 - k, true);
            dsk(add(c, 0, 0.1f, 0), UP, R * (2 + 2.5f * easeOut(k * 2)), FxDraw.RING, 0.1f, MAGIC_PALE, 1 - k, true);
        }
    }

    // a stroke lying on the ground from a to b
    private static void flat(Vector3f a, Vector3f b, float w, float[] col, float op) {
        Vector3f d = new Vector3f(b).sub(a);
        if (d.lengthSquared() < 1.0E-6f) {
            return;
        }
        Vector3f side = new Vector3f(-d.z, 0, d.x).normalize(w / 2);
        if (DbFx.pass && op > 0.002f) {
            FxDraw.quad(new Vector3f(a).add(side), new Vector3f(b).add(side), new Vector3f(b).sub(side), new Vector3f(a).sub(side), FxDraw.SOFT,
                    0.9f, col, op, true);
        }
    }

    // The Majin M on the forehead of the one under Babidi's control: drawn as five strokes facing out of the face,
    // a red glow behind it.
    private static void majin(Fx f, float age, float partial, float out) {
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(f.id);
        if (e == null || e == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        float yaw = Mth.lerp(partial, e.yRotO, e.getYRot()) * Mth.DEG_TO_RAD;
        if (e instanceof net.minecraft.world.entity.LivingEntity le) {
            yaw = Mth.lerp(partial, le.yHeadRotO, le.yHeadRot) * Mth.DEG_TO_RAD;
        }
        Vector3f fwd = new Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vector3f right = new Vector3f(-fwd.z, 0, fwd.x);
        float h = e.getBbHeight();
        float s = Math.max(0.6f, e.getBbWidth() * 0.9f);
        Vector3f c = at(rel(e.getPosition(partial).add(0, h * 0.93f, 0)), fwd, e.getBbWidth() * 0.52f);
        float in = easeOut(age / 5) * out;
        float pulse = 0.85f + 0.15f * Mth.sin(age * 0.3f);
        float w = 0.11f * s, ht = 0.13f * s;
        // the M: left leg, left diagonal, right diagonal, right leg
        Vector3f l0 = at(add(c, 0, -ht, 0), right, -w);
        Vector3f l1 = at(add(c, 0, ht, 0), right, -w);
        Vector3f mid = add(c, 0, -ht * 0.1f, 0);
        Vector3f r1 = at(add(c, 0, ht, 0), right, w);
        Vector3f r0 = at(add(c, 0, -ht, 0), right, w);
        float lw = 0.035f * s;
        rib(l0, l1, lw, FxDraw.STREAK, 0.95f, MAJIN_RED, in * pulse, true);
        rib(l1, mid, lw, FxDraw.STREAK, 0.95f, MAJIN_RED, in * pulse, true);
        rib(mid, r1, lw, FxDraw.STREAK, 0.95f, MAJIN_RED, in * pulse, true);
        rib(r1, r0, lw, FxDraw.STREAK, 0.95f, MAJIN_RED, in * pulse, true);
        bb(c, 0.5f * s, 0, FxDraw.SOFT, 0.4f, MAJIN_RED, 0.35f * in * pulse, true);
        // a dark purple haze around the body, the sign of the spell
        for (int i = 0; i < 4; i++) {
            float ph = (age * 0.04f + hash(f.id, i, 1)) % 1f;
            float ang = hash(f.id, i, 2) * Mth.TWO_PI + age * 0.02f;
            Vector3f q = rel(e.getPosition(partial).add(Mth.cos(ang) * e.getBbWidth() * 0.6, h * (0.2 + ph * 0.9), Mth.sin(ang) * e.getBbWidth() * 0.6));
            bb(q, 0.25f, 0, FxDraw.SOFT, 0.6f, MAGIC, 0.4f * in * (1 - ph), true);
        }
    }

    // ---------- Yamu's needle ----------

    // A stream of golden light from the one drained (pos) into the flask (pos + dir): beads running along a line that
    // sags a little, the flask glowing brighter as a grows. b = 1 is Babidi's spell instead: one quick violet bolt.
    private static void drain(Fx f, float age, float partial, float out) {
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        Vector3f to = at(from, d, 1);
        if (f.b >= 1) {
            float k = Math.min(1, age / 3);
            Vector3f[] fr = frame(d);
            Vector3f last = from;
            for (int i = 1; i <= 8; i++) {
                float u = i / 8f * k;
                Vector3f q = at(at(at(from, d, u), fr[1], (hash(f.id, i, (int) age) - 0.5f) * 0.3f * (1 - u)), fr[2], (hash(f.id, i, 9) - 0.5f) * 0.3f);
                rib(last, q, 0.08f, FxDraw.STREAK, 0.9f, i % 2 == 0 ? MAGIC_PALE : MAGIC, out, true);
                last = q;
            }
            bb(to, 0.8f * k, age, FxDraw.FLARE, 8 / 32f, MAGIC, out * k, true);
            return;
        }
        float full = Mth.clamp(f.a, 0, 1);
        Vector3f last = from;
        int segs = 12;
        for (int i = 1; i <= segs; i++) {
            float u = i / (float) segs;
            Vector3f q = at(from, d, u);
            q.y -= 4 * u * (1 - u) * 0.25f;
            rib(last, q, 0.05f, FxDraw.STREAK, 0.9f, DRAIN_LIGHT, 0.55f * out, true);
            last = q;
        }
        for (int i = 0; i < 9; i++) {
            float u = (age * 0.07f + i / 9f) % 1f;
            Vector3f q = at(from, d, u);
            q.y -= 4 * u * (1 - u) * 0.25f;
            bb(q, 0.12f + 0.04f * Mth.sin(age + i), 0, FxDraw.SOFT, 0.85f, i % 3 == 0 ? LIGHT : DRAIN_LIGHT, 0.9f * out, true);
        }
        // the light leaving the body and the flask filling
        bb(from, 0.6f + 0.1f * Mth.sin(age * 0.5f), age * 0.1f, FxDraw.SOFT, 0.4f, DRAIN_LIGHT, 0.4f * out, true);
        bb(to, 0.35f + 0.25f * full, 0, FxDraw.SOFT, 0.7f, GLASS, 0.5f * out, true);
        bb(to, 0.25f + 0.35f * full, age * 0.2f, FxDraw.FLARE, 6 / 32f, DRAIN_LIGHT, (0.3f + 0.7f * full) * out, true);
    }

    // ---------- the dragon's fire ----------

    // A jet of fire from the mouth (pos) along dir over a ticks: tongues of flame each born at the mouth on its own tick
    // and flying out, swelling, rising and darkening into smoke; a white-hot core near the mouth.
    private static void breath(Fx f, float age, float partial) {
        Vector3f mouth = rel(f.pos);
        Vector3f d = vec(f.dir);
        // the jet stays on the mouth of a dragon that moves (above all a ridden one, flying fast), along where its rider
        // looks: test 2026-11-13, the ridden fire was left behind at the tail
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(f.id);
        if (e != null && age < f.a + 2) {
            float yaw = Mth.lerp(partial, e.yRotO, e.getYRot()) * Mth.DEG_TO_RAD;
            Vec3 at = e.getPosition(partial).add(-Mth.sin(yaw) * 3.6, 3.2, Mth.cos(yaw) * 3.6);
            mouth = rel(at);
            Entity rider = e.getControllingPassenger();
            if (rider != null) {
                Vec3 look = rider.getViewVector(partial).scale(d.length());
                d = vec(look);
            }
        }
        float len = Math.max(1, d.length());
        Vector3f[] fr = frame(d);
        float dur = Math.max(1, f.a);
        float life = 12;
        int first = (int) Math.max(0, Math.floor(age - life));
        int last = (int) Math.min(dur, Math.floor(age));
        for (int b = first; b <= last; b++) {
            for (int j = 0; j < 3; j++) {
                float t = age - b - j / 3f;
                if (t < 0 || t > life) {
                    continue;
                }
                float k = t / life;
                int s = b * 3 + j;
                float sx = (hash(f.id, s, 1) - 0.5f) * 2;
                float sy = (hash(f.id, s, 2) - 0.5f) * 2;
                float dist = len * easeOut(k) * (0.75f + 0.35f * hash(f.id, s, 3));
                Vector3f q = at(at(at(mouth, fr[0], dist), fr[1], sx * dist * 0.18f), fr[2], sy * dist * 0.18f);
                q.y += k * k * 1.2f;
                float size = 0.35f + dist * 0.32f;
                float[] col = k < 0.35f ? FxDraw.mix(FLAME_CORE, FLAME, k / 0.35f) : FxDraw.mix(FLAME, FIRE_DEEP, (k - 0.35f) / 0.65f);
                bb(q, size, s + t * 0.1f, FxDraw.FIRE, Math.min(0.95f, k), col, 0.9f * (1 - k * k), true);
                if (k > 0.55f) {
                    bb(add(q, 0, 0.3f, 0), size * 1.2f, s, FxDraw.SMOKE, 0.4f, SMOKE_DARK, 0.5f * (k - 0.55f) * 2 * (1 - k), false);
                }
            }
        }
        if (age < dur) {
            bb(at(mouth, fr[0], 0.3f), 0.7f + 0.1f * Mth.sin(age * 2.7f), age, FxDraw.SOFT, 0.7f, FLAME_CORE, 0.9f, true);
            rib(mouth, at(mouth, fr[0], Math.min(len, 2.5f)), 0.35f, FxDraw.STREAK, 0.8f, FLAME_CORE, 0.7f, true);
        }
    }

    // ---------- Tao Pai Pai's pillar ----------

    // A stone column (a square trunk with lighter edges and a few cracks) from pos along dir; crumbling into falling
    // blocks when it ends.
    private static void pillar(Fx f, float age, float gone, float out) {
        Vector3f foot = rel(f.pos);
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (len < 0.05f) {
            return;
        }
        if (DbFx.pass) {
            if (age < 12 && Math.abs(d.y) > len * 0.7f) {
                DbFx.nrDust(foot, age, f.id, 7, 0.9f, STONE, 14);
            }
            return;
        }
        Vector3f[] fr = frame(d);
        if (f.endAt < 0) {
            DbFx.box(foot, at(foot, d, 1), 0.75f, STONE, 1);
            for (int i = 0; i < 4; i++) {
                float u = 0.15f + 0.22f * i;
                Vector3f p = at(foot, d, u);
                DbFx.box(at(p, fr[0], -0.02f), at(p, fr[0], 0.06f), 0.8f, i % 2 == 0 ? STONE_DARK : STONE, 1);
            }
            return;
        }
        // the pieces: each falls on its own path and turns
        int n = 9;
        for (int i = 0; i < n; i++) {
            float u = (i + 0.5f) / n;
            Vector3f p = at(foot, d, u);
            Vector3f s = sphere(f.id + 17, i);
            float t = gone;
            Vector3f q = add(p, s.x * t * 0.06f, s.y * 0.04f * t - 0.02f * t * t, s.z * t * 0.06f);
            Vector3f a2 = at(q, new Vector3f(fr[0]).rotateAxis(t * 0.2f * (hash(f.id, i, 3) - 0.5f), fr[1].x, fr[1].y, fr[1].z), len / n * 0.9f);
            DbFx.box(q, a2, 0.6f * (0.7f + 0.3f * hash(f.id, i, 4)), i % 3 == 0 ? STONE_DARK : STONE, out);
        }
    }

    // ---------- Yakon ----------

    // Eating the light: motes of light spiraling from a radius into the mouth (pos) over b ticks, faster near it.
    private static void suck(Fx f, float age) {
        Vector3f mouth = rel(f.pos);
        float R = f.a;
        float dur = Math.max(1, f.b);
        float out = 1 - smooth((age - dur) / 6);
        for (int i = 0; i < 40; i++) {
            float born = hash(f.id, i, 1) * dur * 0.8f;
            float t = age - born;
            float travel = 14 + 8 * hash(f.id, i, 2);
            if (t < 0 || t > travel) {
                continue;
            }
            float k = t / travel;
            float r = R * (1 - k * k) * (0.5f + 0.5f * hash(f.id, i, 3));
            float ang = hash(f.id, i, 4) * Mth.TWO_PI + k * k * 6;
            float y = (hash(f.id, i, 5) - 0.3f) * 3 * (1 - k);
            Vector3f q = add(mouth, Mth.cos(ang) * r, y, Mth.sin(ang) * r);
            Vector3f prev = add(mouth, Mth.cos(ang - 0.4f) * r * 1.08f, y * 1.05f, Mth.sin(ang - 0.4f) * r * 1.08f);
            rib(prev, q, 0.06f, FxDraw.STREAK, 0.9f, i % 3 == 0 ? LIGHT : DRAIN_LIGHT, 0.9f * out * Math.min(1, k * 4), true);
        }
        bb(mouth, 0.6f + 0.2f * Mth.sin(age * 0.8f), age * 0.2f, FxDraw.FLARE, 8 / 32f, LIGHT, 0.7f * out, true);
    }

    // The darkness: a dome of black smoke round pos (radius a) for b ticks; whoever is inside sees nearly nothing.
    private static void dark(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float R = f.a;
        float in = easeOut(age / 10);
        float out = 1 - smooth((age - f.b) / 20);
        if (out <= 0) {
            return;
        }
        if (DbFx.pass) {
            var player = Minecraft.getInstance().player;
            if (player != null && player.position().distanceTo(f.pos) < R * in && player.getId() != f.id) {
                float k = in * out;
                // dark, but the shapes still show (test 2026-11-13: the full black was too much)
                ScreenFx.flash(0x020104, 0.55f * k, 3);
                ScreenFx.vignette(0x000000, 0.9f * k, 4);
            }
            for (int i = 0; i < 10; i++) {
                float ph = (age * 0.02f + hash(f.id, i, 9)) % 1f;
                float ang = hash(f.id, i, 10) * Mth.TWO_PI;
                Vector3f q = add(c, Mth.cos(ang) * R * 0.9f * in, 0.3f + ph * 3, Mth.sin(ang) * R * 0.9f * in);
                bb(q, 0.12f, 0, FxDraw.SOFT, 0.8f, DARK_EDGE, 0.6f * out * (1 - ph), true);
            }
            return;
        }
        for (int i = 0; i < 60; i++) {
            Vector3f s = sphere(f.id + 3, i);
            s.y = Math.abs(s.y);
            float rr = R * in * (0.55f + 0.45f * hash(f.id, i, 1));
            float drift = age * 0.004f * (i % 2 == 0 ? 1 : -1);
            Vector3f dir = new Vector3f(s).rotateY(drift);
            Vector3f q = add(c, dir.x * rr, dir.y * rr * 0.7f + 0.3f, dir.z * rr);
            bb(q, 2.4f + 1.8f * hash(f.id, i, 2), i + age * 0.006f, FxDraw.SMOKE, 0.2f + 0.2f * hash(f.id, i, 3),
                    FxDraw.mix(DARK, DARK_EDGE, hash(f.id, i, 4) * 0.4f), 0.75f * in * out, false);
        }
    }

    // ---------- sand ----------

    // A handful of sand (or, b = 1, a gust that lifts the ground) thrown in a fan from pos along dir: each grain its own
    // speed, drag and fall, a dust cloud behind.
    private static void sand(Fx f, float age) {
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float len = d.length();
        Vector3f[] fr = frame(d);
        boolean gust = f.b >= 1;
        int n = gust ? 70 : 40;
        float life = gust ? 30 : 22;
        for (int i = 0; i < n; i++) {
            float spread = (hash(f.id, i, 1) - 0.5f) * 2 * f.a;
            float up = (hash(f.id, i, 2) - 0.3f) * f.a * 0.6f;
            Vector3f dir = new Vector3f(fr[0]).rotateAxis(spread, 0, 1, 0);
            dir.y += up;
            float drag = 0.12f + 0.12f * hash(f.id, i, 3);
            float sp = len * (0.6f + 0.5f * hash(f.id, i, 4));
            float t = age - hash(f.id, i, 5) * (gust ? 8 : 3);
            if (t < 0) {
                continue;
            }
            float dist = sp * (1 - (float) Math.exp(-drag * t));
            Vector3f q = at(from, dir, dist / Math.max(0.01f, dir.length()));
            q.y += gust ? 0.6f * Mth.sin(t * 0.2f + i) * Math.min(1, t / 6) : -0.006f * t * t;
            float k = t / life;
            if (k > 1) {
                continue;
            }
            bb(q, 0.05f + 0.05f * hash(f.id, i, 6), 0, FxDraw.SOLID, 0, i % 3 == 0 ? SAND_DARK : SAND, 1 - k, false);
        }
        int puffs = gust ? 10 : 5;
        for (int i = 0; i < puffs; i++) {
            float t = age - i * (gust ? 1.5f : 1);
            if (t < 0 || t > life) {
                continue;
            }
            float k = t / life;
            Vector3f q = at(from, fr[0], len * easeOut(k) * (0.4f + 0.6f * hash(f.id, i, 7)));
            q = at(q, fr[1], (hash(f.id, i, 8) - 0.5f) * len * 0.4f);
            bb(add(q, 0, 0.3f + k * 0.5f, 0), (0.6f + 1.4f * k) * (gust ? 1.6f : 1), i + t * 0.02f, FxDraw.SMOKE, 0.3f + k * 0.3f, SAND,
                    0.45f * (1 - k), false);
        }
    }
}
