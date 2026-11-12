package com.starkinhon.battlegroundsmod;

import java.util.List;

import com.starkinhon.battlegroundsmod.client.BossBars;
import com.starkinhon.battlegroundsmod.client.BossCutscene;
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
        modContainer.registerConfig(ModConfig.Type.CLIENT, FxConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(MeteorRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DragonWind::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DragonWind::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DragonWind::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DragonWind::onFogColor);
        // The Floresta da Folha's falling leaves, golden light and warm haze.
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.LeafForestFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.LeafForestFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.LeafForestFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DeathForestFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DeathForestFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DeathForestFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.WindDesertFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.WindDesertFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.WindDesertFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.MistSeaFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.MistSeaFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.MistSeaFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.RainVillageFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.RainVillageFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.RainVillageFx::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.EndValleyFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.EndValleyFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.MyobokuFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.MyobokuFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.CreativeSubTabs::onInit);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.CreativeSubTabs::onRenderPre);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.CreativeSubTabs::onRenderPost);
        NeoForge.EVENT_BUS.addListener(FxWorld::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.TierInventoryInfo::onRenderPost);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.DomainPanorama::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene::onRenderLivingPost);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.StaminaBar::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.FalaRender::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(AshDeath::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.IceShell::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ClivarRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.TimeChamberClient::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.TimeChamberClient::onFogColor);
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
        NeoForge.EVENT_BUS.addListener(BattlegroundsModClient::onClientTick);
        // Shenlong's summoning (the world dims, the dragon rises, the wish menu).
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onComputeFov);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onFogColor);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre.class,
                com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(BossBars::onBossBar);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.RealismoCommand::register);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.SoulsCommand::register);
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, PunchInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseScrollingEvent.class, com.starkinhon.battlegroundsmod.client.fx.AzulRenderer::onScroll);
        // The Duelo de Expansões minigame's keys.
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, com.starkinhon.battlegroundsmod.client.fx.DuelScene::onKey);
        // Before StatusBars: a cancelled layer skips the listeners after it, so its custom bars stay hidden too.
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, com.starkinhon.battlegroundsmod.client.CutsceneHud::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, StatusBars::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Post.class, StatusBars::onRenderLayerPost);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, FaceCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, BossCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, GrabCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, BopCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, HakaiCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, EsferaCutscene::onMovementInput);
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
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "scouter"),
                com.starkinhon.battlegroundsmod.client.ScouterHud::render);
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
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "bop_cutscene"), BopCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "shenlong"),
                com.starkinhon.battlegroundsmod.client.fx.ShenlongScene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "hakai_cutscene"), HakaiCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "esfera_cutscene"), EsferaCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "editor_cutscene"), CutscenePlayer::renderGui);
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
        event.registerEntityRenderer(ModEntities.MADARA.get(), context -> new SoulBossRenderer<>(context, "madara", true));
        event.registerEntityRenderer(ModEntities.BILLS.get(), context -> new SoulBossRenderer<>(context, "bills", true));
        event.registerEntityRenderer(ModEntities.GOJO.get(), context -> new SoulBossRenderer<>(context, "gojo", true));
        event.registerEntityRenderer(ModEntities.KOKUSHIBO.get(), com.starkinhon.battlegroundsmod.client.KokushiboRenderer::new);
        event.registerEntityRenderer(ModEntities.SUKUNA.get(), context -> new SoulBossRenderer<>(context, "sukuna", false));
        event.registerEntityRenderer(ModEntities.DUMMY.get(), context -> new DummyRenderer<>(context, "steve"));
        event.registerEntityRenderer(ModEntities.DUMMY_ATTACKER.get(), context -> new DummyRenderer<>(context, "kai"));
        event.registerEntityRenderer(ModEntities.AIZEN_ILLUSION.get(), AizenIllusionRenderer::new);
        event.registerEntityRenderer(ModEntities.MALEVOLENT_SHRINE.get(), com.starkinhon.battlegroundsmod.client.fx.MalevolentShrineRenderer::new);
        // SubAlma mobs (improvised skins; the girls with slim arms) and the Fighters (three looks per tier).
        event.registerEntityRenderer(ModEntities.BYAKUYA.get(), context -> new SoulBossRenderer<>(context, "byakuya", false));
        event.registerEntityRenderer(ModEntities.RUKIA.get(), context -> new SoulBossRenderer<>(context, "rukia", true));
        event.registerEntityRenderer(ModEntities.GOKU.get(), context -> new SoulBossRenderer<>(context, "goku", false));
        event.registerEntityRenderer(ModEntities.VEGETA.get(), context -> new SoulBossRenderer<>(context, "vegeta", false));
        event.registerEntityRenderer(ModEntities.FREEZA.get(), context -> new SoulBossRenderer<>(context, "freeza", false));
        event.registerEntityRenderer(ModEntities.PICCOLO.get(), context -> new SoulBossRenderer<>(context, "piccolo", false));
        event.registerEntityRenderer(ModEntities.KAME.get(), context -> new SoulBossRenderer<>(context, "kame", false));
        event.registerEntityRenderer(ModEntities.KURIRIN.get(), context -> new SoulBossRenderer<>(context, "kuririn", false));
        event.registerEntityRenderer(ModEntities.NARUTO.get(), context -> new SoulBossRenderer<>(context, "naruto", false));
        event.registerEntityRenderer(ModEntities.SASUKE.get(), context -> new SoulBossRenderer<>(context, "sasuke", false));
        event.registerEntityRenderer(ModEntities.KAKASHI.get(), context -> new SoulBossRenderer<>(context, "kakashi", false));
        event.registerEntityRenderer(ModEntities.GAARA.get(), context -> new SoulBossRenderer<>(context, "gaara", false));
        event.registerEntityRenderer(ModEntities.SHIKAMARU.get(), context -> new SoulBossRenderer<>(context, "shikamaru", false));
        event.registerEntityRenderer(ModEntities.INO.get(), context -> new SoulBossRenderer<>(context, "ino", true));
        event.registerEntityRenderer(ModEntities.ITADORI.get(), context -> new SoulBossRenderer<>(context, "itadori", false));
        event.registerEntityRenderer(ModEntities.CHOSO.get(), context -> new SoulBossRenderer<>(context, "choso", false));
        event.registerEntityRenderer(ModEntities.NANAMI.get(), context -> new SoulBossRenderer<>(context, "nanami", false));
        event.registerEntityRenderer(ModEntities.NOBARA.get(), context -> new SoulBossRenderer<>(context, "nobara", true));
        event.registerEntityRenderer(ModEntities.TODO.get(), context -> new SoulBossRenderer<>(context, "todo", false));
        event.registerEntityRenderer(ModEntities.INUMAKI.get(), context -> new SoulBossRenderer<>(context, "inumaki", false));
        event.registerEntityRenderer(ModEntities.MIWA.get(), context -> new SoulBossRenderer<>(context, "miwa", true));
        event.registerEntityRenderer(ModEntities.NARUTO_CLONE.get(), com.starkinhon.battlegroundsmod.client.NarutoCloneRenderer::new);
        event.registerEntityRenderer(ModEntities.SOLDADO_RED_RIBBON.get(),
                context -> new SoulBossRenderer<>(context, false, "soldado_red_ribbon_0", "soldado_red_ribbon_1"));
        event.registerEntityRenderer(ModEntities.SAIBAMAN.get(), context -> new SoulBossRenderer<>(context, "saibaman", false));
        event.registerEntityRenderer(ModEntities.BANDIDO_DESERTO.get(),
                context -> new SoulBossRenderer<>(context, false, "bandido_deserto_0", "bandido_deserto_1"));
        event.registerEntityRenderer(ModEntities.PIRATA.get(), context -> new SoulBossRenderer<>(context, false, "pirata_0", "pirata_1"));
        // the yeti: a head and a half taller than a man
        event.registerEntityRenderer(ModEntities.YETI.get(), context -> new SoulBossRenderer<>(context, "yeti", false) {
            @Override
            protected void scale(com.starkinhon.battlegroundsmod.boss.SoulMob entity, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
                pose.scale(1.35f, 1.35f, 1.35f);
            }
        });
        // the Dragon Ball biomes' creatures, box models from the approved preview (client/blocky)
        event.registerEntityRenderer(ModEntities.NAMEKIANO.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context,
                java.util.stream.IntStream.range(0, com.starkinhon.battlegroundsmod.animal.db.Namekiano.VARIANTS).mapToObj(i -> "namekiano_" + i).toList(),
                com.starkinhon.battlegroundsmod.client.blocky.DbAnims::namek, 0.6f));
        event.registerEntityRenderer(ModEntities.PEIXE_GIGANTE.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context, "peixe_gigante", com.starkinhon.battlegroundsmod.client.blocky.DbAnims::peixe, 1.4f));
        event.registerEntityRenderer(ModEntities.ANDROIDE_8.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context, "androide_8", com.starkinhon.battlegroundsmod.client.blocky.DbAnims::androide, 1.1f));
        event.registerEntityRenderer(ModEntities.LADRAO_URSO.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context, "ladrao_urso", com.starkinhon.battlegroundsmod.client.blocky.DbAnims::urso, 1.0f));
        event.registerEntityRenderer(ModEntities.BORA.get(), context -> new com.starkinhon.battlegroundsmod.client.blocky.BlockyRenderer<>(context, "bora", com.starkinhon.battlegroundsmod.client.blocky.DbAnims::bora, 0.7f));
        event.registerEntityRenderer(ModEntities.TIRANOSSAURO.get(),
                context -> new com.starkinhon.battlegroundsmod.client.animal.DinoRenderer(context, "tiranossauro", 2.6f));
        event.registerEntityRenderer(ModEntities.PTERODACTILO.get(), com.starkinhon.battlegroundsmod.client.animal.PteroRenderer::new);
        event.registerEntityRenderer(ModEntities.RAPTOR.get(),
                context -> new com.starkinhon.battlegroundsmod.client.animal.DinoRenderer(context, "raptor", 1.0f));
        event.registerEntityRenderer(ModEntities.SOLDADO_FREEZA.get(),
                context -> new SoulBossRenderer<>(context, false, "soldado_freeza_0", "soldado_freeza_1"));
        event.registerEntityRenderer(ModEntities.NINJA_RENEGADO.get(),
                context -> new SoulBossRenderer<>(context, false, "ninja_renegado_0", "ninja_renegado_1"));
        event.registerEntityRenderer(ModEntities.NINJA_SOM.get(), context -> new SoulBossRenderer<>(context, "ninja_som", false));
        event.registerEntityRenderer(ModEntities.ANBU_RAIZ.get(), context -> new SoulBossRenderer<>(context, "anbu_raiz", false));
        event.registerEntityRenderer(ModEntities.NPC_JIRAIYA.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_jiraiya"));
        event.registerEntityRenderer(ModEntities.NPC_TEUCHI.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_teuchi"));
        event.registerEntityRenderer(ModEntities.MALDICAO_MENOR.get(),
                context -> new SoulBossRenderer<>(context, false, "maldicao_menor_0", "maldicao_menor_1"));
        event.registerEntityRenderer(ModEntities.MALDICAO_GRAU2.get(), context -> new SoulBossRenderer<>(context, "maldicao_grau2", false));
        event.registerEntityRenderer(ModEntities.FEITICEIRO_RENEGADO.get(), context -> new SoulBossRenderer<>(context, "feiticeiro_renegado", false));
        event.registerEntityRenderer(ModEntities.NPC_SHOKO.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_shoko"));
        event.registerEntityRenderer(ModEntities.NPC_YAGA.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_yaga"));
        event.registerEntityRenderer(ModEntities.HOLLOW.get(), context -> new SoulBossRenderer<>(context, "hollow", false));
        event.registerEntityRenderer(ModEntities.ARRANCAR.get(), context -> new SoulBossRenderer<>(context, "arrancar", false));
        event.registerEntityRenderer(ModEntities.MENOS_GRANDE.get(), context -> new SoulBossRenderer<>(context, "menos_grande", false));
        event.registerEntityRenderer(ModEntities.ONI_COMUM.get(), context -> new SoulBossRenderer<>(context, "oni_comum", false));
        event.registerEntityRenderer(ModEntities.ONI_LUA_INFERIOR.get(), context -> new SoulBossRenderer<>(context, "oni_lua_inferior", false));
        event.registerEntityRenderer(ModEntities.ONI_ESPADACHIM.get(), context -> new SoulBossRenderer<>(context, "oni_espadachim", false));
        event.registerEntityRenderer(ModEntities.NPC_YORUICHI.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_yoruichi"));
        event.registerEntityRenderer(ModEntities.NPC_URAHARA.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_urahara"));
        event.registerEntityRenderer(ModEntities.NPC_UROKODAKI.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_urokodaki"));
        event.registerEntityRenderer(ModEntities.NPC_TAMAYO.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_tamayo"));
        event.registerEntityRenderer(ModEntities.NPC_GOHAN.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_gohan"));
        event.registerEntityRenderer(ModEntities.RADITZ.get(), context -> new SoulBossRenderer<>(context, "raditz", false));
        // the Cell Jr.: child-sized
        event.registerEntityRenderer(ModEntities.CELL_JR.get(), context -> new SoulBossRenderer<>(context, "cell_jr", false) {
            @Override
            protected void scale(com.starkinhon.battlegroundsmod.boss.SoulMob entity, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
                pose.scale(0.72f, 0.72f, 0.72f);
            }
        });
        event.registerEntityRenderer(ModEntities.PUI_PUI.get(), context -> new SoulBossRenderer<>(context, "pui_pui", false));
        // Yakon: a head taller and broad
        event.registerEntityRenderer(ModEntities.YAKON.get(), context -> new SoulBossRenderer<>(context, "yakon", false) {
            @Override
            protected void scale(com.starkinhon.battlegroundsmod.boss.SoulMob entity, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
                pose.scale(1.3f, 1.28f, 1.3f);
            }
        });
        event.registerEntityRenderer(ModEntities.DABURA.get(), context -> new SoulBossRenderer<>(context, "dabura", false));
        event.registerEntityRenderer(ModEntities.NPC_BULMA.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_bulma"));
        event.registerEntityRenderer(ModEntities.NPC_MESTRE_KAME.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_mestre_kame"));
        event.registerEntityRenderer(ModEntities.NPC_BOO.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_boo"));
        event.registerEntityRenderer(ModEntities.NPC_KORIN.get(), context -> new com.starkinhon.battlegroundsmod.client.NpcRenderer(context, "npc_korin"));
        event.registerEntityRenderer(ModEntities.FIGHTER_HUMANO.get(),
                context -> new SoulBossRenderer<>(context, false, "fighter_humano_0", "fighter_humano_1", "fighter_humano_2"));
        event.registerEntityRenderer(ModEntities.FIGHTER_RUA.get(),
                context -> new SoulBossRenderer<>(context, false, "fighter_rua_0", "fighter_rua_1", "fighter_rua_2"));
        event.registerEntityRenderer(ModEntities.FIGHTER_QUARTEIRAO.get(),
                context -> new SoulBossRenderer<>(context, false, "fighter_quarteirao_0", "fighter_quarteirao_1", "fighter_quarteirao_2"));
    }

    private static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(com.starkinhon.battlegroundsmod.client.animal.DinoModel.LAYER,
                com.starkinhon.battlegroundsmod.client.animal.DinoModel::create);
        event.registerLayerDefinition(com.starkinhon.battlegroundsmod.client.animal.PteroModel.LAYER,
                com.starkinhon.battlegroundsmod.client.animal.PteroModel::create);
    }

    // Players' eyes can trail light too (Mangekyo, see Aura), so their renderers learn where the eyes are.
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new AuraEyesLayer<>(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.outfit.OutfitLayer(renderer));
                renderer.addLayer(new com.starkinhon.battlegroundsmod.client.outfit.KokushiboSheathLayer<>(renderer));
            }
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
        com.starkinhon.battlegroundsmod.client.fx.SoulCeremonyFx.tick();
        Aura.tick();
        SkillEffects.tick();
        com.starkinhon.battlegroundsmod.client.fx.Realismo.tick();
        com.starkinhon.battlegroundsmod.client.fx.DomainScene.tick();
        com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene.tick();
        com.starkinhon.battlegroundsmod.client.fx.DuelScene.tick();
        BossBars.tick();
        Fear.tick();
        com.starkinhon.battlegroundsmod.client.fx.SkillAudio.tick();
        com.starkinhon.battlegroundsmod.client.fx.FlightFx.tick();
    }
}
