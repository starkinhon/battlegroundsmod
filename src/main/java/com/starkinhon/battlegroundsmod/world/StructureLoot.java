package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.command.SoulCommand;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

// Loot in every structure (Stark 2026-11-12: "pode ter loot em todas as estruturas, baseado nas estruturas", mod items
// and useful Minecraft things, ender pearls since the Souls world has no normal mobs, gunpowder in the deserts). The
// tables are loot_table/estruturas/<kind>.json (written by tools/loot/gen_loot.py). When a place finishes building
// (MapPlaces), its chests and barrels get a table: the chest nearest the middle the structure's own, the other chests
// "comum", the barrels "suprimentos" (often half empty); with no chest, the first barrel is the main one. Buildings
// without any container get one chest in a good spot (EXTRA, added by Build.template), picked offline from the
// structure files: inside, on a solid floor, against one wall, room above to open it.
public final class StructureLoot {
    private StructureLoot() {
    }

    private record Spot(int dx, int dy, int dz, Direction facing) {
    }

    // template -> a chest in it (offsets from the template's origin, as Build.template places it)
    private static final Map<String, Spot> EXTRA = Map.of(
            // the bottom room, against the wall across from the ladder (the last guardian's floor)
            "dragonball/babidi_ship", new Spot(-5, -66, -1, Direction.EAST),
            // the crashed ship's cabin, by the beds
            "dragonball/capsule_ship_wreck", new Spot(-6, 1, -2, Direction.EAST),
            // at the foot of the north-west pillar, facing the ring
            "dragonball/cell_arena", new Spot(-25, 1, -26, Direction.EAST),
            // Korin's room under the balcony, against the east wall
            "dragonball/korin_tower", new Spot(12, 154, 0, Direction.WEST),
            // Kami's palace, the west room
            "dragonball/kami_lookout", new Spot(-20, 1, -30, Direction.EAST),
            // Namek: Freeza's ship by the healing tanks, Guru's room, the elder's house in the village
            "dragonball/freeza_ship", new Spot(-12, 5, 6, Direction.EAST),
            "dragonball/guru_house", new Spot(-4, 1, -4, Direction.WEST),
            "dragonball/namek_village", new Spot(-4, 1, -2, Direction.EAST));

    // Called at the end of Build.template.
    static void extraChest(Build b, String template, int dx, int dy, int dz) {
        Spot spot = EXTRA.get(template);
        if (spot != null) {
            b.set(dx + spot.dx(), dy + spot.dy(), dz + spot.dz(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, spot.facing()));
        }
    }

    // A place's kind from its id: "mundo/capsula@x,z", "naruto/folha@x,z", "kame" (the old drawn map), "nave_freeza"...
    static String kind(String map, String id) {
        String k = id;
        int slash = k.indexOf('/');
        if (slash >= 0) {
            k = k.substring(slash + 1);
        }
        int at = k.indexOf('@');
        if (at >= 0) {
            k = k.substring(0, at);
        }
        if (DragonBallPlaces.MAP.equals(map)) {
            // on the old drawn map "babidi" is the crashed ship and "babidi_nave" Babidi's ship
            k = switch (k) {
                case "babidi" -> "nave";
                case "babidi_nave" -> "babidi";
                default -> k;
            };
        }
        return k;
    }

    private static final Set<String> TABLES = Set.of("capsula", "goku", "karin", "cell", "nave", "babidi", "buu", "kame",
            "lookout", "sala_tempo", "nave_freeza", "nave_capsula", "vila", "guru", "folha", "morte", "covil", "deserto",
            "nevoa", "chuva", "myoboku", "pico");
    private static final Set<String> DESERT = Set.of("nave", "babidi", "deserto", "raditz");

    private static ResourceKey<LootTable> table(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "estruturas/" + name));
    }

    // Whether a place of this kind has loot at all (the pads, the Valley of the End and Raditz's pod do not here).
    public static boolean has(String kind) {
        return TABLES.contains(kind);
    }

    // The containers a build puts down (the last put on a spot wins), nearest the middle first.
    private static List<Map.Entry<BlockPos, BlockState>> containers(Build b) {
        Map<BlockPos, BlockState> at = new LinkedHashMap<>();
        for (Build.Put put : b.puts) {
            if (put.column()) {
                continue;
            }
            Block block = put.state().getBlock();
            if (block instanceof ChestBlock || block instanceof BarrelBlock) {
                at.put(put.pos(), put.state());
            } else {
                at.remove(put.pos());
            }
        }
        List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(at.entrySet());
        BlockPos middle = new BlockPos(b.x, b.y, b.z);
        list.sort(Comparator.comparingDouble(e -> e.getKey().distSqr(middle)));
        return list;
    }

    // The table of each container, in the order of containers(): the structure's own on the nearest chest (the first
    // barrel when there is no chest), "comum" on the other chests, "suprimentos" on the barrels.
    private static List<String> names(String kind, List<Map.Entry<BlockPos, BlockState>> list) {
        boolean desert = DESERT.contains(kind);
        boolean anyChest = list.stream().anyMatch(e -> e.getValue().getBlock() instanceof ChestBlock);
        boolean mainGiven = false;
        List<String> names = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : list) {
            boolean chest = e.getValue().getBlock() instanceof ChestBlock;
            if (!mainGiven && (chest || !anyChest)) {
                names.add(kind);
                mainGiven = true;
            } else if (chest) {
                names.add(desert ? "comum_deserto" : "comum");
            } else {
                names.add(desert ? "suprimentos_deserto" : "suprimentos");
            }
        }
        return names;
    }

    // Gives the place's containers their tables. retro: a place built before this existed (the player's old worlds):
    // only containers still standing and still empty get loot, and a missing one is put back only where there is air.
    public static void fill(ServerLevel level, String map, String id, Build b, boolean retro) {
        String kind = kind(map, id);
        if (!has(kind)) {
            return;
        }
        List<Map.Entry<BlockPos, BlockState>> list = containers(b);
        List<String> names = names(kind, list);
        for (int i = 0; i < list.size(); i++) {
            BlockPos pos = list.get(i).getKey();
            BlockState want = list.get(i).getValue();
            if (retro) {
                BlockState now = level.getBlockState(pos);
                if (now.isAir()) {
                    level.setBlock(pos, want, Block.UPDATE_CLIENTS);
                } else if (now.getBlock() != want.getBlock()) {
                    continue;
                }
            }
            if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity box) {
                if (retro && (box.getLootTable() != null || !box.isEmpty())) {
                    continue;
                }
                box.setLootTable(table(names.get(i)), level.random.nextLong());
                box.setChanged();
            }
        }
    }

    // One container put down by an activity (the Snake's Den nest): the structure's own table on top of what it has.
    public static void give(ServerLevel level, BlockPos pos, String kind) {
        if (has(kind) && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity box && box.getLootTable() == null) {
            box.setLootTable(table(kind), level.random.nextLong());
            box.setChanged();
        }
    }

    // /soul bau <tipo> (ops, for testing): to the nearest place of a kind (Dragon Ball or Naruto); once it is built,
    // lists its chests and barrels with their tables in the chat (and the log) and puts you beside the main one, facing it.
    static void register() {
        SoulCommand.addSubcommand(() -> Commands.literal("bau").requires(s -> s.hasPermission(2))
                .then(Commands.argument("tipo", StringArgumentType.word()).executes(c -> bau(c.getSource(),
                        StringArgumentType.getString(c, "tipo")))));
    }

    private static final Set<String> NARUTO = Set.of(NarutoStructGen.FOLHA, NarutoStructGen.MORTE, NarutoStructGen.COVIL,
            NarutoStructGen.DESERTO, NarutoStructGen.NEVOA, NarutoStructGen.CHUVA, NarutoStructGen.MYOBOKU, NarutoStructGen.PICO);

    private static int bau(CommandSourceStack source, String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        boolean naruto = NARUTO.contains(kind);
        String id;
        Build build;
        BlockPos pod = null;
        if (naruto) {
            NarutoSites.Site site = NarutoSites.near(level, kind, player.blockPosition(), 600);
            if (site == null || !MapPlaces.built(level, site.id())) {
                return go(source, player, level, site == null ? NarutoSites.locate(level, kind, player.blockPosition(), 14) : site.pos(), kind);
            }
            id = site.id();
            build = NarutoSites.buildOf(level, site);
            if (NarutoStructGen.COVIL.equals(kind) && site.first("nest") != null) {
                // the den's chest is put on the nest by its activity, not by the build
                pod = site.first("nest").above();
            }
        } else {
            WorldSites.Site site = WorldSites.near(level, kind, player.blockPosition(), 600);
            if (site == null || !MapPlaces.built(level, WorldSites.idOf(site))) {
                return go(source, player, level, site == null ? WorldSites.locate(level, kind, player.blockPosition(), 14) : site.pos(), kind);
            }
            id = WorldSites.idOf(site);
            build = WorldSites.buildOf(site);
            if (WorldSites.RADITZ.equals(kind)) {
                pod = WorldSites.podChestPos(site);
            }
        }
        List<Map.Entry<BlockPos, BlockState>> list = containers(build);
        List<String> names = names(kind(null, id), list);
        List<BlockPos> spots = new ArrayList<>();
        List<String> tables = new ArrayList<>();
        if (pod != null) {
            spots.add(pod);
            tables.add(WorldSites.RADITZ.equals(kind) ? "chests/pod_saiyajin" : "estruturas/" + kind);
        }
        for (int i = 0; i < list.size(); i++) {
            spots.add(list.get(i).getKey());
            tables.add("estruturas/" + names.get(i));
        }
        if (spots.isEmpty()) {
            source.sendFailure(Component.literal("[bau] " + id + ": nenhum baú"));
            return 0;
        }
        for (int i = 0; i < spots.size(); i++) {
            BlockPos p = spots.get(i);
            BlockState now = level.getBlockState(p);
            String state = level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity box
                    ? (box.getLootTable() != null ? "loot=" + box.getLootTable().location() : "sem loot, " + (box.isEmpty() ? "vazio" : "com itens"))
                    : "sem baú (" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(now.getBlock()) + ")";
            String line = "[bau] " + id + " #" + i + " " + p.toShortString() + " quer " + tables.get(i) + " | " + state;
            source.sendSuccess(() -> Component.literal(line), false);
            BattlegroundsMod.LOGGER.info(line);
        }
        // beside the structure's own chest (not a barrel of supplies that happens to be nearer)
        int mainAt = Math.max(0, tables.indexOf(WorldSites.RADITZ.equals(kind) ? "chests/pod_saiyajin" : "estruturas/" + kind));
        BlockPos main = spots.get(mainAt);
        BlockPos feet = null;
        for (int r = 1; r <= 3 && feet == null; r++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dy = 0; dy >= -1 && feet == null; dy--) {
                    BlockPos q = main.relative(d, r).above(dy);
                    if (level.getBlockState(q).getCollisionShape(level, q).isEmpty()
                            && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
                            && !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) {
                        feet = q;
                        break;
                    }
                }
            }
        }
        if (feet == null) {
            feet = main.above();
        }
        double ex = feet.getX() + 0.5, ey = feet.getY() + 1.62, ez = feet.getZ() + 0.5;
        double dx = main.getX() + 0.5 - ex, dy = main.getY() + 0.5 - ey, dz = main.getZ() + 0.5 - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, ex, feet.getY(), ez, yaw, pitch);
        return 1;
    }

    // Not built yet: high over it (it builds when someone comes near); run /soul bau again once it is up.
    private static int go(CommandSourceStack source, ServerPlayer player, ServerLevel level, BlockPos at, String kind) {
        if (at == null) {
            source.sendFailure(Component.literal("[bau] nenhum " + kind + " por perto"));
            return 0;
        }
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) + 30, 140);
        player.teleportTo(at.getX() + 0.5, y, at.getZ() + 40.5);
        String line = "[bau] indo pro " + kind + " em " + at.getX() + " " + at.getZ() + " (construindo; rode de novo)";
        source.sendSuccess(() -> Component.literal(line), false);
        BattlegroundsMod.LOGGER.info(line);
        return 1;
    }

    // Whether all of a build's containers are in loaded chunks (the retro waits for that).
    public static boolean loaded(ServerLevel level, Build b) {
        for (Map.Entry<BlockPos, BlockState> e : containers(b)) {
            if (!level.isLoaded(e.getKey())) {
                return false;
            }
        }
        return true;
    }
}
