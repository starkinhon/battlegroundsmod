package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// V, the Destruction Touch (bills.destruction_touch.json). Bills rises voo.altura blocks over the ground in
// voo.subirTicks and hangs up there, untouchable (no damage or knockback, but skills still catch him). After
// tempo.atrasoTicks he lets a tiny white ball drop from his finger: it falls at bola.velocidade blocks per tick to the
// ground under the target (or under him with nobody around) and lands softly. The ground lights up and cracks for
// impacto.brilhoTicks, pillars of light burst out of it for impacto.pilaresTicks, and then everything blows up in a pink
// and white plasma explosion with a reach of explosao.raio blocks: damage falling from danoCentro to danoBorda, anyone
// left under the Hakai's life limit is pulverized, attacks in flight inside it are erased and the ground is gone in a
// crater (no drops). He glides down after voo.ficarTicks more.
// Stunned for cancelarStunTicks or more, grabbed, frozen or sealed before he lets go of the ball: nothing drops and he
// falls. Once the ball is out it lands anyway (a hakaizinho can still erase it on the way down).
// Timeline: 0 "uso" (rising), atrasoTicks "soltar" (the ball leaves his finger), "queda" while it falls (its life is the
// fall), "toque" when it lands, + brilhoTicks "pilares", + pilaresTicks "explosao", "cancelar" when knocked out of it.
// The "tempo" and "animacao" blocks are also read as an AnimationSpec.
public record DestructionTouchSpec(String skill, String nome, Tempo tempo, Alvo alvo, Voo voo, Bola bola, Impacto impacto,
        Explosao explosao, int cancelarStunTicks, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    public record Alvo(double alcance) {
    }

    // ficarTicks: how long he stays up after the explosion before gliding down for planarTicks.
    public record Voo(double altura, int subirTicks, int ficarTicks, int planarTicks) {
    }

    // The ball's speed must match the "velocidade" of the "bolinha" effect in the vfx list, which draws it falling.
    public record Bola(double velocidade, double raio, double alturaMaxima) {
    }

    public record Impacto(int brilhoTicks, int pilaresTicks) {
    }

    // The crater is a bowl raioCratera wide and profundidadeCratera deep, pulverized over crateraTicks.
    public record Explosao(float danoCentro, float danoBorda, double raio, double empurrao, boolean quebrarBlocos, double raioCratera,
            double profundidadeCratera, int crateraTicks) {
    }

    public static final String SKILL = "bills.destruction_touch";

    public static DestructionTouchSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, DestructionTouchSpec.class, awakened);
    }

    public int dropTick() {
        return Math.max(voo.subirTicks(), tempo.atrasoTicks());
    }
}
