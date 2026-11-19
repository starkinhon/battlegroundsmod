package com.starkinhon.battlegroundsmod.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * Kasumi Miwa's katana, rendered with the baked 3D GLB mesh.
 */
public class MiwaSwordItem extends SwordItem {
    public MiwaSwordItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4f)));
    }
}
