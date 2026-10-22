package com.starkinhon.battlegroundsmod;

import com.starkinhon.battlegroundsmod.client.Freecam;
import com.starkinhon.battlegroundsmod.client.Keybinds;
import com.starkinhon.battlegroundsmod.client.PowerHud;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BattlegroundsMod.MODID, dist = Dist.CLIENT)
public class BattlegroundsModClient {

    public BattlegroundsModClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(BattlegroundsModClient::onClientSetup);
        modEventBus.addListener(BattlegroundsModClient::onRegisterKeys);
        modEventBus.addListener(BattlegroundsModClient::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(BattlegroundsModClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, Freecam::onMovementInput);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, Freecam::onCameraAngles);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        BattlegroundsMod.LOGGER.info("Battlegrounds Mod: client setup");
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        Keybinds.ALL.forEach(event::register);
        event.register(Freecam.KEY);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "power_hud"), PowerHud::render);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Keybinds.tick();
        Freecam.tick();
    }
}
