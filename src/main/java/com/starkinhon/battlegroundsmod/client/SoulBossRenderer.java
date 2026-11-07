package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.client.fx.AuraEyesLayer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.vertex.PoseStack;

// Bosses are drawn with the player model and a skin, so the skill poses (PlayerModelMixin) work on them as well.
public class SoulBossRenderer<T extends SoulBoss> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private final ResourceLocation[] skins;

    // More than one skin: each entity keeps one of them, picked by its UUID (the Fighters' look variants).
    public SoulBossRenderer(EntityRendererProvider.Context context, boolean slim, String... skins) {
        super(context, new PlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5f);
        this.skins = new ResourceLocation[skins.length];
        for (int i = 0; i < skins.length; i++) {
            this.skins[i] = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/" + skins[i] + ".png");
        }
        addLayer(new AuraEyesLayer<>(this));
    }

    public SoulBossRenderer(EntityRendererProvider.Context context, String skin, boolean slim) {
        this(context, slim, skin);
    }

    // The body, then the boss's line over its head (FalaRender), when it is saying one.
    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        FalaRender.render(entity, shouldShowName(entity), partialTicks, poseStack, buffer);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return skins.length == 1 ? skins[0] : skins[Math.floorMod(entity.getUUID().hashCode(), skins.length)];
    }
}
