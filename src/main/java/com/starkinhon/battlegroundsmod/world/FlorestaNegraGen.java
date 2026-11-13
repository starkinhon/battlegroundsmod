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
import java.util.function.IntBinaryOperator;

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;
import com.starkinhon.battlegroundsmod.world.TemploGen.Vox;

// The Floresta Negra (Stark 2026-11-12, "só maldições, chefe ao entrar"; port released 2026-11-13): the line-by-line port
// of the approved preview's generator (/mnt/project-files/forja/jujutsu-floresta-negra/gen.js), the same draws in the
// same order, so the game makes what the preview showed. Change one, change the other.
// A completely dark forest: black trees with thick bent trunks, branches that open and droop, a closed heavy crown (the
// ground stays dark even by day), roots coming out of the black soil, hanging vines. On the ground: pools of black
// water, purple fungi that glow faintly, bones, cracked stone statues, rotten shrines and broken torii with the torn
// talismans of those who tried to seal the place and failed. On the trunks now and then a pair of red eyes open in the
// dark. In the middle, the lair: a clearing of cracked black stone with purple veins of cursed energy, ringed by broken
// torii, and the Árvore Matriz, a giant living tree with an open mouth of teeth and dozens of eyes.
public final class FlorestaNegraGen {
    // The preview's NAMES, in its order.
    public static final byte AIR = 0, STONE = 1, DIRT = 2, SOIL = 3, DEAD_GRASS = 4, MUD = 5, BLACK_WATER = 6, ROOT = 7, BARK = 8,
            BARK_DARK = 9, LEAF = 10, LEAF2 = 11, LEAF_VIOLET = 12, VINE = 13, FUNGUS = 14, EYE = 15, TALISMAN = 16, TORII_ROT = 17,
            TORII_TOP_ROT = 18, STATUE = 19, STATUE_MOSS = 20, BONE = 21, CRACKED = 22, VEIN = 23, FLESH = 24, FLESH_DARK = 25,
            TOOTH = 26, ROT_WOOD = 27, ROOF_ROT = 28, ASH = 29, PUPIL = 30;

    // The lair's clearing radius, and how far the woods keep away from its center.
    public static final int LAIR_R = 34, LAIR_KEEP = 44;
    // How far a black tree's blocks reach from its spot: the trunk's lean (up to about 12 for the tallest), a branch (up
    // to 11.2) and its crown (6 across), the vines below; the roots reach 12.
    public static final int TREE_REACH = 32;
    // How far the lair reaches from its center: the clearing's floor and leaf trimming (R + 12), the dead trees' branches.
    public static final int LAIR_REACH = LAIR_R + 16;

    private FlorestaNegraGen() {
    }

    private static double[] p3(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    // ---------------------------------------------------------------- black tree

    // A thick trunk that rises bent (2 or 3 turns), buttress roots at the foot going into the ground, 4 to 7 branches
    // from the top that rise a little and droop; at each tip a heavy black mass of leaves, flattened, that joins its
    // neighbors in a closed crown; vines falling from under the crown. Some have eyes on the trunk (a pair of red eyes,
    // sometimes three) and torn talismans stuck on. Returns {H, size, dead}.
    public static int[] blackTree(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int size = rng.next() < 0.22 ? 2 : 1;
        int H = size == 2 ? rng.nextInt(22, 30) : rng.nextInt(15, 22);
        double R = size == 2 ? rng.range(1.8, 2.4) : rng.range(0.9, 1.4);
        // the trunk in 3 stretches that keep bending
        double[] p = p3(ox + 0.5, oy - 1, oz + 0.5);
        double lean = rng.range(0, Math.PI * 2);
        int parts = 3;
        for (int i = 0; i < parts; i++) {
            lean += rng.range(-1.2, 1.2);
            double lk = rng.range(0.15, 0.5) * (i + 1) / parts;
            double[] q = p3(p[0] + cos(lean) * lk * H / parts * 1.2, p[1] + (double) H / parts, p[2] + sin(lean) * lk * H / parts * 1.2);
            tube(w, p, q, R * (1 - i * 0.18), R * (1 - (i + 1) * 0.18), i == 0 ? BARK_DARK : BARK);
            p = q;
        }
        double[] top = p;
        boolean dead = rng.next() < 0.12;
        // buttress roots: thick roots from the foot that sink into the ground
        int nr = rng.nextInt(4, 6);
        for (int i = 0; i < nr; i++) {
            double a = (double) i / nr * Math.PI * 2 + rng.range(-0.3, 0.3), l = rng.range(3, 6) * size;
            tube(w, p3(ox + 0.5, oy + 1.5, oz + 0.5), p3(ox + 0.5 + cos(a) * l, oy - 1, oz + 0.5 + sin(a) * l), R * 0.75, 0.5, ROOT);
        }
        // branches and crown
        int nb = rng.nextInt(4, 7);
        List<double[]> tips = new ArrayList<>();
        for (int i = 0; i < nb; i++) {
            double a = rng.range(0, Math.PI * 2), l = rng.range(4, 8) * (size == 2 ? 1.4 : 1);
            double[] from = p3(top[0], top[1] - rng.range(0, H * 0.3), top[2]);
            double[] mid = p3(from[0] + cos(a) * l * 0.6, from[1] + rng.range(1, 3), from[2] + sin(a) * l * 0.6);
            double[] end = p3(from[0] + cos(a) * l, mid[1] - rng.range(0.5, 2.5), from[2] + sin(a) * l);
            tube(w, from, mid, R * 0.55, R * 0.35, BARK);
            tube(w, mid, end, R * 0.35, 0.4, BARK);
            tips.add(end);
            tips.add(mid);
        }
        tips.add(p3(top[0], top[1] + 1, top[2]));
        // the dead one keeps only its dry branches, a hole in the crown where a thread of light comes in
        if (!dead) {
            for (double[] tp : tips) {
                double tx = tp[0], ty = tp[1], tz = tp[2];
                double rx = rng.range(3.2, 5.0) * (size == 2 ? 1.2 : 1), ry = rng.range(1.6, 2.6);
                for (int dx = -6; dx <= 6; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        for (int dz = -6; dz <= 6; dz++) {
                            int px = floor(tx) + dx, py = floor(ty) + dy, pz = floor(tz) + dz;
                            double d = hypot((px - tx) / rx, (py - ty) / ry, (pz - tz) / rx) + (hash3(px, py, pz, seed + 7) - 0.5) * 0.5;
                            if (d > 1) {
                                continue;
                            }
                            double h = hash3(px, py, pz, seed + 8);
                            w.set(px, py, pz, h < 0.1 ? LEAF_VIOLET : h < 0.45 ? LEAF2 : LEAF);
                            // a vine falling from the mass' underside
                            if (py < ty - ry * 0.4 && hash3(px, 3, pz, seed + 9) < 0.025) {
                                int len = 2 + floor(hash3(px, 4, pz, seed + 9) * 8);
                                for (int k = 1; k <= len; k++) {
                                    w.set(px, py - k, pz, VINE);
                                }
                            }
                        }
                    }
                }
            }
        }
        // eyes on the trunk: pairs side by side (sometimes three) 2 to 4 up, looking out
        if (rng.next() < 0.16) {
            double a = rng.range(0, Math.PI * 2);
            int ey = oy + rng.nextInt(2, 4);
            int ex = floor(ox + 0.5 + cos(a) * (R + 0.6)), ez = floor(oz + 0.5 + sin(a) * (R + 0.6));
            int[] across = Math.abs(cos(a)) > Math.abs(sin(a)) ? new int[] {0, 1} : new int[] {1, 0};
            w.force(ex, ey, ez, EYE);
            w.force(ex + across[0] * 2, ey, ez + across[1] * 2, EYE);
            if (rng.next() < 0.3) {
                w.force(ex + across[0], ey + 2, ez + across[1], EYE);
            }
        }
        if (rng.next() < 0.18) {
            double a = rng.range(0, Math.PI * 2);
            for (int k = 0; k < rng.nextInt(1, 3); k++) {
                w.force(floor(ox + 0.5 + cos(a + k * 0.5) * (R + 0.6)), oy + 1 + k, floor(oz + 0.5 + sin(a + k * 0.5) * (R + 0.6)), TALISMAN);
            }
        }
        return new int[] {H, size, dead ? 1 : 0};
    }

    // ---------------------------------------------------------------- things on the ground

    private static void put(Vox w, int cx, int cz, char axis, int u, int yy, byte t) {
        if (axis == 'x') {
            w.force(cx + u, yy, cz, t);
        } else {
            w.force(cx, yy, cz + u, t);
        }
    }

    // A rotten, broken torii: the posts crooked (one sometimes fallen), the top beam split, talismans hanging.
    public static void brokenTorii(Vox w, int cx, int y, int cz, char axis, int seed) {
        Rng rng = new Rng(seed);
        int half = 3, h = rng.nextInt(6, 8);
        int fallen = rng.next() < 0.35 ? (rng.next() < 0.5 ? -1 : 1) : 0;
        for (int u : new int[] {-half, half}) {
            if (fallen == Integer.signum(u)) {
                // the fallen post on the ground, pointing out
                for (int k = 0; k < h - 1; k++) {
                    int ax = axis == 'x' ? cx + u + Integer.signum(u) * k : cx + 1;
                    int az = axis == 'x' ? cz + 1 : cz + u + Integer.signum(u) * k;
                    w.force(ax, y, az, TORII_ROT);
                }
                put(w, cx, cz, axis, u, y, TORII_ROT);
                continue;
            }
            int hh = h - (rng.next() < 0.3 ? rng.nextInt(1, 3) : 0);
            for (int k = 0; k < hh; k++) {
                put(w, cx, cz, axis, u, y + k, k < 2 && rng.next() < 0.5 ? ROT_WOOD : TORII_ROT);
            }
        }
        if (fallen == 0) {
            int brk = rng.nextInt(-1, 1);
            for (int u = -half - 2; u <= half + 2; u++) {
                if (u == brk) {
                    continue;
                }
                int sag = u > brk ? 1 : 0;
                put(w, cx, cz, axis, u, y + h - sag, TORII_TOP_ROT);
                if (Math.abs(u) <= half && hash3(u, y, cx + cz, seed) < 0.35) {
                    put(w, cx, cz, axis, u, y + h - 1 - sag, TALISMAN);
                }
            }
        } else {
            // the top beam on the ground, lying across
            for (int u = -half - 1; u <= half + 1; u++) {
                put(w, cx, cz, axis, u, y, TORII_TOP_ROT);
            }
        }
    }

    // A cracked stone statue (jizo): foot, body, head, moss; some headless (the head on the ground beside).
    public static void statue(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        w.force(x, y, z, STATUE);
        w.force(x, y + 1, z, rng.next() < 0.5 ? STATUE_MOSS : STATUE);
        if (rng.next() < 0.7) {
            w.force(x, y + 2, z, STATUE);
        } else {
            w.force(x + 1, y, z + (rng.next() < 0.5 ? 1 : -1), STATUE_MOSS);
        }
        if (rng.next() < 0.5) {
            w.force(x, y + 1, z + 1, TALISMAN);
        }
    }

    private static final int[][] CORNERS = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};

    // A rotten shrine: a little house of rotten wood with its roof sunk in, talismans in front.
    public static void rotShrine(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                w.force(x + a, y, z + b, CRACKED);
            }
        }
        for (int[] c : CORNERS) {
            for (int k = 1; k <= (rng.next() < 0.25 ? 2 : 4); k++) {
                w.force(x + c[0], y + k, z + c[1], ROT_WOOD);
            }
        }
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                if (Math.abs(a) == 2 && Math.abs(b) < 2 && rng.next() < 0.6) {
                    for (int k = 1; k <= 3; k++) {
                        w.force(x + a, y + k, z + b, ROT_WOOD);
                    }
                }
                int sag = Math.abs(a) <= 1 && Math.abs(b) <= 1 ? 1 : 0;
                if (hash3(x + a, y, z + b, seed) < 0.8) {
                    w.force(x + a, y + 5 - sag, z + b, ROOF_ROT);
                }
            }
        }
        for (int a = -1; a <= 1; a++) {
            if (rng.next() < 0.7) {
                w.force(x + a, y + 2, z - 2, TALISMAN);
            }
        }
        w.force(x, y + 1, z, BONE);
    }

    // Bones: ribs and a skull of some beast (or person) half buried.
    public static void bones(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int n = rng.nextInt(3, 7);
        for (int i = 0; i < n; i++) {
            int dx = rng.nextInt(-2, 2);
            int dy = rng.next() < 0.3 ? 1 : 0;
            w.force(x + dx, y + dy, z + rng.nextInt(-2, 2), BONE);
        }
    }

    // ---------------------------------------------------------------- lair

    public record Lair(int cx, int cz, int y, int[] mouth) {
    }

    private static boolean isLeaf(int t) {
        return t == LEAF || t == LEAF2 || t == LEAF_VIOLET || t == VINE;
    }

    // Whether the lair at (cx, cz) trims the forest's crown over the column: the clearing stays open to the sky.
    public static boolean trimmed(int x, int z, int cx, int cz, int seed) {
        return hypot(x - cx, z - cz) <= LAIR_R + 2 + (valueNoise(x, z, seed + 4, 8) - 0.5) * 8;
    }

    // The boss curse's lair: a clearing of radius ~34 of cracked black stone with purple veins, ringed by 8 broken torii
    // facing the center and a ring of dead trees bent inward; in the middle the Árvore Matriz: a trunk of radius 7 that
    // twists up 56 blocks, huge roots tearing the clearing, a vertical mouth of flesh with teeth in front, dozens of eyes
    // on the bark, dry leafless branches spread like fingers and, under it, the bones of those who came close. trim: the
    // forest's crown over the clearing is cleared in w (the preview's lair runs after its forest; the game's feature
    // trims its own chunk with trimmed()).
    public static Lair lair(Vox w, int cx, int cz, IntBinaryOperator ground, int seed, boolean trim) {
        Rng rng = new Rng(seed);
        int R = LAIR_R;
        int gy = ground.applyAsInt(cx, cz);
        // the clearing's floor: levels gently toward the center and turns to cracked black stone with veins
        for (int x = cx - R - 4; x <= cx + R + 4; x++) {
            for (int z = cz - R - 4; z <= cz + R + 4; z++) {
                double d = hypot(x - cx, z - cz) + (valueNoise(x, z, seed + 3, 9) - 0.5) * 6;
                if (d > R) {
                    continue;
                }
                int g = ground.applyAsInt(x, z);
                double t = Math.min(1, Math.max(0, (R - d) / 12));
                int y = round(g + (gy - g) * t);
                for (int yy = y + 1; yy <= Math.max(g, y) + 2; yy++) {
                    w.force(x, yy, z, AIR);
                }
                for (int yy = Math.min(g, y) - 2; yy <= y; yy++) {
                    w.force(x, yy, z, yy == y ? CRACKED : STONE);
                }
                // veins: thin lines of a noise (where it crosses 0.5), stronger near the center
                double v = Math.abs(valueNoise(x, z, seed + 5, 7) - 0.5) + Math.abs(valueNoise(x, z, seed + 6, 17) - 0.5) * 0.6;
                if (v < 0.035 + 0.03 * (1 - d / R)) {
                    w.force(x, y, z, VEIN);
                } else if (hash3(x, y, z, seed + 7) < 0.06) {
                    w.force(x, y, z, ASH);
                }
            }
        }
        // the forest's crown leaning over the clearing is trimmed: the lair stays open to the sky
        if (trim) {
            for (int x = cx - R - 12; x <= cx + R + 12; x++) {
                for (int z = cz - R - 12; z <= cz + R + 12; z++) {
                    if (!trimmed(x, z, cx, cz, seed)) {
                        continue;
                    }
                    for (int y = gy - 10; y < gy + 70; y++) {
                        if (isLeaf(w.get(x, y, z))) {
                            w.force(x, y, z, AIR);
                        }
                    }
                }
            }
        }
        // the 8 broken torii around, facing the center
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2 + rng.range(-0.15, 0.15);
            int x = round(cx + cos(a) * (R - 5)), z = round(cz + sin(a) * (R - 5));
            char axis = Math.abs(cos(a)) > Math.abs(sin(a)) ? 'z' : 'x';
            brokenTorii(w, x, groundOf(w, x, z, ground) + 1, z, axis, hashInt(i, 1, 2, seed));
        }
        // the ring of dead trees: only trunk and dry branches, bent toward the center
        for (int i = 0; i < 14; i++) {
            double a = i / 14.0 * Math.PI * 2 + rng.range(-0.1, 0.1), d = R + rng.range(-3, 1);
            int x = round(cx + cos(a) * d), z = round(cz + sin(a) * d);
            int y = groundOf(w, x, z, ground) + 1, h = rng.nextInt(10, 18);
            double[] lean = {-(cos(a)) * h * 0.35, h, -(sin(a)) * h * 0.35};
            tube(w, p3(x + 0.5, y - 1, z + 0.5), p3(x + 0.5 + lean[0], y + lean[1], z + 0.5 + lean[2]), 1.1, 0.6, BARK_DARK);
            for (int k = 0; k < 3; k++) {
                double b = rng.range(0, Math.PI * 2), l = rng.range(3, 6);
                double[] from = p3(x + 0.5 + lean[0] * 0.8, y + h * 0.8, z + 0.5 + lean[2] * 0.8);
                tube(w, from, p3(from[0] + cos(b) * l, from[1] + rng.range(-1, 3), from[2] + sin(b) * l), 0.5, 0.3, BARK_DARK);
            }
        }
        // the Árvore Matriz
        int TR = 7, TH = 56;
        double ma = rng.range(0, Math.PI * 2), mx = cos(ma), mz = sin(ma);
        double[] p = p3(cx + 0.5, gy - 2, cz + 0.5);
        double ang = rng.range(0, Math.PI * 2);
        List<double[]> spine = new ArrayList<>();
        spine.add(p);
        for (int i = 0; i < 5; i++) {
            ang += rng.range(0.6, 1.1);
            double[] q = p3(p[0] + cos(ang) * 2.6, p[1] + TH / 5.0, p[2] + sin(ang) * 2.6);
            tube(w, p, q, TR * (1 - i * 0.12), TR * (1 - (i + 1) * 0.12), i < 2 ? BARK_DARK : BARK);
            p = q;
            spine.add(q);
        }
        // giant roots tearing the clearing
        for (int i = 0; i < 9; i++) {
            // it starts beside the mouth (half a step), so no root closes the way to it
            double a = ma + (i + 0.5) / 9 * Math.PI * 2 + rng.range(-0.2, 0.2), l = rng.range(14, 26);
            double[] mid = p3(cx + 0.5 + cos(a) * l * 0.5, gy + rng.range(1, 3), cz + 0.5 + sin(a) * l * 0.5);
            tube(w, p3(cx + 0.5, gy + 3, cz + 0.5), mid, 3.2, 2, ROOT);
            tube(w, mid, p3(cx + 0.5 + cos(a) * l, gy - 1, cz + 0.5 + sin(a) * l), 2, 0.8, ROOT);
        }
        // dry branches spread like fingers up high
        for (int i = 0; i < 7; i++) {
            double a = i / 7.0 * Math.PI * 2 + rng.range(-0.3, 0.3), l = rng.range(12, 20);
            double[] from = spine.get(rng.nextInt(3, 5));
            double[] mid = p3(from[0] + cos(a) * l * 0.55, from[1] + rng.range(3, 7), from[2] + sin(a) * l * 0.55);
            double[] end = p3(from[0] + cos(a) * l, mid[1] + rng.range(-1, 4), from[2] + sin(a) * l);
            tube(w, from, mid, 1.8, 1.0, BARK);
            tube(w, mid, end, 1.0, 0.35, BARK);
            // claws: the tip splits in 2 or 3
            for (int k = 0; k < rng.nextInt(2, 3); k++) {
                double b = a + rng.range(-0.8, 0.8);
                tube(w, end, p3(end[0] + cos(b) * 4, end[1] + rng.range(-2, 2), end[2] + sin(b) * 4), 0.45, 0.3, BARK);
            }
        }
        // the mouth: a vertical slit in the trunk's front, 3 to 15 above the ground. A lip of flesh around it, saw teeth
        // alternating from both sides (one here, one there), fangs at top and bottom, and inside the throat darkening.
        for (int yy = gy + 2; yy <= gy + 16; yy++) {
            double u = (yy - (gy + 9)) / 6.5, open = Math.sqrt(Math.max(0, 1 - u * u)) * 3.6;
            double lip = open + 1.1;
            int sl = (int) Math.ceil(lip);
            for (int d = TR - 5; d <= TR + 1; d++) {
                for (int s = -sl; s <= sl; s++) {
                    int x = floor(cx + 0.5 + mx * d - mz * s), z = floor(cz + 0.5 + mz * d + mx * s);
                    int as = Math.abs(s);
                    if (as > lip) {
                        continue;
                    }
                    if (as > open) {
                        if (d >= TR - 1) {
                            w.force(x, yy, z, FLESH);
                        }
                        continue;
                    }
                    int side = s > 0 ? 1 : 0;
                    boolean tooth = d >= TR - 1 && as > open - 1.3 && (yy + side) % 2 == 0;
                    boolean fang = d >= TR - 1 && Math.abs(u) > 0.78 && as < open - 0.2 && (s + 8) % 2 == 0;
                    if (tooth || fang) {
                        w.force(x, yy, z, TOOTH);
                    } else if (d >= TR - 1) {
                        w.force(x, yy, z, AIR);
                    } else {
                        w.force(x, yy, z, d <= TR - 4 ? FLESH_DARK : FLESH);
                    }
                }
            }
        }
        // eyes on the bark: slits of 3 (red white, black pupil, red) flush with the trunk, with a lid of bark standing
        // out above; most near the mouth, looking at whoever comes, some up the trunk
        for (int i = 0; i < 24; i++) {
            double t = rng.range(0.04, 0.7);
            double[] sp = spine.get(Math.min(4, floor(t * 5)));
            double a = ma + rng.range(-1.5, 1.5) * (i < 14 ? 1 : 2);
            double rr = TR * (1 - t * 0.6) - 0.2;
            int y = floor(gy + t * TH);
            double ca = cos(a), sa = sin(a);
            // finds the real bark: from outside in until it meets the trunk (the trunk twists, the radius isn't enough)
            int out = 5;
            while (out > -4) {
                int[] q = eyeAt(sp, ca, sa, rr, 0, out);
                if (w.get(q[0], y, q[1]) != 0) {
                    break;
                }
                out--;
            }
            int[] c = eyeAt(sp, ca, sa, rr, 0, out);
            int here = w.get(c[0], y, c[1]);
            if (here != BARK && here != BARK_DARK) {
                continue;
            }
            for (int k = -1; k <= 1; k++) {
                int[] q = eyeAt(sp, ca, sa, rr, k, out);
                w.force(q[0], y, q[1], k == 0 ? PUPIL : EYE);
            }
            for (int k = -1; k <= 1; k++) {
                int[] q = eyeAt(sp, ca, sa, rr, k, 1 + out);
                w.force(q[0], y + 1, q[1], BARK_DARK);
            }
        }
        // bones around the foot
        for (int i = 0; i < 18; i++) {
            double a = rng.range(0, Math.PI * 2), d = rng.range(TR + 2, TR + 14);
            int x = round(cx + cos(a) * d), z = round(cz + sin(a) * d);
            bones(w, x, groundOf(w, x, z, ground) + 1, z, hashInt(i, 5, 6, seed));
        }
        return new Lair(cx, cz, gy, new int[] {cx + round(mx * (TR + 6)), gy + 1, cz + round(mz * (TR + 6))});
    }

    private static int[] eyeAt(double[] sp, double ca, double sa, double rr, int k, int out) {
        return new int[] {floor(sp[0] + 0.5 + ca * (rr + out) - sa * k), floor(sp[2] + 0.5 + sa * (rr + out) + ca * k)};
    }

    // The ground's height as the lair left it (the highest top solid up to 6 above the natural one), so the props sit on
    // top.
    public static int groundOf(Vox w, int x, int z, IntBinaryOperator ground) {
        int g = ground.applyAsInt(x, z);
        for (int y = g + 6; y >= g - 6; y--) {
            int t = w.get(x, y, z);
            if (t == CRACKED || t == VEIN || t == ASH || t == STONE || t == SOIL || t == DEAD_GRASS || t == MUD) {
                return y;
            }
        }
        return g;
    }

    // ---------------------------------------------------------------- ground and woods

    public static int heightAt(int x, int z, int seed) {
        double big = valueNoise(x, z, seed, 80), mid = valueNoise(x, z, seed + 1, 28), sm = valueNoise(x, z, seed + 2, 9);
        return floor(10 + (big - 0.5) * 22 + (mid - 0.5) * 10 + (sm - 0.5) * 3);
    }

    // The ground's top: black soil, dead grass in patches, mud near the pools, small roots out of the soil; pools of
    // black water in the hollows, purple fungi. (The preview's pool also needs h below its own terrain + 1, always true.)
    public static byte groundTop(int x, int z, int seed) {
        boolean pool = valueNoise(x, z, seed + 40, 14) > 0.7;
        if (pool && valueNoise(x, z, seed + 40, 14) > 0.76) {
            return BLACK_WATER;
        }
        return pool ? MUD : valueNoise(x, z, seed + 41, 6) > 0.58 ? DEAD_GRASS : hash3(x, 0, z, seed + 42) < 0.08 ? ROOT : SOIL;
    }

    public static void ground0(Vox w, int x, int z, int h, int seed) {
        boolean pool = valueNoise(x, z, seed + 40, 14) > 0.7 && h < heightAt(x, z, seed) + 1;
        byte top = pool ? MUD : valueNoise(x, z, seed + 41, 6) > 0.58 ? DEAD_GRASS : hash3(x, 0, z, seed + 42) < 0.08 ? ROOT : SOIL;
        w.force(x, h, z, top);
        for (int k = 1; k <= 3; k++) {
            w.force(x, h - k, z, k <= 2 ? DIRT : STONE);
        }
        if (pool && valueNoise(x, z, seed + 40, 14) > 0.76) {
            w.force(x, h, z, BLACK_WATER);
        }
    }

    // A tree's spot on the grid of 6 (gx, gz on the grid): its column, or null.
    public static int[] treeSpot(int gx, int gz, int seed) {
        int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 6), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 6);
        if (hash3(x, 3, z, seed + 20) > 0.8) {
            return null;
        }
        return new int[] {x, z};
    }

    public static void tree(Vox w, int x, int h, int z, int seed) {
        blackTree(w, x, h + 1, z, hashInt(x, h, z, seed + 23));
    }

    // What the loose ground holds at a column: a fungus, bones (their seed), or nothing.
    public static final int SCATTER_NONE = 0, SCATTER_FUNGUS = 1, SCATTER_BONES = 2;

    public static int scatterAt(int x, int z, int seed) {
        double r = hash3(x, 9, z, seed + 30);
        return r < 0.025 ? SCATTER_FUNGUS : r < 0.027 ? SCATTER_BONES : SCATTER_NONE;
    }

    // A landmark on the grid of 40 (gx, gz on the grid): {x, z, kind} with kind 0 shrine, 1 torii, 2 statues, or null.
    public static int[] landmark(int gx, int gz, int seed) {
        double r = hash3(gx, 7, gz, seed + 32);
        int x = gx + 8 + floor(hash3(gx, 8, gz, seed + 32) * 24), z = gz + 8 + floor(hash3(gx, 9, gz, seed + 32) * 24);
        int kind = r < 0.3 ? 0 : r < 0.55 ? 1 : r < 0.8 ? 2 : -1;
        return new int[] {x, z, kind};
    }

    // The landmark's clearing (leaves and vines around it taken out), then the landmark itself.
    public static void placeLandmark(Vox w, int[] lm, IntBinaryOperator ground, int seed) {
        int x = lm[0], z = lm[1];
        int h = ground.applyAsInt(x, z);
        for (int a = -4; a <= 4; a++) {
            for (int b = -4; b <= 4; b++) {
                for (int y = h + 1; y < h + 9; y++) {
                    if (isLeaf(w.get(x + a, y, z + b))) {
                        w.force(x + a, y, z + b, AIR);
                    }
                }
            }
        }
        if (lm[2] == 0) {
            rotShrine(w, x, h, z, hashInt(x, 0, z, seed + 33));
        } else if (lm[2] == 1) {
            brokenTorii(w, x, h + 1, z, hash3(x, 1, z, seed) < 0.5 ? 'x' : 'z', hashInt(x, 2, z, seed + 34));
        } else if (lm[2] == 2) {
            for (int i = 0; i < 3; i++) {
                statue(w, x + i * 2 - 2, ground.applyAsInt(x + i * 2 - 2, z) + 1, z, hashInt(x, i, z, seed + 35));
            }
        }
    }

    // The preview's forest (for the parity check): the ground, the trees on the grid of 6, the scatter, the landmarks.
    public static void forest(Vox w, int half, int seed, IntBinaryOperator ground, java.util.function.BiPredicate<Integer, Integer> keepOut) {
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                ground0(w, x, z, ground.applyAsInt(x, z), seed);
            }
        }
        for (int gx = -half; gx < half; gx += 6) {
            for (int gz = -half; gz < half; gz += 6) {
                int[] s = treeSpot(gx, gz, seed);
                if (s == null || keepOut.test(s[0], s[1])) {
                    continue;
                }
                int h = ground.applyAsInt(s[0], s[1]);
                if (w.get(s[0], h, s[1]) == BLACK_WATER) {
                    continue;
                }
                tree(w, s[0], h, s[1], seed);
            }
        }
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (keepOut.test(x, z)) {
                    continue;
                }
                int h = ground.applyAsInt(x, z);
                if (w.get(x, h + 1, z) != 0 || w.get(x, h, z) == BLACK_WATER) {
                    continue;
                }
                int sc = scatterAt(x, z, seed);
                if (sc == SCATTER_FUNGUS) {
                    w.force(x, h + 1, z, FUNGUS);
                } else if (sc == SCATTER_BONES) {
                    bones(w, x, h + 1, z, hashInt(x, 1, z, seed + 31));
                }
            }
        }
        for (int gx = -half; gx < half; gx += 40) {
            for (int gz = -half; gz < half; gz += 40) {
                int[] lm = landmark(gx, gz, seed);
                if (keepOut.test(lm[0], lm[1]) || Math.abs(lm[0]) > half - 6 || Math.abs(lm[1]) > half - 6) {
                    continue;
                }
                placeLandmark(w, lm, ground, seed);
            }
        }
    }
}
