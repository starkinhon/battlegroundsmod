package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// A standard movement every fighter has (padrao.esquiva, padrao.pulo_duplo), in the same "souls-skill/1" file style
// as the skills. Speeds are in blocks per tick: a normal jump is 0.42 up.
// impulsoHorizontal: push along the move direction; impulsoVertical: replaces the vertical speed; manterVelocidade:
// how much of the current horizontal speed is kept (0 = all of it is replaced). invencivelTicks: the dodge window,
// where hits, knockback and skills pass through. semDanoDeQueda: no fall damage until landing.
// funcionaNoCriativo: in creative, pressing jump twice also toggles flying, so the move is off there unless this is true.
public record MovementSpec(String skill, String nome, int recargaTicks, double impulsoHorizontal, double impulsoVertical,
        double manterVelocidade, int invencivelTicks, boolean semDanoDeQueda, boolean funcionaNoCriativo, Poeira poeira, List<Som> sons) {

    // Puffs kicked up at the feet. cor is "#rrggbb"; velocidade is how fast they spread out.
    public record Poeira(boolean ativa, int quantidade, float tamanho, int vidaTicks, double velocidade, String cor) {
        public int rgb() {
            try {
                return Integer.parseInt(cor.replace("#", ""), 16);
            } catch (RuntimeException e) {
                return 0xD8D2C4;
            }
        }
    }
}
