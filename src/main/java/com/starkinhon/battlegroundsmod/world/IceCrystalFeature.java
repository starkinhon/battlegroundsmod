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

// The ice crystals of the Norte Gelado (Stark 2026-11-11: "pode meter bala"): clusters of huge pointed shards of ice
// bursting out of the snow, a tall one in the middle and others leaning out around it. Each shard is its own (length,
// thickness, lean, a six-sided section turned at random), deep blue ice in the heart, packed ice outside, clear ice at
// the tips. Within 10 blocks of its foot.
public final class IceCrystalFeature extends Feature<NoneFeatureConfiguration> {
    private static final int REACH = 10;

    public IceCrystalFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
        BlockPos foot = new BlockPos(origin.getX(), ground, origin.getZ());
        if (ground <= level.getMinBuildHeight() + 4 || !level.getFluidState(foot).isEmpty()) {
            return false;
        }
        boolean big = random.nextInt(4) == 0;
        int shards = 3 + random.nextInt(big ? 7 : 4);
        double spin = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < shards; i++) {
            double tilt, angle, length, radius, ox, oz;
            if (i == 0) {
                tilt = random.nextDouble() * 0.2;
                angle = spin;
                length = (big ? 14 : 7) + random.nextDouble() * (big ? 9 : 6);
                radius = (big ? 2.2 : 1.4) + random.nextDouble() * 0.9;
                ox = 0;
                oz = 0;
            } else {
                tilt = 0.3 + random.nextDouble() * 0.6;
                angle = spin + i * Math.PI * 2 / (shards - 1) + (random.nextDouble() - 0.5) * 0.8;
                length = (big ? 6 : 3.5) + random.nextDouble() * (big ? 9 : 5);
                radius = 0.8 + random.nextDouble() * (big ? 1.4 : 0.8);
                ox = Math.cos(angle) * (0.5 + random.nextDouble() * 1.5);
                oz = Math.sin(angle) * (0.5 + random.nextDouble() * 1.5);
            }
            double dx = Math.cos(angle) * Math.sin(tilt), dy = Math.cos(tilt), dz = Math.sin(angle) * Math.sin(tilt);
            shard(level, foot, ox, -2, oz, dx, dy, dz, length + 2, radius, random.nextDouble() * Math.PI);
        }
        return true;
    }

    // A six-sided shard from (bx, by, bz) along the unit axis (dx, dy, dz), narrowing to a point.
    private static void shard(WorldGenLevel level, BlockPos foot, double bx, double by, double bz, double dx, double dy,
            double dz, double length, double radius, double turn) {
        // two directions across the axis, for the hexagon's angle
        double ux = -dz, uy = 0, uz = dx;
        double ul = Math.sqrt(ux * ux + uz * uz);
        if (ul < 1.0E-4) {
            ux = 1;
            uz = 0;
            ul = 1;
        }
        ux /= ul;
        uz /= ul;
        double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        BlockState heart = Blocks.BLUE_ICE.defaultBlockState(), shell = Blocks.PACKED_ICE.defaultBlockState(),
                tip = Blocks.ICE.defaultBlockState();
        int box = (int) Math.ceil(radius) + 1;
        double ex = bx + dx * length, ey = by + dy * length, ez = bz + dz * length;
        int x0 = (int) Math.floor(Math.min(bx, ex)) - box, x1 = (int) Math.ceil(Math.max(bx, ex)) + box;
        int y0 = (int) Math.floor(Math.min(by, ey)) - box, y1 = (int) Math.ceil(Math.max(by, ey)) + box;
        int z0 = (int) Math.floor(Math.min(bz, ez)) - box, z1 = (int) Math.ceil(Math.max(bz, ez)) + box;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(x0, -REACH); x <= Math.min(x1, REACH); x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = Math.max(z0, -REACH); z <= Math.min(z1, REACH); z++) {
                    double px = x + 0.5 - bx, py = y + 0.5 - by, pz = z + 0.5 - bz;
                    double along = px * dx + py * dy + pz * dz;
                    if (along < 0 || along > length) {
                        continue;
                    }
                    double t = along / length;
                    double cx = px - dx * along, cy = py - dy * along, cz = pz - dz * along;
                    double a = cx * ux + cy * uy + cz * uz, b = cx * vx + cy * vy + cz * vz;
                    double dist = Math.sqrt(a * a + b * b);
                    // a hexagon: the edge comes in between the corners
                    double theta = Math.atan2(b, a) + turn;
                    double hex = Math.cos(Math.PI / 6) / Math.cos(((theta % (Math.PI / 3)) + Math.PI / 3) % (Math.PI / 3) - Math.PI / 6);
                    // straight sides most of the way up, then the point
                    double taper = t < 0.65 ? 1 - 0.25 * t : (1 - t) / 0.35 * 0.84;
                    double r = radius * taper * hex;
                    if (dist > r) {
                        continue;
                    }
                    pos.set(foot.getX() + x, foot.getY() + y, foot.getZ() + z);
                    BlockState there = level.getBlockState(pos);
                    if (!there.isAir() && !there.canBeReplaced() && !there.is(Blocks.SNOW_BLOCK) && !there.is(Blocks.POWDER_SNOW)) {
                        continue;
                    }
                    level.setBlock(pos, t > 0.82 ? tip : dist < r * 0.55 ? heart : shell, 2);
                }
            }
        }
    }
}
