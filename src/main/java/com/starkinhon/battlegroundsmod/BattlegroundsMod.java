package com.starkinhon.battlegroundsmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(BattlegroundsMod.MODID)
public class BattlegroundsMod {
    public static final String MODID = "battlegroundsmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Registers for everything the mod adds. New items, blocks, etc. go here.
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creative tab that will hold the mod's weapons and items
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BATTLEGROUNDS_TAB = CREATIVE_MODE_TABS.register("battlegrounds_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.battlegroundsmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> Items.NETHERITE_SWORD.getDefaultInstance())
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    public BattlegroundsMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Battlegrounds Mod carregado!");
    }
}
