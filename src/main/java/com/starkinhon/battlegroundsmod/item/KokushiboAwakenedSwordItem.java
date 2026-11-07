package com.starkinhon.battlegroundsmod.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

// Kokushibo's sword awakened (Stark, 2026-11-07): the same Kyokokukamusari grown longer, with three curved blades
// branching off it (one up, two down), from the reference model Stark showed. For some of his forms later; for now a
// sword of its own to see it in the game. client/outfit/KokushiboSwordRenderer draws it (models/mesh/
// kokushibo_katana_long.bin, made from the normal sword's mesh). It doesn't fit in the sheath, so it is not a
// KokushiboSwordItem.
public class KokushiboAwakenedSwordItem extends SwordItem {
    public KokushiboAwakenedSwordItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5, -2.8f)));
    }
}
