package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

// The biomes of the "Souls" world type (Stark 2026-11-12: "um tipo de mundo Souls, só o universo tematizado"): the land
// keeps vanilla's shape (the overworld noise: mountains, caves, aquifers, ore veins) but every surface biome is a universe
// one. Vanilla's climate layout is walked once and each of its points gets a universe biome by its temperature and
// humidity band, so the universes sit where their climate fits (the frozen north, the hot deserts) and always border
// each other the vanilla way (frozen never next to hot). It is not a MultiNoiseBiomeSource on purpose: TerraBlender
// blends its regions only into those, and the Dragon Ball and Naruto regions leave vanilla land between their biomes.
// Underground the cave biomes stay vanilla (lush caves, dripstone, the deep dark with its ancient cities): nobody sees
// them from the sky, and they keep the caves worth exploring.
//
// The land (temperature band from frozen to hot, humidity band from dry to wet):
//   frozen:    Norte Gelado everywhere
//   cool:      Monte Myoboku | Monte Myoboku | Vila da Chuva | Vila da Chuva | Vila da Chuva
//   temperate: Montanhas Paozu | Vale do Fim | Vale do Fim | Vale do Fim | Terra Sagrada de Karin
//   warm:      Deserto do Vento | Floresta da Folha | Floresta da Folha | Floresta da Folha | Floresta da Morte
//   hot:       Deserto do Diabo | Deserto do Diabo | Deserto do Diabo | Terras do Dragão | Terras do Dragão
// The high peaks: the Norte Gelado where it is cold, the Picos do Dragão elsewhere. The seas and beaches: the Mar da Névoa
// where it is cold or mild, the Mar do Sul where it is warm (the far mushroom islands too: it has the tropical islands).
// A new universe biome goes into this table.
public final class SoulsBiomeSource extends BiomeSource {
    public static final MapCodec<SoulsBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryOps.retrieveGetter(Registries.BIOME)).apply(instance, instance.stable(SoulsBiomeSource::new)));

    private static final ResourceKey<Biome>[][] LAND = land();

    private static final Set<ResourceKey<Biome>> SEAS = Set.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.BEACH, Biomes.SNOWY_BEACH);
    private static final Set<ResourceKey<Biome>> CAVES = Set.of(Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK);
    private static final Set<ResourceKey<Biome>> PEAKS = Set.of(Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.FROZEN_PEAKS,
            Biomes.SNOWY_SLOPES);

    private final Climate.ParameterList<Holder<Biome>> parameters;

    private SoulsBiomeSource(HolderGetter<Biome> biomes) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> points = new ArrayList<>();
        new OverworldBiomeBuilder().addBiomes(pair -> points.add(Pair.of(pair.getFirst(),
                biomes.getOrThrow(choose(pair.getFirst(), pair.getSecond())))));
        this.parameters = new Climate.ParameterList<>(points);
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Biome>[][] land() {
        ResourceKey<Biome> norte = ModWorldgen.NORTE_GELADO, myoboku = ModWorldgen.MONTE_MYOBOKU, chuva = ModWorldgen.VILA_DA_CHUVA,
                paozu = ModWorldgen.MONTANHAS_PAOZU, vale = ModWorldgen.VALE_DO_FIM, karin = ModWorldgen.TERRA_SAGRADA,
                vento = ModWorldgen.DESERTO_DO_VENTO, folha = ModWorldgen.FLORESTA_DA_FOLHA, morte = ModWorldgen.FLORESTA_DA_MORTE,
                diabo = ModWorldgen.DESERTO_VERMELHO, terras = ModWorldgen.TERRAS_DO_DRAGAO;
        return new ResourceKey[][] {
                {norte, norte, norte, norte, norte},
                {myoboku, myoboku, chuva, chuva, chuva},
                {paozu, vale, vale, vale, karin},
                {vento, folha, folha, folha, morte},
                {diabo, diabo, diabo, terras, terras}};
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

    static ResourceKey<Biome> choose(Climate.ParameterPoint point, ResourceKey<Biome> vanilla) {
        if (CAVES.contains(vanilla)) {
            return vanilla;
        }
        int t = band(point.temperature(), -0.45f, -0.15f, 0.2f, 0.55f);
        int h = band(point.humidity(), -0.35f, -0.1f, 0.1f, 0.3f);
        if (vanilla == Biomes.MUSHROOM_FIELDS) {
            return ModWorldgen.MAR_DO_SUL;
        }
        if (SEAS.contains(vanilla)) {
            return t <= 2 ? ModWorldgen.MAR_DA_NEVOA : ModWorldgen.MAR_DO_SUL;
        }
        if (PEAKS.contains(vanilla)) {
            return t <= 1 ? ModWorldgen.NORTE_GELADO : ModWorldgen.PICOS_DO_DRAGAO;
        }
        // rivers, stony shores, swamps, hills and everything else of the land: the table
        return LAND[t][h];
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return parameters.values().stream().map(Pair::getSecond).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return parameters.findValue(sampler.sample(x, y, z));
    }
}
