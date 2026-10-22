package com.starkinhon.battlegroundsmod.fx;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

// The mod's own particles (textures in assets/battlegroundsmod/textures/particle). Use the helpers below to make
// their settings; send them like any particle (Fx.burst, Fx.moving).
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, BattlegroundsMod.MODID);

    // Soft round light. Meant to be additive: fire, energy, sparks.
    public static final Supplier<ParticleType<SoulParticleOptions>> GLOW = PARTICLE_TYPES.register("glow", ModParticles::type);
    // Lumpy puff of smoke or cloud, tinted by the color.
    public static final Supplier<ParticleType<SoulParticleOptions>> SMOKE = PARTICLE_TYPES.register("smoke", ModParticles::type);

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

    // Additive light that keeps its speed.
    public static SoulParticleOptions glow(int color, float size, float sizeEnd, int lifetime, float alpha) {
        return new SoulParticleOptions(GLOW.get(), color, size, sizeEnd, lifetime, alpha, true, 1f, 0f);
    }

    // See-through smoke that slows down like the Forja's clouds (friction 0.97).
    public static SoulParticleOptions smoke(int color, float size, float sizeEnd, int lifetime, float alpha) {
        return new SoulParticleOptions(SMOKE.get(), color, size, sizeEnd, lifetime, alpha, false, 0.97f, 0f);
    }

    public static SoulParticleOptions with(SoulParticleOptions options, float friction, float gravity) {
        return new SoulParticleOptions(options.type(), options.color(), options.size(), options.sizeEnd(), options.lifetime(),
                options.alpha(), options.additive(), friction, gravity);
    }
}
