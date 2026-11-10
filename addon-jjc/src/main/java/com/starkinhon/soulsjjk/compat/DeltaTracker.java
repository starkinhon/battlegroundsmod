package com.starkinhon.soulsjjk.compat;

import net.minecraft.client.Minecraft;

// 1.21's DeltaTracker: on 1.20.1 the partial tick is a plain float.
public final class DeltaTracker {
    private final float partial;

    private DeltaTracker(float partial) {
        this.partial = partial;
    }

    public static DeltaTracker of(float partial) {
        return new DeltaTracker(partial);
    }

    // The frame's partial tick, read from the game (paused: the frozen one).
    public static DeltaTracker now() {
        Minecraft mc = Minecraft.getInstance();
        return new DeltaTracker(mc.getPartialTick());
    }

    public float getGameTimeDeltaPartialTick(boolean ignoreFreeze) {
        return partial;
    }

    public float getRealtimeDeltaTicks() {
        return Minecraft.getInstance().getDeltaFrameTime();
    }

    public float getGameTimeDeltaTicks() {
        return Minecraft.getInstance().getDeltaFrameTime();
    }
}
