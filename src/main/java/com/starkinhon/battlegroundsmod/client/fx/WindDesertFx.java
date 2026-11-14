package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.ModBlocks;
import com.starkinhon.battlegroundsmod.world.DesertGen;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The wind of the Deserto do Vento (Stark 2026-11-11, from the approved preview), fading in when the player walks into
// the biome and out when he leaves. The wind blows the way DesertGen.WIND says (the dunes' way) and comes in gusts
// (gust(), the preview's gustAt). The preview's numbers:
// - the sand running low over the ground: thin streaks turned to the camera, bright at the head, the tail fading, that
//   follow the ground, jump off the dune crests and come down on the other side;
// - the veils: four sheets lying over the ground (0.25, 0.9, 2.0 and 3.6 blocks over it), a pattern stretched along the
//   wind that runs fast, thick below and thin above, stronger in a gust;
// - the dust blowing off the dune crests: soft puffs that leave the edge, rise a little and fade downwind;
// - in a gust the horizon goes into the dust: the fog closes from 40-260 blocks by up to a third.
public final class WindDesertFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WX = Math.cos(DesertGen.WIND), WZ = Math.sin(DesertGen.WIND);
    private static final int CELLS = 32;
    private static final int SIZE = 2;
    private static final float[][] LAYERS = {{0.25f, 0.46f}, {0.9f, 0.32f}, {2.0f, 0.18f}, {3.6f, 0.09f}};
    private static final int RUNNERS = 1400;
    private static final double AREA = 40;
    private static final int PUFFS = 700;

    private static float strength;
    private static float lastStrength;
    // the top of the ground (with the sand layers' fraction) per grid point, around where it was measured
    private static final float[][] GROUND = new float[CELLS + 1][CELLS + 1];
    private static int groundX = Integer.MIN_VALUE, groundZ, groundY;
    private static long groundAt;

    private static final class Runner {
        double x, y, z, h, s, ph, lx, ly, lz;
    }

    private static final class Puff {
        double x, y, z, vs, up, sz, life, max;
    }

    private static final List<Runner> RUN = new ArrayList<>();
    private static final List<Puff> PUFF = new ArrayList<>();
    private static final List<double[]> CRESTS = new ArrayList<>();
    private static long crestsAt;

    private WindDesertFx() {
    }

    // The gusts: the wind isn't steady, it presses in waves (the preview's gustAt, t in seconds).
    private static double gust(double t) {
        return 0.3 + 0.7 * Math.pow(0.5 + 0.5 * Math.sin(t * 0.45 + Math.sin(t * 0.13) * 2.0), 3);
    }

    private static double now(ClientLevel level, float partial) {
        return (level.getGameTime() + partial) / 20.0;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        for (Runner r : RUN) {
            r.lx = r.x;
            r.ly = r.y;
            r.lz = r.z;
        }
        if (mc.level == null || mc.player == null) {
            clear();
            strength = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        boolean inside = BiomeAura.in("deserto_do_vento", b -> b.is(ModWorldgen.DESERTO_DO_VENTO));
        strength = Mth.clamp(strength + (inside ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            clear();
            groundX = Integer.MIN_VALUE;
            return;
        }
        ClientLevel level = mc.level;
        long time = level.getGameTime();
        Vec3 p = mc.player.position();
        int bx = Mth.floor(p.x / SIZE) * SIZE - CELLS * SIZE / 2, bz = Mth.floor(p.z / SIZE) * SIZE - CELLS * SIZE / 2;
        if (bx != groundX || bz != groundZ || Math.abs(p.y - groundY) > 3 || time - groundAt > 60) {
            measureGround(level, bx, bz, Mth.floor(p.y));
        }
        if (time - crestsAt > 40) {
            findCrests(level, p);
            crestsAt = time;
        }
        double g = gust(time / 20.0), dt = 0.05;
        // the sand running over the ground
        while (RUN.size() < RUNNERS * strength) {
            Runner r = new Runner();
            spawn(level, r, p, true);
            RUN.add(r);
        }
        while (RUN.size() > RUNNERS * strength + 1) {
            RUN.remove(RUN.size() - 1);
        }
        for (Runner r : RUN) {
            double sp = (9 + 7 * g) * r.s;
            r.ph += dt * 3;
            r.x += (WX * sp - WZ * Math.sin(r.ph) * 0.8) * dt;
            r.z += (WZ * sp + WX * Math.sin(r.ph) * 0.8) * dt;
            double ground = top(level, Mth.floor(r.x), Mth.floor(r.z)) + r.h;
            r.y = ground > r.y ? ground : Math.max(ground, r.y - dt * 2.5);
            double u = (r.x - p.x) * WX + (r.z - p.z) * WZ, v = -(r.x - p.x) * WZ + (r.z - p.z) * WX;
            if (u > AREA || Math.abs(v) > AREA || u < -AREA - 4) {
                spawn(level, r, p, false);
            }
        }
        // the dust off the crests
        while (PUFF.size() < PUFFS) {
            Puff f = new Puff();
            f.life = -RANDOM.nextDouble() * 3;
            PUFF.add(f);
        }
        for (Puff f : PUFF) {
            f.life -= dt;
            if (f.life <= 0 && !CRESTS.isEmpty() && f.life < -0.1 && RANDOM.nextDouble() < (0.6 + g) * strength) {
                double[] c = CRESTS.get(RANDOM.nextInt(CRESTS.size()));
                f.x = c[0] + RANDOM.nextDouble();
                f.z = c[2] + RANDOM.nextDouble();
                f.y = c[1] + 0.1;
                f.max = f.life = 1.4 + RANDOM.nextDouble() * 1.8;
                f.vs = 5 + RANDOM.nextDouble() * 5;
                f.up = 0.6 + RANDOM.nextDouble() * 1.2;
                f.sz = 0.8 + RANDOM.nextDouble() * 1.4;
            }
            if (f.life > 0) {
                f.x += WX * f.vs * dt * (0.6 + g);
                f.z += WZ * f.vs * dt * (0.6 + g);
                f.y += f.up * dt;
            }
        }
    }

    private static void clear() {
        RUN.clear();
        PUFF.clear();
        CRESTS.clear();
    }

    private static void spawn(ClientLevel level, Runner r, Vec3 p, boolean anywhere) {
        double v = (RANDOM.nextDouble() - 0.5) * AREA * 2;
        double u = anywhere ? (RANDOM.nextDouble() - 0.5) * AREA * 2 : -AREA;
        r.x = p.x + u * WX - v * WZ;
        r.z = p.z + u * WZ + v * WX;
        r.h = RANDOM.nextDouble() < 0.8 ? RANDOM.nextDouble() * 0.5 : 0.5 + RANDOM.nextDouble() * 1.6;
        r.y = top(level, Mth.floor(r.x), Mth.floor(r.z)) + r.h;
        r.s = 0.7 + RANDOM.nextDouble() * 0.6;
        r.ph = RANDOM.nextDouble() * 6.28;
        r.lx = r.x;
        r.ly = r.y;
        r.lz = r.z;
    }

    // The top of the ground at a column: the heightmap (which a thin sand layer doesn't count), plus the layer's eighths.
    private static float top(ClientLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockState s = level.getBlockState(new BlockPos(x, y, z));
        if (s.is(ModBlocks.SAND_LAYER.get())) {
            return y + s.getValue(SnowLayerBlock.LAYERS) / 8f;
        }
        return y;
    }

    private static void measureGround(ClientLevel level, int bx, int bz, int py) {
        for (int i = 0; i <= CELLS; i++) {
            for (int j = 0; j <= CELLS; j++) {
                float t = top(level, bx + i * SIZE, bz + j * SIZE);
                GROUND[i][j] = Math.abs(t - py) > 24 ? Float.NaN : t;
            }
        }
        groundX = bx;
        groundZ = bz;
        groundY = py;
        groundAt = level.getGameTime();
    }

    // Dune crests: sand that drops off downwind and is at least as high as upwind.
    private static void findCrests(ClientLevel level, Vec3 p) {
        CRESTS.clear();
        int px = Mth.floor(p.x), pz = Mth.floor(p.z);
        for (int x = (px & ~1) - 48; x <= px + 48; x += 2) {
            for (int z = (pz & ~1) - 48; z <= pz + 48; z += 2) {
                float h = top(level, x, z);
                if (Math.abs(h - p.y) > 20) {
                    continue;
                }
                float ahead = top(level, Mth.floor(x + WX * 1.5), Mth.floor(z + WZ * 1.5));
                float back = top(level, Mth.floor(x - WX * 1.5), Mth.floor(z - WZ * 1.5));
                if (h - ahead <= 0.6 || h < back) {
                    continue;
                }
                BlockState s = level.getBlockState(BlockPos.containing(x, Math.ceil(h) - 1, z));
                if (s.is(BlockTags.SAND) || s.is(ModBlocks.SAND_LAYER.get())) {
                    CRESTS.add(new double[] {x, h, z});
                }
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
        float light = 0.35f + 0.65f * mc.level.getSkyDarken(partial);
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        double t = now(mc.level, partial), g = gust(t);
        renderVeils(out, last, camera, t, g, fade, light);
        renderRunners(out, last, camera, partial, fade, light);
        renderPuffs(out, last, cam, camera, partial, g, fade, light);
        buffers.endBatch(FxRenderTypes.AREA);
    }

    private static double hash(int x, int z) {
        double s = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
        return s - Math.floor(s);
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

    // The preview's veil shader: a pattern stretched along the wind, running with it, in slow waves.
    private static double veil(double x, double z, int layer, double t, double g) {
        double u = x * WX + z * WZ, v = -x * WZ + z * WX;
        double run = t * (7.0 + 3.0 * layer);
        double qx = (u - run) * 0.035, qz = v * 0.16 + layer * 5.3;
        double m = noise(qx, qz) * 0.6 + noise(qx * 2.1 + 3.1, qz * 2.7 + 3.1) * 0.28 + noise(qx * 4.3 - 1.7, qz * 6.0 - 1.7) * 0.12;
        double wave = noise((u - t * 11.0) * 0.012, v * 0.01);
        double k = Mth.clamp((m - 0.42) / 0.4, 0, 1);
        k = k * k * (3 - 2 * k);
        return k * (0.25 + 0.75 * g) * (0.5 + wave);
    }

    private static void renderVeils(VertexConsumer out, PoseStack.Pose last, Vec3 camera, double t, double g, float fade, float light) {
        if (groundX == Integer.MIN_VALUE) {
            return;
        }
        // #e6c99e
        float r = 0.902f * light, gr = 0.788f * light, b = 0.62f * light;
        float[][] alpha = new float[CELLS + 1][CELLS + 1];
        float[][] ys = new float[CELLS + 1][CELLS + 1];
        double half = CELLS * SIZE / 2.0;
        for (int layer = 0; layer < LAYERS.length; layer++) {
            for (int i = 0; i <= CELLS; i++) {
                for (int j = 0; j <= CELLS; j++) {
                    float gy = GROUND[i][j];
                    if (Float.isNaN(gy)) {
                        alpha[i][j] = 0;
                        continue;
                    }
                    double x = groundX + i * SIZE, z = groundZ + j * SIZE, y = gy + LAYERS[layer][0];
                    double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float near = (float) Mth.clamp((d - 1.0) / 6.0, 0, 1);
                    near = near * near * (3 - 2 * near);
                    float far = (float) Mth.clamp((half - Math.sqrt(dx * dx + dz * dz)) / (half * 0.35), 0, 1);
                    alpha[i][j] = (float) (LAYERS[layer][1] * veil(x, z, layer, t, g)) * near * far * fade;
                    ys[i][j] = (float) (y - camera.y);
                }
            }
            for (int i = 0; i < CELLS; i++) {
                for (int j = 0; j < CELLS; j++) {
                    if (Float.isNaN(GROUND[i][j]) || Float.isNaN(GROUND[i + 1][j]) || Float.isNaN(GROUND[i][j + 1])
                            || Float.isNaN(GROUND[i + 1][j + 1])) {
                        continue;
                    }
                    if (alpha[i][j] + alpha[i + 1][j] + alpha[i + 1][j + 1] + alpha[i][j + 1] < 0.004f) {
                        continue;
                    }
                    float x0 = (float) (groundX + i * SIZE - camera.x), x1 = x0 + SIZE;
                    float z0 = (float) (groundZ + j * SIZE - camera.z), z1 = z0 + SIZE;
                    out.addVertex(last, x0, ys[i][j], z0).setColor(r, gr, b, alpha[i][j]);
                    out.addVertex(last, x1, ys[i + 1][j], z0).setColor(r, gr, b, alpha[i + 1][j]);
                    out.addVertex(last, x1, ys[i + 1][j + 1], z1).setColor(r, gr, b, alpha[i + 1][j + 1]);
                    out.addVertex(last, x0, ys[i][j + 1], z1).setColor(r, gr, b, alpha[i][j + 1]);
                }
            }
        }
    }

    // A streak: a thin ribbon along the wind, its width turned to the camera; bright head, the tail fading.
    private static void renderRunners(VertexConsumer out, PoseStack.Pose last, Vec3 camera, float partial, float fade, float light) {
        // #fff4de
        float r = 1.0f * light, g = 0.957f * light, b = 0.871f * light;
        double gs = gust(Minecraft.getInstance().level.getGameTime() / 20.0);
        for (Runner rn : RUN) {
            double x = Mth.lerp(partial, rn.lx, rn.x), y = Mth.lerp(partial, rn.ly, rn.y), z = Mth.lerp(partial, rn.lz, rn.z);
            double cx = camera.x - x, cy = camera.y - y, cz = camera.z - z;
            double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
            float a = (float) (0.8 * fade * Mth.clamp((60 - dist) / 40, 0, 1) * Mth.clamp((dist - 2) / 3, 0, 1));
            if (a <= 0.003f) {
                continue;
            }
            double len = (9 + 7 * gs) * rn.s * 0.07;
            double sx = WZ * cy, sy = WX * cz - WZ * cx, sz = -WX * cy;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6) {
                continue;
            }
            double wd = 0.045 * (1 + dist * 0.012) / sl;
            sx *= wd;
            sy *= wd;
            sz *= wd;
            float hx = (float) (x - camera.x), hy = (float) (y - camera.y), hz = (float) (z - camera.z);
            float tx = (float) (hx - WX * len), tz = (float) (hz - WZ * len);
            out.addVertex(last, (float) (tx - sx), (float) (hy - sy), (float) (tz - sz)).setColor(r, g, b, 0);
            out.addVertex(last, (float) (tx + sx), (float) (hy + sy), (float) (tz + sz)).setColor(r, g, b, 0);
            out.addVertex(last, (float) (hx + sx), (float) (hy + sy), (float) (hz + sz)).setColor(r, g, b, a);
            out.addVertex(last, (float) (hx - sx), (float) (hy - sy), (float) (hz - sz)).setColor(r, g, b, a);
        }
    }

    // A puff: soft, bright in the middle and gone at the edge (four quads around the middle), growing as it fades.
    private static void renderPuffs(VertexConsumer out, PoseStack.Pose last, Camera cam, Vec3 camera, float partial, double g, float fade,
            float light) {
        org.joml.Vector3f l = cam.getLeftVector(), u = cam.getUpVector();
        // #ead0a6
        float r = 0.918f * light, gr = 0.816f * light, b = 0.651f * light;
        for (Puff f : PUFF) {
            if (f.life <= 0) {
                continue;
            }
            double t = 1 - (f.life - 0.05 * partial) / f.max;
            float a = (float) (0.1 * Math.sin(Mth.clamp(t, 0, 1) * Math.PI) * (0.4 + g) * fade);
            if (a <= 0.002f) {
                continue;
            }
            double s = f.sz * (1 + t * 4) / 2;
            float x = (float) (f.x - camera.x), y = (float) (f.y - camera.y), z = (float) (f.z - camera.z);
            float lx = (float) (l.x() * s), ly = (float) (l.y() * s), lz = (float) (l.z() * s);
            float ux = (float) (u.x() * s), uy = (float) (u.y() * s), uz = (float) (u.z() * s);
            for (int q = 0; q < 4; q++) {
                float ax = q < 2 ? lx : -lx, ay = q < 2 ? ly : -ly, az = q < 2 ? lz : -lz;
                float bx = q % 2 == 0 ? ux : -ux, by = q % 2 == 0 ? uy : -uy, bz = q % 2 == 0 ? uz : -uz;
                out.addVertex(last, x, y, z).setColor(r, gr, b, a);
                out.addVertex(last, x + ax, y + ay, z + az).setColor(r, gr, b, 0);
                out.addVertex(last, x + ax + bx, y + ay + by, z + az + bz).setColor(r, gr, b, 0);
                out.addVertex(last, x + bx, y + by, z + bz).setColor(r, gr, b, 0);
            }
        }
    }

    // A dusty warmth over the screen when the gust presses.
    public static void renderGrade(GuiGraphics graphics, DeltaTracker delta) {
        float fade = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastStrength, strength) * BiomeAura.nightDim(delta.getGameTimeDeltaPartialTick(false));
        Minecraft mc = Minecraft.getInstance();
        if (fade <= 0.001f || mc.level == null) {
            return;
        }
        double g = gust(now(mc.level, delta.getGameTimeDeltaPartialTick(false)));
        int alpha = Mth.clamp((int) (255 * 0.07 * g * fade), 0, 255);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xD9BC90);
    }

    // The preview's fog: 40 to 260 blocks, closing by up to a third in a gust.
    public static void onFog(ViewportEvent.RenderFog event) {
        float k = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        Minecraft mc = Minecraft.getInstance();
        if (k <= 0.001f || mc.level == null || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        double g = gust(now(mc.level, (float) event.getPartialTick()));
        float far = event.getFarPlaneDistance(), near = event.getNearPlaneDistance();
        float dustFar = (float) Math.min(far, 260 * (1 - 0.3 * g)), dustNear = (float) Math.min(near, 40 * (1 - 0.4 * g));
        event.setFarPlaneDistance(Mth.lerp(k, far, dustFar));
        event.setNearPlaneDistance(Mth.lerp(k, near, dustNear));
        event.setCanceled(true);
    }
}
