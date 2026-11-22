// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.power.powers.aizen.AizenIllusion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

// Kyoka Suigetsu's clone: drawn exactly like the player it copies (their skin, wide or slim arms, their armor and items).
public class AizenIllusionRenderer extends HumanoidMobRenderer<AizenIllusion, PlayerModel<AizenIllusion>> {
    private final PlayerModel<AizenIllusion> wide;
    private final PlayerModel<AizenIllusion> slim;

    public AizenIllusionRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = getModel();
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
    }

    private PlayerSkin skin(AizenIllusion entity) {
        String boss = entity.bossSkin();
        if (!boss.isEmpty()) {
            // A boss's clone: the boss's own skin, wide arms (as SoulBossRenderer draws it).
            return new PlayerSkin(ResourceLocation.fromNamespaceAndPath(com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID,
                    "textures/entity/" + boss + ".png"), null, null, null, PlayerSkin.Model.WIDE, false);
        }
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : entity.owner().map(connection::getPlayerInfo).orElse(null);
        return info != null ? info.getSkin() : entity.owner().map(DefaultPlayerSkin::get).orElseGet(() -> DefaultPlayerSkin.get(entity.getUUID()));
    }

    @Override
    public void render(AizenIllusion entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        model = skin(entity).model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AizenIllusion entity) {
        return skin(entity).texture();
    }
}
