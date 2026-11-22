// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.powers.DomainSpace;

import net.minecraft.world.entity.LivingEntity;

// How Toji fights (the approved page): a hunter, fast and patient, who reads whoever he fights.
// - Glued to him hitting (3 blows in 1.2 s): the Sumiço, he is gone and the kick comes from behind.
// - An expansion opened on him, or a counter stance ready (the Susanoo): the Lança Invertida, it cuts techniques.
// - Runners far off: the Sumiço; a crowd round the target or mid range: the Corrente's fan; up close the Nuvem's combo.
// - Under 25%: the Caçador de Feiticeiros right away, then whenever it is ready.
// - The big moves are his signature: if none came out for 8 s the next free turn goes to one of them, so the fight never
//   turns into punches only.
final class TojiTactics extends TacticsBase {
    private static final double ULT_LIFE = 0.25;
    private static final String[] BIG = {"nuvem", "corrente", "lanca", "sumico"};
    private static final int BIG_GAP = 160;
    private long lastBig = Long.MIN_VALUE;
    private final int[] lastCooldown = new int[BIG.length];

    TojiTactics(TojiEntity boss) {
        super(boss);
    }

    private static boolean held(LivingEntity t) {
        return Freeze.isFrozen(t) || Capture.trapped(t);
    }

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null || ai.usingMove() || held(boss)) {
            return false;
        }
        watchBig(ai);
        if (myLife <= ULT_LIFE && ai.has("cacador") && ai.isOff("cacador") && distance <= 80) {
            if (ai.use("cacador")) {
                ai.say("cacador de feiticeiros");
                lastBig = ai.now();
                return true;
            }
        }
        if (distance <= 4 && boss instanceof TojiEntity t && t.recentHits() >= 3 && ai.has("sumico") && ai.isOff("sumico")) {
            if (ai.use("sumico")) {
                ai.say("sumico (colado)");
                lastBig = ai.now();
                return true;
            }
        }
        if ((DomainSpace.inside(target) || DomainSpace.inside(boss) || guarded) && distance <= 30 && ai.has("lanca") && ai.isOff("lanca")) {
            if (ai.use("lanca")) {
                ai.say("lanca invertida");
                lastBig = ai.now();
                return true;
            }
        }
        return bigMove(ai, target);
    }

    private void watchBig(SoulBossAI ai) {
        long now = ai.now();
        if (lastBig == Long.MIN_VALUE) {
            // the first one about 3 s into the fight
            lastBig = now - BIG_GAP + 60;
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

    private boolean bigMove(SoulBossAI ai, LivingEntity target) {
        if (ai.now() - lastBig < BIG_GAP || !boss.hasLineOfSight(target)) {
            return false;
        }
        String best = null;
        double bestScore = 0;
        for (String name : BIG) {
            BossSpec.Skill skill = boss.spec().skills().get(name);
            if (skill == null || !ai.has(name) || !ai.isOff(name) || distance < skill.distanciaMin() || distance > skill.distanciaMax()) {
                continue;
            }
            double score = weigh(ai, name, false, skill.peso()) * (0.6 + random.nextDouble());
            if (score > bestScore) {
                bestScore = score;
                best = name;
            }
        }
        if (best == null || !ai.use(best)) {
            return false;
        }
        lastBig = ai.now();
        ai.say(best);
        return true;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        double m = 1;
        switch (name) {
            case "cacador" -> {
                if (myLife > ULT_LIFE) {
                    return 0;
                }
                m *= 3;
            }
            case "corrente" -> {
                m *= distance >= 6 && distance <= 50 ? 1.4 : 0.5;
                m *= enemies >= 2 || crowd >= 1 ? 1.5 : 1;
                m *= air ? 0.5 : 1;
            }
            case "nuvem" -> {
                m *= distance <= 30 ? 1.3 : 0.6;
                m *= enemies >= 2 ? 1.3 : 1;
            }
            case "lanca" -> {
                m *= distance >= 3 && distance <= 30 ? 1.3 : 0.5;
                m *= guarded ? 2 : 1;
            }
            case "sumico" -> {
                m *= distance >= 6 ? 1.3 : 0.7;
                m *= fleeing ? 1.6 : 1;
            }
            default -> {
            }
        }
        m *= jitter();
        return score * m;
    }

    @Override
    public String mood() {
        return (myLife <= ULT_LIFE ? "sem amarras" : "cacando") + " " + kind.name().toLowerCase();
    }
}
