package com.starkinhon.battlegroundsmod.client.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

// Ground height for client-side effects. The client only keeps the WORLD_SURFACE and MOTION_BLOCKING heightmaps, so
// MOTION_BLOCKING_NO_LEAVES read on the client returns the bottom of the world (the bug that hid Luffy's rubber wave).
// This reads MOTION_BLOCKING and walks down past leaves instead, giving the same answer NO_LEAVES gives on the server.
public final class ClientGround {
    private ClientGround() {
    }

    // Same contract as level.getHeight(MOTION_BLOCKING_NO_LEAVES, x, z): the first free y above the ground.
    public static int noLeaves(Level level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top - 1, z);
        int min = level.getMinBuildHeight();
        for (int i = 0; i < 64 && pos.getY() >= min; i++) {
            var state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES) && !(state.isAir() && i > 0)) {
                break;
            }
            pos.move(0, -1, 0);
        }
        return pos.getY() + 1;
    }
}
