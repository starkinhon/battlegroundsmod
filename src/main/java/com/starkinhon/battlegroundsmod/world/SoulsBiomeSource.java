package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.levelgen.DensityFunction;

// The biomes of the "Souls" world type (Stark 2026-11-12: "um tipo de mundo Souls, só o universo tematizado"): the land
// keeps vanilla's shape (the overworld noise: mountains, caves, aquifers, ore veins) but every surface biome is a universe
// one. It is not a MultiNoiseBiomeSource on purpose: TerraBlender blends its regions only into those, and the Dragon Ball
// and Naruto regions leave vanilla land between their biomes. Underground the cave biomes stay vanilla (lush caves,
// dripstone, the deep dark with its ancient cities): nobody sees them from the sky, and they keep the caves worth
// exploring.
//
// The land is cut in PATCHES about 1300 blocks across (Stark 2026-11-12: "pode ter 1.000, 1.500 blocos pra andar... eu
// queria ver os outros também"). The first version gave each biome a band of temperature and humidity, and the
// temperature noise is so slow that a band ran 4000 blocks: his first save was 78% Terras do Dragão + Deserto do Diabo
// (the hot band held 27% of the land for only two biomes), while the biomes in the middle of the humidity range (Folha,
// Vale do Fim) came out as thin strips about 400 blocks wide. Measured with a copy of vanilla's climate noise that
// matched the save's real chunks on 99.4% of the points.
//
// How a patch is made: the land is split in cells of CELL blocks, each with one point somewhere inside it (from a hash);
// a place belongs to the nearest point, looked for from a position bent by the weirdness noise so the borders wind
// instead of running straight. The patch's biome: the temperature at its cell (read at TEMPERATURE_SCALE, so the climate
// changes every few patches) picks the band, and a hash picks one of the band's biomes, moved to the next one when a
// neighbour patch of the same band already has it. Temperature still orders the bands, so the frozen north never touches
// the hot deserts.
//
// A new universe biome goes into one of the BANDS. Every patch keeps its size, the bands' shares of the land follow the
// number of biomes in each (from the land's temperature spread, LAND_TEMPERATURE), so every biome gets the same share and
// each one just gets a little rarer.
//
// The high peaks: the Norte Gelado in the cold bands, the Picos do Dragão elsewhere. The seas and beaches: the Mar da
// Névoa in the cold and mild bands, the Mar do Sul in the warm ones (the far mushroom islands too: it has the tropical
// islands), or the Mar de Dagon where the warm sea is humid (humidity >= 0.1).
//
// The Jujutsu biomes (2026-11-13) went into the bands of their climate: Outono de Kyoto in the cold one, Templos Antigos,
// Floresta Negra and Aldeia Amaldiçoada in the cool one, Colônia do Jogo do Abate and Shibuya in the mild one, Terras do
// Jogo and Floresta de Hanami in the hot one.
public final class SoulsBiomeSource extends BiomeSource {
    public static final MapCodec<SoulsBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryOps.retrieveGetter(Registries.BIOME)).apply(instance, instance.stable(SoulsBiomeSource::new)));

    private static final Set<ResourceKey<Biome>> SEAS = Set.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.BEACH, Biomes.SNOWY_BEACH);
    private static final Set<ResourceKey<Biome>> CAVES = Set.of(Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK);
    private static final Set<ResourceKey<Biome>> PEAKS = Set.of(Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.FROZEN_PEAKS,
            Biomes.SNOWY_SLOPES);

    // The land biomes from the coldest band to the hottest. A new universe biome goes into the band of its climate.
    static final List<List<ResourceKey<Biome>>> BANDS = List.of(
            List.of(ModWorldgen.NORTE_GELADO),
            List.of(ModWorldgen.MONTE_MYOBOKU, ModWorldgen.OUTONO_DE_KYOTO, ModWorldgen.VILA_DA_CHUVA),
            List.of(ModWorldgen.MONTANHAS_PAOZU, ModWorldgen.VALE_DO_FIM, ModWorldgen.TEMPLOS_ANTIGOS, ModWorldgen.FLORESTA_NEGRA,
                    ModWorldgen.ALDEIA_AMALDICOADA, ModWorldgen.TERRA_SAGRADA),
            List.of(ModWorldgen.DESERTO_DO_VENTO, ModWorldgen.COLONIA_DO_JOGO_DO_ABATE, ModWorldgen.SHIBUYA,
                    ModWorldgen.FLORESTA_DA_FOLHA, ModWorldgen.FLORESTA_DA_MORTE),
            List.of(ModWorldgen.DESERTO_VERMELHO, ModWorldgen.TERRAS_DO_JOGO, ModWorldgen.TERRAS_DO_DRAGAO,
                    ModWorldgen.FLORESTA_DE_HANAMI));
    // Bands up to this one are cold: their peaks are the Norte Gelado. Up to MILD_BANDS the seas are the Mar da Névoa.
    private static final int COLD_BANDS = 2;
    private static final int MILD_BANDS = 3;

    // The land's temperature (vanilla's noise) at every 5% of it, from 0% to 100%: the mean of six seeds, sampled over
    // 40000 x 40000 blocks. The bands' edges come from it, so each band gets land in proportion to its biomes.
    private static final float[] LAND_TEMPERATURE = {-1.054f, -0.597f, -0.460f, -0.365f, -0.291f, -0.228f, -0.173f, -0.120f,
            -0.070f, -0.022f, 0.026f, 0.072f, 0.118f, 0.166f, 0.216f, 0.270f, 0.331f, 0.401f, 0.489f, 0.614f, 1.170f};
    private static final float[] EDGES = edges();

    private static final int CELL = 1300;
    private static final double TEMPERATURE_SCALE = 1.5;
    // How far the weirdness noise bends a border, in cells (the noise mostly stays within +-0.3), and the size of its
    // bends. Measured in the copy of the noise: every biome 930-1150 blocks of walking, the hot band 1360.
    private static final double WARP = 2.0;
    private static final double WARP_FREQUENCY = 1.7;

    private final Climate.ParameterList<Holder<Biome>> vanilla;
    private final Map<ResourceKey<Biome>, Holder<Biome>> holders = new HashMap<>();
    // The band and biome of every patch looked at, per world (the key holds the world's salt): a patch is asked for
    // hundreds of times while its chunks generate.
    private final Map<Long, Integer> patches = new ConcurrentHashMap<>();
    // The last world's sampler and salt (compared by identity: the sampler's own equals walks its whole noise tree).
    private volatile Object[] lastSalt = {null, 0L};

    private SoulsBiomeSource(HolderGetter<Biome> biomes) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> points = new ArrayList<>();
        new OverworldBiomeBuilder().addBiomes(pair -> points.add(Pair.of(pair.getFirst(), holder(biomes, pair.getSecond()))));
        this.vanilla = new Climate.ParameterList<>(points);
        for (List<ResourceKey<Biome>> band : BANDS) {
            band.forEach(key -> holder(biomes, key));
        }
        holder(biomes, ModWorldgen.PICOS_DO_DRAGAO);
        holder(biomes, ModWorldgen.MAR_DA_NEVOA);
        holder(biomes, ModWorldgen.MAR_DO_SUL);
        holder(biomes, ModWorldgen.MAR_DE_DAGON);
    }

    private Holder<Biome> holder(HolderGetter<Biome> biomes, ResourceKey<Biome> key) {
        return holders.computeIfAbsent(key, biomes::getOrThrow);
    }

    // The temperatures where one band ends and the next begins.
    private static float[] edges() {
        int total = BANDS.stream().mapToInt(List::size).sum();
        float[] edges = new float[BANDS.size() - 1];
        int below = 0;
        for (int b = 0; b < edges.length; b++) {
            below += BANDS.get(b).size();
            double at = (double) below / total * (LAND_TEMPERATURE.length - 1);
            int i = (int) Math.floor(at);
            double f = at - i;
            edges[b] = (float) (LAND_TEMPERATURE[i] + (LAND_TEMPERATURE[Math.min(i + 1, LAND_TEMPERATURE.length - 1)] - LAND_TEMPERATURE[i]) * f);
        }
        return edges;
    }

    private static int band(double temperature) {
        int b = 0;
        while (b < EDGES.length && temperature >= EDGES[b]) {
            b++;
        }
        return b;
    }

    private static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    private static long hash(long salt, long i, long j, int what) {
        return mix(i * 341873128712L + j * 132897987541L + what * 0x5DEECE66DL + salt);
    }

    private static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static double at(DensityFunction function, double x, double z) {
        return function.compute(new DensityFunction.SinglePointContext((int) Math.floor(x), 0, (int) Math.floor(z)));
    }

    // A number of the world's own (from its noise: the biome source does not know the seed), so the patches differ per world.
    private long salt(Climate.Sampler sampler) {
        Object[] last = lastSalt;
        if (last[0] == sampler) {
            return (Long) last[1];
        }
        long salt = mix(Double.doubleToLongBits(at(sampler.temperature(), 1234567, -7654321))
                ^ Double.doubleToLongBits(at(sampler.humidity(), -3456789, 2345678)));
        lastSalt = new Object[] {sampler, salt};
        return salt;
    }

    // The patch's band (high bits) and the biome's index in it (low bits).
    private int patch(Climate.Sampler sampler, long salt, long i, long j) {
        return patches.computeIfAbsent(hash(salt, i, j, 7), key -> {
            long[] first = first(sampler, salt, i, j);
            int band = (int) first[0];
            int size = BANDS.get(band).size();
            int pick = (int) first[1];
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long[] near = first(sampler, salt, i + d[0], j + d[1]);
                if (near[0] == band && near[1] == pick && Long.compareUnsigned(near[2], first[2]) > 0) {
                    pick = (pick + 1) % size;
                }
            }
            return band << 8 | pick;
        });
    }

    // A patch's band, its first pick in the band and its hash (the larger hash keeps its pick when neighbours clash).
    private static long[] first(Climate.Sampler sampler, long salt, long i, long j) {
        double scale = CELL * TEMPERATURE_SCALE;
        int band = band(at(sampler.temperature(), (i + 0.5) * scale, (j + 0.5) * scale));
        long h = hash(salt, i, j, 3);
        return new long[] {band, Long.remainderUnsigned(h, BANDS.get(band).size()), h};
    }

    // The patch a block column belongs to: the nearest cell point to the column, the column bent by the weirdness noise.
    private int patchAt(Climate.Sampler sampler, int x, int z) {
        long salt = salt(sampler);
        long[] last = LAST_COLUMN.get();
        if (last[0] == x && last[1] == z && last[2] == salt && last[4] == 1) {
            return (int) last[3];
        }
        int patch = findPatch(sampler, salt, x, z);
        last[0] = x;
        last[1] = z;
        last[2] = salt;
        last[3] = patch;
        last[4] = 1;
        return patch;
    }

    // The last column each thread asked about (x, z, salt, patch, set): a chunk asks for every quart up a column in a row.
    private static final ThreadLocal<long[]> LAST_COLUMN = ThreadLocal.withInitial(() -> new long[5]);

    private int findPatch(Climate.Sampler sampler, long salt, int x, int z) {
        DensityFunction bend = sampler.weirdness();
        double f = 4.0 * WARP_FREQUENCY / CELL;
        double wx = at(bend, x * f, z * f) + 0.5 * at(bend, x * f * 3 + 80, z * f * 3) + 0.25 * at(bend, x * f * 9 + 500, z * f * 9);
        double wz = at(bend, z * f + 200, x * f) + 0.5 * at(bend, z * f * 3 + 360, x * f * 3) + 0.25 * at(bend, z * f * 9 + 700, x * f * 9);
        double cx = (x + wx * WARP * CELL) / CELL;
        double cz = (z + wz * WARP * CELL) / CELL;
        long ci = (long) Math.floor(cx), cj = (long) Math.floor(cz);
        double best = Double.MAX_VALUE;
        long bi = ci, bj = cj;
        for (long i = ci - 1; i <= ci + 1; i++) {
            for (long j = cj - 1; j <= cj + 1; j++) {
                double px = i + 0.15 + 0.7 * unit(hash(salt, i, j, 1));
                double pz = j + 0.15 + 0.7 * unit(hash(salt, i, j, 2));
                double d = (cx - px) * (cx - px) + (cz - pz) * (cz - pz);
                if (d < best) {
                    best = d;
                    bi = i;
                    bj = j;
                }
            }
        }
        return patch(sampler, salt, bi, bj);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(vanilla.values().stream().map(Pair::getSecond).filter(h -> h.unwrapKey().map(CAVES::contains).orElse(false)),
                holders.entrySet().stream().filter(e -> !e.getKey().location().getNamespace().equals("minecraft")).map(Map.Entry::getValue))
                .distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Climate.TargetPoint target = sampler.sample(x, y, z);
        Holder<Biome> found = vanilla.findValue(target);
        ResourceKey<Biome> key = found.unwrapKey().orElseThrow();
        if (CAVES.contains(key)) {
            return found;
        }
        int patch = patchAt(sampler, QuartPos.toBlock(x), QuartPos.toBlock(z));
        int band = patch >> 8;
        if (key == Biomes.MUSHROOM_FIELDS) {
            return holders.get(ModWorldgen.MAR_DO_SUL);
        }
        if (SEAS.contains(key)) {
            if (band < MILD_BANDS) {
                return holders.get(ModWorldgen.MAR_DA_NEVOA);
            }
            // warm seas: the Mar de Dagon where it is humid (the Jujutsu biomes, 2026-11-13), the Mar do Sul otherwise
            return holders.get(Climate.unquantizeCoord(target.humidity()) >= 0.1f ? ModWorldgen.MAR_DE_DAGON : ModWorldgen.MAR_DO_SUL);
        }
        if (PEAKS.contains(key)) {
            return holders.get(band < COLD_BANDS ? ModWorldgen.NORTE_GELADO : ModWorldgen.PICOS_DO_DRAGAO);
        }
        // rivers, stony shores, swamps, hills and everything else of the land
        return holders.get(BANDS.get(band).get(patch & 0xFF));
    }
}
