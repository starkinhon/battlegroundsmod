// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The air of the Mar de Dagon (Stark 2026-11-19 on the preview forja/jujutsu-dagon-ambiente). amb.js there is the source
// of truth and this is its port, the same names where it helps. Fades in when the player walks into the biome and out
// when the player leaves (BiomeAura). No texture and no thin line: foam and glints are soft discs. Everything makes
// physical sense:
// - the wave comes in slanted on the beach, runs up the sand losing speed (rises as a sine until it stops), goes back
//   more slowly and the foam left behind keeps popping; each stretch of the beach gets the wave at its own time (the
//   phase runs along the coast);
// - the net of light on the bottom (caustics) is the sun focused by the waves: strong in the shallows, gone with depth.
//   Drawn before the water, so the water passes over it as in the game;
// - the sparkle on the water is the sun mirrored on the facets of the ripples: only where the reflection points at the
//   sun (the moon at night);
// - the whirlpool is a free vortex: speed ~ 1/r (slow at the edge, fast at the center), the water pulled in and the
//   surface sinking in the middle;
// - at night the plankton lights where the water is stirred (at the wave's front and in the whirlpool), not on the calm sea;
// - the screen (fx_jjgrade): too beautiful to be normal: colors too vivid, warm by day; blue at night, never darkening;
// - sounds built from noise (tools/sounds/dagon_sounds.py): the sea wind, the abyss's rumble near the Nest, the waves
//   breaking when a crest comes in close.
public final class DagonAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WAVE = 0.5, T = 7.5;
    private static final int SCAN = 64;
    private static final int N = 80;
    private static final int GLINTS = 1800, SWIRL = 900;

    private static float strength, lastStrength;
    private static float night;
    private static final double[] SUN = {0, 1, 0};
    private static long lastNanos;
    private static ClientLevel lastLevel;
    private static double lookX, lookY, lookZ = 1;
    private static double wy = 62.889;
    private static int sea = 62;

    // ---------------------------------------------------------------- the Nest (read from its blocks)

    private static double nestX, nestZ;
    private static boolean nestKnown;
    private static final List<double[]> TENTACLES = new ArrayList<>();

    private static final class Scan {
        double ax, az;
        int an;
        final List<int[]> tent = new ArrayList<>();

        void clear() {
            ax = az = 0;
            an = 0;
            tent.clear();
        }
    }

    private static Scan next = new Scan();
    private static int scanRow = -SCAN - 1, scanCx, scanCz;
    private static boolean firstPass = true;

    // ---------------------------------------------------------------- the columns near (bottom and depth)

    private static final int W = N + 1;
    // floor (-999 = not beach nor sea: a tall column, a tentacle, a palm), position on the beach, flags
    private static int[] fl = new int[W * W], bfl = new int[W * W];
    private static float[] sp = new float[W * W], bsp = new float[W * W];
    private static byte[] flag = new byte[W * W], bflag = new byte[W * W];
    private static final byte VALID = 1, COAST = 2, ABYSS = 4;
    private static int gx, gz, bgx, bgz;
    private static boolean measured;
    private static int mPhase = -1;
    private static double mAt = -100, mfx = 1e9, mfz = 1e9;

    // ---------------------------------------------------------------- moving things

    private static final class Glint {
        double x = 1e9, z, sx, sz, t;
    }

    private static final Glint[] GLINT = new Glint[GLINTS];

    private static final class Swirl {
        double r, a, s, ph;
    }

    private static final Swirl[] SWIRLS = new Swirl[SWIRL];
    private static boolean swirlOn;

    static {
        for (int i = 0; i < GLINTS; i++) {
            GLINT[i] = new Glint();
        }
        for (int i = 0; i < SWIRL; i++) {
            SWIRLS[i] = new Swirl();
        }
    }

    // sounds
    private static WindLoop windLoop;
    private static AbyssLoop abyssLoop;
    private static double crestNear = 1e9, crestX, crestZ, lastSurf = -100;

    private DagonAirFx() {
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

    // the top block that is not water (the sand, the bottom)
    private static int floorAt(ClientLevel level, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!level.hasChunk(ix >> 4, iz >> 4)) {
            return -999;
        }
        int h = ClientHeights.oceanFloor(level, ix, iz) - 1;
        return h <= level.getMinBuildHeight() ? -999 : h;
    }

    // the floor from the measured columns when inside them, else from the world
    private static int floorFast(ClientLevel level, double x, double z) {
        int i = Mth.floor(x) - gx, j = Mth.floor(z) - gz;
        if (measured && i >= 0 && j >= 0 && i <= N && j <= N) {
            return fl[i * W + j];
        }
        return floorAt(level, x, z);
    }

    private static boolean abyssAt(double x, double z) {
        return nestKnown && Math.hypot(x - nestX, z - nestZ) < 9;
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    // ---------------------------------------------------------------- tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            if (lastStrength > 0 || nestKnown || measured) {
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
        boolean in = BiomeAura.in("mar_de_dagon_ar", b -> b.is(ModWorldgen.MAR_DE_DAGON));
        strength = Mth.clamp(strength + (in ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            if (lastStrength > 0) {
                reset(false);
            }
            return;
        }
        ClientLevel level = mc.level;
        sea = level.getSeaLevel() - 1;
        wy = sea + 0.889;
        Vec3 p = mc.player.position();
        int rows = firstPass ? 16 : 1;
        for (int i = 0; i < rows; i++) {
            scanStep(level, p);
        }
        measureStep(level, mc.gameRenderer.getMainCamera().getPosition());
        sounds(mc, level, p);
    }

    // The Nest: the abyss's middle; the tentacles where they come out of the water.
    private static void scanStep(ClientLevel level, Vec3 p) {
        if (scanRow > SCAN) {
            afterPass();
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
        Block abyss = JujutsuBlocks.ABYSS.get(), t1 = JujutsuBlocks.DAGON_TENTACLE.get(), t2 = JujutsuBlocks.DAGON_TENTACLE_DARK.get();
        for (int z = scanCz - SCAN; z <= scanCz + SCAN; z++) {
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int f = floorAt(level, x, z);
            if (f > -900 && level.getBlockState(pos.set(x, f, z)).is(abyss)) {
                next.ax += x + 0.5;
                next.az += z + 0.5;
                next.an++;
            }
            BlockState s = level.getBlockState(pos.set(x, sea, z));
            if (s.is(t1) || s.is(t2)) {
                next.tent.add(new int[] {x, z});
            }
        }
    }

    private static void afterPass() {
        if (next.an >= 20) {
            nestX = next.ax / next.an;
            nestZ = next.az / next.an;
            nestKnown = true;
        }
        // the tentacles: the pieces at the water line, joined when within 4 blocks of each other
        if (!next.tent.isEmpty()) {
            List<double[]> groups = new ArrayList<>();
            for (int[] q : next.tent) {
                double[] g = null;
                for (double[] h : groups) {
                    if (Math.abs(h[0] / h[2] - q[0]) <= 4 && Math.abs(h[1] / h[2] - q[1]) <= 4) {
                        g = h;
                        break;
                    }
                }
                if (g == null) {
                    groups.add(new double[] {q[0] + 0.5, q[1] + 0.5, 1});
                } else {
                    g[0] += q[0] + 0.5;
                    g[1] += q[1] + 0.5;
                    g[2]++;
                }
            }
            TENTACLES.clear();
            for (double[] g : groups) {
                if (g[2] >= 4) {
                    TENTACLES.add(new double[] {g[0] / g[2], g[1] / g[2]});
                }
            }
        } else if (nestKnown && Math.hypot(scanCx - nestX, scanCz - nestZ) < SCAN - 30) {
            TENTACLES.clear();
        }
    }

    // The columns near, re-measured every 5 s or after 6 blocks: first the floors, then the beach position row by row.
    private static void measureStep(ClientLevel level, Vec3 f) {
        double t = level.getGameTime() / 20.0;
        if (mPhase < 0) {
            if (measured && t - mAt < 5 && Math.hypot(f.x - mfx, f.z - mfz) <= 6) {
                return;
            }
            mAt = t;
            mfx = f.x;
            mfz = f.z;
            bgx = Mth.floor(f.x) - N / 2;
            bgz = Mth.floor(f.z) - N / 2;
            for (int i = 0; i <= N; i++) {
                for (int j = 0; j <= N; j++) {
                    int x = bgx + i, z = bgz + j, h = floorAt(level, x, z), k = i * W + j;
                    // a tall column (a tentacle, a palm trunk) is neither beach nor sea: the wave goes round it
                    if (h < -900 || h > sea + 5) {
                        bfl[k] = -999;
                        bflag[k] = 0;
                    } else {
                        bfl[k] = h;
                        bflag[k] = (byte) (VALID | (abyssAt(x + 0.5, z + 0.5) ? ABYSS : 0));
                    }
                }
            }
            mPhase = 0;
            return;
        }
        // the surf only exists at the edge: s = the position on the beach. In the water, minus the distance to the sand
        // (in blocks); on the sand, the height above the water. The wave runs on this axis: breaks some 5 blocks out,
        // comes in and runs up the wet sand.
        for (int r = 0; r < 12 && mPhase <= N; r++, mPhase++) {
            int i = mPhase;
            for (int j = 0; j <= N; j++) {
                int k = i * W + j;
                if ((bflag[k] & VALID) == 0) {
                    continue;
                }
                double e = bfl[k] + 1 - wy;
                if (e > 0) {
                    // sand: its height plus the beach's ramp (the wave loses strength climbing: some 2 to 3 blocks of wet sand)
                    double dw = 99;
                    for (int a = -3; a <= 3; a++) {
                        for (int b = -3; b <= 3; b++) {
                            int ii = i + a, jj = j + b;
                            if (ii < 0 || jj < 0 || ii > N || jj > N) {
                                continue;
                            }
                            int o = ii * W + jj;
                            if ((bflag[o] & VALID) != 0 && bfl[o] + 1 - wy <= 0) {
                                dw = Math.min(dw, Math.sqrt(a * a + b * b));
                            }
                        }
                    }
                    bsp[k] = (float) (e + Math.max(0, dw - 0.5) * 0.35);
                    bflag[k] = (byte) (dw < 4 ? bflag[k] | COAST : bflag[k] & ~COAST);
                    continue;
                }
                double best = 99;
                for (int a = -6; a <= 6; a++) {
                    for (int b = -6; b <= 6; b++) {
                        int ii = i + a, jj = j + b;
                        if (ii < 0 || jj < 0 || ii > N || jj > N) {
                            continue;
                        }
                        int o = ii * W + jj;
                        if ((bflag[o] & VALID) == 0 || bfl[o] + 1 - wy <= 0) {
                            continue;
                        }
                        best = Math.min(best, Math.sqrt(a * a + b * b));
                    }
                }
                bsp[k] = (float) -(best - 0.5);
                bflag[k] = (byte) (best < 6.5 ? bflag[k] | COAST : bflag[k] & ~COAST);
            }
        }
        if (mPhase > N) {
            int[] a = fl;
            fl = bfl;
            bfl = a;
            float[] b = sp;
            sp = bsp;
            bsp = b;
            byte[] c = flag;
            flag = bflag;
            bflag = c;
            gx = bgx;
            gz = bgz;
            measured = true;
            mPhase = -1;
        }
    }

    private static void reset(boolean all) {
        strength = 0;
        firstPass = true;
        scanRow = -SCAN - 1;
        next = new Scan();
        for (Glint g : GLINT) {
            g.x = 1e9;
        }
        swirlOn = false;
        mPhase = -1;
        measured = false;
        mAt = -100;
        crestNear = 1e9;
        if (windLoop != null) {
            windLoop.end();
            windLoop = null;
        }
        if (abyssLoop != null) {
            abyssLoop.end();
            abyssLoop = null;
        }
        if (all) {
            nestKnown = false;
            TENTACLES.clear();
        }
    }

    // ---------------------------------------------------------------- the wave

    // the surf's phase at that point of the beach (0..1): runs up to 0.4 (a sine, stopping), back down to 1
    private static double swP, swF;
    private static boolean swRising;

    private static void swash(double x, double z, double t) {
        double along = x * Math.cos(WAVE) + z * Math.sin(WAVE);
        double p = ((t / T - along / 38) % 1 + 1) % 1;
        // f = where the front is on the beach axis: leaves -5 (breaking), reaches the edge and runs up to 0.95 of sand;
        // back to -1.5
        swP = p;
        swF = p < 0.4 ? -5 + 5.95 * Math.sin(p / 0.4 * Math.PI / 2) : -1.5 + 2.45 * Math.pow(1 - (p - 0.4) / 0.6, 1.3);
        swRising = p < 0.4;
    }

    // Foam lace: real foam is a net of bubbles (the walls between cells), not a blot. The distance to the edge between
    // the two nearest cells (Worley F2-F1): small = on a bubble wall = foam.
    private static double lace(double x, double z, double t) {
        x = x * 1.2 + t * 0.06;
        z = z * 1.2 - t * 0.04;
        int ix = Mth.floor(x), iz = Mth.floor(z);
        double f1 = 81, f2 = 81;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                int cx = ix + a, cz = iz + b;
                double h = hsh(cx, cz);
                double fx = cx + 0.5 + 0.4 * Math.sin(h * 40 + t * 0.5), fz = cz + 0.5 + 0.4 * Math.cos(h * 57 + t * 0.4);
                double dx = fx - x, dz = fz - z, d = dx * dx + dz * dz;
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                } else if (d < f2) {
                    f2 = d;
                }
            }
        }
        return clamp01(1.25 - (Math.sqrt(f2) - Math.sqrt(f1)) / 0.26);
    }

    // how much foam at the point (0..1) and how much front (for the plankton); also leaves the wave's phase
    private static double fA, fFront;

    private static void foamAt(double x, double z, double t, double s) {
        swash(x, z, t);
        double q = swF - s;
        boolean lead = q < 0 && q > -0.5 && swRising, stay = s < 0 && s > -1.2;
        fA = 0;
        fFront = 0;
        if (q < 0 && !lead && !stay) {
            return;
        }
        double net = lace(x, z, t);
        // the front's edge is no knife: half a block of thin foam ahead of it
        if (lead) {
            fFront = 1 + q / 0.5;
            fA = fFront * fFront * 0.6;
        }
        if (q >= 0) {
            // running up: the front is full white and behind it the lace the broken wave leaves; going back, the lace pops
            fFront = swRising ? Math.exp(-q / 0.6) : 0;
            fA = swRising ? Math.max(fFront, net * 0.95 * Math.exp(-q / 1.6)) : net * 0.85 * Math.exp(-q / 2) * (1 - (swP - 0.4) / 0.6);
        }
        // stuck to the sand there is always some lace floating
        if (stay) {
            fA = Math.max(fA, net * 0.5);
        }
    }

    // the position on the smooth beach between the blocks' centers (the wave's front does not move in block steps)
    private static double sAt(int i, int j, double fu, double fv, double cs) {
        int ia = fu < 0.5 ? i - 1 : i, ja = fv < 0.5 ? j - 1 : j;
        double u = fu < 0.5 ? fu + 0.5 : fu - 0.5, v = fv < 0.5 ? fv + 0.5 : fv - 0.5;
        double s00 = same(sGet(ia, ja, cs), cs), s10 = same(sGet(ia + 1, ja, cs), cs), s01 = same(sGet(ia, ja + 1, cs), cs),
                s11 = same(sGet(ia + 1, ja + 1, cs), cs);
        double a = s00 + (s10 - s00) * u, b = s01 + (s11 - s01) * u;
        return a + (b - a) * v;
    }

    private static double sGet(int i, int j, double cs) {
        if (i < 0 || j < 0 || i > N || j > N) {
            return cs;
        }
        int k = i * W + j;
        return (flag[k] & VALID) != 0 ? sp[k] : cs;
    }

    // high sand and water are not mixed (the step is real): only smoothed within the same side
    private static double same(double q, double cs) {
        return (q > 0) == (cs > 0) ? q : cs;
    }

    // ---------------------------------------------------------------- simulation

    private static void spawnGlint(ClientLevel level, Glint g, Vec3 f) {
        for (int k = 0; k < 6; k++) {
            double x = f.x + (rnd() - 0.5) * 80, z = f.z + (rnd() - 0.5) * 80;
            int h = floorFast(level, x, z);
            if (h < -900 || h + 1 > sea) {
                continue;
            }
            g.x = x;
            g.z = z;
            g.sx = (rnd() - 0.5) * 0.5;
            g.sz = (rnd() - 0.5) * 0.5;
            g.t = rnd() * 0.3;
            return;
        }
        g.x = 1e9;
    }

    // 4 arms: born at the edge on one of the arms; the spin itself (fast inside, slow outside) winds the spiral
    private static void spawnSwirl(Swirl s, boolean any) {
        int arm = RANDOM.nextInt(4);
        s.r = any ? 1.5 + rnd() * 17 : 15 + rnd() * 4;
        s.a = arm * Math.PI / 2 + Math.log(s.r) * 1.1 + (rnd() - 0.5) * 0.35;
        s.s = 0.25 + rnd() * 0.35;
        s.ph = rnd() * Mth.TWO_PI;
    }

    private static void step(ClientLevel level, Vec3 f, double dt) {
        for (Glint g : GLINT) {
            if (g.x > 1e8 || Math.abs(g.x - f.x) > 42 || Math.abs(g.z - f.z) > 42) {
                spawnGlint(level, g, f);
                continue;
            }
            g.t -= dt;
            if (g.t < 0) {
                g.sx = (rnd() - 0.5) * 0.5;
                g.sz = (rnd() - 0.5) * 0.5;
                g.t = 0.08 + rnd() * 0.25;
            }
        }
        if (nestKnown) {
            if (!swirlOn) {
                for (Swirl s : SWIRLS) {
                    spawnSwirl(s, true);
                }
                swirlOn = true;
            }
            for (Swirl s : SWIRLS) {
                // free vortex: tangential speed K/r (omega = K/r^2), a solid-body core so it does not blow up; drawn in slowly
                double K = 9, rc = 2.2, w = s.r > rc ? K / (s.r * s.r) : K / (rc * rc);
                s.a += w * dt;
                s.r -= dt * (0.25 + 1.6 / Math.max(1, s.r));
                s.ph += dt;
                if (s.r < 1.2) {
                    spawnSwirl(s, false);
                }
            }
        }
    }

    // ---------------------------------------------------------------- render

    public static void onRenderLevel(RenderLevelStageEvent event) {
        boolean caustic = event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        if (!caustic && event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
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
        double t = (level.getGameTime() % 240000L + partial) / 20.0;
        night = Mth.clamp((0.8f - level.getSkyDarken(partial)) / 0.6f, 0, 1);
        float a = level.getSunAngle(partial);
        SUN[0] = -Math.sin(a);
        SUN[1] = Math.cos(a);
        SUN[2] = 0;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (caustic) {
            // before the water: the water passes over the net of light
            VertexConsumer c = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
            renderCaustics(c, last, cam, level, t, fade);
            buffers.endBatch(FxRenderTypes.GLOW_FLAT);
            return;
        }
        long now = System.nanoTime();
        double dt = lastNanos == 0 || mc.isPaused() ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        Vector3f look = camera.getLookVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        if (dt > 0) {
            step(level, cam, dt);
        }
        VertexConsumer foam = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        GLOWQ.clear();
        renderSurf(foam, last, cam, t, fade);
        renderSwirl(foam, last, cam, fade);
        renderTentacleFoam(foam, last, cam, t, fade);
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
        flushGlow(glow, last, cam);
        renderSwirlGlow(glow, last, cam, fade);
        renderGlints(glow, last, cam, fade);
        buffers.endBatch(FxRenderTypes.GLOW_FLAT);
    }

    private static void v(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        out.addVertex(last, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
    }

    // a soft flat disc (foam, the whirlpool's shade)
    private static void flat(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double r, float cr, float cg, float cb, float a,
            float edge) {
        if (a <= 0.002f) {
            return;
        }
        double px = x + r, pz = z;
        for (int q = 1; q <= 9; q++) {
            double ang = q / 9.0 * Math.PI * 2, nx = x + Math.cos(ang) * r, nz = z + Math.sin(ang) * r;
            v(out, last, cam, x, y, z, cr, cg, cb, a);
            v(out, last, cam, px, y, pz, cr, cg, cb, a * edge);
            v(out, last, cam, nx, y, nz, cr, cg, cb, a * edge);
            v(out, last, cam, nx, y, nz, cr, cg, cb, a * edge);
            px = nx;
            pz = nz;
        }
    }

    // the net of light at each corner, worked out once a frame
    private static final double CR = 24, CST = 0.5;
    private static final int CN = (int) (2 * CR / CST) + 2;
    private static final float[] CS = new float[CN * CN];

    private static double cs(int i, int j, double x, double z, double t) {
        int k = i * CN + j;
        if (Float.isNaN(CS[k])) {
            // where two ripples running different ways cross, the light gathers (a thin crest)
            double a = vnoise(x * 1.3 + t * 0.55, z * 1.3 + t * 0.2), b = vnoise(x * 1.3 - t * 0.35 + 7.1, z * 1.3 + t * 0.45 + 3.3);
            double r = 1 - Math.min(1, Math.abs(a - b) * 3.2);
            CS[k] = (float) (r * r * r * r);
        }
        return CS[k];
    }

    private static void renderCaustics(VertexConsumer out, PoseStack.Pose last, Vec3 cam, ClientLevel level, double t, float fade) {
        double sunUp = Mth.lerp(night, 1f, 0.12f) * fade;
        int x0 = Mth.floor(cam.x - CR), z0 = Mth.floor(cam.z - CR);
        Arrays.fill(CS, Float.NaN);
        int n = (int) (2 * CR / CST);
        for (int i = 0; i < n; i++) {
            double x = x0 + i * CST;
            for (int j = 0; j < n; j++) {
                double z = z0 + j * CST;
                int f = floorFast(level, x, z);
                if (f < -900) {
                    continue;
                }
                double depth = wy - (f + 1);
                if (depth < 0.2 || depth > 7.5 || abyssAt(x, z)) {
                    continue;
                }
                // a sloped bottom: skip the cell that crosses a step (the net stays on each block's floor)
                if (floorFast(level, x + CST, z) != f || floorFast(level, x, z + CST) != f || floorFast(level, x + CST, z + CST) != f) {
                    continue;
                }
                double k = clamp01(1 - depth / 7.5) * clamp01(depth / 0.6) * 0.42 * sunUp;
                double d = Math.hypot(x - cam.x, z - cam.z), fd = clamp01((CR - d) / (CR * 0.3));
                double kk = k * fd;
                if (kk <= 0.002) {
                    continue;
                }
                double v0 = cs(i, j, x, z, t) * kk, v1 = cs(i + 1, j, x + CST, z, t) * kk, v2 = cs(i + 1, j + 1, x + CST, z + CST, t) * kk,
                        v3 = cs(i, j + 1, x, z + CST, t) * kk;
                if (v0 + v1 + v2 + v3 < 0.01) {
                    continue;
                }
                double y = f + 1.01;
                v(out, last, cam, x, y, z, 0.75f, 0.95f, 1f, (float) v0);
                v(out, last, cam, x + CST, y, z, 0.75f, 0.95f, 1f, (float) v1);
                v(out, last, cam, x + CST, y, z + CST, 0.75f, 0.95f, 1f, (float) v2);
                v(out, last, cam, x, y, z + CST, 0.75f, 0.95f, 1f, (float) v3);
            }
        }
    }

    // the foam's corners, worked out once a frame per corner (one for the sand side, one for the water side)
    private static final int D = 2, VW = (N + 1) * D + 1;
    private static final float[] VA = new float[VW * VW * 2], VF = new float[VW * VW * 2];
    private static final int[] VSTAMP = new int[VW * VW * 2];
    private static final double[] VP = new double[VW * VW * 2];
    private static final boolean[] VR = new boolean[VW * VW * 2];
    private static int stamp;
    // the plankton's quads, drawn after the foam in the glowing pass: x, z, y, then 4 alphas
    private static final List<float[]> GLOWQ = new ArrayList<>();

    private static int corner(int i, int j, int u, int v, int side, double x, double z, double t, double cs) {
        int key = (((i * D + u) * VW + (j * D + v)) << 1) | side;
        if (VSTAMP[key] != stamp) {
            foamAt(x, z, t, sAt(i, j, u / (double) D, v / (double) D, cs));
            VA[key] = (float) fA;
            VF[key] = (float) fFront;
            VP[key] = swP;
            VR[key] = swRising;
            VSTAMP[key] = stamp;
        }
        return key;
    }

    private static void renderSurf(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        crestNear = 1e9;
        if (!measured) {
            return;
        }
        stamp++;
        float n = night;
        float cr = Mth.lerp(n, 0.97f, 0.55f), cg = Mth.lerp(n, 0.98f, 0.62f), cb = Mth.lerp(n, 1f, 0.72f);
        double far = 34, st = 1.0 / D;
        int[] K = new int[9];
        for (int i = 0; i <= N; i++) {
            for (int j = 0; j <= N; j++) {
                int k = i * W + j;
                byte fg = flag[k];
                if ((fg & VALID) == 0 || (fg & ABYSS) != 0 || (fg & COAST) == 0 || sp[k] > 1.1) {
                    continue;
                }
                double x0 = gx + i, z0 = gz + j;
                double dc = Math.hypot(x0 + 0.5 - cam.x, z0 + 0.5 - cam.z);
                if (dc > far) {
                    continue;
                }
                double fd = clamp01((far - dc) / 12) * fade;
                double y = Math.max(fl[k] + 1.02, wy + 0.02), cs = sp[k];
                int side = cs > 0 ? 1 : 0;
                for (int u = 0; u <= D; u++) {
                    for (int w = 0; w <= D; w++) {
                        K[u * 3 + w] = corner(i, j, u, w, side, x0 + u * st, z0 + w * st, t, cs);
                    }
                }
                for (int u = 0; u < D; u++) {
                    for (int w = 0; w < D; w++) {
                        int k0 = K[u * 3 + w], k1 = K[(u + 1) * 3 + w], k2 = K[(u + 1) * 3 + w + 1], k3 = K[u * 3 + w + 1];
                        if (VA[k0] + VA[k1] + VA[k2] + VA[k3] < 0.04) {
                            continue;
                        }
                        double xa = x0 + u * st, za = z0 + w * st;
                        float m = (float) fd * Mth.lerp(n, 1f, 0.6f);
                        v(out, last, cam, xa, y, za, cr, cg, cb, Math.min(1, VA[k0]) * m);
                        v(out, last, cam, xa + st, y, za, cr, cg, cb, Math.min(1, VA[k1]) * m);
                        v(out, last, cam, xa + st, y, za + st, cr, cg, cb, Math.min(1, VA[k2]) * m);
                        v(out, last, cam, xa, y, za + st, cr, cg, cb, Math.min(1, VA[k3]) * m);
                        // plankton: lights where the water is being stirred (on the rising front and where the step breaks)
                        if (n > 0.05f) {
                            float g = (float) fd * n;
                            GLOWQ.add(new float[] {(float) xa, (float) za, (float) (y + 0.005), pl(k0) * g, pl(k1) * g, pl(k2) * g, pl(k3) * g});
                        }
                    }
                }
                int k0 = K[0];
                if (VR[k0] && VP[k0] > 0.3 && VP[k0] < 0.4 && dc < crestNear) {
                    crestNear = dc;
                    crestX = x0 + 0.5;
                    crestZ = z0 + 0.5;
                }
            }
        }
    }

    private static float pl(int key) {
        return Math.min(1, VF[key] * 0.55f + VA[key] * 0.15f) * Math.min(1, VA[key]);
    }

    private static void flushGlow(VertexConsumer out, PoseStack.Pose last, Vec3 cam) {
        float st = 1f / D;
        for (float[] q : GLOWQ) {
            if (q[3] + q[4] + q[5] + q[6] < 0.004f) {
                continue;
            }
            v(out, last, cam, q[0], q[2], q[1], 0.15f, 0.65f, 1f, q[3]);
            v(out, last, cam, q[0] + st, q[2], q[1], 0.15f, 0.65f, 1f, q[4]);
            v(out, last, cam, q[0] + st, q[2], q[1] + st, 0.15f, 0.65f, 1f, q[5]);
            v(out, last, cam, q[0], q[2], q[1] + st, 0.15f, 0.65f, 1f, q[6]);
        }
    }

    private static void renderSwirl(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        if (!nestKnown || Math.hypot(nestX - cam.x, nestZ - cam.z) > 140) {
            return;
        }
        float n = night;
        // the funnel: the surface sinks in the middle (dark, deeper = darker)
        for (double r = 7; r >= 1; r -= 1.5) {
            flat(out, last, cam, nestX, wy + 0.02 - 1.2 * Math.exp(-r * r / 14), nestZ, r, 0f, 0.05f, 0.12f, (float) (0.18 + 0.06 * (7 - r)) * fade, 0.6f);
        }
        float cr = Mth.lerp(n, 0.95f, 0.5f), cg = Mth.lerp(n, 0.97f, 0.58f), cb = Mth.lerp(n, 1f, 0.68f);
        for (Swirl s : SWIRLS) {
            double x = nestX + Math.cos(s.a) * s.r, z = nestZ + Math.sin(s.a) * s.r, y = wy + 0.03 - 1.2 * Math.exp(-s.r * s.r / 14);
            double a = 0.55 * clamp01((19 - s.r) / 3) * clamp01((s.r - 1.2) / 1.5) * (0.7 + 0.3 * Math.sin(s.ph * 3));
            flat(out, last, cam, x, y, z, s.s, cr, cg, cb, (float) a * fade, 0);
        }
    }

    private static void renderSwirlGlow(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        if (!nestKnown || night <= 0.05f || Math.hypot(nestX - cam.x, nestZ - cam.z) > 140) {
            return;
        }
        for (Swirl s : SWIRLS) {
            double x = nestX + Math.cos(s.a) * s.r, z = nestZ + Math.sin(s.a) * s.r, y = wy + 0.04 - 1.2 * Math.exp(-s.r * s.r / 14);
            double a = 0.55 * clamp01((19 - s.r) / 3) * clamp01((s.r - 1.2) / 1.5) * (0.7 + 0.3 * Math.sin(s.ph * 3));
            flat(out, last, cam, x, y, z, s.s * 1.4, 0.15f, 0.6f, 1f, (float) (a * 0.5) * fade * night, 0);
        }
    }

    private static void renderTentacleFoam(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        float n = night;
        float cr = Mth.lerp(n, 0.95f, 0.5f), cg = Mth.lerp(n, 0.97f, 0.58f), cb = Mth.lerp(n, 1f, 0.68f);
        for (double[] tt : TENTACLES) {
            if (Math.hypot(tt[0] - cam.x, tt[1] - cam.z) > 120) {
                continue;
            }
            // where the water hits the tentacle's foot: a ring of foam that swells and thins with the ripples
            for (int q = 0; q < 16; q++) {
                double a = q / 16.0 * Math.PI * 2 + 0.2 * Math.sin(t * 0.7 + q), r = 3.4 + 0.5 * Math.sin(t * 1.3 + q * 1.7);
                double al = 0.35 + 0.25 * Math.sin(t * 1.1 + q * 0.9);
                flat(out, last, cam, tt[0] + Math.cos(a) * r, wy + 0.03, tt[1] + Math.sin(a) * r, 0.7, cr, cg, cb, (float) al * fade, 0);
            }
        }
    }

    private static final double[] BR = new double[3], BU = new double[3];

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

    private static void renderGlints(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        // the sun by day, the moon (the other side) at night
        double sx = SUN[0], sy = SUN[1], sz = SUN[2];
        if (night > 0.5f) {
            sx = -sx;
            sy = -sy;
            sz = -sz;
        }
        if (sy <= 0) {
            return;
        }
        boolean n = night > 0.5f;
        double pow = n ? 300 : 260, gain = (n ? 0.5 : 1.6) * fade;
        float cr = n ? 0.7f : 1f, cg = n ? 0.8f : 0.97f, cb = n ? 1f : 0.88f;
        double y = wy + 0.02;
        for (Glint g : GLINT) {
            if (g.x > 1e8) {
                continue;
            }
            double vx = g.x - cam.x, vy = y - cam.y, vz = g.z - cam.z, d = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (d < 1e-3) {
                continue;
            }
            vx /= d;
            vy /= d;
            vz /= d;
            // the ripple's facet: a normal a little tilted; the eye's reflection on it
            double nx = g.sx, ny = 1, nz = g.sz, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= nl;
            ny /= nl;
            nz /= nl;
            double dot = vx * nx + vy * ny + vz * nz, rx = vx - 2 * dot * nx, ry = vy - 2 * dot * ny, rz = vz - 2 * dot * nz;
            double b = Math.pow(Math.max(0, rx * sx + ry * sy + rz * sz), pow) * gain;
            if (b < 0.02) {
                continue;
            }
            double r = 0.035 + d * 0.0022;
            face(cam, g.x, y, g.z);
            double px = g.x + BR[0] * r, py = y + BR[1] * r, pz = g.z + BR[2] * r;
            float al = (float) Math.min(1, b);
            for (int q = 1; q <= 6; q++) {
                double ang = q / 6.0 * Math.PI * 2, ox = Math.cos(ang) * r, oy = Math.sin(ang) * r;
                double qx = g.x + BR[0] * ox + BU[0] * oy, qy = y + BR[1] * ox + BU[1] * oy, qz = g.z + BR[2] * ox + BU[2] * oy;
                v(out, last, cam, g.x, y, g.z, cr, cg, cb, al);
                v(out, last, cam, px, py, pz, cr, cg, cb, 0);
                v(out, last, cam, qx, qy, qz, cr, cg, cb, 0);
                v(out, last, cam, qx, qy, qz, cr, cg, cb, 0);
                px = qx;
                py = qy;
                pz = qz;
            }
        }
    }

    // ---------------------------------------------------------------- the screen and the fog

    public static boolean gradeOn() {
        return active() && FxShaders.jjGrade != null;
    }

    // Too beautiful to be normal: colors too vivid, warm by day; blue at night, never darkening.
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.jjGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        float n = night;
        sh.safeGetUniform("Sat").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.28f, 1.15f)));
        sh.safeGetUniform("Contrast").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.05f, 1.03f)));
        // by day the warm light over it all (the page's warm glow and brightness); none at night
        sh.safeGetUniform("Wash").set(1f, 240 / 255f, 200 / 255f, 0.07f * (1 - n) * fade);
        sh.safeGetUniform("Warm").set(0f);
        sh.safeGetUniform("Vig").set(10 / 255f, 30 / 255f, 70 / 255f, 0.22f * n * fade);
        sh.safeGetUniform("Flash").set(0f);
        FxPost.pass(sh);
    }

    // The fog: a bright sea haze by day, deep blue at night (only the hue moves, never down).
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float k = fade * 0.7f;
        float r = Mth.lerp(night, 0xbf / 255f, 0x0a / 255f), g = Mth.lerp(night, 0xe6 / 255f, 0x1a / 255f), b = Mth.lerp(night, 0xf0 / 255f, 0x30 / 255f);
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
        // (under water the game's own water fog stays)
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) {
            return;
        }
        float near = 30f, far = Mth.lerp(night, 240f, 110f);
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
        if (nestKnown && (abyssLoop == null || !mc.getSoundManager().isActive(abyssLoop))) {
            abyssLoop = new AbyssLoop();
            mc.getSoundManager().play(abyssLoop);
        }
        double t = level.getGameTime() / 20.0;
        // the wave breaks: the crash comes when the near front passes over the sand
        if (crestNear < 40 && t - lastSurf > T * 0.8) {
            lastSurf = t;
            double k = clamp01(1 - crestNear / 40);
            play(level, crestX, sea + 1, crestZ, ModSounds.DAGON_ONDA.get(), (float) (0.3 + 2.2 * k), 0.92f + (float) rnd() * 0.16f);
        }
    }

    // The sea wind all round the player.
    private static final class WindLoop extends AbstractTickableSoundInstance {
        WindLoop() {
            super(ModSounds.DAGON_VENTO.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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
            volume = 0.3f * strength;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    // The abyss's deep rumble, louder near the Nest.
    private static final class AbyssLoop extends AbstractTickableSoundInstance {
        AbyssLoop() {
            super(ModSounds.DAGON_ABISMO.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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
            Minecraft mc = Minecraft.getInstance();
            if (strength <= 0 || !nestKnown || mc.player == null) {
                stop();
                return;
            }
            double d = Math.hypot(mc.player.getX() - nestX, mc.player.getZ() - nestZ);
            volume = (float) Math.max(0.001, 0.9 * Math.pow(clamp01(1 - d / 70), 2) * strength);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
