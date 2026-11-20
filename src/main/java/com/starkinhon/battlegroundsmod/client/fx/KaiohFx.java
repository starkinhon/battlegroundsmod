// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.easeOut;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.smooth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.power.powers.sub.db.KaiohNet;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// What Senhor Kaioh teaches Goku: the Mini Genki Dama (power/powers/sub/db/KaiohNet, SubNet kinds 500-519), drawn as the
// approved preview draws it (mestres/mini-genki.html v3, Stark 2026-11-19), with its shaders carried over to souls_ki
// kinds 25-29 and 36-38: the ground's energy rising in a spiral to the open hand over a floor of light waves running in,
// the ball (light body with veins) and its halo of rays, the twisted trail of the guided throw, the dust ring closing as
// it sinks in, then the column of light (the v1 explosion), the shock ring, the flash and the cel smoke. The ground
// itself breaks for real (the server's crater). Queued once a frame, in DbFx's glow pass, like the Genki Dama.
public final class KaiohFx {
    private static final float[] FLASH = FxDraw.rgb(0xE8F6FF);

    private static final class Ball {
        Vec3 pos;
        Vec3 prev;
        Vec3 ground;
        Vec3 from;
        float r;
        float want;
        float k;
        int state;
        final double born;
        double updated;
        double thrownAt = -1;
        double endAt = -1;

        Ball(Vec3 pos, double now) {
            this.pos = pos;
            this.prev = pos;
            this.born = now;
            this.updated = now;
        }
    }

    private record Boom(Vec3 pos, float ground, float hitY, float size, int seed, double born) {
    }

    private static final Map<Integer, Ball> BALLS = new HashMap<>();
    private static final List<Boom> BOOMS = new ArrayList<>();
    // the landing's timeline in ticks (the page: T_HIT -> T_POP 0.2 s, the column 1.6 s, the smoke to 2.9 s)
    private static final float SINK = 4;
    private static final float BOOM_LIFE = SINK + 60;

    private KaiohFx() {
    }

    static boolean handles(int kind) {
        return kind >= KaiohNet.FIRST && kind <= KaiohNet.LAST;
    }

    static boolean active() {
        return !BALLS.isEmpty() || !BOOMS.isEmpty();
    }

    static void clear() {
        BALLS.clear();
        BOOMS.clear();
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        if (kind == KaiohNet.MINI_GENKI_BOOM) {
            BOOMS.add(new Boom(pos, (float) dir.x, (float) dir.y, a, (int) b, now));
            return;
        }
        if (kind != KaiohNet.MINI_GENKI) {
            return;
        }
        Ball bl = BALLS.computeIfAbsent(id, k -> new Ball(pos, now));
        int state = (int) b;
        if (state >= 2) {
            if (bl.endAt < 0) {
                bl.endAt = now;
            }
            bl.state = 2;
            return;
        }
        if (state == 0) {
            bl.ground = dir;
            bl.want = c;
        } else {
            bl.want = 0;
            if (bl.thrownAt < 0) {
                bl.thrownAt = now;
                bl.from = bl.pos;
            }
        }
        // the spiral comes in over the first 7 ticks and goes in 4 as the hand pulls back
        bl.k += Mth.clamp(bl.want - bl.k, -0.25f, 1 / 7f);
        bl.prev = bl.pos;
        bl.pos = pos;
        bl.r = a;
        bl.state = state;
        bl.updated = now;
    }

    private static void prune(double now) {
        Iterator<Ball> it = BALLS.values().iterator();
        while (it.hasNext()) {
            Ball bl = it.next();
            if (bl.endAt < 0 && now - bl.updated > 6) {
                bl.endAt = now;
            }
            if (bl.endAt >= 0 && now - bl.endAt > 4) {
                it.remove();
            }
        }
        BOOMS.removeIf(bm -> now - bm.born() > BOOM_LIFE);
    }

    static void draw(double now, float partial) {
        if (!DbFx.pass) {
            return;
        }
        prune(now);
        if (!KiMesh.ready()) {
            return;
        }
        for (Ball bl : BALLS.values()) {
            ball(bl, now, partial);
        }
        for (Boom bm : BOOMS) {
            boom(bm, (float) (now - bm.born()) + partial);
        }
    }

    private static void ball(Ball bl, double now, float partial) {
        float age = (float) (now - bl.born) + partial;
        float time = age / 20f;
        float out = bl.endAt < 0 ? 1 : 1 - Mth.clamp((float) (now - bl.endAt + partial) / 3f, 0, 1);
        Vec3 at = bl.state == 2 ? bl.pos : bl.prev.lerp(bl.pos, partial);
        Vector3f p = rel(at);
        // the spiral of the ground's energy and the floor of light under the hand (charging)
        if (bl.k > 0.01f && bl.ground != null && bl.endAt < 0) {
            Vector3f g = rel(bl.ground);
            float lift = Math.max(0.4f, p.y - g.y);
            KiMesh.P sp = KiMesh.p(25);
            sp.time = time;
            sp.life = bl.k;
            sp.len = lift;
            KiMesh.queue(KiMesh.Mesh.TUBE, new Matrix4f().translation(g.x, g.y, g.z), sp, KiMesh.BOTH, 10);
            KiMesh.P fl = KiMesh.p(26);
            fl.time = time;
            fl.life = bl.k;
            // centered on a block corner, so its cells sit on the world's blocks
            Vector3f c = rel(new Vec3(Math.floor(bl.ground.x + 0.5), bl.ground.y + 0.02, Math.floor(bl.ground.z + 0.5)));
            KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(c, 3.8f), fl, KiMesh.BOTH, 5);
        }
        float r = bl.r;
        // landed: the landing draws the ball sinking in from here (only the trail fades)
        boolean landed = bl.state == 2 && bl.thrownAt >= 0;
        if (r > 0.01f && out > 0.002f && !landed) {
            KiMesh.P core = KiMesh.p(36);
            core.time = time;
            core.alpha = out;
            KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(p, r), core, KiMesh.FRONT, 12);
            KiMesh.P halo = KiMesh.p(37);
            halo.time = time;
            halo.alpha = out;
            KiMesh.queue(KiMesh.Mesh.HALO, KiMesh.sphere(p, r * 1.6f), halo, KiMesh.BACK, 13);
        }
        // the twisted trail behind the guided ball, at most 6 blocks, fading in 0.12 s once it lands
        if (bl.thrownAt >= 0 && bl.from != null && out > 0.002f) {
            Vec3 back = bl.pos.subtract(bl.prev);
            if (back.lengthSqr() > 1.0E-4) {
                float len = (float) Math.min(6, at.distanceTo(bl.from));
                if (len > 0.2f) {
                    Vec3 d = back.normalize().scale(-1);
                    KiMesh.P tr = KiMesh.p(27);
                    tr.time = time;
                    tr.alpha = out;
                    tr.len = len;
                    tr.w = 0.55f * Math.max(0.6f, r / 0.72f);
                    KiMesh.queue(KiMesh.Mesh.TUBE, KiMesh.along(p, new Vector3f((float) d.x, (float) d.y, (float) d.z)), tr, KiMesh.BOTH, 11);
                }
            }
        }
    }

    private static void boom(Boom bm, float age) {
        float s = bm.size();
        float time = age / 20f;
        Vector3f c = rel(bm.pos());
        float foot = (float) (bm.ground() - DbFx.cam.y);
        Vector3f base = new Vector3f(c.x, foot, c.z);
        if (age < SINK) {
            // it sinks in: the ball swells a little and the dust ring closes on it
            float k = age / SINK;
            float r = 0.72f * s * (1 + 0.6f * Mth.sin(k * Mth.PI * 0.5f)) * (1 - k * 0.15f);
            Vector3f hit = new Vector3f(c.x, (float) (bm.hitY() - DbFx.cam.y), c.z);
            KiMesh.P core = KiMesh.p(36);
            core.time = time;
            KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(hit, r), core, KiMesh.FRONT, 12);
            KiMesh.P halo = KiMesh.p(37);
            halo.time = time;
            KiMesh.queue(KiMesh.Mesh.HALO, KiMesh.sphere(hit, r * 1.6f), halo, KiMesh.BACK, 13);
            ring(base, (6 * (1 - easeOut(k)) + 0.6f) * s, 0.9f * s, smooth(k / 0.2f), time, 1);
            return;
        }
        float tp = (age - SINK) / 20f;
        // the column of light (the first version's explosion, Stark 03:37), tearing from the top as it rises
        if (tp < 1.6f) {
            float g = easeOut(tp / 0.35f);
            KiMesh.P pl = KiMesh.p(29);
            pl.time = time;
            pl.life = smooth((tp - 0.15f) / 1.45f);
            float wide = (1.2f + 0.9f * g) * s;
            KiMesh.queue(KiMesh.Mesh.TUBE, new Matrix4f().translation(base).scale(wide, Math.max(0.01f, 14 * g * s), wide), pl, KiMesh.BOTH, 14);
        }
        if (tp < 0.7f) {
            ring(new Vector3f(base.x, base.y + 0.03f, base.z), (0.5f + 10 * easeOut(tp / 0.6f)) * s, 0.5f * s, 1 - smooth((tp - 0.3f) / 0.4f), time, 0);
        }
        if (tp < 0.18f) {
            KiMesh.P fl = KiMesh.p(KiMesh.FLASH);
            fl.color = FLASH;
            fl.alpha = 1 - tp / 0.18f;
            KiMesh.queue(KiMesh.Mesh.FLASH, KiMesh.sphere(new Vector3f(base.x, base.y + 0.8f * s, base.z), (1.5f + 4 * Mth.sqrt(tp / 0.18f)) * s), fl,
                    KiMesh.FRONT, 15);
        }
        if (tp > 0.1f && tp < 2.9f) {
            KiMesh.P sm = KiMesh.p(38);
            sm.time = time;
            sm.life = smooth((tp - 0.4f) / 2.5f);
            sm.glow = false;
            float g = easeOut((tp - 0.1f) / 0.9f);
            KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(base, (1.6f + 2.4f * g) * s), sm, KiMesh.FRONT, 9);
        }
    }

    // A ring on the ground (the page's ringMat): dust 1 = the closing dust ring, 0 = the lit shock ring.
    private static void ring(Vector3f at, float radius, float width, float alpha, float time, float dust) {
        if (alpha <= 0.003f) {
            return;
        }
        KiMesh.P rg = KiMesh.p(28);
        rg.time = time;
        rg.ringR = radius;
        rg.w = width;
        rg.alpha = alpha;
        rg.smoke = dust;
        rg.glow = dust < 0.5f;
        KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(at, radius + width * 1.6f + 1), rg, KiMesh.BOTH, 6);
    }
}
