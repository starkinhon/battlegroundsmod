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
import com.starkinhon.battlegroundsmod.ModBlocks;
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
import net.minecraft.tags.FluidTags;
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

// The air of the Aldeia Amaldiçoada (Stark 2026-11-19 on the preview forja/jujutsu-aldeia-ambiente). amb.js there is the
// source of truth and this is its port, the same names where it helps. Fades in when the player walks into the biome and
// out when the player leaves (BiomeAura); the crows overhead stay JujutsuAirFx's. Everything makes physical sense:
// - ONE wind for everything: gusts crossing the land at ~5 blocks/s. Where a gust hits the still water (swamp and paddies)
//   the water ripples and stops mirroring the bright sky: a darker, matte patch running over the water at the wind's
//   speed (a "cat's paw"); it makes the lanterns' flames tremble more and pushes the will-o'-wisps;
// - a paper lantern is a candle inside paper: the light flickers (a flame), now and then nearly goes out and comes back;
//   lit only at night; it throws a warm pool of light on the ground below and draws moths that circle it;
// - the Japanese firefly (genji-botaru) blinks with its neighbors: each its own rhythm (~2.6 s), but it pulls the rhythm
//   of those it sees near (the Kuramoto model), so in minutes the swamp blinks in synchronized waves. It flies low, over
//   water and reeds, and the still water mirrors the glow;
// - the swamp and the paddies let out gas (methane) from the bottom: it pops on the surface in little bubbles, in groups;
// - at night the gas sometimes catches fire by itself: the hitodama (will-o'-wisp), a bluish flame that rises slowly,
//   drifts with the wind and goes out. More of them near the cage, where the village locked away whoever saw curses;
// - the screen (fx_jjgrade): a dull olive swamp by day; a cold teal at night, so the lanterns' light stays warm (never
//   darkening);
// - sounds built from noise and tones (tools/sounds/aldeia_sounds.py): the rice in the gusts, the evening cicada by day,
//   the frogs at night, a bubble popping close by.
public final class AldeiaAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WIND = 0.6;
    private static final int SCAN = 64;
    private static final int FLIES = 320;
    private static final double WATER_TOP = 0.889;

    private static float strength, lastStrength;
    private static float night;
    private static long lastNanos;
    private static ClientLevel lastLevel;
    private static double lookX, lookY, lookZ = 1;

    // ---------------------------------------------------------------- what the world has (a row of columns a tick)

    private static final class Lantern {
        double x, y, z, ph, g, fl = 0.8;
    }

    private static final class Scan {
        final List<Lantern> lanterns = new ArrayList<>();
        final List<double[]> wet = new ArrayList<>();
        double cx, cz;
        int cn;

        void clear() {
            lanterns.clear();
            wet.clear();
            cx = cz = 0;
            cn = 0;
        }
    }

    private static Scan cur = new Scan(), next = new Scan();
    private static int scanRow = -SCAN - 1, scanCx, scanCz;
    private static boolean firstPass = true;
    // the cage (its talismans' middle), once seen
    private static double cageX, cageZ;
    private static boolean cageKnown;

    // ---------------------------------------------------------------- moving things

    private static final class Fly {
        double x = 1e9, y, z, vx, vy, vz, ph, w, dph, age, life;
    }

    private static final Fly[] FLY = new Fly[FLIES];

    static {
        for (int i = 0; i < FLIES; i++) {
            FLY[i] = new Fly();
        }
    }

    private static final class Moth {
        Lantern l;
        double a, r, h, s, ph, bump;
    }

    private static final List<Moth> MOTHS = new ArrayList<>();

    private static final class Bubble {
        double x, y, z, r, age, life;
    }

    private static final List<Bubble> BUBBLES = new ArrayList<>();
    private static double bubAcc;

    private static final class Wisp {
        double x, y, z, age, life, ph;
        final double[][] trail = new double[14][];
        int tn;
    }

    private static final List<Wisp> WISPS = new ArrayList<>();
    private static double nextWisp = 2;

    // sounds
    private static RiceLoop riceLoop;
    private static float windGust;
    private static double nextFrog = 1, nextCicada = 2;
    private static int pops;

    private AldeiaAirFx() {
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

    private static int top(ClientLevel level, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!level.hasChunk(ix >> 4, iz >> 4)) {
            return -999;
        }
        int h = ClientHeights.noLeaves(level, ix, iz) - 1;
        return h <= level.getMinBuildHeight() ? -999 : h;
    }

    private static final BlockPos.MutableBlockPos WP = new BlockPos.MutableBlockPos();

    // the water's surface in the column (a water block is 8/9 full), or NaN if the column does not end in water
    private static double waterY(ClientLevel level, double x, double z) {
        int h = top(level, x, z);
        if (h < -900) {
            return Double.NaN;
        }
        int ix = Mth.floor(x), iz = Mth.floor(z);
        BlockState s = level.getBlockState(WP.set(ix, h, iz));
        if (s.is(Blocks.LILY_PAD)) {
            h--;
            s = level.getBlockState(WP.set(ix, h, iz));
        }
        return s.getFluidState().is(FluidTags.WATER) ? h + WATER_TOP : Double.NaN;
    }

    // the wind: a fixed direction, gusts running at 5 blocks/s; 0 calm, 1 a full gust
    private static double gust(double x, double z, double t) {
        double c = Math.cos(WIND), s = Math.sin(WIND), along = x * c + z * s, across = -x * s + z * c;
        double band = 0.5 + 0.5 * Math.sin((along - t * 5) / 2.2 + vnoise(across / 18, t * 0.05) * 3);
        double patch = vnoise(along / 22 - t * 0.22, across / 14);
        return clamp01(band * band * (0.4 + patch * 1.1));
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    // ---------------------------------------------------------------- tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            if (lastStrength > 0 || cageKnown || !cur.wet.isEmpty()) {
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
        boolean in = BiomeAura.in("aldeia_amaldicoada_ar", b -> b.is(ModWorldgen.ALDEIA_AMALDICOADA));
        strength = Mth.clamp(strength + (in ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            if (lastStrength > 0) {
                reset(false);
            }
            return;
        }
        ClientLevel level = mc.level;
        Vec3 p = mc.player.position();
        int rows = firstPass ? 12 : 1;
        for (int i = 0; i < rows; i++) {
            scanStep(level, p);
        }
        sounds(mc, level, p);
    }

    private static boolean terrain(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.MUD) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.STONE)
                || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.CLAY) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL) || s.is(JujutsuBlocks.SOAKED_GRASS.get())
                || s.is(JujutsuBlocks.CLAY_DIKE.get());
    }

    private static void scanStep(ClientLevel level, Vec3 p) {
        if (scanRow > SCAN) {
            // the moths stay with the lanterns that are still there
            Scan done = next;
            next = cur;
            cur = done;
            next.clear();
            firstPass = false;
            afterPass();
            scanRow = -SCAN - 1;
        }
        if (scanRow == -SCAN - 1) {
            scanCx = Mth.floor(p.x);
            scanCz = Mth.floor(p.z);
            scanRow = -SCAN;
        }
        int x = scanCx + scanRow++;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Block shide = ModBlocks.SHIDE.get();
        for (int z = scanCz - SCAN; z <= scanCz + SCAN; z++) {
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int hi = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int lo = Math.max(level.getMinBuildHeight() + 1, hi - 40);
            for (int y = hi; y >= lo; y--) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                if (s.isAir()) {
                    continue;
                }
                if (s.getFluidState().is(FluidTags.WATER)) {
                    // the still water's top (swamp and paddies)
                    next.wet.add(new double[] {x, y + WATER_TOP, z});
                    break;
                }
                if (s.is(Blocks.LANTERN)) {
                    Lantern L = new Lantern();
                    L.x = x + 0.5;
                    L.y = y + 0.5;
                    L.z = z + 0.5;
                    L.ph = rnd() * 100;
                    int g = y - 1;
                    while (g > y - 12 && !level.getBlockState(pos.set(x, g, z)).blocksMotion()) {
                        g--;
                    }
                    L.g = g + 1;
                    next.lanterns.add(L);
                    pos.set(x, y, z);
                } else if (s.is(shide)) {
                    next.cx += x + 0.5;
                    next.cz += z + 0.5;
                    next.cn++;
                }
                if (terrain(s)) {
                    break;
                }
            }
        }
    }

    private static void afterPass() {
        if (cur.cn >= 3) {
            cageX = cur.cx / cur.cn;
            cageZ = cur.cz / cur.cn;
            cageKnown = true;
        }
        // four moths a lantern; a lantern seen again keeps its moths (matched by place)
        List<Moth> kept = new ArrayList<>();
        for (Lantern L : cur.lanterns) {
            int n = 0;
            for (Moth m : MOTHS) {
                if (n < 4 && m.l.x == L.x && m.l.y == L.y && m.l.z == L.z) {
                    m.l = L;
                    kept.add(m);
                    n++;
                }
            }
            for (; n < 4; n++) {
                Moth m = new Moth();
                m.l = L;
                m.a = rnd() * Mth.TWO_PI;
                m.r = 0.35 + rnd() * 0.7;
                m.h = (rnd() - 0.5) * 0.8;
                m.s = (rnd() < 0.5 ? -1 : 1) * (2.5 + rnd() * 3);
                m.ph = rnd() * Mth.TWO_PI;
                kept.add(m);
            }
        }
        MOTHS.clear();
        MOTHS.addAll(kept);
    }

    private static void reset(boolean all) {
        strength = 0;
        firstPass = true;
        scanRow = -SCAN - 1;
        next = new Scan();
        for (Fly f : FLY) {
            f.x = 1e9;
        }
        BUBBLES.clear();
        WISPS.clear();
        nextWisp = 2;
        bubAcc = 0;
        if (riceLoop != null) {
            riceLoop.end();
            riceLoop = null;
        }
        if (all) {
            cur = new Scan();
            MOTHS.clear();
            cageKnown = false;
        }
    }

    // ---------------------------------------------------------------- simulation

    private static void spawnFly(ClientLevel level, Fly f, Vec3 c, boolean any) {
        List<double[]> wet = cur.wet;
        for (int k = 0; k < 10 && !wet.isEmpty(); k++) {
            double[] w = wet.get(RANDOM.nextInt(wet.size()));
            if (Math.abs(w[0] - c.x) > 34 || Math.abs(w[2] - c.z) > 34) {
                continue;
            }
            f.x = w[0] + rnd();
            f.z = w[2] + rnd();
            f.y = w[1] + 0.3 + rnd() * 2.2;
            f.vx = f.vy = f.vz = 0;
            f.ph = rnd() * Mth.TWO_PI;
            f.w = Mth.TWO_PI / (2.1 + rnd() * 1.0);
            f.dph = f.w;
            f.age = any ? 99 : 0;
            f.life = 30 + rnd() * 60 + (any ? 99 : 0);
            return;
        }
        f.x = 1e9;
    }

    private static void step(ClientLevel level, Vec3 c, double dt, double t) {
        if (night > 0.05f) {
            // fireflies: Kuramoto with the neighbors within 6 blocks (each pulls the phase of the others it sees blink)
            double K = 0.35;
            boolean fresh = true;
            for (Fly a : FLY) {
                if (a.x < 1e8) {
                    fresh = false;
                    break;
                }
            }
            for (int i = 0; i < FLIES; i++) {
                Fly a = FLY[i];
                if (a.x > 1e8) {
                    spawnFly(level, a, c, fresh);
                    continue;
                }
                double s = 0;
                int cnt = 0;
                for (int j = 0; j < FLIES; j++) {
                    Fly b = FLY[j];
                    if (i == j || b.x > 1e8) {
                        continue;
                    }
                    double dx = b.x - a.x, dz = b.z - a.z;
                    if (dx * dx + dz * dz < 36) {
                        s += Math.sin(b.ph - a.ph);
                        cnt++;
                    }
                }
                a.dph = a.w + (cnt > 0 ? K * s / cnt : 0);
            }
            for (Fly a : FLY) {
                if (a.x > 1e8) {
                    continue;
                }
                a.ph = (a.ph + a.dph * dt) % Mth.TWO_PI;
                if (a.ph < 0) {
                    a.ph += Mth.TWO_PI;
                }
                // flight: drifts slowly, rises a little when lit (the male blinks flying), never into the water or the ground
                a.vx += (rnd() - 0.5) * dt * 1.2;
                a.vz += (rnd() - 0.5) * dt * 1.2;
                a.vy += ((a.ph < 1.2 ? 0.15 : -0.05) - a.vy) * dt;
                double sp = Math.hypot(a.vx, a.vz);
                if (sp > 0.5) {
                    a.vx *= 0.5 / sp;
                    a.vz *= 0.5 / sp;
                }
                double ox = a.x, oz = a.z;
                a.x += a.vx * dt;
                a.z += a.vz * dt;
                a.y += a.vy * dt;
                int g0 = top(level, a.x, a.z);
                double g = g0 + 1;
                if (g0 > -900 && g - a.y > 1.5) {
                    // a wall: turn back
                    a.x = ox;
                    a.z = oz;
                    a.vx = -a.vx;
                    a.vz = -a.vz;
                    g = top(level, a.x, a.z) + 1;
                }
                if (a.y < g + 0.25) {
                    a.y = g + 0.25;
                    a.vy = Math.abs(a.vy);
                }
                if (a.y > g + 3) {
                    a.vy -= dt;
                }
                a.age += dt;
                if (a.age > a.life || Math.abs(a.x - c.x) > 38 || Math.abs(a.z - c.z) > 38 || g0 < -900) {
                    spawnFly(level, a, c, false);
                }
            }
            // moths: circle the lantern (steering by the light makes the spiral), bump into it and come back
            for (Moth m : MOTHS) {
                m.a += m.s * dt / m.r;
                m.ph += dt * 7;
                m.r += (0.6 + 0.4 * Math.sin(m.ph * 0.13) - m.r) * dt * 0.8;
                if (m.bump > 0) {
                    m.bump -= dt;
                } else if (rnd() < dt * 0.25) {
                    m.bump = 0.25;
                    m.r = 0.2;
                }
                m.h += (rnd() - 0.5) * dt * 1.5;
                m.h *= 1 - dt * 0.5;
            }
            // will-o'-wisp: rare, rises and goes with the wind, goes out
            nextWisp -= dt;
            if (nextWisp < 0 && WISPS.size() < 3 && !cur.wet.isEmpty()) {
                nextWisp = 5 + rnd() * 9;
                boolean byCage = cageKnown && rnd() < 0.5;
                for (int k = 0; k < 20; k++) {
                    double[] w = cur.wet.get(RANDOM.nextInt(cur.wet.size()));
                    if (byCage ? Math.hypot(w[0] - cageX, w[2] - cageZ) > 16 : Math.abs(w[0] - c.x) > 45 || Math.abs(w[2] - c.z) > 45) {
                        continue;
                    }
                    if (Math.hypot(w[0] - c.x, w[2] - c.z) < 8) {
                        continue;
                    }
                    Wisp p = new Wisp();
                    p.x = w[0] + 0.5;
                    p.z = w[2] + 0.5;
                    p.y = w[1] + 0.4;
                    p.life = 6 + rnd() * 5;
                    p.ph = rnd() * Mth.TWO_PI;
                    WISPS.add(p);
                    break;
                }
            }
            double wc = Math.cos(WIND), ws = Math.sin(WIND);
            for (int i = WISPS.size() - 1; i >= 0; i--) {
                Wisp p = WISPS.get(i);
                p.age += dt;
                double gw = gust(p.x, p.z, t);
                p.x += (wc * (0.15 + 0.5 * gw) + Math.sin(p.age * 1.3 + p.ph) * 0.12) * dt;
                p.z += (ws * (0.15 + 0.5 * gw) + Math.cos(p.age * 1.1 + p.ph) * 0.12) * dt;
                p.y += (0.22 + 0.06 * Math.sin(p.age * 2 + p.ph)) * dt;
                // the trail: the last 14 places, newest first
                System.arraycopy(p.trail, 0, p.trail, 1, p.trail.length - 1);
                p.trail[0] = new double[] {p.x, p.y, p.z};
                p.tn = Math.min(p.trail.length, p.tn + 1);
                if (p.age >= p.life) {
                    WISPS.remove(i);
                }
            }
        }
        // gas from the bottom: pops in little groups of bubbles (day and night)
        bubAcc += dt * 2.2;
        while (bubAcc > 1 && !cur.wet.isEmpty()) {
            bubAcc -= 1;
            double cx = 0, cz = 0, wy = Double.NaN;
            for (int k = 0; k < 12 && Double.isNaN(wy); k++) {
                cx = c.x + (rnd() - 0.5) * 52;
                cz = c.z + (rnd() - 0.5) * 52;
                wy = waterY(level, cx, cz);
            }
            if (Double.isNaN(wy)) {
                continue;
            }
            int k = 2 + RANDOM.nextInt(5);
            for (int i = 0; i < k; i++) {
                Bubble b = new Bubble();
                b.x = cx + (rnd() - 0.5) * 0.3;
                b.y = wy + 0.015;
                b.z = cz + (rnd() - 0.5) * 0.3;
                b.r = 0.05 + rnd() * 0.06;
                b.age = -i * (0.08 + rnd() * 0.2);
                b.life = 0.4 + rnd() * 1.2;
                BUBBLES.add(b);
            }
        }
        for (int i = BUBBLES.size() - 1; i >= 0; i--) {
            Bubble b = BUBBLES.get(i);
            double was = b.age;
            b.age += dt;
            if (was < b.life - 0.05 && b.age >= b.life - 0.05 && Math.hypot(b.x - c.x, b.z - c.z) < 10) {
                pops++;
            }
            if (b.age >= b.life) {
                BUBBLES.remove(i);
            }
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
        VertexConsumer area = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        // "Partículas do ambiente" off (Experiência do jogador): the bubbles, moths, fireflies and wisps go; the gusts on
        // the water and the lanterns stay
        boolean amb = FxConfig.ambient();
        renderGusts(area, last, cam, t, fade);
        if (amb) {
            renderBubbles(area, last, cam, fade);
        }
        if (amb && night > 0.05f) {
            renderMoths(area, last, cam, fade * night);
        }
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        if (night > 0.05f) {
            VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
            float k = fade * night;
            renderLanterns(glow, last, cam, t, k);
            if (amb) {
                renderFlies(glow, last, cam, level, k);
                renderWisps(glow, last, cam, level, k);
            }
            buffers.endBatch(FxRenderTypes.GLOW_FLAT);
        }
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

    private static void disc(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double s, int seg, float r, float g, float b,
            float a, float edge) {
        if (a <= 0.002f) {
            return;
        }
        face(cam, x, y, z);
        double px = x + BR[0] * s, py = y + BR[1] * s, pz = z + BR[2] * s;
        for (int q = 1; q <= seg; q++) {
            double ang = q / (double) seg * Math.PI * 2, cx = Math.cos(ang) * s, cy = Math.sin(ang) * s;
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

    // a soft round spot lying flat (a pool of light, a bubble's ring)
    private static void flat(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double r, float cr, float cg, float cb, float a) {
        if (a <= 0.002f) {
            return;
        }
        double px = x + r, pz = z;
        for (int q = 1; q <= 10; q++) {
            double ang = q / 10.0 * Math.PI * 2, nx = x + Math.cos(ang) * r, nz = z + Math.sin(ang) * r;
            v(out, last, cam, x, y, z, cr, cg, cb, a);
            v(out, last, cam, px, y, pz, cr, cg, cb, 0);
            v(out, last, cam, nx, y, nz, cr, cg, cb, 0);
            v(out, last, cam, nx, y, nz, cr, cg, cb, 0);
            px = nx;
            pz = nz;
        }
    }

    // the gust at a block corner, worked out once a frame per corner
    private static final int GR = 37, GN = 2 * GR + 2;
    private static final float[] GUST = new float[GN * GN];
    private static int gx0, gz0;

    private static double gustAt(int x, int z, double t) {
        int i = x - gx0, j = z - gz0;
        if (i < 0 || j < 0 || i >= GN || j >= GN) {
            return gust(x, z, t);
        }
        int k = i * GN + j;
        if (Float.isNaN(GUST[k])) {
            GUST[k] = (float) gust(x, z, t);
        }
        return GUST[k];
    }

    // A gust on the water: where it hits, the still water (swamp and paddy) ripples and stops mirroring the bright sky. A
    // darker, matte patch that runs over the water at the wind's speed ("cat's paw").
    private static void renderGusts(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        float n = night;
        float cr = Mth.lerp(n, 0.08f, 0.01f), cg = Mth.lerp(n, 0.1f, 0.02f), cb = Mth.lerp(n, 0.08f, 0.02f), k = Mth.lerp(n, 0.5f, 0.32f) * fade;
        double R = 36;
        Arrays.fill(GUST, Float.NaN);
        gx0 = Mth.floor(cam.x) - GR;
        gz0 = Mth.floor(cam.z) - GR;
        for (double[] w : cur.wet) {
            int x = (int) w[0], z = (int) w[2];
            if (Math.abs(x - cam.x) > R || Math.abs(z - cam.z) > R) {
                continue;
            }
            double fd = clamp01((R - Math.hypot(x - cam.x, z - cam.z)) / 10);
            if (fd <= 0) {
                continue;
            }
            double g0 = gustAt(x, z, t), g1 = gustAt(x + 1, z, t), g2 = gustAt(x + 1, z + 1, t), g3 = gustAt(x, z + 1, t);
            if (g0 + g1 + g2 + g3 < 0.1) {
                continue;
            }
            // the rippled water's grain: fine noise running with the wind
            double y = w[1] + 0.015;
            float a = (float) (k * fd);
            v(out, last, cam, x, y, z, cr, cg, cb, (float) (g0 * g0 * rough(x, z, t)) * a);
            v(out, last, cam, x + 1, y, z, cr, cg, cb, (float) (g1 * g1 * rough(x + 1, z, t)) * a);
            v(out, last, cam, x + 1, y, z + 1, cr, cg, cb, (float) (g2 * g2 * rough(x + 1, z + 1, t)) * a);
            v(out, last, cam, x, y, z + 1, cr, cg, cb, (float) (g3 * g3 * rough(x, z + 1, t)) * a);
        }
    }

    private static double rough(double x, double z, double t) {
        return 0.7 + 0.3 * vnoise(x * 1.7 - t * 3, z * 1.7 - t * 2.2);
    }

    private static void renderBubbles(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        float n = night;
        for (Bubble b : BUBBLES) {
            if (b.age < 0) {
                continue;
            }
            double d = Math.hypot(b.x - cam.x, b.z - cam.z);
            if (d > 26) {
                continue;
            }
            // the bubble sits on the surface and grows a little before it pops; its shine is the sky mirrored on the dome
            double k = b.age / b.life, r = b.r * (0.7 + 0.3 * k), a = Math.sin(Math.min(1, k * 1.15) * Math.PI) * clamp01(1.4 - d / 26) * fade;
            flat(out, last, cam, b.x, b.y, b.z, r * 1.6, Mth.lerp(n, 0.32f, 0.12f), Mth.lerp(n, 0.34f, 0.16f), Mth.lerp(n, 0.28f, 0.14f), (float) (a * 0.5));
            disc(out, last, cam, b.x, b.y + r * 0.5, b.z, r * 0.45, 8, Mth.lerp(n, 0.9f, 0.5f), Mth.lerp(n, 0.92f, 0.6f), Mth.lerp(n, 0.85f, 0.65f),
                    (float) (a * 0.75), 0.3f);
        }
    }

    private static void renderLanterns(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float k) {
        for (Lantern L : cur.lanterns) {
            double d = Math.sqrt((L.x - cam.x) * (L.x - cam.x) + (L.y - cam.y) * (L.y - cam.y) + (L.z - cam.z) * (L.z - cam.z));
            if (d > 70) {
                continue;
            }
            // a candle flame: quick noise plus the wind getting into the paper; now and then it nearly goes out and comes back
            double gw = gust(L.x, L.z, t);
            double fl = 0.78 + 0.14 * Math.sin(t * 11 + L.ph) * Math.sin(t * 7.3 + L.ph * 2) + 0.08 * vnoise(t * 6 + L.ph, L.ph) - gw * 0.22 * vnoise(t * 3, L.ph * 3);
            L.fl = fl;
            float f = (float) fl * k;
            disc(out, last, cam, L.x, L.y, L.z, 0.75, 12, 1f, 0.82f, 0.55f, 0.7f * f, 0);
            disc(out, last, cam, L.x, L.y, L.z, 1.7, 12, 1f, 0.62f, 0.25f, 0.34f * f, 0);
            disc(out, last, cam, L.x, L.y, L.z, 4.2, 12, 1f, 0.62f, 0.25f, 0.1f * f, 0);
            // the pool of light on the ground below (falls off with the height squared)
            double h = Math.max(1, L.y - L.g);
            flat(out, last, cam, L.x, L.g + 0.02, L.z, 1.6 + h * 0.8, 1f, 0.62f, 0.25f, (float) Math.min(0.45, 2.4 / (h * h)) * f);
        }
    }

    private static void renderMoths(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float k) {
        for (Moth m : MOTHS) {
            Lantern L = m.l;
            double x = L.x + Math.cos(m.a) * m.r, z = L.z + Math.sin(m.a) * m.r, y = L.y + m.h + Math.sin(m.ph) * 0.06;
            double d = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
            if (d > 20) {
                continue;
            }
            // a moth is seen because the light hits it: closer to the flame, brighter; the wing beats (blinks a little)
            double lit = clamp01(1.2 - m.r) * L.fl * (0.6 + 0.4 * Math.abs(Math.sin(m.ph * 3)));
            disc(out, last, cam, x, y, z, 0.035, 6, 0.95f, 0.82f, 0.62f, (float) (0.85 * lit) * k, 0.6f);
        }
    }

    private static void renderFlies(VertexConsumer out, PoseStack.Pose last, Vec3 cam, ClientLevel level, float k) {
        for (Fly f : FLY) {
            if (f.x > 1e8) {
                continue;
            }
            // the blink: lights fast and fades slowly (~0.5 s) at the start of the cycle
            double p = f.ph, I = p < 0.4 ? p / 0.4 : p < 1.4 ? Math.pow(1 - (p - 0.4) / 1.0, 2) : 0;
            if (I < 0.02) {
                continue;
            }
            double d = Math.sqrt((f.x - cam.x) * (f.x - cam.x) + (f.y - cam.y) * (f.y - cam.y) + (f.z - cam.z) * (f.z - cam.z));
            if (d > 40) {
                continue;
            }
            double s = Math.max(0.05, d * 0.004);
            float a = (float) (I * clamp01(1.3 - d / 40)) * k;
            disc(out, last, cam, f.x, f.y, f.z, s, 8, 0.78f, 1f, 0.3f, a, 0.3f);
            disc(out, last, cam, f.x, f.y, f.z, s * 6, 12, 0.78f, 1f, 0.3f, a * 0.14f, 0);
            // its reflection in the still water just below (a mirror: the same distance under the surface), weaker
            double wy = waterY(level, f.x, f.z);
            if (!Double.isNaN(wy)) {
                disc(out, last, cam, f.x, 2 * wy - f.y, f.z, s * 1.2, 8, 0.78f, 1f, 0.3f, a * 0.35f, 0.2f);
            }
        }
    }

    private static void renderWisps(VertexConsumer out, PoseStack.Pose last, Vec3 cam, ClientLevel level, float k) {
        for (Wisp p : WISPS) {
            float a = (float) (clamp01(p.age / 1.2) * clamp01((p.life - p.age) / 1.8)) * k;
            float fl = (float) (0.85 + 0.15 * Math.sin(p.age * 13 + p.ph));
            // the tail: its own path, smaller and dimmer (a gas flame leaves warm air rising behind it)
            for (int i = p.tn - 1; i >= 1; i--) {
                double[] q = p.trail[i];
                double u = i / (double) p.tn;
                disc(out, last, cam, q[0], q[1] - u * 0.25, q[2], 0.22 * (1 - u), 10, 0.35f, 0.6f, 1f, (float) (a * 0.22 * (1 - u)), 0);
            }
            disc(out, last, cam, p.x, p.y, p.z, 0.16, 10, 0.75f, 0.9f, 1f, a * 0.85f * fl, 0.25f);
            disc(out, last, cam, p.x, p.y, p.z, 0.9, 12, 0.3f, 0.55f, 1f, a * 0.16f * fl, 0);
            // its cold light on the water below
            double wy = waterY(level, p.x, p.z), gy = !Double.isNaN(wy) ? wy : top(level, p.x, p.z) + 1;
            flat(out, last, cam, p.x, gy + 0.02, p.z, 1.6, 0.25f, 0.45f, 1f, (float) (a * 0.12 / Math.max(1, (p.y - gy) * 0.6)));
        }
    }

    // ---------------------------------------------------------------- the screen and the fog

    public static boolean gradeOn() {
        return active() && FxShaders.jjGrade != null;
    }

    // The swamp: a dull olive by day; a cold teal at night, so the lanterns' light stays warm (never darkening).
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.jjGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        float n = night;
        sh.safeGetUniform("Sat").set(Mth.lerp(fade, 1f, Mth.lerp(n, 0.8f, 0.92f)));
        sh.safeGetUniform("Contrast").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.05f, 1.04f)));
        // by day the old-photo olive (the page's sepia and olive wash); none at night
        sh.safeGetUniform("Wash").set(120 / 255f, 130 / 255f, 70 / 255f, 0.1f * (1 - n) * fade);
        sh.safeGetUniform("Warm").set(0f);
        sh.safeGetUniform("Vig").set(Mth.lerp(n, 40, 8) / 255f, Mth.lerp(n, 44, 24) / 255f, Mth.lerp(n, 24, 26) / 255f, Mth.lerp(n, 0.32f, 0.3f) * fade);
        sh.safeGetUniform("Flash").set(0f);
        FxPost.pass(sh);
    }

    // The fog: a grey-green humid haze by day, a dark teal at night (only the hue moves, never down).
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float k = fade * 0.75f;
        float r = Mth.lerp(night, 0x8f / 255f, 0x0f / 255f), g = Mth.lerp(night, 0x98 / 255f, 0x15 / 255f), b = Mth.lerp(night, 0x8a / 255f, 0x17 / 255f);
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
        float near = 6f, far = Mth.lerp(night, 110f, 60f);
        event.setNearPlaneDistance(Mth.lerp(fade, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), near)));
        event.setFarPlaneDistance(Mth.lerp(fade, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), far)));
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- sound

    private static void play(ClientLevel level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playLocalSound(x, y, z, s, SoundSource.AMBIENT, vol * strength, pitch, false);
    }

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 p) {
        if (riceLoop == null || !mc.getSoundManager().isActive(riceLoop)) {
            riceLoop = new RiceLoop();
            mc.getSoundManager().play(riceLoop);
        }
        double t = (level.getGameTime() % 240000L) / 20.0, dt = 0.05;
        windGust = (float) gust(p.x, p.z, t);
        float n = Mth.clamp((0.8f - level.getSkyDarken(1f)) / 0.6f, 0, 1);
        if (n > 0.5f) {
            // the paddy frogs: short croaks in a row, each frog its own pitch
            nextFrog -= dt;
            if (nextFrog < 0) {
                double a = rnd() * Mth.TWO_PI, r = 5 + rnd() * 14;
                play(level, p.x + Math.cos(a) * r, p.y, p.z + Math.sin(a) * r, ModSounds.ALDEIA_SAPO.get(), 1.2f, 0.85f + (float) rnd() * 0.3f);
                nextFrog = 0.3 + rnd() * 1.2;
            }
        } else {
            // higurashi: "ka-na-ka-na", notes falling and fading
            nextCicada -= dt;
            if (nextCicada < 0) {
                double a = rnd() * Mth.TWO_PI, r = 8 + rnd() * 14;
                play(level, p.x + Math.cos(a) * r, p.y + 4, p.z + Math.sin(a) * r, ModSounds.ALDEIA_HIGURASHI.get(), 1.3f, 0.97f + (float) rnd() * 0.06f);
                nextCicada = 4 + rnd() * 6;
            }
        }
        // a gas bubble popping close by
        if (pops > 0) {
            pops = 0;
            double a = rnd() * Mth.TWO_PI;
            play(level, p.x + Math.cos(a) * 4, p.y - 1, p.z + Math.sin(a) * 4, ModSounds.ALDEIA_BOLHA.get(), 0.5f, 0.9f + (float) rnd() * 0.3f);
        }
    }

    // The rice rustling in the gusts, all round the player; louder and higher with the gust.
    private static final class RiceLoop extends AbstractTickableSoundInstance {
        RiceLoop() {
            super(ModSounds.ALDEIA_ARROZ.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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
            volume = (0.18f + 0.82f * windGust) * 0.5f * strength;
            pitch = 0.85f + 0.5f * windGust;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
