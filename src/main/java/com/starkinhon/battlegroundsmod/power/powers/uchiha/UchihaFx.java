package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Cone;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.skill.SkillSpec;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Visuals and sounds of the Uchiha moves. Vanilla particles for now: each method is one effect,
// so moving to Photon means replacing a method body with a call to the matching Photon effect.
// The particle layers follow the Forja de Skills page, which simulates the same emitters.
public final class UchihaFx {
    private static final RandomSource RANDOM = RandomSource.create();

    private UchihaFx() {
    }

    // Burst of flames at the mouth when the fire comes out.
    public static void katonStart(ServerLevel level, SkillSpec spec, Cone cone) {
        Fx.sound(level, cone.origin(), SoundEvents.BLAZE_SHOOT, 2f, 0.5f);
        Fx.sound(level, cone.origin(), SoundEvents.FIRECHARGE_USE, 2f, 0.6f);
        SkillSpec.Explosao inicio = spec.camadas().inicio();
        if (inicio.ativa()) {
            Fx.burst(level, ParticleTypes.FLAME, cone.origin(), inicio.quantidade(), inicio.espalhamento(), inicio.velocidade());
        }
    }

    public static void katonStream(ServerLevel level, SkillSpec spec, Cone cone, int age) {
        SkillSpec.Camadas camadas = spec.camadas();
        Vec3 dir = cone.dir();
        Vec3 side = dir.cross(Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();
        double length = cone.length();

        // Flames shot out of the mouth: they fly along the cone and fade, which sells the motion.
        SkillSpec.Jato chamas = camadas.chamas();
        if (chamas.ativa()) {
            double spread = cone.radiusAt(length) / Math.max(length, 1) * chamas.abertura();
            for (int i = 0; i < chamas.porTick(); i++) {
                Vec3 velocity = dir.add(randomInDisk(side, up, spread)).normalize()
                        .scale(chamas.velocidade() * (0.75 + RANDOM.nextDouble() * 0.35));
                Fx.moving(level, ParticleTypes.FLAME, cone.origin(), velocity);
            }
        }

        // Colored puffs fill the volume, growing and changing color from the mouth to the tip.
        SkillSpec.Volume fogo = camadas.fogo();
        List<String> cores = fogo.cores();
        if (fogo.ativa() && cores != null && !cores.isEmpty()) {
            for (int i = 0; i < fogo.porTick(); i++) {
                double k = Math.pow(RANDOM.nextDouble(), fogo.concentracao());
                double t = k * length;
                Vec3 pos = cone.pointAt(t).add(randomInDisk(side, up, cone.radiusAt(t) * fogo.preenchimento()));
                int color = parseColor(cores.get(Math.min(cores.size() - 1, (int) (k * cores.size()))));
                float size = fogo.tamanhoInicio() + (float) k * (fogo.tamanhoFim() - fogo.tamanhoInicio());
                Fx.burst(level, Fx.dust(color, size), pos, 1, 0, 0);
            }
        }

        Vec3 tip = cone.pointAt(length);
        double tipRadius = cone.radiusAt(length);
        SkillSpec.Fumaca fumaca = camadas.fumaca();
        if (fumaca.ativa() && age % Math.max(1, fumaca.intervaloTicks()) == 0) {
            Fx.burst(level, ParticleTypes.LARGE_SMOKE, tip.add(0, fumaca.altura(), 0), fumaca.quantidade(), tipRadius * fumaca.espalhamento(), 0.02);
        }
        SkillSpec.Lava lava = camadas.lava();
        if (lava.ativa() && age % Math.max(1, lava.intervaloTicks()) == 0) {
            Fx.burst(level, ParticleTypes.LAVA, tip, lava.quantidade(), tipRadius * lava.espalhamento(), 0);
        }
        if (age % 8 == 0) {
            Fx.sound(level, cone.origin(), SoundEvents.FIRE_AMBIENT, 2f, 0.6f);
            Fx.sound(level, cone.pointAt(length * 0.5), SoundEvents.BLAZE_BURN, 1.2f, 0.5f);
        }
    }

    public static void katonBurn(ServerLevel level, Vec3 pos) {
        Fx.burst(level, ParticleTypes.FLAME, pos, 6, 0.3, 0.03);
    }

    // Placeholder look: a red flash in the eyes and a low hum. To be swapped for the real effect later.
    public static void mangekyoStart(ServerLevel level, LivingEntity caster) {
        Vec3 eyes = caster.getEyePosition().add(caster.getLookAngle().scale(0.3));
        Fx.burst(level, Fx.dust(0xD10A0A, 1.2f), eyes, 12, 0.15, 0);
        Fx.sound(level, eyes, SoundEvents.ELDER_GUARDIAN_CURSE, 1.2f, 0.6f);
        Fx.sound(level, eyes, SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
    }

    public static void mangekyoHit(ServerLevel level, LivingEntity target) {
        Vec3 head = target.getEyePosition();
        Fx.ring(level, Fx.dust(0xD10A0A, 1.5f), head.add(0, 0.4, 0), 0.6, 16, 0);
        Fx.burst(level, Fx.dust(0x1A0000, 2f), head, 20, 0.4, 0);
    }

    // "#ff7a10" -> 0xFF7A10. Falls back to orange on a bad value.
    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (NumberFormatException e) {
            return 0xFF7A10;
        }
    }

    // Uniform random point in a disk of the given radius, perpendicular to the cone axis.
    private static Vec3 randomInDisk(Vec3 side, Vec3 up, double radius) {
        double r = Math.sqrt(RANDOM.nextDouble()) * radius;
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        return side.scale(Math.cos(angle) * r).add(up.scale(Math.sin(angle) * r));
    }
}
