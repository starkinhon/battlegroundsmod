package com.starkinhon.battlegroundsmod.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

// Index in ALL matches the ability slot sent to the server (see PowerData).
public final class Keybinds {
    private static final String CATEGORY = "key.categories.battlegroundsmod";

    public static final List<KeyMapping> ALL = List.of(
            key("ability_1", GLFW.GLFW_KEY_Z),
            key("ability_2", GLFW.GLFW_KEY_X),
            key("ability_3", GLFW.GLFW_KEY_C),
            key("ultimate", GLFW.GLFW_KEY_V),
            key("strong_attack", GLFW.GLFW_KEY_R),
            key("barrage", GLFW.GLFW_KEY_G));

    private Keybinds() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.battlegroundsmod." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        for (int slot = 0; slot < ALL.size(); slot++) {
            while (ALL.get(slot).consumeClick()) {
                if (minecraft.player != null && minecraft.screen == null) {
                    PacketDistributor.sendToServer(new ModPayloads.UseAbility(slot));
                }
            }
        }
    }
}
