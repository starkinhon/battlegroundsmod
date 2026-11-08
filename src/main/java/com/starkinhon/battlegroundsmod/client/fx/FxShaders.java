package com.starkinhon.battlegroundsmod.client.fx;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

// The mod's own shaders (assets/battlegroundsmod/shaders/core). Screen passes use fx_blit for the full-screen quad.
public final class FxShaders {
    static ShaderInstance radial;
    static ShaderInstance impact;
    static ShaderInstance moonScreen;
    static ShaderInstance warp;
    static ShaderInstance bloomDown;
    static ShaderInstance bloomUp;
    static ShaderInstance bloomComp;
    static ShaderInstance vfx;
    static ShaderInstance ash;
    static ShaderInstance ice;
    static ShaderInstance aura;
    static ShaderInstance auraOutline;
    static ShaderInstance susanoo;
    static ShaderInstance realismo;
    static ShaderInstance moon;
    static ShaderInstance moon3d;
    static ShaderInstance flesh;
    static ShaderInstance voidFlash;
    // The procedural smoke particles (SmokeParticle); without it they just don't draw.
    static ShaderInstance smoke;

    private static Boolean irisPresent;

    private FxShaders() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            registerAll(event);
        } catch (IOException e) {
            BattlegroundsMod.LOGGER.error("Could not load the effect shaders; effects fall back to the flat look", e);
        }
        // On its own too: without it the Awakened Moon just plays without its screen effects.
        try {
            register(event, "fx_moonscreen", DefaultVertexFormat.POSITION, s -> moonScreen = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Awakened Moon screen shader", e);
        }
        // On its own: a problem here only hides the smoke, never the other effects.
        try {
            register(event, "souls_smoke", DefaultVertexFormat.NEW_ENTITY, s -> smoke = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the smoke shader", e);
        }
        // On its own, so a problem here never takes the other effects down (the Gojo's Expansão de 0,2 s just won't show).
        try {
            register(event, "fx_void_flash", DefaultVertexFormat.POSITION, s -> voidFlash = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the 0.2-second Domain Expansion shader", e);
        }
    }

    private static void registerAll(RegisterShadersEvent event) throws IOException {
        register(event, "fx_radial", DefaultVertexFormat.POSITION, s -> radial = s);
        register(event, "fx_impact", DefaultVertexFormat.POSITION, s -> impact = s);
        register(event, "fx_warp", DefaultVertexFormat.POSITION, s -> warp = s);
        register(event, "fx_bloom_down", DefaultVertexFormat.POSITION, s -> bloomDown = s);
        register(event, "fx_bloom_up", DefaultVertexFormat.POSITION, s -> bloomUp = s);
        register(event, "fx_bloom_comp", DefaultVertexFormat.POSITION, s -> bloomComp = s);
        register(event, "souls_vfx", DefaultVertexFormat.PARTICLE, s -> vfx = s);
        register(event, "souls_ash", DefaultVertexFormat.NEW_ENTITY, s -> ash = s);
        register(event, "souls_ice", DefaultVertexFormat.NEW_ENTITY, s -> ice = s);
        register(event, "souls_aura", DefaultVertexFormat.NEW_ENTITY, s -> aura = s);
        register(event, "fx_aura_outline", DefaultVertexFormat.POSITION, s -> auraOutline = s);
        register(event, "souls_susanoo", DefaultVertexFormat.POSITION_COLOR_NORMAL, s -> susanoo = s);
        register(event, "fx_realismo", DefaultVertexFormat.POSITION, s -> realismo = s);
        register(event, "souls_moon", DefaultVertexFormat.PARTICLE, s -> moon = s);
        register(event, "souls_moon3d", DefaultVertexFormat.PARTICLE, s -> moon3d = s);
        register(event, "souls_flesh", DefaultVertexFormat.NEW_ENTITY, s -> flesh = s);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format, java.util.function.Consumer<ShaderInstance> sink)
            throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name), format), sink);
    }

    static boolean ready() {
        return radial != null && impact != null && warp != null && bloomDown != null && bloomUp != null && bloomComp != null && vfx != null;
    }

    // With an Iris shader pack on, the frame is not ours to touch mid-level: effects draw flat and the screen
    // passes run without depth, on the finished picture.
    public static boolean flat() {
        return !ready() || shaderPackOn();
    }

    private static boolean shaderPackOn() {
        if (irisPresent == Boolean.FALSE) {
            return false;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            irisPresent = true;
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            irisPresent = false;
            return false;
        }
    }
}
