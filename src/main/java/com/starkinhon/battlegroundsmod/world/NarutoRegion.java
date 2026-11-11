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

// The Naruto universe as a part of the normal overworld (Stark 2026-11-11), its own TerraBlender region apart from the
// Dragon Ball one. Stark: "bioma de db -> bioma normal do mine -> bioma de naruto", so the Naruto biomes take climate
// bands that DragonRegion leaves vanilla: where the two regions meet at the same climate, the Dragon Ball side there is
// normal land. For now the Floresta da Folha: the warm band's lowlands on its middle and wet side (vanilla's forests
// and jungles there), the big warm woods of Konoha. Its mountains, seas, rivers and caves stay vanilla, and the land
// keeps vanilla's shape. The Floresta da Morte (Stark 2026-11-11) borders it on the wettest side, as in the anime it
// lies inside Konoha's woods. The other Naruto biomes come into the bands still vanilla here, one by one.
public final class NarutoRegion extends Region {
    // Against vanilla's 10 and the Dragon Ball's 8.
    private static final int WEIGHT = 6;

    private static final Set<ResourceKey<Biome>> SEAS = Set.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.BEACH, Biomes.SNOWY_BEACH, Biomes.STONY_SHORE);
    private static final Set<ResourceKey<Biome>> NOT_LAND = Set.of(Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.MUSHROOM_FIELDS,
            Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK, Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
    private static final Set<ResourceKey<Biome>> MOUNTAINS = Set.of(Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.FROZEN_PEAKS,
            Biomes.SNOWY_SLOPES, Biomes.GROVE, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST,
            Biomes.WINDSWEPT_SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WOODED_BADLANDS, Biomes.MEADOW, Biomes.CHERRY_GROVE);

    private NarutoRegion() {
        super(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "naruto"), RegionType.OVERWORLD, WEIGHT);
    }

    public static void register() {
        Regions.register(new NarutoRegion());
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
        if (NOT_LAND.contains(vanilla) || SEAS.contains(vanilla) || MOUNTAINS.contains(vanilla)) {
            return vanilla;
        }
        int t = band(point.temperature(), -0.45f, -0.15f, 0.2f, 0.55f);
        int h = band(point.humidity(), -0.35f, -0.1f, 0.1f, 0.3f);
        // the Floresta da Morte next to the Folha, on its wettest side (vanilla's jungles there)
        if (t == 3 && h == 4) {
            return ModWorldgen.FLORESTA_DA_MORTE;
        }
        if (t == 3 && h >= 2) {
            return ModWorldgen.FLORESTA_DA_FOLHA;
        }
        return vanilla;
    }
}
