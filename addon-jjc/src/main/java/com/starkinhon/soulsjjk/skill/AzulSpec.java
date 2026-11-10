package com.starkinhon.soulsjjk.skill;

import java.util.List;

// Blue (gojo.azul.json), Gojo's Z. A point of negative energy that pulls everything into it.
// Tap: a small orb leaves the hand, flies to where he aims and locks there, pulling whoever is near slowly, for
// clique.duracaoTicks. Held: the orb grows between his hands up to orbe.escalaMax times (after segurado.carregarTicks);
// let go, it follows his aim (segurado.mira to segurado.miraMax blocks ahead, further the longer it was held; it lags softly behind, see Controle) for segurado.duracaoTicks,
// pulls much harder, tears blocks out of the ground and crushes them in the middle (only with the gamerule
// soulsBlockDestruction), bends the space around it, then implodes. Charged less than full, everything is in between.
// Held longer than segurado.decidirTicks counts as held. visual.estilo: the orb's look in souls_vfx shape 18 (0 mod glow,
// 1 cartoon, 2 realism, the one in the game, 3 mix/glass, kept for later). visual.estiloShift is unused while
// Shift + Z has no variant (Azul.useMix draws it).
// Timeline: "uso" (press), "lancar" (the tap's orb leaves), "travar" (it locks), "fim" (the tap's orb is gone),
// "carregar" (held), "soltar" (let go), "esmagar" (a block crushed), "fimForte" (the held one implodes).
// Also "travarControle", "seguirDeNovo", "afastar", "aproximar", "atirar", "explodir", "orbitar" (see Controle).
// The "tempo" and "animacao" blocks are also read as an AnimationSpec (the aim and push of the hand).
public record AzulSpec(String skill, String nome, Tempo tempo, Visual visual, Orbe orbe, Clique clique, Segurado segurado, Controle controle,
        Orbita orbita, Tiro tiro, Singularidade singularidade, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // estiloShift: the look of Shift + Z (the same skill otherwise).
    public record Visual(int estilo, Integer estiloShift) {
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
    // mira: how far ahead of the eyes it follows at the lightest charge, miraMax fully charged (in between with the charge;
    // the wheel moves it after, see Controle); maxSegurarTicks: bosses only (players let go with the key); raioPuxar/puxar/
    // maxPuxar like the tap's; raioBlocos: how far from it blocks are torn out (blocosPorTick tries a tick, up to
    // maxBlocos); dano every danoACadaTicks to whoever is inside it, danoFim to everyone in it when it implodes.
    public record Segurado(int decidirTicks, int carregarTicks, int maxSegurarTicks, int duracaoTicks, double mira, Double miraMax,
            double raioPuxar, double puxar, double maxPuxar, double raioBlocos, int blocosPorTick, int maxBlocos, float dano,
            int danoACadaTicks, float danoFim) {
    }

    // While the held one is out, Z again: a tap parks it where it is (or sends it back to following the aim); held while it
    // follows the aim it is shot (tiro); held while parked it starts circling him (orbita), on its own until it ends (Z no longer changes it).
    // seguir: how hard it is pulled towards the aim each tick, amortecer: how much of its speed it keeps (the lag and the
    // slowness when following), velocidadeMax: blocks per tick at most. The mouse wheel moves it away or closer by
    // passoScroll blocks, between alcanceMin and alcanceMax (the hotbar doesn't scroll meanwhile).
    public record Controle(double seguir, double amortecer, double velocidadeMax, double alcanceMin, double alcanceMax, double passoScroll) {
    }

    // raio: blocks from him; velocidade: radians per tick; altura: above his feet.
    public record Orbita(double raio, double velocidade, double altura) {
    }

    // Shot towards the aim, chasing whoever is under the crosshair (perseguir: how hard it turns to them), speeding up
    // from velocidade by aceleracao to velocidadeMax; explodes on what it reaches, on a wall, at alcance or after maxTicks.
    // dano to everyone within raioExplosao (times the orb's size); forcaExplosao breaks blocks (with the gamerule on).
    public record Tiro(double velocidade, double aceleracao, double velocidadeMax, double perseguir, double alcance, int maxTicks, float dano,
            double raioExplosao, float forcaExplosao, double empurrao) {
    }

    // The held one touching the ground (following, parked or circling), charged at least cargaMin: its middle is a
    // singularity that eats the ground away within raio blocks, not all at once: each tick tentativasPorTick random
    // spots are tried and a block goes with chance (1 - distance/raio)^curva (near the middle almost at once, the edge
    // slowly), at most maxBlocosPorTick. contato: blocks past its surface that count as touching. Every particulasACada-th
    // block shows its break. Only with the gamerule soulsBlockDestruction. Timeline: "singularidade" (eating, every few ticks).
    public record Singularidade(double cargaMin, double raio, int tentativasPorTick, int maxBlocosPorTick, double curva, double contato,
            int particulasACada) {
    }

    public static final String SKILL = "gojo.azul";

    public static AzulSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, AzulSpec.class, awakened);
    }
}
