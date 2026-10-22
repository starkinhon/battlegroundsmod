package com.starkinhon.battlegroundsmod.skill;

// Camera in front of the caster's face, for the caster and everyone hit. Distances in blocks from the eyes.
// Timeline: push in from distanciaInicio to distanciaFim over aproximarTicks, hold, then glide back to the
// normal view over the last voltarTicks. The skill's particles fire at particulasTicks.
// The last three may be missing from the JSON; they then follow the cutscene length.
public record CutsceneSpec(boolean ativa, int duracaoTicks, double distanciaInicio, double distanciaFim, double altura,
        boolean esconderHud, Integer aproximarTicks, Integer voltarTicks, Integer particulasTicks) {

    // Lets the client read just the cutscene block of any skill file.
    public record Holder(CutsceneSpec cutscene) {
    }

    public int zoomTicks() {
        return aproximarTicks != null ? aproximarTicks : Math.round(duracaoTicks * 0.4f);
    }

    public int returnTicks() {
        return voltarTicks != null ? voltarTicks : Math.round(duracaoTicks * 0.3f);
    }

    // A little after the zoom has landed on the face.
    public int particlesTick() {
        return particulasTicks != null ? particulasTicks : zoomTicks() + 3;
    }
}
