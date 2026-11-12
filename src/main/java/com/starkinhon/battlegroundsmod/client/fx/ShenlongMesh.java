package com.starkinhon.battlegroundsmod.client.fx;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

// Shenlong's sculpted parts (head, jaw and the four legs), the very meshes of the approved preview: forja/shenlong
// sculpts them (head.js, SDF -> surface nets), bakes them (bake.js) and export-game.js writes them to
// assets/battlegroundsmod/shenlong/parts.bin.gz. Each goes once into its own static GPU buffer, one region per
// triangle (as the preview draws them, so the colors never bleed across a border), drawn with souls_shen_body.
final class ShenlongMesh {
    static final int HEAD = 0, JAW = 1, LEG0 = 2;
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "shenlong/parts.bin.gz");
    private static VertexBuffer[] gpu;
    private static boolean broken;

    private ShenlongMesh() {
    }

    // The part's buffer (render thread), or null when the file could not be read.
    static VertexBuffer part(int i) {
        if (gpu == null && !broken) {
            try {
                load();
            } catch (IOException | RuntimeException e) {
                broken = true;
                BattlegroundsMod.LOGGER.error("Could not load Shenlong's meshes", e);
            }
        }
        return gpu == null ? null : gpu[i];
    }

    private static void load() throws IOException {
        try (InputStream raw = Minecraft.getInstance().getResourceManager().getResourceOrThrow(FILE).open();
                DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw)))) {
            int parts = in.readInt();
            VertexBuffer[] out = new VertexBuffer[parts];
            for (int p = 0; p < parts; p++) {
                int n = in.readInt(), tris = in.readInt();
                float scale = in.readFloat();
                float[] pos = new float[n * 3], nor = new float[n * 3];
                for (int i = 0; i < n * 3; i++) {
                    pos[i] = in.readShort() / 32767f * scale;
                }
                for (int i = 0; i < n * 3; i++) {
                    nor[i] = in.readByte() / 127f;
                }
                byte[] reg = new byte[tris];
                in.readFully(reg);
                int[] idx = new int[tris * 3];
                for (int i = 0; i < idx.length; i++) {
                    idx[i] = in.readInt();
                }
                try (ByteBufferBuilder bytes = new ByteBufferBuilder(tris * 3 * DefaultVertexFormat.NEW_ENTITY.getVertexSize())) {
                    BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
                    for (int t = 0; t < tris; t++) {
                        float r = (reg[t] & 0xFF) / 15f;
                        for (int k = 0; k < 3; k++) {
                            int v = idx[t * 3 + k];
                            float x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
                            // aP = the part's own position (the preview's partGeo); the arc comes from ArcFix
                            b.addVertex(x, y, z).setColor(0f, r, 0f, 1f).setUv(x, y).setUv1(0, 0)
                                    .setUv2(Math.round(z * 1000), 1000).setNormal(nor[v * 3], nor[v * 3 + 1], nor[v * 3 + 2]);
                        }
                    }
                    MeshData mesh = b.build();
                    VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    vb.bind();
                    if (mesh != null) {
                        vb.upload(mesh);
                    }
                    out[p] = vb;
                }
            }
            VertexBuffer.unbind();
            gpu = out;
        }
    }
}
