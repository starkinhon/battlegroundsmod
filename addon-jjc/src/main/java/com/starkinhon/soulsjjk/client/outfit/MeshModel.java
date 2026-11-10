package com.starkinhon.soulsjjk.client.outfit;

import com.starkinhon.soulsjjk.compat.VX;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.soulsjjk.SoulsJjk;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

// A triangle mesh baked from a Blender file (assets/soulsjjk/models/mesh/*.bin): a big-endian int with the
// vertex count, then per vertex x y z u v nx ny nz as floats, three vertices per triangle. Positions are already in
// the model's own space (for the swords: 1 = one pixel of the player model, +y the tip, +x the edge, 0 = the guard).
public final class MeshModel {
    private final float[] data;

    private MeshModel(float[] data) {
        this.data = data;
    }

    public static MeshModel load(String name) {
        ResourceLocation id = new ResourceLocation(SoulsJjk.MODID, "models/mesh/" + name + ".bin");
        // Read in one go: float by float on the raw resource stream took ~2 s for the Santuário's 3.5 MB (test 2026-11-09).
        try (InputStream raw = Minecraft.getInstance().getResourceManager().open(id)) {
            ByteBuffer buf = ByteBuffer.wrap(raw.readAllBytes()).order(ByteOrder.BIG_ENDIAN);
            int count = buf.getInt();
            float[] data = new float[count * 8];
            buf.asFloatBuffer().get(data);
            return new MeshModel(data);
        } catch (IOException e) {
            throw new IllegalStateException("Missing mesh " + id, e);
        }
    }

    // Per vertex x y z u v nx ny nz, three vertices per triangle (FleshSheathe bends it vertex by vertex).
    float[] data() {
        return data;
    }

    // Each triangle goes out as a quad with its last corner repeated (entity render types draw quads).
    public void emit(VertexConsumer out, PoseStack.Pose pose, int light, int overlay) {
        float[] d = data;
        for (int i = 0; i < d.length; i += 24) {
            for (int v = 0; v < 4; v++) {
                int o = i + Math.min(v, 2) * 8;
                VX.v(out, pose, d[o], d[o + 1], d[o + 2]).color(1f, 1f, 1f, 1f).uv(d[o + 3], d[o + 4])
                        .overlayCoords(overlay).uv2(light).normal(VX.normal(pose, d[o + 5], d[o + 6], d[o + 7]).x(), VX.normal(pose, d[o + 5], d[o + 6], d[o + 7]).y(), VX.normal(pose, d[o + 5], d[o + 6], d[o + 7]).z()).endVertex();
            }
        }
    }
}
