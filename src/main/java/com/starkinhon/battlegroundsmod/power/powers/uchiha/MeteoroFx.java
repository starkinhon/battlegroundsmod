package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// Look of the Meteoro, with the mod's own particles (fx/ModParticles). The ball is drawn on the client by
// client/fx/MeteorRenderer; everything around it is here.
final class MeteoroFx {
    private static final RandomSource RANDOM = RandomSource.create();

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
        var glow = ModParticles.glow(UchihaFx.parseColor(preparo.cor()), 0.5f, 0.1f, 18, 0.9f);
        for (int i = 0; i < preparo.porTick(); i++) {
            double angle = age * 0.4 + i * Math.PI * 2 / Math.max(1, preparo.porTick());
            double radius = 0.4 + RANDOM.nextDouble() * 0.5;
            Vec3 pos = hand.add(Math.cos(angle) * radius, RANDOM.nextDouble() * 0.6, Math.sin(angle) * radius);
            Fx.moving(level, glow, pos, new Vec3(-Math.cos(angle) * 0.03, 0.25 + RANDOM.nextDouble() * 0.2, -Math.sin(angle) * 0.03));
        }
    }

    // Circle on the ground with a second one closing in as the meteor gets near.
    static void warning(ServerLevel level, MeteoroSpec spec, Vec3 target, double progress, int age) {
        if (!spec.aviso().ativo() || age % 3 != 0) {
            return;
        }
        double radius = spec.impacto().raio();
        var glow = ModParticles.glow(UchihaFx.parseColor(spec.aviso().cor()), 1.2f, 1.0f, 8, 0.8f);
        Vec3 center = target.add(0, 0.3, 0);
        int points = (int) Math.min(200, Math.max(24, radius * 4));
        Fx.ring(level, glow, center, radius, points, 0);
        Fx.ring(level, glow, center, Math.max(0.5, radius * (1 - progress)), Math.max(12, (int) (points * (1 - progress))), age * 0.02);
    }

    static void fall(ServerLevel level, MeteoroSpec spec, Meteoro.Fall fall, int age) {
        Vec3 center = fall.at(age);
        if (age == 0) {
            Fx.sound(level, center, SoundEvents.WITHER_SHOOT, 4f, 0.3f);
        }
        if (age % 10 == 0) {
            Fx.sound(level, fall.end(), SoundEvents.FIRECHARGE_USE, 3f, 0.4f + age / (float) fall.ticks() * 0.4f);
        }
        heat(level, spec.meteoro(), fall.dir(), center);
        double speed = fall.speed(age);
        for (MeteoroSpec.Fumaca fumaca : spec.fumacas()) {
            if (fumaca.ativa()) {
                smoke(level, spec.meteoro(), fumaca, fall.dir(), center, speed);
            }
        }
        if (spec.riscos().ativa()) {
            streaks(level, spec.meteoro(), spec.riscos(), fall.dir(), center, speed);
        }
    }

    // Hot air rippling off the leading face.
    private static void heat(ServerLevel level, MeteoroSpec.Corpo corpo, Vec3 dir, Vec3 center) {
        var glow = ModParticles.glow(UchihaFx.parseColor(corpo.corBrilho()), (float) corpo.raio() * 0.35f, (float) corpo.raio() * 0.6f, 6,
                (float) Math.min(1, 0.25 * corpo.brilho()));
        for (int i = 0; i < 6; i++) {
            Fx.burst(level, glow, center.add(dir.scale(corpo.raio() * 0.8)).add(spread(corpo.raio() * 0.8)), 1, 0, 0);
        }
    }

    // Puffs left behind around the meteor, slowed by friction like the Forja's clouds. Each trail drifts sideways,
    // so a pair of trails opens into a V.
    private static void smoke(ServerLevel level, MeteoroSpec.Corpo corpo, MeteoroSpec.Fumaca fumaca, Vec3 dir, Vec3 center, double speed) {
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        int sides = Math.max(1, fumaca.lados());
        double drift = speed * Math.tan(Math.toRadians(fumaca.abertura() / 2));
        float size = (float) fumaca.largura();
        var puff = ModParticles.smoke(UchihaFx.parseColor(fumaca.cor()), size, size * 1.8f, Math.max(1, fumaca.duracaoTicks()), 0.85f);
        for (int s = 0; s < sides; s++) {
            double angle = s * Math.PI * 2 / sides;
            Vec3 out = sides == 1 ? Vec3.ZERO : side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 base = center.add(out.scale(corpo.raio() * fumaca.deslocamento()));
            for (int i = 0; i < fumaca.densidade(); i++) {
                Vec3 pos = base.add(spread(size * 0.6)).subtract(dir.scale(RANDOM.nextDouble() * speed));
                Fx.moving(level, puff, pos, out.scale(drift).add(spread(0.05)));
            }
        }
    }

    // Thin red streaks pulled out behind the meteor: each spark flies backwards, so together they draw lines.
    private static void streaks(ServerLevel level, MeteoroSpec.Corpo corpo, MeteoroSpec.Riscos riscos, Vec3 dir, Vec3 center, double speed) {
        double velocity = Math.max(0.1, riscos.velocidade());
        int life = (int) Math.max(2, riscos.comprimento() / velocity);
        var spark = ModParticles.glow(UchihaFx.parseColor(riscos.cor()), 0.35f, 0.15f, life, 1f);
        Vec3 back = center.subtract(dir.scale(corpo.raio() * 0.8));
        double spread = corpo.raio() * riscos.espalhamento();
        for (int i = 0; i < riscos.porTick(); i++) {
            for (int j = 0; j < 4; j++) {
                Vec3 pos = back.add(spread(spread)).subtract(dir.scale(RANDOM.nextDouble() * speed));
                Fx.moving(level, spark, pos, dir.scale(-velocity));
            }
        }
    }

    static void impact(ServerLevel level, MeteoroSpec spec, Vec3 pos) {
        MeteoroSpec.Impacto impacto = spec.impacto();
        Fx.sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.5f);
        Fx.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 5f, 0.6f);
        Fx.sound(level, pos, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.5f);
        MeteoroSpec.Explosao explosao = impacto.explosao();
        double size = spec.meteoro().raio();
        if (explosao.ativa()) {
            int color = UchihaFx.parseColor(explosao.cor());
            var fire = ModParticles.with(ModParticles.glow(color, (float) size * 0.5f, (float) size * 0.15f, 30, 1f), 0.9f, 0f);
            for (int i = 0; i < explosao.quantidade(); i++) {
                Vec3 out = spread(2).normalize();
                Fx.moving(level, fire, pos.add(0, size * 0.3, 0).add(out.scale(size * 0.3)), out.scale(0.8 + RANDOM.nextDouble() * 1.6).add(0, 0.4, 0));
            }
            var dust = ModParticles.smoke(0x4A403C, (float) size * 0.6f, (float) size * 1.4f, 120, 0.9f);
            for (int i = 0; i < explosao.fumaca(); i++) {
                Vec3 out = spread(2).normalize();
                Fx.moving(level, dust, pos.add(0, size * 0.2, 0).add(out.scale(size * 0.4)), out.multiply(1, 0.3, 1).scale(0.5 + RANDOM.nextDouble() * 1.5).add(0, 0.3, 0));
            }
            Fx.burst(level, ModParticles.glow(0xFFF2E0, (float) size * 4, (float) size * 6, 12, 0.9f), pos.add(0, size * 0.4, 0), 2, 0, 0);
        }
        MeteoroSpec.Onda onda = impacto.onda();
        if (onda.ativa() && onda.velocidade() > 0) {
            var wave = ModParticles.glow(UchihaFx.parseColor(onda.cor()), onda.tamanho(), onda.tamanho() * 1.5f, 10, 0.9f);
            var ground = ModParticles.smoke(0x8A7A6A, onda.tamanho() * 1.2f, onda.tamanho() * 2.2f, 40, 0.7f);
            int ticks = (int) Math.ceil(impacto.raio() / onda.velocidade());
            Tasks.repeat(ticks, age -> {
                double radius = onda.velocidade() * (age + 1);
                int points = (int) Math.min(240, Math.max(16, radius * 3));
                Fx.ring(level, wave, pos.add(0, 0.6, 0), radius, points, age * 0.1);
                if (age % 2 == 0) {
                    Fx.ring(level, ground, pos.add(0, 0.8, 0), radius, points / 3, 0);
                }
            });
        }
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, pos, 3, size * 0.4, 0);
    }

    private static Vec3 spread(double size) {
        return new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).scale(size * 0.5);
    }
}
