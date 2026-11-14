package com.starkinhon.battlegroundsmod.world;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Tells each player's client where the Colônia's barriers stand near them (ColoniaAirFx draws them fixed in the world:
// the client can't work them out, they come from the seed). Every half second, only in the biome, only when the list
// changed; the work itself on a thread of its own.
public final class ColoniaDomeSync {
    private static final Map<UUID, int[]> SENT = new HashMap<>();
    // The first look at a region works the colonies out from the terrain and the biomes (a few seconds: in the 4th test
    // on the PC it held the server 5 s on joining in the biome), so it runs on its own thread and the answer is sent
    // when it is ready; after that the generator's cache answers at once.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Souls Colonia domes");
        t.setDaemon(true);
        return t;
    });
    private static final Map<UUID, Boolean> BUSY = new ConcurrentHashMap<>();
    private static final Map<UUID, int[]> READY = new ConcurrentHashMap<>();

    private ColoniaDomeSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ColoniaDomeSync::onTick);
        NeoForge.EVENT_BUS.addListener(ColoniaDomeSync::onLogout);
        NeoForge.EVENT_BUS.addListener(ColoniaDomeSync::onChangeDimension);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 7) {
            return;
        }
        UUID id = player.getUUID();
        int[] flat = new int[0];
        if (player.serverLevel().getBiome(player.blockPosition()).is(ModWorldgen.COLONIA_DO_JOGO_DO_ABATE)) {
            int[] ready = READY.remove(id);
            if (ready == null) {
                if (BUSY.putIfAbsent(id, Boolean.TRUE) == null) {
                    net.minecraft.server.level.ServerLevel level = player.serverLevel();
                    int x = player.getBlockX(), z = player.getBlockZ();
                    WORKER.execute(() -> {
                        try {
                            List<int[]> domes = ColoniaFeature.domes(level, x, z);
                            int[] f = new int[domes.size() * 4];
                            for (int i = 0; i < domes.size(); i++) {
                                System.arraycopy(domes.get(i), 0, f, i * 4, 4);
                            }
                            READY.put(id, f);
                        } catch (RuntimeException e) {
                            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Could not work out the Colonia's barriers", e);
                        } finally {
                            BUSY.remove(id);
                        }
                    });
                }
                return;
            }
            flat = ready;
        } else if (!SENT.containsKey(id)) {
            return;
        }
        int[] last = SENT.get(player.getUUID());
        if (last != null && Arrays.equals(last, flat)) {
            return;
        }
        // (outside the biome the client keeps what it has until it is far: an empty list is only sent once it left)
        if (flat.length == 0 && last != null && last.length > 0 && near(player, last)) {
            return;
        }
        SENT.put(player.getUUID(), flat);
        PacketDistributor.sendToPlayer(player, new ModPayloads.ColoniaDomes(flat));
    }

    private static boolean near(ServerPlayer player, int[] flat) {
        for (int i = 0; i + 3 < flat.length; i += 4) {
            double dx = player.getX() - flat[i], dz = player.getZ() - flat[i + 1], r = flat[i + 3] * 1.6 + 64;
            if (dx * dx + dz * dz < r * r) {
                return true;
            }
        }
        return false;
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        READY.remove(event.getEntity().getUUID());
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SENT.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new ModPayloads.ColoniaDomes(new int[0]));
        }
    }
}
