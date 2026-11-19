package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The mist of the Mar da Névoa (Stark 2026-11-11, from the approved preview), fading in when the player goes into the
// biome and out when he leaves. The mist comes and goes: it closes and opens in a slow cycle (about 70 s, with a wobble
// so it isn't regular; mist(), the preview's mistAt). The preview's numbers:
// - six sheets of mist lying over the water (0.3 to 11 blocks over it), a slow drifting pattern, thick below and thin
//   above, thicker and wider when the mist closes;
// - the banks: low wide clouds drifting slowly between the pillars, 3 to 19 blocks over the water;
// - the horizon comes close when the mist closes: the fog from 30-170 blocks down to 2-34.
// The sheets and the banks stay over the Mar da Névoa itself (Stark's Kyoto test, 2026-11-19): next to the sea they
// used to lie over the neighbor biome's low ground too, hiding it, and its trees looked like floating over the white.
public final class MistSeaFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    // height over the water and strength of each sheet
    private static final float[][] SHEETS = {{0.3f, 0.55f}, {1.3f, 0.45f}, {2.8f, 0.34f}, {4.8f, 0.24f}, {7.5f, 0.15f}, {11f, 0.08f}};
    private static final int CELLS = 64;
    private static final int SIZE = 3;
    private static final int BANKS = 46;
    private static final double SPAN = 130;

    private static float strength;
    private static float lastStrength;

    private static final class Bank {
        double x, y, z, w, h, v, a, lx, sea;
    }

    // which grid points are over the Mar da Névoa (1) or not (0), read once per column; MASK is that, softened
    private static final Long2ByteOpenHashMap OVER_SEA = new Long2ByteOpenHashMap();
    private static final float[][] MASK = new float[CELLS + 1][CELLS + 1];
    private static ClientLevel maskLevel;

    private static final Bank[] BANK = new Bank[BANKS];
    // the sheets' pattern on a grid around the player, worked out each tick (it drifts slowly)
    private static final float[][][] PATTERN = new float[SHEETS.length][CELLS + 1][CELLS + 1];
    private static int gridX = Integer.MIN_VALUE, gridZ;

    private MistSeaFx() {
    }

    // Is the column over the Mar da Névoa: 1 yes, 0 no, -1 not loaded yet (asked again later).
    private static int overSea(ClientLevel level, int x, int z) {
        if (level != maskLevel) {
            OVER_SEA.clear();
            maskLevel = level;
        }
        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        if (OVER_SEA.containsKey(k)) {
            return OVER_SEA.get(k);
        }
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return -1;
        }
        byte v = (byte) (level.getBiome(new BlockPos(x, level.getSeaLevel(), z)).is(ModWorldgen.MAR_DA_NEVOA) ? 1 : 0);
        if (OVER_SEA.size() > 60000) {
            OVER_SEA.clear();
        }
        OVER_SEA.put(k, v);
        return v;
    }

    // The sheets' mask on the grid: the mean of the 5 by 5 points around (a soft edge about 6 blocks wide at the shore).
    private static void mask(ClientLevel level) {
        int n = CELLS + 1;
        float[][] raw = new float[n + 4][n + 4];
        for (int i = 0; i < n + 4; i++) {
            for (int j = 0; j < n + 4; j++) {
                raw[i][j] = overSea(level, gridX + (i - 2) * SIZE, gridZ + (j - 2) * SIZE) > 0 ? 1 : 0;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float sum = 0;
                for (int a = 0; a < 5; a++) {
                    for (int b = 0; b < 5; b++) {
                        sum += raw[i + a][j + b];
                    }
                }
                MASK[i][j] = sum / 25f;
            }
        }
    }

    // The mist from 0 (open) to 1 (closed), t in seconds.
    private static double mist(double t) {
        return 0.5 + 0.5 * Math.sin(t * 6.2832 / 70 + Math.sin(t * 0.05) * 1.5);
    }

    private static double now(ClientLevel level, float partial) {
        return (level.getGameTime() + partial) / 20.0;
    }

    private static double water(ClientLevel level) {
        return level.getSeaLevel();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        for (Bank b : BANK) {
            if (b != null) {
                b.lx = b.x;
            }
        }
        if (mc.level == null || mc.player == null) {
            strength = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        boolean inside = BiomeAura.in("mar_da_nevoa", b -> b.is(ModWorldgen.MAR_DA_NEVOA));
        strength = Mth.clamp(strength + (inside ? 0.012f : -0.015f), 0, 1);
        if (strength <= 0) {
            gridX = Integer.MIN_VALUE;
            return;
        }
        Vec3 p = mc.player.position();
        double t = mc.level.getGameTime() / 20.0, m = mist(t);
        gridX = Mth.floor(p.x / SIZE) * SIZE - CELLS * SIZE / 2;
        gridZ = Mth.floor(p.z / SIZE) * SIZE - CELLS * SIZE / 2;
        mask(mc.level);
        for (int layer = 0; layer < SHEETS.length; layer++) {
            for (int i = 0; i <= CELLS; i++) {
                for (int j = 0; j <= CELLS; j++) {
                    PATTERN[layer][i][j] = (float) Math.min(0.92, SHEETS[layer][1] * sheet(gridX + i * SIZE, gridZ + j * SIZE, layer, t, m));
                }
            }
        }
        for (int i = 0; i < BANKS; i++) {
            Bank b = BANK[i];
            if (b == null) {
                b = BANK[i] = new Bank();
                b.x = p.x + (RANDOM.nextDouble() - 0.5) * SPAN * 2;
                b.z = p.z + (RANDOM.nextDouble() - 0.5) * SPAN * 2;
                b.y = 3 + RANDOM.nextDouble() * 16;
                b.w = 26 + RANDOM.nextDouble() * 40;
                b.h = 7 + RANDOM.nextDouble() * 9;
                b.v = 0.6 + RANDOM.nextDouble() * 0.9;
                b.a = 0.3 + RANDOM.nextDouble() * 0.25;
                b.lx = b.x;
            }
            b.x += b.v * 0.05;
            // a bank over the neighbor biome's land melts away (and comes back over the sea); over a chunk not loaded yet
            // it stays as it is (it used to melt there and come back once the chunk arrived)
            int over = overSea(mc.level, Mth.floor(b.x), Mth.floor(b.z));
            if (over >= 0) {
                b.sea = Mth.clamp(b.sea + (over > 0 ? 0.02 : -0.02), 0, 1);
            }
            // keep them around the player: the ones left behind come back on the other side. They come back unseen
            // (sea 0, and edge() is 0 out there), then show slowly: a bank used to jump from one side to the other at
            // full strength, a big white cloud popping in and out of the horizon in one frame (Stark 2026-11-19, "a
            // transição é cortada... parece que tá lagado").
            boolean moved = false;
            if (b.x - p.x > SPAN) {
                b.x -= SPAN * 2;
                moved = true;
            } else if (p.x - b.x > SPAN) {
                b.x += SPAN * 2;
                moved = true;
            }
            if (b.z - p.z > SPAN) {
                b.z -= SPAN * 2;
                moved = true;
            } else if (p.z - b.z > SPAN) {
                b.z += SPAN * 2;
                moved = true;
            }
            if (moved) {
                b.lx = b.x;
                b.sea = 0;
            }
        }
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
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        double sea = water(mc.level);
        // high over the sea (or down in it) the mist thins out over some blocks: it used to vanish all at once at 120
        // blocks up, which flying over the sea looked like a cut
        fade *= (float) (Mth.clamp((sea + 120 - camera.y) / 40, 0, 1) * Mth.clamp((camera.y - (sea - 6)) / 4, 0, 1));
        if (fade <= 0.001f) {
            return;
        }
        float light = 0.3f + 0.7f * mc.level.getSkyDarken(partial);
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        double t = now(mc.level, partial), m = mist(t);
        renderSheets(out, last, camera, sea, fade, light);
        renderBanks(out, last, cam, camera, sea, partial, m, fade, light);
        buffers.endBatch(FxRenderTypes.AREA);
    }

    // An integer hash (the preview's shader uses sin, the same kind of random but cheaper here).
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

    private static double smoothstep(double a, double b, double v) {
        double k = Mth.clamp((v - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }

    // The preview's sheet shader: three octaves drifting slowly, cut thicker as the mist closes.
    private static double sheet(double x, double z, int layer, double t, double m) {
        double qx = x * 0.035 + layer * 3.1 + t * 0.018, qz = z * 0.035 + layer * 1.7 + t * 0.007;
        double n = noise(qx, qz) * 0.55 + noise(qx * 2.2 - t * 0.01, qz * 2.2 - t * 0.01) * 0.3
                + noise(qx * 5.0 + t * 0.02, qz * 5.0 + t * 0.02) * 0.15;
        return smoothstep(0.55 - 0.45 * m, 0.95 - 0.25 * m, n) * (0.3 + 0.7 * m) * (1 + 0.6 * m);
    }

    private static void renderSheets(VertexConsumer out, PoseStack.Pose last, Vec3 camera, double sea, float fade, float light) {
        if (gridX == Integer.MIN_VALUE) {
            return;
        }
        // #dfe6e7
        float r = 0.875f * light, g = 0.902f * light, b = 0.906f * light;
        int bx = gridX, bz = gridZ;
        double half = CELLS * SIZE / 2.0, mx = bx + half, mz = bz + half;
        float[][] alpha = new float[CELLS + 1][CELLS + 1];
        for (int layer = 0; layer < SHEETS.length; layer++) {
            double y = sea + 0.1 + SHEETS[layer][0];
            float ry = (float) (y - camera.y);
            float[][] pat = PATTERN[layer];
            for (int i = 0; i <= CELLS; i++) {
                for (int j = 0; j <= CELLS; j++) {
                    if (pat[i][j] <= 0.0005f) {
                        alpha[i][j] = 0;
                        continue;
                    }
                    double x = bx + i * SIZE, z = bz + j * SIZE;
                    double dx = x - camera.x, dz = z - camera.z, ex = x - mx, ez = z - mz;
                    double d = Math.sqrt(dx * dx + dz * dz + ry * ry), edge = Math.sqrt(ex * ex + ez * ez);
                    double near = smoothstep(1.5, 10, d), far = Mth.clamp((half - edge) / (half * 0.35), 0, 1);
                    alpha[i][j] = pat[i][j] * MASK[i][j] * (float) (near * far) * fade;
                }
            }
            for (int i = 0; i < CELLS; i++) {
                for (int j = 0; j < CELLS; j++) {
                    if (alpha[i][j] + alpha[i + 1][j] + alpha[i + 1][j + 1] + alpha[i][j + 1] < 0.004f) {
                        continue;
                    }
                    float x0 = (float) (bx + i * SIZE - camera.x), x1 = x0 + SIZE;
                    float z0 = (float) (bz + j * SIZE - camera.z), z1 = z0 + SIZE;
                    // both faces, so it shows from under the sheet too
                    out.addVertex(last, x0, ry, z0).setColor(r, g, b, alpha[i][j]);
                    out.addVertex(last, x1, ry, z0).setColor(r, g, b, alpha[i + 1][j]);
                    out.addVertex(last, x1, ry, z1).setColor(r, g, b, alpha[i + 1][j + 1]);
                    out.addVertex(last, x0, ry, z1).setColor(r, g, b, alpha[i][j + 1]);
                    out.addVertex(last, x0, ry, z1).setColor(r, g, b, alpha[i][j + 1]);
                    out.addVertex(last, x1, ry, z1).setColor(r, g, b, alpha[i + 1][j + 1]);
                    out.addVertex(last, x1, ry, z0).setColor(r, g, b, alpha[i + 1][j]);
                    out.addVertex(last, x0, ry, z0).setColor(r, g, b, alpha[i][j]);
                }
            }
        }
    }

    // A bank: a wide soft cloud turned to the camera, bright in the middle and gone at the edge (two rings of a fan),
    // fading as the camera comes into it.
    private static void renderBanks(VertexConsumer out, PoseStack.Pose last, Camera cam, Vec3 camera, double sea, float partial, double m,
            float fade, float light) {
        org.joml.Vector3f l = cam.getLeftVector(), u = cam.getUpVector();
        // #e3e9ea
        float r = 0.89f * light, g = 0.914f * light, b = 0.918f * light;
        int seg = 12;
        for (Bank k : BANK) {
            if (k == null) {
                continue;
            }
            double x = Mth.lerp(partial, k.lx, k.x), y = sea + k.y, z = k.z;
            double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float a = (float) (k.a * k.sea * edge(x - camera.x, z - camera.z) * (0.15 + 0.85 * m) * Mth.clamp((d - 8) / 20, 0, 1)) * fade;
            if (a <= 0.003f) {
                continue;
            }
            float cx = (float) dx, cy = (float) dy, cz = (float) dz;
            double hw = k.w / 2, hh = k.h / 2;
            for (int s = 0; s < seg; s++) {
                double a0 = s * Math.PI * 2 / seg, a1 = (s + 1) * Math.PI * 2 / seg;
                float[] p0 = ring(l, u, a0, hw, hh, 0.5), p1 = ring(l, u, a1, hw, hh, 0.5);
                float[] q0 = ring(l, u, a0, hw, hh, 1), q1 = ring(l, u, a1, hw, hh, 1);
                // the middle (a fan made of quads: the centre twice)
                out.addVertex(last, cx, cy, cz).setColor(r, g, b, a);
                out.addVertex(last, cx + p0[0], cy + p0[1], cz + p0[2]).setColor(r, g, b, a * 0.45f);
                out.addVertex(last, cx + p1[0], cy + p1[1], cz + p1[2]).setColor(r, g, b, a * 0.45f);
                out.addVertex(last, cx, cy, cz).setColor(r, g, b, a);
                // the soft edge
                out.addVertex(last, cx + p0[0], cy + p0[1], cz + p0[2]).setColor(r, g, b, a * 0.45f);
                out.addVertex(last, cx + q0[0], cy + q0[1], cz + q0[2]).setColor(r, g, b, 0);
                out.addVertex(last, cx + q1[0], cy + q1[1], cz + q1[2]).setColor(r, g, b, 0);
                out.addVertex(last, cx + p1[0], cy + p1[1], cz + p1[2]).setColor(r, g, b, a * 0.45f);
            }
        }
    }

    // How much of a bank shows by how far it is in the square it lives in around the player: all of it inside, nothing
    // at the edge, where it goes over to the other side.
    private static double edge(double dx, double dz) {
        double out = Math.max(Math.abs(dx), Math.abs(dz));
        return smoothstep(SPAN, SPAN - 40, out);
    }

    private static float[] ring(org.joml.Vector3f l, org.joml.Vector3f u, double ang, double hw, double hh, double k) {
        double c = Math.cos(ang) * hw * k, s = Math.sin(ang) * hh * k;
        return new float[] {(float) (l.x() * c + u.x() * s), (float) (l.y() * c + u.y() * s), (float) (l.z() * c + u.z() * s)};
    }

    // A cold grey veil over the screen when the mist closes.
    public static void renderGrade(GuiGraphics graphics, DeltaTracker delta) {
        float fade = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastStrength, strength) * BiomeAura.nightDim(delta.getGameTimeDeltaPartialTick(false));
        Minecraft mc = Minecraft.getInstance();
        if (fade <= 0.001f || mc.level == null) {
            return;
        }
        double m = mist(now(mc.level, delta.getGameTimeDeltaPartialTick(false)));
        BiomeAura.wash(graphics, 0xB7C1C4, (float) (0.08 * m * fade));
    }

    // The preview's fog: 30 to 170 blocks with the mist open, down to 2 to 34 when it closes.
    public static void onFog(ViewportEvent.RenderFog event) {
        float k = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        Minecraft mc = Minecraft.getInstance();
        if (k <= 0.001f || mc.level == null || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        double m = mist(now(mc.level, (float) event.getPartialTick()));
        float far = event.getFarPlaneDistance(), near = event.getNearPlaneDistance();
        float mistFar = (float) Math.min(far, 170 * (1 - 0.8 * m)), mistNear = (float) Math.min(near, 30 * (1 - 0.92 * m));
        event.setFarPlaneDistance(Mth.lerp(k, far, mistFar));
        event.setNearPlaneDistance(Mth.lerp(k, near, mistNear));
        event.setCanceled(true);
    }
}
