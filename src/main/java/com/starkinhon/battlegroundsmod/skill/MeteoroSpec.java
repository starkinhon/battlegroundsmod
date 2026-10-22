package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Meteoro (Madara's ult): a giant meteor falls on the aimed spot. Same "souls-skill/1" layout as the other skills,
// tuned in the Forja de Skills page. Colors are "#rrggbb". In awakened mode the fumacas list is replaced as a whole.
public record MeteoroSpec(String skill, String nome, Tempo tempo, Mira mira, Corpo meteoro, List<Fumaca> fumacas, Riscos riscos,
        Preparo preparo, Aviso aviso, Impacto impacto, Rabeta rabeta) {

    public record Tempo(int preparoTicks, int quedaTicks) {
    }

    // modo "mira": where the player looks, up to distanciaMax. "frente": distanciaFrente blocks ahead. Snapped to the ground.
    public record Mira(String modo, double distanciaMax, double distanciaFrente) {
    }

    // anguloQueda: degrees from vertical. giroOrigem: degrees around the target, 0 = from behind the caster.
    // aceleracao 0..1 blends a steady fall into a speeding up one. girar: spin in degrees per tick.
    // afundar: how deep the meteor sinks into the ground, as a fraction of its radius. brilho scales the hot front.
    public record Corpo(double raio, double alturaInicio, double anguloQueda, double giroOrigem, double aceleracao, double girar,
            double afundar, String corEscura, String corBrilho, double brilho) {
    }

    // One kind of smoke trail. lados: how many trails around the meteor; abertura: angle of the V they open into;
    // deslocamento: distance from the center in radii; largura: puff spread; densidade: puffs per tick per trail.
    public record Fumaca(String nome, boolean ativa, int lados, double abertura, double deslocamento, double largura, int densidade,
            int duracaoTicks, String cor) {
    }

    // Thin red streaks pulled out behind the meteor. espalhamento is a fraction of the radius.
    public record Riscos(boolean ativa, int porTick, double comprimento, double velocidade, double espalhamento, String cor) {
    }

    // Tail drawn on the client with the shader effects: fire behind the rock (cor2 hot near it, cor further back) and
    // smoke left in the sky along the path, which lingers fumacaVidaTicks after the rock went by.
    public record Rabeta(boolean ativa, String cor, String cor2, double tamanho, int comprimento, String fumacaCor, double fumacaTamanho,
            int fumacaVidaTicks, int fumacaPorTick) {
    }

    // Particles around the raised hand while calling the meteor.
    public record Preparo(boolean ativa, int porTick, String cor) {
    }

    // Circle on the ground where it will land.
    public record Aviso(boolean ativo, String cor) {
    }

    // Damage goes linearly from danoCentro at the center to danoBorda at the edge. tremerTela scales the camera shake.
    // quebrarBlocos digs a crater raioCratera blocks across and profundidadeCratera deep (needs the mobGriefing rule).
    public record Impacto(double raio, float danoCentro, float danoBorda, double empurrao, double alturaEmpurrao, boolean quebrarBlocos,
            double raioCratera, double profundidadeCratera, double tremerTela, Onda onda, Explosao explosao) {
    }

    // Shockwave ring along the ground: velocidade in blocks per tick, tamanho is the particle size. Out to alcance blocks
    // it hits whoever the front passes (dano, empurrao) and, with quebrarBlocos, tears up the ground it runs over
    // (altura blocks high, profundidade deep, largura wide at the front).
    public record Onda(boolean ativa, double velocidade, float tamanho, String cor, double alcance, float dano, double empurrao,
            boolean quebrarBlocos, int altura, int profundidade, double largura) {
    }

    public record Explosao(boolean ativa, int quantidade, int fumaca, String cor) {
    }
}
