package com.starkinhon.soulsdev.client;

import com.starkinhon.soulsdev.SoulsDev;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

// Parte do cliente do Souls Dev: /devc (DevCliente), roteiros (Roteiro), teclas simuladas (Teclas), câmera de teste
// (CameraDev + mixin), prints e gravação de frames (Gravador), abrir mundo sozinho (AutoMundo).
@Mod(value = SoulsDev.MODID, dist = Dist.CLIENT)
public final class SoulsDevClient {
    public SoulsDevClient(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(DevCliente.class);
        NeoForge.EVENT_BUS.register(Roteiro.class);
        NeoForge.EVENT_BUS.register(Teclas.class);
        NeoForge.EVENT_BUS.register(CameraDev.class);
        NeoForge.EVENT_BUS.register(Gravador.class);
        NeoForge.EVENT_BUS.register(AutoMundo.class);
        NeoForge.EVENT_BUS.register(Mira.class);
    }
}
