package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The rock towers of Namek (Stark 2026-11-11: last time "os pilares estavam muito brutos, parecendo bloco saindo da
// terra; faz um bagulho mais suave, com mais variações de tamanho, de grossura"). Three kinds, all smooth and organic:
// - mushroom spires: a stem pinched at the waist that flares out into a wide cap, rounded underneath, flat and green on
//   top (the shape the anime draws over Namek's seas);
// - mesas: wide flat-topped rocks, a little narrower at the top, their sides notched by erosion;
// - needles: thin tall columns, often leaning.
// Every shape is a smooth profile over height times an outline that wobbles around and drifts as it rises, so no two
// are alike and nothing is square; the rock is banded in the planet's pale and pink stone, the bands rippling instead of
// running straight. They rise from the ground or straight out of the sea. Within 15 blocks of the middle.
public final class NamekSpireFeature extends Feature<NoneFeatureConfiguration> {
    private static final int REACH = 15;

    public NamekSpireFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int ground = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                ground = Math.min(ground, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX() + dx, origin.getZ() + dz));
            }
        }
        if (ground <= level.getMinBuildHeight() + 8 || ground + 70 >= level.getMaxBuildHeight()) {
            return false;
        }
        int sea = level.getSeaLevel();
        // out of the sea they start at the surface's height, so a tall part shows above the water
        int rise = Math.max(0, sea - ground);
        Shape shape = new Shape(random, rise);
        BlockPos base = new BlockPos(origin.getX(), ground - 3, origin.getZ());
        BlockState pale = ModBlocks.NAMEK_ROCK.get().defaultBlockState();
        BlockState pink = ModBlocks.NAMEK_ROCK_LAYER.get().defaultBlockState();
        BlockState light = Blocks.CALCITE.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] top = new int[REACH * 2 + 1][REACH * 2 + 1];
        int total = shape.height + 3;
        double bandPhase = random.nextDouble() * 6.3, bandWidth = 2 + random.nextDouble() * 2.5;
        for (int y = 0; y <= total; y++) {
            double k = (double) y / total;
            double r = shape.radius(k);
            if (r <= 0.3) {
                continue;
            }
            double cx = shape.leanX * y + 0.8 * Math.sin(y * 0.11 + shape.p[0]);
            double cz = shape.leanZ * y + 0.8 * Math.sin(y * 0.09 + shape.p[1]);
            int ri = (int) Math.ceil(r * 1.25) + 1;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    int x = (int) Math.round(cx) + dx, z = (int) Math.round(cz) + dz;
                    if (Math.abs(x) > REACH || Math.abs(z) > REACH) {
                        continue;
                    }
                    double ox = x + 0.5 - cx, oz = z + 0.5 - cz;
                    double a = Math.atan2(oz, ox);
                    double edge = r * (1 + 0.12 * Math.sin(2 * a + shape.p[2] + y * 0.04) + 0.08 * Math.sin(3 * a + shape.p[3] - y * 0.06)
                            + 0.05 * Math.sin(5 * a + shape.p[4] + y * 0.13));
                    // erosion notches on the mesas' sides
                    if (shape.kind == 1 && k < 0.9) {
                        edge -= 0.9 * Math.max(0, Math.sin(y * 0.7 + a * 3 + shape.p[5]) - 0.6) * 3;
                    }
                    double d2 = ox * ox + oz * oz;
                    if (d2 > edge * edge) {
                        continue;
                    }
                    pos.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    double band = (y + 1.6 * Math.sin(a * 2 + bandPhase) + 1.1 * Math.sin(x * 0.21 + z * 0.17)) / bandWidth;
                    int b = Math.floorMod((int) Math.floor(band), 5);
                    BlockState rock = b == 1 || b == 3 ? pink : b == 4 && hash(x + base.getX(), y, z + base.getZ()) > 0.7 ? light : pale;
                    level.setBlock(pos, rock, 2);
                    top[x + REACH][z + REACH] = base.getY() + y;
                }
            }
        }
        // Green on the flat tops (the planet's blue-green grass, tinted by the biome), dirt under it.
        boolean green = shape.kind != 2 || random.nextBoolean();
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                int t = top[x + REACH][z + REACH];
                if (t == 0 || t - base.getY() < total * 0.75 || !green) {
                    continue;
                }
                pos.set(base.getX() + x, t, base.getZ() + z);
                if (level.getBlockState(pos.above()).isAir() && t >= sea) {
                    level.setBlock(pos, grass, 2);
                    level.setBlock(pos.below(), dirt, 2);
                }
            }
        }
        // A Namekian tree or two on a big top.
        if (green && shape.kind != 2) {
            int trees = random.nextInt(3);
            int cx = (int) Math.round(shape.leanX * total), cz = (int) Math.round(shape.leanZ * total);
            for (int tries = 0; tries < 10 && trees > 0; tries++) {
                int x = cx + random.nextInt(7) - 3, z = cz + random.nextInt(7) - 3;
                if (Math.abs(x) > 4 || Math.abs(z) > 4) {
                    continue;
                }
                int t = top[x + REACH][z + REACH];
                if (t != 0 && NamekTreeFeature.grow(level, new BlockPos(base.getX() + x, t + 1, base.getZ() + z), random)) {
                    trees--;
                }
            }
        }
        return true;
    }

    private static final class Shape {
        final int kind;
        final int height;
        final double width;
        final double leanX, leanZ;
        final double[] p = new double[6];
        final double waist, flare;

        Shape(RandomSource random, int rise) {
            int roll = random.nextInt(10);
            kind = roll < 5 ? 0 : roll < 8 ? 1 : 2;
            for (int i = 0; i < p.length; i++) {
                p[i] = random.nextDouble() * 6.3;
            }
            switch (kind) {
                case 0 -> {
                    height = rise + 12 + random.nextInt(random.nextInt(3) == 0 ? 34 : 18);
                    width = 2 + random.nextDouble() * 2.6;
                }
                case 1 -> {
                    height = rise + 7 + random.nextInt(12);
                    width = 6 + random.nextDouble() * 4.5;
                }
                default -> {
                    height = rise + 12 + random.nextInt(22);
                    width = 1 + random.nextDouble() * 1.4;
                }
            }
            double lean = kind == 2 ? 0.05 + random.nextDouble() * 0.12 : random.nextDouble() * 0.06;
            double la = random.nextDouble() * Math.PI * 2;
            leanX = Math.cos(la) * lean;
            leanZ = Math.sin(la) * lean;
            waist = 0.45 + random.nextDouble() * 0.25;
            flare = 1.5 + random.nextDouble() * 1.1;
        }

        // The radius at height k (0 foot, 1 top).
        double radius(double k) {
            return switch (kind) {
                case 0 -> {
                    // wide foot, pinched waist, flaring cap with a rounded underside and a rounded rim on top
                    double stem = width * (1.25 - 0.45 * smooth(0, waist, k) * (1 - smooth(waist, 0.8, k)));
                    double cap = width * flare * Math.sqrt(Math.max(0, 1 - Math.pow((k - 0.9) / 0.12, 2)));
                    yield Math.max(stem * (k < 0.97 ? 1 : 0.6), cap);
                }
                case 1 -> width * (1.05 - 0.2 * k) * (k > 0.92 ? Math.sqrt(Math.max(0, 1 - Math.pow((k - 0.92) / 0.1, 2))) : 1);
                default -> width * (1.2 - 0.75 * k) + 0.35;
            };
        }

        private static double smooth(double a, double b, double x) {
            double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
            return t * t * (3 - 2 * t);
        }
    }

    private static double hash(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7368787L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65535.0;
    }
}
