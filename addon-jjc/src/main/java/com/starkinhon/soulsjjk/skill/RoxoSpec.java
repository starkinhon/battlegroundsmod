package com.starkinhon.soulsjjk.skill;

import java.util.List;

// Purple (gojo.roxo.json), Gojo's C, the Visualizer's "Gojo · Roxo" tab as Stark approved it. Held: the Red (on his right)
// and the Blue (on his left) appear behind him at the height of his chest, come round to the front and press together
// slowly, the purple born where they touch and taking both (formar). Formed and still held, it grows up to
// orbe.escalaMax times in segurado.crescerTicks. Let go: it is launched straight where he aims, speeding up, and erases
// everything in its path (blocks only with the gamerule soulsBlockDestruction, no drops). Let go before it has formed:
// it finishes forming and leaves at its base size.
// The Unlimited Purple (irrestrito): a Red fired at full charge into his own Blue parked at full charge. Both implode,
// a Purple with no contour grows fast to irrestrito.raio and blows up in every direction out to irrestrito.raioExplosao.
// Timeline: "uso", "surgir" (the two spheres appear), "tocar" (they touch), "formar" (formed), "carregar" (held formed),
// "lancar" (launched, at the orb), "apagarAlvo" (on each one it erases), "sumir" (where it ends); the Unlimited Purple:
// "colidir" (the Red reaches the Blue), "nascer" (the Purple is born), "explodir". The "tempo" and "animacao" blocks are
// also read as an AnimationSpec (the launch: hands together, then pointing).
public record RoxoSpec(String skill, String nome, Orbe orbe, Formar formar, Segurado segurado, Tiro tiro, Irrestrito irrestrito, List<Som> sons) {

    // raio: each sphere and the formed Purple's base (blocks; formed it is 10% bigger); escalaMax: how many times bigger
    // fully grown.
    public record Orbe(double raio, double escalaMax) {
    }

    // surgirTicks: the spheres appearing behind him; virTicks: coming round to the front; misturarTicks: pressing
    // together and mixing.
    public record Formar(int surgirTicks, int virTicks, int misturarTicks) {
    }

    // crescerTicks: formed, held this long it is fully grown; maxSegurarTicks: bosses only (players let go with the key).
    public record Segurado(int crescerTicks, int maxSegurarTicks) {
    }

    // velocidade: blocks per tick when launched, growing by aceleracao each tick up to velocidadeMax; alcance: how far it
    // goes; apagar: it erases everything within its radius times this; dano / danoMax: to whoever it erases (base size
    // to fully grown); blocosPorTick: at most this many blocks erased each tick. raioDestruicao / raioDestruicaoMax: the
    // tunnel it erases through the world and the targets it takes, base size to fully grown (Stark: about twice the full
    // Red's; the ball itself stays the size it looks).
    public record Tiro(double velocidade, double aceleracao, double velocidadeMax, double alcance, double apagar, float dano, float danoMax,
            int blocosPorTick, double raioDestruicao, double raioDestruicaoMax) {
    }

    // cargaMin: the Red and the parked Blue both need at least this charge. implodirTicks: the two imploding into one;
    // crescerTicks: the Purple growing to raio; esperaTicks: grown, before it blows up; explosaoTicks: the blast running
    // out to raioExplosao. danoRoxo: to whoever the Purple swallows while it grows; danoCentro / danoBorda: the blast's
    // damage near it and at its edge; empurrao: the throw outwards; puxar: blocks per tick everyone near is pulled in
    // while it implodes (within raioPuxar). The crater: profundidade blocks deep under the middle, shallower to the edge,
    // and everything standing up to altura blocks over the middle is gone; blocosPorTick: at most this many each tick
    // (it keeps going after the blast if the ground is a lot).
    public record Irrestrito(double cargaMin, int implodirTicks, int crescerTicks, int esperaTicks, double raio, int explosaoTicks,
            double raioExplosao, float danoRoxo, float danoCentro, float danoBorda, double empurrao, double puxar, double raioPuxar,
            double profundidade, double altura, int blocosPorTick) {
    }

    public static final String SKILL = "gojo.roxo";

    public static RoxoSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, RoxoSpec.class, awakened);
    }
}
