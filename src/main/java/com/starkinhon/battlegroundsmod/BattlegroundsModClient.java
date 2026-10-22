package com.starkinhon.battlegroundsmod;

import java.util.List;

import com.starkinhon.battlegroundsmod.client.BossBars;
import com.starkinhon.battlegroundsmod.client.BossCutscene;
import com.starkinhon.battlegroundsmod.client.FaceCutscene;
import com.starkinhon.battlegroundsmod.client.Fear;
import com.starkinhon.battlegroundsmod.client.fx.AshDeath;
import com.starkinhon.battlegroundsmod.client.fx.FxConfig;
import com.starkinhon.battlegroundsmod.client.fx.FxShaders;
import com.starkinhon.battlegroundsmod.client.fx.FxWorld;
import com.starkinhon.battlegroundsmod.client.fx.MeteorRenderer;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.client.fx.SkillEffects;
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
import com.starkinhon.battlegroundsmod.client.SoulBossRenderer;

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

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BattlegroundsMod.MODID, dist = Dist.CLIENT)
public class BattlegroundsModClient {

    public BattlegroundsModClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(BattlegroundsModClient::onClientSetup);
        modEventBus.addListener(BattlegroundsModClient::onRegisterKeys);
        modEventBus.addListener(BattlegroundsModClient::onRegisterGuiLayers);
        modEventBus.addListener(BattlegroundsModClient::onRegisterParticles);
        modEventBus.addListener(BattlegroundsModClient::onRegisterRenderers);
        modEventBus.addListener(FxShaders::onRegisterShaders);
        modContainer.registerConfig(ModConfig.Type.CLIENT, FxConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(MeteorRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(FxWorld::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(AshDeath::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, AshDeath::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(ScreenFx::onComputeFov);
        NeoForge.EVENT_BUS.addListener(BattlegroundsModClient::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(BattlegroundsModClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BossBars::onBossBar);
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, PunchInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, StatusBars::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, FaceCutscene::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, BossCutscene::onMovementInput);
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
        // Impact frames and flashes cover the world and the hand, but not the HUD.
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "screen_fx"), ScreenFx::renderGui);
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        for (var type : List.of(ModParticles.FLAME, ModParticles.SOUL_FLAME, ModParticles.DUST, ModParticles.SMOKE, ModParticles.LAVA,
                ModParticles.PUFF, ModParticles.STREAK, ModParticles.FIST, ModParticles.RING)) {
            event.registerSpriteSet(type.get(), SoulParticle.Provider::new);
        }
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        MovementInput.tickPre();
        CombatInput.tickPre();
    }

    // The Madara skin uses slim (3 pixel) arms.
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MADARA.get(), context -> new SoulBossRenderer<>(context, "madara", true));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        MovementInput.tickPost();
        Keybinds.tick();
        FaceCutscene.tick();
        BossCutscene.tick();
        ShoulderCutscene.tick();
        ScreenShake.tick();
        ScreenFx.tick();
        FxWorld.tick();
        AshDeath.tick();
        SkillEffects.tick();
        BossBars.tick();
        Fear.tick();
    }
}
