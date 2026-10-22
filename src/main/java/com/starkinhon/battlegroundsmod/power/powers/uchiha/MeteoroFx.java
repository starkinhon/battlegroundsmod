package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// Look of the Meteoro, in vanilla particles for now. The body is a cloud of dust shaped like a sphere: a model or a
// Photon effect should replace meteorBody later. The smoke trails use campfire smoke, which lingers like the Forja's clouds.
final class MeteoroFx {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    // Campfire smoke never slows down, so its sideways push is scaled to travel about as far as the page's damped clouds.
    private static final double SMOKE_DRIFT = 0.1;

    private MeteoroFx() {
    }

    static void call(ServerLevel level, ServerPlayer player) {
        Fx.sound(level, player.position(), SoundEvents.WITHER_SPAWN, 2f, 0.5f);
        Fx.sound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.4f);
    }

    // Dark red energy pulled up from the raised right hand.
    static void prepare(ServerLevel level, ServerPlayer player, MeteoroSpec spec, int age) {
        MeteoroSpec.Preparo preparo = spec.preparo();
        if (!preparo.ativa()) {
            return;
        }
        Vec3 flat = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Vec3 hand = player.position().add(right.scale(0.4)).add(0, 2.6, 0);
        var dust = Fx.dust(UchihaFx.parseColor(preparo.cor()), 1.5f);
        for (int i = 0; i < preparo.porTick(); i++) {
            double angle = age * 0.4 + i * Math.PI * 2 / Math.max(1, preparo.porTick());
            double up = RANDOM.nextDouble() * 6;
            double radius = 0.3 + up * 0.15;
            Fx.burst(level, dust, hand.add(Math.cos(angle) * radius, up, Math.sin(angle) * radius), 1, 0.05, 0);
        }
        if (age % 4 == 0) {
            Fx.burst(level, ParticleTypes.SMOKE, hand, 3, 0.2, 0.02);
        }
    }

    // Circle on the ground with a second one closing in as the meteor gets near.
    static void warning(ServerLevel level, MeteoroSpec spec, Vec3 target, double progress, int age) {
        if (!spec.aviso().ativo() || age % 3 != 0) {
            return;
        }
        double radius = spec.impacto().raio();
        var dust = Fx.dust(UchihaFx.parseColor(spec.aviso().cor()), 2.5f);
        Vec3 center = target.add(0, 0.2, 0);
        int points = (int) Math.min(200, Math.max(24, radius * 4));
        Fx.ring(level, dust, center, radius, points, 0);
        Fx.ring(level, dust, center, Math.max(0.5, radius * (1 - progress)), Math.max(12, (int) (points * (1 - progress))), age * 0.02);
    }

    static void fall(ServerLevel level, MeteoroSpec spec, Meteoro.Fall fall, int age) {
        Vec3 center = fall.at(age);
        if (age == 0) {
            Fx.sound(level, center, SoundEvents.WITHER_SHOOT, 4f, 0.3f);
        }
        if (age % 10 == 0) {
            Fx.sound(level, fall.end(), SoundEvents.FIRECHARGE_USE, 3f, 0.4f + age / (float) fall.ticks() * 0.4f);
        }
        body(level, spec.meteoro(), fall, center, age);
        double speed = fall.speed(age);
        for (MeteoroSpec.Fumaca fumaca : spec.fumacas()) {
            if (fumaca.ativa()) {
                smoke(level, spec.meteoro(), fumaca, fall.dir(), center, speed, age);
            }
        }
        if (spec.riscos().ativa()) {
            streaks(level, spec.meteoro(), spec.riscos(), fall.dir(), center);
        }
    }

    // A dark sphere that spins as it falls, with a glowing front where it heats up.
    private static void body(ServerLevel level, MeteoroSpec.Corpo corpo, Meteoro.Fall fall, Vec3 center, int age) {
        double radius = corpo.raio();
        int points = (int) Math.min(500, Math.max(60, radius * radius * 2.5));
        double spin = Math.toRadians(corpo.girar() * age);
        var dark = Fx.dust(UchihaFx.parseColor(corpo.corEscura()), 4f);
        var hot = Fx.dust(UchihaFx.parseColor(corpo.corBrilho()), (float) Math.min(4, 2.5 * corpo.brilho()));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i + 0.5) / points * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = GOLDEN_ANGLE * i + spin;
            Vec3 normal = new Vec3(Math.cos(theta) * r, y, Math.sin(theta) * r);
            // The leading face glows; how much of it depends on brilho.
            boolean front = normal.dot(fall.dir()) > 1 - 0.35 * corpo.brilho();
            Fx.burst(level, front ? hot : dark, center.add(normal.scale(radius)), 1, 0, 0);
        }
        if (age % 2 == 0) {
            Fx.burst(level, ParticleTypes.FLAME, center.add(fall.dir().scale(radius)), 20, radius * 0.4, 0.05);
        }
    }

    // Puffs left behind around the meteor. Each trail drifts sideways, so a pair of trails opens into a V.
    private static void smoke(ServerLevel level, MeteoroSpec.Corpo corpo, MeteoroSpec.Fumaca fumaca, Vec3 dir, Vec3 center,
            double speed, int age) {
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        int sides = Math.max(1, fumaca.lados());
        double drift = speed * Math.tan(Math.toRadians(fumaca.abertura() / 2)) * SMOKE_DRIFT;
        var dust = Fx.dust(UchihaFx.parseColor(fumaca.cor()), 4f);
        for (int s = 0; s < sides; s++) {
            double angle = s * Math.PI * 2 / sides;
            Vec3 out = sides == 1 ? Vec3.ZERO : side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 base = center.add(out.scale(corpo.raio() * fumaca.deslocamento()));
            for (int i = 0; i < fumaca.densidade(); i++) {
                Vec3 pos = base.add(spread(fumaca.largura())).subtract(dir.scale(RANDOM.nextDouble() * speed));
                Vec3 velocity = out.scale(drift).add(spread(0.02));
                Fx.moving(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos, velocity);
                if (i % 2 == 0) {
                    Fx.burst(level, dust, pos, 1, fumaca.largura() * 0.3, 0);
                }
            }
        }
    }

    // Thin red streaks trailing from the back of the meteor.
    private static void streaks(ServerLevel level, MeteoroSpec.Corpo corpo, MeteoroSpec.Riscos riscos, Vec3 dir, Vec3 center) {
        var dust = Fx.dust(UchihaFx.parseColor(riscos.cor()), 0.8f);
        Vec3 back = center.subtract(dir.scale(corpo.raio() * 0.8));
        double spread = corpo.raio() * riscos.espalhamento();
        for (int i = 0; i < riscos.porTick(); i++) {
            Vec3 from = back.add(spread(spread)).subtract(dir.scale(RANDOM.nextDouble() * riscos.velocidade() * 4));
            Fx.line(level, dust, from, from.subtract(dir.scale(riscos.comprimento())), 0.5);
        }
    }

    static void impact(ServerLevel level, MeteoroSpec spec, Vec3 pos) {
        MeteoroSpec.Impacto impacto = spec.impacto();
        Fx.sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.5f);
        Fx.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 5f, 0.6f);
        Fx.sound(level, pos, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.5f);
        MeteoroSpec.Explosao explosao = impacto.explosao();
        if (explosao.ativa()) {
            double size = spec.meteoro().raio();
            Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, pos, 6, size * 0.4, 0);
            Fx.burst(level, Fx.dust(UchihaFx.parseColor(explosao.cor()), 4f), pos.add(0, size * 0.3, 0), explosao.quantidade() * 3, size * 0.6, 0);
            Fx.burst(level, ParticleTypes.FLAME, pos.add(0, 1, 0), explosao.quantidade(), size * 0.4, 0.5);
            Fx.burst(level, ParticleTypes.LARGE_SMOKE, pos.add(0, 2, 0), explosao.fumaca() * 2, size * 0.5, 0.2);
            for (int i = 0; i < explosao.fumaca(); i++) {
                Fx.moving(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.add(spread(size * 0.5)), spread(0.3).add(0, 0.2, 0));
            }
        }
        MeteoroSpec.Onda onda = impacto.onda();
        if (onda.ativa() && onda.velocidade() > 0) {
            var dust = Fx.dust(UchihaFx.parseColor(onda.cor()), onda.tamanho());
            int ticks = (int) Math.ceil(impacto.raio() / onda.velocidade());
            Tasks.repeat(ticks, age -> {
                double radius = onda.velocidade() * (age + 1);
                int points = (int) Math.min(240, Math.max(16, radius * 3));
                Fx.ring(level, dust, pos.add(0, 0.3, 0), radius, points, age * 0.1);
                if (age % 2 == 0) {
                    Fx.ring(level, ParticleTypes.POOF, pos.add(0, 0.5, 0), radius, points / 3, 0);
                }
            });
        }
    }

    private static Vec3 spread(double size) {
        return new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).scale(size * 0.5);
    }
}
