package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Ctrl + double tap forward in combat mode (souls_skills/padrao.teleporte.json): the fighter appears distanciaFrente
// blocks in front of whoever they aim at within alcance, or else whoever they fought in the last lembrarAlvoTicks.
// toquesJanelaTicks: how close the two taps must be. Sound/vfx momentos "saida" and "chegada".
public record TeleporteSpec(int recargaTicks, double alcance, double distanciaFrente, int toquesJanelaTicks, int lembrarAlvoTicks, List<Som> sons) {

    public static TeleporteSpec get() {
        return SkillSpecs.get("padrao.teleporte", TeleporteSpec.class, false);
    }
}
