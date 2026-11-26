// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.DefesaSpec;
import com.starkinhon.battlegroundsmod.skill.StaminaSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The Agarrão, fighting game style (Stark 2026-11-22): guard key + attack. Both hands reach out; whoever is caught in
// front is held a moment and shoved away. A guard doesn't stop it: it BREAKS the guard (stunned, open). It's the answer
// to someone who only guards, and it loses to a hit: a real stun before the hands close cancels it, and a miss leaves
// the grabber stuck in place for a moment. Values in padrao.defesa.json "agarrao"; pose soco.agarrao (tools/poses).
// The techniques' own grabs (Makima's chain, Shigaraki's touch...) are another thing and still bounce off a guard
// (Defense.blockGrab).
public final class Grab {
    private static final String POSE = "soco.agarrao";
    // Taller than this many times the grabber can't be lifted (a giant, a big mob).
    private static final double MAX_SIZE = 1.6;
    private static final Map<LivingEntity, Long> READY_AT = new HashMap<>();

    private Grab() {
    }

    public static boolean start(LivingEntity attacker) {
        DefesaSpec.Agarrao g = DefesaSpec.get().agarrao();
        if (g == null || !attacker.isAlive()) {
            return false;
        }
        PowerData data = attacker.getData(ModAttachments.POWER);
        long now = attacker.level().getGameTime();
        if (data.busyTicks > 0 || Freeze.isFrozen(attacker) || !Stun.canAttack(attacker) || READY_AT.getOrDefault(attacker, 0L) > now) {
            return false;
        }
        ServerLevel level = Combat.level(attacker);
        Defense.stop(attacker);
        READY_AT.put(attacker, now + Math.max(g.contatoTicks() + 1, g.recargaTicks()));
        data.busyTicks = g.contatoTicks() + 1;
        CombatMode.enter(attacker);
        anim(attacker, POSE);
        Fx.sound(level, attacker.position(), SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.7f);
        int contact = Math.max(1, g.contatoTicks());
        int hold = Math.max(1, g.seguraTicks());
        LivingEntity[] caught = {null};
        Runnable[] release = {null};
        Runnable letGo = () -> {
            if (release[0] != null) {
                release[0].run();
                release[0] = null;
            }
        };
        Tasks.add(age -> {
            if (!attacker.isAlive() || attacker.isRemoved()) {
                letGo.run();
                return true;
            }
            if (age < contact) {
                // A hit that really stuns before the hands close wins (strike beats throw).
                if (Stun.real(attacker) || Freeze.isFrozen(attacker)) {
                    anim(attacker, "");
                    return true;
                }
                return false;
            }
            if (age == contact) {
                LivingEntity target = find(attacker, g);
                if (target == null) {
                    // Missed: stuck for a moment, open to a punish.
                    data.busyTicks = Math.max(data.busyTicks, g.erroTicks());
                    Fx.sound(level, attacker.position(), SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 0.8f);
                    return true;
                }
                // A standing Gunbai takes the grab and gives out; the guard behind it holds, and the grab is spent.
                if (Defense.gunbaiTakesBreak(target, attacker)) {
                    data.busyTicks = Math.max(data.busyTicks, g.erroTicks());
                    return true;
                }
                caught[0] = target;
                DefesaSpec spec = DefesaSpec.get();
                // A guard from the front breaks (its stun is the break's); someone who wasn't guarding is just stunned.
                if (!Defense.breakGuard(target, attacker)) {
                    Defense.stop(target);
                    Stun.apply(target, Math.max(Stun.INTERRUPT_TICKS, g.atordoaTicks()));
                } else {
                    Stun.apply(target, Math.max(spec.quebraStunTicks(), hold + Stun.INTERRUPT_TICKS));
                }
                data.busyTicks = hold + 6;
                Vec3 spot = holdSpot(attacker, target);
                release[0] = Freeze.hold(target, hold + 1, a -> spot);
                Defense.effects(target, attacker, "agarrar");
                Melee.shake(target, 0.3f, 4);
                return false;
            }
            LivingEntity target = caught[0];
            if (target == null || !target.isAlive() || target.isRemoved()) {
                letGo.run();
                return true;
            }
            if (Stun.real(attacker) || Freeze.isFrozen(attacker)) {
                // Hit hard while holding: lets go.
                letGo.run();
                return true;
            }
            if (age < contact + hold) {
                return false;
            }
            letGo.run();
            Vec3 look = Combat.flatLook(attacker);
            if (Combat.hurtBasic(attacker, target, (float) g.dano() * Melee.multiplier(attacker))) {
                Melee.hitFx(attacker, target, "final");
                Stamina.gain(attacker, StaminaSpec.get().ganhoAcertoPct());
                Melee.shake(target, 0.6f, 8);
            }
            Combat.push(target, look, g.empurrao(), g.subida());
            Defense.effects(target, attacker, "arremesso");
            return true;
        }, letGo);
        return true;
    }

    // Whoever the hands close on: the one aimed at, or the closest in front, within reach. Not the untouchable, not a
    // boss, nobody too big to lift.
    private static LivingEntity find(LivingEntity attacker, DefesaSpec.Agarrao g) {
        double reach = g.alcance() * Melee.size(attacker);
        LivingEntity target = Combat.raycastTarget(attacker, reach);
        if (target == null) {
            Vec3 eye = attacker.getEyePosition();
            Vec3 look = attacker.getLookAngle();
            target = Combat.inCone(attacker, reach, g.cone()).stream()
                    .filter(attacker::hasLineOfSight)
                    .max(java.util.Comparator.comparingDouble(e -> Combat.center(e).subtract(eye).normalize().dot(look) - e.distanceTo(attacker) * 0.1))
                    .orElse(null);
        }
        if (target == null || !target.isAlive() || target.isSpectator() || Intangible.untouchableBy(target, attacker)
                || com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon.hasState(target,
                        com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.KAMUI_COUNTER)
                || target instanceof SoulBoss boss && boss.isBoss() || target.getBbHeight() > attacker.getBbHeight() * MAX_SIZE) {
            return null;
        }
        return target;
    }

    // Right in front of the grabber's chest, on the ground they stand on; where it would be inside a wall, where it is.
    private static Vec3 holdSpot(LivingEntity attacker, LivingEntity target) {
        double gap = attacker.getBbWidth() / 2 + target.getBbWidth() / 2 + 0.3;
        Vec3 spot = attacker.position().add(Combat.flatLook(attacker).scale(gap));
        boolean free = attacker.level().noCollision(target, target.getBoundingBox().move(spot.subtract(target.position())));
        return free ? spot : target.position();
    }

    private static void anim(LivingEntity entity, String move) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.PlaySkillAnimation(entity.getId(), move, false));
    }

    public static void prune() {
        READY_AT.keySet().removeIf(LivingEntity::isRemoved);
    }

    public static void clear() {
        READY_AT.clear();
    }
}
