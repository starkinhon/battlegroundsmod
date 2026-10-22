package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import com.starkinhon.battlegroundsmod.combat.Cone;
import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// Visuals and sounds of the Uchiha moves. Vanilla particles for now: each method is one effect,
// so moving to Photon means replacing a method body with a call to the matching Photon effect.
public final class UchihaFx {
    // Yellow at the mouth, orange in the middle, red at the tip, like a real flame.
    private static final int[] FIRE_COLORS = {0xFFE14A, 0xFFB020, 0xFF7A10, 0xFF4A0A, 0xE5200A};
    // Speed of the moving flames, in blocks per tick. With vanilla flame friction they reach the end of the cone.
    private static final double FLAME_SPEED = 0.8;
    private static final RandomSource RANDOM = RandomSource.create();

    private UchihaFx() {
    }

    public static void katonStart(ServerLevel level, Vec3 mouth) {
        Fx.sound(level, mouth, SoundEvents.BLAZE_SHOOT, 2f, 0.5f);
        Fx.sound(level, mouth, SoundEvents.FIRECHARGE_USE, 2f, 0.6f);
        Fx.burst(level, ParticleTypes.FLAME, mouth, 20, 0.2, 0.15);
    }

    public static void katonStream(ServerLevel level, Cone cone, int age) {
        Vec3 dir = cone.dir();
        Vec3 side = dir.cross(Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();
        double length = cone.length();

        // Flames shot out of the mouth: they fly along the cone and fade, which sells the motion.
        for (int i = 0; i < 20; i++) {
            Vec3 spread = randomInDisk(side, up, cone.radiusAt(length) / Math.max(length, 1));
            Vec3 velocity = dir.add(spread).normalize().scale(FLAME_SPEED * (0.75 + RANDOM.nextDouble() * 0.35));
            Fx.moving(level, ParticleTypes.FLAME, cone.origin(), velocity);
        }

        // Big colored puffs fill the volume, growing and reddening towards the tip.
        for (int i = 0; i < 45; i++) {
            double f = Math.sqrt(RANDOM.nextDouble());
            double t = f * length;
            Vec3 pos = cone.pointAt(t).add(randomInDisk(side, up, cone.radiusAt(t) * 0.9));
            int color = FIRE_COLORS[Math.min(FIRE_COLORS.length - 1, (int) (f * FIRE_COLORS.length))];
            Fx.burst(level, Fx.dust(color, 1.5f + (float) f * 2.5f), pos, 1, 0, 0);
        }

        if (age % 2 == 0) {
            Vec3 tip = cone.pointAt(length);
            Fx.burst(level, ParticleTypes.LARGE_SMOKE, tip.add(0, 0.5, 0), 2, cone.radiusAt(length) * 0.5, 0.02);
            Fx.burst(level, ParticleTypes.LAVA, tip, 1, cone.radiusAt(length) * 0.4, 0);
        }
        if (age % 8 == 0) {
            Fx.sound(level, cone.origin(), SoundEvents.FIRE_AMBIENT, 2f, 0.6f);
            Fx.sound(level, cone.pointAt(length * 0.5), SoundEvents.BLAZE_BURN, 1.2f, 0.5f);
        }
    }

    public static void katonBurn(ServerLevel level, Vec3 pos) {
        Fx.burst(level, ParticleTypes.FLAME, pos, 6, 0.3, 0.03);
    }

    // Uniform random point in a disk of the given radius, perpendicular to the cone axis.
    private static Vec3 randomInDisk(Vec3 side, Vec3 up, double radius) {
        double r = Math.sqrt(RANDOM.nextDouble()) * radius;
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        return side.scale(Math.cos(angle) * r).add(up.scale(Math.sin(angle) * r));
    }
}
