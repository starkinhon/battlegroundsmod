package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

// The Vale do Fim (Stark 2026-11-12, "essa é a melhor construção do mod... NOTA 100", from the approved preview), done
// like the other Naruto features: once per chunk of the biome, the preview's valePiece step by step (ValeGen is its
// line-by-line port):
// 1. the valley: at most one per cell of 900 blocks, where the biome covers its whole footprint (a chunk without the
//    biome never runs this, so a valley that crossed into another biome would be cut off) and the land is not too high
//    around it; every column of it as the preview's: the massif and the gorge (filled from the natural ground up to the
//    new top, the natural ground above it cleared), the upper river, the lake, the waterfall as falling water fed by the
//    upper river, the vines down the cliffs;
// 2. Hashirama and Madara on their promontories, facing each other over the fall (their stone in four tones);
// 3. the boulders of the lake and the gorge;
// 4. the biome's pieces: craters and cuts of the fight, rock outcrops, boulders and trees, each deciding alone whether it
//    goes in (the same rule as the preview, which doesn't depend on which chunk asks).
// Writes only inside its own chunk.
public final class EndValleyFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0x0E7DA1;
    private static final int MID = (ValeGen.UMIN + ValeGen.UMAX) / 2;
    private static final Cache<Long, Optional<ValeGen.Frame>> FRAMES = CacheBuilder.newBuilder().maximumSize(256).build();
    private static final java.util.Set<Long> PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Cache<Long, Map<Long, int[]>> STATUES = CacheBuilder.newBuilder().maximumSize(16).build();
    private static final Cache<Long, int[]> PIECES = CacheBuilder.newBuilder().maximumSize(400).build();
    private static final ValeGen.Kind[] FILLERS = {ValeGen.Kind.CRATER, ValeGen.Kind.CUT, ValeGen.Kind.OUTCROP,
            ValeGen.Kind.BOULDER, ValeGen.Kind.TREE};

    public EndValleyFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        int seed = worldKey ^ SALT;
        int sea = generator.getSeaLevel();
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        // the natural top solid block (the world's land as vanilla shaped it)
        IntBinaryOperator natural = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;

        // the valleys that may reach this chunk or the pieces that decide near it
        List<ValeGen.Frame> frames = framesNear(x0 - 64, z0 - 64, x1 + 64, z1 + 64, seed, sea, generator, random, natural);
        // the ground as the preview sees it: the valley's top where a valley is, the natural land elsewhere
        IntBinaryOperator ground = (x, z) -> {
            for (ValeGen.Frame F : frames) {
                ValeGen.Height h = ValeGen.valleyHeight(F, x, z, natural);
                if (h != null) {
                    return h.s();
                }
            }
            return natural.applyAsInt(x, z);
        };

        // 1. The valley's columns.
        if (!frames.isEmpty()) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    ValeGen.Column c = null;
                    for (ValeGen.Frame F : frames) {
                        c = ValeGen.valleyColumn(F, x, z, natural);
                        if (c != null) {
                            break;
                        }
                    }
                    if (c == null) {
                        continue;
                    }
                    int g = natural.applyAsInt(x, z);
                    int from = Math.max(minY, Math.min(c.low, g + 1));
                    for (int y = from; y <= Math.min(c.s, maxY); y++) {
                        level.setBlock(pos.set(x, y, z), block(c.at(y)), 2);
                    }
                    boolean fall = c.fall > ValeGen.NONE;
                    int upper = Math.max(g + 3, Math.max(c.w, c.fall));
                    if (c.vine != null && !fall) {
                        upper = Math.max(upper, c.vine[1]);
                    }
                    if (g < sea) {
                        upper = Math.max(upper, sea);
                    }
                    for (int y = Math.max(minY, c.s + 1); y <= Math.min(upper, maxY); y++) {
                        BlockState st;
                        if (y <= c.w) {
                            st = Blocks.WATER.defaultBlockState();
                        } else if (fall && y <= c.fall) {
                            st = Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock();
                        } else if (c.vine != null && !fall && y >= c.vine[0] && y <= c.vine[1]) {
                            st = vine(c.vine[2], c.vine[3]);
                        } else {
                            st = Blocks.AIR.defaultBlockState();
                        }
                        if (!level.getBlockState(pos.set(x, y, z)).equals(st)) {
                            level.setBlock(pos, st, 2);
                        }
                    }
                }
            }

            // 2. The statues and 3. the boulders in the water.
            long chunkKey = chunk.toLong();
            for (ValeGen.Frame F : frames) {
                int[] vox = statues(F).get(chunkKey);
                if (vox != null) {
                    for (int i = 0; i < vox.length; i += 4) {
                        if (vox[i + 1] >= minY && vox[i + 1] <= maxY) {
                            level.setBlock(pos.set(vox[i], vox[i + 1], vox[i + 2]), block(vox[i + 3]), 2);
                        }
                    }
                }
                for (ValeGen.Rock r : F.rocks) {
                    int x = F.worldX(r.u(), r.v()), z = F.worldZ(r.u(), r.v());
                    if (x < x0 - 6 || x > x1 + 6 || z < z0 - 6 || z > z1 + 6) {
                        continue;
                    }
                    ValeGen.Column c = ValeGen.valleyColumn(F, x, z, natural);
                    if (c == null || c.kind != ValeGen.K.WATER) {
                        continue;
                    }
                    DesertGen.Vox p = new DesertGen.Vox();
                    ValeGen.boulder(p, x, F.base + 1, z, r.r(), F.seed + 40);
                    int[] b = p.pack();
                    for (int i = 0; i < b.length; i += 4) {
                        if (b[i] < x0 || b[i] > x1 || b[i + 2] < z0 || b[i + 2] > z1) {
                            continue;
                        }
                        BlockState there = level.getBlockState(pos.set(b[i], b[i + 1], b[i + 2]));
                        if (there.isAir() || there.getFluidState().is(Fluids.WATER)) {
                            level.setBlock(pos, block(b[i + 3]), 2);
                        }
                    }
                }
            }
        }

        // 4. The biome's pieces. The candidates come from a wider box than the chunk, so every piece that could make one
        // near the chunk give way is seen here too.
        int wide = 34;
        List<ValeGen.Obj> all = ValeGen.objectsNear(x0 - wide, z0 - wide, x1 + wide, z1 + wide, seed, FILLERS);
        List<ValeGen.Obj> cands = new ArrayList<>();
        for (ValeGen.Obj o : all) {
            if (ok(o, frames, natural, generator, random, sea)) {
                cands.add(o);
            }
        }
        List<ValeGen.Obj> mine = new ArrayList<>();
        for (ValeGen.Obj o : cands) {
            if (o.x() + o.reach() < x0 || o.x() - o.reach() > x1 || o.z() + o.reach() < z0 || o.z() - o.reach() > z1) {
                continue;
            }
            if (o.kind() == ValeGen.Kind.TREE) {
                boolean skip = false;
                for (ValeGen.Obj p : cands) {
                    if (p.kind() != ValeGen.Kind.TREE && sq(p.x() - o.x()) + sq(p.z() - o.z()) < sq(p.reach())) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
            } else {
                boolean skip = false;
                for (ValeGen.Obj p : cands) {
                    if (p != o && p.kind() != ValeGen.Kind.TREE && ValeGen.before(p, o)
                            && sq(p.x() - o.x()) + sq(p.z() - o.z()) < sq(p.reach() * 0.6 + o.reach() * 0.6)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
            }
            mine.add(o);
        }
        // in the preview's order (kinds), the trees by position so the same one wins where two crowns meet
        mine.sort(Comparator.<ValeGen.Obj>comparingInt(o -> o.kind().order).thenComparingInt(ValeGen.Obj::x)
                .thenComparingInt(ValeGen.Obj::z));
        for (ValeGen.Obj o : mine) {
            int gy = ground.applyAsInt(o.x(), o.z());
            int[] vox = piece(o, gy, ground);
            boolean tree = o.kind() == ValeGen.Kind.TREE;
            for (int i = 0; i < vox.length; i += 4) {
                int x = vox[i], y = vox[i + 1], z = vox[i + 2], t = vox[i + 3];
                if (x < x0 || x > x1 || z < z0 || z > z1 || y < minY || y > maxY) {
                    continue;
                }
                pos.set(x, y, z);
                if (t == ValeGen.CARVE) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    continue;
                }
                if (tree) {
                    BlockState there = level.getBlockState(pos);
                    if (!(there.isAir() || (there.canBeReplaced() && there.getFluidState().isEmpty()))) {
                        continue;
                    }
                }
                level.setBlock(pos, block(t), 2);
            }
        }
        return true;
    }

    private static double sq(double a) {
        return a * a;
    }

    // ---- where the valleys go -------------------------------------------------------------------------------------

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.VALE_DO_FIM);
    }

    // The valleys around a box of the running world (NarutoSites: the seals on the statues), the same frames the
    // feature builds from.
    public static List<ValeGen.Frame> framesNear(ServerLevel level, int bx0, int bz0, int bx1, int bz1) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        IntBinaryOperator natural = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        return framesNear(bx0, bz0, bx1, bz1, worldKey ^ SALT, generator.getSeaLevel(), generator, random, natural);
    }

    // The same, for the server's tick: only the valleys already worked out. A cell not known yet is worked out on a
    // background thread and shows up on a later call. Finding a valley costs a lot of terrain samples, and done on the
    // tick it froze the server for seconds (PC profile of 2026-11-13: 15% of the server thread, on top in 4 freezes).
    public static List<ValeGen.Frame> knownFramesNear(ServerLevel level, int bx0, int bz0, int bx1, int bz1) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        int seed = worldKey ^ SALT, sea = generator.getSeaLevel();
        IntBinaryOperator natural = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        List<ValeGen.Frame> out = new ArrayList<>();
        int size = ValeGen.Kind.VALE.size;
        for (int i = Math.floorDiv(bx0, size) - 1; i <= Math.floorDiv(bx1, size) + 1; i++) {
            for (int j = Math.floorDiv(bz0, size) - 1; j <= Math.floorDiv(bz1, size) + 1; j++) {
                long key = (((long) i << 32) | (j & 0xFFFFFFFFL)) ^ ((long) seed * 0x9E3779B97F4A7C15L);
                Optional<ValeGen.Frame> known = FRAMES.getIfPresent(key);
                if (known == null) {
                    if (PENDING.add(key)) {
                        int ci = i, cj = j;
                        net.minecraft.Util.backgroundExecutor().execute(() -> {
                            try {
                                frame(ci, cj, seed, sea, generator, random, natural);
                            } finally {
                                PENDING.remove(key);
                            }
                        });
                    }
                    continue;
                }
                ValeGen.Frame F = known.orElse(null);
                if (F != null && touches(F, bx0, bz0, bx1, bz1)) {
                    out.add(F);
                }
            }
        }
        return out;
    }

    // The valleys whose footprint (with its blending margin) touches the box.
    private static List<ValeGen.Frame> framesNear(int bx0, int bz0, int bx1, int bz1, int seed, int sea, ChunkGenerator generator,
            RandomState random, IntBinaryOperator natural) {
        List<ValeGen.Frame> out = new ArrayList<>();
        int size = ValeGen.Kind.VALE.size;
        for (int i = Math.floorDiv(bx0, size) - 1; i <= Math.floorDiv(bx1, size) + 1; i++) {
            for (int j = Math.floorDiv(bz0, size) - 1; j <= Math.floorDiv(bz1, size) + 1; j++) {
                ValeGen.Frame F = frame(i, j, seed, sea, generator, random, natural);
                if (F != null && touches(F, bx0, bz0, bx1, bz1)) {
                    out.add(F);
                }
            }
        }
        return out;
    }

    private static boolean touches(ValeGen.Frame F, int bx0, int bz0, int bx1, int bz1) {
        double u0 = ValeGen.UMIN - ValeGen.MARGIN - 2, u1 = ValeGen.UMAX + ValeGen.MARGIN + 2, v1 = 66 + ValeGen.MARGIN + 2;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double u : new double[] {u0, u1}) {
            for (double v : new double[] {-v1, v1}) {
                double x = F.cx + F.c * u - F.s * v, z = F.cz + F.s * u + F.c * v;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        return maxX >= bx0 && minX <= bx1 && maxZ >= bz0 && minZ <= bz1;
    }

    private static ValeGen.Frame frame(int i, int j, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator natural) {
        long key = (((long) i << 32) | (j & 0xFFFFFFFFL)) ^ ((long) seed * 0x9E3779B97F4A7C15L);
        try {
            return FRAMES.get(key, () -> Optional.ofNullable(findFrame(i, j, seed, sea, generator, random, natural))).orElse(null);
        } catch (ExecutionException e) {
            return null;
        }
    }

    // The cell's valley: its spot first, then a fine grid over the cell, each with eight turns of the valley (eight
    // seeds, each a whole valley of the preview's), the first place where it fits. The PC test of 2026-11-12: with the
    // spot and a 5 by 5 grid in one turn no valley ever fit in 12000 by 12000 blocks, though the footprint fit in 1876
    // places there.
    private static ValeGen.Frame findFrame(int i, int j, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator natural) {
        ValeGen.Obj o = ValeGen.cellObject(ValeGen.Kind.VALE, i, j, seed);
        if (o == null) {
            return null;
        }
        ValeGen.Kind k = ValeGen.Kind.VALE;
        List<int[]> spots = new ArrayList<>();
        spots.add(new int[] {o.x(), o.z()});
        int n = 9, step = (k.size - 2 * k.pad) / (n - 1);
        for (int gi = 0; gi < n; gi++) {
            for (int gj = 0; gj < n; gj++) {
                spots.add(new int[] {i * k.size + k.pad + gi * step, j * k.size + k.pad + gj * step});
            }
        }
        int[] seeds = new int[8];
        double[] cs = new double[8], ss = new double[8];
        for (int t = 0; t < 8; t++) {
            seeds[t] = t == 0 ? o.seed() : o.seed() ^ (t * 0x9E3779B1);
            double a = new LeafTreeGen.Rng(seeds[t]).range(0, Math.PI * 2);
            cs[t] = StrictMath.cos(a);
            ss[t] = StrictMath.sin(a);
        }
        for (int[] p : spots) {
            for (int t = 0; t < 8; t++) {
                double c = cs[t], s = ss[t];
                // the frame's origin is upstream of the middle of the river
                double cx = p[0] - c * MID, cz = p[1] - s * MID;
                if (!biomeAll(cx, cz, c, s, sea, generator, random)) {
                    continue;
                }
                ValeGen.Frame F = ValeGen.valleyFrame(cx, cz, seeds[t], natural);
                if (F.base < sea - 2) {
                    continue;
                }
                int hi = Integer.MIN_VALUE;
                for (int u = ValeGen.UMIN; u <= ValeGen.UMAX; u += 34) {
                    for (int v = -66; v <= 66; v += 33) {
                        hi = Math.max(hi, natural.applyAsInt(F.worldX(u, v), F.worldZ(u, v)));
                    }
                }
                if (hi <= F.base + 45) {
                    return F;
                }
            }
        }
        return null;
    }

    private static boolean biomeAll(double cx, double cz, double c, double s, int sea, ChunkGenerator generator, RandomState random) {
        int u0 = ValeGen.UMIN - ValeGen.MARGIN, u1 = ValeGen.UMAX + ValeGen.MARGIN;
        for (int k = 0; k <= 10; k++) {
            double u = u0 + (u1 - u0) * k / 10.0;
            for (int v = -88; v <= 88; v += 44) {
                int x = (int) Math.floor(cx + c * u - s * v), z = (int) Math.floor(cz + s * u + c * v);
                if (!biomeAt(generator, random, x, sea, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- the pieces -----------------------------------------------------------------------------------------------

    // The preview's filter, plus the game's: the biome all around as far as the piece reaches, and outside the valleys
    // dry land.
    private static boolean ok(ValeGen.Obj o, List<ValeGen.Frame> frames, IntBinaryOperator natural, ChunkGenerator generator,
            RandomState random, int sea) {
        boolean inValley = false;
        for (ValeGen.Frame F : frames) {
            double[] uv = ValeGen.toLocal(F, o.x(), o.z());
            double e = ValeGen.valleyOut(F, uv[0], uv[1]);
            if (o.kind() != ValeGen.Kind.TREE && o.kind() != ValeGen.Kind.BOULDER && e < 24) {
                return false;
            }
            ValeGen.Column c = ValeGen.valleyColumn(F, o.x(), o.z(), natural);
            if (c != null) {
                if (c.top != ValeGen.GRASS || c.kind == ValeGen.K.STATUE) {
                    return false;
                }
                inValley = true;
            }
            for (ValeGen.Statue st : F.statues) {
                if (sq(o.x() - st.ox()) + sq(o.z() - st.oz()) < 196) {
                    return false;
                }
            }
        }
        int g = natural.applyAsInt(o.x(), o.z());
        if (!inValley && g < sea) {
            return false;
        }
        int y = Math.max(g, sea);
        if (!biomeAt(generator, random, o.x(), y, o.z())) {
            return false;
        }
        int reach = o.reach();
        for (int a = 0; a < 8; a++) {
            int x = o.x() + (int) Math.round(Math.cos(a * Math.PI / 4) * reach);
            int z = o.z() + (int) Math.round(Math.sin(a * Math.PI / 4) * reach);
            if (!biomeAt(generator, random, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static int[] piece(ValeGen.Obj o, int gy, IntBinaryOperator ground) {
        long key = ((long) o.seed() << 20) ^ ((long) o.x() * 341873128712L) ^ ((long) o.z() * 132897987541L) ^ ((long) gy << 8)
                ^ o.kind().ordinal();
        try {
            return PIECES.get(key, () -> ValeGen.build(o, gy, ground).pack());
        } catch (ExecutionException e) {
            return new int[0];
        }
    }

    // Both statues of a valley, their voxels split by chunk.
    private static Map<Long, int[]> statues(ValeGen.Frame F) {
        long key = ((long) F.seed << 32) ^ ((long) F.worldX(0, 0) * 341873128712L) ^ ((long) F.worldZ(0, 0) * 132897987541L);
        try {
            return STATUES.get(key, () -> {
                Map<Long, List<int[]>> parts = new HashMap<>();
                for (ValeGen.Statue st : F.statues) {
                    DesertGen.Vox p = new DesertGen.Vox();
                    ValeGen.statue(p, st.ox(), F.base + ValeGen.HF + 4, st.oz(), st.sx(), st.sz(), st.fx(), st.fz(), st.who(),
                            F.seed + st.who());
                    int[] v = p.pack();
                    for (int i = 0; i < v.length; i += 4) {
                        long ck = ChunkPos.asLong(v[i] >> 4, v[i + 2] >> 4);
                        parts.computeIfAbsent(ck, q -> new ArrayList<>()).add(new int[] {v[i], v[i + 1], v[i + 2], v[i + 3]});
                    }
                }
                Map<Long, int[]> out = new HashMap<>();
                for (Map.Entry<Long, List<int[]>> e : parts.entrySet()) {
                    List<int[]> l = e.getValue();
                    int[] a = new int[l.size() * 4];
                    for (int i = 0; i < l.size(); i++) {
                        System.arraycopy(l.get(i), 0, a, i * 4, 4);
                    }
                    out.put(e.getKey(), a);
                }
                return out;
            });
        } catch (ExecutionException e) {
            return Map.of();
        }
    }

    // ---- blocks ---------------------------------------------------------------------------------------------------

    private static BlockState vine(int dx, int dz) {
        BlockState v = Blocks.VINE.defaultBlockState();
        if (dx == 1) {
            return v.setValue(VineBlock.EAST, true);
        }
        if (dx == -1) {
            return v.setValue(VineBlock.WEST, true);
        }
        if (dz == 1) {
            return v.setValue(VineBlock.SOUTH, true);
        }
        return v.setValue(VineBlock.NORTH, true);
    }

    private static BlockState leaves(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState block(int t) {
        return switch (t) {
            case ValeGen.STONE -> Blocks.STONE.defaultBlockState();
            case ValeGen.ANDESITE -> Blocks.ANDESITE.defaultBlockState();
            case ValeGen.TUFF -> Blocks.TUFF.defaultBlockState();
            case ValeGen.COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
            case ValeGen.MOSSY -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case ValeGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case ValeGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case ValeGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case ValeGen.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case ValeGen.SAND -> Blocks.SAND.defaultBlockState();
            case ValeGen.WATER -> Blocks.WATER.defaultBlockState();
            case ValeGen.FALL -> Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock();
            case ValeGen.LOG -> Blocks.OAK_LOG.defaultBlockState();
            case ValeGen.LEAVES -> leaves(Blocks.OAK_LEAVES);
            case ValeGen.PINE_LOG -> Blocks.SPRUCE_LOG.defaultBlockState();
            case ValeGen.PINE -> leaves(Blocks.SPRUCE_LEAVES);
            case ValeGen.STATUE -> ModBlocks.STATUE_STONE.get().defaultBlockState();
            case ValeGen.STATUE_ALT -> ModBlocks.STATUE_STONE_DARK.get().defaultBlockState();
            case ValeGen.STATUE_MOSS -> ModBlocks.STATUE_STONE_MOSS.get().defaultBlockState();
            case ValeGen.STATUE_DARK -> ModBlocks.STATUE_STONE_SHADOW.get().defaultBlockState();
            case ValeGen.COARSE -> Blocks.COARSE_DIRT.defaultBlockState();
            case ValeGen.LEAVES_DARK -> leaves(Blocks.DARK_OAK_LEAVES);
            case ValeGen.VINE -> Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true);
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
