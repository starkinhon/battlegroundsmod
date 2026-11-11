package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.power.powers.shigaraki.Decay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// The Decadência in the blocks, as the preview's voxels do it (VOX_VS/VOX_FS/DUST_VS): each block the seed reaches dries
// grey and cracks from 0.4 s before it gets there, shakes, and at 0.5 s (when the server takes it away) its copy turns
// on its own axis, shrinks and sinks to nothing while dust puffs off it. The same walk and noise as the server
// (Decay.walk), so both agree on which block goes when. Without the gamerules nothing breaks: the blocks only crack
// and grey, and get their colour back after a while.
public final class DecayBlocks {
    private static final int MAX = 120_000, MAX_OVERLAY = 9000, MAX_COPIES = 2600;
    private static final double OVERLAY_DIST = 110, COPY_DIST = 72;

    static final class B {
        final BlockPos pos;
        final BlockState st;
        double at; // seconds (ShigFx.clock) when the Decadência gets here
        final float rn, lum;
        final boolean breaks;
        boolean dusted;

        B(BlockPos pos, BlockState st, double at, float rn, float lum, boolean breaks) {
            this.pos = pos;
            this.st = st;
            this.at = at;
            this.rn = rn;
            this.lum = lum;
            this.breaks = breaks;
        }
    }

    private static final Map<Long, B> BLOCKS = new HashMap<>();

    private DecayBlocks() {
    }

    static boolean active() {
        return !BLOCKS.isEmpty();
    }

    static void clear() {
        BLOCKS.clear();
    }

    // A seed from the server (Shigaraki.SEED).
    static void seed(int casterId, Vec3 p, Vec3 dir, float mode, float safeR, boolean breaks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity caster = casterId < 0 ? null : level.getEntity(casterId);
        double sx = caster == null ? 0 : caster.getX(), sz = caster == null ? 0 : caster.getZ();
        double now = ShigFx.clock();
        Decay.walk(level, p, (float) dir.x, (float) dir.y, Math.round(mode), dir.z, sx, sz, caster == null ? 0 : safeR,
                hit -> add(level, hit.pos(), hit.state(), now + hit.delay(), breaks));
    }

    // One block taken at a given time (the Fim do Mundo's front feeds these too).
    static void add(ClientLevel level, BlockPos pos, BlockState st, double at, boolean breaks) {
        long key = pos.asLong();
        B was = BLOCKS.get(key);
        if (was != null) {
            if (at < was.at) {
                was.at = at;
            }
            return;
        }
        if (BLOCKS.size() >= MAX) {
            return;
        }
        int col = st.getMapColor(level, pos).col;
        float lum = (((col >> 16) & 255) * 0.3f + ((col >> 8) & 255) * 0.55f + (col & 255) * 0.15f) / 255f;
        BLOCKS.put(key, new B(pos, st, at, hash(pos), lum, breaks));
    }

    static boolean has(BlockPos pos) {
        return BLOCKS.containsKey(pos.asLong());
    }

    private static float hash(BlockPos p) {
        long h = p.getX() * 374761393L + p.getY() * 668265263L + p.getZ() * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    // Dust puffing off the crumbling blocks (the preview's DUST_VS): grey, rising and drifting.
    static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            BLOCKS.clear();
            return;
        }
        double now = ShigFx.clock();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int puffs = 0;
        Iterator<B> it = BLOCKS.values().iterator();
        while (it.hasNext()) {
            B b = it.next();
            double age = now - b.at;
            if (age > (b.breaks ? 2.4 : 6.5)) {
                it.remove();
                continue;
            }
            if (b.breaks && !b.dusted && age > 0.55) {
                b.dusted = true;
                if (puffs < 260 && cam.distanceToSqr(b.pos.getX(), b.pos.getY(), b.pos.getZ()) < 80 * 80) {
                    puffs++;
                    var r = level.getRandom();
                    int n = 1 + (b.rn > 0.5f ? 1 : 0);
                    for (int i = 0; i < n; i++) {
                        int g = 0x8A + r.nextInt(0x28);
                        int color = g << 16 | (int) (g * 0.94f) << 8 | (int) (g * 0.86f);
                        level.addParticle(ModParticles.dust(color, 1.2f + r.nextFloat() * 1.6f, 28 + r.nextInt(32), 0.93f), b.pos.getX() + r.nextDouble(),
                                b.pos.getY() + r.nextDouble(), b.pos.getZ() + r.nextDouble(), 0.035 + (r.nextDouble() - 0.5) * 0.03,
                                0.025 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.03);
                    }
                }
            }
        }
    }

    static void render(Vec3 camera) {
        ShaderInstance sh = ShigShaders.decay;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (BLOCKS.isEmpty() || sh == null || level == null) {
            return;
        }
        double now = ShigFx.clock();
        float[] o = ShigDraw.origin(camera);
        sh.safeGetUniform("Origin").set(o[0], o[1], o[2]);
        // the copies falling apart, drawn with the game's own block models
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        BufferBuilder over = ShigDraw.begin();
        int overlays = 0, copies = 0;
        for (B b : BLOCKS.values()) {
            double age = now - b.at;
            if (age <= -0.4) {
                continue;
            }
            double cx = b.pos.getX() + 0.5 - camera.x, cy = b.pos.getY() + 0.5 - camera.y, cz = b.pos.getZ() + 0.5 - camera.z;
            double d2 = cx * cx + cy * cy + cz * cz;
            float light = light(level, b.pos);
            if (!b.breaks || age < 0.5) {
                if (overlays >= MAX_OVERLAY || d2 > OVERLAY_DIST * OVERLAY_DIST) {
                    continue;
                }
                if (!b.breaks && level.getBlockState(b.pos) != b.st) {
                    continue;
                }
                float fade = b.breaks ? 1 : 1 - ShigDraw.smooth((float) (age - 4.5) / 1.5f);
                float sx = 0, sz = 0;
                if (age > 0 && age < 0.5) {
                    float t = (float) now, k = ShigDraw.smooth((float) age / 0.5f) * 0.025f;
                    sx = (float) Math.sin(t * 61 + b.rn * 30) * k;
                    sz = (float) Math.cos(t * 53 + b.rn * 17) * k;
                }
                cube(over, (float) cx + sx, (float) cy, (float) cz + sz, 1.004f, null, (float) age, b, light, fade);
                overlays++;
                continue;
            }
            float life = 0.8f + b.rn * 0.7f;
            float k = (float) Math.min(1, (age - 0.5) / life);
            if (k >= 1 || copies >= MAX_COPIES || d2 > COPY_DIST * COPY_DIST) {
                continue;
            }
            Vector3f ax = new Vector3f(b.rn - 0.5f, 0.35f, (b.rn * 7.3f) % 1 - 0.5f).normalize();
            float an = k * (1.5f + b.rn * 2);
            Quaternionf q = new Quaternionf().rotationAxis(an, ax);
            float s = 1 - k * k;
            float drop = k * k * 0.7f;
            if (b.st.getRenderShape() == RenderShape.MODEL && b.st.getFluidState().isEmpty()) {
                pose.pushPose();
                pose.translate(cx, cy - drop, cz);
                pose.mulPose(q);
                pose.scale(s, s, s);
                pose.translate(-0.5, -0.5, -0.5);
                mc.getBlockRenderer().renderSingleBlock(b.st, pose, source, LevelRenderer.getLightColor(level, b.pos), OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            cube(over, (float) cx, (float) (cy - drop), (float) cz, s * 1.006f, q, (float) age, b, light, 1);
            copies++;
        }
        source.endBatch();
        ShigDraw.draw(over, sh, ShigDraw.ALPHA, false, false);
    }

    private static float light(ClientLevel level, BlockPos pos) {
        int raw = 0;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            raw = Math.max(raw, level.getMaxLocalRawBrightness(pos.relative(d)));
        }
        float l = raw / 15f;
        return 0.18f + 0.82f * l * l;
    }

    // A cube of side size around (cx, cy, cz), turned by q, carrying the block's numbers for souls_shig_decay.
    private static void cube(BufferBuilder b, float cx, float cy, float cz, float size, Quaternionf q, float age, B blk, float light, float fade) {
        float h = size / 2;
        float[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        Vector3f p = new Vector3f(), nn = new Vector3f();
        for (float[] f : n) {
            // two axes across the face
            float[] u = f[0] != 0 ? new float[] {0, 1, 0} : new float[] {1, 0, 0};
            float[] w = {f[1] * u[2] - f[2] * u[1], f[2] * u[0] - f[0] * u[2], f[0] * u[1] - f[1] * u[0]};
            float[][] c = new float[4][3];
            int[][] s = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int i = 0; i < 4; i++) {
                for (int k = 0; k < 3; k++) {
                    c[i][k] = (f[k] + u[k] * s[i][0] + w[k] * s[i][1]) * h;
                }
            }
            nn.set(f[0], f[1], f[2]);
            if (q != null) {
                nn.rotate(q);
            }
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int i : order) {
                p.set(c[i][0], c[i][1], c[i][2]);
                if (q != null) {
                    p.rotate(q);
                }
                ShigDraw.v(b, cx + p.x, cy + p.y, cz + p.z, blk.lum, light, fade, 1, age, blk.rn, 0, 0, 0, 0, nn.x, nn.y, nn.z);
            }
        }
    }
}
