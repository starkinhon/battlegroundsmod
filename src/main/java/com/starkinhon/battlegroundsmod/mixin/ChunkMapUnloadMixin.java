package com.starkinhon.battlegroundsmod.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;

// Fixes the game hanging forever on "Saving world" (black screen, Stark's PC 2026-11-14, jstack of the voo-325 test:
// Server thread 12 minutes at 100% CPU inside ChunkMap.scheduleUnload, called from stopServer).
//
// Vanilla 1.21.1 bug. A chunk that was queued for unloading can be brought back (the player flies back to it, or a
// neighbour starts generating and claims it). Its old unload callback is still queued. When that callback runs and the
// chunk is claimed by a generation task (generation ref count > 0) while its save future is already done, it calls
// scheduleUnload again, which puts the same callback straight back into the unload queue. processUnloads keeps polling
// that queue while hasMoreTime is true. During a normal tick that ends when the tick runs out of time, but stopServer and
// saveAllChunks pass () -> true, so the server thread spins forever. The generation task that holds the claim is only
// dispatched by runDistanceManagerUpdates, on the same server thread, so the claim is never released: a deadlock.
// Fast flight (Dragon Ball) loads and drops chunks constantly, which is why it showed up in the flight tests.
//
// Fix: when the callback would requeue itself without anything to wait for, it waits for the next processUnloads call
// instead. In between, the server runs the generation tasks and the claim goes away. The chunk is unloaded and saved
// exactly as vanilla does, one tick later.
@Mixin(ChunkMap.class)
public abstract class ChunkMapUnloadMixin {
    @Unique
    private final List<Object[]> battlegroundsmod$deferredUnloads = new ArrayList<>();

    @Shadow
    private void scheduleUnload(long chunkPos, ChunkHolder chunkHolder) {
    }

    @Inject(method = "scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V", at = @At("HEAD"), cancellable = true)
    private void battlegroundsmod$deferBusyUnload(long chunkPos, ChunkHolder holder, CallbackInfo ci) {
        // Only the case that spins: nothing left to wait for, but the chunk is still claimed by a generation task.
        if (holder.getGenerationRefCount() != 0 && holder.getSaveSyncFuture().isDone()) {
            battlegroundsmod$deferredUnloads.add(new Object[] {chunkPos, holder});
            ci.cancel();
        }
    }

    @Inject(method = "processUnloads(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void battlegroundsmod$retryDeferredUnloads(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        if (battlegroundsmod$deferredUnloads.isEmpty()) {
            return;
        }
        List<Object[]> retry = new ArrayList<>(battlegroundsmod$deferredUnloads);
        battlegroundsmod$deferredUnloads.clear();
        for (Object[] entry : retry) {
            // still claimed: deferred again by the HEAD inject above, without spinning
            scheduleUnload((Long) entry[0], (ChunkHolder) entry[1]);
        }
    }
}
