package com.starkinhon.battlegroundsmod.world;

import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

// The biome of each column comes from the drawn map too (forest = forest trees, snow = snowy taiga...), so grass color,
// trees, snow and the sea follow the picture. "biomes" maps each kind of ground (WorldMap.NAMES) to a biome.
public class MapBiomeSource extends BiomeSource {
    public static final MapCodec<MapBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("map").forGetter(source -> source.mapName),
            Codec.unboundedMap(Codec.STRING, Biome.CODEC).fieldOf("biomes").forGetter(source -> source.biomes))
            .apply(instance, MapBiomeSource::new));

    private final String mapName;
    private final Map<String, Holder<Biome>> biomes;
    private final Holder<Biome>[] byKind;

    @SuppressWarnings("unchecked")
    public MapBiomeSource(String mapName, Map<String, Holder<Biome>> biomes) {
        this.mapName = mapName;
        this.biomes = biomes;
        this.byKind = new Holder[WorldMap.NAMES.length];
        Holder<Biome> fallback = biomes.containsKey("deep") ? biomes.get("deep") : biomes.values().iterator().next();
        for (int i = 0; i < byKind.length; i++) {
            byKind[i] = biomes.getOrDefault(WorldMap.NAMES[i], fallback);
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.values().stream().distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int kind = WorldMap.get(mapName).kind(x << 2, z << 2);
        return byKind[Math.max(0, Math.min(byKind.length - 1, kind))];
    }
}
