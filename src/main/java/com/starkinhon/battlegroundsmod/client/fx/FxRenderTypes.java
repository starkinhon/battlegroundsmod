package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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

    // The Susanoo's armored arm (SusanooArm): a depth-only pass first, so only its front surface gets colored (no
    // stacked glass), then the translucent color, then a copy into the glow layer for the bloom. All three use
    // souls_susanoo; the texturing shard hands it the time, the camera and which of the passes it is.
    private static final ShaderStateShard SUSANOO_SHADER = new ShaderStateShard(() -> FxShaders.susanoo);
    public static final RenderType SUS_DEPTH = create("battlegroundsmod_susanoo_depth", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES, 524288, false, false, CompositeState.builder()
                    .setShaderState(SUSANOO_SHADER)
                    .setWriteMaskState(DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setTexturingState(susanooInputs(false))
                    .createCompositeState(false));
    public static final RenderType SUS_COLOR = create("battlegroundsmod_susanoo_color", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES, 524288, false, false, CompositeState.builder()
                    .setShaderState(SUSANOO_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setTexturingState(susanooInputs(false))
                    .createCompositeState(false));
    public static final RenderType SUS_GLOW = create("battlegroundsmod_susanoo_glow", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES, 524288, false, false, CompositeState.builder()
                    .setShaderState(SUSANOO_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setTexturingState(susanooInputs(true))
                    .createCompositeState(false));
    // With a shader pack on: plain see-through color.
    public static final RenderType SUS_FLAT = create("battlegroundsmod_susanoo_flat", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 262144, false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static TexturingStateShard susanooInputs(boolean glow) {
        return new TexturingStateShard("battlegroundsmod_susanoo_inputs_" + glow, () -> {
            ShaderInstance shader = FxShaders.susanoo;
            if (shader == null) {
                return;
            }
            net.minecraft.world.phys.Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            shader.safeGetUniform("CamPos").set((float) cam.x, (float) cam.y, (float) cam.z);
            shader.safeGetUniform("Time").set((float) (SusanooArm.time() / 20.0));
            shader.safeGetUniform("GlowOnly").set(glow ? 1f : 0f);
        }, () -> {
        });
    }

    // Fallback with a shader pack on: one soft dot texture with vanilla's shader, so packs that replace shaders still draw it.
    private static final ResourceLocation SOFT = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/soft.png");
    private static final ShaderStateShard FLAT_SHADER = new ShaderStateShard(GameRenderer::getPositionTexColorShader);
    public static final RenderType FLAT_ADD = create("battlegroundsmod_flat_add", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 262144,
            false, false, CompositeState.builder()
                    .setShaderState(FLAT_SHADER)
                    .setTextureState(new TextureStateShard(SOFT, true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
    public static final RenderType FLAT_ALPHA = create("battlegroundsmod_flat_alpha", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 131072,
            false, true, CompositeState.builder()
                    .setShaderState(FLAT_SHADER)
                    .setTextureState(new TextureStateShard(SOFT, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // A picture drawn as is (the Mangekyo eye), see-through where the texture is.
    private static final java.util.Map<ResourceLocation, RenderType> DECALS = new java.util.HashMap<>();

    public static RenderType decal(ResourceLocation texture) {
        return DECALS.computeIfAbsent(texture, t -> create("battlegroundsmod_decal_" + t.getPath().replace('/', '_'), DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                        .setShaderState(FLAT_SHADER)
                        .setTextureState(new TextureStateShard(t, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false)));
    }

    // The same picture added as light on top of itself, so its bright parts glow.
    private static final java.util.Map<ResourceLocation, RenderType> DECAL_GLOWS = new java.util.HashMap<>();

    public static RenderType decalGlow(ResourceLocation texture) {
        return DECAL_GLOWS.computeIfAbsent(texture, t -> create("battlegroundsmod_decal_glow_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false, CompositeState.builder()
                        .setShaderState(FLAT_SHADER)
                        .setTextureState(new TextureStateShard(t, true, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false)));
    }

    // A body burning to ash (AshDeath): the entity's own texture through the souls_ash shader.
    private static final ShaderStateShard ASH_SHADER = new ShaderStateShard(() -> FxShaders.ash);
    private static final java.util.Map<ResourceLocation, RenderType> ASH = new java.util.HashMap<>();

    public static RenderType ash(ResourceLocation texture) {
        return ASH.computeIfAbsent(texture, t -> create("battlegroundsmod_ash_" + t.getNamespace() + "_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false, CompositeState.builder()
                        .setShaderState(ASH_SHADER)
                        .setTextureState(new TextureStateShard(t, false, false))
                        .setCullState(NO_CULL)
                        .createCompositeState(false)));
    }

    // A body with an aura, drawn flat into the aura mask (see Aura): its own texture only to skip see-through pixels.
    // Pulled a hair toward the camera so it isn't hidden by the same body already in the depth it tests against.
    private static final ShaderStateShard AURA_SHADER = new ShaderStateShard(() -> FxShaders.aura);
    private static final java.util.Map<ResourceLocation, RenderType> AURA = new java.util.HashMap<>();

    public static RenderType auraMask(ResourceLocation texture) {
        return AURA.computeIfAbsent(texture, t -> create("battlegroundsmod_aura_" + t.getNamespace() + "_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false, CompositeState.builder()
                        .setShaderState(AURA_SHADER)
                        .setTextureState(new TextureStateShard(t, false, false))
                        .setCullState(NO_CULL)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false)));
    }

    // The texture a vanilla render type draws with, or null when it has none or can't be read.
    private static final java.util.Map<RenderType, java.util.Optional<ResourceLocation>> TEXTURES = new java.util.HashMap<>();

    public static ResourceLocation textureOf(RenderType type) {
        return TEXTURES.computeIfAbsent(type, FxRenderTypes::readTexture).orElse(null);
    }

    // Vanilla keeps it private (CompositeRenderType.state.textureState.cutoutTexture()), so it is read by name.
    private static java.util.Optional<ResourceLocation> readTexture(RenderType type) {
        try {
            Object state = field(type, "state");
            Object texture = state == null ? null : field(state, "textureState");
            java.lang.reflect.Method cutout = texture == null ? null : method(texture.getClass(), "cutoutTexture");
            if (cutout == null) {
                return java.util.Optional.empty();
            }
            return cutout.invoke(texture) instanceof java.util.Optional<?> found && found.orElse(null) instanceof ResourceLocation location
                    ? java.util.Optional.of(location) : java.util.Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    private static Object field(Object owner, String name) throws IllegalAccessException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException e) {
                // keep looking up the hierarchy
            }
        }
        return null;
    }

    private static java.lang.reflect.Method method(Class<?> owner, String name) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                // keep looking up the hierarchy
            }
        }
        return null;
    }

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
