package com.starkinhon.battlegroundsmod.combat;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// One grab at a time (Stark, 2026-11-08). A trap that keeps someone for a long while (the hakai ball) says so here; a
// grab or cutscene that takes that someone (the Destruction Bop, the Agarrão, the Esfera, the Hakai, Gojo's Shift + Z)
// calls take first, which ends the trap on the spot (the ball blows up) instead of both pulling the target to two places
// at once and the trap yanking it back when the grab is over.
public final class Capture {
    private static final Map<Entity, Runnable> TRAPS = new WeakHashMap<>();
    // After a grab lets them go they can't be grabbed again for a moment, except by the one who grabbed them (their own
    // combo, like the Bop and then the Esfera): no one locks someone down twenty times in a row (Stark, 2026-11-08, as
    // in Street Fighter).
    public static final int REGRAB_TICKS = 30;
    private static final Map<Entity, Long> SAFE_UNTIL = new WeakHashMap<>();
    private static final Map<Entity, Entity> GRABBED_BY = new WeakHashMap<>();

    private Capture() {
    }

    // target is kept by a trap that breaker ends right away.
    public static void trap(Entity target, Runnable breaker) {
        Runnable before = TRAPS.put(target, breaker);
        if (before != null && before != breaker) {
            before.run();
        }
    }

    // The trap ended by itself: forget it (only if it is still the one there).
    public static void untrap(Entity target, Runnable breaker) {
        TRAPS.remove(target, breaker);
    }

    public static boolean trapped(Entity target) {
        return TRAPS.containsKey(target);
    }

    // A grab takes target: whatever trap had it ends now.
    public static void take(Entity target) {
        Runnable breaker = TRAPS.remove(target);
        if (breaker != null) {
            breaker.run();
        }
    }

    // attacker grabs target for ticks: after that target has REGRAB_TICKS safe from everyone else's grabs.
    public static void grabbed(LivingEntity target, LivingEntity attacker, int ticks) {
        long until = target.level().getGameTime() + Math.max(0, ticks) + REGRAB_TICKS;
        SAFE_UNTIL.merge(target, until, Math::max);
        GRABBED_BY.put(target, attacker);
    }

    // A grab of caster's can take target: not in a Domain Expansion caster is not in (or caught by another's domain),
    // where the domain keeps them in place and nothing from outside reaches them, and not just let go by someone
    // else's grab (REGRAB_TICKS).
    public static boolean canGrab(LivingEntity caster, LivingEntity target) {
        if (target == null || InfiniteVoid.apart(caster, target) || InfiniteVoid.heldByOther(target, caster)) {
            return false;
        }
        Long until = SAFE_UNTIL.get(target);
        return until == null || target.level().getGameTime() >= until || GRABBED_BY.get(target) == caster;
    }

    public static void clear() {
        TRAPS.clear();
        SAFE_UNTIL.clear();
        GRABBED_BY.clear();
    }
}
