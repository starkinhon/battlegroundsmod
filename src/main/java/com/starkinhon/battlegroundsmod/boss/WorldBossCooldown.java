// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// The end-game bosses that show up in the world by themselves (Freeza's ship, the Hashirama in the Valley): once one
// appears, that boss doesn't come back for 10 Minecraft days, for the whole world (Stark 2026-11-22: "o freeza spawnou
// bastante qnd eu e meu amigo chegamos no tier 0... isso serviria pra todos"). The first time is free. Kept in the
// overworld's saved data on the game clock (/time set doesn't move it), so it survives a restart and counts for every
// player of a server.
public final class WorldBossCooldown {
    public static final long WAIT = 24000L * 10;
    public static final String FREEZA = "freeza_total", HASHIRAMA = "hashirama";

    private WorldBossCooldown() {
    }

    private static Data data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                "battlegroundsmod_world_bosses");
    }

    private static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    // Ticks until this boss may appear again (0 = free).
    public static long left(ServerLevel level, String boss) {
        Long last = data(level).last.get(boss);
        return last == null ? 0 : Math.max(0, last + WAIT - now(level));
    }

    public static boolean free(ServerLevel level, String boss) {
        return left(level, boss) <= 0;
    }

    // Days left, rounded up (for the messages).
    public static int daysLeft(ServerLevel level, String boss) {
        return (int) ((left(level, boss) + 23999) / 24000);
    }

    // It appeared now: the wait starts.
    public static void used(ServerLevel level, String boss) {
        Data d = data(level);
        d.last.put(boss, now(level));
        d.setDirty();
    }

    public static final class Data extends SavedData {
        final Map<String, Long> last = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            for (String k : tag.getAllKeys()) {
                data.last.put(k, tag.getLong(k));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            last.forEach(tag::putLong);
            return tag;
        }
    }
}
