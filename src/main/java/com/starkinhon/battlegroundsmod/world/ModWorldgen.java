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

    // The Dragon Ball biomes of the normal overworld (tools/biomes/dragonball_biomes.py, put there by DragonRegion).
    public static final ResourceKey<Biome> TERRAS_DO_DRAGAO = biome("terras_do_dragao");
    public static final ResourceKey<Biome> PICOS_DO_DRAGAO = biome("picos_do_dragao");
    public static final ResourceKey<Biome> DESERTO_VERMELHO = biome("deserto_vermelho");
    public static final ResourceKey<Biome> TERRA_SAGRADA = biome("terra_sagrada_de_karin");
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
