package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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

// The air of the Floresta de Hanami (Stark 2026-11-19 on the preview forja/jujutsu-hanami-ambiente). amb.js there is the
// source of truth and this is its port, the same names where it helps. Fades in when the player walks into the biome and
// out when the player leaves (BiomeAura); the golden pollen floating all over the biome and the fireflies stay
// JujutsuAirFx's ("eram bem bonitas"). Everything makes physical sense:
// - komorebi: the sun through a small hole in the canopy makes an image of the sun on the ground (a pinhole camera):
//   a round spot, stretched away from the sun when it is low, bigger the higher the leaves. The leaves sway, so the
//   spots tremble and blink. Only where the sun's ray really reaches the ground (traced block by block up the canopy);
// - hanafubuki: a petal is a light plate: falls slowly, turning (shows its lit and its dark side), and goes with the
//   wind. Now and then a whirlwind (a Rankine vortex: spins like a solid body in its core, falls off as 1/r outside,
//   rising in the middle) lifts the carpet of petals off the ground and swirls it;
// - pollen: the giant flower lets out a cloud of pollen in a strong gust or when someone walks by; the grain shoots out,
//   stops on the drag and floats, the cloud opens (diffusion) and settles slowly. In the sun the grain sparkles;
// - the Heart beats: the Hanami's Heart pulses (tum-tum) and the pulse runs through the roots at 10 blocks/s. Each
//   cursed bud lights when the pulse passes it, and the tree's eye holes light with it;
// - the screen (fx_jjgrade): a forest too alive, green and gold by day; teal at night with the buds' pink left (never
//   darkening);
// - sounds built from noise and tones (tools/sounds/hanami_sounds.py): the canopy in the wind, the birds by day, the
//   crickets at night, the Heart's beat (when the pulse reaches the player).
public final class HanamiAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WIND = 2.2;
    private static final int SCAN = 64;
    private static final int FLECK_R = 30;
    private static final int PETALS = 1600;
    private static final int MAX_MOTES = 4000;

    private static float strength, lastStrength;
    private static float night;
    private static final double[] SUN = {0, 1, 0};
    private static long lastNanos;
    private static ClientLevel lastLevel;
    private static double lookX, lookY, lookZ = 1;

    // ---------------------------------------------------------------- what the world has (a row of columns a tick)

    private static final class Scan {
        final List<double[]> blossoms = new ArrayList<>();
        final List<double[]> leaves = new ArrayList<>();
        final List<double[]> flowers = new ArrayList<>();
        final List<double[]> buds = new ArrayList<>();
        final List<double[]> hollows = new ArrayList<>();
        final Long2ObjectOpenHashMap<Pollen> pollen = new Long2ObjectOpenHashMap<>();

        void clear() {
            blossoms.clear();
            leaves.clear();
            flowers.clear();
            buds.clear();
            hollows.clear();
            pollen.clear();
        }
    }

    private static final class Pollen {
        double x, y, z, next;
    }

    private static Scan cur = new Scan(), next = new Scan();
    private static int scanRow = -SCAN - 1, scanCx, scanCz;
    private static boolean firstPass = true;
    // the Heart (its eye holes' middle): where the beat starts; until it is seen, the buds' middle
    private static double beatX, beatZ;
    private static boolean heartKnown, beatSet;

    // ---------------------------------------------------------------- the sun flecks (measured a few rows a tick)

    private static List<double[]> flecks = new ArrayList<>(), flecksNext = new ArrayList<>();
    private static int fleckRow = -1, fleckX0, fleckZ0;
    private static double fleckAt = -100, fleckFx = 1e9, fleckFz = 1e9;
    private static final double[] FS = {0, 1, 0};

    // ---------------------------------------------------------------- moving things

    private static final class Petal {
        double x = 1e9, y, z, vx, vy, vz, a, w, tilt, rest, hold, sz;
        int kind;
        float r, g, b;
    }

    private static final Petal[] PETAL = new Petal[PETALS];

    static {
        for (int i = 0; i < PETALS; i++) {
            PETAL[i] = new Petal();
        }
    }

    private static final class Mote {
        double x, y, z, vx, vy, vz, age, life, ph;
    }

    private static final class Puff {
        double x, y, z, age, k;
    }

    private static final List<Mote> MOTES = new ArrayList<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final Long2DoubleOpenHashMap COOL = new Long2DoubleOpenHashMap();

    private static final class Vortex {
        double x, z, age, life, g, rc;
    }

    private static Vortex vortex;
    private static double nextVortex = 6;

    // sounds
    private static LeafLoop leafLoop;
    private static CricketLoop cricketLoop;
    private static float windGust;
    private static double nextBird = 1;
    private static long lastBeat = Long.MIN_VALUE;

    private HanamiAirFx() {
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

    private static long key3(int x, int y, int z) {
        return ((long) x & 0x3ffffff) << 38 | ((long) z & 0x3ffffff) << 12 | (y & 0xfff);
    }

    // the ground under the canopy (leaves left out): the top block of the column
    private static int top(ClientLevel level, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!level.hasChunk(ix >> 4, iz >> 4)) {
            return -999;
        }
        int h = ClientHeights.noLeaves(level, ix, iz) - 1;
        return h <= level.getMinBuildHeight() ? -999 : h;
    }

    // The wind: a steady breeze with gusts running at 4 blocks/s. Gives vx, vz and strength 0..1 in W.
    private static final double[] W = new double[3];

    private static double[] wind(double x, double z, double t) {
        double c = Math.cos(WIND), s = Math.sin(WIND), along = x * c + z * s, across = -x * s + z * c;
        double g = clamp01(0.25 + 0.75 * vnoise(along / 16 - t * 0.25, across / 20) * (0.5 + 0.5 * Math.sin((along - t * 4) / 9)));
        double v = 0.4 + 2.2 * g;
        W[0] = c * v;
        W[1] = s * v;
        W[2] = g;
        return W;
    }

    private static boolean isLeaf(BlockState s) {
        return s.is(JujutsuBlocks.HANAMI_LEAVES.get()) || s.is(JujutsuBlocks.HANAMI_LEAVES_LUSH.get()) || s.is(JujutsuBlocks.HANAMI_LEAVES_LIGHT.get())
                || s.is(JujutsuBlocks.BLOOMING_CANOPY.get());
    }

    // the ground of the woods: where a column's reading stops
    private static boolean terrain(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.STONE) || s.is(Blocks.ANDESITE)
                || s.is(Blocks.WATER) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.SAND)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.BEDROCK);
    }

    // how many of the 8 blocks around (x, y, z) on its layer are leaves: 5 or more = the ray goes through a hole
    private static int leavesAround(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockState s = level.getBlockState(pos.set(x + dx, y, z + dz));
                if (isLeaf(s) || s.is(BlockTags.LEAVES)) {
                    n++;
                }
            }
        }
        return n;
    }

    // leaves, the canopy's flowers and anything solid hide the sun; thin plants do not
    private static boolean opaque(BlockState s) {
        return !s.isAir() && (s.is(BlockTags.LEAVES) || isLeaf(s) || s.canOcclude());
    }

    // ---------------------------------------------------------------- colors

    // a flying thing's colors: cherry blossom (pink or white), leaf (three greens), flower petal (the flower's color)
    private static final float[][] LEAFC = {{0.5f, 0.8f, 0.3f}, {0.62f, 0.86f, 0.33f}, {0.78f, 0.84f, 0.36f}};
    // white, yellow, pink, red, blue (the ground flowers); white, pink, yellow, red (the giant flowers' petals)
    private static final float[][] FCOL = {{1f, 0.98f, 0.94f}, {1f, 0.86f, 0.25f}, {1f, 0.6f, 0.82f}, {0.95f, 0.27f, 0.25f}, {0.45f, 0.62f, 1f}};

    private static int flowerColor(BlockState s) {
        if (s.is(Blocks.OXEYE_DAISY) || s.is(JujutsuBlocks.PETAL_WHITE.get())) {
            return 0;
        }
        if (s.is(Blocks.DANDELION) || s.is(JujutsuBlocks.PETAL_YELLOW.get())) {
            return 1;
        }
        if (s.is(Blocks.PINK_TULIP) || s.is(JujutsuBlocks.PETAL_PINK.get())) {
            return 2;
        }
        if (s.is(Blocks.POPPY) || s.is(JujutsuBlocks.PETAL_RED.get())) {
            return 3;
        }
        if (s.is(Blocks.CORNFLOWER)) {
            return 4;
        }
        return -1;
    }

    private static boolean groundFlower(BlockState s) {
        return s.is(Blocks.OXEYE_DAISY) || s.is(Blocks.DANDELION) || s.is(Blocks.PINK_TULIP) || s.is(Blocks.POPPY) || s.is(Blocks.CORNFLOWER);
    }

    private static void blossomColor(Petal p) {
        if (rnd() < 0.7) {
            p.r = 1f;
            p.g = 0.72f;
            p.b = 0.83f;
        } else {
            p.r = 1f;
            p.g = 0.97f;
            p.b = 0.95f;
        }
    }

    // lighter than the canopy (a loose leaf catches light on both sides), and one in seven already yellowing
    private static void leafColor(Petal p, int t, double j) {
        float[] c = rnd() < 0.14 ? new float[] {0.95f, 0.8f, 0.32f} : LEAFC[t];
        p.r = (float) (c[0] * j);
        p.g = (float) (c[1] * j);
        p.b = (float) (c[2] * j);
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    // ---------------------------------------------------------------- tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            if (lastStrength > 0 || heartKnown || !cur.buds.isEmpty()) {
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
        boolean in = BiomeAura.in("floresta_de_hanami_ar", b -> b.is(ModWorldgen.FLORESTA_DE_HANAMI));
        strength = Mth.clamp(strength + (in ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            if (lastStrength > 0) {
                reset(false);
            }
            return;
        }
        ClientLevel level = mc.level;
        Vec3 p = mc.player.position();
        int rows = firstPass ? 8 : 1;
        for (int i = 0; i < rows; i++) {
            scanStep(level, p);
        }
        fleckStep(level, mc.gameRenderer.getMainCamera().getPosition());
        sounds(mc, level, p);
    }

    private static void scanStep(ClientLevel level, Vec3 p) {
        if (scanRow > SCAN) {
            // the pollen flowers keep their timers from pass to pass
            for (Long2ObjectMap.Entry<Pollen> e : next.pollen.long2ObjectEntrySet()) {
                Pollen old = cur.pollen.get(e.getLongKey());
                if (old != null) {
                    e.getValue().next = old.next;
                }
            }
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
        Block bloom = JujutsuBlocks.BLOOMING_CANOPY.get(), leaf1 = JujutsuBlocks.HANAMI_LEAVES.get(), leaf2 = JujutsuBlocks.HANAMI_LEAVES_LUSH.get(),
                leaf3 = JujutsuBlocks.HANAMI_LEAVES_LIGHT.get(), pollen = JujutsuBlocks.GOLDEN_POLLEN.get(), tip = JujutsuBlocks.BUD_TIP.get(),
                hollow = JujutsuBlocks.HANAMI_HOLLOW.get();
        for (int z = scanCz - SCAN; z <= scanCz + SCAN; z++) {
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int hi = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int lo = Math.max(level.getMinBuildHeight() + 1, hi - 120);
            BlockState above = Blocks.AIR.defaultBlockState();
            // down the column from its top to the ground: what hangs, what blooms, what glows
            for (int y = hi; y >= lo; y--) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                boolean air = s.isAir();
                if (air) {
                    if (above.is(bloom)) {
                        next.blossoms.add(new double[] {x + 0.5, y + 1, z + 0.5});
                    } else if ((above.is(leaf1) || above.is(leaf2) || above.is(leaf3)) && hsh(x * 7 + y + 1, z) < 0.35) {
                        next.leaves.add(new double[] {x + 0.5, y + 1, z + 0.5, above.is(leaf1) ? 0 : above.is(leaf2) ? 1 : 2});
                    }
                } else if (above.isAir()) {
                    if (s.is(pollen)) {
                        Pollen f = new Pollen();
                        f.x = x + 0.5;
                        f.y = y + 1;
                        f.z = z + 0.5;
                        f.next = 2 + rnd() * 12;
                        next.pollen.put(key3(x, y, z), f);
                    } else {
                        int c = flowerColor(s);
                        if (c >= 0) {
                            next.flowers.add(new double[] {x + 0.5, y + (groundFlower(s) ? 0.7 : 1), z + 0.5, c});
                        }
                    }
                }
                if (s.is(tip) && !above.is(tip)) {
                    next.buds.add(new double[] {x + 0.5, y + 0.6, z + 0.5});
                } else if (s.is(hollow) && next.hollows.size() < 400) {
                    next.hollows.add(new double[] {x + 0.5, y + 0.5, z + 0.5});
                }
                if (terrain(s)) {
                    break;
                }
                above = s;
            }
        }
    }

    // After a pass: where the beat starts (the Heart's eye holes once seen; until then the buds' middle).
    private static void afterPass() {
        if (!cur.hollows.isEmpty()) {
            double sx = 0, sz = 0;
            for (double[] h : cur.hollows) {
                sx += h[0];
                sz += h[2];
            }
            beatX = sx / cur.hollows.size();
            beatZ = sz / cur.hollows.size();
            heartKnown = beatSet = true;
        } else if (!heartKnown && !cur.buds.isEmpty() && !beatSet) {
            double sx = 0, sz = 0;
            for (double[] b : cur.buds) {
                sx += b[0];
                sz += b[2];
            }
            beatX = sx / cur.buds.size();
            beatZ = sz / cur.buds.size();
            beatSet = true;
        }
    }

    // The sun flecks around the camera, re-measured every 6 s or after 6 blocks: only under the canopy, and only where the
    // sun's ray climbs out of the canopy without hitting anything.
    private static void fleckStep(ClientLevel level, Vec3 c) {
        double t = level.getGameTime() / 20.0;
        float a = level.getSunAngle(1f);
        double sx = -Math.sin(a), sy = Math.cos(a);
        float n = Mth.clamp((0.8f - level.getSkyDarken(1f)) / 0.6f, 0, 1);
        if (fleckRow < 0) {
            if (t - fleckAt < 6 && Math.hypot(c.x - fleckFx, c.z - fleckFz) <= 6) {
                return;
            }
            fleckAt = t;
            fleckFx = c.x;
            fleckFz = c.z;
            if (sy < 0.15 || n > 0.5) {
                flecks = new ArrayList<>();
                return;
            }
            FS[0] = sx;
            FS[1] = sy;
            FS[2] = 0;
            fleckX0 = Mth.floor(c.x - FLECK_R);
            fleckZ0 = Mth.floor(c.z - FLECK_R);
            flecksNext = new ArrayList<>();
            fleckRow = 0;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 0; k < 4 && fleckRow < 2 * FLECK_R; k++, fleckRow++) {
            int x = fleckX0 + fleckRow;
            for (int z = fleckZ0; z < fleckZ0 + 2 * FLECK_R; z++) {
                int g = top(level, x, z);
                if (g < -900) {
                    continue;
                }
                int roof = 0;
                for (int y = g + 3; y < g + 50; y++) {
                    if (isLeaf(level.getBlockState(pos.set(x, y, z)))) {
                        roof = y;
                        break;
                    }
                }
                if (roof == 0) {
                    continue;
                }
                double px = x + 0.15 + hsh(x * 3, z) * 0.7, pz = z + 0.15 + hsh(z * 5, x) * 0.7, py = g + 1.02;
                boolean ok = true, out = false, hole = false;
                for (double d = 0.6; d < 70; d += 0.5) {
                    double qx = px + FS[0] * d, qy = py + FS[1] * d, qz = pz + FS[2] * d;
                    if (qy > roof + 14) {
                        out = true;
                        break;
                    }
                    int bx = Mth.floor(qx), by = Mth.floor(qy), bz = Mth.floor(qz);
                    if (opaque(level.getBlockState(pos.set(bx, by, bz)))) {
                        ok = false;
                        break;
                    }
                    // the spot is the image of the sun through a HOLE in the canopy: where the ray reaches the canopy's
                    // height, the blocks around it must be leaves. A ray leaving by the canopy's open side (low sun, the
                    // edge of the woods, a sparse crown) is plain sunlight, not a fleck: without this, every column near
                    // an edge got a spot and the ground turned into polka dots.
                    if (!hole && by >= roof) {
                        if (leavesAround(level, pos, bx, by, bz) < 5) {
                            ok = false;
                            break;
                        }
                        hole = true;
                    }
                }
                if (ok && out && hole) {
                    flecksNext.add(new double[] {px, py, pz, Math.max(4, roof - g), rnd() * Mth.TWO_PI});
                }
            }
        }
        if (fleckRow >= 2 * FLECK_R) {
            flecks = flecksNext;
            fleckRow = -1;
        }
    }

    private static void reset(boolean all) {
        strength = 0;
        firstPass = true;
        scanRow = -SCAN - 1;
        next = new Scan();
        for (Petal p : PETAL) {
            p.x = 1e9;
        }
        MOTES.clear();
        PUFFS.clear();
        COOL.clear();
        vortex = null;
        nextVortex = 6;
        flecks = new ArrayList<>();
        fleckRow = -1;
        fleckAt = -100;
        if (leafLoop != null) {
            leafLoop.end();
            leafLoop = null;
        }
        if (cricketLoop != null) {
            cricketLoop.end();
            cricketLoop = null;
        }
        if (all) {
            cur = new Scan();
            heartKnown = beatSet = false;
            lastBeat = Long.MIN_VALUE;
        }
    }

    // ---------------------------------------------------------------- petals

    // kind: 0 cherry blossom, 1 leaf, 2 flower petal
    private static void spawnPetal(ClientLevel level, Petal p, Vec3 f, boolean any) {
        double u = rnd();
        int kind = u < 0.55 ? 0 : u < 0.8 ? 1 : 2;
        List<double[]> src = kind == 0 ? cur.blossoms : kind == 1 ? cur.leaves : cur.flowers;
        for (int k = 0; k < 8 && !src.isEmpty(); k++) {
            double[] b = src.get(RANDOM.nextInt(src.size()));
            if (Math.abs(b[0] - f.x) > FLECK_R || Math.abs(b[2] - f.z) > FLECK_R) {
                continue;
            }
            // the flower petal only goes when it is windy: it starts held and waits for the gust
            double drop = kind == 2 ? 0 : any ? rnd() * 12 : 0;
            double x = b[0] + (rnd() - 0.5) * (kind == 2 ? 0.6 : 1), z = b[2] + (rnd() - 0.5) * (kind == 2 ? 0.6 : 1);
            double y = b[1] - (kind == 2 ? 0 : 0.1) - drop;
            int g = top(level, x, z);
            if (g < -900 || y < g + 1 - 0.3) {
                continue;
            }
            if (kind == 0) {
                blossomColor(p);
            } else if (kind == 1) {
                leafColor(p, (int) b[3], 0.85 + rnd() * 0.3);
            } else {
                float[] c = FCOL[(int) b[3]];
                double j = 0.9 + rnd() * 0.15;
                p.r = (float) (c[0] * j);
                p.g = (float) (c[1] * j);
                p.b = (float) (c[2] * j);
            }
            p.x = x;
            p.y = y;
            p.z = z;
            p.vx = 0;
            p.vy = kind == 2 ? 0 : -0.5;
            p.vz = 0;
            p.a = rnd() * Mth.TWO_PI;
            p.w = (rnd() < 0.5 ? -1 : 1) * (kind == 1 ? 4 + rnd() * 6 : 3 + rnd() * 5);
            p.tilt = rnd() * Mth.TWO_PI;
            p.rest = 0;
            p.kind = kind;
            p.hold = kind == 2 ? 1 : 0;
            p.sz = kind == 1 ? 0.13 + rnd() * 0.04 : kind == 2 ? 0.085 : 0.075;
            return;
        }
        p.x = 1e9;
    }

    private static void puff(double x, double y, double z, int n, double k) {
        Puff P = new Puff();
        P.x = x;
        P.y = y;
        P.z = z;
        P.k = k;
        PUFFS.add(P);
        for (int i = 0; i < n; i++) {
            double a = rnd() * Mth.TWO_PI, u = rnd();
            Mote m = new Mote();
            m.x = x;
            m.y = y;
            m.z = z;
            m.vx = Math.cos(a) * (0.4 + u) * k;
            m.vy = (1.6 + rnd() * 1.6) * k;
            m.vz = Math.sin(a) * (0.4 + u) * k;
            m.life = 9 + rnd() * 8;
            m.ph = rnd() * Mth.TWO_PI;
            MOTES.add(m);
        }
    }

    private static void step(ClientLevel level, Vec3 f, Vec3 player, double dt, double t) {
        // the whirlwind: born in front of whoever looks, goes with the wind, lasts some 6 s
        nextVortex -= dt;
        if (vortex == null && nextVortex < 0) {
            double a = Math.atan2(lookZ, lookX) + (rnd() - 0.5) * 1.6, d = 7 + rnd() * 8;
            Vortex V = new Vortex();
            V.x = f.x + Math.cos(a) * d;
            V.z = f.z + Math.sin(a) * d;
            V.life = 6;
            V.g = (rnd() < 0.5 ? -1 : 1) * 30;
            V.rc = 1.6;
            vortex = V;
            nextVortex = 16 + rnd() * 14;
            // the carpet of petals on the ground lifts
            for (Petal p : PETAL) {
                if (p.rest > 0 && Math.hypot(p.x - V.x, p.z - V.z) < 7) {
                    p.rest = 0;
                    p.vy = 0.5;
                }
            }
            int made = 0;
            for (Petal p : PETAL) {
                if (made >= 160) {
                    break;
                }
                if (!(p.x > 1e8 || p.rest > 3)) {
                    continue;
                }
                double r = Math.sqrt(rnd()) * 6, b = rnd() * Mth.TWO_PI, x = V.x + Math.cos(b) * r, z = V.z + Math.sin(b) * r;
                int g = top(level, x, z);
                made++;
                if (g < -900) {
                    continue;
                }
                int kind = rnd() < 0.3 ? 1 : 0;
                if (kind == 1) {
                    leafColor(p, RANDOM.nextInt(3), 0.75 + rnd() * 0.25);
                } else {
                    blossomColor(p);
                }
                p.x = x;
                p.z = z;
                p.y = g + 1.05;
                p.vx = 0;
                p.vy = 0.3;
                p.vz = 0;
                p.a = rnd() * Mth.TWO_PI;
                p.w = 6;
                p.tilt = rnd() * Mth.TWO_PI;
                p.rest = 0;
                p.kind = kind;
                p.hold = 0;
                p.sz = kind == 1 ? 0.13 : 0.075;
            }
        }
        Vortex V = vortex;
        if (V != null) {
            double[] w = wind(V.x, V.z, t);
            V.x += w[0] * 0.5 * dt;
            V.z += w[1] * 0.5 * dt;
            V.age += dt;
            if (V.age > V.life) {
                vortex = null;
                V = null;
            }
        }
        // petals (until the woods are read, the first ones spread in the air instead of all starting at the canopy)
        int idle = 0;
        for (Petal p : PETAL) {
            if (p.x > 1e8) {
                idle++;
            }
        }
        boolean fresh = idle > PETALS * 0.7;
        for (Petal p : PETAL) {
            if (p.x > 1e8) {
                spawnPetal(level, p, f, fresh);
                continue;
            }
            if (p.rest > 0) {
                p.rest += dt;
                if (p.rest > 9) {
                    spawnPetal(level, p, f, false);
                }
                continue;
            }
            double[] w = wind(p.x, p.z, t);
            if (p.hold > 0) {
                // held on the flower: a strong gust tears it off and throws it up; without wind it gives up for another flower
                p.hold += dt;
                if (w[2] > 0.3 && rnd() < dt * 5 * (w[2] - 0.3)) {
                    p.hold = 0;
                    p.vx = w[0] * 0.6;
                    p.vz = w[1] * 0.6;
                    p.vy = 1.2 + rnd() * 1.4;
                } else {
                    if (p.hold > 12) {
                        spawnPetal(level, p, f, false);
                    }
                    continue;
                }
            }
            double ux = w[0], uz = w[1], uy = 0;
            if (V != null) {
                // Rankine: the core spins as one, outside falls off as 1/r; rising in the core; it all fades at the end
                double dx = p.x - V.x, dz = p.z - V.z, r = Math.max(0.01, Math.hypot(dx, dz)), life = Math.sin(Math.min(1, V.age / V.life) * Math.PI);
                double vt = (r < V.rc ? V.g / (2 * Math.PI * V.rc * V.rc) * r : V.g / (2 * Math.PI * r)) * life;
                ux += -dz / r * vt - dx / r * 0.6 * life;
                uz += dx / r * vt - dz / r * 0.6 * life;
                uy += 2.4 * Math.exp(-r * r / 9) * life;
            }
            // a light plate: the drag brings it to the air's speed; falls at ~0.6 block/s swaying with its turn
            // a leaf is heavier: less drag, falls faster and flips more
            double kd = p.kind == 1 ? 1.7 : 2.5, fall = p.kind == 1 ? 1.0 : 0.6, sw = p.kind == 1 ? 0.6 : 0.35, e = Math.min(1, dt * kd);
            p.vx += (ux - p.vx) * e;
            p.vz += (uz - p.vz) * e;
            p.vy += ((uy - fall + sw * Math.sin(p.a * 2)) - p.vy) * e;
            double ox = p.x, oz = p.z;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.a += p.w * dt;
            int g0 = top(level, p.x, p.z);
            if (g0 < -900) {
                spawnPetal(level, p, f, false);
                continue;
            }
            double g = g0 + 1;
            if (p.y <= g + 0.02) {
                if (g - p.y > 1.2) {
                    // ran into a trunk or a rock: it stops against it and slides down
                    p.x = ox;
                    p.z = oz;
                    p.vx = 0;
                    p.vz = 0;
                } else {
                    p.y = g + 0.02;
                    p.rest = 0.001;
                }
            }
            if (Math.abs(p.x - f.x) > 34 || Math.abs(p.z - f.z) > 34) {
                spawnPetal(level, p, f, false);
            }
        }
        // pollen: the giant flower lets it out in a strong gust, or when someone comes close (the stem sways)
        if (night < 0.5f) {
            for (Pollen fl : cur.pollen.values()) {
                if (Math.abs(fl.x - f.x) > 45 || Math.abs(fl.z - f.z) > 45) {
                    continue;
                }
                fl.next -= dt * (0.3 + wind(fl.x, fl.z, t)[2] * 1.4);
                boolean close = Math.hypot(fl.x - player.x, fl.z - player.z) < 3.5 && fl.next < 8;
                if (fl.next < 0 || close) {
                    puff(fl.x, fl.y, fl.z, 46, 1);
                    fl.next = 10 + rnd() * 10;
                }
            }
            // a ground flower: whoever walks through it shakes out a little pollen
            int cx = Mth.floor(player.x), cz = Mth.floor(player.z);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    int x = cx + a, z = cz + b, g = top(level, x, z);
                    if (g < -900 || !groundFlower(level.getBlockState(pos.set(x, g + 1, z)))) {
                        continue;
                    }
                    long k = key(x, z);
                    double last = COOL.containsKey(k) ? COOL.get(k) : -99;
                    if (t - last > 5 && Math.hypot(x + 0.5 - player.x, z + 0.5 - player.z) < 1.3) {
                        COOL.put(k, t);
                        puff(x + 0.5, g + 1.5, z + 0.5, 10, 0.5);
                    }
                }
            }
            if (COOL.size() > 256) {
                COOL.clear();
            }
        }
        for (int i = MOTES.size() - 1; i >= 0; i--) {
            Mote m = MOTES.get(i);
            // a pollen grain: the drag kills the quick push, then it goes with the air and settles very slowly
            double[] w = wind(m.x, m.z, t);
            double e = Math.min(1, dt * 1.6);
            m.vx += (w[0] * 0.6 - m.vx) * e;
            m.vz += (w[1] * 0.6 - m.vz) * e;
            m.vy += (-0.05 - m.vy) * e;
            m.x += (m.vx + Math.sin(m.age * 1.7 + m.ph) * 0.08) * dt;
            m.y += m.vy * dt;
            m.z += (m.vz + Math.cos(m.age * 1.3 + m.ph) * 0.08) * dt;
            m.age += dt;
            if (m.age >= m.life) {
                MOTES.set(i, MOTES.get(MOTES.size() - 1));
                MOTES.remove(MOTES.size() - 1);
            }
        }
        if (MOTES.size() > MAX_MOTES) {
            MOTES.subList(0, MOTES.size() - MAX_MOTES).clear();
        }
        for (int i = PUFFS.size() - 1; i >= 0; i--) {
            Puff P = PUFFS.get(i);
            double[] w = wind(P.x, P.z, t);
            P.x += w[0] * 0.6 * dt;
            P.z += w[1] * 0.6 * dt;
            P.age += dt;
            if (P.age >= 8) {
                PUFFS.remove(i);
            }
        }
    }

    // the beat: tum (strong) and tum (weak) 0.35 s later, every 3.2 s, running through the roots at 10 blocks/s
    private static double beat(double x, double z, double t) {
        double d = Math.hypot(x - beatX, z - beatZ), ph = (((t - d / 10) % 3.2) + 3.2) % 3.2;
        double a = ph / 0.11, b = (ph - 0.35) / 0.11, c = (ph - 3.2) / 0.11;
        return Math.exp(-a * a) + 0.7 * Math.exp(-b * b) + Math.exp(-c * c);
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
            step(level, cam, mc.player.getPosition(partial), dt, t);
        }
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (night < 0.5f && SUN[1] >= 0.15) {
            VertexConsumer light = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
            renderFlecks(light, last, cam, t, fade);
            buffers.endBatch(FxRenderTypes.GLOW_FLAT);
        }
        VertexConsumer area = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        renderPetals(area, last, cam, fade);
        renderPuffs(area, last, cam, fade);
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
        renderMotes(glow, last, cam, fade);
        renderBeat(glow, last, cam, t, fade);
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

    // a round soft spot facing the camera; seg pieces around (tiny things need few)
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

    // a flat ellipse lying on the ground: the long axis (ra) along (ux, uz)
    private static void ell(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double ux, double uz, double ra, double rb,
            float r, float g, float b, float a, float edge) {
        double px = x + ux * ra, pz = z + uz * ra;
        for (int q = 1; q <= 14; q++) {
            double ang = q / 14.0 * Math.PI * 2, p = Math.cos(ang) * ra, s = Math.sin(ang) * rb;
            double qx = x + ux * p - uz * s, qz = z + uz * p + ux * s;
            v(out, last, cam, x, y, z, r, g, b, a);
            v(out, last, cam, px, y, pz, r, g, b, a * edge);
            v(out, last, cam, qx, y, qz, r, g, b, a * edge);
            v(out, last, cam, qx, y, qz, r, g, b, a * edge);
            px = qx;
            pz = qz;
        }
    }

    private static void renderFlecks(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        double hz = Math.max(1e-3, Math.abs(SUN[0])), ux = SUN[0] == 0 ? 1 : -SUN[0] / hz, uz = 0, stretch = 1 / Math.max(0.3, SUN[1]);
        float day = (1 - night) * fade;
        for (double[] f : flecks) {
            double d = Math.hypot(f[0] - cam.x, f[2] - cam.z);
            if (d > 30) {
                continue;
            }
            // the leaf at the hole's edge sways: the spot trembles a little and sometimes goes (the hole closes)
            double w = wind(f[0], f[2], t)[2];
            double open = clamp01(0.35 + 0.9 * vnoise(f[0] * 0.6 + t * (0.8 + w * 2), f[2] * 0.6 + f[4]));
            if (open < 0.05) {
                continue;
            }
            double jx = Math.sin(t * (2 + w * 5) + f[4]) * 0.05 * f[3] / 10, jz = Math.cos(t * (1.7 + w * 4) + f[4]) * 0.05 * f[3] / 10;
            // the image of the sun: the sun is ~0.53 degrees across, so it grows ~1 block every 100 of height; the hole adds
            double r = 0.1 + f[3] * 0.0093;
            ell(out, last, cam, f[0] + jx, f[1], f[2] + jz, ux, uz, r * stretch, r, 1f, 0.9f, 0.62f, (float) (0.65 * open * clamp01((30 - d) / 8)) * day, 0.1f);
        }
    }

    private static final double[] PA = new double[3], PB = new double[3], PC = new double[3], PD = new double[3];

    private static void pt(double[] o, Petal p, double[] ex, double[] ey, double u, double w) {
        o[0] = p.x + ex[0] * u + ey[0] * w;
        o[1] = p.y + 0.01 + ex[1] * u + ey[1] * w;
        o[2] = p.z + ex[2] * u + ey[2] * w;
    }

    private static void q4(VertexConsumer out, PoseStack.Pose last, Vec3 cam, Petal p, double[] ex, double[] ey, double u0, double w0, double u1, double w1,
            double u2, double w2, double u3, double w3, float[] c0, float[] c1, float[] c2, float[] c3) {
        pt(PA, p, ex, ey, u0, w0);
        pt(PB, p, ex, ey, u1, w1);
        pt(PC, p, ex, ey, u2, w2);
        pt(PD, p, ex, ey, u3, w3);
        v(out, last, cam, PA[0], PA[1], PA[2], c0[0], c0[1], c0[2], c0[3]);
        v(out, last, cam, PB[0], PB[1], PB[2], c1[0], c1[1], c1[2], c1[3]);
        v(out, last, cam, PC[0], PC[1], PC[2], c2[0], c2[1], c2[2], c2[3]);
        v(out, last, cam, PD[0], PD[1], PD[2], c3[0], c3[1], c3[2], c3[3]);
    }

    private static final float[] COL = new float[4], CD = new float[4];
    private static final double[] EX = new double[3], EY = new double[3];

    private static void renderPetals(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        float lit = Mth.lerp(night, 1f, 0.35f);
        for (Petal p : PETAL) {
            if (p.x > 1e8 || p.hold > 0) {
                continue;
            }
            double sz = p.sz;
            double d = Math.sqrt((p.x - cam.x) * (p.x - cam.x) + (p.y - cam.y) * (p.y - cam.y) + (p.z - cam.z) * (p.z - cam.z));
            if (d > 34) {
                continue;
            }
            // a turning plate: its normal turns round the tilted axis; the top side catches light, the bottom is in shade
            boolean rest = p.rest > 0;
            double ca = rest ? 0 : Math.cos(p.a), sa = rest ? 1 : Math.sin(p.a), ct = Math.cos(p.tilt), st = Math.sin(p.tilt);
            float k = (float) (rest ? 1 : 0.55 + 0.45 * Math.abs(sa)) * lit;
            COL[0] = p.r * k;
            COL[1] = p.g * k;
            COL[2] = p.b * k;
            COL[3] = (float) ((rest ? clamp01(1 - (p.rest - 6) / 3) : 1) * 0.95 * clamp01(1.25 - d / 34)) * fade;
            // the plate's two axes: one on the ground (ct, st) and one that turns up with the angle
            EX[0] = ct * sz;
            EX[1] = 0;
            EX[2] = st * sz;
            if (rest) {
                EY[0] = -st * sz * 0.75;
                EY[1] = 0;
                EY[2] = ct * sz * 0.75;
            } else {
                EY[0] = -st * sz * sa * 0.75;
                EY[1] = ca * sz * 0.75;
                EY[2] = ct * sz * sa * 0.75;
            }
            float[] c = COL;
            if (p.kind == 1) {
                // a leaf: a long diamond with a little stalk; the vein is the middle a shade darker
                CD[0] = c[0] * 0.8f;
                CD[1] = c[1] * 0.8f;
                CD[2] = c[2] * 0.8f;
                CD[3] = c[3];
                q4(out, last, cam, p, EX, EY, -1, 0, -0.1, -0.55, 0.25, 0, -0.1, 0.55, c, c, CD, c);
                q4(out, last, cam, p, EX, EY, 0.25, 0, -0.1, -0.55, 1.15, 0, -0.1, 0.55, CD, c, c, c);
                q4(out, last, cam, p, EX, EY, -1.35, -0.05, -1, -0.05, -1, 0.05, -1.35, 0.05, CD, CD, CD, CD);
            } else if (p.kind == 2) {
                // a flower petal: a rounded drop, without the cherry's notch
                q4(out, last, cam, p, EX, EY, -1, -0.2, 0.1, -0.7, 0.1, 0.7, -1, 0.2, c, c, c, c);
                q4(out, last, cam, p, EX, EY, 0.1, -0.7, 0.85, -0.45, 0.85, 0.45, 0.1, 0.7, c, c, c, c);
            } else {
                q4(out, last, cam, p, EX, EY, -1, -0.35, 0.2, -0.8, 0.2, 0.8, -1, 0.35, c, c, c, c);
                q4(out, last, cam, p, EX, EY, 0.2, -0.8, 1, -0.45, 0.85, 0, 0.2, 0, c, c, c, c);
                q4(out, last, cam, p, EX, EY, 0.2, 0, 0.85, 0, 1, 0.45, 0.2, 0.8, c, c, c, c);
            }
        }
    }

    private static void renderPuffs(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        for (Puff P : PUFFS) {
            // the cloud: opens with the square root of time (diffusion) and thins out
            double sg = 0.6 + 1.1 * Math.sqrt(P.age), a = 0.28 * P.k / (1 + P.age * 0.9) * clamp01(P.age / 0.25);
            disc(out, last, cam, P.x, P.y + 0.6 * Math.min(1, P.age) * P.k, P.z, sg, 12, 1f, 0.88f, 0.45f, (float) a * fade, 0);
        }
    }

    private static void renderMotes(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        for (Mote m : MOTES) {
            double d = Math.sqrt((m.x - cam.x) * (m.x - cam.x) + (m.y - cam.y) * (m.y - cam.y) + (m.z - cam.z) * (m.z - cam.z));
            if (d > 40) {
                continue;
            }
            double a = clamp01(m.age / 0.3) * clamp01((m.life - m.age) / 2) * clamp01(1.3 - d / 40);
            // in the sun the grain sparkles (light scattered by its shell)
            double sp = 0.55 + 0.45 * Math.pow(Math.abs(Math.sin(m.age * 3 + m.ph)), 6);
            disc(out, last, cam, m.x, m.y, m.z, Math.max(0.025, d * 0.0025), 6, 1f, 0.86f, 0.4f, (float) (a * 0.8 * sp) * fade, 0.3f);
        }
    }

    private static void renderBeat(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float fade) {
        if (!beatSet) {
            return;
        }
        float k = Mth.lerp(night, 0.4f, 1f) * fade;
        for (double[] b : cur.buds) {
            double d = Math.hypot(b[0] - cam.x, b[2] - cam.z);
            if (d > 60) {
                continue;
            }
            double p = beat(b[0], b[2], t);
            disc(out, last, cam, b[0], b[1], b[2], 0.6 + 0.7 * p, 12, 1f, 0.4f, 0.75f, (float) (0.25 + 0.7 * p) * k, 0);
            disc(out, last, cam, b[0], b[1], b[2], 2 + 2 * p, 12, 1f, 0.3f, 0.65f, (float) (0.06 + 0.22 * p) * k, 0);
        }
        if (!cur.hollows.isEmpty()) {
            double p = beat(beatX, beatZ, t);
            // the glow comes from inside the hole: the disc goes in front of the block (or its own face hides it)
            for (double[] h : cur.hollows) {
                double dx = cam.x - h[0], dy = cam.y - h[1], dz = cam.z - h[2], d = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
                if (d > 120) {
                    continue;
                }
                disc(out, last, cam, h[0] + dx / d * 0.7, h[1] + dy / d * 0.7, h[2] + dz / d * 0.7, 1.1, 12, 1f, 0.25f, 0.55f, (float) (0.12 + 0.35 * p) * k, 0);
            }
        }
    }

    // ---------------------------------------------------------------- the screen and the fog

    public static boolean gradeOn() {
        return active() && FxShaders.jjGrade != null;
    }

    // A forest too alive: green and gold by day; teal at night with the buds' pink left (never darkening).
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.jjGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        float n = night;
        sh.safeGetUniform("Sat").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.2f, 1.08f)));
        sh.safeGetUniform("Contrast").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.04f, 1.03f)));
        // by day a warm golden light over it all (the page's warm glow from above); none at night
        sh.safeGetUniform("Wash").set(1f, 236 / 255f, 170 / 255f, 0.06f * (1 - n) * fade);
        sh.safeGetUniform("Warm").set(0f);
        sh.safeGetUniform("Vig").set(20 / 255f, 10 / 255f, 30 / 255f, 0.28f * n * fade);
        sh.safeGetUniform("Flash").set(0f);
        FxPost.pass(sh);
    }

    // The fog: a pale green haze by day, deep teal at night (only the hue moves, never down).
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float k = fade * 0.7f;
        float r = Mth.lerp(night, 0xd4 / 255f, 0x0b / 255f), g = Mth.lerp(night, 0xe6 / 255f, 0x16 / 255f), b = Mth.lerp(night, 0xc4 / 255f, 0x18 / 255f);
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
        float near = 12f, far = Mth.lerp(night, 140f, 70f);
        event.setNearPlaneDistance(Mth.lerp(fade, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), near)));
        event.setFarPlaneDistance(Mth.lerp(fade, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), far)));
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- sound

    private static void play(ClientLevel level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playLocalSound(x, y, z, s, SoundSource.AMBIENT, vol * strength, pitch, false);
    }

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 p) {
        if (leafLoop == null || !mc.getSoundManager().isActive(leafLoop)) {
            leafLoop = new LeafLoop();
            mc.getSoundManager().play(leafLoop);
        }
        if (cricketLoop == null || !mc.getSoundManager().isActive(cricketLoop)) {
            cricketLoop = new CricketLoop();
            mc.getSoundManager().play(cricketLoop);
        }
        double t = (level.getGameTime() % 240000L) / 20.0, dt = 0.05;
        windGust = (float) wind(p.x, p.z, t)[2];
        float n = Mth.clamp((0.8f - level.getSkyDarken(1f)) / 0.6f, 0, 1);
        if (n < 0.5f) {
            // birds: short whistled phrases, each its own way, all round in the woods
            nextBird -= dt;
            if (nextBird < 0) {
                double a = rnd() * Mth.TWO_PI, r = 6 + rnd() * 14;
                play(level, p.x + Math.cos(a) * r, p.y + 3 + rnd() * 6, p.z + Math.sin(a) * r, ModSounds.HANAMI_PASSARO.get(), 1.2f,
                        0.85f + (float) rnd() * 0.3f);
                nextBird = 0.8 + rnd() * 2.5;
            }
        }
        // the Heart: deep, louder near the tree; plays when the pulse passes the listener
        if (heartKnown) {
            double d = Math.hypot(p.x - beatX, p.z - beatZ), vol = Math.pow(clamp01(1 - d / 110), 2) * (n > 0.5f ? 0.5 : 0.25);
            double s = t - d / 10, ph = ((s % 3.2) + 3.2) % 3.2;
            long idx = (long) Math.floor(s / 3.2);
            if (vol > 0.005 && ph < 0.1 && idx != lastBeat) {
                lastBeat = idx;
                // (a few blocks from the player toward the tree: heard from far, and from the right side)
                double dd = Math.max(1, d);
                play(level, p.x + (beatX - p.x) / dd * 4, p.y + 1, p.z + (beatZ - p.z) / dd * 4, ModSounds.HANAMI_CORACAO.get(), (float) Math.min(1, vol * 2.5), 1f);
            }
        }
    }

    // The canopy rustling all round the player, rising with the gusts.
    private static final class LeafLoop extends AbstractTickableSoundInstance {
        LeafLoop() {
            super(ModSounds.HANAMI_FOLHAS.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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
            volume = (0.2f + 0.8f * windGust) * 0.45f * strength;
            pitch = 0.95f + 0.1f * windGust;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    // The crickets at night, all round the player.
    private static final class CricketLoop extends AbstractTickableSoundInstance {
        CricketLoop() {
            super(ModSounds.HANAMI_GRILO.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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
            Minecraft mc = Minecraft.getInstance();
            float n = mc.level == null ? 0 : Mth.clamp((0.8f - mc.level.getSkyDarken(1f)) / 0.6f, 0, 1);
            volume = Math.max(0.001f, 0.5f * n * strength);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
