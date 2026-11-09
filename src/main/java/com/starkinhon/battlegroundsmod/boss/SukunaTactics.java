package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.ModelBar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// How the Sukuna boss plays (Stark, 2026-11-09, thread "Criar boss do Sukuna"). Two moods, like in the anime: at first
// he is playing (the start of the series), later he is serious (Shibuya), with the whole kit.
//
// - Playing (phase 1, life above half): he walks in calmly and lets the target hit him first. Until something lands on
//   him (or he gets bored of waiting, 6 s) he only circles and stares. Then the punches, Desmantelar (taps and the tap
//   combo), the cuts on the opponent (Shift+Z) and the Clivar. No Fuga, no domain, no chant.
// - Serious (phase 2, half life, or as a plain mob): everything. The chant (Desmantelar held to a stage), the Fuga only
//   on a stunned target (the combos build the stun first: punches + strong, Clivar, the slam), the B slam on whoever
//   jumps or flees, the Clivar on whoever stands with a wall at their back (the wall breaks with them), the Domain
//   Expansion once per fight when the target is between 40% and 70% (or he is low), and the World Cutting Slash only
//   with the Modelo bar full, sooner when the target keeps defending (that is what fills it).
// If the target is a Gojo and opens his domain, the Sukuna answers with his own (DomainDuel's bots), into the duel.
final class SukunaTactics extends TacticsBase {
    // Waiting for the first hit at most this long before he gets bored and attacks.
    private static final int BORED = 120;
    // The domain: life range of the target where he opens it, and only once per fight.
    private static final double DOMAIN_LOW = 0.4, DOMAIN_HIGH = 0.7;

    private boolean provoked;
    private int waited;
    private boolean domainUsed;
    private long lastFight = -100_000;
    private int guardedHits;

    SukunaTactics(SukunaEntity boss) {
        super(boss);
    }

    private boolean serious() {
        // As a plain mob (no boss fight) he has everything from the start; in chaos (gamerule soulbotscaoticos) he
        // doesn't play around either.
        return !boss.bossfight() || boss.phase() >= 1 || BossRules.chaos(boss.level());
    }

    // ---------------------------------------------------------------- BossTactics

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null) {
            return false;
        }
        // A new fight (no target for 15 s): playing again, domain ready again.
        if (ai.now() - lastFight > 300) {
            provoked = false;
            waited = 0;
            domainUsed = false;
            guardedHits = 0;
        }
        lastFight = ai.now();
        if (boss.getHealth() < boss.getMaxHealth() - 0.5f || boss.hurtTime > 0) {
            provoked = true;
        }
        if (ModelBar.defended(boss, target) && ai.now() % 20 == 0) {
            guardedHits++;
        }
        // Playing: walks to them and lets them hit first.
        if (!serious() && !provoked && !ai.usingMove() && ai.combo() == null) {
            if (++waited > BORED) {
                provoked = true;
                return false;
            }
            if (distance > 4) {
                boss.getNavigation().moveTo(target, 0.55);
            } else {
                boss.getNavigation().stop();
                boss.getMoveControl().strafe(0, 0);
            }
            ai.lookAt(Combat.center(target), 15);
            ai.say("brincando, esperando o primeiro golpe");
            return true;
        }
        return false;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        double m = 1;
        boolean serious = serious();
        switch (name) {
            case "dominio" -> {
                if (domainUsed || !serious) {
                    return 0;
                }
                boolean window = theirLife >= DOMAIN_LOW && theirLife <= DOMAIN_HIGH || myLife <= 0.3;
                if (!window) {
                    return 0;
                }
                m = 3;
                m *= enemies >= 2 ? 1.5 : 1;
                m *= strongFoe ? 1.3 : 1;
            }
            case "mundo" -> {
                // The World Cutting Slash: only with the bar full; best on someone who keeps defending or a strong foe.
                if (!ModelBar.full(boss) || !sees || distance > 50) {
                    return 0;
                }
                m = 3;
                m *= guarded || guardedHits >= 3 ? 1.6 : 1;
                m *= strongFoe ? 1.4 : 1;
            }
            case "desmantelar_canto" -> {
                if (!serious || ai.pressure() > 0 || distance < 6) {
                    return 0;
                }
                m *= distance >= 10 ? 1.4 : 1;
                m *= ai.stunned() ? 1.8 : 1;
                m *= strongFoe ? 1.2 : 1;
            }
            case "desmantelar" -> {
                // Up close too: the cut leaves the hand right in their face (Stark).
                m *= distance <= 4 ? 1.8 : distance >= 6 ? 1.4 : 1.2;
                m *= fleeing ? 1.5 : 1;
                m *= crowd >= 1 ? 1.3 : 1;
            }
            case "cortes" -> {
                m *= distance >= 4 && distance <= 18 ? 1.3 : 1;
                m *= guarded ? 1.4 : 1;
            }
            case "clivar", "clivar_e_fuga" -> {
                m *= wallBehind(ai.target()) ? 2.2 : 1;
                m *= distance >= 6 ? 1.3 : 1;
                m *= air ? 0.4 : 1;
            }
            case "cravada", "cravada_e_fuga" -> {
                m *= air ? 2.2 : 1;
                m *= fleeing ? 1.8 : 1;
                m *= distance >= 8 ? 1.2 : 1;
            }
            case "porrada_e_fuga" -> {
                m *= distance <= 6 ? 1.4 : 0.7;
                m *= guarded ? 0.2 : 1;
            }
            case "fuga" -> {
                if (!serious) {
                    return 0;
                }
                m *= theirLife < 0.15 && !strongFoe ? 0.4 : 1;
            }
            default -> {
            }
        }
        // Playing: punches and quick cuts; serious: the techniques.
        if ("desmantelar".equals(name)) {
            // His main technique: as often playing as serious.
        } else if (!serious) {
            m *= combo || "forte".equals(name) || "barragem".equals(name) ? 1.3 : 0.8;
        } else {
            m *= "forte".equals(name) || "barragem".equals(name) ? 0.8 : 1.1;
        }
        m *= jitter();
        return score * m;
    }

    @Override
    public BossOrder.Order order(SoulBossAI ai, String name) {
        read(ai);
        String combo = ai.combo();
        return switch (name) {
            case "dominio" -> {
                domainUsed = true;
                yield null;
            }
            case "corte_mundial" -> {
                guardedHits = 0;
                yield new BossOrder.Order(true, 1, "MUNDO", false);
            }
            case "desmantelar_canto" -> {
                // The stage of the chant: the further and the more open they are, the longer he chants.
                double c = ai.stunned() ? range(0.7, 1, 0) : distance > 16 ? range(0.4, 1, 0) : range(0.2, 0.7, 0);
                yield BossOrder.Order.charged(c);
            }
            case "desmantelar" -> {
                if ("desmonte".equals(combo) || "recua_e_corta".equals(combo)) {
                    yield new BossOrder.Order(false, 0, "COMBO3", false);
                }
                // The tap combo (2 or 3), more often serious; a single tap to catch someone running.
                if (fleeing || distance > 20) {
                    yield BossOrder.Order.tap();
                }
                double r = random.nextDouble();
                double three = serious() ? 0.45 : 0.25;
                yield r < three ? new BossOrder.Order(false, 0, "COMBO3", false)
                        : r < three + 0.3 ? new BossOrder.Order(false, 0, "COMBO2", false) : BossOrder.Order.tap();
            }
            default -> null;
        };
    }

    // "recuar": opens a little room before the Desmantelar (Stark, 2026-11-09): a hop back, a dash back or to a side,
    // whichever is ready; nothing ready = he cuts point blank anyway.
    @Override
    public Boolean step(SoulBossAI ai, String step, int stepAge) {
        if (!"recuar".equals(step)) {
            return null;
        }
        LivingEntity target = ai.target();
        if (target == null) {
            return true;
        }
        ai.lookAt(Combat.center(target), 360);
        float side = random.nextBoolean() ? 1 : -1;
        double roll = random.nextDouble();
        boolean moved = roll < 0.35 ? Movement.backstep(boss)
                : roll < 0.65 ? Movement.tryPerform(boss, Movement.Kind.DASH, -1, 0)
                        : Movement.tryPerform(boss, Movement.Kind.DASH, 0, side);
        if (!moved) {
            moved = Movement.backstep(boss) || Movement.tryPerform(boss, Movement.Kind.DASH, 0, side);
        }
        ai.say(moved ? "abrindo distancia pro Desmantelar" : "queima-roupa");
        return true;
    }

    // The Desmantelar taps are his main technique (Stark, 2026-11-09: "quase tanto quanto ataca com golpes normais"):
    // they go between punches with no wait for the other techniques.
    @Override
    public boolean basic(String name) {
        return "desmantelar".equals(name);
    }

    @Override
    public String mood() {
        String m = serious() ? "serio" : provoked ? "brincando (provocado)" : "brincando";
        return m + " " + kind.name().toLowerCase() + String.format(" modelo %.0f%%", ModelBar.get(boss) * 100);
    }

    // A wall right behind the target (seen from him), within 3 blocks: the Clivar pins them to it and breaks through.
    private boolean wallBehind(LivingEntity target) {
        if (target == null) {
            return false;
        }
        Vec3 away = target.position().subtract(boss.position());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1.0E-4) {
            return false;
        }
        away = away.normalize();
        for (double d = 1; d <= 3; d += 1) {
            Vec3 p = target.position().add(away.scale(d)).add(0, 1, 0);
            BlockPos pos = BlockPos.containing(p);
            if (!boss.level().getBlockState(pos).getCollisionShape(boss.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
