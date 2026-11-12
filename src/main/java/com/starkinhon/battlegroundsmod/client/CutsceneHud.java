package com.starkinhon.battlegroundsmod.client;

import java.util.Set;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.cutscene.CutscenePlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

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
        return com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene.hidesHud() || GrabCutscene.active() || BopCutscene.active() || HakaiCutscene.active() || EsferaCutscene.active()
                || BossCutscene.active() || FaceCutscene.hidesHud() || CutscenePlayer.hidesHud()
                || com.starkinhon.battlegroundsmod.client.fx.SoulCeremonyFx.hidesHud() || com.starkinhon.battlegroundsmod.client.fx.DuelScene.cutscene()
                || com.starkinhon.battlegroundsmod.client.fx.ShenlongScene.cutscene();
    }

    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().player == null || !hidden()) {
            return;
        }
        ResourceLocation name = event.getName();
        if (!BattlegroundsMod.MODID.equals(name.getNamespace()) || MOD_HUD.contains(name.getPath())) {
            event.setCanceled(true);
        }
    }
}
