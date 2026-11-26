// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

// Bosses, soul bots and SubAlma mobs caught in an expansion (Gojo's Vazio Infinito, Sukuna's Santuário) try the same way
// out a player has (Stark 2026-11-25: they stood still and took it every time, Gojo and Sukuna were gods against them).
//
// A player holds off the domain with the stamina left and runs out of it. A mob has no stamina bar, so when it is caught
// it gets a budget in its place (the stamina it "had left" from the fight): Stamina.resists and Stamina.drain read it,
// so the domains treat it exactly like a player (Gojo's slowness and drain, the Santuário's wait and drain, out past the
// raio = free, at zero = paralyzed like everyone). While it has some it runs out the shortest way, with the moves every
// fighter has (dash, air jump), and its boss AI stays out of the way (SoulBossAI.tick reads fleeing).
//
// Human, not perfect: it doesn't always try (some freeze up), it takes a moment to react, its line out is a little off
// and gets corrected on the way, and now and then it hesitates mid-run. The rules being a player's, from the middle of
// Gojo's domain it rarely gets out; near the edge it often does.
// A Gojo or Sukuna bot about to answer with its own domain (DomainDuel's clash) doesn't run; if the answer doesn't come,
// it falls back to running like the others.
public final class DomainEscape {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final Map<LivingEntity, Run> RUNS = new WeakHashMap<>();

    // Tuning (Stark 2026-11-26, playtest of #571: "estão fugindo demais", it was easier to miss than to catch them). The
    // domain catching is the norm; getting out is the exception, mostly for one caught near the edge.
    // Chance to try at all, by tier: half of the divine ones, fewer of the rest. Bosses a bit more.
    private static final double TRY_DIVINE = 0.5, TRY_MID = 0.4, TRY_WEAK = 0.25, TRY_BOSS_BONUS = 0.1, TRY_MAX = 0.6;
    // The stamina "left" when caught, percent of a full bar (a boss fights fresher): at the domains' 20%/s that is
    // 1.25 to 3.5 s of running.
    private static final double BUDGET_MIN = 25, BUDGET_MAX = 60, BUDGET_BOSS_MIN = 35, BUDGET_BOSS_MAX = 70;
    // Reaction before the first step (ticks): a boss is quicker. The budget drains while it reacts.
    private static final int REACT_MIN = 12, REACT_SPREAD = 18, REACT_BOSS_MIN = 8, REACT_BOSS_SPREAD = 10;
    // How far off the best line it starts (radians): mostly a little, often a lot (a bad read).
    private static final double JITTER_SMALL = Math.toRadians(20), JITTER_BAD = Math.toRadians(60), BAD_READ = 0.35;
    // It looks back and corrects the line every CORRECT_MIN..+SPREAD ticks, halving the error.
    private static final int CORRECT_MIN = 20, CORRECT_SPREAD = 20;
    // Dashes outwards every DASH_MIN..+SPREAD ticks (the move's own cooldown still applies); an air jump after some.
    private static final int DASH_MIN = 40, DASH_SPREAD = 30;
    private static final double AIR_JUMP_CHANCE = 0.25;
    // Now and then (bots, not bosses) it stops for a moment, unsure.
    private static final double HESITATE_CHANCE = 0.08;
    private static final int HESITATE_MIN = 6, HESITATE_SPREAD = 8;
    // Run speed (navigation multiplier, like sprinting) and how far ahead each leg aims.
    private static final double SPEED = 1.2, LEG = 14;
    // Past the raio by this much it stops running (the domains free it at raio + their slack, about 1).
    private static final double OUT_MARGIN = 3;
    // A run never outlives this, whatever happens to its domain.
    private static final int MAX_TICKS = 20 * 90;

    private DomainEscape() {
    }

    private static final class Run {
        final Object domain;
        final Level level;
        final Vec3 center;
        final double radius;
        final BooleanSupplier open;
        final boolean boss;
        final boolean god;
        double budget;
        boolean trying;
        boolean answer;
        long moveAt;
        double jitter;
        long correctAt;
        long dashAt;
        long airJumpAt = -1;
        long pauseUntil;
        boolean running;

        Run(Object domain, Level level, Vec3 center, double radius, BooleanSupplier open, boolean boss, boolean god) {
            this.domain = domain;
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.open = open;
            this.boss = boss;
            this.god = god;
        }
    }

    // victim was just caught by domain (centered at center, raio radius; open says whether it still stands). Only soul
    // mobs (bosses, bots, SubAlmas, Fighters) get a run: players have their own stamina, plain mobs have no energy to
    // hold it off. Caught again by the same domain (walked back in), it keeps the run it had.
    public static void caught(LivingEntity victim, Object domain, Vec3 center, double radius, BooleanSupplier open) {
        if (!(victim instanceof SoulBoss soul) || victim.level().isClientSide) {
            return;
        }
        Run old = RUNS.get(victim);
        if (old != null && old.domain == domain) {
            return;
        }
        boolean god = soul.spec() != null && soul.spec().god();
        Run run = new Run(domain, victim.level(), center, radius, open, soul.isBoss(), god);
        RUNS.put(victim, run);
        if (DomainDuel.willAnswer(victim)) {
            // It answers with its own domain (the clash): held like before until the answer comes, or doesn't.
            run.answer = true;
        } else {
            decide(victim, run);
        }
        Tasks.addSystem(age -> tick(victim, run, age));
    }

    private static void decide(LivingEntity victim, Run run) {
        int tier = Tiers.of(victim);
        double chance = tier <= 2 ? TRY_DIVINE : tier <= 7 ? TRY_MID : TRY_WEAK;
        if (run.boss) {
            chance = Math.min(TRY_MAX, chance + TRY_BOSS_BONUS);
        }
        if (RANDOM.nextDouble() >= chance) {
            // Froze up: paralyzed like before.
            return;
        }
        long now = victim.level().getGameTime();
        double min = run.boss ? BUDGET_BOSS_MIN : BUDGET_MIN;
        double max = run.boss ? BUDGET_BOSS_MAX : BUDGET_MAX;
        run.budget = min + RANDOM.nextDouble() * (max - min);
        run.trying = true;
        run.moveAt = now + (run.boss ? REACT_BOSS_MIN + RANDOM.nextInt(REACT_BOSS_SPREAD + 1) : REACT_MIN + RANDOM.nextInt(REACT_SPREAD + 1));
        double off = RANDOM.nextDouble() < BAD_READ ? JITTER_BAD : JITTER_SMALL;
        run.jitter = (RANDOM.nextDouble() * 2 - 1) * off;
        run.correctAt = run.moveAt + CORRECT_MIN + RANDOM.nextInt(CORRECT_SPREAD + 1);
        run.dashAt = run.moveAt + 10 + RANDOM.nextInt(15);
    }

    // Whether entity holds off the domain right now (Stamina.resists for a mob).
    public static boolean resists(LivingEntity entity) {
        Run run = RUNS.get(entity);
        return run != null && run.trying && !run.answer && run.budget > 0;
    }

    // The domain's drain on a mob's budget (Stamina.drain for a mob), percent of a full bar.
    public static void drain(LivingEntity entity, double percent) {
        Run run = RUNS.get(entity);
        if (run != null && run.trying && percent > 0) {
            run.budget = Math.max(0, run.budget - percent);
        }
    }

    // The mob is running out of a domain this tick: its own AI keeps off the navigation.
    public static boolean fleeing(LivingEntity entity) {
        Run run = RUNS.get(entity);
        return run != null && run.running;
    }

    private static boolean tick(LivingEntity victim, Run run, int age) {
        if (RUNS.get(victim) != run) {
            return true;
        }
        if (!victim.isAlive() || victim.isRemoved() || age > MAX_TICKS || !run.open.getAsBoolean() || DomainDuel.fighting(victim)) {
            stop(victim, run);
            RUNS.remove(victim);
            return true;
        }
        if (run.answer) {
            if (DomainDuel.willAnswer(victim)) {
                return false;
            }
            // The answer didn't come (no ult, or it couldn't): runs like the others.
            run.answer = false;
            decide(victim, run);
        }
        long now = victim.level().getGameTime();
        boolean can = run.trying && run.budget > 0 && now >= run.moveAt && now >= run.pauseUntil
                && victim.level() == run.level && !Freeze.isFrozen(victim) && !Stun.is(victim) && !DomainSpace.inside(victim);
        Vec3 pos = victim.position();
        Vec3 out = new Vec3(pos.x - run.center.x, 0, pos.z - run.center.z);
        double dist = out.length();
        if (!can || dist > run.radius + OUT_MARGIN) {
            stop(victim, run);
            return false;
        }
        if (!run.boss && run.running && RANDOM.nextDouble() < HESITATE_CHANCE / 20.0) {
            run.pauseUntil = now + HESITATE_MIN + RANDOM.nextInt(HESITATE_SPREAD + 1);
            stop(victim, run);
            return false;
        }
        if (now >= run.correctAt) {
            run.jitter *= 0.5;
            run.correctAt = now + CORRECT_MIN + RANDOM.nextInt(CORRECT_SPREAD + 1);
        }
        double base = dist < 0.5 ? victim.getYRot() * Mth.DEG_TO_RAD + Math.PI / 2 : Math.atan2(out.z, out.x);
        double angle = base + run.jitter;
        Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        run(victim, run, pos, dir, dist, now);
        return false;
    }

    private static void run(LivingEntity victim, Run run, Vec3 pos, Vec3 dir, double dist, long now) {
        boolean first = !run.running;
        run.running = true;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
        if (victim instanceof Mob mob) {
            double leg = Math.min(LEG, run.radius - dist + OUT_MARGIN + 3);
            Vec3 goal = pos.add(dir.scale(Math.max(4, leg)));
            if (first || now % 8 == 0 || mob.getNavigation().isDone()) {
                if (!mob.getNavigation().moveTo(goal.x, pos.y, goal.z, SPEED)) {
                    mob.getMoveControl().setWantedPosition(goal.x, pos.y, goal.z, SPEED);
                }
            }
            mob.setSprinting(true);
        }
        if (run.god) {
            // A god (Bills) doesn't dash or jump around: he walks out, calm.
            return;
        }
        if (now >= run.dashAt && victim.onGround()) {
            face(victim, yaw);
            if (Movement.tryPerform(victim, Movement.Kind.DASH, 1, 0)) {
                run.dashAt = now + DASH_MIN + RANDOM.nextInt(DASH_SPREAD + 1);
                run.airJumpAt = RANDOM.nextDouble() < AIR_JUMP_CHANCE ? now + 5 + RANDOM.nextInt(5) : -1;
            } else {
                run.dashAt = now + 5;
            }
        }
        if (run.airJumpAt >= 0 && now >= run.airJumpAt) {
            run.airJumpAt = -1;
            if (!victim.onGround()) {
                face(victim, yaw);
                Movement.tryPerform(victim, Movement.Kind.PULO_DUPLO, 1, 0);
            }
        }
    }

    private static void face(LivingEntity victim, float yaw) {
        victim.setYRot(yaw);
        victim.setYBodyRot(yaw);
        victim.setYHeadRot(yaw);
    }

    private static void stop(LivingEntity victim, Run run) {
        if (!run.running) {
            return;
        }
        run.running = false;
        if (victim instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setSprinting(false);
        }
    }
}
