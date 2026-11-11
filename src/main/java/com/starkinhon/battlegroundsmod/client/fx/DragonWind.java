package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// The air of the Dragon Ball lands (Stark 2026-11-11: "uns efeitos de vento correndo, pra deixar bonito" and the screen
// "mais alaranjado"). Two things, both fading in when the player walks into one of those biomes and out when he leaves:
// - gusts: thin streaks of wind running across the land with the breeze, each its own curve (length, speed, waves of
//   two sine curves with random phases, sometimes a curl), bright at the head and thinning to nothing at the tail; no
//   texture or particle, every point comes from the gust's own numbers, so no two are alike;
// - the warm grade over the screen: a light orange wash and a burnt-orange vignette at the edges, over the HUD too
//   (a soft yellow-green one in the Terra Sagrada de Karin, Stark: "amarelo é do deserto").
public final class DragonWind {
    private static final int MAX_GUSTS = 46;
    private static final int SAMPLES = 22;

    private record Gust(Vec3 origin, Vec3 dir, Vec3 side, double speed, double length, int life, double width, double amp,
            double freq, double phase, double lift, double liftFreq, double liftPhase, double curlAt, double curlRadius,
            long born, float warm) {
    }

    private static final List<Gust> GUSTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    // 0 outside, 1 inside a Dragon Ball biome; eases in and out over a few seconds.
    private static float strength;
    private static float lastStrength;
    // 0 in the yellow lands, 1 in the Terra Sagrada de Karin, whose grade is a soft yellow-green instead of orange.
    private static float green;

    private DragonWind() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            GUSTS.clear();
            strength = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        var biome = mc.level.getBiome(mc.player.blockPosition());
        boolean inside = biome.is(ModWorldgen.DRAGONBALL_BIOMES);
        if (inside) {
            green = Mth.clamp(green + (biome.is(ModWorldgen.TERRA_SAGRADA) ? 0.015f : -0.015f), 0, 1);
        }
        strength = Mth.clamp(strength + (inside ? 0.012f : -0.02f), 0, 1);
        long now = mc.level.getGameTime();
        GUSTS.removeIf(g -> now - g.born() > g.life());
        if (strength <= 0) {
            return;
        }
        // A gust or so every few ticks, more when the breeze picks up (a slow swell of its own).
        double swell = 0.65 + 0.35 * Math.sin(now / 173.0) * Math.sin(now / 61.0 + 1.3);
        if (GUSTS.size() < MAX_GUSTS && RANDOM.nextFloat() < 0.55f * strength * swell) {
            GUSTS.add(newGust(mc, now));
        }
    }

    // The breeze turns slowly over the day.
    private static double windAngle(long now) {
        return 0.7 + 0.9 * Math.sin(now / 4100.0) + 0.25 * Math.sin(now / 900.0 + 2.0);
    }

    private static Gust newGust(Minecraft mc, long now) {
        double angle = windAngle(now) + (RANDOM.nextDouble() - 0.5) * 0.5;
        Vec3 dir = new Vec3(Math.cos(angle), (RANDOM.nextDouble() - 0.5) * 0.06, Math.sin(angle)).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        double speed = 0.45 + RANDOM.nextDouble() * 0.55;
        double length = 4 + RANDOM.nextDouble() * 9;
        int life = 40 + RANDOM.nextInt(50);
        // Start upwind of the player so the gust crosses the view; mostly low over the ground, some higher up.
        double run = speed * life;
        double across = (RANDOM.nextDouble() - 0.5) * 44;
        double back = run * (0.35 + RANDOM.nextDouble() * 0.4);
        Vec3 eye = mc.player.getEyePosition();
        double height = RANDOM.nextFloat() < 0.7 ? -1.2 + RANDOM.nextDouble() * 3.5 : 2 + RANDOM.nextDouble() * 9;
        Vec3 origin = eye.add(dir.scale(-back)).add(side.scale(across)).add(0, height, 0);
        // Keep it above the ground where it starts.
        int ground = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(origin.x), Mth.floor(origin.z));
        if (origin.y < ground + 0.4) {
            origin = new Vec3(origin.x, ground + 0.4 + RANDOM.nextDouble() * 1.2, origin.z);
        }
        boolean curl = RANDOM.nextFloat() < 0.22f;
        return new Gust(origin, dir, side, speed, length, life, 0.035 + RANDOM.nextDouble() * 0.05,
                0.15 + RANDOM.nextDouble() * 0.9, 0.12 + RANDOM.nextDouble() * 0.35, RANDOM.nextDouble() * Mth.TWO_PI,
                0.1 + RANDOM.nextDouble() * 0.5, 0.08 + RANDOM.nextDouble() * 0.25, RANDOM.nextDouble() * Mth.TWO_PI,
                curl ? run * (0.3 + RANDOM.nextDouble() * 0.4) : -1, 0.6 + RANDOM.nextDouble() * 1.1, now, RANDOM.nextFloat());
    }

    // Where the gust's line is, s blocks along its run.
    private static Vec3 at(Gust g, double s) {
        Vec3 p = g.origin().add(g.dir().scale(s))
                .add(g.side().scale(g.amp() * Math.sin(s * g.freq() + g.phase())))
                .add(0, g.lift() * Math.sin(s * g.liftFreq() + g.liftPhase()), 0);
        if (g.curlAt() > 0) {
            // A little loop: over 2*pi*r blocks the line turns once around a circle standing along the wind.
            double span = Mth.TWO_PI * g.curlRadius();
            double k = (s - g.curlAt()) / span;
            if (k > 0 && k < 1) {
                double a = k * Mth.TWO_PI;
                double r = g.curlRadius() * Math.sin(k * Math.PI);
                p = p.add(g.dir().scale(Math.sin(a) * r)).add(0, (1 - Math.cos(a)) * r, 0);
            }
        }
        return p;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || GUSTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partial, lastStrength, strength);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        PoseStack.Pose last = pose.last();
        double now = mc.level.getGameTime() + partial;
        Vec3[] pts = new Vec3[SAMPLES + 1];
        for (Gust g : GUSTS) {
            double age = now - g.born();
            if (age < 0 || age > g.life()) {
                continue;
            }
            double lifeK = age / g.life();
            float lifeAlpha = (float) (Math.min(1, lifeK / 0.2) * Math.min(1, (1 - lifeK) / 0.35));
            double head = g.speed() * age;
            double tail = Math.max(0, head - g.length());
            if (head - tail < 0.3) {
                continue;
            }
            for (int i = 0; i <= SAMPLES; i++) {
                pts[i] = at(g, tail + (head - tail) * i / SAMPLES).subtract(camera);
            }
            // pale warm white, a few a little more golden
            float r = 1f, gg = 0.95f - 0.12f * g.warm(), b = 0.84f - 0.3f * g.warm();
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 a = pts[i], c = pts[i + 1];
                float ta = (float) i / SAMPLES, tc = (float) (i + 1) / SAMPLES;
                float aa = alpha(ta) * lifeAlpha * fade, ac = alpha(tc) * lifeAlpha * fade;
                Vector3f wa = sideOf(pts, i, g.width() * width(ta));
                Vector3f wc = sideOf(pts, i + 1, g.width() * width(tc));
                out.addVertex(last, (float) a.x - wa.x, (float) a.y - wa.y, (float) a.z - wa.z).setColor(r, gg, b, aa);
                out.addVertex(last, (float) a.x + wa.x, (float) a.y + wa.y, (float) a.z + wa.z).setColor(r, gg, b, aa);
                out.addVertex(last, (float) c.x + wc.x, (float) c.y + wc.y, (float) c.z + wc.z).setColor(r, gg, b, ac);
                out.addVertex(last, (float) c.x - wc.x, (float) c.y - wc.y, (float) c.z - wc.z).setColor(r, gg, b, ac);
            }
        }
        buffers.endBatch(FxRenderTypes.AREA);
    }

    // Along the streak (0 tail, 1 head): grows from nothing, strongest near the head, a soft tip.
    private static float alpha(float t) {
        float body = t * t * (3 - 2 * t);
        float tip = Math.min(1, (1 - t) / 0.08f);
        return 0.42f * body * tip;
    }

    private static float width(float t) {
        return 0.35f + 0.65f * (float) Math.sin(Math.PI * Math.min(1, t * 1.15));
    }

    // Half the width, across the line and facing the camera.
    private static Vector3f sideOf(Vec3[] pts, int i, double half) {
        Vec3 tangent = pts[Math.min(pts.length - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
        Vec3 side = tangent.cross(pts[i]);
        double len = side.length();
        if (len < 1.0E-6) {
            return new Vector3f();
        }
        // a little wider far away so distant gusts don't vanish into a single pixel line
        double scale = half * (1 + Math.min(1.5, pts[i].length() / 24)) / len;
        return new Vector3f((float) (side.x * scale), (float) (side.y * scale), (float) (side.z * scale));
    }

    // The warm grade, over everything the game draws on the screen.
    public static void renderGrade(GuiGraphics graphics, DeltaTracker delta) {
        float fade = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        int wash = mix(0xFF9A30, 0xC8EC6A, green);
        int edge = mix(0xB0480E, 0x4E8A20, green);
        graphics.fill(0, 0, w, h, argb((0.07f - 0.015f * green) * fade, wash));
        // Vignette: burnt orange getting stronger toward the edges, in thin bands.
        int bands = 18;
        int depthX = Math.max(1, w / 5), depthY = Math.max(1, h / 4);
        for (int i = 0; i < bands; i++) {
            float k = 1 - (float) i / bands;
            int color = argb((0.022f - 0.006f * green) * k * k * fade, edge);
            int x = depthX * i / bands, y = depthY * i / bands;
            int x2 = depthX * (i + 1) / bands, y2 = depthY * (i + 1) / bands;
            graphics.fill(x, 0, x2, h, color);
            graphics.fill(w - x2, 0, w - x, h, color);
            graphics.fill(0, y, w, y2, color);
            graphics.fill(0, h - y2, w, h - y, color);
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | rgb;
    }
}
