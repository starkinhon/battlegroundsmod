package com.starkinhon.soulsjjk;

import com.starkinhon.soulsjjk.skill.ComboSpec;
import com.starkinhon.soulsjjk.skill.MedoSpec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

// The mod's status effects. Icons are in textures/mob_effect.
public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS, SoulsJjk.MODID);

    // No fall damage while it lasts; given by the standard moves and taken away on landing (see Movement).
    public static final RegistryObject<MobEffect> PULO_DUPLO = MOB_EFFECTS.register("pulo_duplo", () -> new Marker(0xBFE8FF));
    public static final RegistryObject<MobEffect> ESQUIVA = MOB_EFFECTS.register("esquiva", () -> new Marker(0xE8DCC0));

    // Fear: slower and a heavier jump, with a shaking screen and a racing heartbeat (client/Fear). Values in padrao.medo.json.
    public static final RegistryObject<MobEffect> MEDO = MOB_EFFECTS.register("medo", () -> {
        MedoSpec spec = MedoSpec.get();
        return new Harmful(0x3A0A0A)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, "a0a31aec-306f-58a6-93bb-f3815c3116e2",
                        -spec.lentidao(), AttributeModifier.Operation.MULTIPLY_TOTAL)
                .addAttributeModifier(Attributes.JUMP_STRENGTH, "a48b29d9-792f-5e2c-a72c-30eedb05f568",
                        -spec.pulo(), AttributeModifier.Operation.MULTIPLY_TOTAL);
    });

    // Stunned by a hit: slow, no jump, no attacks, skills or dodges (combat/Stun). Values in padrao.combo.json.
    public static final RegistryObject<MobEffect> ATORDOADO = MOB_EFFECTS.register("atordoado", () -> {
        ComboSpec.Atordoado spec = ComboSpec.get().atordoado();
        return new Harmful(0xF2D24B)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, "f499bdea-bb9e-5b1e-af96-ff1409594ec3",
                        -spec.lentidao(), AttributeModifier.Operation.MULTIPLY_TOTAL)
                .addAttributeModifier(Attributes.JUMP_STRENGTH, "4df1ca6e-574e-5256-be31-b21b09c501c2",
                        -spec.pulo(), AttributeModifier.Operation.MULTIPLY_TOTAL);
    });

    // Cooldown: just took a hit, so no attacks or skills for a moment; moving, guarding and dodging still work
    // (combat/Stun). Keeps a fight from turning into both sides spamming. Length in padrao.combo.json.
    public static final RegistryObject<MobEffect> COOLDOWN = MOB_EFFECTS.register("cooldown", () -> new Harmful(0x78AAE6));

    // Exhausted: the attack stamina ran out. No skills (punches still work) and open to the skills that need a
    // stunned target (combat/Stamina). Only shows it: the timer lives in PowerData.exhaustedUntil, so milk doesn't end it.
    public static final RegistryObject<MobEffect> EXAUSTO = MOB_EFFECTS.register("exausto", () -> new Harmful(0xC23B22));

    // Brain Damage: caught in Gojo's domain, the infinite information leaves the brain fried. No moving, no jumping,
    // no attacks or skills: through the whole domain and for 10 s after it ends (power/powers/gojo/InfiniteVoid).
    public static final RegistryObject<MobEffect> BRAIN_DAMAGE = MOB_EFFECTS.register("brain_damage",
            () -> new Harmful(0xD68CBE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "e09e0ecb-9a51-5201-8214-2cb1ae30f350",
                            -1, AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, "bfdafb11-a1e9-53a2-af95-ec94fbc1d336",
                            -1, AttributeModifier.Operation.MULTIPLY_TOTAL));

    // Rooted: standing still to use a move (Madara's Katon). No walking, jumping or dodging, but the camera still turns.
    // No icon; taken away as soon as the move ends.
    public static final RegistryObject<MobEffect> ENRAIZADO = MOB_EFFECTS.register("enraizado",
            () -> new Marker(0xC0602A)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "367aff38-95a8-5517-a788-ac820175c977",
                            -1, AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, "1a8ff169-1b68-5482-ac46-015ec9fd8c06",
                            -1, AttributeModifier.Operation.MULTIPLY_TOTAL));

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
