// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.starkinhon.battlegroundsmod.client.compat.IrisCompat;

// The mod's own shaders (assets/battlegroundsmod/shaders/core). Screen passes use fx_blit for the full-screen quad.
public final class FxShaders {
    static ShaderInstance radial;
    // Colônia do Jogo do Abate: the dome and sky (ColoniaAirFx), and the Jujutsu screen grade.
    static ShaderInstance colonia;
    static ShaderInstance jjGrade;
    // The Outono de Kyoto's pond: the mirrored garden and the water's surface over it (KyotoAirFx).
    static ShaderInstance kyotoMirror;
    static ShaderInstance kyotoPond;
    static ShaderInstance temploCloud;
    // Shibuya's screen grade (ShibuyaAirFx).
    static ShaderInstance shibuyaGrade;
    static ShaderInstance impact;
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
    static ShaderInstance inoRim;
    static ShaderInstance itVeins;
    static ShaderInstance voidFlash;
    // The procedural smoke particles (SmokeParticle); without it they just don't draw.
    static ShaderInstance smoke;
    // The Dragon Ball kits' 3D effects (KiMesh); on its own, so a problem here only hides them.
    static ShaderInstance ki;
    // Saitama's serious moves (OpmMesh).
    static ShaderInstance opm;
    // Luffy's moves (LuffyMesh).
    static ShaderInstance luffy;
    // Kakashi's Perfect Susanoo (KakashiSusanoo): the body, its flames and outline, and the mist round its feet.
    static ShaderInstance kkSus;
    static ShaderInstance kkMist;
    static ShaderInstance sand;
    public static ShaderInstance wood;
    // the same wood without discard (tools/shaders/wood_solid.py): early depth on for the pieces that can't splinter
    public static ShaderInstance woodSolid;
    static ShaderInstance bleach;
    /** Aizen's kit (meshes in AizenMesh). */
    static ShaderInstance aizen;
    // Bleach's own glow composite (halo only, like the pages).
    static ShaderInstance bleachComp;
    // Sukuna's Fuga (FugaRenderer); without them the Fuga only darkens the world.
    static ShaderInstance fugaFlame;
    static ShaderInstance fugaArrow;
    static ShaderInstance fugaVol;
    static ShaderInstance fugaExp;
    static ShaderInstance fugaRing;
    static ShaderInstance fugaGlow;
    static ShaderInstance fugaOrb;
    // Freeza's Supernova fire dome (NovaRenderer); without it the landing keeps its rings, flash and shake.
    static ShaderInstance novaVol;
    // the end-game bosses' fire, dust and smoke (client/fx/epic/EpicFx)
    public static ShaderInstance boomVol;
    // puts the half-size volume layer back onto the frame (premultiplied); without it the volumes draw at full size
    public static ShaderInstance volComp;
    // The vapour off an exorcised curse (CurseVapor); without it the body still darkens and evaporates.
    static ShaderInstance jjVapor;
    // Sukuna's thrown Dismantle (DismantleRenderer); without it the cuts still hit, they just don't show.
    static ShaderInstance dismantle;
    // Sukuna's technique cuts (SukunaCutsRenderer: the Z, the chant, the World Cutting Slash, the Shift+Z lines).
    static ShaderInstance sukunaFx;
    // The Clivar's grid over a body (ClivarRenderer).
    static ShaderInstance clivar;
    // The Black Flash (BlackFlashRenderer: bolts, core, cracks; forja/black-flash-v3).
    static ShaderInstance bf;
    // Sukuna's Expansão de Domínio (SukunaDomainScene); without them the domain still works, just without its look.
    static ShaderInstance sdSky;
    static ShaderInstance itaStation;
    // The two skies of the Duelo de Expansões, cyan and red, and the Santuário's cracks in the void (DuelScene).
    static ShaderInstance duelSky;
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
    // The Esferas do Dragão (DragonBallRenderer), the ball of the Shenlong cutscene.
    static ShaderInstance dball;
    // The Shenlong cutscene (ShenlongScene): the preview's shaders (forja/shenlong/glsl.js).
    static ShaderInstance shenBody;
    static ShaderInstance shenGlow;
    static ShaderInstance shenSky;
    static ShaderInstance shenGround;
    static ShaderInstance shenBolt;

    // The Esfera da Destruição Planetária's outer space (SpaceScene): the preview's sky, round Earth and sun.
    static ShaderInstance esferaSky;
    static ShaderInstance esferaPlanet;
    static ShaderInstance esferaSun;

    // Shibuya's Telão (client/TelaoRenderer).
    static ShaderInstance telao;

    private FxShaders() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            registerAll(event);
        } catch (IOException e) {
            BattlegroundsMod.LOGGER.error("Could not load the effect shaders; effects fall back to the flat look", e);
        }
        // On its own: without it the Telão just shows its dark block.
        try {
            register(event, "souls_telao", DefaultVertexFormat.POSITION, s -> telao = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Telão shader", e);
        }
        try {
            register(event, "souls_ki", DefaultVertexFormat.POSITION, s -> ki = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Dragon Ball 3D effect shader", e);
        }
        try {
            register(event, "souls_opm", DefaultVertexFormat.POSITION, s -> opm = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load Saitama's effect shader", e);
        }
        try {
            register(event, "souls_luffy", LuffyMesh.FORMAT, s -> luffy = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load Luffy's effect shader", e);
        }
        try {
            register(event, "souls_kk_sus", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, s -> kkSus = s);
            register(event, "souls_kk_mist", DefaultVertexFormat.POSITION, s -> kkMist = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load Kakashi's Susanoo shaders", e);
        }
        try {
            register(event, "souls_sand", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, s -> sand = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load Gaara's sand shader", e);
        }
        try {
            register(event, "souls_wood", DefaultVertexFormat.NEW_ENTITY, s -> wood = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load Hashirama's wood shader", e);
        }
        try {
            register(event, "souls_wood_solid", DefaultVertexFormat.NEW_ENTITY, s -> woodSolid = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load Hashirama's solid wood shader", e);
        }
        // Bleach (Rukia, Byakuya): the same GLSL text as the approved pages (forja/bleach/mod-shaders.sh).
        try {
            register(event, "souls_bleach", DefaultVertexFormat.POSITION, s -> bleach = s);
            register(event, "souls_bleach_comp", DefaultVertexFormat.POSITION, s -> bleachComp = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Bleach 3D effect shader", e);
        }
        try {
            register(event, "souls_aizen", AizenMesh.FORMAT, s -> aizen = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Aizen 3D effect shader", e);
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
        // On its own: a problem here only hides the vapour of the exorcised curses.
        try {
            register(event, "souls_jj_vapor", DefaultVertexFormat.POSITION, s -> jjVapor = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the curse vapour shader", e);
        }
        // On its own: a problem here only hides the Supernova's fire dome.
        try {
            register(event, "souls_nova_vol", DefaultVertexFormat.POSITION, s -> novaVol = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Supernova shader", e);
        }
        // On its own: a problem here only hides the end-game bosses' fire and dust volumes.
        try {
            register(event, "souls_boom_vol", DefaultVertexFormat.POSITION, s -> boomVol = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the end-game volume shader", e);
        }
        try {
            register(event, "souls_vol_comp", DefaultVertexFormat.POSITION, s -> volComp = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the volume compose shader", e);
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
        // On its own: a problem here only hides the Black Flash's look (the hit still lands).
        try {
            register(event, "souls_bf", DefaultVertexFormat.POSITION_TEX_COLOR, s -> bf = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Black Flash shader", e);
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
        // On its own: a problem here only leaves Itadori's station undrawn (the domain still works).
        try {
            register(event, "souls_ita_station", DefaultVertexFormat.POSITION_COLOR, s -> itaStation = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load Itadori's domain shader", e);
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
        // On its own: without it the Esferas do Dragão are drawn plain orange (no stars).
        try {
            register(event, "souls_dball", DefaultVertexFormat.NEW_ENTITY, s -> dball = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Dragon Ball shader", e);
        }
        // On their own: without them Shenlong's cutscene doesn't draw (the wish still happens, by the menu).
        try {
            register(event, "souls_shen_body", DefaultVertexFormat.NEW_ENTITY, s -> shenBody = s);
            register(event, "souls_shen_glow", DefaultVertexFormat.NEW_ENTITY, s -> shenGlow = s);
            register(event, "souls_shen_sky", DefaultVertexFormat.POSITION, s -> shenSky = s);
            register(event, "souls_shen_ground", DefaultVertexFormat.POSITION, s -> shenGround = s);
            register(event, "souls_shen_bolt", DefaultVertexFormat.POSITION_TEX, s -> shenBolt = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Shenlong shaders", e);
        }
        // On their own: without them the Esfera's space is the plain gradient, the old stars and the mod's sun.
        try {
            register(event, "souls_esfera_sky", DefaultVertexFormat.POSITION, s -> esferaSky = s);
            register(event, "souls_esfera_planet", DefaultVertexFormat.POSITION_TEX, s -> esferaPlanet = s);
            register(event, "souls_esfera_sun", DefaultVertexFormat.POSITION_TEX, s -> esferaSun = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Esfera space shaders", e);
        }
        // Shigaraki's (each on its own inside).
        com.starkinhon.battlegroundsmod.client.fx.shig.ShigShaders.register(event);
        // On its own: without it the duel still plays, just without its sky.
        try {
            register(event, "souls_duel_sky", DefaultVertexFormat.POSITION, s -> duelSky = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Domain Duel sky shader", e);
        }
        // On their own: without them the Colônia still has its smoke, ash and fog, just no dome, sky or screen grade.
        try {
            register(event, "souls_colonia", DefaultVertexFormat.POSITION, s -> colonia = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Colonia dome shader", e);
        }
        try {
            register(event, "fx_jjgrade", DefaultVertexFormat.POSITION, s -> jjGrade = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Jujutsu screen grade shader", e);
        }
        // On their own: without them the Outono de Kyoto's pond is vanilla water (its leaves, koi and the rest stay).
        try {
            register(event, "souls_kyoto_mirror", DefaultVertexFormat.POSITION_COLOR_NORMAL, s -> kyotoMirror = s);
            register(event, "souls_kyoto_pond", DefaultVertexFormat.POSITION, s -> kyotoPond = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Kyoto pond shaders", e);
        }
        // On its own: without it the Templo has no sea of clouds below the mountain (the rest stays).
        try {
            register(event, "souls_templo_cloud", DefaultVertexFormat.POSITION, s -> temploCloud = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Templo cloud shader", e);
        }
        // On its own: without it Shibuya keeps its neon, steam and fog, just no screen grade.
        try {
            register(event, "fx_shibuyagrade", DefaultVertexFormat.POSITION, s -> shibuyaGrade = s);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load the Shibuya screen grade shader", e);
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
        register(event, "souls_ino_rim", DefaultVertexFormat.NEW_ENTITY, s -> inoRim = s);
        register(event, "souls_it_veins", DefaultVertexFormat.NEW_ENTITY, s -> itVeins = s);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format, java.util.function.Consumer<ShaderInstance> sink)
            throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name), format), sink);
    }

    static boolean ready() {
        return radial != null && impact != null && warp != null && bloomDown != null && bloomUp != null && bloomComp != null && vfx != null;
    }

    // For what is drawn at AFTER_LEVEL (FxWorld and the screen passes). A shader pack (Iris) has already finished its
    // picture there, so our shaders, the glow layer and the screen passes work as without one (see compat/IrisCompat):
    // flat only when the shaders are missing, or with a pack when the player turned "Full effects with shader packs" off.
    public static boolean flat() {
        return !ready() || IrisCompat.packOn() && !FxConfig.on(FxConfig.SHADER_PACK_FULL);
    }

    // The stage where a body redrawn with our shaders goes (AshDeath, IceShell, ClivarRenderer): after the entities, or
    // with a shader pack after the level, where the pack has finished and our shaders run (mid-level it skips them).
    public static net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage bodyStage() {
        return IrisCompat.packOn() ? net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL
                : net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES;
    }

    // For what is drawn while the world itself is drawn (after the entities, after the translucent blocks, entity
    // layers): there a pack skips our shaders, so those draw their vanilla-shader version.
    public static boolean worldFlat() {
        return !ready() || IrisCompat.packOn();
    }
}
