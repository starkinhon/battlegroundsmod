package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The air of the Floresta da Folha (Stark 2026-11-11, from the approved preview), fading in when the player walks into
// the biome and out when he leaves:
// - leaves falling from the giants' crowns and carried by the wind in gusts that come and go (the preview's numbers:
//   falling 0.6 to 1.4 blocks a second, swaying, the gust pushing them along +x from 0.6 up to 2.8 blocks a second);
//   each leaf is its own shape, size and green (none copied), drawn as a thin leaf that tumbles as it falls, darker at
//   night with the sky;
// - a warm golden light over the screen and a soft deep-green vignette, the sun through the leaves;
// - the warm haze of the preview: the fog a little closer, so the far trunks fade into gold.
public final class LeafForestFx {
    private static final int MAX_LEAVES = 260;
    private static final double AREA = 30;
    private static final double TOP = 28;

    private static final class Leaf {
        double x, y, z, px, py, pz;
        double spin, speed, len, wide;
        float r, g, b;
    }

    private static final List<Leaf> LEAVES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    // 0 outside, 1 inside the Floresta da Folha; eases in and out over a few seconds.
    private static float strength;
    private static float lastStrength;

    private LeafForestFx() {
    }

    // The wind of the preview: a slow swell that turns into a gust every couple of minutes' worth of seconds.
    private static double gust(double ticks) {
        return 0.6 + Math.max(0, Math.sin(ticks * 50 / 2300.0)) * 2.2;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            LEAVES.clear();
            strength = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        boolean inside = BiomeAura.in("floresta_da_folha", b -> b.is(ModWorldgen.FLORESTA_DA_FOLHA));
        strength = Mth.clamp(strength + (inside ? 0.012f : -0.02f), 0, 1);
        if (strength <= 0) {
            LEAVES.clear();
            return;
        }
        long now = mc.level.getGameTime();
        double wind = gust(now) / 20;
        Vec3 p = mc.player.position();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        LEAVES.removeIf(l -> {
            l.px = l.x;
            l.py = l.y;
            l.pz = l.z;
            l.spin += 0.15;
            l.y -= 1.1 * l.speed / 20;
            l.x += (wind * l.speed + Math.sin(l.spin) * 0.6 / 20);
            l.z += Math.cos(l.spin * 0.7) * 0.5 / 20;
            double dx = l.x - p.x, dz = l.z - p.z;
            if (dx * dx + dz * dz > AREA * AREA * 1.6 || l.y < p.y - 24) {
                return true;
            }
            // it lands: on the ground, on water, on anything that isn't air or leaves
            BlockState state = mc.level.getBlockState(at.set(l.x, l.y, l.z));
            return !state.isAir() && !state.is(BlockTags.LEAVES);
        });
        int want = (int) (MAX_LEAVES * strength);
        // a few new ones a tick, from up in the crowns, more upwind so the wind carries them over the player
        for (int i = 0; i < 6 && LEAVES.size() < want; i++) {
            LEAVES.add(newLeaf(p, LEAVES.size() < want / 3));
        }
    }

    private static Leaf newLeaf(Vec3 p, boolean anyHeight) {
        Leaf l = new Leaf();
        l.x = p.x + (RANDOM.nextDouble() * 2 - 1.3) * AREA;
        l.z = p.z + (RANDOM.nextDouble() * 2 - 1) * AREA;
        l.y = p.y + 2 + (anyHeight ? RANDOM.nextDouble() : 0.5 + RANDOM.nextDouble() * 0.5) * TOP;
        l.px = l.x;
        l.py = l.y;
        l.pz = l.z;
        l.spin = RANDOM.nextDouble() * 10;
        l.speed = 0.6 + RANDOM.nextDouble() * 0.8;
        // 0.35 x 0.22 in the preview, each a little different
        l.len = 0.3 + RANDOM.nextDouble() * 0.14;
        l.wide = 0.17 + RANDOM.nextDouble() * 0.09;
        // #8cbf4a, from fresh light green to a few gone yellow
        float age = RANDOM.nextFloat();
        float yellow = RANDOM.nextFloat() < 0.18f ? 0.4f + 0.5f * RANDOM.nextFloat() : 0;
        l.r = Mth.clamp(0.55f - 0.1f * age + 0.35f * yellow, 0, 1);
        l.g = Mth.clamp(0.75f - 0.08f * age + 0.05f * yellow, 0, 1);
        l.b = Mth.clamp(0.29f - 0.06f * age - 0.1f * yellow, 0, 1);
        return l;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || LEAVES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partial, lastStrength, strength);
        float light = mc.level.getSkyDarken(partial);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        for (Leaf l : LEAVES) {
            double x = Mth.lerp(partial, l.px, l.x) - camera.x;
            double y = Mth.lerp(partial, l.py, l.y) - camera.y;
            double z = Mth.lerp(partial, l.pz, l.z) - camera.z;
            double d2 = x * x + y * y + z * z;
            if (d2 > 48 * 48) {
                continue;
            }
            // the tumble of the preview: turned by spin, spin * 0.6 and spin * 0.3 around the three axes
            double s = l.spin + 0.15 * partial;
            Vec3 along = rotate(new Vec3(l.len / 2, 0, 0), s, s * 0.6, s * 0.3);
            Vec3 across = rotate(new Vec3(0, l.wide / 2, 0), s, s * 0.6, s * 0.3);
            // the face turned to the sky is lit, the edge-on and the underside a bit darker
            float shade = (float) (0.72 + 0.28 * Math.abs(along.cross(across).normalize().y));
            float k = shade * light;
            float a = fade * Mth.clamp((float) (1 - Math.sqrt(d2) / 48), 0, 1) * 0.95f;
            // a leaf, not a card: pointed at both ends, widest a bit toward the stem
            Vec3 tip = new Vec3(x, y, z).add(along);
            Vec3 stem = new Vec3(x, y, z).subtract(along.scale(0.8));
            Vec3 side1 = new Vec3(x, y, z).subtract(along.scale(0.15)).add(across);
            Vec3 side2 = new Vec3(x, y, z).subtract(along.scale(0.15)).subtract(across);
            put(out, last, tip, l.r * k, l.g * k, l.b * k, a);
            put(out, last, side1, l.r * k, l.g * k, l.b * k, a);
            put(out, last, stem, l.r * k * 0.9f, l.g * k * 0.9f, l.b * k * 0.9f, a);
            put(out, last, side2, l.r * k, l.g * k, l.b * k, a);
        }
        buffers.endBatch(FxRenderTypes.AREA);
    }

    private static void put(VertexConsumer out, PoseStack.Pose last, Vec3 v, float r, float g, float b, float a) {
        out.addVertex(last, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, b, a);
    }

    // three.js's default Euler order (XYZ, v' = Rx Ry Rz v): the z turn first, then y, then x.
    private static Vec3 rotate(Vec3 v, double ax, double ay, double az) {
        return v.zRot((float) az).yRot((float) ay).xRot((float) ax);
    }

    // The sun through the leaves: a light golden wash, and a soft deep green toward the screen's edges.
    public static void renderGrade(GuiGraphics graphics, DeltaTracker delta) {
        float fade = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastStrength, strength) * BiomeAura.nightDim(delta.getGameTimeDeltaPartialTick(false));
        if (fade <= 0.001f) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        graphics.fill(0, 0, w, h, argb(0.055f * fade, 0xFFD27A));
        int bands = 18;
        int depthX = Math.max(1, w / 5), depthY = Math.max(1, h / 4);
        for (int i = 0; i < bands; i++) {
            float k = 1 - (float) i / bands;
            int color = argb(0.024f * k * k * fade, 0x2A4A12);
            int x = depthX * i / bands, y = depthY * i / bands;
            int x2 = depthX * (i + 1) / bands, y2 = depthY * (i + 1) / bands;
            graphics.fill(x, 0, x2, h, color);
            graphics.fill(w - x2, 0, w - x, h, color);
            graphics.fill(0, y, w, y2, color);
            graphics.fill(0, h - y2, w, h - y, color);
        }
    }

    // The preview's haze (fog from 70 to 230 blocks): closer than vanilla's, never thick. Its color is the biome's own
    // fog color, a warm pale gold.
    public static void onFog(ViewportEvent.RenderFog event) {
        float k = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (k <= 0.001f || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float near = event.getNearPlaneDistance();
        float hazeFar = Math.max(110, far * 0.85f);
        if (hazeFar >= far) {
            return;
        }
        event.setFarPlaneDistance(Mth.lerp(k, far, hazeFar));
        event.setNearPlaneDistance(Mth.lerp(k, near, hazeFar * 0.3f));
        event.setCanceled(true);
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | rgb;
    }
}
