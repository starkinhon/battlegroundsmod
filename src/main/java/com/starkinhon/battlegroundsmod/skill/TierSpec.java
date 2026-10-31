package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Power tiers, from souls_skills/padrao.tiers.json. Tier 10 is a plain human; every tier above adds vidaPorTier health,
// forcaPorTier strength (vanilla hits) and armor up to armaduraMax at tierArmaduraCheia. The mod's own moves deal
// 1 + multiplicadorPorForca x strength bonus times their damage. Walking gets velocidadeAndarPorTier faster per tier and
// sprinting speedCorrendoPorTier levels of Speed (+20% each) per tier, both stopping at tierVelocidadeMax.
// nomes: the tier names, index = tier number. bossTier0: what every boss gets under /gamerule allbossestier0 (see BossRules).
public record TierSpec(double vidaPorTier, double forcaPorTier, double multiplicadorPorForca, double armaduraMax, int tierArmaduraCheia,
        double velocidadeAndarPorTier, int speedCorrendoPorTier, int tierVelocidadeMax, List<String> nomes, BossTier0 bossTier0) {

    public record BossTier0(double vida, double armadura, double resistenciaEmpurrao, float multDano) {
    }

    public static TierSpec get() {
        return SkillSpecs.get("padrao.tiers", TierSpec.class, false);
    }
}
