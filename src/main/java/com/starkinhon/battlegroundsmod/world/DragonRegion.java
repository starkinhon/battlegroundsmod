package com.starkinhon.battlegroundsmod.world;

import java.util.Set;
import java.util.function.Consumer;

import com.mojang.datafixers.util.Pair;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

// The Dragon Ball universe as a part of the normal overworld (Stark 2026-11-11: universos viram biomas, "ambiente
// incrível"; then "deixe todos bem grandes" and "é pra serem separados", they kept coming glued to each other). One
// TerraBlender region with vanilla's climate layout where each Dragon Ball biome takes whole climate bands, and the
// bands between them stay vanilla, so between two Dragon Ball biomes there is always a stretch of normal land:
// - the frozen band: Norte Gelado (all of it, mountains too);
// - the cold band: vanilla;
// - the temperate band: Montanhas Paozu on its dry side, Terra Sagrada de Karin on its wet side, vanilla between;
// - the warm band: vanilla, but its mountains in the middle humidity are the Picos do Dragão and its seas and
//   beaches the Mar do Sul;
// - the hot band: Deserto do Diabo on its dry and middle side, Terras do Dragão on its wettest, vanilla between.
// Temperature and humidity change slowly, so a band covers a big area; to go from one to another the land passes
// through the band between. Rivers, caves and the mushroom islands stay vanilla, and the land keeps vanilla's shape.
public final class DragonRegion extends Region {
    // Against vanilla's 10.
    private static final int WEIGHT = 8;

    private static final Set<ResourceKey<Biome>> SEAS = Set.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.BEACH, Biomes.SNOWY_BEACH, Biomes.STONY_SHORE);
    private static final Set<ResourceKey<Biome>> NOT_LAND = Set.of(Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.MUSHROOM_FIELDS,
            Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK);
    private static final Set<ResourceKey<Biome>> MOUNTAINS = Set.of(Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.FROZEN_PEAKS,
            Biomes.SNOWY_SLOPES, Biomes.GROVE, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST,
            Biomes.WINDSWEPT_SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WOODED_BADLANDS, Biomes.MEADOW, Biomes.CHERRY_GROVE);

    private DragonRegion() {
        super(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dragonball"), RegionType.OVERWORLD, WEIGHT);
    }

    public static void register() {
        Regions.register(new DragonRegion());
        SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, BattlegroundsMod.MODID, surface());
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        new OverworldBiomeBuilder().addBiomes(pair -> mapper.accept(Pair.of(pair.getFirst(), choose(pair.getFirst(), pair.getSecond()))));
    }

    // The band (0 to 4) a climate parameter's middle falls in, by vanilla's band edges.
    private static int band(Climate.Parameter p, float... edges) {
        float mid = Climate.unquantizeCoord((p.min() + p.max()) / 2);
        int i = 0;
        while (i < edges.length && mid > edges[i]) {
            i++;
        }
        return i;
    }

    private static ResourceKey<Biome> choose(Climate.ParameterPoint point, ResourceKey<Biome> vanilla) {
        if (NOT_LAND.contains(vanilla)) {
            return vanilla;
        }
        int t = band(point.temperature(), -0.45f, -0.15f, 0.2f, 0.55f);
        int h = band(point.humidity(), -0.35f, -0.1f, 0.1f, 0.3f);
        if (SEAS.contains(vanilla)) {
            return t == 3 && vanilla != Biomes.STONY_SHORE ? ModWorldgen.MAR_DO_SUL : vanilla;
        }
        switch (t) {
            case 0:
                return ModWorldgen.NORTE_GELADO;
            case 2:
                return h <= 1 ? ModWorldgen.MONTANHAS_PAOZU : h >= 3 ? ModWorldgen.TERRA_SAGRADA : vanilla;
            case 3:
                return h == 2 && MOUNTAINS.contains(vanilla) ? ModWorldgen.PICOS_DO_DRAGAO : vanilla;
            case 4:
                // the desert also takes the middle humidity (Stark: "muito bonito pra ser tão raro"), Terras do Dragão
                // only the wettest, the band between them stays vanilla
                return h <= 2 ? ModWorldgen.DESERTO_VERMELHO : h == 4 ? ModWorldgen.TERRAS_DO_DRAGAO : vanilla;
            default:
                return vanilla;
        }
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
        // The Norte Gelado: deep snow, glazed patches of packed ice and blue ice like frozen ponds, the steep faces
        // ice-plated stone.
        SurfaceRules.RuleSource snow = SurfaceRules.state(Blocks.SNOW_BLOCK.defaultBlockState());
        SurfaceRules.RuleSource packed = SurfaceRules.state(Blocks.PACKED_ICE.defaultBlockState());
        SurfaceRules.RuleSource blue = SurfaceRules.state(Blocks.BLUE_ICE.defaultBlockState());
        SurfaceRules.RuleSource stone = SurfaceRules.state(Blocks.STONE.defaultBlockState());
        SurfaceRules.RuleSource north = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.NORTE_GELADO), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.ifTrue(water, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.steep(), SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.ICE, -0.2, 0.3), packed), stone)),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, 0.46, Double.MAX_VALUE), blue),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, 0.36, 0.46), packed),
                        snow))),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.ifTrue(SurfaceRules.not(SurfaceRules.steep()), snow))));
        // The Naruto Deserto do Vento (Stark 2026-11-11): plain sand over sandstone, the steep faces bare sandstone; the dunes,
        // the cracked plates and the rocks come from WindDesertFeature.
        SurfaceRules.RuleSource sandstone = SurfaceRules.state(Blocks.SANDSTONE.defaultBlockState());
        SurfaceRules.RuleSource wind = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.DESERTO_DO_VENTO), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.ifTrue(water, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.steep(), sandstone), white))),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.steep(), sandstone), white)),
                SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, true, 6, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                        sandstone)));
        // The Jujutsu Colônia do Jogo do Abate (Stark 2026-11-14: no generic grass around the city): wasted ground of
        // coarse dirt and gravel with patches of dead grass, where the city and its skirt don't reach.
        SurfaceRules.RuleSource coarse = SurfaceRules.state(Blocks.COARSE_DIRT.defaultBlockState());
        SurfaceRules.RuleSource gravel = SurfaceRules.state(Blocks.GRAVEL.defaultBlockState());
        SurfaceRules.RuleSource colony = SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.COLONIA_DO_JOGO_DO_ABATE), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.ifTrue(water, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, 0.25, Double.MAX_VALUE), grass),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, -0.15, 0.05), gravel), coarse))),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.state(Blocks.DIRT.defaultBlockState()))));
        return SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(),
                SurfaceRules.sequence(peaks, desert, lands, sea, north, wind, colony));
    }
}
