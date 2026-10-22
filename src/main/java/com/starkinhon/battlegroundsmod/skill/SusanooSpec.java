package com.starkinhon.battlegroundsmod.skill;

// Susanoo counter: for a short window, the next hit is blocked and a spectral arm grabs the attacker,
// crushes them and throws them away. Same "souls-skill/1" layout as the other skills.
public record SusanooSpec(String skill, String nome, Janela janela, Agarrar agarrar, Esmagar esmagar, Arremesso arremesso, Visual visual) {

    // How long the counter stays ready. Attackers farther than alcanceMax (e.g. archers) are not grabbed.
    public record Janela(int duracaoTicks, double alcanceMax) {
    }

    // The arm takes alcancarTicks to bring the attacker to the hand, which sits frente blocks ahead and altura up.
    public record Agarrar(int alcancarTicks, double frente, double altura) {
    }

    // golpes squeezes spread over duracaoTicks, each dealing dano.
    public record Esmagar(int duracaoTicks, int golpes, float dano) {
    }

    // Throw along the caster's facing: forca is horizontal speed, altura vertical speed.
    public record Arremesso(double forca, double altura, float dano) {
    }

    public record Visual(String cor) {
    }
}
