package com.starkinhon.battlegroundsmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.starkinhon.battlegroundsmod.boss.BossRules;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.command.BossCommand;
import com.starkinhon.battlegroundsmod.command.SoulCommand;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.fight.FightRod;
import com.starkinhon.battlegroundsmod.power.PowerEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(BattlegroundsMod.MODID)
public class BattlegroundsMod {
    public static final String MODID = "battlegroundsmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Registers for everything the mod adds. New items, blocks, etc. go here.
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // The creative tabs themselves live in ModCreativeTabs.

    public BattlegroundsMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModPayloads::register);
        modEventBus.addListener(ModEntities::onAttributes);
        modEventBus.addListener(ModEntities::onSpawnPlacements);

        ModBlocks.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.init();
        ModCreativeTabs.init();
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);
        com.starkinhon.battlegroundsmod.world.ModWorldgen.register(modEventBus);

        BossRules.init();
        com.starkinhon.battlegroundsmod.power.powers.kokushibo.OniBody.init();
        BlockRules.init();
        com.starkinhon.battlegroundsmod.power.powers.kokushibo.MoonImpactRule.init();
        PowerEvents.register();
        com.starkinhon.battlegroundsmod.power.Progression.register();
        com.starkinhon.battlegroundsmod.power.Achievements.register();
        com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.register();
        com.starkinhon.battlegroundsmod.challenge.Universes.register();
        com.starkinhon.battlegroundsmod.universe.DragonBallWorld.register();
        com.starkinhon.battlegroundsmod.universe.shenlong.ShenlongServer.register();
        com.starkinhon.battlegroundsmod.universe.DbQuests.register();
        com.starkinhon.battlegroundsmod.universe.Flight.register();
        com.starkinhon.battlegroundsmod.universe.NarutoWorld.register();
        com.starkinhon.battlegroundsmod.universe.SageMode.register();
        com.starkinhon.battlegroundsmod.universe.UniverseSpawns.register();
        com.starkinhon.battlegroundsmod.universe.UniverseBiomes.register();
        com.starkinhon.battlegroundsmod.universe.RoamingSouls.register();
        com.starkinhon.battlegroundsmod.world.MapBorder.register();
        com.starkinhon.battlegroundsmod.world.MapPlaces.register();
        com.starkinhon.battlegroundsmod.world.TimeChamber.register();
        com.starkinhon.battlegroundsmod.universe.JujutsuWorld.register();
        com.starkinhon.battlegroundsmod.universe.ReverseCurse.register();
        com.starkinhon.battlegroundsmod.universe.BleachWorld.register();
        com.starkinhon.battlegroundsmod.universe.DemonSlayerWorld.register();
        FightRod.register();
        SoulCommand.addSubcommand(BossCommand::bossbar);
        SoulCommand.addSubcommand(com.starkinhon.battlegroundsmod.combat.DamageDebug::command);
        SoulCommand.addSubcommand(BossCommand::boss);
        SoulCommand.addSubcommand(com.starkinhon.battlegroundsmod.power.Progression::command);
        SoulCommand.addSubcommand(com.starkinhon.battlegroundsmod.challenge.ChallengeArenas::command);
        SoulCommand.addSubcommand(com.starkinhon.battlegroundsmod.challenge.Universes::command);
        SoulCommand.addSubcommand(com.starkinhon.battlegroundsmod.universe.DragonBallWorld::command);
        SoulCommand.addSubcommand(com.starkinhon.battlegroundsmod.command.StaminaCommand::build);
        SoulCommand.addSubcommand(com.starkinhon.battlegroundsmod.power.powers.kokushibo.MoonImpactRule::command);
        SoulCommand.addSubcommand(com.starkinhon.battlegroundsmod.domain.MalevolentShrine::command);
        NeoForge.EVENT_BUS.addListener(SoulCommand::register);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.boss.SoulCeremony::onPickup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Battlegrounds Mod carregado!");
        event.enqueueWork(com.starkinhon.battlegroundsmod.world.DragonRegion::register);
    }
}
