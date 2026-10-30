package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.Entity;

// Untouchable for a while (used during cutscenes so outsiders can't hit the people in it): no damage, knockback,
// projectile hits or skill targeting. Damage that bypasses invulnerability (void, /kill) still goes through.
// The checks live in PowerEvents and Combat.isTarget.
public final class Intangible {
    private static final Map<Entity, Integer> HELD = new HashMap<>();

    private Intangible() {
    }

    public static boolean is(Entity entity) {
        return HELD.containsKey(entity);
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
    }
}
