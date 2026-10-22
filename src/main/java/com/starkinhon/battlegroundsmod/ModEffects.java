package com.starkinhon.battlegroundsmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The mod's status effects. Icons are in textures/mob_effect.
public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BattlegroundsMod.MODID);

    // No fall damage while it lasts; given by the standard moves and taken away on landing (see Movement).
    public static final DeferredHolder<MobEffect, MobEffect> PULO_DUPLO = MOB_EFFECTS.register("pulo_duplo", () -> new Marker(0xBFE8FF));
    public static final DeferredHolder<MobEffect, MobEffect> ESQUIVA = MOB_EFFECTS.register("esquiva", () -> new Marker(0xE8DCC0));

    private ModEffects() {
    }

    // An effect that does nothing by itself: other code checks for it.
    private static final class Marker extends MobEffect {
        Marker(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }
    }
}
