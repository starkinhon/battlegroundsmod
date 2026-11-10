package com.starkinhon.battlegroundsmod.client.animal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.animal.Dinosaur;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

// The same theropod, small for the raptor and scaled up for the tyrannosaur, each with its own skin.
public class DinoRenderer extends MobRenderer<Dinosaur, DinoModel> {
    private final ResourceLocation texture;
    private final float size;

    public DinoRenderer(EntityRendererProvider.Context context, String skin, float size) {
        super(context, new DinoModel(context.bakeLayer(DinoModel.LAYER)), 0.45f * size);
        this.texture = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/" + skin + ".png");
        this.size = size;
    }

    @Override
    protected void scale(Dinosaur entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(size, size, size);
    }

    @Override
    public ResourceLocation getTextureLocation(Dinosaur entity) {
        return texture;
    }
}
