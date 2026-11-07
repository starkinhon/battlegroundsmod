package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.power.powers.sub.Rukia;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// The SubAlmas' effects (power/powers/sub), drawn with shader shapes only, as in the Visualizador's Bleach and Sukuna
// tabs: Senbonzakura's petals (shape 25), the Getsuga's crescent (26), Sukuna's slits (27), Rukia's ice shell (28) and
// Urahara's shield (29), plus the shared glow, fire and smoke shapes.
// Glow goes in render (into the bloom); the dark parts (slits, the Kuroi Getsuga, the shrine) in overlay, after the glow.
public final class SubFx {
    static final int PETAL = 25;
    static final int CRESCENT = 26;
    static final int SLIT = 27;
    static final int ICE = 28;
    static final int BARRIER = 29;
    static final int SHARD = 14;

    private static final float[] PINK = FxDraw.rgb(0xF4A6C6);
    private static final float[] PINK_DEEP = FxDraw.rgb(0xE0679A);
    private static final float[] ICE_BLUE = FxDraw.rgb(0xBFE8FF);
    private static final float[] GT_BLUE = FxDraw.rgb(0x4FA8FF);
    private static final float[] GT_ICE = FxDraw.rgb(0xDDF4FF);
    private static final float[] RED = FxDraw.rgb(0xFF1A12);
    private static final float[] CRIMSON = FxDraw.rgb(0xD0213A);
    private static final float[] GOLD = FxDraw.rgb(0xFFC94A);
    private static final float[] AMBER = FxDraw.rgb(0xFF9A1F);
    private static final float[] FIRE = FxDraw.rgb(0xFF7A1A);
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] DARK = {0.01f, 0.005f, 0.008f};
    private static final float[] SMOKE_GREY = {0.12f, 0.1f, 0.1f};
    private static final float[] MIST = {0.9f, 0.96f, 1f};
    private static final float[] PALE = {0.84f, 0.95f, 1f};
    private static final float[] SHRINE_DARK = {0.08f, 0.02f, 0.025f};
    private static final float[] SHRINE_BONE = {0.75f, 0.68f, 0.6f};

    // One effect: one-shot kinds play out from born; tracked kinds (sent every tick, or with a duration) are updated.
    private static final class Fx {
        final int kind;
        final int id;
        Vec3 pos;
        Vec3 prev;
        Vec3 dir;
        float a;
        float b;
        float c;
        final double born;
        double updated;
        double endAt = -1;
        double hitAt = -100;
        double crushAt = -1;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();

        Fx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, double now) {
            this.kind = kind;
            this.id = id;
            this.pos = pos;
            this.prev = pos;
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

    private SubFx() {
    }

    private static long key(int kind, int id) {
        return ((long) kind << 32) | (id & 0xFFFFFFFFL);
    }

    public static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        switch (kind) {
            case SubNet.PETALS, SubNet.GOKEI, SubNet.GETSUGA, SubNet.CHARGE, SubNet.NET, SubNet.FUGA -> {
                Fx f = TRACKED.get(key(kind, id));
                if (f == null || f.endAt >= 0) {
                    f = new Fx(kind, id, pos, dir, a, b, c, now);
                    TRACKED.put(key(kind, id), f);
                }
                f.prev = f.pos;
                f.pos = pos;
                f.dir = dir;
                f.a = a;
                f.b = b;
                f.updated = now;
                if (kind != SubNet.NET || b < 0.5f) {
                    f.trail.addLast(pos);
                }
                while (f.trail.size() > 40) {
                    f.trail.removeFirst();
                }
                if (kind == SubNet.GOKEI && c >= 1 && c < 2 && f.crushAt < 0) {
                    f.crushAt = now;
                }
                if (kind == SubNet.NET && b > 0.5f && f.crushAt < 0) {
                    f.crushAt = now;
                }
                if (kind == SubNet.NET && c >= 2 && f.hitAt < 0) {
                    f.hitAt = now;
                }
                if ((kind == SubNet.GOKEI && c >= 2) || (kind == SubNet.NET && c >= 1 && c < 2) || (kind != SubNet.GOKEI && kind != SubNet.NET && c >= 1)) {
                    f.endAt = now;
                    if (kind == SubNet.GETSUGA) {
                        SHOTS.add(new Fx(SubNet.RIP, 0, pos, dir, a * 0.8f, b, 0, now));
                    }
                }
            }
            case SubNet.FREEZE -> {
                if (c >= 1) {
                    IceShell.end(id, c >= 1.5f ? IceShell.THAW : IceShell.SHATTER);
                } else {
                    IceShell.freeze(id, pos, (int) b, (float) dir.x, (float) dir.y);
                }
            }
            case SubNet.ICE_ARMOR -> {
                if (c >= 1) {
                    IceShell.end(id, IceShell.THAW);
                } else {
                    IceShell.armor(id);
                }
            }
            case SubNet.BANKAI_BYAKUYA, SubNet.AURA, SubNet.SEAL, SubNet.CLEAVE_SCAN, SubNet.SHRINE -> {
                Fx f = TRACKED.get(key(kind, id));
                boolean stop = kind == SubNet.BANKAI_BYAKUYA || kind == SubNet.AURA ? c >= 1 : a <= 0;
                if (stop) {
                    if (f != null && f.endAt < 0) {
                        f.endAt = now;
                    }
                    return;
                }
                if (f == null || f.endAt >= 0) {
                    double born = kind == SubNet.BANKAI_BYAKUYA ? now - a : now;
                    f = new Fx(kind, id, pos, dir, a, b, c, born);
                    TRACKED.put(key(kind, id), f);
                }
                f.pos = pos;
                f.updated = now;
                if (kind == SubNet.BANKAI_BYAKUYA) {
                    f.b = b;
                } else {
                    f.a = (float) (now - f.born) + a;
                }
            }
            case SubNet.SHIELD -> {
                Fx f = TRACKED.get(key(kind, id));
                if (a <= 0) {
                    if (f != null && f.endAt < 0) {
                        f.endAt = now;
                    }
                    return;
                }
                f = new Fx(kind, id, pos, dir, a, b, c, now);
                TRACKED.put(key(kind, id), f);
            }
            case SubNet.SHIELD_HIT -> {
                for (Fx f : TRACKED.values()) {
                    if (f.kind == SubNet.SHIELD && f.endAt < 0 && f.pos.distanceToSqr(pos) < 16) {
                        f.hitAt = now;
                    }
                }
                SHOTS.add(new Fx(SubNet.IMPACT, 0, pos, dir, 1.6f, SubNet.COLOR_RED, 0, now));
            }
            default -> {
                SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && (kind == SubNet.EXPLOSION || kind == SubNet.WORLD_CUT) && mc.player.position().distanceTo(pos) < 40) {
                    ScreenShake.start(kind == SubNet.WORLD_CUT ? 1.2f : Math.min(1.2f, 0.3f + a * 0.12f), 14);
                }
            }
        }
    }

    public static boolean active() {
        return !SHOTS.isEmpty() || !TRACKED.isEmpty() || IceShell.active();
    }

    public static void clear() {
        SHOTS.clear();
        TRACKED.clear();
        IceShell.clear();
    }

    // ---------- the two passes ----------

    static void render(Vec3 camera, double now, float partial) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        prune(now);
        draw(camera, now, partial, true);
    }

    static void overlay(Vec3 camera, double now, float partial) {
        draw(camera, now, partial, false);
    }

    private static double life(Fx f) {
        return switch (f.kind) {
            case SubNet.SLASH -> f.c == SubNet.STYLE_SUKUNA ? 16 : 10;
            case SubNet.RIFT -> 80;
            case SubNet.RIP -> 30;
            case SubNet.IMPACT -> 14;
            case SubNet.TSUKISHIRO -> f.b + Rukia.TSUKI_HOLD + 44;
            case SubNet.HAKUREN -> 90;
            case SubNet.SHIRAFUNE -> 60;
            case SubNet.ICE_SPIKE -> f.b;
            case SubNet.FROST -> f.b;
            case SubNet.EXPLOSION -> 36;
            case SubNet.WEB -> 50;
            case SubNet.WORLD_CUT -> 50;
            case SubNet.BEAM -> 10;
            default -> 40;
        };
    }

    private static void prune(double now) {
        SHOTS.removeIf(f -> now - f.born > life(f));
        IceShell.prune(now);
        Iterator<Fx> it = TRACKED.values().iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            boolean streamed = switch (f.kind) {
                case SubNet.PETALS, SubNet.GOKEI, SubNet.GETSUGA, SubNet.CHARGE, SubNet.NET, SubNet.FUGA -> true;
                default -> false;
            };
            double fadeOut = f.kind == SubNet.GOKEI ? 24 : f.kind == SubNet.BANKAI_BYAKUYA ? 20 : 8;
            if (f.endAt < 0 && streamed && now - f.updated > 4) {
                f.endAt = f.updated;
            }
            if (f.endAt < 0 && !streamed && now - f.born > f.a + (f.kind == SubNet.BANKAI_BYAKUYA ? f.b - f.a : 0)) {
                f.endAt = now;
            }
            if (f.endAt >= 0 && now - f.endAt > fadeOut) {
                it.remove();
            }
        }
    }

    private static void draw(Vec3 camera, double now, float partial, boolean glow) {
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born) + partial;
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case SubNet.SLASH -> slash(f, camera, age, glow);
                case SubNet.RIFT -> rift(f, camera, age, glow);
                case SubNet.RIP -> rip(f, camera, age, glow);
                case SubNet.IMPACT -> impact(f, camera, age, glow);
                case SubNet.TSUKISHIRO -> tsukishiro(f, camera, age, glow);
                case SubNet.HAKUREN -> hakuren(f, camera, age, glow);
                case SubNet.SHIRAFUNE -> shirafune(f, camera, age, glow);
                case SubNet.FROST -> frost(f, camera, age, glow);
                case SubNet.ICE_SPIKE -> spike(f, camera, age, glow);
                case SubNet.EXPLOSION -> explosion(f, camera, age, glow);
                case SubNet.WEB -> web(f, camera, age, glow);
                case SubNet.WORLD_CUT -> worldCut(f, camera, age, glow);
                case SubNet.BEAM -> beam(f, camera, age, glow);
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born) + partial;
            float out = f.endAt < 0 ? 1 : 1 - smooth((float) (now - f.endAt + partial) / (f.kind == SubNet.GOKEI ? 24 : f.kind == SubNet.BANKAI_BYAKUYA ? 20 : 8));
            if (out <= 0) {
                continue;
            }
            switch (f.kind) {
                case SubNet.PETALS -> petals(f, camera, now, partial, out, glow);
                case SubNet.GOKEI -> gokei(f, camera, now, partial, out, glow);
                case SubNet.BANKAI_BYAKUYA -> bankaiByakuya(f, camera, age, partial, out, glow);
                case SubNet.GETSUGA -> getsuga(f, camera, now, partial, out, glow);
                case SubNet.CHARGE -> charge(f, camera, age, partial, out, glow);
                case SubNet.AURA -> aura(f, camera, age, partial, out, glow);
                case SubNet.SHIELD -> shield(f, camera, age, now, out, glow);
                case SubNet.NET -> net(f, camera, age, partial, out, glow);
                case SubNet.SEAL -> seal(f, camera, age, partial, out, glow);
                case SubNet.CLEAVE_SCAN -> cleaveScan(f, camera, age, partial, out, glow);
                case SubNet.FUGA -> fuga(f, camera, age, partial, out, glow);
                case SubNet.SHRINE -> shrine(f, camera, age, out, glow);
                default -> {
                }
            }
        }
        IceShell.draw(camera, now, partial, glow);
    }

    // ---------- Byakuya ----------

    // A petal-blade: pointed both ends, its long side along `along`, its flat side facing `facing` roughly.
    private static void petal(Vector3f c, Vector3f along, Vector3f facing, float size, float glint, float[] rgb, float op) {
        Vector3f u = new Vector3f(along).normalize(size * 0.5f);
        Vector3f w = new Vector3f(along).cross(facing);
        if (w.lengthSquared() < 1.0E-6f) {
            w.set(FxDraw.left());
        }
        w.normalize(size * 0.22f);
        FxDraw.quad(new Vector3f(c).sub(w).sub(u), new Vector3f(c).add(w).sub(u), new Vector3f(c).add(w).add(u), new Vector3f(c).sub(w).add(u),
                PETAL, glint, rgb, op, false);
    }

    private static Vec3 handOf(int id, float partial, Vec3 fallback) {
        Entity e = FxAnchor.entity(id);
        if (!(e instanceof LivingEntity le)) {
            return fallback;
        }
        Vec3 look = le.getViewVector(partial);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return le.getPosition(partial).add(0, le.getBbHeight() * 0.62, 0).add(right.scale(0.38)).add(look.scale(0.45));
    }

    // Point s (0..1) along a polyline, with its direction.
    private static Vec3[] along(List<Vec3> pts, float s) {
        int n = pts.size();
        if (n == 1) {
            return new Vec3[] {pts.get(0), new Vec3(0, 0, 1)};
        }
        float x = Mth.clamp(s, 0, 0.9999f) * (n - 1);
        int i = (int) x;
        float k = x - i;
        Vec3 a = pts.get(i);
        Vec3 b = pts.get(i + 1);
        Vec3 d = b.subtract(a);
        return new Vec3[] {a.add(d.scale(k)), d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize()};
    }

    // The river of petals from his hand to where it reached; they flow toward the head, swirling round the path.
    private static void petals(Fx f, Vec3 camera, double now, float partial, float out, boolean glow) {
        if (!glow) {
            return;
        }
        List<Vec3> path = new ArrayList<>();
        path.add(handOf(f.id, partial, f.trail.peekFirst()));
        int skip = Math.max(0, f.trail.size() - 16);
        int i0 = 0;
        for (Vec3 p : f.trail) {
            if (i0++ >= skip) {
                path.add(p);
            }
        }
        Vec3 head = f.prev.lerp(f.pos, Math.min(1, partial));
        path.set(path.size() - 1, head);
        float t = (float) now + partial;
        int count = (int) (60 + 260 * Mth.clamp(f.b, 0, 1));
        float width = Math.max(0.4f, f.a);
        for (int i = 0; i < count; i++) {
            float h1 = hash(i, 1);
            float h2 = hash(i, 2);
            float h3 = hash(i, 3);
            float s = fract(h1 + t * (0.035f + 0.02f * h2));
            Vec3[] at = along(path, s);
            Vec3 tan = at[1];
            Vec3 side = tan.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(tan).normalize();
            float ang = h3 * Mth.TWO_PI + t * (0.25f + 0.3f * h2);
            float r = width * (0.25f + 0.75f * (float) Math.sqrt(h2)) * (0.35f + 0.65f * smooth(s * 4));
            Vec3 p = at[0].add(side.scale(Mth.cos(ang) * r)).add(up.scale(Mth.sin(ang) * r));
            Vector3f c = rel(p, camera);
            Vector3f dirv = new Vector3f((float) tan.x, (float) tan.y, (float) tan.z).add(new Vector3f(Mth.cos(t * 0.4f + i), Mth.sin(t * 0.3f + i * 1.7f), Mth.sin(i)).mul(0.6f));
            float fadeEnds = smooth(s * 10) * smooth((1 - s) * 6);
            petal(c, dirv, c, 0.16f + 0.08f * h3, fract(t * 0.05f + h1), h2 < 0.3f ? PINK_DEEP : PINK, out * fadeEnds);
            if (i % 9 == 0) {
                FxDraw.billboard(c, 0.25f, 0, FxDraw.SOFT, 0.5f, WHITE, 0.25f * out * fadeEnds, true);
            }
        }
        FxDraw.billboard(rel(head, camera), width * 2.2f, 0, FxDraw.SOFT, 0.2f, PINK, 0.25f * out, true);
    }

    // Gokei: petals close into a sphere round the target, squeeze, and crush it from every side.
    private static void gokei(Fx f, Vec3 camera, double now, float partial, float out, boolean glow) {
        if (!glow) {
            return;
        }
        float t = (float) now + partial;
        float closed = Mth.clamp(f.b, 0, 1);
        float crush = f.crushAt < 0 ? 0 : Mth.clamp((float) (now - f.crushAt + partial) / 6, 0, 1);
        float scatter = f.crushAt < 0 ? 0 : Mth.clamp((float) (now - f.crushAt + partial - 6) / 18, 0, 1);
        float radius = f.a * (1.9f - 0.9f * smooth(closed)) * (1 - 0.7f * smooth(crush)) * (1 + 3.5f * scatter);
        Vector3f center = rel(f.pos, camera);
        int count = (int) (180 + 520 * closed);
        for (int i = 0; i < count; i++) {
            float h1 = hash(i, 11);
            float h2 = hash(i, 12);
            float h3 = hash(i, 13);
            float lat = (float) Math.acos(1 - 2 * h1) - Mth.HALF_PI;
            float lon = h2 * Mth.TWO_PI + t * (0.12f + 0.25f * h3) * (h3 < 0.5f ? 1 : -1);
            float r = radius * (0.92f + 0.16f * h3);
            Vector3f n = new Vector3f(Mth.cos(lat) * Mth.cos(lon), Mth.sin(lat), Mth.cos(lat) * Mth.sin(lon));
            Vector3f p = new Vector3f(center).add(new Vector3f(n).mul(r));
            Vector3f tan = new Vector3f(-Mth.sin(lon), 0.3f * Mth.sin(t + i), Mth.cos(lon));
            float vis = out * (0.25f + 0.75f * smooth(closed * 2 - h1)) * (1 - scatter);
            petal(p, tan, n, 0.2f + 0.1f * h3, fract(t * 0.04f + h2), h2 < 0.3f ? PINK_DEEP : PINK, vis);
        }
        FxDraw.billboard(center, radius * 2.4f, 0, FxDraw.SOFT, 0.1f, PINK, 0.22f * out * (1 - scatter), true);
        if (crush > 0 && scatter < 1) {
            FxDraw.billboard(center, f.a * 4 * (0.4f + crush), t * 0.1f, FxDraw.FLARE, 10 / 32f, WHITE, 0.9f * (1 - scatter), true);
            FxDraw.disk(center, new Vector3f(FxDraw.left()).cross(FxDraw.up()), f.a * (2 + 8 * scatter), FxDraw.RING, 0.1f, PINK, 0.8f * (1 - scatter), true);
        }
    }

    // Bankai: two rows of giant blades rise around him, come apart into petals and become the storm that follows him.
    private static void bankaiByakuya(Fx f, Vec3 camera, float age, float partial, float out, boolean glow) {
        if (!glow) {
            return;
        }
        Entity owner = FxAnchor.entity(f.id);
        Vec3 feet = owner != null ? owner.getPosition(partial) : f.pos;
        Vec3 face = new Vec3(f.dir.x, 0, f.dir.z);
        face = face.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : face.normalize();
        Vec3 side = face.cross(new Vec3(0, 1, 0)).normalize();
        float t = age;
        // The blades: 2 rows of 7 along his sides, rising one after another (ticks 6-40), coming apart (40-70).
        if (age < 74) {
            for (int row = -1; row <= 1; row += 2) {
                for (int k = 0; k < 7; k++) {
                    float start = 6 + k * 3.5f;
                    float rise = smooth((age - start) / 10);
                    if (rise <= 0) {
                        continue;
                    }
                    float undo = Mth.clamp((age - 40 - k * 2) / 18, 0, 1);
                    float height = 9 + 2 * hash(k, row + 5);
                    Vec3 base = f.pos.add(face.scale(-3 + k * 3.2)).add(side.scale(row * (5 + k * 0.8)));
                    Vector3f b = rel(base, camera);
                    float shown = height * rise;
                    float cut = height * undo;
                    if (shown - cut <= 0.2f) {
                        continue;
                    }
                    Vector3f lo = new Vector3f(b).add(0, -height + shown + cut, 0);
                    Vector3f hi = new Vector3f(b).add(0, shown, 0);
                    Vector3f mid = new Vector3f(lo).add(hi).mul(0.5f);
                    Vector3f up = new Vector3f(0, (hi.y - lo.y) * 0.5f, 0);
                    Vector3f w = new Vector3f((float) face.x, 0, (float) face.z).mul(0.7f);
                    FxDraw.quad(new Vector3f(mid).sub(w).sub(up), new Vector3f(mid).add(w).sub(up), new Vector3f(mid).add(w).add(up),
                            new Vector3f(mid).sub(w).add(up), PETAL, fract(t * 0.03f + k * 0.2f), PINK, 0.95f * out, false);
                    FxDraw.ribbon(lo, hi, 1.8f, FxDraw.BEAM, 0.1f, PINK, 0.25f * out, true);
                    if (undo > 0) {
                        FxDraw.ribbon(new Vector3f(lo).add(new Vector3f(w).mul(-1.3f)), new Vector3f(lo).add(new Vector3f(w).mul(1.3f)), 0.35f,
                                FxDraw.BEAM, 0.7f, PINK, 0.9f * out, true);
                    }
                }
            }
        }
        // The storm round him: petals pour in from the blades, then orbit for as long as the Bankai lasts.
        float form = smooth((age - 40) / 30);
        if (form <= 0) {
            return;
        }
        Vec3 center = feet.add(0, 1.2, 0);
        Vector3f c = rel(center, camera);
        int count = (int) (420 * form);
        boolean self = FxAnchor.firstPersonSelf(f.id);
        for (int i = 0; i < count; i++) {
            float h1 = hash(i, 21);
            float h2 = hash(i, 22);
            float h3 = hash(i, 23);
            float r = 2.2f + 4.2f * h1;
            float ang = h2 * Mth.TWO_PI + t * (0.05f + 0.08f / (0.4f + h1)) * (h3 < 0.15f ? -1 : 1);
            float y = (h3 - 0.35f) * 4.5f + 0.4f * Mth.sin(t * 0.05f + i);
            float gather = f.endAt >= 0 ? 1 - out : 0;
            r *= 1 - 0.9f * gather;
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * r, y * (1 - gather), Mth.sin(ang) * r);
            if (self && p.lengthSquared() < 1.5f) {
                continue;
            }
            Vector3f tan = new Vector3f(-Mth.sin(ang), 0.15f, Mth.cos(ang));
            petal(p, tan, p, 0.17f + 0.09f * h3, fract(t * 0.05f + h1), h2 < 0.3f ? PINK_DEEP : PINK, Math.min(1, out + 0.3f) * smooth(form * 2 - h1));
        }
        FxDraw.disk(rel(feet.add(0, 0.05, 0), camera), new Vector3f(0, 1, 0), 13, FxDraw.SOFT, 0.05f, PINK, 0.12f * form * out, true);
    }

    // ---------- shared ----------

    private static void slash(Fx f, Vec3 camera, float age, boolean glow) {
        float lifeT = (float) life(f);
        float k = age / lifeT;
        float open = k < 0.18f ? smooth(k / 0.18f) : 1 - smooth((k - 0.18f) / 0.82f);
        Vec3 d = f.dir.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : f.dir.normalize();
        Vector3f center = rel(f.pos, camera);
        Vector3f half = new Vector3f((float) d.x, (float) d.y, (float) d.z).mul(f.a);
        Vector3f a = new Vector3f(center).sub(half);
        Vector3f b = new Vector3f(center).add(half);
        int style = (int) f.c;
        if (style == SubNet.STYLE_SUKUNA) {
            float w = f.b * open;
            if (glow) {
                FxDraw.ribbon(a, b, Math.max(0.05f, w * 1.6f), SLIT, 0f, WHITE, Math.min(1, open * 1.4f), true);
                if (age < 3) {
                    Vector3f a2 = new Vector3f(center).sub(new Vector3f(half).mul(1.7f));
                    Vector3f b2 = new Vector3f(center).add(new Vector3f(half).mul(1.7f));
                    FxDraw.ribbon(a2, b2, 0.06f, FxDraw.STREAK, 1f, WHITE, 1 - age / 3, true);
                }
            } else if (w > 0.01f) {
                FxDraw.ribbon(a, b, w * 1.6f, SLIT, 1f, WHITE, Math.min(1, open * 1.5f), false);
            }
            return;
        }
        if (!glow) {
            return;
        }
        float[] col = style == SubNet.STYLE_PINK ? PINK : style == SubNet.STYLE_CRIMSON ? CRIMSON : style == SubNet.STYLE_GOLD ? GOLD : GT_BLUE;
        float fade = 1 - smooth(k);
        FxDraw.ribbon(a, b, f.b * 2.2f, FxDraw.BEAM, 0.05f, col, 0.5f * fade, true);
        FxDraw.ribbon(a, b, f.b * 0.7f, FxDraw.STREAK, 1f, WHITE, 0.95f * fade, true);
    }

    private static void impact(Fx f, Vec3 camera, float age, boolean glow) {
        if (!glow) {
            return;
        }
        float k = age / 14;
        float[] col = color((int) f.b);
        Vector3f c = rel(f.pos, camera);
        float fade = 1 - smooth(k);
        FxDraw.billboard(c, f.a * (0.6f + 1.2f * smooth(k * 2)), age * 0.05f, FxDraw.FLARE, 8 / 32f, col, 0.95f * fade, true);
        FxDraw.billboard(c, f.a * 1.6f, 0, FxDraw.SOFT, 0.6f, WHITE, 0.7f * (1 - smooth(k * 3)), true);
        FxDraw.billboard(c, f.a * (0.5f + 2.5f * smooth(k)), 0, FxDraw.RING, 0.12f, col, 0.7f * fade, true);
    }

    private static void beam(Fx f, Vec3 camera, float age, boolean glow) {
        if (!glow) {
            return;
        }
        float fade = 1 - smooth(age / 10);
        float[] col = color((int) f.b);
        Vector3f a = rel(f.pos, camera);
        Vector3f b = rel(f.pos.add(f.dir), camera);
        FxDraw.ribbon(a, b, f.a * 2.5f, FxDraw.BEAM, 0.1f, col, 0.5f * fade, true);
        FxDraw.ribbon(a, b, f.a, FxDraw.BEAM, 0.7f, WHITE, 0.9f * fade, true);
    }

    private static float[] color(int c) {
        return switch (c) {
            case SubNet.COLOR_RED -> RED;
            case SubNet.COLOR_PINK -> PINK;
            case SubNet.COLOR_GOLD -> GOLD;
            case SubNet.COLOR_ICE -> ICE_BLUE;
            case SubNet.COLOR_FIRE -> FIRE;
            case SubNet.COLOR_WHITE -> WHITE;
            default -> GT_BLUE;
        };
    }

    // ---------- Ichigo ----------
    // Ported from the Visualizador's Ichigo tab: the crescent with its curtain of light (getsugaBand), the echoes behind,
    // bits shed from the tips, the rip along the cut on a hit (not a round blast), the aura on the blade and the body.

    private static final float[] DIRT = FxDraw.rgb(0x4A3A2C);
    private static final float[] DUST = FxDraw.rgb(0x8A8378);
    private static final float[] GT_DARK = FxDraw.rgb(0x2A0204);

    private static void crescentQuad(Vector3f c, Vector3f fwd, Vector3f side, float len, float width, float param, float[] rgb, float op, boolean glow) {
        Vector3f u = new Vector3f(fwd).mul(len * 0.5f);
        Vector3f v = new Vector3f(side).mul(width * 0.5f);
        FxDraw.quad(new Vector3f(c).sub(u).sub(v), new Vector3f(c).add(u).sub(v), new Vector3f(c).add(u).add(v), new Vector3f(c).sub(u).add(v),
                CRESCENT, param, rgb, op, glow);
    }

    // The side axis of a cut flying along f, rolled like the diagonal swing.
    private static Vector3f slashSide(Vector3f f, float roll) {
        Vector3f flat = new Vector3f(f).cross(0, 1, 0);
        if (flat.lengthSquared() < 1.0E-4f) {
            flat.set(1, 0, 0);
        }
        flat.normalize();
        Vector3f up = new Vector3f(flat).cross(f).normalize();
        return flat.mul(Mth.cos(roll)).add(up.mul(Mth.sin(roll))).normalize();
    }

    // The crescent's curtain: its arc (in the plane of f and s) stretched across the cut, so it has a body seen from any
    // side; where the curtain turns edge-on to the camera it fades out softly (or the fold shows as a hard line).
    private static void getsugaBand(Vector3f pos, Vector3f f, Vector3f s, float w, float l, float[] col, float op, boolean glow, float coreP, float hk,
            float age) {
        if (op <= 0.002f) {
            return;
        }
        Vector3f n = new Vector3f(f).cross(s).normalize();
        int segs = 22;
        float bow = l * 0.9f;
        Vector3f pc = null;
        float ph = 0;
        float pf = 0;
        for (int i = 0; i <= segs; i++) {
            float u = i / (float) segs * 2 - 1;
            float t = 1 - u * u;
            Vector3f cur = new Vector3f(pos).add(new Vector3f(f).mul(-bow * u * u + l * 0.25f)).add(new Vector3f(s).mul(u * w * 0.5f));
            float hh = (0.06f + 0.94f * (float) Math.pow(t, 0.8)) * w * hk * (1 + 0.12f * Mth.sin(age * 1.3f + u * 7));
            Vector3f g0 = new Vector3f(f).mul(-2 * bow * u).add(new Vector3f(s).mul(w * 0.5f)).cross(n);
            float face = g0.lengthSquared() < 1.0E-8f || cur.lengthSquared() < 1.0E-8f ? 1
                    : Math.abs(new Vector3f(g0).normalize().dot(new Vector3f(cur).normalize()));
            float fc = op * (float) Math.pow(smooth((face - 0.04f) / 0.85f), 1.5);
            if (pc != null) {
                FxDraw.quadFade(new Vector3f(pc).sub(new Vector3f(n).mul(ph)), new Vector3f(cur).sub(new Vector3f(n).mul(hh)),
                        new Vector3f(cur).add(new Vector3f(n).mul(hh)), new Vector3f(pc).add(new Vector3f(n).mul(ph)), FxDraw.BEAM, coreP, col, pf, fc, fc,
                        pf, glow);
            }
            pc = cur;
            ph = hh;
            pf = fc;
        }
    }

    // One Getsuga at pos (axes f, s), age ticks after the cut.
    private static void drawGetsuga(Vector3f pos, Vector3f f, Vector3f s, float w, float age, boolean kuroi, float fade, boolean glow) {
        float l = w * 0.62f;
        float o = fade * smooth(age / 3);
        if (o <= 0.01f) {
            return;
        }
        float wob = Mth.sin(age * 0.9f) * 0.05f;
        Vector3f s2 = new Vector3f(s).add(new Vector3f(f).mul(wob)).normalize();
        if (!kuroi) {
            if (glow) {
                for (int e = 1; e <= 3; e++) {
                    getsugaBand(new Vector3f(pos).sub(new Vector3f(f).mul(e * l * 0.55f)), f, s2, w * (1 - e * 0.07f), l, GT_BLUE, o * 0.28f / e, true, 0,
                            0.12f, age - e);
                }
                getsugaBand(pos, f, s2, w * 1.1f, l, GT_BLUE, 0.45f * o, true, 0.1f, 0.2f, age);
                getsugaBand(pos, f, s2, w, l, GT_ICE, 0.9f * o, true, 0.75f, 0.09f, age);
                crescentQuad(pos, f, s2, l * 1.5f, w * 1.25f, 0, GT_BLUE, 0.3f * o, true);
                crescentQuad(pos, f, s2, l, w, 0, GT_ICE, 0.8f * o, true);
            }
        } else if (glow) {
            for (int e = 1; e <= 3; e++) {
                getsugaBand(new Vector3f(pos).sub(new Vector3f(f).mul(e * l * 0.55f)), f, s2, w * (1 - e * 0.07f), l, RED, o * 0.35f / e, true, 0, 0.14f,
                        age - e);
            }
            getsugaBand(pos, f, s2, w * 1.12f, l, RED, 0.75f * o, true, 0.2f, 0.3f, age);
            crescentQuad(pos, f, s2, l * 1.6f, w * 1.3f, 0, RED, 0.45f * o, true);
        } else {
            getsugaBand(pos, f, s2, w, l, GT_DARK, 0.95f * o, false, 0, 0.15f, age);
            crescentQuad(pos, f, s2, l, w, 1, DARK, o, false);
        }
        // Bits coming off the tips and left behind.
        for (int k = 0; k < 26; k++) {
            int born = (int) Math.floor(age) - (k % 13);
            float a = age - born;
            if (born < 0 || a > 13) {
                continue;
            }
            boolean dark = kuroi && k % 3 == 0;
            if (dark == glow) {
                continue;
            }
            float u = hash(k * 131 + born, 3) * 2 - 1;
            float side = u * w * 0.5f;
            float back = -l * 0.2f * (1 - u * u) - a * 0.35f;
            Vector3f p = new Vector3f(pos).add(new Vector3f(s2).mul(side * (1 + a * 0.02f))).add(new Vector3f(f).mul(back)).add(0, a * 0.03f, 0);
            float[] col = kuroi ? (dark ? GT_DARK : RED) : (k % 3 != 0 ? GT_ICE : GT_BLUE);
            FxDraw.billboard(p, (0.18f + hash(k * 131 + born, 4) * 0.2f) * (1 - a / 13), 0, FxDraw.SOFT, 0.7f, col, o * (1 - a / 13), glow);
        }
    }

    private static void getsuga(Fx f, Vec3 camera, double now, float partial, float out, boolean glow) {
        boolean kuroi = f.b > 0.5f;
        Vec3 d = f.dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.dir.normalize();
        Vec3 posW = f.endAt >= 0 ? f.pos : f.prev.lerp(f.pos, Math.min(1, partial));
        Vector3f fwd = new Vector3f((float) d.x, (float) d.y, (float) d.z);
        float roll = kuroi ? -0.5f : f.a > 4.5f ? -0.32f : -0.55f;
        float age = (float) (now - f.born) + partial;
        drawGetsuga(rel(posW, camera), fwd, slashSide(fwd, roll), f.a, age, kuroi, out, glow);
        if (!glow && f.a > 4.5f) {
            // The charged one scrapes the ground: dust rising along its path.
            int j = 0;
            for (Vec3 p : f.trail) {
                if (j++ % 2 == 1) {
                    continue;
                }
                float a2 = (f.trail.size() - j) * 1f;
                if (a2 > 24) {
                    continue;
                }
                FxDraw.billboard(rel(p.add(0, -0.9 + a2 * 0.06, 0), camera), 1.2f + a2 * 0.12f, j, FxDraw.SMOKE, 0.3f, DUST, 0.5f * (1 - a2 / 24) * out,
                        false);
            }
        }
    }

    // The rip of a hit: it opens along the line of the cut (s), with dirt and dust flying; c >= 1 = cut on the ground.
    private static void rip(Fx f, Vec3 camera, float age, boolean glow) {
        boolean kuroi = f.b > 0.5f;
        Vec3 d = f.dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.dir.normalize();
        Vector3f fv = new Vector3f((float) d.x, (float) d.y, (float) d.z);
        Vector3f s = f.c >= 1 ? new Vector3f(0, 1, 0) : slashSide(fv, kuroi ? -0.5f : -0.55f);
        drawRip(rel(f.pos, camera), fv, s, age, f.a, kuroi, glow);
    }

    private static void drawRip(Vector3f at, Vector3f f, Vector3f s, float age, float w, boolean kuroi, boolean glow) {
        if (age < 0 || age > 30) {
            return;
        }
        float k = age / 30;
        float[] c1 = kuroi ? RED : GT_BLUE;
        float[] c2 = kuroi ? FxDraw.rgb(0xFFD0C8) : WHITE;
        float spread = w * 0.5f + (1 - (1 - Math.min(1, age / 8)) * (1 - Math.min(1, age / 8))) * w * 0.6f;
        if (glow) {
            if (age < 12) {
                float a = 1 - age / 12;
                FxDraw.ribbon(new Vector3f(at).sub(new Vector3f(s).mul(spread)), new Vector3f(at).add(new Vector3f(s).mul(spread)), 1.0f * a + 0.2f,
                        FxDraw.BEAM, 0.3f, c1, 0.7f * a, true);
                FxDraw.ribbon(new Vector3f(at).sub(new Vector3f(s).mul(spread * 0.8f)), new Vector3f(at).add(new Vector3f(s).mul(spread * 0.8f)),
                        0.4f * a + 0.1f, FxDraw.BEAM, 0.95f, c2, a, true);
                FxDraw.billboard(at, w * 0.5f * (0.6f + 0.4f * a), age * 0.2f, FxDraw.FLARE, 4 / 32f, c1, 0.7f * a, true);
            }
            return;
        }
        for (int i = 0; i < 22; i++) {
            float u = hash(i, 191) * 2 - 1;
            Vector3f v = new Vector3f(s).mul(u * 0.6f).add(new Vector3f(f).mul(0.4f + hash(i, 192) * 0.6f)).add(0, 0.2f + hash(i, 193) * 0.5f, 0)
                    .normalize().mul(0.35f + hash(i, 194) * 0.35f);
            Vector3f p = new Vector3f(at).add(new Vector3f(s).mul(u * w * 0.4f)).add(new Vector3f(v).mul(age)).add(0, -0.02f * age * age, 0);
            if (p.y < at.y - 1.1f) {
                continue;
            }
            FxDraw.billboard(p, 0.35f + hash(i, 195) * 0.3f, age * 0.4f + i, SHARD, 0, i % 4 != 0 ? DIRT : DUST, 1 - k, false);
        }
        for (int j = 0; j < 6; j++) {
            FxDraw.billboard(new Vector3f(at).add(new Vector3f(s).mul((j / 5f * 2 - 1) * w * 0.4f)).add(0, -0.6f + k * 1.5f, 0), 2 + k * 3, j,
                    FxDraw.SMOKE, 0.3f, DUST, 0.5f * (1 - k), false);
        }
    }

    // Reiatsu while charging: a glow wrapped round the blade (strongest on the edge) and flames rising off the body.
    private static void charge(Fx f, Vec3 camera, float age, float partial, float out, boolean glow) {
        int style = (int) f.b;
        float[] col = switch (style) {
            case 1 -> RED;
            case 2 -> FIRE;
            case 3 -> ICE_BLUE;
            default -> GT_BLUE;
        };
        boolean kuroi = style == 1;
        float a = Math.min(1, 0.3f + 0.7f * Mth.clamp(f.a, 0, 1)) * out;
        Entity e = FxAnchor.entity(f.id);
        boolean self = FxAnchor.firstPersonSelf(f.id);
        Vec3 hand = handOf(f.id, partial, f.pos);
        Vec3 look = e instanceof LivingEntity le ? le.getViewVector(partial) : new Vec3(0, 0, 1);
        Vec3 tipW = hand.add(0, 1.3, 0).add(look.scale(0.35));
        Vector3f base = rel(hand.add(0, 0.2, 0), camera);
        Vector3f tip = rel(tipW, camera);
        if (glow && !self) {
            FxDraw.ribbon(base, tip, 0.6f * a, FxDraw.BEAM, 0.25f, col, 0.7f * a, true);
            FxDraw.ribbon(base, tip, 0.12f * a, FxDraw.BEAM, 0.9f, kuroi ? FxDraw.rgb(0xFFB0A8) : WHITE, 0.8f * a, true);
            for (int i = 0; i < 10; i++) {
                float u = fract(age * 0.06f + i / 10f);
                Vector3f p = new Vector3f(base).lerp(tip, u).add(0, Mth.sin(age * 0.5f + i) * 0.15f, 0);
                FxDraw.billboard(p, 0.35f * a, age * 0.2f + i, FxDraw.FIRE, 0.5f, col, 0.35f * a, true);
            }
        }
        if (glow) {
            FxDraw.billboard(rel(hand, camera), 0.4f + 0.6f * a, 0, FxDraw.SOFT, 0.8f, WHITE, 0.4f * a, true);
        }
        if (e == null) {
            return;
        }
        Vec3 ctr = e.getPosition(partial).add(0, e.getBbHeight() * 0.55, 0);
        for (int i = 0; i < 14; i++) {
            boolean dark = kuroi && i % 2 == 1;
            if (dark == glow) {
                continue;
            }
            float ph = fract(age * 0.04f + hash(i, 31));
            float ang = hash(i, 32) * Mth.TWO_PI;
            Vector3f p = rel(ctr.add(Mth.cos(ang) * 0.45, -0.8 + ph * 2.4, Mth.sin(ang) * 0.45), camera);
            if (self && p.lengthSquared() < 1.5f) {
                continue;
            }
            FxDraw.billboard(p, 0.3f * a * (1 - ph) + 0.15f, 0, FxDraw.FIRE, 0.6f, dark ? GT_DARK : col, 0.5f * a * (1 - ph), glow);
        }
    }

    // Shift+Z, the Getsuga cut into the ground: rips of energy run out over the ground in many directions, tearing up
    // the dirt, each one ending in a rip; a flash and a ring where it hit, dust rising.
    private static void rift(Fx f, Vec3 camera, float age, boolean glow) {
        int n = Math.max(3, (int) f.b - 1);
        boolean kuroi = f.c >= 1;
        float[] col = kuroi ? RED : GT_BLUE;
        Vector3f at = rel(f.pos.add(0, 0.05, 0), camera);
        for (int i = 0; i < n; i++) {
            float ang = i / (float) n * Mth.TWO_PI + 0.3f;
            Vector3f d = new Vector3f(Mth.sin(ang), 0, Mth.cos(ang));
            float reach = f.a * (0.6f + 0.45f * hash(i, 42));
            float len = Math.min(reach, age * 0.9f);
            float a = Math.max(0, 1 - age / 22);
            Vector3f tip = new Vector3f(at).add(new Vector3f(d).mul(len));
            if (glow) {
                groundStrip(at, new Vector3f(tip).add(0, 0.01f, 0), 0.7f * a, FxDraw.BEAM, 0.2f, col, 0.55f * a, true);
                groundStrip(new Vector3f(at).add(0, 0.02f, 0), new Vector3f(tip).add(0, 0.02f, 0), 0.14f * a, FxDraw.BEAM, 0.9f, WHITE, 0.8f * a, true);
                if (len < reach) {
                    for (int k = 0; k < 3; k++) {
                        FxDraw.billboard(new Vector3f(tip).add(0, 0.5f + k * 0.4f, 0), 1.0f - k * 0.25f, k, FxDraw.FIRE, 0.5f, col, 0.35f * a, true);
                    }
                }
            } else {
                groundStrip(new Vector3f(at).add(0, -0.02f, 0), new Vector3f(tip).add(0, -0.02f, 0), 0.6f, FxDraw.SOFT, 0, DIRT,
                        0.7f * Math.min(1, 2 - age / 40), false);
            }
            drawRip(new Vector3f(tip).add(0, 0.4f, 0), d, new Vector3f(0, 1, 0), age - reach / 0.9f, 1.6f, kuroi, glow);
        }
        if (glow && age < 10) {
            FxDraw.billboard(new Vector3f(at).add(0, 0.5f, 0), 3.5f * (1 - age / 10) + 0.8f, 0, FxDraw.FLARE, 7 / 32f, kuroi ? RED : GT_ICE,
                    0.7f * (1 - age / 10), true);
            FxDraw.disk(at, new Vector3f(0, 1, 0), 2 + age * 1.4f, FxDraw.RING, 0.15f, col, 1 - age / 10, true);
        }
        if (!glow) {
            float k = Mth.clamp(age / 30, 0, 1);
            for (int j = 0; j < 8; j++) {
                FxDraw.billboard(new Vector3f(at).add(Mth.cos(j) * (1 + k * 4), 0.5f + k * 1.5f, Mth.sin(j) * (1 + k * 4)), 2 + k * 3, j, FxDraw.SMOKE,
                        0.3f, DUST, 0.55f * (1 - k), false);
            }
        }
    }

    // A strip lying flat on the ground from a to b.
    private static void groundStrip(Vector3f a, Vector3f b, float width, int shape, float param, float[] rgb, float op) {
        Vector3f along = new Vector3f(b).sub(a);
        if (along.lengthSquared() < 1.0E-6f) {
            return;
        }
        Vector3f s = new Vector3f(along).cross(0, 1, 0).normalize(width * 0.5f);
        FxDraw.quad(new Vector3f(a).sub(s), new Vector3f(b).sub(s), new Vector3f(b).add(s), new Vector3f(a).add(s), shape, param, rgb, op, true);
    }

    private static void groundStrip(Vector3f a, Vector3f b, float width, int shape, float param, float[] rgb, float op, boolean glow) {
        Vector3f along = new Vector3f(b).sub(a);
        if (along.lengthSquared() < 1.0E-6f) {
            return;
        }
        Vector3f s = new Vector3f(along).cross(0, 1, 0).normalize(width * 0.5f);
        FxDraw.quad(new Vector3f(a).sub(s), new Vector3f(b).sub(s), new Vector3f(b).add(s), new Vector3f(a).add(s), shape, param, rgb, op, glow);
    }

    // Auras: flames of reiatsu rising off a body (Hollow mask, Tensa Zangetsu, Sukuna), or Rukia's white cold mist.
    private static void aura(Fx f, Vec3 camera, float age, float partial, float out, boolean glow) {
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        int style = (int) f.b;
        boolean self = FxAnchor.firstPersonSelf(f.id);
        Vec3 feet = e.getPosition(partial);
        float h = e.getBbHeight();
        float in = smooth(age / 6) * out;
        float t = age;
        if (style == 2) {
            if (!glow) {
                return;
            }
            for (int i = 0; i < 14; i++) {
                float life = 30;
                float ph = fract((t + hash(i, 51) * life) / life);
                float ang = hash(i, 52) * Mth.TWO_PI + t * 0.02f;
                float r = 0.5f + 0.6f * ph;
                Vector3f p = rel(feet.add(Mth.cos(ang) * r, ph * h * 1.6f, Mth.sin(ang) * r), camera);
                if (self && p.lengthSquared() < 1.2f) {
                    continue;
                }
                FxDraw.billboard(p, 0.9f + ph, ang, FxDraw.SOFT, 0.1f, MIST, 0.25f * in * Mth.sin(ph * Mth.PI), true);
            }
            for (int i = 0; i < 10; i++) {
                float life = 40;
                float ph = fract((t + hash(i, 53) * life) / life);
                float ang = hash(i, 54) * Mth.TWO_PI;
                Vector3f p = rel(feet.add(Mth.cos(ang) * 1.2f, h * (1.3f - ph * 1.3f), Mth.sin(ang) * 1.2f), camera);
                FxDraw.billboard(p, 0.12f, 0, FxDraw.SOFT, 0.9f, WHITE, 0.8f * in, true);
            }
            FxDraw.disk(rel(feet.add(0, 0.05, 0), camera), new Vector3f(0, 1, 0), 4, FxDraw.SOFT, 0.1f, ICE_BLUE, 0.3f * in, true);
            return;
        }
        float[] col = style == 3 ? RED : style == 1 ? RED : CRIMSON;
        int n = style == 1 ? 16 : 11;
        for (int i = 0; i < n; i++) {
            float life = 16;
            float ph = fract((t + hash(i, 55) * life) / life);
            float ang = hash(i, 56) * Mth.TWO_PI + t * 0.03f;
            float r = 0.35f + 0.25f * hash(i, 57);
            Vector3f p = rel(feet.add(Mth.cos(ang) * r, ph * h * 1.25f, Mth.sin(ang) * r), camera);
            if (self && p.lengthSquared() < 1.2f) {
                continue;
            }
            float size = (0.7f + 0.5f * (1 - ph)) * (style == 1 ? 1.2f : 1);
            float vis = in * Mth.sin(ph * Mth.PI);
            if (glow) {
                FxDraw.billboard(p, size * 1.3f, ang, FxDraw.FIRE, ph * 0.6f, col, 0.45f * vis, true);
            } else if (style != 3) {
                FxDraw.billboard(p, size, ang, FxDraw.FIRE, ph * 0.7f, DARK, 0.75f * vis, false);
            }
        }
    }

    // ---------- Rukia ----------
    // Ported from the Visualizador's Rukia tab (v26+): 3D crystals of pixel ice (iceSpike), snow, cold mist, and the
    // ice over bodies in IceShell. Light never cuts off hard: everything fades in and out.

    // A crystal of ice: a six-sided spike from base toward dir, h tall, r wide at the base, each side lit on its own.
    static void iceSpike(Vector3f base, Vector3f dir, float h, float r, float spin, float op) {
        if (h <= 0.01f || op <= 0.002f) {
            return;
        }
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f u = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f w = new Vector3f(d).cross(u);
        Vector3f tip = new Vector3f(base).add(new Vector3f(d).mul(h));
        Vector3f[] ring = new Vector3f[7];
        for (int k = 0; k < 7; k++) {
            float a = spin + k * Mth.TWO_PI / 6;
            ring[k] = new Vector3f(base).add(new Vector3f(u).mul(Mth.cos(a) * r)).add(new Vector3f(w).mul(Mth.sin(a) * r));
        }
        for (int k = 0; k < 6; k++) {
            Vector3f mid = new Vector3f(ring[k]).add(ring[k + 1]).mul(0.5f).sub(base).normalize();
            Vector3f n = new Vector3f(mid).mul(h).add(new Vector3f(d).mul(r)).normalize();
            float lit = 0.55f + 0.45f * Math.max(0, n.dot(0.37f, 0.86f, 0.28f));
            float[] col = {0.78f, 0.9f * lit, 1f};
            FxDraw.quad(ring[k], ring[k + 1], tip, tip, ICE, 1, col, 0.85f * op, false);
        }
    }

    // Fine snow falling round a spot.
    private static void snowfall(Vec3 c, float rad, float age, int n, float a, Vec3 camera) {
        if (a <= 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float y = 7 - fract(age * (0.02f + hash(i, 121) * 0.02f) + hash(i, 122)) * 7;
            float x = (hash(i, 123) * 2 - 1) * rad + Mth.sin(age * 0.03f + i) * 0.4f;
            float z = (hash(i, 124) * 2 - 1) * rad;
            float edge = 1 - smooth((y - 5.5f) / 1.5f);
            FxDraw.billboard(rel(c.add(x, y, z), camera), 0.07f + hash(i, 125) * 0.06f, 0, FxDraw.SOFT, 0.8f, WHITE, 0.85f * a * edge, true);
        }
    }

    private static void tsukishiro(Fx f, Vec3 camera, float age, boolean glow) {
        float rise = f.b;
        float hold = Rukia.TSUKI_HOLD;
        float scale = f.a / 2.5f;
        Vector3f c = rel(f.pos.add(0, 0.05, 0), camera);
        float draw = smooth(age / Math.max(1, rise * 0.8f));
        float colAge = age - rise;
        float ring = 1 - smooth((colAge - hold - 6) / 10);
        if (!glow) {
            FxDraw.disk(rel(f.pos.add(0, 0.02, 0), camera), new Vector3f(0, 1, 0), f.a * 2.1f, FxDraw.SOFT, 0.1f, PALE,
                    0.35f * smooth(colAge / 4) * (1 - smooth((colAge - hold - 10) / 14)), false);
            if (colAge >= 0 && colAge < hold + 2) {
                // Crystals shooting up inside the circle while the statue stands.
                float k = smooth(colAge / 8) * (1 - smooth((colAge - hold) / 2));
                for (int i = 0; i < 18; i++) {
                    float a = hash(i, 131) * Mth.TWO_PI;
                    float r = (0.8f + hash(i, 132) * 1.6f) * scale;
                    Vector3f p = new Vector3f(c).add(Mth.cos(a) * r, -0.05f, Mth.sin(a) * r);
                    iceSpike(p, new Vector3f(Mth.cos(a) * 0.3f, 1, Mth.sin(a) * 0.3f), (0.4f + hash(i, 133) * 1.1f) * k * scale, 0.16f * scale, i, 1);
                }
            }
            if (colAge >= hold) {
                // The statue breaks: crystals flung out, falling.
                float a = colAge - hold;
                for (int i = 0; i < 30; i++) {
                    Vector3f d = new Vector3f(hash(i, 134) - 0.5f, hash(i, 135) * 0.9f, hash(i, 136) - 0.5f).normalize();
                    Vector3f p = new Vector3f(c).add(0, 1.1f, 0).add(new Vector3f(d).mul(a * (0.12f + hash(i, 137) * 0.1f))).add(0, -0.012f * a * a, 0);
                    if (p.y > c.y) {
                        iceSpike(p, d, 0.35f * (1 - a / 40), 0.12f, a * 0.3f + i, 1 - smooth((a - 24) / 16));
                    }
                }
            }
            return;
        }
        // The circle drawn round the target, then its glow.
        int seg = 48;
        int shown = (int) (seg * draw);
        Vector3f last = null;
        for (int i = 0; i <= shown; i++) {
            float ang = i / (float) seg * Mth.TWO_PI;
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * f.a, 0, Mth.sin(ang) * f.a);
            if (last != null) {
                groundStrip(last, p, 0.22f, FxDraw.BEAM, 0.8f, PALE, 0.95f * ring);
            }
            last = p;
        }
        if (colAge < 0) {
            FxDraw.disk(c, new Vector3f(0, 1, 0), f.a * 2, FxDraw.SOFT, 0.2f, ICE_BLUE, 0.25f * draw, true);
        }
        snowfall(f.pos.add(0, 0, 0), f.a * 2.2f, age, 120, ring, camera);
        if (colAge < 0) {
            return;
        }
        float up = 1 - (1 - Math.min(1, colAge / 8)) * (1 - Math.min(1, colAge / 8));
        float fade = 1 - smooth((colAge - 24) / 30);
        Vector3f top = new Vector3f(c).add(0, 40 * up, 0);
        FxDraw.ribbon(c, top, f.a * 2.16f, FxDraw.BEAM, 0.1f, ICE_BLUE, 0.55f * fade, true);
        FxDraw.ribbon(c, top, f.a * 0.96f, FxDraw.BEAM, 0.8f, WHITE, 0.8f * fade, true);
        for (int i = 0; i < 70; i++) {
            float y = (colAge * 0.25f + hash(i, 61) * 40) % 40;
            if (y > 40 * up) {
                continue;
            }
            float ang = hash(i, 62) * Mth.TWO_PI;
            float r = hash(i, 63) * f.a * 0.96f;
            FxDraw.billboard(new Vector3f(c).add(Mth.cos(ang) * r, y, Mth.sin(ang) * r), 0.15f, 0, FxDraw.SOFT, 0.9f, WHITE, 0.9f * fade, true);
        }
        if (colAge < 12) {
            FxDraw.disk(new Vector3f(c).add(0, 0.03f, 0), new Vector3f(0, 1, 0), f.a * 2 + colAge * 0.8f, FxDraw.RING, 0.15f, PALE,
                    1 - colAge / 12, true);
        }
        if (colAge >= hold && colAge < hold + 8) {
            float a = colAge - hold;
            FxDraw.billboard(new Vector3f(c).add(0, 1.2f, 0), 3 * (1 - a / 8) + 0.5f, 0, FxDraw.FLARE, 6 / 32f, PALE, 1 - a / 8, true);
        }
    }

    private static void hakuren(Fx f, Vec3 camera, float age, boolean glow) {
        Vec3 d = new Vec3(f.dir.x, 0, f.dir.z);
        d = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 side = d.cross(new Vec3(0, 1, 0)).normalize();
        float len = f.a;
        float front = len * smooth(age / 14);
        float fade = 1 - smooth((age - 40) / 40);
        // The white avalanche: cold mist rolling forward, wider and taller as it goes, with a soft light in it.
        for (int i = 0; i < 46; i++) {
            float s = hash(i, 71) * front;
            float frac = s / Math.max(1, len);
            float hw = f.b * (0.2f + 0.45f * frac);
            Vec3 p = f.pos.add(d.scale(s)).add(side.scale((hash(i, 72) * 2 - 1) * hw)).add(0, 0.4 + hash(i, 73) * (1 + s * 0.09), 0);
            float near = smooth((front - s + 1) / 3);
            if (glow) {
                if (i % 2 == 0) {
                    FxDraw.billboard(rel(p, camera), 1.8f + s * 0.08f, i, FxDraw.SOFT, 0.3f, ICE_BLUE, 0.25f * fade * near, true);
                }
            } else {
                FxDraw.billboard(rel(p, camera), 2.2f + s * 0.12f, i + age * 0.01f, FxDraw.SMOKE, 0.2f, MIST, 0.55f * fade * near, false);
            }
        }
        if (glow) {
            Vec3 head = f.pos.add(d.scale(front)).add(0, 1.4, 0);
            FxDraw.billboard(rel(head, camera), 4 + front * 0.15f, age * 0.05f, FxDraw.SOFT, 0.6f, WHITE, 0.6f * (1 - smooth((age - 16) / 10)), true);
            snowfall(f.pos.add(d.scale(len * 0.5)), len * 0.45f, age, 120, fade, camera);
            return;
        }
        // Crystals bursting up from the ground as the front passes, leaning forward.
        for (int i = 0; i < 70; i++) {
            float s = 0.3f + hash(i, 74) * (len - 0.3f);
            if (s > front) {
                continue;
            }
            float hw = f.b * (0.16f + 0.5f * s / len);
            Vec3 p = f.pos.add(d.scale(s)).add(side.scale((hash(i, 75) * 2 - 1) * hw)).add(0, -0.05, 0);
            Vec3 dir = side.scale((hash(i, 76) - 0.5) * 0.9).add(0, 1, 0).add(d.scale(0.35));
            float grow = smooth((front - s) / 5.5f);
            iceSpike(rel(p, camera), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z), (0.5f + hash(i, 77) * 1.8f) * grow,
                    0.2f + hash(i, 78) * 0.15f, i, fade);
        }
    }

    private static void shirafune(Fx f, Vec3 camera, float age, boolean glow) {
        Vec3 d = f.dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.dir.normalize();
        float hold = 24;
        float ext = Math.min(1, age / 3) * (1 - smooth((age - hold) / 8));
        float len = f.a * ext;
        Vector3f a = rel(f.pos, camera);
        Vector3f dv = new Vector3f((float) d.x, (float) d.y, (float) d.z);
        Vec3 hitP = f.pos.add(d.scale(f.a));
        if (!glow) {
            if (len > 0.05f) {
                // The blade grown into one long crystal, little spikes along it.
                iceSpike(a, dv, len, 0.09f, 0, 1);
                Vector3f upish = new Vector3f(dv).add(0, 0.6f, 0).normalize();
                for (int k = 0; k < 7; k++) {
                    float u = (k + 0.5f) / 7;
                    if (u * len < 0.3f) {
                        continue;
                    }
                    iceSpike(new Vector3f(a).add(new Vector3f(dv).mul(u * len)), upish, 0.25f * (1 - u * 0.5f), 0.05f, k, 1);
                }
            }
            if (f.c >= 1 && age >= 2) {
                // From the wound the ice spreads out in spikes.
                float k = smooth((age - 2) / 30) * (1 - smooth((age - hold - 4) / 20));
                for (int i = 0; i < 16; i++) {
                    Vec3 r = new Vec3(hash(i, 84) - 0.5, hash(i, 85) - 0.5, hash(i, 86) - 0.5).normalize();
                    double along = r.dot(d);
                    r = r.subtract(d.scale(along)).subtract(d.scale(Math.abs(along) * 0.3)).normalize();
                    Vec3 base = hitP.add(r.scale(0.3 * k));
                    iceSpike(rel(base, camera), new Vector3f((float) r.x, (float) r.y, (float) r.z), (0.3f + hash(i, 87) * 0.6f) * k, 0.1f, i, 1);
                }
            }
            return;
        }
        if (age < 3) {
            // The cold gathering at the tip.
            for (int k = 0; k < 12; k++) {
                float ph = 1 - age / 3;
                FxDraw.billboard(new Vector3f(a).add(Mth.cos(k) * ph, Mth.sin(k * 2) * ph, Mth.sin(k) * ph), 0.1f, 0, FxDraw.SOFT, 0.9f, WHITE,
                        1 - ph, true);
            }
        }
        if (len > 0.05f) {
            Vector3f b = rel(f.pos.add(d.scale(len)), camera);
            FxDraw.ribbon(a, b, 0.5f, FxDraw.BEAM, 0.4f, ICE_BLUE, 0.35f, true);
        }
        if (f.c >= 1 && age >= 2 && age < 8) {
            FxDraw.billboard(rel(hitP, camera), 1.5f, 0, FxDraw.FLARE, 6 / 32f, PALE, 1 - (age - 2) / 6, true);
        }
        snowfall(f.pos.add(d.scale(f.a * 0.5)).add(0, -1.4, 0), f.a * 0.4f, age, 60, 1 - smooth((age - hold) / 20), camera);
    }

    // A crystal of ice standing out of the ground for a while (where she stabs the ground before the Hakuren).
    private static void spike(Fx f, Vec3 camera, float age, boolean glow) {
        float k = smooth(age / 4) * (1 - smooth((age - f.b + 6) / 6));
        if (glow) {
            FxDraw.disk(rel(f.pos.add(0, 0.03, 0), camera), new Vector3f(0, 1, 0), 1.2f, FxDraw.SOFT, 0.2f, ICE_BLUE, 0.4f * k, true);
            for (int i = 0; i < 6; i++) {
                float ph = fract(age * 0.05f + hash(i, 141));
                FxDraw.billboard(rel(f.pos.add((hash(i, 142) - 0.5) * 0.5, ph * 1.4, (hash(i, 143) - 0.5) * 0.5), camera), 0.12f, 0, FxDraw.SOFT,
                        0.9f, WHITE, 0.9f * k * (1 - ph * 0.5f), true);
            }
            return;
        }
        Vec3 dir = f.dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : f.dir.normalize();
        iceSpike(rel(f.pos, camera), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z), f.a * k, 0.14f, f.id, 1);
    }

    // The ground freezing round her (the Bankai): cracks running out, the ground turning to ice, crystals coming up,
    // then the wave of absolute zero (at Rukia.BANKAI_W) and white mist, all of it melting away at the end.
    private static void frost(Fx f, Vec3 camera, float age, boolean glow) {
        float wave = Rukia.BANKAI_W - Rukia.BANKAI_X - Rukia.BANKAI_FROST;
        float spread = smooth(age / 30);
        float fade = 1 - smooth((age - (f.b - 50)) / 50);
        float radius = f.a * spread;
        Vector3f c = rel(f.pos.add(0, 0.04, 0), camera);
        if (!glow) {
            // The ground turning to ice, block by block.
            int n = (int) Math.ceil(radius);
            Vec3 base = new Vec3(Math.floor(f.pos.x), f.pos.y + 0.03, Math.floor(f.pos.z));
            for (int ix = -n; ix <= n; ix++) {
                for (int iz = -n; iz <= n; iz++) {
                    double dx = ix + 0.5;
                    double dz = iz + 0.5;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > radius) {
                        continue;
                    }
                    float local = (float) Mth.clamp((radius - dist) / 2.5, 0, 1) * fade;
                    Vector3f q0 = rel(base.add(ix, 0, iz), camera);
                    Vector3f q1 = rel(base.add(ix + 1, 0, iz), camera);
                    Vector3f q2 = rel(base.add(ix + 1, 0, iz + 1), camera);
                    Vector3f q3 = rel(base.add(ix, 0, iz + 1), camera);
                    FxDraw.quad(q0, q1, q2, q3, ICE, 0.5f + 0.4999f * local, ICE_BLUE, 0.6f, false);
                }
            }
            for (int i = 0; i < 40; i++) {
                float a = hash(i, 101) * Mth.TWO_PI;
                float r = 1.5f + hash(i, 102) * (f.a - 2);
                float k = smooth((age - 5 - r * 2) / 10) * fade;
                iceSpike(new Vector3f(c).add(Mth.sin(a) * r, -0.05f, Mth.cos(a) * r), new Vector3f(Mth.sin(a) * 0.3f, 1, Mth.cos(a) * 0.3f),
                        (0.2f + hash(i, 103) * 0.7f) * k, 0.14f, i, 1);
            }
            if (age >= wave && age < wave + 50) {
                float a = age - wave;
                for (int j = 0; j < 16; j++) {
                    float ang = j / 16f * Mth.TWO_PI;
                    float r = f.a * (1 - (1 - Math.min(1, a / 18)) * (1 - Math.min(1, a / 18)));
                    FxDraw.billboard(new Vector3f(c).add(Mth.sin(ang) * r, 0.6f, Mth.cos(ang) * r), 2.4f, j, FxDraw.SMOKE, 0.2f, MIST,
                            0.5f * (1 - a / 50), false);
                }
            }
            return;
        }
        // Cracks running out from her feet, lying on the ground.
        for (int i = 0; i < 14; i++) {
            float a = i / 14f * Mth.TWO_PI + hash(i, 104) * 0.3f;
            Vector3f last = new Vector3f(c);
            for (int k = 1; k <= 6; k++) {
                float r = k * f.a / 6.5f * smooth((age - k * 2) / 14);
                float ang = a + Mth.sin(k * 3 + i) * 0.25f;
                Vector3f p = new Vector3f(c).add(Mth.sin(ang) * r, 0, Mth.cos(ang) * r);
                groundStrip(last, p, 0.12f, FxDraw.BEAM, 0.9f, ICE_BLUE, 0.8f * fade, true);
                last = p;
            }
        }
        FxDraw.disk(c, new Vector3f(0, 1, 0), radius * 2.1f, FxDraw.SOFT, 0.05f, PALE, 0.3f * fade, true);
        if (age >= wave && age < wave + 50) {
            float a = age - wave;
            float r = f.a * (1 - (1 - Math.min(1, a / 18)) * (1 - Math.min(1, a / 18)));
            FxDraw.disk(new Vector3f(c).add(0, 0.5f, 0), new Vector3f(0, 1, 0), 2 + r * 2, FxDraw.RING, 0.2f, WHITE, 1 - a / 50, true);
            if (f.hitAt < -50) {
                f.hitAt = 0;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.position().distanceTo(f.pos) < f.a * 2) {
                    ScreenFx.flash(0xEAF6FF, 0.45f, 10);
                    ScreenShake.start(0.5f, 16);
                }
            }
        }
        snowfall(f.pos, f.a * 0.85f, age, 220, smooth(age / 20) * fade, camera);
    }

    // The flat look of the ice over a body (IceShell, when the shader can't be used).
    // The six faces of a box cut into quads of about half a block, each filled from the bottom up to `fill` (0..1 of
    // the box's height), or, with fill < 0, dissolving cell by cell (`left` of it remaining).
    static void iceBox(AABB box, Vec3 camera, float fill, float left, int seed) {
        float[] col = {0.75f + 0.02f * (seed % 7), 0.9f, 1f};
        double h = box.getYsize();
        double fillY = box.minY + h * Math.max(0, fill);
        Vec3[][] faces = {
            {new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ), new Vec3(0, 1, 0)},
            {new Vec3(box.maxX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.maxZ), new Vec3(0, 1, 0)},
            {new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.minZ), new Vec3(0, 1, 0)},
            {new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.maxZ), new Vec3(0, 1, 0)},
        };
        for (Vec3[] face : faces) {
            Vec3 a = face[0];
            Vec3 across = face[1].subtract(a);
            int nx = Math.max(1, (int) Math.round(across.length() / 0.5));
            int ny = Math.max(1, (int) Math.round(h / 0.5));
            for (int ix = 0; ix < nx; ix++) {
                for (int iy = 0; iy < ny; iy++) {
                    double y0 = box.minY + h * iy / ny;
                    double y1 = box.minY + h * (iy + 1) / ny;
                    float param;
                    if (fill < 0) {
                        param = 0.5f + 0.4999f * left;
                    } else {
                        if (fillY <= y0) {
                            continue;
                        }
                        float local = (float) Mth.clamp((fillY - y0) / (y1 - y0), 0, 1);
                        param = Math.min(0.4999f, local * 0.5f);
                    }
                    Vec3 p0 = a.add(across.scale(ix / (double) nx));
                    Vec3 p1 = a.add(across.scale((ix + 1) / (double) nx));
                    // uv (0,0) top-left, v = 1 at the bottom.
                    Vector3f q0 = rel(new Vec3(p0.x, y1, p0.z), camera);
                    Vector3f q1 = rel(new Vec3(p1.x, y1, p1.z), camera);
                    Vector3f q2 = rel(new Vec3(p1.x, y0, p1.z), camera);
                    Vector3f q3 = rel(new Vec3(p0.x, y0, p0.z), camera);
                    FxDraw.quad(q0, q1, q2, q3, ICE, param, col, 0.9f, false);
                }
            }
        }
        if (fill >= 0.98f || fill < 0) {
            Vector3f q0 = rel(new Vec3(box.minX, box.maxY, box.minZ), camera);
            Vector3f q1 = rel(new Vec3(box.maxX, box.maxY, box.minZ), camera);
            Vector3f q2 = rel(new Vec3(box.maxX, box.maxY, box.maxZ), camera);
            Vector3f q3 = rel(new Vec3(box.minX, box.maxY, box.maxZ), camera);
            FxDraw.quad(q0, q1, q2, q3, ICE, fill < 0 ? 0.5f + 0.4999f * left : 0.9999f, col, 0.9f, false);
        }
    }

    // ---------- Urahara ----------

    private static void shield(Fx f, Vec3 camera, float age, double now, float out, boolean glow) {
        if (!glow) {
            return;
        }
        float pop = smooth(age / 4);
        float size = Math.max(1, f.c) * (0.7f + 0.3f * pop);
        float hit = (float) Mth.clamp(1 - (now - f.hitAt) / 14, 0, 1);
        float[] col = CRIMSON;
        Vec3 d = new Vec3(f.dir.x, 0, f.dir.z);
        d = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 sideW = d.cross(new Vec3(0, 1, 0)).normalize();
        Vector3f c = rel(f.pos, camera);
        Vector3f s = new Vector3f((float) sideW.x, 0, (float) sideW.z).mul(size * 0.5f);
        Vector3f up = new Vector3f(0, size * 0.5f, 0);
        float param = 0.5f + 0.49f * hit;
        float op = out * pop * (0.85f + 0.15f * hit);
        FxDraw.quad(new Vector3f(c).sub(s).add(up), new Vector3f(c).add(s).add(up), new Vector3f(c).add(s).sub(up), new Vector3f(c).sub(s).sub(up),
                BARRIER, param, col, op, true);
        FxDraw.billboard(c, size * 1.5f, 0, FxDraw.SOFT, 0.05f, col, 0.15f * op + 0.4f * hit, true);
    }

    // Shibari's net, the Visualizador's drawNet: a mesh of diamond threads with gaps, thrown like a fishing net (bowed
    // back as it flies, opening), then closing round the body from the feet up and squeezing.
    private static final float[] NET_CORE = FxDraw.rgb(0xFFD0D8);
    private static final float[] URA_FIRE = FxDraw.rgb(0xFFB030);
    private static final float[] URA_HOT = FxDraw.rgb(0xFF7A4A);

    private static Vector3f netPoint(float a, float b, Vector3f center, Vector3f f, Vector3f s, float r, Vector3f feet, float k, float squeeze, float t) {
        float u = (a + b) / 2;
        float v = (a - b) / 2;
        Vector3f flat = new Vector3f(center).add(new Vector3f(s).mul(u * r)).add(0, v * r * 0.8f, 0).add(new Vector3f(f).mul(-(1 - u * u) * r * 0.35f));
        if (k <= 0) {
            return flat;
        }
        float th = u * Mth.PI + Mth.HALF_PI;
        float rr = Mth.lerp(squeeze, 0.75f, 0.42f) + 0.03f * Mth.sin(t * 0.6f + a * 5);
        float y = (v + 1) * 1.05f;
        Vector3f wrap = new Vector3f(feet).add(Mth.cos(th) * rr, y, Mth.sin(th) * rr);
        return flat.lerp(wrap, smooth(k));
    }

    private static void drawNet(Vector3f center, Vector3f f, Vector3f s, float r, Vector3f feet, float k, float squeeze, float t, float a) {
        if (a <= 0) {
            return;
        }
        for (int sign = -1; sign <= 1; sign += 2) {
            for (int ia = 0; ia < 13; ia++) {
                float an = -1.8f + ia * 0.3f;
                float lim = 2 - Math.abs(an);
                int n = 12;
                Vector3f last = null;
                for (int i = 0; i <= n; i++) {
                    float bn = -lim + 2 * lim * i / n;
                    Vector3f p = sign > 0 ? netPoint(an, bn, center, f, s, r, feet, k, squeeze, t) : netPoint(bn, an, center, f, s, r, feet, k, squeeze, t);
                    if (last != null) {
                        FxDraw.ribbon(last, p, 0.16f, FxDraw.BEAM, 0.4f, RED, 0.75f * a, true);
                        FxDraw.ribbon(last, p, 0.045f, FxDraw.BEAM, 0.95f, NET_CORE, a, true);
                    }
                    last = p;
                }
            }
        }
    }

    private static void net(Fx f, Vec3 camera, float age, float partial, float out, boolean glow) {
        if (!glow) {
            return;
        }
        double now = ScreenFx.now() + partial;
        Vec3 posW = f.endAt >= 0 ? f.pos : f.prev.lerp(f.pos, Math.min(1, partial));
        Vec3 d = f.dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : new Vec3(f.dir.x, 0, f.dir.z).normalize();
        Vector3f fv = new Vector3f((float) d.x, 0, (float) d.z);
        Vector3f sv = new Vector3f(fv).cross(0, 1, 0).normalize();
        boolean wrapped = f.crushAt >= 0;
        if (!wrapped) {
            float r = Mth.lerp(smooth(Math.min(1, age / 10f)), 0.6f, 1.9f);
            Vector3f c = rel(posW, camera);
            drawNet(c, fv, sv, r, c, 0, 0, age, out);
            return;
        }
        float since = (float) (now - f.crushAt);
        float k = smooth((since + 3) / 8);
        float squeeze = smooth((since - 4) / 14);
        Vector3f feet = rel(posW.add(0, -1.05, 0), camera);
        Vector3f flatC = rel(f.trail.isEmpty() ? posW : f.trail.peekLast(), camera);
        drawNet(flatC, fv, sv, 1.9f, feet, k, squeeze, age, out);
        // Fire beads born on the knots after Hiasobi pierces the net, going off one after another.
        if (f.hitAt > 0) {
            float p = (float) (now - f.hitAt);
            int i = 0;
            for (int ia = 0; ia < 13; ia++) {
                for (int ib = 0; ib < 13; ib++) {
                    float an = -1.8f + ia * 0.3f;
                    float bn = -1.8f + ib * 0.3f;
                    if (Math.abs(an) + Math.abs(bn) > 2 || Math.round((an + bn) / 0.3f) % 2 != 0) {
                        continue;
                    }
                    float dist = (float) Math.hypot(an - 0.9f, bn - 0.9f);
                    float born = 2 + dist * 3;
                    float det = 12 + dist * 6 + (i++ % 5) * 0.4f;
                    if (p < born) {
                        continue;
                    }
                    Vector3f q = netPoint(an, bn, flatC, fv, sv, 1.9f, feet, 1, 1, age);
                    if (p < det) {
                        float g = smooth((p - born) / 4);
                        FxDraw.billboard(q, 0.45f * g, age * 0.2f + i, FxDraw.FIRE, 0.3f, URA_FIRE, 0.9f * g, true);
                        FxDraw.billboard(q, 0.18f * g, 0, FxDraw.SOFT, 0.9f, WHITE, g, true);
                    } else if (p < det + 12) {
                        float a = (p - det) / 12;
                        FxDraw.billboard(q, 0.6f + a * 1.4f, i, FxDraw.FIRE, 0.25f + a * 0.4f, URA_HOT, 1 - a, true);
                        FxDraw.billboard(q, 1.0f * (1 - a), 0, FxDraw.FLARE, 6 / 32f, URA_FIRE, 1 - a, true);
                    }
                }
            }
            if (p < 6) {
                FxDraw.billboard(netPoint(0.9f, 0.9f, flatC, fv, sv, 1.9f, feet, 1, 1, age), 1.2f, 0, FxDraw.FLARE, 8 / 32f, WHITE, 1 - p / 6, true);
            }
        }
    }

    private static void explosion(Fx f, Vec3 camera, float age, boolean glow) {
        float k = age / 36;
        boolean fuga = f.b > 0.5f;
        float[] hot = fuga ? FIRE : CRIMSON;
        Vector3f c = rel(f.pos, camera);
        float R = f.a;
        if (!glow) {
            for (int i = 0; i < 8; i++) {
                float h = hash(i, 111);
                float ang = h * Mth.TWO_PI;
                float rr = R * (0.3f + 0.8f * smooth(k * 1.5f)) * (0.5f + 0.5f * hash(i, 112));
                Vector3f p = new Vector3f(c).add(Mth.cos(ang) * rr, R * 0.3f + age * 0.08f, Mth.sin(ang) * rr);
                FxDraw.billboard(p, R * (0.8f + 0.8f * smooth(k)), ang + age * 0.01f, FxDraw.SMOKE, 0.3f + 0.6f * k, SMOKE_GREY,
                        0.55f * smooth(age / 6) * (1 - smooth(k)), false);
            }
            return;
        }
        FxDraw.billboard(c, R * (1.2f + 1.8f * smooth(k * 4)), 0, FxDraw.FLARE, 12 / 32f, WHITE, 1 - smooth(k * 3), true);
        for (int i = 0; i < 10; i++) {
            float h = hash(i, 113);
            float ang = h * Mth.TWO_PI;
            float up = hash(i, 114);
            float rr = R * smooth(k * 3) * (0.4f + 0.6f * hash(i, 115));
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * rr, (up - 0.2f) * rr, Mth.sin(ang) * rr);
            FxDraw.billboard(p, R * (0.9f + 0.6f * h), ang, FxDraw.FIRE, smooth(k * 1.3f), hot, 0.55f * (1 - smooth(k * 1.2f)), true);
        }
        FxDraw.disk(new Vector3f(c).add(0, -R * 0.3f, 0), new Vector3f(0, 1, 0), R * (1 + 4 * smooth(k * 2)), FxDraw.RING, 0.08f, hot,
                0.7f * (1 - smooth(k * 2)), true);
        FxDraw.billboard(c, R * 3, 0, FxDraw.SOFT, 0.1f, hot, 0.35f * (1 - smooth(k)), true);
        if (R >= 2) {
            // The big one throws sparks out and up.
            for (int i = 0; i < 30; i++) {
                Vector3f d = new Vector3f(hash(i, 116) - 0.5f, Math.abs(hash(i, 117) - 0.3f) + 0.2f, hash(i, 118) - 0.5f).normalize();
                float sp = 0.25f + 0.35f * hash(i, 119);
                Vector3f p = new Vector3f(c).add(new Vector3f(d).mul(age * sp * R * 0.4f)).add(0, -0.01f * age * age, 0);
                Vector3f tail = new Vector3f(p).sub(new Vector3f(d).mul(0.5f));
                FxDraw.ribbon(tail, p, 0.08f, FxDraw.STREAK, 0.8f, URA_FIRE, 1 - smooth(k * 1.4f), true);
            }
        }
    }

    private static void seal(Fx f, Vec3 camera, float age, float partial, float out, boolean glow) {
        if (!glow) {
            return;
        }
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        Vec3 feet = e.getPosition(partial);
        float h = e.getBbHeight();
        Vector3f waist = rel(feet.add(0, h * 0.55, 0), camera);
        float in = smooth(age / 5);
        // Rikujokoro: six flat rods of yellow light slam into the waist.
        for (int i = 0; i < 6; i++) {
            float ang = i / 6f * Mth.TWO_PI + 0.3f;
            float far = 0.4f + 6 * (1 - in);
            Vector3f dir = new Vector3f(Mth.cos(ang), 0.15f * Mth.sin(i * 2.1f), Mth.sin(ang));
            Vector3f a = new Vector3f(waist).add(new Vector3f(dir).mul(far));
            Vector3f b = new Vector3f(waist).add(new Vector3f(dir).mul(far + 1.6f));
            FxDraw.ribbon(a, b, 0.55f, FxDraw.BEAM, 0.3f, GOLD, 0.9f * out, true);
            FxDraw.ribbon(a, b, 0.2f, FxDraw.BEAM, 0.8f, WHITE, out, true);
        }
        Vector3f ground = rel(feet.add(0, 0.05, 0), camera);
        FxDraw.disk(ground, new Vector3f(0, 1, 0), 3.2f, FxDraw.RING, 0.08f, CRIMSON, 0.8f * out, true);
        FxDraw.disk(ground, new Vector3f(0, 1, 0), 2.2f, FxDraw.FLARE, 6 / 32f, CRIMSON, 0.5f * out, true);
        FxDraw.billboard(waist, 2.2f, 0, FxDraw.SOFT, 0.2f, GOLD, 0.3f * out, true);
    }

    private static void cleaveScan(Fx f, Vec3 camera, float age, float partial, float out, boolean glow) {
        if (!glow) {
            return;
        }
        Entity e = FxAnchor.entity(f.id);
        if (e == null) {
            return;
        }
        Vec3 p = e.getPosition(partial);
        AABB box = e.getBoundingBox().move(p.subtract(e.position())).inflate(0.08);
        float h = (float) box.getYsize();
        for (int i = 0; i < 5; i++) {
            float y = (float) (box.minY + fract(age * 0.06f + i / 5f) * h);
            Vector3f a = rel(new Vec3(box.minX, y, box.minZ), camera);
            Vector3f b = rel(new Vec3(box.maxX, y, box.maxZ), camera);
            Vector3f a2 = rel(new Vec3(box.maxX, y, box.minZ), camera);
            Vector3f b2 = rel(new Vec3(box.minX, y, box.maxZ), camera);
            FxDraw.ribbon(a, b, 0.05f, FxDraw.STREAK, 1f, RED, out, true);
            FxDraw.ribbon(a2, b2, 0.05f, FxDraw.STREAK, 1f, RED, out, true);
        }
        Vector3f top = rel(new Vec3(box.getCenter().x, box.maxY, box.getCenter().z), camera);
        Vector3f bottom = rel(new Vec3(box.getCenter().x, box.minY, box.getCenter().z), camera);
        FxDraw.ribbon(bottom, top, 0.04f, FxDraw.STREAK, 1f, RED, out, true);
    }

    private static void web(Fx f, Vec3 camera, float age, boolean glow) {
        float grow = smooth(age / 6);
        float fade = 1 - smooth((age - 28) / 20);
        Vector3f c = rel(f.pos.add(0, 0.05, 0), camera);
        float R = f.a * grow;
        int spokes = 11;
        float wDark = 0.28f * fade;
        for (int i = 0; i < spokes; i++) {
            float ang = (i + hash(i, 121) * 0.4f) / spokes * Mth.TWO_PI;
            Vector3f tip = new Vector3f(c).add(Mth.cos(ang) * R, 0, Mth.sin(ang) * R);
            groundStrip(c, tip, glow ? 0.4f : wDark, SLIT, glow ? 0 : 1, WHITE, glow ? fade : fade, glow);
        }
        for (int ring = 1; ring <= 5; ring++) {
            float rr = f.a * ring / 5f;
            if (rr > R) {
                break;
            }
            Vector3f last = null;
            for (int i = 0; i <= spokes; i++) {
                float ang = (i + hash(i % spokes, 121) * 0.4f) / spokes * Mth.TWO_PI;
                Vector3f p = new Vector3f(c).add(Mth.cos(ang) * rr, 0, Mth.sin(ang) * rr);
                if (last != null) {
                    groundStrip(last, p, glow ? 0.32f : wDark * 0.8f, SLIT, glow ? 0 : 1, WHITE, fade, glow);
                }
                last = p;
            }
        }
        if (glow && age < 6) {
            FxDraw.billboard(new Vector3f(c).add(0, 0.4f, 0), 3, 0, FxDraw.FLARE, 8 / 32f, WHITE, 1 - age / 6, true);
        }
    }

    private static void fuga(Fx f, Vec3 camera, float age, float partial, float out, boolean glow) {
        if (!glow) {
            return;
        }
        Vec3 d = f.dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.dir.normalize();
        Vec3 posW = f.endAt >= 0 ? f.pos : f.prev.lerp(f.pos, Math.min(1, partial));
        Vector3f tip = rel(posW, camera);
        boolean flying = f.a > 0.5f;
        float t = age;
        if (!flying) {
            float k = smooth(age / 30);
            for (int i = 0; i < 8; i++) {
                float ph = fract(t * 0.07f + hash(i, 131));
                Vector3f p = new Vector3f(tip).add((hash(i, 132) - 0.5f) * 0.6f, ph * 0.9f, (hash(i, 133) - 0.5f) * 0.6f);
                FxDraw.billboard(p, (0.5f + 0.6f * k) * (1 - ph * 0.5f), i, FxDraw.FIRE, ph * 0.6f, FIRE, 0.6f * out, true);
            }
            FxDraw.billboard(tip, 0.8f + 1.2f * k, 0, FxDraw.SOFT, 0.7f, AMBER, 0.8f * out, true);
            // The arrow forming along the aim.
            Vector3f back = new Vector3f(tip).sub(vec(d.scale(1.6 * k)));
            FxDraw.ribbon(back, new Vector3f(tip).add(vec(d.scale(0.4 * k))), 0.25f * k, FxDraw.BEAM, 0.8f, WHITE, out * k, true);
            return;
        }
        Vector3f tail = new Vector3f(tip).sub(vec(d.scale(2.8)));
        FxDraw.ribbon(tail, tip, 1.4f, FxDraw.BEAM, 0.15f, FIRE, 0.7f * out, true);
        FxDraw.ribbon(tail, tip, 0.45f, FxDraw.BEAM, 0.8f, WHITE, out, true);
        for (int i = 0; i < 10; i++) {
            float s = hash(i, 134);
            Vector3f p = new Vector3f(tip).sub(vec(d.scale(s * 4))).add((hash(i, 135) - 0.5f) * 0.6f, (hash(i, 136) - 0.5f) * 0.6f, (hash(i, 137) - 0.5f) * 0.6f);
            FxDraw.billboard(p, 0.7f * (1 - s * 0.6f), i + t, FxDraw.FIRE, s * 0.7f, FIRE, 0.6f * out, true);
        }
        FxDraw.billboard(tip, 1.6f, t * 0.2f, FxDraw.FLARE, 8 / 32f, AMBER, out, true);
    }

    // The Malevolent Shrine: a dark red ground, a temple rising behind him (provisional boxes), embers in the air.
    private static void shrine(Fx f, Vec3 camera, float age, float out, boolean glow) {
        float rise = smooth(age / 30);
        Vec3 face = new Vec3(f.dir.x, 0, f.dir.z);
        face = face.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : face.normalize();
        Vec3 side = face.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 base = f.pos.subtract(face.scale(6)).add(0, -6 * (1 - rise), 0);
        if (glow) {
            Vector3f c = rel(f.pos.add(0, 0.05, 0), camera);
            FxDraw.disk(c, new Vector3f(0, 1, 0), f.a * 2, FxDraw.SOFT, 0.02f, RED, 0.25f * out * rise, true);
            FxDraw.disk(c, new Vector3f(0, 1, 0), f.a * 2, FxDraw.RING, 0.02f, RED, 0.5f * out * rise, true);
            for (int i = 0; i < 40; i++) {
                float ph = fract(age * 0.02f + hash(i, 141));
                float ang = hash(i, 142) * Mth.TWO_PI;
                float r = f.a * Mth.sqrt(hash(i, 143));
                Vector3f p = new Vector3f(c).add(Mth.cos(ang) * r, ph * 8, Mth.sin(ang) * r);
                FxDraw.billboard(p, 0.15f, 0, FxDraw.SOFT, 0.8f, RED, out * Mth.sin(ph * Mth.PI), true);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.position().distanceTo(f.pos) < f.a + 4) {
                ScreenFx.vignette(0x8A0A0A, 0.55f * out, 6);
            }
            return;
        }
        // Platform, four pillars, a two-sided roof and the bone horns.
        box(base, face, side, 0, 0, 0.8f, 9, 6, SHRINE_DARK, camera, out);
        for (int px = -1; px <= 1; px += 2) {
            for (int pz = -1; pz <= 1; pz += 2) {
                box(base.add(side.scale(px * 3.6)).add(face.scale(pz * 2.2)), face, side, 0.8f, 0.8f + 5f, 0.7f, 0.7f, 0.7f, SHRINE_DARK, camera, out);
            }
        }
        box(base, face, side, 5.8f, 6.6f, 0, 10, 7, SHRINE_DARK, camera, out);
        Vec3 ridge = base.add(0, 8.8, 0);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 eave0 = base.add(0, 6.6, 0).add(face.scale(s * 4.2)).add(side.scale(-5.6));
            Vec3 eave1 = base.add(0, 6.6, 0).add(face.scale(s * 4.2)).add(side.scale(5.6));
            FxDraw.quad(rel(ridge.add(side.scale(-6)), camera), rel(ridge.add(side.scale(6)), camera), rel(eave1, camera), rel(eave0, camera),
                    FxDraw.SOLID, 0, SHRINE_DARK, out, false);
        }
        for (int s = -1; s <= 1; s += 2) {
            Vec3 h0 = ridge.add(side.scale(s * 6));
            Vec3 h1 = h0.add(side.scale(s * 1.6)).add(0, 2.4, 0);
            FxDraw.ribbon(rel(h0, camera), rel(h1, camera), 0.6f, FxDraw.SOLID, 0, SHRINE_BONE, out, false);
        }
    }

    // A box on the shrine's own axes: from height y0 to y1, w along the side, dz along the facing.
    private static void box(Vec3 base, Vec3 face, Vec3 side, float y0, float y1, float w, float dz, float wSide, float[] col, Vec3 camera, float op) {
        float hx = wSide > 0 ? wSide * 0.5f : 0.5f;
        float hz = dz > 0 ? dz * 0.5f : 0.5f;
        if (y1 < y0 + 0.01f) {
            y1 = y0 + w;
        }
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            double sx = (i & 1) == 0 ? -hx : hx;
            double sz = (i & 2) == 0 ? -hz : hz;
            double y = (i & 4) == 0 ? y0 : y1;
            c[i] = base.add(side.scale(sx)).add(face.scale(sz)).add(0, y, 0);
        }
        int[][] faces = {{0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}, {4, 5, 7, 6}};
        for (int[] q : faces) {
            FxDraw.quad(rel(c[q[0]], camera), rel(c[q[1]], camera), rel(c[q[2]], camera), rel(c[q[3]], camera), FxDraw.SOLID, 0, col, op, false);
        }
    }

    private static void worldCut(Fx f, Vec3 camera, float age, boolean glow) {
        float k = age / 50;
        float open = k < 0.1f ? smooth(k / 0.1f) : 1 - smooth((k - 0.35f) / 0.65f);
        Vector3f c = rel(f.pos, camera);
        Vector3f up = new Vector3f(0, f.a, 0);
        Vector3f a = new Vector3f(c).sub(up);
        Vector3f b = new Vector3f(c).add(up);
        float w = 2.6f * open;
        Vec3 d = new Vec3(f.dir.x, 0, f.dir.z);
        d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
        Vector3f h = vec(d.scale(f.a));
        if (glow) {
            FxDraw.ribbon(a, b, Math.max(0.2f, w * 1.6f), SLIT, 0, WHITE, Math.min(1, open * 1.3f), true);
            FxDraw.ribbon(new Vector3f(c).sub(h), new Vector3f(c).add(h), Math.max(0.1f, w * 0.6f), SLIT, 0, WHITE, open, true);
            if (age < 4) {
                ScreenFx.flash(0xFFFFFF, 0.6f * (1 - age / 4), 3);
            }
        } else if (w > 0.02f) {
            FxDraw.ribbon(a, b, w * 1.6f, SLIT, 1, WHITE, Math.min(1, open * 1.4f), false);
            FxDraw.ribbon(new Vector3f(c).sub(h), new Vector3f(c).add(h), w * 0.6f, SLIT, 1, WHITE, open, false);
        }
    }

    // ---------- helpers ----------

    private static Vector3f vec(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    private static Vector3f rel(Vec3 world, Vec3 camera) {
        return new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
    }

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
