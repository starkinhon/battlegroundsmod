package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// The air of the Monte Myoboku (Stark 2026-11-12, from the approved preview): golden pollen (#fff2b0) floating slowly
// around the player, 0 to 14 blocks over the ground, swaying and drifting with a light wind, twinkling; it fades in when
// the player walks into the biome and out when he leaves.
public final class MyobokuFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int MOTES = 700;
    private static final double AREA = 34;

    // x, y, z, phase, speed, last x, last y, last z
    private static final double[][] MOTE = new double[MOTES][];
    private static float strength, lastStrength;

    private MyobokuFx() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            strength = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        boolean inside = level.getBiome(mc.player.blockPosition()).is(ModWorldgen.MONTE_MYOBOKU);
        strength = Mth.clamp(strength + (inside ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            return;
        }
        Vec3 p = mc.player.position();
        double dt = 0.05;
        for (int i = 0; i < MOTES; i++) {
            double[] m = MOTE[i];
            if (m == null) {
                m = MOTE[i] = new double[8];
                m[3] = RANDOM.nextDouble() * 6.283;
                m[4] = 0.3 + RANDOM.nextDouble() * 0.6;
                spawn(level, m, p);
            }
            m[5] = m[0];
            m[6] = m[1];
            m[7] = m[2];
            m[3] += dt * m[4];
            m[0] += Math.cos(m[3] * 0.7) * 0.4 * dt + 0.25 * dt;
            m[1] += Math.sin(m[3]) * 0.3 * dt;
            m[2] += Math.sin(m[3] * 0.5) * 0.4 * dt;
            if (Math.abs(m[0] - p.x) > AREA || Math.abs(m[2] - p.z) > AREA || Math.abs(m[1] - p.y) > 30) {
                spawn(level, m, p);
            }
        }
    }

    private static void spawn(ClientLevel level, double[] m, Vec3 p) {
        m[0] = p.x + (RANDOM.nextDouble() - 0.5) * AREA * 2;
        m[2] = p.z + (RANDOM.nextDouble() - 0.5) * AREA * 2;
        m[1] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(m[0]), Mth.floor(m[2])) + 0.5 + RANDOM.nextDouble() * 14;
        m[5] = m[0];
        m[6] = m[1];
        m[7] = m[2];
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        // pollen glows a little: dimmer at night, but never gone
        float light = Math.min(1, 0.5f + 0.5f * mc.level.getSkyDarken(partial));
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        org.joml.Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        double rx = -left.x(), ry = -left.y(), rz = -left.z(), ux = up.x(), uy = up.y(), uz = up.z();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        float r = 1.0f * light, g = 0.949f * light, b = 0.69f * light;
        for (double[] m : MOTE) {
            if (m == null) {
                continue;
            }
            double x = Mth.lerp(partial, m[5], m[0]) - camera.x, y = Mth.lerp(partial, m[6], m[1]) - camera.y,
                    z = Mth.lerp(partial, m[7], m[2]) - camera.z;
            double d = Math.sqrt(x * x + y * y + z * z);
            float a = (float) ((0.55 + 0.45 * Math.sin(m[3] * 2.3)) * Mth.clamp((AREA - d) / 12, 0, 1) * Mth.clamp((d - 0.5) / 1.5, 0, 1))
                    * 0.9f * fade;
            if (a <= 0.01f) {
                continue;
            }
            double s = 0.05 + d * 0.004;
            // a small diamond: bright in the middle, gone at the corners
            out.addVertex(last, (float) x, (float) y, (float) z).setColor(r, g, b, a);
            out.addVertex(last, (float) (x + rx * s), (float) (y + ry * s), (float) (z + rz * s)).setColor(r, g, b, 0);
            out.addVertex(last, (float) (x + ux * s), (float) (y + uy * s), (float) (z + uz * s)).setColor(r, g, b, 0);
            out.addVertex(last, (float) (x - rx * s), (float) (y - ry * s), (float) (z - rz * s)).setColor(r, g, b, 0);
            out.addVertex(last, (float) x, (float) y, (float) z).setColor(r, g, b, a);
            out.addVertex(last, (float) (x - rx * s), (float) (y - ry * s), (float) (z - rz * s)).setColor(r, g, b, 0);
            out.addVertex(last, (float) (x - ux * s), (float) (y - uy * s), (float) (z - uz * s)).setColor(r, g, b, 0);
            out.addVertex(last, (float) (x + rx * s), (float) (y + ry * s), (float) (z + rz * s)).setColor(r, g, b, 0);
        }
        buffers.endBatch(FxRenderTypes.AREA);
    }
}
