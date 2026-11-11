package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.bb;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.smooth;
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

import com.starkinhon.battlegroundsmod.client.InoClient;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// The Ino's effects, the approved preview v2 (forja/ino-kit/ino.js) line by line: the same GLSL (souls_ki kinds 17 to 25,
// see KiMesh), the same shapes, sizes and timings. Nothing repeated: the spirit is her own body in mind energy, every
// thread is one tube with its own sag and sway, every flower is built from its own seed, every speck has its own size,
// color, speed and life.
public final class InoFx {
    private static final float[][] MIND = {FxDraw.rgb(0xA46BFF), FxDraw.rgb(0xEFE4FF), FxDraw.rgb(0xFF8FD0), FxDraw.rgb(0xC99CFF)};
    private static final float[][] POLLEN = {FxDraw.rgb(0xFFE27A), FxDraw.rgb(0xFFF3B8), FxDraw.rgb(0xFFC0E4), FxDraw.rgb(0xF7D14A), FxDraw.rgb(0xFFFFFF)};
    private static final float[][] MARK = {FxDraw.rgb(0xFFE27A), FxDraw.rgb(0xFFC0E4), FxDraw.rgb(0xFFF7D0)};
    private static final float[][] SEED = {FxDraw.rgb(0x6B4A2A), FxDraw.rgb(0x8A6236), FxDraw.rgb(0x4F3520)};
    private static final float[][] SPROUT = {FxDraw.rgb(0x6B8A4A)};
    private static final float[] AURA = FxDraw.rgb(0xB47DFF);
    private static final float[] BOOM = FxDraw.rgb(0xB67CFF);
    private static final float[] SHOCK = FxDraw.rgb(0xC9A0FF);
    private static final int TINT = 0xC9A0FF;
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    private static final class Fx {
        final int kind;
        final int id;
        Vec3 pos;
        Vec3 dir;
        Vec3 prevPos;
        Vec3 prevDir;
        float a;
        float b;
        float c;
        float width = 0.02f;
        final double born;
        double updated;
        double endAt = -1;
        double pulseAt = -1;
        boolean pulseBack;
        float[] ground;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();

        Fx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, double now) {
            this.kind = kind;
            this.id = id;
            this.pos = pos;
            this.dir = dir;
            this.prevPos = pos;
            this.prevDir = dir;
            this.a = a;
            this.b = b;
            this.c = c;
            this.born = now;
            this.updated = now;
        }

        // Where it is this frame: between the last two messages (one tick behind, but smooth).
        Vec3 at(double now) {
            return prevPos.lerp(pos, Mth.clamp(now - updated, 0, 1));
        }

        Vec3 dirAt(double now) {
            return prevDir.lerp(dir, Mth.clamp(now - updated, 0, 1));
        }
    }

    private static final List<Fx> SHOTS = new ArrayList<>();
    private static final Map<Long, Fx> TRACKED = new HashMap<>();

    private InoFx() {
    }

    static boolean handles(int kind) {
        return kind >= SubNet.INO_SPIRIT && kind <= SubNet.INO_SEEDS;
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    private static boolean stream(int kind) {
        return kind == SubNet.INO_SPIRIT || kind == SubNet.INO_THREAD || kind == SubNet.INO_FIELD || kind == SubNet.INO_MARK
                || kind == SubNet.INO_THEATER || kind == SubNet.INO_STRINGS;
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (kind == SubNet.INO_PILOT || kind == SubNet.INO_INVERT) {
            InoClient.receive(kind, id, a);
            return;
        }
        if (stream(kind)) {
            Fx f = TRACKED.get(key(kind, id));
            if (f == null || f.endAt >= 0) {
                f = new Fx(kind, id, pos, dir, a, b, c, now);
                TRACKED.put(key(kind, id), f);
            } else {
                f.prevPos = f.at(now);
                f.prevDir = f.dirAt(now);
            }
            f.pos = pos;
            f.dir = dir;
            f.a = a;
            f.b = b;
            f.c = c;
            if (kind == SubNet.INO_THREAD && c < 1) {
                f.width = c;
            }
            f.updated = now;
            f.trail.addLast(pos);
            while (f.trail.size() > 8) {
                f.trail.removeFirst();
            }
            if (c == 1) {
                f.endAt = now;
            }
            return;
        }
        if (kind == SubNet.INO_SPIRAL) {
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
        if (kind == SubNet.INO_PULSE && pulse(pos, dir, now)) {
            return;
        }
        if (kind == SubNet.INO_HIT) {
            screen((int) b, pos);
        }
        if (kind == SubNet.INO_WAVE) {
            near(pos, 0.2f, 6);
        }
        SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
    }

    // The damage share running along an Elo thread: lit on the thread it runs on.
    private static boolean pulse(Vec3 from, Vec3 d, double now) {
        Vec3 to = from.add(d);
        for (Fx f : TRACKED.values()) {
            if (f.kind != SubNet.INO_THREAD || f.endAt >= 0) {
                continue;
            }
            Vec3 a = f.pos, b = f.pos.add(f.dir);
            boolean fw = a.distanceTo(from) < 1.5 && b.distanceTo(to) < 1.5;
            boolean bw = b.distanceTo(from) < 1.5 && a.distanceTo(to) < 1.5;
            if (fw || bw) {
                f.pulseAt = now;
                f.pulseBack = bw;
                return true;
            }
        }
        return false;
    }

    private static void near(Vec3 pos, float shake, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float k = (float) Mth.clamp(1 - (mc.player.position().distanceTo(pos) - 16) / 30, 0, 1);
        if (k > 0) {
            ScreenShake.start(shake * k, ticks);
        }
    }

    // The page's hitstop/shake/impact frame for each kind of mind hit (b).
    private static void screen(int which, Vec3 pos) {
        switch (which) {
            case 1 -> {
                near(pos, 0.35f, 8);
                ScreenFx.impactFrame(new int[] {1, 0}, 1, TINT, pos, 0);
            }
            case 2 -> {
                near(pos, 0.7f, 12);
                ScreenFx.impactFrame(new int[] {1, 0, 1}, 1, TINT, pos, 0);
            }
            case 3 -> {
                near(pos, 0.6f, 12);
                ScreenFx.impactFrame(new int[] {1, 0}, 1, TINT, pos, 0);
            }
            case 4 -> {
                near(pos, 1.2f, 20);
                ScreenFx.impactFrame(new int[] {1, 0, 1, 0, 1}, 1, TINT, pos, 0);
            }
            default -> {
            }
        }
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
            case SubNet.INO_SEAL -> f.b >= 1 ? 17 : 11;
            case SubNet.INO_READ -> 13;
            case SubNet.INO_PULSE -> 10;
            case SubNet.INO_SNAP -> 10;
            case SubNet.INO_WAVE -> 14;
            case SubNet.INO_POLLEN -> 56;
            case SubNet.INO_MIND_BOOM -> 48;
            case SubNet.INO_HIT -> 20;
            case SubNet.INO_SEEDS -> 15;
            default -> 20;
        };
    }

    private static float fadeOut(Fx f) {
        return switch (f.kind) {
            case SubNet.INO_MARK -> 22;
            case SubNet.INO_STRINGS -> 8;
            case SubNet.INO_SPIRAL -> 4;
            case SubNet.INO_THREAD -> 3;
            case SubNet.INO_FIELD -> 4;
            default -> 2;
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
            if (f.endAt < 0 && f.kind == SubNet.INO_SPIRAL && now - f.born > f.a) {
                f.endAt = now;
            }
            if (f.endAt >= 0 && now - f.endAt > fadeOut(f)) {
                it.remove();
            }
        }
    }

    static void draw(double now, float partial) {
        if (DbFx.pass) {
            prune(now);
        }
        boolean mesh = KiMesh.ready();
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born);
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case SubNet.INO_SEAL -> seal(f, age, mesh);
                case SubNet.INO_READ -> read(f, age, partial, mesh);
                case SubNet.INO_PULSE -> loosePulse(f, age, mesh);
                case SubNet.INO_SNAP -> snap(f, age, mesh);
                case SubNet.INO_WAVE -> wave(f, age, mesh);
                case SubNet.INO_POLLEN -> burst(f.pos, age, f.id * 31 + 7, 220, POLLEN, 2.6f, 1.8f, 0.05f, 1.4f, -0.25f, 1.6f, 0.8f, 1.2f, false);
                case SubNet.INO_MIND_BOOM -> mindBoom(f, age);
                case SubNet.INO_HIT -> {
                    if (f.a > 0) {
                        burst(f.pos, age, (int) (f.born * 7) + f.id, (int) f.a, MIND, 4, 0.6f, 0.06f, 3, 0, 0.3f, 0, 0, true);
                    }
                    shock(f, age);
                }
                case SubNet.INO_SEEDS -> seeds(f, age);
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born);
            float out = f.endAt < 0 ? 1 : 1 - smooth((float) (now - f.endAt) / fadeOut(f));
            if (out <= 0 && f.kind != SubNet.INO_STRINGS) {
                continue;
            }
            switch (f.kind) {
                case SubNet.INO_SPIRIT -> spirit(f, age, now, out, mesh);
                case SubNet.INO_THREAD -> thread(f, age, now, out, mesh);
                case SubNet.INO_FIELD -> field(f, age, now, out, mesh);
                case SubNet.INO_MARK -> mark(f, now);
                case SubNet.INO_THEATER -> theater(f, age, now, out, mesh);
                case SubNet.INO_STRINGS -> strings(f, age, now, partial, mesh);
                case SubNet.INO_SPIRAL -> spiral(f, age, partial, out, mesh);
                default -> {
                }
            }
        }
    }

    // ---------- pieces ----------

    private static float sec(float ticks) {
        return ticks / 20f;
    }

    private static float easeOut3(float t) {
        float k = 1 - Mth.clamp(t, 0, 1);
        return 1 - k * k * k;
    }

    // Loose specks (pollen, seeds, mind sparks), the page's makeSpecks().burst with each one its own direction, speed,
    // color, size and life, worked out from its age (no state).
    private static void burst(Vec3 at, float age, int seed, int n, float[][] cols, float speed, float life, float size, float drag, float grav, float spread,
            float wob, float upY, boolean glow) {
        if (glow != DbFx.pass) {
            return;
        }
        float t = sec(age);
        if (t < 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float L = life * (0.5f + hash(seed, i, 1));
            if (t > L) {
                continue;
            }
            Vector3f d = new Vector3f(hash(seed, i, 2) - 0.5f, hash(seed, i, 3) - 0.3f, hash(seed, i, 4) - 0.5f);
            if (d.lengthSquared() < 1.0E-6f) {
                d.set(0, 1, 0);
            }
            d.normalize().mul(speed * (0.3f + 0.9f * hash(seed, i, 5)));
            d.y += upY;
            float e = (float) Math.exp(-drag * t);
            float k = drag > 0 ? (1 - e) / drag : t;
            float fall = drag > 0 ? grav * (t / drag - (1 - e) / (drag * drag)) : 0.5f * grav * t * t;
            float w = hash(seed, i, 6) * 6.283f;
            Vector3f p = rel(at).add((hash(seed, i, 7) - 0.5f) * spread, (hash(seed, i, 8) - 0.5f) * spread, (hash(seed, i, 9) - 0.5f) * spread);
            p.add(d.x * k + wob * (Mth.cos(w) - Mth.cos(t * 4 + w)) / 4, d.y * k - fall, d.z * k + wob * (Mth.sin(t * 3.3f + w) - Mth.sin(w)) / 3.3f);
            float u = t / L;
            float a = smooth(u / 0.12f) * (1 - smooth((u - 0.6f) / 0.4f));
            float[] col = cols[(int) (hash(seed, i, 10) * cols.length) % cols.length];
            bb(p, size * (0.5f + hash(seed, i, 11)), 0, FxDraw.SOFT, 0.55f, col, a, glow);
        }
    }

    // The page's shock(): a ring opening round a hit.
    private static void shock(Fx f, float age) {
        float r = switch ((int) f.b) {
            case 1 -> 0.8f;
            case 2 -> 1f;
            case 3 -> 1.6f;
            case 4 -> 2f;
            default -> 0;
        };
        if (r <= 0 || age > 10) {
            return;
        }
        float k = age / 10;
        bb(rel(f.pos), r * 2 * (0.3f + 1.7f * easeOut3(k)), 0, FxDraw.RING, 0.12f, SHOCK, 0.9f * (1 - k), true);
    }

    // The diamond seal between her hands (b 0: Shintenshin; b 1: the Teatro Mental's, kneeling).
    private static void seal(Fx f, float age, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        float t = sec(age);
        boolean theater = f.b >= 1;
        float life, alpha, half;
        if (theater) {
            float k = smooth(t / 0.3f);
            life = Mth.clamp(t / 0.8f, 0, 1);
            alpha = k * (1 - smooth((t - 0.78f) / 0.05f));
            half = 0.3f + 0.2f * k;
        } else {
            life = Mth.clamp(t / 0.5f, 0, 1);
            alpha = smooth(t / 0.18f) * (1 - smooth((t - 0.4f) / 0.12f));
            half = f.a;
        }
        if (alpha <= 0.002f) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.INO_SEAL);
        p.life = life;
        p.alpha = alpha;
        Quaternionf q = new Quaternionf().rotationTo(UP, new Vector3f((float) f.dir.x, (float) f.dir.y, (float) f.dir.z).normalize());
        KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.disc(rel(f.pos), half, q), p, KiMesh.BOTH, 17);
    }

    // Leitura Mental (only the Ino gets it): a seal pulsing over the enemy's head and a thread from her forehead to his.
    private static void read(Fx f, float age, float partial, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity e = FxAnchor.entity(f.id);
        Vec3 head = e != null ? e.getEyePosition(partial).add(0, 0.3, 0) : f.pos.add(f.dir);
        float s = Mth.clamp(age / 10f, 0, 1);
        if (s > 0 && s < 1) {
            KiMesh.P p = KiMesh.p(KiMesh.INO_SEAL);
            p.life = s;
            p.alpha = Mth.sin(Mth.PI * s) * 1.4f;
            Vector3f c = rel(head.add(0, 0.45, 0));
            Vector3f toCam = new Vector3f(c).negate();
            Quaternionf q = new Quaternionf().rotationTo(UP, toCam.lengthSquared() > 1.0E-6f ? toCam.normalize() : new Vector3f(0, 0, 1));
            KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.disc(c, 0.5f * (0.6f + 1.4f * s), q), p, KiMesh.BOTH, 17);
        }
        if (age < 12.6f && mc.player != null) {
            Vec3 me = mc.player.getEyePosition(partial).add(mc.player.getViewVector(partial).scale(0.5)).add(0, -0.18, 0);
            threadPiece(me, head, smooth(age / 3f), 1 - smooth((age - 7.6f) / 5f), 0, -1, 0.016f, 0.2f, f.id, sec(age));
        }
    }

    // One thread (THREAD_F): from a to b, grown to `grow`, sagging by slack, with a pulse at `pulse` (-1 none).
    private static void threadPiece(Vec3 a, Vec3 b, float grow, float alpha, float snap, float pulse, float width, float slack, int seed, float time) {
        if (alpha <= 0.002f || grow <= 0) {
            return;
        }
        Vec3 d = b.subtract(a);
        float L = (float) d.length();
        if (L < 0.05f) {
            return;
        }
        float sag = 0.25f + 0.35f * hash(seed, 3, 41);
        KiMesh.P p = KiMesh.p(KiMesh.INO_THREAD);
        p.len = L;
        p.w = width;
        p.bend = slack * sag * Math.min(L, 9) * 0.12f;
        p.growth = grow;
        p.alpha = alpha;
        p.thresh = snap;
        p.pulse = pulse;
        p.seed = hash(seed, 1, 43) * 10;
        p.time = time;
        KiMesh.queue(KiMesh.Mesh.THIN, KiMesh.along(rel(a), new Vector3f((float) d.x, (float) d.y, (float) d.z)), p, KiMesh.BOTH, 15);
    }

    // Elo Mental thread (a growth, b alpha, width kept from c).
    private static void thread(Fx f, float age, double now, float out, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        Vec3 a = f.at(now);
        Vec3 b = a.add(f.dirAt(now));
        float pulse = -1;
        if (f.pulseAt >= 0) {
            float k = (float) Mth.clamp((now - f.pulseAt) / 9, 0, 1);
            pulse = f.pulseBack ? 1 - k * 1.1f : k * 1.1f;
            if (now - f.pulseAt > 12) {
                f.pulseAt = -1;
            }
        }
        threadPiece(a, b, f.a, f.b * out, 0, pulse, f.width, 1, f.id, sec(age));
    }

    private static void loosePulse(Fx f, float age, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        float k = Mth.clamp(age / 9, 0, 1);
        threadPiece(f.pos, f.pos.add(f.dir), 1, 0.6f * (1 - k), 0, k * 1.1f, 0.026f, 1, f.id + 5, sec(age));
    }

    private static void snap(Fx f, float age, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        threadPiece(f.pos, f.pos.add(f.dir), 1, 0.9f, Math.min(1, sec(age) * 2.4f), -1, 0.026f, 1, f.id, sec(age));
    }

    // Shinranshin: the cone from her forehead (12 long, 9 wide at the mouth), the front running out in 0.35 s.
    private static void wave(Fx f, float age, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        float t = sec(age);
        float alpha = 1 - smooth((t - 0.35f) / 0.3f);
        if (alpha <= 0.002f) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.INO_WAVE);
        p.growth = easeOut3(t / 0.35f);
        p.alpha = alpha;
        p.time = t;
        p.r0 = 0;
        p.r1 = 9;
        p.len = 12;
        KiMesh.queue(KiMesh.Mesh.WIND, KiMesh.along(rel(f.pos), new Vector3f((float) f.dir.x, (float) f.dir.y, (float) f.dir.z)), p, KiMesh.BOTH, 14);
    }

    // The scrambled mind's spiral over the head of entity id.
    private static void spiral(Fx f, float age, float partial, float out, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        Entity e = FxAnchor.entity(f.id);
        if (e == null || FxAnchor.firstPersonSelf(f.id)) {
            return;
        }
        float a = smooth(age / 5) * out;
        if (a <= 0.002f) {
            return;
        }
        float t = sec(age);
        float seed = hash(f.id, 2, 51) * 10;
        Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() + 0.3 + Mth.sin(t * 4 + seed) * 0.04, 0);
        KiMesh.P p = KiMesh.p(KiMesh.INO_SPIRAL);
        p.alpha = a;
        p.time = t;
        p.seed = seed;
        KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(rel(c), 0.45f * e.getBbHeight() / 1.8f), p, KiMesh.BOTH, 17);
    }

    // ---------- Z: the spirit, her own body in mind energy, and its trail ----------

    private static final float[][] GHOST_BOXES = {
            // w, h, d, x, y, z, rx, rz (on her body)
            {0.5f, 0.5f, 0.5f, 0, 1.75f, 0, 0, 0}, {0.5f, 0.75f, 0.25f, 0, 1.125f, 0, 0, 0},
            {0.56f, 0.3f, 0.32f, 0, 0.66f, 0, 0, 0},
            {0.54f, 0.12f, 0.54f, 0, 2.04f, 0, 0, 0}, {0.54f, 0.42f, 0.1f, 0, 1.84f, 0.24f, 0, 0},
            {0.24f, 0.36f, 0.05f, 0.13f, 1.82f, -0.27f, 0, -0.12f}, {0.06f, 0.38f, 0.1f, -0.27f, 1.8f, -0.17f, 0, 0.05f}};
    private static final float[][] TAIL = {{0.2f, 0.26f, 0.2f, 0}, {0.19f, 0.22f, 0.18f, 0.2f}, {0.17f, 0.22f, 0.16f, 0.2f}, {0.15f, 0.2f, 0.14f, 0.2f},
            {0.12f, 0.2f, 0.12f, 0.18f}, {0.08f, 0.16f, 0.08f, 0.18f}};

    private static void spirit(Fx f, float age, double now, float out, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        float a = f.a * out, dis = f.b;
        if (a <= 0.01f) {
            return;
        }
        float t = sec(age);
        Vec3 pos = f.at(now);
        Vec3 d = f.dirAt(now);
        float yaw = d.horizontalDistanceSqr() < 1.0E-6 ? 0 : (float) Math.atan2(-d.x, -d.z);
        Vector3f base = rel(pos);
        // The page: g.rotation.set(-0.35, yaw, 0), leaning into the flight.
        Matrix4f g = new Matrix4f().translation(base).rotateY(yaw).rotateX(-0.35f);
        KiMesh.P p = KiMesh.p(KiMesh.INO_GHOST);
        p.alpha = a;
        p.thresh = dis;
        p.time = t;
        Vec3 cam = DbFx.cam;
        // Dust = the camera's place (mod 256) so the shader's noise sits in the world, like the page's vW.
        float mx = (float) (cam.x - Math.floor(cam.x / 256) * 256), my = (float) (cam.y - Math.floor(cam.y / 256) * 256),
                mz = (float) (cam.z - Math.floor(cam.z / 256) * 256);
        p.dust = new float[] {mx, my, mz};
        for (float[] b : GHOST_BOXES) {
            box(new Matrix4f(g), b, p);
        }
        // Arms and legs (spiritStep: arms back and out, legs trailing).
        limb(g, 0.375f, 1.45f, -0.5f, 0.25f, 0.32f, p);
        limb(g, -0.375f, 1.45f, -0.5f, -0.25f, 0.32f, p);
        limb(g, 0.125f, 0.75f, 0.3f, 0, 0.375f, p);
        limb(g, -0.125f, 0.75f, 0.5f, 0, 0.375f, p);
        // The ponytail, 6 links swaying (inoHair with k 1.6).
        Matrix4f piv = new Matrix4f(g).translate(0, 1.98f, 0.34f);
        for (int i = 0; i < TAIL.length; i++) {
            float[] l = TAIL[i];
            if (i > 0) {
                piv.translate(0, -l[3], 0);
            }
            float k = 1.6f;
            float rx = 0.18f + 0.12f * i * k + Mth.sin(t * 3.1f - i * 0.7f) * 0.05f * (1 + i * 0.3f) * (0.4f + k);
            float rz = Mth.sin(t * 2.3f - i * 0.6f + 1.3f) * 0.04f * (1 + i * 0.4f);
            piv.rotateX(rx).rotateZ(rz);
            KiMesh.queue(KiMesh.Mesh.BOX, new Matrix4f(piv).translate(0, -l[1] / 2, 0).scale(l[0], l[1], l[2]), p, KiMesh.FRONT, 9);
        }
        // The trail: one tube thinning back over its last places (the page keeps 22 frames: 7 ticks).
        List<Vec3> pts = new ArrayList<>(f.trail);
        if (f.endAt < 0) {
            pts.add(pos);
        }
        float ta = a * (1 - dis);
        if (pts.size() < 4 || ta < 0.01f) {
            return;
        }
        int n = pts.size();
        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = pts.get(i).add(0, 1.1, 0), p1 = pts.get(i + 1).add(0, 1.1, 0);
            Vec3 dd = p1.subtract(p0);
            if (dd.lengthSqr() < 1.0E-6) {
                continue;
            }
            float u0 = i / (float) (n - 1), u1 = (i + 1) / (float) (n - 1);
            KiMesh.P tp = KiMesh.p(KiMesh.INO_TAIL);
            tp.len = (float) dd.length();
            tp.r0 = 0.36f * (float) Math.pow(u0, 0.7);
            tp.r1 = 0.36f * (float) Math.pow(u1, 0.7);
            tp.pulse = u0;
            tp.flow = u1;
            tp.alpha = ta;
            tp.time = t;
            KiMesh.queue(KiMesh.Mesh.THIN, KiMesh.along(rel(p0), new Vector3f((float) dd.x, (float) dd.y, (float) dd.z)), tp, KiMesh.BOTH, 8);
        }
    }

    private static void box(Matrix4f g, float[] b, KiMesh.P p) {
        g.translate(b[3], b[4], b[5]).rotateX(b[6]).rotateZ(b[7]).scale(b[0], b[1], b[2]);
        KiMesh.queue(KiMesh.Mesh.BOX, g, p, KiMesh.FRONT, 9);
    }

    private static void limb(Matrix4f g, float x, float y, float rx, float rz, float down, KiMesh.P p) {
        Matrix4f m = new Matrix4f(g).translate(x, y, 0).rotateX(rx).rotateZ(rz).translate(0, -down, 0).scale(0.25f, 0.75f, 0.25f);
        KiMesh.queue(KiMesh.Mesh.BOX, m, p, KiMesh.FRONT, 9);
    }

    // ---------- B: the flower bed, the seeds, the pollen mark ----------

    private static final int FLOWERS = 34;
    private static final float[][] FLOWER_COLS = {FxDraw.rgb(0xFF8FC4), FxDraw.rgb(0xFFF4F8), FxDraw.rgb(0xFFD84F), FxDraw.rgb(0xC9A0FF), FxDraw.rgb(0xFF5F7A),
            FxDraw.rgb(0xFFB3E0), FxDraw.rgb(0xF7F1A8)};
    private static final Map<Integer, Flower> FLOWER_CACHE = new HashMap<>();

    // One flower from its seed (the page's makeFlower, the same draws in the same order).
    private record Petal(float a, float L, float sz, float cup, float[] col) {
    }

    private record Flower(float h, float lean, float[] stem, float leafY, float topX, float topY, Petal[] petals, float ctr, float[] ctrCol, float ry, float rz,
            float delay, float sway) {
    }

    private static Flower flower(int index) {
        return FLOWER_CACHE.computeIfAbsent(index, i -> {
            long[] seed = {1234L + i * 7919L};
            java.util.function.DoubleSupplier r = () -> {
                seed[0] = seed[0] * 16807L % 2147483647L;
                return seed[0] / 2147483647.0;
            };
            float h = (float) (0.28 + 0.4 * r.getAsDouble());
            float lean = (float) ((r.getAsDouble() - 0.5) * 0.5);
            float[] stem = hsl(FxDraw.rgb(0x3F8A3A), (float) ((r.getAsDouble() - 0.5) * 0.06), (float) ((r.getAsDouble() - 0.5) * 0.1));
            float leafY = (float) (h * (0.25 + 0.3 * r.getAsDouble()));
            float topX = (float) (-0.3 - 0.6 * r.getAsDouble());
            float topY = (float) (r.getAsDouble() * 6.28);
            float[] base = FLOWER_COLS[(int) Math.floor(r.getAsDouble() * FLOWER_COLS.length)];
            int n = 4 + (int) Math.floor(r.getAsDouble() * 4);
            float pl = (float) (0.06 + 0.06 * r.getAsDouble());
            float cup = (float) (0.2 + 0.6 * r.getAsDouble());
            Petal[] petals = new Petal[n];
            for (int k = 0; k < n; k++) {
                float[] c = hsl(base, (float) ((r.getAsDouble() - 0.5) * 0.04), (float) ((r.getAsDouble() - 0.5) * 0.12));
                float a = (float) (k / (double) n * 6.283 + (r.getAsDouble() - 0.5) * 0.4);
                float L = (float) (pl * (0.8 + 0.4 * r.getAsDouble()));
                float sz = (float) (L * (0.45 + 0.2 * r.getAsDouble()));
                float cu = (float) (-cup * (0.8 + 0.4 * r.getAsDouble()));
                petals[k] = new Petal(a, L, sz, cu, c);
            }
            float ctr = (float) (0.028 + 0.02 * r.getAsDouble());
            float[] ctrCol = r.getAsDouble() > 0.5 ? FxDraw.rgb(0xF2B630) : FxDraw.rgb(0x8A5A2A);
            float ry = (float) (r.getAsDouble() * 6.28);
            float rz = (float) ((r.getAsDouble() - 0.5) * 0.4);
            float delay = (float) (r.getAsDouble() * 0.45);
            float sway = (float) (r.getAsDouble() * 6.28);
            return new Flower(h, lean, stem, leafY, topX, topY, petals, ctr, ctrCol, ry, rz, delay, sway);
        });
    }

    // THREE.Color.offsetHSL(h, 0, l).
    private static float[] hsl(float[] rgb, float dh, float dl) {
        float r = rgb[0], g = rgb[1], b = rgb[2];
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float h = 0, s, l = (max + min) / 2;
        if (max == min) {
            s = 0;
        } else {
            float d = max - min;
            s = l <= 0.5f ? d / (max + min) : d / (2 - max - min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
        }
        h = ((h + dh) % 1 + 1) % 1;
        l = Mth.clamp(l + dl, 0, 1);
        if (s == 0) {
            return new float[] {l, l, l};
        }
        float q = l <= 0.5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return new float[] {hue(p, q, h + 1 / 3f), hue(p, q, h), hue(p, q, h - 1 / 3f)};
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1 / 6f) {
            return p + (q - p) * 6 * t;
        }
        if (t < 0.5f) {
            return q;
        }
        if (t < 2 / 3f) {
            return p + (q - p) * 6 * (2 / 3f - t);
        }
        return p;
    }

    // The top of the ground under (x, z) near height y (the flowers stand on the real ground).
    private static float groundAt(Level level, double x, double y, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z), top = Mth.floor(y) + 2;
        for (int by = top; by >= top - 6; by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return (float) (by + shape.max(net.minecraft.core.Direction.Axis.Y));
            }
        }
        return (float) y;
    }

    private static void field(Fx f, float age, double now, float out, boolean mesh) {
        if (!DbFx.pass) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || f.pos.distanceTo(DbFx.cam) > 72) {
            return;
        }
        float t = sec(age);
        float R = f.a * 0.92f;
        if (f.ground == null) {
            f.ground = new float[FLOWERS * 3];
            for (int i = 0; i < FLOWERS; i++) {
                float ang = hash(f.id, i, 61) * 6.283f, r = R * Mth.sqrt(hash(f.id, i, 62));
                double x = f.pos.x + Mth.cos(ang) * r, z = f.pos.z + Mth.sin(ang) * r;
                f.ground[i * 3] = (float) x;
                f.ground[i * 3 + 1] = groundAt(mc.level, x, f.pos.y, z);
                f.ground[i * 3 + 2] = (float) z;
            }
        }
        float wilt = f.b;
        for (int i = 0; i < FLOWERS; i++) {
            Flower fl = flower(i);
            Vec3 at = new Vec3(f.ground[i * 3], f.ground[i * 3 + 1], f.ground[i * 3 + 2]);
            float k = Mth.clamp((t - fl.delay) / 0.35f, 0, 1);
            float grow = k < 1 ? 1 + 2.4f * (k - 1) * (k - 1) * (k - 1) + 1.4f * (k - 1) * (k - 1) : 1;
            float s = Math.max(0, grow) * (1 - 0.35f * wilt) * out;
            // The little green specks as it pops out of the ground.
            burst(at.add(0, 0.05, 0), (t - fl.delay) * 20, f.id * 97 + i, 2, SPROUT, 1.2f, 0.4f, 0.04f, 0, 4, 0, 0, 1, false);
            if (s <= 0.002f || !mesh) {
                continue;
            }
            float rx = fl.lean * (1 - wilt) + wilt * 0.9f;
            Matrix4f g = new Matrix4f().translation(rel(at)).rotateX(rx).rotateY(fl.ry).rotateZ(fl.rz).scale(s);
            solid(KiMesh.Mesh.THIN, new Matrix4f(g).scale(0.015f, fl.h, 0.015f), fl.stem);
            solid(KiMesh.Mesh.FLASH, new Matrix4f(g).translate(0.05f, fl.leafY, 0).rotateZ(0.5f).scale(0.084f, 0.015f, 0.036f), fl.stem);
            Matrix4f top = new Matrix4f(g).translate(0, fl.h, 0).rotateX(fl.topX).rotateY(fl.topY).rotateZ(Mth.sin(t * 1.6f + fl.sway) * 0.05f);
            for (Petal pt : fl.petals) {
                Matrix4f m = new Matrix4f(top).translate(Mth.cos(pt.a) * pt.L * 0.9f, 0, Mth.sin(pt.a) * pt.L * 0.9f).rotateY(-pt.a).rotateZ(pt.cup)
                        .scale(pt.L, 0.012f, pt.sz);
                solid(KiMesh.Mesh.FLASH, m, pt.col);
            }
            solid(KiMesh.Mesh.FLASH, new Matrix4f(top).scale(fl.ctr, fl.ctr * 0.6f, fl.ctr), fl.ctrCol);
        }
    }

    private static void solid(KiMesh.Mesh mesh, Matrix4f m, float[] col) {
        KiMesh.P p = KiMesh.p(KiMesh.INO_SOLID);
        p.color = col;
        p.glow = false;
        p.solid = true;
        KiMesh.queue(mesh, m, p, KiMesh.BOTH, 1);
    }

    // The seeds thrown in an arc onto the aim (26, each its own landing spot and time in the air).
    private static void seeds(Fx f, float age) {
        if (DbFx.pass) {
            return;
        }
        float t = sec(age);
        Vec3 aim = f.pos.add(f.dir);
        for (int i = 0; i < 26; i++) {
            float T = 0.55f + 0.15f * hash(f.id, i, 71);
            if (t > T) {
                continue;
            }
            Vec3 to = aim.add((hash(f.id, i, 72) - 0.5) * 3.6, 0, (hash(f.id, i, 73) - 0.5) * 3.6);
            Vec3 v = to.subtract(f.pos).scale(1 / T).add(0, 10 * T / 2, 0);
            Vec3 p = f.pos.add(v.scale(t)).add(0, -5 * t * t, 0);
            float u = t / T;
            bb(rel(p), 0.04f + 0.03f * hash(f.id, i, 74), 0, FxDraw.SOFT, 0.55f, SEED[i % 3], smooth(u / 0.12f), false);
        }
    }

    // The pollen on the marked one: specks coming off round his head all the time (30 a second).
    private static void mark(Fx f, double now) {
        if (DbFx.pass) {
            return;
        }
        Vec3 head = f.at(now);
        double last = f.endAt >= 0 ? f.endAt : now;
        int newest = (int) Math.floor(last * 1.5), oldest = (int) Math.floor((now - 23) * 1.5);
        for (int k = Math.max(oldest, (int) Math.ceil(f.born * 1.5)); k <= newest; k++) {
            double born = k / 1.5;
            float t = (float) (now - born) / 20;
            float L = 0.7f + 0.4f * hash(f.id, k, 81);
            if (t < 0 || t > L) {
                continue;
            }
            float w = hash(f.id, k, 82) * 6.283f;
            float e = (float) Math.exp(-2 * t);
            Vec3 p = head.add((hash(f.id, k, 83) - 0.5) * 0.9 + 0.6 * (Mth.cos(w) - Mth.cos(t * 4 + w)) / 4,
                    (hash(f.id, k, 84) - 0.3) * 0.8 + 0.25 * (1 - e) / 2,
                    (hash(f.id, k, 85) - 0.5) * 0.9 + 0.6 * (Mth.sin(t * 3.3f + w) - Mth.sin(w)) / 3.3);
            float u = t / L;
            float a = smooth(u / 0.12f) * (1 - smooth((u - 0.6f) / 0.4f));
            bb(rel(p), 0.035f + 0.035f * hash(f.id, k, 86), 0, FxDraw.SOFT, 0.55f, MARK[(int) (hash(f.id, k, 87) * 3) % 3], a, false);
        }
    }

    // ---------- V: Teatro Mental ----------

    // dir.x: the dome's alpha, dir.y: her aura, a: the dome's radius, b: the sky.
    private static void theater(Fx f, float age, double now, float out, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        float t = sec(age);
        Vec3 center = f.pos;
        Vector3f c = rel(center);
        float sky = f.b * out;
        if (sky > 0.002f) {
            // Seen from far outside its 80 blocks it would be a purple ball: it fades out past 60.
            float far = 1 - smooth((c.length() - 60) / 20);
            KiMesh.P p = KiMesh.p(KiMesh.INO_SKY);
            p.alpha = sky * far;
            p.time = t;
            p.glow = false;
            if (p.alpha > 0.002f) {
                KiMesh.queue(KiMesh.Mesh.DOME, KiMesh.sphere(c, 80), p, KiMesh.BACK, -5);
            }
        }
        float dome = (float) f.dir.x * out;
        if (dome > 0.002f && f.a > 0.05f) {
            KiMesh.P p = KiMesh.p(KiMesh.INO_DOME);
            p.alpha = dome;
            p.time = t;
            Matrix4f m = KiMesh.sphere(c, f.a);
            KiMesh.queue(KiMesh.Mesh.DOME, m, p, KiMesh.BACK, 4);
            KiMesh.queue(KiMesh.Mesh.DOME, m, p, KiMesh.FRONT, 5);
        }
        float aura = (float) f.dir.y * out;
        Minecraft mc = Minecraft.getInstance();
        Entity camE = mc.getCameraEntity();
        boolean self = camE != null && camE.position().distanceTo(center) < 1.2 && mc.options.getCameraType().isFirstPerson();
        if (aura > 0.01f && !self) {
            // purpleAura(): the ki shell, purple, low tongues.
            KiMesh.P p = KiMesh.p(KiMesh.SHELL);
            p.phase = t * 1.4f;
            p.time = t;
            p.growth = aura;
            p.alpha = aura;
            p.color = AURA;
            p.tongue = 0.22f;
            p.spike = 0.05f;
            p.thresh = 0.68f;
            p.size = new float[] {0.58f, 1.08f, 0.46f};
            KiMesh.queue(KiMesh.Mesh.AURA, new Matrix4f().translation(c.x, c.y + 0.65f, c.z), p, KiMesh.BOTH, 10);
        }
    }

    // The puppet strings: three per one caught (head, right hand, left hand) down from 22 blocks up.
    private static void strings(Fx f, float age, double now, float partial, boolean mesh) {
        if (!DbFx.pass || !mesh) {
            return;
        }
        Entity e = FxAnchor.entity(f.id);
        Vec3 feet = e != null ? e.getPosition(partial) : f.at(now);
        float t = sec(age);
        float grow = f.a;
        float snap;
        float alpha;
        if (f.endAt >= 0 && f.b >= 1) {
            // Snapped by the burst: they tear apart (uSnap += dt * 2.5).
            float s = (float) (now - f.endAt) / 20 * 2.5f;
            snap = Math.min(1, 0.01f + s);
            alpha = 1;
            if (snap >= 1) {
                return;
            }
        } else {
            snap = f.b;
            alpha = f.endAt < 0 ? 1 : 1 - smooth((float) (now - f.endAt) / 8);
        }
        if (alpha <= 0.002f) {
            return;
        }
        int i = Math.floorMod(f.id, 5);
        float hgt = e != null ? e.getBbHeight() : 1.8f;
        float sc = hgt / 2f;
        float yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot * Mth.DEG_TO_RAD : 0;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 top = feet.add(Mth.sin(t * 0.7f + i) * 1.5, 22, Mth.cos(t * 0.5f + i * 2) * 1.5);
        Vec3[] limbs = {feet.add(0, hgt + 0.02, 0), feet.add(right.scale(0.375 * sc)).add(0, 0.75 * sc, 0), feet.add(right.scale(-0.375 * sc)).add(0, 0.75 * sc, 0)};
        for (int w = 0; w < 3; w++) {
            Vec3 a = top.add((w - 1) * 0.8, 0, 0);
            threadPiece(a, limbs[w], grow, alpha, snap, -1, 0.012f, 0.15f, f.id * 3 + w, t);
        }
    }

    // A mind bursting inside a head: the purple blast dome tearing into tongues, and the sparks.
    private static void mindBoom(Fx f, float age) {
        float t = sec(age);
        Vector3f c = rel(f.pos);
        DbFx.blast(c, 0.4f + 2.1f * easeOut3(t / 0.5f), Mth.clamp(t / 1.1f, 0, 1), BOOM, age, 3.7f + f.id % 11, 1, false, 14);
        burst(f.pos, age, f.id * 13 + 5, 80, MIND, 4, 0.6f, 0.06f, 3, 0, 0.3f, 0, 0, true);
    }
}
