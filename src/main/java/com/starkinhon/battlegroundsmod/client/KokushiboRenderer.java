package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.KokushiboEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// The Kokushibo boss: his kimono, and from the awakening on the kimono torn off (the manga page Stark sent: "all you did
// was tear my kimono off").
public class KokushiboRenderer extends SoulBossRenderer<KokushiboEntity> {
    private static final ResourceLocation TORN = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID,
            "textures/entity/kokushibo_despertado.png");

    public KokushiboRenderer(EntityRendererProvider.Context context) {
        super(context, "kokushibo", false);
    }

    @Override
    public ResourceLocation getTextureLocation(KokushiboEntity entity) {
        return entity.look() == KokushiboEntity.LOOK_NORMAL ? super.getTextureLocation(entity) : TORN;
    }
}
