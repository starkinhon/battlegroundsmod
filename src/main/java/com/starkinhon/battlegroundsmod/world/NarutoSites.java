package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModBlocks;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.universe.NarutoActivities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The Naruto structures out in the normal world (Stark 2026-11-12), as WorldSites does for Dragon Ball: the world is cut
// in big cells, each with one spot from the world seed; what stands there depends on the biome under it (one structure
// per Naruto biome, rare: Training Ground 3 in the Folha, the Chunin Exam tower or the Snake's Den in the Forest of Death,
// the Sealed Hideout in the Wind Desert, the Unfinished Bridge and the Swordsman's Grave in the Mist Sea, the Angel's Tower
// in the Rain Village, the Sages' Spring or the Sage's Peak in Myoboku). The blocks come from NarutoStructGen (the
// preview's generator, line by line) turned a quarter, a half or three quarters by the spot, and are built through
// MapPlaces when a player comes near. The Valley of the End has its seals on the heads of its own statues, in every
// valley. What each place does is in universe/NarutoActivities.
public final class NarutoSites {
    private static final int CELL = 560;
    private static final int MARGIN = 110;
    private static final double TRIGGER = 180;

    public static final String VALE = "vale";

    private NarutoSites() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(NarutoSites::onServerTick);
    }

    // A place: its kind, center (floor height in y), quarter turns and the generator's seed; marks = the spots the
    // activities use, in world coordinates.
    public record Site(String kind, int x, int y, int z, int rot, int seed, Map<String, List<BlockPos>> marks) {
        public String id() {
            return "naruto/" + kind + "@" + x + "," + z;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        public List<BlockPos> mark(String name) {
            return marks.getOrDefault(name, List.of());
        }

        public BlockPos first(String name) {
            List<BlockPos> list = mark(name);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 40 != 23 || !Progression.on(event.getServer())) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        Data data = data(level);
        if (!data.restored) {
            data.restored = true;
            // a place whose building was cut by a restart starts again (it is the same every time)
            for (Site site : data.sites.values()) {
                if (!MapPlaces.built(level, site.id())) {
                    MapPlaces.startAt(level, place(level, site));
                } else if (!VALE.equals(site.kind())) {
                    // known to MapPlaces so a place built before the loot gets it when someone comes by (StructureLoot)
                    MapPlaces.addWorldPlace(place(level, site));
                }
            }
        }
        for (ServerPlayer player : level.players()) {
            int pcx = Math.floorDiv(player.getBlockX(), CELL);
            int pcz = Math.floorDiv(player.getBlockZ(), CELL);
            for (int cx = pcx - 1; cx <= pcx + 1; cx++) {
                for (int cz = pcz - 1; cz <= pcz + 1; cz++) {
                    String key = cx + "," + cz;
                    if (data.cells.contains(key)) {
                        continue;
                    }
                    int[] spot = spot(level, cx, cz);
                    double dx = player.getX() - spot[0];
                    double dz = player.getZ() - spot[1];
                    if (dx * dx + dz * dz > TRIGGER * TRIGGER) {
                        continue;
                    }
                    data.cells.add(key);
                    data.setDirty();
                    String kind = kindAt(level, spot[0], spot[1], spot[2]);
                    if (kind == null) {
                        continue;
                    }
                    Site site = new Site(kind, spot[0], 0, spot[1], spot[2] & 3, spot[3], new LinkedHashMap<>());
                    MapPlaces.startAt(level, place(level, site));
                }
            }
            valleys(level, data, player);
        }
    }

    // The cell's spot: x, z, a number for the kind and turn, and the generator's seed, all from the world seed.
    private static int[] spot(ServerLevel level, int cx, int cz) {
        RandomSource random = RandomSource.create(level.getSeed() ^ (cx * 341873128712L) ^ (cz * 132897987541L) ^ 0x7A2B0L);
        int x = cx * CELL + MARGIN + random.nextInt(CELL - 2 * MARGIN);
        int z = cz * CELL + MARGIN + random.nextInt(CELL - 2 * MARGIN);
        int[] spot = {x, z, random.nextInt(1 << 20), random.nextInt()};
        // the Floresta da Morte is thin patches on the Folha's wettest edge (3 PC tests of 2026-11-12: one place in 7,800
        // blocks): a cell with any of it puts its place there
        if (!biomeAt(level, x, z).is(ModWorldgen.FLORESTA_DA_MORTE)) {
            for (int gx = cx * CELL + MARGIN; gx < (cx + 1) * CELL - MARGIN; gx += 56) {
                for (int gz = cz * CELL + MARGIN; gz < (cz + 1) * CELL - MARGIN; gz += 56) {
                    if (biomeAt(level, gx, gz).is(ModWorldgen.FLORESTA_DA_MORTE)) {
                        spot[0] = gx;
                        spot[1] = gz;
                        return spot;
                    }
                }
            }
        }
        return spot;
    }

    private static Holder<Biome> biomeAt(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        return source.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(64),
                QuartPos.fromBlock(z), source.randomState().sampler());
    }

    // The structure of a spot's biome, or none; the whole footprint has to be in that biome (no tower half in a plain).
    private static String kindAt(ServerLevel level, int x, int z, int roll) {
        Holder<Biome> biome = biomeAt(level, x, z);
        String kind;
        int reach;
        boolean death = biome.is(ModWorldgen.FLORESTA_DA_MORTE);
        if (!death && biome.is(ModWorldgen.FLORESTA_DA_FOLHA)) {
            // the Floresta da Morte is a thin strip on the Folha's wettest edge (2nd PC test of 2026-11-12: the first
            // whole spot 6,400 blocks out): a Folha spot with the Morte beside it takes the Morte's place, on the edge
            for (int a = 0; a < 8 && !death; a++) {
                double angle = a * Math.PI / 4;
                death = biomeAt(level, x + (int) (Math.cos(angle) * 48), z + (int) (Math.sin(angle) * 48)).is(ModWorldgen.FLORESTA_DA_MORTE);
            }
        }
        if (death) {
            // the Snake's Den is the rarer one (the only home of the cobra_gigante)
            kind = roll % 5 < 2 ? NarutoStructGen.COVIL : NarutoStructGen.MORTE;
            reach = 32;
        } else if (biome.is(ModWorldgen.FLORESTA_DA_FOLHA)) {
            kind = NarutoStructGen.FOLHA;
            reach = 40;
        } else if (biome.is(ModWorldgen.DESERTO_DO_VENTO)) {
            kind = NarutoStructGen.DESERTO;
            reach = 48;
        } else if (biome.is(ModWorldgen.MAR_DA_NEVOA)) {
            kind = NarutoStructGen.NEVOA;
            reach = 60;
        } else if (biome.is(ModWorldgen.VILA_DA_CHUVA)) {
            kind = NarutoStructGen.CHUVA;
            reach = 40;
        } else if (biome.is(ModWorldgen.MONTE_MYOBOKU)) {
            kind = roll % 3 == 0 ? NarutoStructGen.PICO : NarutoStructGen.MYOBOKU;
            reach = 36;
        } else {
            return null;
        }
        var key = biome.unwrapKey().orElse(null);
        for (int a = 0; a < 8; a++) {
            double angle = a * Math.PI / 4;
            if (key == null) {
                return null;
            }
            Holder<Biome> around = biomeAt(level, x + (int) (Math.cos(angle) * reach), z + (int) (Math.sin(angle) * reach));
            // the Morte's places may spill into the Folha around it (and the other way round)
            // (4th PC test: still 6,500 blocks out with the Folha's ring; the Morte's patches are small, so its places
            // only need their middle in it)
            boolean ok = death || around.is(key);
            if (!ok) {
                return null;
            }
        }
        return kind;
    }

    // ---------------------------------------------------------------- building

    // Local (x, z) of the generator to the world, turned rot quarters clockwise (as Rotation does: north to east).
    static int wx(Site s, int lx, int lz) {
        return s.x() + switch (s.rot()) {
            case 1 -> -lz;
            case 2 -> -lx;
            case 3 -> lz;
            default -> lx;
        };
    }

    static int wz(Site s, int lx, int lz) {
        return s.z() + switch (s.rot()) {
            case 1 -> lx;
            case 2 -> -lz;
            case 3 -> -lx;
            default -> lz;
        };
    }

    private static Rotation rotation(Site s) {
        return switch (s.rot()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    // The natural ground of the world (the top solid block) without generating anything.
    private static int natural(ServerLevel level, int x, int z) {
        long seed = level.getSeed();
        var source = level.getChunkSource();
        return TerrainHeight.at(source.getGenerator(), level, source.randomState(), (int) (seed ^ (seed >>> 32)), x, z) - 1;
    }

    private static MapPlaces.Place place(ServerLevel level, Site site) {
        if (VALE.equals(site.kind())) {
            return new MapPlaces.Place(site.id(), site.x(), site.z(), map -> valeBuild(level, site), (l, b) -> done(l, site));
        }
        return new MapPlaces.Place(site.id(), site.x(), site.z(), map -> build(level, site), (l, b) -> done(l, site));
    }

    private static void done(ServerLevel level, Site site) {
        Data data = data(level);
        Site known = data.sites.get(site.id());
        NarutoActivities.built(level, known != null ? known : site);
    }

    // The build of a known place, made again without placing anything (StructureLoot's /soul bau).
    static Build buildOf(ServerLevel level, Site s) {
        return build(level, s);
    }

    private static Build build(ServerLevel level, Site s) {
        int sea = level.getSeaLevel() - 1;
        int[] heights = new int[9];
        int n = 0;
        for (int dx = -14; dx <= 14; dx += 14) {
            for (int dz = -14; dz <= 14; dz += 14) {
                heights[n++] = natural(level, s.x() + dx, s.z() + dz);
            }
        }
        Arrays.sort(heights);
        int floor = Math.max(heights[4], sea);
        Map<Long, Integer> cache = new HashMap<>();
        NarutoStructGen.World world = new NarutoStructGen.World() {
            @Override
            public int ground(int lx, int lz) {
                return cache.computeIfAbsent(((long) lx << 32) ^ (lz & 0xFFFFFFFFL), k -> natural(level, wx(s, lx, lz), wz(s, lx, lz)));
            }

            @Override
            public int floor() {
                return floor;
            }

            @Override
            public int sea() {
                return sea;
            }
        };
        NarutoStructGen.Result res = NarutoStructGen.generate(s.kind(), s.seed(), world);
        // the place as it is known from now on: its floor and the activities' spots in the world
        Map<String, List<BlockPos>> marks = new LinkedHashMap<>();
        res.marks.forEach((name, list) -> {
            List<BlockPos> out = new ArrayList<>();
            for (int[] p : list) {
                out.add(new BlockPos(wx(s, p[0], p[2]), p[1], wz(s, p[0], p[2])));
            }
            marks.put(name, out);
        });
        Site site = new Site(s.kind(), s.x(), floor, s.z(), s.rot(), s.seed(), marks);
        Data data = data(level);
        data.sites.put(site.id(), site);
        data.setDirty();

        Build b = new Build(s.x(), 0, s.z(), s.seed());
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int[] c : res.columns) {
            b.puts.add(new Build.Put(new BlockPos(wx(s, c[0], c[2]), c[1] + 1, wz(s, c[0], c[2])), air, true));
        }
        List<Long> keys = new ArrayList<>(res.w.m.keySet());
        keys.sort(Comparator.comparingInt(NarutoStructGen.Vox::ky));
        Rotation rot = rotation(s);
        for (long k : keys) {
            int lx = NarutoStructGen.Vox.kx(k), ly = NarutoStructGen.Vox.ky(k), lz = NarutoStructGen.Vox.kz(k);
            int t = res.w.m.get(k);
            BlockState state = t == NarutoStructGen.AIR ? (roofed(res.w, lx, ly, lz) ? LIGHT : air) : state(s.kind(), t, res.w, lx, ly, lz);
            if (state == null) {
                continue;
            }
            b.puts.add(new Build.Put(new BlockPos(wx(s, lx, lz), ly, wz(s, lx, lz)), state.rotate(rot)));
        }
        return b;
    }

    // Rooms lit without lamps on show (the PC test of 2026-11-12: the Rain tower was pitch dark inside): an invisible
    // light every 4 blocks on the floor of carved air under a roof.
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 13);

    private static boolean roofed(NarutoStructGen.Vox w, int x, int y, int z) {
        if (Math.floorMod(x, 4) != 0 || Math.floorMod(z, 4) != 0) {
            return false;
        }
        int below = w.get(x, y - 1, z);
        if (below == NarutoStructGen.AIR || !solid(below)) {
            return false;
        }
        for (int dy = 2; dy <= 14; dy++) {
            int t = w.get(x, y + dy, z);
            if (t != NarutoStructGen.AIR && solid(t)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the preview's blocks in Minecraft

    private static BlockState d(Block block) {
        return block.defaultBlockState();
    }

    private static boolean solid(int t) {
        return switch (t) {
            case NarutoStructGen.AIR, NarutoStructGen.WATER, NarutoStructGen.OIL, NarutoStructGen.OILFALL, NarutoStructGen.FENCE,
                    NarutoStructGen.BARS, NarutoStructGen.ROPE, NarutoStructGen.THREAD, NarutoStructGen.BELL, NarutoStructGen.LANTERN,
                    NarutoStructGen.CHEST, NarutoStructGen.FLOWER, NarutoStructGen.BONE, NarutoStructGen.PAPER, NarutoStructGen.VINE,
                    NarutoStructGen.SLAB, NarutoStructGen.ROOF_SLAB, NarutoStructGen.PLANK_SLAB, NarutoStructGen.SPIKE,
                    NarutoStructGen.LEAVES, NarutoStructGen.LEAVES_D, NarutoStructGen.PINE -> false;
            default -> true;
        };
    }

    // The block of a cell in local space (turned with the place afterwards); null = leave the world as it is.
    static BlockState state(String kind, int t, NarutoStructGen.Vox w, int x, int y, int z) {
        return switch (t) {
            case NarutoStructGen.STONE -> d(Blocks.STONE);
            case NarutoStructGen.COBBLE -> d(Blocks.COBBLESTONE);
            case NarutoStructGen.MOSSY -> d(Blocks.MOSSY_COBBLESTONE);
            case NarutoStructGen.BRICK -> d(Blocks.STONE_BRICKS);
            case NarutoStructGen.SMOOTH -> d(Blocks.SMOOTH_STONE);
            case NarutoStructGen.DARK -> d(Blocks.POLISHED_BLACKSTONE);
            case NarutoStructGen.DARK_CARVE -> d(Blocks.CHISELED_POLISHED_BLACKSTONE);
            case NarutoStructGen.GRASS, NarutoStructGen.DARK_GRASS -> d(Blocks.GRASS_BLOCK);
            case NarutoStructGen.DIRT -> d(Blocks.DIRT);
            case NarutoStructGen.COARSE -> d(Blocks.COARSE_DIRT);
            case NarutoStructGen.PODZOL -> d(Blocks.PODZOL);
            case NarutoStructGen.GRAVEL -> d(Blocks.GRAVEL);
            case NarutoStructGen.SAND -> d(Blocks.SAND);
            case NarutoStructGen.SANDSTONE -> d(Blocks.SANDSTONE);
            case NarutoStructGen.ROCK_R -> d(Blocks.ORANGE_TERRACOTTA);
            case NarutoStructGen.ROCK_L -> d(Blocks.YELLOW_TERRACOTTA);
            case NarutoStructGen.ROCK_D -> d(Blocks.TERRACOTTA);
            case NarutoStructGen.WATER, NarutoStructGen.OIL -> d(Blocks.WATER);
            // the oil falling from the toad's mouth: one row of source at the top, the rest falls by itself
            case NarutoStructGen.OILFALL -> w.get(x, y + 1, z) == NarutoStructGen.OILFALL ? null : d(Blocks.WATER);
            case NarutoStructGen.LOG -> d(Blocks.OAK_LOG);
            case NarutoStructGen.LOG_CUT -> d(Blocks.STRIPPED_OAK_LOG);
            case NarutoStructGen.PLANK -> d(Blocks.OAK_PLANKS);
            case NarutoStructGen.DPLANK -> d(Blocks.DARK_OAK_PLANKS);
            case NarutoStructGen.DLOG -> d(Blocks.DARK_OAK_LOG);
            case NarutoStructGen.LEAVES -> leaves(NarutoStructGen.FOLHA.equals(kind) ? ModBlocks.LEAF_LEAVES.get() : Blocks.OAK_LEAVES);
            case NarutoStructGen.LEAVES_D -> leaves(switch (kind) {
                case NarutoStructGen.FOLHA -> ModBlocks.LEAF_LEAVES_DARK.get();
                case NarutoStructGen.MORTE, NarutoStructGen.COVIL -> ModBlocks.DEATH_LEAVES_DARK.get();
                case NarutoStructGen.MYOBOKU -> ModBlocks.GIANT_LEAF_DARK.get();
                default -> Blocks.DARK_OAK_LEAVES;
            });
            case NarutoStructGen.PINE_LOG -> d(Blocks.SPRUCE_LOG);
            case NarutoStructGen.PINE -> leaves(ModBlocks.PINE_LEAVES.get());
            case NarutoStructGen.FENCE -> cross(d(Blocks.OAK_FENCE), w, x, y, z);
            case NarutoStructGen.BARS -> cross(d(Blocks.IRON_BARS), w, x, y, z);
            case NarutoStructGen.ROPE -> d(ModBlocks.ROPE.get());
            case NarutoStructGen.TARGET -> d(Blocks.TARGET);
            case NarutoStructGen.BELL -> bell(w, x, y, z);
            case NarutoStructGen.PAPER -> seal(w, x, y, z);
            case NarutoStructGen.METAL -> d(ModBlocks.RAIN_METAL.get());
            case NarutoStructGen.METAL_D -> d(ModBlocks.RAIN_METAL_DARK.get());
            case NarutoStructGen.RUST -> d(ModBlocks.RUST.get());
            case NarutoStructGen.WIN -> d(Blocks.BLACK_STAINED_GLASS);
            case NarutoStructGen.WIN_LIT -> NarutoStructGen.MYOBOKU.equals(kind) ? d(Blocks.SHROOMLIGHT) : d(ModBlocks.RAIN_WINDOW_LIT.get());
            case NarutoStructGen.PIPE -> d(ModBlocks.RAIN_PIPE.get());
            case NarutoStructGen.RED -> d(Blocks.RED_CONCRETE);
            case NarutoStructGen.ROOF -> d(Blocks.RED_TERRACOTTA);
            case NarutoStructGen.BONE -> d(Blocks.BONE_BLOCK);
            case NarutoStructGen.SKIN -> d(Blocks.SMOOTH_SANDSTONE);
            case NarutoStructGen.BLADE -> d(Blocks.IRON_BLOCK);
            case NarutoStructGen.WRAP -> d(Blocks.BLACK_WOOL);
            case NarutoStructGen.CALCITE -> d(Blocks.CALCITE);
            case NarutoStructGen.STATUE -> d(ModBlocks.STATUE_STONE.get());
            case NarutoStructGen.STATUE_M -> d(ModBlocks.STATUE_STONE_MOSS.get());
            case NarutoStructGen.LEAF_G -> d(ModBlocks.GIANT_LEAF.get());
            case NarutoStructGen.STEM -> d(ModBlocks.GIANT_STALK.get());
            case NarutoStructGen.SHELF -> d(Blocks.BOOKSHELF);
            case NarutoStructGen.CLOTH -> d(Blocks.PURPLE_TERRACOTTA);
            case NarutoStructGen.PUPPET -> d(Blocks.STRIPPED_OAK_WOOD);
            case NarutoStructGen.GOLD -> d(Blocks.GOLD_BLOCK);
            case NarutoStructGen.LANTERN -> d(Blocks.LANTERN).setValue(LanternBlock.HANGING,
                    solid(w.get(x, y + 1, z)) && !solid(w.get(x, y - 1, z)));
            case NarutoStructGen.CHEST -> d(Blocks.CHEST);
            case NarutoStructGen.FLOWER -> d(switch (kind) {
                case NarutoStructGen.DESERTO -> Blocks.DEAD_BUSH;
                case NarutoStructGen.NEVOA -> Blocks.BLUE_ORCHID;
                case NarutoStructGen.CHUVA -> Blocks.WHITE_TULIP;
                default -> Blocks.LILY_OF_THE_VALLEY;
            });
            case NarutoStructGen.VINE -> vine(w, x, y, z);
            case NarutoStructGen.THREAD -> d(ModBlocks.CHAKRA_THREAD.get());
            case NarutoStructGen.PEAK -> d(ModBlocks.SPIRE_STONE.get());
            case NarutoStructGen.PEAK_M -> d(ModBlocks.SPIRE_STONE_DARK.get());
            case NarutoStructGen.CARVE -> d(Blocks.POLISHED_BLACKSTONE);
            case NarutoStructGen.WHITE -> d(Blocks.WHITE_CONCRETE);
            case NarutoStructGen.BLACK -> d(Blocks.BLACK_CONCRETE);
            case NarutoStructGen.ROOT_W -> d(Blocks.MUSHROOM_STEM);
            case NarutoStructGen.MUD -> d(Blocks.MUD);
            case NarutoStructGen.BARREL -> d(Blocks.BARREL);
            case NarutoStructGen.SLAB -> d(Blocks.STONE_SLAB);
            case NarutoStructGen.ROOF_SLAB -> d(Blocks.RED_NETHER_BRICK_SLAB);
            case NarutoStructGen.PLANK_SLAB -> d(Blocks.OAK_SLAB);
            case NarutoStructGen.SPIKE -> d(ModBlocks.RAIN_SPIKE.get());
            case NarutoStructGen.MOSS -> d(Blocks.MOSS_BLOCK);
            default -> d(Blocks.STONE);
        };
    }

    private static BlockState leaves(Block block) {
        BlockState s = d(block);
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
    }

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    // A fence or bars joined to its neighbours (the build doesn't update shapes, so they are set here).
    private static BlockState cross(BlockState s, NarutoStructGen.Vox w, int x, int y, int z) {
        for (Direction dir : SIDES) {
            int t = w.get(x + dir.getStepX(), y, z + dir.getStepZ());
            boolean join = t == NarutoStructGen.FENCE || t == NarutoStructGen.BARS || (t != NarutoStructGen.AIR && solid(t));
            s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), join);
        }
        return s;
    }

    // The bell hangs from the side of what holds it (the middle stump's bells), or stands on the ground.
    private static BlockState bell(NarutoStructGen.Vox w, int x, int y, int z) {
        for (Direction dir : SIDES) {
            if (solid(w.get(x + dir.getStepX(), y, z + dir.getStepZ()))) {
                return d(Blocks.BELL).setValue(BellBlock.FACING, dir).setValue(BellBlock.ATTACHMENT, BellAttachType.SINGLE_WALL);
            }
        }
        return d(Blocks.BELL);
    }

    // The paper seal faces away from the block it is stuck on.
    private static BlockState seal(NarutoStructGen.Vox w, int x, int y, int z) {
        BlockState s = d(ModBlocks.PAPER_SEAL.get());
        for (Direction dir : SIDES) {
            if (solid(w.get(x - dir.getStepX(), y, z - dir.getStepZ()))) {
                return s.setValue(BlockStateProperties.FACING, dir);
            }
        }
        return s.setValue(BlockStateProperties.FACING, solid(w.get(x, y + 1, z)) && !solid(w.get(x, y - 1, z)) ? Direction.DOWN : Direction.UP);
    }

    private static BlockState vine(NarutoStructGen.Vox w, int x, int y, int z) {
        BlockState s = d(Blocks.VINE);
        boolean any = false;
        for (Direction dir : SIDES) {
            boolean on = solid(w.get(x + dir.getStepX(), y, z + dir.getStepZ()));
            s = s.setValue(VineBlock.getPropertyForFace(dir), on);
            any |= on;
        }
        return any ? s : null;
    }

    // ---------------------------------------------------------------- the Valley of the End: the seals on the statues

    private static void valleys(ServerLevel level, Data data, ServerPlayer player) {
        int px = player.getBlockX(), pz = player.getBlockZ();
        for (ValeGen.Frame F : EndValleyFeature.framesNear(level, px - 200, pz - 200, px + 200, pz + 200)) {
            if (F.statues.size() < 2) {
                continue;
            }
            ValeGen.Statue a = F.statues.get(0), b = F.statues.get(1);
            int mx = (int) Math.round((a.ox() + b.ox()) / 2), mz = (int) Math.round((a.oz() + b.oz()) / 2);
            String key = "vale:" + mx + "," + mz;
            if (data.cells.contains(key)) {
                continue;
            }
            double dx = player.getX() - mx, dz = player.getZ() - mz;
            if (dx * dx + dz * dz > 140 * 140) {
                continue;
            }
            data.cells.add(key);
            data.setDirty();
            Site site = new Site(VALE, mx, F.base, mz, 0, F.seed, new LinkedHashMap<>());
            MapPlaces.startAt(level, place(level, site));
        }
    }

    // On each head, a stone seal with the clan's crest, facing the other statue: the Uchiha fan on Madara, the Senju
    // crest on Hashirama; white roots on the shores, where the Zetsu come out of the ground at night.
    private static Build valeBuild(ServerLevel level, Site s) {
        Build b = new Build(s.x(), 0, s.z(), s.seed());
        Map<String, List<BlockPos>> marks = new LinkedHashMap<>();
        List<ValeGen.Frame> frames = EndValleyFeature.framesNear(level, s.x() - 8, s.z() - 8, s.x() + 8, s.z() + 8);
        ValeGen.Frame F = null;
        for (ValeGen.Frame f : frames) {
            if (f.statues.size() >= 2 && Math.abs((f.statues.get(0).ox() + f.statues.get(1).ox()) / 2 - s.x()) < 2
                    && Math.abs((f.statues.get(0).oz() + f.statues.get(1).oz()) / 2 - s.z()) < 2) {
                F = f;
            }
        }
        if (F != null) {
            for (int i = 0; i < 2; i++) {
                ValeGen.Statue st = F.statues.get(i), other = F.statues.get(1 - i);
                int cx = (int) Math.floor(st.ox()), cz = (int) Math.floor(st.oz());
                BlockPos top = null;
                for (int x = cx - 6; x <= cx + 6; x++) {
                    for (int z = cz - 6; z <= cz + 6; z++) {
                        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                        if (top == null || y > top.getY()) {
                            top = new BlockPos(x, y, z);
                        }
                    }
                }
                if (top == null) {
                    continue;
                }
                double ddx = other.ox() - st.ox(), ddz = other.oz() - st.oz();
                int fx = Math.abs(ddx) > Math.abs(ddz) ? (int) Math.signum(ddx) : 0, fz = fx != 0 ? 0 : (int) Math.signum(ddz);
                int sx = -fz, sz = fx;
                int y = top.getY() + 1;
                for (int u = -2; u <= 2; u++) {
                    for (int v = -2; v <= 2; v++) {
                        b.puts.add(new Build.Put(top.offset(u, 1, v), d(Blocks.STONE_SLAB)));
                    }
                }
                // the standing slab behind and the crest in front of it
                boolean uchiha = st.who() == 1;
                String[] pat = uchiha ? new String[] {".RRR.", "RRRRR", "WWWWW", ".WWW.", "..D.."}
                        : new String[] {".DDD.", "D...D", "DDDDD", "D...D", ".DDD."};
                for (int w = -2; w <= 2; w++) {
                    for (int yy = 1; yy <= 5; yy++) {
                        b.puts.add(new Build.Put(new BlockPos(top.getX() + sx * w - fx, y + yy, top.getZ() + sz * w - fz),
                                d(ModBlocks.STATUE_STONE.get())));
                    }
                }
                for (int gy = 0; gy < 5; gy++) {
                    for (int gx = 0; gx < 5; gx++) {
                        char c = pat[gy].charAt(gx);
                        if (c == '.') {
                            continue;
                        }
                        b.puts.add(new Build.Put(new BlockPos(top.getX() + sx * (gx - 2), y + 5 - gy, top.getZ() + sz * (gx - 2)),
                                d(c == 'R' ? Blocks.RED_CONCRETE : c == 'W' ? Blocks.WHITE_CONCRETE : Blocks.BLACK_CONCRETE)));
                    }
                }
                marks.computeIfAbsent(uchiha ? "uchiha" : "senju", k -> new ArrayList<>()).add(new BlockPos(top.getX(), y + 1, top.getZ()));
            }
            // the white roots, on both shores between the statues
            RandomSource r = RandomSource.create(s.seed());
            ValeGen.Statue a = F.statues.get(0), c = F.statues.get(1);
            double ax = c.ox() - a.ox(), az = c.oz() - a.oz(), len = Math.max(1, Math.sqrt(ax * ax + az * az));
            double px = -az / len, pz = ax / len;
            for (int k = 0; k < 4; k++) {
                double side = k % 2 == 0 ? 1 : -1, along = (r.nextDouble() - 0.5) * 0.5;
                int rx = (int) Math.round(s.x() + ax * along + px * side * (22 + r.nextInt(10)));
                int rz = (int) Math.round(s.z() + az * along + pz * side * (22 + r.nextInt(10)));
                int gy = level.getHeight(Heightmap.Types.OCEAN_FLOOR, rx, rz);
                for (int i = 0; i < 6; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, l = 3 + r.nextDouble() * 4, rise = 0.5 + r.nextDouble() * 3.5;
                    for (double q = 0; q <= 1; q += 0.12) {
                        int x = (int) Math.round(rx + Math.cos(ang) * l * q), z = (int) Math.round(rz + Math.sin(ang) * l * q);
                        int yy = gy + (int) Math.round(rise * q);
                        b.puts.add(new Build.Put(new BlockPos(x, yy, z), d(Blocks.MUSHROOM_STEM)));
                        if (q < 0.4) {
                            b.puts.add(new Build.Put(new BlockPos(x, yy + 1, z), d(Blocks.MUSHROOM_STEM)));
                        }
                    }
                }
                marks.computeIfAbsent("roots", k2 -> new ArrayList<>()).add(new BlockPos(rx, gy + 1, rz));
            }
        }
        Site site = new Site(VALE, s.x(), s.y(), s.z(), 0, s.seed(), marks);
        data(level).sites.put(site.id(), site);
        data(level).setDirty();
        return b;
    }

    // ---------------------------------------------------------------- what is known

    public static List<Site> known(ServerLevel level) {
        return new ArrayList<>(data(level).sites.values());
    }

    // The nearest built place of a kind within a radius (horizontal), or null.
    public static Site near(ServerLevel level, String kind, BlockPos from, double radius) {
        Site best = null;
        double bestD = radius * radius;
        for (Site site : data(level).sites.values()) {
            if (kind != null && !site.kind().equals(kind)) {
                continue;
            }
            double dx = site.x() - from.getX(), dz = site.z() - from.getZ();
            if (dx * dx + dz * dz < bestD) {
                bestD = dx * dx + dz * dz;
                best = site;
            }
        }
        return best;
    }

    // The nearest spot of a kind, built or not, out to some cells away (for a command or a map).
    public static BlockPos locate(ServerLevel level, String kind, BlockPos from, int cells) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int pcx = Math.floorDiv(from.getX(), CELL), pcz = Math.floorDiv(from.getZ(), CELL);
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

    private static Data data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                "battlegroundsmod_naruto_sites");
    }

    public static final class Data extends SavedData {
        final java.util.Set<String> cells = new java.util.HashSet<>();
        final Map<String, Site> sites = new LinkedHashMap<>();
        boolean restored;

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            ListTag cells = tag.getList("cells", Tag.TAG_STRING);
            for (int i = 0; i < cells.size(); i++) {
                data.cells.add(cells.getString(i));
            }
            ListTag sites = tag.getList("sites", Tag.TAG_COMPOUND);
            for (int i = 0; i < sites.size(); i++) {
                CompoundTag s = sites.getCompound(i);
                Map<String, List<BlockPos>> marks = new LinkedHashMap<>();
                CompoundTag m = s.getCompound("marks");
                for (String name : m.getAllKeys()) {
                    int[] flat = m.getIntArray(name);
                    List<BlockPos> list = new ArrayList<>();
                    for (int j = 0; j + 2 < flat.length; j += 3) {
                        list.add(new BlockPos(flat[j], flat[j + 1], flat[j + 2]));
                    }
                    marks.put(name, list);
                }
                Site site = new Site(s.getString("kind"), s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getInt("rot"), s.getInt("seed"), marks);
                data.sites.put(site.id(), site);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag cells = new ListTag();
            for (String c : this.cells) {
                cells.add(net.minecraft.nbt.StringTag.valueOf(c));
            }
            tag.put("cells", cells);
            ListTag sites = new ListTag();
            for (Site site : this.sites.values()) {
                CompoundTag s = new CompoundTag();
                s.putString("kind", site.kind());
                s.putInt("x", site.x());
                s.putInt("y", site.y());
                s.putInt("z", site.z());
                s.putInt("rot", site.rot());
                s.putInt("seed", site.seed());
                CompoundTag m = new CompoundTag();
                site.marks().forEach((name, list) -> {
                    int[] flat = new int[list.size() * 3];
                    for (int j = 0; j < list.size(); j++) {
                        flat[j * 3] = list.get(j).getX();
                        flat[j * 3 + 1] = list.get(j).getY();
                        flat[j * 3 + 2] = list.get(j).getZ();
                    }
                    m.put(name, new IntArrayTag(flat));
                });
                s.put("marks", m);
                sites.add(s);
            }
            tag.put("sites", sites);
            return tag;
        }
    }
}
