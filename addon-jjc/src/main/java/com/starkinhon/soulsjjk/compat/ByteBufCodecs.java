package com.starkinhon.soulsjjk.compat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;

// The handful of 1.21 ByteBufCodecs the mod's payloads use, written for 1.20.1.
public final class ByteBufCodecs {
    private ByteBufCodecs() {
    }

    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of((b, v) -> writeVarInt(b, v), ByteBufCodecs::readVarInt);
    public static final StreamCodec<ByteBuf, Long> VAR_LONG = StreamCodec.of((b, v) -> writeVarLong(b, v), ByteBufCodecs::readVarLong);
    public static final StreamCodec<ByteBuf, Integer> INT = StreamCodec.of((b, v) -> b.writeInt(v), ByteBuf::readInt);
    public static final StreamCodec<ByteBuf, Long> LONG = StreamCodec.of((b, v) -> b.writeLong(v), ByteBuf::readLong);
    public static final StreamCodec<ByteBuf, Float> FLOAT = StreamCodec.of((b, v) -> b.writeFloat(v), ByteBuf::readFloat);
    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of((b, v) -> b.writeDouble(v), ByteBuf::readDouble);
    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of((b, v) -> b.writeBoolean(v), ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = StreamCodec.of((b, v) -> {
        byte[] bytes = v.getBytes(StandardCharsets.UTF_8);
        writeVarInt(b, bytes.length);
        b.writeBytes(bytes);
    }, b -> {
        int n = readVarInt(b);
        byte[] bytes = new byte[n];
        b.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    });
    public static final StreamCodec<ByteBuf, net.minecraft.resources.ResourceLocation> RESOURCE_LOCATION = STRING_UTF8
            .map(net.minecraft.resources.ResourceLocation::new, net.minecraft.resources.ResourceLocation::toString);
    public static final StreamCodec<ByteBuf, java.util.UUID> UUID = StreamCodec.of((b, v) -> {
        b.writeLong(v.getMostSignificantBits());
        b.writeLong(v.getLeastSignificantBits());
    }, b -> new java.util.UUID(b.readLong(), b.readLong()));

    public static <B extends ByteBuf, V> Function<StreamCodec<B, V>, StreamCodec<B, List<V>>> list() {
        return element -> StreamCodec.of((b, list) -> {
            writeVarInt(b, list.size());
            for (V v : list) {
                element.encode(b, v);
            }
        }, b -> {
            int n = readVarInt(b);
            List<V> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(element.decode(b));
            }
            return out;
        });
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.readByte();
            value |= (b & 127) << shift++ * 7;
            if (shift > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((b & 128) == 128);
        return value;
    }

    public static void writeVarLong(ByteBuf buf, long value) {
        while ((value & -128L) != 0L) {
            buf.writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        buf.writeByte((int) value);
    }

    public static long readVarLong(ByteBuf buf) {
        long value = 0L;
        int shift = 0;
        byte b;
        do {
            b = buf.readByte();
            value |= (long) (b & 127) << shift++ * 7;
            if (shift > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while ((b & 128) == 128);
        return value;
    }
}
