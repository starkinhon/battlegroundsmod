package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// The Cabaça de Areia on the back of whoever carries it anywhere in the inventory (Stark 2026-11-12, Itens de Naruto),
// Gaara style: hanging diagonally from the right shoulder down to the left hip, cork up. The server says who carries it
// (power/PowerLogic.sheathCode +8). Drawn with the gourd's own cube model (models/item/cabaca_gaara_3d.json, no display
// transform), so it is the same object as in the hand and the icon.
public class GourdLayer<T extends LivingEntity> extends RenderLayer<T, PlayerModel<T>> {
    private static final Set<Integer> CARRYING = new HashSet<>();
    private static ItemStack stack;

    public GourdLayer(RenderLayerParent<T, PlayerModel<T>> parent) {
        super(parent);
    }

    public static void set(int entityId, boolean carrying) {
        if (carrying) {
            CARRYING.add(entityId);
        } else {
            CARRYING.remove(entityId);
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!CARRYING.contains(entity.getId()) || entity.isInvisible()) {
            return;
        }
        if (stack == null) {
            stack = new ItemStack(ModItems.SAND_GOURD.get());
        }
        poseStack.pushPose();
        // model space: +y down, +z the back; the body's pivot is at the neck
        getParentModel().body.translateAndRotate(poseStack);
        // In game (2026-11-13 prints) 180-28 put the cork over the LEFT shoulder and z 0.27 sank the gourd's front half
        // into the body (it is ~9.5 px deep), so it looked small and off to one side. Mirrored tilt puts the cork over the
        // right shoulder; z = torso half depth (2 px) + armour layer (1 px) + half the gourd's depth, so it rests on the back.
        float scale = 0.95f;
        poseStack.translate(0, 0.4, (3.0 + 4.75 * scale) / 16.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180 + 28));
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }
}
