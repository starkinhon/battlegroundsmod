package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.world.TimeChamber;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The Room of Spirit and Time has no horizon (Stark's refs): the sky is the fog's color and the white floor melts into it.
public final class TimeChamberClient {
    private static final float R = 0.96f, G = 0.98f, B = 1.0f;

    private TimeChamberClient() {
    }

    private static boolean inRoom() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == TimeChamber.LEVEL;
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inRoom()) {
            return;
        }
        event.setRed(R);
        event.setGreen(G);
        event.setBlue(B);
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        if (!inRoom()) {
            return;
        }
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            event.setNearPlaneDistance(0);
            event.setFarPlaneDistance(0.01f);
        } else {
            event.setNearPlaneDistance(24);
            event.setFarPlaneDistance(150);
        }
        event.setCanceled(true);
    }
}
