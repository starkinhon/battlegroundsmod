package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.StaminaSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

// Attack stamina (values in padrao.estamina.json, see StaminaSpec). Server side, players only: bosses and dummies
// never spend it, but stunnable works on anyone.
// Skills spend it, held charged skills spend more while held, the base fighting gives it back. At zero the player is
// exhausted for a while: no skills, only punches, and stunnable (the future skills that need a stunned target land).
public final class Stamina {
    private Stamina() {
    }

    // Whether the stun-only skills can be used on target: under a real stun (Stun.real, never the punches' short one),
    // or exhausted.
    public static boolean stunnable(LivingEntity target) {
        return Stun.real(target) || exhausted(target);
    }

    public static boolean exhausted(LivingEntity entity) {
        return entity.getData(ModAttachments.POWER).exhaustedUntil > entity.level().getGameTime();
    }

    public static double max(LivingEntity entity) {
        return StaminaSpec.get().max(Tiers.of(entity));
    }

    public static float get(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        if (data.stamina < 0) {
            data.stamina = (float) max(entity);
        }
        return data.stamina;
    }

    private static boolean uses(LivingEntity entity) {
        return entity instanceof ServerPlayer player && CombatMode.fighter(player);
    }

    private static boolean free(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        return data.noCooldown || entity instanceof ServerPlayer player && player.isCreative() && !StaminaSpec.get().gastaNoCriativo();
    }

    // A skill of slot was just used: pays its cost and opens the budget a held charge may still drain.
    public static void spend(LivingEntity entity, boolean ult) {
        if (!uses(entity) || free(entity)) {
            return;
        }
        StaminaSpec spec = StaminaSpec.get();
        take(entity, (float) (ult ? spec.custoUlt() : spec.custoSkill()));
        entity.getData(ModAttachments.POWER).chargeLeft = (float) spec.custoCarregadoMax();
    }

    // Called every tick by a charged skill while the player holds its key: returns whether the key counts as still held.
    // Each held tick drains a bit, up to the use's budget; running out lets the charge go.
    public static boolean holding(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        if (!data.abilityHeld) {
            return false;
        }
        if (!uses(entity) || free(entity) || data.chargeLeft <= 0) {
            return true;
        }
        float drain = (float) Math.min(data.chargeLeft, StaminaSpec.get().custoCarregandoPorSegundo() / 20.0);
        data.chargeLeft -= drain;
        take(entity, drain);
        return !exhausted(entity);
    }

    // Whether entity holds off the Gojo's Domain: a player with a power and stamina left (not exhausted).
    public static boolean resists(LivingEntity entity) {
        return uses(entity) && !exhausted(entity) && get(entity) > 0;
    }

    // Drains percent of the max no matter the creative or no-cooldown freebies (the Domain's information overload).
    // Running out exhausts them as usual.
    public static void drain(LivingEntity entity, double percent) {
        if (!uses(entity) || percent <= 0) {
            return;
        }
        take(entity, (float) (max(entity) * percent / 100.0));
    }

    // Whether a skill that runs on stamina while it lasts (Shikamaru's shadows) really drains entity: a player with a
    // power, outside the creative and no-cooldown freebies. The others run on a fixed budget instead.
    public static boolean drains(LivingEntity entity) {
        return uses(entity) && !free(entity);
    }

    // Flying (universe/Flight): takes percent of the max but never below floorPct, so flying never exhausts anyone
    // (they glide down tired instead). Nothing while exhausted.
    public static void tire(LivingEntity entity, double percent, double floorPct) {
        if (!drains(entity) || exhausted(entity) || percent <= 0) {
            return;
        }
        PowerData data = entity.getData(ModAttachments.POWER);
        double max = max(entity);
        double floor = max * floorPct / 100.0;
        float now = get(entity);
        if (now > floor) {
            data.stamina = (float) Math.max(floor, now - max * percent / 100.0);
        }
    }

    // Gives back percent of the max (a punch, a hit, a dash, a base attack). Nothing while exhausted.
    public static void gain(LivingEntity entity, double percent) {
        if (!uses(entity) || exhausted(entity) || percent <= 0) {
            return;
        }
        PowerData data = entity.getData(ModAttachments.POWER);
        double max = max(entity);
        data.stamina = (float) Math.min(max, get(entity) + max * percent / 100.0);
    }

    private static void take(LivingEntity entity, float amount) {
        PowerData data = entity.getData(ModAttachments.POWER);
        if (exhausted(entity)) {
            return;
        }
        data.stamina = Math.max(0, get(entity) - amount);
        if (data.stamina <= 0) {
            exhaust(entity);
        }
    }

    // Out of stamina: exhausted for vulneravelSegundos. Also used by /soul estamina zerar.
    public static void exhaust(LivingEntity entity) {
        StaminaSpec spec = StaminaSpec.get();
        PowerData data = entity.getData(ModAttachments.POWER);
        int ticks = Math.max(1, spec.vulneravelSegundos() * 20);
        data.stamina = 0;
        data.chargeLeft = 0;
        data.exhaustedUntil = entity.level().getGameTime() + ticks;
        entity.addEffect(new MobEffectInstance(ModEffects.EXAUSTO, ticks, 0, false, false, true));
        Fx.sound(Combat.level(entity), entity.position(), SoundEvents.SHIELD_BREAK, 1f, 0.6f);
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.stamina_out", spec.vulneravelSegundos())
                    .withStyle(s -> s.withColor(0xFF4444)), true);
        }
    }

    // Back to full, not exhausted (/soul estamina cheia).
    public static void refill(LivingEntity entity) {
        PowerData data = entity.getData(ModAttachments.POWER);
        data.stamina = (float) max(entity);
        data.exhaustedUntil = 0;
        data.chargeLeft = 0;
        entity.removeEffect(ModEffects.EXAUSTO);
    }

    // Every tick on the server: the exhaustion fills the bar back up to voltaComPct by its end; out of combat it
    // fills up by itself.
    public static void tick(LivingEntity entity) {
        if (!uses(entity)) {
            return;
        }
        StaminaSpec spec = StaminaSpec.get();
        PowerData data = entity.getData(ModAttachments.POWER);
        double max = max(entity);
        float now = get(entity);
        if (now > max) {
            data.stamina = (float) max;
        }
        long left = data.exhaustedUntil - entity.level().getGameTime();
        if (data.exhaustedUntil > 0 && left <= 0) {
            data.exhaustedUntil = 0;
            data.stamina = (float) Math.max(data.stamina, max * spec.voltaComPct() / 100.0);
            entity.removeEffect(ModEffects.EXAUSTO);
        } else if (left > 0) {
            int total = Math.max(1, spec.vulneravelSegundos() * 20);
            data.stamina = (float) (max * spec.voltaComPct() / 100.0 * (1 - Math.min(1, left / (double) total)));
        } else if (!CombatMode.in(entity) && spec.regenForaDeCombatePorSegundo() > 0
                && !(entity instanceof net.minecraft.world.entity.player.Player player && com.starkinhon.battlegroundsmod.universe.Flight.paying(player))) {
            // (moving through the air pays stamina, so it does not fill back up then; floating still in the air does)
            data.stamina = (float) Math.min(max, now + max * spec.regenForaDeCombatePorSegundo() / 100.0 / 20.0);
        }
        if (!data.abilityHeld) {
            data.chargeLeft = 0;
        }
    }

    public static void sync(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        long left = Math.max(0, data.exhaustedUntil - player.level().getGameTime());
        PacketDistributor.sendToPlayer(player, new ModPayloads.SyncStamina(get(player), (float) max(player), (int) left));
    }
}
