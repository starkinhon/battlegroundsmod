package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;

// Raises vanilla's 1024 cap on max health (BattlegroundsMod.raiseHealthCap): the tier curve gives a Tier 0 1800 and a group
// boss several thousand, and the attribute was clamping all of them to 1024.
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {
    @Mutable
    @Accessor("maxValue")
    void battlegrounds$setMaxValue(double value);
}
