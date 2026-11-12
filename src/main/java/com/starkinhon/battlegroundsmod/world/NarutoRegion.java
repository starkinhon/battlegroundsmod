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
// and jungles there), the big warm woods of Konoha. Its mountains, rivers and caves stay vanilla, and the land keeps
// vanilla's shape. The Floresta da Morte (Stark 2026-11-11) borders it on the wettest side, as in the anime it lies
// inside Konoha's woods. The Deserto do Vento (Stark 2026-11-11) takes the same band's dry side, and the Mar da Névoa
// (Stark 2026-11-11) the cold and middle seas. The Vila da Chuva (Stark 2026-11-11) takes the cool band's wet side. The
// Vale do Fim (Stark 2026-11-12) the temperate band but its ends, between the Folha and the Vila da Chuva as the
// valley lies on the Land of Fire's border. The Monte Myoboku (Stark 2026-11-12) takes the cool band's dry side, its
// plains, apart from the other lands as the toads' mountain is hidden from the ninja world. With it all seven Naruto
// biomes are in.
public final class NarutoRegion extends Region {
    // Against vanilla's 10 and the Dragon Ball's 8.
    private static final int WEIGHT = 6;

    private static final Set<ResourceKey<Biome>> SEAS = Set.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.BEACH, Biomes.SNOWY_BEACH, Biomes.STONY_SHORE);
    private static final Set<ResourceKey<Biome>> NOT_LAND = Set.of(Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.MUSHROOM_FIELDS,
            Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK, Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
    // Only the bare peaks stay vanilla: the slopes, meadows, groves and windswept hills of a Naruto band become the Naruto
    // biome too (they are cut out per band like the lowlands), so each biome is one big piece instead of patches between
    // them (the PC test of 2026-11-12: the Vale do Fim's largest piece was 32 blocks wide, too small for its valley).
    private static final Set<ResourceKey<Biome>> MOUNTAINS = Set.of(Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.FROZEN_PEAKS,
            Biomes.SNOWY_SLOPES);

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
        int t = band(point.temperature(), -0.45f, -0.15f, 0.2f, 0.55f);
        // the Mar da Névoa on the cold and the middle seas (the Dragon Ball's Mar do Sul takes the lukewarm ones), with
        // its beaches; the stony shores and the frozen and warm seas stay vanilla
        if (SEAS.contains(vanilla)) {
            return (t == 1 || t == 2) && vanilla != Biomes.STONY_SHORE ? ModWorldgen.MAR_DA_NEVOA : vanilla;
        }
        if (NOT_LAND.contains(vanilla) || MOUNTAINS.contains(vanilla)) {
            return vanilla;
        }
        int h = band(point.humidity(), -0.35f, -0.1f, 0.1f, 0.3f);
        // the Floresta da Morte next to the Folha, on its wettest side (vanilla's jungles there)
        if (t == 3 && h == 4) {
            return ModWorldgen.FLORESTA_DA_MORTE;
        }
        if (t == 3 && h >= 2) {
            return ModWorldgen.FLORESTA_DA_FOLHA;
        }
        // the Vila da Chuva on the cool band's wet side (vanilla's taigas there), next to the Mar da Névoa's cold seas
        if (t == 1 && h >= 2) {
            return ModWorldgen.VILA_DA_CHUVA;
        }
        // the Monte Myoboku on the cool band's dry side (vanilla's plains there), free of the Dragon Ball
        if (t == 1 && h <= 1) {
            return ModWorldgen.MONTE_MYOBOKU;
        }
        // the Vale do Fim on the temperate band (vanilla's forests and plains there) but its driest and wettest ends, so
        // it is wide enough for the valley's 316 by 176 blocks
        if (t == 2 && h >= 1 && h <= 3) {
            return ModWorldgen.VALE_DO_FIM;
        }
        // the Deserto do Vento on the warm band's dry side (vanilla's savannas there), next to the Folha like the Land of
        // Wind next to the Land of Fire
        if (t == 3 && h <= 1) {
            return ModWorldgen.DESERTO_DO_VENTO;
        }
        return vanilla;
    }
}
