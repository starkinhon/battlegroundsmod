package com.starkinhon.battlegroundsmod.skill;

// The body animation of any skill: a wind-up pose, then a main pose, then back to normal. Reads only the "tempo" and
// "animacao" blocks, which come in two shapes: atraso/duracao + preparo/sopro (Katon) or preparo/queda (Meteoro).
public record AnimationSpec(Tempo tempo, Animacao animacao) implements PoseTimeline {

    public record Tempo(Integer atrasoTicks, Integer duracaoTicks, Integer preparoTicks, Integer quedaTicks, Integer fracoTicks) {
        public int windUp() {
            return atrasoTicks != null ? atrasoTicks : orZero(preparoTicks);
        }

        int end() {
            return windUp() + orZero(fracoTicks) + (duracaoTicks != null ? duracaoTicks : orZero(quedaTicks));
        }
    }

    // retornoTicks: how long the way back to normal takes, when it should differ from transicaoTicks (a snappy punch
    // that eases back).
    public record Animacao(int transicaoTicks, Integer retornoTicks, SkillSpec.Pose preparo, SkillSpec.Pose sopro, SkillSpec.Pose queda) {
        SkillSpec.Pose main() {
            return sopro != null ? sopro : queda != null ? queda : SkillSpec.Pose.ZERO;
        }

        SkillSpec.Pose windUp() {
            return preparo != null ? preparo : SkillSpec.Pose.ZERO;
        }

        double back() {
            return Math.max(1, retornoTicks != null ? retornoTicks : transicaoTicks);
        }
    }

    // Body pose at a time in ticks since the skill started, blending smoothly between the phases (same as the page).
    @Override
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
        return animacao.main().lerp(SkillSpec.Pose.ZERO, smooth((t - end) / animacao.back()));
    }

    // Ticks until the body is back to normal after the skill starts.
    @Override
    public double animationEnd() {
        return tempo.end() + animacao.back();
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
