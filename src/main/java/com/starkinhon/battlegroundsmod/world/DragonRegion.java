package com.starkinhon.battlegroundsmod.world;

import java.util.function.Consumer;

import com.mojang.datafixers.util.Pair;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

// The Dragon Ball universe as a part of the normal overworld (Stark 2026-11-11: universos viram biomas, "ambiente
// incrível"). A TerraBlender region with the vanilla climate layout where the land biomes become the Dragon World's:
// the mountains (peaks, slopes, windswept hills) Picos do Dragão, the dry ones Deserto Vermelho, the rest Terras do
// Dragão. The warm seas are the Mar do Sul; rivers and the cold seas stay. The shape of the land is vanilla's for that
// climate, so the peaks are real mountains; the pillars and mesas on top are DragonPillarFeature.
public final class DragonRegion extends Region {
    // Against vanilla's 10: about a quarter of the world's regions.
    private static final int WEIGHT = 4;

    private DragonRegion() {
        super(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dragonball"), RegionType.OVERWORLD, WEIGHT);
    }

    public static void register() {
        Regions.register(new DragonRegion());
        SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, BattlegroundsMod.MODID, surface());
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        this.addModifiedVanillaOverworldBiomes(mapper, builder -> {
            for (ResourceKey<Biome> peak : java.util.List.of(Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.STONY_PEAKS,
                    Biomes.SNOWY_SLOPES, Biomes.GROVE, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
                    Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WOODED_BADLANDS)) {
                builder.replaceBiome(peak, ModWorldgen.PICOS_DO_DRAGAO);
            }
            // The savanna's hot dry climate joins the desert, so the Deserto do Diabo spreads wide (Stark).
            for (ResourceKey<Biome> dry : java.util.List.of(Biomes.DESERT, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.SAVANNA)) {
                builder.replaceBiome(dry, ModWorldgen.DESERTO_VERMELHO);
            }
            // Every wooded climate becomes the Terra Sagrada de Karin (the dense forest of Korin's tower): vanilla's forest
            // alone is small patches, so the taigas and birch woods join it and the forest spreads wide (Stark: "é como
            // se ele fosse minúsculo").
            for (ResourceKey<Biome> woods : java.util.List.of(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.DARK_FOREST,
                    Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
                    Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_TAIGA, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE,
                    Biomes.BAMBOO_JUNGLE, Biomes.MANGROVE_SWAMP)) {
                builder.replaceBiome(woods, ModWorldgen.TERRA_SAGRADA);
            }
            // The warm and temperate seas and their beaches become the Mar do Sul (Stark 2026-11-11: the Kame House's
            // tropical islands, pirates, a strong sun). The cold and frozen seas stay for the Norte Gelado.
            for (ResourceKey<Biome> sea : java.util.List.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN,
                    Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.BEACH)) {
                builder.replaceBiome(sea, ModWorldgen.MAR_DO_SUL);
            }
            for (ResourceKey<Biome> green : java.util.List.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS,
                    Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.SWAMP)) {
                builder.replaceBiome(green, ModWorldgen.TERRAS_DO_DRAGAO);
            }
        });
    }

    // The ground of each biome: the peaks striped like badlands (orange, white, yellow, red: the Dragon World's rock)
    // with patches of grass, the desert its own sand and rock (ModBlocks). Terras do Dragão is grass with patches of bare
    // yellow ground (yellow terracotta, its edge a ring of plain terracotta), like the anime's dry yellow earth (Stark).
    private static SurfaceRules.RuleSource surface() {
        SurfaceRules.RuleSource grass = SurfaceRules.state(Blocks.GRASS_BLOCK.defaultBlockState());
        SurfaceRules.ConditionSource water = SurfaceRules.waterBlockCheck(-1, 0);
        SurfaceRules.RuleSource peaks = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.PICOS_DO_DRAGAO), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.not(SurfaceRules.steep()), SurfaceRules.ifTrue(water,
                                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, -0.1, 1.0), grass))),
                        SurfaceRules.bandlands())),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.bandlands()),
                SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, true, 8, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                        SurfaceRules.bandlands())));
        // The Deserto do Diabo (Stark's Sand Land reference): orange-yellow sand, flat sandstone slabs breaking through it
        // here and there, the slopes bare rock, rock under the sand.
        SurfaceRules.RuleSource sand = SurfaceRules.state(com.starkinhon.battlegroundsmod.ModBlocks.DESERT_SAND.get().defaultBlockState());
        SurfaceRules.RuleSource rock = SurfaceRules.state(com.starkinhon.battlegroundsmod.ModBlocks.DESERT_ROCK.get().defaultBlockState());
        SurfaceRules.RuleSource lightRock = SurfaceRules.state(com.starkinhon.battlegroundsmod.ModBlocks.DESERT_ROCK_LIGHT.get().defaultBlockState());
        SurfaceRules.ConditionSource slab = SurfaceRules.noiseCondition(Noises.SURFACE, 0.38, Double.MAX_VALUE);
        SurfaceRules.ConditionSource slabEdge = SurfaceRules.noiseCondition(Noises.SURFACE, 0.32, 0.38);
        SurfaceRules.RuleSource desert = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.DESERTO_VERMELHO), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.ifTrue(water, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.steep(), rock), SurfaceRules.ifTrue(slab, lightRock),
                        SurfaceRules.ifTrue(slabEdge, rock), sand))),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, rock),
                SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, true, 6, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                        rock)));
        SurfaceRules.RuleSource yellow = SurfaceRules.state(Blocks.YELLOW_TERRACOTTA.defaultBlockState());
        SurfaceRules.RuleSource plain = SurfaceRules.state(Blocks.TERRACOTTA.defaultBlockState());
        SurfaceRules.ConditionSource patch = SurfaceRules.noiseCondition(Noises.SURFACE, 0.34, Double.MAX_VALUE);
        SurfaceRules.ConditionSource rim = SurfaceRules.noiseCondition(Noises.SURFACE, 0.28, 0.34);
        SurfaceRules.RuleSource lands = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.TERRAS_DO_DRAGAO), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.ifTrue(water, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(patch, yellow), SurfaceRules.ifTrue(rim, plain)))),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(patch, yellow), SurfaceRules.ifTrue(rim, plain)))));
        // The Mar do Sul: white sand on the beaches and all over the sea floor (the turquoise water over it), sandstone under.
        SurfaceRules.RuleSource white = SurfaceRules.state(Blocks.SAND.defaultBlockState());
        SurfaceRules.RuleSource sea = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.MAR_DO_SUL), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, white),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, white),
                SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, true, 6, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                        SurfaceRules.state(Blocks.SANDSTONE.defaultBlockState()))));
        return SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(peaks, desert, lands, sea));
    }
}
