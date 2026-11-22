// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.StaminaSpec;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// The attack stamina bar (combat/Stamina). Third person (front or back): a curved bar floating on the right of the
// player, facing the camera, like a ")" that empties from the top down. First person: a straight bar on the left
// side of the screen. White (Stark 2026-11-21, web preview "Barra de estamina branca"): a cool white gradient, thin
// dark outline, glassy track, soft glow, a lit tip and a mark every 25%; the curve's ends taper. Blinks light red when
// low, dark red filling back up while exhausted. A faded white trail shows what was just spent. Full and out of combat
// it fades out. Values in padrao.estamina.json ("barra").
public final class StaminaBar {
    static final int SEGMENTS = 28;
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static Vector3f right = new Vector3f(1, 0, 0);
    private static Vector3f up = new Vector3f(0, 1, 0);
    static boolean captured;

    // Shown values, eased towards the synced one.
    private static float shown = 1;
    private static float trail = 1;
    private static float alpha = 0;
    private static long lastNanos = 0;

    private StaminaBar() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Camera cam = event.getCamera();
        VIEW.set(event.getModelViewMatrix());
        PROJ.set(event.getProjectionMatrix());
        camera = cam.getPosition();
        right = new Vector3f(cam.getLeftVector()).negate();
        up = new Vector3f(cam.getUpVector());
        captured = true;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        // Menus (inventory, chests, the Souls Book...) cover the bars: not drawn over them. The chat keeps them.
        if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) {
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        if (!com.starkinhon.battlegroundsmod.combat.CombatMode.fighter(player)) {
            return;
        }
        // The charge bar stands beside this one, even while this one is faded out.
        ChargeBar.render(graphics, deltaTracker, player, data);
        // The guard meter, on the other side of the body (or under the crosshair).
        GuardBar.render(graphics, deltaTracker, player);
        // Sukuna's Modelo bar, above the hotbar.
        ModelBarHud.render(graphics, deltaTracker, player, data);
        if (data.stamina < 0) {
            return;
        }
        StaminaSpec.Barra spec = StaminaSpec.get().barra();
        float target = Mth.clamp(data.stamina / Math.max(1, data.staminaMax), 0, 1);
        boolean exhausted = data.exhaustedTicks > 0;
        // Time-based easing, so the bar moves the same at any frame rate.
        long nanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        shown += (target - shown) * Math.min(1, dt * 14);
        if (target >= trail) {
            trail = shown;
        } else {
            trail += (target - trail) * Math.min(1, dt * 2.5f);
        }
        boolean visible = exhausted || data.combat || target < 0.995f || !spec.esconderCheia();
        alpha = Mth.clamp(alpha + (visible ? dt * 4 : -dt * 1.5f), 0, 1);
        if (alpha <= 0.01f) {
            return;
        }
        float time = (player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20f;
        int fill;
        if (exhausted) {
            // Pulses while it can't be used.
            float pulse = 0.55f + 0.45f * Mth.sin(time * 9);
            fill = GuiDraw.mix(StaminaSpec.Barra.rgb(spec.corExausto(), 0x8A1F1F), StaminaSpec.Barra.rgb(spec.corBaixa(), 0xFF3B3B), pulse * 0.6f);
        } else if (target * 100 < spec.baixaAbaixoDePct()) {
            float pulse = 0.5f + 0.5f * Mth.sin(time * 6);
            fill = GuiDraw.mix(StaminaSpec.Barra.rgb(spec.cor(), 0xFFFFFF), StaminaSpec.Barra.rgb(spec.corBaixa(), 0xFF7A7A), 0.55f + 0.45f * pulse);
        } else {
            fill = StaminaSpec.Barra.rgb(spec.cor(), 0xFFFFFF);
        }
        float spark = 0.5f + 0.5f * Mth.sin(time * 5);
        if (minecraft.options.getCameraType().isFirstPerson()) {
            side(graphics, spec, fill, exhausted, spark);
        } else if (captured) {
            float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
            arc(graphics, player, player.getPosition(partial), spec, fill, exhausted, spark);
        }
    }

    private static int a(int alphaByte, int rgb) {
        return GuiDraw.argb(Math.round(alphaByte * alpha), rgb);
    }

    private static final int OUTLINE = 0x06080C;
    private static final int COOL = 0xD6E0EE;

    // First person: straight bar on the left, filled from the bottom.
    private static void side(GuiGraphics graphics, StaminaSpec.Barra spec, int fill, boolean exhausted, float spark) {
        float h = spec.primeiraPessoaAltura();
        float w = spec.primeiraPessoaLargura();
        float x = spec.primeiraPessoaX();
        float top = graphics.guiHeight() / 2f - h / 2;
        float bottom = top + h;
        // Thin dark outline and a glassy track with a light left edge.
        GuiDraw.rect(graphics, x - 1, top - 1, x + w + 1, bottom + 1, a(158, OUTLINE));
        GuiDraw.rect(graphics, x, top, x + w, bottom, a(26, 0xFFFFFF));
        GuiDraw.rect(graphics, x, top, x + 1, bottom, a(46, 0xFFFFFF));
        if (trail > shown) {
            GuiDraw.rect(graphics, x, bottom - h * trail, x + w, bottom - h * shown, a(97, 0xFFFFFF));
        }
        if (shown > 0) {
            float y = bottom - h * shown;
            // Soft glow, then the fill: white on the left, cool on the right.
            GuiDraw.rect(graphics, x - 3, y - 2, x + w + 3, bottom + 2, a(exhausted ? 10 : 18, fill));
            GuiDraw.rect(graphics, x - 2, y - 1, x + w + 2, bottom + 1, a(exhausted ? 15 : 31, fill));
            int cool = GuiDraw.mix(fill, COOL, 0.7f);
            GuiDraw.quad(graphics, x, y, x, bottom, x + w, bottom, x + w, y, a(255, fill), a(255, fill), a(255, cool), a(255, cool));
            // The lit tip, a little wider than the bar, and a spark blinking on it.
            GuiDraw.rect(graphics, x - 1, y, x + w + 1, Math.min(bottom, y + 1), a(255, 0xFFFFFF));
            if (!exhausted) {
                int s = a(Math.round(90 + 140 * spark), 0xFFFFFF);
                GuiDraw.rect(graphics, x + w / 2 - 0.5f, y - 3, x + w / 2 + 0.5f, y - 1, s);
                GuiDraw.rect(graphics, x + w / 2 - 0.5f, y + 2, x + w / 2 + 0.5f, y + 3, s);
            }
        }
        // A mark every 25%.
        for (int k = 1; k < 4; k++) {
            float yy = Math.round(bottom - h * k / 4f);
            GuiDraw.rect(graphics, x, yy, x + w, yy + 1, a(115, OUTLINE));
        }
    }

    // Third person: the curve beside the body, drawn in world space and projected, so it sizes with the distance. The
    // ends taper a little.
    private static void arc(GuiGraphics graphics, Player player, Vec3 feet, StaminaSpec.Barra spec, int fill, boolean exhausted, float spark) {
        double scale = player.getBbHeight() / 1.8;
        Vec3 center = feet.add(0, spec.alturaNoCorpo() * scale, 0);
        double radius = spec.distancia() * scale;
        double half = spec.espessura() * scale / 2;
        double span = Math.toRadians(spec.arcoGraus());
        double outline = Math.max(half * 0.35, 0.01 * scale);
        float[][] inner = new float[SEGMENTS + 1][];
        float[][] outer = new float[SEGMENTS + 1][];
        float[][] innerLine = new float[SEGMENTS + 1][];
        float[][] outerLine = new float[SEGMENTS + 1][];
        float[][] innerGlow = new float[SEGMENTS + 1][];
        float[][] outerGlow = new float[SEGMENTS + 1][];
        float[][] innerLight = new float[SEGMENTS + 1][];
        for (int i = 0; i <= SEGMENTS; i++) {
            double f = i / (double) SEGMENTS;
            double angle = -span / 2 + span * f;
            double e = Math.min(f, 1 - f) / 0.1;
            double hh = half * (e < 1 ? 0.6 + 0.4 * e : 1);
            inner[i] = project(graphics, center, angle, radius - hh);
            outer[i] = project(graphics, center, angle, radius + hh);
            innerLine[i] = project(graphics, center, angle, radius - hh - outline);
            outerLine[i] = project(graphics, center, angle, radius + hh + outline);
            innerGlow[i] = project(graphics, center, angle, radius - hh - outline * 2.2);
            outerGlow[i] = project(graphics, center, angle, radius + hh + outline * 2.2);
            innerLight[i] = project(graphics, center, angle, radius - hh * 0.1);
            if (inner[i] == null || outer[i] == null || innerLine[i] == null || outerLine[i] == null || innerGlow[i] == null
                    || outerGlow[i] == null || innerLight[i] == null) {
                return;
            }
        }
        // Outline, the glassy track, the trail, the glow, the fill (cool inside, white outside), the lit tip, the marks.
        band(graphics, innerLine, outerLine, 0, 1, a(153, OUTLINE), a(153, OUTLINE));
        band(graphics, inner, outer, 0, 1, a(28, 0xFFFFFF), a(28, 0xFFFFFF));
        if (trail > shown) {
            band(graphics, inner, outer, shown, trail, a(97, 0xFFFFFF), a(97, 0xFFFFFF));
        }
        if (shown > 0) {
            band(graphics, innerGlow, outerGlow, 0, shown, a(exhausted ? 10 : 20, fill), a(exhausted ? 10 : 20, fill));
            band(graphics, innerLine, outerLine, 0, shown, a(exhausted ? 13 : 31, fill), a(exhausted ? 13 : 31, fill));
            int cool = GuiDraw.mix(fill, COOL, 0.65f);
            band(graphics, inner, outer, 0, shown, a(255, cool), a(255, cool));
            band(graphics, innerLight, outer, 0, shown, a(255, fill), a(255, fill));
            if (!exhausted) {
                float at = shown * SEGMENTS;
                float[] i0 = lerp(inner, at);
                float[] o0 = lerp(outer, at);
                float cx = (i0[0] + o0[0]) / 2;
                float cy = (i0[1] + o0[1]) / 2;
                float r = (float) Math.hypot(o0[0] - i0[0], o0[1] - i0[1]) * 0.5f;
                diamond(graphics, cx, cy, r * 1.9f, a(Math.round(100 + 130 * spark), 0xFFFFFF));
                diamond(graphics, cx, cy, r * 0.9f, a(255, 0xFFFFFF));
            }
        }
        for (int k = 1; k < 4; k++) {
            float at = SEGMENTS * k / 4f;
            float[] i0 = lerp(inner, at);
            float[] o0 = lerp(outer, at);
            float dx = (o0[1] - i0[1]) * 0.08f;
            float dy = (i0[0] - o0[0]) * 0.08f;
            float nx = dx == 0 && dy == 0 ? 0.5f : dx;
            float ny = dy;
            float len = (float) Math.hypot(nx, ny);
            nx = nx / len * 0.5f;
            ny = ny / len * 0.5f;
            GuiDraw.quad(graphics, i0[0] - nx, i0[1] - ny, o0[0] - nx, o0[1] - ny, o0[0] + nx, o0[1] + ny, i0[0] + nx, i0[1] + ny,
                    a(115, OUTLINE), a(115, OUTLINE), a(115, OUTLINE), a(115, OUTLINE));
        }
    }

    private static void diamond(GuiGraphics graphics, float cx, float cy, float r, int color) {
        GuiDraw.quad(graphics, cx, cy - r, cx - r, cy, cx, cy + r, cx + r, cy, color, color, color, color);
    }

    // The band between the inner and outer curves, from fraction from to to (0 = bottom end), outer edge brighter.
    private static void band(GuiGraphics graphics, float[][] inner, float[][] outer, float from, float to, int innerColor, int outerColor) {
        float start = from * SEGMENTS;
        float end = to * SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Math.max(i, start);
            float a1 = Math.min(i + 1, end);
            if (a1 <= a0) {
                continue;
            }
            float[] i0 = lerp(inner, a0);
            float[] i1 = lerp(inner, a1);
            float[] o0 = lerp(outer, a0);
            float[] o1 = lerp(outer, a1);
            GuiDraw.quad(graphics, i0[0], i0[1], o0[0], o0[1], o1[0], o1[1], i1[0], i1[1], innerColor, outerColor, outerColor, innerColor);
        }
    }

    static float[] lerp(float[][] points, float at) {
        int i = Math.min(points.length - 2, (int) Math.floor(at));
        float t = at - i;
        return new float[] {Mth.lerp(t, points[i][0], points[i + 1][0]), Mth.lerp(t, points[i][1], points[i + 1][1])};
    }

    // A point of the curve (angle 0 = straight out to the right, up is positive) on the GUI, or null behind the camera.
    static float[] project(GuiGraphics graphics, Vec3 center, double angle, double distance) {
        double cos = Math.cos(angle) * distance;
        double sin = Math.sin(angle) * distance;
        Vector3f rel = new Vector3f(
                (float) (center.x - camera.x + right.x * cos + up.x * sin),
                (float) (center.y - camera.y + right.y * cos + up.y * sin),
                (float) (center.z - camera.z + right.z * cos + up.z * sin));
        Vector4f clip = new Vector4f(rel, 1);
        VIEW.transform(clip);
        PROJ.transform(clip);
        if (clip.w <= 0.05f) {
            return null;
        }
        float x = (clip.x / clip.w * 0.5f + 0.5f) * graphics.guiWidth();
        float y = (1 - (clip.y / clip.w * 0.5f + 0.5f)) * graphics.guiHeight();
        return new float[] {x, y};
    }
}
