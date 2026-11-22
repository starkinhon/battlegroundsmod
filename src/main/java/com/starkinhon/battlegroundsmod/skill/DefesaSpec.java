// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The defense, from souls_skills/padrao.defesa.json. Holding the defense key in combat mode raises the guard: hits from
// the front (frente: the smallest cosine between where the defender faces and the attacker, 0 = the whole front half)
// lose reducao of their damage (the rest goes through: chip damage) and don't stun. A hit in the first parryTicks of the
// guard is a parry: no damage, and the attacker is stunned for parryStunTicks. Raising the guard again less than
// parryRecargaTicks after the last raise gives no parry window (tapping the key isn't a free parry). The strong attack
// and the Agarrão break the guard: stunned for quebraStunTicks, and the guard can't go up again for recargaQuebraTicks.
// lentidao: how much slower the defender walks.
// resistencia: the guard meter (fighting game style). Every blocked hit wears it down and so does holding it; empty, the
// guard breaks like it does to the strong attack. ult: an ult (attacker's last cast is the ult, within janelaTicks)
// gets partly through the guard.
// agarrao: guard key + attack = the grab, the answer to someone who only guards (see combat/Grab).
// sons/vfx momentos: "bloqueio", "bloqueioTrincado" (the meter low), "parry", "quebra", "agarrar", "arremesso".
public record DefesaSpec(double reducao, double frente, int parryTicks, int parryStunTicks, int parryRecargaTicks, int quebraStunTicks,
        int recargaQuebraTicks, double lentidao, double empurraoBloqueio, Resistencia resistencia, Ult ult, Agarrao agarrao, SkillSpec.Pose pose,
        List<Som> sons) {

    // maximo: the meter's size. custoPorGolpe: what every blocked hit takes; custoPorDano: plus this much for a hit worth the
    // defender's whole max health (proportional). drenoPorSegundo: taken every second the guard is held. regenPorSegundo:
    // given back with the guard down, after atrasoRegenTicks. parryDevolve: a parry gives this back. trincadaAbaixoDe:
    // under this share of the meter the blocks look and sound cracked (both fighters can read it).
    public record Resistencia(double maximo, double custoPorGolpe, double custoPorDano, double drenoPorSegundo, double regenPorSegundo,
            int atrasoRegenTicks, double parryDevolve, double trincadaAbaixoDe) {
    }

    // passa: the share of the damage that goes through a guard (instead of 1 - reducao). custoMult: the meter cost of the
    // block, times this. A parry against an ult only blocks it (no cancel, no stun on the caster).
    public record Ult(double passa, double custoMult, int janelaTicks) {
    }

    // alcance (blocks, times the body size), cone (smallest cosine in front), contatoTicks (when the hands close),
    // seguraTicks (held in front before the shove), dano (Tier 0 max, like the punches), empurrao/subida (the shove),
    // atordoaTicks (stun on someone who wasn't guarding; a guard takes quebraStunTicks), recargaTicks, erroTicks (stuck
    // in place after a miss: punishable).
    public record Agarrao(double alcance, double cone, int contatoTicks, int seguraTicks, double dano, double empurrao, double subida,
            int atordoaTicks, int recargaTicks, int erroTicks) {
    }

    public static DefesaSpec get() {
        return SkillSpecs.get("padrao.defesa", DefesaSpec.class, false);
    }
}
