// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.aizen.Hogyoku;
import com.starkinhon.battlegroundsmod.power.powers.aizen.KyokaSuigetsu;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// How the Aizen boss fights (Stark, 2026-11-22: "a parte tatica do boss"). He never hurries and never trades blows:
// - The middle distance (8 to 14 blocks): the Raikōhō on whoever is far or in a group, the Sōkatsui to poke whoever comes
//   close, the Black Box on someone stunned or held still.
// - The Kyōka Suigetsu: the clone stays where he stood and he walks round behind the target, unseen; there the illusion
//   breaks and the first blow comes from behind (a strong punch or the Sōkatsui). He gives up after 4 s if he can't get there.
// - The Um Dedo + Dankū (B) waits for a hit: worth more against brawlers and while someone is pressing him.
// - From half life: the Reiatsu on everyone near, and the Hōgyoku once he has room (the cocoon is untouchable anyway).
//   In the form: the Fragor whenever it is back, the Hadō 99 once, when the target is low, there are several, or the
//   form is running out.
final class AizenTactics extends TacticsBase {
    private static final double LATE = 0.5;
    private static final int AMBUSH_MAX = 80;
    private static final int AMBUSH_WINDOW = 40;
    private static final double BEHIND = 2.4;

    private int hiddenFor = -1;
    private long ambushUntil;

    AizenTactics(AizenEntity boss) {
        super(boss);
    }

    private boolean ambush(SoulBossAI ai) {
        return ai.now() < ambushUntil;
    }

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null) {
            return false;
        }
        // Unseen: round behind the target, then the illusion breaks.
        if (KyokaSuigetsu.unseen(boss)) {
            hiddenFor++;
            Vec3 look = Combat.flatLook(target);
            Vec3 spot = target.position().subtract(look.scale(BEHIND));
            double toSpot = boss.position().distanceTo(spot);
            if (toSpot <= 1.6 || hiddenFor >= AMBUSH_MAX || Freeze.isFrozen(boss)) {
                boss.getNavigation().stop();
                ai.lookAt(Combat.center(target), 360);
                KyokaSuigetsu.breakIllusion(boss);
                ambushUntil = ai.now() + AMBUSH_WINDOW;
                hiddenFor = -1;
                ai.say("kyoka: atras dele");
                return true;
            }
            boss.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.25);
            ai.say("kyoka: andando sem ser visto");
            return true;
        }
        hiddenFor = -1;
        if (ai.usingMove() || Freeze.isFrozen(boss) || ai.stunned()) {
            return false;
        }
        // The evolved form: the Hadō 99 once at the right moment, the Fragor whenever it is back.
        if (Hogyoku.active(boss)) {
            boolean seen = sees && distance >= 5 && distance <= 40;
            if (seen && Hogyoku.hadoReady(boss)
                    && (theirLife <= 0.5 || enemies >= 2 || Hogyoku.left(boss) < 240 || ai.chaos())) {
                ai.lookAt(Combat.center(target), 360);
                if (Hogyoku.press(boss, PowerData.SLOT_SHIFT_ULT)) {
                    ai.say("hado 99");
                    return true;
                }
            }
            if (seen && Hogyoku.fragorReady(boss) && random.nextDouble() < 0.2) {
                ai.lookAt(Combat.center(target), 360);
                if (Hogyoku.press(boss, PowerData.SLOT_ULT)) {
                    ai.say("fragor");
                    return true;
                }
            }
        } else if ((myLife <= LATE || ai.chaos()) && ai.has("hogyoku") && ai.isOff("hogyoku")) {
            if (ai.use("hogyoku")) {
                ai.say("evolucao do hogyoku");
                return true;
            }
        }
        // The Kyōka Suigetsu: from a little way off, not every chance (he likes it to be a surprise).
        if (!Hogyoku.active(boss) && distance >= 4 && distance <= 22 && ai.has("kyoka") && ai.isOff("kyoka")
                && random.nextDouble() < (ai.chaos() ? 0.2 : 0.035) && ai.use("kyoka")) {
            hiddenFor = 0;
            ai.say("kyoka suigetsu");
            return true;
        }
        return false;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        LivingEntity target = ai.target();
        boolean held = target != null && (Freeze.isFrozen(target) || ai.stunned());
        boolean hidden = KyokaSuigetsu.unseen(boss);
        if (hidden) {
            return 0;
        }
        double m = 1;
        switch (name) {
            case "kyoka", "hogyoku" -> {
                // Only from act, at their moment.
                return 0;
            }
            case "black_box" -> {
                m *= held ? 1.8 : 1;
                m *= distance <= 20 ? 1.2 : 0.7;
                m *= theirLife <= 0.3 ? 1.4 : 1;
                m *= air ? 0.8 : 1;
            }
            case "raikoho" -> {
                m *= distance >= 10 ? 1.4 : 0.7;
                m *= crowd >= 1 || enemies >= 2 ? 1.5 : 1;
                m *= fleeing ? 1.4 : 1;
            }
            case "sokatsui" -> {
                m *= ambush(ai) ? 3 : 1;
                m *= distance <= 10 ? 1.3 : 0.5;
            }
            case "reiatsu" -> {
                m *= enemies >= 2 ? 1.8 : 1;
                m *= distance <= 12 ? 1.4 : 0.3;
                m *= kind == Kind.LUTADOR ? 1.4 : 1;
            }
            case "um_dedo" -> m *= kind == Kind.LUTADOR ? 1.5 : 1;
            case "forte" -> {
                m *= ambush(ai) && distance <= 4 ? 4 : 0.6;
                m *= counterReady(target) ? 0.15 : 1;
            }
            case "barragem" -> m *= 0.5;
            default -> {
            }
        }
        m *= jitter();
        return score * m;
    }

    @Override
    public BossOrder.Order order(SoulBossAI ai, String name) {
        read(ai);
        return null;
    }

    @Override
    public String mood() {
        String state = KyokaSuigetsu.unseen(boss) ? "ilusao" : Hogyoku.active(boss) ? "hogyoku" : myLife <= LATE ? "pressao" : "calmo";
        return state + " " + kind.name().toLowerCase();
    }
}
