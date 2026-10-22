package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The hand-to-hand combo every fighter has (left click), read from souls_skills/padrao.combo.json.
// Each click plays the next entry of golpes; after the last one (or resetTicks without clicking) it starts over.
// alcance: reach of a hit; passo: when nobody is that close but someone is within distanciaMax in front, the fighter
// runs at them first (impulso blocks per tick, for at most ticksMax ticks; cone: how close to the aim they must be). bufferTicks: a click this close to the end of the previous hit
// is kept and played as soon as it can, so mashing never drops a hit.
// atordoado: the stun every hit leaves (lentidao and pulo take that fraction off; bosses get multiplicadorBoss of the time).
// cooldownAoApanharTicks: after any hit (punch, skill, mob) the one hit can't punch or use skills for this long.
public record ComboSpec(String skill, String nome, double alcance, int resetTicks, int bufferTicks, Passo passo, Atordoado atordoado,
        int cooldownAoApanharTicks, List<Golpe> golpes, List<Som> sons) {

    public record Passo(double distanciaMax, double impulso, double cone, int ticksMax) {
    }

    public record Atordoado(double lentidao, double pulo, double multiplicadorBoss) {
    }

    // One hit. tempo.atrasoTicks is the wind-up before it lands; recuperacaoTicks is how long until the next click works.
    // empurrao/subida: knockback away and up (blocks per tick); avanco: the fighter's own step forward with the hit.
    // hitstopTicks: both freeze for a moment on contact; tremor: camera shake for both. momento names the "sons" and "vfx"
    // entries that play on contact ("golpe" or "final"); the whoosh of a miss is momento "erro".
    public record Golpe(String nome, double dano, double empurrao, double subida, double avanco, int stunTicks, int hitstopTicks,
            float tremor, int recuperacaoTicks, String momento, AnimationSpec.Tempo tempo, AnimationSpec.Animacao animacao) {

        public AnimationSpec animation() {
            return new AnimationSpec(tempo, animacao);
        }

        public int windUp() {
            return tempo.windUp();
        }
    }

    public static ComboSpec get() {
        return SkillSpecs.get("padrao.combo", ComboSpec.class, false);
    }

    public Golpe golpe(int step) {
        return golpes.get(Math.floorMod(step, golpes.size()));
    }
}
