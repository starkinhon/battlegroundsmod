package com.starkinhon.soulsdev;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

// Souls Dev: ferramentas SÓ DE TESTE do mod Souls (Stark, 2026-11-12). Mod à parte, no source set "dev": entra no
// runClient e no jar separado build/devlibs/soulsdev-*.jar, NUNCA no jar de lançamento (build/libs).
//
// Servidor: /dev (DevComandos) trava bosses e mobs (DevTravas), faz boss soltar skill, repete comandos.
// Cliente: /devc (client/DevCliente) roda roteiros, simula teclas, posiciona câmera, tira print e grava frames.
@Mod(SoulsDev.MODID)
public final class SoulsDev {
    public static final String MODID = "soulsdev";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoulsDev(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(DevTravas.class);
        NeoForge.EVENT_BUS.register(DevComandos.class);
        NeoForge.EVENT_BUS.register(DevFx.class);
        LOGGER.info("[soulsdev] ferramentas de teste carregadas (/dev e /devc). Não é para ir no lançamento.");
    }

    // Tudo que os comandos dizem também vai pro latest.log com [soulsdev], pros roteiros lerem depois.
    public static void log(String message) {
        LOGGER.info("[soulsdev] {}", message);
    }
}
