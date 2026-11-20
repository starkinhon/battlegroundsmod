// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.animal.db.Bora;
import com.starkinhon.battlegroundsmod.universe.DbActivities;
import com.starkinhon.battlegroundsmod.universe.KaiohTraining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The fixed NPCs never twice (Stark 2026-11-20: three Senhor Kaioh on the planet, a Kogane more at each return to the
// Colônia). The cause: the places checked "is he here?" right after the player arrived, before the chunk's entities had
// come from the disk, found nobody and made another one. Now:
// - settled(): a place only looks for its NPC once every chunk around it has its entities in memory (later() waits for
//   that, for the calls made once, like the arrival point's);
// - spawn(): each home's NPC is written down per world (its UUID), and a copy of an older one that comes back from the
//   disk is dropped (the home already has a newer one);
// - a fixed NPC that comes from the disk next to another of its kind (the copies already in the worlds) is dropped,
//   one stays. Bubbles and the Mestre Kame of the sparring left over from a fight that is no longer on go too.
// Only what comes from the disk is checked: a spawn egg still makes as many as you want.
public final class NpcGuard {
    private NpcGuard() {
    }

    private static final String HOME = "souls_npc_home";
    // two of the same fixed NPC this close are one too many (homes of the same NPC are hundreds of blocks apart)
    private static final double SAME = 48;
    private static final int WAIT_TICKS = 20 * 60;

    private record Later(ResourceKey<Level> dim, BlockPos at, int r, Consumer<ServerLevel> task, int until) {
    }

    private static final List<Later> LATER = new ArrayList<>();
    private static final List<Entity> CHECK = new ArrayList<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NpcGuard::onJoin);
        NeoForge.EVENT_BUS.addListener(NpcGuard::onTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            LATER.clear();
            CHECK.clear();
        });
    }

    // Whether every chunk within r blocks of (x, z) has its entities in memory: only then "nobody here" is true.
    public static boolean settled(ServerLevel level, double x, double z, int r) {
        int x0 = Mth.floor(x - r) >> 4, x1 = Mth.floor(x + r) >> 4;
        int z0 = Mth.floor(z - r) >> 4, z1 = Mth.floor(z + r) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    // Runs the task now if the place is settled, or as soon as it is (given up after a minute: the place's own check
    // runs again the next time someone comes).
    public static void later(ServerLevel level, BlockPos at, int r, Consumer<ServerLevel> task) {
        if (settled(level, at.getX(), at.getZ(), r)) {
            task.accept(level);
            return;
        }
        LATER.add(new Later(level.dimension(), at.immutable(), r, task, level.getServer().getTickCount() + WAIT_TICKS));
    }

    // A home's NPC: spawned and written down as that home's one.
    public static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, BlockPos at, String home) {
        T e = type.spawn(level, at, MobSpawnType.EVENT);
        if (e != null) {
            e.getPersistentData().putString(HOME, home);
            data(level).put(home, e.getUUID());
        }
        return e;
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        Entity e = event.getEntity();
        if (e instanceof UniverseNpc || e instanceof Bora || DbActivities.isSparKame(e)) {
            // checked on the next tick, when the whole chunk is in (nothing is removed while it loads)
            CHECK.add(e);
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!LATER.isEmpty()) {
            int now = server.getTickCount();
            List<Later> ready = new ArrayList<>();
            for (Iterator<Later> it = LATER.iterator(); it.hasNext();) {
                Later l = it.next();
                ServerLevel level = server.getLevel(l.dim());
                if (level == null || now > l.until()) {
                    it.remove();
                } else if (settled(level, l.at().getX(), l.at().getZ(), l.r())) {
                    it.remove();
                    ready.add(l);
                }
            }
            for (Later l : ready) {
                ServerLevel level = server.getLevel(l.dim());
                if (level != null) {
                    l.task().accept(level);
                }
            }
        }
        if (CHECK.isEmpty()) {
            return;
        }
        List<Entity> batch = new ArrayList<>(CHECK);
        CHECK.clear();
        for (Entity e : batch) {
            if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                continue;
            }
            check(level, e);
        }
    }

    private static void check(ServerLevel level, Entity e) {
        if (e.getType() == ModEntities.NPC_BUBBLES.get()) {
            if (!KaiohTraining.isChaseBubbles(e)) {
                drop(e, "Bubbles sem perseguição");
            }
            return;
        }
        if (DbActivities.isSparKame(e)) {
            if (!DbActivities.isLiveSpar(e)) {
                drop(e, "Mestre Kame de treino sem luta");
            }
            return;
        }
        boolean owner = isOwner(level, e);
        String home = e.getPersistentData().getString(HOME);
        if (!home.isEmpty() && !owner && data(level).homes.containsKey(home)) {
            // an older one of a home that has a newer NPC
            drop(e, "cópia antiga de " + home);
            return;
        }
        List<? extends Entity> others = level.getEntities(e.getType(), e.getBoundingBox().inflate(SAME, 64, SAME),
                o -> o != e && o.isAlive());
        if (others.isEmpty()) {
            return;
        }
        if (!owner) {
            drop(e, "repetido");
            return;
        }
        // this one is its home's own: the copies around it go (another home's own stays)
        for (Entity o : others) {
            if (!isOwner(level, o)) {
                drop(o, "repetido");
            }
        }
    }

    private static boolean isOwner(ServerLevel level, Entity e) {
        String home = e.getPersistentData().getString(HOME);
        return !home.isEmpty() && e.getUUID().equals(data(level).homes.get(home));
    }

    private static void drop(Entity e, String why) {
        BattlegroundsMod.LOGGER.info("[npc] {} removido em {} ({})", e.getType().getDescription().getString(), e.blockPosition(), why);
        e.discard();
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "battlegroundsmod_npc_homes");
    }

    // Per world: each home's NPC (its UUID).
    public static final class Data extends SavedData {
        final Map<String, UUID> homes = new HashMap<>();

        void put(String home, UUID id) {
            if (!id.equals(homes.put(home, id))) {
                setDirty();
            }
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            CompoundTag homes = tag.getCompound("homes");
            for (String home : homes.getAllKeys()) {
                if (homes.hasUUID(home)) {
                    data.homes.put(home, homes.getUUID(home));
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag homes = new CompoundTag();
            this.homes.forEach(homes::putUUID);
            tag.put("homes", homes);
            return tag;
        }
    }
}
