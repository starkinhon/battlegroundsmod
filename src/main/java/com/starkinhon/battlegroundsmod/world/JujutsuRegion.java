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
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;

// The Jujutsu universe as a part of the normal overworld (Stark 2026-11-12: "Jujutsu é basicamente o Japão"), its own
// TerraBlender region apart from the Dragon Ball and Naruto ones, so it can take any climate band. For now the Templos
// Antigos: the temperate band but its driest end (vanilla's forests, birch woods, meadows and their hills and slopes
// there), the old Japan of cedar mountains with temples on the hilltops. Like the Naruto biomes it takes the band's
// slopes, meadows, groves and windswept hills too (the temples want hills), and only the bare peaks, rivers, swamps and
// caves stay vanilla. The other Jujutsu biomes take the vanilla biomes they are the Japanese version of (see choose).
public final class JujutsuRegion extends Region {
    // Against vanilla's 10, the Dragon Ball's 8 and the Naruto's 6.
    private static final int WEIGHT = 5;

    private static final Set<ResourceKey<Biome>> NOT_LAND = Set.of(Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.MUSHROOM_FIELDS,
            Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK, Biomes.SWAMP, Biomes.MANGROVE_SWAMP,
            Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN,
            Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.BEACH,
            Biomes.SNOWY_BEACH, Biomes.STONY_SHORE);
    private static final Set<ResourceKey<Biome>> MOUNTAINS = Set.of(Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.FROZEN_PEAKS,
            Biomes.SNOWY_SLOPES);

    private JujutsuRegion() {
        super(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "jujutsu"), RegionType.OVERWORLD, WEIGHT);
    }

    public static void register() {
        Regions.register(new JujutsuRegion());
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

    // Each Jujutsu biome takes the vanilla biome it is the Japanese version of (2026-11-13, all ported): the swamps
    // become the Aldeia Amaldiçoada, the warm seas and the hot beaches the Mar de Dagon, the dark forests the Floresta
    // Negra, the jungles the Floresta de Hanami, the deserts, badlands and high savannas the Terras do Jogo, the low
    // savannas the Colônia do Jogo do Abate, the plains Shibuya, the taigas the Outono de Kyoto, and the rest of the
    // temperate band (forests, birch woods, meadows) the Templos Antigos.
    private static ResourceKey<Biome> choose(Climate.ParameterPoint point, ResourceKey<Biome> vanilla) {
        int t = band(point.temperature(), -0.45f, -0.15f, 0.2f, 0.55f);
        int h = band(point.humidity(), -0.35f, -0.1f, 0.1f, 0.3f);
        if (vanilla == Biomes.SWAMP || vanilla == Biomes.MANGROVE_SWAMP) {
            return ModWorldgen.ALDEIA_AMALDICOADA;
        }
        if (vanilla == Biomes.WARM_OCEAN || vanilla == Biomes.LUKEWARM_OCEAN || vanilla == Biomes.DEEP_LUKEWARM_OCEAN
                || vanilla == Biomes.BEACH && t >= 3) {
            return ModWorldgen.MAR_DE_DAGON;
        }
        if (NOT_LAND.contains(vanilla) || MOUNTAINS.contains(vanilla)) {
            return vanilla;
        }
        if (vanilla == Biomes.DARK_FOREST) {
            return ModWorldgen.FLORESTA_NEGRA;
        }
        if (vanilla == Biomes.JUNGLE || vanilla == Biomes.SPARSE_JUNGLE || vanilla == Biomes.BAMBOO_JUNGLE) {
            return ModWorldgen.FLORESTA_DE_HANAMI;
        }
        if (vanilla == Biomes.DESERT || vanilla == Biomes.BADLANDS || vanilla == Biomes.ERODED_BADLANDS
                || vanilla == Biomes.WOODED_BADLANDS || vanilla == Biomes.SAVANNA_PLATEAU || vanilla == Biomes.WINDSWEPT_SAVANNA) {
            return ModWorldgen.TERRAS_DO_JOGO;
        }
        if (vanilla == Biomes.SAVANNA) {
            return ModWorldgen.COLONIA_DO_JOGO_DO_ABATE;
        }
        if (vanilla == Biomes.PLAINS || vanilla == Biomes.SUNFLOWER_PLAINS) {
            return ModWorldgen.SHIBUYA;
        }
        if (vanilla == Biomes.TAIGA || vanilla == Biomes.OLD_GROWTH_PINE_TAIGA || vanilla == Biomes.OLD_GROWTH_SPRUCE_TAIGA) {
            return ModWorldgen.OUTONO_DE_KYOTO;
        }
        if (t == 2 && h >= 1) {
            return ModWorldgen.TEMPLOS_ANTIGOS;
        }
        return vanilla;
    }
}
