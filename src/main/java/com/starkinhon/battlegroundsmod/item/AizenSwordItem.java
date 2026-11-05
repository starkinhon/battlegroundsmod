package com.starkinhon.battlegroundsmod.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

// Aizen's sword (Kyoka Suigetsu), a normal sword item. The 3D model approved in the Armario/Guarda-roupa viewer is drawn
// by client/outfit/AizenSwordRenderer in hand (first and third person), in the inventory, on the ground and in frames.
// Holding it still lets the power's punch combo run (CombatMode.freeHand).
public class AizenSwordItem extends SwordItem {
    public AizenSwordItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4f)));
    }
}
