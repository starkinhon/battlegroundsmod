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
// side of the screen. Gold, red when low, dark red filling back up while exhausted. A white trail shows what was just
// spent. Full and out of combat it fades out. Values in padrao.estamina.json ("barra").
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
        if (!data.hasPower()) {
            return;
        }
        // The charge bar stands beside this one, even while this one is faded out.
        ChargeBar.render(graphics, deltaTracker, player, data);
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
            fill = GuiDraw.mix(StaminaSpec.Barra.rgb(spec.corBaixa(), 0xFF3B3B), 0xFFFFFF, pulse * 0.15f);
        } else {
            fill = StaminaSpec.Barra.rgb(spec.cor(), 0xFFC93C);
        }
        if (minecraft.options.getCameraType().isFirstPerson()) {
            side(graphics, spec, fill, exhausted);
        } else if (captured) {
            float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
            arc(graphics, player, player.getPosition(partial), spec, fill, exhausted);
        }
    }

    private static int a(int alphaByte, int rgb) {
        return GuiDraw.argb(Math.round(alphaByte * alpha), rgb);
    }

    // First person: straight bar on the left, filled from the bottom.
    private static void side(GuiGraphics graphics, StaminaSpec.Barra spec, int fill, boolean exhausted) {
        float h = spec.primeiraPessoaAltura();
        float w = spec.primeiraPessoaLargura();
        float x = spec.primeiraPessoaX();
        float top = graphics.guiHeight() / 2f - h / 2;
        float bottom = top + h;
        GuiDraw.rect(graphics, x - 1, top - 1, x + w + 1, bottom + 1, a(200, 0x140A04));
        GuiDraw.rect(graphics, x, top, x + w, bottom, a(110, 0x3A3026));
        if (trail > shown) {
            GuiDraw.rect(graphics, x, bottom - h * trail, x + w, bottom - h * shown, a(200, 0xFFF4D6));
        }
        if (shown > 0) {
            float y = bottom - h * shown;
            GuiDraw.quad(graphics, x, y, x, bottom, x + w, bottom, x + w, y,
                    a(255, GuiDraw.mix(fill, 0xFFFFFF, 0.25f)), a(255, fill), a(255, fill), a(255, GuiDraw.mix(fill, 0xFFFFFF, 0.25f)));
            // A bright edge on top of the fill.
            GuiDraw.rect(graphics, x, y, x + w, Math.min(bottom, y + 1), a(exhausted ? 120 : 220, 0xFFFFFF));
        }
    }

    // Third person: the curve beside the body, drawn in world space and projected, so it sizes with the distance.
    private static void arc(GuiGraphics graphics, Player player, Vec3 feet, StaminaSpec.Barra spec, int fill, boolean exhausted) {
        double scale = player.getBbHeight() / 1.8;
        Vec3 center = feet.add(0, spec.alturaNoCorpo() * scale, 0);
        double radius = spec.distancia() * scale;
        double half = spec.espessura() * scale / 2;
        double span = Math.toRadians(spec.arcoGraus());
        float[][] inner = new float[SEGMENTS + 1][];
        float[][] outer = new float[SEGMENTS + 1][];
        float[][] innerLine = new float[SEGMENTS + 1][];
        float[][] outerLine = new float[SEGMENTS + 1][];
        double outline = Math.max(half * 0.45, 0.012 * scale);
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = -span / 2 + span * i / SEGMENTS;
            inner[i] = project(graphics, center, angle, radius - half);
            outer[i] = project(graphics, center, angle, radius + half);
            innerLine[i] = project(graphics, center, angle, radius - half - outline);
            outerLine[i] = project(graphics, center, angle, radius + half + outline);
            if (inner[i] == null || outer[i] == null || innerLine[i] == null || outerLine[i] == null) {
                return;
            }
        }
        // Outline (a bit past both ends too), the empty track, the trail, the fill.
        band(graphics, innerLine, outerLine, 0, 1, a(190, 0x140A04), a(190, 0x140A04));
        band(graphics, inner, outer, 0, 1, a(110, 0x3A3026), a(110, 0x3A3026));
        if (trail > shown) {
            band(graphics, inner, outer, shown, trail, a(210, 0xFFF4D6), a(210, 0xFFF4D6));
        }
        if (shown > 0) {
            int light = GuiDraw.mix(fill, 0xFFFFFF, exhausted ? 0.1f : 0.35f);
            band(graphics, inner, outer, 0, shown, a(255, fill), a(255, light));
        }
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
