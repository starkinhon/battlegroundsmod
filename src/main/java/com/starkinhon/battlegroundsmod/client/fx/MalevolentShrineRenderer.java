package com.starkinhon.battlegroundsmod.client.fx;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.outfit.MeshModel;
import com.starkinhon.battlegroundsmod.domain.MalevolentShrine;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

// Draws the Malevolent Shrine: one baked mesh per material (models/mesh/malevolent_shrine_<material>.bin, 1 unit = 1
// block, mouth toward +z, ground at y = 0) with its own 32 x 32 texture (textures/models/malevolent_shrine/<material>.png).
// The UVs are in blocks and run past 1, so each texture repeats once per block; Minecraft binds mod textures with
// repeat wrapping. The lanterns glow at full light, the mouths' black backs too (they must stay pitch black, not lit).
public class MalevolentShrineRenderer extends EntityRenderer<MalevolentShrine> {
    private static final String[] MATERIALS = {"tile", "black", "dark", "wood", "gold", "post", "red", "void", "gum", "tooth",
            "bone", "horn", "hornB", "bark", "heap", "lamp"};
    private static final Map<String, MeshModel> MESHES = new LinkedHashMap<>();
    private static boolean broken;

    public MalevolentShrineRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    private static ResourceLocation texture(String material) {
        return ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/models/malevolent_shrine/" + material.toLowerCase() + ".png");
    }

    private static boolean ready() {
        if (broken) {
            return false;
        }
        if (MESHES.isEmpty()) {
            try {
                for (String m : MATERIALS) {
                    MESHES.put(m, MeshModel.load("malevolent_shrine_" + m.toLowerCase()));
                }
            } catch (RuntimeException e) {
                broken = true;
                MESHES.clear();
                BattlegroundsMod.LOGGER.error("Malevolent Shrine model failed to load", e);
                return false;
            }
        }
        return true;
    }

    @Override
    public void render(MalevolentShrine shrine, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (ready()) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-shrine.getYRot()));
            PoseStack.Pose last = pose.last();
            for (Map.Entry<String, MeshModel> e : MESHES.entrySet()) {
                String m = e.getKey();
                boolean glow = m.equals("lamp") || m.equals("void");
                int l = glow ? (m.equals("void") ? 0 : LightTexture.FULL_BRIGHT) : light;
                e.getValue().emit(buffers.getBuffer(RenderType.entityCutoutNoCull(texture(m))), last, l, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }
        super.render(shrine, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MalevolentShrine shrine) {
        return texture("tile");
    }
}
