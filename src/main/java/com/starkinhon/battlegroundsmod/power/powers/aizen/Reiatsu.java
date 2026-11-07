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
// The press starts it right away at raioClique; held, it grows to raioMax over carregarTicks, pressing down whoever is
// inside (can't attack or use skills, barely moves) and shaking the screens of those near. Let go (or at maxTicks) it
// blows up in its own aura, hitting harder the longer it was held and keeping them down a while longer. Drawn on the
// clients by client/fx/AizenFx (a cartoon black-violet aura with a boiling rim, the dome, pressure lines falling).
// Timeline: "carregar" while held (tremor "tremer", then "tremerForte" past half charge), "soltar" on the burst.
public final class Reiatsu {
    public static final String SKILL = AizenSpecs.REIATSU;
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int SHAKE_EVERY = 4;

    private Reiatsu() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        AizenSpecs.Reiatsu spec = AizenSpecs.Reiatsu.get(awakened);
        String power = data.power;
        int interrupts = data.interrupts;
        int grow = Math.max(1, spec.carregarTicks());
        int least = Math.max(1, spec.cliqueTicks());
        int most = Math.max(least + 1, spec.maxTicks());
        // Bosses don't hold keys: a quick one now and then, a long one other times.
        int bossHold = RANDOM.nextBoolean() ? least : least + 20 + RANDOM.nextInt(grow);
        data.busyTicks = least + 1;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Function<String, Vec3> where = onde -> Combat.center(caster);
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where, List.of());

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts) {
                AizenNet.send(level, AizenNet.REIATSU_BURST, caster.getId(), caster.position(), Vec3.ZERO, 0, 0);
                stopPose(caster);
                return true;
            }
            boolean held = caster instanceof Player ? com.starkinhon.battlegroundsmod.combat.Stamina.holding(caster) : age < bossHold;
            float charge = Math.min(1, age / (float) grow);
            if (held) {
                com.starkinhon.battlegroundsmod.combat.Charge.report(caster, charge);
            }
            float radius = (float) Mth.lerp(smooth(charge), spec.raioClique(), spec.raioMax());
            float strength = 0.3f + 0.7f * smooth(charge);
            now.busyTicks = Math.max(now.busyTicks, 2);
            AizenNet.send(level, AizenNet.REIATSU_HELD, caster.getId(), caster.position(), Vec3.ZERO, radius, strength);
            for (LivingEntity target : Combat.inRadius(level, caster.position(), radius, caster)) {
                crush(target, 3, strength);
            }
            if (age % 10 == 0) {
                SkillSounds.play(level, spec.sons(), "carregar", 0, 0, where, List.of());
            }
            if (age % SHAKE_EVERY == 0) {
                Fx.effects(level, SKILL, awakened, charge < 0.5f ? "tremer" : "tremerForte", caster.position(), new Vec3(0, 1, 0));
            }
            if ((held || age < least) && age < most) {
                return false;
            }
            burst(caster, level, spec, awakened, where, radius, charge);
            return true;
        });
        return true;
    }

    private static void burst(LivingEntity caster, ServerLevel level, AizenSpecs.Reiatsu spec, boolean awakened, Function<String, Vec3> where,
            float radius, float charge) {
        AizenNet.send(level, AizenNet.REIATSU_BURST, caster.getId(), caster.position(), Vec3.ZERO, radius, 1);
        SkillSounds.play(level, spec.sons(), "soltar", 0, 0, where, List.of());
        Fx.effects(level, SKILL, awakened, "soltar", caster.position().add(0, 0.05, 0), new Vec3(0, 1, 0));
        float damage = Mth.lerp(charge, spec.danoMin(), spec.danoMax());
        for (LivingEntity target : Combat.inRadius(level, caster.position(), radius * 1.1, caster)) {
            Combat.hurtMagic(caster, target, damage);
            crush(target, Math.round(Mth.lerp(charge, spec.ajoelharCliqueTicks(), spec.ajoelharTicks())), 1);
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
