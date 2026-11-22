// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import org.slf4j.Logger;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// The title screen background drawn from our own biomes (Stark, 2026-11-22): one panorama drawn by lot each time
// the game opens, out of assets/battlegroundsmod/textures/gui/title/souls_panorama/<nome>/panorama_0..5.png
// (the folders /souls panorama saves, copied as they are). A folder missing any of the six faces is skipped.
// None packed: the vanilla panorama stays.
public final class SoulsPanorama {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR = "textures/gui/title/souls_panorama";
    private static final String VANILLA = "textures/gui/title/background/panorama_0.png";

    private static boolean picked;
    private static CubeMap chosen;

    private SoulsPanorama() {
    }

    public static boolean isVanilla(ResourceLocation firstFace) {
        return "minecraft".equals(firstFace.getNamespace()) && VANILLA.equals(firstFace.getPath());
    }

    // the panorama of this run, picked once on the first menu frame (the resources are loaded by then)
    public static CubeMap chosen() {
        if (!picked) {
            picked = true;
            try {
                chosen = pick(Minecraft.getInstance().getResourceManager());
            } catch (Exception e) {
                LOGGER.warn("Panoramas do Souls: nao consegui listar", e);
                chosen = null;
            }
        }
        return chosen;
    }

    private static CubeMap pick(ResourceManager resources) {
        List<ResourceLocation> bases = new ArrayList<>();
        for (ResourceLocation face : resources.listResources(DIR, rl -> rl.getPath().endsWith("/panorama_0.png")).keySet()) {
            if (!BattlegroundsMod.MODID.equals(face.getNamespace())) {
                continue;
            }
            String path = face.getPath();
            ResourceLocation base = face.withPath(path.substring(0, path.length() - "_0.png".length()));
            boolean complete = true;
            for (int i = 1; i < 6 && complete; i++) {
                complete = resources.getResource(base.withSuffix("_" + i + ".png")).isPresent();
            }
            if (complete) {
                bases.add(base);
            } else {
                LOGGER.warn("Panorama do Souls incompleto (faltam faces): {}", base);
            }
        }
        if (bases.isEmpty()) {
            return null;
        }
        ResourceLocation base = bases.get(ThreadLocalRandom.current().nextInt(bases.size()));
        LOGGER.info("Panorama do menu: {} (de {})", base, bases.size());
        return new CubeMap(base);
    }
}
