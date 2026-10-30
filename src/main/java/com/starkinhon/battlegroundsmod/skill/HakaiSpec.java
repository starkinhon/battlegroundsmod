package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Bills' ult, Hakai (bills.hakai.json). He appears in front of his target, the camera closes in on his eyes while he
// says "Hakai.", he opens his hand at them in a white and purple flash, and they are erased: the body lights up and
// falls apart into purple sparks from the head down. It kills anything, ignoring armor, shields, totems, creative,
// intangibility and a boss's last-life guard.
// Timeline in ticks from the press: 0 "uso" (he appears, the eye shot starts), tempo.atrasoTicks "mao" (the touch and
// the flash), then desintegrar.atrasoTicks later "hakai" (the body starts to go), and "fim" when nothing is left.
// The "tempo" and "animacao" blocks are also read as an AnimationSpec: the palm goes out at atrasoTicks and is held
// for duracaoTicks.
public record HakaiSpec(String skill, String nome, Tempo tempo, Alvo alvo, Desintegrar desintegrar, Cutscene cutscene, Legenda legenda,
        List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // alcance: how far away the target can be (in view, or the one he is fighting if in front of him).
    // aparecer: he shows up distancia blocks in front of them, facing them, instead of touching them from afar.
    public record Alvo(double alcance, boolean aparecer, double distancia) {
    }

    // brilhoTicks: the body lights up white and purple; then it falls apart over duracaoTicks.
    // faiscas: how many purple sparks the body breaks into; vento: how hard they are blown aside as they rise.
    // cor, cor2: the sparks and the edge (cor2 is the white of the core).
    public record Desintegrar(int atrasoTicks, int brilhoTicks, int duracaoTicks, int faiscas, double subida, double vento, String cor,
            String cor2) {
    }

    // Shots, for Bills and his target only: "olhos" (his eyes, very close, as he speaks) until the touch, "mao" (from the
    // side, both of them and his open hand) until the body starts to go, "alvo" (the target falling apart), then
    // depoisTicks more and a glide back to the player's own view over voltarTicks.
    public record Cutscene(boolean ativa, double distanciaOlhos, double ladoOlhos, double distanciaMao, double distanciaAlvo, int depoisTicks,
            int voltarTicks, boolean barrasPretas, boolean intangivel) {
    }

    // The line he says, at the bottom of the screen like a subtitle, from inicioTick to fimTick.
    public record Legenda(String texto, String cor, int inicioTick, int fimTick, double tamanho) {
    }

    public static final String SKILL = "bills.hakai";

    public static HakaiSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, HakaiSpec.class, awakened);
    }

    // When the body starts to light up and fall apart.
    public int dissolveTick() {
        return Math.max(1, tempo.atrasoTicks()) + Math.max(0, desintegrar.atrasoTicks());
    }

    // When nothing is left of the target: the kill lands here.
    public int killTick() {
        return dissolveTick() + Math.max(1, desintegrar.brilhoTicks()) + Math.max(1, desintegrar.duracaoTicks());
    }

    // When the camera starts gliding back.
    public int cutsceneEnd() {
        return killTick() + Math.max(0, cutscene.depoisTicks());
    }
}
