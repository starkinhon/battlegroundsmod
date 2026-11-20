// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.powers.freezaboss.FreezaTotal;

import net.minecraft.world.entity.LivingEntity;

// How Freeza Poder Total fights (the approved page). Arrogant and cruel, he plays with whoever fights him:
// - The Onda Imperial on whoever stays glued to him hitting (3 blows in 1.2 s): it is what stops "glue and beat" him.
// - The Kienzan and the Rajada at mid range, the Ilha Colossal on runners far off, the Blitz to punish whoever stands still.
// - From far off the Corte do Planeta and the Dois Dedos; at 100% the Death Ball (FzEpic, the end-game scale).
// - At half his life: the Poder Total right away (muscles, Namek falling apart, the geysers); the Chuva de Ki only then.
// - Tired (the clock running down) he skips turns and pants: patience is paid back.
// - The big moves are his signature (Stark, 2026-11-20: "mal usou alguma tecnica foda da previa"): if none came out for
//   a while (14 s at 50%, 9 s at 100%) the next free turn goes to one of them, so the fight never turns into kicks only.
final class FreezaTotalTactics extends TacticsBase {
    private static final double TOTAL_LIFE = 0.5;
    private static final String[] BIG = {"death_ball", "corte", "dedos", "ilha", "chuva"};
    private static final int BIG_GAP_50 = 280;
    private static final int BIG_GAP_100 = 180;
    private boolean powered;
    private boolean saidTired;
    private long lastBig = Long.MIN_VALUE;
    private final int[] lastCooldown = new int[BIG.length];

    FreezaTotalTactics(FreezaTotalEntity boss) {
        super(boss);
    }

    private boolean held(LivingEntity t) {
        return Freeze.isFrozen(t) || Capture.trapped(t);
    }

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null || ai.usingMove() || Freeze.isFrozen(boss)) {
            return false;
        }
        watchBig(ai);
        if (myLife <= TOTAL_LIFE && !powered && ai.has("poder_total")) {
            if (ai.use("poder_total")) {
                powered = true;
                ai.say("poder total");
                // the first big move comes soon after the transformation
                lastBig = ai.now() - BIG_GAP_100 + 60;
                return true;
            }
        }
        if (distance <= 3.5 && boss instanceof FreezaTotalEntity f && f.recentHits() >= 3 && ai.has("onda") && ai.isOff("onda")) {
            if (ai.use("onda")) {
                ai.say("onda imperial");
                return true;
            }
        }
        float tired = FreezaTotal.tired(boss);
        if (tired > 0.3f && random.nextFloat() < 0.25f * tired) {
            if (!saidTired && tired > 0.6f) {
                saidTired = true;
                BossFalas.sayNow(boss, "fala.battlegroundsmod.freeza_total.cansado");
            }
            // panting: this turn goes by
            return true;
        }
        return bigMove(ai, target, tired);
    }

    // A big move just went out when its cooldown jumps up (picked by the AI or forced here).
    private void watchBig(SoulBossAI ai) {
        long now = ai.now();
        if (lastBig == Long.MIN_VALUE) {
            // the first one about 7 s into the fight
            lastBig = now - BIG_GAP_50 + 140;
        }
        for (int i = 0; i < BIG.length; i++) {
            BossSpec.Skill skill = boss.spec().skills().get(BIG[i]);
            if (skill == null) {
                continue;
            }
            int cd = boss.powerData().cooldowns[skill.slot()];
            if (cd > lastCooldown[i]) {
                lastBig = now;
            }
            lastCooldown[i] = cd;
        }
    }

    // Too long without a big move: the next free turn is one, out of the ones ready and in range (the Death Ball first
    // at 100%). Tired he takes longer to find the breath for one.
    private boolean bigMove(SoulBossAI ai, LivingEntity target, float tired) {
        int gap = FreezaTotal.inTotal(boss) ? BIG_GAP_100 : BIG_GAP_50;
        if (ai.now() - lastBig < gap * (1 + tired) || held(boss) || !boss.hasLineOfSight(target)) {
            return false;
        }
        String best = null;
        double bestScore = 0;
        for (String name : BIG) {
            BossSpec.Skill skill = boss.spec().skills().get(name);
            if (skill == null || !ai.has(name) || !ai.isOff(name) || distance < skill.distanciaMin() || distance > skill.distanciaMax()) {
                continue;
            }
            if ("death_ball".equals(name) && !FreezaTotal.inTotal(boss)) {
                continue;
            }
            double score = skill.peso() * (0.6 + random.nextDouble());
            if (score > bestScore) {
                bestScore = score;
                best = name;
            }
        }
        if (best == null || !ai.use(best)) {
            return false;
        }
        lastBig = ai.now();
        ai.say(best.replace('_', ' '));
        return true;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        LivingEntity target = ai.target();
        boolean isHeld = target != null && held(target);
        double m = 1;
        switch (name) {
            case "poder_total", "onda" -> {
                return 0;
            }
            case "kienzan" -> m *= distance >= 6 && distance <= 26 ? 1.3 : 0.6;
            case "ilha" -> {
                m *= distance >= 7 ? 1.3 : 0.6;
                m *= fleeing ? 1.4 : 1;
            }
            case "rajada" -> {
                m *= distance >= 5 && distance <= 20 ? 1.4 : 0.6;
                m *= enemies >= 2 ? 1.3 : 1;
            }
            case "blitz" -> {
                m *= distance >= 3 && distance <= 16 ? 1.4 : 0.5;
                m *= isHeld ? 0.4 : 1;
            }
            case "chuva" -> m *= distance >= 6 ? 1.5 : 0.8;
            // the end-game moves (FzEpic): the Death Ball only at 100%, the others from far enough to be dodged
            case "death_ball" -> {
                if (!FreezaTotal.inTotal(boss)) {
                    return 0;
                }
                m *= distance >= 10 ? 1.8 : 0.6;
            }
            case "corte" -> {
                m *= distance >= 8 ? 1.4 : 0.6;
                m *= enemies >= 2 ? 1.3 : 1;
            }
            case "dedos" -> {
                m *= distance >= 8 ? 1.3 : 0.6;
                m *= isHeld || !fleeing ? 1.3 : 1;
            }
            default -> {
            }
        }
        m *= jitter();
        return score * m;
    }

    @Override
    public String mood() {
        return (FreezaTotal.inTotal(boss) ? "100%" : "50%") + " " + kind.name().toLowerCase();
    }
}
