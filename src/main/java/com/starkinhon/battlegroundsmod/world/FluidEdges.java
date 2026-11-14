package com.starkinhon.battlegroundsmod.world;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

// The generators write water and lava as source blocks, one per cell, and nothing ticks them (an arena drops its fluid
// ticks on purpose, a feature never schedules any), so they never flow. Where a river steps down a slope, or a lava
// tongue runs down the volcano, each source sat alone on its step: the fluid renderer pulls a corner down toward every
// air cell next to it, and the path showed as a row of separate puddles with gaps between them instead of one stream
// (Stark, 2026-11-14, Madara's arena and the lava paths).
// Here every air cell next to a source (the 4 sides and the 4 corners, at the same height) that has ground or the same
// fluid under it gets the flowing block the fluid itself would put there on its first step (lava 6, water 7). That is
// what a real flow does to a step: it spills over and slopes down into the cell below. It stays fixed like the rest of
// the build (no tick, so nothing floods or drains), but the first block a fight breaks lets it run as usual.
// Never over a drop (no ground under the cell: a waterfall lip keeps its own falling water) and never over anything but air.
public final class FluidEdges {
    private static final int[][] AROUND = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    private final LongArrayList sources = new LongArrayList();

    // Writes the block and remembers it when it is a fluid source.
    public void set(LevelAccessor level, BlockPos pos, BlockState state, int flags) {
        level.setBlock(pos, state, flags);
        note(pos, state);
    }

    public void note(BlockPos pos, BlockState state) {
        if (state.getFluidState().isSource()) {
            sources.add(pos.asLong());
        }
    }

    // Gives every remembered source its edge. Returns how many blocks it wrote.
    public int apply(LevelAccessor level, int flags) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int i = 0; i < sources.size(); i++) {
            long p = sources.getLong(i);
            n += edge(level, pos, BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p), flags);
        }
        sources.clear();
        return n;
    }

    public static int edge(LevelAccessor level, BlockPos.MutableBlockPos pos, int x, int y, int z, int flags) {
        FluidState here = level.getBlockState(pos.set(x, y, z)).getFluidState();
        if (!here.isSource() || !(here.getType() instanceof FlowingFluid fluid)) {
            return 0;
        }
        BlockState spill = fluid.getFlowing(fluid.isSame(Fluids.LAVA) ? 6 : 7, false).createLegacyBlock();
        int n = 0;
        for (int[] d : AROUND) {
            if (!level.getBlockState(pos.set(x + d[0], y, z + d[1])).isAir()) {
                continue;
            }
            BlockState below = level.getBlockState(pos.set(x + d[0], y - 1, z + d[1]));
            if (!below.isSolid() && !below.getFluidState().getType().isSame(fluid)) {
                continue;
            }
            level.setBlock(pos.set(x + d[0], y, z + d[1]), spill, flags);
            n++;
        }
        return n;
    }
}
