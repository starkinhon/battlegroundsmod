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
// - At 60% life: Sage Mode and the Buddha of a Thousand Hands right away; again 40 s after it ends.
final class HashiramaTactics extends TacticsBase {
    private static final double SAGE_LIFE = 0.6;
    private long buddhaAgain;
    private boolean summoned;

    HashiramaTactics(HashiramaEntity boss) {
        super(boss);
    }

    void buddhaEnded() {
        buddhaAgain = boss.level().getGameTime() + 800;
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
        if (HsBuddha.active(boss)) {
            // on the Buddha's head: the hands do the fighting
            return true;
        }
        if (myLife <= SAGE_LIFE && (!summoned || ai.now() >= buddhaAgain) && ai.has("buda") && ai.isOff("buda")) {
            if (ai.use("buda")) {
                summoned = true;
                buddhaAgain = Long.MAX_VALUE;
                ai.say("buda das mil maos");
                BossFalas.sayNow(boss, "fala.battlegroundsmod.hashirama.buda");
                return true;
            }
        }
        // the dome when someone is right on him
        if (distance <= 3.2 && ai.has("cupula") && ai.isOff("cupula") && random.nextFloat() < 0.35f) {
            if (ai.use("cupula")) {
                ai.say("cupula");
                return true;
            }
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
            case "buda", "cupula" -> {
                return 0;
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

    @Override
    public String mood() {
        return (HsBuddha.active(boss) ? "buda" : myLife <= SAGE_LIFE ? "modo sabio" : "mokuton") + " " + kind.name().toLowerCase();
    }
}
