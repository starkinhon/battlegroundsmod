package com.starkinhon.battlegroundsmod.skill;

// One entry of a skill's "sons" list, as the Forja exports it. It plays at a moment of the skill (momento, like
// "uso" or "impacto"), atrasoTicks later; with repetirACadaTicks it repeats "vezes" times, or for as long as the
// moment lasts when vezes is 0, sliding from pitch to pitchFim. onde is where it comes from ("tela" = no distance).
public record Som(String nome, String som, String momento, int atrasoTicks, int repetirACadaTicks, int vezes, float volume, float pitch,
        float pitchFim, String onde) {

    public static Som of(String nome, String som, String momento, float volume, float pitch, String onde) {
        return new Som(nome, som, momento, 0, 0, 1, volume, pitch, pitch, onde);
    }

    public Som repeating(int every) {
        return new Som(nome, som, momento, atrasoTicks, every, 0, volume, pitch, pitchFim, onde);
    }

    public Som delayed(int ticks) {
        return new Som(nome, som, momento, ticks, repetirACadaTicks, vezes, volume, pitch, pitchFim, onde);
    }
}
