package com.starkinhon.soulsjjk;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.starkinhon.soulsjjk.compat.Net;
import com.starkinhon.soulsjjk.compat.RegisterPayloadHandlersEvent;
import com.starkinhon.soulsjjk.fx.ModParticles;
import com.starkinhon.soulsjjk.jjc.JjcBook;
import com.starkinhon.soulsjjk.jjc.JjcTechniques;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.PowerEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Souls x JujutsuCraft: the Gojo and Sukuna of the Souls mod as two new techniques of JujutsuCraft (Forge 1.20.1).
// The kits, both Expansões and the Duelo de Expansões are the Souls mod's own code (backported through compat/);
// jjc/ ties them to JujutsuCraft's technique book, selector, start key and cursed energy.
@Mod(SoulsJjk.MODID)
public class SoulsJjk {
    public static final String MODID = "soulsjjk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoulsJjk() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEffects.MOB_EFFECTS.register(modBus);
        ModParticles.PARTICLE_TYPES.register(modBus);

        Net.init();
        RegisterPayloadHandlersEvent payloads = new RegisterPayloadHandlersEvent();
        ModPayloads.register(payloads);
        JjcBook.register(payloads.registrar("1"));

        PowerEvents.register();
        // Before the Souls power's own tick (PowerEvents), so the key held and the power follow JujutsuCraft first.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, SoulsJjk::onPlayerTick);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.starkinhon.soulsjjk.client.SoulsJjkClient.init(modBus));
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            JjcTechniques.tick(player);
        }
    }
}
