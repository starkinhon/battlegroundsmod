// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx.hs;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.fx.hs.WoodGeo.Geo;
import com.starkinhon.battlegroundsmod.power.powers.hashirama.HsNew;

import net.minecraft.world.phys.Vec3;

// Mokuton: Mokujin no Jutsu, the approved page's golem (forja/hashirama-v2/hsnew.js makeGolem) drawn with the Hashirama's
// wood: a barrel torso with chest plates, a horned helmet with a mask, shoulder pads, arms with closed fists. It stands
// waist-deep in the ground at the server's base, facing the server's way, HsNew.GS times the page's size. The arms point
// at the points the server hits (the punch's landing spot, the grab's sweep, the lift and the slam), so what you see is
// where it lands.
final class HsGolem extends HsFx.Fx {
    static final float S = (float) HsNew.GS;
    static final String WOOD = "#a07a4e", DARK = "#3b2614";
    // bones: 0 body, 1 head, 2/3 right upper/fore+fist, 4/5 left upper/fore+fist
    record Piece(Geo g, HsWood.Mat m, int bone) {
    }

    private static List<Piece> pieces;

    // events (seconds since born, -1 = not yet)
    double riseAt = 0, leaveAt = -1, punchAt = -1, grabAt = -1, caughtAt = -1, blockAt = -1, slamAt = -1;
    Vec3 punchP, grabA, grabB, slamP;
    final Quaternionf qR = new Quaternionf(), qL = new Quaternionf();

    private static Vector3d v(double x, double y, double z) {
        return new Vector3d(x, y, z);
    }

    // a closed tube: the ends taper to a point just past the first and last points
    private static void tube(List<Piece> out, int bone, double seed, Vector3d[] pts, double[] r, String wood, String dark) {
        int n = pts.length;
        Vector3d[] p = new Vector3d[n + 2];
        double[] rr = new double[n + 2];
        Vector3d a = new Vector3d(pts[0]).sub(pts[1]).normalize().mul(r[0] * 0.35), b = new Vector3d(pts[n - 1]).sub(pts[n - 2]).normalize().mul(r[n - 1] * 0.35);
        p[0] = new Vector3d(pts[0]).add(a);
        rr[0] = r[0] * 0.4;
        for (int i = 0; i < n; i++) {
            p[i + 1] = pts[i];
            rr[i + 1] = r[i];
        }
        p[n + 1] = new Vector3d(pts[n - 1]).add(b);
        rr[n + 1] = 0.02;
        out.add(new Piece(WoodGeo.tubeGeo(p, rr, 14, 6.283 * Math.max(r[0], 0.3)), HsWood.Mat.tube(seed, wood, dark).tip(0.02).fine(24), bone));
    }

    private static List<Piece> pieces() {
        if (pieces != null) {
            return pieces;
        }
        List<Piece> L = new ArrayList<>();
        // torso, chest plates
        tube(L, 0, 1, new Vector3d[] {v(0, 0, 0), v(0, 3, 0), v(0, 7, 0.2), v(0, 10.5, 0.3), v(0, 12.5, 0)}, new double[] {4.2, 4.6, 5.2, 5.0, 3.0}, WOOD, DARK);
        for (int k = 0; k < 4; k++) {
            tube(L, 0, 10 + k, new Vector3d[] {v(-3.6, 8.6 - k * 1.6, -3.4 + k * 0.25), v(3.6, 8.6 - k * 1.6, -3.4 + k * 0.25)}, new double[] {0.75, 0.75}, "#93693f", DARK);
        }
        // head: helmet, brow, horns, eye slits (head bone at y 13)
        tube(L, 1, 3, new Vector3d[] {v(0, -0.5, 0), v(0, 1.2, -0.2), v(0, 2.6, 0), v(0, 3.4, 0.3)}, new double[] {1.9, 2.2, 2.0, 0.6}, WOOD, DARK);
        tube(L, 1, 4, new Vector3d[] {v(-2.2, 1.6, 0.4), v(0, 1.9, -0.6), v(2.2, 1.6, 0.4)}, new double[] {0.5, 0.7, 0.5}, "#8a6440", DARK);
        for (int s = -1; s <= 1; s += 2) {
            tube(L, 1, 5 + s, new Vector3d[] {v(s * 1.4, 2.4, -0.4), v(s * 2.6, 3.8, -0.7), v(s * 3.2, 5.6, -0.2), v(s * 3.1, 6.6, 0.4)}, new double[] {0.55, 0.45, 0.3, 0.05}, WOOD, DARK);
            tube(L, 1, 40 + s, new Vector3d[] {v(s * 0.3, 1.15, -2.05), v(s * 1.15, 1.15, -1.9)}, new double[] {0.16, 0.14}, "#1d140c", "#0c0805");
        }
        // arms (each bone's origin at its joint): shoulder pad + upper arm on the shoulder, forearm + fist on the elbow
        for (int side = 0; side < 2; side++) {
            int s = side == 0 ? 1 : -1, up = side == 0 ? 2 : 4;
            tube(L, up, 20 + s, new Vector3d[] {v(0, 0.8, 0), v(s * 1.4, 1.2, 0), v(s * 2.4, 0, 0)}, new double[] {2.0, 2.3, 1.6}, "#93693f", DARK);
            tube(L, up, 22 + s, new Vector3d[] {v(0, 0, 0), v(s * 0.4, -3.5, 0), v(s * 0.6, -6.5, 0)}, new double[] {1.7, 1.5, 1.4}, WOOD, DARK);
            tube(L, up + 1, 24 + s, new Vector3d[] {v(0, 0, 0), v(0, -3.2, -0.2), v(0, -6, -0.3)}, new double[] {1.4, 1.35, 1.25}, WOOD, DARK);
            tube(L, up + 1, 26 + s, new Vector3d[] {v(0, -6.2, -0.3), v(0, -7.4, -0.5), v(0, -8.4, -0.7)}, new double[] {1.9, 2.2, 1.6}, WOOD, DARK);
            for (int f = 0; f < 4; f++) {
                tube(L, up + 1, 30 + f, new Vector3d[] {v(-1.3 + f * 0.85, -8.2, -1.5), v(-1.3 + f * 0.85, -7.1, -2.3)}, new double[] {0.42, 0.38}, "#93693f", DARK);
            }
        }
        pieces = L;
        return L;
    }

    @Override
    void event(int kind, Vec3 p, Vec3 d, float a, float b, float c, double now) {
        double t = now - born;
        switch ((int) a) {
            case 1 -> {
                punchAt = t;
                punchP = p;
            }
            case 2 -> {
                grabAt = t;
                grabA = p;
                grabB = d;
                caughtAt = blockAt = slamAt = -1;
            }
            case 3 -> caughtAt = t;
            case 4 -> blockAt = t;
            case 5 -> {
                slamAt = t;
                slamP = p;
            }
            case 6 -> {
                if (leaveAt < 0) {
                    leaveAt = t;
                }
            }
            default -> {
            }
        }
    }

    // A world point in the golem's own units (origin at its base, -z forward), with the body's height offset.
    private Vector3f local(Vec3 w, double yOff) {
        double dx = w.x - pos.x, dy = w.y - pos.y, dz = w.z - pos.z, c = Math.cos(-yaw), s = Math.sin(-yaw);
        return new Vector3f((float) ((dx * c + dz * s) / S), (float) (dy / S - yOff), (float) ((-dx * s + dz * c) / S));
    }

    // The shoulder turned so the straight arm points at the world point w.
    private Quaternionf aim(int side, Vec3 w, double yOff) {
        Vector3f sh = new Vector3f(side * 5.4f, 10.6f, 0), to = local(w, yOff).sub(sh);
        if (to.lengthSquared() < 1.0E-4f) {
            return new Quaternionf();
        }
        return new Quaternionf().rotationTo(new Vector3f(0, -1, 0), to.normalize());
    }

    private static Quaternionf hang(int side, double breathe) {
        return new Quaternionf().rotationXYZ((float) breathe, 0, side * 0.15f);
    }

    @Override
    boolean draw(double t, Vec3 cam) {
        if (leaveAt >= 0 && t > leaveAt + 1.6 || t > 30) {
            return false;
        }
        double rise = WoodGeo.easeOut3(t / 2.0), sink = leaveAt >= 0 ? WoodGeo.smooth((t - leaveAt) / 1.5) : 0;
        double yOff = -3 - 27 * (1 - rise) - 30 * sink;
        double breathe = Math.sin(t * 1.1) * 0.04;
        Quaternionf r = hang(1, breathe - 0.6 * (1 - rise)), l = hang(-1, -breathe - 0.6 * (1 - rise));
        // the punch: the right arm up and back (0.8 s, the shadow on the ground), down onto the spot, back after 1.2 s
        if (punchAt >= 0 && punchP != null) {
            double k = t - punchAt;
            Quaternionf wind = new Quaternionf().rotationTo(new Vector3f(0, -1, 0), new Vector3f(0.2f, 0.9f, 0.45f).normalize());
            Quaternionf hit = aim(1, punchP, yOff);
            if (k < 0.8) {
                r = r.slerp(wind, (float) WoodGeo.smooth(k / 0.8), new Quaternionf());
            } else if (k < 1.0) {
                double q = (k - 0.8) / 0.2;
                r = new Quaternionf(wind).slerp(hit, (float) (q * q));
            } else if (k < 2.2) {
                r = hit;
            } else if (k < 3.0) {
                r = new Quaternionf(hit).slerp(r, (float) WoodGeo.smooth((k - 2.2) / 0.8));
            }
        }
        // the grab: the left hand sweeps the ground (1 s); caught, it lifts and slams; blocked, it bounces back
        if (grabAt >= 0 && grabA != null) {
            double k = t - grabAt;
            Vec3 lift = pos.add(dir.x * 9 * S, 16 * S, dir.z * 9 * S);
            if (blockAt >= 0) {
                double q = WoodGeo.smooth((t - blockAt) / 0.4);
                Vec3 at = grabA.lerp(grabB, Math.min(1, (blockAt - grabAt) / 1.0));
                l = aim(-1, at, yOff).slerp(l, (float) q);
            } else if (slamAt >= 0) {
                double q = t - slamAt;
                Quaternionf hit = aim(-1, slamP, yOff);
                l = q < 0.8 ? hit : new Quaternionf(hit).slerp(l, (float) WoodGeo.smooth((q - 0.8) / 0.8));
            } else if (caughtAt >= 0) {
                double q = WoodGeo.smooth((t - caughtAt) / 1.0);
                Vec3 from = grabA.lerp(grabB, Math.min(1, (caughtAt - grabAt) / 1.0));
                l = aim(-1, from, yOff).slerp(aim(-1, lift, yOff), (float) q);
            } else if (k < 1.0) {
                double q = WoodGeo.smooth(k);
                l = aim(-1, grabA.lerp(grabB, q), yOff);
            } else if (k < 1.8) {
                l = aim(-1, grabB, yOff).slerp(l, (float) WoodGeo.smooth((k - 1.0) / 0.8));
            }
        }
        qR.set(r);
        qL.set(l);
        double shake = blockAt >= 0 && t - blockAt < 1.2 ? Math.sin((t - blockAt) * 7) * 0.15 : 0;

        Matrix4f base = new Matrix4f().translation((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z)).rotateY((float) yaw).scale(S)
                .translate(0, (float) yOff, 0);
        Matrix4f head = new Matrix4f(base).translate(0, 13, 0).rotateZ((float) shake);
        Matrix4f[] bones = new Matrix4f[6];
        bones[0] = base;
        bones[1] = head;
        for (int side = 0; side < 2; side++) {
            int s = side == 0 ? 1 : -1;
            Matrix4f sh = new Matrix4f(base).translate(s * 5.4f, 10.6f, 0).rotate(side == 0 ? qR : qL);
            bones[2 + side * 2] = sh;
            bones[3 + side * 2] = new Matrix4f(sh).translate(s * 0.6f, -6.5f, 0).rotateX(-0.12f);
        }
        float b = bright;
        for (Piece p : pieces()) {
            HsWood.draw(p.g(), p.m(), bones[p.bone()], HsWood.grow(p.m(), 2), 0, b);
        }
        return true;
    }
}
