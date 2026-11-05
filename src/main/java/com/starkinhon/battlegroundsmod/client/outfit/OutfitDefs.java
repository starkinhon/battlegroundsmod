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
