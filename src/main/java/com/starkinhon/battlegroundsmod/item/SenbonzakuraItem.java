package com.starkinhon.battlegroundsmod.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * Byakuya Kuchiki's Zanpakuto (Senbonzakura), rendered with the baked 3D GLB mesh.
 */
public class SenbonzakuraItem extends SwordItem {
    public SenbonzakuraItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4f)));
    }
}
