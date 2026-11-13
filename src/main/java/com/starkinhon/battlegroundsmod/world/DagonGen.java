package com.starkinhon.battlegroundsmod.world;

import static com.starkinhon.battlegroundsmod.world.JjkGen.cos;
import static com.starkinhon.battlegroundsmod.world.JjkGen.floor;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hash3;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hashInt;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hypot;
import static com.starkinhon.battlegroundsmod.world.JjkGen.round;
import static com.starkinhon.battlegroundsmod.world.JjkGen.sin;
import static com.starkinhon.battlegroundsmod.world.JjkGen.valueNoise;

import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;
import com.starkinhon.battlegroundsmod.world.TemploGen.Vox;

// The Mar de Dagon (Stark 2026-11-12; port released 2026-11-13): line-by-line port of the approved preview's generator
// (forja/jujutsu-dagon/gen.js), the same draws and the same hashes from the world's coordinates, with the preview's
// SEA as a parameter (the world's sea level). Dagon's domain (Horizonte Cativante do Skandha) made a biome: a tropical
// coast too beautiful to be normal. White sand, shallow turquoise water darkening into deep blue, crooked coconut palms,
// colored coral reefs in the shallows (pink, orange, purple, yellow), seagrass, shells and starfish on the sand, giant
// fish skeletons stranded on the beach. And the Ninho de Dagon: a round lagoon in the middle of the sea ringed by giant
// stone-flesh tentacles rising out of the water and curling up high, suckers on their inner side and coral growing on
// them, and in the middle a dark bottomless well. At night the shallows glow with plankton.
public final class DagonGen {
    // The preview's NAMES, in its order.
    public static final byte AIR = 0, STONE = 1, SAND = 2, SAND_WET = 3, GRASS = 4, WATER = 5, PALM = 6, FROND = 7, FROND2 = 8,
            COCONUT = 9, CORAL_PINK = 10, CORAL_ORANGE = 11, CORAL_PURPLE = 12, CORAL_YELLOW = 13, SEAGRASS = 14, SHELL = 15,
            STAR = 16, BONE = 17, TENTACLE = 18, TENTACLE_DARK = 19, SUCKER = 20, ABYSS = 21, GRAVEL = 22, ROCK = 23,
            PRISMARINE = 24;

    // How far a palm's blocks reach from its column: the lean (about 4) and a frond (6.5 and its width).
    public static final int PALM_REACH = 13;
    // How far a coral reaches from its column (a branch wanders up to 3).
    public static final int CORAL_REACH = 4;
    // How far a fish skeleton reaches from its spot: the spine (to 20), the skull (2 further and 2 wide).
    public static final int FISH_REACH = 26;
    // How far the nest's blocks reach from its center: the ground (R + 10) and the tentacles (from 22 out, curling out
    // up to about 36 more, 4 thick).
    public static final int NEST_REACH = 66;
    // Nothing of the coast within this of a nest's center (the preview's keepOut).
    public static final int NEST_KEEP = 48;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] ROCK3 = {{0, 0}, {1, 0}, {0, 1}};

    private DagonGen() {
    }

    // ---------------------------------------------------------------- terrain

    // Low islands and sandbanks in a shallow sea sinking slowly: most of the bottom 2 to 7 under water (where coral
    // grows), with deeper channels.
    public static int heightAt(int x, int z, int seed, int sea) {
        double big = valueNoise(x, z, seed, 70), mid = valueNoise(x, z, seed + 1, 24), sm = valueNoise(x, z, seed + 2, 8);
        return floor(sea - 4 + (big - 0.5) * 16 + (mid - 0.5) * 7 + (sm - 0.5) * 2);
    }

    public static void ground0(Vox w, int x, int z, int h, int seed, int sea) {
        int depth = sea - h;
        byte top;
        if (depth >= 1) {
            top = depth > 8 ? (hash3(x, 1, z, seed + 3) < 0.5 ? GRAVEL : SAND) : SAND;
        } else if (h <= sea + 1) {
            top = depth == 0 ? SAND_WET : SAND;
        } else {
            top = h >= sea + 3 && valueNoise(x, z, seed + 4, 6) > 0.45 ? GRASS : SAND;
        }
        w.force(x, h, z, top);
        for (int k = 1; k <= 3; k++) {
            w.force(x, h - k, z, k <= 2 ? SAND : STONE);
        }
        for (int y = h + 1; y <= sea; y++) {
            w.force(x, y, z, WATER);
        }
        // loose rocks on the bottom and at the edge
        if (hash3(x, 2, z, seed + 5) < 0.004 && depth < 6) {
            for (int[] d : ROCK3) {
                w.force(x + d[0], h + 1, z + d[1], ROCK);
            }
        }
    }

    // ---------------------------------------------------------------- life

    // Coconut palm: a trunk of 1 rising and curving to one side, a crown of 6 to 8 fronds out of the top that rise a little
    // and fall in an arc; coconuts under the crown.
    public static void palm(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(7, 12);
        double a = rng.range(0, Math.PI * 2), bend = rng.range(0.15, 0.4);
        double px = x + 0.5, pz = z + 0.5;
        for (int k = 0; k < H; k++) {
            double u = (double) k / H;
            px += cos(a) * bend * u * 1.6;
            pz += sin(a) * bend * u * 1.6;
            w.force(floor(px), y + k, floor(pz), PALM);
        }
        int tx = floor(px), ty = y + H, tz = floor(pz);
        w.force(tx, ty, tz, FROND);
        int n = rng.nextInt(6, 8);
        for (int i = 0; i < n; i++) {
            double fa = (double) i / n * Math.PI * 2 + rng.range(-0.25, 0.25), L = rng.range(4, 6.5);
            for (int s = 1; s <= Math.ceil(L * 1.5); s++) {
                double u = s / (L * 1.5);
                double fx = tx + 0.5 + cos(fa) * u * L, fz = tz + 0.5 + sin(fa) * u * L;
                double fy = ty + 0.6 + sin(u * Math.PI * 0.8) * 1.4 - u * u * 3.2;
                w.set(floor(fx), floor(fy), floor(fz), hash3(i, s, seed, 1) < 0.4 ? FROND2 : FROND);
                // the frond has width: one block to the side in the middle of its length
                if (u > 0.25 && u < 0.75) {
                    w.set(floor(fx - sin(fa) * 0.7), floor(fy), floor(fz + cos(fa) * 0.7), FROND2);
                }
            }
        }
        for (int i = 0; i < rng.nextInt(1, 4); i++) {
            w.set(tx + SIDES[i][0], ty - 1, tz + SIDES[i][1], COCONUT);
        }
    }

    // Coral and seagrass grow in the water: they take an empty place or the water's, never another block.
    private static void wet(Vox w, int x, int y, int z, byte t) {
        int c = w.get(x, y, z);
        if (c == 0 || c == WATER) {
            w.force(x, y, z, t);
        }
    }

    // Coral: tufts growing from the shallow bottom as branches (pink, purple), fans/tables (orange) or brains (yellow).
    public static void coral(Vox w, int x, int h, int z, int seed, int sea) {
        Rng rng = new Rng(seed);
        int kind = rng.nextInt(0, 3);
        int top = sea - 1; // never out of the water
        if (kind == 0 || kind == 2) {
            byte t = kind == 0 ? CORAL_PINK : CORAL_PURPLE;
            for (int i = 0; i < rng.nextInt(2, 4); i++) {
                int cx = x, cz = z;
                int len = rng.nextInt(2, 4);
                for (int k = 1; k <= len && h + k <= top; k++) {
                    if (k > 1 && rng.next() < 0.5) {
                        cx += rng.nextInt(-1, 1);
                        cz += rng.nextInt(-1, 1);
                    }
                    wet(w, cx, h + k, cz, t);
                }
            }
        } else if (kind == 1) {
            int r = rng.nextInt(1, 2), hy = Math.min(top, h + rng.nextInt(1, 3));
            for (int k = h + 1; k < hy; k++) {
                wet(w, x, k, z, CORAL_ORANGE);
            }
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    if (a * a + b * b <= r * r + 1) {
                        wet(w, x + a, hy, z + b, CORAL_ORANGE);
                    }
                }
            }
        } else {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    for (int k = 1; k <= 2; k++) {
                        if (Math.abs(a) + Math.abs(b) + k <= 3 && h + k <= top) {
                            wet(w, x + a, h + k, z + b, CORAL_YELLOW);
                        }
                    }
                }
            }
        }
    }

    // A giant fish skeleton stranded on the sand: a long spine half buried, ribs arching on both sides, a big skull at
    // the end.
    public static void fishBones(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        double a = rng.range(0, Math.PI * 2);
        int L = rng.nextInt(12, 20);
        double ca = cos(a), sa = sin(a);
        for (int i = 0; i <= L; i++) {
            int px = round(x + ca * i), pz = round(z + sa * i);
            w.force(px, y, pz, BONE);
            if (i % 2 == 0 && i > 3 && i < L - 2) {
                int rh = round(4 * sin(((double) i / L) * Math.PI)) + 1;
                for (int s = -1; s <= 1; s += 2) {
                    for (int k = 1; k <= rh + 1; k++) {
                        int out = round(sin((double) k / (rh + 1) * Math.PI * 0.6) * 3);
                        w.force(round(px - sa * s * out), y + k - (k > rh ? 1 : 0), round(pz + ca * s * out), BONE);
                    }
                }
            }
        }
        int hx = round(x + ca * (L + 2)), hz = round(z + sa * (L + 2));
        for (int a2 = -2; a2 <= 2; a2++) {
            for (int b2 = -2; b2 <= 2; b2++) {
                for (int k = 0; k <= 3; k++) {
                    if (hypot(a2, b2, k - 1) > 2.6) {
                        continue;
                    }
                    if (k == 1 && Math.abs(a2) + Math.abs(b2) <= 1) {
                        continue; // hollow eye socket
                    }
                    w.force(hx + a2, y + k, hz + b2, BONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Ninho de Dagon

    // A giant tentacle: out of the bottom, rising and turning outward until it curls into a spiral up high. Thick at the
    // foot, thin at the tip, light suckers on the inner side of the curve and coral stuck to its foot.
    public static void tentacle(Vox w, int bx, int bz, int floorY, double a, double H, double R0, int seed, int sea) {
        Rng rng = new Rng(seed);
        double ca = cos(a), sa = sin(a);
        double px = bx + 0.5, py = floorY, pz = bz + 0.5, th = rng.range(0.05, 0.3);
        final int N = 90;
        for (int i = 0; i < N; i++) {
            double t = (double) i / N;
            double step = H / N * (1.35 - t * 0.9);
            // rises nearly straight to the middle, then turns outward and curls ever tighter
            th += t < 0.5 ? 0.004 : 0.02 + Math.pow((t - 0.5) / 0.5, 2) * 0.26;
            double dx = sin(th), dy = cos(th);
            px += ca * dx * step;
            pz += sa * dx * step;
            py += dy * step;
            double r = R0 * Math.pow(1 - t, 0.8) + 0.45;
            int ri = (int) Math.ceil(r);
            for (int ox = -ri; ox <= ri; ox++) {
                for (int oy = -ri; oy <= ri; oy++) {
                    for (int oz = -ri; oz <= ri; oz++) {
                        if (ox * ox + oy * oy + oz * oz > r * r + 0.3) {
                            continue;
                        }
                        int x = floor(px + ox), y = floor(py + oy), z = floor(pz + oz);
                        // the top/outer face darker, the inner lighter
                        double inner = -(ox * ca + oz * sa) * dy + oy * dx;
                        w.force(x, y, z, inner > r * 0.5 ? TENTACLE : TENTACLE_DARK);
                    }
                }
            }
            // suckers: on the inner side of the curve (where it curls), in pairs, every piece
            if (i % 4 == 2 && r > 0.9) {
                double nx = -ca * dy, ny = dx, nz = -sa * dy; // the normal to the curve's center
                for (int si = 0; si < 2; si++) {
                    double s = si == 0 ? -0.6 : 0.6;
                    double qx = px + nx * (r + 0.2) - sa * s * r, qy = py + ny * (r + 0.2), qz = pz + nz * (r + 0.2) + ca * s * r;
                    w.force(floor(qx), floor(qy), floor(qz), SUCKER);
                }
            }
            // coral stuck to the part under water
            if (py < sea && hash3(i, 0, bx + bz, seed) < 0.5) {
                double ang = rng.range(0, Math.PI * 2);
                int x = floor(px + cos(ang) * (r + 0.6)), z = floor(pz + sin(ang) * (r + 0.6)), y = floor(py);
                byte[] cs = {CORAL_PINK, CORAL_ORANGE, CORAL_PURPLE, CORAL_YELLOW};
                wet(w, x, y, z, cs[hashInt(x, y, z, seed) % 4]);
            }
        }
    }

    public record Nest(int cx, int cz, int R) {
    }

    // The Nest's lagoon: a ring sandbank of radius ~40 around a deep lagoon, 8 tentacles out of the water on the inner
    // ring, turned outward, and in the center a dark well (the abyss) with a rim of sea stone.
    public static Nest nest(Vox w, int cx, int cz, int seed, IntBinaryOperator ground, int sea) {
        Rng rng = new Rng(seed);
        final int R = 40;
        for (int x = cx - R - 10; x <= cx + R + 10; x++) {
            for (int z = cz - R - 10; z <= cz + R + 10; z++) {
                double dd = hypot(x - cx, z - cz),
                        d = dd + (valueNoise(x, z, seed + 1, 10) - 0.5) * 6 * Math.min(1, Math.max(0, (dd - 10) / 8));
                if (d > R + 8) {
                    continue;
                }
                int g = ground.applyAsInt(x, z);
                int h;
                if (d < 9) {
                    h = sea - 14;
                } else if (d < 30) {
                    h = sea - 3 - round((30 - d) / 4);
                } else if (d < R + 2) {
                    h = sea + 1 - round(Math.abs(d - (R - 4)) / 3); // the ring sandbank
                } else {
                    h = round(g + (sea - 2 - g) * Math.max(0, (R + 8 - d) / 6));
                }
                for (int y = Math.min(g, h) - 4; y <= Math.max(g, h) + 3; y++) {
                    w.force(x, y, z, AIR);
                }
                ground0(w, x, z, h, seed, sea);
                if (d < 9) {
                    w.force(x, h, z, ABYSS);
                    for (int y = h + 1; y <= sea; y++) {
                        w.force(x, y, z, WATER);
                    }
                } else if (d < 11) {
                    for (int y = h; y <= sea - 2; y++) {
                        w.force(x, y, z, PRISMARINE);
                    }
                }
            }
        }
        final int n = 8;
        for (int i = 0; i < n; i++) {
            double a = (double) i / n * Math.PI * 2 + rng.range(-0.2, 0.2), d = rng.range(15, 22);
            int bx = round(cx + cos(a) * d), bz = round(cz + sin(a) * d);
            double H = rng.range(26, 40), R0 = rng.range(2.6, 3.6);
            tentacle(w, bx, bz, sea - 6, a + rng.range(-0.4, 0.4), H, R0, hashInt(i, 7, 7, seed), sea);
        }
        return new Nest(cx, cz, R);
    }

    // ---------------------------------------------------------------- the coast, column by column

    // The life on a column of the coast (after the ground): coral or seagrass in the shallows, a shell or a starfish on
    // the wet sand.
    public static void life(Vox w, int x, int z, int h, int seed, int sea) {
        int depth = sea - h;
        double r = hash3(x, 7, z, seed + 10);
        if (depth >= 2 && depth <= 7) {
            if (r < 0.06 * valueNoise(x, z, seed + 11, 12) * 2) {
                coral(w, x, h, z, hashInt(x, h, z, seed + 12), sea);
            } else if (r > 0.9) {
                for (int k = 1; k <= Math.min(depth - 1, 1 + floor(hash3(x, 8, z, seed) * 2)); k++) {
                    wet(w, x, h + k, z, SEAGRASS);
                }
            }
        } else if (h >= sea && h <= sea + 1 && r < 0.006) {
            w.set(x, h + 1, z, r < 0.003 ? SHELL : STAR);
        }
    }

    // A palm's spot on the grid of 7 (gx, gz on it): its column, or null.
    public static int[] palmSpot(int gx, int gz, int seed) {
        int x = gx + floor(hash3(gx, 1, gz, seed + 20) * 7), z = gz + floor(hash3(gx, 2, gz, seed + 20) * 7);
        return new int[] {x, z};
    }

    public static boolean palmHere(int x, int z, int h, int seed, int sea) {
        return h >= sea + 1 && hash3(x, 3, z, seed + 20) <= 0.55;
    }

    public static void palmAt(Vox w, int x, int z, int h, int seed) {
        palm(w, x, h + 1, z, hashInt(x, h, z, seed + 21));
    }

    // A stranded fish's spot on the grid of 50, or null when that cell has none.
    public static int[] fishSpot(int gx, int gz, int seed) {
        if (hash3(gx, 6, gz, seed + 22) > 0.6) {
            return null;
        }
        return new int[] {gx + 10 + floor(hash3(gx, 4, gz, seed + 22) * 30), gz + 10 + floor(hash3(gx, 5, gz, seed + 22) * 30)};
    }

    public static void fishAt(Vox w, int x, int z, int h, int seed) {
        fishBones(w, x, h, z, hashInt(x, 0, z, seed + 23));
    }

    // The preview's coast (for the parity check).
    public static void coast(Vox w, int half, int seed, IntBinaryOperator ground, BiPredicate<Integer, Integer> keepOut, int sea) {
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (!keepOut.test(x, z)) {
                    ground0(w, x, z, ground.applyAsInt(x, z), seed, sea);
                }
            }
        }
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (keepOut.test(x, z)) {
                    continue;
                }
                life(w, x, z, ground.applyAsInt(x, z), seed, sea);
            }
        }
        for (int gx = -half; gx < half; gx += 7) {
            for (int gz = -half; gz < half; gz += 7) {
                int[] s = palmSpot(gx, gz, seed);
                int x = s[0], z = s[1];
                if (keepOut.test(x, z) || Math.abs(x) > half - 6 || Math.abs(z) > half - 6) {
                    continue;
                }
                int h = ground.applyAsInt(x, z);
                if (!palmHere(x, z, h, seed, sea)) {
                    continue;
                }
                palmAt(w, x, z, h, seed);
            }
        }
        // fish stranded on the sand at the edge
        for (int gx = -half; gx < half; gx += 50) {
            for (int gz = -half; gz < half; gz += 50) {
                int x = gx + 10 + floor(hash3(gx, 4, gz, seed + 22) * 30), z = gz + 10 + floor(hash3(gx, 5, gz, seed + 22) * 30);
                int h = ground.applyAsInt(x, z);
                if (keepOut.test(x, z) || h < sea || h > sea + 1 || hash3(gx, 6, gz, seed + 22) > 0.6) {
                    continue;
                }
                fishAt(w, x, z, h, seed);
            }
        }
    }
}
