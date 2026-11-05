package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.AizenSpecs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Reiatsu Esmagadora, Aizen's C (aizen.reiatsu.json): his spiritual pressure crushes everyone around him to their knees.
// A tap is a quick small pulse (raioClique). Held, the pressure grows from raioClique to raioMax: whoever is inside is
// pressed down (can't attack or use skills, barely moves); let go and it bursts, hitting them harder the longer it was
// held and keeping them down a while longer. Drawn on the clients by client/fx/AizenFx (black flames with violet
// edges, a dark column, a dome and pressure lines falling).
// Timeline: tap "pulso"; held "carregar" while held, "soltar" on the burst.
public final class Reiatsu {
    public static final String SKILL = AizenSpecs.REIATSU;
    private static final RandomSource RANDOM = RandomSource.create();

    private Reiatsu() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        AizenSpecs.Reiatsu spec = AizenSpecs.Reiatsu.get(awakened);
        String power = data.power;
        int interrupts = data.interrupts;
        int tap = Math.max(1, spec.cliqueTicks());
        int grow = Math.max(1, spec.carregarTicks());
        int most = Math.max(tap + 1, spec.maxTicks());
        // Bosses don't hold keys: a pulse now and then, a long press other times.
        int bossHold = RANDOM.nextBoolean() ? 0 : tap + 20 + RANDOM.nextInt(grow);
        data.busyTicks = tap + 1;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Function<String, Vec3> where = onde -> Combat.center(caster);
        float[] radius = {(float) spec.raioClique()};

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts) {
                AizenNet.send(level, AizenNet.REIATSU_BURST, caster.getId(), caster.position(), Vec3.ZERO, 0, 0);
                stopPose(caster);
                return true;
            }
            boolean held = caster instanceof Player ? now.abilityHeld : age < bossHold;
            if (age < tap) {
                now.busyTicks = Math.max(now.busyTicks, 2);
                if (held || age == 0) {
                    return false;
                }
                pulse(caster, level, spec, awakened, where);
                return true;
            }
            if (age == tap) {
                SkillSounds.play(level, spec.sons(), "carregar", 0, most - tap, where, List.of());
            }
            float charge = Math.min(1, (age - tap) / (float) grow);
            radius[0] = (float) Mth.lerp(smooth(charge), spec.raioClique(), spec.raioMax());
            float strength = 0.3f + 0.7f * smooth(charge);
            now.busyTicks = Math.max(now.busyTicks, 2);
            AizenNet.send(level, AizenNet.REIATSU_HELD, caster.getId(), caster.position(), Vec3.ZERO, radius[0], strength);
            for (LivingEntity target : Combat.inRadius(level, caster.position(), radius[0], caster)) {
                crush(target, 3, strength);
            }
            if (held && age < most) {
                return false;
            }
            burst(caster, level, spec, awakened, where, radius[0], charge);
            return true;
        });
        return true;
    }

    private static void pulse(LivingEntity caster, ServerLevel level, AizenSpecs.Reiatsu spec, boolean awakened, Function<String, Vec3> where) {
        float r = (float) spec.raioClique();
        AizenNet.send(level, AizenNet.REIATSU_CLICK, caster.getId(), caster.position(), Vec3.ZERO, r, 0.25f);
        SkillSounds.play(level, spec.sons(), "pulso", 0, 0, where, List.of());
        Fx.effects(level, SKILL, awakened, "pulso", caster.position().add(0, 0.05, 0), new Vec3(0, 1, 0));
        for (LivingEntity target : Combat.inRadius(level, caster.position(), r, caster)) {
            Combat.hurtMagic(caster, target, spec.danoClique());
            crush(target, spec.ajoelharCliqueTicks(), 0.5f);
        }
        Tasks.later(Math.max(10, spec.ajoelharCliqueTicks()), () -> stopPose(caster));
    }

    private static void burst(LivingEntity caster, ServerLevel level, AizenSpecs.Reiatsu spec, boolean awakened, Function<String, Vec3> where,
            float radius, float charge) {
        AizenNet.send(level, AizenNet.REIATSU_BURST, caster.getId(), caster.position(), Vec3.ZERO, radius, 1);
        SkillSounds.play(level, spec.sons(), "soltar", 0, 0, where, List.of());
        Fx.effects(level, SKILL, awakened, "soltar", caster.position().add(0, 0.05, 0), new Vec3(0, 1, 0));
        float damage = Mth.lerp(charge, spec.danoMin(), spec.danoMax());
        for (LivingEntity target : Combat.inRadius(level, caster.position(), radius * 1.1, caster)) {
            Combat.hurtMagic(caster, target, damage);
            crush(target, spec.ajoelharTicks(), 1);
        }
        Tasks.later(20, () -> stopPose(caster));
    }

    // Pressed to the ground: can't attack, barely moves, pulled down.
    private static void crush(LivingEntity target, int ticks, float strength) {
        Stun.apply(target, ticks);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 2, strength > 0.6f ? 4 : 2, false, false));
        Vec3 v = target.getDeltaMovement();
        target.setDeltaMovement(v.x * 0.5, Math.min(v.y, -0.3 * strength), v.z * 0.5);
        target.hurtMarked = true;
    }

    private static void stopPose(LivingEntity caster) {
        if (caster.isAlive()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
