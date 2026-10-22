package com.starkinhon.battlegroundsmod.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

// The power's skills (Z, X, C, V: index in ABILITIES = ability slot) and the base fighting keys: R cycles the base
// attack selector, G uses the selected base attack, F holds the guard and Left Alt dashes. All can be changed in Controls.
public final class Keybinds {
    private static final String CATEGORY = "key.categories.battlegroundsmod";

    public static final List<KeyMapping> ABILITIES = List.of(
            key("ability_1", GLFW.GLFW_KEY_Z),
            key("ability_2", GLFW.GLFW_KEY_X),
            key("ability_3", GLFW.GLFW_KEY_C),
            key("ultimate", GLFW.GLFW_KEY_V));
    public static final KeyMapping CYCLE_ATTACK = key("cycle_attack", GLFW.GLFW_KEY_R);
    public static final KeyMapping USE_ATTACK = key("use_attack", GLFW.GLFW_KEY_G);
    public static final KeyMapping GUARD = key("guard", GLFW.GLFW_KEY_F);
    public static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_LEFT_ALT);

    public static final List<KeyMapping> ALL = List.of(ABILITIES.get(0), ABILITIES.get(1), ABILITIES.get(2), ABILITIES.get(3),
            CYCLE_ATTACK, USE_ATTACK, GUARD, DASH);

    private Keybinds() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.battlegroundsmod." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean ready = minecraft.player != null && minecraft.screen == null;
        for (int slot = 0; slot < ABILITIES.size(); slot++) {
            while (ABILITIES.get(slot).consumeClick()) {
                if (ready) {
                    PacketDistributor.sendToServer(new ModPayloads.UseAbility(slot));
                }
            }
        }
        boolean hasPower = ready && minecraft.player.getData(ModAttachments.POWER).hasPower();
        while (CYCLE_ATTACK.consumeClick()) {
            if (hasPower) {
                BaseAttacks.cycle();
            }
        }
        while (USE_ATTACK.consumeClick()) {
            if (hasPower) {
                BaseAttacks.use();
            }
        }
    }
}
