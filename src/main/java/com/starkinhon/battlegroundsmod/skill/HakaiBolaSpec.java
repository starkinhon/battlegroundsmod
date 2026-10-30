package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The hakai ball (bills.hakai_bola.json): what Bills' V does to a target that still has more than the Hakai's
// hitkill.limiteVida of its life. He raises his hand and a big sphere of hakai forms over it (dark magenta, black rim,
// black lines around it, pink glow). It chases the target slowly through anything, wherever they go, for
// bola.perseguirTicks and then fades. If it reaches them they are trapped inside, glowing, taking dano every
// prisao.intervaloTicks for prisao.duracaoTicks, and then it blows up.
// Timeline in ticks from the press: 0 "uso" (the hand goes up), tempo.atrasoTicks "lancar" (the ball is out and starts
// the chase), "prender" when it catches the target, "explosao" at the end of the trap, "sumir" if the chase runs out.
// The "tempo" and "animacao" blocks are also read as an AnimationSpec.
public record HakaiBolaSpec(String skill, String nome, Tempo tempo, Alvo alvo, Bola bola, Prisao prisao, Explosao explosao, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    public record Alvo(double alcance) {
    }

    // raio: the sphere's radius (2.7 = three times as tall as a player). acima: how high over his hand it forms.
    // velocidade: blocks per tick it chases at; curva: how fast it turns towards the target (0 to 1 each tick).
    // perseguirTicks: how long it chases before it fades; pegar: how deep the target has to be in it to be caught (0 to
    // 1 of the radius).
    public record Bola(double raio, double acima, double velocidade, double curva, int perseguirTicks, double pegar) {
    }

    public record Prisao(int duracaoTicks, int intervaloTicks, float dano) {
    }

    public record Explosao(float dano, double raio, double empurrao, double altura) {
    }

    public static final String SKILL = "bills.hakai_bola";

    public static HakaiBolaSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, HakaiBolaSpec.class, awakened);
    }
}
