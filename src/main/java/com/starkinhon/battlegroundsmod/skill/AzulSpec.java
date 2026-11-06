package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Blue (gojo.azul.json), Gojo's Z. A point of negative energy that pulls everything into it.
// Tap: a small orb leaves the hand, flies to where he aims and locks there, pulling whoever is near slowly, for
// clique.duracaoTicks. Held: the orb grows between his hands up to orbe.escalaMax times (after segurado.carregarTicks);
// let go, it follows his aim (segurado.mira blocks ahead, segurado.seguir of the way each tick) for segurado.duracaoTicks,
// pulls much harder, tears blocks out of the ground and crushes them in the middle (only with the gamerule
// soulsBlockDestruction), bends the space around it, then implodes. Charged less than full, everything is in between.
// Held longer than segurado.decidirTicks counts as held. visual.estilo: the orb's look in souls_vfx shape 18 (0 mod glow,
// 1 cartoon, 2 realism, 3 mix/glass).
// Timeline: "uso" (press), "lancar" (the tap's orb leaves), "travar" (it locks), "fim" (the tap's orb is gone),
// "carregar" (held), "soltar" (let go), "esmagar" (a block crushed), "fimForte" (the held one implodes).
// The "tempo" and "animacao" blocks are also read as an AnimationSpec (the aim and push of the hand).
public record AzulSpec(String skill, String nome, Tempo tempo, Visual visual, Orbe orbe, Clique clique, Segurado segurado, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    public record Visual(int estilo) {
    }

    // raio: the tap's orb (blocks); escalaMax: how many times bigger fully charged.
    public record Orbe(double raio, double escalaMax) {
    }

    // velocidade: blocks per tick flying; distancia: how far it locks (or where the aim hits a block); raioPuxar: how far
    // it pulls; puxar: pull speed at the edge (more near it, up to maxPuxar); dano every danoACadaTicks to whoever is
    // stuck on it.
    public record Clique(double velocidade, double distancia, int duracaoTicks, double raioPuxar, double puxar, double maxPuxar, float dano,
            int danoACadaTicks) {
    }

    // decidirTicks: held longer than this it charges; carregarTicks: to full; maxSegurarTicks: let go on its own;
    // mira: how far ahead of the eyes it follows; seguir: the share of the way it moves each tick; raioPuxar/puxar/
    // maxPuxar like the tap's; raioBlocos: how far from it blocks are torn out (blocosPorTick tries a tick, up to
    // maxBlocos); dano every danoACadaTicks to whoever is inside it, danoFim to everyone in it when it implodes.
    public record Segurado(int decidirTicks, int carregarTicks, int maxSegurarTicks, int duracaoTicks, double mira, double seguir,
            double raioPuxar, double puxar, double maxPuxar, double raioBlocos, int blocosPorTick, int maxBlocos, float dano,
            int danoACadaTicks, float danoFim) {
    }

    public static final String SKILL = "gojo.azul";

    public static AzulSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, AzulSpec.class, awakened);
    }
}
