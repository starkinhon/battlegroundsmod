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
    // The Dragon Ball kits' 3D effects (KiMesh); on its own, so a problem here only hides them.
    static ShaderInstance ki;
    // Sukuna's Fuga (FugaRenderer); without them the Fuga only darkens the world.
    static ShaderInstance fugaFlame;
    static ShaderInstance fugaArrow;
    static ShaderInstance fugaVol;
    static ShaderInstance fugaExp;
    static ShaderInstance fugaRing;
    static ShaderInstance fugaGlow;
    static ShaderInstance fugaOrb;
    // Sukuna's thrown Dismantle (DismantleRenderer); without it the cuts still hit, they just don't show.
    static ShaderInstance dismantle;
    // Sukuna's technique cuts (SukunaCutsRenderer: the Z, the chant, the World Cutting Slash, the Shift+Z lines).
    static ShaderInstance sukunaFx;
    // The Clivar's grid over a body (ClivarRenderer).
    static ShaderInstance clivar;
    // Sukuna's Expansão de Domínio (SukunaDomainScene); without them the domain still works, just without its look.
    static ShaderInstance sdSky;
    static ShaderInstance sdFloor;
    static ShaderInstance sdShrine;
    static ShaderInstance sdCol;
    static ShaderInstance sdMist;
    static ShaderInstance sdEmber;
    static ShaderInstance sdLine;
    static ShaderInstance sdScar;
    static ShaderInstance sdGrid;
    static ShaderInstance sdZig;
    static ShaderInstance sdBlood;
    static ShaderInstance sdAbyss;
    static ShaderInstance sdOv;
    static ShaderInstance sdUwBack;
    static ShaderInstance sdUwSurf;
    static ShaderInstance sdBubble;
    static ShaderInstance sdDrop;
    static ShaderInstance sdWave;
    static ShaderInstance sdPano;

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
        try {
            register(event, "souls_ki", DefaultVertexFormat.POSITION, s -> ki = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Dragon Ball 3D effect shader", e);
        }
        // On its own: a problem here only hides the smoke, never the other effects.
        try {
            register(event, "souls_smoke", DefaultVertexFormat.NEW_ENTITY, s -> smoke = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the smoke shader", e);
        }
        // On their own: a problem here only hides the Fuga's fire.
        try {
            register(event, "souls_fuga_flame", DefaultVertexFormat.POSITION_TEX, s -> fugaFlame = s);
            register(event, "souls_fuga_arrow", DefaultVertexFormat.POSITION_TEX, s -> fugaArrow = s);
            register(event, "souls_fuga_vol", DefaultVertexFormat.POSITION, s -> fugaVol = s);
            register(event, "souls_fuga_exp", DefaultVertexFormat.POSITION, s -> fugaExp = s);
            register(event, "souls_fuga_ring", DefaultVertexFormat.POSITION_TEX, s -> fugaRing = s);
            register(event, "souls_fuga_glow", DefaultVertexFormat.POSITION_TEX_COLOR, s -> fugaGlow = s);
            register(event, "souls_fuga_orb", DefaultVertexFormat.POSITION, s -> fugaOrb = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Fuga shaders", e);
        }
        // On its own: a problem here only hides Sukuna's thrown Dismantle.
        try {
            register(event, "souls_dismantle", DefaultVertexFormat.POSITION_TEX, s -> dismantle = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Dismantle shader", e);
        }
        // On its own: a problem here only hides Sukuna's technique cuts (they still hit).
        try {
            register(event, "souls_sukuna_fx", DefaultVertexFormat.POSITION_TEX, s -> sukunaFx = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Sukuna cuts shader", e);
        }
        // On its own: a problem here only hides the grid over the Clivar's target (the hit still lands).
        try {
            register(event, "souls_clivar", DefaultVertexFormat.NEW_ENTITY, s -> clivar = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Clivar grid shader", e);
        }
        // On its own, so a problem here never takes the other effects down (the Gojo's Expansão de 0,2 s just won't show).
        try {
            register(event, "fx_void_flash", DefaultVertexFormat.POSITION, s -> voidFlash = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the 0.2-second Domain Expansion shader", e);
        }
        // On its own too: a problem in the Sukuna's domain shaders never takes the rest down.
        try {
            register(event, "souls_sd_sky", DefaultVertexFormat.POSITION, s -> sdSky = s);
            register(event, "souls_sd_floor", DefaultVertexFormat.POSITION, s -> sdFloor = s);
            register(event, "souls_sd_shrine", DefaultVertexFormat.NEW_ENTITY, s -> sdShrine = s);
            register(event, "souls_sd_col", DefaultVertexFormat.POSITION_TEX, s -> sdCol = s);
            register(event, "souls_sd_mist", DefaultVertexFormat.POSITION, s -> sdMist = s);
            register(event, "souls_sd_ember", DefaultVertexFormat.POSITION_TEX_COLOR, s -> sdEmber = s);
            register(event, "souls_sd_line", DefaultVertexFormat.POSITION_TEX, s -> sdLine = s);
            register(event, "souls_sd_scar", DefaultVertexFormat.POSITION_TEX, s -> sdScar = s);
            register(event, "souls_sd_grid", DefaultVertexFormat.POSITION_COLOR_NORMAL, s -> sdGrid = s);
            register(event, "souls_sd_zig", DefaultVertexFormat.POSITION_TEX, s -> sdZig = s);
            register(event, "souls_sd_blood", DefaultVertexFormat.POSITION_TEX, s -> sdBlood = s);
            register(event, "souls_sd_abyss", DefaultVertexFormat.POSITION, s -> sdAbyss = s);
            register(event, "souls_sd_ov", DefaultVertexFormat.POSITION, s -> sdOv = s);
            register(event, "souls_sd_uwback", DefaultVertexFormat.POSITION, s -> sdUwBack = s);
            register(event, "souls_sd_uwsurf", DefaultVertexFormat.POSITION, s -> sdUwSurf = s);
            register(event, "souls_sd_bubble", DefaultVertexFormat.POSITION_TEX_COLOR, s -> sdBubble = s);
            register(event, "souls_sd_drop", DefaultVertexFormat.POSITION_TEX, s -> sdDrop = s);
            register(event, "souls_sd_wave", DefaultVertexFormat.POSITION_TEX, s -> sdWave = s);
            register(event, "souls_sd_pano", DefaultVertexFormat.POSITION, s -> sdPano = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Sukuna Domain Expansion shaders", e);
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
