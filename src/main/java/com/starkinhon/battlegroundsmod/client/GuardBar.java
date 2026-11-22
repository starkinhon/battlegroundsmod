// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.skill.DefesaSpec;
import com.starkinhon.battlegroundsmod.skill.StaminaSpec;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// The guard meter (combat/Defense): how much more the guard takes before it breaks. Only shows while the guard is up or
// the meter isn't full. Third person: a curve on the LEFT of the body, the stamina's mirror. First person: a thin bar
// under the crosshair. Ice blue; under padrao.defesa.json's trincadaAbaixoDe it blinks orange; broken, it flashes red
// and stays empty until the guard can go up again. With the Gunbai standing in front of the guard (the second guard), the
// bar shows the fan's meter instead, in Madara's crimson; once it gives out, the guard's own ice blue is back.
public final class GuardBar {
    private static final int ICE = 0x9FD8FF;
    private static final int ICE_DEEP = 0x4F9FE0;
    private static final int CRACK = 0xFF9A4A;
    private static final int BROKEN = 0xFF4A4A;
    private static final int OUTLINE = 0x06080C;
    private static final int GUNBAI = 0xE04848;
    private static final int GUNBAI_DEEP = 0x8A1E24;

    // Synced value: 0..1, or -1 while broken.
    private static float value = 1;
    private static float shown = 1;
    private static float alpha = 0;
    private static float flash = 0;
    private static long lastNanos = 0;
    // The Gunbai's synced meter: -2 not holding it, -1 resting, else 0..1.
    private static float gunbai = -2;
    // The local player it was shown for: a new world, server or respawn starts full.
    private static Player owner;

    private GuardBar() {
    }

    static void set(float synced) {
        if (synced < 0 && value >= 0) {
            flash = 1;
        }
        value = synced;
    }

    static void setGunbai(float synced) {
        gunbai = synced;
    }

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker, Player player) {
        if (player != owner) {
            owner = player;
            reset();
        }
        long nanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        boolean fan = gunbai >= 0 && value >= 0;
        boolean broken = !fan && value < 0;
        float target = fan ? Mth.clamp(gunbai, 0, 1) : broken ? 0 : Mth.clamp(value, 0, 1);
        shown += (target - shown) * Math.min(1, dt * (broken ? 20 : 12));
        flash = Math.max(0, flash - dt * 2.5f);
        boolean visible = broken || target < 0.995f || CombatInput.guarding();
        alpha = Mth.clamp(alpha + (visible ? dt * 6 : -dt * 2f), 0, 1);
        if (alpha <= 0.01f) {
            return;
        }
        DefesaSpec.Resistencia r = DefesaSpec.get().resistencia();
        double crackAt = r == null ? 0 : r.trincadaAbaixoDe();
        float time = (player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20f;
        int fill;
        if (broken) {
            fill = BROKEN;
        } else if (fan) {
            fill = GUNBAI;
        } else if (shown < crackAt) {
            float pulse = 0.5f + 0.5f * Mth.sin(time * 12);
            fill = GuiDraw.mix(ICE, CRACK, 0.55f + 0.45f * pulse);
        } else {
            fill = ICE;
        }
        StaminaSpec.Barra stamina = StaminaSpec.get().barra();
        int deep = fan ? GUNBAI_DEEP : ICE_DEEP;
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            under(graphics, fill, deep, broken, time);
        } else if (StaminaBar.captured) {
            float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
            arc(graphics, player, player.getPosition(partial), stamina, fill, deep, broken, time);
        }
    }

    private static int a(int alphaByte, int rgb) {
        return GuiDraw.argb(Math.round(Mth.clamp(alphaByte * alpha, 0, 255)), rgb);
    }

    // First person: under the crosshair, emptying from both ends to the middle.
    private static void under(GuiGraphics graphics, int fill, int deepTone, boolean broken, float time) {
        float w = 40;
        float h = 3;
        float cx = graphics.guiWidth() / 2f;
        float top = graphics.guiHeight() / 2f + 12;
        float x0 = cx - w / 2;
        float x1 = cx + w / 2;
        if (flash > 0) {
            GuiDraw.glow(graphics, cx, top + h / 2, w * 0.6f, BROKEN, flash * 0.5f * alpha);
        }
        GuiDraw.rect(graphics, x0 - 1, top - 1, x1 + 1, top + h + 1, a(160, OUTLINE));
        GuiDraw.rect(graphics, x0, top, x1, top + h, a(broken ? 70 + Math.round(50 * (0.5f + 0.5f * Mth.sin(time * 14))) : 30,
                broken ? BROKEN : 0xFFFFFF));
        if (shown > 0.001f) {
            float half = w / 2 * shown;
            int deep = GuiDraw.mix(fill, deepTone, 0.5f);
            GuiDraw.quad(graphics, cx - half, top, cx - half, top + h, cx + half, top + h, cx + half, top, a(255, deep), a(255, deep), a(255, deep),
                    a(255, deep));
            GuiDraw.rect(graphics, cx - half, top, cx + half, top + 1, a(255, fill));
            // the lit ends
            GuiDraw.rect(graphics, cx - half - 0.5f, top - 0.5f, cx - half + 0.5f, top + h + 0.5f, a(255, 0xFFFFFF));
            GuiDraw.rect(graphics, cx + half - 0.5f, top - 0.5f, cx + half + 0.5f, top + h + 0.5f, a(255, 0xFFFFFF));
        }
    }

    // Third person: the stamina's curve mirrored on the left of the body (filled from the bottom up).
    private static void arc(GuiGraphics graphics, Player player, Vec3 feet, StaminaSpec.Barra stamina, int fill, int deepTone, boolean broken,
            float time) {
        int segments = StaminaBar.SEGMENTS;
        double scale = player.getBbHeight() / 1.8;
        Vec3 center = feet.add(0, stamina.alturaNoCorpo() * scale, 0);
        double radius = stamina.distancia() * scale;
        double half = stamina.espessura() * 0.85 * scale / 2;
        double span = Math.toRadians(stamina.arcoGraus());
        double outline = Math.max(half * 0.4, 0.01 * scale);
        float[][] inner = new float[segments + 1][];
        float[][] outer = new float[segments + 1][];
        float[][] innerLine = new float[segments + 1][];
        float[][] outerLine = new float[segments + 1][];
        for (int i = 0; i <= segments; i++) {
            double f = i / (double) segments;
            // angle PI is straight out to the left; f = 0 is the bottom end
            double angle = Math.PI + span / 2 - span * f;
            double e = Math.min(f, 1 - f) / 0.1;
            double hh = half * (e < 1 ? 0.6 + 0.4 * e : 1);
            inner[i] = StaminaBar.project(graphics, center, angle, radius - hh);
            outer[i] = StaminaBar.project(graphics, center, angle, radius + hh);
            innerLine[i] = StaminaBar.project(graphics, center, angle, radius - hh - outline);
            outerLine[i] = StaminaBar.project(graphics, center, angle, radius + hh + outline);
            if (inner[i] == null || outer[i] == null || innerLine[i] == null || outerLine[i] == null) {
                return;
            }
        }
        if (flash > 0) {
            float[] mid = StaminaBar.lerp(outer, segments / 2f);
            float size = Math.abs(inner[segments][1] - inner[0][1]) * 0.7f + 6;
            GuiDraw.glow(graphics, mid[0], mid[1], size, BROKEN, flash * 0.5f * alpha);
        }
        band(graphics, innerLine, outerLine, 0, 1, a(150, OUTLINE), segments);
        int track = broken ? a(70 + Math.round(50 * (0.5f + 0.5f * Mth.sin(time * 14))), BROKEN) : a(30, 0xFFFFFF);
        band(graphics, inner, outer, 0, 1, track, segments);
        if (shown > 0.001f) {
            band(graphics, inner, outer, 0, shown, a(255, GuiDraw.mix(fill, deepTone, 0.45f)), segments);
            float edge = Math.max(0, shown - 0.03f);
            band(graphics, inner, outer, edge, shown, a(240, 0xFFFFFF), segments);
        }
    }

    private static void band(GuiGraphics graphics, float[][] inner, float[][] outer, float from, float to, int color, int segments) {
        float start = from * segments;
        float end = to * segments;
        for (int i = 0; i < segments; i++) {
            float a0 = Math.max(i, start);
            float a1 = Math.min(i + 1, end);
            if (a1 <= a0) {
                continue;
            }
            float[] i0 = StaminaBar.lerp(inner, a0);
            float[] i1 = StaminaBar.lerp(inner, a1);
            float[] o0 = StaminaBar.lerp(outer, a0);
            float[] o1 = StaminaBar.lerp(outer, a1);
            GuiDraw.quad(graphics, i0[0], i0[1], o0[0], o0[1], o1[0], o1[1], i1[0], i1[1], color, color, color, color);
        }
    }

    private static void reset() {
        value = 1;
        gunbai = -2;
        shown = 1;
        alpha = 0;
        flash = 0;
    }
}
