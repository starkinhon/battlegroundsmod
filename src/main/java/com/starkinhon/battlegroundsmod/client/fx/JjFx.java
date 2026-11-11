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

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

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
    // Itadori's cursed energy in the shell aura (the Visualizador's IT_CE).
    private static final float[] IT_CE = FxDraw.rgb(0x3D86FF);
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

    private JjFx() {
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    private static boolean stream(int kind) {
        return switch (kind) {
            case SubNet.DIV_AURA, SubNet.DIV_MARK, SubNet.CUT_LOOP, SubNet.BLOOD_SWEEP, SubNet.MAX_CHARGE, SubNet.METEOR, SubNet.BF_RING, SubNet.GUARD, SubNet.DASH_TRAIL, SubNet.BLOOD_BALL, SubNet.BLOOD_ORBS, SubNet.ROPE,
                    SubNet.GROUND_MARK, SubNet.NAIL, SubNet.DOLL, SubNet.SIMPLE_DOMAIN -> true;
            default -> false;
        };
    }

    // Kinds that last a while on one message, following an entity (a = ticks, 0 stops).
    private static boolean timed(int kind) {
        return switch (kind) {
            case SubNet.SOUL, SubNet.BLOOD_POOL, SubNet.SCALE, SubNet.RATIO, SubNet.GOLD_AURA, SubNet.SEAL_MARKS, SubNet.HOLD_RINGS, SubNet.COUGH, SubNet.SLEEP -> true;
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
    }

    private static double life(Fx f) {
        return switch (f.kind) {
            case SubNet.DIVERGENT -> f.a + 42;
            case SubNet.INK_BURST -> 20;
            case SubNet.CUT_SNAP -> 21;
            case SubNet.GROUND_SPIKES -> 41;
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
            case SubNet.DIV_AURA, SubNet.DIV_MARK, SubNet.GUARD -> 5;
            case SubNet.CUT_LOOP -> 3;
            case SubNet.BLOOD_SWEEP -> 6;
            case SubNet.MAX_CHARGE, SubNet.METEOR -> 3;
            case SubNet.BLOOD_POOL -> 12;
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
                case SubNet.CUT_SNAP -> cutSnap(f, age);
                case SubNet.GROUND_SPIKES -> groundSpikes(f, age);
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
                case SubNet.DIV_MARK -> divMark(f, age, partial, out);
                case SubNet.CUT_LOOP -> cutLoop(f, age, partial, out);
                case SubNet.SOUL -> soul(f, age, partial, out);
                case SubNet.BLOOD_POOL -> bloodPool(f, age, out);
                case SubNet.BLOOD_SWEEP -> bloodSweep(f, age, out);
                case SubNet.MAX_CHARGE -> maxCharge(f, age, partial, out);
                case SubNet.METEOR -> meteor(f, age, out);
                case SubNet.BF_RING -> bfRing(f, age, out);
                case SubNet.GUARD -> guard(f, partial, out);
                case SubNet.DASH_TRAIL -> dashTrail(f, partial, out);
                case SubNet.BLOOD_BALL -> bloodBallFx(f, age, out);
                case SubNet.BLOOD_ORBS -> supernova(f, age, out);
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

    // Itadori's cursed energy as the approved shell aura (Stark, 2026-11-11: the brush style is out for good): one 3D
    // shell bent by noise into tongues, the same shader as the Visualizador's makeAura, in blue. c = the shell's middle,
    // up = where its tongues point, stretch = how long it is along up.
    static void ceShell(Vector3f c, Vector3f up, float stretch, float sx, float sy, float sz, float t, float growth, float alpha, float flash,
            float seed, float tongue, float upAmt, float crown, float spike) {
        if (!DbFx.pass || growth <= 0.001f || alpha <= 0.001f) {
            return;
        }
        if (!KiMesh.ready()) {
            bb(c, 1.4f * sy * stretch, 0, FxDraw.SOFT, 0.2f, IT_CE, 0.4f * alpha * growth, true);
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.SHELL);
        p.phase = t / 20f * 1.5f;
        p.time = t / 20f * 1.6f;
        p.growth = Math.min(1, growth);
        p.flashAmt = flash;
        p.alpha = Math.min(1, alpha);
        p.color = IT_CE;
        p.seed = seed;
        p.tongue = tongue;
        p.up = upAmt;
        p.crown = crown;
        p.spike = spike;
        p.thresh = 0.6f;
        p.size = new float[] {sx, sy, sz};
        Quaternionf q = new Quaternionf().rotationTo(0, 1, 0, up.x, up.y, up.z);
        Matrix4f m = new Matrix4f().translation(c).rotate(q).scale(1, stretch, 1);
        KiMesh.queue(KiMesh.Mesh.AURA, m, p, KiMesh.BOTH, 10);
    }

    static void fistShell(Vector3f fist, float t, float growth, float flash, float seed) {
        ceShell(add(fist, 0, 0.1f, 0), UP, 1, 0.2f, 0.34f, 0.2f, t, growth, 1, flash, seed, 0.5f, 0.75f, 0.9f, 0.12f);
    }

    // The cursed energy on Itadori: on the fist that strikes (b = 0), both fists in the zone (b = 1), or the fist and
    // the whole body while the Divergent delay holds (b = 2). In first person the body shell would wrap the camera.
    private static void divAuraOf(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Body b = body(e, partial);
        float g = f.a * out;
        boolean self = FxAnchor.firstPersonSelf(f.id);
        fistShell(rel(fist(b, 1)), age, g, f.b == 2 ? 0.6f : 0, 1.3f);
        if (f.b == 1) {
            fistShell(rel(fist(b, -1)), age, g, 0, 2.9f);
        }
        if (f.b == 2 && !self) {
            float s = (float) b.s();
            ceShell(rel(b.point(0, 0, 1.0)), UP, 1, 0.66f * s, 1.16f * s, 0.54f * s, age, g, 0.9f, 0, 4.1f, 0.42f, 0.75f, 0.7f, 0.12f);
        }
    }

    // The delay stored on someone (holding Z): a small blue flame on the chest, pulsing.
    private static void divMark(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Body b = body(e, partial);
        float pulse = 0.75f + 0.25f * Mth.sin(age * 0.45f);
        float s = (float) b.s();
        ceShell(rel(b.point(0.22, 0, 1.2)), UP, 1, 0.13f * s, 0.2f * s, 0.13f * s, age, smooth(age / 4) * pulse * out, 1, 0, 6.7f, 0.5f, 0.75f, 0.7f,
                0.12f);
    }

    // The Divergent Fist (preview v2): a dry white first impact, the energy late (the fist and body auras come from
    // DIV_AURA), then the second impact goes through the body and out of the back as a jet of the shell aura, with rings
    // and white streaks. b = 1: the stored delay going off (only the second impact).
    private static void divergent(Fx f, float age, float partial) {
        float delay = f.a;
        float p1 = f.b == 1 ? -1 : age;
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
            // The energy late: a small flame swelling on the chest where it will come out.
            float sw = smooth(p1 / Math.max(1, delay));
            ceShell(at(c, dir, 0.05f), UP, 1, 0.13f, 0.2f, 0.13f, age, sw, 1, 0, 6.7f, 0.5f, 0.75f, 0.7f, 0.12f);
            warp(c, 1.2f + sw, 0.6f * sw, 2, p1, null);
        }
        if (a >= 0) {
            float fo = a < 5 ? 1 : 1 - smooth((a - 5) / 9);
            float s = easeOut(Math.min(1, a / 5));
            // The jet of cursed energy out of the back (the preview's burstA).
            if (a < 14) {
                Vector3f jet = at(c, dir, 0.9f + 1.2f * s);
                ceShell(jet, dir, 0.7f + 1.5f * s, 0.42f, 1.0f, 0.42f, a, Math.min(1, a / 2), fo, a < 3 ? 1 : 0, 8.3f, 0.55f, 0.95f, 1, 0.2f);
            }
            Vector3f back = at(c, dir, 0.35f);
            if (a < 6) {
                bb(c, 5 * (1 - a / 7), 0, FxDraw.SOFT, 0.4f, CE_W, 0.8f * (1 - a / 6), true);
                bb(c, 3, 0.2f, FxDraw.FLARE, 12 / 32f, IT_CE, 1 - a / 6, true);
            }
            for (int r = 0; r < 4; r++) {
                float ar = a - r * 1.5f;
                if (ar < 0 || ar > 16) {
                    continue;
                }
                float k = ar / 16;
                Vector3f p = at(back, dir, 0.3f + r * 0.9f + ar * 0.28f);
                float rad = (0.5f + 0.35f * r) * (0.6f + 1.2f * easeOut(k));
                dsk(p, dir, rad * 2, FxDraw.RING, 0.07f, IT_CE, (1 - k) * 0.9f, true);
            }
            if (a < 14) {
                dsk(c, new Vector3f(c).negate(), (0.6f + 3.2f * easeOut(a / 14)) * 2, FxDraw.RING, 0.06f, CE_W, 0.8f * (1 - a / 14), true);
            }
            if (a < 16) {
                Vector3f gr = add(c, 0, -f.c + 0.05f, 0);
                dsk(at(gr, dir, 0.3f), UP, (0.6f + 4.5f * easeOut(a / 16)) * 2, FxDraw.RING, 0.08f, IT_CE, 0.6f * (1 - a / 16), true);
                warp(c, 3.5f, 1.0f * (1 - a / 16), 0, a, null);
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

    // ---------- Itadori: Corte de Tesoura and Golpe na Alma ----------

    private static final float[] GHOST = {0.78f, 0.9f, 1f};

    // A "cut here" loop (manga style): a dashed black line round the thing, each dash its own length, and white
    // scissors with a black outline waiting on it, each pair its own size and snip. When it closes (a = 0..1) every
    // pair runs along its stretch of the line, cutting the dashes behind it.
    private static void cutLoop(Fx f, float age, float partial, float out) {
        float r = (float) f.dir.x;
        float k = f.a;
        if (f.dir.y == 1) {
            LivingEntity e = living((int) f.dir.z);
            if (e == null) {
                return;
            }
            float h = e.getBbHeight();
            Vector3f feet = rel(e.getPosition(partial));
            drawLoop(add(feet, 0, h * 0.72f, 0), r, 0.22f, f.id * 7 + 1, age, k, out, 2);
            drawLoop(add(feet, 0, h * 0.45f, 0), r * 0.92f, -0.12f, f.id * 7 + 4, age, k, out, 1);
        } else {
            drawLoop(rel(f.pos), r, 0.04f, f.id * 7 + 2, age, k, out, 3);
        }
    }

    private static void drawLoop(Vector3f c, float r, float tilt, int seed, float age, float k, float out, int pairs) {
        float yaw = hash(seed, 1, 5) * Mth.TWO_PI;
        Vector3f n = v(Mth.sin(tilt) * Mth.cos(yaw), Mth.cos(tilt), Mth.sin(tilt) * Mth.sin(yaw)).normalize();
        Vector3f[] bs = basis(n);
        float drawIn = smooth(age / 5);
        int dashes = 22 + (int) (hash(seed, 2, 5) * 6);
        float span = Mth.TWO_PI / pairs;
        float[] start = new float[pairs];
        for (int j = 0; j < pairs; j++) {
            start[j] = j * span + hash(seed, j, 6) * 0.4f;
        }
        Vector3f look = FxDraw.left().cross(FxDraw.up(), new Vector3f()).normalize();
        for (int i = 0; i < dashes; i++) {
            float a0 = i / (float) dashes * Mth.TWO_PI;
            if (i / (float) dashes > drawIn) {
                break;
            }
            float len = (0.42f + 0.22f * hash(seed, i, 7)) * Mth.TWO_PI / dashes;
            boolean cut = false;
            for (int j = 0; j < pairs; j++) {
                float rel = ((a0 - start[j]) % Mth.TWO_PI + Mth.TWO_PI) % Mth.TWO_PI;
                if (rel < span && rel < k * span) {
                    cut = true;
                }
            }
            if (cut) {
                continue;
            }
            Vector3f p0 = loopAt(c, bs, r, a0);
            Vector3f p1 = loopAt(c, bs, r, a0 + len);
            rib(p0, p1, 0.05f, FxDraw.SOLID, 0, INK, out, false);
        }
        for (int j = 0; j < pairs; j++) {
            float sz = 0.2f + 0.1f * hash(seed, j, 8);
            float ang = start[j] + (k > 0 ? k * span : Mth.sin(age * (0.05f + 0.03f * hash(seed, j, 9)) + j) * 0.18f);
            Vector3f p = loopAt(c, bs, r + 0.07f, ang);
            Vector3f t = safe(loopAt(c, bs, r, ang + 0.05f).sub(loopAt(c, bs, r, ang - 0.05f)));
            Vector3f o = safe(new Vector3f(p).sub(c));
            float snip = k > 0 ? 0.5f + 0.5f * Mth.sin(age * 2.6f + j) : 0.5f + 0.5f * Mth.sin(age * (0.25f + 0.15f * hash(seed, j, 10)) + j * 2);
            scissors(p, t, o, look, sz * drawIn, 0.08f + 0.42f * snip, out);
        }
    }

    private static Vector3f loopAt(Vector3f c, Vector3f[] bs, float r, float ang) {
        return at(at(c, bs[0], Mth.cos(ang) * r), bs[1], Mth.sin(ang) * r);
    }

    // A pair of scissors at p pointing along t (o = out from the loop): two blades opening by open (radians), white
    // inside a black outline, and two ring handles behind the pivot.
    private static void scissors(Vector3f p, Vector3f t, Vector3f o, Vector3f look, float s, float open, float op) {
        if (s <= 0.01f) {
            return;
        }
        Vector3f toCam = safe(new Vector3f(p).negate());
        Vector3f front = at(p, toCam, 0.012f);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f d = safe(at(new Vector3f(t), o, side * open));
            Vector3f tip = at(p, d, s);
            Vector3f mid = at(p, d, s * 0.45f);
            rib(at(p, d, -0.03f), tip, 0.075f * s / 0.25f, FxDraw.SOLID, 0, INK, op, false);
            rib(at(front, d, 0.0f), at(mid, toCam, 0.012f), 0.042f * s / 0.25f, FxDraw.SOLID, 0, WHITE, op, false);
            rib(at(mid, toCam, 0.012f), at(at(tip, d, -0.04f), toCam, 0.012f), 0.022f * s / 0.25f, FxDraw.SOLID, 0, WHITE, op, false);
            Vector3f hd = safe(at(new Vector3f(t).negate(), o, -side * (0.6f + open)));
            Vector3f handle = at(p, hd, s * 0.42f);
            dsk(handle, toCam, s * 0.62f, FxDraw.RING, 0.32f, INK, op, false);
            dsk(at(handle, toCam, 0.012f), toCam, s * 0.5f, FxDraw.RING, 0.22f, WHITE, op, false);
        }
        bb(at(p, toCam, 0.02f), 0.05f * s / 0.25f, 0, FxDraw.SOFT, 0.9f, INK, op, false);
    }

    // The scissors met: a white cut line across, a thin flash ring, and on a block the stone giving way.
    private static void cutSnap(Fx f, float age) {
        if (age > 20) {
            return;
        }
        Vector3f c = rel(f.pos);
        float r = f.a;
        float k = age / 10;
        Vector3f side = safe(FxDraw.left());
        if (age < 10) {
            rib(at(c, side, -r * 1.7f), at(c, side, r * 1.7f), 0.12f * (1 - k), FxDraw.SOLID, 0, INK, 1 - k, false);
            rib(at(c, side, -r * 1.9f), at(c, side, r * 1.9f), 0.06f * (1 - k), FxDraw.STREAK, 0.9f, WHITE, 1 - k, true);
            dsk(c, UP, r * 2 * (1 + easeOut(k) * 1.4f), FxDraw.RING, 0.05f, WHITE, 0.9f * (1 - k), true);
        }
        if (age < 4) {
            bb(c, 1.2f + r, age * 0.2f, FxDraw.FLARE, 6 / 32f, WHITE, 1 - age / 4, true);
        }
        if (f.b == 0) {
            debris(add(c, 0, 0.1f, 0), age, f.id, 8, r / 1.6f, v(0, 0, 0), ROCK);
            DbFx.nrDust(add(c, 0, -0.4f, 0), age, f.id + 3, 10, r, DUST, 30);
        }
    }

    // Golpe na Alma: the soul knocked out of the body (a pale see-through copy thrown back, then snapping in), and while
    // the power is sealed, a dashed black band round the chest and pale wisps.
    private static void soul(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Body b = body(e, partial);
        Vector3f back = safe(vec(f.dir));
        if (age < 18) {
            float push = age < 8 ? easeOut(age / 8) : 1 - smooth((age - 8) / 10);
            float op = 0.6f * (age < 12 ? 1 : 1 - smooth((age - 12) / 6));
            Vector3f off = new Vector3f(back).mul(1.1f * push).add(0, 0.25f * push, 0);
            Vector3f head = rel(b.point(0, 0, 1.62)).add(off);
            Vector3f neck = rel(b.point(0, 0, 1.42)).add(off);
            Vector3f hip = rel(b.point(0, 0, 0.82)).add(off);
            bb(head, 0.55f, 0, FxDraw.SOFT, 0.5f, GHOST, op, true);
            rib(neck, hip, 0.42f, FxDraw.BEAM, 0.4f, GHOST, op, true);
            for (int side = -1; side <= 1; side += 2) {
                Vector3f sh = rel(shoulder(b, side)).add(off);
                Vector3f hand = rel(b.point(-0.05, -0.42 * side, 0.85)).add(off).add(new Vector3f(back).mul(0.25f * push));
                rib(sh, hand, 0.16f, FxDraw.BEAM, 0.3f, GHOST, op * 0.85f, true);
                Vector3f foot = rel(b.point(0, -0.13 * side, 0.05)).add(off).add(new Vector3f(back).mul(0.35f * push));
                rib(hip, foot, 0.18f, FxDraw.BEAM, 0.3f, GHOST, op * 0.85f, true);
            }
        }
        float on = smooth(age / 4) * out;
        Vector3f chest = rel(b.point(0, 0, 1.15));
        float r = (float) (0.42 * b.s());
        for (int i = 0; i < 16; i++) {
            float a0 = i / 16f * Mth.TWO_PI + age * 0.03f;
            float a1 = a0 + (0.5f + 0.2f * hash(f.id, i, 3)) * Mth.TWO_PI / 16;
            rib(add(chest, Mth.cos(a0) * r, 0, Mth.sin(a0) * r), add(chest, Mth.cos(a1) * r, 0, Mth.sin(a1) * r), 0.04f, FxDraw.SOLID, 0, INK, on,
                    false);
        }
        for (int i = 0; i < 5; i++) {
            float ph = DbFx.fract(age * (0.03f + 0.02f * hash(f.id, i, 4)) + hash(f.id, i, 5));
            Vector3f p = add(chest, (hash(f.id, i, 6) - 0.5f) * 0.6f, 0.2f + ph * 0.9f, (hash(f.id, i, 7) - 0.5f) * 0.6f);
            bb(p, 0.12f + 0.08f * hash(f.id, i, 8), 0, FxDraw.SOFT, 0.6f, GHOST, 0.5f * on * Mth.sin(ph * Mth.PI), true);
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

    // ---------- Choso (kit of 2026-11-11) ----------

    // A pool of his blood: an irregular puddle (a few overlapping blobs, each its own size and offset), dark edge,
    // red body, a thin gloss; it spreads in fast and shrinks as it dries.
    private static void bloodPool(Fx f, float age, float out) {
        Vector3f c = add(rel(f.pos), 0, 0.03f, 0);
        float life = Math.max(1, f.a);
        float grow = easeOut(Math.min(1, age / 7));
        float dry = 1 - smooth((age - (life - 30)) / 30);
        float r = f.b * grow * (0.65f + 0.35f * dry);
        if (r <= 0.02f) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            float ang = hash(f.id, i, 1) * Mth.TWO_PI;
            float d = i == 0 ? 0 : r * (0.35f + 0.3f * hash(f.id, i, 2));
            float s = r * (i == 0 ? 1 : 0.45f + 0.35f * hash(f.id, i, 3));
            Vector3f p = add(c, Mth.cos(ang) * d, 0.001f * i, Mth.sin(ang) * d);
            dsk(p, UP, s * 2.15f, FxDraw.SOFT, 0.95f, BLOOD_D, out, false);
            dsk(add(p, 0, 0.004f, 0), UP, s * 1.8f, FxDraw.SOFT, 0.95f, BLOOD, out, false);
        }
        dsk(add(c, r * 0.2f, 0.01f, -r * 0.15f), UP, r * 0.5f, FxDraw.SOFT, 0.5f, GLOSS, 0.35f * out * dry, false);
    }

    // A Piercing Blood that pours for a while and sweeps: one wavy line of liquid blood, thin at the palms, droplets falling
    // off it and a spray where it ends. Thick (a > 0.5) is the Perfurante Maximo.
    private static void bloodSweep(Fx f, float age, float out) {
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (len < 0.1f) {
            return;
        }
        Vector3f dir = new Vector3f(d).div(len);
        Vector3f[] bs = basis(dir);
        float w = f.a * (0.85f + 0.15f * Mth.sin(age * 2.1f));
        boolean big = f.a > 0.5f;
        int n = big ? 28 : 16;
        Vector3f last = new Vector3f(from);
        for (int i = 1; i <= n; i++) {
            float u = i / (float) n;
            float s = u * len;
            float wob = (big ? 0.12f : 0.03f) * Mth.sin(s * 0.9f - age * 1.7f) + (big ? 0.06f : 0.015f) * Mth.sin(s * 2.3f + age * 2.6f + 1);
            Vector3f p = at(at(at(from, dir, s), bs[0], wob), bs[1], wob * 0.6f);
            float wi = w * Math.min(1, u * (big ? 10 : 6) + 0.25f);
            bloodLine(last, p, wi, out, big ? 0.12f : 0.1f);
            last = p;
        }
        int drops = big ? 26 : 10;
        for (int i = 0; i < drops; i++) {
            float ph = DbFx.fract(age * (0.05f + 0.04f * hash(f.id, i, 2)) + hash(f.id, i, 3));
            Vector3f p = add(at(from, dir, len * hash(f.id, i, 1)), 0, -ph * ph * 2.2f, 0);
            bb(p, (0.05f + 0.06f * hash(f.id, i, 4)) * (big ? 2 : 1), 0, FxDraw.SOFT, 0.95f, BLOOD, out * (1 - ph * 0.6f), false);
        }
        Vector3f end = at(from, dir, len);
        for (int i = 0; i < (big ? 12 : 6); i++) {
            float ph = DbFx.fract(age * 0.09f + hash(f.id + 7, i, 1));
            Vector3f sd = sphere(f.id + (int) Math.floor(age * 0.09f + hash(f.id + 7, i, 1)) * 13, i).add(new Vector3f(dir).mul(-0.6f)).normalize();
            Vector3f p = add(at(end, sd, ph * (big ? 2.4f : 1.2f)), 0, -ph * ph * 0.8f, 0);
            bb(p, (big ? 0.18f : 0.08f) * (1 - ph * 0.5f), 0, FxDraw.SOFT, 0.95f, BLOOD, out * (1 - ph), false);
        }
        if (big && DbFx.pass) {
            warp(at(from, dir, 1.2f), 1.4f, 0.4f * out, 0, age, null);
        }
    }

    // The Perfurante Maximo's charge: drops of his blood leave the body, each with its own path, size and pace, and
    // spiral into the ball between his palms (no aura: Stark, 2026-11-11).
    private static void maxCharge(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Body b = body(e, partial);
        Vector3f c = rel(f.pos);
        Vector3f base = rel(b.feet);
        float k = smooth(f.a * 3) * out;
        for (int i = 0; i < 26; i++) {
            float sp = 0.035f + 0.04f * hash(f.id, i, 1);
            float s0 = DbFx.fract(age * sp + hash(f.id, i, 2));
            if (hash(f.id, i, 9) * 0.6f > f.a + 0.2f) {
                continue;
            }
            float a0 = hash(f.id, i, 3) * Mth.TWO_PI;
            float y0 = (0.5f + 1.3f * hash(f.id, i, 4)) * (float) b.s();
            float r0 = (0.35f + 0.4f * hash(f.id, i, 5)) * (float) b.s();
            float turn = (hash(f.id, i, 6) < 0.5f ? -1 : 1) * (1.2f + 1.6f * hash(f.id, i, 7));
            Vector3f p0 = dropAt(base, c, a0, y0, r0, turn, s0);
            Vector3f p1 = dropAt(base, c, a0, y0, r0, turn, Math.max(0, s0 - 0.05f));
            float wd = (0.03f + 0.03f * hash(f.id, i, 8)) * (1 - 0.5f * s0 * s0);
            bloodLine(p1, p0, wd, k, 0);
        }
    }

    private static Vector3f dropAt(Vector3f base, Vector3f c, float a0, float y0, float r0, float turn, float s) {
        float e = s * s;
        float ang = a0 + turn * s * 3.2f;
        float rr = r0 * (1 - e);
        return add(base, Mth.cos(ang) * rr, y0, Mth.sin(ang) * rr).lerp(c, e);
    }

    // Blood spikes out of the ground: each one its own height, width, lean and moment; they hold and then sink back.
    private static void groundSpikes(Fx f, float age) {
        if (DbFx.pass || age > 40) {
            return;
        }
        Vector3f base = rel(f.pos);
        int n = Math.max(1, (int) f.b);
        for (int i = 0; i < n; i++) {
            float delay = hash(f.id, i, 1) * 4;
            float g = easeOut(clamp((age - delay) / 3, 0, 1)) * (1 - smooth((age - 26) / 12));
            if (g <= 0.01f) {
                continue;
            }
            float ang = hash(f.id, i, 2) * Mth.TWO_PI;
            float d = f.a * (float) Math.sqrt(hash(f.id, i, 3));
            Vector3f p = add(base, Mth.cos(ang) * d, -0.2f, Mth.sin(ang) * d);
            float h = f.c * (0.45f + 0.75f * hash(f.id, i, 4)) * g;
            float w = 0.1f + 0.14f * hash(f.id, i, 5);
            Vector3f ax = v(Mth.cos(ang) * 0.5f * hash(f.id, i, 6), 1, Mth.sin(ang) * 0.5f * hash(f.id, i, 6)).normalize();
            Vector3f mid = at(p, ax, h * 0.5f);
            Vector3f tip = at(p, ax, h);
            FxDraw.strip(new Vector3f[] {p, mid, tip}, new float[] {w * 1.25f, w * 0.7f, 0}, new float[] {0, 0.5f, 1}, FxDraw.SOLID, 0, BLOOD_D, 1, false);
            FxDraw.strip(new Vector3f[] {p, mid, tip}, new float[] {w, w * 0.55f, 0}, new float[] {0, 0.5f, 1}, FxDraw.SOLID, 0, BLOOD_R, 1, false);
            FxDraw.strip(new Vector3f[] {at(p, FxDraw.left(), w * 0.2f), at(mid, FxDraw.left(), w * 0.12f), tip}, new float[] {w * 0.2f, w * 0.1f, 0},
                    new float[] {0, 0.5f, 1}, FxDraw.SOLID, 0, GLOSS, 0.8f, false);
        }
        if (age < 10) {
            bloodSplash(add(base, 0, 0.2f, 0), age, f.id, 10, f.a / 1.4f, UP);
        }
    }

    // The Blood Meteorite: a ball of hardened blood (dark, dull, a few cracks of red) with a short dark trail.
    private static void meteor(Fx f, float age, float out) {
        Vector3f p = rel(f.pos);
        float r = f.a;
        bb(p, r * 2.3f, age * 0.03f, FxDraw.SOFT, 0.97f, BLOOD_D, out, false);
        bb(p, r * 1.7f, -age * 0.02f, FxDraw.SOFT, 0.95f, new float[] {0.3f, 0.02f, 0.04f}, out, false);
        for (int i = 0; i < 4; i++) {
            Vector3f a = at(p, sphere(f.id, i), r * 0.55f);
            Vector3f b = at(p, sphere(f.id + 5, i), r * 0.6f);
            rib(a, b, 0.03f, FxDraw.BEAM, 0.9f, BLOOD_R, 0.7f * out, false);
        }
        Vec3[] tr = f.trail.toArray(new Vec3[0]);
        for (int i = 1; i < tr.length; i++) {
            float u = i / (float) tr.length;
            bloodLine(rel(tr[i - 1]), rel(tr[i]), r * 0.5f * u, out * u, 0);
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
