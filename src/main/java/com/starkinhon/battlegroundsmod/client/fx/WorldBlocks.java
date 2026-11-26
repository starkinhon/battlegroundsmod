// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.client.compat.IrisCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Copies of real blocks that powers throw around (Saitama's debris, Luffy's bouncing ground, the bosses' destruction,
// Shigaraki's decay...). They are drawn with the effects at AFTER_LEVEL, and with a shader pack on (Iris) that is after
// the pack has finished its picture: they came out flat, like vanilla blocks pasted over the shaded world (Stark
// 2026-11-26). So with a pack on, a block is only noted there (where it is in the world, its turn, its light) and drawn
// on the next frame while the world itself is drawn (AFTER_ENTITIES), where the pack lights it like any other block.
// The block then trails its real place by one frame; the camera's own move between the frames is taken out.
// Without a pack nothing changes: drawn right away, as before.
public final class WorldBlocks {
    private record Noted(BlockState state, Matrix4f pose, Matrix3f normal, int light, double camX, double camY, double camZ) {
    }

    // (a cap, in case the world pass doesn't come for a while: a menu, a frame without the level)
    private static final int MAX = 30000;
    private static List<Noted> noted = new ArrayList<>();
    private static List<Noted> drawing = new ArrayList<>();
    // drawing them in the world pass failed once: back to drawing them right away
    private static boolean broken;

    private WorldBlocks() {
    }

    // In place of blocks.renderSingleBlock(state, pose, source, light, NO_OVERLAY), with pose relative to the camera.
    public static void single(BlockRenderDispatcher blocks, BlockState state, PoseStack pose, MultiBufferSource source, int light) {
        if (broken || !IrisCompat.packOn()) {
            blocks.renderSingleBlock(state, pose, source, light, OverlayTexture.NO_OVERLAY);
            return;
        }
        if (noted.size() >= MAX) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        PoseStack.Pose last = pose.last();
        noted.add(new Noted(state, new Matrix4f(last.pose()), new Matrix3f(last.normal()), light, cam.x, cam.y, cam.z));
    }

    // A stage listener (BattlegroundsModClient, through world(...)).
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (noted.isEmpty()) {
            return;
        }
        // take this frame's list; the effects note the next one later in this same frame
        List<Noted> list = noted;
        noted = drawing;
        noted.clear();
        drawing = list;
        if (!IrisCompat.packOn()) {
            drawing.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            BlockRenderDispatcher blocks = mc.getBlockRenderer();
            // (our own buffers: the vanilla ones still hold the world's entities, flushed later in their order)
            MultiBufferSource.BufferSource source = FxWorld.buffers();
            PoseStack pose = new PoseStack();
            for (Noted n : drawing) {
                pose.pushPose();
                pose.translate(n.camX() - cam.x, n.camY() - cam.y, n.camZ() - cam.z);
                pose.last().pose().mul(n.pose());
                pose.last().normal().mul(n.normal());
                blocks.renderSingleBlock(n.state(), pose, source, n.light(), OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            source.endBatch();
        } catch (RuntimeException e) {
            broken = true;
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Drawing the effects' blocks in the shader pack's world pass failed; they are drawn with the effects again", e);
        } finally {
            drawing.clear();
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    public static void clear() {
        noted.clear();
        drawing.clear();
    }
}
