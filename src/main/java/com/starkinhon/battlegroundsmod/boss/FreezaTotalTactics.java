package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.powers.freezaboss.FreezaTotal;

import net.minecraft.world.entity.LivingEntity;

// How Freeza Poder Total fights (the approved page). Arrogant and cruel, he plays with whoever fights him:
// - The Onda Imperial on whoever stays glued to him hitting (3 blows in 1.2 s): it is what stops "glue and beat" him.
// - The Kienzan and the Rajada at mid range, the island on runners far off, the Blitz to punish whoever stands still.
// - At half his life: the Poder Total right away (muscles, Namek falling apart, the geysers); the Chuva de Ki only then.
// - Tired (the clock running down) he skips turns and pants: patience is paid back.
final class FreezaTotalTactics extends TacticsBase {
    private static final double TOTAL_LIFE = 0.5;
    private boolean powered;
    private boolean saidTired;

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
        if (myLife <= TOTAL_LIFE && !powered && ai.has("poder_total")) {
            if (ai.use("poder_total")) {
                powered = true;
                ai.say("poder total");
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
        return false;
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
