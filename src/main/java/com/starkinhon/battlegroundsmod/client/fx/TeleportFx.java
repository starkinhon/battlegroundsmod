package com.starkinhon.battlegroundsmod.client.fx;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.skill.VfxSpec.Efeito;

import net.minecraft.util.Mth;

// riscosTp: the manga teleport, a bundle of thin black lines slashed across the screen on a diagonal where someone
// vanishes or appears (Bills' Esfera da Destruição Planetária). The bundle is shaped like a lens: the lines in the
// middle are the longest. They shoot out along the diagonal, hold for a moment and then slide on and thin out.
// tamanho: how long the bundle is; quantidade: how many lines; girar: the diagonal's angle in degrees (default 32);
// cor: the lines (black by default, drawn without glow so they stay black over anything).
final class TeleportFx {
    private TeleportFx() {
    }

    static void streaks(Efeito e, int seed, Vector3f at, float age, float t, float[] cor, float size, float opacity) {
        int count = FxWorld.i(e.quantidade(), 34);
        float angle = (float) FxWorld.d(e.girar(), 32) * Mth.DEG_TO_RAD;
        Vector3f right = FxDraw.left().negate();
        Vector3f up = FxDraw.up();
        Vector3f along = new Vector3f(right).mul(Mth.cos(angle)).add(new Vector3f(up).mul(Mth.sin(angle)));
        Vector3f across = new Vector3f(up).mul(Mth.cos(angle)).sub(new Vector3f(right).mul(Mth.sin(angle)));
        float thick = size * 0.33f;
        // Out fast, hold, then slide on and thin away.
        float grow = FxWorld.easeOut(Math.min(1, t * 5));
        float slide = t < 0.55f ? 0 : (t - 0.55f) / 0.45f;
        float fade = (1 - slide * slide) * opacity;
        for (int k = 0; k < count; k++) {
            float h1 = FxWorld.hash(seed, k, 31);
            float h2 = FxWorld.hash(seed, k, 32);
            float h3 = FxWorld.hash(seed, k, 33);
            // Spread across the bundle, packed towards the middle.
            float p = (h1 * 2 - 1);
            p = p * Math.abs(p) * 0.6f + p * 0.4f;
            float lens = 1 - p * p;
            float length = size * (0.35f + 0.75f * lens) * (0.6f + 0.6f * h2) * grow;
            float shift = (h3 - 0.5f) * size * 0.35f + slide * size * (0.6f + 0.6f * h2);
            Vector3f mid = new Vector3f(at).add(new Vector3f(across).mul(p * thick)).add(new Vector3f(along).mul(shift));
            Vector3f from = new Vector3f(along).mul(-length / 2).add(mid);
            Vector3f to = new Vector3f(along).mul(length / 2).add(mid);
            float width = size * (0.006f + 0.012f * FxWorld.hash(seed, k, 34)) * (1 - 0.6f * slide);
            FxDraw.ribbon(from, to, width, FxDraw.STREAK, 0.95f, cor, fade, false);
        }
    }
}
