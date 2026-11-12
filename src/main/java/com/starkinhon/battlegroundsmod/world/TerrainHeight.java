package com.starkinhon.battlegroundsmod.world;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

// The natural ground's height (the first air block over the solid ground, before anything is built on it), the same
// number as the generator's getBaseHeight(x, z, OCEAN_FLOOR_WG), for a whole 16 by 16 area at once. getBaseHeight makes
// a new one-column NoiseChunk on every call (it rebuilds the whole density graph and evaluates the noise at the column's
// four cell corners all the way up), which was half the time of a Naruto biome's chunk on the PC test of 2026-11-12 (about
// 1 s a chunk in Myoboku and the Folha). Here one NoiseChunk for the area is filled the way the generator fills a
// chunk, cell by cell from the top, stopping a cell's columns as soon as they all have their ground: the same corners and
// the same interpolation, so the same heights, at a small part of the cost.
public final class TerrainHeight {
    private static final Cache<Long, int[]> AREAS = CacheBuilder.newBuilder().maximumSize(4096).build();

    private TerrainHeight() {
    }

    // Keyed by the world's seed too: another world opened in the same game has other heights.
    public static int at(ChunkGenerator generator, LevelHeightAccessor level, RandomState random, int seed, int x, int z) {
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) {
            return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        }
        int ax = x >> 4, az = z >> 4;
        long key = (((long) ax << 32) | (az & 0xFFFFFFFFL)) ^ ((long) seed * 0x9E3779B97F4A7C15L);
        try {
            int[] area = AREAS.get(key, () -> area(noise, level, random, ax << 4, az << 4));
            return area[((x & 15) << 4) | (z & 15)];
        } catch (ExecutionException e) {
            return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        }
    }

    private static int[] area(NoiseBasedChunkGenerator generator, LevelHeightAccessor level, RandomState random, int x0, int z0) {
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        NoiseSettings ns = settings.noiseSettings().clampToHeightAccessor(level);
        int cellH = ns.getCellHeight(), cellW = ns.getCellWidth();
        int minCellY = Mth.floorDiv(ns.minY(), cellH), cellCountY = Mth.floorDiv(ns.height(), cellH);
        int[] out = new int[256];
        Arrays.fill(out, level.getMinBuildHeight());
        if (cellCountY <= 0) {
            return out;
        }
        int n = 16 / cellW;
        Predicate<BlockState> solid = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
        BlockState stone = settings.defaultBlock();
        boolean[] found = new boolean[256];
        NoiseChunk chunk = new NoiseChunk(n, random, x0, z0, ns, DensityFunctions.BeardifierMarker.INSTANCE, settings,
                generator.globalFluidPicker.get(), Blender.empty());
        chunk.initializeForFirstCellX();
        for (int cx = 0; cx < n; cx++) {
            chunk.advanceCellX(cx);
            for (int cz = 0; cz < n; cz++) {
                int left = cellW * cellW;
                for (int cy = cellCountY - 1; cy >= 0 && left > 0; cy--) {
                    chunk.selectCellYZ(cy, cz);
                    for (int yy = cellH - 1; yy >= 0 && left > 0; yy--) {
                        int y = (minCellY + cy) * cellH + yy;
                        chunk.updateForY(y, (double) yy / cellH);
                        for (int xx = 0; xx < cellW; xx++) {
                            int lx = cx * cellW + xx;
                            chunk.updateForX(x0 + lx, (double) xx / cellW);
                            for (int zz = 0; zz < cellW; zz++) {
                                int lz = cz * cellW + zz;
                                int i = (lx << 4) | lz;
                                if (found[i]) {
                                    continue;
                                }
                                chunk.updateForZ(z0 + lz, (double) zz / cellW);
                                BlockState state = chunk.getInterpolatedState();
                                if (solid.test(state == null ? stone : state)) {
                                    found[i] = true;
                                    out[i] = y + 1;
                                    left--;
                                }
                            }
                        }
                    }
                }
            }
            chunk.swapSlices();
        }
        chunk.stopInterpolation();
        return out;
    }
}
