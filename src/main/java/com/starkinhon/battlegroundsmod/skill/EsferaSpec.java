package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Bills' Shift + C, Esfera da Destruição Planetária (bills.esfera.json). He puts both hands behind his back and is
// gone in a flash of black streaks: he and the target are in outer space (a cutscene only the two of them see, see
// client/EsferaCutscene). Seen from the front as he appears there, then from over his shoulder looking down: the target
// lies below him, adrift, slowly falling towards the Earth far below. He charges the sphere between his hands, throws
// it, it implodes into a giant sun on the way, hits the target and carries it down towards the Earth. Back in the world he
// lands with the same black streaks and the target falls from the sky (queda.altura blocks up, queda.distancia blocks
// in front of him), fast, with the sun on top of it, and blows up in flames (vfx "chamas") into a crater.
// Everyone else sees both of them vanish and come back.
// Timeline in ticks from the press: 0 "uso" (hands behind his back), tempo.costasTicks "tp" (gone), then in space:
// tempo.chegadaTicks (he appears, seen from the front), tempo.alvoTicks (over his shoulder, the target adrift),
// tempo.cargaTicks (charging), tempo.lancarTicks (the throw), tempo.vooTicks (the sun flies to the target),
// tempo.explosaoTicks (the sun hits the target, "contato", and carries it down), then "voltar" (back in the world) and "impacto" when the target hits
// the ground. The client-only moments of the cutscene ("chegada", "implodir", "contato") are played on the two
// clients from the same vfx list.
public record EsferaSpec(String skill, String nome, Tempo tempo, Alvo alvo, Esfera esfera, Espaco espaco, Deriva deriva, Queda queda,
        Legenda legenda, Cutscene cutscene, Animacao animacao, float danoEspaco, List<Som> sons) {

    public record Tempo(int costasTicks, int chegadaTicks, int alvoTicks, int cargaTicks, int lancarTicks, int vooTicks, int explosaoTicks) {
    }

    public record Alvo(double alcance) {
    }

    // raioCarga: between his hands, fully charged; raioGigante: after it implodes on the way; raioQueda: on top of the
    // target as they come down from the sky in the world (raioGigante when left out); raioExplosao: how big the
    // explosion of flames grows.
    public record Esfera(double raioCarga, double raioGigante, double raioQueda, double raioExplosao) {
        public double fallRadius() {
            return raioQueda > 0 ? raioQueda : raioGigante;
        }
    }

    // The square Earth: a cube tamanho blocks wide, distancia blocks ahead of him, abaixo blocks below and lado blocks to
    // his left, turning slowly (girar, degrees per tick). estrelas: how many stars. The sky goes from corCeu (top) to
    // corHorizonte; the nebulas are drawn in the corNebulosa colors.
    public record Espaco(double tamanho, double distancia, double abaixo, double lado, double girar, int estrelas, String corCeu,
            String corHorizonte, List<String> corNebulosa) {
    }

    // Where the target floats in space, lying down: frente blocks ahead of him and abaixo blocks below, drifting towards
    // the Earth at velocidade blocks per tick and slowly turning (giro, degrees per tick).
    public record Deriva(double frente, double abaixo, double velocidade, double giro) {
    }

    // After the cutscene the target falls from altura blocks over the ground, distancia blocks in front of Bills, at
    // velocidade blocks per tick. Landing: dano to it, danoArea falling off to anyone else within raio, and a crater
    // raioCratera wide and profundidadeCratera deep.
    public record Queda(double altura, double distancia, double velocidade, float dano, double raio, float danoArea, double empurrao,
            boolean quebrarBlocos, double raioCratera, double profundidadeCratera) {
    }

    public record Legenda(String texto, String cor, int inicioTick, int fimTick, double tamanho) {
    }

    public record Cutscene(boolean ativa, boolean barrasPretas, boolean intangivel) {
    }

    // The poses: costas (hands behind his back), carregar (the sphere between his hands), lancar (the throw) and deriva
    // (the target lying adrift).
    public record Animacao(int transicaoTicks, SkillSpec.Pose costas, SkillSpec.Pose carregar, SkillSpec.Pose lancar, SkillSpec.Pose deriva) {
    }

    public static final String SKILL = "bills.esfera";

    public static EsferaSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, EsferaSpec.class, awakened);
    }

    // Gone from the world (the black streaks).
    public int tpTick() {
        return Math.max(1, tempo.costasTicks());
    }

    // In space from here (a few ticks of streaks first).
    public int spaceTick() {
        return tpTick() + 4;
    }

    public int aimTick() {
        return spaceTick() + Math.max(1, tempo.chegadaTicks());
    }

    public int chargeTick() {
        return aimTick() + Math.max(1, tempo.alvoTicks());
    }

    public int throwTick() {
        return chargeTick() + Math.max(1, tempo.cargaTicks());
    }

    public int flyTick() {
        return throwTick() + Math.max(1, tempo.lancarTicks());
    }

    public int blastTick() {
        return flyTick() + Math.max(1, tempo.vooTicks());
    }

    // The cutscene ends here: back in the world, the target starts to fall.
    public int endTick() {
        return blastTick() + Math.max(1, tempo.explosaoTicks());
    }

    // The body animations, timed like the cutscene: "bills" (hands behind his back, charging, the throw, held until he is
    // back) and "alvo" (the target lying adrift, from the moment it is taken to space).
    public PoseTimeline phase(String name) {
        double tr = Math.max(1, animacao.transicaoTicks());
        SkillSpec.Pose zero = SkillSpec.Pose.ZERO;
        if ("alvo".equals(name)) {
            SkillSpec.Pose drift = pose(animacao.deriva());
            double hold = endTick() - tpTick();
            return PoseTimeline.of(hold + tr, t -> t < hold ? zero.lerp(drift, smooth(t / tr)) : drift.lerp(zero, smooth((t - hold) / tr)));
        }
        SkillSpec.Pose back = pose(animacao.costas());
        SkillSpec.Pose charge = pose(animacao.carregar());
        SkillSpec.Pose thrown = pose(animacao.lancar());
        int charging = chargeTick();
        int throwing = throwTick();
        int end = endTick();
        return PoseTimeline.of(end + tr, t -> {
            if (t < charging) {
                return zero.lerp(back, smooth(t / tr));
            }
            if (t < throwing) {
                return back.lerp(charge, smooth((t - charging) / (tr * 2)));
            }
            if (t < end) {
                // The throw is a snap.
                return charge.lerp(thrown, smooth((t - throwing) / 3));
            }
            return thrown.lerp(zero, smooth((t - end) / tr));
        });
    }

    private static SkillSpec.Pose pose(SkillSpec.Pose pose) {
        return pose != null ? pose : SkillSpec.Pose.ZERO;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
