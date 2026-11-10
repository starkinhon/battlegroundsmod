package com.starkinhon.soulsjjk.client;

import java.util.List;

import com.starkinhon.soulsjjk.SoulsJjk;
import com.starkinhon.soulsjjk.client.cutscene.CutscenePlayer;
import com.starkinhon.soulsjjk.client.fx.AshDeath;
import com.starkinhon.soulsjjk.client.fx.Aura;
import com.starkinhon.soulsjjk.client.fx.AuraEyesLayer;
import com.starkinhon.soulsjjk.client.fx.FxConfig;
import com.starkinhon.soulsjjk.client.fx.FxShaders;
import com.starkinhon.soulsjjk.client.fx.FxWorld;
import com.starkinhon.soulsjjk.client.fx.ScreenFx;
import com.starkinhon.soulsjjk.client.fx.SkillEffects;
import com.starkinhon.soulsjjk.client.fx.SmokeParticle;
import com.starkinhon.soulsjjk.client.fx.SoulParticle;
import com.starkinhon.soulsjjk.compat.DeltaTracker;
import com.starkinhon.soulsjjk.compat.Net;
import com.starkinhon.soulsjjk.fx.ModParticles;
import com.starkinhon.soulsjjk.jjc.JjcBookScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

// Client side of the addon: the same listeners as the Souls mod's client class, for the parts that came over (Gojo,
// Sukuna, their Expansões and the duel). The skill keys are JujutsuCraft's (see jjc/), so the Souls mod's Z/X/C/V keys,
// its HUD and its base fighting keys are not registered.
public final class SoulsJjkClient {
    private SoulsJjkClient() {
    }

    public static void init(IEventBus modBus) {
        Net.clientPlayer = () -> Minecraft.getInstance().player;
        modBus.addListener(SoulsJjkClient::onRegisterGuiOverlays);
        modBus.addListener(SoulsJjkClient::onRegisterParticles);
        modBus.addListener(SoulsJjkClient::onAddLayers);
        modBus.addListener(FxShaders::onRegisterShaders);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FxConfig.SPEC);

        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(JjcBookScreen::onInit);
        bus.addListener(FxWorld::onRenderLevel);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.DomainPanorama::onRenderFrame);
        bus.addListener(EventPriority.HIGHEST, false, RenderLivingEvent.Pre.class, com.starkinhon.soulsjjk.client.fx.SukunaDomainScene::onRenderLivingPre);
        bus.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, com.starkinhon.soulsjjk.client.fx.SukunaDomainScene::onRenderLivingPost);
        bus.addListener(StaminaBar::onRenderLevel);
        bus.addListener(FalaRender::onRenderLevel);
        bus.addListener(AshDeath::onRenderLevel);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.ClivarRenderer::onRenderLevel);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.FugaRenderer::onRenderLevel);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.FugaRenderer::onFog);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.FugaRenderer::onFogColor);
        bus.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Pre.class, AshDeath::onRenderLiving);
        bus.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, com.starkinhon.soulsjjk.client.fx.CravadaRenderer::onRenderPre);
        bus.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, com.starkinhon.soulsjjk.client.fx.CravadaRenderer::onRenderPost);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.CravadaRenderer::onPlayerTick);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.CravadaRenderer::onMovementInput);
        bus.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Pre.class, com.starkinhon.soulsjjk.client.fx.ClivarRenderer::onRenderLiving);
        bus.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Pre.class, Vanish::onRenderLiving);
        bus.addListener(EventPriority.LOW, false, RenderLivingEvent.Pre.class, com.starkinhon.soulsjjk.client.fx.VermelhoRenderer::onRenderPre);
        bus.addListener(EventPriority.LOW, false, RenderLivingEvent.Pre.class, LimpBody::onRenderPre);
        bus.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, LimpBody::onRenderPost);
        bus.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, com.starkinhon.soulsjjk.client.fx.VermelhoRenderer::onRenderPost);
        bus.addListener(ScreenFx::onComputeFov);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.SukunaDomainScene::onComputeFov);
        bus.addListener(com.starkinhon.soulsjjk.client.fx.SukunaDomainScene::onCameraAngles);
        bus.addListener(EventPriority.NORMAL, false, MovementInputUpdateEvent.class, com.starkinhon.soulsjjk.client.fx.SukunaDomainScene::onMovementInput);
        bus.addListener(FirstPersonAnimation::onRenderHand);
        bus.addListener(SoulsJjkClient::onClientTick);
        bus.addListener(EventPriority.NORMAL, false, InputEvent.MouseScrollingEvent.class, com.starkinhon.soulsjjk.client.fx.AzulRenderer::onScroll);
        // The Duelo de Expansões minigame's keys.
        bus.addListener(EventPriority.NORMAL, false, InputEvent.Key.class, com.starkinhon.soulsjjk.client.fx.DuelScene::onKey);
        bus.addListener(EventPriority.NORMAL, false, net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre.class, CutsceneHud::onRenderLayer);
        bus.addListener(EventPriority.NORMAL, false, MovementInputUpdateEvent.class, FaceCutscene::onMovementInput);
        bus.addListener(EventPriority.NORMAL, false, MovementInputUpdateEvent.class, BossCutscene::onMovementInput);
        bus.addListener(EventPriority.NORMAL, false, MovementInputUpdateEvent.class, CutscenePlayer::onMovementInput);
        bus.addListener(CutscenePlayer::onCameraAngles);
        bus.addListener(CutscenePlayer::onComputeFov);
        bus.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, CutscenePlayer::onRenderPre);
        bus.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Post.class, CutscenePlayer::onRenderPost);
    }

    private interface GuiLayer {
        void render(GuiGraphics graphics, DeltaTracker delta);
    }

    private static IGuiOverlay layer(GuiLayer layer) {
        return (gui, graphics, partialTick, width, height) -> layer.render(graphics, DeltaTracker.of(partialTick));
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        // The attack stamina: beside the player in third person, on the left of the screen in first person.
        event.registerAboveAll("stamina_bar", layer(StaminaBar::render));
        // Impact frames and flashes cover the world and the hand, but not the HUD.
        event.registerBelowAll("screen_fx", layer(ScreenFx::renderGui));
        event.registerBelowAll("domain_void", layer(com.starkinhon.soulsjjk.client.fx.DomainScene::renderGui));
        event.registerBelowAll("boss_falas", layer(FalaRender::renderGui));
        // The Duelo de Expansões: its title, the minigame and the result (over the HUD, which the duel hides).
        event.registerAboveAll("expansion_duel", layer(com.starkinhon.soulsjjk.client.fx.DuelScene::renderGui));
        event.registerAboveAll("editor_cutscene", layer(CutscenePlayer::renderGui));
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        for (var type : List.of(ModParticles.FLAME, ModParticles.SOUL_FLAME, ModParticles.DUST, ModParticles.SMOKE, ModParticles.LAVA,
                ModParticles.PUFF, ModParticles.STREAK, ModParticles.FIST, ModParticles.RING)) {
            event.registerSpriteSet(type.get(), SoulParticle.Provider::new);
        }
        event.registerSpecial(ModParticles.SMOKE_FX.get(), new SmokeParticle.Provider());
    }

    // Players' eyes can trail light too (see Aura), so their renderers learn where the eyes are.
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new AuraEyesLayer<>(renderer));
            }
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            com.starkinhon.soulsjjk.client.fx.DuelScene.drainKeys();
            return;
        }
        // Single player paused (Esc): every effect, cutscene and screen effect stops on the frame it is.
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        LimpBody.tick();
        FaceCutscene.tick();
        BossCutscene.tick();
        ShoulderCutscene.tick();
        CutscenePlayer.tick();
        Vanish.tick();
        ScreenShake.tick();
        ScreenFx.tick();
        FxWorld.tick();
        AshDeath.tick();
        Aura.tick();
        SkillEffects.tick();
        com.starkinhon.soulsjjk.client.fx.Realismo.tick();
        com.starkinhon.soulsjjk.client.fx.DomainScene.tick();
        com.starkinhon.soulsjjk.client.fx.SukunaDomainScene.tick();
        com.starkinhon.soulsjjk.client.fx.DuelScene.tick();
        Fear.tick();
        com.starkinhon.soulsjjk.client.fx.SkillAudio.tick();
    }
}
