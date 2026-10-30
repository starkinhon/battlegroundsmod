package com.starkinhon.battlegroundsmod.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

// The power's skills (Z, X, C, V: index in ABILITIES = ability slot, with Shift held the Shift variant; B for a fifth skill) and the base fighting keys: R cycles the base
// attack selector (Shift + R goes back), G uses the selected base attack, F holds the guard and Left Alt dashes. All can be changed in Controls.
public final class Keybinds {
    private static final String CATEGORY = "key.categories.battlegroundsmod";

    public static final List<KeyMapping> ABILITIES = List.of(
            key("ability_1", GLFW.GLFW_KEY_Z),
            key("ability_2", GLFW.GLFW_KEY_X),
            key("ability_3", GLFW.GLFW_KEY_C),
            key("ultimate", GLFW.GLFW_KEY_V));
    public static final KeyMapping EXTRA_ABILITY = key("ability_4", GLFW.GLFW_KEY_B);
    public static final KeyMapping CYCLE_ATTACK = key("cycle_attack", GLFW.GLFW_KEY_R);
    public static final KeyMapping USE_ATTACK = key("use_attack", GLFW.GLFW_KEY_G);
    public static final KeyMapping GUARD = key("guard", GLFW.GLFW_KEY_F);
    public static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_LEFT_ALT);

    public static final List<KeyMapping> ALL = List.of(ABILITIES.get(0), ABILITIES.get(1), ABILITIES.get(2), ABILITIES.get(3),
            EXTRA_ABILITY, CYCLE_ATTACK, USE_ATTACK, GUARD, DASH);

    private Keybinds() {
    }

    // Whether the server was last told a skill key is down.
    private static boolean abilityHeld;

    // The key of a skill slot (a Shift variant uses its normal skill's key).
    public static KeyMapping forSlot(int slot) {
        return slot == PowerData.SLOT_EXTRA ? EXTRA_ABILITY : ABILITIES.get(PowerData.baseSlot(slot));
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.battlegroundsmod." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean ready = minecraft.player != null && minecraft.screen == null;
        // Down before the use, so a charged skill knows the key is held from its first tick.
        boolean held = ready && (EXTRA_ABILITY.isDown() || ABILITIES.stream().anyMatch(KeyMapping::isDown));
        if (held != abilityHeld) {
            abilityHeld = held;
            PacketDistributor.sendToServer(new ModPayloads.AbilityHold(held));
        }
        Power power = ready ? Powers.get(minecraft.player.getData(ModAttachments.POWER).power) : null;
        boolean shift = power != null && !power.shiftSlots().isEmpty() && minecraft.options.keyShift.isDown();
        for (int slot = 0; slot < ABILITIES.size(); slot++) {
            while (ABILITIES.get(slot).consumeClick()) {
                if (ready) {
                    PacketDistributor.sendToServer(new ModPayloads.UseAbility(shift ? PowerData.SLOT_SHIFT + slot : slot));
                }
            }
        }
        while (EXTRA_ABILITY.consumeClick()) {
            if (ready) {
                PacketDistributor.sendToServer(new ModPayloads.UseAbility(PowerData.SLOT_EXTRA));
            }
        }
        boolean hasPower = ready && minecraft.player.getData(ModAttachments.POWER).hasPower();
        while (CYCLE_ATTACK.consumeClick()) {
            if (hasPower) {
                BaseAttacks.cycle(minecraft.options.keyShift.isDown());
            }
        }
        // Down before the use, so the server already knows the key is held when the attack starts.
        BaseAttacks.holdKey(hasPower && USE_ATTACK.isDown());
        while (USE_ATTACK.consumeClick()) {
            if (hasPower) {
                BaseAttacks.use();
            }
        }
    }
}
