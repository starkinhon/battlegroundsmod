package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.skill.BlackBoxSpec;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Aizen's Black Box (power/powers/aizen/BlackBox), drawn with shader shapes only (souls_vfx shapes 12 and 13, no
// textures). "Black glowing violet": walls of black light with faint violet veins and a crisp violet rim, a soft violet
// glow around them that the bloom spreads, and the box's edges drawn as lines of violet light.
// Held: the edges draw themselves out of the corners first, then the walls rise from the ground with a ragged burning
// top and violet flames licking up from it; the box grows with the charge (the size comes from the server every tick).
// Let go: the walls finish rising and the lid shuts from the edges in; lances of the same black stab out through it on
// every hit, more and more of them, under a violet starburst; then it caves in and shatters into shards with violet
// edges. Whoever is inside sees the dark close in over the screen in blocks with violet light between them
// (renderGui).
// The dark parts are drawn after the glow (overlay), so the light of the edges behind the box doesn't show through it.
public final class BlackBoxRenderer {
    private static final float[] DARK = {0.02f, 0.012f, 0.035f};
    private static final float[] DEEP = FxDraw.rgb(0x3A0F8F);
    private static final float[] VIOLET = FxDraw.rgb(0x7A2CFF);
    private static final float[] LILAC = FxDraw.rgb(0xC79BFF);
    private static final float[] PALE = FxDraw.rgb(0xF1E6FF);
    private static final int COFFIN = 12;
    private static final int LANCE = 13;
    // How long the box takes to fade when it is cut short, and how long the shards fly.
    private static final int FADE = 6;
    private static final int SHARDS = 18;

    private static final class Box {
        final int id;
        final boolean awakened;
        final long born;
        Vec3 base;
        float prevHalf, half, prevHeight, height, prevCharge, charge;
        int mode;
        long shutAt = -1;
        long goneAt = -1;
        long updated;
        // How far the walls had risen when it was let go.
        float riseAtShut;

        Box(int id, Vec3 base, float half, float height, float charge, boolean awakened, long now) {
            this.id = id;
            this.base = base;
            this.prevHalf = this.half = half;
            this.prevHeight = this.height = height;
            this.prevCharge = this.charge = charge;
            this.awakened = awakened;
            this.born = now;
            this.updated = now;
        }
    }

    // One frame's look of a box, worked out once and used by both passes.
    private record Look(Vector3f min, Vector3f max, float rise, float lid, float lineK, float glow, float lances, float caveIn,
            float shatter, float fade, float flare) {
    }

    private static final Map<Integer, Box> BOXES = new HashMap<>();

    private BlackBoxRenderer() {
    }

    public static void box(int id, int mode, Vec3 base, Vec3 size, boolean awakened) {
        long now = ScreenFx.now();
        Box b = BOXES.get(id);
        if (b == null) {
            if (mode == 2) {
                return;
            }
            b = new Box(id, base, (float) size.x, (float) size.y, (float) size.z, awakened, now);
            BOXES.put(id, b);
        }
        b.updated = now;
        if (mode == 2) {
            if (b.goneAt < 0) {
                b.goneAt = now;
            }
            return;
        }
        b.prevHalf = b.half;
        b.prevHeight = b.height;
        b.prevCharge = b.charge;
        b.base = base;
        b.half = (float) size.x;
        b.height = (float) size.y;
        b.charge = (float) size.z;
        if (mode == 1 && b.mode == 0) {
            b.shutAt = now;
            b.riseAtShut = heldRise(b, now - b.born);
            // The size stops changing.
            b.prevHalf = b.half;
            b.prevHeight = b.height;
        }
        b.mode = mode;
    }

    public static boolean active() {
        return !BOXES.isEmpty();
    }

    public static void clear() {
        BOXES.clear();
    }

    // ---------- timeline ----------

    private static float heldRise(Box b, double age) {
        BlackBoxSpec.Caixa caixa = BlackBoxSpec.get(b.awakened).caixa();
        return 0.86f * easeOut((float) (age / Math.max(1, caixa.subirTicks())));
    }

    private static int[] stabTimes(BlackBoxSpec spec) {
        int close = Math.max(1, spec.caixa().fecharTicks());
        int stabs = Math.max(1, spec.lancas().golpes());
        int stabTicks = Math.max(stabs, spec.lancas().duracaoTicks());
        int[] at = new int[stabs];
        for (int i = 0; i < stabs; i++) {
            at[i] = close + (int) Math.round(i * (stabTicks - 1) / (double) Math.max(1, stabs - 1));
        }
        return at;
    }

    // Ticks from shutting to the shatter.
    private static int shatterTime(BlackBoxSpec spec) {
        int stabs = Math.max(1, spec.lancas().golpes());
        return Math.max(1, spec.caixa().fecharTicks()) + Math.max(stabs, spec.lancas().duracaoTicks()) + Math.max(1, spec.lancas().quebrarTicks());
    }

    // Null when the box is gone.
    private static Look look(Box b, double now, float partial) {
        BlackBoxSpec spec = BlackBoxSpec.get(b.awakened);
        float half = Mth.lerp(partial, b.prevHalf, b.half);
        float height = Mth.lerp(partial, b.prevHeight, b.height);
        double age = now - b.born;
        float lineK = easeOut((float) (age / 6));
        float rise;
        float lid = 0;
        float lances = 0;
        float caveIn = 0;
        float shatter = -1;
        float fade = 1;
        float flare = 0;
        float charge = Mth.lerp(partial, b.prevCharge, b.charge);
        float glow = 0.75f + 0.35f * charge + 0.08f * Mth.sin((float) age * 0.5f);
        if (b.shutAt < 0) {
            rise = heldRise(b, age);
            if (b.goneAt >= 0) {
                fade = 1 - (float) ((now - b.goneAt) / FADE);
                if (fade <= 0) {
                    return null;
                }
            }
        } else {
            double s = now - b.shutAt;
            int close = Math.max(1, spec.caixa().fecharTicks());
            int end = shatterTime(spec);
            int caveTicks = Math.max(1, spec.lancas().quebrarTicks());
            rise = Mth.lerp(easeOut((float) (s / (close * 0.5f))), b.riseAtShut, 1);
            lid = easeIn((float) ((s - close * 0.3f) / (close * 0.7f)));
            lances = (float) s;
            flare = smooth((float) ((s - close + 1) / 3)) * (1 - smooth((float) ((s - end + caveTicks) / caveTicks)));
            caveIn = easeIn((float) ((s - end + caveTicks) / caveTicks));
            glow += caveIn * 0.8f;
            if (s >= end) {
                shatter = (float) (s - end);
                if (shatter > SHARDS) {
                    return null;
                }
            }
        }
        // Caving in, it shrinks around its middle.
        float squeeze = 1 - 0.2f * caveIn;
        float cy = (float) b.base.y + height / 2;
        Vector3f min = new Vector3f((float) b.base.x - half * squeeze, cy - height / 2 * squeeze, (float) b.base.z - half * squeeze);
        Vector3f max = new Vector3f((float) b.base.x + half * squeeze, cy + height / 2 * squeeze, (float) b.base.z + half * squeeze);
        return new Look(min, max, rise, lid, lineK, glow, lances, caveIn, shatter, fade, flare);
    }

    // ---------- the glow (drawn into the bloom) ----------

    static void render(Vec3 camera, double now, float partial) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        long tick = ScreenFx.now();
        Iterator<Box> it = BOXES.values().iterator();
        while (it.hasNext()) {
            Box b = it.next();
            // A box the server stopped talking about while it was held (out of range, say) goes away on its own.
            if (b.shutAt < 0 && b.goneAt < 0 && tick - b.updated > 6) {
                b.goneAt = tick;
            }
            Look l = look(b, now, partial);
            if (l == null) {
                it.remove();
                continue;
            }
            Vector3f min = new Vector3f(l.min()).sub(camera.toVector3f());
            Vector3f max = new Vector3f(l.max()).sub(camera.toVector3f());
            Vector3f mid = new Vector3f(min).add(max).mul(0.5f);
            float size = Math.max(max.x - min.x, max.y - min.y);
            int seed = b.id * 7919 + 31;
            if (l.shatter() >= 0) {
                shardGlow(b, min, max, l.shatter(), seed);
                continue;
            }
            float f = l.fade();
            float g = l.glow() * f;
            // The violet glow around it, bigger and brighter the more it is charged.
            FxDraw.billboard(mid, size * 2.4f, 0, FxDraw.SOFT, 0, DEEP, 0.2f * g, true);
            FxDraw.billboard(mid, size * 1.6f, 0, FxDraw.SOFT, 0, VIOLET, 0.12f * g, true);
            if (l.flare() > 0) {
                float spin = (float) now * 0.004f;
                FxDraw.billboard(mid, size * 3.2f, spin, FxDraw.FLARE, 12 / 32f, VIOLET, 0.3f * l.flare() * g, true);
                FxDraw.billboard(mid, size * 2.4f, -spin * 1.6f + 0.3f, FxDraw.FLARE, 7 / 32f, LILAC, 0.18f * l.flare() * g, true);
            }
            // The edges, lines of violet light.
            float width = 0.2f + 0.05f * size;
            for (int[] e : EDGES) {
                Vector3f a = corner(min, max, e[0]);
                Vector3f c = new Vector3f(a).lerp(corner(min, max, e[1]), l.lineK());
                FxDraw.ribbon(a, c, width * 2.2f, FxDraw.BEAM, 0, VIOLET, 0.28f * g, true);
                FxDraw.ribbon(a, c, width * 0.5f, FxDraw.BEAM, 0.3f, LILAC, 0.5f * f, true);
            }
            if (l.rise() < 0.999f) {
                flames(min, max, l.rise(), now, seed, f);
            }
            lanceGlow(b, min, max, l, seed, f);
        }
    }

    // Violet flames licking up from the walls' rising top: tongues born along it that rise, shrink and burn out.
    private static void flames(Vector3f min, Vector3f max, float rise, double now, int seed, float fade) {
        float y = min.y + (max.y - min.y) * rise * 1.04f;
        float w = max.x - min.x;
        float d = max.z - min.z;
        float size = 0.35f + 0.18f * Math.max(w, d);
        for (int side = 0; side < 4; side++) {
            int count = 4 + (int) (Math.max(w, d) * 2.5f);
            for (int k = 0; k < count; k++) {
                float period = 7 + 5 * FxWorld.hash(seed + side, k, 1);
                float life = (float) (((now + FxWorld.hash(seed + side, k, 2) * period) / period) % 1.0);
                int cycle = (int) Math.floor((now + FxWorld.hash(seed + side, k, 2) * period) / period);
                float u = FxWorld.hash(seed + side * 31 + cycle, k, 3);
                Vector3f at = switch (side) {
                    case 0 -> new Vector3f(min.x + w * u, y, min.z);
                    case 1 -> new Vector3f(max.x, y, min.z + d * u);
                    case 2 -> new Vector3f(max.x - w * u, y, max.z);
                    default -> new Vector3f(min.x, y, max.z - d * u);
                };
                at.y += life * size * 1.6f;
                float s = size * (1 - 0.5f * life) * (0.7f + 0.6f * FxWorld.hash(seed + side, k, 4));
                FxDraw.billboard(at, s * 1.5f, 0, FxDraw.FIRE, Math.min(0.95f, life * 0.9f), DEEP, 0.55f * fade, true);
                FxDraw.billboard(at, s, 0, FxDraw.FIRE, Math.min(0.95f, life), VIOLET, 0.7f * fade, true);
            }
        }
    }

    // ---------- the dark parts and the thin lines over them ----------

    static void overlay(Vec3 camera, double now, float partial) {
        for (Box b : BOXES.values()) {
            Look l = look(b, now, partial);
            if (l == null) {
                continue;
            }
            Vector3f min = new Vector3f(l.min()).sub(camera.toVector3f());
            Vector3f max = new Vector3f(l.max()).sub(camera.toVector3f());
            int seed = b.id * 7919 + 31;
            if (l.shatter() >= 0) {
                shards(b, min, max, l.shatter(), seed);
                continue;
            }
            float f = l.fade();
            float rise = Math.min(0.499f, l.rise() * 0.5f);
            // The four walls (uv from the bottom left, v up) and the lid.
            FxDraw.quad(new Vector3f(min.x, min.y, min.z), new Vector3f(max.x, min.y, min.z), new Vector3f(max.x, max.y, min.z),
                    new Vector3f(min.x, max.y, min.z), COFFIN, rise, DARK, f, false);
            FxDraw.quad(new Vector3f(max.x, min.y, min.z), new Vector3f(max.x, min.y, max.z), new Vector3f(max.x, max.y, max.z),
                    new Vector3f(max.x, max.y, min.z), COFFIN, rise, DARK, f, false);
            FxDraw.quad(new Vector3f(max.x, min.y, max.z), new Vector3f(min.x, min.y, max.z), new Vector3f(min.x, max.y, max.z),
                    new Vector3f(max.x, max.y, max.z), COFFIN, rise, DARK, f, false);
            FxDraw.quad(new Vector3f(min.x, min.y, max.z), new Vector3f(min.x, min.y, min.z), new Vector3f(min.x, max.y, min.z),
                    new Vector3f(min.x, max.y, max.z), COFFIN, rise, DARK, f, false);
            if (l.lid() > 0) {
                FxDraw.quad(new Vector3f(min.x, max.y, min.z), new Vector3f(max.x, max.y, min.z), new Vector3f(max.x, max.y, max.z),
                        new Vector3f(min.x, max.y, max.z), COFFIN, 0.5f + 0.5f * Math.min(0.999f, l.lid()), DARK, f, false);
            }
            lanceBodies(b, min, max, l, seed, f);
            // Thin hot lines on the edges that face the camera (not the ones behind the box).
            boolean inside = min.x < 0 && max.x > 0 && min.y < 0 && max.y > 0 && min.z < 0 && max.z > 0;
            if (!inside) {
                float width = 0.03f + 0.008f * Math.max(max.x - min.x, max.y - min.y);
                for (int[] e : EDGES) {
                    if (!edgeSeen(min, max, e, l.lid() > 0.5f)) {
                        continue;
                    }
                    Vector3f a = corner(min, max, e[0]);
                    Vector3f c = new Vector3f(a).lerp(corner(min, max, e[1]), l.lineK());
                    FxDraw.ribbon(a, c, width, FxDraw.BEAM, 0.6f, PALE, 0.8f * f, true);
                }
            }
        }
    }

    // ---------- lances ----------

    // A lance: where on the box it comes out and which way. face 0-3 the walls, 4 the lid.
    private record Lance(Vector3f from, Vector3f to, float width, float born) {
    }

    private static void lanceGlow(Box b, Vector3f min, Vector3f max, Look l, int seed, float fade) {
        forLances(b, min, max, l, seed, (lance, k) -> FxDraw.ribbon(lance.from(), lance.to(), lance.width() * 4, LANCE, 0.75f, VIOLET,
                0.55f * k * fade, true));
    }

    private static void lanceBodies(Box b, Vector3f min, Vector3f max, Look l, int seed, float fade) {
        forLances(b, min, max, l, seed, (lance, k) -> {
            FxDraw.ribbon(lance.from(), lance.to(), lance.width(), LANCE, 0.25f, DARK, fade, false);
            // A thin hot line down the middle, so each lance reads against the black box.
            FxDraw.ribbon(lance.from(), new Vector3f(lance.from()).lerp(lance.to(), 0.8f), lance.width() * 0.18f, FxDraw.BEAM, 0.5f, LILAC,
                    0.5f * k * fade, true);
        });
    }

    private interface LanceSink {
        void draw(Lance lance, float k);
    }

    // Every stab sends out a few lances; each one shoots out in 2 ticks and stays until the box caves in, then goes back in.
    private static void forLances(Box b, Vector3f min, Vector3f max, Look l, int seed, LanceSink sink) {
        if (b.shutAt < 0) {
            return;
        }
        BlackBoxSpec spec = BlackBoxSpec.get(b.awakened);
        int[] at = stabTimes(spec);
        float s = l.lances();
        float w = max.x - min.x;
        float h = max.y - min.y;
        float size = Math.max(w, h);
        float back = 1 - easeIn(l.caveIn());
        int per = 3 + (int) Math.min(4, size * 0.8f);
        for (int i = 0; i < at.length; i++) {
            float age = s - at[i];
            if (age < 0) {
                continue;
            }
            float out = easeOut(age / 2) * back;
            if (out <= 0.01f) {
                continue;
            }
            float flash = 1 + 1.5f * Math.max(0, 1 - age / 4);
            for (int k = 0; k < per; k++) {
                int key = i * 16 + k;
                int face = (int) (FxWorld.hash(seed, key, 20) * 4.999f);
                float u = FxWorld.hash(seed, key, 21) * 2 - 1;
                float v = FxWorld.hash(seed, key, 22) * 2 - 1;
                Vector3f n;
                Vector3f t1;
                Vector3f t2;
                Vector3f c = new Vector3f(min).add(max).mul(0.5f);
                float e1;
                float e2;
                switch (face) {
                    case 0 -> { n = new Vector3f(0, 0, -1); t1 = new Vector3f(1, 0, 0); t2 = new Vector3f(0, 1, 0); e1 = w / 2; e2 = h / 2; }
                    case 1 -> { n = new Vector3f(1, 0, 0); t1 = new Vector3f(0, 0, 1); t2 = new Vector3f(0, 1, 0); e1 = w / 2; e2 = h / 2; }
                    case 2 -> { n = new Vector3f(0, 0, 1); t1 = new Vector3f(1, 0, 0); t2 = new Vector3f(0, 1, 0); e1 = w / 2; e2 = h / 2; }
                    case 3 -> { n = new Vector3f(-1, 0, 0); t1 = new Vector3f(0, 0, 1); t2 = new Vector3f(0, 1, 0); e1 = w / 2; e2 = h / 2; }
                    default -> { n = new Vector3f(0, 1, 0); t1 = new Vector3f(1, 0, 0); t2 = new Vector3f(0, 0, 1); e1 = w / 2; e2 = w / 2; }
                }
                Vector3f point = new Vector3f(c).add(new Vector3f(n).mul(face == 4 ? h / 2 : w / 2))
                        .add(new Vector3f(t1).mul(u * e1 * 0.8f)).add(new Vector3f(t2).mul(v * e2 * 0.8f));
                Vector3f dir = new Vector3f(n).add(new Vector3f(t1).mul(u * 0.5f)).add(new Vector3f(t2).mul(v * 0.5f)).normalize();
                float length = size * (0.45f + 0.65f * FxWorld.hash(seed, key, 23)) * out;
                float width = 0.1f + 0.05f * size * (0.6f + 0.6f * FxWorld.hash(seed, key, 24));
                Vector3f from = new Vector3f(point).sub(new Vector3f(dir).mul(0.1f * size));
                Vector3f to = new Vector3f(point).add(new Vector3f(dir).mul(length));
                sink.draw(new Lance(from, to, width, at[i]), Math.min(1.5f, flash * 0.6f));
            }
        }
    }

    // ---------- shatter ----------

    private static final int GRID = 3;

    private interface ShardSink {
        void draw(Vector3f center, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float k);
    }

    // The five faces broken into a 3x3 grid of shards each, thrown out from the middle, turning, falling and fading.
    private static void forShards(Vector3f min, Vector3f max, float t, int seed, ShardSink sink) {
        Vector3f mid = new Vector3f(min).add(max).mul(0.5f);
        float k = 1 - t / SHARDS;
        float size = Math.max(max.x - min.x, max.y - min.y);
        for (int face = 0; face < 5; face++) {
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    float u0 = i / (float) GRID;
                    float u1 = (i + 1) / (float) GRID;
                    float v0 = j / (float) GRID;
                    float v1 = (j + 1) / (float) GRID;
                    Vector3f a = facePoint(min, max, face, u0, v0);
                    Vector3f b = facePoint(min, max, face, u1, v0);
                    Vector3f c = facePoint(min, max, face, u1, v1);
                    Vector3f d = facePoint(min, max, face, u0, v1);
                    Vector3f center = new Vector3f(a).add(b).add(c).add(d).mul(0.25f);
                    int key = face * 16 + i * GRID + j;
                    Vector3f out = new Vector3f(center).sub(mid).normalize();
                    float speed = size * (0.05f + 0.07f * FxWorld.hash(seed, key, 30));
                    float tt = easeOut(t / 10) * 10;
                    Vector3f move = new Vector3f(out).mul(speed * tt).add(0, -0.012f * t * t, 0);
                    Vector3f axis = new Vector3f(FxWorld.hash(seed, key, 31) - 0.5f, FxWorld.hash(seed, key, 32) - 0.5f,
                            FxWorld.hash(seed, key, 33) - 0.5f);
                    if (axis.lengthSquared() < 1.0E-4f) {
                        axis.set(0, 1, 0);
                    }
                    Quaternionf spin = new Quaternionf().fromAxisAngleRad(axis.normalize(), t * (0.15f + 0.25f * FxWorld.hash(seed, key, 34)));
                    float shrink = 0.92f * (0.5f + 0.5f * k);
                    Vector3f to = new Vector3f(center).add(move);
                    sink.draw(to, turn(a, center, to, spin, shrink), turn(b, center, to, spin, shrink), turn(c, center, to, spin, shrink),
                            turn(d, center, to, spin, shrink), k);
                }
            }
        }
    }

    private static Vector3f turn(Vector3f p, Vector3f center, Vector3f to, Quaternionf spin, float shrink) {
        return new Vector3f(p).sub(center).mul(shrink).rotate(spin).add(to);
    }

    private static void shards(Box b, Vector3f min, Vector3f max, float t, int seed) {
        forShards(min, max, t, seed, (center, a, bb, c, d, k) -> FxDraw.quad(a, bb, c, d, COFFIN, 0.499f, DARK, Math.min(1, k * 1.4f), false));
    }

    private static void shardGlow(Box b, Vector3f min, Vector3f max, float t, int seed) {
        float size = Math.max(max.x - min.x, max.y - min.y);
        forShards(min, max, t, seed, (center, a, bb, c, d, k) -> FxDraw.billboard(center, size * 0.45f, 0, FxDraw.SOFT, 0.2f, VIOLET, 0.35f * k,
                true));
        // The burst of light where the box was.
        Vector3f mid = new Vector3f(min).add(max).mul(0.5f);
        float k = 1 - t / SHARDS;
        FxDraw.billboard(mid, size * (1.5f + 2.5f * easeOut(t / 6)), 0, FxDraw.SOFT, 0.6f, LILAC, 0.6f * k * k, true);
    }

    // A point on a face: 0-3 the walls (u along, v up, as the walls are drawn), 4 the lid.
    private static Vector3f facePoint(Vector3f min, Vector3f max, int face, float u, float v) {
        float y = Mth.lerp(v, min.y, max.y);
        return switch (face) {
            case 0 -> new Vector3f(Mth.lerp(u, min.x, max.x), y, min.z);
            case 1 -> new Vector3f(max.x, y, Mth.lerp(u, min.z, max.z));
            case 2 -> new Vector3f(Mth.lerp(u, max.x, min.x), y, max.z);
            case 3 -> new Vector3f(min.x, y, Mth.lerp(u, max.z, min.z));
            default -> new Vector3f(Mth.lerp(u, min.x, max.x), max.y, Mth.lerp(v, min.z, max.z));
        };
    }

    // ---------- box geometry ----------

    // Corners by bits: 1 = x max, 2 = y max, 4 = z max. Edges join corners one bit apart.
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static Vector3f corner(Vector3f min, Vector3f max, int bits) {
        return new Vector3f((bits & 1) != 0 ? max.x : min.x, (bits & 2) != 0 ? max.y : min.y, (bits & 4) != 0 ? max.z : min.z);
    }

    // An edge is seen when one of the two faces it joins looks at the camera (at the origin). The open top only counts
    // once the lid is on.
    private static boolean edgeSeen(Vector3f min, Vector3f max, int[] e, boolean lidOn) {
        int same = ~(e[0] ^ e[1]) & 7;
        for (int bit = 1; bit <= 4; bit <<= 1) {
            if ((same & bit) == 0) {
                continue;
            }
            boolean high = (e[0] & bit) != 0;
            float lo = bit == 1 ? min.x : bit == 2 ? min.y : min.z;
            float hi = bit == 1 ? max.x : bit == 2 ? max.y : max.z;
            if (bit == 2 && high && !lidOn) {
                continue;
            }
            if (high ? hi < 0 : lo > 0) {
                return true;
            }
        }
        return false;
    }

    // ---------- the screen of whoever is inside ----------

    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (BOXES.isEmpty() || mc.level == null) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        double now = ScreenFx.now() + partial;
        for (Box b : BOXES.values()) {
            Look l = look(b, now, partial);
            if (l == null) {
                continue;
            }
            Vector3f min = l.min();
            Vector3f max = l.max();
            if (eye.x < min.x || eye.x > max.x || eye.z < min.z || eye.z > max.z || eye.y < min.y || eye.y > max.y + 0.3f) {
                continue;
            }
            float cover;
            float stab = 0;
            if (l.shatter() >= 0) {
                cover = 1 - easeOut(l.shatter() / 5);
            } else if (b.shutAt < 0) {
                cover = 0.55f * l.rise() / 0.86f * l.fade();
            } else {
                float s = l.lances();
                BlackBoxSpec spec = BlackBoxSpec.get(b.awakened);
                cover = Mth.lerp(easeOut(s / Math.max(1, spec.caixa().fecharTicks())), 0.55f, 1.02f);
                for (int at : stabTimes(spec)) {
                    float age = s - at;
                    if (age >= 0 && age < 4) {
                        stab = Math.max(stab, 1 - age / 4);
                    }
                }
            }
            if (cover > 0.01f) {
                darkness(graphics, cover, stab, now, b.id);
            }
            return;
        }
    }

    // The dark closing in as blocks, the edge of the screen first, violet light glowing between them; on each lance hit
    // a few lines of light cut across.
    private static void darkness(GuiGraphics graphics, float cover, float stab, double now, int id) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        float cell = Math.max(w, h) / 18f;
        int cols = (int) Math.ceil(w / cell);
        int rows = (int) Math.ceil(h / cell);
        int boil = (int) Math.floor(now / 3);
        graphics.flush();
        Matrix4f m = graphics.pose().last().pose();
        int glowOuter = argb(0.2f + 0.15f * stab, 0x7A2CFF);
        int glowInner = argb(0.4f + 0.3f * stab, 0xA060FF);
        int black = argb(1, 0x050308);
        boolean[] dark = new boolean[cols * rows];
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                float dx = ((i + 0.5f) * cell - w / 2f) / (w / 2f);
                float dy = ((j + 0.5f) * cell - h / 2f) / (h / 2f);
                float d = Math.min(1, (float) Math.sqrt(dx * dx + dy * dy) / 1.414f);
                float noise = 0.75f * FxWorld.hash(id, i * 97 + j, 40) + 0.25f * FxWorld.hash(id + boil, i * 97 + j, 41);
                float score = 0.12f + (1 - d) * 0.72f + noise * 0.16f;
                dark[i * rows + j] = score < cover;
            }
        }
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        // Glow around each dark block first, then the blocks over it: only the light at the edges of the dark shows.
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < cols; i++) {
                for (int j = 0; j < rows; j++) {
                    if (!dark[i * rows + j]) {
                        continue;
                    }
                    float x0 = i * cell;
                    float y0 = j * cell;
                    float grow = pass == 0 ? cell * 0.35f : pass == 1 ? cell * 0.15f : 0;
                    int color = pass == 0 ? glowOuter : pass == 1 ? glowInner : black;
                    quad(buf, m, x0 - grow, y0 - grow, x0 + cell + grow, y0 + cell + grow, color);
                }
            }
        }
        if (stab > 0) {
            for (int k = 0; k < 4; k++) {
                float a = FxWorld.hash(id + (int) now, k, 50) * Mth.TWO_PI;
                float cx = w * (0.2f + 0.6f * FxWorld.hash(id + (int) now, k, 51));
                float cy = h * (0.2f + 0.6f * FxWorld.hash(id + (int) now, k, 52));
                float len = Math.max(w, h);
                line(buf, m, cx - Mth.cos(a) * len, cy - Mth.sin(a) * len, cx + Mth.cos(a) * len, cy + Mth.sin(a) * len, cell * 0.12f * stab,
                        argb(0.8f * stab, 0xE6D4FF));
            }
        }
        MeshData mesh = buf.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, int color) {
        b.addVertex(m, x0, y0, 0).setColor(color);
        b.addVertex(m, x0, y1, 0).setColor(color);
        b.addVertex(m, x1, y1, 0).setColor(color);
        b.addVertex(m, x0, y0, 0).setColor(color);
        b.addVertex(m, x1, y1, 0).setColor(color);
        b.addVertex(m, x1, y0, 0).setColor(color);
    }

    private static void line(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-3f) {
            return;
        }
        float nx = -dy / len * width / 2;
        float ny = dx / len * width / 2;
        b.addVertex(m, x0 - nx, y0 - ny, 0).setColor(color);
        b.addVertex(m, x0 + nx, y0 + ny, 0).setColor(color);
        b.addVertex(m, x1 + nx, y1 + ny, 0).setColor(color);
        b.addVertex(m, x0 - nx, y0 - ny, 0).setColor(color);
        b.addVertex(m, x1 + nx, y1 + ny, 0).setColor(color);
        b.addVertex(m, x1 - nx, y1 - ny, 0).setColor(color);
    }

    private static int argb(float a, int rgb) {
        return (int) (Mth.clamp(a, 0, 1) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    private static float easeOut(float x) {
        x = Mth.clamp(x, 0, 1);
        float i = 1 - x;
        return 1 - i * i * i;
    }

    private static float easeIn(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * x;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
