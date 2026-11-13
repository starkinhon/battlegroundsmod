package com.starkinhon.battlegroundsmod.client.fx.hs;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.fx.hs.HsAssets.Part;
import com.starkinhon.battlegroundsmod.client.fx.hs.WoodGeo.Geo;
import com.starkinhon.battlegroundsmod.client.fx.hs.WoodGeo.Rng;
import com.starkinhon.battlegroundsmod.power.powers.hashirama.HsNet;

import net.minecraft.world.phys.Vec3;

// Mokuton Hijutsu: Shinsūsenju, the approved page's buda scene (forja/hashirama-boss/hashirama.js SCENES.buda, fan.js).
// The page's frame sits where Hashirama clapped the ground, page -z towards his target; the Buddha stands BZ behind him.
// a = 0: rises out of the ground (the fan opens row by row); then SLAM events (pos = the ground point, a = 1 the Top
// Strike's giant palm) send one of the fan's own arms: it lifts out of its row, stretches (thickening) round the statue's
// shoulder, its shadow closes on the ground, it slams, holds, and goes back into its place. a = 2: it dissolves.
final class HsBuddha extends HsFx.Fx {
    static final double BZ = 6.5, RISE = 2.4;
    static final String[] ROWC = {"#ad8659", "#9a734a", "#a77f52", "#94704a", "#a47c51", "#8f6b45", "#9e784e"};

    // one row of the fan: its pose this frame
    static final class Row {
        int k;
        double cx, cy, cz, sx, rx, rz;
        boolean visible;
        final Matrix4f m = new Matrix4f();
        final Quaternionf q = new Quaternionf();
    }

    // one attack arm (a fan arm taken out of its row)
    static final class Arm {
        Part hand;
        Row row;
        double th, hs;
        Vector3d[] local, idleCurve = new Vector3d[16], pts = new Vector3d[16];
        double[] rad, radT = new double[16];
        Vector3d hPos;
        Quaternionf hQ, iq = new Quaternionf(), handQ = new Quaternionf();
        Vector3d anchor = new Vector3d(), idle = new Vector3d(), from = new Vector3d(), to = new Vector3d(), handPos = new Vector3d();
        double scale = 1, rs = 1, t;
        String state = "idle";
        boolean big;
        Geo geo;
        HsWood.Mat mat;
    }

    final List<Row> rows = new ArrayList<>();
    final List<Arm> arms = new ArrayList<>();
    int[] order;
    double y = -27, fanU, lastT, finalAt = -1, disAt = -1;
    double top = 17.2, headZ = -0.65;
    final float seed = HsFx.RNG.nextFloat() * 30;

    HsBuddha() {
        List<Part> parts = HsAssets.get("buddha");
        top = parts.get(0).f("topY", 17.2f);
        headZ = parts.get(0).f("headZ", -0.65f);
        for (int k = 0; k < 7; k++) {
            Row r = new Row();
            r.k = k;
            rows.add(r);
        }
        for (Part p : parts) {
            String role = p.s("role");
            if ("row".equals(role)) {
                Row r = rows.get((int) p.f("k", 0));
                r.cx = p.f("cx", 0);
                r.cy = p.f("cy", 10);
                r.cz = p.f("cz", 0.4f);
            } else if ("hand".equals(role)) {
                Arm a = new Arm();
                a.hand = p;
                a.row = rows.get((int) p.f("k", 0));
                a.th = p.f("th", 0);
                a.hs = p.f("hs", 2);
                float[] pts = p.arr("pts"), rad = p.arr("rad"), hp = p.arr("hp"), hq = p.arr("hq");
                a.local = new Vector3d[16];
                a.rad = new double[16];
                for (int i = 0; i < 16; i++) {
                    a.local[i] = new Vector3d(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]);
                    a.idleCurve[i] = new Vector3d(a.local[i]);
                    a.pts[i] = new Vector3d(a.local[i]);
                    a.rad[i] = rad[i];
                    a.radT[i] = rad[i];
                }
                a.hPos = new Vector3d(hp[0], hp[1], hp[2]);
                a.hQ = new Quaternionf(hq[0], hq[1], hq[2], hq[3]);
                a.geo = WoodGeo.tubeGeo(a.pts, a.rad, 8, 1);
                a.geo.surf = true;
                a.geo.ao = new float[a.geo.nv];
                for (int i = 0; i < a.geo.nv; i++) {
                    a.geo.ao[i] = (float) (0.5 + 0.5 * WoodGeo.smooth(Math.floor(i / 9.0) / 15 * 1.3));
                }
                a.mat = HsWood.Mat.surf(300 + a.row.k, ROWC[a.row.k], "#2c1a0b", 5);
                arms.add(a);
            }
        }
        // the page's startRain order: arms shuffled by the seed 5150
        Rng r = new Rng(5150);
        List<Integer> o = new ArrayList<>();
        for (int i = 0; i < arms.size(); i++) {
            o.add(i);
        }
        double[] keys = new double[arms.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = r.next();
        }
        o.sort((x, z) -> Double.compare(keys[x], keys[z]));
        order = o.stream().mapToInt(Integer::intValue).toArray();
        setFan(0, 0);
        for (Arm a : arms) {
            refreshIdle(a);
            a.handQ.set(a.iq);
            layArm(a, a.idle);
        }
    }

    // fan opening row by row, from inside out
    void setFan(double u, double tm) {
        fanU = u;
        for (Row r : rows) {
            double v = WoodGeo.smooth((u - r.k * 0.13) / 0.48), sd = r.k % 2 == 1 ? 1 : -1;
            r.visible = v > 0.01;
            r.sx = 0.06 + 0.94 * WoodGeo.easeOut3(v);
            r.rz = (1 - v) * 0.5 * sd + (tm != 0 ? Math.sin(tm * 0.6 + r.k * 1.3) * 0.012 * v : 0);
            r.rx = (1 - v) * 0.6;
            r.m.identity().translate((float) r.cx, (float) r.cy, (float) r.cz).rotateXYZ((float) r.rx, 0, (float) r.rz).scale((float) r.sx);
            r.q.identity().rotateXYZ((float) r.rx, 0, (float) r.rz);
        }
    }

    void refreshIdle(Arm a) {
        Matrix4f m = a.row.m;
        for (int k = 0; k < 16; k++) {
            Vector3f p = m.transformPosition(new Vector3f((float) a.local[k].x, (float) a.local[k].y, (float) a.local[k].z));
            a.idleCurve[k].set(p.x, p.y, p.z);
        }
        a.anchor.set(a.idleCurve[0]);
        Vector3f h = m.transformPosition(new Vector3f((float) a.hPos.x, (float) a.hPos.y, (float) a.hPos.z));
        a.idle.set(h.x, h.y, h.z);
        a.iq.set(a.row.q).mul(a.hQ);
        a.rs = a.row.sx;
    }

    // the page's layArm: near its rest the fan's own shape moved; far away an arc out round the statue's shoulder
    void layArm(Arm a, Vector3d wrist) {
        Vector3d A = a.anchor, mid = new Vector3d(A).lerp(wrist, 0.5);
        mid.y += 3.5 + A.distance(wrist) * 0.15;
        mid.z -= 0.6;
        mid.x += Math.signum(A.x == 0 ? 1 : A.x) * (2.5 + Math.abs(A.x) * 0.3);
        Vector3d off = new Vector3d(wrist).sub(a.idle);
        double e = WoodGeo.smooth(off.length() / 5);
        for (int k = 0; k < 16; k++) {
            double u = k / 15.0;
            Vector3d arc = new Vector3d(A).lerp(mid, u).lerp(new Vector3d(mid).lerp(wrist, u), u);
            Vector3d fanP = new Vector3d(a.idleCurve[k]).fma(Math.pow(u, 1.5), off);
            a.pts[k].set(fanP.lerp(arc, e));
        }
        double th = 1 + 0.55 * e;
        for (int k = 0; k < 16; k++) {
            a.radT[k] = a.rad[k] * th;
        }
        WoodGeo.updateTube(a.geo, a.pts, a.radT);
        a.handPos.set(wrist);
    }

    Arm pick(int n, boolean big) {
        if (big) {
            Arm best = arms.get(0);
            for (Arm a : arms) {
                if (Math.abs(a.th) < Math.abs(best.th)) {
                    best = a;
                }
            }
            return best;
        }
        for (int i = 0; i < arms.size(); i++) {
            Arm a = arms.get(order[(n + i) % arms.size()]);
            if ("idle".equals(a.state)) {
                return a;
            }
        }
        return null;
    }

    @Override
    void event(int kind, Vec3 p, Vec3 d, float a, float b, float c, double now) {
        double t = now - born;
        if (kind == HsNet.BUDDHA) {
            if (a == 2 && disAt < 0) {
                disAt = t;
            }
            return;
        }
        if (kind == HsNet.SLAM) {
            boolean big = a > 0.5f;
            Arm ar = pick((int) b, big);
            if (ar == null) {
                return;
            }
            Vector3d at = new Vector3d((p.x - pos.x) / HsWood.PU, (p.y - pos.y) / HsWood.PU, (p.z - pos.z) / HsWood.PU);
            // into the page frame (turned by yaw)
            double cs = Math.cos(-yaw), sn = Math.sin(-yaw);
            ar.to.set(at.x * cs + at.z * sn, Math.max(0, at.y), -at.x * sn + at.z * cs);
            ar.state = "warn";
            ar.t = 0;
            ar.big = big;
            if (big) {
                ar.scale = 3.0;
                finalAt = t;
            }
        }
    }

    @Override
    boolean draw(double t, Vec3 cam) {
        double dt = Math.max(0, Math.min(0.1, t - lastT));
        lastT = t;
        if (disAt >= 0 && t > disAt + 1.6 || t > 90) {
            return false;
        }
        double now = HsFx.now();
        double rise = WoodGeo.easeOut3(t / RISE);
        y = -27 + 27 * rise;
        setFan(WoodGeo.clamp((t - 0.8) / 2.0, 0, 1), now);
        Matrix4f m = frame(cam);
        Matrix4f bm = new Matrix4f(m).translate(0, (float) y, (float) BZ);
        float dis = disAt >= 0 ? (float) WoodGeo.smooth((t - disAt) / 1.5) : 0;
        List<Part> parts = HsAssets.get("buddha");
        for (Part p : parts) {
            String role = p.s("role");
            if ("statue".equals(role)) {
                HsWood.draw(p.g(), p.m(), bm, 2, dis, bright);
            } else if ("row".equals(role)) {
                Row r = rows.get((int) p.f("k", 0));
                if (r.visible) {
                    HsWood.draw(p.g(), p.m(), new Matrix4f(bm).mul(r.m), 2, dis, bright);
                }
            }
        }
        for (Arm a : arms) {
            refreshIdle(a);
            if (!a.row.visible) {
                continue;
            }
            stepArm(a, dt, now, t, m);
            Matrix4f hm = new Matrix4f(bm).translate((float) a.handPos.x, (float) a.handPos.y, (float) a.handPos.z).rotate(a.handQ).scale((float) (a.big ? a.scale * a.rs : a.rs));
            HsWood.draw(a.geo, a.mat, bm, 2, dis, bright);
            HsWood.draw(a.hand.g(), a.hand.m(), hm, 2, dis, bright);
        }
        return true;
    }

    void stepArm(Arm a, double dt, double now, double t, Matrix4f m) {
        Quaternionf idleQ = a.iq;
        if ("idle".equals(a.state)) {
            Vector3d w = new Vector3d(a.idle).add(Math.sin(now * 0.9 + a.th * 3) * 0.15, Math.cos(now * 0.7 + a.th * 5) * 0.15, 0);
            if (finalAt >= 0) {
                double ft = t - finalAt;
                w.y += 2.5 * WoodGeo.smooth(ft / 0.6) * (1 - WoodGeo.smooth((ft - 2) / 0.8));
            }
            layArm(a, w);
            a.handQ.slerp(idleQ, 0.1f);
            return;
        }
        a.t += dt;
        Vector3d W2 = new Vector3d(a.to).sub(0, y, BZ);
        double warnT = a.big ? 1.3 : 0.75;
        if ("warn".equals(a.state)) {
            double k = WoodGeo.clamp(a.t / warnT, 0, 1), R = a.big ? 6 : 2.3;
            HsFx.shadow(m, (float) a.to.x, (float) a.to.z, (float) R, (float) k, (float) (seed + a.th * 10));
            Vector3d lift = new Vector3d(a.idle).lerp(new Vector3d(W2.x, W2.y + (a.big ? 13 : 8), W2.z), WoodGeo.smooth(k));
            layArm(a, lift);
            Vector3d dir = new Vector3d(W2).sub(a.anchor).setComponent(1, 0);
            if (dir.lengthSquared() > 1.0E-6) {
                dir.normalize();
                a.handQ.slerp(new Quaternionf().rotationTo(0, 0, -1, (float) dir.x, 0, (float) dir.z), 0.15f);
            }
            if (k >= 1) {
                a.state = "drop";
                a.t = 0;
                a.from.set(lift);
            }
        } else if ("drop".equals(a.state)) {
            double D = a.big ? 0.22 : 0.14, k = WoodGeo.clamp(a.t / D, 0, 1);
            double hs = (a.big ? a.scale : 1) * a.rs;
            Vector3d w = new Vector3d(a.from).lerp(new Vector3d(W2.x, W2.y + 0.45 * hs / 1.8, W2.z), k * k);
            layArm(a, w);
            if (k >= 1) {
                a.state = "hold";
                a.t = 0;
                Vec3 at = toWorld(a.to.x, a.to.y + 0.2, a.to.z);
                HsFx.Debris.splinters(at, new Vec3(0, (a.big ? 9 : 5) * HsWood.PU, 0), a.big ? 22 : 7);
            }
        } else if ("hold".equals(a.state)) {
            if (a.t > (a.big ? 1.6 : 0.6)) {
                a.state = "back";
                a.t = 0;
                a.from.set(a.handPos);
            }
        } else if ("back".equals(a.state)) {
            double k = WoodGeo.smooth(a.t / 0.7);
            layArm(a, new Vector3d(a.from).lerp(a.idle, k).add(0, Math.sin(k * Math.PI) * 3, 0));
            a.handQ.slerp(idleQ, 0.08f);
            if (k >= 1) {
                a.state = "idle";
                if (a.big) {
                    a.big = false;
                    a.scale = 1;
                }
            }
        }
    }
}
