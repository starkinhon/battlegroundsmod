package com.starkinhon.battlegroundsmod.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

// Settings of one of the mod's own particles: color (0xRRGGBB), size in blocks growing from size to sizeEnd,
// lifetime in ticks, starting alpha, additive glow or normal see-through, friction (1 = keeps its speed) and gravity.
// The texture comes from the particle type (glow, smoke...).
public record SoulParticleOptions(ParticleType<SoulParticleOptions> type, int color, float size, float sizeEnd, int lifetime,
        float alpha, boolean additive, float friction, float gravity) implements ParticleOptions {

    @Override
    public ParticleType<SoulParticleOptions> getType() {
        return type;
    }

    static MapCodec<SoulParticleOptions> codec(ParticleType<SoulParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("color").forGetter(SoulParticleOptions::color),
                Codec.FLOAT.fieldOf("size").forGetter(SoulParticleOptions::size),
                Codec.FLOAT.fieldOf("size_end").forGetter(SoulParticleOptions::sizeEnd),
                Codec.INT.fieldOf("lifetime").forGetter(SoulParticleOptions::lifetime),
                Codec.FLOAT.fieldOf("alpha").forGetter(SoulParticleOptions::alpha),
                Codec.BOOL.fieldOf("additive").forGetter(SoulParticleOptions::additive),
                Codec.FLOAT.fieldOf("friction").forGetter(SoulParticleOptions::friction),
                Codec.FLOAT.fieldOf("gravity").forGetter(SoulParticleOptions::gravity))
                .apply(instance, (color, size, sizeEnd, lifetime, alpha, additive, friction, gravity) ->
                        new SoulParticleOptions(type, color, size, sizeEnd, lifetime, alpha, additive, friction, gravity)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, SoulParticleOptions> streamCodec(ParticleType<SoulParticleOptions> type) {
        return StreamCodec.of((buf, options) -> {
            buf.writeInt(options.color);
            buf.writeFloat(options.size);
            buf.writeFloat(options.sizeEnd);
            buf.writeVarInt(options.lifetime);
            buf.writeFloat(options.alpha);
            buf.writeBoolean(options.additive);
            buf.writeFloat(options.friction);
            buf.writeFloat(options.gravity);
        }, buf -> new SoulParticleOptions(type, buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
                buf.readBoolean(), buf.readFloat(), buf.readFloat()));
    }
}
