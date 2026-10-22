package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The defense, from souls_skills/padrao.defesa.json. Holding the defense key in combat mode raises the guard: hits from
// the front (frente: the smallest cosine between where the defender faces and the attacker, 0 = the whole front half)
// lose reducao of their damage and don't stun. A hit in the first parryTicks of the guard is a parry: no damage, and the
// attacker is stunned for parryStunTicks. The strong attack breaks the guard: stunned for quebraStunTicks, and the guard
// can't go up again for recargaQuebraTicks. lentidao: how much slower the defender walks.
// sons/vfx momentos: "bloqueio", "parry", "quebra".
public record DefesaSpec(double reducao, double frente, int parryTicks, int parryStunTicks, int quebraStunTicks, int recargaQuebraTicks,
        double lentidao, double empurraoBloqueio, SkillSpec.Pose pose, List<Som> sons) {

    public static DefesaSpec get() {
        return SkillSpecs.get("padrao.defesa", DefesaSpec.class, false);
    }
}
