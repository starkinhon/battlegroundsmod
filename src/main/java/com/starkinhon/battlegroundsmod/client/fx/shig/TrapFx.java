// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// C, Mãos do Chão (remake 2026-11-26, preview /mnt/project-files/forja/shigaraki-tentaculos/maos-do-chao-v2.html,
// https://claude.ai/artifact/TS8hqzcH8obxL5gngPYNgx; Stark: "ta perfeito dmais"). The server runs the front along the
// ground after his aim (MaosDoChao) and sends each tick where it got to; on the way from the last spot to the new one a
// hand bursts out every 0.55-1.05 blocks, each its own (HandGen), to one side or the other: it breaks the ground (a hole
// with cracks and lumps of earth thrown, souls_shig_mark Mode 2), comes out (ease-out-back in 0.15 s), leans forward
// trying to grab and crumbles to dust. Where the front touches someone, the eight grip hands of the old trap burst out
// around them (ease-out-back in 0.28 s), grip (0.3 to 0.7 s), hold 2.6 s shaking a little and crumble (0.8 s). The
// forearms stay under the ground (souls_shig_hand discards under Ground). Same numbers as the preview.
public final class TrapFx {
    private static final float HOLD = 2.6f, DIS = 0.8f;
    private static int seedCount, waveSeed;

    private static final class Hand {
        HandGen.Mesh mesh;
        float seed, sc, ang, rad, h0, delay, roll;
    }

    // One hand of the run.
    private static final class Wave {
        HandGen.Mesh mesh;
        Vec3 base, dir, side;
        double t;
        float seed, sc, h0, lean0, lean1, tw, roll, snap, life;
    }

    // The hole where a hand broke the ground.
    private static final class Hole {
        Vec3 at;
        double t;
        float seed, rot, size, life;
    }

    // A lump of earth thrown out of a hole: its own size, shade, spin and flight.
    private static final class Clod {
        double x, y, z, vx, vy, vz, floor, wait, age;
        float sx, sy, sz, spx, spy, spz, r, g, b;
    }

    private static final class T {
        Vec3 front, dir;
        final double start = ShigFx.clock();
        double travelled, nextAt = 0.2, caughtAt = -1, overAt = -1;
        float side = 1;
        final HandGen.Rng gap;
        int target = -1;
        Vec3 at;
        Hand[] hands;
        final List<Wave> wave = new ArrayList<>();

        T(int id, Vec3 front, Vec3 dir) {
            this.front = front;
            this.dir = dir;
            this.gap = new HandGen.Rng(31337L + id);
        }
    }

    private static final Map<Integer, T> RUNS = new HashMap<>();
    private static final List<Hole> HOLES = new ArrayList<>();
    private static final List<Clod> CLODS = new ArrayList<>();
    private static double lastFrame = -1;

    private TrapFx() {
    }

    static boolean active() {
        return !RUNS.isEmpty() || !HOLES.isEmpty() || !CLODS.isEmpty();
    }

    static void clear() {
        RUNS.clear();
        HOLES.clear();
        CLODS.clear();
    }

    // Shigaraki.TRAP: id = the run; a = 0 the slap (pos = the start, on the ground, dir = the way), 4 the front moved
    // (pos = the front, dir = its way), 2 caught (pos = where, b = who), 3 over.
    static void receive(int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ShigFx.clock();
        int mode = Math.round(a);
        if (mode == 0) {
            RUNS.put(id, new T(id, pos, dir));
            return;
        }
        T t = RUNS.get(id);
        if (t == null) {
            return;
        }
        if (mode == 4 && t.caughtAt < 0 && t.overAt < 0) {
            advance(t, pos, dir, now);
        } else if (mode == 2 && t.caughtAt < 0) {
            t.caughtAt = now;
            t.target = Math.round(b);
            t.at = pos;
            t.hands = hands(pos);
        } else if (mode == 3 && t.overAt < 0) {
            t.overAt = now;
        }
    }

    // The front went from t.front to `to` in this tick: the hands on the way come out spread over the tick (0.05 s), so
    // they keep the preview's rhythm between the server's packets.
    private static void advance(T t, Vec3 to, Vec3 dir, double now) {
        Vec3 from = t.front;
        double step = Math.hypot(to.x - from.x, to.z - from.z);
        double before = t.travelled;
        t.travelled += step;
        while (t.travelled >= t.nextAt) {
            double k = step < 1e-4 ? 1 : (t.nextAt - before) / step;
            k = Math.max(0, Math.min(1, k));
            Vec3 p = from.add(to.subtract(from).scale(k));
            wave(t, p, dir, now + k * 0.05);
            t.nextAt += 0.55 + t.gap.next() * 0.5;
        }
        t.front = to;
        t.dir = dir;
    }

    private static void wave(T t, Vec3 at, Vec3 dir, double when) {
        HandGen.Rng r = new HandGen.Rng(12400 + waveSeed++);
        Wave w = new Wave();
        float fMul = 1.4f + r.next() * 0.7f;
        w.mesh = HandGen.hand(r, fMul);
        w.seed = r.next();
        w.side = new Vec3(dir.z, 0, -dir.x);
        if (r.next() > 0.2f) {
            t.side = -t.side; // sometimes two in a row on the same side
        }
        float off = t.side * (0.2f + r.next() * 0.75f);
        w.base = at.add(w.side.scale(off));
        w.sc = 1.0f + r.next() * 0.55f;
        w.dir = dir;
        w.t = when;
        w.h0 = 0.95f + r.next() * 0.9f;
        w.lean0 = 0.04f + r.next() * 0.16f;
        w.lean1 = 0.32f + r.next() * 0.38f;
        w.tw = -off * (0.25f + r.next() * 0.35f);
        w.roll = (r.next() - 0.5f) * 2.6f;
        w.snap = 0.6f + r.next() * 0.8f;
        w.life = 0.8f + r.next() * 0.35f;
        t.wave.add(w);
        hole(w.base, r, 1.1f + r.next() * 0.7f, when);
        clods(w.base, dir, r, 2 + (int) Math.floor(r.next() * 3), when);
    }

    private static Hand[] hands(Vec3 center) {
        Hand[] out = new Hand[8];
        for (int i = 0; i < out.length; i++) {
            HandGen.Rng r = new HandGen.Rng(9100 + seedCount++);
            Hand h = new Hand();
            h.mesh = HandGen.hand(r, 2.6f);
            h.seed = r.next();
            h.sc = 1.35f + r.next() * 0.6f;
            h.ang = (float) (i / 8.0 * Math.PI * 2 + (r.next() - 0.5f) * 0.5f);
            h.rad = 0.7f + r.next() * 0.7f + 0.5f;
            h.h0 = 0.1f + r.next() * 1.15f;
            h.delay = r.next() * 0.18f;
            h.roll = (r.next() - 0.5f) * 2.5f;
            out[i] = h;
            float size = 1.6f + r.next() * 0.6f;
            hole(new Vec3(center.x + Math.cos(h.ang) * h.rad, center.y, center.z + Math.sin(h.ang) * h.rad), r, size, ShigFx.clock() + i * 0.02);
        }
        return out;
    }

    private static void hole(Vec3 at, HandGen.Rng r, float size, double when) {
        Hole h = new Hole();
        h.seed = r.next();
        h.rot = (float) (r.next() * Math.PI * 2);
        h.at = at.add(0, 0.02 + r.next() * 0.006, 0);
        h.size = size;
        h.t = when;
        h.life = 2.6f + r.next() * 1.2f;
        HOLES.add(h);
    }

    private static void clods(Vec3 at, Vec3 dir, HandGen.Rng r, int n, double when) {
        for (int k = 0; k < n; k++) {
            Clod c = new Clod();
            float s = 0.1f + r.next() * 0.2f, shade = 0.75f + r.next() * 0.4f;
            c.r = 0.36f * shade;
            c.g = 0.25f * shade;
            c.b = 0.15f * shade;
            c.sx = s;
            c.sy = s * (0.7f + r.next() * 0.5f);
            c.sz = s * (0.8f + r.next() * 0.4f);
            c.x = at.x;
            c.y = at.y + 0.1;
            c.z = at.z;
            c.floor = at.y;
            double a = r.next() * Math.PI * 2, out = 1 + r.next() * 2.2;
            c.vx = Math.cos(a) * out + dir.x * 1.5;
            c.vy = 3 + r.next() * 4;
            c.vz = Math.sin(a) * out + dir.z * 1.5;
            c.spx = (r.next() - 0.5f) * 14;
            c.spy = (r.next() - 0.5f) * 14;
            c.spz = (r.next() - 0.5f) * 14;
            // thrown when its hand breaks the ground: until then it waits in the hole (flies a negative time: none)
            c.wait = when - ShigFx.clock();
            CLODS.add(c);
        }
    }

    static void render(Vec3 camera) {
        double now = ShigFx.clock();
        float dt = lastFrame < 0 ? 0 : (float) Math.max(0, Math.min(0.1, now - lastFrame));
        lastFrame = now;
        for (T t : RUNS.values()) {
            t.wave.removeIf(w -> now - w.t > w.life + 0.05);
        }
        RUNS.values().removeIf(t -> t.wave.isEmpty() && ((t.caughtAt >= 0 && now - t.caughtAt > HOLD + DIS + 0.3) || (t.caughtAt < 0 && t.overAt >= 0)));
        RUNS.values().removeIf(t -> now - t.start > 30);
        HOLES.removeIf(h -> now - h.t > h.life);
        if (!active()) {
            return;
        }
        ShaderInstance mk = ShigShaders.mark, hs = ShigShaders.hand;
        float T = (float) (now % 1000);
        // the holes (under the hands: drawn first, they don't write depth)
        if (mk != null && !HOLES.isEmpty()) {
            mk.safeGetUniform("Mode").set(2f);
            for (Hole h : HOLES) {
                double e = now - h.t;
                if (e < 0) {
                    continue;
                }
                float open = ShigDraw.smooth((float) e / 0.12f);
                float alpha = 1 - ShigDraw.smooth((float) (e - h.life * 0.55) / (h.life * 0.45f));
                mk.safeGetUniform("Open").set(open);
                mk.safeGetUniform("A").set(alpha);
                mk.safeGetUniform("Seed").set(h.seed);
                BufferBuilder b = ShigDraw.begin();
                Vec3 along = new Vec3(Math.cos(h.rot), 0, Math.sin(h.rot)), side = new Vec3(-along.z, 0, along.x);
                decal(b, h.at.subtract(camera), along, side, h.size, h.size);
                ShigDraw.draw(b, mk, ShigDraw.ALPHA, false, false);
            }
        }
        if (hs == null) {
            return;
        }
        float[] o = ShigDraw.origin(camera);
        hs.safeGetUniform("Origin").set(o[0], o[1], o[2]);
        // the lumps of earth: one batch, lit like blocks (vertex alpha 0 = earth in the hand shader)
        if (!CLODS.isEmpty()) {
            hs.safeGetUniform("Ground").set(-1.0e4f);
            hs.safeGetUniform("Dis").set(0f);
            hs.safeGetUniform("Out").set(0f);
            BufferBuilder b = ShigDraw.begin();
            for (int i = CLODS.size() - 1; i >= 0; i--) {
                Clod c = CLODS.get(i);
                if (c.wait > 0) {
                    c.wait -= dt;
                    continue;
                }
                c.vy -= 22 * dt;
                c.x += c.vx * dt;
                c.y += c.vy * dt;
                c.z += c.vz * dt;
                c.age += dt;
                if (c.y < c.floor + c.sx * 0.4 && c.vy < 0) {
                    CLODS.remove(i);
                    continue;
                }
                cube(b, c, camera, (float) c.age);
            }
            ShigDraw.draw(b, hs, ShigDraw.OPAQUE, false, false);
        }
        for (T t : RUNS.values()) {
            // the hands of the run: break the ground, lean forward trying to grab, crumble to dust
            for (Wave w : t.wave) {
                double e = now - w.t;
                if (e < 0) {
                    continue;
                }
                float r = ShigDraw.clamp((float) e / 0.15f, 0, 1);
                float up = r < 1 ? 1 + 2.4f * (float) Math.pow(r - 1, 3) + 1.4f * (float) Math.pow(r - 1, 2) : 1;
                float snatch = ShigDraw.smooth((float) (e - 0.1) / 0.28f);
                float dis = ShigDraw.smooth((float) (e - w.life * 0.5) / (w.life * 0.5f)) * 1.05f;
                float lean = w.lean0 + (w.lean1 - w.lean0) * snatch;
                Vec3 axis = new Vec3(0, Math.cos(lean), 0).add(w.dir.scale(Math.sin(lean))).add(w.side.scale(w.tw)).normalize();
                float reach = w.h0 * up - 2.0f * (1 - up) + 0.25f * snatch - 0.35f * dis;
                Vec3 pos = w.base.add(axis.scale(reach));
                Vec3 look = axis.add(w.dir.scale(0.45 * snatch * w.snap)).normalize();
                float roll = w.roll + snatch * 0.8f * w.snap + (float) Math.sin(T * 9 + w.roll * 7) * 0.05f;
                hs.safeGetUniform("Ground").set((float) (w.base.y - camera.y));
                hs.safeGetUniform("Dis").set(dis);
                hs.safeGetUniform("Seed").set(w.seed);
                for (int pass = 0; pass < 2; pass++) {
                    hs.safeGetUniform("Out").set(pass == 0 ? 0f : 0.035f);
                    BufferBuilder b = ShigDraw.begin();
                    hand(b, w.mesh, w.sc, pos.subtract(camera), look, roll, pass == 0 ? 0 : 0.035f);
                    ShigDraw.draw(b, hs, ShigDraw.OPAQUE, true, pass == 1);
                }
            }
            // held: the grip hands
            double e = t.caughtAt < 0 ? -1 : now - t.caughtAt;
            if (t.hands != null && e >= 0) {
                Entity target = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(t.target);
                Vec3 c = target != null && target.isAlive() ? target.position() : t.at;
                hs.safeGetUniform("Ground").set((float) (t.at.y - camera.y));
                for (Hand h : t.hands) {
                    float r = ShigDraw.clamp((float) (e - h.delay) / 0.28f, 0, 1);
                    float up = r < 1 ? 1 + 2.2f * (float) Math.pow(r - 1, 3) + 1.2f * (float) Math.pow(r - 1, 2) : 1;
                    double bx = c.x + Math.cos(h.ang) * h.rad, bz = c.z + Math.sin(h.ang) * h.rad, gy = t.at.y;
                    float grip = ShigDraw.smooth((float) (e - 0.3) / 0.4f);
                    double k = 0.3 * (1 - grip);
                    double tx = c.x + (bx - c.x) * k, ty = gy + (h.h0 + 0.45 - grip * 0.2) + (3.2 - (h.h0 + 0.45 - grip * 0.2)) * k, tz = c.z + (bz - c.z) * k;
                    Vec3 base = new Vec3(bx, gy, bz), tgt = new Vec3(tx, ty, tz);
                    Vec3 dv = tgt.subtract(base).normalize();
                    float reach = (0.9f + h.h0 * 0.85f) * up - 2.2f * (1 - up);
                    Vec3 pos = base.add(dv.scale(reach));
                    Vec3 look = lerp(tgt.subtract(pos).normalize(), dv, 0.3).normalize();
                    float roll = h.roll + (float) Math.sin(T * 3 + h.ang) * 0.06f;
                    float dis = ShigDraw.smooth((float) (e - HOLD - h.delay * 0.5) / DIS) * 1.05f;
                    hs.safeGetUniform("Dis").set(dis);
                    hs.safeGetUniform("Seed").set(h.seed);
                    for (int pass = 0; pass < 2; pass++) {
                        hs.safeGetUniform("Out").set(pass == 0 ? 0f : 0.035f);
                        BufferBuilder b = ShigDraw.begin();
                        hand(b, h.mesh, h.sc, pos.subtract(camera), look, roll, pass == 0 ? 0 : 0.035f);
                        ShigDraw.draw(b, hs, ShigDraw.OPAQUE, true, pass == 1);
                    }
                }
            }
        }
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double k) {
        return a.add(b.subtract(a).scale(k));
    }

    // A hand at p (camera-relative), its +Z looking along look (three.js lookAt for objects), turned by roll around it.
    static void hand(BufferBuilder b, HandGen.Mesh m, float s, Vec3 p, Vec3 look, float roll, float outline) {
        Vec3 z = look, x = new Vec3(0, 1, 0).cross(z);
        if (x.lengthSqr() < 1e-6) {
            x = new Vec3(1, 0, 0);
        }
        x = x.normalize();
        Vec3 y = z.cross(x);
        double cr = Math.cos(roll), sr = Math.sin(roll);
        Vec3 X = x.scale(cr).add(y.scale(sr)), Y = x.scale(-sr).add(y.scale(cr));
        for (int i = 0; i < m.ni; i++) {
            int v = m.idx[i];
            float px = m.pos[v * 3], py = m.pos[v * 3 + 1], pz = m.pos[v * 3 + 2];
            float nx = m.nrm[v * 3], ny = m.nrm[v * 3 + 1], nz = m.nrm[v * 3 + 2];
            double wnx = X.x * nx + Y.x * ny + z.x * nz, wny = X.y * nx + Y.y * ny + z.y * nz, wnz = X.z * nx + Y.z * ny + z.z * nz;
            double lx = px + nx * outline, ly = py + ny * outline, lz = pz + nz * outline;
            double wx = p.x + (X.x * lx + Y.x * ly + z.x * lz) * s, wy = p.y + (X.y * lx + Y.y * ly + z.y * lz) * s, wz = p.z + (X.z * lx + Y.z * ly + z.z * lz) * s;
            ShigDraw.v(b, (float) wx, (float) wy, (float) wz, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, (float) wnx, (float) wny, (float) wnz);
        }
    }

    // A lump: a box of its own size, turned by its spin (age seconds of it), its colour with alpha 0 (earth).
    private static void cube(BufferBuilder b, Clod c, Vec3 camera, float age) {
        float ax = c.spx * age, ay = c.spy * age, az = c.spz * age;
        float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax), cy = (float) Math.cos(ay), sy = (float) Math.sin(ay), cz = (float) Math.cos(az),
                sz = (float) Math.sin(az);
        // rotation X then Y then Z (three.js Euler XYZ)
        float[][] m = {{cy * cz, -cy * sz, sy}, {cx * sz + sx * sy * cz, cx * cz - sx * sy * sz, -sx * cy},
                {sx * sz - cx * sy * cz, sx * cz + cx * sy * sz, cx * cy}};
        float ox = (float) (c.x - camera.x), oy = (float) (c.y - camera.y), oz = (float) (c.z - camera.z);
        float[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        float[] half = {c.sx * 0.5f, c.sy * 0.5f, c.sz * 0.5f};
        for (float[] n : faces) {
            int ax0 = n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2, a1 = (ax0 + 1) % 3, a2 = (ax0 + 2) % 3;
            float sgn = n[ax0];
            float[][] corners = new float[4][3];
            float[][] uv = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int k = 0; k < 4; k++) {
                corners[k][ax0] = sgn * half[ax0];
                // wind counter-clockwise seen from outside
                corners[k][a1] = uv[k][sgn > 0 ? 0 : 1] * half[a1];
                corners[k][a2] = uv[k][sgn > 0 ? 1 : 0] * half[a2];
            }
            float wnx = m[0][0] * n[0] + m[0][1] * n[1] + m[0][2] * n[2], wny = m[1][0] * n[0] + m[1][1] * n[1] + m[1][2] * n[2],
                    wnz = m[2][0] * n[0] + m[2][1] * n[1] + m[2][2] * n[2];
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int k : order) {
                float[] p = corners[k];
                float wx = ox + m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2], wy = oy + m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2],
                        wz = oz + m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2];
                ShigDraw.v(b, wx, wy, wz, c.r, c.g, c.b, 0, 0, 0, 0, 0, 0, 0, wnx, wny, wnz);
            }
        }
    }

    private static void decal(BufferBuilder b, Vec3 c, Vec3 along, Vec3 side, float len, float width) {
        Vec3 a = along.scale(len / 2), s = side.scale(width / 2);
        Vec3 p00 = c.subtract(a).subtract(s), p10 = c.add(a).subtract(s), p11 = c.add(a).add(s), p01 = c.subtract(a).add(s);
        Vec3[] ps = {p00, p10, p11, p00, p11, p01};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 6; i++) {
            ShigDraw.v(b, (float) ps[i].x, (float) ps[i].y, (float) ps[i].z, 1, 1, 1, 1, uv[i][0], uv[i][1], 0, 0, 0, 0, 0, 1, 0);
        }
    }
}
