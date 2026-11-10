package com.starkinhon.soulsjjk.client.fx;

import org.joml.Matrix4f;
import com.starkinhon.soulsjjk.compat.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.starkinhon.soulsjjk.SoulsJjk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class SpaceScene {
    private SpaceScene() {
    }

    public static boolean active() {
        return false;
    }

    // MISSING backdrop

    // MISSING flames

    // MISSING owns

    // MISSING render
}
