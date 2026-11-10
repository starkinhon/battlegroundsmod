package com.starkinhon.soulsjjk.compat;

import net.minecraft.resources.ResourceLocation;

// 1.21-style payload marker, carried over our own Forge channel (see Net).
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
