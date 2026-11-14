package com.starkinhon.battlegroundsmod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.PowerLineBlock;
import com.starkinhon.battlegroundsmod.PowerLineBlockEntity;
import com.starkinhon.battlegroundsmod.client.fx.FxRenderTypes;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// The power line's cable (Stark 2026-11-14): from the insulator on top of the crossarm to the next power line along the
// street (the axis across the arm), within 28 blocks and a step of height, a catenary that sags with the span, drawn as
// two crossed dark ribbons (seen as a round wire from any side) lit by the world's light. Only the line on the west or
// north end draws it, so each span is drawn once. The partner is looked for again every few seconds while there is none.
public final class PowerLineRenderer implements BlockEntityRenderer<PowerLineBlockEntity> {
    private static final int REACH = 28, SEGMENTS = 20;
    private static final float HALF = 0.035f, TOP = 1.0f;

    public PowerLineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PowerLineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(PowerLineBlock.CABLE) || !state.getValue(PowerLineBlock.CABLE)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        if (be.cableTo != null && !isCable(level.getBlockState(be.cableTo))) {
            be.cableTo = null;
        }
        if (be.cableTo == null && level.getGameTime() >= be.nextLook) {
            be.nextLook = level.getGameTime() + 60;
            be.cableTo = partner(level, pos, state.getValue(PowerLineBlock.FACING));
        }
        if (be.cableTo == null) {
            return;
        }
        BlockPos to = be.cableTo;
        float ex = to.getX() - pos.getX(), ey = to.getY() - pos.getY(), ez = to.getZ() - pos.getZ();
        float len = (float) Math.sqrt(ex * ex + ez * ez);
        float sag = 0.045f * len + 0.15f;
        int l0 = light, l1 = LevelRenderer.getLightColor(level, to);
        VertexConsumer v = buffers.getBuffer(FxRenderTypes.CABLE);
        Matrix4f m = pose.last().pose();
        float px = 0.5f, py = TOP, pz = 0.5f;
        for (int i = 1; i <= SEGMENTS; i++) {
            float t = i / (float) SEGMENTS;
            float x = 0.5f + ex * t, z = 0.5f + ez * t;
            float y = TOP + ey * t - sag * (float) Math.sin(Math.PI * t);
            int li = t < 0.5f ? l0 : l1;
            // a vertical ribbon and a flat one
            quad(v, m, px, py - HALF, pz, x, y - HALF, z, x, y + HALF, z, px, py + HALF, pz, li);
            float dx = x - px, dz = z - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
            float sx = d == 0 ? HALF : -dz / d * HALF, sz = d == 0 ? 0 : dx / d * HALF;
            quad(v, m, px - sx, py, pz - sz, x - sx, y, z - sz, x + sx, y, z + sz, px + sx, py, pz + sz, li);
            px = x;
            py = y;
            pz = z;
        }
    }

    private static boolean isCable(BlockState s) {
        return s.is(JujutsuBlocks.POWER_LINE.get()) && s.getValue(PowerLineBlock.CABLE);
    }

    // The next line along the street, east or south of this one (the axis across the arm), at the same height or one off.
    private static BlockPos partner(Level level, BlockPos pos, Direction facing) {
        Direction along = facing.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = 2; k <= REACH; k++) {
            for (int dy : new int[] {0, -1, 1}) {
                p.set(pos.getX() + along.getStepX() * k, pos.getY() + dy, pos.getZ() + along.getStepZ() * k);
                if (!level.isLoaded(p)) {
                    return null;
                }
                BlockState s = level.getBlockState(p);
                if (isCable(s) && s.getValue(PowerLineBlock.FACING).getAxis() == facing.getAxis()) {
                    return p.immutable();
                }
            }
        }
        return null;
    }

    private static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3, int light) {
        v.addVertex(m, x0, y0, z0).setColor(0.09f, 0.09f, 0.1f, 1f).setLight(light);
        v.addVertex(m, x1, y1, z1).setColor(0.09f, 0.09f, 0.1f, 1f).setLight(light);
        v.addVertex(m, x2, y2, z2).setColor(0.09f, 0.09f, 0.1f, 1f).setLight(light);
        v.addVertex(m, x3, y3, z3).setColor(0.09f, 0.09f, 0.1f, 1f).setLight(light);
    }

    @Override
    public boolean shouldRenderOffScreen(PowerLineBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(PowerLineBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p).inflate(REACH, 3, REACH);
    }
}
