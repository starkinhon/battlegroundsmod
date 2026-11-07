package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.skill.ChargeSpec;
import com.starkinhon.battlegroundsmod.skill.StaminaSpec;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// The charge bar (combat/Charge): how far the attack being held has charged (Ataque Forte, giant Katon, Blue, Red,
// Purple, Black Box...). It stands beside the stamina bar (StaminaBar): in first person a straight bar just right of
// it, in third person a curve with the same arc, between the stamina and the body. It fills from the bottom with the
// soul's gradient (dark at the bottom, light at the tip; padrao.carga.json), shines and pulses once fully charged,
// flashes when the attack goes, then fades out.
public final class ChargeBar {
    private static float shown = 0;
    private static float alpha = 0;
    private static float flash = 0;
    private static boolean charging;
    private static boolean wasFull;
    private static long lastNanos = 0;

    private ChargeBar() {
    }

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker, Player player, PowerData data) {
        long nanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        float target = data.chargeSent;
        if (target >= 0) {
            if (!charging) {
                // A new charge starts from where it is, not from the last one.
                charging = true;
                wasFull = false;
                shown = target;
            }
            shown += (target - shown) * Math.min(1, dt * 18);
            if (target >= 1 && !wasFull) {
                wasFull = true;
                flash = 1;
            }
            alpha = Math.min(1, alpha + dt * 8);
        } else {
            if (charging && wasFull) {
                // Let go fully charged: one more flash as it goes.
                flash = 1;
            }
            charging = false;
            alpha = Math.max(0, alpha - dt * 2.5f);
        }
        flash = Math.max(0, flash - dt * 3.5f);
        if (alpha <= 0.01f) {
            return;
        }
        ChargeSpec spec = ChargeSpec.get();
        StaminaSpec.Barra stamina = StaminaSpec.get().barra();
        Power power = Powers.get(data.power);
        int[] colors = spec.gradient(data.power, power != null ? power.color() : 0xFFFFFF);
        float time = (player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20f;
        // Fully charged and still held: breathes brighter.
        float pulse = charging && wasFull ? 0.5f + 0.5f * Mth.sin(time * 10) : 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType().isFirstPerson()) {
            side(graphics, spec, stamina, colors, pulse);
        } else if (StaminaBar.captured) {
            float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
            arc(graphics, player, player.getPosition(partial), spec, stamina, colors, pulse);
        }
    }

    private static int a(int alphaByte, int rgb) {
        return GuiDraw.argb(Math.round(Mth.clamp(alphaByte * alpha, 0, 255)), rgb);
    }

    // The color at fraction f of the bar (0 = bottom), brightened by the pulse and the flash.
    private static int at(int[] colors, float f, float pulse) {
        int c = GuiDraw.mix(colors[0], colors[1], Mth.clamp(f, 0, 1));
        return GuiDraw.mix(c, 0xFFFFFF, pulse * 0.22f + flash * 0.6f);
    }

    // First person: right of the stamina bar, as tall as it, filled from the bottom.
    private static void side(GuiGraphics graphics, ChargeSpec spec, StaminaSpec.Barra stamina, int[] colors, float pulse) {
        float h = stamina.primeiraPessoaAltura();
        float w = Math.max(1, spec.larguraPrimeiraPessoa());
        float x = stamina.primeiraPessoaX() + stamina.primeiraPessoaLargura() + Math.max(0, spec.espacoPrimeiraPessoa());
        float top = graphics.guiHeight() / 2f - h / 2;
        float bottom = top + h;
        if (charging && wasFull || flash > 0) {
            GuiDraw.glow(graphics, x + w / 2, top + h / 2, h * 0.45f, colors[1], (0.18f + 0.15f * pulse + 0.4f * flash) * alpha);
        }
        GuiDraw.rect(graphics, x - 1, top - 1, x + w + 1, bottom + 1, a(200, 0x0A0510));
        GuiDraw.rect(graphics, x, top, x + w, bottom, a(110, GuiDraw.mix(colors[0], 0x202020, 0.5f)));
        if (shown > 0) {
            float y = bottom - h * shown;
            int tip = at(colors, shown, pulse);
            int base = at(colors, 0, pulse);
            GuiDraw.quad(graphics, x, y, x, bottom, x + w, bottom, x + w, y, a(255, tip), a(255, base), a(255, base), a(255, tip));
            // A bright edge on the tip.
            GuiDraw.rect(graphics, x, y, x + w, Math.min(bottom, y + 1), a(230, GuiDraw.mix(colors[1], 0xFFFFFF, 0.6f)));
        }
    }

    // Third person: a curve like the stamina's, a little closer to the body.
    private static void arc(GuiGraphics graphics, Player player, Vec3 feet, ChargeSpec spec, StaminaSpec.Barra stamina, int[] colors, float pulse) {
        int segments = StaminaBar.SEGMENTS;
        double scale = player.getBbHeight() / 1.8;
        Vec3 center = feet.add(0, stamina.alturaNoCorpo() * scale, 0);
        double radius = (stamina.distancia() - spec.espacoTerceiraPessoa()) * scale;
        double half = spec.espessura() * scale / 2;
        double span = Math.toRadians(stamina.arcoGraus());
        double outline = Math.max(half * 0.45, 0.012 * scale);
        float[][] inner = new float[segments + 1][];
        float[][] outer = new float[segments + 1][];
        float[][] innerLine = new float[segments + 1][];
        float[][] outerLine = new float[segments + 1][];
        for (int i = 0; i <= segments; i++) {
            double angle = -span / 2 + span * i / segments;
            inner[i] = StaminaBar.project(graphics, center, angle, radius - half);
            outer[i] = StaminaBar.project(graphics, center, angle, radius + half);
            innerLine[i] = StaminaBar.project(graphics, center, angle, radius - half - outline);
            outerLine[i] = StaminaBar.project(graphics, center, angle, radius + half + outline);
            if (inner[i] == null || outer[i] == null || innerLine[i] == null || outerLine[i] == null) {
                return;
            }
        }
        if (charging && wasFull || flash > 0) {
            float[] tip = StaminaBar.lerp(outer, segments);
            float size = Math.abs(inner[segments][1] - inner[0][1]) * 0.6f + 6;
            GuiDraw.glow(graphics, tip[0], tip[1], size, colors[1], (0.2f + 0.15f * pulse + 0.4f * flash) * alpha);
        }
        int dark = GuiDraw.mix(colors[0], 0x202020, 0.5f);
        band(graphics, innerLine, outerLine, 0, 1, f -> a(190, 0x0A0510), segments);
        band(graphics, inner, outer, 0, 1, f -> a(110, dark), segments);
        if (shown > 0) {
            band(graphics, inner, outer, 0, shown, f -> a(255, at(colors, f, pulse)), segments);
            // The tip: a thin bright slice.
            float edge = Math.max(0, shown - 0.025f);
            band(graphics, inner, outer, edge, shown, f -> a(230, GuiDraw.mix(colors[1], 0xFFFFFF, 0.6f)), segments);
        }
    }

    private interface Shade {
        int color(float fraction);
    }

    // The band between the curves from fraction from to to (0 = bottom end), colored along its length; the outer edge
    // a bit brighter.
    private static void band(GuiGraphics graphics, float[][] inner, float[][] outer, float from, float to, Shade shade, int segments) {
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
            int c0 = shade.color(a0 / segments);
            int c1 = shade.color(a1 / segments);
            GuiDraw.quad(graphics, i0[0], i0[1], o0[0], o0[1], o1[0], o1[1], i1[0], i1[1], c0, brighter(c0), brighter(c1), c1);
        }
    }

    private static int brighter(int argb) {
        return (argb & 0xFF000000) | GuiDraw.mix(argb & 0xFFFFFF, 0xFFFFFF, 0.2f);
    }
}
