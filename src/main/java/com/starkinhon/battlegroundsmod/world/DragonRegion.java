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
// Dragão. Oceans, rivers and beaches stay (Kame House needs its island). The shape of the land is vanilla's for that
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
            for (ResourceKey<Biome> dry : java.util.List.of(Biomes.DESERT, Biomes.BADLANDS, Biomes.ERODED_BADLANDS)) {
                builder.replaceBiome(dry, ModWorldgen.DESERTO_VERMELHO);
            }
            // The thick woods become the Terra Sagrada de Karin (the dense forest of Korin's tower).
            for (ResourceKey<Biome> woods : java.util.List.of(Biomes.FOREST, Biomes.DARK_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
                    Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.MANGROVE_SWAMP)) {
                builder.replaceBiome(woods, ModWorldgen.TERRA_SAGRADA);
            }
            for (ResourceKey<Biome> green : java.util.List.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SAVANNA,
                    Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.TAIGA,
                    Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA,
                    Biomes.ICE_SPIKES, Biomes.SWAMP)) {
                builder.replaceBiome(green, ModWorldgen.TERRAS_DO_DRAGAO);
            }
        });
    }

    // The ground of each biome: the peaks striped like badlands (orange, white, yellow, red: the Dragon World's rock)
    // with patches of grass, the desert red sand over red sandstone. Terras do Dragão keeps vanilla grass and dirt.
    private static SurfaceRules.RuleSource surface() {
        SurfaceRules.RuleSource redSand = SurfaceRules.state(Blocks.RED_SAND.defaultBlockState());
        SurfaceRules.RuleSource redSandstone = SurfaceRules.state(Blocks.RED_SANDSTONE.defaultBlockState());
        SurfaceRules.RuleSource grass = SurfaceRules.state(Blocks.GRASS_BLOCK.defaultBlockState());
        SurfaceRules.RuleSource orange = SurfaceRules.state(Blocks.ORANGE_TERRACOTTA.defaultBlockState());
        SurfaceRules.ConditionSource water = SurfaceRules.waterBlockCheck(-1, 0);
        SurfaceRules.RuleSource peaks = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.PICOS_DO_DRAGAO), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.not(SurfaceRules.steep()), SurfaceRules.ifTrue(water,
                                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, -0.1, 1.0), grass))),
                        SurfaceRules.bandlands())),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.bandlands()),
                SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, true, 8, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                        SurfaceRules.bandlands())));
        SurfaceRules.RuleSource desert = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.DESERTO_VERMELHO), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.ifTrue(water, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.steep(), orange), redSand))),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.steep(), orange), redSandstone)),
                SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, true, 6, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                        redSandstone)));
        return SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(peaks, desert));
    }
}
