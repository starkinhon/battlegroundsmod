package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.SaitamaEntity;
import com.starkinhon.battlegroundsmod.client.fx.OpmFx;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer for the Saitama boss, swapping to the Modo Serio skin while Serious Mode is active.
 */
public class SaitamaRenderer extends SoulBossRenderer<SaitamaEntity> {
    private static final ResourceLocation SERIOUS = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID,
            "textures/entity/saitama_serio.png");

    public SaitamaRenderer(EntityRendererProvider.Context context) {
        super(context, "saitama", false);
    }

    @Override
    public ResourceLocation getTextureLocation(SaitamaEntity entity) {
        return OpmFx.isSerious(entity) ? SERIOUS : super.getTextureLocation(entity);
    }
}
