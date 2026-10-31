package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Destruction Energy (bills.destruction_energy.json), Bills' Z. While the key is held a ball of light with rings of
// energy turning around it forms over his raised hand (carga.formarTicks to full size) and stays there; when the key
// comes up (or after carga.maxTicks) it is thrown. It flies fast and homes in: on the target he had locked when it left
// the hand, else it follows his crosshair until someone comes within alvo.detectar blocks of it, then goes after them.
// On the first thing it touches it blows up: explosao.dano in explosao.raio blocks and a crater of explosao.raioBlocos.
// Timeline in ticks from the press: 0 "uso" (the hand goes up and the ball forms), "lancar" when it is thrown,
// "explosao" where it blows up, "sumir" if it flies bola.alcance blocks without touching anything.
// The "tempo" and "animacao" blocks are also read as an AnimationSpec (tempo.atrasoTicks holds the raised hand).
public record DestructionEnergySpec(String skill, String nome, Tempo tempo, Alvo alvo, Carga carga, Bola bola, Explosao explosao, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // alcance: how far a target can be locked when the ball is thrown; detectar: how close someone has to come to a ball
    // flying with no target for it to go after them.
    public record Alvo(double alcance, double detectar) {
    }

    // formarTicks: the ball grows to full size over the hand; minTicks: a tap still throws it after this long; maxTicks:
    // thrown on its own when held this long. acima: how high over the hand its center is.
    public record Carga(int formarTicks, int minTicks, int maxTicks, double acima) {
    }

    // raio: the ball's radius; velocidade: blocks per tick; curva: how fast it turns towards where it is going (0 to 1
    // each tick); alcance: blocks it flies before it fades.
    public record Bola(double raio, double velocidade, double curva, double alcance) {
    }

    // dano at the center, half at the edge of raio; empurrao/altura: knockback away and up; raioBlocos: the crater (0 =
    // no blocks broken; needs the mobGriefing gamerule); tremor: camera shake for whoever is close.
    public record Explosao(float dano, double raio, double empurrao, double altura, double raioBlocos, float tremor) {
    }

    public static final String SKILL = "bills.destruction_energy";

    public static DestructionEnergySpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, DestructionEnergySpec.class, awakened);
    }
}
