package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Gojo's ult, Expansão de Domínio: Vazio Infinito (gojo.dominio.json). He makes the hand sign (the cutscene closes in on
// his face and hand while the two lines show), then a black sphere bursts out of him and swallows everything within
// dominio.raio blocks. Inside it there is only the void: endless stars, streams of light flowing into a giant eye of
// light far behind him and a dark mirror floor. Anyone caught inside (except Gojo) gets infinite information poured into
// their head: they can't move, turn, attack or use skills while the domain lasts, and take acerto.dano every
// acerto.aCadaTicks. When it ends the void cracks with light and shatters back into the world.
// Timeline in ticks from the press: "uso" (the hand sign, the "tempo" block is the AnimationSpec), dominio.inicioTick
// "expandir" (the sphere starts growing), + expandirTicks "acerto" (the domain is complete, the sure hit lands),
// + duracaoTicks "fechar" (the void cracks), + fecharTicks gone.
public record DominioSpec(String skill, String nome, Tempo tempo, Dominio dominio, Acerto acerto, Visual visual, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // raio: blocks around where Gojo stood. pararUsuario: Gojo stays still while he makes the sign (until it's complete).
    public record Dominio(double raio, int inicioTick, int expandirTicks, int duracaoTicks, int fecharTicks, boolean pararUsuario) {
    }

    // paralisar: everyone inside is held still (Freeze) while the domain lasts, also anyone who walks in later.
    // dano: damage every aCadaTicks to each of them (0 = none, Gojo does the beating).
    public record Acerto(boolean paralisar, double dano, int aCadaTicks) {
    }

    // The look of the void. corAnel: the eye's ring; corBrilho: its glow and the streams; corFundo: the far haze.
    // estrelas: how many; olhoTamanho: the eye's size (blocks, seen 95 blocks away); olhoAltura: degrees above the horizon.
    // telaVitima: how strong the "information" flicker is on the screen of those caught (0 turns it off).
    public record Visual(String corAnel, String corBrilho, String corFundo, int estrelas, double olhoTamanho, double olhoAltura, double telaVitima) {
    }

    public static final String SKILL = "gojo.dominio";

    public static DominioSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, DominioSpec.class, awakened);
    }

    public int expandTick() {
        return Math.max(0, dominio.inicioTick());
    }

    public int fullTick() {
        return expandTick() + Math.max(1, dominio.expandirTicks());
    }

    public int closeTick() {
        return fullTick() + Math.max(1, dominio.duracaoTicks());
    }

    public int closeTicks() {
        return Math.max(2, dominio.fecharTicks());
    }

    public int goneTick() {
        return closeTick() + closeTicks();
    }
}
