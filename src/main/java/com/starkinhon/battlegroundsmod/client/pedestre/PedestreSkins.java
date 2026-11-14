package com.starkinhon.battlegroundsmod.client.pedestre;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.starkinhon.battlegroundsmod.npc.PedestreLook;
import com.starkinhon.battlegroundsmod.npc.PedestreLook.Rand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

// The pedestrians' skins (port of the approved preview's paintSkin, forja/jujutsu-pedestres/peds.js): a 64x64 skin in
// the player's layout painted pixel by pixel from the look (PedestreLook), each pixel with a little grain so cloth and
// skin don't look flat. One texture per look, kept while it is in use (the last 160 people seen), released after.
// Only the inner layer is painted; the outer layer stays see-through (the model hides it too). Also used by anything
// else that shows Shibuya people (the metro's passengers): ask for a look, get a texture.
public final class PedestreSkins {
    private static final int KEEP = 160;
    private static final Map<Integer, ResourceLocation> SKINS = new LinkedHashMap<>(64, 0.75f, true);
    private static int next;

    private PedestreSkins() {
    }

    public static ResourceLocation get(int look) {
        ResourceLocation loc = SKINS.get(look);
        if (loc != null) {
            return loc;
        }
        NativeImage img = paint(PedestreLook.of(look));
        loc = Minecraft.getInstance().getTextureManager().register("battlegroundsmod_pedestre_" + next++, new DynamicTexture(img));
        SKINS.put(look, loc);
        if (SKINS.size() > KEEP) {
            var it = SKINS.entrySet().iterator();
            ResourceLocation old = it.next().getValue();
            it.remove();
            Minecraft.getInstance().getTextureManager().release(old);
        }
        return loc;
    }

    // ------------------------------------------------------------ the player's layout
    private static final int[][] PARTS = {
            {0, 0, 8, 8, 8}, // head
            {16, 16, 8, 12, 4}, // body
            {40, 16, 4, 12, 4}, // right arm
            {32, 48, 4, 12, 4}, // left arm
            {0, 16, 4, 12, 4}, // right leg
            {16, 48, 4, 12, 4}, // left leg
    };
    private static final int HEAD = 0, BODY = 1, RARM = 2, LARM = 3, RLEG = 4, LLEG = 5;
    private static final int TOP = 0, BOTTOM = 1, RIGHT = 2, FRONT = 3, LEFT = 4, BACK = 5;

    private static int[] faceRect(int part, int face) {
        int[] p = PARTS[part];
        int ox = p[0], oy = p[1], w = p[2], h = p[3], d = p[4];
        return switch (face) {
            case TOP -> new int[] {ox + d, oy, w, d};
            case BOTTOM -> new int[] {ox + d + w, oy, w, d};
            case RIGHT -> new int[] {ox, oy + d, d, h};
            case FRONT -> new int[] {ox + d, oy + d, w, h};
            case LEFT -> new int[] {ox + d + w, oy + d, d, h};
            default -> new int[] {ox + 2 * d + w, oy + d, w, h};
        };
    }

    private static double[] dark(double[] c, double k) {
        return PedestreLook.mul(c, k);
    }

    private static double[] rgb(int r, int g, int b) {
        return new double[] {r, g, b};
    }

    private static boolean in(String s, String... list) {
        for (String x : list) {
            if (x.equals(s)) {
                return true;
            }
        }
        return false;
    }

    public static NativeImage paint(PedestreLook S) {
        return new Painter(S).paint();
    }

    private static final class Painter {
        final PedestreLook S;
        final Rand r;
        final double[] skin, hair;
        final PedestreLook.Top top;
        final PedestreLook.Piece bot;
        final String hs;
        final boolean longHair, skirt;
        final double[] fringe = new double[8];
        final int shoeRows, legTopTo, printSeed;

        Painter(PedestreLook S) {
            this.S = S;
            r = new Rand(S.look ^ 0x5EED5);
            skin = S.skin;
            hair = S.hairC;
            top = S.top;
            bot = S.bottom;
            hs = S.hairStyle;
            longHair = in(hs, "long", "twin", "wolf", "perm");
            for (int u = 0; u < 8; u++) {
                fringe[u] = r.next(); // each one's fringe, strand by strand
            }
            shoeRows = switch (S.shoes.kind) {
                case "platform" -> 3;
                case "boot" -> 4;
                case "heel" -> 1;
                default -> 2;
            };
            legTopTo = switch (bot.kind) {
                case "shorts" -> 4;
                case "pencil" -> 6;
                case "pleated" -> 5;
                case "mini" -> 3;
                case "longskirt" -> 9;
                default -> 12;
            };
            skirt = S.skirt();
            printSeed = r.integer(0, 1000000);
        }

        NativeImage paint() {
            NativeImage img = new NativeImage(64, 64, true);
            for (int part = 0; part < 6; part++) {
                for (int face = 0; face < 6; face++) {
                    int[] f = faceRect(part, face);
                    for (int v = 0; v < f[3]; v++) {
                        for (int u = 0; u < f[2]; u++) {
                            double[] c = switch (part) {
                                case HEAD -> head(face, u, v);
                                case BODY -> body(face, u, v);
                                case RARM, LARM -> arm(part, face, u, v);
                                default -> leg(part, face, u, v);
                            };
                            // the grain of cloth and skin: every pixel a little different
                            double k = 0.93 + r.next() * 0.1;
                            int R = clamp(c[0] * k), G = clamp(c[1] * k), B = clamp(c[2] * k);
                            img.setPixelRGBA(f[0] + u, f[1] + v, 0xFF000000 | (B << 16) | (G << 8) | R);
                        }
                    }
                }
            }
            return img;
        }

        static int clamp(double v) {
            return (int) Math.max(0, Math.min(255, v));
        }

        // head -----------------------------------------------------------------------------------------------
        double[] head(int face, int u, int v) {
            if (face == TOP) {
                return hs.equals("thin") ? (r.next() < 0.45 ? PedestreLook.mix(skin, hair, 0.3) : hair) : hair;
            }
            if (face == BOTTOM) {
                return longHair && v < 4 ? hair : dark(skin, 0.86);
            }
            if (face == BACK) {
                int lim = longHair || in(hs, "bob", "pony", "bun", "perm") ? 8 : in(hs, "buzz", "thin") ? 4 : 6;
                if (v < lim) {
                    return hs.equals("buzz") ? PedestreLook.mix(hair, skin, 0.25) : hair;
                }
                return dark(skin, 0.9);
            }
            if (face == LEFT || face == RIGHT) {
                double f = face == RIGHT ? u / 7.0 : 1 - u / 7.0; // 1 = by the face
                int lim = in(hs, "buzz", "thin") ? 2 : 3;
                if (f < 0.55) {
                    lim = in(hs, "buzz", "thin") ? 4 : 6;
                }
                if (longHair || hs.equals("bob")) {
                    lim = f > 0.8 ? 3 : 8;
                }
                if (hs.equals("bob") && f <= 0.8) {
                    lim = 7;
                }
                if (v < lim) {
                    return hair;
                }
                if (Math.abs(f - 0.5) < 0.1 && v >= 3 && v <= 5) {
                    return dark(skin, 0.88); // ear
                }
                if (S.mask && v == 5) {
                    return rgb(236, 236, 232); // the mask's elastic
                }
                if (S.headphones != null && v >= 2 && v <= 6 && Math.abs(f - 0.5) < 0.2) {
                    return S.headphones;
                }
                return skin;
            }
            // the face
            double[] c = skin;
            double fr = fringe[u];
            int fringeRows = switch (hs) {
                case "part" -> u < 5 ? 2 : 1;
                case "short" -> 1 + (fr < 0.4 ? 1 : 0);
                case "messy" -> 1 + (fr < 0.6 ? 1 : 0) + (fr < 0.2 ? 1 : 0);
                case "wolf" -> 2 + (fr < 0.5 ? 1 : 0);
                case "bob" -> 2 + (fr < 0.3 ? 1 : 0);
                case "long" -> 2 + (fr < 0.35 ? 1 : 0);
                case "pony" -> 1 + (fr < 0.5 ? 1 : 0);
                case "twin" -> 2 + (fr < 0.3 ? 1 : 0);
                case "perm" -> 2;
                case "thin" -> fr < 0.5 ? 1 : 0;
                default -> 1;
            };
            if (v < fringeRows) {
                return hs.equals("buzz") ? PedestreLook.mix(hair, skin, 0.3) : hair;
            }
            if ((longHair || hs.equals("bob")) && (u == 0 || u == 7) && v < 7) {
                return hair; // hair framing the face
            }
            if (v == 3 && (u == 1 || u == 2 || u == 5 || u == 6) && fringeRows <= 3) {
                c = PedestreLook.mix(skin, hair, 0.75); // eyebrow
            }
            if (v == 4) {
                if (S.sunglasses && u >= 1 && u <= 6) {
                    return u == 3 || u == 4 ? rgb(30, 30, 32) : rgb(18, 18, 22);
                }
                if (u == 2 || u == 5) {
                    c = rgb(36, 26, 20);
                } else if ((u == 1 || u == 6) && !S.elder) {
                    c = S.lashes ? rgb(24, 18, 18) : rgb(236, 234, 228);
                }
                if (S.glasses && (u == 0 || u == 3 || u == 4 || u == 7)) {
                    c = rgb(40, 40, 44);
                }
            }
            if (v == 3 && S.glasses && (u == 1 || u == 2 || u == 5 || u == 6)) {
                c = rgb(52, 52, 56);
            }
            if (S.mask && v >= 5) {
                return v == 7 ? rgb(214, 214, 210) : rgb(240, 240, 236);
            }
            if (v == 5 && (u == 1 || u == 6) && S.blush) {
                c = PedestreLook.mix(skin, rgb(230, 120, 120), 0.35);
            }
            if (v == 5 && (u == 3 || u == 4)) {
                c = dark(skin, 0.93); // the nose's shadow
            }
            if (v == 6 && (u == 3 || u == 4)) {
                c = S.female && S.lashes ? rgb(200, 90, 100) : dark(skin, S.female ? 0.82 : 0.86);
            }
            if (v == 7 && S.elder && !S.female && (u == 2 || u == 5)) {
                c = dark(skin, 0.9);
            }
            if (S.towel && v == 0) {
                return rgb(240, 240, 236); // towel on the forehead under the helmet
            }
            return c;
        }

        // trunk ----------------------------------------------------------------------------------------------
        double[] printPx(int u, int v) {
            // the tee's print: a drawn 6x5 picture, mirrored to look like a logo
            if (top.print == null || u < 1 || u > 6 || v < 2 || v > 6) {
                return null;
            }
            int mu = u < 4 ? u - 1 : 6 - u;
            return hash(mu * 7.1 + v * 13.7 + printSeed) < 0.5 ? top.print : null;
        }

        static double hash(double x) {
            double h = Math.sin(x * 12.9898) * 43758.5453;
            return h - Math.floor(h);
        }

        boolean strap(int u, int v) {
            // the camera's/bag's strap across (left shoulder to right hip)
            return S.strap && Math.abs((7 - u) - v * 0.62) < 0.55;
        }

        double[] body(int face, int u, int v) {
            double[] c = top.c;
            if (face == BOTTOM) {
                return bot.c;
            }
            if (face == TOP) {
                if (top.kind.equals("sailor")) {
                    return top.collar;
                }
                if (top.kind.equals("vest")) {
                    return u >= 2 && u <= 5 ? top.shirt : c;
                }
                return c;
            }
            boolean side = face == LEFT || face == RIGHT;
            boolean back = face == BACK;
            double cu = u - 3.5;
            // belt and hem
            boolean tuck = top.kind.equals("suit") || top.kind.equals("gakuran") || top.tuck;
            if (v == 11 && (tuck || in(top.kind, "blazer", "vest", "crop")) && !skirt && !bot.kind.equals("tobi")) {
                if (!side && !back && (u == 3 || u == 4) && !top.kind.equals("gakuran")) {
                    return rgb(170, 160, 140);
                }
                return rgb(28, 24, 22);
            }
            if (S.back != null && !side && !back && (u == 1 || u == 6) && v <= 8) {
                return PedestreLook.mul(S.back.c, 0.75); // the backpack's straps
            }
            if (!side && !back && strap(u, v)) {
                return rgb(34, 30, 28);
            }
            switch (top.kind) {
                case "suit": {
                    if (side || back) {
                        return v == 11 ? dark(c, 0.9) : (back && (u == 3 || u == 4) && v > 7 ? dark(c, 0.88) : c);
                    }
                    double vw = Math.max(0, 2.2 - v * 0.32); // the jacket's V
                    if (Math.abs(cu) < 0.6 + vw || top.open && Math.abs(cu) < 1.2) {
                        if (Math.abs(cu) < 0.6 && v >= 1 && v <= (top.open ? 7 : 8)) {
                            return v == 1 ? dark(top.tie, 0.85) : top.tie;
                        }
                        if (v < 7 || top.open) {
                            return top.shirt;
                        }
                    }
                    if (Math.abs(Math.abs(cu) - (0.6 + vw)) < 0.6 && v < 7) {
                        return dark(c, 0.78); // lapel
                    }
                    if (u == 3 && (v == 8 || v == 10)) {
                        return dark(c, 0.6);
                    }
                    if (v == 9 && (u == 1 || u == 6)) {
                        return dark(c, 0.85); // pocket
                    }
                    return c;
                }
                case "blazer": {
                    if (side || back) {
                        return c;
                    }
                    if (Math.abs(cu) < 1.6) {
                        return v == 0 ? PedestreLook.mul(top.shirt, 0.96) : top.shirt;
                    }
                    if (Math.abs(cu) < 2.3 && v < 6) {
                        return dark(c, 0.82);
                    }
                    return c;
                }
                case "blouse": {
                    if (side || back) {
                        return c;
                    }
                    if (v == 0 && Math.abs(cu) < 1.6) {
                        return PedestreLook.mul(c, 0.92);
                    }
                    if (Math.abs(cu) < 0.6 && v > 0 && v % 3 == 1) {
                        return PedestreLook.mul(c, 0.82);
                    }
                    return c;
                }
                case "sailor": {
                    if (back) {
                        if (v <= 4) {
                            return v == 4 || u == 0 || u == 7 ? top.stripe : top.collar;
                        }
                        return c;
                    }
                    if (side) {
                        return v <= 1 ? top.collar : c;
                    }
                    double vw = Math.max(0, 3.4 - v * 0.75);
                    if (v <= 4 && Math.abs(cu) > vw) {
                        return Math.abs(Math.abs(cu) - vw) < 0.75 ? top.stripe : top.collar;
                    }
                    if ((v == 3 || v == 4) && Math.abs(cu) < 1.6) {
                        return v == 3 ? top.scarf : dark(top.scarf, 0.85); // the bow
                    }
                    if ((v == 5 || v == 6) && Math.abs(cu) < 0.6) {
                        return dark(top.scarf, 0.9);
                    }
                    return c;
                }
                case "gakuran": {
                    if (v == 0) {
                        return dark(c, 0.75); // high collar
                    }
                    if (!side && !back && Math.abs(cu) < 0.6 && v % 2 == 1 && v < 11) {
                        return u == 3 ? top.button : c;
                    }
                    if (!side && !back && u == 4 && v % 2 == 1 && v < 11) {
                        return PedestreLook.mul(top.button, 0.7);
                    }
                    return c;
                }
                case "hoodie": {
                    if (back) {
                        if (v <= 3 && u >= 1 && u <= 6) {
                            return v == 3 || u == 1 || u == 6 ? dark(c, 0.7) : dark(c, 0.85);
                        }
                        return v == 11 ? dark(c, 0.85) : c;
                    }
                    if (side) {
                        return v == 11 ? dark(c, 0.85) : c;
                    }
                    if (v == 0 && Math.abs(cu) < 1.6) {
                        return dark(c, 0.6);
                    }
                    if ((u == 2 || u == 5) && v >= 1 && v <= 4) {
                        return rgb(236, 234, 228);
                    }
                    if (v >= 7 && v <= 9 && u >= 1 && u <= 6) {
                        return v == 7 || u == 1 || u == 6 ? dark(c, 0.78) : PedestreLook.mul(c, 0.94); // kangaroo pocket
                    }
                    if (v == 11) {
                        return dark(c, 0.85);
                    }
                    double[] p = printPx(u, v);
                    if (p != null && v < 6) {
                        return p;
                    }
                    return c;
                }
                case "tee": {
                    if (!side && !back && v == 0 && Math.abs(cu) < 1.6) {
                        return top.polo ? rgb(250, 250, 248) : skin;
                    }
                    if (back || side) {
                        return top.big && v == 11 ? dark(c, 0.9) : c;
                    }
                    double[] p = printPx(u, v);
                    return p != null ? p : c;
                }
                case "cardigan": {
                    if (side || back) {
                        return (u + v) % 2 != 0 ? PedestreLook.mul(c, 0.94) : c; // knit
                    }
                    if (Math.abs(cu) < 0.6 && v < 4) {
                        return top.shirt;
                    }
                    if (u == 4 && v >= 4 && v % 2 == 0) {
                        return PedestreLook.mul(c, 0.6);
                    }
                    if (Math.abs(cu) < 0.6) {
                        return dark(c, 0.86);
                    }
                    return (u + v) % 2 != 0 ? PedestreLook.mul(c, 0.94) : c;
                }
                case "quilt": {
                    // quilted vest over the blouse: diamond seams, the blouse's collar showing
                    if (!side && !back && Math.abs(cu) < 0.6) {
                        return v < 2 ? top.shirt : rgb(150, 150, 156);
                    }
                    if (!side && !back && v == 0) {
                        return top.shirt;
                    }
                    return (u + v) % 3 == 0 || (u - v + 30) % 3 == 0 ? PedestreLook.mul(c, 0.82) : c;
                }
                case "floral": {
                    // floral blouse: drawn one-pixel flowers
                    if (!side && !back && v == 0 && Math.abs(cu) < 1.6) {
                        return PedestreLook.mul(c, 0.9);
                    }
                    return hash(u * 12.3 + v * 7.7 + printSeed + (side ? 50 : back ? 90 : 0)) < 0.18 ? top.print : c;
                }
                case "vest": {
                    double[] band = top.band;
                    if (back) {
                        if (v == 6 || v == 7) {
                            return band;
                        }
                        return v < 6 && Math.abs(Math.abs(cu) - (3.2 - v * 0.55)) < 0.6 ? band : c; // the V stripes on the back
                    }
                    if (side) {
                        return v == 6 || v == 7 ? band : c;
                    }
                    if (Math.abs(cu) < 0.6) {
                        return v < 3 ? top.shirt : rgb(180, 180, 186); // zipper
                    }
                    if (v == 6 || v == 7) {
                        return band;
                    }
                    if ((u == 1 || u == 6) && v < 6) {
                        return band;
                    }
                    return c;
                }
                case "crop": {
                    if (v >= 8) {
                        return skin; // bare belly
                    }
                    double[] j = top.jacket;
                    if (j != null && (side || back || Math.abs(cu) > 1.6)) {
                        return v == 7 ? dark(j, 0.85) : j;
                    }
                    if (!side && !back && v == 0 && Math.abs(cu) < 1.6) {
                        return skin;
                    }
                    return c;
                }
                default:
                    return c;
            }
        }

        // arms -----------------------------------------------------------------------------------------------
        double[] arm(int part, int face, int u, int v) {
            double[] sleeveColor = top.kind.equals("blazer") ? top.c : in(top.kind, "vest", "quilt") ? top.shirt
                    : top.kind.equals("crop") && top.jacket != null ? top.jacket : top.kind.equals("crop") ? skin : top.c;
            if (face == BOTTOM) {
                return skin;
            }
            if (face == TOP) {
                return top.kind.equals("sailor") ? top.collar : sleeveColor;
            }
            boolean shortSleeve = S.sleeve.equals("short") || (top.kind.equals("crop") && top.jacket == null);
            if (shortSleeve) {
                if (v <= 3) {
                    return v == 3 ? dark(sleeveColor, 0.88) : (top.kind.equals("sailor") && v == 2 ? top.stripe : sleeveColor);
                }
                return v == 11 ? dark(skin, 0.94) : skin;
            }
            if (v == 11) {
                return skin;
            }
            if (v == 10) {
                if (in(top.kind, "suit", "blazer")) {
                    return top.shirt != null ? top.shirt : top.c;
                }
                if (S.watch && part == LARM) {
                    return rgb(190, 190, 196);
                }
                if (in(top.kind, "hoodie", "cardigan")) {
                    return dark(sleeveColor, 0.82);
                }
            }
            if (top.kind.equals("sailor") && (v == 8 || v == 9)) {
                return v == 8 ? top.stripe : top.collar;
            }
            if (top.kind.equals("gakuran") && v == 9 && (u == 1 || u == 2) && face == FRONT) {
                return top.button;
            }
            if (top.kind.equals("cardigan")) {
                return (u + v) % 2 != 0 ? PedestreLook.mul(sleeveColor, 0.94) : sleeveColor;
            }
            if (top.kind.equals("floral") && hash(u * 12.3 + v * 7.7 + printSeed + (part == LARM ? 7 : 3)) < 0.18) {
                return top.print;
            }
            return sleeveColor;
        }

        // legs -----------------------------------------------------------------------------------------------
        double[] legwear(int v) {
            PedestreLook.Piece L = S.legwear;
            if (L == null || L.kind.equals("skin")) {
                return skin;
            }
            return switch (L.kind) {
                case "stocking" -> PedestreLook.mix(skin, L.c, L.c[0] < 90 ? 0.72 : 0.5);
                case "kneesock" -> v >= 7 ? (v == 7 ? PedestreLook.mul(L.c, 0.85) : L.c) : skin;
                case "lowsock" -> v >= 12 - shoeRows - 1 ? L.c : skin;
                case "loosesock" -> v >= 6 ? ((v + 1) % 2 != 0 ? L.c : PedestreLook.mul(L.c, 0.88)) : skin; // the gyaru's loose socks
                default -> skin;
            };
        }

        double[] leg(int part, int face, int u, int v) {
            PedestreLook.Piece sh = S.shoes;
            if (face == BOTTOM) {
                return sh.kind.equals("sneaker") ? rgb(220, 218, 212) : sh.kind.equals("platform") ? PedestreLook.mul(sh.c, 0.7) : rgb(24, 22, 20);
            }
            if (face == TOP) {
                return bot.c;
            }
            boolean outer = face == LEFT && part == LLEG || face == RIGHT && part == RLEG;
            if (v >= 12 - shoeRows) {
                switch (sh.kind) {
                    case "sneaker": {
                        if (v == 11) {
                            return rgb(226, 224, 218);
                        }
                        if (outer && v == 10) {
                            return sh.c2; // the outer stripe
                        }
                        if (face == FRONT && v == 10 && (u == 1 || u == 2)) {
                            return PedestreLook.mul(sh.c, 0.9);
                        }
                        return sh.c;
                    }
                    case "platform":
                        return v == 11 ? PedestreLook.mul(sh.c, 0.75) : sh.c;
                    case "boot":
                        return v == 8 ? PedestreLook.mul(sh.c, 1.3) : sh.c;
                    case "heel":
                        return sh.c;
                    default:
                        return v == 11 ? PedestreLook.mul(sh.c, 0.7) : sh.c;
                }
            }
            if (v < legTopTo) {
                double[] c = bot.c;
                switch (bot.kind) {
                    case "jeans": {
                        if (v == legTopTo - 1 && legTopTo == 12) {
                            return PedestreLook.mul(c, 1.25); // rolled hem
                        }
                        if (face == FRONT && (v == 4 || v == 5) && (u == 1 || u == 2)) {
                            return PedestreLook.mul(c, 1.12); // worn knee
                        }
                        return (u + v) % 3 == 0 ? PedestreLook.mul(c, 0.92) : c;
                    }
                    case "cargo":
                        if (outer && v >= 4 && v <= 6) {
                            return v == 4 ? dark(c, 0.75) : dark(c, 0.88);
                        }
                        return c;
                    case "tobi":
                        return v >= 6 ? (v % 2 != 0 ? PedestreLook.mul(c, 0.9) : c) : c; // wide work trousers
                    case "pants":
                        return face == FRONT && (u == 1 || u == 2) && v > 1 ? PedestreLook.mul(c, 0.94) : c;
                    case "shorts":
                        return v == legTopTo - 1 ? dark(c, 0.85) : c;
                    default:
                        return c; // under the skirt: the skirt itself
                }
            }
            return legwear(v);
        }
    }
}
