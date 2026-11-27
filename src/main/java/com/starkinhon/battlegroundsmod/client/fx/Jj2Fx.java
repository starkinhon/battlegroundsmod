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
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.ItadoriWeb;
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
        // how a timed one ended (the c of its last message)
        float result;
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
        return kind == Jj2Net.NANAMI_RULER || kind == Jj2Net.NANAMI_TIE || kind == Jj2Net.NANAMI_WARM || kind == Jj2Net.ITA_AURA
                || kind == Jj2Net.ITA_RING || kind == Jj2Net.ITA_HAND_SCISSORS || kind == Jj2Net.ITA_SOUL;
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (timed(kind)) {
            long key = ((long) kind << 32) | (id & 0xFFFFFFFFL);
            Fx f = TRACKED.get(key);
            if (a <= 0) {
                if (f != null && f.endAt < 0) {
                    f.endAt = now;
                    f.result = c;
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
        if (kind == Jj2Net.ITA_WEB) {
            f.data = webStrokes(f);
        }
        if (kind == Jj2Net.ITA_CUT) {
            cutStrokes(f);
        }
        if (kind == Jj2Net.ITA_CLIVAR) {
            clivarStrokes(f);
        }
        SHOTS.add(f);
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
            case Jj2Net.TODO_GRAY -> f.a + 4;
            case Jj2Net.NANAMI_CRIT -> 10;
            case Jj2Net.NANAMI_CRACK -> f.b == 0 ? 9 : 24;
            case Jj2Net.NANAMI_CLOTH -> f.a == 1 ? 34 : 14;
            case Jj2Net.ITA_ECHO -> 12;
            case Jj2Net.ITA_WEB -> 44;
            case Jj2Net.ITA_CUT -> 10;
            case Jj2Net.ITA_CLIVAR -> 16;
            case Jj2Net.ITA_LIDS -> 34;
            case Jj2Net.ITA_RIP -> 22;
            case Jj2Net.ITA_VEINS -> 24;
            default -> 40;
        };
    }

    // How long a timed one lasts on its own (the ring waits out its window).
    private static float span(Fx f) {
        return f.kind == Jj2Net.ITA_RING ? f.a + f.b + 8 : f.a;
    }

    private static float fadeOut(Fx f) {
        return f.kind == Jj2Net.NANAMI_WARM ? 16 : f.kind == Jj2Net.ITA_AURA ? 12 : f.kind == Jj2Net.ITA_RING ? 8 : 5;
    }

    static void draw(double now, float partial) {
        if (DbFx.pass) {
            SHOTS.removeIf(f -> now - f.born > life(f));
            TRACKED.values().removeIf(f -> now - f.born > span(f) + fadeOut(f) + 2 || f.endAt >= 0 && now - f.endAt > fadeOut(f));
            if (!FISTS.isEmpty() && TRACKED.values().stream().noneMatch(f -> f.kind == Jj2Net.ITA_AURA)) {
                FISTS.clear();
            }
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
                case Jj2Net.ITA_ECHO -> echo(f, age);
                case Jj2Net.ITA_WEB -> web(f, age);
                case Jj2Net.ITA_CUT -> cutFx(f, age);
                case Jj2Net.ITA_CLIVAR -> clivarFx(f, age);
                case Jj2Net.ITA_LIDS -> lids(f, age, partial);
                case Jj2Net.ITA_RIP -> rip(f, age, partial);
                case Jj2Net.ITA_VEINS -> veins(f, age, partial);
                default -> {
                }
            }
        }
        for (Fx f : TRACKED.values()) {
            float age = (float) (now - f.born) + partial;
            float out = f.endAt < 0 ? 1 - Mth.clamp((age - span(f)) / fadeOut(f), 0, 1)
                    : 1 - Mth.clamp((float) (now - f.endAt + partial) / fadeOut(f), 0, 1);
            if (out <= 0) {
                continue;
            }
            switch (f.kind) {
                case Jj2Net.NANAMI_RULER -> ruler(f, age, partial, out);
                case Jj2Net.NANAMI_TIE -> tie(f, age, partial, out);
                case Jj2Net.NANAMI_WARM -> warm(f, age, partial, out);
                case Jj2Net.ITA_AURA -> aura(f, now, partial, out);
                case Jj2Net.ITA_RING -> ring(f, now, age, partial, out);
                case Jj2Net.ITA_HAND_SCISSORS -> handScissors(f, age, partial, out);
                case Jj2Net.ITA_SOUL -> soul(f, age, partial, out);
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

    // ---------- Itadori ----------

    private static final float[] IT_BLUE = {0.3f, 0.66f, 1f};
    private static final float[] IT_CORE = {0.75f, 0.9f, 1f};
    private static final float[] IT_DEEP = {0.04f, 0.1f, 0.35f};
    private static final float[] IT_SHOCK = FxDraw.rgb(0x9CC6FF);
    private static final float[] IT_FLASH = FxDraw.rgb(0xCFE4FF);
    private static final float[] IT_CLIVAR = FxDraw.rgb(0x6FA8FF);
    private static final float[] RING_RED = FxDraw.rgb(0xFF2A3A);
    private static final float[] GHOST = {0.78f, 0.9f, 1f};
    private static final float[] VEIN = {1f, 0.25f, 0.22f};
    private static final float[] VEIN_HALO = {1f, 0.1f, 0.06f};
    private static final float[] SC_INK = FxDraw.rgb(0x07070A);
    private static final float[] SC_WHITE = FxDraw.rgb(0xF6F5EF);
    // Where each Itadori's fists were lately (game time, x, y, z; right then left), for the lagging aura.
    private static final Map<Integer, java.util.ArrayDeque<double[]>> FISTS = new HashMap<>();

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

    private static boolean ownFirstPerson(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return e == mc.cameraEntity && mc.options.getCameraType().isFirstPerson();
    }

    private static float easeOut3(float x) {
        float t = Mth.clamp(x, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // The inverse of easeOut3: when (0-1 of the time) it reaches y.
    private static float easeOut3Inv(float y) {
        return 1 - (float) Math.cbrt(1 - Mth.clamp(y, 0, 1));
    }

    // The Divergent mode (preview cursedAura + the echo shells): on each fist the cursed energy, dense, blue inside with a
    // black edge, in four shells each later than the one before (0.02 to 0.14 s), so it trails the fist as he punches.
    private static void aura(Fx f, double now, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || ownFirstPerson(e)) {
            return;
        }
        double t = now + partial;
        Body b = body(e, partial);
        Vec3 r = HandTrack.fist(e, true, partial);
        Vec3 l = HandTrack.fist(e, false, partial);
        if (r == null) {
            r = b.point(0.5, 0.3, 1.28);
        }
        if (l == null) {
            l = b.point(0.5, -0.3, 1.28);
        }
        java.util.ArrayDeque<double[]> hist = FISTS.computeIfAbsent(f.id, k -> new java.util.ArrayDeque<>());
        if (DbFx.pass) {
            hist.addLast(new double[] {t, r.x, r.y, r.z, l.x, l.y, l.z});
            while (hist.size() > 2 && hist.peekFirst()[0] < t - 4) {
                hist.removeFirst();
            }
        }
        float in = smooth((float) (t - f.born) / 7f) * out;
        if (in <= 0) {
            return;
        }
        for (int side = 0; side < 2; side++) {
            for (int i = 3; i >= 0; i--) {
                double lag = 0.4 + 0.8 * i;
                Vec3 p = lagged(hist, t - lag, side);
                if (p == null) {
                    p = side == 0 ? r : l;
                }
                Vector3f c = rel(p).add(0, 0.05f, 0);
                float s = (0.3f + 0.035f * i) * (1 + 0.05f * Mth.sin((float) t * 0.65f + i * 2.3f + side));
                float a = in * (1 - i * 0.18f);
                // the black edge (alpha pass), the blue body, the bright core (lead shell only)
                bb(c, s * 1.35f, 0, FxDraw.SOFT, 0.55f, BF_BLACK, 0.55f * a, false);
                bb(c, s * 1.05f, (float) t * 0.05f + i, FxDraw.SOFT, 0.35f, IT_DEEP, 0.7f * a, false);
                bb(c, s, (float) t * 0.1f + i, FxDraw.FIRE, 0.3f, IT_BLUE, 0.8f * a, true);
                if (i == 0) {
                    bb(c, s * 0.55f, 0, FxDraw.SOFT, 0.7f, IT_CORE, 0.9f * a, true);
                    // tongues rising off it
                    for (int k = 0; k < 3; k++) {
                        float ph = DbFx.fract((float) t * (0.09f + 0.03f * k) + k * 0.37f + side * 0.21f);
                        Vector3f q = new Vector3f(c).add((hash(f.id, k + side * 5, 81) - 0.5f) * 0.12f, ph * 0.32f, (hash(f.id, k + side * 5, 82) - 0.5f) * 0.12f);
                        bb(q, 0.18f * (1 - ph) + 0.05f, k, FxDraw.FIRE, 0.25f + ph * 0.5f, IT_BLUE, 0.7f * a * (1 - ph), true);
                        bb(q, 0.22f * (1 - ph) + 0.05f, k, FxDraw.SOFT, 0.6f, BF_BLACK, 0.35f * a * (1 - ph), false);
                    }
                }
            }
        }
    }

    private static Vec3 lagged(java.util.ArrayDeque<double[]> hist, double t, int side) {
        double[] best = null;
        for (double[] h : hist) {
            if (h[0] <= t) {
                best = h;
            } else {
                if (best == null) {
                    best = h;
                }
                break;
            }
        }
        if (best == null) {
            return null;
        }
        int o = side == 0 ? 1 : 4;
        return new Vec3(best[o], best[o + 1], best[o + 2]);
    }

    // The Black Flash ring of the 5th punch (preview TIMING2): round the target's chest, facing the camera, closing from
    // 1.1 to 0.22 in a ticks; a thick black outline that grows as it closes, a fading trail, four marks on the target,
    // red in the window; it bursts on a hit and fades on a miss.
    private static void ring(Fx f, double now, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        Vec3 cw = e != null ? e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0) : f.pos;
        Vector3f c = rel(cw);
        Vector3f n = safe(new Vector3f(c).negate());
        float k = Mth.clamp(age / f.a, 0, 1.3f);
        boolean hit = f.endAt >= 0 && f.result == 1;
        if (f.endAt >= 0) {
            float ek = (float) (now - f.endAt + partial) / 8f;
            if (hit) {
                float r = 0.24f + 2.2f * easeOut3(ek);
                dsk(c, n, r, FxDraw.RING, 0.06f, RING_RED, (1 - ek) * 0.9f, true);
                dsk(c, n, r * 1.04f, FxDraw.RING, 0.1f, BF_BLACK, (1 - ek) * 0.8f, false);
            }
            return;
        }
        float rad = (0.2f + 0.8f * (1 - Math.min(1, k))) * 1.1f;
        boolean window = Math.abs(age - f.a) <= f.b;
        float[] col = window ? RING_RED : WHITE;
        float thick = 0.12f + 0.2f * Math.min(1, k);
        dsk(c, n, rad * 1.06f, FxDraw.RING, thick, BF_BLACK, 0.85f * out, false);
        dsk(c, n, rad, FxDraw.RING, thick * 0.45f, col, out, true);
        for (int i = 1; i <= 3; i++) {
            float tr = rad + 0.08f * i;
            dsk(c, n, tr, FxDraw.RING, 0.04f, col, 0.3f / i * out, true);
        }
        Vector3f right = safe(new Vector3f(UP).cross(n));
        Vector3f up = new Vector3f(n).cross(right);
        for (int i = 0; i < 4; i++) {
            float ang = i * Mth.HALF_PI + Mth.PI / 4;
            Vector3f d = new Vector3f(right).mul(Mth.cos(ang)).add(new Vector3f(up).mul(Mth.sin(ang)));
            Vector3f a0 = new Vector3f(c).add(new Vector3f(d).mul(0.2f));
            Vector3f a1 = new Vector3f(c).add(new Vector3f(d).mul(0.32f));
            rib(a0, a1, 0.05f, FxDraw.SOLID, 0, BF_BLACK, out, false);
            rib(a0, a1, 0.025f, FxDraw.STREAK, 0.9f, col, out, true);
        }
    }

    // The echo landing: a pale blue shock ring, a flash, and specks thrown on through.
    private static void echo(Fx f, float age) {
        boolean big = f.a == 1;
        Vector3f c = rel(f.pos);
        Vector3f d = safe(vec(f.dir));
        float k = age / 6;
        if (k < 1) {
            dsk(c, d, (big ? 0.9f : 0.45f) * (0.3f + easeOut3(k)), FxDraw.RING, 0.08f, IT_SHOCK, (1 - k) * 0.9f, true);
            bb(c, (big ? 1.1f : 0.7f) * (1 - k * 0.4f), 0, FxDraw.SOFT, 0.5f, IT_FLASH, (1 - k) * 0.8f, true);
        }
        float t = age / 20f;
        for (int i = 0; i < 8; i++) {
            float lifeK = age / 12;
            if (lifeK >= 1) {
                break;
            }
            Vector3f v = new Vector3f(d).mul(2 + 3 * hash((int) f.born, i, 83)).add((hash((int) f.born, i, 84) - 0.5f) * 2, hash((int) f.born, i, 85) * 2, (hash((int) f.born, i, 86) - 0.5f) * 2);
            Vector3f p = new Vector3f(c).add(new Vector3f(v).mul(t)).add(0, -4.9f * t * t, 0);
            bb(p, 0.05f + 0.04f * hash((int) f.born, i, 87), i, FxDraw.SOLID, 0, IT_BLUE, 1 - lifeK, false);
        }
    }

    // ---------- the scissors and the web ----------

    // A pair of scissors at p pointing along t (o = the side the blades open to): two blades opening by open (radians),
    // white inside a black outline, and two ring handles behind the pivot (the old Corte de Tesoura's, his own).
    private static void scissors(Vector3f p, Vector3f t, Vector3f o, float s, float open, float op) {
        if (s <= 0.01f || DbFx.pass) {
            return;
        }
        Vector3f toCam = safe(new Vector3f(p).negate());
        Vector3f front = new Vector3f(p).add(new Vector3f(toCam).mul(0.012f));
        float w = s / 0.25f;
        for (int side = -1; side <= 1; side += 2) {
            Vector3f d = safe(new Vector3f(t).add(new Vector3f(o).mul(side * open)));
            Vector3f tip = new Vector3f(p).add(new Vector3f(d).mul(s));
            Vector3f mid = new Vector3f(p).add(new Vector3f(d).mul(s * 0.45f));
            FxDraw.ribbon(new Vector3f(p).sub(new Vector3f(d).mul(0.03f)), tip, 0.075f * w, FxDraw.SOLID, 0, SC_INK, op, false);
            FxDraw.ribbon(front, new Vector3f(mid).add(new Vector3f(toCam).mul(0.012f)), 0.042f * w, FxDraw.SOLID, 0, SC_WHITE, op, false);
            FxDraw.ribbon(new Vector3f(mid).add(new Vector3f(toCam).mul(0.012f)),
                    new Vector3f(tip).sub(new Vector3f(d).mul(0.04f)).add(new Vector3f(toCam).mul(0.012f)), 0.022f * w, FxDraw.SOLID, 0, SC_WHITE, op,
                    false);
            Vector3f hd = safe(new Vector3f(t).negate().sub(new Vector3f(o).mul(side * (0.6f + open))));
            Vector3f handle = new Vector3f(p).add(new Vector3f(hd).mul(s * 0.42f));
            FxDraw.disk(handle, toCam, s * 0.62f, FxDraw.RING, 0.32f, SC_INK, op, false);
            FxDraw.disk(new Vector3f(handle).add(new Vector3f(toCam).mul(0.012f)), toCam, s * 0.5f, FxDraw.RING, 0.22f, SC_WHITE, op, false);
        }
        FxDraw.billboard(new Vector3f(p).add(new Vector3f(toCam).mul(0.02f)), 0.05f * w, 0, FxDraw.SOFT, 0.9f, SC_INK, op, false);
    }

    // The web (preview SCENES.teia, in ticks from the palm): thin faint guide threads spread out in 8 ticks; the scissors
    // run out along the spokes in 9 (each spoke a cut behind them, each ring cutting whole when they reach it); 2 later
    // it bursts, glows and tears away.
    private static final int WEB_RUN = 8;
    private static final int WEB_SC = 9;
    private static final int WEB_BURST = 19;

    private static List<ItadoriWeb.Line> webStrokes(Fx f) {
        List<ItadoriWeb.Line> lines = ItadoriWeb.lines(f.id);
        float y = 0.035f;
        Vec3 up = new Vec3(0, 1, 0);
        float spread = ItadoriWeb.R * 1.05f;
        for (ItadoriWeb.Line l : lines) {
            Vec3 a = f.pos.add(l.ax(), y, l.az());
            Vec3 b = f.pos.add(l.bx(), y, l.bz());
            int seed = f.id * 31 + l.k() * 7;
            float g0 = easeOut3Inv(l.r0() / spread) * WEB_RUN;
            float g1 = l.spoke() ? easeOut3Inv(l.r1() / spread) * WEB_RUN : g0 + 1.5f;
            SukunaCutsRenderer.stroke(a, b, l.thick(), up, g0, Math.max(0.5f, g1 - g0), WEB_BURST - g0, 1, 0.8f, 0.7f, 0, seed);
            if (l.spoke()) {
                float run = easeOut3Inv(Math.min(1, l.r1() / ItadoriWeb.R)) * WEB_SC;
                SukunaCutsRenderer.stroke(a, b, l.thick(), up, WEB_RUN, Math.max(1, run), WEB_BURST + 3 - WEB_RUN, 18, 0, 1, 1.2f, seed + 1);
            } else {
                float at = WEB_RUN + easeOut3Inv(Math.min(1, l.r0() / ItadoriWeb.R)) * WEB_SC;
                SukunaCutsRenderer.stroke(a, b, l.thick(), up, at, 1.2f, WEB_BURST + 3 - at, 18, 0, 1, 1.6f, seed + 1);
            }
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static void web(Fx f, float age) {
        List<ItadoriWeb.Line> lines = (List<ItadoriWeb.Line>) f.data;
        Vector3f c = rel(f.pos);
        if (age < 3) {
            bb(new Vector3f(c).add(0, 0.1f, 0), 1.4f * (1 - age / 3), 0, FxDraw.SOFT, 0.5f, IT_FLASH, 1 - age / 3, true);
            dsk(new Vector3f(c).add(0, 0.05f, 0), UP, 0.6f + age * 0.4f, FxDraw.RING, 0.08f, IT_CLIVAR, 1 - age / 3, true);
        }
        float sc = (age - WEB_RUN) / WEB_SC;
        if (sc >= 0 && age <= WEB_BURST) {
            float rNow = easeOut3(sc) * ItadoriWeb.R;
            for (ItadoriWeb.Line l : lines) {
                if (!l.spoke() || rNow > l.r1() + 0.3f) {
                    continue;
                }
                float len = (float) Math.hypot(l.bx(), l.bz());
                float dx = l.bx() / len;
                float dz = l.bz() / len;
                float d = Math.max(0.35f, Math.min(l.r1(), rNow));
                Vector3f p = new Vector3f(c).add(dx * d, 0.32f, dz * d);
                float open = 0.6f * Math.abs(Mth.sin(age * 1.7f + l.k()));
                scissors(p, v(dx, 0, dz), UP, 0.32f, 0.1f + open, 1);
            }
        }
        float bk = (age - WEB_BURST) / 8;
        if (bk >= 0 && bk < 1) {
            dsk(new Vector3f(c).add(0, 0.3f, 0), UP, 1.3f + ItadoriWeb.R * easeOut3(bk), FxDraw.RING, 0.05f, IT_CLIVAR, (1 - bk) * 0.8f, true);
            bb(new Vector3f(c).add(0, 0.4f, 0), 3f * (1 - bk * 0.5f), 0, FxDraw.SOFT, 0.4f, IT_FLASH, (1 - bk) * 0.5f, true);
        }
    }

    // The Corte de Tesoura on someone (preview cutAt): a diagonal cut across the chest facing him, the ghost scissors
    // snapping shut on it, and a thread scratching the ground from him to them. a 2: both cuts of the X at once (the web).
    private static void cutStrokes(Fx f) {
        Vector3f toHim = safe(v((float) f.dir.x, 0, (float) f.dir.z));
        Vec3 n = new Vec3(toHim.x, 0, toHim.z);
        Vec3 right = new Vec3(0, 1, 0).cross(n).normalize();
        Vec3 c = f.pos.add(n.scale(0.3));
        for (int i = 0; i < 2; i++) {
            if (f.a != 2 && i != (int) f.a) {
                continue;
            }
            double sx = i == 1 ? 1 : -1;
            Vec3 d = right.scale(sx * 0.8).add(0, -0.75, 0).normalize().scale(1.3);
            SukunaCutsRenderer.stroke(c.subtract(d), c.add(d), 0.42f, n, 0, 1, 9, 12, 0, 1, 1.6f, (int) f.born * 13 + i);
        }
        if (f.a != 2) {
            double sx = f.a == 1 ? 1 : -1;
            Vec3 feet = f.pos.add(f.dir);
            double y = feet.y + 0.03;
            Vec3 a = new Vec3(feet.x, y, feet.z).add(right.scale(sx * 0.3)).subtract(n.scale(0.7));
            Vec3 b = new Vec3(f.pos.x, y, f.pos.z).subtract(right.scale(sx * 0.25)).add(n.scale(0.45));
            SukunaCutsRenderer.stroke(a, b, 0.2f, new Vec3(0, 1, 0), 0, 1.4f, 10, 14, 0, 1, 1.2f, (int) f.born * 17 + (int) f.a);
        }
    }

    private static void cutFx(Fx f, float age) {
        Vector3f c = rel(f.pos);
        Vector3f toHim = safe(v((float) f.dir.x, 0, (float) f.dir.z));
        if (age < 4) {
            bb(c, 0.9f * (1 - age / 4), 0, FxDraw.SOFT, 0.5f, IT_FLASH, 1 - age / 4, true);
            dsk(c, toHim, 0.45f * (0.4f + easeOut3(age / 4)), FxDraw.RING, 0.08f, IT_FLASH, 1 - age / 4, true);
        }
        if (f.a == 2 || age > 7) {
            return;
        }
        // the ghost scissors (2.6x) closing on the cut in 1.6 ticks
        Vector3f right = safe(new Vector3f(UP).cross(toHim));
        float sx = f.a == 1 ? 1 : -1;
        Vector3f d = safe(new Vector3f(right).mul(sx * 0.8f).add(0, -0.75f, 0));
        Vector3f o = safe(new Vector3f(toHim).cross(d));
        float open = 0.7f * (1 - smooth(age / 1.6f));
        scissors(new Vector3f(c).add(new Vector3f(toHim).mul(0.35f)), new Vector3f(d).negate(), o, 0.62f, open, 1 - Math.max(0, age - 5) / 2);
    }

    // The Clivar landing (preview phase 'encosta'): three heavy cuts across the chest and two cuts opening the ground
    // behind them; one impact frame and a shake by distance.
    private static void clivarStrokes(Fx f) {
        Vec3 dir = f.dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.dir.normalize();
        Vec3 n = dir.scale(-1);
        Vec3 right = new Vec3(0, 1, 0).cross(n).normalize();
        Vec3 c = f.pos.add(n.scale(0.35));
        double[][] ds = {{-1, -1}, {1, -1}, {1, 0}};
        for (int i = 0; i < 3; i++) {
            Vec3 d = right.scale(ds[i][0] * 0.8).add(0, ds[i][1] * 0.7, 0).normalize().scale(1.6);
            SukunaCutsRenderer.stroke(c.subtract(d), c.add(d), 0.6f, n, 0, 0.8f, 11, 14, 0, 1, 1.6f, (int) f.born * 19 + i);
        }
        Vec3 feet = f.pos.subtract(0, 0.95, 0);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int sg = -1; sg <= 1; sg += 2) {
            Vec3 a = feet.add(side.scale(sg * 0.35)).add(dir.scale(0.4)).add(0, 0.03, 0);
            Vec3 b = feet.add(side.scale(sg * 0.9)).add(dir.scale(f.a)).add(0, 0.03, 0);
            SukunaCutsRenderer.stroke(a, b, 0.45f, new Vec3(0, 1, 0), 0, 2, 12, 16, 0, 1, 1.2f, (int) f.born * 23 + sg);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity != null) {
            double dist = mc.cameraEntity.position().distanceTo(f.pos);
            float near = (float) Mth.clamp(1 - dist / 28, 0, 1);
            if (dist < 16) {
                ScreenFx.impactFrame(new int[] {0}, 1, 0xFFFFFF, f.pos, 0);
            }
            if (near > 0) {
                ScreenShake.start(near, 10);
            }
        }
    }

    private static void clivarFx(Fx f, float age) {
        Vector3f c = rel(f.pos);
        float k = age / 8;
        if (k < 1) {
            bb(c, 2.2f * (1 - k * 0.5f), 0, FxDraw.SOFT, 0.4f, IT_FLASH, 1 - k, true);
            bb(c, 1.3f * (0.3f + easeOut3(k)) * 2, 0, FxDraw.RING, 0.05f, IT_CLIVAR, (1 - k) * 0.9f, true);
        }
    }

    // The scissors born in his hand while he charges the Clivar, opening and shutting fast.
    private static void handScissors(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || ownFirstPerson(e)) {
            return;
        }
        Vec3 fw = HandTrack.fist(e, true, partial);
        Body b = body(e, partial);
        if (fw == null) {
            fw = b.point(0.5, 0.3, 1.28);
        }
        Vector3f p = rel(fw.add(0, 0.12, 0).add(b.fwd().scale(0.15)));
        Vector3f t = vec(b.fwd());
        float grow = smooth((age - 3) / 2.4f) * out;
        float open = 0.55f * Math.abs(Mth.sin(age * 0.45f));
        scissors(p, t, UP, 0.3f * grow, 0.08f + open, out);
    }

    // ---------- the soul ----------

    // A body of pale mist the shape of e (head, trunk, arms, legs), moved by off and scaled by sc round its middle.
    private static void ghostBody(Body b, Vector3f off, float sc, float op, float age, int seed) {
        if (op <= 0.003f) {
            return;
        }
        Vec3 mid = b.point(0, 0, 0.9);
        java.util.function.Function<Vec3, Vector3f> at = p -> rel(mid.add(p.subtract(mid).scale(sc))).add(off);
        Vector3f head = at.apply(b.point(0, 0, 1.62));
        Vector3f neck = at.apply(b.point(0, 0, 1.4));
        Vector3f hip = at.apply(b.point(0, 0, 0.8));
        bb(head, 0.5f * sc, 0, FxDraw.SOFT, 0.5f, GHOST, op * 0.9f, false);
        bb(head, 0.6f * sc, 0, FxDraw.SOFT, 0.6f, GHOST, op * 0.4f, true);
        mist(neck, hip, 0.55f * sc, op, age, seed);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f sh = at.apply(b.point(0, -0.37 * side, 1.38));
            Vector3f hand = at.apply(b.point(-0.05, -0.42 * side, 0.82));
            mist(sh, hand, 0.28f * sc, op * 0.85f, age, seed + 3 + side);
            Vector3f leg = at.apply(b.point(0, -0.13 * side, 0.78));
            Vector3f foot = at.apply(b.point(0, -0.13 * side, 0.05));
            mist(leg, foot, 0.3f * sc, op * 0.85f, age, seed + 6 + side);
        }
    }

    private static void mist(Vector3f a, Vector3f b, float w, float op, float age, int seed) {
        int n = Math.max(3, (int) Math.ceil(a.distance(b) / (w * 0.45f)));
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            Vector3f p = new Vector3f(a).lerp(b, u);
            float wob = 1 + 0.12f * Mth.sin(age * 0.4f + i * 1.7f + seed);
            bb(p, w * wob, i + seed, FxDraw.SOFT, 0.45f, GHOST, op * 0.55f, false);
            bb(p, w * 0.7f, 0, FxDraw.SOFT, 0.6f, GHOST, op * 0.22f, true);
        }
    }

    // The marked soul (preview SCENES.alma): pale, a little bigger than the body so its outline shows round it, trembling
    // very fast inside it.
    private static void soul(Fx f, float age, float partial, float out) {
        LivingEntity e = living(f.id);
        if (e == null || ownFirstPerson(e)) {
            return;
        }
        float t = age / 20f;
        float tr = 0.09f;
        Vector3f off = v(Mth.sin(t * 97) * tr, Mth.sin(t * 71 + 1) * tr * 0.6f, Mth.sin(t * 83 + 2) * tr * 0.5f);
        float op = (0.65f + 0.15f * Mth.sin(t * 9)) * smooth(age / 3) * out;
        ghostBody(body(e, partial), off, 1.1f, op, age, f.id);
    }

    // The soul torn out along dir and back with a jolt, pale specks bursting.
    private static void rip(Fx f, float age, float partial) {
        LivingEntity e = living(f.id);
        if (e == null) {
            return;
        }
        Vector3f d = safe(vec(f.dir));
        float push = age < 5 ? easeOut3(age / 5) * 1.6f : Math.max(-0.25f, 1.6f * (1 - (age - 5) / 3f));
        if (age > 8) {
            push = -0.25f * (float) Math.exp(-(age - 8) / 3) * Mth.cos((age - 8) * 0.9f);
        }
        float op = Math.min(1, Math.abs(push) * 1.4f + 0.15f) * (1 - smooth((age - 14) / 6));
        ghostBody(body(e, partial), new Vector3f(d).mul(push).add(0, 0.15f * Math.max(0, push), 0), 1, op, age, f.id);
        Vector3f c = rel(e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0));
        float t = age / 20f;
        for (int i = 0; i < 16; i++) {
            float lk = age / 16;
            if (lk >= 1) {
                break;
            }
            float a = hash((int) f.born, i, 88) * Mth.TWO_PI;
            float sp = 1.5f + 2.5f * hash((int) f.born, i, 89);
            Vector3f p = new Vector3f(c).add(new Vector3f(Mth.cos(a) * sp, 2.5f * hash((int) f.born, i, 90), Mth.sin(a) * sp).add(new Vector3f(d).mul(2)).mul(t))
                    .add(0, -3 * t * t, 0);
            bb(p, 0.06f + 0.05f * hash((int) f.born, i, 91), 0, FxDraw.SOFT, 0.7f, GHOST, 1 - lk, true);
        }
        if (age < 4) {
            bb(c, 1.4f * (1 - age / 4), 0, FxDraw.SOFT, 0.5f, GHOST, 1 - age / 4, true);
        }
    }

    // The blink (only on his own screen): the eyelids shut in 0.14 s and open in 0.45 s, past the screen's corners; as
    // they open the world darkens round the edges, pale smoke pulls in from them and two wide waves leave the soul; it
    // all fades from 0.62 s to 1.62 s (fx_impact mode 20).
    private static void lids(Fx f, float age, float partial) {
        if (!DbFx.pass) {
            return;
        }
        float t = age / 20f;
        float close = t < 0.14f ? smooth(t / 0.14f) : t < 0.22f ? 1 : 1 - smooth((t - 0.22f) / 0.45f);
        float open = (1 - close) * 1.75f;
        float see = t > 0.22f ? 1 - smooth((t - 0.62f) / 1.0f) : 0;
        float waves = Math.max(0, t - 0.25f) * 1.1f;
        if (close <= 0.001f && see <= 0.001f) {
            return;
        }
        LivingEntity e = living(f.id);
        Vec3 c = e != null ? e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0) : f.pos;
        ScreenFx.sustainGrade(20, 1, open, see, waves, c);
    }

    // The Escama Vermelha's veins (preview VEIN_F): lit once under the skin, spreading from the heart in 0.45 s, held,
    // gone by 1 s: branching red lines with a blurred glow round them, over the trunk (front and back), the neck and the
    // arms.
    private static void veins(Fx f, float age, float partial) {
        LivingEntity e = living(f.id);
        if (e == null || ownFirstPerson(e)) {
            return;
        }
        float t = age / 20f;
        float xr = t < 0.12f ? smooth(t / 0.12f) : t < 0.75f ? 1 : 1 - smooth((t - 0.75f) / 0.3f);
        if (xr <= 0) {
            return;
        }
        float rev = Mth.clamp(t / 0.45f, 0, 1) * 1.6f;
        float beat = (float) Math.exp(-(t * 2.4f % 1) * 9);
        Body b = body(e, partial);
        Vec3 heart = b.point(0.14, -0.1, 1.3);
        if (age < 4) {
            bb(rel(heart), 0.9f * (1 - age / 4), 0, FxDraw.SOFT, 0.5f, VEIN_HALO, (1 - age / 4) * 0.9f, true);
        }
        int seed = f.id * 7 + 3;
        // trunk, front and back: branches out of the heart (front) and the spine (back)
        for (int face = 0; face < 2; face++) {
            double depth = face == 0 ? 0.14 : -0.14;
            for (int br = 0; br < 7; br++) {
                float ang = br / 7f * Mth.TWO_PI + hash(seed, br + face * 9, 92) * 0.6f;
                double r = face == 0 ? -0.1 : 0;
                double y = face == 0 ? 1.3 : 1.25;
                double reach = face == 0 ? 0 : 0.25;
                for (int sgm = 0; sgm < 6; sgm++) {
                    double nr = r + Mth.cos(ang) * 0.075;
                    double ny = y + Mth.sin(ang) * 0.075;
                    ang += (hash(seed, br * 13 + sgm, 93 + face) - 0.5f) * 1.2f;
                    nr = Mth.clamp(nr, -0.24, 0.24);
                    ny = Mth.clamp(ny, 0.75, 1.48);
                    reach += 0.075;
                    if (reach < rev) {
                        vein(b.point(depth, r, y), b.point(depth, nr, ny), 1 - sgm / 7f, xr, beat, reach > rev - 0.25);
                    }
                    if (sgm == 2 && hash(seed, br, 94 + face) < 0.6f) {
                        double tr = nr + Mth.cos(ang + 1.1f) * 0.12;
                        double ty = ny + Mth.sin(ang + 1.1f) * 0.12;
                        if (reach + 0.12 < rev) {
                            vein(b.point(depth, nr, ny), b.point(depth, Mth.clamp(tr, -0.24, 0.24), Mth.clamp(ty, 0.75, 1.48)), 0.5f, xr, beat, false);
                        }
                    }
                    r = nr;
                    y = ny;
                }
            }
        }
        // neck and arms: a vein running out along each, from the shoulders
        double[][] limbs = {{0, 0, 1.45, 0, 0, 1.6}, {0, -0.37, 1.38, -0.05, -0.42, 0.82}, {0, 0.37, 1.38, -0.05, 0.42, 0.82}};
        for (int li = 0; li < limbs.length; li++) {
            double[] L = limbs[li];
            Vec3 prev = b.point(L[0] + 0.12, L[1], L[2]);
            for (int sgm = 1; sgm <= 5; sgm++) {
                float u = sgm / 5f;
                double reach = 0.35 + u * 0.6;
                Vec3 p = b.point(L[0] + (L[3] - L[0]) * u + 0.12 + (hash(seed, li * 7 + sgm, 95) - 0.5f) * 0.04, L[1] + (L[4] - L[1]) * u
                        + (hash(seed, li * 7 + sgm, 96) - 0.5f) * 0.05, L[2] + (L[5] - L[2]) * u);
                if (reach < rev) {
                    vein(prev, p, 0.8f - u * 0.4f, xr, beat, reach > rev - 0.25);
                }
                prev = p;
            }
        }
    }

    private static void vein(Vec3 a, Vec3 b, float w, float xr, float beat, boolean tip) {
        Vector3f pa = rel(a);
        Vector3f pb = rel(b);
        float[] col = tip || beat > 0.6f ? WHITE_RED : VEIN;
        rib(pa, pb, 0.022f * (0.5f + w), FxDraw.STREAK, 0.8f, col, xr, true);
        rib(pa, pb, 0.07f * (0.5f + w), FxDraw.SOFT, 0.6f, VEIN_HALO, 0.35f * xr, true);
    }

    private static final float[] WHITE_RED = {1f, 0.55f, 0.5f};
}
