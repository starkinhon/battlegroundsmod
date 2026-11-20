package com.starkinhon.battlegroundsmod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.TrafficLightBlock;
import com.starkinhon.battlegroundsmod.TrafficLightBlockEntity;
import com.starkinhon.battlegroundsmod.client.fx.FxRenderTypes;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// The traffic light's lit lens (TrafficLightBlock): a round disc of light just in front of the dark lens whose turn it
// is by the world's clock, with a whiter core, full bright. The block's model stays the same, so changing color costs
// no chunk rebuild.
public final class TrafficLightRenderer implements BlockEntityRenderer<TrafficLightBlockEntity> {
    private static final int SEG = 14;
    // green, yellow, red (Tokyo's green is a blue-green)
    private static final float[][] COLOR = {{0.2f, 1f, 0.72f}, {1f, 0.7f, 0.1f}, {1f, 0.16f, 0.1f}};
    // which lens (index in TrafficLightBlock.LENS_X) each light is
    private static final int[] LENS = {2, 1, 0};

    public TrafficLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TrafficLightBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(TrafficLightBlock.FACING)) {
            return;
        }
        Direction f = state.getValue(TrafficLightBlock.FACING);
        int on = TrafficLightBlock.light(level.getGameTime(), f);
        float cx = TrafficLightBlock.LENS_X[LENS[on]] / 16f, cy = 7f / 16f;
        float[] c = COLOR[on];
        float[] w = {0.55f + 0.45f * c[0], 0.55f + 0.45f * c[1], 0.55f + 0.45f * c[2]};
        VertexConsumer v = buffers.getBuffer(FxRenderTypes.CABLE);
        Matrix4f m = pose.last().pose();
        disc(v, m, f, cx, cy, 5.42f / 16f, 2.15f / 16f, c);
        disc(v, m, f, cx, cy, 5.3f / 16f, 1.1f / 16f, w);
    }

    // a disc in the plane z (the model facing north), as a fan of quads, turned to the block's facing
    private static void disc(VertexConsumer v, Matrix4f m, Direction f, float cx, float cy, float z, float r, float[] c) {
        for (int i = 0; i < SEG; i++) {
            double a0 = Math.PI * 2 * i / SEG, a1 = Math.PI * 2 * (i + 1) / SEG;
            float x0 = cx + r * (float) Math.cos(a0), y0 = cy + r * (float) Math.sin(a0);
            float x1 = cx + r * (float) Math.cos(a1), y1 = cy + r * (float) Math.sin(a1);
            put(v, m, f, cx, cy, z, c);
            put(v, m, f, x0, y0, z, c);
            put(v, m, f, x1, y1, z, c);
            put(v, m, f, x1, y1, z, c);
        }
    }

    // the north model's point turned the way the blockstate turns it (clockwise seen from above)
    private static void put(VertexConsumer v, Matrix4f m, Direction f, float x, float y, float z, float[] c) {
        float u = x - 0.5f, w = z - 0.5f, rx, rz;
        switch (f) {
            case EAST -> {
                rx = -w;
                rz = u;
            }
            case SOUTH -> {
                rx = -u;
                rz = -w;
            }
            case WEST -> {
                rx = w;
                rz = -u;
            }
            default -> {
                rx = u;
                rz = w;
            }
        }
        v.addVertex(m, rx + 0.5f, y, rz + 0.5f).setColor(c[0], c[1], c[2], 1f).setLight(LightTexture.FULL_BRIGHT);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
