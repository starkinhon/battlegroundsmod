package com.starkinhon.battlegroundsmod.skill;

import java.util.List;
import java.util.Map;

// How a player climbs the tiers by fighting (souls_skills/padrao.progressao.json, plan "Souls: Multiverso e Progressão").
// custo: Poder needed to leave each tier, index = tier (custo[10] takes a human to Tier 9; custo[0] is unused).
// valor: Poder a defeated enemy of each tier is worth, index = tier. Each tier is worth double the one below, so beating
// someone stronger already pays more ("equivalent" to that tier, Stark 2026-11-10).
// abaixoZera: an enemy this many tiers (or more) weaker than the player gives nothing, so nobody farms weak mobs.
// multSubAlma: a SubAlma character (the soul mobs) is worth this many normal enemies of its tier. A Divine Soul boss
// gives the whole tier at once.
// perdaMorte: share of the Poder in the current bar lost on death (never a tier).
// tetoSemAlma: the strongest tier a player reaches with no soul. tetos: the strongest tier each soul (power id) carries;
// a power not listed falls back to tetoSemAlma. Once at the ceiling the bar fills but the tier waits for a stronger soul.
// universos: each power's universe; nomesBarra: what the Poder bar is called in each universe (Ki, Chakra...),
// translation keys under progression.battlegroundsmod.bar.<universe>.
// mobsVanilla: tier of an ordinary hostile mob by its max health (first entry whose vidaMax holds it).
public record ProgressionSpec(List<Double> custo, List<Double> valor, int abaixoZera, double multSubAlma, double perdaMorte,
        int tetoSemAlma, Map<String, Integer> tetos, Map<String, String> universos, List<MobTier> mobsVanilla) {

    public record MobTier(double vidaMax, int tier) {
    }

    public static ProgressionSpec get() {
        return SkillSpecs.get("padrao.progressao", ProgressionSpec.class, false);
    }
}
