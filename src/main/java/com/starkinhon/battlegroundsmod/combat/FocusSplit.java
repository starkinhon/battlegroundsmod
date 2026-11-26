// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

// Splitting the focus between several players (Stark's test with 4 friends, 2026-11-26: the bosses, bots and mobs
// went after one of them only, the first who hit, until he died). Now, after holding a player for HOLD_MIN..HOLD_MAX
// ticks, the mob weighs every player in the fight and draws one: whoever hit it most lately, whoever is closest, a
// bit of luck, and the one it holds weighs less (it wants to turn). It never turns in the middle of a move, a combo or
// while the one it holds is stunned or caught (the combo goes to the end), and a mob fighting someone who is not a
// player (another boss, Kyoka's clone, an Ino victim, a FightRod enemy) is never touched. Alone, nothing changes.
public final class FocusSplit {
    // Holding a player at least this long before thinking of turning (6 to 11 s).
    private static final int HOLD_MIN = 120;
    private static final int HOLD_MAX = 220;
    // Someone hitting hard (EARLY_SHARE of its life in the last seconds) is weighed sooner, after EARLY_HOLD.
    private static final int EARLY_HOLD = 60;
    private static final float EARLY_SHARE = 0.10f;
    // Nobody else to draw: looks again in this many ticks.
    private static final int RETRY = 40;
    // Who hit it counts from this far; someone who didn't, only this close and in sight.
    private static final double HITTER_RANGE = 32;
    private static final double BYSTANDER_RANGE = 16;
    // The damage remembered halves every HALF_LIFE ticks and is forgotten after FORGET.
    private static final double HALF_LIFE = 100;
    private static final long FORGET = 600;
    // The weights: base, damage share, closeness, and the one held (times).
    private static final double W_BASE = 1.0;
    private static final double W_DAMAGE = 3.0;
    private static final double W_CLOSE = 1.5;
    private static final double W_HELD = 0.5;

    private static final class Hit {
        float amount;
        long time;
    }

    private final Map<UUID, Hit> hits = new HashMap<>();
    private LivingEntity held;
    private long heldSince;
    private long nextCheck;
    private long lastLook = Long.MIN_VALUE / 2;

    // A player's hit on the mob (players only: the rest never takes part).
    public void hurtBy(LivingEntity attacker, float amount, long now) {
        if (!(attacker instanceof Player) || amount <= 0) {
            return;
        }
        Hit hit = hits.computeIfAbsent(attacker.getUUID(), k -> new Hit());
        hit.amount = (float) (decayed(hit, now) + amount);
        hit.time = now;
    }

    // Every server tick of the mob. calm: free to turn now (no move, combo or hold going). bystanders: it also goes
    // after players who never hit it (a hostile one); otherwise only after those who did. valid: who it may fight.
    public void tick(Mob mob, boolean calm, boolean bystanders, Predicate<Player> valid) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        LivingEntity current = mob.getTarget();
        if (current != held) {
            held = current;
            heldSince = now;
            nextCheck = now + hold(mob);
        }
        if (!(current instanceof ServerPlayer)) {
            return;
        }
        if (now < nextCheck && !(now - heldSince >= EARLY_HOLD && now - lastLook >= RETRY && someoneHitsHard(mob, current, now))) {
            return;
        }
        if (!calm) {
            // Tries again on the next calm tick.
            return;
        }
        lastLook = now;
        forgetOld(now);
        List<Player> candidates = new ArrayList<>();
        for (Player p : level.players()) {
            if (p == current || !valid.test(p) || p.isInvisible()) {
                continue;
            }
            double d = p.distanceTo(mob);
            boolean hitter = hits.containsKey(p.getUUID());
            if (hitter ? d <= HITTER_RANGE : bystanders && d <= BYSTANDER_RANGE && mob.hasLineOfSight(p)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            nextCheck = now + RETRY;
            return;
        }
        candidates.add((Player) current);
        double total = 0;
        for (Player p : candidates) {
            total += damageOf(p, now);
        }
        double[] weights = new double[candidates.size()];
        double sum = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Player p = candidates.get(i);
            double share = total > 0 ? damageOf(p, now) / total : 0;
            double close = Math.max(0, 1 - p.distanceTo(mob) / HITTER_RANGE);
            double w = W_BASE + W_DAMAGE * share + W_CLOSE * close;
            if (p == current) {
                w *= W_HELD;
            }
            weights[i] = w;
            sum += w;
        }
        double roll = mob.getRandom().nextDouble() * sum;
        Player picked = candidates.get(candidates.size() - 1);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                picked = candidates.get(i);
                break;
            }
        }
        if (picked != current) {
            mob.setTarget(picked);
            if (mob.getTarget() != picked) {
                // Something refused the change: keeps who it had and waits.
                nextCheck = now + RETRY;
                return;
            }
            held = picked;
            heldSince = now;
            nextCheck = now + hold(mob);
        } else {
            nextCheck = now + hold(mob) / 2;
        }
    }

    private boolean someoneHitsHard(Mob mob, LivingEntity current, long now) {
        float line = mob.getMaxHealth() * EARLY_SHARE;
        for (Map.Entry<UUID, Hit> e : hits.entrySet()) {
            if (!e.getKey().equals(current.getUUID()) && decayed(e.getValue(), now) >= line) {
                return true;
            }
        }
        return false;
    }

    private double damageOf(Player p, long now) {
        Hit hit = hits.get(p.getUUID());
        return hit == null ? 0 : decayed(hit, now);
    }

    private static double decayed(Hit hit, long now) {
        return hit.amount * Math.pow(0.5, Math.max(0, now - hit.time) / HALF_LIFE);
    }

    private void forgetOld(long now) {
        for (Iterator<Hit> it = hits.values().iterator(); it.hasNext();) {
            if (now - it.next().time > FORGET) {
                it.remove();
            }
        }
    }

    private static int hold(Mob mob) {
        return HOLD_MIN + mob.getRandom().nextInt(HOLD_MAX - HOLD_MIN + 1);
    }
}
