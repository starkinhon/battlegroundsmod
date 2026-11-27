// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.bb;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.dsk;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rib;
import static com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.hash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.Jj2Net;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The remade Jujutsu SubAlmas of the port's second batch (Todo, Nanami, Itadori; power/powers/sub/jj/Jj2Net, SubNet kinds
// 600-619), drawn as their approved previews draw them (forja/todo-kit/shinjuku-todo.js, forja/nanami-kit/
// nanami-shinjuku.js, forja/itadori-kit/shinjuku.js). Drawn inside DbFx's passes; age in ticks.
final class Jj2Fx {
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final float[] BF_RED = FxDraw.rgb(0xFF2A3A);
    private static final float[] BF_BLACK = {0.02f, 0, 0.03f};
    // Nanami (preview colors): the ruler's ink and paper, the label's gold, the crit's gold, the crack, the tie, the cloth.
    private static final float[] INK = FxDraw.rgb(0x0A0A0C);
    private static final float[] PAPER = FxDraw.rgb(0xF4F1EA);
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] LABEL_3 = FxDraw.rgb(0xFFCF4A);
    private static final float[] CRIT_GOLD = FxDraw.rgb(0xFFE7A0);
    private static final float[] FLASH = FxDraw.rgb(0xFFF2C8);
    private static final float[] CRACK_INK = FxDraw.rgb(0x0D0B0A);
    private static final float[] CRACK_LIP = FxDraw.rgb(0x4A443D);
    private static final float[] TIE = FxDraw.rgb(0xD9C25A);
    private static final float[] CLOTH = FxDraw.rgb(0xF2EFE6);
    private static final float[] CLOTH_DOT = FxDraw.rgb(0x1B1B22);
    // The "7:3" in the Minecraft font (preview's GL), x of each glyph on a 16 x 10 grid.
    private static final String[][] GLYPH = {
            {"#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."},
            {".", "#", ".", ".", ".", "#", "."},
            {".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."}};
    private static final int[] GLYPH_X = {1, 7, 9};
    // The ruler (preview makeMark): length, tilt, the 7:3 point along it.
    private static final float RULER_L = 2.0f;
    private static final float RULER_TILT = -0.38f;

    private static final class Fx {
        final int kind;
        final int id;
        final Vec3 pos;
        final Vec3 dir;
        final float a;
        final float b;
        final float c;
        final double born;
        double endAt = -1;
        Object data;

        Fx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, double now) {
            this.kind = kind;
            this.id = id;
            this.pos = pos;
            this.dir = dir;
            this.a = a;
            this.b = b;
            this.c = c;
            this.born = now;
        }
    }

    private static final List<Fx> SHOTS = new ArrayList<>();
    // The ones that last on one message (a = ticks, 0 ends them), by kind and id.
    private static final Map<Long, Fx> TRACKED = new HashMap<>();

    private Jj2Fx() {
    }

    static boolean handles(int kind) {
        return Jj2Net.has(kind);
    }

    private static boolean timed(int kind) {
        return kind == Jj2Net.NANAMI_RULER || kind == Jj2Net.NANAMI_TIE || kind == Jj2Net.NANAMI_WARM;
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (timed(kind)) {
            long key = ((long) kind << 32) | (id & 0xFFFFFFFFL);
            Fx f = TRACKED.get(key);
            if (a <= 0) {
                if (f != null && f.endAt < 0) {
                    f.endAt = now;
                }
                return;
            }
            TRACKED.put(key, new Fx(kind, id, pos, dir, a, b, c, now));
            return;
        }
        Fx f = new Fx(kind, id, pos, dir, a, b, c, now);
        if (kind == Jj2Net.NANAMI_CRIT) {
            critScreen(f);
        }
        if (kind == Jj2Net.NANAMI_CRACK) {
            f.data = Crack.make(f);
        }
        SHOTS.add(f);
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
            case Jj2Net.TODO_GRAY -> f.a + 4;
            case Jj2Net.NANAMI_CRIT -> 10;
            case Jj2Net.NANAMI_CRACK -> f.b == 0 ? 9 : 24;
            case Jj2Net.NANAMI_CLOTH -> f.a == 1 ? 34 : 14;
            default -> 40;
        };
    }

    private static float fadeOut(Fx f) {
        return f.kind == Jj2Net.NANAMI_WARM ? 16 : 5;
    }

    static void draw(double now, float partial) {
        if (DbFx.pass) {
            SHOTS.removeIf(f -> now - f.born > life(f));
            TRACKED.values().removeIf(f -> now - f.born > f.a + 2 || f.endAt >= 0 && now - f.endAt > fadeOut(f));
        }
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born) + partial;
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case Jj2Net.TODO_GRAY -> todoGray(f, age);
                case Jj2Net.NANAMI_CRIT -> crit(f, age);
                case Jj2Net.NANAMI_CRACK -> ((Crack) f.data).draw(f, age);
                case Jj2Net.NANAMI_CLOTH -> cloth(f, age);
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born) + partial;
            float out = f.endAt < 0 ? 1 - Mth.clamp((age - f.a) / fadeOut(f), 0, 1)
                    : 1 - Mth.clamp((float) (now - f.endAt + partial) / fadeOut(f), 0, 1);
            if (out <= 0) {
                continue;
            }
            switch (f.kind) {
                case Jj2Net.NANAMI_RULER -> ruler(f, age, partial, out);
                case Jj2Net.NANAMI_TIE -> tie(f, age, partial, out);
                case Jj2Net.NANAMI_WARM -> warm(f, age, partial, out);
                default -> {
                }
            }
        }
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static Vector3f safe(Vector3f d) {
        return d.lengthSquared() < 1.0E-6f ? v(0, 0, 1) : new Vector3f(d).normalize();
    }

    private static Vector3f vec(Vec3 d) {
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }

    private static LivingEntity living(int id) {
        Entity e = FxAnchor.entity(id);
        return e instanceof LivingEntity le ? le : null;
    }

    private static float smooth(float x) {
        float t = Mth.clamp(x, 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ---------- Todo ----------

    // The V's contact (preview: phase 'contato'): the screen goes grey in 0.12 s and stays for the slow motion (a ticks),
    // then the color comes back in 0.12 s; small black and red sparks crackle on the fist, more as it squeezes.
    private static void todoGray(Fx f, float age) {
        float in = Mth.clamp(age / 2.4f, 0, 1);
        float out = 1 - Mth.clamp((age - f.a) / 2.4f, 0, 1);
        float g = Math.min(in, out);
        Minecraft mc = Minecraft.getInstance();
        if (g > 0 && mc.cameraEntity != null && mc.cameraEntity.position().distanceTo(f.pos) < 48) {
            // Mode 14 (Saitama's silence) as a plain grey: all grey, no dark edges, no lines.
            ScreenFx.sustainGrade(14, g, 1f, 0f, 0f, f.pos);
        }
        if (age > f.a) {
            return;
        }
        Vector3f p = rel(f.pos);
        float sq = Mth.clamp(age / f.a, 0, 1);
        int frame = (int) (age * 2);
        int n = 1 + (int) (sq * 3);
        for (int i = 0; i < n; i++) {
            int s = frame * 7 + i * 13 + f.id;
            if (hash(s, 7, 61) < 0.3f) {
                continue;
            }
            float ang = hash(s, 1, 61) * Mth.TWO_PI;
            float up = hash(s, 2, 61);
            float r = (0.25f + 0.6f * hash(s, 3, 61)) * (0.6f + 0.8f * sq);
            Vector3f q = new Vector3f(p).add(Mth.cos(ang) * r * 0.7f, (up - 0.3f) * r, Mth.sin(ang) * r * 0.7f);
            float size = 0.05f + 0.05f * hash(s, 4, 61);
            bb(q, size * 1.6f, ang, FxDraw.FLARE, 4 / 32f, BF_RED, 0.9f, true);
            bb(q, size, ang, FxDraw.SOLID, 0, BF_BLACK, 1, false);
        }
    }

    // ---------- Nanami: the ruler ----------

    // A plane to draw flat things on: origin, right, up (both scaled), and the normal to lift along.
    private record Plane(Vector3f o, Vector3f r, Vector3f u, Vector3f n) {
        Vector3f at(float x, float y, float lift) {
            return new Vector3f(o).add(new Vector3f(r).mul(x)).add(new Vector3f(u).mul(y)).add(new Vector3f(n).mul(lift));
        }

        // A rectangle centred at (x, y), size (w, h), turned by tilt round the normal.
        void rect(float x, float y, float w, float h, float tilt, float lift, float[] col, float op) {
            if (op <= 0.002f || w <= 0 || h <= 0) {
                return;
            }
            float cs = Mth.cos(tilt);
            float sn = Mth.sin(tilt);
            float[][] k = {{-w / 2, -h / 2}, {w / 2, -h / 2}, {w / 2, h / 2}, {-w / 2, h / 2}};
            Vector3f[] p = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                float lx = x + k[i][0];
                float ly = y + k[i][1];
                p[i] = at(lx * cs - ly * sn, lx * sn + ly * cs, lift);
            }
            FxDraw.quad(p[0], p[1], p[2], p[3], FxDraw.SOLID, 0, col, op, false);
        }
    }

    // The ruler (preview makeMark, its sizes in blocks): on a body (dir zero: id is the body, facing the camera) or on a
    // wall or the ground (dir is the face's normal, pos the 7:3 point, b its size). It draws in: the bar runs left to
    // right, the ends and the ten parts snap on, the ink bursts on the 7:3 point, "7:3" pops over it, and a dot runs back
    // and forth along it, swelling as it passes the point.
    private static void ruler(Fx f, float age, float partial, float out) {
        if (DbFx.pass) {
            return;
        }
        Vector3f n;
        Vector3f right;
        Vector3f up;
        Vector3f origin;
        float s;
        float p73x = -RULER_L / 2 + RULER_L * 0.7f;
        float px = p73x * Mth.cos(RULER_TILT);
        float py = p73x * Mth.sin(RULER_TILT);
        if (f.dir.lengthSqr() < 1.0E-4) {
            LivingEntity e = living(f.id);
            if (e == null) {
                return;
            }
            s = Mth.clamp(e.getBbHeight() / 1.8f, 1, 2.5f);
            Vector3f feet = rel(e.getPosition(partial));
            n = safe(v(-feet.x, 0, -feet.z));
            right = safe(new Vector3f(UP).cross(n));
            up = new Vector3f(UP);
            origin = new Vector3f(feet).add(new Vector3f(n).mul(e.getBbWidth() * 0.5f + 0.2f)).add(0, e.getBbHeight() * 0.69f, 0);
        } else {
            s = f.b <= 0 ? 1 : f.b;
            n = safe(vec(f.dir));
            Vector3f p = rel(f.pos);
            if (Math.abs(n.y) > 0.9f) {
                Vector3f toCam = safe(v(-p.x, 0, -p.z));
                right = safe(new Vector3f(UP).cross(toCam));
                up = new Vector3f(toCam).negate();
            } else {
                right = safe(new Vector3f(UP).cross(n));
                up = new Vector3f(UP);
            }
            // the 7:3 point sits on pos
            origin = new Vector3f(p).sub(new Vector3f(right).mul(px * s)).sub(new Vector3f(up).mul(py * s));
        }
        Plane pl = new Plane(origin, new Vector3f(right).mul(s), new Vector3f(up).mul(s), n);
        float op = out;
        float k = age / 12f;
        float g = Math.min(1, k * 1.8f);
        float lift = 0.01f / s;
        // the bar, paper rim under the ink, growing from the left end
        float bw = RULER_L * g;
        float bx = -RULER_L / 2 + bw / 2;
        pl.rect(bx, 0, bw + 0.026f, 0.032f + 0.026f, RULER_TILT, lift, PAPER, op);
        pl.rect(bx, 0, bw, 0.032f, RULER_TILT, lift * 2, INK, op);
        if (g > 0.02f) {
            endPart(pl, -RULER_L / 2, 0.04f, 0.34f, lift, op);
        }
        if (g > 0.98f) {
            endPart(pl, RULER_L / 2, 0.04f, 0.34f, lift, op);
        }
        for (int i = 1; i < 10; i++) {
            if (g >= i / 10f) {
                endPart(pl, -RULER_L / 2 + RULER_L * i / 10, 0.02f, i == 5 ? 0.22f : 0.15f, lift, op);
            }
        }
        // the ink splat on the 7:3 point
        float sp = Mth.clamp((k - 0.5f) * 4, 0, 1);
        if (sp > 0) {
            float ss = sp * (1 + 0.5f * Math.max(0, 1 - sp * 1.4f));
            Vector3f c = pl.at(px, py, lift * 3);
            dsk(c, n, 0.31f * ss * s, FxDraw.SPLAT, (f.id & 7) / 8f, INK, op, false);
        }
        // the dot measuring: back and forth, shorter and shorter, locking on the 7:3
        if (g > 0.3f) {
            float tt = age / 20f * 1.6f;
            float swing = Mth.sin(tt * 2.2f) * 0.32f * (0.35f + 0.65f * Math.abs(Mth.sin(tt * 0.55f)));
            float dx = -RULER_L / 2 + RULER_L * Mth.clamp(0.7f + swing, 0.02f, 0.98f);
            float ds = 1 + 0.25f * Math.max(0, 1 - Math.abs(swing) * 12);
            Vector3f c = pl.at(dx * Mth.cos(RULER_TILT), dx * Mth.sin(RULER_TILT), lift * 4);
            dsk(c, n, 0.06f * ds * s, FxDraw.DISC, 1, PAPER, op, false);
            dsk(new Vector3f(c).add(new Vector3f(n).mul(lift)), n, 0.042f * ds * s, FxDraw.DISC, 1, INK, op, false);
        }
        // "7:3" standing over the point (not tilted), thick black outline, the 3 in gold
        float lp = Mth.clamp((k - 0.45f) * 5, 0, 1);
        if (lp > 0) {
            float ls = lp * (1 + 0.35f * Math.max(0, 1 - lp * 1.3f));
            float u = 0.46f / 16 * ls;
            float lx = px + 0.05f;
            float ly = py + 0.36f + 0.025f * Mth.sin(age / 20f * 3);
            for (int layer = 0; layer < 2; layer++) {
                for (int gi = 0; gi < 3; gi++) {
                    String[] rows = GLYPH[gi];
                    for (int j = 0; j < rows.length; j++) {
                        for (int i = 0; i < rows[j].length(); i++) {
                            if (rows[j].charAt(i) != '#') {
                                continue;
                            }
                            // grid cell (cx, cy), y down on a 16 x 10 label
                            float cx = GLYPH_X[gi] + i;
                            float cy = j + 1;
                            if (layer == 0) {
                                // outline: the preview's seven offsets, as two 2x2 squares
                                glyphPx(pl, lx, ly, u, cx + 1, cy + 1, 2, lift * 5, INK, op);
                                glyphPx(pl, lx, ly, u, cx + 2, cy + 2, 2, lift * 5, INK, op);
                            } else {
                                glyphPx(pl, lx, ly, u, cx + 1, cy + 1, 1, lift * 6, gi == 2 ? LABEL_3 : WHITE, op);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void endPart(Plane pl, float x, float w, float h, float lift, float op) {
        float cx = x * Mth.cos(RULER_TILT);
        float cy = x * Mth.sin(RULER_TILT);
        pl.rect(cx, cy, w + 0.026f, h + 0.026f, RULER_TILT, lift, PAPER, op);
        pl.rect(cx, cy, w, h, RULER_TILT, lift * 2, INK, op);
    }

    // One pixel of the label: its centre at grid (cx, cy) of the 16 x 10 label centred at (lx, ly), size cells wide.
    private static void glyphPx(Plane pl, float lx, float ly, float u, float cx, float cy, float size, float lift, float[] col, float op) {
        float x = lx + (cx - size / 2 - 8) * u;
        float y = ly + (5 - (cy - size / 2)) * u;
        pl.rect(x, y, size * u, size * u, 0, lift, col, op);
    }

    // ---------- Nanami: the critical ----------

    // Preview critFx2: no flashing on and off, ONE impact frame and a shake, by distance.
    private static void critScreen(Fx f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null) {
            return;
        }
        double d = mc.cameraEntity.position().distanceTo(f.pos);
        float near = (float) Mth.clamp(1 - d / 24, 0, 1);
        if (near <= 0) {
            return;
        }
        boolean big = f.a == 1;
        if (d < 14) {
            ScreenFx.impactFrame(new int[] {0}, 1, 0xFFFFFF, f.pos, 0);
        }
        ScreenShake.start((big ? 0.9f : 0.5f) * near, big ? 8 : 5);
    }

    // The flash at the point, a gold shock ring opening, and two cuts crossing (white and gold) that shoot out and thin.
    private static void crit(Fx f, float age) {
        boolean big = f.a == 1;
        Vector3f p = rel(f.pos);
        float k = age / (big ? 4.8f : 2.4f);
        if (k < 1) {
            bb(p, (big ? 2.2f : 1.2f) * (0.6f + 0.4f * k), 0, FxDraw.SOFT, 0.5f, FLASH, 1 - k, true);
            bb(p, big ? 1.4f : 0.8f, age * 0.2f, FxDraw.FLARE, 6 / 32f, WHITE, (1 - k) * 0.9f, true);
        }
        float sk = age / 8f;
        if (sk < 1) {
            float r = (big ? 1.1f : 0.6f) * (0.2f + 1.6f * (1 - (1 - sk) * (1 - sk)));
            Vector3f d = safe(vec(f.dir));
            dsk(p, d, r, FxDraw.RING, 0.08f, CRIT_GOLD, (1 - sk) * 0.9f, true);
            bb(p, r * 1.1f, 0, FxDraw.RING, 0.05f, CRIT_GOLD, (1 - sk) * 0.5f, true);
        }
        float ck = age / 4.4f;
        if (ck < 1) {
            float line = (big ? 1.4f : 0.9f) * (1.6f + 0.6f * hash(f.id, 3, 71)) * Math.min(1, ck * 4);
            Vector3f d = safe(vec(f.dir));
            Vector3f side = safe(new Vector3f(d).cross(UP));
            for (int i = 0; i < 2; i++) {
                float tilt = (i == 1 ? 0.7f : -0.6f) + (hash((int) f.born, i, 72) - 0.5f) * 0.3f;
                Vector3f a = new Vector3f(side).mul(Mth.cos(tilt)).add(new Vector3f(UP).mul(Mth.sin(tilt))).mul(line / 2);
                float w = (0.05f + 0.03f * hash((int) f.born, i + 4, 72)) * (1 - ck);
                rib(new Vector3f(p).sub(a), new Vector3f(p).add(a), w, FxDraw.STREAK, 0.9f, i == 1 ? CRIT_GOLD : WHITE, 1 - ck * ck, true);
            }
        }
    }

    // ---------- Nanami: the crack ----------

    // A real crack (preview straightCrack): a broken zigzag path, its width changing and thinning, thin branches; dark
    // ink over a lighter chipped lip, no glow. It opens from pos along dir; a ground crack is eaten by the sinking chain.
    private static final class Crack {
        final List<Vector3f[]> lip = new ArrayList<>();
        final List<Vector3f[]> ink = new ArrayList<>();
        // per strip: where along the main crack (0-1) it starts and ends opening
        final List<float[]> span = new ArrayList<>();

        static Crack make(Fx f) {
            Crack c = new Crack();
            Vector3f a = new Vector3f(0, 0, 0);
            Vector3f b = vec(f.dir);
            float len = b.length();
            Vector3f nrm = vec(Vec3.atLowerCornerOf(Direction.from3DDataValue((int) f.a).getNormal()));
            Vector3f dir = safe(b);
            Vector3f side = safe(new Vector3f(dir).cross(nrm));
            int[] seed = {f.id * 31 + 7};
            List<Vector3f> main = jag(a, b, 6, 0.22f, side, seed);
            c.add(ribbon(main, 0.11f, nrm, seed, 0), ribbon(main, 0.06f, nrm, seed, 0.004f), 0, 1);
            int nb = Math.round(5 + len * 0.9f);
            for (int i = 0; i < nb; i++) {
                float u = 0.08f + 0.84f * rnd(seed);
                Vector3f o = main.get((int) (u * (main.size() - 1)));
                float sgn = rnd(seed) < 0.5f ? 1 : -1;
                Vector3f bd = safe(new Vector3f(dir).mul(0.6f + 0.6f * rnd(seed)).add(new Vector3f(side).mul(sgn * (0.6f + 0.9f * rnd(seed)))));
                float bl = (0.25f + 0.75f * rnd(seed)) * Math.min(2.2f, 0.15f * len + 0.5f);
                List<Vector3f> bp = jag(o, new Vector3f(o).add(new Vector3f(bd).mul(bl)), 4, 0.35f, safe(new Vector3f(bd).cross(nrm)), seed);
                c.add(null, ribbon(bp, 0.018f + 0.016f * rnd(seed), nrm, seed, 0.006f), u, Math.min(1, u + 0.15f + 0.2f * rnd(seed)));
                if (rnd(seed) < 0.45f) {
                    Vector3f q = bp.get((int) (bp.size() * (0.4f + 0.4f * rnd(seed))));
                    Vector3f td = safe(new Vector3f(bd).add(new Vector3f(side).mul(-sgn * 1.2f)));
                    List<Vector3f> tp = jag(q, new Vector3f(q).add(new Vector3f(td).mul(bl * 0.45f)), 3, 0.4f, safe(new Vector3f(td).cross(nrm)), seed);
                    c.add(null, ribbon(tp, 0.012f, nrm, seed, 0.007f), Math.min(1, u + 0.1f), Math.min(1, u + 0.3f));
                }
            }
            return c;
        }

        void add(Vector3f[] lipStrip, Vector3f[] inkStrip, float u0, float u1) {
            lip.add(lipStrip);
            ink.add(inkStrip);
            span.add(new float[] {u0, u1});
        }

        void draw(Fx f, float age) {
            if (DbFx.pass) {
                return;
            }
            boolean wall = f.b == 0;
            // opens in 5 ticks (wall) or 7 (ground); the wall's falls away with the blocks, the ground's is eaten by
            // the chain (8 ticks after the punch, one block a tick)
            float k = smooth(age / (wall ? 5 : 7));
            float len = (float) f.dir.length();
            float cut = wall ? 0 : Mth.clamp((age - 8) / Math.max(1, len), 0, 1);
            float op = wall ? 1 - Mth.clamp((age - 7) / 2, 0, 1) : 1 - Mth.clamp((age - 20) / 4, 0, 1);
            if (op <= 0) {
                return;
            }
            Vector3f base = rel(f.pos);
            for (int i = 0; i < ink.size(); i++) {
                float[] sp = span.get(i);
                float g = Mth.clamp((k - sp[0]) / Math.max(0.01f, sp[1] - sp[0]), 0, 1);
                if (g <= 0) {
                    continue;
                }
                if (lip.get(i) != null) {
                    strip(base, lip.get(i), g, cut, CRACK_LIP, op);
                }
                float from = i == 0 ? cut : (sp[0] < cut ? 1 : 0);
                if (from < 1) {
                    strip(base, ink.get(i), g, i == 0 ? cut : 0, CRACK_INK, op);
                }
            }
        }

        // A strip as pairs of points (left, right per step), drawn up to g of its length, from cut on.
        private static void strip(Vector3f base, Vector3f[] pts, float g, float cut, float[] col, float op) {
            int segs = pts.length / 2 - 1;
            int end = Math.round(segs * g);
            int start = Math.round(segs * cut);
            for (int i = start; i < end; i++) {
                Vector3f l0 = new Vector3f(base).add(pts[i * 2]);
                Vector3f r0 = new Vector3f(base).add(pts[i * 2 + 1]);
                Vector3f l1 = new Vector3f(base).add(pts[i * 2 + 2]);
                Vector3f r1 = new Vector3f(base).add(pts[i * 2 + 3]);
                FxDraw.quad(l0, r0, r1, l1, FxDraw.SOLID, 0, col, op, false);
            }
        }

        private static float rnd(int[] seed) {
            seed[0]++;
            return hash(seed[0], 11, 73);
        }

        private static List<Vector3f> jag(Vector3f a, Vector3f b, int levels, float rough, Vector3f side, int[] seed) {
            List<Vector3f> pts = new ArrayList<>(List.of(new Vector3f(a), new Vector3f(b)));
            for (int l = 0; l < levels; l++) {
                List<Vector3f> n = new ArrayList<>();
                n.add(pts.get(0));
                for (int i = 1; i < pts.size(); i++) {
                    Vector3f p = pts.get(i - 1);
                    Vector3f q = pts.get(i);
                    float len = p.distance(q);
                    n.add(new Vector3f(p).lerp(q, 0.35f + 0.3f * rnd(seed)).add(new Vector3f(side).mul((rnd(seed) - 0.5f) * len * rough)));
                    n.add(q);
                }
                pts = n;
            }
            return pts;
        }

        private static Vector3f[] ribbon(List<Vector3f> pts, float w0, Vector3f nrm, int[] seed, float lift) {
            int n = pts.size();
            Vector3f[] out = new Vector3f[n * 2];
            for (int i = 0; i < n; i++) {
                Vector3f p = pts.get(i);
                Vector3f d = safe(new Vector3f(pts.get(Math.min(n - 1, i + 1))).sub(pts.get(Math.max(0, i - 1))));
                Vector3f sd = safe(new Vector3f(d).cross(nrm));
                float u = i / (float) (n - 1);
                float w = w0 * (float) Math.sqrt(1 - u) * (0.45f + 0.9f * rnd(seed)) * (i == 0 ? 0.6f : 1) + 0.004f;
                Vector3f up = new Vector3f(nrm).mul(0.02f + lift);
                out[i * 2] = new Vector3f(p).add(new Vector3f(sd).mul(w * (0.3f + 0.7f * rnd(seed)))).add(up);
                out[i * 2 + 1] = new Vector3f(p).add(new Vector3f(sd).mul(-w * (0.3f + 0.7f * rnd(seed)))).add(up);
            }
            return out;
        }
    }

    // ---------- Nanami: the cloth, the tie, the warm light ----------

    // The cleaver's cloth (preview lamina2): a snap sheds a few short frayed strips; on the last cut (a = 1) the cloth
    // comes loose whole and flies unrolling (a long strip waving), with the strips.
    private static void cloth(Fx f, float age) {
        if (DbFx.pass) {
            return;
        }
        boolean loose = f.a == 1;
        Vector3f hand = rel(f.pos);
        Vector3f fwd = safe(vec(f.dir));
        int n = loose ? 6 : 3;
        float t = age / 20f;
        for (int i = 0; i < n; i++) {
            int s = f.id * 17 + (int) f.born * 5 + i;
            float lifeT = loose ? 1.2f : 0.6f;
            float k = t / lifeT;
            if (k >= 1) {
                continue;
            }
            Vector3f vel = new Vector3f(fwd).mul(1.5f + 2 * hash(s, 1, 74)).add((hash(s, 2, 74) - 0.5f) * 3, 1 + 2 * hash(s, 3, 74), (hash(s, 4, 74) - 0.5f) * 3);
            Vector3f p = new Vector3f(hand).add(new Vector3f(vel).mul(t)).add(0, -4.5f * t * t, 0);
            float len = 0.15f + 0.2f * hash(s, 5, 74);
            strip(p, len, 0.035f, t * 7 + i, s, 0.04f, 1 - k * k);
        }
        if (loose) {
            float k = t / 1.7f;
            if (k < 1) {
                // the whole cloth: unrolls as it flies forward and up, then drops
                Vector3f p = new Vector3f(hand).add(new Vector3f(fwd).mul(3.2f * t)).add(0, 2.4f * t - 3.4f * t * t, 0);
                float len = 0.3f + 1.3f * smooth(t / 0.5f);
                strip(p, len, 0.07f, t * 3, f.id, 0.14f, 1 - k * k * k);
            }
        }
    }

    // One cloth strip: len long, w wide, starting at p, its direction spun by spin, waving (amp).
    private static void strip(Vector3f p, float len, float w, float spin, int seed, float amp, float op) {
        float yaw = hash(seed, 7, 74) * Mth.TWO_PI + spin;
        float pitch = (hash(seed, 8, 74) - 0.5f) * 2 + spin * 0.6f;
        Vector3f d = v(Mth.cos(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.sin(yaw) * Mth.cos(pitch));
        Vector3f side = safe(new Vector3f(d).cross(UP)).mul(w / 2);
        Vector3f bend = safe(new Vector3f(d).cross(side));
        int segs = 10;
        Vector3f prevL = null;
        Vector3f prevR = null;
        for (int i = 0; i <= segs; i++) {
            float u = i / (float) segs;
            Vector3f c = new Vector3f(p).add(new Vector3f(d).mul(len * u)).add(new Vector3f(bend).mul(Mth.sin(spin * 4 + u * 7 + seed) * amp * u));
            Vector3f l = new Vector3f(c).add(side);
            Vector3f r = new Vector3f(c).sub(side);
            if (prevL != null) {
                FxDraw.quad(prevL, prevR, r, l, FxDraw.SOLID, 0, CLOTH, op, false);
                if (i % 3 == 1 && w > 0.05f) {
                    Vector3f m = new Vector3f(prevL).add(r).mul(0.5f);
                    FxDraw.quad(new Vector3f(m).add(new Vector3f(side).mul(0.3f)).add(new Vector3f(d).mul(0.012f)),
                            new Vector3f(m).sub(new Vector3f(side).mul(0.3f)).add(new Vector3f(d).mul(0.012f)),
                            new Vector3f(m).sub(new Vector3f(side).mul(0.3f)).sub(new Vector3f(d).mul(0.012f)),
                            new Vector3f(m).add(new Vector3f(side).mul(0.3f)).sub(new Vector3f(d).mul(0.012f)), FxDraw.SOLID, 0, CLOTH_DOT, op, false);
                }
            }
            prevL = l;
            prevR = r;
        }
    }

    // Overtime's tie (preview tieWrap): pulled from the collar and wound round his right fist (3.2 turns) in 0.65 s;
    // stays wound while it lasts. Not drawn when his arm isn't on screen (first person).
    private static void tie(Fx f, float age, float partial, float out) {
        if (DbFx.pass) {
            return;
        }
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Vec3 fistW = HandTrack.fist(e, true, partial);
        if (fistW == null) {
            return;
        }
        float yaw = Mth.lerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwdB = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 rightB = new Vec3(-fwdB.z, 0, fwdB.x);
        double sc = e.getBbHeight() / 1.8;
        Vec3 feet = e.getPosition(partial);
        Vec3 shoulder = feet.add(rightB.scale(-0.37 * sc)).add(0, 1.4 * sc, 0);
        Vec3 collar = feet.add(fwdB.scale(0.14 * sc)).add(0, 1.42 * sc, 0);
        Vector3f fist = rel(fistW);
        Vector3f ax = safe(vec(fistW.subtract(shoulder)));
        Vector3f hand = new Vector3f(fist).sub(new Vector3f(ax).mul(0.12f));
        Vector3f s1 = safe(new Vector3f(ax).cross(Math.abs(ax.y) > 0.9f ? v(1, 0, 0) : UP));
        Vector3f s2 = new Vector3f(ax).cross(s1);
        float k = Mth.clamp((age - 5) / 13f, 0, 1);
        int nn = 60;
        float r = 0.17f * (float) sc;
        float wHalf = 0.04f * (float) sc;
        Vector3f prevA = null;
        Vector3f prevB = null;
        Vector3f from = rel(collar);
        for (int i = 0; i <= nn; i++) {
            float u = i / (float) nn;
            Vector3f p;
            if (u < 0.3f) {
                if (k >= 1) {
                    prevA = null;
                    continue;
                }
                p = new Vector3f(from).lerp(hand, u / 0.3f * Math.min(1, k * 2 + 0.2f));
            } else {
                float wu = (u - 0.3f) / 0.7f * k;
                float a = wu * Mth.TWO_PI * 3.2f;
                float h = (wu - 0.5f) * 0.24f + 0.02f * Mth.sin(i * 1.7f);
                p = new Vector3f(hand).add(new Vector3f(s1).mul(Mth.cos(a) * r)).add(new Vector3f(s2).mul(Mth.sin(a) * r)).add(new Vector3f(ax).mul(h * (float) sc));
            }
            Vector3f qa = new Vector3f(p).add(new Vector3f(ax).mul(wHalf));
            Vector3f qb = new Vector3f(p).sub(new Vector3f(ax).mul(wHalf));
            if (prevA != null) {
                FxDraw.quad(prevA, prevB, qb, qa, FxDraw.SOLID, 0, TIE, out, false);
            }
            prevA = qa;
            prevB = qb;
        }
    }

    // Overtime's light: the warm end of the afternoon (mode 19), for whoever is near him, coming in after the tie.
    private static void warm(Fx f, float age, float partial, float out) {
        if (!DbFx.pass) {
            return;
        }
        Entity e = FxAnchor.entity(f.id);
        Minecraft mc = Minecraft.getInstance();
        if (e == null || mc.cameraEntity == null || mc.cameraEntity.distanceTo(e) > 24) {
            return;
        }
        float g = smooth((age - 12) / 10f) * out;
        ScreenFx.sustainGrade(19, g, 0f, 0f, 0f, e.getPosition(partial));
    }
}
