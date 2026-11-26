// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.compat;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import com.mojang.logging.LogUtils;
import com.tom.cpl.render.RenderTypeBuilder;
import com.tom.cpm.shared.MinecraftClientAccess;
import com.tom.cpm.shared.definition.ModelDefinition;
import com.tom.cpm.shared.definition.ModelDefinitionLoader;
import com.tom.cpm.shared.skin.TextureProvider;
import com.tom.cpm.shared.model.RenderManager;
import com.tom.cpm.shared.model.TextureSheetType;
import com.tom.cpm.shared.model.render.ModelRenderManager;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

// The only class that touches CPM; loaded only when CPM is installed (CpmCompat checks first).
//
// It binds a model the way CPM binds a player's own body every frame (RenderManager.bindPlayer, what its
// RenderPlayerEvent.Pre does): from the player entity, so CPM fills the animation state from it. The API's
// PlayerRenderer (#594-#609) starts from an empty AnimationState and CPM threw a NullPointerException on it
// (syncState null, Stark's log 2026-11-26), which is why the first person arm stayed vanilla.
final class CpmBridge {
    private static boolean off;
    private static final Set<String> LOGGED = new HashSet<>();
    // CPM's client objects (com.tom.cpm.client.*, not in the API jar), found once.
    private static RenderManager<?, Player, Model, MultiBufferSource> manager;
    private static Constructor<?> modelTexture;
    // MinecraftObject.DynTexture.getBoundLoc(): the ResourceLocation of the CPM texture bound last
    private static java.lang.reflect.Method boundLoc;

    private CpmBridge() {
    }

    // One line in the log per kind of failure, never a flood every frame.
    static void failed(Throwable t) {
        if (LOGGED.size() < 20 && LOGGED.add(t.getClass().getName() + ":" + t.getMessage())) {
            LogUtils.getLogger().warn("Souls: CPM bind failed (this frame drawn without CPM)", t);
        }
        // a missing CPM class or method: this CPM version is not the one the compat was written for, stop trying
        if (t instanceof LinkageError || t instanceof ReflectiveOperationException) {
            off = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void find() throws ReflectiveOperationException {
        if (manager == null) {
            Class<?> client = Class.forName("com.tom.cpm.client.CustomPlayerModelsClient");
            Object instance = client.getField("INSTANCE").get(null);
            manager = (RenderManager<?, Player, Model, MultiBufferSource>) client.getField("manager").get(instance);
            modelTexture = Class.forName("com.tom.cpm.client.ModelTexture").getConstructor(ResourceLocation.class, Function.class);
            boundLoc = Class.forName("com.tom.cpm.client.MinecraftObject$DynTexture").getMethod("getBoundLoc");
            LogUtils.getLogger().info("Souls: CPM compat on");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static CpmCompat.Bound bind(Model model, Player player, ResourceLocation skin, MultiBufferSource buffers,
            Function<ResourceLocation, RenderType> type) throws ReflectiveOperationException {
        if (off) {
            return null;
        }
        find();
        if (manager == null) {
            return null;
        }
        ModelRenderManager parts = (ModelRenderManager) MinecraftClientAccess.get().getPlayerRenderManager();
        if (parts.isBound(model)) {
            // already CPM's right now (drawn from inside CPM's own render): leave it alone
            return null;
        }
        manager.bindPlayer(player, buffers, model);
        if (!parts.isBound(model)) {
            // this player has no CPM model (or it is still loading): nothing bound, draw the vanilla way
            return null;
        }
        ResourceLocation texture;
        try {
            // CPM's own texture holder (its render manager casts to it): the player's skin unless the model has its own
            RenderTypeBuilder.TextureHandler<ResourceLocation, RenderType> tex =
                    (RenderTypeBuilder.TextureHandler<ResourceLocation, RenderType>) modelTexture.newInstance(skin, type);
            parts.bindSkin(model, tex, TextureSheetType.SKIN);
            texture = tex.getTexture() != null ? tex.getTexture() : skin;
        } catch (Throwable t) {
            manager.unbindFlush(model);
            throw t;
        }
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
                    manager.unbindFlush(model);
                }
            }
        };
    }

    // The texture of player's CPM model and its size in skin pixels (CPM textures are often 128x128 "pixels" with the
    // vanilla 64x64 layout in their top left corner, each pixel 16 texels). Null: no CPM model loaded for them, or a
    // model without a texture of its own (it wears the vanilla skin).
    @SuppressWarnings({"unchecked", "rawtypes"})
    static CpmCompat.Skin skin(Player player) throws ReflectiveOperationException {
        if (off) {
            return null;
        }
        find();
        ModelDefinitionLoader loader = MinecraftClientAccess.get().getDefinitionLoader();
        if (loader == null) {
            return null;
        }
        com.tom.cpm.shared.config.Player<?> pl = loader.loadPlayer(player.getGameProfile(), ModelDefinitionLoader.PLAYER_UNIQUE);
        ModelDefinition def = pl == null ? null : pl.getModelDefinition();
        if (def == null) {
            return null;
        }
        TextureProvider tp = def.getTexture(TextureSheetType.SKIN, false);
        if (tp == null || tp.texture == null || tp.getSize() == null || tp.getSize().x <= 0 || tp.getSize().y <= 0) {
            return null;
        }
        tp.bind();
        ResourceLocation loc = (ResourceLocation) boundLoc.invoke(null);
        return loc == null ? null : new CpmCompat.Skin(loc, tp.getSize().x, tp.getSize().y);
    }

    // Whether CPM has this model swapped for someone's CPM model right now (inside a player's render).
    static boolean bound(Model model) {
        if (off) {
            return false;
        }
        return ((ModelRenderManager) MinecraftClientAccess.get().getPlayerRenderManager()).isBound(model);
    }
}
