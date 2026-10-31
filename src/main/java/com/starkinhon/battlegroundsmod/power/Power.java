package com.starkinhon.battlegroundsmod.power;

import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.network.chat.Component;

// A selectable power: four abilities (the fourth is the ultimate), sometimes a fifth on its own key, plus a multiplier
// for the shared melee moves. shiftAbilities (empty, four or five) are the variants used with Shift held: Z, X, C, V (ult)
// and the fifth skill. A null entry (Arrays.asList) means that key has no variant: with Shift it still uses the normal skill.
public record Power(String id, int color, float meleeMultiplier, List<Ability> abilities, List<Ability> shiftAbilities) {

    public Power(String id, int color, float meleeMultiplier, List<Ability> abilities) {
        this(id, color, meleeMultiplier, abilities, List.of());
    }

    public Component name() {
        return Component.translatable("power." + BattlegroundsMod.MODID + "." + id);
    }

    public Component description() {
        return Component.translatable("power." + BattlegroundsMod.MODID + "." + id + ".desc");
    }

    public Component abilityName(int slot) {
        // Shift variants are 5-9 in the language files, after the fifth skill.
        int index = slot == PowerData.SLOT_EXTRA ? 4 : slot >= PowerData.SLOT_SHIFT ? slot - PowerData.SLOT_SHIFT + 5 : slot;
        return Component.translatable("ability." + BattlegroundsMod.MODID + "." + id + "." + index);
    }

    // The skill slots this power has, in the order they are listed on screen (the ult last).
    public List<Integer> slots() {
        return abilities.size() > 4
                ? List.of(0, 1, 2, PowerData.SLOT_EXTRA, PowerData.SLOT_ULT)
                : List.of(0, 1, 2, PowerData.SLOT_ULT);
    }

    // The Shift variant slots, in the order listed on screen (the ult last), or none.
    public List<Integer> shiftSlots() {
        if (shiftAbilities.size() < 4) {
            return List.of();
        }
        return List.of(shiftSlot(0), shiftSlot(1), shiftSlot(2), shiftSlot(3));
    }

    // The slot a key uses (0-3: Z, X, C, V; 4: B), with or without Shift held.
    public int keySlot(int key, boolean shift) {
        if (!shift) {
            return key == 4 ? PowerData.SLOT_EXTRA : key;
        }
        return shiftSlot(key);
    }

    // The key (0-3: Z, X, C, V; 4: B) that uses a slot. A Shift variant uses its normal skill's key.
    public int slotKey(int slot) {
        int base = PowerData.baseSlot(slot);
        return base == PowerData.SLOT_EXTRA ? 4 : base;
    }

    // The slot a skill key (0-3: Z, X, C, V; 4: the fifth skill) uses with Shift held: its variant, or the normal skill
    // when it has none.
    public int shiftSlot(int key) {
        int normal = key == 4 ? PowerData.SLOT_EXTRA : key;
        return key < shiftAbilities.size() && shiftAbilities.get(key) != null ? PowerData.SLOT_SHIFT + key : normal;
    }
}
