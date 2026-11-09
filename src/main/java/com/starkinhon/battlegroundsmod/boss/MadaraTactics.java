package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.PowerData;

// How the Madara boss plays. SoulBossAI does the fighting; this reads the situation (TacticsBase) and tells it what
// Madara would do with everything he has.
//
// - Katon (Z): the fire stream is not a thrown attack, so neither Bills' hakaizinho nor Gojo's Infinity can stop it:
//   his answer to both. Best at mid range, through a group (the cone catches them all), on someone running.
// - Giant Katon (Shift + Z): he jumps into the sky and charges, so only with room (nobody on him, far away), or on
//   someone who can't move; full charge on groups and strong foes. Breaks through cover with the world breakable.
// - Mangekyo (X): the genjutsu freezes everyone around and locks their skills: against a group, to cut someone who is
//   casting, against a raised Susanoo (it is no hit, the counter doesn't trigger), and before his big moves. Against
//   Bills it is the key: a frozen Bills can't shoot the meteor down (combo genjutsu_e_meteoro).
// - Susanoo (C): the counter while being pressed, more against brawlers; and raised against an attack flying at him
//   (a Hakai ball, a sphere, a Red), it takes the hit for him.
// - Neck grab (B): to catch someone running or casting, to close the gap; never into a counter. Then the Susanoo slash
//   while they are stuck in the ground (combo agarrao_e_corte).
// - Susanoo slash (Shift + C): 40 blocks wide and through the walls when the world breaks: groups, someone running
//   or hiding, far targets. Stark: used a lot.
// - Meteor (V): on someone who can't move, on groups; not while a Bills can shoot it down or a Gojo can stop it.
// - Double meteor (Shift + V): an ultimate, only at the end (low life, or half down against a strong foe, a crowd or
//   someone clearly winning).
// - Izanami (Shift + X): the escape from death, best when he is losing to a strong foe or they just spent their ult;
//   not when they are almost dead (it locks his skills for 30 seconds).
// - It reads the target's own cooldowns: punches into a Madara only while that Madara's counter is recharging, throws
//   at a Bills while his hakaizinho is recharging.
//
// Styles he switches every 20 to 40 seconds and at each phase, picked by the situation but never certain: duelist
// (taijutsu, grab, counter), katon (fire from range, keeps away), susanoo (slash, counter, meteors) and sharingan
// (genjutsu and what comes after it).
final class MadaraTactics extends TacticsBase {
    private enum Style {
        DUELISTA("duelista"), KATON("katon"), SUSANOO("susanoo"), SHARINGAN("sharingan");

        final String label;

        Style(String label) {
            this.label = label;
        }
    }

    private static final int ULT_GAP = 600;
    private static final int GUARD_COOLDOWN = 30;
    private static final double GUARD_RANGE = 5;
    // The Susanoo counter's slot, read on a Madara target.
    private static final int COUNTER_SLOT = 2;

    private Style style;
    private int styleLeft;
    private int stylePhase = -1;
    private long lastUlt = -100_000;
    private int guardCooldown;

    // Thrown attacks are wasted on him now: a Bills with the hakaizinho ready, or the Gojo boss's Infinity.
    private boolean wasted;
    // The target is a Madara whose Susanoo counter is ready.
    private boolean theirCounter;

    MadaraTactics(MadaraEntity boss) {
        super(boss);
    }

    @Override
    protected void read(SoulBossAI ai) {
        long before = readAt;
        super.read(ai);
        if (readAt == before || ai.target() == null) {
            return;
        }
        wasted = kind == Kind.BILLS && theirReady(ai.target(), PowerData.SLOT_SHIFT_ULT) || ai.target() instanceof GojoEntity;
        theirCounter = kind == Kind.MADARA && theirReady(ai.target(), COUNTER_SLOT);
    }

    private boolean finalMoment(SoulBossAI ai) {
        if (ai.now() - lastUlt < ULT_GAP) {
            return false;
        }
        if (myLife <= 0.35) {
            return true;
        }
        return myLife <= 0.6 && (enemies >= 3 || strongFoe || theirLife - myLife >= 0.25);
    }

    // ---------------------------------------------------------------- style

    private void pickStyle() {
        double duel = 0.8 + (kind == Kind.LUTADOR ? 0.4 : 0) + (myLife > 0.6 ? 0.3 : 0) + (kind == Kind.GOJO ? 0.4 : 0);
        double fire = 0.7 + (enemies >= 2 ? 0.5 : 0) + (kind == Kind.BILLS || kind == Kind.GOJO ? 0.5 : 0);
        double susanoo = 0.5 + (strongFoe ? 0.8 : 0) + (myLife < 0.5 ? 0.6 : 0) + (enemies >= 3 ? 0.5 : 0);
        double sharingan = 0.6 + (strongFoe ? 0.4 : 0) + (fleeing ? 0.3 : 0) + (kind == Kind.BILLS ? 0.5 : 0);
        style = Style.values()[pick(new double[] {duel, fire, susanoo, sharingan}, style == null ? -1 : style.ordinal())];
        styleLeft = 400 + random.nextInt(401);
    }

    private double styleWeight(String name) {
        boolean melee = melee(name);
        return switch (style) {
            case DUELISTA -> melee || name.equals("agarrao") || name.equals("susanoo") ? 1.4
                    : name.equals("katon_gigante") || name.equals("meteoro") ? 0.7 : 1;
            case KATON -> name.equals("katon") ? 1.6 : name.equals("katon_gigante") ? 1.5 : name.equals("fogo_cruzado") ? 1.3
                    : melee ? 0.6 : 1;
            case SUSANOO -> name.equals("susanoo_corte") ? 1.6 : name.equals("agarrao_e_corte") ? 1.5 : name.equals("susanoo") ? 1.4
                    : name.equals("meteoro") ? 1.3 : name.equals("meteoro_duplo") ? 1.2 : 1;
            case SHARINGAN -> name.equals("mangekyo") || name.equals("genjutsu_e_meteoro") ? 1.6 : name.equals("olhar_do_inferno") ? 1.5
                    : name.equals("agarrao") ? 1.3 : name.equals("izanami") ? 1.2 : 1;
        };
    }

    private static boolean melee(String name) {
        return switch (name) {
            case "forte", "barragem", "fogo_cruzado", "passo_sombra" -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- BossTactics

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        if (guardCooldown > 0) {
            guardCooldown--;
        }
        if (--styleLeft <= 0 || style == null || stylePhase != boss.phase()) {
            stylePhase = boss.phase();
            pickStyle();
        }
        if (ai.usingMove()) {
            return false;
        }
        // Something thrown at him: the Susanoo takes it, or he is not there when it arrives.
        if (guardCooldown == 0 && ai.now() % 2 == 0
                && Destructible.nearestThreat(Combat.level(boss), boss, GUARD_RANGE) != null) {
            guardCooldown = GUARD_COOLDOWN;
            if (ai.use("susanoo")) {
                ai.say("Susanoo no ataque");
                return true;
            }
            if (random.nextBoolean() ? ai.stepBehind() : blinkAway(ai, 7)) {
                ai.say("saiu do ataque");
                return true;
            }
        }
        // Katon style: he doesn't let them stay on him, a jump back to his range.
        // (Not in chaos, gamerule soulbotscaoticos: no keeping distance.)
        if (style == Style.KATON && !ai.chaos() && ai.combo() == null && distance < 4 && random.nextDouble() < 0.06
                && Movement.tryPerform(boss, Movement.Kind.ESQUIVA, -1, 0)) {
            ai.say("distancia");
            return true;
        }
        return false;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        if (style == null) {
            pickStyle();
        }
        double m = 1;
        boolean stunned = ai.stunned();
        switch (name) {
            case "meteoro_duplo" -> {
                if (!finalMoment(ai)) {
                    return 0;
                }
                m = 3;
                m *= enemies >= 2 || crowd >= 1 ? 1.5 : 1;
                m *= wasted && !stunned ? 0.3 : 1;
                m *= theirLife < 0.15 && !strongFoe ? 0.3 : 1;
            }
            case "katon" -> {
                m *= distance >= 9 && distance <= 18 ? 1.2 : 1;
                m *= crowd >= 1 ? 1.5 : 1;
                m *= fleeing ? 1.3 : 1;
                m *= air ? 0.7 : 1;
                // Nothing stops the fire stream: the answer to the hakaizinho and the Infinity.
                m *= wasted ? 1.6 : 1;
            }
            case "katon_gigante" -> {
                m *= ai.pressure() > 0 ? 0.4 : 1;
                m *= distance >= 15 ? 1.3 : 1;
                m *= crowd >= 1 ? 1.6 : 1;
                m *= stunned ? 1.5 : 1;
                m *= strongFoe ? 1.2 : 1;
                m *= !sees ? 1.3 : 1;
                m *= wasted ? 0.25 : 1;
            }
            case "mangekyo" -> {
                m *= crowd >= 1 ? 1.6 : 1;
                m *= ai.busyTarget() ? 1.8 : 1;
                m *= guarded ? 1.6 : 1;
                m *= strongFoe ? 1.3 : 1;
                m *= kind == Kind.BILLS ? 1.4 : 1;
                m *= theirLife < 0.15 ? 0.6 : 1;
            }
            case "susanoo" -> {
                m *= kind == Kind.LUTADOR ? 1.5 : 1;
                m *= enemies >= 2 ? 1.3 : 1;
            }
            case "agarrao" -> {
                m *= fleeing ? 1.8 : 1;
                m *= ai.busyTarget() ? 1.6 : 1;
                m *= distance >= 8 ? 1.3 : 1;
                m *= air ? 0.7 : 1;
                m *= guarded ? 0.2 : 1;
                m *= kind == Kind.GOJO ? 1.2 : 1;
            }
            case "meteoro" -> {
                m *= crowd >= 1 ? 1.6 : 1;
                m *= fleeing ? 0.7 : 1;
                m *= wasted && !stunned ? 0.2 : 1;
                m *= !sees ? 1.2 : 1;
            }
            case "izanami" -> {
                m *= strongFoe ? 1.5 : 1;
                m *= ai.punishing() ? 2 : 1;
                m *= myLife < 0.25 ? 2 : 1;
                m *= enemies >= 2 ? 1.3 : 1;
                // Almost won: not the time to lock his own skills for 30 seconds.
                m *= theirLife < 0.2 ? 0.2 : 1;
            }
            case "susanoo_corte" -> {
                // Stark: he uses it a lot.
                m *= 1.3;
                m *= crowd >= 1 ? 1.8 : 1;
                m *= fleeing ? 1.6 : 1;
                m *= distance >= 15 ? 1.3 : 1;
                m *= !sees ? 1.5 : 1;
                m *= guarded ? 1.4 : 1;
                m *= strongFoe ? 1.2 : 1;
                m *= wasted ? 0.35 : 1;
            }
            case "genjutsu_e_meteoro" -> {
                m *= crowd >= 1 ? 1.4 : 1;
                m *= strongFoe ? 1.3 : 1;
                // Frozen and locked, a Bills can't shoot the meteor down.
                m *= kind == Kind.BILLS ? 1.8 : 1;
            }
            case "agarrao_e_corte" -> {
                m *= fleeing ? 1.4 : 1;
                m *= strongFoe ? 1.2 : 1;
                m *= guarded ? 0.2 : 1;
            }
            case "olhar_do_inferno" -> {
                m *= strongFoe ? 1.2 : 1;
                m *= guarded ? 1.3 : 1;
            }
            case "fogo_cruzado", "passo_sombra", "forte", "barragem" -> {
                m *= guarded ? 0.2 : 1;
                m *= theirCounter && distance < 6 ? 0.6 : 1;
                m *= kind == Kind.LUTADOR ? 0.9 : 1;
            }
            default -> {
            }
        }
        m *= styleWeight(name);
        m *= jitter();
        return score * m;
    }

    @Override
    public BossOrder.Order order(SoulBossAI ai, String name) {
        read(ai);
        double bump = style == Style.KATON || style == Style.SUSANOO ? 0.15 : 0;
        return switch (name) {
            case "meteoro_duplo" -> {
                lastUlt = ai.now();
                yield null;
            }
            case "katon_gigante" -> {
                if (crowd >= 1 || strongFoe || ai.stunned()) {
                    yield BossOrder.Order.charged(range(0.85, 1, bump));
                }
                if (ai.pressure() > 0 || fleeing) {
                    yield BossOrder.Order.charged(range(0.35, 0.6, bump));
                }
                yield BossOrder.Order.charged(range(0.5, 1, bump));
            }
            default -> null;
        };
    }

    @Override
    public boolean throughWalls(SoulBossAI ai, String name) {
        read(ai);
        // With the world breakable the slash and the giant fireball cut their way to them, the meteor falls through.
        return breaks && distance <= 45 && ("susanoo_corte".equals(name) || "katon_gigante".equals(name) || "meteoro".equals(name));
    }

    @Override
    public String mood() {
        if (style == null) {
            return "";
        }
        return style.label + " " + kind.name().toLowerCase() + (wasted ? " (sem projetil)" : "")
                + (readAt >= 0 && myLife <= 0.6 ? String.format(" vida %.0f%%", myLife * 100) : "");
    }
}
