package com.starkinhon.soulsjjk.compat;

import java.util.function.BiConsumer;

import io.netty.buffer.ByteBuf;

// Same calls as NeoForge's registrar; every payload goes through Net's single Forge channel.
public final class PayloadRegistrar {
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        Net.register(type, codec, handler, true, false);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        Net.register(type, codec, handler, false, true);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type, StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        Net.register(type, codec, handler, true, true);
        return this;
    }
}
