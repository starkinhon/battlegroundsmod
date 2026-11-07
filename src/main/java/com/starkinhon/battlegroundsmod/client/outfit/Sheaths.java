package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.HashMap;
import java.util.Map;

// Which Kokushibo sword the server says each player carries in the inventory (1 realistic, 2 blocky), for the
// sheath on the hip.
public final class Sheaths {
    private static final Map<Integer, Integer> CARRYING = new HashMap<>();

    private Sheaths() {
    }

    public static void set(int entityId, int style) {
        if (style > 0) {
            CARRYING.put(entityId, style);
        } else {
            CARRYING.remove(entityId);
        }
    }

    // 0 = none.
    static int style(int entityId) {
        return CARRYING.getOrDefault(entityId, 0);
    }
}
