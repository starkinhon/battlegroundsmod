package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.skill.DefesaSpec;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The guard (held defense key, combat mode only). See DefesaSpec for the rules; values in padrao.defesa.json.
// Server side: guards live here by entity, with the game time they went up.
public final class Defense {
    public enum Result { NONE, BLOCK, PARRY }

    private static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "defesa_speed");
    private static final Map<LivingEntity, Long> GUARDING = new HashMap<>();
    private static final Map<LivingEntity, Long> BROKEN_UNTIL = new HashMap<>();
    // The last game time each entity blocked a hit, so a punch knows not to stun.
    private static final Map<LivingEntity, Long> BLOCKED_AT = new HashMap<>();

    private Defense() {
    }

    public static boolean isGuarding(Entity entity) {
        return entity instanceof LivingEntity living && GUARDING.containsKey(living);
    }

    public static void setGuard(LivingEntity entity, boolean down) {
        if (!down) {
            stop(entity);
            return;
        }
        long now = entity.level().getGameTime();
        if (isGuarding(entity) || !CombatMode.in(entity) || Stun.is(entity) || Freeze.isFrozen(entity) || !entity.isAlive()
                || entity.getData(ModAttachments.POWER).busyTicks > 0 || BROKEN_UNTIL.getOrDefault(entity, 0L) > now) {
            return;
        }
        GUARDING.put(entity, now);
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
            speed.addTransientModifier(new AttributeModifier(SLOW, -DefesaSpec.get().lentidao(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        CombatMode.enter(entity);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.GuardPose(entity.getId(), true));
    }

    public static void stop(LivingEntity entity) {
        if (GUARDING.remove(entity) == null) {
            return;
        }
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.GuardPose(entity.getId(), false));
    }

    // Every server tick: the guard drops when the fighter can't keep it up.
    public static void tick(LivingEntity entity) {
        if (isGuarding(entity) && (!entity.isAlive() || Stun.is(entity) || Freeze.isFrozen(entity) || !CombatMode.in(entity))) {
            stop(entity);
        } else if (isGuarding(entity)) {
            CombatMode.enter(entity);
        }
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

    // LivingIncomingDamageEvent: blocked hits lose most of their damage, parried ones all of it.
    public static void onIncomingDamage(LivingEntity target, LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        Result result = check(target, attacker);
        if (result == Result.NONE) {
            return;
        }
        DefesaSpec spec = DefesaSpec.get();
        BLOCKED_AT.put(target, target.level().getGameTime());
        if (result == Result.PARRY) {
            event.setCanceled(true);
            Stun.apply(attacker, spec.parryStunTicks());
            effects(target, attacker, "parry");
            return;
        }
        event.setAmount(event.getAmount() * (float) (1 - spec.reducao()));
        effects(target, attacker, "bloqueio");
        Combat.push(target, target.position().subtract(attacker.position()), spec.empurraoBloqueio(), 0.05);
    }

    // The strong attack: the guard breaks and the defender is left open.
    public static boolean breakGuard(LivingEntity target, LivingEntity attacker) {
        if (check(target, attacker) == Result.NONE) {
            return false;
        }
        DefesaSpec spec = DefesaSpec.get();
        stop(target);
        BROKEN_UNTIL.put(target, target.level().getGameTime() + spec.quebraStunTicks() + spec.recargaQuebraTicks());
        Stun.apply(target, spec.quebraStunTicks());
        effects(target, attacker, "quebra");
        return true;
    }

    private static void effects(LivingEntity target, LivingEntity attacker, String momento) {
        ServerLevel level = Combat.level(target);
        Vec3 between = Combat.center(target).add(attacker.getEyePosition().subtract(Combat.center(target)).scale(0.25));
        Vec3 dir = between.subtract(attacker.getEyePosition());
        SkillSounds.play(level, DefesaSpec.get().sons(), momento, 0, 0, onde -> between, List.of());
        Fx.effects(level, "padrao.defesa", false, momento, between, dir.lengthSqr() < 1.0E-4 ? attacker.getLookAngle() : dir.normalize());
    }

    // Drops whoever left the world (logged out, died, unloaded), so a long-running server doesn't keep them around.
    public static void prune() {
        GUARDING.keySet().removeIf(Entity::isRemoved);
        BROKEN_UNTIL.keySet().removeIf(Entity::isRemoved);
        BLOCKED_AT.keySet().removeIf(Entity::isRemoved);
    }

    public static void clear() {
        GUARDING.clear();
        BROKEN_UNTIL.clear();
        BLOCKED_AT.clear();
    }
}
