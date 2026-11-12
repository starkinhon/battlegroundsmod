package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// The waterfall of the Vale do Fim (Stark 2026-11-12, from the approved preview): where the falling water hits the lake,
// the preview's two kinds of particles and the roar of the fall.
// - the mist: 320 soft round puffs (#d3dcdf), 8-18 blocks growing to 1.8x as they age, born 1-8 blocks downstream of
//   the foot of the fall, drifting downstream and rising slowly, living 6-13 s, at most 0.07 opaque;
// - the spray: 600 small drops (#eef3f4) thrown up 3-10 blocks a second from the foot and falling back, 0.7-1.6 s;
// - the roar: the rain's sound, deep, from the foot of the fall nearest to the player.
// The feet of the falls are found by looking, every 2 s while the player is in the biome, at the columns around him whose
// top is falling water.
public final class EndValleyFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int MIST = 320, SPRAY = 600, RADIUS = 72, SEG = 8;

    private static final List<double[]> FEET = new ArrayList<>();
    private static double flowX = 1, flowZ;
    private static long scannedAt = Long.MIN_VALUE;
    private static float strength, lastStrength;

    // x, y, z, vx, vy, vz, life, age, size, last x, last y, last z
    private static final double[][] MISTS = new double[MIST][];
    private static final double[][] SPRAYS = new double[SPRAY][];

    private EndValleyFx() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            strength = 0;
            FEET.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        long time = level.getGameTime();
        boolean inside = BiomeAura.in("vale_do_fim", b -> b.is(ModWorldgen.VALE_DO_FIM));
        if (inside && (time - scannedAt > 40 || time < scannedAt)) {
            scan(level, mc.player.blockPosition());
            scannedAt = time;
        } else if (!inside) {
            scannedAt = Long.MIN_VALUE;
        }
        strength = Mth.clamp(strength + (inside && !FEET.isEmpty() ? 0.02f : -0.02f), 0, 1);
        if (strength <= 0) {
            FEET.clear();
            return;
        }
        double dt = 0.05;
        for (int i = 0; i < MIST; i++) {
            double[] m = MISTS[i];
            if (m == null) {
                m = MISTS[i] = new double[12];
                emitMist(m);
                m[7] = RANDOM.nextDouble() * m[6];
            }
            m[9] = m[0];
            m[10] = m[1];
            m[11] = m[2];
            m[7] += dt;
            if (m[7] >= m[6]) {
                emitMist(m);
            }
            m[0] += m[3] * dt;
            m[1] += m[4] * dt;
            m[2] += m[5] * dt;
            m[4] *= 1 - 0.15 * dt;
        }
        for (int i = 0; i < SPRAY; i++) {
            double[] p = SPRAYS[i];
            if (p == null) {
                p = SPRAYS[i] = new double[12];
                emitSpray(p);
                p[7] = RANDOM.nextDouble() * p[6];
            }
            p[9] = p[0];
            p[10] = p[1];
            p[11] = p[2];
            p[7] += dt;
            if (p[7] >= p[6]) {
                emitSpray(p);
            }
            p[4] -= 16 * dt;
            p[0] += p[3] * dt;
            p[1] += p[4] * dt;
            p[2] += p[5] * dt;
        }
        // the roar from the nearest foot
        if (time % 4 == 0 && !FEET.isEmpty()) {
            Vec3 pp = mc.player.position();
            double[] best = null;
            double bd = Double.MAX_VALUE;
            for (double[] f : FEET) {
                double d = sq(f[0] - pp.x) + sq(f[1] - pp.y) + sq(f[2] - pp.z);
                if (d < bd) {
                    bd = d;
                    best = f;
                }
            }
            if (best != null && bd < 90 * 90) {
                float near = (float) Mth.clamp(1 - Math.sqrt(bd) / 90, 0, 1);
                level.playLocalSound(best[0], best[1], best[2], SoundEvents.WEATHER_RAIN, SoundSource.AMBIENT,
                        (0.6f + 2.4f * near) * strength, 0.45f + RANDOM.nextFloat() * 0.1f, false);
            }
        }
    }

    private static double sq(double a) {
        return a * a;
    }

    // The columns whose top is falling water: walk down the fall to the lake; its foot is where the fall meets the water.
    private static void scan(ClientLevel level, BlockPos center) {
        FEET.clear();
        double fx = 0, fz = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - RADIUS; x <= center.getX() + RADIUS; x++) {
            for (int z = center.getZ() - RADIUS; z <= center.getZ() + RADIUS; z++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                FluidState f = level.getFluidState(pos.set(x, top, z));
                if (!f.is(Fluids.FLOWING_WATER)) {
                    continue;
                }
                FluidState below = level.getFluidState(pos.set(x, top - 1, z));
                if (!below.is(Fluids.FLOWING_WATER) || !below.getValue(FlowingFluid.FALLING)) {
                    continue;
                }
                int y = top - 1;
                while (y > top - 60) {
                    FluidState s = level.getFluidState(pos.set(x, y, z));
                    if (s.isSource() || !s.is(Fluids.FLOWING_WATER)) {
                        break;
                    }
                    y--;
                }
                if (top - y < 4 || !level.getFluidState(pos.set(x, y, z)).isSource()) {
                    continue;
                }
                FEET.add(new double[] {x + 0.5, y + 1.0, z + 0.5});
                // the water comes from the upper river beside the top of the fall, and runs on away from it
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (level.getFluidState(pos.set(x + d[0], top, z + d[1])).isSource()) {
                        fx -= d[0];
                        fz -= d[1];
                    }
                }
            }
        }
        double l = Math.sqrt(fx * fx + fz * fz);
        if (l > 1e-6) {
            flowX = fx / l;
            flowZ = fz / l;
        }
    }

    private static void emitMist(double[] m) {
        m[6] = 6 + RANDOM.nextDouble() * 7;
        m[7] = 0;
        if (FEET.isEmpty()) {
            m[1] = -9999;
            m[9] = m[0];
            m[10] = m[1];
            m[11] = m[2];
            return;
        }
        double[] f = FEET.get(RANDOM.nextInt(FEET.size()));
        double off = 1 + RANDOM.nextDouble() * 7;
        m[0] = f[0] + (RANDOM.nextDouble() - 0.5) * 5 + flowX * off;
        m[2] = f[2] + (RANDOM.nextDouble() - 0.5) * 5 + flowZ * off;
        m[1] = f[1] + RANDOM.nextDouble() * 3;
        double sp = 1 + RANDOM.nextDouble() * 2.5;
        m[3] = flowX * sp + (RANDOM.nextDouble() - 0.5) * 4;
        m[5] = flowZ * sp + (RANDOM.nextDouble() - 0.5) * 4;
        m[4] = 0.6 + RANDOM.nextDouble() * 1.6;
        m[8] = 8 + RANDOM.nextDouble() * 10;
        m[9] = m[0];
        m[10] = m[1];
        m[11] = m[2];
    }

    private static void emitSpray(double[] p) {
        p[6] = 0.7 + RANDOM.nextDouble() * 0.9;
        p[7] = 0;
        if (FEET.isEmpty()) {
            p[1] = -9999;
            p[9] = p[0];
            p[10] = p[1];
            p[11] = p[2];
            return;
        }
        double[] f = FEET.get(RANDOM.nextInt(FEET.size()));
        p[0] = f[0];
        p[2] = f[2];
        p[1] = f[1] + 0.5;
        double a = RANDOM.nextDouble() * Math.PI * 2, sp = 2 + RANDOM.nextDouble() * 6;
        p[3] = Math.cos(a) * sp * 0.6 + flowX * sp * 0.6;
        p[5] = Math.sin(a) * sp * 0.6 + flowZ * sp * 0.6;
        p[4] = 3 + RANDOM.nextDouble() * 7;
        p[8] = 0.5 + RANDOM.nextDouble() * 0.2;
        p[9] = p[0];
        p[10] = p[1];
        p[11] = p[2];
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
        float light = Math.min(1, 0.3f + 0.7f * mc.level.getSkyDarken(partial));
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        // the billboard's axes: the camera's right and up
        org.joml.Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        double rx = -left.x(), ry = -left.y(), rz = -left.z(), ux = up.x(), uy = up.y(), uz = up.z();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        // #d3dcdf, the soft puffs: a round fan, opaque in the middle and gone at the edge
        float mr = 0.827f * light, mg = 0.863f * light, mb = 0.875f * light;
        for (double[] m : MISTS) {
            if (m == null || m[1] < -9000) {
                continue;
            }
            double t = m[7] / m[6];
            float a = (float) (Math.sin(Math.PI * t) * 0.07 * 0.55) * fade;
            double size = m[8] * (0.6 + t * 1.2);
            puff(out, last, camera, lerp(partial, m[9], m[0]), lerp(partial, m[10], m[1]), lerp(partial, m[11], m[2]), size / 2,
                    rx, ry, rz, ux, uy, uz, mr, mg, mb, a, 0);
        }
        // #eef3f4, the spray: small drops, hard in the middle
        float sr = 0.933f * light, sg = 0.953f * light, sb = 0.957f * light;
        for (double[] p : SPRAYS) {
            if (p == null || p[1] < -9000) {
                continue;
            }
            float a = (float) ((1 - p[7] / p[6]) * 0.4) * fade;
            puff(out, last, camera, lerp(partial, p[9], p[0]), lerp(partial, p[10], p[1]), lerp(partial, p[11], p[2]), p[8] / 2,
                    rx, ry, rz, ux, uy, uz, sr, sg, sb, a, 0.5f);
        }
        buffers.endBatch(FxRenderTypes.AREA);
    }

    private static double lerp(float t, double a, double b) {
        return a + (b - a) * t;
    }

    // A round sprite turned to the camera, as a fan of SEG triangles (quads with two corners the same): alpha a in the
    // middle, a * edge at the rim.
    private static void puff(VertexConsumer out, PoseStack.Pose last, Vec3 camera, double x, double y, double z, double r,
            double rx, double ry, double rz, double ux, double uy, double uz, float cr, float cg, float cb, float a, float edge) {
        if (a <= 0.002f) {
            return;
        }
        double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // fade out the ones the camera is inside of, and the far ones
        float k = (float) (Mth.clamp((dist - r * 0.6) / Math.max(1, r), 0, 1) * Mth.clamp((160 - dist) / 40, 0, 1));
        a *= k;
        if (a <= 0.002f) {
            return;
        }
        float cx = (float) dx, cy = (float) dy, cz = (float) dz;
        float ae = a * edge;
        for (int i = 0; i < SEG; i++) {
            double a0 = i * Math.PI * 2 / SEG, a1 = (i + 1) * Math.PI * 2 / SEG;
            double c0 = Math.cos(a0) * r, s0 = Math.sin(a0) * r, c1 = Math.cos(a1) * r, s1 = Math.sin(a1) * r;
            out.addVertex(last, cx, cy, cz).setColor(cr, cg, cb, a);
            out.addVertex(last, cx, cy, cz).setColor(cr, cg, cb, a);
            out.addVertex(last, (float) (dx + rx * c0 + ux * s0), (float) (dy + ry * c0 + uy * s0), (float) (dz + rz * c0 + uz * s0))
                    .setColor(cr, cg, cb, ae);
            out.addVertex(last, (float) (dx + rx * c1 + ux * s1), (float) (dy + ry * c1 + uy * s1), (float) (dz + rz * c1 + uz * s1))
                    .setColor(cr, cg, cb, ae);
        }
    }
}
