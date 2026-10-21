package com.starkinhon.battlegroundsmod.power.powers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Cara de um Soco: absurdly strong normal punches, a serious jump and the serious punch.
public final class OnePunch {
    private static final int GLOVE = 0xD62828;

    private OnePunch() {
    }

    public static Power create() {
        return new Power("one_punch", 0xF2C94C, 1.6f, List.of(
                new Ability(50, 10, OnePunch::normalPunch),
                new Ability(160, 25, OnePunch::consecutivePunches),
                new Ability(140, 20, OnePunch::seriousJump),
                new Ability(100, 0, OnePunch::seriousPunch)));
    }

    // Z: one normal punch. Sends the target flying.
    private static boolean normalPunch(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        Vec3 front = player.getEyePosition().add(look.scale(2.5));
        LivingEntity target = Combat.raycastTarget(player, 5);
        Fx.line(level, ParticleTypes.CLOUD, Combat.handPos(player), front.add(look.scale(3)), 0.3);
        Fx.burst(level, ParticleTypes.SWEEP_ATTACK, front, 1, 0, 0);
        Fx.sound(level, front, SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.5f);
        if (target != null) {
            Vec3 hit = Combat.center(target);
            Combat.hurt(player, target, 12f);
            Combat.push(target, look, 3.5, 0.7);
            Fx.burst(level, ParticleTypes.EXPLOSION, hit, 2, 0.2, 0);
            Fx.burst(level, Fx.dust(GLOVE, 2f), hit, 20, 0.4, 0);
            Fx.sound(level, hit, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.4f);
        }
        return true;
    }

    // X: consecutive normal punches.
    private static boolean consecutivePunches(ServerPlayer player, ServerLevel level) {
        Melee.barrage(player, 34, 4.5, 3f, 10f, GLOVE);
        return true;
    }

    // C: jumps very high and lands with a shockwave that makes a crater.
    private static boolean seriousJump(ServerPlayer player, ServerLevel level) {
        player.getData(ModAttachments.POWER).noFallTicks = 200;
        Combat.launchSelf(player, Combat.flatLook(player).scale(0.6).add(0, 2.2, 0));
        Fx.burst(level, ParticleTypes.CLOUD, player.position(), 30, 0.6, 0.1);
        Fx.sound(level, player.position(), SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.5f);
        Tasks.add(age -> {
            if (!player.isAlive()) {
                return true;
            }
            if (age < 6 || !player.onGround()) {
                return age > 160;
            }
            landing(player, level);
            return true;
        });
        return true;
    }

    private static void landing(ServerPlayer player, ServerLevel level) {
        Vec3 pos = player.position();
        BlockState ground = level.getBlockState(BlockPos.containing(pos).below());
        if (!ground.isAir()) {
            Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), pos, 120, 3, 0.3);
        }
        for (int r = 2; r <= 8; r += 2) {
            Fx.ring(level, ParticleTypes.CLOUD, pos.add(0, 0.2, 0), r, r * 6, 0);
        }
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, pos, 1, 0, 0);
        Combat.explode(player, pos.add(0, -0.5, 0), 2.5f);
        Fx.sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.7f);
        for (LivingEntity target : Combat.inRadius(level, pos, 8, player)) {
            Combat.hurt(player, target, 10f);
            Combat.pushFrom(target, pos, 1.5, 1.1);
        }
    }

    // V (ult): the serious punch. A 60 block blast that goes through everything.
    private static boolean seriousPunch(ServerPlayer player, ServerLevel level) {
        int windup = 15;
        int travel = 10;
        double length = 60;
        double radius = 3.5;
        Set<LivingEntity> hit = new HashSet<>();
        Vec3[] line = new Vec3[2];
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, windup, 9, false, false));
        Fx.sound(level, player.position(), SoundEvents.BEACON_DEACTIVATE, 2f, 0.5f);
        Tasks.add(age -> {
            if (!player.isAlive()) {
                return true;
            }
            if (age < windup) {
                Vec3 hand = Combat.handPos(player);
                Fx.burst(level, Fx.dust(GLOVE, 1.5f), hand, 3, 0.15, 0);
                Fx.burst(level, ParticleTypes.CRIT, hand, 4, 0.3, 0.1);
                if (age == windup - 1) {
                    line[0] = player.getEyePosition();
                    line[1] = line[0].add(player.getLookAngle().scale(length));
                    Fx.sound(level, line[0], SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.5f);
                    Fx.sound(level, line[0], SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
                }
                return false;
            }
            int step = age - windup;
            Vec3 start = line[0];
            Vec3 dir = line[1].subtract(start).normalize();
            Vec3 from = start.add(dir.scale(length * step / travel));
            Vec3 to = start.add(dir.scale(length * (step + 1) / travel));
            Fx.line(level, ParticleTypes.SONIC_BOOM, from, to, 1.5);
            Fx.burst(level, ParticleTypes.CLOUD, to, 60, radius, 0.3);
            Fx.burst(level, ParticleTypes.EXPLOSION, to, 3, radius, 0);
            for (LivingEntity target : Combat.alongLine(level, from, to, radius, player)) {
                if (hit.add(target)) {
                    Combat.hurt(player, target, 50f);
                    Combat.push(target, dir, 5.0, 1.5);
                }
            }
            // Explosions every few blocks carve the path (only when mobGriefing is on).
            if (step > 0) {
                Combat.explode(player, from, 3.5f);
            }
            Combat.explode(player, from.add(to).scale(0.5), 3.5f);
            return step >= travel - 1;
        });
        return true;
    }
}
