package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// Look of the Meteoro, a copy of the Forja's emit() with the mod's own particles (fx/ModParticles). The ball and the
// warning circle on the ground are drawn on the client by client/fx/MeteorRenderer; everything around them is here.
final class MeteoroFx {
    private static final RandomSource RANDOM = RandomSource.create();

    private MeteoroFx() {
    }

    static void call(ServerLevel level, ServerPlayer player) {
        Fx.sound(level, player.position(), SoundEvents.WITHER_SPAWN, 2f, 0.5f);
        Fx.sound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.4f);
    }

    // Dark red dust swirling up around the caster.
    static void prepare(ServerLevel level, ServerPlayer player, MeteoroSpec spec) {
        MeteoroSpec.Preparo preparo = spec.preparo();
        if (!preparo.ativa()) {
            return;
        }
        int color = UchihaFx.parseColor(preparo.cor());
        for (int i = 0; i < preparo.porTick(); i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = 0.7 + RANDOM.nextDouble() * 0.5;
            Vec3 pos = player.position().add(Math.cos(a) * r, RANDOM.nextDouble() * 2.2, Math.sin(a) * r);
            Fx.moving(level, ModParticles.dust(color, 1.3f), pos, new Vec3(-Math.sin(a) * 0.08, 0.06, Math.cos(a) * 0.08));
        }
    }

    static void fall(ServerLevel level, MeteoroSpec spec, Meteoro.Fall fall, int age) {
        Vec3 center = fall.at(age);
        if (age == 0) {
            Fx.sound(level, center, SoundEvents.WITHER_SHOOT, 4f, 0.3f);
        }
        if (age % 10 == 0) {
            Fx.sound(level, fall.end(), SoundEvents.FIRECHARGE_USE, 3f, 0.4f + age / (float) fall.ticks() * 0.4f);
        }
        Vec3 dir = fall.dir();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        double speed = fall.speed(age);
        double radius = spec.meteoro().raio();
        for (MeteoroSpec.Fumaca fumaca : spec.fumacas()) {
            if (fumaca.ativa()) {
                smoke(level, fumaca, center, dir, side, up, radius, speed);
            }
        }
        MeteoroSpec.Riscos riscos = spec.riscos();
        if (riscos.ativa()) {
            int color = UchihaFx.parseColor(riscos.cor());
            for (int i = 0; i < riscos.porTick(); i++) {
                Vec3 pos = center.subtract(dir.scale(radius * (0.7 + RANDOM.nextDouble() * 0.8))).add(disk(side, up, radius * riscos.espalhamento()));
                Fx.moving(level, ModParticles.streak(color, (float) riscos.comprimento() / 2, 6 + RANDOM.nextInt(10)), pos, dir.scale(-riscos.velocidade()));
            }
        }
    }

    // One smoke set: lados trails around the meteor, each puff drifting outwards so a pair opens into a V.
    private static void smoke(ServerLevel level, MeteoroSpec.Fumaca fumaca, Vec3 center, Vec3 dir, Vec3 side, Vec3 up, double radius, double speed) {
        int sides = Math.max(1, fumaca.lados());
        double drift = speed * Math.tan(Math.toRadians(Math.min(fumaca.abertura(), 170) / 2));
        int color = UchihaFx.parseColor(fumaca.cor());
        for (int j = 0; j < sides; j++) {
            double a = sides == 1 ? Math.PI / 2 : j * Math.PI * 2 / sides;
            Vec3 out = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 base = center.add(out.scale(radius * fumaca.deslocamento())).subtract(dir.scale(radius * 0.3));
            for (int k = 0; k < fumaca.densidade(); k++) {
                Vec3 pos = base.add(gauss().scale(fumaca.largura() * 0.18));
                int life = (int) Math.round(fumaca.duracaoTicks() * (0.8 + RANDOM.nextDouble() * 0.4));
                float size = (float) (fumaca.largura() / 2 * (0.7 + RANDOM.nextDouble() * 0.6));
                Fx.moving(level, ModParticles.puff(scale(color, 0.86 + RANDOM.nextDouble() * 0.14), size, life, 0.97f), pos,
                        out.scale(drift * (0.6 + RANDOM.nextDouble() * 0.5)));
            }
        }
    }

    static void impact(ServerLevel level, MeteoroSpec spec, Vec3 pos) {
        MeteoroSpec.Impacto impacto = spec.impacto();
        Fx.sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.5f);
        Fx.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 5f, 0.6f);
        Fx.sound(level, pos, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.5f);
        double radius = impacto.raio();
        MeteoroSpec.Onda onda = impacto.onda();
        if (onda.ativa() && onda.velocidade() > 0) {
            int color = UchihaFx.parseColor(onda.cor());
            int points = (int) Math.max(24, Math.round(radius * 8));
            int life = (int) Math.ceil(radius * 1.2 / onda.velocidade());
            for (double y : new double[] {0.3, 1.2}) {
                for (int i = 0; i < points; i++) {
                    double a = i * Math.PI * 2 / points;
                    Fx.moving(level, ModParticles.dust(color, onda.tamanho(), life, 1), pos.add(0, y, 0), new Vec3(Math.cos(a), 0, Math.sin(a)).scale(onda.velocidade()));
                }
            }
        }
        MeteoroSpec.Explosao explosao = impacto.explosao();
        if (explosao.ativa()) {
            int color = UchihaFx.parseColor(explosao.cor());
            for (int i = 0; i < explosao.quantidade(); i++) {
                Vec3 velocity = new Vec3(RANDOM.nextGaussian() * 0.5, Math.abs(RANDOM.nextGaussian()) * 0.6 + 0.2, RANDOM.nextGaussian() * 0.5);
                if (i % 3 == 0) {
                    Fx.moving(level, ModParticles.flame(), pos.add(RANDOM.nextGaussian() * 2, 1 + RANDOM.nextDouble() * 2, RANDOM.nextGaussian() * 2), velocity);
                } else {
                    Vec3 at = pos.add(RANDOM.nextGaussian() * radius * 0.25, 1 + RANDOM.nextDouble() * 3, RANDOM.nextGaussian() * radius * 0.25);
                    Fx.moving(level, ModParticles.dust(color, 4), at, velocity);
                }
            }
            for (int i = 0; i < explosao.fumaca(); i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double r = RANDOM.nextDouble() * radius * 0.5;
                double g = 0.75 + RANDOM.nextDouble() * 0.2;
                int gray = rgb(g, g * 0.95, g * 0.92);
                Fx.moving(level, ModParticles.puff(gray, (float) (2 + RANDOM.nextDouble() * 2.5), 60 + RANDOM.nextInt(40), 0.95f),
                        pos.add(Math.cos(a) * r, 1 + RANDOM.nextDouble() * 4, Math.sin(a) * r),
                        new Vec3(Math.cos(a) * 0.25, 0.08 + RANDOM.nextDouble() * 0.1, Math.sin(a) * 0.25));
            }
        }
    }

    private static Vec3 gauss() {
        return new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
    }

    // Random point in a disk of radius r across the fall.
    private static Vec3 disk(Vec3 side, Vec3 up, double r) {
        double rr = Math.sqrt(RANDOM.nextDouble()) * r;
        double a = RANDOM.nextDouble() * Math.PI * 2;
        return side.scale(Math.cos(a) * rr).add(up.scale(Math.sin(a) * rr));
    }

    private static int scale(int color, double k) {
        return rgb(((color >> 16) & 0xFF) / 255.0 * k, ((color >> 8) & 0xFF) / 255.0 * k, (color & 0xFF) / 255.0 * k);
    }

    private static int rgb(double r, double g, double b) {
        return (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }
}
