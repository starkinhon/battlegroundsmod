// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod;

import java.util.List;

import com.starkinhon.battlegroundsmod.client.BossBars;
import com.starkinhon.battlegroundsmod.client.BossCutscene;
import com.starkinhon.battlegroundsmod.client.InoClient;
import com.starkinhon.battlegroundsmod.client.EsferaCutscene;
import com.starkinhon.battlegroundsmod.client.FaceCutscene;
import com.starkinhon.battlegroundsmod.client.Fear;
import com.starkinhon.battlegroundsmod.client.BopCutscene;
import com.starkinhon.battlegroundsmod.client.GrabCutscene;
import com.starkinhon.battlegroundsmod.client.HakaiCutscene;
import com.starkinhon.battlegroundsmod.client.fx.AshDeath;
import com.starkinhon.battlegroundsmod.client.fx.Aura;
import com.starkinhon.battlegroundsmod.client.fx.AuraEyesLayer;
import com.starkinhon.battlegroundsmod.client.fx.FxConfig;
import com.starkinhon.battlegroundsmod.client.fx.FxShaders;
import com.starkinhon.battlegroundsmod.client.fx.FxWorld;
import com.starkinhon.battlegroundsmod.client.fx.MeteorRenderer;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.client.fx.SkillEffects;
import com.starkinhon.battlegroundsmod.client.fx.SmokeParticle;
import com.starkinhon.battlegroundsmod.client.fx.SoulParticle;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.client.Keybinds;
import com.starkinhon.battlegroundsmod.client.MovementInput;
import com.starkinhon.battlegroundsmod.client.PowerHud;
import com.starkinhon.battlegroundsmod.client.PunchInput;
import com.starkinhon.battlegroundsmod.client.BaseAttacks;
import com.starkinhon.battlegroundsmod.client.CombatInput;
import com.starkinhon.battlegroundsmod.client.StatusBars;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.client.ShoulderCutscene;
import com.starkinhon.battlegroundsmod.client.Vanish;
import com.starkinhon.battlegroundsmod.client.AizenIllusionRenderer;
import com.starkinhon.battlegroundsmod.client.DummyRenderer;
import com.starkinhon.battlegroundsmod.client.SoulBossRenderer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import com.starkinhon.battlegroundsmod.client.outfit.AizenSwordRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import com.starkinhon.battlegroundsmod.client.cutscene.CutscenePlayer;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BattlegroundsMod.MODID, dist = Dist.CLIENT)
public class BattlegroundsModClient {

    public BattlegroundsModClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(BattlegroundsModClient::onClientSetup);
        modEventBus.addListener(BattlegroundsModClient::onRegisterKeys);
        modEventBus.addListener(BattlegroundsModClient::onRegisterGuiLayers);
        modEventBus.addListener(BattlegroundsModClient::onRegisterParticles);
        modEventBus.addListener(BattlegroundsModClient::onRegisterRenderers);
        modEventBus.addListener(BattlegroundsModClient::onAddLayers);
        modEventBus.addListener(BattlegroundsModClient::onRegisterLayerDefinitions);
        modEventBus.addListener(FxShaders::onRegisterShaders);
        modEventBus.addListener(BattlegroundsModClient::onRegisterClientExtensions);
        modEventBus.addListener(com.starkinhon.battlegroundsmod.client.ArenaClient::onRegisterEffects);
        modEventBus.addListener(com.starkinhon.battlegroundsmod.client.TorneioSky::onRegisterEffects);
        modContainer.registerConfig(ModConfig.Type.CLIENT, FxConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // First of every stage event: is it a shader pack's shadow pass (client/compat/IrisCompat)?
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, false, net.neoforged.neoforge.client.event.RenderLevelStageEvent.class,
                com.starkinhon.battlegroundsmod.client.compat.IrisCompat::onStage);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(MeteorRenderer::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.BloodFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.BloodFx::onClientTick);
        com.starkinhon.battlegroundsmod.client.ChallengeScreen.register();
        com.starkinhon.battlegroundsmod.client.DungeonChoiceScreen.register();
        // Respawning keeps the entity id with a new body (clock back to 0): drop the poses of the move it died in.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone e) -> {
            com.starkinhon.battlegroundsmod.client.SkillAnimations.forget(e.getOldPlayer().getId());
            com.starkinhon.battlegroundsmod.client.SkillAnimations.forget(e.getNewPlayer().getId());
        });
        // Someone flying who leaves sight: their flight is dropped here and sent again when they come back (FlightClient.forget).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) -> {
            if (e.getLevel().isClientSide()) {
                com.starkinhon.battlegroundsmod.client.FlightClient.forget(e.getEntity());
            }
        });
        // Experiência do jogador (key O): the button on the pause menu, and the auto tracking choice told to the server.
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.SoulsSettingsScreen::onScreenInit);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.SoulsSettingsScreen::onLogin);
        // Shoulder Surfing (optional): its camera starts coupled to the player, once per install.
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.ShoulderSurfingSetup::onLogin);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.NarratorSetup::onLogin);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            com.starkinhon.battlegroundsmod.client.SkillAnimations.clear();
            com.starkinhon.battlegroundsmod.client.TalkScreen.clear();
            com.starkinhon.battlegroundsmod.client.CutsceneCamera.reset();
            com.starkinhon.battlegroundsmod.client.fx.SkillEffects.clear();
            com.starkinhon.battlegroundsmod.client.FlightClient.clear();
            // a black screen, a flash or a goal of a scene left in the middle never follows into the next world (#379)
            com.starkinhon.battlegroundsmod.client.IntroHud.clear();
            com.starkinhon.battlegroundsmod.client.PowerSelectScreen.clearSwapHint();
            com.starkinhon.battlegroundsmod.client.ClientHooks.rewind();
            com.starkinhon.battlegroundsmod.client.outfit.Outfits.clear();
            com.starkinhon.battlegroundsmod.client.outfit.Sheaths.clear();
            com.starkinhon.battlegroundsmod.client.outfit.GourdLayer.clear();
            com.starkinhon.battlegroundsmod.client.outfit.SwordStretch.clear();
            com.starkinhon.battlegroundsmod.client.fx.MajinMarkLayer.clear();
            com.starkinhon.battlegroundsmod.client.fx.TeleRim.clear();
            com.starkinhon.battlegroundsmod.client.fx.InoRim.clear();
            com.starkinhon.battlegroundsmod.client.fx.ItadoriVeins.clear();
            com.starkinhon.battlegroundsmod.client.fx.NichirinBurn.clear();
            com.starkinhon.battlegroundsmod.client.FalaRender.clear();
            com.starkinhon.battlegroundsmod.client.fx.hs.HsWood.clear();
            com.starkinhon.battlegroundsmod.client.fx.ScreenFx.clear();
            com.starkinhon.battlegroundsmod.client.ScreenShake.clear();
            com.starkinhon.battlegroundsmod.client.BossBars.clear();
            com.starkinhon.battlegroundsmod.client.fx.ShenlongScene.clear();
            com.starkinhon.battlegroundsmod.power.SkillSwap.clearClient();
            com.starkinhon.battlegroundsmod.challenge.ArenaParty.clearClient();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e) ->
                com.starkinhon.battlegroundsmod.client.IntroHud.holdNight());
        // and before each tick too: the level's sky darkness is worked out in its tick, and a time packet from the server
        // (day) landing just before it lit the whole city up for a tick, every second (the colour flashes in Shibuya,
        // Stark 2026-11-19)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Pre e) ->
                com.starkinhon.battlegroundsmod.client.IntroHud.holdNight());
        // The intro's own fade covers its dimension changes: no "Carregando terreno" screen over it (Stark 2026-11-19).
        // The player does not move meanwhile anyway (LocalPlayer only ticks over a loaded chunk).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Opening e) -> {
            if (e.getNewScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                    && com.starkinhon.battlegroundsmod.client.IntroHud.covering()) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.DragonWind::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DragonWind::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DragonWind::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DragonWind::onFogColor);
        // The Floresta da Folha's falling leaves, golden light and warm haze.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.LeafForestFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.LeafForestFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.LeafForestFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.DeathForestFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DeathForestFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DeathForestFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.WindDesertFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.WindDesertFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.WindDesertFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.MistSeaFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.MistSeaFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.MistSeaFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.RainVillageFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.RainVillageFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.RainVillageFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.EndValleyFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.EndValleyFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.MyobokuFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.MyobokuFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.TemploFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.TemploFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, ambient(com.starkinhon.battlegroundsmod.client.fx.JujutsuAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.JujutsuAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.JujutsuAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.JujutsuAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.ColoniaAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ColoniaAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ColoniaAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ColoniaAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ColoniaAirFx::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.KyotoAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.KyotoAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.KyotoAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.KyotoAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.TemploAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.TemploAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.TemploAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.TemploAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.HanamiAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.HanamiAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.HanamiAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.HanamiAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.AldeiaAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.AldeiaAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.AldeiaAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.AldeiaAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.DagonAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DagonAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DagonAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DagonAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.JogoAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.JogoAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.JogoAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.JogoAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.JogoAirFx::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.FlorestaAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FlorestaAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FlorestaAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FlorestaAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.ShibuyaAirFx::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ShibuyaAirFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ShibuyaAirFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ShibuyaAirFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.CreativeSubTabs::onInit);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.CreativeSubTabs::onRenderPre);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.CreativeSubTabs::onRenderPost);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(FxWorld::onRenderLevel));
        // With a shader pack: the effects' copies of real blocks, drawn in the world pass so the pack lights them.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.WorldBlocks::onRenderLevel));
        // Create world screen: the "Experiência definitiva" bubble over the Souls world type (pops up ~2 s).
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.SoulsWorldTypeBadge::onRenderPost);
        // Saitama: the hole in the clouds (before the game draws them) and his afterimages (from his drawn frames).
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.OpmFx::onRenderLevel));
        // HIGHEST: the snapshot is taken before Customizable Player Models puts the body's parts back (its own Post), while
        // the parts it posed are still the model's.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RenderPlayerEvent.Post.class, com.starkinhon.battlegroundsmod.client.fx.OpmGhosts::onRenderPlayer);
        // Luffy: his body in each Gear (Boundman, small after the G3, upside down in the Jet Mergulho, the Fusen) and whoever he squashed.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderPlayerEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.LuffyFx::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderPlayerEvent.Post.class, com.starkinhon.battlegroundsmod.client.fx.LuffyFx::onRenderPlayerPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.LuffyFx::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, com.starkinhon.battlegroundsmod.client.fx.LuffyFx::onRenderLivingPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.HakiFx::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, com.starkinhon.battlegroundsmod.client.fx.HakiFx::onRenderLivingPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.shig.RegenFx::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, RenderLivingEvent.Post.class, com.starkinhon.battlegroundsmod.client.fx.shig.RegenFx::onRenderPost);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DomainPanorama::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene::onRenderLivingPost);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.StaminaBar::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.FalaRender::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(AshDeath::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.IceShell::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.ClivarRenderer::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FzFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FzFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.TimeChamberClient::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.TimeChamberClient::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.ArenaClient::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.TorneioSky::onFog);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, AshDeath::onRenderLiving);
        // The B's flight: both bodies keep facing the way it goes, the target's whole body tumbles and lies down (pushed last,
        // after anything that cancels the render, and always popped).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.fx.CravadaRenderer::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class,
                com.starkinhon.battlegroundsmod.client.fx.CravadaRenderer::onRenderPost);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.CravadaRenderer::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.CravadaRenderer::onMovementInput);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.ClivarRenderer::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.SoulCeremonyFx::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, Vanish::onRenderLiving);
        // Makima: a body sinking into (or rising from) the dark ground, and one crushed flat by the Ritual.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.fx.MakimaFx::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class,
                com.starkinhon.battlegroundsmod.client.fx.MakimaFx::onRenderLivingPost);
        // Inumaki's words on a body: shaking with the voice, crushed into paper, staggering, swelling, gone.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.fx.InumakiBody::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class,
                com.starkinhon.battlegroundsmod.client.fx.InumakiBody::onRenderPost);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.SwordFx::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, RenderLivingEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.fx.EsferaRenderer::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class,
                com.starkinhon.battlegroundsmod.client.fx.EsferaRenderer::onRenderPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, RenderLivingEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.fx.VermelhoRenderer::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.LimpBody::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, com.starkinhon.battlegroundsmod.client.LimpBody::onRenderPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class,
                com.starkinhon.battlegroundsmod.client.fx.VermelhoRenderer::onRenderPost);
        NeoForge.EVENT_BUS.addListener(ScreenFx::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.FirstPersonAnimation::onRenderHand);
        NeoForge.EVENT_BUS.addListener(BattlegroundsModClient::onClientTickPre);
        // The Dragon Ball flight's body turn (lying down into the cruise, turning into the curves).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.FlightPose::onRenderPre);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post.class,
                com.starkinhon.battlegroundsmod.client.FlightPose::onRenderPost);
        // The Controle de Energia's run on walls and trees (laid flat with the feet on it).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.WallRun::onRenderPre);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post.class,
                com.starkinhon.battlegroundsmod.client.WallRun::onRenderPost);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, com.starkinhon.battlegroundsmod.client.WallRun::tick);
        // The combat flight: the camera stays turned to the locked target.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.AirFightClient::onFrame);
        NeoForge.EVENT_BUS.addListener(BattlegroundsModClient::onClientTick);
        // Shenlong's summoning (the world dims, the dragon rises, the wish menu).
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, world(com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onRenderLevel));
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onFogColor);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(BossBars::onBossBar);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.RealismoCommand::register);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.SoulsCommand::register);
        // /souls panorama: the six faces of a menu panorama, shot over the next ticks (PanoramaCapture)
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, com.starkinhon.battlegroundsmod.client.PanoramaCapture::tick);
        // The Ino's Shintenshin (driving a body) and Shinranshin (inverted controls): before the punch, so a click drives the body.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, InputEvent.InteractionKeyMappingTriggered.class, InoClient::onInteraction);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, InoClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent.class, InoClient::onTurn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles.class,
                InoClient::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, InoClient::tick);
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, PunchInput::onInteraction);
        // the combat flight hotbar shortcuts (1/2/3) and afterimages from rendered bodies
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, com.starkinhon.battlegroundsmod.client.AirFightClient::onKey);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, com.starkinhon.battlegroundsmod.client.AirFightClient::tickPre);
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseScrollingEvent.class, com.starkinhon.battlegroundsmod.client.fx.AzulRenderer::onScroll);
        // The mod's keys win over another mod's key on the same key (KeyPriority): first and last of every key event.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, InputEvent.Key.class, com.starkinhon.battlegroundsmod.client.KeyPriority::onKeyFirst);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, InputEvent.Key.class, com.starkinhon.battlegroundsmod.client.KeyPriority::onKeyLast);
        // The Duelo de Expansões minigame's keys.
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, com.starkinhon.battlegroundsmod.client.fx.DuelScene::onKey);
        // Before StatusBars: a cancelled layer skips the listeners after it, so its custom bars stay hidden too.
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, com.starkinhon.battlegroundsmod.client.CutsceneHud::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, StatusBars::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, com.starkinhon.battlegroundsmod.client.CrystalHotbar::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Post.class, StatusBars::onRenderLayerPost);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, FaceCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, BossCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, GrabCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, BopCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, HakaiCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, EsferaCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(EsferaCutscene::onComputeFov);
        NeoForge.EVENT_BUS.addListener(EsferaCutscene::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, CutscenePlayer::onMovementInput);
        NeoForge.EVENT_BUS.addListener(CutscenePlayer::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(CutscenePlayer::onComputeFov);
        // Last, so a body hidden by another listener never gets its pose stack pushed without the pop.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, CutscenePlayer::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, CutscenePlayer::onRenderPost);
    }

    // Aizen's sword item is drawn by its own renderer (the 3D model from the Armario Souls viewer).
    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        // the Esferas do Dragão: the 3D ball of the Shenlong cutscene, one renderer for all seven
        com.starkinhon.battlegroundsmod.client.DragonBallRenderer[] dragonBall = new com.starkinhon.battlegroundsmod.client.DragonBallRenderer[1];
        IClientItemExtensions dragonBallExt = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (dragonBall[0] == null) {
                    dragonBall[0] = new com.starkinhon.battlegroundsmod.client.DragonBallRenderer();
                }
                return dragonBall[0];
            }
        };
        for (var ball : ModItems.DRAGON_BALLS) {
            event.registerItem(dragonBallExt, ball.get());
        }
        AizenSwordRenderer[] renderer = new AizenSwordRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer[0] == null) {
                    renderer[0] = new AizenSwordRenderer();
                }
                return renderer[0];
            }
        }, ModItems.AIZEN_SWORD.get());
        com.starkinhon.battlegroundsmod.client.outfit.KokushiboSwordRenderer[] kokushibo = new com.starkinhon.battlegroundsmod.client.outfit.KokushiboSwordRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (kokushibo[0] == null) {
                    kokushibo[0] = new com.starkinhon.battlegroundsmod.client.outfit.KokushiboSwordRenderer();
                }
                return kokushibo[0];
            }
        }, ModItems.KOKUSHIBO_SWORD.get());
        com.starkinhon.battlegroundsmod.client.outfit.NanamiBladeRenderer[] nanamiBlade = new com.starkinhon.battlegroundsmod.client.outfit.NanamiBladeRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (nanamiBlade[0] == null) {
                    nanamiBlade[0] = new com.starkinhon.battlegroundsmod.client.outfit.NanamiBladeRenderer();
                }
                return nanamiBlade[0];
            }
        }, ModItems.NANAMI_BLADE.get());
        com.starkinhon.battlegroundsmod.client.outfit.SodeNoShirayukiRenderer[] sode = new com.starkinhon.battlegroundsmod.client.outfit.SodeNoShirayukiRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (sode[0] == null) {
                    sode[0] = new com.starkinhon.battlegroundsmod.client.outfit.SodeNoShirayukiRenderer();
                }
                return sode[0];
            }
        }, ModItems.SODE_NO_SHIRAYUKI.get());
        com.starkinhon.battlegroundsmod.client.outfit.SenbonzakuraRenderer[] senbonzakura = new com.starkinhon.battlegroundsmod.client.outfit.SenbonzakuraRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (senbonzakura[0] == null) {
                    senbonzakura[0] = new com.starkinhon.battlegroundsmod.client.outfit.SenbonzakuraRenderer();
                }
                return senbonzakura[0];
            }
        }, ModItems.SENBONZAKURA.get());
        com.starkinhon.battlegroundsmod.client.outfit.MiwaSwordRenderer[] miwaSword = new com.starkinhon.battlegroundsmod.client.outfit.MiwaSwordRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (miwaSword[0] == null) {
                    miwaSword[0] = new com.starkinhon.battlegroundsmod.client.outfit.MiwaSwordRenderer();
                }
                return miwaSword[0];
            }
        }, ModItems.MIWA_SWORD.get());
        com.starkinhon.battlegroundsmod.client.outfit.NobaraHammerRenderer[] nobaraHammer = new com.starkinhon.battlegroundsmod.client.outfit.NobaraHammerRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (nobaraHammer[0] == null) {
                    nobaraHammer[0] = new com.starkinhon.battlegroundsmod.client.outfit.NobaraHammerRenderer();
                }
                return nobaraHammer[0];
            }
        }, ModItems.NOBARA_HAMMER.get());
        com.starkinhon.battlegroundsmod.client.outfit.InvertedSpearRenderer[] invertedSpear = new com.starkinhon.battlegroundsmod.client.outfit.InvertedSpearRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (invertedSpear[0] == null) {
                    invertedSpear[0] = new com.starkinhon.battlegroundsmod.client.outfit.InvertedSpearRenderer();
                }
                return invertedSpear[0];
            }
        }, ModItems.INVERTED_SPEAR.get());
        com.starkinhon.battlegroundsmod.client.outfit.PlayfulCloudRenderer[] playfulCloud = new com.starkinhon.battlegroundsmod.client.outfit.PlayfulCloudRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (playfulCloud[0] == null) {
                    playfulCloud[0] = new com.starkinhon.battlegroundsmod.client.outfit.PlayfulCloudRenderer();
                }
                return playfulCloud[0];
            }
        }, ModItems.PLAYFUL_CLOUD.get());
        com.starkinhon.battlegroundsmod.client.outfit.NichirinSwordRenderer[] nichirinSword = new com.starkinhon.battlegroundsmod.client.outfit.NichirinSwordRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (nichirinSword[0] == null) {
                    nichirinSword[0] = new com.starkinhon.battlegroundsmod.client.outfit.NichirinSwordRenderer();
                }
                return nichirinSword[0];
            }
        }, ModItems.NICHIRIN_SWORD.get());
        // Stark's Naruto weapons remake (2026-11-22): low poly meshes with his calibrated hand poses.
        com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[] kunai = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (kunai[0] == null) {
                    kunai[0] = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer("kunai",
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.KUNAI_3P,
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.KUNAI_1P,
                            null, 0.515f, 1.20f, 0f, 0f, -45f);
                }
                return kunai[0];
            }
        }, ModItems.KUNAI.get());
        com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[] hiraishin = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (hiraishin[0] == null) {
                    hiraishin[0] = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer("kunai_hiraishin",
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.HIRAISHIN_3P,
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.HIRAISHIN_1P,
                            null, 0.509f, 1.18f, 0f, 0f, -45f);
                }
                return hiraishin[0];
            }
        }, ModItems.HIRAISHIN_KUNAI.get());
        com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[] kusanagi = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (kusanagi[0] == null) {
                    kusanagi[0] = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer("kusanagi",
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.KUSANAGI_3P,
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.KUSANAGI_1P,
                            null, 0.795f, 0.82f, 0f, 0f, -45f);
                }
                return kusanagi[0];
            }
        }, ModItems.KUSANAGI.get());
        com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[] shuriken = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (shuriken[0] == null) {
                    shuriken[0] = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer("shuriken",
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.SHURIKEN_3P,
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.SHURIKEN_1P,
                            null, 0.500f, 1.12f, 78f, 15f, 0f);
                }
                return shuriken[0];
            }
        }, ModItems.SHURIKEN.get());
        com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[] kubikiribocho = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (kubikiribocho[0] == null) {
                    kubikiribocho[0] = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer("kubikiribocho",
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.KUBIKI_3P,
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.KUBIKI_1P,
                            null, 0.800f, 0.70f, 0f, 0f, -45f);
                }
                return kubikiribocho[0];
            }
        }, ModItems.KUBIKIRIBOCHO.get());
        com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[] gunbai = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer[1];
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (gunbai[0] == null) {
                    gunbai[0] = new com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer("gunbai",
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.GUNBAI_3P,
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.GUNBAI_1P,
                            com.starkinhon.battlegroundsmod.client.outfit.NarutoWeaponRenderer.GUNBAI_1P_DEF,
                            0.796f, 0.68f, 0f, 0f, -40f);
                }
                return gunbai[0];
            }
        }, ModItems.GUNBAI.get());
    }

    // Every effect's stage listener: nothing is drawn in a shader pack's shadow pass (see client/compat/IrisCompat).
    private static java.util.function.Consumer<net.neoforged.neoforge.client.event.RenderLevelStageEvent> world(
            java.util.function.Consumer<net.neoforged.neoforge.client.event.RenderLevelStageEvent> render) {
        return event -> {
            if (!com.starkinhon.battlegroundsmod.client.compat.IrisCompat.inShadowPass()) {
                render.accept(event);
            }
        };
    }

    // A biome's ambience drawn only with "Partículas do ambiente" on (Experiência do jogador): all its particles and sheets
    // are drawn after the translucent blocks; the other passes (the sky's suns and aurora) and its fog stay.
    private static java.util.function.Consumer<net.neoforged.neoforge.client.event.RenderLevelStageEvent> ambient(
            java.util.function.Consumer<net.neoforged.neoforge.client.event.RenderLevelStageEvent> render) {
        return event -> {
            if (com.starkinhon.battlegroundsmod.client.compat.IrisCompat.inShadowPass()) {
                return;
            }
            if (FxConfig.ambient() || event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                render.accept(event);
            }
        };
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        BattlegroundsMod.LOGGER.info("Battlegrounds Mod: client setup");
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        Keybinds.ALL.forEach(event::register);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "power_hud"), PowerHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "quest_hud"),
                com.starkinhon.battlegroundsmod.client.QuestHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "intro_hud"),
                com.starkinhon.battlegroundsmod.client.IntroHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "scouter"),
                com.starkinhon.battlegroundsmod.client.ScouterHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "radar_dragao"),
                com.starkinhon.battlegroundsmod.client.DragonRadarHud::render);
        // After every vanilla bar, so it knows how tall the hearts, armor and food stand.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "base_attacks"), BaseAttacks::render);
        // Makima's BANG over her finger.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "makima_bang"),
                com.starkinhon.battlegroundsmod.client.MakimaHud::render);
        // The attack stamina: beside the player in third person, on the left of the screen in first person.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "stamina_bar"),
                com.starkinhon.battlegroundsmod.client.StaminaBar::render);
        // Impact frames and flashes cover the world and the hand, but not the HUD.
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "screen_fx"), ScreenFx::renderGui);
        // Saitama's serious caption and the Modo Serio's time.
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "opm_caption"),
                com.starkinhon.battlegroundsmod.client.fx.OpmFx::renderGui);
        // Luffy's Gear 5 time and the hunger after it.
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "luffy_hud"),
                com.starkinhon.battlegroundsmod.client.fx.LuffyFx::renderGui);
        // Kakashi's Sennen Goroshi title (over the world, under the HUD).
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "kakashi_title"),
                com.starkinhon.battlegroundsmod.client.fx.KakashiTitle::render);
        // Kakashi's Sharingan in close-up in the corner (C Copy).
        // Over the HUD: it shows only a moment, and under it the mod's corner HUD hid half the eye.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "kakashi_eye"),
                com.starkinhon.battlegroundsmod.client.fx.KakashiEye::render);
        // Hashirama's flowers: the sleep closing in.
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "hashirama_sleep"),
                com.starkinhon.battlegroundsmod.client.fx.hs.HsFx::renderGui);
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "domain_void"),
                com.starkinhon.battlegroundsmod.client.fx.DomainScene::renderGui);
        // Inside Aizen's Black Box the dark closes in over the view (below the HUD).
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "black_box"),
                com.starkinhon.battlegroundsmod.client.fx.BlackBoxRenderer::renderGui);
        // The bosses' lines: over the world and every effect, under the impact frames and the HUD (registered last of the
        // "below all" layers, so it is the lowest of them).
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "boss_falas"),
                com.starkinhon.battlegroundsmod.client.FalaRender::renderGui);
        // Kokushibo's form names in kanji beside him, with the lines.
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "form_kanji"),
                com.starkinhon.battlegroundsmod.client.FormKanji::renderGui);
        // The warm orange grade of the Dragon Ball lands, over the HUD too (Stark: "mexer um pouco na GUI, deixar mais
        // alaranjado"); under the cutscenes.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dragon_grade"),
                com.starkinhon.battlegroundsmod.client.fx.DragonWind::renderGrade);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "leaf_forest_grade"),
                com.starkinhon.battlegroundsmod.client.fx.LeafForestFx::renderGrade);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "death_forest_grade"),
                com.starkinhon.battlegroundsmod.client.fx.DeathForestFx::renderGrade);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "wind_desert_grade"),
                com.starkinhon.battlegroundsmod.client.fx.WindDesertFx::renderGrade);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "mist_sea_grade"),
                com.starkinhon.battlegroundsmod.client.fx.MistSeaFx::renderGrade);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "rain_village_grade"),
                com.starkinhon.battlegroundsmod.client.fx.RainVillageFx::renderGrade);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "cutscene_bars"), GrabCutscene::renderGui);
        // The Duelo de Expansões: its title, the minigame and the result (over the HUD, which the duel hides).
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "expansion_duel"),
                com.starkinhon.battlegroundsmod.client.fx.DuelScene::renderGui);
        // The Clash de poderes: who is winning, the time and the energies (only on the screen of the two).
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "clash_bar"),
                com.starkinhon.battlegroundsmod.client.fx.ClashFx::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "bop_cutscene"), BopCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "shenlong"),
                com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "hakai_cutscene"), HakaiCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "esfera_cutscene"), EsferaCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "editor_cutscene"), CutscenePlayer::renderGui);
        // the intro's fades over everything (last)
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "intro_cover"),
                com.starkinhon.battlegroundsmod.client.IntroHud::renderCover);
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        for (var type : List.of(ModParticles.FLAME, ModParticles.SOUL_FLAME, ModParticles.DUST, ModParticles.SMOKE, ModParticles.LAVA,
                ModParticles.PUFF, ModParticles.STREAK, ModParticles.FIST, ModParticles.RING)) {
            event.registerSpriteSet(type.get(), SoulParticle.Provider::new);
        }
        event.registerSpecial(ModParticles.SMOKE_FX.get(), new SmokeParticle.Provider());
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        MovementInput.tickPre();
        CombatInput.tickPre();
        com.starkinhon.battlegroundsmod.client.fx.DuelScene.drainKeys();
    }

    // The Madara skin uses slim (3 pixel) arms.
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.TELAO.get(), com.starkinhon.battlegroundsmod.client.TelaoRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.POWER_LINE.get(), com.starkinhon.battlegroundsmod.client.PowerLineRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.TRAFFIC_LIGHT.get(), com.starkinhon.battlegroundsmod.client.TrafficLightRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.ROPE_ANCHOR_BE.get(), com.starkinhon.battlegroundsmod.client.RopeRenderer::new);
        event.registerEntityRenderer(ModEntities.NINJA_THROWN.get(), com.starkinhon.battlegroundsmod.client.NinjaThrownRenderer::new);
        event.registerEntityRenderer(ModEntities.TODO_STONE.get(), com.starkinhon.battlegroundsmod.client.TodoStoneRenderer::new);
        event.registerEntityRenderer(ModEntities.MADARA.get(), context -> new SoulBossRenderer<>(context, "madara", true));
        event.registerEntityRenderer(ModEntities.BILLS.get(), context -> new SoulBossRenderer<>(context, "bills", true));
        event.registerEntityRenderer(ModEntities.GOJO.get(), context -> new SoulBossRenderer<>(context, "gojo", true));
        event.registerEntityRenderer(ModEntities.KOKUSHIBO.get(), com.starkinhon.battlegroundsmod.client.KokushiboRenderer::new);
        event.registerEntityRenderer(ModEntities.SUKUNA.get(), context -> new SoulBossRenderer<>(context, "sukuna", true));
        event.registerEntityRenderer(ModEntities.JOY_BOY.get(), com.starkinhon.battlegroundsmod.client.JoyBoyRenderer::new);
        event.registerEntityRenderer(ModEntities.SAITAMA.get(), com.starkinhon.battlegroundsmod.client.SaitamaRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIGARAKI.get(), context -> new SoulBossRenderer<>(context, "shigaraki", false));
        event.registerEntityRenderer(ModEntities.HASHIRAMA.get(), com.starkinhon.battlegroundsmod.client.HashiramaRenderer::new);
        event.registerEntityRenderer(ModEntities.FREEZA_TOTAL.get(), com.starkinhon.battlegroundsmod.client.FreezaTotalRenderer::new);
        event.registerEntityRenderer(ModEntities.TOJI.get(), com.starkinhon.battlegroundsmod.client.TojiRenderer::new);
        event.registerEntityRenderer(ModEntities.MAKIMA.get(), context -> new SoulBossRenderer<>(context, "makima", true));
        event.registerEntityRenderer(ModEntities.AIZEN.get(), context -> new SoulBossRenderer<>(context, "aizen", false));
        event.registerEntityRenderer(ModEntities.DUMMY.get(), context -> new DummyRenderer<>(context, "steve"));
        event.registerEntityRenderer(ModEntities.DUMMY_ATTACKER.get(), context -> new DummyRenderer<>(context, "kai"));
        event.registerEntityRenderer(ModEntities.AIZEN_ILLUSION.get(), AizenIllusionRenderer::new);
        event.registerEntityRenderer(ModEntities.MALEVOLENT_SHRINE.get(), com.starkinhon.battlegroundsmod.client.fx.MalevolentShrineRenderer::new);
        // SubAlma mobs and the Fighters (three looks per tier). The arms follow each skin's own layout (slim = 3 px arms): a slim skin on
        // the wide model shows holes down the arms, a wide one on the slim model loses a column.
        event.registerEntityRenderer(ModEntities.BYAKUYA.get(), context -> new SoulBossRenderer<>(context, "byakuya", false));
        event.registerEntityRenderer(ModEntities.RUKIA.get(), context -> new SoulBossRenderer<>(context, "rukia", true));
        event.registerEntityRenderer(ModEntities.GOKU.get(), context -> new SoulBossRenderer<>(context, "goku", false));
        event.registerEntityRenderer(ModEntities.VEGETA.get(), context -> new SoulBossRenderer<>(context, "vegeta", false));
        event.registerEntityRenderer(ModEntities.FREEZA.get(), context -> new SoulBossRenderer<>(context, "freeza", false));
        event.registerEntityRenderer(ModEntities.PICCOLO.get(), context -> new SoulBossRenderer<>(context, "piccolo", false));
        event.registerEntityRenderer(ModEntities.KAME.get(), context -> new SoulBossRenderer<>(context, "kame", false));
        event.registerEntityRenderer(ModEntities.KURIRIN.get(), context -> new SoulBossRenderer<>(context, "kuririn", true));
        event.registerEntityRenderer(ModEntities.NARUTO.get(), context -> new SoulBossRenderer<>(context, "naruto", true));
        event.registerEntityRenderer(ModEntities.SASUKE.get(), context -> new SoulBossRenderer<>(context, "sasuke", true));
        event.registerEntityRenderer(ModEntities.KAKASHI.get(), context -> new SoulBossRenderer<>(context, "kakashi", true));
        event.registerEntityRenderer(ModEntities.GAARA.get(), context -> new SoulBossRenderer<>(context, "gaara", false));
        event.registerEntityRenderer(ModEntities.SHIKAMARU.get(), context -> new SoulBossRenderer<>(context, "shikamaru", true));
        event.registerEntityRenderer(ModEntities.INO.get(), context -> new SoulBossRenderer<>(context, "ino", true));
        event.registerEntityRenderer(ModEntities.ITADORI.get(), context -> new SoulBossRenderer<>(context, "itadori", true));
        event.registerEntityRenderer(ModEntities.CHOSO.get(), context -> new SoulBossRenderer<>(context, "choso", true));
        event.registerEntityRenderer(ModEntities.NANAMI.get(), context -> new SoulBossRenderer<>(context, "nanami", true));
        event.registerEntityRenderer(ModEntities.NOBARA.get(), context -> new SoulBossRenderer<>(context, "nobara", true));
        event.registerEntityRenderer(ModEntities.TODO.get(), context -> new SoulBossRenderer<>(context, "todo", false));
        event.registerEntityRenderer(ModEntities.INUMAKI.get(), context -> new SoulBossRenderer<>(context, "inumaki", true));
        event.registerEntityRenderer(ModEntities.MIWA.get(), context -> new SoulBossRenderer<>(context, "miwa", true));
        event.registerEntityRenderer(ModEntities.NARUTO_CLONE.get(), com.starkinhon.battlegroundsmod.client.NarutoCloneRenderer::new);
        event.registerEntityRenderer(ModEntities.CURSED_PLUSH.get(), com.starkinhon.battlegroundsmod.client.CursedPlushRenderer::new);
        // the Dragon Ball biomes' creatures, box models from the approved preview (client/blocky)
        event.registerEntityRenderer(ModEntities.PEDESTRE.get(), com.starkinhon.battlegroundsmod.client.pedestre.PedestreRenderer::new);
        event.registerEntityRenderer(ModEntities.NAMEKIANO.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context,
                java.util.stream.IntStream.range(0, com.starkinhon.battlegroundsmod.animal.db.Namekiano.VARIANTS).mapToObj(i -> "namekiano_" + i).toList(),
                com.starkinhon.battlegroundsmod.client.blocky.DbAnims::namek, 0.6f));
        event.registerEntityRenderer(ModEntities.ANDROIDE_8.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context, "androide_8", com.starkinhon.battlegroundsmod.client.blocky.DbAnims::androide, 1.1f));
        event.registerEntityRenderer(ModEntities.LADRAO_URSO.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context, "ladrao_urso", com.starkinhon.battlegroundsmod.client.blocky.DbAnims::urso, 1.0f));
        event.registerEntityRenderer(ModEntities.BORA.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context, "bora", com.starkinhon.battlegroundsmod.client.blocky.DbAnims::bora, 0.7f));
        // the Dragon Ball creatures (animal/dbmob): the preview's models and baked animation, the same renderer
        for (var e : ModEntities.DB_MOBS.entrySet()) {
            String id = e.getKey();
            event.registerEntityRenderer(e.getValue().get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.NarutoMobRenderer(context, id,
                    com.starkinhon.battlegroundsmod.animal.dbmob.DbMobSpec.get(id), "db", com.starkinhon.battlegroundsmod.animal.dbmob.DbTravel::of));
        }
        // the Naruto biomes' creatures (animal/naruto): the preview's models and baked animation
        for (var e : ModEntities.NARUTO_MOBS.entrySet()) {
            String id = e.getKey();
            event.registerEntityRenderer(e.getValue().get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.NarutoMobRenderer(context, id));
        }
        // the Jujutsu curses (animal/jjmob): the preview's models and baked animation, the Abate's plate, the Kogane's bubble
        for (var e : ModEntities.JJ_MOBS.entrySet()) {
            String id = e.getKey();
            event.registerEntityRenderer(e.getValue().get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.JjMobRenderer(context, id));
        }
        event.registerEntityRenderer(ModEntities.ANBU_RAIZ.get(), context -> new SoulBossRenderer<>(context, "anbu_raiz", true));
        event.registerEntityRenderer(ModEntities.NPC_JIRAIYA.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_jiraiya", true));
        event.registerEntityRenderer(ModEntities.NPC_TEUCHI.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_teuchi"));
        event.registerEntityRenderer(ModEntities.MALDICAO_MENOR.get(),
                context -> new SoulBossRenderer<>(context, false, "maldicao_menor_0", "maldicao_menor_1"));
        event.registerEntityRenderer(ModEntities.MALDICAO_GRAU2.get(), context -> new SoulBossRenderer<>(context, "maldicao_grau2", false));
        event.registerEntityRenderer(ModEntities.FEITICEIRO_RENEGADO.get(), context -> new SoulBossRenderer<>(context, "feiticeiro_renegado", false));
        event.registerEntityRenderer(ModEntities.NPC_SHOKO.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_shoko", true));
        event.registerEntityRenderer(ModEntities.NPC_YAGA.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_yaga"));
        event.registerEntityRenderer(ModEntities.NPC_KAKASHI.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_kakashi", true));   // same skin as the soul mob (slim arms), Stark 2026-11-22
        event.registerEntityRenderer(ModEntities.NPC_MAKI.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_maki", true));
        event.registerEntityRenderer(ModEntities.NPC_KAIOH.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_kaioh"));
        event.registerEntityRenderer(ModEntities.NPC_BUBBLES.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_bubbles", false, 0.68f));
        event.registerEntityRenderer(ModEntities.HOLLOW.get(), context -> new SoulBossRenderer<>(context, "hollow", false));
        event.registerEntityRenderer(ModEntities.ARRANCAR.get(), context -> new SoulBossRenderer<>(context, "arrancar", false));
        event.registerEntityRenderer(ModEntities.MENOS_GRANDE.get(), context -> new SoulBossRenderer<>(context, "menos_grande", false));
        event.registerEntityRenderer(ModEntities.ONI_COMUM.get(), context -> new SoulBossRenderer<>(context, "oni_comum", false));
        event.registerEntityRenderer(ModEntities.ONI_LUA_INFERIOR.get(), context -> new SoulBossRenderer<>(context, "oni_lua_inferior", false));
        event.registerEntityRenderer(ModEntities.ONI_ESPADACHIM.get(), context -> new SoulBossRenderer<>(context, "oni_espadachim", false));
        event.registerEntityRenderer(ModEntities.NPC_YORUICHI.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_yoruichi"));
        event.registerEntityRenderer(ModEntities.NPC_URAHARA.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_urahara", true));
        event.registerEntityRenderer(ModEntities.NPC_UROKODAKI.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_urokodaki"));
        event.registerEntityRenderer(ModEntities.NPC_TAMAYO.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_tamayo", true));
        event.registerEntityRenderer(ModEntities.NPC_GOHAN.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_gohan"));
        event.registerEntityRenderer(ModEntities.RADITZ.get(), context -> new SoulBossRenderer<>(context, "raditz", false));
        event.registerEntityRenderer(ModEntities.PUI_PUI.get(), context -> new SoulBossRenderer<>(context, "pui_pui", false));
        event.registerEntityRenderer(ModEntities.DABURA.get(), context -> new SoulBossRenderer<>(context, "dabura", false));
        event.registerEntityRenderer(ModEntities.NPC_BULMA.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_bulma", true));
        event.registerEntityRenderer(ModEntities.NPC_MESTRE_KAME.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_mestre_kame"));
        event.registerEntityRenderer(ModEntities.NPC_BOO.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_boo"));
        event.registerEntityRenderer(ModEntities.NPC_KORIN.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_korin"));
    }

    private static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
    }

    // Players' eyes can trail light too (Mangekyo, see Aura), so their renderers learn where the eyes are.
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new AuraEyesLayer<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.outfit.OutfitLayer(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.fx.LuffyFx.Layer<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.fx.OpmEyesLayer<>(renderer));
                com.starkinhon.battlegroundsmod.client.fx.LuffyFx.wrapHeldLayer(renderer);
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.outfit.KokushiboSheathLayer<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.fx.MajinMarkLayer<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.fx.NichirinBurn.Layer<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.outfit.GourdLayer<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.fx.TeleRim<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.fx.InoRim<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.fx.ItadoriVeins<>(renderer));
            }
        }
        // Freeza's telekinesis outline goes on every living body (mobs and bosses too), not only players.
        for (net.minecraft.world.entity.EntityType<?> type : event.getEntityTypes()) {
            addTeleRim(event.getRenderer(type));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTeleRim(Object renderer) {
        if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living) {
            living.addLayer(new com.starkinhon.battlegroundsmod.client.fx.TeleRim(living));
            living.addLayer(new com.starkinhon.battlegroundsmod.client.fx.InoRim(living));
            living.addLayer(new com.starkinhon.battlegroundsmod.client.fx.ItadoriVeins(living));
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        MovementInput.tickPost();
        Keybinds.tick();
        // Single player paused (Esc): the server stops, so every effect, cutscene and screen effect stops on the frame it
        // is, instead of running on alone and being over (and gone) by the time the move resumes. Their clocks
        // (ScreenFx.now and the like) only move here, and the partial tick stays put while paused, so they hold still.
        if (net.minecraft.client.Minecraft.getInstance().isPaused()) {
            return;
        }
        com.starkinhon.battlegroundsmod.client.LimpBody.tick();
        FaceCutscene.tick();
        com.starkinhon.battlegroundsmod.client.fx.ShenlongScene.tick();
        BossCutscene.tick();
        ShoulderCutscene.tick();
        GrabCutscene.tick();
        BopCutscene.tick();
        HakaiCutscene.tick();
        EsferaCutscene.tick();
        CutscenePlayer.tick();
        Vanish.tick();
        ScreenShake.tick();
        ScreenFx.tick();
        FxWorld.tick();
        AshDeath.tick();
        com.starkinhon.battlegroundsmod.client.fx.shig.ShigFx.tick();
        com.starkinhon.battlegroundsmod.client.fx.SoulCeremonyFx.tick();
        Aura.tick();
        SkillEffects.tick();
        com.starkinhon.battlegroundsmod.client.fx.Realismo.tick();
        com.starkinhon.battlegroundsmod.client.fx.DomainScene.tick();
        com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene.tick();
        com.starkinhon.battlegroundsmod.client.fx.ItadoriDomainScene.tick();
        com.starkinhon.battlegroundsmod.client.fx.DuelScene.tick();
        com.starkinhon.battlegroundsmod.client.fx.ClashFx.tick();
        BossBars.tick();
        Fear.tick();
        com.starkinhon.battlegroundsmod.client.fx.SkillAudio.tick();
        com.starkinhon.battlegroundsmod.client.fx.FlightFx.tick();
    }
}
