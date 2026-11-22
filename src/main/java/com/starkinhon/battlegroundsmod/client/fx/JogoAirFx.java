// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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

// The air of the Terras do Jogo (Stark 2026-11-19 on the preview forja/jujutsu-jogo-ambiente). amb.js there is the source
// of truth and this is its port, the same names where it helps. Fades in when the player walks into the biome and out
// when the player leaves (BiomeAura); the vultures stay JujutsuAirFx's. No texture and no thin line: each cloud has its
// own outline, each stone is a cube. Everything makes physical sense:
// - the column rises by buoyancy, widens with height (air drawn into it) and opens into an umbrella where the buoyancy
//   ends; the old umbrella lets fine ash fall in wide faint curtains;
// - the bombs are ballistic (gravity 9.8 blocks/s^2, little drag), bounce with loss and cool by Newton's law, changing
//   color like a black body (yellow, orange, red, black stone); a small stone cools faster;
// - the lava lake and the rivers bubble: the dome swells, its crust stretches and bursts throwing drops;
// - a fumarole's steam is only seen a hand above the mouth (hot steam is clear until it condenses);
// - the sulfur gas is heavier than air: it gathers in the hollows and does not rise;
// - the lightning stays in the low part of the column, where the ash rubs (volcanic lightning), and lights the cloud
//   from inside;
// - the sound comes late with the distance (343 blocks/s);
// - the screen (fx_jjgrade): sun filtered through ash by day; at night the lava's glow from below (never darkening).
public final class JogoAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double G = 9.8, WIND = 0.6;
    private static final int H = 62, CR = 17;
    private static final double HN = H * 0.25 + 46;
    private static final int SCAN = 64, FAR = 192, FAR_STEP = 4;
    private static final int CELLS = 56, GSIZE = 2;

    private static float strength, lastStrength;
    private static float night;
    private static long lastNanos;
    private static ClientLevel lastLevel;
    private static double lookX, lookY, lookZ = 1;
    private static double wx = 1, wz = 0;

    // ---------------------------------------------------------------- what the land has

    // the volcano: its lava lake (read from far, coarse); the rivers and the fumaroles near (a row of columns a tick)
    private static double vcx, vcz;
    private static int lakeY;
    private static boolean volcano;

    private static final class Mouth {
        double x, y, z, ph, acc, pow;
    }

    private static final class Scan {
        final List<double[]> lava = new ArrayList<>();
        final List<double[]> lake = new ArrayList<>();
        final List<Mouth> mouths = new ArrayList<>();

        void clear() {
            lava.clear();
            lake.clear();
            mouths.clear();
        }
    }

    private static Scan cur = new Scan(), next = new Scan();
    private static int scanRow = -SCAN - 1, scanCx, scanCz;
    private static boolean firstPass = true;
    private static final Int2IntOpenHashMap FAR_LAVA = new Int2IntOpenHashMap();
    private static double farSx, farSz;
    private static int farRow = -FAR - FAR_STEP, farCx, farCz;

    // ---------------------------------------------------------------- moving things

    private static final class Cloud {
        double x, y, z, vx, vy, vz, age, life, um, tone, rot, spin, s0, ox, oz, my, grow;
        final double[] r = new double[9];
        boolean jet;
        int kind; // 0 the column, 1 steam, 2 dust
        double d2;
    }

    private static final List<Cloud> PLUME = new ArrayList<>(), STEAM = new ArrayList<>(), DUST = new ArrayList<>();
    private static double plumeAcc;
    private static boolean plumeUp;

    private static final class Bomb {
        double x, y, z, vx, vy, vz, s, T, age, ax, ay, az, ang, spin, trail, restAt;
        boolean rest, dead;
        int bounces;
    }

    private static final List<Bomb> BOMBS = new ArrayList<>();

    private static final class Drop {
        double x, y, z, vx, vy, vz, s, T, age, restAt;
        boolean rest;
    }

    private static final List<Drop> DROPS = new ArrayList<>();

    private static final class Bubble {
        double x, y, z, rmax, grow, age;
        boolean popped;
    }

    private static final List<Bubble> BUBBLES = new ArrayList<>();
    private static double bubAcc;

    private static double nextBurst = 14, nextBolt = 9;
    private static double burstT = -1, burstX, burstY, burstZ, burstD, burstAmt;
    private static boolean boomed;
    private static double boltT = -1, boltX, boltY, boltZ, boltR, boltD, boltAmt;
    private static final double[] STROKES = new double[3];
    private static int strokes;
    private static boolean thundered;
    private static float shake;

    // the sulfur gas: a grid that follows the ground
    private static final int GW = CELLS + 1;
    private static final double[] GY = new double[GW * GW], GLOW_ = new double[GW * GW];
    private static int ggx, ggz;
    private static boolean gMeasured;
    private static double gAt = -100, gfx = 1e9, gfz = 1e9;

    // sounds
    private static Loop rumble, hiss, bubbling;
    private static float rumbleVol, hissVol, bubVol;

    private static final class Pending {
        double at;
        SoundEvent s;
        double x, y, z;
        float vol, pitch;
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static double clock;

    private JogoAirFx() {
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

    // the real top (the client's MOTION_BLOCKING heightmap: lava counts)
    private static int top(ClientLevel level, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!level.hasChunk(ix >> 4, iz >> 4)) {
            return -999;
        }
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz) - 1;
        return h <= level.getMinBuildHeight() ? -999 : h;
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    // ---------------------------------------------------------------- tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            if (lastStrength > 0 || volcano || !cur.lava.isEmpty()) {
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
        boolean in = BiomeAura.in("terras_do_jogo_ar", b -> b.is(ModWorldgen.TERRAS_DO_JOGO));
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
        for (int i = 0; i < (volcano ? 1 : 4); i++) {
            farStep(level, p);
        }
        Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        measureGround(level, c);
        sounds(mc, level, c);
    }

    private static void scanStep(ClientLevel level, Vec3 p) {
        if (scanRow > SCAN) {
            // the fumaroles keep their rhythm from pass to pass (matched by place)
            for (Mouth m : next.mouths) {
                for (Mouth o : cur.mouths) {
                    if (o.x == m.x && o.z == m.z) {
                        m.ph = o.ph;
                        m.acc = o.acc;
                        m.pow = o.pow;
                        break;
                    }
                }
            }
            Scan done = next;
            next = cur;
            cur = done;
            next.clear();
            firstPass = false;
            scanRow = -SCAN - 1;
        }
        if (scanRow == -SCAN - 1) {
            scanCx = Mth.floor(p.x);
            scanCz = Mth.floor(p.z);
            scanRow = -SCAN;
        }
        int x = scanCx + scanRow++;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Block crack = JujutsuBlocks.MAGMA_CRACKED_BASALT.get(), sulfur = JujutsuBlocks.SULFUR.get();
        for (int z = scanCz - SCAN; z <= scanCz + SCAN; z++) {
            int g = top(level, x, z);
            if (g < -900) {
                continue;
            }
            BlockState s = level.getBlockState(pos.set(x, g, z));
            if (s.getFluidState().is(FluidTags.LAVA)) {
                double[] c = {x + 0.5, g + 0.9, z + 0.5};
                if (volcano && Math.hypot(x - vcx, z - vcz) < CR) {
                    next.lake.add(c);
                } else {
                    next.lava.add(c);
                }
            } else if (s.is(crack)) {
                // a fumarole's mouth: the magma crack at the bottom of a well of air ringed with sulfur
                if (level.getBlockState(pos.set(x + 1, g + 1, z)).is(sulfur) || level.getBlockState(pos.set(x - 1, g + 1, z)).is(sulfur)
                        || level.getBlockState(pos.set(x, g + 1, z + 1)).is(sulfur) || level.getBlockState(pos.set(x, g + 1, z - 1)).is(sulfur)) {
                    int yy = g + 1;
                    while (yy < g + 8 && (!level.getBlockState(pos.set(x + 1, yy, z)).isAir() || !level.getBlockState(pos.set(x - 1, yy, z)).isAir()
                            || !level.getBlockState(pos.set(x, yy, z + 1)).isAir() || !level.getBlockState(pos.set(x, yy, z - 1)).isAir())) {
                        yy++;
                    }
                    Mouth m = new Mouth();
                    m.x = x + 0.5;
                    m.y = yy;
                    m.z = z + 0.5;
                    m.ph = rnd() * Mth.TWO_PI;
                    m.pow = 0.7 + rnd() * 0.5;
                    next.mouths.add(m);
                }
            }
        }
    }

    // The volcano read from far: the lava tops counted by height on a coarse grid; the crater's lake is the height with
    // a broad disc of them.
    private static void farStep(ClientLevel level, Vec3 p) {
        if (farRow > FAR) {
            int bestY = 0, bestN = 0;
            for (Int2IntMap.Entry e : FAR_LAVA.int2IntEntrySet()) {
                if (e.getIntValue() > bestN) {
                    bestN = e.getIntValue();
                    bestY = e.getIntKey();
                }
            }
            if (bestN >= 30) {
                // the middle of the lava at that height
                double sx = 0, sz = 0;
                int n = 0;
                for (int i = -FAR; i <= FAR; i += FAR_STEP) {
                    for (int j = -FAR; j <= FAR; j += FAR_STEP) {
                        int x = farCx + i, z = farCz + j;
                        if (top(level, x, z) == bestY && level.getBlockState(new BlockPos(x, bestY, z)).getFluidState().is(FluidTags.LAVA)) {
                            sx += x + 0.5;
                            sz += z + 0.5;
                            n++;
                        }
                    }
                }
                if (n >= 30) {
                    double nx = sx / n, nz = sz / n;
                    if (!volcano || Math.hypot(nx - vcx, nz - vcz) > 3 || lakeY != bestY) {
                        boolean moved = volcano && Math.hypot(nx - vcx, nz - vcz) > 40;
                        vcx = nx;
                        vcz = nz;
                        lakeY = bestY;
                        if (!volcano || moved) {
                            PLUME.clear();
                            plumeUp = false;
                        }
                        volcano = true;
                    }
                }
            }
            FAR_LAVA.clear();
            farRow = -FAR - FAR_STEP;
        }
        if (farRow == -FAR - FAR_STEP) {
            farCx = Mth.floor(p.x / FAR_STEP) * FAR_STEP;
            farCz = Mth.floor(p.z / FAR_STEP) * FAR_STEP;
            farRow = -FAR;
        }
        int x = farCx + farRow;
        farRow += FAR_STEP;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int j = -FAR; j <= FAR; j += FAR_STEP) {
            int z = farCz + j, g = top(level, x, z);
            if (g < -900) {
                continue;
            }
            if (level.getBlockState(pos.set(x, g, z)).getFluidState().is(FluidTags.LAVA)) {
                FAR_LAVA.addTo(g, 1);
            }
        }
    }

    // "a hollow" = below the neighbors' average within ~12 blocks; no gas over lava nor in the crater
    private static void measureGround(ClientLevel level, Vec3 f) {
        double t = level.getGameTime() / 20.0;
        if (gMeasured && t - gAt < 4 && Math.hypot(f.x - gfx, f.z - gfz) <= GSIZE * 4) {
            return;
        }
        gAt = t;
        gfx = f.x;
        gfz = f.z;
        int gx = Mth.floor(f.x / GSIZE) * GSIZE - CELLS * GSIZE / 2, gz = Mth.floor(f.z / GSIZE) * GSIZE - CELLS * GSIZE / 2;
        int[] hgt = new int[GW * GW];
        for (int i = 0; i <= CELLS; i++) {
            for (int j = 0; j <= CELLS; j++) {
                hgt[i * GW + j] = top(level, gx + i * GSIZE, gz + j * GSIZE);
            }
        }
        int w = Math.max(2, Math.round(12f / GSIZE));
        for (int i = 0; i <= CELLS; i++) {
            for (int j = 0; j <= CELLS; j++) {
                int k = i * GW + j, y = hgt[k];
                if (y < -900) {
                    GY[k] = -999;
                    GLOW_[k] = 0;
                    continue;
                }
                double s = 0;
                int c = 0;
                for (int a = -w; a <= w; a += 2) {
                    for (int b = -w; b <= w; b += 2) {
                        int ii = i + a, jj = j + b;
                        if (ii < 0 || jj < 0 || ii > CELLS || jj > CELLS) {
                            continue;
                        }
                        int yy = hgt[ii * GW + jj];
                        if (yy > -900) {
                            s += yy;
                            c++;
                        }
                    }
                }
                double low = clamp01((s / c - y - 0.5) / 4);
                int x = gx + i * GSIZE, z = gz + j * GSIZE;
                boolean crater = volcano && Math.hypot(x - vcx, z - vcz) < CR + 4;
                boolean lava = level.getBlockState(new BlockPos(x, y, z)).getFluidState().is(FluidTags.LAVA);
                GY[k] = y + 1;
                GLOW_[k] = crater || lava ? 0 : low;
            }
        }
        ggx = gx;
        ggz = gz;
        gMeasured = true;
    }

    private static void reset(boolean all) {
        strength = 0;
        firstPass = true;
        scanRow = -SCAN - 1;
        next = new Scan();
        PLUME.clear();
        STEAM.clear();
        DUST.clear();
        BOMBS.clear();
        DROPS.clear();
        BUBBLES.clear();
        PENDING.clear();
        plumeUp = false;
        burstT = boltT = -1;
        shake = 0;
        gMeasured = false;
        for (Loop l : new Loop[] {rumble, hiss, bubbling}) {
            if (l != null) {
                l.end();
            }
        }
        rumble = hiss = bubbling = null;
        if (all) {
            cur = new Scan();
            volcano = false;
            FAR_LAVA.clear();
            farRow = -FAR - FAR_STEP;
        }
    }

    // ---------------------------------------------------------------- the column

    private static Cloud plumePuff(boolean jet) {
        double a = rnd() * Mth.TWO_PI, rr = Math.sqrt(rnd()) * (jet ? 5 : 8);
        Cloud p = new Cloud();
        for (int k = 0; k < 9; k++) {
            p.r[k] = 0.6 + rnd() * 0.55;
        }
        p.x = vcx + Math.cos(a) * rr;
        p.y = lakeY + 1.5;
        p.z = vcz + Math.sin(a) * rr;
        p.vy = jet ? 26 + rnd() * 12 : 10 + rnd() * 5;
        p.life = 60 + rnd() * 35;
        p.tone = 0.3 + rnd() * 0.14;
        p.rot = rnd() * Mth.TWO_PI;
        p.spin = (rnd() - 0.5) * 0.25;
        p.jet = jet;
        p.s0 = jet ? 4.5 : 3.5 + rnd() * 2.5;
        return p;
    }

    private static double windAt(double h) {
        return 0.5 + 5.5 * Math.pow(clamp01(h / HN), 1.5);
    }

    private static void simPlume(Cloud p, double dt) {
        double h = p.y - lakeY;
        p.age += dt;
        // buoyancy: the speed the column "wants" falls with height and ends at HN
        double want = h < HN ? 14 * (1 - (h / HN) * (h / HN)) + 0.8 : 0;
        p.vy += (want - p.vy) * dt * (p.jet && p.age < 2 ? 0.25 : 0.7);
        if (h > HN - 6) {
            // the umbrella: spreads sideways, losing strength with time
            p.um += dt;
            double dx = p.x - vcx, dz = p.z - vcz, l = Math.max(1e-3, Math.hypot(dx, dz));
            double out = 4.5 * Math.exp(-p.um / 20);
            p.vx += (dx / l * out - p.vx * 0.15) * dt;
            p.vz += (dz / l * out - p.vz * 0.15) * dt;
            if (h > HN + 4) {
                // past the point: comes back (the top sinks a little)
                p.vy -= (h - HN - 4) * 0.4 * dt;
            }
        }
        double wv = windAt(h);
        p.x += (p.vx + wx * wv + Math.sin(p.age * 0.3 + p.rot) * 0.4) * dt;
        p.z += (p.vz + wz * wv + Math.cos(p.age * 0.27 + p.rot) * 0.4) * dt;
        p.y += p.vy * dt;
        p.rot += p.spin * dt;
    }

    // the column widens ~0.2 a block of rise (outside air drawn in); in the umbrella it grows more sideways
    private static double plumeR(Cloud p) {
        double h = Math.max(0, p.y - lakeY);
        return p.s0 + Math.min(h, HN) * 0.2 + p.um * 0.7;
    }

    // ---------------------------------------------------------------- the strombolian blast and the bombs

    private static void eruption(Vec3 cam) {
        double cx = vcx, cy = lakeY + 1, cz = vcz;
        int n = 22 + RANDOM.nextInt(16);
        for (int i = 0; i < n; i++) {
            double sp = 20 + rnd() * 20, el = Math.toRadians(62 + rnd() * 26), az = rnd() * Mth.TWO_PI, s = 0.25 + Math.pow(rnd(), 2) * 0.9;
            Bomb b = new Bomb();
            b.x = cx + (rnd() - 0.5) * 6;
            b.y = cy;
            b.z = cz + (rnd() - 0.5) * 6;
            b.vx = Math.cos(az) * Math.cos(el) * sp;
            b.vy = Math.sin(el) * sp;
            b.vz = Math.sin(az) * Math.cos(el) * sp;
            b.s = s;
            b.T = 1080 + rnd() * 80;
            b.ax = rnd() - 0.5;
            b.ay = rnd() - 0.5;
            b.az = rnd() - 0.5;
            b.spin = (2 + rnd() * 6) * (rnd() < 0.5 ? -1 : 1) / (0.5 + s);
            BOMBS.add(b);
        }
        // the blast's jet of ash joins the column
        for (int i = 0; i < 40; i++) {
            PLUME.add(plumePuff(true));
        }
        // big splashes from the lake
        for (int i = 0; i < 60; i++) {
            double a = rnd() * Mth.TWO_PI, r = rnd() * 6;
            spawnDrop(cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, 6 + rnd() * 10, 0.08 + rnd() * 0.12);
        }
        burstD = Math.sqrt((cam.x - cx) * (cam.x - cx) + (cam.y - cy) * (cam.y - cy) + (cam.z - cz) * (cam.z - cz));
        burstT = 0;
        burstX = cx;
        burstY = cy;
        burstZ = cz;
        boomed = false;
        nextBurst = 28 + rnd() * 26;
        // the new ash charges the column
        nextBolt = Math.min(nextBolt, 1.5 + rnd() * 2);
    }

    // a black body's color, roughly (in degrees C): r, g, b, intensity in HOT
    private static final double[] HOT = new double[4];

    private static double[] hot(double T) {
        if (T < 520) {
            HOT[0] = HOT[1] = HOT[2] = HOT[3] = 0;
            return HOT;
        }
        HOT[3] = Math.pow(clamp01((T - 520) / 600), 1.6);
        HOT[0] = 1;
        HOT[1] = 0.1 + 0.75 * clamp01((T - 720) / 520);
        HOT[2] = 0.02 + 0.5 * clamp01((T - 980) / 300);
        return HOT;
    }

    private static void stepBomb(ClientLevel level, Bomb b, Vec3 cam, double dt) {
        b.age += dt;
        // cools: Newton, faster for a small stone (area/volume); on the ground less (lying on hot ash)
        double k = (b.rest ? 0.018 : 0.06) / (0.4 + b.s);
        b.T += (25 - b.T) * k * dt;
        if (b.rest) {
            return;
        }
        double v = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz), drag = 0.004 / b.s * v;
        b.vx -= b.vx * drag * dt;
        b.vy -= (G + b.vy * drag) * dt;
        b.vz -= b.vz * drag * dt;
        b.x += b.vx * dt;
        b.y += b.vy * dt;
        b.z += b.vz * dt;
        b.ang += b.spin * dt;
        // a trail of smoke (clouds, not a line) while it is hot
        b.trail += dt * (b.T > 700 ? 14 : 4) * b.s;
        while (b.trail > 1) {
            b.trail -= 1;
            DUST.add(dustPuff(b.x, b.y, b.z, 0.25 + b.s * 0.4, 0.6, b.T > 800 ? 0.3 : 0.22, 2.5 + rnd() * 2));
        }
        int gy = top(level, b.x, b.z);
        if (gy < -900) {
            b.dead = true;
            return;
        }
        if (b.y - b.s / 2 <= gy + 1) {
            b.y = gy + 1 + b.s / 2;
            // impact dust in proportion to the energy
            double e = Math.min(1, (b.vy * b.vy) / 600) * (0.4 + b.s);
            for (int i = 0; i < 3 + e * 8; i++) {
                DUST.add(dustPuff(b.x + (rnd() - 0.5) * b.s * 2, b.y, b.z + (rnd() - 0.5) * b.s * 2, 0.5 + b.s, 1.2 + e * 2, 0.32, 3 + rnd() * 3));
            }
            double d = Math.sqrt((b.x - cam.x) * (b.x - cam.x) + (b.y - cam.y) * (b.y - cam.y) + (b.z - cam.z) * (b.z - cam.z));
            if (d < 160) {
                later(d / 343, ModSounds.JOGO_BAQUE.get(), b.x, b.y, b.z, (float) (Math.min(1, b.s + 0.2) * 1.6), 0.9f + (float) rnd() * 0.2f);
            }
            if (Math.abs(b.vy) > 5 && b.bounces < 2) {
                // bounces with loss (soft ash holds a lot) and slides a little down the slope
                double gx = top(level, b.x + 1, b.z) - top(level, b.x - 1, b.z), gz = top(level, b.x, b.z + 1) - top(level, b.x, b.z - 1);
                b.vy = -b.vy * 0.28;
                b.vx = b.vx * 0.45 - gx * 0.8;
                b.vz = b.vz * 0.45 - gz * 0.8;
                b.bounces++;
                b.spin *= 0.6;
            } else {
                b.rest = true;
                b.vx = b.vy = b.vz = 0;
                b.restAt = b.age;
            }
        }
    }

    private static void spawnDrop(double x, double y, double z, double sp, double s) {
        double a = rnd() * Mth.TWO_PI, el = Math.toRadians(55 + rnd() * 33);
        Drop d = new Drop();
        d.x = x;
        d.y = y;
        d.z = z;
        d.vx = Math.cos(a) * Math.cos(el) * sp;
        d.vy = Math.sin(el) * sp;
        d.vz = Math.sin(a) * Math.cos(el) * sp;
        d.s = s;
        d.T = 1100;
        DROPS.add(d);
    }

    private static Cloud dustPuff(double x, double y, double z, double s0, double grow, double tone, double life) {
        Cloud p = new Cloud();
        for (int k = 0; k < 9; k++) {
            p.r[k] = 0.6 + rnd() * 0.5;
        }
        p.kind = 2;
        p.x = x;
        p.y = y;
        p.z = z;
        p.vy = 0.4 + rnd() * 0.6;
        p.s0 = s0;
        p.grow = grow;
        p.tone = tone * (0.85 + rnd() * 0.3);
        p.life = life;
        p.rot = rnd() * Mth.TWO_PI;
        return p;
    }

    // ---------------------------------------------------------------- lava bubbles and fumaroles

    private static void spawnBubble(List<double[]> list, boolean big, Vec3 f) {
        if (list.isEmpty()) {
            return;
        }
        for (int tries = 0; tries < 6; tries++) {
            double[] c = list.get(RANDOM.nextInt(list.size()));
            if (Math.abs(c[0] - f.x) > 48 || Math.abs(c[2] - f.z) > 48) {
                continue;
            }
            Bubble b = new Bubble();
            b.x = c[0] + (rnd() - 0.5) * 0.6;
            b.y = c[1];
            b.z = c[2] + (rnd() - 0.5) * 0.6;
            b.rmax = (big ? 0.5 : 0.25) + rnd() * (big ? 0.9 : 0.45);
            b.grow = 0.5 + rnd() * 0.8;
            BUBBLES.add(b);
            return;
        }
    }

    private static double pulse(Mouth m, double t) {
        double a = 0.5 + 0.5 * Math.sin(t * 0.8 + m.ph), b = vnoise(t * 0.35 + m.ph * 10, m.ph);
        return 0.35 + 0.65 * Math.pow(a, 0.6) * (0.6 + 0.4 * b);
    }

    private static Cloud steamPuff(Mouth m, double p) {
        Cloud c = new Cloud();
        for (int k = 0; k < 9; k++) {
            c.r[k] = 0.6 + rnd() * 0.5;
        }
        c.kind = 1;
        c.my = m.y;
        c.x = m.x + (rnd() - 0.5) * 0.4;
        c.y = m.y - 0.6;
        c.z = m.z + (rnd() - 0.5) * 0.4;
        c.vy = 3.5 + 3.5 * p;
        c.life = 4.5 + rnd() * 4;
        c.rot = rnd() * Mth.TWO_PI;
        c.spin = (rnd() - 0.5) * 0.8;
        c.ox = (rnd() - 0.5) * 0.5;
        c.oz = (rnd() - 0.5) * 0.5;
        return c;
    }

    private static void simSteam(Cloud p, double dt) {
        p.age += dt;
        // out in a jet, the drag slows it to the steam's rising speed (buoyancy), the wind lays the plume down
        p.vy += (1.3 - p.vy) * dt * 1.6;
        double rise = p.y - p.my, wv = 1 + 2.2 * clamp01(rise / 6);
        p.x += (wx * wv + p.ox) * dt;
        p.z += (wz * wv + p.oz) * dt;
        p.y += p.vy * dt;
        p.rot += p.spin * dt;
    }

    private static void step(ClientLevel level, Vec3 f, double dt, double t) {
        double dir = WIND + Math.sin(t * 0.03) * 0.3;
        wx = Math.cos(dir);
        wz = Math.sin(dir);
        if (volcano) {
            if (!plumeUp) {
                // the column already standing when the player arrives: each cloud born at a random age and run up to it
                for (int i = 0; i < 700; i++) {
                    Cloud p = plumePuff(false);
                    double age = rnd() * p.life;
                    for (double s = 0; s < age; s += 0.5) {
                        simPlume(p, 0.5);
                    }
                    PLUME.add(p);
                }
                plumeUp = true;
            }
            plumeAcc += dt * 9;
            while (plumeAcc > 1) {
                plumeAcc -= 1;
                PLUME.add(plumePuff(false));
            }
            for (int i = PLUME.size() - 1; i >= 0; i--) {
                Cloud p = PLUME.get(i);
                simPlume(p, dt);
                if (p.age >= p.life || Math.hypot(p.x - vcx, p.z - vcz) >= 420) {
                    PLUME.set(i, PLUME.get(PLUME.size() - 1));
                    PLUME.remove(PLUME.size() - 1);
                }
            }
            nextBurst -= dt;
            if (nextBurst < 0 && Math.hypot(f.x - vcx, f.z - vcz) < 400) {
                eruption(f);
            }
            nextBolt -= dt;
            if (nextBolt < 0) {
                // volcanic lightning: in the low/middle column, where the most ash rubs
                List<Cloud> cand = new ArrayList<>();
                for (Cloud p : PLUME) {
                    double h = p.y - lakeY;
                    if (p.um == 0 && h > 8 && h < 45 && Math.hypot(p.x - vcx, p.z - vcz) < 28) {
                        cand.add(p);
                    }
                }
                if (!cand.isEmpty()) {
                    Cloud p = cand.get(RANDOM.nextInt(cand.size()));
                    boltX = p.x;
                    boltY = p.y;
                    boltZ = p.z;
                    boltT = 0;
                    boltR = 26 + rnd() * 14;
                    STROKES[0] = 0;
                    STROKES[1] = 0.07 + rnd() * 0.05;
                    STROKES[2] = 0.2 + rnd() * 0.12;
                    strokes = 1 + RANDOM.nextInt(3);
                    boltD = Math.sqrt((p.x - f.x) * (p.x - f.x) + (p.y - f.y) * (p.y - f.y) + (p.z - f.z) * (p.z - f.z));
                    thundered = false;
                }
                nextBolt = 7 + rnd() * 16;
            }
        }
        if (boltT >= 0) {
            boltT += dt;
            double k = 0;
            for (int i = 0; i < strokes; i++) {
                if (boltT >= STROKES[i]) {
                    k = Math.max(k, Math.exp(-(boltT - STROKES[i]) / 0.06));
                }
            }
            boltAmt = k;
            if (!thundered && boltT > boltD / 343) {
                thundered = true;
                float kk = (float) Math.max(0.25, 1 - boltD / 600);
                play(boltX, boltY, boltZ, ModSounds.JOGO_TROVAO.get(), 1.2f + 3f * kk, 0.9f + (float) rnd() * 0.2f, f);
            }
            if (boltT > Math.max(1, boltD / 343 + 0.2)) {
                boltT = -1;
                boltAmt = 0;
            }
        }
        if (burstT >= 0) {
            burstT += dt;
            burstAmt = burstT < 0.08 ? burstT / 0.08 : Math.exp(-(burstT - 0.08) * 2.2);
            if (!boomed && burstT > burstD / 343) {
                boomed = true;
                shake = (float) Math.max(shake, clamp01(1 - burstD / 260));
                float k = (float) Math.max(0.3, 1 - burstD / 600);
                play(burstX, burstY, burstZ, ModSounds.JOGO_EXPLOSAO.get(), 1.5f + 4f * k, 0.95f + (float) rnd() * 0.1f, f);
            }
            if (burstT > Math.max(4, burstD / 343 + 1)) {
                burstT = -1;
                burstAmt = 0;
            }
        }
        shake = (float) Math.max(0, shake - dt * 1.4);
        for (int i = BOMBS.size() - 1; i >= 0; i--) {
            Bomb b = BOMBS.get(i);
            stepBomb(level, b, f, dt);
            // a cold stone goes only after a while on the ground
            if (b.dead || b.rest && b.age - b.restAt > 50 && b.T < 300) {
                BOMBS.remove(i);
            }
        }
        for (int i = DROPS.size() - 1; i >= 0; i--) {
            Drop d = DROPS.get(i);
            d.age += dt;
            d.T += (25 - d.T) * (d.rest ? 0.3 : 0.9) / (d.s * 8) * dt;
            if (!d.rest) {
                d.vy -= G * dt;
                d.x += d.vx * dt;
                d.y += d.vy * dt;
                d.z += d.vz * dt;
                int gy = top(level, d.x, d.z);
                if (gy < -900 || d.y <= gy + 1) {
                    d.y = gy + 1.01;
                    d.rest = true;
                    d.restAt = d.age;
                }
            }
            if (d.rest && d.age - d.restAt > 6 || d.age >= 20) {
                DROPS.remove(i);
            }
        }
        // bubbles: many in the lake, some in the rivers; they swell, the crust stretches and bursts throwing drops
        bubAcc += dt * ((cur.lake.isEmpty() ? 0 : 9) + Math.min(14, cur.lava.size() / 60.0));
        while (bubAcc > 1) {
            bubAcc -= 1;
            if (!cur.lake.isEmpty() && rnd() < 0.45) {
                spawnBubble(cur.lake, true, f);
            } else {
                spawnBubble(cur.lava, false, f);
            }
        }
        for (int i = BUBBLES.size() - 1; i >= 0; i--) {
            Bubble b = BUBBLES.get(i);
            b.age += dt;
            if (!b.popped && b.age > b.grow) {
                b.popped = true;
                int n = (int) Math.round(4 + b.rmax * 16);
                for (int k = 0; k < n; k++) {
                    spawnDrop(b.x, b.y + b.rmax * 0.5, b.z, 2 + rnd() * 3.5 * (0.6 + b.rmax), 0.05 + rnd() * 0.07);
                }
                double d = Math.sqrt((b.x - f.x) * (b.x - f.x) + (b.y - f.y) * (b.y - f.y) + (b.z - f.z) * (b.z - f.z));
                if (d < 40) {
                    later(d / 343, ModSounds.JOGO_PLOP.get(), b.x, b.y, b.z, 0.8f, (float) Mth.clamp(0.6 + 0.4 / (0.3 + b.rmax), 0.6, 1.8));
                }
            }
            if (b.age >= b.grow + 0.25) {
                BUBBLES.remove(i);
            }
        }
        // the fumaroles near: only the 16 nearest, up to 130 blocks
        List<Mouth> ms = new ArrayList<>();
        for (Mouth m : cur.mouths) {
            if (Math.hypot(m.x - f.x, m.z - f.z) < 130) {
                ms.add(m);
            }
        }
        ms.sort((a, b) -> Double.compare(Math.hypot(a.x - f.x, a.z - f.z), Math.hypot(b.x - f.x, b.z - f.z)));
        for (int i = 0; i < Math.min(16, ms.size()); i++) {
            Mouth m = ms.get(i);
            double p = pulse(m, t);
            m.acc += dt * 8 * p * m.pow;
            while (m.acc > 1) {
                m.acc -= 1;
                STEAM.add(steamPuff(m, p));
            }
        }
        for (int i = STEAM.size() - 1; i >= 0; i--) {
            Cloud p = STEAM.get(i);
            simSteam(p, dt);
            if (p.age >= p.life) {
                STEAM.set(i, STEAM.get(STEAM.size() - 1));
                STEAM.remove(STEAM.size() - 1);
            }
        }
        for (int i = DUST.size() - 1; i >= 0; i--) {
            Cloud d = DUST.get(i);
            d.age += dt;
            d.y += d.vy * dt;
            d.vy *= 1 - dt * 0.5;
            d.x += wx * 1.2 * dt;
            d.z += wz * 1.2 * dt;
            if (d.age >= d.life) {
                DUST.set(i, DUST.get(DUST.size() - 1));
                DUST.remove(DUST.size() - 1);
            }
        }
        if (DUST.size() > 3000) {
            DUST.subList(0, DUST.size() - 3000).clear();
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
        double t = (level.getGameTime() % 240000L + partial) / 20.0;
        night = Mth.clamp((0.8f - level.getSkyDarken(partial)) / 0.6f, 0, 1);
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
        VertexConsumer solid = buffers.getBuffer(FxRenderTypes.SOLID);
        for (Bomb b : BOMBS) {
            drawBomb(solid, last, cam, b);
        }
        for (Drop d : DROPS) {
            if (!d.rest && hot(d.T)[3] <= 0.02) {
                float c = Mth.lerp(night, 0.12f, 0.05f);
                cube(solid, last, cam, d.x, d.y, d.z, d.s, 1, 1, 0, d.age * 6, c, c, c, true);
            }
        }
        for (Bubble b : BUBBLES) {
            if (!b.popped) {
                drawBubble(solid, last, cam, b);
            }
        }
        buffers.endBatch(FxRenderTypes.SOLID);
        VertexConsumer area = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        // "Partículas do ambiente" off (Experiência do jogador): the gas and the ash curtains go; the volcano stays
        if (FxConfig.ambient()) {
            renderGas(area, last, cam, t, fade);
            renderAshFall(area, last, cam, level, fade);
        }
        for (Drop d : DROPS) {
            if (d.rest) {
                drawCrust(area, last, cam, d, fade);
            }
        }
        // all the clouds (column, steam, dust) in one batch, back to front
        List<Cloud> list = new ArrayList<>(PLUME.size() + STEAM.size() + DUST.size());
        list.addAll(PLUME);
        list.addAll(STEAM);
        list.addAll(DUST);
        for (Cloud p : list) {
            p.d2 = (p.x - cam.x) * (p.x - cam.x) + (p.y - cam.y) * (p.y - cam.y) + (p.z - cam.z) * (p.z - cam.z);
        }
        list.sort((a, b) -> Double.compare(b.d2, a.d2));
        for (Cloud p : list) {
            double d = Math.sqrt(p.d2);
            if (p.kind == 0) {
                drawPlume(area, last, cam, p, d, fade);
            } else if (p.kind == 1) {
                drawSteam(area, last, cam, p, d, fade);
            } else {
                drawDust(area, last, cam, p, d, fade);
            }
        }
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
        for (Bomb b : BOMBS) {
            double[] h = hot(b.T);
            if (h[3] > 0.01) {
                double d = Math.sqrt((b.x - cam.x) * (b.x - cam.x) + (b.y - cam.y) * (b.y - cam.y) + (b.z - cam.z) * (b.z - cam.z));
                double s = b.s * (1.4 + 1.2 * h[3]) * (1 + d * 0.004);
                halo(glow, last, cam, b.x, b.y, b.z, s, (float) h[0], (float) h[1] * 0.8f, (float) h[2] * 0.6f, (float) (h[3] * Mth.lerp(night, 0.4f, 0.75f)) * fade);
            }
        }
        for (Drop d : DROPS) {
            double[] h = hot(d.T);
            if (!d.rest && h[3] > 0.02) {
                halo(glow, last, cam, d.x, d.y, d.z, d.s * 2.4, (float) h[0], (float) h[1], (float) h[2], (float) (h[3] * Mth.lerp(night, 0.8f, 1f)) * fade);
            }
        }
        for (Bubble b : BUBBLES) {
            if (b.popped) {
                // the hole left behind: a glowing ring that closes
                double k = (b.age - b.grow) / 0.25, r = b.rmax * (1 - k * 0.6);
                ringGlow(glow, last, cam, b.x, b.y + 0.02, b.z, r, (float) ((1 - k) * Mth.lerp(night, 0.5f, 0.9f)) * fade);
            }
        }
        if (burstT >= 0 && burstAmt > 0) {
            halo(glow, last, cam, burstX, burstY + 6, burstZ, 26 * (0.6 + 0.4 * burstAmt), 1f, 0.55f, 0.18f, (float) (burstAmt * 0.55) * fade);
        }
        buffers.endBatch(FxRenderTypes.GLOW_FLAT);
    }

    private static void v(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        out.addVertex(last, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
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

    // a fan of 9 spokes with the cloud's outline
    private static void fan(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double cx, double cy, double cz, double[] rr, double rot, double s, double age,
            float r, float g, float b, float a) {
        face(cam, cx, cy, cz);
        double px = 0, py = 0, pz = 0;
        for (int q = 0; q <= 9; q++) {
            double ang = rot + (q % 9) / 9.0 * Math.PI * 2, k = rr[q % 9] * (1 + 0.1 * Math.sin(age * 0.7 + q * 1.7)) * s;
            double ox = Math.cos(ang) * k, oy = Math.sin(ang) * k;
            double x = cx + BR[0] * ox + BU[0] * oy, y = cy + BR[1] * ox + BU[1] * oy, z = cz + BR[2] * ox + BU[2] * oy;
            if (q > 0) {
                v(out, last, cam, cx, cy, cz, r, g, b, a);
                v(out, last, cam, px, py, pz, r, g, b, 0);
                v(out, last, cam, x, y, z, r, g, b, 0);
                v(out, last, cam, x, y, z, r, g, b, 0);
            }
            px = x;
            py = y;
            pz = z;
        }
    }

    // A column cloud: a fan of 2 rings (a dense core to 65% of the radius, a short fading edge) for a cauliflower outline;
    // the top catches the sun and the bottom is in the cloud's own shade.
    private static final double[][] RI = new double[10][4], RO = new double[10][4];

    private static void fan2(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double cx, double cy, double cz, double[] rr, double rot, double s,
            double flat, double age, float r, float g, float b, float a, double lit) {
        face(cam, cx, cy, cz);
        for (int q = 0; q <= 9; q++) {
            double ang = rot + (q % 9) / 9.0 * Math.PI * 2, k = rr[q % 9] * (1 + 0.1 * Math.sin(age * 0.7 + q * 1.7)) * s;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            for (int w = 0; w < 2; w++) {
                double f = w == 0 ? 0.65 : 1, ox = ca * k * f, oy = sa * k * f * flat;
                double[] o = w == 0 ? RI[q] : RO[q];
                o[0] = cx + BR[0] * ox + BU[0] * oy;
                o[1] = cy + BR[1] * ox + BU[1] * oy;
                o[2] = cz + BR[2] * ox + BU[2] * oy;
                o[3] = 1 + lit * sa * f;
            }
        }
        float ai = a * 0.92f;
        for (int q = 0; q < 9; q++) {
            double[] i0 = RI[q], i1 = RI[q + 1], o0 = RO[q], o1 = RO[q + 1];
            v(out, last, cam, cx, cy, cz, r, g, b, a);
            v(out, last, cam, i0[0], i0[1], i0[2], (float) (r * i0[3]), (float) (g * i0[3]), (float) (b * i0[3]), ai);
            v(out, last, cam, i1[0], i1[1], i1[2], (float) (r * i1[3]), (float) (g * i1[3]), (float) (b * i1[3]), ai);
            v(out, last, cam, i1[0], i1[1], i1[2], (float) (r * i1[3]), (float) (g * i1[3]), (float) (b * i1[3]), ai);
            v(out, last, cam, i0[0], i0[1], i0[2], (float) (r * i0[3]), (float) (g * i0[3]), (float) (b * i0[3]), ai);
            v(out, last, cam, i1[0], i1[1], i1[2], (float) (r * i1[3]), (float) (g * i1[3]), (float) (b * i1[3]), ai);
            v(out, last, cam, o1[0], o1[1], o1[2], (float) (r * o1[3]), (float) (g * o1[3]), (float) (b * o1[3]), 0);
            v(out, last, cam, o0[0], o0[1], o0[2], (float) (r * o0[3]), (float) (g * o0[3]), (float) (b * o0[3]), 0);
        }
    }

    private static double boltLight(double x, double y, double z) {
        if (boltT < 0 || boltAmt <= 0) {
            return 0;
        }
        double d = Math.sqrt((x - boltX) * (x - boltX) + (y - boltY) * (y - boltY) + (z - boltZ) * (z - boltZ));
        double f = clamp01(1 - d / boltR);
        return boltAmt * f * f * 1.3;
    }

    private static void drawPlume(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Cloud p, double d, float fade) {
        double h = p.y - lakeY, k = p.age / p.life;
        double a = clamp01(p.age / 1.5) * clamp01((1 - k) / 0.25) * (p.um > 0 ? 0.75 : 0.88);
        a *= clamp01((d - 3) / 8) * fade;
        if (a < 0.004) {
            return;
        }
        double s = plumeR(p), n = night;
        // the lake's orange belly (falls with height), a grey top catching the sun; the umbrella lighter and flatter
        double lava = Math.exp(-h / Mth.lerp(n, 20, 30)) * Mth.lerp(n, 0.4, 1) + (burstT >= 0 ? burstAmt * Math.exp(-h / 40) : 0);
        double sunTop = Mth.lerp(n, 0.55 + 0.45 * clamp01(h / 60), 0.25);
        double tn = p.tone * sunTop + (p.um > 0 ? 0.04 : 0);
        double bl = boltLight(p.x, p.y, p.z);
        float r = (float) (tn * 1.05 + lava * 0.75 + bl * 0.7), g = (float) (tn * 0.95 + lava * 0.26 + bl * 0.66), b = (float) (tn * 0.9 + lava * 0.05 + bl * 0.85);
        fan2(out, last, cam, p.x, p.y, p.z, p.r, p.rot, s, p.um > 0 ? 0.6 : 1, p.age, r, g, b, (float) a, Mth.lerp(n, 0.28, -0.25 * Math.exp(-h / 30)));
    }

    private static void drawSteam(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Cloud p, double d, float fade) {
        double rise = p.y - p.my, k = p.age / p.life;
        // the mouth's gap: hot steam is clear until it condenses, ~0.6 block above
        double a = clamp01((rise + 0.1) / 0.8) * (1 - k) * (1 - k) * 0.62 * clamp01((d - 1.2) / 2.5) * fade;
        if (a < 0.004) {
            return;
        }
        double s = 0.3 + rise * 0.32 + p.age * 0.12, n = night;
        double warm = Math.exp(-Math.max(0, rise) / 2.5) * Mth.lerp(n, 0.15, 0.9), base = Mth.lerp(n, 0.86, 0.32);
        // a touch of the sulfur's yellow near the mouth
        double sul = Math.exp(-Math.max(0, rise) / 3) * 0.08;
        fan(out, last, cam, p.x, p.y, p.z, p.r, p.rot, s, p.age, (float) (base + warm * 0.6), (float) (base * 0.98 + warm * 0.25 - sul * 0.2),
                (float) (base * 0.94 + warm * 0.05 - sul), (float) a);
    }

    private static void drawDust(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Cloud p, double d, float fade) {
        double k = p.age / p.life;
        double a = (1 - k) * (1 - k) * 0.7 * clamp01(p.age / 0.15) * clamp01((d - 1) / 2) * fade;
        if (a < 0.004) {
            return;
        }
        double s = p.s0 + p.age * p.grow, t = p.tone * Mth.lerp(night, 1, 0.45);
        fan(out, last, cam, p.x, p.y, p.z, p.r, p.rot, s, p.age, (float) (t * 1.05), (float) (t * 0.98), (float) (t * 0.92), (float) a);
    }

    // Ash falling from the umbrella: wide faint curtains under the oldest clouds (fine ash falls slowly and far).
    private static void renderAshFall(VertexConsumer out, PoseStack.Pose last, Vec3 cam, ClientLevel level, float fade) {
        if (!volcano) {
            return;
        }
        float n = night;
        float cr = Mth.lerp(n, 0.36f, 0.12f), cg = Mth.lerp(n, 0.32f, 0.08f), cb = Mth.lerp(n, 0.3f, 0.07f);
        for (Cloud p : PLUME) {
            if (p.um < 14) {
                continue;
            }
            double k = p.age / p.life, a = clamp01((p.um - 14) / 10) * clamp01((1 - k) / 0.3) * 0.07 * fade;
            if (a < 0.003) {
                continue;
            }
            int gt = top(level, p.x, p.z);
            if (gt < -900) {
                continue;
            }
            double s = plumeR(p) * 0.7, hh = Math.min(60, p.y - (gt + 1));
            if (hh < 8) {
                continue;
            }
            // turned to the eye only round the vertical axis (a standing curtain)
            double dx = p.x - cam.x, dz = p.z - cam.z, l = Math.max(1e-3, Math.hypot(dx, dz)), sx = -dz / l * s, sz = dx / l * s;
            double y0 = p.y - 2, y1 = y0 - hh, lean = 0.25 * hh;
            v(out, last, cam, p.x - sx, y0, p.z - sz, cr, cg, cb, (float) a);
            v(out, last, cam, p.x + sx, y0, p.z + sz, cr, cg, cb, (float) a);
            v(out, last, cam, p.x + sx + wx * lean, y1, p.z + sz + wz * lean, cr, cg, cb, 0);
            v(out, last, cam, p.x - sx + wx * lean, y1, p.z - sz + wz * lean, cr, cg, cb, 0);
        }
    }

    private static double smoothK(double m) {
        double k = clamp01((m - 0.3) / 0.45);
        return k * k * (3 - 2 * k);
    }

    private static final double[] GA = new double[GW * GW], GYY = new double[GW * GW];

    private static void renderGas(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        if (!gMeasured) {
            return;
        }
        float n = night;
        float cr = Mth.lerp(n, 0.66f, 0.2f), cg = Mth.lerp(n, 0.63f, 0.12f), cb = Mth.lerp(n, 0.44f, 0.07f);
        double half = CELLS * GSIZE / 2.0;
        double[][] layers = {{0.4, 0.34, 0.25}, {1.4, 0.2, 0.4}};
        for (double[] L : layers) {
            double hgt = L[0], amp = L[1], spd = L[2];
            for (int i = 0; i <= CELLS; i++) {
                for (int j = 0; j <= CELLS; j++) {
                    int k = i * GW + j;
                    if (GY[k] < -900 || GLOW_[k] <= 0) {
                        GA[k] = 0;
                        GYY[k] = 0;
                        continue;
                    }
                    double x = ggx + i * GSIZE, z = ggz + j * GSIZE, y = GY[k] + hgt;
                    // the gas creeps slowly, no gusts: only a slow breathing
                    double m = vnoise(x * 0.06 + t * spd * 0.1, z * 0.06 - t * spd * 0.07) * 0.6 + vnoise(x * 0.15 - t * 0.03, z * 0.15) * 0.4;
                    double dd = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
                    double fd = clamp01((dd - 2) / 7) * clamp01((half - Math.hypot(x - (ggx + half), z - (ggz + half))) / (half * 0.35));
                    GA[k] = amp * GLOW_[k] * smoothK(m) * fd * fade;
                    GYY[k] = y;
                }
            }
            for (int i = 0; i < CELLS; i++) {
                for (int j = 0; j < CELLS; j++) {
                    int k00 = i * GW + j, k10 = (i + 1) * GW + j, k11 = (i + 1) * GW + j + 1, k01 = i * GW + j + 1;
                    if (GA[k00] + GA[k10] + GA[k11] + GA[k01] < 0.004) {
                        continue;
                    }
                    if (GY[k00] < -900 || GY[k10] < -900 || GY[k11] < -900 || GY[k01] < -900) {
                        continue;
                    }
                    double ym = Math.max(Math.max(GYY[k00], GYY[k10]), Math.max(GYY[k01], GYY[k11]));
                    double x0 = ggx + i * GSIZE, x1 = x0 + GSIZE, z0 = ggz + j * GSIZE, z1 = z0 + GSIZE;
                    v(out, last, cam, x0, GYY[k00] != 0 ? GYY[k00] : ym, z0, cr, cg, cb, (float) GA[k00]);
                    v(out, last, cam, x1, GYY[k10] != 0 ? GYY[k10] : ym, z0, cr, cg, cb, (float) GA[k10]);
                    v(out, last, cam, x1, GYY[k11] != 0 ? GYY[k11] : ym, z1, cr, cg, cb, (float) GA[k11]);
                    v(out, last, cam, x0, GYY[k01] != 0 ? GYY[k01] : ym, z1, cr, cg, cb, (float) GA[k01]);
                }
            }
        }
    }

    // A stone: a cube spinning in the air, the heat's color added to the rock's.
    private static final int[][][] CF = {
        {{1, -1, -1}, {1, 1, -1}, {1, 1, 1}, {1, -1, 1}, {1, 0, 0}}, {{-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}, {-1, -1, -1}, {-1, 0, 0}},
        {{-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}, {1, 1, -1}, {0, 1, 0}}, {{-1, -1, 1}, {-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {0, -1, 0}},
        {{1, -1, 1}, {1, 1, 1}, {-1, 1, 1}, {-1, -1, 1}, {0, 0, 1}}, {{-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}, {1, -1, -1}, {0, 0, -1}}};

    private static void cube(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double s, double ax, double ay, double az,
            double ang, float r, float g, float b, boolean shade) {
        double l = Math.max(1e-6, Math.sqrt(ax * ax + ay * ay + az * az));
        ax /= l;
        ay /= l;
        az /= l;
        double c = Math.cos(ang), sn = Math.sin(ang), t1 = 1 - c;
        double r00 = c + ax * ax * t1, r01 = ax * ay * t1 - az * sn, r02 = ax * az * t1 + ay * sn;
        double r10 = ay * ax * t1 + az * sn, r11 = c + ay * ay * t1, r12 = ay * az * t1 - ax * sn;
        double r20 = az * ax * t1 - ay * sn, r21 = az * ay * t1 + ax * sn, r22 = c + az * az * t1;
        double h = s / 2;
        for (int[][] f : CF) {
            int[] nn = f[4];
            double ny = r10 * nn[0] + r11 * nn[1] + r12 * nn[2];
            float k = shade ? (float) (0.62 + 0.38 * (ny * 0.5 + 0.5)) : 1;
            for (int q = 0; q < 4; q++) {
                int[] p = f[q];
                v(out, last, cam, x + (r00 * p[0] + r01 * p[1] + r02 * p[2]) * h, y + (r10 * p[0] + r11 * p[1] + r12 * p[2]) * h,
                        z + (r20 * p[0] + r21 * p[1] + r22 * p[2]) * h, r * k, g * k, b * k, 1);
            }
        }
    }

    private static void drawBomb(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Bomb b) {
        double[] hh = hot(b.T);
        double rock = Mth.lerp(night, 0.13, 0.05);
        cube(out, last, cam, b.x, b.y, b.z, b.s, b.ax, b.ay, b.az, b.ang, (float) (rock + hh[0] * hh[3]), (float) (rock * 0.95 + hh[1] * hh[3]),
                (float) (rock * 0.9 + hh[2] * hh[3]), hh[3] < 0.5);
        // a hot stone on the ground still lets out a wisp of smoke (small clouds), as on a fresh volcanic bomb
        if (b.rest && b.T > 380 && rnd() < 0.08) {
            DUST.add(dustPuff(b.x, b.y + b.s * 0.4, b.z, 0.12 + b.s * 0.2, 0.35, Mth.lerp(night, 0.5, 0.4), 2 + rnd()));
        }
    }

    // a splash on the ground: a dark crust that fades
    private static void drawCrust(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Drop d, float fade) {
        double[] hh = hot(d.T);
        float a = (float) (clamp01(1 - (d.age - d.restAt) / 6) * 0.85) * fade;
        double s = d.s * 1.6, c = Mth.lerp(night, 0.1, 0.04);
        float r = (float) (c + hh[0] * hh[3]), g = (float) (c * 0.9 + hh[1] * hh[3]), b = (float) (c * 0.8);
        v(out, last, cam, d.x - s, d.y, d.z - s, r, g, b, a);
        v(out, last, cam, d.x + s, d.y, d.z - s, r, g, b, a);
        v(out, last, cam, d.x + s, d.y, d.z + s, r, g, b, a);
        v(out, last, cam, d.x - s, d.y, d.z + s, r, g, b, a);
    }

    // a soft 8-sided disc: strong in the middle, zero at the edge (no streak)
    private static void halo(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double s, float r, float g, float b, float a) {
        if (a <= 0.002f) {
            return;
        }
        face(cam, x, y, z);
        double px = x + BR[0] * s, py = y + BR[1] * s, pz = z + BR[2] * s;
        for (int q = 1; q <= 8; q++) {
            double ang = q / 8.0 * Math.PI * 2, cx = Math.cos(ang) * s, cy = Math.sin(ang) * s;
            double qx = x + BR[0] * cx + BU[0] * cy, qy = y + BR[1] * cx + BU[1] * cy, qz = z + BR[2] * cx + BU[2] * cy;
            v(out, last, cam, x, y, z, r, g, b, a);
            v(out, last, cam, px, py, pz, r, g, b, 0);
            v(out, last, cam, qx, qy, qz, r, g, b, 0);
            v(out, last, cam, qx, qy, qz, r, g, b, 0);
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    // A bubble: a dome of lava (the crust darker on top, stretching and paling before it bursts).
    private static void drawBubble(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Bubble b) {
        double k = Math.min(1, b.age / b.grow), r = b.rmax * Math.pow(k, 0.7), hgt = r * (0.4 + 0.5 * k);
        float n = night;
        float c0 = Mth.lerp(n, 0.42f, 0.5f), c1 = Mth.lerp(n, 0.14f, 0.12f), c2 = Mth.lerp(n, 0.05f, 0.02f);
        final int SEG = 8, RINGS = 3;
        for (int i = 0; i < RINGS; i++) {
            // the crust thins on top as it stretches: the top pales at the end
            double u0 = (1 - (double) i / RINGS) * 0.8 + k * k * ((double) i / RINGS) * 0.9, u1 = (1 - (i + 1.0) / RINGS) * 0.8 + k * k * ((i + 1.0) / RINGS) * 0.9;
            float a0 = (float) (c0 + (1 - c0) * u0), a1 = (float) (c1 + (0.62 - c1) * u0), a2 = (float) (c2 + (0.15 - c2) * u0);
            float b0 = (float) (c0 + (1 - c0) * u1), b1 = (float) (c1 + (0.62 - c1) * u1), b2 = (float) (c2 + (0.15 - c2) * u1);
            double ph0 = (double) i / RINGS * Math.PI / 2, ph1 = (i + 1.0) / RINGS * Math.PI / 2;
            for (int j = 0; j < SEG; j++) {
                double aa = (double) j / SEG * Math.PI * 2, ab = (j + 1.0) / SEG * Math.PI * 2;
                v(out, last, cam, b.x + Math.cos(aa) * Math.cos(ph0) * r, b.y + Math.sin(ph0) * hgt, b.z + Math.sin(aa) * Math.cos(ph0) * r, a0, a1, a2, 1);
                v(out, last, cam, b.x + Math.cos(ab) * Math.cos(ph0) * r, b.y + Math.sin(ph0) * hgt, b.z + Math.sin(ab) * Math.cos(ph0) * r, a0, a1, a2, 1);
                v(out, last, cam, b.x + Math.cos(ab) * Math.cos(ph1) * r, b.y + Math.sin(ph1) * hgt, b.z + Math.sin(ab) * Math.cos(ph1) * r, b0, b1, b2, 1);
                v(out, last, cam, b.x + Math.cos(aa) * Math.cos(ph1) * r, b.y + Math.sin(ph1) * hgt, b.z + Math.sin(aa) * Math.cos(ph1) * r, b0, b1, b2, 1);
            }
        }
    }

    private static void ringGlow(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double r, float a) {
        if (a <= 0.002f) {
            return;
        }
        for (int j = 0; j < 10; j++) {
            double a0 = j / 10.0 * Math.PI * 2, a1 = (j + 1) / 10.0 * Math.PI * 2;
            v(out, last, cam, x + Math.cos(a0) * r * 0.3, y, z + Math.sin(a0) * r * 0.3, 1f, 0.7f, 0.25f, a);
            v(out, last, cam, x + Math.cos(a1) * r * 0.3, y, z + Math.sin(a1) * r * 0.3, 1f, 0.7f, 0.25f, a);
            v(out, last, cam, x + Math.cos(a1) * r, y, z + Math.sin(a1) * r, 1f, 0.4f, 0.1f, 0);
            v(out, last, cam, x + Math.cos(a0) * r, y, z + Math.sin(a0) * r, 1f, 0.4f, 0.1f, 0);
        }
    }

    // ---------------------------------------------------------------- the screen, the fog and the tremor

    public static boolean gradeOn() {
        return active() && FxShaders.jjGrade != null;
    }

    // Sun filtered through ash by day; at night the lava's glow from below (never darkening). The blast flashes orange,
    // the lightning violet-white.
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.jjGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        float n = night;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double fl = burstT >= 0 && burstT < 2 ? burstAmt * clamp01(1 - burstD / 400) : 0;
        double lt = boltT >= 0 ? boltAmt * clamp01(1 - boltD / 500) : 0;
        sh.safeGetUniform("Sat").set(Mth.lerp(fade, 1f, Mth.lerp(n, 0.82f, 0.95f)));
        sh.safeGetUniform("Contrast").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.03f, 1.04f)));
        // by day the sun through the ash (the page's sepia and brown wash); the blast's orange flash over it
        float wa = 0.1f * (1 - n), fa = (float) (fl * 0.16);
        if (fa > wa) {
            sh.safeGetUniform("Wash").set(1f, 140 / 255f, 50 / 255f, fa * fade);
        } else {
            sh.safeGetUniform("Wash").set(150 / 255f, 90 / 255f, 50 / 255f, wa * fade);
        }
        sh.safeGetUniform("Warm").set(0.12f * n * fade);
        sh.safeGetUniform("Vig").set(Mth.lerp(n, 70, 50) / 255f, Mth.lerp(n, 34, 8) / 255f, Mth.lerp(n, 16, 0) / 255f, Mth.lerp(n, 0.32f, 0.3f) * fade);
        sh.safeGetUniform("Flash").set((float) (lt * 0.7) * fade);
        FxPost.pass(sh);
    }

    // The fog: sunlight through ash by day (a warm brown); at night red-black with the lava below (never down).
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float k = fade * 0.75f;
        float r = Mth.lerp(night, 0x7c / 255f, 0x2c / 255f), g = Mth.lerp(night, 0x5c / 255f, 0x12 / 255f), b = Mth.lerp(night, 0x4a / 255f, 0x0a / 255f);
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
        float near = Mth.lerp(night, 22f, 26f), far = Mth.lerp(night, 190f, 150f);
        event.setNearPlaneDistance(Mth.lerp(fade, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), near)));
        event.setFarPlaneDistance(Mth.lerp(fade, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), far)));
        event.setCanceled(true);
    }

    // The blast's tremor (the preview shakes the camera the same way, a little, fading fast).
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.001f || strength <= 0) {
            return;
        }
        float s = shake * shake * 0.9f;
        event.setPitch(event.getPitch() + (float) (rnd() - 0.5) * s);
        event.setYaw(event.getYaw() + (float) (rnd() - 0.5) * s);
    }

    // ---------------------------------------------------------------- sound

    // A sound coming from far: played toward its source a few blocks from the listener (heard from far, from the right
    // side), louder the closer.
    private static void play(double x, double y, double z, SoundEvent s, float vol, float pitch, Vec3 f) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double dx = x - f.x, dy = y - f.y, dz = z - f.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > 12) {
            x = f.x + dx / d * 12;
            y = f.y + dy / d * 12;
            z = f.z + dz / d * 12;
        }
        level.playLocalSound(x, y, z, s, SoundSource.AMBIENT, vol * strength, pitch, false);
    }

    private static void later(double delay, SoundEvent s, double x, double y, double z, float vol, float pitch) {
        Pending p = new Pending();
        p.at = clock + delay;
        p.s = s;
        p.x = x;
        p.y = y;
        p.z = z;
        p.vol = vol;
        p.pitch = pitch;
        PENDING.add(p);
    }

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 c) {
        clock = level.getGameTime() / 20.0;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            if (clock >= p.at) {
                level.playLocalSound(p.x, p.y, p.z, p.s, SoundSource.AMBIENT, p.vol * strength, p.pitch, false);
                PENDING.remove(i);
            }
        }
        double dv = volcano ? Math.hypot(c.x - vcx, c.z - vcz) : 1e9;
        rumbleVol = (float) ((0.12 + 0.5 * clamp01(1 - dv / 300)) * 1.4);
        double dm = 1e9;
        for (Mouth m : cur.mouths) {
            dm = Math.min(dm, Math.sqrt((c.x - m.x) * (c.x - m.x) + (c.y - m.y) * (c.y - m.y) + (c.z - m.z) * (c.z - m.z)));
        }
        hissVol = (float) (Math.pow(clamp01(1 - dm / 30), 2) * 0.9);
        double dl = 1e9;
        for (int i = 0; i < cur.lava.size(); i += 7) {
            double[] q = cur.lava.get(i);
            dl = Math.min(dl, Math.hypot(c.x - q[0], c.z - q[2]));
        }
        if (volcano) {
            dl = Math.min(dl, Math.max(0, dv - CR));
        }
        bubVol = (float) (Math.pow(clamp01(1 - dl / 40), 2) * 1.0);
        rumble = keep(mc, rumble, ModSounds.JOGO_RONCO.get(), 0);
        hiss = keep(mc, hiss, ModSounds.JOGO_CHIADO.get(), 1);
        bubbling = keep(mc, bubbling, ModSounds.JOGO_BORBULHA.get(), 2);
    }

    private static Loop keep(Minecraft mc, Loop l, SoundEvent s, int which) {
        if (l == null || !mc.getSoundManager().isActive(l)) {
            l = new Loop(s, which);
            mc.getSoundManager().play(l);
        }
        return l;
    }

    // A loop all round the player: the rumble (louder near the volcano), the hiss (near a fumarole), the bubbling lava.
    private static final class Loop extends AbstractTickableSoundInstance {
        private final int which;

        Loop(SoundEvent s, int which) {
            super(s, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.which = which;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0.001f;
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
            float v = which == 0 ? rumbleVol : which == 1 ? hissVol : bubVol;
            volume = Math.max(0.001f, Math.min(1f, v) * strength);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
