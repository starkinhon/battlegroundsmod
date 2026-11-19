package com.starkinhon.battlegroundsmod.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * Kento Nanami's wrapped cleaver blade (7:3), rendered with the baked 3D GLB mesh.
 */
public class NanamiBladeItem extends SwordItem {
    public NanamiBladeItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4f)));
    }
}
