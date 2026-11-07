package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.gojo.Azul;
import com.starkinhon.battlegroundsmod.skill.RoxoSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

// How the Gojo boss plays. SoulBossAI does the fighting; this reads the situation and tells it what Gojo would do.
//
// What he reads (once a tick): how far the target is, whether he sees them, whether they are in the air, running away,
// casting something, holding the Susanoo counter, how many are fighting him and how many stand next to the target,
// whether the world can be broken (gamerule soulsBlockDestruction), and WHO the target is (Madara, Bills, Aizen, another
// Gojo, a brawler like the Rubber Boy or the One Punch, someone with another power, or a plain mob or player).
//
// What he does with it:
// - Blue (Z): pulls whoever is in the air back down, pulls in whoever runs, sucks in groups (charged, chasing them or
//   parked and circling him), explodes on someone stunned (shot), keeps Aizen in place. Taps to bring a target into
//   his punches.
// - Red (X): a charged blast through a group standing in a line, from far away, through a wall (gamerule on), at a
//   Susanoo; a tap to blow off whoever is pressing him or a brawler up close; point blank at the end of his punches.
// - Red behind (Shift + X): to interrupt someone casting, to catch someone running, to finish someone low.
// - Purple (C): it takes five seconds to form, so only with room (far away, nobody hitting him) or when the target
//   can't move (stunned, caught in the domain); full size against groups and strong foes; erases cover.
// - Strong, barrage and the punch combos: when he is playing around, never into a Susanoo.
// - Infinity: an attack flying at him (a Katon, a meteor, a sphere, a Hakai ball) stops in the air before it touches
//   him, and he steps behind whoever threw it.
// - The ultimates, only at the end (Stark: "situações FINAIS, ultimate mesmo"): the Domain Expansion and the Unlimited
//   Purple (Blue at full charge parked on the target, then the Red at full charge into it) are only used when he is low
//   (30%), or half down against a strong foe, a crowd, or someone clearly winning; never one right after the other.
//
// So it doesn't play the same twice: a style he switches every 20 to 40 seconds and at each phase (playing around,
// serious, hunting, artillery, picked by the situation but never certain), every charge and plan rolled within what
// the situation allows, a little jitter on every weight, and the AI's own randomness on top.
final class GojoTactics extends TacticsBase {
    private enum Style {
        BRINCANDO("brincando"), SERIO("serio"), CACADOR("cacador"), ARTILHEIRO("artilheiro");

        final String label;

        Style(String label) {
            this.label = label;
        }
    }

    // Least time between the two ultimates, and the Unlimited Purple's own wait.
    private static final int ULT_GAP = 600;
    private static final int IRRESTRITO_COOLDOWN = 3600;
    private static final int INFINITY_COOLDOWN = 50;
    private static final int INFINITY_HOLD = 40;
    private static final double INFINITY_RANGE = 4.5;

    private Style style;
    private int styleLeft;
    private int stylePhase = -1;
    private long lastUlt = -100_000;
    private long irrestritoAt;
    private int infinityCooldown;

    GojoTactics(GojoEntity boss) {
        super(boss);
    }

    // Time to end it: low on life, or half down against a strong foe, a crowd, or someone clearly winning. Against
    // Bills a little earlier (under 20% his Hakai erases). Never right after the other ultimate.
    private boolean finalMoment(SoulBossAI ai) {
        if (ai.now() - lastUlt < ULT_GAP) {
            return false;
        }
        if (myLife <= 0.3) {
            return true;
        }
        double gate = kind == Kind.BILLS ? 0.6 : 0.55;
        return myLife <= gate && (enemies >= 3 || strongFoe || theirLife - myLife >= 0.25);
    }

    // ---------------------------------------------------------------- style

    private void pickStyle() {
        double play = !strongFoe && myLife > 0.6 ? 1.2 : 0.3;
        double serious = 0.5 + (strongFoe ? 1 : 0) + (myLife < 0.5 ? 0.8 : 0);
        double hunt = 0.8 + (fleeing ? 0.6 : 0) + (kind == Kind.AIZEN ? 0.3 : 0);
        double artillery = 0.7 + (kind == Kind.LUTADOR ? 0.8 : 0) + (enemies >= 2 ? 0.5 : 0);
        double[] weights = {play, serious, hunt, artillery};
        if (style != null) {
            // Rarely the same one twice in a row.
            weights[style.ordinal()] *= 0.4;
        }
        double roll = random.nextDouble() * (weights[0] + weights[1] + weights[2] + weights[3]);
        Style picked = Style.ARTILHEIRO;
        for (Style s : Style.values()) {
            roll -= weights[s.ordinal()];
            if (roll <= 0) {
                picked = s;
                break;
            }
        }
        style = picked;
        styleLeft = 400 + random.nextInt(401);
    }

    private double styleWeight(String name) {
        boolean melee = melee(name);
        return switch (style) {
            case BRINCANDO -> melee ? 1.4 : technique(name) ? 0.6 : 1;
            case SERIO -> name.equals("roxo") ? 1.5 : name.equals("vermelho") ? 1.2 : melee ? 0.8 : 1;
            case CACADOR -> name.equals("azul") ? 1.3 : name.equals("vermelho_atras") || name.equals("azul_e_porrada")
                    || name.equals("azul_black_flash")
                    || name.equals("puxa_e_explode") ? 1.5 : 1;
            case ARTILHEIRO -> name.equals("vermelho") ? 1.4 : name.equals("roxo") ? 1.3 : name.equals("azul") ? 1.2 : melee ? 0.6 : 1;
        };
    }

    private static boolean melee(String name) {
        return switch (name) {
            case "forte", "barragem", "infinito", "teletransporte", "o_mais_forte", "porrada_e_vermelho" -> true;
            default -> false;
        };
    }

    private static boolean technique(String name) {
        return switch (name) {
            case "azul", "vermelho", "vermelho_atras", "azul_black_flash", "roxo" -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- BossTactics

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        if (infinityCooldown > 0) {
            infinityCooldown--;
        }
        coolDown();
        if (--styleLeft <= 0 || style == null || stylePhase != boss.phase()) {
            stylePhase = boss.phase();
            pickStyle();
        }
        ServerLevel level = Combat.level(boss);
        // The Infinity: whatever flies at him stops before it touches him, and he is somewhere else when it moves again.
        if (infinityCooldown == 0 && ai.now() % 2 == 0) {
            Destructible.Attack threat = Destructible.nearestThreat(level, boss, INFINITY_RANGE);
            if (threat != null) {
                Vec3 center = Combat.center(boss);
                Destructible.holdAround(level, center, INFINITY_RANGE, INFINITY_HOLD, boss);
                infinityCooldown = INFINITY_COOLDOWN;
                Fx.sound(level, center, SoundEvents.BEACON_DEACTIVATE, 1.2f, 1.8f);
                Fx.ring(level, Fx.dust(0x9FE6FF, 1.4f), center, 1.6, 36, 0);
                Fx.sphere(level, Fx.dust(0xFFFFFF, 0.8f), center, 1.4, 40);
                if (!ai.stepBehind()) {
                    blinkAway(ai, 8);
                }
                ai.say("Infinito");
                return true;
            }
        }
        // Artillery: he doesn't let them stay on him; a blink back to his range.
        if (style == Style.ARTILHEIRO && !ai.usingMove() && ai.combo() == null && distance < 4 && blinkCooldown == 0
                && random.nextDouble() < 0.08 && blinkAway(ai, 9 + random.nextInt(5))) {
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
            case "dominio" -> {
                if (!finalMoment(ai)) {
                    return 0;
                }
                m = 4;
                m *= enemies >= 2 ? 1.5 : 1;
                m *= fleeing || distance > 12 ? 1.3 : 1;
                m *= theirLife < 0.15 && !strongFoe ? 0.3 : 1;
                return score * m;
            }
            case "roxo_irrestrito" -> {
                if (!finalMoment(ai) || ai.now() < irrestritoAt || distance > 50) {
                    return 0;
                }
                m = 4;
                m *= enemies >= 2 || crowd >= 1 ? 1.6 : 1;
                m *= strongFoe ? 1.4 : 1;
                m *= theirLife < 0.15 && !strongFoe ? 0.3 : 1;
                return score * m;
            }
            case "azul" -> {
                m *= air ? 1.8 : 1;
                m *= fleeing || distance > 14 ? 1.6 : 1;
                m *= crowd >= 1 ? 1.5 : 1;
                m *= distance < 4 ? 0.6 : 1;
                m *= guarded ? 1.3 : 1;
                m *= kind == Kind.AIZEN ? 1.3 : 1;
                m *= kind == Kind.LUTADOR && distance < 6 ? 0.7 : 1;
            }
            case "vermelho" -> {
                m *= crowd >= 1 ? 1.8 : 1;
                m *= distance >= 5 && distance <= 22 ? 1.3 : distance > 40 ? 0.6 : 1;
                m *= kind == Kind.LUTADOR && distance < 5 ? 1.6 : 1;
                m *= guarded ? 1.6 : 1;
                m *= kind == Kind.MADARA ? 1.2 : 1;
                m *= !sees ? 1.4 : 1;
                m *= ai.pressure() >= 2 ? 1.5 : 1;
            }
            case "vermelho_atras" -> {
                m *= ai.busyTarget() ? 2.2 : 1;
                m *= fleeing ? 1.6 : 1;
                m *= theirLife < 0.3 ? 1.5 : 1;
                m *= guarded ? 1.5 : 1;
                m *= kind == Kind.AIZEN ? 1.3 : 1;
                m *= crowd >= 2 ? 0.6 : 1;
            }
            case "azul_black_flash" -> {
                // Pulls them in for the Black Flash: best on someone running, guarding or worn down, at mid range.
                m *= distance >= 4 && distance <= 16 ? 1.4 : distance < 3 ? 0.6 : 1;
                m *= fleeing ? 1.7 : 1;
                m *= guarded ? 1.5 : 1;
                m *= theirLife < 0.4 ? 1.4 : 1;
                m *= !sees ? 0.3 : 1;
                m *= crowd >= 2 ? 0.7 : 1;
            }
            case "roxo" -> {
                // Five seconds to form: only with room, or when they can't get away.
                m *= distance < 8 && !stunned ? 0.3 : 1;
                m *= ai.pressure() > 0 ? 0.3 : 1;
                m *= stunned ? 2 : 1;
                m *= crowd >= 2 ? 1.6 : 1;
                m *= strongFoe ? 1.3 : 1;
                m *= !sees ? 1.5 : 1;
                m *= distance >= 15 ? 1.3 : 1;
                m *= kind == Kind.AIZEN && distance < 12 ? 0.5 : 1;
                m *= theirLife < 0.15 && !strongFoe ? 0.5 : 1;
            }
            case "azul_e_porrada" -> {
                m *= fleeing || distance > 10 ? 1.5 : 1;
                m *= guarded ? 0.2 : 1;
            }
            case "puxa_e_explode" -> {
                m *= crowd >= 1 ? 1.6 : 1;
                m *= air ? 1.4 : 1;
                m *= distance >= 6 && distance <= 20 ? 1.3 : 1;
            }
            case "porrada_e_vermelho", "infinito", "teletransporte", "o_mais_forte" -> {
                m *= guarded ? 0.2 : 1;
                m *= kind == Kind.LUTADOR ? 0.8 : 1;
            }
            default -> {
            }
        }
        m *= styleWeight(name);
        // A little noise, so even the same situation doesn't always end the same way.
        m *= 0.85 + 0.3 * random.nextDouble();
        return score * m;
    }

    @Override
    public BossOrder.Order order(SoulBossAI ai, String name) {
        read(ai);
        String combo = ai.combo();
        boolean stunned = ai.stunned();
        double bump = style == Style.SERIO ? 0.2 : style == Style.BRINCANDO ? -0.15 : 0;
        return switch (name) {
            case "dominio" -> {
                lastUlt = ai.now();
                yield null;
            }
            case "azul_irrestrito" -> {
                lastUlt = ai.now();
                irrestritoAt = ai.now() + IRRESTRITO_COOLDOWN;
                yield BossOrder.Order.charged(1, "IRRESTRITO");
            }
            case "vermelho_irrestrito" -> {
                BossFx.title(Combat.level(boss), boss, "Roxo Irrestrito", "", 0xB44DFF);
                yield new BossOrder.Order(true, 1, null, true);
            }
            case "azul" -> {
                if ("puxa_e_explode".equals(combo) || "azul_e_porrada".equals(combo)) {
                    // Pulls them in for what comes next.
                    yield BossOrder.Order.tap();
                }
                if (air) {
                    yield BossOrder.Order.charged(range(0.6, 1, bump), "CHASE");
                }
                if (crowd >= 1) {
                    yield BossOrder.Order.charged(range(0.8, 1, bump), random.nextBoolean() ? "CHASE" : "PARK_THEN_ORBIT");
                }
                if (stunned) {
                    yield BossOrder.Order.charged(range(0.5, 1, bump), "SHOOT");
                }
                if (fleeing || distance > 14) {
                    yield random.nextDouble() < 0.45 ? BossOrder.Order.tap() : BossOrder.Order.charged(range(0.4, 0.9, bump), "SHOOT");
                }
                if (distance < 5) {
                    yield random.nextDouble() < 0.6 ? BossOrder.Order.tap() : BossOrder.Order.charged(range(0.3, 0.7, bump), "PARK_THEN_ORBIT");
                }
                yield random.nextDouble() < (style == Style.BRINCANDO ? 0.5 : 0.3) ? BossOrder.Order.tap()
                        : BossOrder.Order.charged(range(0.3, 1, bump));
            }
            case "vermelho" -> {
                if ("porrada_e_vermelho".equals(combo)) {
                    yield BossOrder.Order.tap();
                }
                if ("puxa_e_explode".equals(combo)) {
                    yield BossOrder.Order.charged(range(0.35, 0.6, bump));
                }
                if (crowd >= 1) {
                    yield BossOrder.Order.charged(range(0.7, 1, bump));
                }
                if (stunned || guarded) {
                    yield BossOrder.Order.charged(range(0.6, 1, bump));
                }
                if (distance > 22) {
                    yield BossOrder.Order.charged(range(0.5, 1, bump));
                }
                if (distance < 8) {
                    yield BossOrder.Order.tap();
                }
                yield random.nextBoolean() ? BossOrder.Order.tap() : BossOrder.Order.charged(range(0.2, 0.7, bump));
            }
            case "azul_black_flash" -> {
                // Holds it in front of him for a heavier punch when they can't answer.
                if (stunned || theirLife < 0.35) {
                    yield BossOrder.Order.charged(range(0.6, 1, bump));
                }
                yield random.nextDouble() < 0.4 ? BossOrder.Order.tap() : BossOrder.Order.charged(range(0.2, 0.8, bump));
            }
            case "roxo" -> {
                if (stunned || crowd >= 2 || strongFoe && random.nextBoolean()) {
                    // The 200%.
                    yield BossOrder.Order.charged(range(0.7, 1, bump));
                }
                if (distance < 15 || fleeing || ai.pressure() > 0) {
                    yield BossOrder.Order.tap();
                }
                yield random.nextBoolean() ? BossOrder.Order.tap() : BossOrder.Order.charged(range(0.2, 1, bump));
            }
            default -> null;
        };
    }

    @Override
    public Vec3 aim(SoulBossAI ai, String name) {
        if ("vermelho_irrestrito".equals(name)) {
            Azul.Live blue = parkedBlue();
            return blue != null ? blue.pos() : null;
        }
        return null;
    }

    @Override
    public boolean throughWalls(SoulBossAI ai, String name) {
        read(ai);
        // With the world breakable the Red tunnels and the Purple erases its way to them.
        return breaks && distance <= 40 && ("vermelho".equals(name) || "roxo".equals(name));
    }

    @Override
    public Boolean step(SoulBossAI ai, String step, int stepAge) {
        if (!"esperar_azul".equals(step)) {
            return null;
        }
        read(ai);
        // The Unlimited Purple, between the Blue and the Red: he waits for his Blue to park on them, out of its pull.
        if (parkedBlue() != null) {
            return true;
        }
        if (stepAge > 90) {
            // It never parked (cut short, swallowed): no Purple this time.
            ai.dropCombo();
            return false;
        }
        boss.getNavigation().stop();
        boss.getMoveControl().strafe(0, 0);
        ai.lookAt(Combat.center(ai.target()), 20);
        if (distance < 6 && blinkCooldown == 0) {
            blinkAway(ai, 10);
        }
        ai.say("esperando o Azul travar");
        return false;
    }

    @Override
    public String mood() {
        if (style == null) {
            return "";
        }
        return style.label + " " + kind.name().toLowerCase() + (readAt >= 0 && myLife <= 0.6 ? String.format(" vida %.0f%%", myLife * 100) : "");
    }

    // ---------------------------------------------------------------- moving

    private Azul.Live parkedBlue() {
        PowerData data = boss.powerData();
        RoxoSpec roxo = RoxoSpec.get(data.awakened);
        double min = roxo.irrestrito() != null ? roxo.irrestrito().cargaMin() : 2;
        return Azul.parkedBlue(boss, min);
    }
}
