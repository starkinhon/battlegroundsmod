package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// B, the Planetary Destruction (bills.planetary_destruction.json). Bills raises his arm at the target and a tiny sphere
// of destruction (bola.raio, about half a head) forms at his finger; it stays there while the key is held (carga) and
// goes when the key comes up. It flies up bola.subir blocks over him in bola.subirTicks, then chases the target he is
// fighting from up there, hovering bola.alturaSobreAlvo over it at bola.velocidade blocks per tick. bola.implodirTicks
// after it left his hand it implodes: it shrinks for bola.encolherTicks and bursts to bola.raioGigante in
// bola.crescerTicks (impact frame, the screen shakes for whoever is near it, see tremor), then comes down on the target
// at bola.velocidadeGigante. Where it touches the ground or a body it blows up (explosao): an anime explosion of fire
// that turns to smoke and rises (idaTicks), hangs there (pausaTicks), runs back into a point (voltaTicks), waits
// (esperaTicks) and blows up again, bigger, with a shockwave (segundaTicks). bola.vidaMaxTicks after release it blows
// up wherever it is.
// Timeline: "uso" on the press (the ball forms at his finger), "soltar" when it leaves, "implodir" when it bursts to
// full size, "explosao" on the hit, "rebobinar" when the explosion starts running back, "explosao2" on the second blast,
// "cancelar" when he is knocked out of the charge. The client draws the ball and the explosion from this same file
// (client/fx/PlanetaryRenderer), so the explosion timings must stay the same on both sides.
// The "tempo" and "animacao" blocks are also read as an AnimationSpec (tempo.atrasoTicks holds the raised arm).
public record PlanetaryDestructionSpec(String skill, String nome, Tempo tempo, Alvo alvo, Carga carga, Bola bola, Tremor tremor,
        Explosao explosao, int cancelarStunTicks, List<Som> sons) {

    public record Tempo(int atrasoTicks, int duracaoTicks) {
    }

    // alcance: how far a target can be locked; detectar: with nobody locked, how close someone has to come to the ball.
    public record Alvo(double alcance, double detectar) {
    }

    // formarTicks: the ball appears at his finger; minTicks: a tap still lets it go after this long; maxTicks: it goes on
    // its own when held this long.
    public record Carga(int formarTicks, int minTicks, int maxTicks) {
    }

    // curva: how fast it turns towards where it is going (0 to 1 each tick).
    public record Bola(double raio, double raioGigante, double subir, int subirTicks, double alturaSobreAlvo, double velocidade,
            double velocidadeGigante, double curva, int implodirTicks, int encolherTicks, int crescerTicks, int vidaMaxTicks) {
    }

    // The screen shakes with forca for whoever is closer than alcance blocks to the big ball (less the farther away).
    public record Tremor(double alcance, float forca) {
    }

    // raio/dano: the first blast; raio2/dano2: the second one, which also pulverizes whoever is left under the Hakai's
    // life limit, erases attacks in flight and leaves the crater (raioCratera wide, profundidadeCratera deep, needs the
    // mobGriefing gamerule). onda: the shockwave of the second blast, pushing (and hurting a little) out to raioOnda.
    public record Explosao(int idaTicks, int pausaTicks, int voltaTicks, int esperaTicks, int segundaTicks, double raio, float dano,
            double raio2, float dano2, double empurrao, double raioOnda, float danoOnda, boolean quebrarBlocos, double raioCratera,
            double profundidadeCratera) {

        public int rewindAt() {
            return Math.max(1, idaTicks) + Math.max(0, pausaTicks);
        }

        public int secondAt() {
            return rewindAt() + Math.max(1, voltaTicks) + Math.max(0, esperaTicks);
        }

        public int total() {
            return secondAt() + Math.max(1, segundaTicks);
        }
    }

    public static final String SKILL = "bills.planetary_destruction";

    public static PlanetaryDestructionSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, PlanetaryDestructionSpec.class, awakened);
    }
}
