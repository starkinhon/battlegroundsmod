package com.starkinhon.battlegroundsmod.client.animal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.animal.Pterodactyl;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PteroRenderer extends MobRenderer<Pterodactyl, PteroModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID,
            "textures/entity/pterodactilo.png");

    public PteroRenderer(EntityRendererProvider.Context context) {
        super(context, new PteroModel(context.bakeLayer(PteroModel.LAYER)), 0.0f);
    }

    @Override
    protected void scale(Pterodactyl entity, PoseStack pose, float partialTick) {
        // big: about four blocks wingtip to wingtip
        pose.scale(2.2f, 2.2f, 2.2f);
    }

    @Override
    public ResourceLocation getTextureLocation(Pterodactyl entity) {
        return TEXTURE;
    }
}
