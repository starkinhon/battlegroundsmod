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
    static ShaderInstance warp;
    static ShaderInstance bloomDown;
    static ShaderInstance bloomUp;
    static ShaderInstance bloomComp;
    static ShaderInstance vfx;
    static ShaderInstance ash;
    static ShaderInstance aura;
    static ShaderInstance auraOutline;
    static ShaderInstance susanoo;

    private static Boolean irisPresent;

    private FxShaders() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            registerAll(event);
        } catch (IOException e) {
            BattlegroundsMod.LOGGER.error("Could not load the effect shaders; effects fall back to the flat look", e);
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
        register(event, "souls_aura", DefaultVertexFormat.NEW_ENTITY, s -> aura = s);
        register(event, "fx_aura_outline", DefaultVertexFormat.POSITION, s -> auraOutline = s);
        register(event, "souls_susanoo", DefaultVertexFormat.POSITION_COLOR_NORMAL, s -> susanoo = s);
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
