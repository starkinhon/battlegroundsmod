// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.challenge.Torneio;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The Tournament of Power's sky (Stark 2026-11-26, his frames): the purple, magenta and blue nebula with its stars, a
// cube of 6 pictures made by tools/sky/nebulosa.py (the same function draws the web preview's sky: JOGO = PRÉVIA). It
// turns very slowly. No sun, moon, clouds or stars of the vanilla sky, and the fog stays far: the arena floats in space.
public final class TorneioSky {
    private static final String[] FACES = {"up", "down", "north", "south", "east", "west"};
    // per face: right, up, forward (Minecraft axes, as in nebulosa.py)
    private static final float[][][] AXES = {
            {{1, 0, 0}, {0, 0, -1}, {0, 1, 0}},
            {{1, 0, 0}, {0, 0, 1}, {0, -1, 0}},
            {{-1, 0, 0}, {0, 1, 0}, {0, 0, -1}},
            {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}},
            {{0, 0, -1}, {0, 1, 0}, {1, 0, 0}},
            {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}},
    };
    private static final ResourceLocation[] TEX = new ResourceLocation[6];

    static {
        for (int i = 0; i < 6; i++) {
            TEX[i] = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/environment/torneio_" + FACES[i] + ".png");
        }
    }

    private TorneioSky() {
    }

    public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "torneio"),
                new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, false) {
                    @Override
                    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
                        return color;
                    }

                    @Override
                    public boolean isFoggyAt(int x, int y) {
                        return false;
                    }

                    @Override
                    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
                        draw(ticks + partialTick, modelViewMatrix);
                        return true;
                    }
                });
    }

    private static void draw(float time, Matrix4f modelView) {
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.0015f));
        Matrix4f m = pose.last().pose();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        float s = 100f;
        for (int i = 0; i < 6; i++) {
            float[] r = AXES[i][0], u = AXES[i][1], f = AXES[i][2];
            RenderSystem.setShaderTexture(0, TEX[i]);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            corner(b, m, r, u, f, s, -1, 1, 0, 0);
            corner(b, m, r, u, f, s, -1, -1, 0, 1);
            corner(b, m, r, u, f, s, 1, -1, 1, 1);
            corner(b, m, r, u, f, s, 1, 1, 1, 0);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    // the face's point right*a + up*c + forward, at texture (tu, tv)
    private static void corner(BufferBuilder b, Matrix4f m, float[] r, float[] u, float[] f, float s, float a, float c, float tu, float tv) {
        b.addVertex(m, (r[0] * a + u[0] * c + f[0]) * s, (r[1] * a + u[1] * c + f[1]) * s, (r[2] * a + u[2] * c + f[2]) * s).setUv(tu, tv);
    }

    // The fog only past most of the view (the far rocks fade, the arena stays sharp).
    public static void onFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Torneio.LEVEL || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float far = event.getFarPlaneDistance();
        event.setNearPlaneDistance(far * 0.8f);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }
}
