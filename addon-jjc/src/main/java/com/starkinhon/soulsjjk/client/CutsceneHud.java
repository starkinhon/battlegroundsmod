package com.starkinhon.soulsjjk.client;

import java.util.Set;

import com.starkinhon.soulsjjk.SoulsJjk;
import com.starkinhon.soulsjjk.client.cutscene.CutscenePlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;

// No HUD while a cutscene is playing: the vanilla bars, crosshair and hotbar, the mod's skill column, attack picker,
// stamina, and any other mod's layers. The cutscenes also turn on hideGui, but that flag is shared by every cutscene
// (and by F1): when two of them overlap, the one that ends first gives back the value it found and the HUD came back
// in the middle of the other (the Bills trip to space showed "Ataque de Investida", the armor and the crosshair).
// This checks the cutscenes themselves every frame, so it does not depend on that flag.
public final class CutsceneHud {
    // The mod's HUD layers: hidden like the vanilla ones. Every other layer of the mod (the cutscenes' own bars and
    // lines, impact frames, the Domain's void, the bosses' lines) keeps drawing.
    private static final Set<String> MOD_HUD = Set.of("power_hud", "base_attacks", "stamina_bar");

    private CutsceneHud() {
    }

    public static boolean hidden() {
        return com.starkinhon.soulsjjk.client.fx.SukunaDomainScene.hidesHud() || GrabCutscene.active() || BopCutscene.active() || HakaiCutscene.active() || EsferaCutscene.active()
                || BossCutscene.active() || FaceCutscene.hidesHud() || CutscenePlayer.hidesHud()
                || com.starkinhon.soulsjjk.client.fx.SoulCeremonyFx.hidesHud() || com.starkinhon.soulsjjk.client.fx.DuelScene.cutscene();
    }

    public static void onRenderLayer(RenderGuiOverlayEvent.Pre event) {
        if (Minecraft.getInstance().player == null || !hidden()) {
            return;
        }
        ResourceLocation name = event.getOverlay().id();
        if (!SoulsJjk.MODID.equals(name.getNamespace()) || MOD_HUD.contains(name.getPath())) {
            event.setCanceled(true);
        }
    }
}
