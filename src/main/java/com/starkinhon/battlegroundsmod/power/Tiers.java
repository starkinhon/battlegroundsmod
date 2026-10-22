package com.starkinhon.battlegroundsmod.power;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.skill.TierSpec;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

// Power tiers (fandom power scale): 0 Deus ... 10 Humano. Each tier above human adds health, strength, armor and speed,
// kept as saved attribute modifiers on the fighter (so health survives a relog) and refreshed by apply (players every tick, bosses when configured).
// Values in souls_skills/padrao.tiers.json.
public final class Tiers {
    public static final int DEUS = 0;
    public static final int HUMANO = 10;

    private static final ResourceLocation HEALTH = id("tier_health");
    private static final ResourceLocation STRENGTH = id("tier_strength");
    private static final ResourceLocation ARMOR = id("tier_armor");
    private static final ResourceLocation WALK = id("tier_walk");
    private static final ResourceLocation SPRINT = id("tier_sprint");

    private Tiers() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, path);
    }

    public static int clamp(int tier) {
        return Mth.clamp(tier, DEUS, HUMANO);
    }

    public static int of(LivingEntity entity) {
        return clamp(entity.getData(ModAttachments.POWER).tier);
    }

    // Tiers above human.
    private static int steps(int tier) {
        return HUMANO - clamp(tier);
    }

    public static String name(int tier) {
        var names = TierSpec.get().nomes();
        tier = clamp(tier);
        return names != null && tier < names.size() ? names.get(tier) : "Tier " + tier;
    }

    // How much harder the mod's own moves hit for this attacker's tier.
    public static float damageMultiplier(LivingEntity attacker) {
        TierSpec spec = TierSpec.get();
        return (float) (1 + spec.multiplicadorPorForca() * spec.forcaPorTier() * steps(of(attacker)));
    }

    // Puts this fighter's tier bonuses on its attributes. Cheap when nothing changed. Health keeps its fraction when
    // the max changes, so a new tier doesn't leave the player at 20 of 520.
    public static void apply(LivingEntity entity) {
        TierSpec spec = TierSpec.get();
        int steps = steps(of(entity));
        float healthFraction = entity.getHealth() / Math.max(1, entity.getMaxHealth());
        boolean healthChanged = set(entity, Attributes.MAX_HEALTH, HEALTH, spec.vidaPorTier() * steps, AttributeModifier.Operation.ADD_VALUE);
        set(entity, Attributes.ATTACK_DAMAGE, STRENGTH, spec.forcaPorTier() * steps, AttributeModifier.Operation.ADD_VALUE);
        int armorSteps = HUMANO - Math.max(DEUS, spec.tierArmaduraCheia());
        double armor = armorSteps <= 0 ? 0 : spec.armaduraMax() * Math.min(1, steps / (double) armorSteps);
        set(entity, Attributes.ARMOR, ARMOR, armor, AttributeModifier.Operation.ADD_VALUE);
        int speedSteps = Math.min(steps, HUMANO - spec.tierVelocidadeMax());
        set(entity, Attributes.MOVEMENT_SPEED, WALK, spec.velocidadeAndarPorTier() * speedSteps, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        // Speed potion levels are +20% each, on the base speed like the potion.
        boolean sprinting = entity instanceof Player && entity.isSprinting();
        set(entity, Attributes.MOVEMENT_SPEED, SPRINT, sprinting ? 0.2 * spec.speedCorrendoPorTier() * speedSteps : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (healthChanged) {
            entity.setHealth(Math.max(1, healthFraction * entity.getMaxHealth()));
        }
    }

    // Returns true when the modifier changed.
    private static boolean set(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current == null ? amount == 0 : current.amount() == amount) {
            return false;
        }
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, op));
        }
        return true;
    }
}
