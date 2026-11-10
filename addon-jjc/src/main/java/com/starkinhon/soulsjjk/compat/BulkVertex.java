package com.starkinhon.soulsjjk.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;

// 1.21's VertexConsumer gets each whole vertex in one addVertex call (what the effects that catch a model's quads
// override). In 1.20.1 models send it either as one bulk vertex(...) call or as a chain ending in endVertex(); both
// land in addVertex here.
public abstract class BulkVertex extends NoVertex {
    private float x, y, z, u, v, nx, ny, nz;
    private int color = -1, overlay, light;

    public abstract void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz);

    @Override
    public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light, float nx,
            float ny, float nz) {
        addVertex(x, y, z, argb(r, g, b, a), u, v, overlay, light, nx, ny, nz);
    }

    private static int argb(float r, float g, float b, float a) {
        return ((int) (a * 255) & 255) << 24 | ((int) (r * 255) & 255) << 16 | ((int) (g * 255) & 255) << 8 | (int) (b * 255) & 255;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        overlay = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        light = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        nx = x;
        ny = y;
        nz = z;
        return this;
    }

    @Override
    public void endVertex() {
        addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
    }
}
