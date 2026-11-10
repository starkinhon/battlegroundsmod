package com.starkinhon.soulsjjk.compat;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraftforge.client.event.RenderLevelStageEvent;

// The camera's view rotation for a level render stage (1.21's event.getModelViewMatrix()).
// In Forge 1.20.1 every stage hands the view PoseStack except AFTER_LEVEL, whose PoseStack is the projection one
// (GameRenderer.renderLevel passes its local projection stack). There the view is rebuilt from the inverse view
// rotation the frame set up (camera pitch, yaw and roll, so camera mods and shakes still count).
public final class LevelView {
    private LevelView() {
    }

    public static Matrix4f of(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return new Matrix4f(new Matrix3f(RenderSystem.getInverseViewRotationMatrix()).invert());
        }
        return new Matrix4f(event.getPoseStack().last().pose());
    }
}
