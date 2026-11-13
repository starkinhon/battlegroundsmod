package com.starkinhon.battlegroundsmod.client.fx.hs;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.fx.hs.HsAssets.Part;
import com.starkinhon.battlegroundsmod.client.fx.hs.WoodGeo.Geo;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Mokuton: Mokuryū no Jutsu, the approved page's dragao scene (forja/hashirama-boss/hashirama.js SCENES.dragao).
// The page's frame sits on the hole (yaw 0, so page axes = world axes, 0.9 blocks a unit). The warning, the burst and the
// spiral rise play here on their own clock exactly like the page; the chase comes from the server tick by tick (a = 2:
// head position, dir = heading); then a = 3 it missed and stuck its head in the ground, a = 4 it caught the target (pos =
// target's feet: it coils and drinks chakra), a = 5 it lifts and slams, a = 7 it goes away early.
// The body (core tube, braided bundles, arms) is one mesh in body space laid on the trail by the shader (HsWood.Rail).
final class HsDragon extends HsFx.Fx {
    static final int NB = 64, NM = 70;
    static final double SP = 0.4, BK = 1.45, BL = (NB - 1) * SP;
    static final float PU = HsWood.PU;

    private static Geo core;
    private static HsWood.Mat coreMat;

    final Vector3d[] trail = new Vector3d[NB];
    final HsWood.Rail rail = new HsWood.Rail();
    final Quaternionf q = new Quaternionf();
    final float[][] mseed = new float[NM][4];
    Vector3d head = new Vector3d(0, -3.2, 0), hp = new Vector3d(0, -3.2, 0), vel = new Vector3d(0, 1, 0), sA = new Vector3d(), dirT = new Vector3d(0, 0, -1), c;
    Vector3d s0, s1;
    double s1At = -1;
    int phase;
    double phaseAt, lastT, pebT, clumpAcc, lastUp, jaw, disAt = -1, disLen = 1;
    boolean roared;
    final float seed = HsFx.RNG.nextFloat() * 30;

    HsDragon() {
        for (int i = 0; i < NB; i++) {
            trail[i] = new Vector3d(0, -3.2 - i * SP, 0);
        }
        for (float[] m : mseed) {
            for (int k = 0; k < 4; k++) {
                m[k] = HsFx.RNG.nextFloat();
            }
        }
    }

    static double radiusAt(double u) {
        return BK * (u < 0.06 ? 0.78 + 0.18 * u / 0.06 : 0.96 - 0.08 * Math.sin(u * 9.0) * 0.5) * (u > 0.6 ? Math.max(0.06, 1 - Math.pow((u - 0.6) / 0.4, 1.6) * 0.94) : 1);
    }

    private static void ensureCore() {
        if (core != null) {
            return;
        }
        Vector3d[] pts = new Vector3d[NB];
        double[] r = new double[NB];
        for (int i = 0; i < NB; i++) {
            pts[i] = new Vector3d(0, 0, i * SP);
            r[i] = i == NB - 1 ? 0 : radiusAt(i / (double) (NB - 1)) * 0.82;
        }
        core = WoodGeo.tubeGeo(pts, r, 18, 8.5);
        coreMat = HsWood.Mat.tube(17, "#4d6352", "#28362c").gr(0.3).fine(30);
    }

    Vector3d page(Vec3 w) {
        return new Vector3d((w.x - pos.x) / PU, (w.y - pos.y) / PU, (w.z - pos.z) / PU);
    }

    @Override
    void event(int kind, Vec3 p, Vec3 d, float a, float b, float cc, double now) {
        double t = now - born;
        int k = (int) a;
        if (k == 0) {
            Vector3d f = new Vector3d(d.x, 0, d.z);
            if (f.lengthSquared() > 1.0E-6) {
                dirT.set(f.normalize());
            }
            return;
        }
        if (k == 2) {
            Vector3d at = page(p);
            if (s1 == null) {
                s0 = new Vector3d(hp);
            } else {
                s0 = s1;
            }
            s1 = at;
            s1At = t;
            if (d.lengthSqr() > 1.0E-6) {
                vel.set(d.x, d.y, d.z).normalize().mul(17);
            }
            if (phase < 2) {
                set(2, t);
            }
        } else if (k == 3 && phase < 3) {
            hp.set(page(p));
            set(3, t);
            disAt = t + 2.0;
        } else if (k == 4 && phase < 4) {
            c = page(p);
            set(4, t);
        } else if (k == 5 && phase == 4) {
            set(5, t);
            lastUp = 0;
        } else if (k == 7 && disAt < 0) {
            disAt = t;
            disLen = 0.8;
        }
    }

    void set(int ph, double t) {
        phase = ph;
        phaseAt = t;
    }

    void pushTrail(Vector3d p) {
        if (p.distance(trail[1]) < SP) {
            trail[0].set(p);
            return;
        }
        Vector3d last = trail[NB - 1];
        System.arraycopy(trail, 0, trail, 1, NB - 1);
        trail[0] = last.set(p);
    }

    void orientHead(Vector3d dir, float k) {
        if (dir.lengthSquared() < 1.0E-8) {
            return;
        }
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        Quaternionf to = new Quaternionf().rotationTo(0, 0, -1, d.x, d.y, d.z);
        q.slerp(to, k);
    }

    static float[] dirt(Vec3 at) {
        net.minecraft.client.multiplayer.ClientLevel lv = Minecraft.getInstance().level;
        float r = 0.45f, g = 0.34f, b = 0.22f;
        if (lv != null) {
            BlockPos bp = BlockPos.containing(at.x, HsFx.ground(at) - 0.5, at.z);
            int col = lv.getBlockState(bp).getMapColor(lv, bp).col;
            if (col != 0) {
                r = (col >> 16 & 255) / 255f;
                g = (col >> 8 & 255) / 255f;
                b = (col & 255) / 255f;
            }
        }
        float k = 0.75f + 0.3f * HsFx.RNG.nextFloat(), m = 0.35f + 0.5f * HsFx.RNG.nextFloat();
        return new float[] {Mth.lerp(m, r, 0.36f) * k, Mth.lerp(m, g, 0.26f) * k, Mth.lerp(m, b, 0.16f) * k};
    }

    @Override
    boolean draw(double t, Vec3 cam) {
        double dt = Math.max(0, Math.min(0.1, t - lastT));
        lastT = t;
        if (t > 25 || disAt >= 0 && t > disAt + disLen) {
            rail.close();
            return false;
        }
        double tt = t - phaseAt;
        Matrix4f m = frame(cam);
        net.minecraft.util.RandomSource R = HsFx.RNG;
        // ---- warning: the ground cracks in a circle and shakes, dirt hopping in place
        if (phase == 0) {
            double k = WoodGeo.clamp((t - 0.3) / 0.75, 0, 1);
            float half = (float) (0.6 + 2.8 * WoodGeo.easeOut3(k));
            if (t > 0.3) {
                HsFx.crack(m, 0, 0, 4.5f, 0, 0, 0, -1, 1, 0.3f, 2, half, 1, seed);
                pebT -= dt;
                if (pebT <= 0) {
                    pebT = 0.06;
                    for (int i = 0; i < 3 + 4 * k; i++) {
                        double a = R.nextDouble() * 6.283, dd = R.nextDouble() * half;
                        Vec3 at = toWorld(Math.cos(a) * dd, 0.1, Math.sin(a) * dd);
                        HsFx.Debris.add(at, new Vec3((R.nextDouble() - 0.5) * 1.2, 1.5 + 3.5 * k * R.nextDouble(), (R.nextDouble() - 0.5) * 1.2).scale(PU),
                                (float) (0.1 + 0.18 * R.nextDouble()) * PU, dirt(at));
                    }
                }
            }
            if (t > 1.05) {
                set(1, t);
                tt = 0;
                hp.set(0, -3.2, 0);
            } else {
                return true;
            }
        }
        // ---- the burst: spiral up, then arc over the top turning to the target and roar
        if (phase == 1) {
            double D1 = 0.62, D2 = 1.05;
            HsFx.crack(m, 0, 0, 4.5f, 0, 0, 0, -1, 1, 0.3f, 2, 3.4f, (float) Math.max(0, 1 - tt / 0.5), seed);
            Vector3d want;
            if (tt < D1) {
                double k = WoodGeo.easeOut3(tt / D1), a = 0.8 + 5.2 * k, rr = 0.3 + 1.6 * k;
                want = new Vector3d(Math.cos(a) * rr, -3.2 + 15 * k, Math.sin(a) * rr);
                sA.set(want);
                jaw = -0.08;
            } else {
                double qq = WoodGeo.clamp((tt - D1) / D2, 0, 1), e = qq * qq * (3 - 2 * qq);
                want = new Vector3d(sA).fma(4 * e, dirT).add(0, 2.2 * Math.sin(Math.min(1, qq * 1.4) * Math.PI * 0.5) - 1.6 * qq * qq * qq, 0);
                double roar = WoodGeo.smooth((qq - 0.12) / 0.15) * (1 - WoodGeo.smooth((qq - 0.62) / 0.2));
                jaw = -(0.08 + 0.95 * roar);
                roared |= qq > 0.27;
            }
            if (dt > 0) {
                vel.set(want).sub(hp).div(Math.max(dt, 1.0E-3));
            }
            hp.set(want);
            pushTrail(hp);
            orientHead(vel, 0.35f);
            if (tt < 1.1) {
                clumpAcc += dt * 80;
                while (clumpAcc >= 1) {
                    clumpAcc--;
                    int i = (int) Math.floor(R.nextDouble() * NB * 0.7);
                    Vector3d p = trail[i];
                    if (p.y < 0.3 || p.y > 11) {
                        continue;
                    }
                    double a = R.nextDouble() * 6.283, rad = radiusAt(i / (double) (NB - 1));
                    Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 at = toWorld(p.x + o.x * rad * 0.95, p.y, p.z + o.z * rad * 0.95);
                    HsFx.Debris.add(at, o.scale(1 + 2.5 * R.nextDouble()).add(0, R.nextDouble() * 2.5, 0).scale(PU), (float) (0.14 + 0.32 * R.nextDouble() * R.nextDouble()) * PU, dirt(at));
                }
            }
            if (tt > D1 + D2) {
                set(2, t);
                vel.normalize().mul(17);
            }
        } else if (phase == 2) {
            // ---- the chase: the server's head, one tick behind and smooth
            if (s1 != null) {
                double k = WoodGeo.clamp((t - s1At) / 0.05, 0, 1);
                hp.set(s0).lerp(s1, k);
            } else {
                hp.fma(dt, vel);
            }
            pushTrail(hp);
            orientHead(vel, 0.35f);
            jaw = -(0.32 + 0.14 * Math.sin(HsFx.now() * 12));
        } else if (phase == 3) {
            // ---- missed: the head stuck in the ground
            hp.fma(dt * Math.max(0, 1 - tt * 4), vel);
            if (tt < 0.25) {
                pushTrail(hp);
            }
            orientHead(vel, 0.35f);
        } else if (phase == 4 || phase == 5 || phase == 6) {
            if (phase == 4) {
                // ---- caught: coils round the target and drinks its chakra
                double w = Math.min(1, tt / 0.5);
                for (int i = 0; i < NB; i++) {
                    double u = i / (double) (NB - 1);
                    Vector3d coil = new Vector3d(c.x + Math.cos(u * 10 + 1) * (2.0 + u * 0.6), c.y + 0.2 + u * 6.0, c.z + Math.sin(u * 10 + 1) * (2.0 + u * 0.6));
                    trail[i].lerp(coil, w * (1 - WoodGeo.smooth((u - 0.65) / 0.35)));
                }
                Vector3d hd = new Vector3d(c).add(3.4, 6.9, 3.4);
                head.lerp(hd, Math.min(1, dt * 6));
                orientHead(new Vector3d(c.x, c.y + 1.4, c.z).sub(head), 0.2f);
                trail[0].set(head);
            } else if (phase == 5) {
                // ---- lifts and slams
                double k = tt / 0.5, up = Math.sin(Math.min(1, k) * Math.PI) * 3.5, dUp = up - lastUp;
                lastUp = up;
                for (int i = 0; i < NB; i++) {
                    trail[i].y += dUp * (1 - WoodGeo.smooth((i / (double) (NB - 1) - 0.65) / 0.35));
                }
                head.y += dUp;
                if (k >= 1) {
                    set(6, t);
                    if (disAt < 0) {
                        disAt = t + 1.6;
                    }
                }
            }
        }
        if (phase < 4) {
            head.set(trail[0]);
        }
        // ---- draw: body laid on the trail, head and jaw, eyes
        ensureCore();
        rail.set(trail);
        float dis = disAt >= 0 ? (float) WoodGeo.smooth((t - disAt) / disLen) : 0;
        HsWood.drawSkin(core, coreMat, m, 2, dis, bright, rail, (float) BL);
        List<Part> parts = HsAssets.get("dragon");
        Part first = parts.get(0);
        float js = first.f("js", 1.25f);
        Matrix4f hm = new Matrix4f(m).translate((float) head.x, (float) head.y, (float) head.z).rotate(q).scale(js);
        Matrix4f jm = new Matrix4f(hm).translate(first.f("jx", 0) / js, first.f("jy", 0) / js, first.f("jz", 0) / js).rotateX((float) jaw);
        float pulse = (float) (1.05 + 0.08 * Math.sin(HsFx.now() * 3));
        for (Part p : parts) {
            String role = p.s("role");
            switch (role) {
                case "skin", "arms" -> HsWood.drawSkin(p.g(), p.m(), m, 2, dis, bright, rail, (float) BL);
                case "jaw" -> HsWood.draw(p.g(), p.m(), jm, 2, dis, bright);
                default -> {
                    if (p.m().mode == 3) {
                        p.m().tint = new float[] {pulse, 0, 0};
                    }
                    HsWood.draw(p.g(), p.m(), hm, 2, dis, bright);
                }
            }
        }
        if (dis < 1) {
            Vector3f[] ax = HsFx.camAxes();
            HsWood.Tris tr = new HsWood.Tris();
            for (int sx = -1; sx <= 1; sx += 2) {
                Vector3f e = hm.transformPosition(new Vector3f(sx * 0.74f, 0.44f, -0.7f));
                tr.sprite(e.x, e.y, e.z, 0.5f * js * PU, ax[0].x, ax[0].y, ax[0].z, ax[1].x, ax[1].y, ax[1].z, 1f, 0.85f, 0.25f, 0.5f * (1 - dis));
            }
            // the stolen chakra: each grain with its own curve and rhythm, from the target to the mouth
            if (phase == 4 && tt > 0.5) {
                double now = HsFx.now();
                for (float[] s : mseed) {
                    double life = (now * (0.6 + 0.8 * s[0]) + s[3]) % 1, e = life * life;
                    Vector3d st = new Vector3d(c).add(Math.cos(s[1] * 6.28) * 0.4, 0.4 + s[2] * 1.4, Math.sin(s[1] * 6.28) * 0.4);
                    Vector3d ctrl = new Vector3d(st).lerp(head, 0.5).add(Math.cos(s[1] * 9) * 1.3, 0.6, Math.sin(s[1] * 9) * 1.3);
                    Vector3d a = new Vector3d(st).lerp(ctrl, e), b = new Vector3d(ctrl).lerp(head, e);
                    Vector3d p = a.lerp(b, e);
                    Vector3f w = m.transformPosition(new Vector3f((float) p.x, (float) p.y, (float) p.z));
                    float size = (0.06f + 0.22f * s[2] * s[2]) * PU * 0.5f;
                    tr.sprite(w.x, w.y, w.z, size, ax[0].x, ax[0].y, ax[0].z, ax[1].x, ax[1].y, ax[1].z, 0.3f, 0.65f, 1f, 0.8f);
                }
            }
            HsWood.imm(6, new Matrix4f(), tr, false, sh -> {
                sh.safeGetUniform("Tint").set(0.95f, 0.97f, 1f);
                sh.safeGetUniform("Alpha").set(0.6f);
            });
        }
        return true;
    }
}
