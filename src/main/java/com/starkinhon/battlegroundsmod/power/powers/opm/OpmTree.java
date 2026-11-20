// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.opm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

// A whole tree, for the bigger Saltos (felled by the dome) and Espirro (blown away in one piece), Stark 2026-11-20. The
// server and the client find the same blocks from the same lowest log in the same world: the logs joined to it (corners
// too), then the leaves touching those logs, up to a few leaves out. The server sends the base and takes the blocks out;
// the client reads them first and flies them as one piece.
public final class OpmTree {
    // Big trees stay big, but nothing turns into a flood through a forest.
    public static final int MAX_LOGS = 96, MAX_LEAVES = 700, LEAF_STEPS = 5, REACH_XZ = 9, REACH_UP = 34;

    private OpmTree() {
    }

    public static boolean log(BlockState st) {
        return st.is(BlockTags.LOGS);
    }

    public static boolean leaf(BlockState st) {
        return st.is(BlockTags.LEAVES);
    }

    // Walks down a trunk from a log to its lowest log (null when the log stands on more logs than a tree has).
    public static BlockPos base(BlockGetter level, BlockPos log) {
        BlockPos.MutableBlockPos p = log.mutable();
        for (int i = 0; i < REACH_UP; i++) {
            p.move(0, -1, 0);
            if (!log(level.getBlockState(p))) {
                return p.move(0, 1, 0).immutable();
            }
        }
        return null;
    }

    // The blocks of the tree whose lowest log is base: logs first, then leaves. Empty if base is not a log.
    public static List<BlockPos> blocks(BlockGetter level, BlockPos base) {
        List<BlockPos> out = new ArrayList<>();
        if (!log(level.getBlockState(base))) {
            return out;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> logs = new ArrayList<>();
        queue.add(base);
        seen.add(base);
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos p = queue.poll();
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos q = p.offset(dx, dy, dz);
                        if (Math.abs(q.getX() - base.getX()) > REACH_XZ || Math.abs(q.getZ() - base.getZ()) > REACH_XZ || q.getY() - base.getY() > REACH_UP
                                || !seen.add(q)) {
                            continue;
                        }
                        if (log(level.getBlockState(q))) {
                            queue.add(q);
                        }
                    }
                }
            }
        }
        out.addAll(logs);
        // the leaves, step by step out from the logs (faces only)
        List<BlockPos> ring = new ArrayList<>(logs);
        int leaves = 0;
        for (int step = 0; step < LEAF_STEPS && leaves < MAX_LEAVES && !ring.isEmpty(); step++) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos p : ring) {
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    BlockPos q = p.relative(d);
                    if (Math.abs(q.getX() - base.getX()) > REACH_XZ || Math.abs(q.getZ() - base.getZ()) > REACH_XZ || q.getY() < base.getY()
                            || !seen.add(q)) {
                        continue;
                    }
                    if (leaf(level.getBlockState(q))) {
                        next.add(q);
                        out.add(q);
                        if (++leaves >= MAX_LEAVES) {
                            break;
                        }
                    }
                }
                if (leaves >= MAX_LEAVES) {
                    break;
                }
            }
            ring = next;
        }
        return out;
    }

    // Is the column's top (leaves left out) a trunk? Then its lowest log, else null. One or two reads for most columns.
    public static BlockPos trunkAt(BlockGetter level, int x, int topY, int z) {
        BlockPos top = new BlockPos(x, topY, z);
        if (!log(level.getBlockState(top))) {
            return null;
        }
        return base(level, top);
    }
}
