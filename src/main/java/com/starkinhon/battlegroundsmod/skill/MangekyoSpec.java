package com.starkinhon.battlegroundsmod.skill;

// Mangekyo: an eye-contact stun. Same "souls-skill/1" layout as the other skills, tuned in the Forja de Skills page.
public record MangekyoSpec(String skill, String nome, Tempo tempo, SkillSpec.Forma forma, Efeitos efeitos, Cutscene cutscene) {

    // atraso: ticks between pressing the key and the stun landing (the cutscene plays meanwhile).
    public record Tempo(int atrasoTicks) {
    }

    // Levels start at 1; 0 turns an effect off. travarSkills puts every skill of a hit player on cooldown.
    // atordoarTicks freezes the target in place for that long (0 = no freeze).
    public record Efeitos(int duracaoTicks, int fadiga, int cegueira, int lentidao, boolean travarSkills, int atordoarTicks) {
    }

    // Camera in front of the caster's face for the caster and everyone hit. Distances in blocks from the eyes.
    public record Cutscene(boolean ativa, int duracaoTicks, double distanciaInicio, double distanciaFim, double altura, boolean esconderHud) {
    }
}
