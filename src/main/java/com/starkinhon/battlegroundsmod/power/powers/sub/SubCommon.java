package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shared bits of the SubAlmas' skills: timed states (a Bankai, a mask), holding a key, poses, the ground under a point.
public final class SubCommon {
    public static final RandomSource RANDOM = RandomSource.create();
    // Timed states by entity: name -> the game tick it ends.
    private static final Map<UUID, Map<String, Long>> STATES = new HashMap<>();

    private SubCommon() {
    }

    public static void setState(LivingEntity entity, String name, int ticks) {
        STATES.computeIfAbsent(entity.getUUID(), k -> new HashMap<>()).put(name, entity.level().getGameTime() + ticks);
    }

    public static void clearState(LivingEntity entity, String name) {
        Map<String, Long> map = STATES.get(entity.getUUID());
        if (map != null) {
            map.remove(name);
        }
    }

    public static boolean hasState(LivingEntity entity, String name) {
        Map<String, Long> map = STATES.get(entity.getUUID());
        Long end = map == null ? null : map.get(name);
        return end != null && entity.level().getGameTime() < end;
    }

    // A running skill stops when its caster dies, changes power or is interrupted (stunned out of it).
    public static Guard guard(LivingEntity caster) {
        PowerData data = caster.getData(ModAttachments.POWER);
        return new Guard(caster, data.power, data.interrupts);
    }

    public record Guard(LivingEntity caster, String power, int interrupts) {
        public boolean broken() {
            PowerData now = caster.getData(ModAttachments.POWER);
            return !caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts;
        }
    }

    // Players hold the key; bosses hold for the ticks they picked.
    public static boolean held(LivingEntity caster, int age, int bossTicks) {
        return caster instanceof Player ? Stamina.holding(caster) : age < bossTicks;
    }

    public static void busy(LivingEntity caster, int ticks) {
        PowerData data = caster.getData(ModAttachments.POWER);
        data.busyTicks = Math.max(data.busyTicks, ticks);
    }

    public static void pose(LivingEntity caster, String skill) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), skill, false));
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // The target in the aim, or null.
    public static LivingEntity aimed(LivingEntity caster, double range) {
        return Combat.raycastTarget(caster, range);
    }

    // Where the aim lands: the entity aimed at (its feet) or the block hit, up to range.
    public static Vec3 aimPoint(LivingEntity caster, double range) {
        LivingEntity target = aimed(caster, range);
        if (target != null) {
            return target.position();
        }
        return Combat.raycastPoint(caster, range);
    }

    // The ground under a point (a few blocks down at most), or the point itself.
    public static Vec3 ground(ServerLevel level, Vec3 at) {
        Vec3 from = at.add(0, 1.5, 0);
        HitResult hit = level.clip(new ClipContext(from, from.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }

    public static boolean solid(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    public static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
