package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// C, Mãos do Chão (preview v4, APROVADÍSSIMO): the palm slaps the ground, a crack runs under it to a hand print (both
// drawn by souls_shig_mark, signed distances, no texture); armed, the print fades to a hint. Whoever steps on it: eight
// hands, each its own (HandGen with a long forearm), burst out of the ground around it (ease-out-back in 0.28 s), grip
// it (0.3 to 0.7 s), hold it 2.6 s shaking a little, and crumble to dust (0.8 s). The forearms stay under the ground.
public final class TrapFx {
    private static final float HOLD = 2.6f, DIS = 0.8f;
    private static int seedCount;

    private static final class Hand {
        HandGen.Mesh mesh;
        float seed, sc, ang, rad, h0, delay, roll;
    }

    private static final class T {
        final double start;
        final Vec3 print, dir, slap;
        final float len;
        double caughtAt = -1, goneAt = -1;
        int target = -1;
        Vec3 at;
        Hand[] hands;

        T(double start, Vec3 print, Vec3 dir, Vec3 slap) {
            this.start = start;
            this.print = print;
            this.dir = dir;
            this.slap = slap;
            this.len = (float) Math.max(0.5, Math.hypot(print.x - slap.x, print.z - slap.z));
        }
    }

    private static final Map<Integer, T> TRAPS = new HashMap<>();

    private TrapFx() {
    }

    static boolean active() {
        return !TRAPS.isEmpty();
    }

    static void clear() {
        TRAPS.clear();
    }

    // Shigaraki.TRAP: id = the trap; a = 0 slap (pos = the print, dir = the way, c = the caster), 1 armed, 2 caught
    // (pos = where, b = who), 3 gone.
    static void receive(int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ShigFx.clock();
        int mode = Math.round(a);
        if (mode == 0) {
            var level = Minecraft.getInstance().level;
            Entity caster = level == null ? null : level.getEntity(Math.round(c));
            Vec3 feet = caster != null ? caster.position() : pos.subtract(dir.scale(9));
            Vec3 slap = new Vec3(feet.x + dir.x * 0.9, pos.y, feet.z + dir.z * 0.9);
            TRAPS.put(id, new T(now, pos, dir, slap));
            return;
        }
        T t = TRAPS.get(id);
        if (t == null) {
            return;
        }
        if (mode == 2 && t.caughtAt < 0) {
            t.caughtAt = now;
            t.target = Math.round(b);
            t.at = pos;
            t.hands = hands();
        } else if (mode == 3 && t.caughtAt < 0) {
            t.goneAt = now;
        }
    }

    private static Hand[] hands() {
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
        }
        return out;
    }

    static void render(Vec3 camera) {
        double now = ShigFx.clock();
        TRAPS.values().removeIf(t -> (t.goneAt >= 0 && now - t.goneAt > 0.4) || (t.caughtAt >= 0 && now - t.caughtAt > HOLD + DIS + 0.3)
                || now - t.start > 30);
        if (TRAPS.isEmpty()) {
            return;
        }
        ShaderInstance mk = ShigShaders.mark, hs = ShigShaders.hand;
        float T = (float) (now % 1000);
        for (T t : TRAPS.values()) {
            float age = (float) (now - t.start);
            double e = t.caughtAt < 0 ? -1 : now - t.caughtAt;
            boolean show = t.caughtAt < 0 || e < HOLD;
            float out = t.goneAt >= 0 ? 1 - ShigDraw.smooth((float) (now - t.goneAt) / 0.4f) : 1;
            if (mk != null && show) {
                float reveal = ShigDraw.smooth((age - 0.36f) / 0.55f);
                float A = (age < 1.3f ? 0.9f : 0.42f) * out;
                float mark = ShigDraw.smooth((age - 0.85f) / 0.25f) * (age < 1.3f ? 1 : 0.55f) * out;
                mk.safeGetUniform("Reveal").set(reveal);
                mk.safeGetUniform("A").set(A);
                mk.safeGetUniform("Len").set(t.len);
                mk.safeGetUniform("Mark").set(mark);
                Vec3 d = t.dir, side = new Vec3(-d.z, 0, d.x);
                // the crack: x along from the slap to the print, 1.2 wide
                mk.safeGetUniform("Mode").set(0f);
                BufferBuilder b = ShigDraw.begin();
                Vec3 mid = t.slap.add(t.print).scale(0.5);
                decal(b, mid.add(0, 0.02, 0).subtract(camera), d, side, t.len, 1.2f);
                ShigDraw.draw(b, mk, ShigDraw.ALPHA, false, false);
                if (mark > 0.005f) {
                    mk.safeGetUniform("Mode").set(1f);
                    b = ShigDraw.begin();
                    // the print: 6.5 square, the fingers pointing along the way
                    decalPrint(b, t.print.add(0, 0.025, 0).subtract(camera), d, side, 6.5f);
                    ShigDraw.draw(b, mk, ShigDraw.ALPHA, false, false);
                }
            }
            if (hs != null && t.hands != null && e >= 0) {
                Entity target = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(t.target);
                Vec3 c = target != null && target.isAlive() ? target.position() : t.at;
                float[] o = ShigDraw.origin(camera);
                hs.safeGetUniform("Origin").set(o[0], o[1], o[2]);
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
                        hand(b, h, pos.subtract(camera), look, roll, pass == 0 ? 0 : 0.035f);
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
    static void hand(BufferBuilder b, Hand h, Vec3 p, Vec3 look, float roll, float outline) {
        Vec3 z = look, x = new Vec3(0, 1, 0).cross(z);
        if (x.lengthSqr() < 1e-6) {
            x = new Vec3(1, 0, 0);
        }
        x = x.normalize();
        Vec3 y = z.cross(x);
        double cr = Math.cos(roll), sr = Math.sin(roll);
        Vec3 X = x.scale(cr).add(y.scale(sr)), Y = x.scale(-sr).add(y.scale(cr));
        HandGen.Mesh m = h.mesh;
        float s = h.sc;
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

    private static void decal(BufferBuilder b, Vec3 c, Vec3 along, Vec3 side, float len, float width) {
        Vec3 a = along.scale(len / 2), s = side.scale(width / 2);
        Vec3 p00 = c.subtract(a).subtract(s), p10 = c.add(a).subtract(s), p11 = c.add(a).add(s), p01 = c.subtract(a).add(s);
        quad(b, p00, p10, p11, p01, 0, 0, 1, 1);
    }

    // The print: plane x along side, y (the fingers) along the way.
    private static void decalPrint(BufferBuilder b, Vec3 c, Vec3 along, Vec3 side, float size) {
        Vec3 a = along.scale(size / 2), s = side.scale(size / 2);
        Vec3 p00 = c.subtract(s).subtract(a), p10 = c.add(s).subtract(a), p11 = c.add(s).add(a), p01 = c.subtract(s).add(a);
        quad(b, p00, p10, p11, p01, 0, 0, 1, 1);
    }

    private static void quad(BufferBuilder b, Vec3 p00, Vec3 p10, Vec3 p11, Vec3 p01, float u0, float v0, float u1, float v1) {
        Vec3[] ps = {p00, p10, p11, p00, p11, p01};
        float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 6; i++) {
            ShigDraw.v(b, (float) ps[i].x, (float) ps[i].y, (float) ps[i].z, 1, 1, 1, 1, uv[i][0], uv[i][1], 0, 0, 0, 0, 0, 1, 0);
        }
    }
}
