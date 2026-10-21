package com.starkinhon.battlegroundsmod;

import java.util.LinkedHashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.item.PowerBookItem;
import com.starkinhon.battlegroundsmod.item.SoulItem;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModItems {
    public static final DeferredItem<PowerBookItem> POWER_BOOK = BattlegroundsMod.ITEMS.registerItem("power_book",
            PowerBookItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // One soul crystal per power, registered as soul_<power id>.
    public static final Map<String, DeferredItem<SoulItem>> SOULS = new LinkedHashMap<>();

    static {
        for (Power power : Powers.all()) {
            SOULS.put(power.id(), BattlegroundsMod.ITEMS.registerItem("soul_" + power.id(),
                    properties -> new SoulItem(power.id(), properties), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
        }
    }

    private ModItems() {
    }

    // Forces the class to load so the items above are added to the register before it fires.
    static void init() {
    }
}
