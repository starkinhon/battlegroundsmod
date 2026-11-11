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
//   (a soft yellow-green one in the Terra Sagrada de Karin, Stark: "amarelo é do deserto");
// - in the Mar do Sul (Stark: "uma vibe bem ensolarada, um sol forte"): a sea breeze, fewer and whiter gusts, a light
//   golden grade, and the strong tropical sun: a wide warm halo around it in the sky (behind the land, drawn right after
//   the sky) and a glare over the screen when the player looks toward it.
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
    // 1 in the Deserto do Diabo: a drought, a dangerous place (Stark): a red-orange grade, a light haze of sand and
    // stronger, faster, sand-colored wind.
    private static float desert;
    private static float lastDesert;
    // 1 in the Mar do Sul.
    private static float sea;
    private static float lastSea;

    private DragonWind() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        lastDesert = desert;
        lastSea = sea;
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
        desert = Mth.clamp(desert + (inside && biome.is(ModWorldgen.DESERTO_VERMELHO) ? 0.01f : -0.015f), 0, 1);
        sea = Mth.clamp(sea + (inside && biome.is(ModWorldgen.MAR_DO_SUL) ? 0.012f : -0.015f), 0, 1);
        strength = Mth.clamp(strength + (inside ? 0.012f : -0.02f), 0, 1);
        long now = mc.level.getGameTime();
        GUSTS.removeIf(g -> now - g.born() > g.life());
        if (strength <= 0) {
            return;
        }
        // A gust or so every few ticks, more when the breeze picks up (a slow swell of its own).
        double swell = 0.65 + 0.35 * Math.sin(now / 173.0) * Math.sin(now / 61.0 + 1.3);
        int max = MAX_GUSTS + (int) (60 * desert);
        float rate = 0.55f * strength * (float) swell * (1 + 2.2f * desert) * (1 - 0.45f * sea);
        while (rate > 0 && GUSTS.size() < max) {
            if (RANDOM.nextFloat() < Math.min(1, rate)) {
                GUSTS.add(newGust(mc, now));
            }
            rate -= 1;
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
        double speed = (0.45 + RANDOM.nextDouble() * 0.55) * (1 + 0.7 * desert);
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
        // In the desert most gusts carry sand: low over the ground, warm.
        if (desert > 0.5f && RANDOM.nextFloat() < 0.6f) {
            origin = new Vec3(origin.x, Math.min(origin.y, ground + 0.3 + RANDOM.nextDouble() * 1.6), origin.z);
        }
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
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            renderSunHalo(event);
            return;
        }
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
            float sand = desert;
            gg = Mth.lerp(sand, gg, 0.74f - 0.1f * g.warm());
            b = Mth.lerp(sand, b, 0.46f - 0.12f * g.warm());
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

    // Where the sun is: straight up at noon, rising in the east (+x), setting in the west (as the sky draws it).
    private static Vec3 sunDir(Minecraft mc, float partial) {
        float a = mc.level.getSunAngle(partial);
        return new Vec3(-Mth.sin(a), Mth.cos(a), 0);
    }

    // How strong the tropical sun is: in the Mar do Sul, by day, fading as it sets.
    private static float sunPower(Minecraft mc, float partial, Vec3 sun) {
        float s = Mth.lerp(partial, lastSea, sea) * Mth.lerp(partial, lastStrength, strength);
        return s * Mth.clamp((float) sun.y * 3 + 0.25f, 0, 1);
    }

    // The halo of the strong sun: soft rings around it, a bright core fading to nothing far out, golden white at noon
    // and orange when it's low. Drawn right after the sky, so the land and the sea cover it.
    private static void renderSunHalo(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 sun = sunDir(mc, partial);
        float power = sunPower(mc, partial, sun);
        if (power <= 0.001f || mc.level.isRaining()) {
            return;
        }
        Vec3 up = Math.abs(sun.z) < 0.9 ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        Vec3 right = sun.cross(up).normalize();
        Vec3 down = sun.cross(right).normalize();
        Vec3 center = sun.scale(100);
        float low = 1 - Mth.clamp((float) sun.y * 2.5f, 0, 1);
        float r = 1f, g = Mth.lerp(low, 0.95f, 0.72f), b = Mth.lerp(low, 0.74f, 0.42f);
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        int rings = 14, segments = 40;
        double radius = 62;
        for (int i = 0; i < rings; i++) {
            double r0 = radius * Math.pow((double) i / rings, 1.6), r1 = radius * Math.pow((double) (i + 1) / rings, 1.6);
            float a0 = power * glow((float) i / rings), a1 = power * glow((float) (i + 1) / rings);
            for (int j = 0; j < segments; j++) {
                double t0 = j * Mth.TWO_PI / segments, t1 = (j + 1) * Mth.TWO_PI / segments;
                vertex(out, last, center, right, down, r0, t0, r, g, b, a0);
                vertex(out, last, center, right, down, r1, t0, r, g, b, a1);
                vertex(out, last, center, right, down, r1, t1, r, g, b, a1);
                vertex(out, last, center, right, down, r0, t1, r, g, b, a0);
            }
        }
        buffers.endBatch(FxRenderTypes.AREA);
    }

    // The halo's brightness from its middle (0) to its rim (1).
    private static float glow(float k) {
        return 0.55f * (float) Math.pow(1 - k, 2.4) + 0.25f * (float) Math.exp(-k * k * 60);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose last, Vec3 center, Vec3 right, Vec3 down, double radius,
            double angle, float r, float g, float b, float a) {
        double c = Math.cos(angle) * radius, s = Math.sin(angle) * radius;
        out.addVertex(last, (float) (center.x + right.x * c + down.x * s), (float) (center.y + right.y * c + down.y * s),
                (float) (center.z + right.z * c + down.z * s)).setColor(r, g, b, a);
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
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float dry = Mth.lerp(partial, lastDesert, desert);
        float sunny = Mth.lerp(partial, lastSea, sea);
        int wash = mix(mix(mix(0xFF9A30, 0xC8EC6A, green), 0xFF5E1A, dry), 0xFFE07A, sunny);
        int edge = mix(mix(mix(0xB0480E, 0x4E8A20, green), 0x7A2006, dry), 0xC88A30, sunny);
        graphics.fill(0, 0, w, h, argb((0.07f - 0.015f * green + 0.035f * dry - 0.025f * sunny) * fade, wash));
        // The glare of the tropical sun when the player looks toward it.
        Minecraft mc = Minecraft.getInstance();
        if (sunny > 0.001f && mc.level != null && mc.player != null && !mc.level.isRaining()) {
            Vec3 sun = sunDir(mc, partial);
            float look = (float) mc.player.getViewVector(partial).dot(sun);
            float glare = sunPower(mc, partial, sun) * Mth.clamp((look - 0.55f) / 0.45f, 0, 1);
            if (glare > 0.001f) {
                graphics.fill(0, 0, w, h, argb(0.2f * glare * glare, 0xFFF4D0));
            }
        }
        // Vignette: burnt orange getting stronger toward the edges, in thin bands.
        int bands = 18;
        int depthX = Math.max(1, w / 5), depthY = Math.max(1, h / 4);
        for (int i = 0; i < bands; i++) {
            float k = 1 - (float) i / bands;
            int color = argb((0.022f - 0.006f * green + 0.014f * dry - 0.01f * sunny) * k * k * fade, edge);
            int x = depthX * i / bands, y = depthY * i / bands;
            int x2 = depthX * (i + 1) / bands, y2 = depthY * (i + 1) / bands;
            graphics.fill(x, 0, x2, h, color);
            graphics.fill(w - x2, 0, w - x, h, color);
            graphics.fill(0, y, w, y2, color);
            graphics.fill(0, h - y2, w, h - y, color);
        }
    }

    // The sand haze of the desert: the fog comes closer (not thick: the far hills still show) and takes the dust's color,
    // darker at night with the sky.
    public static void onFog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
        float d = (float) Mth.lerp(event.getPartialTick(), lastDesert, desert) * strength;
        if (d <= 0.001f || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float far = event.getFarPlaneDistance();
        event.setNearPlaneDistance(Mth.lerp(d, event.getNearPlaneDistance(), 0));
        event.setFarPlaneDistance(Mth.lerp(d, far, Math.max(56, far * 0.42f)));
        event.setCanceled(true);
    }

    public static void onFogColor(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        float d = (float) Mth.lerp(event.getPartialTick(), lastDesert, desert) * strength;
        if (d <= 0.001f) {
            return;
        }
        float lum = Mth.clamp((event.getRed() + event.getGreen() + event.getBlue()) / 3f / 0.7f, 0.08f, 1.2f);
        float k = 0.6f * d;
        event.setRed(Mth.lerp(k, event.getRed(), 0.88f * lum));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.56f * lum));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.30f * lum));
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
