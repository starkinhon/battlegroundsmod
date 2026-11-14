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

import com.starkinhon.battlegroundsmod.animal.jjmob.JmNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// The Jujutsu curses' own effects (animal/jjmob/JmNet, SubNet kinds 480-499), drawn as the preview draws them
// (tools/mobs/jjmobs, jjfx.js): the tubes of flesh (tongues, the umbilical cord, the well woman's hair, the larva's
// silk), the things they throw (seeds, barnacles, the Okiku's plates, lava bombs), the blue will-o'-wisps, magma on the
// ground and its cracks, the basalt columns, the arms of the ground, the masks' scream, the sea rising and spat, the hot
// ash, the field of flowers, the vines, the mound of something burrowing, the talismans, the stuffing. Every grain and
// drop has its own path from the hash (nothing copied). Solid things take the light where they are (a curse's tongue
// is not lit up at night); fire and magma are light themselves. Drawn in DbFx's two passes. Age in ticks.
final class JmFx {
    private static final float[][] TUBE_COLORS = {
            FxDraw.rgb(0xB04060), FxDraw.rgb(0x9A3A50), FxDraw.rgb(0x101014), FxDraw.rgb(0xEDE8DA), FxDraw.rgb(0x5A3E26), FxDraw.rgb(0x8A6A66)};
    private static final float[][] TUBE_DARK = {
            FxDraw.rgb(0x6A1830), FxDraw.rgb(0x5A1A2A), FxDraw.rgb(0x26262E), FxDraw.rgb(0xB8B2A0), FxDraw.rgb(0x2E1E10), FxDraw.rgb(0x5A4440)};
    private static final float[] WISP = FxDraw.rgb(0x4A9CFF);
    private static final float[] WISP_CORE = FxDraw.rgb(0xD8F0FF);
    private static final float[] WISP_DEEP = FxDraw.rgb(0x1E4ED8);
    private static final float[] MAGMA_HOT = FxDraw.rgb(0xFF7A1A);
    private static final float[] MAGMA_WHITE = FxDraw.rgb(0xFFE6A0);
    private static final float[] CRUST = FxDraw.rgb(0x1A1414);
    private static final float[] CRUST_LIT = FxDraw.rgb(0x3A2E2A);
    private static final float[] BASALT = FxDraw.rgb(0x232023);
    private static final float[] BASALT_EDGE = FxDraw.rgb(0x48444A);
    private static final float[] EARTH = FxDraw.rgb(0x5A4632);
    private static final float[] EARTH_DARK = FxDraw.rgb(0x2E2418);
    private static final float[] ARM = FxDraw.rgb(0x6E5A52);
    private static final float[] ARM_DARK = FxDraw.rgb(0x3A2C2A);
    private static final float[] NAIL = FxDraw.rgb(0x101014);
    private static final float[] CURSE = FxDraw.rgb(0x5A1E8A);
    private static final float[] CURSE_DARK = FxDraw.rgb(0x0E0614);
    private static final float[] CURSE_PALE = FxDraw.rgb(0xB070FF);
    private static final float[] SEA = FxDraw.rgb(0x4C9CC8);
    private static final float[] SEA_DEEP = FxDraw.rgb(0x1E4E6A);
    private static final float[] FOAM = FxDraw.rgb(0xEAF8FF);
    private static final float[] VOMIT = FxDraw.rgb(0x5E7A48);
    private static final float[] SEAWEED = FxDraw.rgb(0x2E5A2A);
    private static final float[] ASH = FxDraw.rgb(0x5A5450);
    private static final float[] ASH_DARK = FxDraw.rgb(0x26221F);
    private static final float[] EMBER = FxDraw.rgb(0xFF6A1A);
    private static final float[][] PETALS = {FxDraw.rgb(0xFFB7CF), FxDraw.rgb(0xB89AF0), FxDraw.rgb(0xE8602A)};
    private static final float[] GRASS = FxDraw.rgb(0x4E8A3A);
    private static final float[] VINE = FxDraw.rgb(0x3E6E2A);
    private static final float[] VINE_DARK = FxDraw.rgb(0x22401A);
    private static final float[] SEED = FxDraw.rgb(0x6A5A2E);
    private static final float[] SHELL = FxDraw.rgb(0xC8C0B0);
    private static final float[] SHELL_DARK = FxDraw.rgb(0x7A7268);
    private static final float[] PORCELAIN = FxDraw.rgb(0xF4F2EC);
    private static final float[] PORCELAIN_BLUE = FxDraw.rgb(0x2A4E9A);
    private static final float[] ROCK = FxDraw.rgb(0x2A2422);
    private static final float[] PAPER = FxDraw.rgb(0xF3EAD2);
    private static final float[] PAPER_RED = FxDraw.rgb(0xB3261E);
    private static final float[] COTTON = FxDraw.rgb(0xF2EEE6);
    private static final float[] CORE = FxDraw.rgb(0x7A5CFF);
    private static final float[] ENERGY = FxDraw.rgb(0x3A7CFF);
    private static final float[] ENERGY_CORE = FxDraw.rgb(0xCFE4FF);
    private static final float[] SMOKE = FxDraw.rgb(0x4A4440);
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
        // for the wisps: where they circle, relative to the one that made them (it moves)
        Vec3 offset = Vec3.ZERO;

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

    private JmFx() {
    }

    static boolean handles(int kind) {
        return kind >= JmNet.FIRST && kind <= JmNet.LAST;
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    private static boolean tracked(int kind) {
        return kind == JmNet.TUBE;
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (kind == JmNet.EXORCIZE) {
            AshDeath.startExorcize(id, (int) a);
            return;
        }
        if (!tracked(kind)) {
            Fx f = new Fx(kind, id, pos, dir, a, b, c, now);
            if (kind == JmNet.WISP) {
                Minecraft mc = Minecraft.getInstance();
                Entity e = mc.level == null ? null : mc.level.getEntity(id);
                if (e != null) {
                    f.offset = pos.subtract(e.position());
                }
            }
            SHOTS.add(f);
            return;
        }
        Fx f = TRACKED.get(key(kind, id));
        if (c >= 1) {
            if (f != null && f.endAt < 0) {
                f.endAt = now;
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
            case JmNet.THROW -> f.a + 26;
            case JmNet.WISP -> f.a + f.c + 14;
            case JmNet.MAGMA -> f.b + 20;
            case JmNet.MAGMA_CRACK -> f.a + 40;
            case JmNet.BASALT -> f.c + 24;
            case JmNet.HANDS -> f.b + 14;
            case JmNet.WAVE -> 30;
            case JmNet.RISING -> f.b + 16;
            case JmNet.JET -> f.a + Math.max(14, f.b >= 1 ? f.c : 0);
            case JmNet.ASH_CLOUD -> f.b + 24;
            case JmNet.FLOWERS -> f.b + 20;
            case JmNet.VINE -> f.a + 10;
            case JmNet.MOUND -> f.a + 16;
            case JmNet.PAPER -> 34;
            case JmNet.STUFFING -> 50;
            case JmNet.ENERGY -> f.a + 8;
            default -> 20;
        };
    }

    private static void prune(double now) {
        SHOTS.removeIf(f -> now - f.born > life(f));
        Iterator<Fx> it = TRACKED.values().iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            if (f.endAt < 0 && now - f.updated > 6) {
                f.endAt = f.updated + 6;
            }
            if (f.endAt >= 0 && now - f.endAt > 6) {
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
                case JmNet.THROW -> thrown(f, age);
                case JmNet.WISP -> wisp(f, age, partial);
                case JmNet.MAGMA -> magma(f, age);
                case JmNet.MAGMA_CRACK -> magmaCrack(f, age);
                case JmNet.BASALT -> basalt(f, age);
                case JmNet.HANDS -> hands(f, age);
                case JmNet.WAVE -> wave(f, age);
                case JmNet.RISING -> rising(f, age);
                case JmNet.JET -> jet(f, age);
                case JmNet.ASH_CLOUD -> ashCloud(f, age);
                case JmNet.FLOWERS -> flowers(f, age);
                case JmNet.VINE -> vine(f, age);
                case JmNet.MOUND -> mound(f, age);
                case JmNet.PAPER -> paper(f, age);
                case JmNet.STUFFING -> stuffing(f, age);
                case JmNet.ENERGY -> energy(f, age, partial);
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born);
            float out = f.endAt < 0 ? 1 : 1 - smooth((float) (now - f.endAt) / 6);
            if (f.kind == JmNet.TUBE) {
                tube(f, age, out);
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

    // the world's light at a point (0.1 in the dark .. 1 in full light), for the solid things
    private static float light(Vec3 w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 1;
        }
        int packed = LevelRenderer.getLightColor(mc.level, BlockPos.containing(w));
        float sky = LightTexture.sky(packed) / 15f * mc.level.getSkyDarken(1f);
        float block = LightTexture.block(packed) / 15f;
        return 0.1f + 0.9f * Math.max(sky, block);
    }

    private static float[] lit(float[] c, float k) {
        return new float[] {c[0] * k, c[1] * k, c[2] * k};
    }

    // a solid bar from a to b (plain pass only), in the light l
    private static void bar(Vector3f a, Vector3f b, float w, float[] col, float l, float op) {
        if (!DbFx.pass && op > 0.01f) {
            DbFx.box(a, b, w, lit(col, l), op);
        }
    }

    private static void ground(Vector3f c, float size, float spin, int shape, float param, float[] col, float op, boolean glow) {
        if (glow == DbFx.pass && op > 0.002f) {
            FxDraw.diskSpin(c, UP, size, spin, shape, param, col, op, glow);
        }
    }

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

    private static Vector3f arc(Vector3f p, Vector3f d, float k, float lift) {
        Vector3f q = at(p, d, k);
        q.y += 4 * k * (1 - k) * lift;
        return q;
    }

    // ---------- tubes of flesh ----------

    // From the root (pos) to the tip (pos + dir), tapering, sagging in an arc, with lumps running down it (the cord's
    // pulse, the tongue's swallow); the hair is a bundle of thin strands, the silk a few pale threads.
    private static void tube(Fx f, float age, float out) {
        Vector3f a = rel(f.pos);
        Vector3f d = vec(f.dir);
        int look = Mth.clamp((int) f.b, 0, TUBE_COLORS.length - 1);
        float len = d.length();
        if (len < 0.05f) {
            return;
        }
        float reach = Math.min(1, age / 3f) * out;
        float l = light(f.pos.add(f.dir.scale(0.5)));
        Vector3f side = new Vector3f(UP).cross(d);
        if (side.lengthSquared() < 1.0E-5f) {
            side.set(1, 0, 0);
        }
        side.normalize();
        float r0 = Math.max(0.03f, f.a);
        float r1 = r0 * (look == 2 || look == 3 ? 0.8f : 0.5f);
        float sag = look == 3 ? 0.05f : Math.min(0.5f, len * 0.06f);
        float pulse = look == 1 ? (age * 0.07f) % 1f : look == 0 ? 1 - (age * 0.05f) % 1f : -1;
        int n = 14;
        int strands = look == 2 ? 5 : look == 3 ? 3 : 1;
        for (int s = 0; s < strands; s++) {
            float so = strands == 1 ? 0 : (s - (strands - 1) / 2f) * r0 * 0.9f;
            Vector3f prev = null;
            for (int i = 0; i <= n; i++) {
                float k = i / (float) n * reach;
                float wig = (look == 2 ? 0.08f : 0.03f) * Mth.sin(k * 9 - age * 0.35f + s);
                Vector3f p = at(a, d, k).add(new Vector3f(side).mul((sag * 0.6f + wig) * Mth.sin(Mth.PI * k) + so * (1 - k * 0.6f)));
                p.y -= sag * Mth.sin(Mth.PI * k);
                if (prev != null) {
                    float w = r0 + (r1 - r0) * k;
                    if (pulse >= 0) {
                        w *= 1 + 0.9f * (float) Math.exp(-Math.pow((k - pulse) * 9, 2));
                    }
                    if (strands > 1) {
                        w = r0 * 0.35f;
                    }
                    float[] col = i % 3 == 0 ? TUBE_DARK[look] : TUBE_COLORS[look];
                    bar(prev, p, w * 2, col, l, out);
                }
                prev = p;
            }
        }
        if (look == 0 && reach > 0.9f) {
            // the tongue's wet tip
            bb(at(a, d, reach), r0 * 3, 0, FxDraw.SOFT, 0.5f, TUBE_COLORS[0], 0.35f * out * l, false);
        }
    }

    // ---------- things thrown ----------

    private static void thrown(Fx f, float age) {
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float flight = Math.max(1, f.a);
        int what = (int) f.b;
        float l = light(f.pos.add(f.dir));
        if (age < flight) {
            float k = age / flight;
            Vector3f p = arc(from, d, k, f.c);
            Vector3f[] fr = frame(d);
            float spin = age * 0.9f + f.id;
            Vector3f ax = new Vector3f(fr[1]).mul(Mth.cos(spin)).add(new Vector3f(fr[2]).mul(Mth.sin(spin)));
            switch (what) {
                case 0 -> bar(at(p, ax, -0.06f), at(p, ax, 0.06f), 0.09f, SEED, l, 1);
                case 1 -> {
                    bar(at(p, ax, -0.08f), at(p, ax, 0.08f), 0.13f, SHELL, l, 1);
                    bar(at(p, ax, 0.06f), at(p, ax, 0.12f), 0.07f, SHELL_DARK, l, 1);
                }
                case 2 -> {
                    // a plate spinning flat, edge first, its blue rim catching the light
                    Vector3f n = new Vector3f(fr[2]).mul(0.25f).add(UP).normalize();
                    if (!DbFx.pass) {
                        FxDraw.diskSpin(p, n, 0.62f, age * 1.4f, FxDraw.DISC, 0.15f, lit(PORCELAIN, l), 1, false);
                        FxDraw.diskSpin(add(p, 0, 0.004f, 0), n, 0.5f, age * 1.4f, FxDraw.RING, 0.12f, lit(PORCELAIN_BLUE, l), 1, false);
                    }
                    bb(p, 0.5f, 0, FxDraw.SOFT, 0.2f, PORCELAIN, 0.12f, true);
                }
                case 3 -> {
                    bar(at(p, ax, -0.16f), at(p, ax, 0.16f), 0.3f, ROCK, 1, 1);
                    bb(p, 0.75f, age, FxDraw.FIRE, 0.25f, MAGMA_HOT, 0.9f, true);
                    bb(p, 0.35f, 0, FxDraw.SOFT, 0.85f, MAGMA_WHITE, 0.8f, true);
                    for (int i = 1; i < 6; i++) {
                        float kk = Math.max(0, k - i * 0.035f);
                        Vector3f q = arc(from, d, kk, f.c);
                        bb(q, 0.25f + i * 0.08f, i + age * 0.05f, FxDraw.SMOKE, 0.3f + i * 0.08f, SMOKE, 0.4f * (1 - i / 6f), false);
                    }
                }
                default -> bb(p, 0.1f, spin, FxDraw.SOFT, 0.6f, PORCELAIN, 0.9f, false);
            }
            return;
        }
        float t = age - flight;
        Vector3f c = at(from, d, 1);
        switch (what) {
            case 0 -> {
                for (int i = 0; i < 8; i++) {
                    Vector3f s = sphere(f.id + 3, i);
                    s.y = Math.abs(s.y);
                    float k = Math.min(1, t / 10);
                    bb(add(c, s.x * 0.4f * k, 0.05f + s.y * 0.4f * k - 0.3f * k * k, s.z * 0.4f * k), 0.18f, i, FxDraw.SMOKE, 0.4f + k * 0.4f, EARTH, 0.5f * (1 - k), false);
                }
            }
            case 2, 4 -> {
                // the plate shatters: shards fly out and lie glinting
                for (int i = 0; i < 12; i++) {
                    Vector3f s = sphere(f.id + 17, i);
                    s.y = Math.abs(s.y) * 0.8f + 0.2f;
                    float k = Math.min(1, t / 9);
                    float sp = 0.6f + 0.8f * hash(f.id, i, 3);
                    Vector3f q = add(c, s.x * sp * k, Math.max(0.03f, s.y * sp * k * 1.2f - 1.2f * k * k), s.z * sp * k);
                    float o = 1 - smooth((t - 14) / 10);
                    if (!DbFx.pass && o > 0) {
                        FxDraw.billboard(q, 0.12f + 0.08f * hash(f.id, i, 4), t * 0.3f + i, 14, (i % 2) * 0.5f + 0.1f, lit(PORCELAIN, l), o, false);
                    }
                }
            }
            case 3 -> {
                float k = Math.min(1, t / 8);
                bb(add(c, 0, 0.3f, 0), 1.6f * easeOut(k), t, FxDraw.FIRE, 0.3f + k * 0.5f, MAGMA_HOT, 0.9f * (1 - k), true);
                for (int i = 0; i < 10; i++) {
                    Vector3f s = sphere(f.id + 5, i);
                    s.y = Math.abs(s.y) + 0.3f;
                    Vector3f q = add(c, s.x * k * 1.2f, s.y * k * 1.4f - 1.6f * k * k, s.z * k * 1.2f);
                    bb(q, 0.12f, 0, FxDraw.SOFT, 0.8f, MAGMA_WHITE, 1 - k, true);
                }
            }
            default -> {
                float k = Math.min(1, t / 8);
                for (int i = 0; i < 5; i++) {
                    Vector3f s = sphere(f.id + 9, i);
                    bb(add(c, s.x * 0.3f * k, Math.abs(s.y) * 0.3f * k, s.z * 0.3f * k), 0.06f, 0, FxDraw.SOFT, 0.6f, SHELL, 1 - k, false);
                }
            }
        }
    }

    // ---------- blue will-o'-wisps ----------

    // Circles its maker's head (its own place on the circle, bobbing), then dives to where it was sent, a tail of blue
    // flame behind it, and puffs out.
    private static void wisp(Fx f, float age, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Entity maker = mc.level == null ? null : mc.level.getEntity(f.id);
        Vec3 center = maker != null ? maker.getPosition(partial).add(f.offset) : f.pos;
        float orbit = Math.max(1, f.a);
        float fly = Math.max(1, f.c);
        float ang = f.b * Mth.TWO_PI / 3 + age * 0.22f;
        float grow = Math.min(1, age / 6);
        Vector3f circ = add(rel(center), Mth.cos(ang) * 0.7f * grow, 0.15f * Mth.sin(age * 0.4f + f.b), Mth.sin(ang) * 0.7f * grow);
        Vector3f p;
        float puff = -1;
        if (age < orbit) {
            p = circ;
        } else if (age < orbit + fly) {
            float k = (age - orbit) / fly;
            Vector3f to = rel(f.pos.add(f.dir));
            p = new Vector3f(circ).lerp(to, k * k);
            p.y += Mth.sin(Mth.PI * k) * 0.6f;
            for (int i = 1; i < 6; i++) {
                float kk = Math.max(0, k - i * 0.06f);
                Vector3f q = new Vector3f(circ).lerp(to, kk * kk);
                q.y += Mth.sin(Mth.PI * kk) * 0.6f;
                bb(q, 0.3f * (1 - i / 7f), age + i, FxDraw.FIRE, 0.3f + i * 0.1f, WISP, 0.7f * (1 - i / 6f), true);
            }
        } else {
            p = rel(f.pos.add(f.dir));
            puff = (age - orbit - fly) / 14;
        }
        if (puff >= 0) {
            bb(p, 0.9f * easeOut(puff), age, FxDraw.FIRE, 0.4f + puff * 0.6f, WISP, 0.8f * (1 - puff), true);
            bb(p, 1.3f * easeOut(puff), 0, FxDraw.RING, 0.2f, WISP_CORE, 0.5f * (1 - puff), true);
            return;
        }
        float fl = 0.85f + 0.15f * Mth.sin(age * 1.7f + f.b * 2);
        bb(add(p, 0, 0.08f, 0), 0.55f * fl * grow, age * 0.3f, FxDraw.FIRE, 0.2f, WISP, 0.95f, true);
        bb(p, 0.3f * grow, 0, FxDraw.SOFT, 0.9f, WISP_CORE, 0.95f, true);
        bb(p, 1.1f * grow, 0, FxDraw.SOFT, 0.1f, WISP_DEEP, 0.35f, true);
    }

    // ---------- magma ----------

    // A pool of molten rock: a dark crust in plates with glowing cracks (the souls_vfx cracks, hot), a molten middle
    // that cools as it goes, heat shimmering and a few bubbles bursting.
    private static void magma(Fx f, float age) {
        Vector3f c = add(rel(f.pos), 0, 0.03f, 0);
        float R = f.a;
        float grow = easeOut(age / 6);
        float out = 1 - smooth((age - f.b) / 20);
        if (out <= 0) {
            return;
        }
        float heat = Mth.clamp(1 - age / Math.max(1, f.b + 10), 0.25f, 1);
        float spin = hash(f.id, 1, 1) * Mth.TWO_PI;
        ground(c, R * 2.1f * grow, spin, FxDraw.DISC, 0.3f, CRUST, 0.92f * out, false);
        ground(add(c, 0, 0.004f, 0), R * 1.5f * grow, spin, FxDraw.SOFT, 0.6f, MAGMA_HOT, 0.85f * heat * out, true);
        ground(add(c, 0, 0.008f, 0), R * 2.0f * grow, spin, 39, (Math.round(63 * grow)) / 255f, MAGMA_HOT, 0.9f * out * (0.4f + 0.6f * heat), true);
        ground(add(c, 0, 0.006f, 0), R * 2.0f * grow, spin + 1, 38, (Math.round(63 * grow)) / 255f, CRUST_LIT, 0.7f * out, false);
        bb(add(c, 0, 0.4f, 0), R * 2.4f, 0, FxDraw.SOFT, 0.2f, MAGMA_HOT, 0.25f * heat * out, true);
        for (int i = 0; i < 6; i++) {
            float ph = (age * (0.04f + 0.03f * hash(f.id, i, 2)) + hash(f.id, i, 3)) % 1f;
            float ang = hash(f.id, i, 4) * Mth.TWO_PI;
            float rr = R * 0.7f * (float) Math.sqrt(hash(f.id, i, 5));
            Vector3f q = add(c, Mth.cos(ang) * rr, 0.05f + ph * 0.1f, Mth.sin(ang) * rr);
            if (ph < 0.85f) {
                bb(q, 0.1f + 0.15f * ph, 0, FxDraw.SOFT, 0.7f, MAGMA_WHITE, 0.7f * heat * out, true);
            } else {
                bb(add(q, 0, 0.1f, 0), 0.4f, i, FxDraw.FIRE, 0.5f, MAGMA_HOT, 0.6f * heat * out * (1 - (ph - 0.85f) * 6), true);
            }
        }
    }

    // A crack of magma running from pos along dir (a ticks), a glowing zig-zag with dark lips, bursting at its end.
    private static void magmaCrack(Fx f, float age) {
        Vector3f a = add(rel(f.pos), 0, 0.03f, 0);
        Vector3f d = vec(f.dir);
        float run = Math.max(1, f.a);
        float k = Mth.clamp(age / run, 0, 1);
        float out = 1 - smooth((age - run - 16) / 24);
        if (out <= 0) {
            return;
        }
        float len = d.length();
        Vector3f side = new Vector3f(UP).cross(d);
        if (side.lengthSquared() > 1.0E-5f) {
            side.normalize();
        }
        int n = Math.max(4, (int) (len * 3));
        Vector3f prev = a;
        for (int i = 1; i <= n; i++) {
            float u = i / (float) n;
            if (u > k) {
                break;
            }
            float zig = (hash(f.id, i, 7) - 0.5f) * 0.35f * (1 - u * 0.5f);
            Vector3f p = at(a, d, u).add(new Vector3f(side).mul(zig));
            float w = 0.22f * (1 - 0.6f * u);
            rib(prev, p, w * 2.2f, FxDraw.STREAK, 0.2f, CRUST, 0.85f * out, false);
            rib(prev, p, w, FxDraw.STREAK, 0.8f, MAGMA_HOT, out, true);
            if (hash(f.id, i, 9) > 0.6f) {
                Vector3f br = add(p, side.x * (hash(f.id, i, 10) - 0.5f) * 0.9f, 0, side.z * (hash(f.id, i, 10) - 0.5f) * 0.9f);
                rib(p, br, w * 0.5f, FxDraw.STREAK, 0.6f, MAGMA_HOT, 0.7f * out, true);
            }
            prev = p;
        }
        if (age >= run) {
            float t = (age - run) / 14;
            Vector3f tip = at(a, d, 1);
            float big = f.b >= 1 ? 1.6f : 1f;
            if (t < 1) {
                bb(add(tip, 0, 0.6f * big, 0), 2.4f * big * easeOut(t), age, FxDraw.FIRE, 0.25f + t * 0.6f, MAGMA_HOT, 0.95f * (1 - t), true);
                bb(add(tip, 0, 0.4f, 0), 1.2f * big, 0, FxDraw.SOFT, 0.85f, MAGMA_WHITE, 0.9f * (1 - t), true);
                for (int i = 0; i < 12; i++) {
                    Vector3f s = sphere(f.id + 21, i);
                    s.y = Math.abs(s.y) + 0.5f;
                    Vector3f q = add(tip, s.x * t * 1.5f * big, s.y * t * 2.2f * big - 2.4f * t * t, s.z * t * 1.5f * big);
                    bb(q, 0.14f, 0, FxDraw.SOFT, 0.8f, MAGMA_WHITE, 1 - t, true);
                }
            }
            bb(add(tip, 0, 0.8f + t * 2, 0), 1.2f + t * 1.5f, age * 0.03f, FxDraw.SMOKE, 0.3f + t * 0.4f, SMOKE, 0.45f * (1 - Math.min(1, t * 0.6f)) * out, false);
        }
    }

    // ---------- basalt ----------

    // A hexagonal column rising out of the ground in a few ticks (a crack of dust round its foot), hot joints glowing
    // while it cools; after c ticks it sinks back.
    private static void basalt(Fx f, float age) {
        Vector3f foot = rel(f.pos);
        float H = f.a;
        float R = f.b;
        float rise = easeOut(age / 4);
        float sink = smooth((age - f.c) / 16);
        float h = H * rise * (1 - sink);
        if (h < 0.02f) {
            return;
        }
        float l = light(f.pos.add(0, H * 0.5, 0));
        float heat = Mth.clamp(1 - age / 60f, 0, 1);
        float turn = hash(f.id, 2, 2) * Mth.TWO_PI;
        Vector3f[] corner = new Vector3f[6];
        for (int i = 0; i < 6; i++) {
            float an = turn + i * Mth.TWO_PI / 6;
            corner[i] = new Vector3f(Mth.cos(an) * R, 0, Mth.sin(an) * R);
        }
        if (!DbFx.pass) {
            for (int i = 0; i < 6; i++) {
                Vector3f c0 = corner[i];
                Vector3f c1 = corner[(i + 1) % 6];
                Vector3f n = new Vector3f(c0).add(c1).normalize();
                float shade = 0.55f + 0.45f * Math.max(0, n.x * 0.4f + n.z * 0.3f + 0.4f);
                float[] col = lit(i % 2 == 0 ? BASALT : BASALT_EDGE, l * shade);
                FxDraw.quad(add(foot, c0.x, -0.2f, c0.z), add(foot, c1.x, -0.2f, c1.z), add(foot, c1.x, h, c1.z), add(foot, c0.x, h, c0.z), FxDraw.SOLID,
                        0, col, 1, false);
            }
            // the top
            Vector3f top = add(foot, 0, h, 0);
            for (int i = 0; i < 6; i++) {
                Vector3f c0 = corner[i];
                Vector3f c1 = corner[(i + 1) % 6];
                FxDraw.quad(top, add(top, c0.x, 0, c0.z), add(top, c1.x, 0, c1.z), top, FxDraw.SOLID, 0, lit(BASALT_EDGE, l), 1, false);
            }
        }
        // the cooling joints, glowing bands round it
        if (heat > 0.02f) {
            for (int j = 1; j < (int) (h / 0.45f); j++) {
                float y = j * 0.45f + 0.1f * hash(f.id, j, 5);
                if (y > h - 0.05f) {
                    break;
                }
                for (int i = 0; i < 6; i++) {
                    Vector3f c0 = corner[i];
                    Vector3f c1 = corner[(i + 1) % 6];
                    rib(add(foot, c0.x * 1.01f, y, c0.z * 1.01f), add(foot, c1.x * 1.01f, y, c1.z * 1.01f), 0.05f, FxDraw.STREAK, 0.8f, MAGMA_HOT, heat * 0.9f, true);
                }
            }
            bb(add(foot, 0, 0.2f, 0), R * 2.5f, 0, FxDraw.SOFT, 0.3f, MAGMA_HOT, 0.3f * heat, true);
        }
        if (age < 10) {
            float t = age / 10;
            for (int i = 0; i < 10; i++) {
                Vector3f s = sphere(f.id + 13, i);
                s.y = Math.abs(s.y);
                bb(add(foot, s.x * (R + t * 1.2f), 0.1f + s.y * t * 0.8f, s.z * (R + t * 1.2f)), 0.35f + t * 0.4f, i, FxDraw.SMOKE, 0.4f + t * 0.4f,
                        EARTH, 0.5f * (1 - t), false);
            }
        }
    }

    // ---------- arms of the ground ----------

    // c arms tearing out of the ground round pos, grey-brown with black nails, reaching in and clutching, then sinking.
    private static void hands(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float R = f.a;
        int n = Math.max(3, (int) f.c);
        float l = light(f.pos.add(0, 1, 0));
        float out = 1 - smooth((age - f.b) / 12);
        if (out <= 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float delay = hash(f.id, i, 1) * 6;
            float t = age - delay;
            if (t < 0) {
                continue;
            }
            float up = easeOut(t / 5) * out;
            float ang = i * Mth.TWO_PI / n + hash(f.id, i, 2) * 0.5f;
            float rr = R * (0.7f + 0.35f * hash(f.id, i, 3));
            Vector3f base = add(c, Mth.cos(ang) * rr, -0.3f, Mth.sin(ang) * rr);
            float lenA = (0.9f + 0.5f * hash(f.id, i, 4)) * up;
            float grip = Mth.clamp((t - 5) / 4, 0, 1);
            Vector3f in = new Vector3f(-Mth.cos(ang), 0, -Mth.sin(ang));
            Vector3f elbow = add(base, in.x * 0.15f, lenA * 0.65f, in.z * 0.15f);
            Vector3f hand = add(elbow, in.x * (0.25f + 0.35f * grip), lenA * 0.4f - 0.2f * grip, in.z * (0.25f + 0.35f * grip));
            float sq = 0.03f * Mth.sin(age * 0.8f + i);
            bar(base, elbow, 0.2f, i % 2 == 0 ? ARM : ARM_DARK, l, 1);
            bar(elbow, hand, 0.17f + sq, ARM, l, 1);
            // fingers clutching
            for (int k = 0; k < 4; k++) {
                float fa = (k - 1.5f) * 0.25f;
                Vector3f dir = new Vector3f(in).rotateY(fa).mul(0.18f);
                Vector3f tip = add(hand, dir.x, 0.12f - 0.18f * grip, dir.z);
                bar(hand, tip, 0.05f, ARM_DARK, l, 1);
                bar(tip, add(tip, dir.x * 0.25f, -0.04f, dir.z * 0.25f), 0.04f, NAIL, l, 1);
            }
        }
        // torn earth round the holes
        if (age < 12) {
            float t = age / 12;
            for (int i = 0; i < 14; i++) {
                Vector3f s = sphere(f.id + 33, i);
                s.y = Math.abs(s.y);
                bb(add(c, s.x * R * (0.8f + t * 0.5f), 0.1f + s.y * t, s.z * R * (0.8f + t * 0.5f)), 0.4f + t * 0.5f, i, FxDraw.SMOKE, 0.4f + t * 0.4f,
                        EARTH_DARK, 0.5f * (1 - t), false);
            }
        }
    }

    // ---------- the masks' scream ----------

    // A wall of black and purple cursed energy running on the ground from pos along dir, licking up (the cursed energy
    // flame, souls_vfx shape 33), tearing the ground's dust up as it passes.
    private static void wave(Fx f, float age) {
        Vector3f a = rel(f.pos);
        Vector3f d = vec(f.dir);
        float k = easeOut(age / 18);
        float out = 1 - smooth((age - 16) / 14);
        if (out <= 0) {
            return;
        }
        Vector3f[] fr = frame(new Vector3f(d.x, 0, d.z));
        float W = f.a;
        Vector3f front = at(a, d, k);
        for (int i = -4; i <= 4; i++) {
            float s = i / 4f;
            Vector3f p = at(front, fr[1], s * W * (0.6f + 0.4f * k));
            p.y += 0.05f;
            float hgt = (1.6f - Math.abs(s) * 0.8f) * (0.8f + 0.2f * Mth.sin(age * 0.7f + i));
            Vector3f top = add(p, 0, hgt, 0);
            rib(p, top, 1.1f, 33, 0.5f, CURSE_DARK, 0.85f * out, false);
            rib(p, top, 0.8f, FxDraw.FIRE, 0.3f, CURSE, 0.7f * out, true);
            bb(add(p, 0, hgt * 0.5f, 0), 0.6f, age + i, FxDraw.SOFT, 0.3f, CURSE_PALE, 0.25f * out, true);
        }
        for (int i = 0; i < 10; i++) {
            float u = hash(f.id, i, 4) * k;
            Vector3f p = at(at(a, d, u), fr[1], (hash(f.id, i, 5) - 0.5f) * W * 1.6f);
            float t = (k - u) * 3;
            bb(add(p, 0, 0.2f + t * 0.6f, 0), 0.5f + t * 0.6f, i, FxDraw.SMOKE, 0.4f + t * 0.3f, CURSE_DARK, 0.5f * out * Math.max(0, 1 - t), false);
        }
    }

    // ---------- water ----------

    // The sea rising round someone: an open column of water climbing to a blocks, its top rippling and foaming, light
    // running in it, drops falling off.
    private static void rising(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float R = Math.max(0.6f, f.c);
        float up = easeOut(age / Math.max(1, f.b * 0.3f));
        float out = 1 - smooth((age - f.b) / 16);
        if (out <= 0) {
            return;
        }
        float H = f.a * up;
        int segs = 16;
        for (int i = 0; i < segs; i++) {
            float a0 = i * Mth.TWO_PI / segs + age * 0.04f;
            float a1 = (i + 1) * Mth.TWO_PI / segs + age * 0.04f;
            Vector3f p0 = add(c, Mth.cos(a0) * R, 0, Mth.sin(a0) * R);
            Vector3f p1 = add(c, Mth.cos(a1) * R, 0, Mth.sin(a1) * R);
            float wv = 0.08f * Mth.sin(a0 * 5 + age * 0.3f);
            if (!DbFx.pass) {
                FxDraw.quad(p0, p1, add(p1, 0, H + wv, 0), add(p0, 0, H + wv, 0), FxDraw.SOLID, 0, i % 2 == 0 ? SEA : SEA_DEEP, 0.42f * out, false);
            }
            bb(add(p0, 0, H + wv, 0), 0.4f, age + i, FxDraw.SOFT, 0.5f, FOAM, 0.6f * out, false);
        }
        for (int i = 0; i < 8; i++) {
            float ph = (age * (0.05f + 0.03f * hash(f.id, i, 2)) + hash(f.id, i, 3)) % 1f;
            float an = hash(f.id, i, 4) * Mth.TWO_PI;
            Vector3f q = add(c, Mth.cos(an) * R * (0.6f + 0.4f * ph), ph * H, Mth.sin(an) * R * (0.6f + 0.4f * ph));
            bb(q, 0.25f, 0, FxDraw.SOFT, 0.7f, FOAM, 0.35f * out * (1 - ph), true);
        }
        dsk(add(c, 0, 0.03f, 0), UP, R * 2.4f, FxDraw.DISC, 0.4f, SEA_DEEP, 0.55f * out, false);
    }

    // A jet of water from the mouth along dir: drops and foam in a thick stream, a splash where it hits; the vomit is
    // green with seaweed and leaves a slippery puddle.
    private static void jet(Fx f, float age) {
        Vector3f a = rel(f.pos);
        Vector3f d = vec(f.dir);
        boolean vomit = f.b >= 1;
        float dur = Math.max(1, f.a);
        float[] col = vomit ? VOMIT : SEA;
        float[] deep = vomit ? SEAWEED : SEA_DEEP;
        float on = age < dur ? 1 : 1 - smooth((age - dur) / 8);
        Vector3f[] fr = frame(d);
        if (on > 0) {
            for (int i = 0; i < 26; i++) {
                float ph = (age * 0.09f + hash(f.id, i, 1)) % 1f;
                float spread = ph * (0.25f + 0.15f * hash(f.id, i, 2));
                float an = hash(f.id, i, 3) * Mth.TWO_PI;
                Vector3f q = at(a, d, ph).add(new Vector3f(fr[1]).mul(Mth.cos(an) * spread)).add(new Vector3f(fr[2]).mul(Mth.sin(an) * spread));
                q.y -= ph * ph * 0.4f;
                bb(q, 0.3f + 0.4f * ph, i + age * 0.1f, FxDraw.SOFT, 0.4f, i % 4 == 0 ? FOAM : i % 2 == 0 ? col : deep, 0.75f * on, false);
            }
            rib(a, at(a, d, 1), 0.5f, FxDraw.BEAM, 0.3f, col, 0.45f * on, false);
            Vector3f end = at(a, d, 1);
            for (int i = 0; i < 10; i++) {
                Vector3f s = sphere(f.id + i + (int) age, i);
                s.y = Math.abs(s.y) + 0.3f;
                float ph = (age * 0.15f + hash(f.id, i, 6)) % 1f;
                bb(add(end, s.x * ph, s.y * ph - ph * ph, s.z * ph), 0.12f, 0, FxDraw.SOFT, 0.6f, FOAM, 0.8f * on * (1 - ph), false);
            }
        }
        if (vomit) {
            float t = age - dur * 0.5f;
            float out = 1 - smooth((t - f.c) / 14);
            if (t > 0 && out > 0) {
                Vector3f floor = add(at(a, d, 0.9f), 0, 0, 0);
                floor.y = (float) (f.pos.y + f.dir.y - DbFx.cam.y) + 0.04f;
                float g = easeOut(t / 8);
                dsk(floor, UP, 3.2f * g, FxDraw.DISC, 0.5f, SEAWEED, 0.7f * out, false);
                dsk(add(floor, 0, 0.004f, 0), UP, 2.4f * g, FxDraw.DISC, 0.5f, VOMIT, 0.55f * out, false);
                for (int i = 0; i < 5; i++) {
                    float ph = (t * 0.05f + hash(f.id, i, 9)) % 1f;
                    float an = hash(f.id, i, 10) * Mth.TWO_PI;
                    dsk(add(floor, Mth.cos(an) * 0.8f, 0.008f, Mth.sin(an) * 0.8f), UP, 0.2f + ph * 1.2f, FxDraw.RING, 0.2f, FOAM, 0.5f * out * (1 - ph), false);
                }
            }
        }
    }

    // ---------- hot ash ----------

    // A cloud of grey-black ash rolling on the ground from pos along dir, embers in it, swelling as it goes.
    private static void ashCloud(Fx f, float age) {
        Vector3f a = rel(f.pos);
        Vector3f d = vec(f.dir);
        float R = f.a;
        float out = 1 - smooth((age - f.b) / 24);
        if (out <= 0) {
            return;
        }
        float k = easeOut(age / 20);
        for (int i = 0; i < 22; i++) {
            float u = hash(f.id, i, 1) * k;
            float an = hash(f.id, i, 2) * Mth.TWO_PI;
            float rr = R * (0.3f + 0.7f * hash(f.id, i, 3)) * (0.5f + 0.5f * k);
            Vector3f q = at(a, d, u).add(Mth.cos(an) * rr, 0.3f + hash(f.id, i, 4) * 1.4f + age * 0.01f, Mth.sin(an) * rr);
            float roll = age * 0.03f + i;
            bb(q, 1.1f + 1.2f * hash(f.id, i, 5) + k * 0.6f, roll, FxDraw.SMOKE, 0.3f + 0.3f * k, i % 3 == 0 ? ASH_DARK : ASH, 0.6f * out, false);
        }
        for (int i = 0; i < 10; i++) {
            float ph = (age * (0.03f + 0.02f * hash(f.id, i, 6)) + hash(f.id, i, 7)) % 1f;
            float an = hash(f.id, i, 8) * Mth.TWO_PI;
            Vector3f q = at(a, d, hash(f.id, i, 9) * k).add(Mth.cos(an) * R * 0.8f, 0.2f + ph * 1.8f, Mth.sin(an) * R * 0.8f);
            bb(q, 0.08f, 0, FxDraw.SOFT, 0.9f, EMBER, out * (1 - ph) * (0.5f + 0.5f * Mth.sin(age * 0.6f + i)), true);
        }
    }

    // ---------- flowers ----------

    // The ground flowering round pos: grass and petals opening out from the middle, a few petals drifting in the air.
    private static void flowers(Fx f, float age) {
        Vector3f c = add(rel(f.pos), 0, 0.03f, 0);
        float R = f.a;
        int col = Mth.clamp((int) f.c, 0, PETALS.length - 1);
        float grow = easeOut(age / 16);
        float out = 1 - smooth((age - f.b) / 20);
        if (out <= 0) {
            return;
        }
        float l = light(f.pos.add(0, 0.5, 0));
        dsk(c, UP, R * 2 * grow, FxDraw.DISC, 0.3f, lit(GRASS, l), 0.55f * out, false);
        ground(add(c, 0, 0.005f, 0), R * 2 * grow, 0, FxDraw.RING, 0.05f, PETALS[col], 0.35f * out, true);
        int n = (int) (R * R * 6);
        for (int i = 0; i < Math.min(n, 120); i++) {
            float an = hash(f.id, i, 1) * Mth.TWO_PI;
            float rr = R * (float) Math.sqrt(hash(f.id, i, 2));
            if (rr > R * grow) {
                continue;
            }
            float open = Mth.clamp((age - rr / R * 12) / 6, 0, 1);
            Vector3f p = add(c, Mth.cos(an) * rr, 0.08f + 0.06f * hash(f.id, i, 3), Mth.sin(an) * rr);
            if (!DbFx.pass) {
                FxDraw.diskSpin(p, UP, 0.22f * open, an, 25, hash(f.id, i, 4), lit(PETALS[col], l), out, false);
                FxDraw.diskSpin(add(p, 0, 0.002f, 0), UP, 0.08f * open, 0, FxDraw.DISC, 0.3f, lit(MAGMA_WHITE, l), out, false);
            }
        }
        for (int i = 0; i < 16; i++) {
            float ph = (age * (0.012f + 0.01f * hash(f.id, i, 5)) + hash(f.id, i, 6)) % 1f;
            float an = hash(f.id, i, 7) * Mth.TWO_PI + ph * 3;
            Vector3f q = add(c, Mth.cos(an) * R * 0.8f, 2.2f - ph * 2.2f, Mth.sin(an) * R * 0.8f);
            if (!DbFx.pass) {
                FxDraw.billboard(q, 0.14f, age * 0.1f + i, 25, ph, lit(PETALS[col], l), 0.8f * out * grow, false);
            }
        }
    }

    // ---------- vines and mounds ----------

    // A vine sprouted from a seed, wound round where a foot stands, curling and swaying until it is torn.
    private static void vine(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float out = 1 - smooth((age - f.a) / 10);
        float l = light(f.pos.add(0, 0.4, 0));
        float up = easeOut(age / 6) * out;
        for (int s = 0; s < 3; s++) {
            Vector3f prev = add(c, 0, 0.02f, 0);
            for (int i = 1; i <= 8; i++) {
                float k = i / 8f;
                float an = s * Mth.TWO_PI / 3 + k * 5 + Mth.sin(age * 0.2f + s) * 0.3f;
                float rr = 0.25f * (1 - k * 0.4f);
                Vector3f p = add(c, Mth.cos(an) * rr, k * 0.75f * up, Mth.sin(an) * rr);
                bar(prev, p, 0.06f * (1 - k * 0.5f), i % 2 == 0 ? VINE : VINE_DARK, l, out);
                prev = p;
            }
        }
    }

    // The ground bulging and running from pos to pos + dir in a ticks (dirt thrown up along it), then a burst of earth.
    private static void mound(Fx f, float age) {
        Vector3f a = rel(f.pos);
        Vector3f d = vec(f.dir);
        float run = Math.max(1, f.a);
        float k = Mth.clamp(age / run, 0, 1);
        float l = light(f.pos.add(f.dir));
        Vector3f head = at(a, d, k);
        if (age < run) {
            bb(add(head, 0, 0.15f, 0), 0.8f, age, FxDraw.SMOKE, 0.3f, lit(EARTH, l), 0.85f, false);
            bb(add(head, 0, 0.3f, 0), 0.5f, -age, FxDraw.SMOKE, 0.3f, lit(EARTH_DARK, l), 0.9f, false);
        }
        int trail = (int) (k * 12);
        for (int i = 0; i < trail; i++) {
            float u = i / 12f;
            float t = (k - u) * run;
            Vector3f q = at(a, d, u);
            bb(add(q, 0, 0.1f + t * 0.02f, 0), 0.5f + t * 0.03f, i, FxDraw.SMOKE, 0.4f + t * 0.03f, lit(EARTH, l), 0.5f * Math.max(0, 1 - t / 16), false);
        }
        if (age >= run) {
            float t = (age - run) / 14;
            for (int i = 0; i < 14; i++) {
                Vector3f s = sphere(f.id + 7, i);
                s.y = Math.abs(s.y) + 0.4f;
                bb(add(head, s.x * t * 1.2f, s.y * t * 1.8f - 1.8f * t * t, s.z * t * 1.2f), 0.25f, i, FxDraw.SMOKE, 0.4f, lit(EARTH_DARK, l), 1 - t, false);
            }
        }
    }

    // ---------- paper, stuffing, energy ----------

    // Talismans bursting out (the Kitsune's fakes going back to paper): each slip flutters out and falls, red marks on it.
    private static void paper(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float l = light(f.pos);
        int n = Math.max(4, (int) f.a);
        for (int i = 0; i < n; i++) {
            Vector3f s = sphere(f.id + 41, i);
            s.y = Math.abs(s.y) + 0.2f;
            float t = age / 34;
            float sp = 1 + hash(f.id, i, 1);
            Vector3f q = add(c, s.x * sp * easeOut(t) + 0.15f * Mth.sin(age * 0.3f + i), s.y * sp * 0.8f * easeOut(t) - t * t * 1.2f, s.z * sp * easeOut(t));
            float o = 1 - smooth((t - 0.6f) / 0.4f);
            Vector3f[] fr = frame(new Vector3f(Mth.cos(age * 0.2f + i), 0.3f, Mth.sin(age * 0.2f + i)));
            Vector3f u = new Vector3f(fr[1]).mul(0.08f);
            Vector3f v = new Vector3f(fr[2]).mul(0.2f);
            if (!DbFx.pass) {
                FxDraw.quad(new Vector3f(q).sub(u).sub(v), new Vector3f(q).add(u).sub(v), new Vector3f(q).add(u).add(v), new Vector3f(q).sub(u).add(v), FxDraw.SOLID, 0,
                        lit(PAPER, l), o, false);
                FxDraw.quad(new Vector3f(q).sub(u.mul(0.5f)).add(v.mul(0.2f)), new Vector3f(q).add(u).add(v), new Vector3f(q).add(u).add(new Vector3f(v).mul(1.5f)),
                        new Vector3f(q).sub(u).add(new Vector3f(v).mul(1.5f)), FxDraw.SOLID, 0, lit(PAPER_RED, l), o * 0.9f, false);
            }
        }
        if (age < 8) {
            bb(add(c, 0, 0.4f, 0), 1.6f * easeOut(age / 8), 0, FxDraw.SOFT, 0.5f, PAPER, 0.5f * (1 - age / 8), true);
        }
    }

    // The cursed corpse's stuffing bursting out of its seams in white tufts, and (a = 1) its energy core rolling out,
    // its light going out.
    private static void stuffing(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float l = light(f.pos);
        for (int i = 0; i < 18; i++) {
            Vector3f s = sphere(f.id + 51, i);
            s.y = Math.abs(s.y) + 0.3f;
            float t = Math.min(1, age / 16);
            float sp = 0.6f + 0.8f * hash(f.id, i, 1);
            Vector3f q = add(c, s.x * sp * easeOut(t), Math.max(-0.45f, s.y * sp * 0.9f * easeOut(t) - t * t * 1.1f), s.z * sp * easeOut(t));
            float o = 1 - smooth((age - 30) / 20);
            bb(q, 0.18f + 0.12f * hash(f.id, i, 2), i + age * 0.02f, FxDraw.SMOKE, 0.25f, lit(COTTON, l), 0.95f * o, false);
        }
        if (f.a >= 1) {
            float t = Math.min(1, age / 30);
            Vector3f core = add(c, 0.6f * easeOut(t), -0.3f * easeOut(Math.min(1, age / 10)), 0.3f * easeOut(t));
            float dim = 1 - t;
            bb(core, 0.22f, 0, FxDraw.SOFT, 0.9f, CORE, 0.9f * (0.3f + 0.7f * dim), false);
            bb(core, 0.8f * dim, 0, FxDraw.SOFT, 0.3f, CORE, 0.5f * dim, true);
        }
    }

    // Cursed energy gathering on a blade (blue light running up it) or on a fist (b = 1: blue fire licking off it).
    private static void energy(Fx f, float age, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(f.id);
        // on the body it gathers on (it moves a little while it charges)
        Vector3f p = e != null ? rel(e.getPosition(partial).add(f.dir)) : rel(f.pos);
        float dur = Math.max(1, f.a);
        float k = Mth.clamp(age / dur, 0, 1);
        float out = 1 - smooth((age - dur) / 8);
        if (out <= 0) {
            return;
        }
        if (f.b >= 1) {
            for (int i = 0; i < 6; i++) {
                float ph = (age * 0.12f + hash(f.id, i, 1)) % 1f;
                Vector3f q = add(p, (hash(f.id, i, 2) - 0.5f) * 0.25f, ph * 0.5f, (hash(f.id, i, 3) - 0.5f) * 0.25f);
                bb(q, (0.35f - ph * 0.2f) * (0.5f + 0.5f * k), age + i, FxDraw.FIRE, 0.2f + ph * 0.6f, ENERGY, 0.9f * out, true);
            }
            bb(p, 0.5f * (0.5f + 0.5f * k), 0, FxDraw.SOFT, 0.8f, ENERGY_CORE, 0.8f * out, true);
            return;
        }
        bb(p, 0.9f * k, age, FxDraw.FLARE, 6 / 32f, ENERGY, 0.6f * out, true);
        bb(p, 0.35f, 0, FxDraw.SOFT, 0.85f, ENERGY_CORE, 0.8f * out * k, true);
    }
}
