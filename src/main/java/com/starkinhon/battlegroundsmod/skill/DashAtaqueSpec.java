package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The Dash Attack, first of the base attacks (souls_skills/padrao.dash_ataque.json): the fighter slides fast where
// they aim (velocidade blocks per tick, for at most duracaoMaxTicks) and can steer while sliding (curva: how much of
// the turn to the aim is taken each tick, 1 = instantly). Coming within raioGrudarAlvo of whoever they fight (or
// raioGrudar of anyone in front), they stick to them and land golpeDoCombo of the punch combo with the values in golpe;
// the next click goes on with the combo from there. tempo/animacao: the sliding pose.
// Sound and vfx momentos: "uso" (start), "rastro" (every rastroACadaTicks while sliding), "golpe" (the hit).
public record DashAtaqueSpec(String skill, String nome, int recargaTicks, int duracaoMaxTicks, double velocidade, double curva,
        double raioGrudar, double raioGrudarAlvo, double distanciaGrudar, int golpeDoCombo, Golpe golpe, Rastro rastro,
        AnimationSpec.Tempo tempo, AnimationSpec.Animacao animacao, List<Som> sons) {

    // multiplicadorDano: times the combo hit's damage. The rest replace the combo hit's own values.
    public record Golpe(double multiplicadorDano, double empurrao, double subida, int stunTicks, int hitstopTicks, float tremor) {
    }

    // The speed and wind trail: streak lines left behind, puffs at the feet and a ring of wind every few ticks.
    public record Rastro(String cor, int linhas, double comprimentoLinha, int vidaTicks, int puffs, int anelACadaTicks,
            double tamanhoAnel, int rastroACadaTicks) {
    }

    public static DashAtaqueSpec get() {
        return SkillSpecs.get("padrao.dash_ataque", DashAtaqueSpec.class, false);
    }
}
