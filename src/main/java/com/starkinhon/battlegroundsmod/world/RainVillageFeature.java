package com.starkinhon.battlegroundsmod.world;

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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

// The Vila da Chuva (Stark 2026-11-11, "ta a cara da vila da chuva", from the approved preview), done like the other
// Naruto features: once per chunk of the biome, in this order (the preview's):
// 1. the ground: the top of the dry land becomes mud, coarse dirt, gravel, dark grass or old concrete paving
//    (RainGen.groundTop), and shallow puddles where the ground makes a dish (all four neighbours as high or higher, so
//    the water doesn't run); the lakes' bottoms stay vanilla;
// 2. the part of every tower, stump, arch pipe, fallen pipe and pipe between towers that reaches the chunk (RainGen's
//    cells, the same seed in every chunk so the pieces meet): the towers and stumps first, standing on the ground or on
//    a lake's bottom, their feet filled down to the ground where the land falls away; then the pipes, the arch and the
//    fallen ones into the ground as in the preview, the ones between towers only into the air.
// Writes only inside its own chunk.
public final class RainVillageFeature extends Feature<NoneFeatureConfiguration> {
    private static final Cache<Long, int[]> PIECES = CacheBuilder.newBuilder().maximumSize(300).build();

    public RainVillageFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        int seed = worldKey ^ 0x0A3E6A;
        int sea = generator.getSeaLevel();
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator ground = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1. The ground.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int foot = ground.applyAsInt(x, z), g = foot - 1;
                if (!level.getBiome(pos.set(x, foot, z)).is(ModWorldgen.VILA_DA_CHUVA)) {
                    continue;
                }
                BlockState above = level.getBlockState(pos.set(x, foot, z));
                if (!above.getFluidState().isEmpty()) {
                    continue; // a lake's bottom
                }
                BlockState top = level.getBlockState(pos.set(x, g, z));
                if (!(top.is(BlockTags.DIRT) || top.is(Blocks.GRAVEL) || top.is(BlockTags.SAND) || top.is(Blocks.SNOW_BLOCK))) {
                    continue;
                }
                byte t = RainGen.groundTop(x, z, seed);
                boolean dish = t != RainGen.CONCRETE && RainGen.puddle(x, z, seed) && ground.applyAsInt(x + 1, z) >= foot
                        && ground.applyAsInt(x - 1, z) >= foot && ground.applyAsInt(x, z + 1) >= foot && ground.applyAsInt(x, z - 1) >= foot;
                if (above.canBeReplaced() && !above.isAir()) {
                    level.setBlock(pos.set(x, foot, z), Blocks.AIR.defaultBlockState(), 2);
                }
                if (dish) {
                    level.setBlock(pos.set(x, g, z), Blocks.WATER.defaultBlockState(), 2);
                    level.setBlock(pos.set(x, g - 1, z), Blocks.MUD.defaultBlockState(), 2);
                } else {
                    level.setBlock(pos.set(x, g, z), block(t), 2);
                    if (t == RainGen.GRASS) {
                        BlockState under = level.getBlockState(pos.set(x, g - 1, z));
                        if (under.is(BlockTags.DIRT) && !under.is(Blocks.MUD)) {
                            level.setBlock(pos, Blocks.MUD.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }

        // 2. Towers, stumps and pipes.
        List<RainGen.Obj> objs = RainGen.objectsNear(x0, z0, x1, z1, seed, o -> good(o, generator, random, ground, sea));
        objs.sort((a, b) -> Integer.compare(order(a), order(b)));
        for (RainGen.Obj o : objs) {
            int foot = o.kind() == RainGen.Kind.LINK ? 0 : ground.applyAsInt(o.x(), o.z());
            int[] vox = voxels(o, foot, ground);
            boolean solid = o.kind().solid();
            int low = o.kind() == RainGen.Kind.TOWER ? foot - 3 : foot - 2;
            for (int i = 0; i < vox.length; i += 4) {
                int x = vox[i], y = vox[i + 1], z = vox[i + 2], t = vox[i + 3];
                if (x < x0 || x > x1 || z < z0 || z > z1 || y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                    continue;
                }
                pos.set(x, y, z);
                if (!put(level, pos, block(t), o.kind())) {
                    continue;
                }
                // a tower's or a stump's lowest blocks go down to the ground where the land falls away
                if (solid && y == low) {
                    for (int k = 1; k <= 14; k++) {
                        BlockState below = level.getBlockState(pos.set(x, y - k, z));
                        if (!below.canBeReplaced() && below.getFluidState().isEmpty()) {
                            break;
                        }
                        level.setBlock(pos, ModBlocks.RAIN_METAL_DARK.get().defaultBlockState(), 2);
                    }
                }
            }
        }
        return true;
    }

    private static int order(RainGen.Obj o) {
        return o.kind() == RainGen.Kind.LINK ? 2 : o.kind().solid() ? 0 : 1;
    }

    // A voxel into the world. Towers and stumps take the place of the ground, water and plants; the arch and the fallen
    // pipes too (they go into the mud), but never into another piece's metal; the pipes between towers only into air,
    // plants and water. Returns whether it went in.
    private static boolean put(WorldGenLevel level, BlockPos pos, BlockState state, RainGen.Kind kind) {
        BlockState there = level.getBlockState(pos);
        boolean water = there.getFluidState().is(Fluids.WATER);
        boolean open = there.isAir() || water || there.canBeReplaced();
        boolean land = there.is(BlockTags.DIRT) || there.is(BlockTags.SAND) || there.is(Blocks.GRAVEL) || there.is(Blocks.CLAY)
                || there.is(BlockTags.BASE_STONE_OVERWORLD) || there.is(Blocks.POLISHED_TUFF) || there.is(Blocks.SNOW_BLOCK);
        if (kind == RainGen.Kind.LINK ? !open : !(open || land)) {
            return false;
        }
        level.setBlock(pos, state, 2);
        return true;
    }

    private static BlockState block(int t) {
        return switch (t) {
            case RainGen.STONE -> Blocks.STONE.defaultBlockState();
            case RainGen.MUD -> Blocks.MUD.defaultBlockState();
            case RainGen.COARSE -> Blocks.COARSE_DIRT.defaultBlockState();
            case RainGen.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case RainGen.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case RainGen.PUDDLE -> Blocks.WATER.defaultBlockState();
            case RainGen.METAL -> ModBlocks.RAIN_METAL.get().defaultBlockState();
            case RainGen.METAL_DARK -> ModBlocks.RAIN_METAL_DARK.get().defaultBlockState();
            case RainGen.RIB -> ModBlocks.RAIN_RIB.get().defaultBlockState();
            case RainGen.RUST -> ModBlocks.RUST.get().defaultBlockState();
            case RainGen.RUST_LIGHT -> ModBlocks.RUST_LIGHT.get().defaultBlockState();
            case RainGen.WINDOW -> ModBlocks.RAIN_WINDOW.get().defaultBlockState();
            case RainGen.WINDOW_LIT -> ModBlocks.RAIN_WINDOW_LIT.get().defaultBlockState();
            case RainGen.PIPE -> ModBlocks.RAIN_PIPE.get().defaultBlockState();
            case RainGen.CONCRETE -> Blocks.POLISHED_TUFF.defaultBlockState();
            case RainGen.SPIKE -> ModBlocks.RAIN_SPIKE.get().defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    private static int[] voxels(RainGen.Obj o, int foot, IntBinaryOperator ground) {
        long key = ((long) o.seed() << 20) ^ ((long) o.x() * 341873128712L) ^ ((long) o.z() * 132897987541L) ^ ((long) foot << 8)
                ^ o.kind().ordinal();
        try {
            return PIECES.get(key, () -> RainGen.build(o, foot, ground).pack());
        } catch (ExecutionException e) {
            return new int[0];
        }
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.VILA_DA_CHUVA);
    }

    // Is the spot good: the biome all around as far as the piece reaches; a tower or a stump on ground not too steep (or
    // in a lake not deeper than 14); a pipe between towers is good when both towers are (RainGen asks for each).
    private static boolean good(RainGen.Obj o, ChunkGenerator generator, RandomState random, IntBinaryOperator ground, int sea) {
        int y = ground.applyAsInt(o.x(), o.z());
        if (y < sea - 14 || !biomeAt(generator, random, o.x(), Math.max(y, sea), o.z())) {
            return false;
        }
        // a tower's 40 blocks of reach are its pipes' and balconies' far ends; its body is at most about 11 from its axis, and any
        // chunk with a bit of the biome builds its part, so the biome only has to be all around the body (the PC test of
        // 2026-11-12: with the whole reach no tower fitted in the biome's pieces, about 66 blocks across)
        int reach = o.kind() == RainGen.Kind.TOWER ? 20 : o.reach();
        for (int a = 0; a < 8; a++) {
            int x = o.x() + (int) Math.round(Math.cos(a * Math.PI / 4) * reach);
            int z = o.z() + (int) Math.round(Math.sin(a * Math.PI / 4) * reach);
            if (!biomeAt(generator, random, x, Math.max(y, sea), z)) {
                return false;
            }
        }
        if (o.kind().solid()) {
            int lo = y, hi = y, d = o.kind() == RainGen.Kind.TOWER ? 9 : 5;
            for (int a = 0; a < 4; a++) {
                int h = ground.applyAsInt(o.x() + (a % 2 == 0 ? d : -d), o.z() + (a < 2 ? d : -d));
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            // a tower's foot goes down to the ground up to 14 blocks where the land falls away, so it stands on a
            // steeper slope than a stump (the Vila lies on the cool band's hills: with 8 only 1 tower in 3 pieces)
            if (hi - lo > (o.kind() == RainGen.Kind.TOWER ? 14 : 8)) {
                return false;
            }
        }
        return true;
    }
}
