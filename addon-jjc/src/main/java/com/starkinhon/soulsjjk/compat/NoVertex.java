package com.starkinhon.soulsjjk.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;

// A VertexConsumer that throws everything away (1.20.1 method set).
public class NoVertex implements VertexConsumer {
    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public void endVertex() {
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
    }

    @Override
    public void unsetDefaultColor() {
    }
}
