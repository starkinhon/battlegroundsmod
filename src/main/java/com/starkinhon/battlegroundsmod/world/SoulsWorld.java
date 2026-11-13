package com.starkinhon.battlegroundsmod.world;

import java.util.List;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import terrablender.api.SurfaceRuleManager;
import terrablender.worldgen.IExtendedNoiseGeneratorSettings;

// The "Souls" world type (worldgen/world_preset/souls.json, listed with Default and Amplified on the create world
// screen): the overworld with only the universe biomes (SoulsBiomeSource), the vanilla Nether and End. The normal
// world type is untouched. This class does the three things the json can't:
// - the universes' own ground (the Dragon Ball desert sand, the yellow earth, the snow and ice...) comes from the surface
//   rules the mod gives TerraBlender, and TerraBlender only switches them on for a MultiNoiseBiomeSource, so here they
//   are switched on for this world's overworld too;
// - the first spawn is on dry land in the Terras do Dragão, where the mod's journey starts (Kuririn, Capsule Corp),
//   instead of wherever vanilla's spawn search lands (a lake, the Vale do Fim's waterfall, the Floresta da Morte);
// - the farm animals (cows, sheep, pigs, chickens) graze on the universe lands here: the universe biomes keep vanilla
//   animals out on purpose, but in this world there is no vanilla land left, and without them there is no food, wool
//   for beds or leather for books. Only in this world type, only on grass (vanilla's spawn rules), the biomes' own
//   jsons untouched.
public final class SoulsWorld {
    private static final List<MobSpawnSettings.SpawnerData> ANIMALS = List.of(
            new MobSpawnSettings.SpawnerData(EntityType.SHEEP, 12, 2, 4),
            new MobSpawnSettings.SpawnerData(EntityType.PIG, 10, 2, 4),
            new MobSpawnSettings.SpawnerData(EntityType.CHICKEN, 10, 2, 4),
            new MobSpawnSettings.SpawnerData(EntityType.COW, 8, 2, 4));

    private SoulsWorld() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(SoulsWorld::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(SoulsWorld::onCreateSpawn);
        NeoForge.EVENT_BUS.addListener(SoulsWorld::onPotentialSpawns);
    }

    public static boolean is(ChunkGenerator generator) {
        return generator.getBiomeSource() instanceof SoulsBiomeSource;
    }

    public static boolean is(LevelAccessor level) {
        return level instanceof ServerLevel server && is(server.getChunkSource().getGenerator());
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        for (LevelStem stem : server.registryAccess().registryOrThrow(Registries.LEVEL_STEM)) {
            if (stem.generator() instanceof NoiseBasedChunkGenerator noise && is(noise)) {
                ((IExtendedNoiseGeneratorSettings) (Object) noise.generatorSettings().value())
                        .setRuleCategory(SurfaceRuleManager.RuleCategory.OVERWORLD);
            }
        }
    }

    private static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !is(level)) {
            return;
        }
        BlockPos spawn = findSpawn(level, biome -> biome.is(ModWorldgen.TERRAS_DO_DRAGAO));
        if (spawn == null) {
            // no dry Terras do Dragão near (it would take a strange seed): any universe land
            spawn = findSpawn(level, biome -> !biome.is(ModWorldgen.MAR_DO_SUL) && !biome.is(ModWorldgen.MAR_DA_NEVOA)
                    && (biome.is(ModWorldgen.DRAGONBALL_BIOMES) || biome.is(ModWorldgen.NARUTO_BIOMES)
                    || biome.is(ModWorldgen.JUJUTSU_BIOMES)));
        }
        if (spawn == null) {
            // vanilla's own search
            return;
        }
        BlockPos at = spawn;
        event.getSettings().setSpawn(spawn, 0.0F);
        event.setCanceled(true);
        // cancelling skips vanilla's bonus chest too, so it is placed here when the world asked for one
        if (level.getServer().getWorldData().worldGenOptions().generateBonusChest()) {
            level.registryAccess().registry(Registries.CONFIGURED_FEATURE)
                    .flatMap(features -> features.getHolder(MiscOverworldFeatures.BONUS_CHEST))
                    .ifPresent(feature -> feature.value().place(level, level.getChunkSource().getGenerator(), level.random, at));
        }
    }

    // The nearest biome of the kind to the world's center, found from the biome noise alone (nothing generated), then a
    // column on it that is dry land above the sea: the base height from the noise, with no water over it.
    private static BlockPos findSpawn(ServerLevel level, java.util.function.Predicate<Holder<Biome>> kind) {
        Pair<BlockPos, Holder<Biome>> lands = level.findClosestBiome3d(kind,
                new BlockPos(0, level.getSeaLevel(), 0), 6400, 32, 64);
        if (lands == null) {
            return null;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        BlockPos center = lands.getFirst();
        // a spiral of columns 8 blocks apart around it, up to 96 blocks out
        for (int ring = 0; ring <= 12; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = center.getX() + dx * 8;
                    int z = center.getZ() + dz * 8;
                    int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    if (floor != surface || floor <= level.getSeaLevel()) {
                        continue;
                    }
                    if (!kind.test(generator.getBiomeSource().getNoiseBiome(x >> 2, floor >> 2, z >> 2, random.sampler()))) {
                        continue;
                    }
                    return new BlockPos(x, floor, z);
                }
            }
        }
        return null;
    }

    private static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.CREATURE || !is(event.getLevel())) {
            return;
        }
        Holder<Biome> biome = event.getLevel().getBiome(event.getPos());
        if (!biome.is(ModWorldgen.DRAGONBALL_BIOMES) && !biome.is(ModWorldgen.NARUTO_BIOMES) && !biome.is(ModWorldgen.JUJUTSU_BIOMES)) {
            return;
        }
        if (biome.is(ModWorldgen.MAR_DO_SUL) || biome.is(ModWorldgen.MAR_DA_NEVOA)) {
            return;
        }
        for (MobSpawnSettings.SpawnerData animal : ANIMALS) {
            event.addSpawnerData(animal);
        }
    }
}
