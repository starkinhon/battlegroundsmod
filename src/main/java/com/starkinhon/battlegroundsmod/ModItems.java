package com.starkinhon.battlegroundsmod;

import com.starkinhon.battlegroundsmod.item.PowerBookItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModItems {
    public static final DeferredItem<PowerBookItem> POWER_BOOK = BattlegroundsMod.ITEMS.registerItem("power_book",
            PowerBookItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    private ModItems() {
    }

    // Forces the class to load so the items above are added to the register before it fires.
    static void init() {
    }
}
