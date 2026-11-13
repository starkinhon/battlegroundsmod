package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Colônia do Jogo do Abate (Stark 2026-11-12; port released 2026-11-13): pieces of destroyed Tokyo standing in the
// biome, exactly the preview's two pieces (ColoniaGen is its line-by-line port, checked block for block against the
// preview in 8 pieces):
// - the colony: the preview's "Colônia com a barreira" (half 120), with the big fight crater and the toppled building
//   in the middle; the dark dome over it is drawn by the client;
// - the ruins: the preview's "Pedaço das ruínas" (half 96), the same broken city without the crater.
// One piece per cell of 360 (30% try the colony first, every cell ruins), on a spot with the biome around it.
// The piece sits on its own level (the terrain's average there): above it the terrain is cut away, below it filled with
// stone, and a skirt of 16 blends the land around into the city's level. Built once per piece and kept, split by chunk.
// Writes only inside its own chunk.
public final class ColoniaFeature extends Feature<NoneFeatureConfiguration> {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int SALT = 0x6A0800;
    private static final int CELL = 360;
    private static final int SKIRT = 16;
    private static final int CLEAR = 24; // how high above the old ground the cut goes (trees, hills' tops)
    private static final Cache<Long, Built> PIECES = CacheBuilder.newBuilder().maximumSize(12).build();
    private static final Cache<Long, int[]> SITES = CacheBuilder.newBuilder().maximumSize(4096).build();
    private static final int[] NONE = {};

    // A built piece already moved to its place, its voxels split by chunk.
    private record Built(Map<Long, int[]> byChunk) {
    }

    public ColoniaFeature(Codec<NoneFeatureConfiguration> codec) {
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
        int sea = generator.getSeaLevel() - 1;
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator ground = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;

        // The pieces reaching this chunk: {cx, cz, half, cityY, pieceSeed, dome}.
        List<int[]> pieces = new ArrayList<>();
        int reach = ColoniaGen.HALF + SKIRT;
        for (int ci = Math.floorDiv(x0 - reach, CELL); ci <= Math.floorDiv(x1 + reach, CELL); ci++) {
            for (int cj = Math.floorDiv(z0 - reach, CELL); cj <= Math.floorDiv(z1 + reach, CELL); cj++) {
                int[] s = site(ci, cj, worldKey, seed, sea, generator, random, ground, maxY);
                if (s != NONE && x1 >= s[0] - s[2] - SKIRT && x0 <= s[0] + s[2] - 1 + SKIRT && z1 >= s[1] - s[2] - SKIRT
                        && z0 <= s[1] + s[2] - 1 + SKIRT) {
                    pieces.add(s);
                }
            }
        }
        if (pieces.isEmpty()) {
            return false;
        }

        // 1. The land: cut above the city's level and filled below it inside the piece, blended around it.
        for (int[] s : pieces) {
            int cx = s[0], cz = s[1], half = s[2], cityY = s[3];
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    int d = Math.max(Math.max(cx - half - x, x - (cx + half - 1)), Math.max(cz - half - z, z - (cz + half - 1)));
                    if (d > SKIRT) {
                        continue;
                    }
                    int h = ground.applyAsInt(x, z);
                    if (d <= 0) {
                        clear(level, pos, x, z, cityY + 1, Math.max(h, cityY) + CLEAR, maxY);
                        for (int y = Math.max(minY, h + 1); y <= cityY - 4; y++) {
                            level.setBlock(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                        }
                    } else {
                        double k = 1 - d / (double) (SKIRT + 1);
                        k = k * k * (3 - 2 * k);
                        int top = JjkGen.round(h + (cityY - h) * k);
                        clear(level, pos, x, z, top + 1, Math.max(h, top) + CLEAR, maxY);
                        for (int y = Math.max(minY, h + 1); y < top; y++) {
                            level.setBlock(pos.set(x, y, z), (top - y > 3 ? Blocks.STONE : Blocks.DIRT).defaultBlockState(), 2);
                        }
                        if (top != h && top >= minY && top <= maxY) {
                            level.setBlock(pos.set(x, top, z), (top <= sea ? Blocks.DIRT : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
                        }
                    }
                }
            }
        }

        // 2. The city itself.
        TemploGen.Vox w = new TemploGen.Vox(x0, z0, x1, z1);
        for (int[] s : pieces) {
            Built b = piece(s, worldKey);
            int[] v = b == null ? null : b.byChunk().get(ChunkPos.asLong(chunk.x, chunk.z));
            if (v == null) {
                continue;
            }
            for (int i = 0; i < v.length; i += 4) {
                w.force(v[i], v[i + 1], v[i + 2], (byte) v[i + 3]);
            }
        }

        // Into the world.
        w.each((x, y, z, t) -> {
            if (y < minY || y > maxY) {
                return;
            }
            pos.set(x, y, z);
            BlockState there = level.getBlockState(pos);
            if (there.is(Blocks.BEDROCK)) {
                return;
            }
            if (t == TemploGen.CARVE) {
                if (!there.isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                return;
            }
            if (t == ColoniaGen.FIRE) {
                // A column of fire: embers where it starts and a real fire over them (it never burns out on embers).
                if (w.get(x, y - 1, z) == ColoniaGen.FIRE) {
                    return;
                }
                level.setBlock(pos, JujutsuBlocks.EMBERS.get().defaultBlockState(), 2);
                if (y + 1 <= maxY && (w.get(x, y + 1, z) == 0 || w.get(x, y + 1, z) == ColoniaGen.FIRE)) {
                    level.setBlock(pos.set(x, y + 1, z), Blocks.FIRE.defaultBlockState(), 2);
                }
                return;
            }
            boolean open = there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty();
            if (t == ColoniaGen.WEED && !open) {
                return; // the weeds only grow into the air, as the preview's set
            }
            level.setBlock(pos, block(t), 2);
        });
        return true;
    }

    // Cuts the land away from y0 to y1 (whatever is there: hills, trees, water).
    private static void clear(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y0, int y1, int maxY) {
        for (int y = y0; y <= Math.min(y1, maxY); y++) {
            BlockState b = level.getBlockState(pos.set(x, y, z));
            if (!b.isAir() && !b.is(Blocks.BEDROCK)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.COLONIA_DO_JOGO_DO_ABATE);
    }

    private static long key(int ci, int cj, int worldKey) {
        return ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
    }

    // A cell's piece: a colony (30%) or ruins (45%) near the cell's middle, so that two pieces never touch. Good only
    // with the biome all around it and the terrain under it within 16 of its average (the city's level).
    // {cx, cz, half, cityY, pieceSeed, dome} or NONE.
    private static int[] site(int ci, int cj, int worldKey, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator ground, int maxY) {
        try {
            return SITES.get(key(ci, cj, worldKey), () -> {
                boolean dome = JjkGen.hash3(ci, 111, cj, seed) < 0.3;
                // The biggest piece that fits (the colony, or ruins of 96, then of 64), each tried at 9 spots of the
                // cell, so a patch of the biome smaller than the cell still gets its piece.
                for (int half : dome ? new int[] {ColoniaGen.HALF, 96, 64} : new int[] {96, 64}) {
                    for (int t = 0; t < 9; t++) {
                        int[] s = fit(ci, cj, t, half, half == ColoniaGen.HALF, seed, sea, generator, random, ground, maxY);
                        if (s != NONE) {
                            return s;
                        }
                    }
                }
                return NONE;
            });
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    // A piece of the given half in a cell: good with the biome at its middle and at 7 of 8 points around it, and the
    // terrain under it within 40 of its average (the city's level). {cx, cz, half, cityY, pieceSeed, dome} or NONE.
    // Spot t: 0 is a random spot of the cell, 1-8 the cell's middle pushed to its edges and corners.
    private static int[] fit(int ci, int cj, int t, int half, boolean dome, int seed, int sea, ChunkGenerator generator,
            RandomState random, IntBinaryOperator ground, int maxY) {
        int slack = CELL / 2 - half - SKIRT;
        int cx, cz;
        if (t == 0) {
            cx = ci * CELL + CELL / 2 - slack + JjkGen.floor(JjkGen.hash3(ci, 112, cj, seed) * (2 * slack + 1));
            cz = cj * CELL + CELL / 2 - slack + JjkGen.floor(JjkGen.hash3(ci, 113, cj, seed) * (2 * slack + 1));
        } else {
            double a = (t - 1) * Math.PI / 4;
            cx = ci * CELL + CELL / 2 + JjkGen.round(Math.cos(a) * slack);
            cz = cj * CELL + CELL / 2 + JjkGen.round(Math.sin(a) * slack);
        }
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, sum = 0, n = 0;
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                int x = cx + a * (half - 4) / 2, z = cz + b * (half - 4) / 2;
                int h = ground.applyAsInt(x, z);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                sum += h;
                n++;
            }
        }
        int cityY = JjkGen.round(sum / (double) n);
        if (hi - cityY > 40 || cityY - lo > 40 || cityY < sea + 2 || cityY + 80 > maxY) {
            return NONE;
        }
        if (!biomeAt(generator, random, cx, cityY, cz)) {
            return NONE;
        }
        int miss = 0;
        for (int a = 0; a < 8; a++) {
            int x = cx + JjkGen.round(Math.cos(a * Math.PI / 4) * half);
            int z = cz + JjkGen.round(Math.sin(a * Math.PI / 4) * half);
            if (!biomeAt(generator, random, x, cityY, z) && ++miss > 1) {
                return NONE;
            }
        }
        return new int[] {cx, cz, half, cityY, JjkGen.hashInt(ci, cj, 111, seed), dome ? 1 : 0};
    }

    // The colony's centers near a spot (for the client's dome and the ambience): {cx, cz, cityY} of each colony whose
    // cell is within one of the spot's cell. Only cells already looked at by the generator are known.
    public static List<int[]> knownColonies(int x, int z, long worldSeed) {
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        List<int[]> out = new ArrayList<>();
        int ci0 = Math.floorDiv(x, CELL), cj0 = Math.floorDiv(z, CELL);
        for (int ci = ci0 - 1; ci <= ci0 + 1; ci++) {
            for (int cj = cj0 - 1; cj <= cj0 + 1; cj++) {
                int[] s = SITES.getIfPresent(key(ci, cj, worldKey));
                if (s != null && s != NONE && s[5] == 1) {
                    out.add(new int[] {s[0], s[1], s[3]});
                }
            }
        }
        return out;
    }

    private static Built piece(int[] s, int worldKey) {
        long k = ((long) s[0] << 32) ^ (s[1] & 0xFFFFFFFFL) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
        try {
            return PIECES.get(k, () -> {
                // The preview's colonyPiece(seed) and ruinsPiece(seed) = colony(seed + 1, 96, false).
                LOGGER.info("[jjk] Colônia: {} de meio {} em {} {} {}", s[5] == 1 ? "colônia" : "ruínas", s[2], s[0], s[3], s[1]);
                TemploGen.Vox v = s[5] == 1 ? ColoniaGen.colony(s[4], ColoniaGen.HALF, true) : ColoniaGen.colony(s[4] + 1, s[2], false);
                int[] p = v.pack();
                int dx = s[0], dy = s[3] - ColoniaGen.GROUND, dz = s[1];
                Map<Long, int[]> count = new HashMap<>();
                for (int i = 0; i < p.length; i += 4) {
                    long c = ChunkPos.asLong((p[i] + dx) >> 4, (p[i + 2] + dz) >> 4);
                    count.computeIfAbsent(c, q -> new int[1])[0]++;
                }
                Map<Long, int[]> by = new HashMap<>();
                Map<Long, int[]> at = new HashMap<>();
                for (Map.Entry<Long, int[]> e : count.entrySet()) {
                    by.put(e.getKey(), new int[e.getValue()[0] * 4]);
                    at.put(e.getKey(), new int[1]);
                }
                for (int i = 0; i < p.length; i += 4) {
                    long c = ChunkPos.asLong((p[i] + dx) >> 4, (p[i + 2] + dz) >> 4);
                    int[] dst = by.get(c);
                    int[] j = at.get(c);
                    dst[j[0]] = p[i] + dx;
                    dst[j[0] + 1] = p[i + 1] + dy;
                    dst[j[0] + 2] = p[i + 2] + dz;
                    dst[j[0] + 3] = p[i + 3];
                    j[0] += 4;
                }
                return new Built(by);
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    static BlockState block(int t) {
        return switch (t) {
            case ColoniaGen.STONE -> Blocks.STONE.defaultBlockState();
            case ColoniaGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case ColoniaGen.ASPHALT -> JujutsuBlocks.ASPHALT.get().defaultBlockState();
            case ColoniaGen.ASPHALT_CRACK -> JujutsuBlocks.ASPHALT_CRACKED.get().defaultBlockState();
            case ColoniaGen.LINE -> JujutsuBlocks.ROAD_LINE.get().defaultBlockState();
            case ColoniaGen.SIDEWALK -> JujutsuBlocks.SIDEWALK.get().defaultBlockState();
            case ColoniaGen.CONCRETE -> JujutsuBlocks.COLONY_CONCRETE.get().defaultBlockState();
            case ColoniaGen.CONCRETE_DARK -> JujutsuBlocks.COLONY_CONCRETE_DARK.get().defaultBlockState();
            case ColoniaGen.CONCRETE_LIGHT -> JujutsuBlocks.COLONY_CONCRETE_LIGHT.get().defaultBlockState();
            case ColoniaGen.WINDOW -> JujutsuBlocks.COLONY_WINDOW.get().defaultBlockState();
            case ColoniaGen.WINDOW_LIT -> JujutsuBlocks.COLONY_WINDOW_LIT.get().defaultBlockState();
            case ColoniaGen.FLOOR -> JujutsuBlocks.FLOOR_SLAB.get().defaultBlockState();
            case ColoniaGen.REBAR -> JujutsuBlocks.REBAR.get().defaultBlockState();
            case ColoniaGen.RUBBLE -> JujutsuBlocks.RUBBLE.get().defaultBlockState();
            case ColoniaGen.SCORCH -> JujutsuBlocks.JOGO_BASALT_DARK.get().defaultBlockState();
            case ColoniaGen.FIRE -> JujutsuBlocks.EMBERS.get().defaultBlockState();
            case ColoniaGen.CAR_RED -> Blocks.RED_CONCRETE.defaultBlockState();
            case ColoniaGen.CAR_WHITE -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case ColoniaGen.CAR_BLUE -> Blocks.BLUE_CONCRETE.defaultBlockState();
            case ColoniaGen.CAR_BLACK -> Blocks.BLACK_CONCRETE.defaultBlockState();
            case ColoniaGen.TIRE -> JujutsuBlocks.TIRE.get().defaultBlockState();
            case ColoniaGen.BARRIER_CONE -> JujutsuBlocks.TRAFFIC_CONE.get().defaultBlockState();
            case ColoniaGen.FENCE -> JujutsuBlocks.WORK_FENCE.get().defaultBlockState();
            case ColoniaGen.SIGN -> JujutsuBlocks.WARNING_SIGN.get().defaultBlockState();
            case ColoniaGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case ColoniaGen.WEED -> JujutsuBlocks.STREET_WEEDS.get().defaultBlockState();
            case ColoniaGen.GLASS_BITS -> Blocks.GLASS_PANE.defaultBlockState();
            case ColoniaGen.TILE -> JujutsuBlocks.LOT_TILES.get().defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
