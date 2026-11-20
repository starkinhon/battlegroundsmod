// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.ModBlocks;
import com.starkinhon.battlegroundsmod.RopeAnchorBlockEntity;
import com.starkinhon.battlegroundsmod.client.fx.FxRenderTypes;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

// The rope bridge's railing (Stark 2026-11-20: "um fio curvo, mas um só"): one hemp rope from this post's coil to the
// other end's post, drawn like the power line's cable (two crossed ribbons, lit by the world's light) but thicker and
// twisted (the strands' two tones alternate along it). It rides about a block over the deck: down from the coil near each
// post to hand height, then the deck's own sag (the same curve SeaGen lays the planks on), so it never cuts into the
// planks nor floats over the water. Drawn while the far post is still there (or its chunk isn't loaded).
public final class RopeRenderer implements BlockEntityRenderer<RopeAnchorBlockEntity> {
    private static final float HALF = 0.05f, TIE = 11f / 16f;
    // height over the deck's top: at the posts (the coil) and along the middle
    private static final float AT_POST = TIE + 1f, HAND = 1.2f;
    private static final float[] HEMP = {0.62f, 0.52f, 0.35f}, DARK = {0.42f, 0.34f, 0.22f};

    public RopeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RopeAnchorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.linked) {
            return;
        }
        BlockPos pos = be.getBlockPos(), to = pos.offset(be.dx, be.dy, be.dz);
        if (level.isLoaded(to) && !level.getBlockState(to).is(ModBlocks.ROPE_ANCHOR.get())) {
            return;
        }
        float ex = be.dx, ez = be.dz;
        float len = (float) Math.sqrt(ex * ex + ez * ez);
        if (len < 1) {
            return;
        }
        int n = Math.max(8, (int) Math.ceil(len * 4));
        // the post is a full block (dark inside): the light over it, over the middle of the span and over the far post
        int l0 = LevelRenderer.getLightColor(level, pos.above()), l1 = LevelRenderer.getLightColor(level, to.above());
        int lm = LevelRenderer.getLightColor(level, pos.offset(be.dx / 2, be.dy / 2, be.dz / 2));
        VertexConsumer v = buffers.getBuffer(FxRenderTypes.CABLE);
        Matrix4f m = pose.last().pose();
        float px = 0.5f, py = TIE, pz = 0.5f;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float x = 0.5f + ex * t, z = 0.5f + ez * t;
            float y = y(be, t, len);
            int li = t < 0.33f ? l0 : t < 0.67f ? lm : l1;
            float[] c = (i & 1) == 0 ? HEMP : DARK;
            quad(v, m, px, py - HALF, pz, x, y - HALF, z, x, y + HALF, z, px, py + HALF, pz, li, c);
            float dx = x - px, dz = z - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
            float sx = d == 0 ? HALF : -dz / d * HALF, sz = d == 0 ? 0 : dx / d * HALF;
            quad(v, m, px - sx, py, pz - sz, x - sx, y, z - sz, x + sx, y, z + sz, px + sx, py, pz + sz, li, c);
            px = x;
            py = y;
            pz = z;
        }
    }

    // The rope's height at t (0 here, 1 at the far post), relative to this post's block: the deck (flat over each
    // pillar's top, sagging between, SeaGen.deckY) plus the rope's height over it, higher by the posts.
    private static float y(RopeAnchorBlockEntity be, float t, float len) {
        float deck;
        if (t <= be.flatA) {
            deck = 0;
        } else if (t >= 1 - be.flatB) {
            deck = be.dy;
        } else {
            float u = (t - be.flatA) / (1 - be.flatA - be.flatB);
            deck = be.dy * u - be.sag * 4 * u * (1 - u);
        }
        float near = Math.min(t, 1 - t) * len;
        float e = near >= 3 ? 0 : 1 - smooth(near / 3);
        // the post's block is 2 over the deck's planks (1 over its top)
        return deck - 1 + HAND + (AT_POST - HAND) * e;
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    private static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3, int light, float[] c) {
        v.addVertex(m, x0, y0, z0).setColor(c[0], c[1], c[2], 1f).setLight(light);
        v.addVertex(m, x1, y1, z1).setColor(c[0], c[1], c[2], 1f).setLight(light);
        v.addVertex(m, x2, y2, z2).setColor(c[0], c[1], c[2], 1f).setLight(light);
        v.addVertex(m, x3, y3, z3).setColor(c[0], c[1], c[2], 1f).setLight(light);
    }

    @Override
    public boolean shouldRenderOffScreen(RopeAnchorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(RopeAnchorBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(Math.min(p.getX(), p.getX() + be.dx), Math.min(p.getY(), p.getY() + be.dy) - 4,
                Math.min(p.getZ(), p.getZ() + be.dz), Math.max(p.getX(), p.getX() + be.dx) + 1,
                Math.max(p.getY(), p.getY() + be.dy) + 2, Math.max(p.getZ(), p.getZ() + be.dz) + 1);
    }
}
