// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.compat;

import java.lang.reflect.Constructor;
import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.tom.cpm.api.IClientAPI;
import com.tom.cpm.api.ICPMPlugin;
import com.tom.cpm.api.ICommonAPI;
import com.tom.cpl.render.RenderTypeBuilder;
import com.tom.cpm.shared.MinecraftClientAccess;
import com.tom.cpm.shared.model.TextureSheetType;
import com.tom.cpm.shared.model.render.ModelRenderManager;
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
            LogUtils.getLogger().info("Souls: CPM compat on");
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

    // CPM's own texture holder (com.tom.cpm.client.ModelTexture, not in the API jar): its render manager casts to it, any
    // other TextureHandler throws (that switched the whole compat off in #606: the vanilla skin again).
    private static Constructor<?> modelTexture;

    @SuppressWarnings("unchecked")
    private static RenderTypeBuilder.TextureHandler<ResourceLocation, RenderType> modelTexture(ResourceLocation skin,
            Function<ResourceLocation, RenderType> type) throws ReflectiveOperationException {
        if (modelTexture == null) {
            modelTexture = Class.forName("com.tom.cpm.client.ModelTexture").getConstructor(ResourceLocation.class, Function.class);
        }
        return (RenderTypeBuilder.TextureHandler<ResourceLocation, RenderType>) modelTexture.newInstance(skin, type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static CpmCompat.Bound bind(Model model, GameProfile profile, ResourceLocation skin, MultiBufferSource buffers,
            Function<ResourceLocation, RenderType> type) throws ReflectiveOperationException {
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
        ModelRenderManager manager = (ModelRenderManager) MinecraftClientAccess.get().getPlayerRenderManager();
        if (!manager.isBound(model)) {
            // no CPM model for this player (or still loading): nothing was bound, draw the vanilla way
            return null;
        }
        // The API's own texture is null when the CPM model has no texture of its own (it then wears the player's skin):
        // bound again with the player's skin as the default (Stark 2026-11-26: the clones came out as Steve).
        RenderTypeBuilder.TextureHandler<ResourceLocation, RenderType> tex;
        try {
            tex = modelTexture(skin, type);
            manager.bindSkin(model, tex, TextureSheetType.SKIN);
        } catch (Throwable t) {
            r.postRender();
            throw t;
        }
        ResourceLocation texture = tex.getTexture() != null ? tex.getTexture() : skin;
        RenderType renderType = type.apply(texture);
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
