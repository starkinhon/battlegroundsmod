// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Mangekyo: an eye-contact stun. Same "souls-skill/1" layout as the other skills, tuned in the Forja de Skills page.
public record MangekyoSpec(String skill, String nome, Tempo tempo, SkillSpec.Forma forma, Efeitos efeitos, CutsceneSpec cutscene,
        List<Som> sons) {

    // atraso: ticks between pressing the key and the stun landing. Caster and targets are frozen meanwhile.
    public record Tempo(int atrasoTicks) {
    }

    // Levels start at 1; 0 turns an effect off. travarSkills puts every skill of a hit player on cooldown.
    // atordoarTicks freezes the target in place for that long (0 = no freeze).
    public record Efeitos(int duracaoTicks, int fadiga, int cegueira, int lentidao, boolean travarSkills, int atordoarTicks) {
    }
}
