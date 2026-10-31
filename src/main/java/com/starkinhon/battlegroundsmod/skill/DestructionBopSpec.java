package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Bills' Destruction Bop (bills.destruction_bop.json): he appears in front of the target and freezes them, flicks them
// up into the air with one finger, vanishes, comes back above and behind them and slaps them down into the ground.
// The target is stunned and sealed the whole time, just taking it.
// Sound and vfx momentos: "uso" (start), "sumir" / "aparecer" (each time he vanishes and comes back), "parar" (the
// target freezes), "peteleco" (the flick), "subir" (the target flying up), "tapa" (the slap), "chao" (the target hits
// the ground), "pousar" (he is back on the ground).
public record DestructionBopSpec(String skill, String nome, Alvo alvo, Tempo tempo, Peteleco peteleco, Tapa tapa, Chao chao,
        Rastro rastro, Cutscene cutscene, Animacao animacao, List<Som> sons) {

    // alcance: how far he can pick a target. distancia: how far in front of them he appears.
    public record Alvo(double alcance, double distancia) {
    }

    // From the moment he appears: pararTicks frozen before the flick, subirTicks flying up, pairarTicks hanging up there
    // (he vanishes at the start of it and comes back aparecerAntesTicks before the slap), descidaTicks falling down,
    // depoisTapaTicks he stays up in the air after the slap before landing.
    public record Tempo(int pararTicks, int subirTicks, int pairarTicks, int aparecerAntesTicks, int descidaTicks, int depoisTapaTicks) {
    }

    // altura: how many blocks the flick sends the target up (less if there's a ceiling).
    public record Peteleco(float dano, double altura) {
    }

    // Where he comes back for the slap: atras blocks behind the target (seen from where he was) and acima blocks above.
    public record Tapa(float dano, double atras, double acima) {
    }

    // The fall: dano on landing, presoTicks stuck in the hole, the hole itself and poeira dust specks.
    public record Chao(float dano, int presoTicks, boolean quebrarBlocos, double raioCratera, double profundidadeCratera, int poeira,
            String corOnda, double velocidadeOnda) {
    }

    // densidade: wind streaks per tick around the target while it flies up and falls.
    public record Rastro(boolean ativa, int densidade, String cor, String cor2) {
    }

    // Only the two fighters see it. depoisTicks: after the landing before the camera goes back over voltarTicks.
    public record Cutscene(boolean ativa, int depoisTicks, int voltarTicks, boolean barrasPretas, boolean intangivel) {
    }

    // Bills: preparo (finger ready), peteleco (the flick), olhar (watching them go up), tapaPreparo (hand up, in the
    // air), tapa (the slap). The target: alvoSubir (thrown up) and alvoCair (falling).
    public record Animacao(int transicaoTicks, SkillSpec.Pose preparo, SkillSpec.Pose peteleco, SkillSpec.Pose olhar, SkillSpec.Pose tapaPreparo,
            SkillSpec.Pose tapa, SkillSpec.Pose alvoSubir, SkillSpec.Pose alvoCair) {
    }

    public static final String SKILL = "bills.destruction_bop";

    public static DestructionBopSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, DestructionBopSpec.class, awakened);
    }

    // Ticks from the moment he appears.
    public int flickTick() {
        return Math.max(1, tempo.pararTicks());
    }

    public int topTick() {
        return flickTick() + Math.max(1, tempo.subirTicks());
    }

    public int slapTick() {
        return topTick() + Math.max(1, tempo.pairarTicks());
    }

    public int vanishTick() {
        return topTick();
    }

    public int appearTick() {
        return Math.max(vanishTick() + 1, slapTick() - Math.max(1, tempo.aparecerAntesTicks()));
    }

    public int groundTick() {
        return slapTick() + Math.max(1, tempo.descidaTicks());
    }

    public int landTick() {
        return Math.max(groundTick(), slapTick() + Math.max(0, tempo.depoisTapaTicks()));
    }

    // "bills" is his whole move; "alvo" is what the target's body does.
    public PoseTimeline phase(String name) {
        double tr = Math.max(1, animacao.transicaoTicks());
        int flick = flickTick();
        int vanish = vanishTick();
        int appear = appearTick();
        int slap = slapTick();
        int land = landTick();
        if ("alvo".equals(name)) {
            int ground = groundTick();
            double free = ground + Math.max(0, chao.presoTicks());
            return PoseTimeline.of(free + tr * 4, t -> {
                if (t < flick) {
                    return SkillSpec.Pose.ZERO;
                }
                if (t < slap) {
                    return SkillSpec.Pose.ZERO.lerp(animacao.alvoSubir(), smooth((t - flick) / tr));
                }
                if (t < free) {
                    return animacao.alvoSubir().lerp(animacao.alvoCair(), smooth((t - slap) / tr));
                }
                return animacao.alvoCair().lerp(SkillSpec.Pose.ZERO, smooth((t - free) / (tr * 4)));
            });
        }
        return PoseTimeline.of(land + tr * 4, t -> {
            if (t < flick) {
                return SkillSpec.Pose.ZERO.lerp(animacao.preparo(), smooth(t / Math.min(flick, tr * 3)));
            }
            if (t < vanish) {
                SkillSpec.Pose hit = animacao.preparo().lerp(animacao.peteleco(), smooth((t - flick) / tr));
                double after = flick + tr + 3;
                return t < after ? hit : animacao.peteleco().lerp(animacao.olhar(), smooth((t - after) / (tr * 2)));
            }
            if (t < slap) {
                // Hidden until he comes back with the hand already up.
                return t < appear ? animacao.olhar() : animacao.tapaPreparo();
            }
            if (t < land) {
                return animacao.tapaPreparo().lerp(animacao.tapa(), smooth((t - slap) / tr));
            }
            return animacao.tapa().lerp(SkillSpec.Pose.ZERO, smooth((t - land) / (tr * 4)));
        });
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
