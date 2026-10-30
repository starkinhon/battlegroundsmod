package com.starkinhon.battlegroundsmod.skill;

// Katon gigante (Shift + Z): Madara jumps, hangs in the sky charging a fireball that keeps growing, and throws it.
// Read from souls_skills/madara.katon_gigante.json. The "tempo" and "animacao" blocks are the body pose (AnimationSpec).
public record KatonGiganteSpec(String skill, String nome, Pulo pulo, Carga carga, Voo voo, Explosao explosao, int planarTicks) {

    // forca: upwards speed of the jump; subirTicks: ticks going up before it stops in the air and starts charging.
    public record Pulo(double forca, int subirTicks) {
    }

    // The ball grows from raioMin to raioMax over maxTicks (a full charge throws it on its own). Let go of the key
    // before that and it goes at the size it had, but never before minTicks. acima: gap between the head and the ball.
    public record Carga(int minTicks, int maxTicks, double raioMin, double raioMax, double acima) {
    }

    // velocidade in blocks per tick; it blows up on the first block or enemy it touches, or after alcance blocks.
    public record Voo(double velocidade, double alcance) {
    }

    // Blast radius = ball radius x raioMult. Damage goes from danoMin (smallest ball) to danoMax (full charge) in the
    // middle, half of that at the edge. With quebrarBlocos (and the mobGriefing rule) it digs a hole crateraMult times the
    // ball radius and, with fogo, leaves the ground around burning.
    public record Explosao(double raioMult, float danoMin, float danoMax, double empurrao, boolean quebrarBlocos, double crateraMult,
            boolean fogo) {
    }
}
