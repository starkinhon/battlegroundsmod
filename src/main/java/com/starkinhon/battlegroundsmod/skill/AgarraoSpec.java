package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Madara's neck grab (madara.agarrao.json, made in the Forja): he vanishes into a very fast dash towards the target;
// whoever he reaches is grabbed by the neck, lifted, held up and slammed into the ground, which breaks open.
// Sound and vfx momentos: "uso" (start), "dash" (he vanishes), "agarrar" (the grab), "esmagar" (the slam),
// "reaparecer" (a dash that missed ends).
public record AgarraoSpec(String skill, String nome, Tempo tempo, Dash dash, Rastro rastro, Agarrar agarrar, Esmagar esmagar,
        Impacto impacto, Cutscene cutscene, Animacao animacao, List<Som> sons) {

    public record Tempo(int preparoTicks) {
    }

    // velocidade in blocks per tick, for at most distanciaMax blocks. Anyone within raioPegar of the path is caught once
    // he gets within distanciaAgarrar of them. seguirAlvo: the dash bends towards the locked target if it moves.
    public record Dash(double velocidade, double distanciaMax, double raioPegar, double distanciaAgarrar, boolean invisivel, boolean seguirAlvo) {
    }

    // densidade: wind specks per block of path. fumacaSumir: dark puffs where he vanishes and reappears.
    public record Rastro(boolean ativa, int densidade, String cor, int fumacaSumir, String corFumaca) {
    }

    // Lifted over levantarTicks until the target's feet are altura blocks up (0 = he comes out of the dash already holding
    // them up), then held segurarTicks. Held distancia blocks in front of his center and lado blocks to his right (negative
    // = left); the slam goes straight down from there. dano lands on the grab.
    public record Agarrar(int levantarTicks, int segurarTicks, double altura, double distancia, double lado, float dano) {
    }

    // The slam takes descidaTicks; then the target lies stuck in the hole for presoTicks.
    public record Esmagar(int descidaTicks, float dano, int presoTicks, boolean quebrarBlocos, double raioCratera, double profundidadeCratera) {
    }

    // The screen effects themselves live in the "vfx" list; poeira is how many dust specks fly out of the hole.
    public record Impacto(Onda onda, int poeira) {
    }

    public record Onda(boolean ativa, double velocidade, String cor) {
    }

    // camera: "baixo", "lado", "ombro" or "alvo" (through the eyes of whoever is grabbed). Only the two fighters see it.
    public record Cutscene(boolean ativa, String camera, int depoisTicks, int voltarTicks, boolean barrasPretas, boolean intangivel) {
    }

    public record Animacao(int transicaoTicks, SkillSpec.Pose dash, SkillSpec.Pose agarrar, SkillSpec.Pose levantar, SkillSpec.Pose esmagar) {
    }

    public static final String SKILL = "madara.agarrao";

    public static AgarraoSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, AgarraoSpec.class, awakened);
    }

    // Ticks from the grab to the slam landing.
    public int slamTick() {
        return Math.max(0, agarrar.levantarTicks()) + Math.max(0, agarrar.segurarTicks()) + Math.max(1, esmagar.descidaTicks());
    }

    // The body animation of one part of the move, same timing as the page: "dash" (wind-up into the dash pose, held
    // until the grab or the end) and "agarrar" (grab, lift, hold, slam, stay down a moment, back to normal).
    public PoseTimeline phase(String name, int dashTicks) {
        double tr = Math.max(1, animacao.transicaoTicks());
        if ("dash".equals(name)) {
            int prep = Math.max(0, tempo.preparoTicks());
            double end = prep + dashTicks;
            return PoseTimeline.of(end + tr * 3, t -> {
                if (t < prep) {
                    return SkillSpec.Pose.ZERO.lerp(animacao.dash(), smooth(t / Math.max(1, prep)));
                }
                return t < end ? animacao.dash() : animacao.dash().lerp(SkillSpec.Pose.ZERO, smooth((t - end) / (tr * 3)));
            });
        }
        int lift = Math.max(0, agarrar.levantarTicks());
        int held = lift + Math.max(0, agarrar.segurarTicks());
        int impact = slamTick();
        double down = impact + 10;
        return PoseTimeline.of(down + tr * 4, t -> {
            if (t < lift) {
                return t < tr ? animacao.dash().lerp(animacao.agarrar(), smooth(t / tr))
                        : animacao.agarrar().lerp(animacao.levantar(), smooth((t - tr) / Math.max(1, lift - tr)));
            }
            if (t < held) {
                return animacao.levantar();
            }
            if (t < impact) {
                return animacao.levantar().lerp(animacao.esmagar(), (t - held) / Math.max(1, impact - held));
            }
            return t < down ? animacao.esmagar() : animacao.esmagar().lerp(SkillSpec.Pose.ZERO, smooth((t - down) / (tr * 4)));
        });
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
