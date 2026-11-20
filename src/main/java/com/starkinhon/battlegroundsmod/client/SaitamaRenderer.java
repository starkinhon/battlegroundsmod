// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.SaitamaEntity;
import com.starkinhon.battlegroundsmod.client.fx.OpmFx;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer for the Saitama boss, swapping to the Modo Serio skin while Serious Mode is active. The two skins have different
 * arms (saitama.png slim, saitama_serio.png wide), so the model swaps with the skin.
 */
public class SaitamaRenderer extends SoulBossRenderer<SaitamaEntity> {
    private static final ResourceLocation SERIOUS = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID,
            "textures/entity/saitama_serio.png");
    private final PlayerModel<SaitamaEntity> slim;
    private final PlayerModel<SaitamaEntity> wide;

    public SaitamaRenderer(EntityRendererProvider.Context context) {
        super(context, "saitama", true);
        slim = model;
        wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        // the serious face: glowing eyes and the shadow over the brow
        addLayer(new com.starkinhon.battlegroundsmod.client.fx.OpmEyesLayer<>(this));
    }

    @Override
    public void render(SaitamaEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        model = OpmFx.isSerious(entity) ? wide : slim;
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SaitamaEntity entity) {
        return OpmFx.isSerious(entity) ? SERIOUS : super.getTextureLocation(entity);
    }
}
