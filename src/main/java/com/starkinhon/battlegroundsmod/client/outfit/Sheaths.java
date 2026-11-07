package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.HashSet;
import java.util.Set;

// Players the server says carry Kokushibo's sword somewhere in their inventory (their sheath is drawn on the hip).
public final class Sheaths {
    private static final Set<Integer> CARRYING = new HashSet<>();

    private Sheaths() {
    }

    public static void set(int entityId, boolean carrying) {
        if (carrying) {
            CARRYING.add(entityId);
        } else {
            CARRYING.remove(entityId);
        }
    }

    static boolean carrying(int entityId) {
        return CARRYING.contains(entityId);
    }
}
