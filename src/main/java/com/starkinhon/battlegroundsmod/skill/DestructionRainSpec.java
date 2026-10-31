package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Shift + Z, the rain of Destruction Energy (bills.destruction_energy_chuva.json). Bills rises voo.altura blocks over
// the ground in voo.subirTicks and stays up there, untouchable (no damage or knockback, but skills still catch him).
// Balls of destruction energy come out of his body on every side, bolas.quantidade of them over bolas.criarTicks, and
// hang around him between bolas.distanciaMin and distanciaMax blocks away. After bolas.pausaTicks they are shot one
// every bolas.intervaloTicks at the target, homing like the Z; then he glides down for voo.planarTicks.
// Stunned for cancelarStunTicks or more, grabbed, frozen or sealed while up there: the balls still around him are gone
// and he falls, the skill over. Each ball blows up like the Z with this file's explosao.
// Timeline: 0 "uso" (rising), "criar" each time a ball comes out of him, "lancar" each shot, "explosao" each hit,
// "sumir" for balls that fade, "cancelar" when he is knocked out of it.
public record DestructionRainSpec(String skill, String nome, Tempo tempo, Alvo alvo, Voo voo, Bolas bolas, Tiro tiro,
        DestructionEnergySpec.Explosao explosao, int cancelarStunTicks, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    public record Alvo(double alcance, double detectar) {
    }

    public record Voo(double altura, int subirTicks, int planarTicks) {
    }

    public record Bolas(int quantidade, int criarTicks, double distanciaMin, double distanciaMax, double raio, int pausaTicks, int intervaloTicks) {
    }

    // How the shot balls fly: the same as the Z's bola (velocidade, curva, alcance); raio comes from bolas.raio.
    public record Tiro(double velocidade, double curva, double alcance) {
    }

    public static final String SKILL = "bills.destruction_energy_chuva";

    public static DestructionRainSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, DestructionRainSpec.class, awakened);
    }
}
