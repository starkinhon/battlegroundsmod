package com.starkinhon.soulsjjk.power.powers.sukuna;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// The B's web of cuts (Cravada), as the 3D preview builds it (forja/sukuna-desmantelar, "Shift+X": its gweb, S_WEB and
// slash lists), shared by the server (the blocks it cuts out of the ground) and the client (client/fx/CravadaRenderer
// draws the same strokes). Everything is in his frame at the punch, around the body: x to his left, z ahead.
// Stark 2026-11-09: no blocks thrown around; the ground is cut out in the web's own shape, in the world's blocks.
public final class CravadaWeb {
    // The web's stages 2 and 3 (the preview's S_WEB): start tick after the stomp, spokes, inner and outer reach, the three
    // rings, how thick.
    public static final double[][] STAGES = {{14, 17, 5.0, 8.6, 6.0, 7.4, 8.4, 1.15}, {28, 25, 8.6, 13.2, 9.8, 11.4, 12.8, 1.3}};
    // The four huge straight cuts across it all: how long, how thick.
    public static final double CUT_LEN = 28, CUT_THICK = 1.15;

    // One stroke of the web: a spoke (angle a, middle at r) or a ring piece (middle mx, mz, along dx, dz). t0: the tick
    // after the stomp it starts drawing.
    public record Stroke(boolean spoke, double a, double r, double mx, double mz, double dx, double dz, double len, double thick, double t0,
            float skew, float rough, float seed) {
        // Its middle and direction in his frame (x left, z ahead).
        public double midX() {
            return spoke ? Math.cos(a) * r : mx;
        }

        public double midZ() {
            return spoke ? Math.sin(a) * r : mz;
        }

        public double dirX() {
            return spoke ? Math.cos(a) : dx;
        }

        public double dirZ() {
            return spoke ? Math.sin(a) : dz;
        }
    }

    public static final List<Stroke> WEB = build();

    private CravadaWeb() {
    }

    // The i-th big cut: angle, offset across, skew, roughness, delay (ticks after the flash).
    public static double cutAngle(int i) {
        return i * 0.81 + 0.35 + hash(i * 5.5) * 0.3;
    }

    public static double cutOffset(int i) {
        return (hash(i * 7.1) - 0.5) * 3;
    }

    public static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    private static List<Stroke> build() {
        List<Stroke> out = new ArrayList<>();
        int n = 11;
        double[] spokeA = new double[n];
        for (int k = 0; k < n; k++) {
            double h = hash(k * 3.3 + 1), a = (k + (hash(k * 1.9) - 0.5) * 0.5) / n * 6.2832, L = 2.6 + 2.8 * h;
            spokeA[k] = a;
            out.add(new Stroke(true, a, 0.3 + L / 2, 0, 0, 0, 0, L, 0.45 + 0.35 * hash(k * 5.1), 0.4 * hash(k * 7.7),
                    (float) (0.55 + 0.5 * hash(k * 8.3)), (float) (0.5 + 0.5 * hash(k * 9.9)), (float) (k * 2.7)));
        }
        double[] rings = {1.3, 2.3, 3.4};
        for (int j = 0; j < 3; j++) {
            double rj = rings[j];
            for (int k = 0; k < n; k++) {
                if (hash(k * 4.1 + j * 7.3) < 0.25) {
                    continue;
                }
                double a0 = spokeA[k], a1 = k + 1 < n ? spokeA[k + 1] : spokeA[0] + 6.2832;
                double r0 = rj * (1 + (hash(k + j * 3.1) - 0.5) * 0.12), r1 = rj * (1 + (hash(k + 1 + j * 3.1) - 0.5) * 0.12);
                out.add(piece(a0, a1, r0, r1, 0.94, 1.12, 0.22 + 0.14 * hash(k * 2.2 + j), 1 + rj * 0.5 + 0.4 * hash(k * 6.6 + j),
                        0.8 + 0.5 * hash(k * 3.9 + j), 0.6 + 0.4 * hash(k * 1.7 + j), 50 + k * 1.3 + j * 7.1));
            }
        }
        for (int si = 0; si < STAGES.length; si++) {
            double[] st = STAGES[si];
            int m = (int) st[1];
            double t = st[0], sr0 = st[2], sr1 = st[3], th = st[7];
            double[] ang = new double[m];
            for (int k = 0; k < m; k++) {
                ang[k] = (k + (hash(k * 2.3 + si * 11) - 0.5) * 0.5) / m * 6.2832 + si * 0.13;
            }
            for (int k = 0; k < m; k++) {
                if (hash(k * 5.7 + si * 3.3) < 0.15) {
                    continue;
                }
                double L = (sr1 - sr0) * (0.75 + 0.45 * hash(k * 1.3 + si * 7));
                out.add(new Stroke(true, ang[k], sr0 - 0.4 + L / 2, 0, 0, 0, 0, L, (0.5 + 0.35 * hash(k * 8.1 + si)) * th,
                        t + 0.4 * hash(k * 7.1 + si), (float) (0.55 + 0.5 * hash(k * 2.9 + si)), (float) (0.5 + 0.5 * hash(k * 6.3 + si)),
                        (float) (300 + si * 100 + k * 1.7)));
            }
            for (int j = 0; j < 3; j++) {
                double rj = st[4 + j];
                for (int k = 0; k < m; k++) {
                    if (hash(k * 4.7 + j * 9.1 + si * 13) < 0.2) {
                        continue;
                    }
                    double a0 = ang[k], a1 = k + 1 < m ? ang[k + 1] : ang[0] + 6.2832;
                    double r0 = rj * (1 + (hash(k + j * 3.7 + si) - 0.5) * 0.1), r1 = rj * (1 + (hash(k + 1 + j * 3.7 + si) - 0.5) * 0.1);
                    out.add(piece(a0, a1, r0, r1, 0.95, 1.1, (0.24 + 0.14 * hash(k * 2.6 + j + si)) * th,
                            t + 1 + (rj - sr0) * 0.35 + 0.4 * hash(k * 6.1 + j + si), 0.8 + 0.5 * hash(k * 3.3 + j + si),
                            0.6 + 0.4 * hash(k * 1.9 + j + si), 500 + si * 100 + k * 1.1 + j * 5.3));
                }
            }
        }
        return out;
    }

    // A ring piece between two spokes: from (a0, r0) to (a1, r1), its middle pulled in a little.
    private static Stroke piece(double a0, double a1, double r0, double r1, double in, double longer, double thick, double t0, double skew,
            double rough, double seed) {
        double ax = Math.cos(a0) * r0, az = Math.sin(a0) * r0, bx = Math.cos(a1) * r1, bz = Math.sin(a1) * r1;
        double dx = bx - ax, dz = bz - az, len = Math.sqrt(dx * dx + dz * dz);
        return new Stroke(false, 0, 0, (ax + bx) * 0.5 * in, (az + bz) * 0.5 * in, dx / len, dz / len, len * longer, thick, t0, (float) skew,
                (float) rough, (float) seed);
    }

    // The blocks the web cuts out of the ground around the body (centre: the body's feet, fwd: his forward at the punch):
    // under every stroke a trench a block wide along the part that is drawn thick (not its needle tips): the ring pieces
    // one deep, the spokes two, the four big cuts three. Each column is cut from the ground's top (the first solid block
    // from 2 over the centre to 4 under). Block entities and unbreakable blocks stay. Same list on server and client.
    public static List<BlockPos> cuts(Level level, Vec3 centre, Vec3 fwd) {
        Vec3 left = new Vec3(fwd.z, 0, -fwd.x);
        Set<BlockPos> out = new LinkedHashSet<>();
        for (Stroke w : WEB) {
            trench(level, centre, left, fwd, w.midX(), w.midZ(), w.dirX(), w.dirZ(), w.len(), w.spoke() ? 2 : 1, out);
        }
        for (int i = 0; i < 4; i++) {
            double a = cutAngle(i), dx = Math.cos(a), dz = Math.sin(a), off = cutOffset(i);
            trench(level, centre, left, fwd, -dz * off, dx * off, dx, dz, CUT_LEN, 3, out);
        }
        return new ArrayList<>(out);
    }

    private static void trench(Level level, Vec3 centre, Vec3 left, Vec3 fwd, double mx, double mz, double dx, double dz, double len, int depth,
            Set<BlockPos> out) {
        int steps = Math.max(2, (int) Math.ceil(len / 0.4));
        int cy = Mth.floor(centre.y);
        for (int i = 0; i <= steps; i++) {
            double u = i / (double) steps;
            if (u < 0.1 || u > 0.9) {
                continue;
            }
            double x = mx + dx * (u - 0.5) * len, z = mz + dz * (u - 0.5) * len;
            Vec3 p = centre.add(left.scale(x)).add(fwd.scale(z));
            int bx = Mth.floor(p.x), bz = Mth.floor(p.z);
            for (int y = cy + 2; y >= cy - 4; y--) {
                BlockPos pos = new BlockPos(bx, y, bz);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                for (int d = 0; d < depth; d++) {
                    BlockPos q = pos.below(d);
                    BlockState s = level.getBlockState(q);
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level, q) < 0) {
                        break;
                    }
                    out.add(q);
                }
                break;
            }
        }
    }
}
