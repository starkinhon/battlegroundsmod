package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Black Box (aizen.black_box.json), Aizen's Z: Hado #90, Kurohitsugi. While the key is held a box of black light with
// violet edges builds itself around the target (shaped to the target's own size), its walls rising from the ground; the
// longer it is held the bigger it grows, up to caixa.escalaMax times the target's size (after caixa.crescerTicks), and the
// harder it hits. When the key comes up (or after caixa.maxTicks) it shuts, lances of the same black stab through it
// (lancas.golpes hits over lancas.duracaoTicks) and then it caves in and shatters.
// Damage goes from dano.minimo (tap, 1x) to dano.maximo (full charge, escalaMax): dano.lancas of it split over the lances,
// the rest on the shatter. Everyone inside the box is hit, not only the target.
// Timeline in ticks: 0 "uso" (press), "fechar" when it shuts, "lanca" on each lance hit, "quebrar" when it shatters.
// The "tempo" and "animacao" blocks are also read as an AnimationSpec (tempo.atrasoTicks holds the hand out).
public record BlackBoxSpec(String skill, String nome, Tempo tempo, Alvo alvo, Caixa caixa, Lancas lancas, Dano dano, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // alcance: how far away the target can be.
    public record Alvo(double alcance) {
    }

    // folga: room left around the target inside the box (blocks); subirTicks: how long the walls take to rise while it is
    // held; minTicks: a tap still casts it after this long; crescerTicks: held this long it reaches escalaMax; maxTicks:
    // shuts on its own when held this long; fecharTicks: how long it takes to shut once let go.
    public record Caixa(double folga, int subirTicks, int minTicks, int crescerTicks, int maxTicks, double escalaMax, int fecharTicks) {
    }

    // golpes: lance hits; duracaoTicks: over how long; quebrarTicks: how long the box takes to cave in before it shatters.
    public record Lancas(int golpes, int duracaoTicks, int quebrarTicks) {
    }

    // minimo/maximo: total damage at 1x and at escalaMax; lancas: the share of it the lances deal (0 to 1).
    public record Dano(float minimo, float maximo, float lancas) {
    }

    public static final String SKILL = "aizen.black_box";

    public static BlackBoxSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, BlackBoxSpec.class, awakened);
    }
}
