package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.Entity;

// Untouchable for a while (used during cutscenes so outsiders can't hit the people in it): no damage, knockback,
// projectile hits or skill targeting. Damage that bypasses invulnerability (void, /kill) still goes through.
// The checks live in PowerEvents and Combat.isTarget.
// A shield (shield) is the softer kind: no damage, knockback or projectile hits either, but skills still target the one
// under it, so a stun or a grab can still knock them out of what they are doing.
public final class Intangible {
    // Who left each hold on an entity (Tasks.owner(); NOBODY when unknown) and how many of theirs are still running, and
    // the ones whose own cutscene it is (holdExcept): their own hits still land. Without that the Destruction Bop and
    // Madara's Agarrão did no damage, their target being intangible to everyone (Stark 2026-11-08).
    private static final class State {
        final Map<Object, Integer> owners = new HashMap<>();
        final Map<Entity, Integer> strikers = new HashMap<>();
    }

    private static final Object NOBODY = new Object();
    private static final Map<Entity, State> HELD = new HashMap<>();
    private static final Map<Entity, Integer> SHIELD = new HashMap<>();

    private Intangible() {
    }

    public static boolean is(Entity entity) {
        return HELD.containsKey(entity);
    }

    // No damage, knockback or projectile hits: intangible or under a shield.
    public static boolean untouchable(Entity entity) {
        return HELD.containsKey(entity) || SHIELD.containsKey(entity);
    }

    // Same, but the one whose cutscene it is (holdExcept) still hits them.
    public static boolean untouchableBy(Entity entity, Entity attacker) {
        if (SHIELD.containsKey(entity)) {
            return true;
        }
        State state = HELD.get(entity);
        return state != null && (attacker == null || !state.strikers.containsKey(attacker));
    }

    // Shields entity until the returned action runs (once; later runs do nothing) or maxTicks pass, whichever is first.
    public static Runnable shield(Entity entity, int maxTicks) {
        SHIELD.merge(entity, 1, Integer::sum);
        boolean[] done = {false};
        Runnable release = () -> {
            if (!done[0]) {
                done[0] = true;
                SHIELD.computeIfPresent(entity, (e, count) -> count > 1 ? count - 1 : null);
            }
        };
        Tasks.addSystem(age -> {
            if (done[0]) {
                return true;
            }
            if (age < maxTicks) {
                return false;
            }
            release.run();
            return true;
        });
        return release;
    }

    public static void hold(Entity entity, int ticks) {
        holdExcept(entity, ticks, null);
    }

    // Intangible to everyone but striker, the one whose cutscene it is: their own blows in it still hurt.
    public static void holdExcept(Entity entity, int ticks, Entity striker) {
        if (ticks <= 0) {
            return;
        }
        Object owner = Tasks.owner() != null ? Tasks.owner() : NOBODY;
        State state = HELD.computeIfAbsent(entity, e -> new State());
        state.owners.merge(owner, 1, Integer::sum);
        if (striker != null) {
            state.strikers.merge(striker, 1, Integer::sum);
        }
        Tasks.addSystem(age -> {
            if (age < ticks) {
                return false;
            }
            if (HELD.get(entity) != state) {
                // Ended early (free) and maybe held again since: not this hold's to end.
                return true;
            }
            state.owners.computeIfPresent(owner, (o, count) -> count > 1 ? count - 1 : null);
            if (striker != null) {
                state.strikers.computeIfPresent(striker, (o, count) -> count > 1 ? count - 1 : null);
            }
            if (state.owners.isEmpty()) {
                HELD.remove(entity);
            }
            return true;
        });
    }

    // Ends the holds the move running now (Tasks.owner()) put on entity, right away; holds from other moves (another
    // cutscene still playing) go on. With no known owner running, every hold ends.
    public static void free(Entity entity) {
        State state = HELD.get(entity);
        if (state == null) {
            return;
        }
        Entity owner = Tasks.owner();
        if (owner != null) {
            state.owners.remove(owner);
            state.owners.remove(NOBODY);
            state.strikers.remove(owner);
        }
        if (owner == null || state.owners.isEmpty()) {
            HELD.remove(entity);
        }
    }

    public static void clear() {
        HELD.clear();
        SHIELD.clear();
    }
}
