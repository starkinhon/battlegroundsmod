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
        super(context, new PlayerModel<UniverseNpc>(context.bakeLayer(ModelLayers.PLAYER), false) {
            // in the talk scene (client/TalkScreen) the NPC gestures while its line types
            @Override
            public void setupAnim(UniverseNpc npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
                super.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                float[] g = TalkScreen.gesture(npc.getId(), ageInTicks);
                if (g != null) {
                    rightArm.xRot = g[0];
                    rightArm.zRot = g[1];
                    leftArm.xRot = g[2];
                    leftArm.zRot = g[3];
                    rightSleeve.copyFrom(rightArm);
                    leftSleeve.copyFrom(leftArm);
                }
            }
        }, 0.5f);
        this.skin = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/" + skin + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(UniverseNpc entity) {
        return skin;
    }
}
