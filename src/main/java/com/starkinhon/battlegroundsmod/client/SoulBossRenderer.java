package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.client.fx.Aura;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

// Bosses are drawn with the player model and a skin, so the skill poses (PlayerModelMixin) work on them as well.
public class SoulBossRenderer<T extends SoulBoss> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private final ResourceLocation skin;

    public SoulBossRenderer(EntityRendererProvider.Context context, String skin, boolean slim) {
        super(context, new PlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5f);
        this.skin = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/" + skin + ".png");
        addLayer(new EyesLayer());
    }

    // Draws nothing: tells the aura where the eyes are this frame, from the head as it is really posed.
    private final class EyesLayer extends RenderLayer<T, PlayerModel<T>> {
        EyesLayer() {
            super(SoulBossRenderer.this);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T boss, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Aura.recordEyes(boss, poseStack, getParentModel().head);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return skin;
    }
}
