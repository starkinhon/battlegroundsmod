package com.starkinhon.battlegroundsmod.world;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// The world generator for drawn universes ("type": "battlegroundsmod:map" in a dimension's json).
public final class ModWorldgen {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, BattlegroundsMod.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, BattlegroundsMod.MODID);

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, BattlegroundsMod.MODID);
    // The rock pillars and mesas of the Dragon World (worldgen/placed_feature/dragon_pillar_*).
    public static final Supplier<DragonPillarFeature> DRAGON_PILLAR = FEATURES.register("dragon_pillar",
            () -> new DragonPillarFeature(NoneFeatureConfiguration.CODEC));
    // The sandstone hoodoos, table rocks and boulders of the Deserto do Diabo (worldgen/placed_feature/desert_rock).
    public static final Supplier<DesertRockFeature> DESERT_ROCK = FEATURES.register("desert_rock",
            () -> new DesertRockFeature(NoneFeatureConfiguration.CODEC));
    // The round puffy trees of the Terra Sagrada de Karin (worldgen/placed_feature/puffy_tree_karin).
    public static final Supplier<PuffyTreeFeature> PUFFY_TREE = FEATURES.register("puffy_tree",
            () -> new PuffyTreeFeature(NoneFeatureConfiguration.CODEC));
    // The Mar do Sul: palms, little tropical islands, pirate ships (worldgen/placed_feature/palm_tree_*, tropical_island,
    // pirate_ship).
    public static final Supplier<PalmTreeFeature> PALM_TREE = FEATURES.register("palm_tree",
            () -> new PalmTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final Supplier<TropicalIslandFeature> TROPICAL_ISLAND = FEATURES.register("tropical_island",
            () -> new TropicalIslandFeature(NoneFeatureConfiguration.CODEC));
    public static final Supplier<PirateShipFeature> PIRATE_SHIP = FEATURES.register("pirate_ship",
            () -> new PirateShipFeature(NoneFeatureConfiguration.CODEC));
    // The Norte Gelado: ice crystal clusters and snow-laden pines (worldgen/placed_feature/ice_crystal, snow_pine).
    public static final Supplier<IceCrystalFeature> ICE_CRYSTAL = FEATURES.register("ice_crystal",
            () -> new IceCrystalFeature(NoneFeatureConfiguration.CODEC));
    public static final Supplier<SnowPineFeature> SNOW_PINE = FEATURES.register("snow_pine",
            () -> new SnowPineFeature(NoneFeatureConfiguration.CODEC));

    // The Dragon Ball biomes of the normal overworld (tools/biomes/dragonball_biomes.py, put there by DragonRegion).
    public static final ResourceKey<Biome> TERRAS_DO_DRAGAO = biome("terras_do_dragao");
    public static final ResourceKey<Biome> PICOS_DO_DRAGAO = biome("picos_do_dragao");
    public static final ResourceKey<Biome> DESERTO_VERMELHO = biome("deserto_vermelho");
    public static final ResourceKey<Biome> TERRA_SAGRADA = biome("terra_sagrada_de_karin");
    public static final ResourceKey<Biome> MAR_DO_SUL = biome("mar_do_sul");
    public static final ResourceKey<Biome> NORTE_GELADO = biome("norte_gelado");
    public static final TagKey<Biome> DRAGONBALL_BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "universe_dragonball"));

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name));
    }

    public static final Supplier<MapCodec<MapChunkGenerator>> MAP = CHUNK_GENERATORS.register("map", () -> MapChunkGenerator.CODEC);
    public static final Supplier<MapCodec<MapBiomeSource>> MAP_BIOMES = BIOME_SOURCES.register("map", () -> MapBiomeSource.CODEC);

    private ModWorldgen() {
    }

    public static void register(IEventBus bus) {
        CHUNK_GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
        FEATURES.register(bus);
    }
}
