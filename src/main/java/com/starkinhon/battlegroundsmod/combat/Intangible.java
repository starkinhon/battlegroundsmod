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
    private static final Map<Entity, Integer> HELD = new HashMap<>();
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
        if (ticks <= 0) {
            return;
        }
        HELD.merge(entity, 1, Integer::sum);
        Tasks.addSystem(age -> {
            if (age < ticks) {
                return false;
            }
            HELD.computeIfPresent(entity, (e, count) -> count > 1 ? count - 1 : null);
            return true;
        });
    }

    public static void clear() {
        HELD.clear();
        SHIELD.clear();
    }
}
