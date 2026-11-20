// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// double tap of the sprint key (Ctrl) in combat mode (souls_skills/padrao.teleporte.json): the fighter appears distanciaFrente
// blocks in front of whoever they aim at within alcance, or else whoever they fought in the last lembrarAlvoTicks.
// toquesJanelaTicks: how close the two taps must be. Sound/vfx momentos "saida" and "chegada".
public record TeleporteSpec(int recargaTicks, double alcance, double distanciaFrente, int toquesJanelaTicks, int lembrarAlvoTicks, List<Som> sons) {

    public static TeleporteSpec get() {
        return SkillSpecs.get("padrao.teleporte", TeleporteSpec.class, false);
    }
}
