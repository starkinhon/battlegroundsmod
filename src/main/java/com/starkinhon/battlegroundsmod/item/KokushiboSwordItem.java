package com.starkinhon.battlegroundsmod.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

// Kokushibo's sword (Kyokokukamusari), a normal sword item. client/outfit/KokushiboSwordRenderer draws the 3D model
// Stark sent (blade, guard, handle) and client/outfit/KokushiboSheathLayer puts its sheath on the owner's left hip:
// empty while the sword is in hand, with the sword inside while it is only in the inventory.
// Two looks to compare: the model as sent (realistic) and a blocky, Minecraft-style remake (blocky = true).
public class KokushiboSwordItem extends SwordItem {
    public final boolean blocky;

    public KokushiboSwordItem(Properties properties, boolean blocky) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4f)));
        this.blocky = blocky;
    }

    // 1 = realistic, 2 = blocky (the sheath style synced to watchers; 0 = none).
    public int style() {
        return blocky ? 2 : 1;
    }
}
