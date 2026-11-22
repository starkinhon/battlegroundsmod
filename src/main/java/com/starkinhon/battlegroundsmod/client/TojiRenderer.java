// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.boss.TojiEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

// Toji: his skin (black shirt, white trousers, the scar at the mouth, the worm round his shoulders), a little taller and
// broader than a player. The weapons in his hands are the mod's own items (the Lança Invertida's mesh, the Nuvem
// Brincalhona's chain physics), drawn by their renderers.
public class TojiRenderer extends SoulBossRenderer<TojiEntity> {
    public TojiRenderer(EntityRendererProvider.Context context) {
        super(context, "toji", false);
    }

    @Override
    protected void scale(TojiEntity entity, PoseStack pose, float partial) {
        super.scale(entity, pose, partial);
        pose.scale(1.08f, 1.06f, 1.08f);
    }
}
