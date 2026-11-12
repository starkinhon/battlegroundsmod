package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Monte Myoboku (Stark 2026-11-12, "literalmente perfeito", from the approved preview), done like the other Naruto
// features: once per chunk of the biome, the preview's myobokuPiece step by step (MyobokuGen is its line-by-line port):
// 1. the ground: the grass on top becomes grass or moss in patches (MyobokuGen.groundTop);
// 2. the part of every spire group, oil pond, stone toad, giant flower, giant leaf plant and shrub that reaches the chunk
//    (the same seed in every chunk so the pieces meet), each deciding alone whether it goes in, with the preview's rule
//    of who gives way, which doesn't depend on which chunk asks. In the preview's order: spires, ponds, toads, then the
//    plants, which only grow into the air.
// Writes only inside its own chunk.
public final class MyobokuFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0x3A0B0C;
    private static final Cache<Long, int[]> PIECES = CacheBuilder.newBuilder().maximumSize(400).build();

    public MyobokuFeature(Codec<NoneFeatureConfiguration> codec) {
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
        // the natural top solid block
        IntBinaryOperator ground = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;

        // 1. The ground.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = ground.applyAsInt(x, z);
                if (!level.getBiome(pos.set(x, g + 1, z)).is(ModWorldgen.MONTE_MYOBOKU)) {
                    continue;
                }
                if (!level.getBlockState(pos.set(x, g + 1, z)).getFluidState().isEmpty()) {
                    continue;
                }
                if (level.getBlockState(pos.set(x, g, z)).is(Blocks.GRASS_BLOCK) && MyobokuGen.groundTop(x, z, seed) == MyobokuGen.MOSS) {
                    level.setBlock(pos, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                }
            }
        }

        // 2. The pieces. The candidates come from a wider box than the chunk, so every piece that could make one near
        // the chunk give way is seen here too.
        int wide = 34;
        List<MyobokuGen.Obj> cands = new ArrayList<>();
        for (MyobokuGen.Obj o : MyobokuGen.objectsNear(x0 - wide, z0 - wide, x1 + wide, z1 + wide, seed)) {
            if (good(o, generator, random, ground, sea)) {
                cands.add(o);
            }
        }
        List<MyobokuGen.Obj> mine = new ArrayList<>();
        for (MyobokuGen.Obj o : cands) {
            if (o.x() + o.reach() < x0 || o.x() - o.reach() > x1 || o.z() + o.reach() < z0 || o.z() - o.reach() > z1) {
                continue;
            }
            if (!MyobokuGen.yields(o, cands)) {
                mine.add(o);
            }
        }
        mine.sort(Comparator.<MyobokuGen.Obj>comparingInt(o -> o.kind().order).thenComparingInt(MyobokuGen.Obj::x)
                .thenComparingInt(MyobokuGen.Obj::z));
        // the vines go in last, once the walls they hang on are there
        List<BlockPos> vines = new ArrayList<>();
        for (MyobokuGen.Obj o : mine) {
            int[] vox = piece(o, ground.applyAsInt(o.x(), o.z()), ground);
            boolean plant = o.kind().plant;
            for (int i = 0; i < vox.length; i += 4) {
                int x = vox[i], y = vox[i + 1], z = vox[i + 2], t = vox[i + 3];
                if (x < x0 || x > x1 || z < z0 || z > z1 || y < minY || y > maxY) {
                    continue;
                }
                pos.set(x, y, z);
                if (t == MyobokuGen.CARVE) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    continue;
                }
                BlockState there = level.getBlockState(pos);
                boolean open = there.isAir() || (there.canBeReplaced() && there.getFluidState().isEmpty());
                if ((plant || t == MyobokuGen.VINE) && !open) {
                    continue;
                }
                if (t == MyobokuGen.VINE) {
                    vines.add(pos.immutable());
                    continue;
                }
                level.setBlock(pos, block(t), 2);
            }
        }
        for (BlockPos p : vines) {
            if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced() && level.getBlockState(p).getFluidState().isEmpty()) {
                level.setBlock(p, vine(level, p), 2);
            }
        }
        return true;
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.MONTE_MYOBOKU);
    }

    // The game's filter (the preview has the whole square): dry land, the biome all around as far as the piece reaches,
    // and for a spire group or a pond ground not too steep.
    private static boolean good(MyobokuGen.Obj o, ChunkGenerator generator, RandomState random, IntBinaryOperator ground, int sea) {
        int g = ground.applyAsInt(o.x(), o.z());
        if (g < sea || !biomeAt(generator, random, o.x(), g, o.z())) {
            return false;
        }
        int reach = o.reach();
        for (int a = 0; a < 8; a++) {
            int x = o.x() + (int) Math.round(Math.cos(a * Math.PI / 4) * reach);
            int z = o.z() + (int) Math.round(Math.sin(a * Math.PI / 4) * reach);
            if (!biomeAt(generator, random, x, g, z)) {
                return false;
            }
        }
        int d = o.kind() == MyobokuGen.Kind.SPIRE ? 8 : o.kind() == MyobokuGen.Kind.POOL ? 7 : 0;
        if (d > 0) {
            int lo = g, hi = g;
            for (int a = 0; a < 8; a++) {
                int h = ground.applyAsInt(o.x() + (int) Math.round(Math.cos(a * Math.PI / 4) * d),
                        o.z() + (int) Math.round(Math.sin(a * Math.PI / 4) * d));
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            if (hi - lo > (o.kind() == MyobokuGen.Kind.POOL ? 3 : 8)) {
                return false;
            }
        }
        return true;
    }

    private static int[] piece(MyobokuGen.Obj o, int gy, IntBinaryOperator ground) {
        long key = ((long) o.seed() << 20) ^ ((long) o.x() * 341873128712L) ^ ((long) o.z() * 132897987541L) ^ ((long) gy << 8)
                ^ o.kind().ordinal();
        try {
            return PIECES.get(key, () -> MyobokuGen.build(o, gy, ground).pack());
        } catch (ExecutionException e) {
            return new int[0];
        }
    }

    // A vine faces the solid block next to it (the spire's wall), or hangs free under a ledge.
    private static BlockState vine(WorldGenLevel level, BlockPos pos) {
        BlockState v = Blocks.VINE.defaultBlockState();
        if (level.getBlockState(pos.east()).isSolid()) {
            return v.setValue(VineBlock.EAST, true);
        }
        if (level.getBlockState(pos.west()).isSolid()) {
            return v.setValue(VineBlock.WEST, true);
        }
        if (level.getBlockState(pos.south()).isSolid()) {
            return v.setValue(VineBlock.SOUTH, true);
        }
        if (level.getBlockState(pos.north()).isSolid()) {
            return v.setValue(VineBlock.NORTH, true);
        }
        return v.setValue(VineBlock.UP, true);
    }

    private static BlockState block(int t) {
        return switch (t) {
            case MyobokuGen.STONE -> ModBlocks.SPIRE_STONE.get().defaultBlockState();
            case MyobokuGen.STONE_DARK -> ModBlocks.SPIRE_STONE_DARK.get().defaultBlockState();
            case MyobokuGen.STONE_LIGHT -> ModBlocks.SPIRE_STONE_LIGHT.get().defaultBlockState();
            case MyobokuGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case MyobokuGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case MyobokuGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case MyobokuGen.WATER -> Blocks.WATER.defaultBlockState();
            case MyobokuGen.STEM -> ModBlocks.GIANT_STALK.get().defaultBlockState();
            case MyobokuGen.LEAF -> ModBlocks.GIANT_LEAF.get().defaultBlockState();
            case MyobokuGen.LEAF_YELLOW -> ModBlocks.GIANT_LEAF_YELLOW.get().defaultBlockState();
            case MyobokuGen.LEAF_DARK -> ModBlocks.GIANT_LEAF_DARK.get().defaultBlockState();
            case MyobokuGen.PETAL -> ModBlocks.GIANT_PETAL.get().defaultBlockState();
            case MyobokuGen.PETAL_TIP -> ModBlocks.GIANT_PETAL_TIP.get().defaultBlockState();
            case MyobokuGen.CORE -> ModBlocks.FLOWER_CORE.get().defaultBlockState();
            case MyobokuGen.FROG -> ModBlocks.STATUE_STONE.get().defaultBlockState();
            case MyobokuGen.FROG_MOSS -> ModBlocks.STATUE_STONE_MOSS.get().defaultBlockState();
            case MyobokuGen.FROG_DARK -> ModBlocks.STATUE_STONE_SHADOW.get().defaultBlockState();
            case MyobokuGen.LILY -> Blocks.LILY_PAD.defaultBlockState();
            case MyobokuGen.CLAY -> Blocks.CLAY.defaultBlockState();
            default -> ModBlocks.SPIRE_STONE.get().defaultBlockState();
        };
    }
}
