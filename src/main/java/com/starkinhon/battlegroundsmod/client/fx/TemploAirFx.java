package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.ModBlocks;
import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The air of the Templos Antigos (Stark 2026-11-19 on the preview forja/jujutsu-templo-ambiente: "bonito pra caralho...
// acho que está pronto"). amb.js there is the source of truth and this is its port, the same names where it helps. Fades
// in when the player walks into the biome and out when the player leaves (BiomeAura); the sakura petals in the air stay
// TemploFx's. Everything makes physical sense:
// - the sea of clouds (unkai): the cold damp air is caught in the valley and the cloud makes a sea below the temple. Its
//   top waves and drifts slowly with the wind (souls_templo_cloud); the side facing the sun is bright, the folds in
//   shade; at dusk the top goes orange. The hills pierce it. Only seen from above it (with a shader pack, not at all);
// - incense: the thread of smoke rises straight (laminar) for a hand's width, then breaks into curls (turbulent), opens
//   and goes with the wind, thinner and thinner. The sticks' tips are embers. The bronze censer stands on the path in
//   front of the hall;
// - the bell: the hanging log (shumoku) is pulled back, let go, and strikes; the sound is deep and long. The boom
//   startles the doves off the roofs: they lift flapping, circle in a flock and glide back to the same spot;
// - the black kite (tobi): circles on a thermal without flapping. Now and then it makes a low pass over the temple and
//   the doves flee it too;
// - shide: the zigzag paper strips hanging from the sacred rope swing and flutter in the gusts;
// - the river of petals: a petal fallen on the stairway lies still until a gust comes; then it goes down step by step,
//   hopping each edge, and gathers at the foot;
// - the lanterns: at dusk they light one by one, from the bottom up, and the flame trembles; at night the shoji glow;
// - the screen (fx_jjgrade): clear mountain air by day, warm at dusk, blue edges at night (never darkening);
// - sounds built from noise and tones (tools/sounds/templo_sounds.py): the wind in the cedars, the bell, the doves'
//   wings, the kite, the bush warbler by day, the owl at night.
public final class TemploAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WIND = 1.9;
    private static final int SCAN = 64;

    private static float strength, lastStrength;
    private static float night, dusk;
    private static final double[] SUN = {0, 1, 0};
    private static double clock;
    private static long lastNanos;
    private static ClientLevel lastLevel;

    // ---------------------------------------------------------------- the column scan (a row a tick)

    private static final class Scan {
        final Long2IntOpenHashMap gravel = new Long2IntOpenHashMap();
        final List<double[]> lamps = new ArrayList<>();
        int lo = Integer.MAX_VALUE;
    }

    private static Scan cur = new Scan(), next = new Scan();
    private static int scanRow = -SCAN - 1, scanCx, scanCz;
    private static boolean firstPass = true;
    private static double cloudY = -999;

    // ---------------------------------------------------------------- the temple (read once)

    private static final class Temple {
        int cx, cz, y, top, fx, fz;
        double[] censer, belfry, sacred;
        final List<int[]> steps = new ArrayList<>();
    }

    private static final class Reading {
        int x0, x1, z0, z1, y0, y1, sx, y, cx, cz;
        final List<double[]> ropes = new ArrayList<>();
        final List<double[]> windows = new ArrayList<>();
        final List<double[]> perches = new ArrayList<>();
        final List<double[]> copper = new ArrayList<>();
        int top;
    }

    private static Temple T;
    private static Reading reading;
    private static long readAt = -100000;
    private static final List<double[]> SHIDE = new ArrayList<>();
    private static final List<double[]> WINDOWS = new ArrayList<>();
    private static final List<double[]> PERCHES = new ArrayList<>();

    // ---------------------------------------------------------------- moving things

    private static final class Puff {
        double x, y, z, vx, vy, vz, age, life, ph, h0;
    }

    private static final List<Puff> SMOKE = new ArrayList<>();
    private static double smokeAcc;

    private static final class Bell {
        double th, w, pull;
        int phase;
    }

    private static final Bell BELL = new Bell();
    private static double nextBell = 6, bellT = -100;

    private static final class Dove {
        double hx, hy, hz, x, y, z, hd, ph, t, a, r, hy2, w, vy, delay, sx, sy, sz, back;
        int st;
    }

    private static final List<Dove> DOVES = new ArrayList<>();

    private static final class Kite {
        double a, r = 28, cx, cz, y, x, z, hd, next, dt;
        boolean dive, flushed;
    }

    private static Kite kite;

    private static final class Petal {
        double k, s, v, hop, rot, rest;
        float r, g, b;
    }

    private static final List<Petal> PETALS = new ArrayList<>();
    private static double litT;

    // sounds
    private static WindLoop windLoop;
    private static double nextBird = 2, nextOwl = 4;
    private static float windGust;
    private static boolean wingsNow, kiteCall;

    private TemploAirFx() {
    }

    // ---------------------------------------------------------------- helpers

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

    private static int top(ClientLevel level, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!level.hasChunk(ix >> 4, iz >> 4)) {
            return -999;
        }
        int h = ClientHeights.noLeaves(level, ix, iz) - 1;
        return h <= level.getMinBuildHeight() ? -999 : h;
    }

    // Mountain wind: a breeze and gusts running down the slope at 4 blocks/s. Gives vx, vz and strength 0..1 in W.
    private static final double[] W = new double[3];

    private static double[] wind(double x, double z, double t) {
        double c = Math.cos(WIND), s = Math.sin(WIND), along = x * c + z * s, across = -x * s + z * c;
        double g = clamp01(0.2 + 0.8 * vnoise(along / 16 - t * 0.25, across / 20) * (0.5 + 0.5 * Math.sin((along - t * 4) / 10)));
        double v = 0.4 + 2.4 * g;
        W[0] = c * v;
        W[1] = s * v;
        W[2] = g;
        return W;
    }

    // day, dusk and night mixed: k at day, at dusk and at night
    private static float hour(float day, float atDusk, float atNight) {
        return Mth.lerp(night, Mth.lerp(dusk, day, atDusk), atNight);
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    // ---------------------------------------------------------------- tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            if (lastStrength > 0 || T != null || reading != null) {
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
        boolean in = BiomeAura.in("templos_antigos_ar", b -> b.is(ModWorldgen.TEMPLOS_ANTIGOS));
        strength = Mth.clamp(strength + (in ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            if (lastStrength > 0) {
                reset(false);
            }
            return;
        }
        ClientLevel level = mc.level;
        Vec3 p = mc.player.position();
        int rows = firstPass ? 16 : 1;
        for (int i = 0; i < rows; i++) {
            scanStep(level, p);
        }
        if (reading != null) {
            readStep(level);
        }
        sounds(mc, level, p);
    }

    private static void scanStep(ClientLevel level, Vec3 p) {
        if (scanRow > SCAN) {
            Scan done = next;
            next = cur;
            cur = done;
            next.gravel.clear();
            next.lamps.clear();
            next.lo = Integer.MAX_VALUE;
            firstPass = false;
            afterPass(level, p);
            scanRow = -SCAN - 1;
        }
        if (scanRow == -SCAN - 1) {
            scanCx = Mth.floor(p.x);
            scanCz = Mth.floor(p.z);
            scanRow = -SCAN;
        }
        int x = scanCx + scanRow++;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Block gravel = ModBlocks.WHITE_GRAVEL.get(), light = ModBlocks.STONE_LANTERN_LIGHT.get();
        for (int z = scanCz - SCAN; z <= scanCz + SCAN; z++) {
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int g = ClientHeights.noLeaves(level, x, z) - 1;
            if (g <= level.getMinBuildHeight()) {
                continue;
            }
            next.lo = Math.min(next.lo, g);
            BlockState s = level.getBlockState(pos.set(x, g, z));
            if (s.is(gravel)) {
                next.gravel.put(key(x, z), g);
            } else if (s.is(Blocks.ANDESITE) && level.getBlockState(pos.set(x, g - 1, z)).is(light)) {
                next.lamps.add(new double[] {x + 0.5, g - 1 + 0.45, z + 0.5, rnd() * 9, hsh(x, z)});
            }
        }
    }

    // After a pass: the cloud's height, and the temple: the white gravel courtyard (the level with the most of it).
    private static void afterPass(ClientLevel level, Vec3 p) {
        // the cloud stays a few blocks above the lowest ground (or the sea)
        int lo = cur.lo;
        for (int a = -100; a <= 100; a += 10) {
            for (int b = -100; b <= 100; b += 10) {
                int g = top(level, p.x + a, p.z + b);
                if (g > -900) {
                    lo = Math.min(lo, g);
                }
            }
        }
        if (lo != Integer.MAX_VALUE) {
            cloudY = Math.max(lo, level.getSeaLevel() - 1) + 1.5;
        }
        Long2IntOpenHashMap gv = cur.gravel;
        if (gv.size() < 150) {
            return;
        }
        Long2IntOpenHashMap count = new Long2IntOpenHashMap();
        for (int y : gv.values()) {
            count.addTo(y, 1);
        }
        int bestY = 0, bestN = 0;
        for (Long2IntMap.Entry e : count.long2IntEntrySet()) {
            if (e.getIntValue() > bestN) {
                bestN = e.getIntValue();
                bestY = (int) e.getLongKey();
            }
        }
        if (bestN < 150) {
            return;
        }
        double sx = 0, sz = 0;
        int n = 0;
        for (Long2IntMap.Entry e : gv.long2IntEntrySet()) {
            if (e.getIntValue() == bestY) {
                sx += (int) (e.getLongKey() >> 32);
                sz += (int) e.getLongKey();
                n++;
            }
        }
        double mx = sx / n, mz = sz / n;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Long2IntMap.Entry e : gv.long2IntEntrySet()) {
            int x = (int) (e.getLongKey() >> 32), z = (int) e.getLongKey();
            if (e.getIntValue() != bestY || Math.abs(x - mx) > 30 || Math.abs(z - mz) > 30) {
                continue;
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        if (maxX - minX < 16 || maxZ - minZ < 16) {
            return;
        }
        int cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        boolean same = T != null && T.y == bestY && Math.abs(T.cx - cx) <= 3 && Math.abs(T.cz - cz) <= 3;
        boolean busy = reading != null && reading.y == bestY && Math.abs(reading.cx - cx) <= 3 && Math.abs(reading.cz - cz) <= 3;
        if (!same && !busy && level.getGameTime() - readAt > 200) {
            Reading r = new Reading();
            r.x0 = minX - 3;
            r.x1 = maxX + 3;
            r.z0 = minZ - 3;
            r.z1 = maxZ + 3;
            r.y = bestY;
            r.y0 = bestY - 1;
            r.y1 = bestY + 48;
            r.sx = r.x0;
            r.cx = cx;
            r.cz = cz;
            r.top = bestY;
            reading = r;
            readAt = level.getGameTime();
        }
    }

    private static void readStep(ClientLevel level) {
        Reading r = reading;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), up = new BlockPos.MutableBlockPos();
        Block rope = ModBlocks.SHIMENAWA.get(), shoji = ModBlocks.SHOJI.get(), lacquer = ModBlocks.BLACK_LACQUER.get();
        for (int slice = 0; slice < 2 && r.sx <= r.x1; slice++) {
            int x = r.sx++;
            for (int z = r.z0; z <= r.z1; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = r.y0; y <= r.y1; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (s.isAir()) {
                        continue;
                    }
                    if (s.is(rope)) {
                        r.ropes.add(new double[] {x, y, z});
                    } else if (s.is(shoji)) {
                        if (TemploGenHash.h(x, y, z) < 0.25) {
                            r.windows.add(new double[] {x + 0.5, y + 0.5, z + 0.5});
                        }
                    } else if (s.is(Blocks.WAXED_WEATHERED_COPPER)) {
                        r.copper.add(new double[] {x, y, z});
                    }
                    boolean roof = s.is(Blocks.POLISHED_DEEPSLATE) || s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.DEEPSLATE_BRICKS);
                    if (roof) {
                        r.top = Math.max(r.top, y);
                    }
                    if ((s.is(Blocks.POLISHED_DEEPSLATE) || s.is(lacquer) || s.is(Blocks.DEEPSLATE_BRICKS) || s.is(Blocks.STONE_BRICKS))
                            && level.getBlockState(up.set(x, y + 1, z)).isAir() && hsh(x * 3, z) < 0.5 && y != r.y) {
                        r.perches.add(new double[] {x + 0.5, y + 1, z + 0.5});
                    }
                }
            }
        }
        if (r.sx > r.x1) {
            finishTemple(level, r);
            reading = null;
        }
    }

    // The preview's hash3 for the shoji that glow (a quarter of them): any fixed hash will do.
    private static final class TemploGenHash {
        static double h(int x, int y, int z) {
            return hsh(x * 31 + y * 7919 + 4 * 104729, z * 17 - y * 3571 + 4);
        }
    }

    private static void finishTemple(ClientLevel level, Reading r) {
        Temple t = new Temple();
        t.cx = r.cx;
        t.cz = r.cz;
        t.y = r.y;
        t.top = r.top;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // the front: the side where the stairway starts (smooth stone just past the courtyard, on its axis)
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int best = -1;
        int[] start = null;
        for (int d = 0; d < 4 && best < 0; d++) {
            int fx = dirs[d][0], fz = dirs[d][1];
            int edge = fx > 0 ? r.x1 - 3 : fx < 0 ? r.x0 + 3 : fz > 0 ? r.z1 - 3 : r.z0 + 3;
            for (int k = 0; k <= 3 && best < 0; k++) {
                for (int s = -1; s <= 1 && best < 0; s++) {
                    int x = fx != 0 ? edge + fx * k : r.cx + s, z = fz != 0 ? edge + fz * k : r.cz + s;
                    for (int y = r.y; y >= r.y - 1; y--) {
                        if (level.getBlockState(pos.set(x, y, z)).is(Blocks.SMOOTH_STONE)) {
                            best = d;
                            start = new int[] {x, z, y};
                            break;
                        }
                    }
                }
            }
        }
        if (best >= 0) {
            t.fx = dirs[best][0];
            t.fz = dirs[best][1];
            // the steps: down the axis while there is smooth stone a block lower at most
            int x = start[0], z = start[1], sy = start[2];
            if (t.fx != 0) {
                z = r.cz;
            } else {
                x = r.cx;
            }
            for (int i = 0; i < 95; i++) {
                int found = Integer.MIN_VALUE;
                for (int y = sy; y >= sy - 1; y--) {
                    if (level.getBlockState(pos.set(x, y, z)).is(Blocks.SMOOTH_STONE)) {
                        found = y;
                        break;
                    }
                }
                if (found == Integer.MIN_VALUE) {
                    break;
                }
                sy = found;
                t.steps.add(new int[] {x, z, sy});
                x += t.fx;
                z += t.fz;
            }
            // the censer: on the path, 4 blocks from its end at the hall
            int kmin = Integer.MAX_VALUE;
            for (int k = -30; k <= 30; k++) {
                int px = r.cx + t.fx * k, pz = r.cz + t.fz * k;
                if (level.getBlockState(pos.set(px, r.y, pz)).is(Blocks.POLISHED_ANDESITE)) {
                    kmin = Math.min(kmin, k);
                }
            }
            if (kmin != Integer.MAX_VALUE) {
                int px = r.cx + t.fx * (kmin + 4), pz = r.cz + t.fz * (kmin + 4);
                boolean free = true;
                for (int y = r.y + 1; y <= r.y + 3; y++) {
                    free &= level.getBlockState(pos.set(px, y, pz)).isAir();
                }
                if (free && level.getBlockState(pos.set(px, r.y, pz)).is(Blocks.POLISHED_ANDESITE)) {
                    t.censer = new double[] {px, pz};
                }
            }
        }
        if (!r.copper.isEmpty()) {
            double bx = 0, bz = 0;
            for (double[] c : r.copper) {
                bx += c[0];
                bz += c[2];
            }
            t.belfry = new double[] {Math.round(bx / r.copper.size()), Math.round(bz / r.copper.size())};
        }
        SHIDE.clear();
        if (!r.ropes.isEmpty()) {
            double ax = 0, az = 0;
            for (double[] c : r.ropes) {
                ax += c[0] + 0.5;
                az += c[2] + 0.5;
            }
            t.sacred = new double[] {ax / r.ropes.size(), az / r.ropes.size()};
            // a strip hung on the rope's outer edge (the inner one is the still shide block)
            for (double[] c : r.ropes) {
                double dx = c[0] + 0.5 - t.sacred[0], dz = c[2] + 0.5 - t.sacred[1], L = Math.max(1e-3, Math.hypot(dx, dz)), ox = dx / L, oz = dz / L;
                SHIDE.add(new double[] {c[0] + 0.5 + ox * 0.55, c[1] + 0.32, c[2] + 0.5 + oz * 0.55, rnd() * Mth.TWO_PI, Math.atan2(oz, ox) + Math.PI / 2});
            }
        }
        WINDOWS.clear();
        WINDOWS.addAll(r.windows);
        PERCHES.clear();
        PERCHES.addAll(r.perches);
        T = t;
        makeDoves();
        makeStairPetals();
        SMOKE.clear();
        kite = null;
        BELL.phase = 0;
        BELL.th = BELL.w = 0;
        nextBell = 6;
    }

    private static void reset(boolean all) {
        strength = 0;
        SMOKE.clear();
        kite = null;
        litT = 0;
        firstPass = true;
        scanRow = -SCAN - 1;
        next = new Scan();
        if (windLoop != null) {
            windLoop.end();
            windLoop = null;
        }
        if (all) {
            cur = new Scan();
            T = null;
            reading = null;
            readAt = -100000;
            cloudY = -999;
            SHIDE.clear();
            WINDOWS.clear();
            PERCHES.clear();
            DOVES.clear();
            PETALS.clear();
        }
    }

    // ---------------------------------------------------------------- incense

    private static void stepSmoke(double dt, double t) {
        if (T == null || T.censer == null) {
            return;
        }
        double cx = T.censer[0], cz = T.censer[1], y0 = T.y + 1 + 1.22;
        smokeAcc += dt * 16;
        while (smokeAcc > 1) {
            smokeAcc--;
            int s = (int) (rnd() * 3);
            Puff p = new Puff();
            p.x = cx + 0.5 + (s - 1) * 0.16;
            p.y = y0;
            p.z = cz + 0.5 + (s == 1 ? 0.12 : -0.06);
            p.vy = 0.55;
            p.life = 8 + rnd() * 4;
            p.ph = rnd() * 100;
            p.h0 = y0;
            SMOKE.add(p);
        }
        for (Puff p : SMOKE) {
            p.age += dt;
            double up = p.y - p.h0;
            double[] w = wind(p.x, p.z, t);
            double wx = w[0], wz = w[1];
            // laminar to ~0.5 block; then the turbulence grows with height and the wind takes more and more
            double turb = clamp01((up - 0.45) / 1.2), k = 0.9;
            double nx = (vnoise(p.x * k + t * 0.6, p.y * k + p.ph) - 0.5) * 2, nz = (vnoise(p.z * k - t * 0.5 + 17, p.y * k) - 0.5) * 2;
            double drift = clamp01(up / 4);
            p.vx += ((nx * 0.9 * turb + wx * 0.55 * drift) - p.vx) * Math.min(1, dt * 2);
            p.vz += ((nz * 0.9 * turb + wz * 0.55 * drift) - p.vz) * Math.min(1, dt * 2);
            p.vy += ((0.55 - 0.3 * turb) - p.vy) * Math.min(1, dt * 1.5);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
        }
        SMOKE.removeIf(p -> p.age >= p.life);
    }

    private static void renderCenser(VertexConsumer solid, VertexConsumer glow, PoseStack.Pose last, Vec3 cam) {
        if (T == null || T.censer == null) {
            return;
        }
        double x = T.censer[0] + 0.5, z = T.censer[1] + 0.5, y = T.y + 1;
        if (Math.hypot(x - cam.x, z - cam.z) > 90) {
            return;
        }
        float k = hour(1f, 0.85f, 0.45f);
        float[] br = {0.36f, 0.33f, 0.26f};
        // the bronze censer: three feet, the bowl, the ash, the columns and the little roof
        for (double a : new double[] {0, 2.09, 4.19}) {
            box(solid, last, cam, x + Math.cos(a) * 0.45, y, z + Math.sin(a) * 0.45, 0.07, 0, 0, 0, 0, 0.07, 0.35, br, k);
        }
        box(solid, last, cam, x, y + 0.35, z, 0.62, 0, 0, 0, 0, 0.62, 0.5, br, k);
        box(solid, last, cam, x, y + 0.85, z, 0.7, 0, 0, 0, 0, 0.7, 0.08, br, k * 1.1f);
        box(solid, last, cam, x, y + 0.86, z, 0.55, 0, 0, 0, 0, 0.55, 0.06, new float[] {0.62f, 0.6f, 0.56f}, k);
        for (double[] ab : new double[][] {{-0.55, -0.55}, {0.55, -0.55}, {-0.55, 0.55}, {0.55, 0.55}}) {
            box(solid, last, cam, x + ab[0], y + 0.93, z + ab[1], 0.05, 0, 0, 0, 0, 0.05, 0.75, br, k);
        }
        box(solid, last, cam, x, y + 1.68, z, 0.85, 0, 0, 0, 0, 0.85, 0.1, br, k);
        box(solid, last, cam, x, y + 1.78, z, 0.55, 0, 0, 0, 0, 0.55, 0.12, br, k * 0.9f);
        box(solid, last, cam, x, y + 1.9, z, 0.18, 0, 0, 0, 0, 0.18, 0.22, br, k);
        // the sticks and their embers
        for (double[] o : new double[][] {{-0.16, -0.06}, {0, 0.12}, {0.16, -0.06}}) {
            box(solid, last, cam, x + o[0], y + 0.92, z + o[1], 0.012, 0, 0, 0, 0, 0.012, 0.28, new float[] {0.55f, 0.25f, 0.15f}, k);
            disc(glow, last, cam, x + o[0], y + 1.21, z + o[1], 0.025, 1f, 0.45f, 0.15f, 0.9f * strength, 0.3f);
            disc(glow, last, cam, x + o[0], y + 1.21, z + o[1], 0.09, 1f, 0.35f, 0.1f, Mth.lerp(night, 0.08f, 0.25f) * strength, 0);
        }
    }

    private static void renderSmoke(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        if (SMOKE.isEmpty()) {
            return;
        }
        // the smoke: a denser core that opens and fades; the tone of each hour's sky, lighter on the sun's side
        float r0 = hour(0.86f, 0.92f, 0.42f), g0 = hour(0.88f, 0.78f, 0.45f), b0 = hour(0.92f, 0.7f, 0.55f);
        for (Puff p : SMOKE) {
            double up = p.y - p.h0, r = 0.035 + 0.08 * up + 0.05 * p.age;
            double a = Math.min(0.5, 0.5 * clamp01(p.age / 0.2)) * clamp01((p.life - p.age) / 4) / (1 + up * 0.9);
            double d = Math.max(1e-3, Math.sqrt((p.x - cam.x) * (p.x - cam.x) + (p.y - cam.y) * (p.y - cam.y) + (p.z - cam.z) * (p.z - cam.z)));
            if (d > 90) {
                continue;
            }
            double back = (1 - night) * Math.max(0, ((p.x - cam.x) * SUN[0] + (p.y - cam.y) * SUN[1] + (p.z - cam.z) * SUN[2]) / d);
            float kk = (float) (1 + back * 0.25);
            disc(out, last, cam, p.x, p.y, p.z, r, Math.min(1, r0 * kk), Math.min(1, g0 * kk), Math.min(1, b0 * kk), (float) (a * 0.55) * fade, 0);
        }
    }

    // ---------------------------------------------------------------- the bell and the doves

    private static void bellStep(double dt) {
        nextBell -= dt;
        if (T != null && T.belfry != null) {
            Bell b = BELL;
            if (nextBell < 0 && b.phase == 0) {
                b.phase = 1;
                b.pull = 0;
            }
            if (b.phase == 1) {
                b.pull += dt;
                b.th += (0.65 - b.th) * Math.min(1, dt * 2.2);
                if (b.pull > 2.2) {
                    b.phase = 2;
                    b.w = 0;
                }
            } else if (b.phase > 1) {
                // a pendulum: g/L with L = 1.5
                b.w += (-9.8 / 1.5 * Math.sin(b.th)) * dt;
                b.w *= 1 - dt * 0.6;
                b.th += b.w * dt;
                if (b.phase == 2 && b.th < -0.02) {
                    b.th = -0.02;
                    b.w = -b.w * 0.35;
                    b.phase = 3;
                    strike(true);
                    nextBell = 35 + rnd() * 15;
                }
                if (b.phase == 3 && Math.abs(b.w) < 0.02 && Math.abs(b.th) < 0.03) {
                    b.phase = 0;
                }
            }
        } else if (nextBell < 0) {
            strike(false);
            nextBell = 40 + rnd() * 20;
        }
    }

    private static boolean bellNow, bellLocal;

    private static void strike(boolean local) {
        bellT = clock;
        bellNow = true;
        bellLocal = local;
        if (local) {
            flush(T.belfry[0], T.belfry[1]);
        }
    }

    private static void renderBell(VertexConsumer solid, VertexConsumer glow, PoseStack.Pose last, Vec3 cam) {
        if (T == null || T.belfry == null) {
            return;
        }
        double gx = T.belfry[0], gz = T.belfry[1];
        int y = T.y;
        if (Math.hypot(gx - cam.x, gz - cam.z) > 90) {
            return;
        }
        float k = Mth.lerp(night, 1f, 0.45f);
        // the log hung on two ropes from the beam, beside the bell, pointing at it (x axis)
        double px = gx + 0.5 + 1.25, py = y + 6, pz = gz + 0.5, L = 1.45, th = BELL.th;
        double cx = px + Math.sin(th) * L, cy = py - Math.cos(th) * L;
        box(solid, last, cam, cx + 0.05, cy - 0.11, pz, 0.6, 0, 0, 0, 0, 0.11, 0.22, new float[] {0.5f, 0.38f, 0.26f}, k);
        box(solid, last, cam, (px + cx) / 2, (py + cy) / 2 - 0.02, pz, Math.sin(th) * L / 2 + 0.015, -Math.cos(th) * L / 2, 0, 0, 0, 0.015, 0.03,
                new float[] {0.85f, 0.8f, 0.65f}, k);
        // the bronze hums: a faint glow that pulses and dies after the strike
        double e = clock - bellT;
        if (e > 0 && e < 5) {
            disc(glow, last, cam, gx + 0.5, y + 4.5, gz + 0.5, 1.6, 0.9f, 0.8f, 0.55f, (float) (0.06 * Math.exp(-e) * (1 + Math.sin(e * 25))) * strength, 0);
        }
    }

    private static void makeDoves() {
        DOVES.clear();
        List<double[]> p = new ArrayList<>();
        for (double[] q : PERCHES) {
            if (Math.abs(q[1] - 1 - T.y) >= 0.5 && Math.abs(q[0] - T.cx) < 40 && Math.abs(q[2] - T.cz) < 40) {
                p.add(q);
            }
        }
        Collections.shuffle(p, new java.util.Random(RANDOM.nextLong()));
        for (int i = 0; i < Math.min(30, p.size()); i++) {
            double[] q = p.get(i);
            Dove d = new Dove();
            d.hx = d.x = q[0];
            d.hy = d.y = q[1];
            d.hz = d.z = q[2];
            d.hd = rnd() * Mth.TWO_PI;
            d.ph = rnd() * 6;
            d.a = rnd() * Mth.TWO_PI;
            d.r = 12 + rnd() * 10;
            d.hy2 = 10 + rnd() * 8;
            d.w = 0.5 + rnd() * 0.25;
            d.back = 16 + rnd() * 8;
            DOVES.add(d);
        }
    }

    private static void flush(double x, double z) {
        boolean any = false;
        for (Dove d : DOVES) {
            if (d.st == 0 && Math.hypot(d.x - x, d.z - z) < 60) {
                d.st = 1;
                d.t = 0;
                d.vy = 3 + rnd() * 2;
                d.delay = rnd() * 0.5;
                d.a = Math.atan2(d.z - T.cz, d.x - T.cx);
                any = true;
            }
        }
        if (any) {
            wingsNow = true;
        }
    }

    private static void stepDoves(double dt) {
        if (T == null) {
            return;
        }
        for (Dove d : DOVES) {
            d.ph += dt;
            if (d.st == 0) {
                continue;
            }
            d.t += dt;
            if (d.st == 1) {
                if (d.t < d.delay) {
                    continue;
                }
                // takes off: climbs hard, opens outward and joins the flock's wheel
                d.a += d.w * dt;
                d.r += (16 - d.r) * dt * 0.3;
                double tx = T.cx + Math.cos(d.a) * d.r, tz = T.cz + Math.sin(d.a) * d.r, ty = T.y + d.hy2 + Math.sin(d.a * 2) * 2;
                double k = Math.min(1, dt * (d.t < 1.5 ? 1.2 : 2.5));
                double ox = d.x, oz = d.z;
                d.x += (tx - d.x) * k;
                d.z += (tz - d.z) * k;
                d.y += (ty - d.y) * k;
                if (Math.hypot(d.x - ox, d.z - oz) > 1e-5) {
                    d.hd = Math.atan2(d.z - oz, d.x - ox);
                }
                if (d.t > d.back) {
                    d.st = 2;
                    d.t = 0;
                    d.sx = d.x;
                    d.sy = d.y;
                    d.sz = d.z;
                }
            } else if (d.st == 2) {
                // glides home: a soft curve to the perch, braking with a few beats at the end
                double u = Math.min(1, d.t / 4), e = u * u * (3 - 2 * u);
                double ox = d.x, oz = d.z;
                d.x = d.sx + (d.hx - d.sx) * e;
                d.z = d.sz + (d.hz - d.sz) * e;
                d.y = d.sy + (d.hy - d.sy) * e + Math.sin(u * Math.PI) * 2;
                if (Math.hypot(d.x - ox, d.z - oz) > 1e-4) {
                    d.hd = Math.atan2(d.z - oz, d.x - ox);
                }
                if (u >= 1) {
                    d.st = 0;
                    d.x = d.hx;
                    d.y = d.hy;
                    d.z = d.hz;
                    d.back = 16 + rnd() * 8;
                }
            }
        }
    }

    private static void renderDoves(VertexConsumer solid, VertexConsumer area, PoseStack.Pose last, Vec3 cam, float fade, boolean solidPass) {
        float k = hour(1f, 0.85f, 0.4f);
        for (Dove d : DOVES) {
            double dist = Math.sqrt((d.x - cam.x) * (d.x - cam.x) + (d.y - cam.y) * (d.y - cam.y) + (d.z - cam.z) * (d.z - cam.z));
            if (dist > 110) {
                continue;
            }
            double S = 1.6, hx = Math.cos(d.hd) * S, hz = Math.sin(d.hd) * S, px = -hz, pz = hx, y = d.y;
            if (d.st == 0) {
                if (solidPass) {
                    // perched: body, breast and a head that pecks now and then
                    double bob = Math.pow(Math.max(0, Math.sin(d.ph * 1.7 + d.hx)), 8) * 0.06;
                    box(solid, last, cam, d.x, y, d.z, hx * 0.16, 0, hz * 0.16, px * 0.08, 0, pz * 0.08, 0.14, new float[] {0.6f, 0.62f, 0.66f}, k);
                    box(solid, last, cam, d.x + hx * 0.15, y + 0.1 - bob, d.z + hz * 0.15, hx * 0.05, 0, hz * 0.05, px * 0.045, 0, pz * 0.045, 0.08,
                            new float[] {0.45f, 0.55f, 0.5f}, k);
                }
                continue;
            }
            if (solidPass) {
                continue;
            }
            float br = 0.6f * k, bg = 0.62f * k, bb = 0.66f * k, dr = 0.35f * k, dg = 0.36f * k, db = 0.4f * k, a = fade;
            double flap = d.st == 2 && d.t < 3.3 ? 0.15 : Math.sin(d.ph * 2 * Math.PI * 6);
            double sp = 0.32, tipY = flap * 0.2;
            v(area, last, cam, d.x + hx * 0.18, y, d.z + hz * 0.18, br, bg, bb, a);
            v(area, last, cam, d.x + px * 0.06, y, d.z + pz * 0.06, br, bg, bb, a);
            v(area, last, cam, d.x - hx * 0.2, y, d.z - hz * 0.2, br, bg, bb, a);
            v(area, last, cam, d.x - px * 0.06, y, d.z - pz * 0.06, br, bg, bb, a);
            v(area, last, cam, d.x - hx * 0.15, y, d.z - hz * 0.15, dr, dg, db, a);
            v(area, last, cam, d.x - hx * 0.3 + px * 0.07, y, d.z - hz * 0.3 + pz * 0.07, dr, dg, db, a);
            v(area, last, cam, d.x - hx * 0.3 - px * 0.07, y, d.z - hz * 0.3 - pz * 0.07, dr, dg, db, a);
            v(area, last, cam, d.x - hx * 0.15, y, d.z - hz * 0.15, dr, dg, db, a);
            for (int s = 1; s >= -1; s -= 2) {
                double r0x = d.x + hx * 0.08, r0z = d.z + hz * 0.08, r1x = d.x - hx * 0.08, r1z = d.z - hz * 0.08;
                v(area, last, cam, r0x, y, r0z, br, bg, bb, a);
                v(area, last, cam, r0x + px * s * sp, y + tipY, r0z + pz * s * sp, dr, dg, db, a);
                v(area, last, cam, r1x + px * s * sp * 0.9 - hx * 0.06, y + tipY, r1z + pz * s * sp * 0.9 - hz * 0.06, dr, dg, db, a);
                v(area, last, cam, r1x, y, r1z, br, bg, bb, a);
            }
        }
    }

    // ---------------------------------------------------------------- the black kite

    private static void stepKite(double dt, double t) {
        if (night > 0.5f) {
            kite = null;
            return;
        }
        if (T == null) {
            return;
        }
        double cx = T.cx, cz = T.cz, top0 = T.top;
        if (kite == null) {
            kite = new Kite();
            kite.a = rnd() * Mth.TWO_PI;
            kite.cx = cx + 20;
            kite.cz = cz - 10;
            kite.y = top0 + 18;
            kite.next = 20 + rnd() * 20;
        }
        Kite K = kite;
        K.next -= dt;
        if (K.next < 0 && !K.dive) {
            K.dive = true;
            K.dt = 0;
            K.next = 40 + rnd() * 30;
        }
        // the thermal: its wheel drifts slowly with the wind; on a low pass the wheel drops to the roof and back
        double[] w = wind(K.cx, K.cz, t);
        K.cx += w[0] * 0.15 * dt;
        K.cz += w[1] * 0.15 * dt;
        K.cx += (cx - K.cx) * dt * 0.02;
        K.cz += (cz - K.cz) * dt * 0.02;
        double ty = top0 + 18, tr = 28;
        if (K.dive) {
            K.dt += dt;
            double u = K.dt / 9;
            ty = top0 + 18 - Math.sin(Math.min(1, u) * Math.PI) * (top0 - T.y + 6);
            tr = 28 - Math.sin(Math.min(1, u) * Math.PI) * 14;
            if (!K.flushed && u > 0.4) {
                K.flushed = true;
                flush(T.cx, T.cz);
            }
            if (u >= 1) {
                K.dive = false;
                K.flushed = false;
            }
        }
        K.r += (tr - K.r) * dt * 0.5;
        K.y += (ty - K.y) * dt * 0.6;
        K.a += (7 / K.r) * dt;
        K.x = K.cx + Math.cos(K.a) * K.r;
        K.z = K.cz + Math.sin(K.a) * K.r;
        K.hd = K.a + Math.PI / 2;
        if (rnd() < dt / 25) {
            kiteCall = true;
        }
    }

    private static void renderKite(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        Kite K = kite;
        if (K == null) {
            return;
        }
        double hx = Math.cos(K.hd), hz = Math.sin(K.hd), px = -hz, pz = hx, bank = 0.35, y = K.y;
        float r = Mth.lerp(dusk, 0.3f, 0.18f), g = Mth.lerp(dusk, 0.22f, 0.12f), b = Mth.lerp(dusk, 0.16f, 0.1f), a = fade;
        float r2 = r * 0.6f, g2 = g * 0.6f, b2 = b * 0.6f;
        // a long straight wing with "fingered" tips; banks into the turn; a forked tail (the tobi's mark)
        quad(out, last, cam, K.x + hx * 0.45, y, K.z + hz * 0.45, K.x + px * 0.1, y, K.z + pz * 0.1, K.x - hx * 0.4, y, K.z - hz * 0.4, K.x - px * 0.1, y, K.z - pz * 0.1, r, g, b, a);
        for (int s = 1; s >= -1; s -= 2) {
            double dy = s * bank;
            quad(out, last, cam, K.x + hx * 0.2, y, K.z + hz * 0.2, K.x + px * s * 1.25 + hx * 0.1, y + dy * 1.25 + 0.06, K.z + pz * s * 1.25 + hz * 0.1,
                    K.x + px * s * 1.15 - hx * 0.15, y + dy * 1.15 + 0.06, K.z + pz * s * 1.15 - hz * 0.15, K.x - hx * 0.15, y, K.z - hz * 0.15, r, g, b, a);
            for (int f = 0; f < 3; f++) {
                double aa = 1.3, u0 = 0.08 - f * 0.1;
                quad(out, last, cam, K.x + px * s * 1.2 + hx * u0, y + dy * 1.2 + 0.06, K.z + pz * s * 1.2 + hz * u0,
                        K.x + px * s * (aa + 0.22) + hx * (u0 + 0.02), y + dy * (aa + 0.22) + 0.08, K.z + pz * s * (aa + 0.22) + hz * (u0 + 0.02),
                        K.x + px * s * (aa + 0.2) + hx * (u0 - 0.04), y + dy * (aa + 0.2) + 0.08, K.z + pz * s * (aa + 0.2) + hz * (u0 - 0.04),
                        K.x + px * s * 1.2 + hx * (u0 - 0.06), y + dy * 1.2 + 0.06, K.z + pz * s * 1.2 + hz * (u0 - 0.06), r2, g2, b2, a);
            }
            quad(out, last, cam, K.x - hx * 0.35, y, K.z - hz * 0.35, K.x - hx * 0.8 + px * s * 0.2, y, K.z - hz * 0.8 + pz * s * 0.2,
                    K.x - hx * 0.7 + px * s * 0.04, y, K.z - hz * 0.7 + pz * s * 0.04, K.x - hx * 0.4, y, K.z - hz * 0.4, r, g, b, a);
        }
    }

    // ---------------------------------------------------------------- shide in the wind

    private static void renderShide(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        float k = hour(1f, 0.9f, 0.45f);
        float r1 = 0.96f * k, g1 = 0.95f * k, b1 = 0.9f * k, r2 = 0.8f * k, g2 = 0.79f * k, b2 = 0.75f * k;
        double wx = Math.cos(WIND), wz = Math.sin(WIND);
        for (double[] S : SHIDE) {
            if (Math.hypot(S[0] - cam.x, S[2] - cam.z) > 60) {
                continue;
            }
            double wg = wind(S[0], S[2], t)[2];
            // the wind pushes (a mean angle) and the strip flutters (fast noise), more at the tip
            double ang = 0.15 + 0.55 * wg + 0.2 * (vnoise(t * 3 + S[3], S[4]) - 0.5) * (0.5 + wg);
            double fx = Math.cos(S[4]), fz = Math.sin(S[4]);
            double px = S[0], py = S[1], pz = S[2];
            for (int i = 0; i < 4; i++) {
                double a = ang * (1 + i * 0.35) + Math.sin(t * 9 + S[3] + i) * 0.08 * wg;
                double L = 0.17, zz = (i % 2 == 1 ? 1 : -1) * 0.07;
                double nx = px + wx * Math.sin(a) * L + fx * zz, ny = py - Math.cos(a) * L, nz = pz + wz * Math.sin(a) * L + fz * zz;
                double half = 0.055;
                boolean alt = i % 2 == 1;
                quad(out, last, cam, px - fx * half, py, pz - fz * half, px + fx * half, py, pz + fz * half, nx + fx * half, ny, nz + fz * half,
                        nx - fx * half, ny, nz - fz * half, alt ? r2 : r1, alt ? g2 : g1, alt ? b2 : b1, fade);
                px = nx;
                py = ny;
                pz = nz;
            }
        }
    }

    // ---------------------------------------------------------------- the river of petals on the stairway

    private static void makeStairPetals() {
        PETALS.clear();
        if (T == null || T.steps.size() < 4) {
            return;
        }
        for (int i = 0; i < 700; i++) {
            Petal p = new Petal();
            stairPetal(p, rnd() * (T.steps.size() - 1));
            PETALS.add(p);
        }
    }

    private static void stairPetal(Petal p, double k) {
        boolean pink = rnd() < 0.75;
        float j = (float) (0.85 + rnd() * 0.2);
        p.k = k;
        p.s = (rnd() - 0.5) * 2.8;
        p.v = 0;
        p.hop = 0;
        p.rot = rnd() * Mth.TWO_PI;
        p.r = (pink ? 0.98f : 1f) * j;
        p.g = (pink ? 0.74f : 0.94f) * j;
        p.b = (pink ? 0.82f : 0.95f) * j;
    }

    private static double stepY(double k) {
        List<int[]> S = T.steps;
        int i = Math.max(0, Math.min(S.size() - 1, (int) Math.floor(k)));
        return S.get(i)[2] + 1.01;
    }

    private static void stepStairPetals(double dt, double t) {
        if (T == null || PETALS.isEmpty()) {
            return;
        }
        List<int[]> S = T.steps;
        for (Petal p : PETALS) {
            int i = (int) Math.floor(p.k);
            int[] st = S.get(Math.min(S.size() - 1, Math.max(0, i)));
            double[] w = wind(st[0], st[1], t);
            // the gust running down the slope: drags only above a threshold (friction); the petal runs and hops the edge
            double push = Math.max(0, w[2] - 0.42) * 3.2 + (w[0] * T.fx + w[1] * T.fz > 0 ? 0.2 : 0) * w[2];
            p.v += (push - p.v) * Math.min(1, dt * 3);
            double ok = p.k;
            p.k += p.v * dt;
            p.s += (vnoise(p.k * 0.7, p.rot) - 0.5) * p.v * dt * 0.8;
            p.s = Math.max(-1.4, Math.min(1.4, p.s));
            if (Math.floor(p.k) != Math.floor(ok)) {
                p.hop = 0.001;
            }
            if (p.hop > 0) {
                p.hop += dt;
                if (p.hop > 0.28) {
                    p.hop = 0;
                }
            }
            p.rot += p.v * dt * 2;
            if (p.k > S.size() - 1.01) {
                // at the foot: lies in the heap a while and goes back to the top (falls again from the upper sakuras)
                p.k = S.size() - 1.01;
                p.v = 0;
                p.rest += dt;
                if (p.rest > 6 + rnd() * 10) {
                    stairPetal(p, rnd() * 3);
                    p.rest = 0;
                }
            }
        }
    }

    private static void renderStairPetals(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        if (T == null || PETALS.isEmpty()) {
            return;
        }
        List<int[]> S = T.steps;
        int sx = T.fz != 0 ? 1 : 0, sz = T.fx != 0 ? 1 : 0;
        float lk = hour(1f, 0.85f, 0.35f);
        for (Petal p : PETALS) {
            int i = Math.min(S.size() - 1, (int) Math.floor(p.k));
            double f = p.k - i;
            int[] st = S.get(i);
            double x = st[0] + 0.5 + T.fx * (f - 0.5) + sx * p.s, z = st[1] + 0.5 + T.fz * (f - 0.5) + sz * p.s;
            if (Math.abs(x - cam.x) > 50 || Math.abs(z - cam.z) > 50) {
                continue;
            }
            // the hop: falls from the step above to the one below in a short parabola
            double y = stepY(p.k);
            if (p.hop > 0) {
                double u = p.hop / 0.28, prev = stepY(p.k - 1);
                y = prev + (y - prev) * u * u + Math.sin(u * Math.PI) * 0.08;
            }
            ell(out, last, cam, x, y, z, Math.cos(p.rot), Math.sin(p.rot), 0.075, 0.045, p.r * lk, p.g * lk, p.b * lk, 0.95f * fade, 0.9f);
        }
    }

    // ---------------------------------------------------------------- lanterns and windows

    private static void renderLamps(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        float lit = Math.max(night, dusk);
        if (lit < 0.05f) {
            return;
        }
        double lo = Double.MAX_VALUE;
        for (double[] L : cur.lamps) {
            lo = Math.min(lo, L[1]);
        }
        for (double[] L : cur.lamps) {
            if (Math.hypot(L[0] - cam.x, L[2] - cam.z) > 120) {
                continue;
            }
            // they light from the bottom up (height decides), with a little chance
            double at = 1.5 + (L[1] - lo) * 1.6 + L[4] * 2.5;
            double u = litT - at;
            if (u < 0) {
                continue;
            }
            // lighting: a flare, then the flame settles, trembling
            double flash = u < 0.4 ? 1.6 - u * 1.5 : 1, fl = 0.8 + 0.2 * Math.sin(t * 13 + L[3]) * Math.sin(t * 7.3 + L[3] * 2);
            // the flame is inside the box: the glow goes to the block's front (or its own face hides it)
            double dx = cam.x - L[0], dy = cam.y - L[1], dz = cam.z - L[2], dd = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double x = L[0] + dx / dd * 0.62, y = L[1] + dy / dd * 0.62, z = L[2] + dz / dd * 0.62;
            disc(out, last, cam, x, y, z, 0.16 * flash, 1f, 0.82f, 0.45f, (float) (0.95 * fl) * fade * lit, 0.4f);
            disc(out, last, cam, x, y, z, 0.8 * flash, 1f, 0.6f, 0.25f, (float) (0.25 * fl) * fade * lit, 0);
        }
        if (night > 0.05f) {
            for (double[] w : WINDOWS) {
                if (Math.hypot(w[0] - cam.x, w[2] - cam.z) < 120) {
                    disc(out, last, cam, w[0], w[1], w[2], 1.2, 1f, 0.62f, 0.3f, 0.12f * night * fade, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the sea of clouds

    private static VertexBuffer cloudGrid;
    private static final int CLOUD_SEGS = 220;
    private static final double CLOUD_SIZE = 900, CLOUD_STEP = CLOUD_SIZE / CLOUD_SEGS;

    private static void drawCloud(RenderLevelStageEvent event, Vec3 cam, float fade, double t) {
        ShaderInstance sh = FxShaders.temploCloud;
        if (sh == null || FxShaders.flat() || cloudY < -900) {
            return;
        }
        // only seen from above it
        float above = (float) clamp01((cam.y - cloudY - 2) / 6);
        if (above <= 0.001f) {
            return;
        }
        if (cloudGrid == null) {
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            float h = (float) (CLOUD_SIZE / 2), s = (float) CLOUD_STEP;
            for (int i = 0; i < CLOUD_SEGS; i++) {
                for (int j = 0; j < CLOUD_SEGS; j++) {
                    float x = -h + i * s, z = -h + j * s;
                    b.addVertex(x, 0, z);
                    b.addVertex(x, 0, z + s);
                    b.addVertex(x + s, 0, z + s);
                    b.addVertex(x + s, 0, z);
                }
            }
            MeshData mesh = b.build();
            if (mesh == null) {
                return;
            }
            cloudGrid = new VertexBuffer(VertexBuffer.Usage.STATIC);
            cloudGrid.bind();
            cloudGrid.upload(mesh);
            VertexBuffer.unbind();
        }
        double ox = Math.floor(cam.x / CLOUD_STEP) * CLOUD_STEP, oz = Math.floor(cam.z / CLOUD_STEP) * CLOUD_STEP;
        // the moon lights it at night
        double sx = SUN[0], sy = SUN[1], sz = SUN[2];
        if (sy < 0) {
            sx = -sx;
            sy = -sy;
            sz = -sz;
        }
        float far = Math.min(420f, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f * 1.6f);
        sh.safeGetUniform("Origin").set((float) (ox - cam.x), (float) (-cam.y), (float) (oz - cam.z));
        sh.safeGetUniform("OriginW").set((float) ox, 0f, (float) oz);
        sh.safeGetUniform("Time").set((float) t);
        sh.safeGetUniform("CloudY").set((float) cloudY);
        sh.safeGetUniform("Wind").set((float) Math.cos(WIND), (float) Math.sin(WIND));
        sh.safeGetUniform("SunDir").set((float) sx, (float) Math.max(0.05, sy), (float) sz);
        sh.safeGetUniform("Top").set(hour(1f, 1f, 0x8e / 255f), hour(1f, 0xc6 / 255f, 0x9a / 255f), hour(1f, 0x9a / 255f, 0xb8 / 255f));
        sh.safeGetUniform("Shade").set(hour(0xae / 255f, 0x6e / 255f, 0x23 / 255f), hour(0xbc / 255f, 0x62 / 255f, 0x2c / 255f), hour(0xcc / 255f, 0x80 / 255f, 0x40 / 255f));
        sh.safeGetUniform("Alpha").set(hour(0.92f, 0.95f, 0.75f) * fade * above);
        sh.safeGetUniform("Far").set(far);
        Matrix4f view = event.getModelViewMatrix(), proj = event.getProjectionMatrix();
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            cloudGrid.bind();
            cloudGrid.drawWithShader(view, proj, sh);
            VertexBuffer.unbind();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    // ---------------------------------------------------------------- render

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
        clock = (level.getGameTime() % 240000L + partial) / 20.0;
        double t = clock;
        night = Mth.clamp((0.8f - level.getSkyDarken(partial)) / 0.6f, 0, 1);
        float a = level.getSunAngle(partial);
        SUN[0] = -Math.sin(a);
        SUN[1] = Math.cos(a);
        SUN[2] = 0;
        dusk = (float) (clamp01(1 - Math.abs(SUN[1]) / 0.35) * (1 - night));
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f look = camera.getLookVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        if (dt > 0) {
            if (Math.max(night, dusk) > 0.35f) {
                litT += dt;
            } else if (night < 0.1f && dusk < 0.1f) {
                litT = 0;
            }
            stepSmoke(dt, t);
            bellStep(dt);
            stepDoves(dt);
            stepKite(dt, t);
            stepStairPetals(dt, t);
        }
        drawCloud(event, cam, fade, t);
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer solid = buffers.getBuffer(FxRenderTypes.SOLID);
        renderCenser(solid, NULL_SINK, last, cam);
        renderBell(solid, NULL_SINK, last, cam);
        renderDoves(solid, null, last, cam, fade, true);
        buffers.endBatch(FxRenderTypes.SOLID);
        VertexConsumer area = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        renderDoves(null, area, last, cam, fade, false);
        renderKite(area, last, cam, fade);
        renderShide(area, last, cam, t, fade);
        renderStairPetals(area, last, cam, fade);
        renderSmoke(area, last, cam, fade);
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
        renderCenser(NULL_SINK, glow, last, cam);
        renderBell(NULL_SINK, glow, last, cam);
        renderLamps(glow, last, cam, t, fade);
        buffers.endBatch(FxRenderTypes.GLOW_FLAT);
    }

    // A sink that drops what is written (to draw only the solid or only the glowing part of a piece).
    private static final VertexConsumer NULL_SINK = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };

    private static void v(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        if (out == NULL_SINK) {
            return;
        }
        out.addVertex(last, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double ax, double ay, double az, double bx, double by, double bz, double cx,
            double cy, double cz, double dx, double dy, double dz, float r, float g, float b, float a) {
        v(out, last, cam, ax, ay, az, r, g, b, a);
        v(out, last, cam, bx, by, bz, r, g, b, a);
        v(out, last, cam, cx, cy, cz, r, g, b, a);
        v(out, last, cam, dx, dy, dz, r, g, b, a);
    }

    // A solid box: its base's center, the length axis (a), the width axis (b) and height h; the sides shaded.
    private static void box(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double cx, double cy, double cz, double ax, double ay, double az, double bx,
            double by, double bz, double h, float[] c, float k) {
        if (out == NULL_SINK) {
            return;
        }
        double[][] p = new double[4][];
        int q = 0;
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                p[q++] = new double[] {cx + ax * i + bx * j, cy + ay * i + by * j, cz + az * i + bz * j};
            }
        }
        double[] p00 = p[0], p01 = p[1], p10 = p[2], p11 = p[3];
        float r1 = c[0] * k, g1 = c[1] * k, b1 = c[2] * k, r2 = r1 * 0.72f, g2 = g1 * 0.72f, b2 = b1 * 0.72f, r3 = r1 * 0.5f, g3 = g1 * 0.5f, b3 = b1 * 0.5f;
        quad(out, last, cam, p00[0], p00[1] + h, p00[2], p01[0], p01[1] + h, p01[2], p11[0], p11[1] + h, p11[2], p10[0], p10[1] + h, p10[2], r1, g1, b1, 1);
        quad(out, last, cam, p00[0], p00[1], p00[2], p01[0], p01[1], p01[2], p01[0], p01[1] + h, p01[2], p00[0], p00[1] + h, p00[2], r2, g2, b2, 1);
        quad(out, last, cam, p10[0], p10[1], p10[2], p11[0], p11[1], p11[2], p11[0], p11[1] + h, p11[2], p10[0], p10[1] + h, p10[2], r2, g2, b2, 1);
        quad(out, last, cam, p00[0], p00[1], p00[2], p10[0], p10[1], p10[2], p10[0], p10[1] + h, p10[2], p00[0], p00[1] + h, p00[2], r2, g2, b2, 1);
        quad(out, last, cam, p01[0], p01[1], p01[2], p11[0], p11[1], p11[2], p11[0], p11[1] + h, p11[2], p01[0], p01[1] + h, p01[2], r2, g2, b2, 1);
        quad(out, last, cam, p00[0], p00[1], p00[2], p01[0], p01[1], p01[2], p11[0], p11[1], p11[2], p10[0], p10[1], p10[2], r3, g3, b3, 1);
    }

    private static double lookX, lookY, lookZ = 1;
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

    private static void disc(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double s, float r, float g, float b, float a, float edge) {
        if (out == NULL_SINK || a <= 0.001f) {
            return;
        }
        face(cam, x, y, z);
        double px = x + BR[0] * s, py = y + BR[1] * s, pz = z + BR[2] * s;
        for (int q = 1; q <= 12; q++) {
            double ang = q / 12.0 * Math.PI * 2, cx = Math.cos(ang) * s, cy = Math.sin(ang) * s;
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

    // A flat ellipse lying on the ground (a petal).
    private static void ell(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double ux, double uz, double ra, double rb,
            float r, float g, float b, float a, float edge) {
        double px = x + ux * ra, pz = z + uz * ra;
        for (int q = 1; q <= 8; q++) {
            double ang = q / 8.0 * Math.PI * 2, p = Math.cos(ang) * ra, s = Math.sin(ang) * rb;
            double qx = x + ux * p - uz * s, qz = z + uz * p + ux * s;
            v(out, last, cam, x, y, z, r, g, b, a);
            v(out, last, cam, px, y, pz, r, g, b, a * edge);
            v(out, last, cam, qx, y, qz, r, g, b, a * edge);
            v(out, last, cam, qx, y, qz, r, g, b, a * edge);
            px = qx;
            pz = qz;
        }
    }

    // ---------------------------------------------------------------- the screen and the fog

    public static boolean gradeOn() {
        return active() && FxShaders.jjGrade != null;
    }

    // Day: clear mountain air; dusk: warm orange; night: blue edges, never darkening.
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.jjGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        sh.safeGetUniform("Sat").set(Mth.lerp(fade, 1f, hour(1.08f, 1.12f, 1.05f)));
        sh.safeGetUniform("Contrast").set(Mth.lerp(fade, 1f, hour(1.05f, 1.04f, 1.04f)));
        sh.safeGetUniform("Wash").set(0f, 0f, 0f, 0f);
        sh.safeGetUniform("Warm").set(0f);
        sh.safeGetUniform("Vig").set(10 / 255f, 16 / 255f, 40 / 255f, 0.26f * night * fade);
        sh.safeGetUniform("Flash").set(0f);
        FxPost.pass(sh);
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float k = fade * 0.75f;
        float r = hour(0xcf / 255f, 0xd8 / 255f, 0x14 / 255f), g = hour(0xda / 255f, 0xa0 / 255f, 0x1c / 255f), b = hour(0xe0 / 255f, 0x80 / 255f, 0x2c / 255f);
        // (at night only the hue moves, never down)
        if (night > 0.5f) {
            float lum = (event.getRed() + event.getGreen() + event.getBlue()) / 3f, mine = (r + g + b) / 3f;
            if (mine < lum) {
                float s = lum / Math.max(1e-3f, mine);
                r *= s;
                g *= s;
                b *= s;
            }
        }
        event.setRed(Mth.lerp(k, event.getRed(), r));
        event.setGreen(Mth.lerp(k, event.getGreen(), g));
        event.setBlue(Mth.lerp(k, event.getBlue(), b));
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float near = 24f, far = Mth.lerp(night, 190f, 140f);
        event.setNearPlaneDistance(Mth.lerp(fade, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), near)));
        event.setFarPlaneDistance(Mth.lerp(fade, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), far)));
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- sound

    private static void play(ClientLevel level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playLocalSound(x, y, z, s, SoundSource.AMBIENT, vol * strength, pitch, false);
    }

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 p) {
        if (windLoop == null || !mc.getSoundManager().isActive(windLoop)) {
            windLoop = new WindLoop();
            mc.getSoundManager().play(windLoop);
        }
        double t = level.getGameTime() / 20.0, dt = 0.05;
        windGust = (float) wind(p.x, p.z, t)[2];
        if (bellNow) {
            bellNow = false;
            if (bellLocal && T != null && T.belfry != null) {
                play(level, T.belfry[0] + 0.5, T.y + 4.5, T.belfry[1] + 0.5, ModSounds.TEMPLO_SINO.get(), 5f, 1f);
            } else {
                double a = rnd() * Mth.TWO_PI;
                play(level, p.x + Math.cos(a) * 30, p.y + 8, p.z + Math.sin(a) * 30, ModSounds.TEMPLO_SINO.get(), 1.4f, 1f);
            }
        }
        if (wingsNow) {
            wingsNow = false;
            if (T != null) {
                play(level, T.cx + 0.5, T.y + 8, T.cz + 0.5, ModSounds.TEMPLO_ASAS.get(), 2.5f, 0.95f + (float) rnd() * 0.1f);
            }
        }
        if (kiteCall) {
            kiteCall = false;
            if (kite != null) {
                play(level, kite.x, kite.y, kite.z, ModSounds.TEMPLO_TOBI.get(), 5f, 0.95f + (float) rnd() * 0.1f);
            }
        }
        if (night < 0.5f) {
            nextBird -= dt;
            if (nextBird < 0) {
                // the bush warbler, somewhere in the woods
                double a = rnd() * Mth.TWO_PI, r = 10 + rnd() * 12;
                play(level, p.x + Math.cos(a) * r, p.y + 4, p.z + Math.sin(a) * r, ModSounds.TEMPLO_UGUISU.get(), 1.4f, 0.97f + (float) rnd() * 0.06f);
                nextBird = 9 + rnd() * 12;
            }
        } else {
            nextOwl -= dt;
            if (nextOwl < 0) {
                double a = rnd() * Mth.TWO_PI, r = 14 + rnd() * 14;
                play(level, p.x + Math.cos(a) * r, p.y + 8, p.z + Math.sin(a) * r, ModSounds.TEMPLO_CORUJA.get(), 1.6f, 0.95f + (float) rnd() * 0.1f);
                nextOwl = 8 + rnd() * 10;
            }
        }
    }

    // The low wind in the cedars, all round the player, rising with the gusts.
    private static final class WindLoop extends AbstractTickableSoundInstance {
        WindLoop() {
            super(ModSounds.TEMPLO_VENTO.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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
            volume = (0.25f + 0.75f * windGust) * 0.6f * strength;
            pitch = 0.95f + 0.1f * windGust;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
