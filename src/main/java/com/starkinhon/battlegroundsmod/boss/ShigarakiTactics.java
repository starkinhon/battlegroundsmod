package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.powers.shigaraki.Absorver;

import net.minecraft.world.entity.LivingEntity;

// How the Shigaraki boss fights (Stark, 2026-11-12). He hates everything and plays dirty:
// - Above half life: the Toque da Decadência (best on someone held), the Onda Sônica on whoever is far or several, and
//   the Mãos do Chão as a trap: the hands catch, the Toque comes (the combo of the kit).
// - From half life: Absorver (he steals the target's power and uses it on them while it lasts), the Tentáculos de Mãos,
//   and once, low on life with room to breathe (far from them, or them held), the Regeneração.
// - At the very end (15% or less): the Fim do Mundo, like the Madara's meteor. He kneels about 2 s before the fingers
//   touch the ground; a strong blow in that time cuts it off (ShigarakiEntity), and he tries again later.
final class ShigarakiTactics extends TacticsBase {
    private static final double LATE = 0.5, REGEN_LIFE = 0.3, END_LIFE = 0.15;
    // The warning (avisoTicks in the file) plus FimDoMundo's kneel before the touch, and a margin.
    private static final int KNEEL = 10 + 30 + 2;
    private static final int END_RETRY = 240;

    private boolean regenUsed;
    private long endAgain;
    private long lastFight = -100_000;
    private SoulBossAI ai;

    ShigarakiTactics(ShigarakiEntity boss) {
        super(boss);
    }

    // A strong blow broke the kneel: the warning (if it was still on) is dropped too.
    void endCut() {
        if (ai != null) {
            ai.reset();
        }
        endAgain = boss.level().getGameTime() + END_RETRY;
        BossFx.shockwave(Combat.level(boss), boss, 0x8E1515, 2);
    }

    private boolean held(LivingEntity target) {
        return Freeze.isFrozen(target) || Capture.trapped(target);
    }

    @Override
    public boolean act(SoulBossAI ai) {
        this.ai = ai;
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null) {
            return false;
        }
        if (ai.now() - lastFight > 300 && boss.getHealth() >= boss.getMaxHealth() * 0.99f) {
            regenUsed = false;
        }
        lastFight = ai.now();
        if (ai.usingMove() || Freeze.isFrozen(boss)) {
            return false;
        }
        // The Fim do Mundo: at the end, when the cooldown and his last try allow.
        if (myLife <= END_LIFE && ai.now() >= endAgain && ai.has("fim") && ai.isOff("fim")) {
            ((ShigarakiEntity) boss).kneelUntil = ai.now() + KNEEL;
            if (ai.use("fim")) {
                endAgain = ai.now() + END_RETRY;
                ai.say("fim do mundo");
                return true;
            }
            ((ShigarakiEntity) boss).kneelUntil = 0;
        }
        // The Regeneração: once, low, with room (far, or them held); up close he blinks away first.
        if (!regenUsed && myLife <= REGEN_LIFE && ai.has("regeneracao") && ai.isOff("regeneracao")) {
            if (distance >= 8 || held(target)) {
                if (ai.use("regeneracao")) {
                    regenUsed = true;
                    ai.say("regenerando");
                    return true;
                }
            } else if (blinkCooldown <= 0 && blinkAway(ai, 12)) {
                ai.say("abrindo espaco pra regenerar");
                return true;
            }
        }
        return false;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        LivingEntity target = ai.target();
        boolean held = target != null && held(target);
        double m = 1;
        switch (name) {
            case "fim", "regeneracao" -> {
                // Only from act, at their moment.
                return 0;
            }
            case "toque" -> {
                if (!com.starkinhon.battlegroundsmod.power.powers.shigaraki.Toque.ready(boss)) {
                    return 0;
                }
                m *= held || ai.stunned() ? 2.5 : 1;
                m *= distance <= 10 ? 1.2 : 1;
            }
            case "onda" -> {
                m *= distance >= 8 ? 1.4 : 0.8;
                m *= enemies >= 2 || crowd >= 1 ? 1.6 : 1;
                m *= fleeing ? 1.3 : 1;
            }
            case "maos", "maos_e_toque" -> {
                m *= held ? 0.2 : 1;
                m *= distance <= 12 ? 1.3 : 0.8;
            }
            case "absorver" -> {
                // out of its 18 blocks or out of sight it only whiffs (b_miss: 10 of 12 casts in the 2026-11-13 retest)
                if (Absorver.holding(boss) || !Absorver.ready(boss) || distance > 16 || target == null || !boss.hasLineOfSight(target)) {
                    return 0;
                }
                m *= strongFoe ? 1.6 : 1;
                m *= distance <= 14 ? 1.2 : 0.6;
            }
            case "usar" -> {
                if (!Absorver.holding(boss) || !Absorver.ready(boss)) {
                    return 0;
                }
                // a stolen technique is the point of the steal: use it while it lasts (only 2 uses in 6 steals before)
                m = 4.0;
            }
            case "tentaculos" -> {
                m *= enemies >= 2 ? 1.5 : 1;
                m *= held || ai.stunned() ? 1.6 : 1;
                m *= myLife <= LATE ? 1.2 : 1;
            }
            default -> {
            }
        }
        if ("forte".equals(name) && counterReady(ai.target())) {
            m *= 0.15;
        }
        m *= jitter();
        return score * m;
    }

    @Override
    public BossOrder.Order order(SoulBossAI ai, String name) {
        ShigarakiEntity me = (ShigarakiEntity) boss;
        long now = boss.level().getGameTime();
        if ("fim".equals(name)) {
            BossFalas.sayNow(boss, "fala.battlegroundsmod.shigaraki.fim");
            // armored once the fingers touch (the kneel stays open to the blow that cuts it)
            me.armorFrom = me.kneelUntil;
            me.armorUntil = now + 10 + 192;
        } else if ("tentaculos".equals(name)) {
            me.armorFrom = now;
            me.armorUntil = now + 14 + 184;
        }
        return null;
    }

    @Override
    public String mood() {
        return (myLife <= END_LIFE ? "fim" : myLife <= LATE ? "sem limites" : "decadencia") + (Absorver.holding(boss) ? " (roubou)" : "")
                + " " + kind.name().toLowerCase();
    }
}
