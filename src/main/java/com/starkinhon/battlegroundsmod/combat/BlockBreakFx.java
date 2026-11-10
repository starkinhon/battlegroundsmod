package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

// The vanilla "block breaking" look (bits of dirt, stone, wood flying off) for blocks the skills erase with setBlock.
// Vanilla's levelEvent 2001 throws ~64 bits and a sound per block, and a crater erases thousands of blocks, so it is
// never used for craters (#236/#258). Here every erased block is a candidate; at the end of each server tick a fair
// random sample of at most PER_TICK of them (per level) gets a few bits, and only SOUNDS_PER_TICK of those play the
// break sound. A crater spread over many ticks still shows bits all over it, at a fixed cost per tick.
public final class BlockBreakFx {
    private static final int PER_TICK = 48;
    private static final int SOUNDS_PER_TICK = 3;
    private static final int BITS = 7;
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    // Reservoir sample of this tick's erased blocks, per level.
    private static final class Pool {
        final BlockPos[] pos = new BlockPos[PER_TICK];
        final BlockState[] state = new BlockState[PER_TICK];
        int seen;
    }

    private static final Map<ServerLevel, Pool> POOLS = new HashMap<>();

    private BlockBreakFx() {
    }

    // Erases the block to air with the given setBlock flags and queues its breaking bits. Same result as setBlock.
    public static boolean erase(ServerLevel level, BlockPos pos, BlockState was, int flags) {
        boolean changed = level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
        if (changed) {
            note(level, pos, was);
        }
        return changed;
    }

    public static boolean erase(ServerLevel level, BlockPos pos, int flags) {
        return erase(level, pos, level.getBlockState(pos), flags);
    }

    // Queues the breaking bits of a block already removed (or replaced) by the caller.
    public static void note(ServerLevel level, BlockPos pos, BlockState was) {
        if (was.isAir() || !was.getFluidState().isEmpty() || was.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) {
            return;
        }
        Pool pool = POOLS.computeIfAbsent(level, l -> new Pool());
        int k = pool.seen++;
        int slot = k < PER_TICK ? k : RANDOM.nextInt(k + 1);
        if (slot < PER_TICK) {
            pool.pos[slot] = pos.immutable();
            pool.state[slot] = was;
        }
    }

    // End of the server tick: shows the sampled bits and empties the pools.
    public static void flush() {
        if (POOLS.isEmpty()) {
            return;
        }
        for (Map.Entry<ServerLevel, Pool> e : POOLS.entrySet()) {
            ServerLevel level = e.getKey();
            Pool pool = e.getValue();
            int n = Math.min(pool.seen, PER_TICK);
            int sounds = 0;
            for (int i = 0; i < n; i++) {
                BlockPos p = pool.pos[i];
                BlockState s = pool.state[i];
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        BITS, 0.3, 0.3, 0.3, 0.15);
                // The sample is already random, so the first few are as good as any.
                if (sounds < SOUNDS_PER_TICK) {
                    sounds++;
                    SoundType type = s.getSoundType();
                    level.playSound(null, p, type.getBreakSound(), SoundSource.BLOCKS, (type.getVolume() + 1) / 2F, type.getPitch() * 0.8F);
                }
            }
        }
        POOLS.clear();
    }

    public static void clear() {
        POOLS.clear();
    }
}
