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
    // Round dark smoke, shrinking to half.
    public static final Supplier<ParticleType<SoulParticleOptions>> SMOKE = PARTICLE_TYPES.register("smoke", ModParticles::type);
    // Hot drop, yellow to orange, that falls with gravity.
    public static final Supplier<ParticleType<SoulParticleOptions>> LAVA = PARTICLE_TYPES.register("lava", ModParticles::type);
    // Big cloud puff: grows in 4 ticks, keeps its size and only shrinks in the last 20% of its life.
    public static final Supplier<ParticleType<SoulParticleOptions>> PUFF = PARTICLE_TYPES.register("puff", ModParticles::type);
    // Thin line along its movement, size is half its length. Used for light streaks.
    public static final Supplier<ParticleType<SoulParticleOptions>> STREAK = PARTICLE_TYPES.register("streak", ModParticles::type);

    private static final RandomSource RANDOM = RandomSource.create();

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

    public static SoulParticleOptions smoke() {
        int gray = (int) (RANDOM.nextFloat() * 0.3f * 255);
        return new SoulParticleOptions(SMOKE.get(), gray << 16 | gray << 8 | gray, baseQuad() * 0.75f * 2.5f, (int) (vanillaLife() * 2.5f), 0.96f, -0.1f);
    }

    public static SoulParticleOptions lava() {
        return new SoulParticleOptions(LAVA.get(), 0xFFFFFF, baseQuad() * (RANDOM.nextFloat() * 2 + 0.2f), (int) (16 / (RANDOM.nextFloat() * 0.8f + 0.2f)), 0.999f, 0.75f);
    }

    public static SoulParticleOptions puff(int color, float size, int lifetime, float friction) {
        return new SoulParticleOptions(PUFF.get(), color, size, lifetime, friction, 0);
    }

    public static SoulParticleOptions streak(int color, float halfLength, int lifetime) {
        return new SoulParticleOptions(STREAK.get(), color, halfLength, lifetime, 1, 0);
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
