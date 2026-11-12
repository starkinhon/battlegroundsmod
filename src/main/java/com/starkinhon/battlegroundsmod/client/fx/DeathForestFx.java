package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.ModBlocks;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The air of the Floresta da Morte (Stark 2026-11-11, from the approved preview), fading in when the player walks into
// the biome and out when he leaves. The preview's numbers:
// - the low fog: four thin sheets lying on the ground (0.2, 0.6, 1.0 and 1.4 blocks over it, thick below and thin
//   above), each with its own slowly moving pattern, so the fog comes up to the waist and the ground shows through in
//   places; it fades out right around the camera so it never turns into a wall;
// - no wind: only spores hanging in the air, rising and sinking slowly;
// - the sun almost never gets in: only where the crown has a hole, a thin pale shaft comes down slanted like the sun,
//   lighting the fog it crosses; by day only;
// - the fog closer than vanilla's (the preview's 22 to 150 blocks) and the screen a little darker and greener at the
//   edges. The fog's and the sky's colors are the biome's own.
public final class DeathForestFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int CELLS = 32;
    private static final int SIZE = 2;
    private static final float[][] LAYERS = {{0.2f, 0.42f}, {0.6f, 0.34f}, {1.0f, 0.24f}, {1.4f, 0.12f}};
    private static final int MAX_SPORES = 320;

    private static float strength;
    private static float lastStrength;
    // the ground under the fog: the top of the dirt, mud or water per grid point, around where it was measured
    private static final int[][] GROUND = new int[CELLS + 1][CELLS + 1];
    private static int groundX = Integer.MIN_VALUE, groundZ, groundY;
    private static long groundAt;

    private static final class Spore {
        double x, y, z, a, s;
    }

    private static final List<Spore> SPORES = new ArrayList<>();
    private static final List<Vec3> SHAFTS = new ArrayList<>();
    private static long shaftsAt;

    private DeathForestFx() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            SPORES.clear();
            SHAFTS.clear();
            strength = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        boolean inside = mc.level.getBiome(mc.player.blockPosition()).is(ModWorldgen.FLORESTA_DA_MORTE);
        strength = Mth.clamp(strength + (inside ? 0.012f : -0.02f), 0, 1);
        if (strength <= 0) {
            SPORES.clear();
            SHAFTS.clear();
            groundX = Integer.MIN_VALUE;
            return;
        }
        long now = mc.level.getGameTime();
        Vec3 p = mc.player.position();
        // the ground under the fog grid, again when the player has moved or every few seconds (the world changes)
        int bx = Mth.floor(p.x / SIZE) * SIZE - CELLS * SIZE / 2, bz = Mth.floor(p.z / SIZE) * SIZE - CELLS * SIZE / 2;
        if (bx != groundX || bz != groundZ || Math.abs(p.y - groundY) > 3 || now - groundAt > 60) {
            measureGround(mc.level, bx, bz, Mth.floor(p.y));
        }
        if (now - shaftsAt > 40) {
            findShafts(mc.level, p);
            shaftsAt = now;
        }
        // spores: slowly up and down, no wind
        SPORES.removeIf(s -> {
            s.a += 0.05 * s.s;
            s.y += Math.sin(s.a) * 0.006;
            s.x += Math.cos(s.a * 0.7) * 0.003;
            double dx = s.x - p.x, dz = s.z - p.z;
            return dx * dx + dz * dz > 30 * 30 || Math.abs(s.y - p.y) > 20;
        });
        int want = (int) (MAX_SPORES * strength);
        for (int i = 0; i < 8 && SPORES.size() < want; i++) {
            Spore s = new Spore();
            s.x = p.x + (RANDOM.nextDouble() * 2 - 1) * 26;
            s.z = p.z + (RANDOM.nextDouble() * 2 - 1) * 26;
            s.y = p.y - 2 + RANDOM.nextDouble() * 16;
            s.a = RANDOM.nextDouble() * 6.28;
            s.s = 0.2 + RANDOM.nextDouble() * 0.5;
            SPORES.add(s);
        }
    }

    private static boolean ground(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.MUD) || state.is(Blocks.WATER) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND);
    }

    private static void measureGround(ClientLevel level, int bx, int bz, int py) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= CELLS; i++) {
            for (int j = 0; j <= CELLS; j++) {
                int x = bx + i * SIZE, z = bz + j * SIZE;
                int y = py + 8, found = Integer.MIN_VALUE;
                for (; y > py - 16; y--) {
                    if (ground(level.getBlockState(at.set(x, y, z)))) {
                        found = y;
                        break;
                    }
                }
                GROUND[i][j] = found;
            }
        }
        groundX = bx;
        groundZ = bz;
        groundY = py;
        groundAt = level.getGameTime();
    }

    // Holes in the crown the sun comes through: open all the way along the sun's slant, leaves all around up high.
    private static void findShafts(ClientLevel level, Vec3 p) {
        SHAFTS.clear();
        Vec3 sun = sunDir(level, 0);
        if (sun.y < 0.25) {
            return;
        }
        // under a roof (the Exam tower, the Snake's Den) the shafts showed through the walls: none indoors (the canopy's
        // leaves don't count as a roof)
        BlockPos head = BlockPos.containing(p).above();
        for (int k = 1; k <= 16; k++) {
            BlockState roof = level.getBlockState(head.above(k));
            if (!roof.isAir() && !roof.is(BlockTags.LEAVES) && roof.isSolid()) {
                return;
            }
        }
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int px = Mth.floor(p.x), pz = Mth.floor(p.z);
        for (int x = (px & ~3) - 48; x <= px + 48 && SHAFTS.size() < 8; x += 4) {
            for (int z = (pz & ~3) - 48; z <= pz + 48 && SHAFTS.size() < 8; z += 4) {
                if (((x * 31 + z * 17) & 3) != 0) {
                    continue;
                }
                int fy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (Math.abs(fy - p.y) > 14) {
                    continue;
                }
                boolean open = true;
                for (int k = 2; k < 60 && open; k++) {
                    BlockState s = level.getBlockState(at.set(x + sun.x * k / sun.y, fy + k, z + sun.z * k / sun.y));
                    open = s.isAir() || s.is(ModBlocks.HANGING_VINE.get());
                }
                if (!open) {
                    continue;
                }
                int around = 0;
                for (int[] d : new int[][] {{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
                    if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + d[0], z + d[1]) > fy + 25) {
                        around++;
                    }
                }
                if (around >= 2) {
                    SHAFTS.add(new Vec3(x + 0.5, fy, z + 0.5));
                }
            }
        }
    }

    private static Vec3 sunDir(ClientLevel level, float partial) {
        float a = level.getSunAngle(partial);
        return new Vec3(-Mth.sin(a), Mth.cos(a), 0);
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
        float light = mc.level.getSkyDarken(partial);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        double now = (mc.level.getGameTime() + partial) / 20.0;
        renderFog(out, last, camera, now, fade, light);
        renderShafts(out, last, camera, mc.level, partial, fade, event.getCamera().getLookVector());
        renderSpores(out, last, camera, partial, fade, light, event.getCamera());
        buffers.endBatch(FxRenderTypes.AREA);
    }

    // The fog pattern of the preview's shader: three octaves of smooth noise moving slowly, each layer shifted.
    private static double pattern(double x, double z, int layer, double t) {
        double qx = x * 0.06 + layer * 3.1, qz = z * 0.06 + layer * 1.7;
        return noise(qx + t * 0.012, qz + t * 0.012) * 0.55 + noise(qx * 2.3 - t * 0.02, qz * 2.3 - t * 0.02) * 0.3
                + noise(qx * 5 + t * 0.03, qz * 5 + t * 0.03) * 0.15;
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

    private static double hash(int x, int z) {
        double s = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    private static void renderFog(VertexConsumer out, PoseStack.Pose last, Vec3 camera, double now, float fade, float light) {
        if (groundX == Integer.MIN_VALUE) {
            return;
        }
        // #8e9886, darker at night with the sky
        float r = 0.556f * light, g = 0.596f * light, b = 0.525f * light;
        float[][] alpha = new float[CELLS + 1][CELLS + 1];
        float[][] ys = new float[CELLS + 1][CELLS + 1];
        double half = CELLS * SIZE / 2.0;
        for (int layer = 0; layer < LAYERS.length; layer++) {
            for (int i = 0; i <= CELLS; i++) {
                for (int j = 0; j <= CELLS; j++) {
                    int gy = GROUND[i][j];
                    if (gy == Integer.MIN_VALUE) {
                        alpha[i][j] = 0;
                        ys[i][j] = 0;
                        continue;
                    }
                    double x = groundX + i * SIZE, z = groundZ + j * SIZE;
                    double y = gy + 1 + LAYERS[layer][0];
                    double m = pattern(x, z, layer, now);
                    double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float near = (float) Mth.clamp((d - 1.5) / 7.5, 0, 1);
                    float far = (float) Mth.clamp((half - Math.sqrt(dx * dx + dz * dz)) / (half * 0.35), 0, 1);
                    float k = (float) Mth.clamp((m - 0.25) / 0.5, 0, 1);
                    k = k * k * (3 - 2 * k);
                    alpha[i][j] = LAYERS[layer][1] * k * near * far * fade;
                    ys[i][j] = (float) (y - camera.y);
                }
            }
            for (int i = 0; i < CELLS; i++) {
                for (int j = 0; j < CELLS; j++) {
                    if (GROUND[i][j] == Integer.MIN_VALUE || GROUND[i + 1][j] == Integer.MIN_VALUE || GROUND[i][j + 1] == Integer.MIN_VALUE
                            || GROUND[i + 1][j + 1] == Integer.MIN_VALUE) {
                        continue;
                    }
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

    // A shaft: a ribbon turned to face the camera along the sun's slant, faint at the foot, strongest low down, fading out
    // high up; a narrow brighter core inside a wide faint one.
    private static void renderShafts(VertexConsumer out, PoseStack.Pose last, Vec3 camera, ClientLevel level, float partial, float fade,
            org.joml.Vector3f look) {
        if (SHAFTS.isEmpty()) {
            return;
        }
        Vec3 sun = sunDir(level, partial);
        float day = Mth.clamp((float) (sun.y - 0.25) * 3, 0, 1) * (level.isRaining() ? 0.3f : 1);
        if (day <= 0.001f) {
            return;
        }
        Vec3 view = new Vec3(look.x(), look.y(), look.z());
        Vec3 side = sun.cross(view);
        if (side.lengthSqr() < 1e-4) {
            return;
        }
        side = side.normalize();
        double len = 58;
        int steps = 12;
        for (Vec3 s : SHAFTS) {
            double w = 0.8 + ((int) (s.x * 7 + s.z * 13) & 7) / 7.0 * 1.4;
            for (int band = 0; band < 2; band++) {
                double half = band == 0 ? w * 1.3 : w * 0.5;
                float base = (band == 0 ? 0.07f : 0.1f) * day * fade;
                for (int i = 0; i < steps; i++) {
                    double t0 = (double) i / steps, t1 = (double) (i + 1) / steps;
                    float a0 = base * along(t0), a1 = base * along(t1);
                    Vec3 p0 = s.add(sun.scale(len * t0)).subtract(camera), p1 = s.add(sun.scale(len * t1)).subtract(camera);
                    double h0 = half * (1 + 0.3 * t0), h1 = half * (1 + 0.3 * t1);
                    out.addVertex(last, (float) (p0.x - side.x * h0), (float) (p0.y - side.y * h0), (float) (p0.z - side.z * h0))
                            .setColor(0.95f, 0.93f, 0.7f, a0);
                    out.addVertex(last, (float) (p0.x + side.x * h0), (float) (p0.y + side.y * h0), (float) (p0.z + side.z * h0))
                            .setColor(0.95f, 0.93f, 0.7f, a0);
                    out.addVertex(last, (float) (p1.x + side.x * h1), (float) (p1.y + side.y * h1), (float) (p1.z + side.z * h1))
                            .setColor(0.95f, 0.93f, 0.7f, a1);
                    out.addVertex(last, (float) (p1.x - side.x * h1), (float) (p1.y - side.y * h1), (float) (p1.z - side.z * h1))
                            .setColor(0.95f, 0.93f, 0.7f, a1);
                }
            }
        }
    }

    private static float along(double t) {
        double up = Mth.clamp(t / 0.12, 0, 1), down = 1 - Mth.clamp((t - 0.45) / 0.5, 0, 1);
        return (float) (up * down);
    }

    private static void renderSpores(VertexConsumer out, PoseStack.Pose last, Vec3 camera, float partial, float fade, float light,
            net.minecraft.client.Camera cam) {
        org.joml.Vector3f l = cam.getLeftVector(), u = cam.getUpVector();
        float s = 0.05f;
        float r = 0.89f * light, g = 0.91f * light, b = 0.69f * light;
        for (Spore p : SPORES) {
            float x = (float) (p.x - camera.x), y = (float) (p.y + Math.sin(p.a + 0.05 * p.s * partial) * 0.006 * partial - camera.y),
                    z = (float) (p.z - camera.z);
            float d2 = x * x + y * y + z * z;
            if (d2 > 28 * 28) {
                continue;
            }
            float a = 0.75f * fade * Mth.clamp(1 - (float) Math.sqrt(d2) / 28, 0, 1);
            out.addVertex(last, x + (l.x() + u.x()) * s, y + (l.y() + u.y()) * s, z + (l.z() + u.z()) * s).setColor(r, g, b, a);
            out.addVertex(last, x + (-l.x() + u.x()) * s, y + (-l.y() + u.y()) * s, z + (-l.z() + u.z()) * s).setColor(r, g, b, a);
            out.addVertex(last, x + (-l.x() - u.x()) * s, y + (-l.y() - u.y()) * s, z + (-l.z() - u.z()) * s).setColor(r, g, b, a);
            out.addVertex(last, x + (l.x() - u.x()) * s, y + (l.y() - u.y()) * s, z + (l.z() - u.z()) * s).setColor(r, g, b, a);
        }
    }

    // A little darker and greener toward the screen's edges.
    public static void renderGrade(GuiGraphics graphics, DeltaTracker delta) {
        float fade = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        graphics.fill(0, 0, w, h, argb(0.06f * fade, 0x1E2A18));
        int bands = 18;
        int depthX = Math.max(1, w / 4), depthY = Math.max(1, h / 3);
        for (int i = 0; i < bands; i++) {
            float k = 1 - (float) i / bands;
            int color = argb(0.035f * k * k * fade, 0x0A1008);
            int x = depthX * i / bands, y = depthY * i / bands;
            int x2 = depthX * (i + 1) / bands, y2 = depthY * (i + 1) / bands;
            graphics.fill(x, 0, x2, h, color);
            graphics.fill(w - x2, 0, w - x, h, color);
            graphics.fill(0, y, w, y2, color);
            graphics.fill(0, h - y2, w, h - y, color);
        }
    }

    // The preview's fog: 22 to 150 blocks.
    public static void onFog(ViewportEvent.RenderFog event) {
        float k = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (k <= 0.001f || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float near = event.getNearPlaneDistance();
        float thickFar = Math.min(far, 150);
        event.setFarPlaneDistance(Mth.lerp(k, far, thickFar));
        event.setNearPlaneDistance(Mth.lerp(k, near, Math.min(near, 22)));
        event.setCanceled(true);
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | rgb;
    }
}
