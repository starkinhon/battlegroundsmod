package com.starkinhon.soulsjjk.compat;

import net.minecraft.world.entity.player.Player;

public interface IPayloadContext {
    Player player();

    void enqueueWork(Runnable work);
}
