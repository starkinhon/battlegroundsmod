package com.starkinhon.battlegroundsmod.client.fx;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Client side of the mod's particles, a copy of the Forja's particle shader: solid pixel shapes (no blur, no
// transparency), always bright, sized and shrunk over their life the same way the Forja does it.
public class SoulParticle extends TextureSheetParticle {
    private enum Kind { FLAME, DUST, SMOKE, LAVA, PUFF, STREAK }

    private final SpriteSet sprites;
    private final Kind kind;
    private final float size;
    private final boolean flip;
    private final Vector3f axis;

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
        this.flip = kind != Kind.FLAME && random.nextBoolean();
        Vec3 velocity = new Vec3(xd, yd, zd);
        this.axis = velocity.lengthSqr() > 1.0E-8 ? velocity.normalize().toVector3f() : new Vector3f(0, 1, 0);
        setSpriteFromAge(sprites);
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
        }
        return Kind.FLAME;
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
        }
    }

    // Same as the Forja's quadSize().
    @Override
    public float getQuadSize(float partialTick) {
        float age = this.age + partialTick;
        float f = Math.min(1, age / lifetime);
        return switch (kind) {
            case FLAME -> size * (1 - f * f * 0.5f);
            case PUFF -> size * Math.min(1, age / 4);
            case STREAK -> size;
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
        // A thin strip along the movement, turned around it to face the camera. Width is 5% of its length, like the
        // Forja's streak.
        Vec3 cam = camera.getPosition();
        Vector3f center = new Vector3f((float) (Mth.lerp(partialTick, xo, x) - cam.x), (float) (Mth.lerp(partialTick, yo, y) - cam.y),
                (float) (Mth.lerp(partialTick, zo, z) - cam.z));
        float length = getQuadSize(partialTick);
        Vector3f along = new Vector3f(axis).mul(length);
        Vector3f across = new Vector3f(axis).cross(center);
        if (across.lengthSquared() < 1.0E-6f) {
            return;
        }
        across.normalize().mul(length * 0.05f);
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
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SoulParticleOptions> {
        @Override
        public Particle createParticle(SoulParticleOptions options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new SoulParticle(level, x, y, z, xd, yd, zd, options, sprites);
        }
    }
}
