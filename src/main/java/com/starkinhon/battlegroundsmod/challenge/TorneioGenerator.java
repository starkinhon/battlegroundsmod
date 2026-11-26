// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.challenge;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

// The Torneio do Poder's dimension (Stark 2026-11-26: "ela não pode ser oca, preenche com aquela pedra"): empty space,
// and the inside of the giant top as solid rock, made with the chunks (fast, off the main thread, lit by the game). The
// arena file (tools/arenas/arena_torneio.py, placed by Torneio) brings the painted floor, the rim, the painted skin of
// the cone, the stem, the pillar and everything floating round it. coneBottom here and cone_bottom there are the same
// formula, so the rock fills the cone exactly to its skin.
public class TorneioGenerator extends ChunkGenerator {
    public static final MapCodec<TorneioGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, TorneioGenerator::new));

    // as in arena_torneio.py (K = 2)
    static final int K = 2; // arena_torneio.py's scale (Stark 2026-11-26: "faz um 2x só")
    static final int RA = 140 * K;
    static final int RIM = 8 * K;
    static final int CONE = 98 * K;
    static final int STEM_R = 6 * K;
    private static final int MIN_Y = 0;
    private static final int HEIGHT = 1024;

    public TorneioGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // y (relative to the stage's floor) of the cone's underside under a column at distance r from the middle
    static int coneBottom(double r) {
        double rr = Math.max(r, STEM_R);
        return (int) Math.floor(-RIM - CONE * (1 - rr / RA) + 0.5);
    }

    // the rock inside the top in column (x, z): from just over the underside to just under the floor, or none
    private static boolean inside(int x, int z) {
        return x * x + z * z <= RA * RA;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures,
            ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int floor = Torneio.FLOOR_Y;
        // the whole chunk is outside the top's reach: nothing to do
        int fx = Math.min(Math.abs(cp.getMinBlockX()), Math.abs(cp.getMaxBlockX())), fz = Math.min(Math.abs(cp.getMinBlockZ()), Math.abs(cp.getMaxBlockZ()));
        boolean crossX = cp.getMinBlockX() <= 0 && cp.getMaxBlockX() >= 0, crossZ = cp.getMinBlockZ() <= 0 && cp.getMaxBlockZ() >= 0;
        long near = (long) (crossX ? 0 : fx) * (crossX ? 0 : fx) + (long) (crossZ ? 0 : fz) * (crossZ ? 0 : fz);
        if (near > (long) RA * RA) {
            return CompletableFuture.completedFuture(chunk);
        }
        BlockState rock = ModBlocks.TORNEIO_ROCK.get().defaultBlockState();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                if (!inside(x, z)) {
                    continue;
                }
                int y0 = floor + coneBottom(Math.sqrt((double) x * x + (double) z * z)) + 1;
                for (int y = Math.max(y0, MIN_Y); y <= floor - 1; y++) {
                    chunk.setBlockState(pos.set(x, y, z), rock, false);
                }
                ocean.update(lx, floor - 1, lz, rock);
                surface.update(lx, floor - 1, lz, rock);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return inside(x, z) ? Torneio.FLOOR_Y : MIN_Y;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[level.getHeight()];
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean in = inside(x, z);
        int y0 = in ? Torneio.FLOOR_Y + coneBottom(Math.sqrt((double) x * x + (double) z * z)) + 1 : Integer.MAX_VALUE;
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            states[i] = in && y >= y0 && y < Torneio.FLOOR_Y ? ModBlocks.TORNEIO_ROCK.get().defaultBlockState() : air;
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
            StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return MIN_Y;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Torneio do Poder");
    }
}
