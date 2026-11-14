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

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
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
    private static final float[] GLOSS = {1, 0.5f, 0.48f};
    private static final float[] STEAM_RED = {0.5f, 0.12f, 0.13f};
    private static final float[] VIOLET = FxDraw.rgb(0xA070FF);
    private static final float[] LILAC = FxDraw.rgb(0xE2D4FF);
    private static final float[] STEEL = {0.82f, 0.86f, 0.9f};
    private static final float[] STEEL_D = {0.6f, 0.62f, 0.66f};
    private static final float[] ROCK = {0.42f, 0.42f, 0.45f};
    private static final float[] ROCK_D = {0.28f, 0.28f, 0.3f};
    private static final float[] CRACK = {0.04f, 0.035f, 0.03f};
    private static final float[] MIWA = FxDraw.rgb(0x8FD0FF);
    private static final float[] MW_GLINT_C = FxDraw.rgb(0xC8E4FF);
    private static final float[] MW_SPARK_B = {0.25f, 0.6f, 1f};
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
                    SubNet.GROUND_MARK, SubNet.NAIL, SubNet.DOLL, SubNet.SIMPLE_DOMAIN, SubNet.TODO_FLAME, SubNet.MEGAPHONE, SubNet.NB_LINK, SubNet.HAMMER,
                    SubNet.PALM_NAIL, SubNet.MW_MARK -> true;
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
        MW_FLASH.clear();
    }

    private static double life(Fx f) {
        return switch (f.kind) {
            case SubNet.DIVERGENT -> f.a + 42;
            case SubNet.INK_BURST -> 20;
            case SubNet.CUT_SNAP -> 21;
            case SubNet.GROUND_SPIKES -> 41;
            case SubNet.BF_SPARKS -> 26;
            case SubNet.ARC -> 8;
            case SubNet.CRACKS -> 70;
            case SubNet.BLOOD_BEAM -> 30;
            case SubNet.ORB_POP -> 16;
            case SubNet.SUPERNOVA_BURST -> 30;
            case SubNet.BLOOD_SPLASH -> 28;
            case SubNet.RATIO_CRIT -> 16;
            case SubNet.SLAM -> 82;
            case SubNet.RESONANCE -> 32;
            case SubNet.NB_BURST -> f.b * 20 + 2;
            case SubNet.THORNS -> (1 + 0.3 * f.a) * 20 + 4;
            case SubNet.RESO_LINE -> 9;
            case SubNet.CLAP -> 13;
            case SubNet.SWAP -> 11;
            case SubNet.VOICE -> 44;
            case SubNet.CRUSH -> 62;
            case SubNet.MIWA_LINE -> 9;
            case SubNet.MIWA_ARC -> 4;
            case SubNet.DASH_LINE -> 9;
            case SubNet.DOMAIN_POP -> 15;
            case SubNet.MW_SLASH -> f.id == 1 ? 24 : 18;
            case SubNet.MW_CUT -> f.b * 20 + 2;
            case SubNet.MW_STREAK -> (0.08 + f.b) * 20 + 2;
            case SubNet.MW_GASH -> f.a * 20 + 2;
            case SubNet.MW_GLINT -> 6;
            default -> 40;
        };
    }

    private static float fadeOut(Fx f) {
        return switch (f.kind) {
            case SubNet.DIV_AURA, SubNet.DIV_MARK, SubNet.GUARD -> 5;
            case SubNet.CUT_LOOP -> 3;
            case SubNet.BLOOD_SWEEP -> 6;
            case SubNet.MAX_CHARGE, SubNet.METEOR -> 3;
            case SubNet.TODO_FLAME -> 5;
            case SubNet.BLOOD_POOL -> 12;
            case SubNet.BF_RING -> 2;
            case SubNet.ROPE -> 6;
            case SubNet.SIMPLE_DOMAIN -> 10;
            case SubNet.NB_LINK -> 7;
            case SubNet.HAMMER -> 3;
            case SubNet.PALM_NAIL -> 6;
            case SubNet.MW_MARK -> 3;
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
                case SubNet.BF_SPARKS -> bfSparks(f, age);
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
                case SubNet.VOICE -> voice(f, age);
                case SubNet.NB_BURST -> nbBurst(f, age);
                case SubNet.THORNS -> thorns(f, age);
                case SubNet.CRUSH -> crush(f, age);
                case SubNet.MIWA_LINE -> miwaLine(rel(f.pos), vec(f.dir), f.a, age, 6, f.b > 0 ? f.b : 0.06f);
                case SubNet.MIWA_ARC -> miwaArc(f, age);
                case SubNet.DASH_LINE -> dashLine(f, age);
                case SubNet.MW_SLASH -> mwSlash(f, age);
                case SubNet.MW_CUT -> mwCut(f, age, partial);
                case SubNet.MW_STREAK -> mwStreak(f, age);
                case SubNet.MW_GASH -> mwGash(f, age);
                case SubNet.MW_GLINT -> {
                    float k = age / 4.4f;
                    if (k < 1) {
                        float op = Mth.sin(k * Mth.PI) * 1.3f;
                        bb(rel(f.pos), f.a, 0, FxDraw.FLARE, 4 / 32f, MW_GLINT_C, Math.min(1, op), false);
                        bb(rel(f.pos), f.a * 1.4f, 0, FxDraw.FLARE, 4 / 32f, MW_GLINT_C, 0.6f * Math.min(1, op), true);
                    }
                }
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
                case SubNet.TODO_FLAME -> todoFlame(f, age, partial, out);
                case SubNet.MEGAPHONE -> megaphone(f, age, partial, out);
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
                case SubNet.NB_LINK -> link(f, now, age, out);
                case SubNet.HAMMER -> hammer(f, now, age, partial, out);
                case SubNet.PALM_NAIL -> {
                    Vector3f palm = rel(f.pos);
                    nbNail(add(palm, 0, -0.05f, 0), v(0, -1, 0), 0.3f, out);
                }
                case SubNet.SEAL_MARKS -> sealMarks(f, age, partial, out);
                case SubNet.HOLD_RINGS -> holdRings(f, age, partial, out);
                case SubNet.COUGH -> cough(f, age, partial, out);
                case SubNet.SLEEP -> sleep(f, age, partial, out);
                case SubNet.SIMPLE_DOMAIN -> simpleDomain(f, now, age, out);
                case SubNet.MW_MARK -> mwMark(f, age, partial, out);
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

    private static final float[] TD_CYAN = FxDraw.rgb(0x5BF2FF);
    private static final float[] TD_TEAL = FxDraw.rgb(0x0B7A8C);
    private static final float[] TD_FIRE = FxDraw.rgb(0xFF7A14);
    private static final float[] TD_EMBER = FxDraw.rgb(0xD8240A);

    // Todo's cursed energy bursting from both shoulders: two fans of flame streaks thrown back and up (each streak with its own
    // length, lean and flicker; white at the shoulder, cyan, dark teal at the tip). On the wind-up, a fire glow behind him.
    private static void todoFlame(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || !DbFx.pass) {
            return;
        }
        Body b = body(e, partial);
        float k = f.a * out;
        boolean dash = f.b == 1;
        Vector3f back = vec(b.fwd).negate();
        Vector3f side = vec(b.right);
        for (int sd = -1; sd <= 1; sd += 2) {
            Vector3f sh = rel(b.point(-0.05, sd * 0.3, 1.55));
            Vector3f axis = new Vector3f(back).mul(dash ? 1f : 0.75f).add(0, dash ? 0.35f : 0.6f, 0).add(new Vector3f(side).mul(sd * 0.45f)).normalize();
            Vector3f[] bs = basis(axis);
            for (int i = 0; i < 22; i++) {
                int s = f.id * 7 + sd * 101 + i;
                float h = hash(s, i, 1);
                float fl = 0.8f + 0.2f * Mth.sin(age * (0.7f + 0.5f * h) + h * 40);
                float len = (0.5f + 1.5f * h * h) * k * fl * (dash ? 1.45f : 1);
                if (len < 0.05f) {
                    continue;
                }
                float ang = (i + hash(s, i, 2) * 0.6f) / 22f * Mth.TWO_PI;
                float spread = 0.25f + 0.35f * hash(s, i, 3);
                Vector3f d = new Vector3f(axis).add(new Vector3f(bs[0]).mul(Mth.cos(ang) * spread)).add(new Vector3f(bs[1]).mul(Mth.sin(ang) * spread)).normalize();
                Vector3f mid = at(sh, d, len * 0.5f);
                mid.y += 0.08f * len * Mth.sin(age * 0.5f + h * 9);
                Vector3f tip = at(at(sh, d, len), axis, 0.15f * len);
                float w = 0.05f + 0.05f * hash(s, i, 4);
                Vector3f[] pts = {sh, mid, tip};
                FxDraw.strip(pts, new float[] {w * 1.6f, w * 1.1f, 0}, new float[] {0, 0.5f, 1}, FxDraw.SOLID, 0, TD_TEAL, 0.5f * k, true);
                FxDraw.strip(pts, new float[] {w, w * 0.6f, 0}, new float[] {0, 0.5f, 1}, FxDraw.SOLID, 0, TD_CYAN, 0.85f * k, true);
                FxDraw.strip(new Vector3f[] {sh, at(sh, d, len * 0.35f)}, new float[] {w * 0.5f, 0}, new float[] {0, 1}, FxDraw.SOLID, 0, WHITE, 0.9f * k, true);
            }
        }
        if (!dash) {
            Vector3f c = rel(b.point(-0.9, 0, 1.0));
            bb(c, 3.4f * k, age * 0.01f, FxDraw.SOFT, 0.2f, TD_EMBER, 0.35f * k, true);
            bb(add(c, 0, -0.5f, 0), 2.2f * k, -age * 0.013f, FxDraw.SOFT, 0.35f, TD_FIRE, 0.4f * k, true);
        }
    }

    // Sparks of black lightning: each a short zigzag of its own (black, red edge) flying out from the blow and flickering out.
    private static void bfSparks(Fx f, float age) {
        Vector3f c = rel(f.pos);
        Vector3f fw = safe(vec(f.dir));
        int n = Math.max(1, (int) f.a);
        for (int i = 0; i < n; i++) {
            int s = f.id * 13 + i;
            float life = 9 + 16 * hash(s, i, 1);
            float a = age - 2 * hash(s, i, 2);
            if (a < 0 || a > life) {
                continue;
            }
            float u = a / life;
            if (u > 0.55f && hash(s, (int) (age * 2), 3) < u - 0.2f) {
                continue;
            }
            Vector3f d = new Vector3f(fw).mul(0.5f * hash(s, i, 4)).add(sphere(s, i)).normalize();
            float dist = (1.5f + 5.5f * hash(s, i, 5)) * easeOut(Math.min(1, a / life * 1.6f));
            Vector3f p = at(c, d, dist);
            p.y -= 0.6f * u * u;
            float len = (0.25f + 0.7f * hash(s, i, 6)) * (1 - u * u);
            Vector3f dd = new Vector3f(d).add(sphere(s + 9, i).mul(0.7f)).normalize();
            bfBolt(p, at(p, dd, len), s + (int) (age / 3), 0.012f + 0.016f * hash(s, i, 7), 1 - u * u * u);
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
    // Blood with body (Stark, 2026-11-14, preview forja/choso-sangue): no flat ribbon, no light stripe, nothing additive.
    // Every thread, drop and ball is a round mesh of quads with a color per vertex: a fixed scene light from above, a
    // darker rim where it turns away, a small wet glint only where the surface sends that light back, all times the light
    // of the place, so it darkens at night and in caves like any block. Alpha pass only, the half that faces the camera,
    // far quads first.

    private static final Vector3f LIGHT = new Vector3f(0.35f, 0.9f, 0.25f).normalize();
    private static float bloodK = 1;

    private record BloodQuad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float[] ca, float[] cb, float[] cc, float[] cd, float dist) {
    }

    // The light where the blood is: block light or daylight (0.25 in the dark, 1 in the sun).
    private static void bloodLight(Vector3f at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            bloodK = 1;
            return;
        }
        BlockPos bp = BlockPos.containing(DbFx.cam.x + at.x, DbFx.cam.y + at.y, DbFx.cam.z + at.z);
        float block = mc.level.getBrightness(LightLayer.BLOCK, bp) / 15f;
        float sky = mc.level.getBrightness(LightLayer.SKY, bp) / 15f * mc.level.getSkyDarken(1);
        bloodK = 0.25f + 0.75f * Math.min(1, Math.max(block, sky));
    }

    private static float[] dim(float[] col) {
        return new float[] {col[0] * bloodK, col[1] * bloodK, col[2] * bloodK};
    }

    // Value noise along a thread (clots, lumps).
    private static float vn(float x, int seed) {
        int i = Mth.floor(x);
        float fr = x - i, u = fr * fr * (3 - 2 * fr);
        return hash(seed, i, 7) * (1 - u) + hash(seed, i + 1, 7) * u;
    }

    private static float sstep(float a, float b, float x) {
        return smooth((x - a) / (b - a));
    }

    // The color of one vertex (n its normal, p where it is, relative to the camera). dry: 0 liquid, 1 hardened (dull,
    // darker, no glint).
    private static float[] bloodShade(Vector3f n, Vector3f p, float clot, float dry) {
        float il = 1 / Math.max(1.0E-4f, p.length());
        float vx = -p.x * il, vy = -p.y * il, vz = -p.z * il;
        float nl = n.dot(LIGHT);
        float d = 0.28f + 0.72f * Math.max(nl, 0);
        float br = 0.4f + 0.2f * clot, bg = 0.01f + 0.016f * clot, bl = 0.026f + 0.018f * clot;
        float r = 0.1f + (br - 0.1f) * d, g = bg * d, b = 0.012f + (bl - 0.012f) * d;
        float rim = 0.42f + 0.58f * smooth(Math.abs(n.x * vx + n.y * vy + n.z * vz) / 0.6f);
        float rx = 2 * nl * n.x - LIGHT.x, ry = 2 * nl * n.y - LIGHT.y, rz = 2 * nl * n.z - LIGHT.z;
        float sp = (float) Math.pow(Math.max(rx * vx + ry * vy + rz * vz, 0), 22) * 0.22f * (1 - dry);
        float wet = 1 - 0.45f * dry;
        return new float[] {(r * rim * wet + 0.04f * dry + 0.8f * sp) * bloodK, (g * rim * wet + 0.32f * sp) * bloodK,
                (b * rim * wet + 0.3f * sp) * bloodK};
    }

    private static void bloodRing(List<Vector3f[]> rp, List<Vector3f[]> rn, List<float[][]> rc, Vector3f c, Vector3f t, Vector3f n, Vector3f b,
            float r, float tilt, float clot, int sides, float dry) {
        Vector3f[] ps = new Vector3f[sides + 1];
        Vector3f[] ns = new Vector3f[sides + 1];
        float[][] cs = new float[sides + 1][];
        float ct = Mth.cos(tilt), st = Mth.sin(tilt);
        for (int j = 0; j <= sides; j++) {
            float a = j / (float) sides * Mth.TWO_PI, ca = Mth.cos(a), sa = Mth.sin(a);
            Vector3f dir = new Vector3f(n).mul(ca).add(b.x * sa, b.y * sa, b.z * sa);
            ps[j] = new Vector3f(c).add(dir.x * r, dir.y * r, dir.z * r);
            ns[j] = new Vector3f(dir).mul(ct).add(t.x * st, t.y * st, t.z * st).normalize();
            cs[j] = bloodShade(ns[j], ps[j], clot, dry);
        }
        rp.add(ps);
        rn.add(ns);
        rc.add(cs);
    }

    // A tube of blood through pts (relative to the camera), its own radius and clot at each point, rounded ends.
    static void bloodTube(Vector3f[] pts, float[] radii, float[] clots, int sides, float op, float dry, boolean capStart, boolean capEnd) {
        int m = pts.length;
        if (DbFx.pass || op <= 0.002f || m < 2) {
            return;
        }
        bloodLight(pts[m / 2]);
        Vector3f[] tan = new Vector3f[m];
        for (int i = 0; i < m; i++) {
            Vector3f t = new Vector3f(pts[Math.min(m - 1, i + 1)]).sub(pts[Math.max(0, i - 1)]);
            tan[i] = t.lengthSquared() < 1.0E-10f ? (i > 0 ? new Vector3f(tan[i - 1]) : v(0, 0, 1)) : t.normalize();
        }
        List<Vector3f[]> rp = new ArrayList<>();
        List<Vector3f[]> rn = new ArrayList<>();
        List<float[][]> rc = new ArrayList<>();
        Vector3f n = Math.abs(tan[0].y) < 0.9f ? v(0, 1, 0) : v(1, 0, 0);
        int caps = 2;
        for (int i = 0; i < m; i++) {
            Vector3f t = tan[i];
            n.sub(new Vector3f(t).mul(n.dot(t)));
            if (n.lengthSquared() < 1.0E-8f) {
                n = Math.abs(t.y) < 0.9f ? v(0, 1, 0) : v(1, 0, 0);
                n.sub(new Vector3f(t).mul(n.dot(t)));
            }
            n.normalize();
            Vector3f b = new Vector3f(t).cross(n).normalize();
            if (i == 0 && capStart) {
                for (int k = caps; k >= 1; k--) {
                    float th = k / (float) caps * Mth.HALF_PI;
                    bloodRing(rp, rn, rc, at(pts[0], t, -radii[0] * Mth.sin(th)), t, n, b, radii[0] * Mth.cos(th), -th, clots[0], sides, dry);
                }
            }
            bloodRing(rp, rn, rc, pts[i], t, n, b, radii[i], 0, clots[i], sides, dry);
            if (i == m - 1 && capEnd) {
                for (int k = 1; k <= caps; k++) {
                    float th = k / (float) caps * Mth.HALF_PI;
                    bloodRing(rp, rn, rc, at(pts[i], t, radii[i] * Mth.sin(th)), t, n, b, radii[i] * Mth.cos(th), th, clots[i], sides, dry);
                }
            }
        }
        List<BloodQuad> qs = new ArrayList<>();
        for (int i = 0; i + 1 < rp.size(); i++) {
            Vector3f[] p0 = rp.get(i), p1 = rp.get(i + 1), n0 = rn.get(i), n1 = rn.get(i + 1);
            float[][] c0 = rc.get(i), c1 = rc.get(i + 1);
            for (int j = 0; j < sides; j++) {
                Vector3f mid = new Vector3f(p0[j]).add(p0[j + 1]).add(p1[j + 1]).add(p1[j]).mul(0.25f);
                Vector3f nn = new Vector3f(n0[j]).add(n0[j + 1]).add(n1[j + 1]).add(n1[j]);
                // The half turned away from the camera is never drawn (the alpha pass writes no depth).
                if (nn.dot(mid) > 0.6f * mid.length()) {
                    continue;
                }
                qs.add(new BloodQuad(p0[j], p0[j + 1], p1[j + 1], p1[j], c0[j], c0[j + 1], c1[j + 1], c1[j], mid.lengthSquared()));
            }
        }
        qs.sort((x, y) -> Float.compare(y.dist(), x.dist()));
        for (BloodQuad q : qs) {
            FxDraw.quad(q.a(), q.b(), q.c(), q.d(), FxDraw.SOLID, 0, q.ca(), q.cb(), q.cc(), q.cd(), op, false);
        }
    }

    // A drop: a little capsule stretched along where it moves.
    static void bloodDrop(Vector3f c, Vector3f dir, float len, float r, float op) {
        Vector3f d = dir.lengthSquared() < 1.0E-8f ? v(0, -1, 0) : new Vector3f(dir).normalize();
        bloodTube(new Vector3f[] {at(c, d, -len / 2), at(c, d, len / 2)}, new float[] {r, r}, new float[] {0.4f, 0.4f}, 6, op, 0, true, true);
    }

    // A ball of blood that wobbles; dry hardens it (the Meteorite).
    static void bloodSphere(Vector3f c, float r, float t, float op, float dry, int seed) {
        int n = 6;
        Vector3f[] pts = new Vector3f[n + 1];
        float[] rad = new float[n + 1];
        float[] cl = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n - 0.5f;
            pts[i] = add(c, u * r * 0.25f, 0.02f * r * Mth.sin(t * 1.5f + i) * (1 - dry), 0);
            rad[i] = r * (float) Math.sqrt(1 - u * u * 0.0625f) * (1 + 0.06f * (1 - dry) * Mth.sin(t * 1.35f + i * 2));
            cl[i] = 0.3f + 0.4f * hash(seed, i, 1);
        }
        bloodTube(pts, rad, cl, 12, op, dry, true, true);
    }

    static void bloodBall(Vector3f at, float r, float t, float a, int seed) {
        if (a <= 0) {
            return;
        }
        bloodSphere(at, r * (0.4f + 0.6f * a), t, Math.min(1, a * 1.5f), 0, seed);
    }

    static void bloodDrips(List<Vector3f> pts, float t, int seed, float op, float ground) {
        for (int i = 0; i < 14; i++) {
            Vector3f p0 = pts.get(Math.min(pts.size() - 1, (int) Math.floor(hash(seed, i, 1) * pts.size())));
            float life = 11 * (0.8f + 0.4f * hash(seed, i, 4));
            float a = DbFx.fract(t / life + hash(seed, i, 2)) * life;
            Vector3f p = add(p0, 0, -0.06f - 0.01225f * a * a, 0);
            if (p.y < ground) {
                continue;
            }
            bloodDrop(p, v(0, -1, 0), 0.05f + Math.min(0.16f, 0.0147f * a), 0.028f + 0.016f * hash(seed, i, 3), op);
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
            Vector3f vel = new Vector3f(d).mul(sp).add(0, -0.01f * age, 0);
            float size = (0.07f + 0.08f * hash(seed, i, 2)) * s;
            bloodDrop(p, vel, size * 1.2f, size * 0.42f, 1 - age / 26);
        }
    }

    private static void bloodBallFx(Fx f, float age, float out) {
        Vector3f hm = rel(f.pos);
        float k = f.b;
        bloodBall(hm, f.a, age * (1 + 3 * k), smooth(age / 6) * out, 3);
    }

    // The Piercing Blood: too fast to see travelling, a thin round thread of blood that thins and breaks into beads as it
    // fades, drops falling off it.
    private static void bloodBeam(Fx f, float age) {
        if (age >= 14) {
            return;
        }
        float fo = 1 - age / 14;
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (len < 0.1f) {
            return;
        }
        Vector3f dir = new Vector3f(d).div(len);
        int n = 40;
        Vector3f[] pts = new Vector3f[n + 1];
        float[] rad = new float[n + 1];
        float[] cl = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, s = u * len;
            pts[i] = add(at(from, dir, s), 0, -0.004f * age * age * u, 0);
            float beads = fo > 0.5f ? 1 : 0.55f + 0.45f * (float) Math.pow(Math.abs(Mth.sin(s * 3.1f + age * 0.4f)), 0.6f * (1 - fo) + 0.01f);
            rad[i] = (0.075f * fo + 0.018f) * beads * smooth((s + 0.1f) / 0.4f);
            cl[i] = vn(s * 2, 51);
        }
        bloodTube(pts, rad, cl, 8, 1, 0, true, true);
        Vector3f to = at(from, dir, len);
        for (int i = 0; i < 18; i++) {
            float u = hash(i, 4, 1);
            Vector3f p = add(lerp(from, to, u * 0.6f), 0, -0.01f * age * age * hash(i, 4, 2), 0);
            bloodDrop(p, v(0, -1, 0), 0.06f + 0.01f * age, 0.03f * (0.5f + fo), 1);
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
            if (a < 14) {
                for (int j = 0; j < 12; j++) {
                    Vector3f d = sphere(i * 13 + 3, j);
                    d.y *= 0.5f;
                    d.normalize();
                    Vector3f p = at(o, d, Math.min(4.5f, a * 0.5f));
                    bloodTube(new Vector3f[] {at(p, d, -0.8f), p}, new float[] {0.045f, 0.005f}, new float[] {0.4f, 0.4f}, 6, 1 - a / 14, 0.3f,
                            true, false);
                }
            }
        }
    }

    // The blood rope (preview forja/choso-sangue): one thick round cord, pulses of blood running down it to the tip, a heavy
    // drop leading it in flight, dripping all the time. Wrapped (a = 1, b = the bound one's height) the same cord goes on in
    // tight turns round the body with the arms inside; when it ends it breaks into beads.
    private static void rope(Fx f, double now, float age, float out) {
        Vector3f hand = rel(f.pos);
        Vector3f tip = at(hand, vec(f.dir), 1);
        boolean wrapped = f.a >= 1;
        float sc = Math.max(0.6f, f.b) / 1.8f;
        float k = wrapped ? smooth((float) (now - f.changed) / 5) : 0;
        float broken = f.endAt >= 0 ? out : 1;
        Vector3f toHand = new Vector3f(hand).sub(tip);
        toHand.y = 0;
        toHand = toHand.lengthSquared() < 1.0E-6f ? v(0, 0, 1) : toHand.normalize();
        Vector3f front = new Vector3f(toHand);
        float yaw = wrapped ? bodyYawNear(tip) : Float.NaN;
        if (!Float.isNaN(yaw)) {
            front = v(-Mth.sin(yaw), 0, Mth.cos(yaw));
        }
        Vector3f side = v(front.z, 0, -front.x);
        float rx = 0.56f * sc, rz = 0.2f * sc;
        float a0 = (float) Math.atan2(toHand.dot(front), toHand.dot(side));
        Vector3f end = wrapped ? add(at(at(tip, side, Mth.cos(a0) * rx), front, Mth.sin(a0) * rz), 0, 1.25f * sc, 0) : tip;
        float len = hand.distance(end);
        int n = Mth.clamp(Math.round(len * 3.2f), 8, 60);
        Vector3f lat = new Vector3f(end).sub(hand);
        lat.set(lat.z, 0, -lat.x);
        lat = lat.lengthSquared() < 1.0E-6f ? v(1, 0, 0) : lat.normalize();
        List<Vector3f> pts = new ArrayList<>();
        List<Float> rad = new ArrayList<>();
        List<Float> cl = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, s = u * len;
            Vector3f p = lerp(hand, end, u);
            p.y += Mth.sin(u * Mth.PI) * (0.35f * (1 - k) + 0.06f);
            p = at(p, lat, Mth.sin(s * 1.6f - age * 0.75f) * 0.07f * (1 - k) * sstep(0, 0.15f, u));
            p.y += Mth.sin(s * 2.3f - age * 0.55f) * 0.03f * (1 - k);
            float pulse = (float) Math.pow(Math.max(0, Mth.sin(s * 2.2f - age * 0.9f)), 4);
            float r = 0.065f * (0.6f + 0.4f * sstep(0, 0.12f, u)) * (1 + 0.32f * pulse);
            if (!wrapped) {
                r = Math.max(r, 0.11f * sstep(1 - 0.35f / Math.max(len, 0.4f), 1, u));
            }
            if (broken < 1) {
                r *= broken * (0.55f + 0.45f * (float) Math.sqrt(Math.abs(Mth.sin(s * 5.5f))));
            }
            pts.add(p);
            rad.add(r);
            cl.add(vn(s * 2.4f - age * 0.15f, 11));
        }
        if (wrapped) {
            int m = Math.round(48 * k);
            float sq = 1 - 0.06f * smooth((float) (now - f.changed - 3) / 4);
            for (int i = 1; i <= m; i++) {
                float u = i / 48f, a = a0 - u * 3 * Mth.TWO_PI;
                float y = (1.25f - u * 0.55f) * sc + 0.02f * Mth.sin(u * 40);
                pts.add(add(at(at(tip, side, Mth.cos(a) * rx * sq), front, Mth.sin(a) * rz * sq), 0, y, 0));
                rad.add(0.058f * sc * (1 + 0.12f * Mth.sin(u * 23)) * (broken < 1 ? broken * (0.55f + 0.45f * Math.abs(Mth.sin(u * 60))) : 1));
                cl.add(vn(u * 30, 13));
            }
        }
        float[] ra = new float[rad.size()];
        float[] ca = new float[cl.size()];
        for (int i = 0; i < ra.length; i++) {
            ra[i] = rad.get(i);
            ca[i] = cl.get(i);
        }
        bloodTube(pts.toArray(new Vector3f[0]), ra, ca, 8, Math.min(1, out * 1.5f), 0, true, true);
        bloodDrips(pts, age, 7, out, wrapped ? tip.y + 0.02f : tip.y - 1.2f);
    }

    // The body yaw (radians) of the living thing standing at a point (relative to the camera), NaN if none.
    private static float bodyYawNear(Vector3f at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Float.NaN;
        }
        Vec3 w = DbFx.cam.add(at.x, at.y, at.z);
        LivingEntity best = null;
        double bd = 0.8;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(w, w).inflate(1.2))) {
            double d = e.position().distanceTo(w);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best == null ? Float.NaN : best.yBodyRot * Mth.DEG_TO_RAD;
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
        bloodLight(c);
        float[] dark = dim(BLOOD_D), body = dim(BLOOD), gloss = dim(GLOSS);
        for (int i = 0; i < 4; i++) {
            float ang = hash(f.id, i, 1) * Mth.TWO_PI;
            float d = i == 0 ? 0 : r * (0.35f + 0.3f * hash(f.id, i, 2));
            float s = r * (i == 0 ? 1 : 0.45f + 0.35f * hash(f.id, i, 3));
            Vector3f p = add(c, Mth.cos(ang) * d, 0.001f * i, Mth.sin(ang) * d);
            dsk(p, UP, s * 2.15f, FxDraw.SOFT, 0.95f, dark, out, false);
            dsk(add(p, 0, 0.004f, 0), UP, s * 1.8f, FxDraw.SOFT, 0.95f, body, out, false);
        }
        dsk(add(c, r * 0.2f, 0.01f, -r * 0.15f), UP, r * 0.5f, FxDraw.SOFT, 0.5f, gloss, 0.35f * out * dry, false);
    }

    // A Piercing Blood that pours for a while and sweeps: a round jet of blood under pressure, lumps running out along it,
    // wider where it bursts from the palms (only the big one), drops coming off it and a spray of dark drops where it ends.
    // Thick (a > 0.5) is the Perfurante Maximo.
    private static void bloodSweep(Fx f, float age, float out) {
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (len < 0.1f) {
            return;
        }
        Vector3f dir = new Vector3f(d).div(len);
        Vector3f[] bs = basis(dir);
        boolean big = f.a > 0.5f;
        float R = f.a * 0.56f * (0.6f + 0.4f * out);
        float amp = 0.08f * f.a;
        int n = big ? 64 : 32;
        Vector3f[] pts = new Vector3f[n + 1];
        float[] rad = new float[n + 1];
        float[] cl = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, s = u * len;
            float lump = vn(s * 0.9f - age * 1.3f, 41) * 0.6f + vn(s * 2.6f - age * 2f, 43) * 0.4f;
            float flare = big ? 1 + 0.55f * (float) Math.exp(-s * 1.4f) : 1;
            float w = smooth(s / 3);
            pts[i] = at(at(at(from, dir, s), bs[0], amp * Mth.sin(s * 0.7f - age * 1.1f) * w), bs[1], amp * 0.8f * Mth.sin(s * 0.55f - age * 0.85f + 1) * w);
            rad[i] = R * (0.82f + 0.36f * lump) * flare * smooth((s + 0.25f) / 0.6f);
            cl[i] = lump;
        }
        bloodTube(pts, rad, cl, big ? 12 : 8, Math.min(1, out * 1.5f), 0, true, true);
        int drops = big ? 26 : 10;
        for (int i = 0; i < drops; i++) {
            float ph = DbFx.fract(age * (0.05f + 0.04f * hash(f.id, i, 2)) + hash(f.id, i, 3));
            Vector3f p = add(at(from, dir, len * hash(f.id, i, 1)), 0, -ph * ph * 2.2f, 0);
            float r = (0.025f + 0.03f * hash(f.id, i, 4)) * (big ? 2 : 1);
            bloodDrop(p, v(0, -1, 0), r * 2 + ph * 0.25f, r, out * (1 - ph * 0.6f));
        }
        Vector3f end = at(from, dir, len);
        for (int i = 0; i < (big ? 12 : 6); i++) {
            float ph = DbFx.fract(age * 0.09f + hash(f.id + 7, i, 1));
            Vector3f sd = sphere(f.id + (int) Math.floor(age * 0.09f + hash(f.id + 7, i, 1)) * 13, i).add(new Vector3f(dir).mul(-0.6f)).normalize();
            Vector3f p = add(at(end, sd, ph * (big ? 2.4f : 1.2f)), 0, -ph * ph * 0.8f, 0);
            float r = (big ? 0.08f : 0.035f) * (1 - ph * 0.5f);
            bloodDrop(p, new Vector3f(sd).add(0, -ph * 1.6f, 0), r * 2.6f, r, out * (1 - ph));
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
            float wd = (0.022f + 0.02f * hash(f.id, i, 8)) * (1 - 0.5f * s0 * s0);
            Vector3f[] pts = new Vector3f[5];
            float[] rad = new float[5];
            float[] cl = new float[5];
            for (int j = 0; j < 5; j++) {
                pts[j] = dropAt(base, c, a0, y0, r0, turn, Math.max(0, s0 - 0.08f * (4 - j) / 4));
                rad[j] = wd * (0.4f + 0.6f * j / 4);
                cl[j] = 0.4f;
            }
            bloodTube(pts, rad, cl, 5, k, 0, true, true);
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
            bloodTube(new Vector3f[] {p, mid, tip}, new float[] {w * 0.55f, w * 0.32f, 0.004f}, new float[] {0.3f, 0.5f, 0.6f}, 7, 1, 0.25f, true, false);
        }
        if (age < 10) {
            bloodSplash(add(base, 0, 0.2f, 0), age, f.id, 10, f.a / 1.4f, UP);
        }
    }

    // The Blood Meteorite: a ball of hardened blood (dark, dull, no glint) with a short dark trail.
    private static void meteor(Fx f, float age, float out) {
        Vector3f p = rel(f.pos);
        float r = f.a;
        bloodSphere(p, r, age, out, 1, f.id);
        Vec3[] tr = f.trail.toArray(new Vec3[0]);
        if (tr.length >= 2) {
            Vector3f[] pts = new Vector3f[tr.length];
            float[] rad = new float[tr.length];
            float[] cl = new float[tr.length];
            for (int i = 0; i < tr.length; i++) {
                float u = (i + 1) / (float) tr.length;
                pts[i] = rel(tr[i]);
                rad[i] = r * 0.25f * u;
                cl[i] = 0.3f;
            }
            bloodTube(pts, rad, cl, 8, out, 0.5f, false, true);
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
                    0.4f, STEAM_RED, 0.2f * k * (1 - ph), false);
        }
        for (int side = -1; side <= 1; side += 2) {
            Vector3f sh = rel(b.point(0, -0.37 * side, 1.38));
            Vector3f hand = rel(b.point(0.05, -0.4 * side, 0.75));
            for (int j = 0; j < 3; j++) {
                Vector3f off = vec(b.fwd).mul(0.13f).add(vec(b.right).mul((j - 1) * 0.05f));
                rib(new Vector3f(sh).add(off), new Vector3f(hand).add(off), 0.02f, FxDraw.BEAM, 0.9f, BLOOD_D, 0.8f * k, false);
            }
        }
    }

    // ---------- Nanami ----------

    // The 7:3 ruler over the target (same as the preview): a white ruler across the chest on a dark backing, ten
    // marks (the 0, 5 and 10 taller), drawn out from the left; then the gold ring, cross and dot at 70% and "7:3"
    // above it in the Minecraft font (5x7 pixels, shadow one pixel down-right, the 3 in gold).
    private static final String[][] GLYPH = {
            {"#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."},
            {".", "#", ".", ".", ".", "#", "."},
            {".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."}};
    private static final int[] GLYPH_X = {1, 7, 9};
    private static final float[] RULER_INK = FxDraw.rgb(0x14141A);
    private static final float[] SHADOW = FxDraw.rgb(0x3F3F3F);
    private static final float[] SHADOW_3 = FxDraw.rgb(0x3F3315);
    private static final float[] LABEL_3 = FxDraw.rgb(0xFFCF4A);

    private static void ratio(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        float a = smooth(age / 3) * out;
        float s = clamp(e.getBbHeight() / 1.8f, 1, 2.5f);
        Vector3f feet = rel(e.getPosition(partial));
        Vector3f toCam = safe(v(-feet.x, 0, -feet.z));
        Vector3f right = safe(new Vector3f(UP).cross(toCam));
        Vector3f c = add(at(feet, toCam, e.getBbWidth() * 0.5f + 0.15f), 0, e.getBbHeight() * 0.66f, 0);
        float k = age / 12;
        float len = Math.min(1, k * 1.6f);
        float w = 1.15f * s;
        Vector3f left = at(c, right, -w / 2);
        if (!DbFx.pass) {
            panel(left, right, toCam, 0, -0.05f * s, (w + 0.05f * s) * len, 0.1f * s, 0, RULER_INK, 0.85f * a);
            panel(left, right, toCam, 0, -0.025f * s, w * len, 0.05f * s, 0.01f, WHITE, a);
            for (int i = 0; i <= 10; i++) {
                float g = clamp((len - i / 10f) * 8, 0, 1);
                if (g <= 0) {
                    continue;
                }
                float h = (i % 5 == 0 ? 0.24f : 0.12f) * (0.85f + 0.3f * hash(i, f.id, 2)) * 1.3f * s * g;
                float y = (hash(i, f.id, 5) - 0.5f) * 0.02f * s;
                panel(left, right, toCam, w * i / 10 - 0.015f * s, y - h / 2, 0.03f * s, h, 0.012f, WHITE, a);
            }
        }
        float pk = clamp((k - 0.55f) * 3, 0, 1);
        if (pk <= 0) {
            return;
        }
        Vector3f pt = at(at(c, right, w * 0.2f), toCam, 0.02f);
        float ps = pk * (1 + 0.12f * Mth.sin(age * 0.9f)) * s;
        dsk(pt, toCam, 0.2f * ps, FxDraw.RING, 0.3f, GOLD, a, false);
        dsk(pt, toCam, 0.09f * ps, FxDraw.DISC, 0.9f, WHITE, a, false);
        bb(pt, 0.45f * ps, 0, FxDraw.SOFT, 0.6f, GOLD, 0.6f * a, true);
        if (!DbFx.pass) {
            panel(pt, right, toCam, -0.21f * ps, -0.006f * ps, 0.42f * ps, 0.012f * ps, 0.004f, GOLD, a);
            panel(pt, right, toCam, -0.006f * ps, -0.21f * ps, 0.012f * ps, 0.42f * ps, 0.004f, GOLD, a);
            // the label: 16x9 pixels over 0.4x0.225, centred 0.3 above the point
            float u = 0.025f * s * pk * (1 + 0.15f * Math.max(0, 1 - pk * 1.5f));
            Vector3f lc = add(pt, 0, (0.3f + 0.03f * Mth.sin(age * 0.15f)) * s, 0);
            for (int pass = 0; pass < 2; pass++) {
                for (int gi = 0; gi < 3; gi++) {
                    float[] col = pass == 0 ? (gi == 2 ? SHADOW_3 : SHADOW) : (gi == 2 ? LABEL_3 : WHITE);
                    String[] rows = GLYPH[gi];
                    for (int j = 0; j < rows.length; j++) {
                        for (int i = 0; i < rows[j].length(); i++) {
                            if (rows[j].charAt(i) != '#') {
                                continue;
                            }
                            float cx = GLYPH_X[gi] + i + (pass == 0 ? 1 : 0);
                            float cy = j + (pass == 0 ? 2 : 1);
                            panel(lc, right, toCam, (cx - 8) * u, (4.5f - cy - 1) * u, u, u, 0.03f + pass * 0.006f, col, a);
                        }
                    }
                }
            }
        }
    }

    // A flat rectangle facing the camera: x along right, y up, from (x, y) with size (w, h), pushed toward the camera.
    private static void panel(Vector3f o, Vector3f right, Vector3f toCam, float x, float y, float w, float h, float lift, float[] col, float op) {
        if (op <= 0.002f || w <= 0 || h <= 0) {
            return;
        }
        Vector3f b = at(at(o, right, x), toCam, lift).add(0, y, 0);
        Vector3f r = new Vector3f(right).mul(w);
        Vector3f p1 = new Vector3f(b).add(r);
        FxDraw.quad(b, p1, add(p1, 0, h, 0), add(b, 0, h, 0), FxDraw.SOLID, 0, col, op, false);
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

    private static final float[] NB_INK = FxDraw.rgb(0x04050B);
    private static final float[] NB_RIM = FxDraw.rgb(0x3D8BFF);
    private static final float[] LK_INK = FxDraw.rgb(0x1C0409);
    private static final float[] LK_RIM = FxDraw.rgb(0xFF2D3F);
    private static final float[] NAIL_M = FxDraw.rgb(0xA3A9B2);
    private static final float[] NAIL_H = FxDraw.rgb(0x6D727C);
    private static final float NAIL_LEN = 0.42f;

    // The preview's makeNail: shaft, point and a flat head; the point at `tip`, pointing along dir. No glow of its own.
    static void nbNail(Vector3f tip, Vector3f dir, float len, float a) {
        if (a <= 0) {
            return;
        }
        Vector3f d = safe(dir);
        Vector3f head = at(tip, d, -len * 0.93f);
        rib(head, at(tip, d, -len * 0.12f), 0.034f, FxDraw.SOLID, 0, NAIL_M, a, false);
        rib(at(tip, d, -len * 0.18f), tip, 0.02f, FxDraw.SOLID, 0, NAIL_M, a, false);
        rib(at(head, d, -0.009f), at(head, d, 0.009f), 0.104f, FxDraw.SOLID, 0, NAIL_H, a, false);
    }

    // A nail (the preview's flying nail with TRAIL_V/TRAIL_F, souls_ki kind 19): the sheath of ink behind it while it flies,
    // gone in 0.15 s once it sticks. Stuck: the point 0.14 into the body (pos = where it went in).
    private static void nailFx(Fx f, float age, float out) {
        Vector3f dir = safe(vec(f.dir));
        Vector3f tip = at(rel(f.pos), dir, f.a < 1 ? 0 : 0.14f);
        nbNail(tip, dir, NAIL_LEN, out);
        float since = f.a < 1 ? 0 : (float) (ScreenFx.now() - f.changed);
        float fade = f.a < 1 ? 1 : 1 - since / 3f;
        Vec3 first = f.trail.peekFirst();
        if (fade > 0 && first != null && f.b < 1 && DbFx.pass && KiMesh.ready()) {
            float ran = (float) first.distanceTo(f.pos);
            KiMesh.P p = KiMesh.p(KiMesh.NAIL_TRAIL);
            p.len = Math.max(0.01f, Math.min(f.a < 1 ? 3.2f : 2.4f, ran));
            p.w = 0.1f + 0.03f * Math.floorMod(f.id, 3);
            p.seed = hash(f.id, 3, 19) * 20;
            p.time = age / 20f;
            p.alpha = fade * out;
            p.color = NB_INK;
            p.color2 = NB_RIM;
            KiMesh.queue(KiMesh.Mesh.TUBE, KiMesh.along(at(tip, dir, 0.02f), new Vector3f(dir).negate()), p, KiMesh.BOTH, 6);
        }
    }

    // The Hairpin's burst (BURST_V/BURST_F, kind 21): a = radius, b = life (s), c = 1 red.
    private static void nbBurst(Fx f, float age) {
        float k = age / Math.max(1, f.b * 20);
        if (k >= 1 || !DbFx.pass || !KiMesh.ready()) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.NB_BURST);
        p.life = k;
        p.seed = hash((int) (f.pos.x * 31 + f.pos.z * 17 + f.pos.y * 7), (int) f.born, 21) * 20;
        p.color = f.c == 1 ? LK_INK : NB_INK;
        p.color2 = f.c == 1 ? LK_RIM : NB_RIM;
        KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(rel(f.pos), f.a), p, KiMesh.FRONT, 7);
    }

    // One barbed thorn (THORN_V/THORN_F, kind 20), from `from` along dir; t in seconds.
    private static void thorn(Vector3f from, Vector3f dir, float len, float r, float t, float life, float grow, float bend, float roll, float seed) {
        if (t < 0 || t > life) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.THORN);
        p.len = len;
        p.w = r;
        p.seed = seed;
        p.time = t;
        p.growth = DbFx.easeOut(Math.min(1, t / grow));
        p.bend = bend;
        p.life = smooth((t - life * 0.4f) / (life * 0.6f));
        p.color = NB_INK;
        p.color2 = NB_RIM;
        KiMesh.queue(KiMesh.Mesh.TUBE, KiMesh.along(from, dir).rotateY(roll), p, KiMesh.BOTH, 7);
    }

    // Resonance on one body (the preview's resonate): a main thorn tearing out the back, branches each with its own length,
    // curve and thickness, a small burst where the nail was. a = power; under 0.3 the ult's hit coming back (one short thorn).
    private static void thorns(Fx f, float age) {
        if (!DbFx.pass || !KiMesh.ready()) {
            return;
        }
        float t = age / 20f, pow = f.a;
        int id = f.id;
        Vector3f at = rel(f.pos);
        Vector3f back = safe(vec(f.dir));
        Vector3f main = safe(add(back, 0, 0.15f, 0));
        if (pow < 0.3f) {
            thorn(at, safe(add(back, 0, 0.2f, 0)), 1.5f, 0.11f, t, 0.6f, 0.07f, 0.1f, 0, hash(id, 1, 20) * 20);
            burstAt(at, 0.3f, 0.35f, t, id);
            return;
        }
        thorn(at, main, 2.6f + 1.6f * pow, 0.3f + 0.1f * pow, t, 1.0f + 0.3f * pow, 0.07f, 0.12f, 0, hash(id, 1, 20) * 20);
        Vector3f right = safe(new Vector3f(back).cross(UP));
        int n = 2 + Math.round(pow * 2);
        for (int i = 0; i < n; i++) {
            float a = (i / (float) n) * Mth.TWO_PI + hash(id, i, 2) * 0.9f;
            Vector3f side = new Vector3f(right).mul(Mth.cos(a)).add(new Vector3f(UP).mul(Mth.sin(a) * 0.8f + 0.2f));
            Vector3f d = safe(new Vector3f(main).mul(0.6f + 0.5f * hash(id, i, 3)).add(side.mul(0.8f + 0.5f * hash(id, i, 4))));
            float r = hash(id, i, 5);
            thorn(at, d, 0.9f + 1.3f * r * (0.6f + 0.4f * pow), 0.1f + 0.08f * hash(id, i, 6), t, 0.7f + 0.4f * hash(id, i, 7), 0.06f + 0.05f * hash(id, i, 8),
                    (hash(id, i, 9) - 0.5f) * 0.6f, hash(id, i, 10) * 6.28f, hash(id, i, 11) * 20);
        }
        burstAt(at, 0.2f + 0.08f * pow, 0.4f, t, id);
    }

    private static void burstAt(Vector3f at, float r, float life, float t, int seed) {
        if (t > life) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.NB_BURST);
        p.life = t / life;
        p.seed = hash(seed, 7, 21) * 20;
        p.color = NB_INK;
        p.color2 = NB_RIM;
        KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(at, r), p, KiMesh.FRONT, 7);
    }

    // The ult's red thread (LINK_V/LINK_F, kind 22) from her palm to the nail in someone; a hit coming back runs it as a pulse.
    private static void link(Fx f, double now, float age, float out) {
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (len < 0.1f || !DbFx.pass || !KiMesh.ready()) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.NB_LINK);
        p.len = len;
        p.w = 0.035f;
        p.seed = hash(f.id, 2, 22) * 20;
        p.time = age / 20f;
        p.growth = smooth(age / 6f);
        float since = (float) (now - f.changed);
        p.pulse = f.a > 0 && since < 5 ? since / 4f : -1;
        p.life = 1 - out;
        p.color = LK_INK;
        p.color2 = LK_RIM;
        KiMesh.queue(KiMesh.Mesh.TUBE, KiMesh.along(rel(f.pos), d), p, KiMesh.BOTH, 6);
    }

    // The hammer in her right hand (the preview's makeNobara hammer and swingArm): raised over the head, swung through
    // when a turns 1, then brought down.
    private static final float[] WOOD = FxDraw.rgb(0x7B5232);
    private static final float[] IRON = FxDraw.rgb(0x3B3E46);
    private static final float[] IRON_FACE = FxDraw.rgb(0x9AA0AA);

    private static void hammer(Fx f, double now, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || DbFx.pass) {
            return;
        }
        float th;
        if (f.a < 1) {
            th = 3.0f * smooth(age / 5.6f);
        } else {
            float k = smooth((float) (now - f.changed) / 1.8f);
            th = 3.0f - 2.2f * k * k;
            th += (0.9f - th) * smooth(((float) (now - f.changed) - 2) / 6);
        }
        Body b = body(e, partial);
        float s = (float) b.s();
        Vector3f fw = vec(b.fwd()), side = vec(b.right()).negate(), up = v(0, 1, 0);
        Vector3f arm = new Vector3f(up).mul(-Mth.cos(th)).add(new Vector3f(fw).mul(Mth.sin(th)));
        Vector3f handle = new Vector3f(up).mul(Mth.sin(th)).add(new Vector3f(fw).mul(Mth.cos(th)));
        Vector3f hand = at(rel(shoulder(b, 1)), arm, 0.66f * s);
        doll(at(hand, handle, 0.22f * s), side, handle, arm, 0, 0, 0, 0.025f * s, 0.25f * s, 0.025f * s, WOOD, out);
        Vector3f head = at(at(hand, handle, 0.47f * s), arm, -0.02f * s);
        doll(head, side, handle, arm, 0, 0, 0, 0.05f * s, 0.055f * s, 0.13f * s, IRON, out);
        doll(at(head, arm, 0.12f * s), side, handle, arm, 0, 0, 0, 0.055f * s, 0.06f * s, 0.025f * s, IRON_FACE, out);
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
        nbNail(tip, new Vector3f(fw).negate(), 0.2f, op);
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

    // The voice (forja/inumaki-kit VOICE_F): ONE open cone from the mouth, its ink rings born as the front passes and
    // tearing as they die (souls_ki kind 17), and the word written in the air in the kanji font. Styles (Inumaki.V_*):
    // {r0, speed (blocks/s), life (s), ring density, word size (blocks per em), word life (s), word flight (blocks), lilac}.
    private static final float[][] VOICE_STYLE = {
            {0.12f, 70, 0.45f, 0.9f, 0.43f, 0.9f, 1.6f, 0},      // an order
            {0.12f, 22, 0.8f, 0.6f, 0.43f, 1.3f, 1.6f, 1},       // "Sleep": slower, lighter
            {0.35f, 34, 0.4f, 1.3f, 0, 0, 0, 0},                 // "Get crushed": the weight from above
            {0.3f, 24, 0.9f, 0.55f, 0.89f, 1.4f, 2.6f, 0},       // through the megaphone
            {0.3f, 24, 0.9f, 0.55f, 0.89f, 1.4f, 2.6f, 1},       // "Sleep" through the megaphone
            {0.3f, 32, 0.7f, 0.75f, 1.19f, 1.4f, 3.5f, 0},       // "Blow away"
            {0, 0, 0, 0, 0.5f, 0.9f, 1.6f, 0}};                  // the word alone
    private static final String[] WORD_TEXT = {"", "止まれ", "潰れろ", "眠れ", "吹っ飛べ"};
    private static final float[] VOICE_INK = FxDraw.rgb(0x140B20);
    private static final float[] VOICE_RIM = FxDraw.rgb(0x8F5BFF);
    private static final float[] SLEEP_INK = FxDraw.rgb(0x2A2142);
    private static final float[] SLEEP_RIM = FxDraw.rgb(0xC4B2FF);

    private static void voice(Fx f, float age) {
        int style = Mth.clamp((int) f.b, 0, VOICE_STYLE.length - 1);
        float[] st = VOICE_STYLE[style];
        float t = age / 20f;
        Vector3f from = rel(f.pos);
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (st[1] > 0 && len > 0.1f && t < len / st[1] + st[2] && DbFx.pass && KiMesh.ready()) {
            KiMesh.P p = KiMesh.p(KiMesh.VOICE);
            p.len = len;
            p.r0 = st[0];
            p.r1 = Math.max(st[0], f.a);
            p.flow = st[1];
            p.life = st[2];
            p.pitch = st[3];
            p.time = t;
            p.seed = Math.floorMod(f.id * 7 + (int) (f.born * 3), 97) * 0.093f;
            p.color = st[7] > 0 ? SLEEP_INK : VOICE_INK;
            p.color2 = st[7] > 0 ? SLEEP_RIM : VOICE_RIM;
            KiMesh.queue(KiMesh.Mesh.TUBE, KiMesh.along(from, d), p, KiMesh.BOTH, 6);
        }
        int w = (int) f.c;
        if (w > 0 && w < WORD_TEXT.length && st[4] > 0) {
            Vector3f dir = len > 0.1f ? new Vector3f(d).div(len) : v(0, 0, 1);
            word(WORD_TEXT[w], from, dir, t, st[4], st[5], st[6], f.id + w);
        }
    }

    // A word in the air (the preview's makeWord): flies `fly` blocks along dir easing out, rises a little, grows in,
    // shakes like a drawing on 12 fps, fades at the end. Facing back toward the speaker. em: blocks per em.
    private record Word(String text, Vector3f at, float yaw, float em, float alpha) {
    }

    private static final List<Word> WORDS = new ArrayList<>();
    private static final net.minecraft.resources.ResourceLocation KANJI = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, "kanji");

    private static void word(String text, Vector3f from, Vector3f dir, float t, float em, float life, float fly, int seed) {
        float k = t / life;
        if (k >= 1 || k < 0) {
            return;
        }
        int step = (int) Math.floor(t * 12);
        Vector3f j = v(hash(step, 1, seed) - 0.5f, hash(step, 2, seed) - 0.5f, 0).mul(0.06f);
        float e3 = 1 - (1 - k) * (1 - k) * (1 - k);
        float k2 = Math.min(1, k * 2);
        float grow = 0.7f + 0.5f * (1 - (1 - k2) * (1 - k2) * (1 - k2));
        float a = 1 - smooth((k - 0.65f) / 0.35f);
        Vector3f at = at(from, dir, fly * e3).add(0, 0.35f + 0.1f * k, 0).add(j);
        Vector3f flat = v(-dir.x, 0, -dir.z);
        if (flat.lengthSquared() < 1.0E-4f) {
            flat = v(-FxDraw.left().z, 0, FxDraw.left().x);
        }
        flat.normalize();
        if (DbFx.pass) {
            // the violet glow round the letters (the preview's canvas shadow)
            bb(at, em * 3.4f * grow, 0, FxDraw.SOFT, 0.15f, VOICE_RIM, 0.45f * a, true);
        } else {
            WORDS.add(new Word(text, at, (float) Math.atan2(flat.x, flat.z), em * grow, a));
        }
    }

    // Drawn after the effects (FxWorld, with the Mafuba's seal): dark letters with a white outline, the mod's kanji font.
    static void drawWords() {
        if (WORDS.isEmpty()) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.Font font = mc.font;
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Word w : WORDS) {
            int alpha = (int) (Mth.clamp(w.alpha(), 0, 1) * 255);
            if (alpha < 8) {
                continue;
            }
            com.mojang.blaze3d.vertex.PoseStack pose = new com.mojang.blaze3d.vertex.PoseStack();
            pose.translate(w.at().x, w.at().y, w.at().z);
            pose.mulPose(com.mojang.math.Axis.YP.rotation(w.yaw()));
            float k = w.em() / 11f;
            pose.scale(k, -k, k);
            net.minecraft.network.chat.Component c = net.minecraft.network.chat.Component.literal(w.text()).withStyle(st -> st.withFont(KANJI));
            font.drawInBatch8xOutline(c.getVisualOrderText(), -font.width(c) / 2f, -5.5f, alpha << 24 | 0x140B20, alpha << 24 | 0xF4F0FF,
                    pose.last().pose(), buffers, 0xF000F0);
        }
        buffers.endBatch();
        WORDS.clear();
    }

    // The snake-and-fangs mark on his mouth (the preview's sealTex, textures/fx/inumaki_seal.png), facing out of his face.
    private static final net.minecraft.resources.ResourceLocation SEAL = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, "textures/fx/inumaki_seal.png");

    private static void sealMarks(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || DbFx.pass) {
            return;
        }
        float a = smooth(age / 3) * out;
        Vec3 look = e.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        float s = e.getBbHeight() / 1.8f;
        Vector3f fw = vec(flat);
        Vector3f right = v(-fw.z, 0, fw.x);
        Vector3f c = rel(e.getEyePosition(partial).add(flat.scale(0.26 * s)).add(0, -0.2 * s, 0));
        float hw = 0.23f * s, hh = 0.115f * s;
        // seen from the front, the picture's left is his right
        FxDraw.quad(FxWorld.decal(SEAL), at(add(c, 0, hh, 0), right, hw), at(add(c, 0, hh, 0), right, -hw), at(add(c, 0, -hh, 0), right, -hw),
                at(add(c, 0, -hh, 0), right, hw), FxDraw.SOLID, 0, WHITE, a);
    }

    // "Stop": the ink rings closing on the body (forja/inumaki-kit BIND_F, souls_ki kind 18): wide, then tight on it.
    private static void holdRings(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || !DbFx.pass || !KiMesh.ready()) {
            return;
        }
        float h = e.getBbHeight();
        Vector3f feet = rel(e.getPosition(partial));
        KiMesh.P p = KiMesh.p(KiMesh.BIND);
        p.len = h * 1.15f;
        p.w = (1.5f - 0.9f * smooth(age / 4)) * Math.max(1, e.getBbWidth() / 0.6f);
        p.growth = smooth(age / 3.6f) * out;
        p.time = age / 20f;
        p.seed = Math.floorMod(f.id, 11) * 0.71f;
        p.color = VOICE_INK;
        p.color2 = VOICE_RIM;
        KiMesh.queue(KiMesh.Mesh.TUBE, KiMesh.along(add(feet, 0, -0.04f * h, 0), UP), p, KiMesh.BOTH, 6);
    }

    // "Get crushed": the ground gives way under the weight (the preview's flash, shock and cracks; a white impact
    // frame on a critical).
    private static void crush(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float r = f.a;
        if (age < 8) {
            float fo = 1 - age / 8;
            bb(add(c, 0, 0.6f, 0), (0.8f + 0.9f * smooth(age / 3)) * (f.b == 1 ? 1.4f : 1), 0, FxDraw.SOFT, 0.6f, LILAC, fo, true);
            warp(add(c, 0, 0.4f, 0), f.b == 1 ? 3f : 2.2f, 0.9f * fo, 0, age, null);
        }
        crackRing(c, age, r * 1.5f, 6, 9, 0.9f * (1 - smooth((age - 40) / 20)));
        DbFx.nrDust(c, age, 71, 12, 1.2f, DUST, 40);
    }

    private static void cough(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Vector3f m = mouthOf(e, partial);
        Vector3f fw = vec(body(e, partial).fwd);
        // each drop its own size, speed and moment (the preview's blood.spit)
        for (int i = 0; i < 14; i++) {
            float t0 = (i % 7) * 4 + hash(i, 15, 3) * 3;
            float b = age - t0;
            if (b < 0 || b > 14 || t0 > 28) {
                continue;
            }
            float sp = 0.05f + 0.1f * hash(i, 15, 4);
            Vector3f side = v(-fw.z, 0, fw.x);
            Vector3f p = add(at(at(m, side, (hash(i, 15, 1) - 0.5f) * 0.07f * b), fw, sp * b), 0, (0.03f + 0.04f * hash(i, 15, 5)) * b - 0.0175f * b * b, 0);
            bb(p, 0.04f + 0.05f * hash(i, 15, 2) * hash(i, 15, 6), 0, FxDraw.SOFT, 0.95f, BLOOD, (1 - b / 14) * out, false);
        }
    }

    // Asleep: nothing hangs over them (the preview shows only the body going limp); a faint lilac on the head.
    private static void sleep(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Vector3f head = add(rel(e.getPosition(partial)), 0, e.getBbHeight() * 0.85f, 0);
        bb(head, 0.9f * e.getBbHeight() / 1.8f, 0, FxDraw.SOFT, 0.2f, SLEEP_RIM, 0.25f * smooth(age / 8) * out, true);
    }

    // The megaphone in his right hand (the preview's model): a white horn with a dark rim and a handle, at his mouth.
    private static void megaphone(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || DbFx.pass) {
            return;
        }
        float k = smooth(age / 6);
        Vec3 look = e.getViewVector(partial);
        float s = e.getBbHeight() / 1.8f;
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 base = e.getEyePosition(partial).add(0, -0.2 * s, 0).add(look.scale(0.4 * s)).add(right.scale(0.1 * s)).add(0, -0.3 * (1 - k), 0);
        Vector3f d = vec(look).normalize();
        Vector3f u = safe(new Vector3f(d).cross(vec(right)));
        Vector3f r = vec(right);
        Vector3f b0 = rel(base);
        float[] white = FxDraw.rgb(0xE8E4DA), dark = FxDraw.rgb(0x3A3A44);
        int n = 16;
        float len = 0.5f * s, ra = 0.07f * s, rb = 0.24f * s;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            Vector3f o0 = new Vector3f(r).mul(Mth.cos(a0)).add(new Vector3f(u).mul(Mth.sin(a0)));
            Vector3f o1 = new Vector3f(r).mul(Mth.cos(a1)).add(new Vector3f(u).mul(Mth.sin(a1)));
            float light = 0.6f + 0.4f * Math.max(0, (o0.y + o1.y) * 0.5f + 0.4f);
            float[] lc = {white[0] * light, white[1] * light, white[2] * light};
            Vector3f p0 = at(b0, o0, ra), p1 = at(b0, o1, ra);
            Vector3f q0 = at(at(b0, d, len), o0, rb), q1 = at(at(b0, d, len), o1, rb);
            FxDraw.quad(p0, p1, q1, q0, FxDraw.SOLID, 0, lc, out, false);
            Vector3f w0 = at(at(b0, d, len + 0.03f * s), o0, rb + 0.025f * s), w1 = at(at(b0, d, len + 0.03f * s), o1, rb + 0.025f * s);
            FxDraw.quad(q0, q1, w1, w0, FxDraw.SOLID, 0, dark, out, false);
        }
        Vector3f g = at(b0, u, -0.06f * s);
        FxDraw.quad(at(g, r, -0.03f * s), at(g, r, 0.03f * s), at(at(g, r, 0.03f * s), u, -0.2f * s), at(at(g, r, -0.03f * s), u, -0.2f * s), FxDraw.SOLID, 0, dark, out,
                false);
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
    // ---------- Miwa, remade (forja/miwa-kit, Realista+Anime; souls_ki kinds 23-27, the page's shaders line by line) ----------
    // The preview's planes are THREE.PlaneGeometry in x/y; KiMesh's quad lies in x/z, so the page's y goes in the z column.

    private static final Map<Integer, double[]> MW_FLASH = new HashMap<>();

    private static Matrix4f plane(Vector3f c, Vector3f ax, float hx, Vector3f n, Vector3f ay, float hy) {
        return new Matrix4f(ax.x * hx, ax.y * hx, ax.z * hx, 0, n.x, n.y, n.z, 0, ay.x * hy, ay.y * hy, ay.z * hy, 0, c.x, c.y, c.z, 1);
    }

    private static float seedOf(Fx f, int salt) {
        return hash((int) (f.pos.x * 31 + f.pos.z * 17 + f.pos.y * 7), (int) f.born, salt) * 20;
    }

    // The Simple Domain: the lit ring with its swirl on the ground (kind 25) and the wall of streaks rising at its edge (kind 26).
    private static void simpleDomain(Fx f, double now, float age, float out) {
        if (!DbFx.pass || !KiMesh.ready() || out <= 0) {
            return;
        }
        double[] fl = MW_FLASH.get(f.id);
        if (fl == null || fl[0] != f.dir.y) {
            fl = new double[] {f.dir.y, f.dir.y > 0 ? now : -1000};
            MW_FLASH.put(f.id, fl);
        }
        float r = f.b, t = age / 20f;
        Vector3f c = add(rel(f.pos), 0, 0.04f, 0);
        KiMesh.P p = KiMesh.p(KiMesh.MW_DOMAIN);
        p.ringMax = r;
        p.life = f.a;
        p.time = t;
        p.thresh = 1 - out;
        p.seed = (f.id % 97) * 0.37f;
        p.rot = (float) Math.atan2(-f.dir.z, f.dir.x);
        p.phase = (float) ((now - fl[1]) / 20);
        KiMesh.queue(KiMesh.Mesh.QUAD, plane(c, v(1, 0, 0), r * 1.15f, v(0, 1, 0), v(0, 0, -1), r * 1.15f), p, KiMesh.BOTH, 7);
        if (r < 1) {
            return;                                                    // the counter's small window: the ring alone
        }
        KiMesh.P w = KiMesh.p(KiMesh.MW_WALL);
        w.time = t;
        w.alpha = 0.8f * smooth(f.a) * out;
        w.seed = (f.id % 89) * 0.53f;
        w.w = r;
        KiMesh.queue(KiMesh.Mesh.TUBE, new Matrix4f().translation(rel(f.pos)).scale(r, 1.5f, r), w, KiMesh.BOTH, 7);
    }

    // The draw's arc (SLASH_F): sweeps in `sweep` s, then tears from the tail.
    private static void mwSlash(Fx f, float age) {
        if (!DbFx.pass || !KiMesh.ready()) {
            return;
        }
        float t = age / 20f;
        float sweep = f.id == 1 ? 0.12f : f.id == 2 ? 0.1f : 0.07f, life = f.id == 1 ? 0.9f : 0.45f, tail = f.id == 1 ? 1 : f.id == 2 ? 0.7f : 0.8f;
        if (t > sweep + life) {
            return;
        }
        Vector3f fwd = safe(vec(f.dir));
        Vector3f n = f.id == 0 ? new Vector3f(0, 1, 0).add(new Vector3f(fwd).mul(0.12f)).normalize() : v(0, 1, 0);
        Vector3f x = new Vector3f(fwd).sub(new Vector3f(n).mul(fwd.dot(n))).normalize();
        Vector3f y = new Vector3f(n).cross(x).normalize();
        KiMesh.P p = KiMesh.p(KiMesh.MW_SLASH);
        p.life = t / sweep;
        p.thresh = smooth((t - sweep - life * 0.25f) / (life * 0.75f));
        p.ringR = f.b;
        p.w = Math.abs(f.c);
        p.pitch = f.c < 0 ? -1 : 1;
        p.tail = tail;
        p.seed = seedOf(f, 23);
        KiMesh.queue(KiMesh.Mesh.QUAD, plane(rel(f.pos), x, f.a * 1.25f, n, y, f.a * 1.25f), p, KiMesh.BOTH, 8);
    }

    // Where a cut sits on a body: its middle, the line's direction and the side it faces (dir = angle, height, 1 front / -1 back).
    private static Vector3f[] cutFrame(LivingEntity e, Vec3 d, float partial) {
        Body b = body(e, partial);
        double side = d.z >= 0 ? 1 : -1, th = d.x;
        Vec3 mid = b.point(0.17 * side, 0, d.y);
        Vec3 along = b.right().scale(Math.cos(th)).add(0, Math.sin(th), 0).normalize();
        Vec3 face = b.fwd().scale(side);
        return new Vector3f[] {rel(mid), vec(along), vec(face), v((float) b.s(), 0, 0)};
    }

    private static void cutQuad(Vector3f[] fr, float len, float draw, float open, float fade, float time, float mark, float seed, float alpha) {
        KiMesh.P p = KiMesh.p(KiMesh.MW_CUT);
        p.len = len;
        p.w = 0.4f;
        p.life = draw;
        p.growth = open;
        p.thresh = fade;
        p.time = time;
        p.pulse = mark;
        p.seed = seed;
        p.alpha = alpha;
        Vector3f y = new Vector3f(fr[2]).cross(fr[1]).normalize();
        KiMesh.queue(KiMesh.Mesh.QUAD, plane(fr[0], fr[1], len * 0.52f, fr[2], y, 0.4f), p, KiMesh.BOTH, 9);
    }

    // CUT_F on someone: the hairline drawn in 0.05 s, opening at c s, light leaking out; sparks fly when it opens.
    private static void mwCut(Fx f, float age, float partial) {
        LivingEntity e = living(f.id);
        float t = age / 20f;
        if (e == null || t > f.b) {
            return;
        }
        Vector3f[] fr = cutFrame(e, f.dir, partial);
        float len = 0.95f * fr[3].x, k = t - f.c;
        if (DbFx.pass && KiMesh.ready()) {
            cutQuad(fr, len, Math.min(1.02f, t / 0.05f), k < 0 ? 0 : f.a * easeOut(k / 0.12f), smooth((t - f.b * 0.6f) / (f.b * 0.4f)), t, 0, seedOf(f, 24), 1);
        }
        if (k >= 0) {
            mwSparks(fr[0], fr[2], k, Math.round(18 * f.a), f.id + (int) f.born);
        }
    }

    // The ult's mark: the same line, faint and shut, waiting for the draw.
    private static void mwMark(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || !DbFx.pass || !KiMesh.ready()) {
            return;
        }
        Vector3f[] fr = cutFrame(e, new Vec3(0, 1.0, 1), partial);
        float t = age / 20f;
        cutQuad(fr, 0.75f * fr[3].x, Math.min(1.02f, t / 0.05f), 0, 0, t, 1, (f.id % 71) * 0.41f, out);
    }

    // Sparks, each its own speed, size and life (no state: all from the hash), white cooling to blue.
    private static void mwSparks(Vector3f p, Vector3f dir, float t, int n, int seed) {
        for (int i = 0; i < n; i++) {
            float life = 0.2f + 0.4f * hash(seed, i, 1);
            if (t > life) {
                continue;
            }
            float sp = 3 + 6 * hash(seed, i, 2);
            Vector3f vel = new Vector3f(dir).mul(sp).add((hash(seed, i, 3) - 0.5f) * 4, hash(seed, i, 4) * 3, (hash(seed, i, 5) - 0.5f) * 4);
            float drag = (1 - (float) Math.exp(-2 * t)) / 2;
            Vector3f q = new Vector3f(p).add(new Vector3f(vel).mul(drag)).add(0, -4.5f * t * t, 0);
            float k = 1 - t / life, s = 0.03f + 0.07f * hash(seed, i, 6) * hash(seed, i, 7);
            float[] col = {MW_SPARK_B[0] + (1 - MW_SPARK_B[0]) * Math.min(1, k * 1.3f), MW_SPARK_B[1] + (1 - MW_SPARK_B[1]) * Math.min(1, k * 1.3f), 1};
            bb(q, s * 2.2f, 0, FxDraw.SOFT, 0, col, k, false);
            bb(q, s * 3.5f, 0, FxDraw.SOFT, 0, col, 0.6f * k, true);
        }
    }

    // The Line Cut's trail (STREAK_F): a ribbon standing up from start to finish, tearing from the tail.
    private static void mwStreak(Fx f, float age) {
        if (!DbFx.pass || !KiMesh.ready()) {
            return;
        }
        float t = age / 20f, life = f.b;
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (len < 0.1f || t > 0.08f + life) {
            return;
        }
        Vector3f x = new Vector3f(d).normalize(), z = new Vector3f(x).cross(UP);
        if (z.lengthSquared() < 1.0E-4f) {
            z.set(1, 0, 0);
        }
        z.normalize();
        Vector3f y = new Vector3f(z).cross(x).normalize();
        KiMesh.P p = KiMesh.p(KiMesh.MW_STREAK);
        p.len = len;
        p.life = t / 0.08f;
        p.thresh = smooth((t - 0.08f - life * 0.2f) / (life * 0.8f));
        p.seed = seedOf(f, 27);
        KiMesh.queue(KiMesh.Mesh.QUAD, plane(new Vector3f(rel(f.pos)).add(new Vector3f(x).mul(len / 2)), x, len / 2, z, y, f.a), p, KiMesh.BOTH, 8);
    }

    // The slit in the ground: the cut's line lying down, opening and staying a while.
    private static void mwGash(Fx f, float age) {
        if (!DbFx.pass || !KiMesh.ready()) {
            return;
        }
        float t = age / 20f, life = f.a;
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (len < 0.1f || t > life) {
            return;
        }
        Vector3f x = new Vector3f(d).normalize(), y = new Vector3f(UP).cross(x).normalize(), n = new Vector3f(x).cross(y).normalize();
        KiMesh.P p = KiMesh.p(KiMesh.MW_CUT);
        p.len = len;
        p.w = 0.3f;
        p.life = Math.min(1.02f, t / 0.08f);
        p.growth = 0.7f * easeOut((t - 0.06f) / 0.2f);
        p.thresh = smooth((t - life * 0.6f) / (life * 0.4f));
        p.time = t;
        p.seed = seedOf(f, 25);
        KiMesh.queue(KiMesh.Mesh.QUAD, plane(new Vector3f(rel(f.pos)).add(new Vector3f(x).mul(len / 2)), x, len / 2, n, y, 0.3f), p, KiMesh.BOTH, 7);
    }
}
