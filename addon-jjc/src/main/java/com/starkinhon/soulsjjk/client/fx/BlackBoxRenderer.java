package com.starkinhon.soulsjjk.client.fx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.soulsjjk.compat.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class BlackBoxRenderer {
    private BlackBoxRenderer() {
    }

    public static boolean active() {
        return false;
    }

    public static void box(int id, int mode, Vec3 base, Vec3 size, boolean awakened) {
        
    }

    public static void clear() {
        
    }

    // MISSING overlay

    // MISSING render
}
