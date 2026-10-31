package com.starkinhon.battlegroundsmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.starkinhon.battlegroundsmod.command.BossCommand;
import com.starkinhon.battlegroundsmod.command.SoulCommand;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.fight.FightRod;
import com.starkinhon.battlegroundsmod.power.PowerEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
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
            .icon(() -> ModItems.SOULS.get("uchiha_ghost").get().getDefaultInstance())
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    public BattlegroundsMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModPayloads::register);
        modEventBus.addListener(ModEntities::onAttributes);

        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.init();
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);

        PowerEvents.register();
        FightRod.register();
        SoulCommand.addSubcommand(BossCommand::bossbar);
        SoulCommand.addSubcommand(BossCommand::boss);
        NeoForge.EVENT_BUS.addListener(SoulCommand::register);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Battlegrounds Mod carregado!");
    }
}
