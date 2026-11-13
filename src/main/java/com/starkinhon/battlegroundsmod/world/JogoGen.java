package com.starkinhon.battlegroundsmod.world;

import static com.starkinhon.battlegroundsmod.world.JjkGen.atan2;
import static com.starkinhon.battlegroundsmod.world.JjkGen.cos;
import static com.starkinhon.battlegroundsmod.world.JjkGen.floor;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hash3;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hashInt;
import static com.starkinhon.battlegroundsmod.world.JjkGen.hypot;
import static com.starkinhon.battlegroundsmod.world.JjkGen.round;
import static com.starkinhon.battlegroundsmod.world.JjkGen.sin;
import static com.starkinhon.battlegroundsmod.world.JjkGen.valueNoise;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

import com.starkinhon.battlegroundsmod.world.LeafTreeGen.Rng;
import com.starkinhon.battlegroundsmod.world.TemploGen.Vox;

// The Terras do Jogo (Stark 2026-11-12; port released 2026-11-13): line-by-line port of the approved preview's generator
// (forja/jujutsu-jogo/gen.js), the same draws and the same hashes from the world's coordinates. The volcanic lands of
// the curse of volcanoes: black basalt ground covered in ash, cracked with glowing magma veins, lava rivers with obsidian
// banks, hexagonal basalt columns, fumaroles with yellow sulfur mouths letting out smoke, charred trees, now and then a
// steaming hot spring (Japan in the middle of hell). And the Caixão da Montanha de Ferro (Jogo's domain made a place): a
// volcano with the lava lake in its crater and three tongues of lava down its slope; beside it, the crater of one of
// Jogo's meteors with the molten rock still in the middle.
public final class JogoGen {
    // The preview's NAMES, in its order.
    public static final byte AIR = 0, STONE = 1, BASALT = 2, BASALT_DARK = 3, ASH = 4, MAGMA_CRACK = 5, LAVA = 6, OBSIDIAN = 7,
            COLUMN = 8, COLUMN_TOP = 9, SULFUR = 10, CHAR = 11, MAGMA_ROCK = 12, CINDER = 13, SPRING = 14, SPRING_STONE = 15,
            IRON = 16, BLACKSTONE = 17;

    // How far a ground object reaches from its column: basalt columns (6 and the noise), a hot spring (3 + 1 + 1).
    public static final int OBJECT_REACH = 8;
    public static final int VOLCANO_R = 76;
    // How far the volcano and its meteor crater reach from the volcano's center: the meteor 98 out, its rim 24 more.
    public static final int VOLCANO_REACH = 124;

    private JogoGen() {
    }

    // ---------------------------------------------------------------- terrain

    // An old lava plateau: rolling, with steps (layers of flows) and shallow valleys where the lava rivers run.
    public static int baseHeight(int x, int z, int seed) {
        double big = valueNoise(x, z, seed, 80), mid = valueNoise(x, z, seed + 1, 26), sm = valueNoise(x, z, seed + 2, 8);
        double raw = 14 + (big - 0.5) * 20 + (mid - 0.5) * 9 + (sm - 0.5) * 2;
        // steps: rounded to layers of 3, the edge softened by the small noise
        return floor(raw - ((raw % 3) + 3) % 3 * 0.6);
    }

    // The lava river: where a noise passes near 0.5 it becomes a valley with lava at the bottom.
    public static double riverAt(int x, int z, int seed) {
        return Math.abs(valueNoise(x, z, seed + 7, 46) - 0.5) + Math.abs(valueNoise(x, z, seed + 8, 13) - 0.5) * 0.25;
    }

    // The top a column's ground ends at (the river's bed 2 down, its bank 1 down).
    public static int topAt(int h, double river) {
        return river < 0.03 ? h - 2 : river < 0.045 ? h - 1 : h;
    }

    public static int ground0(Vox w, int x, int z, int h, int seed, double river) {
        if (river < 0.03) {
            // the bed: lava at the bottom (2 down), obsidian on the banks
            w.force(x, h - 2, z, LAVA);
            w.force(x, h - 3, z, BASALT_DARK);
            w.force(x, h - 4, z, STONE);
            return h - 2;
        }
        if (river < 0.045) {
            w.force(x, h - 1, z, OBSIDIAN);
            w.force(x, h - 2, z, BASALT_DARK);
            w.force(x, h - 3, z, STONE);
            return h - 1;
        }
        double ash = valueNoise(x, z, seed + 3, 10);
        double crackV = Math.abs(valueNoise(x, z, seed + 4, 6) - 0.5);
        byte top = crackV < 0.011 ? MAGMA_CRACK : ash > 0.62 ? ASH : hash3(x, 0, z, seed + 5) < 0.3 ? BASALT_DARK : BASALT;
        w.force(x, h, z, top);
        for (int k = 1; k <= 3; k++) {
            w.force(x, h - k, z, k <= 2 ? BASALT : STONE);
        }
        return h;
    }

    // ---------------------------------------------------------------- things

    // Basalt columns: a group of hexagonal columns (here each column 1 x 1 with its own height) packed together, the tops
    // in steps like a stone organ.
    public static void columns(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int r = rng.nextInt(3, 6), H = rng.nextInt(5, 12);
        double a = rng.range(0, Math.PI * 2);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = hypot(dx, dz);
                if (d > r + (hash3(x + dx, 0, z + dz, seed) - 0.5) * 1.5) {
                    continue;
                }
                // higher on one side, going down to the other
                double slope = (dx * cos(a) + dz * sin(a)) / r;
                int h = Math.max(1, round(H * (0.55 + 0.45 * slope) * (1 - d / (r + 2) * 0.4) + (hash3(x + dx, 1, z + dz, seed) - 0.5) * 3));
                for (int k = 0; k < h; k++) {
                    w.force(x + dx, y + k, z + dz, COLUMN);
                }
                w.force(x + dx, y + h, z + dz, COLUMN_TOP);
            }
        }
    }

    // Fumarole: a mound of cinders with the yellow sulfur mouth and a hole in the middle (where the smoke comes out).
    public static void fumarole(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int r = rng.nextInt(2, 3);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = hypot(dx, dz);
                if (d > r + 0.4) {
                    continue;
                }
                int h = round((r - d) * 0.9) + 1;
                for (int k = 0; k < h; k++) {
                    w.force(x + dx, y + k, z + dz, d < 1.5 ? SULFUR : CINDER);
                }
            }
        }
        for (int k = 0; k <= r + 1; k++) {
            w.force(x, y + k, z, AIR);
        }
        w.force(x, y - 1, z, MAGMA_CRACK);
    }

    // Charred tree: a crooked black trunk with 2 or 3 dry branches.
    public static void charTree(Vox w, int x, int y, int z, int seed) {
        Rng rng = new Rng(seed);
        int H = rng.nextInt(4, 8);
        int px = x, pz = z;
        for (int k = 0; k < H; k++) {
            if (k > 1 && rng.next() < 0.3) {
                px += rng.nextInt(-1, 1);
                pz += rng.nextInt(-1, 1);
            }
            w.force(px, y + k, pz, CHAR);
        }
        for (int i = 0; i < rng.nextInt(2, 3); i++) {
            int dx = rng.nextInt(-1, 1), dz = rng.nextInt(-1, 1), by = y + rng.nextInt(H - 3, H - 1);
            for (int k = 1; k <= rng.nextInt(1, 3); k++) {
                w.force(px + dx * k, by + (k > 1 ? 1 : 0), pz + dz * k, CHAR);
            }
        }
    }

    // Hot spring: a shallow pool of hot water with a rim of smooth stone (Japan even here).
    public static void spring(Vox w, int x, int y, int z, int seed) {
        int r = 3 + (hashInt(x, y, z, seed) % 2);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = hypot(dx, dz) + (hash3(x + dx, 0, z + dz, seed) - 0.5) * 0.8;
                if (d > r + 1) {
                    continue;
                }
                for (int k = 1; k <= 3; k++) {
                    w.force(x + dx, y + k, z + dz, AIR);
                }
                if (d > r) {
                    w.force(x + dx, y + 1, z + dz, SPRING_STONE);
                } else {
                    w.force(x + dx, y, z + dz, SPRING);
                    w.force(x + dx, y - 1, z + dz, SPRING_STONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- volcano and meteor

    public record Volcano(int cx, int cz, int R, int H, int top, int CR, int lakeY, double[] flows) {
    }

    // The Caixão da Montanha de Ferro: a cone of radius ~76 and 62 high over the plateau, a slope of ash and basalt with
    // bands of iron, a ragged crater rim, a deep crater with the lava lake, three tongues of lava running down.
    // The three tongues' directions, the first drawn at random, the other two well apart from it on each side.
    public static double[] flows(int seed) {
        Rng rng = new Rng(seed);
        double f0 = rng.range(0, Math.PI * 2);
        double f1 = f0 + rng.range(1.7, 2.4);
        double f2 = f0 - rng.range(1.7, 2.4);
        return new double[] {f0, f1, f2};
    }

    public static Volcano volcano(Vox w, int cx, int cz, int base, int seed, IntBinaryOperator ground) {
        final int R = VOLCANO_R, H = 62, CR = 17;
        double[] flows = flows(seed);
        for (int x = cx - R - 6; x <= cx + R + 6; x++) {
            for (int z = cz - R - 6; z <= cz + R + 6; z++) {
                volcanoColumn(w, x, z, cx, cz, base, seed, flows, ground);
            }
        }
        return new Volcano(cx, cz, R, H, base + H, CR, base + H - 10, flows);
    }

    // One column of the volcano (they do not depend on each other, so a chunk builds only its own).
    public static void volcanoColumn(Vox w, int x, int z, int cx, int cz, int base, int seed, double[] flows, IntBinaryOperator ground) {
        final int H = 62, CR = 17;
        int lakeY = base + H - 10;
        double hh = heightOf(x, z, cx, cz, base, seed, flows);
        if (hh < -1e8) {
            return;
        }
        int g0 = ground.applyAsInt(x, z), g = Math.max(g0, floor(hh));
        double d = hypot(x - cx, z - cz), a = atan2(z - cz, x - cx);
        for (int y = Math.min(base, g0) - 3; y <= g; y++) {
            // an iron band where floor(y / 4) is a multiple of 5 (the preview's % is 0 or -0 there, whatever the sign)
            boolean band = Math.floorMod(Math.floorDiv(y, 4), 5) == 0 && hash3(x, y, z, seed + 3) < 0.6;
            w.force(x, y, z, y == g ? (d < CR + 8 ? BLACKSTONE : valueNoise(x, z, seed + 4, 9) > 0.55 ? ASH : BASALT)
                    : band ? IRON : BASALT_DARK);
        }
        // the lava lake in the crater
        if (d < CR) {
            for (int y = g + 1; y <= lakeY; y++) {
                w.force(x, y, z, LAVA);
            }
        }
        // the lava tongues: at the bottom of the groove, from the rim to the foot
        for (int i = 0; i < flows.length; i++) {
            double fd = flowDist(a, d, i, flows, seed), wdt = 1.2 + d / 24 + (valueNoise(x, z, seed + 10, 6) - 0.5) * 1.2;
            if (d > CR + 1 && fd < wdt) {
                w.force(x, g, z, LAVA);
            } else if (d > CR + 1 && fd < wdt + 1.4) {
                w.force(x, g, z, hash3(x, g, z, seed + 11) < 0.3 ? MAGMA_CRACK : OBSIDIAN);
            }
        }
    }

    // The side distance (in blocks) to tongue i: its path winds as it goes down.
    static double flowDist(double a, double d, int i, double[] flows, int seed) {
        double f = flows[i] + sin(d * 0.09 + i * 2.1) * 0.16 + (valueNoise(Math.floor(d), i * 50, seed + 9, 7) - 0.5) * 0.2;
        double da = Math.abs(((a - f) % (Math.PI * 2) + Math.PI * 3) % (Math.PI * 2) - Math.PI);
        return da * d;
    }

    static double heightOf(int x, int z, int cx, int cz, int base, int seed, double[] flows) {
        final int R = VOLCANO_R, H = 62, CR = 17;
        int top = base + H;
        double d = hypot(x - cx, z - cz), a = atan2(z - cz, x - cx);
        double n = (valueNoise(x, z, seed + 1, 14) - 0.5) * 6 + (valueNoise(x, z, seed + 2, 5) - 0.5) * 2;
        if (d > R + 6) {
            return -1e9;
        }
        // a cone with a flattened top at the crater's rim; inside, the wall goes down to the lake
        double h = d >= CR ? base + H * Math.pow(Math.max(0, 1 - (d - CR) / (R - CR)), 1.25) + n * Math.min(1, (d - CR) / 12)
                : top - 2 - (CR - d) * 1.4 + n * 0.3;
        if (d >= CR && d < CR + 3) {
            h += 2 * (1 - (d - CR) / 3); // the raised rim
        }
        // the lava tongues' grooves
        double groove = 0;
        for (int i = 0; i < flows.length; i++) {
            groove = Math.max(groove, Math.max(0, 1 - flowDist(a, d, i, flows, seed) / 3));
        }
        return h - groove * 2;
    }

    // Where the meteor crater goes beside a volcano (the preview's, before keeping it inside its piece).
    public static int[] meteorSpot(Volcano v) {
        double ma = v.flows()[0] + Math.PI + 0.6;
        return new int[] {round(v.cx() + cos(ma) * 98), round(v.cz() + sin(ma) * 98)};
    }

    // Meteor crater: a bowl of radius ~18 with a raised rim of broken stone, a floor of black glass (obsidian) and the
    // molten rock still in the middle, a ball of radius 5 cracked with magma.
    public static void meteorCrater(Vox w, int cx, int cz, IntBinaryOperator ground, int seed) {
        final int R = 18;
        int gy = ground.applyAsInt(cx, cz);
        for (int x = cx - R - 6; x <= cx + R + 6; x++) {
            for (int z = cz - R - 6; z <= cz + R + 6; z++) {
                double d = hypot(x - cx, z - cz) + (valueNoise(x, z, seed, 5) - 0.5) * 3;
                if (d > R + 6) {
                    continue;
                }
                int g = ground.applyAsInt(x, z);
                int y;
                if (d < R) {
                    y = gy - round(9 * (1 - (d / R) * (d / R)));
                } else {
                    y = g + round(3 * (1 - (d - R) / 6));
                }
                for (int yy = Math.min(g, y) - 2; yy <= Math.max(g, y) + 4; yy++) {
                    w.force(x, yy, z, yy <= y ? (yy == y ? (d < R * 0.6 ? OBSIDIAN : d < R ? BLACKSTONE : CINDER) : BASALT_DARK) : AIR);
                }
                if (d < R * 0.7 && hash3(x, y, z, seed + 1) < 0.08) {
                    w.force(x, y, z, MAGMA_CRACK);
                }
            }
        }
        int by = gy - 9 + 4;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double d = hypot(dx, dy * 1.1, dz) + (hash3(cx + dx, by + dy, cz + dz, seed + 2) - 0.5) * 0.8;
                    if (d > 5) {
                        continue;
                    }
                    boolean crack = Math.abs(valueNoise(cx + dx + dy * 3, cz + dz - dy * 2, seed + 3, 3) - 0.5) < 0.07;
                    w.force(cx + dx, by + dy, cz + dz, crack || d < 3.5 ? MAGMA_CRACK : MAGMA_ROCK);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the fields, spot by spot

    // A ground object's spot on the grid of 18: {x, z, kind} (0 columns, 1 fumarole, 2 charred tree, 3 hot spring) or
    // kind -1 for none.
    public static int[] objectSpot(int gx, int gz, int seed) {
        int x = gx + 3 + floor(hash3(gx, 1, gz, seed + 20) * 12), z = gz + 3 + floor(hash3(gx, 2, gz, seed + 20) * 12);
        double r = hash3(gx, 3, gz, seed + 20);
        int kind = r < 0.22 ? 0 : r < 0.45 ? 1 : r < 0.7 ? 2 : r < 0.76 ? 3 : -1;
        return new int[] {x, z, kind};
    }

    public static void objectAt(Vox w, int[] o, int y, int seed) {
        int x = o[0], z = o[1];
        switch (o[2]) {
            case 0 -> columns(w, x, y + 1, z, hashInt(x, 0, z, seed + 21));
            case 1 -> fumarole(w, x, y + 1, z, hashInt(x, 1, z, seed + 22));
            case 2 -> charTree(w, x, y + 1, z, hashInt(x, 2, z, seed + 23));
            case 3 -> spring(w, x, y, z, hashInt(x, 3, z, seed + 24));
            default -> {
            }
        }
    }

    // The preview's fields (for the parity check).
    public static void fields(Vox w, int half, int seed, IntBinaryOperator ground, BiPredicate<Integer, Integer> keepOut) {
        Map<Long, Integer> tops = new HashMap<>();
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (keepOut.test(x, z)) {
                    continue;
                }
                int h = ground.applyAsInt(x, z);
                tops.put((long) x * 4096 + z, ground0(w, x, z, h, seed, riverAt(x, z, seed)));
            }
        }
        for (int gx = -half; gx < half; gx += 18) {
            for (int gz = -half; gz < half; gz += 18) {
                int[] o = objectSpot(gx, gz, seed);
                int x = o[0], z = o[1];
                if (keepOut.test(x, z) || Math.abs(x) > half - 8 || Math.abs(z) > half - 8 || riverAt(x, z, seed) < 0.08) {
                    continue;
                }
                Integer y = tops.get((long) x * 4096 + z);
                if (y == null) {
                    continue;
                }
                objectAt(w, o, y, seed);
            }
        }
    }
}
