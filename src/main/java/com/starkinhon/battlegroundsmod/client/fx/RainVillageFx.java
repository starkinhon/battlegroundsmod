package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The rain of the Vila da Chuva (Stark 2026-11-11, from the approved preview): it rains without end there, whatever the
// world's weather, fading in when the player walks into the biome and out when he leaves. The preview's numbers:
// - thin streaks falling at 24-34 blocks a second, slanted by the wind, around the player; they stop at the ground;
// - splashes on the ground and the water (the game's rain splash), and the rain's sound;
// - two thin sheets of haze over the ground (1.6 and 4 blocks), drifting;
// - now and then lightning: the sky and the screen flash two or three times and the thunder comes a moment later;
// - the fog from 18 to 150 blocks.
public final class RainVillageFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WX = 0.22, WZ = 0.08;
    private static final int DROPS = 2600;
    private static final double AREA = 28;
    private static final int CELLS = 32;
    private static final int SIZE = 3;
    private static final float[][] HAZE = {{1.6f, 0.22f}, {4.0f, 0.12f}};

    private static float strength;
    private static float lastStrength;
    private static float flash;
    private static float lastFlash;
    private static long nextBolt;
    private static long thunderAt = -1;

    private static final double[][] DROP = new double[DROPS][];
    // x, y, z, speed, ground, last x, last y, last z
    private static final float[][] GROUND = new float[CELLS + 1][CELLS + 1];
    private static int groundX = Integer.MIN_VALUE, groundZ;
    private static long groundAt;

    private RainVillageFx() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        lastFlash = flash;
        for (double[] d : DROP) {
            if (d != null) {
                d[5] = d[0];
                d[6] = d[1];
                d[7] = d[2];
            }
        }
        if (mc.level == null || mc.player == null) {
            strength = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        boolean inside = BiomeAura.in("vila_da_chuva", b -> b.is(ModWorldgen.VILA_DA_CHUVA));
        strength = Mth.clamp(strength + (inside ? 0.015f : -0.02f), 0, 1);
        flash = Math.max(0, flash - 0.13f);
        if (strength <= 0) {
            groundX = Integer.MIN_VALUE;
            return;
        }
        long time = level.getGameTime();
        Vec3 p = mc.player.position();
        int bx = Mth.floor(p.x / SIZE) * SIZE - CELLS * SIZE / 2, bz = Mth.floor(p.z / SIZE) * SIZE - CELLS * SIZE / 2;
        if (bx != groundX || bz != groundZ || time - groundAt > 40) {
            for (int i = 0; i <= CELLS; i++) {
                for (int j = 0; j <= CELLS; j++) {
                    GROUND[i][j] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx + i * SIZE, bz + j * SIZE);
                }
            }
            groundX = bx;
            groundZ = bz;
            groundAt = time;
        }
        // the streaks
        double dt = 0.05;
        int active = (int) (DROPS * strength);
        for (int i = 0; i < DROPS; i++) {
            double[] d = DROP[i];
            if (i >= active) {
                DROP[i] = null;
                continue;
            }
            if (d == null) {
                d = DROP[i] = new double[8];
                spawn(level, d, p, true);
            }
            double sp = d[3];
            d[1] -= sp * dt;
            d[0] += WX * sp * dt;
            d[2] += WZ * sp * dt;
            if (d[1] < d[4] || Math.abs(d[0] - p.x) > AREA || Math.abs(d[2] - p.z) > AREA || d[1] > p.y + 40) {
                spawn(level, d, p, false);
            }
        }
        // splashes on the ground and the water, and the rain's sound
        int splashes = (int) (40 * strength);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < splashes; i++) {
            int x = Mth.floor(p.x + (RANDOM.nextDouble() - 0.5) * 32), z = Mth.floor(p.z + (RANDOM.nextDouble() - 0.5) * 32);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(y - p.y) > 24) {
                continue;
            }
            pos.set(x, y - 1, z);
            double top = level.getBlockState(pos).getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
            if (!level.getFluidState(pos).isEmpty()) {
                top = level.getFluidState(pos).getHeight(level, pos);
            }
            level.addParticle(ParticleTypes.RAIN, x + RANDOM.nextDouble(), y - 1 + Math.max(0, top) + 0.02, z + RANDOM.nextDouble(), 0, 0, 0);
        }
        if (time % 5 == 0 && RANDOM.nextFloat() < strength) {
            int x = Mth.floor(p.x + (RANDOM.nextDouble() - 0.5) * 16), z = Mth.floor(p.z + (RANDOM.nextDouble() - 0.5) * 16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            boolean above = y > p.y + 1;
            level.playLocalSound(x + 0.5, y, z + 0.5, above ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN, SoundSource.WEATHER,
                    (above ? 0.1f : 0.2f) * strength, above ? 0.5f : 1.0f, false);
        }
        // lightning now and then (every 30-60 s), its thunder a moment later
        if (nextBolt == 0 || nextBolt - time > 2400) {
            nextBolt = time + 600 + RANDOM.nextInt(600);
        }
        if (time >= nextBolt && strength > 0.5f) {
            flash = 1;
            nextBolt = time + 600 + RANDOM.nextInt(600);
            thunderAt = time + 15 + RANDOM.nextInt(45);
        }
        if (thunderAt >= 0 && time >= thunderAt) {
            thunderAt = -1;
            level.playLocalSound(p.x + (RANDOM.nextDouble() - 0.5) * 80, p.y + 30, p.z + (RANDOM.nextDouble() - 0.5) * 80,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.7f * strength, 0.8f + RANDOM.nextFloat() * 0.2f, false);
        }
    }

    private static void spawn(ClientLevel level, double[] d, Vec3 p, boolean anywhere) {
        d[0] = p.x + (RANDOM.nextDouble() - 0.5) * AREA * 2;
        d[2] = p.z + (RANDOM.nextDouble() - 0.5) * AREA * 2;
        d[1] = p.y + (anywhere ? RANDOM.nextDouble() * 34 - 6 : 26 + RANDOM.nextDouble() * 6);
        d[3] = 24 + RANDOM.nextDouble() * 10;
        d[4] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(d[0]), Mth.floor(d[2]));
        d[5] = d[0];
        d[6] = d[1];
        d[7] = d[2];
    }

    // The flash: the bolt flickers (two or three strokes) as it fades.
    private static float flashAt(float partial) {
        float f = Mth.lerp(partial, lastFlash, flash);
        if (f <= 0) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        double t = mc.level == null ? 0 : (mc.level.getGameTime() + partial) * 3.0;
        return f * (float) (0.6 + 0.4 * Math.sin(t));
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
        float fl = flashAt(partial);
        float light = Math.min(1, 0.35f + 0.65f * mc.level.getSkyDarken(partial) + fl * 0.8f);
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        renderHaze(out, last, camera, (mc.level.getGameTime() + partial) / 20.0, fade, light);
        renderDrops(out, last, camera, partial, fade, light);
        buffers.endBatch(FxRenderTypes.AREA);
    }

    // A streak: a thin ribbon along the fall, its width turned to the camera; the head brighter than the tail.
    private static void renderDrops(VertexConsumer out, PoseStack.Pose last, Vec3 camera, float partial, float fade, float light) {
        // #aab6bf
        float r = 0.667f * light, g = 0.714f * light, b = 0.749f * light;
        double l = Math.sqrt(WX * WX + 1 + WZ * WZ), ux = WX / l, uy = -1 / l, uz = WZ / l;
        for (double[] d : DROP) {
            if (d == null) {
                continue;
            }
            double x = Mth.lerp(partial, d[5], d[0]), y = Mth.lerp(partial, d[6], d[1]), z = Mth.lerp(partial, d[7], d[2]);
            if (y < d[4]) {
                continue;
            }
            double cx = camera.x - x, cy = camera.y - y, cz = camera.z - z;
            double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
            float a = (float) (0.45 * fade * Mth.clamp((AREA - dist) / 10, 0, 1) * Mth.clamp((dist - 0.8) / 2, 0, 1));
            if (a <= 0.003f) {
                continue;
            }
            double len = 1.1 * (1.5 + d[3] / 30) * 0.5;
            // the side: across the fall and the line of sight
            double sx = uy * cz - uz * cy, sy = uz * cx - ux * cz, sz = ux * cy - uy * cx;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6) {
                continue;
            }
            double wd = 0.022 * (1 + dist * 0.02) / sl;
            sx *= wd;
            sy *= wd;
            sz *= wd;
            float hx = (float) (x - camera.x), hy = (float) (y - camera.y), hz = (float) (z - camera.z);
            float tx = (float) (hx - ux * len), ty = (float) (hy - uy * len), tz = (float) (hz - uz * len);
            out.addVertex(last, (float) (tx - sx), (float) (ty - sy), (float) (tz - sz)).setColor(r, g, b, a * 0.35f);
            out.addVertex(last, (float) (tx + sx), (float) (ty + sy), (float) (tz + sz)).setColor(r, g, b, a * 0.35f);
            out.addVertex(last, (float) (hx + sx), (float) (hy + sy), (float) (hz + sz)).setColor(r, g, b, a);
            out.addVertex(last, (float) (hx - sx), (float) (hy - sy), (float) (hz - sz)).setColor(r, g, b, a);
        }
    }

    private static double hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0;
    }

    private static double noise(double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz) + (hash(ix + 1, iz) - hash(ix, iz)) * fx;
        double b = hash(ix, iz + 1) + (hash(ix + 1, iz + 1) - hash(ix, iz + 1)) * fx;
        return a + (b - a) * fz;
    }

    // The preview's haze: two octaves drifting slowly, cut to patches, gone close to the camera.
    private static void renderHaze(VertexConsumer out, PoseStack.Pose last, Vec3 camera, double t, float fade, float light) {
        if (groundX == Integer.MIN_VALUE) {
            return;
        }
        // #7f8a90
        float r = 0.498f * light, g = 0.541f * light, b = 0.565f * light;
        double half = CELLS * SIZE / 2.0, mx = groundX + half, mz = groundZ + half;
        float[][] alpha = new float[CELLS + 1][CELLS + 1];
        float[][] ys = new float[CELLS + 1][CELLS + 1];
        for (int layer = 0; layer < HAZE.length; layer++) {
            for (int i = 0; i <= CELLS; i++) {
                for (int j = 0; j <= CELLS; j++) {
                    double x = groundX + i * SIZE, z = groundZ + j * SIZE, y = GROUND[i][j] + HAZE[layer][0];
                    double qx = x * 0.05 + layer * 3.1 + t * 0.03, qz = z * 0.05 + layer * 1.7 + t * 0.012;
                    double m = noise(qx, qz) * 0.6 + noise(qx * 2.4, qz * 2.4) * 0.4;
                    double k = Mth.clamp((m - 0.35) / 0.45, 0, 1);
                    k = k * k * (3 - 2 * k);
                    double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z, ex = x - mx, ez = z - mz;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double near = Mth.clamp((d - 2) / 10, 0, 1), far = Mth.clamp((half - Math.sqrt(ex * ex + ez * ez)) / (half * 0.35), 0, 1);
                    alpha[i][j] = (float) (HAZE[layer][1] * k * near * far) * fade;
                    ys[i][j] = (float) (y - camera.y);
                }
            }
            for (int i = 0; i < CELLS; i++) {
                for (int j = 0; j < CELLS; j++) {
                    if (alpha[i][j] + alpha[i + 1][j] + alpha[i + 1][j + 1] + alpha[i][j + 1] < 0.004f) {
                        continue;
                    }
                    float x0 = (float) (groundX + i * SIZE - camera.x), x1 = x0 + SIZE;
                    float z0 = (float) (groundZ + j * SIZE - camera.z), z1 = z0 + SIZE;
                    out.addVertex(last, x0, ys[i][j], z0).setColor(r, g, b, alpha[i][j]);
                    out.addVertex(last, x1, ys[i + 1][j], z0).setColor(r, g, b, alpha[i + 1][j]);
                    out.addVertex(last, x1, ys[i + 1][j + 1], z1).setColor(r, g, b, alpha[i + 1][j + 1]);
                    out.addVertex(last, x0, ys[i][j + 1], z1).setColor(r, g, b, alpha[i][j + 1]);
                }
            }
        }
    }

    // The grey of the rain over the screen, and the lightning's flash.
    public static void renderGrade(GuiGraphics graphics, DeltaTracker delta) {
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        int alpha = Mth.clamp((int) (255 * 0.1 * fade), 0, 255);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0x3A4248);
        float fl = flashAt(partial);
        if (fl > 0.01f) {
            int fa = Mth.clamp((int) (255 * 0.32 * fl * fade), 0, 255);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (fa << 24) | 0xD8DEE8);
        }
    }

    // The preview's fog: 18 to 150 blocks; the flash pushes it back for a moment.
    public static void onFog(ViewportEvent.RenderFog event) {
        float k = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        Minecraft mc = Minecraft.getInstance();
        if (k <= 0.001f || mc.level == null || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float fl = flashAt((float) event.getPartialTick());
        float far = event.getFarPlaneDistance(), near = event.getNearPlaneDistance();
        float rainFar = Math.min(far, 150 + 60 * fl), rainNear = Math.min(near, 18 + 20 * fl);
        event.setFarPlaneDistance(Mth.lerp(k, far, rainFar));
        event.setNearPlaneDistance(Mth.lerp(k, near, rainNear));
        event.setCanceled(true);
    }
}
