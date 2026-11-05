package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The values of Aizen's skills besides the Black Box, one file each (aizen.<skill>.json). Each file also has the
// "tempo" and "animacao" blocks (read as an AnimationSpec for the body pose), "sons" and "vfx".
public final class AizenSpecs {
    public static final String KYOKA = "aizen.kyoka";
    public static final String REIATSU = "aizen.reiatsu";
    public static final String UM_DEDO = "aizen.um_dedo";
    // The sword cut after the finger stopped a hit (only its pose).
    public static final String UM_DEDO_CORTE = "aizen.um_dedo_corte";
    public static final String HOGYOKU = "aizen.hogyoku";
    public static final String DASH = "aizen.dash";

    private AizenSpecs() {
    }

    // Kyoka Suigetsu (X). usarTicks: the sword goes up before the clone takes his place; limiteTicks: the longest he stays
    // unseen; quebrarTicks: how long the clone cracks before it breaks; recargaTicks: the cooldown, counted from the
    // break. custoAtivar / custoPorSegundo: stamina, for when the stamina bar exists (not used yet).
    public record Kyoka(String skill, int usarTicks, int limiteTicks, int quebrarTicks, int recargaTicks, float custoAtivar,
            float custoPorSegundo, List<Som> sons) {
        public static Kyoka get(boolean awakened) {
            return SkillSpecs.get(KYOKA, Kyoka.class, awakened);
        }
    }

    // Reiatsu Esmagadora (C). A tap (let go before cliqueTicks): a pulse of raioClique. Held: grows from raioClique to
    // raioMax over carregarTicks, crushing whoever is inside; let go (or at maxTicks) it bursts. Damage danoMin to
    // danoMax with the charge; the burst leaves them down ajoelharTicks, the tap ajoelharCliqueTicks.
    public record Reiatsu(String skill, int cliqueTicks, double raioClique, double raioMax, int carregarTicks, int maxTicks,
            float danoClique, float danoMin, float danoMax, int ajoelharCliqueTicks, int ajoelharTicks, List<Som> sons) {
        public static Reiatsu get(boolean awakened) {
            return SkillSpecs.get(REIATSU, Reiatsu.class, awakened);
        }
    }

    // Um Dedo (B, a counter). janelaTicks: how long the finger is up; a hit from within alcance in that time is stopped
    // dead (both held still hitstopTicks), then the sword comes out and cuts corteTicks later for dano, throwing them
    // back (empurrao, subida) and stunning them atordoarTicks.
    public record UmDedo(String skill, int janelaTicks, double alcance, int hitstopTicks, int corteTicks, float dano, double empurrao,
            double subida, int atordoarTicks, List<Som> sons) {
        public static UmDedo get(boolean awakened) {
            return SkillSpecs.get(UM_DEDO, UmDedo.class, awakened);
        }
    }

    // Evolucao do Hogyoku (V, the special). casuloTicks: the cocoon starts closing; eclosaoTicks: it bursts (he can't
    // be hurt or move until then); duracaoTicks: how long the evolved form lasts after that. The burst hits everyone
    // within ondaRaio for ondaDano and throws them off. While evolved: forca/velocidade/resistencia are the effect levels
    // (0 = level I, -1 = none), the Black Box is the full incantation (its "despertado" block), and V again is the dash.
    public record Hogyoku(String skill, int casuloTicks, int eclosaoTicks, int duracaoTicks, double ondaRaio, float ondaDano,
            int forca, int velocidade, int resistencia, List<Som> sons) {
        public static Hogyoku get(boolean awakened) {
            return SkillSpecs.get(HOGYOKU, Hogyoku.class, awakened);
        }
    }

    // The dash of cuts (V again while evolved): he vanishes sumirTicks, then cortes cuts land on the target every
    // intervaloTicks for danoCorte each; he shows up behind it and they all light up at once finalTicks later for danoFinal.
    public record Dash(String skill, double alcance, int sumirTicks, int cortes, int intervaloTicks, int finalTicks, float danoCorte,
            float danoFinal, double empurrao, int recargaTicks, List<Som> sons) {
        public static Dash get(boolean awakened) {
            return SkillSpecs.get(DASH, Dash.class, awakened);
        }
    }
}
