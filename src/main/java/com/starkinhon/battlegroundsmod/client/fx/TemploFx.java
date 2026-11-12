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

// The air of the Templos Antigos (Stark 2026-11-12: "algumas pétalas de sakura flutuando... mais vivo, laranja,
// rosado"; the approved preview has them falling from the sakuras): sakura petals drifting all around the player, from
// the ground up to 20 blocks, falling slowly, carried by a wind that comes and goes in gusts, each one tumbling on its
// own axis so its face catches the light and turns edge-on. Every petal has its own size, pink (the preview's three
// tones and the almost white), spin and sway. They fade in when the player walks into the biome and out when he leaves,
// and darken with the night.
public final class TemploFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int PETALS = 520;
    private static final double AREA = 30;
    private static final float[][] TONES = {{0.949f, 0.722f, 0.784f}, {0.910f, 0.604f, 0.690f}, {0.965f, 0.804f, 0.847f},
            {0.984f, 0.918f, 0.941f}};

    // x, y, z, last x, last y, last z, spin angle, spin speed, axis x, axis y, axis z, size, tone, sway phase, last angle
    private static final double[][] PETAL = new double[PETALS][];
    private static float strength, lastStrength;
    private static double gustPhase;

    private TemploFx() {
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
        boolean inside = BiomeAura.in("templos_antigos", b -> b.is(ModWorldgen.TEMPLOS_ANTIGOS));
        strength = Mth.clamp(strength + (inside ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            return;
        }
        Vec3 p = mc.player.position();
        double dt = 0.05;
        // the wind: a steady breeze with gusts every few seconds, turning slowly
        gustPhase += dt;
        double gust = 0.5 + 0.5 * Math.max(0, Math.sin(gustPhase * 0.45)) * (0.6 + 0.4 * Math.sin(gustPhase * 1.7));
        double windDir = 0.6 + 0.4 * Math.sin(gustPhase * 0.05);
        double wx = Math.cos(windDir) * (0.5 + 1.6 * gust), wz = Math.sin(windDir) * (0.5 + 1.6 * gust);
        for (int i = 0; i < PETALS; i++) {
            double[] m = PETAL[i];
            if (m == null) {
                m = PETAL[i] = new double[15];
                spawn(level, m, p, true);
            }
            m[3] = m[0];
            m[4] = m[1];
            m[5] = m[2];
            m[14] = m[6];
            m[13] += dt * (1.1 + m[11] * 4);
            m[6] += dt * m[7] * (0.7 + gust);
            // falls slowly, faster when edge-on (a petal sails flat and slips edgewise)
            double edge = Math.abs(Math.sin(m[6]));
            m[1] -= dt * (0.35 + 0.5 * edge);
            m[0] += dt * (wx + Math.cos(m[13]) * 0.6);
            m[2] += dt * (wz + Math.sin(m[13] * 0.8) * 0.6);
            boolean far = Math.abs(m[0] - p.x) > AREA || Math.abs(m[2] - p.z) > AREA || m[1] - p.y > 26;
            boolean landed = m[1] < level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(m[0]), Mth.floor(m[2])) + 0.05;
            if (far || landed) {
                spawn(level, m, p, false);
            }
        }
    }

    private static void spawn(ClientLevel level, double[] m, Vec3 p, boolean anywhere) {
        m[0] = p.x + (RANDOM.nextDouble() - 0.5) * AREA * 2;
        m[2] = p.z + (RANDOM.nextDouble() - 0.5) * AREA * 2;
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(m[0]), Mth.floor(m[2]));
        // the first ones fill the air at every height; the new ones come in from up high
        m[1] = Math.max(ground + 0.5, p.y - 4) + (anywhere ? RANDOM.nextDouble() * 20 : 12 + RANDOM.nextDouble() * 10);
        m[3] = m[0];
        m[4] = m[1];
        m[5] = m[2];
        m[6] = RANDOM.nextDouble() * 6.283;
        m[14] = m[6];
        m[7] = (RANDOM.nextBoolean() ? 1 : -1) * (1.5 + RANDOM.nextDouble() * 3.5);
        double ax = RANDOM.nextDouble() - 0.5, ay = RANDOM.nextDouble() - 0.5, az = RANDOM.nextDouble() - 0.5;
        double l = Math.max(1e-3, Math.sqrt(ax * ax + ay * ay + az * az));
        m[8] = ax / l;
        m[9] = ay / l;
        m[10] = az / l;
        m[11] = 0.06 + RANDOM.nextDouble() * 0.05;
        m[12] = RANDOM.nextInt(TONES.length);
        m[13] = RANDOM.nextDouble() * 6.283;
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
        float light = 0.25f + 0.75f * mc.level.getSkyDarken(partial);
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        for (double[] m : PETAL) {
            if (m == null) {
                continue;
            }
            double x = Mth.lerp(partial, m[3], m[0]) - camera.x, y = Mth.lerp(partial, m[4], m[1]) - camera.y,
                    z = Mth.lerp(partial, m[5], m[2]) - camera.z;
            double d = Math.sqrt(x * x + y * y + z * z);
            float a = (float) (Mth.clamp((AREA - d) / 10, 0, 1) * Mth.clamp((d - 0.4) / 1.2, 0, 1)) * 0.95f * fade;
            if (a <= 0.01f) {
                continue;
            }
            // the petal's two half-axes, turned about its own axis by its spin: u along the petal, v across it
            double ang = Mth.lerp(partial, m[14], m[6]);
            double ax = m[8], ay = m[9], az = m[10];
            // a vector not along the axis, made perpendicular to it
            double px = Math.abs(ay) < 0.9 ? 0 : 1, py = Math.abs(ay) < 0.9 ? 1 : 0;
            double e1x = ay * 0 - az * py, e1y = az * px - ax * 0, e1z = ax * py - ay * px;
            double l1 = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
            e1x /= l1;
            e1y /= l1;
            e1z /= l1;
            double e2x = ay * e1z - az * e1y, e2y = az * e1x - ax * e1z, e2z = ax * e1y - ay * e1x;
            double c = Math.cos(ang), s = Math.sin(ang);
            double size = m[11];
            double ux = (e1x * c + e2x * s) * size * 1.3, uy = (e1y * c + e2y * s) * size * 1.3, uz = (e1z * c + e2z * s) * size * 1.3;
            double vx = ax * size * 0.8, vy = ay * size * 0.8, vz = az * size * 0.8;
            // its face toward the light is brighter, the back a little darker
            double facing = Math.abs((e2x * c - e1x * s) * 0.3 + (e2y * c - e1y * s) * 0.9 + (e2z * c - e1z * s) * 0.3);
            float k = (float) (0.78 + 0.22 * facing) * light;
            float[] tone = TONES[(int) m[12]];
            float r = tone[0] * k, g = tone[1] * k, b = tone[2] * k;
            // a rounded diamond: the tips of the petal thin to nothing, both sides drawn
            quad(out, last, x, y, z, ux, uy, uz, vx, vy, vz, r, g, b, a);
        }
        buffers.endBatch(FxRenderTypes.AREA);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose last, double x, double y, double z, double ux, double uy, double uz,
            double vx, double vy, double vz, float r, float g, float b, float a) {
        float tip = a * 0.55f;
        out.addVertex(last, (float) (x + ux), (float) (y + uy), (float) (z + uz)).setColor(r, g, b, tip);
        out.addVertex(last, (float) (x + vx), (float) (y + vy), (float) (z + vz)).setColor(r, g, b, a);
        out.addVertex(last, (float) (x - ux), (float) (y - uy), (float) (z - uz)).setColor(r, g, b, tip);
        out.addVertex(last, (float) (x - vx), (float) (y - vy), (float) (z - vz)).setColor(r, g, b, a);
        out.addVertex(last, (float) (x + ux), (float) (y + uy), (float) (z + uz)).setColor(r, g, b, tip);
        out.addVertex(last, (float) (x - vx), (float) (y - vy), (float) (z - vz)).setColor(r, g, b, a);
        out.addVertex(last, (float) (x - ux), (float) (y - uy), (float) (z - uz)).setColor(r, g, b, tip);
        out.addVertex(last, (float) (x + vx), (float) (y + vy), (float) (z + vz)).setColor(r, g, b, a);
    }
}
