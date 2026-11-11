package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.at;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.bb;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.dsk;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.easeOut;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.fract;
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
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// The Naruto SubAlmas' effects (power/powers/sub/nr), ported from the Visualizador's Naruto tabs (v39): the Rasengan
// (the Azul's glass orb, style 4, in its shell of rings and wind), the drill of wind, the Rasenshuriken and its sphere of
// needles, nature energy, the Chidori and every bolt, the Kirin, Gaara's real sand (no glow: grains that fall, haze that
// stays, the hand, the shell, the wall, the platform), the flat opaque shadows, the paper bomb's fire in the Katon's
// style, Ino's spirit and threads, the lightning hound and Kamui's spiral. Drawn inside DbFx's passes (its bb/rib/dsk
// only draw in the pass they name); solid meshes only in the alpha pass. The Visualizador's T is the effect's own age.
final class NrFx {
    private static final float[] BLUE = FxDraw.rgb(0x3A9CFF);
    private static final float[] PALE = FxDraw.rgb(0xD8F0FF);
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] ORANGE = FxDraw.rgb(0xFF8A1A);
    private static final float[] SMOKE = {0.93f, 0.93f, 0.95f};
    private static final float[] BOLT = FxDraw.rgb(0x7CC4FF);
    private static final float[] BOLT_HOT = FxDraw.rgb(0xEEF9FF);
    private static final float[] RAIKIRI = FxDraw.rgb(0xB4E0FF);
    private static final float[] PURPLE = FxDraw.rgb(0xB05CFF);
    private static final float[] LILAC = FxDraw.rgb(0xE6CCFF);
    private static final float[] SHADOW = {0.012f, 0.012f, 0.018f};
    private static final float[] SAND = FxDraw.rgb(0xC9A76B);
    private static final float[] SAND_D = FxDraw.rgb(0x8A6A3C);
    private static final float[] DUST = FxDraw.rgb(0xCDB88E);
    private static final float[] HOT = FxDraw.rgb(0xFFF1C0);
    private static final float[] RED = FxDraw.rgb(0xFF1A2A);
    private static final float[] DARK_RED = {0.05f, 0, 0};
    private static final float[] GREEN = FxDraw.rgb(0xB8FFCF);
    private static final float[] LEAF_A = FxDraw.rgb(0x4A8A2A);
    private static final float[] LEAF_B = FxDraw.rgb(0x6AA83A);
    private static final float[] AIR = FxDraw.rgb(0xE8F4FF);
    private static final float[] SCORCH = {0.06f, 0.05f, 0.08f};
    private static final float[] KB_COR = FxDraw.rgb(0xFF4A0A);
    private static final float[] KB_COR2 = FxDraw.rgb(0xFFF3B0);
    private static final float[] KB_SMOKE = FxDraw.rgb(0x2A2224);
    private static final float[][] SAND_TONES = {FxDraw.rgb(0xC9A76B), FxDraw.rgb(0x8A6A3C), FxDraw.rgb(0xD9BF8A), FxDraw.rgb(0xA8854E),
            FxDraw.rgb(0xE6D2A4)};
    private static final float[] KUNAI = {0.15f, 0.15f, 0.18f};
    private static final float[] PAPER = FxDraw.rgb(0xEEE2C4);
    private static final float[] PAPER_RED = FxDraw.rgb(0xC01818);
    private static final float[] KAMUI_DARK = {0.06f, 0.04f, 0.08f};
    private static final float[] KAMUI_LIT = FxDraw.rgb(0x9A6AD0);
    private static final float[] KAMUI_RING = FxDraw.rgb(0x6A4A8A);
    private static final float[] STORM = {0.13f, 0.13f, 0.17f};
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final float[] POOF_SMOKE = {0.74f, 0.76f, 0.8f};
    private static final float[] DOME_BLUE = FxDraw.rgb(0x4FB0FF);
    private static final float[] ROCK = FxDraw.rgb(0x7F8187);
    private static final float[] STEEL = FxDraw.rgb(0xA3A9B2);

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
        }
    }

    private static final List<Fx> SHOTS = new ArrayList<>();
    private static final Map<Long, Fx> TRACKED = new HashMap<>();

    private NrFx() {
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    private static boolean stream(int kind) {
        return switch (kind) {
            case SubNet.RASENGAN, SubNet.DRILL, SubNet.RASENSHURIKEN, SubNet.SAGE_GATHER, SubNet.CHIDORI, SubNet.SAND_HAND, SubNet.SAND_SHELL,
                    SubNet.SAND_PLAT, SubNet.SHADOW, SubNet.BODY_SHADOW, SubNet.NECK_HAND, SubNet.SPIKES, SubNet.TAG, SubNet.SPIRIT, SubNet.THREAD,
                    SubNet.RAIJU, SubNet.KAMUI, SubNet.KK_HOUND, SubNet.KK_VORTEX, SubNet.KK_SEAL, SubNet.KK_DOGS, SubNet.GA_HAND, SubNet.GA_COFFIN, SubNet.GA_STREAM, SubNet.GA_PLAT, SubNet.GA_QUICK, SubNet.GA_WAVE, SubNet.KATON, SubNet.SHADOW_SEA, SubNet.FLARE, SubNet.HENGE -> true;
            default -> false;
        };
    }

    // Kinds that last a while on one message, following an entity (a = ticks, 0 stops).
    private static boolean timed(int kind) {
        return kind == SubNet.EYES || kind == SubNet.PARALYZE || kind == SubNet.CONFUSED;
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (kind == SubNet.KK_SUSANOO || kind == SubNet.KK_SHURIKEN) {
            KakashiSusanoo.receive(kind, id, pos, dir, a, b, c, now);
            return;
        }
        if (kind == SubNet.GA_GRAINS) {
            // a burst of loose grains: c = 0 a cone along dir (its length = strength), 1 a ring thrown out over radius b
            if (c < 0.5f) {
                GaaraSand.spray(pos, dir, (int) a, dir.length(), b, Double.NaN);
            } else {
                for (int i = 0; i < (int) a; i++) {
                    double ang = GaaraSand.rng().nextDouble() * 6.283, r = b * Math.sqrt(GaaraSand.rng().nextDouble());
                    double out = 3 + 9 * GaaraSand.rng().nextDouble();
                    GaaraSand.Grain g = GaaraSand.grain(pos.add(Math.cos(ang) * r, (0.3 + GaaraSand.rng().nextDouble() * 1.5) * GaaraSand.PU, Math.sin(ang) * r),
                            new Vec3(Math.cos(ang) * out, 2 + 8 * GaaraSand.rng().nextDouble(), Math.sin(ang) * out).scale(GaaraSand.PU), pos.y);
                    g.life = 1 + GaaraSand.rng().nextFloat();
                    g.s = (0.03f + 0.09f * GaaraSand.rng().nextFloat() * GaaraSand.rng().nextFloat()) * GaaraSand.PU;
                }
            }
            return;
        }
        if (kind == SubNet.KK_SENNEN && a < 0.5f) {
            KakashiTitle.show(now);
            return;
        }
        if (stream(kind)) {
            Fx f = TRACKED.get(key(kind, id));
            if (kind == SubNet.SHADOW && c == 1 && dir.lengthSqr() < 1.0E-6) {
                // Ended with no way given: it fades where it was.
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
            f.c = c;
            f.updated = now;
            f.trail.addLast(pos);
            while (f.trail.size() > 30) {
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
        if (kind == SubNet.SELF_SCREEN) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == id) {
                DbFx.screen((int) a, mc.player.position());
            }
            return;
        }
        SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
    }

    static boolean active() {
        return !SHOTS.isEmpty() || !TRACKED.isEmpty() || KakashiSusanoo.active() || GaaraSand.active();
    }

    static void clear() {
        SHOTS.clear();
        TRACKED.clear();
        KakashiSusanoo.clear();
        GaaraSand.clear();
    }

    private static double life(Fx f) {
        return switch (f.kind) {
            case SubNet.POOF -> 23;
            case SubNet.WIND_SPHERE -> f.b + 18;
            case SubNet.SAGE_BURST -> 42;
            case SubNet.KAWAZU -> 12;
            case SubNet.BOLT_TRAIL -> 72;
            case SubNet.PIERCE -> 16;
            case SubNet.SENBON -> 9;
            case SubNet.STORM -> f.a + 44;
            case SubNet.KIRIN -> 50;
            case SubNet.AFTERIMAGE -> 15;
            case SubNet.SAND_WALL -> 54;
            case SubNet.SAND_BURST -> 62;
            case SubNet.SAND_FLOW -> f.a + 10;
            case SubNet.KUNAI -> f.a + 1;
            case SubNet.FIRE_BOOM -> 82;
            case SubNet.MIND_WAVE -> 17;
            case SubNet.PULSE -> 9;
            case SubNet.DUST -> 52;
            case SubNet.KATON_BOOM -> 60;
            case SubNet.KK_SENNEN -> 14;
            default -> 40;
        };
    }

    private static float fadeOut(Fx f) {
        return switch (f.kind) {
            case SubNet.SHADOW, SubNet.SPIKES, SubNet.SHADOW_SEA -> 6;
            case SubNet.FLARE -> 12;
            case SubNet.SAND_SHELL -> 2;
            case SubNet.SAND_PLAT -> 4;
            case SubNet.SAND_HAND -> 10;
            case SubNet.EYES -> 6;
            case SubNet.KATON -> 2;
            case SubNet.KK_SEAL -> 12;
            case SubNet.KK_DOGS -> 3;
            case SubNet.KK_VORTEX -> 6;
            case SubNet.GA_HAND -> 14;
            case SubNet.GA_COFFIN -> f.dir.y > 0.5 ? 4 : 14;
            case SubNet.GA_STREAM -> 12;
            case SubNet.GA_PLAT -> 12;
            case SubNet.GA_QUICK -> 10;
            case SubNet.GA_WAVE -> 12;
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
                case SubNet.POOF -> poof(rel(f.pos), age, f.id + 40, f.a);
                case SubNet.WIND_SPHERE -> windSphere(f, age);
                case SubNet.SAGE_BURST -> sageBurst(rel(f.pos), age);
                case SubNet.KAWAZU -> kawazu(f, age);
                case SubNet.BOLT_TRAIL -> boltTrail(f, age);
                case SubNet.PIERCE -> pierce(f, age);
                case SubNet.SENBON -> senbon(f, age);
                case SubNet.STORM -> storm(f, age);
                case SubNet.KIRIN -> kirin(f, age);
                case SubNet.AFTERIMAGE -> afterimage(f, age);
                case SubNet.SAND_WALL -> gaWall(f, age);
                case SubNet.SAND_BURST -> sandBurstFx(f, age);
                case SubNet.SAND_FLOW -> sandFlow(f, age);
                case SubNet.KUNAI -> kunai(f, age);
                case SubNet.FIRE_BOOM -> cartoonBoom(rel(f.pos), age, f.a, (int) f.c, (float) (f.dir.x - DbFx.cam.y));
                case SubNet.MIND_WAVE -> mindWave(f, age);
                case SubNet.PULSE -> pulse(f, age);
                case SubNet.DUST -> dust(f, age);
                case SubNet.KATON_BOOM -> katonBoom(f, age);
                case SubNet.KK_SENNEN -> star(f, age);
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
                case SubNet.RASENGAN -> rasenganFx(f, age, out);
                case SubNet.DRILL -> drill(f, age, out);
                case SubNet.RASENSHURIKEN -> rasenshuriken(rel(f.pos), age, f.a * out, f.b);
                case SubNet.SAGE_GATHER -> sageGather(f, age, partial, out);
                case SubNet.EYES -> eyes(f, age, partial, out);
                case SubNet.CHIDORI -> chidoriFx(f, age, out);
                case SubNet.PARALYZE -> paralyze(f, age, partial, out);
                case SubNet.SAND_HAND -> sandHand(f, age, out);
                case SubNet.SAND_SHELL -> sandShell(f, age, out);
                case SubNet.SAND_PLAT -> sandPlat(f, age, partial, out);
                case SubNet.SHADOW -> shadowGround(f, age, out);
                case SubNet.SHADOW_SEA -> shadowSea(f, age, out);
                case SubNet.FLARE -> flare(f, age, out);
                case SubNet.BODY_SHADOW -> bodyShadow(f, age, partial, out);
                case SubNet.NECK_HAND -> neckHand(f, partial, out);
                case SubNet.SPIKES -> spikes(f, out);
                case SubNet.TAG -> tag(f, age, out);
                case SubNet.SPIRIT -> spirit(f, age, out);
                case SubNet.CONFUSED -> confused(f, age, partial, out);
                case SubNet.THREAD -> thread(rel(f.pos), rel(f.pos.add(f.dir)), f.a, f.b * out, age);
                case SubNet.KK_HOUND -> hound(f, age, out);
                case SubNet.KK_VORTEX -> bigVortex(f, age, out);
                case SubNet.KK_SEAL -> seal(f, age, out);
                case SubNet.KK_DOGS -> dogs(f, age, out);
                case SubNet.GA_HAND -> gaHand(f, age, out);
                case SubNet.GA_COFFIN -> gaCoffin(f, age, out);
                case SubNet.GA_STREAM -> gaStream(f, age, out);
                case SubNet.GA_PLAT -> gaPlat(f, age, partial, out);
                case SubNet.GA_QUICK -> gaQuick(f, age, out);
                case SubNet.GA_WAVE -> gaWave(f, age, out);
                case SubNet.KAMUI -> kamui(f, age, out);
                case SubNet.KATON -> katonBall(f, age, now, out);
                case SubNet.HENGE -> henge(f, age, now, out);
                default -> {
                }
            }
        }
    }

    // ---------- Sasuke's Katon (the approved preview v9, forja/sasuke-chidori/katon.js) ----------
    // Nothing repeated: the ball is ONE fire shell turning on a tilted axis with its tongues and its tail coming out of
    // the shell itself; the blast is ONE dome that bursts, burns inside, rises, tears and becomes its own smoke, and ONE
    // wall of fire runs round over the ground. Only the stars and the sparks are streaks, each its own way, length,
    // width and life. All of it is souls_ki (KiMesh kinds KATON and KATON_RING), queued once a frame.

    private static final float[] KATON_FLASH = FxDraw.rgb(0xFFE0B0);
    private static final float[] KATON_STAR = FxDraw.rgb(0xFFF0C0);
    private static final float[] KATON_STAR2 = FxDraw.rgb(0xFF8A40);
    private static final float[] KATON_SPARK2 = FxDraw.rgb(0xFF4010);

    private static void katonBall(Fx f, float age, double now, float out) {
        if (!DbFx.pass || !KiMesh.ready()) {
            return;
        }
        // Moved by the server once a tick: carried on along its last step so it flies smooth.
        Vec3 p = f.pos;
        if (f.endAt < 0 && f.trail.size() >= 2) {
            Iterator<Vec3> it = f.trail.descendingIterator();
            Vec3 last = it.next(), prev = it.next();
            if (last.distanceToSqr(prev) < 9) {
                p = last.add(last.subtract(prev).scale(Math.min(1.5, Math.max(0, now - f.updated))));
            }
        }
        Vector3f c = rel(p);
        KiMesh.P k = KiMesh.p(KiMesh.KATON);
        k.time = age / 20f;
        k.seed = (Math.floorMod(f.id, 97)) * 0.37f;
        k.phase = age * 0.09f;
        k.len = f.b;
        k.size = new float[] {(float) f.dir.x, (float) f.dir.y, (float) f.dir.z};
        k.spike = 0.3f;
        k.life = 1;
        k.thresh = 0;
        k.smoke = 0;
        k.up = 0;
        k.growth = 0;
        k.ringR = -1.0E4f;
        k.alpha = out;
        KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(c, f.a), k, KiMesh.BOTH, 18);
        // Its glow: the orange halo (the Supernova sun's).
        KiMesh.P h = KiMesh.p(KiMesh.SUN_HALO);
        h.time = age / 20f;
        h.alpha = 0.35f * out;
        KiMesh.queue(KiMesh.Mesh.HALO, KiMesh.sphere(c, f.a * 2f), h, KiMesh.BACK, 17);
    }

    private static void katonBoom(Fx f, float age) {
        float x = age / 20f, tk = age, xr = f.a, sc = xr / 4;
        Vector3f c = rel(f.pos);
        float ground = (float) (f.dir.x - DbFx.cam.y);
        int seed = (int) f.c;
        if (DbFx.pass && KiMesh.ready()) {
            if (x < 2.8f) {
                float r = xr * (0.3f + 0.7f * easeOut(Math.min(1, x / 0.3f))) * (1 + 0.12f * x);
                KiMesh.P k = KiMesh.p(KiMesh.KATON);
                k.time = x;
                k.seed = seed * 0.29f;
                k.phase = x * 0.4f;
                k.len = 0;
                k.size = new float[] {0, 1, 0};
                k.spike = 0.32f + 0.15f * x;
                k.life = 0.92f - 0.92f * smooth((x - 0.15f) / 1.25f);
                k.smoke = 1;
                k.thresh = 1.1f * smooth((x - 0.45f) / 1.9f);
                k.up = 0.5f * x * x;
                k.growth = 0.12f * x;
                k.alpha = 1 - smooth((x - 2.2f) / 0.6f);
                k.ringR = ground + 0.03f;
                k.glow = k.life > 0.3f;
                KiMesh.queue(KiMesh.Mesh.BLAST, KiMesh.sphere(new Vector3f(c.x, ground, c.z), r), k, KiMesh.BOTH, 17);
            }
            if (x < 1.4f) {
                float rr = (0.6f + 6.4f * easeOut(Math.min(1, x / 0.7f))) * sc, hh = 1.7f * sc * (1 - smooth((x - 0.35f) / 1.0f));
                if (hh > 0.02f) {
                    KiMesh.P k = KiMesh.p(KiMesh.KATON_RING);
                    k.time = x;
                    k.seed = seed * 0.41f + 3;
                    k.life = 1 - 0.45f * smooth(x / 1.2f);
                    k.thresh = 0.75f * smooth((x - 0.45f) / 0.9f);
                    k.ringR = -1.0E4f;
                    k.alpha = 1;
                    KiMesh.queue(KiMesh.Mesh.TUBE, new org.joml.Matrix4f().translation(c.x, ground, c.z).scale(rr, hh, rr), k, KiMesh.BOTH, 16);
                }
            }
        }
        if (tk < 4) {
            float q = tk / 4;
            DbFx.flashBall(new Vector3f(c.x, ground + 1, c.z), xr * (0.4f + 1.6f * Mth.sqrt(q)) * 0.6f, 0.6f * (1 - q), KATON_FLASH, 2.7f);
        }
        // The stars: rays out of the burst, each its own way, length and width.
        if (tk < 12) {
            float k = 1 - tk / 12;
            for (int i = 0; i < 16; i++) {
                Vector3f d = sphere(811 + seed, i);
                d.y = Math.abs(d.y) * 0.8f + 0.1f;
                d.normalize();
                float len = xr * 2.6f * (0.35f + 0.65f * hash(i, 811, 2)) * easeOut(tk / 6), w = 0.08f + 0.16f * hash(i, 811, 4);
                Vector3f a = at(c, d, xr * 0.35f);
                rib(a, at(a, d, len), w * k, FxDraw.STREAK, 0.9f, mix(KATON_STAR, KATON_STAR2, hash(i, 811, 5)), 0.8f * k, true);
            }
        }
        // The sparks: thrown out and falling, each its own speed, length and life.
        if (tk < 26) {
            for (int i = 0; i < 28; i++) {
                float life = 14 + 12 * hash(i, 831, 4), u = tk / life;
                if (u >= 1) {
                    continue;
                }
                Vector3f d = sphere(831 + seed, i);
                d.y = Math.abs(d.y) + 0.15f;
                d.normalize();
                float v = (8 + 14 * hash(i, 831, 2)) * sc;
                Vector3f p = at(c, d, v * x * 0.45f);
                p.y -= 4 * x * x;
                if (p.y < ground + 0.05f) {
                    continue;
                }
                rib(p, at(p, d, -(0.5f + 1.6f * hash(i, 831, 3)) * (1 - u) * sc), 0.03f + 0.05f * hash(i, 831, 6), FxDraw.STREAK, 0.9f,
                        mix(KATON_STAR, KATON_SPARK2, Math.min(1, u + 0.3f * hash(i, 831, 7))), 1 - u, true);
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

    private static float clamp(float x, float lo, float hi) {
        return Mth.clamp(x, lo, hi);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    // Two unit vectors square to n.
    private static Vector3f[] basis(Vector3f n) {
        Vector3f d = new Vector3f(n);
        if (d.lengthSquared() < 1.0E-6f) {
            d.set(0, 0, 1);
        }
        d.normalize();
        Vector3f u = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? v(1, 0, 0) : v(0, 1, 0)).normalize();
        Vector3f w = new Vector3f(d).cross(u).normalize();
        return new Vector3f[] {u, w, d};
    }

    private static Vector3f vec(Vec3 d) {
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }

    private static Vector3f toCam(Vector3f p) {
        return new Vector3f(p).negate();
    }

    // A lit box: center c, half extents along ax, ay, az (any orientation). Alpha pass only.
    private static void obox(Vector3f c, Vector3f ax, Vector3f ay, Vector3f az, float[] col, float op) {
        if (DbFx.pass || op <= 0.002f) {
            return;
        }
        Vector3f[][] faces = {{ax, ay, az}, {new Vector3f(ax).negate(), az, ay}, {ay, az, ax}, {new Vector3f(ay).negate(), ax, az},
                {az, ax, ay}, {new Vector3f(az).negate(), ay, ax}};
        for (Vector3f[] f : faces) {
            Vector3f n = f[0];
            Vector3f nn = new Vector3f(n).normalize();
            float light = 0.55f + 0.45f * Math.max(0, nn.y * 0.7f + 0.35f + nn.x * 0.15f);
            float[] lc = {col[0] * light, col[1] * light, col[2] * light};
            Vector3f mid = new Vector3f(c).add(n);
            Vector3f p0 = new Vector3f(mid).sub(f[1]).sub(f[2]);
            Vector3f p1 = new Vector3f(mid).add(f[1]).sub(f[2]);
            Vector3f p2 = new Vector3f(mid).add(f[1]).add(f[2]);
            Vector3f p3 = new Vector3f(mid).sub(f[1]).add(f[2]);
            FxDraw.quad(p0, p1, p2, p3, FxDraw.SOLID, 0, lc, op, false);
        }
    }

    // The eyes of an entity this frame (two points just in front of the face).
    private static Vector3f[] eyesOf(Entity e, float partial) {
        Vec3 look = e.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = flat.cross(new Vec3(0, 1, 0));
        Vec3 eye = e.getEyePosition(partial).add(look.scale(0.27)).add(0, -0.02, 0);
        float s = e.getBbHeight() / 1.8f;
        return new Vector3f[] {rel(eye.add(right.scale(-0.1 * s))), rel(eye.add(right.scale(0.1 * s)))};
    }

    // ---------- the shared effects (Visualizador helpers) ----------

    // The shadow clone's white puff.
    static void poof(Vector3f at, float age, int seed, float s) {
        if (age < 0 || age > 22) {
            return;
        }
        if (KiMesh.ready()) {
            // forja/naruto-kit: ONE cel-shaded smoke ball in 3 bands of light that tears from the inside out (each its own seed)
            if (DbFx.pass && age <= 17) {
                float k = age / 17;
                KiMesh.P sm = KiMesh.p(KiMesh.BLAST);
                sm.smoke = 1;
                sm.skirt = 0;
                sm.glow = false;
                sm.life = 0.2f + 0.8f * k;
                sm.time = age / 20f;
                sm.seed = 1.7f + (seed % 23) * 0.61f;
                sm.flat = 0.05f;
                sm.color = POOF_SMOKE;
                KiMesh.queue(KiMesh.Mesh.BLAST, KiMesh.sphere(add(at, 0, k * 0.5f, 0), s * (0.35f + 0.8f * easeOut(age / 4.4f))), sm, KiMesh.BOTH, 18);
            }
            return;
        }
        float k = age / 22;
        float e = easeOut(age / 8);
        for (int i = 0; i < 10; i++) {
            Vector3f d = sphere(seed + 40, i);
            d.y = Math.abs(d.y) * 0.8f;
            bb(add(at(at, d, (0.25f + 0.55f * e) * s), 0, k * 0.4f, 0), (0.6f + 0.9f * e) * s, i + age * 0.02f, FxDraw.SMOKE, 0.3f + k * 0.4f,
                    SMOKE, 0.9f * (1 - k), false);
        }
        if (age < 4) {
            bb(at, 1.2f * s, 0, FxDraw.SOFT, 0.6f, WHITE, 0.5f * (1 - age / 4), true);
        }
    }

    // The Rasengan (v37): the shell of rings turning in many planes, wind streaks and the white core, with the orb in the
    // Azul's style 4 (glass, thin bright rim, white flames inside, frost off the rim).
    static void rasengan(Vector3f at, float r, float t, float a, float[] col) {
        if (a <= 0 || r <= 0.005f) {
            return;
        }
        if (KiMesh.ready()) {
            rasenganKi(at, r, t, a, 0);
            warp(at, r * 2.4f, 0.4f * a, 2, t, null);
            return;
        }
        bb(at, r * 4, 0, FxDraw.SOFT, 0.2f, col, 0.35f * a, true);
        bb(at, r * 2.1f, 0, FxDraw.SOFT, 0.75f, PALE, 0.5f * a, true);
        bb(at, r * 4.2f, 0, FxDraw.AZUL, AzulRenderer.param(3, 0.8f), new float[] {0.5f, 1, 0.35f}, a, false);
        for (int i = 0; i < 8; i++) {
            Vector3f ax = sphere(91, i);
            Vector3f n = sphere(93, i).rotateAxis(t * (0.9f + 0.25f * i), ax.x, ax.y, ax.z);
            dsk(at, n, r * 2.2f * (0.85f + 0.25f * hash(i, 9, 1)), FxDraw.RING, 0.07f, i % 2 != 0 ? PALE : col, 0.75f * a, true);
        }
        for (int i = 0; i < 16; i++) {
            Vector3f ax = sphere(95, i);
            Vector3f u = new Vector3f(ax).cross(0.3f, 1, 0.1f).normalize();
            Vector3f w = new Vector3f(ax).cross(u);
            float th = t * (1.3f + hash(i, 9, 2)) + i;
            Vector3f p = at(at(at, u, Mth.cos(th) * r * 1.15f), w, Mth.sin(th) * r * 1.15f);
            Vector3f q = at(at(at, u, Mth.cos(th - 0.7f) * r * 1.1f), w, Mth.sin(th - 0.7f) * r * 1.1f);
            rib(q, p, r * 0.16f, FxDraw.STREAK, 0.9f, BOLT_HOT, 0.8f * a, true);
        }
        warp(at, r * 2.4f, 0.4f * a, 2, t, null);
    }

    // The Rasengan of the Realista+Anime kit (forja/naruto-kit, approved 2026-11-11): the glass core with the swirl seen
    // through it, the shell of wind streaks on three tilted orbits (gather 1 = still far out, closing in as it forms) and
    // the halo of rays. One piece each, queued once a frame (glow pass).
    static void rasenganKi(Vector3f at, float r, float t, float a, float gather) {
        if (!DbFx.pass || a <= 0) {
            return;
        }
        float sec = t / 20f;
        KiMesh.P core = KiMesh.p(KiMesh.RASEN);
        core.time = sec;
        core.rot = sec * 7;
        core.alpha = a;
        KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(at, r), core, KiMesh.FRONT, 15);
        KiMesh.P wind = KiMesh.p(KiMesh.RASEN_WIND);
        wind.time = sec;
        wind.phase = sec * 11;
        wind.alpha = a * (0.6f + 0.4f * (1 - gather));
        KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(at, r * (1.3f + 1.1f * gather)), wind, KiMesh.BOTH, 17);
        KiMesh.P halo = KiMesh.p(KiMesh.HALO);
        halo.time = sec;
        halo.alpha = a;
        KiMesh.queue(KiMesh.Mesh.HALO, KiMesh.sphere(at, r * 1.65f), halo, KiMesh.BACK, 12);
    }

    static void nrBolt(Vector3f a, Vector3f b, int seed, float w, float[] col, float op, float jag, int segs, int br) {
        DbFx.nrBolt(a, b, seed, w, col, op, jag, segs, br);
    }

    // A ball of lightning in the hand (Chidori / Raikiri).
    static void chidoriBall(Vector3f at, float t, float a, float r, float[] col) {
        if (a <= 0) {
            return;
        }
        int tk = (int) Math.floor(t);
        bb(at, r * 3.2f, 0, FxDraw.SOFT, 0.3f, col, 0.45f * a, true);
        bb(at, r * 1.2f, 0, FxDraw.SOFT, 0.9f, BOLT_HOT, a, true);
        for (int i = 0; i < 9; i++) {
            Vector3f d = sphere(tk * 13 + 5, i);
            nrBolt(at, at(at, d, r * (1.2f + 1.6f * hash(tk, i, 7))), tk * 31 + i, 0.025f, i % 3 != 0 ? col : BOLT_HOT, a, 0.5f, 4, 0);
        }
    }

    static void nrDust(Vector3f p, float age, int seed, int n, float s, float[] col, float life) {
        DbFx.nrDust(p, age, seed, n, s, col, life);
    }

    static void nrHit(Vector3f p, float age, float[] col, float s, int seed, float life) {
        DbFx.nrHit(p, age, col, s, seed, life);
    }

    // Shadow on the ground: flat dark blobs along the way (prog 0..1).
    static void shadowPath(Vector3f a, Vector3f b, float prog, float w, float t, int seed, float op) {
        if (prog <= 0) {
            return;
        }
        float len = a.distance(b);
        int n = Math.max(2, (int) Math.ceil(len / 0.11f * prog));
        Vector3f d = new Vector3f(b).sub(a);
        Vector3f side = v(-d.z, 0, d.x);
        if (side.lengthSquared() < 1.0E-6f) {
            side.set(1, 0, 0);
        }
        side.normalize();
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n * prog;
            Vector3f p = at(lerp(a, b, u), side, Mth.sin(u * 9 + seed) * 0.08f);
            p.y += 0.025f + i * 0.00002f;
            float tip = u > prog - 0.06f ? 1.25f : 1;
            dsk(p, UP, w * tip * (1 + 0.18f * Mth.sin(u * 23 + t * 0.2f + seed)), FxDraw.DISC, 0.12f, SHADOW, op, false);
        }
    }

    // Real sand: grains sifting from points of a box and falling to the ground (gy).
    static void sandSift(Vector3f c, float sx, float sy, float sz, int n, float t, int seed, float op, float life, float gy) {
        if (op <= 0.01f) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float lf = life * (0.7f + 0.6f * hash(seed, i, 1));
            float ph = (t + hash(seed, i, 2) * lf) / lf;
            int cyc = (int) Math.floor(ph);
            float u = ph - cyc;
            float tt = u * lf;
            int s2 = seed + cyc * 7;
            Vector3f p = add(c, (hash(s2, i, 3) - 0.5f) * 2 * sx, (hash(s2, i, 4) - 0.5f) * 2 * sy, (hash(s2, i, 5) - 0.5f) * 2 * sz);
            p.x += (hash(s2, i, 6) - 0.5f) * 0.02f * tt;
            p.z += (hash(s2, i, 7) - 0.5f) * 0.02f * tt;
            p.y -= 0.0045f * tt * tt;
            if (p.y < gy + 0.02f) {
                continue;
            }
            bb(p, 0.035f + 0.05f * hash(seed, i, 8), 0, FxDraw.DISC, 0.4f, SAND_TONES[i % 5], op * (1 - u * 0.4f), false);
        }
    }

    // Haze of fine dust (sand-colored smoke, low and slow), c on the ground.
    static void sandHaze(Vector3f c, float r, int n, float t, int seed, float op, float rise) {
        if (op <= 0.01f) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float ph = fract(t * 0.012f + hash(seed, i, 1));
            float ang = hash(seed, i, 2) * Mth.TWO_PI + ph * 0.6f;
            float rr = r * (0.3f + 0.8f * hash(seed, i, 3)) * (0.8f + 0.4f * ph);
            bb(add(c, Mth.cos(ang) * rr, 0.25f + ph * rise * r + hash(seed, i, 4) * 0.5f, Mth.sin(ang) * rr), r * (0.5f + 0.5f * hash(seed, i, 5)) * (0.8f + 0.6f * ph),
                    i + t * 0.004f, FxDraw.SMOKE, 0.38f, i % 3 != 0 ? DUST : SAND, op * Mth.sin(ph * Mth.PI) * 0.55f, false);
        }
    }

    // Grains climbing in a spiral up the surface of an ellipsoid (c on the ground, radius rx, half height hy) up to top.
    static void sandClimb(Vector3f c, float rx, float hy, float top, int n, float t, int seed, float op) {
        if (op <= 0.01f) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float u = fract(t * (0.02f + 0.015f * hash(seed, i, 1)) + hash(seed, i, 2));
            float y = u * top;
            float dy = (y - hy) / hy;
            float rr = rx * (float) Math.sqrt(Math.max(0, 1 - dy * dy)) + 0.04f + 0.05f * hash(seed, i, 3);
            float ang = hash(seed, i, 4) * Mth.TWO_PI + u * 5;
            bb(add(c, Mth.cos(ang) * rr, y, Mth.sin(ang) * rr), 0.04f + 0.05f * hash(seed, i, 5), 0, FxDraw.DISC, 0.4f, SAND_TONES[i % 5],
                    op * Mth.sin(u * Mth.PI), false);
        }
    }

    // Grains bursting out of c (ballistic).
    static void sandBurst(Vector3f c, float age, int n, float sp, int seed, float life, float gy) {
        if (age < 0 || age > life) {
            return;
        }
        for (int i = 0; i < n; i++) {
            Vector3f d = sphere(seed, i);
            d.y = Math.abs(d.y) * 0.9f + 0.1f;
            d.normalize();
            float vv = sp * (0.4f + 0.8f * hash(seed, i, 1));
            Vector3f p = at(c, d, vv * age);
            p.y -= 0.006f * age * age;
            if (p.y < gy + 0.03f) {
                p.y = gy + 0.03f;
            }
            bb(p, 0.04f + 0.07f * hash(seed, i, 2), 0, FxDraw.DISC, 0.4f, SAND_TONES[i % 5], 1 - smooth((age - life * 0.6f) / (life * 0.4f)), false);
        }
    }

    static void sandPuff(Vector3f at, float s, float op, int seed, float t) {
        for (int i = 0; i < 6; i++) {
            Vector3f d = sphere(seed, i);
            bb(at(at, d, 0.3f * s), (0.6f + 0.4f * hash(seed, i, 1)) * s, i + t * 0.01f, FxDraw.SMOKE, 0.35f, i % 2 != 0 ? SAND : DUST, op, false);
        }
    }

    // ---------- Naruto ----------

    // ---------- Henge Armadilha (forja/naruto-kit, B) ----------
    // a = 0: a clone turned into a rock, waiting (lumpy and lit, each one its own shape from its id, breathing a little: the
    // tell for whoever pays attention). a = 1: the Fuuma Shuriken, four curved blades flat on its spin, flying along dir.
    private static void henge(Fx f, float age, double now, float out) {
        if (out <= 0) {
            return;
        }
        Vec3 pp = f.pos;
        if (f.endAt < 0 && f.trail.size() >= 2) {
            Iterator<Vec3> it = f.trail.descendingIterator();
            Vec3 last = it.next(), prev = it.next();
            if (last.distanceToSqr(prev) < 9) {
                pp = last.add(last.subtract(prev).scale(Math.min(1.5, Math.max(0, now - f.updated))));
            }
        }
        Vector3f c = rel(pp);
        if (f.a < 0.5f) {
            rock(c, f.id, 1 + 0.018f * Math.max(0, Mth.sin(age / 20f * 2.4f)), out);
        } else {
            fuuma(c, age * 1.3f, f.b > 0 ? f.b : 0.9f, out);
        }
    }

    private static Vector3f rockPoint(int id, int i, int j, float breathe) {
        int ii = i % 8;
        float th = ii / 8f * Mth.TWO_PI, ph = j / 6f * Mth.PI;
        float k = j == 0 || j == 6 ? 0.95f : 0.8f + 0.5f * hash(id, ii * 7 + j, 3);
        float r = 0.62f * k;
        return v(Mth.sin(ph) * Mth.cos(th) * r, Mth.cos(ph) * r * 0.72f * breathe, Mth.sin(ph) * Mth.sin(th) * r);
    }

    private static void rock(Vector3f c, int id, float breathe, float out) {
        Vector3f base = add(c, 0, 0.32f, 0);
        Vector3f light = v(-0.45f, 0.8f, -0.35f).normalize();
        for (int j = 0; j < 6; j++) {
            for (int i = 0; i < 8; i++) {
                Vector3f p0 = rockPoint(id, i, j, breathe), p1 = rockPoint(id, i + 1, j, breathe), p2 = rockPoint(id, i + 1, j + 1, breathe), p3 = rockPoint(id, i, j + 1, breathe);
                Vector3f n = new Vector3f(p1).sub(p0).cross(new Vector3f(p3).sub(p0));
                if (n.lengthSquared() < 1.0E-8f) {
                    n = new Vector3f(p2).sub(p1).cross(new Vector3f(p3).sub(p1));
                }
                n.normalize();
                if (n.dot(new Vector3f(p0).add(p2)) < 0) {
                    n.negate();
                }
                float l = 0.5f + 0.5f * Math.max(0, n.dot(light)) + 0.08f * (hash(id, i, j) - 0.5f);
                float[] col = {ROCK[0] * l, ROCK[1] * l, ROCK[2] * l};
                FxDraw.quad(new Vector3f(base).add(p0), new Vector3f(base).add(p1), new Vector3f(base).add(p2), new Vector3f(base).add(p3), FxDraw.SOLID, 0,
                        col, out, false);
            }
        }
    }

    private static void fuuma(Vector3f c, float spin, float size, float out) {
        Vector3f light = v(-0.45f, 0.8f, -0.35f).normalize();
        float shade = 0.75f + 0.25f * light.y;
        for (int b = 0; b < 4; b++) {
            float a0 = spin + b * Mth.HALF_PI;
            // the blade's outline: from the hub out to the tip on a curve, then back in on a tighter one
            Vector3f[] edge = new Vector3f[13];
            for (int j = 0; j <= 6; j++) {
                float u = j / 6f;
                float r = Mth.lerp(u * u, 0.22f, 1.0f) * (1 - 0.05f * Mth.sin(u * Mth.PI));
                float an = a0 - 0.35f + 0.67f * u;
                edge[j] = add(c, Mth.cos(an) * r * size, 0, Mth.sin(an) * r * size);
            }
            for (int j = 1; j <= 6; j++) {
                float u = j / 6f;
                float r = Mth.lerp(u, 1.0f, 0.22f) * (1 - 0.3f * Mth.sin(u * Mth.PI));
                float an = a0 + 0.32f + 0.63f * u;
                edge[6 + j] = add(c, Mth.cos(an) * r * size, 0, Mth.sin(an) * r * size);
            }
            Vector3f mid = add(c, Mth.cos(a0 + 0.3f) * 0.42f * size, 0, Mth.sin(a0 + 0.3f) * 0.42f * size);
            float[] col = {STEEL[0] * shade * (0.92f + 0.08f * b / 3f), STEEL[1] * shade, STEEL[2] * shade};
            for (int j = 0; j < 12; j++) {
                FxDraw.quad(mid, edge[j], edge[j + 1], edge[j + 1], FxDraw.SOLID, 0, col, out, false);
                FxDraw.quad(mid, edge[j + 1], edge[j], edge[j], FxDraw.SOLID, 0, col, out, false);
            }
        }
        dsk(add(c, 0, 0.01f, 0), UP, 0.24f * size, FxDraw.RING, 0.3f, KUNAI, out, false);
    }

    private static void rasenganFx(Fx f, float age, float out) {
        Vector3f at = rel(f.pos);
        float a = f.b * out;
        if (KiMesh.ready()) {
            boolean grinding = f.dir.lengthSqr() > 1.0E-4;
            float gather = f.c == 2 ? 1 - smooth((age - 6) / 28) : 0;
            rasenganKi(at, f.a, grinding ? age * 1.6f : age, a, gather);
            warp(at, f.a * 2.4f, 0.4f * a, 2, age, null);
            if (!grinding) {
                return;
            }
        } else if (f.c == 2) {
            // The chakra spirals into the palm.
            float k = smooth((age - 6) / 28);
            for (int i = 0; i < 12; i++) {
                float ph = fract(age * 0.06f + i / 12f);
                float an = i * 2.4f + ph * 5;
                float rr = 1.2f * (1 - ph);
                rib(add(at, Mth.cos(an) * rr, Mth.sin(an * 0.7f) * rr * 0.5f, Mth.sin(an) * rr),
                        add(at, Mth.cos(an - 0.4f) * rr * 0.9f, Mth.sin((an - 0.4f) * 0.7f) * rr * 0.45f, Mth.sin(an - 0.4f) * rr * 0.9f), 0.04f,
                        FxDraw.STREAK, 0.8f, PALE, ph * k, true);
            }
        }
        boolean grind = f.dir.lengthSqr() > 1.0E-4;
        if (!KiMesh.ready()) {
            rasengan(at, f.a, grind ? age * 1.6f : age, a, BLUE);
        }
        if (grind) {
            // Grinding against the body: a ring flat on the chest and streaks thrown round it.
            Vector3f[] bs = basis(vec(f.dir));
            Vector3f n = bs[2];
            float g = fract(age / 12);
            dsk(at(at, n, -0.1f), n, 1.2f + g * 12 * 0.15f, FxDraw.RING, 0.1f, PALE, 0.7f * (1 - g) * out, true);
            for (int i = 0; i < 8; i++) {
                float ang = age * 0.8f + i / 8f * Mth.TWO_PI;
                Vector3f p0 = at(at(at, bs[0], Mth.cos(ang) * 0.3f), bs[1], Mth.sin(ang) * 0.3f);
                Vector3f p1 = at(at(at(at, bs[0], Mth.cos(ang + 0.9f) * 1.1f), bs[1], Mth.sin(ang + 0.9f) * 1.1f), n, -0.2f);
                rib(p0, p1, 0.06f, FxDraw.STREAK, 0.9f, BOLT_HOT, 0.9f * out, true);
            }
        }
    }

    // The wind behind the one launched: three smooth ribbons in a spiral round the path, turning like a drill, opening and
    // fading at the tail.
    private static void drill(Fx f, float age, float out) {
        float op = f.a * out;
        if (op <= 0.01f || f.trail.size() < 2) {
            return;
        }
        Vec3[] path = f.trail.toArray(new Vec3[0]);
        int last = path.length - 1;
        Vector3f travel = vec(path[last].subtract(path[Math.max(0, last - 3)]));
        Vector3f[] bs = basis(travel);
        float len = Math.min(14, last);
        for (int s = 0; s < 3; s++) {
            Vector3f prev = null;
            for (int j = 0; j <= 26; j++) {
                float u = j / 26f;
                float l = u * len;
                int i0 = Math.max(0, last - (int) Math.floor(l));
                int i1 = Math.max(0, i0 - 1);
                Vector3f c = lerp(rel(path[i0]), rel(path[i1]), l - (float) Math.floor(l));
                float th = -age * 0.55f + u * 9 + s * Mth.TWO_PI / 3;
                float rad = 0.35f + 0.9f * u;
                Vector3f p = at(at(c, bs[0], Mth.cos(th) * rad), bs[1], Mth.sin(th) * rad);
                if (prev != null) {
                    float k = Mth.sin(Math.min(1, u * 1.4f) * Mth.PI) * (1 - u);
                    rib(prev, p, 0.1f + 0.14f * u, FxDraw.BEAM, 0.5f, AIR, op * k, true);
                    rib(prev, p, 0.035f, FxDraw.BEAM, 0.9f, WHITE, op * k * 0.9f, false);
                }
                prev = p;
            }
        }
    }

    static void rasenshuriken(Vector3f at, float t, float k, float r) {
        if (k <= 0) {
            return;
        }
        if (KiMesh.ready()) {
            // forja/naruto-kit (Stark: lying flat): the Rasengan's core in the middle of ONE disc of four saw blades of wind
            rasenganKi(at, 0.26f, t, Math.min(1, k * 1.5f), 0);
            if (DbFx.pass) {
                KiMesh.P d = KiMesh.p(KiMesh.RASENSHURIKEN);
                d.time = t / 20f;
                d.rot = t / 20f * 26;
                d.alpha = Math.min(1, k * 1.5f);
                KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(at, r * k), d, KiMesh.BOTH, 19);
            }
            return;
        }
        rasengan(at, 0.24f, t, 1, BLUE);
        for (int b = 0; b < 4; b++) {
            float a0 = t * 1.5f + b * Mth.PI / 2;
            Vector3f last = new Vector3f(at);
            for (int j = 1; j <= 8; j++) {
                float u = j / 8f;
                float ang = a0 - u * 0.9f;
                float rr = 0.25f + (r - 0.25f) * u * k;
                Vector3f p = add(at, Mth.cos(ang) * rr, 0, Mth.sin(ang) * rr);
                rib(last, p, (0.55f * (1 - u) + 0.08f) * k, FxDraw.STREAK, 0.9f, u < 0.5f ? WHITE : PALE, 0.9f, true);
                last = p;
            }
        }
        dsk(at, UP, r * 2 * k, FxDraw.RING, 0.04f, PALE, 0.5f, true);
        dsk(at, UP, r * 2.2f * k, FxDraw.SOFT, 0.1f, BLUE, 0.25f, true);
        if (k > 0.05f && k < 1) {
            for (int i = 0; i < 6; i++) {
                float a = t * 2 + i;
                bb(add(at, Mth.cos(a) * 1.5f * k, (hash(i, 3, 1) - 0.5f) * 0.3f, Mth.sin(a) * 1.5f * k), 0.1f, 0, FxDraw.SOFT, 0.9f, WHITE, 0.6f, true);
            }
        }
    }

    // The giant sphere of wind needles (the Rasenshuriken's blast).
    private static void windSphere(Fx f, float a) {
        Vector3f tgt = rel(f.pos);
        float ground = (float) (f.dir.x - DbFx.cam.y);
        float lasts = f.b;
        float grow = easeOut(a / 9);
        float fade = 1 - smooth((a - lasts) / 16);
        float r = f.a * grow * (a > lasts ? 1 - smooth((a - lasts) / 16) * 0.6f : 1);
        Vector3f floor = v(tgt.x, ground, tgt.z);
        boolean ki = KiMesh.ready();
        if (ki && DbFx.pass && fade > 0) {
            // forja/naruto-kit: ONE blue dome of wind that tears into tongues round the needles
            KiMesh.P d = KiMesh.p(KiMesh.BLAST);
            d.color = DOME_BLUE;
            d.life = clamp(0.3f + a / 52f, 0, 1);
            d.time = a / 20f;
            d.seed = 4.1f;
            d.flat = 0.3f;
            d.alpha = fade;
            KiMesh.queue(KiMesh.Mesh.BLAST, KiMesh.sphere(tgt, r * 0.95f), d, KiMesh.BOTH, 14);
        }
        if (fade > 0) {
            if (!ki) {
                bb(tgt, r * 2.4f, 0, FxDraw.SOFT, 0.35f, PALE, 0.5f * fade, true);
                bb(tgt, r * 1.2f, 0, FxDraw.SOFT, 0.9f, WHITE, 0.3f * fade, true);
            }
            dsk(tgt, toCam(tgt), r * 2.05f, FxDraw.RING, 0.04f, WHITE, 0.65f * fade, true);
            dsk(tgt, toCam(tgt), r * 1.6f, FxDraw.RING, 0.03f, PALE, 0.4f * fade, true);
            int tk = (int) Math.floor(a);
            for (int i = 0; i < 150; i++) {
                Vector3f d = sphere(tk * 3 + 1, i);
                Vector3f e = sphere(tk * 5 + 2, i);
                Vector3f p = at(tgt, d, r * 0.95f * (float) Math.cbrt(hash(tk, i, 9)));
                rib(p, at(p, e, 0.8f + hash(tk, i, 8) * 1.8f), 0.035f, FxDraw.STREAK, 0.9f, WHITE, 0.9f * fade, true);
            }
            for (int i = 0; i < 3; i++) {
                dsk(add(floor, 0, 0.08f + i * 0.4f, 0), UP, r * (1.7f + 0.25f * i), FxDraw.RING, 0.04f, AIR, 0.5f * fade, true);
            }
            warp(tgt, r * 1.25f, 0.8f * fade, 1, a, UP);
            nrDust(floor, a, 14, 22, 3.4f, DUST, 50);
            nrDust(floor, a - 10, 15, 16, 4.4f, new float[] {0.42f, 0.37f, 0.3f}, 60);
        }
        if (a < 16) {
            for (int k = 0; k < 2; k++) {
                float ar = a - k * 5;
                if (ar < 0) {
                    continue;
                }
                dsk(add(floor, 0, 0.06f, 0), UP, (4 + 14 * easeOut(ar / 16)) * 2, FxDraw.RING, 0.05f, WHITE, 0.9f * (1 - ar / 16), true);
            }
        }
    }

    // Nature energy: light motes coming from far into the body (no aura, as in the reference).
    private static void sageGather(Fx f, float t, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        Vector3f c = e != null ? rel(e.getPosition(partial).add(0, 0.5, 0)) : rel(f.pos.add(0, 0.5, 0));
        float k = f.a * out;
        if (k <= 0) {
            return;
        }
        for (int i = 0; i < 40; i++) {
            float ph = fract(t * (0.008f + hash(i, 1, 1) * 0.008f) + hash(i, 1, 2));
            Vector3f d = sphere(120, i);
            d.y = Math.abs(d.y) * 0.6f;
            Vector3f p = at(c, d, 0.3f + 5 * (1 - ph) * (1 - ph));
            bb(p, 0.06f + 0.05f * ph, 0, FxDraw.SOFT, 0.8f, i % 3 != 0 ? GREEN : HOT, ph * k * 0.9f, true);
        }
    }

    // Sage Mode on: a wave of nature (ring, bent air), leaves and dust thrown round him.
    private static void sageBurst(Vector3f feet, float a) {
        if (a < 22) {
            dsk(add(feet, 0, 0.05f, 0), UP, 1 + a * 0.6f, FxDraw.RING, 0.08f, GREEN, 0.6f * (1 - a / 22), true);
            warp(add(feet, 0, 0.8f, 0), 6, 0.6f * (1 - a / 22), 0, a, null);
        }
        if (a < 40) {
            for (int i = 0; i < 24; i++) {
                float ang = hash(i, 2, 1) * Mth.TWO_PI;
                float rr = 0.6f + a * 0.22f * (0.6f + hash(i, 2, 2));
                float y = 0.15f + Mth.sin(a * 0.2f + i) * 0.2f + a * 0.02f;
                bb(add(feet, Mth.cos(ang) * rr, y, Mth.sin(ang) * rr), 0.12f, a * 0.3f + i, FxDraw.SOLID, 0, i % 2 != 0 ? LEAF_A : LEAF_B, 1 - a / 40, false);
            }
        }
        nrDust(feet, a, 12, 14, 1.4f, DUST, 30);
    }

    // Glowing eyes: Sage (orange), Sharingan (red with the turning tomoe), Ino's purple, Kakashi's Mangekyo (one eye).
    private static void eyes(Fx f, float t, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        if (e == null || FxAnchor.firstPersonSelf(f.id)) {
            return;
        }
        Vector3f[] ey = eyesOf(e, partial);
        int style = (int) f.b;
        for (int i = 0; i < 2; i++) {
            if (style == 3 && i == 0) {
                continue;
            }
            Vector3f p = ey[i];
            switch (style) {
                case 0 -> bb(p, 0.16f, 0, FxDraw.SOFT, 0.7f, ORANGE, 0.8f * out, true);
                case 1, 3 -> {
                    bb(p, style == 3 ? 0.25f : 0.22f, 0, FxDraw.SOFT, 0.6f, RED, 0.85f * out, true);
                    bb(p, style == 3 ? 0.16f : 0.12f, t * 0.3f, FxDraw.FLARE, 3 / 32f, DARK_RED, 0.9f * out, false);
                }
                default -> bb(p, 0.2f, 0, FxDraw.SOFT, 0.7f, PURPLE, 0.9f * out, true);
            }
        }
    }

    // Kawazu Kumite: the punch doesn't touch, the wave does.
    private static void kawazu(Fx f, float a) {
        Vector3f fist = rel(f.pos);
        Vector3f hit = rel(f.pos.add(f.dir));
        Vector3f n = new Vector3f(hit).sub(fist).normalize();
        if (a < 10) {
            dsk(lerp(fist, hit, Math.min(1, a / 4)), n, 0.5f + a * 0.08f, FxDraw.RING, 0.15f, AIR, 0.8f * (1 - a / 10), true);
            warp(lerp(fist, hit, clamp(a / 3, 0, 1)), 1.2f, 0.8f * (1 - a / 10), 2, a, null);
            rib(fist, hit, 0.25f, FxDraw.STREAK, 0.4f, AIR, 0.5f * (1 - a / 10), true);
        }
    }

    // ---------- Sasuke / Kakashi: lightning ----------

    private static void chidoriFx(Fx f, float t, float out) {
        Vector3f hand = rel(f.pos);
        float[] col = f.b >= 1 ? RAIKIRI : BOLT;
        chidoriBall(hand, t, out * Math.min(1, f.a * 4), Math.max(0.05f, f.a), col);
        if (f.dir.lengthSqr() > 1.0E-4 && t > 8 && f.a > 0.05f) {
            // The bolts scratch the ground in front.
            Vector3f g0 = rel(f.pos.add(f.dir));
            int tk = (int) Math.floor(t);
            for (int j = 0; j < 2; j++) {
                Vector3f g = add(g0, (hash(tk, j, 1) - 0.5f) * 1.6f, 0.02f, (hash(tk, j, 2) - 0.2f) * 1.4f);
                nrBolt(hand, g, tk * 7 + j, 0.02f, col, 0.8f * out, 0.4f, 5, 1);
                bb(g, 0.3f, 0, FxDraw.SOFT, 0.8f, BOLT_HOT, 0.8f * out, true);
            }
        }
    }

    // The Chidori's way: bolts along it and a burned line on the ground.
    private static void boltTrail(Fx f, float a) {
        Vector3f from = rel(f.pos);
        Vector3f to = rel(f.pos.add(f.dir));
        float h = f.a;
        int tk = (int) Math.floor(a);
        for (int i = 0; i < 2; i++) {
            nrBolt(from, to, tk * 3 + i, 0.03f, BOLT, 0.8f * (1 - smooth((a - 10) / 14)), 0.12f, 12, 2);
        }
        rib(add(from, 0, -h + 0.03f, 0), add(to, 0, -h + 0.03f, 0), 0.18f, FxDraw.BEAM, 0.6f, SCORCH, 0.8f * (1 - smooth((a - 40) / 30)), false);
    }

    // Through the body: the flash and bolts coming out of the back.
    private static void pierce(Fx f, float a) {
        Vector3f cp = rel(f.pos);
        Vector3f d = vec(f.dir).normalize();
        nrHit(cp, a, f.a >= 1 ? RED : BOLT, 1, 3, 12);
        if (a < 14) {
            int tk = (int) Math.floor(a);
            Vector3f[] bs = basis(d);
            for (int i = 0; i < 4; i++) {
                Vector3f end = at(at(at(cp, bs[0], (hash(i, tk, 1) - 0.5f) * 2), bs[1], (hash(i, tk, 2) - 0.5f) * 1.4f), d, 2 + hash(i, 3, 3));
                nrBolt(cp, end, tk * 9 + i, 0.03f, BOLT, 1 - a / 14, 0.3f, 6, 1);
            }
        }
    }

    private static void senbon(Fx f, float a) {
        Vector3f hand = rel(f.pos);
        Vector3f d = vec(f.dir);
        float len = d.length();
        if (len < 0.01f) {
            return;
        }
        d.div(len);
        float k = a / 6;
        if (k <= 0 || k >= 1) {
            return;
        }
        Vector3f head = at(hand, d, len * k);
        rib(at(head, d, -1.4f), head, 0.05f, FxDraw.STREAK, 0.95f, BOLT_HOT, 1, true);
        bb(head, 0.25f, 0, FxDraw.SOFT, 0.8f, BOLT, 0.9f, true);
    }

    // Paralyzed: sparks running over the whole body.
    private static void paralyze(Fx f, float t, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        Vector3f feet = rel(e.getPosition(partial));
        float h = e.getBbHeight();
        int tk = (int) Math.floor(t);
        bb(add(feet, 0, h * 0.55f, 0), 2.2f * h / 1.8f, 0, FxDraw.SOFT, 0.2f, BOLT, 0.25f * out, true);
        for (int j = 0; j < 8; j++) {
            Vector3f a = add(feet, (hash(tk, j, 1) - 0.5f) * 0.6f, 0.2f + hash(tk, j, 2) * (h - 0.2f), (hash(tk, j, 3) - 0.5f) * 0.4f);
            Vector3f b = add(a, (hash(tk, j, 4) - 0.5f) * 0.7f, (hash(tk, j, 5) - 0.5f) * 0.7f, (hash(tk, j, 6) - 0.5f) * 0.4f);
            nrBolt(a, b, tk * 5 + j, 0.035f, j % 2 != 0 ? BOLT : BOLT_HOT, out, 0.5f, 5, 1);
        }
    }

    // Storm clouds turning over the target, bolts flashing inside, until the strike (and fading after).
    private static void storm(Fx f, float t) {
        Vector3f sky = rel(f.pos);
        float strike = f.a;
        float r = f.b;
        float fade = 1 - smooth((t - strike - 10) / 30);
        int tk = (int) Math.floor(t);
        for (int i = 0; i < 22; i++) {
            float a = hash(i, 5, 1) * Mth.TWO_PI + t * 0.004f * (1 + hash(i, 5, 2));
            float rr = 2 + hash(i, 5, 3) * (r - 2);
            bb(add(sky, Mth.cos(a) * rr, (hash(i, 5, 4) - 0.5f) * 2, Mth.sin(a) * rr), 9 + hash(i, 5, 5) * 6, i + t * 0.002f, FxDraw.SMOKE, 0.3f, STORM,
                    0.85f * smooth((t - 6 - i) / 30) * fade, false);
        }
        if (t > 30 && t < strike && hash(tk, 1, 7) > 0.8f) {
            Vector3f a = add(sky, (hash(tk, 2, 1) - 0.5f) * 14, -1, (hash(tk, 2, 2) - 0.5f) * 10);
            nrBolt(a, add(a, (hash(tk, 2, 3) - 0.5f) * 8, -1 - hash(tk, 2, 4) * 2, (hash(tk, 2, 5) - 0.5f) * 6), tk, 0.06f, BOLT, 0.9f, 0.3f, 7, 2);
            bb(a, 10, 0, FxDraw.SOFT, 0.2f, BOLT, 0.25f, true);
        }
    }

    // The Kirin: the dragon's head comes down in 6 ticks, then the bolt from the sky, the flash, the scorch and the dust.
    private static void kirin(Fx f, float a) {
        Vector3f sky = rel(f.pos);
        Vector3f tgt = rel(f.pos.add(f.dir));
        Vector3f floor = add(tgt, 0, -0.9f, 0);
        int tk = (int) Math.floor(a);
        if (a < 6) {
            float k = a / 6;
            Vector3f head = lerp(sky, tgt, k);
            nrBolt(sky, head, tk * 3, 0.25f, BOLT, 1, 0.12f, 14, 4);
            bb(head, 3.5f, a * 0.2f, FxDraw.FLARE, 6 / 32f, BOLT_HOT, 1, true);
            for (int s = -1; s <= 1; s += 2) {
                nrBolt(head, add(head, s * 1.2f, 1.6f, 0), tk + s, 0.08f, BOLT_HOT, 0.9f, 0.3f, 5, 0);
            }
            return;
        }
        float h = a - 6;
        float fd = 1 - smooth((h - 10) / 20);
        if (fd > 0) {
            nrBolt(sky, tgt, tk * 3, 0.35f * fd + 0.05f, BOLT, fd, 0.1f, 16, 6);
            nrBolt(sky, tgt, tk * 3 + 1, 0.12f, BOLT_HOT, fd, 0.14f, 16, 3);
            bb(add(tgt, 0, 1, 0), 8 * fd, 0, FxDraw.SOFT, 0.3f, BOLT, 0.5f * fd, true);
        }
        // The blast's reach comes in f.a (8 blocks since Stark asked for a bigger, electric blast; 3.4 before).
        float R = f.a > 0 ? f.a : 3.4f;
        float big = R / 3.4f;
        nrHit(tgt, h, BOLT, 2.2f * Math.min(2, big), 7, 16);
        if (h < 30) {
            dsk(add(floor, 0, 0.04f, 0), UP, 2 + h * 0.5f * big, FxDraw.RING, 0.1f, BOLT, 1 - h / 30, true);
        }
        dsk(add(floor, 0, 0.03f, 0), UP, R, FxDraw.SOFT, 0.1f, new float[] {0.04f, 0.04f, 0.06f}, 0.85f * smooth(h / 4) * (1 - smooth((h - 30) / 14)), false);
        if (h < 40) {
            int n = 6 + (int) (4 * big);
            for (int j = 0; j < n; j++) {
                float ang = j * 6.2832f / n + hash(j, tk, 5) * 0.6f;
                float len = R * (0.6f + 0.5f * hash(j, tk, 6));
                nrBolt(add(floor, 0, 0.05f, 0), add(floor, Mth.cos(ang) * len, 0.05f, Mth.sin(ang) * len), tk * 11 + j, 0.04f, BOLT, 1 - h / 40, 0.3f, 6, 1);
            }
        }
        // The smoke is lit from inside: short bolts jumping up through it while it rises.
        if (h > 2 && h < 34) {
            for (int j = 0; j < 4; j++) {
                if (hash(j, tk, 7) < 0.45f) {
                    continue;
                }
                float ang = hash(j, tk, 8) * 6.2832f;
                float rr = R * 0.7f * hash(j, tk, 9);
                Vector3f lo = add(floor, Mth.cos(ang) * rr, 0.5f + hash(j, tk, 10) * 1.5f, Mth.sin(ang) * rr);
                nrBolt(lo, add(lo, (hash(j, tk, 11) - 0.5f) * 2, 1.5f + hash(j, tk, 12) * 2.5f * big, (hash(j, tk, 13) - 0.5f) * 2), tk * 13 + j, 0.04f, BOLT_HOT,
                        1 - h / 34, 0.3f, 7, 1);
            }
        }
        nrDust(floor, h - 2, 15, 16, 1.6f * big, new float[] {0.4f, 0.4f, 0.45f}, 34);
    }

    // Sasuke's afterimage: a red streak to where he went and a fading dark copy where he stood.
    private static void afterimage(Fx f, float a) {
        Vector3f from = rel(f.pos);
        Vector3f to = rel(f.pos.add(f.dir));
        float h = f.a;
        if (a < 8) {
            rib(add(from, 0, h * 0.55f, 0), add(to, 0, h * 0.55f, 0), 0.4f, FxDraw.STREAK, 0.6f, RED, 0.5f * (1 - a / 8), true);
        }
        float op = 0.55f * (1 - a / 14);
        if (op <= 0) {
            return;
        }
        Vector3f p = add(from, a * 0.01f, 0, 0);
        Vector3f d = vec(f.dir);
        d.y = 0;
        Vector3f[] bs = basis(d.lengthSquared() < 1.0E-4f ? v(0, 0, 1) : d);
        Vector3f side = new Vector3f(d).cross(UP).normalize();
        if (!Float.isFinite(side.x)) {
            side.set(1, 0, 0);
        }
        float s = h / 1.8f;
        float[] ghost = {0.35f, 0.32f, 0.42f};
        Vector3f fw = new Vector3f(d).normalize();
        obox(add(p, 0, 1.12f * s, 0), new Vector3f(side).mul(0.25f * s), v(0, 0.37f * s, 0), new Vector3f(fw).mul(0.13f * s), ghost, op);
        obox(add(p, 0, 1.75f * s, 0), new Vector3f(side).mul(0.25f * s), v(0, 0.25f * s, 0), new Vector3f(fw).mul(0.25f * s), ghost, op);
        for (int i = -1; i <= 1; i += 2) {
            obox(at(add(p, 0, 0.37f * s, 0), side, i * 0.12f * s), new Vector3f(side).mul(0.12f * s), v(0, 0.37f * s, 0), new Vector3f(fw).mul(0.12f * s), ghost, op);
            obox(at(add(p, 0, 1.12f * s, 0), side, i * 0.37f * s), new Vector3f(side).mul(0.12f * s), v(0, 0.37f * s, 0), new Vector3f(fw).mul(0.12f * s), ghost, op);
        }
        bb(add(p, 0, h * 0.55f, 0), h * 1.2f, 0, FxDraw.SOFT, 0.2f, RED, 0.2f * op, true);
        if (bs.length < 0) {
            return;
        }
    }

    // ---------- Gaara: sand ----------

    // The wall of sand: cells rising in a curve in front of him, the sand flowing from the gourd while it forms.
    private static void sandWall(Fx f, float t, float partial) {
        Entity e = FxAnchor.entity(f.id);
        Vector3f feet = e != null ? rel(e.getPosition(partial)) : rel(f.pos);
        Vector3f fw = vec(f.dir);
        fw.y = 0;
        if (fw.lengthSquared() < 1.0E-4f) {
            fw.set(0, 0, 1);
        }
        fw.normalize();
        Vector3f side = new Vector3f(fw).cross(UP).normalize();
        float a = t - 2;
        float wallK = a < 0 ? 0 : a < 8 ? smooth(a / 8) : a < 40 ? 1 : 1 - smooth((a - 40) / 14);
        if (wallK <= 0.01f) {
            return;
        }
        for (int c = 0; c < 9; c++) {
            for (int r = 0; r < 6; r++) {
                float ang = (c - 4) / 4f * 1.05f;
                float rise = clamp(wallK * 1.6f - r * 0.12f - Math.abs(c - 4) * 0.05f, 0, 1);
                if (rise <= 0.02f) {
                    continue;
                }
                Vector3f p = at(at(feet, side, Mth.sin(ang) * 1.3f + (hash(c, r, 1) - 0.5f) * 0.05f), fw, Mth.cos(ang) * 1.3f);
                p.y += (r * 0.33f + 0.18f) * rise;
                float sc = 0.4f + 0.6f * rise;
                Vector3f cx = at(new Vector3f(side).mul(Mth.cos(ang)), fw, -Mth.sin(ang)).mul(0.17f * sc);
                Vector3f cz = at(new Vector3f(fw).mul(Mth.cos(ang)), side, Mth.sin(ang)).mul(0.15f * sc);
                obox(p, cx, v(0, 0.18f * sc, 0), cz, SAND_TONES[(c * 7 + r * 3) % 5], 1);
            }
        }
        if (wallK < 0.999f) {
            Vector3f gourd = e != null ? at(add(feet, 0, 1.0f * e.getBbHeight() / 1.8f, 0), fw, -0.35f) : add(feet, 0, 1, 0);
            for (int i = 0; i < 26; i++) {
                float u = fract(t * 0.08f + i / 26f);
                float aa = (hash(i, 6, 1) - 0.5f) * 2;
                Vector3f end = at(at(add(feet, 0, 0.4f + hash(i, 6, 2) * 1.6f, 0), side, Mth.sin(aa) * 1.3f), fw, Mth.cos(aa) * 1.3f);
                Vector3f p = add(lerp(gourd, end, u), 0, Mth.sin(u * Mth.PI) * 0.6f, 0);
                bb(p, 0.07f, 0, FxDraw.DISC, 0.4f, i % 2 != 0 ? SAND : SAND_D, 0.9f, false);
            }
            for (int i = 0; i < 5; i++) {
                bb(at(at(add(feet, 0, 0.2f, 0), side, Mth.sin((i - 2) * 0.4f) * 1.3f), fw, Mth.cos((i - 2) * 0.4f) * 1.3f), 1, i, FxDraw.SMOKE, 0.4f, DUST,
                        0.35f * wallK * (1 - wallK) * 4, false);
            }
        }
        sandSift(add(at(feet, fw, 1.3f), 0, 1, 0), 1.1f, 0.9f, 0.2f, 50, t, 3, 0.8f * wallK, 20, feet.y);
    }

    // The Sand Hand (the Visualizador's lumpy boxes: forearm, palm, jointed fingers, thumb), 1.45 times its size.
    private static void sandHand(Fx f, float t, float out) {
        Vector3f base = rel(f.pos);
        Vector3f fw = vec(f.dir);
        fw.y = 0;
        if (fw.lengthSquared() < 1.0E-4f) {
            fw.set(0, 0, 1);
        }
        fw.normalize();
        Vector3f xr = new Vector3f(fw).cross(UP).normalize();
        float rise = f.a;
        float swing = f.b;
        boolean closed = f.c == 2;
        float sc = 1.45f;
        float y = Mth.lerp(rise, -4.6f, 0.2f);
        Vector3f o = add(base, 0, y, 0);
        // Tipped forward by swing round its x.
        Vector3f yy = new Vector3f(UP).mul(Mth.cos(swing)).add(new Vector3f(fw).mul(Mth.sin(swing)));
        Vector3f zz = new Vector3f(fw).mul(Mth.cos(swing)).sub(new Vector3f(UP).mul(Mth.sin(swing)));
        if (rise > 0.01f) {
            float op = out;
            hb(o, xr, yy, zz, sc, 0, -0.9f, -0.1f, 1.0f, 2.6f, 0.9f, 0, op, base.y);
            hb(o, xr, yy, zz, sc, 0, 0.45f, 0, 1.25f, 0.5f, 0.95f, 1, op, base.y);
            hb(o, xr, yy, zz, sc, 0, 1.2f, 0.05f, 1.4f, 1.3f, 0.5f, 2, op, base.y);
            float curl = closed ? 1.6f : t < 14 ? 0.9f * (1 - rise) : 0;
            for (int i = 0; i < 4; i++) {
                float fx = -0.5f + i * 0.335f;
                float tilt = (i - 1.5f) * -0.08f;
                Vector3f fxAxis = xr;
                Vector3f fy = rot(yy, zz, curl, fxAxis, tilt, true);
                Vector3f fz = rot(zz, yy, curl, fxAxis, tilt, false);
                Vector3f knuckle = local(o, xr, yy, zz, sc, fx, 1.85f, 0.05f);
                box(knuckle, xr, fy, fz, sc, 0, 0.3f, 0, 0.27f, 0.62f, 0.32f, 3 + i, op, base.y);
                Vector3f joint = at(knuckle, fy, 0.6f * sc);
                Vector3f jy = new Vector3f(fy).mul(Mth.cos(curl * 1.2f)).add(new Vector3f(fz).mul(Mth.sin(curl * 1.2f)));
                Vector3f jz = new Vector3f(fz).mul(Mth.cos(curl * 1.2f)).sub(new Vector3f(fy).mul(Mth.sin(curl * 1.2f)));
                box(joint, xr, jy, jz, sc, 0, 0.24f, 0, 0.24f, 0.5f - (i == 3 ? 0.12f : 0), 0.28f, 7 + i, op, base.y);
            }
            Vector3f th = local(o, xr, yy, zz, sc, 0.72f, 0.95f, 0.15f);
            Vector3f ty = new Vector3f(yy).mul(Mth.cos(-0.9f)).add(new Vector3f(xr).mul(-Mth.sin(-0.9f)));
            Vector3f tx = new Vector3f(xr).mul(Mth.cos(-0.9f)).add(new Vector3f(yy).mul(Mth.sin(-0.9f)));
            box(th, tx, ty, zz, sc, 0, 0.35f, 0, 0.28f, 0.75f, 0.32f, 11, op, base.y);
            // Loose sand: grains falling off it all the time, haze on the ground.
            Vector3f hc = local(o, xr, yy, zz, sc, 0, 0.6f, 0);
            sandSift(hc, 0.9f, 1.5f, 0.5f, 90, t, 31, 0.9f * out, 20, base.y);
            sandHaze(base, 1.7f, 14, t, 33, (t < 34 ? 1 : 0.6f) * out * Math.min(1, rise * 2), 0.3f);
            if (rise < 0.9f) {
                for (int i = 0; i < 24; i++) {
                    float a = hash(i, 22, 1) * Mth.TWO_PI;
                    float r = (1 - rise) * 1.6f + 0.3f;
                    bb(add(base, Mth.cos(a) * r, 0.05f, Mth.sin(a) * r), 0.25f, 0, FxDraw.DISC, 0.4f, SAND, 0.9f, false);
                }
            }
        }
    }

    // One lumpy box of the hand: center (lx, ly, lz) in the hand's frame, size (w, h, d), not below the ground.
    private static void hb(Vector3f o, Vector3f xr, Vector3f yy, Vector3f zz, float sc, float lx, float ly, float lz, float w, float h, float d,
            int seed, float op, float gy) {
        box(local(o, xr, yy, zz, sc, lx, 0, lz), xr, yy, zz, sc, 0, ly, 0, w, h, d, seed, op, gy);
    }

    private static void box(Vector3f o, Vector3f xr, Vector3f yy, Vector3f zz, float sc, float lx, float ly, float lz, float w, float h, float d,
            int seed, float op, float gy) {
        Vector3f c = local(o, xr, yy, zz, sc, lx, ly, lz);
        if (c.y + h * sc * 0.5f < gy) {
            return;
        }
        float lump = 1 + (hash(seed, 3, 9) - 0.5f) * 0.12f;
        obox(c, new Vector3f(xr).mul(w * 0.5f * sc * lump), new Vector3f(yy).mul(h * 0.5f * sc), new Vector3f(zz).mul(d * 0.5f * sc * lump),
                SAND_TONES[seed % 5], op);
    }

    private static Vector3f local(Vector3f o, Vector3f xr, Vector3f yy, Vector3f zz, float sc, float x, float y, float z) {
        return new Vector3f(o).add(new Vector3f(xr).mul(x * sc)).add(new Vector3f(yy).mul(y * sc)).add(new Vector3f(zz).mul(z * sc));
    }

    // A finger's own axes: bent by curl round x (toward the palm), leaned by tilt round z.
    private static Vector3f rot(Vector3f a, Vector3f b, float curl, Vector3f x, float tilt, boolean isY) {
        Vector3f r = isY ? new Vector3f(a).mul(Mth.cos(curl)).add(new Vector3f(b).mul(Mth.sin(curl)))
                : new Vector3f(a).mul(Mth.cos(curl)).sub(new Vector3f(b).mul(Mth.sin(curl)));
        if (isY) {
            r.add(new Vector3f(x).mul(-Mth.sin(tilt)));
        }
        return r.normalize();
    }

    // The Sand Coffin: a lumpy shell (a sphere of sand quads) swallowing the whole body, grains climbing it in a spiral
    // and sifting off it.
    private static void sandShell(Fx f, float t, float out) {
        Vector3f feet = rel(f.pos);
        float s = (float) f.dir.x;
        float grow = f.a;
        float crush = f.b;
        if (grow <= 0.01f) {
            return;
        }
        float sy = 1.25f * grow * s;
        float sx = (0.62f + 0.36f * grow) * (1 - 0.16f * crush) * s;
        float syy = sy * (1 - 0.08f * crush);
        Vector3f c = add(feet, 0, sy, 0);
        if (!DbFx.pass) {
            int nu = 22;
            int nv = 14;
            float spin = t * 0.02f;
            for (int i = 0; i < nu; i++) {
                for (int j = 0; j < nv; j++) {
                    Vector3f p00 = shellPoint(c, sx, syy, i, j, nu, nv, spin);
                    Vector3f p10 = shellPoint(c, sx, syy, i + 1, j, nu, nv, spin);
                    Vector3f p11 = shellPoint(c, sx, syy, i + 1, j + 1, nu, nv, spin);
                    Vector3f p01 = shellPoint(c, sx, syy, i, j + 1, nu, nv, spin);
                    if (p00.y < feet.y && p11.y < feet.y) {
                        continue;
                    }
                    Vector3f n = new Vector3f(p10).sub(p00).cross(new Vector3f(p01).sub(p00));
                    if (n.dot(new Vector3f(p00).sub(c)) < 0) {
                        n.negate();
                    }
                    n.normalize();
                    float light = 0.55f + 0.45f * Math.max(0, n.y * 0.7f + 0.35f + n.x * 0.15f);
                    float[] col = SAND_TONES[(int) (hash(i, j, 77) * 5) % 5];
                    FxDraw.quad(p00, p10, p11, p01, FxDraw.SOLID, 0, new float[] {col[0] * light, col[1] * light, col[2] * light}, out, false);
                }
            }
        }
        sandClimb(feet, sx, sy, 2 * sy, 120, t, 5, grow < 1 ? 1 : 0.45f);
        sandSift(add(feet, 0, sy * 1.1f, 0), sx * 0.95f, sy * 0.85f, sx * 0.95f, 90, t, 6, 0.9f, 18, feet.y);
        sandHaze(feet, 1.5f * s, 12, t, 8, grow < 1 ? 1 : 0.55f, 0.3f);
        dsk(add(feet, 0, 0.025f, 0), UP, sx * 3.4f, FxDraw.SOFT, 0.55f, SAND, 0.85f * grow, false);
    }

    private static Vector3f shellPoint(Vector3f c, float rx, float ry, int i, int j, int nu, int nv, float spin) {
        float th = i / (float) nu * Mth.TWO_PI + spin;
        float ph = j / (float) nv * Mth.PI;
        float x = Mth.sin(ph) * Mth.cos(th);
        float y = Mth.cos(ph);
        float z = Mth.sin(ph) * Mth.sin(th);
        float n = Mth.sin(x * 7 + 3) * Mth.sin(y * 5 + 6) * Mth.sin(z * 6 + 9) * 0.6f + (hash(i % nu, j, 3) - 0.5f) * 0.5f;
        float k = 1 + n * 0.14f;
        return add(c, x * rx * k, y * ry * k, z * rx * k);
    }

    private static void sandBurstFx(Fx f, float a) {
        Vector3f c = rel(f.pos);
        float s = f.a;
        float gy = c.y - 1.3f * Math.max(0.5f, s / 2.2f);
        sandBurst(c, a, (int) (60 + 80 * Math.min(1, s / 2.2f)), 0.1f + 0.06f * s, 170 + f.id, 50, gy);
        Vector3f floor = v(c.x, gy, c.z);
        nrDust(add(floor, 0, 0.4f, 0), a, 17, 20, 1.2f * s, DUST, 60);
        sandHaze(floor, 0.9f * s, 10, a, 19, 0.5f * (1 - smooth((a - 20) / 40)), 0.6f);
        if (a < 40) {
            dsk(add(floor, 0, 0.03f, 0), UP, (0.7f + a * 0.03f) * s, FxDraw.DISC, 0.3f, SAND, 0.9f * (1 - smooth((a - 20) / 20)), false);
        }
    }

    // The platform under his feet: a lumpy dish of sand, sand sifting off it, its shadow and haze on the ground.
    private static void sandPlat(Fx f, float t, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        Vector3f feet = e != null ? rel(e.getPosition(partial)) : rel(f.pos);
        float ground = (float) (f.dir.x - DbFx.cam.y);
        float form = f.a;
        float melt = f.b;
        float k = form * (1 - melt * 0.6f) * out;
        if (k <= 0.02f) {
            return;
        }
        if (!DbFx.pass) {
            DbFx.cylinder(add(feet, 0, -0.42f * form * (1 - melt), 0), 1.15f * k, 0.7f * k, 0, 0.42f * form * (1 - melt), 0, SAND, 1);
        }
        sandSift(add(feet, 0, -0.35f, 0), 0.8f, 0.12f, 0.8f, 100, t, 53, 0.95f * (1 - melt) * out, 26, ground);
        Vector3f floor = v(feet.x, ground, feet.z);
        float above = feet.y - ground;
        if (above > 0.5f) {
            dsk(add(floor, 0, 0.02f, 0), UP, 2.6f, FxDraw.SOFT, 0.5f, new float[] {0.1f, 0.08f, 0.06f}, 0.45f * Math.max(0.3f, 1 - above / 8) * out, false);
        }
        sandHaze(floor, 1.5f, 8, t, 55, 0.5f * (1 - melt) * out, 0.5f);
    }

    // Sand running along the ground to a point, or gathering in round it.
    private static void sandFlow(Fx f, float age) {
        Vector3f from = rel(f.pos);
        float run = Math.max(1, f.a);
        if (f.b >= 1) {
            float r0 = (float) f.dir.x;
            float k = clamp(age / run, 0, 1);
            for (int i = 0; i < 90; i++) {
                float a = hash(i, 41, 1) * Mth.TWO_PI;
                float u = clamp(k * 1.3f - hash(i, 41, 2) * 0.3f, 0, 1);
                float r = Mth.lerp(u, r0 + 1.5f * hash(i, 41, 3), 0.4f);
                bb(add(from, Mth.cos(a + u * 2) * r, 0.03f + 0.15f * u * hash(i, 41, 4), Mth.sin(a + u * 2) * r), 0.05f + 0.06f * hash(i, 41, 5), 0,
                        FxDraw.DISC, 0.4f, SAND_TONES[i % 5], 0.95f * (1 - smooth((age - run) / 8)), false);
            }
            sandHaze(from, 1.4f, 10, age, 51, smooth(k * 3) * (1 - smooth((age - run) / 8)), 0.3f);
            return;
        }
        Vector3f to = rel(f.pos.add(f.dir));
        Vector3f d = new Vector3f(to).sub(from);
        Vector3f side = v(-d.z, 0, d.x);
        if (side.lengthSquared() < 1.0E-6f) {
            side.set(1, 0, 0);
        }
        side.normalize();
        for (int i = 0; i < 90; i++) {
            float u = clamp(age / (run - 2) - i / 110f, 0, 1);
            Vector3f p = at(lerp(from, to, u), side, (hash(i, 9, 1) - 0.5f) * 0.9f);
            p.y += 0.04f + 0.1f * hash(i, 9, 2);
            bb(p, 0.05f + 0.07f * hash(i, 9, 3), 0, FxDraw.DISC, 0.4f, SAND_TONES[i % 5], 0.95f * (1 - smooth((age - run) / 8)), false);
        }
        sandHaze(lerp(from, to, clamp(age / run, 0, 1)), 0.9f, 6, age, 61, 0.8f * (1 - smooth((age - run) / 8)), 0.3f);
    }

    // ---------- Gaara (forja/gaara-kit/gaara.js v1): real sand in the page's sand material (GaaraSand) ----------

    private static float e3(float t) {
        t = clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // grains per second scaled to this frame
    private static int rate(float perSecond) {
        float n = perSecond * GaaraSand.dt;
        int k = (int) n;
        return k + (GaaraSand.rng().nextFloat() < n - k ? 1 : 0);
    }

    private static float rnd() {
        return GaaraSand.rng().nextFloat();
    }

    private static Vec3 world(Vector3f relPos) {
        return DbFx.cam.add(relPos.x, relPos.y, relPos.z);
    }

    // a point of a page frame (page units) in the world
    private static Vec3 world(Matrix4f m, float x, float y, float z) {
        return world(m.transformPosition(new Vector3f(x, y, z)));
    }

    private static Vector3f fwdOf(float yaw) {
        return v(Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    private static final float[][] FINGERS = {{-0.72f, 0.9f, 0.82f}, {-0.25f, 1.12f, 1.0f}, {0.24f, 1.08f, 0.94f}, {0.7f, 0.86f, 0.74f}};
    private static final float[] SEGS = {0.44f, 0.33f, 0.26f};

    // Z Sand Hand: palm, four fingers of three joints each its own size, the thumb, held to the ground by a forearm of sand
    // (the page's makeSandHand + place). pos = the ground under the wrist, dir = (lean, curl, wrist height in page units),
    // a = facing, b = crumble, c = -(how high it has come up + 1) or 1 gone.
    private static void gaHand(Fx f, float t, float out) {
        if (DbFx.pass) {
            return;
        }
        Vector3f g = rel(f.pos);
        Vector3f fwd = fwdOf(f.a);
        float lean = (float) f.dir.x, curl = (float) f.dir.y, wy = (float) f.dir.z;
        float rise = f.c < 0 ? -f.c - 1 : 4;
        float dis = Math.max(f.b, 1 - out);
        Matrix4f F = GaaraSand.frame(g, fwd);
        float riseY = g.y + rise * PU;
        GaaraSand.piece(0.14f, 0.8f, 1, dis, riseY, 9, m -> {
            Matrix4f hand = new Matrix4f(F).translate(0, wy, 0).rotateX(-lean);
            m.sphere(new Matrix4f(hand).scale(1.05f, 1.2f, 0.45f), 32, 24);
            for (int fi = 0; fi < 4; fi++) {
                float[] fg = FINGERS[fi];
                Matrix4f j = new Matrix4f(hand);
                for (int k = 0; k < 3; k++) {
                    j.translate(k == 0 ? fg[0] : 0, k == 0 ? fg[1] : fg[2] * SEGS[k - 1], 0);
                    j.rotateX(-curl * new float[] {1.15f, 1.35f, 1.2f}[k] * (0.92f + 0.08f * fi)).rotateZ((fi - 1.5f) * 0.06f * (1 - curl));
                    float len = fg[2] * SEGS[k], r = 0.21f - 0.035f * k + (fi == 1 ? 0.02f : 0);
                    m.cylinder(new Matrix4f(j).translate(0, len / 2, 0), r * 0.88f, r, len, 14, 2, false, 0, Mth.TWO_PI);
                    m.sphere(new Matrix4f(j).scale(r * 1.05f), 12, 9);
                    if (k == 2) {
                        m.sphere(new Matrix4f(j).translate(0, len, 0).scale(r * 0.9f, r * 1.17f, r * 0.9f), 12, 9);
                    }
                }
            }
            Matrix4f th = new Matrix4f(hand);
            float[][] tj = {{1.0f, -0.15f}, {0, 0.42f}, {0, 0.34f}};
            for (int k = 0; k < 3; k++) {
                th.translate(tj[k][0], tj[k][1], 0);
                th.rotateX(-curl * (k == 0 ? 0.6f : 0.9f));
                if (k == 0) {
                    th.rotateZ(-0.75f);
                }
                float len = new float[] {0.42f, 0.34f, 0.28f}[k], r = 0.22f - 0.035f * k;
                m.cylinder(new Matrix4f(th).translate(0, len / 2, 0), r * 0.9f, r, len, 14, 2, false, 0, Mth.TWO_PI);
                m.sphere(new Matrix4f(th).scale(r * 1.05f), 12, 9);
            }
            Vector3f[] arm = {v(0, -0.3f, 2.6f + 0.8f * (1 - lean)), v(0, 0.5f, 1.7f), v(0, wy - 1.5f, 0.7f), v(0, wy - 0.95f, 0.2f)};
            m.tube(F, arm, s -> 0.95 - 0.45 * s + 0.4 * Math.pow(1 - s, 3), 30, 16, 1);
        });
        // sand pouring off it all the time
        double floor = f.pos.y;
        if (dis < 1) {
            for (int i = rate(rise < 3.9f ? 420 : 300); i > 0; i--) {
                Vec3 p = world(F, (rnd() - 0.5f) * 1.8f, wy * (0.2f + 0.9f * rnd()), (rnd() - 0.2f) * 2);
                GaaraSand.grain(p, new Vec3((rnd() - 0.5f) * 0.9, rise < 3.9f ? 2 + 3 * rnd() : -0.9, (rnd() - 0.5f) * 0.9), floor);
            }
        }
    }

    private static final float[][] COFFIN = {{0.05f, -0.05f}, {0.66f, 0}, {0.76f, 0.45f}, {0.66f, 0.95f}, {0.72f, 1.3f}, {0.82f, 1.55f}, {0.7f, 1.95f},
            {0.46f, 2.3f}, {0.16f, 2.48f}, {0.02f, 2.5f}};

    // X Sabaku Kyu: ONE shell rising from the feet over the whole body, grains spiralling up as it climbs; squeezed by the
    // Soso. pos = feet, dir = (size of the body, squeeze, -), a = how far up, b = crumble.
    private static void gaCoffin(Fx f, float t, float out) {
        if (DbFx.pass) {
            return;
        }
        Vector3f p = rel(f.pos);
        float sc = (float) Math.max(0.5, f.dir.x), sq = (float) f.dir.y, rise = f.a;
        float dis = Math.max(f.b, f.endAt >= 0 ? 1 - out : 0);
        float riseY = p.y + (-0.2f + rise * 2.9f) * PU * sc;
        GaaraSand.piece(0.13f + 0.12f * sq, 0.9f, 1, dis, riseY, 9, m -> m.lathe(new Matrix4f().translation(p).scale(PU * sc).scale(1 - 0.3f * sq, 1 - 0.12f * sq, 1 - 0.3f * sq), COFFIN, 40));
        if (rise > 0.02f && rise < 0.99f) {
            for (int i = rate(380); i > 0; i--) {
                GaaraSand.orbit(f.pos, (0.75 + 0.2 * rnd()) * PU * sc, 7 + 4 * rnd(), f.pos.y + (0.05 + 2.4 * rise * rnd()) * PU * sc, (1.2 + 1.5 * rnd()) * PU,
                        0.35f + 0.4f * rnd());
            }
        } else if (rise >= 0.99f && dis <= 0 && rnd() < GaaraSand.dt * 30) {
            Vec3 top = f.pos.add((rnd() - 0.5) * 1.1 * sc, 2.1 * sc, (rnd() - 0.5) * 1.1 * sc);
            GaaraSand.Grain gr = GaaraSand.grain(top, new Vec3(0, -0.2, 0), f.pos.y);
            gr.grav = 5 * PU;
        }
    }

    // A stream of sand running along the ground from pos to pos + dir, shown up to a = how far, b = crumble; c = its
    // thickness (page units, under 1) or 1 gone.
    private static void gaStream(Fx f, float t, float out) {
        if (DbFx.pass) {
            return;
        }
        Vector3f a = rel(f.pos);
        Vec3 dw = f.dir;
        float len = (float) dw.length() / PU;
        if (len < 0.2f) {
            return;
        }
        Vector3f d = v((float) dw.x, (float) dw.y, (float) dw.z).div(PU);
        Vector3f dn = new Vector3f(d).normalize();
        Vector3f side = v(-dn.z, 0, dn.x);
        if (side.lengthSquared() < 1e-6f) {
            side.set(1, 0, 0);
        }
        side.normalize();
        float r = f.c > 0 && f.c < 1 ? f.c : 0.38f;
        Vector3f b = new Vector3f(d);
        Vector3f[] pts = {v(0, 0, 0), new Vector3f(d).mul(0.3f).add(new Vector3f(side).mul(-0.6f)), new Vector3f(d).mul(0.65f).add(new Vector3f(side).mul(0.5f)),
                new Vector3f(b).sub(new Vector3f(dn).mul(Math.min(0.7f, len * 0.2f))), b};
        float seed = (f.id % 17) * 0.37f;
        float dis = Math.max(f.b, f.endAt >= 0 ? 1 - out : 0);
        GaaraSand.piece(0.1f, 1.6f, 1.3f, dis, 99, f.a, m -> m.tube(new Matrix4f().translation(a).scale(PU), pts,
                s -> r * (0.55 + 0.45 * Math.sin(Math.PI * Math.min(1, s * 1.2 + 0.08))) * (0.85 + 0.15 * (Math.sin(s * 23 + seed) * 0.6 + Math.sin(s * 9.3 + seed * 3) * 0.4 + 1)),
                44, 12, 0.5f));
        if (f.a < 1 && dis <= 0) {
            Vec3 head = f.pos.add(f.dir.scale(Math.max(0, f.a)));
            for (int i = rate(160); i > 0; i--) {
                GaaraSand.grain(head.add((rnd() - 0.5) * 0.55, 0.05, (rnd() - 0.5) * 0.55), new Vec3((rnd() - 0.5) * 1.3, (1 + 2 * rnd()) * PU, (rnd() - 0.5) * 1.3), f.pos.y);
            }
        }
    }

    // C Sand Platform: a thick disc with its underside dripping grains all the time, a trail of sand dust behind at speed.
    // pos = his feet, dir = (facing, pitch, bank), a = how formed, b = crumble.
    private static void gaPlat(Fx f, float t, float partial, float out) {
        if (DbFx.pass) {
            return;
        }
        Vec3 at = f.pos;
        Entity e = FxAnchor.entity(f.id);
        if (e != null && f.endAt < 0) {
            at = e.getPosition(partial);
        }
        Vector3f p = rel(at);
        float yaw = (float) f.dir.x, pitch = (float) f.dir.y, bank = (float) f.dir.z;
        Matrix4f F = new Matrix4f().translation(p).rotateY((float) Math.atan2(-Mth.sin(yaw), -Mth.cos(yaw))).rotateX(pitch).rotateZ(bank).scale(PU);
        float riseY = f.a >= 0.999f ? 99 : p.y + (-1.22f + 1.4f * f.a) * PU;
        float dis = Math.max(f.b, f.endAt >= 0 ? 1 - out : 0);
        GaaraSand.piece(0.1f, 1.0f, 1, dis, riseY, 9, m -> {
            m.cylinder(new Matrix4f(F).translate(0, -0.21f, 0), 1.25f, 0.9f, 0.42f, 48, 4, false, 0, Mth.TWO_PI);
            m.cylinder(new Matrix4f(F).translate(0, -0.62f, 0).rotateX(Mth.PI), 0, 0.85f, 0.7f, 36, 4, true, 0, Mth.TWO_PI);
        });
        if (dis >= 1) {
            return;
        }
        // speed from its last steps (blocks a second)
        Vec3 vel = Vec3.ZERO;
        if (f.trail.size() >= 2) {
            Iterator<Vec3> it = f.trail.descendingIterator();
            Vec3 last = it.next(), prev = it.next();
            vel = last.subtract(prev).scale(20);
        }
        double v = vel.length();
        double floor = at.y - 40;
        for (int i = rate(f.a < 0.999f ? 700 : 260); i > 0; i--) {
            if (f.a < 0.999f) {
                double ang = rnd() * 6.283, r = (1.6 + 1.5 * rnd()) * PU;
                GaaraSand.Grain gr = GaaraSand.grain(at.add(Math.cos(ang) * r, -0.35, Math.sin(ang) * r),
                        new Vec3(-Math.cos(ang) * r * 2.4, (1.5 + rnd()) * PU, -Math.sin(ang) * r * 2.4), at.y - 0.4);
                gr.grav = 6 * PU;
                gr.life = 0.35f + 0.2f * rnd();
            } else {
                Vec3 q = world(F, (rnd() - 0.5f) * 1.4f, -0.5f - 0.4f * rnd(), (rnd() - 0.5f) * 1.4f);
                GaaraSand.Grain gr = GaaraSand.grain(q, vel.scale(0.15 * rnd()).add(0, -(1 + rnd()) * PU, 0), floor);
                gr.grav = 9 * PU;
            }
        }
        if (v > 1) {
            Vec3 dir = vel.normalize();
            for (int i = rate((float) (v * 0.6 * 60 / PU)); i > 0; i--) {
                Vec3 q = at.add(dir.scale(-(1.1 + rnd() * 0.6) * PU)).add((rnd() - 0.5) * 1.4, -0.3 + (rnd() - 0.5) * 0.4, (rnd() - 0.5) * 1.4);
                GaaraSand.Grain gr = GaaraSand.grain(q, new Vec3(rnd() - 0.5, (rnd() - 0.5) * 0.4, rnd() - 0.5).scale(PU), floor);
                gr.grav = 0.6f * PU;
                gr.drag = 1.5f;
                gr.life = 0.6f + 0.9f * rnd();
                gr.s = (0.02f + 0.03f * rnd()) * PU;
            }
        }
    }

    // B / V quicksand: a spiral of sand turning into the middle, almost hidden until someone sinks (souls_ki kind 35).
    // pos = center on the ground, dir.x = radius (blocks), a = how visible, b = sinking.
    private static void gaQuick(Fx f, float t, float out) {
        float alpha = f.a * out;
        if (alpha <= 0.002f) {
            return;
        }
        if (DbFx.pass && KiMesh.ready()) {
            KiMesh.P k = KiMesh.p(KiMesh.SAND_QUICK);
            k.alpha = alpha;
            k.pulse = f.b;
            k.rot = t / 20f * (0.4f + 2.6f * f.b);
            k.time = t / 20f;
            k.glow = false;
            k.color = GaaraSand.BASE;
            k.color2 = GaaraSand.SHADE;
            k.dust = GaaraSand.DEEP;
            k.size = GaaraSand.LIGHT;
            KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(add(rel(f.pos), 0, 0.03f, 0), (float) f.dir.x), k, KiMesh.BOTH, 5);
        }
        if (!DbFx.pass && f.b > 0.05f) {
            float r = (float) f.dir.x;
            for (int i = rate(30 * f.b * Math.min(6, r)); i > 0; i--) {
                double ang = rnd() * 6.283, rr = r * 0.8 * Math.sqrt(rnd());
                GaaraSand.grain(f.pos.add(Math.cos(ang) * rr, 0.05, Math.sin(ang) * rr), new Vec3((rnd() - 0.5) * 1.3, (1.5 + rnd()) * PU, (rnd() - 0.5) * 1.3), f.pos.y);
            }
        }
    }

    // V Sabaku Taiso: the ring of sand rising round the area (a profile turned round it), curling in and closing in a dome,
    // then falling. pos = center on the ground, dir = (area radius in page units, crumble, squash), a = height, b = curl.
    private static void gaWave(Fx f, float t, float out) {
        if (DbFx.pass) {
            return;
        }
        float R = (float) f.dir.x, ext = f.a, phi = f.b, thick = 1.6f, sy = (float) Math.max(0.05, f.dir.z <= 0 ? 1 : f.dir.z);
        float sx = 1 + 0.08f * (1 - sy) / 0.95f;
        float dis = Math.max((float) f.dir.y, f.endAt >= 0 ? 1 - out : 0);
        int N = 22;
        float[][] outer = new float[N + 1][], inner = new float[N + 1][];
        float x = R + 0.8f, y = 0, step = ext / N;
        for (int i = 0; i <= N; i++) {
            float s = (float) i / N, a = phi * (float) Math.pow(s, 1.35), w = thick * (1.25f * (1 - s) + 0.28f) * (1 + 0.25f * Mth.sin(s * 9));
            float nx = Mth.cos(a), ny = Mth.sin(a);
            outer[i] = new float[] {Math.max(0.05f, x + nx * w * 0.5f), y + ny * w * 0.5f};
            inner[i] = new float[] {Math.max(0.05f, x - nx * w * 0.5f), Math.max(-0.2f, y - ny * w * 0.5f)};
            x -= Mth.sin(a) * step;
            y += Mth.cos(a) * step;
        }
        float[][] pts = new float[2 * N + 4][];
        pts[0] = new float[] {R + 0.8f + thick, -0.3f};
        for (int i = 0; i <= N; i++) {
            pts[1 + i] = outer[i];
            pts[2 * N + 2 - i] = inner[i];
        }
        pts[2 * N + 3] = new float[] {R + 0.8f - thick, -0.3f};
        Vector3f c = rel(f.pos);
        float tipX = x, tipY = y;
        GaaraSand.piece(0.32f, 1.1f, 0.7f, dis, 99, 9, m -> m.lathe(new Matrix4f().translation(c).scale(PU * sx, PU * sy, PU * sx), pts, 72));
        if (dis <= 0 && sy > 0.9f) {
            for (int i = rate(900); i > 0; i--) {
                double ang = rnd() * 6.283, r = Math.max(0.3, tipX) * PU;
                GaaraSand.Grain gr = GaaraSand.grain(f.pos.add(Math.cos(ang) * r, (tipY + (rnd() - 0.5) * 0.6) * PU, Math.sin(ang) * r),
                        new Vec3(-Math.cos(ang) * 1.5 * rnd(), -0.5 - 2 * rnd(), -Math.sin(ang) * 1.5 * rnd()).scale(PU), f.pos.y);
                gr.grav = 9 * PU;
                gr.life = 1 + 0.6f * rnd();
                gr.s = (0.04f + 0.08f * rnd() * rnd()) * PU;
            }
        }
    }

    // The passive Sand Shield: the sand leaves the gourd on its own over his shoulder, runs to the ground and rises in a
    // curved wall toward the blow (pos = his feet, dir = toward it); what hits sticks; then it crumbles and runs back.
    private static void gaWall(Fx f, float age) {
        if (DbFx.pass) {
            return;
        }
        float t = age / 20f;
        Vector3f g = rel(f.pos);
        Vector3f fwd = vec(f.dir);
        fwd.y = 0;
        if (fwd.lengthSquared() < 1e-6f) {
            fwd.set(0, 0, 1);
        }
        fwd.normalize();
        Matrix4f F = GaaraSand.frame(g, fwd);
        float prog, riseY, dis = 0;
        if (t < 0.3f) {
            prog = clamp(t / 0.16f, 0, 1);
            riseY = -0.2f + 3.1f * e3((t - 0.1f) / 0.18f);
        } else {
            float t2 = t - 0.3f;
            dis = smooth((t2 - 1.3f) / 0.7f);
            prog = 1 - smooth((t2 - 1.6f) / 0.5f);
            riseY = 3;
        }
        float rise = clamp((t - 0.1f) / 0.18f, 0, 1);
        if (rise > 0 && dis < 1) {
            float ry = g.y + riseY * PU;
            float d = dis;
            GaaraSand.piece(0.16f, 0.7f, 1, d, ry, 9,
                    m -> m.cylinder(new Matrix4f(F).translate(0, 1.35f, 0), 1.45f, 1.6f, 2.7f, 60, 10, true, Mth.PI - 1.25f, 2.5f));
        }
        if (prog > 0.001f) {
            Vector3f[] pts = {v(0.37f, 1.73f, 0.38f), v(0.87f, 2.18f, 0.28f), v(0.7f, 1.5f, -0.7f), v(0.5f, 0.25f, -1.2f), v(0, 0.08f, -1.55f)};
            GaaraSand.piece(0.1f, 1.6f, 1.3f, 0, 99, prog, m -> m.tube(F, pts,
                    s -> 0.16 * (0.55 + 0.45 * Math.sin(Math.PI * Math.min(1, s * 1.2 + 0.08))), 40, 10, 0.5f));
        }
        double floor = f.pos.y;
        if (rise > 0 && rise < 1) {
            for (int i = rate(360); i > 0; i--) {
                float a = Mth.PI - 1.2f + 2.4f * rnd();
                GaaraSand.grain(world(F, Mth.sin(a) * 1.5f, 2.9f * e3(rise), Mth.cos(a) * 1.5f), new Vec3(0, (1 + 2 * rnd()) * PU, 0), floor);
            }
        }
        if (dis > 0 && dis < 1) {
            for (int i = rate(540); i > 0; i--) {
                float a = Mth.PI - 1.2f + 2.4f * rnd();
                GaaraSand.Grain gr = GaaraSand.grain(world(F, Mth.sin(a) * 1.5f, 0.2f + 2.6f * rnd(), Mth.cos(a) * 1.5f), new Vec3(0, -0.45, 0), floor);
                gr.life = 0.6f + 0.6f * rnd();
            }
        }
        if (t > 0.33f && t < 1.5f) {
            for (int i = rate(120); i > 0; i--) {
                float a = Mth.PI - 1.2f + 2.4f * rnd();
                GaaraSand.Grain gr = GaaraSand.grain(world(F, Mth.sin(a) * 1.48f, 2.8f, Mth.cos(a) * 1.48f), new Vec3(0, -0.27, 0), floor);
                gr.grav = 6 * PU;
            }
        }
    }

    // ---------- Shikamaru: shadows ----------

    // The shadow arm climbing the body in a spiral to the neck (one curve, thick at the ground, its own turns), with the
    // blot on the ground it rose from.
    private static void bodyShadow(Fx f, float t, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        Vector3f feet = rel(e.getPosition(partial));
        float k = f.a;
        float s = e.getBbHeight() / 1.8f;
        float r0 = Math.max(0.3f, e.getBbWidth() * 0.62f);
        dsk(add(feet, 0, 0.026f, 0), UP, r0 * 1.9f, FxDraw.DISC, 0.2f, SHADOW, 0.95f * out, false);
        if (k <= 0.13f) {
            return;
        }
        int seed = Math.floorMod(f.id, 61);
        float turns = 1.15f + 0.2f * hash(seed, 1, 1), a0 = hash(seed, 1, 2) * Mth.TWO_PI, top = (1.48f - 0.25f) * s;
        int n = 40;
        Vector3f last = null;
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            if (u > k) {
                break;
            }
            float ang = a0 + u * turns * Mth.TWO_PI, rr = r0 + 0.05f * Mth.sin(u * 9 + seed) + (1 - u) * 0.12f;
            Vector3f p = add(feet, Mth.cos(ang) * rr, 0.02f + u * top, Mth.sin(ang) * rr);
            if (last != null) {
                float w = (0.17f + 0.1f * (1 - u) + 0.03f * Mth.sin(u * 23 + seed)) * s;
                rib(last, p, w, FxDraw.SOLID, 0, SHADOW, 0.95f * out, false);
            }
            last = p;
        }
    }

    // The shadow hand closing on the neck, finger by finger.
    private static void neckHand(Fx f, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        float a = f.a;
        Vec3 look = e.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vector3f fw = vec(flat);
        Vector3f side = new Vector3f(fw).cross(UP).normalize();
        float s = e.getBbHeight() / 1.8f;
        Vector3f neck = rel(e.getPosition(partial).add(0, 1.47 * s, 0));
        for (int fi = 0; fi < 5; fi++) {
            float close = smooth((a - fi * 5) / 8);
            Vector3f last = at(at(add(neck, 0, -0.27f * s, 0), side, (fi - 2) * 0.08f * s), fw, -0.27f * s);
            for (int j = 1; j <= 6; j++) {
                float u = j / 6f;
                float ang = -Mth.PI / 2 + (fi - 2) * 0.35f + u * (1.6f + fi * 0.1f) * close * (fi % 2 != 0 ? 1 : -1) * 1.4f;
                Vector3f p = at(at(add(neck, 0, ((fi - 2) * 0.03f + u * 0.06f) * s, 0), side, Mth.cos(ang) * 0.29f * s), fw, Mth.sin(ang) * 0.29f * s);
                if (j == 1) {
                    p.lerp(last, 0.5f);
                }
                rib(last, p, 0.1f * s, FxDraw.SOLID, 0, SHADOW, out, false);
                last = p;
            }
        }
    }

    // A Kage Nui needle (forja/shikamaru buildNeedle): it runs low out of his shadow to the target, rises crooked
    // through them and comes out sharp above. One curve per target, each with its own swing, lean and height.
    private static void spikes(Fx f, float out) {
        float k0 = f.a * out;
        if (k0 <= 0) {
            return;
        }
        Vector3f at = rel(f.pos);
        Vector3f back = vec(f.dir);
        back.y = 0;
        float L = back.length();
        if (L < 1.0E-3f) {
            back.set(0, 0, 1);
            L = 1;
        }
        back.div(L);
        Vector3f side = v(-back.z, 0, back.x);
        int seed = (int) f.b * 31 + Math.floorMod(f.id, 17);
        float swing = (hash(seed, 2, 1) - 0.5f) * 1.6f, lean = (hash(seed, 3, 3) - 0.5f) * 0.9f, up = 3.2f + 1.2f * hash(seed, 3, 4);
        float run = Math.min(2.6f, L);
        float w = 0.12f + 0.05f * hash(seed, 4, 5);
        int n = 34;
        Vector3f last = null;
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            if (u > k0 * 1.02f) {
                break;
            }
            Vector3f p;
            if (u < 0.3f) {
                float g = u / 0.3f;                                    // low along the ground, swinging aside
                p = at(at(at, back, run * (1 - g)), side, Mth.sin(g * Mth.PI) * swing * 0.5f);
                p.y += 0.05f + 0.3f * g * g;
            } else {
                float g = (u - 0.3f) / 0.7f;                           // up through the body, crooked
                p = at(at(at, side, lean * g * g), back, -0.35f * g * g);
                p.y += 0.35f + (up - 0.35f) * g;
            }
            if (last != null) {
                float ww = u < 0.72f ? w * (0.75f + 0.25f * Mth.sin(u * 13 + seed)) : w * (float) Math.pow(Math.max(0, 1 - (u - 0.72f) / 0.28f), 1.3) + 0.004f;
                rib(last, p, ww * 2, FxDraw.SOLID, 0, SHADOW, out, false);
            }
            last = p;
        }
    }

    // The paper bomb: a kunai stuck in the ground with the paper hanging from it, the seal burning from the bottom up
    // (b) before it goes off. a = size (the Xeque-mate's rise bigger).
    private static void tag(Fx f, float t, float out) {
        Vector3f p = rel(f.pos);
        float s = f.a > 0 ? f.a : 1;
        float burn = clamp(f.b, 0, 1);
        if (!DbFx.pass && out > 0) {
            DbFx.box(add(p, 0, 0.02f * s, 0), add(p, 0.05f * s, 0.62f * s, 0.04f * s), 0.035f * s, KUNAI, out);
        }
        Vector3f pc = add(p, 0.09f * s, 0.42f * s, 0.03f);
        float left = 1 - burn;
        obox(add(pc, 0, -0.21f * s * burn, 0), v(0.1f * s, 0, 0.012f * s), v(0, 0.21f * s * left + 0.004f, 0), v(0, 0, 0.004f), PAPER, out);
        obox(add(pc, 0, 0.02f * s - 0.21f * s * burn, 0.006f), v(0.022f * s, 0, 0), v(0, 0.16f * s * left + 0.004f, 0), v(0, 0, 0.003f), PAPER_RED, out);
        if (burn > 0) {
            Vector3f edge = add(pc, 0, -0.21f * s + 0.42f * s * burn, 0.01f);
            bb(edge, (0.18f + 0.2f * burn) * s, t * 0.3f, FxDraw.SOFT, 0.6f, KATON_STAR2, 0.9f * out, true);
            bb(edge, 0.08f * s, 0, FxDraw.SOFT, 0.6f, KATON_STAR, out, true);
        } else if (((int) Math.floor(t / 10)) % 2 != 0) {
            bb(add(pc, 0, 0, 0.02f), 0.22f * s, 0, FxDraw.SOFT, 0.6f, RED, 0.3f * out, true);
        }
    }

    // ---------- Shikamaru: the shadow on the ground, the flood, the flare (forja/shikamaru, souls_ki kinds 15 and 16) ----------

    private static final float[] FLARE_COL = FxDraw.rgb(0xFFF1D6);
    private static final float[] FLARE_HOT = FxDraw.rgb(0xFFB070);

    // The way the shadow runs: ONE shape drawn by the shader (snaking, thinner in the middle, fingers feeling ahead at
    // the tip, the blot that catches), from his own round shadow.
    private static void shadowGround(Fx f, float age, float out) {
        float prog = Math.min(1, Math.max(0, f.a));
        float grab = clamp(f.a - 1, 0, 1);
        boolean stretch = f.b < 0;
        float w = Math.abs(f.b);
        Vector3f from = rel(f.pos);
        if (!KiMesh.ready()) {
            shadowPath(from, rel(f.pos.add(f.dir)), prog * out, w, age, f.id % 9, 0.94f);
            return;
        }
        if (!DbFx.pass) {
            return;
        }
        Vector3f d = vec(f.dir);
        d.y = 0;
        float L = d.length();
        Vector3f u = L < 1.0E-3f ? v(0, 0, 1) : new Vector3f(d).div(L);
        float halfL = L * 0.5f + 1.4f, halfW = w * 1.6f + 1.1f;
        Vector3f c = at(from, u, L * 0.5f);
        c.y += 0.03f;
        org.joml.Matrix4f model = new org.joml.Matrix4f().translation(c).rotateY((float) Math.atan2(u.x, u.z)).scale(halfW, 1, halfL);
        KiMesh.P p = KiMesh.p(KiMesh.SHADOW_PATH);
        p.len = L;
        p.growth = prog;
        p.thresh = grab;
        p.w = w;
        p.twist = stretch ? 1 : 0;
        p.pulse = 1;
        p.seed = Math.floorMod(f.id, 9) * 7.3f + 1;
        p.time = age / 20f;
        p.alpha = out;
        p.glow = false;
        KiMesh.queue(KiMesh.Mesh.QUAD, model, p, KiMesh.FRONT, 3);
    }

    // The Xeque-mate's flood: a disc of shadow whose edge licks forward in tongues.
    private static void shadowSea(Fx f, float age, float out) {
        if (!DbFx.pass || !KiMesh.ready() || f.a <= 0.01f) {
            return;
        }
        Vector3f c = add(rel(f.pos), 0, 0.03f, 0);
        KiMesh.P p = KiMesh.p(KiMesh.SHADOW_SEA);
        p.ringR = f.a;
        p.seed = Math.floorMod(f.id, 13) * 3.1f;
        p.time = age / 20f;
        p.alpha = out;
        p.glow = false;
        KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(c, f.a + 2.4f), p, KiMesh.FRONT, 3);
    }

    // The signal flare: a white-hot point with a warm glow round it, hanging in the sky.
    private static void flare(Fx f, float age, float out) {
        Vector3f c = rel(f.pos);
        float k = f.a * out;
        if (k <= 0.01f) {
            return;
        }
        float flick = 0.9f + 0.1f * Mth.sin(age * 1.7f) * Mth.sin(age * 0.63f + 1);
        DbFx.flashBall(c, 0.35f + 0.1f * flick, k, FLARE_COL, 1.3f);
        DbFx.flashBall(c, 2.2f * flick, 0.35f * k, FLARE_HOT, 4.1f);
        bb(c, 6.5f * flick, age * 0.02f, FxDraw.SOFT, 0.5f, FLARE_COL, 0.35f * k, true);
        if (f.b > 0.5f) {
            Vector3f tail = add(c, 0, -1.6f, 0);
            rib(tail, c, 0.12f, FxDraw.STREAK, 0.9f, FLARE_HOT, 0.8f * k, true);
        }
    }

    private static void kunai(Fx f, float age) {
        Vector3f from = rel(f.pos);
        Vector3f to = rel(f.pos.add(f.dir));
        float k = clamp(age / Math.max(1, f.a), 0, 1);
        Vector3f p = lerp(from, to, k);
        p.y += Mth.sin(k * Mth.PI) * 0.6f;
        Vector3f d = new Vector3f(to).sub(from).normalize();
        rib(at(p, d, -0.4f), p, 0.05f, FxDraw.SOLID, 0, KUNAI, 1, false);
    }

    // The explosion in the style of Madara's Katon (v39): only the shader's fire, flames born white-yellow in the middle in
    // the first ticks, flying out and up, growing red and big, some turning to dark smoke.
    static void cartoonBoom(Vector3f at, float age, float r, int seed, float ground) {
        if (age < 0 || age > 80) {
            return;
        }
        Vector3f floor = v(at.x, ground, at.z);
        if (age < 6) {
            bb(at, r * 4 * (1 - age / 7), 0, FxDraw.SOFT, 0.5f, KB_COR2, 1 - age / 6, true);
            bb(at, r * 2.6f, 0.2f, FxDraw.FLARE, 10 / 32f, new float[] {1, 1, 0.9f}, 1 - age / 6, true);
        }
        int emit = 12;
        int per = 26;
        int newest = (int) Math.floor(age);
        for (int born = Math.max(0, newest - 40); born <= Math.min(newest, emit); born++) {
            for (int k = 0; k < per; k++) {
                float h = hash(seed + born, k, 0);
                float a = age - born - h;
                float life = (22 + 16 * hash(seed + born, k, 1)) * (1 - born / (float) emit * 0.35f);
                if (a < 0 || a > life) {
                    continue;
                }
                float k01 = a / life;
                Vector3f d = sphere(seed * 13 + born, k);
                d.y = Math.abs(d.y) * 0.75f + 0.2f;
                d.normalize();
                float reach = r * (0.45f + 0.75f * hash(seed + born, k, 3)) * (1 - born / (float) emit * 0.5f);
                float dist = reach * easeOut(Math.min(1, a / 10));
                Vector3f pos = add(at(at, d, dist), 0, k01 * k01 * r * 0.9f, 0);
                float s = Mth.lerp(Math.min(1, k01 * 1.4f), 0.4f, r * 0.9f) * (0.7f + 0.6f * hash(seed + born, k, 4));
                float[] c = k01 < 0.35f ? mix(KB_COR2, KB_COR, k01 / 0.35f) : mix(KB_COR, mix(KB_COR, KB_SMOKE, 0.6f), (k01 - 0.35f) / 0.65f);
                float o = 1 - smooth((k01 - 0.7f) / 0.3f);
                if (k % 7 == 0 && k01 > 0.5f) {
                    bb(add(pos, 0, r * 0.3f, 0), s * 1.5f, h * 6, FxDraw.SMOKE, k01, KB_SMOKE, o * 0.7f, false);
                } else {
                    bb(pos, s, h * 6 + a * 0.05f, FxDraw.FIRE, k01 * 0.85f, c, o, true);
                }
            }
        }
        if (age > 10) {
            for (int i = 0; i < 12; i++) {
                float k = clamp((age - 10) / 60, 0, 1);
                float ang = hash(seed, i, 51) * Mth.TWO_PI;
                float rr = r * (0.3f + 0.6f * hash(seed, i, 52));
                bb(add(at, Mth.cos(ang) * rr, r * (0.6f + 1.6f * k) + hash(seed, i, 53) * r * 0.6f, Mth.sin(ang) * rr), r * (0.9f + 0.8f * k), i + age * 0.01f,
                        FxDraw.SMOKE, 0.3f + k * 0.4f, KB_SMOKE, 0.65f * smooth((age - 10) / 10) * (1 - smooth((age - 55) / 25)), false);
            }
        }
        if (age < 16) {
            dsk(add(floor, 0, 0.06f, 0), UP, r * 3.6f * easeOut(age / 14), FxDraw.RING, 0.12f, KB_COR, 0.9f * (1 - age / 16), true);
        }
        dsk(add(floor, 0, 0.03f, 0), UP, r * 2.4f, FxDraw.SOFT, 0.1f, new float[] {0.05f, 0.04f, 0.04f}, 0.7f * smooth(age / 5) * (1 - smooth((age - 60) / 20)), false);
        nrDust(floor, age - 4, seed + 90, 14, r * 0.8f, new float[] {0.35f, 0.3f, 0.27f}, 50);
    }

    // ---------- Ino: mind ----------

    // Her spirit: a see-through purple copy of her, flying leaned forward, sparks trailing behind.
    private static void spirit(Fx f, float t, float out) {
        Vector3f p = rel(f.pos);
        float a = f.a * out;
        if (a <= 0.01f) {
            return;
        }
        Vector3f fw = vec(f.dir);
        fw.y = 0;
        if (fw.lengthSquared() < 1.0E-4f) {
            fw.set(0, 0, 1);
        }
        fw.normalize();
        Vector3f side = new Vector3f(fw).cross(UP).normalize();
        // Leaned 20 degrees forward.
        Vector3f up = new Vector3f(UP).mul(Mth.cos(0.35f)).add(new Vector3f(fw).mul(Mth.sin(0.35f)));
        Vector3f back = new Vector3f(fw).mul(Mth.cos(0.35f)).sub(new Vector3f(UP).mul(Mth.sin(0.35f)));
        float[] col = {0.75f, 0.5f, 1f};
        float op = 0.45f * a;
        Vector3f hip = add(p, 0, 0.75f, 0);
        obox(at(hip, up, 0.37f), new Vector3f(side).mul(0.23f), new Vector3f(up).mul(0.35f), new Vector3f(back).mul(0.12f), col, op);
        obox(at(hip, up, 0.95f), new Vector3f(side).mul(0.23f), new Vector3f(up).mul(0.23f), new Vector3f(back).mul(0.23f), col, op);
        for (int i = -1; i <= 1; i += 2) {
            obox(at(at(at(hip, side, i * 0.12f), UP, -0.34f), fw, -0.15f), new Vector3f(side).mul(0.11f), new Vector3f(up).mul(0.34f), new Vector3f(back).mul(0.11f), col, op);
            obox(at(at(at(hip, up, 0.4f), side, i * 0.34f), fw, -0.12f), new Vector3f(side).mul(0.09f), new Vector3f(up).mul(0.32f), new Vector3f(back).mul(0.09f), col, op);
        }
        bb(add(p, 0, 0.9f, 0), 2.2f, 0, FxDraw.SOFT, 0.2f, PURPLE, 0.35f * a, true);
        for (int i = 0; i < 8; i++) {
            float u = fract(t * 0.08f + i / 8f);
            bb(at(at(add(p, 0, 0.4f + hash(i, 18, 2) * 1.2f, 0), side, (hash(i, 18, 1) - 0.5f) * 0.5f), fw, -u * 1.4f), 0.12f, 0, FxDraw.SOFT, 0.8f, LILAC,
                    0.7f * a * (1 - u), true);
        }
    }

    private static void mindWave(Fx f, float a) {
        Vector3f head = rel(f.pos);
        Vector3f n = vec(f.dir).normalize();
        if (a >= 16) {
            return;
        }
        float k = a / 12;
        for (int i = 0; i < 3; i++) {
            float r = 1 + 8 * clamp(k - i * 0.1f, 0, 1);
            dsk(at(head, n, r * 0.95f), n, r * 0.9f, FxDraw.RING, 0.04f, PURPLE, 0.8f * (1 - a / 16), true);
        }
    }

    private static void confused(Fx f, float t, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        Vector3f c = rel(e.getPosition(partial).add(0, e.getBbHeight() + 0.05, 0));
        for (int i = 0; i < 6; i++) {
            float ang = t * (0.25f + i * 0.05f) * (i % 2 != 0 ? 1 : -1) + i;
            float r = 0.32f + 0.05f * Mth.sin(t * 0.3f + i);
            rib(add(c, Mth.cos(ang) * r, Mth.sin(ang * 1.7f) * 0.06f, Mth.sin(ang) * r),
                    add(c, Mth.cos(ang + 0.6f) * r, Mth.sin((ang + 0.6f) * 1.7f) * 0.06f, Mth.sin(ang + 0.6f) * r), 0.05f, FxDraw.STREAK, 0.8f, LILAC, 0.9f * out, true);
        }
        bb(c, 0.9f, 0, FxDraw.SOFT, 0.2f, PURPLE, 0.35f * out, true);
    }

    static void thread(Vector3f a, Vector3f b, float k, float op, float t) {
        if (op <= 0.01f || k <= 0) {
            return;
        }
        Vector3f last = a;
        for (int j = 1; j <= 14; j++) {
            float u = j / 14f * k;
            Vector3f p = add(lerp(a, b, u), 0, Mth.sin(u * Mth.PI) * 0.4f + Mth.sin(u * 12 + t * 0.2f) * 0.04f, 0);
            rib(last, p, 0.035f, FxDraw.STREAK, 0.9f, LILAC, op, true);
            last = p;
        }
        if (k >= 0.99f) {
            bb(b, 0.5f, 0, FxDraw.SOFT, 0.5f, PURPLE, 0.6f * op, true);
        }
    }

    private static void pulse(Fx f, float age) {
        Vector3f a = rel(f.pos);
        Vector3f b = rel(f.pos.add(f.dir));
        float k = age / 8;
        if (k > 0 && k < 1) {
            bb(add(lerp(a, b, k), 0, Mth.sin(k * Mth.PI) * 0.4f, 0), 0.5f, 0, FxDraw.SOFT, 0.9f, LILAC, 1, true);
        }
    }

    // ---------- Kakashi (forja/kakashi-kit v13: kakashi.js houndBolts, VORT_F; kakashi2.js NINKEN, SEAL_F) ----------

    private static final float[] KK_BODY = FxDraw.rgb(0xD6ECFF);
    private static final float[] KK_EDGE = FxDraw.rgb(0x4F86FF);
    private static final float[] VORT_BODY = FxDraw.rgb(0x5FC8FF);
    private static final float[] VORT_EDGE = FxDraw.rgb(0x122A8F);
    private static final float[] STAR = FxDraw.rgb(0xFFF6C8);
    // One page unit is 0.9 blocks (the page's Kakashi is 2 units tall).
    private static final float PU = 0.9f;

    // A bolt through several points (the page's chainBolt): each stretch its own seed, finer the longer it is.
    private static void chain(Vector3f[] pts, int seed, float w, float[] col, float op) {
        for (int i = 0; i < pts.length - 1; i++) {
            int segs = Math.max(3, Math.round(pts[i].distance(pts[i + 1]) * 9 / PU));
            nrBolt(pts[i], pts[i + 1], seed + i * 7, w, col, op, 0.18f, segs, 0);
        }
    }

    // Raiju Tsuiga: the hound drawn only with bolts, each stroke with its own job (the page's houndBolts): back to the
    // snout, jaw, chest and belly, pointed ears, four legs in a gallop, lightning fur crackling off the back and two bolts
    // dragging behind on the ground. pos = its feet on the ground, dir = where it runs, a = gait, b = jaw.
    private static void hound(Fx f, float t, float out) {
        Vector3f p = rel(f.pos);
        Vector3f dir = vec(f.dir);
        dir.y = 0;
        if (dir.lengthSquared() < 1.0E-4f) {
            dir.set(0, 0, 1);
        }
        dir.normalize();
        Vector3f side = v(dir.z, 0, -dir.x);
        float sc = 1.7f * PU, gait = f.a, jaw = f.b, k = out;
        int tk = (int) Math.floor(t);
        java.util.function.BiFunction<float[], Float, Vector3f> L = (fu, s) -> at(at(add(p, 0, fu[1] * sc, 0), dir, fu[0] * sc), side, s * sc);
        float bob = Mth.sin(gait * 2) * 0.05f, stretch = Mth.sin(gait) * 0.12f;
        Vector3f hip = L.apply(new float[] {-0.6f - stretch * 0.5f, 0.62f + bob}, 0f), sh = L.apply(new float[] {0.45f + stretch * 0.5f, 0.72f - bob}, 0f);
        Vector3f neck = L.apply(new float[] {0.72f, 0.98f}, 0f), head = L.apply(new float[] {0.98f, 1.1f}, 0f);
        Vector3f snout = L.apply(new float[] {1.48f, 1.0f}, 0f), nose = L.apply(new float[] {1.55f, 0.94f}, 0f);
        Vector3f jawTip = L.apply(new float[] {1.42f, 0.86f - 0.22f * jaw}, 0f), throat = L.apply(new float[] {1.0f, 0.8f}, 0f);
        Vector3f tail = L.apply(new float[] {-1.05f, 0.98f + 0.12f * Mth.sin(gait * 1.7f)}, 0.08f * Mth.sin(gait * 2.3f));
        float w = 0.06f * PU * k;
        chain(new Vector3f[] {tail, hip, sh, neck, head, snout, nose}, 2001 + tk, w * 1.3f, BOLT, k);
        chain(new Vector3f[] {nose, jawTip, throat, L.apply(new float[] {0.6f, 0.48f}, 0f), L.apply(new float[] {-0.1f, 0.42f + bob}, 0f),
                L.apply(new float[] {-0.62f, 0.48f}, 0f), hip}, 2101 + tk, w, BOLT, k);
        for (int j = 0; j < 2; j++) {
            float s = j == 0 ? -1 : 1;
            chain(new Vector3f[] {L.apply(new float[] {0.86f, 1.12f}, 0.1f * s), L.apply(new float[] {0.82f, 1.42f}, 0.14f * s),
                    L.apply(new float[] {1.02f, 1.16f}, 0.1f * s)}, 2201 + j * 13 + tk, w * 0.8f, BOLT_HOT, k);
            float fa = gait + (j == 1 ? 0.6f : 0), ba = gait + Mth.PI + (j == 1 ? 0.5f : 0);
            Vector3f fk = L.apply(new float[] {0.5f + 0.25f * Mth.sin(fa), 0.32f + 0.08f * Mth.cos(fa)}, 0.16f * s);
            Vector3f fp = L.apply(new float[] {0.55f + 0.45f * Mth.sin(fa), Math.max(0.02f, 0.1f * Mth.cos(fa) + 0.04f)}, 0.16f * s);
            Vector3f bk = L.apply(new float[] {-0.5f + 0.22f * Mth.sin(ba), 0.36f}, 0.17f * s);
            Vector3f bp = L.apply(new float[] {-0.62f + 0.42f * Mth.sin(ba), Math.max(0.02f, 0.1f * Mth.cos(ba) + 0.04f)}, 0.17f * s);
            chain(new Vector3f[] {L.apply(new float[] {0.45f, 0.66f}, 0.15f * s), fk, fp}, 2301 + j * 17 + tk, w * 1.1f, BOLT, k);
            chain(new Vector3f[] {L.apply(new float[] {-0.55f, 0.6f}, 0.15f * s), bk, bp}, 2401 + j * 17 + tk, w * 1.1f, BOLT, k);
        }
        // lightning fur: short crackles off the back, each with its own life and size
        for (int i = 0; i < 9; i++) {
            int life = 1 + (int) Math.floor(hash(77, i, 1) * 3);
            int ep = (int) Math.floor((tk + hash(77, i, 2) * life) / life);
            Vector3f a = add(lerp(hip, sh, hash(77, i, ep)), 0, 0.05f, 0);
            Vector3f d = v((hash(78, i, ep) - 0.5f) * 1.2f, 0.6f + 0.6f * hash(79, i, ep), (hash(80, i, ep) - 0.5f) * 1.2f).normalize();
            d = at(d, dir, -0.7f).normalize();
            nrBolt(a, at(a, d, (0.2f + 0.35f * hash(81, i, ep)) * PU), 2600 + i * 9 + ep, 0.016f * PU * k, BOLT_HOT, 0.9f * k, 0.4f, 4, 0);
        }
        // the trail: two bolts left behind stuck to the ground
        for (int i = 0; i < 2; i++) {
            int ep = tk / 2;
            Vector3f b = L.apply(new float[] {-1.6f - 1.3f * hash(82, i, ep), 0.03f}, (hash(83, i, ep) - 0.5f));
            nrBolt(hip, b, 2800 + i * 5 + ep, 0.03f * PU * k, BOLT, 0.85f * k, 0.3f, 9, 0);
        }
        Vector3f chest = L.apply(new float[] {0.55f, 0.7f}, 0f);
        float flick = (tk & 1) != 0 ? 0.9f : 1.06f;
        bb(chest, 1.1f * k * flick, 0, FxDraw.SOFT, 0.5f, KK_BODY, 0.55f * k, true);
        bb(chest, 0.6f * k * flick, t * 0.4f, FxDraw.FLARE, 5 / 32f, BOLT_HOT, 0.9f * k, true);
        rib(add(hip, 0, -hip.y + p.y + 0.03f, 0), add(sh, 0, -sh.y + p.y + 0.03f, 0), 0.12f, FxDraw.BEAM, 0.6f, SCORCH, 0.5f * k, false);
    }

    // The Kamui: a ball of space turned into a spiral and swallowed (the page's VORT_F, souls_ki kind 33), the picture of
    // the world twisting round it. life 0..1 how far in; spin < 0 turns it the other way (spitting out).
    static void vortex(Vector3f c, Vector3f axis, float r, float life, float spin, float alpha, float age) {
        if (r <= 0.01f || alpha <= 0.002f) {
            return;
        }
        if (DbFx.pass && KiMesh.ready()) {
            KiMesh.P k = KiMesh.p(KiMesh.KAMUI_VORTEX);
            k.size = new float[] {axis.x, axis.y, axis.z};
            k.rot = spin;
            k.life = Math.max(0, Math.min(1, life));
            k.time = age / 20f;
            k.alpha = alpha;
            k.color = VORT_BODY;
            k.color2 = VORT_EDGE;
            KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(c, r), k, KiMesh.FRONT, 19);
        }
        warp(c, r * 1.65f, (0.35f + 0.65f * Math.max(0, Math.min(1, life))) * alpha, 1, age, axis);
    }

    // X Kamui round the target (a = how open, b = how far in; b < 0 it spits the target back out).
    private static void kamui(Fx f, float t, float out) {
        float open = f.a * out;
        if (open <= 0) {
            return;
        }
        Vector3f c = rel(f.pos);
        Vector3f ax = vec(f.dir);
        if (ax.lengthSquared() < 1.0E-4f) {
            ax.set(0, 0, 1);
        }
        ax.normalize();
        boolean spit = f.b < 0;
        float sec = t / 20f;
        float spin = spit ? sec * 9 : -sec * 7 - sec * sec * 5;
        vortex(c, ax, 1.8f * PU * open, Math.abs(f.b), spin, 1, t);
    }

    // The big Kamui (the shurikens' hit) and Shift+X round himself: a = radius, b = how far in.
    private static void bigVortex(Fx f, float t, float out) {
        Vector3f ax = vec(f.dir);
        if (ax.lengthSquared() < 1.0E-4f) {
            ax.set(0, 0, -1);
        }
        ax.normalize();
        float sec = t / 20f;
        vortex(rel(f.pos), ax, f.a * out, f.b, -sec * 6 - sec * sec * 4, out, t);
    }

    // The ninken seal of blood on the ground (souls_ki kind 34): a = opacity, b = lit.
    private static void seal(Fx f, float t, float out) {
        if (!DbFx.pass || !KiMesh.ready() || f.a * out <= 0.002f) {
            return;
        }
        KiMesh.P k = KiMesh.p(KiMesh.NINKEN_SEAL);
        k.alpha = f.a * out;
        k.pulse = f.b;
        k.time = t / 20f;
        k.glow = f.b > 0.05f;
        KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(add(rel(f.pos), 0, 0.03f, 0), 1.6f * PU), k, KiMesh.BOTH, 8);
    }

    // The eight ninken, each its own (Pakkun small in his vest, Bull huge...): scale, length, height, coat, second color,
    // snout, ears up or down, tail, curled tail, vest.
    private record Ninken(float s, float len, float h, float[] c, float[] c2, float snout, boolean earUp, float tail, boolean curl, boolean vest, String part) {
    }

    private static final Ninken[] NINKEN = {
            new Ninken(0.55f, 0.75f, 0.42f, FxDraw.rgb(0xB98A52), FxDraw.rgb(0x3A4F8A), 0.12f, false, 0.15f, false, true, "arm"),
            new Ninken(1.25f, 0.95f, 0.62f, FxDraw.rgb(0x6B5A4C), FxDraw.rgb(0xD8D0C4), 0.16f, false, 0.12f, false, false, "leg"),
            new Ninken(0.85f, 1.0f, 0.52f, FxDraw.rgb(0x3C3A3A), FxDraw.rgb(0xE8E2D8), 0.3f, true, 0.4f, false, false, "arm"),
            new Ninken(0.8f, 0.95f, 0.5f, FxDraw.rgb(0xD6A35A), FxDraw.rgb(0xF2E8D8), 0.24f, true, 0.3f, true, false, "leg"),
            new Ninken(0.82f, 1.0f, 0.5f, FxDraw.rgb(0x8A6A4A), FxDraw.rgb(0xC9B49A), 0.26f, false, 0.35f, false, false, "arm"),
            new Ninken(0.9f, 1.05f, 0.55f, FxDraw.rgb(0x2C2B30), FxDraw.rgb(0xA8A4B4), 0.28f, true, 0.38f, false, false, "body"),
            new Ninken(0.88f, 1.0f, 0.56f, FxDraw.rgb(0x5E4A36), FxDraw.rgb(0xD2B896), 0.22f, true, 0.42f, false, false, "leg"),
            new Ninken(0.86f, 1.1f, 0.5f, FxDraw.rgb(0x9A9590), FxDraw.rgb(0x4A4744), 0.3f, true, 0.45f, false, false, "arm")};
    private static final float[] NOSE = FxDraw.rgb(0x151515);

    // Where ninken i stands round the target and where it came out of the ground (blocks, from the target's feet / the seal).
    public static float slotAngle(int i) {
        return i / 8f * Mth.TWO_PI + 0.3f;
    }

    private static void ldBox(Matrix4f m, float x, float y, float z, float w, float h, float d, float[] col, float op) {
        Vector3f c = m.transformPosition(new Vector3f(x, y, z));
        obox(c, m.transformDirection(new Vector3f(w / 2, 0, 0)), m.transformDirection(new Vector3f(0, h / 2, 0)), m.transformDirection(new Vector3f(0, 0, d / 2)), col,
                op);
    }

    // B Cães Ninja: they come out of the ground round the seal, leap and bite arms, legs and body (a = ticks since).
    private static void dogs(Fx f, float t, float out) {
        if (DbFx.pass) {
            return;
        }
        Vector3f tp = rel(f.pos);
        Vector3f seal = rel(f.dir);
        float sec = t / 20f;
        boolean hold = sec > 0.75f;
        for (int i = 0; i < NINKEN.length; i++) {
            Ninken o = NINKEN[i];
            float a = slotAngle(i), r = (1.0f + 0.25f * o.s) * PU, delay = 0.04f * i + 0.02f * ((i * 5) % 3), lt = sec - delay;
            if (lt < 0) {
                continue;
            }
            Vector3f from = v(seal.x + Mth.cos(a) * 2.6f * PU, seal.y, seal.z + Mth.sin(a) * 2.6f * PU);
            Vector3f goal = v(tp.x + Mth.cos(a) * r, tp.y, tp.z + Mth.sin(a) * r);
            float k = smooth(lt / 0.35f);
            Vector3f p = hold ? goal : lerp(from, goal, k);
            float hop = hold ? 0 : Mth.sin(Math.min(1, lt / 0.35f) * Mth.PI) * 0.9f - 1.2f * (1 - smooth(lt / 0.12f));
            boolean high = o.part.equals("arm");
            p.y += (hop + (high ? 0.35f * k : 0)) * PU;
            float yaw = (float) Math.atan2(-(tp.x - p.x), -(tp.z - p.z));
            float pitch = hold ? (o.part.equals("leg") ? 0.15f : -0.35f) + Mth.sin(sec * 30 + i) * 0.05f : -0.4f * Mth.sin(Math.min(1, lt / 0.35f) * Mth.PI);
            float gait = sec * 18 + i, gk = hold ? 0.15f : 1 - k;
            Matrix4f m = new Matrix4f().translation(p).rotateY(yaw).scale(o.s * PU).rotateX(pitch);
            float L = o.len, H = o.h;
            ldBox(m, 0, H, 0, 0.34f, 0.3f, L, o.c, out);
            if (o.vest) {
                ldBox(m, 0, H + 0.01f, -0.05f, 0.38f, 0.32f, L * 0.5f, o.c2, out);
            } else {
                ldBox(m, 0, H - 0.15f, 0.05f, 0.3f, 0.06f, L * 0.7f, o.c2, out);
            }
            Matrix4f hd = new Matrix4f(m).translate(0, H + 0.2f, -L / 2 - 0.08f);
            ldBox(hd, 0, 0, 0, 0.32f, 0.3f, 0.3f, o.c, out);
            ldBox(hd, 0, -0.06f, -0.15f - o.snout / 2, 0.2f, 0.15f, o.snout, o.c2, out);
            ldBox(hd, 0, -0.01f, -0.15f - o.snout, 0.08f, 0.06f, 0.05f, NOSE, out);
            Matrix4f jw = new Matrix4f(hd).translate(0, -0.16f, -0.14f).rotateX(hold ? 0.05f : 0.5f * (1 - k));
            ldBox(jw, 0, 0, -o.snout / 2, 0.18f, 0.05f, o.snout * 0.9f, o.c2, out);
            for (int s = -1; s <= 1; s += 2) {
                if (o.earUp) {
                    ldBox(hd, 0.11f * s, 0.22f, 0.06f, 0.09f, 0.16f, 0.05f, o.c, out);
                } else {
                    ldBox(new Matrix4f(hd).translate(0.17f * s, 0.08f, 0.06f).rotateZ(0.5f * s), 0, 0, 0, 0.09f, 0.12f, 0.05f, o.c, out);
                }
                ldBox(hd, 0.08f * s, 0.05f, -0.155f, 0.06f, 0.04f, 0.01f, NOSE, out);
            }
            for (int l = 0; l < 4; l++) {
                int sx = l % 2 == 0 ? -1 : 1, sz = l < 2 ? -1 : 1;
                float swing = Mth.sin(gait + (l < 2 ? 0 : Mth.PI) + (l % 2) * 0.4f) * 0.8f * gk;
                Matrix4f lg = new Matrix4f(m).translate(0.11f * sx, H - 0.1f, sz * (L / 2 - 0.1f)).rotateX(swing);
                ldBox(lg, 0, -(H - 0.05f) / 2, 0, 0.1f, H - 0.05f, 0.1f, o.c, out);
            }
            Matrix4f tl = new Matrix4f(m).translate(0, H + 0.08f, L / 2).rotateY(Mth.sin(gait * 1.3f) * 0.4f).rotateX(o.curl ? 1.4f : 0.6f);
            ldBox(tl, 0, 0, o.tail / 2, 0.06f, 0.06f, o.tail, o.c, out);
        }
    }

    // Sennen Goroshi: the target turned into a little star blinking high up (a = 1). The title is KakashiTitle.
    private static void star(Fx f, float t) {
        float k = t / 12f;
        if (k >= 1) {
            return;
        }
        Vector3f c = rel(f.pos);
        float s = 0.01f + 0.9f * Mth.sin(Math.min(1, k) * Mth.PI) * 1.6f;
        // an eight-point star: four long points and four short ones, turning
        Vector3f view = new Vector3f(c).normalize();
        Vector3f[] bs = basis(view);
        for (int i = 0; i < 4; i++) {
            float ang = i * Mth.PI / 4 + t * 0.15f;
            float len = (i % 2 == 0 ? 1 : 0.55f) * s;
            Vector3f d = new Vector3f(bs[0]).mul(Mth.cos(ang)).add(new Vector3f(bs[1]).mul(Mth.sin(ang)));
            rib(at(c, d, -len), at(c, d, len), 0.16f * s, FxDraw.STREAK, 0.6f, STAR, 1, true);
        }
        bb(c, 0.9f * s, 0, FxDraw.SOFT, 0.5f, STAR, 0.8f, true);
    }

    // Dust thrown up where something slammed into the ground.
    private static void dust(Fx f, float a) {
        Vector3f p = rel(f.pos);
        nrDust(p, a, 9 + f.id % 7, 14, f.a, DUST, 34);
        if (a < 18) {
            dsk(add(p, 0, 0.05f, 0), UP, 1 + a * 0.4f * f.a / 1.3f, FxDraw.RING, 0.12f, DUST, 1 - a / 18, false);
        }
    }
}
