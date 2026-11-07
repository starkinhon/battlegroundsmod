package com.starkinhon.battlegroundsmod.skill;

// The Bills boss's Inevitável (bills.inevitavel.json): Bills is a god (tier 0), and whoever he targets that is tier
// tierMinimo or weaker isn't worth a fight. He appears distancia blocks in front of them and they freeze where they
// stand, while he says "Você realmente acha que pode vir me enfrentar com esse nível de poder? Patético." and raises his
// arm at them very slowly. At hakaiTick comes the Hakai (cutscene, open hand, erased whatever their life), and as the
// last sparks go he says "Saiba, o deus da destruição é inevitável." (fimAntesTicks before the kill).
// The "tempo" and "animacao" blocks are also read as an AnimationSpec: the arm rises over transicaoTicks to preparo
// (sopro is the same pose, held while the Hakai plays), then back over retornoTicks.
public record InevitavelSpec(String skill, String nome, int tierMinimo, double alcance, double distancia, int recargaTicks, int falaTick,
        int hakaiTick, int fimAntesTicks) {

    public static final String SKILL = "bills.inevitavel";
    public static final String FALA = "fala.battlegroundsmod.bills.inevitavel";
    public static final String FALA_FIM = "fala.battlegroundsmod.bills.inevitavel_fim";

    public static InevitavelSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, InevitavelSpec.class, awakened);
    }
}
