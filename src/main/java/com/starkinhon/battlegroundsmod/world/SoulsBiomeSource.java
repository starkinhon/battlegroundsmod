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
// one. It is not a MultiNoiseBiomeSource on purpose: TerraBlender blends its regions only into those, and the Dragon Ball
// and Naruto regions leave vanilla land between their biomes. Underground the cave biomes stay vanilla (lush caves,
// dripstone, the deep dark with its ancient cities): nobody sees them from the sky, and they keep the caves worth
// exploring.
//
// Vanilla's climate layout is walked once and each of its points is cut at this world's own temperature and humidity
// edges, each piece getting the universe biome of its cell. The edges are not vanilla's bands: vanilla's hottest band is
// only about 3% of the land (its deserts and badlands), so the first test (2026-11-12, seed 1) had no Terras do Dragão
// within 6400 blocks and the Deserto do Diabo 3900 blocks away. These edges split the temperature noise's real spread
// (simulated with vanilla's octaves) so every biome gets about the same share of the land, and temperature still decides
// the order, so the frozen north never touches the hot deserts:
//   temperature < -0.5 (about 9%):   Norte Gelado
//   -0.5 to -0.25 (about 20%):        Monte Myoboku (humidity < 0) | Vila da Chuva
//   -0.25 to 0 (about 25%):           Montanhas Paozu (humidity < -0.1) | Vale do Fim | Terra Sagrada de Karin (> 0.1)
//   0 to 0.25 (about 25%):            Deserto do Vento (< -0.1) | Floresta da Folha | Floresta da Morte (> 0.1)
//   0.25 and up (about 20%):          Deserto do Diabo (humidity < 0) | Terras do Dragão
// The high peaks: the Norte Gelado where it is cold, the Picos do Dragão elsewhere. The seas and beaches: the Mar da Névoa
// where it is cold or mild, the Mar do Sul where it is warm (the far mushroom islands too: it has the tropical islands).
// A new universe biome goes into this table.
public final class SoulsBiomeSource extends BiomeSource {
    public static final MapCodec<SoulsBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryOps.retrieveGetter(Registries.BIOME)).apply(instance, instance.stable(SoulsBiomeSource::new)));

    private static final Set<ResourceKey<Biome>> SEAS = Set.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.BEACH, Biomes.SNOWY_BEACH);
    private static final Set<ResourceKey<Biome>> CAVES = Set.of(Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK);
    private static final Set<ResourceKey<Biome>> PEAKS = Set.of(Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.FROZEN_PEAKS,
            Biomes.SNOWY_SLOPES);

    private final Climate.ParameterList<Holder<Biome>> parameters;

    private static final float[] TEMPERATURE = {-0.5f, -0.25f, 0.0f, 0.25f};
    private static final float[] HUMIDITY = {-0.1f, 0.0f, 0.1f};

    private SoulsBiomeSource(HolderGetter<Biome> biomes) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> points = new ArrayList<>();
        new OverworldBiomeBuilder().addBiomes(pair -> {
            Climate.ParameterPoint point = pair.getFirst();
            if (CAVES.contains(pair.getSecond())) {
                points.add(Pair.of(point, biomes.getOrThrow(pair.getSecond())));
                return;
            }
            for (Climate.Parameter temperature : cut(point.temperature(), TEMPERATURE)) {
                for (Climate.Parameter humidity : cut(point.humidity(), HUMIDITY)) {
                    Climate.ParameterPoint piece = new Climate.ParameterPoint(temperature, humidity, point.continentalness(),
                            point.erosion(), point.depth(), point.weirdness(), point.offset());
                    points.add(Pair.of(piece, biomes.getOrThrow(choose(mid(temperature), mid(humidity), pair.getSecond()))));
                }
            }
        });
        this.parameters = new Climate.ParameterList<>(points);
    }

    // A climate range cut at the edges inside it.
    private static List<Climate.Parameter> cut(Climate.Parameter range, float[] edges) {
        List<Climate.Parameter> pieces = new ArrayList<>();
        long min = range.min();
        for (float edge : edges) {
            long at = Climate.quantizeCoord(edge);
            if (at > min && at < range.max()) {
                pieces.add(new Climate.Parameter(min, at));
                min = at;
            }
        }
        pieces.add(new Climate.Parameter(min, range.max()));
        return pieces;
    }

    private static float mid(Climate.Parameter range) {
        return Climate.unquantizeCoord((range.min() + range.max()) / 2);
    }

    static ResourceKey<Biome> choose(float temperature, float humidity, ResourceKey<Biome> vanilla) {
        if (vanilla == Biomes.MUSHROOM_FIELDS) {
            return ModWorldgen.MAR_DO_SUL;
        }
        if (SEAS.contains(vanilla)) {
            return temperature < 0.0f ? ModWorldgen.MAR_DA_NEVOA : ModWorldgen.MAR_DO_SUL;
        }
        if (PEAKS.contains(vanilla)) {
            return temperature < -0.25f ? ModWorldgen.NORTE_GELADO : ModWorldgen.PICOS_DO_DRAGAO;
        }
        // rivers, stony shores, swamps, hills and everything else of the land
        if (temperature < -0.5f) {
            return ModWorldgen.NORTE_GELADO;
        }
        if (temperature < -0.25f) {
            return humidity < 0.0f ? ModWorldgen.MONTE_MYOBOKU : ModWorldgen.VILA_DA_CHUVA;
        }
        if (temperature < 0.0f) {
            return humidity < -0.1f ? ModWorldgen.MONTANHAS_PAOZU : humidity < 0.1f ? ModWorldgen.VALE_DO_FIM : ModWorldgen.TERRA_SAGRADA;
        }
        if (temperature < 0.25f) {
            return humidity < -0.1f ? ModWorldgen.DESERTO_DO_VENTO : humidity < 0.1f ? ModWorldgen.FLORESTA_DA_FOLHA
                    : ModWorldgen.FLORESTA_DA_MORTE;
        }
        return humidity < 0.0f ? ModWorldgen.DESERTO_VERMELHO : ModWorldgen.TERRAS_DO_DRAGAO;
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
