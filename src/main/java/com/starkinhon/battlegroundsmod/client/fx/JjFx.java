package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.at;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.bb;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.dsk;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.easeOut;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rib;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.smooth;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.sphere;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.warp;
import static com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.hash;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Jujutsu SubAlmas' effects (power/powers/sub/jj), ported from the Visualizador's Jujutsu tabs (v56): Itadori's
// cursed energy in flat ink (shape CEINK) and the Divergent Fist's opaque brush aura (shape BRUSH) that lags behind the
// fist in echoes, the delayed second impact going through the body, the Black Flash's closing ring; Choso's blood that
// looks liquid (dark edge, strong red, a thin gloss, no neon); Nanami's 7:3 lines and the fist in the ground; Nobara's
// nails, doll and Resonance; Todo's clap and swaps; Inumaki's rings of voice; Miwa's thin light blue cuts and Simple
// Domain. Drawn inside DbFx's passes (its bb/rib/dsk only draw in the pass they name). The Visualizador's T is the
// effect's own age.
final class JjFx {
    private static final float[] CE_INK = FxDraw.rgb(0x2CC8F2);
    private static final float[] CE = FxDraw.rgb(0x4AA8FF);
    private static final float[] CE_W = FxDraw.rgb(0xDCEFFF);
    private static final float[] DIV_CYAN = FxDraw.rgb(0x5AD8FF);
    private static final float[] DIV_MID = FxDraw.rgb(0x2AA8FF);
    private static final float[] DIV_HOT = FxDraw.rgb(0xBFF4FF);
    private static final float[] INK = {0.06f, 0.06f, 0.07f};
    private static final float[] BLACK = {0, 0, 0};
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] RED = FxDraw.rgb(0xFF1A2A);
    private static final float[] GOLD = FxDraw.rgb(0xFFB43A);
    private static final float[] GOLD_W = FxDraw.rgb(0xFFF0C8);
    private static final float[] BLOOD = {0.6f, 0, 0.035f};
    private static final float[] BLOOD_D = {0.22f, 0, 0.02f};
    private static final float[] BLOOD_HI = FxDraw.rgb(0xFF5A5A);
    private static final float[] BLOOD_R = FxDraw.rgb(0xC8000F);
    private static final float[] GLOSS = {1, 0.5f, 0.48f};
    private static final float[] VIOLET = FxDraw.rgb(0xA070FF);
    private static final float[] LILAC = FxDraw.rgb(0xE2D4FF);
    private static final float[] STEEL = {0.82f, 0.86f, 0.9f};
    private static final float[] STEEL_D = {0.6f, 0.62f, 0.66f};
    private static final float[] ROCK = {0.42f, 0.42f, 0.45f};
    private static final float[] ROCK_D = {0.28f, 0.28f, 0.3f};
    private static final float[] CRACK = {0.04f, 0.035f, 0.03f};
    private static final float[] MIWA = FxDraw.rgb(0x8FD0FF);
    private static final float[] AIR = FxDraw.rgb(0xE8F4FF);
    private static final float[] DUST = {0.42f, 0.4f, 0.38f};
    private static final float[] SMOKE_COL = {0.45f, 0.41f, 0.36f};
    private static final float[] SMOKE_D = {0.38f, 0.34f, 0.3f};
    private static final float[] RING_DIRT = {0.55f, 0.48f, 0.38f};
    private static final float[] DIRT = FxDraw.rgb(0x5A4632);
    private static final float[] STRAW = FxDraw.rgb(0xC8A860);
    private static final float[] STRAW_D = FxDraw.rgb(0x8A6A34);
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
        // When a stream's a last changed (the rope wrapping, the doll's nail hammered in).
        double changed;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();

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
            this.changed = now;
        }
    }

    private static final List<Fx> SHOTS = new ArrayList<>();
    private static final Map<Long, Fx> TRACKED = new HashMap<>();
    // Where each aura's fists were, tick by tick (the echoes that lag behind).
    private static final Map<Integer, ArrayDeque<Vec3[]>> FISTS = new HashMap<>();
    private static long fistTick = Long.MIN_VALUE;

    private JjFx() {
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    private static boolean stream(int kind) {
        return switch (kind) {
            case SubNet.DIV_AURA, SubNet.BF_RING, SubNet.GUARD, SubNet.DASH_TRAIL, SubNet.BLOOD_BALL, SubNet.SUPERNOVA, SubNet.ROPE,
                    SubNet.GROUND_MARK, SubNet.NAIL, SubNet.DOLL, SubNet.SIMPLE_DOMAIN -> true;
            default -> false;
        };
    }

    // Kinds that last a while on one message, following an entity (a = ticks, 0 stops).
    private static boolean timed(int kind) {
        return switch (kind) {
            case SubNet.SCALE, SubNet.RATIO, SubNet.GOLD_AURA, SubNet.SEAL_MARKS, SubNet.HOLD_RINGS, SubNet.COUGH, SubNet.SLEEP -> true;
            default -> false;
        };
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (stream(kind)) {
            Fx f = TRACKED.get(key(kind, id));
            if (f == null || f.endAt >= 0) {
                f = new Fx(kind, id, pos, dir, a, b, c, now);
                TRACKED.put(key(kind, id), f);
            }
            if (f.a != a) {
                f.changed = now;
            }
            f.pos = pos;
            f.dir = dir;
            f.a = a;
            f.b = b;
            f.c = c;
            f.updated = now;
            f.trail.addLast(pos);
            while (f.trail.size() > 12) {
                f.trail.removeFirst();
            }
            if (c == 1) {
                f.endAt = now;
            }
            return;
        }
        if (timed(kind)) {
            Fx f = TRACKED.get(key(kind, id));
            if (a <= 0) {
                if (f != null && f.endAt < 0) {
                    f.endAt = now;
                }
                return;
            }
            TRACKED.put(key(kind, id), new Fx(kind, id, pos, dir, a, b, c, now));
            return;
        }
        SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
    }

    static boolean active() {
        return !SHOTS.isEmpty() || !TRACKED.isEmpty();
    }

    static void clear() {
        SHOTS.clear();
        TRACKED.clear();
        FISTS.clear();
    }

    private static double life(Fx f) {
        return switch (f.kind) {
            case SubNet.DIVERGENT -> f.a + 42;
            case SubNet.INK_BURST -> 20;
            case SubNet.ARC -> 8;
            case SubNet.CRACKS -> 70;
            case SubNet.BLOOD_BEAM -> 30;
            case SubNet.ORB_POP -> 16;
            case SubNet.SUPERNOVA_BURST -> 30;
            case SubNet.BLOOD_SPLASH -> 28;
            case SubNet.RATIO_CRIT -> 16;
            case SubNet.SLAM -> 82;
            case SubNet.RESONANCE -> 32;
            case SubNet.RESO_LINE -> 9;
            case SubNet.CLAP -> 13;
            case SubNet.SWAP -> 11;
            case SubNet.VOICE -> 40;
            case SubNet.CRUSH -> 62;
            case SubNet.MIWA_LINE -> 9;
            case SubNet.MIWA_ARC -> 4;
            case SubNet.DASH_LINE -> 9;
            case SubNet.DOMAIN_POP -> 15;
            default -> 40;
        };
    }

    private static float fadeOut(Fx f) {
        return switch (f.kind) {
            case SubNet.DIV_AURA, SubNet.GUARD -> 5;
            case SubNet.BF_RING -> 2;
            case SubNet.ROPE -> 6;
            case SubNet.SIMPLE_DOMAIN -> 10;
            default -> 8;
        };
    }

    private static void prune(double now) {
        SHOTS.removeIf(f -> now - f.born > life(f));
        Iterator<Fx> it = TRACKED.values().iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            if (f.endAt < 0 && stream(f.kind) && now - f.updated > 4) {
                f.endAt = f.updated;
            }
            if (f.endAt < 0 && timed(f.kind) && now - f.born > f.a) {
                f.endAt = now;
            }
            if (f.endAt >= 0 && now - f.endAt > fadeOut(f)) {
                it.remove();
            }
        }
    }

    static void draw(double now, float partial) {
        boolean glow = DbFx.pass;
        if (glow) {
            prune(now);
            recordFists(now, partial);
        }
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born);
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case SubNet.DIVERGENT -> divergent(f, age, partial);
                case SubNet.INK_BURST -> {
                    float[] col = f.b == 1 ? GOLD : CE_INK;
                    ceInkBurst(rel(f.pos), age, f.a, f.id + 3, vec(f.dir), col);
                    DbFx.nrHit(rel(f.pos), age, col, f.a, f.id + 5, 12);
                }
                case SubNet.ARC -> arc(f, age);
                case SubNet.CRACKS -> {
                    Vector3f c = rel(f.pos);
                    crackRing(c, age, f.a, 5 + (int) f.b, Math.max(5, (int) f.b), 0.9f * (1 - smooth((age - 46) / 22)));
                    if (f.c == 1) {
                        debris(add(c, 0, 0.2f, 0), age, 7 + (int) f.b, 14, f.a / 2.4f, v(0, 0, 0), ROCK);
                        DbFx.nrDust(c, age, 31 + (int) f.b, 12, f.a / 2.2f, DUST, 44);
                    }
                }
                case SubNet.BLOOD_BEAM -> bloodBeam(f, age);
                case SubNet.ORB_POP -> {
                    bloodSplash(rel(f.pos), age, 5, 10, 0.8f, v(0, 0.3f, 1));
                    DbFx.nrHit(rel(f.pos), age, WHITE, 0.5f, 4, 8);
                }
                case SubNet.SUPERNOVA_BURST -> supernovaBurst(f, age);
                case SubNet.BLOOD_SPLASH -> {
                    bloodSplash(rel(f.pos), age, 11, 16, f.a, vec(f.dir).normalize());
                    DbFx.nrHit(rel(f.pos), age, RED, 0.6f * f.a, 3, 8);
                }
                case SubNet.RATIO_CRIT -> ratioCrit(f, age);
                case SubNet.SLAM -> slam(f, age);
                case SubNet.RESONANCE -> resonance(rel(f.pos), age, f.a, 3, safe(vec(f.dir)));
                case SubNet.RESO_LINE -> {
                    float k = 1 - age / 8;
                    if (k > 0) {
                        Vector3f p = rel(f.pos);
                        rib(p, at(p, vec(f.dir), 1), 0.03f, FxDraw.BEAM, 0.9f, CE, 0.35f * k, true);
                    }
                }
                case SubNet.CLAP -> clapFx(rel(f.pos), age, f.a == 1);
                case SubNet.SWAP -> swapFlash(rel(f.pos), age);
                case SubNet.VOICE -> speech(rel(f.pos), at(rel(f.pos), vec(f.dir), 1), age, f.b == 1 ? LILAC : VIOLET, f.a, f.c > 0 ? f.c : 10);
                case SubNet.CRUSH -> crush(f, age);
                case SubNet.MIWA_LINE -> miwaLine(rel(f.pos), vec(f.dir), f.a, age, 6, f.b > 0 ? f.b : 0.06f);
                case SubNet.MIWA_ARC -> miwaArc(f, age);
                case SubNet.DASH_LINE -> dashLine(f, age);
                case SubNet.DOMAIN_POP -> {
                    Vector3f c = rel(f.pos);
                    for (int i = 0; i < 12; i++) {
                        float ang = i / 12f * Mth.TWO_PI;
                        bb(add(c, Mth.cos(ang) * f.a, 0.1f + age * 0.05f, Mth.sin(ang) * f.a), 0.15f, 0, FxDraw.SOFT, 0.8f, MIWA, 1 - age / 14, true);
                    }
                }
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born);
            float out = f.endAt < 0 ? 1 : 1 - smooth((float) (now - f.endAt) / fadeOut(f));
            if (out <= 0) {
                continue;
            }
            switch (f.kind) {
                case SubNet.DIV_AURA -> divAuraOf(f, age, partial, out);
                case SubNet.BF_RING -> bfRing(f, age, out);
                case SubNet.GUARD -> guard(f, partial, out);
                case SubNet.DASH_TRAIL -> dashTrail(f, partial, out);
                case SubNet.BLOOD_BALL -> bloodBallFx(f, age, out);
                case SubNet.SUPERNOVA -> supernova(f, age, out);
                case SubNet.ROPE -> rope(f, now, age, out);
                case SubNet.SCALE -> scale(f, age, partial, out);
                case SubNet.RATIO -> ratio(f, age, partial, out);
                case SubNet.GROUND_MARK -> groundMark(f, age, partial, out);
                case SubNet.GOLD_AURA -> goldAura(f, age, partial, out);
                case SubNet.NAIL -> nailFx(f, age, out);
                case SubNet.DOLL -> doll(f, now, partial, out);
                case SubNet.SEAL_MARKS -> sealMarks(f, age, partial, out);
                case SubNet.HOLD_RINGS -> holdRings(f, age, partial, out);
                case SubNet.COUGH -> cough(f, age, partial, out);
                case SubNet.SLEEP -> sleep(f, age, partial, out);
                case SubNet.SIMPLE_DOMAIN -> simpleDomain(f, age, out);
                default -> {
                }
            }
        }
    }

    // ---------- small helpers ----------

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static Vector3f add(Vector3f p, float x, float y, float z) {
        return new Vector3f(p).add(x, y, z);
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    private static Vector3f vec(Vec3 d) {
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }

    private static Vector3f safe(Vector3f d) {
        return d.lengthSquared() < 1.0E-6f ? v(0, 0, 1) : new Vector3f(d).normalize();
    }

    private static float clamp(float x, float lo, float hi) {
        return Mth.clamp(x, lo, hi);
    }

    // Two unit vectors square to n, and n itself.
    private static Vector3f[] basis(Vector3f n) {
        Vector3f d = safe(n);
        Vector3f u = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? v(1, 0, 0) : v(0, 1, 0)).normalize();
        Vector3f w = new Vector3f(d).cross(u).normalize();
        return new Vector3f[] {u, w, d};
    }

    private static LivingEntity living(int id) {
        Entity e = FxAnchor.entity(id);
        return e instanceof LivingEntity le ? le : null;
    }

    // The body of an entity this frame: feet, forward and right on the ground, and its size (1 for a player).
    private record Body(Vec3 feet, Vec3 fwd, Vec3 right, double s) {
        Vec3 point(double f, double r, double y) {
            return feet.add(fwd.scale(f * s)).add(right.scale(r * s)).add(0, y * s, 0);
        }
    }

    private static Body body(LivingEntity e, float partial) {
        float yaw = Mth.lerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return new Body(e.getPosition(partial), fwd, right, e.getBbHeight() / 1.8);
    }

    // The fists held up in front (a guard, a punch): right (side 1) or left (side -1).
    private static Vec3 fist(Body b, int side) {
        return b.point(0.5, -0.3 * side, 1.28);
    }

    private static Vec3 shoulder(Body b, int side) {
        return b.point(0, -0.37 * side, 1.4);
    }

    private static Vector3f[] eyesOf(LivingEntity e, float partial) {
        Vec3 look = e.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = flat.cross(new Vec3(0, 1, 0));
        Vec3 eye = e.getEyePosition(partial).add(look.scale(0.27)).add(0, -0.02, 0);
        float s = e.getBbHeight() / 1.8f;
        return new Vector3f[] {rel(eye.add(right.scale(-0.1 * s))), rel(eye.add(right.scale(0.1 * s)))};
    }

    private static Vector3f mouthOf(LivingEntity e, float partial) {
        Vec3 look = e.getViewVector(partial);
        return rel(e.getEyePosition(partial).add(look.scale(0.28)).add(0, -0.17 * e.getBbHeight() / 1.8, 0));
    }

    // From the camera toward q (the Visualizador's camLook).
    private static Vector3f look(Vector3f q) {
        return safe(q);
    }

    // ---------- Itadori: cursed energy in ink, and the Divergent Fist's aura ----------

    // A flame tongue in flat ink (shape CEINK on a ribbon).
    static void ceTongue(Vector3f from, Vector3f dir, float len, float w, int seed, float k, float[] col) {
        if (k <= 0.02f || len <= 0.02f) {
            return;
        }
        rib(from, at(from, dir, len * k), w * (0.6f + 0.4f * k), DbFx.CEINK, hash(seed, 1, 77) * 0.499f, col, 1, false);
    }

    // The Visualizador's ceArm: tongues off the fist and up the forearm, a blob on the fist, white sparks.
    static void ceArm(Vector3f fist, Vector3f sh, float t, float k, int seed, float s, float[] col) {
        if (k <= 0.02f) {
            return;
        }
        Vector3f back = safe(new Vector3f(sh).sub(fist));
        bb(fist, 1.3f * s, 0, FxDraw.SOFT, 0.1f, col, 0.22f * k, true);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                bb(fist, 0.62f * s * (0.6f + 0.4f * k), t * 0.02f, DbFx.CEINK, 0.5f + hash(seed, 9, 77) * 0.499f, col, 1, false);
            }
            for (int i = pass == 0 ? 0 : 9; i < (pass == 0 ? 9 : 13); i++) {
                boolean fore = i >= 9;
                float ph = DbFx.fract(t * (0.05f + 0.03f * hash(seed, i, 2)) + hash(seed, i, 3));
                float life = Mth.sin(ph * Mth.PI);
                Vector3f p = fore ? lerp(fist, sh, 0.12f + 0.1f * (i - 9)) : new Vector3f(fist);
                p.add(sphere(seed, i).mul(0.06f * s)).add(0, ph * 0.12f * s, 0);
                Vector3f d = new Vector3f(0, 1, 0).add(new Vector3f(back).mul(fore ? 0.9f : 0.55f)).add(sphere(seed + 3, i).mul(0.55f)).normalize();
                ceTongue(p, d, (fore ? 0.5f : 0.6f + 0.45f * hash(seed, i, 4)) * s, (fore ? 0.32f : 0.42f) * s, seed * 31 + i, k * (0.35f + 0.65f * life),
                        col);
            }
        }
        if (k > 0.3f) {
            for (int i = 0; i < 4; i++) {
                float ph = DbFx.fract(t * 0.06f + hash(seed, i, 8));
                int cyc = (int) Math.floor(t * 0.06f + hash(seed, i, 8));
                Vector3f p = at(fist, sphere(seed + 9, i + cyc * 5), (0.18f + 0.4f * ph) * s);
                bb(p, 0.12f * s * Mth.sin(ph * Mth.PI), ph * 2, FxDraw.FLARE, 4 / 32f, CE_W, 0.9f * k, true);
            }
        }
    }

    // Burst of cursed energy at the blow: ink tongues opening out and fading, a blob in the middle, sparks.
    static void ceInkBurst(Vector3f at, float age, float s, int seed, Vector3f dir, float[] col) {
        if (age < 0 || age > 18) {
            return;
        }
        float g = easeOut(Math.min(1, age / 5));
        float f = age < 8 ? 1 : 1 - smooth((age - 8) / 10);
        bb(at, 3 * s * g, 0, FxDraw.SOFT, 0.15f, col, 0.35f * f, true);
        Vector3f dd = safe(dir);
        for (int i = 0; i < 12; i++) {
            Vector3f d = sphere(seed, i).add(new Vector3f(dd).mul(0.7f)).add(0, 0.25f, 0).normalize();
            ceTongue(at(at, d, 0.1f * s), d, (0.9f + 0.8f * hash(seed, i, 5)) * s * g, 0.6f * s, seed * 17 + i, f, col);
        }
        if (age < 10) {
            bb(at, 0.9f * s * (0.5f + g) * (1 - age / 14), age * 0.05f, DbFx.CEINK, 0.5f + hash(seed, 2, 77) * 0.499f, col, 1, false);
        }
        for (int i = 0; i < 10; i++) {
            Vector3f p = at(at, sphere(seed + 4, i), (0.4f + 2.2f * hash(seed, i, 6)) * s * g);
            bb(p, 0.16f * s * f, age * 0.3f, FxDraw.FLARE, 4 / 32f, CE_W, f, true);
        }
    }

    // The Divergent Fist's aura (approved by Stark, v56): a flat half-opaque blot of color (shape BRUSH) right behind the
    // fist, black brush outline in parts, one tip on top, and black ink hairs leaking off the tip with a pinch of cyan.
    static void divAura(Vector3f q, Vector3f d, float t, float s, float op, int seed) {
        if (op <= 0.02f) {
            return;
        }
        s /= 2.5f;
        Vector3f lk = look(q);
        Vector3f left = FxDraw.left();
        Vector3f up = FxDraw.up();
        Vector3f p = at(at(q, lk, 0.08f * s), d, -0.04f * s);
        bb(at(p, up, 0.3f * s), 2.1f * s, 0, DbFx.BRUSH, DbFx.fract(seed * 0.137f), DIV_CYAN, op, false);
        Vector3f top = at(at(p, up, 0.85f * s), lk, -0.03f * s);
        for (int i = 0; i < 6; i++) {
            float ph = DbFx.fract(t * 0.045f + hash(seed, i, 31));
            float x0 = (hash(seed, i, 32) - 0.5f) * 0.35f * s;
            float len = (0.35f + 0.35f * hash(seed, i, 33)) * s;
            float fade = Mth.sin(ph * Mth.PI);
            Vector3f last = null;
            for (int j = 0; j <= 7; j++) {
                float u = j / 7f;
                float y = (ph * 0.5f + u * 0.5f) * len * 1.6f - 0.12f * s;
                float w = Mth.sin(u * 3 + t * 0.25f + i * 1.7f) * 0.06f * s * u;
                Vector3f pt = at(at(top, left, x0 * (1 - 0.4f * u) + w), up, y);
                if (last != null) {
                    float wd = (0.07f * (1 - u) + 0.012f) * s * fade;
                    rib(last, pt, wd, FxDraw.SOLID, 0, INK, op, false);
                    if (i % 2 == 0 && j >= 2 && j <= 4) {
                        rib(last, pt, wd * 0.5f, FxDraw.SOLID, 0, DIV_CYAN, op, false);
                    }
                }
                last = pt;
            }
        }
    }

    // Every tick, where each aura's fists are (for the echoes).
    private static void recordFists(double now, float partial) {
        long tick = (long) Math.floor(now);
        if (tick == fistTick) {
            return;
        }
        fistTick = tick;
        FISTS.keySet().removeIf(id -> !TRACKED.containsKey(key(SubNet.DIV_AURA, id)));
        for (Fx f : TRACKED.values()) {
            if (f.kind != SubNet.DIV_AURA) {
                continue;
            }
            LivingEntity e = living(f.id);
            if (e == null) {
                continue;
            }
            Body b = body(e, partial);
            ArrayDeque<Vec3[]> h = FISTS.computeIfAbsent(f.id, k -> new ArrayDeque<>());
            h.addLast(new Vec3[] {fist(b, 1), fist(b, -1)});
            while (h.size() > 10) {
                h.removeFirst();
            }
        }
    }

    // The aura on the fists: it doesn't follow the fist, it lags in echoes (4 and 8 ticks behind) and only catches up an
    // instant later, with a snap.
    private static void divAuraOf(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Body b = body(e, partial);
        ArrayDeque<Vec3[]> h = FISTS.get(f.id);
        Vec3[][] hist = h == null ? new Vec3[0][] : h.toArray(new Vec3[0][]);
        float op = f.a * out;
        for (int side = 1; side >= -1; side -= 2) {
            if (side == -1 && f.b < 1) {
                break;
            }
            int k = side == 1 ? 0 : 1;
            Vector3f now = rel(fist(b, side));
            Vector3f d = safe(new Vector3f(now).sub(rel(shoulder(b, side))));
            float gap1 = 0;
            float gap2 = 0;
            if (hist.length >= 9) {
                Vector3f e1 = rel(hist[hist.length - 5][k]);
                Vector3f e2 = rel(hist[hist.length - 9][k]);
                gap1 = e1.distance(now);
                gap2 = e2.distance(now);
                divAura(e2, d, age, 1.1f, clamp(gap2 * 2, 0, 0.55f) * op, 7 + k);
                divAura(e1, d, age, 1.2f, clamp(gap1 * 2.5f, 0, 0.8f) * op, 5 + k);
            }
            boolean moving = gap2 > 0.05f;
            divAura(now, d, age, 1.3f, (moving ? 0.35f : 1) * op, 3 + k * 8);
        }
    }

    // The Divergent Fist: a dry white first impact, the energy late (waves up the arm, a mark swelling on the chest while
    // the color drains), then the second impact goes through the body and out of the back in a cone of ink with rings.
    private static void divergent(Fx f, float age, float partial) {
        float delay = f.a;
        float p1 = age;
        float a = age - delay;
        Vector3f c = rel(f.pos);
        Vector3f dir = safe(vec(f.dir));
        Vector3f[] bs = basis(dir);
        if (p1 >= 0 && p1 < 8) {
            float k = p1 / 8;
            bb(c, 1.4f * (1 - k * 0.5f), 0.3f, FxDraw.FLARE, 6 / 32f, WHITE, 1 - k, true);
            dsk(c, dir, (0.25f + 0.7f * easeOut(k)) * 2, FxDraw.RING, 0.12f, WHITE, 1 - k, false);
            for (int i = 0; i < 10; i++) {
                float ang = i / 10f * Mth.TWO_PI + hash(31, i, 1);
                Vector3f d = at(new Vector3f(bs[0]).mul(Mth.cos(ang)), bs[1], Mth.sin(ang));
                Vector3f p = at(c, d, 0.25f + 0.8f * easeOut(k));
                rib(at(p, d, -0.3f), p, 0.025f, FxDraw.SOLID, 0, WHITE, 1 - k, false);
            }
        }
        if (p1 >= 0 && a < 2) {
            LivingEntity e = living(f.id);
            if (e != null) {
                Body b = body(e, partial);
                Vector3f fistP = rel(b.point(0.75, -0.3, 1.32));
                Vector3f sh = rel(shoulder(b, 1));
                Vector3f adir = safe(new Vector3f(fistP).sub(sh));
                for (int k = 0; k < 3; k++) {
                    float u = clamp((p1 - k * 3.5f) / 7, 0, 1);
                    if (u <= 0 || u >= 1) {
                        continue;
                    }
                    Vector3f p = lerp(sh, fistP, u);
                    float sn = Mth.sin(u * Mth.PI);
                    dsk(p, adir, (0.16f + 0.1f * sn) * 2, FxDraw.RING, 0.12f, CE_INK, sn, true);
                    dsk(p, adir, (0.12f + 0.08f * sn) * 2, FxDraw.RING, 0.08f, CE_W, sn * 0.8f, false);
                    ceTongue(p, safe(new Vector3f(adir).negate().add(0, 0.5f, 0)), 0.5f, 0.35f, 70 + k + (int) Math.floor(age / 2), sn, CE_INK);
                }
                warp(lerp(sh, fistP, 0.6f), 0.6f, 0.5f, 0, p1, null);
            }
            float sw = smooth(p1 / delay);
            float pulse = 1 + 0.12f * Mth.sin(age * 1.8f);
            bb(at(c, dir, 0.05f), 0.25f + 0.75f * sw * pulse, age * 0.02f, DbFx.CEINK, 0.83f, CE_INK, 1, false);
            bb(c, 1.8f * sw, 0, FxDraw.SOFT, 0.2f, CE_INK, 0.45f * sw, true);
            for (int i = 0; i < 8; i++) {
                Vector3f d = sphere(41 + (int) Math.floor(age / 2), i);
                float along = d.dot(dir);
                d.sub(new Vector3f(dir).mul(along)).add(new Vector3f(dir).mul(Math.abs(along) * 0.3f)).normalize();
                ceTongue(at(c, d, 0.15f), d, 0.4f + 0.5f * sw, 0.3f, 80 + i + (int) Math.floor(age / 2), sw * 0.8f, CE_INK);
            }
            warp(c, 1.2f + sw, 0.9f * sw, 2, p1, null);
            if (DbFx.pass && a < 0) {
                Minecraft mc = Minecraft.getInstance();
                float near = mc.player == null ? 0 : 1 - smooth((float) (mc.player.position().distanceTo(f.pos) - 20) / 20);
                ScreenFx.sustainGrade(10, smooth((p1 - 1) / 4) * 0.7f * near, 0x0A3A5A, f.pos);
            }
        }
        if (a >= 0) {
            float g = easeOut(Math.min(1, a / 5));
            float fo = a < 8 ? 1 : 1 - smooth((a - 8) / 16);
            Vector3f back = at(c, dir, 0.35f);
            if (a < 6) {
                bb(c, 7 * (1 - a / 7), 0, FxDraw.SOFT, 0.4f, CE_W, 1 - a / 6, true);
                bb(c, 4, 0.2f, FxDraw.FLARE, 12 / 32f, CE_INK, 1 - a / 6, true);
            }
            int tk = (int) Math.floor(a / 2);
            if (fo > 0.02f) {
                for (int i = 0; i < 20; i++) {
                    Vector3f d = sphere(91, i).mul(0.55f).add(new Vector3f(dir)).add(0, 0.1f, 0).normalize();
                    ceTongue(at(back, d, 0.1f), d, (1.6f + 2.4f * hash(91, i, 5)) * g, 0.9f + 0.5f * hash(91, i, 6), 300 + i * 7 + tk, fo, CE_INK);
                }
                for (int i = 0; i < 7; i++) {
                    Vector3f d = sphere(93, i).mul(0.8f).add(new Vector3f(dir).mul(-0.6f)).add(0, 0.2f, 0).normalize();
                    ceTongue(at(c, d, 0.1f), d, (0.6f + 0.6f * hash(93, i, 5)) * g, 0.5f, 400 + i * 5 + tk, fo * 0.8f, CE_INK);
                }
            }
            if (a < 9) {
                bb(c, (1.2f + 1.4f * g) * (1 - a / 12), a * 0.04f, DbFx.CEINK, 0.71f, CE_INK, 1, false);
            }
            for (int r = 0; r < 5; r++) {
                float ar = a - r * 1.5f;
                if (ar < 0 || ar > 18) {
                    continue;
                }
                float k = ar / 18;
                Vector3f p = at(back, dir, 0.3f + r * 0.9f + ar * 0.28f);
                float rad = (0.5f + 0.35f * r) * (0.6f + 1.2f * easeOut(k));
                dsk(p, dir, rad * 2, FxDraw.RING, 0.07f, CE_INK, (1 - k) * 0.95f, true);
                dsk(p, dir, (rad - 0.02f) * 2, FxDraw.RING, 0.05f, BLACK, (1 - k) * 0.85f, false);
            }
            if (a < 14) {
                dsk(c, new Vector3f(c).negate(), (0.6f + 3.2f * easeOut(a / 14)) * 2, FxDraw.RING, 0.06f, CE_W, 1 - a / 14, true);
            }
            if (a < 16) {
                Vector3f gr = add(c, 0, -f.c + 0.05f, 0);
                dsk(at(gr, dir, 0.3f), UP, (0.6f + 4.5f * easeOut(a / 16)) * 2, FxDraw.RING, 0.08f, CE_INK, 0.8f * (1 - a / 16), true);
                warp(c, 3.5f, 1.3f * (1 - a / 16), 0, a, null);
            }
            if (a <= 14) {
                for (int i = 0; i < 14; i++) {
                    Vector3f d = sphere(97, i).mul(0.7f).add(new Vector3f(dir)).add(0, 0.15f, 0).normalize();
                    Vector3f p = at(back, d, 0.6f + 5 * easeOut(a / 14));
                    rib(at(p, d, -1.2f * (1 - a / 14)), p, 0.035f, FxDraw.STREAK, 0.9f, CE_W, 1 - a / 14, true);
                }
            }
            DbFx.nrDust(add(at(c, dir, 1.6f), 0, -f.c, 0), a - 3, 27, 14, 1.5f, new float[] {0.4f, 0.42f, 0.46f}, 40);
        }
    }

    // The Black Flash's ring: it closes round the fist; press when it touches. The fist crackles with black lightning.
    private static void bfRing(Fx f, float age, float out) {
        Vector3f p = rel(f.pos);
        float ring = f.a;
        Vector3f n = new Vector3f(p).negate();
        dsk(p, n, Mth.lerp(ring, 2.6f, 0.5f), FxDraw.RING, 0.05f, WHITE, (0.4f + 0.6f * ring) * out, true);
        dsk(p, n, 0.5f, FxDraw.RING, 0.03f, RED, 0.5f * out, true);
        float k = 0.55f * ring * ring * out;
        if (k > 0.01f) {
            int ep = (int) Math.floor(age / 2);
            int count = 2 + Math.round(7 * k);
            for (int i = 0; i < count; i++) {
                int s = ep * 37 + i * 11;
                bfBolt(p, at(p, sphere(s, i), 0.25f + 0.55f * k * hash(s, i, 2)), s, 0.018f + 0.02f * k, k);
            }
            bb(p, 0.8f + 1.6f * k, age * 0.1f, FxDraw.SOFT, 0.3f, RED, 0.55f * k, true);
        }
    }

    private static Vector3f[] boltPts(Vector3f a, Vector3f b, int seed, float jag, int segs) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        Vector3f[] bs = basis(d);
        Vector3f[] pts = new Vector3f[segs + 1];
        pts[0] = new Vector3f(a);
        float amp = jag * len * 0.3f;
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            float m = i == segs ? 0 : amp * (float) Math.sqrt(Mth.sin(t * Mth.PI));
            pts[i] = at(at(at(a, d, t), bs[0], (hash(seed, i, 1) - 0.5f) * 2 * m), bs[1], (hash(seed, i, 2) - 0.5f) * 2 * m);
        }
        return pts;
    }

    // Black lightning with a red edge.
    private static void bfBolt(Vector3f a, Vector3f b, int seed, float w, float op) {
        Vector3f[] pts = boltPts(a, b, seed, 0.45f, 6);
        for (int i = 1; i < pts.length; i++) {
            rib(pts[i - 1], pts[i], w * 3.2f, FxDraw.BEAM, 0.15f, RED, op * 0.75f, true);
            rib(pts[i - 1], pts[i], w, FxDraw.BEAM, 0, BLACK, op, false);
        }
    }

    // The Manji Kick's guard: a thin ring in front of him.
    private static void guard(Fx f, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Body b = body(e, partial);
        Vector3f c = rel(b.point(0.5, 0, 1.2));
        dsk(c, vec(b.fwd), 1.3f, FxDraw.RING, 0.04f, CE, 0.35f * f.a * out, true);
    }

    // A swing's arc: ribbons growing along a circle round pos (horizontal, or vertical for c = 1).
    private static void arc(Fx f, float age) {
        float a = Math.min(1, (age + 1) / 4);
        float fade = age < 4 ? 1 : 1 - (age - 4) / 4;
        if (fade <= 0) {
            return;
        }
        Vector3f c = rel(f.pos);
        Vector3f fwd = safe(vec(f.dir));
        Vector3f side = f.c == 1 ? v(0, 1, 0) : safe(new Vector3f(fwd).cross(UP));
        float r = f.a;
        float[] col = DbFx.colorOf((int) f.b);
        for (int i = 0; i < 8; i++) {
            float u0 = i / 8f;
            float u1 = (i + 1) / 8f;
            float b0 = -1.6f + u0 * 2.2f * a;
            float b1 = -1.6f + u1 * 2.2f * a;
            Vector3f p0 = add(at(at(c, side, Mth.sin(b0) * r), fwd, Mth.cos(b0) * r - r * 0.6f), 0, u0 * 0.3f * (f.c == 1 ? 0 : 1), 0);
            Vector3f p1 = add(at(at(c, side, Mth.sin(b1) * r), fwd, Mth.cos(b1) * r - r * 0.6f), 0, u1 * 0.3f * (f.c == 1 ? 0 : 1), 0);
            rib(p0, p1, 0.2f * u1 * r, FxDraw.STREAK, 0.6f, col, 0.8f * u1 * fade, true);
        }
    }

    // Speed lines behind someone dashing.
    private static void dashTrail(Fx f, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Vec3 mv = e.getDeltaMovement();
        Vector3f back = mv.horizontalDistanceSqr() > 1.0E-3 ? safe(vec(new Vec3(-mv.x, 0, -mv.z))) : vec(body(e, partial).fwd).negate();
        Vector3f side = safe(new Vector3f(back).cross(UP));
        Vector3f feet = rel(e.getPosition(partial));
        float[] col = f.a == 1 ? RED : f.a == 2 ? AIR : CE_INK;
        for (int i = 0; i < 6; i++) {
            Vector3f p = add(at(feet, side, (hash(i, 3, 1) - 0.5f) * 0.8f), 0, 0.4f + hash(i, 3, 2) * 1.4f, 0);
            p = at(p, back, 0.5f);
            rib(p, at(p, back, 2), 0.12f, FxDraw.STREAK, 0.6f, col, 0.6f * out, true);
        }
    }

    // Cracks running out on the ground.
    static void crackRing(Vector3f c, float age, float r, int seed, int n, float op) {
        if (age < 0 || op <= 0) {
            return;
        }
        float g = easeOut(Math.min(1, age / 8));
        for (int i = 0; i < n; i++) {
            float a0 = i / (float) n * Mth.TWO_PI + hash(seed, i, 1) * 0.5f;
            float len = r * (0.6f + 0.4f * hash(seed, i, 2)) * g;
            Vector3f last = add(c, 0, 0.03f, 0);
            for (int j = 1; j <= 4; j++) {
                float rr = len * j / 4;
                float a = a0 + (hash(seed, i * 7 + j, 3) - 0.5f) * 0.5f;
                Vector3f p = add(c, Mth.cos(a) * rr, 0.03f, Mth.sin(a) * rr);
                rib(last, p, 0.07f * (1 - j / 6f), FxDraw.BEAM, 0.9f, CRACK, op, false);
                last = p;
            }
        }
    }

    // Bits of rock thrown up and falling back.
    static void debris(Vector3f base, float age, int seed, int n, float s, Vector3f dir, float[] col) {
        if (age < 0 || age > 50) {
            return;
        }
        float ground = base.y - 0.15f;
        for (int i = 0; i < n; i++) {
            float vx = (hash(seed, i, 1) - 0.5f) * 0.12f + dir.x * 0.1f * hash(seed, i, 4);
            float vy = 0.08f + hash(seed, i, 2) * 0.12f;
            float vz = (hash(seed, i, 3) - 0.5f) * 0.12f + dir.z * 0.12f * hash(seed, i, 4);
            Vector3f p = add(base, vx * age, vy * age - 0.006f * age * age, vz * age);
            if (p.y < ground) {
                p.y = ground;
            }
            bb(p, (0.14f + 0.24f * hash(seed, i, 5)) * s, age * 0.15f * hash(seed, i, 6) + i, FxDraw.SOLID, 0, i % 3 != 0 ? col : ROCK_D,
                    age > 40 ? 1 - (age - 40) / 10 : 1, false);
        }
    }

    // ---------- Choso: blood ----------

    static void bloodBall(Vector3f at, float r, float t, float a, int seed) {
        if (a <= 0) {
            return;
        }
        bb(at, r * 3.4f, 0, FxDraw.SOFT, 0.1f, RED, 0.25f * a, true);
        bb(at, r * 2.1f, t * 0.05f, FxDraw.SOFT, 0.95f, BLOOD, a, false);
        bb(at, r * 1.4f, 0, FxDraw.SOFT, 0.95f, BLOOD_D, 0.6f * a, false);
        bb(add(at(at, FxDraw.left(), r * 0.3f), 0, r * 0.35f, 0), r * 0.6f, 0, FxDraw.SOFT, 0.6f, BLOOD_HI, 0.55f * a, true);
        for (int i = 0; i < 5; i++) {
            Vector3f ax = sphere(seed + 40, i);
            Vector3f u = safe(new Vector3f(ax).cross(0, 1, 0.3f));
            Vector3f w = new Vector3f(ax).cross(u);
            float th = t * 0.3f + i;
            rib(at(at(at, u, Mth.cos(th) * r * 1.05f), w, Mth.sin(th) * r * 1.05f),
                    at(at(at, u, Mth.cos(th + 0.8f) * r * 1.05f), w, Mth.sin(th + 0.8f) * r * 1.05f), r * 0.16f, FxDraw.STREAK, 0.6f, BLOOD, 0.9f * a, false);
        }
    }

    // A thread of blood that looks liquid: dark edge, strong red core, a thin light gloss (no neon glow) and a very faint
    // red halo so it shows in the dark.
    static void bloodLine(Vector3f a, Vector3f b, float w, float op, float halo) {
        rib(a, b, w * 1.25f, FxDraw.BEAM, 0.9f, BLOOD_D, op, false);
        rib(a, b, w, FxDraw.BEAM, 0.9f, BLOOD_R, op, false);
        rib(a, b, w * 0.28f, FxDraw.BEAM, 0.9f, GLOSS, 0.85f * op, false);
        if (halo > 0) {
            rib(a, b, w * 3, FxDraw.BEAM, 0.05f, RED, halo * op, true);
        }
    }

    static void bloodDrips(List<Vector3f> pts, float t, int seed, float op, float ground) {
        for (int i = 0; i < 10; i++) {
            Vector3f p0 = pts.get(Math.min(pts.size() - 1, (int) Math.floor(hash(seed, i, 1) * pts.size())));
            float ph = DbFx.fract(t * 0.05f + hash(seed, i, 2));
            Vector3f p = add(p0, 0, -ph * ph * 1.6f, 0);
            if (p.y < ground) {
                continue;
            }
            bb(p, 0.05f + 0.03f * hash(seed, i, 3), 0, FxDraw.SOFT, 0.95f, BLOOD_R, op * (1 - ph * 0.5f), false);
        }
    }

    static void bloodSplash(Vector3f at, float age, int seed, int n, float s, Vector3f dir) {
        if (age < 0 || age > 26) {
            return;
        }
        for (int i = 0; i < n; i++) {
            Vector3f d = sphere(seed, i).add(new Vector3f(dir).mul(0.8f)).normalize();
            float sp = (0.05f + 0.08f * hash(seed, i, 1)) * s;
            Vector3f p = add(at(at, d, sp * age), 0, -0.005f * age * age, 0);
            bb(p, (0.07f + 0.08f * hash(seed, i, 2)) * s, 0, FxDraw.SOFT, 0.9f, BLOOD, 1 - age / 26, false);
        }
    }

    private static void bloodBallFx(Fx f, float age, float out) {
        Vector3f hm = rel(f.pos);
        float k = f.b;
        bloodBall(hm, f.a, age * (1 + 3 * k), smooth(age / 6) * out, 3);
        Vector3f n = safe(vec(f.dir));
        for (int i = 0; i < 3; i++) {
            float ph = DbFx.fract(age * 0.04f * (1 + 2 * k) + i / 3f);
            dsk(hm, n, (0.9f - ph * 0.7f) * 2, FxDraw.RING, 0.05f, RED, 0.4f * k * out, true);
        }
    }

    // The Piercing Blood: too fast to see travelling, a thin line that fades, droplets falling off it.
    private static void bloodBeam(Fx f, float age) {
        if (age >= 14) {
            return;
        }
        float fo = 1 - age / 14;
        Vector3f from = rel(f.pos);
        Vector3f to = at(from, vec(f.dir), 1);
        bloodLine(from, to, 0.16f * fo + 0.03f, fo, 0.14f);
        rib(from, to, 0.05f * fo, FxDraw.BEAM, 0.9f, RED, 0.6f * fo, true);
        for (int i = 0; i < 18; i++) {
            float u = hash(i, 4, 1);
            Vector3f p = add(lerp(from, to, u * 0.6f), 0, -0.01f * age * age * hash(i, 4, 2), 0);
            bb(p, 0.07f, 0, FxDraw.SOFT, 0.9f, BLOOD, fo, false);
        }
    }

    private static Vector3f orb(Vector3f c, int i, float phase, float grow, float age) {
        float a = phase + i / 5f * Mth.TWO_PI;
        float r = 1.3f * grow;
        return add(c, Mth.cos(a) * r, 0.15f * Mth.sin(age * 0.1f + i), Mth.sin(a) * r);
    }

    private static void supernova(Fx f, float age, float out) {
        Vector3f c = rel(f.pos);
        int mask = (int) f.a;
        for (int i = 0; i < 5; i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }
            float grow = smooth((age - i * 2) / 8);
            bloodBall(orb(c, i, f.b, grow, age), 0.3f, age, smooth((age - i * 2) / 5) * out, i * 3);
        }
    }

    private static void supernovaBurst(Fx f, float a) {
        Vector3f c = rel(f.pos);
        int mask = (int) f.a;
        for (int i = 0; i < 5; i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }
            Vector3f o = orb(c, i, f.b, 1, 0);
            if (a < 6) {
                bb(o, 1 - a / 6, 0, FxDraw.SOFT, 0.5f, RED, 1 - a / 6, true);
            }
            if (a < 14) {
                for (int j = 0; j < 12; j++) {
                    Vector3f d = sphere(i * 13 + 3, j);
                    d.y *= 0.5f;
                    d.normalize();
                    Vector3f p = at(o, d, Math.min(4.5f, a * 0.5f));
                    rib(at(p, d, -0.8f), p, 0.09f, FxDraw.STREAK, 0.9f, BLOOD, 1 - a / 14, false);
                    rib(at(p, d, -0.4f), p, 0.03f, FxDraw.STREAK, 0.9f, BLOOD_HI, 1 - a / 14, true);
                }
            }
        }
    }

    // The blood rope: flying (a = 0) with a sag, or wrapped round the bound one (a = 1), coiling up the body.
    private static void rope(Fx f, double now, float age, float out) {
        Vector3f hand = rel(f.pos);
        Vector3f end = at(hand, vec(f.dir), 1);
        boolean wrapped = f.a >= 1;
        int n = 30;
        List<Vector3f> pts = new ArrayList<>();
        Vector3f last = new Vector3f(hand);
        Vector3f body = add(end, 0, f.b * 0.55f, 0);
        for (int i = 1; i <= n; i++) {
            float u = i / (float) n;
            Vector3f p = lerp(hand, wrapped ? body : end, u);
            p.y += Mth.sin(u * Mth.PI) * (wrapped ? 0.05f : 0.25f);
            bloodLine(last, p, 0.1f, out, 0.08f);
            pts.add(p);
            last = p;
        }
        bloodDrips(pts, age, 7, out, end.y - 0.2f);
        if (wrapped) {
            float k = smooth((float) (now - f.changed) / 8);
            Vector3f prev = null;
            float h = Math.max(0.6f, f.b);
            for (int i = 0; i <= 40; i++) {
                float u = i / 40f;
                float th = u * 22 + age * 0.02f;
                Vector3f p = add(end, Mth.cos(th) * 0.42f * h / 1.8f, (0.45f + u * 1.15f * k) * h / 1.8f, Mth.sin(th) * 0.42f * h / 1.8f);
                if (prev != null) {
                    bloodLine(prev, p, 0.11f, out, 0.08f);
                }
                prev = p;
            }
        }
    }

    // Red Scale: the marks on the face lit, red steam off the body, red veins down the arms.
    private static void scale(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        float k = smooth(age / 10) * out;
        for (Vector3f eye : eyesOf(e, partial)) {
            bb(add(eye, 0, -0.1f, 0), 0.14f, 0, FxDraw.SOFT, 0.6f, RED, 0.7f * k, true);
        }
        Body b = body(e, partial);
        Vector3f feet = rel(b.feet);
        for (int i = 0; i < 10; i++) {
            float ph = DbFx.fract(age * 0.04f + hash(i, 6, 1));
            bb(add(feet, (hash(i, 6, 2) - 0.5f) * 0.7f, 0.4f + ph * 1.8f, (hash(i, 6, 3) - 0.5f) * 0.5f), 0.5f + ph * 0.5f, i + age * 0.01f, FxDraw.SMOKE,
                    0.4f, RED, 0.25f * k * (1 - ph), true);
        }
        for (int side = -1; side <= 1; side += 2) {
            Vector3f sh = rel(b.point(0, -0.37 * side, 1.38));
            Vector3f hand = rel(b.point(0.05, -0.4 * side, 0.75));
            for (int j = 0; j < 3; j++) {
                Vector3f off = vec(b.fwd).mul(0.13f).add(vec(b.right).mul((j - 1) * 0.05f));
                rib(new Vector3f(sh).add(off), new Vector3f(hand).add(off), 0.02f, FxDraw.BEAM, 0.9f, RED, 0.8f * k, true);
            }
        }
    }

    // ---------- Nanami ----------

    // The 7:3 lines over the target: the body cut in ten by gold lines, the 3/10 line wider, its point glowing.
    private static void ratio(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        float a = smooth(age / 4) * out;
        Vector3f feet = rel(e.getPosition(partial));
        Vector3f toCam = safe(v(-feet.x, 0, -feet.z));
        Vector3f side = safe(new Vector3f(toCam).cross(UP));
        float h = e.getBbHeight();
        Vector3f c = at(feet, toCam, e.getBbWidth() * 0.5f + 0.05f);
        rib(add(c, 0, 0.05f, 0), add(c, 0, h, 0), 0.025f, FxDraw.BEAM, 0.9f, GOLD_W, 0.8f * a, true);
        for (int i = 0; i <= 10; i++) {
            float y = Mth.lerp(i / 10f, 0.05f, h);
            float w = i == 3 ? 0.55f : 0.14f;
            rib(at(add(c, 0, y, 0), side, -w), at(add(c, 0, y, 0), side, w), i == 3 ? 0.04f : 0.018f, FxDraw.BEAM, 0.9f, i == 3 ? GOLD : GOLD_W,
                    (i == 3 ? 1 : 0.6f) * a, true);
        }
        Vector3f p = add(at(c, toCam, 0.02f), 0, Mth.lerp(0.3f, 0.05f, h), 0);
        bb(p, 0.5f + 0.1f * Mth.sin(age * 0.4f), 0, FxDraw.SOFT, 0.7f, GOLD, a, true);
        bb(p, 0.9f, age * 0.05f, FxDraw.FLARE, 4 / 32f, GOLD_W, 0.7f * a, true);
    }

    private static void ratioCrit(Fx f, float a) {
        Vector3f at = rel(f.pos);
        Vector3f dir = safe(vec(f.dir));
        DbFx.nrHit(at, a, GOLD, 1.6f, 9, 14);
        if (a < 14) {
            for (int i = 0; i < 10; i++) {
                Vector3f d = sphere(91, i);
                d.add(new Vector3f(dir).mul(Math.abs(d.dot(dir)) + 0.6f)).normalize();
                rib(at, at(at, d, 0.6f + a * 0.2f), 0.08f * (1 - a / 14), FxDraw.STREAK, 0.9f, GOLD_W, 1 - a / 14, true);
            }
            warp(at, 1.6f, 0.8f * (1 - a / 14), 0, a, null);
        }
    }

    // A small flame of energy (Nanami's gold).
    static void ceFlame(Vector3f at, float s, float a, float t, int seed, float[] col) {
        if (a <= 0) {
            return;
        }
        bb(at, 0.9f * s, 0, FxDraw.SOFT, 0.2f, col, 0.35f * a, true);
        for (int i = 0; i < 9; i++) {
            float ph = DbFx.fract(t * (0.07f + hash(i, 1, seed) * 0.05f) + hash(i, 2, seed));
            Vector3f p = add(at, (hash(i, 3, seed) - 0.5f) * 0.3f * s, ph * 0.55f * s, (hash(i, 4, seed) - 0.5f) * 0.3f * s);
            bb(p, (0.34f * (1 - ph) + 0.08f) * s, i + t * 0.02f, FxDraw.FIRE, 0.25f + ph * 0.5f, i % 3 != 0 ? col : GOLD_W, 0.75f * a * (1 - ph), true);
        }
    }

    // The 7:3 point on the ground where the fist will land, and the gold on his fist.
    private static void groundMark(Fx f, float age, float partial, float out) {
        Vector3f c = rel(f.pos);
        float k = f.a * out;
        for (int i = 0; i < 4; i++) {
            float ang = i * Mth.HALF_PI + 0.4f;
            float len = 1.4f * k;
            rib(add(c, 0, 0.03f, 0), add(c, Mth.cos(ang) * len, 0.03f, Mth.sin(ang) * len), 0.05f, FxDraw.BEAM, 0.9f, GOLD, 0.8f * k, true);
        }
        dsk(add(c, 0, 0.04f, 0), UP, 0.7f + 0.15f * Mth.sin(age * 0.5f), FxDraw.SOFT, 0.6f, GOLD, 0.8f * k, true);
        dsk(add(c, 0, 0.04f, 0), UP, 1.1f, FxDraw.RING, 0.05f, GOLD_W, 0.7f * k, true);
        LivingEntity e = living(f.id);
        if (e != null) {
            ceFlame(rel(body(e, partial).point(-0.1, -0.35, 1.15)), 0.9f, smooth(age / 6) * out, age, 4, GOLD);
        }
    }

    // The fist in the ground: flash, shockwave rings, a column and a ring of smoke running out, cracks, rubble and dust.
    private static void slam(Fx f, float a) {
        Vector3f c = rel(f.pos);
        Vector3f cc = add(c, 0, 0.2f, 0);
        float reach = f.a;
        if (a < 10) {
            bb(cc, 6 * easeOut(a / 6), 0, FxDraw.SOFT, 0.4f, GOLD_W, 1 - a / 10, true);
            bb(cc, 3.5f, a * 0.1f, FxDraw.FLARE, 12 / 32f, GOLD, 1 - a / 10, true);
            warp(cc, 5, 1.2f * (1 - a / 10), 2, a, null);
        }
        for (int r = 0; r < 2; r++) {
            float ar = a - r * 4;
            if (ar < 0 || ar > 22) {
                continue;
            }
            float rad = (2 + (reach - 2) * easeOut(ar / 22)) * (1 - r * 0.2f);
            dsk(add(c, 0, 0.06f, 0), UP, rad * 2, FxDraw.RING, 0.05f, GOLD_W, 0.9f * (1 - ar / 22), true);
            dsk(add(c, 0, 0.05f, 0), UP, rad * 2.05f, FxDraw.RING, 0.18f, RING_DIRT, 0.6f * (1 - ar / 22), false);
        }
        if (a < 60) {
            for (int i = 0; i < 16; i++) {
                float ang = i / 16f * Mth.TWO_PI + hash(i, 1, 81);
                float rr = (1.5f + (reach - 1) * easeOut(Math.min(1, a / 26))) * (0.8f + 0.3f * hash(i, 2, 81));
                Vector3f p = add(c, Mth.cos(ang) * rr, 0.5f + 0.6f * hash(i, 3, 81) + a * 0.01f, Mth.sin(ang) * rr);
                bb(p, (1.6f + 0.07f * a) * (0.8f + 0.4f * hash(i, 4, 81)), a * 0.02f + i, FxDraw.SMOKE, 0.3f, SMOKE_COL, 0.75f * (1 - smooth((a - 20) / 40)),
                        false);
            }
            for (int i = 0; i < 8; i++) {
                Vector3f p = add(c, (hash(i, 5, 81) - 0.5f) * 2, 0.8f + a * 0.06f * (0.5f + hash(i, 6, 81)) + i * 0.3f, (hash(i, 7, 81) - 0.5f) * 2);
                bb(p, (2 + 0.08f * a) * (0.8f + 0.4f * hash(i, 8, 81)), a * 0.015f + i, FxDraw.SMOKE, 0.3f, SMOKE_D, 0.7f * (1 - smooth((a - 16) / 44)), false);
            }
        }
        crackRing(c, a - 2, reach, 11, 14, 0.9f * (1 - smooth((a - 60) / 20)));
        debris(cc, a, 19, 26, 1.8f, v(0, 1, 0), ROCK);
        DbFx.nrDust(c, a - 4, 43, 18, 2.2f, SMOKE_COL, 50);
    }

    // Overtime: gold energy rising off him, on his fist, his eyes lit.
    private static void goldAura(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        float k = smooth(age / 10) * out;
        Body b = body(e, partial);
        ceFlame(rel(b.point(0, 0, 0.2)), 2.2f, 0.55f * k, age, 8, GOLD);
        ceFlame(rel(b.point(0.35, -0.35, 1.0)), 0.8f, k, age, 4, GOLD);
        for (Vector3f eye : eyesOf(e, partial)) {
            bb(eye, 0.12f, 0, FxDraw.SOFT, 0.8f, GOLD_W, 0.8f * k, true);
        }
    }

    // ---------- Nobara ----------

    static void nail(Vector3f at, Vector3f dir, float len, float a, float glowA) {
        if (a <= 0) {
            return;
        }
        Vector3f d = safe(dir);
        Vector3f b = at(at, d, -len);
        rib(b, at, 0.05f, FxDraw.SOLID, 0, STEEL, a, false);
        rib(b, at(b, d, 0.05f), 0.12f, FxDraw.SOLID, 0, STEEL_D, a, false);
        bb(at(at, d, -len * 0.4f), 0.55f, 0, FxDraw.SOFT, 0.5f, CE, glowA * a, true);
        bb(at, 0.22f, 0, FxDraw.SOFT, 0.9f, CE_W, glowA * a, true);
    }

    // Resonance: a spike of blue energy through the point, both ways along axis.
    static void resonance(Vector3f at, float age, float s, int seed, Vector3f axis) {
        if (age < 0 || age > 22) {
            return;
        }
        float k = age / 22;
        float fo = 1 - k;
        bb(at, 2.4f * s * (1 - k * 0.4f), 0.785f, FxDraw.FLARE, 6 / 32f, CE, fo, true);
        bb(at, 0.8f * s, 0, FxDraw.SOFT, 0.9f, CE_W, fo, true);
        for (int sg = -1; sg <= 1; sg += 2) {
            Vector3f tip = at(at, axis, sg * (0.4f + 1.6f * easeOut(Math.min(1, age / 4))) * s);
            rib(at, tip, 0.22f * s * fo, FxDraw.STREAK, 0.3f, CE, fo, true);
            rib(at, tip, 0.07f * s * fo, FxDraw.STREAK, 0.9f, CE_W, fo, true);
        }
        for (int i = 0; i < 8; i++) {
            Vector3f d = sphere(seed + 7, i);
            rib(at(at, d, 0.15f), at(at, d, (0.3f + 0.7f * easeOut(k)) * s), 0.03f, FxDraw.STREAK, 0.9f, CE_W, fo, true);
        }
    }

    private static void nailFx(Fx f, float age, float out) {
        Vector3f tip = rel(f.pos);
        Vector3f dir = safe(vec(f.dir));
        if (f.a < 1) {
            nail(tip, dir, 0.26f, out, 1);
            Vec3 first = f.trail.peekFirst();
            if (first != null) {
                rib(rel(first), tip, 0.1f, FxDraw.STREAK, 0.6f, CE, 0.7f * out, true);
            }
        } else {
            nail(tip, dir, 0.18f, out, 0.5f + 0.3f * Mth.sin(age * 0.3f + f.id));
        }
    }

    // The straw doll in her left hand, a nail on it (hammered in once a = 1).
    private static void doll(Fx f, double now, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Body b = body(e, partial);
        Vector3f c = rel(b.point(0.45, 0.22, 1.2));
        Vector3f fw = vec(b.fwd);
        Vector3f rt = vec(b.right);
        Vector3f up = v(0, 1, 0);
        float op = out;
        // Body, head, arms, legs and the two bindings (the Visualizador's DOLL, in blocks).
        doll(c, rt, up, fw, 0, 0, 0, 0.08f, 0.11f, 0.04f, STRAW, op);
        doll(c, rt, up, fw, 0, 0.17f, 0, 0.06f, 0.06f, 0.04f, STRAW, op);
        doll(c, rt, up, fw, 0, 0.06f, 0, 0.16f, 0.025f, 0.025f, STRAW, op);
        doll(c, rt, up, fw, 0.05f, -0.18f, 0, 0.025f, 0.08f, 0.025f, STRAW, op);
        doll(c, rt, up, fw, -0.05f, -0.18f, 0, 0.025f, 0.08f, 0.025f, STRAW, op);
        doll(c, rt, up, fw, 0, 0.1f, 0, 0.085f, 0.015f, 0.045f, STRAW_D, op);
        doll(c, rt, up, fw, 0, -0.06f, 0, 0.085f, 0.015f, 0.045f, STRAW_D, op);
        boolean in = f.a >= 1;
        Vector3f tip = at(add(c, 0, 0.04f, 0), fw, in ? 0.12f : 0.22f);
        nail(tip, new Vector3f(fw).negate(), 0.22f, op, in ? 1 : 0.3f);
        float a = (float) (now - f.changed);
        if (in && a < 12) {
            bb(tip, 0.9f * (1 - a / 12), 0.4f, FxDraw.FLARE, 6 / 32f, CE, 1 - a / 12, true);
            dsk(tip, new Vector3f(tip).negate(), 0.2f + a * 0.12f, FxDraw.RING, 0.08f, CE, 1 - a / 12, true);
        }
    }

    private static void doll(Vector3f c, Vector3f rt, Vector3f up, Vector3f fw, float x, float y, float z, float hx, float hy, float hz, float[] col,
            float op) {
        if (DbFx.pass || op <= 0.002f) {
            return;
        }
        Vector3f center = at(at(at(c, rt, x), up, y), fw, z);
        Vector3f ax = new Vector3f(rt).mul(hx);
        Vector3f ay = new Vector3f(up).mul(hy);
        Vector3f az = new Vector3f(fw).mul(hz);
        Vector3f[][] faces = {{ax, ay, az}, {new Vector3f(ax).negate(), az, ay}, {ay, az, ax}, {new Vector3f(ay).negate(), ax, az}, {az, ax, ay},
                {new Vector3f(az).negate(), ay, ax}};
        for (Vector3f[] fc : faces) {
            Vector3f nn = safe(fc[0]);
            float light = 0.55f + 0.45f * Math.max(0, nn.y * 0.7f + 0.35f + nn.x * 0.15f);
            float[] lc = {col[0] * light, col[1] * light, col[2] * light};
            Vector3f mid = new Vector3f(center).add(fc[0]);
            FxDraw.quad(new Vector3f(mid).sub(fc[1]).sub(fc[2]), new Vector3f(mid).add(fc[1]).sub(fc[2]), new Vector3f(mid).add(fc[1]).add(fc[2]),
                    new Vector3f(mid).sub(fc[1]).add(fc[2]), FxDraw.SOLID, 0, lc, op, false);
        }
    }

    // ---------- Todo ----------

    private static void clapFx(Vector3f c, float age, boolean real) {
        if (age < 0 || age > 12) {
            return;
        }
        float fo = 1 - age / 12;
        dsk(c, new Vector3f(c).negate(), 0.3f + age * 0.25f, FxDraw.RING, 0.08f, real ? CE_W : WHITE, fo, true);
        for (int i = 0; i < 6; i++) {
            Vector3f d = sphere(13, i);
            rib(at(c, d, 0.2f + age * 0.05f), at(c, d, 0.4f + age * 0.08f), 0.03f, FxDraw.STREAK, 0.9f, WHITE, fo, true);
        }
    }

    private static void swapFlash(Vector3f at, float age) {
        if (age < 0 || age > 10) {
            return;
        }
        float fo = 1 - age / 10;
        rib(add(at, 0, -0.2f, 0), add(at, 0, 2.2f, 0), 0.5f * fo, FxDraw.BEAM, 0.8f, CE_W, fo, true);
        warp(add(at, 0, 1, 0), 1.3f, 0.7f * fo, 0, age, null);
        bb(add(at, 0, 1, 0), 1.6f * fo, 0, FxDraw.SOFT, 0.5f, CE, 0.6f * fo, true);
    }

    // ---------- Inumaki ----------

    // Rings of voice flying from the mouth to the target, growing as they go.
    static void speech(Vector3f from, Vector3f to, float age, float[] col, float s, float life) {
        if (age < 0) {
            return;
        }
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        Vector3f dn = safe(d);
        float end = 1 - smooth((age - life) / 6);
        for (int i = 0; i < 5; i++) {
            float u = age * 0.09f - i * 0.18f;
            if (u < 0 || u > 1) {
                continue;
            }
            Vector3f p = at(from, dn, u * len);
            float fo = (1 - u) * 0.8f + 0.2f;
            dsk(p, dn, (0.5f + u * 1.6f) * s, FxDraw.RING, 0.05f, col, 0.7f * fo * end, true);
            dsk(p, dn, (0.5f + u * 1.6f) * s, FxDraw.RING, 0.02f, WHITE, 0.4f * fo * end, true);
            if (i == 0) {
                warp(p, 0.9f * s, 0.5f, 0, age, null);
            }
        }
    }

    private static void sealMarks(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        float a = smooth(age / 4) * out;
        for (Vector3f eye : eyesOf(e, partial)) {
            bb(add(eye, 0, -0.16f, 0), 0.1f, 0, FxDraw.SOFT, 0.6f, VIOLET, 0.8f * a, true);
        }
        bb(mouthOf(e, partial), 0.35f, age * 0.1f, FxDraw.FLARE, 4 / 32f, LILAC, 0.5f * a, true);
    }

    private static void holdRings(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Vector3f c = add(rel(e.getPosition(partial)), 0, e.getBbHeight() * 0.55f, 0);
        float s = e.getBbWidth() / 0.6f;
        for (int i = 0; i < 3; i++) {
            dsk(add(c, 0, (-0.5f + i * 0.5f) * e.getBbHeight() / 1.8f, 0), UP, (1 + 0.05f * Mth.sin(age * 0.6f + i)) * s, FxDraw.RING, 0.04f, VIOLET,
                    0.6f * out, true);
        }
        for (Vector3f eye : eyesOf(e, partial)) {
            bb(eye, 0.12f, 0, FxDraw.SOFT, 0.8f, VIOLET, 0.8f * out, true);
        }
    }

    // "Get crushed!": streaks coming down, a ring and a crater-dark patch, cracks and dust.
    private static void crush(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float r = f.a;
        if (age < 6) {
            float fo = 1 - smooth(age / 6);
            for (int i = 0; i < 10; i++) {
                float x = (hash(i, 14, 1) - 0.5f) * r * 0.7f;
                float z = (hash(i, 14, 2) - 0.5f) * r * 0.7f;
                float y0 = 4 - ((age + 4) / 6) * 3;
                rib(add(c, x, y0 + 1, z), add(c, x, y0, z), 0.08f, FxDraw.STREAK, 0.6f, LILAC, 0.6f * fo, true);
            }
        }
        if (age < 18) {
            dsk(add(c, 0, 0.05f, 0), UP, 1 + age * 0.3f, FxDraw.RING, 0.1f, VIOLET, 1 - age / 18, true);
            warp(add(c, 0, 0.4f, 0), 2.2f, 0.9f * (1 - age / 18), 0, age, null);
        }
        crackRing(c, age, r, 6, 10, 0.9f * (1 - smooth((age - 40) / 20)));
        DbFx.nrDust(c, age, 71, 12, 1.2f, DUST, 40);
        dsk(add(c, 0, 0.03f, 0), UP, r, FxDraw.SOFT, 0.12f, DIRT, 0.9f * smooth(age / 4) * (1 - smooth((age - 40) / 20)), false);
    }

    private static void cough(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Vector3f m = mouthOf(e, partial);
        Vector3f fw = vec(body(e, partial).fwd);
        for (int i = 0; i < 8; i++) {
            float t0 = (i % 4) * 6;
            float b = DbFx.fract((age - t0) / 24f) * 24;
            if (age < t0 || b > 16) {
                continue;
            }
            Vector3f p = add(at(at(m, FxDraw.left(), (hash(i, 15, 1) - 0.5f) * 0.2f), fw, 0.03f * b), 0, -0.01f * b * b, 0);
            bb(p, 0.06f + 0.04f * hash(i, 15, 2), 0, FxDraw.SOFT, 0.9f, BLOOD, (1 - b / 16) * out, false);
        }
    }

    private static void sleep(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Vector3f head = add(rel(e.getPosition(partial)), 0.2f, e.getBbHeight() * 0.4f, 0);
        for (int i = 0; i < 3; i++) {
            float ph = DbFx.fract(age * 0.02f + i / 3f);
            bb(add(head, ph * 0.4f, ph * 1.2f, 0), 0.12f + ph * 0.18f, 0, FxDraw.RING, 0.2f, LILAC, 0.8f * (1 - ph) * out, true);
        }
    }

    // ---------- Miwa ----------

    static void miwaLine(Vector3f c, Vector3f dir, float len, float age, float life, float w) {
        if (age < 0 || age > life) {
            return;
        }
        float fo = 1 - age / life;
        Vector3f d = safe(dir);
        float ext = len * (0.5f + 0.5f * easeOut(Math.min(1, age / 2)));
        rib(at(c, d, -ext), at(c, d, ext), w * fo + 0.01f, FxDraw.STREAK, 0.9f, MIWA, fo, true);
        rib(at(c, d, -ext * 0.7f), at(c, d, ext * 0.7f), w * 0.4f * fo, FxDraw.STREAK, 0.95f, WHITE, fo, true);
    }

    private static void miwaArc(Fx f, float age) {
        if (age > 3) {
            return;
        }
        Vector3f c = rel(f.pos);
        Vector3f fwd = safe(vec(f.dir));
        Vector3f side = safe(new Vector3f(fwd).cross(UP));
        float r = f.a;
        for (int i = 0; i < 8; i++) {
            float u0 = i / 8f;
            float u1 = (i + 1) / 8f;
            float b0 = -1.2f + u0 * 2.4f;
            float b1 = -1.2f + u1 * 2.4f;
            rib(at(at(c, side, Mth.sin(b0) * r), fwd, Mth.cos(b0) * r), at(at(c, side, Mth.sin(b1) * r), fwd, Mth.cos(b1) * r), 0.12f * u1,
                    FxDraw.STREAK, 0.6f, MIWA, 0.8f * (1 - age / 4), true);
        }
    }

    private static void dashLine(Fx f, float age) {
        if (age > 8) {
            return;
        }
        float fo = 1 - age / 8;
        Vector3f a = rel(f.pos);
        Vector3f d = vec(f.dir);
        Vector3f b = at(a, d, 1);
        rib(a, b, 0.5f * fo, FxDraw.STREAK, 0.5f, MIWA, 0.6f * fo, true);
        rib(a, b, 0.08f * fo, FxDraw.STREAK, 0.9f, WHITE, fo, true);
        Vector3f side = safe(new Vector3f(d).cross(UP));
        Vector3f dn = safe(d);
        for (int i = 0; i < 4; i++) {
            Vector3f p = add(at(b, side, (i - 1.5f) * 0.25f), 0, -0.6f + i * 0.3f, 0);
            rib(at(p, dn, -0.6f), at(p, dn, -Math.min(3, d.length())), 0.06f, FxDraw.STREAK, 0.6f, AIR, 0.5f * fo, true);
        }
    }

    // Simple Domain: a thin circle on the ground, a faint fill, an inner white line and ticks round the edge.
    private static void simpleDomain(Fx f, float age, float out) {
        Vector3f c = rel(f.pos);
        float k = f.a * out;
        float r = f.b;
        if (k <= 0) {
            return;
        }
        dsk(add(c, 0, 0.04f, 0), UP, r * 2, FxDraw.RING, 0.012f, MIWA, 0.9f * k, true);
        dsk(add(c, 0, 0.035f, 0), UP, r * 2, FxDraw.SOFT, 0.05f, MIWA, 0.12f * k, true);
        dsk(add(c, 0, 0.04f, 0), UP, r * 2 * 0.94f, FxDraw.RING, 0.006f, WHITE, 0.5f * k, true);
        for (int i = 0; i < 24; i++) {
            float a = i / 24f * Mth.TWO_PI + age * 0.002f;
            float r0 = r * (i % 2 != 0 ? 0.88f : 0.92f);
            rib(add(c, Mth.cos(a) * r0, 0.045f, Mth.sin(a) * r0), add(c, Mth.cos(a) * r * 0.99f, 0.045f, Mth.sin(a) * r * 0.99f), 0.03f, FxDraw.BEAM,
                    0.9f, MIWA, 0.7f * k, true);
        }
    }
}
