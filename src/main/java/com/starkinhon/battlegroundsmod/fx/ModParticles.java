package com.starkinhon.battlegroundsmod.fx;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredRegister;

// The mod's own particles, drawn exactly like the Forja's: solid 8x8 pixel shapes (textures/particle), always bright.
// The helpers below copy the Forja's particle functions (flame, dust, smoke, lava...), random sizes and lifetimes
// included, so a skill written in the Forja looks the same in game. Send them like any particle (Fx.burst, Fx.moving).
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, BattlegroundsMod.MODID);

    // Teardrop of fire, yellow inside and orange outside. Shrinks to half at the end.
    public static final Supplier<ParticleType<SoulParticleOptions>> FLAME = PARTICLE_TYPES.register("flame", ModParticles::type);
    // Same teardrop in soul-fire blue.
    public static final Supplier<ParticleType<SoulParticleOptions>> SOUL_FLAME = PARTICLE_TYPES.register("soul_flame", ModParticles::type);
    // Round speck tinted by the color, shrinking to 45%.
    public static final Supplier<ParticleType<SoulParticleOptions>> DUST = PARTICLE_TYPES.register("dust", ModParticles::type);
    // Soft dark smoke cloud that rises a little, billows out, turns and tears apart as it fades.
    public static final Supplier<ParticleType<SoulParticleOptions>> SMOKE = PARTICLE_TYPES.register("smoke", ModParticles::type);
    // Hot drop, yellow to orange, that falls with gravity.
    public static final Supplier<ParticleType<SoulParticleOptions>> LAVA = PARTICLE_TYPES.register("lava", ModParticles::type);
    // Soft cloud puff (dust, wind, smoke) tinted by the color: billows out fast, keeps spreading and thins out over
    // the second half of its life.
    public static final Supplier<ParticleType<SoulParticleOptions>> PUFF = PARTICLE_TYPES.register("puff", ModParticles::type);
    // Thin soft line of light along its movement, size is half its length: stretches out and fades. Speed lines.
    public static final Supplier<ParticleType<SoulParticleOptions>> STREAK = PARTICLE_TYPES.register("streak", ModParticles::type);
    // A fist seen from the front, tinted by the color: pops in, holds and shrinks away. The barrage's afterimages.
    public static final Supplier<ParticleType<SoulParticleOptions>> FIST = PARTICLE_TYPES.register("fist", ModParticles::type);
    // A circle outline that grows from nothing to its size while it lives: wind rings and impact waves.
    public static final Supplier<ParticleType<SoulParticleOptions>> RING = PARTICLE_TYPES.register("ring", ModParticles::type);
    // Procedural smoke drawn by a shader (client/fx/SmokeParticle): billows, ground dust, wind lines, shadow smoke,
    // sparks and marks on the ground. The generic smoke and puffs below are drawn with it.
    public static final Supplier<ParticleType<SmokeOptions>> SMOKE_FX = PARTICLE_TYPES.register("smoke_fx", ModParticles::smokeType);

    // Thread safe: these helpers run on the server thread (bosses, skills) and on the client thread (effects) at the
    // same time in single player. A plain RandomSource.create() crashes with "Accessing LegacyRandomSource from multiple threads".
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    private ModParticles() {
    }

    private static ParticleType<SoulParticleOptions> type() {
        return new ParticleType<>(true) {
            private final MapCodec<SoulParticleOptions> codec = SoulParticleOptions.codec(this);
            private final StreamCodec<RegistryFriendlyByteBuf, SoulParticleOptions> streamCodec = SoulParticleOptions.streamCodec(this);

            @Override
            public MapCodec<SoulParticleOptions> codec() {
                return codec;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, SoulParticleOptions> streamCodec() {
                return streamCodec;
            }
        };
    }

    private static ParticleType<SmokeOptions> smokeType() {
        return new ParticleType<>(true) {
            private final MapCodec<SmokeOptions> codec = SmokeOptions.codec(this);
            private final StreamCodec<RegistryFriendlyByteBuf, SmokeOptions> streamCodec = SmokeOptions.streamCodec(this);

            @Override
            public MapCodec<SmokeOptions> codec() {
                return codec;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, SmokeOptions> streamCodec() {
                return streamCodec;
            }
        };
    }

    // One procedural smoke particle (see SmokeOptions for what each number does).
    public static SmokeOptions fx(int style, int color, float alpha, float size, float grow, int lifetime, float friction, float gravity,
            float flat, float stretch) {
        return new SmokeOptions(SMOKE_FX.get(), style, color, alpha, size, grow, lifetime, friction, gravity, flat, stretch);
    }

    // Vanilla particle lifetime and size, as the Forja draws them.
    private static int vanillaLife() {
        return (int) (8 / (RANDOM.nextFloat() * 0.8f + 0.2f));
    }

    private static float baseQuad() {
        return 0.1f * (RANDOM.nextFloat() * 0.5f + 0.5f) * 2;
    }

    public static SoulParticleOptions flame() {
        return new SoulParticleOptions(FLAME.get(), 0xFFFFFF, baseQuad(), vanillaLife() + 4, 0.96f, 0);
    }

    public static SoulParticleOptions soulFlame() {
        return new SoulParticleOptions(SOUL_FLAME.get(), 0xFFFFFF, baseQuad(), vanillaLife() + 4, 0.96f, 0);
    }

    // Like vanilla dust: scale 0.01 to 4 makes it bigger and last longer, and the color is shaded a little each time.
    public static SoulParticleOptions dust(int color, float scale) {
        scale = Mth.clamp(scale, 0.01f, 4);
        return dust(color, scale, (int) (vanillaLife() * scale), 0.96f);
    }

    // Dust with its own lifetime and friction and no size limit, like the Forja's shockwave.
    public static SoulParticleOptions dust(int color, float scale, int lifetime, float friction) {
        return new SoulParticleOptions(DUST.get(), shade(color), baseQuad() * 0.75f * scale, lifetime, friction, 0);
    }

    // Dark smoke that rises: thick procedural billow, lit from above.
    public static SmokeOptions smoke() {
        int gray = (int) ((0.12f + RANDOM.nextFloat() * 0.22f) * 255);
        return fx(SmokeOptions.BILLOW, gray << 16 | gray << 8 | gray, 0.85f, baseQuad() * 0.75f * 2.5f * 1.25f, 0.6f, (int) (vanillaLife() * 2.5f),
                0.96f, -0.1f, 1, 0);
    }

    // Smoke of a chosen color and size (half the width in blocks) that drifts up slowly: fire smoke, vanishing.
    public static SmokeOptions smoke(int color, float size, int lifetime) {
        return fx(SmokeOptions.BILLOW, color, 0.85f, size * 1.25f, 0.6f, lifetime, 0.94f, -0.02f, 1, 0);
    }

    public static SoulParticleOptions lava() {
        return new SoulParticleOptions(LAVA.get(), 0xFFFFFF, baseQuad() * (RANDOM.nextFloat() * 2 + 0.2f), (int) (16 / (RANDOM.nextFloat() * 0.8f + 0.2f)), 0.999f, 0.75f);
    }

    // Soft cloud puff (dust, wind, smoke) tinted by the color: billows out and tears apart as it fades.
    public static SmokeOptions puff(int color, float size, int lifetime, float friction) {
        return fx(SmokeOptions.BILLOW, color, 0.85f, size * 1.25f, 0.6f, lifetime, friction, 0, 1, 0);
    }

    public static SoulParticleOptions streak(int color, float halfLength, int lifetime) {
        return new SoulParticleOptions(STREAK.get(), color, halfLength, lifetime, 1, 0);
    }

    public static SoulParticleOptions fist(int color, float size, int lifetime) {
        return new SoulParticleOptions(FIST.get(), color, size, lifetime, 0.8f, 0);
    }

    public static SoulParticleOptions ring(int color, float size, int lifetime) {
        return new SoulParticleOptions(RING.get(), color, size, lifetime, 1, 0);
    }

    // The Forja's dustColor: each channel a bit random, then the whole color 60-100% as bright.
    private static int shade(int color) {
        float f = RANDOM.nextFloat() * 0.4f + 0.6f;
        int r = (int) (((color >> 16) & 0xFF) * (RANDOM.nextFloat() * 0.2f + 0.8f) * f);
        int g = (int) (((color >> 8) & 0xFF) * (RANDOM.nextFloat() * 0.2f + 0.8f) * f);
        int b = (int) ((color & 0xFF) * (RANDOM.nextFloat() * 0.2f + 0.8f) * f);
        return r << 16 | g << 8 | b;
    }
}
