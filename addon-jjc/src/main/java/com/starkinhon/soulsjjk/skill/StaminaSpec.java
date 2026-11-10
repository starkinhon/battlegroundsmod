package com.starkinhon.soulsjjk.skill;

import java.util.List;

// Attack stamina, from souls_skills/padrao.estamina.json. Skills spend it (a click a little, holding a charged skill
// more, an ult a lot); the base fighting gives it back (punches thrown and landed, Forte, Barragem, Dash Attack, dash).
// At zero the fighter is exhausted for vulneravelSegundos: no skills, only punches, and open to the skills that need a
// stunned target (combat/Stamina.stunnable). Players only for now; bosses don't use it.
// maximoPorTier: the max stamina, index = tier (0 Deus ... 10 Humano). Costs are flat points; gains (ganho*Pct) are
// percent of the max, so high tiers also get theirs back fast.
// custoCarregandoPorSegundo: drained while a charged skill is held, up to custoCarregadoMax more per use.
// regenForaDeCombatePorSegundo: percent of the max per second out of combat mode (0 = none).
// voltaComPct: the stamina the fighter has when the exhaustion ends (it fills up to it during the exhaustion).
// gastaNoCriativo: creative mode spends it too (/soul semcooldown always makes it free).
public record StaminaSpec(List<Double> maximoPorTier, double custoSkill, double custoUlt, double custoCarregandoPorSegundo,
        double custoCarregadoMax, double ganhoGolpePct, double ganhoAcertoPct, double ganhoAcertoBarragemPct, double ganhoAtaqueBasePct,
        double ganhoDashPct, double regenForaDeCombatePorSegundo, int vulneravelSegundos, double voltaComPct, boolean gastaNoCriativo,
        Barra barra) {

    // The bar. Third person: a curved bar on the right of the fighter (distancia blocks out from the body, arcoGraus of
    // circle, espessura blocks thick). First person: a straight bar on the left side of the screen (GUI pixels).
    // baixaAbaixoDePct: under it the bar turns corBaixa. esconderCheia: full and out of combat, the bar fades out.
    public record Barra(String cor, String corBaixa, String corExausto, double baixaAbaixoDePct, double distancia, double alturaNoCorpo,
            double arcoGraus, double espessura, boolean esconderCheia, int primeiraPessoaX, int primeiraPessoaAltura, int primeiraPessoaLargura) {

        public static int rgb(String color, int fallback) {
            try {
                return Integer.parseInt(color.replace("#", ""), 16);
            } catch (RuntimeException e) {
                return fallback;
            }
        }
    }

    public double max(int tier) {
        if (maximoPorTier == null || maximoPorTier.isEmpty()) {
            return 100;
        }
        return maximoPorTier.get(Math.max(0, Math.min(tier, maximoPorTier.size() - 1)));
    }

    public static StaminaSpec get() {
        return SkillSpecs.get("padrao.estamina", StaminaSpec.class, false);
    }
}
