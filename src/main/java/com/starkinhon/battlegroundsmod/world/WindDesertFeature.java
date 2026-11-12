package com.starkinhon.battlegroundsmod.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Deserto do Vento (Stark 2026-11-11, "que perfeição", from the approved preview), done like the forests'
// features: once per chunk of the biome, in this order (the preview's):
// 1. the dunes: DesertGen.duneHeight's sand over the ground, whole blocks and a top layer of eighths (camada_de_areia),
//    fading out toward the biome's edge so the dunes never end in a wall; where there's no dune, the ground's top is
//    sand, cut sandstone plates or red sand (DesertGen.groundTop);
// 2. the part of every mesa, arch, hoodoo group, mushroom rock, boulder, skeleton and dead tree that reaches the chunk
//    (DesertGen's cells, the same seed in every chunk so the pieces meet): rock takes the place of the sand, bones and
//    wood only go into the air (the dunes bury them as in the preview); a rock's foot is filled down to the ground where
//    the land falls away under it;
// 3. dead bushes and dry grass on the hard ground.
// The terrain's own shape stays vanilla's (the heights come from the generator's noise, so every chunk agrees). Writes
// only inside its own chunk.
public final class WindDesertFeature extends Feature<NoneFeatureConfiguration> {
    private static final Cache<Long, int[]> PIECES = CacheBuilder.newBuilder().maximumSize(300).build();
    // the fade grid: one point every FADE blocks, the dunes' height scaled between them
    private static final int FADE = 8;

    public WindDesertFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        int seed = worldKey ^ 0x5EED0D2E;
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator height = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Map<Long, Double> fades = new HashMap<>();

        // 1. Dunes and the hard ground.
        boolean[][] inside = new boolean[16][16];
        double[][] dune = new double[16][16];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int foot = height.applyAsInt(x, z), g = foot - 1;
                if (!level.getBiome(pos.set(x, foot, z)).is(ModWorldgen.DESERTO_DO_VENTO)) {
                    continue;
                }
                inside[x - x0][z - z0] = true;
                BlockState ground = level.getBlockState(pos.set(x, g, z));
                if (!ground.getFluidState().isEmpty() || !(ground.is(BlockTags.SAND) || ground.is(Blocks.SANDSTONE))) {
                    continue;
                }
                double h = DesertGen.duneHeight(x, z, seed) * fade(fades, generator, random, x, z);
                dune[x - x0][z - z0] = h;
                int full = (int) Math.floor(h), l = (int) Math.round((h - full) * 8);
                if (full == 0 && l == 0) {
                    if (ground.is(Blocks.SAND)) {
                        level.setBlock(pos.set(x, g, z), ground(DesertGen.groundTop(x, z, seed)), 2);
                    }
                    continue;
                }
                for (int k = 1; k <= full; k++) {
                    if (!fill(level, pos.set(x, g + k, z), Blocks.SAND.defaultBlockState())) {
                        break;
                    }
                }
                if (l == 8) {
                    fill(level, pos.set(x, g + full + 1, z), Blocks.SAND.defaultBlockState());
                } else if (l > 0) {
                    fill(level, pos.set(x, g + full + 1, z), layer(l));
                }
            }
        }

        // 2. Rock, bones and dead trees.
        List<DesertGen.Obj> objs = DesertGen.objectsNear(x0, z0, x1, z1, seed, o -> good(o, generator, random, height));
        for (DesertGen.Obj o : objs) {
            int foot = height.applyAsInt(o.x(), o.z());
            int[] vox = voxels(o, foot);
            boolean rock = o.kind().rock();
            for (int i = 0; i < vox.length; i += 4) {
                int x = vox[i], y = vox[i + 1], z = vox[i + 2];
                if (x < x0 || x > x1 || z < z0 || z > z1 || y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                    continue;
                }
                pos.set(x, y, z);
                if (!put(level, pos, vox[i + 3], rock)) {
                    continue;
                }
                // a rock's lowest blocks go down to the ground where the land falls away
                if (rock && y == foot - 1 && !DesertGen.isLayer(vox[i + 3]) && vox[i + 3] != DesertGen.SAND) {
                    for (int k = 1; k <= 12; k++) {
                        BlockState below = level.getBlockState(pos.set(x, y - k, z));
                        if (!below.canBeReplaced() || !below.getFluidState().isEmpty()) {
                            break;
                        }
                        level.setBlock(pos, Blocks.SANDSTONE.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 3. Plants on the hard ground.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!inside[x - x0][z - z0] || dune[x - x0][z - z0] > 0.05) {
                    continue;
                }
                int foot = height.applyAsInt(x, z);
                if (!level.getBlockState(pos.set(x, foot, z)).isAir()) {
                    continue;
                }
                byte deco = DesertGen.groundDeco(x, z, seed);
                if (deco == 0) {
                    continue;
                }
                BlockState under = level.getBlockState(pos.set(x, foot - 1, z));
                pos.set(x, foot, z);
                if (deco == DesertGen.DEAD_BUSH && under.is(BlockTags.SAND)) {
                    level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), 2);
                } else if (deco == DesertGen.DRY_GRASS && (under.is(BlockTags.SAND) || under.is(Blocks.CUT_SANDSTONE))) {
                    level.setBlock(pos, ModBlocks.DRY_GRASS.get().defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    // Sand of the dunes into air and plants only (never over water, never into the land).
    private static boolean fill(WorldGenLevel level, BlockPos pos, BlockState state) {
        BlockState there = level.getBlockState(pos);
        if (!there.canBeReplaced() || !there.getFluidState().isEmpty()) {
            return false;
        }
        level.setBlock(pos, state, 2);
        return true;
    }

    private static BlockState layer(int eighths) {
        return ModBlocks.SAND_LAYER.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS, eighths);
    }

    // How far into the biome, 0 at its edge to 1 well inside: on a grid every FADE blocks, the share of the biome among
    // 9 points around (the middle and 8 at 16 blocks), eased; between grid points bilinear, so the dunes sink smoothly.
    private static double fade(Map<Long, Double> fades, ChunkGenerator generator, RandomState random, int x, int z) {
        int gx = Math.floorDiv(x, FADE), gz = Math.floorDiv(z, FADE);
        double tx = (x - gx * FADE) / (double) FADE, tz = (z - gz * FADE) / (double) FADE;
        double a = gridFade(fades, generator, random, gx, gz), b = gridFade(fades, generator, random, gx + 1, gz);
        double c = gridFade(fades, generator, random, gx, gz + 1), d = gridFade(fades, generator, random, gx + 1, gz + 1);
        double top = a + (b - a) * tx, bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    private static double gridFade(Map<Long, Double> fades, ChunkGenerator generator, RandomState random, int gx, int gz) {
        long key = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
        Double cached = fades.get(key);
        if (cached != null) {
            return cached;
        }
        int x = gx * FADE, z = gz * FADE, y = generator.getSeaLevel() + 8, in = 0;
        if (biomeAt(generator, random, x, y, z)) {
            in++;
        }
        for (int a = 0; a < 8; a++) {
            if (biomeAt(generator, random, x + (int) Math.round(Math.cos(a * Math.PI / 4) * 16), y,
                    z + (int) Math.round(Math.sin(a * Math.PI / 4) * 16))) {
                in++;
            }
        }
        double t = Math.max(0, Math.min(1, (in / 9.0 - 0.55) / 0.45));
        double v = t * t * (3 - 2 * t);
        fades.put(key, v);
        return v;
    }

    private static int[] voxels(DesertGen.Obj o, int foot) {
        long key = ((long) o.seed() << 20) ^ ((long) o.x() * 341873128712L) ^ ((long) o.z() * 132897987541L) ^ ((long) foot << 8)
                ^ o.kind().ordinal();
        try {
            return PIECES.get(key, () -> DesertGen.build(o, foot).pack());
        } catch (ExecutionException e) {
            return new int[0];
        }
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.DESERTO_DO_VENTO);
    }

    // Is the spot good: dry land above the sea, the biome all around as far as the thing reaches and, under a mesa or an
    // arch, ground not too steep.
    private static boolean good(DesertGen.Obj o, ChunkGenerator generator, RandomState random, IntBinaryOperator height) {
        int y = height.applyAsInt(o.x(), o.z());
        if (y <= generator.getSeaLevel() + 1 || !biomeAt(generator, random, o.x(), y, o.z())) {
            return false;
        }
        int reach = o.kind().reach();
        for (int a = 0; a < 8; a++) {
            int x = o.x() + (int) Math.round(Math.cos(a * Math.PI / 4) * reach);
            int z = o.z() + (int) Math.round(Math.sin(a * Math.PI / 4) * reach);
            if (!biomeAt(generator, random, x, y, z)) {
                return false;
            }
        }
        if (o.kind() == DesertGen.Kind.MESA || o.kind() == DesertGen.Kind.ARCH || o.kind() == DesertGen.Kind.SKELETON) {
            int lo = y, hi = y, d = reach / 2;
            for (int a = 0; a < 4; a++) {
                int h = height.applyAsInt(o.x() + (a % 2 == 0 ? d : -d), o.z() + (a < 2 ? d : -d));
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            if (hi - lo > 6 || lo <= generator.getSeaLevel()) {
                return false;
            }
        }
        return true;
    }

    // A voxel into the world. Rock takes the place of sand, dirt, stone and plants (never water); bones and wood only go
    // into air, plants and the dunes' top layer. Returns whether it went in.
    private static boolean put(WorldGenLevel level, BlockPos pos, int type, boolean rock) {
        BlockState there = level.getBlockState(pos);
        if (!there.getFluidState().isEmpty()) {
            return false;
        }
        if (rock) {
            if (!(there.canBeReplaced() || there.is(BlockTags.SAND) || there.is(BlockTags.DIRT) || there.is(BlockTags.TERRACOTTA)
                    || there.is(BlockTags.BASE_STONE_OVERWORLD) || there.is(Blocks.SANDSTONE) || there.is(Blocks.SMOOTH_SANDSTONE)
                    || there.is(Blocks.CUT_SANDSTONE) || there.is(Blocks.RED_SANDSTONE) || there.is(ModBlocks.SAND_LAYER.get()))) {
                return false;
            }
            // the talus's sand doesn't bury another rock's stone
            if ((type == DesertGen.SAND || DesertGen.isLayer(type)) && !(there.canBeReplaced() || there.is(ModBlocks.SAND_LAYER.get()))) {
                return false;
            }
        } else if (!(there.canBeReplaced() || there.is(ModBlocks.SAND_LAYER.get()))) {
            return false;
        }
        level.setBlock(pos, DesertGen.isLayer(type) ? layer(type - DesertGen.LAYER) : ground((byte) type), 2);
        return true;
    }

    private static BlockState ground(byte type) {
        return switch (type) {
            case DesertGen.SAND -> Blocks.SAND.defaultBlockState();
            case DesertGen.SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
            case DesertGen.SMOOTH -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case DesertGen.RED_SAND -> Blocks.RED_SAND.defaultBlockState();
            case DesertGen.T_ORANGE -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            case DesertGen.T_YELLOW -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            case DesertGen.T_WHITE -> Blocks.WHITE_TERRACOTTA.defaultBlockState();
            case DesertGen.T_BROWN -> Blocks.BROWN_TERRACOTTA.defaultBlockState();
            case DesertGen.RED_SS -> Blocks.RED_SANDSTONE.defaultBlockState();
            case DesertGen.BONE -> Blocks.BONE_BLOCK.defaultBlockState();
            case DesertGen.DEADWOOD -> Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState();
            case DesertGen.DEAD_BUSH -> Blocks.DEAD_BUSH.defaultBlockState();
            case DesertGen.DRY_GRASS -> ModBlocks.DRY_GRASS.get().defaultBlockState();
            case DesertGen.CRACKED -> Blocks.CUT_SANDSTONE.defaultBlockState();
            default -> Blocks.SAND.defaultBlockState();
        };
    }
}
