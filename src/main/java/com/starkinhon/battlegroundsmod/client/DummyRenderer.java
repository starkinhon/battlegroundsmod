package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.dummy.TrainingDummy;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

// The test dummies wear one of the vanilla default skins on the player model, so combo and skill poses play on them.
public class DummyRenderer<T extends TrainingDummy> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private final ResourceLocation skin;

    public DummyRenderer(EntityRendererProvider.Context context, String vanillaSkin) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.skin = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + vanillaSkin + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return skin;
    }
}
