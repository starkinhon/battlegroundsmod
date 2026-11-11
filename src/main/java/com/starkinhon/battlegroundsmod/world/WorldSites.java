package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;
import com.starkinhon.battlegroundsmod.power.Progression;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The Dragon Ball buildings out in the normal world (Stark 2026-11-11: os universos viram biomas, com estruturas que dão
// itens do universo). The world is cut in big cells; each cell has one spot picked from the world seed, and what stands
// there depends on the biome under it: Capsule Corp, Goku's house, Korin's tower (with Kami's lookout floating near it)
// or Cell's ring in the Dragon Lands, the crashed ship, Raditz's crater, Babidi's ship or Cell's ring in the Red
// Desert, Buu's house in the peaks, the Kame House on a beach by them; nothing anywhere else. A spot is looked at
// when a player comes near and, if it is one, built through MapPlaces (a few thousand blocks per tick, once, saved).
// The same choice is made without building anything by locate() (the missions' arrow).
public final class WorldSites {
    private static final int CELL = 448;
    private static final int MARGIN = 80;
    private static final double TRIGGER = 170;
    private static final int LOOKOUT_Y = 281;
    private static final int LOOKOUT_OFFSET = 110;
    private static final int KORIN_FLOOR = 162;

    public static final String CAPSULE = "capsula", GOKU = "goku", KARIN = "karin", CELL_RING = "cell", WRECK = "nave",
            RADITZ = "raditz", BABIDI = "babidi", BUU = "buu", KAME = "kame", LOOKOUT = "lookout";

    private static final List<String> LANDS = List.of(CAPSULE, GOKU, CELL_RING, RADITZ, CAPSULE, GOKU);
    // Korin's tower stands in the dense forest (Stark 2026-11-11), Goku's house now and then at its edge.
    private static final List<String> FOREST = List.of(KARIN, KARIN, GOKU);
    private static final List<String> DESERT = List.of(WRECK, RADITZ, BABIDI, CELL_RING);

    private WorldSites() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(WorldSites::onServerTick);
    }

    // A built (or chosen) place: its kind and floor.
    public record Site(String kind, int x, int y, int z) {
        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 40 != 17 || !Progression.on(event.getServer())) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        Data data = data(level);
        if (!data.restored) {
            data.restored = true;
            for (Site site : data.sites()) {
                links(level, site);
            }
        }
        for (ServerPlayer player : level.players()) {
            int pcx = Math.floorDiv(player.getBlockX(), CELL);
            int pcz = Math.floorDiv(player.getBlockZ(), CELL);
            for (int cx = pcx - 1; cx <= pcx + 1; cx++) {
                for (int cz = pcz - 1; cz <= pcz + 1; cz++) {
                    String key = cx + "," + cz;
                    if (data.cells.containsKey(key)) {
                        continue;
                    }
                    int[] spot = spot(level, cx, cz);
                    double dx = player.getX() - spot[0];
                    double dz = player.getZ() - spot[1];
                    if (dx * dx + dz * dz > TRIGGER * TRIGGER) {
                        continue;
                    }
                    String kind = kindAt(level, spot[0], spot[1], spot[2]);
                    if (kind == null) {
                        data.cells.put(key, "-");
                        data.setDirty();
                        continue;
                    }
                    Site site = new Site(kind, spot[0], floor(level, kind, spot[0], spot[1]), spot[1]);
                    data.cells.put(key, site.kind() + " " + site.x() + " " + site.y() + " " + site.z());
                    data.setDirty();
                    links(level, site);
                    MapPlaces.startAt(level, place(site));
                    if (KARIN.equals(kind)) {
                        MapPlaces.startAt(level, lookoutPlace(site));
                    }
                }
            }
        }
    }

    // The cell's spot: x, z and a random number for the kind, all from the world seed.
    private static int[] spot(ServerLevel level, int cx, int cz) {
        RandomSource random = RandomSource.create(level.getSeed() ^ (cx * 341873128712L) ^ (cz * 132897987541L) ^ 0x5D0B5L);
        int x = cx * CELL + MARGIN + random.nextInt(CELL - 2 * MARGIN);
        int z = cz * CELL + MARGIN + random.nextInt(CELL - 2 * MARGIN);
        return new int[] {x, z, random.nextInt(1 << 20)};
    }

    // The biome at a spot from the world's noise alone (nothing is generated or loaded).
    private static Holder<Biome> biomeAt(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        return source.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(80),
                QuartPos.fromBlock(z), source.randomState().sampler());
    }

    private static String kindAt(ServerLevel level, int x, int z, int roll) {
        Holder<Biome> biome = biomeAt(level, x, z);
        if (biome.is(ModWorldgen.TERRAS_DO_DRAGAO)) {
            return LANDS.get(roll % LANDS.size());
        }
        if (biome.is(ModWorldgen.DESERTO_VERMELHO)) {
            return DESERT.get(roll % DESERT.size());
        }
        if (biome.is(ModWorldgen.TERRA_SAGRADA)) {
            return FOREST.get(roll % FOREST.size());
        }
        if (biome.is(ModWorldgen.PICOS_DO_DRAGAO)) {
            return BUU;
        }
        if (biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_OCEAN)) {
            // The Kame House: on the coast of a Dragon Ball land.
            for (int a = 0; a < 8; a++) {
                double angle = a * Math.PI / 4;
                Holder<Biome> near = biomeAt(level, x + (int) (Math.cos(angle) * 64), z + (int) (Math.sin(angle) * 64));
                if (near.is(ModWorldgen.DRAGONBALL_BIOMES)) {
                    return KAME;
                }
            }
        }
        return null;
    }

    // The floor of a place: the middle height of the ground around it (the sea's surface for the Kame House).
    private static int floor(ServerLevel level, String kind, int x, int z) {
        int[] heights = new int[9];
        int n = 0;
        for (int dx = -12; dx <= 12; dx += 12) {
            for (int dz = -12; dz <= 12; dz += 12) {
                heights[n++] = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x + dx, z + dz);
            }
        }
        Arrays.sort(heights);
        int y = heights[4];
        if (KAME.equals(kind)) {
            y = Math.max(y, level.getSeaLevel());
        }
        return Math.max(y, level.getSeaLevel() - 1);
    }

    // Where someone gets the buildings and pads once a site is known (and again after a restart).
    private static void links(ServerLevel level, Site site) {
        MapPlaces.addWorldPlace(place(site));
        if (KARIN.equals(site.kind())) {
            MapPlaces.addWorldPlace(lookoutPlace(site));
            BlockPos korinPad = new BlockPos(site.x() + 14, site.y() + KORIN_FLOOR, site.z());
            BlockPos besideKorin = new BlockPos(site.x() + 14, site.y() + KORIN_FLOOR, site.z() + 3);
            int lx = site.x(), lz = site.z() - LOOKOUT_OFFSET;
            BlockPos lookoutPad = new BlockPos(lx, LOOKOUT_Y - 1, lz + 40);
            BlockPos besideLookout = new BlockPos(lx, LOOKOUT_Y - 1, lz + 36);
            MapPlaces.addWorldPad(new MapPlaces.Pad(id(site) + "/ir_kami", id(site), korinPad, lookoutId(site), besideLookout,
                    "message.battlegroundsmod.pad_kami"));
            MapPlaces.addWorldPad(new MapPlaces.Pad(id(site) + "/voltar_karin", lookoutId(site), lookoutPad, id(site), besideKorin,
                    "message.battlegroundsmod.pad_karin"));
        }
    }

    private static String id(Site site) {
        return "mundo/" + site.kind() + "@" + site.x() + "," + site.z();
    }

    private static String lookoutId(Site site) {
        return "mundo/lookout@" + site.x() + "," + site.z();
    }

    private static MapPlaces.Place place(Site site) {
        return new MapPlaces.Place(id(site), site.x(), site.z(), map -> build(site),
                KARIN.equals(site.kind()) ? (level, b) -> korin(level, b) : null);
    }

    private static MapPlaces.Place lookoutPlace(Site site) {
        int lz = site.z() - LOOKOUT_OFFSET;
        return new MapPlaces.Place(lookoutId(site), site.x(), lz,
                map -> new Build(site.x(), LOOKOUT_Y, lz, 280).template("dragonball/kami_lookout", 0, 0, 0), null);
    }

    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    private static Build build(Site site) {
        Build b = new Build(site.x(), site.y() + 1, site.z(), (long) site.x() * 31 + site.z());
        BlockState grass = s(Blocks.GRASS_BLOCK), dirt = s(Blocks.DIRT);
        BlockState sand = s(Blocks.RED_SAND), sandstone = s(Blocks.RED_SANDSTONE);
        return switch (site.kind()) {
            case CAPSULE -> b.clear(38, 0, 32).foundation(37, 10, grass, dirt).template("dragonball/capsule_corp", 0, 0, 0);
            case GOKU -> b.clear(24, 0, 24).foundation(23, 10, grass, dirt).template("dragonball/goku_house", 0, 0, 0);
            case KARIN -> b.clear(20, 0, 14).foundation(19, 8, grass, dirt).template("dragonball/korin_tower", 0, 0, 0);
            case CELL_RING -> b.clear(34, 0, 48).foundation(33, 10, s(Blocks.STONE), s(Blocks.STONE))
                    .template("dragonball/cell_arena", 0, 0, 0);
            case BABIDI -> b.clear(20, 0, 12).template("dragonball/babidi_ship", 0, 0, 0);
            case BUU -> b.clear(17, 0, 18).foundation(16, 10, grass, dirt).template("dragonball/buu_house", 0, 0, 0);
            case KAME -> b.clear(17, 0, 24).foundation(16, 6, s(Blocks.SAND), s(Blocks.SANDSTONE))
                    .template("dragonball/kame_house", 0, 0, 0);
            case WRECK -> wreck(b, sand, sandstone);
            case RADITZ -> raditz(b);
            default -> b;
        };
    }

    // The crashed Capsule Corp ship, sunk in a scorched patch with wreckage and smoke around it.
    private static Build wreck(Build b, BlockState sand, BlockState sandstone) {
        b.clear(14, 0, 16).foundation(13, 6, sand, sandstone);
        for (int n = 0; n < 30; n++) {
            double a = b.random.nextDouble() * Math.PI * 2;
            int r = 9 + b.random.nextInt(9);
            int i = (int) Math.round(Math.cos(a) * r), k = (int) Math.round(Math.sin(a) * r);
            Block piece = switch (b.random.nextInt(6)) {
                case 0 -> Blocks.IRON_BLOCK;
                case 1 -> Blocks.WHITE_CONCRETE;
                case 2 -> Blocks.LIGHT_GRAY_CONCRETE;
                case 3 -> Blocks.CHAIN;
                case 4 -> Blocks.IRON_BARS;
                default -> Blocks.COARSE_DIRT;
            };
            b.set(i, 0, k, piece);
        }
        for (int[] c : new int[][] {{10, -6}, {-8, 9}, {12, 8}, {-11, -7}}) {
            b.set(c[0], 0, c[1], Blocks.CAMPFIRE);
        }
        return b.template("dragonball/capsule_ship_wreck", 0, 0, 0);
    }

    // Raditz's landing: a scorched crater with cracks running out, the round Saiyan pod in the middle, hatch open.
    private static Build raditz(Build b) {
        b.clear(12, 0, 8);
        for (int i = -11; i <= 11; i++) {
            for (int k = -11; k <= 11; k++) {
                double d = Math.sqrt(i * i + k * k);
                if (d > 11) {
                    continue;
                }
                int depth = (int) Math.round(4 * (1 - (d / 11) * (d / 11)));
                for (int j = -depth; j < 0; j++) {
                    b.set(i, j, k, Blocks.AIR);
                }
                b.set(i, -depth - 1, k, b.random.nextInt(3) == 0 ? Blocks.BLACKSTONE : Blocks.COARSE_DIRT);
                for (int j = -depth - 5; j < -depth - 1; j++) {
                    b.set(i, j, k, Blocks.DIRT);
                }
            }
        }
        for (int ray = 0; ray < 11; ray++) {
            double a = ray * Math.PI * 2 / 11 + b.random.nextDouble() * 0.4;
            int length = 12 + b.random.nextInt(10);
            for (int step = 9; step < length; step++) {
                int i = (int) Math.round(Math.cos(a) * step), k = (int) Math.round(Math.sin(a) * step);
                b.set(i, -1, k, b.random.nextInt(4) == 0 ? Blocks.BASALT : Blocks.BLACKSTONE);
            }
        }
        b.ball(0, -3, 0, 2.6, s(Blocks.WHITE_CONCRETE), s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.RED_STAINED_GLASS));
        b.set(0, -3, 0, Blocks.AIR).set(0, -2, 0, Blocks.AIR).set(2, -3, 0, Blocks.AIR).set(2, -2, 0, Blocks.AIR);
        b.set(-3, -3, 2, Blocks.CAMPFIRE).set(2, -3, -3, Blocks.CAMPFIRE);
        return b;
    }

    // Korin on top of his tower.
    private static void korin(ServerLevel level, Build b) {
        BlockPos top = new BlockPos(b.x - 8, b.y + KORIN_FLOOR + 1, b.z);
        if (!level.getEntities(ModEntities.NPC_KORIN.get(), new AABB(top).inflate(48, 64, 48), e -> e.isAlive()).isEmpty()) {
            return;
        }
        UniverseNpc korin = ModEntities.NPC_KORIN.get().spawn(level, top, MobSpawnType.EVENT);
        if (korin != null) {
            korin.restrictTo(top, 3);
            korin.setCustomName(korin.getType().getDescription());
            korin.setCustomNameVisible(true);
        }
    }

    // The nearest known place of a kind within a radius, or null (the activities: Bulma at Capsule Corp...).
    public static Site near(Level level, String kind, BlockPos from, double radius) {
        if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) {
            return null;
        }
        Site best = null;
        double bestD = radius * radius;
        for (Site site : data(server).sites()) {
            if (!site.kind().equals(kind)) {
                continue;
            }
            double dx = site.x() - from.getX();
            double dz = site.z() - from.getZ();
            if (dx * dx + dz * dz < bestD) {
                bestD = dx * dx + dz * dz;
                best = site;
            }
        }
        return best;
    }

    // Every known place (built or about to be).
    public static List<Site> known(ServerLevel level) {
        return data(level).sites();
    }

    // The nearest spot of a kind, found or not, out to some cells away (from the noise; nothing is generated).
    public static BlockPos locate(ServerLevel level, String kind, BlockPos from, int cells) {
        Site site = near(level, kind, from, Double.MAX_VALUE / 4);
        BlockPos best = site == null ? null : site.pos();
        double bestD = best == null ? Double.MAX_VALUE : best.distSqr(from);
        int pcx = Math.floorDiv(from.getX(), CELL);
        int pcz = Math.floorDiv(from.getZ(), CELL);
        for (int cx = pcx - cells; cx <= pcx + cells; cx++) {
            for (int cz = pcz - cells; cz <= pcz + cells; cz++) {
                int[] spot = spot(level, cx, cz);
                double dx = spot[0] - from.getX(), dz = spot[1] - from.getZ();
                if (dx * dx + dz * dz >= bestD) {
                    continue;
                }
                if (kind.equals(kindAt(level, spot[0], spot[1], spot[2]))) {
                    bestD = dx * dx + dz * dz;
                    best = new BlockPos(spot[0], 0, spot[1]);
                }
            }
        }
        return best;
    }

    // The pad in front of the Time Chamber's door of a lookout near this spot (world/TimeChamber), or null.
    public static BlockPos doorPadNear(ServerLevel level, BlockPos from) {
        Site karin = near(level, KARIN, from.offset(0, 0, LOOKOUT_OFFSET), 120);
        if (karin == null) {
            return null;
        }
        return new BlockPos(karin.x(), LOOKOUT_Y, karin.z() - LOOKOUT_OFFSET - 49);
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                "battlegroundsmod_sites");
    }

    public static final class Data extends SavedData {
        final Map<String, String> cells = new HashMap<>();
        boolean restored;

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            CompoundTag cells = tag.getCompound("cells");
            for (String key : cells.getAllKeys()) {
                data.cells.put(key, cells.getString(key));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag cells = new CompoundTag();
            this.cells.forEach(cells::putString);
            tag.put("cells", cells);
            return tag;
        }

        List<Site> sites() {
            List<Site> list = new ArrayList<>();
            for (String entry : cells.values()) {
                String[] parts = entry.split(" ");
                if (parts.length == 4) {
                    try {
                        list.add(new Site(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return list;
        }
    }
}
