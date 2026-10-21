package com.starkinhon.battlegroundsmod.power;

import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.network.chat.Component;

// A selectable power: four abilities (the last one is the ultimate) plus a multiplier for the shared melee moves.
public record Power(String id, int color, float meleeMultiplier, List<Ability> abilities) {

    public Component name() {
        return Component.translatable("power." + BattlegroundsMod.MODID + "." + id);
    }

    public Component description() {
        return Component.translatable("power." + BattlegroundsMod.MODID + "." + id + ".desc");
    }

    public Component abilityName(int slot) {
        return Component.translatable("ability." + BattlegroundsMod.MODID + "." + id + "." + slot);
    }
}
