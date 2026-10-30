package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The "vfx" list of a skill file, as the Forja exports it. Each entry is one effect that plays at a moment of
// the skill (momento, like the "sons" list), inicio ticks later. Only the client reads it: the server just says
// "moment X of skill Y happened here". Fields an effect doesn't use are left out of the file.
//
// tipo: screen effects impactFrame, flash, blurRadial, distorcao, tremor, fov, vinheta; world effects clarao, estrela,
// anelChao, anel, raios, feixe, esfera, fumaca, faiscas, fogo, jatoFogo, rastroBoca, parede, anelCabeca, olhoChao, olho,
// bracoEspada (the Susanoo arm swinging its sword), corte (the energy slash flying away).
// seguir ("centro", "cabeca", "pes", "boca") keeps the effect on the entity the server named, facing where it looks.
// duraMomento: lasts as long as the server says this moment lasts (a stun, a counter window) instead of vidaTicks.
// golpeTicks: when the swing of bracoEspada lands (ticks after it appears).
// primeiraPessoa: opacity when the effect follows you and you are in first person (so it doesn't cover the screen).
public record VfxSpec(List<Efeito> vfx) {

    public record Efeito(String tipo, String momento, Boolean ativa, Integer inicio, Integer vidaTicks, Double raio, Double forca,
            Double velocidade, Double tamanho, Integer quantidade, String cor, String cor2, String modo, List<String> sequencia,
            Integer ticksPorQuadro, Double alcance, Double deslocamento, Double altura, Double comprimento, Double tinta, String seguir,
            Boolean duraMomento, Double primeiraPessoa, String textura, Double girar, Integer golpeTicks) {
    }
}
