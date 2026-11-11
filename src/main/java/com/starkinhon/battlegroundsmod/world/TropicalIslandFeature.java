package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The little islands of the Mar do Sul (Stark 2026-11-11: "ilhas tropicais da Kame House", strong sun, turquoise sea):
// a round sand island with a beach all around, sometimes a green hump in the middle, palms leaning out over the water,
// and a sandy slope going down under the sea to the floor so the water turns light over it. One to four lobes of
// different sizes melt into one coastline, wobbled and nibbled, so each island has its own outline; two islands next to
// each other make an archipelago. Within 15 blocks of its center.
public final class TropicalIslandFeature extends Feature<NoneFeatureConfiguration> {
    private static final int REACH = 15;
    // how far out (in island radii past the shore) the slope runs under water
    private static final double SHELF = 0.7;

    public TropicalIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int sea = level.getSeaLevel();
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
        if (sea - floor < 2 || sea - floor > 34) {
            return false;
        }
        int lobes = 1 + random.nextInt(random.nextInt(4) == 0 ? 4 : 2);
        double[][] lobe = new double[lobes][];
        double main = 5 + random.nextDouble() * 6;
        lobe[0] = new double[] {0, 0, main, random.nextDouble() * 6.3, random.nextDouble() * 6.3};
        for (int i = 1; i < lobes; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = main * (0.5 + random.nextDouble() * 0.5);
            lobe[i] = new double[] {Math.cos(a) * d, Math.sin(a) * d, main * (0.4 + random.nextDouble() * 0.4),
                    random.nextDouble() * 6.3, random.nextDouble() * 6.3};
        }
        // The hump: big islands get a green hill, small ones stay a flat sand bar.
        double peak = main > 8 ? 2 + random.nextDouble() * 4 : random.nextDouble() * 1.6;
        BlockState sand = Blocks.SAND.defaultBlockState(), sandstone = Blocks.SANDSTONE.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] land = new int[REACH * 2 + 1][REACH * 2 + 1];
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                double t = -9;
                for (double[] l : lobe) {
                    double dx = x - l[0], dz = z - l[1];
                    double angle = Math.atan2(dz, dx);
                    double r = l[2] * (1 + 0.14 * Math.sin(3 * angle + l[3]) + 0.08 * Math.sin(5 * angle + l[4]));
                    t = Math.max(t, 1 - Math.sqrt(dx * dx + dz * dz) / r);
                }
                int wx = origin.getX() + x, wz = origin.getZ() + z;
                t += (hash(wx, 0, wz) - 0.5) * 0.06;
                if (t < -SHELF) {
                    continue;
                }
                int colFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, wx, wz);
                int top;
                if (t >= 0) {
                    top = sea + (int) Math.floor(peak * Math.pow(Math.min(1, t * 1.4), 1.3));
                } else {
                    top = sea - 1 - (int) Math.round((sea - 1 - colFloor) * Math.pow(-t / SHELF, 0.75));
                }
                if (top < colFloor) {
                    continue;
                }
                boolean green = top >= sea + 2 && t > 0.3;
                for (int y = colFloor - 1; y <= top; y++) {
                    pos.set(wx, y, wz);
                    BlockState there = level.getBlockState(pos);
                    if (y < colFloor && !there.is(Blocks.GRAVEL) && !there.is(Blocks.DIRT) && !there.is(Blocks.CLAY)) {
                        continue;
                    }
                    if (y >= colFloor && !there.isAir() && !there.canBeReplaced()) {
                        continue;
                    }
                    int depth = top - y;
                    BlockState state = green ? (depth == 0 ? grass : depth < 3 ? dirt : sandstone) : depth < 3 ? sand : sandstone;
                    level.setBlock(pos, state, 2);
                }
                if (top >= sea) {
                    // the foot of anything growing there, signed: negative on the green
                    land[x + REACH][z + REACH] = green ? -(top + 1) : top + 1;
                }
            }
        }
        // Palms on the land, more on the beach than on the hill, apart from each other; grass and ferns on the green.
        int palms = 1 + random.nextInt(2) + (int) (main / 3.5);
        for (int tries = 0; tries < 40 && palms > 0; tries++) {
            // within 8 of the middle: a palm reaches 8 more, and a feature may only touch the chunks next to its own
            int x = random.nextInt(17) - 8, z = random.nextInt(17) - 8;
            if (land[x + REACH][z + REACH] == 0) {
                continue;
            }
            int wx = origin.getX() + x, wz = origin.getZ() + z;
            BlockPos foot = new BlockPos(wx, Math.abs(land[x + REACH][z + REACH]), wz);
            if (PalmTreeFeature.grow(level, foot, random)) {
                palms--;
            }
        }
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                if (land[x + REACH][z + REACH] >= 0 || random.nextInt(3) != 0) {
                    continue;
                }
                pos.set(origin.getX() + x, -land[x + REACH][z + REACH], origin.getZ() + z);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(pos, (random.nextInt(3) == 0 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    private static double hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7368787L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65535.0;
    }
}
