// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.logging.LogUtils;
import com.starkinhon.battlegroundsmod.client.fx.FxConfig;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;

// Shoulder Surfing Reloaded (recommended, optional) starts with the camera decoupled from the player, and in our fights
// the body turns in an odd way. Stark wants it to come as if "Toggle Camera Coupling" had been pressed once: the first
// time a world is joined with the mod installed, a decoupled camera is coupled through Shoulder Surfing's public API
// (IShoulderSurfing.toggleCameraCoupling, the same call as the key, which saves its own config file). A flag in our
// client config keeps it from ever happening again, so a player who turns it back keeps their choice.
// Reflection only: without Shoulder Surfing nothing loads and the flag stays off (installing it later still applies).
public final class ShoulderSurfingSetup {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String API = "com.github.exopandora.shouldersurfing.api.";

    private ShoulderSurfingSetup() {
    }

    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ModList.get().isLoaded("shouldersurfing") || FxConfig.get(FxConfig.SHOULDER_SURFING_SETUP)) {
            return;
        }
        try {
            // the methods are looked up on the API's interfaces, never on the hidden classes behind them
            Class<?> api = Class.forName(API + "client.IShoulderSurfing");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object clientConfig = api.getMethod("getClientConfig").invoke(instance);
            Object cameraConfig = Class.forName(API + "config.IClientConfig").getMethod("getCameraConfig").invoke(clientConfig);
            Method decoupled = Class.forName(API + "config.ICameraConfig").getMethod("isCameraDecoupled");
            if ((Boolean) decoupled.invoke(cameraConfig)) {
                api.getMethod("toggleCameraCoupling").invoke(instance);
            }
            FxConfig.set(FxConfig.SHOULDER_SURFING_SETUP, true);
        } catch (Throwable e) {
            // another Shoulder Surfing version without this API: leave its camera as it is, and don't try again
            LOGGER.warn("Souls: couldn't couple Shoulder Surfing's camera ({})", e.toString());
            FxConfig.set(FxConfig.SHOULDER_SURFING_SETUP, true);
        }
    }
}
