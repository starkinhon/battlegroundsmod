package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// The Dragon Ball SubAlmas' effects (power/powers/sub/db), ported line by line from the Visualizador's Dragon Ball
// tabs (v60): kiBeam, kiBall, kiAura (the mod's own fire of glow), kiBoom, the saw disc (shape 30), the Genki Dama's
// glass (31), the Big Bang made of Zanka tongues (32), the Death Ball's red sun, the Makankosappo's two spirals, the
// Mafuba's gale, spiral, tornado and jar, and the hit flashes, dust and bolts the Visualizador shares (nrHit, nrDust,
// nrBolt). The Visualizador's T is the effect's own age in ticks here; its ground (y = 0) is the ground the server
// sends. Every draw call says which layer it goes in (glow = additive, else the alpha layer) and only draws in that
// pass, so the code reads the same as the page.
public final class DbFx {
    static final int SAW = 30;
    static final int GENKI = 31;
    static final int TONGUE = 32;
    // Jujutsu: cursed energy as flat flame with a thick ink outline (tongue on a ribbon when param < 0.5, else a blob),
    // and the Divergent Fist's opaque brush aura.
    static final int CEINK = 33;
    static final int BRUSH = 34;

    private static final float[][] COLORS = {
            FxDraw.rgb(0x5AB8FF), FxDraw.rgb(0xE8F8FF), FxDraw.rgb(0xFFD84A), FxDraw.rgb(0xFFF6C8), FxDraw.rgb(0xFF3A2A),
            FxDraw.rgb(0xFFD0C8), FxDraw.rgb(0xC050FF), FxDraw.rgb(0xFF60D0), FxDraw.rgb(0xFFE0F6), FxDraw.rgb(0x70FF80),
            {1f, 1f, 1f}, FxDraw.rgb(0xFF8A2A), FxDraw.rgb(0x60B0FF), FxDraw.rgb(0x2F7DFF)};
    private static final float[] WHITE = {1f, 1f, 1f};

    static float[] colorOf(int i) {
        return COLORS[Math.floorMod(i, COLORS.length)];
    }
    private static final float[] DIRT = FxDraw.rgb(0x5A4632);
    private static final float[] DUST = FxDraw.rgb(0xCDB88E);
    private static final float[] AIR = FxDraw.rgb(0xE8F4FF);
    private static final float[] SMOKE_GREY = {0.3f, 0.27f, 0.25f};
    private static final float[] PEBBLE = {0.3f, 0.28f, 0.26f};

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
        double sealAt = -1;
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
    // Which pass is drawing: true the glow (additive), false the alpha layer.
    static boolean pass;
    static Vec3 cam = Vec3.ZERO;

    private DbFx() {
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    private static boolean stream(int kind) {
        return switch (kind) {
            case SubNet.KI_BEAM, SubNet.KI_BALL, SubNet.SAW, SubNet.GENKI, SubNet.DEATH_BALL, SubNet.BIG_BANG, SubNet.SPARKS, SubNet.TELEKINESIS,
                    SubNet.GALE, SubNet.SPIRAL, SubNet.TORNADO, SubNet.ARM, SubNet.SMOKE_COVER -> true;
            default -> false;
        };
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        if (kind >= SubNet.DIV_AURA) {
            JjFx.receive(kind, id, pos, dir, a, b, c);
            return;
        }
        if (kind >= SubNet.RASENGAN) {
            NrFx.receive(kind, id, pos, dir, a, b, c);
            return;
        }
        double now = ScreenFx.now();
        if (stream(kind)) {
            Fx f = TRACKED.get(key(kind, id));
            if (f == null || f.endAt >= 0) {
                f = new Fx(kind, id, pos, dir, a, b, c, now);
                TRACKED.put(key(kind, id), f);
            }
            f.pos = pos;
            f.dir = dir;
            f.a = a;
            f.b = b;
            f.updated = now;
            f.trail.addLast(pos);
            while (f.trail.size() > 12) {
                f.trail.removeFirst();
            }
            if (c >= 1) {
                f.endAt = now;
            }
            return;
        }
        switch (kind) {
            case SubNet.KI_AURA -> {
                Fx f = TRACKED.get(key(kind, id));
                if (a <= 0) {
                    if (f != null && f.endAt < 0) {
                        f.endAt = now;
                    }
                    return;
                }
                TRACKED.put(key(kind, id), new Fx(kind, id, pos, dir, a, b, c, now));
            }
            case SubNet.DIZZY -> {
                Minecraft mc = Minecraft.getInstance();
                boolean self = mc.player != null && mc.player.getId() == id;
                if (a <= 0) {
                    // Woken up: the stars go and the white leaves the screen.
                    Fx f = TRACKED.get(key(kind, id));
                    if (f != null && f.endAt < 0) {
                        f.endAt = now;
                    }
                    if (self) {
                        ScreenFx.clearVignette();
                    }
                    return;
                }
                TRACKED.put(key(kind, id), new Fx(kind, id, pos, dir, a, b, c, now));
                if (self) {
                    // Blinded: the screen burns white and only slowly comes back.
                    ScreenFx.flash(0xFFFFFF, 1f, 30);
                    ScreenFx.vignette(0xFFFFFF, 0.75f, (int) a);
                }
            }
            case SubNet.JAR -> {
                Fx f = TRACKED.get(key(kind, id));
                if (f == null) {
                    f = new Fx(kind, id, pos, dir, a, b, c, now);
                    TRACKED.put(key(kind, id), f);
                }
                if (c >= 1) {
                    f.sealAt = now;
                    f.a = a;
                    f.b = b;
                }
                f.updated = now;
            }
            case SubNet.SCREEN -> screen((int) a, pos);
            case SubNet.KI_BOOM -> {
                SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
                if (a >= 2.5f && KiMesh.ready()) {
                    // A big ki blast landing: the manga impact frame (negative and positive) on the hit, never on the throw.
                    screen(46, pos);
                }
            }
            default -> SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
        }
    }

    static boolean active() {
        return !SHOTS.isEmpty() || !TRACKED.isEmpty() || NrFx.active() || JjFx.active();
    }

    static void clear() {
        SHOTS.clear();
        TRACKED.clear();
        NrFx.clear();
        JjFx.clear();
    }

    private static double life(Fx f) {
        return switch (f.kind) {
            case SubNet.KI_BOOM -> 110;
            case SubNet.GENKI_BOOM -> 130;
            case SubNet.SUPERNOVA -> 90;
            case SubNet.BIG_BANG_BOOM -> 72;
            case SubNet.HIT -> 13;
            case SubNet.DEATH_BEAM -> 11;
            case SubNet.MAKANKO -> f.a + 11;
            case SubNet.SOLAR -> 31;
            case SubNet.STREAK -> Math.max(1, f.c);
            case SubNet.RAIN -> 14 + f.b + 22;
            case SubNet.SHOT -> f.a * 2.2 + 1;
            case SubNet.VANISH -> 9;
            case SubNet.FLEX -> 42;
            case SubNet.SCAR -> f.b;
            default -> 40;
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
            if (f.endAt < 0 && (f.kind == SubNet.KI_AURA || f.kind == SubNet.DIZZY) && now - f.born > f.a) {
                f.endAt = now;
            }
            if (f.kind == SubNet.JAR) {
                // Open and waiting for 10 s at most; sealed: its time, then breaking or fading.
                if ((f.sealAt < 0 && now - f.updated > 200) || (f.sealAt >= 0 && now - f.sealAt > f.a + 40)) {
                    it.remove();
                }
                continue;
            }
            if (f.endAt >= 0 && now - f.endAt > fadeOut(f)) {
                it.remove();
            }
        }
    }

    private static float fadeOut(Fx f) {
        return switch (f.kind) {
            case SubNet.KI_AURA -> 14;
            // The 3D beam's end leaves the hand and runs to the tip (1.3 blocks a tick) before it is gone.
            case SubNet.KI_BEAM -> KiMesh.ready() ? (float) Math.max(12, f.dir.length() / 1.3 + 3) : 12;
            case SubNet.SMOKE_COVER -> 40;
            case SubNet.SAW -> 6;
            default -> 8;
        };
    }

    static void draw(Vec3 camera, double now, float partial, boolean glow) {
        if (glow) {
            prune(now);
        }
        pass = glow;
        cam = camera;
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born);
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case SubNet.KI_BOOM -> kiBoom(rel(f.pos), age, f.a, color(f.b), (int) f.c, groundY(f));
                case SubNet.GENKI_BOOM -> genkiBoom(f, age);
                case SubNet.SUPERNOVA -> supernova(f, age);
                case SubNet.BIG_BANG_BOOM -> bbBoom(rel(f.pos), age, f.a, groundY(f));
                case SubNet.HIT -> nrHit(rel(f.pos), age, color(f.b), f.a, (int) f.c, 12);
                case SubNet.DEATH_BEAM -> deathBeam(f, age);
                case SubNet.MAKANKO -> makanko(f, age);
                case SubNet.SOLAR -> solar(f, age);
                case SubNet.STREAK -> streak(f, age);
                case SubNet.RAIN -> rain(f, age);
                case SubNet.SHOT -> shot(f, age);
                case SubNet.VANISH -> vanish(f, age);
                case SubNet.FLEX -> flex(f, age);
                case SubNet.SCAR -> scar(f, age);
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born);
            float out = f.endAt < 0 ? 1 : 1 - smooth((float) (now - f.endAt) / fadeOut(f));
            if (out <= 0 && f.kind != SubNet.JAR) {
                continue;
            }
            switch (f.kind) {
                case SubNet.KI_BEAM -> kiBeam(rel(f.pos), rel(f.pos.add(f.dir)), f.a + (f.b == Db.KAME ? 0.08f * Mth.sin(age * 1.3f) : 0),
                        color(f.b), age, out, f.id % 7 + 1, f.endAt < 0 ? -1 : (float) (now - f.endAt) * 1.3f);
                case SubNet.KI_BALL -> ball(f, age, out);
                case SubNet.KI_AURA -> aura(f, age, partial, out);
                case SubNet.SAW -> saw(f, age, out);
                case SubNet.GENKI -> genki(f, age, out);
                case SubNet.DEATH_BALL -> deathBall(f, age, out);
                case SubNet.BIG_BANG -> bigBangBall(rel(f.pos), f.a, age, f.b * out);
                case SubNet.SPARKS -> sparks(f, age, out);
                case SubNet.TELEKINESIS -> telekinesis(f, age, partial, out);
                case SubNet.DIZZY -> dizzy(f, age, partial, out);
                case SubNet.GALE -> gale(f, age, out);
                case SubNet.SPIRAL -> spiral(f, age, out);
                case SubNet.TORNADO -> tornado(f, age, out);
                case SubNet.JAR -> jar(f, now);
                case SubNet.ARM -> arm(f, partial, out);
                case SubNet.SMOKE_COVER -> smokeCover(f, age, partial, out);
                default -> {
                }
            }
        }
        NrFx.draw(now, partial);
        JjFx.draw(now, partial);
    }

    // ---------- drawing in the right pass ----------

    static void bb(Vector3f c, float size, float spin, int shape, float param, float[] col, float op, boolean glow) {
        if (glow == pass && op > 0.002f) {
            FxDraw.billboard(c, size, spin, shape, param, col, op, glow);
        }
    }

    static void rib(Vector3f a, Vector3f b, float w, int shape, float param, float[] col, float op, boolean glow) {
        if (glow == pass && op > 0.002f) {
            FxDraw.ribbon(a, b, w, shape, param, col, op, glow);
        }
    }

    static void dsk(Vector3f c, Vector3f n, float size, int shape, float param, float[] col, float op, boolean glow) {
        if (glow == pass && op > 0.002f) {
            FxDraw.disk(c, n, size, shape, param, col, op, glow);
        }
    }

    static void warp(Vector3f at, float radius, float strength, int mode, float age, Vector3f axis) {
        if (pass && strength > 0.001f) {
            Vec3 world = cam.add(at.x, at.y, at.z);
            ScreenFx.holdWarp(world, radius, strength, mode, axis == null ? new Vec3(0, 1, 0) : new Vec3(axis.x, axis.y, axis.z), age / 20);
        }
    }

    // ---------- the Visualizador's shared ki effects ----------

    static void kiBeam(Vector3f from, Vector3f to, float w, float[] col, float t, float a, int seed) {
        kiBeam(from, to, w, col, t, a, seed, -1);
    }

    // tail: how far (blocks) the beam's end has left the hand once it was let go, -1 while it is still fired.
    static void kiBeam(Vector3f from, Vector3f to, float w, float[] col, float t, float a, int seed, float tail) {
        if (a <= 0) {
            return;
        }
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        if (len < 0.01f) {
            return;
        }
        Vector3f dn = new Vector3f(d).div(len);
        if (KiMesh.ready()) {
            // Realista+Anime (the approved Kamehameha): one tube, twisted and swollen in the shader, tongues running
            // from the hand to the tip, white middle going to the color at the edge, and a head of energy at the tip.
            if (pass) {
                float fade = tail < 0 ? 1 : 1 - smooth((tail - len + 1) / 2);
                if (fade <= 0) {
                    return;
                }
                float radius = w * 0.55f;
                KiMesh.P p = KiMesh.p(KiMesh.BEAM);
                p.len = Math.max(0.1f, len);
                p.w = radius;
                p.flow = t * 0.35f;
                p.time = t / 20f;
                p.alpha = fade * (tail < 0 ? Math.min(1, a * 1.5f) : 1);
                p.tail = tail;
                p.color = col;
                KiMesh.queue(KiMesh.Mesh.TUBE, KiMesh.along(from, dn), p, KiMesh.BOTH, 14);
                if (tail < len - 0.3f) {
                    blast(to, radius * (1.55f + 0.12f * Mth.sin(t)), 0.14f, col, t, seed * 1.7f, p.alpha, false, 16);
                }
            }
            return;
        }
        rib(from, to, w * 2.6f, FxDraw.BEAM, 0.05f, col, 0.3f * a, true);
        rib(from, to, w, FxDraw.BEAM, 0.6f, col, 0.95f * a, true);
        rib(from, to, w * 0.45f, FxDraw.BEAM, 0.9f, WHITE, a, true);
        int n = (int) Math.min(24, Math.ceil(len / 0.8));
        for (int i = 0; i < n; i++) {
            float u = fract((i + t * 0.6f) / n);
            dsk(at(from, dn, u * len), dn, w * (1.4f + 0.15f * Mth.sin(t * 0.8f + i)), FxDraw.RING, 0.1f, col, 0.35f * a, true);
        }
        bb(to, w * 4, t * 0.1f, FxDraw.FLARE, 8 / 32f, col, a, true);
        bb(to, w * 2.2f, 0, FxDraw.SOFT, 0.85f, WHITE, a, true);
        bb(from, w * 2.4f, 0, FxDraw.SOFT, 0.6f, col, 0.8f * a, true);
        int tk = (int) Math.floor(t);
        for (int i = 0; i < 6; i++) {
            float u = Db.hash(tk, i, 1);
            Vector3f p = at(from, dn, u * len);
            Vector3f side = sphere(tk * 3 + seed, i);
            Vector3f q = at(p, side, w * 0.6f);
            rib(q, at(q, dn, -1.2f), 0.05f, FxDraw.STREAK, 0.9f, WHITE, 0.6f * a, true);
        }
    }

    static void kiBall(Vector3f p, float r, float[] col, float a, float t) {
        if (a <= 0) {
            return;
        }
        if (KiMesh.ready()) {
            // A small blast dome, held at life 0.3 so it keeps its color (lower life burns out white).
            if (pass) {
                blast(p, r, 0.3f, col, t, 3.1f, Math.min(1, a), false, 14);
            }
            return;
        }
        bb(p, r * 5, 0, FxDraw.SOFT, 0.15f, col, 0.4f * a, true);
        bb(p, r * 2.2f, t * 0.05f, FxDraw.FLARE, 6 / 32f, col, 0.8f * a, true);
        bb(p, r * 2, 0, FxDraw.SOFT, 0.85f, WHITE, a, true);
    }

    static float[] pale(float[] col, float k) {
        return new float[] {col[0] + (1 - col[0]) * k, col[1] + (1 - col[1]) * k, col[2] + (1 - col[2]) * k};
    }

    // The ki aura in the mod's style (v58): fire of glow rising round the body, white jets, wind on the ground, pebbles.
    static void kiAura(Vector3f feet, float t, float[] col, float a, float s, int seed, boolean self) {
        kiAura(feet, t, col, a, s, seed, self, a, 0);
    }

    // growth: how far the 3D shell has grown (0 to 1, it rises from the feet); flash: the burst when it is lit (1 to 0).
    static void kiAura(Vector3f feet, float t, float[] col, float a, float s, int seed, boolean self, float growth, float flash) {
        if (a <= 0) {
            return;
        }
        if (KiMesh.ready()) {
            // Realista+Anime: the approved "Aura de Ki em Casca", one shell round the body bent by 3D noise into
            // tongues of fire and cut by a threshold. Golden ki (Super Saiyajin) gets the spiky, taller form. In first
            // person the shell would wrap the camera, so only the pebbles show.
            if (pass && !self && growth > 0.001f) {
                boolean sup = col == COLORS[Db.GOLD] || col == COLORS[Db.GOLD_W];
                KiMesh.P p = KiMesh.p(KiMesh.SHELL);
                p.phase = t / 20f * 0.9f;
                p.time = t / 20f;
                p.growth = growth;
                p.flashAmt = flash;
                p.alpha = Mth.clamp(0.45f + 0.55f * a, 0, 1);
                p.color = col;
                p.seed = 0.37f * seed;
                p.tongue = sup ? 0.4f : 0.36f;
                p.spike = sup ? 0.34f : 0.1f;
                p.spikeFreq = sup ? 7.5f : 4.5f;
                p.spikeSharp = sup ? 2.6f : 2.2f;
                p.crown = sup ? 0.85f : 0.55f;
                p.size = new float[] {0.62f, 1.12f, 0.5f};
                Matrix4f m = new Matrix4f().translation(feet.x, feet.y + 1.0f * s, feet.z).scale(s);
                KiMesh.queue(KiMesh.Mesh.AURA, m, p, KiMesh.BOTH, 10);
            }
            pebbles(feet, t, a, s, seed);
            return;
        }
        Vector3f left = FxDraw.left();
        Vector3f look = new Vector3f(feet).add(0, s, 0).normalize();
        Vector3f back = new Vector3f(look.x, 0, look.z);
        if (back.lengthSquared() < 1.0E-6f) {
            back.set(0, 0, 1);
        }
        back.normalize();
        float[] pl = pale(col, 0.55f);
        if (!self) {
            bb(at(new Vector3f(feet).add(0, 1.1f * s, 0), back, 0.5f * s), 3.6f * s, 0, FxDraw.SOFT, 0.05f, col, 0.22f * a, true);
        }
        for (int i = 0; i < 34; i++) {
            float sp = 0.035f + 0.025f * Db.hash(i, 1, seed);
            float raw = t * sp + Db.hash(i, 2, seed);
            float ph = fract(raw);
            int cyc = (int) Math.floor(raw);
            float side = (Db.hash(i, cyc, seed + 3) - 0.5f) * 2;
            float edge = Math.abs(side);
            float h0 = (0.05f + 1.0f * Db.hash(i, cyc, seed + 4)) * s;
            Vector3f p = at(at(feet, left, side * (0.5f + 0.55f * ph) * s), back, (0.3f + 0.3f * (1 - edge)) * s)
                    .add(0, h0 + ph * (2.4f + 1.0f * Db.hash(i, 5, seed)) * s, 0);
            if (self && p.lengthSquared() < 2.5f) {
                continue;
            }
            float sz = (1.25f + 0.9f * Db.hash(i, 6, seed)) * s * Mth.sin(Math.min(1, ph * 1.4f) * Mth.PI * 0.5f + 0.35f) * (1.1f - 0.5f * ph);
            bb(p, sz, i * 1.7f + t * 0.04f, FxDraw.FIRE, Math.min(0.95f, 0.12f + ph * 0.8f), ph < 0.25f ? pl : col, a * 0.75f * (1 - ph * 0.6f), true);
        }
        for (int i = 0; i < 14; i++) {
            float ph = fract(t * (0.06f + 0.04f * Db.hash(i, 7, seed)) + Db.hash(i, 8, seed));
            float side = (Db.hash(i, 9, seed) - 0.5f) * 2;
            Vector3f p = at(at(feet, left, side * 0.5f * s), back, 0.15f * s).add(0, (0.1f + ph * 2.4f) * s, 0);
            Vector3f q = at(new Vector3f(p).add(0, (0.35f + 0.3f * Db.hash(i, 10, seed)) * s, 0), left, side * 0.08f * s);
            rib(p, q, 0.07f * s, FxDraw.STREAK, 0.8f, i % 3 != 0 ? col : pl, a * 0.7f * Mth.sin(ph * Mth.PI), true);
        }
        for (int i = 0; i < 6; i++) {
            float ph = fract(t * 0.09f + Db.hash(i, 4, seed));
            float sd = i % 2 != 0 ? 1 : -1;
            Vector3f p = at(at(feet, left, sd * (0.55f + 0.35f * Db.hash(i, 5, seed)) * s), back, 0.1f * s).add(0, (0.2f + ph * 2.6f) * s, 0);
            rib(p, new Vector3f(p).add(0, 0.8f * s, 0), 0.05f * s, FxDraw.BEAM, 0.9f, pl, a * 0.7f * Mth.sin(ph * Mth.PI), true);
        }
        Vector3f up = new Vector3f(0, 1, 0);
        for (int r = 0; r < 2; r++) {
            float ph = fract(t * 0.06f + r * 0.5f);
            dsk(new Vector3f(feet).add(0, 0.05f + ph * 0.12f, 0), up, (0.8f + 3.4f * easeOut(ph)) * s, FxDraw.RING, 0.05f, pl, a * (1 - ph) * 0.7f, true);
        }
        for (int i = 0; i < 10; i++) {
            float ph = fract(t * 0.07f + Db.hash(i, 6, seed));
            float ang = Db.hash(i, 7, seed) * Mth.TWO_PI;
            Vector3f d = new Vector3f(Mth.cos(ang), 0, Mth.sin(ang));
            float r0 = (0.5f + 2.2f * ph) * s;
            rib(at(feet, d, r0).add(0, 0.06f, 0), at(feet, d, r0 + 0.6f * s).add(0, 0.06f, 0), 0.05f * s, FxDraw.STREAK, 0.9f, pl, a * 0.5f * (1 - ph), true);
        }
        dsk(new Vector3f(feet).add(0, 0.04f, 0), up, 2.6f * s, FxDraw.SOFT, 0.2f, col, 0.3f * a, true);
        pebbles(feet, t, a, s, seed);
    }

    // Small stones floating up round someone charging, each at its own pace.
    static void pebbles(Vector3f feet, float t, float a, float s, int seed) {
        for (int i = 0; i < 12; i++) {
            float ph = fract(t * 0.025f + Db.hash(i, 8, seed));
            float ang = Db.hash(i, 9, seed) * Mth.TWO_PI;
            float r = (0.6f + 1.1f * Db.hash(i, 10, seed)) * s;
            Vector3f p = new Vector3f(feet).add(Mth.cos(ang) * r + Mth.sin(t * 1.7f + i) * 0.02f, ph * 1.9f * s, Mth.sin(ang) * r);
            bb(p, (0.05f + 0.05f * Db.hash(i, 11, seed)) * s, i + t * 0.05f, FxDraw.SOLID, 0, PEBBLE, a * Math.min(1, (1 - ph) * 3), false);
        }
    }

    static void kiBoom(Vector3f p, float age, float r, float[] col, int seed, float ground) {
        if (KiMesh.ready()) {
            if (age < 0 || age > 110) {
                return;
            }
            boom3d(p, age, r * 1.4f, col, seed, ground);
            if (age < 60) {
                dsk(new Vector3f(p.x, ground + 0.03f, p.z), new Vector3f(0, 1, 0), r * 2.4f, FxDraw.SOFT, 0.15f, DIRT, 0.8f * smooth(age / 4) * (1 - smooth((age - 40) / 20)), false);
            }
            return;
        }
        if (age < 0 || age > 60) {
            return;
        }
        float k = easeOut(age / 12);
        float f = 1 - smooth((age - 8) / 40);
        if (age < 10) {
            bb(p, r * 4 * (1 - age / 10), 0.3f, FxDraw.FLARE, 10 / 32f, WHITE, 1 - age / 10, true);
        }
        bb(p, r * 2.6f * k, 0, FxDraw.SOFT, 0.5f, col, 0.85f * f, true);
        bb(p, r * 1.6f * k, 0, FxDraw.SOFT, 0.9f, WHITE, 0.7f * f * (1 - smooth(age / 24)), true);
        dsk(p, new Vector3f(p).negate(), r * 2.4f * k, FxDraw.RING, 0.06f, WHITE, f, true);
        Vector3f floor = new Vector3f(p.x, ground, p.z);
        if (age < 24) {
            dsk(new Vector3f(floor).add(0, 0.05f, 0), new Vector3f(0, 1, 0), r * 3.5f * easeOut(age / 20), FxDraw.RING, 0.08f, col, 1 - age / 24, true);
            warp(p, r * 3, 0.8f * (1 - age / 24), 0, age, null);
        }
        nrDust(floor, age - 4, seed, 16, r * 0.9f, DUST, 56);
        dsk(new Vector3f(floor).add(0, 0.03f, 0), new Vector3f(0, 1, 0), r * 2.4f, FxDraw.SOFT, 0.15f, DIRT, 0.8f * smooth(age / 4), false);
    }

    // ---------- Realista+Anime pieces (KiMesh), queued in the glow call only ----------

    private static final float[] SMOKE_3D = FxDraw.rgb(0x6B6F78);
    private static final float[] RING_DUST = FxDraw.rgb(0x8A8D94);

    // The blast dome (smoke: the cel-shaded smoke in 3 bands). life 0 solid, tearing into tongues until 1 (gone).
    static void blast(Vector3f c, float r, float life, float[] col, float t, float seed, float alpha, boolean smoke, int order) {
        if (!pass || life >= 1 || r <= 0.02f || alpha <= 0.002f) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.BLAST);
        p.life = Math.max(0, life);
        p.seed = seed;
        p.time = t / 20f;
        p.flat = 0.3f;
        p.color = col;
        p.alpha = Math.min(1, alpha);
        p.smoke = smoke ? 1 : 0;
        p.glow = !smoke;
        KiMesh.queue(KiMesh.Mesh.BLAST, KiMesh.sphere(c, r), p, KiMesh.BOTH, smoke ? 6 : order);
    }

    // The dust ring running over the ground (ringR its front, max the square it is drawn on).
    static void dustRing(Vector3f floor, float ringR, float max, float life, float[] tint, float seed) {
        if (!pass || life >= 1 || max <= 0.05f) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.RING);
        p.ringR = ringR;
        p.ringMax = max;
        p.life = Math.max(0, life);
        p.color = tint;
        p.dust = RING_DUST;
        p.seed = seed;
        p.glow = false;
        KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(new Vector3f(floor).add(0, 0.06f, 0), max), p, KiMesh.BOTH, 7);
    }

    // The white burst of a hit: a fresnel shell.
    static void flashBall(Vector3f c, float r, float alpha, float[] col, float seed) {
        if (!pass || alpha <= 0.002f || r <= 0.02f) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.FLASH);
        p.alpha = alpha;
        p.color = col;
        p.seed = seed;
        KiMesh.queue(KiMesh.Mesh.FLASH, KiMesh.sphere(c, r), p, KiMesh.FRONT, 20);
    }

    // A ki blast of radius R landing (the approved Kamehameha end and Explosao de ki): the dome tearing into tongues,
    // the white burst, the dust ring over the ground, the shock wave bending the screen, then the smoke staying on top.
    static void boom3d(Vector3f p, float age, float R, float[] col, int seed, float ground) {
        boolean low = p.y - ground < R * 0.6f;
        Vector3f c = low ? new Vector3f(p.x, ground + 0.4f, p.z) : new Vector3f(p);
        blast(c, R * easeOut(age / 12), Mth.clamp(age / 40, 0, 1), col, age, seed * 1.37f, 1, false, 14);
        float late = Math.max(0, age - 20) / 20;
        if (age >= 20) {
            blast(new Vector3f(c).add(0, 0.2f + late * 0.45f, 0), R * 0.98f + late * 0.4f, 0.35f + 0.65f * Mth.clamp((age - 20) / 80, 0, 1), SMOKE_3D, age,
                    seed * 2.11f, 1, true, 6);
        }
        if (p.y - ground < R * 1.5f) {
            float max = R * 2.3f;
            dustRing(new Vector3f(p.x, ground, p.z), max * 0.86f * easeOut(age / 26), max, Mth.clamp(age / 44, 0, 1), col, seed * 0.7f);
        }
        if (age < 6) {
            float k = age / 6;
            flashBall(c, R * (0.12f + 0.7f * Mth.sqrt(k)), 1 - k, pale(col, 0.6f), seed);
        }
        if (age < 24) {
            warp(c, R * 2, 0.8f * (1 - age / 24), 0, age, null);
        }
    }

    static void nrHit(Vector3f p, float age, float[] col, float s, int seed, float life) {
        if (age < 0 || age > life) {
            return;
        }
        float k = age / life;
        bb(p, 2.4f * s * (1 - k * 0.5f), 0.3f, FxDraw.FLARE, 8 / 32f, col, 1 - k, true);
        bb(p, 0.9f * s, 0, FxDraw.SOFT, 0.9f, WHITE, (1 - k) * 0.9f, true);
        dsk(p, new Vector3f(p).negate(), (0.6f + 2.6f * easeOut(k)) * s, FxDraw.RING, 0.12f, col, 1 - k, true);
        for (int i = 0; i < 10; i++) {
            Vector3f d = sphere(seed + 60, i);
            Vector3f q = at(p, d, (0.2f + 1.4f * easeOut(k)) * s);
            rib(at(q, d, -0.35f * s * (1 - k)), q, 0.05f * s, FxDraw.STREAK, 0.9f, WHITE, 1 - k, true);
        }
    }

    static void nrDust(Vector3f p, float age, int seed, int n, float s, float[] col, float life) {
        if (age < 0 || age > life) {
            return;
        }
        float k = age / life;
        float e = easeOut(age / 14);
        for (int i = 0; i < n; i++) {
            float a = Db.hash(seed, i, 1) * Mth.TWO_PI;
            float rr = (0.4f + 1.8f * e * (0.5f + Db.hash(seed, i, 2))) * s;
            bb(new Vector3f(p).add(Mth.cos(a) * rr, 0.2f + e * 0.6f * s * Db.hash(seed, i, 3), Mth.sin(a) * rr), (0.8f + 1.4f * e) * s, i,
                    FxDraw.SMOKE, 0.3f + k * 0.3f, col, 0.75f * (1 - k), false);
        }
    }

    static void nrBolt(Vector3f a, Vector3f b, int seed, float w, float[] col, float op, float jag, int segs, int br) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-3f || op <= 0) {
            return;
        }
        Vector3f dn = new Vector3f(d).div(len);
        Vector3f u = new Vector3f(dn).cross(Math.abs(dn.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f v = new Vector3f(dn).cross(u);
        Vector3f[] pts = new Vector3f[segs + 1];
        pts[0] = new Vector3f(a);
        float amp = jag * Math.min(len, 8) * 0.3f;
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            float m = i == segs ? 0 : amp * (float) Math.sqrt(Mth.sin(t * Mth.PI));
            pts[i] = at(at(at(a, d, t), u, (Db.hash(seed, i, 1) - 0.5f) * 2 * m), v, (Db.hash(seed, i, 2) - 0.5f) * 2 * m);
        }
        for (int i = 1; i < pts.length; i++) {
            rib(pts[i - 1], pts[i], w, FxDraw.BEAM, 0.85f, col, op, true);
            rib(pts[i - 1], pts[i], w * 7, FxDraw.BEAM, 0.05f, col, op * 0.2f, true);
        }
        for (int j = 0; j < br; j++) {
            Vector3f s = pts[1 + (int) Math.floor(Db.hash(seed, j, 3) * (segs - 1))];
            Vector3f dir = at(at(dn, u, (Db.hash(seed, j, 4) - 0.5f) * 2), v, (Db.hash(seed, j, 5) - 0.5f) * 2).normalize();
            nrBolt(s, at(s, dir, len * (0.12f + 0.2f * Db.hash(seed, j, 6))), seed * 7 + j + 1, w * 0.6f, col, op * 0.8f, jag, 4, 0);
        }
    }

    // The saw disc (Kienzan, Death Saucer): thin, flat, teeth on the rim, spin marks (never a plate of light).
    static void sawDisc(Vector3f p, float r, float t, float a, float[] col, Vector3f up) {
        if (a <= 0) {
            return;
        }
        dsk(p, up, r * 2, SAW, 0.6f, col, a, false);
        dsk(new Vector3f(p).add(0, 0.01f, 0), up, r * 2.6f, FxDraw.SOFT, 0.55f, col, 0.3f * a, true);
        Vector3f u = new Vector3f(up).cross(1, 0, 0.001f).normalize();
        Vector3f v = new Vector3f(up).cross(u);
        for (int i = 0; i < 3; i++) {
            float th = t * 1.6f + i * 2.1f;
            Vector3f q0 = at(at(p, u, Mth.cos(th) * r * 1.03f), v, Mth.sin(th) * r * 1.03f);
            Vector3f q1 = at(at(p, u, Mth.cos(th + 0.9f) * r * 1.03f), v, Mth.sin(th + 0.9f) * r * 1.03f);
            rib(q0, q1, 0.03f, FxDraw.STREAK, 0.9f, WHITE, 0.7f * a, true);
        }
    }

    // ---------- Big Bang (v59/v60): a ball of Zanka tongues ----------

    private static final float[] BB_COL = FxDraw.rgb(0x2F7DFF);

    static void tongue(Vector3f base, Vector3f dir, float len, float w, float life, float sd, float[] col, boolean halo) {
        int lifeStep = Math.min(15, (int) Math.floor(life * 16));
        int seedStep = Math.min(15, (int) Math.floor(fract(sd) * 16));
        rib(base, at(base, dir, len), w, TONGUE, (lifeStep * 16 + seedStep) / 255f, col, 1, false);
        if (halo) {
            Vector3f bk = new Vector3f(base).normalize(w * 0.8f);
            rib(new Vector3f(base).add(bk), at(base, dir, len * 1.1f).add(bk), w * 1.45f, FxDraw.BEAM, 0.1f, col, 0.2f * (1 - life), true);
        }
    }

    static void bbTongues(Vector3f p, float r, float t, float a, int n, int seed, float spread) {
        if (a <= 0) {
            return;
        }
        Vector3f look = new Vector3f(p).normalize();
        for (int i = 0; i < n; i++) {
            float per = 9 + 9 * Db.hash(seed, i, 0);
            float u = (t + Db.hash(seed, i, 1) * per) / per;
            int cyc = (int) Math.floor(u);
            float life = u - cyc;
            if (life * 6 > 1 && a < 1 && Db.hash(seed, i, cyc * 7 + 9) > a) {
                continue;
            }
            Vector3f d = new Vector3f(Db.hash(seed, i, cyc * 7 + 1) - 0.5f, Db.hash(seed, i, cyc * 7 + 2) - 0.5f, Db.hash(seed, i, cyc * 7 + 3) - 0.5f);
            d.sub(new Vector3f(look).mul(d.dot(look)));
            if (d.lengthSquared() < 1.0E-6f) {
                d.set(0, 1, 0);
            }
            d.normalize();
            d.y += 0.55f;
            d.sub(new Vector3f(look).mul(d.dot(look))).normalize();
            float grow = Math.min(1, life * 6);
            float len = r * (0.9f + 0.7f * Db.hash(seed, i, cyc * 7 + 4)) * spread * (0.6f + 0.5f * life) * grow;
            Vector3f b0 = at(at(p, d, r * 0.15f), look, 0.05f * (Db.hash(seed, i, cyc * 7 + 8) - 0.5f));
            tongue(b0, d, len, r * (1.3f + 0.5f * Db.hash(seed, i, cyc * 7 + 5)), life, Db.hash(seed, i, cyc * 7 + 6), BB_COL, i % 4 == 0);
        }
    }

    static void bigBangBall(Vector3f p, float r, float t, float a) {
        if (a <= 0) {
            return;
        }
        float[] col = COLORS[Db.BLUE];
        float[] pl = pale(col, 0.6f);
        Vector3f look = new Vector3f(p).normalize();
        bb(at(p, look, r * 1.6f), r * 5, 0, FxDraw.SOFT, 0.1f, col, 0.35f * a, true);
        bbTongues(p, r, t, a, 30, 301, 1);
        for (int k = 0; k < 5; k++) {
            Vector3f ax = rotY(sphere(160, k), t * 0.03f * (1 + 0.3f * k));
            float ph = fract(t * (0.045f + 0.01f * k) + k / 5f);
            float lat = -1 + 2 * ph;
            float rad = r * 1.25f * (float) Math.sqrt(Math.max(0, 1 - lat * lat));
            if (rad > 0.01f) {
                dsk(at(p, ax, lat * r * 1.25f), ax, rad * 2, FxDraw.RING, 0.07f, pl, a * Math.min(1, 2 * Mth.sin(ph * Mth.PI)), false);
            }
        }
    }

    static void bbBoom(Vector3f p, float age, float r, float ground) {
        if (age < 0 || age > 70) {
            return;
        }
        Vector3f look = new Vector3f(p).normalize();
        float g = easeOut(Math.min(1, age / 10));
        if (age < 6) {
            bb(p, r * 5 * (1 - age / 6), 0, FxDraw.SOFT, 0.6f, WHITE, 1 - age / 6, true);
        }
        bb(at(p, look, r * 1.5f), r * 3.5f * g, 0, FxDraw.SOFT, 0.2f, BB_COL, 0.45f * (1 - smooth((age - 10) / 30)), true);
        for (int i = 0; i < 46; i++) {
            float life = Mth.clamp((age - Db.hash(i, 1, 77) * 6) / (26 + 14 * Db.hash(i, 2, 77)), 0, 1);
            if (life <= 0 || life >= 1) {
                continue;
            }
            Vector3f d = sphere(707, i);
            d.sub(new Vector3f(look).mul(0.8f * d.dot(look))).normalize();
            float len = r * (0.7f + 0.8f * Db.hash(i, 3, 77)) * easeOut(Math.min(1, life * 3));
            Vector3f dir = new Vector3f(d).add(0, 0.35f * life, 0).normalize();
            tongue(at(p, d, r * 0.25f + r * 0.6f * easeOut(life)), dir, len, r * (0.7f + 0.3f * Db.hash(i, 4, 77)), life, Db.hash(i, 5, 77), BB_COL, i % 5 == 0);
        }
        for (int i = 0; i < 26; i++) {
            float ang = i / 26f * Mth.TWO_PI;
            float life = Mth.clamp((age - 3) / 30, 0, 1);
            if (life <= 0 || life >= 1) {
                continue;
            }
            float rr = r * (0.6f + 1.6f * easeOut(life));
            Vector3f b0 = new Vector3f(p.x + Mth.cos(ang) * rr, ground + 0.02f, p.z + Mth.sin(ang) * rr);
            tongue(b0, new Vector3f(Mth.cos(ang) * 0.4f, 1, Mth.sin(ang) * 0.4f).normalize(), r * (0.6f + 0.4f * Db.hash(i, 6, 77)), r * 0.5f, life,
                    Db.hash(i, 7, 77), BB_COL, false);
        }
        if (age < 22) {
            dsk(new Vector3f(p.x, ground + 0.06f, p.z), new Vector3f(0, 1, 0), r * 4 * easeOut(age / 18), FxDraw.RING, 0.06f, WHITE, 1 - age / 22, true);
            warp(p, r * 3, 0.9f * (1 - age / 22), 0, age, null);
        }
        for (int i = 0; i < 10; i++) {
            float a2 = age - 12 - i * 1.5f;
            float k = a2 / 46;
            if (a2 < 0 || k > 1) {
                continue;
            }
            Vector3f q = new Vector3f(p).add((Db.hash(i, 8, 77) - 0.5f) * r * 2, r * 0.3f + k * r * 1.6f, (Db.hash(i, 9, 77) - 0.5f) * r * 2);
            bb(q, r * (1 + k), i + age * 0.01f, FxDraw.SMOKE, 0.35f, new float[] {0.26f, 0.25f, 0.3f}, 0.7f * (1 - k), false);
        }
        nrDust(new Vector3f(p.x, ground, p.z), age - 4, 6, 14, r * 0.8f, new float[] {0.55f, 0.5f, 0.45f}, 50);
    }

    // ---------- each move ----------

    private static void ball(Fx f, float age, float out) {
        int style = (int) f.b / 16;
        float[] col = color(f.b % 16);
        Vector3f p = rel(f.pos);
        float r = f.a;
        kiBall(p, r, col, out, age);
        if (style == 1 && !KiMesh.ready()) {
            // Charging (Kamehameha): sparks crackling out of it.
            float k = Mth.clamp((r - 0.06f) / 0.16f, 0, 1);
            for (int i = 0; i < 6; i++) {
                Vector3f d = sphere((int) Math.floor(age) * 3, i);
                rib(p, at(p, d, 0.25f + 0.3f * k), 0.025f, FxDraw.STREAK, 0.9f, pale(col, 0.85f), 0.8f * k * out, true);
            }
        } else if (style == 2 && f.trail.size() > 2) {
            Vec3 back = f.trail.peekFirst();
            int skip = Math.max(0, f.trail.size() - 4);
            int i = 0;
            for (Vec3 q : f.trail) {
                if (i++ == skip) {
                    back = q;
                    break;
                }
            }
            rib(rel(back), p, r, FxDraw.STREAK, 0.6f, col, 0.6f * out, true);
        }
    }

    private static void aura(Fx f, float age, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        float in = smooth(age / 4) * out;
        float strength = (float) f.dir.x;
        // The shell grows up from the feet in 0.7 s and bursts when a strong aura is lit (Kaioken, Super Saiyajin).
        float growth = Math.min(smooth(age / 14), out);
        float flash = strength >= 0.6f && age < 6 ? 1 - age / 6 : 0;
        kiAura(rel(e.getPosition(partial)), age, color(f.b), strength * in, Math.max(0.3f, f.c), f.id % 7 + 1, FxAnchor.firstPersonSelf(f.id), growth, flash);
    }

    private static void saw(Fx f, float age, float out) {
        float[] col = color(f.b);
        Vector3f p = rel(f.pos);
        Vector3f up = new Vector3f((float) f.dir.x, (float) f.dir.y, (float) f.dir.z);
        if (up.lengthSquared() < 1.0E-6f) {
            up.set(0, 1, 0);
        }
        up.normalize();
        sawDisc(p, f.a, age, out, col, up);
        if (f.trail.size() > 3) {
            Vec3 q = null;
            int skip = f.trail.size() - 4;
            int i = 0;
            for (Vec3 v : f.trail) {
                if (i++ == skip) {
                    q = v;
                    break;
                }
            }
            if (q != null && q.distanceToSqr(f.pos) > 0.04) {
                rib(rel(q), p, 0.35f * f.a / 0.6f, FxDraw.STREAK, 0.6f, col, 0.5f * out, true);
            }
        }
    }

    private static final float[] GENKI_BLUE = FxDraw.rgb(0x4FB4FF);
    private static final float[] GENKI_FLASH = FxDraw.rgb(0xBFE6FF);

    private static void genki(Fx f, float age, float out) {
        Vector3f p = rel(f.pos);
        float r = f.a;
        float a = smooth(age / 10) * out;
        if (KiMesh.ready()) {
            // Realista+Anime (the approved Genki Dama): a ball of light (white middle, light cyan body, sky-blue edge,
            // plasma turning inside in veins) with a halo of rays round it 1.6 times its size.
            if (pass && a > 0.002f) {
                KiMesh.P core = KiMesh.p(KiMesh.GENKI);
                core.time = age / 20f;
                core.alpha = a;
                KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(p, r), core, KiMesh.FRONT, 12);
                KiMesh.P halo = KiMesh.p(KiMesh.HALO);
                halo.time = age / 20f;
                halo.alpha = a;
                KiMesh.queue(KiMesh.Mesh.HALO, KiMesh.sphere(p, r * 1.6f), halo, KiMesh.BACK, 13);
            }
            if (f.b > 0.5f) {
                // Points of light from the whole sky and the ground, each on its own curve, speeding up into the ball.
                Vector3f feet = rel(f.dir);
                for (int i = 0; i < 140; i++) {
                    float ph = fract(age * (0.006f + Db.hash(i, 5, 1) * 0.008f) + Db.hash(i, 5, 2));
                    float e = (float) Math.pow(ph, 1.7);
                    Vector3f start = new Vector3f(feet).add((Db.hash(i, 5, 3) - 0.5f) * 50, Db.hash(i, 5, 4) > 0.5f ? 0.2f : 8 + Db.hash(i, 5, 5) * 14,
                            (Db.hash(i, 5, 6) - 0.5f) * 50);
                    Vector3f ctrl = new Vector3f(start).lerp(p, 0.5f).add((Db.hash(i, 5, 7) - 0.5f) * 14, 3 + Db.hash(i, 5, 8) * 8, (Db.hash(i, 5, 9) - 0.5f) * 14);
                    Vector3f q = new Vector3f(start).lerp(ctrl, e).lerp(new Vector3f(ctrl).lerp(p, e), e);
                    float vis = smooth(ph / 0.12f) * (1 - smooth((ph - 0.88f) / 0.12f));
                    bb(q, (0.1f + 0.08f * Db.hash(i, 5, 10)) * (0.6f + 0.9f * e), 0, FxDraw.SOFT, 0.9f, i % 3 != 0 ? COLORS[Db.KAME_W] : WHITE, 0.9f * vis * out, true);
                }
            }
            return;
        }
        bb(p, r * 4.4f, 0, FxDraw.SOFT, 0.15f, COLORS[Db.BLUE], 0.3f * a, true);
        bb(p, r * 4.2f, 0, GENKI, 0.3f, WHITE, a, false);
        if (f.b > 0.5f) {
            // Points of light come from the ground, the trees and the whole sky and gather in it.
            Vector3f feet = rel(f.dir);
            for (int i = 0; i < 70; i++) {
                float ph = fract(age * (0.006f + Db.hash(i, 5, 1) * 0.008f) + Db.hash(i, 5, 2));
                Vector3f start = new Vector3f(feet).add((Db.hash(i, 5, 3) - 0.5f) * 50, Db.hash(i, 5, 4) > 0.5f ? 0.2f : 8 + Db.hash(i, 5, 5) * 14,
                        (Db.hash(i, 5, 6) - 0.5f) * 50);
                Vector3f q = new Vector3f(start).lerp(p, ph * ph);
                bb(q, 0.12f + 0.1f * ph, 0, FxDraw.SOFT, 0.9f, i % 3 != 0 ? COLORS[Db.KAME_W] : WHITE, 0.9f * Mth.sin(ph * Mth.PI) * out, true);
            }
        }
    }

    private static void genkiBoom(Fx f, float a) {
        float s = f.a;
        Vector3f c = rel(f.pos);
        float ground = groundY(f);
        if (KiMesh.ready()) {
            // The approved landing: the blue dome tearing into tongues, the white burst, the dust ring, then the smoke.
            float rb = 7 * s;
            float tb = a - 2.4f;
            Vector3f mid = new Vector3f(c.x, ground + 0.3f, c.z);
            if (tb > 0) {
                blast(mid, rb * easeOut(tb / 20), Mth.clamp(tb / 56, 0, 1), GENKI_BLUE, a, 9.3f, 1, false, 14);
                dustRing(new Vector3f(c.x, ground, c.z), rb * 2.3f * easeOut(tb / 32), rb * 2.6f, Mth.clamp(tb / 52, 0, 1), GENKI_BLUE, 4.1f);
            }
            if (a >= 32) {
                float late = (a - 32) / 20;
                blast(new Vector3f(c.x, ground + 0.2f + late * 0.5f, c.z), rb * (1 + late * 0.1f), 0.35f + 0.65f * Mth.clamp((a - 32) / 90, 0, 1), SMOKE_3D, a,
                        5.7f, 1, true, 6);
            }
            if (a < 7) {
                float k = a / 7;
                flashBall(mid, rb * (0.3f + 0.7f * Mth.sqrt(k)), 1 - k, GENKI_FLASH, 2.9f);
            }
            if (a < 30) {
                warp(mid, rb * 2, 1 - a / 30, 0, a, null);
            }
            if (a < 90) {
                dsk(new Vector3f(c.x, ground + 0.03f, c.z), new Vector3f(0, 1, 0), rb * 1.6f, FxDraw.SOFT, 0.15f, DIRT, 0.8f * smooth(a / 4) * (1 - smooth((a - 60) / 30)),
                        false);
            }
            return;
        }
        kiBoom(c, a, 7 * s, COLORS[Db.KAME], 9, ground);
        kiBoom(new Vector3f(c).add(0, s, 0), a - 6, 5 * s, COLORS[Db.BLUE], 19, ground);
        if (a < 70) {
            float g = easeOut(Math.min(1, a / 22));
            float fd = 1 - smooth((a - 30) / 40);
            bb(c, (4 + 9 * g) * 2.2f * s, 0, FxDraw.SOFT, 0.6f, COLORS[Db.KAME], 0.75f * fd, true);
            bb(c, (3 + 8 * g) * 2.1f * s, 0, GENKI, 0.7f, WHITE, fd, false);
        }
        if (a < 30) {
            for (int i = 0; i < 3; i++) {
                dsk(new Vector3f(c.x, ground + 0.06f + i * 0.4f, c.z), new Vector3f(0, 1, 0), (6 + 18 * easeOut(Math.max(0, a - i * 3) / 26)) * s,
                        FxDraw.RING, 0.05f, COLORS[Db.KAME_W], 1 - a / 30, true);
            }
        }
    }

    private static void deathBall(Fx f, float age, float out) {
        Vector3f p = rel(f.pos);
        float r = f.a;
        float grow = (r - 0.08f) / 3.4f;
        bb(p, r * 4.5f, 0, FxDraw.SOFT, 0, COLORS[Db.RED], 0.35f * out, true);
        bb(p, r * 2.8f, 0, FxDraw.SOFT, 0.1f, COLORS[Db.ORANGE], 0.45f * out, true);
        bb(p, r * 2.2f, age * 0.002f, FxDraw.SUN, 0.1f, new float[] {1, 0.62f, 0.5f}, out, false);
        if (grow > 0.2f) {
            warp(p, r * 2, 0.4f * out, 0, age, null);
        }
    }

    private static void supernova(Fx f, float a) {
        float s = f.a;
        Vector3f c = rel(f.pos);
        float ground = groundY(f);
        kiBoom(c, a, 7 * s, COLORS[Db.RED], 11, ground);
        kiBoom(new Vector3f(c).add(1.5f * s, 0.5f * s, 0), a - 5, 5 * s, COLORS[Db.ORANGE], 12, ground);
        kiBoom(new Vector3f(c).add(-1.5f * s, s, 0), a - 9, 5 * s, COLORS[Db.RED], 13, ground);
        if (a < 80) {
            float g = easeOut(Math.min(1, a / 24));
            float fd = 1 - smooth((a - 34) / 46);
            bb(c, (3.5f + 8 * g) * 2.2f * s, a * 0.002f, FxDraw.SUN, 0.1f, new float[] {1, 0.5f, 0.35f}, fd, false);
            bb(c, (3.5f + 9 * g) * 4 * s, 0, FxDraw.SOFT, 0.1f, COLORS[Db.RED], 0.5f * fd, true);
        }
        if (a < 32) {
            for (int i = 0; i < 3; i++) {
                dsk(new Vector3f(c.x, ground + 0.06f + i * 0.5f, c.z), new Vector3f(0, 1, 0), (6 + 22 * easeOut(Math.max(0, a - i * 3) / 28)) * s,
                        FxDraw.RING, 0.05f, COLORS[Db.RED_W], 1 - a / 32, true);
            }
        }
    }

    private static void deathBeam(Fx f, float a) {
        Vector3f tip = rel(f.pos);
        Vector3f end = rel(f.pos.add(f.dir));
        float fd = 1 - a / 10;
        rib(tip, end, 0.08f * fd + 0.02f, FxDraw.BEAM, 0.9f, COLORS[Db.PINK], fd, true);
        rib(tip, end, 0.4f * fd, FxDraw.BEAM, 0.05f, COLORS[Db.PURPLE], 0.4f * fd, true);
        bb(tip, 0.25f, 0, FxDraw.SOFT, 0.8f, COLORS[Db.PINK], 0.7f * fd, true);
        bb(end, 0.2f, 0, FxDraw.SOFT, 0.9f, COLORS[Db.PINK_W], fd, true);
    }

    private static void makanko(Fx f, float age) {
        float fd = 1 - smooth((age - f.a) / 10);
        Vector3f tip = rel(f.pos);
        Vector3f to = rel(f.pos.add(f.dir));
        Vector3f end = new Vector3f(tip).lerp(to, Math.min(1, age / 4));
        Vector3f d = new Vector3f(to).sub(tip).normalize();
        Vector3f u = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f v = new Vector3f(d).cross(u).normalize();
        rib(tip, end, 0.14f, FxDraw.BEAM, 0.9f, COLORS[Db.GOLD], fd, true);
        rib(tip, end, 0.6f, FxDraw.BEAM, 0.05f, COLORS[Db.GOLD], 0.3f * fd, true);
        // Two tight spirals stuck to the ray (Stark): one gold and one pink, half a turn apart.
        float len = tip.distance(end);
        int steps = (int) Mth.clamp(len * 7.3f, 8, 220);
        for (int h = 0; h < 2; h++) {
            float[] col = h == 1 ? COLORS[Db.PINK] : COLORS[Db.GOLD];
            float[] hot = h == 1 ? COLORS[Db.PINK_W] : COLORS[Db.GOLD_W];
            Vector3f last = null;
            for (int i = 0; i <= steps; i++) {
                float s = i / (float) steps * len;
                float th = s * 5.5f - age * 1.4f + h * Mth.PI;
                Vector3f p = at(at(at(tip, d, s), u, Mth.cos(th) * 0.13f), v, Mth.sin(th) * 0.13f);
                if (last != null) {
                    rib(last, p, 0.06f, FxDraw.STREAK, 0.9f, col, 0.95f * fd, true);
                    rib(last, p, 0.025f, FxDraw.SOLID, 0, hot, 0.9f * fd, true);
                }
                last = p;
            }
        }
    }

    private static void sparks(Fx f, float age, float out) {
        float k = Mth.clamp(f.a, 0, 1) * out;
        float[] col = color(f.b);
        float[] hot = pale(col, 0.75f);
        int tk = (int) Math.floor(age);
        Vector3f tip = rel(f.pos);
        if (f.dir.lengthSqr() < 1.0E-6) {
            bb(tip, 0.4f + 0.3f * k, 0, FxDraw.SOFT, 0.8f, col, 0.9f * k, true);
            for (int j = 0; j < 4; j++) {
                nrBolt(tip, at(tip, sphere(tk * 3, j), 0.25f + 0.35f * k), tk * 4 + j, 0.02f, hot, k, 0.5f, 4, 0);
            }
            return;
        }
        Vector3f feet = rel(f.dir);
        for (int j = 0; j < 3; j++) {
            nrBolt(new Vector3f(feet).add((Db.hash(tk, j, 1) - 0.5f) * 2, 0.1f, (Db.hash(tk, j, 2) - 0.5f) * 2),
                    new Vector3f(feet).add((Db.hash(tk, j, 3) - 0.5f) * 1.4f, 1 + Db.hash(tk, j, 4) * 1.5f, (Db.hash(tk, j, 5) - 0.5f) * 1.4f), tk * 7 + j,
                    0.03f, hot, k, 0.5f, 5, 1);
        }
        for (int i = 0; i < 14; i++) {
            float ph = fract(age * 0.03f + Db.hash(i, 6, 1));
            bb(new Vector3f(feet).add((Db.hash(i, 6, 2) - 0.5f) * 6, ph * 1.4f, (Db.hash(i, 6, 3) - 0.5f) * 6), 0.2f, i, FxDraw.SOLID, 0,
                    new float[] {0.4f, 0.36f, 0.32f}, k * (1 - ph), false);
        }
    }

    private static void telekinesis(Fx f, float age, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        Vector3f c = e != null ? rel(e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0)) : rel(f.pos);
        float a = smooth(age / 8) * Mth.clamp(f.a, 0, 1) * out;
        bb(c, 3, 0, FxDraw.SOFT, 0.15f, COLORS[Db.PURPLE], 0.45f * a, true);
        for (int i = 0; i < 8; i++) {
            dsk(new Vector3f(c).add(0, (i - 4) * 0.2f, 0), new Vector3f(0, 1, 0), 1.3f + 0.1f * Mth.sin(age * 0.2f + i), FxDraw.RING, 0.05f,
                    COLORS[Db.PINK], 0.4f * a, true);
        }
        bb(rel(f.dir), 0.5f, 0, FxDraw.SOFT, 0.6f, COLORS[Db.PURPLE], 0.6f * a, true);
    }

    private static void solar(Fx f, float a) {
        Vector3f head = rel(f.pos);
        float fd = 1 - a / 30;
        Vector3f fwd = new Vector3f((float) f.dir.x, (float) f.dir.y, (float) f.dir.z).normalize();
        Vector3f right = new Vector3f(fwd).cross(0, 1, 0);
        if (right.lengthSquared() < 1.0E-4f) {
            right.set(1, 0, 0);
        }
        right.normalize();
        Vector3f up = new Vector3f(right).cross(fwd).normalize();
        bb(head, 8 * fd + 2, 0, FxDraw.FLARE, 16 / 32f, WHITE, fd, true);
        bb(head, 14 * fd, 0, FxDraw.SOFT, 0.4f, COLORS[Db.GOLD_W], 0.8f * fd, true);
        for (int i = 0; i < 16; i++) {
            Vector3f s = sphere(31, i);
            Vector3f d = at(at(at(new Vector3f(), right, s.x), up, s.y), fwd, Math.abs(s.z) + 0.6f).normalize();
            rib(head, at(head, d, 6 + 6 * (1 - fd)), 0.3f, FxDraw.STREAK, 0.6f, WHITE, 0.8f * fd, true);
        }
    }

    private static void dizzy(Fx f, float age, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        if (e == null || FxAnchor.firstPersonSelf(f.id)) {
            return;
        }
        Vector3f hc = rel(e.getPosition(partial).add(0, e.getBbHeight() * 1.19, 0));
        for (int i = 0; i < 4; i++) {
            float th = age * 0.15f + i / 4f * Mth.TWO_PI;
            bb(new Vector3f(hc).add(Mth.cos(th) * 0.4f, 0.05f * Mth.sin(age * 0.3f + i), Mth.sin(th) * 0.4f), 0.16f, age * 0.1f, FxDraw.FLARE, 5 / 32f,
                    COLORS[Db.GOLD_W], 0.9f * out, true);
        }
    }

    private static void gale(Fx f, float age, float out) {
        Vector3f hm = rel(f.pos);
        Vector3f to = rel(f.dir);
        float aw = smooth(age / 8) * Mth.clamp(f.a, 0, 1) * out;
        float[] green = COLORS[Db.GREEN];
        bb(hm, 0.9f, 0, FxDraw.SOFT, 0.5f, green, 0.7f * aw, true);
        bb(hm, 0.35f, 0, FxDraw.SOFT, 0.9f, WHITE, 0.8f * aw, true);
        Vector3f d = new Vector3f(to).sub(hm);
        float lh = d.length();
        if (lh < 0.1f) {
            return;
        }
        d.div(lh);
        Vector3f u = new Vector3f(d).cross(0, 1, 0);
        if (u.lengthSquared() < 1.0E-4f) {
            u.set(1, 0, 0);
        }
        u.normalize();
        Vector3f v = new Vector3f(u).cross(d);
        for (int i = 0; i < 46; i++) {
            float ph = fract(age * (0.07f + 0.05f * Db.hash(i, 1, 3)) + Db.hash(i, 2, 3));
            float ang = Db.hash(i, 3, 3) * Mth.TWO_PI;
            float spread = (0.15f + 1.3f * ph) * (0.5f + 0.7f * Db.hash(i, 4, 3));
            float tw = age * 0.08f;
            Vector3f a0 = galeAt(hm, d, u, v, lh, ang + tw, spread, Math.max(0, ph - 0.12f));
            Vector3f a1 = galeAt(hm, d, u, v, lh, ang + tw, spread, ph);
            rib(a0, a1, 0.05f + 0.05f * Db.hash(i, 5, 3), FxDraw.STREAK, 0.9f, i % 4 != 0 ? green : WHITE, aw * 0.9f * Mth.sin(ph * Mth.PI), true);
        }
        // Stones flying along with it.
        for (int i = 0; i < 14; i++) {
            float ph = fract(age * 0.035f + Db.hash(i, 6, 3));
            Vector3f st = at(new Vector3f(hm).add(0, -1.1f, 0), d, 1 + Db.hash(i, 8, 3) * 3).add(u.x * (Db.hash(i, 7, 3) - 0.5f) * 3, 0,
                    u.z * (Db.hash(i, 7, 3) - 0.5f) * 3);
            Vector3f p = new Vector3f(st).lerp(to, ph * ph).add(0, Mth.sin(ph * Mth.PI) * 1.2f, 0);
            bb(p, 0.08f + 0.08f * Db.hash(i, 9, 3), age * 0.3f + i, FxDraw.SOLID, 0, new float[] {0.42f, 0.38f, 0.33f}, aw * Math.min(1, (1 - ph) * 4), false);
        }
    }

    private static Vector3f galeAt(Vector3f hm, Vector3f d, Vector3f u, Vector3f v, float lh, float ang, float spread, float s) {
        return at(at(at(hm, d, s * lh), u, Mth.cos(ang) * spread * s * 1.2f), v, Mth.sin(ang) * spread * s * 1.2f);
    }

    private static void spiral(Fx f, float age, float out) {
        Vector3f c = rel(f.pos);
        float a = Mth.clamp(f.b, 0, 1) * out;
        float rr = f.a;
        Vector3f n = new Vector3f(c).negate().normalize();
        Vector3f u = new Vector3f(n).cross(0, 1, 0);
        if (u.lengthSquared() < 1.0E-4f) {
            u.set(1, 0, 0);
        }
        u.normalize();
        Vector3f v = new Vector3f(u).cross(n);
        warp(c, rr * 1.6f, 1.1f * a, 1, age * 0.1f * 20, n);
        bb(c, rr * 0.9f, 0, FxDraw.SOFT, 0.9f, WHITE, 0.7f * a, true);
        bb(c, rr * 2.4f, 0, FxDraw.SOFT, 0.15f, COLORS[Db.GREEN], 0.35f * a, true);
        for (int arm = 0; arm < 4; arm++) {
            Vector3f last = null;
            for (int i = 0; i <= 22; i++) {
                float s = i / 22f;
                float th = arm / 4f * Mth.TWO_PI + s * 5.2f - age * 0.35f;
                float r = rr * (0.12f + 0.95f * s);
                Vector3f p = at(at(c, u, Mth.cos(th) * r), v, Mth.sin(th) * r);
                if (last != null) {
                    rib(last, p, 0.1f * (0.4f + s), FxDraw.STREAK, 0.7f, s < 0.3f ? WHITE : COLORS[Db.GREEN], a * (1 - s * 0.5f), true);
                }
                last = p;
            }
        }
    }

    private static void tornado(Fx f, float age, float out) {
        Vector3f center = rel(f.pos);
        float hgt = f.a;
        float rad = f.b;
        float a = smooth(age / 10) * out;
        for (int i = 0; i < 56; i++) {
            float u = i / 56f;
            float ang = age * 0.45f + u * 22;
            float r = rad * (0.35f + u * 0.8f);
            Vector3f p = new Vector3f(center).add(Mth.cos(ang) * r, u * hgt, Mth.sin(ang) * r);
            Vector3f q = new Vector3f(center).add(Mth.cos(ang - 0.6f) * r, u * hgt - 0.05f, Mth.sin(ang - 0.6f) * r);
            rib(q, p, 0.1f, FxDraw.STREAK, 0.6f, i % 5 != 0 ? COLORS[Db.GREEN] : WHITE, 0.75f * a, true);
        }
    }

    // The Mafuba's jar: white body with a red tag; the lid closes, it shakes harder and harder and cracks, then breaks
    // (b = 1) or stays shut and fades (b = 0, the target died sealed).
    private static final float[] JAR_WHITE = FxDraw.rgb(0xE8E2D0);
    private static final float[] JAR_RED = FxDraw.rgb(0xC02020);

    private static void jar(Fx f, double now) {
        float sinceSeal = f.sealAt < 0 ? -1 : (float) (now - f.sealAt);
        float sealTicks = f.a;
        boolean breaks = f.b > 0.5f;
        float t = (float) (now - f.born);
        if (sinceSeal >= 0 && breaks && sinceSeal >= sealTicks) {
            jarBreak(rel(f.pos), sinceSeal - sealTicks);
            return;
        }
        float fade = sinceSeal >= 0 && !breaks ? 1 - smooth((sinceSeal - sealTicks) / 30) : 1;
        if (fade <= 0) {
            return;
        }
        boolean sealed = sinceSeal >= 0;
        float shake = sealed ? 0.02f + 0.06f * smooth((sinceSeal - 60) / Math.max(1, sealTicks - 60)) : 0;
        Vector3f base = rel(f.pos).add(sealed ? Mth.sin(t * 1.6f) * shake : 0, 0, 0);
        float tilt = sealed ? Mth.sin(t * 1.3f) * shake * 2.5f : 0;
        if (!pass) {
            cylinder(base, 0.22f, 0.26f, 0, 0.42f, tilt, JAR_WHITE, fade);
            float lidY = sinceSeal < 0 ? 0.62f : Mth.lerp(smooth(sinceSeal / 4), 0.62f, 0.46f);
            cylinder(new Vector3f(base).add(0, lidY - 0.035f, 0), 0.24f, 0.24f, 0, 0.07f, tilt + (sinceSeal < 0 ? 0.5f : 0.5f * (1 - smooth(sinceSeal / 4))),
                    JAR_WHITE, fade);
            Vector3f tag = new Vector3f(base).add(0, 0.3f, 0);
            Vector3f toCam = new Vector3f(tag).negate();
            toCam.y = 0;
            if (toCam.lengthSquared() > 1.0E-4f) {
                toCam.normalize();
                Vector3f side = new Vector3f(toCam).cross(0, 1, 0).normalize(0.05f);
                Vector3f face = at(tag, toCam, 0.265f);
                FxDraw.quad(new Vector3f(face).sub(side).add(0, -0.15f, 0), new Vector3f(face).add(side).add(0, -0.15f, 0),
                        new Vector3f(face).add(side).add(0, 0.15f, 0), new Vector3f(face).sub(side).add(0, 0.15f, 0), FxDraw.SOLID, 0, JAR_RED, fade, false);
            }
            if (sealed) {
                // Cracks running over it, more and more.
                float cr = smooth((sinceSeal - 100) / Math.max(1, sealTicks - 100));
                for (int i = 0; i < 4; i++) {
                    if (cr * 4 < i) {
                        break;
                    }
                    float a0 = Db.hash(i, 1, 21) * Mth.TWO_PI;
                    float y0 = 0.08f + 0.3f * Db.hash(i, 2, 21);
                    Vector3f p = new Vector3f(base).add(Mth.cos(a0) * 0.27f, y0, Mth.sin(a0) * 0.27f);
                    for (int j = 0; j < 3; j++) {
                        Vector3f q = new Vector3f(p).add((Db.hash(i, j, 22) - 0.5f) * 0.12f, 0.06f + 0.04f * Db.hash(i, j, 23), (Db.hash(i, j, 24) - 0.5f) * 0.05f);
                        FxDraw.ribbon(p, q, 0.012f, FxDraw.SOLID, 0, new float[] {0.1f, 0.08f, 0.08f}, fade, false);
                        p = q;
                    }
                }
            }
        } else if (sealed) {
            Vector3f seal = new Vector3f(base).add(0, 0.3f, 0);
            Vector3f toCam = new Vector3f(seal).negate().normalize(0.28f);
            bb(seal.add(toCam), 0.4f, 0, FxDraw.SOFT, 0.5f, COLORS[Db.RED], (0.5f + 0.3f * Mth.sin(t * 0.3f)) * fade, true);
        }
    }

    private static void jarBreak(Vector3f base, float a) {
        for (int i = 0; i < 16; i++) {
            Vector3f d = sphere(33, i);
            d.y = Math.abs(d.y) + 0.3f;
            d.normalize();
            Vector3f p = at(new Vector3f(base).add(0, 0.25f, 0), d, 0.2f + a * 0.12f);
            p.y -= a * a * 0.004f;
            if (p.y > base.y + 0.03f) {
                bb(p, 0.1f + 0.06f * Db.hash(i, 3, 33), a * 0.2f + i, FxDraw.SOLID, 0, i == 3 ? new float[] {0.75f, 0.12f, 0.12f} : new float[] {0.91f, 0.89f, 0.82f},
                        1, false);
            }
        }
        if (a < 10) {
            bb(new Vector3f(base).add(0, 0.4f, 0), 2.5f * (1 - a / 10), 0, FxDraw.SOFT, 0.6f, COLORS[Db.GREEN], 1 - a / 10, true);
            dsk(new Vector3f(base).add(0, 0.05f, 0), new Vector3f(0, 1, 0), 0.4f + a * 0.3f, FxDraw.RING, 0.08f, COLORS[Db.GREEN], 1 - a / 10, true);
        }
        nrDust(base, a, 61, 10, 0.8f, new float[] {0.6f, 0.55f, 0.5f}, 30);
    }

    // A lit cylinder (12 sides) standing on base from y0 to y1, tipped by tilt radians round x.
    static void cylinder(Vector3f base, float rTop, float rBottom, float y0, float y1, float tilt, float[] col, float op) {
        int n = 12;
        float cos = Mth.cos(tilt);
        float sin = Mth.sin(tilt);
        for (int i = 0; i < n; i++) {
            float a0 = i / (float) n * Mth.TWO_PI;
            float a1 = (i + 1) / (float) n * Mth.TWO_PI;
            float light = 0.62f + 0.38f * Math.max(0, Mth.cos((a0 + a1) / 2 - 0.8f));
            float[] c = {col[0] * light, col[1] * light, col[2] * light};
            FxDraw.quad(tilted(base, Mth.cos(a0) * rBottom, y0, Mth.sin(a0) * rBottom, cos, sin), tilted(base, Mth.cos(a1) * rBottom, y0, Mth.sin(a1) * rBottom, cos, sin),
                    tilted(base, Mth.cos(a1) * rTop, y1, Mth.sin(a1) * rTop, cos, sin), tilted(base, Mth.cos(a0) * rTop, y1, Mth.sin(a0) * rTop, cos, sin),
                    FxDraw.SOLID, 0, c, op, false);
        }
        for (int i = 0; i < n; i += 2) {
            float a0 = i / (float) n * Mth.TWO_PI;
            float a1 = (i + 1) / (float) n * Mth.TWO_PI;
            float a2 = (i + 2) / (float) n * Mth.TWO_PI;
            FxDraw.quad(tilted(base, 0, y1, 0, cos, sin), tilted(base, Mth.cos(a0) * rTop, y1, Mth.sin(a0) * rTop, cos, sin),
                    tilted(base, Mth.cos(a1) * rTop, y1, Mth.sin(a1) * rTop, cos, sin), tilted(base, Mth.cos(a2) * rTop, y1, Mth.sin(a2) * rTop, cos, sin),
                    FxDraw.SOLID, 0, col, op, false);
        }
    }

    private static Vector3f tilted(Vector3f base, float x, float y, float z, float cos, float sin) {
        return new Vector3f(base).add(x, y * cos - z * sin, y * sin + z * cos);
    }

    private static final float[] PICCOLO_SKIN = FxDraw.rgb(0x62B04A);
    private static final float[] PICCOLO_CUFF = FxDraw.rgb(0xA0402A);

    // Piccolo's arm stretched out to pos: a long green arm (lit box) with the red cuff at the shoulder.
    private static void arm(Fx f, float partial, float out) {
        if (pass) {
            return;
        }
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        Vec3 look = e.getViewVector(partial);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 shoulder = e.getPosition(partial).add(0, e.getBbHeight() * 0.75, 0).add(right.scale(0.33));
        Vector3f a = rel(shoulder);
        Vector3f b = rel(f.pos);
        box(a, at(a, new Vector3f(b).sub(a).normalize(), 0.25f), 0.27f, PICCOLO_CUFF, out);
        box(at(a, new Vector3f(b).sub(a).normalize(), 0.25f), b, 0.24f, PICCOLO_SKIN, out);
    }

    // A lit square bar from a to b, w thick.
    static void box(Vector3f a, Vector3f b, float w, float[] col, float op) {
        Vector3f d = new Vector3f(b).sub(a);
        if (d.lengthSquared() < 1.0E-6f) {
            return;
        }
        d.normalize();
        Vector3f u = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize(w / 2);
        Vector3f v = new Vector3f(d).cross(u).normalize(w / 2);
        Vector3f[][] sides = {{u, v}, {v, new Vector3f(u).negate()}, {new Vector3f(u).negate(), new Vector3f(v).negate()}, {new Vector3f(v).negate(), u}};
        for (Vector3f[] s : sides) {
            Vector3f n = new Vector3f(s[0]).normalize();
            float light = 0.6f + 0.4f * Math.max(0, n.y * 0.8f + 0.3f);
            float[] c = {col[0] * light, col[1] * light, col[2] * light};
            Vector3f p0 = new Vector3f(a).add(s[0]).sub(s[1]);
            Vector3f p1 = new Vector3f(b).add(s[0]).sub(s[1]);
            Vector3f p2 = new Vector3f(b).add(s[0]).add(s[1]);
            Vector3f p3 = new Vector3f(a).add(s[0]).add(s[1]);
            FxDraw.quad(p0, p1, p2, p3, FxDraw.SOLID, 0, c, op, false);
        }
        Vector3f cap = new Vector3f(b);
        FxDraw.quad(new Vector3f(cap).add(u).add(v), new Vector3f(cap).sub(u).add(v), new Vector3f(cap).sub(u).sub(v), new Vector3f(cap).add(u).sub(v),
                FxDraw.SOLID, 0, col, op, false);
    }

    private static void smokeCover(Fx f, float age, float partial, float out) {
        Entity e = FxAnchor.entity(f.id);
        Vector3f tgt = e != null ? rel(e.getPosition(partial).add(0, 1.1, 0)) : rel(f.pos);
        float cover = Mth.clamp(f.a, 0, 1) * out;
        for (int i = 0; i < 16; i++) {
            float a = Db.hash(i, 8, 1) * Mth.TWO_PI;
            float rr = 0.4f + Db.hash(i, 8, 2) * 1.6f;
            bb(new Vector3f(tgt).add(Mth.cos(a) * rr, -0.6f + Db.hash(i, 8, 3) * 2.2f + age * 0.004f, Mth.sin(a) * rr * 0.6f), 1.4f + Db.hash(i, 8, 4),
                    i + age * 0.01f, FxDraw.SMOKE, 0.35f, SMOKE_GREY, 0.85f * cover, false);
        }
    }

    private static void streak(Fx f, float age) {
        float k = 1 - age / Math.max(1, f.c);
        rib(rel(f.pos), rel(f.pos.add(f.dir)), f.a * k, FxDraw.STREAK, 0.6f, color(f.b), 0.85f * k, true);
    }

    // Kakusandan: the ball bursts high up into ~30 bolts that fan out and rain down one by one on the area.
    private static void rain(Fx f, float a) {
        Vector3f top = rel(f.pos);
        Vec3 mid = f.dir;
        int seed = (int) f.c;
        if (a < 10) {
            bb(top, 3 * (1 - a / 10) + 1, 0, FxDraw.FLARE, 8 / 32f, COLORS[Db.GOLD_W], 1 - a / 10, true);
        }
        float[] gold = COLORS[Db.GOLD];
        for (int i = 0; i < 30; i++) {
            float t0 = 14 + Db.rainTime(seed, i, f.b);
            Vector3f g = rel(mid.add(Db.rainSpot(seed, i, f.a))).add(0, 0.05f, 0);
            Vector3f fanned = new Vector3f(top).add((Db.hash(i, 10 + seed, 4) - 0.5f) * 3, 1 + Db.hash(i, 10 + seed, 5), (Db.hash(i, 10 + seed, 6) - 0.5f) * 3);
            if (a < t0) {
                kiBall(new Vector3f(top).lerp(fanned, smooth(a / t0)), 0.1f, gold, 0.8f, a);
            } else if (a < t0 + 4) {
                float k = (a - t0) / 4;
                rib(new Vector3f(fanned).lerp(g, Math.max(0, k - 0.4f)), new Vector3f(fanned).lerp(g, k), 0.12f, FxDraw.BEAM, 0.8f, gold, 1, true);
            } else if (a < t0 + 20) {
                float b = (a - t0 - 4) / 16;
                bb(new Vector3f(g).add(0, 0.3f, 0), 1 + b, i, FxDraw.FIRE, 0.25f + b * 0.4f, gold, 0.8f * (1 - b), true);
                bb(new Vector3f(g).add(0, 0.5f + b, 0), 1 + b, i, FxDraw.SMOKE, 0.35f, SMOKE_GREY, 0.5f * (1 - b), false);
            }
        }
    }

    // One of Vegeta's ki shots, with its little burst where it lands.
    private static void shot(Fx f, float age) {
        Vector3f from = rel(f.pos);
        Vector3f to = rel(f.pos.add(f.dir));
        float[] col = color(f.b);
        float k = age / Math.max(1, f.a);
        if (k < 1) {
            Vector3f p = new Vector3f(from).lerp(to, k);
            kiBall(p, 0.12f, col, 1, age);
            rib(new Vector3f(from).lerp(to, Math.max(0, k - 0.2f)), p, 0.12f, FxDraw.STREAK, 0.8f, col, 0.7f, true);
        } else if (k < 2.2f) {
            float b = (k - 1) / 1.2f;
            bb(to, 1.4f * (0.4f + b), f.id, FxDraw.FIRE, 0.25f + b * 0.4f, col, 0.8f * (1 - b), true);
            bb(to, 0.6f, 0, FxDraw.SOFT, 0.9f, pale(col, 0.8f), 1 - b, true);
        }
    }

    private static void vanish(Fx f, float a) {
        Vector3f p = rel(f.pos);
        float k = 1 - a / 8;
        if (k <= 0) {
            return;
        }
        rib(new Vector3f(p).add(0, -1, 0), new Vector3f(p).add(0, 1.4f, 0), 0.5f * k, FxDraw.BEAM, 0.8f, COLORS[Db.KAME_W], k, true);
        warp(p, 1.2f, 0.6f * k, 0, a, null);
    }

    // Max Power: dark cracks run out on the ground and bits of the shirt fly off.
    private static void flex(Fx f, float a) {
        Vector3f feet = rel(f.pos);
        for (int i = 0; i < 8; i++) {
            float ang = i / 8f * Mth.TWO_PI;
            float len = smooth(a / 10) * (1 + Db.hash(i, 11, 1) * 1.6f);
            rib(new Vector3f(feet).add(Mth.cos(ang) * 0.3f, 0.03f, Mth.sin(ang) * 0.3f), new Vector3f(feet).add(Mth.cos(ang) * len, 0.03f, Mth.sin(ang) * len),
                    0.06f, FxDraw.BEAM, 0.9f, new float[] {0.07f, 0.06f, 0.05f}, 0.85f * (1 - smooth((a - 30) / 12)), false);
        }
        if (a < 16) {
            for (int i = 0; i < 10; i++) {
                Vector3f d = sphere(80, i);
                bb(at(new Vector3f(feet).add(0, 1.2f, 0), d, 0.5f + a * 0.1f), 0.18f, a + i, FxDraw.SOLID, 0, FxDraw.rgb(0xFF9A3A), 1 - a / 16, false);
            }
        }
    }

    private static void scar(Fx f, float a) {
        Vector3f start = rel(f.pos);
        Vector3f d = new Vector3f((float) f.dir.x, 0, (float) f.dir.z);
        float full = d.length();
        if (full < 0.1f) {
            return;
        }
        d.div(full);
        float len = Math.min(full, a * 7);
        float op = 0.8f * smooth(a / 4) * (1 - smooth((a - (f.b - 30)) / 30));
        Vector3f side = new Vector3f(d).cross(0, 1, 0).normalize(f.a / 2);
        Vector3f end = at(start, d, len);
        if (!pass) {
            FxDraw.quad(new Vector3f(start).sub(side).add(0, 0.03f, 0), new Vector3f(end).sub(side).add(0, 0.03f, 0), new Vector3f(end).add(side).add(0, 0.03f, 0),
                    new Vector3f(start).add(side).add(0, 0.03f, 0), FxDraw.BEAM, 0.7f, DIRT, op, false);
        }
        for (int i = 0; i < 6; i++) {
            Vector3f q = at(start, d, 3 + i * Math.max(1, full / 8));
            nrDust(at(q, side, i % 2 != 0 ? 1.8f : -1.8f), a - i, 40 + i, 6, 1.2f, new float[] {0.5f, 0.45f, 0.4f}, 50);
        }
    }

    // ---------- screen effects (the Visualizador's setup lists) ----------

    // Impact frame modes: 0 white, 1 color, 2 inverted, 3 manga.
    static void screen(int preset, Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double dist = mc.player.position().distanceTo(pos);
        float near = (float) Mth.clamp(1 - (dist - 20) / 40, 0, 1);
        if (near <= 0) {
            return;
        }
        switch (preset) {
            case 0 -> {
                ScreenFx.flash(0xD8F0FF, 0.5f * near, 8);
                ScreenFx.impactFrame(new int[] {0, 1, 2}, 1, 0x3A8AFF, pos, 0);
                ScreenShake.start(0.9f * near, 44);
            }
            // Goku's punch behind the target: the manga frame, negative then positive.
            case 1 -> {
                ScreenFx.impactFrame(new int[] {4, 3}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(0.7f * near, 10);
            }
            // Kaioken lit: a red burst and the shake (no impact frame on an invocation).
            case 2 -> {
                ScreenFx.flash(0xFF4030, 0.45f * near, 10);
                ScreenShake.start(0.5f * near, 8);
            }
            case 3 -> {
                ScreenFx.impactFrame(new int[] {1, 2, 1}, 1, 0xFF2A1A, pos, 0);
                ScreenShake.start(0.6f * near, 8);
            }
            case 4 -> ScreenShake.start(0.3f * near, 4);
            case 5 -> {
                ScreenFx.flash(0xE8F6FF, near, 16);
                ScreenFx.impactFrame(new int[] {0, 0, 2, 1, 0}, 2, 0x3A8AFF, pos, 0);
                ScreenShake.start(2.2f * near, 50);
            }
            case 6 -> {
                ScreenFx.flash(0xE0F0FF, 0.8f * near, 10);
                ScreenFx.impactFrame(new int[] {2, 3, 1}, 1, 0x60B0FF, pos, 0);
                ScreenShake.start(1.6f * near, 32);
            }
            case 7 -> {
                ScreenFx.flash(0xFFF6C8, 0.8f * near, 12);
                ScreenFx.impactFrame(new int[] {0, 1, 2, 1, 0}, 2, 0xFFD84A, pos, 0);
                ScreenShake.start(1.8f * near, 60);
            }
            case 8 -> {
                ScreenFx.impactFrame(new int[] {3, 0}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(0.4f * near, 6);
            }
            case 9 -> {
                ScreenFx.impactFrame(new int[] {2, 1}, 1, 0xC050FF, pos, 0);
                ScreenShake.start(0.25f * near, 4);
            }
            case 10 -> {
                ScreenFx.impactFrame(new int[] {3, 2}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(1.1f * near, 16);
            }
            case 11 -> {
                ScreenFx.flash(0xFFB080, near, 16);
                ScreenFx.flash(0xFF6A3A, 0.8f * near, 26);
                ScreenFx.impactFrame(new int[] {0, 1, 2, 1}, 2, 0xFF4A1A, pos, 0);
                ScreenShake.start(2.4f * near, 50);
            }
            case 12 -> {
                ScreenFx.impactFrame(new int[] {2, 1}, 1, 0xFF60D0, pos, 0);
                ScreenShake.start(0.7f * near, 8);
            }
            case 13 -> {
                ScreenFx.impactFrame(new int[] {2, 1, 3}, 1, 0xFF60D0, pos, 0);
                ScreenShake.start(0.9f * near, 12);
            }
            case 14 -> {
                ScreenFx.impactFrame(new int[] {0, 1, 2}, 1, 0xFFD84A, pos, 0);
                ScreenShake.start(0.8f * near, 14);
            }
            case 15 -> {
                ScreenFx.impactFrame(new int[] {3, 2}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(0.7f * near, 10);
            }
            case 16 -> {
                ScreenFx.flash(0xFFF6C8, 0.6f * near, 10);
                ScreenFx.impactFrame(new int[] {0, 1, 2}, 1, 0xFFD84A, pos, 0);
                ScreenShake.start(1.2f * near, 24);
            }
            case 17 -> {
                ScreenFx.impactFrame(new int[] {0, 3}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(0.5f * near, 6);
            }
            case 18 -> {
                ScreenFx.flash(0xD8F0FF, 0.5f * near, 8);
                ScreenShake.start(1.3f * near, 44);
            }
            case 19 -> ScreenFx.flash(0xFFFBEA, 0.9f * near, 4);
            case 20 -> {
                ScreenFx.impactFrame(new int[] {0, 3, 2}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(near, 12);
            }
            // Naruto: Sage Mode switching on.
            case 21 -> {
                ScreenFx.flash(0xFFCF80, 0.2f * near, 8);
                ScreenShake.start(0.4f * near, 10);
            }
            // Shikamaru's paper bomb.
            case 22 -> {
                ScreenFx.flash(0xFFF0C0, 0.7f * near, 8);
                ScreenFx.impactFrame(new int[] {0, 3, 1}, 1, 0xFF7A12, pos, 0);
                ScreenShake.start(1.8f * near, 26);
            }
            // Kakashi's sight blurred after the Kamui (sent to him only).
            case 23 -> {
                ScreenFx.vignette(0x101018, 0.55f, 40);
                ScreenFx.radialBlur(mc.player.getEyePosition().add(mc.player.getLookAngle().scale(3)), 0.7f, 40);
            }
            // ----- Jujutsu (impact modes: 0 white, 1 color, 2 inverted, 3 manga, 4 manga on black) -----
            // Divergent Fist: the first, dry impact.
            case 24 -> {
                ScreenFx.flash(0xFFFFFF, 0.35f * near, 3);
                ScreenShake.start(0.35f * near, 4);
            }
            // Divergent Fist: the second impact going through.
            case 25 -> {
                ScreenFx.impactFrame(new int[] {4, 1, 3}, 1, 0x2CC8F2, pos, 0);
                ScreenShake.start(1.6f * near, 20);
                ScreenFx.radialBlur(pos, 0.9f * near, 10);
                ScreenFx.fovKick(9 * near, 12);
            }
            // Black Flash (Itadori): three of the Black Flash's own frames.
            case 26 -> {
                ScreenFx.impactFrame(new int[] {6, 7, 9}, 1, 0xFF0018, pos, 0);
                ScreenShake.start(1.1f * near, 14);
            }
            // Manji Kick.
            case 27 -> {
                ScreenFx.impactFrame(new int[] {3, 2}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(0.8f * near, 10);
            }
            // Itadori's dash: the knee.
            case 28 -> {
                ScreenFx.impactFrame(new int[] {0, 3, 2}, 1, 0x2CC8F2, pos, 0);
                ScreenShake.start(1.1f * near, 14);
            }
            // Piercing Blood.
            case 29 -> {
                ScreenFx.impactFrame(new int[] {1, 2}, 1, 0xA0101A, pos, 0);
                ScreenShake.start(0.6f * near, 8);
            }
            // Supernova bursting.
            case 30 -> {
                ScreenFx.impactFrame(new int[] {1, 2, 1}, 1, 0xA0101A, pos, 0);
                ScreenShake.start(0.9f * near, 14);
            }
            // Red Scale's hook.
            case 31 -> {
                ScreenFx.impactFrame(new int[] {1, 2, 1}, 1, 0xC0101A, pos, 0);
                ScreenShake.start(1f * near, 12);
            }
            // Nanami's critical on the 7:3 point.
            case 32 -> {
                ScreenFx.impactFrame(new int[] {0, 3, 2}, 1, 0xFFB43A, pos, 0);
                ScreenShake.start(1.2f * near, 14);
            }
            // Nanami's fist in the ground.
            case 33 -> {
                ScreenFx.impactFrame(new int[] {0, 3, 2}, 1, 0xFFB43A, pos, 0);
                ScreenShake.start(2f * near, 30);
                ScreenFx.fovKick(9 * near, 14);
                ScreenFx.radialBlur(pos, 0.9f * near, 12);
            }
            // Overtime's first blow.
            case 34 -> {
                ScreenFx.impactFrame(new int[] {0, 1, 2, 3}, 1, 0xFFB43A, pos, 0);
                ScreenShake.start(1.5f * near, 20);
            }
            // Nobara's Hairpin.
            case 35 -> {
                ScreenFx.impactFrame(new int[] {1, 2, 1}, 1, 0x3A8AFF, pos, 0);
                ScreenShake.start(1f * near, 14);
            }
            // Resonance.
            case 36 -> {
                ScreenFx.impactFrame(new int[] {2, 1, 2}, 1, 0x3A8AFF, pos, 0);
                ScreenShake.start(1f * near, 14);
            }
            // The nail hammered in point blank.
            case 37 -> {
                ScreenFx.impactFrame(new int[] {3, 1}, 1, 0x3A8AFF, pos, 0);
                ScreenShake.start(0.8f * near, 10);
            }
            // Todo's clap.
            case 38 -> ScreenFx.impactFrame(new int[] {0}, 1, 0xFFFFFF, pos, 0);
            // Todo's punch.
            case 39 -> {
                ScreenFx.impactFrame(new int[] {3, 2}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(1f * near, 12);
            }
            // Todo coming down from the sky.
            case 40 -> {
                ScreenFx.impactFrame(new int[] {0, 3, 2}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(1.4f * near, 18);
            }
            // Inumaki: "Stop."
            case 41 -> ScreenFx.impactFrame(new int[] {3}, 2, 0xA070FF, pos, 0);
            // Inumaki: "Get crushed!"
            case 42 -> {
                ScreenFx.impactFrame(new int[] {2, 3}, 1, 0xA070FF, pos, 0);
                ScreenShake.start(1.3f * near, 16);
            }
            // Miwa's draw.
            case 43 -> {
                ScreenFx.impactFrame(new int[] {0}, 1, 0x8FD0FF, pos, 0);
                ScreenShake.start(0.4f * near, 4);
            }
            // Miwa's Line Cut and Simple Domain cutting.
            case 44 -> {
                ScreenFx.impactFrame(new int[] {0, 2}, 1, 0x8FD0FF, pos, 0);
                ScreenShake.start(0.6f * near, 8);
            }
            // Realista+Anime: a ki beam let go (no impact frame on the throw, only light and the shake).
            case 45 -> {
                ScreenFx.flash(0xD8F0FF, 0.35f * near, 6);
                ScreenShake.start(0.7f * near, 30);
            }
            // Realista+Anime: a ki blast landing, the manga impact frame in black and white, negative then positive.
            case 46 -> {
                ScreenFx.impactFrame(new int[] {4, 3}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(0.9f * near, 14);
            }
            // Realista+Anime: the Genki Dama landing, three manga frames (negative, positive, negative).
            case 47 -> {
                ScreenFx.flash(0xE8F6FF, 0.6f * near, 12);
                ScreenFx.impactFrame(new int[] {4, 3, 4}, 1, 0xFFFFFF, pos, 0);
                ScreenShake.start(2.2f * near, 50);
            }
            default -> {
            }
        }
    }

    // ---------- small helpers ----------

    private static float[] color(float c) {
        int i = (int) c;
        return i >= 0 && i < COLORS.length ? COLORS[i] : WHITE;
    }

    private static float groundY(Fx f) {
        return (float) (f.dir.x - cam.y);
    }

    static Vector3f rel(Vec3 world) {
        return new Vector3f((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z));
    }

    static Vector3f at(Vector3f p, Vector3f d, float k) {
        return new Vector3f(p).add(d.x * k, d.y * k, d.z * k);
    }

    static Vector3f sphere(int seed, int k) {
        Vec3 d = Db.sphereDir(seed, k);
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }

    static Vector3f rotY(Vector3f v, float a) {
        // THREE's applyAxisAngle(up, a): x' = x cos + z sin, z' = -x sin + z cos.
        float c = Mth.cos(a);
        float s = Mth.sin(a);
        return new Vector3f(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    static float easeOut(float t) {
        float u = 1 - Mth.clamp(t, 0, 1);
        return 1 - u * u * u;
    }
}
