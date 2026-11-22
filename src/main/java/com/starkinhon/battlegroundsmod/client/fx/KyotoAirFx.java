// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.ModBlocks;
import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The air of the Outono de Kyoto (Stark 2026-11-19: the preview forja/jujutsu-kyoto-ambiente "aprovadíssimo"). amb.js
// there is the source of truth and this is its port, the same names where it helps. Fades in when the player walks into
// the biome and out when the player leaves (BiomeAura). Everything makes physical sense:
// - the mirror pond: the garden's dark water reflects the Golden Pavilion, the maples and the sky. The blocks above the
//   water near the pond, read once and mirrored on the water's plane (souls_kyoto_mirror), drawn each frame into a
//   picture of their own; the pond's surface (souls_kyoto_pond) shows that picture where it has something and the sky
//   where it doesn't. The lower the look, the more mirror (Fresnel); the farther from the water what is reflected, the
//   more the ripple bends it. (With a shader pack the frame isn't ours: the pond stays vanilla water.)
// - water light: the sun hits the pond and bounces up; where that ray lands (a wall facing the pond, the eave's
//   underside) patches of light wander. Only where the ray's sum really falls inside the pond;
// - the gold glints: each gold leaf of the pavilion is a small, slightly crooked mirror. It glints when the sun's
//   reflection in it points at whoever looks (moving the camera moves the glints);
// - a maple leaf falls like a pendulum: slides to one side, pitches up, nearly stops and comes back, falling more in the
//   middle of the swing. Against the sun it lights up. The ginkgo drops its leaves almost all at once: now and then a
//   tree pours a golden shower; bamboo leaves spin fast round their length;
// - leaves on the water: on the pond they float slowly with the wind until they run aground on the downwind shore; in a
//   stream they go down with the current and catch on the stones. On the ground a gust lifts the litter and runs it;
// - koi: swim waving their bodies (a wave growing to the tail), turn away from the shore and each other, and when
//   someone stands at the edge they come and rise to the surface. They push the floating leaves;
// - at night the garden's maples are lit from below (the temples' autumn "light-up"), and that shows in the pond too;
// - red dragonflies by day (stop in the air, dart 2 to 5 blocks, sometimes touch the water), crows crossing the sky, the
//   shishi-odoshi by the tea house (fills, tips into the pond and knocks back on its stone: "kon");
// - the screen (fx_jjgrade): warm saturation by day; at night indigo edges, never a dark veil;
// - sounds built from noise and tones (tools/sounds/kyoto_sounds.py): leaves in the wind, a far temple bell, crows, the
//   bell cricket at night, bamboo knocking in the gusts, the shishi-odoshi.
public final class KyotoAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WIND = 0.5;
    private static final int SCAN = 64;
    private static final int LEAVES = 1500;
    // The mirror pond is off (Stark 2026-11-19): it only works on a still, full surface, and a Kyoto stream is running
    // water (sloped, not full), so the biome's water wouldn't all mirror alike. "Se não der em todos, nem tenta": the
    // water stays vanilla. The garden keeps its koi, gold glints, water light and light-up.
    private static final boolean MIRROR = false;

    private static float strength, lastStrength;
    private static float night;
    private static final double[] SUN = {0, 1, 0};
    private static double clock;
    private static long lastNanos;
    private static ClientLevel lastLevel;

    // ---------------------------------------------------------------- what the world has (the column scan)

    // One full pass over the square around the player, a row of columns per tick; then it replaces the last one.
    private static final class Scan {
        final List<double[]> src = new ArrayList<>();
        final List<double[]> litter = new ArrayList<>();
        final List<double[]> ginkgos = new ArrayList<>();
        final Long2IntOpenHashMap water = new Long2IntOpenHashMap();
        final List<double[]> bamboo = new ArrayList<>();
        // the Golden Pavilion's gold leaf (no pavilion near, no mirror pond)
        final List<int[]> gold = new ArrayList<>();
    }

    private static Scan cur = new Scan(), next = new Scan();
    private static int scanRow = -SCAN - 1, scanCx, scanCz;
    private static boolean firstPass = true;

    // the pond (P: the water's surface height) and the streams' current
    private static final LongOpenHashSet POND = new LongOpenHashSet();
    private static final Long2ObjectOpenHashMap<double[]> FLOW = new Long2ObjectOpenHashMap<>();
    private static double P = -999;
    private static int pondY = Integer.MIN_VALUE;
    private static double pondCx, pondCz, pondR;
    private static long[] pondNear = new long[0], flowNear = new long[0];
    private static final List<double[]> LITTER_NEAR = new ArrayList<>();
    private static long nearAt;

    // ---------------------------------------------------------------- the garden (read once around the pond)

    private static final class Garden {
        int x0, x1, z0, z1, y0, y1, sx;
        double cx, cz, r2;
        final LongOpenHashSet water = new LongOpenHashSet();
        final List<float[]> faces = new ArrayList<>();
        final List<double[]> gold = new ArrayList<>();
        final List<double[]> pav = new ArrayList<>();
        final List<double[]> tatami = new ArrayList<>();
        final Map<Long, List<double[]>> maples = new HashMap<>();
        long startedAt;
    }

    private static Garden building;
    private static boolean gardenOn;
    private static int gardenY = Integer.MIN_VALUE;
    private static double gardenCx, gardenCz;
    private static int gx0, gx1, gz0, gz1;
    private static long gardenAt = -100000;
    private static final List<double[]> GOLD = new ArrayList<>();
    private static final List<double[]> PAV = new ArrayList<>();
    private static final List<double[]> WALL = new ArrayList<>();
    private static long wallAt;
    private static final List<Spot> SPOTS = new ArrayList<>();
    private static double teaX, teaZ;
    private static boolean hasTea;

    private static final class Spot {
        double x, y, z;
        final List<double[]> b = new ArrayList<>();
    }

    // the mirror's mesh (relative to its origin) and the picture it is drawn into each frame
    private static VertexBuffer mirror;
    private static double mirX, mirY, mirZ;
    private static TextureTarget refl;
    private static float[] pondQuads = new float[0];
    private static boolean mirrorDrawn;

    // ---------------------------------------------------------------- moving things

    private static final class Leaf {
        double x, y, z, vx, vz, age, ph, phr, amp, hd, sp, sz, gy;
        int st = -1;
        boolean g, bam;
        float r, gg, b;
    }

    private static final Leaf[] LEAF = new Leaf[LEAVES];
    private static double nextShower = 5;

    private static final class Koi {
        double x, z, d, td, hd, th, sp, tsp, turn, L, id, ph, wt;
        int pat;
    }

    private static final List<Koi> KOI = new ArrayList<>();

    private static final class Fly {
        double x = 1e9, y, z, tx, ty, tz, vx, vy, vz, hold, hd, ph;
        boolean dip;
    }

    private static final Fly[] FLIES = new Fly[26];

    private static final class Crow {
        double x, y, z, hx, hz, ph, glide;
    }

    private static List<Crow> crows;
    private static double nextCrows = 30, crowLife;
    private static boolean crowCall;

    private static final class Shishi {
        double x, y, z, fill, ang = 0.35, w, pour;
        int ux, uz;
        boolean back, knock;
        final List<double[]> drops = new ArrayList<>();
    }

    private static Shishi shishi;

    // sounds
    private static LeafLoop leafLoop;
    private static double nextBell = 8, nextBug, nextKnock;

    private KyotoAirFx() {
    }

    static {
        for (int i = 0; i < LEAVES; i++) {
            LEAF[i] = new Leaf();
        }
        for (int i = 0; i < FLIES.length; i++) {
            FLIES[i] = new Fly();
            FLIES[i].hold = rnd() * 2;
            FLIES[i].hd = rnd() * Mth.TWO_PI;
            FLIES[i].ph = rnd() * 9;
        }
    }

    // ---------------------------------------------------------------- small helpers

    private static double rnd() {
        return RANDOM.nextDouble();
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    // An integer hash (no sin hash in loops: it costs a lot here).
    private static double hsh(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffffff) / 16777216.0;
    }

    private static double hash3(int x, int y, int z, int s) {
        return hsh(x * 31 + y * 7919 + s * 104729, z * 17 - y * 3571 + s);
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

    private static long key(double x, double z) {
        return key(Mth.floor(x), Mth.floor(z));
    }

    private static long key(int x, int z) {
        return (long) x << 32 ^ (z & 0xffffffffL);
    }

    private static int kx(long k) {
        return (int) (k >> 32);
    }

    private static int kz(long k) {
        return (int) k;
    }

    private static float[] hex(int v) {
        return new float[] {(v >> 16 & 255) / 255f, (v >> 8 & 255) / 255f, (v & 255) / 255f};
    }

    // The ground's top under (x, z), leaves left out (a falling leaf goes through the crowns): -999 where not loaded.
    private static int top(ClientLevel level, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!level.hasChunk(ix >> 4, iz >> 4)) {
            return -999;
        }
        int h = ClientHeights.noLeaves(level, ix, iz) - 1;
        return h <= level.getMinBuildHeight() ? -999 : h;
    }

    private static boolean isWater(ClientLevel level, int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
    }

    // Autumn wind: a weak breeze and gusts running at 3 blocks/s. Gives vx, vz and strength 0..1 in W.
    private static final double[] W = new double[3];

    private static double[] wind(double x, double z, double t) {
        double c = Math.cos(WIND), s = Math.sin(WIND), along = x * c + z * s, across = -x * s + z * c;
        double g = clamp01(0.2 + 0.8 * vnoise(along / 18 - t * 0.17, across / 22) * (0.5 + 0.5 * Math.sin((along - t * 3) / 11)));
        double v = 0.3 + 1.9 * g;
        W[0] = c * v;
        W[1] = s * v;
        W[2] = g;
        return W;
    }

    // Rodrigues: v turned round the unit axis k by a.
    private static void rot(double[] v, double[] k, double a) {
        double c = Math.cos(a), s = Math.sin(a), d = v[0] * k[0] + v[1] * k[1] + v[2] * k[2];
        double x = v[0] * c + (k[1] * v[2] - k[2] * v[1]) * s + k[0] * d * (1 - c);
        double y = v[1] * c + (k[2] * v[0] - k[0] * v[2]) * s + k[1] * d * (1 - c);
        double z = v[2] * c + (k[0] * v[1] - k[1] * v[0]) * s + k[2] * d * (1 - c);
        v[0] = x;
        v[1] = y;
        v[2] = z;
    }

    // The leaves' colors (the preview's palette): red, crimson, orange, gold maple, ginkgo, bamboo.
    private static final float[][] PALC = {hex(0xc8301e), hex(0x9a1e18), hex(0xe0682a), hex(0xe8a830), hex(0xf0c838), hex(0x5a8a34)};
    private static final int GINKGO = 4, BAMBOO = 5;

    private static int leafKind(BlockState s) {
        Block b = s.getBlock();
        if (b == JujutsuBlocks.MAPLE_RED.get()) {
            return 0;
        }
        if (b == JujutsuBlocks.MAPLE_CRIMSON.get()) {
            return 1;
        }
        if (b == JujutsuBlocks.MAPLE_ORANGE.get()) {
            return 2;
        }
        if (b == JujutsuBlocks.MAPLE_GOLD.get()) {
            return 3;
        }
        if (b == JujutsuBlocks.GINKGO_LEAVES.get()) {
            return GINKGO;
        }
        if (b == JujutsuBlocks.BAMBOO_LEAVES.get()) {
            return BAMBOO;
        }
        return -1;
    }

    private static int litterKind(BlockState s) {
        Block b = s.getBlock();
        if (b == JujutsuBlocks.LITTER_RED.get()) {
            return 0;
        }
        if (b == JujutsuBlocks.LITTER_ORANGE.get()) {
            return 2;
        }
        if (b == JujutsuBlocks.LITTER_GOLD.get()) {
            return 3;
        }
        return -1;
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    // ---------------------------------------------------------------- tick: the scans, the sounds

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            if (lastStrength > 0 || gardenOn || building != null || !POND.isEmpty()) {
                reset(true);
            }
            return;
        }
        if (mc.level != lastLevel) {
            // another world or dimension: what was read belongs to the old one
            lastLevel = mc.level;
            reset(true);
        }
        if (mc.isPaused()) {
            return;
        }
        boolean in = BiomeAura.in("outono_de_kyoto", b -> b.is(ModWorldgen.OUTONO_DE_KYOTO));
        strength = Mth.clamp(strength + (in ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            if (lastStrength > 0) {
                reset(false);
            }
            return;
        }
        ClientLevel level = mc.level;
        Vec3 p = mc.player.position();
        // the first pass fast (the leaves need their trees), then a row a tick
        int rows = firstPass ? 16 : 1;
        for (int i = 0; i < rows; i++) {
            scanStep(level, p);
        }
        if (building != null) {
            gardenStep(level);
        }
        long now = level.getGameTime();
        if (gardenOn && now - wallAt > 100) {
            wallAt = now;
            pickWalls();
        }
        sounds(mc, level, p);
    }

    private static void scanStep(ClientLevel level, Vec3 p) {
        if (scanRow > SCAN) {
            Scan done = next;
            next = cur;
            cur = done;
            next.src.clear();
            next.litter.clear();
            next.ginkgos.clear();
            next.water.clear();
            next.bamboo.clear();
            next.gold.clear();
            firstPass = false;
            classify(level);
            nearWater(p);
            scanRow = -SCAN - 1;
        }
        if (scanRow == -SCAN - 1) {
            scanCx = Mth.floor(p.x);
            scanCz = Mth.floor(p.z);
            scanRow = -SCAN;
        }
        int x = scanCx + scanRow++;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = scanCz - SCAN; z <= scanCz + SCAN; z++) {
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int g = ClientHeights.noLeaves(level, x, z) - 1;
            int surf = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (g <= level.getMinBuildHeight()) {
                continue;
            }
            BlockState ground = level.getBlockState(pos.set(x, g, z));
            int lk = litterKind(ground);
            if (lk >= 0 && level.getBlockState(pos.set(x, g + 1, z)).isAir()) {
                next.litter.add(new double[] {x + 0.5, g + 1.02, z + 0.5, lk});
            } else if (ground.getFluidState().is(FluidTags.WATER) && ground.getFluidState().isSource() && kyoto(level, x, g, z)) {
                // (only the biome's own water: a lake or sea next door never gets the pond, the koi or the leaves)
                next.water.put(key(x, z), g);
            }
            if (ground.is(Blocks.BAMBOO_BLOCK) || level.getBlockState(pos.set(x, g + 2, z)).is(Blocks.BAMBOO_BLOCK)) {
                next.bamboo.add(new double[] {x + 0.5, g + 1, z + 0.5});
            }
            // the crowns' undersides: a leaf block with air under it lets leaves go
            boolean airBelow = false;
            for (int y = g + 1; y <= Math.min(surf, g + 40); y++) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                if (s.is(JujutsuBlocks.GOLD_LEAF.get())) {
                    next.gold.add(new int[] {x, y, z});
                }
                int k = s.isAir() ? -1 : leafKind(s);
                if (k >= 0 && airBelow) {
                    next.src.add(new double[] {x + 0.5, y, z + 0.5, k});
                    if (k == GINKGO) {
                        next.ginkgos.add(new double[] {x + 0.5, y, z + 0.5});
                    }
                }
                airBelow = s.isAir();
            }
        }
    }

    // After each pass: which water is the pond (a big flat body: the garden's), which runs in streams, and where to.
    private static void classify(ClientLevel level) {
        Long2IntOpenHashMap water = cur.water;
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList best = null;
        int bestY = 0;
        ArrayDeque<Long> q = new ArrayDeque<>();
        for (long k0 : water.keySet()) {
            if (seen.contains(k0)) {
                continue;
            }
            int y = water.get(k0);
            LongArrayList cl = new LongArrayList();
            q.add(k0);
            seen.add(k0);
            while (!q.isEmpty()) {
                long k = q.poll();
                cl.add(k);
                int x = kx(k), z = kz(k);
                for (long n : new long[] {key(x + 1, z), key(x - 1, z), key(x, z + 1), key(x, z - 1)}) {
                    if (!seen.contains(n) && water.containsKey(n) && water.get(n) == y) {
                        seen.add(n);
                        q.add(n);
                    }
                }
            }
            if (best == null || cl.size() > best.size()) {
                best = cl;
                bestY = y;
            }
        }
        LongOpenHashSet pond = new LongOpenHashSet();
        if (best != null && best.size() >= 250) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            double sx = 0, sz = 0;
            for (long k : best) {
                int x = kx(k), z = kz(k);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                sx += x + 0.5;
                sz += z + 0.5;
            }
            int w = maxX - minX + 1, d = maxZ - minZ + 1;
            // a pond, not a long stream: wide both ways and filling its box
            if (Math.min(w, d) >= 16 && best.size() >= 0.3 * w * d) {
                pond.addAll(best);
                double cx = sx / best.size(), cz = sz / best.size();
                boolean far = !gardenOn && building == null || bestY != gardenY || Math.hypot(cx - gardenCx, cz - gardenCz) > 40;
                boolean grew = gardenOn && (minX < gx0 + 6 || maxX > gx1 - 6 || minZ < gz0 + 6 || maxZ > gz1 - 6);
                if ((far || grew) && level.getGameTime() - gardenAt > 600 && pavilionNear(minX, maxX, minZ, maxZ)) {
                    startGarden(minX, maxX, minZ, maxZ, bestY, cx, cz, best);
                }
            }
        }
        if (gardenOn || building != null) {
            // the garden's own reading of the pond wins (it sees under the bridge and the whole pond at once)
            if (gardenOn && Minecraft.getInstance().player != null
                    && Math.hypot(Minecraft.getInstance().player.getX() - gardenCx, Minecraft.getInstance().player.getZ() - gardenCz) > 220) {
                dropGarden();
            }
        }
        if (!gardenOn) {
            POND.clear();
            POND.addAll(pond);
            if (!pond.isEmpty()) {
                pondY = bestY;
                P = bestY + 0.8889;
                measurePond();
            } else {
                pondY = Integer.MIN_VALUE;
                P = -999;
            }
        }
        flows(level, water);
    }

    private static boolean kyoto(ClientLevel level, int x, int y, int z) {
        return level.getBiome(new BlockPos(x, y, z)).is(ModWorldgen.OUTONO_DE_KYOTO);
    }

    // The mirror is the garden's: only a pond with the Golden Pavilion's gold on its shore (Stark's test, 2026-11-19: a
    // river, and a sea in the next biome, took the mirror and the floating leaves).
    private static boolean pavilionNear(int minX, int maxX, int minZ, int maxZ) {
        int n = 0;
        for (int[] g : cur.gold) {
            if (g[0] >= minX - 30 && g[0] <= maxX + 30 && g[2] >= minZ - 30 && g[2] <= maxZ + 30) {
                n++;
            }
        }
        return n >= 20;
    }

    private static void measurePond() {
        double sx = 0, sz = 0;
        for (long k : POND) {
            sx += kx(k) + 0.5;
            sz += kz(k) + 0.5;
        }
        pondCx = sx / Math.max(1, POND.size());
        pondCz = sz / Math.max(1, POND.size());
        double r = 0;
        for (long k : POND) {
            r = Math.max(r, Math.hypot(kx(k) + 0.5 - pondCx, kz(k) + 0.5 - pondCz));
        }
        pondR = r;
    }

    // The current: where the water goes down (a lower neighbor); on the flat, along the bed (its long axis) toward the
    // lower ground; then smoothed twice with the neighbors.
    private static void flows(ClientLevel level, Long2IntOpenHashMap water) {
        FLOW.clear();
        for (Long2IntMap.Entry e : water.long2IntEntrySet()) {
            long k = e.getLongKey();
            if (POND.contains(k)) {
                continue;
            }
            int x = kx(k), z = kz(k), y = e.getIntValue();
            double dx = 0, dz = 0;
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    long n = key(x + a, z + b);
                    if (water.containsKey(n) && !POND.contains(n) && water.get(n) < y) {
                        dx += a;
                        dz += b;
                    }
                }
            }
            if (dx == 0 && dz == 0) {
                double xx = 0, xz = 0, zz = 0;
                for (int a = -2; a <= 2; a++) {
                    for (int b = -2; b <= 2; b++) {
                        if (water.containsKey(key(x + a, z + b))) {
                            xx += a * a;
                            xz += a * b;
                            zz += b * b;
                        }
                    }
                }
                double ang = 0.5 * Math.atan2(2 * xz, xx - zz);
                dx = Math.cos(ang);
                dz = Math.sin(ang);
                int ga = top(level, x + dx * 5, z + dz * 5), gb = top(level, x - dx * 5, z - dz * 5);
                if (ga > gb) {
                    dx = -dx;
                    dz = -dz;
                }
            }
            double L = Math.hypot(dx, dz);
            if (L < 1e-6) {
                continue;
            }
            FLOW.put(k, new double[] {dx / L, dz / L, y});
        }
        for (int it = 0; it < 2; it++) {
            Long2ObjectOpenHashMap<double[]> out = new Long2ObjectOpenHashMap<>();
            for (Long2ObjectMap.Entry<double[]> e : FLOW.long2ObjectEntrySet()) {
                long k = e.getLongKey();
                double[] f = e.getValue();
                int x = kx(k), z = kz(k);
                double sx = f[0], sz = f[1];
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        double[] o = FLOW.get(key(x + a, z + b));
                        if (o != null && o != f) {
                            // (an axis has no sign: a neighbor pointing back is the same bed)
                            double s = o[0] * f[0] + o[1] * f[1] < 0 ? -0.5 : 0.5;
                            sx += o[0] * s;
                            sz += o[1] * s;
                        }
                    }
                }
                double L = Math.hypot(sx, sz);
                out.put(k, L < 1e-6 ? f : new double[] {sx / L, sz / L, f[2]});
            }
            FLOW.clear();
            FLOW.putAll(out);
        }
    }

    // Part of the leaves that end go straight back onto the water near the player (or the pond and stream go empty).
    private static void nearWater(Vec3 F) {
        double R = 30;
        LongArrayList pn = new LongArrayList(), fn = new LongArrayList();
        for (long k : POND) {
            if (Math.abs(kx(k) - F.x) < R && Math.abs(kz(k) - F.z) < R) {
                pn.add(k);
            }
        }
        for (long k : FLOW.keySet()) {
            if (Math.abs(kx(k) - F.x) < R && Math.abs(kz(k) - F.z) < R) {
                fn.add(k);
            }
        }
        pondNear = pn.toLongArray();
        flowNear = fn.toLongArray();
        LITTER_NEAR.clear();
        for (double[] L : cur.litter) {
            if (Math.abs(L[0] - F.x) < 22 && Math.abs(L[2] - F.z) < 22) {
                LITTER_NEAR.add(L);
            }
        }
        nearAt = System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- the garden: read once, a slice of x per tick

    private static void startGarden(int minX, int maxX, int minZ, int maxZ, int y, double cx, double cz, LongArrayList cells) {
        Garden g = new Garden();
        double r = 0;
        for (long k : cells) {
            r = Math.max(r, Math.hypot(kx(k) + 0.5 - cx, kz(k) + 0.5 - cz));
        }
        // the pond and what stands round it (the preview: R + 48 around the garden's center), at most 90 out
        double reach = Math.min(90, r + 48);
        g.cx = cx;
        g.cz = cz;
        g.r2 = reach * reach;
        g.x0 = Math.max(minX - 24, Mth.floor(cx - reach));
        g.x1 = Math.min(maxX + 24, Mth.ceil(cx + reach));
        g.z0 = Math.max(minZ - 24, Mth.floor(cz - reach));
        g.z1 = Math.min(maxZ + 24, Mth.ceil(cz + reach));
        g.x0 = Math.min(g.x0, minX - 2);
        g.x1 = Math.max(g.x1, maxX + 2);
        g.z0 = Math.min(g.z0, minZ - 2);
        g.z1 = Math.max(g.z1, maxZ + 2);
        g.y0 = y;
        g.y1 = y + 32;
        g.sx = g.x0;
        building = g;
        gardenAt = Minecraft.getInstance().level.getGameTime();
    }

    private static final int[][] NB = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    // the preview's face shades: +x, -x, +y, -y, +z, -z
    private static final float[] FACE_S = {0.8f, 0.8f, 1.0f, 0.5f, 0.68f, 0.68f};

    private static void gardenStep(ClientLevel level) {
        Garden g = building;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), nb = new BlockPos.MutableBlockPos();
        for (int slice = 0; slice < 2 && g.sx <= g.x1; slice++) {
            int x = g.sx++;
            for (int z = g.z0; z <= g.z1; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                BlockState w = level.getBlockState(pos.set(x, g.y0, z));
                if (w.getFluidState().is(FluidTags.WATER) && !level.getFluidState(nb.set(x, g.y0 + 1, z)).is(FluidTags.WATER)
                        && kyoto(level, x, g.y0, z)) {
                    g.water.add(key(x, z));
                }
                double dc = (x + 0.5 - g.cx) * (x + 0.5 - g.cx) + (z + 0.5 - g.cz) * (z + 0.5 - g.cz);
                if (dc > g.r2) {
                    continue;
                }
                for (int y = g.y0 + 1; y <= g.y1; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (s.isAir() || !s.getFluidState().isEmpty() && !s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                        continue;
                    }
                    gardenBlock(level, g, s, x, y, z, pos, nb);
                }
            }
        }
        if (g.sx > g.x1) {
            finishGarden(level, g);
            building = null;
        }
    }

    private static boolean occludes(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && (s.canOcclude() || s.getBlock() instanceof LeavesBlock);
    }

    private static void gardenBlock(ClientLevel level, Garden g, BlockState s, int x, int y, int z, BlockPos.MutableBlockPos pos, BlockPos.MutableBlockPos nb) {
        Block block = s.getBlock();
        boolean leaf = block instanceof LeavesBlock;
        boolean full = leaf || s.canOcclude();
        AABB box;
        if (full) {
            box = null;
        } else {
            // plants (ferns, grass) don't show in a dark mirror; other small blocks by their outline
            if (s.getCollisionShape(level, pos).isEmpty()) {
                return;
            }
            var shape = s.getShape(level, pos);
            if (shape.isEmpty()) {
                return;
            }
            box = shape.bounds();
        }
        if (block == JujutsuBlocks.GOLD_LEAF.get()) {
            // (the glints: each side face open to the air)
            for (int f = 0; f < 6; f++) {
                int[] n = NB[f];
                if (n[1] != 0 || !level.getBlockState(nb.set(x + n[0], y + n[1], z + n[2])).isAir()) {
                    continue;
                }
                g.gold.add(new double[] {x + 0.5 + n[0] * 0.5, y + 0.5, z + 0.5 + n[2] * 0.5, n[0], n[1], n[2],
                        hash3(x, y, z, 78) - 0.5, hash3(x, y, z, 79) - 0.5, hash3(x, y, z, 77) - 0.5});
            }
        }
        if (block == JujutsuBlocks.TATAMI.get()) {
            g.tatami.add(new double[] {x + 0.5, y, z + 0.5});
        }
        int lk = leafKind(s);
        if (lk >= 0 && lk <= 3 && level.getBlockState(nb.set(x, y - 1, z)).isAir()) {
            g.maples.computeIfAbsent(key(Math.floorDiv(x, 6), Math.floorDiv(z, 6)), k -> new ArrayList<>()).add(new double[] {x + 0.5, y, z + 0.5, lk});
        }
        boolean pav = !leaf && full;
        int color = mirrorColor(level, s, pos);
        boolean glow = s.getLightEmission() > 0;
        double h = hash3(x, y, z, 11);
        float[] lin = {lin(color >> 16 & 255), lin(color >> 8 & 255), lin(color & 255)};
        for (int f = 0; f < 6; f++) {
            int[] n = NB[f];
            BlockState o = level.getBlockState(nb.set(x + n[0], y + n[1], z + n[2]));
            boolean open = !occludes(o);
            if (pav && open && o.isAir() && n[1] != -1 && g.pav.size() < 60000) {
                g.pav.add(new double[] {x + 0.5 + n[0] * 0.5, y + 0.5 + n[1] * 0.5, z + 0.5 + n[2] * 0.5, n[0], n[1], n[2]});
            }
            if (full && !open) {
                continue;
            }
            // an upward face never shows in the reflection (the ray leaving the water goes up and only meets faces from
            // below or the side)
            if (n[1] == 1 || g.faces.size() >= 160000) {
                continue;
            }
            float k = glow ? 0.9f : (float) ((leaf ? 0.82 + h * 0.36 : 0.9 + h * 0.2) * (0.55 + 0.45 * FACE_S[f]));
            float[] face = new float[16];
            double ax = box == null ? 0 : box.minX, ay = box == null ? 0 : box.minY, az = box == null ? 0 : box.minZ;
            double bx = box == null ? 1 : box.maxX, by = box == null ? 1 : box.maxY, bz = box == null ? 1 : box.maxZ;
            double[][] c = FACE_C[f];
            for (int i = 0; i < 4; i++) {
                face[i * 3] = (float) (x + (c[i][0] > 0 ? bx : ax));
                face[i * 3 + 1] = (float) (y + (c[i][1] > 0 ? by : ay));
                face[i * 3 + 2] = (float) (z + (c[i][2] > 0 ? bz : az));
            }
            face[12] = lin[0] * k;
            face[13] = lin[1] * k;
            face[14] = lin[2] * k;
            face[15] = f + (glow ? 8 : 0);
            g.faces.add(face);
        }
    }

    private static final double[][][] FACE_C = {
        {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
        {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
        {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
        {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
        {{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
        {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},
    };

    private static float lin(int c) {
        return (float) Math.pow(c / 255.0, 2.2);
    }

    // The colors the preview gives the garden's blocks; anything else by its map color.
    private static Map<Block, Integer> palette;

    private static int mirrorColor(ClientLevel level, BlockState s, BlockPos pos) {
        if (palette == null) {
            Map<Block, Integer> m = new IdentityHashMap<>();
            m.put(Blocks.STONE, 0x7a7672);
            m.put(Blocks.DIRT, 0x5a4030);
            m.put(Blocks.GRASS_BLOCK, 0x6a8a3a);
            m.put(Blocks.MOSS_BLOCK, 0x4e7a34);
            m.put(JujutsuBlocks.LITTER_RED.get(), 0xa8382a);
            m.put(JujutsuBlocks.LITTER_ORANGE.get(), 0xc06a2a);
            m.put(JujutsuBlocks.LITTER_GOLD.get(), 0xd0a838);
            m.put(JujutsuBlocks.MAPLE_TRUNK.get(), 0x4a3428);
            m.put(JujutsuBlocks.KYOTO_DARK_BARK.get(), 0x3a2c24);
            m.put(JujutsuBlocks.MAPLE_RED.get(), 0xc8301e);
            m.put(JujutsuBlocks.MAPLE_CRIMSON.get(), 0x9a1e18);
            m.put(JujutsuBlocks.MAPLE_ORANGE.get(), 0xe0682a);
            m.put(JujutsuBlocks.MAPLE_GOLD.get(), 0xe8a830);
            m.put(JujutsuBlocks.GINKGO_LEAVES.get(), 0xf0c838);
            m.put(JujutsuBlocks.JAPANESE_PINE_NEEDLES.get(), 0x2e4a2a);
            m.put(Blocks.BAMBOO_BLOCK, 0x7aa040);
            m.put(JujutsuBlocks.BAMBOO_LEAVES.get(), 0x5a8a34);
            m.put(Blocks.GRAVEL, 0x8a8478);
            m.put(ModBlocks.WHITE_GRAVEL.get(), 0xb0a68e);
            m.put(Blocks.POLISHED_ANDESITE, 0x8a8680);
            m.put(ModBlocks.STONE_LANTERN_LIGHT.get(), 0xffc070);
            m.put(JujutsuBlocks.KYOTO_LIGHT_WOOD.get(), 0xa07a50);
            m.put(JujutsuBlocks.FARM_WOOD_DARK.get(), 0x3a2a20);
            m.put(ModBlocks.SHOJI.get(), 0xe8e0cc);
            m.put(JujutsuBlocks.GOLD_LEAF.get(), 0xe8b830);
            m.put(JujutsuBlocks.DARK_GOLD.get(), 0xa87818);
            m.put(JujutsuBlocks.BARK_ROOF.get(), 0x3a3230);
            m.put(JujutsuBlocks.BARK_EAVE.get(), 0x2a2422);
            m.put(ModBlocks.PLASTER.get(), 0xd8ccb4);
            m.put(JujutsuBlocks.BRIDGE_WOOD.get(), 0x8a5a34);
            m.put(Blocks.ANDESITE, 0x8a8680);
            m.put(Blocks.MOSSY_COBBLESTONE, 0x5e7048);
            m.put(JujutsuBlocks.TATAMI.get(), 0xc8b878);
            palette = m;
        }
        Integer c = palette.get(s.getBlock());
        return c != null ? c : s.getMapColor(level, pos).col;
    }

    private static void finishGarden(ClientLevel level, Garden g) {
        // the pond: the water at its level joined to the cells the column scan found (under the bridge too)
        LongOpenHashSet pond = new LongOpenHashSet();
        ArrayDeque<Long> q = new ArrayDeque<>();
        long seed = key(g.cx, g.cz);
        for (long k : POND) {
            if (g.water.contains(k)) {
                seed = k;
                break;
            }
        }
        if (g.water.contains(seed)) {
            q.add(seed);
            pond.add(seed);
        }
        while (!q.isEmpty()) {
            long k = q.poll();
            int x = kx(k), z = kz(k);
            for (long n : new long[] {key(x + 1, z), key(x - 1, z), key(x, z + 1), key(x, z - 1)}) {
                if (g.water.contains(n) && pond.add(n)) {
                    q.add(n);
                }
            }
        }
        if (pond.size() < 200 || g.gold.size() < 12) {
            return;
        }
        POND.clear();
        POND.addAll(pond);
        pondY = g.y0;
        P = g.y0 + 0.8889;
        measurePond();
        gardenOn = true;
        gardenY = g.y0;
        gardenCx = g.cx;
        gardenCz = g.cz;
        gx0 = g.x0;
        gx1 = g.x1;
        gz0 = g.z0;
        gz1 = g.z1;
        // the surface quads, a little over the vanilla water (its top is 8/9 of the block)
        float[] pq = new float[pond.size() * 3];
        int i = 0;
        for (long k : pond) {
            pq[i++] = kx(k);
            pq[i++] = (float) P;
            pq[i++] = kz(k);
        }
        pondQuads = pq;
        if (MIRROR) {
            buildMirror(g);
        }
        GOLD.clear();
        GOLD.addAll(g.gold);
        // the faces that may get the water's light: those of the pavilion (near the gold) and anything right by the pond
        PAV.clear();
        double gx = 0, gz = 0;
        for (double[] o : g.gold) {
            gx += o[0];
            gz += o[2];
        }
        boolean hasGold = !g.gold.isEmpty();
        gx /= Math.max(1, g.gold.size());
        gz /= Math.max(1, g.gold.size());
        for (double[] f : g.pav) {
            if (hasGold && Math.abs(f[0] - gx) < 13 && Math.abs(f[2] - gz) < 13 && f[1] < P + 25) {
                PAV.add(f);
            }
        }
        wallAt = 0;
        // the tea house: the tatami away from the pavilion
        double tx = 0, tz = 0;
        int tn = 0;
        for (double[] t : g.tatami) {
            if (!hasGold || Math.hypot(t[0] - gx, t[2] - gz) > 14) {
                tx += t[0];
                tz += t[2];
                tn++;
            }
        }
        hasTea = tn > 0;
        if (hasTea) {
            teaX = tx / tn;
            teaZ = tz / tn;
        } else if (hasGold) {
            // no tea house in sight: the shore across from the pavilion
            teaX = pondCx - (gx - pondCx);
            teaZ = pondCz - (gz - pondCz);
            hasTea = true;
        }
        // the garden's maples to light at night: crowns grouped in cells of 6; the light on the ground, in the middle
        SPOTS.clear();
        double gr = pondR + 26;
        for (List<double[]> b : g.maples.values()) {
            List<double[]> in = new ArrayList<>();
            double sx = 0, sz = 0;
            for (double[] o : b) {
                if (Math.hypot(o[0] - g.cx, o[2] - g.cz) <= gr) {
                    in.add(o);
                    sx += o[0];
                    sz += o[2];
                }
            }
            if (in.size() < 8) {
                continue;
            }
            double x = sx / in.size(), z = sz / in.size();
            int gy = top(level, x, z);
            if (gy < -900 || isWater(level, Mth.floor(x), gy, Mth.floor(z))) {
                continue;
            }
            Spot s = new Spot();
            s.x = x;
            s.y = gy + 1.1;
            s.z = z;
            s.b.addAll(in);
            SPOTS.add(s);
        }
        makeKoi();
        placeShishi(level);
        for (Leaf L : LEAF) {
            if (rnd() < 0.18) {
                spawnLeaf(level, L, true);
            }
        }
    }

    private static void buildMirror(Garden g) {
        if (mirror != null) {
            mirror.close();
            mirror = null;
        }
        if (g.faces.isEmpty()) {
            return;
        }
        mirX = Math.floor(g.cx);
        mirY = g.y0;
        mirZ = Math.floor(g.cz);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (float[] f : g.faces) {
            int fi = (int) f[15] & 7;
            boolean glow = f[15] >= 8;
            int[] n = NB[fi];
            for (int i = 0; i < 4; i++) {
                b.addVertex((float) (f[i * 3] - mirX), (float) (f[i * 3 + 1] - mirY), (float) (f[i * 3 + 2] - mirZ))
                        .setColor(f[12], f[13], f[14], glow ? 0f : 1f).setNormal(n[0], n[1], n[2]);
            }
        }
        MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        mirror = new VertexBuffer(VertexBuffer.Usage.STATIC);
        mirror.bind();
        mirror.upload(mesh);
        VertexBuffer.unbind();
    }

    private static void dropGarden() {
        gardenOn = false;
        gardenY = Integer.MIN_VALUE;
        GOLD.clear();
        PAV.clear();
        WALL.clear();
        SPOTS.clear();
        KOI.clear();
        shishi = null;
        hasTea = false;
        pondQuads = new float[0];
        if (mirror != null) {
            mirror.close();
            mirror = null;
        }
    }

    // Light coming back from the pond: leaves the water along the sun's mirror (-sx, sy, -sz); reaches a face that looks
    // at it, and only if the spot it left from is pond.
    private static void pickWalls() {
        WALL.clear();
        double sx = SUN[0], sy = SUN[1], sz = SUN[2];
        if (sy < 0.08) {
            return;
        }
        for (double[] f : PAV) {
            double cos = f[3] * sx - f[4] * sy + f[5] * sz;
            if (f[4] == 1 || cos <= 0.05) {
                continue;
            }
            double L = (f[1] - P) / sy;
            if (L <= 0) {
                continue;
            }
            if (!POND.contains(key(f[0] + sx * L, f[2] + sz * L))) {
                continue;
            }
            WALL.add(new double[] {f[0], f[1], f[2], f[3], f[4], f[5], L, cos});
            if (WALL.size() > 900) {
                break;
            }
        }
    }

    private static void reset(boolean all) {
        strength = 0;
        for (Leaf L : LEAF) {
            L.st = -1;
        }
        crows = null;
        // (back in the biome, the first pass reads the place again quickly: the old one may be far away)
        firstPass = true;
        scanRow = -SCAN - 1;
        next = new Scan();
        if (leafLoop != null) {
            leafLoop.end();
            leafLoop = null;
        }
        if (all) {
            cur = new Scan();
            next = new Scan();
            scanRow = -SCAN - 1;
            firstPass = true;
            POND.clear();
            FLOW.clear();
            P = -999;
            pondY = Integer.MIN_VALUE;
            building = null;
            gardenAt = -100000;
            dropGarden();
        }
    }

    // ---------------------------------------------------------------- leaves

    private static void leafInit(Leaf p, double sx, double sy, double sz, int kind) {
        boolean bam = kind == BAMBOO;
        float[] c = bam && rnd() < 0.4 ? new float[] {0.78f, 0.74f, 0.36f} : PALC[kind];
        float j = (float) (0.85 + rnd() * 0.3);
        boolean brown = !bam && kind != GINKGO && rnd() < 0.12;
        p.x = sx + (rnd() - 0.5);
        p.y = sy - 0.15;
        p.z = sz + (rnd() - 0.5);
        p.vx = p.vz = 0;
        p.st = 0;
        p.age = 0;
        p.ph = rnd() * Mth.TWO_PI;
        p.phr = kind == GINKGO ? 3.2 + rnd() * 1.2 : 2.2 + rnd() * 0.9;
        p.amp = 0.55 + rnd() * 0.5;
        p.hd = rnd() * Mth.TWO_PI;
        p.sp = rnd() * Mth.TWO_PI;
        p.g = kind == GINKGO;
        p.bam = bam;
        p.sz = bam ? 0.2 + rnd() * 0.05 : kind == GINKGO ? 0.14 + rnd() * 0.03 : 0.16 + rnd() * 0.05;
        if (brown) {
            p.r = 0.55f * j;
            p.gg = 0.25f * j;
            p.b = 0.12f * j;
        } else {
            p.r = c[0] * j;
            p.gg = c[1] * j;
            p.b = c[2] * j;
        }
    }

    private static boolean spawnLeaf(ClientLevel level, Leaf p, boolean water) {
        Vec3 F = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double R = 30, u = rnd();
        if ((water || u < 0.1) && flowNear.length > 0 && (!water || rnd() < 0.4)) {
            long k = flowNear[(int) (rnd() * flowNear.length)];
            if (floatAt(p, kx(k) + 0.3 + rnd() * 0.4, kz(k) + 0.3 + rnd() * 0.4)) {
                p.age = water ? rnd() * 20 : 0;
                return true;
            }
        }
        if ((water || u > 0.92) && pondNear.length > 0) {
            long k = pondNear[(int) (rnd() * pondNear.length)];
            if (floatAt(p, kx(k) + rnd(), kz(k) + rnd())) {
                p.age = water ? rnd() * 60 : 0;
                return true;
            }
        }
        List<double[]> src = cur.src;
        for (int k = 0; k < 10 && !src.isEmpty(); k++) {
            double[] s = src.get((int) (rnd() * src.size()));
            if (Math.abs(s[0] - F.x) > R || Math.abs(s[2] - F.z) > R) {
                continue;
            }
            if (s[3] == BAMBOO && rnd() > 0.3) {
                continue;
            }
            leafInit(p, s[0], s[1], s[2], (int) s[3]);
            return true;
        }
        p.st = -1;
        return false;
    }

    // A leaf on some water (so the pond and the stream start with leaves).
    private static boolean floatAt(Leaf p, double x, double z) {
        long k = key(x, z);
        int kind = new int[] {0, 1, 2, 3, 0}[(int) (rnd() * 5)];
        if (POND.contains(k)) {
            leafInit(p, x, 0, z, kind);
            p.x = x;
            p.z = z;
            p.y = P + 0.02;
            p.st = 2;
            return true;
        }
        double[] f = FLOW.get(k);
        if (f != null) {
            leafInit(p, x, 0, z, kind);
            p.x = x;
            p.z = z;
            p.y = f[2] + 0.9;
            p.st = 3;
            return true;
        }
        return false;
    }

    // Where a strong gust passes, a leaf on the ground lifts and runs.
    private static void gustLitter(double t, double dt) {
        if (LITTER_NEAR.isEmpty()) {
            return;
        }
        int tries = rnd() < dt * 60 ? 3 : 0;
        for (int k = 0; k < tries; k++) {
            double[] L = LITTER_NEAR.get((int) (rnd() * LITTER_NEAR.size()));
            if (wind(L[0], L[2], t)[2] < 0.5) {
                continue;
            }
            Leaf p = null;
            for (Leaf q : LEAF) {
                if (q.st == 1 || q.st == -1) {
                    p = q;
                    break;
                }
            }
            if (p == null) {
                return;
            }
            leafInit(p, L[0], L[1] + 0.15, L[2], (int) L[3]);
            p.y = L[1];
            p.gy = L[1];
            p.st = 4;
            p.age = 0;
            p.ph = rnd() * 3;
        }
    }

    private static void stepLeaves(ClientLevel level, Vec3 F, double dt, double t) {
        double RR = 34;
        gustLitter(t, dt);
        int waiting = 0;
        for (Leaf p : LEAF) {
            if (p.st == -1) {
                waiting++;
            }
        }
        boolean fresh = waiting > LEAVES * 0.7;
        for (Leaf p : LEAF) {
            if (p.st == -1) {
                // (at the start, most leaves still waiting: spread them down the fall, or they all leave the crowns at once)
                if (rnd() < dt * 3 && spawnLeaf(level, p, false) && p.st == 0 && fresh) {
                    p.y -= rnd() * 10;
                }
                continue;
            }
            p.age += dt;
            if (p.st == 0) {
                // pendulum: slides along its heading, pitches up at the ends (rises a little), falls more mid-swing
                double[] w = wind(p.x, p.z, t);
                p.vx += (w[0] * 0.85 - p.vx) * Math.min(1, dt * 1.6);
                p.vz += (w[1] * 0.85 - p.vz) * Math.min(1, dt * 1.6);
                p.ph += p.phr * dt;
                p.hd += (rnd() - 0.5) * dt * 0.6;
                p.sp += dt * (p.bam ? 11 : 0.4);
                // a bamboo leaf is long and narrow: spins fast round its length (autorotation) and glides sideways
                double sv = p.bam ? 0.7 : p.amp * Math.cos(p.ph), vy = p.bam ? -0.95 : -0.62 + 0.3 * p.phr * Math.sin(2 * p.ph) * 0.5;
                p.x += (p.vx + Math.cos(p.hd) * sv) * dt;
                p.z += (p.vz + Math.sin(p.hd) * sv) * dt;
                p.y += vy * dt;
                int g = top(level, p.x, p.z);
                if (g < -900 || Math.abs(p.x - F.x) > RR || Math.abs(p.z - F.z) > RR) {
                    spawnLeaf(level, p, false);
                    continue;
                }
                if (p.y <= g + 1.02) {
                    if (isWater(level, Mth.floor(p.x), g, Mth.floor(p.z))) {
                        if (POND.contains(key(p.x, p.z)) && g == pondY) {
                            p.st = 2;
                            p.y = P + 0.02;
                        } else {
                            p.st = 3;
                            p.y = g + 0.9;
                        }
                    } else {
                        p.st = 1;
                        p.y = g + 1.02;
                    }
                    p.age = 0;
                    p.vx = p.vz = 0;
                }
            } else if (p.st == 1) {
                if (p.age > 10) {
                    spawnLeaf(level, p, false);
                }
            } else if (p.st == 4) {
                // a dry leaf dragged on the ground by the gust: runs in little hops, flips now and then, stops when the
                // wind drops
                double[] w = wind(p.x, p.z, t);
                p.vx += (w[0] * 0.9 - p.vx) * dt * 3;
                p.vz += (w[1] * 0.9 - p.vz) * dt * 3;
                double wg = w[2];
                double nx = p.x + p.vx * dt, nz = p.z + p.vz * dt;
                int g = top(level, nx, nz);
                if (g < -900 || Math.abs(g + 1.02 - p.gy) > 1.1 || isWater(level, Mth.floor(nx), g, Mth.floor(nz))) {
                    p.st = 1;
                    p.age = 4;
                    continue;
                }
                p.x = nx;
                p.z = nz;
                p.gy += (g + 1.02 - p.gy) * Math.min(1, dt * 8);
                p.ph += dt * (6 + Math.hypot(p.vx, p.vz) * 4);
                p.y = p.gy + Math.abs(Math.sin(p.ph)) * 0.12 * wg;
                p.sp += dt * (p.vx - p.vz) * 2;
                if (wg < 0.35 || p.age > 9) {
                    p.st = 1;
                    p.y = p.gy;
                    p.age = 3;
                }
            } else if (p.st == 2) {
                // floats: goes with ~5% of the wind; doesn't climb the shore (runs aground and gathers)
                double[] w = wind(p.x, p.z, t);
                p.vx += (w[0] * 0.09 - p.vx) * dt * 0.8;
                p.vz += (w[1] * 0.09 - p.vz) * dt * 0.8;
                double nx = p.x + p.vx * dt, nz = p.z + p.vz * dt;
                if (POND.contains(key(nx + Math.signum(p.vx) * 0.15, nz + Math.signum(p.vz) * 0.15))) {
                    p.x = nx;
                    p.z = nz;
                } else {
                    p.vx *= -0.1;
                    p.vz *= -0.1;
                }
                p.sp += (p.vx - p.vz) * dt * 0.8;
                if (p.age > 140 || Math.abs(p.x - F.x) > RR + 20 || Math.abs(p.z - F.z) > RR + 20 || !POND.contains(key(p.x, p.z))) {
                    spawnLeaf(level, p, false);
                }
            } else if (p.st == 3) {
                // stream: a current of ~1.2 blocks/s downhill; catches on a stone and slides to one side
                double[] f = FLOW.get(key(p.x, p.z));
                if (f == null) {
                    p.st = 1;
                    p.age = 6;
                    continue;
                }
                double sp = 1.2 * (0.7 + 0.6 * vnoise(p.x * 0.5, p.z * 0.5));
                p.vx += (f[0] * sp - p.vx) * dt * 2;
                p.vz += (f[1] * sp - p.vz) * dt * 2;
                double nx = p.x + p.vx * dt, nz = p.z + p.vz * dt;
                if (!FLOW.containsKey(key(nx, nz))) {
                    double s = p.sp % 2 > 1 ? 1 : -1, ax = -p.vz * s, az = p.vx * s;
                    if (FLOW.containsKey(key(p.x + ax * dt * 2, p.z + az * dt * 2))) {
                        nx = p.x + ax * dt * 2;
                        nz = p.z + az * dt * 2;
                    } else {
                        nx = p.x;
                        nz = p.z;
                        p.vx *= 0.3;
                        p.vz *= 0.3;
                        p.sp += dt * 3;
                    }
                }
                p.x = nx;
                p.z = nz;
                p.sp += dt * 1.1;
                double[] wy = FLOW.get(key(p.x, p.z));
                if (wy != null) {
                    p.y += (wy[2] + 0.9 - p.y) * Math.min(1, dt * 6);
                }
                if (p.age > 50 || Math.abs(p.x - F.x) > RR || Math.abs(p.z - F.z) > RR) {
                    spawnLeaf(level, p, false);
                }
            }
        }
    }

    // A ginkgo shower: a tree near the player lets go of many leaves at once in a gust.
    private static void shower(Vec3 F, double t) {
        List<double[]> gk = cur.ginkgos;
        if (gk.isEmpty()) {
            return;
        }
        double[] best = null;
        double bd = 1e9;
        for (int k = 0; k < 40; k++) {
            double[] g = gk.get((int) (rnd() * gk.size()));
            double d = Math.hypot(g[0] - F.x, g[2] - F.z) + rnd() * 10;
            if (d < bd && d > 5) {
                bd = d;
                best = g;
            }
        }
        if (best == null || bd > 40) {
            return;
        }
        List<double[]> mine = new ArrayList<>();
        for (double[] g : gk) {
            if (Math.abs(g[0] - best[0]) < 5 && Math.abs(g[2] - best[2]) < 5) {
                mine.add(g);
            }
        }
        int n = 0;
        for (Leaf p : LEAF) {
            if (n >= 140) {
                break;
            }
            if (p.st == 0 && p.age < 3) {
                continue;
            }
            double[] g = mine.get((int) (rnd() * mine.size()));
            leafInit(p, g[0], g[1], g[2], GINKGO);
            p.y -= rnd() * 0.5;
            double[] w = wind(g[0], g[2], t);
            p.vx = w[0] * 1.3;
            p.vz = w[1] * 1.3;
            n++;
        }
    }

    // The leaf's outline in its plane (u = tip, v = side): a five-lobed maple, a ginkgo fan, a bamboo lance.
    private static final double[][] MAPLE;
    private static final double[][] GINK;
    private static final double[][] BAMB = {{-1.2, 0}, {-0.9, -0.11}, {-0.3, -0.17}, {0.4, -0.14}, {1.0, -0.05}, {1.25, 0}, {1.0, 0.05},
        {0.4, 0.14}, {-0.3, 0.17}, {-0.9, 0.11}, {-1.2, 0}};

    static {
        double[][] a = {{-150, 0.3}, {-105, 0.78}, {-80, 0.4}, {-52, 0.98}, {-26, 0.45}, {0, 1.12}, {26, 0.45}, {52, 0.98}, {80, 0.4}, {105, 0.78}, {150, 0.3}};
        MAPLE = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            MAPLE[i] = new double[] {Math.cos(Math.toRadians(a[i][0])) * a[i][1], Math.sin(Math.toRadians(a[i][0])) * a[i][1]};
        }
        List<double[]> o = new ArrayList<>();
        for (int d = -62; d <= 62; d += 12) {
            double r = Math.abs(d) < 7 ? 0.82 : 1.0;
            o.add(new double[] {Math.cos(Math.toRadians(d)) * r, Math.sin(Math.toRadians(d)) * r});
        }
        GINK = o.toArray(new double[0][]);
    }

    private static final double[] LU = new double[3], LV = new double[3], LN = new double[3], LK = new double[3];

    private static void renderLeaves(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        double RR = 34, n = night;
        for (Leaf p : LEAF) {
            if (p.st < 0) {
                continue;
            }
            double d = Math.sqrt((p.x - cam.x) * (p.x - cam.x) + (p.y - cam.y) * (p.y - cam.y) + (p.z - cam.z) * (p.z - cam.z));
            if (d > RR || d < 0.05) {
                continue;
            }
            LU[0] = Math.cos(p.sp);
            LU[1] = 0;
            LU[2] = Math.sin(p.sp);
            LV[0] = -Math.sin(p.sp);
            LV[1] = 0;
            LV[2] = Math.cos(p.sp);
            LN[0] = 0;
            LN[1] = 1;
            LN[2] = 0;
            if (p.bam && p.st == 0) {
                // the long axis along the heading; the side turns round it
                LU[0] = Math.cos(p.hd);
                LU[2] = Math.sin(p.hd);
                double hx = -Math.sin(p.hd), hz = Math.cos(p.hd), cs = Math.cos(p.sp), sn = Math.sin(p.sp);
                LV[0] = hx * cs;
                LV[1] = sn;
                LV[2] = hz * cs;
                LN[0] = -hx * sn;
                LN[1] = cs;
                LN[2] = -hz * sn;
            } else if (p.st == 0) {
                // tilts in the swing: turns round the lying axis across its heading
                LK[0] = -Math.sin(p.hd);
                LK[1] = 0;
                LK[2] = Math.cos(p.hd);
                double a = 0.85 * Math.sin(p.ph);
                rot(LU, LK, a);
                rot(LV, LK, a);
                rot(LN, LK, a);
            }
            double s = p.sz, lift = p.st == 2 ? Math.sin(clock * 1.3 + p.ph) * 0.01 : 0;
            // light: the side facing the sun bright; against the sun the leaf lights up (light through it)
            double vx = (p.x - cam.x) / d, vy = (p.y - cam.y) / d, vz = (p.z - cam.z) / d;
            double lam = Math.abs(LN[0] * SUN[0] + LN[1] * SUN[1] + LN[2] * SUN[2]);
            double back = Math.pow(Math.max(0, vx * SUN[0] + vy * SUN[1] + vz * SUN[2]), 4) * (1 - n) * clamp01(SUN[1] * 5);
            double k = Mth.lerp(n, 0.6 + 0.4 * lam, 0.32);
            if (n > 0.01) {
                for (Spot S : SPOTS) {
                    double dd = (p.x - S.x) * (p.x - S.x) + (p.z - S.z) * (p.z - S.z);
                    if (dd < 64) {
                        k += 0.6 * (1 - dd / 64) * n;
                    }
                }
            }
            double fd = p.st == 4 ? 1 : p.st == 1 ? clamp01((10 - p.age) / 3) : p.st == 2 ? clamp01((140 - p.age) / 6) : p.st == 3 ? clamp01((50 - p.age) / 4) : 1;
            float r = (float) Math.min(1, p.r * k * (1 + back * 0.9)), g = (float) Math.min(1, p.gg * k * (1 + back * 0.35)), b = (float) Math.min(1, p.b * k);
            float a = (float) (0.96 * fd * clamp01(1.25 - d / RR)) * fade;
            if (a < 0.01f) {
                continue;
            }
            double[][] shape = p.bam ? BAMB : p.g ? GINK : MAPLE;
            double ox = p.g ? -0.55 : 0;
            double c0u = (p.g ? 0 : 0.05) + ox;
            double c0x = p.x + LU[0] * c0u * s, c0y = p.y + lift + LU[1] * c0u * s, c0z = p.z + LU[2] * c0u * s;
            for (int i = 0; i + 1 < shape.length; i++) {
                double au = shape[i][0] + ox, av = shape[i][1], bu = shape[i + 1][0] + ox, bv = shape[i + 1][1];
                double ax = p.x + (LU[0] * au + LV[0] * av) * s, ay = p.y + lift + (LU[1] * au + LV[1] * av) * s, az = p.z + (LU[2] * au + LV[2] * av) * s;
                double bx = p.x + (LU[0] * bu + LV[0] * bv) * s, by = p.y + lift + (LU[1] * bu + LV[1] * bv) * s, bz = p.z + (LU[2] * bu + LV[2] * bv) * s;
                v(out, last, cam, c0x, c0y, c0z, r * 0.85f, g * 0.85f, b * 0.85f, a);
                v(out, last, cam, ax, ay, az, r, g, b, a);
                v(out, last, cam, bx, by, bz, r, g, b, a);
                v(out, last, cam, bx, by, bz, r, g, b, a);
            }
            if (p.bam) {
                continue;
            }
            // the stalk: a narrow quad out of the base (thicker than a line)
            double[][] st = p.g ? new double[][] {{0, -0.05}, {0, 0.05}, {-0.45, 0.04}, {-0.45, -0.04}}
                    : new double[][] {{-0.25, -0.05}, {-0.25, 0.05}, {-0.75, 0.04}, {-0.75, -0.04}};
            for (double[] q : st) {
                double u = q[0] + ox;
                v(out, last, cam, p.x + (LU[0] * u + LV[0] * q[1]) * s, p.y + lift + (LU[1] * u + LV[1] * q[1]) * s, p.z + (LU[2] * u + LV[2] * q[1]) * s,
                        r * 0.6f, g * 0.55f, b * 0.5f, a);
            }
        }
    }

    // ---------------------------------------------------------------- koi

    // kohaku, sanke, showa, ogon, orange chagoi, asagi: base, spots, how much spot, ink, white, spotted sides
    private static final float[][] KOI_BASE = {{0.97f, 0.95f, 0.9f}, {0.97f, 0.95f, 0.9f}, {0.12f, 0.11f, 0.12f}, {1, 0.78f, 0.32f}, {0.95f, 0.5f, 0.16f}, {0.55f, 0.62f, 0.7f}};
    private static final float[][] KOI_SPOT = {{0.9f, 0.22f, 0.1f}, {0.88f, 0.2f, 0.1f}, {0.92f, 0.25f, 0.1f}, {1, 0.9f, 0.5f}, {1, 0.62f, 0.25f}, {0.9f, 0.45f, 0.2f}};
    private static final double[] KOI_K = {0.5, 0.45, 0.5, 0.3, 0.3, 0.35};
    private static final double[] KOI_INK = {0, 0.18, 0, 0, 0, 0};
    private static final double[] KOI_WHITE = {0, 0, 0.3, 0, 0, 0};
    private static final boolean[] KOI_SIDE = {false, false, false, false, false, true};

    private static void makeKoi() {
        KOI.clear();
        if (POND.isEmpty()) {
            return;
        }
        long[] cells = POND.toLongArray();
        for (int i = 0; i < 14; i++) {
            long k = cells[(int) (rnd() * cells.length)];
            Koi f = new Koi();
            f.x = kx(k) + 0.5;
            f.z = kz(k) + 0.5;
            f.d = 0.5 + rnd() * 0.4;
            f.td = 0.6;
            f.hd = rnd() * Mth.TWO_PI;
            f.sp = f.tsp = 0.4;
            f.L = 0.9 + rnd() * 0.6;
            f.pat = i % KOI_BASE.length;
            f.id = i * 7.3 + rnd();
            f.ph = rnd() * Mth.TWO_PI;
            KOI.add(f);
        }
    }

    private static boolean pondOk(double x, double z) {
        return POND.contains(key(x, z));
    }

    private static void stepKoi(Vec3 c, double dt) {
        if (KOI.isEmpty()) {
            return;
        }
        // someone standing at the edge: the pond spot nearest them
        double[] bait = null;
        if (c.y > P && c.y < P + 4) {
            int bd = 81;
            for (int a = -9; a <= 9; a++) {
                for (int b = -9; b <= 9; b++) {
                    int x = Mth.floor(c.x) + a, z = Mth.floor(c.z) + b, d = a * a + b * b;
                    if (d < bd && pondOk(x, z) && pondOk(x + 1, z) && pondOk(x - 1, z) && pondOk(x, z + 1) && pondOk(x, z - 1)) {
                        bd = d;
                        bait = new double[] {x + 0.5, z + 0.5};
                    }
                }
            }
        }
        for (Koi f : KOI) {
            f.wt -= dt;
            double want = f.hd + f.th;
            if (f.wt < 0) {
                f.wt = 2 + rnd() * 4;
                f.th = (rnd() - 0.5) * 1.6;
                f.tsp = 0.25 + rnd() * 0.45;
                f.td = 0.35 + rnd() * 0.55;
            }
            if (bait != null) {
                double dx = bait[0] - f.x, dz = bait[1] - f.z, d = Math.hypot(dx, dz);
                if (d < 16) {
                    want = Math.atan2(dz, dx) + (d < 2.2 ? 1.2 : 0);
                    f.tsp = d < 2.2 ? 0.25 : 0.8;
                    f.td = d < 3.5 ? 0.12 : 0.4;
                }
            }
            for (Koi o : KOI) {
                if (o == f) {
                    continue;
                }
                double dx = f.x - o.x, dz = f.z - o.z, d = Math.hypot(dx, dz);
                if (d < 0.8 && d > 0) {
                    want += Math.sin(Math.atan2(dz, dx) - f.hd) * 0.8;
                }
            }
            // the shore: looks 1.2 and 2.2 blocks ahead; land there, turns to the side that has water
            if (!pondOk(f.x + Math.cos(f.hd) * 1.2, f.z + Math.sin(f.hd) * 1.2) || !pondOk(f.x + Math.cos(f.hd) * 2.2, f.z + Math.sin(f.hd) * 2.2)) {
                for (double da : new double[] {0.6, -0.6, 1.2, -1.2, 2, -2, 3}) {
                    if (pondOk(f.x + Math.cos(f.hd + da) * 1.6, f.z + Math.sin(f.hd + da) * 1.6)) {
                        want = f.hd + da * 1.5;
                        break;
                    }
                }
                f.tsp = Math.min(f.tsp, 0.35);
            }
            double dh = ((want - f.hd + Math.PI) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) - Math.PI;
            double tr = Math.max(-1.4, Math.min(1.4, dh * 1.5));
            f.turn += (tr - f.turn) * Math.min(1, dt * 3);
            f.hd += f.turn * dt;
            f.sp += (f.tsp - f.sp) * Math.min(1, dt * 0.9);
            double nx = f.x + Math.cos(f.hd) * f.sp * dt, nz = f.z + Math.sin(f.hd) * f.sp * dt;
            if (pondOk(nx, nz)) {
                f.x = nx;
                f.z = nz;
            } else {
                f.hd += Math.PI * dt * 2;
            }
            f.d += (f.td - f.d) * Math.min(1, dt * 0.6);
            f.ph += dt * (3 + f.sp * 9 + Math.abs(f.turn) * 3);
            // pushes a floating leaf near its head when swimming shallow
            if (f.d < 0.45) {
                for (Leaf p : LEAF) {
                    if (p.st == 2) {
                        double dx = p.x - f.x, dz = p.z - f.z;
                        if (dx * dx + dz * dz < 0.5) {
                            p.vx += Math.cos(f.hd) * f.sp * 0.6 * dt * 3;
                            p.vz += Math.sin(f.hd) * f.sp * 0.6 * dt * 3;
                        }
                    }
                }
            }
        }
    }

    private static final double[] WID = {0.08, 0.15, 0.18, 0.175, 0.15, 0.115, 0.085, 0.06, 0.045, 0.034, 0.03};
    private static final double[][] SPINE = new double[11][2];
    private static final float[] KC = new float[4];

    private static void koiColor(Koi f, double u, boolean edge, double mixw, double vis, float[] wc, float[] o) {
        int pat = f.pat;
        float[] c = KOI_BASE[pat];
        double m = vnoise(u * 4.5 + f.id * 3.1, f.id * 1.7 + (edge ? 0.6 : 0));
        if (m > 1 - KOI_K[pat] && u < 0.9) {
            c = KOI_SPOT[pat];
        }
        if (KOI_INK[pat] > 0 && vnoise(u * 9 + f.id, f.id * 2.3) > 0.78) {
            c = new float[] {0.08f, 0.08f, 0.09f};
        }
        if (KOI_WHITE[pat] > 0 && vnoise(u * 6 + f.id * 2, f.id) > 0.68) {
            c = new float[] {0.96f, 0.94f, 0.9f};
        }
        if (KOI_SIDE[pat] && edge) {
            c = KOI_SPOT[pat];
        }
        float e = edge ? 0.85f : 1f;
        o[0] = (float) (c[0] * e + (wc[0] - c[0] * e) * mixw);
        o[1] = (float) (c[1] * e + (wc[1] - c[1] * e) * mixw);
        o[2] = (float) (c[2] * e + (wc[2] - c[2] * e) * mixw);
        o[3] = (float) (vis * 0.95);
    }

    // Under the water: each point is drawn where the camera's ray to it crosses the surface (the same spot on the
    // screen, at the surface's depth: hidden by what stands in front of the water, never seen through the ground).
    private static void vu(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        double ps = P + 0.004, t = (ps - cam.y) / (y - cam.y);
        v(out, last, cam, cam.x + (x - cam.x) * t, ps, cam.z + (z - cam.z) * t, r, g, b, a);
    }

    private static void renderKoi(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        if (cam.y <= P + 0.05) {
            return;
        }
        double n = night;
        float[] wc = {(float) Mth.lerp(n, 0.1, 0.04), (float) Mth.lerp(n, 0.22, 0.07), (float) Mth.lerp(n, 0.2, 0.09)};
        float[] ca = new float[4], cb = new float[4], ea = new float[4], eb = new float[4];
        for (Koi f : KOI) {
            double dist = Math.sqrt((f.x - cam.x) * (f.x - cam.x) + (P - cam.y) * (P - cam.y) + (f.z - cam.z) * (f.z - cam.z));
            if (dist > 40) {
                continue;
            }
            // the look's Fresnel: grazing = more mirror, less fish
            double cz = Math.abs(cam.y - P) / dist, Fr = 0.02 + 0.98 * Math.pow(1 - cz, 5);
            double R = Mth.lerp(n, 0.42, 0.5) + 0.58 * Fr * Mth.lerp(n, 0.58, 0.5);
            double vis = clamp01((1 - R) * 1.7) * (1 - f.d * 0.55) * Mth.lerp(n, 1, 0.35) * fade;
            if (vis < 0.02) {
                continue;
            }
            double y = P - f.d, L = f.L, hx = Math.cos(f.hd), hz = Math.sin(f.hd), px = -hz, pz = hx;
            double amp = (0.5 + f.sp) * L, curv = f.turn / Math.max(0.2, f.sp);
            for (int i = 0; i <= 10; i++) {
                double u = i / 10.0, s = u * L;
                double lat = amp * (0.015 + 0.11 * u * u) * Math.sin(f.ph - 5.5 * u) - curv * s * s * 0.35;
                SPINE[i][0] = f.x - hx * s + px * lat;
                SPINE[i][1] = f.z - hz * s + pz * lat;
            }
            double mixw = 0.2 + f.d * 0.55;
            for (int i = 0; i < 10; i++) {
                double[] a = SPINE[i], b = SPINE[i + 1];
                double dx = b[0] - a[0], dz = b[1] - a[1], l = Math.max(1e-6, Math.hypot(dx, dz)), nx = -dz / l, nz = dx / l;
                double w0 = WID[i] * L, w1 = WID[i + 1] * L;
                koiColor(f, i / 10.0, false, mixw, vis, wc, ca);
                koiColor(f, (i + 1) / 10.0, false, mixw, vis, wc, cb);
                koiColor(f, i / 10.0, true, mixw, vis, wc, ea);
                koiColor(f, (i + 1) / 10.0, true, mixw, vis, wc, eb);
                for (int sd = 1; sd >= -1; sd -= 2) {
                    vu(out, last, cam, a[0], y, a[1], ca[0], ca[1], ca[2], ca[3]);
                    vu(out, last, cam, b[0], y, b[1], cb[0], cb[1], cb[2], cb[3]);
                    vu(out, last, cam, b[0] + nx * w1 * sd, y, b[1] + nz * w1 * sd, eb[0], eb[1], eb[2], eb[3]);
                    vu(out, last, cam, a[0] + nx * w0 * sd, y, a[1] + nz * w0 * sd, ea[0], ea[1], ea[2], ea[3]);
                }
            }
            // the fan tail and the pectoral fins (half see-through)
            double[] t0 = SPINE[10], t1 = SPINE[9];
            double dx = t0[0] - t1[0], dz = t0[1] - t1[1], l = Math.max(1e-6, Math.hypot(dx, dz)), ux = dx / l, uz = dz / l;
            koiColor(f, 0.95, true, mixw, vis, wc, KC);
            float fa = KC[3] * 0.6f;
            double tw = Math.sin(f.ph - 6) * 0.06 * L;
            vu(out, last, cam, t0[0], y, t0[1], KC[0], KC[1], KC[2], fa);
            vu(out, last, cam, t0[0] + ux * 0.2 * L - uz * (0.13 * L + tw), y, t0[1] + uz * 0.2 * L + ux * (0.13 * L + tw), KC[0], KC[1], KC[2], fa);
            vu(out, last, cam, t0[0] + ux * 0.13 * L, y, t0[1] + uz * 0.13 * L, KC[0], KC[1], KC[2], fa);
            vu(out, last, cam, t0[0] + ux * 0.2 * L + uz * (0.13 * L - tw), y, t0[1] + uz * 0.2 * L - ux * (0.13 * L - tw), KC[0], KC[1], KC[2], fa);
            double[] pf = SPINE[2];
            double fl = 0.11 * L * (0.8 + 0.2 * Math.sin(f.ph * 0.7));
            for (int sd = 1; sd >= -1; sd -= 2) {
                double bx = pf[0] + px * sd * WID[2] * L, bz = pf[1] + pz * sd * WID[2] * L;
                vu(out, last, cam, bx, y, bz, KC[0], KC[1], KC[2], fa);
                vu(out, last, cam, bx + px * sd * fl - hx * fl * 0.6, y, bz + pz * sd * fl - hz * fl * 0.6, KC[0], KC[1], KC[2], fa);
                vu(out, last, cam, bx - hx * fl * 0.9, y, bz - hz * fl * 0.9, KC[0], KC[1], KC[2], fa);
                vu(out, last, cam, bx - hx * 0.05, y, bz - hz * 0.05, KC[0], KC[1], KC[2], fa);
            }
        }
    }

    // ---------------------------------------------------------------- the pavilion: gold glints and water light

    private static void renderGold(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float k0) {
        double sx = SUN[0], sy = SUN[1], sz = SUN[2];
        for (double[] g : GOLD) {
            double nx = g[3] + g[6] * 0.4, ny = g[4] + g[7] * 0.4, nz = g[5] + g[8] * 0.4, ln = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= ln;
            ny /= ln;
            nz /= ln;
            double dn = sx * nx + sy * ny + sz * nz;
            if (dn <= 0) {
                continue;
            }
            double rx = 2 * dn * nx - sx, ry = 2 * dn * ny - sy, rz = 2 * dn * nz - sz;
            double dx = cam.x - g[0], dy = cam.y - g[1], dz = cam.z - g[2], d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 0.5 || d > 160) {
                continue;
            }
            double k = Math.pow(Math.max(0, (rx * dx + ry * dy + rz * dz) / d), 40);
            if (k < 0.03) {
                continue;
            }
            double s = (0.18 + 0.45 * k) * Math.max(1, d / 30);
            disc(out, last, cam, g[0] + g[3] * 0.06, g[1] + g[4] * 0.06, g[2] + g[5] * 0.06, s, 1f, 0.88f, 0.55f, (float) (0.9 * k * k0), 0);
        }
    }

    private static void renderWaterLight(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double t, float k0) {
        final int N = 3;
        double[] px = new double[16], py = new double[16], pz = new double[16], al = new double[16];
        for (double[] w : WALL) {
            if (Math.abs(w[0] - cam.x) > 90 || Math.abs(w[2] - cam.z) > 90) {
                continue;
            }
            // the patch of light runs with the ripple; the farther from the water, the bigger and fainter (the beam opens)
            double L = w[6], sc = 1.3 / (1 + L * 0.12), amp = 1.0 * w[7] / (1 + L * 0.09);
            double nx = w[3], ny = w[4], nz = w[5];
            double ax = nx != 0 ? 0 : 1, ay = 0, az = nx != 0 ? 1 : 0;
            double bx = 0, by = ny != 0 ? 0 : 1, bz = ny != 0 ? 1 : 0;
            for (int i = 0; i <= N; i++) {
                for (int j = 0; j <= N; j++) {
                    double u = (double) i / N - 0.5, v = (double) j / N - 0.5;
                    double x = w[0] + ax * u + bx * v + nx * 0.015, y = w[1] + ay * u + by * v + ny * 0.015, z = w[2] + az * u + bz * v + nz * 0.015;
                    double s1 = vnoise((x + z) * sc + t * 0.55, y * sc * 1.6 - t * 0.8), s2 = vnoise((x - z) * sc * 1.7 - t * 0.7, y * sc * 2.1 + t * 0.4);
                    int q = i * (N + 1) + j;
                    px[q] = x;
                    py[q] = y;
                    pz[q] = z;
                    al[q] = amp * Math.pow(clamp01((s1 + s2 - 1.0) * 2.2), 1.6) * k0;
                }
            }
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    int k0i = i * (N + 1) + j, k1 = k0i + N + 1;
                    if (al[k0i] + al[k1] + al[k1 + 1] + al[k0i + 1] < 0.01) {
                        continue;
                    }
                    for (int q : new int[] {k0i, k1, k1 + 1, k0i + 1}) {
                        v(out, last, cam, px[q], py[q], pz[q], 1f, 0.95f, 0.82f, (float) al[q]);
                    }
                }
            }
        }
    }

    // At night: a light on the ground aimed at the crown; the crown's underside lights up (falling off with distance),
    // and shows in the pond too.
    private static void renderLightUp(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float k0, boolean mirror) {
        for (Spot S : SPOTS) {
            double d = Math.hypot(S.x - cam.x, S.z - cam.z);
            if (d > 80) {
                continue;
            }
            disc(out, last, cam, S.x, S.y, S.z, 0.22, 1f, 0.85f, 0.55f, 0.9f * k0, 0.1f);
            disc(out, last, cam, S.x, S.y, S.z, 0.9, 1f, 0.7f, 0.4f, 0.18f * k0, 0);
            boolean many = S.b.size() > 20;
            for (int i = many ? 1 : 0; i < S.b.size(); i += many ? 2 : 1) {
                double[] b = S.b.get(i);
                double dd = (b[0] - S.x) * (b[0] - S.x) + (b[1] - S.y) * (b[1] - S.y) + (b[2] - S.z) * (b[2] - S.z), I = clamp01(40 / (6 + dd));
                if (I < 0.1) {
                    continue;
                }
                float[] c = PALC[(int) b[3]];
                float r = Math.min(1, c[0] * 1.3f + 0.1f), g = Math.min(1, c[1] * 1.15f + 0.05f), bl = c[2] * 0.8f;
                // the crown's underside seen from any side: a disc facing the camera just under the block
                disc(out, last, cam, b[0], b[1] - 0.05, b[2], 1.4, r, g, bl, (float) (I * 0.26) * k0, 0);
                if (mirror && b[1] > P) {
                    discUnder(out, last, cam, b[0], 2 * P - b[1] + 0.05, b[2], 1.4, r, g, bl, (float) (I * 0.2) * k0);
                }
            }
            if (mirror && S.y > P) {
                discUnder(out, last, cam, S.x, 2 * P - S.y, S.z, 0.9, 1f, 0.7f, 0.4f, 0.12f * k0);
            }
        }
    }

    // ---------------------------------------------------------------- red dragonflies (aka-tombo), by day

    private static double[] flyPlace(ClientLevel level, Vec3 F) {
        double R = 16;
        for (int k = 0; k < 12; k++) {
            // they like open places: the pond, the garden's lawn, the trail
            double x = F.x + (rnd() - 0.5) * 2 * R, z = F.z + (rnd() - 0.5) * 2 * R;
            int g = top(level, x, z);
            if (g < -900) {
                continue;
            }
            boolean pond = POND.contains(key(x, z));
            boolean open = level.getBlockState(new BlockPos(Mth.floor(x), g + 2, Mth.floor(z))).isAir()
                    && level.getBlockState(new BlockPos(Mth.floor(x), g + 4, Mth.floor(z))).isAir();
            if (!pond && !open) {
                continue;
            }
            return new double[] {x, (pond ? P : g + 1) + 0.8 + rnd() * 1.8, z, pond ? 1 : 0};
        }
        return null;
    }

    private static void stepFlies(ClientLevel level, Vec3 F, double dt) {
        if (night > 0.5f) {
            return;
        }
        double R = 22;
        for (Fly f : FLIES) {
            if (f.x > 1e8 || Math.abs(f.x - F.x) > R || Math.abs(f.z - F.z) > R) {
                double[] q = flyPlace(level, F);
                if (q == null) {
                    continue;
                }
                f.x = f.tx = q[0];
                f.y = f.ty = q[1];
                f.z = f.tz = q[2];
                f.vx = f.vy = f.vz = 0;
                f.hold = rnd() * 2;
                continue;
            }
            f.ph += dt;
            f.hold -= dt;
            if (f.hold < 0) {
                double[] q = flyPlace(level, F);
                if (q != null) {
                    double dx = q[0] - f.x, dz = q[2] - f.z, d = Math.hypot(dx, dz), m = Math.min(1, (2 + rnd() * 3) / (d == 0 ? 1 : d));
                    f.tx = f.x + dx * m;
                    f.tz = f.z + dz * m;
                    int g = top(level, f.tx, f.tz);
                    boolean pond = POND.contains(key(f.tx, f.tz));
                    f.ty = (pond ? P : g + 1) + 0.7 + rnd() * 1.6;
                    f.dip = pond && rnd() < 0.35;
                }
                f.hold = 0.6 + rnd() * 2.2;
            }
            // a stiff, damped spring: starts fast, brakes hard and hovers trembling
            double ty = f.dip && f.hold < 0.5 && f.hold > 0.25 ? P + 0.05 : f.ty;
            double k = 22, c = 9, h = Math.min(dt, 0.03);
            for (double s = 0; s < dt - 1e-9; s += h) {
                double e = Math.min(h, dt - s);
                f.vx += ((f.tx - f.x) * k - f.vx * c) * e;
                f.vy += ((ty - f.y) * k - f.vy * c) * e;
                f.vz += ((f.tz - f.z) * k - f.vz * c) * e;
                f.x += f.vx * e;
                f.y += f.vy * e;
                f.z += f.vz * e;
            }
            if (Math.hypot(f.vx, f.vz) > 0.6) {
                f.hd = Math.atan2(f.vz, f.vx);
            }
        }
    }

    private static void quad(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double[] a, double[] b, double[] c, double[] d, float r, float g, float bl, float al) {
        v(out, last, cam, a[0], a[1], a[2], r, g, bl, al);
        v(out, last, cam, b[0], b[1], b[2], r, g, bl, al);
        v(out, last, cam, c[0], c[1], c[2], r, g, bl, al);
        v(out, last, cam, d[0], d[1], d[2], r, g, bl, al);
    }

    private static void renderFlies(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        float day = 1 - Mth.clamp((night - 0.35f) / 0.15f, 0, 1);
        if (day <= 0) {
            return;
        }
        for (Fly f : FLIES) {
            if (f.x > 1e8) {
                continue;
            }
            double d = Math.sqrt((f.x - cam.x) * (f.x - cam.x) + (f.y - cam.y) * (f.y - cam.y) + (f.z - cam.z) * (f.z - cam.z));
            if (d > 26) {
                continue;
            }
            float a = (float) clamp01(1.3 - d / 26) * fade * day;
            double hx = Math.cos(f.hd), hz = Math.sin(f.hd), px = -hz, pz = hx, y = f.y + Math.sin(f.ph * 11) * 0.01;
            // a thin abdomen (with some thickness), the thorax and the head
            seg(out, last, cam, f.x, y, f.z, hx, hz, px, pz, -0.38, -0.06, 0.016, 0.022, 0.78f, 0.12f, 0.08f, a);
            seg(out, last, cam, f.x, y, f.z, hx, hz, px, pz, -0.06, 0.06, 0.035, 0.035, 0.45f, 0.1f, 0.06f, a);
            seg(out, last, cam, f.x, y, f.z, hx, hz, px, pz, 0.06, 0.11, 0.04, 0.02, 0.45f, 0.1f, 0.06f, a);
            // four wings: they beat too fast to see, so they are a pale veil that flickers
            double fl = 0.55 + 0.45 * Math.abs(Math.sin(f.ph * 37)), lift = 0.02 * Math.sin(f.ph * 37);
            float wa = (float) (0.32 * a * fl);
            for (double[] ul : new double[][] {{0.03, 0.26}, {-0.04, 0.24}}) {
                for (int sdn = 1; sdn >= -1; sdn -= 2) {
                    double bx = f.x + hx * ul[0], bz = f.z + hz * ul[0], ex = px * sdn * ul[1], ez = pz * sdn * ul[1], w = 0.045;
                    quad(out, last, cam, new double[] {bx + hx * w, y, bz + hz * w}, new double[] {bx + ex + hx * w * 0.6, y + lift, bz + ez + hz * w * 0.6},
                            new double[] {bx + ex - hx * w * 0.6, y + lift, bz + ez - hz * w * 0.6}, new double[] {bx - hx * w, y, bz - hz * w}, 0.92f, 0.9f, 0.85f, wa);
                }
            }
        }
    }

    private static void seg(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double hx, double hz, double px, double pz,
            double u0, double u1, double w0, double w1, float r, float g, float b, float a) {
        v(out, last, cam, x + hx * u0 + px * w0, y, z + hz * u0 + pz * w0, r, g, b, a);
        v(out, last, cam, x + hx * u1 + px * w1, y, z + hz * u1 + pz * w1, r, g, b, a);
        v(out, last, cam, x + hx * u1 - px * w1, y, z + hz * u1 - pz * w1, r, g, b, a);
        v(out, last, cam, x + hx * u0 - px * w0, y, z + hz * u0 - pz * w0, r, g, b, a);
    }

    // ---------------------------------------------------------------- crows crossing the sky, by day

    private static void stepCrows(ClientLevel level, Vec3 F, double dt, double t) {
        if (night > 0.5f) {
            crows = null;
            return;
        }
        nextCrows -= dt;
        if (crows == null && nextCrows < 0) {
            double a = rnd() * Mth.TWO_PI, R = 60, hx = Math.cos(a + Math.PI), hz = Math.sin(a + Math.PI);
            double x0 = F.x + Math.cos(a) * R + (rnd() - 0.5) * 20, z0 = F.z + Math.sin(a) * R + (rnd() - 0.5) * 20;
            int g = top(level, F.x, F.z);
            double y0 = (g < -900 ? F.y : g) + 22 + rnd() * 14;
            // (Stark's test, 2026-11-19: flocks of up to 6 every half minute were too many; a pair or three now and then)
            int n = 2 + (int) (rnd() * 2);
            crows = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Crow c = new Crow();
                c.x = x0 - hx * i * 2.5 + (rnd() - 0.5) * 4;
                c.y = y0 + (rnd() - 0.5) * 3;
                c.z = z0 - hz * i * 2.5 + (rnd() - 0.5) * 4;
                c.hx = hx;
                c.hz = hz;
                c.ph = rnd() * 6;
                crows.add(c);
            }
            crowLife = 2 * R / 7 + 8;
            nextCrows = 60 + rnd() * 60;
            crowCall = true;
        }
        if (crows == null) {
            return;
        }
        crowLife -= dt;
        if (crowLife < 0) {
            crows = null;
            return;
        }
        for (Crow c : crows) {
            c.glide -= dt;
            // flaps a little, glides a little
            if (c.glide < -1.6 - rnd()) {
                c.glide = 0.8 + rnd() * 1.2;
            }
            if (c.glide < 0) {
                c.ph += dt * 2 * Math.PI * 3.2;
            }
            double w = Math.sin(t * 0.3 + c.ph * 0.01) * 0.3;
            c.x += (c.hx * 7 + c.hz * w) * dt;
            c.z += (c.hz * 7 - c.hx * w) * dt;
            c.y += Math.sin(c.ph) * 0.15 * dt;
        }
    }

    private static void renderCrows(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        if (crows == null) {
            return;
        }
        float r = 0.07f, g = 0.07f, b = 0.09f, a = 0.95f * fade;
        for (Crow c : crows) {
            double hx = c.hx, hz = c.hz, px = -hz, pz = hx, flap = c.glide > 0 ? 0.1 : Math.sin(c.ph);
            double tip = 0.55 * flap, mid = 0.25 * flap;
            // body, fan tail, wings in two pieces (arm and hand) that fold in the beat
            double[] b0 = {c.x + hx * 0.45, c.y, c.z + hz * 0.45}, b1 = {c.x - hx * 0.35, c.y, c.z - hz * 0.35};
            quad(out, last, cam, new double[] {b0[0] + px * 0.06, b0[1], b0[2] + pz * 0.06}, new double[] {b1[0] + px * 0.1, b1[1], b1[2] + pz * 0.1},
                    new double[] {b1[0] - px * 0.1, b1[1], b1[2] - pz * 0.1}, new double[] {b0[0] - px * 0.06, b0[1], b0[2] - pz * 0.06}, r, g, b, a);
            quad(out, last, cam, new double[] {b1[0] + px * 0.08, b1[1], b1[2] + pz * 0.08}, new double[] {b1[0] - hx * 0.35 + px * 0.16, b1[1], b1[2] - hz * 0.35 + pz * 0.16},
                    new double[] {b1[0] - hx * 0.35 - px * 0.16, b1[1], b1[2] - hz * 0.35 - pz * 0.16}, new double[] {b1[0] - px * 0.08, b1[1], b1[2] - pz * 0.08}, r, g, b, a);
            for (int s = 1; s >= -1; s -= 2) {
                double[] r0 = {c.x + hx * 0.15, c.y, c.z + hz * 0.15}, r1 = {c.x - hx * 0.2, c.y, c.z - hz * 0.2};
                double[] m0 = {r0[0] + px * s * 0.5, c.y + mid, r0[2] + pz * s * 0.5}, m1 = {r1[0] + px * s * 0.5, c.y + mid, r1[2] + pz * s * 0.5};
                double[] t0 = {m0[0] + px * s * 0.55 - hx * 0.1, c.y + mid + tip, m0[2] + pz * s * 0.55 - hz * 0.1};
                double[] t1 = {m1[0] + px * s * 0.5 - hx * 0.05, c.y + mid + tip, m1[2] + pz * s * 0.5 - hz * 0.05};
                quad(out, last, cam, r0, m0, m1, r1, r, g, b, a);
                quad(out, last, cam, m0, t0, t1, m1, r, g, b, a);
            }
        }
    }

    // ---------------------------------------------------------------- the shishi-odoshi by the pond
    // the spout fills the tube; when the water's weight passes the pin it tips, pours into the pond and swings back to
    // knock on the stone: "kon"

    private static void placeShishi(ClientLevel level) {
        shishi = null;
        if (!hasTea) {
            return;
        }
        int tx = Mth.floor(teaX), tz = Mth.floor(teaZ), gy = pondY + 1;
        Shishi best = null;
        double bd = 1e9;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int a = -12; a <= 12; a++) {
            for (int b = -12; b <= 12; b++) {
                int x = tx + a, z = tz + b, g = top(level, x, z);
                if (g != gy || POND.contains(key(x, z))) {
                    continue;
                }
                if (!level.getBlockState(pos.set(x, g + 1, z)).isAir() || !level.getBlockState(pos.set(x, g + 2, z)).isAir()
                        || !level.getBlockState(pos.set(x, g + 3, z)).isAir()) {
                    continue;
                }
                // facing the pond: the neighbor toward the center is water
                double dx = pondCx - x, dz = pondCz - z, L = Math.hypot(dx, dz);
                int ux = (int) Math.round(dx / L), uz = (int) Math.round(dz / L);
                if (ux != 0 && uz != 0 || !POND.contains(key(x + ux, z + uz))) {
                    continue;
                }
                double d = Math.hypot(a, b) + (Math.hypot(a, b) < 4 ? 20 : 0);
                if (d < bd) {
                    bd = d;
                    best = new Shishi();
                    best.x = x + 0.5;
                    best.y = g + 1;
                    best.z = z + 0.5;
                    best.ux = ux;
                    best.uz = uz;
                }
            }
        }
        if (best != null) {
            best.fill = rnd();
        }
        shishi = best;
    }

    private static void stepShishi(double dt) {
        Shishi S = shishi;
        if (S == null) {
            return;
        }
        // fills in ~7 s; the tube's mouth is up (angle +); past the point it tips all the way
        if (S.pour <= 0) {
            S.fill += dt / 7;
            double target = 0.35 - Math.max(0, S.fill - 0.85) * 2.2;
            S.ang += (target - S.ang) * Math.min(1, dt * 4);
            if (S.fill >= 1) {
                S.pour = 1;
                S.w = -4;
            }
        } else {
            S.w += -14 * dt;
            S.ang += S.w * dt;
            if (S.ang < -0.55) {
                S.ang = -0.55;
                if (S.fill > 0) {
                    for (int i = 0; i < 14; i++) {
                        S.drops.add(new double[] {S.x + S.ux * 1.0, S.y + 0.25, S.z + S.uz * 1.0, S.ux * (0.6 + rnd()) + (rnd() - 0.5) * 0.4, rnd() * 0.6,
                            S.uz * (0.6 + rnd()) + (rnd() - 0.5) * 0.4, 0});
                    }
                    S.fill = 0;
                }
                S.w = 9;
                S.back = true;
            }
            if (S.back && S.ang > 0.35) {
                S.ang = 0.35;
                S.pour = 0;
                S.back = false;
                S.w = 0;
                S.knock = true;
            }
        }
        // the spout's trickle: drops falling into the tube
        if (rnd() < dt * 9) {
            S.drops.add(new double[] {S.x + S.ux * 0.62, S.y + 1.17, S.z + S.uz * 0.62, 0, 0, 0, 1});
        }
        for (double[] d : S.drops) {
            d[4] -= 9.8 * dt;
            d[0] += d[3] * dt;
            d[1] += d[4] * dt;
            d[2] += d[5] * dt;
        }
        S.drops.removeIf(d -> d[1] <= (d[6] > 0 ? S.y + 0.5 + 0.62 * Math.sin(S.ang) + 0.06 : P));
    }

    private static void box(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double cx, double cy, double cz, double ax, double ay, double az, double bx,
            double by, double bz, double h, float[] c, float k) {
        double[][] p = new double[4][];
        int q = 0;
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                p[q++] = new double[] {cx + ax * i + bx * j, cy + ay * i + by * j, cz + az * i + bz * j};
            }
        }
        double[] p00 = p[0], p01 = p[1], p10 = p[2], p11 = p[3];
        double[] t00 = {p00[0], p00[1] + h, p00[2]}, t01 = {p01[0], p01[1] + h, p01[2]}, t10 = {p10[0], p10[1] + h, p10[2]}, t11 = {p11[0], p11[1] + h, p11[2]};
        float r1 = c[0] * k, g1 = c[1] * k, b1 = c[2] * k, r2 = r1 * 0.75f, g2 = g1 * 0.75f, b2 = b1 * 0.75f;
        quad(out, last, cam, t00, t01, t11, t10, r1, g1, b1, 1);
        quad(out, last, cam, p00, p01, t01, t00, r2, g2, b2, 1);
        quad(out, last, cam, p10, p11, t11, t10, r2, g2, b2, 1);
        quad(out, last, cam, p00, p10, t10, t00, r2, g2, b2, 1);
        quad(out, last, cam, p01, p11, t11, t01, r2, g2, b2, 1);
        quad(out, last, cam, p00, p01, p11, p10, r2, g2, b2, 1);
    }

    private static void renderShishi(VertexConsumer out, PoseStack.Pose last, Vec3 cam) {
        Shishi S = shishi;
        if (S == null || Math.hypot(S.x - cam.x, S.z - cam.z) > 50) {
            return;
        }
        float k = Mth.lerp(night, 1f, 0.4f);
        double ux = S.ux, uz = S.uz, px = -uz, pz = ux;
        float[] bam = {0.55f, 0.62f, 0.28f}, post = {0.42f, 0.32f, 0.2f}, rock = {0.5f, 0.5f, 0.48f}, node = {0.44f, 0.5f, 0.22f};
        // two posts, the stone it knocks on and the bamboo spout behind
        box(out, last, cam, S.x + px * 0.18, S.y, S.z + pz * 0.18, ux * 0.05, 0, uz * 0.05, px * 0.04, 0, pz * 0.04, 0.6, post, k);
        box(out, last, cam, S.x - px * 0.18, S.y, S.z - pz * 0.18, ux * 0.05, 0, uz * 0.05, px * 0.04, 0, pz * 0.04, 0.6, post, k);
        box(out, last, cam, S.x - ux * 0.62, S.y, S.z - uz * 0.62, ux * 0.18, 0, uz * 0.18, px * 0.16, 0, pz * 0.16, 0.14, rock, k);
        // the spout stands to the side, its arm over the tube's mouth
        box(out, last, cam, S.x + ux * 0.62 + px * 0.32, S.y, S.z + uz * 0.62 + pz * 0.32, ux * 0.05, 0, uz * 0.05, px * 0.05, 0, pz * 0.05, 1.25, bam, k);
        box(out, last, cam, S.x + ux * 0.62 + px * 0.17, S.y + 1.15, S.z + uz * 0.62 + pz * 0.17, ux * 0.035, 0, uz * 0.035, px * 0.17, 0, pz * 0.17, 0.07, bam, k);
        // the tube: pin at 0.5 high, mouth to the pond (+u); angle + = mouth raised
        double ca = Math.cos(S.ang), sa = Math.sin(S.ang), L = 0.75, cy = S.y + 0.5;
        for (int i = -4; i < 4; i++) {
            double u0 = i / 4.0 * L, u1 = (i + 1) / 4.0 * L, um = (u0 + u1) / 2;
            boolean nd = i == -1 || i == 2;
            box(out, last, cam, S.x + ux * um * ca, cy + um * sa - 0.06, S.z + uz * um * ca, ux * (u1 - u0) / 2 * ca, (u1 - u0) / 2 * sa, uz * (u1 - u0) / 2 * ca,
                    px * 0.07, 0, pz * 0.07, 0.12, nd ? node : bam, k);
        }
    }

    private static void renderDrops(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        Shishi S = shishi;
        if (S == null) {
            return;
        }
        float r = Mth.lerp(night, 0.7f, 0.3f), g = Mth.lerp(night, 0.85f, 0.4f), b = Mth.lerp(night, 1f, 0.5f);
        for (double[] d : S.drops) {
            disc(out, last, cam, d[0], d[1], d[2], d[6] > 0 ? 0.025 : 0.04, r, g, b, 0.8f * fade, 0.6f);
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
        float daylight = level.getSkyDarken(partial);
        night = Mth.clamp((0.8f - daylight) / 0.6f, 0, 1);
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

        // the step (each frame, like the preview)
        if (dt > 0) {
            if (System.currentTimeMillis() - nearAt > 3000) {
                nearWater(cam);
            }
            stepLeaves(level, cam, dt, t);
            nextShower -= dt;
            if (nextShower < 0) {
                shower(cam, t);
                nextShower = 18 + rnd() * 16;
            }
            stepKoi(cam, dt);
            stepFlies(level, cam, dt);
            stepCrows(level, cam, dt, t);
            stepShishi(dt);
        }

        PoseStack.Pose last = event.getPoseStack().last();
        boolean pondHere = MIRROR && P > -900 && pondQuads.length > 0 && cam.y > P + 0.05 && Math.hypot(cam.x - pondCx, cam.z - pondCz) < pondR + 160;
        mirrorDrawn = false;
        if (pondHere && !FxShaders.flat()) {
            drawPond(event, cam, fade, t);
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // the shishi-odoshi first (solid: drawn after the see-through things it would cover them)
        if (shishi != null) {
            VertexConsumer solid = buffers.getBuffer(FxRenderTypes.SOLID);
            renderShishi(solid, last, cam);
            buffers.endBatch(FxRenderTypes.SOLID);
        }
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        if (gardenOn) {
            renderKoi(out, last, cam, fade);
        }
        // "Partículas do ambiente" off (Experiência do jogador): the loose particles go, the garden itself stays
        if (FxConfig.ambient()) {
            renderLeaves(out, last, cam, fade);
            renderFlies(out, last, cam, fade);
            renderCrows(out, last, cam, fade);
            renderDrops(out, last, cam, fade);
        }
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        if (gardenOn) {
            VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW_FLAT);
            float day = (1 - night) * (float) clamp01(SUN[1] * 5) * fade * (level.isRaining() ? 0.3f : 1f);
            if (day > 0.01f) {
                renderGold(glow, last, cam, day);
                renderWaterLight(glow, last, cam, t, day);
            }
            if (night > 0.01f) {
                renderLightUp(glow, last, cam, night * fade, mirrorDrawn);
            }
            buffers.endBatch(FxRenderTypes.GLOW_FLAT);
        }
    }

    // The pond: the mirrored world into its own picture, then the surface (dark water over the mirror) over the vanilla
    // water.
    private static void drawPond(RenderLevelStageEvent event, Vec3 cam, float fade, double t) {
        ShaderInstance pond = FxShaders.kyotoPond;
        if (pond == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Matrix4f view = event.getModelViewMatrix(), proj = event.getProjectionMatrix();
        int prev = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        var main = mc.getMainRenderTarget();
        boolean useRefl = false;
        ShaderInstance mir = FxShaders.kyotoMirror;
        if (refl == null) {
            refl = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (refl.width != main.width || refl.height != main.height) {
            refl.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        try {
            refl.setClearColor(0, 0, 0, 0);
            refl.clear(Minecraft.ON_OSX);
            if (mirror != null && mir != null) {
                refl.bindWrite(true);
                mir.safeGetUniform("Origin").set((float) (mirX - cam.x), (float) (mirY - cam.y), (float) (mirZ - cam.z));
                mir.safeGetUniform("OriginW").set((float) mirX, (float) mirY, (float) mirZ);
                mir.safeGetUniform("Plane").set((float) (P - mirY));
                mir.safeGetUniform("Time").set((float) t);
                mir.safeGetUniform("Ripple").set((float) (0.35 + 0.9 * wind(cam.x, cam.z, t)[2]));
                mir.safeGetUniform("SunDir").set((float) SUN[0], (float) SUN[1], (float) SUN[2]);
                mir.safeGetUniform("Night").set(night);
                mir.safeGetUniform("Bright").set(Mth.lerp(night, 1f, 0.85f));
                RenderSystem.disableBlend();
                RenderSystem.disableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                mirror.bind();
                mirror.drawWithShader(view, proj, mir);
                VertexBuffer.unbind();
                useRefl = true;
            }
        } finally {
            RenderSystem.enableCull();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
            RenderSystem.viewport(vp[0], vp[1], vp[2], vp[3]);
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(view);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            float[] fog = RenderSystem.getShaderFogColor();
            Vec3 sky = mc.level.getSkyColor(cam, event.getPartialTick().getGameTimeDeltaPartialTick(false));
            double n = night;
            pond.setSampler("Refl", refl.getColorTextureId());
            pond.safeGetUniform("UseRefl").set(useRefl ? 1f : 0f);
            pond.safeGetUniform("Deep").set((float) Mth.lerp(n, 0x0e / 255.0, 0x03 / 255.0), (float) Mth.lerp(n, 0x22 / 255.0, 0x07 / 255.0),
                    (float) Mth.lerp(n, 0x20 / 255.0, 0x0a / 255.0));
            pond.safeGetUniform("Rfl").set((float) Mth.lerp(n, 0.42, 0.5));
            pond.safeGetUniform("SkyTop").set((float) sky.x, (float) sky.y, (float) sky.z);
            pond.safeGetUniform("SkyHor").set(fog[0], fog[1], fog[2]);
            pond.safeGetUniform("SunDir").set((float) SUN[0], (float) SUN[1], (float) SUN[2]);
            pond.safeGetUniform("Night").set(night);
            pond.safeGetUniform("Fade").set(fade);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            float[] q = pondQuads;
            float ox = (float) cam.x, oy = (float) (cam.y - 0.0015), oz = (float) cam.z;
            int drawn = 0;
            for (int i = 0; i < q.length; i += 3) {
                if (Math.abs(q[i] - ox) > 200 || Math.abs(q[i + 2] - oz) > 200) {
                    continue;
                }
                float x = q[i] - ox, y = q[i + 1] - oy, z = q[i + 2] - oz;
                b.addVertex(x, y, z + 1);
                b.addVertex(x + 1, y, z + 1);
                b.addVertex(x + 1, y, z);
                b.addVertex(x, y, z);
                drawn++;
            }
            MeshData mesh = b.build();
            if (mesh != null) {
                RenderSystem.setShader(() -> pond);
                BufferUploader.drawWithShader(mesh);
            }
            mirrorDrawn = useRefl && drawn > 0;
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

    private static void v(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        out.addVertex(last, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
    }

    // A flat piece's two axes, turned to face the camera's POSITION (not where it looks): turning the head doesn't turn
    // it, only walking round it does, so it stays in the world instead of stuck on the screen.
    private static double lookX, lookY, lookZ = 1;
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

    // A round disc facing the camera, its center at a, the rim at a * edge (14 slices).
    private static void disc(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double s, float r, float g, float b, float a, float edge) {
        face(cam, x, y, z);
        double px = x + BR[0] * s, py = y + BR[1] * s, pz = z + BR[2] * s;
        for (int q = 1; q <= 14; q++) {
            double ang = q / 14.0 * Math.PI * 2, cx = Math.cos(ang) * s, cy = Math.sin(ang) * s;
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

    // A disc seen in the pond (under its surface): faced to the camera, then laid on the surface along the camera's ray.
    private static void discUnder(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double s, float r, float g, float b, float a) {
        if (cam.y <= P + 0.05 || y >= P) {
            return;
        }
        face(cam, x, y, z);
        double px = x + BR[0] * s, py = y + BR[1] * s, pz = z + BR[2] * s;
        for (int q = 1; q <= 14; q++) {
            double ang = q / 14.0 * Math.PI * 2, cx = Math.cos(ang) * s, cy = Math.sin(ang) * s;
            double qx = x + BR[0] * cx + BU[0] * cy, qy = Math.min(P - 0.01, y + BR[1] * cx + BU[1] * cy), qz = z + BR[2] * cx + BU[2] * cy;
            vu(out, last, cam, x, y, z, r, g, b, a);
            vu(out, last, cam, px, Math.min(P - 0.01, py), pz, r, g, b, 0);
            vu(out, last, cam, qx, qy, qz, r, g, b, 0);
            vu(out, last, cam, qx, qy, qz, r, g, b, 0);
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    // ---------------------------------------------------------------- the screen and the fog

    public static boolean gradeOn() {
        return active() && FxShaders.jjGrade != null;
    }

    // Autumn: warm and a little more color by day; at night indigo edges, never darkening, the lights' warmth left over.
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.jjGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        float n = night;
        sh.safeGetUniform("Sat").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.12f, 1.06f)));
        sh.safeGetUniform("Contrast").set(Mth.lerp(fade, 1f, Mth.lerp(n, 1.05f, 1.04f)));
        sh.safeGetUniform("Wash").set(0f, 0f, 0f, 0f);
        sh.safeGetUniform("Warm").set(0f);
        sh.safeGetUniform("Vig").set(16 / 255f, 12 / 255f, 40 / 255f, 0.26f * n * fade);
        sh.safeGetUniform("Flash").set(0f);
        FxPost.pass(sh);
    }

    // The fog: autumn's golden haze by day, a deep indigo at night (never black).
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float n = night, k = fade * 0.75f;
        float r = Mth.lerp(n, 0xec / 255f, 0x12 / 255f), g = Mth.lerp(n, 0xd4 / 255f, 0x10 / 255f), b = Mth.lerp(n, 0xb2 / 255f, 0x22 / 255f);
        // (the vanilla night fog is darker still: at night only its hue moves, never down)
        if (n > 0.5f) {
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
        float near = 20f, far = Mth.lerp(night, 170f, 130f);
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
        double t = level.getGameTime() / 20.0;
        double w = wind(p.x, p.z, t)[2];
        leafGust = (float) w;
        double dt = 0.05;
        nextBell -= dt;
        if (nextBell < 0) {
            // a temple bell far off, from some side
            double a = rnd() * Mth.TWO_PI;
            play(level, p.x + Math.cos(a) * 30, p.y + 8, p.z + Math.sin(a) * 30, ModSounds.KYOTO_SINO.get(), 2.6f, 1f);
            nextBell = 40 + rnd() * 30;
        }
        nextBug -= dt;
        if (night > 0.6f && nextBug < 0) {
            // the bell cricket: a high trembling "riiin" in phrases, from the grass round the player
            double a = rnd() * Mth.TWO_PI, r = 5 + rnd() * 9;
            double x = p.x + Math.cos(a) * r, z = p.z + Math.sin(a) * r;
            int g = top(level, x, z);
            play(level, x, (g < -900 ? p.y : g + 1), z, ModSounds.KYOTO_SUZUMUSHI.get(), 0.45f, 0.96f + (float) rnd() * 0.08f);
            nextBug = 0.9 + rnd() * 1.6;
        }
        nextKnock -= dt;
        // bamboo knocking together in a gust (only near a grove)
        if (w > 0.55 && nextKnock < 0) {
            double[] best = null;
            int n = 0;
            for (double[] b : cur.bamboo) {
                if (Math.abs(b[0] - p.x) < 12 && Math.abs(b[2] - p.z) < 12) {
                    n++;
                    if (best == null || rnd() < 1.0 / n) {
                        best = b;
                    }
                }
            }
            if (n > 2) {
                play(level, best[0], best[1] + 3, best[2], ModSounds.KYOTO_BAMBU.get(), 0.6f, 0.8f + (float) rnd() * 0.5f);
            }
            nextKnock = 0.15 + rnd() * 0.9;
        }
        if (shishi != null && shishi.knock) {
            shishi.knock = false;
            play(level, shishi.x, shishi.y + 0.5, shishi.z, ModSounds.KYOTO_SHISHI.get(), 1.6f, 0.97f + (float) rnd() * 0.06f);
        }
        if (crows != null && crowCall && Math.hypot(crows.get(0).x - p.x, crows.get(0).z - p.z) < 70) {
            crowCall = false;
            Crow c = crows.get(0);
            play(level, c.x, c.y, c.z, ModSounds.KYOTO_CORVO.get(), 3.5f, 0.9f + (float) rnd() * 0.2f);
        }
    }

    private static float leafGust;

    // The leaves in the wind, all round the player, rising with the gusts.
    private static final class LeafLoop extends AbstractTickableSoundInstance {
        LeafLoop() {
            super(ModSounds.KYOTO_FOLHAS.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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
            volume = (0.2f + 0.8f * leafGust) * 0.55f * strength;
            pitch = 0.95f + 0.1f * leafGust;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
