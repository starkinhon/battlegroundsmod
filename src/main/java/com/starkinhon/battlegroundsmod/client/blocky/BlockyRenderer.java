package com.starkinhon.battlegroundsmod.client.blocky;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.animal.db.DbCreature;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

// Renders a DbCreature with its box model(s). One id = one model and skin; the Namekians have one per variant
// (namekiano_0..N), picked by the entity's variant.
public class BlockyRenderer<T extends DbCreature> extends MobRenderer<T, BlockyModel<T>> {
    private final List<BlockyModel<T>> models = new ArrayList<>();
    private final List<ResourceLocation> textures = new ArrayList<>();

    public BlockyRenderer(EntityRendererProvider.Context context, List<String> ids, BlockyModel.Anim<T> anim, float shadow) {
        super(context, load(context, ids.get(0), anim), shadow);
        for (String id : ids) {
            models.add(id.equals(ids.get(0)) ? model : load(context, id, anim));
            textures.add(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/" + id + ".png"));
        }
    }

    public BlockyRenderer(EntityRendererProvider.Context context, String id, BlockyModel.Anim<T> anim, float shadow) {
        this(context, List.of(id), anim, shadow);
    }

    private static <T extends DbCreature> BlockyModel<T> load(EntityRendererProvider.Context context, String id, BlockyModel.Anim<T> anim) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "blocky/" + id + ".json");
        try (Reader reader = context.getResourceManager().getResourceOrThrow(loc).openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return new BlockyModel<>(json, anim);
        } catch (Exception e) {
            throw new IllegalStateException("Souls: modelo " + loc + " não abriu", e);
        }
    }

    private int index(T entity) {
        return Math.floorMod(entity.variant(), models.size());
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        this.model = models.get(index(entity));
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return textures.get(index(entity));
    }
}
