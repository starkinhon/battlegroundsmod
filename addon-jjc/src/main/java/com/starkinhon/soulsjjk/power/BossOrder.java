package com.starkinhon.soulsjjk.power;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// How a boss "holds the key" this time. Players hold Z/X/C as long as they want; a boss can't, so its held techniques
// (the Blue, the Red, the Purple) roll a charge of their own. A boss with tactics (boss/BossTactics, the Gojo boss)
// leaves an order here right before casting instead, and the technique takes it when it starts:
// - hold: charge it instead of the tap;
// - charge: how much of a full charge (0 to 1; the Purple: how much it grows before it is launched);
// - plan: what a charged Blue does once out (Azul's BossPlan names: CHASE, SHOOT, PARK, PARK_THEN_UNPARK,
//   PARK_THEN_ORBIT, IRRESTRITO), null = its own pick;
// - aimAtBlue: the Red goes at the boss's own parked Blue, not at the target (the Unlimited Purple).
// Without an order (other bosses, or a move the tactics leave alone) each technique keeps its own random.
public final class BossOrder {
    public record Order(boolean hold, double charge, String plan, boolean aimAtBlue) {
        public static Order tap() {
            return new Order(false, 0, null, false);
        }

        public static Order charged(double charge) {
            return new Order(true, charge, null, false);
        }

        public static Order charged(double charge, String plan) {
            return new Order(true, charge, plan, false);
        }
    }

    private static final Map<LivingEntity, Order> ORDERS = new WeakHashMap<>();

    private BossOrder() {
    }

    public static void give(LivingEntity caster, Order order) {
        if (order != null && !(caster instanceof Player)) {
            ORDERS.put(caster, order);
        }
    }

    // The order for the technique starting now, gone once taken. null = none (always for players).
    public static Order take(LivingEntity caster) {
        return caster instanceof Player ? null : ORDERS.remove(caster);
    }

    public static void clear(LivingEntity caster) {
        ORDERS.remove(caster);
    }
}
