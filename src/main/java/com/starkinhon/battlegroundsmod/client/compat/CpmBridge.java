// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.compat;

import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.tom.cpm.api.IClientAPI;
import com.tom.cpm.api.ICPMPlugin;
import com.tom.cpm.api.ICommonAPI;
import com.tom.cpm.shared.animation.AnimationEngine;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;

// The only class that touches CPM's API; loaded only when CPM is installed (CpmCompat checks first).
final class CpmBridge {
    private static IClientAPI api;
    private static boolean broken;

    private CpmBridge() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener((InterModEnqueueEvent e) -> InterModComms.sendTo("cpm", "api",
                () -> (Supplier<ICPMPlugin>) Plugin::new));
    }

    static final class Plugin implements ICPMPlugin {
        @Override
        public void initClient(IClientAPI clientApi) {
            api = clientApi;
        }

        @Override
        public void initCommon(ICommonAPI commonApi) {
        }

        @Override
        public String getOwnerModId() {
            return BattlegroundsMod.MODID;
        }
    }

    static void broken(Throwable t) {
        if (!broken) {
            broken = true;
            LogUtils.getLogger().warn("Souls: CPM compat off (CPM API changed?)", t);
        }
    }

    static CpmCompat.Bound bind(Model model, GameProfile profile, MultiBufferSource buffers, Function<ResourceLocation, RenderType> type) {
        if (api == null || broken) {
            return null;
        }
        // A new renderer each time: CPM's keeps the last texture when the next player has no model.
        IClientAPI.PlayerRenderer<Model, ResourceLocation, RenderType, MultiBufferSource, GameProfile> r = api.createPlayerRenderer(Model.class,
                ResourceLocation.class, RenderType.class, MultiBufferSource.class, GameProfile.class);
        if (r == null) {
            return null;
        }
        r.setRenderModel(model);
        r.setRenderType(type);
        r.setGameProfile(profile);
        // PLAYER, also for the first person arms: they are drawn where the body really holds them, not at the vanilla
        // hand's spot (HAND would move them to the model's first person hand position).
        r.preRender(buffers, AnimationEngine.AnimationMode.PLAYER);
        ResourceLocation texture = r.getDefaultTexture();
        if (texture == null) {
            // no CPM model for this player: preRender bound nothing
            return null;
        }
        RenderType renderType = r.getDefaultRenderType();
        return new CpmCompat.Bound() {
            private boolean ended;

            @Override
            public RenderType renderType() {
                return renderType;
            }

            @Override
            public ResourceLocation texture() {
                return texture;
            }

            @Override
            public void end() {
                if (!ended) {
                    ended = true;
                    r.postRender();
                }
            }
        };
    }
}
