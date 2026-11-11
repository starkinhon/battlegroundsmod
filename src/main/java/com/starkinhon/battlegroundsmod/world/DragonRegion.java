package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.List;
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
// incrível"). One TerraBlender region per Dragon Ball biome (Stark's test: "deixe todos bem grandes" and "é pra serem
// separados", they used to come packed side by side in one region): in its region every land climate becomes that
// biome, so it covers the whole region (big), and the vanilla regions around keep the biomes apart. The Mar do Sul's
// region turns only its seas and beaches. Rivers stay, and the shape of the land is vanilla's for each climate, so the
// mountains are real mountains everywhere (the pillars, rocks, spires and crystals on top are each biome's features).
public final class DragonRegion extends Region {
    // Each region against vanilla's 10: together a bit over a third of the world, in big separate patches.
    private static final int WEIGHT = 1;

    // Every vanilla land biome but the rivers, the beaches and the cave biomes.
    private static final List<ResourceKey<Biome>> LAND = List.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SNOWY_PLAINS,
            Biomes.ICE_SPIKES, Biomes.DESERT, Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.FOREST, Biomes.FLOWER_FOREST,
            Biomes.BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU,
            Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_SAVANNA,
            Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.BADLANDS, Biomes.ERODED_BADLANDS,
            Biomes.WOODED_BADLANDS, Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.GROVE, Biomes.SNOWY_SLOPES,
            Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.MUSHROOM_FIELDS, Biomes.STONY_SHORE);
    private static final List<ResourceKey<Biome>> COAST = List.of(Biomes.BEACH, Biomes.SNOWY_BEACH);
    private static final List<ResourceKey<Biome>> SEAS = List.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN);

    private final ResourceKey<Biome> biome;
    private final List<ResourceKey<Biome>> replaced;

    private DragonRegion(String name, ResourceKey<Biome> biome, List<ResourceKey<Biome>> replaced) {
        super(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name), RegionType.OVERWORLD, WEIGHT);
        this.biome = biome;
        this.replaced = replaced;
    }

    public static void register() {
        List<ResourceKey<Biome>> land = new ArrayList<>(LAND);
        land.addAll(COAST);
        Regions.register(new DragonRegion("db_terras", ModWorldgen.TERRAS_DO_DRAGAO, land));
        Regions.register(new DragonRegion("db_picos", ModWorldgen.PICOS_DO_DRAGAO, LAND));
        Regions.register(new DragonRegion("db_deserto", ModWorldgen.DESERTO_VERMELHO, land));
        Regions.register(new DragonRegion("db_karin", ModWorldgen.TERRA_SAGRADA, LAND));
        Regions.register(new DragonRegion("db_paozu", ModWorldgen.MONTANHAS_PAOZU, LAND));
        Regions.register(new DragonRegion("db_norte", ModWorldgen.NORTE_GELADO, land));
        List<ResourceKey<Biome>> sea = new ArrayList<>(SEAS);
        sea.addAll(COAST);
        Regions.register(new DragonRegion("db_mar_do_sul", ModWorldgen.MAR_DO_SUL, sea));
        SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, BattlegroundsMod.MODID, surface());
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        this.addModifiedVanillaOverworldBiomes(mapper, builder -> {
            for (ResourceKey<Biome> vanilla : replaced) {
                builder.replaceBiome(vanilla, biome);
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
        return SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(peaks, desert, lands, sea, north));
    }
}
