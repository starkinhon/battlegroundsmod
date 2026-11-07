package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NarutoClone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

// Naruto's shadow clone: drawn exactly like whoever made it. A player's skin (wide or slim arms), or a mob Naruto's own
// texture.
public class NarutoCloneRenderer extends HumanoidMobRenderer<NarutoClone, PlayerModel<NarutoClone>> {
    private final PlayerModel<NarutoClone> wide;
    private final PlayerModel<NarutoClone> slim;

    public NarutoCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = getModel();
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
    }

    private PlayerSkin skin(NarutoClone entity) {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : entity.owner().map(connection::getPlayerInfo).orElse(null);
        return info != null ? info.getSkin() : entity.owner().map(DefaultPlayerSkin::get).orElseGet(() -> DefaultPlayerSkin.get(entity.getUUID()));
    }

    private Entity ownerMob(NarutoClone entity) {
        var level = Minecraft.getInstance().level;
        Entity owner = level == null ? null : level.getEntity(entity.ownerId());
        return owner != null && !(owner instanceof Player) && !(owner instanceof NarutoClone) ? owner : null;
    }

    @Override
    public void render(NarutoClone entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        model = ownerMob(entity) == null && skin(entity).model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ResourceLocation getTextureLocation(NarutoClone entity) {
        Entity mob = ownerMob(entity);
        if (mob != null) {
            var renderer = (net.minecraft.client.renderer.entity.EntityRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
            return renderer.getTextureLocation(mob);
        }
        return skin(entity).texture();
    }
}
