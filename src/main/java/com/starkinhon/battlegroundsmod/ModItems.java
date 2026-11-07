package com.starkinhon.battlegroundsmod;

import java.util.LinkedHashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.item.AizenSwordItem;
import com.starkinhon.battlegroundsmod.item.FightRodItem;
import com.starkinhon.battlegroundsmod.item.PowerBookItem;
import com.starkinhon.battlegroundsmod.item.SoulInsectItem;
import com.starkinhon.battlegroundsmod.item.SoulItem;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModItems {
    public static final DeferredItem<PowerBookItem> POWER_BOOK = BattlegroundsMod.ITEMS.registerItem("power_book",
            PowerBookItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // Pick two mobs and make them fight (fight/FightRod).
    public static final DeferredItem<FightRodItem> FIGHT_ROD = BattlegroundsMod.ITEMS.registerItem("fight_rod",
            FightRodItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // Aizen's sword: the model from the Armario Souls viewer, as a normal (unbreakable) sword.
    public static final DeferredItem<AizenSwordItem> AIZEN_SWORD = BattlegroundsMod.ITEMS.registerItem("aizen_sword",
            AizenSwordItem::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant().component(DataComponents.UNBREAKABLE, new Unbreakable(true)));

    // Eaten once: never hungry again, never healed by it (item/SoulInsectItem).
    public static final DeferredItem<SoulInsectItem> SOUL_INSECT = BattlegroundsMod.ITEMS.registerItem("soul_insect",
            SoulInsectItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
                    .food(new FoodProperties.Builder().nutrition(0).saturationModifier(0).alwaysEdible().build()));

    public static final DeferredItem<DeferredSpawnEggItem> MADARA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("madara_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MADARA, 0x1A1A1F, 0xD12F2F, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BILLS_SPAWN_EGG = BattlegroundsMod.ITEMS.register("bills_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BILLS, 0x9B30FF, 0xF2C94C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GOJO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("gojo_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GOJO, 0x14161C, 0x4FC3FF, new Item.Properties()));

    // SubAlma and Fighter mobs.
    public static final DeferredItem<DeferredSpawnEggItem> BYAKUYA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("byakuya_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BYAKUYA, 0x1B1B22, 0xF2B6C8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> RUKIA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("rukia_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.RUKIA, 0x15151C, 0xCFEFFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FIGHTER_HUMANO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("fighter_humano_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FIGHTER_HUMANO, 0x8A6A4F, 0x3E6FB0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FIGHTER_RUA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("fighter_rua_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FIGHTER_RUA, 0x8A6A4F, 0xC0392B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FIGHTER_QUARTEIRAO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("fighter_quarteirao_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FIGHTER_QUARTEIRAO, 0x8A6A4F, 0x2C2C2C, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> DUMMY_SPAWN_EGG = BattlegroundsMod.ITEMS.register("dummy_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DUMMY, 0xC8A165, 0x6B4A2B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> DUMMY_ATTACKER_SPAWN_EGG = BattlegroundsMod.ITEMS.register("dummy_attacker_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DUMMY_ATTACKER, 0xC8A165, 0xC0392B, new Item.Properties()));

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
