package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

// Screen passes, done by hand like the big effect mods do: copy the frame into our own texture, then draw one
// full-screen quad back onto it with a shader that reads the copy. Also owns the glow layer and its bloom.
public final class FxPost {
    private static final int GL_READ_FRAMEBUFFER = 36008;
    private static final int GL_DRAW_FRAMEBUFFER = 36009;
    private static final int GL_COLOR_BUFFER_BIT = 16384;
    private static final int GL_NEAREST = 9728;
    private static final int GL_LINEAR = 9729;
    private static final int BLOOM_LEVELS = 5;

    private static TextureTarget scene;
    private static TextureTarget glow;
    private static final TextureTarget[] BLOOM = new TextureTarget[BLOOM_LEVELS];
    private static boolean depthThisFrame;

    private FxPost() {
    }

    private static RenderTarget main() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    private static TextureTarget fit(TextureTarget target, int width, int height, boolean depth) {
        if (target == null) {
            target = new TextureTarget(width, height, depth, Minecraft.ON_OSX);
            target.setFilterMode(GL_LINEAR);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
            target.setFilterMode(GL_LINEAR);
        }
        return target;
    }

    // Called once per frame before anything reads the scene depth.
    static void newFrame() {
        depthThisFrame = false;
    }

    // Keeps this frame's depth (the hand and GUI clear it later) and colors.
    static void captureScene() {
        RenderTarget main = main();
        scene = fit(scene, main.width, main.height, true);
        scene.copyDepthFrom(main);
        copyColor();
        depthThisFrame = true;
    }

    // Texture of this frame's scene depth, or 0 when there is none (shader packs, or nothing captured yet).
    static int sceneDepth() {
        return depthThisFrame && scene != null ? scene.getDepthTextureId() : 0;
    }

    private static void copyColor() {
        RenderTarget main = main();
        scene = fit(scene, main.width, main.height, true);
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, scene.width, scene.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        main.bindWrite(false);
    }

    static void beginPasses() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
    }

    static void endPasses() {
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        main().bindWrite(true);
    }

    // One screen pass: the shader reads the frame as it is now (DiffuseSampler) and the scene depth (DepthSampler).
    // Call between beginPasses and endPasses.
    static void pass(ShaderInstance shader) {
        copyColor();
        shader.setSampler("DiffuseSampler", scene.getColorTextureId());
        int depth = sceneDepth();
        shader.setSampler("DepthSampler", depth > 0 ? depth : scene.getColorTextureId());
        main().bindWrite(false);
        fullScreen(shader);
    }

    private static void fullScreen(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(0, 0, 0);
        builder.addVertex(1, 0, 0);
        builder.addVertex(1, 1, 0);
        builder.addVertex(0, 1, 0);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    // The glow layer: an empty picture with the scene's depth, so glows still hide behind walls. Everything
    // additive draws into it, then endGlow blurs it and adds it onto the frame.
    static void beginGlow() {
        RenderTarget main = main();
        glow = fit(glow, main.width, main.height, true);
        glow.setClearColor(0, 0, 0, 0);
        glow.clear(Minecraft.ON_OSX);
        glow.copyDepthFrom(main);
        glow.bindWrite(false);
    }

    static void endGlow(boolean bloom) {
        RenderTarget main = main();
        beginPasses();
        TextureTarget source = glow;
        if (bloom) {
            for (int i = 0; i < BLOOM_LEVELS; i++) {
                BLOOM[i] = fit(BLOOM[i], Math.max(1, main.width >> (i + 1)), Math.max(1, main.height >> (i + 1)), false);
                BLOOM[i].bindWrite(true);
                FxShaders.bloomDown.setSampler("DiffuseSampler", source.getColorTextureId());
                FxShaders.bloomDown.safeGetUniform("Texel").set(1f / source.width, 1f / source.height);
                FxShaders.bloomDown.safeGetUniform("Threshold").set(0f);
                fullScreen(FxShaders.bloomDown);
                source = BLOOM[i];
            }
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            for (int i = BLOOM_LEVELS - 1; i > 0; i--) {
                BLOOM[i - 1].bindWrite(true);
                FxShaders.bloomUp.setSampler("DiffuseSampler", BLOOM[i].getColorTextureId());
                FxShaders.bloomUp.safeGetUniform("Texel").set(1f / BLOOM[i].width, 1f / BLOOM[i].height);
                fullScreen(FxShaders.bloomUp);
            }
        }
        main.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        FxShaders.bloomComp.setSampler("DiffuseSampler", glow.getColorTextureId());
        FxShaders.bloomComp.setSampler("BloomSampler", bloom ? BLOOM[0].getColorTextureId() : glow.getColorTextureId());
        FxShaders.bloomComp.safeGetUniform("Intensity").set(bloom ? 1.1f : 0f);
        fullScreen(FxShaders.bloomComp);
        endPasses();
    }
}
