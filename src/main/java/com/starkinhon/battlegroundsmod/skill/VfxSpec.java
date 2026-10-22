package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The "vfx" list of a skill file, as the Forja exports it. Each entry is one effect that plays at a moment of
// the skill (momento, like the "sons" list), inicio ticks later. Only the client reads it: the server just says
// "moment X of skill Y happened here". Fields an effect doesn't use are left out of the file.
//
// tipo: screen effects impactFrame, flash, blurRadial, distorcao, tremor, fov; world effects clarao, estrela,
// anelChao, anel, raios, feixe, esfera, fumaca, faiscas, fogo.
public record VfxSpec(List<Efeito> vfx) {

    public record Efeito(String tipo, String momento, Boolean ativa, Integer inicio, Integer vidaTicks, Double raio, Double forca,
            Double velocidade, Double tamanho, Integer quantidade, String cor, String cor2, String modo, List<String> sequencia,
            Integer ticksPorQuadro, Double alcance, Double deslocamento, Double altura, Double comprimento, Double tinta) {
    }
}
