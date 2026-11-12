package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

// The rock towers of the Montanhas Paozu (Stark 2026-11-11: "você tem que fazer um diferencial foda... pra não ser
// parecido com uma planície do Mine"): the steep karst spires of Goku's childhood mountains, like the hills of Guilin the
// anime drew them from. Each one a tall rounded column of grey rock rising out of the green, its sides bulging and pinched,
// streaked with darker stone, moss on the ledges, vines hanging down, a green cap with a puffy tree or two on top, and now
// and then a waterfall pouring from a crack high up. Height, girth, lean, the outline at every height and the streaks all
// come from the random, so no two spires match; in clusters they make the forest of stone towers. Within 15 blocks.
public final class KarstSpireFeature extends Feature<NoneFeatureConfiguration> {
    private static final int REACH = 15;

    public KarstSpireFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int ground = Integer.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dz = -4; dz <= 4; dz += 4) {
                ground = Math.min(ground, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX() + dx, origin.getZ() + dz));
            }
        }
        BlockPos base = new BlockPos(origin.getX(), ground - 3, origin.getZ());
        if (ground <= level.getMinBuildHeight() + 4 || ground + 60 >= level.getMaxBuildHeight()) {
            return false;
        }
        int height = 20 + random.nextInt(random.nextInt(3) == 0 ? 34 : 18);
        double radius = 4.5 + random.nextDouble() * 3.5 + height * 0.04;
        radius = Math.min(radius, 10);
        double leanX = (random.nextDouble() - 0.5) * 0.12, leanZ = (random.nextDouble() - 0.5) * 0.12;
        double[] phase = {random.nextDouble() * 6.3, random.nextDouble() * 6.3, random.nextDouble() * 6.3, random.nextDouble() * 6.3};
        double bulgeF = 0.12 + random.nextDouble() * 0.12;
        BlockState stone = Blocks.STONE.defaultBlockState(), dark = Blocks.ANDESITE.defaultBlockState(), tuff = Blocks.TUFF.defaultBlockState();
        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(),
                dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int total = height + 3;
        int[][] top = new int[REACH * 2 + 1][REACH * 2 + 1];
        for (int y = 0; y <= total; y++) {
            double k = (double) y / total;
            // a little narrower going up, bulges and waists along the way, a rounded crown
            double r = radius * (1 - 0.22 * k) * (1 + 0.16 * Math.sin(y * bulgeF + phase[0]) + 0.07 * Math.sin(y * bulgeF * 2.7 + phase[1]));
            if (k > 0.82) {
                double c = (k - 0.82) / 0.18;
                r *= Math.sqrt(Math.max(0, 1 - c * c));
            }
            double cx = leanX * y, cz = leanZ * y;
            int ri = (int) Math.ceil(r * 1.3) + 1;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    int x = (int) Math.round(cx) + dx, z = (int) Math.round(cz) + dz;
                    if (Math.abs(x) > REACH || Math.abs(z) > REACH) {
                        continue;
                    }
                    double ox = x + 0.5 - cx, oz = z + 0.5 - cz;
                    double angle = Math.atan2(oz, ox);
                    double edge = r * (1 + 0.15 * Math.sin(3 * angle + phase[2] + y * 0.05) + 0.08 * Math.sin(5 * angle + phase[3] - y * 0.07));
                    if (ox * ox + oz * oz > edge * edge) {
                        continue;
                    }
                    pos.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    // vertical streaks of darker rock, from the angle around the spire
                    double streak = hash((int) Math.floor(angle * 9), 0, base.getX() ^ base.getZ());
                    BlockState rock = streak > 0.78 ? dark : streak > 0.66 ? tuff : stone;
                    level.setBlock(pos, rock, 2);
                    top[x + REACH][z + REACH] = base.getY() + y;
                }
            }
        }
        // Moss on the ledges, grass on the crown, vines down the sides.
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                int t = top[x + REACH][z + REACH];
                if (t == 0) {
                    continue;
                }
                pos.set(base.getX() + x, t, base.getZ() + z);
                boolean crown = t - base.getY() > total * 0.8;
                if (!level.getBlockState(pos.above()).isAir()) {
                    continue;
                }
                if (crown) {
                    level.setBlock(pos, grass, 2);
                    level.setBlock(pos.below(), dirt, 2);
                } else if (t > ground + 1 && random.nextInt(3) != 0) {
                    level.setBlock(pos, moss, 2);
                }
            }
        }
        vines(level, base, top, random, pos);
        // A tree or two on the crown.
        int trees = 1 + random.nextInt(2);
        for (int tries = 0; tries < 12 && trees > 0; tries++) {
            int x = (int) Math.round(leanX * total) + random.nextInt(5) - 2, z = (int) Math.round(leanZ * total) + random.nextInt(5) - 2;
            if (Math.abs(x) > 3 || Math.abs(z) > 3) {
                continue;
            }
            int t = top[x + REACH][z + REACH];
            if (t != 0 && PuffyTreeFeature.grow(level, new BlockPos(base.getX() + x, t + 1, base.getZ() + z), random)) {
                trees--;
            }
        }
        // A waterfall from a crack high up on the side.
        if (random.nextInt(5) < 2) {
            waterfall(level, base, total, radius, leanX, leanZ, random, pos);
        }
        return true;
    }

    private static void vines(WorldGenLevel level, BlockPos base, int[][] top, RandomSource random, BlockPos.MutableBlockPos pos) {
        for (int n = 0; n < 40; n++) {
            int x = random.nextInt(REACH * 2 + 1) - REACH, z = random.nextInt(REACH * 2 + 1) - REACH;
            int t = top[x + REACH][z + REACH];
            if (t == 0 || Math.abs(x) > REACH - 1 || Math.abs(z) > REACH - 1) {
                continue;
            }
            Direction out = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int y = base.getY() + 4 + random.nextInt(Math.max(1, t - base.getY() - 4));
            pos.set(base.getX() + x, y, base.getZ() + z);
            if (level.getBlockState(pos).isAir()) {
                continue;
            }
            // walk out of the rock to its face, then hang a vine down from there
            for (int step = 0; step < 6 && !level.getBlockState(pos).isAir(); step++) {
                pos.move(out);
            }
            if (!level.getBlockState(pos).isAir() || level.getBlockState(pos.relative(out.getOpposite())).isAir()) {
                continue;
            }
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(out.getOpposite()), true);
            int length = 3 + random.nextInt(10);
            for (int i = 0; i < length && level.getBlockState(pos).isAir()
                    && !level.getBlockState(pos.relative(out.getOpposite())).isAir(); i++) {
                level.setBlock(pos, vine, 2);
                pos.move(Direction.DOWN);
            }
        }
    }

    private static void waterfall(WorldGenLevel level, BlockPos base, int total, double radius, double leanX, double leanZ,
            RandomSource random, BlockPos.MutableBlockPos pos) {
        int y = (int) (total * (0.55 + random.nextDouble() * 0.2));
        double angle = random.nextDouble() * Math.PI * 2;
        double cx = leanX * y, cz = leanZ * y;
        // from the middle outward to the last rock block before the air
        int lastX = Integer.MIN_VALUE, lastZ = 0;
        for (double d = 0; d < radius * 1.5 + 2; d += 0.5) {
            int x = (int) Math.round(cx + Math.cos(angle) * d), z = (int) Math.round(cz + Math.sin(angle) * d);
            if (Math.abs(x) > REACH - 1 || Math.abs(z) > REACH - 1) {
                return;
            }
            pos.set(base.getX() + x, base.getY() + y, base.getZ() + z);
            if (level.getBlockState(pos).isAir()) {
                break;
            }
            lastX = x;
            lastZ = z;
        }
        if (lastX == Integer.MIN_VALUE) {
            return;
        }
        pos.set(base.getX() + lastX, base.getY() + y, base.getZ() + lastZ);
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
        level.scheduleTick(pos.immutable(), Fluids.WATER, 0);
        level.setBlock(pos.above(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
    }

    private static double hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7368787L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65535.0;
    }
}
