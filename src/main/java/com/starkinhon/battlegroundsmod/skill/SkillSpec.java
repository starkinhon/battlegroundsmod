package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// A skill tuned in the Forja de Skills page ("souls-skill/1" format). Field names match the JSON the page exports,
// so a new export can be pasted over the file in data/battlegroundsmod/souls_skills/ as is.
public record SkillSpec(String skill, String nome, Tempo tempo, Origem origem, Direcao direcao, Forma forma, Camadas camadas, Animacao animacao,
        List<Som> sons) {

    // atraso: wind-up before the effect starts; duracao: how long it lasts; crescimento: ticks to reach full length.
    public record Tempo(int atrasoTicks, int duracaoTicks, int crescimentoTicks) {
        public int totalTicks() {
            return atrasoTicks + duracaoTicks;
        }
    }

    // Offset from the eyes, in blocks, along the aim direction, straight up, and to the right.
    public record Origem(String ancora, double frente, double altura, double lado) {
    }

    // Degrees added to where the player looks: giro turns right, inclinacao tilts up.
    public record Direcao(double giro, double inclinacao) {
    }

    // tipo: "cone", "linha", "circulo", "esfera", "anel" or "alvo" (see combat/Area). The Forja only exports the
    // fields of the chosen shape, so the others may be missing.
    public record Forma(String tipo, double comprimento, double raioInicio, double raioFim, Double largura, Double raio,
            Double raioInterno, Double altura, Double distanciaMax) {
    }

    public record Camadas(Explosao inicio, Jato chamas, Volume fogo, Fumaca fumaca, Lava lava) {
    }

    public record Explosao(boolean ativa, int quantidade, double espalhamento, double velocidade) {
    }

    public record Jato(boolean ativa, int porTick, double velocidade, double abertura) {
    }

    // concentracao: below 1 packs puffs towards the tip, above 1 towards the mouth.
    public record Volume(boolean ativa, int porTick, float tamanhoInicio, float tamanhoFim, double preenchimento, double concentracao, List<String> cores) {
    }

    // Emitted at the tip every intervaloTicks. espalhamento is a fraction of the cone radius there.
    public record Fumaca(boolean ativa, int quantidade, int intervaloTicks, double espalhamento, double altura) {
    }

    public record Lava(boolean ativa, int quantidade, int intervaloTicks, double espalhamento) {
    }

    // Two body poses: preparo during the wind-up, sopro while the effect runs. Angles in degrees.
    public record Animacao(int transicaoTicks, Pose preparo, Pose sopro) {
    }

    public record Braco(double frente, double abrir) {
        static final Braco ZERO = new Braco(0, 0);

        Braco lerp(Braco to, double t) {
            return new Braco(frente + (to.frente - frente) * t, abrir + (to.abrir - abrir) * t);
        }
    }

    public record Pose(double cabeca, double tronco, Braco bracoDir, Braco bracoEsq) {
        public static final Pose ZERO = new Pose(0, 0, Braco.ZERO, Braco.ZERO);

        Pose lerp(Pose to, double t) {
            return new Pose(cabeca + (to.cabeca - cabeca) * t, tronco + (to.tronco - tronco) * t,
                    bracoDir.lerp(to.bracoDir, t), bracoEsq.lerp(to.bracoEsq, t));
        }
    }
}
