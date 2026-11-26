// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.starkinhon.battlegroundsmod.combat.CombatMode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;

// The mod's keys win over another mod's key on the same key (Stark 2026-11-26: with O or U, another mod's screen opened
// on top of the Experiência or the Livro das Almas). Minecraft clicks every mapping bound to the pressed key, so both
// mods reacted and the last screen opened stayed on top.
// Only Minecraft's own key event is used (window focused, in game), never a global key hook. At the key's press, before
// anyone's tick reads it, the other mods' mappings on that key are silenced: their queued clicks are drained and their
// held state cleared. The mod's own clicks stay untouched for Keybinds.tick, which opens the screen as before.
// - Screen keys (U, O, J): always, when no screen is open. A screen another mod opened from this same key press (in its
//   own key listener, before this one at LOWEST) is closed, so the mod's screen opens alone on the next tick.
// - Combat keys (skills, R, G, F, Alt, K, H, N, Y): only for a fighter (CombatMode.fighter); a player with no soul
//   keeps the other mod's key working.
// Minecraft's own keys are never silenced (a conflict with them shows red in Controls, the player decides), and another
// mod's key held with a modifier (Ctrl + O) that is down wins, since it was the one meant.
public final class KeyPriority {
    private static final List<KeyMapping> SCREEN_KEYS = List.of(Keybinds.SKILLS_MENU, Keybinds.SETTINGS, Keybinds.MISSIONS);

    // Whether the game had no screen open when this key event started (the HIGHEST listener).
    private static boolean inGameAtPress;
    // Minecraft's own mappings (every KeyMapping field of Options), found once.
    private static Set<KeyMapping> vanilla;

    private KeyPriority() {
    }

    // HIGHEST: before any other mod's key listener can open a screen.
    public static void onKeyFirst(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        inGameAtPress = minecraft.player != null && minecraft.screen == null && minecraft.getOverlay() == null;
    }

    // LOWEST: after every other mod's key listener.
    public static void onKeyLast(InputEvent.Key event) {
        if (!inGameAtPress || event.getAction() == GLFW.GLFW_RELEASE) {
            return;
        }
        inGameAtPress = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        InputConstants.Key input = InputConstants.getKey(event.getKey(), event.getScanCode());
        for (KeyMapping key : SCREEN_KEYS) {
            if (key.isActiveAndMatches(input)) {
                if (silenceOthers(minecraft, input) && event.getAction() == GLFW.GLFW_PRESS && minecraft.screen != null) {
                    // another mod opened its screen from this press: closed, the mod's own opens on the tick
                    minecraft.setScreen(null);
                }
                return;
            }
        }
        if (!CombatMode.fighter(minecraft.player)) {
            return;
        }
        for (KeyMapping key : Keybinds.ALL) {
            if (key.isActiveAndMatches(input)) {
                silenceOthers(minecraft, input);
                return;
            }
        }
    }

    // Drains the clicks and clears the held state of every other mod's mapping on this key. False when another mod's
    // mapping with a held modifier owns the press (the mod then yields).
    private static boolean silenceOthers(Minecraft minecraft, InputConstants.Key input) {
        Set<KeyMapping> own = vanilla(minecraft);
        boolean yield = false;
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (other.getKey().equals(input) && other.getKeyModifier() != KeyModifier.NONE
                    && other.getKeyModifier().isActive(other.getKeyConflictContext()) && !Keybinds.ALL.contains(other)) {
                yield = true;
            }
        }
        if (yield) {
            return false;
        }
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (!other.getKey().equals(input) || own.contains(other) || Keybinds.ALL.contains(other)) {
                continue;
            }
            while (other.consumeClick()) {
                // the other mod's press of this key: the mod's key took it
            }
            other.setDown(false);
        }
        return true;
    }

    private static Set<KeyMapping> vanilla(Minecraft minecraft) {
        if (vanilla != null) {
            return vanilla;
        }
        Set<KeyMapping> found = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Field field : Options.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || "keyMappings".equals(field.getName())) {
                continue;
            }
            try {
                if (field.getType() == KeyMapping.class) {
                    field.setAccessible(true);
                    Object value = field.get(minecraft.options);
                    if (value != null) {
                        found.add((KeyMapping) value);
                    }
                } else if (field.getType() == KeyMapping[].class) {
                    field.setAccessible(true);
                    Object value = field.get(minecraft.options);
                    if (value != null) {
                        Collections.addAll(found, (KeyMapping[]) value);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // unreadable field: its mapping is treated as another mod's
            }
        }
        vanilla = found;
        return found;
    }
}
