package com.starkinhon.battlegroundsmod.world;

import static com.starkinhon.battlegroundsmod.world.JjkGen.cos;
import static com.starkinhon.battlegroundsmod.world.JjkGen.floor;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hash3;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hashInt;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hypot;
import static com.starkinhon.battlegroundsmod.world.JjkGen.round;
import static com.starkinhon.battlegroundsmod.world.JjkGen.sin;
import static com.starkinhon.battlegroundsmod.world.JjkGen.valueNoise;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;
import com.starkinhon.battlegroundsmod.world.TemploGen.Vox;

// The Colônia do Jogo do Abate (Stark 2026-11-12; port released 2026-11-13): line-by-line port of the approved
// preview's generator (forja/jujutsu-colonia/gen.js), the same draws and the same hashes. A piece of city closed in by an
// enormous dome barrier, abandoned in the middle of the game: broken concrete buildings (chunks torn out by blows,
// floors exposed, rebar sticking out), rubble heaped at their feet, cracked streets with burnt craters, abandoned cars,
// barricades, fires, and in the middle the crater of a big fight with a toppled building across it. Built as the
// preview builds it, around (0, 0) on a flat ground at GROUND; the feature moves it to its place and its level.
public final class ColoniaGen {
    // The preview's NAMES, in its order.
    public static final byte AIR = 0, STONE = 1, DIRT = 2, ASPHALT = 3, ASPHALT_CRACK = 4, LINE = 5, SIDEWALK = 6, CONCRETE = 7,
            CONCRETE_DARK = 8, CONCRETE_LIGHT = 9, WINDOW = 10, WINDOW_LIT = 11, FLOOR = 12, REBAR = 13, RUBBLE = 14, SCORCH = 15,
            FIRE = 16, CAR_RED = 17, CAR_WHITE = 18, CAR_BLUE = 19, CAR_BLACK = 20, TIRE = 21, BARRIER_CONE = 22, FENCE = 23,
            SIGN = 24, GRASS = 25, WEED = 26, GLASS_BITS = 27, TILE = 28;
    public static final int GROUND = 10, BLOCK = 34, ROAD = 8;
    // The colony's half size and the dome's radius (0.95 of it), as the preview's colonyPiece.
    public static final int HALF = 120;
    public static final int DOME_R = 114;

    private ColoniaGen() {
    }

    // ---------------------------------------------------------------- streets

    // A grid of blocks of 34 with streets of 8: asphalt with a dashed line in the middle, a sidewalk of 2 at the blocks'
    // edges, cracks and weeds growing in them.
    public static void street(Vox w, int x, int z, int seed) {
        int u = Math.floorMod(x, BLOCK), v = Math.floorMod(z, BLOCK);
        boolean onX = u < ROAD, onZ = v < ROAD;
        for (int k = 1; k <= 3; k++) {
            w.force(x, GROUND - k, z, k < 3 ? DIRT : STONE);
        }
        if (onX || onZ) {
            boolean crack = Math.abs(valueNoise(x, z, seed + 1, 6) - 0.5) < 0.02;
            boolean dash = (onX && !onZ && u == ROAD / 2 && Math.floorMod(z, 6) < 3) || (onZ && !onX && v == ROAD / 2 && Math.floorMod(x, 6) < 3);
            w.force(x, GROUND, z, crack ? ASPHALT_CRACK : dash ? LINE : ASPHALT);
            if (crack && hash3(x, 1, z, seed) < 0.3) {
                w.force(x, GROUND + 1, z, WEED);
            }
            return;
        }
        boolean sw = u < ROAD + 2 || u >= BLOCK - 2 || v < ROAD + 2 || v >= BLOCK - 2;
        w.force(x, GROUND, z, sw ? SIDEWALK : TILE);
    }

    // ---------------------------------------------------------------- the ruined building

    public record Building(int H, List<double[]> holes) {
    }

    // A concrete building: a box of wx by wz, floors of 4 (a slab, 3 of wall with a band of windows), the facade in bands;
    // then the wounds: 1 to 3 spheres of destruction tearing pieces out (from above or the side), rebar out of the cut's
    // edge, rubble at its foot. Some are left a stump of 1 to 3 floors.
    public static Building building(Vox w, int x0, int z0, int wx, int wz, int floors, int seed, int cuts) {
        Rng rng = new Rng(seed);
        int H = floors * 4;
        int style = rng.nextInt(0, 2);
        byte wall = new byte[] {CONCRETE, CONCRETE_LIGHT, CONCRETE_DARK}[style];
        for (int x = x0; x < x0 + wx; x++) {
            for (int z = z0; z < z0 + wz; z++) {
                boolean edgeX = x == x0 || x == x0 + wx - 1, edgeZ = z == z0 || z == z0 + wz - 1;
                for (int y = GROUND + 1; y <= GROUND + H; y++) {
                    int fy = (y - GROUND - 1) % 4;
                    if (fy == 0) {
                        w.force(x, y, z, edgeX || edgeZ ? wall : FLOOR);
                        continue;
                    }
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    boolean corner = edgeX && edgeZ;
                    int along = edgeX ? z - z0 : x - x0;
                    boolean win = !corner && along % 3 != 0 && fy >= 1 && fy <= 2;
                    w.force(x, y, z, win ? (hash3(x, y, z, seed) < 0.06 ? WINDOW_LIT : WINDOW) : wall);
                }
                w.force(x, GROUND + H + 1, z, edgeX || edgeZ ? wall : FLOOR);
            }
        }
        // the wounds
        List<double[]> holes = new ArrayList<>();
        for (int i = 0; i < cuts; i++) {
            boolean side = rng.next() < 0.5;
            int cx = x0 + (side ? (rng.next() < 0.5 ? 0 : wx - 1) : rng.nextInt(0, wx - 1));
            int cz = z0 + (side ? rng.nextInt(0, wz - 1) : (rng.next() < 0.5 ? 0 : wz - 1));
            int cy = GROUND + (i == 0 ? H + 1 : rng.nextInt(floor(H * 0.3), H));
            double r = i == 0 ? rng.range(Math.min(wx, wz) * 0.6, Math.max(wx, wz) * 0.9) : rng.range(3, 7);
            holes.add(new double[] {cx, cy, cz, r});
        }
        int lost = 0;
        for (int x = x0; x < x0 + wx; x++) {
            for (int z = z0; z < z0 + wz; z++) {
                for (int y = GROUND + 1; y <= GROUND + H + 1; y++) {
                    if (w.get(x, y, z) == 0 || !inHole(holes, x, y, z, seed)) {
                        continue;
                    }
                    w.force(x, y, z, AIR);
                    lost++;
                    // rebar: at the cut's edge, on a slab, sticking out into the hole
                    if ((y - GROUND - 1) % 4 == 0 && hash3(x, y, z, seed + 6) < 0.25) {
                        int below = w.get(x, y - 1, z);
                        if (below != 0 && hash3(x, y, z, seed + 7) < 0.5) {
                            w.force(x, y, z, REBAR);
                        }
                    }
                }
            }
        }
        // rubble at the foot: a heap in proportion to what fell, by the holes
        int piles = Math.min(8, 1 + lost / 160);
        for (int i = 0; i < piles; i++) {
            double[] hole = holes.get(i % holes.size());
            int hx = (int) hole[0], hz = (int) hole[2];
            int px = hx + rng.nextInt(-3, 3) + (hx <= x0 ? -2 : hx >= x0 + wx - 1 ? 2 : 0);
            int pz = hz + rng.nextInt(-3, 3) + (hz <= z0 ? -2 : hz >= z0 + wz - 1 ? 2 : 0);
            double r = rng.range(2, 4);
            for (int a = -4; a <= 4; a++) {
                for (int b = -4; b <= 4; b++) {
                    double d = hypot(a, b) + (hash3(px + a, 0, pz + b, seed + 8) - 0.5) * 1.2;
                    if (d > r) {
                        continue;
                    }
                    int h = round((r - d) * 0.8) + 1;
                    for (int k = 1; k <= h; k++) {
                        if (w.get(px + a, GROUND + k, pz + b) == 0) {
                            w.force(px + a, GROUND + k, pz + b, hash3(px + a, k, pz + b, seed + 9) < 0.2 ? wall : RUBBLE);
                        }
                    }
                }
            }
        }
        return new Building(H, holes);
    }

    private static boolean inHole(List<double[]> holes, int x, int y, int z, int seed) {
        for (double[] h : holes) {
            if (hypot(x - h[0], (y - h[1]) * 0.8, z - h[2]) + (hash3(x, y, z, seed + 5) - 0.5) * 2.2 < h[3]) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- things in the street

    public static void car(Vox w, int x, int z, char axis, int seed) {
        Rng rng = new Rng(seed);
        byte col = new byte[] {CAR_RED, CAR_WHITE, CAR_BLUE, CAR_BLACK}[rng.nextInt(0, 3)];
        boolean burnt = rng.next() < 0.3;
        final int L = 4, Wd = 2;
        for (int a = 0; a < L; a++) {
            for (int b = 0; b < Wd; b++) {
                int px = axis == 'x' ? x + a : x + b, pz = axis == 'x' ? z + b : z + a;
                w.force(px, GROUND + 1, pz, (a == 0 || a == L - 1) ? TIRE : burnt ? SCORCH : col);
                if (a >= 1 && a <= 2) {
                    w.force(px, GROUND + 2, pz, burnt ? SCORCH : WINDOW);
                }
            }
        }
    }

    // The crater of a blow: a shallow bowl in the asphalt with a raised rim of broken asphalt and a scorched bottom.
    public static void crater(Vox w, int cx, int cz, int r, int seed) {
        for (int x = cx - r - 2; x <= cx + r + 2; x++) {
            for (int z = cz - r - 2; z <= cz + r + 2; z++) {
                double d = hypot(x - cx, z - cz) + (hash3(x, 0, z, seed) - 0.5) * 1.5;
                if (d > r + 2) {
                    continue;
                }
                if (d < r) {
                    int depth = round((1 - (d / r) * (d / r)) * r * 0.45);
                    for (int y = GROUND - depth + 1; y <= GROUND + 4; y++) {
                        w.force(x, y, z, AIR);
                    }
                    w.force(x, GROUND - depth, z, hash3(x, 1, z, seed) < 0.6 ? SCORCH : RUBBLE);
                    for (int y = GROUND - depth - 2; y < GROUND - depth; y++) {
                        w.force(x, y, z, DIRT);
                    }
                } else if (hash3(x, 2, z, seed) < 0.55) {
                    w.force(x, GROUND + 1, z, hash3(x, 3, z, seed) < 0.5 ? ASPHALT_CRACK : RUBBLE);
                }
            }
        }
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static void fire(Vox w, int x, int y, int z, int seed) {
        int h = 1 + (hashInt(x, y, z, seed) % 3);
        for (int k = 0; k < h; k++) {
            w.force(x, y + k, z, FIRE);
        }
        for (int[] s : SIDES) {
            if (hash3(x + s[0], y, z + s[1], seed) < 0.5) {
                w.force(x + s[0], y, z + s[1], FIRE);
            }
        }
    }

    public static void barricade(Vox w, int x, int z, char axis, int seed) {
        for (int i = -3; i <= 3; i++) {
            int px = axis == 'x' ? x + i : x, pz = axis == 'x' ? z : z + i;
            if (Math.abs(i) % 2 == 0) {
                w.force(px, GROUND + 1, pz, BARRIER_CONE);
            } else {
                w.force(px, GROUND + 1, pz, FENCE);
                w.force(px, GROUND + 2, pz, FENCE);
            }
        }
        w.force(axis == 'x' ? x : x + 1, GROUND + 3, axis == 'x' ? z + 1 : z, SIGN);
    }

    // ---------------------------------------------------------------- the colony

    public static Vox colony(int seed, int half, boolean dome) {
        Vox w = new Vox(-half, -half, half - 1, half - 1);
        Rng rng = new Rng(seed);
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                street(w, x, z, seed);
            }
        }
        // the big fight's crater in the middle
        final int CR = dome ? 20 : 0;
        // buildings: one or two a block
        List<int[]> blds = new ArrayList<>();
        for (int bx = floor((double) -half / BLOCK) - 1; bx <= (int) Math.ceil((double) half / BLOCK); bx++) {
            for (int bz = floor((double) -half / BLOCK) - 1; bz <= (int) Math.ceil((double) half / BLOCK); bz++) {
                int lx = bx * BLOCK + ROAD + 2, lz = bz * BLOCK + ROAD + 2, L = BLOCK - ROAD - 4;
                if (lx < -half + 2 || lz < -half + 2 || lx + L > half - 2 || lz + L > half - 2) {
                    continue;
                }
                int s = hashInt(bx, 0, bz, seed + 10);
                Rng r = new Rng(s);
                double centerDist = hypot(lx + L / 2.0, lz + L / 2.0);
                if (CR != 0 && centerDist < CR + 14) {
                    continue;
                }
                boolean split = r.next() < 0.45;
                int[][] parts = split ? new int[][] {{lx, lz, L / 2 - 1, L}, {lx + L / 2 + 1, lz, L - L / 2 - 1, L}}
                        : new int[][] {{lx + 1, lz + 1, L - 2, L - 2}};
                for (int[] p : parts) {
                    boolean tall = r.next() < 0.3;
                    int floors = tall ? r.nextInt(9, 16) : r.nextInt(3, 8);
                    boolean stump = r.next() < 0.18;
                    int fl = stump ? r.nextInt(1, 3) : floors;
                    int cuts = stump ? 1 : r.nextInt(1, 3);
                    building(w, p[0], p[1], p[2], p[3], fl, hashInt(p[0], 1, p[1], seed + 11), cuts);
                    blds.add(p);
                }
            }
        }
        // craters, cars, barricades and fire along the streets
        for (int i = 0; i < half * half / 900; i++) {
            int x = rng.nextInt(-half + 6, half - 6), z = rng.nextInt(-half + 6, half - 6);
            int u = Math.floorMod(x, BLOCK), v = Math.floorMod(z, BLOCK);
            double r = rng.next();
            if (u >= ROAD && v >= ROAD) {
                continue;
            }
            if (r < 0.3) {
                crater(w, x, z, rng.nextInt(3, 6), hashInt(x, 0, z, seed + 12));
            } else if (r < 0.7) {
                car(w, x, z, u < ROAD ? 'z' : 'x', hashInt(x, 1, z, seed + 13));
            } else if (r < 0.82) {
                barricade(w, x, z, u < ROAD ? 'x' : 'z', hashInt(x, 2, z, seed + 14));
            } else {
                fire(w, x, GROUND + 1, z, seed + 15);
            }
        }
        // fire on top of rubble
        for (int[] b : blds) {
            if (hash3(b[0], 3, b[1], seed) < 0.3) {
                fire(w, b[0] - 2, GROUND + 1, b[1] + b[3] / 2, seed + 16);
            }
        }
        // the big crater with the toppled building across it
        if (CR != 0) {
            crater(w, 0, 0, CR, seed + 17);
            for (int x = -CR - 4; x <= CR + 4; x++) {
                for (int z = -CR - 4; z <= CR + 4; z++) {
                    double d = hypot(x, z);
                    if (d < CR + 4 && d > CR - 1 && hash3(x, 4, z, seed) < 0.35) {
                        for (int k = 1; k <= (hash3(x, 5, z, seed) < 0.25 ? 2 : 1); k++) {
                            w.force(x, GROUND + k, z, RUBBLE);
                        }
                    }
                }
            }
            // the toppled building: a long box lying down, tilted into the crater
            double a = rng.range(0, Math.PI), ca = cos(a), sa = sin(a);
            final int Lb = 46;
            final double Wb = 9;
            for (int s = -Lb / 2; s <= Lb / 2; s++) {
                for (double t = -Wb / 2; t <= Wb / 2; t++) {
                    int x = round(ca * s - sa * t), z = round(sa * s + ca * t);
                    int base = GROUND - 6 + round((s + Lb / 2.0) / Lb * 10);
                    for (int k = 0; k < 9; k++) {
                        boolean edge = Math.abs(t) >= Wb / 2 - 0.5 || k == 0 || k == 8;
                        int sl = round(s + Lb / 2.0);
                        boolean fy = sl % 4 == 0;
                        w.force(x, base + k, z, edge ? (fy || k == 0 || k == 8 ? CONCRETE_DARK
                                : (k % 3 != 0 && sl % 4 != 1) ? (hash3(x, base + k, z, seed) < 0.12 ? AIR : WINDOW) : CONCRETE_DARK)
                                : fy ? FLOOR : AIR);
                    }
                }
            }
            for (int i = 0; i < 7; i++) {
                int fx = rng.nextInt(-CR + 4, CR - 4), fz = rng.nextInt(-CR + 4, CR - 4);
                int fy = GROUND + 12;
                while (fy > GROUND - 14 && w.get(fx, fy - 1, fz) == 0) {
                    fy--;
                }
                fire(w, fx, fy, fz, seed + 18 + i);
            }
        }
        // (the preview draws the Kogane floating over the streets here; in the game it is not a block)
        return w;
    }
}
