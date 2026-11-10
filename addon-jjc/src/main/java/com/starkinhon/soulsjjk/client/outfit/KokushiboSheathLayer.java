package com.starkinhon.soulsjjk.client.outfit;

import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.soulsjjk.client.SkillAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class KokushiboSheathLayer {
    private KokushiboSheathLayer() {
    }

    public static boolean hidesHand(LivingEntity entity) {
        return false;
    }

    public static void holdArm(LivingEntity entity, net.minecraft.client.model.HumanoidModel<?> model, float ageInTicks) {
        
    }
}
