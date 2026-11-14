package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;

// The Templos Antigos (Stark 2026-11-12, "muito lindo, pode meter no game"): the line-by-line port of the approved
// preview's generator (/mnt/project-files/forja/jujutsu-templo/gen.js), the same draws in the same order, so the game
// makes what the preview showed. Change one, change the other.
// Jujutsu's old Japan: mountains covered in very tall cedars (sugi), sakura groves with the ground pink with petals,
// moss on the stones, and up on the slopes the temples: a white gravel courtyard on a stone wall, the main hall of wood
// with the curved roof, a five-storey pagoda or a belfry, stone lanterns, the sacred tree with the shimenawa rope, and
// the stone stairway going down the slope under red torii (the scene where Gojo takes Riko and Toji attacks). Out in
// the woods now and then a small shrine (hokora).
// The land's shape is the game's; the generator puts the pieces and the ground's top.
public final class TemploGen {
    // The preview's NAMES, in its order.
    public static final byte AIR = 0, STONE = 1, DIRT = 2, GRASS = 3, MOSS = 4, NEEDLES = 5, PETALS = 6, MOSSY_STONE = 7,
            GRAVEL_W = 8, PATH = 9, WATER = 10, CEDAR_BARK = 11, CEDAR_LEAF = 12, CEDAR_LEAF2 = 13, SAKURA_BARK = 14,
            SAKURA0 = 15, SAKURA1 = 16, SAKURA2 = 17, SAKURA_W = 18, WALL_STONE = 19, STEP = 20, PLASTER = 21, WOOD = 22,
            WOOD_DARK = 23, VERMILION = 24, LATTICE = 25, FLOOR_WOOD = 26, ROOF_TILE = 27, ROOF_RIDGE = 28, ROOF_EAVE = 29,
            GOLD = 30, BRONZE = 31, TORII = 32, TORII_TOP = 33, LANTERN_STONE = 34, LANTERN_FIRE = 35, ROPE = 36, SHIDE = 37,
            FERN = 38, SHRUB = 39,
            // Escola Jujutsu: the training field's packed earth, the classrooms' glass, the training dummies' straw, the
            // wooden door, the dark of the way down to the Corredor das Estrelas
            PACKED = 40, GLASS = 41, STRAW = 42, DOOR = 43, DARK = 44;
    // The preview's force(..., 0): in the preview the ground is in the same map and this takes it out; here it says
    // "make it air" to the feature.
    public static final byte CARVE = 100;

    public static final int SITE_CELL = 150;
    // How far a temple reaches from its center: the courtyard (up to 19 + 2) and the stairway (up to 90 steps, the foot's
    // landing and torii past it) along the front.
    public static final int TEMPLE_REACH = 21 + 90 + 8;
    // How far a tree's blocks reach from its trunk (a sakura's branch, 6.5, plus its blossom, 3.8 + 0.5 of shake, or a
    // big cedar's crown, about 6.5 from its 2 x 2 trunk).
    public static final int TREE_REACH = 12;

    private TemploGen() {
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    static double hash3(int x, int y, int z, int seed) {
        return LeafTreeGen.hash3(x, y, z, seed);
    }

    public static int hashInt(int x, int y, int z, int seed) {
        return (int) Math.floor(hash3(x, y, z, seed) * 2147483646) + 1;
    }

    static double valueNoise(double x, double z, int seed, double scale) {
        return DesertGen.valueNoise(x, z, seed, scale);
    }

    private static int floor(double a) {
        return (int) Math.floor(a);
    }

    // The preview's Math.hypot for two and three numbers (sqrt of the squares: the same double for these sizes).
    private static double hypot(double a, double b) {
        return Math.sqrt(a * a + b * b);
    }

    private static double hypot(double a, double b, double c) {
        return Math.sqrt(a * a + b * b + c * c);
    }

    // The preview's Vox: set only where nothing is yet, force always. Bounded to a box of columns (the feature's chunk),
    // or unbounded. A force to air is kept as CARVE (get sees it as empty, set may fill it, as in the preview).
    public static final class Vox {
        final Map<Long, Byte> m = new HashMap<>();
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
            Byte t = m.get(LeafTreeGen.Keys.key(x, y, z));
            return t == null || t == CARVE ? 0 : t;
        }

        public void set(int x, int y, int z, byte t) {
            if (!in(x, z)) {
                return;
            }
            long k = LeafTreeGen.Keys.key(x, y, z);
            Byte there = m.get(k);
            if (there == null || there == CARVE) {
                m.put(k, t);
            }
        }

        public void force(int x, int y, int z, byte t) {
            if (!in(x, z)) {
                return;
            }
            m.put(LeafTreeGen.Keys.key(x, y, z), t == AIR ? CARVE : t);
        }

        // Packed as x, y, z, type per voxel, CARVE included.
        public int[] pack() {
            int[] out = new int[m.size() * 4];
            int i = 0;
            for (Map.Entry<Long, Byte> e : m.entrySet()) {
                long k = e.getKey();
                out[i++] = LeafTreeGen.Keys.x(k);
                out[i++] = LeafTreeGen.Keys.y(k);
                out[i++] = LeafTreeGen.Keys.z(k);
                out[i++] = e.getValue();
            }
            return out;
        }

        public void each(VoxConsumer f) {
            for (Map.Entry<Long, Byte> e : m.entrySet()) {
                long k = e.getKey();
                f.accept(LeafTreeGen.Keys.x(k), LeafTreeGen.Keys.y(k), LeafTreeGen.Keys.z(k), e.getValue());
            }
        }
    }

    public interface VoxConsumer {
        void accept(int x, int y, int z, byte t);
    }

    // ---- trees ----------------------------------------------------------------------------------------------------

    private static final int[][] ROOTS = {{-1, 0}, {2, 1}, {1, -1}, {0, 2}};

    // Cedar (sugi): a tall straight trunk, sometimes 2 x 2 (the old ones), a conical crown in tiers narrowing to the tip,
    // each tier with a ragged, slightly drooping edge. No two alike. Returns its height.
    public static int cedar(Vox w, int ox, int oy, int oz, int seed, boolean sacred) {
        Rng rng = new Rng(seed);
        boolean big = sacred || rng.next() < 0.18;
        int H = sacred ? rng.nextInt(34, 40) : big ? rng.nextInt(28, 36) : rng.nextInt(17, 28);
        int T = big ? 2 : 1;
        int bare = floor(H * rng.range(0.32, 0.45)); // clean trunk at the bottom
        for (int y = 0; y < H - 2; y++) {
            for (int a = 0; a < T; a++) {
                for (int b = 0; b < T; b++) {
                    w.force(ox + a, oy + y, oz + b, CEDAR_BARK);
                }
            }
        }
        // roots at the big ones' foot
        if (big) {
            for (int[] r : ROOTS) {
                if (rng.next() < 0.7) {
                    w.force(ox + r[0], oy, oz + r[1], CEDAR_BARK);
                }
            }
        }
        double cx = ox + (T - 1) / 2.0, cz = oz + (T - 1) / 2.0;
        int crown = H - bare;
        double R0 = (big ? 5.2 : 3.8) * rng.range(0.9, 1.1);
        for (int y = bare; y <= H + 1; y++) {
            double t = (double) (y - bare) / crown; // 0 at the bottom, 1 at the tip
            // tiers: the radius pulses every 3 blocks (branches in storeys), narrowing to the tip
            double tier = ((y - bare) % 3) / 3.0;
            double r = R0 * StrictMath.pow(1 - Math.min(1, t), 0.9) * (0.72 + 0.28 * (1 - tier)) + 0.6;
            int ri = (int) Math.ceil(r) + 1;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    int x = (int) Math.round(cx + dx), z = (int) Math.round(cz + dz);
                    double d = hypot(x - cx, z - cz) + (hash3(x, y, z, seed + 3) - 0.5) * 1.3;
                    if (d > r) {
                        continue;
                    }
                    if (d > r - 1.2 && tier < 0.34 && hash3(x, y, z, seed + 4) < 0.5) {
                        w.set(x, oy + y - 1, z, CEDAR_LEAF2); // drooping edge
                    }
                    w.set(x, oy + y, z, hash3(x, y, z, seed + 5) < 0.28 ? CEDAR_LEAF2 : CEDAR_LEAF);
                }
            }
        }
        if (sacred) {
            // the shimenawa rope around the trunk 3 up, with the zigzag paper strips hanging from it
            for (int dx = -1; dx <= T; dx++) {
                for (int dz = -1; dz <= T; dz++) {
                    if (dx >= 0 && dx < T && dz >= 0 && dz < T) {
                        continue;
                    }
                    w.force(ox + dx, oy + 3, oz + dz, ROPE);
                    if ((dx + dz) % 2 == 0) {
                        w.force(ox + dx, oy + 2, oz + dz, SHIDE);
                    }
                }
            }
        }
        return H;
    }

    // Sakura: a short crooked trunk opening into 3 to 5 thick branches rising at a slant, and at each branch's end a cloud
    // of blossom in lobes (three pinks and an almost white), wider than tall.
    public static void sakura(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int trunkH = rng.nextInt(3, 5);
        double x = ox + 0.5, z = oz + 0.5;
        double lean = rng.range(0, Math.PI * 2), lk = rng.range(0, 0.35);
        for (int y = 0; y < trunkH; y++) {
            x += StrictMath.cos(lean) * lk;
            z += StrictMath.sin(lean) * lk;
            w.force(floor(x), oy + y, floor(z), SAKURA_BARK);
        }
        List<double[]> tips = new ArrayList<>();
        int nb = rng.nextInt(3, 5);
        double a0 = rng.range(0, Math.PI * 2);
        for (int b = 0; b < nb; b++) {
            double a = a0 + b * (Math.PI * 2 / nb) + rng.range(-0.4, 0.4), len = rng.range(3.5, 6.5), rise = rng.range(0.45, 0.8);
            double bx = x, by = oy + trunkH - 0.5, bz = z;
            for (double s = 0; s < len; s += 0.5) {
                bx += StrictMath.cos(a) * 0.5;
                bz += StrictMath.sin(a) * 0.5;
                by += rise * 0.5 * (1 - s / len * 0.6);
                w.force(floor(bx), floor(by), floor(bz), SAKURA_BARK);
            }
            tips.add(new double[] {bx, by, bz});
        }
        tips.add(new double[] {x, oy + trunkH + 2.5, z});
        int shade = rng.nextInt(0, 2);
        for (double[] tip : tips) {
            double tx = tip[0], ty = tip[1], tz = tip[2];
            double rx = rng.range(2.6, 3.8), ry = rng.range(1.6, 2.4);
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        int px = floor(tx) + dx, py = floor(ty) + dy, pz = floor(tz) + dz;
                        double d = hypot((px - tx) / rx, (py - ty) / ry, (pz - tz) / rx) + (hash3(px, py, pz, seed + 7) - 0.5) * 0.45;
                        if (d > 1) {
                            continue;
                        }
                        double h = hash3(px, py, pz, seed + 8);
                        w.set(px, py, pz, h < 0.12 ? SAKURA_W : (byte) (SAKURA0 + ((shade + (h < 0.45 ? 0 : h < 0.8 ? 1 : 2)) % 3)));
                    }
                }
            }
        }
    }

    // ---- temple ---------------------------------------------------------------------------------------------------

    // Hip roof with the corners turned up: over [x0, x1] x [z0, z1] (the eaves already past the walls), from y0, up 1 and
    // in 1 each layer until it becomes the ridge. At the eave's corners the tip rises 1 (2 on the big roofs).
    static void roof(Vox w, int x0, int z0, int x1, int z1, int y0, int lift) {
        int k = 0;
        for (;;) {
            int ax0 = x0 + k, ax1 = x1 - k, az0 = z0 + k, az1 = z1 - k;
            if (ax0 > ax1 || az0 > az1) {
                break;
            }
            int y = y0 + k;
            boolean ridge = ax0 == ax1 || az0 == az1 || ax1 - ax0 <= 1 || az1 - az0 <= 1;
            for (int x = ax0; x <= ax1; x++) {
                for (int z = az0; z <= az1; z++) {
                    boolean edge = x == ax0 || x == ax1 || z == az0 || z == az1;
                    if (!edge && !ridge) {
                        continue;
                    }
                    w.force(x, y, z, ridge ? ROOF_RIDGE : k == 0 ? ROOF_EAVE : ROOF_TILE);
                }
            }
            if (ridge) {
                // the ridge's end pieces
                if (ax1 - ax0 >= az1 - az0) {
                    w.force(ax0, y + 1, Math.floorDiv(az0 + az1, 2), ROOF_RIDGE);
                    w.force(ax1, y + 1, Math.floorDiv(az0 + az1, 2), ROOF_RIDGE);
                } else {
                    w.force(Math.floorDiv(ax0 + ax1, 2), y + 1, az0, ROOF_RIDGE);
                    w.force(Math.floorDiv(ax0 + ax1, 2), y + 1, az1, ROOF_RIDGE);
                }
                break;
            }
            k++;
        }
        // the turned-up tips: the eave's corners rise
        int[][] corners = {{x0, z0, 1, 1}, {x1, z0, -1, 1}, {x0, z1, 1, -1}, {x1, z1, -1, -1}};
        for (int[] c : corners) {
            for (int l = 1; l <= lift; l++) {
                w.force(c[0], y0 + l, c[1], ROOF_EAVE);
                if (l == 1) {
                    w.force(c[0] + c[2], y0 + 1, c[1], ROOF_TILE);
                    w.force(c[0], y0 + 1, c[1] + c[3], ROOF_TILE);
                }
            }
        }
    }

    // A wooden hall on a stone base: raised floor, a veranda all around, posts (vermilion on a shinto shrine, dark wood
    // on a buddhist temple) every 3, white plaster walls with wooden lattice, an open front with steps, and the curved
    // roof. Returns its top.
    static int hall(Vox w, int x0, int z0, int x1, int z1, int y, char front, boolean shinto, Rng rng) {
        byte post = shinto ? VERMILION : WOOD_DARK;
        // stone base and floor
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                w.force(x, y, z, WALL_STONE);
                w.force(x, y + 1, z, (x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1) ? WOOD : FLOOR_WOOD);
            }
        }
        int wallH = rng.nextInt(4, 6), fy = y + 2;
        int iw0 = x0 + 1, iw1 = x1 - 1, iz0 = z0 + 1, iz1 = z1 - 1; // walls set back: a veranda of 1 around
        for (int yy = fy; yy < fy + wallH; yy++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean outer = x == x0 || x == x1 || z == z0 || z == z1;
                    boolean wall = x == iw0 || x == iw1 || z == iz0 || z == iz1;
                    boolean inside = x >= iw0 && x <= iw1 && z >= iz0 && z <= iz1;
                    if (outer) {
                        // the veranda's posts
                        if (((x - x0) % 3 == 0 && (z == z0 || z == z1)) || ((z - z0) % 3 == 0 && (x == x0 || x == x1))) {
                            w.force(x, yy, z, post);
                        } else if (yy == fy) {
                            w.force(x, yy, z, WOOD); // low railing
                        }
                        continue;
                    }
                    if (!inside || !wall) {
                        continue;
                    }
                    boolean corner = (x == iw0 || x == iw1) && (z == iz0 || z == iz1);
                    int u = (z == iz0 || z == iz1) ? x - iw0 : z - iz0;
                    boolean isFront = (front == 'n' && z == iz0) || (front == 's' && z == iz1) || (front == 'w' && x == iw0)
                            || (front == 'e' && x == iw1);
                    byte t;
                    if (corner || u % 3 == 0) {
                        t = post;
                    } else if (yy == fy + wallH - 1) {
                        t = WOOD; // top beam
                    } else if (isFront) {
                        t = yy <= fy + 2 ? AIR : LATTICE; // sliding doors open
                    } else {
                        t = yy == fy + 1 || yy == fy + 2 ? (hash3(x, yy, z, 3) < 0.5 ? LATTICE : PLASTER) : PLASTER;
                    }
                    w.force(x, yy, z, t);
                }
            }
        }
        // wooden steps at the front
        int mx = Math.floorDiv(x0 + x1, 2), mz = Math.floorDiv(z0 + z1, 2);
        for (int k = -1; k <= 1; k++) {
            if (front == 'n') {
                w.force(mx + k, y + 1, z0 - 2, STEP);
                w.force(mx + k, y, z0 - 3, STEP);
            }
            if (front == 's') {
                w.force(mx + k, y + 1, z1 + 2, STEP);
                w.force(mx + k, y, z1 + 3, STEP);
            }
            if (front == 'w') {
                w.force(x0 - 2, y + 1, mz + k, STEP);
                w.force(x0 - 3, y, mz + k, STEP);
            }
            if (front == 'e') {
                w.force(x1 + 2, y + 1, mz + k, STEP);
                w.force(x1 + 3, y, mz + k, STEP);
            }
        }
        // roof: eaves 2 past the veranda
        int ry = fy + wallH;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                w.force(x, ry, z, WOOD_DARK);
            }
        }
        boolean big = (x1 - x0) >= 12;
        roof(w, x0 - 2, z0 - 2, x1 + 2, z1 + 2, ry + 1, big ? 2 : 1);
        // the gold plaque in the middle of the front
        if (front == 'n') {
            w.force(mx, ry - 1, z0, GOLD);
        }
        if (front == 's') {
            w.force(mx, ry - 1, z1, GOLD);
        }
        if (front == 'w') {
            w.force(x0, ry - 1, mz, GOLD);
        }
        if (front == 'e') {
            w.force(x1, ry - 1, mz, GOLD);
        }
        return ry + (int) Math.ceil(Math.min(x1 - x0, z1 - z0) / 2.0) + 3;
    }

    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    // Five-storey pagoda: each storey a bit smaller, wooden wall with the little balcony, wide eaves with the tips turned
    // up, and on top the bronze mast with its rings.
    static int pagoda(Vox w, int cx, int cz, int y, Rng rng) {
        int levels = 5, base = rng.nextInt(3, 4);
        for (int x = cx - base - 2; x <= cx + base + 2; x++) {
            for (int z = cz - base - 2; z <= cz + base + 2; z++) {
                w.force(x, y, z, WALL_STONE);
            }
        }
        int yy = y + 1;
        for (int l = 0; l < levels; l++) {
            int r = Math.max(1, base - floor(l * 0.6)), h = l == 0 ? 5 : 3;
            for (int k = 0; k < h; k++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) != r && Math.abs(dz) != r) {
                            continue;
                        }
                        boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                        boolean mid = dx == 0 || dz == 0;
                        w.force(cx + dx, yy + k, cz + dz, corner ? VERMILION : (mid && k < 2 && l == 0) ? AIR
                                : (k == h - 1 ? WOOD_DARK : (mid ? LATTICE : PLASTER)));
                    }
                }
            }
            yy += h;
            int e = r + 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    w.force(cx + dx, yy, cz + dz, WOOD_DARK);
                }
            }
            // the storey's roof: only the eaves in two steps (the storey above covers the rest)
            for (int dx = -e; dx <= e; dx++) {
                for (int dz = -e; dz <= e; dz++) {
                    int m = Math.max(Math.abs(dx), Math.abs(dz));
                    if (m == e) {
                        w.force(cx + dx, yy, cz + dz, ROOF_EAVE);
                    } else if (m == e - 1) {
                        w.force(cx + dx, yy + 1, cz + dz, ROOF_TILE);
                    } else if (m > r - 1 || l == levels - 1) {
                        w.force(cx + dx, yy + 1 + (l == levels - 1 ? Math.max(0, r + 1 - m) : 0), cz + dz, ROOF_TILE);
                    }
                }
            }
            for (int[] s : DIAG) {
                w.force(cx + s[0] * e, yy + 1, cz + s[1] * e, ROOF_EAVE);
            }
            yy += 2;
        }
        int top = yy + base;
        for (int k = yy; k <= top + 7; k++) {
            w.force(cx, k, cz, (k - yy) % 2 == 1 && k < top + 5 ? GOLD : BRONZE);
        }
        return top + 8;
    }

    private static final int[][] BELFRY_POSTS = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};

    // Belfry: four posts, a roof and the bronze bell hanging.
    static void belfry(Vox w, int cx, int cz, int y) {
        for (int[] p : BELFRY_POSTS) {
            for (int k = 1; k <= 5; k++) {
                w.force(cx + p[0], y + k, cz + p[1], WOOD_DARK);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.force(cx + dx, y, cz + dz, WALL_STONE);
                w.force(cx + dx, y + 6, cz + dz, WOOD_DARK);
            }
        }
        roof(w, cx - 4, cz - 4, cx + 4, cz + 4, y + 7, 1);
        w.force(cx, y + 5, cz, BRONZE);
        w.force(cx, y + 4, cz, BRONZE);
        w.force(cx, y + 3, cz, BRONZE);
    }

    // Stone lantern (toro): foot, shaft, the fire box (lit at night) and the cap.
    static void lantern(Vox w, int x, int y, int z, boolean tall) {
        w.force(x, y, z, LANTERN_STONE);
        if (tall) {
            w.force(x, y + 1, z, LANTERN_STONE);
        }
        int f = y + (tall ? 2 : 1);
        w.force(x, f, z, LANTERN_FIRE);
        w.force(x, f + 1, z, LANTERN_STONE);
    }

    // Torii: two posts, the lower beam (nuki) and the black upper one (kasagi), wider and with its ends raised.
    static void torii(Vox w, int cx, int y, int cz, char axis, int half, int h) {
        for (int u : new int[] {-half, half}) {
            for (int k = 0; k < h; k++) {
                toriiPut(w, cx, cz, axis, u, y + k, TORII);
            }
        }
        for (int u = -half; u <= half; u++) {
            toriiPut(w, cx, cz, axis, u, y + h - 2, TORII);
        }
        for (int u = -half - 1; u <= half + 1; u++) {
            toriiPut(w, cx, cz, axis, u, y + h, TORII);
        }
        for (int u = -half - 2; u <= half + 2; u++) {
            toriiPut(w, cx, cz, axis, u, y + h + 1, TORII_TOP);
        }
        toriiPut(w, cx, cz, axis, -half - 2, y + h + 2, TORII_TOP);
        toriiPut(w, cx, cz, axis, half + 2, y + h + 2, TORII_TOP);
        toriiPut(w, cx, cz, axis, 0, y + h - 1, GOLD); // plaque
    }

    private static void toriiPut(Vox w, int cx, int cz, char axis, int u, int y, byte t) {
        if (axis == 'x') {
            w.force(cx + u, y, cz, t);
        } else {
            w.force(cx, y, cz + u, t);
        }
    }

    // A temple's spot: center, courtyard height, the side facing the drop and its own seed.
    public record Site(int cx, int cz, int y, char front, int seed) {
    }

    public record Temple(int cx, int cz, int y, int top, char front, int stairs, boolean tunnel) {
    }

    static int[] dir(char front) {
        return switch (front) {
            case 'n' -> new int[] {0, -1};
            case 's' -> new int[] {0, 1};
            case 'w' -> new int[] {-1, 0};
            default -> new int[] {1, 0};
        };
    }

    // The whole temple on a spot: a levelled courtyard (white gravel) on a mossy stone wall, the main hall at the back, a
    // pagoda or belfry to one side, lanterns along the path, the sacred tree, sakuras in the corners, the torii at the
    // entrance and the stairway down the slope until it meets the ground, under a row of torii in 1 of 3 temples. The
    // ground is the top solid block's height; sea is the water's level (the preview's 0, the game's sea level - 1).
    public static Temple temple(Vox w, Site site, IntBinaryOperator ground, int seed, int sea) {
        Rng rng = new Rng(site.seed());
        int cx = site.cx(), cz = site.cz(), y = site.y();
        int hw = rng.nextInt(15, 19), hd = rng.nextInt(13, 16); // the courtyard's half width
        char front = site.front(); // the side looking down the slope
        boolean shinto = rng.next() < 0.5;
        int px0 = cx - hw, px1 = cx + hw, pz0 = cz - hd, pz1 = cz + hd;
        // courtyard and wall
        for (int x = px0 - 1; x <= px1 + 1; x++) {
            for (int z = pz0 - 1; z <= pz1 + 1; z++) {
                int g = ground.applyAsInt(x, z);
                boolean rim = x == px0 - 1 || x == px1 + 1 || z == pz0 - 1 || z == pz1 + 1;
                for (int yy = Math.min(g, y) - 2; yy < y; yy++) {
                    w.force(x, yy, z, rim || yy < y - 1 ? (hash3(x, yy, z, seed + 5) < 0.35 ? MOSSY_STONE : WALL_STONE) : DIRT);
                }
                w.force(x, y, z, rim ? WALL_STONE : GRAVEL_W);
                for (int yy = y + 1; yy <= Math.max(g, y) + 1; yy++) {
                    w.force(x, yy, z, AIR);
                }
                if (rim && hash3(x, 0, z, seed + 6) < 0.8) {
                    w.force(x, y + 1, z, hash3(x, 1, z, seed + 6) < 0.3 ? MOSSY_STONE : WALL_STONE);
                }
            }
        }
        // the temple's axis: from the torii (front) to the hall (back)
        int[] F = dir(front);
        int depth = F[0] != 0 ? hw : hd, width = F[0] != 0 ? hd : hw;
        // the hall at the back
        int sw = rng.nextInt(5, 7), sd = rng.nextInt(4, 5);
        int[] hxz = along(cx, cz, F, -(depth - sd - 3), 0);
        int hx = hxz[0], hz = hxz[1];
        int[] H = F[0] != 0 ? new int[] {hx - sd, hz - sw, hx + sd, hz + sw} : new int[] {hx - sw, hz - sd, hx + sw, hz + sd};
        int hallTop = hall(w, H[0], H[1], H[2], H[3], y, front, shinto, rng);
        // stone path from the gate to the hall
        for (int k = -(depth - sd * 2 - 4); k <= depth; k++) {
            for (int s = -1; s <= 1; s++) {
                int[] p = along(cx, cz, F, k, s);
                w.force(p[0], y, p[1], PATH);
            }
        }
        // lanterns on both sides of the path
        for (int k = -(depth - sd * 2 - 6); k <= depth - 3; k += 4) {
            for (int s : new int[] {-3, 3}) {
                int[] p = along(cx, cz, F, k, s);
                lantern(w, p[0], y + 1, p[1], true);
            }
        }
        // pagoda (or belfry) on one side, the sacred tree on the other
        int side = rng.next() < 0.5 ? 1 : -1;
        int[] g = along(cx, cz, F, floor(-depth * 0.1), side * (width - 6));
        int tallest = hallTop;
        if (rng.next() < 0.6) {
            tallest = Math.max(tallest, pagoda(w, g[0], g[1], y, rng));
        } else {
            belfry(w, g[0], g[1], y);
        }
        int[] t = along(cx, cz, F, floor(depth * 0.25), -side * (width - 5));
        for (int a = -2; a <= 3; a++) {
            for (int b = -2; b <= 3; b++) {
                w.force(t[0] + a, y, t[1] + b, MOSS);
            }
        }
        cedar(w, t[0], y + 1, t[1], site.seed() + 77, true);
        // sakuras in the front corners
        for (int s : new int[] {-1, 1}) {
            int[] p = along(cx, cz, F, depth - 4, s * (width - 4));
            sakura(w, p[0], y + 1, p[1], site.seed() + 90 + s);
        }
        // torii at the entrance
        int[] e = along(cx, cz, F, depth - 1, 0);
        char axis = F[0] != 0 ? 'z' : 'x';
        torii(w, e[0], y + 1, e[1], axis, 3, 7);
        // stairway: down the slope from the courtyard's edge, 5 wide (stone at the edges), at most 1 down per block and
        // following the ground where it is flat (landings); stops at the valley's bottom, where the ground rises again or
        // at the water
        boolean tunnel = rng.next() < 0.34;
        int sy = y, k = depth + 1, flat = 0;
        List<int[]> stairs = new ArrayList<>();
        while (stairs.size() < 90) {
            int[] c = along(cx, cz, F, k, 0);
            int gg = ground.applyAsInt(c[0], c[1]);
            if (gg > sy + 1) {
                break;
            }
                // a cliff over the sea: the stairway keeps going down over the water to the shore
                // (Stark 2026-11-13: it stopped at the top, no way down)
                if (gg < sea + 1) {
                    if (sy <= sea + 1) {
                        break;
                    }
                    gg = sea + 1;
                }
            int ny = Math.max(Math.min(gg, sy), sy - 1);
            flat = ny == sy ? flat + 1 : 0;
            if (flat > 5 && stairs.size() > 8) {
                break;
            }
            sy = ny;
            for (int s = -2; s <= 2; s++) {
                int[] p = along(cx, cz, F, k, s);
                boolean edge = Math.abs(s) == 2;
                int gs = ground.applyAsInt(p[0], p[1]);
                for (int yy = Math.min(gs, sy) - 3; yy <= sy; yy++) {
                    w.force(p[0], yy, p[1], edge ? WALL_STONE : yy == sy ? STEP : WALL_STONE);
                }
                for (int yy = sy + 1; yy <= Math.max(gs, sy) + 3; yy++) {
                    w.force(p[0], yy, p[1], AIR);
                }
                if (edge && stairs.size() % 6 == 3) {
                    lantern(w, p[0], sy + 1, p[1], false);
                }
            }
            stairs.add(new int[] {k, sy});
            k++;
        }
        // the torii tunnel: one every 2 steps
        for (int i = 0; i < stairs.size(); i++) {
            int[] st = stairs.get(i);
            int[] p = along(cx, cz, F, st[0], 0);
            if (tunnel && i % 2 == 1 && i < stairs.size() - 1) {
                torii(w, p[0], st[1] + 1, p[1], axis, 2, 5);
            }
        }
        // the landing at the stairway's foot and the torii there
        if (!stairs.isEmpty()) {
            int[] last = stairs.get(stairs.size() - 1);
            int[] b = along(cx, cz, F, last[0] + 2, 0);
            for (int a = -3; a <= 3; a++) {
                for (int bb = 0; bb <= 3; bb++) {
                    int[] p = along(cx, cz, F, last[0] + 1 + bb, a);
                    for (int yy = Math.min(ground.applyAsInt(p[0], p[1]), last[1]) - 2; yy < last[1]; yy++) {
                        w.force(p[0], yy, p[1], WALL_STONE);
                    }
                    w.force(p[0], last[1], p[1], PATH);
                    for (int yy = last[1] + 1; yy < last[1] + 5; yy++) {
                        w.force(p[0], yy, p[1], AIR);
                    }
                }
            }
            torii(w, b[0], last[1] + 1, b[1], axis, 3, 8);
        }
        return new Temple(cx, cz, y, tallest, front, stairs.size(), tunnel);
    }

    private static int[] along(int cx, int cz, int[] F, int k, int side) {
        return new int[] {cx + F[0] * k + (F[1] != 0 ? side : 0), cz + F[1] * k + (F[0] != 0 ? side : 0)};
    }

    // ---- Escola Jujutsu ---------------------------------------------------------------------------------------------

    // The Tokyo Jujutsu High (Stark 2026-11-12, "muito foda"): a temple-style campus on top of the mountain, hidden in the
    // woods. Two walled terraces: on the lower one the two-storey gate (sanmon) at the top of the stairway, the classroom
    // building (two floors, rows of glass windows, principal Yaga's room with the cursed dolls) and the training field of
    // packed earth with a fence and straw dummies; on the upper one, up the stone stairs in the middle, the main hall, the
    // pagoda, the long dormitory joined to the hall by a covered walkway, Shoko's infirmary and, in the back corner, the
    // little stone house with the dark way down to the Corredor das Estrelas (Master Tengen's), the sacred cedar beside it.
    // A plaster wall with a small roof all around. In the game 1 of every 10 temple spots is the school (isEscolaSite).
    public static final int ESC_D = 46, ESC_W = 40, ESC_UP = 6;
    // How far the school reaches from its center: the terraces (47 along, 41 across, so about 63 at the corners) and the
    // stairway (up to 120 steps from 52, the torii's landing past it).
    public static final int ESCOLA_REACH = 52 + 120 + 6;

    public record School(int cx, int cz, int y, int top, char front, int stairs, int[] yaga, int[] shoko) {
    }

    public static boolean isEscolaSite(Site site, int seed) {
        return hash3(site.cx(), 53, site.cz(), seed) < 0.1;
    }

    private static int[] rect(int cx, int cz, int[] F, int k0, int k1, int s0, int s1) {
        int[] a = along(cx, cz, F, k0, s0), b = along(cx, cz, F, k1, s1);
        return new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    public static School escola(Vox w, Site site, IntBinaryOperator ground, int seed, int sea) {
        Rng rng = new Rng(site.seed());
        int cx = site.cx(), cz = site.cz(), y = site.y();
        char front = site.front();
        int[] F = dir(front);
        // the side the +s looks to (and the -s), as a direction letter
        char sideP = F[0] != 0 ? 's' : 'e', sideN = F[0] != 0 ? 'n' : 'w';
        int y2 = y + ESC_UP;
        // terraces: a stone retaining wall down to the ground, gravel on top, air up past the hill
        for (int k = -ESC_D - 1; k <= ESC_D + 1; k++) {
            for (int s = -ESC_W - 1; s <= ESC_W + 1; s++) {
                int[] p = along(cx, cz, F, k, s);
                int x = p[0], z = p[1];
                int g = ground.applyAsInt(x, z), ly = k <= 7 ? y2 : y;
                boolean rim = Math.abs(k) == ESC_D + 1 || Math.abs(s) == ESC_W + 1;
                for (int yy = Math.min(g, ly) - 2; yy < ly; yy++) {
                    w.force(x, yy, z, rim || yy < ly - 1 ? (hash3(x, yy, z, seed + 5) < 0.3 ? MOSSY_STONE : WALL_STONE) : DIRT);
                }
                w.force(x, ly, z, rim ? WALL_STONE : GRAVEL_W);
                for (int yy = ly + 1; yy <= Math.max(g, ly) + 1; yy++) {
                    w.force(x, yy, z, AIR);
                }
            }
        }
        // the step between the terraces: a stone retaining wall with the wide stairs in the middle
        for (int s = -ESC_W; s <= ESC_W; s++) {
            if (Math.abs(s) <= 4) {
                continue;
            }
            int[] p = along(cx, cz, F, 8, s);
            for (int yy = y; yy <= y2; yy++) {
                w.force(p[0], yy, p[1], hash3(p[0], yy, p[1], seed + 6) < 0.25 ? MOSSY_STONE : WALL_STONE);
            }
        }
        for (int j = 1; j <= ESC_UP; j++) {
            for (int s = -4; s <= 4; s++) {
                int[] p = along(cx, cz, F, 7 + j, s);
                int sy = y2 - j;
                for (int yy = y; yy <= sy; yy++) {
                    w.force(p[0], yy, p[1], Math.abs(s) == 4 ? WALL_STONE : yy == sy ? STEP : WALL_STONE);
                }
                for (int yy = sy + 1; yy <= y2 + 2; yy++) {
                    if (Math.abs(s) < 4) {
                        w.force(p[0], yy, p[1], AIR);
                    }
                }
            }
        }
        // the plaster wall with a small roof all around (tsuijibei), open at the gate
        for (int k = -ESC_D; k <= ESC_D; k++) {
            for (int s = -ESC_W; s <= ESC_W; s++) {
                if (Math.abs(k) != ESC_D && Math.abs(s) != ESC_W) {
                    continue;
                }
                if (k == ESC_D && Math.abs(s) <= 7) {
                    continue;
                }
                int[] p = along(cx, cz, F, k, s);
                int ly = k <= 7 ? y2 : y;
                w.force(p[0], ly + 1, p[1], WALL_STONE);
                w.force(p[0], ly + 2, p[1], PLASTER);
                w.force(p[0], ly + 3, p[1], PLASTER);
                w.force(p[0], ly + 4, p[1], (k + s) % 6 == 0 ? WOOD_DARK : PLASTER);
                w.force(p[0], ly + 5, p[1], ROOF_TILE);
            }
        }
        // the stone path from the gate to the middle stairs and from them to the hall
        for (int k = -24; k <= ESC_D; k++) {
            for (int s = -2; s <= 2; s++) {
                if (k >= 8 && k <= 13) {
                    continue;
                }
                int[] p = along(cx, cz, F, k, s);
                w.force(p[0], k <= 7 ? y2 : y, p[1], PATH);
            }
        }
        for (int k = 16; k <= ESC_D - 6; k += 5) {
            for (int s : new int[] {-4, 4}) {
                int[] p = along(cx, cz, F, k, s);
                lantern(w, p[0], y + 1, p[1], true);
            }
        }
        for (int k = -20; k <= 2; k += 5) {
            for (int s : new int[] {-4, 4}) {
                int[] p = along(cx, cz, F, k, s);
                lantern(w, p[0], y2 + 1, p[1], true);
            }
        }

        // the two-storey gate (sanmon) in the middle of the front wall
        {
            int gk = ESC_D;
            for (int s : new int[] {-6, -2, 2, 6}) {
                for (int dk : new int[] {-2, 2}) {
                    int[] p = along(cx, cz, F, gk + dk, s);
                    for (int k = 1; k <= 7; k++) {
                        w.force(p[0], y + k, p[1], WOOD_DARK);
                    }
                }
            }
            for (int s = -6; s <= 6; s++) {
                for (int dk = -2; dk <= 2; dk++) {
                    int[] p = along(cx, cz, F, gk + dk, s);
                    w.force(p[0], y + 8, p[1], WOOD_DARK);
                    w.force(p[0], y, p[1], PATH);
                }
            }
            int[] r1 = rect(cx, cz, F, gk - 4, gk + 4, -9, 9);
            roof(w, r1[0], r1[1], r1[2], r1[3], y + 9, 1);
            // the upper storey: lattice walls set back, and the upper roof
            for (int s = -5; s <= 5; s++) {
                for (int dk = -1; dk <= 1; dk++) {
                    if (Math.abs(s) != 5 && Math.abs(dk) != 1) {
                        continue;
                    }
                    int[] p = along(cx, cz, F, gk + dk, s);
                    for (int k = 10; k <= 13; k++) {
                        w.force(p[0], y + k, p[1], Math.abs(s) == 5 && Math.abs(dk) == 1 ? VERMILION : k == 13 ? WOOD_DARK : LATTICE);
                    }
                }
            }
            int[] r2 = rect(cx, cz, F, gk - 3, gk + 3, -8, 8);
            roof(w, r2[0], r2[1], r2[2], r2[3], y + 14, 2);
            int[] p = along(cx, cz, F, gk + 2, 0);
            w.force(p[0], y + 7, p[1], GOLD);
        }

        // the classroom building: two floors, the row of windows on both, an eave between the floors
        {
            int[] cls = rect(cx, cz, F, 14, 36, -37, -15);
            int x0 = cls[0], z0 = cls[1], x1 = cls[2], z1 = cls[3];
            int fy = y + 1, story = 5;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    w.force(x, y, z, WALL_STONE);
                    w.force(x, fy + story - 1, z, FLOOR_WOOD);
                }
            }
            for (int f = 0; f < 2; f++) {
                for (int yy = fy + f * story; yy < fy + (f + 1) * story; yy++) {
                    for (int x = x0; x <= x1; x++) {
                        for (int z = z0; z <= z1; z++) {
                            boolean edgeX = x == x0 || x == x1, edgeZ = z == z0 || z == z1;
                            if (!edgeX && !edgeZ) {
                                continue;
                            }
                            int u = edgeZ ? x - x0 : z - z0;
                            boolean corner = edgeX && edgeZ;
                            int k = yy - (fy + f * story);
                            byte t;
                            if (corner || u % 4 == 0) {
                                t = WOOD_DARK;
                            } else if (k == story - 1) {
                                t = WOOD;
                            } else if (k == 0) {
                                t = PLASTER;
                            } else {
                                t = (k == 1 || k == 2) ? GLASS : PLASTER;
                            }
                            w.force(x, yy, z, t);
                        }
                    }
                }
            }
            // doors on the side facing the path (+s)
            int[] d = along(cx, cz, F, 25, -15);
            for (int a = -1; a <= 1; a++) {
                for (int yy = fy; yy <= fy + 2; yy++) {
                    int x = F[1] != 0 ? d[0] : d[0] + a, z = F[1] != 0 ? d[1] + a : d[1];
                    w.force(x, yy, z, yy == fy + 2 ? WOOD : DOOR);
                }
            }
            // the eave between the floors, and the hip roof
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    if (x != x0 - 1 && x != x1 + 1 && z != z0 - 1 && z != z1 + 1) {
                        continue;
                    }
                    w.force(x, fy + story, z, ROOF_EAVE);
                }
            }
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    w.force(x, fy + story * 2, z, WOOD_DARK);
                }
            }
            roof(w, x0 - 2, z0 - 2, x1 + 2, z1 + 2, fy + story * 2 + 1, 2);
        }
        // Yaga at his room's door, with three cursed dolls on the porch
        int[] yagaAt = along(cx, cz, F, 25, -12);
        int[] yaga = {yagaAt[0], y + 1, yagaAt[1]};
        for (int[] ks : new int[][] {{20, -13}, {30, -13}, {31, -12}}) {
            int[] p = along(cx, cz, F, ks[0], ks[1]);
            w.force(p[0], y + 1, p[1], STRAW);
            w.force(p[0], y + 2, p[1], LANTERN_STONE);
        }

        // the training field: packed earth, a wooden fence, straw dummies and the bench
        {
            for (int k = 14; k <= 40; k++) {
                for (int s = 12; s <= 38; s++) {
                    int[] p = along(cx, cz, F, k, s);
                    int x = p[0], z = p[1];
                    boolean edge = k == 14 || k == 40 || s == 12 || s == 38;
                    w.force(x, y, z, hash3(x, 0, z, seed + 40) < 0.08 ? DIRT : PACKED);
                    if (edge) {
                        boolean post = (k + s) % 3 == 0;
                        if (post) {
                            w.force(x, y + 1, z, WOOD_DARK);
                            w.force(x, y + 2, z, WOOD_DARK);
                        } else if (!(s == 12 && k >= 25 && k <= 28)) {
                            w.force(x, y + 2, z, WOOD);
                        }
                    }
                }
            }
            // dummies: a post, a straw body, a wooden arm across, some crooked from all the beating
            for (int i = 0; i < 5; i++) {
                int k = 18 + i * 5 + rng.nextInt(-1, 1), s = rng.nextInt(20, 32);
                int[] p = along(cx, cz, F, k, s);
                int x = p[0], z = p[1];
                w.force(x, y + 1, z, WOOD_DARK);
                w.force(x, y + 2, z, STRAW);
                w.force(x, y + 3, z, STRAW);
                boolean tilt = rng.next() < 0.35;
                int[] hp = tilt ? along(cx, cz, F, k + 1, s) : new int[] {x, z};
                w.force(hp[0], y + 4, hp[1], STRAW);
                for (int u : new int[] {-1, 1}) {
                    int ax = F[1] != 0 ? x + u : x, az = F[1] != 0 ? z : z + u;
                    w.force(ax, y + 3, az, WOOD);
                }
            }
            for (int k = 20; k <= 32; k++) {
                int[] p = along(cx, cz, F, k, 36);
                w.force(p[0], y + 1, p[1], WOOD);
            }
        }

        // the main hall at the back of the upper terrace
        int[] H = rect(cx, cz, F, -42, -26, -13, 13);
        int hallTop = hall(w, H[0], H[1], H[2], H[3], y2, front, true, rng);
        // the pagoda on one side
        int[] pg = along(cx, cz, F, -30, 28);
        int pagTop = pagoda(w, pg[0], pg[1], y2, rng);
        // the long dormitory on the other side, looking to the middle (+s)
        int[] D = rect(cx, cz, F, -22, 4, -37, -22);
        hall(w, D[0], D[1], D[2], D[3], y2, sideP, false, rng);
        // the covered walkway from the hall to the dormitory
        for (int s = -21; s <= -14; s++) {
            for (int dk = -1; dk <= 1; dk++) {
                int[] p = along(cx, cz, F, -30 + dk, s);
                w.force(p[0], y2 + 1, p[1], FLOOR_WOOD);
                if (Math.abs(dk) == 1 && s % 3 == 0) {
                    for (int k = 2; k <= 4; k++) {
                        w.force(p[0], y2 + k, p[1], WOOD_DARK);
                    }
                }
                w.force(p[0], y2 + 5, p[1], WOOD_DARK);
            }
        }
        {
            int[] r = rect(cx, cz, F, -32, -28, -22, -13);
            roof(w, r[0], r[1], r[2], r[3], y2 + 6, 0);
        }
        // Shoko's infirmary (looking to the middle, -s)
        int[] E = rect(cx, cz, F, -14, 0, 17, 29);
        hall(w, E[0], E[1], E[2], E[3], y2, sideN, false, rng);
        int[] shokoAt = along(cx, cz, F, -7, 15);
        int[] shoko = {shokoAt[0], y2 + 1, shokoAt[1]};
        // the way down to the Corredor das Estrelas: a little stone house in the back corner, a dark door, stairs that
        // vanish into the dark
        {
            int k0 = -44, s0 = 34;
            for (int dk = -3; dk <= 3; dk++) {
                for (int ds = -3; ds <= 3; ds++) {
                    int[] p = along(cx, cz, F, k0 + dk, s0 + ds);
                    boolean wall = Math.abs(dk) == 3 || Math.abs(ds) == 3;
                    for (int k = 1; k <= 4; k++) {
                        w.force(p[0], y2 + k, p[1], wall ? MOSSY_STONE : AIR);
                    }
                    w.force(p[0], y2 + 5, p[1], ROOF_TILE);
                }
            }
            int[] rp = along(cx, cz, F, k0 - 4, s0);
            w.force(rp[0], y2 + 6, rp[1], ROOF_RIDGE);
            // the door on the side looking to the front
            for (int ds = -1; ds <= 1; ds++) {
                for (int k = 1; k <= 3; k++) {
                    int[] p = along(cx, cz, F, k0 + 3, s0 + ds);
                    w.force(p[0], y2 + k, p[1], AIR);
                }
            }
            // stairs going down to the back, darker and darker, and the dark at the end
            for (int j = 0; j < 9; j++) {
                for (int ds = -1; ds <= 1; ds++) {
                    int[] p = along(cx, cz, F, k0 + 2 - j, s0 + ds);
                    int sy = y2 - j;
                    w.force(p[0], sy, p[1], STEP);
                    for (int yy = sy + 1; yy <= sy + 3; yy++) {
                        w.force(p[0], yy, p[1], j > 5 ? DARK : AIR);
                    }
                }
            }
            int[] t = along(cx, cz, F, k0 + 6, s0 - 6);
            for (int a = -2; a <= 3; a++) {
                for (int b = -2; b <= 3; b++) {
                    w.force(t[0] + a, y2, t[1] + b, MOSS);
                }
            }
            cedar(w, t[0], y2 + 1, t[1], site.seed() + 77, true);
        }
        // sakuras: in the terraces' corners and along the inner wall
        for (int[] ks : new int[][] {{42, -36}, {42, 36}, {11, -30}, {11, 30}, {-44, -36}, {-12, 36}}) {
            int[] p = along(cx, cz, F, ks[0], ks[1]);
            sakura(w, p[0], (ks[0] <= 7 ? y2 : y) + 1, p[1], site.seed() + 200 + ks[0] * 7 + ks[1]);
        }
        // the stairway from the gate down the hill, wide, with landings and lanterns; a torii at its foot
        char axis = F[0] != 0 ? 'z' : 'x';
        int sy = y, k = ESC_D + 6, flat = 0;
        List<int[]> stairs = new ArrayList<>();
        for (int kk = ESC_D + 2; kk < ESC_D + 6; kk++) {
            for (int s = -4; s <= 4; s++) {
                int[] p = along(cx, cz, F, kk, s);
                int g = ground.applyAsInt(p[0], p[1]);
                for (int yy = Math.min(g, y) - 2; yy <= y; yy++) {
                    w.force(p[0], yy, p[1], yy == y ? PATH : WALL_STONE);
                }
                for (int yy = y + 1; yy <= Math.max(g, y) + 3; yy++) {
                    w.force(p[0], yy, p[1], AIR);
                }
            }
        }
        while (stairs.size() < 120) {
            int[] c = along(cx, cz, F, k, 0);
            int g = ground.applyAsInt(c[0], c[1]);
            if (g > sy + 1) {
                break;
            }
                // a cliff over the sea: the stairway keeps going down over the water to the shore
                // (Stark 2026-11-13: it stopped at the top, no way down)
                if (g < sea + 1) {
                    if (sy <= sea + 1) {
                        break;
                    }
                    g = sea + 1;
                }
            int ny = Math.max(Math.min(g, sy), sy - 1);
            flat = ny == sy ? flat + 1 : 0;
            if (flat > 5 && stairs.size() > 8) {
                break;
            }
            sy = ny;
            for (int s = -4; s <= 4; s++) {
                int[] p = along(cx, cz, F, k, s);
                boolean edge = Math.abs(s) == 4;
                int gs = ground.applyAsInt(p[0], p[1]);
                for (int yy = Math.min(gs, sy) - 3; yy <= sy; yy++) {
                    w.force(p[0], yy, p[1], edge ? WALL_STONE : yy == sy ? STEP : WALL_STONE);
                }
                for (int yy = sy + 1; yy <= Math.max(gs, sy) + 3; yy++) {
                    w.force(p[0], yy, p[1], AIR);
                }
                if (edge && stairs.size() % 8 == 4) {
                    lantern(w, p[0], sy + 1, p[1], true);
                }
            }
            stairs.add(new int[] {k, sy});
            k++;
        }
        if (!stairs.isEmpty()) {
            int[] last = stairs.get(stairs.size() - 1);
            int[] b = along(cx, cz, F, last[0] + 2, 0);
            for (int a = -5; a <= 5; a++) {
                for (int bb = 0; bb <= 3; bb++) {
                    int[] p = along(cx, cz, F, last[0] + 1 + bb, a);
                    for (int yy = Math.min(ground.applyAsInt(p[0], p[1]), last[1]) - 2; yy < last[1]; yy++) {
                        w.force(p[0], yy, p[1], WALL_STONE);
                    }
                    w.force(p[0], last[1], p[1], PATH);
                    for (int yy = last[1] + 1; yy < last[1] + 5; yy++) {
                        w.force(p[0], yy, p[1], AIR);
                    }
                }
            }
            torii(w, b[0], last[1] + 1, b[1], axis, 5, 10);
        }
        return new School(cx, cz, y, Math.max(hallTop, pagTop), front, stairs.size(), yaga, shoko);
    }

    // A small shrine of the woods (hokora): a little wooden house on a stone plinth, with a small torii in front.
    public static void hokora(Vox w, int x, int y, int z, int seed, IntBinaryOperator ground) {
        Rng rng = new Rng(seed);
        int s0 = new Rng(seed).next() < 0.5 ? 1 : -1;
        // a footing down to the ground under the plinth, the torii's posts and the lantern (Stark 2026-11-13: on a slope
        // the torii and the lantern hung in the air)
        java.util.List<int[]> feet = new java.util.ArrayList<>();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                feet.add(new int[] {x + a, z + b, y});
            }
        }
        feet.add(new int[] {x - 1, z + 3 * s0, y + 1});
        feet.add(new int[] {x + 1, z + 3 * s0, y + 1});
        feet.add(new int[] {x + 2 * s0, z + 2 * s0, y + 1});
        for (int[] f : feet) {
            for (int yy = ground.applyAsInt(f[0], f[1]) + 1; yy < f[2]; yy++) {
                w.force(f[0], yy, f[1], MOSSY_STONE);
            }
        }
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                w.force(x + a, y, z + b, MOSSY_STONE);
            }
        }
        w.force(x, y + 1, z, WOOD);
        w.force(x, y + 2, z, WOOD);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                w.force(x + a, y + 3, z + b, ROOF_TILE);
            }
        }
        w.force(x, y + 4, z, ROOF_RIDGE);
        int s = rng.next() < 0.5 ? 1 : -1;
        for (int u : new int[] {-1, 1}) {
            for (int k = 0; k < 3; k++) {
                w.force(x + u, y + 1 + k, z + 3 * s, TORII);
            }
        }
        for (int u = -2; u <= 2; u++) {
            w.force(x + u, y + 4, z + 3 * s, TORII_TOP);
        }
        lantern(w, x + 2 * s, y + 1, z + 2 * s, false);
    }

    // ---- the biome's pieces ---------------------------------------------------------------------------------------

    // A temple's spot: one per SITE_CELL cell, the flattest of 16 tries, only well above the water. The front looks down
    // the biggest drop. Null when the cell has none.
    public static Site siteIn(int ci, int cj, int seed, IntBinaryOperator ground, int sea) {
        Rng rng = new Rng(hashInt(ci, cj, 51, seed));
        if (rng.next() > 0.75) {
            return null;
        }
        int bx = 0, bz = 0, bg = 0;
        double bs = 0;
        boolean found = false;
        for (int t = 0; t < 16; t++) {
            int x = ci * SITE_CELL + rng.nextInt(30, SITE_CELL - 30), z = cj * SITE_CELL + rng.nextInt(30, SITE_CELL - 30);
            int g = ground.applyAsInt(x, z);
            if (g < sea + 8) {
                continue;
            }
            int mx = Integer.MIN_VALUE, mn = Integer.MAX_VALUE;
            for (int a = -16; a <= 16; a += 8) {
                for (int b = -14; b <= 14; b += 7) {
                    int h = ground.applyAsInt(x + a, z + b);
                    mx = Math.max(mx, h);
                    mn = Math.min(mn, h);
                }
            }
            // up high: a nearly flat courtyard, but with a strong drop to some side (the long stairway)
            int drop = Math.max(Math.max(g - ground.applyAsInt(x, z - 40), g - ground.applyAsInt(x, z + 40)),
                    Math.max(g - ground.applyAsInt(x - 40, z), g - ground.applyAsInt(x + 40, z)));
            double score = (mx - mn) - 0.7 * drop;
            if (!found || score < bs) {
                found = true;
                bx = x;
                bz = z;
                bg = mx + 1;
                bs = score;
            }
        }
        if (!found) {
            return null;
        }
        // the preview sorts n, s, w, e by the drop (a stable sort): the first of the biggest
        char[] sides = {'n', 's', 'w', 'e'};
        int[] drops = {bg - ground.applyAsInt(bx, bz - 40), bg - ground.applyAsInt(bx, bz + 40), bg - ground.applyAsInt(bx - 40, bz),
                bg - ground.applyAsInt(bx + 40, bz)};
        int best = 0;
        for (int i = 1; i < 4; i++) {
            if (drops[i] > drops[best]) {
                best = i;
            }
        }
        return new Site(bx, bz, bg, sides[best], hashInt(ci, cj, 52, seed));
    }

    // The ground's top in the woods (the preview's templePiece): moss stone or bare stone on the steep slopes, pink petals
    // in the sakura groves, needles under the cedars, moss in patches, grass.
    public static boolean sakuraZone(int x, int z, int seed) {
        return valueNoise(x, z, seed + 31, 55) > 0.6;
    }

    public static byte groundTop(int x, int h, int z, int slope, int seed) {
        if (slope >= 3) {
            return hash3(x, h, z, seed + 9) < 0.5 ? MOSSY_STONE : STONE;
        }
        if (sakuraZone(x, z, seed)) {
            return hash3(x, 0, z, seed + 10) < 0.55 ? PETALS : GRASS;
        }
        return valueNoise(x, z, seed + 12, 9) > 0.55 ? NEEDLES : (hash3(x, 0, z, seed + 11) < 0.3 ? MOSS : GRASS);
    }

    // A tree's spot on the grid of 5 (gx, gz on the grid): its column, or a fern/shrub, or nothing.
    public record Spot(int x, int z, int kind) {
        public static final int CEDAR = 1, SAKURA = 2, PLANT = 3;
    }

    // What the grid point (gx, gz) holds, before the checks of the ground (those are the caller's): the preview's draw.
    public static Spot spot(int gx, int gz, int seed) {
        int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 5), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 5);
        boolean sk = sakuraZone(x, z, seed);
        double dens = sk ? 0.45 : 0.72;
        if (hash3(x, 3, z, seed + 20) > dens) {
            return hash3(x, 4, z, seed + 21) < 0.25 ? new Spot(x, z, Spot.PLANT) : null;
        }
        return new Spot(x, z, sk ? Spot.SAKURA : Spot.CEDAR);
    }

    public static byte plantAt(int x, int z, int seed) {
        return hash3(x, 5, z, seed) < 0.5 ? FERN : SHRUB;
    }

    // The loose ferns and shrubs, every 2 blocks.
    public static byte scatterAt(int x, int z, int seed) {
        if ((x & 1) != 0 || (z & 1) != 0 || hash3(x, 9, z, seed + 25) > 0.08) {
            return AIR;
        }
        return hash3(x, 8, z, seed) < 0.6 ? FERN : SHRUB;
    }

    public static void tree(Vox w, Spot s, int h, int seed) {
        if (s.kind() == Spot.SAKURA) {
            sakura(w, s.x(), h + 1, s.z(), hashInt(s.x(), h, s.z(), seed + 22));
        } else {
            cedar(w, s.x(), h + 1, s.z(), hashInt(s.x(), h, s.z(), seed + 23), false);
        }
    }

    // A hokora's spot on the grid of 48, or null.
    public static int[] hokoraSpot(int gx, int gz, int seed) {
        if (hash3(gx, 7, gz, seed + 26) > 0.35) {
            return null;
        }
        return new int[] {gx + 10 + floor(hash3(gx, 8, gz, seed + 26) * 28), gz + 10 + floor(hash3(gx, 9, gz, seed + 26) * 28)};
    }

    // Around a hokora the woods give way: leaves, ferns and shrubs within 3 and up to 7 above the ground are cleared.
    public static boolean clearedByHokora(int t) {
        return t == CEDAR_LEAF || t == CEDAR_LEAF2 || t == FERN || t == SHRUB;
    }
}
