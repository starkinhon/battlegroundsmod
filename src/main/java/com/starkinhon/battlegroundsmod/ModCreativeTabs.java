package com.starkinhon.battlegroundsmod;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.starkinhon.battlegroundsmod.item.SoulItem;
import com.starkinhon.battlegroundsmod.item.UniverseItems;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The mod's creative tabs: Essentials (Souls Book, Fight Rod and everything of no universe), then one per universe
 * (UniverseItems says where each item goes).
 * Anything that fits no group goes to Essentials, so nothing ever disappears.
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ESSENTIALS = BattlegroundsMod.CREATIVE_MODE_TABS.register("battlegrounds_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.battlegroundsmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.POWER_BOOK.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.POWER_BOOK.get());
                output.accept(ModItems.FIGHT_ROD.get());
                for (Item item : modItems()) {
                    if (item != ModItems.POWER_BOOK.get() && item != ModItems.FIGHT_ROD.get() && UniverseItems.universe(item) == null) {
                        output.accept(item);
                    }
                }
            })
            .build());

    // One tab per universe (Stark, 2026-11-10: "usa isso pra separar universo por universo"): its souls first (Divine,
    // then SubAlmas), then everything else of it. The arrows above the items (client/CreativeSubTabs) flip between
    // Tudo, Almas, Armas, Itens and Ovos inside the tab.
    public static final Map<String, DeferredHolder<CreativeModeTab, CreativeModeTab>> UNIVERSES = new LinkedHashMap<>();

    static {
        String before = "battlegrounds_tab";
        for (String universe : UniverseItems.UNIVERSES) {
            String name = universe + "_tab";
            String after = before;
            UNIVERSES.put(universe, BattlegroundsMod.CREATIVE_MODE_TABS.register(name, () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.battlegroundsmod." + universe))
                    .withTabsBefore(key(after))
                    .icon(() -> icon(universe))
                    .displayItems((parameters, output) -> {
                        for (Power power : Powers.divine()) {
                            if (universe.equals(universeOf(power))) {
                                acceptSoul(output, power);
                            }
                        }
                        for (Power power : Powers.sub()) {
                            if (universe.equals(universeOf(power))) {
                                acceptSoul(output, power);
                            }
                        }
                        acceptAll(output, item -> !(item instanceof SoulItem) && universe.equals(UniverseItems.universe(item)));
                    })
                    .build()));
            before = name;
        }
    }

    private static String universeOf(Power power) {
        var soul = ModItems.SOULS.get(power.id());
        return soul == null ? null : UniverseItems.universe(soul.get());
    }

    // The tab's icon: its Divine Soul's crystal (Bleach has none yet: Byakuya's).
    private static net.minecraft.world.item.ItemStack icon(String universe) {
        String power = switch (universe) {
            case "dragonball" -> "destruction_god";
            case "naruto" -> "uchiha_ghost";
            case "jujutsu" -> "strongest";
            case "bleach" -> "byakuya";
            default -> "moon_demon";
        };
        var soul = ModItems.SOULS.get(power);
        return soul != null ? soul.get().getDefaultInstance() : ModItems.POWER_BOOK.get().getDefaultInstance();
    }

    private ModCreativeTabs() {
    }

    // Forces the class to load so the tabs above are added to the register before it fires.
    static void init() {
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
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        // The old per-universe Livros dos Desafios: one general book replaced them (Stark 2026-11-12).
        return REMOVED.contains(id) || id.startsWith("livro_desafios_");
    }

    private static ResourceKey<CreativeModeTab> key(String name) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name));
    }
}
