package com.starkinhon.battlegroundsmod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.TelaoBlockEntity;
import com.starkinhon.battlegroundsmod.client.fx.FxRenderTypes;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

// The Telão's moving picture (Stark 2026-11-13): over each face of the block open to the air, a quad a hair outside it
// drawn with souls_telao, the preview's screen shader, which paints its piece of one video in world space (so the whole
// screen shows a single picture, not the same tile on every block). Faces against another Telão or a solid block are
// skipped.
public final class TelaoRenderer implements BlockEntityRenderer<TelaoBlockEntity> {
    private static final float OUT = 0.002f;

    public TelaoRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TelaoBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        VertexConsumer v = buffers.getBuffer(FxRenderTypes.TELAO);
        Matrix4f m = pose.last().pose();
        float a = -OUT, b = 1 + OUT;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.getBlockState(n).isSolidRender(level, n)) {
                continue;
            }
            switch (d) {
                case NORTH -> quad(v, m, b, 0, a, a, 0, a, a, 1, a, b, 1, a);
                case SOUTH -> quad(v, m, a, 0, b, b, 0, b, b, 1, b, a, 1, b);
                case WEST -> quad(v, m, a, 0, a, a, 0, b, a, 1, b, a, 1, a);
                case EAST -> quad(v, m, b, 0, b, b, 0, a, b, 1, a, b, 1, b);
                case UP -> quad(v, m, a, b, a, a, b, b, b, b, b, b, b, a);
                case DOWN -> quad(v, m, a, a, b, a, a, a, b, a, a, b, a, b);
            }
        }
    }

    private static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        v.addVertex(m, x0, y0, z0);
        v.addVertex(m, x1, y1, z1);
        v.addVertex(m, x2, y2, z2);
        v.addVertex(m, x3, y3, z3);
    }

    // The screens are the city's skyline: seen from far, like the buildings they hang on.
    @Override
    public int getViewDistance() {
        return 192;
    }

    @Override
    public AABB getRenderBoundingBox(TelaoBlockEntity be) {
        return new AABB(be.getBlockPos());
    }
}
