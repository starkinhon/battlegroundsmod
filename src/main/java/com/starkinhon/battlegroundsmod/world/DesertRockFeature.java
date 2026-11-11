package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The rocks of the Deserto do Diabo (Stark 2026-11-11, his Sand Land reference): eroded sandstone standing out of the
// sand. Three kinds, each its own every time (sizes, offsets, lean and the banding all from the feature's random):
// - hoodoos: a stack of rounded lumps, wide and narrow in turns, sometimes a big balanced cap on top;
// - table rocks: a few thick legs holding a wide rounded cap (the anime's "stone elephants");
// - boulders: one to four rounded stones, half sunk in the sand.
// Banded in the two desert rocks. It stays within 8 blocks of its center and is filled down into the ground.
public final class DesertRockFeature extends Feature<NoneFeatureConfiguration> {
    private static final int REACH = 8;

    public DesertRockFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int ground = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                ground = Math.min(ground, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX() + dx, origin.getZ() + dz));
            }
        }
        BlockPos base = new BlockPos(origin.getX(), ground, origin.getZ());
        if (ground <= level.getMinBuildHeight() + 4 || !level.getFluidState(base).isEmpty()) {
            return false;
        }
        Shape shape = new Shape(level, base, random);
        int roll = random.nextInt(10);
        if (roll < 4) {
            shape.hoodoo();
        } else if (roll < 6) {
            shape.table();
        } else {
            shape.boulders();
        }
        return true;
    }

    private static final class Shape {
        final WorldGenLevel level;
        final BlockPos base;
        final RandomSource random;
        final int bandOffset;
        final int bandWidth;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Shape(WorldGenLevel level, BlockPos base, RandomSource random) {
            this.level = level;
            this.base = base;
            this.random = random;
            this.bandOffset = random.nextInt(16);
            this.bandWidth = 2 + random.nextInt(3);
        }

        void hoodoo() {
            int lumps = 3 + random.nextInt(5);
            double y = -2;
            double cx = 0, cz = 0;
            double leanX = (random.nextDouble() - 0.5) * 0.5, leanZ = (random.nextDouble() - 0.5) * 0.5;
            for (int i = 0; i < lumps; i++) {
                boolean top = i == lumps - 1;
                boolean wide = i % 2 == 0;
                double rx = top && random.nextInt(3) == 0 ? 2.6 + random.nextDouble() * 1.4
                        : wide ? 1.8 + random.nextDouble() * 1.4 : 1.1 + random.nextDouble() * 0.7;
                double rz = rx * (0.75 + random.nextDouble() * 0.5);
                double ry = 1.2 + random.nextDouble() * 1.3;
                y += ry * 0.9;
                ellipsoid(cx, y, cz, rx, ry, rz);
                y += ry * 0.8;
                cx += leanX + (random.nextDouble() - 0.5) * 0.6;
                cz += leanZ + (random.nextDouble() - 0.5) * 0.6;
            }
        }

        void table() {
            double capY = 5 + random.nextDouble() * 5;
            double capRx = 3.6 + random.nextDouble() * 2.4;
            double capRz = capRx * (0.6 + random.nextDouble() * 0.4);
            int legs = 2 + random.nextInt(3);
            double spin = random.nextDouble() * Math.PI * 2;
            for (int l = 0; l < legs; l++) {
                double a = spin + l * Math.PI * 2 / legs + (random.nextDouble() - 0.5) * 0.7;
                double lx = Math.cos(a) * capRx * 0.55, lz = Math.sin(a) * capRz * 0.55;
                double r = 0.9 + random.nextDouble() * 0.8;
                for (double y = -2; y < capY; y += 0.5) {
                    // a little thicker at the foot, like the anime's legs
                    double k = 1 + 0.35 * Math.max(0, 1 - (y + 2) / 3);
                    ellipsoid(lx, y, lz, r * k, 0.6, r * k);
                }
            }
            double capRy = 1.6 + random.nextDouble() * 1.4;
            ellipsoid(0, capY + capRy * 0.5, 0, capRx, capRy, capRz);
            // a second, smaller swell on the cap so the top isn't one smooth dome
            double a = random.nextDouble() * Math.PI * 2;
            ellipsoid(Math.cos(a) * capRx * 0.35, capY + capRy, Math.sin(a) * capRz * 0.35, capRx * 0.6, capRy * 0.8, capRz * 0.6);
        }

        void boulders() {
            int n = 1 + random.nextInt(4);
            for (int i = 0; i < n; i++) {
                double r = 0.9 + random.nextDouble() * 1.6;
                double ox = (random.nextDouble() - 0.5) * 9, oz = (random.nextDouble() - 0.5) * 9;
                ellipsoid(ox, r * 0.3 - 0.5, oz, r, r * (0.6 + random.nextDouble() * 0.4), r * (0.7 + random.nextDouble() * 0.5));
            }
        }

        // A lump centered at (cx, cy, cz) from the base, its edge a little rough.
        void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz) {
            int ix = (int) Math.ceil(rx) + 1, iy = (int) Math.ceil(ry) + 1, iz = (int) Math.ceil(rz) + 1;
            for (int dx = -ix; dx <= ix; dx++) {
                for (int dy = -iy; dy <= iy; dy++) {
                    for (int dz = -iz; dz <= iz; dz++) {
                        int x = (int) Math.floor(cx) + dx, y = (int) Math.floor(cy) + dy, z = (int) Math.floor(cz) + dz;
                        if (Math.abs(x) > REACH || Math.abs(z) > REACH) {
                            continue;
                        }
                        double ox = (x + 0.5 - cx) / rx, oy = (y + 0.5 - cy) / ry, oz = (z + 0.5 - cz) / rz;
                        double d = ox * ox + oy * oy + oz * oz;
                        if (d > 0.88 + 0.12 * hash(x + base.getX(), y + base.getY(), z + base.getZ())) {
                            continue;
                        }
                        put(x, y, z);
                    }
                }
            }
        }

        void put(int x, int y, int z) {
            pos.set(base.getX() + x, base.getY() + y, base.getZ() + z);
            BlockState there = level.getBlockState(pos);
            if (!there.isAir() && !there.canBeReplaced() && !there.is(ModBlocks.DESERT_SAND.get())) {
                return;
            }
            boolean light = Math.floorMod(Math.floorDiv(y + bandOffset, bandWidth), 2) == 1;
            level.setBlock(pos, (light ? ModBlocks.DESERT_ROCK_LIGHT : ModBlocks.DESERT_ROCK).get().defaultBlockState(), 2);
        }

        static double hash(int x, int y, int z) {
            long h = x * 3129871L ^ z * 116129781L ^ y * 7368787L;
            h = h * h * 42317861L + h * 11L;
            return ((h >> 16) & 0xFFFF) / 65535.0;
        }
    }
}
