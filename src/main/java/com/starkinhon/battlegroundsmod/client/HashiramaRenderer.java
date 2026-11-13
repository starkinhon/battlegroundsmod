package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.HashiramaEntity;
import com.starkinhon.battlegroundsmod.client.fx.hs.HsFx;
import com.starkinhon.battlegroundsmod.client.fx.hs.HsWood;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

// Hashirama: his skin, the Sage Mode skin (red marks) from phase 2 on, and the right arm turned to wood for the Lança
// Ramificada (the page's arm: bark growing down the arm in 0.5 s, the warning), drawn with the souls_wood shader.
public class HashiramaRenderer extends SoulBossRenderer<HashiramaEntity> {
    private static final ResourceLocation SAGE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/hashirama_sage.png");

    public HashiramaRenderer(EntityRendererProvider.Context context) {
        super(context, "hashirama", false);
        addLayer(new WoodArm(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HashiramaEntity entity) {
        return entity.sage() ? SAGE : super.getTextureLocation(entity);
    }

    static final class WoodArm extends RenderLayer<HashiramaEntity, PlayerModel<HashiramaEntity>> {
        WoodArm(RenderLayerParent<HashiramaEntity, PlayerModel<HashiramaEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, HashiramaEntity e, float limbSwing, float limbSwingAmount, float partial, float age,
                float headYaw, float headPitch) {
            float[] arm = HsFx.arm(e.getId());
            if (arm == null || e.isInvisible()) {
                return;
            }
            pose.pushPose();
            getParentModel().rightArm.translateAndRotate(pose);
            HsFx.drawArm(new org.joml.Matrix4f(pose.last().pose()), arm[0], arm[1], HsWood.bright(e.position().add(0, 1, 0)));
            pose.popPose();
        }
    }
}
