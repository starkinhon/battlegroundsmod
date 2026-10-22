package com.starkinhon.battlegroundsmod.client;

import net.minecraft.client.Minecraft;

// Client-only entry points called from common code. Only call these behind a level.isClientSide check.
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void playSkillAnimation(int entityId, String skill) {
        SkillAnimations.play(entityId, skill);
    }

    public static void openPowerSelect() {
        Minecraft.getInstance().setScreen(new PowerSelectScreen());
    }
}
