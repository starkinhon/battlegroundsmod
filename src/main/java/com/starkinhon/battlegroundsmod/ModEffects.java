package com.starkinhon.battlegroundsmod;

import com.starkinhon.battlegroundsmod.skill.ComboSpec;
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

    // Stunned by a hit: slow, no jump, no attacks, skills or dodges (combat/Stun). Values in padrao.combo.json.
    public static final DeferredHolder<MobEffect, MobEffect> ATORDOADO = MOB_EFFECTS.register("atordoado", () -> {
        ComboSpec.Atordoado spec = ComboSpec.get().atordoado();
        return new Harmful(0xF2D24B)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "atordoado_speed"),
                        -spec.lentidao(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                .addAttributeModifier(Attributes.JUMP_STRENGTH, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "atordoado_jump"),
                        -spec.pulo(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    });

    // Cooldown: just took a hit, so no attacks or skills for a moment; moving, guarding and dodging still work
    // (combat/Stun). Keeps a fight from turning into both sides spamming. Length in padrao.combo.json.
    public static final DeferredHolder<MobEffect, MobEffect> COOLDOWN = MOB_EFFECTS.register("cooldown", () -> new Harmful(0x78AAE6));

    // Brain Damage: caught in Gojo's domain, the infinite information leaves the brain fried. No moving, no jumping,
    // no attacks or skills: through the whole domain and for 10 s after it ends (power/powers/gojo/InfiniteVoid).
    public static final DeferredHolder<MobEffect, MobEffect> BRAIN_DAMAGE = MOB_EFFECTS.register("brain_damage",
            () -> new Harmful(0xD68CBE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "brain_damage_speed"),
                            -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "brain_damage_jump"),
                            -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

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
