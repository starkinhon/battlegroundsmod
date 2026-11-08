package com.starkinhon.battlegroundsmod.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

// Settings of one procedural smoke particle (client/fx/SmokeParticle + shaders/core/souls_smoke), the look of the
// Fumaça Nova preview approved by Stark on 2026-11-08 (/mnt/project-files/forja/fumaca-nova).
// style: one of the constants below. color 0xRRGGBB (ink: the rim). alpha: how strong (additive ones too).
// size: half the width in blocks. grow: how much it keeps spreading over its life (decals: size at the end / size).
// flat: 1 = round, less squashes it flat (dust rolling on the ground). stretch: speed lines, length / width.
// For decals stretch says how they fade: 0 fades out, 1 holds, 2 fades in then out.
public record SmokeOptions(ParticleType<SmokeOptions> type, int style, int color, float alpha, float size, float grow, int lifetime,
        float friction, float gravity, float flat, float stretch) implements ParticleOptions {

    public static final int BILLOW = 0;
    public static final int DUST = 1;
    public static final int WISP = 2;
    public static final int INK = 3;
    public static final int ENERGY = 4;
    public static final int SPECK = 5;
    public static final int RING = 6;
    public static final int SHADOW = 7;
    public static final int GLOW = 8;

    @Override
    public ParticleType<SmokeOptions> getType() {
        return type;
    }

    public static boolean decal(int style) {
        return style >= RING;
    }

    public static boolean additive(int style) {
        return style == ENERGY || style == SPECK || style == RING || style == GLOW;
    }

    static MapCodec<SmokeOptions> codec(ParticleType<SmokeOptions> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("style").forGetter(SmokeOptions::style),
                Codec.INT.fieldOf("color").forGetter(SmokeOptions::color),
                Codec.FLOAT.fieldOf("alpha").forGetter(SmokeOptions::alpha),
                Codec.FLOAT.fieldOf("size").forGetter(SmokeOptions::size),
                Codec.FLOAT.fieldOf("grow").forGetter(SmokeOptions::grow),
                Codec.INT.fieldOf("lifetime").forGetter(SmokeOptions::lifetime),
                Codec.FLOAT.fieldOf("friction").forGetter(SmokeOptions::friction),
                Codec.FLOAT.fieldOf("gravity").forGetter(SmokeOptions::gravity),
                Codec.FLOAT.fieldOf("flat").forGetter(SmokeOptions::flat),
                Codec.FLOAT.fieldOf("stretch").forGetter(SmokeOptions::stretch))
                .apply(instance, (style, color, alpha, size, grow, lifetime, friction, gravity, flat, stretch) ->
                        new SmokeOptions(type, style, color, alpha, size, grow, lifetime, friction, gravity, flat, stretch)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, SmokeOptions> streamCodec(ParticleType<SmokeOptions> type) {
        return StreamCodec.of((buf, o) -> {
            buf.writeByte(o.style);
            buf.writeInt(o.color);
            buf.writeFloat(o.alpha);
            buf.writeFloat(o.size);
            buf.writeFloat(o.grow);
            buf.writeVarInt(o.lifetime);
            buf.writeFloat(o.friction);
            buf.writeFloat(o.gravity);
            buf.writeFloat(o.flat);
            buf.writeFloat(o.stretch);
        }, buf -> new SmokeOptions(type, buf.readByte(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
    }
}
