package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Shift + V, the Destruction Touch on a planet (bills.destruction_touch_planeta.json). Bills snaps his fingers and is
// gone to outer space in a cutscene only he sees (client/PlanetCutscene), the anime's sequence with the square Earth
// of the C: a pink glow gathers on one half of the planet (aura), a white point lights up there and bursts into a
// blue-white starburst (clarao), everything goes quiet (pausa), then a huge white shockwave disc cuts through it, that
// half of the planet goes off in purple lightning and breaks apart into a lilac cloud with black rocks flying out
// (explosao), seen last from behind his back (costas). Everyone else sees him vanish in a flash.
// Back in the world, the target glows for avisoTicks and the same blast hits where it is: explosao, as in the V.
// Timeline in ticks from the press: 0 "uso" (the snap), subirTicks "espaco" (he is gone), then the phases above;
// "voltar" when he is back, + avisoTicks "impacto".
// The "tempo" and "animacao" blocks are also read as an AnimationSpec (the snap: atrasoTicks, duracaoTicks).
public record DestructionPlanetSpec(String skill, String nome, Tempo tempo, Alvo alvo, EsferaSpec.Espaco espaco, Planeta planeta,
        DestructionTouchSpec.Explosao explosao, Cutscene cutscene, List<Som> sons) {

    public record Tempo(int subirTicks, int auraTicks, int claraoTicks, int pausaTicks, int explosaoTicks, int costasTicks, int avisoTicks,
            int atrasoTicks, int duracaoTicks) {
    }

    public record Alvo(double alcance) {
    }

    // How the planet breaks: pedacos cubes per side (only the half facing Bills flies apart), how far they fly (in
    // planet sizes), the cloud's size (in planet sizes) and how many black rocks fly out.
    public record Planeta(int pedacos, double voo, double nuvem, int rochas) {
    }

    public record Cutscene(boolean ativa, boolean barrasPretas, boolean intangivel) {
    }

    public static final String SKILL = "bills.destruction_touch_planeta";

    public static DestructionPlanetSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, DestructionPlanetSpec.class, awakened);
    }

    public int spaceTick() {
        return Math.max(1, tempo.subirTicks());
    }

    public int flashTick() {
        return spaceTick() + Math.max(1, tempo.auraTicks());
    }

    public int pauseTick() {
        return flashTick() + Math.max(1, tempo.claraoTicks());
    }

    public int blastTick() {
        return pauseTick() + Math.max(0, tempo.pausaTicks());
    }

    public int backTick() {
        return blastTick() + Math.max(1, tempo.explosaoTicks());
    }

    // The cutscene ends here: back in the world.
    public int returnTick() {
        return backTick() + Math.max(0, tempo.costasTicks());
    }

    public int impactTick() {
        return returnTick() + Math.max(1, tempo.avisoTicks());
    }
}
