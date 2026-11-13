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
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;
import com.starkinhon.battlegroundsmod.world.TemploGen.Vox;

// The Aldeia Amaldiçoada (Stark 2026-11-12, "Aldeia Amaldiçoada (pântanos)"; port released 2026-11-13): the line-by-line
// port of the approved preview's generator (/mnt/project-files/forja/jujutsu-aldeia/gen.js), the same draws in the same
// order, so the game makes what the preview showed. Change one, change the other. The preview's water level is its
// constant SEA (8); here every function takes it as sea (the game's sea level - 1).
// A village of Japan's countryside sinking into a swamp. Still dark green water, mud, reeds, water lilies, swamp
// cypresses with their "knees" out of the water and old man's beard hanging from the branches. In the middle, on a
// slightly higher island, the village: farmhouses with thick thatched roofs (some whole, some rotten, one or another
// burnt), on stilts where the water reaches, plank walkways joining everything over the swamp, a well in the square,
// flooded rice paddies with scarecrows and rice drying racks, jizo statues with red bibs at the entrance, a small shrine
// with a faded torii and, away from it all, the shed with the wooden cell covered in talismans: the village that locked
// up whoever saw curses.
public final class AldeiaGen {
    // The preview's NAMES, in its order.
    public static final byte AIR = 0, STONE = 1, DIRT = 2, MUD = 3, GRASS = 4, GRASS_WET = 5, WATER = 6, PATH = 7, BOARD = 8,
            POST = 9, WOOD = 10, WOOD_DARK = 11, PLASTER = 12, SHOJI = 13, SHOJI_TORN = 14, THATCH = 15, THATCH_DARK = 16,
            RIDGE = 17, CHAR = 18, LANTERN = 19, STRAW = 20, RICE = 21, REED = 22, LILY = 23, BARK = 24, LEAF = 25, LEAF2 = 26,
            MOSS_HANG = 27, ROOT = 28, TORII = 29, STATUE = 30, RED_BIB = 31, BARS = 32, TALISMAN = 33, DIKE = 34, BAMBOO = 35,
            COBBLE = 36;

    // How far a cypress' blocks reach from its trunk: a branch (up to 5) and its clump (3.4), the knees (5).
    public static final int TREE_REACH = 10;
    // How far the village's blocks reach from its center: the paddies (to 70 + 9), the cage shed (74 + 5).
    public static final int VILLAGE_REACH = 84;

    private AldeiaGen() {
    }

    // ---------------------------------------------------------------- terrain

    // The swamp: nearly all of it level with the water (sea), with islets and channels.
    public static int heightAt(int x, int z, int seed, int sea) {
        double big = valueNoise(x, z, seed, 60), mid = valueNoise(x, z, seed + 1, 22), sm = valueNoise(x, z, seed + 2, 7);
        return floor(sea - 0.5 + (big - 0.5) * 9 + (mid - 0.5) * 6 + (sm - 0.5) * 2);
    }

    // The swamp's ground: mud at the water's edge, wet grass just above, dry grass higher up; water up to the sea with
    // reeds and water lilies.
    public static void ground0(Vox w, int x, int z, int h, int seed, int sea) {
        boolean under = h < sea;
        byte top = under ? (valueNoise(x, z, seed + 40, 5) > 0.55 ? MUD : DIRT)
                : h == sea ? (hash3(x, 0, z, seed + 41) < 0.6 ? MUD : GRASS_WET) : h == sea + 1 ? GRASS_WET : GRASS;
        w.force(x, h, z, top);
        for (int k = 1; k <= 3; k++) {
            w.force(x, h - k, z, k <= 2 ? (under ? MUD : DIRT) : STONE);
        }
        if (under) {
            for (int y = h + 1; y <= sea; y++) {
                w.force(x, y, z, WATER);
            }
            double r = hash3(x, 5, z, seed + 42);
            if (sea - h <= 2 && r < 0.16) {
                w.force(x, sea + 1, z, REED);
                if (r < 0.07) {
                    w.force(x, sea + 2, z, REED);
                }
            } else if (r > 0.93 && valueNoise(x, z, seed + 43, 9) > 0.5) {
                w.force(x, sea + 1, z, LILY);
            }
        } else if (h <= sea + 1 && hash3(x, 6, z, seed + 42) < 0.1) {
            w.force(x, h + 1, z, REED);
        }
    }

    // ---------------------------------------------------------------- swamp cypress

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAG = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    // A straight trunk thickening at the foot (spreading into buttresses in the water), short nearly level branches only
    // on the upper half, with flat thin tufts of leaves; old man's beard falling from the tufts; around it in the water,
    // the cypress' "knees" one or two blocks out. Returns its height.
    public static int swampTree(Vox w, int x, int y, int z, int seed, int sea) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(11, 19);
        boolean wet = w.get(x, y - 1, z) == WATER || w.get(x, y, z) == WATER;
        int bottom = y;
        while (bottom > y - 6 && w.get(x, bottom - 1, z) == WATER) {
            bottom--;
        }
        for (int k = bottom; k < y + H; k++) {
            w.force(x, k, z, BARK);
        }
        // the widened foot
        int flare = wet ? 3 : 2;
        for (int[] d : DIRS) {
            for (int k = bottom; k < y + flare - (rng.next() < 0.4 ? 1 : 0); k++) {
                w.force(x + d[0], k, z + d[1], BARK);
            }
        }
        if (wet) {
            for (int[] d : DIAG) {
                if (rng.next() < 0.6) {
                    for (int k = bottom; k < y; k++) {
                        w.force(x + d[0], k, z + d[1], BARK);
                    }
                }
            }
        }
        // knees
        if (wet) {
            for (int i = 0; i < rng.nextInt(2, 5); i++) {
                double a = rng.range(0, Math.PI * 2), d = rng.range(2.5, 5);
                int kx = round(x + cos(a) * d), kz = round(z + sin(a) * d);
                if (w.get(kx, sea, kz) == WATER) {
                    w.force(kx, sea, kz, ROOT);
                    if (rng.next() < 0.5) {
                        w.force(kx, sea + 1, kz, ROOT);
                    }
                }
            }
        }
        // branches and tufts
        int nb = rng.nextInt(4, 7);
        for (int i = 0; i < nb; i++) {
            double a = rng.range(0, Math.PI * 2);
            int by = y + floor(H * rng.range(0.5, 0.95)), l = rng.nextInt(2, 5);
            int ex = x, ez = z;
            for (int k = 1; k <= l; k++) {
                ex = round(x + cos(a) * k);
                ez = round(z + sin(a) * k);
                w.force(ex, by + (k > l - 1 ? 1 : 0), ez, BARK);
            }
            clump(w, ex, by + 1, ez, rng.range(2.2, 3.4), seed + i * 17);
        }
        clump(w, x, y + H, z, rng.range(2.0, 3.0), seed + 99);
        return H;
    }

    static void clump(Vox w, int cx, int cy, int cz, double r, int seed) {
        int ri = (int) Math.ceil(r);
        for (int a = -ri; a <= ri; a++) {
            for (int b = -1; b <= 0; b++) {
                for (int c = -ri; c <= ri; c++) {
                    double d = hypot(a / r, (b + 0.5) / 1.2, c / r) + (hash3(cx + a, cy + b, cz + c, seed) - 0.5) * 0.6;
                    if (d > 1) {
                        continue;
                    }
                    // thin: a hole here and there
                    if (hash3(cx + a, cy + b, cz + c, seed + 1) < 0.18) {
                        continue;
                    }
                    w.set(cx + a, cy + b, cz + c, hash3(cx + a, cy + b, cz + c, seed + 2) < 0.4 ? LEAF2 : LEAF);
                    if (b == -1 && hash3(cx + a, 0, cz + c, seed + 3) < 0.1) {
                        int len = 1 + floor(hash3(cx + a, 1, cz + c, seed + 3) * 5);
                        for (int k = 1; k <= len; k++) {
                            w.set(cx + a, cy + b - k, cz + c, MOSS_HANG);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- houses

    // A farmhouse (minka): a rectangle of len x wid, long along axis, its front facing front (+1/-1 on the other axis). The
    // floor raised on stilts (water and mud pass under it), a plank porch (engawa) in front, walls of dark posts with
    // dirty plaster and shoji in front, the door in the middle. A thick, steep two-slope thatched roof running past the
    // wall, with the dark ridge. state: 0 whole, 1 rotten (holes in the roof, torn shoji, a fallen wall), 2 burnt (charcoal,
    // half the roof gone). Returns {fy, door x, door z}.
    public static int[] house(Vox w, int cx, int cz, char axis, int front, int len, int wid, int seed, int state, IntBinaryOperator ground,
            int sea) {
        Rng rng = new Rng(seed);
        int hl = floor(len / 2.0), hw = floor(wid / 2.0);
        Frame f = new Frame(axis, cx, cz, front);
        int fy = sea + 1;
        for (int u = -hl; u <= hl; u++) {
            for (int v = -hw; v <= hw + 1; v++) {
                fy = Math.max(fy, ground.applyAsInt(f.x(u, v), f.z(u, v)) + 1);
            }
        }
        boolean burnt = state == 2;
        // stilts and floor
        for (int u = -hl; u <= hl; u++) {
            for (int v = -hw; v <= hw + 1; v++) {
                int x = f.x(u, v), z = f.z(u, v), g = ground.applyAsInt(x, z);
                boolean post = (Math.abs(u) == hl || u % 3 == 0) && (Math.abs(v) == hw || v == hw + 1);
                if (post) {
                    for (int y = g + 1; y < fy; y++) {
                        f.put(w, u, v, y, POST);
                    }
                }
                for (int y = g + 1; y < fy; y++) {
                    if (!post && w.get(x, y, z) != WATER) {
                        f.put(w, u, v, y, AIR);
                    }
                }
                f.put(w, u, v, fy, v == hw + 1 ? BOARD : (burnt && hash3(x, fy, z, seed + 2) < 0.3 ? CHAR : WOOD));
            }
        }
        // the porch steps
        for (int k = 1; k <= fy - ground.applyAsInt(f.x(0, hw + 2), f.z(0, hw + 2)); k++) {
            f.put(w, 0, hw + 1 + k, fy - k, BOARD);
            f.put(w, 1, hw + 1 + k, fy - k, BOARD);
        }
        // walls
        int WH = 4;
        for (int u = -hl; u <= hl; u++) {
            for (int v : new int[] {-hw, hw}) {
                for (int y = fy + 1; y <= fy + WH; y++) {
                    boolean corner = Math.abs(u) == hl, pillar = corner || u % 2 == 0;
                    byte t = pillar ? WOOD_DARK : y == fy + WH ? WOOD_DARK
                            : v == hw ? (state == 1 && hash3(f.x(u, v), y, f.z(u, v), seed + 3) < 0.35 ? SHOJI_TORN : SHOJI)
                            : wallT(f, u, v, y, burnt, seed);
                    if (v == hw && Math.abs(u) <= 1 && y <= fy + 3 && !corner) {
                        t = AIR; // the door
                    }
                    if (state == 1 && !corner && v == -hw && hash3(u, y, seed, 5) < 0.12) {
                        t = AIR;
                    }
                    if (burnt && y > fy + 2 && hash3(f.x(u, v), y, f.z(u, v), seed + 4) < 0.4) {
                        t = AIR;
                    }
                    f.put(w, u, v, y, t);
                }
            }
        }
        for (int v = -hw + 1; v < hw; v++) {
            for (int u : new int[] {-hl, hl}) {
                for (int y = fy + 1; y <= fy + WH; y++) {
                    byte t = v % 2 == 0 ? WOOD_DARK : y == fy + WH ? WOOD_DARK : wallT(f, u, v, y, burnt, seed);
                    if (burnt && y > fy + 2 && hash3(f.x(u, v), y, f.z(u, v), seed + 4) < 0.4) {
                        t = AIR;
                    }
                    f.put(w, u, v, y, t);
                }
            }
        }
        // the porch posts
        for (int u : new int[] {-hl, hl}) {
            for (int y = fy + 1; y <= fy + WH; y++) {
                f.put(w, u, hw + 1, y, POST);
            }
        }
        // the thatched roof: two slopes, the ridge along the length, the eave 2 past the wall (it covers the porch)
        int ry = fy + WH + 1, RW = hw + 2, rise = RW + 1;
        for (int k = 0; k <= rise; k++) {
            double vv = RW - k + 0.5; // the roof's half width at this layer
            if (vv < 0) {
                break;
            }
            for (int u = -hl - 1; u <= hl + 1; u++) {
                for (int v = -RW; v <= RW + 1; v++) {
                    double vc = v - 0.5;
                    double d = Math.abs(vc);
                    if (d > vv || d < vv - 2) {
                        continue; // a shell 2 thick
                    }
                    if (state == 1 && hash3(f.x(u, v), ry + k, f.z(u, v), seed + 6) < 0.13) {
                        continue;
                    }
                    if (burnt && (u > 0 || hash3(f.x(u, v), ry + k, f.z(u, v), seed + 7) < 0.3)) {
                        continue;
                    }
                    byte t = k == rise || vv < 1.5 ? RIDGE
                            : (burnt ? CHAR : (hash3(f.x(u, v), ry + k, f.z(u, v), seed + 8) < 0.3 ? THATCH_DARK : THATCH));
                    f.put(w, u, v, ry + k, t);
                }
            }
            // the gable: closes the triangle at the ends
            if (!burnt) {
                for (int u : new int[] {-hl, hl}) {
                    for (int v = -RW; v <= RW + 1; v++) {
                        double d = Math.abs(v - 0.5);
                        if (d < vv - 1.5 && d <= hw) {
                            f.put(w, u, v, ry + k, WOOD);
                        }
                    }
                }
            }
        }
        // a paper lantern hanging at the door (none on the burnt one) and a talisman on the door (the village guarding
        // itself)
        if (!burnt) {
            f.put(w, 2, hw + 1, fy + WH - 1, LANTERN);
        }
        if (rng.next() < 0.6) {
            f.put(w, -2, hw, fy + 2, TALISMAN);
        }
        return new int[] {fy, f.x(0, hw + 3), f.z(0, hw + 3)};
    }

    private static byte wallT(Frame f, int u, int v, int y, boolean burnt, int seed) {
        if (burnt && hash3(f.x(u, v), y, f.z(u, v), seed + 1) < 0.5) {
            return CHAR;
        }
        return PLASTER;
    }

    // u along the length, v across (v positive = the front)
    private record Frame(char axis, int cx, int cz, int front) {
        int x(int u, int v) {
            return axis == 'x' ? cx + u : cx + v * front;
        }

        int z(int u, int v) {
            return axis == 'x' ? cz + v * front : cz + u;
        }

        void put(Vox w, int u, int v, int y, byte t) {
            w.force(x(u, v), y, z(u, v), t);
        }
    }

    // ---------------------------------------------------------------- the village's things

    public static void well(Vox w, int x, int y, int z) {
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    w.force(x, y, z, WATER);
                    w.force(x, y - 1, z, WATER);
                    w.force(x, y + 1, z, AIR);
                    continue;
                }
                w.force(x + a, y, z + b, COBBLE);
                w.force(x + a, y + 1, z + b, COBBLE);
            }
        }
        for (int a : new int[] {-1, 1}) {
            for (int k = 2; k <= 4; k++) {
                w.force(x + a, y + k, z, POST);
            }
        }
        for (int a = -2; a <= 2; a++) {
            for (int b : new int[] {-1, 0, 1}) {
                w.force(x + a, y + 5 + (b == 0 ? 1 : 0), z + b, b == 0 ? RIDGE : THATCH);
            }
        }
        w.force(x, y + 4, z, WOOD_DARK);
    }

    public static void scarecrow(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        for (int k = 0; k < 4; k++) {
            w.force(x, y + k, z, POST);
        }
        boolean ax = rng.next() < 0.5;
        for (int s : new int[] {-1, 1}) {
            w.force(x + (ax ? s : 0), y + 2, z + (ax ? 0 : s), STRAW);
        }
        w.force(x, y + 2, z, STRAW);
        w.force(x, y + 4, z, STRAW);
        for (int[] d : DIRS) {
            if (rng.next() < 0.8) {
                w.force(x + d[0], y + 4, z + d[1], THATCH);
            }
        }
    }

    // A rice drying rack (hasa-gake): two trestles and a pole with bundles of straw hanging.
    public static void dryingRack(Vox w, int x, int y, int z, char axis, int seed) {
        int n = 4 + (hashInt(x, y, z, seed) % 3);
        for (int u = -n; u <= n; u++) {
            int px = axis == 'x' ? x + u : x, pz = axis == 'x' ? z : z + u;
            if (Math.abs(u) == n) {
                for (int k = 0; k < 3; k++) {
                    w.force(px, y + k, pz, POST);
                }
            } else {
                w.force(px, y + 2, pz, STRAW);
                if (hash3(px, y, pz, seed) < 0.7) {
                    w.force(px, y + 1, pz, STRAW);
                }
            }
        }
    }

    public static void jizo(Vox w, int x, int y, int z) {
        w.force(x, y, z, STATUE);
        w.force(x, y + 1, z, RED_BIB);
        w.force(x, y + 2, z, STATUE);
    }

    // The village shrine: a stone platform, a little wooden house with a thatched roof, a faded torii in front.
    public static void shrine(Vox w, int x, int y, int z, int front, int seed) {
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                w.force(x + a, y, z + b, COBBLE);
                for (int k = 1; k <= 6; k++) {
                    w.force(x + a, y + k, z + b, AIR);
                }
            }
        }
        for (int[] d : DIAG) {
            for (int k = 1; k <= 3; k++) {
                w.force(x + d[0], y + k, z + d[1], WOOD_DARK);
            }
        }
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (Math.abs(a) < 1 || Math.abs(b) < 1) {
                    w.force(x + a, y + 1, z + b, WOOD);
                }
            }
        }
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                w.force(x + a, y + 4, z + b, Math.abs(a) <= 1 && Math.abs(b) <= 1 ? THATCH : THATCH_DARK);
            }
        }
        for (int a = -1; a <= 1; a++) {
            w.force(x + a, y + 5, z, RIDGE);
        }
        w.force(x, y + 2, z + 2 * front, TALISMAN);
        // the torii in front
        int tz = z + 7 * front;
        for (int a : new int[] {-2, 2}) {
            for (int k = 1; k <= 5; k++) {
                w.force(x + a, y + k - 1, tz, TORII);
            }
        }
        for (int a = -3; a <= 3; a++) {
            w.force(x + a, y + 5, tz, TORII);
        }
        for (int a = -2; a <= 2; a++) {
            w.force(x + a, y + 3, tz, TORII);
        }
    }

    private static void shedPut(Vox w, int x, int z, char axis, int a, int b, int y, byte t) {
        if (axis == 'x') {
            w.force(x + a, y, z + b, t);
        } else {
            w.force(x + b, y, z + a, t);
        }
    }

    // The cage shed: a barn of dark planks, the door locked, and inside a cell of wooden bars (zashikiro) covered in
    // talismans. It stands apart from the village, at the swamp's edge.
    public static void cageShed(Vox w, int x, int z, char axis, int seed, IntBinaryOperator ground, int sea) {
        int fy = sea + 1;
        for (int a = -4; a <= 4; a++) {
            for (int b = -3; b <= 3; b++) {
                fy = Math.max(fy, ground.applyAsInt(x + a, z + b) + 1);
            }
        }
        for (int a = -4; a <= 4; a++) {
            for (int b = -3; b <= 3; b++) {
                int gx = axis == 'x' ? x + a : x + b, gz = axis == 'x' ? z + b : z + a;
                for (int y = ground.applyAsInt(gx, gz) + 1; y < fy; y++) {
                    shedPut(w, x, z, axis, a, b, y, (Math.abs(a) == 4 && Math.abs(b) == 3) ? POST : MUD);
                }
                shedPut(w, x, z, axis, a, b, fy, WOOD_DARK);
                for (int y = fy + 1; y <= fy + 4; y++) {
                    shedPut(w, x, z, axis, a, b, y, AIR);
                }
            }
        }
        for (int a = -4; a <= 4; a++) {
            for (int b = -3; b <= 3; b++) {
                boolean edge = Math.abs(a) == 4 || Math.abs(b) == 3;
                if (!edge) {
                    continue;
                }
                for (int y = fy + 1; y <= fy + 4; y++) {
                    boolean door = b == 3 && Math.abs(a) <= 0 && y <= fy + 3;
                    shedPut(w, x, z, axis, a, b, y, door ? WOOD : (Math.abs(a) == 4 && Math.abs(b) == 3) ? POST : WOOD_DARK);
                }
            }
        }
        // a crack of light: some planks missing
        for (int a = -3; a <= 3; a += 3) {
            shedPut(w, x, z, axis, a, -3, fy + 3, AIR);
        }
        // the cell: bars of 4 x 3 at the back, talismans on the bars
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 0; b++) {
                boolean edge = Math.abs(a) == 2 || b == 0;
                if (!edge) {
                    continue;
                }
                for (int y = fy + 1; y <= fy + 3; y++) {
                    shedPut(w, x, z, axis, a, b, y, (hash3(a, y, b, seed) < 0.2 && b == 0) ? TALISMAN : BARS);
                }
            }
        }
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 0; b++) {
                shedPut(w, x, z, axis, a, b, fy + 4, WOOD_DARK);
            }
        }
        // a roof of old planks and thatch
        for (int a = -5; a <= 5; a++) {
            for (int b = -4; b <= 4; b++) {
                int k = 4 - Math.abs(b);
                if (hash3(a, b, seed, 9) < 0.08) {
                    continue;
                }
                shedPut(w, x, z, axis, a, b, fy + 5 + floor(k / 2.0), THATCH_DARK);
            }
        }
        for (int a = -4; a <= 4; a++) {
            for (int b : new int[] {-1, 0, 1}) {
                for (int y = fy + 5; y < fy + 7; y++) {
                    if (w.get(axis == 'x' ? x + a : x + b, y, axis == 'x' ? z + b : z + a) == 0
                            && y < fy + 5 + floor((4 - Math.abs(b)) / 2.0)) {
                        shedPut(w, x, z, axis, a, b, y, WOOD_DARK);
                    }
                }
            }
        }
        // talismans on the door
        shedPut(w, x, z, axis, 1, 4, fy + 2, TALISMAN);
        shedPut(w, x, z, axis, -1, 4, fy + 3, TALISMAN);
    }

    // A plank walkway over the swamp: straight from a to b (first along one axis, then the other); over water it is a
    // plank at sea + 1 with a stake every 3; over land a packed earth trail. (The preview's step from a point to itself
    // is NaN and does nothing but count: null here.)
    public static void walkway(Vox w, int[] a, int[] b, IntBinaryOperator ground, int sea) {
        List<int[]> pts = new ArrayList<>();
        step(pts, a[0], a[1], b[0], a[1]);
        step(pts, b[0], a[1], b[0], b[1]);
        int i = 0;
        for (int[] p : pts) {
            i++;
            if (p == null) {
                continue;
            }
            for (int[] o : new int[][] {{0, 0}, {1, 0}, {0, 1}}) {
                int px = p[0] + o[0], pz = p[1] + o[1], g = ground.applyAsInt(px, pz);
                int top = w.get(px, g + 1, pz);
                if (top == POST || top == WOOD || top == BOARD || top == COBBLE) {
                    continue;
                }
                if (g < sea + 1) {
                    w.force(px, sea + 1, pz, BOARD);
                    w.force(px, sea + 2, pz, AIR);
                    if (i % 3 == 0 && o[0] == 0 && o[1] == 0) {
                        for (int y = g + 1; y <= sea; y++) {
                            w.force(px, y, pz, POST);
                        }
                    }
                } else if (w.get(px, g, pz) == GRASS || w.get(px, g, pz) == GRASS_WET || w.get(px, g, pz) == MUD) {
                    w.force(px, g, pz, PATH);
                    if (w.get(px, g + 1, pz) == REED) {
                        w.force(px, g + 1, pz, AIR);
                    }
                }
            }
        }
    }

    private static void step(List<int[]> pts, int x0, int z0, int x1, int z1) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= n; i++) {
            if (n == 0) {
                pts.add(null);
                continue;
            }
            pts.add(new int[] {round(x0 + (double) ((x1 - x0) * i) / n), round(z0 + (double) ((z1 - z0) * i) / n)});
        }
    }

    // ---------------------------------------------------------------- the village

    private record Occ(int x, int z, double rx, double rz) {
    }

    private static boolean occupied(List<Occ> occ, int x, int z, double rx, double rz) {
        for (Occ o : occ) {
            if (Math.abs(o.x() - x) < o.rx() + rx && Math.abs(o.z() - z) < o.rz() + rz) {
                return true;
            }
        }
        return false;
    }

    public record Village(int houses, int paddies, int[] shrine, int[] cage, int[] plaza, int[] entrance) {
    }

    // The village on a higher island around (0, 0): a square with a well in the middle, houses around it facing the
    // square, walkways joining each house to the square, flooded rice paddies in terraces at the island's edge (with
    // scarecrows and drying racks), jizo at the entrance, the shrine on one side and the cage shed apart, on the other.
    public static Village village(Vox w, int seed, IntBinaryOperator ground, int sea) {
        Rng rng = new Rng(seed);
        int py = Math.max(sea + 1, ground.applyAsInt(0, 0));
        // the square of packed earth
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                if (hypot(x, z) > 9 + (valueNoise(x, z, seed + 1, 4) - 0.5) * 3) {
                    continue;
                }
                int g = ground.applyAsInt(x, z);
                for (int y = g + 1; y <= py; y++) {
                    w.force(x, y, z, DIRT);
                }
                for (int y = py + 1; y <= py + 3; y++) {
                    w.force(x, y, z, AIR);
                }
                w.force(x, py, z, PATH);
            }
        }
        well(w, 0, py, 0);
        // houses: tries on a ring, none touching another
        List<Occ> occ = new ArrayList<>();
        List<int[]> doors = new ArrayList<>();
        int houses = 0;
        for (int tries = 0; tries < 70 && houses < 11; tries++) {
            double a = rng.range(0, Math.PI * 2), d = rng.range(16, 44);
            int cx = round(cos(a) * d), cz = round(sin(a) * d);
            char axis = Math.abs(cos(a)) > Math.abs(sin(a)) ? 'z' : 'x';
            int len = rng.nextInt(4, 6) * 2 + 1, wid = rng.nextInt(3, 4) * 2 + 1;
            int front = axis == 'z' ? (cx > 0 ? -1 : 1) : (cz > 0 ? -1 : 1);
            double rx = (axis == 'x' ? len : wid) / 2.0 + 4, rz = (axis == 'x' ? wid : len) / 2.0 + 4;
            if (occupied(occ, cx, cz, rx, rz)) {
                continue;
            }
            if (ground.applyAsInt(cx, cz) < sea - 3) {
                continue;
            }
            occ.add(new Occ(cx, cz, rx, rz));
            double r = rng.next();
            int state = r < 0.15 ? 2 : r < 0.5 ? 1 : 0;
            int[] h = house(w, cx, cz, axis, front, len, wid, hashInt(cx, 1, cz, seed + 2), state, ground, sea);
            doors.add(new int[] {h[1], h[2]});
            houses++;
        }
        for (int[] door : doors) {
            walkway(w, door, new int[] {Integer.signum(door[0]) * 6, Integer.signum(door[1]) * 6}, ground, sea);
        }
        // paddies: plots of 9 x 7 flooded at the island's edge, each on its own level (terraces), with an earth dike around
        int paddies = 0;
        for (int gx = -66; gx < 66; gx += 10) {
            for (int gz = -66; gz < 66; gz += 8) {
                int cx = gx + 5, cz = gz + 4;
                double d = hypot(cx, cz);
                if (d < 48 || d > 70) {
                    continue;
                }
                if (occupied(occ, cx, cz, 6, 5)) {
                    continue;
                }
                if (valueNoise(cx, cz, seed + 5, 20) < 0.55) {
                    continue;
                }
                int sum = 0;
                for (int x = gx; x < gx + 10; x++) {
                    for (int z = gz; z < gz + 8; z++) {
                        sum += ground.applyAsInt(x, z);
                    }
                }
                int lv = Math.max(sea, round(sum / 80.0));
                for (int x = gx; x < gx + 10; x++) {
                    for (int z = gz; z < gz + 8; z++) {
                        boolean edge = x == gx || x == gx + 9 || z == gz || z == gz + 7;
                        int g = ground.applyAsInt(x, z);
                        for (int y = Math.min(g, lv) - 1; y <= Math.max(g, lv) + 3; y++) {
                            w.force(x, y, z, y < lv ? DIRT : AIR);
                        }
                        if (edge) {
                            w.force(x, lv, z, DIKE);
                            w.force(x, lv + 1, z, DIKE);
                            continue;
                        }
                        w.force(x, lv, z, MUD);
                        w.force(x, lv + 1, z, WATER);
                        // rice in rows
                        if ((x - gx) % 2 == 1 && (z - gz) % 2 == 1 && hash3(x, lv, z, seed + 6) < 0.85) {
                            w.force(x, lv + 2, z, RICE);
                        }
                    }
                }
                paddies++;
                double r = hash3(cx, 1, cz, seed + 7);
                if (r < 0.35) {
                    scarecrow(w, cx, lv + 2, cz, hashInt(cx, 2, cz, seed));
                }
            }
        }
        // racks between the paddies and the houses
        for (int i = 0; i < 4; i++) {
            double a = rng.range(0, Math.PI * 2), d = rng.range(40, 47);
            int x = round(cos(a) * d), z = round(sin(a) * d), g = ground.applyAsInt(x, z);
            if (g < sea || occupied(occ, x, z, 4, 4)) {
                continue;
            }
            dryingRack(w, x, g + 1, z, Math.abs(cos(a)) > 0.7 ? 'z' : 'x', hashInt(i, 3, 3, seed));
        }
        // the shrine on one side, the cell on the other, jizo at the entrance
        double sa = rng.range(0, Math.PI * 2);
        int sx = round(cos(sa) * 30), sz = round(sin(sa) * 30);
        int sy = sea + 2;
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                sy = Math.max(sy, ground.applyAsInt(sx + a, sz + b) + 1);
            }
        }
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                for (int y = ground.applyAsInt(sx + a, sz + b) + 1; y < sy; y++) {
                    w.force(sx + a, y, sz + b, COBBLE);
                }
            }
        }
        shrine(w, sx, sy, sz, sz > 0 ? -1 : 1, seed + 8);
        double ca = sa + Math.PI + rng.range(-0.5, 0.5);
        int kx = round(cos(ca) * 74), kz = round(sin(ca) * 74);
        cageShed(w, kx, kz, Math.abs(cos(ca)) > 0.7 ? 'z' : 'x', seed + 9, ground, sea);
        walkway(w, new int[] {round(cos(ca) * 10), round(sin(ca) * 10)},
                new int[] {kx - round(cos(ca) * 6), kz - round(sin(ca) * 6)}, ground, sea);
        double ea = ca + Math.PI / 2;
        int ex = round(cos(ea) * 50), ez = round(sin(ea) * 50);
        walkway(w, new int[] {round(cos(ea) * 9), round(sin(ea) * 9)}, new int[] {ex, ez}, ground, sea);
        for (int i = -2; i <= 2; i++) {
            int jx = ex + round(-sin(ea) * 3 + cos(ea) * i * 2), jz = ez + round(cos(ea) * 3 + sin(ea) * i * 2);
            jizo(w, jx, Math.max(sea + 1, ground.applyAsInt(jx, jz) + 1), jz);
        }
        return new Village(houses, paddies, new int[] {sx, sy, sz}, new int[] {kx, kz}, new int[] {0, py, 0}, new int[] {ex, ez});
    }

    // A cypress' spot on the grid of 9 (gx, gz on the grid): its column, or null.
    public static int[] treeSpot(int gx, int gz, int seed) {
        int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 9), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 9);
        if (hash3(x, 3, z, seed + 20) > 0.62) {
            return null;
        }
        return new int[] {x, z};
    }

    public static void tree(Vox w, int x, int g, int z, int seed, int sea) {
        swampTree(w, x, Math.max(g, sea) + 1, z, hashInt(x, g, z, seed + 23), sea);
    }

    // The preview's swamp (for the parity check).
    public static void swamp(Vox w, int half, int seed, IntBinaryOperator ground, BiPredicate<Integer, Integer> keepOut, int sea) {
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                ground0(w, x, z, ground.applyAsInt(x, z), seed, sea);
            }
        }
        for (int gx = -half; gx < half; gx += 9) {
            for (int gz = -half; gz < half; gz += 9) {
                int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 9), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 9);
                if (keepOut.test(x, z) || Math.abs(x) > half - 4 || Math.abs(z) > half - 4) {
                    continue;
                }
                if (hash3(x, 3, z, seed + 20) > 0.62) {
                    continue;
                }
                int g = ground.applyAsInt(x, z);
                if (g < sea - 3) {
                    continue;
                }
                tree(w, x, g, z, seed, sea);
            }
        }
    }
}
