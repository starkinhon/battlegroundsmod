package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.power.powers.shigaraki.DecayNoise;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// X, Onda Sônica (preview v3, "MUITO FODA"): the scream leaves his mouth as a curved shell torn at the rim (34 blocks a
// second, fading by 46) with a funnel of speed lines behind it while he screams (1.25 s); the ground's top blocks jump
// as the front passes (the preview's voxels lifted by exp(-(6.5 dt)^2), drawn here as copies of the real blocks).
public final class ScreamFx {
    static final float SPEED = 34, RANGE = 46, PUSH_R = 26, HALF = 0.5f;

    private static final class S {
        final int caster;
        final double start;
        final Vec3 o, dir;

        S(int caster, double start, Vec3 o, Vec3 dir) {
            this.caster = caster;
            this.start = start;
            this.o = o;
            this.dir = dir;
        }
    }

    private static final List<S> LIST = new ArrayList<>();

    private ScreamFx() {
    }

    static boolean active() {
        return !LIST.isEmpty();
    }

    static void clear() {
        LIST.clear();
    }

    // Shigaraki.SCREAM: a = 1 the scream goes out at pos along dir.
    static void receive(int id, Vec3 pos, Vec3 dir, float a) {
        if (a < 0.5f) {
            return;
        }
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        LIST.add(new S(id, ShigFx.clock(), pos, flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize()));
        var player = Minecraft.getInstance().player;
        if (player != null && player.position().distanceTo(pos) < 30) {
            ScreenShake.start(0.6f, 26);
        }
    }

    static void render(Vec3 camera) {
        double now = ShigFx.clock();
        LIST.removeIf(s -> now - s.start > 2.3);
        ShaderInstance sh = ShigShaders.scream;
        if (LIST.isEmpty()) {
            return;
        }
        for (S s : LIST) {
            float e = (float) (now - s.start);
            ground(s, e, camera);
            if (sh == null) {
                continue;
            }
            float R = e * SPEED;
            float T = (float) (now % 1000);
            float A = e > 0 ? ShigDraw.smooth(e / 0.05f) * (float) Math.pow(ShigDraw.clamp(1 - R / RANGE, 0, 1), 0.6) : 0;
            float scr = ShigDraw.smooth(e / 0.08f) * (1 - ShigDraw.smooth((e - 1.25f) / 0.5f));
            float A2 = scr * ShigDraw.smooth(e / 0.1f);
            float len = ShigDraw.clamp(R, 0.3f, 30);
            // frame: z along the scream, x = up x z, y = z x x (three.js lookAt)
            Vec3 z = s.dir, x = new Vec3(0, 1, 0).cross(z).normalize(), y = z.cross(x);
            float ox = (float) (s.o.x - camera.x), oy = (float) (s.o.y - camera.y), oz = (float) (s.o.z - camera.z);
            sh.safeGetUniform("A").set(A);
            sh.safeGetUniform("A2").set(A2);
            sh.safeGetUniform("T").set(T);
            sh.safeGetUniform("Len").set(len);
            if (A > 0.005f) {
                sh.safeGetUniform("Mode").set(0f);
                BufferBuilder b = ShigDraw.begin();
                float r = Math.max(R, 0.3f), thMax = HALF + 0.12f;
                int ws = 96, hs = 24;
                float[][][] g = new float[hs + 1][ws + 1][];
                for (int i = 0; i <= hs; i++) {
                    float th = i / (float) hs * thMax;
                    for (int j = 0; j <= ws; j++) {
                        float ph = (float) (j / (double) ws * Math.PI * 2);
                        float px = (float) (Math.cos(ph) * Math.sin(th)), py = (float) (Math.sin(ph) * Math.sin(th)), pz = (float) Math.cos(th);
                        float wob = 1 + 0.07f * DecayNoise.vn(ph * 3, th * 9, T * 4);
                        float qx = px * r * wob, qy = py * r * wob, qz = pz * r * wob * 0.55f;
                        g[i][j] = new float[] {ox + (float) (x.x * qx + y.x * qy + z.x * qz), oy + (float) (x.y * qx + y.y * qy + z.y * qz),
                                oz + (float) (x.z * qx + y.z * qy + z.z * qz), th / HALF, (float) Math.atan2(py, px)};
                    }
                }
                grid(b, g, hs, ws);
                ShigDraw.draw(b, sh, ShigDraw.ALPHA, false, false);
            }
            if (A2 > 0.005f) {
                sh.safeGetUniform("Mode").set(1f);
                BufferBuilder b = ShigDraw.begin();
                int nr = 72, nz = 48;
                float tanH = (float) Math.tan(HALF);
                float[][][] g = new float[nz + 1][nr + 1][];
                for (int k = 0; k <= nz; k++) {
                    float zz = k / (float) nz * len;
                    for (int a = 0; a <= nr; a++) {
                        float ph = (float) (a / (double) nr * Math.PI * 2);
                        float rr = 0.22f + tanH * zz * (0.92f + 0.1f * DecayNoise.vn((float) Math.cos(ph) * 3, (float) Math.sin(ph) * 3, zz * 0.2f - T * 3));
                        float qx = (float) Math.cos(ph) * rr, qy = (float) Math.sin(ph) * rr * 0.8f;
                        g[k][a] = new float[] {ox + (float) (x.x * qx + y.x * qy + z.x * zz), oy + (float) (x.y * qx + y.y * qy + z.y * zz),
                                oz + (float) (x.z * qx + y.z * qy + z.z * zz), zz, ph};
                    }
                }
                grid(b, g, nz, nr);
                ShigDraw.draw(b, sh, ShigDraw.ALPHA, false, false);
            }
        }
    }

    private static void grid(BufferBuilder b, float[][][] g, int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float[][] q = {g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j], g[i + 1][j + 1], g[i][j + 1]};
                for (float[] p : q) {
                    ShigDraw.v(b, p[0], p[1], p[2], 1, 1, 1, 1, p[3], p[4], 0, 0, 0, 0, 0, 1, 0);
                }
            }
        }
    }

    // The ground jumping as the front passes (inside the cone, up to 26 blocks): copies of the top blocks lifted.
    private static void ground(S s, float e, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || e <= 0) {
            return;
        }
        float R = e * SPEED;
        if (R - 10 > PUSH_R) {
            return;
        }
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int drawn = 0;
        int r = (int) PUSH_R + 1;
        int cx = Mth.floor(s.o.x), cz = Mth.floor(s.o.z);
        for (int dx = -r; dx <= r && drawn < 1600; dx++) {
            for (int dz = -r; dz <= r && drawn < 1600; dz++) {
                float px = cx + dx + 0.5f - (float) s.o.x, pz = cz + dz + 0.5f - (float) s.o.z;
                float dist = Mth.sqrt(px * px + pz * pz);
                if (dist < 0.8f || dist > PUSH_R) {
                    continue;
                }
                float ang = (float) Math.acos(Mth.clamp((px * s.dir.x + pz * s.dir.z) / dist, -1, 1));
                float inC = ShigDraw.smooth((HALF + 0.25f - ang) / 0.3f);
                if (inC < 0.01f) {
                    continue;
                }
                float ax = e - dist / SPEED;
                float wave = (float) Math.exp(-Math.pow(ax * 6.5, 2));
                if (wave < 0.03f) {
                    continue;
                }
                int x = cx + dx, z = cz + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (Math.abs(y - s.o.y + 1.6) > 5) {
                    continue;
                }
                at.set(x, y, z);
                BlockState st = level.getBlockState(at);
                if (st.isAir() || st.getRenderShape() != RenderShape.MODEL || !st.getFluidState().isEmpty()) {
                    continue;
                }
                float k = 1 - dist / PUSH_R;
                float rn = hash(x, z);
                float lift = inC * k * (0.7f + 0.3f * rn) * wave;
                if (lift < 0.04f) {
                    continue;
                }
                pose.pushPose();
                pose.translate(x - camera.x, y + lift - camera.y, z - camera.z);
                mc.getBlockRenderer().renderSingleBlock(st, pose, source, LevelRenderer.getLightColor(level, at.above()), OverlayTexture.NO_OVERLAY);
                pose.popPose();
                drawn++;
            }
        }
        source.endBatch();
    }

    private static float hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }
}
