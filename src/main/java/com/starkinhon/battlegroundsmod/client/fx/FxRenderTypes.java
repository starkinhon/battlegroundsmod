package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

// Flat colored shapes for the mod's effects, all unlit (like the Forja's shaders), so colors look the same in game.
// Extends RenderType only to reach its state pieces.
public final class FxRenderTypes extends RenderType {
    // Solid, writes depth: bodies like the meteor rock.
    public static final RenderType SOLID = create("battlegroundsmod_fx_solid", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536,
            false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
    // Additive light that doesn't hide what is behind it: halos and glows.
    public static final RenderType GLOW = create("battlegroundsmod_fx_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536,
            false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
    // See-through color, like the Forja's floor markers.
    public static final RenderType AREA = create("battlegroundsmod_fx_area", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536,
            false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));


    // The souls_vfx shapes (see FxDraw). The additive one is drawn into the glow layer, which gets the bloom.
    private static final ShaderStateShard VFX_SHADER = new ShaderStateShard(() -> FxShaders.vfx);
    private static final TexturingStateShard VFX_INPUTS = new TexturingStateShard("battlegroundsmod_vfx_inputs", FxRenderTypes::vfxInputs, () -> {
    });
    public static final RenderType VFX_ADD = create("battlegroundsmod_vfx_add", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 262144,
            false, false, CompositeState.builder()
                    .setShaderState(VFX_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setTexturingState(VFX_INPUTS)
                    .createCompositeState(false));
    public static final RenderType VFX_ALPHA = create("battlegroundsmod_vfx_alpha", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 131072,
            false, true, CompositeState.builder()
                    .setShaderState(VFX_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setTexturingState(VFX_INPUTS)
                    .createCompositeState(false));

    // Fallback with a shader pack on: one soft dot texture with vanilla's shader, so packs that replace shaders still draw it.
    private static final ResourceLocation SOFT = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/soft.png");
    public static final RenderType FLAT_ADD = create("battlegroundsmod_flat_add", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 262144,
            false, false, CompositeState.builder()
                    .setShaderState(POSITION_TEX_COLOR_SHADER)
                    .setTextureState(new TextureStateShard(SOFT, true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
    public static final RenderType FLAT_ALPHA = create("battlegroundsmod_flat_alpha", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 131072,
            false, true, CompositeState.builder()
                    .setShaderState(POSITION_TEX_COLOR_SHADER)
                    .setTextureState(new TextureStateShard(SOFT, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // Scene depth for the soft edges, plus the numbers to read it.
    private static void vfxInputs() {
        ShaderInstance shader = FxShaders.vfx;
        if (shader == null) {
            return;
        }
        var target = Minecraft.getInstance().getMainRenderTarget();
        int depth = FxPost.sceneDepth();
        RenderSystem.setShaderTexture(1, depth);
        shader.safeGetUniform("Screen").set((float) target.width, (float) target.height);
        shader.safeGetUniform("DepthRange").set(0.05f, Minecraft.getInstance().gameRenderer.getDepthFar());
        shader.safeGetUniform("SoftOn").set(depth > 0 ? 1f : 0f);
    }

    private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
            boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
