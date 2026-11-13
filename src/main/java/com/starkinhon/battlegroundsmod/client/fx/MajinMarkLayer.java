package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

// Babidi's mark (universe/MajinMark): the M drawn on the forehead of a player who wears it, following the head as it
// is posed. A thin quad just off the front of the head, in the skin's own light.
public class MajinMarkLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/marca_majin.png");
    private static final Set<Integer> MARKED = new HashSet<>();

    public MajinMarkLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void set(int entityId, boolean on) {
        if (on) {
            MARKED.add(entityId);
        } else {
            MARKED.remove(entityId);
        }
    }

    public static void clear() {
        MARKED.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!MARKED.contains(entity.getId()) || entity.isInvisible() || !getParentModel().head.visible) {
            return;
        }
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // the head is 8x8x8 pixels from y -8 to 0; the M sits on the forehead, a hair in front of the face
        float x0 = -2.5f / 16, x1 = 2.5f / 16, y0 = -7.4f / 16, y1 = -4.9f / 16, z = -4.06f / 16;
        vertex(out, pose, x0, y1, z, 0, 1, packedLight);
        vertex(out, pose, x1, y1, z, 1, 1, packedLight);
        vertex(out, pose, x1, y0, z, 1, 0, packedLight);
        vertex(out, pose, x0, y0, z, 0, 0, packedLight);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light) {
        out.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, -1);
    }
}
