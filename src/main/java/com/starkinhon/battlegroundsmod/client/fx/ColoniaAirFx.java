package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The air of the Colônia do Jogo do Abate (Stark 2026-11-14: Jujutsu's ambience "muito inferior" to the Deserto do Vento
// and the Deserto do Diabo; doc "Ambientação dos biomas de Jujutsu", preview forja/jujutsu-colonia-ambiente approved to
// be seen in the game). amb.js there is the source of truth and this is its line-by-line port; the same names where it
// helps. Fades in when the player walks into the biome and out when the player leaves (BiomeAura).
// - the barrier: a dark see-through dome fixed over each domed colony (the server tells where: ColoniaDomeSync), veins
//   of energy running over it
//   (souls_colonia mode 0); from inside it the top of the sky goes violet, the fires' orange glows low on the horizon at
//   night (mode 1, behind everything);
// - smoke columns: every building on fire (embers with fire on top) near the player sends up its own column, leaning
//   with the wind and thicker in a gust; each cloud has its own outline (a fan of 9 rays, drawn once, twisting with
//   time) and the base takes the fire's orange; the embers come only from the columns' bases;
// - low smoke: three sheets following the real ground (0.35, 1.3 and 2.8 blocks over it), thicker in the streets than on
//   the roofs, warming to orange near the columns at night;
// - the wind: heavy gusts with calm between (gustAt), ash tumbling, streaks of ash running low and high, burnt paper;
//   the horizon closes a little in each gust;
// - the screen (fx_jjgrade): desaturated grey with a purple vignette; at night only the edges and the fires' warmth
//   from below, never a dark veil over the screen;
// - now and then a far cursed-energy blast: a violet-white flash on the horizon, a column of light, the boom arriving
//   later (343 blocks a second) with a tremor;
// - sounds built from noise (tools/sounds/colonia_sounds.py): the wind through the ruins rising with the gusts, the roar
//   and crackle of the nearest column, far collapses.
public final class ColoniaAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WIND = 0.15;
    private static final int CELLS = 48;
    private static final int SIZE = 2;
    private static final double DOME_R = 108;
    private static final float[][] LAYERS = {{0.35f, 0.40f}, {1.3f, 0.28f}, {2.8f, 0.15f}};

    private static float strength, lastStrength;
    private static double gust, wx = 1, wz;
    private static float night;

    private static final class Col {
        double x, y, z, power, tone, ph, acc;
    }

    private static final class Puff {
        Col c;
        double x, y, z, vy, age, life, s0, grow, tone, spin, rot;
        double lx, ly, lz, dist;
        final double[] r = new double[9];
    }

    private static final class Ember {
        double x, y, z, vy, age, life, s, ph, hue, lx, ly, lz;
    }

    // ash and paper: tumbling flat pieces
    private static final class Flake {
        double x, y, z, s, ang, spin, ax, ay, az, ph, tone, lx, ly, lz, lang;
    }

    private static final class Streak {
        double x, y, z, h, s, ph, lx, ly, lz;
    }

    private static final List<Col> COLS = new ArrayList<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final List<Ember> EMBERS = new ArrayList<>();
    private static final List<Flake> ASH = new ArrayList<>();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final List<Flake> PAPER = new ArrayList<>();

    // the fire scan: one row of columns per tick over a square of 2*SCAN around the player
    private static final int SCAN = 72;
    private static int scanRow = -SCAN;
    private static int scanX, scanZ;
    private static final Map<Long, double[]> CELLS_FOUND = new HashMap<>();

    // the ground under the low smoke
    private static final float[][] GROUND = new float[CELLS + 1][CELLS + 1];
    private static final float[][] STREET = new float[CELLS + 1][CELLS + 1];
    private static int groundX = Integer.MIN_VALUE, groundZ, groundY;
    private static long groundAt;
    private static float cityY;

    // the barriers near the player, {cx, cz, cityY, half} each, from the server; the nearest is drawn, fixed in the world
    private static int[] domes = new int[0];
    private static double domeX, domeZ, domeY, domeR = DOME_R;
    private static boolean hasDome;
    private static float inside;

    // the far blast
    private static double flashT = -1, flashDist, flashX, flashZ, flashH, flashAmt;
    private static Vec3 flashDir = new Vec3(1, 0, 0);
    private static boolean boomed;
    private static double nextFlash = 18;
    private static float shake;
    private static double nextFall = 8;

    // the low smoke's pattern, worked out each tick (and blended between ticks), not each frame
    private static final float[][][] VEIL = new float[LAYERS.length][CELLS + 1][CELLS + 1];
    private static final float[][][] VEIL_LAST = new float[LAYERS.length][CELLS + 1][CELLS + 1];
    private static final float[][] WARM = new float[CELLS + 1][CELLS + 1];
    private static boolean veilFresh;

    private static double lookX, lookY = 0, lookZ = 1;
    private static int quads;
    private static final List<Puff> DRAW = new ArrayList<>();

    // -Dsouls.coloniaPerf=true: logs what the Colônia costs every 200 frames (for testing on the PC)
    private static final boolean PERF = Boolean.getBoolean("souls.coloniaPerf");
    private static long perfDome, perfBuild, perfDraw, perfQuads, perfTick;
    private static int perfFrames, perfTicks;

    private static WindLoop windLoop;
    private static FireLoop fireLoop;

    private ColoniaAirFx() {
    }

    // The Colônia's gusts: heavier and slower than the desert's, with calm between (a fire wind).
    private static double gustAt(double t) {
        return 0.2 + 0.8 * Math.pow(0.5 + 0.5 * Math.sin(t * 0.31 + Math.sin(t * 0.09) * 2.4), 4);
    }

    // An integer hash (the preview's sin hash cost ~5 ms a tick here: the low smoke asks for ~115 thousand of them).
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

    private static double rnd() {
        return RANDOM.nextDouble();
    }

    // The Mine's flat clouds don't belong under the barrier's smoky sky (at night the fog turned them a flat salmon).
    public static boolean hidesClouds() {
        return strength > 0.5f;
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    // ---------------------------------------------------------------- tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            domes = new int[0];
            reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        boolean inside = BiomeAura.in("colonia_do_jogo_do_abate", b -> b.is(ModWorldgen.COLONIA_DO_JOGO_DO_ABATE));
        strength = Mth.clamp(strength + (inside ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            reset();
            return;
        }
        long perfStart = PERF ? System.nanoTime() : 0;
        ClientLevel level = mc.level;
        Vec3 p = mc.player.position();
        double dt = 0.05, t = level.getGameTime() / 20.0;
        float daylight = level.getSkyDarken(1);
        night = Mth.clamp((0.8f - daylight) / 0.6f, 0, 1);
        gust = gustAt(t);
        double dir = WIND + Math.sin(t * 0.04) * 0.25;
        wx = Math.cos(dir);
        wz = Math.sin(dir);
        double g = gust;

        int bx = Mth.floor(p.x / SIZE) * SIZE - CELLS * SIZE / 2, bz = Mth.floor(p.z / SIZE) * SIZE - CELLS * SIZE / 2;
        if (bx != groundX || bz != groundZ || Math.abs(p.y - groundY) > 3 || level.getGameTime() - groundAt > 60) {
            measureGround(level, bx, bz, Mth.floor(p.y));
        }
        scanFires(level, p);
        pickDome(p);

        // the columns: clouds born at the rhythm of their strength and the gust (which feeds the fire)
        for (Col c : COLS) {
            c.acc += dt * (5 + 4 * g) * c.power;
            while (c.acc > 1) {
                c.acc -= 1;
                PUFFS.add(puff(c));
            }
            if (rnd() < dt * (20 + 30 * g) * c.power) {
                EMBERS.add(ember(c));
            }
        }
        for (Puff f : PUFFS) {
            f.lx = f.x;
            f.ly = f.y;
            f.lz = f.z;
            simPuff(f, dt, g);
        }
        PUFFS.removeIf(f -> f.age >= f.life);
        for (Ember e : EMBERS) {
            e.lx = e.x;
            e.ly = e.y;
            e.lz = e.z;
            e.age += dt;
            double lean = 1.5 + 4 * g;
            e.x += (wx * lean + Math.cos(e.age * 3 + e.ph) * 0.7) * dt;
            e.z += (wz * lean + Math.sin(e.age * 2.6 + e.ph) * 0.7) * dt;
            e.y += e.vy * dt;
            e.vy *= 1 - dt * 0.25;
        }
        EMBERS.removeIf(e -> e.age >= e.life);

        fill(level, p);
        for (Flake a : ASH) {
            a.lx = a.x;
            a.ly = a.y;
            a.lz = a.z;
            a.lang = a.ang;
            double slow = 0.6;
            a.ang += dt * a.spin * (0.5 + g);
            a.ph += dt * 1.3;
            double edge = Math.abs(Math.sin(a.ang));
            a.y -= dt * (0.25 + 0.2 * edge) * (1.2 - g);
            a.x += dt * (wx * (1.5 + 6 * g) * slow + Math.cos(a.ph) * 0.5);
            a.z += dt * (wz * (1.5 + 6 * g) * slow + Math.sin(a.ph * 0.8) * 0.5);
            if (Math.abs(a.x - p.x) > 36 || Math.abs(a.z - p.z) > 36 || a.y < top(level, a.x, a.z)) {
                spawnAsh(level, a, p, false);
            }
        }
        for (Streak r : STREAKS) {
            r.lx = r.x;
            r.ly = r.y;
            r.lz = r.z;
            r.ph += dt * 3;
            double sp = (5 + 11 * g) * r.s;
            r.x += (wx * sp - wz * Math.sin(r.ph) * 0.6) * dt;
            r.z += (wz * sp + wx * Math.sin(r.ph) * 0.6) * dt;
            double base = Math.max(cityY, top(level, r.x, r.z)) + r.h;
            r.y = base > r.y ? base : Math.max(base, r.y - dt * 2);
            double u = (r.x - p.x) * wx + (r.z - p.z) * wz, v = -(r.x - p.x) * wz + (r.z - p.z) * wx;
            if (u > 40 || Math.abs(v) > 40 || u < -44) {
                spawnStreak(level, r, p, false);
            }
        }
        for (Flake f : PAPER) {
            f.lx = f.x;
            f.ly = f.y;
            f.lz = f.z;
            f.lang = f.ang;
            f.ang += dt * f.spin * (0.4 + g);
            f.ph += dt;
            f.y += dt * (-0.2 + g * 0.9 * Math.sin(f.ph * 0.7));
            f.x += dt * (wx * (1 + 5 * g) + Math.cos(f.ph) * 0.8);
            f.z += dt * (wz * (1 + 5 * g) + Math.sin(f.ph * 0.8) * 0.8);
            if (Math.abs(f.x - p.x) > 30 || Math.abs(f.z - p.z) > 30 || f.y < top(level, f.x, f.z) + 0.05) {
                spawnPaper(level, f, p, false);
            }
        }

        // the far blast: on its own every 30-60 s
        nextFlash -= dt;
        if (nextFlash < 0 && strength > 0.9f) {
            triggerFlash(p, rnd() * Mth.TWO_PI);
        }
        if (flashT >= 0) {
            flashT += dt;
            double ft = flashT;
            flashAmt = ft < 0.12 ? ft / 0.12 : Math.exp(-(ft - 0.12) * 1.6) * (1 + 0.6 * Math.max(0, Math.sin(ft * 31)) * Math.exp(-ft * 3));
            if (!boomed && ft > flashDist / 343) {
                boomed = true;
                shake = 0.9f;
                float k = (float) Math.max(0.35, 1 - flashDist / 500);
                // from its side of the horizon, close enough to be heard
                level.playLocalSound(p.x + flashDir.x * 24, p.y + 6, p.z + flashDir.z * 24, ModSounds.COLONIA_ESTRONDO.get(), SoundSource.AMBIENT,
                        2.4f * k * strength, 0.9f + (float) rnd() * 0.15f, false);
            }
            if (ft > Math.max(6, flashDist / 343 + 3)) {
                flashT = -1;
                flashAmt = 0;
            }
        }
        shake = Math.max(0, shake - (float) dt * 1.2f);
        sounds(mc, level, p, g);
        tickVeils(t, g);
        if (PERF) {
            perfTick += System.nanoTime() - perfStart;
            perfTicks++;
        }
    }

    private static void reset() {
        strength = 0;
        COLS.clear();
        PUFFS.clear();
        EMBERS.clear();
        ASH.clear();
        STREAKS.clear();
        PAPER.clear();
        CELLS_FOUND.clear();
        scanRow = -SCAN;
        groundX = Integer.MIN_VALUE;
        veilFresh = true;
        hasDome = false;
        inside = 0;
        flashT = -1;
        flashAmt = 0;
        shake = 0;
        if (windLoop != null) {
            windLoop.end();
            windLoop = null;
        }
        if (fireLoop != null) {
            fireLoop.end();
            fireLoop = null;
        }
    }

    // Called with what the server sent (ClientHooks.coloniaDomes).
    public static void setDomes(int[] flat) {
        domes = flat == null ? new int[0] : flat;
    }

    // The nearest barrier stays where the server says it is; the violet of its sky comes in as the player walks under it.
    private static void pickDome(Vec3 p) {
        double best = Double.MAX_VALUE;
        hasDome = false;
        for (int i = 0; i + 3 < domes.length; i += 4) {
            double r = DOME_R * domes[i + 3] / 120.0, dx = p.x - (domes[i] + 0.5), dz = p.z - (domes[i + 1] + 0.5);
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < r * 1.6 + 64 && d < best) {
                best = d;
                hasDome = true;
                domeX = domes[i] + 0.5;
                domeZ = domes[i + 1] + 0.5;
                domeY = domes[i + 2] - 2;
                domeR = r;
            }
        }
        float target = hasDome ? (float) Mth.clamp((domeR - best) / 12 + 0.5, 0, 1) : 0;
        inside += Mth.clamp(target - inside, -0.03f, 0.03f);
    }

    private static void triggerFlash(Vec3 p, double ang) {
        flashDist = 160 + rnd() * 180;
        flashX = p.x + Math.cos(ang) * flashDist;
        flashZ = p.z + Math.sin(ang) * flashDist;
        flashDir = new Vec3(Math.cos(ang), 0.06, Math.sin(ang)).normalize();
        flashH = 40 + rnd() * 50;
        flashT = 0;
        boomed = false;
        nextFlash = 30 + rnd() * 30;
    }

    // The top of the ground at a column (the first air over the ground, as the heightmap gives it).
    private static float top(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    private static void measureGround(ClientLevel level, int bx, int bz, int py) {
        float[] all = new float[(CELLS + 1) * (CELLS + 1)];
        int n = 0;
        for (int i = 0; i <= CELLS; i++) {
            for (int j = 0; j <= CELLS; j++) {
                float t = top(level, bx + i * SIZE, bz + j * SIZE);
                GROUND[i][j] = Math.abs(t - py) > 24 ? Float.NaN : t;
                if (!Float.isNaN(GROUND[i][j])) {
                    all[n++] = t;
                }
            }
        }
        // the city's own level: the low tenth of the ground around (the streets), the roofs stand above it
        if (n > 0) {
            java.util.Arrays.sort(all, 0, n);
            cityY = all[n / 10];
        } else {
            cityY = py;
        }
        for (int i = 0; i <= CELLS; i++) {
            for (int j = 0; j <= CELLS; j++) {
                STREET[i][j] = !Float.isNaN(GROUND[i][j]) && GROUND[i][j] <= cityY + 2 ? 1 : 0.3f;
            }
        }
        groundX = bx;
        groundZ = bz;
        groundY = py;
        groundAt = level.getGameTime();
        veilFresh = true;
    }

    // The fires: an ember block with the fire burning on it at the top of a column. One row per tick; when the square
    // is done, the 14 strongest cells of 14 blocks (most fire, highest up) become the columns.
    private static void scanFires(ClientLevel level, Vec3 p) {
        if (scanRow == -SCAN) {
            scanX = Mth.floor(p.x);
            scanZ = Mth.floor(p.z);
            CELLS_FOUND.clear();
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = scanX + scanRow;
        for (int z = scanZ - SCAN; z <= scanZ + SCAN; z++) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (!level.getBlockState(pos.set(x, y, z)).is(JujutsuBlocks.EMBERS.get())) {
                continue;
            }
            long key = (long) Math.floorDiv(x, 14) << 32 | (Math.floorDiv(z, 14) & 0xffffffffL);
            double[] c = CELLS_FOUND.get(key);
            if (c == null) {
                CELLS_FOUND.put(key, new double[] {x + 0.5, y + 2, z + 0.5, 1});
            } else {
                c[3]++;
                if (y + 2 > c[1]) {
                    c[0] = x + 0.5;
                    c[1] = y + 2;
                    c[2] = z + 0.5;
                }
            }
        }
        if (++scanRow <= SCAN) {
            return;
        }
        scanRow = -SCAN;
        List<double[]> list = new ArrayList<>(CELLS_FOUND.values());
        list.sort((a, b) -> Double.compare(b[3] + (b[1] - cityY) * 0.4, a[3] + (a[1] - cityY) * 0.4));
        List<Col> next = new ArrayList<>();
        for (double[] c : list.subList(0, Math.min(14, list.size()))) {
            // a column that was already there keeps its strength and tone, so the smoke doesn't jump
            Col keep = null;
            for (Col o : COLS) {
                if (Math.abs(o.x - c[0]) < 7 && Math.abs(o.z - c[2]) < 7) {
                    keep = o;
                }
            }
            if (keep == null) {
                keep = new Col();
                keep.power = 0.6 + Math.min(1, c[3] / 6) * 0.6 + rnd() * 0.3;
                keep.tone = 0.75 + rnd() * 0.5;
                keep.ph = rnd() * 6.28;
                keep.x = c[0];
                keep.y = c[1];
                keep.z = c[2];
                // a new column starts already standing
                for (int k = 0; k < 70 * keep.power; k++) {
                    Puff f = puff(keep);
                    double tt = rnd() * f.life;
                    simPuff(f, tt, 0.5);
                    f.lx = f.x;
                    f.ly = f.y;
                    f.lz = f.z;
                    PUFFS.add(f);
                }
            }
            next.add(keep);
        }
        COLS.clear();
        COLS.addAll(next);
        PUFFS.removeIf(f -> !COLS.contains(f.c) && f.age > f.life * 0.5);
    }

    private static Puff puff(Col c) {
        Puff f = new Puff();
        for (int k = 0; k < 9; k++) {
            f.r[k] = 0.62 + rnd() * 0.5;
        }
        f.c = c;
        f.x = c.x + (rnd() - 0.5) * 1.2;
        f.y = c.y + 0.4;
        f.z = c.z + (rnd() - 0.5) * 1.2;
        f.vy = 2.6 + rnd() * 1.6;
        f.life = 9 + rnd() * 9;
        f.s0 = 1.2 + rnd() * 1.0;
        f.grow = 0.7 + rnd() * 0.5;
        f.tone = (0.13 + rnd() * 0.12) * c.tone;
        f.spin = (rnd() - 0.5) * 0.6;
        f.rot = rnd() * 6.28;
        f.lx = f.x;
        f.ly = f.y;
        f.lz = f.z;
        return f;
    }

    private static void simPuff(Puff f, double dt, double g) {
        f.age += dt;
        double lift = Math.min(1, (f.y - f.c.y) / 5);
        double sp = (1.6 + 4.2 * g) * lift;
        f.x += (wx * sp + Math.sin(f.age * 0.7 + f.rot) * 0.25) * dt;
        f.z += (wz * sp + Math.cos(f.age * 0.6 + f.rot) * 0.25) * dt;
        f.y += f.vy * dt * f.c.power;
        f.vy = Math.max(0.5, f.vy - dt * 0.12);
        f.rot += f.spin * dt;
    }

    private static Ember ember(Col c) {
        Ember e = new Ember();
        e.x = c.x + (rnd() - 0.5) * 2;
        e.y = c.y + rnd() * 1.5;
        e.z = c.z + (rnd() - 0.5) * 2;
        e.vy = 2.5 + rnd() * 4;
        e.life = 1.5 + rnd() * 3.5;
        e.s = 0.04 + rnd() * 0.07;
        e.ph = rnd() * 6.28;
        e.hue = rnd();
        e.lx = e.x;
        e.ly = e.y;
        e.lz = e.z;
        return e;
    }

    private static void fill(ClientLevel level, Vec3 p) {
        while (ASH.size() < 1800) {
            Flake a = new Flake();
            spawnAsh(level, a, p, true);
            ASH.add(a);
        }
        while (STREAKS.size() < 1000) {
            Streak r = new Streak();
            spawnStreak(level, r, p, true);
            STREAKS.add(r);
        }
        while (PAPER.size() < 70) {
            Flake f = new Flake();
            spawnPaper(level, f, p, true);
            PAPER.add(f);
        }
    }

    private static void axis(Flake a) {
        double ax = rnd() - 0.5, ay = rnd() - 0.5, az = rnd() - 0.5, l = Math.sqrt(ax * ax + ay * ay + az * az);
        if (l < 1e-6) {
            l = 1;
        }
        a.ax = ax / l;
        a.ay = ay / l;
        a.az = az / l;
    }

    private static void spawnAsh(ClientLevel level, Flake a, Vec3 p, boolean any) {
        a.x = p.x + (rnd() - 0.5) * 2 * 34;
        a.z = p.z + (rnd() - 0.5) * 2 * 34;
        a.y = any ? cityY + rnd() * 30 : p.y + 10 + rnd() * 18;
        a.s = 0.05 + rnd() * 0.08;
        a.ang = rnd() * 6.28;
        a.spin = (rnd() < 0.5 ? -1 : 1) * (1.5 + rnd() * 3.5);
        axis(a);
        a.ph = rnd() * 6.28;
        a.tone = rnd();
        a.lx = a.x;
        a.ly = a.y;
        a.lz = a.z;
        a.lang = a.ang;
    }

    private static void spawnStreak(ClientLevel level, Streak r, Vec3 p, boolean any) {
        double v = (rnd() - 0.5) * 2 * 40, u = any ? (rnd() - 0.5) * 2 * 40 : -40;
        r.x = p.x + u * wx - v * wz;
        r.z = p.z + u * wz + v * wx;
        // 70% low over the ground (0 to 2 blocks), the rest high, so the gust shows even in flight
        r.h = rnd() < 0.7 ? rnd() * 2 : 4 + rnd() * 26;
        r.y = Math.max(cityY, top(level, r.x, r.z)) + r.h;
        r.s = 0.7 + rnd() * 0.6;
        r.ph = rnd() * 6.28;
        r.lx = r.x;
        r.ly = r.y;
        r.lz = r.z;
    }

    private static void spawnPaper(ClientLevel level, Flake f, Vec3 p, boolean any) {
        f.x = p.x + (rnd() - 0.5) * 2 * 30;
        f.z = p.z + (rnd() - 0.5) * 2 * 30;
        f.y = cityY + 1 + (any ? rnd() * 14 : rnd() * 2);
        f.s = 0.14 + rnd() * 0.12;
        f.ang = rnd() * 6.28;
        f.spin = (rnd() < 0.5 ? -1 : 1) * (1 + rnd() * 3);
        axis(f);
        f.ph = rnd() * 6.28;
        f.tone = rnd();
        f.lx = f.x;
        f.ly = f.y;
        f.lz = f.z;
        f.lang = f.ang;
    }

    // ---------------------------------------------------------------- sounds

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 p, double g) {
        if (windLoop == null || !mc.getSoundManager().isActive(windLoop)) {
            windLoop = new WindLoop();
            mc.getSoundManager().play(windLoop);
        }
        Col near = null;
        double best = 45;
        for (Col c : COLS) {
            double d = Math.sqrt((c.x - p.x) * (c.x - p.x) + (c.y - p.y) * (c.y - p.y) + (c.z - p.z) * (c.z - p.z));
            if (d < best) {
                best = d;
                near = c;
            }
        }
        if (near != null && (fireLoop == null || !mc.getSoundManager().isActive(fireLoop))) {
            fireLoop = new FireLoop();
            fireLoop.moveTo(near);
            mc.getSoundManager().play(fireLoop);
        }
        if (fireLoop != null) {
            fireLoop.target = near;
        }
        // a far collapse now and then: a deep rumble and rubble falling
        nextFall -= 0.05;
        if (nextFall < 0) {
            double a = rnd() * Mth.TWO_PI, d = 40 + rnd() * 20;
            level.playLocalSound(p.x + Math.cos(a) * d, cityY + 4, p.z + Math.sin(a) * d, ModSounds.COLONIA_DESABAMENTO.get(), SoundSource.AMBIENT,
                    4.5f * strength, 0.8f + (float) rnd() * 0.3f, false);
            nextFall = 18 + rnd() * 22;
        }
    }

    // The wind through the ruins: everywhere (not placed), louder in a gust.
    private static final class WindLoop extends AbstractTickableSoundInstance {
        WindLoop() {
            super(ModSounds.COLONIA_VENTO.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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
            volume = (float) ((0.18 + 0.82 * gust) * 0.75 * strength);
            pitch = (float) (0.9 + 0.2 * gust);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    // The roar and crackle of the nearest burning column (heard to about 40 blocks).
    private static final class FireLoop extends AbstractTickableSoundInstance {
        Col target;

        FireLoop() {
            super(ModSounds.COLONIA_FOGO.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            attenuation = SoundInstance.Attenuation.LINEAR;
            volume = 0.01f;
        }

        void moveTo(Col c) {
            target = c;
            x = c.x;
            y = c.y;
            z = c.z;
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
            if (target != null) {
                x += (target.x - x) * 0.2;
                y += (target.y - y) * 0.2;
                z += (target.z - z) * 0.2;
            }
            volume = target == null ? Math.max(0, volume - 0.1f) : 2.6f * strength * (float) target.power;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    // ---------------------------------------------------------------- render

    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            shaded(event, 1, fade, partial);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        PoseStack.Pose last = event.getPoseStack().last();
        float light = Mth.lerp(night, 1f, 0.32f);
        Vector3f look = cam.getLookVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        long t0 = PERF ? System.nanoTime() : 0;
        // the barrier first: it is the farthest thing, and drawn after the smoke its wall would cover the columns
        // (nothing here writes depth, so only the order decides)
        shaded(event, 0, fade, partial);
        long t1 = PERF ? System.nanoTime() : 0;
        // one buffer drawn in the order written (no sorting of every quad each frame, which cost most of the frame):
        // the low smoke, then the clouds far to near (sorted here, per cloud), then the small pieces
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        quads = 0;
        renderVeils(out, last, camera, partial, fade);
        renderSmoke(out, last, camera, left, up, partial, fade);
        renderAsh(out, last, camera, partial, fade, light);
        renderStreaks(out, last, camera, partial, fade, light);
        renderPaper(out, last, camera, partial, fade, light);
        long t2 = PERF ? System.nanoTime() : 0;
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
        renderEmbers(glow, last, camera, left, up, partial, fade);
        renderFlashBeam(glow, last, camera, left, up, fade);
        buffers.endBatch(FxRenderTypes.GLOW);
        if (PERF) {
            long t3 = System.nanoTime();
            perfDome += t1 - t0;
            perfBuild += t2 - t1;
            perfDraw += t3 - t2;
            perfQuads += quads;
            if (++perfFrames >= 200) {
                com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.info(
                        "[colonia] per frame: dome {} ms, build {} ms, draw {} ms, {} quads; per tick {} ms; {} columns, {} clouds",
                        String.format("%.2f", perfDome / 1e6 / perfFrames), String.format("%.2f", perfBuild / 1e6 / perfFrames),
                        String.format("%.2f", perfDraw / 1e6 / perfFrames), perfQuads / perfFrames,
                        String.format("%.2f", perfTick / 1e6 / Math.max(1, perfTicks)), COLS.size(), PUFFS.size());
                perfDome = perfBuild = perfDraw = perfQuads = perfTick = 0;
                perfFrames = perfTicks = 0;
            }
        }
    }

    // A flat piece's two axes, turned to face the camera's POSITION (not its looking direction): turning the head doesn't
    // turn the smoke with it, only walking around it does, so it stays in the world instead of stuck on the screen
    // (Stark, 2026-11-14: "todos os efeitos parecem estar na minha tela").
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
        // right = forward x world up; straight above or below, any horizontal axis
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
        // up = right x forward
        BU[0] = -rz * fy;
        BU[1] = rz * fx - rx * fz;
        BU[2] = rx * fy;
    }

    // In front of the camera, give or take r (what is behind it isn't built at all).
    private static boolean seen(Vec3 cam, double x, double y, double z, double r) {
        return (x - cam.x) * lookX + (y - cam.y) * lookY + (z - cam.z) * lookZ > -r;
    }

    // The barrier (mode 0) and the sky behind everything (mode 1), through souls_colonia, relative to the camera.
    private static void shaded(RenderLevelStageEvent event, int mode, float fade, float partial) {
        ShaderInstance sh = FxShaders.colonia;
        // (with a shader pack on, the frame isn't ours to draw custom shaders into: the Colônia keeps its smoke and fog)
        if (sh == null || FxShaders.flat() || mode == 0 && !hasDome) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            sh.safeGetUniform("Mode").set((float) mode);
            sh.safeGetUniform("Time").set((float) ((mc.level.getGameTime() + partial) / 20.0));
            sh.safeGetUniform("Night").set(Mth.clamp(night - (float) flashAmt * 0.8f, -1f, 1f));
            sh.safeGetUniform("Fade").set(fade);
            sh.safeGetUniform("Cam").set((float) camera.x, (float) camera.y, (float) camera.z);
            sh.safeGetUniform("Center").set((float) domeX, (float) domeY, (float) domeZ);
            sh.safeGetUniform("Inside").set(inside);
            sh.safeGetUniform("Fire").set(Mth.lerp(night, 0.18f, 1f));
            sh.safeGetUniform("FDir").set((float) flashDir.x, (float) flashDir.y, (float) flashDir.z);
            sh.safeGetUniform("FAmt").set((float) flashAmt);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            if (mode == 1) {
                RenderSystem.disableDepthTest();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                // (only its direction counts in the shader: small, so even a short render distance doesn't clip it)
                sphere(b, 0, 0, 0, 48, -Math.PI / 2, 32, 64);
            } else {
                RenderSystem.enableDepthTest();
                RenderSystem.defaultBlendFunc();
                sphere(b, domeX - camera.x, domeY - camera.y, domeZ - camera.z, domeR, 0, 48, 96);
            }
            MeshData mesh = b.build();
            if (mesh != null) {
                RenderSystem.setShader(() -> sh);
                BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // A sphere (or its top half, from lat0 = 0) of quads around (cx, cy, cz).
    private static void sphere(BufferBuilder b, double cx, double cy, double cz, double r, double lat0, int rings, int segs) {
        for (int i = 0; i < rings; i++) {
            double t0 = lat0 + (Math.PI / 2 - lat0) * i / rings, t1 = lat0 + (Math.PI / 2 - lat0) * (i + 1) / rings;
            for (int j = 0; j < segs; j++) {
                double p0 = Math.PI * 2 * j / segs, p1 = Math.PI * 2 * (j + 1) / segs;
                sv(b, cx, cy, cz, r, t0, p0);
                sv(b, cx, cy, cz, r, t1, p0);
                sv(b, cx, cy, cz, r, t1, p1);
                sv(b, cx, cy, cz, r, t0, p1);
            }
        }
    }

    private static void sv(BufferBuilder b, double cx, double cy, double cz, double r, double lat, double lon) {
        double c = Math.cos(lat);
        b.addVertex((float) (cx + r * c * Math.cos(lon)), (float) (cy + r * Math.sin(lat)), (float) (cz + r * c * Math.sin(lon)));
    }

    private static void v(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        out.addVertex(last, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
    }

    // The veil: a pattern stretched along the wind, running with it, in slow waves; each sheet its own speed.
    private static double veil(double x, double z, int layer, double t, double g) {
        double u = x * wx + z * wz, v = -x * wz + z * wx;
        double run = t * (3.0 + 1.6 * layer);
        double qx = (u - run) * 0.045, qz = v * 0.09 + layer * 7.1;
        double m = vnoise(qx, qz) * 0.55 + vnoise(qx * 2.3 + 3.1, qz * 2.1 + 1.3) * 0.3 + vnoise(qx * 5.1 - 1.7, qz * 4.7 - 1.7) * 0.15;
        double wave = vnoise((u - t * 5.0) * 0.015, v * 0.012);
        double k = Mth.clamp((m - 0.36) / 0.42, 0, 1);
        k = k * k * (3 - 2 * k);
        return k * (0.35 + 0.65 * g) * (0.45 + wave);
    }

    // The sheets' strength at each corner of the grid: the moving pattern, the edge of the square, the street, and the
    // fires' warmth at night. Each tick; the frame blends the last two.
    private static void tickVeils(double t, double g) {
        if (groundX == Integer.MIN_VALUE) {
            return;
        }
        double half = CELLS * SIZE / 2.0;
        for (int i = 0; i <= CELLS; i++) {
            for (int j = 0; j <= CELLS; j++) {
                double x = groundX + i * SIZE, z = groundZ + j * SIZE;
                float w = 0;
                if (night > 0.01f) {
                    for (Col c : COLS) {
                        double dd = Math.sqrt((x - c.x) * (x - c.x) + (z - c.z) * (z - c.z));
                        if (dd < 26) {
                            w = Math.max(w, (float) (1 - dd / 26));
                        }
                    }
                }
                WARM[i][j] = w * night;
                float far = (float) Mth.clamp((half - Math.sqrt((x - (groundX + half)) * (x - (groundX + half)) + (z - (groundZ + half)) * (z - (groundZ + half))))
                        / (half * 0.35), 0, 1);
                for (int layer = 0; layer < LAYERS.length; layer++) {
                    VEIL_LAST[layer][i][j] = VEIL[layer][i][j];
                    VEIL[layer][i][j] = Float.isNaN(GROUND[i][j]) ? 0
                            : (float) (LAYERS[layer][1] * veil(x, z, layer, t, g)) * far * (0.35f + 0.65f * STREET[i][j]);
                    if (veilFresh) {
                        VEIL_LAST[layer][i][j] = VEIL[layer][i][j];
                    }
                }
            }
        }
        veilFresh = false;
    }

    private static void renderVeils(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float partial, float fade) {
        if (groundX == Integer.MIN_VALUE) {
            return;
        }
        // low smoke: lead grey by day, almost black at night, warming to orange near the columns
        float[] base = {Mth.lerp(night, 0.42f, 0.13f), Mth.lerp(night, 0.4f, 0.09f), Mth.lerp(night, 0.42f, 0.1f)};
        float[][] al = new float[CELLS + 1][CELLS + 1];
        for (int layer = 0; layer < LAYERS.length; layer++) {
            float h = LAYERS[layer][0];
            for (int i = 0; i <= CELLS; i++) {
                for (int j = 0; j <= CELLS; j++) {
                    float k = Mth.lerp(partial, VEIL_LAST[layer][i][j], VEIL[layer][i][j]);
                    if (k <= 0 || Float.isNaN(GROUND[i][j])) {
                        al[i][j] = 0;
                        continue;
                    }
                    double dx = groundX + i * SIZE - cam.x, dy = GROUND[i][j] + h - cam.y, dz = groundZ + j * SIZE - cam.z;
                    float near = (float) Mth.clamp((Math.sqrt(dx * dx + dy * dy + dz * dz) - 1) / 6, 0, 1);
                    al[i][j] = k * near * near * (3 - 2 * near) * fade;
                }
            }
            for (int i = 0; i < CELLS; i++) {
                for (int j = 0; j < CELLS; j++) {
                    if (al[i][j] + al[i + 1][j] + al[i + 1][j + 1] + al[i][j + 1] < 0.004f) {
                        continue;
                    }
                    if (Float.isNaN(GROUND[i][j]) || Float.isNaN(GROUND[i + 1][j]) || Float.isNaN(GROUND[i][j + 1]) || Float.isNaN(GROUND[i + 1][j + 1])) {
                        continue;
                    }
                    double x0 = groundX + i * SIZE, x1 = x0 + SIZE, z0 = groundZ + j * SIZE, z1 = z0 + SIZE;
                    if (!seen(cam, x0 + SIZE * 0.5, GROUND[i][j] + h, z0 + SIZE * 0.5, SIZE * 2)) {
                        continue;
                    }
                    veilV(out, last, cam, x0, GROUND[i][j] + h, z0, base, WARM[i][j], al[i][j]);
                    veilV(out, last, cam, x1, GROUND[i + 1][j] + h, z0, base, WARM[i + 1][j], al[i + 1][j]);
                    veilV(out, last, cam, x1, GROUND[i + 1][j + 1] + h, z1, base, WARM[i + 1][j + 1], al[i + 1][j + 1]);
                    veilV(out, last, cam, x0, GROUND[i][j + 1] + h, z1, base, WARM[i][j + 1], al[i][j + 1]);
                    quads++;
                }
            }
        }
    }

    private static void veilV(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float[] base, float w, float a) {
        v(out, last, cam, x, y, z, base[0] + w * 0.45f, base[1] + w * 0.16f, base[2] + w * 0.02f, a);
    }

    // A cloud: a fan of 9 rays with its own outline, solid in the middle and gone at the edge; it grows and lightens as it
    // rises. Each wedge is a quad with its last corner doubled.
    private static void renderSmoke(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Vector3f left, Vector3f up, float partial, float fade) {
        DRAW.clear();
        for (Puff f : PUFFS) {
            double s = (f.s0 + f.age * f.grow * 1.5) * f.c.power;
            if (seen(cam, f.x, f.y, f.z, s * 1.6 + 2)) {
                f.dist = (f.x - cam.x) * (f.x - cam.x) + (f.y - cam.y) * (f.y - cam.y) + (f.z - cam.z) * (f.z - cam.z);
                DRAW.add(f);
            }
        }
        DRAW.sort((a, b) -> Double.compare(b.dist, a.dist));
        for (Puff f : DRAW) {
            double age = f.age + 0.05 * partial;
            double k = age / f.life;
            if (k >= 1) {
                continue;
            }
            double s = (f.s0 + age * f.grow * (1 + gust * 0.5)) * f.c.power;
            double x = Mth.lerp(partial, f.lx, f.x), y = Mth.lerp(partial, f.ly, f.y), z = Mth.lerp(partial, f.lz, f.z);
            double a = Math.sin(Math.min(1, k * 4) * Math.PI / 2) * (1 - k) * (1 - k) * 0.78;
            double d = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
            a *= Mth.clamp((d - 1.5) / 4, 0, 1) * fade;
            if (a < 0.004) {
                continue;
            }
            double hgt = y - f.c.y;
            float n = night;
            // the base takes the fire (orange, more at night), the top lightens to the sky's grey
            // (at night the fire lights the column higher up, so from the street it still stands out against the dark
            // violet sky: in the first test on the PC it was only a light haze there)
            float fireLit = (float) Math.max(0, 1 - hgt / Mth.lerp(n, 6, 22));
            float tn = (float) (f.tone + Math.min(0.18, hgt * 0.004));
            float r = Mth.lerp(n, tn, tn * 0.75f) + fireLit * Mth.lerp(n, 0.2f, 0.62f);
            float g = Mth.lerp(n, tn * 0.97f, tn * 0.6f) + fireLit * Mth.lerp(n, 0.07f, 0.24f);
            float b = Mth.lerp(n, tn * 1.02f, tn * 0.7f) + fireLit * 0.03f;
            face(cam, x, y, z);
            double px = 0, py = 0, pz = 0;
            for (int q = 0; q <= 9; q++) {
                double ang = f.rot + (q % 9) / 9.0 * Math.PI * 2;
                double rr = f.r[q % 9] * (1 + 0.12 * Math.sin(age * 0.9 + q * 1.7)) * s;
                double ox = Math.cos(ang) * rr, oy = Math.sin(ang) * rr;
                double tx = x + BR[0] * ox + BU[0] * oy, ty = y + BR[1] * ox + BU[1] * oy, tz = z + BR[2] * ox + BU[2] * oy;
                if (q > 0) {
                    quads++;
                    v(out, last, cam, x, y, z, r, g, b, (float) a);
                    v(out, last, cam, px, py, pz, r, g, b, 0);
                    v(out, last, cam, tx, ty, tz, r, g, b, 0);
                    v(out, last, cam, tx, ty, tz, r, g, b, 0);
                }
                px = tx;
                py = ty;
                pz = tz;
            }
        }
    }

    private static void renderEmbers(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Vector3f left, Vector3f up, float partial, float fade) {
        for (Ember e : EMBERS) {
            double age = e.age + 0.05 * partial;
            double k = Math.min(1, age / e.life);
            double x = Mth.lerp(partial, e.lx, e.x), y = Mth.lerp(partial, e.ly, e.y), z = Mth.lerp(partial, e.lz, e.z);
            double a = (1 - k) * (0.6 + 0.4 * Math.sin(age * 14 + e.ph));
            double d = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
            a *= Mth.clamp((d - 0.5) / 2, 0, 1) * Mth.clamp((120 - d) / 30, 0, 1) * fade;
            if (a < 0.01) {
                continue;
            }
            if (!seen(cam, x, y, z, 1)) {
                continue;
            }
            double s = e.s * (1 + d * 0.02);
            float r = 1, g = (float) (0.45 + e.hue * 0.35 * (1 - k)), b = (float) (0.12 * (1 - k));
            face(cam, x, y, z);
            double lx = BR[0] * s, ly = BR[1] * s, lz = BR[2] * s, ux = BU[0] * s, uy = BU[1] * s, uz = BU[2] * s;
            v(out, last, cam, x + lx, y + ly, z + lz, r, g, b, (float) a * 0.4f);
            v(out, last, cam, x + ux, y + uy, z + uz, r, g, b, (float) a);
            v(out, last, cam, x - lx, y - ly, z - lz, r, g, b, (float) a * 0.4f);
            v(out, last, cam, x - ux, y - uy, z - uz, r, g, b, (float) a);
        }
    }

    // The tumbling piece's two half-axes (the preview's tumble): u turned about the piece's own axis by its spin.
    private static double[] tumble(Flake o, double ang, double s, double lng, double wide) {
        double ax = o.ax, ay = o.ay, az = o.az;
        double px = Math.abs(ay) < 0.9 ? 0 : 1, py = Math.abs(ay) < 0.9 ? 1 : 0;
        double e1x = -az * py, e1y = az * px, e1z = ax * py - ay * px;
        double l1 = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= l1;
        e1y /= l1;
        e1z /= l1;
        double e2x = ay * e1z - az * e1y, e2y = az * e1x - ax * e1z, e2z = ax * e1y - ay * e1x;
        double c = Math.cos(ang), sn = Math.sin(ang);
        double facing = Math.abs((e2x * c - e1x * sn) * 0.3 + (e2y * c - e1y * sn) * 0.9 + (e2z * c - e1z * sn) * 0.3);
        return new double[] {(e1x * c + e2x * sn) * s * lng, (e1y * c + e2y * sn) * s * lng, (e1z * c + e2z * sn) * s * lng, ax * s * wide, ay * s * wide,
                az * s * wide, facing};
    }

    private static void renderAsh(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float partial, float fade, float light) {
        for (Flake a : ASH) {
            double x = Mth.lerp(partial, a.lx, a.x), y = Mth.lerp(partial, a.ly, a.y), z = Mth.lerp(partial, a.lz, a.z);
            double d = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
            float al = (float) (Mth.clamp((36 - d) / 10, 0, 1) * Mth.clamp((d - 1.5) / 2.5, 0, 1) * 0.9) * fade;
            if (al < 0.01f || !seen(cam, x, y, z, 1)) {
                continue;
            }
            quads++;
            double[] T = tumble(a, Mth.lerp(partial, a.lang, a.ang), a.s, 1.1, 0.9);
            float tone = (float) (0.42 + a.tone * 0.3), kk = (float) (0.7 + 0.3 * T[6]) * light;
            float r = tone * kk, g = tone * kk * 0.98f, b = tone * kk * 1.04f;
            v(out, last, cam, x + T[0], y + T[1], z + T[2], r, g, b, al * 0.6f);
            v(out, last, cam, x + T[3], y + T[4], z + T[5], r, g, b, al);
            v(out, last, cam, x - T[0], y - T[1], z - T[2], r, g, b, al * 0.6f);
            v(out, last, cam, x - T[3], y - T[4], z - T[5], r, g, b, al);
        }
    }

    // A streak of ash running with the wind: a thin ribbon turned to the camera, the head stronger, the tail fading.
    private static void renderStreaks(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float partial, float fade, float light) {
        double g = gust;
        // light ash (shows on the asphalt and the dark fronts); at night it takes a little of the fires' orange
        float cr = Mth.lerp(night, 0.86f, 0.5f), cg = Mth.lerp(night, 0.84f, 0.36f), cb = Mth.lerp(night, 0.86f, 0.3f);
        for (Streak r : STREAKS) {
            double x = Mth.lerp(partial, r.lx, r.x), y = Mth.lerp(partial, r.ly, r.y), z = Mth.lerp(partial, r.lz, r.z);
            double cx = cam.x - x, cy = cam.y - y, cz = cam.z - z, dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
            float a = (float) (0.42 * Mth.clamp((60 - dist) / 40, 0, 1) * Mth.clamp((dist - 2.5) / 4, 0, 1) * (0.25 + 0.75 * g)) * fade;
            if (a < 0.004f || !seen(cam, x, y, z, 4)) {
                continue;
            }
            quads++;
            double len = (5 + 11 * g) * r.s * 0.22;
            double sx = wz * cy, sy = wx * cz - wz * cx, sz = -wx * cy;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6) {
                continue;
            }
            double wd = 0.03 * (1 + dist * 0.02) / sl;
            sx *= wd;
            sy *= wd;
            sz *= wd;
            double tx = x - wx * len, tz = z - wz * len;
            v(out, last, cam, tx - sx, y - sy, tz - sz, cr, cg, cb, 0);
            v(out, last, cam, tx + sx, y + sy, tz + sz, cr, cg, cb, 0);
            v(out, last, cam, x + sx, y + sy, z + sz, cr, cg, cb, a);
            v(out, last, cam, x - sx, y - sy, z - sz, cr, cg, cb, a);
        }
    }

    // Burnt paper: a real rectangle (corners u±v), the middle still light, the edge burnt (each sheet as burnt as it is).
    private static void renderPaper(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float partial, float fade, float light) {
        for (Flake f : PAPER) {
            double x = Mth.lerp(partial, f.lx, f.x), y = Mth.lerp(partial, f.ly, f.y), z = Mth.lerp(partial, f.lz, f.z);
            double d = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
            float al = (float) (Mth.clamp((30 - d) / 8, 0, 1) * Mth.clamp((d - 3) / 3, 0, 1)) * fade;
            if (al < 0.01f || !seen(cam, x, y, z, 1)) {
                continue;
            }
            quads++;
            double[] T = tumble(f, Mth.lerp(partial, f.lang, f.ang), f.s * 0.45, 1.0, 0.72);
            float kk = (float) (0.75 + 0.25 * T[6]) * light;
            float mr = 0.78f * kk, mg = 0.72f * kk, mb = 0.6f * kk;
            float er = (float) (0.2 * kk * (1 - f.tone) + 0.12 * kk), eg = 0.15f * kk, eb = 0.12f * kk;
            v(out, last, cam, x + T[0] + T[3], y + T[1] + T[4], z + T[2] + T[5], er, eg, eb, al);
            v(out, last, cam, x + T[0] - T[3], y + T[1] - T[4], z + T[2] - T[5], mr, mg, mb, al);
            v(out, last, cam, x - T[0] - T[3], y - T[1] - T[4], z - T[2] - T[5], er, eg, eb, al);
            v(out, last, cam, x - T[0] + T[3], y - T[1] + T[4], z - T[2] + T[5], mr, mg, mb, al);
        }
    }

    // In the blast a column of violet light rises far away and fades, with a soft burst at its foot.
    private static void renderFlashBeam(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Vector3f left, Vector3f up, float fade) {
        if (flashT < 0) {
            return;
        }
        float a = (float) (flashAmt * 0.8) * fade;
        if (a < 0.01f) {
            return;
        }
        double G = cityY, w = 3 + flashT * 6;
        double dx = flashX - cam.x, dz = flashZ - cam.z, l = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
        double sx = -dz / l * w, sz = dx / l * w;
        v(out, last, cam, flashX - sx, G, flashZ - sz, 0.8f, 0.55f, 1, a);
        v(out, last, cam, flashX + sx, G, flashZ + sz, 0.8f, 0.55f, 1, a);
        v(out, last, cam, flashX + sx * 0.3, G + flashH, flashZ + sz * 0.3, 0.6f, 0.3f, 1, 0);
        v(out, last, cam, flashX - sx * 0.3, G + flashH, flashZ - sz * 0.3, 0.6f, 0.3f, 1, 0);
        double s = 18 + flashT * 30, cy = G + 4;
        face(cam, flashX, cy, flashZ);
        double lx = BR[0] * s, ly = BR[1] * s, lz = BR[2] * s, ux = BU[0] * s, uy = BU[1] * s, uz = BU[2] * s;
        for (int q = 0; q < 4; q++) {
            double ax = q < 2 ? lx : -lx, ay = q < 2 ? ly : -ly, az = q < 2 ? lz : -lz;
            double bx = q % 2 == 0 ? ux : -ux, by = q % 2 == 0 ? uy : -uy, bz = q % 2 == 0 ? uz : -uz;
            v(out, last, cam, flashX, cy, flashZ, 0.9f, 0.7f, 1, a * 0.7f);
            v(out, last, cam, flashX + ax, cy + ay, flashZ + az, 0.9f, 0.7f, 1, 0);
            v(out, last, cam, flashX + ax + bx, cy + ay + by, flashZ + az + bz, 0.9f, 0.7f, 1, 0);
            v(out, last, cam, flashX + bx, cy + by, flashZ + bz, 0.9f, 0.7f, 1, 0);
        }
    }

    // ---------------------------------------------------------------- screen, fog, camera

    public static boolean gradeOn() {
        return active() && FxShaders.jjGrade != null;
    }

    // The screen grade (between FxPost.beginPasses and endPasses): the preview's CSS filter and overlay layers.
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.jjGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        float n = night;
        // at night the grade NEVER darkens the screen (Stark: the veil that covered the night): only the edges and the
        // fires' warmth from below
        float dark = Mth.lerp(n, 0.05f + 0.03f * (float) gust, 0);
        sh.safeGetUniform("Sat").set(Mth.lerp(fade, 1f, Mth.lerp(n, 0.72f, 0.85f)));
        sh.safeGetUniform("Contrast").set(Mth.lerp(fade, 1f, 1.04f));
        sh.safeGetUniform("Wash").set(Mth.lerp(n, 120, 70) / 255f, Mth.lerp(n, 104, 40) / 255f, Mth.lerp(n, 140, 80) / 255f, dark * fade);
        sh.safeGetUniform("Warm").set(0.10f * n * fade);
        sh.safeGetUniform("Vig").set(Mth.lerp(n, 52, 60) / 255f, Mth.lerp(n, 20, 20) / 255f, Mth.lerp(n, 70, 80) / 255f, Mth.lerp(n, 0.36f, 0.28f) * fade);
        sh.safeGetUniform("Flash").set((float) flashAmt * fade);
        FxPost.pass(sh);
    }

    // The fog: grey-violet and desaturated; at night a dark violet, never black. Closes a little in each gust.
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float r = Mth.lerp(night, 0x8a / 255f, 0x3a / 255f), g = Mth.lerp(night, 0x82 / 255f, 0x28 / 255f), b = Mth.lerp(night, 0x90 / 255f, 0x34 / 255f);
        event.setRed(Mth.lerp(fade, event.getRed(), r));
        event.setGreen(Mth.lerp(fade, event.getGreen(), g));
        event.setBlue(Mth.lerp(fade, event.getBlue(), b));
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        double g = gust;
        float near = (float) (Mth.lerp(night, 18f, 24f) * (1 - 0.45 * g)), far = (float) (Mth.lerp(night, 160f, 140f) * (1 - 0.3 * g));
        event.setNearPlaneDistance(Mth.lerp(fade, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), near)));
        event.setFarPlaneDistance(Mth.lerp(fade, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), far)));
        event.setCanceled(true);
    }

    // The blast's tremor (the preview shakes the camera the same way, a little, fading fast).
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.001f) {
            return;
        }
        float s = shake * shake * 0.9f;
        event.setPitch(event.getPitch() + (float) (rnd() - 0.5) * s);
        event.setYaw(event.getYaw() + (float) (rnd() - 0.5) * s);
    }
}
