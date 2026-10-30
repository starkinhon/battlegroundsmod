package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Shift + V, the hakaizinho (bills.hakaizinho.json): a tiny pink spark of hakai flicked from Bills' fingertip, flying
// straight where he aims. It goes through everything and destroys what it touches: blocks along its path turn to
// nothing, anyone hit takes dano (and is erased like the Hakai if already under its hitkill.limiteVida), and attacks in
// flight are pulverized (Madara's meteor, the giant fireball, the Susanoo slash, another Bills' sphere...).
// Timeline in ticks from the press: 0 "uso" (the finger goes up), tempo.atrasoTicks "lancar" (it flies), "pulverizar"
// where each attack it erases was, "acerto" on whoever it hits.
// The "tempo" and "animacao" blocks are also read as an AnimationSpec.
public record HakaizinhoSpec(String skill, String nome, Tempo tempo, Voo voo, Destruir destruir, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // velocidade: blocks per tick; alcance: how far it goes; raio: how wide its touch is.
    public record Voo(double velocidade, double alcance, double raio) {
    }

    // raioBlocos: the tunnel it digs through blocks (0 for none, mobGriefing has to be on). raioAtaques: how close an
    // attack in flight has to pass to be pulverized.
    public record Destruir(float dano, double raioBlocos, double raioAtaques) {
    }

    public static final String SKILL = "bills.hakaizinho";

    public static HakaizinhoSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, HakaizinhoSpec.class, awakened);
    }
}
