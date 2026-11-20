// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// The ground's height on the client for the heightmaps the server doesn't send (Stark's Kyoto test, 2026-11-19: leaves
// pouring out of a single tree). A client chunk has MOTION_BLOCKING_NO_LEAVES and OCEAN_FLOOR made empty in its
// constructor and only touched by later block changes, so level.getHeight with them gives the world's bottom almost
// everywhere (and a right number only where a block changed). Here the same rules as the vanilla heightmaps, read down
// from MOTION_BLOCKING (which the server does send), kept 2 s per column so a thousand leaves a frame cost little.
// Returns what level.getHeight would: the first free block over the ground.
public final class ClientHeights {
    private static final Long2LongOpenHashMap NO_LEAVES = new Long2LongOpenHashMap();
    private static final Long2LongOpenHashMap OCEAN_FLOOR = new Long2LongOpenHashMap();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static Level lastLevel;

    private ClientHeights() {
    }

    // MOTION_BLOCKING_NO_LEAVES: what stops a body or holds a fluid, leaves left out
    public static int noLeaves(Level level, int x, int z) {
        return get(level, NO_LEAVES, x, z, true);
    }

    // OCEAN_FLOOR: what stops a body (under the water)
    public static int oceanFloor(Level level, int x, int z) {
        return get(level, OCEAN_FLOOR, x, z, false);
    }

    private static int get(Level level, Long2LongOpenHashMap map, int x, int z, boolean leaves) {
        if (level != lastLevel) {
            NO_LEAVES.clear();
            OCEAN_FLOOR.clear();
            lastLevel = level;
        }
        int min = level.getMinBuildHeight();
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return min;
        }
        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL), now = level.getGameTime();
        if (map.containsKey(k)) {
            long v = map.get(k), at = v >> 32;
            if (now >= at && now - at < 40) {
                return (int) v;
            }
        }
        int h = min;
        for (int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1; y >= min; y--) {
            BlockState s = level.getBlockState(POS.set(x, y, z));
            boolean stop = leaves ? (s.blocksMotion() || !s.getFluidState().isEmpty()) && !(s.getBlock() instanceof LeavesBlock) : s.blocksMotion();
            if (stop) {
                h = y + 1;
                break;
            }
        }
        if (map.size() > 250000) {
            map.clear();
        }
        map.put(k, (now << 32) | (h & 0xFFFFFFFFL));
        return h;
    }
}
