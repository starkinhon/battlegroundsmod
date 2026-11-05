package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

// Who is wearing which outfit, as the server says (OutfitState): the power's id while the outfit is on, "" when off.
// The outfit goes on when a power is picked and can be taken off in the abilities menu (U).
public final class Outfits {
    private static final Map<Integer, String> WORN = new HashMap<>();

    private Outfits() {
    }

    // Does this power have an outfit at all?
    public static boolean exists(String power) {
        return OutfitDefs.has(power);
    }

    static String worn(int entityId) {
        return WORN.getOrDefault(entityId, "");
    }

    public static void set(int entityId, String power) {
        String old = WORN.put(entityId, power);
        if (!power.equals(old)) {
            OutfitLayer.forget(entityId);
        }
        if (power.isEmpty()) {
            WORN.remove(entityId);
        }
    }

    // Is the local player wearing their outfit?
    public static boolean selfOn() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && !worn(minecraft.player.getId()).isEmpty();
    }

    // Asks the server to put the outfit on or take it off. Shown at once here; the server confirms to everyone.
    public static void toggleSelf(String power) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        boolean on = !selfOn();
        set(minecraft.player.getId(), on ? power : "");
        PacketDistributor.sendToServer(new ModPayloads.ToggleOutfit(on));
    }

    public static void clear() {
        WORN.clear();
    }
}
