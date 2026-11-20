// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The important places (Kame House, Korin's tower, Kami's lookout, Goku's house, Capsule Corp, the Naruto villages,
// Namek...) put back the blocks that fights and techniques broke, so a quest never gets stuck on a broken building
// (Stark 2026-11-20: "regenerarem os blocos com o tempo ... qnd o player tiver longe").
//
// How:
// - The snapshot is the place's own Build: it is made the same way every time from the map / the world seed (the same
//   thing MapPlaces.lootOld and /soul bau rely on), so nothing extra is saved per block.
// - A place is marked "touched" when a player comes within WATCH blocks of it (only then can anything break it).
// - Once nobody has been within NEAR blocks of it for QUIET ticks, and its chunks are loaded (someone coming back
//   from far away, or still around but far), its blocks are compared with the build a few thousand per tick, and only
//   the ones that differ are put back. A player coming near stops it; it starts again later.
// - What is put back: the building's blocks (compared by block, so open doors, lit lamps, leaves and the like are
//   never reset). Air only where the fights leave debris (fire, lava, water, sand, gravel, magma, ice...), so
//   whatever a player builds inside stays. A block holding things (a chest, a barrel, a sign: BlockRules.keeps) is
//   never replaced, so no one's items are lost. The ground poured under a place (Build.skirt) and the terrain cut
//   above it (column clears) are terrain, not building: left alone.
// - The Naruto activities' own blocks (boulders, seals, bells, walls opened by a quest: NarutoSites marks) are left
//   as the quest left them.
// - /gamerule soulsRegenEstruturas false turns it off.
public final class StructureRegen {
    public static final GameRules.Key<GameRules.BooleanValue> RULE =
            GameRules.register("soulsRegenEstruturas", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    private static final int CHECK_EVERY = 100;
    // ticks with nobody near before the blocks come back (3 minutes)
    private static final long QUIET = 3600;
    // added to the place's own radius: nobody within NEAR (the blocks never pop back in front of anyone), and a player
    // within WATCH may have broken something
    private static final int NEAR = 64;
    private static final int WATCH = 128;
    private static final int DEFAULT_RADIUS = 48;
    private static final int BUDGET = 3000;
    private static final long TIME_BUDGET_NS = 2_000_000L;
    // after a stop (someone came near, a chunk unloaded), wait this long before trying again
    private static final long RETRY = 600;

    private static final class Job {
        final ResourceKey<Level> level;
        final String id, key;
        final int x, z, radius;
        final long[] pos;
        final BlockState[] state;
        int next, changed;

        Job(ResourceKey<Level> level, String id, String key, int x, int z, int radius, long[] pos, BlockState[] state) {
            this.level = level;
            this.id = id;
            this.key = key;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.pos = pos;
            this.state = state;
        }
    }

    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED = new HashSet<>();
    private static final Map<String, Integer> RADIUS = new HashMap<>();
    private static final Map<String, Long> RETRY_AT = new HashMap<>();

    private StructureRegen() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StructureRegen::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            QUEUE.clear();
            QUEUED.clear();
            RADIUS.clear();
            RETRY_AT.clear();
        });
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!server.getGameRules().getBoolean(RULE)) {
            QUEUE.clear();
            QUEUED.clear();
            return;
        }
        restoreSome(server);
        if (server.getTickCount() % CHECK_EVERY == 61) {
            scan(server);
        }
    }

    private static String key(ServerLevel level, String id) {
        return level.dimension().location() + "/" + id;
    }

    // The nearest player to (x, z), horizontally, squared (spectators count too: they would see the blocks pop back).
    private static double nearest(ServerLevel level, int x, int z) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x, dz = player.getZ() - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private static void scan(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == TimeChamber.LEVEL) {
                // the room of Spirit and Time is put back when someone comes in and nobody is there (TimeChamber)
                if (!level.players().isEmpty()) {
                    data(level).touch(TimeChamber.REGEN_ID);
                }
                continue;
            }
            List<MapPlaces.Place> places = MapPlaces.placesOf(level);
            if (places.isEmpty()) {
                continue;
            }
            Data data = data(level);
            long now = level.getGameTime();
            boolean anyone = !level.players().isEmpty();
            for (MapPlaces.Place place : places) {
                if (!data.touched.contains(place.id()) && !anyone) {
                    continue;
                }
                if (MapPlaces.noRegen(place.id()) || !MapPlaces.built(level, place.id())) {
                    continue;
                }
                String key = key(level, place.id());
                int r = RADIUS.getOrDefault(key, DEFAULT_RADIUS);
                double d2 = nearest(level, place.x(), place.z());
                double watch = r + WATCH, near = r + NEAR;
                if (d2 < watch * watch) {
                    data.touch(place.id());
                }
                if (d2 < near * near) {
                    data.seen.put(place.id(), now);
                    data.setDirty();
                    continue;
                }
                if (!data.touched.contains(place.id()) || QUEUED.contains(key)
                        || now - data.seen.getOrDefault(place.id(), 0L) < QUIET || now < RETRY_AT.getOrDefault(key, 0L)
                        || !level.hasChunkAt(new BlockPos(place.x(), level.getSeaLevel(), place.z()))) {
                    continue;
                }
                start(level, place, key);
            }
        }
    }

    private static void start(ServerLevel level, MapPlaces.Place place, String key) {
        Build build;
        try {
            build = MapPlaces.buildOf(level, place);
        } catch (RuntimeException e) {
            BattlegroundsMod.LOGGER.warn("Regeneração: não deu pra refazer a planta de {}", place.id(), e);
            RETRY_AT.put(key, level.getGameTime() + RETRY * 10);
            return;
        }
        // Namek's buildings sit on the ground found when they were built (NamekWorld.groundAt): made again later, the
        // ground under them is the building itself. Their floor is kept when built, and the plan moved onto it; one
        // built before that was kept is left alone (it would come back at the wrong height).
        Integer floor = data(level).floors.get(place.id());
        int dy = floor != null ? floor - build.y : 0;
        if (floor == null && level.dimension() == NamekWorld.LEVEL && !NamekWorld.PAD_PLACE.equals(place.id())) {
            RETRY_AT.put(key, Long.MAX_VALUE);
            return;
        }
        LongSet keep = NarutoSites.questSpots(level, place.id());
        LongArrayList pos = new LongArrayList();
        List<BlockState> state = new ArrayList<>();
        int radius = plan(build, dy, place.x(), place.z(), keep, pos, state);
        RADIUS.put(key, radius);
        Job job = new Job(level.dimension(), place.id(), key, place.x(), place.z(), radius, pos.toLongArray(),
                state.toArray(new BlockState[0]));
        QUEUE.add(job);
        QUEUED.add(key);
    }

    // The building's final blocks (the last put on a spot wins, as when it was built), without the ground skirt, the
    // column clears and the spots in keep. Returns the farthest horizontal reach from (cx, cz).
    private static int plan(Build build, int dy, int cx, int cz, LongSet keep, LongArrayList pos, List<BlockState> state) {
        LongOpenHashSet seen = new LongOpenHashSet(build.puts.size());
        double far = 0;
        for (int i = build.puts.size() - 1; i >= 0; i--) {
            Build.Put put = build.puts.get(i);
            if (put.column()) {
                continue;
            }
            long at = dy == 0 ? put.pos().asLong() : BlockPos.asLong(put.pos().getX(), put.pos().getY() + dy, put.pos().getZ());
            if (!seen.add(at) || put.fill() || keep.contains(at)) {
                continue;
            }
            pos.add(at);
            state.add(put.state());
            double dx = put.pos().getX() - cx, dz = put.pos().getZ() - cz;
            far = Math.max(far, dx * dx + dz * dz);
        }
        return (int) Math.ceil(Math.sqrt(far));
    }

    private static void restoreSome(MinecraftServer server) {
        long start = System.nanoTime();
        int done = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (!QUEUE.isEmpty() && done < BUDGET && System.nanoTime() - start < TIME_BUDGET_NS) {
            Job job = QUEUE.peek();
            ServerLevel level = server.getLevel(job.level);
            if (level == null) {
                QUEUE.poll();
                QUEUED.remove(job.key);
                continue;
            }
            double near = job.radius + NEAR;
            if (nearest(level, job.x, job.z) < near * near) {
                stop(level, job);
                continue;
            }
            long lastChunk = Long.MIN_VALUE;
            boolean stopped = false;
            while (job.next < job.pos.length && done < BUDGET) {
                p.set(job.pos[job.next]);
                long chunk = (long) (p.getX() >> 4) << 32 | ((p.getZ() >> 4) & 0xFFFFFFFFL);
                if (chunk != lastChunk) {
                    // never load a chunk for this: it waits for the chunks to be there
                    if (!level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
                        stop(level, job);
                        stopped = true;
                        break;
                    }
                    lastChunk = chunk;
                }
                if (restore(level, p, job.state[job.next])) {
                    job.changed++;
                }
                job.next++;
                done++;
                if ((done & 255) == 0 && System.nanoTime() - start > TIME_BUDGET_NS) {
                    break;
                }
            }
            if (stopped) {
                continue;
            }
            if (job.next >= job.pos.length) {
                QUEUE.poll();
                QUEUED.remove(job.key);
                Data data = data(level);
                if (data.touched.remove(job.id)) {
                    data.setDirty();
                }
                if (job.changed > 0) {
                    BattlegroundsMod.LOGGER.info("Regeneração: {} blocos de {} voltaram", job.changed, job.id);
                }
            }
        }
    }

    private static void stop(ServerLevel level, Job job) {
        QUEUE.remove(job);
        QUEUED.remove(job.key);
        RETRY_AT.put(job.key, level.getGameTime() + RETRY);
    }

    // Puts the building's block back at p if it was broken or swapped. Returns whether anything changed.
    static boolean restore(ServerLevel level, BlockPos p, BlockState want) {
        BlockState there = level.getBlockState(p);
        if (same(there, want)) {
            return false;
        }
        // a chest, a barrel, a sign... (someone's items) and the unbreakable blocks stay
        if (com.starkinhon.battlegroundsmod.combat.BlockRules.keeps(there) || there.getDestroySpeed(level, p) < 0) {
            return false;
        }
        // air, and the invisible lights of the rooms (NarutoSites): only over debris, never over a player's blocks
        if ((want.isAir() || want.getBlock() == Blocks.LIGHT) && !there.isAir() && !debris(there)) {
            return false;
        }
        level.setBlock(p, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return true;
    }

    private static boolean same(BlockState there, BlockState want) {
        Block a = there.getBlock(), b = want.getBlock();
        if (a == b) {
            return true;
        }
        if (want.isAir()) {
            return there.isAir();
        }
        // grass spreading on dirt, a path walked on it, water frozen in the cold: the same building
        if (there.is(BlockTags.DIRT) && want.is(BlockTags.DIRT)) {
            return true;
        }
        return b == Blocks.WATER && (there.is(BlockTags.ICE) || a == Blocks.FROSTED_ICE);
    }

    // What the fights leave where the building had air: fire, lava and water poured in, fallen sand and gravel, the
    // techniques' magma, basalt, ice and snow. A player's own blocks inside are not this.
    private static boolean debris(BlockState there) {
        Block b = there.getBlock();
        return !there.getFluidState().isEmpty() && there.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                || b instanceof BaseFireBlock || b instanceof FallingBlock
                || b == Blocks.MAGMA_BLOCK || b == Blocks.BASALT || b == Blocks.BLACKSTONE || b == Blocks.OBSIDIAN
                || b == Blocks.COARSE_DIRT || b == Blocks.ROOTED_DIRT || b == Blocks.COBWEB
                || there.is(BlockTags.ICE) || b == Blocks.FROSTED_ICE || b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW
                || b == Blocks.SNOW;
    }

    // A place built by itself (the room of Spirit and Time): when someone comes in and nobody is inside, its blocks are
    // put back at once if it was touched. The chunks load for the player coming in anyway.
    static void restoreNow(ServerLevel level, String id, java.util.function.Supplier<Build> plan) {
        if (!level.getServer().getGameRules().getBoolean(RULE) || !level.players().isEmpty()) {
            return;
        }
        Data data = data(level);
        if (!data.touched.contains(id)) {
            return;
        }
        Build build = plan.get();
        LongArrayList pos = new LongArrayList();
        List<BlockState> state = new ArrayList<>();
        plan(build, 0, build.x, build.z, LongSets.EMPTY_SET, pos, state);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int i = 0; i < pos.size(); i++) {
            if (restore(level, p.set(pos.getLong(i)), state.get(i))) {
                changed++;
            }
        }
        data.touched.remove(id);
        data.setDirty();
        if (changed > 0) {
            BattlegroundsMod.LOGGER.info("Regeneração: {} blocos de {} voltaram", changed, id);
        }
    }

    // A place just finished building (MapPlaces): its floor is kept for the plans made later.
    static void built(ServerLevel level, String id, Build build) {
        Data data = data(level);
        Integer old = data.floors.put(id, build.y);
        if (old == null || old != build.y) {
            data.setDirty();
        }
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                "battlegroundsmod_regen");
    }

    // Per world: the places a player came near since their last check, and when someone was last near each.
    public static final class Data extends SavedData {
        final Set<String> touched = new HashSet<>();
        final Map<String, Long> seen = new HashMap<>();
        // the floor (Build.y) each place was built at
        final Map<String, Integer> floors = new HashMap<>();

        void touch(String id) {
            if (touched.add(id)) {
                setDirty();
            }
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            ListTag list = tag.getList("touched", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.touched.add(list.getString(i));
            }
            CompoundTag seen = tag.getCompound("seen");
            for (String id : seen.getAllKeys()) {
                data.seen.put(id, seen.getLong(id));
            }
            CompoundTag floors = tag.getCompound("floors");
            for (String id : floors.getAllKeys()) {
                data.floors.put(id, floors.getInt(id));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (String id : touched) {
                list.add(StringTag.valueOf(id));
            }
            tag.put("touched", list);
            CompoundTag seen = new CompoundTag();
            for (Map.Entry<String, Long> e : this.seen.entrySet()) {
                seen.putLong(e.getKey(), e.getValue());
            }
            tag.put("seen", seen);
            CompoundTag floors = new CompoundTag();
            for (Map.Entry<String, Integer> e : this.floors.entrySet()) {
                floors.putInt(e.getKey(), e.getValue());
            }
            tag.put("floors", floors);
            return tag;
        }
    }
}
