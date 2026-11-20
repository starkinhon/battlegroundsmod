// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.skill.TierSpec;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

// Power tiers (fandom power scale): 0 Deus ... 10 Humano. Each tier sets health and speed, kept as saved attribute
// modifiers on the fighter (so health survives a relog) and refreshed by apply (players every tick; bosses take their
// health from their file). The moves' damage scales with the attacker's tier (damageMultiplier) and the target's Escudo
// shrinks blows from lower tiers (shield, in PowerEvents.onIncomingDamage). Against the same tier the Escudo does nothing,
// so every fighter also gets its tier's Armadura (the real armor attribute, the bar over the hotbar: stops physical
// blows like punches, less so the heavy ones) and Resistencia (a share of every blow, techniques too), like the
// Jujutsu Craft's armor and Resistance (Stark, 2026-11-09: a Tier 0 died to a Tier 0's punches in 10 s). Players and
// bosses alike (defense). Values in souls_skills/padrao.tiers.json.
public final class Tiers {
    public static final int DEUS = 0;
    public static final int HUMANO = 10;

    private static final ResourceLocation HEALTH = id("tier_health");
    private static final ResourceLocation STRENGTH = id("tier_strength");
    private static final ResourceLocation ARMOR = id("tier_armor");
    private static final ResourceLocation DEFENSE_ARMOR = id("tier_defense_armor");
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

    // The tier's name in the game's language (tier.battlegroundsmod.<n> in the lang files); the name in
    // padrao.tiers.json is only the fallback, so the HUD never shows Portuguese with the game in English.
    public static Component name(int tier) {
        var names = TierSpec.get().nomes();
        tier = clamp(tier);
        String fallback = names != null && tier < names.size() ? names.get(tier) : "Tier " + tier;
        return Component.translatableWithFallback("tier.battlegroundsmod." + tier, fallback);
    }

    private static final double[] DEFAULT_HEALTH = {1800, 1300, 940, 680, 490, 355, 255, 185, 135, 95, 65};
    // Reference health for move damage scaling so TTK stays consistent (~30-45s) across all tiers.
    private static final double DAMAGE_HEALTH_REF = 850.0;

    // The least max health a player has during a Dungeon run.
    private static final double DUNGEON_MIN_HEALTH = 50.0;

    // The curve before 2026-11-17, that the bosses' files (souls_bosses/*.json "vida") were written against.
    private static final double[] LEGACY_HEALTH = {400, 300, 220, 165, 120, 90, 66, 49, 36, 27, 20};

    public static double legacyHealth(int tier) {
        return LEGACY_HEALTH[clamp(tier)];
    }

    // Max health of this tier (Tier 10 = 65, a plain human).
    public static double health(int tier) {
        var list = TierSpec.get().vida();
        tier = clamp(tier);
        return list != null && tier < list.size() ? list.get(tier) : DEFAULT_HEALTH[tier];
    }

    // A player's max health at this tier: vanilla's 20 plus what the tier adds over a human (apply). Tier 10 = 20, Tier 8 = 90.
    public static double playerHealth(int tier) {
        return PLAYER_BASE_HEALTH + health(tier) - health(HUMANO);
    }

    private static final double PLAYER_BASE_HEALTH = 20.0;

    // The share of a move's damage this attacker deals: scales linearly with its tier's health.
    public static float damageMultiplier(LivingEntity attacker) {
        return (float) (health(of(attacker)) / DAMAGE_HEALTH_REF);
    }

    // Escudo points: 10 at Tier 0, none for a human.
    public static int shield(LivingEntity entity) {
        return HUMANO - of(entity);
    }

    // What is left of a blow of this amount from the attacker after the target's Escudo. Only a target of a higher tier
    // than the attacker shields: each point of difference keeps (1 - escudoCorte) of the blow, never less than escudoPiso
    // of it (Stark, 2026-11-19: never zero). The tier gap already weighs through health and damageMultiplier (a human vs
    // a god takes ~34x the blows of an even fight with no Escudo at all), so the Escudo is only the extra on top.
    public static float shield(LivingEntity attacker, LivingEntity target, float amount) {
        int gap = shield(target) - shield(attacker);
        if (gap <= 0 || amount <= 0) {
            return amount;
        }
        TierSpec spec = TierSpec.get();
        double floor = spec.escudoPiso() > 0 ? spec.escudoPiso() : 0.05;
        double factor = Math.max(floor, Math.pow(1 - spec.escudoCorte(), gap));
        double kept = amount * factor - target.getMaxHealth() * spec.resistenciaEscudo() * gap;
        return (float) Math.max(amount * floor, kept);
    }

    // Armadura points of this tier: armaduraPorTier per tier above human (Tier 0 = 20, a full bar).
    public static double armor(int tier) {
        return Math.max(0, TierSpec.get().armaduraPorTier() * steps(tier));
    }

    // Share of every blow this tier's Resistencia takes off: resistenciaPorTier per tier above human, at most 90%.
    public static double resistance(int tier) {
        return Mth.clamp(TierSpec.get().resistenciaPorTier() * steps(tier), 0, 0.9);
    }

    // What is left of a blow after the target's Resistencia. Void, /kill and the like go through.
    public static float resist(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        // A plain mob has no tier (and gets no attachment made here): a human.
        if (amount <= 0 || !target.hasData(ModAttachments.POWER) || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE)) {
            return amount;
        }
        return (float) (amount * (1 - resistance(of(target))));
    }

    // The tier's Armadura on the armor attribute (on top of worn armor and a boss file's own). Cheap when unchanged.
    public static void applyDefense(LivingEntity entity) {
        set(entity, Attributes.ARMOR, DEFENSE_ARMOR, armor(of(entity)), AttributeModifier.Operation.ADD_VALUE);
    }

    // Puts this fighter's tier bonuses on its attributes. Cheap when nothing changed. Health keeps its fraction when
    // the max changes, so a new tier doesn't leave the player at 20 of 400.
    public static void apply(LivingEntity entity) {
        TierSpec spec = TierSpec.get();
        int steps = steps(of(entity));
        float healthFraction = entity.getHealth() / Math.max(1, entity.getMaxHealth());
        double tierHealth = health(of(entity)) - health(HUMANO);
        // In a Dungeon run the player never has less than DUNGEON_MIN_HEALTH (Stark, 2026-11-19: 20 of life in the Tier 10
        // etapa was too little for a first fight; at least 50). Only the Tier 10 etapa is below it; out of the run it goes back.
        if (entity instanceof Player && entity.getData(ModAttachments.DUNGEON_BACKUP).active()) {
            double base = entity.getAttributeBaseValue(Attributes.MAX_HEALTH);
            tierHealth = Math.max(tierHealth, DUNGEON_MIN_HEALTH - base);
        }
        boolean healthChanged = set(entity, Attributes.MAX_HEALTH, HEALTH, tierHealth, AttributeModifier.Operation.ADD_VALUE);
        // The old strength and armor bonuses: the tier's damage and the Escudo do that now (taken off old saves).
        set(entity, Attributes.ATTACK_DAMAGE, STRENGTH, 0, AttributeModifier.Operation.ADD_VALUE);
        set(entity, Attributes.ARMOR, ARMOR, 0, AttributeModifier.Operation.ADD_VALUE);
        applyDefense(entity);
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
