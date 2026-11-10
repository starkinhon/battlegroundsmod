package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

// The universes' NPCs: the player model with a skin (textures/entity/<id>.png).
public class NpcRenderer extends HumanoidMobRenderer<UniverseNpc, PlayerModel<UniverseNpc>> {
    private final ResourceLocation skin;

    public NpcRenderer(EntityRendererProvider.Context context, String skin) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.skin = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/" + skin + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(UniverseNpc entity) {
        return skin;
    }
}
