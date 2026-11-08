package com.starkinhon.battlegroundsmod.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

// The player's own camera (first or third person) and F1 state, kept once for every cutscene. Each cutscene used to keep
// its own copy: when two overlapped (the Destruction Bop followed by the trip to space), the second one kept the third
// person of the first and gave it back at the end, so the player was left in third person, or with the HUD gone
// (Stark, 2026-11-08). Now the first cutscene to open keeps it and only the last one to close gives it back.
public final class CutsceneCamera {
    private static final Set<String> OPEN = new HashSet<>();
    private static CameraType savedType = CameraType.FIRST_PERSON;
    private static boolean savedHideGui;

    private CutsceneCamera() {
    }

    // A cutscene opens (call once, when it was not already playing).
    public static void begin(String key) {
        if (OPEN.isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            savedType = minecraft.options.getCameraType();
            savedHideGui = minecraft.options.hideGui;
        }
        OPEN.add(key);
    }

    // A cutscene closes: the player's camera and HUD come back only when no other one is still playing.
    public static void end(String key) {
        if (!OPEN.remove(key) || !OPEN.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.setCameraType(savedType);
        minecraft.options.hideGui = savedHideGui;
    }

    // The player's own camera, from before the first cutscene opened.
    public static CameraType savedType() {
        return savedType;
    }

    public static boolean savedHideGui() {
        return savedHideGui;
    }
}
