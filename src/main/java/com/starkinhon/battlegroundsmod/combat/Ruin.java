// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// The end-game bosses' destruction (Hashirama and Freeza Poder Total, approved page forja/escala-fim-de-jogo,
// https://claude.ai/artifact/3W7E3frk5jNpm6AVxXzcyP, Stark 2026-11-19: "ISSO É UM BOSS FINAL"). Every change to the
// terrain runs like the page's jobs: spread over ticks from the middle out, with flags 2|16 (no neighbour updates, the
// lag lesson of the Supernova #258) and a budget of block changes per tick shared by every job, so the front only
// slows down instead of freezing the server. Nothing happens to the blocks when BlockRules.canBreak is off (a Dungeon
// arena, the gamerules): the visuals and the damage stay the same.
//   bowl      a crater: a bowl with its rim lifted, the floor torn (scorched near the middle with heat, magma and lava
//             pools at the bottom when it burns)
//   fissures  crooked cracks running out from the middle (the zero of a noise round the angle), with lava or not
//   trench    a furrow along a line, opened with its tip (a dragon digging under the ground, the Corte do Planeta)
//   palm      the print of a hand sunk into the ground: palm, four fingers and the thumb, rim lifted round it
//   heave     the ground swelling in a wave (one or two blocks up here and there), torn in spots
//   Build     blocks set at their own tick (the colossal trees); without destruction they go away after a while
// The client draws the flying blocks, the dust and the tremors on its own (client/fx/epic, RuinNet).
public final class Ruin {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    // block changes per pulse, every job together
    private static final int BUDGET = 9000;
    // Every job changes its blocks on the same ticks, one in PULSE (Stark 2026-11-19: the dragon and the Buddha took him
    // from 90 to 10-20 FPS): each chunk section the client has to re-mesh is re-meshed once a pulse instead of every tick.
    private static final int PULSE = 3;
    private static long budgetTick = Long.MIN_VALUE;
    private static int spent;

    private Ruin() {
    }

    private static void job(ServerLevel level, Tasks.Task task) {
        Tasks.addSystem(age -> level.getGameTime() % PULSE == 0 && task.tick(age));
    }

    private static boolean spend(ServerLevel level) {
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            spent = 0;
        }
        return spent < BUDGET;
    }

    // ---------- the column helpers ----------

    private static boolean loaded(ServerLevel level, int x, int z) {
        return level.hasChunk(x >> 4, z >> 4);
    }

    // The top block of the ground at (x, z): leaves and plants left out, water looked through (its floor).
    public static int ground(ServerLevel level, int x, int z) {
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, h, z);
        for (int i = 0; i < 40 && h > level.getMinBuildHeight(); i++) {
            BlockState s = level.getBlockState(p);
            if (s.getFluidState().isEmpty() && !s.isAir() && !s.is(BlockTags.LOGS)) {
                return h;
            }
            p.setY(--h);
        }
        return h;
    }

    private static boolean fragile(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
    }

    // Takes one block out (air), unless it holds things (BlockRules.keeps) or cannot be broken (bedrock, barriers).
    private static boolean clear(ServerLevel level, BlockPos.MutableBlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || BlockRules.keeps(s) || s.getDestroySpeed(level, p) < 0) {
            return false;
        }
        level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        spent++;
        return true;
    }

    private static void put(ServerLevel level, BlockPos p, BlockState state) {
        BlockState s = level.getBlockState(p);
        if (BlockRules.keeps(s) || s.getDestroySpeed(level, p) < 0 || s == state) {
            return;
        }
        level.setBlock(p, state, FLAGS);
        spent++;
    }

    // Lowers the column at (x, z) to floor (that block stays, everything over it goes, trees and plants too).
    private static int lower(ServerLevel level, int x, int z, int h, int floor) {
        int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, h + 40);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        int n = 0;
        for (int y = top; y > floor && y > level.getMinBuildHeight() + 1; y--) {
            p.setY(y);
            if (clear(level, p)) {
                n++;
            }
        }
        return n;
    }

    // Lifts the column by n blocks of the given state (only into air and plants).
    private static void lift(ServerLevel level, int x, int z, int h, int n, BlockState state) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, h, z);
        for (int i = 1; i <= n; i++) {
            p.setY(h + i);
            if (fragile(level.getBlockState(p))) {
                level.setBlock(p, state, FLAGS);
                spent++;
            }
        }
    }

    private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState ROOTED = Blocks.ROOTED_DIRT.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

    private static BlockState torn(float r) {
        return r < 0.55f ? COARSE : r < 0.8f ? ROOTED : DIRT;
    }

    // The floor of a torn column: soft ground turns to torn dirt, rock stays rock; burned near the middle.
    private static void floor(ServerLevel level, int x, int y, int z, float scorch, boolean deep, float r) {
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) {
            return;
        }
        if (scorch > 0) {
            put(level, p, r < 0.5f ? Blocks.BLACKSTONE.defaultBlockState() : r < 0.75f ? Blocks.BASALT.defaultBlockState()
                    : r < 0.9f ? COARSE : Blocks.MAGMA_BLOCK.defaultBlockState());
        } else if (s.is(BlockTags.DIRT) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)) {
            put(level, p, deep && r < 0.3f ? Blocks.GRAVEL.defaultBlockState() : torn(r));
        }
    }

    // ---------- noise (the page's vnoise, cheap value noise) ----------

    private static float hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1442695041 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    public static float noise(double x, double y, double z, int seed) {
        int ix = Mth.floor(x), iy = Mth.floor(y), iz = Mth.floor(z);
        float fx = (float) (x - ix), fy = (float) (y - iy), fz = (float) (z - iz);
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float a = Mth.lerp(fx, hash(ix, iy, iz, seed), hash(ix + 1, iy, iz, seed));
        float b = Mth.lerp(fx, hash(ix, iy + 1, iz, seed), hash(ix + 1, iy + 1, iz, seed));
        float c = Mth.lerp(fx, hash(ix, iy, iz + 1, seed), hash(ix + 1, iy, iz + 1, seed));
        float d = Mth.lerp(fx, hash(ix, iy + 1, iz + 1, seed), hash(ix + 1, iy + 1, iz + 1, seed));
        return Mth.lerp(fz, Mth.lerp(fy, a, b), Mth.lerp(fy, c, d));
    }

    private static float rnd(int x, int z, int k) {
        return hash(x, k, z, 77);
    }

    // ---------- the jobs ----------

    // Options of a terrain job (the page's o = {...}).
    public static final class Opts {
        double rim = 0.22, scorch, lavaU, extra, lip = 1.5, width = 0.075;
        int seed = 3;
        boolean lava, left;
        double base = Double.NaN;

        public Opts rim(double v) {
            rim = v;
            return this;
        }

        // the share of the radius that burns (blackstone, basalt, magma)
        public Opts scorch(double v) {
            scorch = v;
            return this;
        }

        // lava pools in the bottom inside this share of the radius (fissures: lava in the cracks)
        public Opts lava(double u) {
            lava = true;
            lavaU = u;
            return this;
        }

        public Opts base(double y) {
            base = y;
            return this;
        }

        public Opts extra(double v) {
            extra = v;
            return this;
        }

        public Opts lip(double v) {
            lip = v;
            return this;
        }

        public Opts width(double v) {
            width = v;
            return this;
        }

        public Opts seed(int v) {
            seed = v;
            return this;
        }

        public Opts left(boolean v) {
            left = v;
            return this;
        }
    }

    public static Opts opts() {
        return new Opts();
    }

    private static int[][] disk(double r) {
        int rr = Mth.ceil(r) + 1;
        List<int[]> out = new ArrayList<>();
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r) {
                    out.add(new int[] {dx, dz, (int) Math.round(d * 64)});
                }
            }
        }
        int[][] a = out.toArray(new int[0][]);
        Arrays.sort(a, (p, q) -> Integer.compare(p[2], q[2]));
        return a;
    }

    // A crater of radius R and depth (blocks) at (cx, cz); the front runs from the middle to the rim in dur ticks.
    public static void bowl(ServerLevel level, double cx, double cz, double R, double depth, int dur, int delay, Opts o) {
        if (!BlockRules.canBreak(level) || R < 1) {
            return;
        }
        int[][] cells = disk(R * 1.38);
        int x0 = Mth.floor(cx), z0 = Mth.floor(cz);
        int[] k = {0};
        double[] ref = {o.base};
        job(level, age -> {
            int t = age - delay;
            if (t < 0) {
                return false;
            }
            if (Double.isNaN(ref[0])) {
                ref[0] = loaded(level, x0, z0) ? ground(level, x0, z0) : 64;
            }
            double front = R * 1.38 * Math.min(1, Math.pow((t + 1) / (double) Math.max(1, dur), 0.7)) * 64;
            while (k[0] < cells.length && cells[k[0]][2] <= front && spend(level)) {
                int[] c = cells[k[0]++];
                int x = x0 + c[0], z = z0 + c[1];
                if (!loaded(level, x, z)) {
                    continue;
                }
                double u = c[2] / 64.0 / R;
                float n = noise(x * 0.25, 0, z * 0.25, 5) - 0.5f, r = rnd(x, z, 1);
                int h = ground(level, x, z);
                if (u < 1) {
                    int floorY = (int) Math.round(Math.min(h, ref[0]) - depth * Math.sqrt(Math.max(0, 1 - u * u)) * (0.9 + 0.25 * n) - o.extra * (1 - u));
                    int target = Math.min(h, floorY);
                    if (target < h) {
                        lower(level, x, z, h, target);
                    }
                    boolean burn = o.scorch > 0 && u < o.scorch;
                    if (o.lava && u < o.lavaU && r < 0.55f) {
                        put(level, new BlockPos(x, target, z), r < 0.3f ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState());
                    } else if (target < h || burn) {
                        floor(level, x, target, z, burn ? 1 : 0, h - target > 3 && u < 0.85, r);
                    }
                } else if (o.rim > 0) {
                    int lift = (int) Math.round(depth * o.rim * Math.exp(-Math.pow((u - 1.08) / 0.14, 2)) * (0.7 + 0.6 * (n + 0.5)));
                    if (lift > 0) {
                        lift(level, x, z, h, lift, torn(r));
                    }
                }
            }
            return k[0] >= cells.length;
        });
    }

    // Cracks running out from r0 to r1 at speed blocks a tick: depth blocks deep, width the share of the angle's noise.
    public static void fissures(ServerLevel level, double cx, double cz, double r0, double r1, double speed, int delay, double depth, Opts o) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int x0 = Mth.floor(cx), z0 = Mth.floor(cz);
        List<int[]> list = new ArrayList<>();
        int rr = Mth.ceil(r1) + 1, seed = o.seed;
        double width = o.width;
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r < r0 || r > r1) {
                    continue;
                }
                double a = Math.atan2(dz, dx), ca = Math.cos(a), sa = Math.sin(a);
                double n = (noise(ca * 2.1 + seed, sa * 2.1, r * 0.022, 9) - 0.5) * 2 + 0.45 * (noise(ca * 5.3, sa * 5.3 + seed, r * 0.06, 9) - 0.5) * 2
                        + 0.15 * noise(dx * 0.3, dz * 0.3, seed, 9);
                double w = width * (0.6 + 0.8 * Math.exp(-r / (r1 * 0.5))) * (1 - 0.7 * Math.pow(r / r1, 2));
                if (Math.abs(n) < w) {
                    list.add(new int[] {dx, dz, (int) Math.round(r * 64), (int) (Math.abs(n) / w * 1000)});
                } else if (Math.abs(n) < w * 2.3) {
                    list.add(new int[] {dx, dz, (int) Math.round(r * 64), -1});
                }
            }
        }
        int[][] cells = list.toArray(new int[0][]);
        Arrays.sort(cells, (p, q) -> Integer.compare(p[2], q[2]));
        int[] k = {0};
        job(level, age -> {
            int t = age - delay;
            if (t < 0) {
                return false;
            }
            double front = (r0 + t * speed) * 64;
            while (k[0] < cells.length && cells[k[0]][2] <= front && spend(level)) {
                int[] c = cells[k[0]++];
                int x = x0 + c[0], z = z0 + c[1];
                if (!loaded(level, x, z)) {
                    continue;
                }
                int h = ground(level, x, z);
                float r = rnd(x, z, 2);
                if (c[3] >= 0) {
                    double cc = c[3] / 1000.0, rad = c[2] / 64.0;
                    int dd = (int) Math.round(depth * (1 - cc * 0.6) * (1 - 0.5 * rad / r1) + r);
                    if (dd > 0) {
                        lower(level, x, z, h, h - dd);
                        BlockPos b = new BlockPos(x, h - dd, z);
                        if (o.lava) {
                            put(level, b, r < 0.35f ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState());
                        } else {
                            floor(level, x, h - dd, z, 0, true, r);
                        }
                    }
                } else if (r < 0.55f) {
                    if (r < 0.27f) {
                        lift(level, x, z, h, 1, torn(r * 2));
                    } else {
                        floor(level, x, h, z, 0, false, r);
                    }
                }
            }
            return k[0] >= cells.length;
        });
    }

    // A furrow from A to B (only x and z matter), width and depth in blocks, opened with its tip in dur ticks.
    public static void trench(ServerLevel level, Vec3 A, Vec3 B, double width, double depth, int dur, int delay, Opts o) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        double L = Math.max(1, Math.hypot(B.x - A.x, B.z - A.z)), dx = (B.x - A.x) / L, dz = (B.z - A.z) / L, nx = -dz, nz = dx;
        List<double[]> list = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        double W = Math.ceil(width * 1.6) + 1;
        for (double s = -2; s <= L + 2; s += 0.5) {
            for (double w = -W; w <= W; w += 0.5) {
                int x = Mth.floor(A.x + dx * s + nx * w), z = Mth.floor(A.z + dz * s + nz * w);
                if (seen.add(BlockPos.asLong(x, 0, z))) {
                    list.add(new double[] {x, z, s, w});
                }
            }
        }
        double[][] cells = list.toArray(new double[0][]);
        Arrays.sort(cells, (p, q) -> Double.compare(p[2], q[2]));
        int[] k = {0};
        job(level, age -> {
            int t = age - delay;
            if (t < 0) {
                return false;
            }
            double front = L * Math.min(1, (t + 1) / (double) Math.max(1, dur)) + 2;
            while (k[0] < cells.length && cells[k[0]][2] <= front && spend(level)) {
                double[] c = cells[k[0]++];
                int x = (int) c[0], z = (int) c[1];
                if (!loaded(level, x, z)) {
                    continue;
                }
                double wob = width * (0.85 + 0.35 * noise(c[2] * 0.2, 1, o.seed, 4)), u = Math.abs(c[3]) / wob;
                int h = ground(level, x, z);
                float r = rnd(x, z, 3);
                if (u < 1) {
                    int d = (int) Math.round(depth * Math.sqrt(1 - u * u) * (0.85 + 0.3 * noise(x * 0.4, 0, z * 0.4, 2)));
                    if (d > 0) {
                        lower(level, x, z, h, h - d);
                        floor(level, x, h - d, z, o.scorch > 0 && u < 0.6 ? 1 : 0, true, r);
                    }
                } else if (u < 1.6) {
                    int lift = (int) Math.round(o.lip * (1 - (u - 1) / 0.6) * (0.5 + r));
                    if (lift > 0) {
                        lift(level, x, z, h, lift, torn(r));
                    }
                }
            }
            return k[0] >= cells.length;
        });
    }

    private static double segD(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az, k = Mth.clamp(((px - ax) * vx + (pz - az) * vz) / (vx * vx + vz * vz), 0, 1);
        return Math.hypot(px - ax - vx * k, pz - az - vz * k);
    }

    // The hand's shape (the page's palmPrint mask): > 0 inside, the palm along u (fingers forward), v sideways.
    static double palmMask(double u, double v, double L, boolean left) {
        double m = 1 - Math.hypot(u / (0.58 * L), v / (0.5 * L));
        double[] fv = {-0.34, -0.115, 0.115, 0.34}, fl = {0.95, 1.2, 1.15, 0.9};
        for (int i = 0; i < 4; i++) {
            double len = fl[i] * L * 0.62, w = 0.12 * L;
            m = Math.max(m, 1 - segD(u, v, 0.45 * L, fv[i] * L, 0.45 * L + len, fv[i] * L * 1.15) / w);
        }
        double th = left ? -1 : 1;
        return Math.max(m, 1 - segD(u, v, 0.05 * L, th * 0.45 * L, 0.5 * L, th * 0.82 * L) / (0.13 * L));
    }

    // The print of a hand of size L (blocks) sunk depth into the ground at (cx, cz), fingers towards yaw (the page's
    // frame: forward = (sin yaw, -cos yaw)).
    public static void palm(ServerLevel level, double cx, double cz, double yaw, double L, double depth, int dur, int delay, Opts o) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        double fx = Math.sin(yaw), fz = -Math.cos(yaw), sx = -fz, sz = fx;
        int rr = Mth.ceil(L * 1.5), x0 = (int) Math.round(cx), z0 = (int) Math.round(cz);
        List<double[]> list = new ArrayList<>();
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                double u = dx * fx + dz * fz, v = dx * sx + dz * sz, m = palmMask(u, v, L, o.left);
                if (m > -0.25) {
                    list.add(new double[] {dx, dz, m, Math.hypot(dx, dz)});
                }
            }
        }
        double[][] cells = list.toArray(new double[0][]);
        Arrays.sort(cells, (p, q) -> Double.compare(p[3], q[3]));
        int[] k = {0};
        double[] ref = {Double.NaN};
        job(level, age -> {
            int t = age - delay;
            if (t < 0) {
                return false;
            }
            if (Double.isNaN(ref[0])) {
                ref[0] = loaded(level, x0, z0) ? ground(level, x0, z0) : 64;
            }
            double front = rr * Math.min(1, (t + 1) / (double) Math.max(1, dur)) + 1;
            while (k[0] < cells.length && cells[k[0]][3] <= front && spend(level)) {
                double[] c = cells[k[0]++];
                int x = x0 + (int) c[0], z = z0 + (int) c[1];
                if (!loaded(level, x, z)) {
                    continue;
                }
                double m = c[2];
                int h = ground(level, x, z);
                float r = rnd(x, z, 4);
                if (m > 0) {
                    int target = Math.min(h, (int) Math.round(ref[0] - depth * Math.min(1, 0.55 + m * 1.4)));
                    if (target < h) {
                        lower(level, x, z, h, target);
                        floor(level, x, target, z, 0, m > 0.35, r);
                    }
                } else {
                    int lift = (int) Math.round(o.lip * (1 + m * 4) * (0.6 + 0.8 * r));
                    if (lift > 0) {
                        lift(level, x, z, h, lift, torn(r));
                    }
                }
            }
            return k[0] >= cells.length;
        });
    }

    // The ground swelling in a wave from (cx, cz) out to R at speed blocks a tick: columns go up a block or two where a
    // noise says, some torn open. The grass stays on top.
    public static void heave(ServerLevel level, double cx, double cz, double r0, double R, double speed, int delay, int seed) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int[][] cells = disk(R);
        int x0 = Mth.floor(cx), z0 = Mth.floor(cz);
        int[] k = {0};
        job(level, age -> {
            int t = age - delay;
            if (t < 0) {
                return false;
            }
            double front = (t + 1) * speed * 64;
            while (k[0] < cells.length && cells[k[0]][2] <= front && spend(level)) {
                int[] c = cells[k[0]++];
                double r = c[2] / 64.0;
                if (r < r0) {
                    continue;
                }
                int x = x0 + c[0], z = z0 + c[1];
                if (!loaded(level, x, z)) {
                    continue;
                }
                float n = noise(x * 0.18, 0, z * 0.18, 11 + seed), m = noise(x * 0.6, 0, z * 0.6, 4 + seed);
                int up = (int) Math.round((n - 0.35) * 3.4 * (1 - Math.pow(r / R, 3)));
                int h = ground(level, x, z);
                BlockPos top = new BlockPos(x, h, z);
                BlockState s = level.getBlockState(top);
                if (up > 0 && (s.is(BlockTags.DIRT) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL))) {
                    BlockState cap = m > 0.55f ? torn(m) : s;
                    if (fragile(level.getBlockState(top.above(up)))) {
                        put(level, top, s.is(Blocks.GRASS_BLOCK) ? DIRT : s);
                        lift(level, x, z, h, up - 1, s.is(Blocks.GRASS_BLOCK) ? DIRT : s);
                        BlockPos p = top.above(up);
                        if (fragile(level.getBlockState(p))) {
                            level.setBlock(p, cap, FLAGS);
                            spent++;
                        }
                    }
                } else if (m > 0.78f) {
                    floor(level, x, h, z, 0, false, m);
                }
            }
            return k[0] >= cells.length;
        });
    }

    // Blocks set each at its own tick (sorted by it). With destruction on they take the place of air, plants, leaves and
    // snow and stay in the world; with it off they only go into air and are taken away after keepFor ticks.
    public static final class Build {
        private final List<long[]> list = new ArrayList<>();
        private final List<BlockState> states = new ArrayList<>();

        public void add(int x, int y, int z, BlockState state, int tick) {
            list.add(new long[] {BlockPos.asLong(x, y, z), tick, states.size()});
            states.add(state);
        }

        public int size() {
            return list.size();
        }

        public void start(ServerLevel level, int keepFor) {
            long[][] cells = list.toArray(new long[0][]);
            Arrays.sort(cells, (p, q) -> Long.compare(p[1], q[1]));
            boolean stays = BlockRules.canBreak(level);
            List<BlockPos> placed = new ArrayList<>();
            Set<Long> done = new HashSet<>();
            int[] k = {0};
            job(level, age -> {
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                while (k[0] < cells.length && cells[k[0]][1] <= age && spend(level)) {
                    long[] c = cells[k[0]++];
                    if (!done.add(c[0])) {
                        continue;
                    }
                    p.set(c[0]);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (stays ? fragile(s) && !BlockRules.keeps(s) : s.isAir()) {
                        level.setBlock(p, states.get((int) c[2]), FLAGS);
                        spent++;
                        if (!stays) {
                            placed.add(p.immutable());
                        }
                    }
                }
                if (k[0] < cells.length) {
                    return false;
                }
                if (!stays && !placed.isEmpty()) {
                    takeAway(level, placed, keepFor);
                }
                return true;
            });
        }

        private static void takeAway(ServerLevel level, List<BlockPos> placed, int after) {
            List<BlockState> mine = new ArrayList<>();
            for (BlockPos p : placed) {
                mine.add(level.getBlockState(p));
            }
            int[] k = {placed.size() - 1};
            job(level, age -> {
                if (age < after) {
                    return false;
                }
                while (k[0] >= 0 && spend(level)) {
                    BlockPos p = placed.get(k[0]);
                    if (level.isLoaded(p) && level.getBlockState(p) == mine.get(k[0])) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                        spent++;
                    }
                    k[0]--;
                }
                return k[0] < 0;
            });
        }
    }
}
