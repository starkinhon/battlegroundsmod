package com.starkinhon.battlegroundsmod.world;

import static com.starkinhon.battlegroundsmod.world.JjkGen.floor;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hash3;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hashInt;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hypot;
import static com.starkinhon.battlegroundsmod.world.JjkGen.round;
import static com.starkinhon.battlegroundsmod.world.JjkGen.sin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.world.NarutoStructGen.Rng;

// Shibuya (Stark 2026-11-12; port released 2026-11-13): line-by-line port of the approved preview's generator
// (forja/jujutsu-shibuya/gen.js), the same draws and the same hashes, everything out of the world's coordinates. A
// slightly crooked street grid (wide avenues with trees and guard rails, narrow streets with light poles and wires),
// alleys cutting the blocks, buildings glued to each other, each one drawn whole (facade, color, height, setback, shop
// with awning and sign, vertical signs with "letters", rooftop tanks, ACs, billboards and antennas, lit windows), the
// big scramble crossings with diagonal stripes and screens, rare skyscrapers, cursed buildings (broken windows, black
// stains, purple veins, boarded entrance), and the metro under the main avenue with its station, train and tunnels,
// one of them taken by the curses. Heights are relative to the street (y = 0 is the street's top).
//
// The types go past 255 (a lit window is its glass type + LIT, + COOL when the light is cold office white), so this
// generator has its own Vox with int types; a force to air is kept as CARVE like TemploGen.Vox.
public final class ShibuyaGen {
    public static final int AIR = 0, STONE = 1, SOIL = 2, ASPHALT = 3, LANE = 4, YELLOW = 5, ZEBRA = 6, SIDEWALK = 7,
            SIDEWALK2 = 8, TACTILE = 9, CURB = 10, LOT = 11, ALLEY = 12, GRAVEL = 13, PARK_LINE = 14, WALL0 = 15,
            WALL1 = 16, WALL2 = 17, WALL3 = 18, WALL4 = 19, WALL5 = 20, WALL6 = 21, WALL7 = 22, GLASS0 = 23,
            GLASS1 = 24, GLASS2 = 25, GLASS3 = 26, WIN = 27, MULLION = 28, SLAB = 29, CORNER = 30, ROOF = 31,
            PARAPET = 32, SHOP = 33, DOOR = 34, AWNING0 = 35, AWNING1 = 36, AWNING2 = 37, AWNING3 = 38, SIGN0 = 39,
            SIGN1 = 40, SIGN2 = 41, SIGN3 = 42, SIGN4 = 43, SIGN5 = 44, SIGN6 = 45, SIGN7 = 46, GLYPH = 47,
            SIGN_FRAME = 48, SCREEN = 49, METAL = 50, METAL_DARK = 51, TANK = 52, AVIATION = 53, POLE = 54,
            CABLE = 55, LAMP = 56, TRAFFIC_BOX = 57, TRAFFIC_R = 58, TRAFFIC_G = 59, RAILING = 60, PLANTER = 61,
            TRUNK = 62, LEAVES = 63, LEAVES2 = 64, VENDING = 65, VEND0 = 66, VEND1 = 67, VEND2 = 68, VEND3 = 69,
            TORII = 70, TORII_TOP = 71, SHRINE_WOOD = 72, SHRINE_ROOF = 73, LANTERN = 74, CURSE_STAIN = 75,
            CURSE_VEIN = 76, CURSE_GLOW = 77, CURSE_FLOOR = 78, BOARD = 79, TAPE = 80, TALISMAN = 81, DEAD_SIGN = 82,
            TILE_WALL = 83, TILE_LINE = 84, TILE_FLOOR = 85, CEIL = 86, CEIL_LIGHT = 87, PILLAR = 88, GATE = 89,
            EDGE = 90, STAIR = 91, BENCH = 92, METRO_SIGN = 93, SIGN_DARK = 94, BALLAST = 95, RAIL = 96, TUNNEL = 97,
            TUNNEL_LIGHT = 98, CROWN_LED = 99, FIN = 100, DECK_GLASS = 101, LOUVER = 102, TRAIN = 103,
            TRAIN_STRIPE = 104, TRAIN_WIN = 105, TRAIN_DOOR = 106, TRAIN_ROOF = 107, TRAIN_END = 108,
            TRAIN_UNDER = 109, TRAIN_FRONT = 110, HEADLIGHT = 111;
    // The power line (a crossarm, PowerLineBlock): ARM + facing toward its pole (0 north, 1 east, 2 south, 3 west),
    // + 4 with a cable, + 8 when it reaches into the pole's block. Stark 2026-11-14: the wires were blocks and fences.
    public static final int ARM = 112;
    public static final int LIT = 256, COOL = 512;
    public static final int CARVE = -1;
    public static final int CELL = 64;

    private ShibuyaGen() {
    }

    public static int base(int t) {
        return t & 255;
    }

    // ---------------------------------------------------------------- voxels

    public static final class Vox {
        final Map<Long, Integer> m = new HashMap<>();
        final int lx, lz, hx, hz;

        public Vox() {
            this(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        public Vox(int lx, int lz, int hx, int hz) {
            this.lx = lx;
            this.lz = lz;
            this.hx = hx;
            this.hz = hz;
        }

        public boolean in(int x, int z) {
            return x >= lx && x <= hx && z >= lz && z <= hz;
        }

        public int get(int x, int y, int z) {
            Integer t = m.get(LeafTreeGen.Keys.key(x, y, z));
            return t == null || t == CARVE ? 0 : t;
        }

        public void set(int x, int y, int z, int t) {
            if (!in(x, z)) {
                return;
            }
            long k = LeafTreeGen.Keys.key(x, y, z);
            Integer there = m.get(k);
            if (there == null || there == CARVE) {
                m.put(k, t);
            }
        }

        public void force(int x, int y, int z, int t) {
            if (!in(x, z)) {
                return;
            }
            m.put(LeafTreeGen.Keys.key(x, y, z), t == AIR ? CARVE : t);
        }

        // Packed as x, y, z, type per voxel, CARVE included.
        public int[] pack() {
            int[] out = new int[m.size() * 4];
            int i = 0;
            for (Map.Entry<Long, Integer> e : m.entrySet()) {
                long k = e.getKey();
                out[i++] = LeafTreeGen.Keys.x(k);
                out[i++] = LeafTreeGen.Keys.y(k);
                out[i++] = LeafTreeGen.Keys.z(k);
                out[i++] = e.getValue();
            }
            return out;
        }

        public void each(Each f) {
            for (Map.Entry<Long, Integer> e : m.entrySet()) {
                long k = e.getKey();
                f.accept(LeafTreeGen.Keys.x(k), LeafTreeGen.Keys.y(k), LeafTreeGen.Keys.z(k), e.getValue());
            }
        }
    }

    public interface Each {
        void accept(int x, int y, int z, int t);
    }

    private static double smooth(double a) {
        return a * a * (3 - 2 * a);
    }

    public static double valueNoise3(int x, int y, int z, int seed, double scale) {
        double fx = x / scale, fy = y / scale, fz = z / scale;
        int ix = floor(fx), iy = floor(fy), iz = floor(fz);
        double tx = smooth(fx - ix), ty = smooth(fy - iy), tz = smooth(fz - iz);
        double x00 = l(hash3(ix, iy, iz, seed), hash3(ix + 1, iy, iz, seed), tx);
        double x10 = l(hash3(ix, iy + 1, iz, seed), hash3(ix + 1, iy + 1, iz, seed), tx);
        double x01 = l(hash3(ix, iy, iz + 1, seed), hash3(ix + 1, iy, iz + 1, seed), tx);
        double x11 = l(hash3(ix, iy + 1, iz + 1, seed), hash3(ix + 1, iy + 1, iz + 1, seed), tx);
        return l(l(x00, x10, ty), l(x01, x11, ty), tz);
    }

    private static double l(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ---------------------------------------------------------------- streets

    // A street of the axis: 'x' runs along x and sits at z = c; 'z' runs along z and sits at x = c. Lanes relative to
    // c: road [r0, r1], sidewalks [s0, r0 - 1] and [r1 + 1, s1]. Street 0 of both axes is always an avenue.
    public record Street(int i, int c, boolean avenue, int r0, int r1, int s0, int s1) {
    }

    public record At(Street s, int d) {
    }

    public static Street street(char axis, int i, int seed) {
        int a = axis == 'x' ? 1 : 2;
        boolean avenue = i == 0 || hash3(i, a, 8, seed) < 0.34;
        int c = i * CELL + (i == 0 ? 0 : round((hash3(i, a, 7, seed) - 0.5) * 18));
        return avenue ? new Street(i, c, true, -6, 5, -11, 10) : new Street(i, c, false, -3, 2, -6, 5);
    }

    // The street of the axis that takes coordinate v (or null), with d = v - c.
    public static At streetAt(char axis, int v, int seed) {
        int k = round(v / (double) CELL);
        for (int i = k - 1; i <= k + 1; i++) {
            Street s = street(axis, i, seed);
            int d = v - s.c();
            if (d >= s.s0() && d <= s.s1()) {
                return new At(s, d);
            }
        }
        return null;
    }

    static boolean inRoad(At r) {
        return r != null && r.d() >= r.s().r0() && r.d() <= r.s().r1();
    }

    // The sidewalk's row counting from the curb (0) inwards.
    static int sideRow(At r) {
        return r.d() > r.s().r1() ? r.d() - r.s().r1() - 1 : r.s().r0() - 1 - r.d();
    }

    // Is the crossing of streets i ('x') and j ('z') a big scramble crossing, with diagonal stripes and screens?
    public static boolean scramble(int i, int j, int seed) {
        if (i == 0 && j == 0) {
            return true;
        }
        return street('x', i, seed).avenue() && street('z', j, seed).avenue() && hash3(i, j, 9, seed) < 0.3;
    }

    // The ground's top (y = 0) of the column, or -1 if it is a lot (inside a block).
    public static int groundAt(int x, int z, int seed) {
        At X = streetAt('x', z, seed), Z = streetAt('z', x, seed);
        boolean rx = inRoad(X), rz = inRoad(Z);
        if (rx && rz) {
            if (scramble(X.s().i(), Z.s().i(), seed)) {
                double p = X.d() - (X.s().r0() + X.s().r1()) / 2.0, q = Z.d() - (Z.s().r0() + Z.s().r1()) / 2.0;
                if ((Math.abs(p - q) <= 1.5 || Math.abs(p + q) <= 1.5) && ((floor(p + q) & 1) == 0)) {
                    return ZEBRA;
                }
            }
            return ASPHALT;
        }
        if (rx || rz) {
            At R = rx ? X : Z, O = rx ? Z : X;
            int along = rx ? x : z;
            if (O != null) {
                int k = sideRow(O);
                if (k >= 1) {
                    return ((R.d() - R.s().r0()) & 1) == 0 ? ZEBRA : ASPHALT;
                }
                return LANE;
            }
            int d = R.d();
            if (R.s().avenue()) {
                if (d == 0) {
                    return YELLOW;
                }
                if ((d == -3 || d == 3) && (((along % 8) + 8) % 8) < 4) {
                    return LANE;
                }
                if (d == R.s().r0() || d == R.s().r1()) {
                    return LANE;
                }
            } else if (d == 0 && (((along % 6) + 6) % 6) < 3) {
                return LANE;
            }
            return ASPHALT;
        }
        if (X != null || Z != null) {
            int k = Math.min(X != null ? sideRow(X) : 99, Z != null ? sideRow(Z) : 99);
            boolean av = (X != null && X.s().avenue()) || (Z != null && Z.s().avenue());
            if (k == 0) {
                return CURB;
            }
            if (av && k == 2 && !(X != null && Z != null)) {
                return TACTILE;
            }
            return hash3(x, 0, z, seed + 3) < 0.28 ? SIDEWALK2 : SIDEWALK;
        }
        return -1;
    }

    // ---------------------------------------------------------------- blocks and lots

    // Sides in the preview's order: n, s, w, e.
    static final char[] IDS = {'n', 's', 'w', 'e'};
    static final int N = 0, S = 1, W = 2, E = 3;

    public static final class Lot {
        public int x0, x1, z0, z1;
        boolean[] alley = new boolean[4];
        public boolean[] front = new boolean[4];
        public boolean avenue;
        public int seed;
        public String kind;
        public boolean[] screen; // null when no screen

        Lot(int x0, int x1, int z0, int z1, boolean[] alley) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.alley = alley;
        }
    }

    public static final class Plan {
        public final int x0, x1, z0, z1;
        public final List<int[]> alleys = new ArrayList<>();
        public final List<Lot> lots = new ArrayList<>();
        public boolean[] towerAv; // null when not a tower block
        public int towerSeed;

        Plan(int x0, int x1, int z0, int z1) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
        }
    }

    private static boolean[] with(boolean[] a, int i, boolean v) {
        boolean[] b = a.clone();
        b[i] = v;
        return b;
    }

    // The block between streets 'x' i and i + 1 and streets 'z' j and j + 1: the inner rectangle, the sides facing an
    // avenue, sometimes an alley, and the lots (halved until they are the size of a building).
    public static Plan blockPlan(int i, int j, int seed) {
        Street n = street('x', i, seed), s = street('x', i + 1, seed), w = street('z', j, seed), e = street('z', j + 1, seed);
        Plan plan = new Plan(w.c() + w.s1() + 1, e.c() + e.s0() - 1, n.c() + n.s1() + 1, s.c() + s.s0() - 1);
        boolean[] av = {n.avenue(), s.avenue(), w.avenue(), e.avenue()};
        int Wd = plan.x1 - plan.x0 + 1, D = plan.z1 - plan.z0 + 1;
        if (towerBlock(i, j, seed) && Wd >= 30 && D >= 30) {
            plan.towerAv = av;
            plan.towerSeed = hashInt(i, j, 29, seed);
            return plan;
        }
        Rng rng = new Rng(hashInt(i, j, 11, seed));
        List<Lot> parts = new ArrayList<>();
        parts.add(new Lot(plan.x0, plan.x1, plan.z0, plan.z1, new boolean[4]));
        if (rng.next() < 0.5 && Math.max(Wd, D) >= 32) {
            parts.clear();
            if (Wd >= D) {
                int ax = plan.x0 + floor(Wd * rng.range(0.35, 0.65));
                plan.alleys.add(new int[] {ax, ax + 2, plan.z0, plan.z1});
                parts.add(new Lot(plan.x0, ax - 1, plan.z0, plan.z1, with(new boolean[4], E, true)));
                parts.add(new Lot(ax + 3, plan.x1, plan.z0, plan.z1, with(new boolean[4], W, true)));
            } else {
                int az = plan.z0 + floor(D * rng.range(0.35, 0.65));
                plan.alleys.add(new int[] {plan.x0, plan.x1, az, az + 2});
                parts.add(new Lot(plan.x0, plan.x1, plan.z0, az - 1, with(new boolean[4], S, true)));
                parts.add(new Lot(plan.x0, plan.x1, az + 3, plan.z1, with(new boolean[4], N, true)));
            }
        }
        for (Lot p : parts) {
            split(p, rng, plan.lots);
        }
        for (Lot lt : plan.lots) {
            lt.front[N] = lt.z0 == plan.z0 || lt.alley[N];
            lt.front[S] = lt.z1 == plan.z1 || lt.alley[S];
            lt.front[W] = lt.x0 == plan.x0 || lt.alley[W];
            lt.front[E] = lt.x1 == plan.x1 || lt.alley[E];
            lt.avenue = (lt.z0 == plan.z0 && av[N]) || (lt.z1 == plan.z1 && av[S]) || (lt.x0 == plan.x0 && av[W])
                    || (lt.x1 == plan.x1 && av[E]);
            lt.seed = hashInt(lt.x0, lt.z0, 13, seed);
            Rng r = new Rng(lt.seed);
            int area = (lt.x1 - lt.x0 + 1) * (lt.z1 - lt.z0 + 1);
            lt.kind = area <= 150 && r.next() < 0.16 ? "shrine" : area <= 260 && r.next() < 0.07 ? "parking" : "building";
        }
        // the four corner lots of a big crossing get a screen
        Street[][] corners = {{n, w}, {n, e}, {s, w}, {s, e}};
        int[][] ab = {{N, W}, {N, E}, {S, W}, {S, E}};
        for (int q = 0; q < 4; q++) {
            Street zs = corners[q][0], xs = corners[q][1];
            int a = ab[q][0], b = ab[q][1];
            if (!scramble(zs.i(), xs.i(), seed)) {
                continue;
            }
            int cx = b == W ? plan.x0 : plan.x1, cz = a == N ? plan.z0 : plan.z1;
            Lot lot = null;
            for (Lot lt : plan.lots) {
                if ((b == W ? lt.x0 == cx : lt.x1 == cx) && (a == N ? lt.z0 == cz : lt.z1 == cz)) {
                    lot = lt;
                    break;
                }
            }
            if (lot != null) {
                lot.kind = "building";
                lot.screen = new boolean[4];
                lot.screen[a] = true;
                lot.screen[b] = true;
                lot.avenue = true;
            }
        }
        return plan;
    }

    private static void split(Lot p, Rng rng, List<Lot> lots) {
        int pw = p.x1 - p.x0 + 1, pd = p.z1 - p.z0 + 1;
        if (Math.max(pw, pd) <= 20 || Math.min(pw, pd) < 8) {
            lots.add(p);
            return;
        }
        if (pw >= pd) {
            int cx = p.x0 + Math.max(7, Math.min(pw - 8, floor(pw * rng.range(0.35, 0.65))));
            split(new Lot(p.x0, cx - 1, p.z0, p.z1, with(p.alley, E, false)), rng, lots);
            split(new Lot(cx, p.x1, p.z0, p.z1, with(p.alley, W, false)), rng, lots);
        } else {
            int cz = p.z0 + Math.max(7, Math.min(pd - 8, floor(pd * rng.range(0.35, 0.65))));
            split(new Lot(p.x0, p.x1, p.z0, cz - 1, with(p.alley, S, false)), rng, lots);
            split(new Lot(p.x0, p.x1, cz, p.z1, with(p.alley, N, false)), rng, lots);
        }
    }

    // One block in each 3 x 3 (at a drawn place in it), with 55% chance, and never at a corner of a big crossing.
    public static boolean towerBlock(int i, int j, int seed) {
        int si = Math.floorDiv(i, 3), sj = Math.floorDiv(j, 3);
        if (hash3(si, sj, 27, seed) >= 0.55) {
            return false;
        }
        int ti = si * 3 + floor(hash3(si, sj, 28, seed) * 3), tj = sj * 3 + floor(hash3(sj, si, 28, seed) * 3);
        if (ti != i || tj != j) {
            return false;
        }
        return !scramble(i, j, seed) && !scramble(i + 1, j, seed) && !scramble(i, j + 1, seed) && !scramble(i + 1, j + 1, seed);
    }

    // ---------------------------------------------------------------- skyscraper

    private static final String[] SHAPES = {"chamfer", "steps", "taper"};

    public static void skyscraper(Vox w, Plan plan, int seed) {
        Rng rng = new Rng(plan.towerSeed);
        int W0 = plan.x1 - plan.x0 + 1, D = plan.z1 - plan.z0 + 1;
        int podFloors = rng.nextInt(3, 5), podTop = 1 + 5 + podFloors * 4 - 1;
        int podWall = WALL0 + rng.nextInt(0, 7), podSign = SIGN0 + rng.nextInt(0, 7);
        int bx0 = plan.x0 + 1, bx1 = plan.x1 - 1, bz0 = plan.z0 + 1, bz1 = plan.z1 - 1;
        for (int x = plan.x0; x <= plan.x1; x++) {
            for (int z = plan.z0; z <= plan.z1; z++) {
                w.force(x, 0, z, SIDEWALK2);
            }
        }
        for (int y = 1; y <= podTop; y++) {
            int yRel = y - 1;
            for (int x = bx0; x <= bx1; x++) {
                for (int z = bz0; z <= bz1; z++) {
                    boolean edge = x == bx0 || x == bx1 || z == bz0 || z == bz1;
                    if (!edge) {
                        if (y == podTop) {
                            w.force(x, y, z, ROOF);
                        }
                        continue;
                    }
                    int f = z == bz0 ? 0 : z == bz1 ? 1 : x == bx0 ? 2 : 3;
                    int u = f < 2 ? x - bx0 : z - bz0, len = f < 2 ? bx1 - bx0 + 1 : bz1 - bz0 + 1;
                    boolean corner = u == 0 || u == len - 1;
                    int t;
                    if (corner) {
                        t = CORNER;
                    } else if (yRel < 5) {
                        t = yRel == 4 ? (glyph(u, 1, plan.towerSeed + f) && u > 2 && u < len - 3 ? GLYPH : podSign)
                                : (Math.abs(u - Math.floorDiv(len, 2)) <= 1 && yRel <= 2 ? DOOR : SHOP);
                    } else {
                        int rr = (yRel - 5) & 3;
                        t = rr == 0 ? podWall : (u % 6 == 0 ? FIN : SHOP);
                    }
                    if (y == podTop) {
                        t = PARAPET;
                    }
                    w.force(x, y, z, t);
                }
            }
        }
        // the big screen on one of the base's fronts facing an avenue (or the first)
        List<Integer> avs = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            if (plan.towerAv[k]) {
                avs.add(k);
            }
        }
        int sf = avs.isEmpty() ? N : avs.get(rng.nextInt(0, avs.size() - 1));
        boolean horiz = sf == N || sf == S;
        int len = horiz ? W0 - 2 : D - 2, sw = Math.min(22, len - 6), sh = Math.min(podTop - 7, 12), u0 = rng.nextInt(3, len - 3 - sw);
        for (int u = u0; u < u0 + sw; u++) {
            for (int y = 7; y < 7 + sh; y++) {
                int x = horiz ? bx0 + u : (sf == W ? bx0 - 1 : bx1 + 1), z = horiz ? (sf == N ? bz0 - 1 : bz1 + 1) : bz0 + u;
                w.force(x, y, z, u == u0 || u == u0 + sw - 1 || y == 7 || y == 6 + sh ? SIGN_FRAME : SCREEN);
            }
        }

        // the tower
        int Sz = Math.max(16, Math.min(26, Math.min(W0, D) - 8));
        double hx0 = Sz / 2.0, hz0 = (Sz - 2 * rng.nextInt(0, 2)) / 2.0;
        int cx = Math.floorDiv(bx0 + bx1, 2) + rng.nextInt(-2, 2);
        int cz = Math.floorDiv(bz0 + bz1, 2) + rng.nextInt(-2, 2);
        int top = rng.nextInt(120, 175), deck = top - rng.nextInt(8, 11);
        String shape = SHAPES[rng.nextInt(0, 2)];
        int cham = rng.nextInt(3, 5), glass = GLASS0 + rng.nextInt(0, 3);
        int step0 = podTop + floor((top - podTop) * rng.range(0.45, 0.6));
        int step1 = podTop + floor((top - podTop) * rng.range(0.72, 0.85));
        int finEvery = rng.nextInt(4, 6), mech = rng.nextInt(26, 34);
        Tower tw = new Tower(shape, hx0, hz0, step0, step1, podTop, top, cham);
        int R = (int) Math.ceil(Math.max(hx0, hz0)) + 1;
        for (int y = podTop + 1; y <= top; y++) {
            int yRel = y - podTop - 1, f = yRel >> 2, rr = yRel & 3;
            boolean mechBand = yRel % mech >= mech - 2 && y < deck - 2;
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (!tw.inside(dx, dz, y)) {
                        continue;
                    }
                    boolean shell = !tw.inside(dx + 1, dz, y) || !tw.inside(dx - 1, dz, y) || !tw.inside(dx, dz + 1, y)
                            || !tw.inside(dx, dz - 1, y);
                    int x = cx + dx, z = cz + dz;
                    if (!shell) {
                        if (y == top || y == deck || !tw.inside(dx, dz, y + 1)) {
                            w.force(x, y, z, ROOF);
                        }
                        continue;
                    }
                    int u = Math.abs(dx) >= Math.abs(dz) ? dz + 100 : dx + 100;
                    int t;
                    if (y > deck) {
                        t = y == top ? CROWN_LED : (y == deck + 1 ? SLAB : DECK_GLASS);
                    } else if (y == deck) {
                        t = CROWN_LED;
                    } else if (mechBand) {
                        t = LOUVER;
                    } else if (u % finEvery == 0) {
                        t = FIN;
                    } else if (rr == 0) {
                        t = SLAB;
                    } else {
                        t = glass;
                        double hh = hash3(Math.floorDiv(u, finEvery) + 7 * (dx > 0 ? 1 : 0) + 13 * (dz > 0 ? 1 : 0), f, 5, plan.towerSeed);
                        if (hh < 0.55) {
                            t |= LIT | (hash3(u, f, 7, plan.towerSeed) < 0.85 ? COOL : 0);
                        }
                    }
                    w.force(x, y, z, t);
                }
            }
        }
        // the deck: only the glass edge rises above the deck's floor; the middle is open up to the top (crown)
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (!tw.inside(dx, dz, deck)) {
                    continue;
                }
                boolean shell = !tw.inside(dx + 1, dz, deck) || !tw.inside(dx - 1, dz, deck) || !tw.inside(dx, dz + 1, deck)
                        || !tw.inside(dx, dz - 1, deck);
                for (int y = deck + 1; y <= top; y++) {
                    if (shell) {
                        continue;
                    }
                    double hx = tw.hx(deck), hz = tw.hz(deck);
                    boolean core = Math.abs(dx + 0.5) <= hx - 4 && Math.abs(dz + 0.5) <= hz - 4;
                    if (!core) {
                        w.force(cx + dx, y, cz + dz, AIR);
                    } else if (y == top) {
                        boolean hX = Math.abs(dx + 0.5) <= 2.5 && Math.abs(dz + 0.5) <= 3
                                && (Math.abs(dx + 0.5) >= 2 || Math.abs(dz + 0.5) <= 0.5);
                        boolean ring = Math.abs(hypot(dx + 0.5, dz + 0.5) - (Math.min(hx, hz) - 5)) < 0.6;
                        w.force(cx + dx, y, cz + dz, hX || ring ? PARK_LINE : ROOF);
                    } else {
                        w.force(cx + dx, y, cz + dz, Math.abs(dx + 0.5) >= hx - 4.5 || Math.abs(dz + 0.5) >= hz - 4.5 ? DECK_GLASS : WALL4);
                    }
                }
                if (!shell) {
                    double hx = tw.hx(deck), hz = tw.hz(deck);
                    boolean core = Math.abs(dx + 0.5) <= hx - 4 && Math.abs(dz + 0.5) <= hz - 4;
                    boolean nearShell = !tw.inside(dx + 2, dz, deck) || !tw.inside(dx - 2, dz, deck) || !tw.inside(dx, dz + 2, deck)
                            || !tw.inside(dx, dz - 2, deck);
                    if (!core && nearShell) {
                        w.force(cx + dx, deck + 1, cz + dz, DECK_GLASS);
                    }
                }
            }
        }
        // the glass shell above the deck goes: the deck is open, only the rail and the LED crown on the middle house
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (!tw.inside(dx, dz, deck)) {
                    continue;
                }
                boolean shell = !tw.inside(dx + 1, dz, deck) || !tw.inside(dx - 1, dz, deck) || !tw.inside(dx, dz + 1, deck)
                        || !tw.inside(dx, dz - 1, deck);
                if (!shell) {
                    continue;
                }
                for (int y = deck + 2; y <= top; y++) {
                    w.force(cx + dx, y, cz + dz, AIR);
                }
                w.force(cx + dx, deck + 1, cz + dz, DECK_GLASS);
            }
        }
        // the antenna with red lights on the middle house, and lights at the deck's corners
        int mast = rng.nextInt(14, 26);
        for (int y = top + 1; y <= top + mast; y++) {
            w.force(cx, y, cz, y % 6 == 0 ? AVIATION : POLE);
        }
        w.force(cx, top + mast + 1, cz, AVIATION);
        double hx = tw.hx(deck), hz = tw.hz(deck);
        int[][] sg = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] q : sg) {
            double inset = shape.equals("steps") ? 1 : cham / 2.0;
            int ex = round(q[0] * (hx - inset) - 0.5), ez = round(q[1] * (hz - inset) - 0.5);
            w.force(cx + ex, deck + 2, cz + ez, AVIATION);
        }
    }

    private record Tower(String shape, double hx0, double hz0, int step0, int step1, int podTop, int top, int cham) {
        int k(int y) {
            if (shape.equals("steps")) {
                return (y >= step0 ? 2 : 0) + (y >= step1 ? 2 : 0);
            }
            if (shape.equals("taper")) {
                return floor(3.0 * (y - podTop) / (top - podTop));
            }
            return 0;
        }

        double hx(int y) {
            return hx0 - k(y);
        }

        double hz(int y) {
            return hz0 - k(y);
        }

        boolean inside(int dx, int dz, int y) {
            double hx = hx(y), hz = hz(y);
            int c = shape.equals("steps") ? 2 : cham;
            double ax = Math.abs(dx + 0.5), az = Math.abs(dz + 0.5);
            return ax <= hx && az <= hz && ax + az <= hx + hz - c;
        }
    }

    // ---------------------------------------------------------------- building

    static final int GLYPH_ROWS = 3;

    // A sign's "letter": lit pixels in a 3 x 3 grid per character, drawn per character (no sign repeats).
    public static boolean glyph(int col, int row, int seed) {
        int ch = Math.floorDiv(row, GLYPH_ROWS), cy = row % GLYPH_ROWS;
        if (cy == GLYPH_ROWS - 1) {
            return false;
        }
        return hash3(col, ch * 7 + cy, 3, seed) < 0.55;
    }

    private static final String[] STYLES = {"glass", "band", "tile", "band", "tile"};
    private static final int[][] NORMALS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private record Facade(Lot lot, String style, boolean pencil, boolean cursed, int wall, int glass, double lit, double cool,
            int signColor, int awning) {
        int window(int sideIdx, int bay, int f) {
            double hh = hash3(sideIdx * 97 + bay, f, 5, lot.seed);
            int t = style.equals("glass") ? glass : WIN;
            if (cursed) {
                if (hash3(sideIdx * 97 + bay, f, 6, lot.seed) < 0.45) {
                    return 0;
                }
                return hh < 0.12 ? CURSE_GLOW : t;
            }
            if (hh < lit) {
                t |= LIT | (hash3(sideIdx * 97 + bay, f, 7, lot.seed) < cool ? COOL : 0);
            }
            return t;
        }

        int face(int sideIdx, int u, int len, int yRel, boolean front) {
            boolean corner = u == 0 || u == len - 1;
            if (yRel < 5) {
                if (!front) {
                    return wall;
                }
                if (corner) {
                    return style.equals("glass") ? CORNER : wall;
                }
                if (yRel == 4) {
                    if (cursed) {
                        return DEAD_SIGN;
                    }
                    return glyph(u, 1, lot.seed + sideIdx) && u > 1 && u < len - 2 ? GLYPH : signColor;
                }
                if (cursed) {
                    if (yRel == 1 && ((u + sideIdx) % 3) != 0) {
                        return TAPE;
                    }
                    return hash3(u, yRel, sideIdx, lot.seed) < 0.12 ? TALISMAN : BOARD;
                }
                boolean door = Math.abs(u - Math.floorDiv(len, 2)) <= 0 || (len > 12 && Math.abs(u - Math.floorDiv(len, 2)) == 1);
                if (door && yRel <= 2) {
                    return DOOR;
                }
                if (yRel == 3) {
                    return awning != 0 ? wall : SHOP;
                }
                return SHOP;
            }
            int yy = yRel - 5, f = yy >> 2, r = yy & 3;
            if (corner) {
                return style.equals("glass") ? CORNER : wall;
            }
            if (style.equals("glass")) {
                if (r == 0) {
                    return SLAB;
                }
                if (u % 3 == 0) {
                    return MULLION;
                }
                return window(sideIdx, u / 3, f);
            }
            if (style.equals("band")) {
                if (r <= 1) {
                    return wall;
                }
                if (u % 4 == 0) {
                    return MULLION;
                }
                return window(sideIdx, u / 4, f);
            }
            if (style.equals("tile")) {
                int m = u % 4;
                if ((m == 1 || m == 2) && (r == 1 || r == 2)) {
                    return window(sideIdx, u >> 2, f);
                }
                return wall;
            }
            if (!front) {
                return wall;
            }
            if (r == 0) {
                return wall;
            }
            return window(sideIdx, 0, f);
        }
    }

    public static void building(Vox w, Lot lot, int seed) {
        Rng rng = new Rng(lot.seed);
        int Wd = lot.x1 - lot.x0 + 1, D = lot.z1 - lot.z0 + 1;
        int frontLen = Math.min(lot.front[N] || lot.front[S] ? Wd : 99, lot.front[W] || lot.front[E] ? D : 99);
        boolean pencil = frontLen <= 10 && rng.next() < 0.75;
        int floors = lot.avenue ? rng.nextInt(5, 16) : rng.nextInt(2, 9);
        if (pencil) {
            floors = rng.nextInt(4, 10);
        }
        if (lot.screen != null) {
            floors = Math.max(floors, rng.nextInt(8, 13));
        }
        String style = pencil ? "pencil" : STYLES[rng.nextInt(0, 4)];
        int wall = WALL0 + rng.nextInt(0, 7);
        int glass = GLASS0 + rng.nextInt(0, 3);
        double lit = rng.range(0.18, 0.6);
        double cool = style.equals("glass") ? 0.75 : rng.range(0.1, 0.5);
        int signColor = SIGN0 + rng.nextInt(0, 7);
        int awning = rng.next() < 0.5 ? AWNING0 + rng.nextInt(0, 3) : 0;
        boolean cursed = lot.screen == null && rng.next() < 0.16;
        int H = 5 + floors * 4;
        int y0 = 1, top = y0 + H - 1;
        int setFloor = floors >= 9 && Wd >= 14 && D >= 14 && rng.next() < 0.55 ? rng.nextInt(floor(floors * 0.55), floors - 2) : -1;
        int setY = setFloor < 0 ? 1000000000 : y0 + 5 + setFloor * 4;
        Facade fc = new Facade(lot, style, pencil, cursed, wall, glass, lit, cool, signColor, awning);

        for (int y = y0; y <= top; y++) {
            int inset = y >= setY ? 2 : 0;
            int bx0 = lot.x0 + inset, bx1 = lot.x1 - inset, bz0 = lot.z0 + inset, bz1 = lot.z1 - inset;
            int yRel = y - y0;
            for (int si = 0; si < 4; si++) {
                boolean horiz = NORMALS[si][1] != 0;
                int len = horiz ? bx1 - bx0 + 1 : bz1 - bz0 + 1;
                boolean front = lot.front[si] && inset == 0;
                for (int u = 0; u < len; u++) {
                    int x = horiz ? bx0 + u : (si == W ? bx0 : bx1);
                    int z = horiz ? (si == N ? bz0 : bz1) : bz0 + u;
                    int t = fc.face(si, u, len, yRel, front || (pencil && lot.front[si]));
                    w.force(x, y, z, t);
                }
            }
            if (y == setY - 1 || y == top) {
                int in = y == top ? inset : 0;
                for (int x = lot.x0 + in; x <= lot.x1 - in; x++) {
                    for (int z = lot.z0 + in; z <= lot.z1 - in; z++) {
                        if (y == setY - 1 && x >= lot.x0 + 2 && x <= lot.x1 - 2 && z >= lot.z0 + 2 && z <= lot.z1 - 2) {
                            continue;
                        }
                        boolean edge = x == lot.x0 + in || x == lot.x1 - in || z == lot.z0 + in || z == lot.z1 - in;
                        if (!edge || y == setY - 1) {
                            w.force(x, y, z, cursed && hash3(x, y, z, lot.seed) < 0.3 ? CURSE_STAIN : ROOF);
                        }
                        if (y == setY - 1) {
                            boolean e2 = x == lot.x0 || x == lot.x1 || z == lot.z0 || z == lot.z1;
                            if (e2) {
                                w.force(x, y + 1, z, PARAPET);
                            }
                        }
                    }
                }
            }
            if (cursed && yRel >= 5 && ((yRel - 5) & 3) == 0) {
                for (int x = bx0 + 1; x < bx1; x++) {
                    for (int z = bz0 + 1; z < bz1; z++) {
                        w.force(x, y, z, CURSE_FLOOR);
                    }
                }
            }
        }
        // the parapet on top
        int ti = top >= setY ? 2 : 0;
        for (int x = lot.x0 + ti; x <= lot.x1 - ti; x++) {
            for (int z = lot.z0 + ti; z <= lot.z1 - ti; z++) {
                if (x == lot.x0 + ti || x == lot.x1 - ti || z == lot.z0 + ti || z == lot.z1 - ti) {
                    w.force(x, top, z, style.equals("glass") ? CORNER : wall);
                    w.force(x, top + 1, z, PARAPET);
                }
            }
        }

        // awning: 1 block out over the shop windows
        if (awning != 0 && !cursed) {
            for (int si = 0; si < 4; si++) {
                if (!lot.front[si]) {
                    continue;
                }
                boolean horiz = NORMALS[si][1] != 0;
                int a0 = horiz ? lot.x0 : lot.z0, a1 = horiz ? lot.x1 : lot.z1;
                for (int a = a0 + 1; a < a1; a++) {
                    int x = horiz ? a : (si == W ? lot.x0 - 1 : lot.x1 + 1), z = horiz ? (si == N ? lot.z0 - 1 : lot.z1 + 1) : a;
                    w.force(x, y0 + 3, z, awning);
                }
            }
        }

        // the screen on the big crossing
        if (lot.screen != null) {
            for (int si = 0; si < 4; si++) {
                if (!lot.screen[si]) {
                    continue;
                }
                boolean horiz = NORMALS[si][1] != 0;
                int len = horiz ? Wd : D;
                int sw = Math.min(16, len - 3), sh = Math.min(11, floor(sw * 0.7));
                boolean nearLow = horiz ? lot.screen[W] : lot.screen[N];
                int u0 = nearLow ? 1 : len - 1 - sw;
                for (int u = u0; u < u0 + sw; u++) {
                    for (int y = y0 + 8; y < y0 + 8 + sh; y++) {
                        int x = horiz ? lot.x0 + u : (si == W ? lot.x0 - 1 : lot.x1 + 1);
                        int z = horiz ? (si == N ? lot.z0 - 1 : lot.z1 + 1) : lot.z0 + u;
                        boolean rim = u == u0 || u == u0 + sw - 1 || y == y0 + 8 || y == y0 + 8 + sh - 1;
                        w.force(x, y, z, rim ? SIGN_FRAME : SCREEN);
                    }
                }
            }
        }

        // vertical signs (tate-kanban): a panel 1 thick sticking out of the facade, with "letters"
        int nSigns = cursed || floors < 3 ? 0 : pencil ? (rng.next() < 0.9 ? 1 : 0) : (rng.next() < 0.75 ? (rng.next() < 0.35 ? 2 : 1) : 0);
        Set<String> usedSign = new HashSet<>();
        for (int sg = 0; sg < nSigns; sg++) {
            List<Integer> opts = new ArrayList<>();
            for (int si = 0; si < 4; si++) {
                if (lot.front[si] && !(lot.screen != null && lot.screen[si])) {
                    opts.add(si);
                }
            }
            if (!opts.isEmpty()) {
                int si = opts.get(rng.nextInt(0, opts.size() - 1));
                boolean horiz = NORMALS[si][1] != 0;
                int len = horiz ? Wd : D;
                int u = rng.next() < 0.5 ? 1 : len - 2;
                if (usedSign.contains(IDS[si] + "" + u)) {
                    u = len - 1 - u;
                }
                if (usedSign.contains(IDS[si] + "" + u)) {
                    continue;
                }
                usedSign.add(IDS[si] + "" + u);
                int out = rng.nextInt(2, 3), hgt = Math.min(top - y0 - 7, rng.nextInt(9, 24));
                int color = SIGN0 + rng.nextInt(0, 7), ps = hashInt(lot.x0 + sg * 31, lot.z0, 17, seed);
                for (int k = 1; k <= out; k++) {
                    for (int y = y0 + 6; y < y0 + 6 + hgt; y++) {
                        int x = horiz ? lot.x0 + u : (si == W ? lot.x0 - k : lot.x1 + k);
                        int z = horiz ? (si == N ? lot.z0 - k : lot.z1 + k) : lot.z0 + u;
                        boolean rim = k == out || y == y0 + 6 || y == y0 + 6 + hgt - 1;
                        w.force(x, y, z, rim ? SIGN_FRAME : (glyph(k, y - y0 - 7, ps) ? GLYPH : color));
                    }
                }
            }
        }

        // the roof: machine house, ACs, water tank, billboard, antenna with a red light
        int rx0 = lot.x0 + ti + 1, rx1 = lot.x1 - ti - 1, rz0 = lot.z0 + ti + 1, rz1 = lot.z1 - ti - 1;
        if (rx1 - rx0 >= 3 && rz1 - rz0 >= 3) {
            int ry = top + 1;
            if (rx1 - rx0 >= 6 && rz1 - rz0 >= 6 && rng.next() < 0.6) {
                int mx = rng.nextInt(rx0, rx1 - 3), mz = rng.nextInt(rz0, rz1 - 3), mh = rng.nextInt(2, 3);
                for (int x = mx; x < mx + 3; x++) {
                    for (int z = mz; z < mz + 3; z++) {
                        for (int y = ry; y < ry + mh; y++) {
                            w.force(x, y, z, wall);
                        }
                    }
                }
            }
            int acs = floor((rx1 - rx0) * (rz1 - rz0) / 18.0);
            for (int k = 0; k < acs; k++) {
                int x = rng.nextInt(rx0, rx1), z = rng.nextInt(rz0, rz1);
                if (w.get(x, ry, z) == 0) {
                    w.force(x, ry, z, rng.next() < 0.5 ? METAL : METAL_DARK);
                }
            }
            if (rng.next() < 0.45) {
                int cx = rng.nextInt(rx0 + 1, rx1 - 1), cz = rng.nextInt(rz0 + 1, rz1 - 1), legs = rng.nextInt(1, 2);
                int[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
                for (int[] c : corners) {
                    for (int y = ry; y < ry + legs; y++) {
                        w.force(cx + c[0], y, cz + c[1], POLE);
                    }
                }
                for (int y = ry + legs; y < ry + legs + 3; y++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.abs(dx) + Math.abs(dz) <= 1 || y < ry + legs + 2) {
                                w.force(cx + dx, y, cz + dz, TANK);
                            }
                        }
                    }
                }
            }
            if (floors >= 4 && lot.screen == null && rng.next() < (lot.avenue ? 0.55 : 0.25)) {
                int si = 0;
                for (int q = 0; q < 4; q++) {
                    if (lot.front[q]) {
                        si = q;
                        break;
                    }
                }
                boolean horiz = NORMALS[si][1] != 0;
                int len = horiz ? rx1 - rx0 + 1 : rz1 - rz0 + 1, bw = Math.min(len, rng.nextInt(9, 14)), bh = rng.nextInt(4, 6);
                int u0 = Math.floorDiv(len - bw, 2), color = SIGN0 + rng.nextInt(0, 7), ps = hashInt(lot.x0, lot.z0, 19, seed);
                for (int u = 0; u < bw; u++) {
                    int x = horiz ? rx0 + u0 + u : (si == W ? rx0 : rx1);
                    int z = horiz ? (si == N ? rz0 : rz1) : rz0 + u0 + u;
                    for (int y = ry; y < ry + 2; y++) {
                        if (u % 4 == 0 || u == bw - 1) {
                            w.force(x, y, z, METAL_DARK);
                        }
                    }
                    for (int y = ry + 2; y < ry + 2 + bh; y++) {
                        boolean rim = u == 0 || u == bw - 1 || y == ry + 2 || y == ry + 1 + bh;
                        w.force(x, y, z, cursed ? DEAD_SIGN : rim ? SIGN_FRAME : (glyph(u, y - ry - 3, ps) ? GLYPH : color));
                    }
                }
            }
            if (floors >= 7 && rng.next() < 0.5) {
                int x = rng.nextInt(rx0, rx1), z = rng.nextInt(rz0, rz1), hh = rng.nextInt(5, 11);
                if (w.get(x, ry, z) == 0) {
                    for (int y = ry; y < ry + hh; y++) {
                        w.force(x, y, z, POLE);
                    }
                    w.force(x, ry + hh, z, AVIATION);
                }
            }
        }

        // the curse: black stains dripping, purple veins, a stain at the building's foot
        if (cursed) {
            for (int y = y0; y <= top + 1; y++) {
                for (int x = lot.x0 - 1; x <= lot.x1 + 1; x++) {
                    for (int z = lot.z0 - 1; z <= lot.z1 + 1; z++) {
                        int t = w.get(x, y, z);
                        if (t == 0 || base(t) == CURSE_FLOOR) {
                            continue;
                        }
                        boolean onFace = x <= lot.x0 + 2 || x >= lot.x1 - 2 || z <= lot.z0 + 2 || z >= lot.z1 - 2;
                        if (!onFace) {
                            continue;
                        }
                        double col = hash3(x, 0, z, lot.seed + 31);
                        int dripTop = y0 + 5 + floor(hash3(x, 1, z, lot.seed + 31) * (top - y0));
                        int dripLen = 4 + floor(hash3(x, 2, z, lot.seed + 31) * 16);
                        boolean s = col < 0.24 && y <= dripTop && y >= dripTop - dripLen;
                        if (y < y0 + 3 && hash3(x, y, z, lot.seed + 33) < 0.55) {
                            s = true;
                        }
                        double v = valueNoise3(x, y, z, lot.seed + 77, 7);
                        if (Math.abs(v - 0.5) < 0.022) {
                            w.force(x, y, z, CURSE_VEIN);
                        } else if (s) {
                            w.force(x, y, z, CURSE_STAIN);
                        }
                    }
                }
            }
        }

        // drink machines against the facade, on the sidewalk
        if (!cursed && rng.next() < 0.4) {
            int si = -1;
            for (int q = 0; q < 4; q++) {
                if (lot.front[q] && !lot.alley[q]) {
                    si = q;
                    break;
                }
            }
            if (si >= 0) {
                boolean horiz = NORMALS[si][1] != 0;
                int len = horiz ? Wd : D, n = rng.nextInt(1, 3), u0 = rng.nextInt(2, Math.max(2, len - 2 - n));
                for (int k = 0; k < n; k++) {
                    int u = u0 + k;
                    if (u >= len - 1) {
                        break;
                    }
                    int x = horiz ? lot.x0 + u : (si == W ? lot.x0 - 1 : lot.x1 + 1);
                    int z = horiz ? (si == N ? lot.z0 - 1 : lot.z1 + 1) : lot.z0 + u;
                    if (w.get(x, 0, z) == 0 || w.get(x, 1, z) != 0) {
                        continue;
                    }
                    w.force(x, 1, z, VEND0 + rng.nextInt(0, 3));
                    w.force(x, 2, z, VENDING);
                }
            }
        }
    }

    // A neighborhood shrine: gravel ground, red torii in front, a small wooden house at the back and two lanterns.
    public static void shrine(Vox w, Lot lot, int seed) {
        Rng rng = new Rng(lot.seed);
        for (int x = lot.x0; x <= lot.x1; x++) {
            for (int z = lot.z0; z <= lot.z1; z++) {
                w.force(x, 0, z, GRAVEL);
            }
        }
        List<Integer> sides = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            if (lot.front[q]) {
                sides.add(q);
            }
        }
        int sd = sides.isEmpty() ? N : sides.get(rng.nextInt(0, sides.size() - 1));
        boolean horiz = sd == N || sd == S;
        int mid = horiz ? Math.floorDiv(lot.x0 + lot.x1, 2) : Math.floorDiv(lot.z0 + lot.z1, 2);
        int front = sd == N ? lot.z0 + 1 : sd == S ? lot.z1 - 1 : sd == W ? lot.x0 + 1 : lot.x1 - 1;
        int back = sd == N ? lot.z1 - 1 : sd == S ? lot.z0 + 1 : sd == W ? lot.x1 - 1 : lot.x0 + 1;
        int th = rng.nextInt(5, 6);
        for (int a : new int[] {mid - 2, mid + 2}) {
            for (int y = 1; y <= th; y++) {
                put(w, horiz, a, front, y, TORII);
            }
        }
        for (int a = mid - 3; a <= mid + 3; a++) {
            put(w, horiz, a, front, th + 1, TORII_TOP);
            if (Math.abs(a - mid) <= 2) {
                put(w, horiz, a, front, th - 1, TORII);
            }
        }
        int dir = back > front ? 1 : -1;
        for (int a = mid - 2; a <= mid + 2; a++) {
            for (int b = 0; b < 3; b++) {
                for (int y = 1; y <= 3; y++) {
                    put(w, horiz, a, back - dir * b, y, (a == mid && b == 2 && y <= 2) ? DOOR : SHRINE_WOOD);
                }
            }
        }
        for (int a = mid - 3; a <= mid + 3; a++) {
            for (int b = -1; b < 4; b++) {
                put(w, horiz, a, back - dir * b, 4, SHRINE_ROOF);
                if (Math.abs(a - mid) <= 1 && b >= 0 && b <= 2) {
                    put(w, horiz, a, back - dir * b, 5, SHRINE_ROOF);
                }
            }
        }
        for (int a : new int[] {mid - 3, mid + 3}) {
            put(w, horiz, a, front + dir * 2, 1, LANTERN);
            put(w, horiz, a, front + dir * 2, 2, LANTERN);
        }
    }

    // a along the front, b from the back to the front
    private static void put(Vox w, boolean horiz, int a, int b, int y, int t) {
        if (horiz) {
            w.force(a, y, b, t);
        } else {
            w.force(b, y, a, t);
        }
    }

    // A parking lot: asphalt with the painted spaces and the yellow sign on the pole.
    public static void parking(Vox w, Lot lot) {
        for (int x = lot.x0; x <= lot.x1; x++) {
            for (int z = lot.z0; z <= lot.z1; z++) {
                boolean line = (lot.x1 - lot.x0 >= lot.z1 - lot.z0)
                        ? ((x - lot.x0) % 3 == 0 && (z == lot.z0 + 1 || z == lot.z1 - 1 || z == lot.z0 + 2 || z == lot.z1 - 2))
                        : ((z - lot.z0) % 3 == 0 && (x == lot.x0 + 1 || x == lot.x1 - 1 || x == lot.x0 + 2 || x == lot.x1 - 2));
                w.force(x, 0, z, line ? PARK_LINE : ASPHALT);
            }
        }
        for (int y = 1; y <= 4; y++) {
            w.force(lot.x0, y, lot.z0, POLE);
        }
        w.force(lot.x0, 5, lot.z0, SIGN2);
    }

    // ---------------------------------------------------------------- metro

    // The station under avenue 'x' i, around x = ox (the preview's: i = 0, ox = 0): two stair entrances on the
    // sidewalks, the concourse with gates, the island platform with stairs, the tracks on both sides with a train
    // stopped on one, and the tunnels going on along the avenue for half on each side; the west tunnel is taken by the
    // curses (no light, stains and veins on the walls). Everything inside is measured from ox (local x).
    public static final int FLOOR_C = -8, CEIL_C = -3, FLOOR_P = -16, CEIL_P = -9, BED = -17;
    public static final int CZ0 = -11, CZ1 = 10, CX0 = -38, CX1 = 38, PX0 = -52, PX1 = 52;
    public static final int WALL = 8, TRACK0 = 5, TRACK1 = 7, ISLAND = 4;

    // The two sidewalk rows' z of the entrances (south, north), for the station on avenue 'x' i.
    public static int[][] entrances(int c) {
        return new int[][] {{c + 8, c + 9, c + 10}, {c - 11, c - 10, c - 9}};
    }

    public static void metro(Vox w, int seed, int half, int i, int ox) {
        int c = street('x', i, seed).c();
        for (int x = -half; x <= half; x++) {
            for (int z = c - 15; z <= c + 14; z++) {
                for (int y = -26; y <= -1; y++) {
                    w.force(ox + x, y, z, STONE);
                }
            }
        }
        // concourse
        for (int x = CX0; x <= CX1; x++) {
            for (int z = c + CZ0; z <= c + CZ1; z++) {
                for (int y = FLOOR_C + 1; y < CEIL_C; y++) {
                    w.force(ox + x, y, z, AIR);
                }
            }
        }
        // platform, tracks and tunnels (the whole avenue of the piece)
        for (int x = -half; x <= half; x++) {
            for (int z = c - WALL + 1; z <= c + WALL - 2; z++) {
                double a = Math.abs(z - c + 0.5);
                boolean platform = x >= PX0 && x <= PX1;
                int top = platform && a <= ISLAND ? FLOOR_P : BED;
                for (int y = top + 1; y < CEIL_P; y++) {
                    w.force(ox + x, y, z, AIR);
                }
            }
        }
        // the platform's stairs: from the concourse (x = ±4) down outwards to the platform (x = ±12)
        for (int sgn : new int[] {-1, 1}) {
            for (int k = 0; k < 8; k++) {
                int x = sgn * (4 + k), topY = FLOOR_C - 1 - k;
                for (int z = c - 1; z <= c; z++) {
                    for (int y = FLOOR_P; y <= topY; y++) {
                        w.force(ox + x, y, z, STAIR);
                    }
                    for (int y = topY + 1; y < CEIL_C; y++) {
                        w.force(ox + x, y, z, AIR);
                    }
                }
            }
        }
        // the entrances on the sidewalk: openings of 3 on the inner rows, 8 steps down to the concourse
        int[][] en = entrances(c);
        int[] south = en[0], north = en[1];
        for (int q = 0; q < 2; q++) {
            int[] zs = q == 0 ? south : north;
            int sgn = q == 0 ? 1 : -1;
            for (int k = 0; k < 8; k++) {
                int x = sgn * (29 - k), topY = -1 - k;
                for (int z : zs) {
                    for (int y = FLOOR_C; y <= topY; y++) {
                        w.force(ox + x, y, z, STAIR);
                    }
                    for (int y = topY + 1; y <= 0; y++) {
                        w.force(ox + x, y, z, AIR);
                    }
                }
            }
        }

        // the lining: all stone touching the void becomes tile, floor, ceiling or tunnel concrete
        List<int[]> lining = new ArrayList<>();
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int x = -half; x <= half; x++) {
            for (int z = c - 15; z <= c + 14; z++) {
                for (int y = -21; y <= 0; y++) {
                    if (w.get(ox + x, y, z) != 0) {
                        continue;
                    }
                    if (y == 0 && !(has(south, z) || has(north, z))) {
                        continue;
                    }
                    for (int[] d : nb) {
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        if (ny > -1 || w.get(ox + nx, ny, nz) != STONE) {
                            continue;
                        }
                        lining.add(new int[] {nx, ny, nz, d[1]});
                    }
                }
            }
        }
        for (int[] e : lining) {
            int x = e[0], y = e[1], z = e[2], dy = e[3];
            boolean station = x >= PX0 && x <= PX1;
            boolean concourse = y >= FLOOR_C && y <= CEIL_C && station;
            int t;
            if (!station) {
                t = dy == -1 ? BALLAST : TUNNEL;
            } else if (dy == 1) {
                t = CEIL;
            } else if (dy == -1) {
                t = TILE_FLOOR;
            } else if (concourse) {
                t = y == -6 ? TILE_LINE : TILE_WALL;
            } else {
                t = y == -11 ? TILE_LINE : TILE_WALL;
            }
            w.force(ox + x, y, z, t);
        }
        // the platform's edge (light concrete) and the yellow tactile strip right after it
        for (int x = PX0; x <= PX1; x++) {
            for (int z = c - ISLAND; z <= c + ISLAND - 1; z++) {
                double a = Math.abs(z - c + 0.5);
                if (w.get(ox + x, FLOOR_P, z) == STAIR) {
                    continue;
                }
                if (a > ISLAND - 1) {
                    w.force(ox + x, FLOOR_P, z, EDGE);
                } else if (a > ISLAND - 2) {
                    w.force(ox + x, FLOOR_P, z, TACTILE);
                }
            }
        }
        // tracks
        for (int x = -half; x <= half; x++) {
            for (int s : new int[] {-1, 1}) {
                for (int d : new int[] {TRACK0, TRACK1}) {
                    int z = s < 0 ? c - d : c + d - 1;
                    w.force(ox + x, BED, z, BALLAST);
                    w.force(ox + x, BED + 1, z, RAIL);
                }
            }
        }
        // ceiling lights: strips in the concourse and over the platform
        for (int x = CX0; x <= CX1; x++) {
            for (int z : new int[] {c - 6, c + 5}) {
                if ((((x % 7) + 7) % 7) < 4 && w.get(ox + x, CEIL_C, z) == CEIL) {
                    w.force(ox + x, CEIL_C, z, CEIL_LIGHT);
                }
            }
        }
        for (int x = PX0; x <= PX1; x++) {
            for (int z : new int[] {c - 3, c + 2}) {
                if ((((x % 6) + 6) % 6) < 4 && w.get(ox + x, CEIL_P, z) == CEIL) {
                    w.force(ox + x, CEIL_P, z, CEIL_LIGHT);
                }
            }
        }
        // pillars: platform (in the middle, away from the stairs) and concourse
        for (int x = PX0 + 4; x <= PX1 - 4; x += 8) {
            if (Math.abs(x) >= 3 && Math.abs(x) <= 13) {
                continue;
            }
            for (int y = FLOOR_P + 1; y < CEIL_P; y++) {
                for (int z : new int[] {c - 1, c}) {
                    w.force(ox + x, y, z, PILLAR);
                }
            }
            for (int z : new int[] {c - 1, c}) {
                if (Math.abs(x + 4) > 13 && x + 4 <= PX1 - 2) {
                    w.force(ox + x + 4, FLOOR_P + 1, z, BENCH);
                }
            }
        }
        for (int x = CX0 + 4; x <= CX1 - 4; x += 8) {
            for (int z : new int[] {c - 5, c + 4}) {
                if (Math.abs(x) >= 3 && Math.abs(x) <= 13 && Math.abs(z - c + 0.5) < 3) {
                    continue;
                }
                for (int y = FLOOR_C + 1; y < CEIL_C; y++) {
                    w.force(ox + x, y, z, PILLAR);
                }
            }
        }
        // gates at x = ±16 (alternate blocks with passages)
        for (int x : new int[] {-16, 16}) {
            for (int z = c + CZ0; z <= c + CZ1; z++) {
                if (w.get(ox + x, FLOOR_C + 1, z) != 0) {
                    continue;
                }
                int a = floor(Math.abs(z - c + 0.5));
                if (a >= 8) {
                    for (int y = FLOOR_C + 1; y < CEIL_C; y++) {
                        w.force(ox + x, y, z, TILE_WALL);
                    }
                    continue;
                }
                if ((a & 1) == 0) {
                    w.force(ox + x, FLOOR_C + 1, z, GATE);
                }
            }
        }
        // railings around the stairs' openings in the concourse
        for (int sgn : new int[] {-1, 1}) {
            for (int k = 0; k < 8; k++) {
                for (int z : new int[] {c - 2, c + 1}) {
                    w.force(ox + sgn * (4 + k), FLOOR_C + 1, z, RAILING);
                }
            }
            for (int z = c - 1; z <= c; z++) {
                w.force(ox + sgn * 12, FLOOR_C + 1, z, RAILING);
            }
        }
        // signs: the station's name on the track walls (white with dark letters) and hanging signs in the concourse
        for (int x = PX0 + 6; x <= PX1 - 12; x += 22) {
            for (int s : new int[] {-1, 1}) {
                int z = s < 0 ? c - WALL : c + WALL - 1, ps = hashInt(x, s, 23, seed);
                for (int u = 0; u < 7; u++) {
                    for (int y = -14; y <= -13; y++) {
                        w.force(ox + x + u, y, z, glyph(u, (y + 14) + 3, ps) && u > 0 && u < 6 ? SIGN_DARK : METRO_SIGN);
                    }
                }
            }
        }
        for (int x = CX0 + 8; x <= CX1 - 8; x += 12) {
            for (int z = c - 2; z <= c + 1; z++) {
                if (Math.abs(x) <= 13) {
                    continue;
                }
                w.force(ox + x, CEIL_C - 1, z, METRO_SIGN);
            }
        }
        // a train stopped on the north track: 3 cars of 20 with 1 between
        int[] tz = {c - TRACK1, c - TRACK1 + 1, c - TRACK0};
        int[] doors = {3, 4, 9, 10, 15, 16};
        for (int car = 0; car < 3; car++) {
            int x0 = -48 + car * 21;
            for (int k = 0; k < 20; k++) {
                int x = x0 + k;
                boolean end = k == 0 || k == 19, door = has(doors, k);
                for (int z : tz) {
                    boolean outer = z != tz[1];
                    w.force(ox + x, BED + 1, z, TRAIN_UNDER);
                    for (int y = BED + 2; y <= BED + 5; y++) {
                        int t = TRAIN;
                        if (!outer && !end) {
                            t = TRAIN;
                        } else if (end) {
                            t = car == 0 && k == 0 ? ((y == BED + 4 || y == BED + 3) && z == tz[1] ? TRAIN_FRONT
                                    : y == BED + 2 && z != tz[1] ? HEADLIGHT : TRAIN_END) : TRAIN_END;
                        } else if (y == BED + 2) {
                            t = door ? TRAIN_DOOR : TRAIN_STRIPE;
                        } else if (y == BED + 3 || y == BED + 4) {
                            t = door ? (y == BED + 4 ? TRAIN_WIN : TRAIN_DOOR) : TRAIN_WIN;
                        } else {
                            t = TRAIN;
                        }
                        w.force(ox + x, y, z, t);
                    }
                    w.force(ox + x, BED + 6, z, TRAIN_ROOF);
                }
            }
        }
        // the east tunnel's lights; the west one (x < PX0) belongs to the curses
        for (int x = -half; x <= half; x++) {
            if (x >= PX0 && x <= PX1) {
                continue;
            }
            for (int z : new int[] {c - WALL, c + WALL - 1}) {
                if (w.get(ox + x, -12, z) != TUNNEL) {
                    continue;
                }
                if (x > PX1) {
                    if (((x % 12) + 12) % 12 == 0) {
                        w.force(ox + x, -12, z, TUNNEL_LIGHT);
                    }
                }
            }
            if (x < PX0) {
                for (int z = c - WALL; z <= c + WALL - 1; z++) {
                    for (int y = BED; y <= CEIL_P; y++) {
                        int t = w.get(ox + x, y, z);
                        if (t != TUNNEL && t != BALLAST && t != CEIL) {
                            continue;
                        }
                        double deep = Math.min(1, (PX0 - x) / 30.0);
                        double v = valueNoise3(x, y, z, seed + 91, 6);
                        if (Math.abs(v - 0.5) < 0.03 * deep) {
                            w.force(ox + x, y, z, CURSE_VEIN);
                        } else if (hash3(x, y, z, seed + 92) < 0.55 * deep && valueNoise3(x, y, z, seed + 93, 9) > 0.45) {
                            w.force(ox + x, y, z, CURSE_STAIN);
                        }
                    }
                }
            }
        }
    }

    private static boolean has(int[] a, int v) {
        for (int x : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- street furniture

    // Light poles, avenue trees, guard rails, wires and traffic lights: of the streets 'x' i0..i1 for x in [x0, x1]
    // along them, of the streets 'z' j0..j1 for z in [z0, z1]; traffic lights only with |x|, |z| <= lim (the preview's
    // piece). The game passes lim = Integer.MAX_VALUE, and then a traffic light also needs ground under its pole.
    public static void furniture(Vox w, int seed, int i0, int i1, int j0, int j1, int x0, int x1, int z0, int z1, int lim) {
        for (char axis : new char[] {'x', 'z'}) {
            char other = axis == 'x' ? 'z' : 'x';
            int lo = axis == 'x' ? i0 : j0, hi = axis == 'x' ? i1 : j1;
            int a0 = axis == 'x' ? x0 : z0, a1 = axis == 'x' ? x1 : z1;
            for (int i = lo; i <= hi; i++) {
                Street s = street(axis, i, seed);
                int off = floor(hash3(i, axis == 'x' ? 1 : 2, 40, seed) * 16);
                int[][] curbs = {{s.c() + s.r1() + 1, 1}, {s.c() + s.r0() - 1, -1}};
                for (int[] cb : curbs) {
                    int b = cb[0], out = cb[1];
                    for (int a = a0; a <= a1; a++) {
                        int x = axis == 'x' ? a : b, z = axis == 'x' ? b : a;
                        if (w.get(x, 0, z) != CURB) {
                            continue;
                        }
                        boolean near = streetAt(other, a - 3, seed) != null || streetAt(other, a + 3, seed) != null
                                || streetAt(other, a, seed) != null;
                        int m = (((a + off) % 16) + 16) % 16;
                        if (s.avenue()) {
                            if (near) {
                                continue;
                            }
                            if (m == 0) {
                                for (int y = 1; y <= 7; y++) {
                                    putEmpty(w, x, y, z, POLE);
                                }
                                putEmpty(w, px(axis, a, b - out), 7, pz(axis, a, b - out), arm(axis, out, false, true));
                                putEmpty(w, px(axis, a, b - out * 2), 7, pz(axis, a, b - out * 2), LAMP);
                            } else if (m == 8) {
                                w.force(x, 0, z, PLANTER);
                                int th = 2 + floor(hash3(a, i, 41, seed) * 2);
                                for (int y = 1; y <= th; y++) {
                                    putEmpty(w, x, y, z, TRUNK);
                                }
                                double rr = 2.1 + hash3(a, i, 42, seed) * 0.9;
                                int cy = th + 2;
                                for (int dx = -3; dx <= 3; dx++) {
                                    for (int dy = -2; dy <= 2; dy++) {
                                        for (int dz = -3; dz <= 3; dz++) {
                                            double ey = dy * 1.4;
                                            double d = Math.sqrt(dx * dx + ey * ey + dz * dz)
                                                    + (hash3(x + dx, cy + dy, z + dz, seed + 43) - 0.5) * 1.1;
                                            if (d <= rr) {
                                                putEmpty(w, x + dx, cy + dy, z + dz,
                                                        hash3(x + dx, cy + dy, z + dz, seed + 44) < 0.3 ? LEAVES2 : LEAVES);
                                            }
                                        }
                                    }
                                }
                            } else if (m != 7 && m != 9 && m != 15 && m != 1) {
                                putEmpty(w, x, 1, z, RAILING);
                            }
                        } else if (out < 0) {
                            int m2 = (((a + off) % 20) + 20) % 20;
                            if (m2 != 0 || near) {
                                continue;
                            }
                            for (int y = 1; y <= 9; y++) {
                                putEmpty(w, x, y, z, POLE);
                            }
                            // the crossarm, each side with its insulator; the client draws the cables to the next pole
                            putEmpty(w, px(axis, a, b - 1), 8, pz(axis, a, b - 1), arm(axis, 1, true, true));
                            putEmpty(w, px(axis, a, b + 1), 8, pz(axis, a, b + 1), arm(axis, -1, true, true));
                            putEmpty(w, px(axis, a, b + 1), 6, pz(axis, a, b + 1), METAL);
                            putEmpty(w, px(axis, a, b - 1), 5, pz(axis, a, b - 1), LAMP);

                        }
                    }
                }
            }
        }
        // traffic lights: a pole at each corner of the crossing, the arm over the road
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                Street X = street('x', i, seed), Z = street('z', j, seed);
                int[][] sg = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
                for (int[] q : sg) {
                    int sx = q[0], sz = q[1];
                    int x = sx > 0 ? Z.c() + Z.r1() + 1 : Z.c() + Z.r0() - 1, z = sz > 0 ? X.c() + X.r1() + 1 : X.c() + X.r0() - 1;
                    if (Math.abs(x) > lim || Math.abs(z) > lim || lim == Integer.MAX_VALUE && w.get(x, 0, z) == 0) {
                        continue;
                    }
                    for (int y = 1; y <= 6; y++) {
                        putEmpty(w, x, y, z, POLE);
                    }
                    for (int k = 1; k <= 3; k++) {
                        putEmpty(w, x, 6, z - sz * k, ARM + (sz > 0 ? 2 : 0) + (k == 1 ? 8 : 0));
                    }
                    putEmpty(w, x, 5, z - sz * 3, TRAFFIC_BOX);
                    putEmpty(w, x - sx, 5, z - sz * 3, ((i + j) & 1) != 0 ? TRAFFIC_R : TRAFFIC_G);
                }
            }
        }
    }

    // A crossarm on a street along the axis, with its pole toward +across (side 1) or -across (side -1).
    private static int arm(char axis, int side, boolean cable, boolean reach) {
        int facing = axis == 'x' ? (side > 0 ? 2 : 0) : (side > 0 ? 1 : 3);
        return ARM + facing + (cable ? 4 : 0) + (reach ? 8 : 0);
    }

    private static int px(char axis, int a, int b) {
        return axis == 'x' ? a : b;
    }

    private static int pz(char axis, int a, int b) {
        return axis == 'x' ? b : a;
    }

    private static void putEmpty(Vox w, int x, int y, int z, int t) {
        if (w.get(x, y, z) == 0) {
            w.force(x, y, z, t);
        }
    }

    // ---------------------------------------------------------------- the preview's piece

    // The preview's shibuyaPiece(seed, half), for the parity check.
    public static Vox piece(int seed, int half) {
        Vox w = new Vox(-half, -half, half - 1, half - 1);
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                int g = groundAt(x, z, seed);
                w.force(x, 0, z, g < 0 ? LOT : g);
                w.force(x, -1, z, SOIL);
                w.force(x, -2, z, STONE);
            }
        }
        int lo = floor((-half - 20) / (double) CELL), hi = (int) Math.ceil((half + 20) / (double) CELL);
        List<Plan> blocks = new ArrayList<>();
        for (int i = lo; i < hi; i++) {
            for (int j = lo; j < hi; j++) {
                Plan plan = blockPlan(i, j, seed);
                if (plan.x1 < -half || plan.x0 >= half || plan.z1 < -half || plan.z0 >= half) {
                    continue;
                }
                blocks.add(plan);
                for (int[] a : plan.alleys) {
                    for (int x = a[0]; x <= a[1]; x++) {
                        for (int z = a[2]; z <= a[3]; z++) {
                            w.force(x, 0, z, ALLEY);
                        }
                    }
                }
            }
        }
        metro(w, seed, half, 0, 0);
        for (Plan plan : blocks) {
            if (plan.towerAv != null) {
                skyscraper(w, plan, seed);
            }
        }
        for (Plan plan : blocks) {
            for (Lot lot : plan.lots) {
                if (lot.kind.equals("shrine")) {
                    shrine(w, lot, seed);
                } else if (lot.kind.equals("parking")) {
                    parking(w, lot);
                } else {
                    building(w, lot, seed);
                }
            }
        }
        furniture(w, seed, lo, hi, lo, hi, -half, half, -half, half, half);
        return w;
    }
}
