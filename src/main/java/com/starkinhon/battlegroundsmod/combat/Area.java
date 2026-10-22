package com.starkinhon.battlegroundsmod.combat;

import java.util.List;

import com.starkinhon.battlegroundsmod.skill.SkillSpec;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Where a skill lands. Built from the "forma" block of a skill spec, so the shape can be picked in the Forja page.
public interface Area {
    // Missing sizes in the JSON fall back to these.
    double DEFAULT_RADIUS = 5;
    double DEFAULT_HEIGHT = 3;
    double DEFAULT_RANGE = 20;

    List<LivingEntity> targets();

    // Outline in particles, for /poder hitbox.
    void draw(ParticleOptions particle);

    // tipo "cone": opens from the eyes along the aim (comprimento, raioInicio, raioFim).
    // tipo "linha": a straight beam from the eyes along the aim (comprimento, largura as its radius).
    // tipo "circulo": a cylinder around the caster's feet (raio, altura).
    // tipo "esfera": a ball around the middle of the caster's body (raio).
    // tipo "anel": like circulo, but the middle up to raioInterno is left out (raioInterno, raio, altura).
    // tipo "alvo": a cylinder on the block the caster aims at, up to distanciaMax away (distanciaMax, raio, altura).
    static Area of(SkillSpec.Forma forma, LivingEntity caster) {
        ServerLevel level = Combat.level(caster);
        Vec3 eyes = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        double radius = orDefault(forma.raio(), DEFAULT_RADIUS);
        double height = orDefault(forma.altura(), DEFAULT_HEIGHT);
        return switch (forma.tipo() == null ? "cone" : forma.tipo()) {
            case "linha" -> {
                double width = orDefault(forma.largura(), 1);
                yield new Cone(level, caster, eyes, look, forma.comprimento(), forma.comprimento(), width, width);
            }
            case "circulo" -> new Cylinder(level, caster, caster.position(), 0, radius, height);
            case "anel" -> new Cylinder(level, caster, caster.position(), orDefault(forma.raioInterno(), 0), radius, height);
            case "esfera" -> new Sphere(level, caster, caster.position().add(0, 0.9, 0), radius);
            case "alvo" -> new Cylinder(level, caster,
                    Combat.raycastPoint(caster, orDefault(forma.distanciaMax(), DEFAULT_RANGE)), 0, radius, height);
            default -> new Cone(level, caster, eyes, look, forma.comprimento(), forma.comprimento(), forma.raioInicio(), forma.raioFim());
        };
    }

    private static double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }
}
