// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.skill.DefesaSpec;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The guard (held defense key, combat mode only). See DefesaSpec for the rules; values in padrao.defesa.json.
// Server side: guards live here by entity, with the game time their parry window started.
// Fighting game style (Stark 2026-11-22): blocked hits leave chip damage, a parry at the right time, a guard meter that
// blocked hits and holding wear down (empty = guard break), the Agarrão (combat/Grab) and the strong attack break it,
// and an ult gets partly through.
public final class Defense {
    public enum Result { NONE, BLOCK, PARRY }

    private static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "defesa_speed");
    private static final Map<LivingEntity, Long> GUARDING = new HashMap<>();
    private static final Map<LivingEntity, Long> BROKEN_UNTIL = new HashMap<>();
    // The last game time each entity blocked a hit, so a punch knows not to stun.
    private static final Map<LivingEntity, Long> BLOCKED_AT = new HashMap<>();
    // Whether that last blocked hit was an ult's technique (its stun still gets through, see absorbsStun).
    private static final Map<LivingEntity, Boolean> BLOCKED_ULT = new HashMap<>();
    // A technique's stun landing this many ticks after the guard blocked its hit does nothing (absorbsStun).
    private static final int STUN_SHIELD_TICKS = 2;
    // Players holding the guard key right now (the Guard packet). When something drops their guard (a stun, a hold, the
    // Agarrão they threw) and it ends while the key is still down, the guard comes back up by itself, without a parry
    // window (Stark 2026-11-25: the pose dropped, he thought the guard was gone, let go and pressed again).
    private static final Set<LivingEntity> HELD = new HashSet<>();
    // When the guard really went up (GUARDING keeps the parry window's start), and the last raise (parry spam).
    private static final Map<LivingEntity, Long> RAISED_AT = new HashMap<>();
    private static final Map<LivingEntity, Long> LAST_RAISE = new HashMap<>();
    // The guard meter (missing = full), when it was last used (regen waits), the last flat cost, and what the player's
    // screen shows (GuardBar).
    private static final Map<LivingEntity, Double> METER = new HashMap<>();
    private static final Map<LivingEntity, Long> USED_AT = new HashMap<>();
    private static final Map<LivingEntity, Long> FLAT_COST_AT = new HashMap<>();
    private static final Map<LivingEntity, Float> SENT = new HashMap<>();
    // Rapid hits (beams, barrages) pay the flat cost at most once every this many ticks; the proportional one always.
    private static final int FLAT_COST_EVERY = 4;
    // The Gunbai (Madara's war fan, Stark 2026-11-22): a second guard in front of the fighter's own. Held in either hand
    // with the guard up, it takes the wear first (same meter size, same costs, so it lasts as many hits); when it gives
    // out the guard stays up and the fan rests for GUNBAI_RECARGA_TICKS. FAN = its meter (missing = full), FAN_UNTIL =
    // the end of its rest.
    public static final int GUNBAI_RECARGA_TICKS = 60 * 20;
    private static final Map<LivingEntity, Double> FAN = new HashMap<>();
    private static final Map<LivingEntity, Long> FAN_UNTIL = new HashMap<>();
    private static final Map<LivingEntity, Float> FAN_SENT = new HashMap<>();

    private Defense() {
    }

    public static boolean isGuarding(Entity entity) {
        return entity instanceof LivingEntity living && GUARDING.containsKey(living);
    }

    public static void setGuard(LivingEntity entity, boolean down) {
        if (!down) {
            HELD.remove(entity);
            stop(entity);
            return;
        }
        HELD.add(entity);
        raise(entity, true);
    }

    // pressed: the key just went down (a parry window, unless it's spam); otherwise the guard comes back up on its own
    // with the key still held, never with a parry window.
    private static void raise(LivingEntity entity, boolean pressed) {
        long now = entity.level().getGameTime();
        if (isGuarding(entity) || !CombatMode.in(entity) || Stun.is(entity) || Freeze.isFrozen(entity) || !entity.isAlive()
                || entity.getData(ModAttachments.POWER).busyTicks > 0 || BROKEN_UNTIL.getOrDefault(entity, 0L) > now) {
            return;
        }
        DefesaSpec spec = DefesaSpec.get();
        // Tapping the key again right away gives no new parry window (the window counts as already over).
        boolean spam = !pressed || now - LAST_RAISE.getOrDefault(entity, -1000L) < spec.parryRecargaTicks();
        GUARDING.put(entity, spam ? now - spec.parryTicks() - 1 : now);
        RAISED_AT.put(entity, now);
        if (pressed) {
            LAST_RAISE.put(entity, now);
        }
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
            speed.addTransientModifier(new AttributeModifier(SLOW, -spec.lentidao(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        CombatMode.enter(entity);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.GuardPose(entity.getId(), true));
    }

    public static void stop(LivingEntity entity) {
        if (GUARDING.remove(entity) == null) {
            return;
        }
        RAISED_AT.remove(entity);
        USED_AT.put(entity, entity.level().getGameTime());
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.GuardPose(entity.getId(), false));
    }

    // Every server tick: the guard drops when the fighter can't keep it up. Held, it wears the meter down (empty = it
    // breaks); down, the meter fills back after a moment.
    public static void tick(LivingEntity entity) {
        if (isGuarding(entity) && (!entity.isAlive() || Stun.is(entity) || Freeze.isFrozen(entity) || !CombatMode.in(entity))) {
            stop(entity);
        } else if (isGuarding(entity)) {
            CombatMode.enter(entity);
        } else if (HELD.contains(entity)) {
            // the key is still down: back up as soon as the fighter can (raise checks stun, hold, busy, broken guard)
            raise(entity, false);
        }
        DefesaSpec.Resistencia r = DefesaSpec.get().resistencia();
        if (r != null && r.maximo() > 0) {
            long now = entity.level().getGameTime();
            if (isGuarding(entity)) {
                USED_AT.put(entity, now);
                if (wearGuard(entity, null, r.drenoPorSegundo() / 20.0, r)) {
                    breakNow(entity, null);
                }
            } else if (FAN.containsKey(entity) && now - USED_AT.getOrDefault(entity, 0L) >= r.atrasoRegenTicks()) {
                double next = FAN.get(entity) + r.regenPorSegundo() / 20.0;
                if (next >= r.maximo()) {
                    FAN.remove(entity);
                } else {
                    FAN.put(entity, next);
                }
            }
            if (!isGuarding(entity) && METER.containsKey(entity) && BROKEN_UNTIL.getOrDefault(entity, 0L) <= now
                    && now - USED_AT.getOrDefault(entity, 0L) >= r.atrasoRegenTicks()) {
                double next = METER.get(entity) + r.regenPorSegundo() / 20.0;
                if (next >= r.maximo()) {
                    METER.remove(entity);
                } else {
                    METER.put(entity, next);
                }
            }
        }
        sync(entity);
    }

    // The guard meter, 0..1 (1 = full or no meter).
    public static double meter(LivingEntity entity) {
        DefesaSpec.Resistencia r = DefesaSpec.get().resistencia();
        if (r == null || r.maximo() <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, METER.getOrDefault(entity, r.maximo()) / r.maximo()));
    }

    // How long the guard has been up (0 if it's down): the bosses grab someone who only guards (Melee.normalHit).
    public static int heldTicks(Entity entity) {
        Long at = entity instanceof LivingEntity living ? RAISED_AT.get(living) : null;
        return at == null ? 0 : (int) (entity.level().getGameTime() - at);
    }

    // Whether the Gunbai stands in front of this fighter's guard: held in either hand and not resting.
    public static boolean gunbaiUp(LivingEntity entity) {
        return holdsGunbai(entity) && FAN_UNTIL.getOrDefault(entity, 0L) <= entity.level().getGameTime();
    }

    private static boolean holdsGunbai(LivingEntity entity) {
        net.minecraft.world.item.Item fan = com.starkinhon.battlegroundsmod.ModItems.GUNBAI.get();
        return entity.getMainHandItem().is(fan) || entity.getOffhandItem().is(fan);
    }

    // The Gunbai's meter, 0..1 (1 = full).
    public static double gunbaiMeter(LivingEntity entity) {
        DefesaSpec.Resistencia r = DefesaSpec.get().resistencia();
        if (r == null || r.maximo() <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, FAN.getOrDefault(entity, r.maximo()) / r.maximo()));
    }

    // Wear on the guard: the Gunbai takes it while it stands (and gives out instead of the guard), otherwise the guard's
    // own meter. True when the guard's own meter ran out.
    private static boolean wearGuard(LivingEntity entity, LivingEntity attacker, double amount, DefesaSpec.Resistencia r) {
        if (gunbaiUp(entity)) {
            double next = FAN.getOrDefault(entity, r.maximo()) - amount;
            if (next <= 0) {
                gunbaiBreak(entity, attacker);
            } else {
                FAN.put(entity, next);
            }
            return false;
        }
        return wear(entity, amount, r);
    }

    // The Gunbai gives out: the guard stays up, the fan rests for GUNBAI_RECARGA_TICKS (shown as the item's cooldown).
    private static void gunbaiBreak(LivingEntity entity, LivingEntity attacker) {
        FAN.remove(entity);
        FAN_UNTIL.put(entity, entity.level().getGameTime() + GUNBAI_RECARGA_TICKS);
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            player.getCooldowns().addCooldown(com.starkinhon.battlegroundsmod.ModItems.GUNBAI.get(), GUNBAI_RECARGA_TICKS);
        }
        reaction(entity, 0);
        effects(entity, attacker, "quebra");
        sync(entity);
    }

    // Each hit the Gunbai blocks steals GUNBAI_ROUBO_PCT of the max stamina from whoever struck (Stark's passive).
    private static final double GUNBAI_ROUBO_PCT = 1.0;

    private static void gunbaiSteal(LivingEntity holder, LivingEntity attacker) {
        Stamina.gain(holder, GUNBAI_ROUBO_PCT);
        Stamina.drain(attacker, GUNBAI_ROUBO_PCT);
        if (holder instanceof ServerPlayer player) {
            Stamina.sync(player);
        }
        if (attacker instanceof ServerPlayer player) {
            Stamina.sync(player);
        }
    }

    // A guard break (strong attack, Agarrão) against a standing Gunbai: the fan takes it and gives out, the guard stays.
    // True when it did (the break didn't reach the guard).
    public static boolean gunbaiTakesBreak(LivingEntity target, LivingEntity attacker) {
        if (!gunbaiUp(target) || check(target, attacker) == Result.NONE) {
            return false;
        }
        BLOCKED_AT.put(target, target.level().getGameTime());
        gunbaiBreak(target, attacker);
        return true;
    }

    // Takes amount off the meter; true when it ran out.
    private static boolean wear(LivingEntity entity, double amount, DefesaSpec.Resistencia r) {
        double next = METER.getOrDefault(entity, r.maximo()) - amount;
        METER.put(entity, Math.max(0, next));
        return next <= 0;
    }

    // The player's own guard meter on their screen (client/GuardBar): sent when it moves enough or breaks/recovers.
    private static void sync(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        syncFan(player);
        boolean broken = BROKEN_UNTIL.getOrDefault(entity, 0L) > entity.level().getGameTime();
        float value = broken ? -1 : (float) meter(entity);
        Float sent = SENT.get(entity);
        if (sent == null && value >= 1) {
            return;
        }
        if (sent == null || Math.abs(sent - value) >= 0.01f || (value >= 1) != (sent >= 1) || (value < 0) != (sent < 0)) {
            SENT.put(entity, value);
            PacketDistributor.sendToPlayer(player, new ModPayloads.GuardMeter(value));
            if (value >= 1) {
                SENT.remove(entity);
            }
        }
    }

    // The Gunbai's meter on the holder's screen (GuardBar draws it in front of the guard's): -2 not holding it, -1
    // resting, else 0..1.
    private static void syncFan(ServerPlayer player) {
        float value = !holdsGunbai(player) ? -2 : gunbaiUp(player) ? (float) gunbaiMeter(player) : -1;
        Float sent = FAN_SENT.get(player);
        if (sent == null ? value != -2 : Math.abs(sent - value) >= 0.01f || (value >= 1) != (sent >= 1)) {
            FAN_SENT.put(player, value);
            PacketDistributor.sendToPlayer(player, new ModPayloads.GunbaiMeter(value));
            if (value == -2) {
                FAN_SENT.remove(player);
            }
        }
    }

    // Whether this hit is an ult's technique: the attacker's last cast was the ult, recent enough, and the damage comes
    // from a technique (not the punches thrown meanwhile).
    private static boolean ultHit(LivingEntity attacker, DefesaSpec spec) {
        if (spec.ult() == null || !Combat.technique() || !attacker.hasData(ModAttachments.POWER)) {
            return false;
        }
        var data = attacker.getData(ModAttachments.POWER);
        return com.starkinhon.battlegroundsmod.power.PowerData.isUlt(data.lastCastSlot)
                && attacker.level().getGameTime() - data.lastCastTime <= spec.ult().janelaTicks();
    }

    // What the guard does against a hit from attacker, without doing it.
    public static Result check(LivingEntity target, Entity attacker) {
        Long since = GUARDING.get(target);
        if (since == null || attacker == null || attacker == target) {
            return Result.NONE;
        }
        DefesaSpec spec = DefesaSpec.get();
        Vec3 facing = Combat.flatLook(target);
        Vec3 to = attacker.position().subtract(target.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() > 1.0E-4 && flat.normalize().dot(facing) < spec.frente()) {
            return Result.NONE;
        }
        return target.level().getGameTime() - since <= spec.parryTicks() ? Result.PARRY : Result.BLOCK;
    }

    public static boolean blockedThisTick(LivingEntity target) {
        Long at = BLOCKED_AT.get(target);
        return at != null && at == target.level().getGameTime();
    }

    // Stun.apply: a technique's stun on someone whose guard just blocked (or parried) its hit from the front does nothing,
    // the way a blocked punch or cut never stuns (Melee, SwordArts). Before, the technique's stun still landed and dropped
    // the guard with the meter full (Stark 2026-11-25). An ult's stun still gets through, like its damage; a guard break
    // clears all this first (breakNow), so its stun always lands.
    public static boolean absorbsStun(LivingEntity target) {
        Long at = BLOCKED_AT.get(target);
        return at != null && isGuarding(target) && target.level().getGameTime() - at <= STUN_SHIELD_TICKS
                && !BLOCKED_ULT.getOrDefault(target, false);
    }

    // LivingIncomingDamageEvent: blocked hits lose most of their damage (the rest is chip damage) and wear the guard
    // meter, parried ones lose all of it. An ult gets more through and can't be parried away.
    public static void onIncomingDamage(LivingEntity target, LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        Result result = check(target, attacker);
        if (result == Result.NONE) {
            // a hit that got around the guard (from behind): its stun must land, even right after a block
            if (isGuarding(target)) {
                BLOCKED_AT.remove(target);
                BLOCKED_ULT.remove(target);
            }
            return;
        }
        DefesaSpec spec = DefesaSpec.get();
        long now = target.level().getGameTime();
        boolean ult = ultHit(attacker, spec);
        BLOCKED_AT.put(target, now);
        BLOCKED_ULT.put(target, ult);
        DefesaSpec.Resistencia r = spec.resistencia();
        if (result == Result.PARRY && !ult) {
            event.setCanceled(true);
            // full length on bosses too: hitting the perfect block's window is rewarded in full (Stark 2026-11-26)
            Stun.apply(attacker, spec.parryStunTicks(), true);
            if (r != null && METER.containsKey(target)) {
                METER.put(target, Math.min(r.maximo(), METER.get(target) + r.parryDevolve()));
            }
            effects(target, attacker, "parry");
            reaction(target, 1);
            return;
        }
        float full = event.getAmount();
        double through = ult ? spec.ult().passa() : 1 - spec.reducao();
        event.setAmount(full * (float) Math.max(0, Math.min(1, through)));
        boolean broke = false;
        if (r != null && r.maximo() > 0) {
            USED_AT.put(target, now);
            double cost = r.custoPorDano() * full / Math.max(1, target.getMaxHealth());
            if (now - FLAT_COST_AT.getOrDefault(target, -100L) >= FLAT_COST_EVERY) {
                FLAT_COST_AT.put(target, now);
                cost += r.custoPorGolpe();
                if (gunbaiUp(target)) {
                    gunbaiSteal(target, attacker);
                }
            }
            if (ult) {
                cost *= spec.ult().custoMult();
            }
            broke = wearGuard(target, attacker, cost, r);
        }
        if (broke) {
            // The hit that empties the meter breaks the guard: open, like after the strong attack.
            breakNow(target, attacker);
            return;
        }
        effects(target, attacker, meter(target) < (r == null ? 0 : r.trincadaAbaixoDe()) ? "bloqueioTrincado" : "bloqueio");
        reaction(target, 0);
        Combat.push(target, target.position().subtract(attacker.position()), spec.empurraoBloqueio() * (ult ? 2 : 1), 0.05);
    }

    // A grab that comes to you (chain, hand, dash) meeting the guard from the front: it fails and whoever tried is
    // stunned, 1 s on a block and 2 s on a perfect defense. Area, gaze, domain and appearing behind you are never
    // blocked this way (rebalance 2026-11-20).
    public static final int GRAB_BLOCK_STUN = 20;
    public static final int GRAB_PARRY_STUN = 40;

    public static boolean blockGrab(LivingEntity target, LivingEntity attacker) {
        Result result = check(target, attacker);
        if (result == Result.NONE) {
            return false;
        }
        BLOCKED_AT.put(target, target.level().getGameTime());
        boolean parry = result == Result.PARRY;
        Stun.apply(attacker, parry ? GRAB_PARRY_STUN : GRAB_BLOCK_STUN);
        effects(target, attacker, parry ? "parry" : "bloqueio");
        reaction(target, parry ? 1 : 0);
        return true;
    }

    // The strong attack: the guard breaks and the defender is left open.
    public static boolean breakGuard(LivingEntity target, LivingEntity attacker) {
        if (check(target, attacker) == Result.NONE || gunbaiTakesBreak(target, attacker)) {
            return false;
        }
        breakNow(target, attacker);
        return true;
    }

    // The guard breaks (strong attack, Agarrão, meter empty): the break pose, stunned, can't guard for a while, and the
    // meter is full again once it can. attacker null: it gave out on its own (held too long).
    private static void breakNow(LivingEntity target, LivingEntity attacker) {
        DefesaSpec spec = DefesaSpec.get();
        // the break pose first, so the client doesn't start lowering the guard the normal way
        reaction(target, 2);
        stop(target);
        BROKEN_UNTIL.put(target, target.level().getGameTime() + spec.quebraStunTicks() + spec.recargaQuebraTicks());
        Stun.apply(target, spec.quebraStunTicks());
        METER.remove(target);
        FLAT_COST_AT.remove(target);
        BLOCKED_AT.remove(target);
        BLOCKED_ULT.remove(target);
        effects(target, attacker, "quebra");
        sync(target);
    }

    // The guard's reaction pose on everyone watching (0 block, 1 parry, 2 break; client/GuardAnim).
    private static void reaction(LivingEntity target, int kind) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.GuardHit(target.getId(), kind));
    }

    static void effects(LivingEntity target, LivingEntity attacker, String momento) {
        ServerLevel level = Combat.level(target);
        // No attacker (the guard gave out on its own): in front of the defender's chest.
        Vec3 from = attacker != null ? attacker.getEyePosition() : Combat.center(target).add(Combat.flatLook(target).scale(2));
        Vec3 between = Combat.center(target).add(from.subtract(Combat.center(target)).scale(0.25));
        Vec3 dir = between.subtract(from);
        SkillSounds.play(level, DefesaSpec.get().sons(), momento, 0, 0, onde -> between, List.of());
        Fx.effects(level, "padrao.defesa", false, momento, between, dir.lengthSqr() < 1.0E-4 ? Combat.flatLook(target) : dir.normalize());
    }

    // Drops whoever left the world (logged out, died, unloaded), so a long-running server doesn't keep them around.
    public static void prune() {
        GUARDING.keySet().removeIf(Entity::isRemoved);
        BROKEN_UNTIL.keySet().removeIf(Entity::isRemoved);
        BLOCKED_AT.keySet().removeIf(Entity::isRemoved);
        BLOCKED_ULT.keySet().removeIf(Entity::isRemoved);
        HELD.removeIf(Entity::isRemoved);
        RAISED_AT.keySet().removeIf(Entity::isRemoved);
        LAST_RAISE.keySet().removeIf(Entity::isRemoved);
        METER.keySet().removeIf(Entity::isRemoved);
        USED_AT.keySet().removeIf(Entity::isRemoved);
        FLAT_COST_AT.keySet().removeIf(Entity::isRemoved);
        SENT.keySet().removeIf(Entity::isRemoved);
        FAN.keySet().removeIf(Entity::isRemoved);
        FAN_UNTIL.keySet().removeIf(Entity::isRemoved);
        FAN_SENT.keySet().removeIf(Entity::isRemoved);
    }

    public static void clear() {
        GUARDING.clear();
        BROKEN_UNTIL.clear();
        BLOCKED_AT.clear();
        BLOCKED_ULT.clear();
        HELD.clear();
        RAISED_AT.clear();
        LAST_RAISE.clear();
        METER.clear();
        USED_AT.clear();
        FLAT_COST_AT.clear();
        SENT.clear();
        FAN.clear();
        FAN_UNTIL.clear();
        FAN_SENT.clear();
    }
}
