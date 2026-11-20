// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.JoyBoyEntity;
import com.starkinhon.battlegroundsmod.client.fx.LuffyFx;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer for the Joy Boy (Luffy) boss, swapping to the Gear 5 skin while Nika is active.
 */
public class JoyBoyRenderer extends SoulBossRenderer<JoyBoyEntity> {
    private static final ResourceLocation GEAR5 = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID,
            "textures/entity/joy_boy_g5.png");

    public JoyBoyRenderer(EntityRendererProvider.Context context) {
        // both skins (joy_boy and joy_boy_g5) are slim
        super(context, "joy_boy", true);
        addLayer(new LuffyFx.Layer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(JoyBoyEntity entity) {
        return LuffyFx.isGear5(entity) ? GEAR5 : super.getTextureLocation(entity);
    }
}
