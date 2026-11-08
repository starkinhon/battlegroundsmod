package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.HashSet;
import java.util.Set;

// Players the server says carry Kokushibo's sword somewhere in their inventory (their sheath is drawn on the hip), and
// those of them who have it drawn (held in combat mode; out of it the sword rests in the sheath even when selected).
public final class Sheaths {
    private static final Set<Integer> CARRYING = new HashSet<>();
    private static final Set<Integer> DRAWN = new HashSet<>();

    private Sheaths() {
    }

    public static void set(int entityId, boolean carrying, boolean drawn) {
        if (carrying) {
            CARRYING.add(entityId);
        } else {
            CARRYING.remove(entityId);
        }
        if (drawn) {
            DRAWN.add(entityId);
        } else {
            DRAWN.remove(entityId);
        }
    }

    static boolean carrying(int entityId) {
        return CARRYING.contains(entityId);
    }

    static boolean drawn(int entityId) {
        return DRAWN.contains(entityId);
    }

    // The Kokushibo boss carries it until the awakening eats the sheath, and draws it when his fight starts.
    static boolean carrying(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof com.starkinhon.battlegroundsmod.boss.KokushiboEntity boss) {
            return boss.look() == com.starkinhon.battlegroundsmod.boss.KokushiboEntity.LOOK_NORMAL;
        }
        return carrying(entity.getId());
    }

    static boolean drawn(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof com.starkinhon.battlegroundsmod.boss.KokushiboEntity boss) {
            return boss.drawn() || boss.look() != com.starkinhon.battlegroundsmod.boss.KokushiboEntity.LOOK_NORMAL;
        }
        return drawn(entity.getId());
    }
}
