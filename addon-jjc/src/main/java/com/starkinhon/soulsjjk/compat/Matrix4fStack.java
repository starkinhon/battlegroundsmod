package com.starkinhon.soulsjjk.compat;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

// 1.21's RenderSystem.getModelViewStack() is a joml Matrix4fStack; in 1.20.1 it is a PoseStack. This wraps the
// PoseStack with the handful of Matrix4fStack calls the effects use, so their code stays the same.
public final class Matrix4fStack {
    private static final Matrix4fStack MODEL_VIEW = new Matrix4fStack();

    private Matrix4fStack() {
    }

    public static Matrix4fStack modelView() {
        return MODEL_VIEW;
    }

    private static PoseStack ps() {
        return RenderSystem.getModelViewStack();
    }

    public Matrix4fStack pushMatrix() {
        ps().pushPose();
        return this;
    }

    public Matrix4fStack popMatrix() {
        ps().popPose();
        return this;
    }

    public Matrix4fStack set(Matrix4f m) {
        ps().last().pose().set(m);
        ps().last().normal().set(new org.joml.Matrix3f(m));
        return this;
    }

    public Matrix4fStack identity() {
        ps().setIdentity();
        return this;
    }

    public Matrix4fStack mul(Matrix4f m) {
        ps().mulPoseMatrix(m);
        return this;
    }

    public Matrix4fStack translate(float x, float y, float z) {
        ps().translate(x, y, z);
        return this;
    }

    public Matrix4fStack translate(double x, double y, double z) {
        ps().translate(x, y, z);
        return this;
    }
}
