package com.starkinhon.battlegroundsmod.client.fx.epic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.client.fx.FxShaders;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.combat.RuinNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// What the end-game bosses' destruction looks like (combat/RuinNet; approved page forja/escala-fim-de-jogo, Stark
// 2026-11-19): the fire, dust and smoke volumes raymarched one shape each (souls_boom_vol), the dust rings running over
// the ground and the shock rings in the air, the ground's top blocks lifting in waves (copies of the real blocks, so the
// server moves nothing), the blocks flying out of the craters (copies too, falling and rolling to a stop) and the
// camera shaking by distance, with the manga impact frame on the big hits.
public final class EpicFx {
    private static final RandomSource RNG = RandomSource.createThreadSafe();
    private static final int MAX_VOLUMES = 14, MAX_DEBRIS = 900, MAX_LIFTED = 4200;

    private static final class Vol {
        Vec3 at;
        float R, H, grow, life, heat, seed, phase, lastAge;
        int mode, shape;
        float[] dust;
        double born;
        boolean ring;
    }

    private static final class Wave {
        Vec3 at;
        float amp, speed, reach;
        double born;
    }

    private static final class Deb {
        double x, y, z, vx, vy, vz;
        float rx, ry, rz, sx, sy, sz, size, heat, rest;
        BlockState state;
        boolean resting;
    }

    private static final List<Vol> VOLS = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final List<Deb> DEBRIS = new ArrayList<>();
    private static double lastFrame = -1;

    private EpicFx() {
    }

    public static boolean handles(int kind) {
        return kind >= RuinNet.FIRST && kind <= RuinNet.LAST;
    }

    public static boolean active() {
        return !VOLS.isEmpty() || !WAVES.isEmpty() || !DEBRIS.isEmpty();
    }

    public static void clear() {
        VOLS.clear();
        WAVES.clear();
        DEBRIS.clear();
    }

    private static double now() {
        Minecraft mc = Minecraft.getInstance();
        return ScreenFx.now() + mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static float[] rgb(int c) {
        return new float[] {(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    public static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = now();
        switch (kind) {
            case RuinNet.BOOM -> {
                int code = Math.round(a);
                Vol v = new Vol();
                v.at = pos;
                v.R = (float) dir.x;
                v.H = (float) dir.y;
                v.grow = Math.max(1, (float) dir.z);
                v.mode = code & 3;
                v.shape = code >> 2 & 3;
                v.heat = (code >> 4) / 100f;
                v.life = Math.max(1, b);
                v.dust = rgb((int) c);
                v.born = now;
                v.seed = RNG.nextFloat() * 20;
                add(v);
            }
            case RuinNet.RING -> {
                Vol v = new Vol();
                v.at = pos;
                v.R = (float) dir.x;
                v.H = (float) dir.y;
                v.ring = true;
                v.shape = dir.z > 0.5 ? 3 : 2;
                v.mode = 1;
                v.life = Math.max(1, a);
                v.dust = rgb((int) b);
                v.born = now;
                v.seed = RNG.nextFloat() * 20;
                add(v);
            }
            case RuinNet.TREMOR -> {
                Wave w = new Wave();
                w.at = pos;
                w.amp = a;
                w.speed = b;
                w.reach = c;
                w.born = now;
                if (WAVES.size() > 10) {
                    WAVES.remove(0);
                }
                WAVES.add(w);
            }
            case RuinNet.QUAKE -> quake(pos, a, b > 0.5f, (int) c);
            case RuinNet.DEBRIS -> crater(pos, (float) dir.x, (float) dir.y, (float) dir.z, (int) a, b);
            case RuinNet.DEBRIS_LINE -> line(pos, dir, (int) a, b);
            default -> {
            }
        }
    }

    private static void add(Vol v) {
        if (VOLS.size() >= MAX_VOLUMES) {
            Vol oldest = VOLS.get(0);
            for (Vol o : VOLS) {
                if (o.born < oldest.born) {
                    oldest = o;
                }
            }
            VOLS.remove(oldest);
        }
        VOLS.add(v);
    }

    // The page's bigShake: stronger the closer you are; the impact frame only for whoever is near enough to see it.
    private static void quake(Vec3 at, float strength, boolean impact, int tint) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double d = mc.player.position().distanceTo(at);
        float s = strength * (float) Mth.clamp(60 / Math.max(d, 10), 0.25, 1.4);
        if (d < 260) {
            ScreenShake.start(s, 16 + Math.round(strength * 6));
        }
        if (impact && d < 140) {
            ScreenFx.impactFrame(new int[] {1, 0, 1, 0}, 1, tint == 0 ? 0xFFE2B8 : tint, at, 0);
        }
    }

    // ---------- flying blocks ----------

    private static BlockState top(Level level, int x, int z, int[] yOut) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(p);
        if (!s.isAir() && (s.canBeReplaced() || !s.getFluidState().isEmpty())) {
            p = p.below();
            s = level.getBlockState(p);
        }
        yOut[0] = p.getY();
        return s;
    }

    private static boolean drawable(BlockState s) {
        return !s.isAir() && s.getRenderShape() == RenderShape.MODEL && s.getFluidState().isEmpty();
    }

    // Grass flying shows more of its dirt side (the page's matCol).
    private static BlockState chunk(BlockState s) {
        if (s.is(Blocks.GRASS_BLOCK) && RNG.nextFloat() < 0.7f) {
            return Blocks.DIRT.defaultBlockState();
        }
        return s;
    }

    private static void spawn(double x, double y, double z, double vx, double vy, double vz, float size, BlockState s, float heat) {
        if (DEBRIS.size() >= MAX_DEBRIS) {
            for (int i = 0; i < DEBRIS.size(); i++) {
                if (DEBRIS.get(i).resting) {
                    DEBRIS.remove(i);
                    break;
                }
            }
            if (DEBRIS.size() >= MAX_DEBRIS) {
                return;
            }
        }
        Deb d = new Deb();
        d.x = x;
        d.y = y;
        d.z = z;
        d.vx = vx;
        d.vy = vy;
        d.vz = vz;
        d.size = size;
        d.rx = RNG.nextFloat() * 6;
        d.ry = RNG.nextFloat() * 6;
        d.rz = RNG.nextFloat() * 6;
        d.sx = (RNG.nextFloat() - 0.5f) * 9;
        d.sy = (RNG.nextFloat() - 0.5f) * 9;
        d.sz = (RNG.nextFloat() - 0.5f) * 9;
        d.state = s;
        d.heat = heat;
        DEBRIS.add(d);
    }

    // Blocks torn out of a crater of the given radius, thrown out and up (speed in blocks/s at the middle).
    private static void crater(Vec3 at, float radius, float speed, float heat, int count, float size) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        int[] y = {0};
        for (int i = 0; i < count; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, u = Math.sqrt(RNG.nextDouble());
            int x = Mth.floor(at.x + Math.cos(a) * u * radius), z = Mth.floor(at.z + Math.sin(a) * u * radius);
            BlockState s = top(level, x, z, y);
            if (!drawable(s)) {
                continue;
            }
            double sp = speed * (0.55 + 0.45 * (1 - u)) * (0.3 + 0.7 * RNG.nextDouble());
            spawn(x + 0.5, y[0] + 0.6, z + 0.5, Math.cos(a) * sp, speed * (1 - u * 0.5) * (0.6 + RNG.nextDouble() * 0.8), Math.sin(a) * sp,
                    size * (0.5 + RNG.nextFloat() * 0.7), chunk(s), heat * RNG.nextFloat());
        }
    }

    // Blocks thrown up to both sides of a line (a furrow running).
    private static void line(Vec3 from, Vec3 d, int count, float speed) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 f = new Vec3(d.x, 0, d.z);
        double L = f.length();
        if (L < 1.0E-3) {
            return;
        }
        Vec3 dir = f.scale(1 / L), n = new Vec3(-dir.z, 0, dir.x);
        int[] y = {0};
        for (int i = 0; i < count; i++) {
            double s = RNG.nextDouble() * L, side = RNG.nextBoolean() ? 1 : -1, w = RNG.nextDouble() * 2.5;
            int x = Mth.floor(from.x + dir.x * s + n.x * side * w), z = Mth.floor(from.z + dir.z * s + n.z * side * w);
            BlockState st = top(level, x, z, y);
            if (!drawable(st)) {
                continue;
            }
            double out = (0.35 + 0.65 * RNG.nextDouble()) * speed * 0.6;
            spawn(x + 0.5, y[0] + 0.6, z + 0.5, n.x * side * out + dir.x * speed * 0.15, speed * (0.6 + 0.6 * RNG.nextDouble()), n.z * side * out + dir.z * speed * 0.15,
                    0.6f + RNG.nextFloat() * 0.9f, chunk(st), 0);
        }
    }

    private static void stepDebris(Level level, float dt) {
        for (Iterator<Deb> it = DEBRIS.iterator(); it.hasNext();) {
            Deb d = it.next();
            if (d.resting) {
                d.rest += dt;
                if (d.rest > 3.5f) {
                    it.remove();
                }
                continue;
            }
            d.vy -= 30 * dt;
            double drag = 1 - dt * 0.15;
            d.vx *= drag;
            d.vy *= drag;
            d.vz *= drag;
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            d.z += d.vz * dt;
            d.rx += d.sx * dt;
            d.ry += d.sy * dt;
            d.rz += d.sz * dt;
            d.heat = Math.max(0, d.heat - dt * 0.2f);
            int gx = Mth.floor(d.x), gz = Mth.floor(d.z);
            double g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz) + d.size * 0.5;
            if (d.y < g && d.vy < 0) {
                if (d.y < g - 2.5) {
                    // fell into a wall: gone
                    it.remove();
                    continue;
                }
                d.y = g;
                if (d.vy < -7) {
                    d.vy *= -0.25;
                    d.vx *= 0.5;
                    d.vz *= 0.5;
                    d.sx *= 0.5;
                    d.sy *= 0.5;
                    d.sz *= 0.5;
                } else {
                    d.resting = true;
                    d.rx = Math.round(d.rx / Mth.HALF_PI) * Mth.HALF_PI;
                    d.rz = Math.round(d.rz / Mth.HALF_PI) * Mth.HALF_PI;
                }
            }
            if (d.y < level.getMinBuildHeight()) {
                it.remove();
            }
        }
    }

    // ---------- drawing ----------

    // Once a frame from FxWorld, at the end of the level (with the Supernova's dome).
    public static void render(Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || !active()) {
            return;
        }
        double now = now();
        float dt = lastFrame < 0 ? 0 : (float) Mth.clamp((now - lastFrame) / 20, 0, 0.1);
        lastFrame = now;
        stepDebris(level, dt);
        WAVES.removeIf(w -> (now - w.born) / 20 * w.speed > w.reach + 12);
        VOLS.removeIf(v -> now - v.born > v.life);
        blocks(level, camera, now);
        volumes(camera, now);
    }

    // The flying blocks and the ground lifting under the tremors: copies of the real blocks.
    private static void blocks(Level level, Vec3 camera, double now) {
        if (DEBRIS.isEmpty() && WAVES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher br = mc.getBlockRenderer();
        PoseStack pose = new PoseStack();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Deb d : DEBRIS) {
            float s = d.size * (d.resting ? 1 - smooth((d.rest - 2.8f) / 0.7f) : 1);
            if (s <= 0.02f) {
                continue;
            }
            pose.pushPose();
            pose.translate(d.x - camera.x, d.y - camera.y, d.z - camera.z);
            pose.mulPose(new Quaternionf().rotationXYZ(d.rx, d.ry, d.rz));
            pose.scale(s, s, s);
            pose.translate(-0.5, -0.5, -0.5);
            at.set(Mth.floor(d.x), Mth.floor(d.y) + 1, Mth.floor(d.z));
            int light = d.heat > 0.2f ? LightTexture_FULL : LevelRenderer.getLightColor(level, at);
            br.renderSingleBlock(d.state, pose, source, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        int drawn = 0;
        Map<Long, float[]> lift = new HashMap<>();
        for (Wave w : WAVES) {
            float pr = (float) ((now - w.born) / 20 * w.speed), width = 2.6f + w.amp * 0.6f;
            float r0 = Math.max(2, pr - width * 2.2f), r1 = Math.min(w.reach, pr + width * 2.2f);
            for (float rr = r0; rr <= r1; rr += 0.7f) {
                float step = 0.7f / rr;
                for (float a = 0; a < Mth.TWO_PI; a += step) {
                    int x = Mth.floor(w.at.x + Mth.cos(a) * rr), z = Mth.floor(w.at.z + Mth.sin(a) * rr);
                    float rx = x + 0.5f - (float) w.at.x, rz = z + 0.5f - (float) w.at.z, dist = Mth.sqrt(rx * rx + rz * rz);
                    float b = (float) Math.exp(-Math.pow((dist - pr) / width, 2)) * w.amp * (1 - smooth((dist - w.reach * 0.7f) / (w.reach * 0.3f)));
                    float[] v = lift.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), q -> new float[1]);
                    if (b > v[0]) {
                        v[0] = b;
                    }
                }
            }
        }
        for (Map.Entry<Long, float[]> en : lift.entrySet()) {
            if (drawn > MAX_LIFTED) {
                break;
            }
            int x = (int) (en.getKey() >> 32), z = (int) (long) en.getKey();
            float b = en.getValue()[0];
            float s3 = hash(x, z, 3);
            float dy = b * (0.35f + 1.4f * s3);
            if (dy < 0.08f) {
                continue;
            }
            int[] y = {0};
            BlockState st = top(level, x, z, y);
            if (!drawable(st)) {
                continue;
            }
            at.set(x, y[0], z);
            float ang = b * 0.5f * (s3 - 0.5f);
            Vector3f axis = new Vector3f(hash(x, z, 1) - 0.5f, 0, hash(x, z, 2) - 0.5f);
            if (axis.lengthSquared() < 1.0E-4f) {
                axis.set(1, 0, 0);
            }
            axis.normalize();
            pose.pushPose();
            pose.translate(x + 0.5 - camera.x, y[0] + 1 + dy - camera.y, z + 0.5 - camera.z);
            pose.mulPose(new Quaternionf().rotationAxis(ang, axis));
            pose.translate(-0.5, -1, -0.5);
            br.renderSingleBlock(st, pose, source, LevelRenderer.getLightColor(level, at.above()), OverlayTexture.NO_OVERLAY);
            pose.popPose();
            drawn++;
        }
        source.endBatch();
    }

    private static final int LightTexture_FULL = 0xF000F0;

    private static void volumes(Vec3 camera, double now) {
        ShaderInstance sh = FxShaders.boomVol;
        if (sh == null || VOLS.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // far ones first, so the near ones blend over them
        List<Vol> order = new ArrayList<>(VOLS);
        order.sort((p, q) -> Double.compare(q.at.distanceToSqr(camera), p.at.distanceToSqr(camera)));
        for (Vol v : order) {
            float age = (float) (now - v.born), k = age / v.life;
            if (k >= 1 || age < 0) {
                continue;
            }
            float R, H, fade;
            if (v.ring) {
                // the page's rings: the band runs out to Rmax, thinning and fading
                float e = 1 - (float) Math.pow(1 - k, 3);
                R = Math.max(1, v.R * e);
                H = v.shape == 3 ? Math.max(0.6f, v.H * 0.35f) : v.H;
                fade = (float) Math.pow(1 - k, 1.3);
            } else {
                float g = 1 - (float) Math.pow(1 - Math.min(1, age / v.grow), 3);
                R = v.R * (0.15f + 0.85f * g) * (1 + 0.15f * k);
                H = v.H * (0.15f + 0.85f * g) * (1 + 0.3f * k);
                fade = 1 - smooth((k - 0.75f) / 0.25f);
            }
            float dtv = Math.max(0, age - v.lastAge) / 20;
            v.lastAge = age;
            v.phase += dtv * 1.4f * (1.4f - k);
            Vector3f e = new Vector3f((float) (v.at.x - camera.x), (float) (v.at.y - camera.y), (float) (v.at.z - camera.z));
            float ext = v.shape >= 2 ? R + Math.max(R * 0.35f, H * 3) : R * 1.45f;
            float top = v.shape == 1 ? H * 1.45f + R * 0.3f : v.shape == 3 ? H * 2.5f : v.shape == 2 ? H * 1.6f : H * 1.6f + k * k * H * 0.4f;
            float bottom = v.shape == 3 ? H * 2.5f : 0.6f;
            float[] mn = {e.x - ext, e.y - bottom, e.z - ext};
            float[] mx = {e.x + ext, e.y + top, e.z + ext};
            sh.safeGetUniform("E").set(e.x, e.y, e.z);
            sh.safeGetUniform("Bmin").set(mn[0], mn[1], mn[2]);
            sh.safeGetUniform("Bmax").set(mx[0], mx[1], mx[2]);
            sh.safeGetUniform("Dust").set(v.dust[0], v.dust[1], v.dust[2]);
            sh.safeGetUniform("R").set(R);
            sh.safeGetUniform("H").set(H);
            sh.safeGetUniform("Life").set(v.ring ? 0.2f : k);
            sh.safeGetUniform("Phase").set(v.phase);
            sh.safeGetUniform("Heat").set(v.heat * (1 - 0.6f * k));
            sh.safeGetUniform("Seed").set(v.seed);
            sh.safeGetUniform("Fade").set(fade);
            sh.safeGetUniform("Mode").set((float) v.mode);
            sh.safeGetUniform("Shape").set((float) v.shape);
            sh.safeGetUniform("Ground").set(v.shape == 3 ? -1.0E4f : e.y - 0.5f);
            box(sh, mn, mx);
        }
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    // The faces of the box (camera-relative) that face away from the camera (all of them with the camera inside).
    private static void box(ShaderInstance sh, float[] mn, float[] mx) {
        Vector3f[] v = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            v[i] = new Vector3f((i & 1) == 0 ? mn[0] : mx[0], (i & 2) == 0 ? mn[1] : mx[1], (i & 4) == 0 ? mn[2] : mx[2]);
        }
        Vector3f centre = new Vector3f((mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2);
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int count = 0;
        for (int[] face : faces) {
            Vector3f fc = new Vector3f(v[face[0]]).add(v[face[1]]).add(v[face[2]]).add(v[face[3]]).div(4);
            Vector3f out = new Vector3f(fc).sub(centre);
            if (out.dot(fc) <= 0) {
                continue;
            }
            for (int k : face) {
                b.addVertex(v[k].x, v[k].y, v[k].z);
            }
            count++;
        }
        MeshData mesh = b.build();
        if (count == 0 || mesh == null) {
            if (mesh != null) {
                mesh.close();
            }
            return;
        }
        RenderSystem.setShader(() -> sh);
        BufferUploader.drawWithShader(mesh);
    }

    private static float hash(int x, int z, int k) {
        long h = x * 374761393L + z * 668265263L + k * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
