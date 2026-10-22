package com.starkinhon.battlegroundsmod;

import com.starkinhon.battlegroundsmod.skill.MedoSpec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The mod's status effects. Icons are in textures/mob_effect.
public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BattlegroundsMod.MODID);

    // No fall damage while it lasts; given by the standard moves and taken away on landing (see Movement).
    public static final DeferredHolder<MobEffect, MobEffect> PULO_DUPLO = MOB_EFFECTS.register("pulo_duplo", () -> new Marker(0xBFE8FF));
    public static final DeferredHolder<MobEffect, MobEffect> ESQUIVA = MOB_EFFECTS.register("esquiva", () -> new Marker(0xE8DCC0));

    // Fear: slower and a heavier jump, with a shaking screen and a racing heartbeat (client/Fear). Values in padrao.medo.json.
    public static final DeferredHolder<MobEffect, MobEffect> MEDO = MOB_EFFECTS.register("medo", () -> {
        MedoSpec spec = MedoSpec.get();
        return new Harmful(0x3A0A0A)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "medo_speed"),
                        -spec.lentidao(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                .addAttributeModifier(Attributes.JUMP_STRENGTH, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "medo_jump"),
                        -spec.pulo(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    });

    private ModEffects() {
    }

    private static final class Harmful extends MobEffect {
        Harmful(int color) {
            super(MobEffectCategory.HARMFUL, color);
        }
    }

    // An effect that does nothing by itself: other code checks for it.
    private static final class Marker extends MobEffect {
        Marker(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }
    }
}
