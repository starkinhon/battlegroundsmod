// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
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

// Reiatsu, Aizen's Shift+B (aizen.reiatsu.json; forja/aizen-kit, the reiatsu scene): his spiritual pressure crushes
// everyone around him, first to their knees, then face down, and the whole ground sinks under it (a step at a third of
// the charge, another near the end); let go and it explodes into a crater.
// The press starts it right away at raioClique; held, it grows to raioMax over carregarTicks, pressing down whoever is
// inside (can't attack or use skills, barely moves) and shaking the screens of those near. Let go (or at maxTicks) it
// blows up in its own aura, hitting harder the longer it was held and keeping them down a while longer. Drawn on the
// clients by client/fx/AizenKit (the page's black flame aura with a violet edge, the dome of pressure and its falling
// streaks, the dark violet blast dome).
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
        Vec3 feet = AizenHit.ground(level, caster.position());
        int[] sunk = {0};

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts) {
                AizenNet.send(level, AizenNet.PRESS_BOOM, caster.getId(), caster.position(), Vec3.ZERO, 0, 0);
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
            AizenNet.send(level, AizenNet.PRESS, caster.getId(), caster.position(), Vec3.ZERO, radius, strength);
            // The ground sinks under the pressure, in steps (no block flies: everything is pushed down).
            if (sunk[0] == 0 && charge >= 0.3f || sunk[0] == 1 && charge >= 0.8f) {
                double r = sunk[0] == 0 ? radius * 1.05 : radius;
                Ruin.bowl(level, feet.x, feet.z, r, sunk[0] == 0 ? 1 : 3, 6, 0, Ruin.opts().rim(0.05).seed(11 + sunk[0]));
                RuinNet.tremor(level, feet, 0.5, 30, r * 1.5);
                RuinNet.quake(level, feet, 0.6, false, 0xC8B0FF);
                sunk[0]++;
            }
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
        AizenNet.send(level, AizenNet.PRESS_BOOM, caster.getId(), caster.position(), Vec3.ZERO, radius, charge);
        SkillSounds.play(level, spec.sons(), "soltar", 0, 0, where, List.of());
        Fx.effects(level, SKILL, awakened, "soltar", caster.position().add(0, 0.05, 0), new Vec3(0, 1, 0));
        float damage = Mth.lerp(charge, spec.danoMin(), spec.danoMax());
        for (LivingEntity target : Combat.inRadius(level, caster.position(), radius * 1.1, caster)) {
            Combat.hurtMagic(caster, target, damage);
            crush(target, Math.round(Mth.lerp(charge, spec.ajoelharCliqueTicks(), spec.ajoelharTicks())), 1);
            Combat.pushFrom(target, caster.position(), 1.2 + 1.4 * charge, 0.6 + 0.5 * charge);
        }
        // The blast's crater: a divine soul's (22 blocks deep 9 at full charge).
        AizenHit.crater(level, AizenHit.ground(level, caster.position()), Math.max(4, radius * 0.88), 2 + 7 * charge, 10, 0, true, 0xC8B0FF);
        // The pose waits at its "soltar" mark while it grows; let go, the arms fling out with the burst.
        if (caster.isAlive()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                    new ModPayloads.PlaySkillAnimation(caster.getId(), ModPayloads.PlaySkillAnimation.RELEASE, false));
        }
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
