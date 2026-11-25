// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.powers.hashirama.Hashirama;
import com.starkinhon.battlegroundsmod.power.powers.hashirama.HsBuddha;

import net.minecraft.world.entity.LivingEntity;

// How the Hashirama boss fights (the approved page's two phases). Calm and in control, he reads the fight:
// - The Cúpula when someone comes in to hit him up close (and it is the punish for whoever hits it without thinking).
// - The Prisão on whoever is free, then a follow-up on the prisoner (stakes, lance, the dragon): never a second trap on
//   someone already held.
// - The Floresta and the Estacas at range and on runners, the Flores on a crowd or someone standing still, the Lança on
//   whoever dashes around at mid range, the Dragão as his ult.
// - At 60% life: Sage Mode and the Buddha of a Thousand Hands right away. Only ONE more summon in the fight, at 30% life
//   and at least 60 s after the first one ended (Stark 2026-11-25: "usa o Buda de madeira 20.000 vezes... abaixo da
//   metade fica meio impossível de matar"). Before, it came back 20 s after every end: up ~29 s, down 20 s, forever, and
//   nobody without range can touch him up there. Now the Buddha is a moment of the fight, not the whole second half.
// Harder (Stark 2026-11-22: "abusei ele, foi mt ez... lento, apanha feito patinho"), at the Freeza Poder Total's level:
// - Whoever stays glued hitting (3 blows in 1.2 s) always gets the Cúpula; with it recharging he steps out behind them.
// - The end-game moves are his signature: if none came out for a while (14 s, 9 s in Sage Mode) the next free turn goes
//   to the Floresta Profunda or the Dragões, so the fight never turns into punches only.
// The new moves mixed with the old (Stark 2026-11-22: "mistura com as skills que já tem, aí ele vai ser recheadão"):
// - Mar de Estacas and Prisão Gigante are the giant versions of the X and C slots (asked with an order, HsNew); each
//   comes at most every 25 s / 22 s, the old small ones fill the gaps.
// - Golem de Madeira in Sage Mode (a signature move too); the Substituição answers whoever glues to him with the Cúpula
//   recharging.
final class HashiramaTactics extends TacticsBase {
    private static final double SAGE_LIFE = 0.6;
    private static final String[] BIG = {"floresta", "dragao", "golem"};
    private static final String[] PICK = {"floresta", "dragao", "golem", "mar_estacas"};
    private static final int SEA_GAP = 500, PRISON_GAP = 440;
    private long seaAgain, prisonAgain;
    private static final int BIG_GAP = 280;
    private static final int BIG_GAP_SAGE = 180;
    // the second Buddha: at 30% life, 60 s after the first one ended at the soonest; never a third
    private static final double SECOND_BUDDHA_LIFE = 0.3;
    private static final int BUDDHA_GAP = 1200;
    private static final int MAX_BUDDHAS = 2;
    private long buddhaAgain;
    private boolean summoned;
    private boolean up;
    private int buddhas;
    private long lastBig = Long.MIN_VALUE;
    private final int[] lastCooldown = new int[BIG.length];

    HashiramaTactics(HashiramaEntity boss) {
        super(boss);
    }

    void buddhaEnded() {
        up = false;
        buddhaAgain = boss.level().getGameTime() + BUDDHA_GAP;
    }

    // Whether he may call the Buddha now: the first one as soon as he reaches 60% (kept trying until it is really up, a blow
    // in the warning cancels it), the second one only at 30% and after the gap.
    private boolean buddhaReady(SoulBossAI ai) {
        if (myLife > SAGE_LIFE || buddhas >= MAX_BUDDHAS) {
            return false;
        }
        if (!summoned) {
            return true;
        }
        return myLife <= SECOND_BUDDHA_LIFE && ai.now() >= buddhaAgain;
    }

    private boolean held(LivingEntity t) {
        return Freeze.isFrozen(t) || Capture.trapped(t);
    }

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null || ai.usingMove() || Freeze.isFrozen(boss) && !Hashirama.inDome(boss)) {
            return false;
        }
        watchBig(ai);
        if (HsBuddha.active(boss)) {
            if (!up) {
                up = true;
                summoned = true;
                buddhas++;
                buddhaAgain = Long.MAX_VALUE;
            }
            // on the Buddha's head: the hands do the fighting
            return true;
        }
        // The Buddha every fight (Stark 2026-11-22: "só usou as 1000 mãos 1 vez em umas 6 lutas"). Before, the summon was
        // marked done as soon as its warning started: a blow in the warning cancelled it (the dodge reaction) and he never
        // tried again. Now it only counts once the Buddha is really up, and he keeps trying until it is.
        if (buddhaReady(ai) && ai.has("buda") && ai.isOff("buda")) {
            if (ai.use("buda")) {
                ai.say("buda das mil maos");
                if (!summoned) {
                    BossFalas.sayNow(boss, "fala.battlegroundsmod.hashirama.buda");
                }
                return true;
            }
        }
        // glued to him hitting: the dome every time; with it recharging, out behind them
        int hits = boss instanceof HashiramaEntity h ? h.recentHits() : 0;
        if (distance <= 4 && hits >= 3) {
            if (ai.has("cupula") && ai.isOff("cupula") && ai.use("cupula")) {
                ai.say("cupula (anti-colar)");
                return true;
            }
            if (ai.has("troca") && ai.isOff("troca") && ai.use("troca")) {
                ai.say("substituicao de madeira");
                return true;
            }
            if (ai.stepBehind()) {
                ai.say("passo atras de quem cola");
                return true;
            }
            if (ai.has("forte") && ai.isOff("forte") && ai.use("forte")) {
                ai.say("golpe forte em quem cola");
                return true;
            }
        }
        // the dome when someone is right on him
        if (distance <= 3.2 && ai.has("cupula") && ai.isOff("cupula") && random.nextFloat() < 0.5f) {
            if (ai.use("cupula")) {
                ai.say("cupula");
                return true;
            }
        }
        return bigMove(ai, target);
    }

    // A big move just went out when its cooldown jumps up (picked by the AI or forced here).
    private void watchBig(SoulBossAI ai) {
        long now = ai.now();
        if (lastBig == Long.MIN_VALUE) {
            // the first one about 6 s into the fight
            lastBig = now - BIG_GAP + 120;
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

    // Too long without a big move: the next free turn is one of the ready ones in range.
    private boolean bigMove(SoulBossAI ai, LivingEntity target) {
        int gap = myLife <= SAGE_LIFE ? BIG_GAP_SAGE : BIG_GAP;
        if (ai.now() - lastBig < gap || held(boss) || !boss.hasLineOfSight(target)) {
            return false;
        }
        String best = null;
        double bestScore = 0;
        for (String name : PICK) {
            BossSpec.Skill skill = boss.spec().skills().get(name);
            if (skill == null || !ai.has(name) || !ai.isOff(name) || distance < skill.distanciaMin() || distance > skill.distanciaMax()
                    || "mar_estacas".equals(name) && ai.now() < seaAgain) {
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
        ai.say(best);
        return true;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        LivingEntity target = ai.target();
        boolean isHeld = target != null && held(target);
        double m = 1;
        switch (name) {
            case "buda", "cupula", "troca" -> {
                return 0;
            }
            case "mar_estacas" -> {
                if (ai.now() < seaAgain) {
                    return 0;
                }
                m *= distance >= 8 ? 1.5 : 0.6;
                m *= fleeing ? 1.4 : 1;
            }
            case "prisao_gigante" -> {
                if (ai.now() < prisonAgain || isHeld) {
                    return 0;
                }
                m *= distance <= 24 ? 1.5 : 0.5;
            }
            case "golem" -> {
                m *= distance >= 8 ? 1.5 : 0.8;
                m *= isHeld ? 1.4 : 1;
            }
            case "prisao" -> m *= isHeld ? 0 : distance <= 18 ? 1.4 : 0.6;
            case "estacas" -> {
                m *= distance >= 6 ? 1.3 : 0.7;
                m *= fleeing || isHeld ? 1.5 : 1;
            }
            case "floresta" -> {
                m *= distance >= 7 ? 1.3 : 0.8;
                m *= enemies >= 2 || crowd >= 1 ? 1.5 : 1;
            }
            case "flores" -> {
                m *= enemies >= 2 || crowd >= 1 ? 1.6 : 1;
                m *= isHeld ? 0.3 : 1;
            }
            case "lanca" -> {
                m *= distance >= 5 && distance <= 22 ? 1.3 : 0.6;
                m *= isHeld ? 1.8 : 1;
            }
            case "dragao" -> {
                m *= isHeld ? 1.6 : 1;
                m *= myLife <= 0.8 ? 1.3 : 0.8;
            }
            case "prisao_e_lanca", "prisao_e_estacas" -> m *= isHeld ? 0 : distance <= 18 ? 1.3 : 0.5;
            default -> {
            }
        }
        m *= jitter();
        return score * m;
    }

    // The giant versions of X and C go out with an order (HsNew reads it); their own gaps start now.
    @Override
    public com.starkinhon.battlegroundsmod.power.BossOrder.Order order(SoulBossAI ai, String name) {
        switch (name) {
            case "mar_estacas" -> {
                seaAgain = ai.now() + SEA_GAP;
                lastBig = ai.now();
                return com.starkinhon.battlegroundsmod.power.powers.hashirama.HsNew.epicOrder();
            }
            case "prisao_gigante" -> {
                prisonAgain = ai.now() + PRISON_GAP;
                return com.starkinhon.battlegroundsmod.power.powers.hashirama.HsNew.epicOrder();
            }
            default -> {
                return null;
            }
        }
    }

    @Override
    public String mood() {
        return (HsBuddha.active(boss) ? "buda" : myLife <= SAGE_LIFE ? "modo sabio" : "mokuton") + " " + kind.name().toLowerCase();
    }
}
