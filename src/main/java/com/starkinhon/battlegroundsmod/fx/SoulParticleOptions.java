package com.starkinhon.battlegroundsmod.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

// Settings of one of the mod's own particles, the same numbers the Forja uses: color (0xRRGGBB), size (half the width
// in blocks, the Forja's q), lifetime in ticks, friction (1 = keeps its speed) and gravity. The shape and how it
// shrinks come from the particle type (flame, dust, puff...).
public record SoulParticleOptions(ParticleType<SoulParticleOptions> type, int color, float size, int lifetime, float friction,
        float gravity) implements ParticleOptions {

    @Override
    public ParticleType<SoulParticleOptions> getType() {
        return type;
    }

    static MapCodec<SoulParticleOptions> codec(ParticleType<SoulParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("color").forGetter(SoulParticleOptions::color),
                Codec.FLOAT.fieldOf("size").forGetter(SoulParticleOptions::size),
                Codec.INT.fieldOf("lifetime").forGetter(SoulParticleOptions::lifetime),
                Codec.FLOAT.fieldOf("friction").forGetter(SoulParticleOptions::friction),
                Codec.FLOAT.fieldOf("gravity").forGetter(SoulParticleOptions::gravity))
                .apply(instance, (color, size, lifetime, friction, gravity) ->
                        new SoulParticleOptions(type, color, size, lifetime, friction, gravity)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, SoulParticleOptions> streamCodec(ParticleType<SoulParticleOptions> type) {
        return StreamCodec.of((buf, options) -> {
            buf.writeInt(options.color);
            buf.writeFloat(options.size);
            buf.writeVarInt(options.lifetime);
            buf.writeFloat(options.friction);
            buf.writeFloat(options.gravity);
        }, buf -> new SoulParticleOptions(type, buf.readInt(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat()));
    }
}
