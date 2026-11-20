// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx.epic;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

// A big set of blocks that moves as one piece (Freeza's ship coming down, the Ilha Colossal): built into GPU buffers once
// and then drawn every frame with one matrix, instead of building each block's model again every frame (Stark
// 2026-11-19: 90 FPS down to 10-20). Render thread only; close() gives the buffers back.
public final class BakedBlocks implements AutoCloseable {
    public interface Sink {
        void add(BlockState state, float x, float y, float z, int light);
    }

    private final Map<RenderType, VertexBuffer> parts = new LinkedHashMap<>();

    private BakedBlocks() {
    }

    public static BakedBlocks bake(Consumer<Sink> fill) {
        RenderSystem.assertOnRenderThread();
        BakedBlocks out = new BakedBlocks();
        Map<RenderType, ByteBufferBuilder> memory = new HashMap<>();
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        MultiBufferSource source = type -> builders.computeIfAbsent(type, t -> {
            ByteBufferBuilder bytes = new ByteBufferBuilder(t.bufferSize());
            memory.put(t, bytes);
            return new BufferBuilder(bytes, t.mode(), t.format());
        });
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();
        PoseStack pose = new PoseStack();
        try {
            fill.accept((state, x, y, z, light) -> {
                pose.pushPose();
                pose.translate(x, y, z);
                br.renderSingleBlock(state, pose, source, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            });
            for (Map.Entry<RenderType, BufferBuilder> e : builders.entrySet()) {
                MeshData mesh = e.getValue().build();
                if (mesh == null) {
                    continue;
                }
                VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(mesh);
                VertexBuffer.unbind();
                out.parts.put(e.getKey(), vb);
            }
        } finally {
            memory.values().forEach(ByteBufferBuilder::close);
        }
        return out;
    }

    // pose: camera-relative, like the PoseStacks the effects build (the level's model-view is put in front).
    public void draw(Matrix4f pose) {
        if (parts.isEmpty()) {
            return;
        }
        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
        for (Map.Entry<RenderType, VertexBuffer> e : parts.entrySet()) {
            RenderType type = e.getKey();
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                VertexBuffer vb = e.getValue();
                vb.bind();
                vb.drawWithShader(mv, RenderSystem.getProjectionMatrix(), shader);
            }
            type.clearRenderState();
        }
        VertexBuffer.unbind();
    }

    @Override
    public void close() {
        parts.values().forEach(VertexBuffer::close);
        parts.clear();
    }
}
