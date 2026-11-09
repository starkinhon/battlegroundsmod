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
        modEventBus.addListener(FxShaders::onRegisterShaders);
        modEventBus.addListener(BattlegroundsModClient::onRegisterClientExtensions);
        modContainer.registerConfig(ModConfig.Type.CLIENT, FxConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(MeteorRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(FxWorld::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.StaminaBar::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.FalaRender::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(AshDeath::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.IceShell::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onFog);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.fx.FugaRenderer::onFogColor);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, AshDeath::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, com.starkinhon.battlegroundsmod.client.fx.SoulCeremonyFx::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, Vanish::onRenderLiving);
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
        NeoForge.EVENT_BUS.addListener(BattlegroundsModClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BossBars::onBossBar);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.RealismoCommand::register);
        NeoForge.EVENT_BUS.addListener(com.starkinhon.battlegroundsmod.client.SoulsCommand::register);
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, PunchInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseScrollingEvent.class, com.starkinhon.battlegroundsmod.client.fx.AzulRenderer::onScroll);
        // Before StatusBars: a cancelled layer skips the listeners after it, so its custom bars stay hidden too.
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, com.starkinhon.battlegroundsmod.client.CutsceneHud::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, StatusBars::onRenderLayer);
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
        // After every vanilla bar, so it knows how tall the hearts, armor and food stand.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "base_attacks"), BaseAttacks::render);
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
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "cutscene_bars"), GrabCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "bop_cutscene"), BopCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "hakai_cutscene"), HakaiCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "esfera_cutscene"), EsferaCutscene::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "editor_cutscene"), CutscenePlayer::renderGui);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "sukuna_domain"), com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene::renderGui);
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
    }

    // The Madara skin uses slim (3 pixel) arms.
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MADARA.get(), context -> new SoulBossRenderer<>(context, "madara", true));
        event.registerEntityRenderer(ModEntities.BILLS.get(), context -> new SoulBossRenderer<>(context, "bills", true));
        event.registerEntityRenderer(ModEntities.GOJO.get(), context -> new SoulBossRenderer<>(context, "gojo", true));
        event.registerEntityRenderer(ModEntities.KOKUSHIBO.get(), com.starkinhon.battlegroundsmod.client.KokushiboRenderer::new);
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
        event.registerEntityRenderer(ModEntities.FIGHTER_HUMANO.get(),
                context -> new SoulBossRenderer<>(context, false, "fighter_humano_0", "fighter_humano_1", "fighter_humano_2"));
        event.registerEntityRenderer(ModEntities.FIGHTER_RUA.get(),
                context -> new SoulBossRenderer<>(context, false, "fighter_rua_0", "fighter_rua_1", "fighter_rua_2"));
        event.registerEntityRenderer(ModEntities.FIGHTER_QUARTEIRAO.get(),
                context -> new SoulBossRenderer<>(context, false, "fighter_quarteirao_0", "fighter_quarteirao_1", "fighter_quarteirao_2"));
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
        BossBars.tick();
        Fear.tick();
        com.starkinhon.battlegroundsmod.client.fx.SkillAudio.tick();
    }
}
