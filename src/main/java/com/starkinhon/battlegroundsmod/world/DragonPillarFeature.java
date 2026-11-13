package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The rock pillars of the Dragon World (Stark 2026-11-11: "montanhas incríveis"): tall leaning spires and flat-topped
// mesas of striped rock standing out of the plains, the desert and the mountains, grass and sometimes a tree on top.
// Every one is its own: height, width, lean, taper, the wobble of its outline (three sine waves with random phases
// and frequencies, so no two have the same shape) and the order of its bands all come from the feature's random.
// It stays within 9 blocks of its center, inside the chunks a feature may write to.
public final class DragonPillarFeature extends Feature<NoneFeatureConfiguration> {
    private static final Block[] BANDS = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
            Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA,
            Blocks.SMOOTH_SANDSTONE};
    private static final int REACH = 9;

    public DragonPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int ground = lowestGround(level, origin);
        if (ground <= level.getMinBuildHeight() + 4 || !level.getFluidState(new BlockPos(origin.getX(), ground, origin.getZ())).isEmpty()) {
            return false;
        }
        if (nearOtherUniverse(level, origin, ground)
                || ColoniaFeature.covers(level, context.chunkGenerator(), origin.getX(), origin.getZ(), REACH)) {
            return false;
        }
        boolean mesa = random.nextInt(4) == 0;
        double base = mesa ? 5.5 + random.nextDouble() * 2.0 : 2.6 + random.nextDouble() * 3.0;
        int height = mesa ? 14 + random.nextInt(14) : 22 + random.nextInt(34);
        double taper = mesa ? 0.1 + random.nextDouble() * 0.15 : 0.35 + random.nextDouble() * 0.35;
        // The spire leans a little and its top may bulge out (the mushroom-shaped rocks of the anime).
        double leanX = (random.nextDouble() - 0.5) * (mesa ? 0.0 : 0.12);
        double leanZ = (random.nextDouble() - 0.5) * (mesa ? 0.0 : 0.12);
        double cap = mesa || random.nextInt(3) == 0 ? 1.0 + random.nextDouble() * 0.9 : 0;
        double[] freq = {1 + random.nextInt(3), 3 + random.nextInt(3), 6 + random.nextInt(4)};
        double[] amp = {0.35 + random.nextDouble() * 0.4, 0.18 + random.nextDouble() * 0.25, 0.08 + random.nextDouble() * 0.12};
        double[] phase = {random.nextDouble() * 6.3, random.nextDouble() * 6.3, random.nextDouble() * 6.3};
        double[] rise = {random.nextDouble() * 0.3, random.nextDouble() * 0.5, random.nextDouble() * 0.8};
        Block[] bands = new Block[5 + random.nextInt(4)];
        for (int i = 0; i < bands.length; i++) {
            bands[i] = BANDS[random.nextInt(BANDS.length)];
        }
        int bandOffset = random.nextInt(64);
        double bandSkew = random.nextDouble() * 0.6;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = ground + height;
        for (int y = ground - 3; y <= top; y++) {
            double t = Math.max(0, (double) (y - ground) / height);
            double radius = base * (1 - taper * t);
            if (cap > 0 && t > 0.82) {
                radius += cap * Math.sin((t - 0.82) / 0.18 * Math.PI * 0.5);
            }
            double cx = origin.getX() + leanX * (y - ground);
            double cz = origin.getZ() + leanZ * (y - ground);
            int r = (int) Math.ceil(radius + 1.2);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = (int) Math.round(cx) + dx;
                    int z = (int) Math.round(cz) + dz;
                    if (Math.abs(x - origin.getX()) > REACH || Math.abs(z - origin.getZ()) > REACH) {
                        continue;
                    }
                    double ox = x - cx;
                    double oz = z - cz;
                    double angle = Math.atan2(oz, ox);
                    double wobble = 0;
                    for (int w = 0; w < 3; w++) {
                        wobble += amp[w] * Math.sin(angle * freq[w] + phase[w] + y * rise[w] * 0.15);
                    }
                    if (Math.sqrt(ox * ox + oz * oz) > radius + wobble) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (y == top) {
                        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    } else {
                        int band = Math.floorMod((int) Math.floor((y + bandOffset + ox * bandSkew) / 3.0), bands.length);
                        level.setBlock(pos, bands[band].defaultBlockState(), 2);
                    }
                }
            }
        }
        if (random.nextInt(mesa ? 2 : 4) == 0) {
            tree(level, random, new BlockPos((int) Math.round(origin.getX() + leanX * height), top + 1,
                    (int) Math.round(origin.getZ() + leanZ * height)));
        }
        return true;
    }

    // A pillar whose center is in the Dragon World can still reach 9 blocks into the next chunk, and that chunk may
    // already hold a Shibuya city, a Colony of the Culling Game or a Naruto village (Stark 2026-11-13: "as pedras da
    // terra dos dragoes tao por cima de algumas cidades"). If any column the pillar may touch belongs to another
    // universe, the pillar is not placed at all, so it never lands on a building and is never cut in half.
    private static boolean nearOtherUniverse(WorldGenLevel level, BlockPos origin, int ground) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx += 3) {
            for (int dz = -REACH; dz <= REACH; dz += 3) {
                var biome = level.getBiome(pos.set(origin.getX() + dx, ground, origin.getZ() + dz));
                if (biome.is(ModWorldgen.JUJUTSU_BIOMES) || biome.is(ModWorldgen.NARUTO_BIOMES)) {
                    return true;
                }
            }
        }
        return false;
    }

    // The lowest ground under the pillar's foot, so it never floats on a slope.
    private static int lowestGround(WorldGenLevel level, BlockPos origin) {
        int low = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                low = Math.min(low, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() + dx, origin.getZ() + dz));
            }
        }
        return low;
    }

    // A small round tree on the top (the anime's puffy trees on the rocks).
    private static void tree(WorldGenLevel level, RandomSource random, BlockPos at) {
        int trunk = 2 + random.nextInt(3);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int i = 0; i < trunk; i++) {
            level.setBlock(at.above(i), log, 2);
        }
        double r = 1.6 + random.nextDouble();
        BlockPos crown = at.above(trunk);
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -1; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dy * dy * 1.6 + dz * dz <= r * r) {
                        BlockPos p = crown.offset(dx, dy, dz);
                        if (level.getBlockState(p).isAir()) {
                            level.setBlock(p, leaves, 2);
                        }
                    }
                }
            }
        }
    }
}
