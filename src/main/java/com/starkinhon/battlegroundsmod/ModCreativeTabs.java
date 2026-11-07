package com.starkinhon.battlegroundsmod;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import com.starkinhon.battlegroundsmod.item.SoulItem;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TieredItem;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The mod's creative tabs, one per group so the items are easy to find:
 * Essentials (Souls Book, Fight Rod), Souls, Weapons and Spawn Eggs.
 * Items are sorted by type, so new ones land in the right tab on their own.
 * Anything that fits no group goes to Essentials, so nothing ever disappears.
 */
public final class ModCreativeTabs {
    // Items of characters taken out of the game: never shown, even while their code still exists.
    // When Sukuna comes back as a Divine Soul, take his ids out of this list.
    private static final Set<String> REMOVED = Set.of(
            "ichigo_spawn_egg", "soul_ichigo",
            "urahara_spawn_egg", "soul_urahara",
            "sukuna_spawn_egg", "soul_sukuna_cuts",
            "orihime_spawn_egg", "soul_orihime");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ESSENTIALS = BattlegroundsMod.CREATIVE_MODE_TABS.register("battlegrounds_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.battlegroundsmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.POWER_BOOK.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.POWER_BOOK.get());
                output.accept(ModItems.FIGHT_ROD.get());
                for (Item item : modItems()) {
                    if (item != ModItems.POWER_BOOK.get() && item != ModItems.FIGHT_ROD.get() && group(item) == null) {
                        output.accept(item);
                    }
                }
            })
            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SOULS = BattlegroundsMod.CREATIVE_MODE_TABS.register("souls_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.battlegroundsmod.souls"))
            .withTabsBefore(key("battlegrounds_tab"))
            .icon(() -> ModItems.SOULS.get("uchiha_ghost").get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // Divine Souls first, then the SubAlmas.
                for (Power power : Powers.divine()) {
                    acceptSoul(output, power);
                }
                for (Power power : Powers.sub()) {
                    acceptSoul(output, power);
                }
            })
            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WEAPONS = BattlegroundsMod.CREATIVE_MODE_TABS.register("weapons_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.battlegroundsmod.weapons"))
            .withTabsBefore(key("souls_tab"))
            .icon(() -> ModItems.AIZEN_SWORD.get().getDefaultInstance())
            .displayItems((parameters, output) -> acceptAll(output, item -> group(item) == Group.WEAPON))
            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPAWN_EGGS = BattlegroundsMod.CREATIVE_MODE_TABS.register("spawn_eggs_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.battlegroundsmod.spawn_eggs"))
            .withTabsBefore(key("weapons_tab"))
            .icon(() -> ModItems.GOJO_SPAWN_EGG.get().getDefaultInstance())
            .displayItems((parameters, output) -> acceptAll(output, item -> group(item) == Group.SPAWN_EGG))
            .build());

    private enum Group { SOUL, WEAPON, SPAWN_EGG }

    private ModCreativeTabs() {
    }

    // Forces the class to load so the tabs above are added to the register before it fires.
    static void init() {
    }

    private static Group group(Item item) {
        if (item instanceof SoulItem) {
            return Group.SOUL;
        }
        if (item instanceof SpawnEggItem) {
            return Group.SPAWN_EGG;
        }
        if (item instanceof TieredItem) {
            return Group.WEAPON;
        }
        return null;
    }

    private static void acceptSoul(CreativeModeTab.Output output, Power power) {
        var soul = ModItems.SOULS.get(power.id());
        if (soul != null && !isRemoved(soul.get())) {
            output.accept(soul.get());
        }
    }

    private static void acceptAll(CreativeModeTab.Output output, Predicate<Item> filter) {
        for (Item item : modItems()) {
            if (filter.test(item)) {
                output.accept(item);
            }
        }
    }

    // Every mod item in registration order, minus the removed characters.
    private static Set<Item> modItems() {
        Set<Item> items = new LinkedHashSet<>();
        BattlegroundsMod.ITEMS.getEntries().forEach(holder -> {
            Item item = holder.get();
            if (!isRemoved(item)) {
                items.add(item);
            }
        });
        return items;
    }

    private static boolean isRemoved(Item item) {
        return REMOVED.contains(BuiltInRegistries.ITEM.getKey(item).getPath());
    }

    private static ResourceKey<CreativeModeTab> key(String name) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name));
    }
}
