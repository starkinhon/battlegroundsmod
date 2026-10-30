package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

import net.minecraft.world.phys.Vec3;

// Bills' C, Esfera da Destruição (bills.esfera.json). Bills looks up and leaves for outer space (only he sees it): he
// floats over the square Earth, charges a huge sun of destruction over his raised hand, says "Agora, eu vou destruir a
// terra..." and throws it at the planet. Back in the normal view, the sphere is falling from the sky and lands right on
// the target's head. Everyone else just sees him vanish in a flash and come back.
// Timeline in ticks from the press: 0 "uso", tempo.subirTicks "espaco" (he is gone), + tempo.cargaTicks "lancar" (the
// throw), + tempo.lancarTicks "queda" (back in the world, the sphere starts falling), + tempo.quedaTicks "impacto".
// The "tempo" and "animacao" blocks are also read as an AnimationSpec: the arm goes up at once (preparo) and throws
// (sopro) at atrasoTicks for duracaoTicks.
public record EsferaSpec(String skill, String nome, Tempo tempo, Alvo alvo, Esfera esfera, Espaco espaco, Impacto impacto, Legenda legenda,
        Cutscene cutscene, List<Som> sons) {

    public record Tempo(int subirTicks, int cargaTicks, int lancarTicks, int quedaTicks, int atrasoTicks, int duracaoTicks) {
    }

    public record Alvo(double alcance) {
    }

    // raioEspaco: the sphere's radius when fully charged in space; raioQueda: its radius falling on the target, from
    // alturaQueda blocks up, following the target's head the whole way.
    public record Esfera(double raioEspaco, double raioQueda, double alturaQueda) {
    }

    // The square Earth: a giant grass block tamanho blocks wide, distancia blocks ahead and abaixo blocks below him,
    // turning slowly (girar, degrees per tick). estrelas: how many stars.
    public record Espaco(double tamanho, double distancia, double abaixo, double girar, int estrelas, String corCeu, String corHorizonte) {
    }

    // Damage falls from danoCentro on the target's head to danoBorda at raio. The ground breaks open the vanilla way.
    public record Impacto(float danoCentro, float danoBorda, double raio, double empurrao, boolean quebrarBlocos, double raioCratera,
            double profundidadeCratera) {
    }

    public record Legenda(String texto, String cor, int inicioTick, int fimTick, double tamanho) {
    }

    public record Cutscene(boolean ativa, boolean barrasPretas, boolean intangivel) {
    }

    public static final String SKILL = "bills.esfera";

    public static EsferaSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, EsferaSpec.class, awakened);
    }

    public int spaceTick() {
        return Math.max(1, tempo.subirTicks());
    }

    public int throwTick() {
        return spaceTick() + Math.max(1, tempo.cargaTicks());
    }

    // The cutscene ends here: back in the world, the sphere starts to fall.
    public int fallTick() {
        return throwTick() + Math.max(1, tempo.lancarTicks());
    }

    public int impactTick() {
        return fallTick() + Math.max(1, tempo.quedaTicks());
    }

    // Where the falling sphere's center is after k (0 to 1) of the fall: from start towards the head it follows, slow
    // and heavy, a little faster at the end. The server lands it with the same numbers the clients draw.
    public static Vec3 fallAt(Vec3 start, Vec3 head, double k) {
        k = Math.max(0, Math.min(1, k));
        return start.lerp(head, k * 0.6 + k * k * 0.4);
    }
}
