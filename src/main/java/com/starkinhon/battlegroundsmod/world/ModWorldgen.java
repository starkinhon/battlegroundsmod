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
    // The Montanhas Paozu: the karst spires (worldgen/placed_feature/karst_spire).
    public static final Supplier<KarstSpireFeature> KARST_SPIRE = FEATURES.register("karst_spire",
            () -> new KarstSpireFeature(NoneFeatureConfiguration.CODEC));
    // Namek's rock towers and trees (worldgen/placed_feature/namek_spire, namek_tree; tools/biomes/namek.py).
    public static final Supplier<NamekSpireFeature> NAMEK_SPIRE = FEATURES.register("namek_spire",
            () -> new NamekSpireFeature(NoneFeatureConfiguration.CODEC));
    public static final Supplier<NamekTreeFeature> NAMEK_TREE = FEATURES.register("namek_tree",
            () -> new NamekTreeFeature(NoneFeatureConfiguration.CODEC));

    // The woods of the Floresta da Folha: its giant and medium trees, logs, bushes and ground, chunk by chunk
    // (worldgen/placed_feature/leaf_forest; LeafTreeGen).
    public static final Supplier<LeafForestFeature> LEAF_FOREST = FEATURES.register("leaf_forest",
            () -> new LeafForestFeature(NoneFeatureConfiguration.CODEC));
    // The Floresta da Morte's woods, mushrooms, flowers, pools and ground (DeathForestFeature).
    public static final Supplier<DeathForestFeature> DEATH_FOREST = FEATURES.register("death_forest",
            () -> new DeathForestFeature(NoneFeatureConfiguration.CODEC));
    // The Deserto do Vento's dunes, wind-carved rock, bones and dead trees (WindDesertFeature).
    public static final Supplier<WindDesertFeature> WIND_DESERT = FEATURES.register("wind_desert",
            () -> new WindDesertFeature(NoneFeatureConfiguration.CODEC));
    // The Mar da Névoa's stone pillars, islets, needles, old piers and rope bridges (MistSeaFeature).
    public static final Supplier<MistSeaFeature> MIST_SEA = FEATURES.register("mist_sea",
            () -> new MistSeaFeature(NoneFeatureConfiguration.CODEC));
    // The Vila da Chuva's ground, metal towers, stumps and pipes (RainVillageFeature).
    public static final Supplier<RainVillageFeature> RAIN_VILLAGE = FEATURES.register("rain_village",
            () -> new RainVillageFeature(NoneFeatureConfiguration.CODEC));
    // The Vale do Fim's valley with the waterfall and the two statues, and the marks of the fight (EndValleyFeature).
    public static final Supplier<EndValleyFeature> END_VALLEY = FEATURES.register("end_valley",
            () -> new EndValleyFeature(NoneFeatureConfiguration.CODEC));
    // The Monte Myoboku's stone spires, giant plants, oil ponds and stone toads (MyobokuFeature).
    public static final Supplier<MyobokuFeature> MYOBOKU = FEATURES.register("myoboku",
            () -> new MyobokuFeature(NoneFeatureConfiguration.CODEC));

    // The Naruto biomes of the normal overworld (put there by NarutoRegion).
    public static final ResourceKey<Biome> FLORESTA_DA_FOLHA = biome("floresta_da_folha");
    public static final ResourceKey<Biome> FLORESTA_DA_MORTE = biome("floresta_da_morte");
    public static final ResourceKey<Biome> DESERTO_DO_VENTO = biome("deserto_do_vento");
    public static final ResourceKey<Biome> MAR_DA_NEVOA = biome("mar_da_nevoa");
    public static final ResourceKey<Biome> VILA_DA_CHUVA = biome("vila_da_chuva");
    public static final ResourceKey<Biome> VALE_DO_FIM = biome("vale_do_fim");
    public static final ResourceKey<Biome> MONTE_MYOBOKU = biome("monte_myoboku");
    public static final TagKey<Biome> NARUTO_BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "universe_naruto"));

    // The Dragon Ball biomes of the normal overworld (tools/biomes/dragonball_biomes.py, put there by DragonRegion).
    public static final ResourceKey<Biome> TERRAS_DO_DRAGAO = biome("terras_do_dragao");
    public static final ResourceKey<Biome> PICOS_DO_DRAGAO = biome("picos_do_dragao");
    public static final ResourceKey<Biome> DESERTO_VERMELHO = biome("deserto_vermelho");
    public static final ResourceKey<Biome> TERRA_SAGRADA = biome("terra_sagrada_de_karin");
    public static final ResourceKey<Biome> MAR_DO_SUL = biome("mar_do_sul");
    public static final ResourceKey<Biome> NORTE_GELADO = biome("norte_gelado");
    public static final ResourceKey<Biome> MONTANHAS_PAOZU = biome("montanhas_paozu");
    public static final ResourceKey<Biome> NAMEK = biome("namek");
    public static final TagKey<Biome> DRAGONBALL_BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "universe_dragonball"));

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name));
    }

    public static final Supplier<MapCodec<MapChunkGenerator>> MAP = CHUNK_GENERATORS.register("map", () -> MapChunkGenerator.CODEC);
    public static final Supplier<MapCodec<MapBiomeSource>> MAP_BIOMES = BIOME_SOURCES.register("map", () -> MapBiomeSource.CODEC);
    // The "Souls" world type's overworld: only the universe biomes (worldgen/world_preset/souls.json, SoulsWorld).
    public static final Supplier<MapCodec<SoulsBiomeSource>> SOULS_BIOMES = BIOME_SOURCES.register("souls", () -> SoulsBiomeSource.CODEC);

    private ModWorldgen() {
    }

    public static void register(IEventBus bus) {
        CHUNK_GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
        FEATURES.register(bus);
        SoulsWorld.register();
    }
}
