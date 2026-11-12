package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

import com.starkinhon.battlegroundsmod.world.DesertGen.Vox;
import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;

// The Vila da Chuva (Stark 2026-11-11, "ta a cara da vila da chuva", the approved preview built from
// /mnt/project-files/forja/naruto-chuva/gen.js). That generator line by line, like SeaGen: the same steps in the same
// order with the same random, so a seed makes here what it makes in the preview. Change one, change the other.
// Amegakure: it rains without end. From the soaked ground and the grey lakes rise towers of dark metal, tall and
// smooth, with reinforcing rings, rows of windows (some lit), rust running down and a crown of crooked spikes on top.
// Thick pipes run down the facades, join one tower to the next high up and come out of the ground in an arch to go back
// in. Stumps of broken towers, fallen pipes half buried, old concrete paving showing through the mud, puddles all over.
// The land's shape is the game's; the generator puts the pieces and the ground.
public final class RainGen {
    public static final byte AIR = 0, STONE = 1, MUD = 2, COARSE = 3, GRAVEL = 4, GRASS = 5, PUDDLE = 6, METAL = 7, METAL_DARK = 8,
            RIB = 9, RUST = 10, RUST_LIGHT = 11, WINDOW = 12, WINDOW_LIT = 13, PIPE = 14, CONCRETE = 15, SPIKE = 16;

    private RainGen() {
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

    private static double sq(double a) {
        return a * a;
    }

    // ---- towers -------------------------------------------------------------------------------------------------

    // A tower's size comes first out of its random: the pipes between towers and the pieces nearby know its height and
    // width without building it.
    public record Size(int H, double R, boolean round, double taper, int step, Rng rng) {
    }

    public static Size towerSize(int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(44, 82);
        double R = rng.range(6, 11);
        boolean round = rng.next() < 0.55;
        double taper = rng.range(0.72, 0.92);
        int step = rng.nextInt(8, 11);
        return new Size(H, R, round, taper, step, rng);
    }

    // The tower's plan at height dy (0 = foot): round, or square with rounded corners; the foot is wider.
    static double towerR(Size s, int dy) {
        double r = s.R * (1 - (1 - s.taper) * dy / s.H);
        if (dy < 3) {
            r *= 1.18;
        }
        return r;
    }

    static boolean inPlan(boolean round, int dx, int dz, double r) {
        if (r <= 0) {
            return false;
        }
        if (round) {
            return Math.hypot(dx, dz) <= r;
        }
        int ax = Math.abs(dx), az = Math.abs(dz);
        if (ax > r || az > r) {
            return false;
        }
        double c = Math.min(2.5, r * 0.4);
        return sq(Math.max(ax - (r - c), 0)) + sq(Math.max(az - (r - c), 0)) <= c * c;
    }

    // A tower: a shell of dark metal in panels, reinforcing rings every `step` blocks, rows of windows between the rings
    // (some lit), rust running down in streaks and at the foot, a lid on top and a crown of crooked spikes opening
    // outward with an antenna in the middle. Pipes run down the facade.
    static int tower(Vox w, int ox, int oy, int oz, int seed) {
        Size s = towerSize(seed);
        Rng rng = s.rng;
        int top = oy + s.H;
        int n = (int) Math.ceil(s.R * 1.2 + 2);
        for (int y = oy - 3; y <= top; y++) {
            int dy = y - oy;
            double r = towerR(s, Math.max(0, dy));
            int k = ((dy % s.step) + s.step) % s.step;
            boolean rib = k == 0 && dy > 2 && y < top;
            for (int dx = -n; dx <= n; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    int x = ox + dx, z = oz + dz;
                    if (rib && inPlan(s.round, dx, dz, r + 0.7) && !inPlan(s.round, dx, dz, r - 1)) {
                        w.force(x, y, z, RIB);
                        continue;
                    }
                    if (!inPlan(s.round, dx, dz, r)) {
                        continue;
                    }
                    if (y < top && inPlan(s.round, dx, dz, r - 2)) {
                        continue; // hollow inside
                    }
                    boolean outer = !inPlan(s.round, dx, dz, r - 1);
                    byte t = hash3(x, floor(y / 3.0), z, seed) < 0.22 ? METAL_DARK : METAL;
                    if (outer && y < top) {
                        // windows: rows two blocks tall between the rings
                        if ((k == 3 || k == 4 || (s.step >= 10 && (k == 7 || k == 8))) && dy > 3) {
                            double u;
                            if (s.round) {
                                u = (Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * Math.max(6, Math.round(2 * Math.PI * r / 3));
                            } else {
                                u = (Math.abs(dx) >= Math.abs(dz) ? dz : dx) / 3.0 + 0.5;
                            }
                            double f = u - Math.floor(u);
                            boolean edge = !s.round && Math.abs(dx) > r - 2 && Math.abs(dz) > r - 2;
                            if (f > 0.3 && f < 0.7 && !edge) {
                                int side = Math.abs(dx) >= Math.abs(dz) ? Integer.signum(dx) : 2 + Integer.signum(dz);
                                t = hash3(floor(u), floor(dy / (double) s.step), side, seed) < 0.12 ? WINDOW_LIT : WINDOW;
                            }
                        }
                        // rust running down streaks of the facade, and at the foot
                        if (t == METAL || t == METAL_DARK) {
                            boolean streak = hash3(x, 7, z, seed) < 0.12 && valueNoise3(x, y * 0.25, z, seed + 9, 4) > 0.48;
                            if (streak) {
                                t = valueNoise3(x, y, z, seed + 10, 2) > 0.6 ? RUST_LIGHT : RUST;
                            } else if (dy < 4 && valueNoise3(x, y, z, seed + 12, 3) > 0.45) {
                                t = RUST;
                            }
                        }
                    }
                    w.force(x, y, z, t);
                }
            }
        }
        // the crown: crooked spikes opening outward, thicker at the base, and an antenna in the middle
        double rt = towerR(s, s.H);
        int spikes = rng.nextInt(6, 10);
        for (int i = 0; i < spikes; i++) {
            double a = i / (double) spikes * 2 * Math.PI + rng.range(-0.2, 0.2);
            double lean = rng.range(0.25, 0.7);
            int L = rng.nextInt(7, 16);
            double x = ox + 0.5 + Math.cos(a) * rt * 0.8, y = top + 1, z = oz + 0.5 + Math.sin(a) * rt * 0.8;
            double l = Math.hypot(lean, 1), dx = Math.cos(a) * lean / l, dz = Math.sin(a) * lean / l, ddy = 1 / l;
            for (int k = 0; k < L; k++) {
                w.set(floor(x), floor(y), floor(z), SPIKE);
                if (k < L * 0.4) {
                    w.set(floor(x) + 1, floor(y), floor(z), SPIKE);
                    w.set(floor(x), floor(y), floor(z) + 1, SPIKE);
                }
                x += dx;
                y += ddy;
                z += dz;
            }
        }
        int ant = rng.nextInt(10, 20);
        for (int k = 1; k <= ant; k++) {
            w.set(ox, top + k, oz, SPIKE);
        }
        int bar = top + floor(ant * 0.6);
        for (int k = -2; k <= 2; k++) {
            w.set(ox + k, bar, oz, SPIKE);
            w.set(ox, bar, oz + k, SPIKE);
        }
        // pipes down the facade, held by the rings
        int pipes = rng.nextInt(1, 3);
        for (int i = 0; i < pipes; i++) {
            double a = rng.range(0, 2 * Math.PI), rad = rng.range(0.9, 1.5);
            int from = rng.nextInt(floor(s.H * 0.5), s.H - 4);
            List<double[]> pts = new ArrayList<>();
            // the top end goes into the wall
            pts.add(new double[] {ox + 0.5 + Math.cos(a) * (towerR(s, from) - 1), oy + from + 0.5, oz + 0.5 + Math.sin(a) * (towerR(s, from) - 1)});
            for (int dy : new int[] {from, floor(from / 2.0), -2}) {
                double r = towerR(s, Math.max(0, dy)) + rad + 0.4;
                pts.add(new double[] {ox + 0.5 + Math.cos(a) * r, oy + dy + 0.5, oz + 0.5 + Math.sin(a) * r});
            }
            pipe(w, pts, rad, seed + 20 + i);
        }
        return top;
    }

    // A pipe: a tube through the points, with flanges every 6 blocks and patches of rust. Doesn't go into what's there.
    static void pipe(Vox w, List<double[]> pts, double rad, int seed) {
        double along = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] A = pts.get(i), Bp = pts.get(i + 1);
            double vx = Bp[0] - A[0], vy = Bp[1] - A[1], vz = Bp[2] - A[2], L = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (L < 1e-6) {
                continue;
            }
            double m = rad + 1.5;
            int x0 = floor(Math.min(A[0], Bp[0]) - m), x1 = floor(Math.max(A[0], Bp[0]) + m);
            int y0 = floor(Math.min(A[1], Bp[1]) - m), y1 = floor(Math.max(A[1], Bp[1]) + m);
            int z0 = floor(Math.min(A[2], Bp[2]) - m), z1 = floor(Math.max(A[2], Bp[2]) + m);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        double px = x + 0.5 - A[0], py = y + 0.5 - A[1], pz = z + 0.5 - A[2];
                        double tr = (px * vx + py * vy + pz * vz) / (L * L), t = Math.max(0, Math.min(1, tr));
                        double ex = px - vx * t, ey = py - vy * t, ez = pz - vz * t, d = Math.sqrt(ex * ex + ey * ey + ez * ez);
                        double s = along + t * L;
                        boolean flange = tr > 0 && tr < 1 && s % 6 < 1;
                        if (d <= rad) {
                            w.set(x, y, z, valueNoise3(x, y, z, seed + 11, 3) > 0.66 ? RUST : PIPE);
                        } else if (flange && d <= rad + 0.8) {
                            w.set(x, y, z, RIB);
                        }
                    }
                }
            }
            along += L;
        }
    }

    // A stump of a broken tower: a low shell, the top torn, much more rust.
    static void ruin(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double R = rng.range(3, 5.5);
        int H = rng.nextInt(6, 16);
        boolean round = rng.next() < 0.5;
        int n = (int) Math.ceil(R * 1.2 + 2);
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                int x = ox + dx, z = oz + dz;
                int h = H - floor(valueNoise(x, z, seed + 1, 2.5) * 7);
                for (int y = oy - 2; y <= oy + h; y++) {
                    double r = R * (y - oy < 2 ? 1.15 : 1);
                    if (!inPlan(round, dx, dz, r)) {
                        continue;
                    }
                    boolean shell = !inPlan(round, dx, dz, r - 1.5);
                    if (!shell && y > oy + rng.nextInt(0, 2) - 1) {
                        continue;
                    }
                    double rust = valueNoise3(x, y * 0.5, z, seed + 2, 3);
                    w.force(x, y, z, rust > 0.62 ? RUST_LIGHT : rust > 0.4 ? RUST : hash3(x, y, z, seed) < 0.3 ? METAL_DARK : METAL);
                }
            }
        }
    }

    // A pipe in an arch: out of the ground (or the water), up and back in further on.
    static void arch(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, 2 * Math.PI);
        int D = rng.nextInt(22, 44), Hh = rng.nextInt(12, 24);
        double rad = rng.range(2.2, 3.4);
        double ux = Math.cos(a), uz = Math.sin(a);
        List<double[]> pts = new ArrayList<>();
        int N = 14;
        for (int i = -1; i <= N + 1; i++) {
            double t = Math.max(-0.08, Math.min(1.08, i / (double) N));
            double c = Math.cos(Math.PI * t);
            double s = Math.sin(Math.PI * Math.max(0, Math.min(1, t)));
            pts.add(new double[] {ox + 0.5 - ux * c * D / 2, oy - 3 + (Hh + 3) * s + (t < 0 || t > 1 ? -3 : 0), oz + 0.5 - uz * c * D / 2});
        }
        pipe(w, pts, rad, seed);
    }

    // A fallen pipe, half buried in the mud.
    static void debris(Vox w, int ox, int oy, int oz, int seed) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, 2 * Math.PI), L = rng.range(5, 11), rad = rng.range(0.9, 1.5);
        double tilt = rng.range(-1.2, 1.2);
        double ux = Math.cos(a), uz = Math.sin(a);
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {ox + 0.5 - ux * L / 2, oy - rad * 0.4 + tilt, oz + 0.5 - uz * L / 2});
        pts.add(new double[] {ox + 0.5 + ux * L / 2, oy - rad * 0.4 - tilt * 0.3, oz + 0.5 + uz * L / 2});
        pipe(w, pts, rad, seed);
    }

    public record End(int x, int z, int H) {
    }

    // A pipe between two towers, straight, high up.
    static void link(Vox w, End a, End b, IntBinaryOperator ground, int seed) {
        double f = 0.3 + 0.3 * hash3(a.x, 5, b.z, seed);
        double y = Math.max(ground.applyAsInt(a.x, a.z), ground.applyAsInt(b.x, b.z)) + Math.max(8, floor(Math.min(a.H, b.H) * f)) + 0.5;
        double rad = 1.1 + 0.8 * hash3(b.x, 6, a.z, seed);
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {a.x + 0.5, y, a.z + 0.5});
        pts.add(new double[] {b.x + 0.5, y, b.z + 0.5});
        pipe(w, pts, rad, seed);
    }

    // ---- cells ----------------------------------------------------------------------------------------------------

    public enum Kind {
        TOWER(84, 0.6, 16, 40, 1301), RUIN(44, 0.4, 8, 9, 1307), ARCH(96, 0.6, 24, 27, 1319), DEBRIS(20, 0.3, 4, 7, 1321),
        LINK(0, 0, 0, 0, 0);

        final int size, pad, reach, salt;
        final double chance;

        Kind(int size, double chance, int pad, int reach, int salt) {
            this.size = size;
            this.chance = chance;
            this.pad = pad;
            this.reach = reach;
            this.salt = salt;
        }

        // the tower and the stump stand on their own; the pipes don't go into another piece
        public boolean solid() {
            return this == TOWER || this == RUIN;
        }
    }

    private static final Kind[] CELLS = {Kind.TOWER, Kind.RUIN, Kind.ARCH, Kind.DEBRIS};

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

    // The cell's tower: its spot, or where the game says it can't stand there (outside the biome, too steep), the first
    // of a 4 by 4 grid over the cell where it can, the same tower moved (the PC test of 2026-11-12: the Vila's pieces
    // are small and most spots fell outside them, 2 towers in 3000 by 3000 blocks). The preview accepts every spot, so
    // there it is always the cell's spot.
    private static Obj towerAt(int i, int j, int seed, Predicate<Obj> accept) {
        Kind c = Kind.TOWER;
        Obj o = cellObject(c, i, j, seed);
        if (o == null || accept.test(o)) {
            return o;
        }
        int span = c.size - 2 * c.pad;
        for (int gi = 0; gi < 4; gi++) {
            for (int gj = 0; gj < 4; gj++) {
                Obj m = new Obj(c, i * c.size + c.pad + gi * span / 3, j * c.size + c.pad + gj * span / 3, o.seed, c.reach, i, j,
                        null, null);
                if (accept.test(m)) {
                    return m;
                }
            }
        }
        return null;
    }

    // Nothing touches a tower (except its own pipes).
    private static boolean crowded(Obj o, int seed, Predicate<Obj> accept) {
        if (o.kind == Kind.TOWER) {
            return false;
        }
        Kind c = Kind.TOWER;
        for (int i = floor((o.x - 30 - c.size) / (double) c.size); i <= floor((o.x + 30) / (double) c.size); i++) {
            for (int j = floor((o.z - 30 - c.size) / (double) c.size); j <= floor((o.z + 30) / (double) c.size); j++) {
                Obj g = towerAt(i, j, seed, accept);
                if (g != null && Math.hypot(g.x - o.x, g.z - o.z) < towerSize(g.seed).R * 1.2 + (o.kind == Kind.ARCH ? 22 : 8)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Pipes between towers: from each tower to its neighbour in the cell to the right and in front, 24-84 blocks apart.
    private static List<Obj> linksFrom(Obj p, int seed, Predicate<Obj> accept) {
        List<Obj> out = new ArrayList<>();
        Kind c = Kind.TOWER;
        Size sp = towerSize(p.seed);
        int[][] dirs = {{1, 0}, {0, 1}};
        for (int[] dd : dirs) {
            int di = dd[0], dj = dd[1];
            Obj q = towerAt(p.i + di, p.j + dj, seed, accept);
            if (q == null) {
                continue;
            }
            Size sq2 = towerSize(q.seed);
            double L = Math.hypot(q.x - p.x, q.z - p.z);
            if (L < 24 || L > 84) {
                continue;
            }
            if (hash3(p.i * 2 + di, 1361, p.j * 2 + dj, seed) > 0.8) {
                continue;
            }
            out.add(new Obj(Kind.LINK, (int) Math.round((p.x + q.x) / 2.0), (int) Math.round((p.z + q.z) / 2.0), p.seed ^ q.seed,
                    (int) Math.ceil(L / 2) + 3, 0, 0, new End(p.x, p.z, sp.H), new End(q.x, q.z, sq2.H)));
        }
        return out;
    }

    public static List<Obj> objectsNear(int x0, int z0, int x1, int z1, int seed, Predicate<Obj> accept) {
        List<Obj> out = new ArrayList<>();
        for (Kind c : CELLS) {
            int reach = c == Kind.TOWER ? c.reach + 46 : c.reach; // a tower's pipe reaches farther
            for (int i = floor((x0 - reach) / (double) c.size); i <= floor((x1 + reach) / (double) c.size); i++) {
                for (int j = floor((z0 - reach) / (double) c.size); j <= floor((z1 + reach) / (double) c.size); j++) {
                    Obj o = c == Kind.TOWER ? towerAt(i, j, seed, accept) : cellObject(c, i, j, seed);
                    if (o == null || crowded(o, seed, accept) || c != Kind.TOWER && !accept.test(o)) {
                        continue;
                    }
                    if (c == Kind.TOWER) {
                        for (Obj l : linksFrom(o, seed, accept)) {
                            if (l.x + l.reach < x0 || l.x - l.reach > x1 || l.z + l.reach < z0 || l.z - l.reach > z1) {
                                continue;
                            }
                            out.add(l);
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

    // foot: the first block above the ground (or the lake's bottom) at the piece; ground(x, z): the same anywhere.
    public static Vox build(Obj o, int foot, IntBinaryOperator ground) {
        Vox w = new Vox();
        switch (o.kind) {
            case TOWER -> tower(w, o.x, foot, o.z, o.seed);
            case RUIN -> ruin(w, o.x, foot, o.z, o.seed);
            case ARCH -> arch(w, o.x, foot, o.z, o.seed);
            case DEBRIS -> debris(w, o.x, foot, o.z, o.seed);
            default -> link(w, o.a, o.b, ground, o.seed);
        }
        return w;
    }

    // ---- ground ---------------------------------------------------------------------------------------------------

    // The top of the ground: mud, coarse dirt, gravel, dark soaked grass, old concrete paving showing through.
    public static byte groundTop(int x, int z, int seed) {
        if (valueNoise(x, z, seed + 21, 23) > 0.74 && valueNoise(x, z, seed + 22, 4) > 0.3) {
            return CONCRETE;
        }
        double n = valueNoise(x, z, seed + 23, 11);
        if (n < 0.3) {
            return MUD;
        }
        if (n > 0.74) {
            return GRAVEL;
        }
        if (valueNoise(x, z, seed + 24, 5) > 0.62) {
            return COARSE;
        }
        return GRASS;
    }

    // A puddle: shallow water in place of the ground's top, where it makes a dish.
    public static boolean puddle(int x, int z, int seed) {
        return valueNoise(x, z, seed + 25, 7) > 0.64;
    }
}
