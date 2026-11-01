package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Gojo's ult, Expansão de Domínio: Vazio Infinito (gojo.dominio.json). It plays out in five phases, in ticks from the press:
// - choque (fases.choque): Gojo makes the hand sign (the cutscene closes in on his face and hand). At dominio.ondaTick a
//   shockwave leaves him and stops in the air every attack in flight within dominio.raioChoque blocks (fireballs, slashes,
//   meteors, Hakai balls, energy, spheres), and he takes no damage (Infinity) until the void is complete.
// - branco (fases.branco): a dome of soft white light, like a cloud, swallows everything within dominio.raio blocks,
//   the ground too: a full sphere, its lower half shows if the ground around it is gone. The frozen attacks are erased.
//   Everyone the white reaches is caught.
// - tunel (fases.tunel): inside, streaks of violet, magenta, red and blue light rush past into a far vanishing point,
//   faster and faster, with the screen stretching with speed. From outside it is already a smooth black ball.
// - vazio (fases.vazio): the void. No walls, no floor, endless depth, a black hole turning far away, white ink drifting
//   by, a ring of water under everyone every 2 s. Those caught are paralyzed, take acerto.dano every acerto.aCadaTicks and get
//   infinite information on their screen (only them, never Gojo).
// - quebra (fases.quebra): it cracks everywhere and shatters like glowing glass, black and white, until nothing is left.
public record DominioSpec(String skill, String nome, Tempo tempo, Fases fases, Dominio dominio, Acerto acerto, Visual visual, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // How long each phase lasts, in ticks.
    public record Fases(int choque, int branco, int tunel, int vazio, int quebra) {
    }

    // raio: the domain's radius around where Gojo stood. raioChoque: how far the shockwave stops attacks. ondaTick: when
    // the shockwave leaves him. pararUsuario: Gojo stays still until the white has closed.
    public record Dominio(double raio, double raioChoque, int ondaTick, boolean pararUsuario) {
    }

    // paralisar: everyone inside is held still (Freeze) from when the white reaches them to the end, also anyone who
    // walks in later. dano: damage every aCadaTicks to each of them during the void (0 = none, Gojo does the beating).
    public record Acerto(boolean paralisar, double dano, int aCadaTicks) {
    }

    // corBuraco: the black hole's glowing rings. corNevoa: the far haze of the void. estrelas: how many.
    // buracoTamanho: the black hole's size (blocks, seen 95 away). buracoAltura: degrees above the horizon.
    // telaVitima: how strong the information on the screen of those caught is (0 turns it off).
    public record Visual(String corBuraco, String corNevoa, int estrelas, double buracoTamanho, double buracoAltura, double telaVitima) {
    }

    public static final String SKILL = "gojo.dominio";
    // How long the shockwave takes to reach raioChoque. The white takes most of its phase to reach raio, then holds.
    public static final int WAVE_TICKS = 14;

    public static DominioSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, DominioSpec.class, awakened);
    }

    public int ondaTick() {
        return Math.max(0, Math.min(dominio.ondaTick(), whiteTick() - 1));
    }

    public int whiteTick() {
        return Math.max(1, fases.choque());
    }

    public int tunnelTick() {
        return whiteTick() + Math.max(4, fases.branco());
    }

    public int voidTick() {
        return tunnelTick() + Math.max(1, fases.tunel());
    }

    public int closeTick() {
        return voidTick() + Math.max(1, fases.vazio());
    }

    public int closeTicks() {
        return Math.max(10, fases.quebra());
    }

    public int goneTick() {
        return closeTick() + closeTicks();
    }

    public double radius() {
        return Math.max(4, dominio.raio());
    }

    // How far the shockwave has gone t ticks after the press (0 before it leaves).
    public double waveRadius(double t) {
        double k = (t - ondaTick()) / WAVE_TICKS;
        if (k < 0) {
            return 0;
        }
        k = Math.min(1, k);
        return Math.max(1, dominio.raioChoque()) * (1 - Math.pow(1 - k, 3));
    }

    // How far the white dome has grown t ticks after the press.
    public double whiteRadius(double t) {
        double k = (t - whiteTick()) / Math.max(4, fases.branco() * 0.7);
        if (k < 0) {
            return 0;
        }
        k = Math.min(1, k);
        return radius() * (1 - Math.pow(1 - k, 2.4));
    }
}
