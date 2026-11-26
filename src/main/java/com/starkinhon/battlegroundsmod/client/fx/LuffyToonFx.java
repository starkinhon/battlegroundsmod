// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// Gear 5's cartoon on every blow that lands (forja/luffy-haki: onomato.js + doodle.js, page g5-onomato.html, approved by
// Stark 2026-11-26 03h27 "gg"): a comic word in Minecraft's pixels with a glow of its color (POW!, BAM!, ... and every
// third blow a big DON!!, DOOM!! or KABOOM!!, never the same word twice in a row, its colors drawn each time), and ink
// doodles round the blow: rays opening out, little sparkles, fright marks over his head, speed lines behind whoever
// flies, dust puffs at his feet and three stars spinning round his head with a dizzy ring.
// All in the world, not on the screen (Stark: "no mundo"): each piece is a flat card turned to whoever looks only when it
// is born, then it stays put (walk round it and it is seen from the side) and whatever is in front hides it. The dizzy
// stars are the one exception: little 3D stars, they keep facing the camera as they spin.
// The pictures are baked from the page (textures/fx/luffy_ono_words.png: 9 color sets across, 18 words down;
// luffy_ono_doodles.png: rays, sparkle, fright marks, puff, speed lines, star, ring in a 4 x 2 grid).
public final class LuffyToonFx {
    private static final ResourceLocation WORDS = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/luffy_ono_words.png");
    private static final ResourceLocation DOODLES = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/luffy_ono_doodles.png");
    // the atlas: rows 0-14 the words, 15-17 the big ones
    private static final int COLORS = 9, ROWS = 18, SMALL = 15, BIG = 3;
    private static final int RAYS = 0, SPARK = 1, TICKS = 2, CLOUD = 3, SWOOSH = 4, STAR = 5, RING = 6;
    private static final int K_POP = 0, K_GROW = 1, K_TWINKLE = 2, K_TRAIL = 3, K_PUFF = 4, K_ORBIT = 5;
    private static final double DIZZY = 2.6;

    private static final class Word {
        Vec3 at;
        double born;
        int row, col;
        float yaw, size, rot;
    }

    private static final class Dood {
        int cell, kind, target = -1, k;
        Vec3 at, off = Vec3.ZERO, drift = Vec3.ZERO;
        double born, delay, life;
        float size, rot, yaw;
        boolean faced, onHead;
    }

    private static final class Dizzy {
        double until, born;
    }

    private static final List<Word> WORDS_ON = new ArrayList<>();
    private static final List<Dood> DOODS = new ArrayList<>();
    private static final Map<Integer, Dizzy> DIZZY_ON = new HashMap<>();
    // the last blow on each target (a Gatling must not pile up a word on every fist)
    private static final Map<Integer, Double> LAST = new HashMap<>();
    // the last position seen of each target (if it is gone, its pieces stay where it was)
    private static final Map<Integer, Vec3> SEEN = new HashMap<>();
    private static int hits, lastRow = -1;
    private static double now;
    private static float partial;

    private LuffyToonFx() {
    }

    static boolean active() {
        return !WORDS_ON.isEmpty() || !DOODS.isEmpty();
    }

    static void clear() {
        WORDS_ON.clear();
        DOODS.clear();
        DIZZY_ON.clear();
        LAST.clear();
        SEEN.clear();
    }

    private static double rnd() {
        return OpmBlocks.rnd();
    }

    private static double sec() {
        return now / 20;
    }

    // a Gear 5 blow landed on target at c, coming along dir (LuffyFx, the LUFFY_HAKI packet)
    static void hit(int target, Vec3 c, Vec3 dir, double s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || c.distanceTo(mc.gameRenderer.getMainCamera().getPosition()) > 64) {
            return;
        }
        now = ScreenFx.now() + partial;
        Double last = LAST.get(target);
        if (last != null && sec() - last < 0.25) {
            return;
        }
        LAST.put(target, sec());
        if (LAST.size() > 64) {
            LAST.clear();
        }
        Vec3 d = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 hitP = c.subtract(d.scale(0.28 * s));
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        hits++;
        word(hitP, cam, hits % 3 == 0, s);
        doodles(target, hitP, d, cam, s);
    }

    // ---------- the word ----------

    private static void word(Vec3 p, Vec3 cam, boolean big, double s) {
        int row;
        do {
            row = big ? SMALL + (int) (rnd() * BIG) : (int) (rnd() * SMALL);
        } while (row == lastRow);
        lastRow = row;
        // beside the target, toward the middle of the screen, a little up
        Vec3 f = cam.subtract(p);
        Vec3 right = new Vec3(f.z, 0, -f.x);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 look = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().add(new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector()));
        double side = Math.signum(right.dot(look.subtract(p)));
        if (side == 0) {
            side = hits % 2 == 0 ? 1 : -1;
        }
        Word w = new Word();
        w.at = p.add(right.scale(side * (0.4 + 0.4 * rnd()) * s)).add(0, (0.75 + 0.35 * rnd()) * s, 0);
        w.born = sec();
        w.row = row;
        w.col = (int) (rnd() * COLORS);
        w.size = (float) ((big ? 2.6 : 1.9) * (0.9 + 0.2 * rnd()) * s);
        w.rot = (float) ((rnd() - 0.5) * 0.3);
        w.yaw = yaw(w.at, cam);
        WORDS_ON.add(w);
        if (WORDS_ON.size() > 8) {
            WORDS_ON.remove(0);
        }
    }

    // ---------- the doodles ----------

    private static Dood dood(int cell, int kind, double life, double size) {
        Dood d = new Dood();
        d.cell = cell;
        d.kind = kind;
        d.life = life;
        d.size = (float) size;
        d.born = sec();
        DOODS.add(d);
        return d;
    }

    private static void doodles(int target, Vec3 p, Vec3 dir, Vec3 cam, double s) {
        Dood rays = dood(RAYS, K_GROW, 0.36, 2.0 * s);
        rays.at = p;
        rays.rot = (float) ((rnd() - 0.5) * 3);
        for (int i = 0; i < 4; i++) {
            double a = rnd() * Math.PI * 2;
            Dood sp = dood(SPARK, K_TWINKLE, 0.55 + 0.3 * rnd(), (0.32 + 0.22 * rnd()) * s);
            sp.at = p.add(Math.cos(a) * (0.6 + 0.4 * rnd()) * s, (rnd() - 0.5) * 0.9 * s, Math.sin(a) * (0.6 + 0.4 * rnd()) * s);
            sp.delay = 0.05 * i;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity t = mc.level.getEntity(target);
        if (t == null) {
            return;
        }
        // his own head in first person: nothing in his face
        boolean ownFace = t == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        if (!ownFace) {
            Dood tk = dood(TICKS, K_POP, 0.55, 0.75 * s);
            tk.target = target;
            tk.onHead = true;
            tk.off = new Vec3(0, 0.32 * s, 0);
            tk.rot = (float) ((rnd() - 0.5) * 0.5);
            tk.delay = 0.06;
        }
        // speed lines: on him, on the side the blow came from, while he flies
        Vec3 back = new Vec3(-dir.x, 0, -dir.z);
        back = back.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : back.normalize();
        for (int i = 0; i < 2; i++) {
            Dood sw = dood(SWOOSH, K_TRAIL, 0.5, (1.4 - i * 0.3) * s);
            sw.target = target;
            sw.off = back.scale((0.7 + i * 0.3) * s).add(0, (0.6 + i * 0.5) * s, 0);
            sw.rot = (float) ((rnd() - 0.5) * 0.3);
            sw.delay = 0.03 * i;
        }
        // dust puffs at his feet as he leaves, and as he stops
        for (int i = 0; i < 5; i++) {
            boolean stop = i >= 3;
            Dood cl = dood(CLOUD, K_PUFF, stop ? 0.6 : 0.7, (stop ? 0.65 : 0.6 + 0.3 * rnd()) * s);
            cl.target = target;
            cl.off = new Vec3((rnd() - 0.5) * (stop ? 0.6 : 0.8) * s, 0.18 * s, (rnd() - 0.5) * (stop ? 0.6 : 0.8) * s);
            cl.drift = new Vec3(rnd() - 0.5, stop ? 0.25 : 0.3, rnd() - 0.5);
            cl.delay = stop ? 0.5 + 0.1 * (i - 3) : 0.08 + 0.08 * i;
        }
        // dizzy: three stars round his head for 2.6 s, again from the start on every blow
        if (!ownFace) {
            Dizzy z = DIZZY_ON.get(target);
            if (z == null || z.until < sec()) {
                z = new Dizzy();
                z.born = sec();
                DIZZY_ON.put(target, z);
                for (int k = 0; k < 3; k++) {
                    Dood st = dood(STAR, K_ORBIT, 99, 0.34 * s);
                    st.target = target;
                    st.onHead = true;
                    st.k = k;
                }
            }
            z.until = sec() + DIZZY;
        }
    }

    // ---------- every frame (from HakiFx.render, inside the world's draw) ----------

    static void render(Vec3 cam, double nowTicks, float pt, double dt) {
        now = nowTicks;
        partial = pt;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (WORDS_ON.isEmpty() && DOODS.isEmpty())) {
            return;
        }
        double t = sec();
        VertexConsumer words = WORDS_ON.isEmpty() ? null : FxWorld.decalPixel(WORDS);
        for (Iterator<Word> it = WORDS_ON.iterator(); it.hasNext(); ) {
            Word w = it.next();
            double a = t - w.born, life = 0.95;
            if (a > life) {
                it.remove();
                continue;
            }
            // pops in too big, settles, shakes a little, holds, then grows away as it fades (no blinking)
            double k;
            if (a < 0.07) {
                k = a / 0.07 * 1.3;
            } else if (a < 0.17) {
                k = 1.3 - 0.3 * Math.sin((a - 0.07) / 0.1 * Math.PI / 2);
            } else {
                k = 1 + 0.03 * Math.sin(a * 40) * Math.exp(-(a - 0.17) * 6) + Math.max(0, a - 0.72) * 0.6;
            }
            double out = Math.max(0, (a - 0.72) / (life - 0.72));
            float op = (float) (1 - out * out);
            float rot = (float) (w.rot + (a < 0.17 ? (1 - a / 0.17) * 0.25 : 0));
            Vec3 at = w.at.add(0, a * 0.25, 0);
            float u0 = w.col / (float) COLORS, v0 = w.row / (float) ROWS;
            card(words, at, cam, w.yaw, (float) (w.size * k), (float) (w.size * k / 2), rot, u0, v0, u0 + 1f / COLORS, v0 + 1f / ROWS, op);
        }
        DIZZY_ON.values().removeIf(z -> z.until + 0.3 < t);
        VertexConsumer out = DOODS.isEmpty() ? null : FxWorld.decalPixel(DOODLES);
        for (Iterator<Dood> it = DOODS.iterator(); it.hasNext(); ) {
            Dood d = it.next();
            double a = t - d.born - d.delay;
            Vec3 base = d.at;
            if (d.target >= 0) {
                Entity e = mc.level.getEntity(d.target);
                Vec3 seen = SEEN.get(d.target);
                Vec3 pos = e != null ? (d.onHead ? head(e) : e.getPosition(partial)) : seen == null ? null : d.onHead ? seen.add(0, 1.9, 0) : seen;
                if (e != null) {
                    SEEN.put(d.target, e.getPosition(partial));
                }
                if (pos == null) {
                    it.remove();
                    continue;
                }
                base = pos.add(d.off);
            }
            if (d.kind == K_ORBIT) {
                Dizzy z = DIZZY_ON.get(d.target);
                if (z == null) {
                    it.remove();
                    continue;
                }
                // the dizziness ends: the stars shrink away
                double end = Math.min(1, Math.max(0, (z.until + 0.3 - t) / 0.3)), grow = Math.min(1, (t - z.born) / 0.12);
                double th = (t - z.born) * 6.5 + d.k * 2.094, R = 0.5 * d.size / 0.34;
                Vec3 p = base.add(Math.cos(th) * R, 0.06 * Math.sin(th * 2) + 0.05 * Math.sin(t * 3), Math.sin(th) * R * 0.85);
                float sz = (float) (d.size * grow * end * (1 + 0.12 * Math.sin(t * 9 + d.k)));
                cell(out, p, cam, yaw(p, cam), sz, (float) (t * 4 + d.k), STAR, 1);
                // the ring lies flat round his head (drawn once, with the first star)
                if (d.k == 0) {
                    float rk = (float) (Math.min(1, (t - z.born) / 0.15) * end);
                    ring(out, base.add(0, 0.04, 0), cam, (float) (1.15 * rk * d.size / 0.34), (float) (1.0 * rk * d.size / 0.34), (float) (t * 2));
                }
                continue;
            }
            if (a > d.life) {
                it.remove();
                continue;
            }
            if (a < 0) {
                continue;
            }
            double q = a / d.life;
            double s = d.size;
            float op = 1, rot = d.rot;
            switch (d.kind) {
                case K_POP -> {
                    s *= a < 0.08 ? easeBack(a / 0.08) : 1 - (q > 0.6 ? (q - 0.6) / 0.4 * 0.9 : 0);
                    op = (float) (q > 0.7 ? 1 - (q - 0.7) / 0.3 : 1);
                }
                case K_GROW -> {
                    s *= 0.5 + 0.9 * (1 - Math.pow(1 - q, 2));
                    op = (float) (1 - q * q);
                }
                case K_TWINKLE -> {
                    s *= Math.sin(Math.min(1, q) * Math.PI) * (1 + 0.25 * Math.sin(a * 30));
                    rot = (float) (a * 3);
                }
                case K_TRAIL -> {
                    s *= q < 0.15 ? q / 0.15 : 1;
                    op = (float) (1 - q);
                }
                case K_PUFF -> {
                    s *= 0.4 + 0.8 * (1 - Math.pow(1 - q, 3));
                    op = (float) (q > 0.5 ? 1 - (q - 0.5) / 0.5 : 1);
                    base = base.add(d.drift.scale(q * 0.6));
                }
                default -> {
                }
            }
            // turned to whoever looks once, when it is born; then it stays put in the world
            if (!d.faced) {
                d.yaw = yaw(base, cam);
                d.faced = true;
            }
            cell(out, base, cam, d.yaw, (float) s, rot, d.cell, op);
        }
    }

    private static double easeBack(double q) {
        double s = 1.9;
        q -= 1;
        return q * q * ((s + 1) * q + s) + 1;
    }

    private static Vec3 head(Entity e) {
        float sc = e instanceof net.minecraft.world.entity.LivingEntity l ? l.getScale() : 1;
        return e.getPosition(partial).add(0, e.getBbHeight() + 0.12 * sc, 0);
    }

    // the yaw that turns a card at p to face cam
    private static float yaw(Vec3 p, Vec3 cam) {
        return (float) Math.atan2(cam.x - p.x, cam.z - p.z);
    }

    private static void cell(VertexConsumer out, Vec3 at, Vec3 cam, float yaw, float size, float rot, int cell, float op) {
        float u0 = (cell % 4) / 4f, v0 = (cell / 4) / 2f;
        card(out, at, cam, yaw, size, size, rot, u0, v0, u0 + 0.25f, v0 + 0.5f, op);
    }

    // a card standing up at at, facing along yaw, w x h blocks, turned by rot in its own plane
    private static void card(VertexConsumer out, Vec3 at, Vec3 cam, float yaw, float w, float h, float rot, float u0, float v0, float u1, float v1, float op) {
        if (out == null || op <= 0.01f || w <= 0.001f) {
            return;
        }
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        Vector3f R = new Vector3f(fz, 0, -fx), U = new Vector3f(0, 1, 0);
        float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
        Vector3f x = new Vector3f(R).mul(c * w / 2).add(new Vector3f(U).mul(s * w / 2));
        Vector3f y = new Vector3f(U).mul(c * h / 2).sub(new Vector3f(R).mul(s * h / 2));
        Vector3f o = new Vector3f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z));
        quad(out, new Vector3f(o).sub(x).sub(y), new Vector3f(o).add(x).sub(y), new Vector3f(o).add(x).add(y), new Vector3f(o).sub(x).add(y), u0, v0, u1, v1, op);
    }

    // the dizzy ring, laid flat
    private static void ring(VertexConsumer out, Vec3 at, Vec3 cam, float w, float d, float spin) {
        if (w <= 0.001f) {
            return;
        }
        float c = (float) Math.cos(spin), s = (float) Math.sin(spin);
        Vector3f x = new Vector3f(c * w / 2, 0, s * w / 2), z = new Vector3f(-s * d / 2, 0, c * d / 2);
        Vector3f o = new Vector3f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z));
        float u0 = (RING % 4) / 4f, v0 = (RING / 4) / 2f;
        quad(out, new Vector3f(o).sub(x).sub(z), new Vector3f(o).add(x).sub(z), new Vector3f(o).add(x).add(z), new Vector3f(o).sub(x).add(z), u0, v0, u0 + 0.25f, v0 + 0.5f, 1);
    }

    private static void quad(VertexConsumer out, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u0, float v0, float u1, float v1, float op) {
        out.addVertex(a.x, a.y, a.z).setUv(u0, v1).setColor(1f, 1f, 1f, op);
        out.addVertex(b.x, b.y, b.z).setUv(u1, v1).setColor(1f, 1f, 1f, op);
        out.addVertex(c.x, c.y, c.z).setUv(u1, v0).setColor(1f, 1f, 1f, op);
        out.addVertex(d.x, d.y, d.z).setUv(u0, v0).setColor(1f, 1f, 1f, op);
    }
}
