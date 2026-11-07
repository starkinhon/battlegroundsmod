package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.power.BossOrder;

// How the Bills boss plays. SoulBossAI does the fighting (calm, a god: no dodges, the hakaizinho on whatever is thrown
// at him); this reads the situation (TacticsBase) and tells it what Bills would do with everything he has.
//
// - Destruction Energy (Z): homes in, so good on someone running or in the air; charged on groups and strong foes,
//   thrown as soon as it forms on someone running.
// - The rain (Shift + Z): he rises untouchable, so it is also his way out of a beating, and against groups.
// - Destruction Bop (X): he appears in front of them and they freeze, sealed (not even a counter): to cut someone who
//   is casting, to catch someone running, against a raised Susanoo; then the Hakai or the Planetary Destruction while
//   they are stuck in the ground (combos peteleco_e_hakai, peteleco_e_planeta).
// - Destruction Sphere (C): on someone who can't move, on groups, from far away; never while being hit (it charges).
// - Planetary Destruction (B): groups, someone standing still (stunned, holding a Susanoo); not on someone almost dead.
// - Space Sphere (Shift + C): his ultimate trip, when he is half down, against a strong foe, or on someone stunned who
//   still has a lot of life; never one right after the other.
// - Hakai (V): erases whoever is low enough (SoulBossAI already wants nothing more); the Hakai ball goes through
//   walls, for whoever hides or runs.
// - Hakaizinho (Shift + V): kept to shoot attacks down while the target throws things (Madara, Gojo, another Bills, any
//   power); against someone with nothing to throw, he also snipes them with it through the walls.
// - Against the Gojo boss's Infinity (it holds thrown attacks) he uses what it can't stop: the Bop, the Hakai, the space
//   trip, the Planetary Destruction. Against a Madara he punches only while that Madara's counter is recharging.
//
// Styles he switches every 20 to 40 seconds and at each phase, picked by the situation but never certain: bored
// (punches, the Bop, the energy, barely trying), judge (the Hakai and what leads to it), destroyer (the big area
// moves) and annoyed (when he is hurt or the foe is strong: everything, faster).
final class BillsTactics extends TacticsBase {
    private enum Style {
        ENTEDIADO("entediado"), JUIZ("juiz"), DESTRUIDOR("destruidor"), IRRITADO("irritado");

        final String label;

        Style(String label) {
            this.label = label;
        }
    }

    private static final int ULT_GAP = 600;
    private static final int COUNTER_SLOT = 2;

    private Style style;
    private int styleLeft;
    private int stylePhase = -1;
    private long lastUlt = -100_000;
    private int snipeCooldown;

    // The Gojo boss: his Infinity holds thrown attacks.
    private boolean infinity;
    // The target is a Madara whose Susanoo counter is ready.
    private boolean theirCounter;

    BillsTactics(BillsEntity boss) {
        super(boss);
    }

    @Override
    protected void read(SoulBossAI ai) {
        long before = readAt;
        super.read(ai);
        if (readAt == before || ai.target() == null) {
            return;
        }
        infinity = ai.target() instanceof GojoEntity;
        theirCounter = kind == Kind.MADARA && theirReady(ai.target(), COUNTER_SLOT);
    }

    // The hakaizinho stays for defense against anyone who throws things.
    private boolean keepsHakaizinho() {
        return kind != Kind.COMUM && kind != Kind.LUTADOR;
    }

    // ---------------------------------------------------------------- style

    private void pickStyle() {
        double bored = !strongFoe && myLife > 0.6 ? 1.4 : 0.2;
        double judge = 0.6 + (theirLife < 0.5 ? 0.8 : 0) + (fleeing ? 0.4 : 0);
        double destroyer = 0.6 + (enemies >= 2 ? 0.8 : 0) + (strongFoe ? 0.4 : 0);
        double annoyed = (myLife < 0.7 ? 0.9 : 0.1) + (strongFoe ? 0.5 : 0);
        style = Style.values()[pick(new double[] {bored, judge, destroyer, annoyed}, style == null ? -1 : style.ordinal())];
        styleLeft = 400 + random.nextInt(401);
    }

    private double styleWeight(String name) {
        boolean melee = melee(name);
        return switch (style) {
            case ENTEDIADO -> melee ? 1.4 : name.equals("destruction_bop") || name.equals("destruction_energy") ? 1.2
                    : name.equals("destruction_touch") || name.equals("esfera") || name.equals("destruction_sphere") ? 0.5 : 1;
            case JUIZ -> name.equals("hakai") || name.equals("peteleco_e_hakai") ? 1.6 : name.equals("hakaizinho") ? 1.5
                    : name.equals("destruction_bop") ? 1.2 : 1;
            case DESTRUIDOR -> name.equals("destruction_touch") ? 1.6 : name.equals("destruction_sphere") || name.equals("peteleco_e_planeta") ? 1.5
                    : name.equals("destruction_energy_chuva") ? 1.4 : name.equals("esfera") ? 1.3 : melee ? 0.6 : 1;
            case IRRITADO -> name.equals("destruction_bop") ? 1.4 : melee || name.equals("destruction_energy") ? 1.3 : 1.1;
        };
    }

    private static boolean melee(String name) {
        return switch (name) {
            case "forte", "barragem", "julgamento", "punho_divino" -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- BossTactics

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        if (snipeCooldown > 0) {
            snipeCooldown--;
        }
        if (--styleLeft <= 0 || style == null || stylePhase != boss.phase()) {
            stylePhase = boss.phase();
            pickStyle();
        }
        // Someone with nothing to throw at him, hiding or running: the hakaizinho goes through the walls at them.
        if (!keepsHakaizinho() && !ai.usingMove() && ai.combo() == null && snipeCooldown == 0 && (style == Style.JUIZ || style == Style.IRRITADO)
                && (!sees || fleeing) && distance >= 8 && distance <= 60) {
            snipeCooldown = 40;
            if (random.nextDouble() < 0.5 && ai.use("hakaizinho")) {
                ai.say("hakaizinho na parede");
                return true;
            }
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
            case "esfera" -> {
                if (ai.now() - lastUlt < ULT_GAP) {
                    return 0;
                }
                boolean worth = myLife <= 0.5 || strongFoe || stunned && theirLife > 0.5 || enemies >= 3;
                m *= worth ? 1.6 : 0.3;
                m *= infinity ? 1.5 : 1;
                m *= theirLife < 0.15 && !strongFoe ? 0.3 : 1;
            }
            case "destruction_energy" -> {
                m *= fleeing ? 1.4 : 1;
                m *= air ? 1.3 : 1;
                m *= crowd >= 1 ? 1.2 : 1;
                m *= infinity ? 0.5 : 1;
            }
            case "destruction_energy_chuva" -> {
                // He rises untouchable: also his way out of a beating.
                m *= ai.pressure() >= 2 ? 2 : 1;
                m *= crowd >= 1 ? 1.5 : 1;
                m *= enemies >= 2 ? 1.4 : 1;
                m *= infinity ? 0.5 : 1;
            }
            case "destruction_bop" -> {
                m *= fleeing ? 1.5 : 1;
                m *= ai.busyTarget() ? 2 : 1;
                m *= guarded ? 1.8 : 1;
                m *= air ? 1.3 : 1;
                m *= infinity ? 1.6 : 1;
                m *= theirCounter ? 1.3 : 1;
            }
            case "destruction_sphere" -> {
                m *= stunned ? 1.8 : 1;
                m *= crowd >= 1 ? 1.6 : 1;
                m *= distance >= 15 ? 1.3 : 1;
                m *= ai.pressure() > 0 ? 0.4 : 1;
                m *= fleeing ? 0.8 : 1;
                m *= infinity ? 0.4 : 1;
            }
            case "destruction_touch" -> {
                m *= crowd >= 1 ? 1.8 : 1;
                m *= enemies >= 2 ? 1.4 : 1;
                m *= stunned ? 1.6 : 1;
                m *= guarded ? 1.6 : 1;
                m *= ai.pressure() > 0 ? 0.5 : 1;
                m *= fleeing ? 0.7 : 1;
                m *= theirLife < 0.15 && !strongFoe ? 0.5 : 1;
            }
            case "hakai" -> {
                m *= !sees ? 1.6 : 1;
                m *= fleeing ? 1.4 : 1;
                m *= infinity && theirLife >= 0.35 ? 0.4 : 1;
            }
            case "hakaizinho" -> {
                // As a move (finishing someone low and far): not while it is needed for defense.
                m *= keepsHakaizinho() ? 0.6 : 1.4;
            }
            case "peteleco_e_hakai" -> {
                m *= theirLife >= 0.2 && theirLife <= 0.5 ? 1.5 : 1;
                m *= guarded ? 1.6 : 1;
                m *= infinity ? 1.3 : 1;
            }
            case "peteleco_e_planeta" -> {
                m *= crowd >= 1 ? 1.5 : 1;
                m *= strongFoe ? 1.3 : 1;
            }
            case "julgamento", "punho_divino", "forte", "barragem" -> {
                m *= guarded ? 0.2 : 1;
                m *= theirCounter && distance < 6 ? 0.6 : 1;
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
        double bump = style == Style.DESTRUIDOR ? 0.1 : style == Style.ENTEDIADO ? -0.1 : 0;
        return switch (name) {
            case "esfera" -> {
                lastUlt = ai.now();
                yield null;
            }
            // Energy: 0 = thrown as soon as it forms, 1 = five seconds of charge.
            case "destruction_energy" -> {
                if (crowd >= 1 || strongFoe && random.nextBoolean() || ai.stunned()) {
                    yield BossOrder.Order.charged(range(0.35, 0.7, bump));
                }
                if (fleeing || ai.pressure() > 0) {
                    yield BossOrder.Order.charged(0);
                }
                yield BossOrder.Order.charged(range(0, 0.3, bump));
            }
            // Sphere: up to ten seconds of charge for a player; a boss lets go within the first two or three.
            case "destruction_sphere" -> {
                if (crowd >= 1 || ai.stunned()) {
                    yield BossOrder.Order.charged(range(0.1, 0.25, bump));
                }
                yield BossOrder.Order.charged(range(0.02, 0.12, bump));
            }
            default -> null;
        };
    }

    @Override
    public boolean throughWalls(SoulBossAI ai, String name) {
        read(ai);
        // The Hakai ball and the hakaizinho go through everything.
        return distance <= 40 && ("hakai".equals(name) || "hakaizinho".equals(name));
    }

    @Override
    public String mood() {
        if (style == null) {
            return "";
        }
        return style.label + " " + kind.name().toLowerCase() + (readAt >= 0 && myLife <= 0.6 ? String.format(" vida %.0f%%", myLife * 100) : "");
    }
}
