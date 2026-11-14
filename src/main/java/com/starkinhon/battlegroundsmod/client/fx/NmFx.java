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

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// The Naruto creatures' own effects (animal/naruto/NmNet, SubNet kinds 380-399), drawn as the preview draws them
// (tools/mobs/naruto): the frogs' and the shark's water, the hunter's water prison, the salamander's gas on the ground,
// Zetsu's spores, the wood clone's stakes and cage, the crow's tomoe, flock and feathers, the Sound ninja's rings, the
// fan's wind blades, the mist, the Rain ninja's chain and umbrella, the toad's shockwave, the Gatsuga's drill, the
// marionetista's blue threads, and the screen of whoever is swallowed, caged, drowned or caught in a genjutsu. Drawn in
// DbFx's two passes (solid things in the plain one, light in the glow one). Age in ticks.
final class NmFx {
    private static final float[] WATER = FxDraw.rgb(0x5AB4F0);
    private static final float[] WATER_PALE = FxDraw.rgb(0xD8F2FF);
    private static final float[] WATER_DEEP = FxDraw.rgb(0x2A70B8);
    private static final float[] FOAM = FxDraw.rgb(0xF2F8FF);
    private static final float[] GAS = FxDraw.rgb(0x7A5A9A);
    private static final float[] GAS_GREEN = FxDraw.rgb(0x8AB04A);
    private static final float[] SPORE = FxDraw.rgb(0xF4F2E6);
    private static final float[] WOOD = FxDraw.rgb(0x8A6A44);
    private static final float[] WOOD_DARK = FxDraw.rgb(0x5A4028);
    private static final float[] RED = FxDraw.rgb(0xE01020);
    private static final float[] TOMOE = FxDraw.rgb(0x120808);
    private static final float[] SOUND = FxDraw.rgb(0xE6DCFF);
    private static final float[] AIR = FxDraw.rgb(0xEAF6FF);
    private static final float[] CROW = FxDraw.rgb(0x101014);
    private static final float[] MIST = FxDraw.rgb(0xE4EAEE);
    private static final float[] STEEL = FxDraw.rgb(0x8A9098);
    private static final float[] CLOTH = FxDraw.rgb(0x5A6070);
    private static final float[] SHADOW = {0.02f, 0.02f, 0.03f};
    private static final float[] DUST = FxDraw.rgb(0xA89A80);
    private static final float[] THREAD = FxDraw.rgb(0x6AB0FF);
    private static final float[] THREAD_CORE = FxDraw.rgb(0xDCEEFF);
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    private static final class Fx {
        final int kind;
        final int id;
        Vec3 pos;
        Vec3 dir;
        float a;
        float b;
        final double born;
        double updated;
        double endAt = -1;

        Fx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, double now) {
            this.kind = kind;
            this.id = id;
            this.pos = pos;
            this.dir = dir;
            this.a = a;
            this.b = b;
            this.born = now;
            this.updated = now;
        }
    }

    private static final List<Fx> SHOTS = new ArrayList<>();
    private static final Map<Long, Fx> TRACKED = new HashMap<>();

    // the screen of the local player: mode, until when
    private static int screenMode = -1;
    private static double screenUntil;
    private static double screenFrom;

    private NmFx() {
    }

    static boolean handles(int kind) {
        return kind >= NmNet.FIRST && kind <= NmNet.LAST;
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    private static boolean tracked(int kind) {
        return kind == NmNet.BUBBLE || kind == NmNet.SPORES || kind == NmNet.CAGE || kind == NmNet.MIST || kind == NmNet.CHAIN
                || kind == NmNet.DRILL || kind == NmNet.THREADS || kind == NmNet.UMBRELLA;
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (kind == NmNet.SCREEN) {
            screenMode = (int) a;
            screenFrom = now;
            screenUntil = b <= 0 ? now : now + b;
            if (b <= 0) {
                screenMode = -1;
            }
            return;
        }
        if (!tracked(kind)) {
            SHOTS.add(new Fx(kind, id, pos, dir, a, b, now));
            return;
        }
        Fx f = TRACKED.get(key(kind, id));
        boolean end = c >= 1 || (kind == NmNet.SPORES || kind == NmNet.DRILL) && a <= 0;
        if (end) {
            if (f != null && f.endAt < 0) {
                f.endAt = now;
            }
            return;
        }
        if (f == null || f.endAt >= 0) {
            f = new Fx(kind, id, pos, dir, a, b, now);
            TRACKED.put(key(kind, id), f);
        }
        f.pos = pos;
        f.dir = dir;
        f.a = a;
        f.b = b;
        f.updated = now;
        if (kind == NmNet.MIST || kind == NmNet.CAGE || kind == NmNet.UMBRELLA) {
            // these stand for their time from the last message
            f.endAt = -1;
        }
    }

    static boolean active() {
        return !SHOTS.isEmpty() || !TRACKED.isEmpty() || screenMode >= 0;
    }

    static void clear() {
        SHOTS.clear();
        TRACKED.clear();
        screenMode = -1;
    }

    private static float life(Fx f) {
        return switch (f.kind) {
            case NmNet.WATER_BALL -> f.b + 16;
            case NmNet.SPLASH -> 20;
            case NmNet.GAS -> f.b + 20;
            case NmNet.STAKES -> 46;
            case NmNet.TOMOE -> f.a;
            case NmNet.SOUND -> 18;
            case NmNet.WIND -> 14;
            case NmNet.FLOCK -> f.a + 6;
            case NmNet.SHOCK -> 16;
            case NmNet.FEATHERS -> f.a + 20;
            default -> 20;
        };
    }

    // how long a tracked effect lasts without news before it fades, and how long the fade takes
    private static float hold(Fx f) {
        return switch (f.kind) {
            case NmNet.MIST, NmNet.UMBRELLA -> f.b;
            case NmNet.CAGE -> f.a;
            case NmNet.THREADS -> 30;
            case NmNet.SPORES -> 200;
            default -> 6;
        };
    }

    private static float fade(Fx f) {
        return switch (f.kind) {
            case NmNet.MIST -> 30;
            case NmNet.CAGE -> 10;
            case NmNet.BUBBLE -> 6;
            default -> 8;
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
        if (screenMode >= 0 && now > screenUntil) {
            screenMode = -1;
        }
    }

    static void draw(double now, float partial) {
        boolean glow = DbFx.pass;
        if (glow) {
            prune(now);
            screen(now);
        }
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born);
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case NmNet.WATER_BALL -> waterBall(f, age);
                case NmNet.SPLASH -> splash(rel(f.pos), age, f.a, f.id + 7);
                case NmNet.GAS -> gas(f, age);
                case NmNet.STAKES -> stakes(f, age);
                case NmNet.TOMOE -> tomoe(f, age);
                case NmNet.SOUND -> sound(f, age);
                case NmNet.WIND -> wind(f, age);
                case NmNet.FLOCK -> flock(f, age);
                case NmNet.SHOCK -> shock(rel(f.pos), age, f.a);
                case NmNet.FEATHERS -> feathers(f, age);
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born);
            float out = f.endAt < 0 ? 1 : 1 - smooth((float) (now - f.endAt) / fade(f));
            if (out <= 0) {
                continue;
            }
            switch (f.kind) {
                case NmNet.BUBBLE -> bubble(f, age, out);
                case NmNet.SPORES -> spores(f, age, partial, out);
                case NmNet.CAGE -> cage(f, age, out);
                case NmNet.MIST -> mist(f, age, out);
                case NmNet.CHAIN -> chain(f, age, out);
                case NmNet.UMBRELLA -> umbrella(f, age, out);
                case NmNet.DRILL -> drill(f, age, partial, out);
                case NmNet.THREADS -> threads(f, age, partial, out);
                default -> {
                }
            }
        }
    }

    // ---------- helpers ----------

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static Vector3f add(Vector3f p, float x, float y, float z) {
        return new Vector3f(p).add(x, y, z);
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float k) {
        return new Vector3f(a).lerp(b, k);
    }

    private static Vector3f vec(Vec3 d) {
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }

    // two unit vectors square to d
    private static Vector3f[] frame(Vector3f d) {
        Vector3f n = new Vector3f(d);
        if (n.lengthSquared() < 1.0E-6f) {
            n.set(0, 0, 1);
        }
        n.normalize();
        Vector3f u = new Vector3f(n).cross(Math.abs(n.y) > 0.9f ? v(1, 0, 0) : UP).normalize();
        Vector3f w = new Vector3f(n).cross(u).normalize();
        return new Vector3f[] {n, u, w};
    }

    private static Vector3f bodyAt(int id, float partial, float up) {
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(id);
        if (e == null) {
            return null;
        }
        Vec3 p = e.getPosition(partial).add(0, e.getBbHeight() * up, 0);
        return rel(p);
    }

    // ---------- the screen ----------

    private static void screen(double now) {
        if (screenMode < 0) {
            return;
        }
        float left = (float) (screenUntil - now);
        float in = Math.min(1, (float) (now - screenFrom) / 4);
        float k = in * Math.min(1, left / 6);
        var player = Minecraft.getInstance().player;
        Vec3 eye = player == null ? Vec3.ZERO : player.getEyePosition();
        switch (screenMode) {
            // swallowed, caged: dark, a dim red of flesh or wood at the edges
            case 0 -> {
                ScreenFx.flash(0x050203, 0.93f * k, 3);
                ScreenFx.vignette(0x200404, 0.9f * k, 4);
            }
            // the genjutsu: the world goes red and black
            case 1 -> {
                ScreenFx.flash(0x5A0006, 0.38f * k, 3);
                ScreenFx.vignette(0x000000, 0.95f * k, 4);
            }
            // foam in the eyes
            case 2 -> {
                ScreenFx.flash(0xEEF6FF, 0.55f * k, 3);
                ScreenFx.sustainBlur(eye.add(player == null ? Vec3.ZERO : player.getLookAngle()), 0.6f * k);
            }
            // drowning in the bubble
            case 3 -> {
                ScreenFx.flash(0x1A5AA0, 0.32f * k, 3);
                ScreenFx.vignette(0x0A2A5A, 0.85f * k, 4);
            }
            // the Jujutsu curses (animal/jjmob, JjMobMoves): 4 the nine eyes' stare, purple closing in from the edges
            case 4 -> {
                ScreenFx.flash(0x3A0A5A, 0.3f * k, 3);
                ScreenFx.vignette(0x12021E, 0.95f * k, 4);
            }
            // 5 hot ash in the eyes: grey-black, nearly blind
            case 5 -> {
                ScreenFx.flash(0x1C1A18, 0.86f * k, 3);
                ScreenFx.vignette(0x050404, 0.95f * k, 4);
            }
            // 6 crows pecking at the face: dark, blinking with each peck
            case 6 -> {
                float blink = 0.55f + 0.35f * Math.abs(net.minecraft.util.Mth.sin((float) now * 0.9f));
                ScreenFx.flash(0x060406, blink * k, 3);
                ScreenFx.vignette(0x000000, 0.9f * k, 4);
            }
            // 7 a curse clinging to the face (the fly): half dark, a dim flesh edge
            case 7 -> {
                ScreenFx.flash(0x0A0608, 0.55f * k, 3);
                ScreenFx.vignette(0x2A0A10, 0.92f * k, 4);
            }
            default -> {
            }
        }
    }

    // ---------- water ----------

    // A ball of water (a = radius) flying from pos to pos + dir over b ticks; c-style 1 a tight spiral (torpedo),
    // 2 a gulp of foam. Bursts at the end.
    private static void waterBall(Fx f, float age) {
        float flight = Math.max(1, f.b);
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float len = d.length();
        float r = f.a;
        if (age < flight) {
            float k = age / flight;
            Vector3f p = at(from, d, k);
            p.y += Mth.sin(k * Mth.PI) * Math.min(1.2f, len * 0.08f) * (f.id == 0 ? 1 : 0);
            Vector3f[] fr = frame(d);
            float grow = Math.min(1, age / 3);
            // the body of water: a pale core, a wobbling skin, a ring turning along the way
            bb(p, r * 2.6f * grow, age * 0.2f, FxDraw.SOFT, 0.35f, WATER, 0.55f, true);
            bb(p, r * 1.5f * grow, -age * 0.3f, FxDraw.SOFT, 0.8f, WATER_PALE, 0.8f, true);
            for (int i = 0; i < 3; i++) {
                Vector3f n = new Vector3f(fr[0]).rotateAxis(age * 0.4f + i * 2.1f, fr[1].x, fr[1].y, fr[1].z);
                dsk(p, n, r * 2.1f * grow * (1 + 0.08f * Mth.sin(age + i)), FxDraw.RING, 0.08f, i == 0 ? WATER_PALE : WATER, 0.7f, true);
            }
            // drops and the spray behind it
            for (int i = 0; i < 8; i++) {
                float back = (i + 1) * r * 0.55f;
                Vector3f q = at(p, fr[0], -back);
                q = at(at(q, fr[1], (hash(i, f.id, 3) - 0.5f) * r), fr[2], (hash(i, f.id, 5) - 0.5f) * r);
                bb(q, r * (0.5f - i * 0.04f), 0, FxDraw.SOFT, 0.7f, i % 2 == 0 ? WATER_PALE : WATER, 0.6f * (1 - i / 8f), true);
            }
            if (f.id == 1) {
                // the torpedo: water turning around the line
                for (int i = 0; i < 10; i++) {
                    float u = i / 10f;
                    float th = age * 0.9f + u * 12;
                    Vector3f q = at(at(at(p, fr[0], -u * 2), fr[1], Mth.cos(th) * r * 1.3f), fr[2], Mth.sin(th) * r * 1.3f);
                    Vector3f q2 = at(at(at(p, fr[0], -u * 2 - 0.25f), fr[1], Mth.cos(th - 0.5f) * r * 1.3f), fr[2], Mth.sin(th - 0.5f) * r * 1.3f);
                    rib(q2, q, r * 0.3f, FxDraw.STREAK, 0.9f, WATER_PALE, 0.7f * (1 - u), true);
                }
            }
            return;
        }
        splash(at(from, d, 1), age - flight, Math.max(0.8f, r * 2.2f), f.id + 11);
    }

    // A splash: a ring on the water and drops thrown up that fall back.
    static void splash(Vector3f p, float age, float s, int seed) {
        if (age < 0 || age > 20) {
            return;
        }
        float k = age / 20;
        dsk(add(p, 0, 0.05f, 0), UP, s * (0.6f + 1.8f * easeOut(age / 10)), FxDraw.RING, 0.12f, WATER_PALE, 0.8f * (1 - k), true);
        dsk(add(p, 0, 0.04f, 0), UP, s * (0.4f + 1.2f * easeOut(age / 8)), FxDraw.RING, 0.2f, WATER, 0.6f * (1 - k), true);
        for (int i = 0; i < 18; i++) {
            Vector3f d = sphere(seed, i);
            d.y = Math.abs(d.y) * 1.6f + 0.6f;
            float sp = s * (0.08f + 0.06f * hash(seed, i, 2));
            float t = age;
            Vector3f q = v(p.x + d.x * sp * t, p.y + d.y * sp * t - 0.012f * t * t, p.z + d.z * sp * t);
            if (q.y < p.y - 0.1f) {
                continue;
            }
            bb(q, s * 0.18f * (1 - k * 0.5f), 0, FxDraw.SOFT, 0.75f, i % 3 == 0 ? FOAM : WATER, 0.85f * (1 - k), true);
        }
        if (age < 5) {
            bb(add(p, 0, 0.3f * s, 0), s * 1.6f, 0, FxDraw.SOFT, 0.5f, WATER_PALE, 0.5f * (1 - age / 5), true);
        }
    }

    // The water prison: a sphere of water around someone, its surface moving.
    private static void bubble(Fx f, float age, float out) {
        Vector3f c = rel(f.pos);
        float r = f.a * Math.min(1, easeOut(age / 6)) * (f.endAt < 0 ? 1 : 1 + (1 - out) * 0.4f);
        bb(c, r * 2.3f, age * 0.05f, FxDraw.SOFT, 0.25f, WATER_DEEP, 0.35f * out, true);
        bb(c, r * 2.0f, -age * 0.07f, FxDraw.SOFT, 0.55f, WATER, 0.25f * out, true);
        for (int i = 0; i < 9; i++) {
            Vector3f ax = sphere(71, i);
            Vector3f n = sphere(73, i).rotateAxis(age * (0.05f + 0.02f * i), ax.x, ax.y, ax.z);
            dsk(c, n, r * 2 * (0.97f + 0.03f * Mth.sin(age * 0.3f + i)), FxDraw.RING, 0.05f, i % 3 == 0 ? WATER_PALE : WATER, 0.55f * out, true);
        }
        // bubbles of the last air rising inside
        for (int i = 0; i < 6; i++) {
            float ph = (age * 0.04f + hash(i, 3, 1)) % 1f;
            Vector3f q = add(c, (hash(i, 3, 2) - 0.5f) * r, -r * 0.8f + ph * r * 1.6f, (hash(i, 3, 4) - 0.5f) * r);
            bb(q, 0.09f, 0, FxDraw.RING, 0.3f, WATER_PALE, 0.7f * out * (1 - ph), true);
        }
    }

    // ---------- gas, spores, mist ----------

    // The salamander's gas: low, heavy, spreading out to a radius and lying there.
    private static void gas(Fx f, float age) {
        if (DbFx.pass) {
            return;
        }
        Vector3f c = rel(f.pos);
        float R = f.a;
        float spread = easeOut(age / 14);
        float out = 1 - smooth((age - f.b) / 20);
        if (out <= 0) {
            return;
        }
        for (int i = 0; i < 46; i++) {
            float ang = hash(i, f.id, 1) * Mth.TWO_PI + age * 0.006f * (i % 2 == 0 ? 1 : -1);
            float rr = (0.15f + 0.85f * (float) Math.sqrt(hash(i, f.id, 2))) * R * spread;
            Vector3f q = add(c, Mth.cos(ang) * rr, 0.25f + 0.35f * hash(i, f.id, 3) + 0.08f * Mth.sin(age * 0.07f + i), Mth.sin(ang) * rr);
            float size = 1.3f + 1.2f * hash(i, f.id, 4);
            float[] col = FxDraw.mix(GAS, GAS_GREEN, hash(i, f.id, 5) * 0.7f);
            bb(q, size, i + age * 0.01f, FxDraw.SMOKE, 0.35f + 0.25f * hash(i, f.id, 6), col, 0.55f * out * Math.min(1, age / 5), false);
        }
    }

    // Zetsu's spores stuck on someone: little pale balls on the body, more of them the more are left.
    private static void spores(Fx f, float age, float partial, float out) {
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(f.id);
        if (e == null) {
            return;
        }
        Vec3 base = e.getPosition(partial);
        int n = Math.min(12, (int) f.a);
        float w = e.getBbWidth() * 0.55f, h = e.getBbHeight();
        for (int i = 0; i < n; i++) {
            float ang = hash(i, 9, 1) * Mth.TWO_PI;
            float y = 0.2f + 0.7f * hash(i, 9, 2);
            Vector3f p = rel(base.add(Mth.cos(ang) * w, y * h, Mth.sin(ang) * w));
            float pulse = 1 + 0.15f * Mth.sin(age * 0.4f + i);
            bb(p, 0.17f * pulse, 0, FxDraw.SOFT, 0.85f, SPORE, out, true);
            bb(p, 0.32f * pulse, 0, FxDraw.SOFT, 0.3f, GAS_GREEN, 0.3f * out, true);
        }
    }

    // The Hidden Mist: a white wall of fog around pos, slowly turning.
    private static void mist(Fx f, float age, float out) {
        if (DbFx.pass) {
            return;
        }
        Vector3f c = rel(f.pos);
        float R = f.a;
        float in = Math.min(1, age / 20);
        for (int i = 0; i < 40; i++) {
            float ang = hash(i, 5, 1) * Mth.TWO_PI + age * 0.004f;
            float rr = (0.3f + 0.7f * hash(i, 5, 2)) * R;
            Vector3f q = add(c, Mth.cos(ang) * rr, 0.4f + 2.2f * hash(i, 5, 3), Mth.sin(ang) * rr);
            bb(q, 2.4f + 1.6f * hash(i, 5, 4), i + age * 0.004f, FxDraw.SMOKE, 0.2f, MIST, 0.32f * in * out, false);
        }
    }

    // ---------- wood ----------

    // One stake: a square post of wood rising out of the ground, sharpened.
    private static void stake(Vector3f foot, float h, float w, float lean, float out) {
        if (h <= 0.02f) {
            return;
        }
        Vector3f top = add(foot, lean * h, h, 0);
        DbFx.box(add(foot, 0, -0.3f, 0), lerp(foot, top, 0.82f), w, WOOD, out);
        DbFx.box(lerp(foot, top, 0.8f), top, w * 0.45f, WOOD_DARK, out);
    }

    private static void stakes(Fx f, float age) {
        if (DbFx.pass) {
            if (age < 10) {
                DbFx.nrDust(rel(f.pos), age, f.id, 6, 0.8f, DUST, 14);
            }
            return;
        }
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        int n = Math.max(2, (int) f.a);
        float sink = age > 34 ? smooth((age - 34) / 12) : 0;
        for (int i = 0; i < n; i++) {
            float t0 = i * 1.4f;
            float grow = easeOut((age - t0) / 3);
            if (grow <= 0) {
                continue;
            }
            float u = (i + 0.5f) / n;
            Vector3f foot = at(from, d, u);
            foot = add(foot, (hash(i, f.id, 1) - 0.5f) * 0.4f, 0, (hash(i, f.id, 2) - 0.5f) * 0.4f);
            boolean last = i == n - 1;
            float h = (last ? 2.0f : 0.9f + 0.6f * hash(i, f.id, 3)) * grow * (1 - sink);
            stake(foot, h, last ? 0.45f : 0.28f, (hash(i, f.id, 4) - 0.5f) * 0.3f, 1);
            if (!last) {
                stake(add(foot, 0.35f, 0, 0.2f), h * 0.6f, 0.18f, 0.25f, 1);
            }
        }
    }

    // The wooden cage: eight beams out of the ground in a circle of 2 blocks, bending in to close over the middle.
    private static void cage(Fx f, float age, float out) {
        if (DbFx.pass) {
            if (age < 10) {
                DbFx.nrDust(rel(f.pos), age, f.id, 8, 1.1f, DUST, 14);
            }
            return;
        }
        Vector3f c = rel(f.pos);
        float grow = easeOut(age / 9) * out;
        for (int i = 0; i < 8; i++) {
            float ang = i * Mth.TWO_PI / 8 + 0.2f;
            Vector3f last = add(c, Mth.cos(ang) * 2, -0.2f, Mth.sin(ang) * 2);
            int segs = 7;
            for (int j = 1; j <= segs; j++) {
                float u = j / (float) segs;
                if (u > grow) {
                    break;
                }
                // up, then in over the top
                float bend = u * u;
                float rr = 2 * (1 - bend * 0.92f);
                float y = 3.1f * Mth.sin(u * Mth.HALF_PI);
                Vector3f p = add(c, Mth.cos(ang) * rr, y, Mth.sin(ang) * rr);
                DbFx.box(last, p, 0.32f - 0.12f * u, (i + j) % 3 == 0 ? WOOD_DARK : WOOD, 1);
                last = p;
            }
        }
    }

    // ---------- the crow ----------

    // The genjutsu: the crow's eye lights red and the three tomoe turn on a ring hanging between it and you.
    private static void tomoe(Fx f, float age) {
        if (!DbFx.pass) {
            return;
        }
        float life = f.a;
        float out = 1 - smooth((age - life + 10) / 10);
        Vector3f eye = rel(f.pos);
        Vector3f d = vec(f.dir);
        bb(eye, 0.35f + 0.1f * Mth.sin(age * 0.5f), 0, FxDraw.SOFT, 0.7f, RED, out, true);
        if (age < 10 || d.lengthSquared() < 0.01f) {
            return;
        }
        float k = Math.min(1, (age - 10) / 6);
        Vector3f mid = at(eye, d, 0.5f);
        Vector3f[] fr = frame(d);
        float R = 0.55f * easeOut(k);
        dsk(mid, fr[0], R * 2.4f, FxDraw.RING, 0.08f, RED, 0.9f * out, true);
        dsk(mid, fr[0], R * 0.6f, FxDraw.DISC, 0.2f, RED, 0.8f * out, true);
        for (int i = 0; i < 3; i++) {
            float th = age * 0.35f + i * Mth.TWO_PI / 3;
            Vector3f p = at(at(mid, fr[1], Mth.cos(th) * R), fr[2], Mth.sin(th) * R);
            bb(p, 0.24f, th, FxDraw.SOFT, 0.95f, TOMOE, out, false);
            Vector3f tail = at(at(mid, fr[1], Mth.cos(th - 0.5f) * R * 0.95f), fr[2], Mth.sin(th - 0.5f) * R * 0.95f);
            rib(tail, p, 0.07f, FxDraw.STREAK, 0.9f, TOMOE, out, false);
        }
    }

    // The flock: thirty crows streaming from pos through the target to pos + dir.
    private static void flock(Fx f, float age) {
        if (DbFx.pass) {
            return;
        }
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        Vector3f[] fr = frame(d);
        float T = Math.max(1, f.a);
        for (int i = 0; i < 30; i++) {
            float delay = hash(i, f.id, 1) * 8;
            float k = (age - delay) / (T - 8);
            if (k < 0 || k > 1) {
                continue;
            }
            // the bundle opens in the middle around the target and closes again at the end
            float spread = Mth.sin(k * Mth.PI) * 1.3f;
            float ox = (hash(i, f.id, 2) - 0.5f) * 2 * spread + Mth.sin(age * 0.3f + i) * 0.15f;
            float oy = (hash(i, f.id, 3) - 0.3f) * 1.6f * spread + Mth.cos(age * 0.25f + i) * 0.12f;
            Vector3f p = at(at(at(from, d, k), fr[1], ox), fr[2], oy);
            float flap = Mth.sin(age * 1.6f + i * 1.7f) * 0.18f;
            bb(p, 0.22f, 0, FxDraw.SOFT, 0.95f, CROW, 1, false);
            Vector3f wl = at(at(p, fr[1], 0.32f), UP, flap);
            Vector3f wr = at(at(p, fr[1], -0.32f), UP, flap);
            rib(p, wl, 0.12f, FxDraw.STREAK, 0.6f, CROW, 1, false);
            rib(p, wr, 0.12f, FxDraw.STREAK, 0.6f, CROW, 1, false);
        }
    }

    // Black feathers falling around the one caught in the genjutsu.
    private static void feathers(Fx f, float age) {
        if (DbFx.pass) {
            return;
        }
        Vector3f c = rel(f.pos);
        float out = 1 - smooth((age - f.a) / 20);
        for (int i = 0; i < 16; i++) {
            float start = hash(i, 4, 1) * f.a * 0.6f;
            float t = age - start;
            if (t < 0) {
                continue;
            }
            float y = 3.2f - t * 0.06f;
            if (y < 0.05f) {
                continue;
            }
            float ang = hash(i, 4, 2) * Mth.TWO_PI;
            float rr = 0.6f + 1.4f * hash(i, 4, 3);
            float sway = Mth.sin(t * 0.18f + i) * 0.35f;
            Vector3f p = add(c, Mth.cos(ang) * rr + sway, y, Mth.sin(ang) * rr);
            Vector3f q = add(p, 0.12f * Mth.cos(t * 0.2f + i), -0.22f, 0.12f * Mth.sin(t * 0.2f + i));
            rib(q, p, 0.08f, FxDraw.STREAK, 0.5f, CROW, out, false);
        }
    }

    // ---------- sound and wind ----------

    // Three rings of sound running out along dir, one after the other.
    private static void sound(Fx f, float age) {
        if (!DbFx.pass) {
            return;
        }
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        Vector3f n = new Vector3f(d).normalize();
        for (int i = 0; i < 3; i++) {
            float t = age - i * 3;
            if (t < 0 || t > 10) {
                continue;
            }
            float k = t / 10;
            Vector3f p = at(from, d, easeOut(k));
            dsk(p, n, 0.4f + 1.6f * k, FxDraw.RING, 0.1f, SOUND, 0.85f * (1 - k), true);
            dsk(p, n, 0.3f + 1.2f * k, FxDraw.RING, 0.05f, AIR, 0.5f * (1 - k), true);
            DbFx.warp(p, 0.8f + k, 0.3f * (1 - k), 1, t, new Vector3f(n));
        }
    }

    // The fan's wind blades: crescents of air thrown out in a fan of spread radians.
    private static void wind(Fx f, float age) {
        if (!DbFx.pass) {
            return;
        }
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float range = d.length();
        Vector3f fwd = new Vector3f(d).normalize();
        float k = easeOut(age / 9);
        float op = 1 - smooth((age - 7) / 7);
        for (int b = 0; b < 5; b++) {
            float yaw = (b - 2) / 2f * f.a;
            Vector3f dir = DbFx.rotY(fwd, yaw);
            Vector3f side = v(-dir.z, 0, dir.x);
            Vector3f mid = at(from, dir, range * k * (0.85f + 0.15f * hash(b, 2, 1)));
            mid.y += (hash(b, 2, 2) - 0.5f) * 0.6f;
            Vector3f last = null;
            for (int j = 0; j <= 8; j++) {
                float u = j / 8f - 0.5f;
                Vector3f p = at(at(mid, side, u * 1.6f), dir, -u * u * 1.4f);
                if (last != null) {
                    rib(last, p, 0.12f * (1 - Math.abs(u) * 1.4f) + 0.02f, FxDraw.STREAK, 0.9f, AIR, 0.85f * op, true);
                }
                last = p;
            }
        }
    }

    // ---------- the Rain ninja ----------

    // The chain: links from the hand to the hook.
    private static void chain(Fx f, float age, float out) {
        Vector3f from = rel(f.pos);
        Vector3f to = rel(f.pos.add(f.dir));
        float k = f.endAt < 0 ? easeOut(age / 4) : out;
        Vector3f tip = lerp(from, to, k);
        float len = from.distance(tip);
        int n = Math.max(2, (int) (len / 0.16f));
        Vector3f d = new Vector3f(tip).sub(from);
        if (d.lengthSquared() < 1.0E-4f) {
            return;
        }
        Vector3f[] fr = frame(d);
        if (!DbFx.pass) {
            for (int i = 0; i < n; i++) {
                Vector3f p = lerp(from, tip, (i + 0.5f) / n);
                p.y -= Mth.sin((i + 0.5f) / n * Mth.PI) * 0.15f * len / 6;
                dsk(p, i % 2 == 0 ? fr[1] : fr[2], 0.14f, FxDraw.RING, 0.35f, STEEL, 1, false);
            }
            DbFx.box(tip, at(tip, fr[0], 0.35f), 0.08f, STEEL, 1);
            DbFx.box(at(tip, fr[0], 0.3f), at(at(tip, fr[0], 0.15f), fr[1], 0.22f), 0.06f, STEEL, 1);
        } else if (age < 6) {
            bb(tip, 0.4f, 0, FxDraw.SOFT, 0.6f, AIR, 0.5f * (1 - age / 6), true);
        }
    }

    // The umbrella over the target: it opens and spins, its shadow turns on the ground, then the needles rain.
    private static void umbrella(Fx f, float age, float out) {
        Vector3f g = rel(f.pos);
        float R = f.a;
        float open = easeOut(age / 6);
        Vector3f top = add(g, 0, 5.2f - 0.4f * open, 0);
        float spin = age * 0.35f;
        if (!DbFx.pass) {
            // the shadow on the ground (the warning)
            dsk(add(g, 0, 0.04f, 0), UP, R * 0.9f * open, FxDraw.DISC, 0.25f, SHADOW, 0.45f * out, false);
            // the canopy: ribs and cloth panels
            for (int i = 0; i < 8; i++) {
                float th = spin + i * Mth.TWO_PI / 8;
                Vector3f rim = add(top, Mth.cos(th) * 0.8f * open, -0.35f * open, Mth.sin(th) * 0.8f * open);
                DbFx.box(top, rim, 0.06f, STEEL, out);
                float th2 = th + Mth.TWO_PI / 16;
                Vector3f mid = add(top, Mth.cos(th2) * 0.6f * open, -0.22f * open, Mth.sin(th2) * 0.6f * open);
                DbFx.box(top, mid, 0.3f * open, CLOTH, out);
            }
            DbFx.box(add(top, 0, 0.3f, 0), add(top, 0, -0.6f, 0), 0.05f, STEEL, out);
            return;
        }
        // the needles: falling glints over the circle once the rain starts
        if (age < 21) {
            return;
        }
        for (int i = 0; i < 24; i++) {
            float ph = ((age - 21) * 0.11f + hash(i, f.id, 1)) % 1f;
            float ang = hash(i, f.id, 2) * Mth.TWO_PI + spin * 0.2f;
            float rr = (float) Math.sqrt(hash(i, f.id, 3)) * R;
            Vector3f p = add(g, Mth.cos(ang) * rr, 4.6f * (1 - ph), Mth.sin(ang) * rr);
            Vector3f q = add(p, 0, 0.5f, 0);
            rib(p, q, 0.035f, FxDraw.STREAK, 0.95f, STEEL, 0.9f * out, true);
        }
    }

    // ---------- the ground ----------

    private static void shock(Vector3f p, float age, float R) {
        if (!DbFx.pass) {
            return;
        }
        float k = easeOut(age / 9);
        float op = 1 - smooth(age / 16);
        dsk(add(p, 0, 0.06f, 0), UP, R * 2 * k, FxDraw.RING, 0.1f, AIR, 0.8f * op, true);
        dsk(add(p, 0, 0.05f, 0), UP, R * 1.6f * k, FxDraw.RING, 0.25f, DUST, 0.5f * op, true);
        DbFx.nrDust(p, age, 17, 12, R * 0.4f, DUST, 18);
    }

    // ---------- the Gatsuga ----------

    private static void drill(Fx f, float age, float partial, float out) {
        if (!DbFx.pass) {
            return;
        }
        Vector3f c = bodyAt(f.id, partial, 0.5f);
        if (c == null) {
            return;
        }
        Vector3f fwd = vec(f.dir);
        Vector3f[] fr = frame(fwd);
        for (int i = 0; i < 14; i++) {
            float u = i / 14f;
            float th = age * 1.4f + u * 10;
            float r = 0.65f * (1 - u * 0.6f);
            Vector3f p = at(at(at(c, fr[0], 0.8f - u * 1.6f), fr[1], Mth.cos(th) * r), fr[2], Mth.sin(th) * r);
            Vector3f q = at(at(at(c, fr[0], 0.8f - u * 1.6f - 0.2f), fr[1], Mth.cos(th - 0.6f) * r), fr[2], Mth.sin(th - 0.6f) * r);
            rib(q, p, 0.09f, FxDraw.STREAK, 0.9f, i % 3 == 0 ? DUST : AIR, 0.8f * out, true);
        }
        bb(c, 1.6f, age, FxDraw.SOFT, 0.3f, AIR, 0.25f * out, true);
    }

    // ---------- the marionetista ----------

    // Four blue threads from its fingers to the puppet's back, a little slack between.
    private static void threads(Fx f, float age, float partial, float out) {
        if (!DbFx.pass) {
            return;
        }
        Vector3f a = bodyAt(f.id, partial, 0.68f);
        Vector3f b = bodyAt((int) f.b, partial, 0.8f);
        if (a == null || b == null) {
            return;
        }
        Vector3f d = new Vector3f(b).sub(a);
        Vector3f[] fr = frame(d);
        Vector3f hands = at(a, v(d.x, 0, d.z).normalize(), 0.35f);
        for (int k = 0; k < 4; k++) {
            float off = (k - 1.5f) * 0.12f;
            Vector3f from = at(hands, fr[1], off);
            Vector3f to = at(at(b, fr[1], off * 2), UP, (k % 2) * 0.15f);
            Vector3f last = from;
            for (int j = 1; j <= 12; j++) {
                float u = j / 12f;
                Vector3f p = lerp(from, to, u);
                p.y -= Mth.sin(u * Mth.PI) * 0.18f + Mth.sin(u * 9 + age * 0.15f + k) * 0.02f;
                rib(last, p, 0.03f, FxDraw.STREAK, 0.9f, THREAD, 0.85f * out, true);
                last = p;
            }
            bb(to, 0.12f, 0, FxDraw.SOFT, 0.8f, THREAD_CORE, 0.6f * out, true);
        }
    }
}
