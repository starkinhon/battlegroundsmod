package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The places of a drawn universe (Kame House, the Red Ribbon tower, Capsule Corp, Namekusei...) are built by code the
// first time a player comes near, a few thousand blocks per tick so nothing freezes, and only once per world (saved).
// Pads (Pad) take whoever stands on them for two seconds to the other end (the Earth to Namekusei and back).
public final class MapPlaces {
    // A place: its center, how near a player must be to start building it, and how it is built from the map.
    public record Place(String id, int x, int z, Function<WorldMap, Build> build, BiConsumer<ServerLevel, Build> done) {
    }

    // Stand on (x, y, z) (the block under the feet) to go to "to".
    public record Pad(String id, String place, BlockPos at, String toPlace, BlockPos to, String message) {
    }

    private static final double TRIGGER = 150;
    private static final int BUDGET = 4000;
    private static final long TIME_BUDGET_NS = 15_000_000L;
    private static final int PAD_TICKS = 40;

    private static final Map<String, List<Place>> PLACES = new HashMap<>();
    private static final Map<String, List<Pad>> PADS = new HashMap<>();

    private record Job(ResourceKey<Level> level, Place place, Build build, int[] next) {
    }

    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED = new HashSet<>();
    private static final Map<UUID, Integer> ON_PAD = new HashMap<>();

    private MapPlaces() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MapPlaces::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            QUEUE.clear();
            QUEUED.clear();
            ON_PAD.clear();
        });
        DragonBallPlaces.register();
    }

    public static void add(String map, Place place) {
        PLACES.computeIfAbsent(map, k -> new ArrayList<>()).add(place);
    }

    public static void add(String map, Pad pad) {
        PADS.computeIfAbsent(map, k -> new ArrayList<>()).add(pad);
    }

    // Ground kept free of trees and plants (MapChunkGenerator skips decorating chunks there): a circle per building.
    private static final Map<String, List<int[]>> CLEAR = new HashMap<>();

    public static void keepClear(String map, int x, int z, int radius) {
        CLEAR.computeIfAbsent(map, k -> new ArrayList<>()).add(new int[] {x, z, radius});
    }

    // Whether the chunk whose middle is (x, z) touches a kept-clear circle (16 blocks of margin: a tree from a chunk
    // next to it reaches in).
    public static boolean keptClear(String map, int x, int z) {
        for (int[] c : CLEAR.getOrDefault(map, List.of())) {
            double dx = x - c[0];
            double dz = z - c[1];
            double r = c[2] + 16;
            if (dx * dx + dz * dz < r * r) {
                return true;
            }
        }
        return false;
    }

    private static String mapOf(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof MapChunkGenerator drawn ? drawn.mapName() : null;
    }

    private static WorldMap map(ServerLevel level) {
        return ((MapChunkGenerator) level.getChunkSource().getGenerator()).map();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        placeSome(server);
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            String map = mapOf(level);
            if (map == null || player.isSpectator()) {
                ON_PAD.remove(player.getUUID());
                continue;
            }
            if (server.getTickCount() % 20 == 0) {
                for (Place place : PLACES.getOrDefault(map, List.of())) {
                    double dx = player.getX() - place.x();
                    double dz = player.getZ() - place.z();
                    if (dx * dx + dz * dz < TRIGGER * TRIGGER) {
                        start(level, place, false);
                    }
                }
            }
            pads(player, level, map);
            if (DragonBallPlaces.MAP.equals(map)) {
                DragonBallPlaces.catchFalls(player);
            }
        }
    }

    // Starts building a place (or builds it right now, all at once, when someone is about to arrive there).
    public static void start(ServerLevel level, Place place, boolean now) {
        String key = level.dimension().location() + "/" + place.id();
        Data data = data(level);
        if (data.built.contains(place.id())) {
            return;
        }
        if (QUEUED.contains(key) && !now) {
            return;
        }
        Job job = null;
        if (QUEUED.contains(key)) {
            for (Job queued : QUEUE) {
                if (queued.place() == place && queued.level().equals(level.dimension())) {
                    job = queued;
                }
            }
        }
        if (job == null) {
            Build build = place.build().apply(map(level));
            job = new Job(level.dimension(), place, build, new int[] {0});
            QUEUED.add(key);
            QUEUE.add(job);
        }
        if (now) {
            while (job.next()[0] < job.build().puts.size()) {
                put(level, job.build().puts.get(job.next()[0]++));
            }
            finish(level, job);
            QUEUE.remove(job);
        }
    }

    private static void placeSome(MinecraftServer server) {
        long start = System.nanoTime();
        int done = 0;
        while (!QUEUE.isEmpty() && done < BUDGET && System.nanoTime() - start < TIME_BUDGET_NS) {
            Job job = QUEUE.peek();
            ServerLevel level = server.getLevel(job.level());
            if (level == null) {
                QUEUE.poll();
                continue;
            }
            List<Build.Put> puts = job.build().puts;
            while (job.next()[0] < puts.size() && done < BUDGET) {
                if (put(level, puts.get(job.next()[0]++))) {
                    done++;
                }
                if ((done & 255) == 0 && System.nanoTime() - start > TIME_BUDGET_NS) {
                    break;
                }
            }
            if (job.next()[0] >= puts.size()) {
                QUEUE.poll();
                finish(level, job);
            }
        }
    }

    private static boolean put(ServerLevel level, Build.Put put) {
        if (put.state().isAir() && level.getBlockState(put.pos()).isAir()) {
            return false;
        }
        level.setBlock(put.pos(), put.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return true;
    }

    private static void finish(ServerLevel level, Job job) {
        QUEUED.remove(level.dimension().location() + "/" + job.place().id());
        Data data = data(level);
        if (data.built.add(job.place().id())) {
            data.setDirty();
        }
        if (job.place().done() != null) {
            job.place().done().accept(level, job.build());
        }
    }

    public static boolean built(ServerLevel level, String id) {
        return data(level).built.contains(id);
    }

    private static Place place(String map, String id) {
        for (Place place : PLACES.getOrDefault(map, List.of())) {
            if (place.id().equals(id)) {
                return place;
            }
        }
        return null;
    }

    private static void pads(ServerPlayer player, ServerLevel level, String map) {
        Pad on = null;
        for (Pad pad : PADS.getOrDefault(map, List.of())) {
            BlockPos at = pad.at();
            if (Math.abs(player.getX() - (at.getX() + 0.5)) < 0.9 && Math.abs(player.getZ() - (at.getZ() + 0.5)) < 0.9
                    && Math.abs(player.getY() - (at.getY() + 1)) < 1.2 && built(level, pad.place())) {
                on = pad;
            }
        }
        if (on == null) {
            ON_PAD.remove(player.getUUID());
            return;
        }
        int ticks = ON_PAD.merge(player.getUUID(), 10, Integer::sum);
        Vec3 center = Vec3.atBottomCenterOf(on.at().above());
        Fx.burst(level, ParticleTypes.END_ROD, center, 10, 0.4, 0.8);
        player.displayClientMessage(Component.translatable(on.message(), Math.max(0, (PAD_TICKS - ticks) / 20 + 1)), true);
        if (ticks < PAD_TICKS) {
            return;
        }
        ON_PAD.remove(player.getUUID());
        Place target = place(map, on.toPlace());
        if (target != null) {
            start(level, target, true);
        }
        BlockPos to = on.to();
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, player.position().add(0, 1, 0), 60, 0.6, 0.6);
        player.teleportTo(level, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, player.position().add(0, 1, 0), 60, 0.6, 0.6);
        Fx.sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.4f);
        // Standing on the pad at the other end does not send them straight back.
        ON_PAD.put(player.getUUID(), -60);
    }

    // Which places of this world are already built.
    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                "battlegroundsmod_places");
    }

    public static final class Data extends SavedData {
        final Set<String> built = new HashSet<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            ListTag list = tag.getList("built", 8);
            for (int i = 0; i < list.size(); i++) {
                data.built.add(list.getString(i));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (String id : built) {
                list.add(StringTag.valueOf(id));
            }
            tag.put("built", list);
            return tag;
        }

        void forget(String id) {
            if (built.remove(id)) {
                setDirty();
            }
        }
    }

    // /soul lugar <id>: rebuild a place (ops; after changing how it is built).
    public static int rebuild(ServerLevel level, String id) {
        String map = mapOf(level);
        Place place = map == null ? null : place(map, id);
        if (place == null) {
            return 0;
        }
        data(level).forget(id);
        start(level, place, false);
        return 1;
    }

    public static List<String> ids(ServerLevel level) {
        String map = mapOf(level);
        List<String> ids = new ArrayList<>();
        for (Place place : PLACES.getOrDefault(map == null ? "" : map, List.of())) {
            ids.add(place.id());
        }
        return ids;
    }
}
