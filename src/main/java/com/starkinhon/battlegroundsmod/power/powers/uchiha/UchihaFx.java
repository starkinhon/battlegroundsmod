package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Cone;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.skill.SkillSpec;
import com.starkinhon.battlegroundsmod.skill.SusanooSpec;

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

    // Burst of flames at the mouth when the fire comes out. Sounds come from the file (Katon).
    public static void katonStart(ServerLevel level, SkillSpec spec, Cone cone) {
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
    }

    public static void katonBurn(ServerLevel level, Vec3 pos) {
        Fx.burst(level, ParticleTypes.FLAME, pos, 6, 0.3, 0.03);
    }

    // Placeholder look: a red flash in the eyes, right after the cutscene zooms in.
    public static void mangekyoEyes(ServerLevel level, LivingEntity caster) {
        Vec3 eyes = caster.getEyePosition().add(caster.getLookAngle().scale(0.3));
        Fx.burst(level, Fx.dust(0xD10A0A, 1.2f), eyes, 12, 0.15, 0);
    }

    public static void mangekyoHit(ServerLevel level, LivingEntity target) {
        Vec3 head = target.getEyePosition();
        Fx.ring(level, Fx.dust(0xD10A0A, 1.5f), head.add(0, 0.4, 0), 0.6, 16, 0);
        Fx.burst(level, Fx.dust(0x1A0000, 2f), head, 20, 0.4, 0);
    }

    // Susanoo counter is ready: a blue flare and a low hum.
    public static void susanooReady(ServerLevel level, LivingEntity player, SusanooSpec spec) {
        Vec3 pos = player.position();
        Fx.ring(level, Fx.dust(parseColor(spec.visual().cor()), 2f), pos.add(0, 0.2, 0), 1.6, 24, 0);
        Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, pos.add(0, 1, 0), 20, 0.6, 0.05);
        Fx.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
        Fx.sound(level, pos, SoundEvents.WITHER_SHOOT, 0.6f, 0.4f);
    }

    // While the window is open: faint spectral ribs around the caster.
    public static void susanooAura(ServerLevel level, LivingEntity player, SusanooSpec spec, int age) {
        if (age % 2 != 0) {
            return;
        }
        Vec3 base = player.position();
        for (int ring = 0; ring < 4; ring++) {
            double radius = 1.5 - Math.abs(ring - 1.5) * 0.2;
            Fx.ring(level, Fx.dust(parseColor(spec.visual().cor()), 1.4f), base.add(0, 0.3 + ring * 0.6, 0), radius, 12, age * 0.08 + ring);
        }
        Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, base.add(0, 1.2, 0), 2, 0.8, 0.02);
    }

    public static void susanooGrab(ServerLevel level, LivingEntity player, LivingEntity target, SusanooSpec spec) {
        Fx.sound(level, player.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.5f, 1.4f);
        Fx.sound(level, target.position(), SoundEvents.IRON_GOLEM_ATTACK, 1.5f, 0.5f);
        Fx.burst(level, ParticleTypes.SOUL, Combat.center(target), 15, 0.5, 0.05);
    }

    // The spectral arm, from behind the shoulder in an arc up to the fist. progress 0..1 is how far it has reached.
    public static void susanooArm(ServerLevel level, LivingEntity player, LivingEntity target, SusanooSpec spec, double progress) {
        var color = Fx.dust(parseColor(spec.visual().cor()), 2f);
        Vec3 from = Susanoo.shoulder(player);
        Vec3 to = Susanoo.hand(player, spec);
        Vec3 control = from.add(to.subtract(from).scale(0.3)).add(0, 1.5, 0);
        int points = Math.max(4, (int) (to.distanceTo(from) / 0.3));
        for (int i = 0; i <= points * progress; i++) {
            double t = i / (double) points;
            double u = 1 - t;
            Vec3 point = from.scale(u * u).add(control.scale(2 * u * t)).add(to.scale(t * t));
            Fx.burst(level, color, point, 2, 0.12, 0);
        }
        if (progress >= 1) {
            double radius = Math.max(0.7, target.getBbWidth() * 0.9);
            Fx.sphere(level, color, Combat.center(target), radius, 36);
            Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, Combat.center(target), 2, radius * 0.6, 0.01);
        }
    }

    public static void susanooCrush(ServerLevel level, LivingEntity target, SusanooSpec spec) {
        Vec3 center = Combat.center(target);
        Fx.sphere(level, Fx.dust(parseColor(spec.visual().cor()), 2.5f), center, 0.5, 30);
        Fx.burst(level, ParticleTypes.CRIT, center, 25, 0.4, 0.4);
        Fx.sound(level, center, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
        Fx.sound(level, center, SoundEvents.SKELETON_HURT, 1f, 0.5f);
    }

    public static void susanooThrow(ServerLevel level, LivingEntity target, SusanooSpec spec) {
        Vec3 center = Combat.center(target);
        Fx.burst(level, Fx.dust(parseColor(spec.visual().cor()), 3f), center, 30, 0.8, 0);
        Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, center, 25, 0.6, 0.15);
        Fx.sound(level, center, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2f, 0.5f);
        Fx.sound(level, center, SoundEvents.WARDEN_ATTACK_IMPACT, 1.5f, 0.7f);
    }

    // "#ff7a10" -> 0xFF7A10. Falls back to orange on a bad value.
    static int parseColor(String hex) {
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
