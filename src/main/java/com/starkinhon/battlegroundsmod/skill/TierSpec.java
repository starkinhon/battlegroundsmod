package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Power tiers, from souls_skills/padrao.tiers.json (rebalance of 2026-11-08, Jujutsu Craft style).
// vida: max health per tier, index = tier number (0 Deus ... 10 Humano). Every move's damage in the files is its maximum
// (Tier 0); a fighter of tier t deals vida[t] / vida[0] of it, so a move takes the same share of the life of someone of
// the same tier at every tier.
// Escudo: a fighter has (10 - tier) shield points. Against someone of a LOWER tier each point of difference keeps only
// (1 - escudoCorte) of the blow, and resistenciaEscudo of the target's max health per point is then taken off every
// blow (Resistencia de escudo), so a god takes nothing from a human. Same tier or stronger attacker: no shield.
// armaduraPorTier: armor points per tier above human (the real armor attribute; Tier 0 = 20). resistenciaPorTier: share
// of every blow taken off per tier above human (Tier 0 = 10 x it). Both hold against the same tier too (2026-11-09).
// multSubAlmas: the SubAlmas' damages are written in the code at their old scale; this brings them to the new one.
// Walking gets velocidadeAndarPorTier faster per tier and sprinting speedCorrendoPorTier levels of Speed (+20% each)
// per tier, both stopping at tierVelocidadeMax. nomes: the tier names. bossTier0: what every boss gets under
// /gamerule allbossestier0 (see BossRules).
public record TierSpec(List<Double> vida, double escudoCorte, double resistenciaEscudo, double armaduraPorTier, double resistenciaPorTier, double multSubAlmas,
        double velocidadeAndarPorTier, int speedCorrendoPorTier, int tierVelocidadeMax, List<String> nomes, BossTier0 bossTier0) {

    public record BossTier0(double vida, double armadura, double resistenciaEmpurrao, float multDano) {
    }

    public static TierSpec get() {
        return SkillSpecs.get("padrao.tiers", TierSpec.class, false);
    }
}
