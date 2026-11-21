// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
    // The rare ones (Stark 2026-11-19, "quero que ele apareça menos do que os outros"): the share of the Terras do
    // Jogo's patches it keeps, and the humidity a warm sea needs to be the Mar de Dagon.
    private static final double TERRAS_KEPT = 0.3;
    private static final float DAGON_HUMIDITY = 0.3f;
    private static final int MILD_BANDS = 3;
    // The band Shibuya is in.
    private static final int SHIBUYA_BAND = shibuyaBand();

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
    // The universe biomes by a small number (for the scrap cleanup's tables), and back.
    private final List<Holder<Biome>> indexed = new ArrayList<>();
    private final Map<Holder<Biome>, Integer> index = new java.util.IdentityHashMap<>();
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
        for (Holder<Biome> h : holders.values()) {
            if (!index.containsKey(h)) {
                index.put(h, indexed.size());
                indexed.add(h);
            }
        }
    }

    // The vanilla biome at a climate point (cave biomes too).
    private ResourceKey<Biome> vanillaKey(Climate.TargetPoint target) {
        return vanilla.findValue(target).unwrapKey().orElseThrow();
    }

    private Holder<Biome> holder(HolderGetter<Biome> biomes, ResourceKey<Biome> key) {
        return holders.computeIfAbsent(key, biomes::getOrThrow);
    }

    private static int shibuyaBand() {
        for (int b = 0; b < BANDS.size(); b++) {
            if (BANDS.get(b).contains(ModWorldgen.SHIBUYA)) {
                return b;
            }
        }
        throw new IllegalStateException("Shibuya is in no band");
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

    static long hash(long salt, long i, long j, int what) {
        return mix(i * 341873128712L + j * 132897987541L + what * 0x5DEECE66DL + salt);
    }

    static double unit(long hash) {
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
        long salt = saltOf(sampler);
        lastSalt = new Object[] {sampler, salt};
        return salt;
    }

    static long saltOf(Climate.Sampler sampler) {
        return mix(Double.doubleToLongBits(at(sampler.temperature(), 1234567, -7654321))
                ^ Double.doubleToLongBits(at(sampler.humidity(), -3456789, 2345678)));
    }

    // The patch's band (high bits) and the biome's index in it (low bits).
    private int patch(Climate.Sampler sampler, long salt, long i, long j) {
        return patches.computeIfAbsent(hash(salt, i, j, 7), key -> {
            long[] first = first(sampler, salt, i, j);
            int band = (int) first[0];
            int size = BANDS.get(band).size();
            int pick = (int) first[1];
            List<Integer> nearPicks = new ArrayList<>();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long[] near = first(sampler, salt, i + d[0], j + d[1]);
                if (near[0] == band && near[1] == pick && Long.compareUnsigned(near[2], first[2]) > 0) {
                    pick = (pick + 1) % size;
                }
                if (near[0] == band) {
                    nearPicks.add((int) near[1]);
                }
            }
            if (band == SHIBUYA_BAND) {
                int shibuya = BANDS.get(band).indexOf(ModWorldgen.SHIBUYA);
                if (pick == shibuya || unit(hash(salt, i, j, 13)) < CITY_EXTRA) {
                    boolean city = cityFits(sampler, salt, i, j, this::vanillaKey);
                    if (city) {
                        pick = shibuya;
                    } else if (pick == shibuya) {
                        // no room for a whole city: another biome of the band, one the neighbours don't have if any
                        int other = (pick + 1) % size;
                        for (int k = 1; k < size; k++) {
                            int c = (pick + k) % size;
                            if (!nearPicks.contains(c)) {
                                other = c;
                                break;
                            }
                        }
                        pick = other;
                    }
                }
            }
            return band << 8 | pick;
        });
    }

    // A patch's band, its first pick in the band and its hash (the larger hash keeps its pick when neighbours clash).
    static long[] first(Climate.Sampler sampler, long salt, long i, long j) {
        double scale = CELL * TEMPERATURE_SCALE;
        int band = band(at(sampler.temperature(), (i + 0.5) * scale, (j + 0.5) * scale));
        long h = hash(salt, i, j, 3);
        List<ResourceKey<Biome>> list = BANDS.get(band);
        int pick = (int) Long.remainderUnsigned(h, list.size());
        if (list.get(pick) == ModWorldgen.TERRAS_DO_JOGO && unit(hash(salt, i, j, 11)) >= TERRAS_KEPT) {
            // Rare (Stark 2026-11-19): most of the Terras do Jogo's patches go to another biome of the band; the other
            // patches keep their pick, so nothing else moves
            pick = (pick + 1 + (int) Long.remainderUnsigned(hash(salt, i, j, 12), list.size() - 1)) % list.size();
        }
        return new long[] {band, pick, h};
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
        long[] cell = nearestCell(sampler, salt, x, z);
        return patch(sampler, salt, cell[0], cell[1]);
    }

    // The cell whose patch holds a block column: the nearest cell point to the column, bent by the weirdness noise.
    static long[] nearestCell(Climate.Sampler sampler, long salt, int x, int z) {
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
        return new long[] {bi, bj};
    }

    // Shibuya only where a big city fits (Stark 2026-11-19: the city came out in scraps between the sea and the
    // beaches, "só gerar se for pra gerar de um jeito bom, bem feito, e com espaço pra ter um bioma grande"). The city
    // sits at one level just above the sea and cuts or fills the land to it, so it only looks right on wide, low land.
    // The patch is walked on a grid of CITY_STEP blocks (only the points whose nearest cell is this one): it is a city
    // when at most CITY_MAX_SEA of its points are sea or beach (no city cut in islands and bays) and its low land (no
    // peak, slope, shore or hill biome, and the land, the highest of the point and 4 points CITY_REACH around, under
    // CITY_MAX_HEIGHT; a river counts, the city fills it) is at least CITY_GOOD of the patch and CITY_MIN_POINTS points
    // (about 860 x 860 blocks). A patch picked as Shibuya that fails takes another biome of the band; so Shibuya
    // doesn't get too rare, CITY_EXTRA of the band's other patches become Shibuya when they pass (only the best land).
    // Measured with the game's own noise (6 seeds x 22000 x 22000 blocks, 2026-11-19): of the band's 456 patches, 81
    // were Shibuya (mostly cut by sea or hills) and 4 of them pass; with CITY_EXTRA there are 24 Shibuyas now, each one
    // a whole city. The other four biomes of the band give up about 5% of their patches. The answer is kept with the patch (patch() runs once per patch per world)
    // and nothing here asks for a patch, so no lock is taken twice.
    private static final int CITY_STEP = 96;
    private static final int SEA_LEVEL = 63;
    private static final double CITY_MAX_SEA = 0.1;
    private static final double CITY_GOOD = 0.7;
    private static final int CITY_MIN_POINTS = 80;
    private static final int CITY_MAX_HEIGHT = 86;
    private static final int CITY_REACH = 32;
    private static final double CITY_EXTRA = 0.6;
    private static final Set<ResourceKey<Biome>> NOT_CITY = Set.of(Biomes.STONY_SHORE, Biomes.WINDSWEPT_HILLS,
            Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_SAVANNA, Biomes.MEADOW, Biomes.GROVE,
            Biomes.CHERRY_GROVE, Biomes.SNOWY_SLOPES, Biomes.MUSHROOM_FIELDS, Biomes.SAVANNA_PLATEAU, Biomes.WOODED_BADLANDS,
            Biomes.ERODED_BADLANDS, Biomes.BADLANDS);

    static boolean cityFits(Climate.Sampler sampler, long salt, long i, long j, Function<Climate.TargetPoint, ResourceKey<Biome>> vanilla) {
        int[] land = cityLand(sampler, salt, i, j, vanilla);
        return land[1] <= CITY_MAX_SEA * land[2] && land[0] >= CITY_MIN_POINTS && land[0] >= CITY_GOOD * land[2];
    }

    // The patch's points of city land, of sea or beach, and all its points, on the CITY_STEP grid.
    static int[] cityLand(Climate.Sampler sampler, long salt, long i, long j, Function<Climate.TargetPoint, ResourceKey<Biome>> vanilla) {
        int x0 = (int) ((i - 2) * CELL), x1 = (int) ((i + 3) * CELL);
        int z0 = (int) ((j - 2) * CELL), z1 = (int) ((j + 3) * CELL);
        int qy = QuartPos.fromBlock(160);
        int good = 0, sea = 0, total = 0;
        for (int x = x0 + CITY_STEP / 2; x < x1; x += CITY_STEP) {
            for (int z = z0 + CITY_STEP / 2; z < z1; z += CITY_STEP) {
                long[] cell = nearestCell(sampler, salt, x, z);
                if (cell[0] != i || cell[1] != j) {
                    continue;
                }
                total++;
                ResourceKey<Biome> key = vanilla.apply(sampler.sample(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z)));
                if (SEAS.contains(key)) {
                    sea++;
                } else if (cityLandAt(sampler, key, x, z)) {
                    good++;
                }
            }
        }
        return new int[] {good, sea, total};
    }

    // Whether a land point (not sea) is low and flat enough for the city.
    static boolean cityLandAt(Climate.Sampler sampler, ResourceKey<Biome> key, int x, int z) {
        if (PEAKS.contains(key) || NOT_CITY.contains(key)) {
            return false;
        }
        double depth = depthAt(sampler, x, z);
        depth = Math.max(depth, depthAt(sampler, x + CITY_REACH, z));
        depth = Math.max(depth, depthAt(sampler, x - CITY_REACH, z));
        depth = Math.max(depth, depthAt(sampler, x, z + CITY_REACH));
        depth = Math.max(depth, depthAt(sampler, x, z - CITY_REACH));
        // vanilla's depth is about 0 at the land's surface and falls 1 every 128 blocks up
        return SEA_LEVEL + depth * 128 < CITY_MAX_HEIGHT;
    }

    private static double depthAt(Climate.Sampler sampler, int x, int z) {
        return sampler.depth().compute(new DensityFunction.SinglePointContext(x, SEA_LEVEL, z));
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
        int bx = QuartPos.toBlock(x), bz = QuartPos.toBlock(z);
        Holder<Biome> picked = pick(sampler, target, key, bx, bz);
        if (!isLand(key)) {
            return picked;
        }
        return wholeOrNothing(sampler, picked, bx, bz);
    }

    // The land key of a point: not a cave, sea, beach, peak or mushroom island (those keep their own biome everywhere).
    private static boolean isLand(ResourceKey<Biome> key) {
        return !CAVES.contains(key) && !SEAS.contains(key) && !PEAKS.contains(key) && key != Biomes.MUSHROOM_FIELDS;
    }

    // The biome of a surface point before the scraps are cleaned up: the sea, peak or patch biome.
    private Holder<Biome> pick(Climate.Sampler sampler, Climate.TargetPoint target, ResourceKey<Biome> key, int bx, int bz) {
        int patch = patchAt(sampler, bx, bz);
        int band = patch >> 8;
        if (key == Biomes.MUSHROOM_FIELDS) {
            return holders.get(ModWorldgen.MAR_DO_SUL);
        }
        if (SEAS.contains(key)) {
            if (band < MILD_BANDS) {
                return holders.get(ModWorldgen.MAR_DA_NEVOA);
            }
            // warm seas: the Mar de Dagon where it is humid (the Jujutsu biomes, 2026-11-13), the Mar do Sul otherwise
            // (rare, Stark 2026-11-19: only the most humid warm seas; it took humidity 0.1 and up)
            return holders.get(Climate.unquantizeCoord(target.humidity()) >= DAGON_HUMIDITY ? ModWorldgen.MAR_DE_DAGON : ModWorldgen.MAR_DO_SUL);
        }
        if (PEAKS.contains(key)) {
            return holders.get(band < COLD_BANDS ? ModWorldgen.NORTE_GELADO : ModWorldgen.PICOS_DO_DRAGAO);
        }
        // rivers, stony shores, swamps, hills and everything else of the land
        return holders.get(BANDS.get(band).get(patch & 0xFF));
    }

    // No scraps of a biome (Stark 2026-11-21: a piece of the Colônia do Jogo do Abate about 5 blocks wide, at the spawn
    // between the Mar do Sul and the Picos do Dragão, turned the whole place into the Colônia's ambience, "se for pra
    // aparecer, que apareça completo"). A patch gives its biome only to its land: its sea and beaches are the sea biome,
    // its peaks the Picos do Dragão / Norte Gelado. A patch that is almost all sea and peaks is left with a few scraps of
    // land (an islet, a river's bank, a ledge between two peaks), and each scrap was the patch's biome with nothing of it
    // around. So a land column keeps its biome only when the biome holds at least SCRAP_MIN points of a grid of
    // SCRAP_STEP blocks within SCRAP_RADIUS of it (about 55 x 55 blocks of it near); otherwise it takes the biome most of
    // those points have (the sea around an islet, the peaks around a ledge, the land biome next to it). The grid points
    // are read at one height above the land, the same for every column, so the answer is the same from every chunk and
    // never leaves a seam. A compact piece smaller than the circle is all inside every one of its columns' circles, so it
    // stays or goes whole.
    private static final int SCRAP_STEP = 16;
    private static final int SCRAP_RADIUS = 64;
    private static final int SCRAP_MIN = 12;
    private static final int SCRAP_Y = QuartPos.fromBlock(160);
    // the grid points within SCRAP_RADIUS of a column's grid point, nearest first: {dx, dz} in grid steps
    private static final int[][] SCRAP_RING = scrapRing();

    private static int[][] scrapRing() {
        int r = SCRAP_RADIUS / SCRAP_STEP;
        List<int[]> ring = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r) {
                    ring.add(new int[] {dx, dz});
                }
            }
        }
        ring.sort(java.util.Comparator.comparingInt(d -> d[0] * d[0] + d[1] * d[1]));
        return ring.toArray(new int[0][]);
    }

    private Holder<Biome> wholeOrNothing(Climate.Sampler sampler, Holder<Biome> picked, int bx, int bz) {
        long salt = salt(sampler);
        long[] cache = SCRAP_COLUMNS.get();
        int slot = ((bx >> 2) & 7 | ((bz >> 2) & 7) << 3) * 4;
        if (cache[slot] == bx && cache[slot + 1] == bz && cache[slot + 2] == salt && cache[slot + 3] != 0) {
            return indexed.get((int) cache[slot + 3] - 1);
        }
        Holder<Biome> result = scrapCheck(sampler, salt, picked, bx, bz);
        cache[slot] = bx;
        cache[slot + 1] = bz;
        cache[slot + 2] = salt;
        cache[slot + 3] = index.get(result) + 1;
        return result;
    }

    // The last columns each thread cleaned up (x, z, salt, biome index + 1), 64 slots by the column's low bits: a chunk
    // asks for every quart of a section, column after column, again for every section.
    private static final ThreadLocal<long[]> SCRAP_COLUMNS = ThreadLocal.withInitial(() -> new long[64 * 4]);

    private Holder<Biome> scrapCheck(Climate.Sampler sampler, long salt, Holder<Biome> picked, int bx, int bz) {
        int own = index.get(picked);
        // the column's own grid point: the nearest one
        int gx = Math.floorDiv(bx + SCRAP_STEP / 2, SCRAP_STEP), gz = Math.floorDiv(bz + SCRAP_STEP / 2, SCRAP_STEP);
        int count = 1;
        int[] votes = null;
        for (int k = 0; k < SCRAP_RING.length; k++) {
            int b = grid(sampler, salt, gx + SCRAP_RING[k][0], gz + SCRAP_RING[k][1]);
            if (b == own) {
                count++;
                if (count >= SCRAP_MIN) {
                    return picked;
                }
            } else {
                if (votes == null) {
                    votes = new int[indexed.size()];
                }
                votes[b]++;
            }
        }
        if (votes == null) {
            return picked;
        }
        int best = own, most = 0;
        for (int b = 0; b < votes.length; b++) {
            if (votes[b] > most) {
                most = votes[b];
                best = b;
            }
        }
        return indexed.get(best);
    }

    // The biome index of a grid point before the cleanup, kept in a small shared table (a point is asked for by every
    // column around it). Each entry is one long: the point (24 bits each way, enough for the 30 million block border at
    // 16 blocks a step), the biome index + 1 (8 bits); a lost entry is just worked out again.
    private static final int GRID_BITS = 16;
    private volatile GridCache gridCache = new GridCache(0L);

    private record GridCache(long salt, java.util.concurrent.atomic.AtomicLongArray table) {
        GridCache(long salt) {
            this(salt, new java.util.concurrent.atomic.AtomicLongArray(1 << GRID_BITS));
        }
    }

    private int grid(Climate.Sampler sampler, long salt, int gx, int gz) {
        GridCache cache = gridCache;
        if (cache.salt() != salt) {
            cache = new GridCache(salt);
            gridCache = cache;
        }
        long point = ((long) (gx & 0xFFFFFF) << 24 | (gz & 0xFFFFFF)) << 8;
        int slot = (int) (mix(point ^ 0x9E3779B97F4A7C15L) & ((1 << GRID_BITS) - 1));
        long entry = cache.table().get(slot);
        if ((entry & ~0xFFL) == point && (entry & 0xFF) != 0) {
            return (int) (entry & 0xFF) - 1;
        }
        int bx = gx * SCRAP_STEP, bz = gz * SCRAP_STEP;
        int qx = QuartPos.fromBlock(bx), qz = QuartPos.fromBlock(bz);
        Climate.TargetPoint target = sampler.sample(qx, SCRAP_Y, qz);
        ResourceKey<Biome> key = vanilla.findValue(target).unwrapKey().orElseThrow();
        if (CAVES.contains(key)) {
            // (never at this height; the land around it then)
            key = Biomes.PLAINS;
        }
        int b = index.get(pick(sampler, target, key, bx, bz));
        cache.table().set(slot, point | (b + 1));
        return b;
    }
}
