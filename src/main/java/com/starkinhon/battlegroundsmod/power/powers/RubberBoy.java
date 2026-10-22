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

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Garoto Borracha: stretching punches, a rubber rocket and a giant fist.
public final class RubberBoy {
    private static final int SKIN = 0xE8B48A;
    private static final int SLEEVE = 0xC0392B;

    private RubberBoy() {
    }

    public static Power create() {
        return new Power("rubber_boy", 0xC0392B, 1.15f, List.of(
                Ability.player(40, 12, RubberBoy::pistol),
                Ability.player(160, 25, RubberBoy::gatling),
                Ability.player(120, 20, RubberBoy::rocket),
                Ability.player(100, 0, RubberBoy::giantPistol)));
    }

    private static void arm(ServerLevel level, Vec3 from, Vec3 to, float scale) {
        Vec3 sleeveEnd = from.add(to.subtract(from).normalize().scale(Math.min(0.8, from.distanceTo(to))));
        Fx.line(level, Fx.dust(SLEEVE, scale), from, sleeveEnd, 0.2);
        Fx.line(level, Fx.dust(SKIN, scale), sleeveEnd, to, 0.25);
        Fx.burst(level, Fx.dust(SKIN, scale * 1.6f), to, 4, 0.15 * scale, 0);
    }

    // Z: the arm stretches out to 16 blocks and punches whatever it reaches.
    private static boolean pistol(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        LivingEntity target = Combat.raycastTarget(player, 16);
        Vec3 reach = target != null ? Combat.center(target) : Combat.raycastPoint(player, 16);
        Fx.sound(level, player.position(), SoundEvents.FISHING_BOBBER_THROW, 1.5f, 0.5f);
        Tasks.repeat(6, age -> {
            // Out for 3 ticks, back for 3.
            double t = age < 3 ? (age + 1) / 3.0 : (5 - age) / 3.0;
            Vec3 hand = Combat.handPos(player);
            arm(level, hand, hand.add(reach.subtract(hand).scale(t)), 1.2f);
            if (age == 2) {
                Fx.burst(level, ParticleTypes.POOF, reach, 10, 0.3, 0.1);
                Fx.burst(level, ParticleTypes.CRIT, reach, 10, 0.3, 0.3);
                if (target != null && target.isAlive()) {
                    Combat.hurt(player, target, 8f);
                    Combat.push(target, look, 1.6, 0.4);
                    Fx.sound(level, reach, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.7f);
                }
            }
        });
        return true;
    }

    // X: rubber gatling, a long barrage of stretching punches.
    private static boolean gatling(ServerPlayer player, ServerLevel level) {
        Melee.barrage(player, 30, 6.0, 2.5f, 7f, SKIN);
        return true;
    }

    // C: launches the player like a rubber rocket. Hits anyone in the way; no fall damage.
    private static boolean rocket(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        Vec3 anchor = player.position().add(0, 1, 0);
        Set<LivingEntity> hit = new HashSet<>();
        player.getData(ModAttachments.POWER).noFallTicks = 80;
        Combat.launchSelf(player, look.scale(2.4).add(0, 0.3, 0));
        Fx.sound(level, anchor, SoundEvents.SLIME_JUMP, 2f, 0.5f);
        Tasks.repeat(20, age -> {
            Vec3 pos = Combat.center(player);
            Fx.burst(level, ParticleTypes.CLOUD, pos, 3, 0.3, 0.02);
            if (age < 4) {
                // Arms stretch back to where the rocket started.
                arm(level, pos, anchor, 1f);
            }
            for (LivingEntity target : Combat.inRadius(level, pos, 1.8, player)) {
                if (hit.add(target)) {
                    Combat.hurt(player, target, 7f);
                    Combat.push(target, look, 1.5, 0.6);
                    Fx.burst(level, ParticleTypes.EXPLOSION, Combat.center(target), 1, 0, 0);
                }
            }
        });
        return true;
    }

    // V (ult): inflates the arm and throws a giant punch that plows forward 26 blocks.
    private static boolean giantPistol(ServerPlayer player, ServerLevel level) {
        int windup = 20;
        int travel = 8;
        double length = 26;
        double radius = 3;
        Set<LivingEntity> hit = new HashSet<>();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, windup, 3, false, false));
        Fx.sound(level, player.position(), SoundEvents.FIRE_EXTINGUISH, 2f, 0.5f);
        Vec3[] line = new Vec3[2];
        Tasks.add(age -> {
            if (!player.isAlive()) {
                return true;
            }
            if (age < windup) {
                // Steam and a swelling fist while charging.
                Vec3 base = player.position();
                Fx.burst(level, ParticleTypes.CLOUD, base.add(0, 1, 0), 4, 0.6, 0.05);
                Fx.sphere(level, Fx.dust(SKIN, 2f), Combat.handPos(player), 0.3 + age * 0.08, 16);
                if (age == windup - 1) {
                    line[0] = Combat.handPos(player);
                    line[1] = player.getEyePosition().add(player.getLookAngle().scale(length));
                    Fx.sound(level, base, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.6f);
                }
                return false;
            }
            int step = age - windup;
            Vec3 start = line[0];
            Vec3 dir = line[1].subtract(start).normalize();
            Vec3 from = start.add(dir.scale(length * step / travel));
            Vec3 to = start.add(dir.scale(length * (step + 1) / travel));
            Fx.sphere(level, Fx.dust(SKIN, 4f), to, radius, 50);
            Fx.burst(level, ParticleTypes.CLOUD, to, 20, radius, 0.1);
            arm(level, start, to, 3f);
            for (LivingEntity target : Combat.alongLine(level, from, to, radius, player)) {
                if (hit.add(target)) {
                    Combat.hurt(player, target, 28f);
                    Combat.push(target, dir, 3.0, 1.0);
                }
            }
            if (step >= travel - 1) {
                Combat.explode(player, to, 4f);
                Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, to, 1, 0, 0);
                return true;
            }
            return false;
        });
        return true;
    }
}
