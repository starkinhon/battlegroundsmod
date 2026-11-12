package com.starkinhon.battlegroundsmod.world;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

// A palm of the Mar do Sul (Stark 2026-11-11: "ilhas tropicais da Kame House", sunny): a thin trunk that leans and curves
// as it rises, a crown of long fronds arching out and drooping at the tips, coconuts under the crown. How tall, how far
// it leans and which way, how many fronds and how long each one is all come from the random, so no two palms match.
// The shape is given block by block to a Placer, so the same palm grows from a worldgen feature (PalmTreeFeature, the
// islands) or from a building (the Kame House island, WorldSites).
public final class PalmTree {
    // Puts one block at an offset from the foot; log is true for the trunk (it may replace leaves).
    public interface Placer {
        void put(int dx, int dy, int dz, BlockState state, boolean log);
    }

    private PalmTree() {
    }

    public static void grow(RandomSource random, Placer placer) {
        int height = 6 + random.nextInt(5);
        double lean = random.nextDouble() * Math.PI * 2;
        double bend = 1.2 + random.nextDouble() * 2.6;
        BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
        int lastX = 0, lastZ = 0;
        for (int y = 0; y < height; y++) {
            double t = (double) y / height;
            int x = (int) Math.round(Math.cos(lean) * bend * t * t);
            int z = (int) Math.round(Math.sin(lean) * bend * t * t);
            if (x != lastX && z != lastZ) {
                // a diagonal step: a block beside it so the trunk stays joined
                placer.put(x, y - 1, lastZ, log, true);
            } else if (x != lastX || z != lastZ) {
                placer.put(x, y - 1, z, log, true);
            }
            placer.put(x, y, z, log, true);
            lastX = x;
            lastZ = z;
        }
        int top = height - 1;
        BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        placer.put(lastX, top + 1, lastZ, leaves, false);
        // Coconuts: cocoa pods hung on the trunk just under the crown.
        int nuts = random.nextInt(4);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (nuts > 0 && random.nextInt(2) == 0) {
                nuts--;
                placer.put(lastX + side.getStepX(), top - 1, lastZ + side.getStepZ(), Blocks.COCOA.defaultBlockState()
                        .setValue(CocoaBlock.FACING, side.getOpposite()).setValue(CocoaBlock.AGE, 2), false);
            }
        }
        int fronds = 6 + random.nextInt(3);
        double spin = random.nextDouble() * Math.PI * 2;
        for (int f = 0; f < fronds; f++) {
            double a = spin + f * Math.PI * 2 / fronds + (random.nextDouble() - 0.5) * 0.5;
            double length = 3.2 + random.nextDouble() * 2.3;
            double rise = 0.55 + random.nextDouble() * 0.35;
            double droop = 0.16 + random.nextDouble() * 0.1;
            int px = Integer.MIN_VALUE, py = 0, pz = 0;
            for (double s = 0.6; s <= length; s += 0.35) {
                int x = lastX + (int) Math.round(Math.cos(a) * s);
                int z = lastZ + (int) Math.round(Math.sin(a) * s);
                int y = top + 1 + (int) Math.round(rise * s - droop * s * s);
                if (x == px && y == py && z == pz) {
                    continue;
                }
                placer.put(x, y, z, leaves, false);
                // the frond's broad middle: a leaf to one side, now and then
                if (s > length * 0.3 && s < length * 0.75 && random.nextInt(3) == 0) {
                    int sx = (int) Math.round(-Math.sin(a)), sz = (int) Math.round(Math.cos(a));
                    int sign = random.nextBoolean() ? 1 : -1;
                    placer.put(x + sx * sign, y, z + sz * sign, leaves, false);
                }
                px = x;
                py = y;
                pz = z;
            }
            // the tip hangs down one more
            if (px != Integer.MIN_VALUE) {
                placer.put(px, py - 1, pz, leaves, false);
            }
        }
    }
}
