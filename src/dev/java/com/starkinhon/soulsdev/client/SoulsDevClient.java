// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
        // Sem o tutorial "Mova-se com WASD" fixo no canto dos prints.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) ->
                net.minecraft.client.Minecraft.getInstance().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE));
    }
}
