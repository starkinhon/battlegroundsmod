package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.starkinhon.battlegroundsmod.world.DesertGen.Vox;
import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;

// The Mar da Névoa (Stark 2026-11-11, "um dos mais bonitos até agora", the approved preview built from
// /mnt/project-files/forja/naruto-nevoa/gen.js). That generator line by line, like DesertGen: the same steps in the same
// order with the same random, so a seed makes here what it makes in the preview. Change one, change the other.
// The Land of Water (Kirigakure): a calm grey sea and, rising out of it, tall straight stone pillars with green tops and
// crooked coastal pines (crowns in flat pads), low islets of stone and grass, groups of thin stone needles, the posts of
// an old pier. Between neighbouring pillars, rope-and-plank bridges. The water ate the stone at its line (a notch with
// moss); vines hang down the cliffs. The sea is the game's: the bottom stays vanilla.
public final class SeaGen {
    public static final byte AIR = 0, STONE = 1, ANDESITE = 2, TUFF = 3, MOSSY = 4, GRASS = 5, DIRT = 6, MOSS = 7, LOG = 8, PINE = 9,
            PINE_DARK = 10, VINE = 11, PLANK = 12, ROPE = 13, POST = 14, GRAVEL = 15, SAND = 16, CLAY = 17,
            // the game's rope runs along one axis per block (ROPE is along x)
            ROPE_Z = 18, ROPE_Y = 19;

    private SeaGen() {
    }

    private static double hash3(int x, int y, int z, int seed) {
        return LeafTreeGen.hash3(x, y, z, seed);
    }

    private static double valueNoise(double x, double z, int seed, double scale) {
        return DesertGen.valueNoise(x, z, seed, scale);
    }

    private static double valueNoise3(double x, double y, double z, int seed, double scale) {
        return DesertGen.valueNoise3(x, y, z, seed, scale);
    }

    private static int floor(double a) {
        return (int) Math.floor(a);
    }

    private static int round(double a) {
        return (int) Math.round(a);
    }

    // The rock, by height in the world: grey with bands of andesite and tuff.
    static byte rockAt(int x, int y, int z, int seed) {
        int k = floor(y + (valueNoise(x, z, seed + 50, 30) - 0.5) * 3);
        int m = ((k % 11) + 11) % 11;
        if (m == 3 || m == 4) {
            return ANDESITE;
        }
        if (m == 8) {
            return TUFF;
        }
        return STONE;
    }

    // ---- coastal pine -------------------------------------------------------------------------------------------

    private record V(double x, double y, double z) {
        V norm() {
            double l = Math.sqrt(x * x + y * y + z * z);
            if (l == 0) {
                l = 1;
            }
            return new V(x / l, y / l, z / l);
        }
    }

    private record Pad(double x, double y, double z, double rx, double ry) {
    }

    // A crooked pine: the trunk leaves leaning and bends (the sea wind), boughs almost lying down and the crown in flat
    // pads, dark underneath. Like the black pines of the Japanese islands.
    static int pine(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(6, 11);
        double lean = rng.range(0, 6.28), tilt = rng.range(0.15, 0.55);
        V p = new V(ox + 0.5, oy, oz + 0.5);
        V d = new V(Math.cos(lean) * tilt, 1, Math.sin(lean) * tilt).norm();
        List<Pad> pads = new ArrayList<>();
        for (int i = 0; i <= H; i++) {
            w.set(floor(p.x), floor(p.y), floor(p.z), LOG);
            if (i < 2) {
                w.set(floor(p.x) + 1, floor(p.y), floor(p.z), LOG);
                w.set(floor(p.x), floor(p.y), floor(p.z) + 1, LOG);
            }
            if (i >= floor(H * 0.45) && i < H && rng.next() < 0.45) {
                // a bough almost lying down with a pad at its end
                double a = rng.range(0, 6.28);
                int L = rng.nextInt(2, 5);
                V q = p;
                for (int k = 0; k < L; k++) {
                    q = new V(q.x + Math.cos(a), q.y + (k % 2 == 1 ? 1 : 0) * 0.5, q.z + Math.sin(a));
                    w.set(floor(q.x), floor(q.y), floor(q.z), LOG);
                }
                double rx = rng.range(1.8, 3.0), ry = rng.range(0.7, 1.0);
                pads.add(new Pad(q.x, q.y + 0.6, q.z, rx, ry));
            }
            // the trunk bends: climbs more at the tip
            double ax = rng.range(-0.18, 0.18), az;
            double dy = d.y + 0.08;
            az = rng.range(-0.18, 0.18);
            d = new V(d.x + ax, dy, d.z + az).norm();
            p = new V(p.x + d.x, p.y + d.y, p.z + d.z);
        }
        double rx = rng.range(2.6, 3.8), ry = rng.range(0.9, 1.3);
        pads.add(new Pad(p.x, p.y + 0.3, p.z, rx, ry));
        for (Pad c : pads) {
            int nx = (int) Math.ceil(c.rx), ny = (int) Math.ceil(c.ry) + 1;
            for (int dx = -nx; dx <= nx; dx++) {
                for (int dy = -ny; dy <= ny; dy++) {
                    for (int dz = -nx; dz <= nx; dz++) {
                        int x = floor(c.x) + dx, y = floor(c.y) + dy, z = floor(c.z) + dz;
                        double qx = (x + 0.5 - c.x) / c.rx, qy = (y + 0.5 - c.y) / c.ry, qz = (z + 0.5 - c.z) / c.rx;
                        double q = qx * qx + qy * qy + qz * qz;
                        if (q > 1) {
                            continue;
                        }
                        if (q > 0.6 && hash3(x, y, z, seed) < 0.35) {
                            continue;
                        }
                        w.set(x, y, z, y + 0.5 < c.y - 0.2 ? PINE_DARK : PINE);
                    }
                }
            }
        }
        return (int) Math.ceil(p.y - oy + 2);
    }

    // ---- rock in the sea ----------------------------------------------------------------------------------------

    // A pillar's size comes first out of its random, so a bridge knows the height without building the pillar.
    public record Size(int H, double R, Rng rng) {
    }

    public static Size pillarSize(int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(10, 34);
        double R = rng.range(3.2, 7.5);
        return new Size(H, R, rng);
    }

    // A pillar: straight up from the bottom to well above the sea, a little wider below, the rock eaten at the water
    // (a notch with moss), a grass top with moss on the rim, vines on the cliffs, one to three pines on top.
    static void pillar(Vox w, int ox, int floor, int oz, int seed, int sea) {
        Size s = pillarSize(seed);
        int H = s.H;
        double R = s.R;
        Rng rng = s.rng;
        int top = sea + H;
        int n = (int) Math.ceil(R * 1.45 + 2);
        double ph = rng.range(0, 6.28);
        for (int y = floor - 1; y <= top; y++) {
            double t = (y - floor) / (double) Math.max(1, top - floor);
            double r0 = R * (1.35 - 0.35 * Math.sqrt(t));
            if (y >= sea - 2 && y <= sea + 1) {
                r0 *= 0.86; // the water's notch
            }
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    int x = ox + dx, z = oz + dz;
                    double d = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
                    // an uneven plan and vertical grooves
                    double r = r0 * (0.8 + 0.32 * valueNoise(a * R + ph, 0, seed + 3, R * 0.8))
                            * (0.9 + 0.2 * valueNoise3(x, y * 0.4, z, seed + 4, 3));
                    if (d > r) {
                        continue;
                    }
                    boolean shell = d > r - 1.3;
                    byte t2;
                    if (y == top) {
                        t2 = GRASS;
                    } else if (y >= top - 2) {
                        t2 = shell && hash3(x, y, z, seed + 5) < 0.4 ? MOSS : DIRT;
                    } else if (shell && y >= sea - 3 && y <= sea + 1) {
                        t2 = MOSSY;
                    } else {
                        t2 = rockAt(x, y, z, seed);
                    }
                    w.force(x, y, z, t2);
                    // a vine down the cliff
                    if (shell && y < top - 2 && y > sea + 1 && hash3(x, 11, z, seed) < 0.05 && hash3(x, y, z, seed + 6) < 0.6) {
                        double ux = d > 0 ? dx / d : 1, uz = d > 0 ? dz / d : 0;
                        w.set(round(x + ux), y, round(z + uz), VINE);
                    }
                }
            }
        }
        int trees = R > 4.2 ? rng.nextInt(1, 3) : rng.nextInt(0, 1);
        for (int i = 0; i < trees; i++) {
            double a = rng.range(0, 6.28), dd = rng.range(0, R * 0.55);
            int x = round(ox + Math.cos(a) * dd), z = round(oz + Math.sin(a) * dd);
            if (w.get(x, top, z) != GRASS) {
                continue;
            }
            pine(w, x, top + 1, z, floor(rng.next() * 1e9) + 1);
        }
    }

    // A low islet: a dome of stone barely out of the water, gravel on the shore, grass on top, pines and loose rocks.
    static void islet(Vox w, int ox, int floor, int oz, int seed, int sea) {
        Rng rng = new Rng(seed);
        double R = rng.range(11, 19), Hm = rng.range(3, 6.5);
        int n = (int) Math.ceil(R * 1.3 + 2);
        List<int[]> tops = new ArrayList<>();
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                int x = ox + dx, z = oz + dz;
                double a = Math.atan2(dz, dx);
                double rr = R * (0.75 + 0.4 * valueNoise(a * R, 0, seed + 3, R * 0.7));
                double d = Math.hypot(dx, dz) / rr;
                if (d > 1.25) {
                    continue;
                }
                double h = sea + Hm * (1 - d * d) + (valueNoise(x, z, seed + 4, 5) - 0.5) * 1.6 - 0.6;
                int ty = floor(h);
                for (int y = floor - 1; y <= ty; y++) {
                    byte t = rockAt(x, y, z, seed);
                    if (y == ty) {
                        t = ty <= sea ? (hash3(x, y, z, seed) < 0.5 ? GRAVEL : MOSSY) : (ty == sea + 1 && d > 0.75 ? GRAVEL : GRASS);
                    } else if (y >= ty - 1 && ty > sea) {
                        t = DIRT;
                    }
                    w.force(x, y, z, t);
                }
                if (ty > sea + 1 && d < 0.7) {
                    tops.add(new int[] {x, ty, z});
                }
            }
        }
        int trees = rng.nextInt(2, 5);
        for (int i = 0; i < trees && !tops.isEmpty(); i++) {
            int[] t = tops.get(floor(rng.next() * tops.size()));
            pine(w, t[0], t[1] + 1, t[2], floor(rng.next() * 1e9) + 1);
        }
        int rocks = rng.nextInt(1, 3);
        for (int i = 0; i < rocks && !tops.isEmpty(); i++) {
            int[] t = tops.get(floor(rng.next() * tops.size()));
            int x = t[0], y = t[1], z = t[2];
            double r = rng.range(1.0, 1.8);
            for (int ex = -2; ex <= 2; ex++) {
                for (int ey = 0; ey <= 2; ey++) {
                    for (int ez = -2; ez <= 2; ez++) {
                        double e = ey * 1.3;
                        if (ex * ex + e * e + ez * ez <= r * r) {
                            w.set(x + ex, y + 1 + ey, z + ez, hash3(x + ex, ey, z + ez, seed) < 0.5 ? MOSSY : STONE);
                        }
                    }
                }
            }
        }
    }

    // Stone needles in a group: thin, no green top, eaten by the water.
    static void stacks(Vox w, int ox, int floor, int oz, int seed, int sea) {
        Rng rng = new Rng(seed);
        int n = rng.nextInt(2, 4);
        for (int i = 0; i < n; i++) {
            double a = rng.range(0, 6.28), dd = i == 0 ? 0 : rng.range(2.5, 6);
            int cx = round(ox + Math.cos(a) * dd), cz = round(oz + Math.sin(a) * dd);
            int H = rng.nextInt(3, 14);
            double r0 = rng.range(1.2, 2.6);
            for (int y = floor - 1; y <= sea + H; y++) {
                double t = (y - floor) / (double) Math.max(1, sea + H - floor);
                double r = r0 * (1.4 - 0.7 * t);
                if (y >= sea - 2 && y <= sea + 1) {
                    r *= 0.8;
                }
                int m = (int) Math.ceil(r + 1);
                for (int dx = -m; dx <= m; dx++) {
                    for (int dz = -m; dz <= m; dz++) {
                        int x = cx + dx, z = cz + dz;
                        double rr = r * (0.8 + 0.35 * valueNoise3(x, y * 0.5, z, seed + 7, 2));
                        if (dx * dx + dz * dz > rr * rr) {
                            continue;
                        }
                        w.force(x, y, z, y >= sea - 3 && y <= sea + 1 ? MOSSY : rockAt(x, y, z, seed));
                    }
                }
            }
        }
    }

    // An old pier: two rows of posts out of the water, some crooked and broken, a few planks still on top.
    static void pier(Vox w, int ox, int floor, int oz, int seed, int sea) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, 6.28);
        int L = rng.nextInt(4, 9);
        double dx = Math.cos(a), dz = Math.sin(a), sx = -dz, sz = dx;
        for (int i = 0; i < L; i++) {
            for (double s : new double[] {-1.5, 1.5}) {
                int x = round(ox + dx * i * 2.5 + sx * s), z = round(oz + dz * i * 2.5 + sz * s);
                int h = sea + (hash3(x, 1, z, seed) < 0.3 ? rng.nextInt(-1, 0) : rng.nextInt(1, 2));
                for (int y = floor; y <= h; y++) {
                    w.set(x, y, z, POST);
                }
                if (h > sea && hash3(i, 2, 0, seed) < 0.5) {
                    // a plank left between the two posts
                    for (int k = -1; k <= 1; k++) {
                        w.set(round(ox + dx * i * 2.5 + sx * k), sea + 2, round(oz + dz * i * 2.5 + sz * k), PLANK);
                    }
                }
            }
        }
    }

    public record End(int x, int z, int H, double R) {
    }

    // A rope bridge between two pillars: planks on a curve (catenary) that sags in the middle, rope on both sides tied to
    // posts at the ends.
    static void bridge(Vox w, End a0, End b0, int sea) {
        double L0 = Math.hypot(b0.x - a0.x, b0.z - a0.z), ux = (b0.x - a0.x) / L0, uz = (b0.z - a0.z) / L0;
        // the ends start near the rim of each top, not the middle (where a pine usually stands)
        double ax = a0.x + ux * a0.R * 0.45, az = a0.z + uz * a0.R * 0.45;
        double bx = b0.x - ux * b0.R * 0.45, bz = b0.z - uz * b0.R * 0.45;
        int ya = sea + a0.H + 1, yb = sea + b0.H + 1;
        double L = Math.hypot(bx - ax, bz - az);
        int n = (int) Math.ceil(L * 2.5);
        double dx = (bx - ax) / L, dz = (bz - az) / L, sx = -dz, sz = dx;
        double sag = 1 + L * 0.08;
        // flat over each top out to past its rim, the sag only over the water between (it used to start sagging on the
        // top, so the deck went down into the pillar's own dirt and came out as loose steps beside the cliff)
        double da = a0.R * 0.65, db = b0.R * 0.65;
        if (L - da - db < 6) {
            da = db = 0;
        }
        // the posts first, so the rope tied to them doesn't take their middle block (it left the top block floating)
        double[][] ends = {{ax, az, ya}, {bx, bz, yb}};
        for (double[] p : ends) {
            for (int s = -2; s <= 2; s += 4) {
                for (int k = 0; k <= 2; k++) {
                    w.force(round(p[0] + sx * s), (int) p[2] + k, round(p[1] + sz * s), POST);
                }
            }
        }
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double x = ax + (bx - ax) * t, z = az + (bz - az) * t;
            double y = deckY(t, L, da, db, ya, yb, sag);
            for (int s = -1; s <= 1; s++) {
                w.set(round(x + sx * s), round(y) - 1 + 1, round(z + sz * s), PLANK);
            }
        }
        // The rope on each side: the preview draws a line through its blocks, diagonals too; a block of rope in the game is
        // straight along one axis, so the line goes block to block along the axes (a step in x, then in z, then in y),
        // each block turned the way the rope leaves it, and it reads as one rope instead of loose sticks (Stark's print
        // of 2026-11-12).
        for (int s = -2; s <= 2; s += 4) {
            int cx = 0, cy = 0, cz = 0;
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                double x = ax + (bx - ax) * t, z = az + (bz - az) * t;
                double y = deckY(t, L, da, db, ya, yb, sag);
                int rx = round(x + sx * s), ry = round(y) + 1, rz = round(z + sz * s);
                if (i == 0) {
                    cx = rx;
                    cy = ry;
                    cz = rz;
                    continue;
                }
                while (cx != rx || cz != rz || cy != ry) {
                    byte type = cx != rx ? ROPE : cz != rz ? ROPE_Z : ROPE_Y;
                    w.set(cx, cy, cz, type);
                    if (cx != rx) {
                        cx += Integer.signum(rx - cx);
                    } else if (cz != rz) {
                        cz += Integer.signum(rz - cz);
                    } else {
                        cy += Integer.signum(ry - cy);
                    }
                }
            }
            w.set(cx, cy, cz, Math.abs(bx - ax) >= Math.abs(bz - az) ? ROPE : ROPE_Z);
        }
    }

    private static double deckY(double t, double L, double da, double db, int ya, int yb, double sag) {
        double d = t * L;
        if (d <= da) {
            return ya;
        }
        if (d >= L - db) {
            return yb;
        }
        double u = (d - da) / (L - da - db);
        return ya + (yb - ya) * u - sag * 4 * u * (1 - u);
    }

    // ---- cells ----------------------------------------------------------------------------------------------------

    public enum Kind {
        PILLAR(40, 0.55, 8, 14, 911), ISLET(96, 0.45, 24, 28, 929), STACKS(30, 0.35, 5, 8, 937), PIER(72, 0.25, 12, 14, 941),
        BRIDGE(0, 0, 0, 0, 0);

        final int size, pad, reach, salt;
        final double chance;

        Kind(int size, double chance, int pad, int reach, int salt) {
            this.size = size;
            this.chance = chance;
            this.pad = pad;
            this.reach = reach;
            this.salt = salt;
        }

        public boolean rock() {
            return this == PILLAR || this == ISLET || this == STACKS;
        }
    }

    private static final Kind[] CELLS = {Kind.PILLAR, Kind.ISLET, Kind.STACKS, Kind.PIER};

    public record Obj(Kind kind, int x, int z, int seed, int reach, int i, int j, End a, End b) {
    }

    private static Obj cellObject(Kind c, int i, int j, int seed) {
        if (hash3(i, c.salt, j, seed) >= c.chance) {
            return null;
        }
        int span = c.size - 2 * c.pad;
        int x = i * c.size + c.pad + floor(hash3(i, c.salt + 1, j, seed) * span);
        int z = j * c.size + c.pad + floor(hash3(i, c.salt + 2, j, seed) * span);
        return new Obj(c, x, z, floor(hash3(i, c.salt + 3, j, seed) * 1e9) + 1, c.reach, i, j, null, null);
    }

    // Nothing touches an islet (it's wide); needles and piers away from the pillars.
    private static boolean crowded(Obj o, int seed) {
        if (o.kind == Kind.ISLET) {
            return false;
        }
        Kind c = Kind.ISLET;
        for (int i = floor((o.x - 40 - c.size) / (double) c.size); i <= floor((o.x + 40) / (double) c.size); i++) {
            for (int j = floor((o.z - 40 - c.size) / (double) c.size); j <= floor((o.z + 40) / (double) c.size); j++) {
                Obj g = cellObject(c, i, j, seed);
                if (g != null && Math.hypot(g.x - o.x, g.z - o.z) < 26) {
                    return true;
                }
            }
        }
        if (o.kind == Kind.STACKS || o.kind == Kind.PIER) {
            Kind p = Kind.PILLAR;
            for (int i = floor((o.x - 20 - p.size) / (double) p.size); i <= floor((o.x + 20) / (double) p.size); i++) {
                for (int j = floor((o.z - 20 - p.size) / (double) p.size); j <= floor((o.z + 20) / (double) p.size); j++) {
                    Obj g = cellObject(p, i, j, seed);
                    if (g != null && Math.hypot(g.x - o.x, g.z - o.z) < 14) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Bridges: from each pillar to its neighbour in the cell to the right and in front, if both are tall and 14-42
    // blocks apart.
    private static List<Obj> bridgesFrom(Obj p, int seed, Predicate<Obj> accept) {
        List<Obj> out = new ArrayList<>();
        Kind c = Kind.PILLAR;
        Size sp = pillarSize(p.seed);
        if (sp.H < 12) {
            return out;
        }
        int[][] dirs = {{1, 0}, {0, 1}};
        for (int[] dd : dirs) {
            int di = dd[0], dj = dd[1];
            Obj q = cellObject(c, p.i + di, p.j + dj, seed);
            if (q == null || crowded(q, seed) || !accept.test(q)) {
                continue;
            }
            Size sq = pillarSize(q.seed);
            double L = Math.hypot(q.x - p.x, q.z - p.z);
            if (sq.H < 12 || L < 14 || L > 42 || Math.abs(sp.H - sq.H) > 10) {
                continue;
            }
            if (hash3(p.i * 2 + di, 977, p.j * 2 + dj, seed) > 0.6) {
                continue;
            }
            out.add(new Obj(Kind.BRIDGE, round((p.x + q.x) / 2.0), round((p.z + q.z) / 2.0), p.seed ^ q.seed, (int) Math.ceil(L / 2) + 3,
                    0, 0, new End(p.x, p.z, sp.H, sp.R), new End(q.x, q.z, sq.H, sq.R)));
        }
        return out;
    }

    public static List<Obj> objectsNear(int x0, int z0, int x1, int z1, int seed, Predicate<Obj> accept) {
        List<Obj> out = new ArrayList<>();
        for (Kind c : CELLS) {
            int reach = c == Kind.PILLAR ? c.reach + 26 : c.reach; // a pillar's bridge reaches farther
            for (int i = floor((x0 - reach) / (double) c.size); i <= floor((x1 + reach) / (double) c.size); i++) {
                for (int j = floor((z0 - reach) / (double) c.size); j <= floor((z1 + reach) / (double) c.size); j++) {
                    Obj o = cellObject(c, i, j, seed);
                    if (o == null || crowded(o, seed) || !accept.test(o)) {
                        continue;
                    }
                    if (c == Kind.PILLAR) {
                        for (Obj b : bridgesFrom(o, seed, accept)) {
                            if (b.x + b.reach < x0 || b.x - b.reach > x1 || b.z + b.reach < z0 || b.z - b.reach > z1) {
                                continue;
                            }
                            out.add(b);
                        }
                    }
                    if (o.x + c.reach < x0 || o.x - c.reach > x1 || o.z + c.reach < z0 || o.z - c.reach > z1) {
                        continue;
                    }
                    out.add(o);
                }
            }
        }
        return out;
    }

    public static Vox build(Obj o, int floor, int sea) {
        Vox w = new Vox();
        switch (o.kind) {
            case PILLAR -> pillar(w, o.x, floor, o.z, o.seed, sea);
            case ISLET -> islet(w, o.x, floor, o.z, o.seed, sea);
            case STACKS -> stacks(w, o.x, floor, o.z, o.seed, sea);
            case PIER -> pier(w, o.x, floor, o.z, o.seed, sea);
            default -> bridge(w, o.a, o.b, sea);
        }
        return w;
    }
}
