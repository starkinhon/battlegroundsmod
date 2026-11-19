package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The air of the Floresta Negra (Stark 2026-11-19: the preview forja/jujutsu-floresta-ambiente, v2 approved). amb.js
// there is the source of truth and this is its port, the same names where it helps. Fades in when the player walks into
// the biome and out when the player leaves (BiomeAura). Everything makes physical sense:
// - the mist is cold still air: it gathers in the hollows and near the water, drags slowly, opens round whoever walks
//   and closes again behind (the pushed air comes back). Soft flat discs lying on the ground in 3 heights, no edge;
// - the canopy drips: a drop falls with gravity from the lowest leaf of the column and opens a ring on the black water;
// - a light shaft only where the canopy has a hole, slanted by the sun; the dust only shows inside it;
// - the Árvore Matriz's miasma is heavier than air: it leaves the mouth, sinks and runs along the ground losing force;
// - a falling leaf swings from side to side (pendulum);
// - the cursed fungi breathe their light and now and then let out spores;
// - the cursed eyes on the bark blink and their slit pupil turns to whoever comes near;
// - at night, eyes of small and big curses in the dark between the trees: always in pairs, they only shine from the front
//   (a turned head goes dark), they lurk walking slowly and flee (close and go) when you come near. A trunk in front
//   hides them (depth test), and they only open where they see you (a free line to the camera);
// - the screen (fx_jjgrade): less color and violet edges by day, violet edges at night, never a dark veil;
// - sounds built from noise and tones (tools/sounds/floresta_sounds.py): the low drone, trunks creaking far away, drops
//   on the water, the Matriz exhaling.
public final class FlorestaAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double G = 9.8;
    private static final int SCAN = 48;
    private static final int CELLS = 64;

    private static float strength, lastStrength;
    private static float night;
    private static final double[] SUN = {0, 1, 0};
    private static long lastNanos;
    private static ClientLevel lastLevel;
    private static double lookX, lookY = 0, lookZ = 1;

    // ---------------------------------------------------------------- what the world has (the column scan)

    private static final class Eye {
        int x, y, z, nx, nz;
        double px, py, blink, next = 1 + RANDOM.nextDouble() * 8, look = RANDOM.nextDouble() * 6.28;
    }

    private static final class Fungus {
        double x, y, z, ph = RANDOM.nextDouble() * 6.28, per = 4 + RANDOM.nextDouble() * 5, next = 6 + RANDOM.nextDouble() * 30;
    }

    private static final class Scan {
        final List<int[]> water = new ArrayList<>();
        final LongOpenHashSet wet = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<Fungus> fungi = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<Eye> eyes = new Long2ObjectOpenHashMap<>();
        double mawX, mawY, mawZ, fleshX, fleshZ;
        int maw, flesh;
    }

    private static Scan cur = new Scan(), next = new Scan();
    private static int scanRow = -SCAN - 1, scanCx, scanCz;
    private static boolean firstPass = true;

    // the lowest leaf over each column (or none), read when asked and kept 10 s
    private static final Long2IntOpenHashMap CANOPY = new Long2IntOpenHashMap();
    private static long canopyAt;
    private static final int NONE = Integer.MIN_VALUE;

    // the holes in the canopy (a shaft of light by day): x, z, y0, y1, w, ph
    private static List<double[]> gaps = new ArrayList<>();
    // the Árvore Matriz's mouth (where the miasma comes out) and the way it faces
    private static boolean lair;
    private static double mouthX, mouthY, mouthZ, mouthDx, mouthDz;

    // ---------------------------------------------------------------- moving things

    private static final class Mist {
        double y, k, under;
    }

    private static final Mist[][] MIST = new Mist[CELLS + 1][CELLS + 1];
    private static int mistX = Integer.MIN_VALUE, mistZ;
    private static long mistAt;
    private static double mistFx, mistFz;

    private static final class Wake {
        double x, z, age;
    }

    private static final List<Wake> WAKE = new ArrayList<>();
    private static double lastCamX = 1e9, lastCamZ;

    private static final class Drip {
        double x, y, z, vy, gy;
        boolean pool;
    }

    private static final List<Drip> DRIPS = new ArrayList<>();
    private static double dripAcc;

    private static final class Ring {
        double x, y, z, age, r;
    }

    private static final List<Ring> RINGS = new ArrayList<>();

    private static final class Spore {
        double x, y, z, vx, vy, vz, age, life, ph;
    }

    private static final List<Spore> SPORES = new ArrayList<>();

    private static final class Puff {
        double x, y, z, vx, vy, vz, age, life, rot, s0;
        final double[] r = new double[9];
    }

    private static final List<Puff> BREATH = new ArrayList<>();
    private static double nextBreath = 6;

    private static final class Leaf {
        double x, y = -999, z, g, ph, amp, ax, az, s, tone, rest;
        boolean violet;
    }

    private static final Leaf[] LEAVES = new Leaf[260];

    private static final class Mote {
        int g;
        double u, h, v, ph;
    }

    private static final Mote[] MOTES = new Mote[900];

    private static final class Watch {
        double x, y, z, h, sp, r, age, life, out, th, tth, nextTurn, blink, nextBlink, vx, vz, nextWalk, walk;
        boolean big, dead = true, red;
    }

    private static final List<Watch> WATCH = new ArrayList<>();

    // sounds
    private static DroneLoop drone;
    private static double nextCreak = 6;

    private static final class Pending {
        double at, x, y, z;
        float vol;
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static double clockS;

    private FlorestaAirFx() {
    }

    static {
        for (int i = 0; i < LEAVES.length; i++) {
            LEAVES[i] = new Leaf();
        }
        for (int i = 0; i < MOTES.length; i++) {
            Mote m = MOTES[i] = new Mote();
            m.g = i;
            m.u = RANDOM.nextDouble();
            m.h = RANDOM.nextDouble();
            m.v = RANDOM.nextDouble();
            m.ph = RANDOM.nextDouble() * 6.28;
        }
    }

    // ---------------------------------------------------------------- small helpers

    private static double rnd() {
        return RANDOM.nextDouble();
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double hsh(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffffff) / 16777216.0;
    }

    private static double vnoise(double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hsh(ix, iz) + (hsh(ix + 1, iz) - hsh(ix, iz)) * fx;
        double b = hsh(ix, iz + 1) + (hsh(ix + 1, iz + 1) - hsh(ix, iz + 1)) * fx;
        return a + (b - a) * fz;
    }

    private static long key(int x, int z) {
        return (long) x << 32 ^ (z & 0xffffffffL);
    }

    private static long key3(int x, int y, int z) {
        return ((long) x & 0x3ffffff) << 38 | ((long) z & 0x3ffffff) << 12 | (y & 0xfff);
    }

    // the ground under the canopy (leaves left out): the top block of the column, -999 where not loaded
    private static int top(ClientLevel level, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!level.hasChunk(ix >> 4, iz >> 4)) {
            return -999;
        }
        int h = ClientHeights.noLeaves(level, ix, iz) - 1;
        return h <= level.getMinBuildHeight() ? -999 : h;
    }

    // the lowest leaf of the canopy over the column (sweeping up from the ground), or NONE: open sky
    private static int canopyAt(ClientLevel level, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        long k = key(ix, iz);
        if (CANOPY.containsKey(k)) {
            return CANOPY.get(k);
        }
        int g = top(level, ix, iz);
        if (g < -900) {
            return NONE;
        }
        int c = NONE;
        int hi = Math.min(g + 45, level.getHeight(Heightmap.Types.WORLD_SURFACE, ix, iz));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = g + 3; y < hi; y++) {
            if (level.getBlockState(pos.set(ix, y, iz)).is(BlockTags.LEAVES)) {
                c = y;
                break;
            }
        }
        if (CANOPY.size() > 40000) {
            CANOPY.clear();
        }
        CANOPY.put(k, c);
        return c;
    }

    private static boolean isWater(ClientLevel level, int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    // ---------------------------------------------------------------- tick: the scan, the sounds

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            if (lastStrength > 0 || lair || !cur.water.isEmpty()) {
                reset(true);
            }
            return;
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset(true);
        }
        if (mc.isPaused()) {
            return;
        }
        boolean in = BiomeAura.in("floresta_negra_ar", b -> b.is(ModWorldgen.FLORESTA_NEGRA));
        strength = Mth.clamp(strength + (in ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            if (lastStrength > 0) {
                reset(false);
            }
            return;
        }
        ClientLevel level = mc.level;
        if (level.getGameTime() - canopyAt > 200) {
            canopyAt = level.getGameTime();
            CANOPY.clear();
        }
        Vec3 p = mc.player.position();
        int rows = firstPass ? 8 : 1;
        for (int i = 0; i < rows; i++) {
            scanStep(level, p);
        }
        sounds(mc, level, p);
    }

    private static void scanStep(ClientLevel level, Vec3 p) {
        if (scanRow > SCAN) {
            // the eyes and fungi keep their timers from pass to pass
            for (var e : next.eyes.long2ObjectEntrySet()) {
                Eye old = cur.eyes.get(e.getLongKey());
                if (old != null) {
                    e.setValue(old);
                }
            }
            for (var e : next.fungi.long2ObjectEntrySet()) {
                Fungus old = cur.fungi.get(e.getLongKey());
                if (old != null) {
                    e.setValue(old);
                }
            }
            Scan done = next;
            next = cur;
            cur = done;
            next.water.clear();
            next.wet.clear();
            next.fungi.clear();
            next.eyes.clear();
            next.maw = next.flesh = 0;
            next.mawX = next.mawY = next.mawZ = next.fleshX = next.fleshZ = 0;
            firstPass = false;
            afterPass(level);
            scanRow = -SCAN - 1;
        }
        if (scanRow == -SCAN - 1) {
            scanCx = Mth.floor(p.x);
            scanCz = Mth.floor(p.z);
            scanRow = -SCAN;
        }
        int x = scanCx + scanRow++;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Block eye = JujutsuBlocks.CURSED_EYE.get(), fungus = JujutsuBlocks.CURSED_FUNGUS.get(), maw = JujutsuBlocks.MAW.get(),
                flesh = JujutsuBlocks.CURSED_FLESH.get();
        for (int z = scanCz - SCAN; z <= scanCz + SCAN; z++) {
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int g = top(level, x, z);
            if (g < -900) {
                continue;
            }
            if (level.getFluidState(pos.set(x, g, z)).is(FluidTags.WATER) && level.getBlockState(pos.set(x, g + 1, z)).isAir()) {
                next.water.add(new int[] {x, g, z});
                // the air near water is wetter (more mist): marks the 2-block cells round it
                for (int a = -2; a <= 2; a++) {
                    for (int b = -2; b <= 2; b++) {
                        next.wet.add(key((x >> 1) + a, (z >> 1) + b));
                    }
                }
            }
            int surf = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = g - 1; y < Math.min(surf, g + 60); y++) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                if (s.isAir()) {
                    continue;
                }
                Block b = s.getBlock();
                if (b == fungus) {
                    Fungus f = new Fungus();
                    f.x = x + 0.5;
                    f.y = y + 0.5;
                    f.z = z + 0.5;
                    next.fungi.put(key3(x, y, z), f);
                } else if (b == eye) {
                    // the face that looks at the air is the eye's
                    int[][] faces = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] f : faces) {
                        if (level.getBlockState(pos.set(x + f[0], y, z + f[1])).isAir()) {
                            Eye e = new Eye();
                            e.x = x;
                            e.y = y;
                            e.z = z;
                            e.nx = f[0];
                            e.nz = f[1];
                            next.eyes.put(key3(x, y, z), e);
                            break;
                        }
                    }
                } else if (b == maw) {
                    next.maw++;
                    next.mawX += x + 0.5;
                    next.mawY += y;
                    next.mawZ += z + 0.5;
                } else if (b == flesh) {
                    next.flesh++;
                    next.fleshX += x + 0.5;
                    next.fleshZ += z + 0.5;
                }
            }
        }
    }

    // After each pass: the canopy's holes (shafts) and the Matriz's mouth.
    private static void afterPass(ClientLevel level) {
        List<double[]> g = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        int[][] ring = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, -3}, {3, -3}, {-3, 3}};
        for (int x = scanCx - SCAN + 4; x < scanCx + SCAN - 4; x += 2) {
            for (int z = scanCz - SCAN + 4; z < scanCz + SCAN - 4; z += 2) {
                if (canopyAt(level, x, z) != NONE || top(level, x, z) < -900) {
                    continue;
                }
                int around = 0;
                for (int[] d : ring) {
                    if (canopyAt(level, x + d[0], z + d[1]) != NONE) {
                        around++;
                    }
                }
                if (around < 6) {
                    continue;
                }
                long k = key(Math.floorDiv(x, 6), Math.floorDiv(z, 6));
                if (!seen.add(k)) {
                    continue;
                }
                // the canopy's height round it = where the shaft starts
                double ch = 0;
                int n = 0;
                for (int a = 0; a < 4; a++) {
                    int c = canopyAt(level, x + ring[a][0], z + ring[a][1]);
                    if (c != NONE) {
                        ch += c;
                        n++;
                    }
                }
                g.add(new double[] {x + 0.5, z + 0.5, top(level, x, z) + 1, ch / n + 4, 1.2 + rnd() * 1.4, rnd() * 6.28});
            }
        }
        gaps = g;
        for (Mote m : MOTES) {
            m.g = g.isEmpty() ? 0 : (int) (rnd() * g.size());
        }
        Scan s = cur;
        if (s.maw >= 20 && s.flesh >= 10) {
            double tx = s.mawX / s.maw, tz = s.mawZ / s.maw, fx = s.fleshX / s.flesh, fz = s.fleshZ / s.flesh;
            double dx = fx - tx, dz = fz - tz, l = Math.hypot(dx, dz);
            if (l > 0.3) {
                mouthDx = dx / l;
                mouthDz = dz / l;
                mouthX = tx + mouthDx * 8;
                mouthZ = tz + mouthDz * 8;
                int gy = top(level, mouthX, mouthZ);
                mouthY = gy < -900 ? s.mawY / s.maw - 6 : gy + 1;
                lair = true;
            }
        } else if (s.maw == 0) {
            lair = false;
        }
    }

    private static void reset(boolean all) {
        strength = 0;
        firstPass = true;
        scanRow = -SCAN - 1;
        next = new Scan();
        for (Leaf l : LEAVES) {
            l.y = -999;
        }
        DRIPS.clear();
        RINGS.clear();
        SPORES.clear();
        BREATH.clear();
        WAKE.clear();
        WATCH.clear();
        PENDING.clear();
        mistX = Integer.MIN_VALUE;
        if (drone != null) {
            drone.end();
            drone = null;
        }
        if (all) {
            cur = new Scan();
            gaps = new ArrayList<>();
            lair = false;
            CANOPY.clear();
        }
    }

    // ---------------------------------------------------------------- mist

    private static void measureMist(ClientLevel level, Vec3 f) {
        int N = CELLS;
        mistX = Mth.floor(f.x) - N / 2;
        mistZ = Mth.floor(f.z) - N / 2;
        int[][] H = new int[N + 1][N + 1];
        for (int i = 0; i <= N; i++) {
            for (int j = 0; j <= N; j++) {
                H[i][j] = top(level, mistX + i, mistZ + j);
            }
        }
        int W = 8;
        for (int i = 0; i <= N; i++) {
            for (int j = 0; j <= N; j++) {
                int y = H[i][j];
                if (y < -900) {
                    MIST[i][j] = null;
                    continue;
                }
                double s = 0;
                int c = 0;
                for (int a = -W; a <= W; a += 2) {
                    for (int b = -W; b <= W; b += 2) {
                        int ii = i + a, jj = j + b;
                        if (ii >= 0 && ii <= N && jj >= 0 && jj <= N && H[ii][jj] > -900) {
                            s += H[ii][jj];
                            c++;
                        }
                    }
                }
                int x = mistX + i, z = mistZ + j;
                double wet = cur.wet.contains(key(x >> 1, z >> 1)) ? 1 : 0;
                double low = clamp01((s / c - y + 1) / 3);
                Mist m = MIST[i][j] == null ? new Mist() : MIST[i][j];
                m.y = y + 1;
                m.k = 0.35 + 0.45 * low + 0.2 * wet;
                m.under = canopyAt(level, x, z) != NONE ? 1 : 0.6;
                MIST[i][j] = m;
            }
        }
        mistAt = System.currentTimeMillis();
        mistFx = f.x;
        mistFz = f.z;
    }

    // how much the mist was pushed away here: near the player none; his trail closes again in ~6 s
    private static double parted(Vec3 c, double x, double z) {
        double k = clamp01((Math.hypot(x - c.x, z - c.z) - 0.8) / 2.6);
        for (Wake p : WAKE) {
            double d = Math.hypot(x - p.x, z - p.z), r = 1.4 + p.age * 0.25;
            if (d < r) {
                k *= 1 - (1 - d / r) * clamp01(1 - p.age / 6) * 0.85;
            }
        }
        return k;
    }

    // ---------------------------------------------------------------- the Matriz's breath

    private static void exhale(ClientLevel level, Vec3 c) {
        for (int i = 0; i < 46; i++) {
            Puff p = new Puff();
            for (int k = 0; k < 9; k++) {
                p.r[k] = 0.6 + rnd() * 0.5;
            }
            double sp = 3 + rnd() * 3, spread = (rnd() - 0.5) * 0.9, cs = Math.cos(spread), sn = Math.sin(spread);
            p.x = mouthX + (rnd() - 0.5) * 2;
            p.y = mouthY + 2 + rnd() * 3;
            p.z = mouthZ + (rnd() - 0.5) * 2;
            p.vx = (mouthDx * cs - mouthDz * sn) * sp;
            p.vy = 0.5 + rnd();
            p.vz = (mouthDx * sn + mouthDz * cs) * sp;
            p.age = -i * 0.04;
            p.life = 9 + rnd() * 5;
            p.rot = rnd() * 6.28;
            p.s0 = 0.8 + rnd() * 0.8;
            BREATH.add(p);
        }
        nextBreath = 14 + rnd() * 10;
        // heard far (deep), from the mouth's side, arriving at the speed of sound
        double d = Math.hypot(c.x - mouthX, c.z - mouthZ);
        Pending s = new Pending();
        s.at = clockS + d / 343;
        double dd = Math.max(1, d);
        s.x = c.x + (mouthX - c.x) / dd * 4;
        s.y = c.y + 1;
        s.z = c.z + (mouthZ - c.z) / dd * 4;
        s.vol = (float) Math.max(0.15, 1 - d / 120);
        PENDING.add(s);
    }

    // ---------------------------------------------------------------- leaves

    private static void spawnLeaf(ClientLevel level, Leaf l, Vec3 f, boolean any) {
        double R = 24;
        for (int k = 0; k < 8; k++) {
            double x = f.x + (rnd() - 0.5) * 2 * R, z = f.z + (rnd() - 0.5) * 2 * R;
            int c = canopyAt(level, x, z);
            if (c == NONE) {
                continue;
            }
            int g = top(level, x, z) + 1;
            l.x = x;
            l.z = z;
            l.y = any ? g + rnd() * (c - g) : c - 0.2;
            l.g = g;
            l.ph = rnd() * 6.28;
            l.amp = 0.4 + rnd() * 0.6;
            l.ax = rnd() - 0.5;
            l.az = rnd() - 0.5;
            l.s = 0.09 + rnd() * 0.07;
            l.tone = rnd();
            l.rest = 0;
            l.violet = rnd() < 0.15;
            return;
        }
        l.y = -999;
    }

    // ---------------------------------------------------------------- the step (each frame, like the preview)

    private static void step(ClientLevel level, Vec3 c, double dt, double t) {
        if (mistX == Integer.MIN_VALUE || System.currentTimeMillis() - mistAt > 4000 || Math.hypot(c.x - mistFx, c.z - mistFz) > 6) {
            measureMist(level, c);
        }
        // the player's trail in the mist
        if (Math.hypot(c.x - lastCamX, c.z - lastCamZ) > 0.7) {
            Wake w = new Wake();
            w.x = c.x;
            w.z = c.z;
            WAKE.add(w);
            lastCamX = c.x;
            lastCamZ = c.z;
        }
        for (Wake w : WAKE) {
            w.age += dt;
        }
        WAKE.removeIf(w -> w.age >= 6);
        // drops: from the leaves over the pools near (and some on the ground)
        dripAcc += dt * 7;
        List<int[]> water = cur.water;
        while (dripAcc > 1) {
            dripAcc -= 1;
            double R = 26, x, z, gy;
            boolean pool = rnd() < 0.75 && !water.isEmpty();
            if (pool) {
                int[] wv = water.get((int) (rnd() * water.size()));
                if (Math.abs(wv[0] - c.x) > R || Math.abs(wv[2] - c.z) > R) {
                    continue;
                }
                x = wv[0] + rnd();
                z = wv[2] + rnd();
                gy = wv[1] + 0.88;
            } else {
                x = c.x + (rnd() - 0.5) * 2 * R;
                z = c.z + (rnd() - 0.5) * 2 * R;
                gy = top(level, x, z) + 1;
            }
            int cy = canopyAt(level, x, z);
            if (cy == NONE || gy < -900) {
                continue;
            }
            Drip d = new Drip();
            d.x = x;
            d.y = cy - 0.05;
            d.z = z;
            d.gy = gy;
            d.pool = pool;
            DRIPS.add(d);
        }
        for (Iterator<Drip> it = DRIPS.iterator(); it.hasNext();) {
            Drip d = it.next();
            d.vy -= G * dt;
            d.y += d.vy * dt;
            if (d.y <= d.gy) {
                it.remove();
                if (d.pool) {
                    Ring r = new Ring();
                    r.x = d.x;
                    r.y = d.gy + 0.01;
                    r.z = d.z;
                    r.r = 0.5 + rnd() * 0.4;
                    RINGS.add(r);
                    double dist = Math.sqrt((d.x - c.x) * (d.x - c.x) + (d.gy - c.y) * (d.gy - c.y) + (d.z - c.z) * (d.z - c.z));
                    if (dist < 22) {
                        level.playLocalSound(d.x, d.gy, d.z, ModSounds.FLORESTA_GOTA.get(), SoundSource.AMBIENT,
                                (float) (Math.pow(1 - dist / 22, 2) * 0.8) * strength, 0.9f + (float) rnd() * 0.25f, false);
                    }
                }
            }
        }
        for (Ring r : RINGS) {
            r.age += dt;
        }
        RINGS.removeIf(r -> r.age >= 1.6);
        // fungi: breathe their light; now and then let out spores
        for (Fungus g : cur.fungi.values()) {
            g.next -= dt;
            if (g.next < 0 && Math.hypot(g.x - c.x, g.z - c.z) < 40) {
                for (int i = 0; i < 24 && SPORES.size() < 1500; i++) {
                    Spore s = new Spore();
                    s.x = g.x + (rnd() - 0.5) * 0.4;
                    s.y = g.y + 0.2;
                    s.z = g.z + (rnd() - 0.5) * 0.4;
                    s.vx = (rnd() - 0.5) * 0.5;
                    s.vy = 0.4 + rnd() * 0.6;
                    s.vz = (rnd() - 0.5) * 0.5;
                    s.life = 7 + rnd() * 6;
                    s.ph = rnd() * 6.28;
                    SPORES.add(s);
                }
                g.next = 12 + rnd() * 30;
            }
        }
        for (Spore s : SPORES) {
            // a spore is light but heavier than air: the first push dies in the drag and it sinks very slowly
            s.age += dt;
            s.vx *= 1 - dt * 1.5;
            s.vz *= 1 - dt * 1.5;
            s.vy += (-0.06 - s.vy) * dt * 1.2;
            s.x += (s.vx + Math.sin(s.age * 0.7 + s.ph) * 0.08) * dt;
            s.y += s.vy * dt;
            s.z += (s.vz + Math.cos(s.age * 0.6 + s.ph) * 0.08) * dt;
        }
        SPORES.removeIf(s -> s.age >= s.life);
        // the bark's eyes: blink; near, the pupil turns to you; far, they look about
        for (Eye e : cur.eyes.values()) {
            e.next -= dt;
            if (e.next < 0) {
                e.blink = 0.001;
                e.next = 2.5 + rnd() * 7;
            }
            if (e.blink > 0) {
                e.blink += dt;
                if (e.blink > 0.22) {
                    e.blink = 0;
                }
            }
            double ex = e.x + 0.5 + e.nx * 0.5, ey = e.y + 0.5, ez = e.z + 0.5 + e.nz * 0.5;
            double d = Math.sqrt((c.x - ex) * (c.x - ex) + (c.y - ey) * (c.y - ey) + (c.z - ez) * (c.z - ez)), tx, ty;
            if (d < 18) {
                // the way to the player's eye, in the face's plane: (side, up)
                double lx = -e.nz, lz = e.nx, vx = c.x - ex, vy = c.y - ey, vz = c.z - ez;
                tx = Mth.clamp((vx * lx + vz * lz) / Math.max(1, d) * 2.2, -1, 1);
                ty = Mth.clamp(vy / Math.max(1, d) * 2.2, -1, 1);
            } else {
                e.look += dt * (rnd() - 0.5) * 2;
                tx = Math.sin(e.look) * 0.7;
                ty = Math.cos(e.look * 0.7) * 0.3;
            }
            // the eye turns fast (a saccade) and stops: follows the target in a short step
            e.px += (tx - e.px) * Math.min(1, dt * 9);
            e.py += (ty - e.py) * Math.min(1, dt * 9);
        }
        stepWatch(level, c, dt);
        // the tree's breath
        if (lair && Math.hypot(c.x - mouthX, c.z - mouthZ) < 140) {
            nextBreath -= dt;
            if (nextBreath < 0) {
                exhale(level, c);
            }
        }
        for (Puff p : BREATH) {
            p.age += dt;
            if (p.age < 0) {
                continue;
            }
            // a current of heavy air: sinks to the ground and runs along it, slowing
            double gy = top(level, p.x, p.z) + 1.2;
            if (gy < -900) {
                gy = p.y;
            }
            p.vy += (p.y > gy ? -1.4 : (gy - p.y) * 2) * dt;
            p.vy *= 1 - dt * 0.8;
            p.vx *= 1 - dt * 0.28;
            p.vz *= 1 - dt * 0.28;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.rot += dt * 0.15;
            if (p.y < gy - 0.4) {
                p.y = gy - 0.4;
            }
        }
        BREATH.removeIf(p -> p.age >= p.life);
        // leaves: a pendulum fall, the swing stronger than the fall
        for (Leaf l : LEAVES) {
            if (l.y < -900) {
                spawnLeaf(level, l, c, false);
                continue;
            }
            if (l.rest > 0) {
                l.rest += dt;
                if (l.rest > 4) {
                    spawnLeaf(level, l, c, false);
                }
                continue;
            }
            l.ph += dt * 2.4;
            double sw = Math.sin(l.ph) * l.amp;
            l.x += l.ax * sw * dt * 2;
            l.z += l.az * sw * dt * 2;
            l.y -= dt * (0.5 + 0.6 * Math.abs(Math.cos(l.ph)));
            if (l.y <= l.g + 0.02) {
                l.y = l.g + 0.02;
                l.rest = 0.001;
            }
            if (Math.abs(l.x - c.x) > 26 || Math.abs(l.z - c.z) > 26) {
                spawnLeaf(level, l, c, false);
            }
        }
        // dust in the shafts: floats (slow Brownian motion)
        for (Mote m : MOTES) {
            m.ph += dt * 0.3;
            m.h += dt * 0.004 * Math.sin(m.ph);
            m.u += dt * 0.01 * Math.cos(m.ph * 1.3);
            m.v += dt * 0.01 * Math.sin(m.ph * 0.9);
            m.h = (m.h + 1) % 1;
            m.u = (m.u + 1) % 1;
            m.v = (m.v + 1) % 1;
        }
    }

    // ---------------------------------------------------------------- eyes in the dark (night)

    // the curse only opens its eyes where it sees you (a line free of blocks to the camera): that's why you see the glow
    private static boolean clearLine(ClientLevel level, Vec3 c, double x, double y, double z) {
        double L = Math.sqrt((x - c.x) * (x - c.x) + (y - c.y) * (y - c.y) + (z - c.z) * (z - c.z));
        int n = (int) Math.ceil(L / 0.5);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 1; i < n - 1; i++) {
            double k = (double) i / n;
            if (!level.getBlockState(pos.set(Mth.floor(c.x + (x - c.x) * k), Mth.floor(c.y + (y - c.y) * k), Mth.floor(c.z + (z - c.z) * k))).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static void spawnWatch(ClientLevel level, Watch o, Vec3 c, boolean first) {
        double r0 = 9, r1 = 32, yaw = Math.atan2(lookZ, lookX);
        // 7 in 10 are born in front (within about 70 degrees), the rest all round: turning, there are eyes behind too
        boolean front = rnd() < 0.7;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int tr = 0; tr < 12; tr++) {
            double ang = front ? yaw + (rnd() - 0.5) * 2.4 : rnd() * Math.PI * 2, d = r0 + Math.sqrt(rnd()) * (r1 - r0);
            double x = c.x + Math.cos(ang) * d, z = c.z + Math.sin(ang) * d;
            int g = top(level, x, z);
            if (g < -900 || isWater(level, Mth.floor(x), g, Mth.floor(z))) {
                continue;
            }
            boolean big = rnd() < 0.35;
            double h = big ? 1.3 + rnd() * 1.1 : 0.35 + rnd() * 0.55, y = g + 1 + h;
            if (!level.getBlockState(pos.set(Mth.floor(x), Mth.floor(y), Mth.floor(z))).isAir()) {
                continue;
            }
            if (tr < 10 && !clearLine(level, c, x, y, z)) {
                continue;
            }
            o.x = x;
            o.z = z;
            o.h = h;
            o.y = y;
            o.big = big;
            o.sp = big ? 0.34 + rnd() * 0.16 : 0.18 + rnd() * 0.08;
            o.r = big ? 0.085 + rnd() * 0.025 : 0.055 + rnd() * 0.012;
            o.red = rnd() < 0.55;
            o.age = first ? -rnd() * 6 : -rnd() * 2.5;
            o.life = 5 + rnd() * 10;
            o.out = 0;
            o.th = (rnd() - 0.5) * 0.8;
            o.tth = o.th;
            o.nextTurn = 1 + rnd() * 4;
            o.blink = 0;
            o.nextBlink = 1 + rnd() * 4;
            o.vx = o.vz = 0;
            o.walk = 0;
            o.nextWalk = 2 + rnd() * 5;
            o.dead = false;
            return;
        }
        // tries again next frame
        o.dead = true;
        o.age = -1;
    }

    private static void stepWatch(ClientLevel level, Vec3 c, double dt) {
        if (night < 0.5f) {
            WATCH.clear();
            return;
        }
        double r1 = 32;
        int spawned = 0;
        while (WATCH.size() < 46) {
            Watch o = new Watch();
            WATCH.add(o);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Watch o : WATCH) {
            if (o.dead) {
                // (a few a frame: each try looks along a line of blocks)
                if (spawned++ < 6) {
                    spawnWatch(level, o, c, true);
                }
                continue;
            }
            o.age += dt;
            if (o.age < 0) {
                continue;
            }
            double d = Math.hypot(o.x - c.x, o.z - c.z);
            // came near: the curse flees (shuts its eyes fast and goes)
            if (o.out == 0 && (d < 7 || o.age > o.life || d > r1 + 6)) {
                o.out = 0.001;
            }
            if (o.out > 0) {
                o.out += dt * (d < 7 ? 6 : 1.6);
                if (o.out >= 1) {
                    o.dead = true;
                    continue;
                }
            }
            // a blink: both lids together, 0.16 s
            o.nextBlink -= dt;
            if (o.nextBlink < 0) {
                o.blink = 0.001;
                o.nextBlink = 1.5 + rnd() * 4.5;
            }
            if (o.blink > 0) {
                o.blink += dt;
                if (o.blink > 0.16) {
                    o.blink = 0;
                }
            }
            // turns its head now and then (sideways the pair narrows and the glow drops; turned too far, it goes dark)
            o.nextTurn -= dt;
            if (o.nextTurn < 0) {
                o.tth = (rnd() - 0.5) * (rnd() < 0.2 ? 3.4 : 1.0);
                o.nextTurn = 1.5 + rnd() * 4;
            }
            o.th += (o.tth - o.th) * Math.min(1, dt * 5);
            // lurks: walks a little to one side and stops
            o.nextWalk -= dt;
            if (o.nextWalk < 0) {
                double a = rnd() * Math.PI * 2, v = o.big ? 0.6 : 0.9;
                o.vx = Math.cos(a) * v;
                o.vz = Math.sin(a) * v;
                o.walk = 0.6 + rnd() * 1.4;
                o.nextWalk = 3 + rnd() * 6;
            }
            if (o.walk > 0) {
                o.walk -= dt;
                double nx = o.x + o.vx * dt, nz = o.z + o.vz * dt;
                int g = top(level, nx, nz);
                double ny = g + 1 + o.h;
                if (g > -900 && level.getBlockState(pos.set(Mth.floor(nx), Mth.floor(ny), Mth.floor(nz))).isAir()) {
                    o.x = nx;
                    o.z = nz;
                    o.y += (ny - o.y) * Math.min(1, dt * 6);
                } else {
                    o.walk = 0;
                }
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (fade <= 0.001f) {
            lastNanos = 0;
            return;
        }
        long now = System.nanoTime();
        double dt = lastNanos == 0 || mc.isPaused() ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        double t = (level.getGameTime() % 240000L + partial) / 20.0;
        clockS += dt;
        night = Mth.clamp((0.8f - level.getSkyDarken(partial)) / 0.6f, 0, 1);
        float a = level.getSunAngle(partial);
        SUN[0] = -Math.sin(a);
        SUN[1] = Math.cos(a);
        SUN[2] = 0;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f look = camera.getLookVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        if (dt > 0) {
            step(level, cam, dt, t);
        }
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean n = night > 0.5f;
        VertexConsumer area = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        renderMist(area, last, cam, t, fade);
        renderBreath(area, last, cam, fade);
        renderLeaves(area, last, cam, fade);
        renderDrips(area, last, cam, fade);
        renderRings(area, last, cam, fade);
        renderEyes(area, last, cam, fade);
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
        if (!n && SUN[1] > 0.1) {
            renderShafts(glow, last, cam, t, fade * (float) clamp01((SUN[1] - 0.1) * 4) * (level.isRaining() ? 0.3f : 1f));
        }
        renderFungi(glow, last, cam, t, fade);
        renderSpores(glow, last, cam, fade);
        if (n) {
            renderWatch(glow, last, cam, fade * (float) clamp01((night - 0.5) * 4));
        }
        buffers.endBatch(FxRenderTypes.GLOW_FLAT);
    }

    private static void v(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        out.addVertex(last, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double[] p0, double[] p1, double[] p2, double[] p3, float r, float g, float b,
            float a0, float a1, float a2, float a3) {
        v(out, last, cam, p0[0], p0[1], p0[2], r, g, b, a0);
        v(out, last, cam, p1[0], p1[1], p1[2], r, g, b, a1);
        v(out, last, cam, p2[0], p2[1], p2[2], r, g, b, a2);
        v(out, last, cam, p3[0], p3[1], p3[2], r, g, b, a3);
    }

    private static final double[] BR = new double[3], BU = new double[3];

    // A flat piece's axes facing the camera's POSITION (not where it looks): stays in the world, not stuck on the screen.
    private static void face(Vec3 cam, double x, double y, double z) {
        double fx = x - cam.x, fy = y - cam.y, fz = z - cam.z, l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (l < 1e-4) {
            fx = lookX;
            fy = lookY;
            fz = lookZ;
            l = Math.max(1e-4, Math.sqrt(fx * fx + fy * fy + fz * fz));
        }
        fx /= l;
        fy /= l;
        fz /= l;
        double rx = -fz, rz = fx, rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-3) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        BR[0] = rx;
        BR[1] = 0;
        BR[2] = rz;
        BU[0] = -rz * fy;
        BU[1] = rz * fx - rx * fz;
        BU[2] = rx * fy;
    }

    // an ellipse facing the camera, bright in the middle and gone (edge) at the rim
    private static void ellipse(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double sx, double sy, int seg, float r,
            float g, float b, float a, float edge) {
        if (a <= 0.002f) {
            return;
        }
        face(cam, x, y, z);
        double px = x + BR[0] * sx, py = y + BR[1] * sx, pz = z + BR[2] * sx;
        for (int q = 1; q <= seg; q++) {
            double ang = q / (double) seg * Math.PI * 2, cx = Math.cos(ang) * sx, cy = Math.sin(ang) * sy;
            double qx = x + BR[0] * cx + BU[0] * cy, qy = y + BR[1] * cx + BU[1] * cy, qz = z + BR[2] * cx + BU[2] * cy;
            v(out, last, cam, x, y, z, r, g, b, a);
            v(out, last, cam, px, py, pz, r, g, b, a * edge);
            v(out, last, cam, qx, qy, qz, r, g, b, a * edge);
            v(out, last, cam, qx, qy, qz, r, g, b, a * edge);
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    // the mist: soft flat discs lying on the ground (no edge, no seam or triangle showing), in 3 heights
    private static final double[][] LAYERS = {{0.3, 0.42, 0.06, 2.6}, {1.0, 0.26, 0.1, 3.2}, {2.0, 0.13, 0.15, 3.8}};

    private static void renderMist(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        if (mistX == Integer.MIN_VALUE) {
            return;
        }
        int N = CELLS;
        double half = N / 2.0;
        float r = Mth.lerp(night, 0.42f, 0.15f), g = Mth.lerp(night, 0.38f, 0.12f), b = Mth.lerp(night, 0.5f, 0.22f);
        for (int L = 0; L < LAYERS.length; L++) {
            double hgt = LAYERS[L][0], amp = LAYERS[L][1], sp = LAYERS[L][2], rad = LAYERS[L][3];
            for (int i = 0; i <= N; i += 2) {
                for (int j = 0; j <= N; j += 2) {
                    Mist gp = MIST[i][j];
                    if (gp == null) {
                        continue;
                    }
                    // each disc in a slightly drawn place (fixed for the cell), so it doesn't make a grid
                    double jx = (hsh(i + mistX, j + mistZ + L * 7) - 0.5) * 2, jz = (hsh(j + mistZ, i + mistX - L * 7) - 0.5) * 2;
                    double x = mistX + i + jx, z = mistZ + j + jz, y = gp.y + hgt;
                    double m = vnoise(x * 0.09 + t * sp, z * 0.09 - t * sp * 0.6) * 0.6 + vnoise(x * 0.23 - t * sp * 1.4, z * 0.23 + t * sp * 0.4) * 0.4;
                    double k = clamp01((m - 0.25) / 0.5);
                    k = k * k * (3 - 2 * k);
                    double dd = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
                    double edge = clamp01((half - Math.hypot(x - (mistX + half), z - (mistZ + half))) / (half * 0.3)) * clamp01((dd - 0.8) / 2.5);
                    double a = amp * gp.k * gp.under * (0.25 + 0.75 * k) * edge * parted(cam, x, z) * fade;
                    if (a < 0.004) {
                        continue;
                    }
                    double px = x + rad, pz = z;
                    for (int q = 1; q <= 10; q++) {
                        double ang = q / 10.0 * Math.PI * 2, nx = x + Math.cos(ang) * rad, nz = z + Math.sin(ang) * rad;
                        v(out, last, cam, x, y, z, r, g, b, (float) a);
                        v(out, last, cam, px, y, pz, r, g, b, 0);
                        v(out, last, cam, nx, y, nz, r, g, b, 0);
                        v(out, last, cam, nx, y, nz, r, g, b, 0);
                        px = nx;
                        pz = nz;
                    }
                }
            }
        }
    }

    // a shaft: an upright band turned to the eye round its own axis (slanted by the sun), its edges and ends fading
    private static final double[][] SH_K = {{0, 0.15, 0, 1}, {0.15, 0.75, 1, 1}, {0.75, 1, 1, 0}};
    private static final double[][] SH_S = {{-1, -0.35, 0, 1}, {-0.35, 0.35, 1, 1}, {0.35, 1, 1, 0}};

    private static void renderShafts(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        if (fade <= 0.002f) {
            return;
        }
        double sy = Math.max(0.3, SUN[1]);
        for (double[] gp : gaps) {
            double gx = gp[0], gz = gp[1], y0 = gp[2], y1 = gp[3], w = gp[4], ph = gp[5];
            double kk = (y1 - y0) / sy, tx = gx + SUN[0] * kk, tz = gz + SUN[2] * kk;
            double ax = tx - gx, ay = y1 - y0, az = tz - gz;
            double mx = (tx + gx) / 2, my = (y1 + y0) / 2, mz = (tz + gz) / 2;
            double d = Math.hypot(cam.x - mx, cam.z - mz);
            if (d > 70) {
                continue;
            }
            double sx = (cam.y - my) * az - (cam.z - mz) * ay, syy = (cam.z - mz) * ax - (cam.x - mx) * az, sz = (cam.x - mx) * ay - (cam.y - my) * ax;
            double sl = Math.sqrt(sx * sx + syy * syy + sz * sz);
            if (sl < 1e-6) {
                continue;
            }
            sx /= sl;
            syy /= sl;
            sz /= sl;
            float a = (float) (0.12 * (0.8 + 0.2 * Math.sin(t * 0.2 + ph)) * clamp01((d - 2) / 6) * clamp01((70 - d) / 15)) * fade;
            for (double[] K : SH_K) {
                for (double[] S : SH_S) {
                    double[] p0 = {gx + ax * K[0] + sx * w * S[0], y0 + ay * K[0] + syy * w * S[0], gz + az * K[0] + sz * w * S[0]};
                    double[] p1 = {gx + ax * K[0] + sx * w * S[1], y0 + ay * K[0] + syy * w * S[1], gz + az * K[0] + sz * w * S[1]};
                    double[] p2 = {gx + ax * K[1] + sx * w * S[1], y0 + ay * K[1] + syy * w * S[1], gz + az * K[1] + sz * w * S[1]};
                    double[] p3 = {gx + ax * K[1] + sx * w * S[0], y0 + ay * K[1] + syy * w * S[0], gz + az * K[1] + sz * w * S[0]};
                    quad(out, last, cam, p0, p1, p2, p3, 0.75f, 0.7f, 0.85f, (float) (a * S[2] * K[2]), (float) (a * S[3] * K[2]), (float) (a * S[3] * K[3]),
                            (float) (a * S[2] * K[3]));
                }
            }
        }
        // the dust: only inside a shaft, shining with it
        if (gaps.isEmpty()) {
            return;
        }
        for (Mote m : MOTES) {
            if (m.g >= gaps.size()) {
                continue;
            }
            double[] gp = gaps.get(m.g);
            double kk = (gp[3] - gp[2]) / sy, tx = gp[0] + SUN[0] * kk, tz = gp[1] + SUN[2] * kk;
            double k = m.h, rr = gp[4] * 0.8;
            double x = gp[0] + (tx - gp[0]) * k + (m.u - 0.5) * 2 * rr, y = gp[2] + (gp[3] - gp[2]) * k, z = gp[1] + (tz - gp[1]) * k + (m.v - 0.5) * 2 * rr;
            double d = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
            if (d > 30) {
                continue;
            }
            float a = (float) (0.5 * Math.sin(k * Math.PI) * clamp01((d - 0.6) / 1.5) * (0.6 + 0.4 * Math.sin(m.ph * 7))) * fade;
            double s = 0.025 + d * 0.0015;
            ellipse(out, last, cam, x, y, z, s, s, 6, 0.9f, 0.85f, 1f, a, 0);
        }
    }

    // the miasma: a soft lumpy cloud (9 radii), flattening along the ground
    private static void renderBreath(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        if (BREATH.isEmpty()) {
            return;
        }
        List<Puff> list = new ArrayList<>();
        for (Puff p : BREATH) {
            if (p.age > 0) {
                list.add(p);
            }
        }
        list.sort((p, q) -> Double.compare(d2(q, cam), d2(p, cam)));
        float r = Mth.lerp(night, 0.26f, 0.2f), g = Mth.lerp(night, 0.14f, 0.08f), b = Mth.lerp(night, 0.34f, 0.3f);
        for (Puff p : list) {
            double k = p.age / p.life, d = Math.sqrt(d2(p, cam));
            float a = (float) (clamp01(p.age / 0.6) * (1 - k) * (1 - k) * 0.38 * clamp01((d - 2) / 5)) * fade;
            if (a < 0.004f) {
                continue;
            }
            double s = p.s0 + p.age * 0.55, flat = 0.55 + 0.45 * clamp01(1 - p.age / 4);
            face(cam, p.x, p.y, p.z);
            double[] prev = null;
            for (int q = 0; q <= 9; q++) {
                double ang = p.rot + (q % 9) / 9.0 * Math.PI * 2, kk = p.r[q % 9] * s;
                double ox = Math.cos(ang) * kk, oy = Math.sin(ang) * kk * flat;
                double[] pt = {p.x + BR[0] * ox + BU[0] * oy, p.y + BR[1] * ox + BU[1] * oy, p.z + BR[2] * ox + BU[2] * oy};
                if (prev != null) {
                    v(out, last, cam, p.x, p.y, p.z, r, g, b, a);
                    v(out, last, cam, prev[0], prev[1], prev[2], r, g, b, 0);
                    v(out, last, cam, pt[0], pt[1], pt[2], r, g, b, 0);
                    v(out, last, cam, pt[0], pt[1], pt[2], r, g, b, 0);
                }
                prev = pt;
            }
        }
    }

    private static double d2(Puff p, Vec3 c) {
        return (p.x - c.x) * (p.x - c.x) + (p.y - c.y) * (p.y - c.y) + (p.z - c.z) * (p.z - c.z);
    }

    private static void renderLeaves(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        float k = Mth.lerp(night, 1f, 0.6f);
        for (Leaf l : LEAVES) {
            if (l.y < -900) {
                continue;
            }
            double d = Math.sqrt((l.x - cam.x) * (l.x - cam.x) + (l.y - cam.y) * (l.y - cam.y) + (l.z - cam.z) * (l.z - cam.z));
            float a = (float) ((l.rest > 0 ? clamp01(1 - l.rest / 4) : 1) * clamp01((d - 0.5) / 1.5) * clamp01((26 - d) / 8) * 0.95) * fade;
            if (a < 0.01f) {
                continue;
            }
            // the leaf tilts toward its swing
            double tilt = l.rest > 0 ? 0 : Math.cos(l.ph) * 0.9, s = l.s;
            double ux = l.ax * s * 1.6, uz = l.az * s * 1.6, uy = tilt * s, vx = -l.az * s, vz = l.ax * s;
            double tn = 0.08 + l.tone * 0.07;
            float r = (float) (l.violet ? tn * 1.6 : tn * 1.1) * k, g = (float) (l.violet ? tn * 0.8 : tn * 1.15) * k, b = (float) (l.violet ? tn * 1.9 : tn * 0.95) * k;
            v(out, last, cam, l.x + ux, l.y + uy, l.z + uz, r, g, b, a);
            v(out, last, cam, l.x + vx, l.y, l.z + vz, r, g, b, a);
            v(out, last, cam, l.x - ux, l.y - uy, l.z - uz, r, g, b, a);
            v(out, last, cam, l.x - vx, l.y, l.z - vz, r, g, b, a);
        }
    }

    // a drop: a small bead, a little stretched in the fall (no streak)
    private static void renderDrips(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        float r = Mth.lerp(night, 0.75f, 0.45f), g = Mth.lerp(night, 0.72f, 0.42f), b = Mth.lerp(night, 0.85f, 0.6f);
        for (Drip d : DRIPS) {
            double dist = Math.sqrt((d.x - cam.x) * (d.x - cam.x) + (d.y - cam.y) * (d.y - cam.y) + (d.z - cam.z) * (d.z - cam.z));
            if (dist > 40) {
                continue;
            }
            double s = 0.03 + dist * 0.001, st = Math.min(2.2, 1 + Math.abs(d.vy) * 0.06);
            float a = (float) (0.55 * clamp01((dist - 0.5) / 1.5)) * fade;
            face(cam, d.x, d.y, d.z);
            double px = d.x + BR[0] * s, py = d.y, pz = d.z + BR[2] * s;
            for (int q = 1; q <= 6; q++) {
                double ang = q / 6.0 * Math.PI * 2, ox = Math.cos(ang) * s, oy = Math.sin(ang) * s * st;
                double qx = d.x + BR[0] * ox, qy = d.y + oy, qz = d.z + BR[2] * ox;
                v(out, last, cam, d.x, d.y, d.z, r, g, b, a);
                v(out, last, cam, px, py, pz, r, g, b, a * 0.2f);
                v(out, last, cam, qx, qy, qz, r, g, b, a * 0.2f);
                v(out, last, cam, qx, qy, qz, r, g, b, a * 0.2f);
                px = qx;
                py = qy;
                pz = qz;
            }
        }
    }

    private static void renderRings(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        float r = Mth.lerp(night, 0.7f, 0.4f), g = Mth.lerp(night, 0.66f, 0.35f), b = Mth.lerp(night, 0.8f, 0.55f);
        for (Ring rg : RINGS) {
            double k = rg.age / 1.6, R = rg.r * Math.sqrt(k) * 1.4, w = 0.05 + 0.06 * k;
            float a = (float) ((1 - k) * Mth.lerp(night, 0.5f, 0.35f)) * fade;
            for (int j = 0; j < 14; j++) {
                double a0 = j / 14.0 * Math.PI * 2, a1 = (j + 1) / 14.0 * Math.PI * 2;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                v(out, last, cam, rg.x + c0 * (R - w), rg.y, rg.z + s0 * (R - w), r, g, b, 0);
                v(out, last, cam, rg.x + c1 * (R - w), rg.y, rg.z + s1 * (R - w), r, g, b, 0);
                v(out, last, cam, rg.x + c1 * (R + w), rg.y, rg.z + s1 * (R + w), r, g, b, a);
                v(out, last, cam, rg.x + c0 * (R + w), rg.y, rg.z + s0 * (R + w), r, g, b, a);
            }
        }
    }

    // bioluminescence: rises slowly and falls slower still, each fungus in its own rhythm
    private static void renderFungi(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        for (Fungus g : cur.fungi.values()) {
            double d = Math.sqrt((g.x - cam.x) * (g.x - cam.x) + (g.y - cam.y) * (g.y - cam.y) + (g.z - cam.z) * (g.z - cam.z));
            if (d > 50) {
                continue;
            }
            double p = 0.5 + 0.5 * Math.sin(t * Math.PI * 2 / g.per + g.ph), br = 0.25 + 0.75 * p * p;
            double s = 0.9 + 0.5 * br;
            ellipse(out, last, cam, g.x, g.y, g.z, s, s, 6, 0.55f, 0.22f, 1f, (float) (br * Mth.lerp(night, 0.16f, 0.32f)) * fade, 0);
        }
    }

    private static void renderSpores(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        for (Spore s : SPORES) {
            double d = Math.sqrt((s.x - cam.x) * (s.x - cam.x) + (s.y - cam.y) * (s.y - cam.y) + (s.z - cam.z) * (s.z - cam.z));
            if (d > 50) {
                continue;
            }
            double k = s.age / s.life;
            float a = (float) (clamp01(s.age / 0.5) * (1 - k) * Mth.lerp(night, 0.5f, 0.8f) * clamp01((d - 0.4) / 1.2)) * fade;
            double r = 0.03 + d * 0.0015;
            ellipse(out, last, cam, s.x, s.y, s.z, r, r, 6, 0.7f, 0.4f, 1f, a, 0);
        }
    }

    // the bark's eye: a slit pupil moving inside the face, and lids of bark closing in the blink (kept 0.02 off the face:
    // no flicker)
    private static void renderEyes(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        for (Eye e : cur.eyes.values()) {
            double d = Math.sqrt((e.x + 0.5 - cam.x) * (e.x + 0.5 - cam.x) + (e.y + 0.5 - cam.y) * (e.y + 0.5 - cam.y) + (e.z + 0.5 - cam.z) * (e.z + 0.5 - cam.z));
            if (d > 40) {
                continue;
            }
            double lx = -e.nz, lz = e.nx;
            double fx = e.x + 0.5 + e.nx * 0.5, fz = e.z + 0.5 + e.nz * 0.5, fy = e.y + 0.5;
            double pu = e.px * 0.22, pv = e.py * 0.18, pw = 0.07, ph = 0.24;
            eyeQuad(out, last, cam, e, fx, fy, fz, lx, lz, pu - pw, pv - ph, pu + pw, pv + ph, 0.02, 0.02f, 0f, 0.01f, fade);
            // a wet glint over the pupil
            eyeQuad(out, last, cam, e, fx, fy, fz, lx, lz, pu + 0.02, pv + 0.08, pu + 0.07, pv + 0.15, 0.025, 1f, 0.5f, 0.45f,
                    Mth.lerp(night, 0.35f, 0.5f) * fade);
            if (e.blink > 0) {
                // lids: from above and below, close and open (0.22 s)
                double k = Math.sin(e.blink / 0.22 * Math.PI), h = 0.5 * k;
                eyeQuad(out, last, cam, e, fx, fy, fz, lx, lz, -0.5, 0.5 - h, 0.5, 0.5, 0.03, 0.13f, 0.1f, 0.1f, fade);
                eyeQuad(out, last, cam, e, fx, fy, fz, lx, lz, -0.5, -0.5, 0.5, -0.5 + h, 0.03, 0.13f, 0.1f, 0.1f, fade);
            }
        }
    }

    private static void eyeQuad(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Eye e, double fx, double fy, double fz, double lx, double lz, double u0,
            double v0, double u1, double v1, double off, float r, float g, float b, float a) {
        double ox = e.nx * off, oz = e.nz * off;
        v(out, last, cam, fx + lx * u0 + ox, fy + v0, fz + lz * u0 + oz, r, g, b, a);
        v(out, last, cam, fx + lx * u1 + ox, fy + v0, fz + lz * u1 + oz, r, g, b, a);
        v(out, last, cam, fx + lx * u1 + ox, fy + v1, fz + lz * u1 + oz, r, g, b, a);
        v(out, last, cam, fx + lx * u0 + ox, fy + v1, fz + lz * u0 + oz, r, g, b, a);
    }

    private static void renderWatch(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        double R = 32;
        for (Watch o : WATCH) {
            if (o.dead || o.age < 0) {
                continue;
            }
            double dx = cam.x - o.x, dz = cam.z - o.z, d = Math.sqrt(dx * dx + dz * dz + (cam.y - o.y) * (cam.y - o.y));
            double fc = Math.cos(o.th);
            if (fc < 0.2) {
                continue;
            }
            // opens slowly when it appears, closes when it goes
            double open = clamp01(o.age / 0.7) * (o.out > 0 ? 1 - o.out : 1) * (o.blink > 0 ? 1 - Math.sin(o.blink / 0.16 * Math.PI) : 1);
            if (open < 0.02) {
                continue;
            }
            double vis = clamp01((fc - 0.2) / 0.5) * clamp01(1.25 - d / R);
            // the pair lies across the line to the player, turned by the head
            double hd = Math.max(1e-3, Math.hypot(dx, dz)), ax = -dz / hd, az = dx / hd, half = o.sp * 0.5 * fc;
            // far, the eye doesn't vanish into a point: keeps about 2 pixels
            double r = Math.max(o.r, d * 0.0042);
            float cr = 1f, cg = o.red ? 0.1f : 0.78f, cb = o.red ? 0.04f : 0.12f;
            for (int s = -1; s <= 1; s += 2) {
                double x = o.x + ax * half * s, z = o.z + az * half * s;
                ellipse(out, last, cam, x, o.y, z, r * 2.5, r * 1.9 * Math.max(0.35, open), 8, cr, cg, cb, (float) (0.17 * vis) * fade, 0.35f);
                ellipse(out, last, cam, x, o.y, z, r, r * 0.62 * open, 8, cr, cg, cb, (float) (0.95 * vis) * fade, 0.35f);
                ellipse(out, last, cam, x, o.y, z, r * 0.42, r * 0.26 * open, 8, 1f, 0.95f, 0.8f, (float) (0.55 * vis) * fade, 0.35f);
            }
        }
    }

    // ---------------------------------------------------------------- screen, fog

    public static boolean gradeOn() {
        return active() && FxShaders.jjGrade != null;
    }

    // Less color and a violet cast by day, violet edges at night (the preview's saturate 0.78 / 0.9), never a dark veil.
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.jjGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        float n = night;
        sh.safeGetUniform("Sat").set(Mth.lerp(fade, 1f, Mth.lerp(n, 0.78f, 0.9f)));
        sh.safeGetUniform("Contrast").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.06f, 1.05f)));
        sh.safeGetUniform("Wash").set(70 / 255f, 40 / 255f, 110 / 255f, 0.06f * (1 - n) * fade);
        sh.safeGetUniform("Warm").set(0f);
        sh.safeGetUniform("Vig").set(Mth.lerp(n, 34, 30) / 255f, Mth.lerp(n, 14, 10) / 255f, Mth.lerp(n, 52, 50) / 255f, Mth.lerp(n, 0.42f, 0.3f) * fade);
        sh.safeGetUniform("Flash").set(0f);
        FxPost.pass(sh);
    }

    // The fog: dusky violet by day, deep violet at night (only the hue moves, never down).
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float k = fade * 0.8f;
        float r = Mth.lerp(night, 0x26 / 255f, 0x0e / 255f), g = Mth.lerp(night, 0x21 / 255f, 0x0b / 255f), b = Mth.lerp(night, 0x30 / 255f, 0x16 / 255f);
        float lum = (event.getRed() + event.getGreen() + event.getBlue()) / 3f, mine = (r + g + b) / 3f;
        if (night > 0.5f && mine < lum) {
            float s = lum / Math.max(1e-3f, mine);
            r *= s;
            g *= s;
            b *= s;
        }
        event.setRed(Mth.lerp(k, event.getRed(), Math.min(1f, r)));
        event.setGreen(Mth.lerp(k, event.getGreen(), Math.min(1f, g)));
        event.setBlue(Mth.lerp(k, event.getBlue(), Math.min(1f, b)));
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float near = Mth.lerp(night, 5f, 6f), far = Mth.lerp(night, 64f, 50f);
        event.setNearPlaneDistance(Mth.lerp(fade, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), near)));
        event.setFarPlaneDistance(Mth.lerp(fade, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), far)));
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- sound

    private static void play(ClientLevel level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playLocalSound(x, y, z, s, SoundSource.AMBIENT, vol * strength, pitch, false);
    }

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 p) {
        if (drone == null || !mc.getSoundManager().isActive(drone)) {
            drone = new DroneLoop();
            mc.getSoundManager().play(drone);
        }
        // wood creaking: a far trunk, now and then
        nextCreak -= 0.05;
        if (nextCreak < 0) {
            double a = rnd() * Mth.TWO_PI, r = 14 + rnd() * 18;
            play(level, p.x + Math.cos(a) * r, p.y + 4 + rnd() * 8, p.z + Math.sin(a) * r, ModSounds.FLORESTA_RANGIDO.get(), 1.4f, 0.8f + (float) rnd() * 0.4f);
            nextCreak = 5 + rnd() * 12;
        }
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
            Pending s = it.next();
            if (clockS >= s.at) {
                it.remove();
                play(level, s.x, s.y, s.z, ModSounds.FLORESTA_SOPRO.get(), Math.min(1f, s.vol * 1.4f), 1f);
            }
        }
    }

    // The forest's low breath all round the player.
    private static final class DroneLoop extends AbstractTickableSoundInstance {
        DroneLoop() {
            super(ModSounds.FLORESTA_DRONE.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0.01f;
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            if (strength <= 0) {
                stop();
                return;
            }
            volume = Math.max(0.001f, 0.55f * strength);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
