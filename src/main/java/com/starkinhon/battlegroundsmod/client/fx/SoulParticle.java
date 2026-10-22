package com.starkinhon.battlegroundsmod.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.fx.SoulParticleOptions;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;

// Client side of the mod's particles: fades in quickly, grows from size to sizeEnd, fades out, always full bright.
public class SoulParticle extends TextureSheetParticle {
    // Colors add up like light, so overlapping fire gets brighter instead of muddier.
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "battlegroundsmod:additive";
        }
    };

    private final SoulParticleOptions options;
    private final float spin;

    protected SoulParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SoulParticleOptions options) {
        super(level, x, y, z);
        this.options = options;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = Math.max(1, options.lifetime());
        this.friction = options.friction();
        this.gravity = options.gravity();
        this.hasPhysics = false;
        this.rCol = ((options.color() >> 16) & 0xFF) / 255f;
        this.gCol = ((options.color() >> 8) & 0xFF) / 255f;
        this.bCol = (options.color() & 0xFF) / 255f;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.spin = (random.nextFloat() - 0.5f) * 0.05f;
        update(0);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        update(age / (float) lifetime);
    }

    private void update(float t) {
        float in = Math.min(1, t / 0.1f);
        float out = t < 0.4f ? 1 : 1 - (t - 0.4f) / 0.6f;
        alpha = options.alpha() * in * Math.max(0, out);
        quadSize = Mth.lerp(t, options.size(), options.sizeEnd()) / 2;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return options.additive() ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SoulParticleOptions> {
        @Override
        public Particle createParticle(SoulParticleOptions options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            SoulParticle particle = new SoulParticle(level, x, y, z, xd, yd, zd, options);
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
