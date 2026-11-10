package com.starkinhon.battlegroundsmod.world;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

// Builds a universe from a drawn map (WorldMap): each column gets the height and the ground the picture says, the sea
// fills up to level 63, and the biomes' own trees, grass, snow and ores go on top (MapBiomeSource). No caves, no
// villages or other vanilla structures: the places of the universe are built by the mod.
public class MapChunkGenerator extends ChunkGenerator {
    public static final MapCodec<MapChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            Codec.STRING.fieldOf("map").forGetter(generator -> generator.mapName))
            .apply(instance, MapChunkGenerator::new));

    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;

    private final String mapName;

    public MapChunkGenerator(BiomeSource biomeSource, String mapName) {
        super(biomeSource);
        this.mapName = mapName;
    }

    public String mapName() {
        return mapName;
    }

    public WorldMap map() {
        return WorldMap.get(mapName);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures,
            ChunkAccess chunk) {
        WorldMap map = map();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = chunk.getPos();
        int minY = Math.max(chunk.getMinBuildHeight(), MIN_Y);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkPos.getMinBlockX() + lx;
                int z = chunkPos.getMinBlockZ() + lz;
                int top = map.ground(x, z);
                int kind = map.kind(x, z);
                int slope = slope(map, x, z, top);
                for (int y = minY; y <= top; y++) {
                    BlockState state = block(kind, x, y, z, top, slope, minY);
                    chunk.setBlockState(pos.set(x, y, z), state, false);
                    oceanFloor.update(lx, y, lz, state);
                    surface.update(lx, y, lz, state);
                }
                for (int y = top + 1; y < WorldMap.SEA; y++) {
                    BlockState water = Blocks.WATER.defaultBlockState();
                    chunk.setBlockState(pos.set(x, y, z), water, false);
                    surface.update(lx, y, lz, water);
                }
            }
        }
        if ("namek".equals(mapName)) {
            namekPlants(chunk, map, surface);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    // Namek's plants (Stark's refs, 2026-11-10): the Namekian trees, a long thin trunk with a round ball of leaves on
    // top (the biome colors the leaves blue), tall ones and short ones, and tufts of blue grass. Every tree fits inside
    // its own chunk, so they can be grown while the chunk is made.
    private static void namekPlants(ChunkAccess chunk, WorldMap map, Heightmap surface) {
        ChunkPos cp = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        int roll = hash(cp.x, 77, cp.z) % 100;
        int trees = roll < 30 ? 0 : roll < 70 ? 1 : roll < 90 ? 2 : 3;
        for (int t = 0; t < trees; t++) {
            int lx = 3 + hash(cp.x, t * 7 + 1, cp.z) % 10;
            int lz = 3 + hash(cp.x, t * 7 + 2, cp.z) % 10;
            int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
            int kind = map.kind(x, z);
            int top = map.ground(x, z);
            if ((kind != WorldMap.NGRASS && kind != WorldMap.NMESA) || top < WorldMap.SEA || slope(map, x, z, top) >= 2) {
                continue;
            }
            boolean tall = hash(x, 5, z) % 3 == 0;
            int h = tall ? 14 + hash(x, 6, z) % 9 : 6 + hash(x, 6, z) % 6;
            double r = tall ? 2.9 : 1.9;
            for (int y = top + 1; y <= top + h; y++) {
                chunk.setBlockState(pos.set(x, y, z), log, false);
                surface.update(lx, y, lz, log);
            }
            int cy = top + h + (int) Math.round(r * 0.6);
            int R = (int) Math.ceil(r);
            for (int dx = -R; dx <= R; dx++) {
                for (int dy = -R; dy <= R; dy++) {
                    for (int dz = -R; dz <= R; dz++) {
                        double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz);
                        if (d > r + (hash(x + dx, cy + dy, z + dz) % 100) / 400.0) {
                            continue;
                        }
                        pos.set(x + dx, cy + dy, z + dz);
                        if (chunk.getBlockState(pos).isAir()) {
                            chunk.setBlockState(pos, leaves, false);
                            surface.update(lx + dx, cy + dy, lz + dz, leaves);
                        }
                    }
                }
            }
        }
        BlockState tuft = Blocks.SHORT_GRASS.defaultBlockState();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                int top = map.ground(x, z);
                if (top < WorldMap.SEA || hash(x, top, z) % 100 >= 10
                        || !chunk.getBlockState(pos.set(x, top, z)).is(Blocks.GRASS_BLOCK)
                        || !chunk.getBlockState(pos.set(x, top + 1, z)).isAir()) {
                    continue;
                }
                chunk.setBlockState(pos.set(x, top + 1, z), tuft, false);
            }
        }
    }

    // How steep the ground is around this column (the biggest step to a neighbor).
    private static int slope(WorldMap map, int x, int z, int top) {
        int s = 0;
        s = Math.max(s, Math.abs(map.ground(x + 1, z) - top));
        s = Math.max(s, Math.abs(map.ground(x - 1, z) - top));
        s = Math.max(s, Math.abs(map.ground(x, z + 1) - top));
        s = Math.max(s, Math.abs(map.ground(x, z - 1) - top));
        return s;
    }

    private static BlockState NAMEK_ROCK() {
        return com.starkinhon.battlegroundsmod.ModBlocks.NAMEK_ROCK.get().defaultBlockState();
    }

    private static BlockState NAMEK_LAYER() {
        return com.starkinhon.battlegroundsmod.ModBlocks.NAMEK_ROCK_LAYER.get().defaultBlockState();
    }

    private static BlockState NAMEK_SAND() {
        return com.starkinhon.battlegroundsmod.ModBlocks.NAMEK_SAND.get().defaultBlockState();
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    // Smooth noise from 0 to 1 that changes over about "cell" blocks (patches of red sand, wavy layers).
    private static double smooth(int x, int z, int cell) {
        int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
        double fx = (double) Math.floorMod(x, cell) / cell, fz = (double) Math.floorMod(z, cell) / cell;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(gx, cell, gz) % 1000 / 1000.0, b = hash(gx + 1, cell, gz) % 1000 / 1000.0;
        double c = hash(gx, cell, gz + 1) % 1000 / 1000.0, e = hash(gx + 1, cell, gz + 1) % 1000 / 1000.0;
        return (a + (b - a) * fx) + ((c + (e - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    // The block at height y of a column of that kind whose top is "top".
    private static BlockState block(int kind, int x, int y, int z, int top, int slope, int minY) {
        if (y == minY) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        if (y < 0) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        int d = top - y;
        int r = hash(x, y, z) % 100;
        boolean steep = slope >= 3;
        switch (kind) {
            case WorldMap.DEEP:
                return d < 3 ? (r < 25 ? Blocks.GRAVEL : r < 35 ? Blocks.CLAY : Blocks.SAND).defaultBlockState()
                        : Blocks.STONE.defaultBlockState();
            case WorldMap.SHALLOW:
                return d < 4 ? Blocks.SAND.defaultBlockState() : d < 6 ? Blocks.SANDSTONE.defaultBlockState()
                        : Blocks.STONE.defaultBlockState();
            case WorldMap.SAND:
                return d < 4 ? Blocks.SAND.defaultBlockState() : d < 8 ? Blocks.SANDSTONE.defaultBlockState()
                        : Blocks.STONE.defaultBlockState();
            case WorldMap.DESERT: {
                // Yellow sand with patches of red sand, terracotta under it (Stark, 2026-11-10).
                boolean red = smooth(x, z, 20) > 0.62;
                if (d < 3) {
                    return (red ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState();
                }
                if (d < 8) {
                    return (red ? Blocks.ORANGE_TERRACOTTA : r < 60 ? Blocks.SANDSTONE : Blocks.YELLOW_TERRACOTTA).defaultBlockState();
                }
                return Blocks.STONE.defaultBlockState();
            }
            case WorldMap.CANYON: {
                // The yellow mountains of the desert: layers of yellow terracotta, wavy, sand on the flat tops.
                if (d == 0 && !steep) {
                    return (smooth(x, z, 9) > 0.6 ? Blocks.YELLOW_TERRACOTTA : Blocks.SAND).defaultBlockState();
                }
                if (y < 58) {
                    return Blocks.STONE.defaultBlockState();
                }
                int band = Math.floorMod(y + (int) (smooth(x, z, 28) * 5), 9);
                return switch (band) {
                    case 0, 1, 2 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
                    case 3 -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    case 4 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
                    case 5 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
                    case 6 -> Blocks.WHITE_TERRACOTTA.defaultBlockState();
                    case 7 -> Blocks.TERRACOTTA.defaultBlockState();
                    default -> Blocks.SANDSTONE.defaultBlockState();
                };
            }
            case WorldMap.GRASS:
            case WorldMap.FOREST:
                if (d == 0) {
                    if (steep && slope >= 4) {
                        return (r < 50 ? Blocks.STONE : Blocks.ANDESITE).defaultBlockState();
                    }
                    return (kind == WorldMap.FOREST && r < 12 ? Blocks.PODZOL : Blocks.GRASS_BLOCK).defaultBlockState();
                }
                return d < 4 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
            case WorldMap.SNOW:
                if (d == 0) {
                    return (steep && slope >= 4 ? (r < 60 ? Blocks.STONE : Blocks.PACKED_ICE) : Blocks.SNOW_BLOCK).defaultBlockState();
                }
                return d < 3 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
            case WorldMap.ROCK:
                if (d <= 1) {
                    if (steep || top > 88) {
                        return (r < 45 ? Blocks.STONE : r < 75 ? Blocks.GRANITE : Blocks.ANDESITE).defaultBlockState();
                    }
                    return (r < 40 ? Blocks.COARSE_DIRT : r < 60 ? Blocks.GRANITE : r < 80 ? Blocks.BROWN_TERRACOTTA
                            : Blocks.GRASS_BLOCK).defaultBlockState();
                }
                return d < 4 ? (r < 50 ? Blocks.DIRT : Blocks.GRANITE).defaultBlockState() : Blocks.STONE.defaultBlockState();
            case WorldMap.REDROCK:
                if (d == 0 && !steep) {
                    return Blocks.RED_SAND.defaultBlockState();
                }
                if (y < 60) {
                    return Blocks.STONE.defaultBlockState();
                }
                return switch (Math.floorMod(y + (int) (smooth(x, z, 24) * 4), 8)) {
                    case 0, 1 -> Blocks.RED_TERRACOTTA.defaultBlockState();
                    case 2 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
                    case 3 -> Blocks.TERRACOTTA.defaultBlockState();
                    case 4 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
                    case 5 -> Blocks.RED_TERRACOTTA.defaultBlockState();
                    case 6 -> Blocks.BROWN_TERRACOTTA.defaultBlockState();
                    default -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
                };
            case WorldMap.CRATER:
                // Ground blasted by a falling pod: scorched dirt, gravel, a few cooled stones.
                if (d == 0) {
                    return (r < 40 ? Blocks.COARSE_DIRT : r < 60 ? Blocks.PACKED_MUD : r < 75 ? Blocks.GRAVEL
                            : r < 90 ? Blocks.ROOTED_DIRT : Blocks.TUFF).defaultBlockState();
                }
                return d < 4 ? (r < 60 ? Blocks.DIRT : Blocks.COARSE_DIRT).defaultBlockState() : Blocks.STONE.defaultBlockState();
            case WorldMap.NSEA:
                return d < 3 ? (r < 70 ? NAMEK_SAND() : NAMEK_ROCK()) : Blocks.STONE.defaultBlockState();
            case WorldMap.NSAND:
                if (d == 0 && !steep) {
                    return NAMEK_SAND();
                }
                return y < 40 ? Blocks.STONE.defaultBlockState() : NAMEK_ROCK();
            case WorldMap.NGRASS:
            case WorldMap.NMESA: {
                // Blue grass on top (the biome tints it), the cliffs of pale pink-beige rock in layers under it.
                if (d == 0 && !(steep && slope >= 3)) {
                    return Blocks.GRASS_BLOCK.defaultBlockState();
                }
                if (kind == WorldMap.NGRASS && d < 3 && !steep) {
                    return Blocks.DIRT.defaultBlockState();
                }
                if (y < 40) {
                    return Blocks.STONE.defaultBlockState();
                }
                int band = Math.floorMod(y + (int) (smooth(x, z, 22) * 4), kind == WorldMap.NMESA ? 7 : 5);
                return band == 0 || (kind == WorldMap.NMESA && band == 4 && r < 60) ? NAMEK_LAYER() : NAMEK_ROCK();
            }
            case WorldMap.LAVA:
                if (d == 0) {
                    return Blocks.LAVA.defaultBlockState();
                }
                return d < 3 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            case WorldMap.DARK:
            default:
                if (d <= 1) {
                    return (r < 50 ? Blocks.BLACKSTONE : r < 80 ? Blocks.BASALT : Blocks.TUFF).defaultBlockState();
                }
                return (r < 60 ? Blocks.BLACKSTONE : Blocks.STONE).defaultBlockState();
        }
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int top = map().ground(x, z);
        if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) {
            return top + 1;
        }
        return Math.max(top, WorldMap.SEA - 1) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        WorldMap map = map();
        int top = map.ground(x, z);
        int kind = map.kind(x, z);
        int slope = slope(map, x, z, top);
        int minY = Math.max(level.getMinBuildHeight(), MIN_Y);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y < minY) {
                states[i] = Blocks.AIR.defaultBlockState();
            } else if (y <= top) {
                states[i] = block(kind, x, y, z, top, slope, minY);
            } else if (y < WorldMap.SEA) {
                states[i] = Blocks.WATER.defaultBlockState();
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    // No trees or grass where a building goes (Stark's test, 2026-11-10: oaks grew into the Capsule Corp dome, because
    // the chunks around are decorated after the building is put there).
    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        net.minecraft.world.level.ChunkPos pos = chunk.getPos();
        if (MapPlaces.keptClear(mapName(), pos.getMiddleBlockX(), pos.getMiddleBlockZ())) {
            return;
        }
        super.applyBiomeDecoration(level, chunk, structures);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    // No villages, shipwrecks, monuments...: the universe's places are the mod's.
    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
            StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        ChunkPos center = level.getCenter();
        Holder<Biome> biome = level.getBiome(center.getWorldPosition().atY(level.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(level.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(level, biome, center, random);
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return WorldMap.SEA;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        WorldMap map = map();
        info.add("Mapa " + mapName + ": " + WorldMap.NAMES[map.kind(pos.getX(), pos.getZ())]
                + " chão " + map.ground(pos.getX(), pos.getZ()));
    }
}
