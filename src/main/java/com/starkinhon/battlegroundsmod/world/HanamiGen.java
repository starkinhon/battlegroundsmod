package com.starkinhon.battlegroundsmod.world;

import static com.starkinhon.battlegroundsmod.world.JjkGen.cos;
import static com.starkinhon.battlegroundsmod.world.JjkGen.floor;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hash3;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hashInt;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hypot;
import static com.starkinhon.battlegroundsmod.world.JjkGen.round;
import static com.starkinhon.battlegroundsmod.world.JjkGen.sin;
import static com.starkinhon.battlegroundsmod.world.JjkGen.tube;
import static com.starkinhon.battlegroundsmod.world.JjkGen.valueNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

import com.starkinhon.battlegroundsmod.world.JjkGen.TypeAt;
import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;
import com.starkinhon.battlegroundsmod.world.TemploGen.Vox;

// The Floresta de Hanami (Stark 2026-11-12; port released 2026-11-13): line-by-line port of the approved preview's
// generator (forja/jujutsu-hanami/gen.js), the same draws and the same hashes from the world's coordinates. The curse of
// the forests' forest, the Floresta Negra's opposite: a virgin forest too alive. Giant ancient trees with roots arching
// over the ground, moss, ferns, flowers of every color on the ground, giant flowers, huge mushrooms, mossy fallen logs.
// The danger shows in the details: fans of sharp wooden stakes sprouting from the ground (Hanami's attack), cursed buds
// (green bulbs with pink tips) stuck to the trunks sucking energy. In the middle, the Coração da Hanami: a clearing of
// flowers around a colossal tree with two holes in its trunk like her eyes, crooked branches coming out of them like
// horns.
public final class HanamiGen {
    // The preview's NAMES, in its order.
    public static final byte AIR = 0, STONE = 1, DIRT = 2, GRASS = 3, MOSS = 4, BARK = 5, BARK_MOSS = 6, WOOD_IN = 7, LEAF = 8,
            LEAF2 = 9, LEAF3 = 10, BLOSSOM = 11, FERN = 12, TALLGRASS = 13, FLOWER_W = 14, FLOWER_Y = 15, FLOWER_P = 16,
            FLOWER_R = 17, FLOWER_B = 18, PETAL_W = 19, PETAL_P = 20, PETAL_Y = 21, PETAL_R = 22, STEM = 23, POLLEN = 24,
            CAP = 25, CAP_DOT = 26, STALK = 27, SPIKE = 28, BUD = 29, BUD_TIP = 30, HOLLOW = 31, VINE = 32, WATER = 33,
            ROCK = 34;

    // How far an ancient tree's blocks reach from its column: a big one's branch (13) and its crown (7), its lean (2.5)
    // and its trunk (3.2), a root (12).
    public static final int TREE_REACH = 27;
    // How far a ground object reaches from its column: a fallen log (14 long, 1.4 thick), a fan of stakes (3 + 7).
    public static final int OBJECT_REACH = 17;
    // How far the Heart reaches from its center: the canopy trimmed (R + 14), the colossal crown (16) and its branches
    // (24 out, a crown of 9 on each).
    public static final int HEART_REACH = 50;
    // Nothing of the woods (trees, objects) within this of the Heart's center (the preview's keepOut).
    public static final int HEART_KEEP = 36;
    // The Heart's meadow (the flowers dense) within this of its center.
    public static final int MEADOW = 34;
    public static final int HEART_R = 32;

    private static final byte[] FLOWERS = {FLOWER_W, FLOWER_Y, FLOWER_P, FLOWER_R, FLOWER_B};

    private HanamiGen() {
    }

    // ---------------------------------------------------------------- trees

    // Bark with moss: the moss covers the trunk's foot and patches all over.
    static TypeAt barkAt(int seed, int gy) {
        return (x, y, z) -> y < gy + 4 || hash3(x, y, z, seed) < 0.22 ? BARK_MOSS : BARK;
    }

    // Crown: a flattened fluffy mass, three greens; some trees in bloom (white and pink crown mixed).
    public static void crown(Vox w, int cx, int cy, int cz, double rx, double ry, int seed, boolean bloom) {
        int ri = (int) Math.ceil(rx), rj = (int) Math.ceil(ry);
        for (int a = -ri; a <= ri; a++) {
            for (int b = -rj; b <= rj; b++) {
                for (int c = -ri; c <= ri; c++) {
                    double d = hypot(a / rx, b / ry, c / rx) + (hash3(cx + a, cy + b, cz + c, seed) - 0.5) * 0.45;
                    if (d > 1) {
                        continue;
                    }
                    double h = hash3(cx + a, cy + b, cz + c, seed + 1);
                    w.set(cx + a, cy + b, cz + c, bloom && h < 0.35 ? BLOSSOM : h < 0.3 ? LEAF : h < 0.7 ? LEAF2 : LEAF3);
                    if (b == -rj + 1 && hash3(cx + a, 0, cz + c, seed + 2) < 0.05) {
                        int len = 2 + floor(hash3(cx + a, 1, cz + c, seed + 2) * 7);
                        for (int k = 1; k <= len; k++) {
                            w.set(cx + a, cy + b - k, cz + c, VINE);
                        }
                    }
                }
            }
        }
    }

    // Ancient tree: a thick straight trunk with a slight lean, roots leaving the trunk 3 to 6 up and arching down to the
    // ground far off (one can walk under the big ones'), thick branches opening up high, a wide crown in several masses.
    // Now and then a cursed bud stuck to the trunk. Returns whether it is a big one.
    public static boolean ancient(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        boolean big = rng.next() < 0.3;
        int H = big ? rng.nextInt(26, 36) : rng.nextInt(14, 22);
        double R = big ? rng.range(2.2, 3.2) : rng.range(1.0, 1.6);
        TypeAt bark = barkAt(seed + 5, y);
        double lean = rng.range(0, Math.PI * 2), lk = rng.range(0.5, 2.5);
        double[] top = {x + 0.5 + cos(lean) * lk, y + H, z + 0.5 + sin(lean) * lk};
        double[] mid = {x + 0.5 + cos(lean) * lk * 0.4, y + H * 0.5, z + 0.5 + sin(lean) * lk * 0.4};
        tube(w, new double[] {x + 0.5, y - 2, z + 0.5}, mid, R, R * 0.85, bark);
        tube(w, new double[] {x + 0.5 + cos(lean) * lk * 0.4, y + H * 0.5, z + 0.5 + sin(lean) * lk * 0.4}, top, R * 0.85, R * 0.6, bark);
        // arching roots
        int nr = big ? rng.nextInt(5, 7) : rng.nextInt(3, 4);
        for (int i = 0; i < nr; i++) {
            double a = (double) i / nr * Math.PI * 2 + rng.range(-0.3, 0.3), L = big ? rng.range(7, 12) : rng.range(3, 5);
            double fy = y + (big ? rng.range(3, 6) : 1.5);
            double[] from = {x + 0.5 + cos(a) * R * 0.6, fy, z + 0.5 + sin(a) * R * 0.6};
            double[] m = {x + 0.5 + cos(a) * L * 0.55, from[1] + (big ? 1.5 : 0.3), z + 0.5 + sin(a) * L * 0.55};
            double[] end = {x + 0.5 + cos(a) * L, y - 2, z + 0.5 + sin(a) * L};
            tube(w, from, m, big ? 1.4 : 0.8, big ? 1.0 : 0.6, bark);
            tube(w, m, end, big ? 1.0 : 0.6, 0.5, bark);
        }
        // branches and crown
        boolean bloom = !big && rng.next() < 0.18;
        int nb = big ? rng.nextInt(5, 7) : rng.nextInt(3, 5);
        crown(w, floor(top[0]), floor(top[1]) + 1, floor(top[2]), big ? 8 : 5, big ? 4 : 3, seed + 9, bloom);
        for (int i = 0; i < nb; i++) {
            double a = rng.range(0, Math.PI * 2), l = big ? rng.range(8, 13) : rng.range(4, 7);
            double[] from = {top[0], top[1] - rng.range(2, H * 0.3), top[2]};
            double[] end = {from[0] + cos(a) * l, from[1] + rng.range(2, 5), from[2] + sin(a) * l};
            tube(w, from, end, big ? 1.1 : 0.6, 0.4, bark);
            double crx = big ? rng.range(5, 7) : rng.range(3, 4.5);
            crown(w, floor(end[0]), floor(end[1]) + 1, floor(end[2]), crx, big ? 3 : 2.2, seed + 10 + i, bloom);
        }
        if (rng.next() < 0.15) {
            bud(w, floor(x + 0.5 + cos(lean + 2) * (R + 0.8)), y + rng.nextInt(2, 6), floor(z + 0.5 + sin(lean + 2) * (R + 0.8)));
        }
        return big;
    }

    // Cursed bud: a green bulb of 3 stuck to the trunk, with a pink tip.
    public static void bud(Vox w, int x, int y, int z) {
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                for (int c = 0; c <= 2; c++) {
                    if (Math.abs(a) + Math.abs(b) + Math.abs(c - 1) <= 2) {
                        w.force(x + a, y + c, z + b, BUD);
                    }
                }
            }
        }
        w.force(x, y + 3, z, BUD_TIP);
        w.force(x, y + 4, z, BUD_TIP);
    }

    // ---------------------------------------------------------------- ground

    // Giant flower: a tall green stem with leaves on it, a rounded 5 x 5 disc of petals in the flower's color, a yellow
    // heart.
    public static void giantFlower(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(5, 9);
        byte pet = new byte[] {PETAL_W, PETAL_P, PETAL_Y, PETAL_R}[rng.nextInt(0, 3)];
        for (int k = 0; k < H; k++) {
            w.force(x, y + k, z, STEM);
        }
        w.force(x + 1, y + 2, z, LEAF2);
        w.force(x - 1, y + 3, z, LEAF2);
        int ty = y + H;
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                if (a * a + b * b > 5) {
                    continue;
                }
                w.force(x + a, ty + (a * a + b * b >= 4 ? 1 : 0), z + b, a == 0 && b == 0 ? POLLEN : pet);
            }
        }
    }

    // Giant mushroom: a pale stalk and a red cap with white dots.
    public static void mushroom(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(4, 7), r = rng.nextInt(2, 3);
        for (int k = 0; k < H; k++) {
            w.force(x, y + k, z, STALK);
        }
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                int d = a * a + b * b;
                if (d > r * r + 1) {
                    continue;
                }
                int yy = y + H + (d <= (r - 1) * (r - 1) ? 1 : 0);
                w.force(x + a, yy, z + b, hash3(x + a, yy, z + b, seed) < 0.2 ? CAP_DOT : CAP);
            }
        }
    }

    // Fan of stakes: 5 to 9 sharp wooden stakes sprouting from the ground leaning outward, as if something had just
    // attacked from there; churned earth around.
    public static void spikes(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int n = rng.nextInt(5, 9);
        double dir = rng.range(0, Math.PI * 2);
        for (int i = 0; i < n; i++) {
            double a = dir + rng.range(-0.9, 0.9), L = rng.range(4, 9), tilt = rng.range(0.35, 0.8);
            double bx = x + 0.5 + cos(a) * rng.range(0, 3);
            double bz = z + 0.5 + sin(a) * rng.range(0, 3);
            tube(w, new double[] {bx, y - 1, bz}, new double[] {bx + cos(a) * L * tilt, y + L, bz + sin(a) * L * tilt}, 0.9, 0.2, SPIKE);
        }
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                if (hash3(x + a, y, z + b, seed) < 0.5) {
                    w.force(x + a, y - 1, z + b, DIRT);
                }
            }
        }
    }

    // A fallen log covered in moss.
    public static void log(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, Math.PI * 2);
        int L = rng.nextInt(8, 14);
        double r = rng.range(0.9, 1.4);
        tube(w, new double[] {x, y + r - 0.5, z}, new double[] {x + cos(a) * L, y + r - 0.5, z + sin(a) * L}, r, r * 0.9,
                (xx, yy, zz) -> yy >= y + r - 0.3 || hash3(xx, yy, zz, seed) < 0.3 ? MOSS : BARK);
    }

    public static int heightAt(int x, int z, int seed) {
        double big = valueNoise(x, z, seed, 90), mid = valueNoise(x, z, seed + 1, 30), sm = valueNoise(x, z, seed + 2, 9);
        return floor(12 + (big - 0.5) * 18 + (mid - 0.5) * 8 + (sm - 0.5) * 2);
    }

    // The ground: living grass with patches of moss.
    public static byte groundTop(int x, int z, int seed) {
        return valueNoise(x, z, seed + 40, 7) > 0.62 ? MOSS : GRASS;
    }

    public static void ground0(Vox w, int x, int z, int h, int seed) {
        w.force(x, h, z, groundTop(x, z, seed));
        for (int k = 1; k <= 3; k++) {
            w.force(x, h - k, z, k <= 2 ? DIRT : STONE);
        }
    }

    // Flowers in patches by color (each patch pulls one color); ferns and tall grass.
    public static void plantAt(Vox w, int x, int h, int z, int seed, boolean meadow) {
        if (w.get(x, h + 1, z) != 0) {
            return;
        }
        double r = hash3(x, 9, z, seed + 30);
        double patch = valueNoise(x, z, seed + 31, 8);
        byte fc = FLOWERS[floor(valueNoise(x, z, seed + 32, 11) * 5) % 5];
        double fDens = meadow ? 0.55 : patch > 0.6 ? 0.35 : 0.04;
        if (r < fDens) {
            w.force(x, h + 1, z, hash3(x, 10, z, seed) < 0.75 ? fc : FLOWERS[hashInt(x, 11, z, seed) % 5]);
        } else if (r < fDens + 0.1) {
            w.force(x, h + 1, z, TALLGRASS);
        } else if (!meadow && r < fDens + 0.16) {
            w.force(x, h + 1, z, FERN);
            if (r < fDens + 0.115) {
                w.force(x, h + 2, z, FERN);
            }
        }
    }

    // ---------------------------------------------------------------- Coração da Hanami

    public record Heart(int cx, int cz, int y, int R, double fx, double fz, int eyeY) {
    }

    // The meadow-clearing: leveled gently toward the center.
    public static void heartClearing(Vox w, int cx, int cz, IntBinaryOperator ground, int seed) {
        final int R = HEART_R;
        int gy = ground.applyAsInt(cx, cz);
        for (int x = cx - R - 4; x <= cx + R + 4; x++) {
            for (int z = cz - R - 4; z <= cz + R + 4; z++) {
                double d = hypot(x - cx, z - cz) + (valueNoise(x, z, seed + 1, 9) - 0.5) * 6;
                if (d > R) {
                    continue;
                }
                int g = ground.applyAsInt(x, z);
                double t = Math.min(1, Math.max(0, (R - d) / 12));
                int y = round(g + (gy - g) * t);
                for (int yy = Math.min(g, y) - 3; yy <= Math.max(g, y) + 30; yy++) {
                    w.force(x, yy, z, yy < y ? DIRT : AIR);
                }
                w.force(x, y, z, GRASS);
            }
        }
    }

    // The canopy of the woods around covering the meadow is trimmed (the meadow open to the sky, the tree seen from afar).
    public static boolean trimmed(int x, int z, int cx, int cz, int seed) {
        return hypot(x - cx, z - cz) <= HEART_R + 4 + (valueNoise(x, z, seed + 4, 8) - 0.5) * 8;
    }

    public static void heartTrim(Vox w, int cx, int cz, IntBinaryOperator ground, int seed, int x0, int z0, int x1, int z1) {
        final int R = HEART_R;
        int gy = ground.applyAsInt(cx, cz);
        for (int x = Math.max(x0, cx - R - 14); x <= Math.min(x1, cx + R + 14); x++) {
            for (int z = Math.max(z0, cz - R - 14); z <= Math.min(z1, cz + R + 14); z++) {
                if (!trimmed(x, z, cx, cz, seed)) {
                    continue;
                }
                for (int y = gy - 10; y < gy + 90; y++) {
                    int t = w.get(x, y, z);
                    if (t == LEAF || t == LEAF2 || t == LEAF3 || t == BLOSSOM || t == VINE) {
                        w.force(x, y, z, AIR);
                    }
                }
            }
        }
    }

    // The colossal tree, its eyes and horns, its crown, and the meadow's giant flowers, stakes and buds.
    public static Heart heartTree(Vox w, int cx, int cz, IntBinaryOperator ground, int seed) {
        Rng rng = new Rng(seed);
        final int R = HEART_R;
        int gy = ground.applyAsInt(cx, cz);
        final int TR = 6, TH = 60;
        TypeAt bark = barkAt(seed + 5, gy);
        tube(w, new double[] {cx + 0.5, gy - 3, cz + 0.5}, new double[] {cx + 0.5, gy + TH * 0.6, cz + 0.5}, TR, TR * 0.8, bark);
        tube(w, new double[] {cx + 0.5, gy + TH * 0.6, cz + 0.5}, new double[] {cx + 1.5, gy + TH, cz + 0.5}, TR * 0.8, TR * 0.55, bark);
        for (int i = 0; i < 9; i++) {
            double a = (double) i / 9 * Math.PI * 2 + rng.range(-0.2, 0.2), L = rng.range(14, 22);
            double[] from = {cx + 0.5 + cos(a) * TR * 0.5, gy + rng.range(6, 10), cz + 0.5 + sin(a) * TR * 0.5};
            double[] m = {cx + 0.5 + cos(a) * L * 0.55, from[1] + 1, cz + 0.5 + sin(a) * L * 0.55};
            tube(w, from, m, 2.6, 1.8, bark);
            tube(w, m, new double[] {cx + 0.5 + cos(a) * L, gy - 2, cz + 0.5 + sin(a) * L}, 1.8, 0.7, bark);
        }
        // the eyes: two deep holes side by side in the trunk's front, 14 up, crooked branches coming out of them up and
        // outward like Hanami's horns
        double fa = rng.range(0, Math.PI * 2), fx = cos(fa), fz = sin(fa);
        int ey = gy + 14;
        for (int side = -1; side <= 1; side += 2) {
            double ox = cx + 0.5 + fx * (TR - 1) - fz * side * 2.6, oz = cz + 0.5 + fz * (TR - 1) + fx * side * 2.6;
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    for (int d = -1; d <= 3; d++) {
                        if (a * a / 2.2 + b * b / 4.5 > 1) {
                            continue;
                        }
                        int x = floor(ox + fx * d - fz * a), z = floor(oz + fz * d + fx * a), y = ey + b;
                        w.force(x, y, z, d <= -1 ? HOLLOW : AIR);
                    }
                }
            }
            // the horn-branch comes out of the eye
            double[] base = {ox + fx * 1.5, ey + 0.5, oz + fz * 1.5};
            double[] m1 = {base[0] + fx * 4 - fz * side * 3, ey + 6, base[2] + fz * 4 + fx * side * 3};
            double[] m2 = {m1[0] + fx * 2 - fz * side * 4, ey + 13, m1[2] + fz * 2 + fx * side * 4};
            tube(w, base, m1, 1.3, 0.9, BARK);
            tube(w, m1, m2, 0.9, 0.35, BARK);
            for (int k = 0; k < 2; k++) {
                double bx = m1[0] - fz * side * rng.range(1, 3);
                double by = m1[1] + rng.range(2, 4);
                double bz = m1[2] + fx * side * rng.range(1, 3);
                tube(w, m1, new double[] {bx, by, bz}, 0.5, 0.3, BARK);
            }
        }
        // branches and the immense crown, in bloom
        double[] top = {cx + 1.5, gy + TH, cz + 0.5};
        crown(w, floor(top[0]), floor(top[1]) + 2, floor(top[2]), 16, 7, seed + 9, true);
        for (int i = 0; i < 8; i++) {
            double a = (double) i / 8 * Math.PI * 2 + rng.range(-0.3, 0.3), l = rng.range(14, 20);
            double[] from = {top[0], top[1] - rng.range(6, 16), top[2]};
            double[] end = {from[0] + cos(a) * l * 1.2, from[1] + rng.range(3, 7), from[2] + sin(a) * l * 1.2};
            tube(w, from, end, 2.0, 0.6, bark);
            crown(w, floor(end[0]), floor(end[1]) + 1, floor(end[2]), rng.range(7, 9), 4, seed + 20 + i, true);
        }
        // the meadow: giant flowers, fans of stakes, buds at the roots' feet
        List<int[]> flowers = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            double a = rng.range(0, Math.PI * 2), d = rng.range(TR + 14, R - 3);
            int x = round(cx + cos(a) * d), z = round(cz + sin(a) * d);
            boolean close = false;
            for (int[] f : flowers) {
                if (hypot(f[0] - x, f[1] - z) < 6) {
                    close = true;
                    break;
                }
            }
            if (close) {
                continue;
            }
            flowers.add(new int[] {x, z});
            giantFlower(w, x, topOf(w, x, z, ground) + 1, z, hashInt(i, 3, 3, seed));
        }
        for (int i = 0; i < 4; i++) {
            double a = rng.range(0, Math.PI * 2), d = rng.range(TR + 10, R - 6);
            int x = round(cx + cos(a) * d), z = round(cz + sin(a) * d);
            spikes(w, x, topOf(w, x, z, ground) + 1, z, hashInt(i, 4, 4, seed));
        }
        for (int i = 0; i < 5; i++) {
            double a = rng.range(0, Math.PI * 2);
            int x = round(cx + cos(a) * (TR + 1.5)), z = round(cz + sin(a) * (TR + 1.5));
            bud(w, x, gy + rng.nextInt(1, 8), z);
        }
        return new Heart(cx, cz, gy, R, fx, fz, ey);
    }

    public static Heart heart(Vox w, int cx, int cz, IntBinaryOperator ground, int seed) {
        heartClearing(w, cx, cz, ground, seed);
        heartTrim(w, cx, cz, ground, seed, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2);
        return heartTree(w, cx, cz, ground, seed);
    }

    public static int topOf(Vox w, int x, int z, IntBinaryOperator ground) {
        int g = ground.applyAsInt(x, z);
        for (int y = g + 30; y >= g - 6; y--) {
            int t = w.get(x, y, z);
            if (t == GRASS || t == MOSS || t == DIRT) {
                return y;
            }
        }
        return g;
    }

    // ---------------------------------------------------------------- the woods, spot by spot

    // An ancient tree's spot on the grid of 10 (gx, gz on it): its column, or null.
    public static int[] treeSpot(int gx, int gz, int seed) {
        int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 10), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 10);
        if (hash3(x, 3, z, seed + 20) > 0.62) {
            return null;
        }
        return new int[] {x, z};
    }

    public static boolean treeAt(Vox w, int x, int z, int h, int seed) {
        return ancient(w, x, h + 1, z, hashInt(x, h, z, seed + 23));
    }

    // A ground object's spot on the grid of 24: {x, z, kind} (0 mushroom, 1 giant flower, 2 log, 3 stakes), or null.
    public static int[] objectSpot(int gx, int gz, int seed) {
        double r = hash3(gx, 7, gz, seed + 32);
        int x = gx + 4 + floor(hash3(gx, 8, gz, seed + 32) * 16), z = gz + 4 + floor(hash3(gx, 9, gz, seed + 32) * 16);
        int kind = r < 0.25 ? 0 : r < 0.45 ? 1 : r < 0.62 ? 2 : r < 0.72 ? 3 : -1;
        return new int[] {x, z, kind};
    }

    public static void objectAt(Vox w, int[] o, int h, int seed) {
        int x = o[0], z = o[1];
        switch (o[2]) {
            case 0 -> mushroom(w, x, h + 1, z, hashInt(x, 0, z, seed + 33));
            case 1 -> giantFlower(w, x, h + 1, z, hashInt(x, 1, z, seed + 34));
            case 2 -> log(w, x, h + 1, z, hashInt(x, 2, z, seed + 35));
            case 3 -> spikes(w, x, h + 1, z, hashInt(x, 3, z, seed + 36));
            default -> {
            }
        }
    }

    // The preview's woods (for the parity check).
    public static void woods(Vox w, int half, int seed, IntBinaryOperator ground, BiPredicate<Integer, Integer> keepOut) {
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                ground0(w, x, z, ground.applyAsInt(x, z), seed);
            }
        }
        for (int gx = -half; gx < half; gx += 10) {
            for (int gz = -half; gz < half; gz += 10) {
                int[] s = treeSpot(gx, gz, seed);
                int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 10), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 10);
                if (keepOut.test(x, z) || Math.abs(x) > half - 5 || Math.abs(z) > half - 5 || s == null) {
                    continue;
                }
                treeAt(w, x, z, ground.applyAsInt(x, z), seed);
            }
        }
        for (int gx = -half; gx < half; gx += 24) {
            for (int gz = -half; gz < half; gz += 24) {
                int[] o = objectSpot(gx, gz, seed);
                int x = o[0], z = o[1];
                if (keepOut.test(x, z) || Math.abs(x) > half - 8 || Math.abs(z) > half - 8) {
                    continue;
                }
                int h = topOf(w, x, z, ground);
                if (w.get(x, h + 1, z) != 0) {
                    continue;
                }
                objectAt(w, o, h, seed);
            }
        }
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                int h = topOf(w, x, z, ground);
                plantAt(w, x, h, z, seed, false);
            }
        }
    }
}
