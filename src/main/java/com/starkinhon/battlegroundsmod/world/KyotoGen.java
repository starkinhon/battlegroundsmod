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

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;
import com.starkinhon.battlegroundsmod.world.TemploGen.Vox;

// The Outono de Kyoto (Stark 2026-11-12; port released 2026-11-13): line-by-line port of the approved preview's
// generator (forja/jujutsu-kyoto/gen.js), the same draws and the same hashes from the world's coordinates. Hills
// covered in Japanese maple (momiji) red, orange and gold, yellow ginkgos, tall closed bamboo groves with a trail
// inside, crooked pines with cloud-shaped crowns, the ground covered in fallen leaves, streams with stones, stone
// lanterns. The centerpiece is a pond garden: the mirror pond with pine-and-stone islands, a wooden bridge to an island,
// a tea house and the Golden Pavilion on the shore (three floors, the upper two in gold leaf, dark bark roofs with the
// eaves turned up and the phoenix on top).
public final class KyotoGen {
    // The preview's NAMES, in its order.
    public static final byte AIR = 0, STONE = 1, DIRT = 2, GRASS = 3, MOSS = 4, LITTER_R = 5, LITTER_O = 6, LITTER_Y = 7, BARK = 8,
            BARK_DARK = 9, MAPLE_R = 10, MAPLE_R2 = 11, MAPLE_O = 12, MAPLE_Y = 13, GINKGO = 14, PINE = 15, BAMBOO = 16,
            BAMBOO_LEAF = 17, WATER = 18, GRAVEL = 19, PATH = 20, LANTERN = 21, LANTERN_LIT = 22, WOOD = 23, WOOD_DARK = 24,
            SHOJI = 25, GOLD = 26, GOLD_DARK = 27, ROOF = 28, ROOF_EDGE = 29, PLASTER = 30, BRIDGE = 31, ROCK = 32,
            ROCK_MOSS = 33, TATAMI = 34, FERN = 35;

    // How far a tree's blocks reach from its column: a pine's lean (4), branch (4.5) and pad (4).
    public static final int TREE_REACH = 14;
    // The pond garden: the leveled ground reaches R + 20 (in x; z stretched by 1.25), the tea house R + 8 + 4, the
    // maples R + 15 and their crowns.
    public static final int GARDEN_R = 34;
    public static final int GARDEN_REACH = 66;

    private static final byte[][] MAPLE_TONES = {{MAPLE_R, MAPLE_R2, MAPLE_O}, {MAPLE_O, MAPLE_R, MAPLE_Y}, {MAPLE_Y, MAPLE_O, MAPLE_Y},
            {MAPLE_R2, MAPLE_R, MAPLE_R}};
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private KyotoGen() {
    }

    // ---------------------------------------------------------------- trees

    // Japanese maple: a short trunk opening into 3 to 5 leaning branches, a wide dome crown of several masses in one main
    // color (red, orange or gold) with touches of the next; fallen leaves on the ground around, the same color.
    public static void maple(Vox w, int x, int y, int z, int seed, IntBinaryOperator ground) {
        Rng rng = new Rng(seed);
        byte[] tone = MAPLE_TONES[rng.nextInt(0, 3)];
        int T = rng.nextInt(2, 4), H = rng.nextInt(7, 11);
        for (int k = -1; k < T; k++) {
            w.force(x, y + k, z, BARK);
        }
        int n = rng.nextInt(3, 5);
        List<double[]> tips = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = (double) i / n * Math.PI * 2 + rng.range(-0.4, 0.4), l = rng.range(2.5, 4.5);
            double ex = x + 0.5 + cos(a) * l;
            double ey = y + T + rng.range(H * 0.4, H * 0.7);
            double[] end = {ex, ey, z + 0.5 + sin(a) * l};
            tube(w, new double[] {x + 0.5, y + T - 0.5, z + 0.5}, end, 0.6, 0.35, BARK);
            tips.add(end);
        }
        tips.add(new double[] {x + 0.5, y + H, z + 0.5});
        for (double[] tp : tips) {
            double tx = tp[0], ty = tp[1], tz = tp[2];
            double rx = rng.range(3, 4.5), ry = rng.range(2, 2.8);
            for (int a = -5; a <= 5; a++) {
                for (int b = -3; b <= 3; b++) {
                    for (int c = -5; c <= 5; c++) {
                        int px = floor(tx) + a, py = floor(ty) + b, pz = floor(tz) + c;
                        double d = hypot((px + 0.5 - tx) / rx, (py + 0.5 - ty) / ry, (pz + 0.5 - tz) / rx) + (hash3(px, py, pz, seed) - 0.5) * 0.4;
                        if (d > 1) {
                            continue;
                        }
                        double h = hash3(px, py, pz, seed + 1);
                        w.set(px, py, pz, h < 0.6 ? tone[0] : h < 0.85 ? tone[1] : tone[2]);
                    }
                }
            }
        }
        // the carpet of fallen leaves
        byte lit = tone[0] == MAPLE_Y ? LITTER_Y : tone[0] == MAPLE_O ? LITTER_O : LITTER_R;
        for (int a = -6; a <= 6; a++) {
            for (int c = -6; c <= 6; c++) {
                double d = hypot(a, c);
                if (d > 6 || hash3(x + a, 0, z + c, seed + 2) > 0.85 - d * 0.1) {
                    continue;
                }
                int g = ground.applyAsInt(x + a, z + c);
                int t = w.get(x + a, g, z + c);
                if (t == GRASS || t == MOSS) {
                    w.force(x + a, g, z + c, lit);
                }
            }
        }
    }

    // Ginkgo: a tall straight trunk, short branches rising, a bright yellow cone crown; the ground yellow under it.
    public static void ginkgo(Vox w, int x, int y, int z, int seed, IntBinaryOperator ground) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(13, 18);
        for (int k = -1; k < H; k++) {
            w.force(x, y + k, z, BARK_DARK);
        }
        for (int k = 4; k <= H + 2; k++) {
            double r = (double) (H + 2 - k) / (H - 2) * 4.2 + 0.6;
            for (int a = -5; a <= 5; a++) {
                for (int c = -5; c <= 5; c++) {
                    double d = hypot(a, c) + (hash3(x + a, y + k, z + c, seed) - 0.5) * 1.1;
                    if (d > r || (a == 0 && c == 0 && k < H)) {
                        continue;
                    }
                    w.set(x + a, y + k, z + c, GINKGO);
                }
            }
        }
        for (int a = -5; a <= 5; a++) {
            for (int c = -5; c <= 5; c++) {
                if (hypot(a, c) > 5 || hash3(x + a, 1, z + c, seed) > 0.6) {
                    continue;
                }
                int g = ground.applyAsInt(x + a, z + c), t = w.get(x + a, g, z + c);
                if (t == GRASS || t == MOSS) {
                    w.force(x + a, g, z + c, LITTER_Y);
                }
            }
        }
    }

    // Japanese pine: a crooked leaning trunk, nearly level branches, and at each tip a flattened cushion of needles.
    public static void pine(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(6, 10);
        double a = rng.range(0, Math.PI * 2), lean = rng.range(1.5, 4);
        double[] top = {x + 0.5 + cos(a) * lean, y + H, z + 0.5 + sin(a) * lean};
        double[] mid = {x + 0.5 + cos(a) * lean * 0.3, y + H * 0.5, z + 0.5 + sin(a) * lean * 0.3};
        tube(w, new double[] {x + 0.5, y - 1, z + 0.5}, mid, 0.8, 0.6, BARK_DARK);
        tube(w, mid, top, 0.6, 0.4, BARK_DARK);
        List<double[]> pads = new ArrayList<>();
        pads.add(top);
        for (int i = 0; i < rng.nextInt(3, 5); i++) {
            double b = rng.range(0, Math.PI * 2), l = rng.range(2.5, 4.5), hy = y + rng.range(H * 0.45, H * 0.95);
            double[] end = {x + 0.5 + cos(a) * lean * (hy - y) / H + cos(b) * l, hy, z + 0.5 + sin(a) * lean * (hy - y) / H + sin(b) * l};
            tube(w, new double[] {end[0] - cos(b) * l, hy - 0.5, end[2] - sin(b) * l}, end, 0.4, 0.3, BARK_DARK);
            pads.add(end);
        }
        for (double[] p : pads) {
            double px = p[0], py = p[1], pz = p[2];
            double r = rng.range(2, 3.2);
            for (int da = -4; da <= 4; da++) {
                for (int dc = -4; dc <= 4; dc++) {
                    for (int db = 0; db <= 1; db++) {
                        double d = hypot(da, dc) + db * 1.2 + (hash3(floor(px) + da, db, floor(pz) + dc, seed) - 0.5) * 0.6;
                        if (d > r) {
                            continue;
                        }
                        w.set(floor(px) + da, floor(py) + db, floor(pz) + dc, PINE);
                    }
                }
            }
        }
    }

    // Bamboo: a culm of 1, tall (12 to 20); leaves only on the upper third, in tufts hanging outward.
    public static void bamboo(Vox w, int x, int y, int z, int seed) {
        int H = 12 + (hashInt(x, y, z, seed) % 9);
        for (int k = 0; k < H; k++) {
            w.force(x, y + k, z, BAMBOO);
        }
        for (int k = floor(H * 0.6); k <= H; k++) {
            for (int[] s : SIDES) {
                if (hash3(x + s[0], y + k, z + s[1], seed) < 0.38) {
                    w.set(x + s[0], y + k, z + s[1], BAMBOO_LEAF);
                }
            }
        }
        w.set(x, y + H, z, BAMBOO_LEAF);
    }

    // Stone lantern (toro): foot, shaft, the light box (lit at night), a wide hat and the little tip.
    public static void lantern(Vox w, int x, int y, int z) {
        w.force(x, y, z, ROCK);
        w.force(x, y + 1, z, ROCK);
        w.force(x, y + 2, z, LANTERN_LIT);
        w.force(x, y + 3, z, LANTERN);
        for (int[] s : SIDES) {
            w.force(x + s[0], y + 3, z + s[1], LANTERN);
        }
        w.force(x, y + 4, z, LANTERN);
    }

    // ---------------------------------------------------------------- the pond garden

    // A dark bark roof with the eaves turned up: layers that shrink, the lowest edge one block up at the corners.
    public static void roofKyoto(Vox w, int x0, int x1, int z0, int z1, int y, int layers) {
        for (int k = 0; k < layers; k++) {
            for (int x = x0 + k; x <= x1 - k; x++) {
                for (int z = z0 + k; z <= z1 - k; z++) {
                    boolean edge = x == x0 + k || x == x1 - k || z == z0 + k || z == z1 - k;
                    if (!edge && k < layers - 1) {
                        continue;
                    }
                    boolean corner = (x == x0 + k || x == x1 - k) && (z == z0 + k || z == z1 - k);
                    w.force(x, y + k + (corner && k == 0 ? 1 : 0), z, k == 0 ? ROOF_EDGE : ROOF);
                }
            }
        }
    }

    private interface Cell {
        void at(int x, int z, boolean edge);
    }

    private static void rect(int x0, int x1, int z0, int z1, Cell f) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                f.at(x, z, x == x0 || x == x1 || z == z0 || z == z1);
            }
        }
    }

    // The Golden Pavilion: a ground floor of light wood with shoji and an open veranda; the 2nd and 3rd floors in gold
    // leaf (pillars and beams darker); a bark roof on every floor with wide eaves; a golden phoenix on top.
    public static void pavilion(Vox w, int cx, int y, int cz, int seed) {
        // the stone base at the water's edge
        rect(cx - 9, cx + 9, cz - 7, cz + 7, (x, z, e) -> {
            for (int yy = y - 4; yy <= y; yy++) {
                w.force(x, yy, z, ROCK);
            }
            for (int yy = y + 1; yy <= y + 30; yy++) {
                w.force(x, yy, z, AIR);
            }
        });
        // ground floor (natural wood): 15 x 11
        rect(cx - 7, cx + 7, cz - 5, cz + 5, (x, z, e) -> w.force(x, y + 1, z, TATAMI));
        for (int yy = y + 2; yy <= y + 5; yy++) {
            final int fy = yy;
            rect(cx - 7, cx + 7, cz - 5, cz + 5, (x, z, e) -> {
                if (!e) {
                    return;
                }
                boolean pillar = (x - cx) % 3 == 0 || (z - cz) % 3 == 0 && (x == cx - 7 || x == cx + 7);
                boolean front = z == cz + 5;
                w.force(x, fy, z, fy == y + 5 ? WOOD_DARK : pillar ? WOOD : front ? AIR : SHOJI);
            });
        }
        roofKyoto(w, cx - 9, cx + 9, cz - 7, cz + 7, y + 6, 2);
        // 2nd floor (gold): 13 x 9
        rect(cx - 6, cx + 6, cz - 4, cz + 4, (x, z, e) -> w.force(x, y + 7, z, GOLD_DARK));
        for (int yy = y + 8; yy <= y + 11; yy++) {
            final int fy = yy;
            rect(cx - 6, cx + 6, cz - 4, cz + 4, (x, z, e) -> {
                if (!e) {
                    return;
                }
                boolean pillar = (x - cx) % 3 == 0 && (z == cz - 4 || z == cz + 4) || (Math.abs(x - cx) == 6);
                w.force(x, fy, z, fy == y + 11 ? GOLD_DARK : pillar ? GOLD_DARK
                        : (fy == y + 9 || fy == y + 10) && z == cz + 4 && (x - cx) % 3 != 0 ? SHOJI : GOLD);
            });
        }
        // the 2nd floor's veranda with its parapet
        rect(cx - 7, cx + 7, cz - 5, cz + 5, (x, z, e) -> {
            if (e) {
                w.force(x, y + 7, z, GOLD_DARK);
                if (z == cz + 5) {
                    w.force(x, y + 8, z, GOLD_DARK);
                }
            }
        });
        roofKyoto(w, cx - 8, cx + 8, cz - 6, cz + 6, y + 12, 2);
        // 3rd floor (gold, smaller): 7 x 7
        for (int yy = y + 13; yy <= y + 16; yy++) {
            final int fy = yy;
            rect(cx - 3, cx + 3, cz - 3, cz + 3, (x, z, e) -> {
                if (!e) {
                    return;
                }
                w.force(x, fy, z, fy == y + 16 || Math.abs(x - cx) == 3 && Math.abs(z - cz) == 3 ? GOLD_DARK
                        : fy == y + 14 && (x == cx || z == cz) ? GOLD_DARK : GOLD);
            });
        }
        roofKyoto(w, cx - 5, cx + 5, cz - 5, cz + 5, y + 17, 5);
        // the phoenix
        w.force(cx, y + 22, cz, GOLD);
        w.force(cx, y + 23, cz, GOLD);
        w.force(cx + 1, y + 23, cz, GOLD);
        w.force(cx - 1, y + 24, cz, GOLD);
        w.force(cx, y + 24, cz, GOLD_DARK);
    }

    // A small tea house: a raised floor, walls of plaster and shoji, a bark roof.
    public static void teaHouse(Vox w, int cx, int y, int cz) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                w.force(x, y, z, WOOD);
                for (int yy = y + 1; yy <= y + 10; yy++) {
                    w.force(x, yy, z, AIR);
                }
                boolean e = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                if (!e) {
                    w.force(x, y + 1, z, TATAMI);
                    continue;
                }
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    w.force(x, yy, z, Math.abs(x - cx) == 3 && Math.abs(z - cz) == 3 ? WOOD_DARK : z == cz + 3 ? SHOJI : PLASTER);
                }
            }
        }
        roofKyoto(w, cx - 4, cx + 4, cz - 4, cz + 4, y + 4, 4);
    }

    public record Garden(int cx, int cz, int R, int y, int[] pavilion, int[] tea) {
    }

    public static Garden garden(Vox w, int cx, int cz, IntBinaryOperator ground, int seed) {
        Rng rng = new Rng(seed);
        final int R = GARDEN_R;
        int gy = ground.applyAsInt(cx, cz), wy = gy - 1;
        // a pond with a ragged edge, a shallow bottom; islands
        double[][] islands = new double[3][];
        for (int i = 0; i < 3; i++) {
            double a = rng.range(0, Math.PI * 2), d = rng.range(6, 18);
            double ix = cx + cos(a) * d, iz = cz + sin(a) * d;
            islands[i] = new double[] {ix, iz, rng.range(3, 6)};
        }
        BiPredicate<Integer, Integer> inPond = (x, z) -> hypot(x - cx, (z - cz) * 1.25) + (valueNoise(x, z, seed + 1, 9) - 0.5) * 10 < R;
        BiPredicate<Integer, Integer> onIsle = (x, z) -> {
            for (double[] is : islands) {
                if (hypot(x - is[0], z - is[1]) + (hash3(x, 5, z, seed) - 0.5) < is[2]) {
                    return true;
                }
            }
            return false;
        };
        for (int x = cx - R - 20; x <= cx + R + 20; x++) {
            for (int z = cz - R - 20; z <= cz + R + 20; z++) {
                int g = ground.applyAsInt(x, z);
                double d = hypot(x - cx, (z - cz) * 1.25);
                if (d > R + 20) {
                    continue;
                }
                // the garden on one level (leveled near the pond)
                double t = Math.min(1, Math.max(0, (R + 20 - d) / 10));
                int y = round(g + (gy - g) * t);
                for (int yy = Math.min(g, y) - 3; yy <= Math.max(g, y) + 25; yy++) {
                    w.force(x, yy, z, yy < y ? DIRT : AIR);
                }
                if (inPond.test(x, z) && !onIsle.test(x, z)) {
                    int depth = 2 + round(valueNoise(x, z, seed + 2, 7) * 2);
                    w.force(x, wy - depth, z, GRAVEL);
                    for (int yy = wy - depth + 1; yy <= wy; yy++) {
                        w.force(x, yy, z, WATER);
                    }
                    for (int yy = wy + 1; yy <= y; yy++) {
                        w.force(x, yy, z, AIR);
                    }
                    if (hash3(x, 1, z, seed) < 0.006) {
                        w.force(x, wy, z, ROCK_MOSS);
                    }
                } else {
                    w.force(x, y, z, valueNoise(x, z, seed + 3, 5) > 0.55 ? MOSS : GRASS);
                    // the pond's edge with stones
                    if (!onIsle.test(x, z) && (inPond.test(x + 1, z) || inPond.test(x - 1, z) || inPond.test(x, z + 1) || inPond.test(x, z - 1))
                            && hash3(x, 2, z, seed) < 0.4) {
                        w.force(x, y, z, ROCK);
                    }
                }
            }
        }
        // the islands: a pine and a mossy stone
        for (double[] is : islands) {
            int x = round(is[0]), z = round(is[1]);
            pine(w, x, gy + 1, z, hashInt(x, 0, z, seed + 4));
            w.force(x + 2, gy + 1, z + 1, ROCK_MOSS);
            w.force(x + 2, gy + 2, z + 1, ROCK_MOSS);
        }
        // the pavilion on the north shore, facing the pond
        double pa = -Math.PI / 2 + rng.range(-0.3, 0.3);
        int px = round(cx + cos(pa) * (R - 6)), pz = round(cz + sin(pa) * (R - 6) * 0.8);
        pavilion(w, px, gy, pz, seed + 5);
        // a bridge from the south shore to the nearest island
        double[] isle = islands[0];
        for (int i = 1; i < 3; i++) {
            if (islands[i][1] > isle[1]) {
                isle = islands[i];
            }
        }
        int bx = round(isle[0]), bz0 = round(isle[1] + isle[2] - 1);
        int bz1 = bz0;
        while (bz1 < cz + R + 6 && inPond.test(bx, bz1 + 1)) {
            bz1++;
        }
        int span = bz1 - bz0 + 2;
        for (int k = 0; k <= span; k++) {
            int z = bz0 + k, arc = round(sin((double) k / span * Math.PI) * 2);
            for (int dx = -1; dx <= 1; dx++) {
                w.force(bx + dx, gy + 1 + arc, z, BRIDGE);
                for (int yy = gy + 2 + arc; yy <= gy + 4 + arc; yy++) {
                    if (w.get(bx + dx, yy, z) == WATER) {
                        w.force(bx + dx, yy, z, AIR);
                    }
                }
            }
            for (int dx = -2; dx <= 2; dx += 4) {
                if (k % 2 == 0) {
                    w.force(bx + dx, gy + 2 + arc, z, BRIDGE);
                }
            }
        }
        // the tea house on the east shore, lanterns around the pond, maples and a ginkgo in the garden
        double ta = rng.range(-0.3, 0.3);
        int tx = round(cx + cos(ta) * (R + 8)), tz = round(cz + sin(ta) * (R + 8) * 0.8);
        teaHouse(w, tx, gy, tz);
        for (int i = 0; i < 10; i++) {
            double a = (double) i / 10 * Math.PI * 2 + 0.2;
            int lx = round(cx + cos(a) * (R + 3)), lz = round(cz + sin(a) * (R + 3) * 0.8);
            if (w.get(lx, gy + 1, lz) == 0 && w.get(lx, gy, lz) != WATER) {
                lantern(w, lx, gy + 1, lz);
            }
        }
        for (int i = 0; i < 9; i++) {
            double a = rng.range(0, Math.PI * 2), d = rng.range(R + 6, R + 15);
            int x = round(cx + cos(a) * d), z = round(cz + sin(a) * d * 0.8);
            if (hypot(x - px, z - pz) < 14 || hypot(x - tx, z - tz) < 8) {
                continue;
            }
            if (w.get(x, gy + 1, z) != 0) {
                continue;
            }
            maple(w, x, gy + 1, z, hashInt(x, 1, z, seed + 6), (a2, b2) -> gy);
        }
        return new Garden(cx, cz, R, gy, new int[] {px, gy, pz}, new int[] {tx, tz});
    }

    public static boolean gardenKeep(int x, int z, int cx, int cz) {
        return hypot(x - cx, (z - cz) * 1.25) < GARDEN_R + 20;
    }

    // ---------------------------------------------------------------- terrain and the hills, piece by piece

    public static int heightAt(int x, int z, int seed) {
        double big = valueNoise(x, z, seed, 70), mid = valueNoise(x, z, seed + 1, 24), sm = valueNoise(x, z, seed + 2, 8);
        return floor(16 + (big - 0.5) * 26 + (mid - 0.5) * 9 + (sm - 0.5) * 2);
    }

    public static void ground0(Vox w, int x, int z, int h, int seed) {
        w.force(x, h, z, valueNoise(x, z, seed + 40, 6) > 0.64 ? MOSS : GRASS);
        for (int k = 1; k <= 3; k++) {
            w.force(x, h - k, z, k <= 2 ? DIRT : STONE);
        }
    }

    // A tree's spot on the grid of 8: {x, z, kind} (0 maple, 1 ginkgo, 2 pine) or null (none, or in a bamboo grove).
    public static int[] treeSpot(int gx, int gz, int seed) {
        int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 8), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 8);
        double r = hash3(x, 3, z, seed + 20);
        if (grove(x, z, seed) || r > 0.8) {
            return null;
        }
        return new int[] {x, z, r < 0.62 ? 0 : r < 0.72 ? 1 : 2};
    }

    public static void treeAt(Vox w, int[] s, int h, int seed, IntBinaryOperator ground) {
        int x = s[0], z = s[1];
        switch (s[2]) {
            case 0 -> maple(w, x, h + 1, z, hashInt(x, h, z, seed + 23), ground);
            case 1 -> ginkgo(w, x, h + 1, z, hashInt(x, h, z, seed + 24), ground);
            default -> pine(w, x, h + 1, z, hashInt(x, h, z, seed + 25));
        }
    }

    public static boolean grove(int x, int z, int seed) {
        return valueNoise(x, z, seed + 21, 34) > 0.64;
    }

    // A column of a bamboo grove: the gravel trail through it, a culm, or a fern.
    public static void groveAt(Vox w, int x, int z, int h, int seed) {
        boolean trail = Math.abs(valueNoise(x, z, seed + 22, 40) - 0.5) < 0.03;
        if (trail) {
            w.force(x, h, z, PATH);
            return;
        }
        if (hash3(x, 4, z, seed + 26) < 0.14) {
            bamboo(w, x, h + 1, z, seed + 27);
        } else if (hash3(x, 5, z, seed + 26) < 0.1) {
            w.set(x, h + 1, z, FERN);
        }
    }

    public static boolean stream(int x, int z, int seed) {
        return Math.abs(valueNoise(x, z, seed + 30, 52) - 0.5) <= 0.03;
    }

    // A column of the stream: a shallow valley with water and stones.
    public static void streamAt(Vox w, int x, int z, int h, int seed) {
        for (int k = 0; k <= 4; k++) {
            if (w.get(x, h - 1 + k, z) != WATER) {
                w.force(x, h - 1 + k, z, k == 0 ? WATER : AIR);
            }
        }
        w.force(x, h - 2, z, GRAVEL);
        if (hash3(x, 6, z, seed) < 0.06) {
            w.force(x, h - 1, z, ROCK_MOSS);
        }
    }

    // The preview's hills (for the parity check).
    public static void hills(Vox w, int half, int seed, IntBinaryOperator ground, BiPredicate<Integer, Integer> keepOut) {
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (!keepOut.test(x, z)) {
                    ground0(w, x, z, ground.applyAsInt(x, z), seed);
                }
            }
        }
        for (int gx = -half; gx < half; gx += 8) {
            for (int gz = -half; gz < half; gz += 8) {
                int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 8), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 8);
                if (keepOut.test(x, z) || Math.abs(x) > half - 6 || Math.abs(z) > half - 6) {
                    continue;
                }
                int[] s = treeSpot(gx, gz, seed);
                if (s == null) {
                    continue;
                }
                treeAt(w, s, ground.applyAsInt(x, z), seed, ground);
            }
        }
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (keepOut.test(x, z) || !grove(x, z, seed)) {
                    continue;
                }
                groveAt(w, x, z, ground.applyAsInt(x, z), seed);
            }
        }
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (keepOut.test(x, z) || !stream(x, z, seed)) {
                    continue;
                }
                streamAt(w, x, z, ground.applyAsInt(x, z), seed);
            }
        }
    }
}
