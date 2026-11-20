// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// The game's own biome colors of the sky and the fog (the biome JSONs' sky_color and fog_color) are blended in space,
// over a dozen blocks around the camera. On foot that is a slow blend; flying fast (the Dragon Ball flight) the camera
// crosses it in a fraction of a second, and the sky and the horizon jumped from one biome's color to the next like a cut
// (Stark 2026-11-19). Here the color the game samples is eased in time too: it follows the sampled one over about a
// second, so crossing a border at any speed is a blend. Only the camera's own sample is eased (anything sampled
// elsewhere is passed through), and a new world or dimension starts from its own color.
public final class BiomeColorEase {
    // how fast the shown color follows the sampled one (seconds to cover ~63% of the way)
    private static final double TAU = 0.55;

    public static final BiomeColorEase SKY = new BiomeColorEase();
    public static final BiomeColorEase FOG = new BiomeColorEase();

    private Vec3 shown;
    private long lastNanos;
    private Level level;

    private BiomeColorEase() {
    }

    // sample: where the game sampled (quart coordinates, the camera's position minus 2 blocks, times 0.25);
    // sampled: the color it got there.
    public Vec3 ease(Vec3 sample, Vec3 sampled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || sampled == null || sample == null) {
            shown = null;
            return sampled;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 at = sample.scale(4).add(2, 2, 2);
        if (at.distanceToSqr(cam) > 4 * 4) {
            return sampled;
        }
        long now = System.nanoTime();
        if (shown == null || level != mc.level || now - lastNanos > 2_000_000_000L) {
            shown = sampled;
            level = mc.level;
            lastNanos = now;
            return sampled;
        }
        double dt = (now - lastNanos) / 1e9;
        lastNanos = now;
        double k = 1 - Math.exp(-dt / TAU);
        shown = shown.add(sampled.subtract(shown).scale(k));
        return shown;
    }
}
