// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.TodoStone;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

// Todo's stones (power/powers/sub/jj/TodoStone): the block they were torn from, as in the preview a box a little uneven
// on each stone (height 0.8-1.1, depth 0.85-1.15 of its size), tumbling while it flies and settling on a face.
public class TodoStoneRenderer extends EntityRenderer<TodoStone> {
    private final BlockRenderDispatcher blocks;

    public TodoStoneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.3f;
    }

    @Override
    public void render(TodoStone stone, float yaw, float partial, PoseStack pose, MultiBufferSource buffer, int light) {
        BlockState state = stone.blockState();
        if (state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        float s = stone.size() * 0.9f;
        int id = stone.getId();
        float sy = 0.8f + 0.3f * frac(id * 0.7548777f);
        float sz = 0.85f + 0.3f * frac(id * 0.5698403f);
        pose.pushPose();
        pose.translate(0, s * 0.5f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(stone.getYRot()));
        pose.mulPose(Axis.XP.rotation(Mth.lerp(partial, stone.rotXO, stone.rotX)));
        pose.mulPose(Axis.ZP.rotation(Mth.lerp(partial, stone.rotZO, stone.rotZ)));
        pose.scale(s, s * sy, s * sz);
        pose.translate(-0.5f, -0.5f, -0.5f);
        blocks.renderSingleBlock(state, pose, buffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(stone, yaw, partial, pose, buffer, light);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    @Override
    public ResourceLocation getTextureLocation(TodoStone stone) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
