package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import net.minecraft.util.RandomSource;

// Camera shake from big impacts. strength 1 is about a degree and a half of jitter, fading out over the ticks.
// Applied by CameraMixin on top of whatever the camera is doing.
public final class ScreenShake {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final float DEGREES_PER_STRENGTH = 1.5f;

    private static float strength;
    private static int ticks;
    private static int remaining;

    private ScreenShake() {
    }

    public static void start(float newStrength, int duration) {
        newStrength *= FxConfig.shake();
        if (newStrength <= 0) {
            return;
        }
        if (newStrength >= current()) {
            strength = newStrength;
            ticks = Math.max(1, duration);
            remaining = ticks;
        }
    }

    public static void tick() {
        if (remaining > 0) {
            remaining--;
        }
    }

    private static float current() {
        return remaining > 0 ? strength * remaining / ticks : 0;
    }

    // Random yaw and pitch offsets for this frame, or null when nothing shakes.
    public static float[] offsets() {
        float amount = current() * DEGREES_PER_STRENGTH;
        if (amount <= 0) {
            return null;
        }
        return new float[] {(RANDOM.nextFloat() * 2 - 1) * amount, (RANDOM.nextFloat() * 2 - 1) * amount};
    }
}
