package com.starkinhon.battlegroundsmod.power;

import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.network.chat.Component;

// A selectable power: four abilities (the fourth is the ultimate), sometimes a fifth on its own key, plus a multiplier
// for the shared melee moves.
public record Power(String id, int color, float meleeMultiplier, List<Ability> abilities) {

    public Component name() {
        return Component.translatable("power." + BattlegroundsMod.MODID + "." + id);
    }

    public Component description() {
        return Component.translatable("power." + BattlegroundsMod.MODID + "." + id + ".desc");
    }

    public Component abilityName(int slot) {
        int index = slot == PowerData.SLOT_EXTRA ? 4 : slot;
        return Component.translatable("ability." + BattlegroundsMod.MODID + "." + id + "." + index);
    }

    // The skill slots this power has, in the order they are listed on screen (the ult last).
    public List<Integer> slots() {
        return abilities.size() > 4
                ? List.of(0, 1, 2, PowerData.SLOT_EXTRA, PowerData.SLOT_ULT)
                : List.of(0, 1, 2, PowerData.SLOT_ULT);
    }
}
