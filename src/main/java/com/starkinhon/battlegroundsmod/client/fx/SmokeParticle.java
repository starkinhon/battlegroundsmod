package com.starkinhon.battlegroundsmod.client.fx;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.fx.SmokeOptions;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// The procedural smoke (Fumaça Nova, approved by Stark on 2026-11-08): no texture, the whole look is drawn by
// shaders/core/souls_smoke from a few numbers packed in each vertex (see souls_smoke.vsh). Billows, ground dust and
// shadow smoke face the camera; wind lines and energy stretch along their movement; rings, glows and shadows lie on
// the ground. Same numbers and curves as the preview page (/mnt/project-files/forja/fumaca-nova/tpl.html).
public class SmokeParticle extends Particle {
    // Blended, not writing depth, so soft clouds don't cut holes in each other.
    static final ParticleRenderType ALPHA = renderType(false);
    // Light added on top (energy, sparks, glowing rings).
    static final ParticleRenderType ADD = renderType(true);

    private static final double NO_GROUND = Double.NaN;

    private final int style;
    private final float size;
    private final float grow;
    private final float strength;
    private final float flat;
    private final float stretch;
    private final int seed;
    private final Vector3f axis;
    private double groundY = NO_GROUND;

    protected SmokeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SmokeOptions o) {
        super(level, x, y, z);
        this.style = o.style();
        this.size = o.size();
        this.grow = o.grow();
        this.strength = o.alpha();
        this.flat = o.flat();
        this.stretch = o.stretch();
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = Math.max(1, o.lifetime());
        this.friction = o.friction();
        this.gravity = o.gravity();
        this.rCol = ((o.color() >> 16) & 0xFF) / 255f;
        this.gCol = ((o.color() >> 8) & 0xFF) / 255f;
        this.bCol = (o.color() & 0xFF) / 255f;
        this.seed = random.nextInt(1024);
        // Dust and anything squashed flat rolls along the ground and stops on it.
        this.hasPhysics = !SmokeOptions.decal(style) && (style == SmokeOptions.DUST || flat < 0.99f);
        Vec3 velocity = new Vec3(xd, yd, zd);
        this.axis = velocity.lengthSqr() > 1.0E-8 ? velocity.normalize().toVector3f() : new Vector3f(0, 1, 0);
        findGround();
    }

    private static ParticleRenderType renderType(boolean additive) {
        return new ParticleRenderType() {
            @Override
            public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                if (additive) {
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                } else {
                    RenderSystem.defaultBlendFunc();
                }
                RenderSystem.setShader(() -> FxShaders.smoke);
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            }

            @Override
            public String toString() {
                return additive ? "battlegroundsmod:smoke_add" : "battlegroundsmod:smoke";
            }
        };
    }

    // Top of the first solid block under it (a few blocks down at most): the smoke fades out softly there instead of
    // being cut by the ground.
    private void findGround() {
        groundY = NO_GROUND;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int i = 0; i < 6; i++) {
            VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
            if (!shape.isEmpty()) {
                double top = at.getY() + shape.max(Direction.Axis.Y);
                if (top <= y + 0.5) {
                    groundY = top;
                    return;
                }
            }
            at.move(0, -1, 0);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return SmokeOptions.additive(style) ? ADD : ALPHA;
    }

    // Like Particle.tick, without the 0.7 slow down on the ground: dust keeps rolling out.
    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        if (SmokeOptions.decal(style)) {
            return;
        }
        yd -= 0.04 * gravity;
        move(xd, yd, zd);
        xd *= friction;
        yd *= friction;
        zd *= friction;
        if (hasPhysics && age % 4 == 0) {
            findGround();
        }
    }

    @Override
    public AABB getRenderBoundingBox(float partialTick) {
        return getBoundingBox().inflate(size * (2 + Math.max(stretch, grow)) + 1);
    }

    private float lookSize(float age, float f) {
        if (SmokeOptions.decal(style)) {
            float ease = 1 - (1 - f) * (1 - f);
            return size * (1 + (grow - 1) * ease);
        }
        return switch (style) {
            case SmokeOptions.WISP, SmokeOptions.ENERGY -> size;
            case SmokeOptions.SPECK -> size * (1 - f * 0.6f);
            default -> {
                float g = Math.min(1, age / Math.max(3, lifetime * 0.25f));
                yield size * (0.45f + 0.55f * (1 - (1 - g) * (1 - g))) * (1 + grow * f);
            }
        };
    }

    private float lookAlpha(float age, float f) {
        if (SmokeOptions.decal(style)) {
            int mode = Math.round(stretch);
            return strength * (mode == 1 ? 1 : mode == 2 ? (f < 0.3f ? f / 0.3f : 1 - (f - 0.3f) / 0.7f) : 1 - f);
        }
        float a = strength * Math.min(1, (age + 0.5f) / 2.5f);
        switch (style) {
            case SmokeOptions.WISP, SmokeOptions.ENERGY -> a *= f < 0.5f ? 1 : (1 - f) * 2;
            case SmokeOptions.SPECK -> a *= 1 - f * f;
            default -> a *= 1 - Math.max(0, (f - 0.9f) / 0.1f);
        }
        return a;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (FxShaders.smoke == null) {
            return;
        }
        float age = this.age + partialTick;
        float f = Math.min(1, age / lifetime);
        float s = lookSize(age, f);
        float a = Math.min(1, lookAlpha(age, f));
        if (s <= 0 || a <= 0.002f) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Vector3f c = new Vector3f((float) (Mth.lerp(partialTick, xo, x) - cam.x), (float) (Mth.lerp(partialTick, yo, y) - cam.y),
                (float) (Mth.lerp(partialTick, zo, z) - cam.z));
        Vector3f u;
        Vector3f v;
        if (SmokeOptions.decal(style)) {
            c.y += 0.02f;
            u = new Vector3f(s, 0, 0);
            v = new Vector3f(0, 0, s);
        } else if (style == SmokeOptions.WISP || style == SmokeOptions.ENERGY) {
            u = new Vector3f(axis).mul(s * Math.max(1, stretch));
            v = new Vector3f(axis).cross(c);
            if (v.lengthSquared() < 1.0E-6f) {
                return;
            }
            v.normalize().mul(s);
        } else {
            u = new Vector3f(camera.getLeftVector()).mul(-s);
            v = new Vector3f(camera.getUpVector()).mul(s);
            u.y *= flat;
            v.y *= flat;
        }
        int light = getLightColor(partialTick);
        int lifeQ = Math.round(f * 1000);
        int packed = style + 16 * seed;
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        Vector3f[] pos = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            float cx = corners[i][0] * 2 - 1;
            float cy = corners[i][1] * 2 - 1;
            pos[i] = new Vector3f(c).add(new Vector3f(u).mul(cx)).add(new Vector3f(v).mul(cy));
        }
        // Wind the quad towards the camera (back faces are culled).
        Vector3f n = new Vector3f(pos[1]).sub(pos[0]).cross(new Vector3f(pos[2]).sub(pos[0]));
        boolean flip = n.dot(pos[0]) > 0;
        for (int k = 0; k < 4; k++) {
            int i = flip ? 3 - k : k;
            Vector3f p = pos[i];
            int heightQ = 32767;
            if (!SmokeOptions.decal(style) && !Double.isNaN(groundY)) {
                double h = p.y + cam.y - groundY;
                heightQ = (int) Mth.clamp(Math.round((h + 8) * 256), 0, 32767);
            }
            buffer.addVertex(p.x, p.y, p.z)
                    .setColor(rCol, gCol, bCol, a)
                    .setUv(corners[i][0] + 2 * lifeQ, corners[i][1])
                    .setUv1(heightQ, packed)
                    .setLight(light)
                    .setNormal(0, 1, 0);
        }
    }

    public static class Provider implements ParticleProvider<SmokeOptions> {
        @Override
        public Particle createParticle(SmokeOptions options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new SmokeParticle(level, x, y, z, xd, yd, zd, options);
        }
    }
}
