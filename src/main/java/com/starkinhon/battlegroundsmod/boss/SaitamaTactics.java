package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.powers.opm.Opm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// How the Saitama boss fights (Stark, 2026-11-12). The strategy is the character's: he takes it, hits back, GG.
// - Bored (above half life): he walks up slowly and lets them hit first (3 s at most; never against another boss), then the normal moves: the
//   consecutive punches, the hook (it cuts off whatever the target is winding up), the kick (stun), and the counter's
//   flick when they press him. Nothing pushes him (Pedra Viva).
// - Serious (half life down): every so often the face goes from "ok" to serious (V, the Modo Sério) and the next move is a
//   serious one, with a long warning first, dodgeable: the Saltos Laterais up close, the Virada de Mesa, the Espirro from
//   afar. The Soco Sério only under 20% of his life: its aim is fixed when the warning ends (the punch goes where he
//   looked, not where they went), so whoever gets out of the line lives.
// Between serious moves he fights with the normal ones.
final class SaitamaTactics extends TacticsBase {
    // At most 3 s waiting to be hit first (a boss that kites him with fire got minutes of free shots at 6 s a fight).
    private static final int BORED = 60;
    private static final double SERIOUS_LIFE = 0.5, PUNCH_LIFE = 0.2;
    // Ticks between two serious modes (his own clock: the V's slot is reset by the mode itself).
    private static final int SERIOUS_GAP_MIN = 420, SERIOUS_GAP_MAX = 600;

    private boolean provoked;
    private int waited;
    private long seriousAt;
    private long lastFight = -100_000;
    // Where the serious move that is playing aims (fixed when its warning ended), or null.
    private Vec3 locked;
    private String lockedFor;

    SaitamaTactics(SaitamaEntity boss) {
        super(boss);
    }

    private boolean serious() {
        return Opm.serious(boss);
    }

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null) {
            return false;
        }
        if (ai.now() - lastFight > 300) {
            provoked = false;
            waited = 0;
            seriousAt = ai.now() + 60;
        }
        lastFight = ai.now();
        if (!ai.usingMove()) {
            locked = null;
            lockedFor = null;
        }
        // Another boss is no bored fight: he takes it seriously from the start (Madara made him look like nothing).
        if (boss.getHealth() < boss.getMaxHealth() - 0.5f || boss.hurtTime > 0 || target instanceof SoulBoss) {
            provoked = true;
        }
        if (ai.usingMove() || Freeze.isFrozen(boss)) {
            return false;
        }
        // The face changes: serious from half life, every so often, the next move a serious one.
        if (!serious() && (myLife <= SERIOUS_LIFE || ai.chaos()) && ai.now() >= seriousAt && sees && distance <= 40
                && ai.has("modo_serio") && ai.use("modo_serio")) {
            seriousAt = ai.now() + SERIOUS_GAP_MIN + random.nextInt(SERIOUS_GAP_MAX - SERIOUS_GAP_MIN + 1);
            ai.say("ok -> serio");
            return true;
        }
        // Bored: walks up and lets them hit first.
        if (!provoked && !ai.chaos() && myLife > SERIOUS_LIFE && ai.combo() == null) {
            if (++waited > BORED) {
                provoked = true;
                return false;
            }
            if (distance > 3) {
                boss.getNavigation().moveTo(target, 0.5);
            } else {
                ai.halt();
            }
            ai.lookAt(Combat.center(target), 12);
            ai.say("entediado, esperando");
            return true;
        }
        return false;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        boolean serious = serious();
        if ("modo_serio".equals(name)) {
            return 0;
        }
        boolean seriousMove = "soco_serio".equals(name) || "saltos".equals(name) || "mesa".equals(name) || "espirro".equals(name);
        if (seriousMove != serious || serious && combo) {
            // In the mode only the serious moves; out of it never.
            return 0;
        }
        double m = 1;
        switch (name) {
            case "soco_serio" -> {
                if (myLife > PUNCH_LIFE) {
                    return 0;
                }
                m = 4;
            }
            case "saltos" -> m *= distance <= 10 ? 1.6 : 0.6;
            case "mesa" -> m *= distance >= 5 && distance <= 24 ? 1.5 : 0.8;
            case "espirro" -> {
                m *= distance >= 10 ? 1.6 : 0.7;
                m *= enemies >= 2 ? 1.5 : 1;
            }
            case "gancho" -> {
                // The hook cuts off a wind-up: best on someone charging something.
                m *= ai.busyTarget() ? 2.2 : 1;
            }
            case "chute" -> m *= ai.pressure() > 0 ? 1.4 : 1;
            case "socos" -> m *= ai.stunned() ? 1.8 : 1;
            default -> {
            }
        }
        m *= jitter();
        return score * m;
    }

    @Override
    public BossOrder.Order order(SoulBossAI ai, String name) {
        read(ai);
        LivingEntity target = ai.target();
        if (("soco_serio".equals(name) || "espirro".equals(name)) && target != null) {
            // The aim is fixed now: the move goes where he looks when the warning ends.
            locked = Combat.center(target);
            lockedFor = name;
        }
        if ("soco_serio".equals(name)) {
            BossFalas.sayNow(boss, "fala.battlegroundsmod.saitama.soco_serio");
        }
        return null;
    }

    @Override
    public Vec3 aim(SoulBossAI ai, String name) {
        return locked != null && name.equals(lockedFor) ? locked : null;
    }

    @Override
    public String mood() {
        return (serious() ? "serio" : provoked ? "entediado (provocado)" : "entediado") + " " + kind.name().toLowerCase();
    }
}
