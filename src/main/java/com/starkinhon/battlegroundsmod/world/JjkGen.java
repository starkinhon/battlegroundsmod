package com.starkinhon.battlegroundsmod.world;

// What every Jujutsu biome's generator shares with its preview's gen.js (forja/jujutsu-<bioma>): the same hash, noise
// and draws (LeafTreeGen, DesertGen), the Vox of the Templos (TemploGen.Vox: set fills only what is empty, force always,
// a force to air kept as CARVE), and the JavaScript math the previews use, done the same way so the doubles come out the
// same: Math.hypot as V8 does it (scaled by the biggest, Kahan sum), sin/cos/pow as fdlibm (StrictMath).
public final class JjkGen {
    private JjkGen() {
    }

    public static double hypot(double a, double b) {
        double x = Math.abs(a), y = Math.abs(b);
        double max = Math.max(x, y);
        if (max == 0) {
            return 0;
        }
        double sum = 0, comp = 0;
        for (double v : new double[] {x, y}) {
            double n = v / max, summand = n * n - comp, pre = sum + summand;
            comp = (pre - sum) - summand;
            sum = pre;
        }
        return Math.sqrt(sum) * max;
    }

    public static double hypot(double a, double b, double c) {
        double x = Math.abs(a), y = Math.abs(b), z = Math.abs(c);
        double max = Math.max(Math.max(x, y), z);
        if (max == 0) {
            return 0;
        }
        double sum = 0, comp = 0;
        for (double v : new double[] {x, y, z}) {
            double n = v / max, summand = n * n - comp, pre = sum + summand;
            comp = (pre - sum) - summand;
            sum = pre;
        }
        return Math.sqrt(sum) * max;
    }

    public static double cos(double a) {
        return StrictMath.cos(a);
    }

    public static double sin(double a) {
        return StrictMath.sin(a);
    }

    public static int floor(double a) {
        return (int) Math.floor(a);
    }

    // JavaScript's Math.round (half up).
    public static int round(double a) {
        return (int) Math.floor(a + 0.5);
    }

    public static double hash3(int x, int y, int z, int seed) {
        return LeafTreeGen.hash3(x, y, z, seed);
    }

    public static int hashInt(int x, int y, int z, int seed) {
        return (int) Math.floor(hash3(x, y, z, seed) * 2147483646) + 1;
    }

    public static double valueNoise(double x, double z, int seed, double scale) {
        return DesertGen.valueNoise(x, z, seed, scale);
    }

    // A tube of blocks between two points, its radius going from r0 to r1 (branches, roots, bent trunks). soft: set
    // instead of force (only where nothing is yet).
    public static void tube(TemploGen.Vox w, double[] a, double[] b, double r0, double r1, byte t, boolean soft) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = hypot(dx, dy, dz);
        int steps = Math.max(1, (int) Math.ceil(len * 2));
        for (int i = 0; i <= steps; i++) {
            double u = (double) i / steps, r = r0 + (r1 - r0) * u;
            double cx = a[0] + dx * u, cy = a[1] + dy * u, cz = a[2] + dz * u;
            int ri = (int) Math.ceil(r);
            for (int ox = -ri; ox <= ri; ox++) {
                for (int oy = -ri; oy <= ri; oy++) {
                    for (int oz = -ri; oz <= ri; oz++) {
                        if (ox * ox + oy * oy + oz * oz > r * r + 0.3) {
                            continue;
                        }
                        int x = floor(cx + ox), y = floor(cy + oy), z = floor(cz + oz);
                        if (soft) {
                            w.set(x, y, z, t);
                        } else {
                            w.force(x, y, z, t);
                        }
                    }
                }
            }
        }
    }

    public static void tube(TemploGen.Vox w, double[] a, double[] b, double r0, double r1, byte t) {
        tube(w, a, b, r0, r1, t, false);
    }
}
