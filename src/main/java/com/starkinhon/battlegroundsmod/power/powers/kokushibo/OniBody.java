package com.starkinhon.battlegroundsmod.power.powers.kokushibo;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;

// Kokushibo is an oni (Stark, 2026-11-07): a strong regeneration, cut for a while by a sword or Bills' destruction; stronger
// at night (1.5x his general stats), more at the full moon; under the sun weaker and slower to heal (he doesn't burn: that
// is left out on purpose). The stats ride on attribute modifiers on top of the tier's (health keeps its fraction when it
// changes) and the moves' damage on damageMultiplier (Combat.hurt).
public final class OniBody {
    public static final String POWER = "moon_demon";

    public static final int NORMAL = 0, NIGHT = 1, FULL_MOON = 2, DAY = 3;
    // Per phase (NORMAL, NIGHT, FULL_MOON, DAY): general stats (health, damage, armor), walking speed (kept lower so he stays
    // playable), regeneration (share of max health per second).
    private static final double[] STATS = {1.0, 1.5, 1.75, 0.85};
    private static final double[] SPEED = {1.0, 1.15, 1.25, 0.9};
    // Night and the full moon healed too fast (Stark, 2026-11-08): half of what they were (0.03 and 0.04).
    private static final double[] REGEN = {0.015, 0.015, 0.02, 0.005};
    // At night he sees in the dark (Stark, 2026-11-08): Night Vision kept topped up while it lasts, taken off at dawn.
    // Over 10 s so the vanilla flicker of a running-out Night Vision never shows.
    private static final int NIGHT_VISION = 300, NIGHT_VISION_REFRESH = 220;
    // How long a sword or a destruction hit stops him healing (ticks).
    private static final int REGEN_CUT = 100;

    private static final ResourceLocation HEALTH = id("oni_health");
    private static final ResourceLocation ARMOR = id("oni_armor");
    private static final ResourceLocation STRENGTH = id("oni_strength");
    private static final ResourceLocation SPEED_ID = id("oni_speed");

    private OniBody() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, path);
    }

    public static boolean is(LivingEntity entity) {
        return entity.hasData(ModAttachments.POWER) && POWER.equals(entity.getData(ModAttachments.POWER).power);
    }

    // Night and the full moon only where there is a sky and a moon (the overworld); the sun only weakens him out under it.
    public static int phase(LivingEntity entity) {
        Level level = entity.level();
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling() || level.dimensionType().hasFixedTime()) {
            return NORMAL;
        }
        long time = level.getDayTime() % 24000L;
        boolean night = time >= 13000L && time < 23000L;
        if (night) {
            return level.getMoonPhase() == 0 ? FULL_MOON : NIGHT;
        }
        return level.canSeeSky(BlockPos.containing(entity.getEyePosition())) ? DAY : NORMAL;
    }

    public static float damageMultiplier(LivingEntity attacker) {
        return is(attacker) ? (float) STATS[phase(attacker)] : 1f;
    }

    // Every tick, for every fighter (takes its modifiers off when it isn't Kokushibo).
    public static void tick(LivingEntity entity) {
        boolean oni = is(entity);
        int phase = oni ? phase(entity) : NORMAL;
        float fraction = entity.getHealth() / Math.max(1, entity.getMaxHealth());
        // The boss keeps the life of his file (Stark: 1000) at night too; the rest of the night's strength he gets.
        boolean healthChanged = set(entity, Attributes.MAX_HEALTH, HEALTH,
                entity instanceof net.minecraft.world.entity.player.Player ? STATS[phase] - 1 : 0);
        set(entity, Attributes.ARMOR, ARMOR, STATS[phase] - 1);
        set(entity, Attributes.ATTACK_DAMAGE, STRENGTH, STATS[phase] - 1);
        set(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED[phase] - 1);
        if (healthChanged) {
            entity.setHealth(Math.max(1, fraction * entity.getMaxHealth()));
        }
        if (!oni) {
            return;
        }
        nightVision(entity, phase == NIGHT || phase == FULL_MOON);
        PowerData data = entity.getData(ModAttachments.POWER);
        long now = entity.level().getGameTime();
        if (entity.isAlive() && now >= data.oniRegenCutUntil && entity.getHealth() < entity.getMaxHealth() && entity.tickCount % 10 == 0) {
            entity.heal((float) (entity.getMaxHealth() * REGEN[phase] / 2));
        }
        if (phase != data.oniPhaseSent) {
            boolean first = data.oniPhaseSent < 0;
            data.oniPhaseSent = phase;
            if (!first && entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message." + BattlegroundsMod.MODID + ".oni.phase." + phase), true);
            }
        }
    }

    // Ours is the ambient, particle-free one no longer than NIGHT_VISION: a potion's own Night Vision is left alone.
    private static void nightVision(LivingEntity entity, boolean night) {
        MobEffectInstance current = entity.getEffect(MobEffects.NIGHT_VISION);
        boolean ours = current != null && current.isAmbient() && !current.isVisible() && current.getDuration() <= NIGHT_VISION;
        if (night) {
            if (current == null || ours && current.getDuration() < NIGHT_VISION_REFRESH) {
                entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION, 0, true, false, false));
            }
        } else if (ours) {
            entity.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    // A hit on him: a sword (in the attacker's hand) or Bills' destruction stops the regeneration for a while.
    public static void onHurt(LivingEntity victim, LivingEntity attacker) {
        if (!is(victim) || attacker == null) {
            return;
        }
        boolean sword = attacker.getMainHandItem().getItem() instanceof SwordItem;
        boolean destruction = attacker.hasData(ModAttachments.POWER) && "destruction_god".equals(attacker.getData(ModAttachments.POWER).power);
        if (sword || destruction) {
            PowerData data = victim.getData(ModAttachments.POWER);
            data.oniRegenCutUntil = victim.level().getGameTime() + REGEN_CUT;
        }
    }

    private static boolean set(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current == null ? Math.abs(amount) < 1.0E-9 : current.amount() == amount) {
            return false;
        }
        instance.removeModifier(id);
        if (Math.abs(amount) >= 1.0E-9) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        return true;
    }
}
