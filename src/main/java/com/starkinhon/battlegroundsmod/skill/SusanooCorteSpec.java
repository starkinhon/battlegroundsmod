package com.starkinhon.battlegroundsmod.skill;

// Susanoo: Corte (Shift + C): the Susanoo arm swings its sword and throws a huge blue slash where the caster aims.
// Read from souls_skills/madara.susanoo_corte.json; "tempo" + "animacao" are the body pose (AnimationSpec): the slash
// leaves at atrasoTicks, when the swing lands.
public record SusanooCorteSpec(String skill, String nome, Tempo tempo, Corte corte) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // largura: width of the slash in blocks; alcance: how far it flies; velocidade: blocks per tick; espessura: how thick
    // the strip of blocks it cuts through is. It starts frente blocks ahead and subir blocks above the feet.
    // dano/empurrao/altura: what each enemy it passes takes (once). quebrarBlocos needs the mobGriefing rule.
    public record Corte(double largura, double alcance, double velocidade, double espessura, float dano, double empurrao, double altura,
            boolean quebrarBlocos, double frente, double subir) {
    }
}
