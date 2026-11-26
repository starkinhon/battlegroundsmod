// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.compat;

import java.lang.reflect.Method;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Shader packs (Iris on NeoForge, Oculus on Forge), optional, read through Iris's public API by name so no Iris class is
// ever needed to build or run the mod.
//
// What a pack changes for us (checked in Iris 1.8 for 1.21.1):
// - While the world is drawn, Iris draws into its own buffers and SKIPS every shader it doesn't know (ours), so anything
//   with our own shaders drawn mid-level (after the entities, after the translucent blocks...) is invisible: those keep
//   a vanilla-shader fallback (FxShaders.worldFlat).
// - Iris finishes its picture (composite and final passes) at the end of LevelRenderer.renderLevel, BEFORE NeoForge's
//   AFTER_LEVEL stage. So at AFTER_LEVEL the main framebuffer already holds the pack's finished picture, its depth is the
//   scene's depth (Iris uses the main depth texture as its depthtex0) and our shaders run as without a pack: the effects
//   drawn there keep their full look (FxShaders.flat).
// - Iris draws the world a second time from the sun for its shadows, and that pass fires the block stages
//   (AFTER_SOLID/CUTOUT/TRANSLUCENT_BLOCKS) with the sun's matrices: whatever we draw there lands in the shadow map
//   (phantom shadows of mist sheets and leaves) and costs a second draw. Nothing of ours is drawn in it (inShadowPass).
public final class IrisCompat {
    private static final boolean LOADED = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");

    private static boolean broken;
    private static Object api;
    private static Method packInUse;
    private static Method shadowPass;
    // Read once per stage event (first listener), so the effects' own listeners only look at a field.
    private static boolean shadowNow;

    private IrisCompat() {
    }

    private static boolean lookUp() {
        if (!LOADED || broken) {
            return false;
        }
        if (api != null) {
            return true;
        }
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = type.getMethod("getInstance").invoke(null);
            packInUse = type.getMethod("isShaderPackInUse");
            shadowPass = type.getMethod("isRenderingShadowPass");
            api = instance;
            BattlegroundsMod.LOGGER.info("Souls: shader pack compat on (Iris API found)");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            BattlegroundsMod.LOGGER.warn("Souls: Iris/Oculus is installed but its API could not be read; effects use the plain look", e);
            return false;
        }
    }

    // A shader pack is on right now.
    public static boolean packOn() {
        if (!lookUp()) {
            return false;
        }
        try {
            return (Boolean) packInUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return false;
        }
    }

    private static boolean readShadowPass() {
        if (!lookUp()) {
            return false;
        }
        try {
            return (Boolean) shadowPass.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return false;
        }
    }

    // First listener of every stage event (BattlegroundsModClient, priority HIGHEST).
    public static void onStage(RenderLevelStageEvent event) {
        shadowNow = LOADED && readShadowPass();
    }

    // This stage event is the pack's shadow pass: draw nothing.
    public static boolean inShadowPass() {
        return shadowNow;
    }

    // Asked right now, outside the stage events: an entity or a layer being drawn in the pack's shadow pass. There the
    // pose is the sun's view, not "world minus camera": anything that turns the pose into a world position (the sword's
    // trail, a spring that follows the chest, strings left behind in the air) must not record it.
    public static boolean shadowPassNow() {
        return LOADED && readShadowPass();
    }
}
