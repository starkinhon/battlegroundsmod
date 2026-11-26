// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.compat;

import java.util.function.Function;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

// Customizable Player Models (CPM, tom5454), optional. The mod draws players' bodies itself in a few places (the first
// person arms of a move, Kyoka's clone, the shadow clones, Saitama's afterimages); with CPM those must show the player's
// CPM model and skin, not the vanilla skin (Stark 2026-11-26, playing with friends). Without CPM nothing here runs, and no
// CPM class is ever loaded: everything that touches CPM is in CpmBridge.
public final class CpmCompat {
    private static final boolean LOADED = ModList.get().isLoaded("cpm");

    // A body bound to someone's CPM model: draw the model with renderType() (or texture() where the renderer picks the
    // render type itself), then end() right after, also when drawing throws.
    public interface Bound {
        RenderType renderType();

        ResourceLocation texture();

        void end();
    }

    private CpmCompat() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    // Called from the client mod constructor: hands CPM our plugin, which gets CPM's client API.
    public static void register(IEventBus modEventBus) {
        if (LOADED) {
            CpmBridge.register(modEventBus);
        }
    }

    // model drawn as profile's CPM model, from now until end(); null (nothing bound) without CPM, without a profile, or
    // when that player has no CPM model (or it is still downloading): then draw the vanilla way.
    public static Bound bind(Model model, GameProfile profile, MultiBufferSource buffers, Function<ResourceLocation, RenderType> type) {
        if (!LOADED || profile == null || model == null) {
            return null;
        }
        try {
            return CpmBridge.bind(model, profile, buffers, type);
        } catch (Throwable t) {
            // (a CPM version with another API: the vanilla skin, never a crash)
            CpmBridge.broken(t);
            return null;
        }
    }
}
