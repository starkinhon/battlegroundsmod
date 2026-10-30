package com.starkinhon.battlegroundsmod.skill;

// Izanami (Shift + X): records everything around the caster for gravacaoTicks, then a cutscene on the caster's eyes,
// then everything plays backwards over voltarTicks until the world is as it was. raio: blocks around the spot where
// it was used. Read from souls_skills/madara.izanami.json; the cutscene block is the same as the Mangekyo's.
public record IzanamiSpec(String skill, String nome, int gravacaoTicks, double raio, int voltarTicks, CutsceneSpec cutscene) {
}
