package com.starkinhon.battlegroundsmod.skill;

// The body animation of any skill: a wind-up pose, then a main pose, then back to normal. Reads only the "tempo" and
// "animacao" blocks, which come in two shapes: atraso/duracao + preparo/sopro (Katon) or preparo/queda (Meteoro).
public record AnimationSpec(Tempo tempo, Animacao animacao) {

    public record Tempo(Integer atrasoTicks, Integer duracaoTicks, Integer preparoTicks, Integer quedaTicks, Integer fracoTicks) {
        int windUp() {
            return atrasoTicks != null ? atrasoTicks : orZero(preparoTicks);
        }

        int end() {
            return windUp() + orZero(fracoTicks) + (duracaoTicks != null ? duracaoTicks : orZero(quedaTicks));
        }
    }

    public record Animacao(int transicaoTicks, SkillSpec.Pose preparo, SkillSpec.Pose sopro, SkillSpec.Pose queda) {
        SkillSpec.Pose main() {
            return sopro != null ? sopro : queda != null ? queda : SkillSpec.Pose.ZERO;
        }

        SkillSpec.Pose windUp() {
            return preparo != null ? preparo : SkillSpec.Pose.ZERO;
        }
    }

    // Body pose at a time in ticks since the skill started, blending smoothly between the phases (same as the page).
    public SkillSpec.Pose poseAt(double t) {
        int at = tempo.windUp();
        int end = tempo.end();
        double tr = Math.max(1, animacao.transicaoTicks());
        if (t < at) {
            return SkillSpec.Pose.ZERO.lerp(animacao.windUp(), smooth(t / Math.min(tr, at)));
        }
        if (t < end) {
            return (at > 0 ? animacao.windUp() : SkillSpec.Pose.ZERO).lerp(animacao.main(), smooth((t - at) / tr));
        }
        return animacao.main().lerp(SkillSpec.Pose.ZERO, smooth((t - end) / tr));
    }

    // Ticks until the body is back to normal after the skill starts.
    public double animationEnd() {
        return tempo.end() + Math.max(1, animacao.transicaoTicks());
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
