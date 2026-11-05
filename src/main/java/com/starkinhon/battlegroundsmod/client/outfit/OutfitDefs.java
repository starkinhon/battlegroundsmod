package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.resources.ResourceLocation;

// The 3D outfits, piece by piece, as approved in the Guarda-roupa viewer (same numbers). Only clothes: the player's own
// skin stays underneath. Pieces ride the vanilla bones; the tabard, the kimono skirt and the waist plates hang as cloth.
final class OutfitDefs {
    static final float PI = (float) Math.PI;

    static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/outfit/" + name + ".png");
    }

    static final ResourceLocation WHITE = tex("white");
    static final ResourceLocation COLLAR = tex("collar");
    static final ResourceLocation TABARD = tex("tabard");
    static final ResourceLocation LACQUER = tex("lacquer");
    static final ResourceLocation DOU = tex("dou");
    static final ResourceLocation NAVY = tex("navy");
    static final ResourceLocation SWORD_WRAP = tex("sword_wrap");
    static final ResourceLocation SWORD_WRAP_SIDE = tex("sword_wrap_side");

    record Piece(Rig.Bone bone, ResourceLocation texture, float[] tris) {
    }

    // A cloth drawn as a sheet; radial = the outside is away from the body's axis (skirt), else +z (tabard).
    record Sheet(Cloth.Spec spec, ResourceLocation texture, int color, boolean radial) {
    }

    // A strip of armor plates hanging on a 2-column rigid cloth.
    record Plates(Cloth.Spec spec, float width, float[] lame, float[] laces) {
    }

    record Def(List<Piece> pieces, List<Sheet> sheets, List<Plates> plates, List<Cloth.ColliderSpec> colliders) {
    }

    private static final class Builder {
        final Map<String, Geo> geos = new LinkedHashMap<>();
        final Map<String, Rig.Bone> bones = new LinkedHashMap<>();
        final Map<String, ResourceLocation> texs = new LinkedHashMap<>();
        final List<Sheet> sheets = new ArrayList<>();
        final List<Plates> plates = new ArrayList<>();
        final List<Cloth.ColliderSpec> colliders = new ArrayList<>();

        Geo geo(Rig.Bone bone, ResourceLocation texture) {
            String key = bone + "|" + texture;
            bones.put(key, bone);
            texs.put(key, texture);
            return geos.computeIfAbsent(key, k -> new Geo());
        }

        Def build() {
            List<Piece> pieces = new ArrayList<>();
            for (String key : geos.keySet()) {
                pieces.add(new Piece(bones.get(key), texs.get(key), geos.get(key).bake()));
            }
            return new Def(pieces, sheets, plates, colliders);
        }
    }

    private static Geo.RadiusFn sinBulge(float amp) {
        return (s, ph) -> 1 + amp * (float) Math.sin(s * PI);
    }

    // ===================================================== BILLS =====================================================

    static float pantsR(float s, float ph) {
        float bump = (float) Math.exp(-Math.pow((s - 0.68f) / 0.2f, 2));
        float r = 3.05f + 0.35f * s + 1.15f * bump;
        r *= 1 - 0.28f * Geo.smooth(0.86f, 1, s);
        return r + 0.16f * bump * (float) Math.sin(ph * 5 + s * 10) + 0.1f * (float) Math.sin(ph * 8 - s * 16) * (0.4f + bump);
    }

    static Def bills() {
        Builder b = new Builder();
        // Usekh collar: a flat ring laid over the chest, back and shoulders.
        {
            float yt = 24.32f, zs = 2.34f, rr = 0.7f, zc = zs - rr;
            Geo g = b.geo(Rig.Bone.BODY, COLLAR).color(0xFFFFFF);
            Geo.GridFn pt = (u, t) -> {
                float ph = u * 2 * PI, c = (float) Math.cos(ph), s = (float) Math.sin(ph);
                float[] in = Geo.supe(c, s, 4.4f, 4.4f, 8), out = Geo.supe(c, s, 7.1f, s > 0 ? 8.5f : 7.7f, 2.4f);
                float x = in[0] + (out[0] - in[0]) * t, z = in[1] + (out[1] - in[1]) * t;
                float az = Math.abs(z), sg = z < 0 ? -1 : 1, y;
                if (az <= zc) {
                    y = yt;
                } else {
                    float d = az - zc, arc = PI / 2 * rr;
                    if (d < arc) {
                        float a = d / rr;
                        y = yt - rr + rr * (float) Math.cos(a);
                        z = sg * (zc + rr * (float) Math.sin(a));
                    } else {
                        float e = d - arc;
                        y = yt - rr - e;
                        z = sg * (zs + 0.1f * e);
                    }
                }
                return new float[] {x, y, z, u, 1 - t};
            };
            g.grid(160, 12, pt, null, (x, y, z) -> new Vector3f(x, y - 18, z));
            Geo trim = b.geo(Rig.Bone.BODY, WHITE).color(0xE9EEF6);
            for (float t : new float[] {1, 0}) {
                float[][] pts = new float[61][];
                for (int j = 0; j <= 60; j++) {
                    float[] q = pt.at(j / 60f, t);
                    pts[j] = new float[] {q[0], q[1] + 0.05f, q[2]};
                }
                Geo.cord(trim, pts, t == 1 ? 0.26f : 0.2f, 180);
            }
        }
        int gold = 0xF2C64A;
        new Geo.Tube(4.45f, 4.45f, 10, 0.95f, 0.1f).rings(2, 48).rf((s, ph) -> 1 + 0.025f * (float) Math.sin(s * PI))
                .build(b.geo(Rig.Bone.HEAD, WHITE).color(gold));
        for (Rig.Bone arm : new Rig.Bone[] {Rig.Bone.ARM_R, Rig.Bone.ARM_L}) {
            Geo g = b.geo(arm, WHITE).color(gold);
            new Geo.Tube(2.42f, 2.42f, 5, -3.6f, -4.4f).rings(2, 40).rf((s, ph) -> 1 + 0.03f * (float) Math.sin(s * PI)).build(g);
            new Geo.Tube(2.45f, 2.45f, 5, -7.8f, -10.5f).rings(6, 40).rf((s, ph) -> 1 + 0.07f * s + 0.03f * (float) Math.sin(s * PI)).build(g);
        }
        // Waist sash: three wraps.
        Geo.Tube[] sash = {
                new Geo.Tube(4.62f, 2.66f, 5, 14.4f, 13.3f),
                new Geo.Tube(4.72f, 2.76f, 5, 13.45f, 12.45f).off((s, ph, x, z) -> new float[] {0, 0.12f * x / 4.7f, 0}),
                new Geo.Tube(4.6f, 2.64f, 5, 12.6f, 11.6f).off((s, ph, x, z) -> new float[] {0, -0.1f * x / 4.6f, 0})};
        for (int i = 0; i < sash.length; i++) {
            float amp = i == 1 ? 0.045f : 0.04f;
            sash[i].rings(5, 56).rf((s, ph) -> 1 + amp * (float) Math.sin(s * PI) + 0.012f * (float) Math.sin(ph * 11 + s * 3))
                    .shade((s, ph) -> 0.85f + 0.15f * (float) Math.sin(ph * 11 + s * 3))
                    .build(b.geo(Rig.Bone.BODY, WHITE).color(i == 1 ? 0x222C94 : 0x2B38B4));
        }
        // Baggy pants, cuffs, anklets and boots.
        for (Rig.Bone leg : new Rig.Bone[] {Rig.Bone.LEG_R, Rig.Bone.LEG_L}) {
            float sd = leg == Rig.Bone.LEG_R ? -1 : 1;
            float shift = sd < 0 ? PI : 0;
            new Geo.Tube(1, 1, 4, 0.8f, -9).rings(26, 52).rf((s, ph) -> pantsR(s, ph + shift))
                    .off((s, ph, x, z) -> new float[] {x * sd < 0 ? -x * 0.2f : 0, 0, 0})
                    .shade((s, ph) -> {
                        float bump = (float) Math.exp(-Math.pow((s - 0.68f) / 0.2f, 2));
                        float f = (float) Math.sin((ph + shift) * 5 + s * 10) * bump + 0.6f * (float) Math.sin((ph + shift) * 8 - s * 16);
                        return 0.8f + 0.13f * f;
                    }).build(b.geo(leg, WHITE).color(0x5AB6FF));
            new Geo.Tube(2.62f, 2.62f, 4, -8.7f, -9.7f).rings(3, 40).rf(sinBulge(0.05f)).build(b.geo(leg, WHITE).color(0x2A3CC8));
            new Geo.Tube(2.5f, 2.5f, 5, -9.75f, -10.25f).rings(2, 40).rf(sinBulge(0.03f)).build(b.geo(leg, WHITE).color(gold));
            new Geo.Tube(2.42f, 2.42f, 4, -10.2f, -12).rings(6, 40).caps(false, true).rf((s, ph) -> 1 + 0.04f * s)
                    .off((s, ph, x, z) -> new float[] {0, 0, z > 0 ? 1.1f * s * s : 0}).build(b.geo(leg, WHITE).color(0x7B4A2B));
            b.colliders.add(Cloth.ColliderSpec.radial(leg, 9.8f, (s, ph) -> pantsR(s, 0) * 1.28f));
        }
        b.colliders.add(Cloth.ColliderSpec.box(Rig.Bone.BODY, 0, 18, 0, 4.6f, 6.8f, 2.6f));
        // Black tabard with the diamonds.
        float[][] tab = {{-1.85f, 12.5f, 3.0f}, {0, 12.5f, 3.0f}, {1.85f, 12.5f, 3.0f}};
        b.sheets.add(new Sheet(new Cloth.Spec(tab, 10, 0.95f, false, 0.3f, 8, 0, 0.9f, null, new float[] {1, 0, 0}, 1.85f),
                TABARD, 0xFFFFFF, false));
        return b.build();
    }

    // ===================================================== MADARA ====================================================

    static Def madara() {
        Builder b = new Builder();
        int navyDark = 0x9AA0C8, lacq = 0xFFFFFF, cord = 0xCAA24C;
        // Shirt and high collar.
        new Geo.Tube(4.42f, 2.42f, 7, 24.25f, 12.3f).rings(16, 56).caps(true, false)
                .rf((s, ph) -> 1 + 0.012f * (0.6f * (float) Math.sin(ph * 9 + s * 7) + 0.4f * (float) Math.sin(s * 13 + ph * 3)))
                .shade((s, ph) -> 0.86f + 0.14f * (float) Math.sin(ph * 9 + s * 7)).build(b.geo(Rig.Bone.BODY, NAVY).color(0xFFFFFF));
        new Geo.Tube(4.55f, 4.55f, 10, 1.5f, -0.3f).rings(3, 48).arc(PI / 2 + 0.6f, PI / 2 + 2 * PI - 0.6f)
                .rf((s, ph) -> 1 + 0.03f * s).build(b.geo(Rig.Bone.HEAD, NAVY).color(navyDark));
        // Wide sleeves with a hem.
        for (Rig.Bone arm : new Rig.Bone[] {Rig.Bone.ARM_R, Rig.Bone.ARM_L}) {
            new Geo.Tube(2.95f, 2.95f, 6, 2.25f, -8.4f).rings(14, 44).caps(true, false)
                    .rf((s, ph) -> 1 + 0.02f * (0.6f * (float) Math.sin(ph * 7 + s * 7) + 0.4f * (float) Math.sin(s * 11 + ph * 3)))
                    .shade((s, ph) -> 0.84f + 0.16f * (float) Math.sin(ph * 7 + s * 7)).build(b.geo(arm, NAVY).color(0xFFFFFF));
            new Geo.Tube(3.2f, 3.2f, 6, -8.3f, -9.2f).rings(2, 44).caps(false, true).build(b.geo(arm, WHITE).color(0x555B82));
        }
        // Chest plate (dou): an apron shape, a bit thick, bent around the chest.
        {
            Geo g = b.geo(Rig.Bone.BODY, DOU).color(lacq);
            int first = g.size();
            float depth = 0.6f;
            for (int side = 0; side < 2; side++) {
                float z = side == 0 ? depth : 0;
                int s = side == 0 ? 1 : -1;
                g.grid(14, 18, (u, v) -> {
                    float y = 9 * (1 - v), hw = douHalf(y), x = -hw + 2 * hw * u;
                    return new float[] {x, y, z, x / 8 + 0.5f, 1 - y / 9};
                }, null, (x, y, zz) -> new Vector3f(0, 0, s));
            }
            // the rim, walking around the outline
            float[][] rim = douOutline();
            int m = rim.length - 1;
            g.grid(m, 1, (u, v) -> {
                float[] q = rim[Math.round(u * m)];
                return new float[] {q[0], q[1], depth * (1 - v), q[0] / 8 + 0.5f, 1 - q[1] / 9};
            }, null, (x, y, z) -> new Vector3f(x, y - 4.5f, 0));
            // edges go back around the chest, the middle bulges a little
            g.warp(first, (x, y, z) -> new float[] {x, y, z - 0.03f * x * x + 0.25f * (float) Math.sin(y / 9 * PI)});
            g.transform(first, new Matrix4f().translation(0, 14.9f, 2.35f));
            Geo cg = b.geo(Rig.Bone.BODY, WHITE).color(cord);
            for (int s = -1; s <= 1; s += 2) {
                Geo.cord(cg, new float[][] {{1.9f * s, 23.75f, 2.95f}, {2.3f * s, 24.55f, 1.6f}, {2.45f * s, 24.6f, -0.6f}, {2.3f * s, 24.2f, -2.55f}},
                        0.13f, 24);
                Geo.sphere(cg, 1.9f * s, 23.6f, 2.95f, 0.28f);
            }
        }
        // Red belt.
        new Geo.Tube(4.62f, 2.66f, 6, 13.9f, 12.3f).rings(5, 56)
                .rf((s, ph) -> 1 + 0.035f * (float) Math.sin(s * PI) + 0.01f * (float) Math.sin(ph * 13))
                .shade((s, ph) -> 0.85f + 0.15f * (float) Math.sin(ph * 13)).build(b.geo(Rig.Bone.BODY, WHITE).color(0x7A1B2C));
        // Shoulder guards (sode): a curved cap and four plates.
        for (Rig.Bone arm : new Rig.Bone[] {Rig.Bone.ARM_R, Rig.Bone.ARM_L}) {
            float ox = arm == Rig.Bone.ARM_R ? -1 : 1;
            Geo lg = b.geo(arm, LACQUER).color(lacq);
            Geo.bentPlate(lg, 4.4f, 6.4f, 0.4f, 2.7f, new Matrix4f().translation(ox * 0.9f, 2.6f, 0).rotateX(-PI / 2));
            Geo cg = b.geo(arm, WHITE).color(cord);
            for (int i = 0; i < 4; i++) {
                Matrix4f tilt = new Matrix4f().translation(ox * (3.3f + 0.13f * i), 1.5f - i * 1.42f, 0).rotateZ(ox * (0.1f + 0.05f * i));
                Geo.bentPlate(lg, 6.5f, 1.75f, 0.38f, 3.7f, new Matrix4f(tilt).rotateY(ox * PI / 2));
                for (float zz : new float[] {-1.7f, 1.7f}) {
                    Geo.box(cg, 0.14f, 1.5f, 0.12f, new Matrix4f(tilt).translate(ox * 0.3f, 0, zz));
                }
            }
        }
        // Shin wraps and shoes.
        for (Rig.Bone leg : new Rig.Bone[] {Rig.Bone.LEG_R, Rig.Bone.LEG_L}) {
            for (int i = 0; i < 5; i++) {
                float y0 = -6.6f - i * 0.62f, tiltSign = i % 2 == 1 ? 1 : -1;
                new Geo.Tube(2.36f + (i % 2) * 0.06f, 2.36f + (i % 2) * 0.06f, 6, y0, y0 - 0.66f).rings(2, 40).rf(sinBulge(0.035f))
                        .off((s, ph, x, z) -> new float[] {0, 0.13f * x / 2.4f * tiltSign, 0})
                        .build(b.geo(leg, WHITE).color(i % 2 == 1 ? 0xE1E3EA : 0xC4C7D1));
            }
            new Geo.Tube(2.6f, 2.6f, 5, -9.75f, -10.35f).rings(2, 40).rf(sinBulge(0.03f)).build(b.geo(leg, WHITE).color(0x555B82));
            new Geo.Tube(2.46f, 2.46f, 5, -10.3f, -12).rings(4, 40).caps(false, true)
                    .off((s, ph, x, z) -> new float[] {0, 0, z > 0 ? 0.5f * s : 0}).build(b.geo(leg, NAVY).color(navyDark));
            b.colliders.add(Cloth.ColliderSpec.box(leg, 0, -6, 0, 2, 6.2f, 2));
        }
        b.colliders.add(Cloth.ColliderSpec.box(Rig.Bone.BODY, 0, 18.3f, 0, 4, 6.3f, 2));
        // Kimono skirt: a ring of 32 hanging threads.
        {
            int k = 32;
            float[][] anchors = new float[k][], outs = new float[k][];
            for (int i = 0; i < k; i++) {
                float ph = i / (float) k * 2 * PI;
                float[] xz = Geo.supe((float) Math.cos(ph), (float) Math.sin(ph), 4.85f, 2.9f, 4);
                anchors[i] = new float[] {xz[0], 12.7f, xz[1]};
                outs[i] = new float[] {(float) Math.cos(ph), 0, (float) Math.sin(ph)};
            }
            b.sheets.add(new Sheet(new Cloth.Spec(anchors, 8, 1.12f, true, 0.42f, 8, 0.08f, 0.7f, outs, null, 0), NAVY, 0xFFFFFF, true));
        }
        // Waist plates (kusazuri): front and both sides.
        float[][][] tassets = {{{0, 12.9f, 3.3f}, {0, 0, 1}, {4.6f}}, {{5.15f, 12.9f, 0}, {1, 0, 0}, {3.8f}}, {{-5.15f, 12.9f, 0}, {-1, 0, 0}, {3.8f}}};
        for (float[][] t : tassets) {
            Vector3f n = new Vector3f(t[1][0], t[1][1], t[1][2]);
            Vector3f right = new Vector3f(0, 1, 0).cross(n).normalize();
            float w = t[2][0];
            float[][] anchors = new float[2][];
            for (int s = 0; s < 2; s++) {
                float f = s == 0 ? -w / 2 : w / 2;
                anchors[s] = new float[] {t[0][0] + right.x * f, t[0][1], t[0][2] + right.z * f};
            }
            Geo lame = new Geo().color(lacq);
            Geo.bentPlate(lame, w, 1.95f, 0.42f, n.z != 0 ? 7 : 5, new Matrix4f());
            Geo laces = new Geo().color(cord);
            for (float s : new float[] {-0.3f, 0.3f}) {
                Geo.box(laces, 0.16f, 1.75f, 0.1f, new Matrix4f().translation(s * w, 0, 0.27f));
            }
            b.plates.add(new Plates(new Cloth.Spec(anchors, 5, 1.72f, false, 1.05f, 10, 0, 1, null, new float[] {right.x, right.y, right.z}, w / 2),
                    w, lame.bake(), laces.bake()));
        }
        return b.build();
    }

    private static float douHalf(float y) {
        if (y <= 6.7f) {
            return 3.8f;
        }
        float t = (y - 6.7f) / 2.3f;
        return 2.1f + 1.7f * (1 - t) * (1 - t);
    }

    private static float[][] douOutline() {
        List<float[]> pts = new ArrayList<>();
        int n = 12;
        for (int i = 0; i <= n; i++) {
            float y = 9f * i / n;
            pts.add(new float[] {-douHalf(y), y});
        }
        for (int i = 1; i <= 4; i++) {
            pts.add(new float[] {-2.1f + 4.2f * i / 4, 9});
        }
        for (int i = n - 1; i >= 0; i--) {
            float y = 9f * i / n;
            pts.add(new float[] {douHalf(y), y});
        }
        for (int i = 3; i >= 0; i--) {
            pts.add(new float[] {-3.8f + 7.6f * i / 4, 0});
        }
        return pts.toArray(new float[0][]);
    }


    // ================================================= AIZEN: SWORD ==================================================
    // The sword from the Armario/Guarda-roupa viewer ("Espada do Aizen"), approved by Stark on 2026-11-05 as final. It is
    // a normal item (ModItems.AIZEN_SWORD) drawn by AizenSwordRenderer. Built in its own space, in player pixels:
    // +y = tip, x = blade width with the edge at +x, z = thickness, origin = top of the guard.

    // One ring of a low-poly prism: a convex polygon of (x, z) points at height y.
    private record Ring(float y, float[][] pts) {
    }

    private interface FaceGeo {
        Geo at(int face);
    }

    private static float[][] oct(float hx, float hz, float c) {
        return new float[][] {{hx - c, -hz}, {hx, -hz + c}, {hx, hz - c}, {hx - c, hz}, {-hx + c, hz}, {-hx, hz - c}, {-hx, -hz + c}, {-hx + c, -hz}};
    }

    private static float[][] scaled(float[][] pts, float k) {
        float[][] out = new float[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            out[i] = new float[] {pts[i][0] * k, pts[i][1] * k};
        }
        return out;
    }

    // Pulls a convex counter-clockwise polygon in by d.
    private static float[][] inset(float[][] pts, float d) {
        int n = pts.length;
        float[][] lines = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] a = pts[i], b = pts[(i + 1) % n];
            float ex = b[0] - a[0], ey = b[1] - a[1], l = (float) Math.hypot(ex, ey);
            lines[i] = new float[] {a[0] - ey / l * d, a[1] + ex / l * d, ex, ey};
        }
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] l = lines[i], m = lines[(i + n - 1) % n];
            float den = m[2] * l[3] - m[3] * l[2];
            float t = ((l[0] - m[0]) * l[3] - (l[1] - m[1]) * l[2]) / den;
            out[i] = new float[] {m[0] + m[2] * t, m[1] + m[3] * t};
        }
        return out;
    }

    // Flat sides from ring to ring, one grid per side so the edges stay sharp. Texture u goes across a side, v down it.
    private static void prism(Ring[] rings, FaceGeo geoOf) {
        int n = rings[0].pts().length, last = rings.length - 1;
        float y0 = rings[0].y(), y1 = rings[last].y();
        float cx = 0, cz = 0;
        for (float[] q : rings[0].pts()) {
            cx += q[0] / n;
            cz += q[1] / n;
        }
        for (int i = 0; i < n; i++) {
            Geo g = geoOf.at(i);
            if (g == null) {
                continue;
            }
            int fi = i, i2 = (i + 1) % n;
            float[] a0 = rings[0].pts()[i], b0 = rings[0].pts()[i2];
            float hx = (a0[0] + b0[0]) / 2 - cx, hz = (a0[1] + b0[1]) / 2 - cz;
            g.grid(1, last, (u, v) -> {
                Ring r = rings[Math.round(v * last)];
                float[] a = r.pts()[fi], b = r.pts()[i2];
                return new float[] {a[0] + (b[0] - a[0]) * u, r.y(), a[1] + (b[1] - a[1]) * u, u, 1 - (r.y() - y0) / (y1 - y0)};
            }, null, (x, y, z) -> new Vector3f(hx, 0, hz));
        }
    }

    private static void capRing(Geo g, Ring r, float up) {
        int first = g.size();
        float[][] p = r.pts();
        for (int i = 0; i <= p.length; i++) {
            float[] q = p[i % p.length];
            g.vertex(q[0], r.y(), q[1], 0.5f, 0.5f, 1);
        }
        g.cap(first, p.length + 1, (x, y, z) -> new Vector3f(0, up, 0));
    }

    // The flat band between an outer and an inner polygon at height y (top of the guard's raised frame).
    private static void band(Geo g, float[][] out, float[][] in, float y, float up) {
        int n = out.length;
        for (int i = 0; i < n; i++) {
            float[] a = out[i], b = out[(i + 1) % n], c = in[i], d = in[(i + 1) % n];
            g.grid(1, 1, (u, v) -> {
                float ox = a[0] + (b[0] - a[0]) * u, oz = a[1] + (b[1] - a[1]) * u;
                float ix = c[0] + (d[0] - c[0]) * u, iz = c[1] + (d[1] - c[1]) * u;
                return new float[] {ox + (ix - ox) * v, y, oz + (iz - oz) * v, u, v};
            }, null, (x, yy, z) -> new Vector3f(0, up, 0));
        }
    }

    static final float SWORD_TSUKA = 6.6f, SWORD_BLADE = 21, SWORD_W = 0.95f, SWORD_T = 0.24f;
    // Where the hand holds the grip (2.2 px under the guard) and the middle of the whole sword, on its axis.
    static final float SWORD_GRIP = -2.2f, SWORD_MIDDLE = (SWORD_BLADE - SWORD_TSUKA - 0.98f) / 2;

    // The sword in its own space, for the item renderer (AizenSwordRenderer).
    static List<Piece> sword() {
        Builder b = new Builder();
        Rig.Bone hand = Rig.Bone.BODY;
        Geo flat = b.geo(hand, SWORD_WRAP).color(0xFFFFFF), side = b.geo(hand, SWORD_WRAP_SIDE).color(0xFFFFFF), w = b.geo(hand, WHITE);
        int wrapEdge = 0x1C4637, khaki = 0x8B7E5B, khakiDark = 0x5E5440, panel = 0x5A605A, under = 0x3D3427;
        int steel = 0xB3BAC7, steelEdge = 0xD5DBE5, steelBack = 0x6B707E;

        // Grip (tsuka): 8 sides, the wrap's diamonds on the wide ones, slanted bands on the narrow ones, 6% thinner at the end.
        float tk = SWORD_TSUKA;
        float[][] grip = oct(0.62f, 0.5f, 0.13f);
        prism(new Ring[] {new Ring(-tk, scaled(grip, 0.94f)), new Ring(-tk / 2, scaled(grip, 0.975f)), new Ring(0, grip)},
                i -> i == 3 || i == 7 ? flat : i == 1 || i == 5 ? side : w.color(wrapEdge));
        // Pommel (kashira): khaki cap with a lip at the top and a bevel at the bottom.
        float[][] kb = oct(0.66f, 0.54f, 0.15f);
        float ky = -tk;
        Ring kBot = new Ring(ky - 0.98f, scaled(kb, 0.8f));
        prism(new Ring[] {kBot, new Ring(ky - 0.78f, kb), new Ring(ky - 0.12f, kb), new Ring(ky + 0.12f, scaled(kb, 0.97f))},
                i -> w.color(i == 3 || i == 7 ? khaki : khakiDark));
        capRing(w.color(khaki), kBot, -1);
        Ring lipBot = new Ring(ky - 0.16f, scaled(kb, 1.07f)), lipTop = new Ring(ky + 0.04f, scaled(kb, 1.07f));
        prism(new Ring[] {lipBot, lipTop}, i -> w.color(khakiDark));
        capRing(w, lipBot, -1);
        capRing(w, lipTop, 1);

        // Guard (tsuba): a long hexagon across the blade's width, raised khaki frame, grey middle, dark brown underside.
        float[][] hex = {{2.15f, 0}, {1.45f, 0.92f}, {-1.45f, 0.92f}, {-2.15f, 0}, {-1.45f, -0.92f}, {1.45f, -0.92f}};
        float tb = -0.26f;
        Ring pBot = new Ring(tb, hex), pTop = new Ring(0, hex);
        prism(new Ring[] {pBot, pTop}, i -> w.color(khaki));
        capRing(w, pBot, -1);
        capRing(w, pTop, 1);
        float[][] in15 = inset(hex, 0.15f), in07 = inset(hex, 0.07f);
        Ring mid = new Ring(0.023f, in15);
        prism(new Ring[] {new Ring(0.003f, in15), mid}, i -> w.color(panel));
        capRing(w, mid, 1);
        prism(new Ring[] {new Ring(0, hex), new Ring(0.07f, hex)}, i -> w.color(khaki));
        prism(new Ring[] {new Ring(0, in15), new Ring(0.07f, in15)}, i -> w);
        band(w, hex, in15, 0.07f, 1);
        Ring uTop = new Ring(tb, in07), uBot = new Ring(tb - 0.02f, in07);
        prism(new Ring[] {uBot, uTop}, i -> w.color(under));
        capRing(w, uBot, -1);
        // the line from point to point, on top and underneath
        w.color(khaki);
        Geo.box(w, 4.0f, 0.05f, 0.07f, new Matrix4f().translation(0, 0.055f, 0));
        Geo.box(w, 4.0f, 0.03f, 0.07f, new Matrix4f().translation(0, tb - 0.03f, 0));
        // Seppa (low plate) and habaki (the block that holds the blade).
        float[][] seppa = oct(0.82f, 0.4f, 0.2f);
        Ring sTop = new Ring(0.08f, seppa);
        prism(new Ring[] {new Ring(0, seppa), sTop}, i -> w.color(khaki));
        capRing(w, sTop, 1);
        Geo.box(w.color(khakiDark), 1.04f, 0.62f, 0.5f, new Matrix4f().translation(0, 0.39f, 0));
        Geo.box(w.color(khaki), 1.0f, 0.06f, 0.46f, new Matrix4f().translation(0, 0.73f, 0));

        // Blade: thick back, ridge line (shinogi), thin edge, the tip (kissaki) rounding off on the edge side.
        float len = SWORD_BLADE, kiss = 2.3f, by0 = 0.6f;
        Ring[] rings = new Ring[27];
        for (int k = 0; k <= 26; k++) {
            float s = k / 26f;
            float y = s < 0.85f ? by0 + (len - kiss - by0) * (s / 0.85f) : len - kiss + (s - 0.85f) / 0.15f * kiss;
            y = Math.min(len, y);
            float tap = 1 - 0.13f * (y - by0) / (len - by0), bw = SWORD_W * tap, bt = SWORD_T * tap;
            float u = Math.max(0, (y - (len - kiss)) / kiss), back = -bw / 2;
            float ex = u >= 1 ? back + 0.001f : back + bw * (float) Math.sqrt(Math.max(0, 1 - u * u));
            float sx = Math.min(back + 0.55f * bw, ex - 0.02f * (1 - u)), tt = bt * (1 - 0.7f * u);
            rings[k] = new Ring(y, new float[][] {{ex, 0}, {sx, tt * 0.42f}, {back, tt * 0.5f}, {back, -tt * 0.5f}, {sx, -tt * 0.42f}});
        }
        prism(rings, i -> w.color(i == 0 || i == 4 ? steelEdge : i == 2 ? steelBack : steel));
        capRing(w.color(steel), rings[0], -1);

        return b.build().pieces();
    }

    // ===================================================== LOOKUP ====================================================

    private static Map<String, Def> defs;

    static Def get(String power) {
        if (defs == null) {
            defs = new LinkedHashMap<>();
            defs.put("destruction_god", bills());
            defs.put("uchiha_ghost", madara());
        }
        return defs.get(power);
    }

    static boolean has(String power) {
        return "destruction_god".equals(power) || "uchiha_ghost".equals(power);
    }
}
