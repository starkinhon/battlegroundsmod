package com.starkinhon.battlegroundsmod.client.fx;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.fx.SoulParticleOptions;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Client side of the mod's particles, a copy of the Forja's particle shader: solid pixel shapes (no blur, no
// transparency), always bright, sized and shrunk over their life the same way the Forja does it.
// Smoke and puffs are the exception: soft see-through clouds that billow out, turn and tear apart as they fade, lit
// by the world. Streaks are soft lines of light that stretch out and fade.
public class SoulParticle extends TextureSheetParticle {
    private enum Kind { FLAME, DUST, SMOKE, LAVA, PUFF, STREAK, FIST, RING }

    // Cloud textures: CLOUD_SHAPES different clouds, each in CLOUD_FRAMES steps from full to torn apart.
    private static final int CLOUD_SHAPES = 4;
    private static final int CLOUD_FRAMES = 6;
    // Soft edges leave the drawn cloud smaller than its square; this brings it back to the size asked for.
    private static final float CLOUD_SCALE = 1.5f;

    // See-through, blended and not writing depth, so overlapping soft clouds and lines don't cut holes in each other.
    static final ParticleRenderType SOFT = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "battlegroundsmod:soft";
        }
    };

    private final SpriteSet sprites;
    private final Kind kind;
    private final float size;
    private final boolean flip;
    private final Vector3f axis;
    private final boolean cloud;
    private final int shape;
    private final float maxAlpha;
    private float spin;

    protected SoulParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SoulParticleOptions options,
            SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.kind = kind(options.type());
        this.size = options.size();
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = Math.max(1, options.lifetime());
        this.friction = options.friction();
        this.gravity = options.gravity();
        this.hasPhysics = kind == Kind.LAVA;
        // Textures are stored a little darker so the brightest pixel fits; the color makes up for it.
        float boost = kind == Kind.DUST || kind == Kind.SMOKE ? 1.15f : kind == Kind.PUFF ? 1.12f : 1;
        this.rCol = Math.min(1, ((options.color() >> 16) & 0xFF) / 255f * boost);
        this.gCol = Math.min(1, ((options.color() >> 8) & 0xFF) / 255f * boost);
        this.bCol = Math.min(1, (options.color() & 0xFF) / 255f * boost);
        this.flip = kind != Kind.FLAME && kind != Kind.FIST && random.nextBoolean();
        Vec3 velocity = new Vec3(xd, yd, zd);
        this.axis = velocity.lengthSqr() > 1.0E-8 ? velocity.normalize().toVector3f() : new Vector3f(0, 1, 0);
        this.cloud = kind == Kind.PUFF || kind == Kind.SMOKE;
        this.shape = random.nextInt(CLOUD_SHAPES);
        this.maxAlpha = kind == Kind.SMOKE ? 0.85f : kind == Kind.PUFF ? 0.8f : 1;
        if (cloud) {
            this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
            this.spin = (random.nextFloat() - 0.5f) * 0.08f;
        }
        updateLook();
    }

    private static Kind kind(ParticleType<?> type) {
        if (type == ModParticles.DUST.get()) {
            return Kind.DUST;
        } else if (type == ModParticles.SMOKE.get()) {
            return Kind.SMOKE;
        } else if (type == ModParticles.LAVA.get()) {
            return Kind.LAVA;
        } else if (type == ModParticles.PUFF.get()) {
            return Kind.PUFF;
        } else if (type == ModParticles.STREAK.get()) {
            return Kind.STREAK;
        } else if (type == ModParticles.FIST.get()) {
            return Kind.FIST;
        } else if (type == ModParticles.RING.get()) {
            return Kind.RING;
        }
        return Kind.FLAME;
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            if (cloud) {
                oRoll = roll;
                roll += spin;
                spin *= 0.96f;
            }
            updateLook();
        }
    }

    private void updateLook() {
        float f = Math.min(1, age / (float) lifetime);
        if (cloud) {
            // Fades in over 3 ticks, holds, then thins out over the second half while it tears apart.
            int frame = Math.min(CLOUD_FRAMES - 1, (int) (f * CLOUD_FRAMES));
            setSprite(sprites.get(shape * CLOUD_FRAMES + frame, CLOUD_SHAPES * CLOUD_FRAMES - 1));
            alpha = maxAlpha * Math.min(1, (age + 1) / 3f) * (f < 0.5f ? 1 : (1 - f) / 0.5f);
        } else {
            setSpriteFromAge(sprites);
            if (kind == Kind.STREAK) {
                alpha = f < 0.4f ? 1 : (1 - f) / 0.6f;
            }
        }
    }

    // Same as the Forja's quadSize().
    @Override
    public float getQuadSize(float partialTick) {
        float age = this.age + partialTick;
        float f = Math.min(1, age / lifetime);
        return switch (kind) {
            case FLAME -> size * (1 - f * f * 0.5f);
            // Billows out fast, then keeps spreading slowly while it fades.
            case PUFF, SMOKE -> {
                float grow = Math.min(1, age / Math.max(4, lifetime * 0.3f));
                yield size * CLOUD_SCALE * (0.5f + 0.5f * (1 - (1 - grow) * (1 - grow))) * (1 + 0.4f * f);
            }
            // Shoots out to its full length in the first ticks.
            case STREAK -> size * (0.35f + 0.65f * Math.min(1, age / 2.5f));
            case FIST -> size * Math.min(1, age / 2) * (f < 0.7f ? 1 : 1 - (f - 0.7f) / 0.3f);
            case RING -> size * (float) Math.sqrt(f);
            case LAVA -> size * (1 - f * f);
            default -> size * Math.min(1, f * 32);
        };
    }

    // Big puffs and long streaks reach far from their center; without this they vanish at the edge of the screen.
    @Override
    public AABB getRenderBoundingBox(float partialTick) {
        return getBoundingBox().inflate(size * 2 + 1);
    }

    @Override
    protected float getU0() {
        return flip ? super.getU1() : super.getU0();
    }

    @Override
    protected float getU1() {
        return flip ? super.getU0() : super.getU1();
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (kind != Kind.STREAK) {
            super.render(buffer, camera, partialTick);
            return;
        }
        // A thin strip along the movement, turned around it to face the camera. Width is 6% of its full length: the
        // texture is a bright core with a soft glow, so it reads thinner than that.
        Vec3 cam = camera.getPosition();
        Vector3f center = new Vector3f((float) (Mth.lerp(partialTick, xo, x) - cam.x), (float) (Mth.lerp(partialTick, yo, y) - cam.y),
                (float) (Mth.lerp(partialTick, zo, z) - cam.z));
        float length = getQuadSize(partialTick);
        Vector3f along = new Vector3f(axis).mul(length);
        Vector3f across = new Vector3f(axis).cross(center);
        if (across.lengthSquared() < 1.0E-6f) {
            return;
        }
        across.normalize().mul(Math.max(0.025f, size * 0.06f));
        int light = getLightColor(partialTick);
        Vector3f[] corners = {
                new Vector3f(center).sub(along).sub(across), new Vector3f(center).sub(along).add(across),
                new Vector3f(center).add(along).add(across), new Vector3f(center).add(along).sub(across)};
        float[][] uv = {{getU0(), getV1()}, {getU1(), getV1()}, {getU1(), getV0()}, {getU0(), getV0()}};
        // Both windings, so it shows from either side.
        for (int i = 0; i < 4; i++) {
            vertex(buffer, corners[i], uv[i], light);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(buffer, corners[i], uv[i], light);
        }
    }

    private void vertex(VertexConsumer buffer, Vector3f pos, float[] uv, int light) {
        buffer.addVertex(pos.x, pos.y, pos.z).setUv(uv[0], uv[1]).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return cloud || kind == Kind.STREAK ? SOFT : ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    // Clouds take the world's light (dark smoke stays dark, nothing glows at night), never darker than a lit room.
    @Override
    protected int getLightColor(float partialTick) {
        if (!cloud) {
            return LightTexture.FULL_BRIGHT;
        }
        int light = super.getLightColor(partialTick);
        return LightTexture.pack(Math.max(8, LightTexture.block(light)), LightTexture.sky(light));
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SoulParticleOptions> {
        @Override
        public Particle createParticle(SoulParticleOptions options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new SoulParticle(level, x, y, z, xd, yd, zd, options, sprites);
        }
    }
}
