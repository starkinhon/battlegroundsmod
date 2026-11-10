package com.starkinhon.soulsjjk.power;

import net.minecraft.server.level.ServerPlayer;

// The Souls multiverse progression is not part of the JujutsuCraft addon: JJC has its own grades.
public final class Progression {
    public static boolean clientOn = false;

    private Progression() {
    }

    public static boolean on(ServerPlayer player) {
        return false;
    }

    public static void sync(ServerPlayer player) {
    }
}
