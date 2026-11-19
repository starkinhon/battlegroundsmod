package com.starkinhon.battlegroundsmod;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.starkinhon.battlegroundsmod.item.SoulItem;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TieredItem;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The mod's creative tabs, separated by utility:
 * Souls & Books, Weapons, Items & Consumables, Blocks & Decoration, Boss Spawn Eggs, Mob Spawn Eggs, and Dev & Testing.
 * Every non-removed mod item falls into exactly one tab (Items & Consumables is the catch-all), so nothing ever disappears.
 */
public final class ModCreativeTabs {
    // Items of characters taken out of the game: never shown, even while their code still exists.
    // When Sukuna comes back as a Divine Soul, take his ids out of this list.
    private static final Set<String> REMOVED = Set.of(
            // gone from the game (Stark 2026-11-13); still registered so old worlds keep loading
            "cristal_multiversal",
            "ichigo_spawn_egg", "soul_ichigo",
            "urahara_spawn_egg", "soul_urahara",
            "soul_sukuna_cuts",
            "orihime_spawn_egg", "soul_orihime",
            // Luffy and Saitama: hidden until they are ready.
            "soul_rubber_boy", "soul_one_punch");

    // Weapons that are not TieredItem (thrown or special melee).
    private static final Set<String> WEAPON_IDS = Set.of(
            "nuvem_brincalhona", "shuriken", "kunai", "papel_bomba");

    // Boss spawn eggs (SoulBoss entities in ModEntities), separated from regular mobs.
    private static final Set<String> BOSS_EGG_IDS = Set.of(
            "madara_spawn_egg", "bills_spawn_egg", "gojo_spawn_egg", "sukuna_spawn_egg",
            "joy_boy_spawn_egg", "saitama_spawn_egg", "hashirama_spawn_egg", "freeza_total_spawn_egg",
            "shigaraki_spawn_egg", "makima_spawn_egg", "kokushibo_spawn_egg");

    // Testing and sandbox tools.
    private static final Set<String> DEV_IDS = Set.of(
            "fight_rod", "dummy_spawn_egg", "dummy_attacker_spawn_egg");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SOULS_AND_BOOKS = BattlegroundsMod.CREATIVE_MODE_TABS.register("souls_books_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.battlegroundsmod.souls_books"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.POWER_BOOK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        Set<Item> added = new LinkedHashSet<>();
                        acceptUnique(output, added, ModItems.POWER_BOOK.get());
                        acceptUnique(output, added, ModItems.CHALLENGE_BOOK.get());
                        for (Power power : Powers.divine()) {
                            acceptSoul(output, added, power);
                        }
                        for (Power power : Powers.sub()) {
                            acceptSoul(output, added, power);
                        }
                        for (Item item : modItems()) {
                            if (isSoulOrBook(item)) {
                                acceptUnique(output, added, item);
                            }
                        }
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WEAPONS = BattlegroundsMod.CREATIVE_MODE_TABS.register("weapons_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.battlegroundsmod.weapons"))
                    .withTabsBefore(key("souls_books_tab"))
                    .icon(() -> ModItems.NICHIRIN_SWORD.get().getDefaultInstance())
                    .displayItems((parameters, output) -> acceptAll(output,
                            item -> !isDev(item) && !isSoulOrBook(item) && isWeapon(item)))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS = BattlegroundsMod.CREATIVE_MODE_TABS.register("items_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.battlegroundsmod.items"))
                    .withTabsBefore(key("weapons_tab"))
                    .icon(() -> ModItems.SENZU.get().getDefaultInstance())
                    .displayItems((parameters, output) -> acceptAll(output,
                            item -> !isDev(item) && !isSoulOrBook(item) && !isWeapon(item)
                                    && !(item instanceof SpawnEggItem) && !(item instanceof BlockItem)))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS = BattlegroundsMod.CREATIVE_MODE_TABS.register("blocks_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.battlegroundsmod.blocks"))
                    .withTabsBefore(key("items_tab"))
                    .icon(() -> ModBlocks.VERMILION_WOOD.asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        Set<Item> added = new LinkedHashSet<>();
                        for (Item item : modItems()) {
                            if (!isDev(item) && item instanceof BlockItem) {
                                acceptUnique(output, added, item);
                            }
                        }
                        ModBlocks.BLOCK_ITEMS.getEntries().forEach(holder -> {
                            Item item = holder.get();
                            if (!isRemoved(item)) {
                                acceptUnique(output, added, item);
                            }
                        });
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BOSS_EGGS = BattlegroundsMod.CREATIVE_MODE_TABS.register("boss_eggs_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.battlegroundsmod.boss_eggs"))
                    .withTabsBefore(key("blocks_tab"))
                    .icon(() -> ModItems.GOJO_SPAWN_EGG.get().getDefaultInstance())
                    .displayItems((parameters, output) -> acceptAll(output, ModCreativeTabs::isBossEgg))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOB_EGGS = BattlegroundsMod.CREATIVE_MODE_TABS.register("mob_eggs_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.battlegroundsmod.mob_eggs"))
                    .withTabsBefore(key("boss_eggs_tab"))
                    .icon(() -> ModItems.SAIBAMAN_SPAWN_EGG.get().getDefaultInstance())
                    .displayItems((parameters, output) -> acceptAll(output,
                            item -> item instanceof SpawnEggItem && !isBossEgg(item) && !isDev(item)))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEV = BattlegroundsMod.CREATIVE_MODE_TABS.register("dev_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.battlegroundsmod.dev"))
                    .withTabsBefore(key("mob_eggs_tab"))
                    .icon(() -> ModItems.FIGHT_ROD.get().getDefaultInstance())
                    .displayItems((parameters, output) -> acceptAll(output, ModCreativeTabs::isDev))
                    .build());

    // All mod creative tabs, used by CreativeSubTabs to attach universe filter arrows where applicable.
    public static final List<DeferredHolder<CreativeModeTab, CreativeModeTab>> ALL = List.of(
            SOULS_AND_BOOKS, WEAPONS, ITEMS, BLOCKS, BOSS_EGGS, MOB_EGGS, DEV);

    private ModCreativeTabs() {
    }

    // Forces the class to load so the tabs above are added to the register before it fires.
    static void init() {
    }

    private static boolean isSoulOrBook(Item item) {
        return item instanceof SoulItem
                || item == ModItems.POWER_BOOK.get()
                || item == ModItems.CHALLENGE_BOOK.get();
    }

    private static boolean isWeapon(Item item) {
        return item instanceof TieredItem || WEAPON_IDS.contains(id(item));
    }

    private static boolean isBossEgg(Item item) {
        return item instanceof SpawnEggItem && BOSS_EGG_IDS.contains(id(item));
    }

    private static boolean isDev(Item item) {
        return DEV_IDS.contains(id(item));
    }

    private static void acceptUnique(CreativeModeTab.Output output, Set<Item> added, Item item) {
        if (!isRemoved(item) && added.add(item)) {
            output.accept(item);
        }
    }

    private static void acceptSoul(CreativeModeTab.Output output, Set<Item> added, Power power) {
        var soul = ModItems.SOULS.get(power.id());
        if (soul != null) {
            acceptUnique(output, added, soul.get());
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
        String id = id(item);
        // The old per-universe Livros dos Desafios: one general book replaced them (Stark 2026-11-12).
        return REMOVED.contains(id) || id.startsWith("livro_desafios_");
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    private static ResourceKey<CreativeModeTab> key(String name) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name));
    }
}
